//! Reviewed, create-only authoring from pinned release blobs.
//!
//! Preview checks every required context. It never imports a historical tree
//! into production or invents feature ownership to hide an ambiguous witness.

use super::candidate_lock::checked_directory;
use super::candidate_lock::checked_file;
use super::core_catalog::CoreCatalog;
use super::core_inputs::CORE_METADATA_PATH;
use super::core_inputs::CORE_ROOT;
use super::core_inputs::CoreProjectInputs;
use super::core_inputs::InputPredicate;
use super::core_inputs::InputVariant;
use super::core_inputs::MAX_CORE_FILE_BYTES;
use super::core_inputs::MAX_CORE_METADATA_BYTES;
use super::core_inputs::discover_core_source_files;
use super::core_inputs::select_core_inputs;
use super::inputs::render_java_artifact;
use super::oracle::ORACLES_PATH;
use super::oracle::in_scope;
use super::oracle::parse_bindings;
use super::oracle::read_bounded;
use super::oracle_compare::compare_project_bytes;
use super::oracle_git::OracleGitRepository;
use super::projection_catalog::SUPPORTED_TARGETS;
use super::projection_catalog::validate_projection_key;
use super::provenance::sha256;
use super::sync::ProjectedArtifact;
use crate::cancellation::CancellationToken;
use eyre::Result;
use eyre::WrapErr;
use eyre::ensure;
use facet::Facet;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::fs;
use std::io::Write as _;
use std::path::Path;
use std::path::PathBuf;

const MAX_OUTPUTS: usize = 8;

/// Aliases are explicit additional output paths for the same newly authored input.
#[derive(Clone, Debug)]
pub struct OracleSeedRequest<'a> {
    pub projection: &'a str,
    pub file: &'a str,
    pub input: Option<&'a str>,
    pub output_aliases: &'a [String],
    pub apply: bool,
    pub reviewed_plan_identity: Option<&'a str>,
}

#[derive(Clone, Debug, Facet)]
pub struct OracleSeedWitness {
    pub projection: String,
    pub target_id: String,
    pub reference: String,
    pub commit: String,
    pub present: bool,
}

#[derive(Debug, Facet)]
pub struct OracleSeedReport {
    pub schema: String,
    pub operation: String,
    pub plan_identity: String,
    pub writes_performed: bool,
    pub projection: String,
    pub input: String,
    pub outputs: Vec<String>,
    pub targets: Vec<String>,
    pub oracle_commit: String,
    pub oracle_blob: String,
    pub oracle_mode: u32,
    pub source_identity: String,
    pub source_bytes: usize,
    pub binding_identity: String,
    pub catalog_identity: String,
    pub feature_registry_identity: String,
    pub source_inventory_identity: String,
    pub metadata_preimage_identity: String,
    pub metadata_proposal_identity: String,
    pub proposed_rules: BTreeMap<String, Vec<InputVariant>>,
    pub witnesses: Vec<OracleSeedWitness>,
}

struct PreparedSeed {
    input: String,
    bytes: Vec<u8>,
    mode: u32,
    metadata_before: Vec<u8>,
    metadata_after: Vec<u8>,
}

/// Preview a missing core input and its explicit membership rules. Apply requires
/// the identity returned by a reviewed preview and never overwrites a core input.
/// The initial authoring path supports shared bytes with proven target membership;
/// differing content or release/dev membership needs a reviewed conditional draft.
///
/// # Errors
/// Rejects non-release witnesses, ambiguous ownership/content, existing inputs or
/// rules, stale plans, invalid full-matrix selection, unsafe paths and failed I/O.
#[expect(
    clippy::too_many_lines,
    reason = "One bounded preflight keeps every authoring condition before the first write"
)]
pub fn seed_missing(
    repo_root: &Path,
    invocation_dir: &Path,
    request: &OracleSeedRequest<'_>,
    cancellation: &CancellationToken,
) -> Result<OracleSeedReport> {
    cancellation.bail_if_cancelled()?;
    let catalog = CoreCatalog::load(repo_root, invocation_dir)?;
    let binding_bytes = read_bounded(&catalog.repo_root, ORACLES_PATH, 1024 * 1024)?;
    let bindings = parse_bindings(&binding_bytes)?;
    let witness_binding = bindings
        .bindings
        .iter()
        .find(|row| row.projection == request.projection)
        .ok_or_else(|| eyre::eyre!("No required oracle binding for {}", request.projection))?;
    ensure!(
        witness_binding.environment == "release",
        "seed-missing requires a pinned previous-release witness; development-only input needs an explicit reviewed draft"
    );

    let outputs = request_outputs(request)?;
    let input = request.input.map_or_else(
        || {
            if request.file.starts_with("src/") {
                request.file.to_owned()
            } else {
                format!("build/shared/{}", request.file)
            }
        },
        str::to_owned,
    );
    validate_projection_key(&input)?;
    ensure!(
        !input.starts_with("src/") || outputs.contains(&input),
        "A source input must have an explicit same-path output; otherwise core/src discovery would add an unintended output"
    );
    let core = checked_directory(&catalog.repo_root.join(CORE_ROOT))?;
    ensure_absent(&core, &input)?;
    let metadata_before = read_bounded(
        &catalog.repo_root,
        CORE_METADATA_PATH,
        MAX_CORE_METADATA_BYTES,
    )?;
    let metadata = CoreProjectInputs::from_json(
        std::str::from_utf8(&metadata_before)?,
        &catalog.registered_features,
    )?;
    for output in &outputs {
        ensure!(
            !metadata.source_rules.contains_key(output)
                && !metadata.project_files.contains_key(output),
            "Core output {output} already has an authored rule; preserve its owner and review that rule before seeding"
        );
    }
    let inventory = discover_core_source_files(&core)?;
    let git = OracleGitRepository::open(&catalog.repo_root)?;
    let anchor_files = git.files_at_commit_filtered(
        &witness_binding.commit,
        &witness_binding.source_prefix,
        |path| path == request.file,
    )?;
    let anchor = anchor_files.get(request.file).ok_or_else(|| {
        eyre::eyre!(
            "Pinned release {} has no {}",
            request.projection,
            request.file
        )
    })?;
    ensure!(
        u64::try_from(anchor.bytes.len())? <= MAX_CORE_FILE_BYTES,
        "Seed witness exceeds the core file limit"
    );

    let mut witnesses = Vec::new();
    let mut membership = BTreeMap::new();
    for binding in &bindings.bindings {
        cancellation.bail_if_cancelled()?;
        let entry = catalog.catalog.entry(&binding.projection)?;
        ensure!(
            entry.target_id()? == binding.target_id
                && entry.environment.as_str() == binding.environment,
            "Oracle context no longer agrees with catalog: {}",
            binding.projection
        );
        let current = git.resolve_reference(&binding.reference)?;
        ensure!(
            current.commit == binding.commit && current.tree == binding.tree,
            "Oracle reference {} moved; inspect drift before authoring from a stale matrix",
            binding.reference
        );
        let revision = git.resolve_commit(&binding.commit)?;
        ensure!(
            revision.tree == binding.tree,
            "Pinned commit/tree mismatch for {}",
            binding.projection
        );
        let files =
            git.files_at_commit_filtered(&binding.commit, &binding.source_prefix, |path| {
                outputs.iter().any(|output| output == path)
            })?;
        let present = files.contains_key(request.file);
        for output in &outputs {
            ensure!(
                files.contains_key(output) == present,
                "Alias {output} has different membership in {}; use separate reviewed drafts",
                binding.projection
            );
            if let Some(file) = files.get(output) {
                ensure!(
                    file.mode == anchor.mode
                        && file.oid == anchor.oid
                        && file.bytes == anchor.bytes,
                    "Oracle variants differ for {output} in {}; use a conditional draft instead of automatic seeding",
                    binding.projection
                );
                let mut artifact = ProjectedArtifact {
                    source_path: format!("{CORE_ROOT}/{input}"),
                    source_bytes: anchor.bytes.clone(),
                    output_bytes: anchor.bytes.clone(),
                    overlay: None,
                };
                let context = catalog.context(&binding.projection)?;
                render_java_artifact(output, &mut artifact, &context)?;
                let compared = compare_project_bytes(
                    output,
                    &binding.target_id,
                    &file.bytes,
                    &artifact.output_bytes,
                    &bindings.comparison_policy,
                );
                ensure!(
                    compared.normalized_equal && compared.diagnostics.is_empty(),
                    "Real renderer cannot reproduce the seed witness for {output} in {}",
                    binding.projection
                );
            }
        }
        if let Some(previous) = membership.insert(binding.target_id.clone(), present) {
            ensure!(
                previous == present,
                "Release/dev membership differs for {} on {}; feature ownership needs a reviewed draft",
                request.file,
                binding.target_id
            );
        }
        witnesses.push(OracleSeedWitness {
            projection: binding.projection.clone(),
            target_id: binding.target_id.clone(),
            reference: binding.reference.clone(),
            commit: binding.commit.clone(),
            present,
        });
    }
    let targets = SUPPORTED_TARGETS
        .iter()
        .filter(|(target, _)| membership.get(*target) == Some(&true))
        .map(|(target, _)| (*target).to_owned())
        .collect::<Vec<_>>();
    ensure!(
        !targets.is_empty(),
        "Seed has no witnessed target membership"
    );
    let variant = InputVariant {
        input: input.clone(),
        template: false,
        when: InputPredicate {
            targets: targets.clone(),
            ..InputPredicate::default()
        },
    };
    let proposed_rules = outputs
        .iter()
        .map(|output| (output.clone(), vec![variant.clone()]))
        .collect::<BTreeMap<_, _>>();
    let mut proposed = metadata.clone();
    for (output, variants) in &proposed_rules {
        if output.starts_with("src/") {
            proposed
                .source_rules
                .insert(output.clone(), variants.clone());
        } else {
            proposed
                .project_files
                .insert(output.clone(), variants.clone());
        }
    }
    proposed.validate(&catalog.registered_features)?;
    let mut prospective_inventory = inventory.clone();
    if input.starts_with("src/") {
        prospective_inventory.insert(input.clone());
    }
    for witness in &witnesses {
        cancellation.bail_if_cancelled()?;
        let context = catalog.context(&witness.projection)?;
        let before = select_core_inputs(&metadata, &context, &inventory)?;
        let after = select_core_inputs(&proposed, &context, &prospective_inventory)?;
        verify_selection(
            &before.inputs,
            &after.inputs,
            &outputs,
            &input,
            witness.present,
        )?;
    }
    let metadata_after = append_rules(&metadata_before, &proposed_rules)?;
    ensure!(
        CoreProjectInputs::from_json(
            std::str::from_utf8(&metadata_after)?,
            &catalog.registered_features
        )? == proposed,
        "Minimal metadata edit does not match the reviewed typed proposal"
    );
    let mut report = OracleSeedReport {
        schema: "sfm:projection_oracle_seed@1".into(),
        operation: "preview".into(),
        plan_identity: String::new(),
        writes_performed: false,
        projection: request.projection.into(),
        input: input.clone(),
        outputs,
        targets,
        oracle_commit: witness_binding.commit.clone(),
        oracle_blob: anchor.oid.clone(),
        oracle_mode: anchor.mode,
        source_identity: sha256(&anchor.bytes),
        source_bytes: anchor.bytes.len(),
        binding_identity: sha256(&binding_bytes),
        catalog_identity: catalog.catalog_sha256,
        feature_registry_identity: catalog.feature_definitions_sha256,
        source_inventory_identity: sha256(facet_json::to_string(&inventory)?.as_bytes()),
        metadata_preimage_identity: sha256(&metadata_before),
        metadata_proposal_identity: sha256(&metadata_after),
        proposed_rules,
        witnesses,
    };
    report.plan_identity = sha256(facet_json::to_string(&report)?.as_bytes());
    if request.apply {
        ensure!(
            request.reviewed_plan_identity == Some(report.plan_identity.as_str()),
            "Apply requires --reviewed-plan {} from this preview; changed inputs require a new preview",
            report.plan_identity
        );
        let current_catalog = CoreCatalog::load(&catalog.repo_root, invocation_dir)?;
        ensure!(
            current_catalog.catalog_sha256 == report.catalog_identity
                && current_catalog.feature_definitions_sha256 == report.feature_registry_identity,
            "Core catalog or feature registry changed during seed preflight; preview again"
        );
        ensure!(
            sha256(&read_bounded(
                &catalog.repo_root,
                ORACLES_PATH,
                1024 * 1024
            )?) == report.binding_identity,
            "Oracle bindings changed during seed preflight; preview again"
        );
        let current_core = checked_directory(&catalog.repo_root.join(CORE_ROOT))?;
        let current_inventory = discover_core_source_files(&current_core)?;
        ensure!(
            sha256(facet_json::to_string(&current_inventory)?.as_bytes())
                == report.source_inventory_identity,
            "Core source membership changed during seed preflight; preview again"
        );
        for binding in &bindings.bindings {
            cancellation.bail_if_cancelled()?;
            let current = git.resolve_reference(&binding.reference)?;
            ensure!(
                current.commit == binding.commit && current.tree == binding.tree,
                "Oracle reference {} moved during seed preflight; inspect drift before applying",
                binding.reference
            );
        }
        let prepared = PreparedSeed {
            input,
            bytes: anchor.bytes.clone(),
            mode: anchor.mode,
            metadata_before,
            metadata_after,
        };
        apply_prepared(&catalog.repo_root, &prepared, cancellation)?;
        report.operation = "apply".into();
        report.writes_performed = true;
    }
    Ok(report)
}

fn request_outputs(request: &OracleSeedRequest<'_>) -> Result<Vec<String>> {
    ensure!(
        request.output_aliases.len() < MAX_OUTPUTS,
        "Seed has too many output aliases"
    );
    let mut outputs = BTreeSet::new();
    let mut folded = BTreeSet::new();
    for output in
        std::iter::once(request.file).chain(request.output_aliases.iter().map(String::as_str))
    {
        validate_projection_key(output)?;
        ensure!(
            in_scope(output),
            "Seed output {output} is excluded from the declared oracle scope"
        );
        ensure!(
            folded.insert(output.to_ascii_lowercase()),
            "Duplicate or case-aliased seed output {output}"
        );
        outputs.insert(output.to_owned());
    }
    Ok(outputs.into_iter().collect())
}

fn verify_selection(
    before: &BTreeMap<String, super::core_inputs::SelectedCoreInput>,
    after: &BTreeMap<String, super::core_inputs::SelectedCoreInput>,
    outputs: &[String],
    input: &str,
    present: bool,
) -> Result<()> {
    for output in outputs {
        ensure!(
            !before.contains_key(output),
            "Seed output {output} is already selected; refine the existing authored input"
        );
        ensure!(
            after.contains_key(output) == present,
            "Prospective source membership for {output} does not match its oracle; core/src defaults must not leak into omitted targets"
        );
        if let Some(selected) = after.get(output) {
            ensure!(
                selected.input == input && !selected.template,
                "Seed output {output} selects a different authored input"
            );
        }
    }
    let unchanged = after
        .iter()
        .filter(|(output, _)| !outputs.contains(output))
        .map(|(output, selected)| (output.clone(), selected.clone()))
        .collect::<BTreeMap<_, _>>();
    ensure!(
        &unchanged == before,
        "Seeding would change unrelated selected inputs"
    );
    Ok(())
}

/// Preserve every byte outside the newly inserted explicit rule entries.
fn append_rules(before: &[u8], rules: &BTreeMap<String, Vec<InputVariant>>) -> Result<Vec<u8>> {
    let mut text = std::str::from_utf8(before)?.to_owned();
    for (section, source) in [("source_rules", true), ("project_files", false)] {
        let additions = rules
            .iter()
            .filter(|(output, _)| output.starts_with("src/") == source)
            .map(|(output, variants)| {
                Ok(format!(
                    "    {}: {}",
                    facet_json::to_string(output)?,
                    facet_json::to_string(variants)?
                ))
            })
            .collect::<Result<Vec<_>>>()?;
        if additions.is_empty() {
            continue;
        }
        let at = root_object_insertion(&text, section)?;
        let populated = !text[at..].trim_start().starts_with('}');
        let insertion = format!(
            "\n{}{}",
            additions.join(",\n"),
            if populated { "," } else { "\n  " }
        );
        text.insert_str(at, &insertion);
    }
    ensure!(
        u64::try_from(text.len())? <= MAX_CORE_METADATA_BYTES,
        "Seed metadata proposal exceeds its byte limit"
    );
    Ok(text.into_bytes())
}

fn root_object_insertion(text: &str, wanted: &str) -> Result<usize> {
    let bytes = text.as_bytes();
    let mut at = 0;
    let mut depth = 0;
    while at < bytes.len() {
        match bytes[at] {
            b'{' | b'[' => {
                depth += 1;
                at += 1;
            }
            b'}' | b']' => {
                depth -= 1;
                at += 1;
            }
            b'"' => {
                let start = at;
                at += 1;
                while at < bytes.len() {
                    if bytes[at] == b'\\' {
                        at += 2;
                    } else if bytes[at] == b'"' {
                        at += 1;
                        break;
                    } else {
                        at += 1;
                    }
                }
                if depth != 1 {
                    continue;
                }
                let name: String = facet_json::from_str(&text[start..at])?;
                if name != wanted {
                    continue;
                }
                while bytes.get(at).is_some_and(u8::is_ascii_whitespace) {
                    at += 1;
                }
                ensure!(
                    bytes.get(at) == Some(&b':'),
                    "Metadata section {wanted} is not a property"
                );
                at += 1;
                while bytes.get(at).is_some_and(u8::is_ascii_whitespace) {
                    at += 1;
                }
                ensure!(
                    bytes.get(at) == Some(&b'{'),
                    "Metadata section {wanted} is not an object"
                );
                return Ok(at + 1);
            }
            _ => at += 1,
        }
    }
    eyre::bail!("Metadata has no root {wanted} object")
}

fn ensure_absent(core: &Path, relative: &str) -> Result<PathBuf> {
    validate_projection_key(relative)?;
    let core = checked_directory(core)?;
    let mut parent = core;
    let components = relative.split('/').collect::<Vec<_>>();
    for (index, component) in components.iter().enumerate() {
        let candidate = parent.join(component);
        if parent.try_exists()? {
            for entry in fs::read_dir(&parent)? {
                let entry = entry?;
                let name = entry.file_name();
                if name
                    .to_str()
                    .is_some_and(|name| name.eq_ignore_ascii_case(component))
                {
                    ensure!(
                        name == std::ffi::OsStr::new(component),
                        "Core seed path has a case alias at {relative}"
                    );
                }
            }
        }
        match fs::symlink_metadata(&candidate) {
            Ok(_) if index + 1 == components.len() => {
                eyre::bail!("Core seed input already exists: {relative}; refusing overwrite")
            }
            Ok(_) => {
                checked_directory(&candidate)?;
            }
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => {
                return Ok(components[index..]
                    .iter()
                    .fold(parent, |path, part| path.join(part)));
            }
            Err(error) => return Err(error).wrap_err("Cannot inspect prospective core seed path"),
        }
        parent = candidate;
    }
    eyre::bail!("Core seed input already exists: {relative}")
}

fn create_parents(core: &Path, relative: &str) -> Result<()> {
    validate_projection_key(relative)?;
    let mut parent = checked_directory(core)?;
    let parts = relative.split('/').collect::<Vec<_>>();
    for part in &parts[..parts.len() - 1] {
        parent.push(part);
        match fs::create_dir(&parent) {
            Ok(()) => {}
            Err(error) if error.kind() == std::io::ErrorKind::AlreadyExists => {}
            Err(error) => return Err(error).wrap_err("Cannot create core seed parent"),
        }
        checked_directory(&parent)?;
    }
    Ok(())
}

fn apply_prepared(
    repo_root: &Path,
    prepared: &PreparedSeed,
    cancellation: &CancellationToken,
) -> Result<()> {
    let core = checked_directory(&repo_root.join(CORE_ROOT))?;
    ensure_absent(&core, &prepared.input)?;
    ensure!(
        read_bounded(repo_root, CORE_METADATA_PATH, MAX_CORE_METADATA_BYTES)?
            == prepared.metadata_before,
        "Core metadata changed since seed preview; refusing the entire apply set"
    );
    cancellation.bail_if_cancelled()?;
    create_parents(&core, &prepared.input)?;
    // Temporary files remain outside core/src, so production discovery cannot
    // mistake staged bytes for an additional source output.
    let mut source = tempfile::NamedTempFile::new_in(&core)?;
    source.write_all(&prepared.bytes)?;
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt as _;
        source
            .as_file()
            .set_permissions(fs::Permissions::from_mode(prepared.mode & 0o777))?;
    }
    #[cfg(not(unix))]
    ensure!(
        matches!(prepared.mode, 0o100_644 | 0o100_755),
        "Unsupported seed mode"
    );
    source.as_file().sync_all()?;
    let metadata_path = checked_file(repo_root, CORE_METADATA_PATH)?;
    let mut metadata = tempfile::NamedTempFile::new_in(&core)?;
    metadata.write_all(&prepared.metadata_after)?;
    metadata
        .as_file()
        .set_permissions(fs::metadata(&metadata_path)?.permissions())?;
    metadata.as_file().sync_all()?;
    cancellation.bail_if_cancelled()?;
    let destination = ensure_absent(&core, &prepared.input)?;
    ensure!(
        read_bounded(repo_root, CORE_METADATA_PATH, MAX_CORE_METADATA_BYTES)?
            == prepared.metadata_before,
        "Core metadata changed during seed preparation; refusing apply"
    );
    // Install membership before a core/src input can become discoverable. If
    // input publication fails, generation reports the missing input instead of
    // silently applying the shared-source default to omitted targets.
    checked_directory(&core)?;
    ensure!(
        checked_file(repo_root, CORE_METADATA_PATH)? == metadata_path,
        "Core metadata path changed during seed apply"
    );
    metadata
        .persist(&metadata_path)
        .map_err(|error| error.error)?;
    let create_input = (|| -> Result<()> {
        checked_directory(&core)?;
        ensure!(
            ensure_absent(&core, &prepared.input)? == destination,
            "Core seed destination changed during apply"
        );
        ensure!(
            read_bounded(repo_root, CORE_METADATA_PATH, MAX_CORE_METADATA_BYTES)?
                == prepared.metadata_after,
            "Core membership metadata changed before input publication"
        );
        source
            .persist_noclobber(&destination)
            .map_err(|error| error.error)?;
        Ok(())
    })();
    create_input.wrap_err_with(|| format!(
        "Membership metadata was applied but creation of {} failed; retain existing contributor inputs and review this incomplete apply; no destructive rollback was performed",
        prepared.input))
}

#[cfg(test)]
mod tests {
    use super::super::core_inputs::SelectedCoreInput;
    use super::*;

    #[test]
    fn metadata_insert_preserves_other_bytes_and_handles_escaped_root_key() {
        let before = b"{\"schema_version\":1,\"source_rules\":{},\"project_\\u0066iles\":{\"existing\":[]},\"untouched\":\"literal project_files\"}";
        let variant = InputVariant {
            input: "build/shared/code.xml".into(),
            template: false,
            when: InputPredicate {
                targets: vec!["1.19.2".into()],
                ..InputPredicate::default()
            },
        };
        let rules = BTreeMap::from([("code.xml".into(), vec![variant])]);
        let after = String::from_utf8(append_rules(before, &rules).unwrap()).unwrap();
        assert!(
            after.starts_with(
                "{\"schema_version\":1,\"source_rules\":{},\"project_\\u0066iles\":{\n"
            )
        );
        assert!(after.ends_with("\"existing\":[]},\"untouched\":\"literal project_files\"}"));
        assert!(after.contains("\"targets\":[\"1.19.2\"]"));
        assert!(!after.ends_with('\n'));
    }

    #[test]
    fn prospective_selection_rejects_source_default_leak_and_unrelated_change() {
        let before = BTreeMap::new();
        let outputs = vec!["src/A.java".into()];
        let selected = SelectedCoreInput {
            input: "src/A.java".into(),
            template: false,
        };
        let after = BTreeMap::from([("src/A.java".into(), selected.clone())]);
        assert!(verify_selection(&before, &after, &outputs, "src/A.java", false).is_err());
        verify_selection(&before, &after, &outputs, "src/A.java", true).unwrap();
        let after = BTreeMap::from([
            ("src/A.java".into(), selected.clone()),
            ("src/leaked.java".into(), selected),
        ]);
        assert!(verify_selection(&before, &after, &outputs, "src/A.java", true).is_err());
    }

    #[test]
    fn actual_selector_keeps_unwitnessed_target_omitted_after_source_creation() {
        use super::super::context::ProjectionContext;
        use super::super::core_inputs::BuildTargetMetadata;

        let metadata = CoreProjectInputs {
            schema_version: 1,
            targets: ["1.19.2", "1.19.4"]
                .into_iter()
                .map(|target| {
                    (
                        target.into(),
                        BuildTargetMetadata {
                            java_major: 17,
                            loader: "forge".into(),
                        },
                    )
                })
                .collect(),
            source_rules: BTreeMap::new(),
            project_files: BTreeMap::new(),
        };
        let output = "src/main/java/proof/Shared.java".to_owned();
        let mut proposed = metadata.clone();
        proposed.source_rules.insert(
            output.clone(),
            vec![InputVariant {
                input: output.clone(),
                template: false,
                when: InputPredicate {
                    targets: vec!["1.19.2".into()],
                    ..InputPredicate::default()
                },
            }],
        );
        let inventory = BTreeSet::from([output.clone()]);
        for (target, present) in [("1.19.2", true), ("1.19.4", false)] {
            let mut flags = SUPPORTED_TARGETS
                .iter()
                .map(|(id, _)| (format!("mc_{}", id.replace('.', "_")), *id == target))
                .collect::<BTreeMap<_, _>>();
            flags.insert("forge".into(), true);
            flags.insert("neoforge".into(), false);
            let context = ProjectionContext {
                minecraft_version: target.into(),
                preset: "fixture".into(),
                environment: "release".into(),
                projection_key: "fixture".into(),
                features: BTreeMap::new(),
                targets: flags,
            };
            let before = select_core_inputs(&metadata, &context, &BTreeSet::new()).unwrap();
            let after = select_core_inputs(&proposed, &context, &inventory).unwrap();
            verify_selection(
                &before.inputs,
                &after.inputs,
                std::slice::from_ref(&output),
                &output,
                present,
            )
            .unwrap();
            if !present {
                let leaked = select_core_inputs(&metadata, &context, &inventory).unwrap();
                assert!(
                    verify_selection(
                        &before.inputs,
                        &leaked.inputs,
                        std::slice::from_ref(&output),
                        &output,
                        false
                    )
                    .is_err()
                );
            }
        }
    }

    #[test]
    fn create_only_apply_preserves_binary_and_no_final_newline_then_refuses_retry() {
        let temp = tempfile::tempdir().unwrap();
        let core = temp.path().join(CORE_ROOT);
        fs::create_dir_all(&core).unwrap();
        let metadata = temp.path().join(CORE_METADATA_PATH);
        fs::write(&metadata, b"before").unwrap();
        let prepared = PreparedSeed {
            input: "build/shared/exact.bin".into(),
            bytes: vec![0, 255, b'>'],
            mode: 0o100_644,
            metadata_before: b"before".to_vec(),
            metadata_after: b"after".to_vec(),
        };
        apply_prepared(temp.path(), &prepared, &CancellationToken::default()).unwrap();
        assert_eq!(
            fs::read(core.join(&prepared.input)).unwrap(),
            [0, 255, b'>']
        );
        assert_eq!(fs::read(&metadata).unwrap(), b"after");
        assert!(apply_prepared(temp.path(), &prepared, &CancellationToken::default()).is_err());
        assert_eq!(
            fs::read(core.join(&prepared.input)).unwrap(),
            [0, 255, b'>']
        );
    }

    #[test]
    fn stale_metadata_refuses_before_creating_input_or_parent() {
        let temp = tempfile::tempdir().unwrap();
        let core = temp.path().join(CORE_ROOT);
        fs::create_dir_all(&core).unwrap();
        fs::write(
            temp.path().join(CORE_METADATA_PATH),
            b"changed by contributor",
        )
        .unwrap();
        let prepared = PreparedSeed {
            input: "new/seed.xml".into(),
            bytes: b"raw>".to_vec(),
            mode: 0o100_644,
            metadata_before: b"old".to_vec(),
            metadata_after: b"new".to_vec(),
        };
        assert!(apply_prepared(temp.path(), &prepared, &CancellationToken::default()).is_err());
        assert!(!core.join("new").exists());
        assert_eq!(
            fs::read(temp.path().join(CORE_METADATA_PATH)).unwrap(),
            b"changed by contributor"
        );
    }

    #[test]
    fn aliases_are_explicit_and_excluded_datagen_cache_cannot_be_seeded() {
        let aliases = vec!["codestyles/Default.xml".into()];
        let request = OracleSeedRequest {
            projection: "sfm-4.34.0/mc-1.19.2",
            file: "code.xml",
            input: None,
            output_aliases: &aliases,
            apply: false,
            reviewed_plan_identity: None,
        };
        assert_eq!(
            request_outputs(&request).unwrap(),
            ["code.xml", "codestyles/Default.xml"]
        );
        let request = OracleSeedRequest {
            file: "src/generated/resources/.cache/123",
            ..request
        };
        assert!(request_outputs(&request).is_err());
    }

    #[cfg(unix)]
    #[test]
    fn seed_parent_symlink_and_existing_file_are_refused() {
        let temp = tempfile::tempdir().unwrap();
        let core = temp.path().join(CORE_ROOT);
        fs::create_dir_all(&core).unwrap();
        let outside = temp.path().join("outside");
        fs::create_dir(&outside).unwrap();
        std::os::unix::fs::symlink(&outside, core.join("escape")).unwrap();
        assert!(ensure_absent(&core, "escape/input.xml").is_err());
        fs::write(core.join("existing.xml"), b"owned").unwrap();
        assert!(ensure_absent(&core, "existing.xml").is_err());
    }
}
