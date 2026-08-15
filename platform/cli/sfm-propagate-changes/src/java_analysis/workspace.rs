use super::JavaAnalysisContextOutput;
use super::JavaAnalysisDiagnosticOutput;
use super::JavaClasspathMode;
use super::JavaSourceExclusionOutput;
use super::JavaSourceRootKind;
use super::JavaSourceRootOutput;
use super::JavaSourceSetOutput;
use super::is_excluded_java_source;
use super::read_version_source_excludes;
use crate::branch_targets::select_single_worktree_target;
use crate::cli::jar::BranchSelector;
use crate::java_source_catalog::CatalogJavaSourceRootKind;
use crate::java_source_catalog::JAVA_SOURCE_CATALOG;
use crate::java_source_catalog::JavaSourceCatalog;
use crate::toolchain_lockfile_schema::read_current;
use crate::toolchain_lockfile_schema::read_profile_source_excludes;
use eyre::Context as _;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::path::Path;
use std::path::PathBuf;

pub const JAVA_PARSER_FINGERPRINT: &str = "arborium-java/2.18.1";

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct JavaSourceFile {
    pub absolute_path: PathBuf,
    pub root_id: String,
    pub root_relative_path: String,
    pub report_path: String,
    pub source_set: String,
    /// Request-scoped current text. Ordinary workspaces read `absolute_path`;
    /// editor-location requests may replace exactly one document without
    /// mutating the source tree.
    pub source_override: Option<String>,
}

/// Filesystem authority corresponding one-to-one with a public source-root
/// projection. This stays out of ordinary reports so absolute paths are only
/// disclosed by explicitly local worker handshakes.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct JavaSourceRootAuthority {
    pub root_id: String,
    pub source_set: String,
    pub canonical_absolute_path: PathBuf,
    pub report_root_path: String,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct JavaSourceWorkspace {
    pub context: JavaAnalysisContextOutput,
    /// Ordered identically to `context.source_roots`.
    pub root_authorities: Vec<JavaSourceRootAuthority>,
    pub files: Vec<JavaSourceFile>,
    pub diagnostics: Vec<JavaAnalysisDiagnosticOutput>,
    /// Stable lockfile identities only. These are evidence for branch-mode
    /// resolution, not an instruction to acquire or build missing artifacts.
    pub classpath_entries: Vec<String>,
}

impl JavaSourceWorkspace {
    /// Resolve the branch context and collect every selected Java source file
    /// without invoking Gradle, javac, dependency acquisition, or a build.
    ///
    /// # Errors
    ///
    /// Returns an error when branch selection, lockfile/toolchain context, or
    /// source-root discovery fails.
    pub fn resolve(
        branch: BranchSelector,
        source_roots: &[PathBuf],
        classpath_mode: JavaClasspathMode,
        invocation_dir: &Path,
    ) -> eyre::Result<Self> {
        let target = select_single_worktree_target(&branch.into_query()?)?;
        let resolved_branch = target.branch.to_string();
        let minecraft_version = target
            .mc_version
            .as_ref()
            .ok_or_else(|| eyre::eyre!("branch `{}` has no Minecraft version", target.branch))?
            .as_str()
            .to_owned();
        let worktree = target.worktree_path.as_path();
        let minecraft_dir = worktree.join("platform").join("minecraft");
        let java_release = read_java_release(&minecraft_dir, &minecraft_version)?;

        let (classpath_entries, classpath_fingerprint) = match classpath_mode {
            JavaClasspathMode::Branch => branch_classpath_context(&minecraft_dir)?,
            JavaClasspathMode::Isolated => (Vec::new(), fingerprint(["isolated"])),
        };

        let mut workspace = if source_roots.is_empty() {
            collect_branch_workspace(
                &resolved_branch,
                worktree,
                &minecraft_dir,
                &minecraft_version,
                &java_release,
                classpath_mode,
                classpath_entries,
                classpath_fingerprint,
            )?
        } else {
            collect_custom_workspace(
                &resolved_branch,
                &minecraft_version,
                &java_release,
                source_roots,
                invocation_dir,
                classpath_mode,
                classpath_entries,
                classpath_fingerprint,
            )?
        };
        workspace.files.sort_by(|left, right| {
            left.report_path
                .cmp(&right.report_path)
                .then(left.source_set.cmp(&right.source_set))
        });
        Ok(workspace)
    }

    #[must_use]
    pub fn is_visible(&self, from: &str, target: &str) -> bool {
        self.context
            .source_sets
            .iter()
            .find(|source_set| source_set.id == from)
            .is_some_and(|source_set| {
                source_set
                    .visible_source_sets
                    .iter()
                    .any(|visible| visible == target)
            })
    }

    /// Resolve a source without joining caller text onto a filesystem root.
    /// Exact report paths win; otherwise a unique root-relative path is
    /// accepted.
    ///
    /// # Errors
    ///
    /// Returns an error when the path is empty, escapes a root, is absent, or
    /// is ambiguous across selected roots.
    pub fn source_file(&self, source_path: &str) -> eyre::Result<&JavaSourceFile> {
        let source_path = normalize_requested_source_path(source_path)?;
        let exact = self
            .files
            .iter()
            .filter(|file| file.report_path == source_path)
            .collect::<Vec<_>>();
        match exact.as_slice() {
            [file] => return Ok(file),
            [] => {}
            _ => eyre::bail!("Java source path `{source_path}` matched multiple report paths"),
        }

        let relative = self
            .files
            .iter()
            .filter(|file| file.root_relative_path == source_path)
            .collect::<Vec<_>>();
        match relative.as_slice() {
            [file] => Ok(file),
            [] => eyre::bail!("Java source path `{source_path}` is not in the selected workspace"),
            files => {
                let matches = files
                    .iter()
                    .map(|file| {
                        format!(
                            "{} ({}, {})",
                            file.report_path, file.root_id, file.source_set
                        )
                    })
                    .collect::<Vec<_>>()
                    .join(", ");
                eyre::bail!(
                    "Java source path `{source_path}` is ambiguous across roots: {matches}. Pass an exact report path or a single --source-root"
                )
            }
        }
    }

    /// Resolve the public location-query grammar strictly as a source-root
    /// relative path. An optional root ID disambiguates identical relative
    /// paths without overloading the argument with report-path semantics.
    ///
    /// # Errors
    ///
    /// Returns an error when the path is malformed, absent, or ambiguous.
    pub fn source_file_at(
        &self,
        root_id: Option<&str>,
        root_relative_path: &str,
    ) -> eyre::Result<&JavaSourceFile> {
        let relative = normalize_requested_source_path(root_relative_path)?;
        let matches = self
            .files
            .iter()
            .filter(|file| {
                file.root_relative_path == relative
                    && root_id.is_none_or(|requested| file.root_id == requested)
            })
            .collect::<Vec<_>>();
        match matches.as_slice() {
            [file] => Ok(file),
            [] => {
                if let Some(root_id) = root_id {
                    eyre::bail!(
                        "Java source `{root_id}:{relative}` is not in the selected workspace"
                    );
                }
                eyre::bail!("Java source path `{relative}` is not in the selected workspace")
            }
            files => {
                let roots = files
                    .iter()
                    .map(|file| file.root_id.as_str())
                    .collect::<Vec<_>>()
                    .join(", ");
                eyre::bail!(
                    "Java source path `{relative}` is ambiguous across roots [{roots}]; pass --source-root-id"
                )
            }
        }
    }

    /// Replace one exact workspace document with immutable caller-supplied
    /// text for this analysis snapshot.
    ///
    /// # Errors
    ///
    /// Returns an error when the root-relative identity is absent or not
    /// unique.
    pub fn with_source_overlay(
        mut self,
        root_id: &str,
        root_relative_path: &str,
        source: String,
    ) -> eyre::Result<Self> {
        let relative = normalize_requested_source_path(root_relative_path)?;
        let matches = self
            .files
            .iter()
            .enumerate()
            .filter(|(_, file)| file.root_id == root_id && file.root_relative_path == relative)
            .map(|(index, _)| index)
            .collect::<Vec<_>>();
        let [index] = matches.as_slice() else {
            eyre::bail!(
                "Java source `{root_id}:{relative}` matched {} workspace files",
                matches.len()
            );
        };
        self.files[*index].source_override = Some(source);
        Ok(self)
    }
}

#[expect(
    clippy::too_many_arguments,
    reason = "branch resolution passes one explicit immutable analysis context"
)]
fn collect_branch_workspace(
    branch: &str,
    worktree: &Path,
    minecraft_dir: &Path,
    minecraft_version: &str,
    java_release: &str,
    classpath_mode: JavaClasspathMode,
    classpath_entries: Vec<String>,
    classpath_fingerprint: String,
) -> eyre::Result<JavaSourceWorkspace> {
    collect_branch_workspace_with_catalog(
        branch,
        worktree,
        minecraft_dir,
        minecraft_version,
        java_release,
        classpath_mode,
        classpath_entries,
        classpath_fingerprint,
        JAVA_SOURCE_CATALOG,
    )
}

#[expect(
    clippy::too_many_arguments,
    reason = "branch resolution passes one explicit immutable analysis context"
)]
fn collect_branch_workspace_with_catalog(
    branch: &str,
    worktree: &Path,
    minecraft_dir: &Path,
    minecraft_version: &str,
    java_release: &str,
    classpath_mode: JavaClasspathMode,
    classpath_entries: Vec<String>,
    classpath_fingerprint: String,
    catalog: JavaSourceCatalog,
) -> eyre::Result<JavaSourceWorkspace> {
    catalog.validate()?;
    let lockfile_path = minecraft_dir.join("sfm-toolchain.lock.json");
    let lockfile_text = std::fs::read_to_string(&lockfile_path)
        .wrap_err_with(|| format!("Failed to read {}", lockfile_path.display()))?;
    let feature_excludes = read_profile_source_excludes(&lockfile_text, "rust-toolchain")?;
    let mut feature_excludes_by_set = BTreeMap::<String, Vec<String>>::new();
    for (source_set, path) in &feature_excludes {
        feature_excludes_by_set
            .entry(normalize_source_set_id(source_set)?)
            .or_default()
            .push(normalize_slashes(path));
    }

    let mut roots = Vec::new();
    let mut root_authorities = Vec::new();
    let mut exclusions = Vec::new();
    let mut files = Vec::new();
    let mut excludes_by_set = BTreeMap::<String, Vec<String>>::new();
    for source_set in catalog.source_sets {
        let mut excludes =
            read_version_source_excludes(minecraft_dir, minecraft_version, source_set.id)?;
        for path in &excludes {
            exclusions.push(JavaSourceExclusionOutput {
                source_set: source_set.id.to_owned(),
                path: path.clone(),
                origin: "version-source-excludes".to_owned(),
            });
        }
        if let Some(feature) = feature_excludes_by_set.get(source_set.id) {
            for path in feature {
                exclusions.push(JavaSourceExclusionOutput {
                    source_set: source_set.id.to_owned(),
                    path: path.clone(),
                    origin: "inactive-lockfile-feature".to_owned(),
                });
            }
            excludes.extend(feature.iter().cloned());
        }
        excludes.sort();
        excludes.dedup();
        excludes_by_set.insert(source_set.id.to_owned(), excludes);
    }

    collect_catalog_roots(
        catalog,
        minecraft_dir,
        worktree,
        &excludes_by_set,
        &mut roots,
        &mut root_authorities,
        &mut files,
    )?;

    exclusions.sort();
    deduplicate_files(&mut files)?;
    root_authorities.sort_by(|left, right| left.root_id.cmp(&right.root_id));
    let context = context(
        branch,
        minecraft_version,
        java_release,
        roots,
        declared_source_set_outputs(catalog),
        exclusions,
        classpath_mode,
        classpath_fingerprint,
        &files,
    );
    Ok(JavaSourceWorkspace {
        context,
        root_authorities,
        files,
        diagnostics: Vec::new(),
        classpath_entries,
    })
}

fn collect_catalog_roots(
    catalog: JavaSourceCatalog,
    minecraft_dir: &Path,
    worktree: &Path,
    excludes_by_set: &BTreeMap<String, Vec<String>>,
    roots: &mut Vec<JavaSourceRootOutput>,
    root_authorities: &mut Vec<JavaSourceRootAuthority>,
    files: &mut Vec<JavaSourceFile>,
) -> eyre::Result<()> {
    for declaration in catalog.roots {
        let root = declaration.resolve(minecraft_dir);
        let report_root_path = worktree_relative(worktree, &root);
        root_authorities.push(JavaSourceRootAuthority {
            root_id: declaration.id.to_owned(),
            source_set: declaration.source_set.to_owned(),
            canonical_absolute_path: canonical_absolute_path(&root)?,
            report_root_path: report_root_path.clone(),
        });
        roots.push(root_output(
            declaration.id.to_owned(),
            declaration.source_set,
            report_root_path,
            match declaration.kind {
                CatalogJavaSourceRootKind::Declared => JavaSourceRootKind::Declared,
                CatalogJavaSourceRootKind::Generated => JavaSourceRootKind::Generated,
            },
            root.is_dir(),
        ));
        let excludes = declaration
            .honors_source_excludes
            .then(|| excludes_by_set.get(declaration.source_set))
            .flatten()
            .map_or(&[][..], Vec::as_slice);
        collect_files(
            &root,
            worktree,
            declaration.id,
            declaration.source_set,
            excludes,
            files,
        )?;
    }
    Ok(())
}

#[expect(
    clippy::too_many_arguments,
    reason = "custom-root resolution passes one explicit immutable analysis context"
)]
fn collect_custom_workspace(
    branch: &str,
    minecraft_version: &str,
    java_release: &str,
    source_roots: &[PathBuf],
    invocation_dir: &Path,
    classpath_mode: JavaClasspathMode,
    classpath_entries: Vec<String>,
    classpath_fingerprint: String,
) -> eyre::Result<JavaSourceWorkspace> {
    let mut roots = Vec::new();
    let mut root_authorities = Vec::new();
    let mut files = Vec::new();
    let mut selected_source_sets = BTreeSet::new();
    let mut seen_roots = BTreeSet::new();
    for (index, source_root) in source_roots.iter().enumerate() {
        let absolute = if source_root.is_absolute() {
            source_root.clone()
        } else {
            invocation_dir.join(source_root)
        };
        let absolute = dunce::canonicalize(&absolute)
            .wrap_err_with(|| format!("Failed to resolve source root {}", absolute.display()))?;
        if !seen_roots.insert(absolute.clone()) {
            continue;
        }
        let display_root = if source_root.is_absolute() {
            format!("custom-{index}")
        } else {
            normalize_slashes(&source_root.to_string_lossy())
        };
        let source_set = custom_root_source_set(source_root);
        selected_source_sets.insert(source_set.clone());
        roots.push(root_output(
            format!("custom-{index}"),
            &source_set,
            display_root.clone(),
            JavaSourceRootKind::Custom,
            true,
        ));
        root_authorities.push(JavaSourceRootAuthority {
            root_id: format!("custom-{index}"),
            source_set: source_set.clone(),
            canonical_absolute_path: absolute.clone(),
            report_root_path: display_root.clone(),
        });
        collect_files_with_prefix(
            &absolute,
            &display_root,
            &format!("custom-{index}"),
            &source_set,
            &[],
            &mut files,
        )?;
    }
    deduplicate_files(&mut files)?;
    root_authorities.sort_by(|left, right| left.root_id.cmp(&right.root_id));
    let source_sets = custom_source_set_outputs(&selected_source_sets);
    let context = context(
        branch,
        minecraft_version,
        java_release,
        roots,
        source_sets,
        Vec::new(),
        classpath_mode,
        classpath_fingerprint,
        &files,
    );
    Ok(JavaSourceWorkspace {
        context,
        root_authorities,
        files,
        diagnostics: Vec::new(),
        classpath_entries,
    })
}

fn custom_root_source_set(root: &Path) -> String {
    let is_java_root = root
        .file_name()
        .and_then(|value| value.to_str())
        .is_some_and(|value| value.eq_ignore_ascii_case("java"));
    let candidate = is_java_root
        .then(|| root.parent()?.file_name()?.to_str())
        .flatten();
    candidate
        .and_then(|candidate| {
            JAVA_SOURCE_CATALOG
                .source_sets
                .iter()
                .find(|known| candidate.eq_ignore_ascii_case(known.id))
                .map(|known| known.id.to_owned())
        })
        .unwrap_or_else(|| "custom".to_owned())
}

fn custom_source_set_outputs(selected: &BTreeSet<String>) -> Vec<JavaSourceSetOutput> {
    selected
        .iter()
        .map(|id| {
            let visible_source_sets = JAVA_SOURCE_CATALOG.source_set(id).map_or_else(
                || vec![id.clone()],
                |known| {
                    known
                        .visible_source_sets
                        .iter()
                        .filter(|candidate| selected.contains(**candidate))
                        .map(|candidate| (*candidate).to_owned())
                        .collect()
                },
            );
            JavaSourceSetOutput {
                id: id.clone(),
                visible_source_sets,
            }
        })
        .collect()
}

#[expect(
    clippy::too_many_arguments,
    reason = "the versioned report context is assembled at one deterministic boundary"
)]
fn context(
    branch: &str,
    minecraft_version: &str,
    java_release: &str,
    mut roots: Vec<JavaSourceRootOutput>,
    mut source_sets: Vec<JavaSourceSetOutput>,
    mut exclusions: Vec<JavaSourceExclusionOutput>,
    classpath_mode: JavaClasspathMode,
    classpath_fingerprint: String,
    files: &[JavaSourceFile],
) -> JavaAnalysisContextOutput {
    roots.sort_by(|left, right| left.id.cmp(&right.id));
    source_sets.sort_by(|left, right| left.id.cmp(&right.id));
    for source_set in &mut source_sets {
        source_set.visible_source_sets.sort();
        source_set.visible_source_sets.dedup();
    }
    exclusions.sort();
    let workspace_fingerprint = fingerprint(
        files
            .iter()
            .map(|file| format!("{}:{}", file.source_set, file.report_path)),
    );
    JavaAnalysisContextOutput {
        branch: branch.to_owned(),
        minecraft_version: minecraft_version.to_owned(),
        java_release: java_release.to_owned(),
        jdk: format!("java-{java_release}"),
        source_roots: roots,
        source_sets,
        source_exclusions: exclusions,
        classpath_mode,
        classpath_fingerprint,
        parser_fingerprint: JAVA_PARSER_FINGERPRINT.to_owned(),
        index_fingerprint: workspace_fingerprint,
    }
}

fn declared_source_set_outputs(catalog: JavaSourceCatalog) -> Vec<JavaSourceSetOutput> {
    catalog
        .source_sets
        .iter()
        .map(|source_set| JavaSourceSetOutput {
            id: source_set.id.to_owned(),
            visible_source_sets: source_set
                .visible_source_sets
                .iter()
                .map(|value| (*value).to_owned())
                .collect(),
        })
        .collect()
}

fn branch_classpath_context(minecraft_dir: &Path) -> eyre::Result<(Vec<String>, String)> {
    let lockfile_path = minecraft_dir.join("sfm-toolchain.lock.json");
    let input = std::fs::read_to_string(&lockfile_path)
        .wrap_err_with(|| format!("Failed to read {}", lockfile_path.display()))?;
    let lockfile = read_current(&input)?;
    let mut entries = lockfile
        .artifacts
        .iter()
        .map(|artifact| format!("{}@{}", artifact.id, artifact.hash))
        .collect::<Vec<_>>();
    entries.sort();
    entries.dedup();
    let fingerprint = fingerprint(entries.iter().map(String::as_str));
    Ok((entries, fingerprint))
}

fn collect_files(
    root: &Path,
    report_base: &Path,
    root_id: &str,
    source_set: &str,
    excludes: &[String],
    output: &mut Vec<JavaSourceFile>,
) -> eyre::Result<()> {
    if !root.is_dir() {
        return Ok(());
    }
    for entry in walkdir::WalkDir::new(root).follow_links(false) {
        let entry = entry.wrap_err_with(|| format!("Failed to walk {}", root.display()))?;
        if !entry.file_type().is_file()
            || entry.path().extension().and_then(|value| value.to_str()) != Some("java")
        {
            continue;
        }
        let relative_to_root = path_relative(root, entry.path())?;
        if is_excluded_java_source(&relative_to_root, excludes) {
            continue;
        }
        output.push(JavaSourceFile {
            absolute_path: entry.path().to_path_buf(),
            root_id: root_id.to_owned(),
            root_relative_path: relative_to_root,
            report_path: path_relative(report_base, entry.path())?,
            source_set: source_set.to_owned(),
            source_override: None,
        });
    }
    Ok(())
}

fn collect_files_with_prefix(
    root: &Path,
    report_prefix: &str,
    root_id: &str,
    source_set: &str,
    excludes: &[String],
    output: &mut Vec<JavaSourceFile>,
) -> eyre::Result<()> {
    if !root.is_dir() {
        return Ok(());
    }
    for entry in walkdir::WalkDir::new(root).follow_links(false) {
        let entry = entry.wrap_err_with(|| format!("Failed to walk {}", root.display()))?;
        if !entry.file_type().is_file()
            || entry.path().extension().and_then(|value| value.to_str()) != Some("java")
        {
            continue;
        }
        let relative = path_relative(root, entry.path())?;
        if is_excluded_java_source(&relative, excludes) {
            continue;
        }
        output.push(JavaSourceFile {
            absolute_path: entry.path().to_path_buf(),
            root_id: root_id.to_owned(),
            root_relative_path: relative.clone(),
            report_path: format!("{}/{relative}", report_prefix.trim_end_matches('/')),
            source_set: source_set.to_owned(),
            source_override: None,
        });
    }
    Ok(())
}

fn deduplicate_files(files: &mut Vec<JavaSourceFile>) -> eyre::Result<()> {
    let mut unique = BTreeMap::<PathBuf, JavaSourceFile>::new();
    for file in std::mem::take(files) {
        match unique.get_mut(&file.absolute_path) {
            Some(existing) if existing.source_set != file.source_set => eyre::bail!(
                "Java source {} was assigned to conflicting source sets `{}` and `{}`",
                file.absolute_path.display(),
                existing.source_set,
                file.source_set
            ),
            Some(existing) if file.report_path < existing.report_path => *existing = file,
            Some(_) => {}
            None => {
                unique.insert(file.absolute_path.clone(), file);
            }
        }
    }
    files.extend(unique.into_values());
    files.sort_by(|left, right| {
        left.report_path
            .cmp(&right.report_path)
            .then(left.source_set.cmp(&right.source_set))
    });
    Ok(())
}

fn root_output(
    id: String,
    source_set: &str,
    path: String,
    kind: JavaSourceRootKind,
    exists: bool,
) -> JavaSourceRootOutput {
    JavaSourceRootOutput {
        id,
        source_set: source_set.to_owned(),
        path,
        kind,
        exists,
    }
}

/// Resolve an absolute, symlink-normalized path even when the final generated
/// source directory does not exist yet. The nearest existing ancestor is
/// canonicalized before the missing suffix is appended.
fn canonical_absolute_path(path: &Path) -> eyre::Result<PathBuf> {
    let mut existing = path;
    let mut missing = Vec::new();
    while !existing.exists() {
        let name = existing.file_name().ok_or_else(|| {
            eyre::eyre!("Could not find an existing ancestor for {}", path.display())
        })?;
        missing.push(name.to_os_string());
        existing = existing.parent().ok_or_else(|| {
            eyre::eyre!("Could not find an existing ancestor for {}", path.display())
        })?;
    }
    let mut canonical = dunce::canonicalize(existing).wrap_err_with(|| {
        format!(
            "Failed to canonicalize source root ancestor {}",
            existing.display()
        )
    })?;
    for component in missing.into_iter().rev() {
        canonical.push(component);
    }
    Ok(canonical)
}

fn read_java_release(minecraft_dir: &Path, minecraft_version: &str) -> eyre::Result<String> {
    let path = minecraft_dir
        .join("gradle")
        .join("java-toolchain")
        .join(minecraft_version)
        .join("java-toolchain.gradle");
    let input = std::fs::read_to_string(&path)
        .wrap_err_with(|| format!("Failed to read {}", path.display()))?;
    let marker = "JavaLanguageVersion.of(";
    let start = input
        .find(marker)
        .ok_or_else(|| eyre::eyre!("{} does not declare JavaLanguageVersion.of", path.display()))?
        + marker.len();
    let tail = &input[start..];
    let end = tail.find(')').ok_or_else(|| {
        eyre::eyre!(
            "{} has an unterminated Java language version",
            path.display()
        )
    })?;
    let release = tail[..end].trim();
    if release.is_empty() || !release.chars().all(|character| character.is_ascii_digit()) {
        eyre::bail!(
            "{} has invalid Java language version `{release}`",
            path.display()
        );
    }
    Ok(release.to_owned())
}

fn normalize_source_set_id(value: &str) -> eyre::Result<String> {
    let value = value.strip_suffix("-java").unwrap_or(value);
    if JAVA_SOURCE_CATALOG.source_set(value).is_some() {
        Ok(value.to_owned())
    } else {
        eyre::bail!("lockfile feature references unknown Java source set `{value}`")
    }
}

fn worktree_relative(worktree: &Path, path: &Path) -> String {
    path.strip_prefix(worktree).map_or_else(
        |_| normalize_slashes(&path.to_string_lossy()),
        |relative| normalize_slashes(&relative.to_string_lossy()),
    )
}

fn path_relative(root: &Path, path: &Path) -> eyre::Result<String> {
    let relative = path
        .strip_prefix(root)
        .wrap_err_with(|| format!("{} is not beneath {}", path.display(), root.display()))?;
    Ok(normalize_slashes(&relative.to_string_lossy()))
}

fn normalize_slashes(value: &str) -> String {
    value.replace('\\', "/")
}

fn normalize_requested_source_path(value: &str) -> eyre::Result<String> {
    let value = normalize_slashes(value.trim());
    if value.is_empty() {
        eyre::bail!("Java source path cannot be empty");
    }
    if value.starts_with('/')
        || value
            .split('/')
            .any(|segment| segment.is_empty() || matches!(segment, "." | ".."))
    {
        eyre::bail!("Java source path `{value}` is not a normalized relative path");
    }
    Ok(value)
}

fn fingerprint(values: impl IntoIterator<Item = impl AsRef<str>>) -> String {
    let mut hasher = blake3::Hasher::new();
    for value in values {
        let value = value.as_ref().as_bytes();
        hasher.update(&(value.len() as u64).to_le_bytes());
        hasher.update(value);
    }
    format!("blake3:{}", hasher.finalize().to_hex())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn java_analysis_workspace_visibility_is_directed() {
        let workspace = JavaSourceWorkspace {
            context: context(
                "1.19.2",
                "1.19.2",
                "17",
                Vec::new(),
                declared_source_set_outputs(JAVA_SOURCE_CATALOG),
                Vec::new(),
                JavaClasspathMode::Isolated,
                fingerprint(["isolated"]),
                &[],
            ),
            root_authorities: Vec::new(),
            files: Vec::new(),
            diagnostics: Vec::new(),
            classpath_entries: Vec::new(),
        };
        assert!(workspace.is_visible("test", "main"));
        assert!(!workspace.is_visible("main", "test"));
        assert!(workspace.is_visible("gametest", "main"));
    }

    #[test]
    fn java_analysis_workspace_custom_roots_are_sorted_and_isolated() {
        let directory = tempfile::tempdir().expect("temporary scenario");
        let source = directory.path().join("source");
        std::fs::create_dir_all(source.join("z")).expect("source directory");
        std::fs::create_dir_all(source.join("a")).expect("source directory");
        std::fs::write(source.join("z/Z.java"), "package z; class Z {}").expect("source file");
        std::fs::write(source.join("a/A.java"), "package a; class A {}").expect("source file");

        let workspace = collect_custom_workspace(
            "1.19.2",
            "1.19.2",
            "17",
            &[PathBuf::from("source")],
            directory.path(),
            JavaClasspathMode::Isolated,
            Vec::new(),
            fingerprint(["isolated"]),
        )
        .expect("custom workspace");
        let paths = workspace
            .files
            .iter()
            .map(|file| file.report_path.as_str())
            .collect::<Vec<_>>();
        assert_eq!(paths, ["source/a/A.java", "source/z/Z.java"]);
        assert!(workspace.classpath_entries.is_empty());
        assert_eq!(
            workspace.context.classpath_mode,
            JavaClasspathMode::Isolated
        );
    }

    #[test]
    fn java_analysis_workspace_rejects_unknown_feature_source_sets() {
        let _error = normalize_source_set_id("mystery-java")
            .expect_err("unknown source set must be rejected");
        assert_eq!(normalize_source_set_id("main-java").expect("known"), "main");
    }

    #[test]
    fn java_analysis_workspace_custom_gradle_roots_retain_source_set_visibility() {
        let selected = BTreeSet::from(["main".to_owned(), "test".to_owned()]);
        let sets = custom_source_set_outputs(&selected);
        let main = sets.iter().find(|set| set.id == "main").expect("main");
        let test = sets.iter().find(|set| set.id == "test").expect("test");
        assert_eq!(main.visible_source_sets, ["main"]);
        assert_eq!(test.visible_source_sets, ["test", "main"]);
        assert_eq!(
            custom_root_source_set(Path::new("source/main/java")),
            "main"
        );
        assert_eq!(
            custom_root_source_set(Path::new("source/arbitrary")),
            "custom"
        );
    }

    #[test]
    fn java_analysis_workspace_deduplicates_repeated_custom_roots() {
        let directory = tempfile::tempdir().expect("temporary scenario");
        let source = directory.path().join("source");
        std::fs::create_dir_all(source.join("example")).expect("source directory");
        std::fs::write(source.join("example/A.java"), "package example; class A {}")
            .expect("source file");
        let workspace = collect_custom_workspace(
            "1.19.2",
            "1.19.2",
            "17",
            &[PathBuf::from("source"), PathBuf::from("source")],
            directory.path(),
            JavaClasspathMode::Isolated,
            Vec::new(),
            fingerprint(["isolated"]),
        )
        .expect("duplicate roots should be idempotent");
        assert_eq!(workspace.files.len(), 1);
        assert_eq!(workspace.context.source_roots.len(), 1);
    }

    #[test]
    fn java_analysis_workspace_resolves_exact_or_unique_document_paths() {
        let directory = tempfile::tempdir().expect("temporary scenario");
        for root in ["source-a", "source-b"] {
            let package = directory.path().join(root).join("example");
            std::fs::create_dir_all(&package).expect("source directory");
            std::fs::write(package.join("A.java"), "package example; class A {}")
                .expect("source file");
        }
        let workspace = collect_custom_workspace(
            "1.19.2",
            "1.19.2",
            "17",
            &[PathBuf::from("source-a"), PathBuf::from("source-b")],
            directory.path(),
            JavaClasspathMode::Isolated,
            Vec::new(),
            fingerprint(["isolated"]),
        )
        .expect("custom workspace");

        let _ = workspace.source_file("example/A.java").unwrap_err();
        let _ = workspace
            .source_file_at(None, "example/A.java")
            .unwrap_err();
        let exact = workspace
            .source_file("source-a/example/A.java")
            .expect("exact report path");
        assert_eq!(exact.root_id, "custom-0");
        assert_eq!(exact.root_relative_path, "example/A.java");
        let rooted = workspace
            .source_file_at(Some("custom-1"), "example/A.java")
            .expect("root-qualified relative path");
        assert_eq!(rooted.report_path, "source-b/example/A.java");
        let _ = workspace.source_file("../example/A.java").unwrap_err();
    }

    #[test]
    fn java_analysis_workspace_discovers_new_catalog_roots_deterministically() {
        use crate::java_source_catalog::JavaBuildSourceGroup;
        use crate::java_source_catalog::JavaSourceRootDeclaration;
        use crate::java_source_catalog::JavaSourceSetDeclaration;

        const SOURCE_SETS: &[JavaSourceSetDeclaration] = &[JavaSourceSetDeclaration {
            id: "main",
            visible_source_sets: &["main"],
        }];
        const SOURCE_ROOTS: &[JavaSourceRootDeclaration] = &[
            JavaSourceRootDeclaration {
                id: "declared-main",
                source_set: "main",
                minecraft_relative_path: "src/main/java",
                kind: CatalogJavaSourceRootKind::Declared,
                build_group: Some(JavaBuildSourceGroup::Main),
                honors_source_excludes: true,
            },
            JavaSourceRootDeclaration {
                id: "generated-example-main",
                source_set: "main",
                minecraft_relative_path: "build/generated/example/main",
                kind: CatalogJavaSourceRootKind::Generated,
                build_group: Some(JavaBuildSourceGroup::Main),
                honors_source_excludes: false,
            },
        ];
        const CATALOG: JavaSourceCatalog = JavaSourceCatalog {
            source_sets: SOURCE_SETS,
            roots: SOURCE_ROOTS,
        };

        let directory = tempfile::tempdir().expect("temporary catalog workspace");
        let worktree = directory.path();
        let minecraft_dir = worktree.join("platform/minecraft");
        let declared = minecraft_dir.join("src/main/java/example");
        let generated = minecraft_dir.join("build/generated/example/main/example");
        std::fs::create_dir_all(&declared).expect("declared root");
        std::fs::create_dir_all(&generated).expect("generated root");
        std::fs::write(declared.join("Z.java"), "package example; class Z {}")
            .expect("declared source");
        std::fs::write(declared.join("Skip.java"), "package example; class Skip {}")
            .expect("excluded source");
        std::fs::write(generated.join("A.java"), "package example; class A {}")
            .expect("generated source");

        let excludes = BTreeMap::from([("main".to_owned(), vec!["example/Skip.java".to_owned()])]);
        let mut roots = Vec::new();
        let mut root_authorities = Vec::new();
        let mut files = Vec::new();
        collect_catalog_roots(
            CATALOG,
            &minecraft_dir,
            worktree,
            &excludes,
            &mut roots,
            &mut root_authorities,
            &mut files,
        )
        .expect("catalog roots should be collected");
        deduplicate_files(&mut files).expect("catalog inventory should be deterministic");

        assert_eq!(
            roots
                .iter()
                .map(|root| root.id.as_str())
                .collect::<Vec<_>>(),
            ["declared-main", "generated-example-main"]
        );
        assert_eq!(
            files
                .iter()
                .map(|file| file.report_path.as_str())
                .collect::<Vec<_>>(),
            [
                "platform/minecraft/build/generated/example/main/example/A.java",
                "platform/minecraft/src/main/java/example/Z.java",
            ]
        );
        assert_eq!(
            declared_source_set_outputs(CATALOG)[0].visible_source_sets,
            ["main"]
        );
        assert_eq!(
            CATALOG
                .build_roots(JavaBuildSourceGroup::Main)
                .map(|root| root.id)
                .collect::<Vec<_>>(),
            ["declared-main", "generated-example-main"]
        );
    }

    #[test]
    fn java_analysis_workspace_inventory_covers_the_real_branch_source_sets() {
        let workspace = JavaSourceWorkspace::resolve(
            BranchSelector::from("1.19.2".to_owned()),
            &[],
            JavaClasspathMode::Branch,
            Path::new(env!("CARGO_MANIFEST_DIR")),
        )
        .expect("1.19.2 analysis workspace");
        assert_eq!(workspace.context.branch, "1.19.2");
        let sets = workspace
            .context
            .source_sets
            .iter()
            .map(|source_set| source_set.id.as_str())
            .collect::<Vec<_>>();
        assert_eq!(sets, ["datagen", "gametest", "generated", "main", "test"]);
        for expected in ["main", "gametest", "datagen", "test"] {
            assert!(
                workspace
                    .files
                    .iter()
                    .any(|file| file.source_set == expected),
                "expected at least one {expected} Java source"
            );
        }
        assert!(!workspace.classpath_entries.is_empty());
        assert!(
            workspace
                .context
                .source_roots
                .iter()
                .any(|root| root.id == "generated-antlr-main")
        );
        assert!(
            workspace
                .files
                .windows(2)
                .all(|pair| pair[0].report_path <= pair[1].report_path)
        );
    }
}
