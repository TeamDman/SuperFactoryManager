use super::CONFIGURATION;
use super::CONFIGURATION_SHA;
use super::CancellationToken;
use super::PROVENANCE_SHA;
use super::Path;
use super::PreflightReport;
use super::SOURCE_SHA;
use super::TestPin;
use super::authority;
use super::cache;
use super::configuration::Configuration;
use super::identity;
use super::issue;
use super::read_pinned;
use super::roots;
use super::source_lock::SourceLock;
use super::target_recipe::TargetRecipe;
use super::target_report::TargetReport;
use crate::source_projection::projection_catalog::SUPPORTED_TARGETS;
use std::time::Instant;

pub(crate) fn preflight(repo: &Path, cancel: &CancellationToken) -> eyre::Result<PreflightReport> {
    run(
        repo,
        &super::super::common_toolchain_cache_dir().join("maven"),
        None,
        cancel,
    )
}

pub(super) fn empty_report() -> PreflightReport {
    PreflightReport {
        schema: "sfm:junit_dependency_preflight@1".to_owned(),
        targets: Vec::new(),
        issues: Vec::new(),
        artifacts: Vec::new(),
        closure_complete: false,
        writes_performed: false,
        java_launched: false,
        live_pom_discovery: false,
        test_source_lock_sha256: SOURCE_SHA.to_owned(),
        test_provenance_sha256: PROVENANCE_SHA.to_owned(),
        configuration_sha256: CONFIGURATION_SHA.to_owned(),
        files_checked: 0,
        bytes_read: 0,
        elapsed_ms: 0,
    }
}

pub(super) fn run(
    repo: &Path,
    cache: &Path,
    targets: Option<&[String]>,
    cancel: &CancellationToken,
) -> eyre::Result<PreflightReport> {
    let started = Instant::now();
    let mut report = empty_report();
    cancel.bail_if_cancelled()?;
    let pins = match authority::load(&mut report, repo) {
        Ok(pins) => pins,
        Err(error) => {
            issue(
                &mut report,
                None,
                "test_only",
                "test_authority_invalid",
                format!("{error:#}"),
            );
            Vec::new()
        }
    };
    let configuration = read_pinned(&mut report, repo, CONFIGURATION, CONFIGURATION_SHA)
        .and_then(|bytes| facet_json::from_slice::<Configuration>(&bytes).map_err(Into::into));
    match configuration {
        Ok(config) => check_targets(&mut report, repo, &config, &pins, targets, cancel)?,
        Err(error) => issue(
            &mut report,
            None,
            "test_only",
            "test_configuration_invalid",
            format!("{error:#}"),
        ),
    }
    if !pins.is_empty() {
        cache::verify_all(&mut report, cache, cancel)?;
    }
    finish(&mut report);
    report.elapsed_ms = u64::try_from(started.elapsed().as_millis()).unwrap_or(u64::MAX);
    Ok(report)
}

fn check_targets(
    report: &mut PreflightReport,
    repo: &Path,
    config: &Configuration,
    pins: &[TestPin],
    selected: Option<&[String]>,
    cancel: &CancellationToken,
) -> eyre::Result<()> {
    let selected_roles = match super::role_selection::RoleSelection::load(report, repo) {
        Ok(selected) => selected,
        Err(error) => {
            issue(
                report,
                None,
                "test_only",
                "test_role_selection_invalid",
                format!("{error:#}"),
            );
            return Ok(());
        }
    };
    for (target, version) in SUPPORTED_TARGETS {
        if selected.is_some_and(|ids| !ids.iter().any(|id| id == target)) {
            continue;
        }
        cancel.bail_if_cancelled()?;
        let recipes: Vec<_> = config
            .targets
            .iter()
            .filter(|r| r.target == target)
            .collect();
        if recipes.len() != 1 || recipes[0].minecraft_version != version {
            issue(
                report,
                Some(target),
                "test_only",
                "test_target_recipe_invalid",
                "missing/ambiguous target recipe or Minecraft version mismatch".to_owned(),
            );
            continue;
        }
        if let Err(error) = check_target(report, repo, recipes[0], pins, &selected_roles) {
            issue(
                report,
                Some(target),
                "test_only",
                "test_target_inputs_invalid",
                format!("{error:#}"),
            );
        }
    }
    Ok(())
}

fn check_target(
    report: &mut PreflightReport,
    repo: &Path,
    recipe: &TargetRecipe,
    common: &[TestPin],
    selected_roles: &super::role_selection::RoleSelection,
) -> eyre::Result<()> {
    let raw = match read_pinned(
        report,
        repo,
        &recipe.source_lock.path,
        &recipe.source_lock.sha256,
    ) {
        Ok(raw) => raw,
        Err(error) => {
            issue(
                report,
                Some(&recipe.target),
                "production",
                "production_source_lock_integrity",
                format!("{error:#}"),
            );
            return Ok(());
        }
    };
    let lock: SourceLock = facet_json::from_slice(&raw)?;
    let (role_selection_identity, role_bindings) =
        super::roles::check(report, repo, recipe, selected_roles)?;
    let production_base_witnesses = roots::production_base_witnesses(report, recipe, &lock)?;
    let mut pins = common.to_vec();
    let roots = roots::validate_target(report, recipe, &lock, &mut pins)?;
    report.targets.push(TargetReport {
        projection_key: format!("sfm-4.35.0/mc-{}", recipe.target),
        target_id: recipe.target.clone(),
        minecraft_version: recipe.minecraft_version.clone(),
        released_recipe_id: recipe.recipe_id.clone(),
        raw_source_lock_sha256: recipe.source_lock.sha256.clone(),
        ordered_test_roots: roots,
        role_selection_identity,
        role_bindings,
        selected_pin_identity: identity(&pins)?,
        selected_pins: pins,
        production_base_witnesses,
        classpath_bindings: super::bindings::for_target(&recipe.target),
        closure_complete: false,
    });
    Ok(())
}

pub(super) fn finish(report: &mut PreflightReport) {
    let invalid: Vec<_> = report
        .targets
        .iter()
        .filter(|target| !exact_pin_set(target))
        .map(|target| target.target_id.clone())
        .collect();
    for target in invalid {
        issue(report, Some(&target), "test_only", "test_only_flat_closure_mismatch",
            "selected pins must exactly equal the reviewed unique coordinate/hash set (24 common, 25 for 26.1.2)".to_owned());
    }
    let invalid_base: Vec<_> = report
        .targets
        .iter()
        .filter(|target| !exact_base_witnesses(target))
        .map(|target| target.target_id.clone())
        .collect();
    for target in invalid_base {
        issue(
            report,
            Some(&target),
            "production",
            "production_base_witness_set_mismatch",
            "JUnit closure requires the single already-pinned jsr305 base witness".to_owned(),
        );
    }
    let invalid_bindings: Vec<_> = report
        .targets
        .iter()
        .filter(|target| {
            target.classpath_bindings != super::bindings::for_target(&target.target_id)
        })
        .map(|target| target.target_id.clone())
        .collect();
    for target in invalid_bindings {
        issue(
            report,
            Some(&target),
            "test_only",
            "test_only_ordered_binding_mismatch",
            "JUnit ordered role bindings differ from the reviewed root-first closure".to_owned(),
        );
    }
    for target in &mut report.targets {
        target.closure_complete = exact_pin_set(target)
            && !report.issues.iter().any(|issue| {
                issue.target_ids.is_empty() || issue.target_ids.contains(&target.target_id)
            });
    }
    report.closure_complete = !report.targets.is_empty()
        && report.issues.is_empty()
        && report.targets.iter().all(|target| target.closure_complete);
}

fn exact_pin_set(target: &TargetReport) -> bool {
    let mut expected = super::approved_pins::COMMON_PINS.to_vec();
    if target.target_id == "26.1.2" {
        expected.push(super::approved_pins::LAUNCHER);
    }
    expected.sort_unstable();
    let mut actual: Vec<_> = target
        .selected_pins
        .iter()
        .map(|p| (p.coordinate.as_str(), p.content_hash.as_str()))
        .collect();
    actual.sort_unstable();
    actual == expected
}

fn exact_base_witnesses(target: &TargetReport) -> bool {
    let base = super::approved_pins::BASE_JSR305;
    target.production_base_witnesses.len() == 1
        && target.production_base_witnesses[0].coordinate == base.0
        && target.production_base_witnesses[0].content_hash == base.1
        && target.production_base_witnesses[0].authority == "existing_production_base_classpath"
}
