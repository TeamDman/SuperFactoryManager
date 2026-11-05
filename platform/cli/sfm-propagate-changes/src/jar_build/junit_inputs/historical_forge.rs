//! Bind the reviewed test-only closure to the four plain historical Forge owners.
//! Frozen product locks and the production resolver remain unchanged.
use super::super::BuildPlan;
use super::identity;
use super::source_lock::SourceLock;
use super::target_report::TargetReport;

pub(super) fn target(key: &str) -> Option<&str> {
    key.strip_prefix("sfm-4.34.0/mc-")
        .filter(|version| matches!(*version, "1.19.2" | "1.19.4" | "1.20" | "1.20.1"))
}

pub(super) fn verify_owner(plan: &BuildPlan) -> eyre::Result<()> {
    let key = plan.target_label();
    let version =
        target(&key).ok_or_else(|| eyre::eyre!("outside historical Forge JUnit scope"))?;
    let project = plan
        .named_project()
        .ok_or_else(|| eyre::eyre!("historical JUnit requires its checked frozen project"))?;
    let owner = project.project().receipt();
    let recipe = super::super::named_forge_compile_recipe_for_project(project)?;
    eyre::ensure!(
        owner.environment
            == crate::source_projection::projection_catalog::ProjectionEnvironment::Release
            && matches!(owner.loader.as_str(), "forge" | "neoforge")
            && owner.target_id == version
            && owner.minecraft_version == plan.minecraft_version.as_str()
            && owner.minecraft_version == version
            && project.receipt().project_mod_version == "4.34.0"
            && plan.loader_toolchain.kind == super::super::named_forge_loader_kind(recipe),
        "historical Forge JUnit owner/version/loader mismatch"
    );
    // This admits only the established four exact product recipes. It does not
    // add test pins to them or turn their portable receipts into capabilities.
    Ok(())
}

pub(super) fn verify_pins(plan: &BuildPlan, authority: &TargetReport) -> eyre::Result<()> {
    let project = plan
        .named_project()
        .ok_or_else(|| eyre::eyre!("historical JUnit lost its frozen source owner"))?;
    let source: SourceLock =
        facet_json::from_slice(project.dependencies().raw_source_lock_bytes())?;
    verify_product_pin_overlap(&source, authority)
}

fn verify_product_pin_overlap(source: &SourceLock, authority: &TargetReport) -> eyre::Result<()> {
    let pins = super::authority::pin_map(source)?;
    // Absence of a test-only transitive (such as Javassist) is valid. A product
    // pin for the same exact coordinate may never be silently overridden.
    for pin in authority
        .selected_pins
        .iter()
        .chain(&authority.production_base_witnesses)
    {
        if let Some(actual) = pins.get(&pin.coordinate) {
            eyre::ensure!(
                actual == &pin.content_hash,
                "historical product pin conflicts with reviewed JUnit authority: {}",
                pin.coordinate
            );
        }
    }
    // The ordinary classpath still supplies the existing production witness.
    for pin in &authority.production_base_witnesses {
        eyre::ensure!(
            pins.get(&pin.coordinate) == Some(&pin.content_hash),
            "historical product lacks the reviewed JUnit base witness: {}",
            pin.coordinate
        );
    }
    Ok(())
}

pub(super) fn bind_identity(plan: &BuildPlan, target: &TargetReport) -> eyre::Result<String> {
    verify_owner(plan)?;
    verify_pins(plan, target)?;
    let project = plan.named_project().expect("owner checked above");
    identity(&(
        "sfm:historical_forge_junit_binding@1",
        identity(project.receipt())?,
        target.selected_pin_identity.clone(),
    ))
}

#[cfg(all(test, any(windows, target_os = "linux")))]
#[path = "historical_forge_tests.rs"]
mod tests;
