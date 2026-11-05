//! Bind the reviewed test-only closure to nine plain historical source owners.
//! Frozen product locks and the production resolver remain unchanged.
use super::super::BuildPlan;
use super::identity;
use super::source_lock::SourceLock;
use super::target_report::TargetReport;

const TARGETS: [&str; 9] = [
    "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
];

pub(super) fn target(key: &str) -> Option<&str> {
    key.strip_prefix("sfm-4.34.0/mc-")
        .filter(|version| TARGETS.contains(version))
}

pub(super) fn verify_owner(plan: &BuildPlan) -> eyre::Result<()> {
    let key = plan.target_label();
    let version =
        target(&key).ok_or_else(|| eyre::eyre!("outside reviewed historical JUnit scope"))?;
    let minecraft = crate::source_projection::projection_catalog::SUPPORTED_TARGETS
        .iter()
        .find(|(id, _)| *id == version)
        .map(|(_, minecraft)| *minecraft)
        .ok_or_else(|| eyre::eyre!("historical JUnit target has no Minecraft identity"))?;
    let project = plan
        .named_project()
        .ok_or_else(|| eyre::eyre!("historical JUnit requires its checked frozen project"))?;
    let owner = project.project().receipt();
    super::super::validate_first_named_compile(project)?;
    let loader = match version {
        "1.19.2" | "1.19.4" | "1.20" => super::super::LoaderToolchainKind::ForgeGradleForge,
        "1.20.1" => super::super::LoaderToolchainKind::ForgeGradleNeoForgeGroup,
        _ => super::super::LoaderToolchainKind::NeoGradleUserdev,
    };
    eyre::ensure!(
        owner.environment
            == crate::source_projection::projection_catalog::ProjectionEnvironment::Release
            && matches!(owner.loader.as_str(), "forge" | "neoforge")
            && owner.target_id == version
            && owner.minecraft_version == plan.minecraft_version.as_str()
            && owner.minecraft_version == minecraft
            && project.receipt().project_mod_version == "4.34.0"
            && plan.loader_toolchain.kind == loader,
        "historical JUnit owner/version/loader mismatch"
    );
    // This admits only the established exact product recipes. It does not
    // add test pins to them or turn their portable receipts into capabilities.
    Ok(())
}

pub(super) fn verify_pins(plan: &BuildPlan, authority: &TargetReport) -> eyre::Result<()> {
    let project = plan
        .named_project()
        .ok_or_else(|| eyre::eyre!("historical JUnit lost its frozen source owner"))?;
    let owner = project.project().receipt();
    eyre::ensure!(
        authority.target_id == owner.target_id
            && authority.minecraft_version == owner.minecraft_version
            && authority.projection_key == format!("sfm-4.35.0/mc-{}", owner.target_id)
            && authority.closure_complete,
        "historical JUnit authority target/version differs from its actual source owner"
    );
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
        "sfm:historical_junit_binding@2",
        identity(project.receipt())?,
        target.selected_pin_identity.clone(),
    ))
}

#[cfg(all(test, any(windows, target_os = "linux")))]
#[path = "historical_tests.rs"]
mod tests;
