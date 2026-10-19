//! Compile-only binding of the existing reviewed test closure to two dev owners.
//! The target report retains its released authority labels; the source identity
//! and SDK below belong to the actual retained development invocation.
use super::super::BuildPlan;
use super::super::BuildProjectIdentity;
use super::super::LoaderToolchainKind;
use super::CancellationToken;
use super::PreparedJunitInputs;
use super::identity;
use super::preflight;
use super::preflight_report::PreflightReport;
use super::require_complete;
use super::target_report::TargetReport;

pub(in crate::jar_build::engine) fn prepare_for_compile_plan(
    plan: &BuildPlan,
    cancel: &CancellationToken,
) -> eyre::Result<Option<PreparedJunitInputs>> {
    if plan.identity.development_target().is_none() {
        return super::runtime::prepare_for_plan(plan, cancel);
    }
    if development_owner_identity(plan)?.is_none() {
        return Ok(None);
    }
    let ids = vec![plan.minecraft_version.as_str().to_owned()];
    prepare_development_with(plan, cancel, || {
        preflight::run(
            plan.repository_root(),
            &plan.maven_cache_dir,
            Some(&ids),
            cancel,
        )
    })
    .map(Some)
}

pub(super) fn development_owner_identity(plan: &BuildPlan) -> eyre::Result<Option<String>> {
    use crate::source_projection::native_project_target::NATIVE_DEPENDENCY_PROFILE;
    use crate::source_projection::projection_catalog::ProjectionEnvironment;
    let Some(target) = plan.identity.development_target() else {
        return Ok(None);
    };
    let owner = &target.receipt;
    if !matches!(owner.target_id.as_str(), "1.20.3" | "1.20.4")
        || owner.projection_key != format!("sfm-dev/mc-{}", owner.target_id)
    {
        return Ok(None);
    }
    eyre::ensure!(
        owner.environment == ProjectionEnvironment::Dev
            && owner.dependency_profile == NATIVE_DEPENDENCY_PROFILE
            && owner.minecraft_version == owner.target_id
            && owner.minecraft_version == plan.minecraft_version.as_str()
            && owner.loader == "neoforge"
            && plan.loader_toolchain.kind == LoaderToolchainKind::NeoGradleUserdev,
        "development JUnit compile owner/profile/loader/version mismatch"
    );
    Ok(Some(identity(owner)?))
}

pub(super) fn verify_development_roots_and_pins(
    plan: &BuildPlan,
    target: &TargetReport,
) -> eyre::Result<()> {
    let BuildProjectIdentity::Development { project, .. } = &plan.identity else {
        eyre::bail!("development JUnit compile requires its retained source owner");
    };
    // Read the declared profile from retained selected bytes, not a fresh live
    // POM or an independently selected projection's dependency declarations.
    let lock = project.selected_input("sfm-toolchain.lock.json")?;
    let effective =
        crate::toolchain_lockfile_schema::read_current(std::str::from_utf8(lock.output_bytes())?)?;
    let mut actual = Vec::new();
    for row in super::super::project_v3_dependencies(&effective)? {
        if row.configuration.starts_with("test") {
            eyre::ensure!(
                !row.loader_managed(),
                "development JUnit authority cannot replace a loader-managed test root"
            );
            actual.push((row.configuration, row.coordinate.to_string()));
        }
    }
    let mut expected: Vec<_> = target
        .ordered_test_roots
        .iter()
        .map(|row| (row.configuration.clone(), row.coordinate.clone()))
        .collect();
    actual.sort();
    expected.sort();
    eyre::ensure!(
        actual == expected,
        "development JUnit roots differ from the reviewed test authority"
    );
    let pins: std::collections::BTreeMap<_, _> = target
        .selected_pins
        .iter()
        .chain(&target.production_base_witnesses)
        .map(|pin| (pin.coordinate.as_str(), pin.content_hash.as_str()))
        .collect();
    for artifact in &effective.artifacts {
        if let Some(coordinate) = artifact.coordinate.as_deref()
            && let Some(expected) = pins.get(coordinate)
        {
            let expected = super::super::ContentHash::parse(expected).map_err(eyre::Report::msg)?;
            eyre::ensure!(
                artifact.weak.is_none() && artifact.hash == expected,
                "development JUnit declared pin conflicts with reviewed authority: {coordinate}"
            );
        }
    }
    for (_, coordinate) in actual {
        eyre::ensure!(
            effective
                .artifacts
                .iter()
                .any(
                    |artifact| artifact.coordinate.as_deref() == Some(coordinate.as_str())
                        && pins.contains_key(coordinate.as_str())
                ),
            "development JUnit root lacks an equivalent declared pin: {coordinate}"
        );
    }
    Ok(())
}

pub(super) fn prepare_development_with(
    plan: &BuildPlan,
    cancel: &CancellationToken,
    verify_authority: impl FnOnce() -> eyre::Result<PreflightReport>,
) -> eyre::Result<PreparedJunitInputs> {
    cancel.bail_if_cancelled()?;
    let source_snapshot_identity = development_owner_identity(plan)?.ok_or_else(|| {
        eyre::eyre!("projection is outside the reviewed development compile scope")
    })?;
    plan.recheck_named_inputs()?;
    plan.recheck_named_sdk()?;
    let mut report = verify_authority()?;
    require_complete(&report)?;
    eyre::ensure!(
        report.targets.len() == 1,
        "development JUnit authority target is ambiguous"
    );
    let target = report.targets.remove(0);
    eyre::ensure!(
        target.target_id == plan.minecraft_version.as_str()
            && target.minecraft_version == plan.minecraft_version.as_str()
            && target.projection_key == format!("sfm-4.35.0/mc-{}", target.target_id),
        "development JUnit authority target/version mismatch"
    );
    verify_development_roots_and_pins(plan, &target)?;
    let actual_sdk = plan.java.execution_identity.clone().ok_or_else(|| {
        eyre::eyre!("development JUnit compile requires its actual checked SDK identity")
    })?;
    let mut prepared = PreparedJunitInputs {
        schema: "sfm:prepared_junit_dependency_inputs@1".to_owned(),
        identity: String::new(),
        target,
        source_snapshot_identity,
        java_identity: identity(&(plan.java.cache_identity(), actual_sdk))?,
        cli_sha256: super::sha256(&std::fs::read(std::env::current_exe()?)?),
        test_source_lock_sha256: report.test_source_lock_sha256,
        test_provenance_sha256: report.test_provenance_sha256,
        configuration_sha256: report.configuration_sha256,
        artifacts: report.artifacts,
        annotation_processing_enabled: false,
    };
    prepared.identity = identity(&prepared)?;
    Ok(prepared)
}
