//! A tracked, strict source-only supplement for current catalog projections.
//! The emitted dependency lock remains the binary authority. This binding is
//! selected only after its exact lock and project keys have been checked.

use crate::toolchain_lockfile_schema::version::v3::ArtifactLockfileV3;
use crate::toolchain_lockfile_schema::version::v3::SourceProviderV3;
use crate::toolchain_lockfile_schema::version::v3::ToolchainComponentKindV3;
use eyre::Context;
use eyre::ensure;
use facet::Facet;
use std::path::Path;
use std::path::PathBuf;

const BINDING_PATH: &str =
    "platform/minecraft/core-liquid-template/build/supplements/neoform-1211-shared-source.json";
const PROJECT_KEYS: [&str; 2] = [
    "sfm-4.35.0-extended/mc-1.21.1",
    "sfm-4.35.0-issue610/mc-1.21.1",
];
const RECIPE_ID: &str = "sfm:development-native-inputs/1.21.1@1";
const PRIVATE_TREE: &str =
    "build/sfm-toolchain/neoform/1.21.1/classes/gameSourcesWithNeoForge.filetree";

#[derive(Clone, Debug, Facet)]
struct SharedPlatformSourceBinding {
    schema: String,
    base_lock_sha256: String,
    project_keys: Vec<String>,
    producer_recipe: String,
    fingerprint: String,
    private_tree_cache_path: String,
    shared_tree_cache_path: String,
    source_payload_sha256: String,
}

fn selected_project(key: &str) -> bool {
    PROJECT_KEYS.contains(&key)
}

pub(crate) fn still_matches(repo_root: &Path, expected: &str) -> bool {
    let path = repo_root.join(BINDING_PATH);
    if !std::fs::symlink_metadata(&path)
        .is_ok_and(|metadata| metadata.is_file() && !metadata.file_type().is_symlink())
    {
        return false;
    }
    std::fs::read_to_string(path).is_ok_and(|input| {
        let Ok(binding) = facet_json::from_str::<SharedPlatformSourceBinding>(&input) else {
            return false;
        };
        binding_digest(&binding).is_ok_and(|actual| actual == expected)
    })
}

fn binding_digest(binding: &SharedPlatformSourceBinding) -> eyre::Result<String> {
    // Checkout newline/whitespace differences must not change a shared key.
    Ok(crate::source_projection::provenance::sha256(
        facet_json::to_string(binding)?.as_bytes(),
    ))
}

fn sha256_digest(value: &str) -> bool {
    value.strip_prefix("sha256:").is_some_and(|hex| {
        hex.len() == 64
            && hex
                .bytes()
                .all(|byte| byte.is_ascii_digit() || (b'a'..=b'f').contains(&byte))
    })
}

fn checked_binding(
    root: &Path,
    key: &str,
    lock_text: &str,
) -> eyre::Result<(SharedPlatformSourceBinding, String)> {
    let path = root.join(BINDING_PATH);
    ensure!(
        std::fs::symlink_metadata(&path)
            .is_ok_and(|metadata| metadata.is_file() && !metadata.file_type().is_symlink()),
        "tracked source binding must be a regular file"
    );
    let bytes = std::fs::read(&path)
        .wrap_err_with(|| format!("missing tracked source binding {}", path.display()))?;
    let binding: SharedPlatformSourceBinding = facet_json::from_str(std::str::from_utf8(&bytes)?)?;
    ensure!(
        binding.schema == "sfm.shared-platform-source-binding/1",
        "unsupported shared source binding schema"
    );
    ensure!(
        binding.project_keys.len() == PROJECT_KEYS.len()
            && binding.project_keys[0] == PROJECT_KEYS[0]
            && binding.project_keys[1] == PROJECT_KEYS[1],
        "shared source binding changed its reviewed project scope"
    );
    ensure!(
        selected_project(key),
        "source binding does not select this project"
    );
    ensure!(
        sha256_digest(&binding.base_lock_sha256),
        "invalid source binding base lock digest"
    );
    ensure!(
        binding.base_lock_sha256
            == crate::source_projection::provenance::sha256(lock_text.as_bytes()),
        "shared source binding does not match the selected emitted lock"
    );
    ensure!(
        binding.producer_recipe == RECIPE_ID,
        "unexpected source producer recipe"
    );
    ensure!(
        binding.fingerprint == "neogradle-21.1.206",
        "unexpected source mapping fingerprint"
    );
    ensure!(
        binding.private_tree_cache_path == PRIVATE_TREE,
        "unexpected private source path"
    );
    ensure!(
        sha256_digest(&binding.source_payload_sha256),
        "invalid shared source payload digest"
    );
    let portable = &binding.shared_tree_cache_path;
    ensure!(
        portable.starts_with("$sfm-cache/sources/platform/neoforge-21.1.206/minecraft-1.21.1/")
            && portable.ends_with(&format!(
                "gameSourcesWithNeoForge-{}.filetree",
                &binding.source_payload_sha256["sha256:".len()..][..8]
            ))
            && !portable.contains('\\')
            && !portable.contains("..")
            && !portable.contains("//"),
        "shared source binding path is not a safe managed filetree"
    );
    let digest = binding_digest(&binding)?;
    Ok((binding, digest))
}

fn selected_provider<'a>(
    lockfile: &'a mut ArtifactLockfileV3,
    dependency_id: &str,
    component_id: &str,
    provider_id: &str,
    kind: ToolchainComponentKindV3,
) -> eyre::Result<
    &'a mut crate::toolchain_lockfile_schema::version::v3::PlatformPipelineSourceProviderV3,
> {
    let dependency = lockfile
        .dependencies
        .iter_mut()
        .find(|item| item.id == dependency_id)
        .ok_or_else(|| eyre::eyre!("source binding dependency `{dependency_id}` is missing"))?;
    let component = dependency
        .components
        .iter_mut()
        .find(|item| item.id == component_id)
        .ok_or_else(|| {
            eyre::eyre!("source binding component `{dependency_id}/{component_id}` is missing")
        })?;
    let matching = component
        .source_providers
        .iter_mut()
        .filter_map(|item| match item {
            SourceProviderV3::PlatformPipeline(provider) if provider.id == provider_id => {
                Some(provider)
            }
            _ => None,
        })
        .collect::<Vec<_>>();
    ensure!(
        matching.len() == 1,
        "source binding requires one exact platform provider"
    );
    let provider = matching.into_iter().next().expect("one checked provider");
    ensure!(
        provider.declaration.kind == kind,
        "source binding provider kind differs"
    );
    Ok(provider)
}

fn targets() -> [(
    &'static str,
    &'static str,
    &'static str,
    ToolchainComponentKindV3,
); 2] {
    [
        (
            "minecraft",
            "main",
            "minecraft-pipeline",
            ToolchainComponentKindV3::Minecraft,
        ),
        (
            "neoforge",
            "userdev",
            "loader-pipeline",
            ToolchainComponentKindV3::Loader,
        ),
    ]
}

/// Apply a source-only effective binding. The emitted lock text is never edited.
pub(crate) fn apply_for_project(
    repo_root: &Path,
    project_key: &str,
    lock_text: &str,
    lockfile: &mut ArtifactLockfileV3,
) -> eyre::Result<()> {
    if !selected_project(project_key) {
        return Ok(());
    }
    let (binding, digest) = checked_binding(repo_root, project_key, lock_text)?;
    for (dependency, component, id, kind) in targets() {
        let provider = selected_provider(lockfile, dependency, component, id, kind)?;
        ensure!(
            provider.derived_checks.fingerprint == binding.fingerprint,
            "source mapping fingerprint changed"
        );
        ensure!(
            provider.derived_checks.tree_cache_path.as_path()
                == Path::new(&binding.private_tree_cache_path),
            "private source path changed"
        );
        ensure!(
            provider.derived_checks.catalog_projection.is_none()
                && provider.derived_checks.source_jar_sha256.is_none()
                && provider.derived_checks.source_payload_sha256.is_none()
                && provider.derived_checks.source_binding_sha256.is_none(),
            "base lock already declares a different source authority"
        );
        provider.derived_checks.tree_cache_path = PathBuf::from(&binding.shared_tree_cache_path);
        provider.derived_checks.catalog_projection = Some(project_key.to_owned());
        provider.derived_checks.source_payload_sha256 = Some(binding.source_payload_sha256.clone());
        provider.derived_checks.source_binding_sha256 = Some(digest.clone());
    }
    Ok(())
}

/// Keep source-only effective fields out of an emitted dependency-lock edit.
/// An edit to the bound providers itself must use the reviewed supplement path.
pub(crate) fn restore_base_providers(
    repo_root: &Path,
    project_key: &str,
    original_text: &str,
    base: &ArtifactLockfileV3,
    edited: &mut ArtifactLockfileV3,
) -> eyre::Result<()> {
    if !selected_project(project_key) {
        return Ok(());
    }
    let mut expected = base.clone();
    let mut base_providers = base.clone();
    apply_for_project(repo_root, project_key, original_text, &mut expected)?;
    for (dependency, component, id, kind) in targets() {
        let checked = selected_provider(&mut expected, dependency, component, id, kind)?;
        let current = selected_provider(edited, dependency, component, id, kind)?;
        ensure!(
            *current == *checked,
            "edit to a bound source provider requires a reviewed supplement change"
        );
        *current = selected_provider(&mut base_providers, dependency, component, id, kind)?.clone();
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::toolchain_lockfile_schema::ToolchainLockfileDocument;
    use crate::toolchain_lockfile_schema::parse_document;

    const LOCK: &str = include_str!(
        "../../../minecraft/projections/sfm-4.35.0-extended/mc-1.21.1/sfm-toolchain.lock.json"
    );
    const BINDING: &str = include_str!(
        "../../../minecraft/core-liquid-template/build/supplements/neoform-1211-shared-source.json"
    );

    fn fixture() -> eyre::Result<(tempfile::TempDir, ArtifactLockfileV3)> {
        let temporary = tempfile::tempdir()?;
        let path = temporary.path().join(BINDING_PATH);
        std::fs::create_dir_all(path.parent().expect("binding parent"))?;
        std::fs::write(path, BINDING)?;
        let ToolchainLockfileDocument::V4(document) = parse_document(LOCK)? else {
            eyre::bail!("fixture must use schema 4");
        };
        let effective = ArtifactLockfileV3 {
            schema_version: crate::toolchain_lockfile_schema::version::v3::SCHEMA_VERSION,
            platform: document.platform,
            policy: document.policy,
            repositories: document.repositories,
            dependencies: document.dependencies,
            artifacts: document.artifacts,
        };
        Ok((temporary, effective))
    }

    #[test]
    fn exact_current_owners_share_payload_but_retain_distinct_consumers() -> eyre::Result<()> {
        let (temporary, base) = fixture()?;
        let mut extended = base.clone();
        let mut issue = base.clone();
        apply_for_project(temporary.path(), PROJECT_KEYS[0], LOCK, &mut extended)?;
        apply_for_project(temporary.path(), PROJECT_KEYS[1], LOCK, &mut issue)?;
        for (dependency, component, id, kind) in targets() {
            let first = selected_provider(&mut extended, dependency, component, id, kind)?;
            let second = selected_provider(&mut issue, dependency, component, id, kind)?;
            assert_eq!(
                first.derived_checks.tree_cache_path,
                second.derived_checks.tree_cache_path
            );
            assert_eq!(
                first.derived_checks.source_payload_sha256,
                second.derived_checks.source_payload_sha256
            );
            assert_eq!(
                first.derived_checks.source_binding_sha256,
                second.derived_checks.source_binding_sha256
            );
            assert_eq!(
                first.derived_checks.catalog_projection.as_deref(),
                Some(PROJECT_KEYS[0])
            );
            assert_eq!(
                second.derived_checks.catalog_projection.as_deref(),
                Some(PROJECT_KEYS[1])
            );
        }
        restore_base_providers(
            temporary.path(),
            PROJECT_KEYS[0],
            LOCK,
            &base,
            &mut extended,
        )?;
        assert_eq!(extended, base);
        Ok(())
    }

    #[test]
    fn stale_lock_or_binding_and_unreviewed_owner_fail_closed() -> eyre::Result<()> {
        let (temporary, base) = fixture()?;
        let original: SharedPlatformSourceBinding = facet_json::from_str(BINDING)?;
        let original_digest = binding_digest(&original)?;
        assert!(still_matches(temporary.path(), &original_digest));
        let binding_path = temporary.path().join(BINDING_PATH);
        std::fs::write(&binding_path, facet_json::to_string(&original)?)?;
        assert!(still_matches(temporary.path(), &original_digest));
        let mut stale_lock = base.clone();
        assert!(
            apply_for_project(
                temporary.path(),
                PROJECT_KEYS[0],
                &format!("{LOCK}\n"),
                &mut stale_lock
            )
            .is_err()
        );
        assert_eq!(stale_lock, base);
        let mut historical = base.clone();
        apply_for_project(
            temporary.path(),
            "sfm-4.34.0-extended/mc-1.21.1",
            LOCK,
            &mut historical,
        )?;
        assert_eq!(historical, base);
        std::fs::write(
            &binding_path,
            BINDING.replace("2d0981f7.filetree", "00000000.filetree"),
        )?;
        assert!(!still_matches(temporary.path(), &original_digest));
        let mut wrong_binding = base.clone();
        assert!(
            apply_for_project(temporary.path(), PROJECT_KEYS[0], LOCK, &mut wrong_binding).is_err()
        );
        assert_eq!(wrong_binding, base);
        std::fs::remove_file(&binding_path)?;
        let mut missing_binding = base.clone();
        assert!(
            apply_for_project(temporary.path(), PROJECT_KEYS[0], LOCK, &mut missing_binding)
                .is_err()
        );
        assert_eq!(missing_binding, base);
        Ok(())
    }

    #[test]
    fn bound_provider_edit_cannot_leak_into_emitted_lock() -> eyre::Result<()> {
        let (temporary, base) = fixture()?;
        let mut effective = base.clone();
        apply_for_project(temporary.path(), PROJECT_KEYS[0], LOCK, &mut effective)?;
        let provider = selected_provider(
            &mut effective,
            "minecraft",
            "main",
            "minecraft-pipeline",
            ToolchainComponentKindV3::Minecraft,
        )?;
        provider.derived_checks.source_payload_sha256 = Some(format!("sha256:{}", "0".repeat(64)));
        assert!(
            restore_base_providers(
                temporary.path(),
                PROJECT_KEYS[0],
                LOCK,
                &base,
                &mut effective
            )
            .is_err()
        );
        Ok(())
    }
}
