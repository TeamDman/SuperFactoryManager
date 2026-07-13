use crate::toolchain_lockfile_schema::version::v3::ArtifactLockfileV3;
use crate::toolchain_lockfile_schema::version::v3::DependencyComponentV3;
use crate::toolchain_lockfile_schema::version::v3::SourceProviderV3;
use std::collections::BTreeMap;

impl ArtifactLockfileV3 {
    pub(crate) fn to_canonical_json(&self) -> eyre::Result<String> {
        let mut canonical = self.clone();
        canonical.canonicalize();
        canonical.validate()?;
        let mut output = facet_json::to_string_pretty(&canonical)?;
        output.push('\n');
        Ok(output)
    }

    pub(crate) fn refresh_derived_state(&self, resolved: &Self) -> eyre::Result<Self> {
        self.validate()?;
        resolved.validate()?;
        let mut refreshed = self.clone();
        let resolved_dependencies: BTreeMap<_, _> = resolved
            .dependencies
            .iter()
            .map(|dependency| (dependency.id.as_str(), dependency))
            .collect();

        for dependency in &mut refreshed.dependencies {
            let resolved_dependency = resolved_dependencies
                .get(dependency.id.as_str())
                .ok_or_else(|| {
                    eyre::eyre!("resolved state is missing dependency '{}'", dependency.id)
                })?;
            let resolved_components: BTreeMap<_, _> = resolved_dependency
                .components
                .iter()
                .map(|component| (component.id.as_str(), component))
                .collect();
            for component in &mut dependency.components {
                let resolved_component = resolved_components
                    .get(component.id.as_str())
                    .ok_or_else(|| {
                        eyre::eyre!(
                            "resolved state is missing component '{}/{}'",
                            dependency.id,
                            component.id
                        )
                    })?;
                refresh_component_derived_state(component, resolved_component)?;
            }
            if resolved_components.len() != dependency.components.len() {
                eyre::bail!(
                    "resolved component topology differs for dependency '{}'",
                    dependency.id
                );
            }
        }
        if resolved_dependencies.len() != refreshed.dependencies.len() {
            eyre::bail!("resolved dependency topology differs from maintained declarations");
        }

        refreshed.artifacts.clone_from(&resolved.artifacts);
        refreshed.canonicalize();
        refreshed.validate()?;
        Ok(refreshed)
    }

    fn canonicalize(&mut self) {
        self.repositories
            .sort_by(|left, right| left.id.cmp(&right.id));
        self.dependencies
            .sort_by(|left, right| left.id.cmp(&right.id));
        for dependency in &mut self.dependencies {
            dependency
                .components
                .sort_by(|left, right| left.id.cmp(&right.id));
            for component in &mut dependency.components {
                component.declaration.scopes.sort();
                component.declaration.scopes.dedup();
                component
                    .source_providers
                    .sort_by(|left, right| provider_id(left).cmp(provider_id(right)));
                for provider in &mut component.source_providers {
                    canonicalize_provider(provider);
                }
            }
        }
        self.artifacts.sort_by(|left, right| left.id.cmp(&right.id));
        for artifact in &mut self.artifacts {
            artifact.purposes.sort();
            artifact.purposes.dedup();
        }
    }
}

fn refresh_component_derived_state(
    maintained: &mut DependencyComponentV3,
    resolved: &DependencyComponentV3,
) -> eyre::Result<()> {
    maintained
        .derived_checks
        .clone_from(&resolved.derived_checks);
    let resolved_providers: BTreeMap<_, _> = resolved
        .source_providers
        .iter()
        .map(|provider| (provider_id(provider), provider))
        .collect();
    for provider in &mut maintained.source_providers {
        let resolved_provider = resolved_providers
            .get(provider_id(provider))
            .ok_or_else(|| {
                eyre::eyre!(
                    "resolved state is missing source provider '{}'",
                    provider_id(provider)
                )
            })?;
        replace_provider_derived_checks(provider, resolved_provider)?;
    }
    if resolved_providers.len() != maintained.source_providers.len() {
        eyre::bail!("resolved source-provider topology differs from maintained declarations");
    }
    Ok(())
}

fn provider_id(provider: &SourceProviderV3) -> &str {
    match provider {
        SourceProviderV3::MavenSources(provider) => &provider.id,
        SourceProviderV3::Git(provider) => &provider.id,
        SourceProviderV3::Decompile(provider) => &provider.id,
        SourceProviderV3::PlatformPipeline(provider) => &provider.id,
    }
}

fn canonicalize_provider(provider: &mut SourceProviderV3) {
    let roots = match provider {
        SourceProviderV3::MavenSources(provider) => &mut provider.declaration.roots,
        SourceProviderV3::Git(provider) => &mut provider.declaration.roots,
        SourceProviderV3::Decompile(provider) => &mut provider.declaration.roots,
        SourceProviderV3::PlatformPipeline(provider) => &mut provider.declaration.roots,
    };
    roots.sort();
    roots.dedup();
}

fn replace_provider_derived_checks(
    maintained: &mut SourceProviderV3,
    resolved: &SourceProviderV3,
) -> eyre::Result<()> {
    match (maintained, resolved) {
        (SourceProviderV3::MavenSources(maintained), SourceProviderV3::MavenSources(resolved)) => {
            maintained
                .derived_checks
                .clone_from(&resolved.derived_checks);
        }
        (SourceProviderV3::Git(maintained), SourceProviderV3::Git(resolved)) => {
            maintained
                .derived_checks
                .clone_from(&resolved.derived_checks);
        }
        (SourceProviderV3::Decompile(maintained), SourceProviderV3::Decompile(resolved)) => {
            maintained
                .derived_checks
                .clone_from(&resolved.derived_checks);
        }
        (
            SourceProviderV3::PlatformPipeline(maintained),
            SourceProviderV3::PlatformPipeline(resolved),
        ) => {
            maintained
                .derived_checks
                .clone_from(&resolved.derived_checks);
        }
        _ => eyre::bail!("source provider kind changed during resolution"),
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::toolchain_lockfile_schema::version::v3::ArtifactTreatmentV3;
    use crate::toolchain_lockfile_schema::version::v3::DataRunPolicyV3;
    use crate::toolchain_lockfile_schema::version::v3::GitSourceDeclarationV3;
    use crate::toolchain_lockfile_schema::version::v3::GitSourceDerivedChecksV3;
    use crate::toolchain_lockfile_schema::version::v3::GitSourceProviderV3;
    use crate::toolchain_lockfile_schema::version::v3::MavenSourceDeclarationV3;
    use crate::toolchain_lockfile_schema::version::v3::MavenSourceDerivedChecksV3;
    use crate::toolchain_lockfile_schema::version::v3::MavenSourceProviderV3;
    use std::path::PathBuf;

    const V3_LOCKFILE: &str = include_str!("../../../../../minecraft/sfm-toolchain.lock.json");

    #[test]
    fn canonical_json_is_stable_across_input_ordering() {
        let original = lockfile();
        let mut scrambled = original.clone();
        scrambled.repositories.reverse();
        scrambled.dependencies.reverse();
        scrambled.artifacts.reverse();
        for dependency in &mut scrambled.dependencies {
            dependency.components.reverse();
            for component in &mut dependency.components {
                component.declaration.scopes.reverse();
            }
        }

        assert_eq!(
            original.to_canonical_json().expect("canonical original"),
            scrambled.to_canonical_json().expect("canonical scrambled")
        );
    }

    #[test]
    fn refresh_replaces_checks_but_preserves_declarations() {
        let mut maintained = lockfile();
        let provider = git_provider_mut(component_mut(&mut maintained, "cc-tweaked"));
        provider.declaration.requested_revision = "v1.19.2-1.101.3".to_owned();
        add_git_provider(
            &mut maintained,
            "applied-energistics-2",
            "ae2-tag",
            "ae2-old",
        );
        let mut resolved = maintained.clone();
        let cc = component_mut(&mut resolved, "cc-tweaked");
        let provider = git_provider_mut(cc);
        provider.declaration.requested_revision = "must-not-replace-declaration".to_owned();
        provider.derived_checks.commit = "new-commit".to_owned();
        provider.derived_checks.tree_cache_path = PathBuf::from("$sfm-cache/sources/git/new");

        let refreshed = maintained
            .refresh_derived_state(&resolved)
            .expect("refresh should validate");
        let refreshed_cc = component(&refreshed, "cc-tweaked");
        let provider = refreshed_cc
            .source_providers
            .iter()
            .find_map(|provider| match provider {
                SourceProviderV3::Git(provider) => Some(provider),
                _ => None,
            })
            .expect("Git provider");
        assert_eq!(provider.declaration.requested_revision, "v1.19.2-1.101.3");
        assert_eq!(provider.derived_checks.commit, "new-commit");
        let ae2 = component(&refreshed, "applied-energistics-2");
        let SourceProviderV3::Git(provider) = &ae2.source_providers[0] else {
            panic!("expected Git provider");
        };
        assert_eq!(provider.declaration.requested_revision, "ae2-tag");
        assert_eq!(provider.derived_checks.commit, "ae2-old");
    }

    #[test]
    fn refresh_rejects_missing_declared_provider() {
        let maintained = lockfile();
        let mut resolved = lockfile();
        component_mut(&mut resolved, "cc-tweaked")
            .source_providers
            .retain(|provider| !matches!(provider, SourceProviderV3::Git(_)));

        let error = maintained
            .refresh_derived_state(&resolved)
            .expect_err("missing provider must fail");

        assert!(error.to_string().contains("missing source provider"));
    }

    #[test]
    fn checked_in_fixture_covers_multicomponent_scopes_treatments_and_policies() {
        let lockfile = lockfile();
        let ae2 = lockfile
            .dependencies
            .iter()
            .find(|dependency| dependency.id == "applied-energistics-2")
            .expect("AE2 fixture");
        assert_eq!(ae2.components.len(), 2);
        assert_eq!(
            component(&lockfile, "cc-tweaked")
                .declaration
                .data_run_policy,
            DataRunPolicyV3::Exclude
        );
        assert_eq!(
            component(&lockfile, "cc-tweaked")
                .declaration
                .artifact_treatment,
            ArtifactTreatmentV3::LoaderManagedMod
        );
        let ae2_api = ae2
            .components
            .iter()
            .find(|component| component.id == "api")
            .expect("AE2 API fixture");
        assert_eq!(
            ae2_api.declaration.artifact_treatment,
            ArtifactTreatmentV3::LoaderManagedMod
        );
        let mekanism_api = lockfile
            .dependencies
            .iter()
            .find(|dependency| dependency.id == "mekanism")
            .expect("Mekanism fixture")
            .components
            .iter()
            .find(|component| component.id == "api")
            .expect("Mekanism API fixture");
        assert_eq!(
            mekanism_api.declaration.artifact_treatment,
            ArtifactTreatmentV3::Plain
        );
        let minecraft = component(&lockfile, "minecraft");
        assert_eq!(
            minecraft.declaration.data_run_policy,
            DataRunPolicyV3::Include
        );
    }

    #[test]
    fn maven_sources_provider_roundtrips_with_portable_paths() {
        let mut lockfile = lockfile();
        let hash = component(&lockfile, "cc-tweaked")
            .derived_checks
            .expected_hash;
        component_mut(&mut lockfile, "cc-tweaked")
            .source_providers
            .push(SourceProviderV3::MavenSources(MavenSourceProviderV3 {
                id: "fixture-maven-sources".to_owned(),
                declaration: MavenSourceDeclarationV3 {
                    requested_coordinate: "org.squiddev:cc-tweaked-1.19.2:1.101.3:sources"
                        .to_owned(),
                    repository_id: "squiddev".to_owned(),
                    roots: vec!["projects/common/src/main/java".to_owned()],
                },
                derived_checks: MavenSourceDerivedChecksV3 {
                    resolved_coordinate: "org.squiddev:cc-tweaked-1.19.2:1.101.3:sources"
                        .to_owned(),
                    url: "https://squiddev.cc/maven/cc-tweaked-sources.jar".to_owned(),
                    hash,
                    archive_cache_path: PathBuf::from(
                        "$sfm-cache/sources/maven/cc-tweaked-sources.jar",
                    ),
                    tree_cache_path: PathBuf::from("$sfm-cache/sources/trees/cc-tweaked"),
                },
            }));

        let json = lockfile
            .to_canonical_json()
            .expect("Maven sources should validate");
        let reparsed: ArtifactLockfileV3 = facet_json::from_str(&json).expect("round trip");
        reparsed
            .validate()
            .expect("round-tripped provider should validate");
    }

    #[test]
    fn absolute_artifact_cache_path_is_rejected() {
        let mut lockfile = lockfile();
        lockfile.artifacts[0].cache_path = PathBuf::from("C:/machine-specific/artifact.jar");

        let error = lockfile.validate().expect_err("absolute path should fail");

        assert!(error.to_string().contains("must be portable"));
    }

    fn lockfile() -> ArtifactLockfileV3 {
        facet_json::from_str(V3_LOCKFILE).expect("checked-in v3 lockfile should parse")
    }

    fn add_git_provider(
        lockfile: &mut ArtifactLockfileV3,
        dependency_id: &str,
        requested_revision: &str,
        commit: &str,
    ) {
        component_mut(lockfile, dependency_id)
            .source_providers
            .push(SourceProviderV3::Git(GitSourceProviderV3 {
                id: "git".to_owned(),
                declaration: GitSourceDeclarationV3 {
                    remote_url: format!("https://example.invalid/{dependency_id}.git"),
                    requested_revision: requested_revision.to_owned(),
                    roots: vec!["src".to_owned()],
                },
                derived_checks: GitSourceDerivedChecksV3 {
                    commit: commit.to_owned(),
                    repository_cache_path: PathBuf::from(format!(
                        "$sfm-cache/sources/git/{dependency_id}.git"
                    )),
                    tree_cache_path: PathBuf::from(format!(
                        "$sfm-cache/sources/git/{dependency_id}/{commit}"
                    )),
                },
            }));
    }

    fn component<'a>(
        lockfile: &'a ArtifactLockfileV3,
        dependency_id: &str,
    ) -> &'a DependencyComponentV3 {
        lockfile
            .dependencies
            .iter()
            .find(|dependency| dependency.id == dependency_id)
            .expect("dependency fixture")
            .components
            .iter()
            .find(|component| component.id == "main")
            .expect("main component fixture")
    }

    fn component_mut<'a>(
        lockfile: &'a mut ArtifactLockfileV3,
        dependency_id: &str,
    ) -> &'a mut DependencyComponentV3 {
        lockfile
            .dependencies
            .iter_mut()
            .find(|dependency| dependency.id == dependency_id)
            .expect("dependency fixture")
            .components
            .iter_mut()
            .find(|component| component.id == "main")
            .expect("main component fixture")
    }

    fn git_provider_mut(component: &mut DependencyComponentV3) -> &mut GitSourceProviderV3 {
        component
            .source_providers
            .iter_mut()
            .find_map(|provider| match provider {
                SourceProviderV3::Git(provider) => Some(provider),
                _ => None,
            })
            .expect("Git provider")
    }
}
