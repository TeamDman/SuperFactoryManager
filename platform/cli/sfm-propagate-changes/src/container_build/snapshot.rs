use super::artifact::put_bytes;
use super::context_manifest::ContextManifest;
use crate::source_projection::catalog_owned_project::CatalogOwnedProject;
use crate::source_projection::core_catalog::read_bounded_input;
use crate::source_projection::provenance::sha256;
use eyre::ensure;
use std::collections::BTreeMap;
use std::path::Path;

pub(super) fn cli_source_identity(root: &Path) -> eyre::Result<String> {
    let files = cli_inputs(root, "platform/cli/sfm-propagate-changes")?;
    let inventory = files
        .iter()
        .map(|(path, bytes)| (path.clone(), sha256(bytes)))
        .collect::<BTreeMap<_, _>>();
    Ok(sha256(facet_json::to_string(&inventory)?.as_bytes()))
}

pub(super) fn cli_inputs(root: &Path, relative: &str) -> eyre::Result<BTreeMap<String, Vec<u8>>> {
    let mut files = BTreeMap::new();
    for name in ["Cargo.toml", "Cargo.lock", "build.rs"] {
        let path = format!("{relative}/{name}");
        if root.join(&path).exists() {
            files.insert(
                path.clone(),
                read_bounded_input(root, &path, 16 * 1024 * 1024)?,
            );
        }
    }
    for entry in walkdir::WalkDir::new(root.join(relative).join("src"))
        .follow_links(false)
        .max_depth(24)
    {
        let entry = entry?;
        ensure!(
            !entry.file_type().is_symlink(),
            "CLI input traverses a symlink"
        );
        if entry.file_type().is_file()
            && entry.path().extension().is_some_and(|ext| {
                ["rs", "java", "py", "js", "css", "liquid", "json"]
                    .iter()
                    .any(|allowed| ext == *allowed)
            })
        {
            let path = entry
                .path()
                .strip_prefix(root)?
                .to_str()
                .ok_or_else(|| eyre::eyre!("non-Unicode CLI input"))?
                .replace('\\', "/");
            ensure!(files.len() < 10000, "CLI source-count budget exceeded");
            files.insert(
                path.clone(),
                read_bounded_input(root, &path, 16 * 1024 * 1024)?,
            );
        }
    }
    Ok(files)
}

pub(super) fn inputs(
    project: &CatalogOwnedProject,
) -> eyre::Result<BTreeMap<String, (Vec<u8>, String)>> {
    let owner = project.ownership_identity()?;
    let mut inputs = BTreeMap::new();
    for (output, artifact) in project.artifacts() {
        for (path, bytes) in [
            (
                format!("snapshot/{}/{output}", project.receipt().project_dir),
                &artifact.output_bytes,
            ),
            (
                format!("snapshot/{}", artifact.source_path),
                &artifact.source_bytes,
            ),
        ] {
            if let Some((existing, _)) = inputs.insert(path.clone(), (bytes.clone(), owner.clone()))
            {
                ensure!(existing == *bytes, "conflicting snapshot input: {path}");
            }
        }
    }
    for relative in [
        "platform/minecraft/projections.json",
        "platform/minecraft/core-liquid-template/project-inputs.json",
        "platform/minecraft/core-liquid-template/feature-definitions.json",
    ] {
        let bytes = read_bounded_input(project.repo_root(), relative, 16 * 1024 * 1024)?;
        inputs.insert(format!("snapshot/{relative}"), (bytes, owner.clone()));
    }
    let configuration = "platform/minecraft/build-configuration/released-native.json";
    if project.repo_root().join(configuration).exists() {
        let bytes = read_bounded_input(project.repo_root(), configuration, 1024 * 1024)?;
        let parent = sha256(&bytes);
        inputs.insert(format!("snapshot/{configuration}"), (bytes, parent));
    }
    for (path, bytes) in cli_inputs(project.repo_root(), "platform/cli/sfm")? {
        inputs.insert(format!("snapshot/{path}"), (bytes, owner.clone()));
    }
    for relative in super::public_test_fixtures::PATHS {
        let bytes = read_bounded_input(project.repo_root(), relative, 128 * 1024)?;
        inputs.insert(format!("snapshot/{relative}"), (bytes, owner.clone()));
    }
    let hashes = inputs
        .iter()
        .map(|(path, (bytes, _))| {
            (
                path.trim_start_matches("snapshot/").to_owned(),
                sha256(bytes),
            )
        })
        .collect::<BTreeMap<_, _>>();
    let manifest = facet_json::to_vec(&hashes)?;
    inputs.insert("snapshot/container-inputs.json".into(), (manifest, owner));
    Ok(inputs)
}
pub(super) fn export(
    project: &CatalogOwnedProject,
    stage: &Path,
    report: &mut ContextManifest,
    max_bytes: u64,
) -> eyre::Result<()> {
    for (path, (bytes, parent)) in inputs(project)? {
        put_bytes(stage, &path, &bytes, &parent, report, max_bytes)?;
    }
    Ok(())
}

/// Export the full bounded authored catalog tree for in-container derivatives.
/// No generated projections, local caches, worlds or Git metadata are copied.
pub(super) fn export_catalog(
    root: &Path,
    stage: &Path,
    report: &mut ContextManifest,
    max_bytes: u64,
) -> eyre::Result<()> {
    let core = "platform/minecraft/core-liquid-template";
    for entry in walkdir::WalkDir::new(root.join(core))
        .follow_links(false)
        .max_depth(32)
    {
        let entry = entry?;
        ensure!(
            !entry.file_type().is_symlink(),
            "catalog input symlink refused"
        );
        if entry.file_type().is_file() {
            let relative = entry
                .path()
                .strip_prefix(root)?
                .to_str()
                .ok_or_else(|| eyre::eyre!("non-Unicode catalog input"))?
                .replace('\\', "/");
            let bytes = read_bounded_input(root, &relative, 16 * 1024 * 1024)?;
            put_bytes(
                stage,
                &format!("snapshot/{relative}"),
                &bytes,
                &sha256(&bytes),
                report,
                max_bytes,
            )?;
        }
    }
    for relative in [
        "platform/minecraft/build-configuration/nfrt.json",
        "platform/minecraft/build-configuration/released-native.json",
    ] {
        if root.join(relative).exists() {
            let bytes = read_bounded_input(root, relative, 1024 * 1024)?;
            put_bytes(
                stage,
                &format!("snapshot/{relative}"),
                &bytes,
                &sha256(&bytes),
                report,
                max_bytes,
            )?;
        }
    }
    Ok(())
}
