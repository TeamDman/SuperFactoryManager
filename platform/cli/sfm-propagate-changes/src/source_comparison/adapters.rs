use super::file::ComparisonFile;
use super::identity::InputIdentity;
use super::input::ComparisonInput;
use super::snapshot::ComparisonSnapshot;
use crate::cancellation::CancellationToken;
use crate::source_projection::candidate_lock::checked_directory;
use crate::source_projection::candidate_lock::checked_file;
use crate::source_projection::candidate_lock::is_reparse;
use crate::source_projection::core_catalog::CoreCatalog;
use crate::source_projection::core_catalog::read_bounded_input;
use crate::source_projection::core_inputs::CORE_METADATA_PATH;
use crate::source_projection::core_inputs::CoreProjectInputs;
use crate::source_projection::core_inputs::collect_core_artifacts;
use crate::source_projection::core_inputs::discover_core_source_files;
use crate::source_projection::core_inputs::select_core_inputs;
use crate::source_projection::oracle_git::OracleGitRepository;
use crate::source_projection::promotion::validate_relative_path;
use crate::source_projection::provenance::sha256;
use eyre::Result;
use eyre::WrapErr;
use eyre::ensure;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::io::Read as _;
use std::path::Path;
const MAX_FILE_BYTES: u64 = 64 * 1024 * 1024;
const MAX_TOTAL_BYTES: usize = 512 * 1024 * 1024;
const MAX_FILES: usize = 100_000;

fn file(bytes: Vec<u8>, mode: Option<u32>) -> ComparisonFile {
    ComparisonFile {
        related_references: None,
        sha256: sha256(&bytes),
        bytes,
        mode,
        git_oid: None,
        source_path: None,
        source_sha256: None,
    }
}

/// Capture exactly the selected local input without fetch, checkout or project writes.
///
/// # Errors
/// Rejects unsafe paths, links/reparse points, missing objects and budget failures.
pub fn snapshot(
    input: &ComparisonInput,
    cancellation: &CancellationToken,
) -> Result<ComparisonSnapshot> {
    cancellation.bail_if_cancelled()?;
    let mut evidence = BTreeMap::new();
    let mut exclusions = Vec::new();
    let files = match input {
        ComparisonInput::Directory { root } => {
            exclusions.push(".git (Git administration only)".to_owned());
            directory_files(root, cancellation)?
        }
        ComparisonInput::PinnedGit {
            repository,
            commit,
            prefix,
        } => {
            let git = OracleGitRepository::open(Path::new(repository))?;
            let revision = git.resolve_commit(commit)?;
            evidence.insert("commit".into(), revision.commit.clone());
            evidence.insert("tree".into(), revision.tree);
            git.files_at_commit(&revision.commit, prefix)?
                .into_iter()
                .map(|(path, raw)| {
                    let mut record = file(raw.bytes, Some(raw.mode));
                    record.git_oid = Some(raw.oid);
                    (path, record)
                })
                .collect()
        }
        ComparisonInput::Projection {
            repository,
            projection,
        } => projection_files(repository, projection, &mut evidence)?,
    };
    seal(input.clone(), evidence, exclusions, files).wrap_err("cannot seal comparison input")
}

pub(super) fn seal(
    selected: ComparisonInput,
    evidence: BTreeMap<String, String>,
    exclusions: Vec<String>,
    files: BTreeMap<String, ComparisonFile>,
) -> Result<ComparisonSnapshot> {
    let mut names = BTreeSet::new();
    let mut components = BTreeMap::new();
    let mut content = Vec::new();
    let mut total = 0;
    ensure!(files.len() <= MAX_FILES, "comparison exceeds file budget");
    for (path, record) in &files {
        validate_relative_path(path)?;
        if let Some(issues) = &record.related_references {
            issues.validate()?;
        }
        let mut prefix = String::new();
        for component in path.split('/') {
            ensure!(
                component.encode_utf16().count() <= 255,
                "comparison filename exceeds portable limit"
            );
            ensure!(
                !component.eq_ignore_ascii_case(".git"),
                "comparison contains Git administration path"
            );
            if !prefix.is_empty() {
                prefix.push('/');
            }
            prefix.push_str(component);
            if let Some(previous) = components.insert(prefix.to_uppercase(), prefix.clone()) {
                ensure!(
                    previous == prefix,
                    "comparison directory components collide by case"
                );
            }
        }
        ensure!(
            path.len() <= 4096 && names.insert(path.to_uppercase()),
            "comparison contains unsafe/colliding paths"
        );
        ensure!(
            record.bytes.len() as u64 <= MAX_FILE_BYTES,
            "comparison exceeds per-file budget"
        );
        total += record.bytes.len();
        ensure!(
            total <= MAX_TOTAL_BYTES,
            "comparison exceeds total byte budget"
        );
        ensure!(
            record.sha256 == sha256(&record.bytes),
            "comparison raw evidence hash mismatch"
        );
        content.extend_from_slice(
            format!("{path}\0{}\0{}\n", record.sha256, record.bytes.len()).as_bytes(),
        );
    }
    for path in files.keys() {
        let mut parent = Path::new(path).parent();
        while let Some(value) = parent {
            if let Some(value) = value.to_str() {
                ensure!(
                    !names.contains(&value.to_uppercase()),
                    "comparison file/directory collision"
                );
            }
            parent = value.parent();
        }
    }
    let content_sha256 = sha256(&content);
    let descriptors: BTreeMap<_, _> = files
        .iter()
        .map(|(path, record)| {
            (
                path.clone(),
                (
                    record.sha256.clone(),
                    (record.bytes.len(), record.mode),
                    (
                        record.git_oid.clone(),
                        record.source_path.clone(),
                        record.source_sha256.clone(),
                        record.related_references.clone(),
                    ),
                ),
            )
        })
        .collect();
    let snapshot_sha256 = sha256(
        facet_json::to_string(&(&selected, &evidence, &exclusions, &descriptors))?.as_bytes(),
    );
    Ok(ComparisonSnapshot {
        identity: InputIdentity {
            selected,
            evidence,
            content_sha256,
            snapshot_sha256,
            exclusions,
        },
        files,
    })
}

fn read_regular(path: &Path) -> Result<std::fs::File> {
    let mut options = std::fs::OpenOptions::new();
    options.read(true);
    #[cfg(windows)]
    {
        use std::os::windows::fs::OpenOptionsExt as _;
        // FILE_FLAG_OPEN_REPARSE_POINT: inspect the opened object, never follow a leaf link.
        options.custom_flags(0x0020_0000)
    };
    let file = options.open(path)?;
    let metadata = file.metadata()?;
    ensure!(
        metadata.is_file() && !is_reparse(&metadata),
        "comparison input became a link/non-file"
    );
    ensure!(
        metadata.len() <= MAX_FILE_BYTES,
        "comparison input exceeds byte limit"
    );
    Ok(file)
}

fn directory_files(
    root: &str,
    cancellation: &CancellationToken,
) -> Result<BTreeMap<String, ComparisonFile>> {
    let root = checked_directory(Path::new(root))?;
    let mut files = BTreeMap::new();
    let mut total = 0;
    for entry in walkdir::WalkDir::new(&root)
        .follow_links(false)
        .max_depth(129)
        .sort_by_file_name()
        .into_iter()
        .filter_entry(|entry| {
            entry.depth() == 0
                || !entry
                    .file_name()
                    .to_str()
                    .is_some_and(|name| name.eq_ignore_ascii_case(".git"))
        })
    {
        cancellation.bail_if_cancelled()?;
        let entry = entry?;
        ensure!(
            !is_reparse(&std::fs::symlink_metadata(entry.path())?),
            "comparison directory contains a link/reparse point"
        );
        ensure!(
            entry.depth() <= 128,
            "comparison directory exceeds depth limit"
        );
        if entry.file_type().is_dir() {
            continue;
        }
        ensure!(
            entry.file_type().is_file(),
            "comparison directory contains a non-regular file"
        );
        let relative = entry
            .path()
            .strip_prefix(&root)?
            .to_str()
            .ok_or_else(|| eyre::eyre!("comparison path is not UTF-8"))?
            .replace('\\', "/");
        validate_relative_path(&relative)?;
        let path = checked_file(&root, &relative)?;
        let mut bytes = Vec::new();
        read_regular(&path)?
            .take(MAX_FILE_BYTES + 1)
            .read_to_end(&mut bytes)?;
        ensure!(
            bytes.len() as u64 <= MAX_FILE_BYTES,
            "comparison file exceeds byte limit: {relative}"
        );
        total += bytes.len();
        ensure!(
            total <= MAX_TOTAL_BYTES && files.len() < MAX_FILES,
            "comparison directory exceeds inventory budget"
        );
        #[cfg(unix)]
        let mode = {
            use std::os::unix::fs::PermissionsExt as _;
            Some(
                if std::fs::metadata(&path)?.permissions().mode() & 0o111 != 0 {
                    0o100_755
                } else {
                    0o100_644
                },
            )
        };
        #[cfg(not(unix))]
        let mode = None;
        files.insert(relative, file(bytes, mode));
    }
    Ok(files)
}

fn projection_files(
    repository: &str,
    projection: &str,
    evidence: &mut BTreeMap<String, String>,
) -> Result<BTreeMap<String, ComparisonFile>> {
    let catalog = CoreCatalog::load(Path::new(repository), Path::new("."))?;
    let context = catalog.context(projection)?;
    let metadata_bytes = read_bounded_input(
        &catalog.repo_root,
        CORE_METADATA_PATH,
        crate::source_projection::core_inputs::MAX_CORE_METADATA_BYTES,
    )?;
    let metadata = CoreProjectInputs::from_json(
        std::str::from_utf8(&metadata_bytes)?,
        &catalog.registered_features,
    )?;
    let core = catalog
        .repo_root
        .join("platform/minecraft/core-liquid-template");
    let inventory = discover_core_source_files(&core)?;
    let selection = select_core_inputs(&metadata, &context, &inventory)?;
    evidence.insert("catalog_sha256".into(), catalog.catalog_sha256.clone());
    evidence.insert(
        "feature_definitions_sha256".into(),
        catalog.feature_definitions_sha256.clone(),
    );
    evidence.insert("project_inputs_sha256".into(), sha256(&metadata_bytes));
    evidence.insert(
        "context_identity".into(),
        catalog.catalog.context_identity(projection)?,
    );
    evidence.insert("context_json".into(), facet_json::to_string(&context)?);
    let files = collect_core_artifacts(&core, &selection, &context)?
        .into_iter()
        .map(|(path, artifact)| {
            let mut record = file(
                artifact.output_bytes,
                Some(if path == "gradlew" {
                    0o100_755
                } else {
                    0o100_644
                }),
            );
            record.related_references = artifact.related_references;
            record.source_path = Some(artifact.source_path);
            record.source_sha256 = Some(sha256(&artifact.source_bytes));
            (path, record)
        })
        .collect();
    Ok(files)
}
