// Reviewed adapter for four original named Compile dependency-remap recipes.
// This is not a general Java cwd rewrite or an OS network sandbox.

const SPECIALSOURCE_RELEASE_1192_KEY: &str = "sfm-4.34.0/mc-1.19.2";
const SPECIALSOURCE_RELEASE_1192_RECIPE: &str = "sfm:released-native-inputs/4.34.0/1.19.2@1";
const SPECIALSOURCE_RELEASE_1192_LOCK: &str =
    "sha256:4e25ec4540fb6feecba27836c90ec7eab6a0109bc21f193ef358c64f558567ae";
const SPECIALSOURCE_RELEASE_1194_KEY: &str = "sfm-4.34.0/mc-1.19.4";
const SPECIALSOURCE_RELEASE_1194_RECIPE: &str = "sfm:released-native-inputs/4.34.0/1.19.4@1";
const SPECIALSOURCE_RELEASE_1194_LOCK: &str =
    "sha256:ab0ad620449f524ffeaaee7db21ca4f967e34a728a1a90c51823d5eb9d0c6616";
const SPECIALSOURCE_RELEASE_120_KEY: &str = "sfm-4.34.0/mc-1.20";
const SPECIALSOURCE_RELEASE_120_RECIPE: &str = "sfm:released-native-inputs/4.34.0/1.20@1";
const SPECIALSOURCE_RELEASE_120_LOCK: &str =
    "sha256:687924ae50658639c45b17b167c25e6186cd384dd00ef0120484b5dade67b01c";
const SPECIALSOURCE_RELEASE_1201_KEY: &str = "sfm-4.34.0/mc-1.20.1";
const SPECIALSOURCE_RELEASE_1201_RECIPE: &str = "sfm:released-native-inputs/4.34.0/1.20.1@1";
const SPECIALSOURCE_RELEASE_1201_LOCK: &str =
    "sha256:7bf92855b47d5a407f03e80cc34234aedcd7312708b8a06818c840cc0dc82c8a";
// All four exact original schema-2 locks pin these identical SpecialSource bytes.
const SPECIALSOURCE_REVIEWED_COORDINATE: &str = "net.md-5:SpecialSource:1.11.0:shaded";
const SPECIALSOURCE_REVIEWED_HASH: &str = "blake3:f9134682aaae921bcef5a98a75f058145b0bf113";
const SPECIALSOURCE_REVIEWED_MAIN: &str = "net.md_5.specialsource.SpecialSource";
const SPECIALSOURCE_LAUNCH_CWD_LIMIT: usize = 240;
const SPECIALSOURCE_LOCAL_FILE_LIMIT: u64 = 512 * 1024 * 1024;
const SPECIALSOURCE_JVM_OVERRIDE_NAMES: [&str; 3] =
    ["JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS"];

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
struct ReviewedSpecialSourceRecipe {
    projection_key: &'static str,
    target: &'static str,
    recipe_id: &'static str,
    source_lock_sha256: &'static str,
    failure_context: &'static str,
}

impl ReviewedSpecialSourceRecipe {
    fn loader_kind(self) -> LoaderToolchainKind {
        if self.projection_key == SPECIALSOURCE_RELEASE_1201_KEY {
            LoaderToolchainKind::ForgeGradleNeoForgeGroup
        } else {
            LoaderToolchainKind::ForgeGradleForge
        }
    }

    fn mapping_path(self, cache: &Path) -> PathBuf {
        cache.join(format!(
            "forge/{}/mappings/srg_to_official.tsrg",
            self.target
        ))
    }
}

fn reviewed_specialsource_recipe(key: &str) -> Option<ReviewedSpecialSourceRecipe> {
    match key {
        SPECIALSOURCE_RELEASE_1192_KEY => Some(ReviewedSpecialSourceRecipe {
            projection_key: SPECIALSOURCE_RELEASE_1192_KEY,
            target: "1.19.2",
            recipe_id: SPECIALSOURCE_RELEASE_1192_RECIPE,
            source_lock_sha256: SPECIALSOURCE_RELEASE_1192_LOCK,
            failure_context: "Failed reviewed original1192 SpecialSource launch",
        }),
        SPECIALSOURCE_RELEASE_1194_KEY => Some(ReviewedSpecialSourceRecipe {
            projection_key: SPECIALSOURCE_RELEASE_1194_KEY,
            target: "1.19.4",
            recipe_id: SPECIALSOURCE_RELEASE_1194_RECIPE,
            source_lock_sha256: SPECIALSOURCE_RELEASE_1194_LOCK,
            failure_context: "Failed reviewed original1194 SpecialSource launch",
        }),
        SPECIALSOURCE_RELEASE_120_KEY => Some(ReviewedSpecialSourceRecipe {
            projection_key: SPECIALSOURCE_RELEASE_120_KEY,
            target: "1.20",
            recipe_id: SPECIALSOURCE_RELEASE_120_RECIPE,
            source_lock_sha256: SPECIALSOURCE_RELEASE_120_LOCK,
            failure_context: "Failed reviewed original120 SpecialSource launch",
        }),
        SPECIALSOURCE_RELEASE_1201_KEY => Some(ReviewedSpecialSourceRecipe {
            projection_key: SPECIALSOURCE_RELEASE_1201_KEY,
            target: "1.20.1",
            recipe_id: SPECIALSOURCE_RELEASE_1201_RECIPE,
            source_lock_sha256: SPECIALSOURCE_RELEASE_1201_LOCK,
            failure_context: "Failed reviewed original1201 SpecialSource launch",
        }),
        _ => None,
    }
}

fn reviewed_specialsource_project_recipe(
    ownership: &crate::source_projection::catalog_owned_project::CatalogOwnedProjectReceipt,
) -> Option<ReviewedSpecialSourceRecipe> {
    use crate::source_projection::projection_catalog::ProjectionEnvironment;

    if ownership.environment != ProjectionEnvironment::Release {
        return None;
    }
    // A catalog key names an output directory, not a recipe or feature set.
    // The caller still checks the exact recipe, source lock, SDK and tool bytes.
    let key = match ownership.target_id.as_str() {
        "1.19.2" => SPECIALSOURCE_RELEASE_1192_KEY,
        "1.19.4" => SPECIALSOURCE_RELEASE_1194_KEY,
        "1.20" => SPECIALSOURCE_RELEASE_120_KEY,
        "1.20.1" => SPECIALSOURCE_RELEASE_1201_KEY,
        _ => return None,
    };
    reviewed_specialsource_recipe(key)
}

fn require_reviewed_specialsource_identity(
    recipe: ReviewedSpecialSourceRecipe,
    ownership: &crate::source_projection::catalog_owned_project::CatalogOwnedProjectReceipt,
    plan_minecraft_version: &str,
    plan_loader: &LoaderToolchainKind,
    released_identity: (&str, &str),
    sdk_compiler_major: (u32, u32),
) -> eyre::Result<()> {
    crate::source_projection::projection_catalog::validate_projection_key(
        &ownership.projection_key,
    )?;
    eyre::ensure!(
        ownership.project_dir
            == format!(
                "{}/{}",
                crate::source_projection::projection_catalog::PROJECTIONS_DIR,
                ownership.projection_key
            )
            && ownership.environment
                == crate::source_projection::projection_catalog::ProjectionEnvironment::Release
            && ownership.target_id == recipe.target
            && ownership.minecraft_version == recipe.target
            && plan_minecraft_version == recipe.target
            && *plan_loader == recipe.loader_kind()
            && released_identity == (recipe.recipe_id, recipe.source_lock_sha256)
            && sdk_compiler_major == (17, 17),
        "reviewed SpecialSource launch requires the exact original released{} Compile recipe",
        recipe.target
    );
    Ok(())
}

fn refuse_specialsource_jvm_overrides(
    mut value: impl FnMut(&str) -> Option<std::ffi::OsString>,
) -> eyre::Result<()> {
    for name in SPECIALSOURCE_JVM_OVERRIDE_NAMES {
        eyre::ensure!(
            value(name).is_none_or(|value| value.is_empty()),
            "reviewed SpecialSource launch refuses nonempty {name}; its value is not logged or erased"
        );
    }
    Ok(())
}

fn require_specialsource_normal_absolute_path(path: &Path) -> eyre::Result<&str> {
    use std::path::Component;

    eyre::ensure!(
        path.is_absolute()
            && !path
                .components()
                .any(|part| matches!(part, Component::ParentDir | Component::CurDir)),
        "reviewed SpecialSource requires an absolute path without traversal"
    );
    #[cfg(windows)]
    eyre::ensure!(
        !matches!(path.components().next(), Some(Component::Prefix(prefix))
            if matches!(prefix.kind(), std::path::Prefix::Verbatim(_)
                | std::path::Prefix::VerbatimDisk(_)
                | std::path::Prefix::VerbatimUNC(_, _))),
        "reviewed SpecialSource refuses verbatim path spelling; no silent executable or argument normalization is performed"
    );
    let text = path
        .to_str()
        .ok_or_else(|| eyre::eyre!("reviewed SpecialSource requires a Unicode path"))?;
    eyre::ensure!(
        !text.contains(['\r', '\n', '\0']),
        "reviewed SpecialSource path contains a control character"
    );
    Ok(text)
}

fn specialsource_path_utf16_units(path: &Path) -> eyre::Result<usize> {
    Ok(require_specialsource_normal_absolute_path(path)?
        .encode_utf16()
        .count())
}

fn specialsource_dependency_arguments(
    input: &Path,
    output: &Path,
    mapping: &Path,
) -> eyre::Result<Vec<String>> {
    Ok(vec![
        "--in-jar".to_owned(),
        require_specialsource_normal_absolute_path(input)?.to_owned(),
        "--out-jar".to_owned(),
        require_specialsource_normal_absolute_path(output)?.to_owned(),
        "--srg-in".to_owned(),
        require_specialsource_normal_absolute_path(mapping)?.to_owned(),
        "--live".to_owned(),
    ])
}

fn create_specialsource_diagnostic_directory(root: &Path, directory: &Path) -> eyre::Result<()> {
    crate::source_projection::candidate_lock::checked_directory(root)?;
    let relative = directory
        .strip_prefix(root)
        .wrap_err("reviewed SpecialSource diagnostic directory is outside its original cache")?;
    let mut current = root.to_path_buf();
    for part in relative.components() {
        let std::path::Component::Normal(name) = part else {
            eyre::bail!("reviewed SpecialSource diagnostic path contains traversal");
        };
        current.push(name);
        match fs::symlink_metadata(&current) {
            Ok(_) => {}
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => {
                match fs::create_dir(&current) {
                    Ok(()) => {}
                    Err(error) if error.kind() == std::io::ErrorKind::AlreadyExists => {}
                    Err(error) => return Err(error.into()),
                }
            }
            Err(error) => return Err(error.into()),
        }
        crate::source_projection::candidate_lock::checked_directory(&current)?;
    }
    Ok(())
}

struct ReviewedSpecialSourceFile {
    path: PathBuf,
    held: File,
    identity: crate::file_identity::FileIdentity,
    content_hash: ContentHash,
    bytes: u64,
    modified: SystemTime,
}

impl ReviewedSpecialSourceFile {
    fn read(path: &Path, expected: &ContentHash) -> eyre::Result<Self> {
        require_specialsource_normal_absolute_path(path)?;
        let parent = path
            .parent()
            .ok_or_else(|| eyre::eyre!("reviewed SpecialSource input lacks a parent"))?;
        crate::source_projection::candidate_lock::checked_directory(parent)?;
        require_named_regular_file(path, &fs::symlink_metadata(path)?)?;
        let mut held = File::open(path)?;
        let before = held.metadata()?;
        require_named_regular_file(path, &before)?;
        eyre::ensure!(
            before.len() > 0 && before.len() <= SPECIALSOURCE_LOCAL_FILE_LIMIT,
            "reviewed SpecialSource input is empty or exceeds its bound"
        );
        let identity = crate::file_identity::file_identity(&held)?;
        let modified = before.modified()?;
        let mut bytes = Vec::new();
        (&mut held)
            .take(SPECIALSOURCE_LOCAL_FILE_LIMIT + 1)
            .read_to_end(&mut bytes)?;
        let after = held.metadata()?;
        eyre::ensure!(
            u64::try_from(bytes.len())? == before.len()
                && after.len() == before.len()
                && after.modified()? == modified
                && crate::file_identity::file_identity(&held)? == identity,
            "reviewed SpecialSource input changed during its verified read"
        );
        let current = File::open(path)?;
        eyre::ensure!(
            crate::file_identity::file_identity(&current)? == identity,
            "reviewed SpecialSource input path was replaced during its verified read"
        );
        let content_hash = ContentHash::from_bytes(&bytes, expected.algorithm);
        eyre::ensure!(
            content_hash == *expected,
            "reviewed SpecialSource input differs from its exact expected bytes"
        );
        Ok(Self {
            path: path.to_path_buf(),
            held,
            identity,
            content_hash,
            bytes: before.len(),
            modified,
        })
    }

    fn recheck(&self) -> eyre::Result<()> {
        let current = Self::read(&self.path, &self.content_hash)?;
        eyre::ensure!(
            current.identity == self.identity
                && current.bytes == self.bytes
                && current.modified == self.modified
                && crate::file_identity::file_identity(&self.held)? == self.identity,
            "reviewed SpecialSource input identity changed before launch"
        );
        Ok(())
    }
}

struct RetainedSpecialSourceScratch {
    path: PathBuf,
    held: File,
    identity: crate::file_identity::FileIdentity,
}

fn open_specialsource_directory(path: &Path) -> eyre::Result<File> {
    let mut options = OpenOptions::new();
    options.read(true);
    #[cfg(windows)]
    {
        use std::os::windows::fs::OpenOptionsExt as _;
        // The existing standard-library directory-open flag, not a new handle
        // wrapper or windows-rs feature. `File` owns and closes this handle.
        options.custom_flags(0x0200_0000) // FILE_FLAG_BACKUP_SEMANTICS
    };
    Ok(options.open(path)?)
}

impl RetainedSpecialSourceScratch {
    fn create() -> eyre::Result<Self> {
        let root = std::env::temp_dir();
        eyre::ensure!(
            specialsource_path_utf16_units(&root)? + 32 < SPECIALSOURCE_LAUNCH_CWD_LIMIT,
            "reviewed SpecialSource temp root cannot provide a bounded short cwd"
        );
        crate::source_projection::candidate_lock::checked_directory(&root)?;
        let owner = tempfile::Builder::new()
            .prefix("sfm-ss-")
            .tempdir_in(&root)?;
        // Keep every first-proof scratch directory, including errors/crashes.
        // No Drop cleanup can erase a JVM's hs_err report after a failure.
        let path = keep_specialsource_scratch(owner);
        tracing::info!(scratch = %path.display(), "specialsource_scratch_retained");
        eyre::ensure!(
            specialsource_path_utf16_units(&path)? < SPECIALSOURCE_LAUNCH_CWD_LIMIT,
            "reviewed SpecialSource created scratch exceeds the cwd bound; retained for inspection"
        );
        crate::source_projection::candidate_lock::checked_directory(&path)?;
        let held = open_specialsource_directory(&path)?;
        let identity = crate::file_identity::file_identity(&held)?;
        let scratch = Self {
            path,
            held,
            identity,
        };
        scratch.recheck_empty()?;
        Ok(scratch)
    }

    fn recheck_empty(&self) -> eyre::Result<()> {
        crate::source_projection::candidate_lock::checked_directory(&self.path)?;
        let current = open_specialsource_directory(&self.path)?;
        eyre::ensure!(
            crate::file_identity::file_identity(&self.held)? == self.identity
                && crate::file_identity::file_identity(&current)? == self.identity
                && fs::read_dir(&self.path)?.next().is_none(),
            "reviewed SpecialSource scratch was replaced or is no longer empty"
        );
        Ok(())
    }
}

fn keep_specialsource_scratch(owner: tempfile::TempDir) -> PathBuf {
    owner.keep()
}

#[derive(Clone, Debug, Facet)]
struct SpecialSourceLaunchReceipt {
    schema: String,
    invocation_id: String,
    phase: String,
    projection_key: String,
    context_identity: String,
    preparation_identity: String,
    recipe_id: String,
    source_lock_sha256: String,
    cache_identity: String,
    sdk_identity: String,
    java_executable: String,
    tool_coordinate: String,
    tool_content_hash: String,
    main_class: String,
    input_coordinate: String,
    input_content_hash: String,
    input_jar: String,
    mapping_content_hash: String,
    mapping_file: String,
    output_jar: String,
    diagnostic_cwd: String,
    diagnostic_cwd_utf16_units: usize,
    launch_cwd: String,
    launch_cwd_utf16_units: usize,
    scratch_kept: bool,
    original_argfile: String,
    transport_argfile: String,
    argfile_bytes: usize,
    argfile_sha256: String,
    child_created: bool,
    child_pid: Option<u32>,
    io_error_kind: Option<String>,
    raw_os_error: Option<i32>,
    child_status: Option<String>,
    cancelled: bool,
}

impl SpecialSourceLaunchReceipt {
    fn spawn_event(&self, report: &ProcessSpawnReport) -> Self {
        let mut receipt = self.clone();
        if report.child_pid.is_some() {
            "child_created"
        } else {
            "spawn_failed"
        }
        .clone_into(&mut receipt.phase);
        receipt.child_created = report.child_pid.is_some();
        receipt.child_pid = report.child_pid;
        receipt.io_error_kind.clone_from(&report.io_error_kind);
        receipt.raw_os_error = report.raw_os_error;
        receipt
    }

    fn persist(&self) -> eyre::Result<PathBuf> {
        let bytes = facet_json::to_string_pretty(self)?;
        // tempfile::keep uses a direct Win32 attribute call; retain the
        // checked extended-length spelling for filesystem operations only.
        let directory = crate::source_projection::candidate_lock::checked_directory(Path::new(
            &self.diagnostic_cwd,
        ))?;
        let owner = tempfile::Builder::new()
            .prefix("sfm-specialsource-launch-")
            .suffix(".json")
            .tempfile_in(&directory)?;
        // Retain even a partial receipt if a write fails; never overwrite a
        // receipt from another attempt and never replace its primary error.
        let (mut file, path) = owner.keep()?;
        file.write_all(bytes.as_bytes())?;
        file.sync_all()?;
        tracing::info!(receipt = %path.display(), phase = %self.phase,
            scratch = %self.launch_cwd, "specialsource_launch_receipt");
        Ok(path)
    }

    fn persist_secondary(&self) -> Option<eyre::Report> {
        self.persist().err().inspect(|error| {
            tracing::error!(phase = %self.phase, raw_os_error = self.raw_os_error,
                child_pid = self.child_pid, scratch = %self.launch_cwd,
                error = %error, "specialsource_launch_receipt_write_failed");
        })
    }
}

impl ExecutionContext<'_> {
    #[expect(
        clippy::too_many_lines,
        reason = "The exact recipe guard and launch receipt are kept in one reviewable caller."
    )]
    fn run_dependency_specialsource(
        &self,
        input: &ArtifactPlan,
        output: &Path,
        mapping: &Path,
        mapping_hash: ContentHash,
        coordinate: &MavenCoordinate,
        diagnostic_dir: &Path,
    ) -> eyre::Result<()> {
        let Some(project) = self.plan.named_project().filter(|project| {
            reviewed_specialsource_project_recipe(project.project().receipt()).is_some()
        }) else {
            if self.plan.identity.development_target().is_some() {
                // The captured profile retains its exact tool and arguments.
                // Only the process cwd is transported: CreateProcess rejects
                // deep projection paths even with a verbatim prefix. All three
                // SpecialSource file arguments are validated absolute paths.
                let args = specialsource_dependency_arguments(&input.cache_path, output, mapping)?;
                create_specialsource_diagnostic_directory(&self.plan.cache_dir, diagnostic_dir)?;
                let scratch = RetainedSpecialSourceScratch::create()?;
                return self.run_java_tool_with_classpath_and_scratch(
                    "tool-specialsource",
                    &[],
                    &[],
                    &args,
                    diagnostic_dir,
                    Some(&scratch),
                );
            }
            // Reconstruct the original caller, including its path spelling.
            // Specialized validation must not change any legacy/other recipe.
            return self.run_java_tool_with_classpath(
                "tool-specialsource",
                &[],
                &[],
                &[
                    "--in-jar".to_owned(),
                    input.cache_path.display().to_string(),
                    "--out-jar".to_owned(),
                    output.display().to_string(),
                    "--srg-in".to_owned(),
                    mapping.display().to_string(),
                    "--live".to_owned(),
                ],
                diagnostic_dir,
            );
        };
        let args = specialsource_dependency_arguments(&input.cache_path, output, mapping)?;
        self.bail_if_cancelled()?;
        let ownership = project.project().receipt();
        let prepared = project.dependencies();
        let released = &project.receipt().released_inputs;
        let recipe = reviewed_specialsource_project_recipe(ownership).ok_or_else(|| {
            eyre::eyre!("reviewed SpecialSource projection lost its exact recipe")
        })?;
        require_reviewed_specialsource_identity(
            recipe,
            ownership,
            self.plan.minecraft_version.as_str(),
            &self.plan.loader_toolchain.kind,
            (&released.recipe_id, &prepared.receipt().source_lock_sha256),
            (self.plan.java.major_version, self.plan.java_release),
        )?;
        project.recheck(false)?;
        self.plan.recheck_named_sdk()?;
        require_specialsource_normal_absolute_path(&self.plan.java.executable)?;
        #[cfg(windows)]
        eyre::ensure!(
            specialsource_path_utf16_units(&self.plan.java.executable)? < 260,
            "reviewed SpecialSource requires a verified normal short SDK executable spelling; no executable normalization is performed"
        );
        refuse_specialsource_jvm_overrides(|name| std::env::var_os(name))?;
        let tool = self.artifact(ArtifactId::from("tool-specialsource"))?;
        let request = prepared.coordinate(SPECIALSOURCE_REVIEWED_COORDINATE, false)?;
        eyre::ensure!(
            tool.coordinate.as_deref() == Some(SPECIALSOURCE_REVIEWED_COORDINATE)
                && request.original_content_hash() == SPECIALSOURCE_REVIEWED_HASH
                && tool.provenance.hash.to_string() == SPECIALSOURCE_REVIEWED_HASH,
            "reviewed SpecialSource tool differs from its original pin"
        );
        let coordinate_text = coordinate.to_string();
        let input_request = prepared.coordinate(&coordinate_text, false)?;
        eyre::ensure!(
            input.coordinate.as_deref() == Some(coordinate_text.as_str())
                && input.provenance.hash.to_string() == input_request.original_content_hash(),
            "reviewed SpecialSource input differs from its original prepared artifact"
        );
        let original_output_root = self.plan.cache_dir.join("dependencies");
        let expected_output = specialsource_dependency_output_path(
            &original_output_root,
            &input.provenance.hash,
            &mapping_hash,
            coordinate,
        );
        let expected_diagnostic = original_output_root
            .join("remap-work")
            .join(safe_path_segment(&coordinate.file_name()));
        let expected_mapping = recipe.mapping_path(&self.plan.cache_dir);
        eyre::ensure!(
            output == expected_output
                && diagnostic_dir == expected_diagnostic
                && mapping == expected_mapping,
            "reviewed SpecialSource output/mapping/diagnostic paths differ from the original context-owned recipe"
        );
        self.assert_allowed_input(&tool.cache_path)?;
        self.assert_allowed_input(&input.cache_path)?;
        self.assert_allowed_input(mapping)?;
        let _tool_lock = acquire_artifact_path_read_lock_cancellable(
            &tool.cache_path,
            &self.cancellation_token,
        )?;
        let _input_lock = acquire_artifact_path_read_lock_cancellable(
            &input.cache_path,
            &self.cancellation_token,
        )?;
        let tool_hash =
            ContentHash::parse(SPECIALSOURCE_REVIEWED_HASH).map_err(|error| eyre::eyre!(error))?;
        let tool_file = ReviewedSpecialSourceFile::read(&tool.cache_path, &tool_hash)?;
        let input_file =
            ReviewedSpecialSourceFile::read(&input.cache_path, &input.provenance.hash)?;
        let mapping_file = ReviewedSpecialSourceFile::read(mapping, &mapping_hash)?;
        let main_class = read_main_class(&tool.cache_path)?;
        eyre::ensure!(
            main_class == SPECIALSOURCE_REVIEWED_MAIN,
            "reviewed SpecialSource Main-Class changed"
        );
        require_specialsource_normal_absolute_path(diagnostic_dir)?;
        crate::source_projection::candidate_lock::checked_directory(&original_output_root)?;
        create_specialsource_diagnostic_directory(&original_output_root, diagnostic_dir)?;
        let scratch = RetainedSpecialSourceScratch::create()?;
        let cache_identity = self
            .plan
            .cache_dir
            .file_name()
            .and_then(|name| name.to_str())
            .ok_or_else(|| eyre::eyre!("reviewed SpecialSource cache identity is unavailable"))?;
        eyre::ensure!(
            cache_identity.len() == 64
                && cache_identity.bytes().all(|byte| byte.is_ascii_hexdigit()),
            "reviewed SpecialSource requires the original named cache digest"
        );
        let classpath_arg = join_classpath(std::slice::from_ref(&tool.cache_path));
        let mut java_args = vec!["-cp".to_owned(), classpath_arg.clone(), main_class.clone()];
        java_args.extend(args.iter().cloned());
        let argfile_contents = java_args
            .into_iter()
            .map(escape_argfile_arg)
            .collect::<Vec<_>>()
            .join("\n");
        let original_argfile = diagnostic_dir.join("tool-specialsource.java.args");
        fs::write(&original_argfile, &argfile_contents)?;
        let transport = java_tool_argfile_transport(argfile_contents.as_bytes())?;
        let mut receipt = SpecialSourceLaunchReceipt {
            schema: "sfm:java_tool_launch_receipt@1".to_owned(),
            invocation_id: scratch
                .path
                .file_name()
                .and_then(|name| name.to_str())
                .ok_or_else(|| eyre::eyre!("scratch identity is not Unicode"))?
                .to_owned(),
            phase: "prepared".to_owned(),
            projection_key: ownership.projection_key.clone(),
            context_identity: ownership.context_identity.clone(),
            preparation_identity: project.receipt().preparation_identity()?,
            recipe_id: released.recipe_id.clone(),
            source_lock_sha256: prepared.receipt().source_lock_sha256.clone(),
            cache_identity: cache_identity.to_owned(),
            sdk_identity: self.plan.java.cache_identity(),
            java_executable: require_specialsource_normal_absolute_path(
                &self.plan.java.executable,
            )?
            .to_owned(),
            tool_coordinate: SPECIALSOURCE_REVIEWED_COORDINATE.to_owned(),
            tool_content_hash: tool_hash.to_string(),
            main_class: main_class.clone(),
            input_coordinate: coordinate_text,
            input_content_hash: input.provenance.hash.to_string(),
            input_jar: require_specialsource_normal_absolute_path(&input.cache_path)?.to_owned(),
            mapping_content_hash: mapping_hash.to_string(),
            mapping_file: require_specialsource_normal_absolute_path(mapping)?.to_owned(),
            output_jar: require_specialsource_normal_absolute_path(output)?.to_owned(),
            diagnostic_cwd: require_specialsource_normal_absolute_path(diagnostic_dir)?.to_owned(),
            diagnostic_cwd_utf16_units: specialsource_path_utf16_units(diagnostic_dir)?,
            launch_cwd: require_specialsource_normal_absolute_path(&scratch.path)?.to_owned(),
            launch_cwd_utf16_units: specialsource_path_utf16_units(&scratch.path)?,
            scratch_kept: true,
            original_argfile: require_specialsource_normal_absolute_path(&original_argfile)?
                .to_owned(),
            transport_argfile: require_specialsource_normal_absolute_path(transport.path())?
                .to_owned(),
            argfile_bytes: argfile_contents.len(),
            argfile_sha256: crate::source_projection::provenance::sha256(
                argfile_contents.as_bytes(),
            ),
            child_created: false,
            child_pid: None,
            io_error_kind: None,
            raw_os_error: None,
            child_status: None,
            cancelled: false,
        };
        receipt.persist()?;
        let preflight = (|| {
            self.bail_if_cancelled()?;
            project.recheck(false)?;
            self.plan.recheck_named_sdk()?;
            refuse_specialsource_jvm_overrides(|name| std::env::var_os(name))?;
            tool_file.recheck()?;
            input_file.recheck()?;
            mapping_file.recheck()?;
            scratch.recheck_empty()?;
            eyre::ensure!(
                fs::read(transport.path())? == argfile_contents.as_bytes(),
                "reviewed SpecialSource argument transport changed"
            );
            Ok::<(), eyre::Report>(())
        })();
        if let Err(error) = preflight {
            "preflight_refused".clone_into(&mut receipt.phase);
            let _secondary = receipt.persist_secondary();
            return Err(error);
        }
        let started = Instant::now();
        let log_path = diagnostic_dir.join("console.log");
        let mut command = Command::new(&self.plan.java.executable);
        command
            .arg(java_tool_argfile_argument(transport.path())?)
            .current_dir(&scratch.path);
        let mut observer_error = None;
        let output = run_command_capture_output_observing_spawn(
            &self.cancellation_token,
            &mut command,
            "tool-specialsource",
            |report| {
                receipt = receipt.spawn_event(report);
                observer_error = receipt.persist_secondary();
            },
        );
        let output = match output {
            Ok(output) => output,
            Err(error) => {
                if receipt.child_created {
                    "capture_failed".clone_into(&mut receipt.phase);
                    if let Some(io_error) = error.downcast_ref::<std::io::Error>() {
                        receipt.io_error_kind = Some(format!("{:?}", io_error.kind()));
                        receipt.raw_os_error = io_error.raw_os_error();
                    }
                    let _secondary = receipt.persist_secondary();
                } else if receipt.phase == "prepared" {
                    if self.cancellation_token.is_cancelled() {
                        "cancelled"
                    } else {
                        "preflight_refused"
                    }
                    .clone_into(&mut receipt.phase);
                    receipt.cancelled = self.cancellation_token.is_cancelled();
                    let _secondary = receipt.persist_secondary();
                }
                // Preserve the original std::io::Error chain even if the
                // secondary receipt write failed at the spawn boundary.
                return Err(error).wrap_err(recipe.failure_context);
            }
        };
        if output.cancelled {
            "cancelled"
        } else {
            "child_exited"
        }
        .clone_into(&mut receipt.phase);
        receipt.child_status = Some(output.status.to_string());
        receipt.cancelled = output.cancelled;
        let final_receipt_error = receipt.persist_secondary();
        trace_subprocess_bytes(
            self.plan,
            "java-tool",
            "tool-specialsource",
            "stdout",
            &output.stdout,
        );
        trace_subprocess_bytes(
            self.plan,
            "java-tool",
            "tool-specialsource",
            "stderr",
            &output.stderr,
        );
        write_java_tool_console_log(
            &log_path,
            "tool-specialsource",
            &main_class,
            started.elapsed().as_millis(),
            &classpath_arg,
            &args,
            &output,
        )?;
        eyre::ensure!(
            !output.cancelled,
            "Reviewed SpecialSource was cancelled; scratch retained at {}",
            scratch.path.display()
        );
        eyre::ensure!(
            output.status.success(),
            "Reviewed SpecialSource failed with {}; see {} and retained scratch {}",
            output.status,
            log_path.display(),
            scratch.path.display()
        );
        if let Some(error) = observer_error.or(final_receipt_error) {
            return Err(error).wrap_err(
                "SpecialSource completed but its durable launch receipt could not be persisted",
            );
        }
        Ok(())
    }
}

#[cfg(test)]
mod reviewed_specialsource_launch_tests {
    use super::*;

    fn is_reviewed_specialsource_projection(key: &str) -> bool {
        reviewed_specialsource_recipe(key).is_some()
    }

    #[test]
    fn exact_projection_selection_does_not_broaden_other_targets_or_dev() {
        assert!(is_reviewed_specialsource_projection("sfm-4.34.0/mc-1.19.2"));
        assert!(is_reviewed_specialsource_projection("sfm-4.34.0/mc-1.19.4"));
        assert!(is_reviewed_specialsource_projection("sfm-4.34.0/mc-1.20"));
        assert!(is_reviewed_specialsource_projection("sfm-4.34.0/mc-1.20.1"));
        for target in ["1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1", "26.1.2"] {
            assert!(!is_reviewed_specialsource_projection(&format!(
                "sfm-4.34.0/mc-{target}"
            )));
        }
        assert!(!is_reviewed_specialsource_projection("sfm-dev/mc-1.19.2"));
        assert!(!is_reviewed_specialsource_projection("sfm-dev/mc-1.19.4"));
        assert!(!is_reviewed_specialsource_projection("sfm-dev/mc-1.20"));
        assert!(!is_reviewed_specialsource_projection("sfm-dev/mc-1.20.1"));
        assert!(!is_reviewed_specialsource_projection("1.19.2"));
        assert!(!is_reviewed_specialsource_projection("1.19.4"));
        assert!(!is_reviewed_specialsource_projection("1.20"));
        assert!(!is_reviewed_specialsource_projection(""));
    }

    fn ownership_fixture(
        recipe: ReviewedSpecialSourceRecipe,
    ) -> crate::source_projection::catalog_owned_project::CatalogOwnedProjectReceipt {
        use crate::source_projection::catalog_owned_project::CatalogOwnedProjectReceipt;
        use crate::source_projection::projection_catalog::ProjectionEnvironment;

        // Pure guard input only: this is not a checked project or effect permit.
        CatalogOwnedProjectReceipt {
            schema: "sfm:catalog_owned_project@1".to_owned(),
            scope: "synthetic-no-project-or-effects".to_owned(),
            projection_key: recipe.projection_key.to_owned(),
            environment: ProjectionEnvironment::Release,
            target_id: recipe.target.to_owned(),
            minecraft_version: recipe.target.to_owned(),
            java_major: 17,
            loader: "forge".to_owned(),
            context_identity: "synthetic-context".to_owned(),
            project_dir: format!("platform/minecraft/projections/{}", recipe.projection_key),
            catalog_sha256: "synthetic-catalog".to_owned(),
            feature_definitions_sha256: "synthetic-features".to_owned(),
            project_inputs_sha256: "synthetic-inputs".to_owned(),
            provenance_sha256: "synthetic-provenance".to_owned(),
            authored_source_inventory_sha256: "synthetic-authored".to_owned(),
            generated_source_inventory_sha256: "synthetic-generated".to_owned(),
            files: BTreeMap::new(),
        }
    }

    #[test]
    fn nested_partial_release_uses_the_same_exact_reviewed_recipe() {
        let recipe = reviewed_specialsource_recipe(SPECIALSOURCE_RELEASE_1192_KEY).unwrap();
        let mut ownership = ownership_fixture(recipe);
        ownership.projection_key = "sfm-review/regex-overlap/mc-1.19.2".to_owned();
        ownership.project_dir = format!(
            "platform/minecraft/projections/{}",
            ownership.projection_key
        );
        assert_eq!(
            reviewed_specialsource_project_recipe(&ownership),
            Some(recipe)
        );
        require_reviewed_specialsource_identity(
            recipe,
            &ownership,
            "1.19.2",
            &LoaderToolchainKind::ForgeGradleForge,
            (recipe.recipe_id, recipe.source_lock_sha256),
            (17, 17),
        )
        .unwrap();
    }

    #[test]
    fn partial_release_recipe_selection_uses_context_not_directory_spelling() {
        use crate::source_projection::projection_catalog::ProjectionEnvironment;

        for key in [
            SPECIALSOURCE_RELEASE_1192_KEY,
            SPECIALSOURCE_RELEASE_1194_KEY,
            SPECIALSOURCE_RELEASE_120_KEY,
            SPECIALSOURCE_RELEASE_1201_KEY,
        ] {
            let recipe = reviewed_specialsource_recipe(key).unwrap();
            let mut ownership = ownership_fixture(recipe);
            // Even a version-looking directory name must not override context.
            ownership.projection_key = "experiment/mc-26.1.2".to_owned();
            ownership.project_dir =
                "platform/minecraft/projections/experiment/mc-26.1.2".to_owned();
            assert_eq!(
                reviewed_specialsource_project_recipe(&ownership),
                Some(recipe)
            );
            let validate = |owner| {
                require_reviewed_specialsource_identity(
                    recipe,
                    owner,
                    recipe.target,
                    &recipe.loader_kind(),
                    (recipe.recipe_id, recipe.source_lock_sha256),
                    (17, 17),
                )
            };
            validate(&ownership).unwrap();
            let mut changed = ownership.clone();
            changed.project_dir.push_str("/other");
            assert!(validate(&changed).is_err());
            let mut changed = ownership.clone();
            changed.projection_key = "experiment/../escape".to_owned();
            changed.project_dir = "platform/minecraft/projections/experiment/../escape".to_owned();
            assert!(validate(&changed).is_err());
            let mut changed = ownership.clone();
            changed.environment = ProjectionEnvironment::Dev;
            assert!(reviewed_specialsource_project_recipe(&changed).is_none());
            assert!(validate(&changed).is_err());
            let mut changed = ownership.clone();
            changed.target_id = "26.1.2".to_owned();
            assert!(reviewed_specialsource_project_recipe(&changed).is_none());
            assert!(validate(&changed).is_err());
        }
    }

    #[test]
    fn all_reviewed_recipes_match_exact_original_lock_and_specialsource_bytes() {
        #[derive(Facet)]
        struct FrozenToolRow {
            coordinate: String,
            hash: String,
        }
        #[derive(Facet)]
        struct FrozenLock {
            artifacts: Vec<FrozenToolRow>,
        }
        let witnesses: [(&str, &[u8]); 4] = [
            (
                SPECIALSOURCE_RELEASE_1192_KEY,
                include_bytes!(
                    "../../../../minecraft/core-liquid-template/build/lockfiles/1.19.2/schema-2.json"
                ),
            ),
            (
                SPECIALSOURCE_RELEASE_1194_KEY,
                include_bytes!(
                    "../../../../minecraft/core-liquid-template/build/lockfiles/1.19.4/schema-2.json"
                ),
            ),
            (
                SPECIALSOURCE_RELEASE_120_KEY,
                include_bytes!(
                    "../../../../minecraft/core-liquid-template/build/lockfiles/1.20/schema-2.json"
                ),
            ),
            (
                SPECIALSOURCE_RELEASE_1201_KEY,
                include_bytes!(
                    "../../../../minecraft/core-liquid-template/build/lockfiles/1.20.1/schema-2.json"
                ),
            ),
        ];
        for (key, bytes) in witnesses {
            let recipe = reviewed_specialsource_recipe(key).unwrap();
            assert_eq!(
                crate::source_projection::provenance::sha256(bytes),
                recipe.source_lock_sha256
            );
            let lock: FrozenLock = facet_json::from_slice(bytes).unwrap();
            let tools = lock
                .artifacts
                .iter()
                .filter(|row| row.coordinate == SPECIALSOURCE_REVIEWED_COORDINATE)
                .collect::<Vec<_>>();
            assert_eq!(tools.len(), 1);
            assert_eq!(tools[0].hash, SPECIALSOURCE_REVIEWED_HASH);
        }
    }

    #[test]
    fn reviewed_identity_refuses_each_mismatched_field_before_any_effect() {
        use crate::source_projection::projection_catalog::ProjectionEnvironment;

        for key in [
            SPECIALSOURCE_RELEASE_1192_KEY,
            SPECIALSOURCE_RELEASE_1194_KEY,
            SPECIALSOURCE_RELEASE_120_KEY,
        ] {
            let recipe = reviewed_specialsource_recipe(key).unwrap();
            let original = ownership_fixture(recipe);
            let other_target = if recipe.target == "1.20" {
                "1.19.2"
            } else {
                "1.20"
            };
            let validate = |ownership: &crate::source_projection::catalog_owned_project::CatalogOwnedProjectReceipt,
                            plan_target: &str,
                            loader: &LoaderToolchainKind,
                            released: (&str, &str),
                            sdk: (u32, u32)| {
                require_reviewed_specialsource_identity(
                    recipe, ownership, plan_target, loader, released, sdk,
                )
            };
            let released = (recipe.recipe_id, recipe.source_lock_sha256);
            assert!(
                validate(
                    &original,
                    recipe.target,
                    &LoaderToolchainKind::ForgeGradleForge,
                    released,
                    (17, 17)
                )
                .is_ok()
            );
            let mut changed = original.clone();
            changed.projection_key = "sfm-dev/mc-1.19.4".to_owned();
            assert!(
                validate(
                    &changed,
                    recipe.target,
                    &LoaderToolchainKind::ForgeGradleForge,
                    released,
                    (17, 17)
                )
                .is_err()
            );
            let mut changed = original.clone();
            changed.environment = ProjectionEnvironment::Dev;
            assert!(
                validate(
                    &changed,
                    recipe.target,
                    &LoaderToolchainKind::ForgeGradleForge,
                    released,
                    (17, 17)
                )
                .is_err()
            );
            let mut changed = original.clone();
            changed.target_id = other_target.to_owned();
            assert!(
                validate(
                    &changed,
                    recipe.target,
                    &LoaderToolchainKind::ForgeGradleForge,
                    released,
                    (17, 17)
                )
                .is_err()
            );
            let mut changed = original.clone();
            changed.minecraft_version = other_target.to_owned();
            assert!(
                validate(
                    &changed,
                    recipe.target,
                    &LoaderToolchainKind::ForgeGradleForge,
                    released,
                    (17, 17)
                )
                .is_err()
            );
            assert!(
                validate(
                    &original,
                    other_target,
                    &LoaderToolchainKind::ForgeGradleForge,
                    released,
                    (17, 17)
                )
                .is_err()
            );
            assert!(
                validate(
                    &original,
                    recipe.target,
                    &LoaderToolchainKind::ForgeGradleNeoForgeGroup,
                    released,
                    (17, 17)
                )
                .is_err()
            );
            assert!(
                validate(
                    &original,
                    recipe.target,
                    &LoaderToolchainKind::NeoGradleUserdev,
                    released,
                    (17, 17)
                )
                .is_err()
            );
            assert!(
                validate(
                    &original,
                    recipe.target,
                    &LoaderToolchainKind::ForgeGradleForge,
                    ("unknown-recipe", recipe.source_lock_sha256),
                    (17, 17)
                )
                .is_err()
            );
            assert!(
                validate(
                    &original,
                    recipe.target,
                    &LoaderToolchainKind::ForgeGradleForge,
                    (recipe.recipe_id, "unknown-lock"),
                    (17, 17)
                )
                .is_err()
            );
            assert!(
                validate(
                    &original,
                    recipe.target,
                    &LoaderToolchainKind::ForgeGradleForge,
                    released,
                    (21, 17)
                )
                .is_err()
            );
            assert!(
                validate(
                    &original,
                    recipe.target,
                    &LoaderToolchainKind::ForgeGradleForge,
                    released,
                    (17, 21)
                )
                .is_err()
            );
        }
    }

    #[test]
    fn reviewed_mapping_and_failure_context_follow_the_exact_target() {
        let cache = std::env::temp_dir().join("synthetic-cache-no-files-opened");
        for key in [
            SPECIALSOURCE_RELEASE_1192_KEY,
            SPECIALSOURCE_RELEASE_1194_KEY,
            SPECIALSOURCE_RELEASE_120_KEY,
            SPECIALSOURCE_RELEASE_1201_KEY,
        ] {
            let recipe = reviewed_specialsource_recipe(key).unwrap();
            assert_eq!(
                recipe.mapping_path(&cache),
                cache.join(format!(
                    "forge/{}/mappings/srg_to_official.tsrg",
                    recipe.target
                ))
            );
            let other = if recipe.target == "1.19.2" {
                "1.19.4"
            } else {
                "1.19.2"
            };
            assert_ne!(
                recipe.mapping_path(&cache),
                cache.join(format!("forge/{other}/mappings/srg_to_official.tsrg"))
            );
            assert_eq!(
                recipe.failure_context,
                format!(
                    "Failed reviewed original{} SpecialSource launch",
                    recipe.target.replace('.', "")
                )
            );
        }
    }

    #[test]
    fn original120_adapter_does_not_accept_another_released_recipe_or_lock() {
        let recipe = reviewed_specialsource_recipe(SPECIALSOURCE_RELEASE_120_KEY).unwrap();
        let ownership = ownership_fixture(recipe);
        for other_key in [
            SPECIALSOURCE_RELEASE_1192_KEY,
            SPECIALSOURCE_RELEASE_1194_KEY,
        ] {
            let other = reviewed_specialsource_recipe(other_key).unwrap();
            assert!(
                require_reviewed_specialsource_identity(
                    recipe,
                    &ownership,
                    "1.20",
                    &LoaderToolchainKind::ForgeGradleForge,
                    (other.recipe_id, other.source_lock_sha256),
                    (17, 17),
                )
                .is_err()
            );
        }
        assert!(reviewed_specialsource_recipe("sfm-4.34.0/mc-1.20.2").is_none());
        assert!(reviewed_specialsource_recipe("sfm-dev/mc-1.20").is_none());
        assert!(reviewed_specialsource_recipe("sfm-4.34.0/mc-1.20/").is_none());
    }

    #[test]
    fn original1201_adapter_requires_its_exact_neoforge_group_recipe() {
        let recipe = reviewed_specialsource_recipe(SPECIALSOURCE_RELEASE_1201_KEY).unwrap();
        let ownership = ownership_fixture(recipe);
        let validate = |owner: &crate::source_projection::catalog_owned_project::CatalogOwnedProjectReceipt,
                        target: &str,
                        loader: &LoaderToolchainKind,
                        identity: (&str, &str),
                        sdk: (u32, u32)| {
            require_reviewed_specialsource_identity(recipe, owner, target, loader, identity, sdk)
        };
        let exact = (recipe.recipe_id, recipe.source_lock_sha256);
        let loader = LoaderToolchainKind::ForgeGradleNeoForgeGroup;
        assert!(validate(&ownership, "1.20.1", &loader, exact, (17, 17)).is_ok());
        for wrong in [
            LoaderToolchainKind::ForgeGradleForge,
            LoaderToolchainKind::NeoGradleUserdev,
        ] {
            assert!(validate(&ownership, "1.20.1", &wrong, exact, (17, 17)).is_err());
        }
        for target in ["1.20", "1.20.2"] {
            assert!(validate(&ownership, target, &loader, exact, (17, 17)).is_err());
        }
        for other_key in [
            SPECIALSOURCE_RELEASE_1192_KEY,
            SPECIALSOURCE_RELEASE_1194_KEY,
            SPECIALSOURCE_RELEASE_120_KEY,
        ] {
            let other = reviewed_specialsource_recipe(other_key).unwrap();
            assert!(
                validate(
                    &ownership,
                    "1.20.1",
                    &loader,
                    (other.recipe_id, recipe.source_lock_sha256),
                    (17, 17)
                )
                .is_err()
            );
            assert!(
                validate(
                    &ownership,
                    "1.20.1",
                    &loader,
                    (recipe.recipe_id, other.source_lock_sha256),
                    (17, 17)
                )
                .is_err()
            );
        }
        for sdk in [(21, 17), (17, 21)] {
            assert!(validate(&ownership, "1.20.1", &loader, exact, sdk).is_err());
        }
        let mut changed = ownership.clone();
        changed.environment =
            crate::source_projection::projection_catalog::ProjectionEnvironment::Dev;
        assert!(validate(&changed, "1.20.1", &loader, exact, (17, 17)).is_err());
        changed = ownership.clone();
        changed.projection_key = "sfm-dev/mc-1.20.1".to_owned();
        assert!(validate(&changed, "1.20.1", &loader, exact, (17, 17)).is_err());
        changed = ownership.clone();
        changed.target_id = "1.20".to_owned();
        assert!(validate(&changed, "1.20.1", &loader, exact, (17, 17)).is_err());
        changed = ownership;
        changed.minecraft_version = "1.20".to_owned();
        assert!(validate(&changed, "1.20.1", &loader, exact, (17, 17)).is_err());
    }

    #[cfg(windows)]
    #[test]
    fn original1201_observed_failed_cwd_preserves_long_mapping_and_output_arguments() {
        // The real named release1201 launch failed before child creation with
        // OS error267 at 268 UTF-16 units. No machine-specific path is needed.
        let root = Path::new(r"D:\fixture");
        let suffix = r"dependencies\remap-work\the-one-probe-245211-4629624.jar";
        let padding = "p".repeat(268 - root.to_str().unwrap().len() - suffix.len() - 2);
        let cache = root.join(padding);
        let failed_cwd = cache.join(suffix);
        assert_eq!(specialsource_path_utf16_units(&failed_cwd).unwrap(), 268);
        assert!(
            specialsource_path_utf16_units(&failed_cwd).unwrap() >= SPECIALSOURCE_LAUNCH_CWD_LIMIT
        );
        let recipe = reviewed_specialsource_recipe(SPECIALSOURCE_RELEASE_1201_KEY).unwrap();
        let mapping = recipe.mapping_path(&cache);
        assert!(mapping.ends_with("forge/1.20.1/mappings/srg_to_official.tsrg"));
        let input = Path::new(r"C:\fixture\locked-the-one-probe-245211-4629624.jar");
        let output = cache.join("dependencies/specialsource/fixture.jar");
        assert_eq!(
            specialsource_dependency_arguments(input, &output, &mapping).unwrap(),
            [
                "--in-jar",
                input.to_str().unwrap(),
                "--out-jar",
                output.to_str().unwrap(),
                "--srg-in",
                mapping.to_str().unwrap(),
                "--live",
            ]
        );
    }

    #[cfg(windows)]
    #[test]
    fn original120_observed_failed_cwd_length_keeps_original_arguments() {
        // The actual first exact-release120 attempt reported OS error267 at
        // 266 UTF-16 units. Keep its machine-specific path in local logs only.
        // This synthetic path regression does not spawn Java or open a cache.
        let root = Path::new(r"D:\fixture");
        let suffix = r"dependencies\remap-work\the-one-probe-245211-4579432.jar";
        let padding = "p".repeat(266 - root.to_str().unwrap().len() - suffix.len() - 2);
        let cache = root.join(padding);
        let failed_cwd = cache.join(suffix);
        assert_eq!(specialsource_path_utf16_units(&failed_cwd).unwrap(), 266);
        assert!(
            specialsource_path_utf16_units(&failed_cwd).unwrap() >= SPECIALSOURCE_LAUNCH_CWD_LIMIT
        );
        let recipe = reviewed_specialsource_recipe(SPECIALSOURCE_RELEASE_120_KEY).unwrap();
        let mapping = recipe.mapping_path(&cache);
        assert!(mapping.ends_with("forge/1.20/mappings/srg_to_official.tsrg"));
        let input = Path::new(r"C:\fixture\locked-the-one-probe-245211-4579432.jar");
        let output = cache.join("dependencies/specialsource/fixture.jar");
        let args = specialsource_dependency_arguments(input, &output, &mapping).unwrap();
        assert_eq!(args.len(), 7);
        assert_eq!(args[0], "--in-jar");
        assert_eq!(args[1], input.to_str().unwrap());
        assert_eq!(args[2], "--out-jar");
        assert_eq!(args[3], output.to_str().unwrap());
        assert_eq!(args[4], "--srg-in");
        assert_eq!(args[5], mapping.to_str().unwrap());
        assert_eq!(args[6], "--live");
    }

    #[test]
    fn nonempty_override_refuses_without_exposing_values() {
        for refused in SPECIALSOURCE_JVM_OVERRIDE_NAMES {
            let error = refuse_specialsource_jvm_overrides(|name| {
                (name == refused)
                    .then(|| std::ffi::OsString::from("private-value-not-for-a-receipt"))
            })
            .unwrap_err();
            assert!(error.to_string().contains(refused));
            assert!(
                !error
                    .to_string()
                    .contains("private-value-not-for-a-receipt")
            );
        }
        assert!(refuse_specialsource_jvm_overrides(|_| None).is_ok());
        assert!(refuse_specialsource_jvm_overrides(|_| Some(std::ffi::OsString::new())).is_ok());
    }

    #[test]
    fn relative_paths_and_control_characters_refuse() {
        assert!(require_specialsource_normal_absolute_path(Path::new("relative.jar")).is_err());
        let root = std::env::temp_dir();
        assert!(require_specialsource_normal_absolute_path(&root.join("bad\n.jar")).is_err());
        assert!(require_specialsource_normal_absolute_path(&root.join("bad\r.jar")).is_err());
    }

    #[test]
    fn fixed_arguments_keep_original_order_and_long_absolute_output() {
        let root = std::env::temp_dir();
        let input = root.join("input.jar");
        let output = root.join("nested".repeat(55)).join("output.jar");
        let mapping = root.join("mapping.tsrg");
        let args = specialsource_dependency_arguments(&input, &output, &mapping).unwrap();
        assert_eq!(
            args,
            [
                "--in-jar",
                input.to_str().unwrap(),
                "--out-jar",
                output.to_str().unwrap(),
                "--srg-in",
                mapping.to_str().unwrap(),
                "--live"
            ]
        );
        assert!(specialsource_path_utf16_units(&output).unwrap() > 260);
    }

    #[cfg(windows)]
    #[test]
    fn verbatim_executable_or_argument_spelling_is_not_silently_changed() {
        assert!(
            require_specialsource_normal_absolute_path(Path::new(r"\\?\C:\sdk\bin\java.exe"))
                .is_err()
        );
        assert!(
            require_specialsource_normal_absolute_path(Path::new(r"\\?\C:\input.jar")).is_err()
        );
    }

    #[test]
    fn verified_file_rejects_directory_empty_tampering_and_replacement() {
        let temp = tempfile::tempdir().unwrap();
        let path = temp.path().join("input.bin");
        let hash = ContentHash::from_bytes(b"abc", ContentHashAlgorithm::Blake3);
        assert!(ReviewedSpecialSourceFile::read(temp.path(), &hash).is_err());
        fs::write(&path, b"").unwrap();
        assert!(ReviewedSpecialSourceFile::read(&path, &hash).is_err());
        fs::write(&path, b"abc").unwrap();
        let captured = ReviewedSpecialSourceFile::read(&path, &hash).unwrap();
        fs::write(&path, b"xyz").unwrap();
        assert!(captured.recheck().is_err());
        fs::write(&path, b"abc").unwrap();
        let captured = ReviewedSpecialSourceFile::read(&path, &hash).unwrap();
        fs::rename(&path, temp.path().join("moved.bin")).unwrap();
        fs::write(&path, b"abc").unwrap();
        assert!(captured.recheck().is_err());
    }

    fn receipt_fixture(directory: &Path) -> SpecialSourceLaunchReceipt {
        let path = directory.to_str().unwrap().to_owned();
        SpecialSourceLaunchReceipt {
            schema: "sfm:java_tool_launch_receipt@1".to_owned(),
            invocation_id: "synthetic-no-child-created".to_owned(),
            phase: "prepared".to_owned(),
            projection_key: SPECIALSOURCE_RELEASE_1192_KEY.to_owned(),
            context_identity: "synthetic-context".to_owned(),
            preparation_identity: "synthetic-preparation".to_owned(),
            recipe_id: SPECIALSOURCE_RELEASE_1192_RECIPE.to_owned(),
            source_lock_sha256: SPECIALSOURCE_RELEASE_1192_LOCK.to_owned(),
            cache_identity: "0".repeat(64),
            sdk_identity: "synthetic-no-sdk-executed".to_owned(),
            java_executable: directory
                .join("java-not-executed")
                .to_str()
                .unwrap()
                .to_owned(),
            tool_coordinate: SPECIALSOURCE_REVIEWED_COORDINATE.to_owned(),
            tool_content_hash: SPECIALSOURCE_REVIEWED_HASH.to_owned(),
            main_class: SPECIALSOURCE_REVIEWED_MAIN.to_owned(),
            input_coordinate: "synthetic:input:1".to_owned(),
            input_content_hash: "synthetic-input-hash".to_owned(),
            input_jar: directory
                .join("input-not-opened.jar")
                .to_str()
                .unwrap()
                .to_owned(),
            mapping_content_hash: "synthetic-mapping-hash".to_owned(),
            mapping_file: directory
                .join("mappings-not-opened.tsrg")
                .to_str()
                .unwrap()
                .to_owned(),
            output_jar: directory
                .join("output-not-created.jar")
                .to_str()
                .unwrap()
                .to_owned(),
            diagnostic_cwd: path.clone(),
            diagnostic_cwd_utf16_units: path.encode_utf16().count(),
            launch_cwd: path.clone(),
            launch_cwd_utf16_units: path.encode_utf16().count(),
            scratch_kept: true,
            original_argfile: directory
                .join("original-not-written.args")
                .to_str()
                .unwrap()
                .to_owned(),
            transport_argfile: directory
                .join("transport-not-created.args")
                .to_str()
                .unwrap()
                .to_owned(),
            argfile_bytes: 0,
            argfile_sha256: "synthetic-no-argfile".to_owned(),
            child_created: false,
            child_pid: None,
            io_error_kind: None,
            raw_os_error: None,
            child_status: None,
            cancelled: false,
        }
    }

    #[test]
    fn all_reviewed_receipts_keep_their_own_projection_recipe_lock_and_mapping() {
        let fixture = tempfile::tempdir().unwrap();
        for key in [
            SPECIALSOURCE_RELEASE_1192_KEY,
            SPECIALSOURCE_RELEASE_1194_KEY,
            SPECIALSOURCE_RELEASE_120_KEY,
        ] {
            let recipe = reviewed_specialsource_recipe(key).unwrap();
            let mut receipt = receipt_fixture(fixture.path());
            receipt.projection_key = recipe.projection_key.to_owned();
            receipt.recipe_id = recipe.recipe_id.to_owned();
            receipt.source_lock_sha256 = recipe.source_lock_sha256.to_owned();
            receipt.mapping_file = recipe
                .mapping_path(fixture.path())
                .to_str()
                .unwrap()
                .to_owned();
            let written: SpecialSourceLaunchReceipt =
                facet_json::from_slice(&fs::read(receipt.persist().unwrap()).unwrap()).unwrap();
            assert_eq!(written.projection_key, recipe.projection_key);
            assert_eq!(written.recipe_id, recipe.recipe_id);
            assert_eq!(written.source_lock_sha256, recipe.source_lock_sha256);
            assert_eq!(
                Path::new(&written.mapping_file),
                recipe.mapping_path(fixture.path())
            );
            assert_eq!(written.tool_coordinate, SPECIALSOURCE_REVIEWED_COORDINATE);
            assert_eq!(written.tool_content_hash, SPECIALSOURCE_REVIEWED_HASH);
            assert_eq!(written.main_class, SPECIALSOURCE_REVIEWED_MAIN);
            assert_eq!(written.phase, "prepared");
            assert!(!written.child_created);
            assert!(written.child_pid.is_none());
            assert!(written.scratch_kept);
        }
    }

    #[test]
    fn numeric_spawn_error_is_durable_and_repeated_attempts_do_not_overwrite() {
        let temp = tempfile::tempdir().unwrap();
        let base = receipt_fixture(temp.path());
        let mut persisted = None;
        let result = observe_process_spawn_result(
            Err::<u32, _>(std::io::Error::from_raw_os_error(267)),
            |child| *child,
            |report| {
                persisted = Some(base.spawn_event(report).persist().unwrap());
            },
        );
        assert_eq!(result.unwrap_err().raw_os_error(), Some(267));
        let first = persisted.unwrap();
        let first_bytes = fs::read(&first).unwrap();
        let written: SpecialSourceLaunchReceipt = facet_json::from_slice(&first_bytes).unwrap();
        assert_eq!(written.phase, "spawn_failed");
        assert_eq!(written.raw_os_error, Some(267));
        assert!(!written.child_created);
        assert!(written.child_pid.is_none());
        let second = written.persist().unwrap();
        assert_ne!(first, second);
        assert_eq!(fs::read(&first).unwrap(), first_bytes);
        assert_eq!(fs::read(&second).unwrap(), first_bytes);
    }

    #[cfg(windows)]
    #[test]
    fn long_diagnostic_directory_keeps_original_receipt_evidence() {
        use std::os::windows::ffi::OsStrExt as _;

        let fixture = tempfile::tempdir().unwrap();
        let mut directory = dunce::simplified(fixture.path()).to_path_buf();
        while directory.as_os_str().encode_wide().count() < 280 {
            directory = directory.join("receipt-evidence-beyond-the-win32-path-boundary");
            fs::create_dir(&directory).unwrap();
        }
        let prepared = receipt_fixture(&directory);
        let original_directory = prepared.diagnostic_cwd.clone();
        let first = prepared.persist().unwrap();
        let first_bytes = fs::read(&first).unwrap();
        let written: SpecialSourceLaunchReceipt = facet_json::from_slice(&first_bytes).unwrap();
        assert_eq!(written.diagnostic_cwd, original_directory);
        assert_eq!(written.phase, "prepared");
        assert!(!written.child_created);
        assert!(written.child_pid.is_none());
        assert_eq!(
            fs::canonicalize(first.parent().unwrap()).unwrap(),
            fs::canonicalize(&directory).unwrap()
        );

        let failed = prepared.spawn_event(&ProcessSpawnReport {
            child_pid: None,
            io_error_kind: Some("Uncategorized".to_owned()),
            raw_os_error: Some(267),
        });
        let second = failed.persist().unwrap();
        assert_ne!(first, second);
        assert_eq!(fs::read(&first).unwrap(), first_bytes);
        let written: SpecialSourceLaunchReceipt =
            facet_json::from_slice(&fs::read(second).unwrap()).unwrap();
        assert_eq!(written.diagnostic_cwd, original_directory);
        assert_eq!(written.phase, "spawn_failed");
        assert_eq!(written.raw_os_error, Some(267));
    }

    #[test]
    fn receipt_failure_does_not_replace_original_numeric_spawn_error() {
        let temp = tempfile::tempdir().unwrap();
        let base = receipt_fixture(&temp.path().join("not-created"));
        let mut secondary = None;
        let result = observe_process_spawn_result(
            Err::<u32, _>(std::io::Error::from_raw_os_error(267)),
            |child| *child,
            |report| {
                secondary = base.spawn_event(report).persist().err();
            },
        );
        assert!(secondary.is_some());
        assert_eq!(result.unwrap_err().raw_os_error(), Some(267));
    }

    #[test]
    fn spawn_observer_preserves_success_and_missing_raw_error_is_not_zero() {
        let mut report = None;
        let result = observe_process_spawn_result(
            Ok::<u32, std::io::Error>(1042),
            |child| *child,
            |observed| {
                report = Some(observed.clone());
            },
        );
        assert_eq!(result.unwrap(), 1042);
        let report = report.unwrap();
        assert_eq!(report.child_pid, Some(1042));
        assert!(report.raw_os_error.is_none());
        assert!(report.io_error_kind.is_none());
        let mut report = None;
        let result = observe_process_spawn_result(
            Err::<u32, _>(std::io::Error::other("synthetic")),
            |child| *child,
            |observed| {
                report = Some(observed.clone());
            },
        );
        assert!(result.is_err());
        let report = report.unwrap();
        assert!(report.child_pid.is_none());
        assert!(report.raw_os_error.is_none());
        assert!(report.io_error_kind.is_some());
    }

    #[test]
    fn kept_scratch_owner_survives_drop_without_erasing_crash_evidence() {
        let fixture = tempfile::tempdir().unwrap();
        let owner = tempfile::tempdir_in(fixture.path()).unwrap();
        let path = keep_specialsource_scratch(owner);
        fs::write(path.join("hs_err_synthetic.log"), b"synthetic-no-jvm").unwrap();
        assert_eq!(
            fs::read(path.join("hs_err_synthetic.log")).unwrap(),
            b"synthetic-no-jvm"
        );
        assert!(path.is_dir());
        // Only the test-owned outer fixture performs automatic cleanup.
    }

    #[test]
    fn scratch_recheck_refuses_new_unowned_members_and_replaced_directory() {
        let fixture = tempfile::tempdir().unwrap();
        let path = fixture.path().join("scratch");
        fs::create_dir(&path).unwrap();
        let held = open_specialsource_directory(&path).unwrap();
        let identity = crate::file_identity::file_identity(&held).unwrap();
        let scratch = RetainedSpecialSourceScratch {
            path: path.clone(),
            held,
            identity,
        };
        assert!(scratch.recheck_empty().is_ok());
        fs::write(path.join("unowned.bin"), b"synthetic").unwrap();
        assert!(scratch.recheck_empty().is_err());
        fs::rename(&path, fixture.path().join("moved")).unwrap();
        fs::create_dir(&path).unwrap();
        assert!(scratch.recheck_empty().is_err());
    }

    #[test]
    fn diagnostic_creation_refuses_escape_and_a_regular_file_parent() {
        let fixture = tempfile::tempdir().unwrap();
        let outside = fixture.path().parent().unwrap().join("must-not-be-created");
        assert!(create_specialsource_diagnostic_directory(fixture.path(), &outside).is_err());
        let regular = fixture.path().join("regular.bin");
        fs::write(&regular, b"synthetic").unwrap();
        assert!(
            create_specialsource_diagnostic_directory(fixture.path(), &regular.join("child"))
                .is_err()
        );
        assert!(!regular.join("child").exists());
    }
}
