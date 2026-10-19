use super::hotswap_cache_plan::HotswapCachePlan;
use crate::artifact_lock::ArtifactLock;
use crate::cancellation::CancellationToken;
use crate::cli::jar::JarBuildOptionsArgs;
use crate::jar_build::BuildMode;
use crate::jar_build::RunTestAction;
use crate::jar_build::RunTestCommand as JarBuildRunTestCommand;
use crate::jar_build::RunTestOptions;
use crate::jar_build::build_snapshot_receipt::BuildSnapshotReceipt;
use crate::jar_build::runtime_session_receipt::RuntimeSessionReceipt;
use eyre::Context;
use eyre::ensure;
use facet::Facet;
use figue as args;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::ffi::OsStr;
use std::fs;
use std::path::Path;
use std::path::PathBuf;
use std::process::Command;

const DEFAULT_HOTSWAP_PORT: u16 = 5005;
const DEFAULT_CLASS_PREFIX: &str = "ca.teamdman.";
const HOTSWAP_HELPER_SOURCE: &str = include_str!("sfm_hotswap_helper.java");

/// Explicit application of successful compiled classes to an identified live client.
#[derive(Facet, Debug, Clone)]
pub struct RunHotswapArgs {
    /// Build and compile options.
    #[facet(flatten)]
    pub options: JarBuildOptionsArgs,
    /// Apply this successful snapshot receipt without compiling another candidate.
    #[facet(default, args::named)]
    pub build_snapshot: Option<PathBuf>,
    /// Exact live session receipt; otherwise use the projection's latest session for the selected port.
    #[facet(default, args::named)]
    pub session: Option<PathBuf>,
    /// JDWP port exposed by run client --hotswap.
    #[facet(default, args::named)]
    pub port: Option<u16>,
    /// Only reload loaded classes whose binary name starts with this prefix.
    #[facet(default, args::named)]
    pub class_prefix: Option<String>,
    /// Only reload this exact loaded class binary name.
    #[facet(default, args::named)]
    pub class_name: Option<String>,
}

impl RunHotswapArgs {
    /// # Errors
    /// Rejects unsuccessful/stale snapshots, wrong sessions, unsafe paths and unsupported updates.
    #[expect(
        clippy::needless_pass_by_value,
        reason = "Run commands retain the owning cancellation-token dispatcher interface."
    )]
    pub fn invoke(self, cancellation_token: CancellationToken) -> eyre::Result<()> {
        let (root, keys) = crate::jar_build::projection_selection::select_projections(
            self.options.repo_root.as_deref(),
            &self.options.projection,
        )?;
        ensure!(keys.len() == 1, "hotswap requires exactly one projection");
        let minecraft_dir = root.join("platform/minecraft/projections").join(&keys[0]);
        let Some(snapshot) = self.prepare_snapshot(&minecraft_dir, cancellation_token.clone())?
        else {
            return Ok(());
        };
        cancellation_token.bail_if_cancelled()?;
        let session = self.select_session(&minecraft_dir, &snapshot)?;
        let baseline = BuildSnapshotReceipt::load(&session.snapshot_receipt, &minecraft_dir)?;
        ensure_apply_contract(
            &snapshot.runtime_contract,
            &baseline.runtime_contract,
            &snapshot.files,
            &baseline.files,
            &session.active_roots,
        )?;
        let port = session.jdwp_port.ok_or_else(|| {
            eyre::eyre!("selected client has no JDWP port; restart with --hotswap")
        })?;
        let _apply_lock = ArtifactLock::try_acquire(
            session.session_dir.join(".apply.lock"),
            "explicit live apply",
        )?
        .ok_or_else(|| eyre::eyre!("another explicit application is using this session"))?;
        let classes_dirs = active_snapshot_classes(&snapshot, &session)?;
        let java = snapshot.java();
        let helper = tempfile::tempdir().wrap_err("Failed to create owned apply staging")?;
        let helper_classes = helper.path().join("classes");
        compile_hotswap_helper(&java, &helper_classes)?;
        cancellation_token.bail_if_cancelled()?;
        snapshot.recheck()?;
        session.check_active(&minecraft_dir, port)?;
        let selector = self
            .class_name
            .as_deref()
            .map(|name| format!("={name}"))
            .or(self.class_prefix)
            .unwrap_or_else(|| DEFAULT_CLASS_PREFIX.to_owned());
        run_hotswap_helper(
            &java,
            &helper_classes,
            port,
            &session,
            &selector,
            &classes_dirs,
        )
    }

    fn prepare_snapshot(
        &self,
        minecraft_dir: &Path,
        cancellation: CancellationToken,
    ) -> eyre::Result<Option<BuildSnapshotReceipt>> {
        if let Some(receipt) = &self.build_snapshot {
            let snapshot = BuildSnapshotReceipt::load(receipt, minecraft_dir)?;
            if self.options.dry_run {
                tracing::info!(receipt = %receipt.display(), "Verified snapshot; dry run does not attach or apply");
                return Ok(None);
            }
            return Ok(Some(snapshot));
        }
        // Legacy explicit `run hotswap` still prepares first. Ordinary compile
        // and checks only publish a snapshot and never reach this apply path.
        let mut options = self.options.clone().into_options(BuildMode::Build)?;
        let temporary = tempfile::tempdir()?;
        let plan_path = options
            .plan_json
            .clone()
            .unwrap_or_else(|| temporary.path().join("plan.json"));
        options.plan_json = Some(plan_path.clone());
        JarBuildRunTestCommand::new(
            options,
            RunTestOptions {
                action: RunTestAction::Compile,
                filter: None,
                no_capture: false,
            },
            cancellation,
        )
        .invoke()?;
        if self.options.dry_run {
            return Ok(None);
        }
        let plan: HotswapCachePlan = facet_json::from_slice(&fs::read(&plan_path)?)?;
        Ok(Some(BuildSnapshotReceipt::load(
            &plan.cache_dir.join("state/latest-successful-build.json"),
            minecraft_dir,
        )?))
    }

    fn select_session(
        &self,
        minecraft_dir: &Path,
        snapshot: &BuildSnapshotReceipt,
    ) -> eyre::Result<RuntimeSessionReceipt> {
        let port = self.port.unwrap_or(DEFAULT_HOTSWAP_PORT);
        let locator = self.session.clone().unwrap_or_else(|| {
            minecraft_dir
                .join("build/sfm-runtime")
                .join(format!("latest-hotswap-{port}.json"))
        });
        let session = RuntimeSessionReceipt::load(&locator, minecraft_dir)?;
        let port = self.port.or(session.jdwp_port).unwrap_or(port);
        session.check_active(minecraft_dir, port)?;
        ensure!(
            session.java_major == snapshot.java_major
                && session.sdk_identity == snapshot.sdk_identity,
            "successful snapshot and live client use different SDK identities; restart the client with the selected SDK"
        );
        ensure!(
            matches!(
                session.run_kind.as_str(),
                "runClient" | "runGameTestPreview"
            ),
            "selected session is not a supported interactive client; restart/select the correct client"
        );
        Ok(session)
    }
}

fn active_snapshot_classes(
    snapshot: &BuildSnapshotReceipt,
    session: &RuntimeSessionReceipt,
) -> eyre::Result<Vec<PathBuf>> {
    let roots = snapshot.class_directories()?;
    let roots = roots
        .into_iter()
        .filter(|path| {
            path.strip_prefix(snapshot.snapshot_dir.join("project"))
                .ok()
                .and_then(|relative| relative.to_str())
                .is_some_and(|relative| {
                    session
                        .active_roots
                        .iter()
                        .any(|root| root == &relative.replace('\\', "/"))
                })
        })
        .collect::<Vec<_>>();
    ensure!(
        !roots.is_empty(),
        "no successful class directories are active in this runtime session"
    );
    Ok(roots)
}

fn ensure_apply_contract(
    candidate_contract: &str,
    baseline_contract: &str,
    candidate_files: &BTreeMap<String, String>,
    baseline_files: &BTreeMap<String, String>,
    roots: &[String],
) -> eyre::Result<()> {
    ensure!(
        candidate_contract == baseline_contract,
        "loader, dependency, platform or project metadata changed; restart the client with this successful build"
    );
    let active = |path: &str| {
        roots
            .iter()
            .any(|root| path.starts_with(&format!("project/{root}/")))
    };
    let classes = |files: &BTreeMap<String, String>| {
        files
            .keys()
            .filter(|path| active(path) && Path::new(path).extension() == Some(OsStr::new("class")))
            .cloned()
            .collect::<BTreeSet<_>>()
    };
    ensure!(
        classes(candidate_files) == classes(baseline_files),
        "the active class set changed; restart the client to load added/removed classes"
    );
    let resources = |files: &BTreeMap<String, String>| {
        files
            .iter()
            .filter(|(path, _)| {
                active(path) && Path::new(path).extension() != Some(OsStr::new("class"))
            })
            .map(|(path, value)| (path.clone(), value.clone()))
            .collect::<BTreeMap<_, _>>()
    };
    ensure!(
        resources(candidate_files) == resources(baseline_files),
        "active resources changed; restart the client because class redefinition does not replace resources"
    );
    Ok(())
}

fn compile_hotswap_helper(
    java: &crate::jdk::ResolvedJava,
    helper_classes_dir: &Path,
) -> eyre::Result<()> {
    let helper_root = helper_classes_dir
        .parent()
        .ok_or_else(|| eyre::eyre!("Invalid hotswap helper output path"))?;
    fs::create_dir_all(helper_root).wrap_err_with(|| {
        format!(
            "Failed to create hotswap helper directory {}",
            helper_root.display()
        )
    })?;
    fs::create_dir_all(helper_classes_dir).wrap_err_with(|| {
        format!(
            "Failed to create hotswap helper classes directory {}",
            helper_classes_dir.display()
        )
    })?;
    let source_path = helper_root.join("SfmHotswapHelper.java");
    fs::write(&source_path, HOTSWAP_HELPER_SOURCE)
        .wrap_err_with(|| format!("Failed to write {}", source_path.display()))?;

    let mut command = Command::new(javac_executable(java));
    command
        .arg("--add-modules")
        .arg("jdk.jdi")
        .arg("-d")
        .arg(helper_classes_dir)
        .arg(&source_path);
    let output = command
        .output()
        .wrap_err("Failed to launch javac for hotswap helper")?;
    if !output.status.success() {
        eyre::bail!(
            "javac failed for hotswap helper with {}.\nstdout:\n{}\nstderr:\n{}",
            output.status,
            String::from_utf8_lossy(&output.stdout),
            String::from_utf8_lossy(&output.stderr)
        );
    }
    Ok(())
}

fn run_hotswap_helper(
    java: &crate::jdk::ResolvedJava,
    helper_classes_dir: &Path,
    port: u16,
    session: &RuntimeSessionReceipt,
    class_prefix: &str,
    classes_dirs: &[PathBuf],
) -> eyre::Result<()> {
    let mut command = Command::new(&java.executable);
    command
        .arg("--add-modules")
        .arg("jdk.jdi")
        .arg("-cp")
        .arg(helper_classes_dir)
        .arg("SfmHotswapHelper")
        .arg("127.0.0.1")
        .arg(port.to_string())
        .arg(&session.session_id)
        .arg(session.java_major.to_string())
        .arg(class_prefix)
        .args(classes_dirs);
    let output = command
        .output()
        .wrap_err("Failed to launch hotswap helper")?;
    print!("{}", String::from_utf8_lossy(&output.stdout));
    eprint!("{}", String::from_utf8_lossy(&output.stderr));
    if !output.status.success() {
        eyre::bail!(
            "hotswap application failed with {}; inspect the compatibility diagnostic above and restart the client if the change is unsupported",
            output.status
        );
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::ensure_apply_contract;
    use std::collections::BTreeMap;

    #[test]
    fn existing_class_bytes_can_change_but_resources_classes_and_dependencies_need_restart() {
        let roots = vec!["classes".to_owned(), "staged-resources".to_owned()];
        let original = BTreeMap::from([
            (
                "project/classes/Main.class".to_owned(),
                "old-class".to_owned(),
            ),
            (
                "project/staged-resources/mod.json".to_owned(),
                "resource".to_owned(),
            ),
        ]);
        let mut candidate = original.clone();
        candidate.insert(
            "project/classes/Main.class".to_owned(),
            "new-class".to_owned(),
        );
        assert!(
            ensure_apply_contract(
                "same-contract",
                "same-contract",
                &candidate,
                &original,
                &roots
            )
            .is_ok()
        );
        assert!(
            ensure_apply_contract(
                "new-dependencies",
                "same-contract",
                &candidate,
                &original,
                &roots
            )
            .unwrap_err()
            .to_string()
            .contains("restart")
        );
        candidate.insert(
            "project/staged-resources/mod.json".to_owned(),
            "changed-resource".to_owned(),
        );
        assert!(
            ensure_apply_contract(
                "same-contract",
                "same-contract",
                &candidate,
                &original,
                &roots
            )
            .is_err()
        );
        candidate = original.clone();
        candidate.insert(
            "project/classes/Added.class".to_owned(),
            "new-class".to_owned(),
        );
        assert!(
            ensure_apply_contract(
                "same-contract",
                "same-contract",
                &candidate,
                &original,
                &roots
            )
            .is_err()
        );
        candidate = original.clone();
        candidate.remove("project/classes/Main.class");
        assert!(
            ensure_apply_contract(
                "same-contract",
                "same-contract",
                &candidate,
                &original,
                &roots
            )
            .is_err()
        );
    }

    #[test]
    fn inactive_source_sets_do_not_expand_the_live_apply_contract() {
        let roots = vec!["classes".to_owned()];
        let original =
            BTreeMap::from([("project/classes/Main.class".to_owned(), "old".to_owned())]);
        let mut candidate = original.clone();
        candidate.insert(
            "project/datagen/classes/Data.class".to_owned(),
            "new".to_owned(),
        );
        assert!(ensure_apply_contract("same", "same", &candidate, &original, &roots).is_ok());
    }
}

fn javac_executable(java: &crate::jdk::ResolvedJava) -> PathBuf {
    java.home.as_ref().map_or_else(
        || PathBuf::from(if cfg!(windows) { "javac.exe" } else { "javac" }),
        |home| {
            home.join("bin")
                .join(if cfg!(windows) { "javac.exe" } else { "javac" })
        },
    )
}
