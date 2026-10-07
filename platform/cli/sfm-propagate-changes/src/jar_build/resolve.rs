//! Artifact resolution with an opt-in exact released-input boundary.
//!
//! Legacy constructors retain existing behavior. Named native planner and
//! external-tool integration remain separate gates, not implied by this API.

use super::ArtifactId;
use super::ArtifactLockEntry;
use super::ArtifactLockfile;
use super::ArtifactPlan;
use super::ArtifactProvenance;
use super::ArtifactPurpose;
use super::ArtifactSource;
use super::DependencyPlan;
use super::DependencySource;
use super::MavenCoordinate;
use super::Repository;
use super::SourceBuildSystem;
use super::SourceGitProvenance;
use super::acquire_artifact_path_lock_cancellable;
use super::acquire_artifact_path_read_lock_cancellable;
use super::artifact_provenance;
use super::compare_version_text;
use super::copy_file_to_path_checked_locked;
use super::download_text_optional;
use super::download_to_path_overwrite_locked;
use super::materialize_source_build;
use super::parse_maven_pom_runtime_dependencies;
use super::parse_maven_versions;
use super::prepare_existing_artifact_for_reuse;
use super::read_artifact_provenance;
use super::remote_exists;
use super::source_build_checkout_key;
use super::source_git_provenance;
use super::write_artifact_provenance;
use crate::cancellation::CancellationToken;
use crate::jar_build::hash::ContentHash;
use crate::jar_build::hash::ContentHashAlgorithm;
use crate::source_cache::SourceCacheLayout;
use crate::source_projection::prepared_dependency_inputs::FrozenRuntimeRoot;
use crate::source_projection::prepared_dependency_inputs::PreparedArtifactRequest;
use crate::source_projection::prepared_dependency_inputs::PreparedDependencyInputs;
use crate::source_projection::released_native_inputs::ReleasedPreparedDependency;
use eyre::Context;
use rayon::prelude::*;
use reqwest::blocking::Client;
use std::fs;
use std::path::Path;
use std::path::PathBuf;
use std::sync::Arc;
use tracing::instrument;

#[cfg(test)]
mod immutable_catalog_tests {
    use super::*;

    #[test]
    fn unknown_catalog_artifacts_are_refused_before_cache_or_network() -> eyre::Result<()> {
        let temp = tempfile::tempdir()?;
        let cache = temp.path().join("must-not-create");
        let lock = ArtifactLockfile {
            schema_version: 1,
            minecraft_version: "1.19.2".to_owned(),
            maven_cache_dir: cache.clone(),
            allow_local_artifact_cache: false,
            repositories: Vec::new(),
            dependencies: Vec::new(),
            artifacts: Vec::new(),
        };
        let resolver = Resolver::new(
            cache.clone(),
            Vec::new(),
            false,
            false,
            Vec::new(),
            Some(lock),
            None,
            CancellationToken::new(),
        )?
        .require_immutable_catalog()?;
        let coordinate = MavenCoordinate::parse("invalid.example:absent:1")?;
        let error = resolver
            .resolve_artifact(
                ArtifactId::from("proof"),
                &coordinate,
                ArtifactPurpose::from("refusal proof"),
            )
            .unwrap_err()
            .to_string();
        assert!(
            error.contains("absent from the captured profile lock"),
            "{error}"
        );
        assert!(!cache.exists());
        assert!(
            Resolver::new(
                cache,
                Vec::new(),
                false,
                false,
                Vec::new(),
                None,
                None,
                CancellationToken::new()
            )?
            .require_immutable_catalog()
            .is_err()
        );
        Ok(())
    }
}

#[derive(Clone, Debug)]
pub(super) struct Resolver {
    pub(super) client: Client,
    cache_dir: PathBuf,
    repositories: Arc<[Repository]>,
    refresh: bool,
    allow_local_artifact_cache: bool,
    artifact_sources: Arc<[PathBuf]>,
    lockfile: Option<Arc<ArtifactLockfile>>,
    materialization_lockfile: Option<Arc<ArtifactLockfile>>,
    prepared_inputs: Option<Arc<PreparedDependencyInputs>>,
    immutable_catalog: bool,
    pub(super) cancellation_token: CancellationToken,
}

impl Resolver {
    #[expect(
        clippy::too_many_arguments,
        reason = "Resolver construction mirrors the normalized planner state it owns."
    )]
    #[tracing::instrument(
        name = "resolver_new",
        level = "debug",
        skip_all,
        fields(
            cache_dir = %cache_dir.display(),
            repository_count = repositories.len(),
            refresh,
            allow_local_artifact_cache,
            artifact_source_count = artifact_sources.len(),
            has_lockfile = lockfile.is_some(),
        )
    )]
    pub(super) fn new(
        cache_dir: PathBuf,
        repositories: Vec<Repository>,
        refresh: bool,
        allow_local_artifact_cache: bool,
        artifact_sources: Vec<PathBuf>,
        lockfile: Option<ArtifactLockfile>,
        materialization_lockfile: Option<ArtifactLockfile>,
        cancellation_token: CancellationToken,
    ) -> eyre::Result<Self> {
        cancellation_token.bail_if_cancelled()?;
        let client = Client::builder()
            .user_agent("sfm-propagate-changes/no-gradle-toolchain")
            .build()
            .wrap_err("Failed to create HTTP client")?;

        Ok(Self {
            client,
            cache_dir,
            repositories: Arc::from(repositories),
            refresh,
            allow_local_artifact_cache,
            artifact_sources: Arc::from(artifact_sources),
            lockfile: lockfile.map(Arc::new),
            materialization_lockfile: materialization_lockfile.map(Arc::new),
            prepared_inputs: None,
            immutable_catalog: false,
            cancellation_token,
        })
    }

    pub(super) fn require_immutable_catalog(mut self) -> eyre::Result<Self> {
        eyre::ensure!(
            self.lockfile.is_some()
                && !self.refresh
                && !self.allow_local_artifact_cache
                && self.artifact_sources.is_empty(),
            "catalog resolver requires immutable locked inputs"
        );
        self.immutable_catalog = true;
        Ok(self)
    }

    fn validate_catalog_coordinate(&self, coordinate: &MavenCoordinate) -> eyre::Result<()> {
        if self.immutable_catalog {
            let notation = coordinate.to_string();
            eyre::ensure!(
                self.lockfile.as_ref().is_some_and(|lock| lock
                    .artifacts
                    .iter()
                    .any(|artifact| artifact.coordinate.as_deref() == Some(notation.as_str()))),
                "catalog artifact is absent from the captured profile lock: {notation}"
            );
        }
        Ok(())
    }

    /// Strict released-input constructor. Never use a profile inferred from a branch.
    /// Caller owns exact named-target rechecks and trusted cache-root resolution.
    /// Reject refresh before client construction or any cache/acquisition work.
    pub(super) fn new_prepared(
        cache_dir: PathBuf,
        prepared_inputs: Arc<PreparedDependencyInputs>,
        refresh: bool,
        cancellation_token: CancellationToken,
    ) -> eyre::Result<Self> {
        prepared_inputs
            .verify_source_lock_bytes(prepared_inputs.raw_source_lock_bytes(), refresh)?;
        cancellation_token.bail_if_cancelled()?;
        let lock = prepared_inputs.original_lock().clone().into_latest();
        let repositories = lock.repositories.clone();
        let mut resolver = Self::new(
            cache_dir,
            repositories,
            false,
            false,
            Vec::new(),
            Some(lock),
            None,
            cancellation_token,
        )?;
        resolver.materialization_lockfile = resolver.lockfile.clone();
        resolver.prepared_inputs = Some(prepared_inputs);
        Ok(resolver)
    }

    #[cfg_attr(
        not(test),
        expect(
            dead_code,
            reason = "Shared prepared-handle plumbing enters with the named native planner."
        )
    )]
    pub(super) fn prepared_dependency_inputs(&self) -> Option<&Arc<PreparedDependencyInputs>> {
        self.prepared_inputs.as_ref()
    }

    fn prepared_coordinate(
        &self,
        coordinate: &MavenCoordinate,
    ) -> eyre::Result<Option<PreparedArtifactRequest>> {
        self.prepared_inputs
            .as_ref()
            .map(|inputs| inputs.coordinate(&coordinate.to_string(), self.refresh))
            .transpose()
    }

    fn prepared_dependency(
        &self,
        configuration: &str,
        coordinate: &MavenCoordinate,
    ) -> eyre::Result<Option<&ReleasedPreparedDependency>> {
        let Some(inputs) = &self.prepared_inputs else {
            return Ok(None);
        };
        let requested = coordinate.to_string();
        inputs.coordinate(&requested, self.refresh)?;
        let mut rows = inputs.dependencies().iter().filter(|row| {
            row.configuration == configuration
                && (row.requested_coordinate == requested || row.resolved_coordinate == requested)
        });
        let row = rows.next().ok_or_else(|| {
            eyre::eyre!(
                "dependency role is outside the frozen prepared rows: {configuration} {requested}"
            )
        })?;
        eyre::ensure!(
            rows.next().is_none(),
            "ambiguous frozen dependency role: {configuration} {requested}"
        );
        Ok(Some(row))
    }

    // Shared by the actual dispatcher and zero-effect callback tests. Validate
    // the complete batch before Rayon can acquire even a permitted first pin.
    fn with_prepared_artifact_batch<T>(
        &self,
        values: &[(ArtifactId, MavenCoordinate, ArtifactPurpose)],
        execute: impl FnOnce() -> eyre::Result<T>,
    ) -> eyre::Result<T> {
        if self.prepared_inputs.is_some() {
            for (_, coordinate, _) in values {
                self.prepared_coordinate(coordinate)?;
            }
        }
        execute()
    }

    fn with_prepared_dependency_batch<T>(
        &self,
        values: &[(String, MavenCoordinate)],
        execute: impl FnOnce() -> eyre::Result<T>,
    ) -> eyre::Result<T> {
        if self.prepared_inputs.is_some() {
            for (configuration, coordinate) in values {
                self.prepared_dependency(configuration, coordinate)?;
            }
        }
        execute()
    }

    fn with_prepared_url_guard<T>(
        &self,
        coordinate: &MavenCoordinate,
        url: &str,
        execute: impl FnOnce() -> eyre::Result<T>,
    ) -> eyre::Result<T> {
        if let Some(inputs) = &self.prepared_inputs {
            let coordinate_request = inputs.coordinate(&coordinate.to_string(), self.refresh)?;
            let url_request = inputs.exact_url(url, self.refresh)?;
            eyre::ensure!(
                coordinate_request.original_artifact_index()
                    == url_request.original_artifact_index(),
                "transport URL belongs to another frozen coordinate"
            );
        }
        execute()
    }

    fn refuse_prepared_repository_fallback(
        &self,
        coordinate: &MavenCoordinate,
    ) -> eyre::Result<()> {
        eyre::ensure!(
            self.prepared_inputs.is_none(),
            "strict released request {coordinate} could not use its exact frozen source; repository discovery/fallback is unavailable"
        );
        Ok(())
    }

    /// The planner must pass its exact selected root sequence, not a per-root
    /// POM guess. The original rows and order remain available for plan assembly.
    pub(super) fn frozen_runtime_rows_for(
        &self,
        roots: &[FrozenRuntimeRoot],
    ) -> eyre::Result<Option<Vec<&ReleasedPreparedDependency>>> {
        self.prepared_inputs
            .as_ref()
            .map(|inputs| inputs.frozen_runtime_rows_for(roots, self.refresh))
            .transpose()
    }

    /// Bind the planner's selected roots before any transitive acquisition.
    pub(super) fn frozen_runtime_rows_for_selected_roots(
        &self,
        selected: &[(String, String)],
    ) -> eyre::Result<Option<Vec<&ReleasedPreparedDependency>>> {
        let Some(inputs) = &self.prepared_inputs else {
            return Ok(None);
        };
        let roots = selected
            .iter()
            .map(|(configuration, coordinate)| {
                inputs
                    .runtime_roots()
                    .iter()
                    .find(|root| {
                        root.configuration == *configuration
                            && root.resolved_coordinate == *coordinate
                    })
                    .cloned()
                    .ok_or_else(|| eyre::eyre!("selected runtime root is outside the frozen set: {configuration} {coordinate}"))
            })
            .collect::<eyre::Result<Vec<_>>>()?;
        self.frozen_runtime_rows_for(&roots)
    }

    #[instrument(skip_all)]
    pub(super) fn resolve_artifacts(
        &self,
        values: impl IntoIterator<Item = (ArtifactId, MavenCoordinate, ArtifactPurpose)>,
    ) -> eyre::Result<Vec<ArtifactPlan>> {
        let values = values.into_iter().collect::<Vec<_>>();
        self.with_prepared_artifact_batch(&values, || {
            let _span = tracing::debug_span!(
                "resolve_artifacts_parallel",
                artifact_count = values.len(),
                workers = rayon::current_num_threads()
            )
            .entered();
            values
                .par_iter()
                .map(|(id, coordinate, required_for)| {
                    self.cancellation_token.bail_if_cancelled()?;
                    self.resolve_artifact(id.clone(), coordinate, required_for.clone())
                        .wrap_err_with(|| format!("Failed to resolve core coordinate {coordinate}"))
                })
                .collect::<Vec<eyre::Result<_>>>()
                .into_iter()
                .collect::<eyre::Result<Vec<_>>>()
                .wrap_err("Failed to resolve core artifact")
        })
    }

    #[expect(
        clippy::too_many_lines,
        clippy::needless_pass_by_value,
        reason = "Artifact resolution is a linear fallback chain and owns ids/purposes at the API boundary."
    )]
    pub(super) fn resolve_artifact(
        &self,
        id: ArtifactId,
        coordinate: &MavenCoordinate,
        required_for: ArtifactPurpose,
    ) -> eyre::Result<ArtifactPlan> {
        self.cancellation_token.bail_if_cancelled()?;
        self.prepared_coordinate(coordinate)?;
        let _span = tracing::debug_span!(
            "resolve_artifact",
            id = %id,
            coordinate = %coordinate,
            required_for = %required_for,
        )
        .entered();
        let coordinate = {
            let _span = tracing::debug_span!(
                "resolve_artifact_coordinate",
                dynamic_version = coordinate.version.ends_with('+'),
                has_lockfile = self.lockfile.is_some(),
            )
            .entered();
            self.resolve_dynamic_coordinate(coordinate)?
        };
        self.cancellation_token.bail_if_cancelled()?;
        let cache_path = self.cache_path_for(&coordinate);
        let expected_hash = {
            let _span = tracing::debug_span!(
                "resolve_artifact_lock_lookup",
                has_lockfile = self.lockfile.is_some()
            )
            .entered();
            self.locked_artifact_hash(&coordinate).copied()
        };

        if let Some(artifact) = {
            let _span = tracing::debug_span!(
                "resolve_artifact_valid_cache",
                refresh = self.refresh,
                cache_exists = cache_path.is_file(),
                has_expected_hash = expected_hash.is_some(),
            )
            .entered();
            self.cached_artifact_if_valid(
                &id,
                &coordinate,
                &cache_path,
                &required_for,
                expected_hash.as_ref(),
            )?
        } {
            return Ok(artifact);
        }
        self.cancellation_token.bail_if_cancelled()?;

        // A verified existing source-built artifact is reusable, but repairing
        // a miss would cross an external recipe boundary that is not sealed.
        // Refuse before creating an artifact lock or replacing cache bytes.
        if let Some(inputs) = &self.prepared_inputs {
            let request = inputs.coordinate(&coordinate.to_string(), self.refresh)?;
            let pin = inputs.artifact_pin(&request)?;
            verify_strict_cache_miss_source(&pin.source, &coordinate.to_string())?;
        }

        let _cache_lock = {
            let _span = tracing::debug_span!(
                "resolve_artifact_prepare_cache",
                refresh = self.refresh,
                cache_exists = cache_path.is_file(),
                has_expected_hash = expected_hash.is_some(),
            )
            .entered();
            let cache_lock =
                acquire_artifact_path_lock_cancellable(&cache_path, &self.cancellation_token)?;
            self.cancellation_token.bail_if_cancelled()?;
            prepare_existing_artifact_for_reuse(&cache_path, expected_hash.as_ref())?;

            if cache_path.is_file() && !self.refresh {
                let hash = ContentHash::from_path(&cache_path, ContentHashAlgorithm::Blake3)?;
                let artifact =
                    self.cached_artifact_plan(&id, &coordinate, cache_path, &required_for, hash)?;
                self.verify_locked_artifact(&coordinate, &artifact)?;
                tracing::debug!(
                    coordinate = %coordinate,
                    cache_path = %artifact.cache_path.display(),
                    hash = artifact.sha1.as_ref().map(ToString::to_string),
                    "artifact cache hit"
                );
                return Ok(artifact);
            }
            cache_lock
        };

        let mut attempted = Vec::new();
        if let Some(artifact) = {
            let _span = tracing::debug_span!(
                "resolve_artifact_explicit_source",
                artifact_source_count = self.artifact_sources.len(),
                has_expected_hash = expected_hash.is_some(),
            )
            .entered();
            self.explicit_artifact_source_fallback(
                &id,
                &coordinate,
                cache_path.clone(),
                &required_for,
                expected_hash.as_ref(),
            )?
        } {
            return Ok(artifact);
        }
        self.cancellation_token.bail_if_cancelled()?;

        if let Some(artifact) = {
            let _span = tracing::debug_span!(
                "resolve_artifact_source_build",
                has_materialization_lockfile = self.materialization_lockfile.is_some(),
                has_expected_hash = expected_hash.is_some(),
            )
            .entered();
            self.source_build_fallback(
                &id,
                &coordinate,
                cache_path.clone(),
                &required_for,
                expected_hash.as_ref(),
            )?
        } {
            return Ok(artifact);
        }
        self.cancellation_token.bail_if_cancelled()?;

        if let Some(artifact) = {
            let _span = tracing::debug_span!(
                "resolve_artifact_remote",
                repository_candidates = self.candidate_repositories(&coordinate).len(),
                has_expected_hash = expected_hash.is_some(),
            )
            .entered();
            self.remote_artifact(
                &id,
                &coordinate,
                &cache_path,
                &required_for,
                expected_hash.as_ref(),
                &mut attempted,
            )?
        } {
            return Ok(artifact);
        }
        self.cancellation_token.bail_if_cancelled()?;

        if let Some(artifact) = {
            let _span = tracing::debug_span!(
                "resolve_artifact_local_cache",
                allow_local_artifact_cache = self.allow_local_artifact_cache,
                has_expected_hash = expected_hash.is_some(),
            )
            .entered();
            self.local_artifact_fallback(
                &id,
                &coordinate,
                cache_path,
                &required_for,
                expected_hash.as_ref(),
            )?
        } {
            return Ok(artifact);
        }

        eyre::bail!(
            "Could not resolve artifact {}. Tried:\n{}\nPass --artifact-source <path> to import from an explicit local project/Maven source, or pass --allow-local-artifact-cache to allow bootstrapping from local Maven-created .m2/Gradle caches.",
            coordinate,
            attempted.join("\n")
        );
    }

    fn source_build_fallback(
        &self,
        id: &ArtifactId,
        coordinate: &MavenCoordinate,
        cache_path: PathBuf,
        required_for: &ArtifactPurpose,
        expected_hash: Option<&ContentHash>,
    ) -> eyre::Result<Option<ArtifactPlan>> {
        self.cancellation_token.bail_if_cancelled()?;
        let Some(locked) = self.materializable_locked_artifact(coordinate) else {
            return Ok(None);
        };
        eyre::ensure!(
            self.prepared_inputs.is_none(),
            "strict source-build cache miss for {coordinate}; external recipe dependency-closure validation is not integrated"
        );
        let Some(source_git) = &locked.source_git else {
            return Ok(None);
        };
        let Some(source_build) = &locked.source_build else {
            return Ok(None);
        };
        let Some(remote_url) = source_git.remote_url.as_deref() else {
            return Ok(None);
        };

        let (checkout_dir, portable_source_root, repository_dir) = self
            .source_build_checkout_paths(
                remote_url,
                &source_git.commit,
                &source_git.root,
                &source_build.build_system,
            );
        let _source_build_lock =
            acquire_artifact_path_lock_cancellable(&checkout_dir, &self.cancellation_token)?;
        materialize_source_build(
            &self.cancellation_token,
            remote_url,
            &source_git.commit,
            source_build,
            &checkout_dir,
            &repository_dir,
        )?;
        self.cancellation_token.bail_if_cancelled()?;
        let source_output = checkout_dir.join(&source_build.output_path);
        if !source_output.is_file() {
            eyre::bail!(
                "Source build for {} completed but did not produce {}",
                coordinate,
                source_output.display()
            );
        }
        copy_file_to_path_checked_locked(&source_output, &cache_path, expected_hash)?;
        let hash = ContentHash::from_path(&cache_path, ContentHashAlgorithm::Blake3)?;
        let provenance = ArtifactProvenance {
            schema_version: 1,
            source: ArtifactSource::SourceBuild,
            coordinate: Some(coordinate.to_string()),
            repository: None,
            url: None,
            original_path: None,
            source_relative_path: Some(source_build.output_path.clone()),
            source_git: Some(SourceGitProvenance {
                root: portable_source_root,
                commit: source_git.commit.clone(),
                branch: source_git.branch.clone(),
                dirty: false,
                remote_url: Some(remote_url.to_string()),
            }),
            source_build: Some(source_build.clone()),
            hash,
        };
        write_artifact_provenance(&cache_path, &provenance)?;
        let artifact = ArtifactPlan {
            id: id.clone(),
            coordinate: Some(coordinate.to_string()),
            repository: provenance.repository.clone(),
            url: provenance.url.clone(),
            cache_path,
            sha1: Some(hash),
            downloaded: false,
            required_for: required_for.clone(),
            provenance,
        };
        self.verify_locked_artifact(coordinate, &artifact)?;
        tracing::info!(
            coordinate = %coordinate,
            cache_path = %artifact.cache_path.display(),
            remote = remote_url,
            commit = source_git.commit,
            tasks = ?source_build.tasks,
            "artifact materialized from source build"
        );
        Ok(Some(artifact))
    }

    fn source_build_checkout_paths(
        &self,
        remote_url: &str,
        commit: &str,
        locked_root: &Path,
        build_system: &SourceBuildSystem,
    ) -> (PathBuf, PathBuf, PathBuf) {
        let common_cache_dir = self.cache_dir.parent().unwrap_or(&self.cache_dir);
        let compact_checkout = || {
            super::source_build_root()
                .join("sfm-source-builds")
                .join(source_build_checkout_key(remote_url, commit))
        };
        if let Ok(relative) = locked_root.strip_prefix(Path::new("$sfm-cache")) {
            let repository = SourceCacheLayout::git(remote_url, commit).repository;
            let repository_relative = repository
                .strip_prefix(Path::new("$sfm-cache"))
                .expect("source cache layout is rooted at $sfm-cache");
            return (
                if matches!(build_system, SourceBuildSystem::CargoCommand) {
                    compact_checkout()
                } else {
                    common_cache_dir.join(relative)
                },
                locked_root.to_path_buf(),
                common_cache_dir.join(repository_relative),
            );
        }

        let checkout_key = source_build_checkout_key(remote_url, commit);
        let portable_source_root = PathBuf::from("$sfm-cache")
            .join("source-builds-gix")
            .join(&checkout_key);
        let repository = SourceCacheLayout::git(remote_url, commit).repository;
        let repository_relative = repository
            .strip_prefix(Path::new("$sfm-cache"))
            .expect("source cache layout is rooted at $sfm-cache");
        (
            if matches!(build_system, SourceBuildSystem::CargoCommand) {
                compact_checkout()
            } else {
                common_cache_dir
                    .join("source-builds-gix")
                    .join(checkout_key)
            },
            portable_source_root,
            common_cache_dir.join(repository_relative),
        )
    }

    fn materializable_locked_artifact(
        &self,
        coordinate: &MavenCoordinate,
    ) -> Option<&ArtifactLockEntry> {
        let coordinate_text = coordinate.to_string();
        self.materialization_lockfile
            .as_ref()?
            .artifacts
            .iter()
            .find(|artifact| {
                artifact.coordinate.as_deref() == Some(coordinate_text.as_str())
                    && artifact.source.can_be_materialized_from_source()
                    && artifact
                        .source_git
                        .as_ref()
                        .is_some_and(|source_git| source_git.remote_url.is_some())
                    && artifact.source_build.is_some()
            })
    }

    fn remote_artifact(
        &self,
        id: &ArtifactId,
        coordinate: &MavenCoordinate,
        cache_path: &Path,
        required_for: &ArtifactPurpose,
        expected_hash: Option<&ContentHash>,
        attempted: &mut Vec<String>,
    ) -> eyre::Result<Option<ArtifactPlan>> {
        if let Some(artifact) = self.download_locked_remote_artifact(
            id,
            coordinate,
            cache_path,
            required_for,
            expected_hash,
            attempted,
        )? {
            return Ok(Some(artifact));
        }
        self.refuse_prepared_repository_fallback(coordinate)?;

        for repo in self.candidate_repositories(coordinate) {
            let _repo_span = tracing::debug_span!(
                "resolve_artifact_remote_candidate",
                repository = repo.name.as_str(),
                requires_existence_check = coordinate.group != "curse.maven",
            )
            .entered();
            self.cancellation_token.bail_if_cancelled()?;
            let url = Self::artifact_url(repo, coordinate);
            attempted.push(url.clone());
            tracing::debug!(
                coordinate = %coordinate,
                repository = repo.name.as_str(),
                url = url.as_str(),
                "checking artifact remote"
            );
            let download_result = {
                let _span = tracing::debug_span!(
                    "resolve_artifact_remote_download",
                    refresh = self.refresh,
                    has_expected_hash = expected_hash.is_some(),
                )
                .entered();
                if coordinate.group == "curse.maven" {
                    download_to_path_overwrite_locked(
                        &self.cancellation_token,
                        &self.client,
                        &url,
                        cache_path,
                        self.refresh,
                        expected_hash,
                    )
                } else {
                    if !remote_exists(&self.cancellation_token, &self.client, &url)? {
                        continue;
                    }
                    download_to_path_overwrite_locked(
                        &self.cancellation_token,
                        &self.client,
                        &url,
                        cache_path,
                        self.refresh,
                        expected_hash,
                    )
                }
            };

            if let Err(error) = download_result {
                if self.cancellation_token.is_cancelled() {
                    return Err(error);
                }
                attempted.push(format!("{url} ({error:#})"));
                continue;
            }

            let artifact = Self::remote_artifact_plan(
                id,
                coordinate,
                repo,
                url,
                cache_path.to_path_buf(),
                required_for,
            )?;
            self.verify_locked_artifact(coordinate, &artifact)?;
            tracing::info!(
                coordinate = %coordinate,
                repository = artifact.repository.as_deref(),
                cache_path = %artifact.cache_path.display(),
                hash = artifact.sha1.as_ref().map(ToString::to_string),
                "artifact downloaded"
            );
            return Ok(Some(artifact));
        }
        Ok(None)
    }

    fn download_locked_remote_artifact(
        &self,
        id: &ArtifactId,
        coordinate: &MavenCoordinate,
        cache_path: &Path,
        required_for: &ArtifactPurpose,
        expected_hash: Option<&ContentHash>,
        attempted: &mut Vec<String>,
    ) -> eyre::Result<Option<ArtifactPlan>> {
        let Some(locked) = self.locked_remote_artifact(coordinate) else {
            return Ok(None);
        };
        let url = locked
            .url
            .as_deref()
            .expect("locked remote artifact has a URL");
        self.with_prepared_url_guard(coordinate, url, || {
            attempted.push(url.to_string());
            match download_to_path_overwrite_locked(
                &self.cancellation_token,
                &self.client,
                url,
                cache_path,
                self.refresh,
                expected_hash,
            ) {
                Ok(()) => {
                    let artifact = Self::locked_remote_artifact_plan(
                        id,
                        coordinate,
                        locked,
                        cache_path.to_path_buf(),
                        required_for,
                    )?;
                    self.verify_locked_artifact(coordinate, &artifact)?;
                    tracing::info!(
                        coordinate = %coordinate,
                        repository = artifact.repository.as_deref(),
                        cache_path = %artifact.cache_path.display(),
                        hash = artifact.sha1.as_ref().map(ToString::to_string),
                        "artifact downloaded from locked URL"
                    );
                    Ok(Some(artifact))
                }
                Err(error) if self.cancellation_token.is_cancelled() => Err(error),
                Err(error) => {
                    attempted.push(format!("{url} ({error:#})"));
                    Ok(None)
                }
            }
        })
    }

    fn locked_remote_artifact(&self, coordinate: &MavenCoordinate) -> Option<&ArtifactLockEntry> {
        let coordinate_text = coordinate.to_string();
        self.lockfile
            .as_ref()
            .or(self.materialization_lockfile.as_ref())?
            .artifacts
            .iter()
            .find(|artifact| {
                artifact.coordinate.as_deref() == Some(coordinate_text.as_str())
                    && matches!(
                        artifact.source,
                        ArtifactSource::RemoteMaven | ArtifactSource::RemoteHttp
                    )
                    && artifact.url.is_some()
                    && artifact.weak.is_none()
            })
    }

    fn explicit_artifact_source_fallback(
        &self,
        id: &ArtifactId,
        coordinate: &MavenCoordinate,
        cache_path: PathBuf,
        required_for: &ArtifactPurpose,
        expected_hash: Option<&ContentHash>,
    ) -> eyre::Result<Option<ArtifactPlan>> {
        let Some(local_artifact) =
            find_explicit_source_artifact(coordinate, self.artifact_sources.as_ref())
        else {
            return Ok(None);
        };
        let artifact = Self::local_artifact_plan(
            id,
            coordinate,
            local_artifact,
            cache_path,
            required_for,
            expected_hash,
        )?;
        self.verify_locked_artifact(coordinate, &artifact)?;
        tracing::info!(
            coordinate = %coordinate,
            cache_path = %artifact.cache_path.display(),
            source = ?artifact.provenance.source,
            original_path = artifact.provenance.original_path.as_ref().map(|path| path.display().to_string()),
            hash = artifact.sha1.as_ref().map(ToString::to_string),
            "artifact copied from explicit artifact source"
        );
        Ok(Some(artifact))
    }

    fn local_artifact_fallback(
        &self,
        id: &ArtifactId,
        coordinate: &MavenCoordinate,
        cache_path: PathBuf,
        required_for: &ArtifactPurpose,
        expected_hash: Option<&ContentHash>,
    ) -> eyre::Result<Option<ArtifactPlan>> {
        if !self.allow_local_artifact_cache {
            return Ok(None);
        }
        let Some(local_artifact) = find_local_cached_artifact(coordinate) else {
            return Ok(None);
        };
        let artifact = Self::local_artifact_plan(
            id,
            coordinate,
            local_artifact,
            cache_path,
            required_for,
            expected_hash,
        )?;
        self.verify_locked_artifact(coordinate, &artifact)?;
        tracing::info!(
            coordinate = %coordinate,
            cache_path = %artifact.cache_path.display(),
            source = ?artifact.provenance.source,
            hash = artifact.sha1.as_ref().map(ToString::to_string),
            "artifact copied from local cache fallback"
        );
        Ok(Some(artifact))
    }

    fn cached_artifact_if_valid(
        &self,
        id: &ArtifactId,
        coordinate: &MavenCoordinate,
        cache_path: &Path,
        required_for: &ArtifactPurpose,
        expected_hash: Option<&ContentHash>,
    ) -> eyre::Result<Option<ArtifactPlan>> {
        if self.refresh || !cache_path.is_file() {
            return Ok(None);
        }

        let _cache_read_lock =
            acquire_artifact_path_read_lock_cancellable(cache_path, &self.cancellation_token)?;
        let expected_actual_hash = match expected_hash {
            Some(expected_hash) => {
                let actual_hash = ContentHash::from_path(cache_path, expected_hash.algorithm)?;
                if actual_hash != *expected_hash {
                    return Ok(None);
                }
                Some(actual_hash)
            }
            None => None,
        };

        let hash = match expected_actual_hash {
            Some(actual_hash) if actual_hash.algorithm == ContentHashAlgorithm::Blake3 => {
                actual_hash
            }
            _ => ContentHash::from_path(cache_path, ContentHashAlgorithm::Blake3)?,
        };
        let artifact = self.cached_artifact_plan(
            id,
            coordinate,
            cache_path.to_path_buf(),
            required_for,
            hash,
        )?;
        self.verify_locked_artifact_with_actual_hash(coordinate, &artifact, expected_actual_hash)?;
        tracing::debug!(
            coordinate = %coordinate,
            cache_path = %artifact.cache_path.display(),
            hash = artifact.sha1.as_ref().map(ToString::to_string),
            "artifact cache hit"
        );
        Ok(Some(artifact))
    }

    fn locked_artifact_hash(&self, coordinate: &MavenCoordinate) -> Option<&ContentHash> {
        let coordinate_text = coordinate.to_string();
        let locked = self
            .lockfile
            .as_ref()?
            .artifacts
            .iter()
            .find(|entry| entry.coordinate.as_deref() == Some(coordinate_text.as_str()))?;
        (locked.weak.is_none() || self.prepared_inputs.is_some()).then_some(&locked.hash)
    }

    fn verify_locked_artifact(
        &self,
        coordinate: &MavenCoordinate,
        artifact: &ArtifactPlan,
    ) -> eyre::Result<()> {
        self.verify_locked_artifact_with_actual_hash(coordinate, artifact, None)
    }

    fn verify_locked_artifact_with_actual_hash(
        &self,
        coordinate: &MavenCoordinate,
        artifact: &ArtifactPlan,
        actual_hash: Option<ContentHash>,
    ) -> eyre::Result<()> {
        let Some(lockfile) = &self.lockfile else {
            return Ok(());
        };
        let coordinate_text = coordinate.to_string();
        let Some(locked) = lockfile
            .artifacts
            .iter()
            .find(|entry| entry.coordinate.as_deref() == Some(coordinate_text.as_str()))
        else {
            eyre::bail!(
                "Artifact {} is not present in {}. Run jar build --branch {} --refresh to update the lockfile intentionally.",
                coordinate_text,
                "sfm-toolchain.lock.json",
                lockfile.minecraft_version
            );
        };
        let actual_hash = match actual_hash {
            Some(actual_hash) if actual_hash.algorithm == locked.hash.algorithm => actual_hash,
            _ => ContentHash::from_path(&artifact.cache_path, locked.hash.algorithm)?,
        };
        if self.prepared_inputs.is_some() {
            return verify_strict_original_hash(locked.hash, actual_hash, &coordinate_text);
        }
        super::validate_locked_artifact_content(&artifact.cache_path, locked, actual_hash)
            .wrap_err_with(|| format!("Failed to validate locked artifact {coordinate_text}"))?;
        Ok(())
    }

    #[instrument(level = "debug", skip_all)]
    pub(super) fn cached_artifact_plan(
        &self,
        id: &ArtifactId,
        coordinate: &MavenCoordinate,
        cache_path: PathBuf,
        required_for: &ArtifactPurpose,
        hash: ContentHash,
    ) -> eyre::Result<ArtifactPlan> {
        if let Some(inputs) = &self.prepared_inputs {
            let request = inputs.coordinate(&coordinate.to_string(), self.refresh)?;
            let pin = inputs.artifact_pin(&request)?;
            let actual = ContentHash::from_path(&cache_path, pin.hash.algorithm)?;
            verify_strict_original_hash(pin.hash, actual, &coordinate.to_string())?;
            let reported_actual = if hash.algorithm == pin.hash.algorithm {
                actual
            } else {
                ContentHash::from_path(&cache_path, hash.algorithm)?
            };
            eyre::ensure!(
                hash == reported_actual,
                "strict cache request supplied a digest different from the verified file contents"
            );
        }
        let cached_provenance = read_artifact_provenance(&cache_path)?;
        let provenance =
            if let Some(locked_provenance) = self.locked_artifact_provenance(coordinate, hash) {
                if cached_provenance.as_ref() != Some(&locked_provenance) {
                    write_artifact_provenance(&cache_path, &locked_provenance)?;
                }
                locked_provenance
            } else {
                match cached_provenance {
                    Some(provenance) if provenance.hash == hash => provenance,
                    _ => {
                        let provenance = artifact_provenance(
                            ArtifactSource::ExistingSfmCacheUnknown,
                            Some(coordinate.to_string()),
                            None,
                            None,
                            None,
                            None,
                            hash,
                        );
                        write_artifact_provenance(&cache_path, &provenance)?;
                        provenance
                    }
                }
            };
        Ok(ArtifactPlan {
            id: id.clone(),
            coordinate: Some(coordinate.to_string()),
            repository: provenance.repository.clone(),
            url: provenance.url.clone(),
            sha1: Some(hash),
            cache_path,
            downloaded: false,
            required_for: required_for.clone(),
            provenance,
        })
    }

    fn locked_artifact_provenance(
        &self,
        coordinate: &MavenCoordinate,
        hash: ContentHash,
    ) -> Option<ArtifactProvenance> {
        let coordinate_text = coordinate.to_string();
        let locked = self.lockfile.as_ref()?.artifacts.iter().find(|entry| {
            entry.coordinate.as_deref() == Some(coordinate_text.as_str()) && entry.hash == hash
        })?;
        Some(ArtifactProvenance {
            schema_version: 1,
            source: locked.source.clone(),
            coordinate: locked.coordinate.clone().or(Some(coordinate_text)),
            repository: locked.repository.clone(),
            url: locked.url.clone(),
            original_path: locked.original_path.clone(),
            source_relative_path: locked.source_relative_path.clone().or_else(|| {
                locked
                    .source_build
                    .as_ref()
                    .map(|source_build| source_build.output_path.clone())
            }),
            source_git: locked.source_git.clone(),
            source_build: locked.source_build.clone(),
            hash,
        })
    }

    fn remote_artifact_plan(
        id: &ArtifactId,
        coordinate: &MavenCoordinate,
        repo: &Repository,
        url: String,
        cache_path: PathBuf,
        required_for: &ArtifactPurpose,
    ) -> eyre::Result<ArtifactPlan> {
        let hash = ContentHash::from_path(&cache_path, ContentHashAlgorithm::Blake3)?;
        let provenance = artifact_provenance(
            ArtifactSource::RemoteMaven,
            Some(coordinate.to_string()),
            Some(repo.name.clone()),
            Some(url.clone()),
            None,
            None,
            hash,
        );
        write_artifact_provenance(&cache_path, &provenance)?;
        Ok(ArtifactPlan {
            id: id.clone(),
            coordinate: Some(coordinate.to_string()),
            repository: Some(repo.name.clone()),
            url: Some(url),
            sha1: Some(hash),
            cache_path,
            downloaded: true,
            required_for: required_for.clone(),
            provenance,
        })
    }

    fn locked_remote_artifact_plan(
        id: &ArtifactId,
        coordinate: &MavenCoordinate,
        locked: &ArtifactLockEntry,
        cache_path: PathBuf,
        required_for: &ArtifactPurpose,
    ) -> eyre::Result<ArtifactPlan> {
        let hash = ContentHash::from_path(&cache_path, ContentHashAlgorithm::Blake3)?;
        let provenance = ArtifactProvenance {
            schema_version: 1,
            source: locked.source.clone(),
            coordinate: locked
                .coordinate
                .clone()
                .or_else(|| Some(coordinate.to_string())),
            repository: locked.repository.clone(),
            url: locked.url.clone(),
            original_path: locked.original_path.clone(),
            source_relative_path: locked.source_relative_path.clone(),
            source_git: locked.source_git.clone(),
            source_build: locked.source_build.clone(),
            hash,
        };
        write_artifact_provenance(&cache_path, &provenance)?;
        Ok(ArtifactPlan {
            id: id.clone(),
            coordinate: Some(coordinate.to_string()),
            repository: provenance.repository.clone(),
            url: provenance.url.clone(),
            sha1: Some(hash),
            cache_path,
            downloaded: true,
            required_for: required_for.clone(),
            provenance,
        })
    }

    fn local_artifact_plan(
        id: &ArtifactId,
        coordinate: &MavenCoordinate,
        local_artifact: LocalCachedArtifact,
        cache_path: PathBuf,
        required_for: &ArtifactPurpose,
        expected_hash: Option<&ContentHash>,
    ) -> eyre::Result<ArtifactPlan> {
        copy_file_to_path_checked_locked(&local_artifact.path, &cache_path, expected_hash)?;
        let hash = ContentHash::from_path(&cache_path, ContentHashAlgorithm::Blake3)?;
        let source_git = if local_artifact.source == ArtifactSource::ExplicitSource {
            source_git_provenance(&local_artifact.path)
        } else {
            None
        };
        let provenance = artifact_provenance(
            local_artifact.source,
            Some(coordinate.to_string()),
            Some(local_artifact.repository.clone()),
            None,
            Some(local_artifact.path.clone()),
            source_git,
            hash,
        );
        write_artifact_provenance(&cache_path, &provenance)?;
        Ok(ArtifactPlan {
            id: id.clone(),
            coordinate: Some(coordinate.to_string()),
            repository: Some(local_artifact.repository),
            url: Some(local_artifact.path.display().to_string()),
            sha1: Some(hash),
            cache_path,
            downloaded: false,
            required_for: required_for.clone(),
            provenance,
        })
    }

    pub(super) fn resolve_dependencies(
        &self,
        items: impl IntoIterator<Item = (String, MavenCoordinate)>,
    ) -> eyre::Result<Vec<DependencyPlan>> {
        let items = items.into_iter().collect::<Vec<_>>();
        self.with_prepared_dependency_batch(&items, || {
            let _span = tracing::debug_span!(
                "resolve_dependencies_parallel",
                dependency_count = items.len(),
                workers = rayon::current_num_threads()
            )
            .entered();
            items
                .par_iter()
                .map(|(configuration, coordinate)| {
                    self.cancellation_token.bail_if_cancelled()?;
                    self.resolve_dependency(configuration, coordinate)
                        .wrap_err_with(|| {
                            format!("Failed to resolve dependency {configuration} {coordinate}")
                        })
                })
                .collect::<Vec<eyre::Result<_>>>()
                .into_iter()
                .collect::<eyre::Result<Vec<_>>>()
                .wrap_err("Failed to resolve dependency")
        })
    }

    pub(super) fn resolve_dependency(
        &self,
        configuration: &str,
        coordinate: &MavenCoordinate,
    ) -> eyre::Result<DependencyPlan> {
        self.cancellation_token.bail_if_cancelled()?;
        let prepared_row = self.prepared_dependency(configuration, coordinate)?;
        let dynamic_version = coordinate.version.ends_with('+');
        let resolved = self.resolve_dynamic_coordinate(coordinate)?;
        self.cancellation_token.bail_if_cancelled()?;
        let source = if resolved.group == "curse.maven" {
            DependencySource::CurseMaven
        } else {
            DependencySource::Maven
        };
        let artifact = self.resolve_artifact(
            ArtifactId::from(format!("dependency:{configuration}:{resolved}")),
            &resolved,
            ArtifactPurpose::from(configuration),
        )?;

        let mut plan = DependencyPlan {
            configuration: configuration.to_string(),
            bundle: None,
            artifact_treatment:
                crate::toolchain_lockfile_schema::version::v3::ArtifactTreatmentV3::Plain,
            data_run_policy:
                crate::toolchain_lockfile_schema::version::v3::DataRunPolicyV3::Include,
            notation: coordinate.to_string(),
            resolved_notation: resolved.to_string(),
            source,
            cache_path: artifact.cache_path,
            url: artifact.url,
            dynamic_version,
        };
        if let Some(row) = prepared_row {
            apply_prepared_dependency_policy(&mut plan, row)?;
        }
        Ok(plan)
    }

    fn resolve_dynamic_coordinate(
        &self,
        coordinate: &MavenCoordinate,
    ) -> eyre::Result<MavenCoordinate> {
        self.cancellation_token.bail_if_cancelled()?;
        if let Some(request) = self.prepared_coordinate(coordinate)? {
            return MavenCoordinate::parse(request.resolved_coordinate().ok_or_else(|| {
                eyre::eyre!("frozen coordinate request has no coordinate identity")
            })?);
        }
        if !coordinate.version.ends_with('+') {
            self.validate_catalog_coordinate(coordinate)?;
            return Ok(coordinate.clone());
        }

        if let Some(lockfile) = &self.lockfile {
            let notation = coordinate.to_string();
            let Some(locked) = lockfile
                .dependencies
                .iter()
                .find(|dependency| dependency.notation == notation)
            else {
                eyre::bail!(
                    "Dynamic dependency {} is not present in sfm-toolchain.lock.json. Run jar build --branch {} --refresh to update the lockfile intentionally.",
                    notation,
                    lockfile.minecraft_version
                );
            };
            let resolved = MavenCoordinate::parse(&locked.resolved_notation)?;
            if resolved.version.ends_with('+') {
                eyre::bail!(
                    "sfm-toolchain.lock.json resolved {} to dynamic version {}; refresh the lockfile.",
                    notation,
                    locked.resolved_notation
                );
            }
            self.validate_catalog_coordinate(&resolved)?;
            return Ok(resolved);
        }

        let prefix = coordinate.version.trim_end_matches('+');
        let mut candidates = Vec::new();

        for repo in self.candidate_repositories(coordinate) {
            self.cancellation_token.bail_if_cancelled()?;
            let metadata_url = Self::maven_metadata_url(repo, coordinate);
            let metadata =
                match download_text_optional(&self.cancellation_token, &self.client, &metadata_url)
                {
                    Ok(metadata) => metadata,
                    Err(error) if self.cancellation_token.is_cancelled() => return Err(error),
                    Err(_) => continue,
                };

            self.cancellation_token.bail_if_cancelled()?;

            candidates.extend(
                parse_maven_versions(&metadata)
                    .into_iter()
                    .filter(|version| version.starts_with(prefix)),
            );
        }

        candidates.sort_by(|left, right| compare_version_text(left, right));
        candidates.dedup();

        let Some(version) = candidates.pop() else {
            eyre::bail!(
                "No Maven metadata version matched {} for {}:{}",
                coordinate.version,
                coordinate.group,
                coordinate.artifact
            );
        };

        Ok(MavenCoordinate {
            version,
            ..coordinate.clone()
        })
    }

    pub(super) fn candidate_repositories(&self, coordinate: &MavenCoordinate) -> Vec<&Repository> {
        let preferred_ids: &[&str] = if coordinate.group == "curse.maven" {
            &["cursemaven"]
        } else if coordinate.group == "com.teamcofh" {
            &["thermal"]
        } else if coordinate.group == "mezz.jei" {
            &["blamejared", "jei"]
        } else if coordinate.group == "org.parchmentmc.data" {
            &["parchment"]
        } else if coordinate.group == "org.spongepowered" {
            &["sponge", "maven-central"]
        } else if coordinate.group == "org.squiddev" {
            &["squiddev"]
        } else if coordinate.group == "net.minecraftforge" || coordinate.group == "de.oceanlabs.mcp"
        {
            &["forge"]
        } else if coordinate.group == "net.neoforged" {
            &["neoforged"]
        } else if coordinate.group.starts_with("org.")
            || coordinate.group.starts_with("com.github.")
            || coordinate.group.starts_with("junit")
        {
            &["maven-central"]
        } else {
            &[]
        };

        let mut selected = Vec::new();
        for id in preferred_ids {
            if let Some(repo) = self.repositories.iter().find(|repo| repo.name == *id) {
                selected.push(repo);
            }
        }

        if selected.is_empty() {
            selected.extend(self.repositories.iter());
        }

        selected
    }

    pub(super) fn cache_path_for(&self, coordinate: &MavenCoordinate) -> PathBuf {
        maven_cache_path_for(&self.cache_dir, coordinate)
    }

    fn artifact_url(repo: &Repository, coordinate: &MavenCoordinate) -> String {
        format!(
            "{}/{}/{}/{}/{}",
            repo.url.trim_end_matches('/'),
            coordinate.group.replace('.', "/"),
            coordinate.artifact,
            coordinate.version,
            coordinate.file_name()
        )
    }

    fn maven_metadata_url(repo: &Repository, coordinate: &MavenCoordinate) -> String {
        format!(
            "{}/{}/{}/maven-metadata.xml",
            repo.url.trim_end_matches('/'),
            coordinate.group.replace('.', "/"),
            coordinate.artifact
        )
    }

    pub(super) fn resolve_pom_runtime_dependencies(
        &self,
        coordinate: &MavenCoordinate,
    ) -> eyre::Result<Vec<MavenCoordinate>> {
        self.cancellation_token.bail_if_cancelled()?;
        if self.prepared_inputs.is_some() {
            self.prepared_coordinate(coordinate)?;
            eyre::bail!(
                "strict released dependencies use the frozen global runtime closure; per-root POM discovery is unavailable"
            );
        }
        if coordinate.group == "curse.maven"
            || coordinate.classifier.is_some()
            || coordinate.extension != "jar"
        {
            return Ok(Vec::new());
        }

        let pom_coordinate = coordinate.with_extension("pom");
        for repo in self.candidate_repositories(coordinate) {
            self.cancellation_token.bail_if_cancelled()?;
            let url = Self::artifact_url(repo, &pom_coordinate);
            let pom = match download_text_optional(&self.cancellation_token, &self.client, &url) {
                Ok(pom) => pom,
                Err(error) if self.cancellation_token.is_cancelled() => return Err(error),
                Err(_) => continue,
            };
            return Ok(parse_maven_pom_runtime_dependencies(&pom, coordinate));
        }

        Ok(Vec::new())
    }
}

fn verify_strict_cache_miss_source(source: &ArtifactSource, coordinate: &str) -> eyre::Result<()> {
    eyre::ensure!(
        !source.can_be_materialized_from_source(),
        "strict source-build cache miss for {coordinate}; external recipe dependency-closure validation is not integrated"
    );
    Ok(())
}

fn verify_strict_original_hash(
    expected: ContentHash,
    actual: ContentHash,
    coordinate: &str,
) -> eyre::Result<()> {
    eyre::ensure!(
        actual == expected,
        "strict artifact {coordinate} exact-byte mismatch: actual {actual}, original frozen pin {expected}; weak metadata cannot override it"
    );
    Ok(())
}

// Only planner policy is adapted. The schema-2 lock, role rows and provenance
// are untouched. Legacy callers retain their existing Plain/Include defaults.
fn apply_prepared_dependency_policy(
    plan: &mut DependencyPlan,
    row: &ReleasedPreparedDependency,
) -> eyre::Result<()> {
    use crate::toolchain_lockfile_schema::version::v3::ArtifactTreatmentV3;
    use crate::toolchain_lockfile_schema::version::v3::BundlePolicyV3;
    use crate::toolchain_lockfile_schema::version::v3::DataRunPolicyV3;
    plan.notation.clone_from(&row.requested_coordinate);
    plan.resolved_notation.clone_from(&row.resolved_coordinate);
    plan.dynamic_version = row.requested_coordinate != row.resolved_coordinate;
    plan.bundle = row.bundle.as_ref().map(|bundle| BundlePolicyV3 {
        accepted_version_range: bundle.accepted_version_range.clone(),
        artifact_version: bundle.artifact_version.clone(),
        is_obfuscated: bundle.is_obfuscated,
    });
    plan.artifact_treatment = match row.artifact_treatment.as_str() {
        "plain" => ArtifactTreatmentV3::Plain,
        "loader_managed_mod" => ArtifactTreatmentV3::LoaderManagedMod,
        other => eyre::bail!("unsupported prepared artifact treatment: {other}"),
    };
    plan.data_run_policy = match row.data_run_policy.as_str() {
        "include" => DataRunPolicyV3::Include,
        "exclude" => DataRunPolicyV3::Exclude,
        other => eyre::bail!("unsupported prepared data-run policy: {other}"),
    };
    Ok(())
}

pub(super) fn maven_cache_path_for(cache_dir: &Path, coordinate: &MavenCoordinate) -> PathBuf {
    let mut path = cache_dir.to_path_buf();
    for segment in coordinate.group.split('.') {
        path.push(segment);
    }
    path.join(&coordinate.artifact)
        .join(&coordinate.version)
        .join(coordinate.file_name())
}

#[derive(Debug)]
struct LocalCachedArtifact {
    path: PathBuf,
    source: ArtifactSource,
    repository: String,
}

fn find_explicit_source_artifact(
    coordinate: &MavenCoordinate,
    artifact_sources: &[PathBuf],
) -> Option<LocalCachedArtifact> {
    artifact_sources.iter().find_map(|artifact_source| {
        explicit_artifact_source_candidates(artifact_source, coordinate)
            .into_iter()
            .find(|candidate| candidate.is_file())
            .map(|path| LocalCachedArtifact {
                path,
                source: ArtifactSource::ExplicitSource,
                repository: "explicit-artifact-source".to_string(),
            })
    })
}

fn explicit_artifact_source_candidates(
    artifact_source: &Path,
    coordinate: &MavenCoordinate,
) -> Vec<PathBuf> {
    let file_name = coordinate.file_name();
    if artifact_source.is_file() {
        return artifact_source
            .file_name()
            .and_then(|name| name.to_str())
            .filter(|name| *name == file_name)
            .map_or_else(Vec::new, |_| vec![artifact_source.to_path_buf()]);
    }

    let maven_relative = PathBuf::from(coordinate.group.replace('.', "/"))
        .join(&coordinate.artifact)
        .join(&coordinate.version)
        .join(&file_name);
    vec![
        artifact_source.join(maven_relative),
        artifact_source.join(&file_name),
        artifact_source.join("build").join("libs").join(file_name),
    ]
}

fn find_local_cached_artifact(coordinate: &MavenCoordinate) -> Option<LocalCachedArtifact> {
    let relative = PathBuf::from(coordinate.group.replace('.', "/"))
        .join(&coordinate.artifact)
        .join(&coordinate.version)
        .join(coordinate.file_name());
    if let Some(home) = std::env::var_os("USERPROFILE").or_else(|| std::env::var_os("HOME")) {
        let home = PathBuf::from(home);
        let m2 = home.join(".m2").join("repository").join(&relative);
        if m2.is_file() {
            return Some(LocalCachedArtifact {
                path: m2,
                source: ArtifactSource::LocalM2Cache,
                repository: "local-artifact-cache".to_string(),
            });
        }

        let gradle_module = home
            .join(".gradle")
            .join("caches")
            .join("modules-2")
            .join("files-2.1")
            .join(&coordinate.group)
            .join(&coordinate.artifact)
            .join(&coordinate.version);
        if let Ok(hash_dirs) = fs::read_dir(gradle_module) {
            for hash_dir in hash_dirs.flatten() {
                let candidate = hash_dir.path().join(coordinate.file_name());
                if candidate.is_file() {
                    return Some(LocalCachedArtifact {
                        path: candidate,
                        source: ArtifactSource::LocalGradleModuleCache,
                        repository: "local-artifact-cache".to_string(),
                    });
                }
            }
        }
    }

    None
}

#[cfg(test)]
mod prepared_resolver_proposal_tests {
    use super::*;
    use crate::source_projection::candidate_lock::checked_file;
    use crate::source_projection::provenance::sha256;
    use crate::source_projection::released_native_inputs::ReleasedBundlePolicy;
    use crate::source_projection::released_native_inputs::ReleasedNativeRequest;
    use crate::source_projection::released_native_inputs::prepare_released_native_inputs;
    use crate::toolchain_lockfile_schema::ToolchainLockfileDocument;
    use crate::toolchain_lockfile_schema::parse_document;
    use facet::Facet;
    use std::cell::Cell;
    use std::collections::BTreeMap;
    use std::io::Read;

    #[derive(Debug, Facet)]
    struct Recipes {
        targets: Vec<Recipe>,
    }

    #[derive(Debug, Facet)]
    struct Recipe {
        target: String,
        minecraft_version: String,
        recipe_id: String,
        platform: Platform,
        source_lock: LockWitness,
        role_input_hashes: Vec<InputWitness>,
        recorded_dependency_roles: Vec<RoleWitness>,
    }

    #[derive(Debug, Facet)]
    struct Platform {
        java_major: u16,
    }

    #[derive(Debug, Facet)]
    struct LockWitness {
        path: String,
        sha256: String,
        bytes: usize,
    }

    #[derive(Debug, Facet)]
    struct InputWitness {
        path: String,
    }

    #[derive(Debug, Facet)]
    struct RoleWitness {
        index: usize,
        configuration: String,
        notation: String,
        resolved_notation: String,
        artifact_index: usize,
        artifact_hash: String,
        artifact_treatment: String,
        data_run_policy: String,
        bundle: Option<ReleasedBundlePolicy>,
    }

    type Fixture = (Recipe, Vec<u8>, BTreeMap<String, Vec<u8>>);

    fn read_bounded(path: &Path, limit: u64) -> eyre::Result<Vec<u8>> {
        let file = fs::File::open(path)?;
        let metadata = file.metadata()?;
        eyre::ensure!(
            metadata.is_file() && metadata.len() <= limit,
            "fixture file exceeds bound"
        );
        let mut bytes = Vec::new();
        file.take(limit + 1).read_to_end(&mut bytes)?;
        eyre::ensure!(bytes.len() as u64 <= limit, "fixture grew beyond bound");
        Ok(bytes)
    }

    fn fixture(target: &str) -> eyre::Result<Fixture> {
        let repository = Path::new(env!("CARGO_MANIFEST_DIR"))
            .ancestors()
            .nth(3)
            .ok_or_else(|| eyre::eyre!("CLI crate must be below repository"))?;
        let review = read_bounded(
            &checked_file(
                repository,
                "docs/tasks/sfm-core-released-native-adapter-review.json",
            )?,
            512 * 1024,
        )?;
        let recipe = facet_json::from_str::<Recipes>(std::str::from_utf8(&review)?)?
            .targets
            .into_iter()
            .find(|recipe| recipe.target == target)
            .ok_or_else(|| eyre::eyre!("fixture target missing"))?;
        let raw = read_bounded(
            &checked_file(repository, &recipe.source_lock.path)?,
            1024 * 1024,
        )?;
        eyre::ensure!(
            raw.len() == recipe.source_lock.bytes
                && sha256(&raw) == format!("sha256:{}", recipe.source_lock.sha256),
            "fixture raw lock changed"
        );
        let inputs = recipe
            .role_input_hashes
            .iter()
            .map(|input| {
                Ok((
                    input.path.clone(),
                    read_bounded(&checked_file(repository, &input.path)?, 512 * 1024)?,
                ))
            })
            .collect::<eyre::Result<BTreeMap<_, _>>>()?;
        Ok((recipe, raw, inputs))
    }

    fn prepared(target: &str) -> eyre::Result<Arc<PreparedDependencyInputs>> {
        let (recipe, raw, inputs) = fixture(target)?;
        let request = ReleasedNativeRequest {
            target_id: &recipe.target,
            minecraft_version: &recipe.minecraft_version,
            java_major: recipe.platform.java_major,
            recipe_id: &recipe.recipe_id,
            refresh: false,
        };
        Ok(Arc::new(PreparedDependencyInputs::from_released(
            prepare_released_native_inputs(&request, &raw, &inputs, &BTreeMap::new())?,
            false,
        )?))
    }

    fn strict(target: &str, cache: PathBuf) -> eyre::Result<Resolver> {
        Resolver::new_prepared(cache, prepared(target)?, false, CancellationToken::new())
    }

    fn legacy(cache: PathBuf) -> eyre::Result<Resolver> {
        Resolver::new(
            cache,
            Vec::new(),
            false,
            false,
            Vec::new(),
            None,
            None,
            CancellationToken::new(),
        )
    }

    fn artifact_request(
        coordinate: &str,
    ) -> eyre::Result<(ArtifactId, MavenCoordinate, ArtifactPurpose)> {
        Ok((
            ArtifactId::from("prepared-test"),
            MavenCoordinate::parse(coordinate)?,
            ArtifactPurpose::from("prepared test"),
        ))
    }

    #[test]
    fn legacy_constructor_and_non_effectful_paths_remain_unrestricted() -> eyre::Result<()> {
        let directory = tempfile::tempdir()?;
        let cache = directory.path().join("legacy cache");
        let resolver = legacy(cache.clone())?;
        eyre::ensure!(
            resolver.prepared_dependency_inputs().is_none(),
            "legacy selected strict input"
        );
        let unknown = MavenCoordinate::parse("example:unknown:1")?;
        eyre::ensure!(
            resolver.resolve_dynamic_coordinate(&unknown)? == unknown,
            "legacy exact-coordinate change"
        );
        let calls = Cell::new(0usize);
        resolver.with_prepared_artifact_batch(&[artifact_request("example:unknown:1")?], || {
            calls.set(calls.get() + 1);
            Ok(())
        })?;
        resolver.with_prepared_dependency_batch(
            &[("legacyConfiguration".to_owned(), unknown)],
            || {
                calls.set(calls.get() + 1);
                Ok(())
            },
        )?;
        resolver
            .refuse_prepared_repository_fallback(&MavenCoordinate::parse("example:unknown:1")?)?;
        eyre::ensure!(
            calls.get() == 2 && !cache.exists(),
            "legacy callback semantics changed"
        );
        Ok(())
    }

    #[test]
    fn strict_refresh_and_single_unknown_requests_refuse_before_cache_effects() -> eyre::Result<()>
    {
        let directory = tempfile::tempdir()?;
        let cache = directory.path().join("never created cache");
        let error = Resolver::new_prepared(
            cache.clone(),
            prepared("1.19.4")?,
            true,
            CancellationToken::new(),
        )
        .unwrap_err()
        .to_string();
        eyre::ensure!(
            error.contains("immutable") && error.contains("before any effects"),
            "refresh did not refuse at the input boundary"
        );
        let mut resolver = strict("1.19.4", cache.clone())?;
        let (id, unknown, purpose) = artifact_request("example:unknown:1")?;
        eyre::ensure!(
            resolver
                .resolve_artifact(id.clone(), &unknown, purpose.clone())
                .is_err(),
            "single unknown artifact accepted"
        );
        eyre::ensure!(
            resolver
                .cached_artifact_plan(
                    &id,
                    &unknown,
                    cache.join("absent.jar"),
                    &purpose,
                    ContentHash::from_bytes(b"absent", ContentHashAlgorithm::Blake3)
                )
                .is_err(),
            "direct unknown cache entry passed eager pin lookup"
        );
        let known = MavenCoordinate::parse("net.minecraftforge:forge:1.19.4-45.0.9:userdev")?;
        eyre::ensure!(
            resolver
                .resolve_dependency("unrecordedRole", &known)
                .is_err(),
            "single unknown role accepted"
        );
        resolver.refresh = true;
        let (id, known, purpose) =
            artifact_request("net.minecraftforge:forge:1.19.4-45.0.9:userdev")?;
        let error = resolver
            .resolve_artifact(id, &known, purpose)
            .unwrap_err()
            .to_string();
        eyre::ensure!(
            error.contains("immutable") && !cache.exists(),
            "late refresh or single-request refusal touched the cache"
        );
        Ok(())
    }

    #[test]
    fn strict_mixed_batches_refuse_before_the_first_effect_callback() -> eyre::Result<()> {
        let directory = tempfile::tempdir()?;
        let cache = directory.path().join("untouched batch cache");
        let resolver = strict("1.19.4", cache.clone())?;
        let effects = Cell::new(0usize);
        for values in [
            vec![
                artifact_request("net.minecraftforge:forge:1.19.4-45.0.9:userdev")?,
                artifact_request("example:unknown:1")?,
            ],
            vec![
                artifact_request("example:unknown:1")?,
                artifact_request("net.minecraftforge:forge:1.19.4-45.0.9:userdev")?,
            ],
        ] {
            eyre::ensure!(
                resolver
                    .with_prepared_artifact_batch(&values, || {
                        effects.set(effects.get() + 1);
                        Ok(())
                    })
                    .is_err(),
                "mixed artifact batch dispatched"
            );
        }
        let prepared = resolver
            .prepared_dependency_inputs()
            .ok_or_else(|| eyre::eyre!("prepared handle missing"))?;
        let known = &prepared.dependencies()[0];
        let values = vec![
            (
                known.configuration.clone(),
                MavenCoordinate::parse(&known.requested_coordinate)?,
            ),
            (
                "unrecordedRole".to_owned(),
                MavenCoordinate::parse(&known.resolved_coordinate)?,
            ),
        ];
        eyre::ensure!(
            resolver
                .with_prepared_dependency_batch(&values, || {
                    effects.set(effects.get() + 1);
                    Ok(())
                })
                .is_err(),
            "unrecorded role batch dispatched"
        );
        eyre::ensure!(
            effects.get() == 0 && !cache.exists(),
            "batch refusal had effects"
        );
        Ok(())
    }

    #[test]
    fn strict_dynamic_resolution_uses_only_the_recorded_alias() -> eyre::Result<()> {
        let directory = tempfile::tempdir()?;
        let resolver = strict("1.19.2", directory.path().join("alias cache"))?;
        let requested = MavenCoordinate::parse("com.teamcofh:cofh_core:1.19.2-10.2.0.+")?;
        eyre::ensure!(
            resolver.resolve_dynamic_coordinate(&requested)?.to_string()
                == "com.teamcofh:cofh_core:1.19.2-10.2.0.38",
            "dynamic pin drift"
        );
        eyre::ensure!(
            resolver
                .resolve_dynamic_coordinate(&MavenCoordinate::parse(
                    "com.teamcofh:cofh_core:1.19.2-10.2.+"
                )?)
                .is_err(),
            "unrecorded alias rediscovered"
        );
        Ok(())
    }

    #[test]
    fn strict_url_guard_binds_one_pin_and_refuses_repository_fallback() -> eyre::Result<()> {
        let directory = tempfile::tempdir()?;
        let cache = directory.path().join("url cache");
        let resolver = strict("1.19.4", cache.clone())?;
        let prepared = resolver
            .prepared_dependency_inputs()
            .ok_or_else(|| eyre::eyre!("prepared handle missing"))?;
        let coordinate = MavenCoordinate::parse("net.minecraftforge:forge:1.19.4-45.0.9:userdev")?;
        let request = prepared.coordinate(&coordinate.to_string(), false)?;
        let url = request
            .exact_transport_url()
            .ok_or_else(|| eyre::eyre!("transport URL absent"))?;
        let other_url = prepared
            .original_lock()
            .artifacts
            .iter()
            .find(|pin| pin.coordinate.is_some() && pin.coordinate != Some(coordinate.to_string()))
            .and_then(|pin| pin.url.as_deref())
            .ok_or_else(|| eyre::eyre!("other exact transport URL absent"))?;
        let effects = Cell::new(0usize);
        for candidate in ["https://example.invalid/unpinned.jar", other_url] {
            eyre::ensure!(
                resolver
                    .with_prepared_url_guard(&coordinate, candidate, || {
                        effects.set(effects.get() + 1);
                        Ok(())
                    })
                    .is_err(),
                "unbound URL dispatched"
            );
        }
        eyre::ensure!(
            resolver
                .refuse_prepared_repository_fallback(&coordinate)
                .is_err(),
            "repository fallback accepted"
        );
        eyre::ensure!(
            effects.get() == 0 && !cache.exists(),
            "URL refusal had effects"
        );
        resolver.with_prepared_url_guard(&coordinate, url, || {
            effects.set(effects.get() + 1);
            Ok(())
        })?;
        eyre::ensure!(
            effects.get() == 1 && !cache.exists(),
            "bound pure URL callback not accepted"
        );
        Ok(())
    }

    #[test]
    fn strict_per_root_pom_refuses_even_classifier_roots_and_keeps_global_rows() -> eyre::Result<()>
    {
        let directory = tempfile::tempdir()?;
        let resolver = strict("1.21.1", directory.path().join("runtime cache"))?;
        let prepared = resolver
            .prepared_dependency_inputs()
            .ok_or_else(|| eyre::eyre!("prepared handle missing"))?;
        let classifier = prepared
            .original_lock()
            .artifacts
            .iter()
            .filter_map(|pin| pin.coordinate.as_deref())
            .map(MavenCoordinate::parse)
            .collect::<eyre::Result<Vec<_>>>()?
            .into_iter()
            .find(|coordinate| coordinate.classifier.is_some())
            .ok_or_else(|| eyre::eyre!("classifier fixture absent"))?;
        let error = resolver
            .resolve_pom_runtime_dependencies(&classifier)
            .unwrap_err()
            .to_string();
        eyre::ensure!(
            error.contains("per-root POM discovery is unavailable"),
            "classifier returned synthetic empty closure"
        );
        let rows = resolver
            .frozen_runtime_rows_for(prepared.runtime_roots())?
            .ok_or_else(|| eyre::eyre!("global closure absent"))?;
        eyre::ensure!(
            rows.len() == 1
                && rows[0].resolved_coordinate == "org.appliedenergistics:guideme:21.1.1",
            "global closure drift"
        );
        let mut changed = prepared.runtime_roots().to_vec();
        changed.pop();
        eyre::ensure!(
            resolver.frozen_runtime_rows_for(&changed).is_err(),
            "partial roots used global closure"
        );
        Ok(())
    }

    #[test]
    fn strict_planner_selected_roots_use_ordered_global_closure_without_pom_discovery()
    -> eyre::Result<()> {
        let directory = tempfile::tempdir()?;
        for target in ["1.20.3", "1.20.4", "1.21.0", "1.21.1"] {
            let cache = directory.path().join(target);
            let (_fixture, project) =
                crate::jar_build::nfrt_launch_contract::tests::project_fixture(target, "release")?;
            let resolver = Resolver::new_prepared(
                cache.clone(),
                project.dependencies().clone(),
                false,
                CancellationToken::new(),
            )?;
            let inputs = resolver.prepared_dependency_inputs().unwrap();
            let selected = inputs
                .runtime_roots()
                .iter()
                .map(|root| (root.configuration.clone(), root.resolved_coordinate.clone()))
                .collect::<Vec<_>>();
            let actual = resolver
                .frozen_runtime_rows_for_selected_roots(&selected)?
                .unwrap();
            let expected = inputs.frozen_runtime_rows_for(inputs.runtime_roots(), false)?;
            eyre::ensure!(actual == expected, "{target}: frozen closure changed");
            if target == "1.20.4" {
                eyre::ensure!(
                    selected.len() == 3 && actual.is_empty(),
                    "1.20.4 must retain three roots and its recorded empty extra closure"
                );
            }
            eyre::ensure!(
                resolver
                    .frozen_runtime_rows_for_selected_roots(&[(
                        "implementation".to_owned(),
                        "example:unapproved:1".to_owned(),
                    )])
                    .is_err()
                    && !cache.exists(),
                "unknown root accepted or pure selection created cache state"
            );
            if selected.is_empty() {
                continue;
            }
            let mut missing = selected.clone();
            missing.pop();
            eyre::ensure!(
                resolver
                    .frozen_runtime_rows_for_selected_roots(&missing)
                    .is_err()
            );
            let mut reordered = selected.clone();
            reordered.reverse();
            if reordered != selected {
                eyre::ensure!(
                    resolver
                        .frozen_runtime_rows_for_selected_roots(&reordered)
                        .is_err()
                );
            }
            let mut changed = selected.clone();
            changed[0].1 = "example:unapproved:1".to_owned();
            eyre::ensure!(
                resolver
                    .frozen_runtime_rows_for_selected_roots(&changed)
                    .is_err()
            );
            let mut duplicate = selected.clone();
            duplicate.push(selected[0].clone());
            eyre::ensure!(
                resolver
                    .frozen_runtime_rows_for_selected_roots(&duplicate)
                    .is_err()
            );
            eyre::ensure!(
                !cache.exists(),
                "pure closure selection created cache state"
            );
        }
        let resolver = legacy(directory.path().join("legacy"))?;
        eyre::ensure!(
            resolver
                .frozen_runtime_rows_for_selected_roots(&[])
                .is_ok_and(|rows| rows.is_none())
        );
        Ok(())
    }

    #[test]
    fn strict_hash_gate_never_uses_weak_metadata_as_identity() -> eyre::Result<()> {
        let (_, raw, _) = fixture("26.1.2")?;
        let ToolchainLockfileDocument::V2 { lockfile: lock, .. } =
            parse_document(std::str::from_utf8(&raw)?)?
        else {
            eyre::bail!("fixture is not schema 2");
        };
        let pin = &lock.artifacts[18];
        eyre::ensure!(pin.weak.is_some(), "weak fixture lost");
        let coordinate = pin
            .coordinate
            .as_deref()
            .ok_or_else(|| eyre::eyre!("coordinate absent"))?;
        eyre::ensure!(
            verify_strict_cache_miss_source(&pin.source, coordinate).is_err(),
            "source-built cache miss crossed the external recipe boundary"
        );
        verify_strict_cache_miss_source(&ArtifactSource::RemoteMaven, "example:remote:1")?;
        let wrong =
            ContentHash::from_bytes(b"modId=mekanism\nversion=10.8.0\n", pin.hash.algorithm);
        eyre::ensure!(
            verify_strict_original_hash(pin.hash, wrong, coordinate).is_err(),
            "metadata blessed another hash"
        );
        verify_strict_original_hash(pin.hash, pin.hash, coordinate)?;
        // This is a comparison-helper proof, not 26.1.2 exact-byte preparation.
        Ok(())
    }

    #[test]
    #[ignore = "requires an explicitly supplied existing artifact; never acquires or rebuilds it"]
    fn exact_original_source_built_cache_is_reused_and_changed_bytes_refuse() -> eyre::Result<()> {
        let supplied = std::env::var_os("SFM_EXACT_RELEASED_MEKANISM_26_JAR")
            .ok_or_else(|| eyre::eyre!("supply SFM_EXACT_RELEASED_MEKANISM_26_JAR explicitly"))?;
        let bytes = read_bounded(Path::new(&supplied), 16 * 1024 * 1024)?;
        let (recipe, raw, inputs) = fixture("26.1.2")?;
        let request = ReleasedNativeRequest {
            target_id: &recipe.target,
            minecraft_version: &recipe.minecraft_version,
            java_major: recipe.platform.java_major,
            recipe_id: &recipe.recipe_id,
            refresh: false,
        };
        let review =
            crate::source_projection::released_native_inputs::review_released_native_inputs(
                &request, &raw, &inputs,
            )?;
        eyre::ensure!(
            review.exact_byte_requirements().len() == 1,
            "weak pin scope changed"
        );
        let index = review.exact_byte_requirements()[0].artifact_index;
        let prepared = Arc::new(PreparedDependencyInputs::from_released(
            review.require_exact_bytes(&BTreeMap::from([(index, bytes.clone())]))?,
            false,
        )?);
        let pin = &prepared.original_lock().artifacts[index];
        let coordinate = MavenCoordinate::parse(
            pin.coordinate
                .as_deref()
                .ok_or_else(|| eyre::eyre!("verified pin lacks coordinate"))?,
        )?;
        let hash = pin.hash;
        let directory = tempfile::tempdir()?;
        let resolver = Resolver::new_prepared(
            directory.path().join("exact cache"),
            prepared,
            false,
            CancellationToken::new(),
        )?;
        let path = resolver.cache_path_for(&coordinate);
        fs::create_dir_all(
            path.parent()
                .ok_or_else(|| eyre::eyre!("cache parent absent"))?,
        )?;
        fs::write(&path, &bytes)?;
        let id = ArtifactId::from("exact-source-built-proof");
        let purpose = ArtifactPurpose::from("exact existing artifact proof");
        let wrong = ContentHash::from_bytes(b"not the artifact", hash.algorithm);
        eyre::ensure!(
            resolver
                .cached_artifact_plan(&id, &coordinate, path.clone(), &purpose, wrong)
                .is_err()
                && read_artifact_provenance(&path)?.is_none(),
            "wrong caller digest was accepted or wrote provenance"
        );
        let plan = resolver.resolve_artifact(id.clone(), &coordinate, purpose.clone())?;
        eyre::ensure!(
            plan.cache_path == path
                && !plan.downloaded
                && plan.sha1 == Some(hash)
                && fs::read(&path)? == bytes
                && read_artifact_provenance(&path)?.is_some(),
            "exact cache bytes were not verified and reused"
        );
        let before_provenance = read_artifact_provenance(&path)?;
        let mut changed = bytes;
        changed[0] ^= 1;
        fs::write(&path, &changed)?;
        eyre::ensure!(
            resolver.resolve_artifact(id, &coordinate, purpose).is_err()
                && fs::read(&path)? == changed
                && read_artifact_provenance(&path)? == before_provenance,
            "changed source-built bytes were accepted or repaired across the unsealed recipe boundary"
        );
        resolver
            .prepared_dependency_inputs()
            .ok_or_else(|| eyre::eyre!("prepared handle disappeared"))?
            .verify_source_lock_bytes(&raw, false)?;
        Ok(())
    }

    #[test]
    fn strict_handle_is_shared_and_planner_role_policy_is_preserved() -> eyre::Result<()> {
        let directory = tempfile::tempdir()?;
        let prepared = prepared("1.19.2")?;
        let resolver = Resolver::new_prepared(
            directory.path().join("shared cache"),
            Arc::clone(&prepared),
            false,
            CancellationToken::new(),
        )?;
        let cloned = resolver.clone();
        eyre::ensure!(
            Arc::ptr_eq(
                resolver
                    .prepared_dependency_inputs()
                    .ok_or_else(|| eyre::eyre!("missing handle"))?,
                cloned
                    .prepared_dependency_inputs()
                    .ok_or_else(|| eyre::eyre!("missing clone handle"))?
            ),
            "prepared handle not shared"
        );
        eyre::ensure!(
            Arc::ptr_eq(
                resolver
                    .lockfile
                    .as_ref()
                    .ok_or_else(|| eyre::eyre!("lock absent"))?,
                resolver
                    .materialization_lockfile
                    .as_ref()
                    .ok_or_else(|| eyre::eyre!("source lock absent"))?
            ),
            "legacy pin view copied instead of shared"
        );
        let row = prepared
            .dependencies()
            .iter()
            .find(|row| row.data_run_policy == "exclude")
            .ok_or_else(|| eyre::eyre!("real Data exclusion fixture absent"))?;
        eyre::ensure!(
            resolver
                .prepared_dependency(
                    &row.configuration,
                    &MavenCoordinate::parse(&row.requested_coordinate)?
                )?
                .is_some(),
            "declared role absent"
        );
        let mut plan = DependencyPlan {
            configuration: row.configuration.clone(),
            bundle: None,
            artifact_treatment:
                crate::toolchain_lockfile_schema::version::v3::ArtifactTreatmentV3::Plain,
            data_run_policy:
                crate::toolchain_lockfile_schema::version::v3::DataRunPolicyV3::Include,
            notation: row.resolved_coordinate.clone(),
            resolved_notation: row.resolved_coordinate.clone(),
            source: DependencySource::CurseMaven,
            cache_path: directory.path().join("not materialized.jar"),
            url: None,
            dynamic_version: false,
        };
        apply_prepared_dependency_policy(&mut plan, row)?;
        eyre::ensure!(
            plan.data_run_policy
                == crate::toolchain_lockfile_schema::version::v3::DataRunPolicyV3::Exclude
                && plan.notation == row.requested_coordinate,
            "Data policy lost"
        );
        let (recipe, _, _) = fixture("26.1.2")?;
        let bundle = recipe
            .recorded_dependency_roles
            .iter()
            .find(|role| role.bundle.is_some())
            .ok_or_else(|| eyre::eyre!("real bundle fixture absent"))?;
        let row = ReleasedPreparedDependency {
            original_dependency_index: Some(bundle.index),
            configuration: bundle.configuration.clone(),
            requested_coordinate: bundle.notation.clone(),
            resolved_coordinate: bundle.resolved_notation.clone(),
            artifact_index: bundle.artifact_index,
            artifact_hash: bundle.artifact_hash.clone(),
            artifact_treatment: bundle.artifact_treatment.clone(),
            data_run_policy: bundle.data_run_policy.clone(),
            bundle: bundle.bundle.clone(),
        };
        apply_prepared_dependency_policy(&mut plan, &row)?;
        eyre::ensure!(
            plan.bundle
                .as_ref()
                .is_some_and(|bundle| bundle.accepted_version_range == "[4.13.1]"
                    && bundle.artifact_version == "4.13.1"
                    && !bundle.is_obfuscated),
            "bundle policy lost"
        );
        Ok(())
    }
}
