use super::DefinitionSourceSpanOutput;
use super::DiagnosticSeverity;
use super::JAVA_PARSER_FINGERPRINT;
use super::JavaAnalysisContextOutput;
use super::JavaAnalysisDiagnosticOutput;
use super::JavaDependencyResolutionDefinition;
use super::JavaFileFactDetail;
use super::JavaFileFacts;
use super::JavaFileFactsInput;
use super::JavaSourceFile;
use super::JavaSourceRootKind;
use super::JavaSourceRootOutput;
use super::JavaSourceSetOutput;
use super::blake3_content_hash;
use super::extract_java_file_facts_from_text_with_detail;
use super::sha256_content_hash;
use super::syntax::JavaSyntaxFile;
use super::syntax::is_nonsemantic_literal_or_comment;
use super::syntax::named_children;
use crate::artifact_lock::ArtifactLock;
use crate::cancellation::CancellationToken;
use crate::jar_build::hash::ContentHash;
use crate::jar_build::hash::ContentHashAlgorithm;
use crate::jdk::JdkSource;
use crate::jdk::ResolvedJava;
use crate::jdk::host_jbrsdk_platform;
use crate::jdk::resolve_exact_java;
use crate::jdk::resolve_exact_java_for_minecraft_dir;
use crate::jdk::select_jdk_source;
use crate::jdk_artifact_cache::acquire_pinned_jbrsdk_zip;
use crate::paths::CACHE_DIR;
use crate::source_archive::extract_zip_atomically;
use crate::source_cache::SourceCacheLayout;
use crate::toolchain_lockfile_schema::ToolchainLockfileDocument;
use crate::toolchain_lockfile_schema::parse_document;
use eyre::Context as _;
use facet::Facet;
use rayon::prelude::*;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::path::Path;
use std::path::PathBuf;
use std::sync::Arc;
use tree_sitter_patched_arborium::Node;
use walkdir::WalkDir;

const JDK_SOURCE_INDEX_FORMAT: &str = "sfm.jdk-source-index/1";
const JDK_SOURCE_WORKER_CONTEXT_SCHEMA: &str = "sfm.jdk-source-worker-context/1";

/// Branch-selected JDK source state. Synthetic workspaces deliberately use
/// `Disabled`; a real branch always records either a content-addressed source
/// domain or an explicit diagnostic explaining why that domain is unavailable.
#[derive(Clone, Debug, Eq, PartialEq)]
pub(crate) enum JdkSourceDomainState {
    Disabled,
    Unavailable {
        java_release: String,
        diagnostic: JavaAnalysisDiagnosticOutput,
    },
    Ready(Arc<JdkSourceDomain>),
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub(crate) struct JdkSourceDomain {
    identity: String,
    java_release: String,
    root_id: String,
    source_set: String,
    report_prefix: String,
    portable_tree: PathBuf,
    canonical_tree: PathBuf,
    /// Top-level qualified source type to one or more module-relative files.
    inventory: BTreeMap<String, Vec<String>>,
}

#[derive(Facet)]
struct JdkSourceWorkerContext {
    schema: String,
    identity: String,
    java_release: String,
    root_id: String,
    source_set: String,
    report_prefix: String,
    portable_tree: String,
    canonical_tree: String,
    inventory: BTreeMap<String, Vec<String>>,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub(crate) struct JdkSourceReportIdentity {
    pub(crate) root_id: String,
    pub(crate) root_relative_path: String,
}

impl JdkSourceDomainState {
    pub(crate) fn resolve(
        java_release: &str,
        branch: &str,
        minecraft_version: &str,
        minecraft_dir: &Path,
    ) -> Self {
        match JdkSourceDomain::resolve(java_release, branch, minecraft_version, minecraft_dir) {
            Ok(domain) => Self::Ready(Arc::new(domain)),
            Err(error) => Self::Unavailable {
                java_release: java_release.to_owned(),
                diagnostic: JavaAnalysisDiagnosticOutput {
                    code: "java.jdk-source-unavailable".to_owned(),
                    severity: DiagnosticSeverity::Warning,
                    message: format!(
                        "JDK source domain for Java {java_release} is unavailable: {error:#}"
                    ),
                    span: None,
                },
            },
        }
    }

    /// Follow the generated project's lockfile JDK selection. Pinned SDKs
    /// must already be cached; older lockfiles use exact-major installed-JDK
    /// selection without claiming support for source run on those schemas.
    pub(crate) fn resolve_generated_project(
        java_release: &str,
        project_root: &Path,
        jdk_cache_root: &Path,
        source_cache_root: &Path,
        java_home: Option<&Path>,
    ) -> eyre::Result<Self> {
        match JdkSourceDomain::resolve_generated_project(
            java_release,
            project_root,
            jdk_cache_root,
            source_cache_root,
            java_home,
        ) {
            Ok(domain) => Ok(Self::Ready(Arc::new(domain))),
            Err(error) if java_home.is_some() => {
                Err(error).wrap_err("explicit generated-project --java-home could not be used")
            }
            Err(error) => Ok(Self::Unavailable {
                java_release: java_release.to_owned(),
                diagnostic: JavaAnalysisDiagnosticOutput {
                    code: "java.jdk-source-unavailable".to_owned(),
                    severity: DiagnosticSeverity::Warning,
                    message: format!(
                        "Generated project JDK source domain for Java {java_release} is unavailable: {error:#}"
                    ),
                    span: None,
                },
            }),
        }
    }

    pub(crate) fn apply_to_context(&self, context: &mut JavaAnalysisContextOutput) {
        match self {
            Self::Disabled => {}
            Self::Unavailable { java_release, .. } => {
                let source_set = format!("jdk:java-{java_release}");
                add_jdk_source_set(context, &source_set);
                context.source_roots.push(JavaSourceRootOutput {
                    id: format!("jdk-java-{java_release}-missing"),
                    source_set,
                    path: format!("jdk://java-{java_release}/missing"),
                    kind: JavaSourceRootKind::Jdk,
                    exists: false,
                });
                sort_context_roots(context);
            }
            Self::Ready(domain) => domain.apply_to_context(context),
        }
    }

    pub(crate) fn diagnostics(&self) -> Vec<JavaAnalysisDiagnosticOutput> {
        match self {
            Self::Unavailable { diagnostic, .. } => vec![diagnostic.clone()],
            Self::Disabled | Self::Ready(_) => Vec::new(),
        }
    }

    pub(crate) fn facts_for_project(
        &self,
        project_facts: &[JavaFileFacts],
        first_sequence: u64,
        visible_source_sets: &[String],
        cancellation_token: &CancellationToken,
    ) -> eyre::Result<Vec<JavaFileFacts>> {
        match self {
            Self::Ready(domain) => domain.facts_for_project(
                project_facts,
                first_sequence,
                visible_source_sets,
                cancellation_token,
            ),
            Self::Disabled | Self::Unavailable { .. } => Ok(Vec::new()),
        }
    }

    pub(crate) fn enrich_span(&self, span: &mut DefinitionSourceSpanOutput) {
        if let Self::Ready(domain) = self {
            domain.enrich_span(span);
        }
    }

    pub(crate) fn report_identity(
        &self,
        report_path: &str,
        source_set: &str,
    ) -> Option<JdkSourceReportIdentity> {
        let Self::Ready(domain) = self else {
            return None;
        };
        domain.report_identity(report_path, source_set)
    }

    pub(crate) fn report_prefix_for_root(&self, root_id: &str) -> Option<&str> {
        let Self::Ready(domain) = self else {
            return None;
        };
        (domain.root_id == root_id).then_some(domain.report_prefix.as_str())
    }

    /// Resolve one caller-addressed JDK document without admitting the JDK
    /// tree to the eager editable-workspace inventory.
    pub(crate) fn addressed_source_file(
        &self,
        root_id: &str,
        root_relative_path: &str,
    ) -> eyre::Result<Option<JavaSourceFile>> {
        let Self::Ready(domain) = self else {
            return Ok(None);
        };
        domain.addressed_source_file(root_id, root_relative_path)
    }

    /// Resolve only the JDK source file declaring a requested type (or its
    /// enclosing type). A symbol lookup must not admit the entire JDK source
    /// tree, or even every JDK type referenced by the workspace.
    pub(crate) fn source_files_for_type(
        &self,
        qualified_name: &str,
    ) -> eyre::Result<Vec<JavaSourceFile>> {
        match self {
            Self::Ready(domain) => domain.source_files_for_type(qualified_name),
            Self::Disabled | Self::Unavailable { .. } => Ok(Vec::new()),
        }
    }

    pub(crate) fn syntax_files_for_project(
        &self,
        project_files: &[JavaSyntaxFile],
        cancellation_token: &CancellationToken,
    ) -> eyre::Result<Vec<JavaSyntaxFile>> {
        match self {
            Self::Ready(domain) => {
                domain.syntax_files_for_project(project_files, cancellation_token)
            }
            Self::Disabled | Self::Unavailable { .. } => Ok(Vec::new()),
        }
    }

    /// Persist the already-resolved, lock-backed JDK source inventory for
    /// short-lived Java analysis workers. This private snapshot prevents every
    /// worker from rescanning the JDK tree while preserving the exact source
    /// domain selected by the branch toolchain plan.
    pub(crate) fn write_worker_context(&self, path: &Path) -> eyre::Result<bool> {
        let Self::Ready(domain) = self else {
            return Ok(false);
        };
        let portable_tree = domain
            .portable_tree
            .to_str()
            .ok_or_else(|| eyre::eyre!("JDK worker portable source path is not UTF-8"))?;
        let canonical_tree = domain
            .canonical_tree
            .to_str()
            .ok_or_else(|| eyre::eyre!("JDK worker canonical source path is not UTF-8"))?;
        let context = JdkSourceWorkerContext {
            schema: JDK_SOURCE_WORKER_CONTEXT_SCHEMA.to_owned(),
            identity: domain.identity.clone(),
            java_release: domain.java_release.clone(),
            root_id: domain.root_id.clone(),
            source_set: domain.source_set.clone(),
            report_prefix: domain.report_prefix.clone(),
            portable_tree: portable_tree.to_owned(),
            canonical_tree: canonical_tree.to_owned(),
            inventory: domain.inventory.clone(),
        };
        let mut writer = std::io::BufWriter::new(std::fs::File::create(path)?);
        facet_json::to_writer_std(&mut writer, &context)?;
        std::io::Write::flush(&mut writer)?;
        Ok(true)
    }

    /// Rehydrate a private worker snapshot written by `write_worker_context`.
    /// The snapshot is transport only: its canonical tree must still exist,
    /// and every inventory entry remains a canonical relative path.
    pub(crate) fn read_worker_context(path: &Path) -> eyre::Result<Self> {
        let encoded = std::fs::read_to_string(path)?;
        let context: JdkSourceWorkerContext = facet_json::from_str(&encoded)?;
        if context.schema != JDK_SOURCE_WORKER_CONTEXT_SCHEMA {
            eyre::bail!("unsupported JDK source worker context schema");
        }
        for (qualified_name, entries) in &context.inventory {
            if qualified_name.trim().is_empty() || entries.is_empty() {
                eyre::bail!("JDK source worker inventory contains an empty type entry");
            }
            for entry in entries {
                validate_addressed_relative_path(entry)?;
            }
        }
        if context.inventory.is_empty() {
            eyre::bail!("JDK source worker inventory is empty");
        }
        let canonical_tree = dunce::canonicalize(&context.canonical_tree).wrap_err_with(|| {
            format!(
                "failed to canonicalize JDK worker source tree {}",
                context.canonical_tree
            )
        })?;
        Ok(Self::Ready(Arc::new(JdkSourceDomain {
            identity: context.identity,
            java_release: context.java_release,
            root_id: context.root_id,
            source_set: context.source_set,
            report_prefix: context.report_prefix,
            portable_tree: PathBuf::from(context.portable_tree),
            canonical_tree,
            inventory: context.inventory,
        })))
    }

    #[cfg(test)]
    pub(crate) fn ready_from_tree(java_release: &str, canonical_tree: &Path) -> eyre::Result<Self> {
        Ok(Self::Ready(Arc::new(JdkSourceDomain::from_tree(
            java_release,
            "fixture-jdk-source-identity".to_owned(),
            canonical_tree.to_path_buf(),
            canonical_tree,
        )?)))
    }
}

impl JdkSourceDomain {
    fn resolve(
        java_release: &str,
        branch: &str,
        minecraft_version: &str,
        minecraft_dir: &Path,
    ) -> eyre::Result<Self> {
        if let Some(fixture) = super::current_scenario_fixture() {
            return Self::from_scenario_tree(java_release, &fixture.jdk_source_tree);
        }
        let provider = BranchJdkSourceProvider::resolve(
            java_release,
            branch,
            minecraft_version,
            minecraft_dir,
        )?;
        Self::from_archive(
            java_release,
            &provider.identity,
            &provider.source_archive,
            provider.source_hash,
            &CACHE_DIR.0,
        )
    }

    fn resolve_generated_project(
        java_release: &str,
        project_root: &Path,
        jdk_cache_root: &Path,
        source_cache_root: &Path,
        java_home: Option<&Path>,
    ) -> eyre::Result<Self> {
        if let Some(fixture) = super::current_scenario_fixture() {
            return Self::from_scenario_tree(java_release, &fixture.jdk_source_tree);
        }
        Self::resolve_generated_project_with_java_selection(
            java_release,
            project_root,
            jdk_cache_root,
            source_cache_root,
            java_home,
            |root, major, explicit_home| match explicit_home {
                Some(home) => resolve_exact_java_for_minecraft_dir(root, major, Some(home)),
                None => resolve_exact_java(major),
            },
        )
    }

    fn resolve_generated_project_with_java_selection(
        java_release: &str,
        project_root: &Path,
        jdk_cache_root: &Path,
        source_cache_root: &Path,
        java_home: Option<&Path>,
        select_java: impl FnOnce(&Path, u32, Option<&Path>) -> eyre::Result<ResolvedJava>,
    ) -> eyre::Result<Self> {
        let expected_release = java_release
            .parse::<u32>()
            .wrap_err_with(|| format!("invalid Java release `{java_release}`"))?;
        let lockfile_path = project_root.join("sfm-toolchain.lock.json");
        let lockfile_text = std::fs::read_to_string(&lockfile_path).wrap_err_with(|| {
            format!(
                "failed to read generated project lockfile {}",
                lockfile_path.display()
            )
        })?;
        let pins = match parse_document(&lockfile_text)? {
            ToolchainLockfileDocument::V4(lockfile) => lockfile.jdk_pins,
            ToolchainLockfileDocument::V1(_)
            | ToolchainLockfileDocument::V2 { .. }
            | ToolchainLockfileDocument::V3(_) => None,
        };
        let selected_java = if java_home.is_some() || pins.is_none() {
            Some(select_java(project_root, expected_release, java_home)?)
        } else {
            None
        };
        let (home, selection_identity) = if let Some(java) = selected_java {
            eyre::ensure!(
                java.major_version == expected_release,
                "generated project requires Java {expected_release}, but selected Java {}",
                java.major_version
            );
            let home = java.home.ok_or_else(|| {
                eyre::eyre!(
                    "exact Java {expected_release} selected for generated project has no JDK home"
                )
            })?;
            (
                home,
                format!(
                    "installed-selection={}\nversion={}",
                    java.selection, java.version_output
                ),
            )
        } else if let Some(pins) = pins {
            let platform = host_jbrsdk_platform().unwrap_or("unsupported-host");
            let JdkSource::Pinned { pin, artifact } =
                select_jdk_source(None, Some(&pins), expected_release, platform)?
            else {
                eyre::bail!("generated project did not select an exact JDK pin");
            };
            let home = acquire_pinned_jbrsdk_zip(pin, artifact, jdk_cache_root, true)?;
            (
                home,
                format!(
                    "pin-version={}\npin-build={}\nartifact={}",
                    pin.version, pin.build, artifact.sha512
                ),
            )
        } else {
            eyre::bail!("generated project did not select a Java provider");
        };
        let home = canonical_existing(&home)?;
        let source_archive = [home.join("lib").join("src.zip"), home.join("src.zip")]
            .into_iter()
            .find(|candidate| candidate.is_file())
            .ok_or_else(|| {
                eyre::eyre!(
                    "project-selected Java {expected_release} SDK at {} has no src.zip",
                    home.display()
                )
            })?;
        let source_archive = canonical_existing(&source_archive)?;
        let source_hash = ContentHash::from_path(&source_archive, ContentHashAlgorithm::Blake3)?;
        let lockfile_hash = ContentHash::from_path(&lockfile_path, ContentHashAlgorithm::Blake3)?;
        let provider_material = format!(
            "project-lockfile={lockfile_hash}\njava-release={expected_release}\n{selection_identity}\nsrc.zip={source_hash}"
        );
        let provider_identity =
            blake3::hash(provider_material.as_bytes()).to_hex()[..16].to_owned();
        Self::from_archive(
            java_release,
            &provider_identity,
            &source_archive,
            source_hash,
            source_cache_root,
        )
    }

    fn from_archive(
        java_release: &str,
        provider_identity: &str,
        archive: &Path,
        source_hash: ContentHash,
        source_cache_root: &Path,
    ) -> eyre::Result<Self> {
        let portable = SourceCacheLayout::jdk(java_release, source_hash, JAVA_PARSER_FINGERPRINT);
        let canonical_tree = local_cache_path(&portable.tree, source_cache_root);
        if !canonical_tree.is_dir() {
            let parent = canonical_tree.parent().ok_or_else(|| {
                eyre::eyre!(
                    "JDK source cache tree has no parent: {}",
                    canonical_tree.display()
                )
            })?;
            std::fs::create_dir_all(parent).wrap_err_with(|| {
                format!(
                    "failed to create JDK source cache parent {}",
                    parent.display()
                )
            })?;
            let lock_path = parent.join("extract.lock");
            let _lock = ArtifactLock::acquire(
                &lock_path,
                format!("JDK Java {java_release} source extraction"),
            )?;
            if !canonical_tree.is_dir() {
                extract_zip_atomically(archive, &canonical_tree)?;
            }
        }
        let identity = format!(
            "{JDK_SOURCE_INDEX_FORMAT}:java-{java_release}:provider-{provider_identity}:source-{source_hash}:parser-{JAVA_PARSER_FINGERPRINT}"
        );
        Self::from_tree(java_release, identity, portable.tree, &canonical_tree)
    }

    fn from_scenario_tree(java_release: &str, tree: &Path) -> eyre::Result<Self> {
        let mut paths = WalkDir::new(tree)
            .into_iter()
            .collect::<Result<Vec<_>, _>>()?
            .into_iter()
            .filter(|entry| {
                entry.file_type().is_file()
                    && entry
                        .path()
                        .extension()
                        .is_some_and(|extension| extension == "java")
            })
            .map(walkdir::DirEntry::into_path)
            .collect::<Vec<_>>();
        paths.sort();
        let mut hasher = blake3::Hasher::new();
        for path in paths {
            let relative = path.strip_prefix(tree)?;
            hasher.update(relative.to_string_lossy().replace('\\', "/").as_bytes());
            hasher.update(&[0]);
            hasher.update(&std::fs::read(&path)?);
            hasher.update(&[0]);
        }
        let identity = format!(
            "{JDK_SOURCE_INDEX_FORMAT}:java-{java_release}:scenario-source-{}:parser-{JAVA_PARSER_FINGERPRINT}",
            hasher.finalize().to_hex()
        );
        Self::from_tree(
            java_release,
            identity,
            PathBuf::from(format!("$scenario-jdk/java-{java_release}")),
            tree,
        )
    }

    fn from_tree(
        java_release: &str,
        identity: String,
        portable_tree: PathBuf,
        canonical_tree: &Path,
    ) -> eyre::Result<Self> {
        let canonical_tree = canonical_tree.canonicalize().wrap_err_with(|| {
            format!(
                "failed to canonicalize JDK source tree {}",
                canonical_tree.display()
            )
        })?;
        let inventory = inventory(&canonical_tree)?;
        if inventory.is_empty() {
            eyre::bail!(
                "JDK source tree {} contains no addressable Java types",
                canonical_tree.display()
            );
        }
        let identity_hash = blake3::hash(identity.as_bytes());
        let short_identity = &identity_hash.to_hex()[..16];
        Ok(Self {
            identity,
            java_release: java_release.to_owned(),
            root_id: format!("jdk-java-{java_release}-{short_identity}"),
            source_set: format!("jdk:java-{java_release}"),
            report_prefix: format!("jdk/java-{java_release}/{short_identity}"),
            portable_tree,
            canonical_tree,
            inventory,
        })
    }

    fn apply_to_context(&self, context: &mut JavaAnalysisContextOutput) {
        add_jdk_source_set(context, &self.source_set);
        context.source_roots.push(JavaSourceRootOutput {
            id: self.root_id.clone(),
            source_set: self.source_set.clone(),
            path: slash_path(&self.portable_tree),
            kind: JavaSourceRootKind::Jdk,
            exists: true,
        });
        let mut hasher = blake3::Hasher::new();
        hasher.update(context.index_fingerprint.as_bytes());
        hasher.update(&[0]);
        hasher.update(self.identity.as_bytes());
        context.index_fingerprint = format!("blake3:{}", hasher.finalize().to_hex());
        sort_context_roots(context);
    }

    fn facts_for_project(
        &self,
        project_facts: &[JavaFileFacts],
        first_sequence: u64,
        visible_source_sets: &[String],
        cancellation_token: &CancellationToken,
    ) -> eyre::Result<Vec<JavaFileFacts>> {
        cancellation_token.bail_if_cancelled()?;
        let mut candidate_names = BTreeSet::new();
        for facts in project_facts {
            for import in &facts.direct_type_imports {
                candidate_names.insert(import.qualified_name.clone());
            }
            for import in &facts.static_imports {
                candidate_names.insert(import.owner.clone());
            }
            candidate_names.extend(facts.static_wildcard_owners.iter().cloned());
            for raw_name in &facts.referenced_type_names {
                let name = raw_name.trim();
                if name.is_empty() {
                    continue;
                }
                if name.chars().next().is_some_and(char::is_lowercase) && name.contains('.') {
                    candidate_names.insert(name.to_owned());
                }
                if !name.contains('.') || name.chars().next().is_some_and(char::is_uppercase) {
                    candidate_names.insert(format!("java.lang.{name}"));
                    for package in &facts.wildcard_packages {
                        candidate_names.insert(format!("{package}.{name}"));
                    }
                }
            }
        }

        let mut relative_paths = BTreeSet::new();
        for candidate in candidate_names {
            relative_paths.extend(self.source_entries(&candidate));
        }
        let existing_paths = project_facts
            .iter()
            .filter(|facts| facts.file.source_set == self.source_set)
            .filter_map(|facts| {
                facts
                    .file
                    .report_path
                    .strip_prefix(self.report_prefix.trim_end_matches('/'))
                    .and_then(|tail| tail.strip_prefix('/'))
                    .map(ToOwned::to_owned)
            })
            .collect::<BTreeSet<_>>();
        relative_paths.retain(|relative| !existing_paths.contains(relative));
        let relative_paths = relative_paths.into_iter().collect::<Vec<_>>();
        let mut visible = visible_source_sets.to_vec();
        visible.push(self.source_set.clone());
        visible.sort();
        visible.dedup();

        relative_paths
            .par_iter()
            .enumerate()
            .map(|(offset, relative)| {
                cancellation_token.bail_if_cancelled()?;
                let absolute = join_slash_path(&self.canonical_tree, relative);
                let source = std::fs::read_to_string(&absolute).wrap_err_with(|| {
                    format!("failed to read JDK source {}", absolute.display())
                })?;
                let sequence = first_sequence
                    .checked_add(u64::try_from(offset).unwrap_or(u64::MAX))
                    .ok_or_else(|| eyre::eyre!("JDK source fact sequence overflow"))?;
                let report_path = format!("{}/{}", self.report_prefix, relative);
                extract_java_file_facts_from_text_with_detail(
                    JavaFileFactsInput {
                        sequence,
                        report_path: &report_path,
                        source_set: &self.source_set,
                        visible_source_sets: &visible,
                    },
                    source,
                    None,
                    // The interactive project resolver needs source-backed
                    // JDK type identity (including implicit java.lang), but
                    // eagerly linking every JDK field and callable inflated
                    // the real 1.19.2 surface to 150k declarations and tens of
                    // gigabytes. Member lookup can be added as a target-lazy
                    // domain; it must not be a tax on every F12 request.
                    JavaFileFactDetail::TypesOnly,
                )
            })
            .collect()
    }

    fn syntax_files_for_project(
        &self,
        project_files: &[JavaSyntaxFile],
        cancellation_token: &CancellationToken,
    ) -> eyre::Result<Vec<JavaSyntaxFile>> {
        cancellation_token.bail_if_cancelled()?;
        let mut candidate_names = BTreeSet::new();
        for file in project_files {
            for imports in file.imports.direct_types.values() {
                candidate_names.extend(imports.iter().map(|import| import.qualified_name.clone()));
            }
            candidate_names.extend(
                file.imports
                    .static_members
                    .iter()
                    .map(|import| import.owner.clone()),
            );
            candidate_names.extend(file.imports.static_wildcard_owners.iter().cloned());
            let mut referenced = BTreeSet::new();
            collect_syntax_type_names(file, file.tree.root_node(), &mut referenced);
            for name in referenced {
                if name.chars().next().is_some_and(char::is_lowercase) && name.contains('.') {
                    candidate_names.insert(name.clone());
                }
                if !name.contains('.') || name.chars().next().is_some_and(char::is_uppercase) {
                    candidate_names.insert(format!("java.lang.{name}"));
                    for package in &file.imports.wildcard_packages {
                        candidate_names.insert(format!("{package}.{name}"));
                    }
                }
            }
        }
        let mut relative_paths = BTreeSet::new();
        for candidate in candidate_names {
            relative_paths.extend(self.source_entries(&candidate));
        }
        relative_paths
            .into_iter()
            .collect::<Vec<_>>()
            .par_iter()
            .map(|relative| {
                cancellation_token.bail_if_cancelled()?;
                let absolute = join_slash_path(&self.canonical_tree, relative);
                let source = std::fs::read_to_string(&absolute).wrap_err_with(|| {
                    format!("failed to read JDK source {}", absolute.display())
                })?;
                JavaSyntaxFile::parse_text_with_diagnostic_limit(
                    &format!("{}/{}", self.report_prefix, relative),
                    &self.source_set,
                    source,
                    None,
                )
            })
            .collect()
    }

    fn source_entries(&self, qualified_name: &str) -> Vec<String> {
        let mut candidate = qualified_name.replace('$', ".");
        loop {
            if let Some(entries) = self.inventory.get(&candidate) {
                return entries.clone();
            }
            let Some((parent, _)) = candidate.rsplit_once('.') else {
                return Vec::new();
            };
            candidate = parent.to_owned();
        }
    }

    fn source_files_for_type(&self, qualified_name: &str) -> eyre::Result<Vec<JavaSourceFile>> {
        self.source_entries(qualified_name)
            .into_iter()
            .map(|relative| {
                self.addressed_source_file(&self.root_id, &relative)?
                    .ok_or_else(|| eyre::eyre!("JDK source inventory did not resolve `{relative}`"))
            })
            .collect()
    }

    fn enrich_span(&self, span: &mut DefinitionSourceSpanOutput) {
        if span.resolver_id != "dependency-index" || span.source_set != self.source_set {
            return;
        }
        let Some(relative) = span
            .report_path
            .strip_prefix(self.report_prefix.trim_end_matches('/'))
            .and_then(|tail| tail.strip_prefix('/'))
        else {
            return;
        };
        let absolute = join_slash_path(&self.canonical_tree, relative);
        let Ok(source) = std::fs::read_to_string(&absolute) else {
            return;
        };
        if blake3_content_hash(&source) != span.source_hash {
            return;
        }
        "jdk-source".clone_into(&mut span.resolver_id);
        span.root_id.clone_from(&self.root_id);
        relative.clone_into(&mut span.root_relative_path);
        span.address = super::contributed_address("jdk-source", &self.root_id, relative);
        span.source_sha256 = Some(sha256_content_hash(&source));
    }

    fn report_identity(
        &self,
        report_path: &str,
        source_set: &str,
    ) -> Option<JdkSourceReportIdentity> {
        if source_set != self.source_set {
            return None;
        }
        let relative = report_path
            .strip_prefix(self.report_prefix.trim_end_matches('/'))?
            .strip_prefix('/')?;
        if relative.is_empty() || relative.split('/').any(str::is_empty) {
            return None;
        }
        Some(JdkSourceReportIdentity {
            root_id: self.root_id.clone(),
            root_relative_path: relative.to_owned(),
        })
    }

    fn addressed_source_file(
        &self,
        root_id: &str,
        root_relative_path: &str,
    ) -> eyre::Result<Option<JavaSourceFile>> {
        if root_id != self.root_id {
            return Ok(None);
        }
        validate_addressed_relative_path(root_relative_path)?;
        let canonical_root = dunce::canonicalize(&self.canonical_tree).wrap_err_with(|| {
            format!("failed to normalize addressed JDK source root `{root_id}`")
        })?;
        let candidate = join_slash_path(&canonical_root, root_relative_path);
        let absolute_path = dunce::canonicalize(&candidate).wrap_err_with(|| {
            format!("failed to resolve addressed JDK source `{root_id}:{root_relative_path}`")
        })?;
        if !absolute_path.starts_with(&canonical_root) {
            eyre::bail!(
                "addressed JDK source `{root_id}:{root_relative_path}` escapes its canonical root"
            );
        }
        if !absolute_path.is_file()
            || absolute_path
                .extension()
                .and_then(|extension| extension.to_str())
                != Some("java")
        {
            eyre::bail!("addressed JDK source `{root_id}:{root_relative_path}` is not a Java file");
        }
        Ok(Some(JavaSourceFile {
            absolute_path,
            root_id: self.root_id.clone(),
            root_relative_path: root_relative_path.to_owned(),
            report_path: format!(
                "{}/{}",
                self.report_prefix.trim_end_matches('/'),
                root_relative_path
            ),
            source_set: self.source_set.clone(),
            source_override: None,
        }))
    }
}

fn validate_addressed_relative_path(path: &str) -> eyre::Result<()> {
    if path.is_empty()
        || path.contains('\\')
        || path
            .split('/')
            .any(|segment| segment.is_empty() || matches!(segment, "." | ".."))
    {
        eyre::bail!("addressed managed-source path is not canonical");
    }
    Ok(())
}

/// The exact Java provider persisted by the branch's most recent SFM
/// toolchain plan. Source analysis never searches installed JDKs, `PATH`,
/// `JAVA_HOME`, or user-level JDK directories: the plan is the authority.
#[derive(Clone, Debug, Eq, PartialEq)]
struct BranchJdkSourceProvider {
    identity: String,
    source_archive: PathBuf,
    source_hash: ContentHash,
}

#[derive(Facet, Debug)]
struct BranchToolchainPlan {
    schema_version: u64,
    branch_name: String,
    minecraft_version: String,
    lockfile_path: String,
    java: BranchToolchainJavaPlan,
    java_release: u32,
}

#[derive(Facet, Debug)]
struct BranchToolchainJavaPlan {
    home: Option<String>,
    version_output: String,
    major_version: u32,
}

impl BranchJdkSourceProvider {
    fn resolve(
        java_release: &str,
        branch: &str,
        minecraft_version: &str,
        minecraft_dir: &Path,
    ) -> eyre::Result<Self> {
        let expected_release = java_release
            .parse::<u32>()
            .wrap_err_with(|| format!("invalid Java release `{java_release}`"))?;
        let plan_path = minecraft_dir
            .join("build")
            .join("sfm-toolchain")
            .join("state")
            .join("last-plan.json");
        let plan_text = std::fs::read_to_string(&plan_path).wrap_err_with(|| {
            format!(
                "branch-selected Java provider is unavailable because {} could not be read; run an SFM toolchain plan/build for branch `{branch}`",
                plan_path.display()
            )
        })?;
        let plan: BranchToolchainPlan = facet_json::from_str(&plan_text).wrap_err_with(|| {
            format!(
                "failed to decode branch toolchain plan {}",
                plan_path.display()
            )
        })?;
        if plan.schema_version != 1 {
            eyre::bail!(
                "branch toolchain plan {} has unsupported schema version {}",
                plan_path.display(),
                plan.schema_version
            );
        }
        if plan.branch_name != branch || plan.minecraft_version != minecraft_version {
            eyre::bail!(
                "branch toolchain plan {} selects branch `{}` / Minecraft `{}` instead of `{branch}` / `{minecraft_version}`",
                plan_path.display(),
                plan.branch_name,
                plan.minecraft_version
            );
        }
        if plan.java_release != expected_release || plan.java.major_version != expected_release {
            eyre::bail!(
                "branch toolchain plan {} selects Java release {} with provider major {}, but analysis requires exact Java {expected_release}",
                plan_path.display(),
                plan.java_release,
                plan.java.major_version
            );
        }

        let expected_lockfile = canonical_existing(&minecraft_dir.join("sfm-toolchain.lock.json"))?;
        let selected_lockfile = canonical_existing(Path::new(&plan.lockfile_path))?;
        if selected_lockfile != expected_lockfile {
            eyre::bail!(
                "branch toolchain plan {} is bound to {}, not the selected branch lockfile {}",
                plan_path.display(),
                selected_lockfile.display(),
                expected_lockfile.display()
            );
        }
        let lockfile_hash =
            ContentHash::from_path(&expected_lockfile, ContentHashAlgorithm::Blake3)?;
        let home = plan.java.home.as_deref().map(PathBuf::from).ok_or_else(|| {
            eyre::eyre!(
                "branch toolchain plan {} has no Java home and therefore cannot identify src.zip",
                plan_path.display()
            )
        })?;
        let home = canonical_existing(&home)?;
        let source_archive = [home.join("lib").join("src.zip"), home.join("src.zip")]
            .into_iter()
            .find(|candidate| candidate.is_file())
            .ok_or_else(|| {
                eyre::eyre!(
                    "branch-selected Java {expected_release} provider at {} has no src.zip",
                    home.display()
                )
            })?;
        let source_archive = canonical_existing(&source_archive)?;
        let source_hash = ContentHash::from_path(&source_archive, ContentHashAlgorithm::Blake3)?;
        let provider_material = format!(
            "schema={}\nbranch={branch}\nminecraft={minecraft_version}\njava-release={expected_release}\nversion={}\nlockfile={}\nsrc.zip={source_hash}",
            plan.schema_version, plan.java.version_output, lockfile_hash,
        );
        let provider_hash = blake3::hash(provider_material.as_bytes());
        Ok(Self {
            identity: provider_hash.to_hex()[..16].to_owned(),
            source_archive,
            source_hash,
        })
    }
}

fn canonical_existing(path: &Path) -> eyre::Result<PathBuf> {
    dunce::canonicalize(path).wrap_err_with(|| {
        format!(
            "failed to canonicalize pinned toolchain path {}",
            path.display()
        )
    })
}

pub(crate) fn jdk_resolution_definitions(
    facts: &[JavaFileFacts],
) -> Vec<JavaDependencyResolutionDefinition> {
    let mut definitions = facts
        .iter()
        .flat_map(|facts| &facts.types)
        .map(|definition| JavaDependencyResolutionDefinition {
            symbol: definition.symbol.clone(),
            source_set: definition.identifier_span.source_set.clone(),
        })
        .collect::<Vec<_>>();
    definitions.sort();
    definitions.dedup();
    definitions
}

fn add_jdk_source_set(context: &mut JavaAnalysisContextOutput, jdk_source_set: &str) {
    let mut all_sets = context
        .source_sets
        .iter()
        .map(|source_set| source_set.id.clone())
        .collect::<BTreeSet<_>>();
    all_sets.insert(jdk_source_set.to_owned());
    for source_set in &mut context.source_sets {
        source_set
            .visible_source_sets
            .push(jdk_source_set.to_owned());
        source_set.visible_source_sets.sort();
        source_set.visible_source_sets.dedup();
    }
    if !context
        .source_sets
        .iter()
        .any(|source_set| source_set.id == jdk_source_set)
    {
        context.source_sets.push(JavaSourceSetOutput {
            id: jdk_source_set.to_owned(),
            visible_source_sets: all_sets.into_iter().collect(),
        });
    }
    context
        .source_sets
        .sort_by(|left, right| left.id.cmp(&right.id));
}

fn sort_context_roots(context: &mut JavaAnalysisContextOutput) {
    context.source_roots.sort_by(|left, right| {
        (&left.id, &left.source_set, &left.path).cmp(&(&right.id, &right.source_set, &right.path))
    });
    context
        .source_roots
        .dedup_by(|left, right| left.id == right.id);
}

fn inventory(root: &Path) -> eyre::Result<BTreeMap<String, Vec<String>>> {
    let mut inventory = BTreeMap::<String, Vec<String>>::new();
    for entry in WalkDir::new(root).follow_links(false) {
        let entry = entry
            .wrap_err_with(|| format!("failed to inventory JDK source tree {}", root.display()))?;
        if !entry.file_type().is_file()
            || entry.path().extension().and_then(|value| value.to_str()) != Some("java")
        {
            continue;
        }
        let relative = entry
            .path()
            .strip_prefix(root)
            .wrap_err("JDK source inventory entry escaped its root")?;
        let components = relative
            .components()
            .map(|component| component.as_os_str().to_str().map(ToOwned::to_owned))
            .collect::<Option<Vec<_>>>()
            .ok_or_else(|| {
                eyre::eyre!(
                    "JDK source path is not UTF-8 compatible: {}",
                    relative.display()
                )
            })?;
        if components.len() < 2 {
            continue;
        }
        let Some(stem) = Path::new(components.last().expect("length checked"))
            .file_stem()
            .and_then(|value| value.to_str())
        else {
            continue;
        };
        if matches!(stem, "module-info" | "package-info") {
            continue;
        }
        let mut qualified = components[1..components.len() - 1].to_vec();
        qualified.push(stem.to_owned());
        inventory
            .entry(qualified.join("."))
            .or_default()
            .push(slash_path(relative));
    }
    for entries in inventory.values_mut() {
        entries.sort();
        entries.dedup();
    }
    Ok(inventory)
}

fn local_cache_path(portable: &Path, cache_root: &Path) -> PathBuf {
    portable.strip_prefix(Path::new("$sfm-cache")).map_or_else(
        |_| portable.to_path_buf(),
        |relative| cache_root.join(relative),
    )
}

fn slash_path(path: &Path) -> String {
    path.to_string_lossy().replace('\\', "/")
}

fn join_slash_path(root: &Path, relative: &str) -> PathBuf {
    let mut result = root.to_path_buf();
    for component in relative.split('/') {
        result.push(component);
    }
    result
}

fn collect_syntax_type_names(file: &JavaSyntaxFile, node: Node<'_>, names: &mut BTreeSet<String>) {
    if is_nonsemantic_literal_or_comment(node.kind()) {
        return;
    }
    match node.kind() {
        "type_identifier" | "scoped_type_identifier" => {
            if let Some(name) = file.text(node) {
                names.insert(name.to_owned());
            }
        }
        "marker_annotation" | "annotation" => {
            let name = node
                .child_by_field_name("name")
                .or_else(|| named_children(node).into_iter().next());
            if let Some(name) = name.and_then(|name| file.text(name)) {
                names.insert(name.trim_start_matches('@').to_owned());
            }
        }
        _ => {}
    }
    for child in named_children(node) {
        collect_syntax_type_names(file, child, names);
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::java_analysis::JavaClasspathMode;
    use std::io::Cursor;
    use std::io::Write as _;
    use zip::ZipWriter;
    use zip::write::SimpleFileOptions;

    #[test]
    fn generated_project_uses_scoped_jdk_without_host_selection() {
        let temporary = tempfile::tempdir().unwrap();
        let tree = temporary.path().join("jdk");
        let source = tree.join("java.base/java/lang/Object.java");
        std::fs::create_dir_all(source.parent().unwrap()).unwrap();
        std::fs::write(source, "package java.lang; public class Object {}").unwrap();
        crate::java_analysis::with_java_analysis_scenario_fixture(
            crate::java_analysis::JavaAnalysisScenarioFixture {
                jdk_source_tree: tree,
                cache_home: crate::paths::CacheHome(temporary.path().join("cache")),
                decompiler_runtime_identity: "fixture".to_owned(),
            },
            || {
                // No project lockfile or installed JDK is needed in this scope.
                let domain = JdkSourceDomain::resolve_generated_project(
                    "17",
                    &temporary.path().join("missing-project"),
                    &temporary.path().join("missing-sdk-cache"),
                    &temporary.path().join("missing-source-cache"),
                    None,
                )
                .unwrap();
                assert!(!domain.source_entries("java.lang.Object").is_empty());
                assert!(!temporary.path().join("cache").exists());
                assert!(!temporary.path().join("missing-sdk-cache").exists());
                assert!(!temporary.path().join("missing-source-cache").exists());
            },
        );
    }

    fn context() -> JavaAnalysisContextOutput {
        JavaAnalysisContextOutput {
            branch: "fixture".to_owned(),
            minecraft_version: "fixture".to_owned(),
            java_release: "17".to_owned(),
            jdk: "java-17".to_owned(),
            source_roots: Vec::new(),
            source_sets: vec![JavaSourceSetOutput {
                id: "main".to_owned(),
                visible_source_sets: vec!["main".to_owned()],
            }],
            source_exclusions: Vec::new(),
            classpath_mode: JavaClasspathMode::Isolated,
            classpath_fingerprint: "fixture".to_owned(),
            parser_fingerprint: JAVA_PARSER_FINGERPRINT.to_owned(),
            index_fingerprint: "fixture".to_owned(),
        }
    }

    #[test]
    fn synthetic_tree_is_inventory_backed_and_context_addressable() {
        let temporary = tempfile::tempdir().expect("temporary JDK tree");
        let source = temporary
            .path()
            .join("java.base")
            .join("java")
            .join("lang")
            .join("String.java");
        std::fs::create_dir_all(source.parent().expect("source parent")).expect("source parent");
        std::fs::write(&source, "package java.lang; public final class String {}\n")
            .expect("JDK source");
        let state =
            JdkSourceDomainState::ready_from_tree("17", temporary.path()).expect("JDK domain");
        let mut context = context();

        state.apply_to_context(&mut context);

        assert!(context.source_roots.iter().any(|root| {
            root.kind == JavaSourceRootKind::Jdk && root.exists && root.source_set == "jdk:java-17"
        }));
        assert!(
            context.source_sets[0]
                .visible_source_sets
                .contains(&"jdk:java-17".to_owned())
        );
    }

    #[test]
    fn generated_legacy_release_lockfile_uses_selected_exact_jdk_home() {
        assert_unpinned_lockfile_uses_selected_home(include_str!(
            "../../../../minecraft/core-liquid-template/build/lockfiles/1.21.0/schema-2.json"
        ));
    }

    #[test]
    fn generated_v3_lockfile_uses_selected_exact_jdk_home() {
        let v3 = crate::toolchain_lockfile_schema::read_current(include_str!(
            "../../../../minecraft/sfm-toolchain.lock.json"
        ))
        .expect("checked-in lockfile's v3 dependency view");
        let encoded = facet_json::to_string(&v3).expect("v3 lockfile JSON");
        assert_unpinned_lockfile_uses_selected_home(&encoded);
    }

    fn assert_unpinned_lockfile_uses_selected_home(lockfile: &str) {
        let temporary = tempfile::tempdir().expect("temporary generated project");
        let project_root = temporary.path().join("generated-project");
        let selected_home = temporary.path().join("selected-jdk");
        std::fs::create_dir_all(&project_root).expect("project root");
        std::fs::create_dir_all(selected_home.join("lib")).expect("selected JDK lib");
        std::fs::write(project_root.join("sfm-toolchain.lock.json"), lockfile)
            .expect("project lockfile");
        write_string_source_zip(&selected_home);

        let domain = JdkSourceDomain::resolve_generated_project_with_java_selection(
            "21",
            &project_root,
            &temporary.path().join("unused-jdk-cache"),
            &temporary.path().join("source-cache"),
            None,
            |selected_root, major, explicit_home| {
                assert_eq!(selected_root, project_root);
                assert_eq!(major, 21);
                assert!(explicit_home.is_none());
                Ok(ResolvedJava {
                    executable: selected_home.join("bin/java"),
                    home: Some(selected_home.clone()),
                    version_output: "openjdk version \"21.0.8\"".to_owned(),
                    major_version: 21,
                    selection: "exact-major-installed".to_owned(),
                    pin_url: None,
                    pin_sha512: None,
                })
            },
        )
        .expect("unpinned project JDK source domain");
        assert!(domain.inventory.contains_key("java.lang.String"));
        assert_eq!(domain.source_set, "jdk:java-21");
    }

    #[test]
    fn generated_legacy_release_without_src_zip_reports_selected_home() {
        let temporary = tempfile::tempdir().expect("temporary generated project");
        let project_root = temporary.path().join("generated-project");
        let selected_home = temporary.path().join("selected-jdk");
        std::fs::create_dir_all(&project_root).expect("project root");
        std::fs::create_dir_all(&selected_home).expect("selected JDK home");
        std::fs::write(
            project_root.join("sfm-toolchain.lock.json"),
            include_str!(
                "../../../../minecraft/core-liquid-template/build/lockfiles/1.21.0/schema-2.json"
            ),
        )
        .expect("real release lockfile");
        let error = JdkSourceDomain::resolve_generated_project_with_java_selection(
            "21",
            &project_root,
            &temporary.path().join("unused-jdk-cache"),
            &temporary.path().join("source-cache"),
            None,
            |_, _, explicit_home| {
                assert!(explicit_home.is_none());
                Ok(ResolvedJava {
                    executable: selected_home.join("bin/java"),
                    home: Some(selected_home.clone()),
                    version_output: "openjdk version \"21.0.8\"".to_owned(),
                    major_version: 21,
                    selection: "exact-major-installed".to_owned(),
                    pin_url: None,
                    pin_sha512: None,
                })
            },
        )
        .expect_err("selected JDK without sources");
        assert!(error.to_string().contains("has no src.zip"));
    }

    #[test]
    fn generated_project_explicit_java_home_overrides_pinned_cache() {
        let temporary = tempfile::tempdir().expect("temporary generated project");
        let project_root = temporary.path().join("generated-project");
        let selected_home = temporary.path().join("selected-jdk");
        std::fs::create_dir_all(&project_root).expect("project root");
        std::fs::create_dir_all(selected_home.join("lib")).expect("selected JDK lib");
        std::fs::write(
            project_root.join("sfm-toolchain.lock.json"),
            include_str!("../../../../minecraft/sfm-toolchain.lock.json"),
        )
        .expect("pinned project lockfile");
        write_string_source_zip(&selected_home);

        let domain = JdkSourceDomain::resolve_generated_project_with_java_selection(
            "17",
            &project_root,
            &temporary.path().join("empty-jdk-cache"),
            &temporary.path().join("source-cache"),
            Some(&selected_home),
            |root, major, explicit_home| {
                assert_eq!(root, project_root);
                assert_eq!(major, 17);
                assert_eq!(explicit_home, Some(selected_home.as_path()));
                Ok(ResolvedJava {
                    executable: selected_home.join("bin/java"),
                    home: Some(selected_home.clone()),
                    version_output: "openjdk version \"17.0.14\"".to_owned(),
                    major_version: 17,
                    selection: "explicit".to_owned(),
                    pin_url: None,
                    pin_sha512: None,
                })
            },
        )
        .expect("explicit JDK source domain");
        assert!(domain.inventory.contains_key("java.lang.String"));
        assert_eq!(domain.source_set, "jdk:java-17");
    }

    #[test]
    fn generated_project_explicit_java_home_requires_exact_major() {
        let temporary = tempfile::tempdir().expect("temporary generated project");
        let project_root = temporary.path().join("generated-project");
        let selected_home = temporary.path().join("wrong-major-jdk");
        std::fs::create_dir_all(&project_root).expect("project root");
        std::fs::write(
            project_root.join("sfm-toolchain.lock.json"),
            include_str!("../../../../minecraft/sfm-toolchain.lock.json"),
        )
        .expect("pinned project lockfile");

        let error = JdkSourceDomain::resolve_generated_project_with_java_selection(
            "17",
            &project_root,
            &temporary.path().join("empty-jdk-cache"),
            &temporary.path().join("source-cache"),
            Some(&selected_home),
            |_, _, explicit_home| {
                assert_eq!(explicit_home, Some(selected_home.as_path()));
                Ok(ResolvedJava {
                    executable: selected_home.join("bin/java"),
                    home: Some(selected_home.clone()),
                    version_output: "openjdk version \"21.0.8\"".to_owned(),
                    major_version: 21,
                    selection: "explicit".to_owned(),
                    pin_url: None,
                    pin_sha512: None,
                })
            },
        )
        .expect_err("wrong major must fail before source lookup");
        assert!(
            error
                .to_string()
                .contains("requires Java 17, but selected Java 21")
        );
    }

    fn write_string_source_zip(selected_home: &Path) {
        let mut source_zip = ZipWriter::new(Cursor::new(Vec::new()));
        source_zip
            .start_file(
                "java.base/java/lang/String.java",
                SimpleFileOptions::default(),
            )
            .expect("JDK source ZIP entry");
        source_zip
            .write_all(b"package java.lang; public final class String {}")
            .expect("JDK source ZIP content");
        std::fs::write(
            selected_home.join("lib/src.zip"),
            source_zip.finish().expect("JDK source ZIP").into_inner(),
        )
        .expect("selected source archive");
    }

    #[test]
    fn branch_plan_pins_jdk_source_provider_and_explicit_archive_identity() {
        let temporary = tempfile::tempdir().expect("temporary branch workspace");
        let minecraft_dir = temporary.path().join("platform/minecraft");
        let state_dir = minecraft_dir.join("build/sfm-toolchain/state");
        let selected_home = temporary.path().join("selected-jdk");
        let ambient_same_major_home = temporary.path().join("ambient-jdk");
        std::fs::create_dir_all(selected_home.join("lib")).expect("selected JDK lib");
        std::fs::create_dir_all(ambient_same_major_home.join("lib")).expect("ambient JDK lib");
        std::fs::create_dir_all(&state_dir).expect("branch state");
        std::fs::write(
            minecraft_dir.join("sfm-toolchain.lock.json"),
            "{\"schema_version\":4}",
        )
        .expect("branch lockfile");
        std::fs::write(
            selected_home.join("lib/src.zip"),
            b"selected source archive v1",
        )
        .expect("selected source archive");
        std::fs::write(
            ambient_same_major_home.join("lib/src.zip"),
            b"ambient source archive v1",
        )
        .expect("ambient source archive");
        write_branch_plan(&minecraft_dir, &selected_home);

        let selected = BranchJdkSourceProvider::resolve("17", "1.19.2", "1.19.2", &minecraft_dir)
            .expect("selected provider");
        assert_eq!(
            selected.source_archive,
            canonical_existing(&selected_home.join("lib/src.zip")).expect("selected archive")
        );
        assert_eq!(
            selected.source_hash,
            ContentHash::from_path(
                selected_home.join("lib/src.zip"),
                ContentHashAlgorithm::Blake3,
            )
            .expect("selected source hash")
        );

        std::fs::write(
            ambient_same_major_home.join("lib/src.zip"),
            b"ambient source archive v2 must not drift selection",
        )
        .expect("changed ambient source archive");
        let after_ambient_change =
            BranchJdkSourceProvider::resolve("17", "1.19.2", "1.19.2", &minecraft_dir)
                .expect("provider remains pinned");
        assert_eq!(after_ambient_change, selected);

        std::fs::write(
            selected_home.join("lib/src.zip"),
            b"selected source archive v2",
        )
        .expect("changed selected source archive");
        let after_selected_change =
            BranchJdkSourceProvider::resolve("17", "1.19.2", "1.19.2", &minecraft_dir)
                .expect("updated selected provider");
        assert_ne!(after_selected_change.source_hash, selected.source_hash);
        assert_ne!(after_selected_change.identity, selected.identity);
    }

    fn write_branch_plan(minecraft_dir: &Path, selected_home: &Path) {
        let plan = BranchToolchainPlan {
            schema_version: 1,
            branch_name: "1.19.2".to_owned(),
            minecraft_version: "1.19.2".to_owned(),
            lockfile_path: minecraft_dir
                .join("sfm-toolchain.lock.json")
                .to_string_lossy()
                .into_owned(),
            java: BranchToolchainJavaPlan {
                home: Some(selected_home.to_string_lossy().into_owned()),
                version_output: "openjdk version \"17.0.99-pinned\"".to_owned(),
                major_version: 17,
            },
            java_release: 17,
        };
        let encoded = facet_json::to_string(&plan).expect("branch plan JSON");
        std::fs::write(
            minecraft_dir.join("build/sfm-toolchain/state/last-plan.json"),
            encoded,
        )
        .expect("branch plan");
    }
}
