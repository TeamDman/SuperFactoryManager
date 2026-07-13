use crate::jar_build::DependencyLockEntry;
use crate::toolchain_lockfile_schema::version::v2::ArtifactLockEntryV2;
use crate::toolchain_lockfile_schema::version::v2::ArtifactLockfileV2;
use crate::toolchain_lockfile_schema::version::v2::ComponentMigrationHintV2;
use crate::toolchain_lockfile_schema::version::v2::MigrationHintsV2;
use crate::toolchain_lockfile_schema::version::v3::ArtifactLockfileV3;
use crate::toolchain_lockfile_schema::version::v3::ArtifactOwnerV3;
use crate::toolchain_lockfile_schema::version::v3::ArtifactProvenanceV3;
use crate::toolchain_lockfile_schema::version::v3::ArtifactPurposeV3;
use crate::toolchain_lockfile_schema::version::v3::ArtifactV3;
use crate::toolchain_lockfile_schema::version::v3::ComponentAcquisitionV3;
use crate::toolchain_lockfile_schema::version::v3::ComponentDeclarationV3;
use crate::toolchain_lockfile_schema::version::v3::ComponentDerivedChecksV3;
use crate::toolchain_lockfile_schema::version::v3::CurseForgeAcquisitionV3;
use crate::toolchain_lockfile_schema::version::v3::DependencyComponentV3;
use crate::toolchain_lockfile_schema::version::v3::DependencyKindV3;
use crate::toolchain_lockfile_schema::version::v3::DependencyScopeV3;
use crate::toolchain_lockfile_schema::version::v3::DependencyV3;
use crate::toolchain_lockfile_schema::version::v3::LockfilePolicyV3;
use crate::toolchain_lockfile_schema::version::v3::MavenAcquisitionV3;
use crate::toolchain_lockfile_schema::version::v3::PlatformV3;
use crate::toolchain_lockfile_schema::version::v3::RepositoryV3;
use crate::toolchain_lockfile_schema::version::v3::SCHEMA_VERSION;
use crate::toolchain_lockfile_schema::version::v3::WeakArtifactValidationV3;
use facet::Facet;
use std::collections::BTreeMap;
use std::collections::BTreeSet;

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub(crate) struct MigrationDiagnostic {
    pub(crate) path: String,
    pub(crate) message: String,
    #[facet(default)]
    pub(crate) legacy_dependency_index: Option<usize>,
    #[facet(default)]
    pub(crate) configuration: Option<String>,
    #[facet(default)]
    pub(crate) coordinate: Option<String>,
    #[facet(default)]
    pub(crate) candidates: Vec<String>,
    pub(crate) remediation: String,
}

impl ArtifactLockfileV2 {
    pub(crate) fn migration_diagnostics(&self) -> Vec<MigrationDiagnostic> {
        let mut diagnostics = Vec::new();
        let Some(hints) = &self.migration_hints else {
            diagnostics.push(diagnostic(
                "migration_hints",
                "v2 lockfile has no migration hints",
                "add migration_hints and rerun dependency migrate --check",
            ));
            return diagnostics;
        };

        require_non_empty(
            hints.minecraft_dependency_id.as_deref(),
            "migration_hints.minecraft_dependency_id",
            "Minecraft dependency ID is missing",
            "set this to the logical dependency whose kind is minecraft",
            &mut diagnostics,
        );
        require_non_empty(
            hints.loader_dependency_id.as_deref(),
            "migration_hints.loader_dependency_id",
            "loader dependency ID is missing",
            "set this to the logical dependency whose kind is loader",
            &mut diagnostics,
        );
        let repository_names = self
            .repositories
            .iter()
            .map(|repository| repository.name.as_str())
            .collect();
        validate_dependencies(
            hints,
            &self.dependencies,
            &self.artifacts,
            &repository_names,
            &mut diagnostics,
        );
        diagnostics
            .sort_by(|left, right| (&left.path, &left.message).cmp(&(&right.path, &right.message)));
        diagnostics
    }

    pub(crate) fn migrate_to_v3(&self) -> eyre::Result<ArtifactLockfileV3> {
        let diagnostics = self.migration_diagnostics();
        if !diagnostics.is_empty() {
            eyre::bail!(
                "cannot construct schema v3 while {} migration diagnostic(s) remain",
                diagnostics.len()
            );
        }
        let hints = self
            .migration_hints
            .as_ref()
            .expect("diagnostics require migration hints");
        let (repositories, repository_ids) = migrate_repositories(self);
        let artifact_ids = create_artifact_ids(&self.artifacts);
        let references = collect_component_artifact_references(hints, self)?;
        let dependencies =
            migrate_dependencies(hints, self, &repository_ids, &artifact_ids, &references)?;
        let artifacts = migrate_artifacts(self, &repository_ids, &artifact_ids, &references);
        let lockfile = ArtifactLockfileV3 {
            schema_version: SCHEMA_VERSION,
            platform: PlatformV3 {
                minecraft_dependency: hints
                    .minecraft_dependency_id
                    .clone()
                    .expect("diagnostics require Minecraft dependency ID"),
                loader_dependency: hints
                    .loader_dependency_id
                    .clone()
                    .expect("diagnostics require loader dependency ID"),
            },
            policy: LockfilePolicyV3 {
                allow_local_artifact_cache: self.allow_local_artifact_cache,
            },
            repositories,
            dependencies,
            artifacts,
        };
        lockfile.validate()?;
        Ok(lockfile)
    }
}

#[derive(Clone)]
struct ComponentArtifactReference {
    dependency_id: String,
    component_id: String,
    artifact_index: usize,
    scopes: Vec<DependencyScopeV3>,
}

fn migrate_repositories(
    lockfile: &ArtifactLockfileV2,
) -> (Vec<RepositoryV3>, BTreeMap<&str, String>) {
    let mut used = BTreeSet::new();
    let mut ids = BTreeMap::new();
    let repositories = lockfile
        .repositories
        .iter()
        .map(|repository| {
            let base = portable_id(&repository.name);
            let id = unique_id(&base, &mut used);
            ids.insert(repository.name.as_str(), id.clone());
            RepositoryV3 {
                id,
                url: repository.url.clone(),
            }
        })
        .collect();
    (repositories, ids)
}

fn create_artifact_ids(artifacts: &[ArtifactLockEntryV2]) -> Vec<String> {
    let mut used = BTreeSet::new();
    artifacts
        .iter()
        .enumerate()
        .map(|(index, artifact)| {
            let identity = artifact
                .coordinate
                .as_deref()
                .or_else(|| {
                    artifact
                        .cache_path
                        .file_stem()
                        .and_then(|value| value.to_str())
                })
                .map_or_else(|| format!("artifact-{index}"), portable_id);
            unique_id(
                &format!("{identity}-{}", artifact.hash.short_hex(8)),
                &mut used,
            )
        })
        .collect()
}

fn collect_component_artifact_references(
    hints: &MigrationHintsV2,
    lockfile: &ArtifactLockfileV2,
) -> eyre::Result<Vec<ComponentArtifactReference>> {
    let mut references = Vec::new();
    for dependency in &hints.dependencies {
        for component in &dependency.components {
            let artifact_index = component_artifact_index(component, lockfile)?;
            references.push(ComponentArtifactReference {
                dependency_id: dependency.id.clone(),
                component_id: component.id.clone(),
                artifact_index,
                scopes: component
                    .scopes
                    .clone()
                    .expect("diagnostics require semantic scopes"),
            });
        }
    }
    Ok(references)
}

fn component_artifact_index(
    component: &ComponentMigrationHintV2,
    lockfile: &ArtifactLockfileV2,
) -> eyre::Result<usize> {
    if let Some(index) = component.legacy_artifact_index {
        return Ok(index);
    }
    let row = component
        .legacy_dependency_indices
        .first()
        .and_then(|index| lockfile.dependencies.get(*index))
        .expect("diagnostics require component artifact evidence");
    lockfile
        .artifacts
        .iter()
        .position(|artifact| {
            artifact.coordinate.as_deref() == Some(row.resolved_notation.as_str())
                || artifact.cache_path == row.cache_path
        })
        .ok_or_else(|| eyre::eyre!("validated component artifact disappeared"))
}

fn migrate_dependencies(
    hints: &MigrationHintsV2,
    lockfile: &ArtifactLockfileV2,
    repository_ids: &BTreeMap<&str, String>,
    artifact_ids: &[String],
    references: &[ComponentArtifactReference],
) -> eyre::Result<Vec<DependencyV3>> {
    hints
        .dependencies
        .iter()
        .map(|dependency| {
            let components = dependency
                .components
                .iter()
                .map(|component| {
                    let reference = references
                        .iter()
                        .find(|reference| {
                            reference.dependency_id == dependency.id
                                && reference.component_id == component.id
                        })
                        .expect("component reference collected above");
                    let artifact = &lockfile.artifacts[reference.artifact_index];
                    let acquisition = component.acquisition.clone().unwrap_or_else(|| {
                        derive_acquisition(component, lockfile, artifact, repository_ids)
                    });
                    let resolved_coordinate = component
                        .legacy_dependency_indices
                        .first()
                        .and_then(|index| lockfile.dependencies.get(*index))
                        .map(|row| row.resolved_notation.clone())
                        .or_else(|| artifact.coordinate.clone());
                    Ok(DependencyComponentV3 {
                        id: component.id.clone(),
                        declaration: ComponentDeclarationV3 {
                            acquisition,
                            scopes: reference.scopes.clone(),
                            artifact_treatment: component
                                .artifact_treatment
                                .expect("diagnostics require artifact treatment"),
                            data_run_policy: component
                                .data_run_policy
                                .expect("diagnostics require data-run policy"),
                        },
                        derived_checks: ComponentDerivedChecksV3 {
                            artifact_id: artifact_ids[reference.artifact_index].clone(),
                            resolved_coordinate,
                            expected_hash: artifact.hash,
                            cache_path: artifact.cache_path.clone(),
                        },
                        source_providers: Vec::new(),
                    })
                })
                .collect::<eyre::Result<Vec<_>>>()?;
            Ok(DependencyV3 {
                id: dependency.id.clone(),
                kind: dependency
                    .kind
                    .expect("diagnostics require dependency kind"),
                role: dependency
                    .role
                    .expect("diagnostics require dependency role"),
                display_name: dependency.display_name.clone(),
                project_url: dependency.project_url.clone(),
                notes: dependency.notes.clone(),
                components,
            })
        })
        .collect()
}

fn derive_acquisition(
    component: &ComponentMigrationHintV2,
    lockfile: &ArtifactLockfileV2,
    artifact: &ArtifactLockEntryV2,
    repository_ids: &BTreeMap<&str, String>,
) -> ComponentAcquisitionV3 {
    let row = component
        .legacy_dependency_indices
        .first()
        .and_then(|index| lockfile.dependencies.get(*index))
        .expect("diagnostics require rows for derived acquisition");
    let repository_name = artifact
        .repository
        .as_deref()
        .expect("diagnostics require artifact repository");
    let repository_id = repository_ids
        .get(repository_name)
        .cloned()
        .expect("diagnostics require known artifact repository");
    if let Some(curse) = parse_curse_coordinate(&row.notation) {
        return ComponentAcquisitionV3::CurseForge(CurseForgeAcquisitionV3 {
            project_id: curse.project_id,
            file_id: curse.file_id,
            slug: curse.slug.to_owned(),
            repository_id,
        });
    }
    ComponentAcquisitionV3::Maven(MavenAcquisitionV3 {
        requested_coordinate: row.notation.clone(),
        repository_id,
    })
}

fn migrate_artifacts(
    lockfile: &ArtifactLockfileV2,
    repository_ids: &BTreeMap<&str, String>,
    artifact_ids: &[String],
    references: &[ComponentArtifactReference],
) -> Vec<ArtifactV3> {
    lockfile
        .artifacts
        .iter()
        .enumerate()
        .map(|(index, artifact)| {
            let owners: Vec<_> = references
                .iter()
                .filter(|reference| reference.artifact_index == index)
                .collect();
            let owner = match owners.as_slice() {
                [reference] => Some(ArtifactOwnerV3 {
                    dependency_id: reference.dependency_id.clone(),
                    component_id: reference.component_id.clone(),
                }),
                _ => None,
            };
            let mut purposes = BTreeSet::new();
            for reference in &owners {
                for scope in &reference.scopes {
                    purposes.insert(scope_purpose(*scope));
                }
            }
            if purposes.is_empty() {
                purposes.insert(ArtifactPurposeV3::Toolchain);
            }
            ArtifactV3 {
                id: artifact_ids[index].clone(),
                owner,
                purposes: purposes.into_iter().collect(),
                coordinate: artifact.coordinate.clone(),
                repository_id: artifact
                    .repository
                    .as_deref()
                    .and_then(|name| repository_ids.get(name).cloned()),
                url: artifact.url.clone(),
                hash: artifact.hash,
                cache_path: artifact.cache_path.clone(),
                provenance: artifact_provenance(artifact),
                weak: artifact.weak.as_ref().map(|weak| WeakArtifactValidationV3 {
                    metadata_path: weak.metadata_path.clone(),
                    mod_id: weak.mod_id.clone(),
                    version: weak.version.clone(),
                }),
            }
        })
        .collect()
}

fn scope_purpose(scope: DependencyScopeV3) -> ArtifactPurposeV3 {
    match scope {
        DependencyScopeV3::AnnotationProcessor
        | DependencyScopeV3::Codegen
        | DependencyScopeV3::Compile
        | DependencyScopeV3::Bundle => ArtifactPurposeV3::Build,
        DependencyScopeV3::Runtime => ArtifactPurposeV3::Runtime,
        DependencyScopeV3::GametestCompile | DependencyScopeV3::GametestRuntime => {
            ArtifactPurposeV3::Gametest
        }
        DependencyScopeV3::TestCompile
        | DependencyScopeV3::TestAnnotationProcessor
        | DependencyScopeV3::TestRuntime => ArtifactPurposeV3::Test,
    }
}

fn artifact_provenance(artifact: &ArtifactLockEntryV2) -> ArtifactProvenanceV3 {
    if artifact.source_build.is_some() {
        ArtifactProvenanceV3::SourceBuild
    } else if artifact.coordinate.is_some() {
        ArtifactProvenanceV3::RemoteMaven
    } else if artifact.url.is_some() {
        ArtifactProvenanceV3::RemoteHttp
    } else {
        ArtifactProvenanceV3::ToolchainGenerated
    }
}

fn portable_id(value: &str) -> String {
    let mut result = String::new();
    let mut separator = false;
    for character in value.chars() {
        if character.is_ascii_alphanumeric() {
            result.push(character.to_ascii_lowercase());
            separator = false;
        } else if !result.is_empty() && !separator {
            result.push('-');
            separator = true;
        }
    }
    while result.ends_with('-') {
        result.pop();
    }
    if result.is_empty() {
        "item".to_owned()
    } else {
        result
    }
}

fn unique_id(base: &str, used: &mut BTreeSet<String>) -> String {
    if used.insert(base.to_owned()) {
        return base.to_owned();
    }
    for suffix in 2usize.. {
        let candidate = format!("{base}-{suffix}");
        if used.insert(candidate.clone()) {
            return candidate;
        }
    }
    unreachable!("unbounded numeric suffixes must yield a unique ID")
}

struct CurseCoordinate<'a> {
    slug: &'a str,
    project_id: u64,
    file_id: u64,
}

fn parse_curse_coordinate(input: &str) -> Option<CurseCoordinate<'_>> {
    let parts: Vec<_> = input.split(':').collect();
    let ["curse.maven", artifact, file_id] = parts.as_slice() else {
        return None;
    };
    let (slug, project_id) = artifact.rsplit_once('-')?;
    if slug.is_empty() {
        return None;
    }
    Some(CurseCoordinate {
        slug,
        project_id: project_id.parse().ok()?,
        file_id: file_id.parse().ok()?,
    })
}

fn validate_dependencies(
    hints: &MigrationHintsV2,
    legacy: &[DependencyLockEntry],
    artifacts: &[ArtifactLockEntryV2],
    repository_names: &BTreeSet<&str>,
    diagnostics: &mut Vec<MigrationDiagnostic>,
) {
    let mut dependency_ids = BTreeSet::new();
    let mut row_owners: BTreeMap<usize, String> = BTreeMap::new();

    for (dependency_index, dependency) in hints.dependencies.iter().enumerate() {
        let path = format!("migration_hints.dependencies[{dependency_index}]");
        if dependency.id.trim().is_empty() {
            diagnostics.push(diagnostic(
                format!("{path}.id"),
                "logical dependency ID is empty",
                "assign a stable logical dependency ID",
            ));
        } else if !dependency_ids.insert(dependency.id.as_str()) {
            diagnostics.push(diagnostic(
                format!("{path}.id"),
                format!("logical dependency ID '{}' is duplicated", dependency.id),
                "give each logical dependency a unique ID",
            ));
        }
        if dependency.kind.is_none() {
            diagnostics.push(missing(
                format!("{path}.kind"),
                "dependency kind is missing",
                &["minecraft", "loader", "mod", "library", "tool"],
            ));
        }
        if dependency.role.is_none() {
            diagnostics.push(missing(
                format!("{path}.role"),
                "dependency role is missing",
                &["platform", "integration", "build", "test", "library"],
            ));
        }
        if dependency.components.is_empty() {
            diagnostics.push(diagnostic(
                format!("{path}.components"),
                "logical dependency has no components",
                "add a component and assign its legacy dependency row indexes",
            ));
        }

        let mut component_ids = BTreeSet::new();
        for (component_index, component) in dependency.components.iter().enumerate() {
            validate_component(
                component,
                &format!("{path}.components[{component_index}]"),
                &dependency.id,
                legacy,
                &mut component_ids,
                &mut row_owners,
                diagnostics,
            );
            validate_component_evidence(
                component,
                &format!("{path}.components[{component_index}]"),
                legacy,
                artifacts,
                repository_names,
                diagnostics,
            );
        }
    }

    validate_platform_reference(
        hints.minecraft_dependency_id.as_deref(),
        DependencyKindV3::Minecraft,
        "migration_hints.minecraft_dependency_id",
        hints,
        diagnostics,
    );
    validate_platform_reference(
        hints.loader_dependency_id.as_deref(),
        DependencyKindV3::Loader,
        "migration_hints.loader_dependency_id",
        hints,
        diagnostics,
    );

    for (index, row) in legacy.iter().enumerate() {
        if !row_owners.contains_key(&index) {
            diagnostics.push(row_diagnostic(
                format!("dependencies[{index}]"),
                "legacy dependency row is not assigned to a migration component",
                index,
                row,
                "add this index to exactly one component's legacy_dependency_indices",
            ));
        }
    }
}

fn validate_component<'a>(
    component: &'a ComponentMigrationHintV2,
    path: &str,
    dependency_id: &str,
    legacy: &[DependencyLockEntry],
    component_ids: &mut BTreeSet<&'a str>,
    row_owners: &mut BTreeMap<usize, String>,
    diagnostics: &mut Vec<MigrationDiagnostic>,
) {
    if component.id.trim().is_empty() {
        diagnostics.push(diagnostic(
            format!("{path}.id"),
            "component ID is empty",
            "assign a stable component ID",
        ));
    } else if !component_ids.insert(component.id.as_str()) {
        diagnostics.push(diagnostic(
            format!("{path}.id"),
            format!("component ID '{}' is duplicated", component.id),
            "give each component a unique ID within its dependency",
        ));
    }

    if component.legacy_dependency_indices.is_empty()
        && (component.acquisition.is_none() || component.legacy_artifact_index.is_none())
    {
        diagnostics.push(diagnostic(
            format!("{path}.legacy_dependency_indices"),
            "component has neither legacy rows nor complete explicit acquisition/artifact evidence",
            "assign legacy rows or populate acquisition and legacy_artifact_index",
        ));
    }
    for &index in &component.legacy_dependency_indices {
        let Some(row) = legacy.get(index) else {
            diagnostics.push(diagnostic(
                format!("{path}.legacy_dependency_indices"),
                format!("legacy dependency index {index} is out of range"),
                format!("use an index below {}", legacy.len()),
            ));
            continue;
        };
        let owner = format!("{dependency_id}/{}", component.id);
        if let Some(first_owner) = row_owners.insert(index, owner.clone()) {
            diagnostics.push(row_diagnostic(
                format!("{path}.legacy_dependency_indices"),
                format!("row is assigned to both '{first_owner}' and '{owner}'"),
                index,
                row,
                "remove the duplicate index so the row has exactly one owner",
            ));
        }
    }

    match component.scopes.as_deref() {
        None => diagnostics.push(missing(
            format!("{path}.scopes"),
            "semantic scopes are missing",
            &scope_candidates(component, legacy),
        )),
        Some([]) => diagnostics.push(diagnostic(
            format!("{path}.scopes"),
            "semantic scopes are empty",
            "add the scopes represented by the legacy rows",
        )),
        Some(scopes) => {
            let mut unique = BTreeSet::new();
            if scopes.iter().any(|scope| !unique.insert(scope)) {
                diagnostics.push(diagnostic(
                    format!("{path}.scopes"),
                    "semantic scopes contain duplicates",
                    "remove duplicate scopes",
                ));
            }
        }
    }
    if component.artifact_treatment.is_none() {
        diagnostics.push(missing(
            format!("{path}.artifact_treatment"),
            "artifact treatment is missing",
            &["loader-managed-mod", "plain"],
        ));
    }
    if component.data_run_policy.is_none() {
        diagnostics.push(missing(
            format!("{path}.data_run_policy"),
            "data-run policy is missing",
            &["exclude", "include"],
        ));
    }
}

fn validate_component_evidence(
    component: &ComponentMigrationHintV2,
    path: &str,
    legacy: &[DependencyLockEntry],
    artifacts: &[ArtifactLockEntryV2],
    repository_names: &BTreeSet<&str>,
    diagnostics: &mut Vec<MigrationDiagnostic>,
) {
    validate_explicit_artifact_index(component, path, artifacts, diagnostics);
    let rows: Vec<(usize, &DependencyLockEntry)> = component
        .legacy_dependency_indices
        .iter()
        .filter_map(|index| legacy.get(*index).map(|row| (*index, row)))
        .collect();
    if rows.is_empty() {
        validate_component_without_rows(component, path, diagnostics);
        return;
    }

    let requested: BTreeSet<&str> = rows.iter().map(|(_, row)| row.notation.as_str()).collect();
    let resolved: BTreeSet<&str> = rows
        .iter()
        .map(|(_, row)| row.resolved_notation.as_str())
        .collect();
    let cache_paths: BTreeSet<_> = rows.iter().map(|(_, row)| &row.cache_path).collect();
    if requested.len() != 1 {
        diagnostics.push(evidence_diagnostic(
            format!("{path}.legacy_dependency_indices"),
            "component rows have different requested coordinates",
            &rows,
            requested.iter().copied(),
            "split rows with different requested coordinates into separate components",
        ));
    }
    if resolved.len() != 1 {
        diagnostics.push(evidence_diagnostic(
            format!("{path}.legacy_dependency_indices"),
            "component rows have different resolved coordinates",
            &rows,
            resolved.iter().copied(),
            "split rows with different resolved coordinates into separate components",
        ));
    }
    if cache_paths.len() != 1 {
        let mut item = row_diagnostic(
            format!("{path}.legacy_dependency_indices"),
            "component rows resolve to different cache paths",
            rows[0].0,
            rows[0].1,
            "split rows that resolve to different binary artifacts into separate components",
        );
        item.candidates = cache_paths
            .iter()
            .map(|value| value.to_string_lossy().into_owned())
            .collect();
        diagnostics.push(item);
    }
    if requested.len() != 1 || resolved.len() != 1 || cache_paths.len() != 1 {
        return;
    }

    let requested = *requested.first().expect("one requested coordinate");
    let resolved = *resolved.first().expect("one resolved coordinate");
    let cache_path = *cache_paths.first().expect("one cache path");
    if component.acquisition.is_none() {
        validate_acquisition(requested, path, &rows, diagnostics);
    }

    let matches: Vec<_> = if let Some(index) = component.legacy_artifact_index {
        artifacts.get(index).into_iter().collect()
    } else {
        artifacts
            .iter()
            .filter(|artifact| {
                artifact.coordinate.as_deref() == Some(resolved)
                    || &artifact.cache_path == cache_path
            })
            .collect()
    };
    match matches.as_slice() {
        [] => diagnostics.push(row_diagnostic(
            format!("{path}.derived_checks.artifact_id"),
            "no root artifact matches the component's resolved coordinate or cache path",
            rows[0].0,
            rows[0].1,
            "refresh the v2 lockfile artifacts before migrating",
        )),
        [artifact] => validate_artifact_evidence(
            artifact,
            path,
            requested,
            repository_names,
            &rows,
            diagnostics,
        ),
        _ => diagnostics.push(row_diagnostic(
            format!("{path}.derived_checks.artifact_id"),
            format!("{} root artifacts match this component", matches.len()),
            rows[0].0,
            rows[0].1,
            "remove duplicate artifact evidence or split the component before migrating",
        )),
    }
}

fn validate_component_without_rows(
    component: &ComponentMigrationHintV2,
    path: &str,
    diagnostics: &mut Vec<MigrationDiagnostic>,
) {
    if component.acquisition.is_none() {
        diagnostics.push(missing(
            format!("{path}.acquisition"),
            "explicit acquisition is required when no legacy dependency rows exist",
            &["toolchain", "maven", "curse-forge", "http"],
        ));
    }
    if component.legacy_artifact_index.is_none() {
        diagnostics.push(diagnostic(
            format!("{path}.legacy_artifact_index"),
            "explicit artifact evidence is required when no legacy dependency rows exist",
            "set this to the matching index in the v2 artifacts array",
        ));
    }
}

fn validate_explicit_artifact_index(
    component: &ComponentMigrationHintV2,
    path: &str,
    artifacts: &[ArtifactLockEntryV2],
    diagnostics: &mut Vec<MigrationDiagnostic>,
) {
    if let Some(index) = component.legacy_artifact_index
        && artifacts.get(index).is_none()
    {
        diagnostics.push(diagnostic(
            format!("{path}.legacy_artifact_index"),
            format!("legacy artifact index {index} is out of range"),
            format!("use an index below {}", artifacts.len()),
        ));
    }
}

fn validate_acquisition(
    requested: &str,
    path: &str,
    rows: &[(usize, &DependencyLockEntry)],
    diagnostics: &mut Vec<MigrationDiagnostic>,
) {
    if !requested.starts_with("curse.maven:") {
        return;
    }
    if parse_curse_coordinate(requested).is_none() {
        diagnostics.push(row_diagnostic(
            format!("{path}.declaration.acquisition"),
            "CurseMaven coordinate does not contain an unambiguous slug, project ID, and file ID",
            rows[0].0,
            rows[0].1,
            "correct the coordinate to curse.maven:<slug>-<project-id>:<file-id>",
        ));
    }
}

fn validate_artifact_evidence(
    artifact: &ArtifactLockEntryV2,
    path: &str,
    requested: &str,
    repository_names: &BTreeSet<&str>,
    rows: &[(usize, &DependencyLockEntry)],
    diagnostics: &mut Vec<MigrationDiagnostic>,
) {
    let repository_path = format!("{path}.declaration.acquisition.repository_id");
    match artifact.repository.as_deref() {
        None => diagnostics.push(row_diagnostic(
            repository_path,
            "matching artifact has no repository",
            rows[0].0,
            rows[0].1,
            "refresh the v2 artifact with repository provenance before migrating",
        )),
        Some(repository) if !repository_names.contains(repository) => {
            diagnostics.push(row_diagnostic(
                repository_path,
                format!("matching artifact references unknown repository '{repository}'"),
                rows[0].0,
                rows[0].1,
                "add the repository to the v2 repositories list or correct artifact provenance",
            ));
        }
        Some(repository) if requested.starts_with("curse.maven:") && repository != "CurseMaven" => {
            diagnostics.push(row_diagnostic(
                repository_path,
                format!("CurseMaven component resolves through repository '{repository}'"),
                rows[0].0,
                rows[0].1,
                "correct the artifact repository provenance to CurseMaven",
            ));
        }
        Some(_) => {}
    }
}

fn evidence_diagnostic<T: ToString>(
    path: String,
    message: &str,
    rows: &[(usize, &DependencyLockEntry)],
    candidates: impl IntoIterator<Item = T>,
    remediation: &str,
) -> MigrationDiagnostic {
    let mut result = row_diagnostic(path, message, rows[0].0, rows[0].1, remediation);
    result.candidates = candidates
        .into_iter()
        .map(|value| value.to_string())
        .collect();
    result
}

fn validate_platform_reference(
    id: Option<&str>,
    expected_kind: DependencyKindV3,
    path: &str,
    hints: &MigrationHintsV2,
    diagnostics: &mut Vec<MigrationDiagnostic>,
) {
    let Some(id) = id.filter(|id| !id.trim().is_empty()) else {
        return;
    };
    let Some(dependency) = hints
        .dependencies
        .iter()
        .find(|dependency| dependency.id == id)
    else {
        diagnostics.push(diagnostic(
            path,
            format!("platform dependency ID '{id}' does not reference a migration dependency"),
            "use an ID present in migration_hints.dependencies",
        ));
        return;
    };
    if dependency
        .kind
        .as_ref()
        .is_some_and(|kind| kind != &expected_kind)
    {
        diagnostics.push(diagnostic(
            path,
            format!("platform dependency '{id}' has kind {:?}", dependency.kind),
            format!("set its kind to {expected_kind:?} or reference the correct dependency"),
        ));
    }
}

fn scope_candidates(
    component: &ComponentMigrationHintV2,
    legacy: &[DependencyLockEntry],
) -> Vec<&'static str> {
    let mut candidates = BTreeSet::new();
    for row in component
        .legacy_dependency_indices
        .iter()
        .filter_map(|index| legacy.get(*index))
    {
        let configuration = row.configuration.to_ascii_lowercase();
        let candidate = if configuration.contains("gametest") && configuration.contains("runtime") {
            "gametest-runtime"
        } else if configuration.contains("gametest") {
            "gametest-compile"
        } else if configuration.contains("test") && configuration.contains("runtime") {
            "test-runtime"
        } else if configuration.contains("test") {
            "test-compile"
        } else if configuration.contains("runtime") {
            "runtime"
        } else if configuration.contains("annotationprocessor") {
            "annotation-processor"
        } else if configuration.contains("compile") || configuration.contains("implementation") {
            "compile"
        } else {
            continue;
        };
        candidates.insert(candidate);
    }
    candidates.into_iter().collect()
}

fn require_non_empty(
    value: Option<&str>,
    path: &str,
    message: &str,
    remediation: &str,
    diagnostics: &mut Vec<MigrationDiagnostic>,
) {
    if value.is_none_or(|value| value.trim().is_empty()) {
        diagnostics.push(diagnostic(path, message, remediation));
    }
}

fn missing(path: String, message: &str, candidates: &[&str]) -> MigrationDiagnostic {
    let mut result = diagnostic(
        path,
        message,
        "populate this field and rerun dependency migrate --check",
    );
    result.candidates = candidates.iter().map(|value| (*value).to_owned()).collect();
    result
}

fn row_diagnostic(
    path: String,
    message: impl Into<String>,
    index: usize,
    row: &DependencyLockEntry,
    remediation: impl Into<String>,
) -> MigrationDiagnostic {
    MigrationDiagnostic {
        path,
        message: message.into(),
        legacy_dependency_index: Some(index),
        configuration: Some(row.configuration.clone()),
        coordinate: Some(row.resolved_notation.clone()),
        candidates: Vec::new(),
        remediation: remediation.into(),
    }
}

fn diagnostic(
    path: impl Into<String>,
    message: impl Into<String>,
    remediation: impl Into<String>,
) -> MigrationDiagnostic {
    MigrationDiagnostic {
        path: path.into(),
        message: message.into(),
        legacy_dependency_index: None,
        configuration: None,
        coordinate: None,
        candidates: Vec::new(),
        remediation: remediation.into(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::jar_build::Repository;
    use crate::jar_build::WeakArtifactValidation;
    use crate::toolchain_lockfile_schema::ENGINE_SCHEMA_VERSION;
    use crate::toolchain_lockfile_schema::version::v2::DependencyMigrationHintV2;
    use crate::toolchain_lockfile_schema::version::v3::ArtifactTreatmentV3;
    use crate::toolchain_lockfile_schema::version::v3::DataRunPolicyV3;
    use crate::toolchain_lockfile_schema::version::v3::DependencyRoleV3;
    use std::path::PathBuf;

    #[test]
    fn absent_hints_produce_one_actionable_root_diagnostic() {
        let lockfile = lockfile(Vec::new(), None);
        let diagnostics = lockfile.migration_diagnostics();

        assert_eq!(diagnostics.len(), 1);
        assert_eq!(diagnostics[0].path, "migration_hints");
        assert!(diagnostics[0].remediation.contains("migrate --check"));
    }

    #[test]
    fn incomplete_hints_report_all_missing_fields_and_row_context() {
        let lockfile = lockfile(
            vec![legacy_row("gametestRuntimeOnly", "example:mod:1.0")],
            Some(MigrationHintsV2 {
                minecraft_dependency_id: None,
                loader_dependency_id: None,
                dependencies: vec![DependencyMigrationHintV2 {
                    id: "example".to_owned(),
                    kind: None,
                    role: None,
                    display_name: None,
                    project_url: None,
                    notes: None,
                    components: vec![ComponentMigrationHintV2 {
                        id: "main".to_owned(),
                        legacy_dependency_indices: vec![0],
                        acquisition: None,
                        legacy_artifact_index: None,
                        scopes: None,
                        artifact_treatment: None,
                        data_run_policy: None,
                    }],
                }],
            }),
        );
        let diagnostics = lockfile.migration_diagnostics();
        let paths: BTreeSet<_> = diagnostics.iter().map(|item| item.path.as_str()).collect();
        assert_eq!(diagnostics.len(), 7);
        assert!(paths.contains("migration_hints.minecraft_dependency_id"));
        assert!(paths.contains("migration_hints.loader_dependency_id"));
        assert!(paths.contains("migration_hints.dependencies[0].kind"));
        assert!(paths.contains("migration_hints.dependencies[0].role"));
        assert!(paths.contains("migration_hints.dependencies[0].components[0].scopes"));
        let scopes = diagnostics
            .iter()
            .find(|item| item.path.ends_with(".scopes"))
            .expect("scope diagnostic");
        assert_eq!(scopes.candidates, vec!["gametest-runtime"]);
    }

    #[test]
    fn complete_platform_hints_have_no_diagnostics() {
        let dependencies = vec![
            complete_dependency("minecraft", DependencyKindV3::Minecraft, 0),
            complete_dependency("forge", DependencyKindV3::Loader, 1),
        ];
        let mut lockfile = lockfile(
            vec![
                legacy_row("implementation", "net.minecraft:minecraft:1.19.2"),
                legacy_row("implementation", "net.minecraftforge:forge:1.19.2-43.4.0"),
            ],
            Some(MigrationHintsV2 {
                minecraft_dependency_id: Some("minecraft".to_owned()),
                loader_dependency_id: Some("forge".to_owned()),
                dependencies,
            }),
        );
        lockfile.artifacts[0].weak = Some(WeakArtifactValidation {
            metadata_path: PathBuf::from("META-INF/mods.toml"),
            mod_id: "minecraft-fixture".to_owned(),
            version: "1.19.2".to_owned(),
        });

        assert_eq!(lockfile.migration_diagnostics(), Vec::new());
        let migrated = lockfile.migrate_to_v3().expect("migration should succeed");
        assert_eq!(migrated.schema_version, SCHEMA_VERSION);
        assert_eq!(migrated.platform.minecraft_dependency, "minecraft");
        assert_eq!(migrated.platform.loader_dependency, "forge");
        assert_eq!(migrated.dependencies.len(), 2);
        assert_eq!(migrated.artifacts.len(), 2);
        assert!(migrated.artifacts.iter().any(|artifact| {
            artifact
                .weak
                .as_ref()
                .is_some_and(|weak| weak.mod_id == "minecraft-fixture")
        }));
        let json = facet_json::to_string_pretty(&migrated).expect("v3 should serialize");
        let reparsed: ArtifactLockfileV3 =
            facet_json::from_str(&json).expect("serialized v3 should parse");
        reparsed.validate().expect("serialized v3 should validate");
    }

    fn complete_dependency(
        id: &str,
        kind: DependencyKindV3,
        legacy_index: usize,
    ) -> DependencyMigrationHintV2 {
        DependencyMigrationHintV2 {
            id: id.to_owned(),
            kind: Some(kind),
            role: Some(DependencyRoleV3::Platform),
            display_name: None,
            project_url: None,
            notes: None,
            components: vec![ComponentMigrationHintV2 {
                id: "main".to_owned(),
                legacy_dependency_indices: vec![legacy_index],
                acquisition: None,
                legacy_artifact_index: None,
                scopes: Some(vec![DependencyScopeV3::Compile]),
                artifact_treatment: Some(ArtifactTreatmentV3::Plain),
                data_run_policy: Some(DataRunPolicyV3::Exclude),
            }],
        }
    }

    fn lockfile(
        dependencies: Vec<DependencyLockEntry>,
        migration_hints: Option<MigrationHintsV2>,
    ) -> ArtifactLockfileV2 {
        let artifacts = dependencies
            .iter()
            .map(|dependency| {
                legacy_artifact(&dependency.resolved_notation, &dependency.cache_path)
            })
            .collect();
        ArtifactLockfileV2 {
            schema_version: ENGINE_SCHEMA_VERSION,
            minecraft_version: "1.19.2".to_owned(),
            maven_cache_dir: PathBuf::from("$sfm-cache/maven"),
            allow_local_artifact_cache: false,
            repositories: vec![Repository {
                name: "Test".to_owned(),
                url: "https://example.invalid/maven".to_owned(),
            }],
            dependencies,
            artifacts,
            migration_hints,
        }
    }

    fn legacy_row(configuration: &str, notation: &str) -> DependencyLockEntry {
        let cache_path = format!("$sfm-cache/{notation}.jar");
        facet_json::from_str(&format!(
            r#"{{
                "configuration": "{configuration}",
                "notation": "{notation}",
                "resolved_notation": "{notation}",
                "source": "Maven",
                "dynamic_version": false,
                "cache_path": "{cache_path}"
            }}"#
        ))
        .expect("legacy dependency fixture should parse")
    }

    fn legacy_artifact(coordinate: &str, cache_path: &std::path::Path) -> ArtifactLockEntryV2 {
        facet_json::from_str(&format!(
            r#"{{
                "coordinate": "{coordinate}",
                "source": "remote-maven",
                "repository": "Test",
                "url": "https://example.invalid/{coordinate}.jar",
                "cache_path": "{}",
                "original_path": null,
                "source_relative_path": null,
                "source_git": null,
                "source_build": null,
                "hash": "blake3:0000000000000000000000000000000000000000",
                "weak": null
            }}"#,
            cache_path.display()
        ))
        .expect("legacy artifact fixture should parse")
    }
}
