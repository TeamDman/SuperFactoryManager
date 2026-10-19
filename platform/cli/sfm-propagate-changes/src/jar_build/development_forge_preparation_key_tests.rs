use super::LoaderToolchainKind;
use super::forge_preparation_project_key;
use crate::source_projection::provenance::sha256;

fn eligible_key(legacy: &str) -> String {
    forge_preparation_project_key(
        legacy,
        true,
        true,
        &LoaderToolchainKind::ForgeGradleForge,
        "1.19.2",
    )
}

#[test]
fn development_forge_preparation_key_has_64_hex_directory_and_differs_from_l_and_m() {
    let legacy = sha256(b"full-source-receipt\nselected-sdk");
    let key = eligible_key(&legacy);
    let directory = key.strip_prefix("sha256:").unwrap();
    assert_eq!(directory.len(), 64);
    assert!(
        directory
            .bytes()
            .all(|byte| byte.is_ascii_digit() || (b'a'..=b'f').contains(&byte))
    );
    assert_eq!(eligible_key(&legacy), key);
    assert_ne!(key, legacy, "L must remain a separate project namespace");
    let n_key =
        sha256(format!("sfm:development_forge_preparation_namespace@1\n{legacy}").as_bytes());
    assert_ne!(
        key, n_key,
        "N project payloads must remain a separate namespace"
    );
    assert_ne!(
        directory,
        format!(
            "{}-forge-preparation-v1",
            legacy.trim_start_matches("sha256:")
        ),
        "M's partial output namespace must remain separate"
    );
}

#[test]
fn development_forge_preparation_key_preserves_full_default_source_and_sdk_sensitivity() {
    // These are the existing default route's full receipt + SDK key inputs.
    let legacy = |source: &str, sdk: &str| sha256(format!("{source}\n{sdk}").as_bytes());
    let original = eligible_key(&legacy("full-source-a", "sdk-a"));
    assert_ne!(original, eligible_key(&legacy("full-source-b", "sdk-a")));
    assert_ne!(original, eligible_key(&legacy("full-source-a", "sdk-b")));
    assert_eq!(original, eligible_key(&legacy("full-source-a", "sdk-a")));
}

#[test]
fn development_forge_preparation_key_leaves_every_other_platform_owner_loader_and_version_unchanged()
 {
    let legacy = sha256(b"existing original namespace");
    for (windows, development, loader, minecraft) in [
        (false, true, LoaderToolchainKind::ForgeGradleForge, "1.19.2"),
        (true, false, LoaderToolchainKind::ForgeGradleForge, "1.19.2"),
        (
            true,
            true,
            LoaderToolchainKind::ForgeGradleNeoForgeGroup,
            "1.19.2",
        ),
        (true, true, LoaderToolchainKind::NeoGradleUserdev, "1.19.2"),
        (true, true, LoaderToolchainKind::ForgeGradleForge, "1.20.3"),
        (true, true, LoaderToolchainKind::ForgeGradleForge, "1.19.4"),
    ] {
        assert_eq!(
            forge_preparation_project_key(&legacy, windows, development, &loader, minecraft),
            legacy
        );
    }
}

#[test]
fn development_forge_preparation_key_wraps_existing_explicit_key_without_changing_root_isolation() {
    // The explicit route keeps stable_overridden_project_namespace unchanged;
    // this helper receives its existing stable key, not a full-source receipt.
    let stable = sha256(b"selected project/profile/lock/sdk explicit-root namespace");
    let key = eligible_key(&stable);
    let component = key.trim_start_matches("sha256:");
    let first = std::path::Path::new("owned-cache-a")
        .join("projects")
        .join(component);
    let second = std::path::Path::new("owned-cache-b")
        .join("projects")
        .join(component);
    assert_ne!(first, second);
    assert_eq!(first.file_name(), second.file_name());
    assert_eq!(first.file_name().unwrap().to_str().unwrap().len(), 64);
    assert_eq!(
        eligible_key(&stable),
        key,
        "an unchanged stable explicit key stays stable"
    );
    assert_ne!(
        eligible_key(&sha256(b"different stable lock/sdk namespace")),
        key
    );
}
