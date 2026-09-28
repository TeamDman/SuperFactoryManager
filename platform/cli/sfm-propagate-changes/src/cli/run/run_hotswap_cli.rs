use crate::branch_targets::select_single_worktree_target;
use crate::cancellation::CancellationToken;
use crate::cli::jar::JarBuildOptionsArgs;
use crate::jar_build::BuildMode;
use crate::jar_build::RunTestAction;
use crate::jar_build::RunTestCommand as JarBuildRunTestCommand;
use crate::jar_build::RunTestOptions;
use crate::jar_build::json_path::JsonOptionalPath;
use crate::jar_build::json_path::JsonPath;
use eyre::Context;
use facet::Facet;
use figue as args;
use std::fs;
use std::path::Path;
use std::path::PathBuf;
use std::process::Command;

const DEFAULT_HOTSWAP_PORT: u16 = 5005;
const DEFAULT_CLASS_PREFIX: &str = "ca.teamdman.";
const HOTSWAP_HELPER_SOURCE: &str = include_str!("sfm_hotswap_helper.java");

/// Arguments for redefining classes in a running hotswap-enabled client.
#[derive(Facet, Debug, Clone)]
pub struct RunHotswapArgs {
    /// Build and compile options.
    #[facet(flatten)]
    pub options: JarBuildOptionsArgs,
    /// JDWP port exposed by `run client --hotswap`.
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
    ///
    /// Returns an error if compilation fails, the helper cannot be built, or JDWP redefinition fails.
    pub fn invoke(self, cancellation_token: CancellationToken) -> eyre::Result<()> {
        let build_options = self.options.clone().into_options(BuildMode::Build)?;
        let temporary_plan_dir =
            tempfile::tempdir().wrap_err("Failed to create a temporary hotswap plan directory")?;
        let plan_path = build_options
            .plan_json
            .clone()
            .unwrap_or_else(|| temporary_plan_dir.path().join("hotswap-build-plan.json"));
        let mut compile_options = build_options.clone();
        compile_options.plan_json = Some(plan_path.clone());
        JarBuildRunTestCommand::new(
            compile_options,
            RunTestOptions {
                action: RunTestAction::Compile,
                filter: None,
                no_capture: false,
            },
            cancellation_token,
        )
        .invoke()?;

        let target = select_single_worktree_target(&build_options.branch)?;
        let minecraft_dir = target
            .worktree_path
            .as_path()
            .join("platform")
            .join("minecraft");
        let project_classes_dir = minecraft_dir
            .join("build")
            .join("sfm-toolchain")
            .join("project");
        let classes_dirs = client_hotswap_class_directories(&project_classes_dir)?;

        let helper_classes_dir = minecraft_dir
            .join("build")
            .join("sfm-toolchain")
            .join("run")
            .join("hotswap-helper")
            .join("classes");
        let java = selected_java_from_build_plan(&plan_path, &minecraft_dir)?;
        compile_hotswap_helper(&java, &helper_classes_dir)?;
        let class_selector = self
            .class_name
            .as_deref()
            .map(|class_name| format!("={class_name}"))
            .or_else(|| self.class_prefix.clone())
            .unwrap_or_else(|| DEFAULT_CLASS_PREFIX.to_string());
        run_hotswap_helper(
            &java,
            &helper_classes_dir,
            self.port.unwrap_or(DEFAULT_HOTSWAP_PORT),
            &class_selector,
            &classes_dirs,
        )
    }
}

#[derive(Facet)]
struct HotswapBuildPlan {
    #[facet(proxy = JsonPath)]
    minecraft_dir: PathBuf,
    java: HotswapJavaPlan,
}

#[derive(Facet)]
struct HotswapJavaPlan {
    #[facet(proxy = JsonPath)]
    executable: PathBuf,
    #[facet(proxy = JsonOptionalPath)]
    home: Option<PathBuf>,
    version_output: String,
    major_version: u32,
    selection: String,
    pin_url: Option<String>,
    pin_sha512: Option<String>,
}

fn selected_java_from_build_plan(
    plan_path: &Path,
    expected_minecraft_dir: &Path,
) -> eyre::Result<crate::jdk::ResolvedJava> {
    let plan_json = fs::read_to_string(plan_path).wrap_err_with(|| {
        format!(
            "Failed to read completed build plan {}",
            plan_path.display()
        )
    })?;
    let plan: HotswapBuildPlan = facet_json::from_str(&plan_json).wrap_err_with(|| {
        format!(
            "Failed to parse completed build plan {}",
            plan_path.display()
        )
    })?;
    let planned_dir = fs::canonicalize(&plan.minecraft_dir).wrap_err_with(|| {
        format!(
            "Failed to resolve planned Minecraft directory {}",
            plan.minecraft_dir.display()
        )
    })?;
    let expected_dir = fs::canonicalize(expected_minecraft_dir).wrap_err_with(|| {
        format!(
            "Failed to resolve selected Minecraft directory {}",
            expected_minecraft_dir.display()
        )
    })?;
    if planned_dir != expected_dir {
        eyre::bail!(
            "Completed build plan targets {}, not {}",
            plan.minecraft_dir.display(),
            expected_minecraft_dir.display()
        );
    }
    Ok(crate::jdk::ResolvedJava {
        executable: plan.java.executable,
        home: plan.java.home,
        version_output: plan.java.version_output,
        major_version: plan.java.major_version,
        selection: plan.java.selection,
        pin_url: plan.java.pin_url,
        pin_sha512: plan.java.pin_sha512,
    })
}

fn client_hotswap_class_directories(project_dir: &Path) -> eyre::Result<Vec<PathBuf>> {
    let candidates = [
        project_dir.join("classes"),
        project_dir.join("gametest").join("classes"),
    ];
    let classes_dirs = candidates
        .into_iter()
        .filter(|path| path.is_dir())
        .collect::<Vec<_>>();
    if classes_dirs.is_empty() {
        eyre::bail!(
            "No compiled class directories active in the interactive client exist under {}",
            project_dir.display()
        );
    }
    Ok(classes_dirs)
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
        .arg(class_prefix)
        .args(classes_dirs);
    let output = command
        .output()
        .wrap_err("Failed to launch hotswap helper")?;
    print!("{}", String::from_utf8_lossy(&output.stdout));
    eprint!("{}", String::from_utf8_lossy(&output.stderr));
    if !output.status.success() {
        eyre::bail!("hotswap helper failed with {}", output.status);
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::client_hotswap_class_directories;
    use super::selected_java_from_build_plan;
    use std::fs;

    #[test]
    fn hotswap_uses_java_selected_by_completed_build_for_each_runtime_line() {
        let temp = tempfile::tempdir().expect("tempdir");
        let minecraft_dir = temp.path().join("minecraft");
        fs::create_dir(&minecraft_dir).expect("minecraft dir");

        for major in [17, 21, 25] {
            let plan_path = temp.path().join(format!("java-{major}-plan.json"));
            let java_home = temp.path().join(format!("jdk-{major}"));
            let java_executable = java_home.join("bin").join("java.exe");
            let (selection, pin_url, pin_sha512) = match major {
                17 => ("legacy-discovery", "null", "null"),
                21 => (
                    "exact-lockfile-pin",
                    "\"https://example.test/jbrsdk21.zip\"",
                    "\"test-sha512\"",
                ),
                25 => ("explicit-override", "null", "null"),
                _ => unreachable!("test covers three runtime lines"),
            };
            let plan = format!(
                "{{\"minecraft_dir\":{:?},\"java\":{{\"executable\":{:?},\"home\":{:?},\"version_output\":{:?},\"major_version\":{major},\"selection\":\"{selection}\",\"pin_url\":{pin_url},\"pin_sha512\":{pin_sha512}}},\"unrelated_build_field\":true}}",
                minecraft_dir.to_string_lossy(),
                java_executable.to_string_lossy(),
                java_home.to_string_lossy(),
                format!("openjdk version \"{major}\""),
            );
            fs::write(&plan_path, plan).expect("build plan");

            let selected = selected_java_from_build_plan(&plan_path, &minecraft_dir)
                .expect("selected Java should come from the plan");
            assert_eq!(selected.major_version, major);
            assert_eq!(selected.home, Some(java_home));
            assert_eq!(selected.executable, java_executable);
            assert_eq!(selected.selection, selection);
            assert_eq!(
                selected.pin_url.as_deref(),
                (major == 21).then_some("https://example.test/jbrsdk21.zip")
            );
            assert_eq!(
                selected.pin_sha512.as_deref(),
                (major == 21).then_some("test-sha512")
            );
        }
    }

    #[test]
    fn hotswap_rejects_a_plan_for_another_target() {
        let temp = tempfile::tempdir().expect("tempdir");
        let minecraft_dir = temp.path().join("minecraft");
        let other_minecraft_dir = temp.path().join("other-minecraft");
        fs::create_dir(&minecraft_dir).expect("minecraft dir");
        fs::create_dir(&other_minecraft_dir).expect("other minecraft dir");
        let plan_path = temp.path().join("other-plan.json");
        fs::write(
            &plan_path,
            format!(
                "{{\"minecraft_dir\":{:?},\"java\":{{\"executable\":\"java\",\"home\":null,\"version_output\":\"openjdk version 21\",\"major_version\":21,\"selection\":\"test-selected\",\"pin_url\":null,\"pin_sha512\":null}}}}",
                other_minecraft_dir.to_string_lossy()
            ),
        )
        .expect("build plan");

        let error = selected_java_from_build_plan(&plan_path, &minecraft_dir)
            .expect_err("plan from another target must be rejected");
        assert!(error.to_string().contains("not"));
    }

    #[test]
    fn discovers_every_class_directory_active_in_an_interactive_client() {
        let temp = tempfile::tempdir().expect("tempdir");
        let project = temp.path().join("project");
        let main = project.join("classes");
        let gametest = project.join("gametest").join("classes");
        let datagen = project.join("datagen").join("classes");
        let test = project.join("test").join("classes");
        for directory in [&main, &gametest, &datagen, &test] {
            fs::create_dir_all(directory).expect("class directory");
        }

        assert_eq!(
            client_hotswap_class_directories(&project).expect("interactive class directories"),
            vec![main, gametest]
        );
    }

    #[test]
    fn permits_a_client_without_the_optional_gametest_source_set() {
        let temp = tempfile::tempdir().expect("tempdir");
        let project = temp.path().join("project");
        let main = project.join("classes");
        fs::create_dir_all(&main).expect("main class directory");

        assert_eq!(
            client_hotswap_class_directories(&project).expect("main class directory"),
            vec![main]
        );
    }

    #[test]
    fn rejects_missing_interactive_client_outputs() {
        let temp = tempfile::tempdir().expect("tempdir");

        let error = client_hotswap_class_directories(temp.path())
            .expect_err("missing class directories should fail");

        assert!(error.to_string().contains("No compiled class directories"));
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
