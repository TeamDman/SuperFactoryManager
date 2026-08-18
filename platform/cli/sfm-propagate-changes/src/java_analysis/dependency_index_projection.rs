use super::DEPENDENCY_SYMBOL_INDEX_IDENTITY_SCHEMA;
use super::DependencyArtifactProjection;
use super::DependencyComponentProjection;
use super::DependencyIndexContextProjection;
use super::DependencyIndexFormatProjection;
use super::DependencyIndexParserProjection;
use super::DependencyProjection;
use super::DependencyRepositoryProjection;
use super::DependencySemanticProperty;
use super::DependencySourceProviderKind;
use super::DependencySourceProviderProjection;
use super::DependencySymbolIndexIdentityProjection;
use super::EffectiveDependencyLockProjection;
use crate::dependency_inventory::DependencyInventory;
use crate::dependency_inventory::kind_label;
use crate::dependency_inventory::role_label;
use crate::dependency_inventory::scope_label;
use crate::dependency_locked_sources::LockedArtifactSource;
#[cfg(test)]
use crate::dependency_locked_sources::derive_locked_loader_artifact_sources;
use crate::java_analysis::DEPENDENCY_JAVA_SYMBOL_INDEX_BODY_SCHEMA;
use crate::java_analysis::DEPENDENCY_JAVA_SYMBOL_INDEX_STREAM_SCHEMA;
use crate::java_analysis::DEPENDENCY_SYMBOL_INDEX_STORE_FORMAT_VERSION;
use crate::java_analysis::JavaAnalysisContextOutput;
use crate::java_analysis::syntax::JAVA_PARSER_FINGERPRINT;
use crate::source_provider::SourceProviderKind;
use crate::source_provider::SourceProviderView;
use crate::toolchain_lockfile_schema::ToolchainLockfileDocument;
use crate::toolchain_lockfile_schema::parse_document;
use crate::toolchain_lockfile_schema::version::v3::ArtifactProvenanceV3;
use crate::toolchain_lockfile_schema::version::v3::ArtifactTreatmentV3;
use crate::toolchain_lockfile_schema::version::v3::ComponentAcquisitionV3;
use crate::toolchain_lockfile_schema::version::v3::DataRunPolicyV3;
use crate::toolchain_lockfile_schema::version::v3::DependencyComponentV3;
use crate::toolchain_lockfile_schema::version::v3::DependencyV3;
use crate::toolchain_lockfile_schema::version::v3::SourceProviderV3;
use crate::toolchain_lockfile_schema::version::v3::ToolchainComponentKindV3;
use crate::toolchain_lockfile_schema::version::v4::ArtifactLockfileV4;
use crate::toolchain_lockfile_schema::version::v4::FeatureV4;
use std::collections::BTreeMap;
use std::collections::BTreeSet;

pub(crate) const DEPENDENCY_SYMBOL_INDEX_TOOLCHAIN_PROFILE: &str = "rust-toolchain";
pub(crate) const DEPENDENCY_JAVA_SYMBOL_INDEX_ALGORITHM_FINGERPRINT: &str =
    "sfm.dependency-java-symbol-index-algorithm/5";

/// Path-free context supplied by the branch/workspace resolver around the
/// effective dependency inventory.
#[derive(Clone, Debug, Eq, PartialEq)]
pub(crate) struct DependencySymbolIndexProjectionInputs {
    pub(crate) context: DependencyIndexContextProjection,
    pub(crate) parser: DependencyIndexParserProjection,
    pub(crate) format: DependencyIndexFormatProjection,
}

/// Derive the canonical path-free identity inputs for a resolved branch
/// workspace and its effective dependency inventory.
///
/// Schema-v4 inventories retain semantic feature/profile declarations in
/// `original_input`; this helper parses those declarations and records the
/// transitive active feature set for the `rust-toolchain` profile. A schema-v3
/// document is already an effective lock and has no feature/profile metadata,
/// so its explicitly represented active feature set is empty. Raw lockfile
/// text and formatting never enter the returned identity inputs.
///
/// # Errors
///
/// Returns an error when the inventory document cannot be interpreted, the
/// resolved Java context does not use the current Java parser fingerprint, or
/// the effective loader dependency does not contain exactly one semantic
/// loader toolchain version.
pub(crate) fn derive_dependency_symbol_index_projection_inputs(
    inventory: &DependencyInventory,
    analysis_context: &JavaAnalysisContextOutput,
) -> eyre::Result<DependencySymbolIndexProjectionInputs> {
    if analysis_context.parser_fingerprint != JAVA_PARSER_FINGERPRINT {
        eyre::bail!(
            "resolved Java analysis parser fingerprint `{}` does not match `{JAVA_PARSER_FINGERPRINT}`",
            analysis_context.parser_fingerprint
        );
    }
    let (parser, parser_version) = JAVA_PARSER_FINGERPRINT.split_once('/').ok_or_else(|| {
        eyre::eyre!(
            "Java parser fingerprint `{JAVA_PARSER_FINGERPRINT}` must use <parser>/<version> form"
        )
    })?;
    let loader_id = inventory.lockfile.platform.loader_dependency.clone();
    let loader = inventory.dependency(&loader_id)?;
    let loader_version = semantic_platform_version(loader, ToolchainComponentKindV3::Loader)?;
    let active_features = active_rust_toolchain_features(inventory)?;

    Ok(DependencySymbolIndexProjectionInputs {
        context: DependencyIndexContextProjection {
            minecraft_version: analysis_context.minecraft_version.clone(),
            loader_id,
            loader_version,
            java_release: analysis_context.java_release.clone(),
            toolchain_profile: DEPENDENCY_SYMBOL_INDEX_TOOLCHAIN_PROFILE.to_owned(),
            active_features,
        },
        parser: DependencyIndexParserProjection {
            parser: parser.to_owned(),
            parser_version: parser_version.to_owned(),
            grammar_fingerprint: JAVA_PARSER_FINGERPRINT.to_owned(),
        },
        format: DependencyIndexFormatProjection {
            store_format_version: DEPENDENCY_SYMBOL_INDEX_STORE_FORMAT_VERSION,
            payload_schema: DEPENDENCY_JAVA_SYMBOL_INDEX_STREAM_SCHEMA.to_owned(),
            index_algorithm_fingerprint: format!(
                "{DEPENDENCY_JAVA_SYMBOL_INDEX_ALGORITHM_FINGERPRINT};body={DEPENDENCY_JAVA_SYMBOL_INDEX_BODY_SCHEMA}"
            ),
        },
    })
}

fn semantic_platform_version(
    dependency: &DependencyV3,
    expected_kind: ToolchainComponentKindV3,
) -> eyre::Result<String> {
    let versions = dependency
        .components
        .iter()
        .filter_map(|component| match &component.declaration.acquisition {
            ComponentAcquisitionV3::Toolchain(acquisition) if acquisition.kind == expected_kind => {
                Some(acquisition.requested_version.clone())
            }
            _ => None,
        })
        .collect::<BTreeSet<_>>();
    if versions.len() != 1 {
        eyre::bail!(
            "platform dependency `{}` must declare exactly one distinct {} toolchain version; found {}",
            dependency.id,
            toolchain_kind_label(expected_kind),
            versions.len()
        );
    }
    Ok(versions.into_iter().next().expect("one checked version"))
}

fn active_rust_toolchain_features(inventory: &DependencyInventory) -> eyre::Result<Vec<String>> {
    match parse_document(&inventory.original_input)? {
        ToolchainLockfileDocument::V4(lockfile) => {
            active_features_for_profile(&lockfile, DEPENDENCY_SYMBOL_INDEX_TOOLCHAIN_PROFILE)
        }
        ToolchainLockfileDocument::V3(_) => Ok(Vec::new()),
        ToolchainLockfileDocument::V1(_) | ToolchainLockfileDocument::V2 { .. } => eyre::bail!(
            "dependency symbol index projection requires a schema-v3 or schema-v4 lockfile"
        ),
    }
}

fn active_features_for_profile(
    lockfile: &ArtifactLockfileV4,
    profile_id: &str,
) -> eyre::Result<Vec<String>> {
    let profile = lockfile
        .profiles
        .iter()
        .find(|profile| profile.id == profile_id)
        .ok_or_else(|| eyre::eyre!("unknown lockfile profile `{profile_id}`"))?;
    let features = lockfile
        .features
        .iter()
        .map(|feature| (feature.id.as_str(), feature))
        .collect::<BTreeMap<_, _>>();
    let mut active = BTreeSet::new();
    let mut visiting = BTreeSet::new();
    for feature in &profile.features {
        visit_active_feature(feature, &features, &mut active, &mut visiting)?;
    }
    Ok(active.into_iter().map(str::to_owned).collect())
}

fn visit_active_feature<'a>(
    feature_id: &'a str,
    features: &BTreeMap<&'a str, &'a FeatureV4>,
    active: &mut BTreeSet<&'a str>,
    visiting: &mut BTreeSet<&'a str>,
) -> eyre::Result<()> {
    if active.contains(feature_id) {
        return Ok(());
    }
    let feature = features
        .get(feature_id)
        .ok_or_else(|| eyre::eyre!("unknown lockfile feature `{feature_id}`"))?;
    if !visiting.insert(feature_id) {
        eyre::bail!("lockfile feature dependency cycle includes `{feature_id}`");
    }
    for required in &feature.requires {
        visit_active_feature(required, features, active, visiting)?;
    }
    visiting.remove(feature_id);
    active.insert(feature_id);
    Ok(())
}

/// Project an already-effective dependency inventory into the immutable
/// dependency-index identity model.
///
/// Provider declaration order is priority order. The first provider for each
/// component is therefore the unfiltered preferred provider, matching the
/// existing dependency-source preflight contract. Provider materialization
/// status and concrete searchable roots are intentionally not identity inputs.
///
/// # Errors
///
/// Returns an error when the effective lock is invalid, a provider priority
/// cannot fit the portable representation, or the resulting path-free
/// projection fails identity validation.
#[cfg(test)]
pub(crate) fn project_dependency_symbol_index_identity(
    inventory: &DependencyInventory,
    inputs: DependencySymbolIndexProjectionInputs,
) -> eyre::Result<DependencySymbolIndexIdentityProjection> {
    let locked_artifact_sources = derive_locked_loader_artifact_sources(inventory)?;
    project_dependency_symbol_index_identity_with_locked_sources(
        inventory,
        inputs,
        &locked_artifact_sources,
    )
}

pub(crate) fn project_dependency_symbol_index_identity_with_locked_sources(
    inventory: &DependencyInventory,
    inputs: DependencySymbolIndexProjectionInputs,
    locked_artifact_sources: &[LockedArtifactSource],
) -> eyre::Result<DependencySymbolIndexIdentityProjection> {
    inventory.lockfile.validate()?;
    let projection = DependencySymbolIndexIdentityProjection {
        schema: DEPENDENCY_SYMBOL_INDEX_IDENTITY_SCHEMA.to_owned(),
        effective_lock: EffectiveDependencyLockProjection {
            minecraft_dependency: inventory.lockfile.platform.minecraft_dependency.clone(),
            loader_dependency: inventory.lockfile.platform.loader_dependency.clone(),
            repositories: inventory
                .lockfile
                .repositories
                .iter()
                .map(|repository| DependencyRepositoryProjection {
                    id: repository.id.clone(),
                    url: repository.url.clone(),
                })
                .collect(),
            dependencies: inventory
                .dependencies()
                .map(|dependency| {
                    project_dependency(inventory, dependency, locked_artifact_sources)
                })
                .collect::<eyre::Result<Vec<_>>>()?,
        },
        context: inputs.context,
        parser: inputs.parser,
        format: inputs.format,
    }
    .normalized();
    projection.validate()?;
    Ok(projection)
}

fn project_dependency(
    inventory: &DependencyInventory,
    dependency: &DependencyV3,
    locked_artifact_sources: &[LockedArtifactSource],
) -> eyre::Result<DependencyProjection> {
    let mut components = dependency
        .components
        .iter()
        .map(|component| project_component(inventory, component))
        .collect::<eyre::Result<Vec<_>>>()?;
    components.extend(
        locked_artifact_sources
            .iter()
            .filter(|source| source.dependency_id == dependency.id)
            .map(|source| project_locked_artifact_source(inventory, source))
            .collect::<eyre::Result<Vec<_>>>()?,
    );
    Ok(DependencyProjection {
        id: dependency.id.clone(),
        kind: kind_label(dependency.kind).to_owned(),
        role: role_label(dependency.role).to_owned(),
        components,
    })
}

fn project_locked_artifact_source(
    inventory: &DependencyInventory,
    source: &LockedArtifactSource,
) -> eyre::Result<DependencyComponentProjection> {
    let artifact = inventory
        .artifact_by_id(&source.artifact_id)
        .ok_or_else(|| eyre::eyre!("Unknown locked source artifact `{}`", source.artifact_id))?;
    let provider = SourceProviderV3::Decompile(source.provider.clone());
    let mut acquisition = vec![
        property("acquisition-kind", "derived-locked-artifact"),
        property("locked-coordinate", &source.coordinate),
        property("derived-artifact-id", &source.artifact_id),
        property("derived-expected-hash", &artifact.hash.to_string()),
    ];
    if let Some(repository_id) = &source.repository_id {
        acquisition.push(property("repository-id", repository_id));
    }
    Ok(DependencyComponentProjection {
        id: source.component_id.clone(),
        scopes: vec!["compile".to_owned(), "runtime".to_owned()],
        acquisition,
        artifact: DependencyArtifactProjection {
            id: artifact.id.clone(),
            content_hash: artifact.hash.to_string(),
            resolved_coordinate: Some(source.coordinate.clone()),
            provenance: artifact_provenance_label(artifact.provenance).to_owned(),
        },
        preferred_provider: Some(project_dependency_source_provider(
            SourceProviderView::new(inventory, &provider, 0),
        )?),
    })
}

fn project_component(
    inventory: &DependencyInventory,
    component: &DependencyComponentV3,
) -> eyre::Result<DependencyComponentProjection> {
    let artifact = inventory.artifact(component);
    Ok(DependencyComponentProjection {
        id: component.id.clone(),
        scopes: component
            .declaration
            .scopes
            .iter()
            .map(|scope| scope_label(*scope).to_owned())
            .collect(),
        acquisition: project_component_semantics(component),
        artifact: DependencyArtifactProjection {
            id: artifact.id.clone(),
            content_hash: artifact.hash.to_string(),
            resolved_coordinate: component.derived_checks.resolved_coordinate.clone(),
            provenance: artifact_provenance_label(artifact.provenance).to_owned(),
        },
        preferred_provider: inventory
            .source_providers(component)
            .next()
            .map(project_dependency_source_provider)
            .transpose()?,
    })
}

fn project_component_semantics(
    component: &DependencyComponentV3,
) -> Vec<DependencySemanticProperty> {
    let mut properties = Vec::new();
    match &component.declaration.acquisition {
        ComponentAcquisitionV3::Maven(acquisition) => {
            push_property(&mut properties, "acquisition-kind", "maven");
            push_property(
                &mut properties,
                "requested-coordinate",
                &acquisition.requested_coordinate,
            );
            push_property(&mut properties, "repository-id", &acquisition.repository_id);
        }
        ComponentAcquisitionV3::CurseForge(acquisition) => {
            push_property(&mut properties, "acquisition-kind", "curse-forge");
            push_property(
                &mut properties,
                "project-id",
                &acquisition.project_id.to_string(),
            );
            push_property(&mut properties, "file-id", &acquisition.file_id.to_string());
            push_property(&mut properties, "slug", &acquisition.slug);
            push_property(&mut properties, "repository-id", &acquisition.repository_id);
        }
        ComponentAcquisitionV3::Http(acquisition) => {
            push_property(&mut properties, "acquisition-kind", "http");
            push_property(&mut properties, "url", &acquisition.url);
        }
        ComponentAcquisitionV3::Toolchain(acquisition) => {
            push_property(&mut properties, "acquisition-kind", "toolchain");
            push_property(
                &mut properties,
                "toolchain-kind",
                toolchain_kind_label(acquisition.kind),
            );
            push_property(
                &mut properties,
                "requested-version",
                &acquisition.requested_version,
            );
        }
        ComponentAcquisitionV3::SourceBuild(acquisition) => {
            push_property(&mut properties, "acquisition-kind", "source-build");
            push_property(
                &mut properties,
                "source-artifact-id",
                &acquisition.artifact_id,
            );
        }
    }
    push_property(
        &mut properties,
        "artifact-treatment",
        artifact_treatment_label(component.declaration.artifact_treatment),
    );
    push_property(
        &mut properties,
        "data-run-policy",
        data_run_policy_label(component.declaration.data_run_policy),
    );
    push_property(
        &mut properties,
        "derived-artifact-id",
        &component.derived_checks.artifact_id,
    );
    push_property(
        &mut properties,
        "derived-expected-hash",
        &component.derived_checks.expected_hash.to_string(),
    );
    if let Some(coordinate) = &component.derived_checks.resolved_coordinate {
        push_property(&mut properties, "derived-resolved-coordinate", coordinate);
    }
    if let Some(bundle) = &component.declaration.bundle {
        push_property(
            &mut properties,
            "bundle-accepted-version-range",
            &bundle.accepted_version_range,
        );
        push_property(
            &mut properties,
            "bundle-artifact-version",
            &bundle.artifact_version,
        );
        push_property(
            &mut properties,
            "bundle-is-obfuscated",
            if bundle.is_obfuscated {
                "true"
            } else {
                "false"
            },
        );
    }
    properties
}

/// Project one preferred provider view without observing its concrete tree,
/// searchable roots, cache status, or unavailable reason.
///
/// # Errors
///
/// Returns an error if the provider's declaration priority does not fit in a
/// portable `u64`.
pub(crate) fn project_dependency_source_provider(
    view: SourceProviderView<'_>,
) -> eyre::Result<DependencySourceProviderProjection> {
    let (portable_roots, declaration, derived_checks) = match view.definition() {
        SourceProviderV3::MavenSources(provider) => (
            provider.declaration.roots.clone(),
            vec![
                property(
                    "requested-coordinate",
                    &provider.declaration.requested_coordinate,
                ),
                property("repository-id", &provider.declaration.repository_id),
            ],
            vec![
                property(
                    "resolved-coordinate",
                    &provider.derived_checks.resolved_coordinate,
                ),
                property("url", &provider.derived_checks.url),
                property("hash", &provider.derived_checks.hash.to_string()),
            ],
        ),
        SourceProviderV3::Git(provider) => (
            provider.declaration.roots.clone(),
            vec![
                property("remote-url", &provider.declaration.remote_url),
                property(
                    "requested-revision",
                    &provider.declaration.requested_revision,
                ),
            ],
            vec![property("commit", &provider.derived_checks.commit)],
        ),
        SourceProviderV3::Decompile(provider) => (
            provider.declaration.roots.clone(),
            Vec::new(),
            vec![
                property(
                    "binary-artifact-id",
                    &provider.derived_checks.binary_artifact_id,
                ),
                property(
                    "decompiler-artifact-id",
                    &provider.derived_checks.decompiler_artifact_id,
                ),
                property("fingerprint", &provider.derived_checks.fingerprint),
            ],
        ),
        SourceProviderV3::PlatformPipeline(provider) => (
            provider.declaration.roots.clone(),
            vec![property(
                "toolchain-kind",
                toolchain_kind_label(provider.declaration.kind),
            )],
            vec![property(
                "fingerprint",
                &provider.derived_checks.fingerprint,
            )],
        ),
    };
    Ok(DependencySourceProviderProjection {
        id: view.id().to_owned(),
        kind: source_provider_kind(view.kind()),
        priority: u64::try_from(view.priority())?,
        portable_roots,
        declaration,
        derived_checks,
    })
}

fn push_property(properties: &mut Vec<DependencySemanticProperty>, key: &str, value: &str) {
    properties.push(property(key, value));
}

fn property(key: &str, value: &str) -> DependencySemanticProperty {
    DependencySemanticProperty {
        key: key.to_owned(),
        value: value.to_owned(),
    }
}

const fn source_provider_kind(kind: SourceProviderKind) -> DependencySourceProviderKind {
    match kind {
        SourceProviderKind::MavenSources => DependencySourceProviderKind::MavenSources,
        SourceProviderKind::Git => DependencySourceProviderKind::Git,
        SourceProviderKind::Decompile => DependencySourceProviderKind::Decompile,
        SourceProviderKind::PlatformPipeline => DependencySourceProviderKind::PlatformPipeline,
    }
}

const fn toolchain_kind_label(kind: ToolchainComponentKindV3) -> &'static str {
    match kind {
        ToolchainComponentKindV3::Minecraft => "minecraft",
        ToolchainComponentKindV3::Loader => "loader",
    }
}

const fn artifact_treatment_label(treatment: ArtifactTreatmentV3) -> &'static str {
    match treatment {
        ArtifactTreatmentV3::LoaderManagedMod => "loader-managed-mod",
        ArtifactTreatmentV3::Plain => "plain",
    }
}

const fn data_run_policy_label(policy: DataRunPolicyV3) -> &'static str {
    match policy {
        DataRunPolicyV3::Exclude => "exclude",
        DataRunPolicyV3::Include => "include",
    }
}

const fn artifact_provenance_label(provenance: ArtifactProvenanceV3) -> &'static str {
    match provenance {
        ArtifactProvenanceV3::RemoteMaven => "remote-maven",
        ArtifactProvenanceV3::RemoteHttp => "remote-http",
        ArtifactProvenanceV3::ToolchainGenerated => "toolchain-generated",
        ArtifactProvenanceV3::SourceBuild => "source-build",
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::branch_targets::BranchName;
    use crate::branch_targets::MinecraftVersion;
    use crate::branch_targets::WorktreePath;
    use crate::branch_targets::WorktreeTarget;
    use crate::jar_build::hash::ContentHash;
    use crate::jar_build::hash::ContentHashAlgorithm;
    use crate::java_analysis::JavaClasspathMode;
    use crate::paths::CacheHome;
    use crate::toolchain_lockfile_schema::read_current;
    use std::path::PathBuf;

    #[test]
    fn dependency_index_projection_inputs_derive_real_branch_and_lock_semantics() {
        let mut inventory = fixture("real-inputs", "replaced below");
        inventory.original_input = checked_in_lockfile().to_owned();

        let inputs = derive_dependency_symbol_index_projection_inputs(
            &inventory,
            &analysis_context(JAVA_PARSER_FINGERPRINT),
        )
        .expect("derived projection inputs");

        assert_eq!(inputs.context.minecraft_version, "1.19.2");
        assert_eq!(inputs.context.loader_id, "forge");
        assert_eq!(inputs.context.loader_version, "1.19.2-43.4.0");
        assert_eq!(inputs.context.java_release, "17");
        assert_eq!(
            inputs.context.toolchain_profile,
            DEPENDENCY_SYMBOL_INDEX_TOOLCHAIN_PROFILE
        );
        assert_eq!(inputs.context.active_features, ["rust"]);
        assert_eq!(inputs.parser.parser, "arborium-java");
        assert_eq!(inputs.parser.parser_version, "2.18.1");
        assert_eq!(inputs.parser.grammar_fingerprint, JAVA_PARSER_FINGERPRINT);
        assert_eq!(
            inputs.format.store_format_version,
            DEPENDENCY_SYMBOL_INDEX_STORE_FORMAT_VERSION
        );
        assert_eq!(
            inputs.format.payload_schema,
            DEPENDENCY_JAVA_SYMBOL_INDEX_STREAM_SCHEMA
        );
        assert_eq!(
            inputs.format.index_algorithm_fingerprint,
            format!(
                "{DEPENDENCY_JAVA_SYMBOL_INDEX_ALGORITHM_FINGERPRINT};body={DEPENDENCY_JAVA_SYMBOL_INDEX_BODY_SCHEMA}"
            )
        );
    }

    #[test]
    fn dependency_index_projection_inputs_explicitly_use_no_features_for_v3() {
        let mut inventory = fixture("v3-inputs", "replaced below");
        inventory.original_input =
            facet_json::to_string_pretty(&inventory.lockfile).expect("v3 lockfile JSON");

        let inputs = derive_dependency_symbol_index_projection_inputs(
            &inventory,
            &analysis_context(JAVA_PARSER_FINGERPRINT),
        )
        .expect("derived v3 projection inputs");

        assert!(inputs.context.active_features.is_empty());
    }

    #[test]
    fn dependency_index_projection_inputs_reject_stale_parser_context() {
        let mut inventory = fixture("stale-parser", "replaced below");
        inventory.original_input = checked_in_lockfile().to_owned();

        let error = derive_dependency_symbol_index_projection_inputs(
            &inventory,
            &analysis_context("arborium-java/stale"),
        )
        .expect_err("stale parser context must fail");

        assert!(error.to_string().contains(JAVA_PARSER_FINGERPRINT));
    }

    #[test]
    fn dependency_index_projection_covers_every_effective_component_and_preferred_provider() {
        let inventory = fixture("first", "raw formatting one");
        let projection = project(&inventory);
        let locked_artifact_sources =
            derive_locked_loader_artifact_sources(&inventory).expect("locked artifact sources");
        assert_eq!(
            projection.effective_lock.dependencies.len(),
            inventory.lockfile.dependencies.len()
        );
        assert_eq!(
            projection
                .effective_lock
                .dependencies
                .iter()
                .map(|dependency| dependency.components.len())
                .sum::<usize>(),
            inventory
                .lockfile
                .dependencies
                .iter()
                .map(|dependency| dependency.components.len())
                .sum::<usize>()
                + locked_artifact_sources.len()
        );

        for dependency in &inventory.lockfile.dependencies {
            let projected_dependency = projection
                .effective_lock
                .dependencies
                .iter()
                .find(|candidate| candidate.id == dependency.id)
                .expect("projected dependency");
            for component in &dependency.components {
                let projected_component = projected_dependency
                    .components
                    .iter()
                    .find(|candidate| candidate.id == component.id)
                    .expect("projected component");
                let expected_provider = inventory.source_providers(component).next();
                assert_eq!(
                    projected_component
                        .preferred_provider
                        .as_ref()
                        .map(|provider| provider.id.as_str()),
                    expected_provider.map(SourceProviderView::id)
                );
                if let Some(provider) = &projected_component.preferred_provider {
                    assert_eq!(provider.priority, 0);
                }
            }
        }

        let forge = projection
            .effective_lock
            .dependencies
            .iter()
            .find(|dependency| dependency.id == "forge")
            .expect("Forge dependency projection");
        let javafml = forge
            .components
            .iter()
            .find(|component| component.id == "javafmllanguage")
            .expect("locked javafmllanguage source projection");
        assert_eq!(
            javafml.artifact.content_hash,
            "blake3:84a169e7cb2f76d914ac3b5432055cdd5b0e34c0"
        );
        let provider = javafml
            .preferred_provider
            .as_ref()
            .expect("derived decompile provider");
        assert_eq!(provider.kind, DependencySourceProviderKind::Decompile);
        assert!(provider.derived_checks.iter().any(|property| {
            property.key == "decompiler-artifact-id"
                && property.value == "org-vineflower-vineflower-1-12-0-865bc756"
        }));
        assert!(provider.derived_checks.iter().any(|property| {
            property.key == "fingerprint" && property.value.starts_with("blake3:")
        }));
    }

    #[test]
    fn dependency_index_projection_is_deterministic_under_lock_collection_reordering() {
        let first = fixture("first", "raw formatting one");
        let mut reordered = fixture("second", "raw formatting two");
        reordered.lockfile.repositories.reverse();
        reordered.lockfile.dependencies.reverse();
        reordered.lockfile.artifacts.reverse();
        for dependency in &mut reordered.lockfile.dependencies {
            dependency.components.reverse();
        }

        assert_eq!(project(&first), project(&reordered));
    }

    #[test]
    fn dependency_index_projection_omits_raw_and_machine_path_inputs() {
        let first = fixture("first-location", "RAW-LOCK-TEXT-FIRST");
        let mut relocated = fixture("second-location", "RAW-LOCK-TEXT-SECOND");
        relocate_omitted_cache_paths(&mut relocated);

        let first_projection = project(&first);
        let relocated_projection = project(&relocated);
        assert_eq!(first_projection, relocated_projection);
        let json = facet_json::to_string(&first_projection).expect("projection JSON");
        for forbidden in [
            "RAW-LOCK-TEXT-FIRST",
            "first-location",
            "$sfm-cache",
            "tree_cache_path",
            "archive_cache_path",
            "cache_path",
        ] {
            assert!(!json.contains(forbidden), "projection leaked {forbidden}");
        }
    }

    #[test]
    fn dependency_index_projection_changes_for_artifact_provider_and_preference_semantics() {
        let baseline_inventory = fixture("baseline", "raw");
        let baseline = project(&baseline_inventory);

        let mut artifact_changed = fixture("artifact", "raw");
        let replacement_hash =
            ContentHash::from_bytes(b"different artifact", ContentHashAlgorithm::Blake3);
        let component = &mut artifact_changed.lockfile.dependencies[0].components[0];
        let artifact_id = component.derived_checks.artifact_id.clone();
        component.derived_checks.expected_hash = replacement_hash;
        artifact_changed
            .lockfile
            .artifacts
            .iter_mut()
            .find(|artifact| artifact.id == artifact_id)
            .expect("component artifact")
            .hash = replacement_hash;
        assert_ne!(baseline, project(&artifact_changed));

        let mut provider_changed = fixture("provider", "raw");
        mutate_first_preferred_provider_semantics(&mut provider_changed);
        assert_ne!(baseline, project(&provider_changed));

        let mut preference_changed = fixture("preference", "raw");
        let providers = preference_changed
            .lockfile
            .dependencies
            .iter_mut()
            .flat_map(|dependency| &mut dependency.components)
            .find(|component| component.source_providers.len() > 1)
            .expect("fixture component with provider fallback");
        providers.source_providers.swap(0, 1);
        assert_ne!(baseline, project(&preference_changed));
    }

    #[test]
    fn dependency_index_provider_projection_includes_semantics_but_not_materialization() {
        let inventory = fixture("provider", "raw");
        let mut observed_kinds = Vec::new();
        for dependency in inventory.dependencies() {
            for component in &dependency.components {
                let Some(view) = inventory.source_providers(component).next() else {
                    continue;
                };
                let projected =
                    project_dependency_source_provider(view).expect("provider projection");
                observed_kinds.push(projected.kind);
                let json = facet_json::to_string(&projected).expect("provider JSON");
                assert!(!json.contains("cache_path"));
                assert!(!json.contains("tree_cache_path"));
                assert!(!json.contains("provider-location"));
                assert_eq!(projected.id, view.id());
                assert_eq!(projected.priority, u64::try_from(view.priority()).unwrap());
            }
        }
        observed_kinds.sort();
        observed_kinds.dedup();
        assert!(observed_kinds.contains(&DependencySourceProviderKind::Git));
        assert!(observed_kinds.contains(&DependencySourceProviderKind::Decompile));
        assert!(observed_kinds.contains(&DependencySourceProviderKind::PlatformPipeline));
    }

    fn project(inventory: &DependencyInventory) -> DependencySymbolIndexIdentityProjection {
        project_dependency_symbol_index_identity(inventory, inputs())
            .expect("dependency index projection")
    }

    fn inputs() -> DependencySymbolIndexProjectionInputs {
        DependencySymbolIndexProjectionInputs {
            context: DependencyIndexContextProjection {
                minecraft_version: "1.19.2".to_owned(),
                loader_id: "forge".to_owned(),
                loader_version: "43.4.0".to_owned(),
                java_release: "17".to_owned(),
                toolchain_profile: "rust-toolchain".to_owned(),
                active_features: vec!["rust".to_owned(), "main".to_owned()],
            },
            parser: DependencyIndexParserProjection {
                parser: "arborium-java".to_owned(),
                parser_version: "2.18.1".to_owned(),
                grammar_fingerprint: "blake3:grammar".to_owned(),
            },
            format: DependencyIndexFormatProjection {
                store_format_version: 1,
                payload_schema: "sfm.dependency-symbol-index-payload/1".to_owned(),
                index_algorithm_fingerprint: "java-index/1".to_owned(),
            },
        }
    }

    fn fixture(location: &str, original_input: &str) -> DependencyInventory {
        let input = checked_in_lockfile();
        DependencyInventory {
            target: WorktreeTarget {
                branch: BranchName::from("1.19.2"),
                worktree_path: WorktreePath::from(PathBuf::from(format!(
                    r"D:\synthetic\{location}\worktree"
                ))),
                core: true,
                mc_version: Some(MinecraftVersion::parse("1.19.2").expect("Minecraft version")),
            },
            lockfile_path: PathBuf::from(format!(
                r"D:\synthetic\{location}\sfm-toolchain.lock.json"
            )),
            cache_home: CacheHome(PathBuf::from(format!(r"D:\synthetic\{location}\cache"))),
            original_input: original_input.to_owned(),
            lockfile: read_current(input).expect("effective lock fixture"),
        }
    }

    fn checked_in_lockfile() -> &'static str {
        include_str!("../../../../minecraft/sfm-toolchain.lock.json")
    }

    fn analysis_context(parser_fingerprint: &str) -> JavaAnalysisContextOutput {
        JavaAnalysisContextOutput {
            branch: "1.19.2".to_owned(),
            minecraft_version: "1.19.2".to_owned(),
            java_release: "17".to_owned(),
            jdk: "java-17".to_owned(),
            source_roots: Vec::new(),
            source_sets: Vec::new(),
            source_exclusions: Vec::new(),
            classpath_mode: JavaClasspathMode::Branch,
            classpath_fingerprint: "classpath".to_owned(),
            parser_fingerprint: parser_fingerprint.to_owned(),
            index_fingerprint: "workspace".to_owned(),
        }
    }

    fn relocate_omitted_cache_paths(inventory: &mut DependencyInventory) {
        for dependency in &mut inventory.lockfile.dependencies {
            for component in &mut dependency.components {
                let replacement = PathBuf::from(format!(
                    "$sfm-cache/relocated/{}/{}",
                    dependency.id, component.id
                ));
                component.derived_checks.cache_path.clone_from(&replacement);
                let artifact_id = component.derived_checks.artifact_id.clone();
                inventory
                    .lockfile
                    .artifacts
                    .iter_mut()
                    .find(|artifact| artifact.id == artifact_id)
                    .expect("component artifact")
                    .cache_path = replacement;
                for provider in &mut component.source_providers {
                    match provider {
                        SourceProviderV3::MavenSources(provider) => {
                            provider.derived_checks.archive_cache_path = PathBuf::from(format!(
                                "$sfm-cache/relocated/{}/{}/sources.jar",
                                dependency.id, component.id
                            ));
                            provider.derived_checks.tree_cache_path = PathBuf::from(format!(
                                "$sfm-cache/relocated/{}/{}/tree",
                                dependency.id, component.id
                            ));
                        }
                        SourceProviderV3::Git(provider) => {
                            provider.derived_checks.repository_cache_path = PathBuf::from(format!(
                                "$sfm-cache/relocated/{}/{}/repository.git",
                                dependency.id, component.id
                            ));
                            provider.derived_checks.tree_cache_path = PathBuf::from(format!(
                                "$sfm-cache/relocated/{}/{}/tree",
                                dependency.id, component.id
                            ));
                        }
                        SourceProviderV3::Decompile(provider) => {
                            provider.derived_checks.tree_cache_path = PathBuf::from(format!(
                                "$sfm-cache/relocated/{}/{}/tree",
                                dependency.id, component.id
                            ));
                        }
                        SourceProviderV3::PlatformPipeline(provider) => {
                            provider.derived_checks.tree_cache_path = PathBuf::from(format!(
                                "$sfm-cache/relocated/{}/{}/tree",
                                dependency.id, component.id
                            ));
                        }
                    }
                }
            }
        }
    }

    fn mutate_first_preferred_provider_semantics(inventory: &mut DependencyInventory) {
        let provider = inventory
            .lockfile
            .dependencies
            .iter_mut()
            .flat_map(|dependency| &mut dependency.components)
            .find_map(|component| component.source_providers.first_mut())
            .expect("preferred provider");
        match provider {
            SourceProviderV3::MavenSources(provider) => {
                provider
                    .derived_checks
                    .resolved_coordinate
                    .push_str("-changed");
            }
            SourceProviderV3::Git(provider) => provider.derived_checks.commit.push_str("changed"),
            SourceProviderV3::Decompile(provider) => {
                provider.derived_checks.fingerprint.push_str("-changed");
            }
            SourceProviderV3::PlatformPipeline(provider) => {
                provider.derived_checks.fingerprint.push_str("-changed");
            }
        }
    }
}
