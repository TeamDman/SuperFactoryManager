use super::dependency_context::load_inventory;
use crate::cancellation::CancellationToken;
use crate::cli::jar::BranchSelector;
use crate::dependency_inventory::DependencyInventory;
use crate::jar_build::hash::ContentHash;
use crate::jar_build::hash::ContentHashAlgorithm;
use crate::paths::CacheHome;
use crate::terminal_output::stdout_line;
use crate::toolchain_lockfile_schema::version::v3::WeakArtifactValidationV3;
use crate::toolchain_lockfile_write::write_lockfile_atomically;
use eyre::Context;
use facet::Facet;
use figue as args;
use std::io::Cursor;
use std::io::Read;
use std::path::Path;
use std::path::PathBuf;
use zip::ZipArchive;

#[derive(Facet, Debug)]
pub struct DependencyArtifactAcceptArgs {
    /// Logical dependency or dependency/component ID to accept.
    #[facet(args::positional)]
    pub target: String,
    /// Branch selector to update. Must match exactly one worktree.
    #[facet(args::named)]
    pub branch: BranchSelector,
    /// Accept byte drift when stable mod metadata matches.
    #[facet(default = false, args::named)]
    pub weak_mod_metadata: bool,
    /// Metadata entry to validate for weak acceptance.
    #[facet(default, args::named)]
    pub metadata_path: Option<PathBuf>,
    /// Expected mod ID. Defaults to the JAR metadata value.
    #[facet(default, args::named)]
    pub mod_id: Option<String>,
    /// Expected mod version. Defaults to the JAR metadata value.
    #[facet(default, args::named)]
    pub version: Option<String>,
}

impl DependencyArtifactAcceptArgs {
    /// # Errors
    ///
    /// Returns an error when lookup, hashing, metadata validation, or atomic writing fails.
    pub fn invoke(
        self,
        cancellation_token: &CancellationToken,
        cache_home: &CacheHome,
    ) -> eyre::Result<()> {
        cancellation_token.bail_if_cancelled()?;
        let inventory = load_inventory(self.branch.clone(), cache_home)?;
        let report = accept_artifact(inventory, &self)?;
        stdout_line(format!(
            "Accepted {}/{}: {} -> {} ({})",
            report.dependency_id,
            report.component_id,
            report.old_hash,
            report.new_hash,
            if report.weak {
                "weak metadata"
            } else {
                "exact bytes"
            }
        ))?;
        Ok(())
    }
}

struct ArtifactAcceptReport {
    dependency_id: String,
    component_id: String,
    old_hash: ContentHash,
    new_hash: ContentHash,
    weak: bool,
}

fn accept_artifact(
    mut inventory: DependencyInventory,
    args: &DependencyArtifactAcceptArgs,
) -> eyre::Result<ArtifactAcceptReport> {
    let (dependency_id, requested_component) = split_target(&args.target)?;
    let dependency_index = inventory
        .lockfile
        .dependencies
        .iter()
        .position(|dependency| dependency.id == dependency_id)
        .ok_or_else(|| eyre::eyre!("Unknown dependency '{dependency_id}'."))?;
    let component_index = select_component_index(
        &inventory.lockfile.dependencies[dependency_index],
        requested_component,
    )?;
    let component = &inventory.lockfile.dependencies[dependency_index].components[component_index];
    let component_id = component.id.clone();
    let artifact_id = component.derived_checks.artifact_id.clone();
    let artifact_index = inventory
        .lockfile
        .artifacts
        .iter()
        .position(|artifact| artifact.id == artifact_id)
        .expect("v3 validation guarantees artifact references");
    let artifact_path = inventory.local_path(&component.derived_checks.cache_path);
    if !artifact_path.is_file() {
        eyre::bail!("Locked artifact is missing: {}", artifact_path.display());
    }
    let hash = ContentHash::from_path(&artifact_path, ContentHashAlgorithm::Blake3)?;
    let weak = if args.weak_mod_metadata {
        Some(weak_metadata(&artifact_path, args)?)
    } else {
        None
    };
    let old_hash = inventory.lockfile.artifacts[artifact_index].hash;
    inventory.lockfile.artifacts[artifact_index].hash = hash;
    inventory.lockfile.artifacts[artifact_index].weak = weak;
    for dependency in &mut inventory.lockfile.dependencies {
        for component in &mut dependency.components {
            if component.derived_checks.artifact_id == artifact_id {
                component.derived_checks.expected_hash = hash;
            }
        }
    }
    let output = inventory.lockfile.to_canonical_json()?;
    write_lockfile_atomically(
        &inventory.lockfile_path,
        &inventory.original_input,
        output.as_bytes(),
    )?;
    Ok(ArtifactAcceptReport {
        dependency_id: dependency_id.to_owned(),
        component_id,
        old_hash,
        new_hash: hash,
        weak: inventory.lockfile.artifacts[artifact_index].weak.is_some(),
    })
}

fn split_target(target: &str) -> eyre::Result<(&str, Option<&str>)> {
    let mut parts = target.split('/');
    let dependency = parts.next().unwrap_or_default();
    let component = parts.next();
    if dependency.is_empty() || parts.next().is_some() || component.is_some_and(str::is_empty) {
        eyre::bail!("Expected dependency or dependency/component, got '{target}'.");
    }
    Ok((dependency, component))
}

fn select_component_index(
    dependency: &crate::toolchain_lockfile_schema::version::v3::DependencyV3,
    requested: Option<&str>,
) -> eyre::Result<usize> {
    if let Some(requested) = requested {
        return dependency
            .components
            .iter()
            .position(|component| component.id == requested)
            .ok_or_else(|| eyre::eyre!("Unknown component '{}/{}'.", dependency.id, requested));
    }
    if dependency.components.len() == 1 {
        return Ok(0);
    }
    dependency
        .components
        .iter()
        .position(|component| component.id == "main")
        .ok_or_else(|| {
            eyre::eyre!(
                "Dependency '{}' has multiple components; specify dependency/component.",
                dependency.id
            )
        })
}

fn weak_metadata(
    artifact_path: &Path,
    args: &DependencyArtifactAcceptArgs,
) -> eyre::Result<WeakArtifactValidationV3> {
    let metadata_path = match &args.metadata_path {
        Some(path) => path.clone(),
        None => detect_metadata_path(artifact_path)?,
    };
    let metadata = read_zip_text_entry(artifact_path, &metadata_path)?;
    let mod_id = args
        .mod_id
        .clone()
        .or_else(|| metadata_assignment(&metadata, "modId"))
        .ok_or_else(|| eyre::eyre!("{} has no modId assignment", metadata_path.display()))?;
    let version = args
        .version
        .clone()
        .or_else(|| metadata_assignment(&metadata, "version"))
        .ok_or_else(|| eyre::eyre!("{} has no version assignment", metadata_path.display()))?;
    Ok(WeakArtifactValidationV3 {
        metadata_path,
        mod_id,
        version,
    })
}

fn detect_metadata_path(artifact_path: &Path) -> eyre::Result<PathBuf> {
    for candidate in ["META-INF/mods.toml", "META-INF/neoforge.mods.toml"] {
        let path = PathBuf::from(candidate);
        if read_zip_text_entry(artifact_path, &path).is_ok() {
            return Ok(path);
        }
    }
    eyre::bail!(
        "Could not find Forge or NeoForge mod metadata in {}",
        artifact_path.display()
    )
}

fn read_zip_text_entry(path: &Path, entry_name: &Path) -> eyre::Result<String> {
    let entry_name = entry_name.to_string_lossy().replace('\\', "/");
    let bytes =
        std::fs::read(path).wrap_err_with(|| format!("Failed to read {}", path.display()))?;
    let mut archive = ZipArchive::new(Cursor::new(bytes))
        .wrap_err_with(|| format!("Failed to read zip archive {}", path.display()))?;
    let mut entry = archive
        .by_name(&entry_name)
        .wrap_err_with(|| format!("Archive {} missing {entry_name}", path.display()))?;
    let mut content = String::new();
    entry
        .read_to_string(&mut content)
        .wrap_err_with(|| format!("Failed to read {entry_name} from {}", path.display()))?;
    Ok(content)
}

fn metadata_assignment(content: &str, key: &str) -> Option<String> {
    content.lines().find_map(|line| {
        let line = line.split('#').next()?.trim();
        let (left, right) = line.split_once('=')?;
        (left.trim() == key).then(|| right.trim().trim_matches('"').trim_matches('\'').to_owned())
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::branch_targets::BranchName;
    use crate::branch_targets::WorktreePath;
    use crate::branch_targets::WorktreeTarget;
    use crate::toolchain_lockfile_schema::read_current;

    #[test]
    fn accept_uses_injected_cache_and_updates_all_artifact_checks() {
        let directory = tempfile::tempdir().expect("temp directory");
        let cache_home = CacheHome(directory.path().join("isolated-cache"));
        let input = include_str!("../../../../../minecraft/sfm-toolchain.lock.json");
        let lockfile_path = directory.path().join("sfm-toolchain.lock.json");
        std::fs::write(&lockfile_path, input).expect("lockfile fixture");
        let inventory = DependencyInventory {
            target: WorktreeTarget {
                branch: BranchName::from("1.19.2"),
                worktree_path: WorktreePath::from(directory.path().to_path_buf()),
                core: true,
                mc_version: None,
            },
            lockfile_path: lockfile_path.clone(),
            cache_home,
            original_input: input.to_owned(),
            lockfile: read_current(input).expect("v3 fixture"),
        };
        let component = &inventory
            .lockfile
            .dependencies
            .iter()
            .find(|dependency| dependency.id == "cc-tweaked")
            .expect("CC:Tweaked fixture")
            .components[0];
        let artifact_id = component.derived_checks.artifact_id.clone();
        let artifact_path = inventory.local_path(&component.derived_checks.cache_path);
        std::fs::create_dir_all(artifact_path.parent().expect("artifact parent"))
            .expect("cache fixture");
        std::fs::write(&artifact_path, b"controlled artifact bytes").expect("artifact fixture");

        let report = accept_artifact(inventory, &args("cc-tweaked")).expect("accept artifact");

        let expected =
            ContentHash::from_bytes(b"controlled artifact bytes", ContentHashAlgorithm::Blake3);
        assert_eq!(report.new_hash, expected);
        let written =
            read_current(&std::fs::read_to_string(lockfile_path).expect("updated lockfile read"))
                .expect("updated v3 lockfile");
        assert_eq!(
            written
                .artifacts
                .iter()
                .find(|artifact| artifact.id == artifact_id)
                .expect("updated artifact")
                .hash,
            expected
        );
        assert!(
            written
                .dependencies
                .iter()
                .flat_map(|dependency| &dependency.components)
                .filter(|component| component.derived_checks.artifact_id == artifact_id)
                .all(|component| component.derived_checks.expected_hash == expected)
        );
    }

    #[test]
    fn target_requires_an_unambiguous_dependency_component_shape() {
        assert_eq!(
            split_target("cc-tweaked").expect("dependency"),
            ("cc-tweaked", None)
        );
        assert_eq!(
            split_target("cc-tweaked/main").expect("component"),
            ("cc-tweaked", Some("main"))
        );
        let _error =
            split_target("cc-tweaked/main/extra").expect_err("too many target segments must fail");
        let _error = split_target("cc-tweaked/").expect_err("empty component must fail");
    }

    fn args(target: &str) -> DependencyArtifactAcceptArgs {
        DependencyArtifactAcceptArgs {
            target: target.to_owned(),
            branch: BranchSelector::from("1.19.2".to_owned()),
            weak_mod_metadata: false,
            metadata_path: None,
            mod_id: None,
            version: None,
        }
    }
}
