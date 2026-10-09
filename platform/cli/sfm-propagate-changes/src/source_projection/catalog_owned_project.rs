//! Ownership of exact catalog-selected project inputs.
//!
//! Dependency interpretation is deliberately separate. Schema-2 recipe and
//! schema-4 profile callers can validate collected lock bytes before invoking
//! the same ownership check. The explicit development preparation method uses
//! the guarded sync transaction; release ownership stays check-only. No build,
//! cache, JDK or acquisition capability is returned. Historical files are never read.

use super::candidate_lock::checked_directory;
use super::candidate_lock::checked_file;
use super::context::ProjectionContext;
use super::core_catalog::CoreCatalog;
use super::core_catalog::read_bounded_catalog_input;
use super::core_features::FEATURE_DEFINITIONS_PATH;
use super::core_inputs::BuildTargetMetadata;
use super::core_inputs::CORE_METADATA_PATH;
use super::core_inputs::CORE_ROOT;
use super::core_inputs::CoreProjectInputs;
use super::core_inputs::CoreSelection;
use super::core_inputs::MAX_CORE_FILE_BYTES;
use super::core_inputs::MAX_CORE_METADATA_BYTES;
use super::core_inputs::MAX_CORE_PROJECTION_BYTES;
use super::core_inputs::collect_core_artifacts;
use super::core_inputs::discover_core_source_files;
use super::core_inputs::select_core_inputs;
use super::named_root::catalog_projection_root;
use super::projection_catalog::CATALOG_PATH;
use super::projection_catalog::ProjectionEnvironment;
use super::projection_catalog::validate_projection_key;
use super::provenance::sha256;
use super::sync::CatalogProjectionIdentity;
use super::sync::ProjectedArtifact;
use super::sync::SyncMode;
use super::sync::sync_catalog_projection;
use eyre::Result;
use eyre::WrapErr;
use eyre::ensure;
use facet::Facet;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::fs;
use std::io::Read as _;
use std::path::Component;
use std::path::Path;
use std::path::PathBuf;

const RECEIPT_SCHEMA: &str = "sfm:catalog_owned_project@1";
const MAX_SOURCE_ENTRIES: usize = 131_072;

/// Portable ownership evidence, not a dependency profile or build result.
#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub struct CatalogOwnedProjectReceipt {
    pub schema: String,
    pub scope: String,
    pub projection_key: String,
    pub environment: ProjectionEnvironment,
    pub target_id: String,
    pub minecraft_version: String,
    pub java_major: u16,
    pub loader: String,
    pub context_identity: String,
    pub project_dir: String,
    pub catalog_sha256: String,
    pub feature_definitions_sha256: String,
    pub project_inputs_sha256: String,
    pub authored_source_inventory_sha256: String,
    pub generated_source_inventory_sha256: String,
    pub files: BTreeMap<String, CatalogOwnedFileReceipt>,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub struct CatalogOwnedFileReceipt {
    pub authored_input: String,
    pub declared_template: bool,
    pub source_sha256: String,
    pub source_bytes: u64,
    pub output_sha256: String,
    pub output_bytes: u64,
}

/// Collected authored bytes have not yet established generated ownership.
/// Private fields prevent callers from substituting an arbitrary source map.
#[derive(Debug)]
pub struct CollectedCatalogProject {
    loaded: CoreCatalog,
    context: ProjectionContext,
    identity: CatalogProjectionIdentity,
    metadata_bytes: Vec<u8>,
    core: PathBuf,
    source_inventory: BTreeSet<String>,
    selection: CoreSelection,
    artifacts: BTreeMap<String, ProjectedArtifact>,
}

/// A checked immutable snapshot, not a filesystem lock or execution permit.
/// Callers must recheck it after preparation and immediately before effects.
#[derive(Debug)]
pub struct CatalogOwnedProject {
    collected: CollectedCatalogProject,
    project_root: PathBuf,
    receipt: CatalogOwnedProjectReceipt,
}

/// Borrowed selected bytes bound to the checked project and its output path.
/// This view is not constructible from a path or an independent byte vector.
#[derive(Clone, Copy, Debug)]
pub struct CatalogOwnedInput<'a> {
    output_path: &'a str,
    artifact: &'a ProjectedArtifact,
    declared_template: bool,
}

impl CatalogOwnedInput<'_> {
    #[must_use]
    pub fn output_path(&self) -> &str {
        self.output_path
    }
    #[must_use]
    pub fn source_path(&self) -> &str {
        &self.artifact.source_path
    }
    #[must_use]
    pub fn source_bytes(&self) -> &[u8] {
        &self.artifact.source_bytes
    }
    #[must_use]
    pub fn output_bytes(&self) -> &[u8] {
        &self.artifact.output_bytes
    }
    /// The selected authoring metadata flag, not an inference from byte equality.
    /// Every Java output still uses the controlled Java renderer independently.
    #[must_use]
    pub fn declared_template(&self) -> bool {
        self.declared_template
    }

    /// Require an exact-copy role, with no rendering or normalization.
    ///
    /// # Errors
    /// Rejects a template-enabled role, including unchanged rendering, or any
    /// role whose raw authored and projected bytes differ.
    pub fn require_exact_copy(&self) -> Result<&[u8]> {
        ensure!(
            !self.declared_template,
            "selected role input declares template rendering: {}",
            self.output_path
        );
        ensure!(
            self.artifact.source_bytes == self.artifact.output_bytes,
            "selected role input is not an exact copy: {}",
            self.output_path
        );
        Ok(&self.artifact.source_bytes)
    }
}

impl CollectedCatalogProject {
    /// Apply the existing guarded development transaction using collected bytes.
    /// Release callers must use `check_current` instead. Fresh input checks bind
    /// profile validation, generation and returned ownership to one snapshot.
    pub(crate) fn prepare_development(self) -> Result<CatalogOwnedProject> {
        ensure!(
            self.identity.environment == ProjectionEnvironment::Dev,
            "development preparation cannot synchronize a release projection"
        );
        self.recheck_authored_snapshot()?;
        let root = catalog_projection_root(
            &self.loaded.repo_root,
            &self.loaded.catalog,
            &self.identity.projection_key,
            &self.artifacts,
        )?;
        sync_catalog_projection(&root, &self.identity, &self.artifacts, SyncMode::Apply)?;
        self.check_current()
    }

    #[must_use]
    pub fn context(&self) -> &ProjectionContext {
        &self.context
    }
    #[must_use]
    pub fn identity(&self) -> &CatalogProjectionIdentity {
        &self.identity
    }
    #[must_use]
    pub fn build_target(&self) -> &BuildTargetMetadata {
        &self.selection.build_target
    }
    /// Borrow the immutable collected map for existing pure profile validation.
    #[must_use]
    pub(crate) fn artifacts(&self) -> &BTreeMap<String, ProjectedArtifact> {
        &self.artifacts
    }

    /// Read already-collected output bytes for early schema/profile validation.
    /// This does not claim that the generated project exists or is current.
    ///
    /// # Errors
    /// Rejects an output absent from this exact selected artifact set.
    pub fn output_bytes(&self, output: &str) -> Result<&[u8]> {
        Ok(&self
            .artifacts
            .get(output)
            .ok_or_else(|| eyre::eyre!("catalog does not select output `{output}`"))?
            .output_bytes)
    }

    /// Verify current named ownership without synchronizing or acquiring.
    ///
    /// # Errors
    /// Rejects changed authored inputs, invalid root policy, unowned source
    /// files/reparse points, stale/conflicting outputs and changed provenance.
    #[tracing::instrument(name = "catalog.check_current", skip_all)]
    pub fn check_current(self) -> Result<CatalogOwnedProject> {
        self.recheck_authored_snapshot()?;
        let project_root = catalog_projection_root(
            &self.loaded.repo_root,
            &self.loaded.catalog,
            &self.identity.projection_key,
            &self.artifacts,
        )?;
        let source_inventory = inspect_generated_sources(&project_root, &self.artifacts)?;
        sync_catalog_projection(&project_root, &self.identity, &self.artifacts, SyncMode::Check)
            .wrap_err("catalog ownership requires a current generated project; use the existing named source workflow")?;
        let mut files = BTreeMap::new();
        let mut total = 0_u64;
        for (output, artifact) in &self.artifacts {
            let actual = read_checked(&project_root, output, MAX_CORE_FILE_BYTES)?;
            add_budget(&mut total, actual.len())?;
            ensure!(
                actual == artifact.output_bytes,
                "catalog-owned generated output changed during check: {output}"
            );
            files.insert(
                output.clone(),
                CatalogOwnedFileReceipt {
                    authored_input: artifact.source_path.clone(),
                    declared_template: selected_template_mode(&self.selection, output)?,
                    source_sha256: sha256(&artifact.source_bytes),
                    source_bytes: artifact.source_bytes.len() as u64,
                    output_sha256: sha256(&artifact.output_bytes),
                    output_bytes: artifact.output_bytes.len() as u64,
                },
            );
        }
        ensure!(
            inspect_generated_sources(&project_root, &self.artifacts)? == source_inventory,
            "catalog generated source inventory changed during check"
        );
        self.recheck_authored_snapshot()?;
        let receipt = CatalogOwnedProjectReceipt {
            schema: RECEIPT_SCHEMA.to_owned(),
            scope: "read_only_selected_input_ownership_not_native_build".to_owned(),
            projection_key: self.identity.projection_key.clone(),
            environment: self.identity.environment,
            target_id: self.identity.target_id.clone(),
            minecraft_version: self.identity.minecraft_version.clone(),
            java_major: self.selection.build_target.java_major,
            loader: self.selection.build_target.loader.clone(),
            context_identity: self.identity.context_identity.clone(),
            project_dir: portable_path(
                &self
                    .loaded
                    .catalog
                    .project_dir(&self.identity.projection_key)?,
            )?,
            catalog_sha256: self.loaded.catalog_sha256.clone(),
            feature_definitions_sha256: self.loaded.feature_definitions_sha256.clone(),
            project_inputs_sha256: sha256(&self.metadata_bytes),
            authored_source_inventory_sha256: inventory_digest(&self.source_inventory)?,
            generated_source_inventory_sha256: inventory_digest(&source_inventory)?,
            files,
        };
        Ok(CatalogOwnedProject {
            collected: self,
            project_root: checked_directory(&project_root)?,
            receipt,
        })
    }

    #[tracing::instrument(name = "catalog.recheck_authored", skip_all)]
    fn recheck_authored_snapshot(&self) -> Result<()> {
        ensure!(
            self.loaded.catalog_sha256
                == sha256(&read_bounded_catalog_input(
                    &self.loaded.repo_root,
                    CATALOG_PATH
                )?)
                && self.loaded.feature_definitions_sha256
                    == sha256(&read_bounded_catalog_input(
                        &self.loaded.repo_root,
                        FEATURE_DEFINITIONS_PATH
                    )?)
                && read_checked(
                    &self.loaded.repo_root,
                    CORE_METADATA_PATH,
                    MAX_CORE_METADATA_BYTES
                )? == self.metadata_bytes
                && discover_core_source_files(&self.core)? == self.source_inventory,
            "catalog authored metadata or source inventory changed during collection"
        );
        let mut total = 0_u64;
        for artifact in self.artifacts.values() {
            let bytes = read_checked(
                &self.loaded.repo_root,
                &artifact.source_path,
                MAX_CORE_FILE_BYTES,
            )?;
            add_budget(&mut total, bytes.len())?;
            ensure!(
                bytes == artifact.source_bytes,
                "selected core-owned source bytes changed: {}",
                artifact.source_path
            );
        }
        Ok(())
    }
}

impl CatalogOwnedProject {
    #[must_use]
    pub fn receipt(&self) -> &CatalogOwnedProjectReceipt {
        &self.receipt
    }
    #[must_use]
    pub fn repo_root(&self) -> &Path {
        &self.collected.loaded.repo_root
    }
    #[must_use]
    pub fn project_root(&self) -> &Path {
        &self.project_root
    }
    #[must_use]
    pub fn identity(&self) -> &CatalogProjectionIdentity {
        &self.collected.identity
    }
    #[must_use]
    pub fn build_target(&self) -> &BuildTargetMetadata {
        &self.collected.selection.build_target
    }

    pub(crate) fn artifacts(&self) -> &BTreeMap<String, ProjectedArtifact> {
        &self.collected.artifacts
    }

    /// Borrow only an exact selected generated output and its authored source.
    ///
    /// # Errors
    /// Rejects any unselected output; no path-based filesystem fallback exists.
    pub fn selected_input(&self, output: &str) -> Result<CatalogOwnedInput<'_>> {
        let (path, artifact) = self
            .collected
            .artifacts
            .get_key_value(output)
            .ok_or_else(|| eyre::eyre!("catalog project does not select `{output}`"))?;
        Ok(CatalogOwnedInput {
            output_path: path,
            artifact,
            declared_template: selected_template_mode(&self.collected.selection, path)?,
        })
    }

    /// Resolve a reviewed authored role only through its selected output.
    ///
    /// # Errors
    /// Rejects an unselected/ambiguous source path or any rendered byte change.
    /// Matching a raw authored file hash alone does not establish selection.
    pub fn exact_authored_input(&self, source_path: &str) -> Result<CatalogOwnedInput<'_>> {
        let input = self.authored_input(source_path)?;
        input.require_exact_copy()?;
        Ok(input)
    }

    /// Resolve one selected authored input without allowing arbitrary file reads.
    /// Rendering policy remains the responsibility of the role-specific caller.
    ///
    /// # Errors
    /// Rejects unsafe, unselected or ambiguous authored paths.
    pub(crate) fn authored_input(&self, source_path: &str) -> Result<CatalogOwnedInput<'_>> {
        validate_projection_key(source_path)?;
        let mut matches = self
            .collected
            .artifacts
            .iter()
            .filter(|(_, artifact)| artifact.source_path == source_path);
        let (output, artifact) = matches
            .next()
            .ok_or_else(|| eyre::eyre!("reviewed authored role is not selected: {source_path}"))?;
        ensure!(
            matches.next().is_none(),
            "reviewed authored role has ambiguous selected outputs: {source_path}"
        );
        let input = CatalogOwnedInput {
            output_path: output,
            artifact,
            declared_template: selected_template_mode(&self.collected.selection, output)?,
        };
        Ok(input)
    }

    /// Hash portable ownership evidence, without a cache or dependency policy.
    ///
    /// # Errors
    /// Returns an error if the typed receipt cannot be serialized.
    pub fn ownership_identity(&self) -> Result<String> {
        Ok(sha256(facet_json::to_string(&self.receipt)?.as_bytes()))
    }

    /// Recheck exact input/owner/output bytes before a later preparation gate.
    ///
    /// # Errors
    /// Rejects changed input bytes, catalog context, policy, source membership
    /// or provenance. This does not hold a filesystem lock across effects.
    #[tracing::instrument(name = "catalog.recheck", skip_all)]
    pub fn recheck(&self) -> Result<()> {
        #[cfg(test)]
        recheck_observation::notify(self);
        // Rendering is deterministic for these exact retained input bytes.
        // Re-read content and membership, never trust mtimes or skip effects'
        // rechecks, but do not parse/render the same full tree at every launch.
        self.collected.recheck_authored_snapshot()?;
        let root = catalog_projection_root(
            self.repo_root(),
            &self.collected.loaded.catalog,
            &self.receipt.projection_key,
            &self.collected.artifacts,
        )?;
        ensure!(
            checked_directory(&root)? == self.project_root
                && inventory_digest(&inspect_generated_sources(&root, self.artifacts())?)?
                    == self.receipt.generated_source_inventory_sha256,
            "checked catalog project root or generated membership changed"
        );
        sync_catalog_projection(
            &root,
            &self.collected.identity,
            self.artifacts(),
            SyncMode::Check,
        )?;
        for (output, artifact) in self.artifacts() {
            ensure!(
                read_checked(&root, output, MAX_CORE_FILE_BYTES)? == artifact.output_bytes,
                "checked catalog generated output changed: {output}"
            );
        }
        self.collected.recheck_authored_snapshot()?;
        ensure!(
            inventory_digest(&inspect_generated_sources(&root, self.artifacts())?)?
                == self.receipt.generated_source_inventory_sha256,
            "checked catalog generated membership changed during recheck"
        );
        Ok(())
    }
}

/// Test-only measurement and deterministic fixture mutation. This callback
/// returns no authority; the unchanged real recheck always runs afterwards.
#[cfg(test)]
pub(crate) mod recheck_observation {
    use super::CatalogOwnedProject;
    use std::cell::RefCell;
    use std::marker::PhantomData;
    use std::rc::Rc;

    type Observer = Box<dyn FnMut(&CatalogOwnedProject)>;
    thread_local! {
        static OBSERVER: RefCell<Option<Observer>> = const { RefCell::new(None) };
    }

    pub(crate) struct Scope {
        _same_thread: PhantomData<Rc<()>>,
    }

    pub(crate) fn install(observer: impl FnMut(&CatalogOwnedProject) + 'static) -> Scope {
        OBSERVER.with(|slot| {
            let mut slot = slot.borrow_mut();
            assert!(slot.is_none(), "test recheck observer already installed");
            *slot = Some(Box::new(observer));
        });
        Scope {
            _same_thread: PhantomData,
        }
    }

    pub(super) fn notify(project: &CatalogOwnedProject) {
        OBSERVER.with(|slot| {
            if let Some(observer) = slot.borrow_mut().as_mut() {
                observer(project);
            }
        });
    }

    impl Drop for Scope {
        fn drop(&mut self) {
            OBSERVER.with(|slot| {
                slot.borrow_mut().take();
            });
        }
    }
}

/// Collect only the current core-owned catalog context and immutable bytes.
///
/// Callers validate the intended dependency interpretation before `check_current`;
/// this collector never parses, migrates or rewrites a toolchain lock schema.
///
/// # Errors
/// Rejects invalid catalog/features/core metadata, unsafe selected input paths,
/// oversized reads, invalid templates and incomplete standalone project inputs.
#[tracing::instrument(name = "catalog.collect", skip_all, fields(projection_key))]
pub fn collect_catalog_project(
    repo_root: &Path,
    invocation_dir: &Path,
    projection_key: &str,
) -> Result<CollectedCatalogProject> {
    let loaded = CoreCatalog::load(repo_root, invocation_dir)?;
    let context = loaded.context(projection_key)?;
    let identity = CatalogProjectionIdentity::from_catalog(&loaded.catalog, projection_key)?;
    let metadata_bytes = read_checked(
        &loaded.repo_root,
        CORE_METADATA_PATH,
        MAX_CORE_METADATA_BYTES,
    )?;
    let metadata = CoreProjectInputs::from_json(
        std::str::from_utf8(&metadata_bytes)?,
        &loaded.registered_features,
    )?;
    let core = checked_directory(&loaded.repo_root.join(CORE_ROOT))?;
    let source_inventory = discover_core_source_files(&core)?;
    let selection = select_core_inputs(&metadata, &context, &source_inventory)?;
    let artifacts = collect_core_artifacts(&core, &selection, &context)?;
    let collected = CollectedCatalogProject {
        loaded,
        context,
        identity,
        metadata_bytes,
        core,
        source_inventory,
        selection,
        artifacts,
    };
    collected.recheck_authored_snapshot()?;
    Ok(collected)
}

fn selected_template_mode(selection: &CoreSelection, output: &str) -> Result<bool> {
    Ok(selection
        .inputs
        .get(output)
        .ok_or_else(|| eyre::eyre!("catalog artifact has no selected input metadata: {output}"))?
        .template)
}

fn inspect_generated_sources(
    project_root: &Path,
    artifacts: &BTreeMap<String, ProjectedArtifact>,
) -> Result<BTreeSet<String>> {
    let project = checked_directory(project_root)?;
    let expected = artifacts
        .keys()
        .filter(|path| path.starts_with("src/"))
        .cloned()
        .collect::<BTreeSet<_>>();
    let mut src_present = false;
    for entry in fs::read_dir(&project)? {
        let entry = entry?;
        if entry
            .file_name()
            .to_str()
            .is_some_and(|name| name.eq_ignore_ascii_case("src"))
        {
            ensure!(
                entry.file_name() == "src",
                "catalog source root has a case alias"
            );
            src_present = true;
        }
    }
    if !src_present {
        ensure!(
            expected.is_empty(),
            "catalog generated source root is absent"
        );
        return Ok(BTreeSet::new());
    }
    let source = checked_directory(&project.join("src"))?;
    ensure!(
        source.starts_with(&project),
        "catalog source root escaped its project"
    );
    let mut pending = vec![("src".to_owned(), source)];
    let mut paths = BTreeSet::new();
    let mut case_components = BTreeMap::new();
    let mut entries = 0_usize;
    while let Some((parent, directory)) = pending.pop() {
        for entry in fs::read_dir(&directory)? {
            let entry = entry?;
            entries = entries
                .checked_add(1)
                .ok_or_else(|| eyre::eyre!("catalog source entry counter overflow"))?;
            ensure!(
                entries <= MAX_SOURCE_ENTRIES,
                "catalog generated source inventory exceeds its bounded entry limit"
            );
            let name = entry.file_name();
            let name = name
                .to_str()
                .ok_or_else(|| eyre::eyre!("catalog source name is not UTF-8"))?;
            let relative = format!("{parent}/{name}");
            validate_projection_key(&relative)?;
            let folded = relative.to_ascii_lowercase();
            if let Some(previous) = case_components.insert(folded, relative.clone()) {
                ensure!(
                    previous == relative,
                    "catalog source inventory contains case-only aliases"
                );
            }
            let metadata = fs::symlink_metadata(entry.path())?;
            if metadata.is_dir() {
                let child = checked_directory(&entry.path())?;
                ensure!(
                    child.starts_with(&project),
                    "catalog source directory escaped its project"
                );
                pending.push((relative, child));
            } else {
                ensure!(
                    metadata.is_file(),
                    "catalog source input is not a regular file: {relative}"
                );
                checked_file(&project, &relative)?;
                ensure!(paths.insert(relative), "duplicate catalog source input");
            }
        }
    }
    ensure!(
        paths == expected,
        "catalog source inventory contains missing or extra unowned source inputs; native directory scans are refused"
    );
    Ok(paths)
}

fn inventory_digest(paths: &BTreeSet<String>) -> Result<String> {
    Ok(sha256(facet_json::to_string(paths)?.as_bytes()))
}
fn add_budget(total: &mut u64, bytes: usize) -> Result<()> {
    *total = total
        .checked_add(bytes as u64)
        .ok_or_else(|| eyre::eyre!("catalog input byte budget overflow"))?;
    ensure!(
        *total <= MAX_CORE_PROJECTION_BYTES,
        "catalog input snapshot exceeds bounded byte budget"
    );
    Ok(())
}
fn read_checked(root: &Path, relative: &str, limit: u64) -> Result<Vec<u8>> {
    let path = checked_file(root, relative)?;
    let file = fs::File::open(path)
        .wrap_err_with(|| format!("cannot read catalog-owned input `{relative}`"))?;
    ensure!(
        file.metadata()?.len() <= limit,
        "catalog-owned input exceeds byte limit: {relative}"
    );
    let mut bytes = Vec::new();
    file.take(limit + 1).read_to_end(&mut bytes)?;
    ensure!(
        bytes.len() as u64 <= limit,
        "catalog-owned input grew beyond byte limit: {relative}"
    );
    Ok(bytes)
}
fn portable_path(path: &Path) -> Result<String> {
    path.components()
        .map(|part| match part {
            Component::Normal(name) => name
                .to_str()
                .map(str::to_owned)
                .ok_or_else(|| eyre::eyre!("catalog project path is not UTF-8")),
            _ => Err(eyre::eyre!(
                "catalog project path is not an exact relative path"
            )),
        })
        .collect::<Result<Vec<_>>>()
        .map(|parts| parts.join("/"))
}

#[cfg(test)]
pub(crate) mod tests {
    use super::super::core_inputs::InputPredicate;
    use super::super::core_inputs::InputVariant;
    use super::super::projection_catalog::SUPPORTED_TARGETS;
    use super::*;
    use std::process::Command;

    const LOCKS: [(&str, &[u8]); 10] = [
        (
            "1.19.2",
            include_bytes!(
                "../../../../minecraft/core-liquid-template/build/lockfiles/1.19.2/schema-2.json"
            ),
        ),
        (
            "1.19.4",
            include_bytes!(
                "../../../../minecraft/core-liquid-template/build/lockfiles/1.19.4/schema-2.json"
            ),
        ),
        (
            "1.20",
            include_bytes!(
                "../../../../minecraft/core-liquid-template/build/lockfiles/1.20/schema-2.json"
            ),
        ),
        (
            "1.20.1",
            include_bytes!(
                "../../../../minecraft/core-liquid-template/build/lockfiles/1.20.1/schema-2.json"
            ),
        ),
        (
            "1.20.2",
            include_bytes!(
                "../../../../minecraft/core-liquid-template/build/lockfiles/1.20.2/schema-2.json"
            ),
        ),
        (
            "1.20.3",
            include_bytes!(
                "../../../../minecraft/core-liquid-template/build/lockfiles/1.20.3/schema-2.json"
            ),
        ),
        (
            "1.20.4",
            include_bytes!(
                "../../../../minecraft/core-liquid-template/build/lockfiles/1.20.4/schema-2.json"
            ),
        ),
        (
            "1.21.0",
            include_bytes!(
                "../../../../minecraft/core-liquid-template/build/lockfiles/1.21.0/schema-2.json"
            ),
        ),
        (
            "1.21.1",
            include_bytes!(
                "../../../../minecraft/core-liquid-template/build/lockfiles/1.21.1/schema-2.json"
            ),
        ),
        (
            "26.1.2",
            include_bytes!(
                "../../../../minecraft/core-liquid-template/build/lockfiles/26.1.2/schema-2.json"
            ),
        ),
    ];
    const RAW_V4: &[u8] = include_bytes!(
        "../../../../minecraft/core-liquid-template/build/lockfiles/1.19.2/schema-4.json"
    );
    const JAVA: &str = "src/main/java/Shared.java";
    const ROLE_INPUT: &str = "build/roles/active.gradle";
    const ROLE_OUTPUT: &str = "gradle/active-role.gradle";
    const INACTIVE_ROLE: &str = "build/roles/inactive.gradle";
    const RENDERED_ROLE: &str = "build/roles/rendered.gradle";
    const RENDERED_OUTPUT: &str = "gradle/rendered-role.gradle";
    const ROLE_BYTES: &[u8] = b"// immutable role\r\n";

    pub(crate) struct Fixture {
        temp: tempfile::TempDir,
        metadata: CoreProjectInputs,
    }
    impl Fixture {
        pub(crate) fn new() -> Self {
            Self::new_selected(None)
        }

        pub(crate) fn new_for_target(target: &str) -> Self {
            assert!(SUPPORTED_TARGETS.iter().any(|(id, _)| *id == target));
            Self::new_selected(Some(target))
        }

        fn new_selected(selected_target: Option<&str>) -> Self {
            let temp = tempfile::Builder::new()
                .prefix("sfm owned project fixture ")
                .tempdir()
                .unwrap();
            gix::init(temp.path()).unwrap();
            fs::write(
                temp.path().join(".gitignore"),
                "/platform/minecraft/projections/scratch/\n",
            )
            .unwrap();
            let core = temp.path().join(CORE_ROOT);
            fs::create_dir_all(core.join("src/main/java")).unwrap();
            fs::write(core.join(JAVA),
                "package proof;\n{% if features.review_toggle %}\nclass Shared { int enabled; }\n{% else %}\nclass Shared {}\n{% endif %}\n").unwrap();
            for (relative, bytes) in [
                ("src/main/resources/fixture.bin", &[0, 255, 13, 10][..]),
                (
                    "src/generated/resources/fixture.json",
                    b"{\"fixture\":true}\n",
                ),
                (
                    "src/main/antlr/fixture/Fixture.g4",
                    b"grammar Fixture; value: EOF;\n",
                ),
                ("src/gametest/resources/fixture.txt", b"test fixture\n"),
            ] {
                let path = core.join(relative);
                fs::create_dir_all(path.parent().unwrap()).unwrap();
                fs::write(path, bytes).unwrap();
            }
            let definitions = format!(
                r#"{{"review_toggle":{{"supported_targets":{},"requires":[]}}}}"#,
                facet_json::to_string(
                    &SUPPORTED_TARGETS
                        .iter()
                        .map(|(id, _)| *id)
                        .collect::<Vec<_>>()
                )
                .unwrap(),
            );
            fs::write(core.join("feature-definitions.json"), definitions).unwrap();
            let mut entries = Vec::new();
            let mut metadata = CoreProjectInputs {
                schema_version: 1,
                targets: BTreeMap::new(),
                source_rules: BTreeMap::new(),
                project_files: BTreeMap::new(),
            };
            for (slot, (target, actual)) in SUPPORTED_TARGETS.iter().enumerate() {
                if selected_target.is_some_and(|selected| selected != *target) {
                    continue;
                }
                let loader = if matches!(*target, "1.19.2" | "1.19.4" | "1.20") {
                    "forge"
                } else {
                    "neoforge"
                };
                let java_major = if *target == "26.1.2" {
                    25
                } else if matches!(*target, "1.21.0" | "1.21.1") {
                    21
                } else {
                    17
                };
                metadata.targets.insert(
                    (*target).to_owned(),
                    BuildTargetMetadata {
                        java_major,
                        loader: loader.to_owned(),
                    },
                );
                for environment in ["release", "dev"] {
                    let key = Self::key(slot, environment);
                    entries.push(format!(
                        r#""{key}":{{"minecraft_version":"{actual}","environment":"{environment}","features":[]}}"#
                    ));
                }
                let predicate = InputPredicate {
                    targets: vec![(*target).to_owned()],
                    ..Default::default()
                };
                for (output, name, bytes) in [
                    (
                        "gradle.properties",
                        "properties",
                        format!(
                            "minecraft_version={actual}\nneo_version=43.4.0\nmod_version=4.34.0\n"
                        )
                        .into_bytes(),
                    ),
                    (
                        "settings.gradle",
                        "settings",
                        format!("rootProject.name = 'sfm-{target}'\n").into_bytes(),
                    ),
                    (
                        "sfm-toolchain.lock.json",
                        "lock",
                        LOCKS
                            .iter()
                            .find(|row| row.0 == *target)
                            .unwrap()
                            .1
                            .to_vec(),
                    ),
                ] {
                    let input = format!("build/fixture/{target}/{name}");
                    Self::insert(
                        &core,
                        &mut metadata,
                        output,
                        &input,
                        &bytes,
                        predicate.clone(),
                        false,
                    );
                }
            }
            for (output, bytes) in [
                ("build.gradle", &b"// no executable fixture build\n"[..]),
                ("gradlew", &b"fixture wrapper\n"[..]),
                ("gradlew.bat", &b"fixture wrapper\r\n"[..]),
                (
                    "gradle/wrapper/gradle-wrapper.properties",
                    &b"distributionUrl=https\\://example.invalid/gradle-7.5-bin.zip\n"[..],
                ),
                ("gradle/wrapper/gradle-wrapper.jar", &[0, 255, 42][..]),
            ] {
                let input = format!("build/fixture/shared/{output}");
                Self::insert(
                    &core,
                    &mut metadata,
                    output,
                    &input,
                    bytes,
                    InputPredicate::default(),
                    false,
                );
            }
            Self::insert(
                &core,
                &mut metadata,
                ROLE_OUTPUT,
                ROLE_INPUT,
                ROLE_BYTES,
                InputPredicate::default(),
                false,
            );
            let inactive = core.join(INACTIVE_ROLE);
            fs::create_dir_all(inactive.parent().unwrap()).unwrap();
            fs::write(inactive, ROLE_BYTES).unwrap();
            Self::insert(
                &core,
                &mut metadata,
                RENDERED_OUTPUT,
                RENDERED_ROLE,
                b"{% if features.review_toggle %}\n// enabled\n{% endif %}\n// rendered role\n",
                InputPredicate::default(),
                true,
            );
            fs::write(
                temp.path().join(CATALOG_PATH),
                format!("{{{}}}", entries.join(",")),
            )
            .unwrap();
            fs::write(
                temp.path().join(CORE_METADATA_PATH),
                facet_json::to_string_pretty(&metadata).unwrap(),
            )
            .unwrap();
            Self { temp, metadata }
        }
        pub(crate) fn key(slot: usize, environment: &str) -> String {
            if environment == "dev" {
                format!("scratch/arbitrary/slot-{slot}")
            } else {
                format!("published/nested/slot-{slot}")
            }
        }
        pub(crate) fn collect(&self, key: &str) -> Result<CollectedCatalogProject> {
            collect_catalog_project(self.temp.path(), self.temp.path(), key)
        }
        pub(crate) fn publish(&self, key: &str) -> PathBuf {
            let collected = self.collect(key).unwrap();
            let root = catalog_projection_root(
                &collected.loaded.repo_root,
                &collected.loaded.catalog,
                key,
                &collected.artifacts,
            )
            .unwrap();
            sync_catalog_projection(
                &root,
                &collected.identity,
                &collected.artifacts,
                SyncMode::Apply,
            )
            .unwrap();
            root
        }
        fn insert(
            core: &Path,
            metadata: &mut CoreProjectInputs,
            output: &str,
            input: &str,
            bytes: &[u8],
            when: InputPredicate,
            template: bool,
        ) {
            let path = core.join(input);
            fs::create_dir_all(path.parent().unwrap()).unwrap();
            fs::write(path, bytes).unwrap();
            metadata
                .project_files
                .entry(output.to_owned())
                .or_default()
                .push(InputVariant {
                    input: input.to_owned(),
                    when,
                    template,
                });
        }
        fn source(&self, relative: &str) -> PathBuf {
            self.temp.path().join(CORE_ROOT).join(relative)
        }
        pub(crate) fn repository(&self) -> &Path {
            self.temp.path()
        }
        /// Render imported original roles with their real registered feature context.
        /// Toy sources retain `review_toggle`; neither imported bytes nor template
        /// modes are replaced by pre-rendered copies.
        pub(crate) fn import_feature_context(
            &self,
            original: &CoreCatalog,
            original_key: &str,
            fixture_key: &str,
        ) -> Result<()> {
            use super::super::core_features::CoreFeatureDefinitions;
            use super::super::projection_catalog::ProjectionCatalog;

            let path = self
                .temp
                .path()
                .join(super::super::core_features::FEATURE_DEFINITIONS_PATH);
            let mut definitions = CoreFeatureDefinitions::from_json(&fs::read_to_string(&path)?)?;
            for (name, definition) in &original.definitions.0 {
                ensure!(
                    !definitions.0.contains_key(name),
                    "fixture feature collision: {name}"
                );
                definitions.0.insert(name.clone(), definition.clone());
            }
            definitions.validate()?;
            let registered = definitions.registered_names();
            let catalog_path = self.temp.path().join(CATALOG_PATH);
            let mut catalog =
                ProjectionCatalog::from_json(&fs::read_to_string(&catalog_path)?, &registered)?;
            let original_entry = original
                .catalog
                .0
                .get(original_key)
                .ok_or_else(|| eyre::eyre!("original fixture context missing"))?;
            let entry = catalog
                .0
                .get_mut(fixture_key)
                .ok_or_else(|| eyre::eyre!("destination fixture context missing"))?;
            ensure!(
                entry.minecraft_version == original_entry.minecraft_version,
                "fixture feature context belongs to another Minecraft version"
            );
            entry.features.clone_from(&original_entry.features);
            definitions.validate_entry(entry)?;
            fs::write(path, facet_json::to_string_pretty(&definitions)?)?;
            fs::write(catalog_path, catalog.to_json(&registered)?)?;
            Ok(())
        }
        /// Replace only one target's selected project role in this temp fixture.
        /// The input is core-relative; other target variants remain unchanged.
        pub(crate) fn set_project_file_for(
            &mut self,
            target: &str,
            output: &str,
            input: &str,
            bytes: &[u8],
            template: bool,
        ) -> Result<()> {
            self.set_project_files_for(target, [(output, input, bytes, template)])
        }

        /// Assemble a fixture transaction, validating and serializing its
        /// metadata once rather than once per imported file.
        pub(crate) fn set_project_files_for<'a>(
            &mut self,
            target: &str,
            files: impl IntoIterator<Item = (&'a str, &'a str, &'a [u8], bool)>,
        ) -> Result<()> {
            ensure!(
                SUPPORTED_TARGETS.iter().any(|(id, _)| *id == target),
                "fixture override has an unknown target"
            );
            for (output, input, bytes, template) in files {
                validate_projection_key(output)?;
                validate_projection_key(input)?;
                let reused_elsewhere =
                    self.metadata
                        .project_files
                        .values()
                        .flatten()
                        .any(|variant| {
                            variant.input == input
                                && (variant.when.targets.is_empty()
                                    || variant.when.targets.iter().any(|id| id != target))
                        });
                if reused_elsewhere {
                    ensure!(
                        fs::read(self.source(input))? == bytes,
                        "fixture target override would change another target's authored bytes"
                    );
                }
                let variants = self
                    .metadata
                    .project_files
                    .entry(output.to_owned())
                    .or_default();
                for variant in variants.iter_mut() {
                    ensure!(
                        variant.when.all_features.is_empty()
                            && variant.when.any_features.is_empty()
                            && variant.when.none_features.is_empty(),
                        "fixture override cannot rewrite a feature-dependent role"
                    );
                    if variant.when.targets.is_empty() {
                        variant.when.targets = SUPPORTED_TARGETS
                            .iter()
                            .map(|(id, _)| (*id).to_owned())
                            .collect();
                    }
                    variant.when.targets.retain(|id| id != target);
                }
                variants.retain(|variant| !variant.when.targets.is_empty());
                Self::insert(
                    &self.temp.path().join(CORE_ROOT),
                    &mut self.metadata,
                    output,
                    input,
                    bytes,
                    InputPredicate {
                        targets: vec![target.to_owned()],
                        ..Default::default()
                    },
                    template,
                );
            }
            self.metadata
                .validate(&BTreeSet::from(["review_toggle".to_owned()]))?;
            fs::write(
                self.temp.path().join(CORE_METADATA_PATH),
                facet_json::to_string_pretty(&self.metadata)?,
            )?;
            Ok(())
        }
        fn root(&self, key: &str) -> PathBuf {
            self.temp
                .path()
                .join("platform/minecraft/projections")
                .join(key)
        }
    }

    #[test]
    fn all20_catalog_contexts_check_exact_schema2_bytes_without_native_interpretation() -> Result<()>
    {
        let fixture = Fixture::new();
        let mut verified = 0;
        for (slot, (target, actual)) in SUPPORTED_TARGETS.iter().enumerate() {
            for environment in ["release", "dev"] {
                let key = Fixture::key(slot, environment);
                let collected = fixture.collect(&key)?;
                assert_eq!(collected.identity().target_id, *target);
                assert_eq!(collected.context().minecraft_version, *actual);
                assert!(!collected.context().features["review_toggle"]);
                let lock = LOCKS.iter().find(|row| row.0 == *target).unwrap().1;
                assert_eq!(collected.output_bytes("sfm-toolchain.lock.json")?, lock);
                assert!(collected.check_current().is_err());
                assert!(!fixture.root(&key).exists());
                let root = fixture.publish(&key);
                let manifest_before = fs::read(root.join(JAVA))?;
                let checked = fixture.collect(&key)?.check_current()?;
                assert_eq!(checked.receipt().schema, RECEIPT_SCHEMA);
                assert_eq!(checked.receipt().environment.as_str(), environment);
                assert_eq!(checked.receipt().target_id, *target);
                assert_eq!(checked.receipt().minecraft_version, *actual);
                assert_eq!(
                    checked.receipt().project_dir,
                    format!("platform/minecraft/projections/{key}")
                );
                assert_eq!(checked.receipt().files.len(), 15);
                assert_eq!(
                    checked
                        .selected_input("sfm-toolchain.lock.json")?
                        .require_exact_copy()?,
                    lock
                );
                assert_eq!(
                    checked
                        .selected_input("src/main/resources/fixture.bin")?
                        .output_bytes(),
                    &[0, 255, 13, 10]
                );
                checked.recheck()?;
                assert_eq!(fs::read(root.join(JAVA))?, manifest_before);
                assert!(!root.join("build").exists());
                assert!(
                    !fixture
                        .temp
                        .path()
                        .join("platform/minecraft/build")
                        .exists()
                );
                verified += 1;
            }
        }
        assert_eq!(verified, 20);
        Ok(())
    }

    #[test]
    fn existing_v4_preflight_keeps_released_schema2_refusal_before_named_check() -> Result<()> {
        let fixture = Fixture::new();
        for slot in 0..10 {
            for environment in ["release", "dev"] {
                let key = Fixture::key(slot, environment);
                let collected = fixture.collect(&key)?;
                let bytes = collected.output_bytes("sfm-toolchain.lock.json")?;
                assert!(matches!(
                    crate::toolchain_lockfile_schema::parse_document(std::str::from_utf8(bytes)?)?,
                    crate::toolchain_lockfile_schema::ToolchainLockfileDocument::V2 { .. }
                ));
                let error = super::super::native_project_target::preflight_native_project(
                    fixture.temp.path(),
                    fixture.temp.path(),
                    &key,
                    super::super::native_project_target::NATIVE_DEPENDENCY_PROFILE,
                )
                .unwrap_err();
                assert!(
                    error.to_string().contains("genuine released-input adapter"),
                    "{error:?}"
                );
                assert!(!fixture.root(&key).exists());
            }
        }
        Ok(())
    }

    #[test]
    fn selected_role_inputs_refuse_unselected_equal_hash_and_rendered_sources() -> Result<()> {
        let fixture = Fixture::new();
        let key = Fixture::key(0, "release");
        fixture.publish(&key);
        let checked = fixture.collect(&key)?.check_current()?;
        let source_path = format!("{CORE_ROOT}/{ROLE_INPUT}");
        let role = checked.exact_authored_input(&source_path)?;
        assert_eq!(role.output_path(), ROLE_OUTPUT);
        assert_eq!(role.source_path(), source_path);
        assert_eq!(role.require_exact_copy()?, ROLE_BYTES);
        assert!(!role.declared_template());
        assert!(!checked.receipt().files[ROLE_OUTPUT].declared_template);
        assert_eq!(role.source_bytes(), role.output_bytes());
        let inactive = fs::read(fixture.source(INACTIVE_ROLE))?;
        assert_eq!(sha256(&inactive), sha256(role.source_bytes()));
        assert!(
            checked
                .exact_authored_input(&format!("{CORE_ROOT}/{INACTIVE_ROLE}"))
                .is_err()
        );
        assert!(
            checked
                .exact_authored_input(&format!("{CORE_ROOT}/{RENDERED_ROLE}"))
                .is_err()
        );
        assert!(
            checked
                .selected_input(RENDERED_OUTPUT)?
                .require_exact_copy()
                .is_err()
        );
        assert!(checked.exact_authored_input("../escaped").is_err());
        assert!(
            checked
                .selected_input("src/main/java/Unselected.java")
                .is_err()
        );
        assert!(
            checked
                .exact_authored_input("platform/minecraft/release-baselines/fixture.gradle")
                .is_err()
        );
        Ok(())
    }

    #[test]
    fn unchanged_template_enabled_role_is_not_an_exact_copy_contract() -> Result<()> {
        let mut fixture = Fixture::new_for_target("1.19.2");
        let key = Fixture::key(0, "release");
        let input = "build/roles/unchanged-template.gradle";
        let output = "gradle/unchanged-template.gradle";
        let bytes = b"// no Liquid directives, but template mode is explicitly enabled\n";
        fixture.set_project_file_for("1.19.2", output, input, bytes, true)?;
        fixture.publish(&key);
        let checked = fixture.collect(&key)?.check_current()?;
        let view = checked.selected_input(output)?;
        assert_eq!(view.source_bytes(), bytes);
        assert_eq!(view.output_bytes(), bytes);
        assert!(view.declared_template());
        assert!(checked.receipt().files[output].declared_template);
        let error = view.require_exact_copy().unwrap_err();
        assert!(error.to_string().contains("declares template rendering"));
        assert!(
            checked
                .exact_authored_input(&format!("{CORE_ROOT}/{input}"))
                .is_err()
        );
        checked.recheck()?;
        Ok(())
    }

    #[test]
    fn selected_roles_refuse_ambiguous_authored_aliases_without_new_reads() -> Result<()> {
        let fixture = Fixture::new_for_target("1.19.2");
        let key = Fixture::key(0, "release");
        fixture.publish(&key);
        let mut checked = fixture.collect(&key)?.check_current()?;
        // Only this child test can access the private storage. Public callers
        // cannot forge this handle or construct an owned-input view.
        let artifact = checked.collected.artifacts[ROLE_OUTPUT].clone();
        checked
            .collected
            .artifacts
            .insert("gradle/forged-alias.gradle".to_owned(), artifact);
        assert!(
            checked
                .exact_authored_input(&format!("{CORE_ROOT}/{ROLE_INPUT}"))
                .is_err()
        );
        Ok(())
    }

    #[test]
    fn every_generated_source_file_requires_manifest_ownership_before_scan() -> Result<()> {
        for extra in [
            "src/main/java/Injected.java",
            "src/main/resources/extra.bin",
            "src/main/antlr/fixture/Extra.g4",
            "src/unregistered/source.input",
            "src/gametest/resources/extra.txt",
        ] {
            let fixture = Fixture::new_for_target("1.19.2");
            let key = Fixture::key(0, "release");
            let root = fixture.publish(&key);
            let path = root.join(extra);
            fs::create_dir_all(path.parent().unwrap())?;
            fs::write(&path, b"unowned input")?;
            let before = fs::read(root.join(JAVA))?;
            let error = fixture.collect(&key)?.check_current().unwrap_err();
            assert!(
                error.to_string().contains("extra unowned source inputs"),
                "{error:?}"
            );
            assert_eq!(fs::read(path)?, b"unowned input");
            assert_eq!(fs::read(root.join(JAVA))?, before);
            assert!(!root.join("build").exists());
        }
        Ok(())
    }

    #[test]
    fn retained_recheck_refuses_same_size_preserved_timestamp_content_edits() -> Result<()> {
        for authored in [true, false] {
            let fixture = Fixture::new_for_target("1.19.2");
            let key = Fixture::key(0, "dev");
            let root = fixture.publish(&key);
            let checked = fixture.collect(&key)?.check_current()?;
            checked.recheck()?;
            assert_eq!(
                fixture.collect(&key)?.check_current()?.receipt(),
                checked.receipt()
            );
            let path = if authored {
                fixture.temp.path().join(CORE_ROOT).join(JAVA)
            } else {
                root.join(JAVA)
            };
            let before = fs::metadata(&path)?;
            let mut bytes = fs::read(&path)?;
            bytes[0] ^= 1;
            fs::write(&path, &bytes)?;
            fs::OpenOptions::new()
                .write(true)
                .open(&path)?
                .set_times(fs::FileTimes::new().set_modified(before.modified()?))?;
            assert_eq!(fs::metadata(&path)?.len(), before.len());
            assert_eq!(fs::metadata(&path)?.modified()?, before.modified()?);
            assert!(checked.recheck().is_err());
            assert_eq!(fs::read(path)?, bytes);
            assert!(!root.join("build").exists());
        }
        Ok(())
    }

    #[test]
    fn retained_development_preparation_refreshes_ignored_disposable_outputs() -> Result<()> {
        let fixture = Fixture::new_for_target("1.19.2");
        let key = Fixture::key(0, "dev");
        let checked = fixture.collect(&key)?.prepare_development()?;
        checked.recheck()?;
        let changed = checked.project_root().join(JAVA);
        let expected = fs::read(&changed)?;
        fs::write(&changed, b"class ContributorEdit {}\n")?;
        assert!(checked.recheck().is_err());
        fixture.collect(&key)?.prepare_development()?.recheck()?;
        assert_eq!(fs::read(changed)?, expected);
        Ok(())
    }

    #[test]
    fn retained_development_preparation_refuses_release_and_changed_authored_input_before_writes()
    -> Result<()> {
        for environment in ["release", "dev"] {
            let fixture = Fixture::new_for_target("1.19.2");
            let key = Fixture::key(0, environment);
            let collected = fixture.collect(&key)?;
            let root = catalog_projection_root(
                &collected.loaded.repo_root,
                &collected.loaded.catalog,
                &key,
                &collected.artifacts,
            )?;
            if environment == "dev" {
                fs::write(
                    fixture.temp.path().join(CORE_ROOT).join(JAVA),
                    b"class ChangedDuringPreparation {}\n",
                )?;
            }
            assert!(!root.exists());
            assert!(collected.prepare_development().is_err());
            assert!(!root.exists());
        }
        Ok(())
    }

    #[test]
    fn collection_and_checked_handle_refuse_changed_snapshots_without_writes() -> Result<()> {
        for relative in [
            format!("{CORE_ROOT}/{JAVA}"),
            CORE_METADATA_PATH.to_owned(),
            FEATURE_DEFINITIONS_PATH.to_owned(),
            CATALOG_PATH.to_owned(),
            format!("{CORE_ROOT}/{ROLE_INPUT}"),
        ] {
            let fixture = Fixture::new_for_target("1.19.2");
            let key = Fixture::key(0, "release");
            let root = fixture.publish(&key);
            let collected = fixture.collect(&key)?;
            let checked = fixture.collect(&key)?.check_current()?;
            let path = fixture.temp.path().join(relative);
            let mut changed = fs::read(&path)?;
            changed.extend_from_slice(b" \n");
            fs::write(&path, &changed)?;
            assert!(collected.check_current().is_err());
            assert!(checked.recheck().is_err());
            assert_eq!(fs::read(path)?, changed);
            assert!(!root.join("build").exists());
        }
        for relative in [JAVA, ROLE_OUTPUT] {
            let fixture = Fixture::new();
            let key = Fixture::key(0, "release");
            let root = fixture.publish(&key);
            let checked = fixture.collect(&key)?.check_current()?;
            let path = root.join(relative);
            let mut changed = fs::read(&path)?;
            changed.extend_from_slice(b" \n");
            fs::write(&path, &changed)?;
            assert!(checked.recheck().is_err());
            assert_eq!(fs::read(path)?, changed);
            assert!(!root.join("build").exists());
        }
        Ok(())
    }

    #[test]
    fn missing_sources_and_unowned_output_collision_refuse_without_repair() -> Result<()> {
        let fixture = Fixture::new();
        let key = Fixture::key(0, "release");
        let root = fixture.publish(&key);
        fs::remove_file(root.join(JAVA))?;
        let error = fixture.collect(&key)?.check_current().unwrap_err();
        assert!(
            error
                .to_string()
                .contains("missing or extra unowned source inputs")
        );
        assert!(!root.join(JAVA).exists());
        let fixture = Fixture::new();
        let root = fixture.publish(&key);
        assert!(!root.join(".sfm-source-projection-manifest.json").exists());
        fixture.collect(&key)?.check_current()?;
        Ok(())
    }

    #[test]
    fn catalog_owner_and_destination_policy_do_not_follow_key_spelling() -> Result<()> {
        let fixture = Fixture::new();
        for key in [
            "unknown",
            "../escape",
            "published/nested/slot-0/child",
            "C:/outside",
        ] {
            assert!(fixture.collect(key).is_err());
        }
        let key = Fixture::key(0, "dev");
        let root = fixture.publish(&key);
        fs::write(fixture.temp.path().join(".gitignore"), "")?;
        assert!(fixture.collect(&key)?.check_current().is_err());
        assert!(root.join(JAVA).exists());
        let fixture = Fixture::new();
        let root = fixture.publish(&key);
        assert!(
            Command::new("git")
                .arg("-C")
                .arg(fixture.temp.path())
                .args(["add", "-f", "--"])
                .arg(root.join(JAVA))
                .status()?
                .success()
        );
        assert!(fixture.collect(&key)?.check_current().is_err());
        let fixture = Fixture::new();
        let key = Fixture::key(0, "release");
        fixture.publish(&key);
        fs::write(
            fixture.temp.path().join(".gitignore"),
            "/platform/minecraft/projections/\n",
        )?;
        assert!(fixture.collect(&key)?.check_current().is_err());
        Ok(())
    }

    #[test]
    fn source_case_aliases_are_refused_before_case_insensitive_file_reads() -> Result<()> {
        let fixture = Fixture::new();
        let key = Fixture::key(0, "release");
        let root = fixture.publish(&key);
        fs::rename(root.join(JAVA), root.join("src/main/java/shared.java"))?;
        assert!(fixture.collect(&key)?.check_current().is_err());
        let fixture = Fixture::new();
        let root = fixture.publish(&key);
        fs::rename(root.join("src"), root.join("SRC"))?;
        assert!(fixture.collect(&key)?.check_current().is_err());
        Ok(())
    }

    #[test]
    fn opaque_lock_bytes_and_original_native_v4_receipt_are_not_rewritten() -> Result<()> {
        let fixture = Fixture::new();
        let key = Fixture::key(0, "release");
        fs::write(fixture.source("build/fixture/1.19.2/lock"), RAW_V4)?;
        let root = fixture.publish(&key);
        let old = super::super::native_project_target::preflight_native_project(
            fixture.temp.path(),
            fixture.temp.path(),
            &key,
            super::super::native_project_target::NATIVE_DEPENDENCY_PROFILE,
        )?;
        let receipt_before = old.receipt.to_json()?;
        let provenance_before = fs::read(root.join(JAVA))?;
        let checked = fixture.collect(&key)?.check_current()?;
        assert_eq!(
            checked
                .selected_input("sfm-toolchain.lock.json")?
                .require_exact_copy()?,
            RAW_V4
        );
        let document =
            crate::toolchain_lockfile_schema::parse_document(std::str::from_utf8(RAW_V4)?)?;
        let crate::toolchain_lockfile_schema::ToolchainLockfileDocument::V4(lock) = document else {
            eyre::bail!("the V4 fixture must remain a genuine declared-profile lock");
        };
        let profile = super::super::native_project_target::NATIVE_DEPENDENCY_PROFILE;
        let owned = checked.receipt();
        let mapped = super::super::native_project_target::NativeProjectReceipt {
            schema: "sfm:native_project_preflight@1".to_owned(),
            compilation: super::super::native_project_target::NativeValidationStatus::NotPerformed,
            release_compatibility:
                super::super::native_project_target::NativeValidationStatus::NotPerformed,
            projection_key: owned.projection_key.clone(),
            environment: owned.environment,
            target_id: owned.target_id.clone(),
            minecraft_version: owned.minecraft_version.clone(),
            java_major: owned.java_major,
            loader: owned.loader.clone(),
            context_identity: owned.context_identity.clone(),
            project_dir: owned.project_dir.clone(),
            dependency_profile: profile.to_owned(),
            catalog_sha256: owned.catalog_sha256.clone(),
            feature_definitions_sha256: owned.feature_definitions_sha256.clone(),
            project_inputs_sha256: owned.project_inputs_sha256.clone(),
            lockfile_sha256: sha256(
                checked
                    .selected_input("sfm-toolchain.lock.json")?
                    .output_bytes(),
            ),
            effective_dependencies_sha256: sha256(
                facet_json::to_string(&lock.effective_lockfile(profile)?)?.as_bytes(),
            ),
            effective_source_exclusions_sha256: sha256(
                facet_json::to_string(&lock.effective_source_excludes(profile)?)?.as_bytes(),
            ),
            files: owned
                .files
                .iter()
                .map(|(output, file)| {
                    (
                        output.clone(),
                        super::super::native_project_target::NativeProjectFile {
                            authored_input: file.authored_input.clone(),
                            source_sha256: file.source_sha256.clone(),
                            output_sha256: file.output_sha256.clone(),
                        },
                    )
                })
                .collect(),
        };
        assert_eq!(mapped.to_json()?, receipt_before);
        assert_eq!(mapped.cache_identity()?, old.cache_identity);
        assert_eq!(old.receipt.to_json()?, receipt_before);
        assert_eq!(fs::read(root.join(JAVA))?, provenance_before);
        old.recheck()?;
        assert!(!old.cache_dir.exists());
        Ok(())
    }

    #[test]
    fn authored_membership_changes_and_bounded_reads_refuse_before_preparation() -> Result<()> {
        let fixture = Fixture::new();
        let key = Fixture::key(0, "release");
        let root = fixture.publish(&key);
        let collected = fixture.collect(&key)?;
        let checked = fixture.collect(&key)?.check_current()?;
        let added = fixture.source("src/main/resources/new-owner.bin");
        fs::write(&added, [0, 255])?;
        assert!(collected.check_current().is_err());
        assert!(checked.recheck().is_err());
        assert!(!root.join("src/main/resources/new-owner.bin").exists());
        assert_eq!(fs::read(added)?, [0, 255]);
        assert!(read_checked(&root, ROLE_OUTPUT, 1).is_err());
        assert!(!root.join("build").exists());
        Ok(())
    }

    #[test]
    fn shared_fixture_role_override_preserves_other_target_selection() -> Result<()> {
        let mut fixture = Fixture::new();
        let before = fixture.collect(&Fixture::key(1, "release"))?;
        fixture.set_project_file_for(
            "1.19.2",
            ROLE_OUTPUT,
            "build/roles/only-1.19.2.gradle",
            b"// target-scoped replacement\n",
            false,
        )?;
        let changed = fixture.collect(&Fixture::key(0, "release"))?;
        assert_eq!(
            changed.output_bytes(ROLE_OUTPUT)?,
            b"// target-scoped replacement\n"
        );
        let unchanged = fixture.collect(&Fixture::key(1, "release"))?;
        assert_eq!(
            unchanged.output_bytes(ROLE_OUTPUT)?,
            before.output_bytes(ROLE_OUTPUT)?
        );
        assert_eq!(
            unchanged.artifacts()[ROLE_OUTPUT].source_path,
            before.artifacts()[ROLE_OUTPUT].source_path
        );
        assert!(
            fixture
                .set_project_file_for(
                    "1.19.2",
                    ROLE_OUTPUT,
                    ROLE_INPUT,
                    b"// must not mutate another target\n",
                    false,
                )
                .is_err()
        );
        assert_eq!(
            fixture
                .collect(&Fixture::key(1, "release"))?
                .output_bytes(ROLE_OUTPUT)?,
            before.output_bytes(ROLE_OUTPUT)?
        );
        Ok(())
    }

    #[test]
    fn ownership_fingerprint_binds_context_metadata_provenance_and_source_inventory() -> Result<()>
    {
        let fixture = Fixture::new();
        let key = Fixture::key(0, "release");
        fixture.publish(&key);
        let checked = fixture.collect(&key)?.check_current()?;
        let original = checked.ownership_identity()?;
        for field in 0..9 {
            let mut receipt = checked.receipt().clone();
            match field {
                0 => receipt.projection_key.push_str("-other"),
                1 => receipt.environment = ProjectionEnvironment::Dev,
                2 => receipt.context_identity.push('0'),
                3 => receipt.catalog_sha256.push('0'),
                4 => receipt.feature_definitions_sha256.push('0'),
                5 => receipt.project_inputs_sha256.push('0'),
                6 => receipt.context_identity.push('0'),
                7 => receipt.authored_source_inventory_sha256.push('0'),
                _ => receipt.generated_source_inventory_sha256.push('0'),
            }
            assert_ne!(
                sha256(facet_json::to_string(&receipt)?.as_bytes()),
                original
            );
        }
        Ok(())
    }

    #[cfg(unix)]
    #[test]
    fn generated_src_symlinks_are_refused_even_when_not_owned_inputs() -> Result<()> {
        use std::os::unix::fs::symlink;
        for directory in [true, false] {
            let fixture = Fixture::new();
            let key = Fixture::key(0, "release");
            let root = fixture.publish(&key);
            let outside = tempfile::tempdir()?;
            if directory {
                symlink(outside.path(), root.join("src/unused-linked-directory"))?;
            } else {
                let file = outside.path().join("fixture.java");
                fs::write(&file, b"class Outside {}")?;
                symlink(&file, root.join("src/unused-linked-file"))?;
            }
            assert!(fixture.collect(&key)?.check_current().is_err());
            assert!(!root.join("build").exists());
        }
        Ok(())
    }

    #[cfg(windows)]
    #[test]
    fn generated_src_junctions_are_refused_before_traversal() -> Result<()> {
        let fixture = Fixture::new();
        let key = Fixture::key(0, "release");
        let root = fixture.publish(&key);
        let outside = tempfile::Builder::new()
            .prefix("sfm ownership external ")
            .tempdir()?;
        let result = Command::new("powershell.exe")
            .args(["-NoLogo", "-NoProfile", "-NonInteractive", "-Command",
                "$ownedParent = (Resolve-Path -LiteralPath $env:SFM_OWNED_TEST_PARENT -ErrorAction Stop).ProviderPath; $ownedTarget = (Resolve-Path -LiteralPath $env:SFM_OWNED_TEST_TARGET -ErrorAction Stop).ProviderPath; $ownedPath = Join-Path -Path $ownedParent -ChildPath 'unused-linked-directory'; New-Item -ItemType Junction -Path $ownedPath -Target $ownedTarget -ErrorAction Stop | Out-Null"])
            .env("SFM_OWNED_TEST_PARENT", dunce::canonicalize(root.join("src"))?)
            .env("SFM_OWNED_TEST_TARGET", dunce::canonicalize(outside.path())?)
            .output()?;
        ensure!(
            result.status.success(),
            "junction fixture failed: {}",
            String::from_utf8_lossy(&result.stderr)
        );
        let result = fixture.collect(&key)?.check_current();
        fs::remove_dir(root.join("src/unused-linked-directory"))?;
        assert!(result.is_err());
        assert!(fs::read_dir(outside.path())?.next().is_none());
        assert!(!root.join("build").exists());
        Ok(())
    }

    #[test]
    fn all20_changed_outputs_refuse_recheck_and_do_not_touch_other_projection_roots() -> Result<()>
    {
        let fixture = Fixture::new();
        let mut checked = Vec::new();
        for slot in 0..10 {
            for environment in ["release", "dev"] {
                let key = Fixture::key(slot, environment);
                let root = fixture.publish(&key);
                checked.push((fixture.collect(&key)?.check_current()?, root));
            }
        }
        for (owner, root) in &checked {
            fs::write(root.join(ROLE_OUTPUT), b"contributor role edit")?;
            assert!(owner.recheck().is_err());
            assert_eq!(fs::read(root.join(ROLE_OUTPUT))?, b"contributor role edit");
            assert!(!root.join("build").exists());
        }
        Ok(())
    }
}
