//! Portable, read-only verification of a ten-target source-projection release candidate.
//!
//! The lock contains hashes and repository-relative paths, never local candidate
//! roots. Callers provide those roots separately. Build task, profile and JDK
//! build IDs are reviewed assertions; this verifier cannot attest the process
//! that ran Gradle.

use super::manifest::SourceProjectionManifest;
use super::promotion::validate_relative_path;
use super::provenance::ProjectionProvenance;
use super::provenance::sha256;
use super::sync::MANIFEST_FILE;
use eyre::Result;
use eyre::WrapErr;
use eyre::ensure;
use facet::Facet;
use sha2::Digest;
use sha2::Sha256;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::fs;
use std::io::Read as _;
use std::path::Component;
use std::path::Path;
use std::path::PathBuf;
use std::process::Command;
use walkdir::WalkDir;

const LOCK_SCHEMA: &str = "sfm:source_candidate_lock@1";
const REPORT_SCHEMA: &str = "sfm:source_candidate_byte_inventory@1";
const SOURCE_MANIFEST: &str = "platform/minecraft/source-projection.json";
const ROOT_GRADLE_PROPERTIES: &str = "platform/minecraft/gradle.properties";
const MATRIX_SIZE: usize = 10;

#[derive(Clone, Debug, Facet)]
#[facet(deny_unknown_fields)]
pub struct SourceCandidateLock {
    pub schema: String,
    /// Exact authored commit that contains the reviewed projection definition.
    pub source_commit: String,
    pub source_manifest_sha256: String,
    pub mod_version: String,
    pub candidate_preset_id: String,
    pub candidate_definition_identity: String,
    pub compatibility_evidence_relative_path: String,
    pub compatibility_evidence_sha256: String,
    pub targets: Vec<CandidateTargetLock>,
}

#[derive(Clone, Debug, Facet)]
#[facet(deny_unknown_fields)]
pub struct CandidateTargetLock {
    pub target_id: String,
    /// Gradle Minecraft version; `1.21.0` targets Minecraft `1.21`.
    pub minecraft_version: String,
    pub loader: String,
    /// Exact `neo_version` from the projected Gradle properties.
    pub loader_version: String,
    /// `default` or a reviewed Gradle profile name; an assertion, not attestation.
    pub gradle_profile: String,
    pub production_task: String,
    pub jdk_major: u16,
    /// Reviewed build identity; an assertion, not process attestation.
    pub jdk_build_id: String,
    pub provenance_manifest_sha256: String,
    pub production_jar_relative_path: String,
    pub production_jar_sha256: String,
}

#[derive(Debug, Facet)]
pub struct CandidateVerificationReport {
    pub schema: String,
    pub lock_sha256: String,
    pub source_commit: String,
    pub mod_version: String,
    pub candidate_preset_id: String,
    pub verified_targets: BTreeMap<String, String>,
    /// Set only by the CLI after the independent deterministic source check.
    pub deterministic_source_check: bool,
    pub toolchain_fields_are_reviewed_assertions: bool,
}

impl SourceCandidateLock {
    /// Parse a strict portable candidate lock. Local root paths are not part of
    /// the schema and unknown fields fail parsing.
    ///
    /// # Errors
    ///
    /// Rejects unsupported schemas, unsafe paths and an incomplete matrix.
    pub fn from_json(input: &str) -> Result<Self> {
        let lock: Self = facet_json::from_str(input)
            .wrap_err("cannot parse strict source-candidate lock JSON")?;
        lock.validate()?;
        Ok(lock)
    }

    fn validate(&self) -> Result<()> {
        ensure!(
            self.schema == LOCK_SCHEMA,
            "unsupported source-candidate lock schema"
        );
        validate_lower_hex(&self.source_commit, 40, "source commit")?;
        validate_digest(&self.source_manifest_sha256)?;
        validate_digest(&self.compatibility_evidence_sha256)?;
        validate_relative_path(&self.compatibility_evidence_relative_path)?;
        let identity = self
            .candidate_definition_identity
            .strip_prefix("blake3:")
            .ok_or_else(|| eyre::eyre!("invalid candidate definition identity"))?;
        validate_lower_hex(identity, 64, "candidate definition identity")?;
        ensure!(
            safe_label(&self.mod_version) && !self.mod_version.contains("-dev."),
            "invalid release mod version"
        );
        ensure!(
            self.candidate_preset_id == format!("released-{}", self.mod_version),
            "candidate preset ID must be released-<mod_version>"
        );
        ensure!(
            self.targets.len() == MATRIX_SIZE,
            "candidate lock requires exactly {MATRIX_SIZE} targets"
        );
        let mut targets = BTreeSet::new();
        let mut names = BTreeSet::new();
        for target in &self.targets {
            ensure!(
                targets.insert(target.target_id.clone()),
                "duplicate candidate target '{}'",
                target.target_id
            );
            ensure!(
                safe_label(&target.target_id)
                    && safe_label(&target.minecraft_version)
                    && safe_label(&target.loader)
                    && safe_label(&target.loader_version)
                    && safe_label(&target.gradle_profile),
                "invalid candidate identity or build field for '{}'",
                target.target_id
            );
            ensure!(
                target.production_task == expected_production_task(&target.target_id)?,
                "wrong production task for '{}'",
                target.target_id
            );
            let (_, jdk_version) = target
                .jdk_build_id
                .rsplit_once('-')
                .ok_or_else(|| eyre::eyre!("invalid JDK build ID for '{}'", target.target_id))?;
            ensure!(
                safe_label(&target.jdk_build_id)
                    && target.jdk_build_id.len() <= 128
                    && jdk_version.split('.').next() == Some(target.jdk_major.to_string().as_str()),
                "JDK build ID does not match major for '{}'",
                target.target_id
            );
            validate_digest(&target.provenance_manifest_sha256)?;
            validate_digest(&target.production_jar_sha256)?;
            validate_relative_path(&target.production_jar_relative_path)?;
            let parts = target
                .production_jar_relative_path
                .split('/')
                .collect::<Vec<_>>();
            ensure!(
                matches!(parts.as_slice(), ["build", "libs", _]),
                "production JAR must be directly under build/libs for '{}'",
                target.target_id
            );
            let name = parts[2];
            ensure!(
                name.ends_with(&format!(
                    "-MC{}-{}.jar",
                    target.minecraft_version, self.mod_version
                )) && !name.contains("-dev."),
                "production JAR filename does not match locked release version for '{}'",
                target.target_id
            );
            ensure!(
                names.insert(name.to_ascii_lowercase()),
                "duplicate production JAR filename '{name}'"
            );
        }
        Ok(())
    }

    /// Verify the exact-byte candidate inventory without changing it.
    /// This alone is not release acceptance: callers must also run the
    /// deterministic source check against the authored commit.
    ///
    /// # Errors
    ///
    /// Fails on a changed Git definition, incomplete root mapping, changed
    /// provenance/output/JAR bytes, unsafe paths or wrong Gradle metadata.
    pub fn verify_in(
        &self,
        repository_root: &Path,
        candidate_roots: &BTreeMap<String, PathBuf>,
        lock_sha256: String,
    ) -> Result<CandidateVerificationReport> {
        self.validate()?;
        validate_digest(&lock_sha256)?;
        let root = checked_directory(repository_root)?;
        let source_manifest = self.verify_repository_inputs(&root)?;
        let preset = source_manifest.preset(&self.candidate_preset_id)?;
        ensure_authored_checkout(
            &root,
            &self.source_commit,
            &source_manifest,
            &self.candidate_preset_id,
        )?;
        ensure!(
            preset.identity == self.candidate_definition_identity,
            "candidate preset definition identity differs from lock"
        );
        let expected_ids = source_manifest
            .targets
            .iter()
            .map(|target| target.id.as_str())
            .collect::<BTreeSet<_>>();
        let locked_ids = self
            .targets
            .iter()
            .map(|target| target.target_id.as_str())
            .collect::<BTreeSet<_>>();
        ensure!(
            expected_ids == locked_ids
                && preset
                    .targets
                    .iter()
                    .map(String::as_str)
                    .collect::<BTreeSet<_>>()
                    == locked_ids,
            "candidate lock targets do not match complete projection preset matrix"
        );
        ensure!(
            candidate_roots
                .keys()
                .map(String::as_str)
                .collect::<BTreeSet<_>>()
                == locked_ids,
            "local candidate roots do not match complete locked target matrix"
        );
        let mut verified_targets = BTreeMap::new();
        let mut seen_roots: Vec<PathBuf> = Vec::new();
        for target in &self.targets {
            let declared = source_manifest.target(&target.target_id)?;
            ensure!(
                target.minecraft_version == declared.minecraft_version
                    && target.loader == declared.loader
                    && target.jdk_major == declared.java_major,
                "target version, loader or JDK major differs from projection definition for '{}'",
                target.target_id
            );
            let candidate_root = checked_directory(&candidate_roots[&target.target_id])?;
            ensure!(
                !is_within(&candidate_root, &root) && !is_within(&root, &candidate_root),
                "candidate root for '{}' must be outside repository",
                target.target_id
            );
            for existing in &seen_roots {
                ensure!(
                    !is_within(&candidate_root, existing) && !is_within(existing, &candidate_root),
                    "candidate roots overlap"
                );
            }
            seen_roots.push(candidate_root.clone());
            verify_target(&candidate_root, self, target)?;
            verified_targets.insert(
                target.target_id.clone(),
                target.production_jar_sha256.clone(),
            );
        }
        Ok(CandidateVerificationReport {
            schema: REPORT_SCHEMA.to_owned(),
            lock_sha256,
            source_commit: self.source_commit.clone(),
            mod_version: self.mod_version.clone(),
            candidate_preset_id: self.candidate_preset_id.clone(),
            verified_targets,
            deterministic_source_check: false,
            toolchain_fields_are_reviewed_assertions: true,
        })
    }

    fn verify_repository_inputs(&self, root: &Path) -> Result<SourceProjectionManifest> {
        let source_bytes = read_regular(root, SOURCE_MANIFEST)?;
        ensure!(
            sha256(&source_bytes) == self.source_manifest_sha256,
            "current source-projection definition differs from candidate lock"
        );
        ensure_committed_current_file(root, SOURCE_MANIFEST, &source_bytes)?;
        ensure!(
            git_file_at_commit(root, &self.source_commit, SOURCE_MANIFEST)? == source_bytes,
            "source commit does not contain the locked source-projection definition"
        );
        let manifest = SourceProjectionManifest::from_json(std::str::from_utf8(&source_bytes)?)?;
        let root_properties = read_regular(root, ROOT_GRADLE_PROPERTIES)?;
        ensure_committed_current_file(root, ROOT_GRADLE_PROPERTIES, &root_properties)?;
        ensure!(
            git_file_at_commit(root, &self.source_commit, ROOT_GRADLE_PROPERTIES)?
                == root_properties,
            "source commit does not contain the locked release Gradle properties"
        );
        let release_version = match &manifest
            .preset(&self.candidate_preset_id)?
            .release_mod_version
        {
            Some(version) => version.as_str(),
            None => gradle_property(std::str::from_utf8(&root_properties)?, "mod_version")?,
        };
        ensure!(
            release_version == self.mod_version,
            "reviewed release mod_version differs from candidate lock"
        );
        let evidence = read_regular(root, &self.compatibility_evidence_relative_path)?;
        ensure!(
            sha256(&evidence) == self.compatibility_evidence_sha256,
            "compatibility evidence differs from candidate lock"
        );
        ensure_committed_current_file(root, &self.compatibility_evidence_relative_path, &evidence)?;
        Ok(manifest)
    }
}

fn verify_target(
    root: &Path,
    lock: &SourceCandidateLock,
    target: &CandidateTargetLock,
) -> Result<()> {
    let manifest_bytes = read_regular(root, MANIFEST_FILE)?;
    ensure!(
        sha256(&manifest_bytes) == target.provenance_manifest_sha256,
        "candidate manifest hash mismatch for '{}'",
        target.target_id
    );
    let manifest = ProjectionProvenance::from_json(std::str::from_utf8(&manifest_bytes)?)?;
    ensure!(
        manifest.to_json()?.as_bytes() == manifest_bytes,
        "noncanonical candidate manifest for '{}'",
        target.target_id
    );
    ensure!(
        manifest.target_id == target.target_id
            && manifest.minecraft_version == target.minecraft_version
            && manifest.preset_id == lock.candidate_preset_id
            && manifest.preset_definition_identity == lock.candidate_definition_identity,
        "candidate provenance identity mismatch for '{}'",
        target.target_id
    );
    ensure!(
        manifest.files.contains_key("gradle.properties")
            && !manifest.files.contains_key(MANIFEST_FILE),
        "candidate manifest must own gradle.properties and not itself"
    );
    let mut paths = BTreeSet::new();
    for (path, file) in &manifest.files {
        validate_relative_path(path)?;
        validate_relative_path(&file.source_path)?;
        validate_digest(&file.source_sha256)?;
        validate_digest(&file.output_sha256)?;
        ensure!(
            paths.insert(path.to_ascii_lowercase()),
            "case-only candidate output collision for '{}'",
            target.target_id
        );
        ensure!(
            hash_regular(root, path)? == file.output_sha256,
            "candidate output hash mismatch for '{}' at '{path}'",
            target.target_id
        );
    }
    ensure_closed_candidate_inputs(root, &manifest.files)?;
    let properties = read_regular(root, "gradle.properties")?;
    let properties =
        std::str::from_utf8(&properties).wrap_err("Gradle properties are not UTF-8")?;
    ensure!(
        gradle_property(properties, "minecraft_version")? == target.minecraft_version
            && gradle_property(properties, "mod_version")? == lock.mod_version
            && gradle_property(properties, "neo_version")? == target.loader_version,
        "candidate Gradle version or loader metadata mismatch for '{}'",
        target.target_id
    );
    ensure!(
        hash_regular(root, &target.production_jar_relative_path)? == target.production_jar_sha256,
        "candidate production JAR hash mismatch for '{}'",
        target.target_id
    );
    Ok(())
}

fn ensure_closed_candidate_inputs(
    root: &Path,
    owned: &BTreeMap<String, super::provenance::ProjectedFileProvenance>,
) -> Result<()> {
    for entry in fs::read_dir(root)? {
        let entry = entry?;
        let name = entry
            .file_name()
            .into_string()
            .map_err(|bad| eyre::eyre!("candidate root has a non-UTF-8 entry: {bad:?}"))?;
        let metadata = fs::symlink_metadata(entry.path())?;
        ensure!(
            !is_reparse(&metadata),
            "candidate root contains a reparse point"
        );
        if metadata.is_dir() {
            // Gradle's GameTest server writes runtime state here, not project inputs.
            if matches!(
                name.as_str(),
                "build" | ".gradle" | "run" | "runs" | "logs" | "runGameTest"
            ) {
                continue;
            }
            ensure!(
                name == "src"
                    || name == "gradle"
                    || owned
                        .keys()
                        .any(|path| path.starts_with(&format!("{name}/"))),
                "unowned candidate project directory '{name}'"
            );
            for child in WalkDir::new(entry.path()).follow_links(false) {
                let child = child.wrap_err("cannot walk candidate project inputs")?;
                let metadata = fs::symlink_metadata(child.path())?;
                ensure!(
                    !is_reparse(&metadata),
                    "candidate project input is a reparse point"
                );
                if metadata.is_dir() {
                    continue;
                }
                ensure!(metadata.is_file(), "candidate project input is not regular");
                let relative = child.path().strip_prefix(root)?;
                let relative = relative
                    .to_str()
                    .ok_or_else(|| eyre::eyre!("candidate project input path is not UTF-8"))?
                    .replace('\\', "/");
                validate_relative_path(&relative)?;
                ensure!(
                    owned.contains_key(&relative),
                    "unowned candidate project input '{relative}'"
                );
            }
        } else {
            ensure!(metadata.is_file(), "candidate root entry is not regular");
            ensure!(
                name == MANIFEST_FILE || owned.contains_key(&name),
                "unowned candidate project input '{name}'"
            );
        }
    }
    Ok(())
}

fn gradle_property<'a>(content: &'a str, name: &str) -> Result<&'a str> {
    let values = content
        .lines()
        .map(str::trim)
        .filter(|line| !line.starts_with('#'))
        .filter_map(|line| line.strip_prefix(name)?.strip_prefix('='))
        .map(str::trim)
        .collect::<Vec<_>>();
    ensure!(
        matches!(values.as_slice(), [value] if !value.is_empty()),
        "Gradle property '{name}' must occur exactly once and be nonempty"
    );
    Ok(values[0])
}

fn ensure_authored_checkout(
    root: &Path,
    source_commit: &str,
    manifest: &SourceProjectionManifest,
    preset_id: &str,
) -> Result<()> {
    let head = git_output(root, &["rev-parse", "HEAD"])?;
    ensure!(
        String::from_utf8(head)?.trim() == source_commit,
        "candidate source commit must be the authored checkout HEAD"
    );
    for arguments in [
        ["diff", "--quiet", "HEAD", "--", "platform/minecraft"].as_slice(),
        [
            "diff",
            "--cached",
            "--quiet",
            "HEAD",
            "--",
            "platform/minecraft",
        ]
        .as_slice(),
    ] {
        let output = Command::new("git")
            .arg("-C")
            .arg(root)
            .args(arguments)
            .output()?;
        ensure!(
            output.status.success(),
            "authored platform/minecraft inputs differ from source commit"
        );
    }
    ensure!(
        git_output(
            root,
            &[
                "ls-files",
                "-z",
                "--others",
                "--exclude-standard",
                "--",
                "platform/minecraft"
            ]
        )?
        .is_empty(),
        "authored platform/minecraft tree contains untracked inputs"
    );
    let tracked = git_output(
        root,
        &["ls-files", "-z", "--cached", "--", "platform/minecraft"],
    )?
    .split(|byte| *byte == 0)
    .filter(|path| !path.is_empty())
    .map(|path| String::from_utf8(path.to_vec()))
    .collect::<std::result::Result<BTreeSet<_>, _>>()?;
    let preset = manifest.preset(preset_id)?;
    let mut selected_roots = BTreeSet::from([
        "platform/minecraft/src".to_owned(),
        "platform/minecraft/gradle".to_owned(),
    ]);
    if preset.release_baselines.is_empty() {
        for target_id in &preset.targets {
            selected_roots.insert(manifest.target(target_id)?.project_dir.clone());
        }
    } else {
        for binding in &preset.release_baselines {
            let parent = Path::new(&binding.import_manifest)
                .parent()
                .ok_or_else(|| eyre::eyre!("release import manifest has no parent"))?;
            selected_roots.insert(parent.to_string_lossy().replace('\\', "/"));
            for resource in binding.post_baseline_resources.values() {
                ensure!(
                    tracked.contains(&resource.source_path),
                    "selected release resource is not tracked at source commit"
                );
                checked_file(root, &resource.source_path)?;
            }
        }
    }
    for feature in &manifest.features {
        if !preset
            .targets
            .iter()
            .any(|target| preset.feature_enabled_for(target, &feature.id))
        {
            continue;
        }
        for input in feature
            .source_effects
            .iter()
            .chain(&feature.resource_effects)
            .filter_map(|effect| effect.input_path.as_ref())
        {
            ensure!(
                tracked.contains(input),
                "selected feature input is not tracked at source commit"
            );
            checked_file(root, input)?;
        }
    }
    for selected in selected_roots {
        ensure_tracked_tree(root, &selected, &tracked)?;
    }
    Ok(())
}

fn ensure_tracked_tree(root: &Path, relative: &str, tracked: &BTreeSet<String>) -> Result<()> {
    validate_relative_path(relative)?;
    let selected = root.join(relative);
    if !selected.exists() {
        return Ok(());
    }
    for entry in WalkDir::new(selected).follow_links(false) {
        let entry = entry.wrap_err("cannot walk selected authored input tree")?;
        let metadata = fs::symlink_metadata(entry.path())?;
        ensure!(
            !is_reparse(&metadata),
            "selected authored input is a reparse point"
        );
        if metadata.is_dir() {
            continue;
        }
        ensure!(metadata.is_file(), "selected authored input is not regular");
        let path = entry.path().strip_prefix(root)?;
        let path = path
            .to_str()
            .ok_or_else(|| eyre::eyre!("selected authored input path is not UTF-8"))?
            .replace('\\', "/");
        ensure!(
            tracked.contains(&path),
            "selected authored input '{path}' is not tracked at source commit"
        );
    }
    Ok(())
}

fn git_output(root: &Path, args: &[&str]) -> Result<Vec<u8>> {
    let output = Command::new("git")
        .arg("-C")
        .arg(root)
        .args(args)
        .output()?;
    ensure!(output.status.success(), "candidate Git inspection failed");
    Ok(output.stdout)
}

fn git_file_at_commit(root: &Path, commit: &str, path: &str) -> Result<Vec<u8>> {
    let top = Command::new("git")
        .arg("-C")
        .arg(root)
        .args(["rev-parse", "--show-toplevel"])
        .output()
        .wrap_err("cannot inspect candidate-lock Git repository")?;
    ensure!(
        top.status.success(),
        "candidate lock root is not a Git worktree"
    );
    ensure!(
        fs::canonicalize(String::from_utf8(top.stdout)?.trim())? == root,
        "candidate lock repository root must be the Git worktree root"
    );
    let kind = Command::new("git")
        .arg("-C")
        .arg(root)
        .args(["cat-file", "-t", commit])
        .output()
        .wrap_err("cannot inspect candidate-lock source commit")?;
    ensure!(
        kind.status.success() && kind.stdout == b"commit\n",
        "source candidate lock must name an available Git commit"
    );
    let output = Command::new("git")
        .arg("-C")
        .arg(root)
        .args(["show", &format!("{commit}:{path}")])
        .output()
        .wrap_err("cannot read candidate-lock source commit")?;
    ensure!(
        output.status.success(),
        "source commit or its projection definition is unavailable"
    );
    Ok(output.stdout)
}

fn ensure_committed_current_file(root: &Path, path: &str, bytes: &[u8]) -> Result<()> {
    let staged = Command::new("git")
        .arg("-C")
        .arg(root)
        .args(["diff", "--cached", "--quiet", "HEAD", "--", path])
        .status()
        .wrap_err("cannot inspect staged candidate-lock input")?;
    ensure!(
        staged.success(),
        "candidate-lock input '{path}' differs from HEAD in Git index"
    );
    ensure!(
        git_file_at_commit(root, "HEAD", path)? == bytes,
        "candidate-lock input '{path}' differs from HEAD"
    );
    Ok(())
}

fn expected_production_task(target: &str) -> Result<&'static str> {
    Ok(match target {
        "1.19.2" | "1.19.4" | "1.20" | "1.20.1" => "reobfJar",
        "1.20.2" | "1.20.3" | "1.20.4" | "1.21.0" | "1.21.1" => "jar",
        "26.1.2" => "jarJar",
        _ => eyre::bail!("unsupported source-candidate target '{target}'"),
    })
}

fn safe_label(value: &str) -> bool {
    !value.is_empty()
        && value.len() <= 128
        && value
            .bytes()
            .all(|byte| byte.is_ascii_alphanumeric() || b"._+-".contains(&byte))
}

fn validate_digest(value: &str) -> Result<()> {
    let hex = value.strip_prefix("sha256:").unwrap_or("");
    validate_lower_hex(hex, 64, "SHA-256 digest")
}

fn validate_lower_hex(value: &str, length: usize, label: &str) -> Result<()> {
    ensure!(
        value.len() == length
            && value
                .bytes()
                .all(|byte| byte.is_ascii_digit() || (b'a'..=b'f').contains(&byte)),
        "invalid {label}"
    );
    Ok(())
}

/// Resolve an existing absolute directory without traversing reparse points.
///
/// # Errors
///
/// Rejects missing, relative, traversing or reparse-point paths.
pub(crate) fn checked_directory(path: &Path) -> Result<PathBuf> {
    ensure!(
        path.is_absolute(),
        "candidate verification root must be absolute"
    );
    ensure!(
        !path
            .components()
            .any(|part| matches!(part, Component::ParentDir | Component::CurDir)),
        "candidate verification root cannot contain parent/current traversal"
    );
    for ancestor in path.ancestors() {
        let metadata = fs::symlink_metadata(ancestor)
            .wrap_err_with(|| format!("cannot inspect directory '{}'", ancestor.display()))?;
        ensure!(
            metadata.is_dir() && !is_reparse(&metadata),
            "candidate verification root traverses a reparse point or non-directory"
        );
    }
    fs::canonicalize(path).wrap_err("cannot resolve candidate verification root")
}

fn read_regular(root: &Path, relative: &str) -> Result<Vec<u8>> {
    let path = checked_file(root, relative)?;
    fs::read(&path).wrap_err_with(|| format!("cannot read '{}'", path.display()))
}

fn hash_regular(root: &Path, relative: &str) -> Result<String> {
    let path = checked_file(root, relative)?;
    let mut file =
        fs::File::open(&path).wrap_err_with(|| format!("cannot open '{}'", path.display()))?;
    let mut hasher = Sha256::new();
    let mut buffer = [0_u8; 16_384];
    loop {
        let read = file.read(&mut buffer)?;
        if read == 0 {
            break;
        }
        hasher.update(&buffer[..read]);
    }
    Ok(format!("sha256:{:x}", hasher.finalize()))
}

/// Check one locked file path and every parent below its verified root.
///
/// # Errors
///
/// Rejects unsafe, missing, nonregular or reparse-point components.
pub(crate) fn checked_file(root: &Path, relative: &str) -> Result<PathBuf> {
    validate_relative_path(relative)?;
    let parts = relative.split('/').collect::<Vec<_>>();
    let mut path = root.to_path_buf();
    for (index, part) in parts.iter().enumerate() {
        path.push(part);
        let metadata = fs::symlink_metadata(&path)
            .wrap_err_with(|| format!("required candidate file is missing at '{relative}'"))?;
        ensure!(
            !is_reparse(&metadata)
                && if index + 1 == parts.len() {
                    metadata.is_file()
                } else {
                    metadata.is_dir()
                },
            "candidate path is a reparse point or wrong file type at '{relative}'"
        );
    }
    Ok(path)
}

fn is_reparse(metadata: &fs::Metadata) -> bool {
    if metadata.file_type().is_symlink() {
        return true;
    }
    #[cfg(windows)]
    {
        use std::os::windows::fs::MetadataExt as _;
        metadata.file_attributes() & 0x0000_0400 != 0
    }
    #[cfg(not(windows))]
    {
        false
    }
}

/// Compare canonical paths at directory-component boundaries.
pub(crate) fn is_within(path: &Path, parent: &Path) -> bool {
    let path = path.to_string_lossy().to_lowercase();
    let parent = parent.to_string_lossy().to_lowercase();
    path == parent || path.starts_with(&format!("{parent}{}", std::path::MAIN_SEPARATOR))
}

#[cfg(test)]
pub(crate) mod tests {
    use super::*;
    use crate::cancellation::CancellationToken;
    use crate::cli::source::CandidateVerifyArgs;
    use crate::cli::source::SourceArgs;
    use crate::cli::source::SourceCommand;
    use crate::cli::source::SourceProjectArgs;
    use crate::source_projection::manifest::ProjectionPreset;
    use crate::source_projection::manifest::ProjectionTarget;
    use crate::source_projection::manifest::SCHEMA_VERSION;
    use crate::source_projection::promotion::PromotionCandidate;
    use crate::source_projection::promotion::PromotionRequest;
    use crate::source_projection::promotion::PromotionTransition;
    use crate::source_projection::promotion::ReviewedOperationKind;
    use crate::source_projection::promotion::ReviewedPromotionOperation;
    use tempfile::TempDir;

    const TARGETS: [(&str, &str, &str, u16); 10] = [
        ("1.19.2", "1.19.2", "forge", 17),
        ("1.19.4", "1.19.4", "forge", 17),
        ("1.20", "1.20", "forge", 17),
        ("1.20.1", "1.20.1", "neoforge", 17),
        ("1.20.2", "1.20.2", "neoforge", 17),
        ("1.20.3", "1.20.3", "neoforge", 17),
        ("1.20.4", "1.20.4", "neoforge", 17),
        ("1.21.0", "1.21", "neoforge", 21),
        ("1.21.1", "1.21.1", "neoforge", 21),
        ("26.1.2", "26.1.2", "neoforge", 25),
    ];

    pub(crate) struct Fixture {
        _temp: TempDir,
        repo: PathBuf,
        roots: BTreeMap<String, PathBuf>,
        lock: SourceCandidateLock,
    }

    impl Fixture {
        pub(crate) fn new() -> Self {
            Self::new_with_version("4.35.0", false)
        }

        fn new_with_version(mod_version: &str, include_refmap: bool) -> Self {
            Self::new_with_source_version(mod_version, mod_version, include_refmap, false)
        }

        pub(crate) fn new_with_release_version_override() -> Self {
            Self::new_with_source_version("9.99.99-fixture", "4.34.0", false, true)
        }

        fn new_with_source_version(
            mod_version: &str,
            source_version: &str,
            include_refmap: bool,
            release_override: bool,
        ) -> Self {
            let temp = tempfile::tempdir().unwrap();
            let repo = temp.path().join("repo");
            fs::create_dir_all(repo.join("platform/minecraft")).unwrap();
            fs::create_dir_all(repo.join("docs")).unwrap();
            let mut manifest = SourceProjectionManifest {
                schema_version: SCHEMA_VERSION,
                targets: TARGETS
                    .iter()
                    .map(
                        |(id, minecraft_version, loader, java_major)| ProjectionTarget {
                            id: (*id).to_owned(),
                            template_key: format!("mc_{}", id.replace('.', "_")),
                            minecraft_version: (*minecraft_version).to_owned(),
                            loader: (*loader).to_owned(),
                            java_major: *java_major,
                            project_dir: format!("platform/minecraft/mc-version/{id}"),
                        },
                    )
                    .collect(),
                features: vec![],
                presets: vec![ProjectionPreset {
                    id: format!("released-{mod_version}"),
                    release_mod_version: release_override.then(|| mod_version.to_owned()),
                    targets: TARGETS
                        .iter()
                        .map(|(id, _, _, _)| (*id).to_owned())
                        .collect(),
                    enabled_features: vec![],
                    target_features: BTreeMap::new(),
                    release_baselines: vec![],
                    frozen_source_commit: None,
                    frozen_sources: vec![],
                    canonical_project_fixture_provenance_sha256: None,
                    identity: String::new(),
                }],
            };
            manifest.presets[0].identity = manifest
                .compute_preset_identity(&manifest.presets[0])
                .unwrap();
            let manifest_json = facet_json::to_string_pretty(&manifest).unwrap() + "\n";
            fs::write(repo.join(SOURCE_MANIFEST), &manifest_json).unwrap();
            fs::write(
                repo.join(ROOT_GRADLE_PROPERTIES),
                format!("mod_version={source_version}\n"),
            )
            .unwrap();
            fs::create_dir_all(repo.join("platform/minecraft/src/main/java")).unwrap();
            fs::write(
                repo.join("platform/minecraft/src/main/java/Candidate.java"),
                "class Candidate {}\n",
            )
            .unwrap();
            if include_refmap {
                let refmap = repo.join("platform/minecraft/src/main/resources/sfm.refmap.json");
                fs::create_dir_all(refmap.parent().unwrap()).unwrap();
                fs::write(refmap, "{}\n").unwrap();
            }
            fs::create_dir_all(repo.join("platform/minecraft/gradle/wrapper")).unwrap();
            for (path, bytes) in [
                ("build.gradle", "// synthetic build\n"),
                ("settings.gradle", "rootProject.name = 'base'\n"),
                ("gradlew", "#!/bin/sh\n"),
                ("gradlew.bat", "@echo off\n"),
                ("sfm-toolchain.lock.json", "{}\n"),
                (
                    "gradle/wrapper/gradle-wrapper.properties",
                    "distributionUrl=https://example.invalid/gradle-8.12-bin.zip\n",
                ),
            ] {
                fs::write(repo.join("platform/minecraft").join(path), bytes).unwrap();
            }
            for (id, minecraft_version, _, _) in TARGETS {
                let overlay = repo.join(format!("platform/minecraft/mc-version/{id}"));
                fs::create_dir_all(overlay.join("gradle/wrapper")).unwrap();
                fs::write(
                    overlay.join("gradle.properties"),
                    format!(
                        "minecraft_version={minecraft_version}\nmod_version={source_version}\nneo_version=1.2.3\n"
                    ),
                )
                .unwrap();
                fs::write(
                    overlay.join("settings.gradle"),
                    format!("rootProject.name = 'sfm-{id}'\n"),
                )
                .unwrap();
                fs::write(overlay.join("sfm-toolchain.lock.json"), "{}\n").unwrap();
                fs::write(
                    overlay.join("gradle/wrapper/gradle-wrapper.properties"),
                    "distributionUrl=https://example.invalid/gradle-8.12-bin.zip\n",
                )
                .unwrap();
                if include_refmap {
                    let synthetic =
                        repo.join(format!("platform/minecraft/test-gradle-overlays/{id}"));
                    for path in [
                        "gradle.properties",
                        "settings.gradle",
                        "sfm-toolchain.lock.json",
                        "gradle/wrapper/gradle-wrapper.properties",
                    ] {
                        let output = synthetic.join(path);
                        fs::create_dir_all(output.parent().unwrap()).unwrap();
                        fs::write(output, fs::read(overlay.join(path)).unwrap()).unwrap();
                    }
                }
            }
            let evidence = b"Reviewed compatibility differences.\n";
            fs::write(repo.join("docs/compatibility.md"), evidence).unwrap();
            fs::write(
                repo.join(".gitignore"),
                "platform/minecraft/src/main/java/Hidden.java\n/platform/minecraft/.sfm-source-promotion-stage-*/\n",
            )
            .unwrap();
            git(&repo, &["init", "-q"]);
            git(&repo, &["config", "user.name", "Candidate Test"]);
            git(
                &repo,
                &["config", "user.email", "candidate@example.invalid"],
            );
            git(&repo, &["add", "."]);
            git(&repo, &["commit", "-qm", "reviewed source"]);
            let source_commit = git(&repo, &["rev-parse", "HEAD"]);
            let source_commit = source_commit.trim().to_owned();
            let mut roots = BTreeMap::new();
            let mut targets = Vec::new();
            for (id, minecraft_version, loader, java_major) in TARGETS {
                let root = temp.path().join("candidates").join(id);
                SourceArgs {
                    command: SourceCommand::Sync(SourceProjectArgs {
                        repo_root: repo.clone(),
                        target: id.to_owned(),
                        preset: format!("released-{mod_version}"),
                        manifest: None,
                        primary_src_root: None,
                        gradle_project_root: None,
                        output_root: root.clone(),
                        overlay: Vec::new(),
                        gradle_overlay: vec![if include_refmap {
                            format!("synthetic=platform/minecraft/test-gradle-overlays/{id}")
                        } else {
                            format!("target=platform/minecraft/mc-version/{id}")
                        }],
                    }),
                }
                .invoke_in(&CancellationToken::new(), &repo)
                .unwrap();
                fs::create_dir_all(root.join("build/libs")).unwrap();
                let jar_name = format!("SFM-MC{minecraft_version}-{mod_version}.jar");
                let jar_path = root.join("build/libs").join(&jar_name);
                let jar_bytes = format!("synthetic JAR for {id}\n");
                fs::write(&jar_path, &jar_bytes).unwrap();
                let provenance_json = fs::read_to_string(root.join(MANIFEST_FILE)).unwrap();
                targets.push(CandidateTargetLock {
                    target_id: id.to_owned(),
                    minecraft_version: minecraft_version.to_owned(),
                    loader: loader.to_owned(),
                    loader_version: "1.2.3".to_owned(),
                    gradle_profile: "default".to_owned(),
                    production_task: expected_production_task(id).unwrap().to_owned(),
                    jdk_major: java_major,
                    jdk_build_id: format!("JBRSDK-{java_major}.0.1"),
                    provenance_manifest_sha256: sha256(provenance_json.as_bytes()),
                    production_jar_relative_path: format!("build/libs/{jar_name}"),
                    production_jar_sha256: sha256(jar_bytes.as_bytes()),
                });
                roots.insert(id.to_owned(), root);
            }
            let lock = SourceCandidateLock {
                schema: LOCK_SCHEMA.to_owned(),
                source_commit,
                source_manifest_sha256: sha256(manifest_json.as_bytes()),
                mod_version: mod_version.to_owned(),
                candidate_preset_id: format!("released-{mod_version}"),
                candidate_definition_identity: manifest.presets[0].identity.clone(),
                compatibility_evidence_relative_path: "docs/compatibility.md".to_owned(),
                compatibility_evidence_sha256: sha256(evidence),
                targets,
            };
            Self {
                _temp: temp,
                repo,
                roots,
                lock,
            }
        }

        /// Build the same ten checked-in-root repair shape as the real 4.34.0
        /// transition, but from tiny source inputs and synthetic production JARs.
        /// Only test code can access this fixture.
        pub(crate) fn new_repair_for_cli()
        -> (Self, PromotionRequest, String, BTreeMap<String, String>) {
            const REFMAP: &str = "src/main/resources/sfm.refmap.json";
            const OLD_IDENTITY: &str =
                "blake3:1cd6a9078deb867e527f16b11644b3c07e3d916bffa3edfe4aacb0b40c72f7bd";
            const REFMAP_CREATES: [&str; 5] = ["1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1"];
            let mut fixture = Self::new_with_version("4.34.0", true);
            let mut candidates = BTreeMap::new();
            let mut reviewed_operations = Vec::new();
            let mut gradle_overlays = BTreeMap::new();
            for (target, _, _, _) in TARGETS {
                let candidate_root = &fixture.roots[target];
                let candidate_bytes = fs::read(candidate_root.join(MANIFEST_FILE)).unwrap();
                let candidate_manifest =
                    ProjectionProvenance::from_json(std::str::from_utf8(&candidate_bytes).unwrap())
                        .unwrap();
                let destination = fixture
                    .repo
                    .join(format!("platform/minecraft/mc-version/{target}"));
                let overlay_relative = format!("platform/minecraft/test-gradle-overlays/{target}");
                let overlay = fixture.repo.join(&overlay_relative);
                for path in [
                    "gradle.properties",
                    "settings.gradle",
                    "sfm-toolchain.lock.json",
                    "gradle/wrapper/gradle-wrapper.properties",
                ] {
                    let output = overlay.join(path);
                    fs::create_dir_all(output.parent().unwrap()).unwrap();
                    fs::write(output, fs::read(destination.join(path)).unwrap()).unwrap();
                }
                gradle_overlays.insert(target.to_owned(), overlay_relative);
                let mut old_manifest = candidate_manifest;
                old_manifest.preset_definition_identity = OLD_IDENTITY.to_owned();
                if REFMAP_CREATES.contains(&target) {
                    old_manifest.files.remove(REFMAP);
                    reviewed_operations.push(ReviewedPromotionOperation {
                        target_id: target.to_owned(),
                        relative_path: REFMAP.to_owned(),
                        kind: ReviewedOperationKind::Create,
                        old_sha256: None,
                        new_sha256: sha256(&fs::read(candidate_root.join(REFMAP)).unwrap()),
                    });
                }
                for path in old_manifest.files.keys() {
                    let output = destination.join(path);
                    fs::create_dir_all(output.parent().unwrap()).unwrap();
                    fs::write(output, fs::read(candidate_root.join(path)).unwrap()).unwrap();
                }
                let old_bytes = old_manifest.to_json().unwrap().into_bytes();
                fs::write(destination.join(MANIFEST_FILE), &old_bytes).unwrap();
                reviewed_operations.push(ReviewedPromotionOperation {
                    target_id: target.to_owned(),
                    relative_path: MANIFEST_FILE.to_owned(),
                    kind: ReviewedOperationKind::Manifest,
                    old_sha256: Some(sha256(&old_bytes)),
                    new_sha256: sha256(&candidate_bytes),
                });
                let locked = fixture
                    .lock
                    .targets
                    .iter()
                    .find(|locked| locked.target_id == target)
                    .unwrap();
                candidates.insert(
                    target.to_owned(),
                    PromotionCandidate {
                        project_root: candidate_root.clone(),
                        reviewed_manifest_sha256: locked.provenance_manifest_sha256.clone(),
                        production_jar_relative_path: locked.production_jar_relative_path.clone(),
                        production_jar_sha256: locked.production_jar_sha256.clone(),
                        production_task: locked.production_task.clone(),
                        jdk_major: locked.jdk_major,
                        jdk_build_id: locked.jdk_build_id.clone(),
                    },
                );
            }
            git(&fixture.repo, &["add", "."]);
            git(
                &fixture.repo,
                &["commit", "-qm", "checked-in old projection roots"],
            );
            fixture.lock.source_commit =
                git(&fixture.repo, &["rev-parse", "HEAD"]).trim().to_owned();
            let refmap_sha256 = sha256(
                &fs::read(
                    fixture
                        .repo
                        .join("platform/minecraft/src/main/resources/sfm.refmap.json"),
                )
                .unwrap(),
            );
            let request = PromotionRequest {
                repository_root: fixture.repo.clone(),
                reviewed_head_commit: fixture.lock.source_commit.clone(),
                reviewed_source_manifest_sha256: fixture.lock.source_manifest_sha256.clone(),
                compatibility_evidence_relative_path: fixture
                    .lock
                    .compatibility_evidence_relative_path
                    .clone(),
                reviewed_compatibility_evidence_sha256: fixture
                    .lock
                    .compatibility_evidence_sha256
                    .clone(),
                candidates,
                candidate_preset_id: fixture.lock.candidate_preset_id.clone(),
                candidate_definition_identity: fixture.lock.candidate_definition_identity.clone(),
                transition: PromotionTransition::PreAcceptanceBaselineRepair {
                    expected_old_preset_id: "released-4.34.0".to_owned(),
                    expected_old_definition_identity: OLD_IDENTITY.to_owned(),
                    reviewed_operations,
                },
                accept_identical_edits: false,
            };
            (fixture, request, refmap_sha256, gradle_overlays)
        }

        pub(crate) fn lock(&self) -> &SourceCandidateLock {
            &self.lock
        }

        pub(crate) fn repo(&self) -> &Path {
            &self.repo
        }

        pub(crate) fn roots(&self) -> &BTreeMap<String, PathBuf> {
            &self.roots
        }

        fn verify(&self) -> Result<CandidateVerificationReport> {
            self.lock.verify_in(
                &self.repo,
                &self.roots,
                sha256(facet_json::to_string(&self.lock)?.as_bytes()),
            )
        }

        fn verify_release(&self) -> Result<()> {
            let lock_path = self._temp.path().join("portable-candidate-lock.json");
            fs::write(&lock_path, facet_json::to_string(&self.lock)?)?;
            let candidate_root = self
                .roots
                .iter()
                .map(|(target, path)| format!("{target}={}", path.display()))
                .collect();
            SourceArgs {
                command: SourceCommand::CandidateVerify(CandidateVerifyArgs {
                    repo_root: self.repo.clone(),
                    lock: lock_path,
                    candidate_root,
                }),
            }
            .invoke_in(&CancellationToken::new(), &self.repo)?;
            Ok(())
        }
    }

    fn git(repo: &Path, args: &[&str]) -> String {
        let output = Command::new("git")
            .arg("-C")
            .arg(repo)
            .args(args)
            .output()
            .unwrap();
        assert!(
            output.status.success(),
            "git {:?}: {}",
            args,
            String::from_utf8_lossy(&output.stderr)
        );
        String::from_utf8(output.stdout).unwrap()
    }

    #[test]
    fn verifies_ten_committed_targets_and_distinct_minecraft_version() {
        let fixture = Fixture::new();
        let json = facet_json::to_string(&fixture.lock).unwrap();
        let parsed = SourceCandidateLock::from_json(&json).unwrap();
        assert_eq!(parsed.targets.len(), 10);
        let report = fixture.verify().unwrap();
        assert_eq!(report.verified_targets.len(), 10);
        assert!(!report.deterministic_source_check);
        assert!(report.verified_targets.contains_key("1.21.0"));
        assert_eq!(fixture.lock.targets[7].minecraft_version, "1.21");
        fixture.verify_release().unwrap();
    }

    #[test]
    fn verifies_fictional_release_version_from_unchanged_synthetic_gradle_inputs() {
        let fixture = Fixture::new_with_release_version_override();
        assert_eq!(fixture.lock.mod_version, "9.99.99-fixture");
        assert_eq!(
            fs::read_to_string(fixture.repo.join(ROOT_GRADLE_PROPERTIES)).unwrap(),
            "mod_version=4.34.0\n"
        );
        for (target, root) in &fixture.roots {
            let original = fixture.repo.join(format!(
                "platform/minecraft/mc-version/{target}/gradle.properties"
            ));
            assert!(
                fs::read_to_string(original)
                    .unwrap()
                    .contains("mod_version=4.34.0\n")
            );
            assert!(
                fs::read_to_string(root.join("gradle.properties"))
                    .unwrap()
                    .contains("mod_version=9.99.99-fixture\n")
            );
            let provenance = ProjectionProvenance::from_json(
                &fs::read_to_string(root.join(MANIFEST_FILE)).unwrap(),
            )
            .unwrap();
            let gradle = &provenance.files["gradle.properties"];
            assert_ne!(gradle.source_sha256, gradle.output_sha256);
        }
        fixture.verify_release().unwrap();
        assert!(
            git(&fixture.repo, &["status", "--porcelain"])
                .trim()
                .is_empty()
        );

        let changed = fixture.roots["1.19.2"].join("gradle.properties");
        fs::write(
            changed,
            "minecraft_version=1.19.2\nmod_version=4.34.0\nneo_version=1.2.3\n",
        )
        .unwrap();
        assert!(fixture.verify().is_err());
    }

    #[test]
    fn lock_parser_rejects_missing_duplicate_unknown_and_nonportable_fields() {
        let mut fixture = Fixture::new();
        let json = facet_json::to_string(&fixture.lock).unwrap();
        let unknown = json.replacen(
            "\"schema\":",
            "\"project_root\":\"C:/private\",\"schema\":",
            1,
        );
        let _ = SourceCandidateLock::from_json(&unknown).unwrap_err();
        let duplicate_key = json.replacen(
            "\"schema\":",
            "\"schema\":\"sfm:source_candidate_lock@1\",\"schema\":",
            1,
        );
        let _ = SourceCandidateLock::from_json(&duplicate_key).unwrap_err();
        let last = fixture.lock.targets.pop().unwrap();
        let _ = SourceCandidateLock::from_json(&facet_json::to_string(&fixture.lock).unwrap())
            .unwrap_err();
        fixture.lock.targets.push(fixture.lock.targets[0].clone());
        let _ = SourceCandidateLock::from_json(&facet_json::to_string(&fixture.lock).unwrap())
            .unwrap_err();
        fixture.lock.targets.pop();
        fixture.lock.targets.push(last);
        fixture.lock.targets[0].production_jar_sha256 = "sha256:invalid".to_owned();
        let _ = SourceCandidateLock::from_json(&facet_json::to_string(&fixture.lock).unwrap())
            .unwrap_err();
    }

    #[test]
    fn rejects_unsafe_or_development_jar_and_wrong_task_or_jdk() {
        let mut fixture = Fixture::new();
        fixture.lock.targets[0].production_jar_relative_path =
            "build/libs/../escape.jar".to_owned();
        let _ = fixture.lock.validate().unwrap_err();
        fixture.lock.targets[0].production_jar_relative_path =
            "build/libs/SFM-MC1.19.2-4.35.0-dev.abc.jar".to_owned();
        let _ = fixture.lock.validate().unwrap_err();
        fixture.lock.targets[0].production_jar_relative_path =
            "build/libs/SFM-MC1.19.2-4.35.0.jar".to_owned();
        fixture.lock.targets[0].production_task = "jar".to_owned();
        let _ = fixture.lock.validate().unwrap_err();
        fixture.lock.targets[0].production_task = "reobfJar".to_owned();
        fixture.lock.targets[0].jdk_build_id = "temurin-21.0.8".to_owned();
        let _ = fixture.lock.validate().unwrap_err();
        fixture.lock.targets[0].jdk_major = 21;
        let _ = fixture.verify().unwrap_err();
    }

    #[test]
    fn rejects_changed_artifact_provenance_owned_output_and_version() {
        let mut fixture = Fixture::new();
        let candidate = &fixture.roots["1.20.2"];
        let jar = candidate.join(&fixture.lock.targets[4].production_jar_relative_path);
        let source_path = candidate.join("src/main/java/Candidate.java");
        let original_source = fs::read(&source_path).unwrap();
        let original_manifest = fs::read(candidate.join(MANIFEST_FILE)).unwrap();
        fs::write(&jar, "changed JAR").unwrap();
        assert!(
            fixture
                .verify()
                .unwrap_err()
                .to_string()
                .contains("JAR hash")
        );
        fs::write(&jar, "synthetic JAR for 1.20.2\n").unwrap();
        fs::write(&source_path, "edited").unwrap();
        assert!(
            fixture
                .verify()
                .unwrap_err()
                .to_string()
                .contains("output hash")
        );
        fs::write(&source_path, original_source).unwrap();
        fs::write(candidate.join(MANIFEST_FILE), "changed manifest").unwrap();
        assert!(
            fixture
                .verify()
                .unwrap_err()
                .to_string()
                .contains("manifest hash")
        );
        fs::write(candidate.join(MANIFEST_FILE), original_manifest).unwrap();
        fixture.lock.targets[4].minecraft_version = "1.20.1".to_owned();
        let _ = fixture.verify().unwrap_err();
    }

    #[test]
    fn rejects_changed_definition_evidence_or_missing_mapping() {
        let mut fixture = Fixture::new();
        fixture.lock.source_commit = "a".repeat(40);
        let _ = fixture.verify().unwrap_err();
        fixture.lock.source_commit = git(&fixture.repo, &["rev-parse", "HEAD"]).trim().to_owned();
        fs::write(fixture.repo.join("docs/compatibility.md"), "edited").unwrap();
        let _ = fixture.verify().unwrap_err();
        fs::write(
            fixture.repo.join("docs/compatibility.md"),
            "Reviewed compatibility differences.\n",
        )
        .unwrap();
        fixture.roots.remove("26.1.2");
        let _ = fixture.verify().unwrap_err();
    }

    #[test]
    fn rejects_staged_only_change_to_reviewed_evidence() {
        let fixture = Fixture::new();
        let path = fixture.repo.join("docs/compatibility.md");
        fs::write(&path, "staged change\n").unwrap();
        git(&fixture.repo, &["add", "docs/compatibility.md"]);
        fs::write(&path, "Reviewed compatibility differences.\n").unwrap();
        assert!(
            fixture
                .verify()
                .unwrap_err()
                .to_string()
                .contains("Git index")
        );
    }

    #[test]
    fn rejects_repository_release_version_drift() {
        let fixture = Fixture::new();
        fs::write(
            fixture.repo.join(ROOT_GRADLE_PROPERTIES),
            "mod_version=4.36.0\n",
        )
        .unwrap();
        let _ = fixture.verify().unwrap_err();
    }

    #[test]
    fn rejects_authored_source_drift_after_locked_commit() {
        let fixture = Fixture::new();
        fs::write(
            fixture
                .repo
                .join("platform/minecraft/src/main/java/Candidate.java"),
            "class Candidate { int drift; }\n",
        )
        .unwrap();
        assert!(
            fixture
                .verify_release()
                .unwrap_err()
                .to_string()
                .contains("differ from source commit")
        );
    }

    #[test]
    fn rejects_ignored_untracked_selected_source_input() {
        let fixture = Fixture::new();
        fs::write(
            fixture
                .repo
                .join("platform/minecraft/src/main/java/Hidden.java"),
            "class Hidden {}\n",
        )
        .unwrap();
        assert!(
            fixture
                .verify_release()
                .unwrap_err()
                .to_string()
                .contains("not tracked at source commit")
        );
    }

    #[test]
    fn truncated_self_declared_manifest_is_not_deterministic_projection() {
        let mut fixture = Fixture::new();
        let candidate = &fixture.roots["1.20.2"];
        let manifest_path = candidate.join(MANIFEST_FILE);
        let mut manifest =
            ProjectionProvenance::from_json(&fs::read_to_string(&manifest_path).unwrap()).unwrap();
        manifest
            .files
            .remove("src/main/java/Candidate.java")
            .unwrap();
        let bytes = manifest.to_json().unwrap();
        fs::write(&manifest_path, &bytes).unwrap();
        fs::remove_file(candidate.join("src/main/java/Candidate.java")).unwrap();
        fixture.lock.targets[4].provenance_manifest_sha256 = sha256(bytes.as_bytes());
        fixture.verify().unwrap(); // Exact-byte inventory alone accepts this self-declaration.
        assert!(
            fixture
                .verify_release()
                .unwrap_err()
                .to_string()
                .contains("source projection")
        );
    }

    #[test]
    fn accepts_game_test_output_but_rejects_unowned_resource_and_gradle_inputs() {
        let fixture = Fixture::new();
        let candidate = &fixture.roots["1.20.2"];
        fs::create_dir_all(candidate.join("runGameTest/world")).unwrap();
        fs::write(candidate.join("runGameTest/world/level.dat"), "runtime\n").unwrap();
        fixture.verify_release().unwrap();
        fs::create_dir_all(candidate.join("src/main/resources")).unwrap();
        fs::write(candidate.join("src/main/resources/rogue.json"), "{}\n").unwrap();
        assert!(
            fixture
                .verify_release()
                .unwrap_err()
                .to_string()
                .contains("unowned candidate project input")
        );
        fs::remove_file(candidate.join("src/main/resources/rogue.json")).unwrap();
        fs::write(candidate.join("gradle/rogue.gradle"), "// rogue\n").unwrap();
        assert!(
            fixture
                .verify_release()
                .unwrap_err()
                .to_string()
                .contains("unowned candidate project input")
        );
    }

    #[test]
    fn declared_project_root_example_is_owned_but_unlisted_neighbor_is_not() {
        let root = tempfile::tempdir().unwrap();
        fs::create_dir(root.path().join("examples")).unwrap();
        fs::write(root.path().join("examples/01.sfm"), b"example\n").unwrap();
        let owned = BTreeMap::from([(
            "examples/01.sfm".to_owned(),
            super::super::provenance::ProjectedFileProvenance {
                source_path: "examples/01.sfm".to_owned(),
                source_sha256: sha256(b"example\n"),
                overlay: Some("release-tag-examples".to_owned()),
                output_sha256: sha256(b"example\n"),
            },
        )]);
        ensure_closed_candidate_inputs(root.path(), &owned).unwrap();
        fs::write(root.path().join("examples/unlisted.sfm"), b"unowned\n").unwrap();
        let error = ensure_closed_candidate_inputs(root.path(), &owned)
            .unwrap_err()
            .to_string();
        assert!(error.contains("unowned candidate project input"), "{error}");
    }

    #[test]
    fn rejects_reparse_jar_when_symlinks_are_available() {
        let fixture = Fixture::new();
        let candidate = &fixture.roots["1.19.2"];
        let jar = candidate.join(&fixture.lock.targets[0].production_jar_relative_path);
        let replacement = candidate.join("build/libs/replacement.jar");
        fs::rename(&jar, &replacement).unwrap();
        #[cfg(windows)]
        let result = std::os::windows::fs::symlink_file(&replacement, &jar);
        #[cfg(unix)]
        let result = std::os::unix::fs::symlink(&replacement, &jar);
        if result.is_err() {
            return;
        }
        assert!(
            fixture
                .verify()
                .unwrap_err()
                .to_string()
                .contains("reparse point")
        );
    }
}
