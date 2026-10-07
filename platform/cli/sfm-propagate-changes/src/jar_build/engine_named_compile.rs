// Included in the engine namespace after engine_model.rs.

#[cfg(test)]
mod named_forge_compile_cohort_tests {
    use super::*;
    use crate::source_projection::candidate_lock::checked_file;
    use crate::source_projection::catalog_owned_project::tests::Fixture;
    use crate::source_projection::core_catalog::CoreCatalog;
    use crate::source_projection::core_inputs::CORE_METADATA_PATH;
    use crate::source_projection::core_inputs::CORE_ROOT;
    use crate::source_projection::core_inputs::CoreProjectInputs;
    use crate::source_projection::core_inputs::select_core_inputs;
    use crate::source_projection::frozen_recipe_project::prepare_frozen_recipe_project;
    use crate::source_projection::projection_catalog::SUPPORTED_TARGETS;

    #[derive(Facet)]
    struct Review {
        targets: Vec<ReviewRow>,
    }

    #[derive(Facet)]
    struct ReviewRow {
        target: String,
        recipe_id: String,
        source_lock: InputWitness,
        role_input_hashes: Vec<InputWitness>,
    }

    #[derive(Facet)]
    struct InputWitness {
        path: String,
    }

    const REVIEW: &str =
        include_str!("../../../../../docs/tasks/sfm-core-released-native-adapter-review.json");
    const REVIEW_SHA256: &str =
        "sha256:8b927a2b978715a6fc6ae1d0fd828a8a46b729a41a43a54897ca5e6cac313e21";
    const TARGETS: [&str; 4] = ["1.19.2", "1.19.4", "1.20", "1.20.1"];
    fn read_bounded(path: &Path, limit: u64) -> eyre::Result<Vec<u8>> {
        use std::io::Read;
        eyre::ensure!(
            limit > 0 && limit <= 16 * 1024 * 1024,
            "invalid fixture read bound"
        );
        let file = fs::File::open(path)?;
        let metadata = file.metadata()?;
        eyre::ensure!(
            metadata.is_file() && metadata.len() <= limit,
            "fixture exceeds its read bound"
        );
        let mut bytes = Vec::new();
        file.take(limit + 1).read_to_end(&mut bytes)?;
        eyre::ensure!(
            bytes.len() as u64 <= limit,
            "fixture grew beyond its read bound"
        );
        Ok(bytes)
    }
    const TOOL_IDS: [&str; 8] = [
        "tool-installer-tools-1-2",
        "tool-installer-tools-1-3",
        "tool-forgeflower",
        "tool-mergetool-1-1-5",
        "tool-fart",
        "tool-diffpatch",
        "tool-access-transformers",
        "tool-specialsource",
    ];

    // These are independent reviewed consumer goldens, not derived from the
    // production selector or from permissive Maven/POM discovery.
    fn expected_tools(target: &str) -> Vec<&'static str> {
        let (installer, decompiler, merger, renamer) = match target {
            "1.19.2" => (
                "net.minecraftforge:installertools:1.3.0:fatjar",
                "net.minecraftforge:forgeflower:1.5.605.9",
                "net.minecraftforge:mergetool:1.1.5:fatjar",
                "net.minecraftforge:ForgeAutoRenamingTool:0.1.22:all",
            ),
            "1.19.4" => (
                "net.minecraftforge:installertools:1.3.0:fatjar",
                "net.minecraftforge:forgeflower:2.0.627.2",
                "net.minecraftforge:mergetool:1.1.5:fatjar",
                "net.minecraftforge:ForgeAutoRenamingTool:0.1.22:all",
            ),
            "1.20" => (
                "net.minecraftforge:installertools:1.3.2:fatjar",
                "net.minecraftforge:forgeflower:2.0.629.0",
                "net.minecraftforge:mergetool:1.1.7:fatjar",
                "net.minecraftforge:ForgeAutoRenamingTool:1.0.1:all",
            ),
            "1.20.1" => (
                "net.minecraftforge:installertools:1.3.2:fatjar",
                "net.minecraftforge:forgeflower:2.0.629.0",
                "net.minecraftforge:mergetool:1.1.7:fatjar",
                "net.minecraftforge:ForgeAutoRenamingTool:1.0.2:all",
            ),
            _ => panic!("unreviewed test target"),
        };
        vec![
            "net.minecraftforge:installertools:1.2.0:fatjar",
            installer,
            decompiler,
            merger,
            renamer,
            "net.minecraftforge:DiffPatch:2.0.12:all",
            "net.minecraftforge:accesstransformers:8.0.4:fatjar",
            "net.md-5:SpecialSource:1.11.0:shaded",
        ]
    }

    pub(super) fn fixture(target: &str) -> eyre::Result<(Fixture, usize, String, Vec<u8>)> {
        eyre::ensure!(
            crate::source_projection::provenance::sha256(REVIEW.as_bytes()) == REVIEW_SHA256,
            "review golden changed"
        );
        let recipe = facet_json::from_str::<Review>(REVIEW)?
            .targets
            .into_iter()
            .find(|row| row.target == target)
            .ok_or_else(|| eyre::eyre!("reviewed target missing"))?;
        let repository = Path::new(env!("CARGO_MANIFEST_DIR"))
            .ancestors()
            .nth(3)
            .ok_or_else(|| eyre::eyre!("CLI crate must be below repository"))?;
        let catalog = CoreCatalog::load(repository, repository)?;
        let metadata = CoreProjectInputs::from_json(
            std::str::from_utf8(&read_bounded(
                &checked_file(repository, CORE_METADATA_PATH)?,
                8 * 1024 * 1024,
            )?)?,
            &catalog.registered_features,
        )?;
        let context = catalog.context(&format!("sfm-4.34.0/mc-{target}"))?;
        let selection = select_core_inputs(&metadata, &context, &BTreeSet::new())?;
        let mut fixture = Fixture::new();
        let raw_lock = read_bounded(
            &checked_file(repository, &recipe.source_lock.path)?,
            1024 * 1024,
        )?;
        for source_path in std::iter::once(recipe.source_lock.path.as_str())
            .chain(recipe.role_input_hashes.iter().map(|row| row.path.as_str()))
        {
            let core_relative = source_path
                .strip_prefix(&format!("{CORE_ROOT}/"))
                .ok_or_else(|| eyre::eyre!("frozen fixture role is not core owned"))?;
            let selected = selection
                .inputs
                .iter()
                .filter(|(_, input)| input.input == core_relative)
                .collect::<Vec<_>>();
            eyre::ensure!(
                selected.len() == 1 && !selected[0].1.template,
                "frozen fixture role must be one exact-copy selected input"
            );
            let bytes = read_bounded(&checked_file(repository, source_path)?, 1024 * 1024)?;
            fixture.set_project_file_for(target, selected[0].0, core_relative, &bytes, false)?;
        }
        let slot = SUPPORTED_TARGETS
            .iter()
            .position(|(candidate, _)| *candidate == target)
            .ok_or_else(|| eyre::eyre!("fixture slot missing"))?;
        Ok((fixture, slot, recipe.recipe_id, raw_lock))
    }

    pub(super) fn prepared(
        fixture: &Fixture,
        slot: usize,
        environment: &str,
        recipe_id: &str,
    ) -> eyre::Result<crate::source_projection::frozen_recipe_project::FrozenRecipeProject> {
        let key = Fixture::key(slot, environment);
        fixture.publish(&key);
        prepare_frozen_recipe_project(
            fixture.collect(&key)?.check_current()?,
            recipe_id,
            false,
            &BTreeMap::new(),
        )
    }

    #[test]
    fn exact_four_recipe_eight_context_consumer_matrix_preserves_pins_and_loader()
    -> eyre::Result<()> {
        for target in TARGETS {
            let (fixture, slot, recipe_id, raw_lock) = fixture(target)?;
            for environment in ["release", "dev"] {
                let project = prepared(&fixture, slot, environment, &recipe_id)?;
                validate_first_named_compile(&project)?;
                let recipe = named_forge_compile_recipe_for_project(&project)?;
                let functions = BTreeMap::new();
                let requests = named_forge_mcp_tool_coordinates(
                    recipe,
                    Some(recipe.mcp_coordinate),
                    &functions,
                    project.dependencies(),
                )?;
                assert_eq!(
                    requests
                        .iter()
                        .map(|(id, _, _)| id.0.as_str())
                        .collect::<Vec<_>>(),
                    TOOL_IDS,
                );
                assert_eq!(
                    requests
                        .iter()
                        .map(|(_, coordinate, _)| coordinate.to_string())
                        .collect::<Vec<_>>(),
                    expected_tools(target),
                );
                assert_eq!(
                    project.dependencies().raw_source_lock_bytes(),
                    raw_lock,
                    "consumer selection cannot rewrite the original lock",
                );
                let kind = named_forge_loader_kind(recipe);
                assert_eq!(
                    kind,
                    if target == "1.20.1" {
                        LoaderToolchainKind::ForgeGradleNeoForgeGroup
                    } else {
                        LoaderToolchainKind::ForgeGradleForge
                    },
                );
                assert_eq!(
                    project.receipt().ownership.projection_key,
                    Fixture::key(slot, environment),
                );
                project.recheck(false)?;
                assert!(project.recheck(true).is_err());
            }
        }
        Ok(())
    }

    #[test]
    fn declared_mcp_functions_confirm_exact_recipe_without_changing_order() -> eyre::Result<()> {
        for target in TARGETS {
            let (fixture, slot, recipe_id, _) = fixture(target)?;
            let project = prepared(&fixture, slot, "release", &recipe_id)?;
            let recipe = named_forge_compile_recipe_for_project(&project)?;
            let functions = named_forge_mcp_tool_recipes(recipe)
                .into_iter()
                .map(|(_, function, coordinate, _)| (function.to_owned(), coordinate.to_owned()))
                .collect::<BTreeMap<_, _>>();
            let named = named_forge_mcp_tool_coordinates(
                recipe,
                Some(recipe.mcp_coordinate),
                &functions,
                project.dependencies(),
            )?;
            let mcp = McpConfigPlan {
                artifact: ArtifactPlan {
                    id: ArtifactId::from("mcp-config"),
                    coordinate: Some(recipe.mcp_coordinate.to_owned()),
                    repository: None,
                    url: None,
                    cache_path: PathBuf::from("fixture-only/not-read.zip"),
                    sha1: None,
                    downloaded: false,
                    required_for: ArtifactPurpose::from("fixture"),
                    provenance: ArtifactProvenance {
                        schema_version: 1,
                        source: ArtifactSource::RemoteMaven,
                        coordinate: Some(recipe.mcp_coordinate.to_owned()),
                        repository: None,
                        url: None,
                        original_path: None,
                        source_relative_path: None,
                        source_git: None,
                        source_build: None,
                        hash: ContentHash::from_bytes(b"fixture", ContentHashAlgorithm::Blake3),
                    },
                },
                joined_steps: Vec::new(),
                function_coordinates: functions,
                function_count: 8,
                data_keys: Vec::new(),
                library_count: 0,
            };
            let mut legacy = Vec::new();
            add_mcp_tool_coordinates(&mut legacy, Some(&mcp))?;
            assert_eq!(
                named
                    .iter()
                    .map(|(id, coordinate, purpose)| (
                        id.0.as_str(),
                        coordinate.to_string(),
                        purpose.0.as_str()
                    ))
                    .collect::<Vec<_>>(),
                legacy
                    .iter()
                    .map(|(id, coordinate, purpose)| (
                        id.0.as_str(),
                        coordinate.to_string(),
                        purpose.0.as_str()
                    ))
                    .collect::<Vec<_>>(),
                "the original consumer IDs, function choices and order must agree",
            );
        }
        Ok(())
    }

    #[test]
    fn changed_function_refuses_even_when_another_original_pin_has_cached_bytes() -> eyre::Result<()>
    {
        let (fixture, slot, recipe_id, raw_lock) = fixture("1.20")?;
        let project = prepared(&fixture, slot, "release", &recipe_id)?;
        let recipe = named_forge_compile_recipe_for_project(&project)?;
        let cache = tempfile::tempdir()?;
        let cached = cache.path().join("original-other-tool.jar");
        let sentinel = b"existing cache sentinel; no tool bytes are consumed by this test";
        fs::write(&cached, sentinel)?;
        let changed = "net.minecraftforge:installertools:1.2.0:fatjar";
        project.dependencies().coordinate(changed, false)?;
        let functions = BTreeMap::from([("rename".to_owned(), changed.to_owned())]);
        let error = named_forge_mcp_tool_coordinates(
            recipe,
            Some(recipe.mcp_coordinate),
            &functions,
            project.dependencies(),
        )
        .unwrap_err()
        .to_string();
        assert!(error.contains("MCP function rename changed its original coordinate"));
        assert_eq!(fs::read(&cached)?, sentinel);
        assert_eq!(fs::read_dir(cache.path())?.count(), 1);
        assert_eq!(project.dependencies().raw_source_lock_bytes(), raw_lock);
        project.recheck(false)?;
        Ok(())
    }

    #[test]
    fn absent_pin_or_cross_recipe_mcp_refuses_without_fallback() -> eyre::Result<()> {
        let (fixture, slot, recipe_id, _) = fixture("1.19.4")?;
        let project = prepared(&fixture, slot, "release", &recipe_id)?;
        let recipe = named_forge_compile_recipe_for_project(&project)?;
        assert!(
            named_forge_mcp_tool_coordinates(
                recipe,
                Some("de.oceanlabs.mcp:mcp_config:1.19.2-20220805.130853@zip"),
                &BTreeMap::new(),
                project.dependencies(),
            )
            .is_err()
        );
        let mut missing = recipe;
        missing.renamer = "unrecorded:renamer:1";
        assert!(
            named_forge_mcp_tool_coordinates(
                missing,
                Some(recipe.mcp_coordinate),
                &BTreeMap::new(),
                project.dependencies(),
            )
            .is_err()
        );
        project.recheck(false)?;
        Ok(())
    }

    #[test]
    fn catalog_java_directory_spelling_preserves_the_checked_owner() -> eyre::Result<()> {
        let (fixture, slot, recipe, _) = fixture("1.19.2")?;
        let frozen = Arc::new(prepared(&fixture, slot, "release", &recipe)?);
        let original_receipt = frozen.receipt().clone();
        let canonical = frozen.project().project_root().to_path_buf();
        let identity = BuildProjectIdentity::Catalog {
            frozen: Arc::clone(&frozen),
        };
        let java_root = identity.minecraft_dir();
        assert_eq!(fs::canonicalize(&java_root)?, canonical);
        assert_eq!(frozen.receipt(), &original_receipt);
        identity.recheck()?;
        #[cfg(windows)]
        {
            use std::path::Component;
            use std::path::Prefix;
            assert!(
                matches!(
                    canonical.components().next(),
                    Some(Component::Prefix(prefix))
                        if matches!(prefix.kind(), Prefix::VerbatimDisk(_))
                ),
                "the original Windows prefix must exercise the reported failure"
            );
            assert!(matches!(
                java_root.components().next(),
                Some(Component::Prefix(prefix))
                    if matches!(prefix.kind(), Prefix::Disk(_))
            ));
            let output = java_root.join("build").join("mcp").join("output.jar");
            assert!(!output.display().to_string().starts_with(r"\\?\"));
        }
        Ok(())
    }

    #[test]
    fn legacy_worktree_java_directory_is_not_rewritten() {
        let root = PathBuf::from("legacy-worktree");
        let identity = BuildProjectIdentity::Worktree {
            branch: BranchName::from("1.19.2"),
            root: root.clone(),
        };
        assert_eq!(identity.minecraft_dir(), root.join("platform/minecraft"));
    }

    #[test]
    fn unsupported_recipes_and_changed_boundaries_refuse_before_any_cache_or_sdk()
    -> eyre::Result<()> {
        for target in [
            "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1", "26.1.2", "unknown",
        ] {
            assert!(
                named_forge_compile_recipe(
                    target,
                    target,
                    "unreviewed",
                    "neogradle_userdev",
                    "net.neoforged:neoforge:unreviewed",
                    17,
                    17,
                )
                .is_err()
            );
        }
        let valid = [
            (
                "1.19.2",
                "forge_gradle_forge",
                "net.minecraftforge:forge:1.19.2-43.4.0",
            ),
            (
                "1.19.4",
                "forge_gradle_forge",
                "net.minecraftforge:forge:1.19.4-45.0.9",
            ),
            (
                "1.20",
                "forge_gradle_forge",
                "net.minecraftforge:forge:1.20-46.0.10",
            ),
            (
                "1.20.1",
                "forge_gradle_neoforge_group",
                "net.neoforged:forge:1.20.1-47.1.65",
            ),
        ];
        for (target, kind, coordinate) in valid {
            let recipe = format!("sfm:released-native-inputs/4.34.0/{target}@1");
            for (mc, id, loader, pin, release, jvm) in [
                ("different", recipe.as_str(), kind, coordinate, 17, 17),
                (target, "unreviewed", kind, coordinate, 17, 17),
                (
                    target,
                    recipe.as_str(),
                    "neogradle_userdev",
                    coordinate,
                    17,
                    17,
                ),
                (
                    target,
                    recipe.as_str(),
                    kind,
                    "net.minecraftforge:forge:1.19.4-45.0.42",
                    17,
                    17,
                ),
                (target, recipe.as_str(), kind, coordinate, 21, 17),
                (target, recipe.as_str(), kind, coordinate, 17, 21),
            ] {
                assert!(
                    named_forge_compile_recipe(target, mc, id, loader, pin, release, jvm,).is_err()
                );
            }
        }
        Ok(())
    }
}

#[derive(Clone, Debug)]
enum BuildProjectIdentity {
    Worktree {
        branch: BranchName,
        root: PathBuf,
    },
    Catalog {
        frozen: Arc<crate::source_projection::frozen_recipe_project::FrozenRecipeProject>,
    },
    Development {
        project: Arc<crate::source_projection::catalog_owned_project::CatalogOwnedProject>,
        target: Arc<crate::source_projection::native_project_target::NativeProjectTarget>,
    },
}

impl BuildProjectIdentity {
    fn named_project(
        &self,
    ) -> Option<&crate::source_projection::frozen_recipe_project::FrozenRecipeProject> {
        match self {
            Self::Worktree { .. } | Self::Development { .. } => None,
            Self::Catalog { frozen } => Some(frozen),
        }
    }

    fn legacy_branch(&self) -> Option<&BranchName> {
        match self {
            Self::Worktree { branch, .. } => Some(branch),
            Self::Catalog { .. } | Self::Development { .. } => None,
        }
    }

    fn repository_root(&self) -> &Path {
        match self {
            Self::Worktree { root, .. } => root,
            Self::Catalog { frozen } => frozen.project().repo_root(),
            Self::Development { project, .. } => project.repo_root(),
        }
    }

    fn minecraft_dir(&self) -> PathBuf {
        match self {
            Self::Worktree { root, .. } => root.join("platform/minecraft"),
            Self::Catalog { frozen } => {
                // Ownership keeps its canonical path in the frozen handle.
                // Java NIO tools reject Windows verbatim prefixes in arguments;
                // simplify only when dunce proves equivalent Win32 spelling.
                dunce::simplified(frozen.project().project_root()).to_path_buf()
            }
            Self::Development { project, .. } => {
                dunce::simplified(project.project_root()).to_path_buf()
            }
        }
    }

    fn recheck(&self) -> eyre::Result<()> {
        if let Some(project) = self.named_project() {
            project.recheck(false)?;
        }
        if let Self::Development { project, target } = self {
            target.recheck_with_project(project)?;
        }
        Ok(())
    }

    fn is_catalog_owned(&self) -> bool {
        !matches!(self, Self::Worktree { .. })
    }

    fn development_target(
        &self,
    ) -> Option<&crate::source_projection::native_project_target::NativeProjectTarget> {
        match self {
            Self::Development { target, .. } => Some(target),
            Self::Worktree { .. } | Self::Catalog { .. } => None,
        }
    }
}

// Optional wire fields retain the old string representation and ordering when
// Some. Catalog targets never fabricate a BranchName or a worktree-path alias.
#[derive(Clone, Debug, Facet)]
#[facet(transparent)]
struct JsonOptionalBranchName(Option<String>);

impl TryFrom<&Option<BranchName>> for JsonOptionalBranchName {
    type Error = String;
    fn try_from(value: &Option<BranchName>) -> Result<Self, Self::Error> {
        Ok(Self(
            value.as_ref().map(|branch| branch.as_str().to_owned()),
        ))
    }
}

impl TryFrom<JsonOptionalBranchName> for Option<BranchName> {
    type Error = String;
    fn try_from(value: JsonOptionalBranchName) -> Result<Self, Self::Error> {
        Ok(value.0.map(BranchName::from))
    }
}

#[derive(Clone, Debug)]
struct NativePlanningOptions {
    mode: BuildMode,
    java_home: Option<PathBuf>,
    refresh: bool,
    allow_local_artifact_cache: bool,
    artifact_sources: Vec<PathBuf>,
    require_portable_artifacts: bool,
}

impl From<&BuildOptions> for NativePlanningOptions {
    fn from(value: &BuildOptions) -> Self {
        Self {
            mode: value.mode,
            java_home: value.java_home.clone(),
            refresh: value.refresh,
            allow_local_artifact_cache: value.allow_local_artifact_cache,
            artifact_sources: value.artifact_sources.clone(),
            require_portable_artifacts: value.require_portable_artifacts,
        }
    }
}

impl BuildPlan {
    fn named_project(
        &self,
    ) -> Option<&crate::source_projection::frozen_recipe_project::FrozenRecipeProject> {
        self.identity.named_project()
    }

    fn repository_root(&self) -> &Path {
        self.identity.repository_root()
    }

    fn target_label(&self) -> String {
        match &self.identity {
            BuildProjectIdentity::Worktree { branch, .. } => branch.as_str().to_owned(),
            BuildProjectIdentity::Catalog { frozen } => {
                frozen.project().receipt().projection_key.clone()
            }
            BuildProjectIdentity::Development { target, .. } => {
                target.receipt.projection_key.clone()
            }
        }
    }

    fn require_legacy_branch(&self) -> eyre::Result<&BranchName> {
        self.identity.legacy_branch().ok_or_else(|| eyre::eyre!(
            "catalog-owned target currently supports native Compile only; branch launch/JUnit/puppet paths are not authorized"
        ))
    }

    fn recheck_named_inputs(&self) -> eyre::Result<()> {
        self.identity.recheck()
    }

    fn recheck_named_sdk(&self) -> eyre::Result<()> {
        if self.identity.is_catalog_owned() {
            verify_named_sdk_identity(&self.java)?;
        }
        Ok(())
    }

    fn resolver(&self, cancellation: &CancellationToken) -> eyre::Result<Resolver> {
        if let Some(project) = self.named_project() {
            project.recheck(false)?;
            Resolver::new_prepared(
                self.maven_cache_dir.clone(),
                Arc::clone(project.dependencies()),
                false,
                cancellation.clone(),
            )
        } else {
            let resolver = Resolver::new(
                self.maven_cache_dir.clone(),
                self.repositories.clone(),
                self.refresh,
                self.allow_local_artifact_cache,
                self.artifact_sources.clone(),
                self.lockfile.clone(),
                self.lockfile.clone(),
                cancellation.clone(),
            )?;
            if self.identity.development_target().is_some() {
                resolver.require_immutable_catalog()
            } else {
                Ok(resolver)
            }
        }
    }

    fn parsed_dependencies(&self) -> eyre::Result<Vec<ParsedDependency>> {
        if let Some(project) = self.named_project() {
            project.recheck(false)?;
            prepared_parsed_dependencies(project.dependencies())
        } else {
            read_projected_dependencies(&self.lockfile_path)
        }
    }
}

fn prepared_parsed_dependencies(
    inputs: &crate::source_projection::prepared_dependency_inputs::PreparedDependencyInputs,
) -> eyre::Result<Vec<ParsedDependency>> {
    use crate::toolchain_lockfile_schema::version::v3::ArtifactTreatmentV3;
    use crate::toolchain_lockfile_schema::version::v3::BundlePolicyV3;
    use crate::toolchain_lockfile_schema::version::v3::DataRunPolicyV3;
    inputs
        .dependencies()
        .iter()
        .map(|row| {
            Ok(ParsedDependency {
                configuration: row.configuration.clone(),
                // Keep original aliases and order; strict resolver binds the exact
                // resolved coordinate without live metadata or row merging.
                coordinate: MavenCoordinate::parse(&row.requested_coordinate)?,
                bundle: row.bundle.as_ref().map(|value| BundlePolicyV3 {
                    accepted_version_range: value.accepted_version_range.clone(),
                    artifact_version: value.artifact_version.clone(),
                    is_obfuscated: value.is_obfuscated,
                }),
                artifact_treatment: match row.artifact_treatment.as_str() {
                    "loader_managed_mod" => ArtifactTreatmentV3::LoaderManagedMod,
                    "plain" => ArtifactTreatmentV3::Plain,
                    other => eyre::bail!("unknown prepared artifact treatment {other}"),
                },
                data_run_policy: match row.data_run_policy.as_str() {
                    "include" => DataRunPolicyV3::Include,
                    "exclude" => DataRunPolicyV3::Exclude,
                    other => eyre::bail!("unknown prepared data-run policy {other}"),
                },
            })
        })
        .collect()
}

#[derive(Debug)]
pub(crate) struct NamedCompileOptions {
    pub(crate) java_home: PathBuf,
    pub(crate) explain_rebuild: bool,
    pub(crate) wait_for_build_lock: bool,
}

#[derive(Debug, Facet)]
pub(crate) struct NamedCompileReport {
    schema: String,
    scope: String,
    preparation: crate::source_projection::frozen_recipe_project::FrozenRecipeProjectReceipt,
    java_release: u32,
    tool_jvm_major: u32,
    actual_sdk_identity: String,
    #[facet(proxy = JsonPath)]
    cache_dir: PathBuf,
    compilation: String,
    jar_packaging: String,
    execution: String,
    immutable_source_lock: bool,
}

/// First real native compile route. No branch selection, jar packaging or launch.
pub(crate) fn invoke_named_compile(
    project: crate::source_projection::frozen_recipe_project::FrozenRecipeProject,
    options: &NamedCompileOptions,
    cancellation: &CancellationToken,
) -> eyre::Result<NamedCompileReport> {
    validate_first_named_compile(&project)?;
    project.recheck(false)?;
    let identity = BuildProjectIdentity::Catalog {
        frozen: Arc::new(project),
    };
    let planning = NativePlanningOptions {
        mode: BuildMode::Build,
        java_home: Some(options.java_home.clone()),
        refresh: false,
        allow_local_artifact_cache: false,
        artifact_sources: Vec::new(),
        require_portable_artifacts: true,
    };
    let plan = create_plan_for_project(&planning, &identity, cancellation)?;
    plan.recheck_named_inputs()?;
    let artifact = format!("{} native compile cache", plan.target_label());
    let path = build_cache_lock_path(&plan);
    let _lock = if options.wait_for_build_lock {
        ArtifactLock::acquire(&path, artifact)?
    } else {
        ArtifactLock::try_acquire(&path, artifact)?
            .ok_or_else(|| eyre::eyre!("named native compile cache is already locked"))?
    };
    plan.recheck_named_inputs()?;
    write_last_plan_output(&plan)?;
    execute_build(
        &plan,
        options.explain_rebuild,
        BuildTarget::Compile,
        cancellation,
    )?;
    plan.recheck_named_inputs()?;
    let project = plan
        .named_project()
        .ok_or_else(|| eyre::eyre!("named compile lost its checked owner"))?;
    Ok(NamedCompileReport {
        schema: "sfm:named_native_compile@1".to_owned(),
        scope: "real native main/gametest/datagen compile; no jar/launch/release parity claim"
            .to_owned(),
        preparation: project.receipt().clone(),
        java_release: plan.java_release,
        tool_jvm_major: plan.java.major_version,
        actual_sdk_identity: plan.java.cache_identity(),
        cache_dir: plan.cache_dir.clone(),
        compilation: "completed".to_owned(),
        jar_packaging: "not_performed".to_owned(),
        execution: "not_performed".to_owned(),
        immutable_source_lock: true,
    })
}

fn validate_first_named_compile(
    project: &crate::source_projection::frozen_recipe_project::FrozenRecipeProject,
) -> eyre::Result<()> {
    if project.receipt().released_inputs.loader_kind == "neogradle_userdev" {
        return validate_named_neoform_compile(project);
    }
    named_forge_compile_recipe_for_project(project)?;
    Ok(())
}

#[derive(Clone, Copy, Debug)]
struct NamedForgeCompileRecipe {
    target: &'static str,
    loader_kind: &'static str,
    loader_coordinate: &'static str,
    mcp_coordinate: &'static str,
    forgeflower: &'static str,
    installer_bundle: &'static str,
    merger: &'static str,
    renamer: &'static str,
}

fn named_forge_compile_recipe(
    target: &str,
    minecraft_version: &str,
    recipe_id: &str,
    loader_kind: &str,
    loader_coordinate: &str,
    compiler_release: u16,
    tool_jvm_minimum: u16,
) -> eyre::Result<NamedForgeCompileRecipe> {
    let recipe = match target {
        "1.19.2" => NamedForgeCompileRecipe {
            target: "1.19.2",
            loader_kind: "forge_gradle_forge",
            loader_coordinate: "net.minecraftforge:forge:1.19.2-43.4.0",
            mcp_coordinate: "de.oceanlabs.mcp:mcp_config:1.19.2-20220805.130853@zip",
            forgeflower: "net.minecraftforge:forgeflower:1.5.605.9",
            installer_bundle: "net.minecraftforge:installertools:1.3.0:fatjar",
            merger: "net.minecraftforge:mergetool:1.1.5:fatjar",
            renamer: "net.minecraftforge:ForgeAutoRenamingTool:0.1.22:all",
        },
        "1.19.4" => NamedForgeCompileRecipe {
            target: "1.19.4",
            loader_kind: "forge_gradle_forge",
            loader_coordinate: "net.minecraftforge:forge:1.19.4-45.0.9",
            mcp_coordinate: "de.oceanlabs.mcp:mcp_config:1.19.4-20230314.122934@zip",
            forgeflower: "net.minecraftforge:forgeflower:2.0.627.2",
            installer_bundle: "net.minecraftforge:installertools:1.3.0:fatjar",
            merger: "net.minecraftforge:mergetool:1.1.5:fatjar",
            renamer: "net.minecraftforge:ForgeAutoRenamingTool:0.1.22:all",
        },
        "1.20" => NamedForgeCompileRecipe {
            target: "1.20",
            loader_kind: "forge_gradle_forge",
            loader_coordinate: "net.minecraftforge:forge:1.20-46.0.10",
            mcp_coordinate: "de.oceanlabs.mcp:mcp_config:1.20-20230608.053357@zip",
            forgeflower: "net.minecraftforge:forgeflower:2.0.629.0",
            installer_bundle: "net.minecraftforge:installertools:1.3.2:fatjar",
            merger: "net.minecraftforge:mergetool:1.1.7:fatjar",
            renamer: "net.minecraftforge:ForgeAutoRenamingTool:1.0.1:all",
        },
        "1.20.1" => NamedForgeCompileRecipe {
            target: "1.20.1",
            loader_kind: "forge_gradle_neoforge_group",
            loader_coordinate: "net.neoforged:forge:1.20.1-47.1.65",
            mcp_coordinate: "de.oceanlabs.mcp:mcp_config:1.20.1-20230612.114412@zip",
            forgeflower: "net.minecraftforge:forgeflower:2.0.629.0",
            installer_bundle: "net.minecraftforge:installertools:1.3.2:fatjar",
            merger: "net.minecraftforge:mergetool:1.1.7:fatjar",
            renamer: "net.minecraftforge:ForgeAutoRenamingTool:1.0.2:all",
        },
        _ => eyre::bail!(
            "named native Compile currently supports only the exact released Forge-family recipes for 1.19.2, 1.19.4, 1.20 and 1.20.1; NeoForm, packaging, JUnit and launch remain separate gates"
        ),
    };
    eyre::ensure!(
        minecraft_version == recipe.target
            && recipe_id == format!("sfm:released-native-inputs/4.34.0/{}@1", recipe.target)
            && loader_kind == recipe.loader_kind
            && loader_coordinate == recipe.loader_coordinate
            && compiler_release == 17
            && tool_jvm_minimum == 17,
        "named Forge-family Compile must match its original released recipe, loader coordinate and compiler/tool boundary"
    );
    Ok(recipe)
}

fn named_forge_compile_recipe_for_project(
    project: &crate::source_projection::frozen_recipe_project::FrozenRecipeProject,
) -> eyre::Result<NamedForgeCompileRecipe> {
    let receipt = project.receipt();
    let released = &receipt.released_inputs;
    eyre::ensure!(
        receipt.ownership.target_id == released.target_id
            && receipt.ownership.minecraft_version == released.minecraft_version,
        "named Forge-family Compile lost its exact catalog/recipe target identity"
    );
    let recipe = named_forge_compile_recipe(
        &receipt.ownership.target_id,
        &receipt.ownership.minecraft_version,
        &released.recipe_id,
        &released.loader_kind,
        &released.loader_coordinate,
        receipt.compiler_release,
        receipt.tool_jvm_minimum,
    )?;
    // Validate all original coordinates before cache creation or acquisition.
    // This does not substitute a missing pin with a legacy 1.19.2 default.
    let inputs = project.dependencies();
    // The base loader coordinate is the recipe identity, not a bare JAR request.
    // The original locks pin its three actual component artifacts instead.
    for classifier in ["userdev", "sources", "universal"] {
        inputs.coordinate(&format!("{}:{classifier}", recipe.loader_coordinate), false)?;
    }
    inputs.coordinate(recipe.mcp_coordinate, false)?;
    for (_, _, coordinate, _) in named_forge_mcp_tool_recipes(recipe) {
        inputs.coordinate(coordinate, false)?;
    }
    Ok(recipe)
}

fn named_forge_loader_kind(recipe: NamedForgeCompileRecipe) -> LoaderToolchainKind {
    if recipe.target == "1.20.1" {
        LoaderToolchainKind::ForgeGradleNeoForgeGroup
    } else {
        LoaderToolchainKind::ForgeGradleForge
    }
}

// Keep the existing consumer order and IDs. The explicit per-recipe inputs are
// all original locked coordinates; MCP declarations may confirm them, never
// silently select an alternative coordinate or a newer fallback.
fn named_forge_mcp_tool_recipes(
    recipe: NamedForgeCompileRecipe,
) -> [(&'static str, &'static str, &'static str, &'static str); 8] {
    [
        (
            "tool-installer-tools-1-2",
            "mergeMappings",
            "net.minecraftforge:installertools:1.2.0:fatjar",
            "MCPConfig MERGE_MAPPING function",
        ),
        (
            "tool-installer-tools-1-3",
            "bundleExtractJar",
            recipe.installer_bundle,
            "MCPConfig server bundle extraction",
        ),
        (
            "tool-forgeflower",
            "decompile",
            recipe.forgeflower,
            "MCPConfig decompile function",
        ),
        (
            "tool-mergetool-1-1-5",
            "merge",
            recipe.merger,
            "MCPConfig client/server merge function",
        ),
        (
            "tool-fart",
            "rename",
            recipe.renamer,
            "MCPConfig rename and jar remapping",
        ),
        (
            "tool-diffpatch",
            "patch",
            "net.minecraftforge:DiffPatch:2.0.12:all",
            "MCPConfig and Forge source patch application",
        ),
        (
            "tool-access-transformers",
            "accessTransformers",
            "net.minecraftforge:accesstransformers:8.0.4:fatjar",
            "Forge access transformer application",
        ),
        (
            "tool-specialsource",
            "reobfuscate",
            "net.md-5:SpecialSource:1.11.0:shaded",
            "Forge-style jar reobfuscation",
        ),
    ]
}

fn named_forge_mcp_tool_coordinates(
    recipe: NamedForgeCompileRecipe,
    mcp_coordinate: Option<&str>,
    functions: &BTreeMap<String, String>,
    inputs: &crate::source_projection::prepared_dependency_inputs::PreparedDependencyInputs,
) -> eyre::Result<Vec<(ArtifactId, MavenCoordinate, ArtifactPurpose)>> {
    eyre::ensure!(
        inputs.receipt().target_id == recipe.target
            && inputs.receipt().minecraft_version == recipe.target
            && inputs.receipt().recipe_id
                == format!("sfm:released-native-inputs/4.34.0/{}@1", recipe.target)
            && mcp_coordinate == Some(recipe.mcp_coordinate),
        "named Forge MCP consumers must belong to the exact prepared target recipe and original MCP pin"
    );
    inputs.coordinate(recipe.mcp_coordinate, false)?;
    let mut result = Vec::new();
    for (id, function, expected, purpose) in named_forge_mcp_tool_recipes(recipe) {
        if let Some(recorded) = functions.get(function) {
            eyre::ensure!(
                recorded == expected,
                "named Forge MCP function {function} changed its original coordinate: expected {expected}, got {recorded}; cached bytes cannot authorize a substitute"
            );
        }
        inputs.coordinate(expected, false)?;
        result.push((
            ArtifactId::from(id),
            MavenCoordinate::parse(expected)?,
            ArtifactPurpose::from(purpose),
        ));
    }
    Ok(result)
}

fn add_named_forge_mcp_tool_coordinates(
    coordinates: &mut Vec<(ArtifactId, MavenCoordinate, ArtifactPurpose)>,
    project: &crate::source_projection::frozen_recipe_project::FrozenRecipeProject,
    mcp_config: Option<&McpConfigPlan>,
) -> eyre::Result<()> {
    let recipe = named_forge_compile_recipe_for_project(project)?;
    let mcp = mcp_config
        .ok_or_else(|| eyre::eyre!("named Forge-family Compile requires its original MCPConfig"))?;
    coordinates.extend(named_forge_mcp_tool_coordinates(
        recipe,
        mcp.artifact.coordinate.as_deref(),
        &mcp.function_coordinates,
        project.dependencies(),
    )?);
    Ok(())
}

fn named_sdk_identity(java: &JavaPlan) -> eyre::Result<String> {
    let home = java
        .home
        .as_deref()
        .ok_or_else(|| eyre::eyre!("named compile requires an explicit verified SDK home"))?;
    let javac = home
        .join("bin")
        .join(if cfg!(windows) { "javac.exe" } else { "javac" });
    let mut evidence = format!(
        "sfm:native_sdk_identity@1\n{}\n{}\n",
        java.version_output,
        home.display()
    );
    for path in [
        &java.executable,
        &javac,
        &home.join("release"),
        &home.join("lib/modules"),
    ] {
        require_named_regular_file(path, &fs::symlink_metadata(path)?)?;
        let hash = ContentHash::from_path(path, ContentHashAlgorithm::Blake3)?;
        writeln!(evidence, "{}={hash}", path.display())?;
    }
    Ok(crate::source_projection::provenance::sha256(
        evidence.as_bytes(),
    ))
}

fn verify_named_sdk_identity(java: &JavaPlan) -> eyre::Result<()> {
    let captured = java
        .execution_identity
        .as_deref()
        .ok_or_else(|| eyre::eyre!("named SDK identity was not captured"))?;
    eyre::ensure!(
        named_sdk_identity(java)? == captured,
        "named SDK bytes changed after preparation; refusing cache reuse or subprocess without rekeying"
    );
    Ok(())
}

fn resolve_frozen_minecraft_plan(
    project: &crate::source_projection::frozen_recipe_project::FrozenRecipeProject,
    minecraft_cache: &Path,
    client: &Client,
    cancellation: &CancellationToken,
) -> eyre::Result<MinecraftPlan> {
    use authenticated_minecraft_inputs::MinecraftCompileDownload as Role;

    project.recheck(false)?;
    let receipt = &project.receipt().released_inputs;
    let hash = ContentHash::parse(&receipt.direct_version_json_hash)
        .map_err(|error| eyre::eyre!(error))?;
    let path = minecraft_cache
        .join("versions")
        .join(&receipt.minecraft_version)
        .join("version.json");
    let raw = acquire_named_frozen_download(
        cancellation,
        client,
        &receipt.direct_version_json_url,
        &path,
        &hash,
        16 * 1024 * 1024,
        None,
        || project.recheck(false),
    )?;
    let inputs = authenticated_minecraft_inputs::authenticate_version_json(project, &raw)?;
    let (client_mappings_url, server_mappings_url) = inputs.compile_mapping_urls()?;
    let artifact = plain_artifact(
        ArtifactId::from("minecraft-version-json"),
        &receipt.direct_version_json_url,
        path,
        ArtifactPurpose::from("exact frozen Minecraft libraries and downloads"),
    )?;
    Ok(MinecraftPlan {
        version_manifest: None,
        version_json: artifact,
        client_jar_url: inputs.download(Role::ClientJar)?.url().to_owned(),
        server_jar_url: inputs.download(Role::ServerJar)?.url().to_owned(),
        client_mappings_url,
        server_mappings_url,
        libraries_count: inputs.libraries().count(),
        authenticated_inputs: Some(inputs),
    })
}

fn download_compile_child(
    context: &ExecutionContext<'_>,
    client: &Client,
    role: authenticated_minecraft_inputs::MinecraftCompileDownload,
    path: &Path,
) -> eyre::Result<()> {
    use authenticated_minecraft_inputs::MinecraftCompileDownload as Role;

    if let Some(inputs) = &context.plan.minecraft.authenticated_inputs {
        context.plan.recheck_named_inputs()?;
        let request = inputs.download(role)?;
        return acquire_authenticated_child(context, client, inputs, request, path);
    }
    let url = match role {
        Role::ClientJar => &context.plan.minecraft.client_jar_url,
        Role::ServerJar => &context.plan.minecraft.server_jar_url,
        Role::ClientMappings => required_minecraft_mapping_url(
            context,
            context.plan.minecraft.client_mappings_url.as_deref(),
            "client",
        )?,
        Role::ServerMappings => required_minecraft_mapping_url(
            context,
            context.plan.minecraft.server_mappings_url.as_deref(),
            "server",
        )?,
    };
    download_to_path(&context.cancellation_token, client, url, path)
}

fn acquire_authenticated_child(
    context: &ExecutionContext<'_>,
    client: &Client,
    inputs: &authenticated_minecraft_inputs::AuthenticatedMinecraftInputs,
    request: &authenticated_minecraft_inputs::AuthenticatedMinecraftChild,
    path: &Path,
) -> eyre::Result<()> {
    context.plan.recheck_named_inputs()?;
    acquire_authenticated_child_bytes(context, client, inputs, request, path)
}

// Cached library batches bracket this helper with exact-project checks. Every
// cache miss still rechecks the real owner immediately before each effect.
fn acquire_authenticated_child_bytes(
    context: &ExecutionContext<'_>,
    client: &Client,
    inputs: &authenticated_minecraft_inputs::AuthenticatedMinecraftInputs,
    request: &authenticated_minecraft_inputs::AuthenticatedMinecraftChild,
    path: &Path,
) -> eyre::Result<()> {
    let raw = acquire_named_frozen_download(
        &context.cancellation_token,
        client,
        request.url(),
        path,
        request.expected_hash(),
        request.expected_bytes(),
        Some(request.expected_bytes()),
        || context.plan.recheck_named_inputs(),
    )?;
    inputs.verify_child_bytes(request, &raw)?;
    context.assert_allowed_input(path)?;
    Ok(())
}

fn read_named_authenticated_file(path: &Path, maximum: u64) -> eyre::Result<Vec<u8>> {
    let mut raw = Vec::new();
    File::open(path)?
        .take(
            maximum
                .checked_add(1)
                .ok_or_else(|| eyre::eyre!("authenticated byte limit overflow"))?,
        )
        .read_to_end(&mut raw)?;
    eyre::ensure!(
        u64::try_from(raw.len())? <= maximum,
        "authenticated input exceeds its bounded byte limit: {}",
        path.display()
    );
    Ok(raw)
}

pub(crate) fn prepare_named_frozen_recipe_project(
    project: crate::source_projection::catalog_owned_project::CatalogOwnedProject,
    recipe: &str,
) -> eyre::Result<crate::source_projection::frozen_recipe_project::FrozenRecipeProject> {
    crate::source_projection::frozen_recipe_project::prepare_frozen_recipe_project_with_witness_loader(
        project,
        recipe,
        false,
        |requirements| {
            load_named_exact_byte_witnesses(
                requirements,
                &common_toolchain_cache_dir().join("maven"),
            )
        },
    )
}

fn load_named_exact_byte_witnesses(
    requirements: &[crate::source_projection::released_native_inputs::ReleasedExactByteRequirement],
    maven_cache: &Path,
) -> eyre::Result<BTreeMap<usize, Vec<u8>>> {
    // Read-only canonical cache inputs, never caller-provided artifact paths,
    // source-build discovery, downloads, weak metadata or substituted versions.
    const MAXIMUM: u64 = 128 * 1024 * 1024;
    let mut witnesses = BTreeMap::new();
    let mut total = 0u64;
    if requirements.is_empty() {
        return Ok(witnesses);
    }
    let cache_root = crate::source_projection::candidate_lock::checked_directory(maven_cache)?;
    for requirement in requirements {
        let coordinate = MavenCoordinate::parse(&requirement.coordinate)?;
        let path = maven_cache_path_for(&cache_root, &coordinate);
        let relative = path
            .strip_prefix(&cache_root)?
            .to_str()
            .ok_or_else(|| eyre::eyre!("non-UTF-8 frozen witness coordinate path"))?
            .replace('\\', "/");
        let checked =
            crate::source_projection::candidate_lock::checked_file(&cache_root, &relative)
                .wrap_err_with(|| {
                    format!(
                        "missing or unsafe canonical exact-byte witness for {}",
                        requirement.coordinate
                    )
                })?;
        let bytes = read_named_authenticated_file(&checked, MAXIMUM - total)?;
        total += u64::try_from(bytes.len())?;
        eyre::ensure!(
            witnesses
                .insert(requirement.artifact_index, bytes)
                .is_none(),
            "duplicate frozen exact-byte requirement"
        );
    }
    Ok(witnesses)
}

/// Exact missing-only acquisition. Existing corrupt bytes refuse under lock
/// without quarantine, repair, replacement or .bad sibling cleanup.
#[expect(
    clippy::too_many_arguments,
    reason = "The missing-effect callback retains the exact owner before acquisition and publication."
)]
fn acquire_named_frozen_download(
    cancellation: &CancellationToken,
    client: &Client,
    url: &str,
    path: &Path,
    expected: &ContentHash,
    maximum: u64,
    exact_size: Option<u64>,
    before_missing_effect: impl Fn() -> eyre::Result<()>,
) -> eyre::Result<Vec<u8>> {
    acquire_named_frozen_download_verified(
        cancellation,
        client,
        url,
        path,
        maximum,
        |bytes| verify_named_download_bytes(bytes, expected, exact_size),
        before_missing_effect,
    )
}

// Keep complete SHA-256 supplement pins outside the legacy 20-byte ContentHash
// representation. Both algorithms use this same locked, create-only path.
fn acquire_named_frozen_download_verified(
    cancellation: &CancellationToken,
    client: &Client,
    url: &str,
    path: &Path,
    maximum: u64,
    verify: impl Fn(&[u8]) -> eyre::Result<()>,
    before_missing_effect: impl Fn() -> eyre::Result<()>,
) -> eyre::Result<Vec<u8>> {
    cancellation.bail_if_cancelled()?;
    let _lock = acquire_artifact_path_lock_cancellable(path, cancellation)?;
    match fs::symlink_metadata(path) {
        Ok(metadata) => {
            require_named_regular_file(path, &metadata)?;
            let bytes = read_named_authenticated_file(path, maximum)?;
            verify(&bytes)?;
            return Ok(bytes);
        }
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
        Err(error) => return Err(error.into()),
    }
    cancellation.bail_if_cancelled()?;
    before_missing_effect()?;
    cancellation.bail_if_cancelled()?;
    let mut response = client
        .get(url)
        .send()
        .wrap_err_with(|| format!("failed exact named request {url}"))?;
    cancellation.bail_if_cancelled()?;
    eyre::ensure!(
        response.status().is_success(),
        "exact named request failed: HTTP {}",
        response.status()
    );
    eyre::ensure!(
        response.content_length().is_none_or(|size| size <= maximum),
        "exact named response exceeds byte limit"
    );
    let mut bytes = Vec::new();
    (&mut response)
        .take(
            maximum
                .checked_add(1)
                .ok_or_else(|| eyre::eyre!("named response limit overflow"))?,
        )
        .read_to_end(&mut bytes)?;
    cancellation.bail_if_cancelled()?;
    eyre::ensure!(
        u64::try_from(bytes.len())? <= maximum,
        "exact named response exceeds byte limit"
    );
    verify(&bytes)?;
    cancellation.bail_if_cancelled()?;
    before_missing_effect()?;
    cancellation.bail_if_cancelled()?;
    let temporary = write_unique_temp_file(path, &bytes)?;
    // Atomic create-if-missing cannot overwrite a non-cooperating writer.
    // A failed link leaves only our recovery temporary; no prior file is moved.
    cancellation.bail_if_cancelled()?;
    before_missing_effect()?;
    cancellation.bail_if_cancelled()?;
    fs::hard_link(&temporary, path)
        .wrap_err("named destination appeared or cannot be published without replacement")?;
    fs::remove_file(&temporary)?;
    Ok(bytes)
}

fn verify_named_download_bytes(
    bytes: &[u8],
    expected: &ContentHash,
    exact_size: Option<u64>,
) -> eyre::Result<()> {
    eyre::ensure!(
        exact_size.is_none_or(|size| u64::try_from(bytes.len()).ok() == Some(size)),
        "named frozen cache size mismatch; no repair performed"
    );
    eyre::ensure!(
        ContentHash::from_bytes(bytes, expected.algorithm) == *expected,
        "named frozen cache hash mismatch; no repair performed"
    );
    Ok(())
}

fn require_named_regular_file(path: &Path, metadata: &fs::Metadata) -> eyre::Result<()> {
    #[cfg(windows)]
    use std::os::windows::fs::MetadataExt as _;

    eyre::ensure!(
        metadata.is_file() && !metadata.file_type().is_symlink(),
        "named input is not a regular file: {}",
        path.display()
    );
    #[cfg(windows)]
    eyre::ensure!(
        metadata.file_attributes() & 0x400 == 0,
        "named input is a reparse point: {}",
        path.display()
    );
    Ok(())
}

#[expect(
    clippy::case_sensitive_file_extension_comparisons,
    reason = "Frozen grammar paths and role membership require exact case, not aliases."
)]
fn first_named_compile_grammars<'a>(
    owned_paths: impl IntoIterator<Item = &'a str>,
) -> eyre::Result<Vec<&'a str>> {
    let paths = owned_paths
        .into_iter()
        .filter(|path| path.starts_with("src/") && path.ends_with(".g4"))
        .collect::<BTreeSet<_>>();
    let baseline = [
        "src/main/antlr/sfml/SFML.g4",
        "src/main/antlr/toml/TomlLexer.g4",
        "src/main/antlr/toml/TomlParser.g4",
    ];
    let pair = [
        "src/main/antlr/antlr4/ANTLRv4Lexer.g4",
        "src/main/antlr/antlr4/ANTLRv4Parser.g4",
    ];
    eyre::ensure!(
        baseline.iter().all(|path| paths.contains(path)),
        "first named recipe lacks required baseline grammars"
    );
    eyre::ensure!(
        paths.contains(pair[0]) == paths.contains(pair[1]),
        "first named recipe requires the complete optional ANTLRv4 pair"
    );
    eyre::ensure!(
        paths
            .iter()
            .all(|path| baseline.contains(path) || pair.contains(path)),
        "first named recipe contains an unsupported owned grammar"
    );
    let mut result = baseline.into_iter().collect::<Vec<_>>();
    if paths.contains(pair[0]) {
        result.extend(pair);
    }
    Ok(result)
}

#[cfg(test)]
mod named_compile_wire_tests {
    use super::*;

    #[test]
    #[ignore = "requires explicit opt-in to the existing canonical frozen 26.1.2 artifact; no acquisition"]
    fn named_canonical_26_witness_prepares_exact_frozen_project() -> eyre::Result<()> {
        eyre::ensure!(
            std::env::var("SFM_VERIFY_CANONICAL_RELEASED_26_WITNESS").as_deref() == Ok("1"),
            "explicit canonical witness verification opt-in required"
        );
        let (_fixture, project) =
            super::super::nfrt_launch_contract::tests::project_fixture_with_witness_loader(
                "26.1.2",
                "release",
                |requirements| {
                    eyre::ensure!(
                        requirements.len() == 1
                            && requirements[0].artifact_index == 18
                            && requirements[0].coordinate == "mekanism:Mekanism:26.1.2-10.8.0.86"
                            && requirements[0].original_content_hash
                                == "blake3:fa5d96536fb3195b1a113d1d9e30695927d76378",
                        "original 26 witness boundary changed"
                    );
                    load_named_exact_byte_witnesses(
                        requirements,
                        &common_toolchain_cache_dir().join("maven"),
                    )
                },
            )?;
        validate_named_neoform_compile(&project)?;
        validate_named_package_project(&project)?;
        project.recheck(false)?;
        assert!(
            project
                .receipt()
                .released_inputs
                .exact_bytes_verified
                .contains_key(&18)
        );
        assert_eq!(project.receipt().native_compilation, "not_performed");
        Ok(())
    }

    #[test]
    fn named_exact_witness_loader_is_canonical_read_only_and_missing_closed() -> eyre::Result<()> {
        use crate::source_projection::released_native_inputs::ReleasedExactByteRequirement;
        let directory = tempfile::tempdir()?;
        let absent = directory.path().join("absent-cache");
        assert!(load_named_exact_byte_witnesses(&[], &absent)?.is_empty());
        assert!(!absent.exists());
        let requirement = ReleasedExactByteRequirement {
            artifact_index: 18,
            coordinate: "fixture:artifact:1".to_owned(),
            original_content_hash: "not-verified-by-the-IO-loader".to_owned(),
            source_commit: "fixture-source-commit".to_owned(),
            source_build_sha256: "fixture-source-build".to_owned(),
        };
        assert!(
            load_named_exact_byte_witnesses(std::slice::from_ref(&requirement), directory.path())
                .is_err()
        );
        let canonical = maven_cache_path_for(
            directory.path(),
            &MavenCoordinate::parse(&requirement.coordinate)?,
        );
        fs::create_dir_all(canonical.parent().unwrap())?;
        fs::write(&canonical, b"exact fixture bytes")?;
        let witnesses =
            load_named_exact_byte_witnesses(std::slice::from_ref(&requirement), directory.path())?;
        assert_eq!(witnesses[&18], b"exact fixture bytes");
        assert_eq!(fs::read(&canonical)?, b"exact fixture bytes");
        let mut other = requirement.clone();
        other.coordinate = "fixture:artifact:2".to_owned();
        assert!(load_named_exact_byte_witnesses(&[other], directory.path()).is_err());
        assert!(
            load_named_exact_byte_witnesses(&[requirement.clone(), requirement], directory.path())
                .is_err()
        );
        Ok(())
    }

    #[derive(Facet)]
    struct LegacyIdentityWire {
        schema_version: u32,
        #[facet(proxy = JsonBranchName)]
        branch_name: BranchName,
        #[facet(proxy = JsonPath)]
        worktree_path: PathBuf,
        #[facet(proxy = JsonPath)]
        minecraft_dir: PathBuf,
    }

    #[derive(Facet)]
    struct AdditiveIdentityWire {
        schema_version: u32,
        #[facet(proxy = JsonOptionalBranchName, skip_unless_truthy)]
        branch_name: Option<BranchName>,
        #[facet(proxy = JsonOptionalPath, skip_unless_truthy)]
        worktree_path: Option<PathBuf>,
        #[facet(proxy = JsonPath)]
        minecraft_dir: PathBuf,
        #[facet(skip_unless_truthy)]
        catalog_project: Option<String>,
    }

    #[test]
    fn named_compile_wire_preserves_legacy_identity_bytes_and_order() -> eyre::Result<()> {
        let legacy = LegacyIdentityWire {
            schema_version: 1,
            branch_name: BranchName::from("1.19.2"),
            worktree_path: PathBuf::from("example/repository"),
            minecraft_dir: PathBuf::from("example/repository/platform/minecraft"),
        };
        let additive = AdditiveIdentityWire {
            schema_version: legacy.schema_version,
            branch_name: Some(legacy.branch_name.clone()),
            worktree_path: Some(legacy.worktree_path.clone()),
            minecraft_dir: legacy.minecraft_dir.clone(),
            catalog_project: None,
        };
        assert_eq!(
            facet_json::to_string(&legacy)?,
            facet_json::to_string(&additive)?
        );
        Ok(())
    }

    #[test]
    fn named_compile_wire_has_no_fabricated_branch_or_worktree() -> eyre::Result<()> {
        let wire = AdditiveIdentityWire {
            schema_version: 2,
            branch_name: None,
            worktree_path: None,
            minecraft_dir: PathBuf::from("projections/arbitrary/nested/project"),
            catalog_project: Some("exact-owned-receipt".to_owned()),
        };
        let json = facet_json::to_string(&wire)?;
        assert!(!json.contains("branch_name"));
        assert!(!json.contains("worktree_path"));
        assert!(json.contains("catalog_project"));
        Ok(())
    }

    #[test]
    fn named_compile_authenticated_file_reads_are_bounded() -> eyre::Result<()> {
        let directory = tempfile::tempdir()?;
        let path = directory.path().join("version.json");
        fs::write(&path, b"bounded bytes")?;
        assert_eq!(read_named_authenticated_file(&path, 13)?, b"bounded bytes");
        assert!(read_named_authenticated_file(&path, 12).is_err());
        assert!(read_named_authenticated_file(&path, u64::MAX).is_err());
        Ok(())
    }

    #[test]
    fn named_compile_grammar_selection_requires_baseline_and_complete_optional_pair()
    -> eyre::Result<()> {
        let baseline = [
            "src/main/antlr/sfml/SFML.g4",
            "src/main/antlr/toml/TomlLexer.g4",
            "src/main/antlr/toml/TomlParser.g4",
        ];
        assert_eq!(first_named_compile_grammars(baseline)?, baseline);
        let mut complete = baseline.to_vec();
        complete.extend([
            "src/main/antlr/antlr4/ANTLRv4Lexer.g4",
            "src/main/antlr/antlr4/ANTLRv4Parser.g4",
        ]);
        assert_eq!(
            first_named_compile_grammars(complete.iter().copied())?,
            complete
        );
        assert!(first_named_compile_grammars(baseline.iter().take(2).copied()).is_err());
        complete.pop();
        assert!(first_named_compile_grammars(complete).is_err());
        let mut unexpected = baseline.to_vec();
        unexpected.push("src/main/antlr/new/Unreviewed.g4");
        assert!(first_named_compile_grammars(unexpected).is_err());
        Ok(())
    }

    #[test]
    fn named_compile_corrupt_existing_parent_and_child_refuse_without_repair() -> eyre::Result<()> {
        let directory = tempfile::tempdir()?;
        let client = Client::builder().build()?;
        let token = CancellationToken::new();
        for algorithm in [ContentHashAlgorithm::Blake3, ContentHashAlgorithm::Sha1] {
            let path = directory.path().join(format!("input-{algorithm}.bin"));
            let bad_sibling = path.with_extension("bin.bad.kept");
            let bytes = b"corrupted";
            fs::write(&path, bytes)?;
            fs::write(&bad_sibling, b"prior recovery")?;
            let expected = ContentHash::from_bytes(b"expected!", algorithm);
            assert!(
                acquire_named_frozen_download(
                    &token,
                    &client,
                    "https://never-contact.invalid/input",
                    &path,
                    &expected,
                    64,
                    Some(9),
                    || eyre::bail!("fixture forbids missing-file acquisition effects"),
                )
                .is_err()
            );
            assert_eq!(fs::read(&path)?, bytes);
            assert_eq!(fs::read(&bad_sibling)?, b"prior recovery");
            let files = fs::read_dir(directory.path())?
                .map(|entry| entry.map(|item| item.path()))
                .collect::<std::io::Result<Vec<_>>>()?;
            assert!(
                files
                    .iter()
                    .filter(|file| file
                        .file_name()
                        .is_some_and(|name| name.to_string_lossy().contains(".bad")))
                    .all(|file| file
                        .extension()
                        .is_some_and(|extension| extension == "kept"))
            );
        }
        Ok(())
    }

    #[test]
    fn named_compile_valid_existing_bytes_are_used_without_contacting_url() -> eyre::Result<()> {
        let directory = tempfile::tempdir()?;
        let path = directory.path().join("valid.bin");
        let bytes = b"verified existing bytes";
        fs::write(&path, bytes)?;
        let hash = ContentHash::from_bytes(bytes, ContentHashAlgorithm::Sha1);
        let actual = acquire_named_frozen_download(
            &CancellationToken::new(),
            &Client::builder().build()?,
            "https://never-contact.invalid/input",
            &path,
            &hash,
            64,
            Some(u64::try_from(bytes.len())?),
            || eyre::bail!("fixture forbids missing-file acquisition effects"),
        )?;
        assert_eq!(actual, bytes);
        assert_eq!(fs::read(&path)?, bytes);
        Ok(())
    }

    #[test]
    fn named_compile_sdk_rechecks_refuse_each_changed_execution_input_without_rekey()
    -> eyre::Result<()> {
        let directory = tempfile::tempdir()?;
        let home = directory.path();
        fs::create_dir_all(home.join("bin"))?;
        fs::create_dir_all(home.join("lib"))?;
        let java_path = home
            .join("bin")
            .join(if cfg!(windows) { "java.exe" } else { "java" });
        let javac_path = home
            .join("bin")
            .join(if cfg!(windows) { "javac.exe" } else { "javac" });
        let paths = [
            java_path.clone(),
            javac_path,
            home.join("release"),
            home.join("lib/modules"),
        ];
        for path in &paths {
            fs::write(path, b"original")?;
        }
        let mut java = JavaPlan {
            executable: java_path,
            home: Some(home.to_path_buf()),
            version_output: "fixture Java 17".to_owned(),
            major_version: 17,
            selection: "explicit".to_owned(),
            pin_url: None,
            pin_sha512: None,
            execution_identity: None,
        };
        let identity = named_sdk_identity(&java)?;
        java.execution_identity = Some(identity.clone());
        verify_named_sdk_identity(&java)?;
        for path in &paths {
            fs::write(path, b"changed")?;
            assert!(verify_named_sdk_identity(&java).is_err());
            assert_eq!(java.execution_identity.as_deref(), Some(identity.as_str()));
            fs::write(path, b"original")?;
            verify_named_sdk_identity(&java)?;
        }
        Ok(())
    }
}
