use crate::dependency_inventory::AcquisitionStatus;
use crate::dependency_inventory::DependencyInventory;
use crate::dependency_inventory::SourceStatus;
use crate::source_archive::extracted_tree_matches_archive;
use crate::source_decompile::completed_tree_matches;
use crate::source_git::locked_git_tree_matches_commit;
use crate::toolchain_lockfile_schema::version::v3::SourceProviderV3;
use std::path::Path;
use std::path::PathBuf;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(crate) enum SourceProviderKind {
    MavenSources,
    Git,
    Decompile,
    PlatformPipeline,
}

impl SourceProviderKind {
    #[must_use]
    pub const fn label(self) -> &'static str {
        match self {
            Self::MavenSources => "maven-sources",
            Self::Git => "git",
            Self::Decompile => "decompile",
            Self::PlatformPipeline => "platform-pipeline",
        }
    }
}

#[derive(Clone, Copy)]
pub(crate) struct SourceProviderView<'a> {
    inventory: &'a DependencyInventory,
    provider: &'a SourceProviderV3,
    priority: usize,
}

impl<'a> SourceProviderView<'a> {
    #[must_use]
    pub const fn new(
        inventory: &'a DependencyInventory,
        provider: &'a SourceProviderV3,
        priority: usize,
    ) -> Self {
        Self {
            inventory,
            provider,
            priority,
        }
    }

    #[must_use]
    pub const fn definition(self) -> &'a SourceProviderV3 {
        self.provider
    }

    #[must_use]
    pub fn id(self) -> &'a str {
        match self.provider {
            SourceProviderV3::MavenSources(provider) => &provider.id,
            SourceProviderV3::Git(provider) => &provider.id,
            SourceProviderV3::Decompile(provider) => &provider.id,
            SourceProviderV3::PlatformPipeline(provider) => &provider.id,
        }
    }

    #[must_use]
    pub const fn kind(self) -> SourceProviderKind {
        match self.provider {
            SourceProviderV3::MavenSources(_) => SourceProviderKind::MavenSources,
            SourceProviderV3::Git(_) => SourceProviderKind::Git,
            SourceProviderV3::Decompile(_) => SourceProviderKind::Decompile,
            SourceProviderV3::PlatformPipeline(_) => SourceProviderKind::PlatformPipeline,
        }
    }

    #[must_use]
    pub const fn priority(self) -> usize {
        self.priority
    }

    #[must_use]
    pub fn tree_path(self) -> PathBuf {
        self.inventory.local_path(self.tree_cache_path())
    }

    #[must_use]
    pub fn searchable_roots(self) -> Vec<PathBuf> {
        let tree = self.tree_path();
        let roots = self.roots();
        if roots.is_empty() {
            vec![tree]
        } else {
            roots.iter().map(|root| tree.join(root)).collect()
        }
    }

    #[must_use]
    pub fn status(self) -> SourceStatus {
        let materialization = match self.provider {
            SourceProviderV3::MavenSources(provider) => {
                let archive = self.inventory.locked_file_status(
                    &provider.derived_checks.archive_cache_path,
                    provider.derived_checks.hash,
                );
                if archive == AcquisitionStatus::Stale {
                    SourceStatus::Stale
                } else if archive == AcquisitionStatus::Acquired
                    && extracted_tree_matches_archive(
                        &self
                            .inventory
                            .local_path(&provider.derived_checks.archive_cache_path),
                        &self.tree_path(),
                    )
                    .unwrap_or(false)
                {
                    SourceStatus::Acquired
                } else {
                    SourceStatus::Missing
                }
            }
            SourceProviderV3::Git(provider) => {
                let repository = self
                    .inventory
                    .local_path(&provider.derived_checks.repository_cache_path);
                if locked_git_tree_matches_commit(
                    &repository,
                    &self.tree_path(),
                    &provider.derived_checks.commit,
                    &provider.declaration.roots,
                ) {
                    SourceStatus::Acquired
                } else {
                    SourceStatus::Missing
                }
            }
            SourceProviderV3::Decompile(provider) => {
                if completed_tree_matches(
                    &self.tree_path(),
                    &provider.derived_checks.fingerprint,
                    &provider.declaration.roots,
                ) {
                    SourceStatus::Acquired
                } else {
                    SourceStatus::Missing
                }
            }
            SourceProviderV3::PlatformPipeline(provider) => {
                let tree = self.tree_path();
                let source_jar = tree.with_extension("jar");
                let archive_matches_lock = provider
                    .derived_checks
                    .source_jar_sha256
                    .as_deref()
                    .is_none_or(|expected| {
                        std::fs::read(&source_jar).is_ok_and(|bytes| {
                            crate::source_projection::provenance::sha256(&bytes) == expected
                        })
                    })
                    && provider
                        .derived_checks
                        .source_payload_sha256
                        .as_deref()
                        .is_none_or(|expected| {
                            crate::source_archive::archive_payload_sha256(&source_jar)
                                .is_ok_and(|actual| actual == expected)
                        });
                if archive_matches_lock
                    && tree
                        .extension()
                        .is_some_and(|extension| extension == "filetree")
                    && extracted_tree_matches_archive(&source_jar, &tree).unwrap_or(false)
                {
                    SourceStatus::Acquired
                } else {
                    SourceStatus::Missing
                }
            }
        };
        if materialization == SourceStatus::Acquired
            && self.searchable_roots().iter().any(|root| !root.is_dir())
        {
            SourceStatus::Missing
        } else {
            materialization
        }
    }

    #[must_use]
    pub fn unavailable_reason(self) -> Option<&'static str> {
        if self.status() == SourceStatus::Acquired {
            return None;
        }
        match self.provider {
            SourceProviderV3::MavenSources(provider) => {
                let archive = self.inventory.locked_file_status(
                    &provider.derived_checks.archive_cache_path,
                    provider.derived_checks.hash,
                );
                match archive {
                    AcquisitionStatus::Stale => Some("locked source archive hash does not match"),
                    AcquisitionStatus::Missing => Some("locked source archive is not cached"),
                    AcquisitionStatus::Acquired if !self.tree_path().is_dir() => {
                        Some("source archive has not been extracted")
                    }
                    AcquisitionStatus::Acquired => Some(
                        "source tree differs from its authenticated archive or a configured root is missing",
                    ),
                }
            }
            SourceProviderV3::Git(provider) => {
                let repository = self
                    .inventory
                    .local_path(&provider.derived_checks.repository_cache_path);
                match (repository.is_dir(), self.tree_path().is_dir()) {
                    (false, false) => Some("managed repository and materialized tree are missing"),
                    (false, true) => Some("managed repository is missing"),
                    (true, false) => Some("locked commit tree is not materialized"),
                    (true, true) => Some(
                        "Git source tree differs from its locked commit or a configured root is missing",
                    ),
                }
            }
            SourceProviderV3::Decompile(_) => {
                Some("decompiled tree is missing or does not match its locked fingerprint")
            }
            SourceProviderV3::PlatformPipeline(_) => {
                Some("platform source filetree is missing or differs from its source JAR")
            }
        }
    }

    fn roots(self) -> &'a [String] {
        match self.provider {
            SourceProviderV3::MavenSources(provider) => &provider.declaration.roots,
            SourceProviderV3::Git(provider) => &provider.declaration.roots,
            SourceProviderV3::Decompile(provider) => &provider.declaration.roots,
            SourceProviderV3::PlatformPipeline(provider) => &provider.declaration.roots,
        }
    }

    fn tree_cache_path(self) -> &'a Path {
        match self.provider {
            SourceProviderV3::MavenSources(provider) => &provider.derived_checks.tree_cache_path,
            SourceProviderV3::Git(provider) => &provider.derived_checks.tree_cache_path,
            SourceProviderV3::Decompile(provider) => &provider.derived_checks.tree_cache_path,
            SourceProviderV3::PlatformPipeline(provider) => {
                &provider.derived_checks.tree_cache_path
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::branch_targets::BranchName;
    use crate::branch_targets::WorktreePath;
    use crate::branch_targets::WorktreeTarget;
    use crate::jar_build::hash::ContentHash;
    use crate::jar_build::hash::ContentHashAlgorithm;
    use crate::paths::CacheHome;
    use crate::source_archive::extract_zip_atomically;
    use crate::source_cache::SourceCacheLayout;
    use crate::source_decompile::FINGERPRINT_FILE;
    use crate::toolchain_lockfile_schema::read_current;
    use crate::toolchain_lockfile_schema::version::v3::DecompileSourceDeclarationV3;
    use crate::toolchain_lockfile_schema::version::v3::DecompileSourceDerivedChecksV3;
    use crate::toolchain_lockfile_schema::version::v3::DecompileSourceProviderV3;
    use crate::toolchain_lockfile_schema::version::v3::GitSourceDeclarationV3;
    use crate::toolchain_lockfile_schema::version::v3::GitSourceDerivedChecksV3;
    use crate::toolchain_lockfile_schema::version::v3::GitSourceProviderV3;
    use crate::toolchain_lockfile_schema::version::v3::MavenSourceDeclarationV3;
    use crate::toolchain_lockfile_schema::version::v3::MavenSourceDerivedChecksV3;
    use crate::toolchain_lockfile_schema::version::v3::MavenSourceProviderV3;
    use crate::toolchain_lockfile_schema::version::v3::PlatformPipelineSourceDeclarationV3;
    use crate::toolchain_lockfile_schema::version::v3::PlatformPipelineSourceDerivedChecksV3;
    use crate::toolchain_lockfile_schema::version::v3::PlatformPipelineSourceProviderV3;
    use crate::toolchain_lockfile_schema::version::v3::ToolchainComponentKindV3;
    use std::io::Cursor;
    use std::io::Write;
    use zip::ZipWriter;
    use zip::write::SimpleFileOptions;

    #[test]
    fn historical_platform_source_path_does_not_adopt_a_shared_candidate() {
        let temporary = tempfile::tempdir().expect("temporary roots");
        let mut inventory = fixture(CacheHome(temporary.path().join("cache")));
        inventory.target.worktree_path = WorktreePath::from(temporary.path().join("checkout"));
        let relative = PathBuf::from(
            "build/sfm-toolchain/forge/1.19.2/sources/combined-deobfuscated.filetree",
        );
        let provider = SourceProviderV3::PlatformPipeline(PlatformPipelineSourceProviderV3 {
            id: "minecraft-pipeline".to_owned(),
            declaration: PlatformPipelineSourceDeclarationV3 {
                kind: ToolchainComponentKindV3::Minecraft,
                roots: Vec::new(),
            },
            derived_checks: PlatformPipelineSourceDerivedChecksV3 {
                fingerprint: "forgegradle-1.19.2-43.4.0".to_owned(),
                tree_cache_path: relative.clone(),
                catalog_projection: None,
                source_jar_sha256: None,
                source_payload_sha256: None,
            },
        });
        let shared_candidate =
            inventory.local_path(Path::new("$sfm-cache/sources/platform/forge/1.19.2/tree"));
        std::fs::create_dir_all(&shared_candidate).expect("separate shared candidate");
        let view = SourceProviderView::new(&inventory, &provider, 0);
        let historical = temporary
            .path()
            .join("checkout/platform/minecraft")
            .join(relative);
        assert_eq!(view.tree_path(), historical);
        assert_eq!(view.status(), SourceStatus::Missing);
        std::fs::create_dir_all(historical.parent().unwrap()).expect("pipeline source parent");
        let mut writer = ZipWriter::new(Cursor::new(Vec::new()));
        writer
            .start_file("src/Example.java", SimpleFileOptions::default())
            .unwrap();
        writer.write_all(b"class Example {}").unwrap();
        let source_jar = historical.with_extension("jar");
        std::fs::write(&source_jar, writer.finish().unwrap().into_inner()).unwrap();
        assert_eq!(view.status(), SourceStatus::Missing);
        extract_zip_atomically(&source_jar, &historical).unwrap();
        assert_eq!(view.status(), SourceStatus::Acquired);
        std::fs::write(historical.join("src/Example.java"), b"changed").unwrap();
        assert_eq!(view.status(), SourceStatus::Missing);
        assert!(view.unavailable_reason().unwrap().contains("differs"));
        std::fs::write(historical.join("src/Example.java"), b"class Example {}").unwrap();
        assert_eq!(view.status(), SourceStatus::Acquired);
        std::fs::write(historical.join("src/extra.java"), b"extra").unwrap();
        assert_eq!(view.status(), SourceStatus::Missing);
        std::fs::remove_file(historical.join("src/extra.java")).unwrap();
        std::fs::remove_file(source_jar).unwrap();
        assert_eq!(view.status(), SourceStatus::Missing);
    }

    #[test]
    fn catalog_platform_source_requires_locked_jar_and_exact_sealed_tree() {
        let temporary = tempfile::tempdir().unwrap();
        let inventory = fixture(CacheHome(temporary.path().join("cache")));
        let portable = PathBuf::from(
            "$sfm-cache/sources/platform/forge/1.19.2/fixture/combined-deobfuscated.filetree",
        );
        let tree = inventory.local_path(&portable);
        assert_eq!(
            tree,
            temporary
                .path()
                .join("cache/minecraft-toolchain/sources/platform/forge/1.19.2/fixture/combined-deobfuscated.filetree")
        );
        std::fs::create_dir_all(tree.parent().unwrap()).unwrap();
        let mut writer = ZipWriter::new(Cursor::new(Vec::new()));
        writer
            .start_file("src/Example.java", SimpleFileOptions::default())
            .unwrap();
        writer.write_all(b"class Example {}").unwrap();
        let bytes = writer.finish().unwrap().into_inner();
        let jar = tree.with_extension("jar");
        let provider = SourceProviderV3::PlatformPipeline(PlatformPipelineSourceProviderV3 {
            id: "minecraft-pipeline".to_owned(),
            declaration: PlatformPipelineSourceDeclarationV3 {
                kind: ToolchainComponentKindV3::Minecraft,
                roots: Vec::new(),
            },
            derived_checks: PlatformPipelineSourceDerivedChecksV3 {
                fingerprint: "fixture".to_owned(),
                tree_cache_path: portable,
                catalog_projection: Some("sfm-dev/mc-1.19.2".to_owned()),
                source_jar_sha256: Some(crate::source_projection::provenance::sha256(&bytes)),
                source_payload_sha256: None,
            },
        });
        let view = SourceProviderView::new(&inventory, &provider, 0);
        assert_eq!(view.status(), SourceStatus::Missing);
        std::fs::write(&jar, &bytes).unwrap();
        assert_eq!(view.status(), SourceStatus::Missing);
        extract_zip_atomically(&jar, &tree).unwrap();
        assert_eq!(view.status(), SourceStatus::Acquired);
        std::fs::write(tree.join("src/Example.java"), b"tampered").unwrap();
        assert_eq!(view.status(), SourceStatus::Missing);
        std::fs::write(tree.join("src/Example.java"), b"class Example {}").unwrap();
        assert_eq!(view.status(), SourceStatus::Acquired);
        std::fs::write(&jar, b"changed archive").unwrap();
        assert_eq!(view.status(), SourceStatus::Missing);
        std::fs::remove_file(&jar).unwrap();
        assert_eq!(view.status(), SourceStatus::Missing);
    }

    #[test]
    fn maven_provider_does_not_advertise_a_modified_extracted_tree() {
        let cache = tempfile::tempdir().expect("temporary cache");
        let inventory = fixture(CacheHome(cache.path().to_path_buf()));
        let mut writer = ZipWriter::new(Cursor::new(Vec::new()));
        writer
            .start_file("src/Example.java", SimpleFileOptions::default())
            .unwrap();
        writer.write_all(b"class Example {}").unwrap();
        let bytes = writer.finish().unwrap().into_inner();
        let hash = ContentHash::from_bytes(&bytes, ContentHashAlgorithm::Blake3);
        let paths = SourceCacheLayout::maven("example:source:1.0:sources", hash);
        let archive = inventory.local_path(&paths.archive);
        std::fs::create_dir_all(archive.parent().unwrap()).unwrap();
        std::fs::write(&archive, bytes).unwrap();
        let tree = inventory.local_path(&paths.tree);
        extract_zip_atomically(&archive, &tree).unwrap();
        let provider = SourceProviderV3::MavenSources(MavenSourceProviderV3 {
            id: "maven-sources".to_owned(),
            declaration: MavenSourceDeclarationV3 {
                requested_coordinate: "example:source:1.0:sources".to_owned(),
                repository_id: "fixture".to_owned(),
                roots: vec!["src".to_owned()],
            },
            derived_checks: MavenSourceDerivedChecksV3 {
                resolved_coordinate: "example:source:1.0:sources".to_owned(),
                url: "https://example.invalid/sources.jar".to_owned(),
                hash,
                archive_cache_path: paths.archive,
                tree_cache_path: paths.tree,
            },
        });
        let view = SourceProviderView::new(&inventory, &provider, 0);
        assert_eq!(view.status(), SourceStatus::Acquired);
        std::fs::write(tree.join("src/Example.java"), b"class Example { altered }").unwrap();
        assert_eq!(view.status(), SourceStatus::Missing);
        assert!(view.unavailable_reason().unwrap().contains("differs"));
    }

    #[test]
    fn git_provider_rejects_unverified_directories_even_with_declared_roots() {
        let cache = tempfile::tempdir().expect("temporary cache");
        let inventory = fixture(CacheHome(cache.path().to_path_buf()));
        let provider = SourceProviderV3::Git(GitSourceProviderV3 {
            id: "upstream".to_owned(),
            declaration: GitSourceDeclarationV3 {
                remote_url: "https://example.invalid/upstream.git".to_owned(),
                requested_revision: "v1".to_owned(),
                roots: vec!["src/main/java".to_owned(), "projects/api/src".to_owned()],
            },
            derived_checks: GitSourceDerivedChecksV3 {
                commit: "0123456789abcdef".to_owned(),
                repository_cache_path: PathBuf::from(
                    "$sfm-cache/sources/git/repositories/test.git",
                ),
                tree_cache_path: PathBuf::from("$sfm-cache/sources/git/trees/test/revision"),
            },
        });
        let view = SourceProviderView::new(&inventory, &provider, 2);

        assert_eq!(view.id(), "upstream");
        assert_eq!(view.kind(), SourceProviderKind::Git);
        assert_eq!(view.priority(), 2);
        assert_eq!(view.status(), SourceStatus::Missing);
        assert_eq!(
            view.unavailable_reason(),
            Some("managed repository and materialized tree are missing")
        );
        assert_eq!(
            view.searchable_roots(),
            vec![
                view.tree_path().join("src/main/java"),
                view.tree_path().join("projects/api/src")
            ]
        );

        let SourceProviderV3::Git(provider) = &provider else {
            unreachable!()
        };
        std::fs::create_dir_all(
            inventory.local_path(&provider.derived_checks.repository_cache_path),
        )
        .expect("repository cache");
        for root in view.searchable_roots() {
            std::fs::create_dir_all(root).expect("searchable root");
        }
        assert_eq!(view.status(), SourceStatus::Missing);
        assert!(
            view.unavailable_reason()
                .unwrap()
                .contains("differs from its locked commit")
        );
    }

    #[test]
    fn decompile_provider_requires_a_matching_completion_fingerprint() {
        let cache = tempfile::tempdir().expect("temporary cache");
        let inventory = fixture(CacheHome(cache.path().to_path_buf()));
        let provider = SourceProviderV3::Decompile(DecompileSourceProviderV3 {
            id: "vineflower".to_owned(),
            declaration: DecompileSourceDeclarationV3 {
                roots: vec!["src".to_owned()],
            },
            derived_checks: DecompileSourceDerivedChecksV3 {
                binary_artifact_id: "binary".to_owned(),
                decompiler_artifact_id: "decompiler".to_owned(),
                fingerprint: "blake3:fixture".to_owned(),
                tree_cache_path: PathBuf::from("$sfm-cache/sources/decompiled/fixture/tree"),
            },
        });
        let view = SourceProviderView::new(&inventory, &provider, 0);
        let tree = view.tree_path();
        std::fs::create_dir_all(tree.join("src")).expect("source root");

        assert_eq!(view.status(), SourceStatus::Missing);
        std::fs::write(tree.join(FINGERPRINT_FILE), "blake3:fixture").expect("completion marker");
        assert_eq!(view.status(), SourceStatus::Acquired);
    }

    fn fixture(cache_home: CacheHome) -> DependencyInventory {
        let input = include_str!("../../../minecraft/sfm-toolchain.lock.json");
        DependencyInventory {
            target: WorktreeTarget {
                branch: BranchName::from("1.19.2"),
                worktree_path: WorktreePath::from(PathBuf::from("fixture")),
                core: true,
                mc_version: None,
            },
            lockfile_path: PathBuf::from("fixture/sfm-toolchain.lock.json"),
            cache_home,
            original_input: input.to_owned(),
            lockfile: read_current(input).expect("v3 fixture"),
        }
    }
}
