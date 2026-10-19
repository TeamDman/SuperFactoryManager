use super::super::BuildOptions;
use super::super::BuildPlan;
use super::CancellationToken;
use super::Path;
use super::PreparedJunitInputs;
use super::execution_receipt::ExecutionReceipt;
use super::identity;
use super::preflight;
use super::require_complete;
use super::sha256;
use std::path::PathBuf;

fn supported_key(key: &str) -> bool {
    crate::source_projection::projection_catalog::SUPPORTED_TARGETS
        .iter()
        .any(|(target, _)| key == format!("sfm-4.35.0/mc-{target}"))
}

pub(in crate::jar_build::engine) fn enforce_selected(
    options: &BuildOptions,
    cancel: &CancellationToken,
) -> eyre::Result<()> {
    let crate::jar_build::BuildSelection::Projection {
        selector,
        repo_root,
    } = &options.selection
    else {
        return Ok(());
    };
    let (root, keys) =
        crate::jar_build::projection_selection::select_projections(repo_root.as_deref(), selector)?;
    let ids: Vec<_> = keys
        .iter()
        .filter(|key| supported_key(key))
        .filter_map(|key| key.strip_prefix("sfm-4.35.0/mc-").map(str::to_owned))
        .collect();
    if ids.is_empty() {
        return Ok(());
    }
    let report = preflight::run(
        &root,
        &super::super::common_toolchain_cache_dir().join("maven"),
        Some(&ids),
        cancel,
    )?;
    require_complete(&report)
}

pub(in crate::jar_build::engine) fn prepare_for_plan(
    plan: &BuildPlan,
    cancel: &CancellationToken,
) -> eyre::Result<Option<PreparedJunitInputs>> {
    let key = plan.target_label();
    if !plan.identity.is_catalog_owned() || !supported_key(&key) {
        return Ok(None);
    }
    plan.recheck_named_inputs()?;
    plan.recheck_named_sdk()?;
    let ids = vec![key.trim_start_matches("sfm-4.35.0/mc-").to_owned()];
    let mut report = preflight::run(
        plan.repository_root(),
        &plan.maven_cache_dir,
        Some(&ids),
        cancel,
    )?;
    require_complete(&report)?;
    let target = report.targets.remove(0);
    let actual_roots: Vec<_> = plan
        .parsed_dependencies()?
        .iter()
        .filter(|d| d.configuration.starts_with("test"))
        .map(|d| (d.configuration.clone(), d.coordinate.to_string()))
        .collect();
    let expected_roots: Vec<_> = target
        .ordered_test_roots
        .iter()
        .map(|r| (r.configuration.clone(), r.coordinate.clone()))
        .collect();
    let mut actual_sorted = actual_roots;
    actual_sorted.sort();
    let mut expected_sorted = expected_roots;
    expected_sorted.sort();
    eyre::ensure!(
        actual_sorted == expected_sorted,
        "prepared JUnit roots differ from the frozen project roles"
    );
    let owner = plan
        .named_project()
        .expect("catalog project checked")
        .project()
        .receipt();
    let mut prepared = PreparedJunitInputs {
        schema: "sfm:prepared_junit_dependency_inputs@1".to_owned(),
        identity: String::new(),
        target,
        source_snapshot_identity: identity(owner)?,
        java_identity: plan.java.cache_identity(),
        cli_sha256: sha256(&std::fs::read(std::env::current_exe()?)?),
        test_source_lock_sha256: report.test_source_lock_sha256,
        test_provenance_sha256: report.test_provenance_sha256,
        configuration_sha256: report.configuration_sha256,
        artifacts: report.artifacts,
        annotation_processing_enabled: false,
    };
    prepared.identity = identity(&prepared)?;
    Ok(Some(prepared))
}

impl PreparedJunitInputs {
    pub(in crate::jar_build::engine) fn verify_base_classpath(
        &self,
        base: &[PathBuf],
    ) -> eyre::Result<()> {
        for witness in &self.target.production_base_witnesses {
            let artifact = self
                .artifacts
                .iter()
                .find(|a| a.coordinate == witness.coordinate)
                .ok_or_else(|| {
                    eyre::eyre!(
                        "verified production base witness absent: {}",
                        witness.coordinate
                    )
                })?;
            eyre::ensure!(
                base.contains(&PathBuf::from(&artifact.cache_path)),
                "required production base witness missing from actual base classpath: {}",
                witness.coordinate
            );
        }
        Ok(())
    }

    pub(in crate::jar_build::engine) fn classpath(
        &self,
        compile: bool,
    ) -> eyre::Result<Vec<PathBuf>> {
        let bindings = if compile {
            &self.target.classpath_bindings.compile
        } else {
            &self.target.classpath_bindings.runtime
        };
        bindings
            .iter()
            .map(|coordinate| {
                self.artifacts
                    .iter()
                    .find(|a| a.coordinate == *coordinate)
                    .map(|a| PathBuf::from(&a.cache_path))
                    .ok_or_else(|| {
                        eyre::eyre!("ordered JUnit binding has no verified artifact: {coordinate}")
                    })
            })
            .collect()
    }

    pub(in crate::jar_build::engine) fn console(&self) -> eyre::Result<PathBuf> {
        self.artifacts
            .iter()
            .find(|a| a.coordinate == "org.junit.platform:junit-platform-console-standalone:1.8.1")
            .map(|a| PathBuf::from(&a.cache_path))
            .ok_or_else(|| eyre::eyre!("reviewed JUnit console pin absent"))
    }

    pub(in crate::jar_build::engine) fn with_rechecked<T>(
        &self,
        plan: &BuildPlan,
        cancel: &CancellationToken,
        effect: impl FnOnce() -> eyre::Result<T>,
    ) -> eyre::Result<T> {
        checked_effect(
            &self.identity,
            || {
                super::development_compile::prepare_for_compile_plan(plan, cancel)?
                    .map(|current| current.identity)
                    .ok_or_else(|| eyre::eyre!("JUnit adapter scope changed"))
            },
            effect,
        )
    }

    pub(in crate::jar_build::engine) fn recheck(
        &self,
        plan: &BuildPlan,
        cancel: &CancellationToken,
    ) -> eyre::Result<()> {
        self.with_rechecked(plan, cancel, || Ok(()))
    }
}

pub(in crate::jar_build::engine) fn write_execution_receipt(
    prepared: &PreparedJunitInputs,
    plan: &BuildPlan,
    options: &crate::jar_build::RunTestOptions,
    output: &super::super::CancellableOutput,
    summary: String,
    launch_inputs: &super::launch_inputs::LaunchInputs,
    output_path: &Path,
) -> eyre::Result<()> {
    let receipt = ExecutionReceipt {
        schema: "sfm:junit_dependency_execution@1".to_owned(),
        preparation_identity: prepared.identity.clone(),
        projection_key: plan.target_label(),
        cli_sha256: prepared.cli_sha256.clone(),
        java_identity: prepared.java_identity.clone(),
        filter: options.filter.clone(),
        summary,
        exit_status: output.status.to_string(),
        raw_stdout_sha256: sha256(&output.stdout),
        raw_stderr_sha256: sha256(&output.stderr),
        runtime_classpath_sha256: launch_inputs.runtime_classpath_sha256.clone(),
        argfile_sha256: launch_inputs.argfile_sha256.clone(),
        scope: "generated_projection_headless_junit; packaged_candidate_runtime_not_inferred"
            .to_owned(),
    };
    std::fs::write(output_path, facet_json::to_string_pretty(&receipt)?)?;
    Ok(())
}

// The actual optional-test javac subprocess and the mutation regression share
// this small gate. A fresh valid admission may not replace the original one.
pub(super) fn checked_effect<T>(
    expected_identity: &str,
    verify: impl FnOnce() -> eyre::Result<String>,
    effect: impl FnOnce() -> eyre::Result<T>,
) -> eyre::Result<T> {
    let current_identity = verify()?;
    eyre::ensure!(
        expected_identity == current_identity,
        "prepared JUnit inputs changed before Java effects"
    );
    effect()
}
