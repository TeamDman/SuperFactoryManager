// Included in the engine namespace. Exact named Jar admission; dependency
// remapping and legacy launch retain their separate boundaries.

const NAMED_JAR_TOOL_COORDINATE: &str = "net.md-5:SpecialSource:1.11.0:shaded";
const NAMED_JAR_TOOL_HASH: &str = "blake3:f9134682aaae921bcef5a98a75f058145b0bf113";
const NAMED_JAR_MAIN: &str = "net.md_5.specialsource.SpecialSource";
const NAMED_JAR_MAX_CLASSPATH: usize = 256;
const NAMED_JAR_MAX_HELD_BYTES: u64 = 2 * 1024 * 1024 * 1024;
const NAMED_JAR_MAX_ARGFILE_BYTES: usize = 1024 * 1024;
const NAMED_JAR_MAX_RECEIPT_BYTES: usize = 2 * 1024 * 1024;
const NAMED_JAR_MAX_TREE_ENTRIES: usize = 32768;
const NAMED_JAR_MAX_TREE_DEPTH: usize = 32;
const NAMED_JAR_RECIPE_LOCKS: [(&str, &str); 4] = [
    (
        "1.19.2",
        "sha256:4e25ec4540fb6feecba27836c90ec7eab6a0109bc21f193ef358c64f558567ae",
    ),
    (
        "1.19.4",
        "sha256:ab0ad620449f524ffeaaee7db21ca4f967e34a728a1a90c51823d5eb9d0c6616",
    ),
    (
        "1.20",
        "sha256:687924ae50658639c45b17b167c25e6186cd384dd00ef0120484b5dade67b01c",
    ),
    (
        "1.20.1",
        "sha256:7bf92855b47d5a407f03e80cc34234aedcd7312708b8a06818c840cc0dc82c8a",
    ),
];

fn require_named_jar_recipe_identity(
    target: &str,
    minecraft_version: &str,
    recipe: &str,
    lock: &str,
    compiler_and_tool: (u32, u32),
) -> eyre::Result<()> {
    let expected = NAMED_JAR_RECIPE_LOCKS
        .iter()
        .find(|row| row.0 == target)
        .ok_or_else(|| {
            eyre::eyre!("named Jar supports only four exact released Forge-family recipes")
        })?;
    eyre::ensure!(
        minecraft_version == target
            && recipe == format!("sfm:released-native-inputs/4.34.0/{target}@1")
            && lock == expected.1
            && compiler_and_tool == (17, 17),
        "named Jar lost its exact original target/recipe/raw-lock/compiler/tool identity"
    );
    Ok(())
}

fn validate_named_jar_project(
    project: &crate::source_projection::frozen_recipe_project::FrozenRecipeProject,
) -> eyre::Result<()> {
    validate_first_named_compile(project)?;
    let receipt = project.receipt();
    require_named_jar_recipe_identity(
        &receipt.ownership.target_id,
        &receipt.ownership.minecraft_version,
        &receipt.released_inputs.recipe_id,
        &receipt.prepared_inputs.source_lock_sha256,
        (
            u32::from(receipt.compiler_release),
            u32::from(receipt.tool_jvm_minimum),
        ),
    )?;
    eyre::ensure!(
        project
            .dependencies()
            .coordinate(NAMED_JAR_TOOL_COORDINATE, false)?
            .original_content_hash()
            == NAMED_JAR_TOOL_HASH,
        "named Jar tool lost its original pin"
    );
    project.recheck(false)?;
    Ok(())
}

fn validate_named_native_build_target(plan: &BuildPlan, target: BuildTarget) -> eyre::Result<()> {
    if target == BuildTarget::Run && plan.identity.is_catalog_owned() {
        return ensure_projection_client_plan(plan);
    }
    require_named_native_target(plan.identity.is_catalog_owned(), target)?;
    let Some(project) = plan.named_project() else {
        return Ok(());
    };
    match target {
        BuildTarget::Compile => Ok(()),
        BuildTarget::Jar => validate_named_package_project(project),
        BuildTarget::Run | BuildTarget::SourceOutputs => eyre::bail!(
            "catalog-owned native route permits Compile or exact Jar only; NeoForm/JUnit/launch stay disabled"
        ),
    }
}

// Forge's SpecialSource validator stays separate: a named NeoForge package
// uses the original named class closure, with no remapping tool or mappings.
fn validate_named_package_project(
    project: &crate::source_projection::frozen_recipe_project::FrozenRecipeProject,
) -> eyre::Result<()> {
    if project.receipt().released_inputs.loader_kind == "neogradle_userdev" {
        validate_named_neoform_compile(project)?;
        project.recheck(false)
    } else {
        validate_named_jar_project(project)
    }
}

fn require_named_package_recipe_identity(
    target: &str,
    minecraft_version: &str,
    recipe: &str,
    lock: &str,
    compiler_and_tool: (u32, u32),
) -> eyre::Result<()> {
    if NAMED_NEOFORM_RECIPES.iter().any(|row| row.target == target) {
        require_named_neoform_recipe_identity(
            target,
            minecraft_version,
            recipe,
            lock,
            compiler_and_tool,
        )?;
        Ok(())
    } else {
        require_named_jar_recipe_identity(
            target,
            minecraft_version,
            recipe,
            lock,
            compiler_and_tool,
        )
    }
}

#[derive(Debug, Facet)]
pub(crate) struct NamedJarReport {
    schema: String,
    scope: String,
    preparation: crate::source_projection::frozen_recipe_project::FrozenRecipeProjectReceipt,
    java_release: u32,
    tool_jvm_major: u32,
    actual_sdk_identity: String,
    #[facet(proxy = JsonPath)]
    cache_dir: PathBuf,
    #[facet(proxy = JsonPath)]
    output_jar: PathBuf,
    output_content_hash: String,
    compilation: String,
    jar_packaging: String,
    application_execution: String,
    runtime_compatibility: String,
    immutable_source_lock: bool,
}

/// Exact named Jar route; sources/output/cache/recipe remain checked owner-derived.
pub(crate) fn invoke_named_jar(
    project: crate::source_projection::frozen_recipe_project::FrozenRecipeProject,
    options: &NamedCompileOptions,
    cancellation: &CancellationToken,
) -> eyre::Result<NamedJarReport> {
    validate_named_package_project(&project)?;
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
    plan.recheck_named_sdk()?;
    let path = build_cache_lock_path(&plan);
    let artifact = format!("{} native jar cache", plan.target_label());
    let _lock = if options.wait_for_build_lock {
        ArtifactLock::acquire(&path, artifact)?
    } else {
        ArtifactLock::try_acquire(&path, artifact)?
            .ok_or_else(|| eyre::eyre!("named native jar cache is already locked"))?
    };
    plan.recheck_named_inputs()?;
    require_named_jar_output_absent(&plan.minecraft_dir, &plan.rust_output_jar)?;
    write_last_plan_output(&plan)?;
    execute_build(
        &plan,
        options.explain_rebuild,
        BuildTarget::Jar,
        cancellation,
    )?;
    plan.recheck_named_inputs()?;
    plan.recheck_named_sdk()?;
    let hash = capture_named_package_hash(&plan.rust_output_jar)?;
    let output = ReviewedSpecialSourceFile::read(&plan.rust_output_jar, &hash)?;
    output.recheck()?;
    let project = plan
        .named_project()
        .ok_or_else(|| eyre::eyre!("named Jar lost checked owner"))?;
    Ok(NamedJarReport {
        schema: "sfm:named_native_jar@1".to_owned(),
        scope: "native compile and exact loader packaging; no JVM application/runtime/release parity proof".to_owned(),
        preparation: project.receipt().clone(), java_release: plan.java_release,
        tool_jvm_major: plan.java.major_version, actual_sdk_identity: plan.java.cache_identity(),
        cache_dir: plan.cache_dir.clone(), output_jar: plan.rust_output_jar.clone(),
        output_content_hash: output.content_hash.to_string(), compilation: "completed_or_valid_compile_cache".to_owned(),
        jar_packaging: "completed_this_invocation_no_unreceipted_jar_cache_reuse".to_owned(),
        application_execution: "not_performed".to_owned(), runtime_compatibility: "not_performed".to_owned(),
        immutable_source_lock: true,
    })
}

fn require_named_jar_path(path: &Path) -> eyre::Result<&str> {
    let text = require_specialsource_normal_absolute_path(path)?;
    let separator = if cfg!(windows) { ';' } else { ':' };
    eyre::ensure!(
        !text.contains(separator),
        "named Jar path contains a classpath separator"
    );
    Ok(text)
}

fn capture_named_package_hash(path: &Path) -> eyre::Result<ContentHash> {
    capture_named_package_hash_with_empty(path, false)
}

fn capture_named_mixin_hash(path: &Path) -> eyre::Result<ContentHash> {
    capture_named_package_hash_with_empty(path, true)
}

fn capture_named_package_hash_with_empty(
    path: &Path,
    empty_mixin: bool,
) -> eyre::Result<ContentHash> {
    require_named_jar_path(path)?;
    crate::source_projection::candidate_lock::checked_directory(
        path.parent()
            .ok_or_else(|| eyre::eyre!("named Jar generated input has no parent"))?,
    )?;
    let metadata = fs::symlink_metadata(path)?;
    require_named_regular_file(path, &metadata)?;
    eyre::ensure!(
        (metadata.len() > 0 || empty_mixin) && metadata.len() <= SPECIALSOURCE_LOCAL_FILE_LIMIT,
        "named Jar generated input is empty outside the exact mixin role or exceeds its byte bound"
    );
    let bytes = read_named_authenticated_file(path, SPECIALSOURCE_LOCAL_FILE_LIMIT)?;
    eyre::ensure!(
        !bytes.is_empty() || empty_mixin,
        "named Jar generated input became empty"
    );
    Ok(ContentHash::from_bytes(
        &bytes,
        ContentHashAlgorithm::Blake3,
    ))
}

// Shared lexical spelling only; named owner, path and argv admission remain separate.
struct ProjectPackageInputPaths {
    development_jar: PathBuf,
    reobf_mapping: PathBuf,
    mixin_reobf_mapping: PathBuf,
}

fn project_package_input_paths(
    cache_dir: &Path,
    minecraft_version: &str,
) -> ProjectPackageInputPaths {
    let project = cache_dir.join("project");
    ProjectPackageInputPaths {
        development_jar: project.join("dev.jar"),
        reobf_mapping: cache_dir
            .join("forge")
            .join(minecraft_version)
            .join("mappings")
            .join("official_to_srg.tsrg"),
        mixin_reobf_mapping: project.join("compileJava-mappings.tsrg"),
    }
}

fn named_jar_arguments(
    input: &Path,
    output: &Path,
    official: &Path,
    mixin: &Path,
) -> eyre::Result<Vec<String>> {
    Ok(vec![
        "--in-jar".to_owned(),
        require_named_jar_path(input)?.to_owned(),
        "--out-jar".to_owned(),
        require_named_jar_path(output)?.to_owned(),
        "--srg-in".to_owned(),
        require_named_jar_path(official)?.to_owned(),
        "--srg-in".to_owned(),
        require_named_jar_path(mixin)?.to_owned(),
        "--live".to_owned(),
    ])
}

fn require_named_jar_output_path(project: &Path, path: &Path) -> eyre::Result<()> {
    require_named_jar_path(path)?;
    let parent = path
        .parent()
        .ok_or_else(|| eyre::eyre!("named Jar output has no parent"))?;
    let relative = parent
        .strip_prefix(project)
        .wrap_err("named Jar output escapes its owner")?;
    crate::source_projection::native_project_target::inspect_cache_ancestors(
        project,
        &relative.to_string_lossy().replace('\\', "/"),
    )?;
    match fs::symlink_metadata(path) {
        Ok(metadata) => require_named_regular_file(path, &metadata)?,
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
        Err(error) => return Err(error.into()),
    }
    Ok(())
}

fn require_named_jar_output_absent(project: &Path, output: &Path) -> eyre::Result<()> {
    require_named_jar_output_path(project, output)?;
    match fs::symlink_metadata(output) {
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => Ok(()),
        Ok(_) => {
            eyre::bail!("named Jar refuses existing output of any kind; prior evidence is retained")
        }
        Err(error) => Err(error.into()),
    }
}

fn named_package_previous_output_removal_allowed(named: bool) -> bool {
    !named
}

fn copy_named_neoform_package(input: &Path, output: &Path) -> eyre::Result<()> {
    let hash = capture_named_package_hash(input)?;
    let held = ReviewedSpecialSourceFile::read(input, &hash)?;
    require_named_jar_path(output)?;
    let mut source = File::open(input)?;
    let mut destination = fs::OpenOptions::new()
        .write(true)
        .create_new(true)
        .open(output)?;
    std::io::copy(&mut source, &mut destination)?;
    destination.sync_all()?;
    drop(destination);
    held.recheck()?;
    eyre::ensure!(
        capture_named_package_hash(output)? == hash,
        "named NeoForge package copy changed bytes"
    );
    Ok(())
}

enum NamedPackageHeldFile {
    NonEmpty(ReviewedSpecialSourceFile),
    EmptyMixin {
        path: PathBuf,
        held: File,
        identity: crate::file_identity::FileIdentity,
        modified: SystemTime,
        content_hash: ContentHash,
    },
}
impl NamedPackageHeldFile {
    fn read(role: &str, path: &Path, expected: &ContentHash) -> eyre::Result<Self> {
        if role != "package_mixin_reobfuscation" || fs::symlink_metadata(path)?.len() != 0 {
            return Ok(Self::NonEmpty(ReviewedSpecialSourceFile::read(
                path, expected,
            )?));
        }
        require_named_jar_path(path)?;
        crate::source_projection::candidate_lock::checked_directory(
            path.parent()
                .ok_or_else(|| eyre::eyre!("empty mixin mapping has no parent"))?,
        )?;
        require_named_regular_file(path, &fs::symlink_metadata(path)?)?;
        let held = File::open(path)?;
        let metadata = held.metadata()?;
        require_named_regular_file(path, &metadata)?;
        let identity = crate::file_identity::file_identity(&held)?;
        let modified = metadata.modified()?;
        let content_hash = ContentHash::from_bytes(&[], expected.algorithm);
        let after = held.metadata()?;
        let current = File::open(path)?;
        eyre::ensure!(
            metadata.len() == 0
                && after.len() == 0
                && after.modified()? == modified
                && current.metadata()?.len() == 0
                && crate::file_identity::file_identity(&held)? == identity
                && crate::file_identity::file_identity(&current)? == identity
                && content_hash == *expected,
            "exact empty mixin mapping changed or lost its expected byte identity"
        );
        Ok(Self::EmptyMixin {
            path: path.to_path_buf(),
            held,
            identity,
            modified,
            content_hash,
        })
    }
    fn path(&self) -> &Path {
        match self {
            Self::NonEmpty(file) => &file.path,
            Self::EmptyMixin { path, .. } => path,
        }
    }
    fn content_hash(&self) -> ContentHash {
        match self {
            Self::NonEmpty(file) => file.content_hash,
            Self::EmptyMixin { content_hash, .. } => *content_hash,
        }
    }
    fn bytes(&self) -> u64 {
        match self {
            Self::NonEmpty(file) => file.bytes,
            Self::EmptyMixin { .. } => 0,
        }
    }
    fn recheck(&self) -> eyre::Result<()> {
        match self {
            Self::NonEmpty(file) => file.recheck(),
            Self::EmptyMixin {
                path,
                held,
                identity,
                modified,
                content_hash,
            } => {
                let current = Self::read("package_mixin_reobfuscation", path, content_hash)?;
                let Self::EmptyMixin {
                    identity: current_identity,
                    modified: current_modified,
                    ..
                } = current
                else {
                    eyre::bail!("empty mixin mapping became nonempty");
                };
                eyre::ensure!(
                    current_identity == *identity
                        && current_modified == *modified
                        && held.metadata()?.len() == 0
                        && held.metadata()?.modified()? == *modified
                        && crate::file_identity::file_identity(held)? == *identity,
                    "empty mixin mapping changed before delegation"
                );
                Ok(())
            }
        }
    }
}

struct NamedPackageDiagnosticFile {
    root: PathBuf,
    path: PathBuf,
    held: File,
    identity: crate::file_identity::FileIdentity,
}
impl NamedPackageDiagnosticFile {
    fn create(root: &Path, directory: &Path, role: &str) -> eyre::Result<Self> {
        let (prefix, suffix) = match role {
            "argfile" => ("tool-specialsource-package-", ".java.args"),
            "console_log" => ("console-package-", ".log"),
            _ => eyre::bail!("unreviewed named Jar diagnostic role"),
        };
        create_specialsource_diagnostic_directory(root, directory)?;
        let canonical_directory =
            crate::source_projection::candidate_lock::checked_directory(directory)?;
        let owner = tempfile::Builder::new()
            .prefix(prefix)
            .suffix(suffix)
            .tempfile_in(&canonical_directory)?;
        let (held, canonical_path) = owner.keep()?;
        let basename = canonical_path
            .file_name()
            .ok_or_else(|| eyre::eyre!("created diagnostic lacks basename"))?;
        let path = directory.join(basename);
        require_named_jar_output_path(root, &path)?;
        let identity = crate::file_identity::file_identity(&held)?;
        eyre::ensure!(
            crate::file_identity::file_identity(&File::open(&canonical_path)?)? == identity
                && crate::file_identity::file_identity(&File::open(&path)?)? == identity,
            "created named Jar diagnostic lost canonical/normal owner identity"
        );
        Ok(Self {
            root: root.to_path_buf(),
            path,
            held,
            identity,
        })
    }
    fn recheck(&self) -> eyre::Result<()> {
        require_named_jar_output_path(&self.root, &self.path)?;
        let current = File::open(&self.path)?;
        eyre::ensure!(
            crate::file_identity::file_identity(&current)? == self.identity
                && crate::file_identity::file_identity(&self.held)? == self.identity,
            "named Jar created diagnostic path identity changed"
        );
        Ok(())
    }
}

struct NamedPackageAdmission {
    files: Vec<(String, NamedPackageHeldFile)>,
    held_locks: Vec<ArtifactReadLock>,
}
impl NamedPackageAdmission {
    fn recheck(&self) -> eyre::Result<()> {
        for (_, file) in &self.files {
            file.recheck()?;
        }
        Ok(())
    }
}

fn require_named_native_target(named: bool, target: BuildTarget) -> eyre::Result<()> {
    eyre::ensure!(
        !named || matches!(target, BuildTarget::Compile | BuildTarget::Jar),
        "catalog-owned native route refuses Run/SourceOutputs; NeoForm/JUnit/launch stay disabled"
    );
    Ok(())
}

fn named_package_cache_reuse_allowed(named: bool) -> bool {
    !named
}

fn require_named_jar_cache_tree(root: &Path) -> eyre::Result<()> {
    require_named_jar_cache_tree_with_bounds(
        root,
        NAMED_JAR_MAX_TREE_ENTRIES,
        NAMED_JAR_MAX_TREE_DEPTH,
    )
}

fn require_named_jar_cache_tree_with_bounds(
    root: &Path,
    max_entries: usize,
    max_depth: usize,
) -> eyre::Result<()> {
    eyre::ensure!(
        max_entries > 0
            && max_entries <= NAMED_JAR_MAX_TREE_ENTRIES
            && max_depth > 0
            && max_depth <= NAMED_JAR_MAX_TREE_DEPTH,
        "invalid named Jar cache scan bound"
    );
    let mut pending = vec![(root.to_path_buf(), 0_usize)];
    let mut entries = 0_usize;
    while let Some((directory, depth)) = pending.pop() {
        crate::source_projection::candidate_lock::checked_directory(&directory)?;
        eyre::ensure!(
            depth <= max_depth,
            "named Jar cache tree exceeds depth bound"
        );
        for entry in fs::read_dir(&directory)? {
            entries += 1;
            eyre::ensure!(
                entries <= max_entries,
                "named Jar cache tree exceeds entry bound"
            );
            let path = entry?.path();
            let metadata = fs::symlink_metadata(&path)?;
            if metadata.is_dir() {
                crate::source_projection::candidate_lock::checked_directory(&path)?;
                pending.push((path, depth + 1));
            } else {
                require_named_regular_file(&path, &metadata)?;
            }
        }
    }
    Ok(())
}

fn require_named_jar_invocation(
    args: &[String],
    input: &Path,
    output: &Path,
    official: &Path,
    mixin: &Path,
) -> eyre::Result<()> {
    eyre::ensure!(
        args == named_jar_arguments(input, output, official, mixin)?,
        "named Jar refuses changed effect argv"
    );
    Ok(())
}

fn require_named_generated_jar(allowed: &BTreeSet<PathBuf>, path: &Path) -> eyre::Result<()> {
    eyre::ensure!(
        allowed.contains(path),
        "named Jar refuses undeclared generated classpath JAR"
    );
    Ok(())
}

fn insert_named_jar_pin(
    pins: &mut BTreeMap<PathBuf, ContentHash>,
    path: PathBuf,
    hash: ContentHash,
) -> eyre::Result<()> {
    if let Some(existing) = pins.insert(path, hash) {
        eyre::ensure!(
            existing == hash,
            "named Jar has conflicting byte pins for one path"
        );
    }
    Ok(())
}

impl ExecutionContext<'_> {
    fn require_named_package_paths(&self) -> eyre::Result<()> {
        let Some(project) = self.plan.named_project() else {
            return Ok(());
        };
        validate_named_package_project(project)?;
        self.plan.recheck_named_sdk()?;
        require_named_package_recipe_identity(
            &project.receipt().ownership.target_id,
            self.plan.minecraft_version.as_str(),
            &project.receipt().released_inputs.recipe_id,
            &project.dependencies().receipt().source_lock_sha256,
            (self.plan.java_release, self.plan.java.major_version),
        )?;
        let expected = rust_output_jar_path(
            &self.plan.minecraft_dir,
            required_property(&self.plan.properties, "mod_name")?,
            self.plan.minecraft_version.as_str(),
            required_property(&self.plan.properties, "mod_version")?,
        );
        eyre::ensure!(
            expected == self.plan.rust_output_jar
                && expected != self.plan.gradle_output_jar
                && !self.plan.refresh
                && !self.plan.allow_local_artifact_cache
                && self.plan.artifact_sources.is_empty(),
            "named Jar output/planning authority changed"
        );
        for relative in [
            "project/classes",
            "project/resources",
            "project/staged-resources",
        ] {
            let path = self.plan.cache_dir.join(relative);
            let relative = path
                .strip_prefix(&self.plan.minecraft_dir)?
                .to_string_lossy()
                .replace('\\', "/");
            crate::source_projection::native_project_target::inspect_cache_ancestors(
                &self.plan.minecraft_dir,
                &relative,
            )?;
            if path.exists() {
                require_named_jar_cache_tree(&path)?;
            }
        }
        let paths =
            project_package_input_paths(&self.plan.cache_dir, self.plan.minecraft_version.as_str());
        for path in [
            &expected,
            &paths.development_jar,
            &paths.mixin_reobf_mapping,
            &paths.reobf_mapping,
        ] {
            require_named_jar_output_path(&self.plan.minecraft_dir, path)?;
        }
        Ok(())
    }

    fn prepare_named_package_admission(&self) -> eyre::Result<Option<NamedPackageAdmission>> {
        if self.plan.named_project().is_none() {
            return Ok(None);
        }
        self.require_named_package_paths()?;
        require_named_jar_output_absent(&self.plan.minecraft_dir, &self.plan.rust_output_jar)?;
        if self.plan.loader_toolchain.kind == LoaderToolchainKind::NeoGradleUserdev {
            // Only the scoped producer consumer can supply this held path. Merely
            // finding an inherited compile JAR does not confer package authority.
            let classes = self
                .held_neoform_compile_jar
                .ok_or_else(|| eyre::eyre!("named NeoForge package lost its live producer"))?;
            let hash = capture_named_package_hash(classes)?;
            let admission = NamedPackageAdmission {
                files: vec![(
                    "held_neoform_classes".to_owned(),
                    NamedPackageHeldFile::read("held_neoform_classes", classes, &hash)?,
                )],
                held_locks: Vec::new(),
            };
            self.recheck_named_package_admission(Some(&admission))?;
            return Ok(Some(admission));
        }
        refuse_specialsource_jvm_overrides(|name| std::env::var_os(name))?;
        let resolver = self.plan.resolver(&self.cancellation_token)?;
        let classpath = self.package_reobfuscation_classpath(&resolver)?;
        eyre::ensure!(
            classpath.len() <= NAMED_JAR_MAX_CLASSPATH,
            "named Jar classpath exceeds bound"
        );
        let pins = self.named_package_classpath_pins(&resolver)?;
        let paths =
            project_package_input_paths(&self.plan.cache_dir, self.plan.minecraft_version.as_str());
        let official = paths.reobf_mapping;
        let mixin = paths.mixin_reobf_mapping;
        let tool = self.artifact(ArtifactId::from("tool-specialsource"))?;
        eyre::ensure!(
            tool.coordinate.as_deref() == Some(NAMED_JAR_TOOL_COORDINATE)
                && tool.provenance.hash.to_string() == NAMED_JAR_TOOL_HASH,
            "named Jar admission tool differs from original pin"
        );
        let tool_hash = ContentHash::parse(NAMED_JAR_TOOL_HASH).map_err(|e| eyre::eyre!(e))?;
        let mut requests = vec![
            ("tool", &tool.cache_path, tool_hash),
            (
                "official_to_srg",
                &official,
                capture_named_package_hash(&official)?,
            ),
            (
                "package_mixin_reobfuscation",
                &mixin,
                capture_named_mixin_hash(&mixin)?,
            ),
        ];
        for path in &classpath {
            requests.push((
                "live_classpath",
                path,
                *pins
                    .get(path)
                    .ok_or_else(|| eyre::eyre!("unbound named Jar admission classpath input"))?,
            ));
        }
        let mut admission = NamedPackageAdmission {
            files: Vec::new(),
            held_locks: Vec::new(),
        };
        let mut held_bytes = 0_u64;
        for (role, path, expected) in requests {
            self.assert_allowed_input(path)?;
            admission
                .held_locks
                .push(acquire_artifact_path_read_lock_cancellable(
                    path,
                    &self.cancellation_token,
                )?);
            let held = NamedPackageHeldFile::read(role, path, &expected)?;
            held_bytes = held_bytes
                .checked_add(held.bytes())
                .ok_or_else(|| eyre::eyre!("held admission budget overflow"))?;
            eyre::ensure!(
                held_bytes <= NAMED_JAR_MAX_HELD_BYTES,
                "named Jar admission exceeds held byte bound"
            );
            admission.files.push((role.to_owned(), held));
        }
        eyre::ensure!(
            read_main_class(&tool.cache_path)? == NAMED_JAR_MAIN,
            "named Jar admission tool Main-Class differs from locked recipe"
        );
        self.recheck_named_package_admission(Some(&admission))?;
        Ok(Some(admission))
    }

    fn recheck_named_package_admission(
        &self,
        admission: Option<&NamedPackageAdmission>,
    ) -> eyre::Result<()> {
        let Some(admission) = admission else {
            eyre::ensure!(
                self.plan.named_project().is_none(),
                "named Jar lost its held admission"
            );
            return Ok(());
        };
        self.bail_if_cancelled()?;
        self.plan.recheck_named_inputs()?;
        self.plan.recheck_named_sdk()?;
        self.require_named_package_paths()?;
        admission.recheck()?;
        require_named_jar_output_absent(&self.plan.minecraft_dir, &self.plan.rust_output_jar)
    }

    // All collected generated JARs must have recipe-derived names, not presence authority.
    fn named_package_generated_pins(
        &self,
        resolver: &Resolver,
    ) -> eyre::Result<BTreeMap<PathBuf, ContentHash>> {
        let mut pins = BTreeMap::new();
        let root = self.plan.cache_dir.join("dependencies");
        require_named_jar_cache_tree(&root)?;
        let mapping = self.plan.cache_dir.join(format!(
            "forge/{}/mappings/srg_to_official.tsrg",
            self.plan.minecraft_version
        ));
        let mapping_hash = capture_named_package_hash(&mapping)?;
        let mut allowed = BTreeSet::new();
        for dependency in self
            .plan
            .dependencies
            .iter()
            .filter(|d| requires_forge_dependency_deobf(d))
        {
            let coordinate = MavenCoordinate::parse(&dependency.resolved_notation)?;
            let project = self
                .plan
                .named_project()
                .ok_or_else(|| eyre::eyre!("missing named owner"))?;
            let pin = project
                .dependencies()
                .coordinate(&coordinate.to_string(), false)?;
            let input_hash =
                ContentHash::parse(&pin.original_content_hash()).map_err(|e| eyre::eyre!(e))?;
            allowed.insert(remapped_dependency_output_path(
                &root,
                &input_hash,
                &mapping_hash,
                &coordinate,
            ));
            allowed.insert(specialsource_dependency_output_path(
                &root,
                &input_hash,
                &mapping_hash,
                &coordinate,
            ));
        }
        let collected = collect_jars(self, &root)?;
        for path in collected {
            require_named_generated_jar(&allowed, &path)?;
            insert_named_jar_pin(&mut pins, path.clone(), capture_named_package_hash(&path)?)?;
        }
        let loader = loader_dev_compile_jar(self);
        insert_named_jar_pin(
            &mut pins,
            loader.clone(),
            capture_named_package_hash(&loader)?,
        )?;
        // No resolver request/acquisition is made here.
        let _ = resolver;
        Ok(pins)
    }

    fn package_reobfuscation_classpath(&self, resolver: &Resolver) -> eyre::Result<Vec<PathBuf>> {
        if self.plan.named_project().is_some() {
            self.named_package_generated_pins(resolver)?;
        }
        let antlr = resolve_antlr_classpath(self, resolver)?;
        resolve_project_compile_classpath(self, resolver, &antlr)
    }

    fn named_package_classpath_pins(
        &self,
        resolver: &Resolver,
    ) -> eyre::Result<BTreeMap<PathBuf, ContentHash>> {
        let project = self
            .plan
            .named_project()
            .ok_or_else(|| eyre::eyre!("missing named owner"))?;
        let mut pins = self.named_package_generated_pins(resolver)?;
        for artifact in &project.dependencies().original_lock().artifacts {
            if let Some(coordinate) = &artifact.coordinate {
                let coordinate = MavenCoordinate::parse(coordinate)?;
                // The private prepared handle already binds every immutable original row.
                insert_named_jar_pin(
                    &mut pins,
                    resolver.cache_path_for(&coordinate),
                    artifact.hash,
                )?;
            }
        }
        let authenticated = self
            .plan
            .minecraft
            .authenticated_inputs
            .as_ref()
            .ok_or_else(|| {
                eyre::eyre!("named Jar requires authenticated Minecraft library descendants")
            })?;
        for request in authenticated.libraries() {
            let path = self.plan.minecraft_libraries_dir.join(
                request
                    .library_relative_path()
                    .ok_or_else(|| eyre::eyre!("authenticated library lost exact path"))?,
            );
            insert_named_jar_pin(&mut pins, path, *request.expected_hash())?;
        }
        Ok(pins)
    }

    #[expect(
        clippy::too_many_lines,
        reason = "Separate exact Jar effect boundary and durable failure receipt."
    )]
    fn run_project_package_specialsource(
        &self,
        classpath: &[PathBuf],
        args: &[String],
        diagnostic: &Path,
    ) -> eyre::Result<()> {
        let Some(project) = self.plan.named_project() else {
            return self.run_java_tool_with_classpath(
                "tool-specialsource",
                &[],
                classpath,
                args,
                diagnostic,
            );
        };
        self.require_named_package_paths()?;
        self.bail_if_cancelled()?;
        let project_root = self.plan.cache_dir.join("project");
        let ProjectPackageInputPaths {
            development_jar: input,
            reobf_mapping: official,
            mixin_reobf_mapping: mixin,
        } = project_package_input_paths(&self.plan.cache_dir, self.plan.minecraft_version.as_str());
        eyre::ensure!(
            diagnostic == project_root.join("reobf"),
            "named Jar refuses changed diagnostic path"
        );
        require_named_jar_invocation(args, &input, &self.plan.rust_output_jar, &official, &mixin)?;
        require_named_jar_output_absent(&self.plan.minecraft_dir, &self.plan.rust_output_jar)?;
        let resolver = self.plan.resolver(&self.cancellation_token)?;
        eyre::ensure!(
            classpath == self.package_reobfuscation_classpath(&resolver)?
                && classpath.len() <= NAMED_JAR_MAX_CLASSPATH,
            "named Jar live classpath changed/exceeds bound"
        );
        let pins = self.named_package_classpath_pins(&resolver)?;
        let tool = self.artifact(ArtifactId::from("tool-specialsource"))?;
        eyre::ensure!(
            tool.coordinate.as_deref() == Some(NAMED_JAR_TOOL_COORDINATE)
                && tool.provenance.hash.to_string() == NAMED_JAR_TOOL_HASH,
            "named Jar resolved tool differs from original pin"
        );
        let hash = ContentHash::parse(NAMED_JAR_TOOL_HASH).map_err(|e| eyre::eyre!(e))?;
        let mut files = Vec::new();
        let mut read_locks = Vec::new();
        let mut held_bytes = 0_u64;
        let mut requests = vec![
            ("tool", &tool.cache_path, hash),
            (
                "development_jar",
                &input,
                capture_named_package_hash(&input)?,
            ),
            (
                "official_to_srg",
                &official,
                capture_named_package_hash(&official)?,
            ),
            (
                "package_mixin_reobfuscation",
                &mixin,
                capture_named_mixin_hash(&mixin)?,
            ),
        ];
        for path in classpath {
            let expected = pins
                .get(path)
                .copied()
                .ok_or_else(|| eyre::eyre!("unbound named Jar classpath input"))?;
            requests.push(("live_classpath", path, expected));
        }
        for (role, path, expected) in requests {
            require_named_jar_path(path)?;
            self.assert_allowed_input(path)?;
            read_locks.push(acquire_artifact_path_read_lock_cancellable(
                path,
                &self.cancellation_token,
            )?);
            let held = NamedPackageHeldFile::read(role, path, &expected)?;
            held_bytes = held_bytes
                .checked_add(held.bytes())
                .ok_or_else(|| eyre::eyre!("held input budget overflow"))?;
            eyre::ensure!(
                held_bytes <= NAMED_JAR_MAX_HELD_BYTES,
                "named Jar held inputs exceed aggregate bound"
            );
            files.push((role.to_owned(), held));
        }
        let main = read_main_class(&tool.cache_path)?;
        eyre::ensure!(
            main == NAMED_JAR_MAIN,
            "named Jar tool Main-Class differs from locked recipe"
        );
        require_named_jar_path(&self.plan.java.executable)?;
        #[cfg(windows)]
        eyre::ensure!(
            specialsource_path_utf16_units(&self.plan.java.executable)? < 260,
            "named Jar requires verified normal short SDK executable spelling"
        );
        refuse_specialsource_jvm_overrides(|name| std::env::var_os(name))?;
        create_specialsource_diagnostic_directory(&project_root, diagnostic)?;
        let scratch = RetainedSpecialSourceScratch::create()?;
        let full_classpath = dedup_paths_preserve_order(
            std::iter::once(tool.cache_path.clone())
                .chain(classpath.iter().cloned())
                .collect(),
        );
        let classpath_arg = join_classpath(&full_classpath);
        let mut java_args = vec!["-cp".to_owned(), classpath_arg.clone(), main.clone()];
        java_args.extend_from_slice(args);
        let contents = java_args
            .into_iter()
            .map(escape_argfile_arg)
            .collect::<Vec<_>>()
            .join("\n");
        eyre::ensure!(
            contents.len() <= NAMED_JAR_MAX_ARGFILE_BYTES,
            "named Jar argument file exceeds bound"
        );
        let mut original_args =
            NamedPackageDiagnosticFile::create(&project_root, diagnostic, "argfile")?;
        original_args.held.write_all(contents.as_bytes())?;
        original_args.held.sync_all()?;
        let console_log =
            NamedPackageDiagnosticFile::create(&project_root, diagnostic, "console_log")?;
        let transport = java_tool_argfile_transport(contents.as_bytes())?;
        let mut receipt = NamedPackageLaunchReceipt::prepared(
            self,
            &files,
            diagnostic,
            &scratch.path,
            &original_args.path,
            transport.path(),
            &console_log.path,
            contents.as_bytes(),
        )?;
        receipt.persist()?;
        let preflight = (|| {
            self.bail_if_cancelled()?;
            project.recheck(false)?;
            self.plan.recheck_named_sdk()?;
            self.require_named_package_paths()?;
            refuse_specialsource_jvm_overrides(|name| std::env::var_os(name))?;
            for (_, file) in &files {
                file.recheck()?;
            }
            scratch.recheck_empty()?;
            original_args.recheck()?;
            console_log.recheck()?;
            eyre::ensure!(
                read_named_authenticated_file(
                    &original_args.path,
                    u64::try_from(NAMED_JAR_MAX_ARGFILE_BYTES)?
                )? == contents.as_bytes()
                    && console_log.held.metadata()?.len() == 0,
                "named Jar diagnostics changed before launch"
            );
            require_named_jar_output_absent(&self.plan.minecraft_dir, &self.plan.rust_output_jar)?;
            eyre::ensure!(
                read_named_authenticated_file(
                    transport.path(),
                    u64::try_from(NAMED_JAR_MAX_ARGFILE_BYTES)?
                )? == contents.as_bytes(),
                "named Jar argument transport changed"
            );
            Ok::<(), eyre::Report>(())
        })();
        if let Err(error) = preflight {
            "preflight_refused".clone_into(&mut receipt.phase);
            let _ = receipt.persist_secondary();
            return Err(error);
        }
        let mut command = Command::new(&self.plan.java.executable);
        command
            .arg(java_tool_argfile_argument(transport.path())?)
            .current_dir(&scratch.path);
        let started = Instant::now();
        let mut observer_error = None;
        let output = run_command_capture_output_observing_spawn(
            &self.cancellation_token,
            &mut command,
            "tool-specialsource-package",
            |spawn| {
                receipt.observe(spawn);
                observer_error = receipt.persist_secondary();
            },
        );
        let output = match output {
            Ok(output) => output,
            Err(error) => {
                if receipt.child_created {
                    "capture_failed".clone_into(&mut receipt.phase);
                } else if receipt.phase == "prepared" {
                    "preflight_refused".clone_into(&mut receipt.phase);
                }
                if let Some(io) = error.downcast_ref::<std::io::Error>() {
                    receipt.io_error_kind = Some(format!("{:?}", io.kind()));
                    receipt.raw_os_error = io.raw_os_error();
                }
                let _ = receipt.persist_secondary();
                return Err(error)
                    .wrap_err("Failed reviewed named Jar SpecialSource package launch");
            }
        };
        receipt.child_status = Some(output.status.to_string());
        receipt.cancelled = output.cancelled;
        if output.cancelled {
            "cancelled"
        } else {
            "child_exited"
        }
        .clone_into(&mut receipt.phase);
        let child_receipt_error = receipt.persist_secondary();
        console_log.recheck()?;
        eyre::ensure!(
            console_log.held.metadata()?.len() == 0,
            "named Jar created console leaf changed"
        );
        let log = &console_log.path;
        write_java_tool_console_log(
            log,
            "tool-specialsource-package",
            &main,
            started.elapsed().as_millis(),
            &classpath_arg,
            args,
            &output,
        )?;
        if output.cancelled || !output.status.success() {
            let _ = receipt.persist_secondary();
            eyre::bail!(
                "Named Jar SpecialSource failed/cancelled; retained scratch {} and log {}",
                scratch.path.display(),
                log.display()
            );
        }
        self.require_named_package_paths()?;
        let hash = capture_named_package_hash(&self.plan.rust_output_jar)?;
        let produced = ReviewedSpecialSourceFile::read(&self.plan.rust_output_jar, &hash)?;
        produced.recheck()?;
        receipt.output_content_hash = Some(produced.content_hash.to_string());
        receipt.output_bytes = Some(produced.bytes);
        "writer_completed".clone_into(&mut receipt.phase);
        let final_error = receipt.persist_secondary();
        if let Some(error) = observer_error.or(child_receipt_error).or(final_error) {
            return Err(error)
                .wrap_err("Named Jar writer completed but durable receipt was not persisted");
        }
        // Locks and all held inputs outlive the child and its output hash/receipt.
        drop(read_locks);
        Ok(())
    }
}

#[derive(Clone, Debug, Facet)]
struct NamedPackageInputWitness {
    role: String,
    path: String,
    content_hash: String,
    bytes: u64,
    empty: bool,
}
#[derive(Clone, Debug, Facet)]
#[expect(
    clippy::struct_excessive_bools,
    reason = "Stable receipt fields record independent diagnostic retention, child creation and cancellation facts."
)]
struct NamedPackageLaunchReceipt {
    schema: String,
    mode: String,
    phase: String,
    projection_key: String,
    context_identity: String,
    preparation_identity: String,
    recipe_id: String,
    source_lock_sha256: String,
    sdk_identity: String,
    cache_identity: String,
    classpath_entries: usize,
    exact_jar_arguments: Vec<String>,
    compilation: String,
    application_execution: String,
    runtime_compatibility: String,
    java_executable: String,
    tool_coordinate: String,
    tool_content_hash: String,
    main_class: String,
    inputs: Vec<NamedPackageInputWitness>,
    output_jar: String,
    output_content_hash: Option<String>,
    output_bytes: Option<u64>,
    diagnostic_cwd: String,
    launch_cwd: String,
    launch_cwd_utf16_units: usize,
    scratch_kept: bool,
    diagnostics_kept: bool,
    original_argfile: String,
    transport_argfile: String,
    console_log: String,
    argfile_bytes: usize,
    argfile_sha256: String,
    child_created: bool,
    child_pid: Option<u32>,
    io_error_kind: Option<String>,
    raw_os_error: Option<i32>,
    child_status: Option<String>,
    cancelled: bool,
}
impl NamedPackageLaunchReceipt {
    #[expect(
        clippy::too_many_arguments,
        reason = "Receipt retains exact ownership, held inputs and both retained diagnostic outputs."
    )]
    fn prepared(
        context: &ExecutionContext<'_>,
        files: &[(String, NamedPackageHeldFile)],
        diagnostic: &Path,
        scratch: &Path,
        original: &Path,
        transport: &Path,
        log: &Path,
        bytes: &[u8],
    ) -> eyre::Result<Self> {
        let project = context
            .plan
            .named_project()
            .ok_or_else(|| eyre::eyre!("missing named Jar owner"))?;
        let paths = project_package_input_paths(
            &context.plan.cache_dir,
            context.plan.minecraft_version.as_str(),
        );
        Ok(Self {
            schema: "sfm:named_jar_specialsource_launch@1".to_owned(),
            mode: "project_jar_two_mappings_live".to_owned(),
            phase: "prepared".to_owned(),
            projection_key: project.receipt().ownership.projection_key.clone(),
            context_identity: project.receipt().ownership.context_identity.clone(),
            preparation_identity: project.receipt().preparation_identity()?,
            recipe_id: project.receipt().released_inputs.recipe_id.clone(),
            source_lock_sha256: project.dependencies().receipt().source_lock_sha256.clone(),
            sdk_identity: context.plan.java.cache_identity(),
            cache_identity: context.plan.cache_dir.display().to_string(),
            classpath_entries: files
                .iter()
                .filter(|(role, _)| role == "live_classpath")
                .count(),
            exact_jar_arguments: named_jar_arguments(
                &paths.development_jar,
                &context.plan.rust_output_jar,
                &paths.reobf_mapping,
                &paths.mixin_reobf_mapping,
            )?,
            compilation: "completed_or_valid_compile_cache".to_owned(),
            application_execution: "not_performed".to_owned(),
            runtime_compatibility: "not_performed".to_owned(),
            java_executable: require_named_jar_path(&context.plan.java.executable)?.to_owned(),
            tool_coordinate: NAMED_JAR_TOOL_COORDINATE.to_owned(),
            tool_content_hash: NAMED_JAR_TOOL_HASH.to_owned(),
            main_class: NAMED_JAR_MAIN.to_owned(),
            inputs: files
                .iter()
                .map(|(role, file)| {
                    Ok(NamedPackageInputWitness {
                        role: role.clone(),
                        path: require_named_jar_path(file.path())?.to_owned(),
                        content_hash: file.content_hash().to_string(),
                        bytes: file.bytes(),
                        empty: file.bytes() == 0,
                    })
                })
                .collect::<eyre::Result<Vec<_>>>()?,
            output_jar: require_named_jar_path(&context.plan.rust_output_jar)?.to_owned(),
            output_content_hash: None,
            output_bytes: None,
            diagnostic_cwd: require_named_jar_path(diagnostic)?.to_owned(),
            launch_cwd: require_named_jar_path(scratch)?.to_owned(),
            launch_cwd_utf16_units: specialsource_path_utf16_units(scratch)?,
            scratch_kept: true,
            diagnostics_kept: true,
            original_argfile: require_named_jar_path(original)?.to_owned(),
            transport_argfile: require_named_jar_path(transport)?.to_owned(),
            console_log: require_named_jar_path(log)?.to_owned(),
            argfile_bytes: bytes.len(),
            argfile_sha256: crate::source_projection::provenance::sha256(bytes),
            child_created: false,
            child_pid: None,
            io_error_kind: None,
            raw_os_error: None,
            child_status: None,
            cancelled: false,
        })
    }
    fn observe(&mut self, report: &ProcessSpawnReport) {
        if report.child_pid.is_some() {
            "child_created"
        } else {
            "spawn_failed"
        }
        .clone_into(&mut self.phase);
        self.child_created = report.child_pid.is_some();
        self.child_pid = report.child_pid;
        self.io_error_kind.clone_from(&report.io_error_kind);
        self.raw_os_error = report.raw_os_error;
    }
    fn persist(&self) -> eyre::Result<PathBuf> {
        let directory = crate::source_projection::candidate_lock::checked_directory(Path::new(
            &self.diagnostic_cwd,
        ))?;
        let encoded = facet_json::to_string_pretty(self)?;
        eyre::ensure!(
            encoded.len() <= NAMED_JAR_MAX_RECEIPT_BYTES,
            "named Jar receipt exceeds byte bound"
        );
        let owner = tempfile::Builder::new()
            .prefix("sfm-named-package-launch-")
            .suffix(".json")
            .tempfile_in(directory)?;
        let (mut file, path) = owner.keep()?;
        file.write_all(encoded.as_bytes())?;
        file.sync_all()?;
        Ok(path)
    }
    fn persist_secondary(&self) -> Option<eyre::Report> {
        self.persist().err().inspect(|error| {
            tracing::error!(phase = %self.phase, error = %error, "named_package_receipt_write_failed");
        })
    }
}

#[cfg(test)]
include!("engine_named_jar_tests.rs");
