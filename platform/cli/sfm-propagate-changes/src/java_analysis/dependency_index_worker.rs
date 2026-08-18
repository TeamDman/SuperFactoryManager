use super::DependencyJavaSymbolIndexBody;
use super::DependencyJavaSymbolIndexStreamHeader;
use super::DependencySymbolIndexCounts;
use super::DependencySymbolIndexIdentity;
use super::DiagnosticSeverity;
use super::JavaAnalysisContextOutput;
use super::JavaAnalysisDiagnosticOutput;
use super::JavaDependencyResolutionDefinition;
use super::JavaFileFactDetail;
use super::JavaFileFactsInput;
use super::JavaSourceFile;
#[cfg(test)]
use super::JavaSourceSpanOutput;
use super::JavaSourceWorkspace;
#[cfg(test)]
use super::JavaSymbolDefinitionOutput;
use super::JavaSymbolIdentityOutput;
use super::JavaSymbolIndex;
use super::JavaSymbolKind;
use super::JdkSourceDomainState;
#[cfg(test)]
use super::ResolutionConfidence;
use super::extract_java_file_facts_from_text_with_detail;
use super::jdk_resolution_definitions;
use crate::cancellation::CancellationToken;
use eyre::WrapErr;
use facet::Facet;
use std::borrow::Borrow;
use std::collections::BTreeSet;
use std::io::BufRead;
use std::io::BufReader;
use std::io::BufWriter;
use std::io::Write;
use std::path::Path;
use std::path::PathBuf;
use std::process::Child;
use std::process::Command;
use std::process::ExitStatus;
use std::time::Duration;

const WORKER_ENV: &str = "SFM_DEPENDENCY_SYMBOL_INDEX_WORKER_REQUEST";
const WORKER_SCHEMA: &str = "sfm.dependency-symbol-index-worker/5";
const WORKER_OUTPUT_SCHEMA: &str = "sfm.dependency-symbol-index-worker-output/2";
const RESOLUTION_SCHEMA: &str = "sfm.dependency-symbol-resolution-index/3";
const WORKER_CHUNK_SIZE: usize = 32;
// Real 1.19.2 dependency evidence puts `LevelRenderer.java` at about 546 MiB
// and the resolution-heavy `BlockModelGenerators.java` at about 1.106 GiB
// private memory. Keep a hard ceiling with bounded headroom while shard
// bisection controls multi-source aggregation independently.
const WORKER_MEMORY_LIMIT_BYTES: usize = 1536 * 1024 * 1024;
const REFRESH_PARENT_MEMORY_LIMIT_BYTES: usize = 1536 * 1024 * 1024;
const WORKER_OUTPUT_LIMIT_BYTES: u64 = 128 * 1024 * 1024;
const WORKER_DIAGNOSTIC_LIMIT: usize = 8;
const KEEP_FAILED_WORKDIR_ENV: &str = "SFM_KEEP_FAILED_DEPENDENCY_INDEX_WORKDIR";

pub(crate) struct DependencyIndexBuildArtifacts {
    _temporary: tempfile::TempDir,
    records_path: PathBuf,
    counts: DependencySymbolIndexCounts,
}

impl DependencyIndexBuildArtifacts {
    pub(crate) fn empty(identity: &DependencySymbolIndexIdentity) -> eyre::Result<Self> {
        let temporary = tempfile::tempdir()?;
        let records_path = temporary.path().join("records.ndjson");
        let mut writer = BufWriter::new(std::fs::File::create(&records_path)?);
        facet_json::to_writer_std(
            &mut writer,
            &DependencyJavaSymbolIndexStreamHeader::new(identity.clone()),
        )?;
        writer.write_all(b"\n")?;
        writer.flush()?;
        Ok(Self {
            _temporary: temporary,
            records_path,
            counts: DependencySymbolIndexCounts {
                source_files: 0,
                definitions: 0,
                usages: 0,
                diagnostics: 0,
            },
        })
    }

    pub(crate) fn records_path(&self) -> &Path {
        &self.records_path
    }

    pub(crate) const fn counts(&self) -> &DependencySymbolIndexCounts {
        &self.counts
    }
}

#[derive(Facet, Clone, Copy, Debug, Eq, PartialEq)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
enum WorkerPass {
    Types,
    Members,
    Usages,
}

#[derive(Facet, Clone, Debug)]
struct WorkerSourceFile {
    path: String,
    report_path: String,
    source_set: String,
}

#[derive(Facet, Clone, Debug)]
struct WorkerRequest {
    schema: String,
    pass: WorkerPass,
    context: JavaAnalysisContextOutput,
    files: Vec<WorkerSourceFile>,
    jdk_context_path: Option<String>,
    resolution_body_path: Option<String>,
    output_path: String,
}

struct WorkerInvocation {
    pass: WorkerPass,
    files: Vec<WorkerSourceFile>,
    jdk_context_path: Option<String>,
    resolution_body_path: Option<String>,
    output_path: String,
}

/// Handle the private worker environment before public CLI parsing.
///
/// # Errors
///
/// Returns an error when the request or output cannot be read, indexed, or written.
pub(crate) fn run_dependency_index_worker_from_env() -> eyre::Result<bool> {
    let Some(request_path) = std::env::var_os(WORKER_ENV) else {
        return Ok(false);
    };
    let request = read_worker_request(Path::new(&request_path))?;
    run_worker_request(request)?;
    Ok(true)
}

fn read_worker_request(path: &Path) -> eyre::Result<WorkerRequest> {
    let file = std::fs::File::open(path)?;
    let mut lines = BufReader::new(file).lines();
    let schema = next_worker_request_field(&mut lines, "schema")?;
    if schema != WORKER_SCHEMA {
        eyre::bail!("unsupported dependency symbol index worker schema");
    }
    let pass = parse_worker_pass(&next_worker_request_field(&mut lines, "pass")?)?;
    let context_path = next_worker_request_field(&mut lines, "context")?;
    let context: JavaAnalysisContextOutput =
        facet_json::from_str(&std::fs::read_to_string(context_path)?)?;
    let jdk_context = next_worker_request_field(&mut lines, "jdk-context")?;
    let resolution = next_worker_request_field(&mut lines, "resolution")?;
    let output_path = next_worker_request_field(&mut lines, "output")?;
    let mut files = Vec::new();
    for (line_index, line) in lines.enumerate() {
        let line = line?;
        let mut fields = line.split('\t');
        if fields.next() != Some("file") {
            eyre::bail!(
                "dependency symbol worker request line {} is not a file record",
                line_index + 7
            );
        }
        let Some(path) = fields.next() else {
            eyre::bail!("dependency symbol worker file record is missing its path");
        };
        let Some(report_path) = fields.next() else {
            eyre::bail!("dependency symbol worker file record is missing its report path");
        };
        let Some(source_set) = fields.next() else {
            eyre::bail!("dependency symbol worker file record is missing its source set");
        };
        if fields.next().is_some() {
            eyre::bail!("dependency symbol worker file record has extra fields");
        }
        files.push(WorkerSourceFile {
            path: path.to_owned(),
            report_path: report_path.to_owned(),
            source_set: source_set.to_owned(),
        });
    }
    Ok(WorkerRequest {
        schema,
        pass,
        context,
        files,
        jdk_context_path: (!jdk_context.is_empty()).then_some(jdk_context),
        resolution_body_path: (!resolution.is_empty()).then_some(resolution),
        output_path,
    })
}

fn next_worker_request_field(
    lines: &mut impl Iterator<Item = std::io::Result<String>>,
    expected: &str,
) -> eyre::Result<String> {
    let line = lines
        .next()
        .transpose()?
        .ok_or_else(|| eyre::eyre!("dependency symbol worker request is missing `{expected}`"))?;
    if expected == "schema" {
        return Ok(line);
    }
    let (name, value) = line.split_once('\t').ok_or_else(|| {
        eyre::eyre!("dependency symbol worker request field `{expected}` is malformed")
    })?;
    if name != expected {
        eyre::bail!("dependency symbol worker request expected `{expected}`, found `{name}`");
    }
    Ok(value.to_owned())
}

fn write_worker_request(
    path: &Path,
    request: &WorkerInvocation,
    context_path: &Path,
) -> eyre::Result<()> {
    let file = std::fs::File::create(path)?;
    let mut writer = BufWriter::new(file);
    writeln!(writer, "{WORKER_SCHEMA}")?;
    write_worker_request_field(&mut writer, "pass", worker_pass_name(request.pass))?;
    write_worker_request_field(&mut writer, "context", &context_path.display().to_string())?;
    write_worker_request_field(
        &mut writer,
        "jdk-context",
        request.jdk_context_path.as_deref().unwrap_or_default(),
    )?;
    write_worker_request_field(
        &mut writer,
        "resolution",
        request.resolution_body_path.as_deref().unwrap_or_default(),
    )?;
    write_worker_request_field(&mut writer, "output", &request.output_path)?;
    for source in &request.files {
        for value in [&source.path, &source.report_path, &source.source_set] {
            validate_worker_request_value(value)?;
        }
        writeln!(
            writer,
            "file\t{}\t{}\t{}",
            source.path, source.report_path, source.source_set
        )?;
    }
    writer.flush()?;
    Ok(())
}

fn write_worker_request_field(
    writer: &mut impl Write,
    name: &str,
    value: &str,
) -> eyre::Result<()> {
    validate_worker_request_value(value)?;
    writeln!(writer, "{name}\t{value}")?;
    Ok(())
}

fn validate_worker_request_value(value: &str) -> eyre::Result<()> {
    if value.contains(['\t', '\r', '\n']) {
        eyre::bail!("dependency symbol worker request value contains a tab or newline");
    }
    Ok(())
}

const fn worker_pass_name(pass: WorkerPass) -> &'static str {
    match pass {
        WorkerPass::Types => "types",
        WorkerPass::Members => "members",
        WorkerPass::Usages => "usages",
    }
}

fn parse_worker_pass(value: &str) -> eyre::Result<WorkerPass> {
    match value {
        "types" => Ok(WorkerPass::Types),
        "members" => Ok(WorkerPass::Members),
        "usages" => Ok(WorkerPass::Usages),
        _ => eyre::bail!("unknown dependency symbol worker pass `{value}`"),
    }
}

fn run_worker_request(request: WorkerRequest) -> eyre::Result<()> {
    if request.files.len() > WORKER_CHUNK_SIZE {
        eyre::bail!(
            "dependency symbol index worker received {} files; maximum is {}",
            request.files.len(),
            WORKER_CHUNK_SIZE
        );
    }
    let source_texts = request
        .files
        .iter()
        .map(|file| std::fs::read_to_string(&file.path))
        .collect::<std::io::Result<Vec<_>>>()?;
    let jdk_sources = if request.pass == WorkerPass::Types {
        JdkSourceDomainState::Disabled
    } else {
        request
            .jdk_context_path
            .as_deref()
            .map(Path::new)
            .map(JdkSourceDomainState::read_worker_context)
            .transpose()?
            .unwrap_or(JdkSourceDomainState::Disabled)
    };
    let resolution_identifiers = source_texts
        .iter()
        .flat_map(|source| java_identifier_tokens(source))
        .map(str::to_owned)
        .collect::<BTreeSet<_>>();
    let resolution_member_accesses = source_texts
        .iter()
        .flat_map(|source| java_member_access_tokens(source))
        .map(str::to_owned)
        .collect::<BTreeSet<_>>();
    let mut dependencies = request
        .resolution_body_path
        .as_deref()
        .map(|path| {
            read_resolution_body_relevant(
                path,
                &resolution_identifiers,
                &resolution_member_accesses,
            )
        })
        .transpose()?
        .unwrap_or_default();
    dependencies.extend(worker_jdk_resolution_definitions(
        &request.context,
        &request.files,
        &source_texts,
        &jdk_sources,
    )?);
    dependencies.sort();
    dependencies.dedup();
    let mut body = DependencyJavaSymbolIndexBody::new(Vec::new(), Vec::new(), Vec::new());
    for (file, source) in request.files.into_iter().zip(source_texts) {
        append_worker_file_output(
            &request.context,
            request.pass,
            file,
            &source,
            &dependencies,
            &mut body,
        )?;
    }
    limit_worker_diagnostics(&mut body.diagnostics);
    let body = DependencyJavaSymbolIndexBody::new(body.definitions, body.usages, body.diagnostics);
    write_body(Path::new(&request.output_path), &body)?;
    Ok(())
}

fn append_worker_file_output(
    context: &JavaAnalysisContextOutput,
    pass: WorkerPass,
    file: WorkerSourceFile,
    source: &str,
    dependencies: &[JavaDependencyResolutionDefinition],
    body: &mut DependencyJavaSymbolIndexBody,
) -> eyre::Result<()> {
    let report_path = file.report_path.clone();
    let resolution_dependencies =
        (!dependencies.is_empty()).then(|| relevant_resolution_definitions(dependencies, source));
    let workspace = JavaSourceWorkspace {
        context: context.clone(),
        root_authorities: Vec::new(),
        files: vec![JavaSourceFile {
            absolute_path: PathBuf::from(file.path),
            root_id: "worker-shard".to_owned(),
            root_relative_path: file.report_path.clone(),
            report_path: file.report_path,
            source_set: file.source_set,
            source_override: None,
        }],
        diagnostics: Vec::new(),
        classpath_entries: Vec::new(),
        jdk_sources: JdkSourceDomainState::Disabled,
    };
    let index = JavaSymbolIndex::build_dependency_worker(
        &workspace,
        resolution_dependencies.as_deref(),
        pass == WorkerPass::Usages,
        if pass == WorkerPass::Types { 8 } else { 0 },
    )?;
    let mut file_body = index.dependency_body(&[]);
    file_body.definitions.retain(|definition| {
        definition.identifier_span.path == report_path
            && match pass {
                WorkerPass::Types => is_type(definition.symbol.kind),
                WorkerPass::Members => !is_type(definition.symbol.kind),
                WorkerPass::Usages => false,
            }
    });
    file_body
        .usages
        .retain(|usage| pass == WorkerPass::Usages && usage.span.path == report_path);
    // Type shards run before the cross-shard type table exists. The shared
    // analyzer also inspects members, so only its syntax diagnostics are
    // authoritative in this pass; member diagnostics belong to the later
    // member pass, which resolves against the completed type table.
    file_body.diagnostics.retain(|diagnostic| {
        diagnostic
            .span
            .as_ref()
            .is_none_or(|span| span.path == report_path)
            && (pass != WorkerPass::Types || diagnostic.code.starts_with("java.parse-"))
    });
    body.definitions.append(&mut file_body.definitions);
    body.usages.append(&mut file_body.usages);
    body.diagnostics.append(&mut file_body.diagnostics);
    Ok(())
}

fn worker_jdk_resolution_definitions(
    context: &JavaAnalysisContextOutput,
    files: &[WorkerSourceFile],
    source_texts: &[String],
    jdk_sources: &JdkSourceDomainState,
) -> eyre::Result<Vec<JavaDependencyResolutionDefinition>> {
    if !matches!(jdk_sources, JdkSourceDomainState::Ready(_)) {
        return Ok(Vec::new());
    }
    let mut project_facts = Vec::with_capacity(files.len());
    for (sequence, (file, source)) in files.iter().zip(source_texts).enumerate() {
        let visible_source_sets = context
            .source_sets
            .iter()
            .find(|source_set| source_set.id == file.source_set)
            .map_or_else(
                || vec![file.source_set.clone()],
                |source_set| source_set.visible_source_sets.clone(),
            );
        let mut facts = extract_java_file_facts_from_text_with_detail(
            JavaFileFactsInput {
                sequence: u64::try_from(sequence).unwrap_or(u64::MAX),
                report_path: &file.report_path,
                source_set: &file.source_set,
                visible_source_sets: &visible_source_sets,
            },
            source.clone(),
            Some(WORKER_DIAGNOSTIC_LIMIT),
            JavaFileFactDetail::Declarations,
        )?;
        // JDK candidate discovery needs imports and referenced type names, not
        // retained project declarations. Release those owned collections before
        // the worker materializes the small set of selected JDK source facts.
        facts.types.clear();
        facts.fields.clear();
        facts.callables.clear();
        facts.diagnostics.clear();
        project_facts.push(facts);
    }
    let visible_source_sets = context
        .source_sets
        .iter()
        .map(|source_set| source_set.id.clone())
        .collect::<Vec<_>>();
    let jdk_facts = jdk_sources.facts_for_project(
        &project_facts,
        u64::try_from(project_facts.len()).unwrap_or(u64::MAX),
        &visible_source_sets,
        &CancellationToken::new(),
    )?;
    Ok(jdk_resolution_definitions(&jdk_facts))
}

fn relevant_resolution_definitions(
    definitions: &[JavaDependencyResolutionDefinition],
    source: &str,
) -> Vec<JavaDependencyResolutionDefinition> {
    let identifiers = java_identifier_tokens(source);
    let member_accesses = java_member_access_tokens(source);
    definitions
        .iter()
        .filter(|definition| {
            resolution_definition_is_relevant(
                definition.symbol.kind,
                &definition.symbol.owner,
                &definition.symbol.name,
                &identifiers,
                &member_accesses,
            )
        })
        .cloned()
        .collect()
}

fn resolution_definition_is_relevant<T: Borrow<str> + Ord, U: Borrow<str> + Ord>(
    kind: JavaSymbolKind,
    owner: &str,
    name: &str,
    identifiers: &BTreeSet<T>,
    member_accesses: &BTreeSet<U>,
) -> bool {
    let owner_name = owner.rsplit(['.', '$']).next().unwrap_or(owner);
    match kind {
        JavaSymbolKind::Class
        | JavaSymbolKind::Interface
        | JavaSymbolKind::Enum
        | JavaSymbolKind::Record
        | JavaSymbolKind::Annotation => identifiers.contains(name),
        JavaSymbolKind::Constructor => identifiers.contains(owner_name),
        JavaSymbolKind::Field | JavaSymbolKind::Method => {
            member_accesses.contains(name) && identifiers.contains(owner_name)
        }
        JavaSymbolKind::LocalVariable | JavaSymbolKind::Parameter => false,
    }
}

pub(crate) fn java_identifier_tokens(source: &str) -> BTreeSet<&str> {
    source
        .split(|character: char| {
            !(character == '_' || character == '$' || character.is_alphanumeric())
        })
        .filter(|token| !token.is_empty())
        .collect()
}

pub(crate) fn java_member_access_tokens(source: &str) -> BTreeSet<&str> {
    let mut tokens = BTreeSet::new();
    let mut token_start = None;
    for (index, character) in source
        .char_indices()
        .chain(std::iter::once((source.len(), '\0')))
    {
        let is_identifier = character == '_' || character == '$' || character.is_alphanumeric();
        match (token_start, is_identifier) {
            (None, true) => token_start = Some(index),
            (Some(start), false) => {
                if source[..start]
                    .chars()
                    .rev()
                    .find(|previous| !previous.is_whitespace())
                    == Some('.')
                {
                    tokens.insert(&source[start..index]);
                }
                token_start = None;
            }
            _ => {}
        }
    }
    tokens
}

/// Build a dependency index through short-lived, fixed-size worker processes.
/// The operating system reclaims each parser heap between shards.
///
/// # Errors
///
/// Returns an error when a worker cannot start, is cancelled, exits
/// unsuccessfully, or emits invalid typed output.
pub(crate) fn build_dependency_index_sharded(
    workspace: &JavaSourceWorkspace,
    identity: &DependencySymbolIndexIdentity,
    cancellation_token: &CancellationToken,
) -> eyre::Result<DependencyIndexBuildArtifacts> {
    build_java_index_sharded(workspace, identity, &[], true, cancellation_token)
}

/// Build a live query index through the same short-lived parser workers used
/// by dependency refresh. `external_resolution` supplies lockfile-pinned
/// declarations for resolving live signatures without retaining dependency
/// syntax trees in the query process.
pub(crate) fn build_java_index_sharded(
    workspace: &JavaSourceWorkspace,
    identity: &DependencySymbolIndexIdentity,
    external_resolution: &[JavaDependencyResolutionDefinition],
    include_usages: bool,
    cancellation_token: &CancellationToken,
) -> eyre::Result<DependencyIndexBuildArtifacts> {
    let temporary = tempfile::tempdir()?;
    let result = build_java_index_sharded_in_temp(
        workspace,
        identity,
        external_resolution,
        include_usages,
        cancellation_token,
        temporary.path(),
    );
    match result {
        Ok((records_path, counts)) => Ok(DependencyIndexBuildArtifacts {
            _temporary: temporary,
            records_path,
            counts,
        }),
        Err(error) if std::env::var_os(KEEP_FAILED_WORKDIR_ENV).is_some() => {
            let preserved = temporary.keep();
            Err(error.wrap_err(format!(
                "preserved failed dependency-index work directory at {} because {KEEP_FAILED_WORKDIR_ENV} is set",
                preserved.display()
            )))
        }
        Err(error) => Err(error),
    }
}

fn build_java_index_sharded_in_temp(
    workspace: &JavaSourceWorkspace,
    identity: &DependencySymbolIndexIdentity,
    external_resolution: &[JavaDependencyResolutionDefinition],
    include_usages: bool,
    cancellation_token: &CancellationToken,
    temporary: &Path,
) -> eyre::Result<(PathBuf, DependencySymbolIndexCounts)> {
    let context_path = temporary.join("context.json");
    let mut context_writer = BufWriter::new(std::fs::File::create(&context_path)?);
    facet_json::to_writer_std(&mut context_writer, &workspace.context)?;
    context_writer.flush()?;
    drop(context_writer);
    let jdk_context_path = temporary.join("jdk-context.json");
    let jdk_context_path = workspace
        .jdk_sources
        .write_worker_context(&jdk_context_path)?
        .then_some(jdk_context_path);
    let records_path = temporary.join("records.ndjson");
    let mut records = BufWriter::new(std::fs::File::create(&records_path)?);
    facet_json::to_writer_std(
        &mut records,
        &DependencyJavaSymbolIndexStreamHeader::new(identity.clone()),
    )?;
    records.write_all(b"\n")?;
    let mut counts = DependencySymbolIndexCounts {
        source_files: u64::try_from(workspace.files.len()).unwrap_or(u64::MAX),
        definitions: 0,
        usages: 0,
        diagnostics: 0,
    };
    let mut diagnostics_seen = BTreeSet::new();

    let external_path = temporary.join("external.tsv");
    write_resolution_index(&external_path, external_resolution)?;
    let types_path = temporary.join("types.tsv");
    run_resolution_pass(
        WorkerPass::Types,
        workspace,
        None,
        temporary,
        &context_path,
        jdk_context_path.as_deref(),
        cancellation_token,
        &mut records,
        &types_path,
        &mut counts,
        &mut diagnostics_seen,
    )?;

    let member_input_path = temporary.join("member-input.tsv");
    combine_resolution_files(&[&types_path, &external_path], &member_input_path)?;
    let members_path = temporary.join("members.tsv");
    run_resolution_pass(
        WorkerPass::Members,
        workspace,
        Some(&member_input_path),
        temporary,
        &context_path,
        jdk_context_path.as_deref(),
        cancellation_token,
        &mut records,
        &members_path,
        &mut counts,
        &mut diagnostics_seen,
    )?;

    let definitions_path = temporary.join("definitions.tsv");
    combine_resolution_files(
        &[&types_path, &members_path, &external_path],
        &definitions_path,
    )?;
    if include_usages {
        run_pass(
            WorkerPass::Usages,
            workspace,
            Some(&definitions_path),
            temporary,
            &context_path,
            jdk_context_path.as_deref(),
            cancellation_token,
            &mut records,
            None,
            &mut counts,
            &mut diagnostics_seen,
        )?;
    }
    records.flush()?;
    Ok((records_path, counts))
}

#[expect(
    clippy::too_many_arguments,
    reason = "one resolution pass owns the worker paths, cancellation, and typed output sinks"
)]
fn run_resolution_pass(
    pass: WorkerPass,
    workspace: &JavaSourceWorkspace,
    resolution_body_path: Option<&Path>,
    temporary: &Path,
    context_path: &Path,
    jdk_context_path: Option<&Path>,
    cancellation_token: &CancellationToken,
    records: &mut BufWriter<std::fs::File>,
    output_path: &Path,
    counts: &mut DependencySymbolIndexCounts,
    diagnostics_seen: &mut BTreeSet<String>,
) -> eyre::Result<()> {
    let mut resolution = BufWriter::new(std::fs::File::create(output_path)?);
    writeln!(resolution, "{RESOLUTION_SCHEMA}")?;
    run_pass(
        pass,
        workspace,
        resolution_body_path,
        temporary,
        context_path,
        jdk_context_path,
        cancellation_token,
        records,
        Some(&mut resolution),
        counts,
        diagnostics_seen,
    )?;
    resolution.flush()?;
    Ok(())
}

fn write_resolution_index(
    path: &Path,
    definitions: &[JavaDependencyResolutionDefinition],
) -> eyre::Result<()> {
    let mut writer = BufWriter::new(std::fs::File::create(path)?);
    writeln!(writer, "{RESOLUTION_SCHEMA}")?;
    for definition in definitions {
        write_symbol_route(&mut writer, &definition.symbol, &definition.source_set)?;
        writer.write_all(b"\n")?;
    }
    writer.flush()?;
    Ok(())
}

fn combine_resolution_files(inputs: &[&Path], output: &Path) -> eyre::Result<()> {
    let mut writer = BufWriter::new(std::fs::File::create(output)?);
    writeln!(writer, "{RESOLUTION_SCHEMA}")?;
    for input in inputs {
        copy_resolution_rows(input, &mut writer)?;
    }
    writer.flush()?;
    Ok(())
}

fn copy_resolution_rows(path: &Path, writer: &mut impl Write) -> eyre::Result<()> {
    let file = std::fs::File::open(path)?;
    let mut reader = BufReader::new(file);
    let mut line = Vec::new();
    if reader.read_until(b'\n', &mut line)? == 0 {
        eyre::bail!("dependency symbol resolution index is empty");
    }
    trim_line_ending(&mut line);
    if line != RESOLUTION_SCHEMA.as_bytes() {
        eyre::bail!("unsupported dependency symbol resolution index schema");
    }
    loop {
        line.clear();
        if reader.read_until(b'\n', &mut line)? == 0 {
            break;
        }
        writer.write_all(&line)?;
        if line.last() != Some(&b'\n') {
            writer.write_all(b"\n")?;
        }
    }
    Ok(())
}

fn limit_worker_diagnostics(diagnostics: &mut Vec<JavaAnalysisDiagnosticOutput>) {
    diagnostics.sort();
    diagnostics.dedup();
    let suppressed = diagnostics.len().saturating_sub(WORKER_DIAGNOSTIC_LIMIT);
    diagnostics.truncate(WORKER_DIAGNOSTIC_LIMIT);
    if suppressed > 0 {
        diagnostics.push(JavaAnalysisDiagnosticOutput {
            code: "java.dependency-worker-diagnostics-suppressed".to_owned(),
            severity: DiagnosticSeverity::Warning,
            message: format!(
                "Suppressed {suppressed} additional dependency diagnostics in this worker shard"
            ),
            span: None,
        });
    }
}

/// Put the refresh coordinator itself under a hard process-memory ceiling.
/// This bounds compact index accumulation independently from parser workers.
///
/// # Errors
///
/// Returns an error before refresh begins if the operating system cannot
/// enforce the ceiling.
#[cfg(windows)]
pub(crate) fn enforce_refresh_parent_memory_limit() -> eyre::Result<()> {
    use windows::Win32::System::Threading::GetCurrentProcess;

    let memory_limit = create_memory_limit_job()?;
    set_job_memory_limit(&memory_limit, REFRESH_PARENT_MEMORY_LIMIT_BYTES, false)?;
    // SAFETY: `GetCurrentProcess` has no preconditions and returns a pseudo
    // handle valid for the lifetime of this process.
    let current_process = unsafe { GetCurrentProcess() };
    assign_process_to_memory_job(&memory_limit, current_process)?;
    std::mem::forget(memory_limit);
    Ok(())
}

#[cfg(not(windows))]
pub(crate) fn enforce_refresh_parent_memory_limit() -> eyre::Result<()> {
    Ok(())
}

#[expect(
    clippy::too_many_arguments,
    reason = "one pass explicitly owns its workspace, worker protocol paths, cancellation, and output evidence"
)]
fn run_pass(
    pass: WorkerPass,
    workspace: &JavaSourceWorkspace,
    resolution_body_path: Option<&Path>,
    temporary: &Path,
    context_path: &Path,
    jdk_context_path: Option<&Path>,
    cancellation_token: &CancellationToken,
    records: &mut BufWriter<std::fs::File>,
    resolution: Option<&mut BufWriter<std::fs::File>>,
    counts: &mut DependencySymbolIndexCounts,
    diagnostics_seen: &mut BTreeSet<String>,
) -> eyre::Result<()> {
    let mut accumulator = PassAccumulator {
        records,
        resolution,
        counts,
        diagnostics_seen,
        output: PassOutputMeasurements::default(),
    };
    let chunks = workspace
        .files
        .chunks(WORKER_CHUNK_SIZE)
        .collect::<Vec<_>>();
    for (chunk_index, chunk) in chunks.iter().enumerate() {
        run_pass_chunk(
            pass,
            resolution_body_path,
            temporary,
            context_path,
            jdk_context_path,
            cancellation_token,
            chunk,
            &chunk_index.to_string(),
            chunk_index + 1,
            chunks.len(),
            &mut accumulator,
        )?;
        tracing::info!(
            target: "sfm::java_analysis",
            pass = ?pass,
            completed = chunk_index + 1,
            total = chunks.len(),
            "dependency symbol index worker progress"
        );
    }
    tracing::info!(
        target: "sfm::java_analysis",
        pass = ?pass,
        successful_workers = accumulator.output.successful_workers,
        streamed_output_bytes = accumulator.output.streamed_bytes,
        largest_worker_output_bytes = accumulator.output.largest_worker_bytes,
        "dependency symbol index pass completed through bounded worker spools"
    );
    Ok(())
}

struct PassAccumulator<'a> {
    records: &'a mut BufWriter<std::fs::File>,
    resolution: Option<&'a mut BufWriter<std::fs::File>>,
    counts: &'a mut DependencySymbolIndexCounts,
    diagnostics_seen: &'a mut BTreeSet<String>,
    output: PassOutputMeasurements,
}

#[derive(Debug, Default, PartialEq, Eq)]
struct PassOutputMeasurements {
    streamed_bytes: u64,
    largest_worker_bytes: u64,
    successful_workers: usize,
}

impl PassOutputMeasurements {
    fn observe_worker_output(&mut self, output_bytes: u64) -> eyre::Result<()> {
        if output_bytes > WORKER_OUTPUT_LIMIT_BYTES {
            eyre::bail!(
                "dependency symbol index worker output exceeded {} bytes",
                WORKER_OUTPUT_LIMIT_BYTES
            );
        }
        self.streamed_bytes = self
            .streamed_bytes
            .checked_add(output_bytes)
            .ok_or_else(|| eyre::eyre!("dependency symbol index output byte count overflowed"))?;
        self.largest_worker_bytes = self.largest_worker_bytes.max(output_bytes);
        Ok(())
    }
}

#[expect(
    clippy::too_many_arguments,
    reason = "recursive worker bisection carries explicit pass and evidence context"
)]
fn run_pass_chunk(
    pass: WorkerPass,
    resolution_body_path: Option<&Path>,
    temporary: &Path,
    context_path: &Path,
    jdk_context_path: Option<&Path>,
    cancellation_token: &CancellationToken,
    files: &[JavaSourceFile],
    shard: &str,
    initial_chunk: usize,
    initial_chunk_count: usize,
    accumulator: &mut PassAccumulator<'_>,
) -> eyre::Result<()> {
    cancellation_token.bail_if_cancelled()?;
    let request_path = temporary.join(format!("{pass:?}-{shard}-request.json"));
    let output_path = temporary.join(format!("{pass:?}-{shard}-output.json"));
    let request = WorkerInvocation {
        pass,
        files: files
            .iter()
            .map(|file| WorkerSourceFile {
                path: file.absolute_path.display().to_string(),
                report_path: file.report_path.clone(),
                source_set: file.source_set.clone(),
            })
            .collect(),
        jdk_context_path: jdk_context_path.map(|path| path.display().to_string()),
        resolution_body_path: resolution_body_path.map(|path| path.display().to_string()),
        output_path: output_path.display().to_string(),
    };
    write_worker_request(&request_path, &request, context_path)?;
    if let Some(status) = run_worker(&request_path, cancellation_token).wrap_err_with(|| {
        format!(
            "dependency symbol index {pass:?} pass worker infrastructure failed; request={} output={}",
            request_path.display(),
            output_path.display()
        )
    })? {
        if files.len() > 1 {
            let split = files.len() / 2;
            tracing::warn!(
                target: "sfm::java_analysis",
                pass = ?pass,
                shard,
                source_count = files.len(),
                %status,
                "dependency symbol index worker failed; retrying deterministic half-shards"
            );
            run_pass_chunk(
                pass,
                resolution_body_path,
                temporary,
                context_path,
                jdk_context_path,
                cancellation_token,
                &files[..split],
                &format!("{shard}a"),
                initial_chunk,
                initial_chunk_count,
                accumulator,
            )?;
            return run_pass_chunk(
                pass,
                resolution_body_path,
                temporary,
                context_path,
                jdk_context_path,
                cancellation_token,
                &files[split..],
                &format!("{shard}b"),
                initial_chunk,
                initial_chunk_count,
                accumulator,
            );
        }
        eyre::bail!(
            "dependency symbol index {pass:?} pass worker for one source exited with {status}; request={} output={}; each worker is limited to {} bytes",
            request_path.display(),
            output_path.display(),
            WORKER_MEMORY_LIMIT_BYTES
        );
    }
    let output_bytes = consume_worker_output(&output_path, accumulator)?;
    std::fs::remove_file(&request_path).wrap_err_with(|| {
        format!(
            "Failed to remove consumed dependency symbol worker request {}",
            request_path.display()
        )
    })?;
    tracing::info!(
        target: "sfm::java_analysis",
        pass = ?pass,
        shard,
        source_count = files.len(),
        output_bytes,
        successful_workers = accumulator.output.successful_workers,
        pass_streamed_output_bytes = accumulator.output.streamed_bytes,
        initial_chunk,
        initial_chunk_count,
        "dependency symbol index worker shard completed"
    );
    Ok(())
}

fn run_worker(
    request_path: &Path,
    cancellation_token: &CancellationToken,
) -> eyre::Result<Option<ExitStatus>> {
    let mut command = Command::new(std::env::current_exe()?);
    command.env(WORKER_ENV, request_path);
    let (mut child, _memory_limit) = spawn_memory_limited_worker(&mut command)?;
    loop {
        if let Some(status) = child.try_wait()? {
            if status.success() {
                return Ok(None);
            }
            return Ok(Some(status));
        }
        if cancellation_token.is_cancelled() {
            let _ = child.kill();
            let _ = child.wait();
            cancellation_token.bail_if_cancelled()?;
        }
        std::thread::sleep(Duration::from_millis(25));
    }
}

#[cfg(windows)]
struct WorkerMemoryLimit(windows::Win32::Foundation::HANDLE);

#[cfg(windows)]
impl Drop for WorkerMemoryLimit {
    fn drop(&mut self) {
        // SAFETY: this handle was returned by `CreateJobObjectW`, remains owned
        // by this guard, and is closed exactly once here.
        let _ = unsafe { windows::Win32::Foundation::CloseHandle(self.0) };
    }
}

#[cfg(windows)]
fn create_memory_limit_job() -> eyre::Result<WorkerMemoryLimit> {
    use windows::Win32::System::JobObjects::CreateJobObjectW;

    // SAFETY: null security attributes and no name request an ordinary private
    // job object; both optional arguments satisfy the Win32 contract.
    let handle = unsafe { CreateJobObjectW(None, None) }?;
    Ok(WorkerMemoryLimit(handle))
}

#[cfg(windows)]
fn set_job_memory_limit(
    job: &WorkerMemoryLimit,
    bytes: usize,
    kill_on_close: bool,
) -> eyre::Result<()> {
    use windows::Win32::System::JobObjects::JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE;
    use windows::Win32::System::JobObjects::JOB_OBJECT_LIMIT_PROCESS_MEMORY;
    use windows::Win32::System::JobObjects::JOBOBJECT_EXTENDED_LIMIT_INFORMATION;
    use windows::Win32::System::JobObjects::JobObjectExtendedLimitInformation;
    use windows::Win32::System::JobObjects::SetInformationJobObject;

    let mut information = JOBOBJECT_EXTENDED_LIMIT_INFORMATION::default();
    information.BasicLimitInformation.LimitFlags = if kill_on_close {
        JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE | JOB_OBJECT_LIMIT_PROCESS_MEMORY
    } else {
        JOB_OBJECT_LIMIT_PROCESS_MEMORY
    };
    information.ProcessMemoryLimit = bytes;
    let information_size =
        u32::try_from(std::mem::size_of::<JOBOBJECT_EXTENDED_LIMIT_INFORMATION>())
            .wrap_err("Job Object information size does not fit the Win32 API")?;
    // SAFETY: `job` owns a live Job Object handle, the pointer references the
    // declared information type for the duration of the call, and the byte
    // count is the exact size of that type.
    unsafe {
        SetInformationJobObject(
            job.0,
            JobObjectExtendedLimitInformation,
            std::ptr::from_ref(&information).cast(),
            information_size,
        )
    }?;
    Ok(())
}

#[cfg(windows)]
fn assign_process_to_memory_job(
    job: &WorkerMemoryLimit,
    process: windows::Win32::Foundation::HANDLE,
) -> eyre::Result<()> {
    use windows::Win32::System::JobObjects::AssignProcessToJobObject;

    // SAFETY: `job` owns a live Job Object handle and `process` is either the
    // current-process pseudo handle or a live child process handle.
    unsafe { AssignProcessToJobObject(job.0, process) }?;
    Ok(())
}

#[cfg(windows)]
fn spawn_memory_limited_worker(command: &mut Command) -> eyre::Result<(Child, WorkerMemoryLimit)> {
    use std::os::windows::io::AsRawHandle;
    use windows::Win32::Foundation::HANDLE;

    let memory_limit = create_memory_limit_job()?;
    set_job_memory_limit(&memory_limit, WORKER_MEMORY_LIMIT_BYTES, true)?;
    let mut child = command.spawn()?;
    let process = HANDLE(child.as_raw_handle());
    if let Err(error) = assign_process_to_memory_job(&memory_limit, process) {
        let _ = child.kill();
        let _ = child.wait();
        return Err(error);
    }
    Ok((child, memory_limit))
}

#[cfg(not(windows))]
fn spawn_memory_limited_worker(command: &mut Command) -> eyre::Result<(Child, ())> {
    Ok((command.spawn()?, ()))
}

#[cfg(test)]
fn read_body(path: impl AsRef<Path>) -> eyre::Result<DependencyJavaSymbolIndexBody> {
    let file = std::fs::File::open(path)?;
    let mut lines = BufReader::new(file).lines();
    let schema = lines
        .next()
        .transpose()?
        .ok_or_else(|| eyre::eyre!("dependency symbol worker output is empty"))?;
    if schema != WORKER_OUTPUT_SCHEMA {
        eyre::bail!("unsupported dependency symbol worker output schema");
    }
    let mut definitions = Vec::new();
    let mut usages = Vec::new();
    let mut diagnostics = Vec::new();
    for (line_index, line) in lines.enumerate() {
        let line = line?;
        if line.is_empty() {
            continue;
        }
        let (record_kind, record) = line.split_once('\t').ok_or_else(|| {
            eyre::eyre!(
                "dependency symbol worker output line {} has no record tag",
                line_index + 2
            )
        })?;
        match record_kind {
            "definition" => {
                let (_, json) = split_worker_symbol_route(record, true)?;
                definitions.push(facet_json::from_str(json)?);
            }
            "usage" => {
                let (_, json) = split_worker_symbol_route(record, false)?;
                usages.push(facet_json::from_str(json)?);
            }
            "diagnostic" => diagnostics.push(facet_json::from_str(record)?),
            _ => eyre::bail!(
                "dependency symbol worker output line {} has unknown record tag `{record_kind}`",
                line_index + 2
            ),
        }
    }
    Ok(DependencyJavaSymbolIndexBody::new(
        definitions,
        usages,
        diagnostics,
    ))
}

fn append_worker_output(path: &Path, accumulator: &mut PassAccumulator<'_>) -> eyre::Result<()> {
    let file = std::fs::File::open(path)?;
    let mut reader = BufReader::new(file);
    let mut line = Vec::new();
    if reader.read_until(b'\n', &mut line)? == 0 {
        eyre::bail!("dependency symbol worker output is empty");
    }
    trim_line_ending(&mut line);
    if line != WORKER_OUTPUT_SCHEMA.as_bytes() {
        eyre::bail!("unsupported dependency symbol worker output schema");
    }
    let mut line_index = 1_usize;
    loop {
        line.clear();
        if reader.read_until(b'\n', &mut line)? == 0 {
            break;
        }
        line_index += 1;
        trim_line_ending(&mut line);
        if line.is_empty() {
            continue;
        }
        let Some(separator) = line.iter().position(|byte| *byte == b'\t') else {
            eyre::bail!("dependency symbol worker output line {line_index} has no record tag");
        };
        let record_kind = &line[..separator];
        let record = &line[separator + 1..];
        let emit = match record_kind {
            b"definition" => {
                let record = std::str::from_utf8(record)?;
                let (route, _) = split_worker_symbol_route(record, true)?;
                accumulator.counts.definitions = accumulator.counts.definitions.saturating_add(1);
                if let Some(writer) = accumulator.resolution.as_mut() {
                    writer.write_all(route.as_bytes())?;
                    writer.write_all(b"\n")?;
                }
                true
            }
            b"usage" => {
                let record = std::str::from_utf8(record)?;
                split_worker_symbol_route(record, false)?;
                accumulator.counts.usages = accumulator.counts.usages.saturating_add(1);
                true
            }
            b"diagnostic" => {
                let record = std::str::from_utf8(record)?;
                if accumulator.diagnostics_seen.insert(record.to_owned()) {
                    accumulator.counts.diagnostics =
                        accumulator.counts.diagnostics.saturating_add(1);
                    true
                } else {
                    false
                }
            }
            _ => {
                let record_kind = String::from_utf8_lossy(record_kind);
                eyre::bail!(
                    "dependency symbol worker output line {line_index} has unknown record tag `{record_kind}`"
                );
            }
        };
        if emit {
            accumulator.records.write_all(&line)?;
            accumulator.records.write_all(b"\n")?;
        }
    }
    Ok(())
}

fn consume_worker_output(path: &Path, accumulator: &mut PassAccumulator<'_>) -> eyre::Result<u64> {
    let output_bytes = std::fs::metadata(path)
        .wrap_err_with(|| format!("Failed to measure worker spool {}", path.display()))?
        .len();
    accumulator.output.observe_worker_output(output_bytes)?;
    append_worker_output(path, accumulator)?;
    std::fs::remove_file(path).wrap_err_with(|| {
        format!(
            "Failed to remove consumed dependency symbol worker spool {}",
            path.display()
        )
    })?;
    accumulator.output.successful_workers += 1;
    Ok(output_bytes)
}

fn trim_line_ending(line: &mut Vec<u8>) {
    if line.last() == Some(&b'\n') {
        line.pop();
    }
    if line.last() == Some(&b'\r') {
        line.pop();
    }
}

fn split_worker_symbol_route(record: &str, require_source_set: bool) -> eyre::Result<(&str, &str)> {
    let (route, json) = record
        .rsplit_once('\t')
        .ok_or_else(|| eyre::eyre!("dependency symbol routed record is missing its JSON body"))?;
    validate_symbol_route(route, require_source_set)?;
    if json.is_empty() {
        eyre::bail!("dependency symbol routed record has an empty JSON body");
    }
    Ok((route, json))
}

fn validate_symbol_route(record: &str, require_source_set: bool) -> eyre::Result<()> {
    let mut fields = record.split('\t');
    let Some(kind) = fields.next() else {
        eyre::bail!("dependency symbol resolution record is empty");
    };
    let Some(owner) = fields.next() else {
        eyre::bail!("dependency symbol resolution record is missing owner");
    };
    let Some(name) = fields.next() else {
        eyre::bail!("dependency symbol resolution record is missing name");
    };
    let Some(_descriptor) = fields.next() else {
        eyre::bail!("dependency symbol resolution record is missing descriptor");
    };
    let Some(qualified_name) = fields.next() else {
        eyre::bail!("dependency symbol resolution record is missing qualified name");
    };
    let Some(source_set) = fields.next() else {
        eyre::bail!("dependency symbol resolution record is missing source set");
    };
    if fields.next().is_some() {
        eyre::bail!("dependency symbol resolution record has extra fields");
    }
    parse_resolution_kind(kind)?;
    if owner.is_empty() || name.is_empty() || qualified_name.is_empty() {
        eyre::bail!("dependency symbol resolution record contains an empty required field");
    }
    if require_source_set == source_set.is_empty() {
        eyre::bail!("dependency symbol routed record has an invalid source-set field");
    }
    Ok(())
}

#[cfg(test)]
fn read_resolution_body(
    path: impl AsRef<Path>,
) -> eyre::Result<Vec<JavaDependencyResolutionDefinition>> {
    read_resolution_body_filtered(path, None)
}

fn read_resolution_body_relevant(
    path: impl AsRef<Path>,
    identifiers: &BTreeSet<String>,
    member_accesses: &BTreeSet<String>,
) -> eyre::Result<Vec<JavaDependencyResolutionDefinition>> {
    read_resolution_body_filtered(path, Some((identifiers, member_accesses)))
}

fn read_resolution_body_filtered(
    path: impl AsRef<Path>,
    vocabulary: Option<(&BTreeSet<String>, &BTreeSet<String>)>,
) -> eyre::Result<Vec<JavaDependencyResolutionDefinition>> {
    let file = std::fs::File::open(path)?;
    let mut reader = BufReader::new(file);
    let mut line = Vec::new();
    if reader.read_until(b'\n', &mut line)? == 0 {
        eyre::bail!("dependency symbol resolution index is empty");
    }
    trim_line_ending(&mut line);
    if line != RESOLUTION_SCHEMA.as_bytes() {
        eyre::bail!("unsupported dependency symbol resolution index schema");
    }
    let mut definitions = Vec::new();
    let mut line_index = 1_usize;
    loop {
        line.clear();
        if reader.read_until(b'\n', &mut line)? == 0 {
            break;
        }
        line_index += 1;
        trim_line_ending(&mut line);
        if line.is_empty() {
            continue;
        }
        let record = std::str::from_utf8(&line)?;
        let mut fields = record.split('\t');
        let kind = fields.next().unwrap_or_default();
        let owner = fields.next().unwrap_or_default();
        let name = fields.next().unwrap_or_default();
        let descriptor = fields.next().unwrap_or_default();
        let qualified_name = fields.next().unwrap_or_default();
        let source_set = fields.next().unwrap_or_default();
        if fields.next().is_some() {
            eyre::bail!(
                "dependency symbol resolution index line {line_index} has more than 6 fields"
            );
        }
        let kind = parse_resolution_kind(kind)?;
        if owner.is_empty() || name.is_empty() || qualified_name.is_empty() || source_set.is_empty()
        {
            eyre::bail!(
                "dependency symbol resolution index line {} contains an empty required field",
                line_index
            );
        }
        if vocabulary.is_some_and(|(identifiers, member_accesses)| {
            !resolution_definition_is_relevant(kind, owner, name, identifiers, member_accesses)
        }) {
            continue;
        }
        definitions.push(JavaDependencyResolutionDefinition {
            symbol: JavaSymbolIdentityOutput {
                kind,
                owner: owner.to_owned(),
                name: name.to_owned(),
                descriptor: (!descriptor.is_empty()).then(|| descriptor.to_owned()),
                qualified_name: qualified_name.to_owned(),
            },
            source_set: source_set.to_owned(),
        });
    }
    Ok(definitions)
}

#[cfg(test)]
fn write_resolution_body(
    path: &Path,
    definitions: &[JavaSymbolDefinitionOutput],
) -> eyre::Result<()> {
    let file = std::fs::File::create(path)?;
    let mut writer = BufWriter::new(file);
    writeln!(writer, "{RESOLUTION_SCHEMA}")?;
    for definition in definitions {
        write_resolution_definition(&mut writer, definition)?;
    }
    writer.flush()?;
    Ok(())
}

#[cfg(test)]
fn write_resolution_definition(
    writer: &mut impl Write,
    definition: &JavaSymbolDefinitionOutput,
) -> eyre::Result<()> {
    write_symbol_route(
        writer,
        &definition.symbol,
        &definition.identifier_span.source_set,
    )?;
    writer.write_all(b"\n")?;
    Ok(())
}

fn write_symbol_route(
    writer: &mut impl Write,
    symbol: &JavaSymbolIdentityOutput,
    source_set: &str,
) -> eyre::Result<()> {
    let fields = [
        resolution_kind(symbol.kind),
        &symbol.owner,
        &symbol.name,
        symbol.descriptor.as_deref().unwrap_or_default(),
        &symbol.qualified_name,
        source_set,
    ];
    if fields
        .iter()
        .any(|field| field.contains(['\t', '\r', '\n']))
    {
        eyre::bail!("dependency symbol resolution field contains a tab or newline");
    }
    write!(writer, "{}", fields.join("\t"))?;
    Ok(())
}

const fn resolution_kind(kind: JavaSymbolKind) -> &'static str {
    match kind {
        JavaSymbolKind::Class => "class",
        JavaSymbolKind::Interface => "interface",
        JavaSymbolKind::Enum => "enum",
        JavaSymbolKind::Record => "record",
        JavaSymbolKind::Annotation => "annotation",
        JavaSymbolKind::Field => "field",
        JavaSymbolKind::Method => "method",
        JavaSymbolKind::Constructor => "constructor",
        JavaSymbolKind::LocalVariable => "local-variable",
        JavaSymbolKind::Parameter => "parameter",
    }
}

fn parse_resolution_kind(value: &str) -> eyre::Result<JavaSymbolKind> {
    match value {
        "class" => Ok(JavaSymbolKind::Class),
        "interface" => Ok(JavaSymbolKind::Interface),
        "enum" => Ok(JavaSymbolKind::Enum),
        "record" => Ok(JavaSymbolKind::Record),
        "annotation" => Ok(JavaSymbolKind::Annotation),
        "field" => Ok(JavaSymbolKind::Field),
        "method" => Ok(JavaSymbolKind::Method),
        "constructor" => Ok(JavaSymbolKind::Constructor),
        "local-variable" => Ok(JavaSymbolKind::LocalVariable),
        "parameter" => Ok(JavaSymbolKind::Parameter),
        _ => eyre::bail!("unknown dependency symbol resolution kind `{value}`"),
    }
}

fn write_body(path: &Path, body: &DependencyJavaSymbolIndexBody) -> eyre::Result<()> {
    let file = std::fs::File::create(path)?;
    let mut writer = BufWriter::new(file);
    writeln!(writer, "{WORKER_OUTPUT_SCHEMA}")?;
    for definition in &body.definitions {
        write!(writer, "definition\t")?;
        write_symbol_route(
            &mut writer,
            &definition.symbol,
            &definition.identifier_span.source_set,
        )?;
        writer.write_all(b"\t")?;
        facet_json::to_writer_std(&mut writer, definition)?;
        writer.write_all(b"\n")?;
    }
    for usage in &body.usages {
        write!(writer, "usage\t")?;
        write_symbol_route(&mut writer, &usage.target, "")?;
        writer.write_all(b"\t")?;
        facet_json::to_writer_std(&mut writer, usage)?;
        writer.write_all(b"\n")?;
    }
    write_json_records(&mut writer, "diagnostic", &body.diagnostics)?;
    writer.flush()?;
    Ok(())
}

fn write_json_records<T: Facet<'static>>(
    writer: &mut impl Write,
    record_kind: &str,
    records: &[T],
) -> eyre::Result<()> {
    for record in records {
        write!(writer, "{record_kind}\t")?;
        facet_json::to_writer_std(&mut *writer, record)?;
        writer.write_all(b"\n")?;
    }
    Ok(())
}

const fn is_type(kind: JavaSymbolKind) -> bool {
    matches!(
        kind,
        JavaSymbolKind::Class
            | JavaSymbolKind::Interface
            | JavaSymbolKind::Enum
            | JavaSymbolKind::Record
            | JavaSymbolKind::Annotation
    )
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::java_analysis::JavaClasspathMode;
    use std::time::Instant;

    fn read_legacy_body(path: impl AsRef<Path>) -> eyre::Result<DependencyJavaSymbolIndexBody> {
        Ok(facet_json::from_str(&std::fs::read_to_string(path)?)?)
    }

    fn context() -> JavaAnalysisContextOutput {
        JavaAnalysisContextOutput {
            branch: "1.19.2".to_owned(),
            minecraft_version: "1.19.2".to_owned(),
            java_release: "17".to_owned(),
            jdk: "java-17".to_owned(),
            source_roots: Vec::new(),
            source_sets: Vec::new(),
            source_exclusions: Vec::new(),
            classpath_mode: JavaClasspathMode::Isolated,
            classpath_fingerprint: "blake3:isolated".to_owned(),
            parser_fingerprint: String::new(),
            index_fingerprint: String::new(),
        }
    }

    #[test]
    fn streamed_spool_pass_accepts_more_than_the_legacy_aggregate_limit() {
        const LEGACY_PASS_OUTPUT_LIMIT_BYTES: u64 = 512 * 1024 * 1024;
        let mut measurements = PassOutputMeasurements::default();

        // Five individually bounded spools reproduce the aggregate condition
        // that used to abort a real branch refresh, without materializing a
        // 640 MiB test fixture. Aggregate volume is telemetry; the per-worker
        // spool and process memory ceilings are the safety boundaries.
        for _ in 0..5 {
            measurements
                .observe_worker_output(WORKER_OUTPUT_LIMIT_BYTES)
                .expect("each bounded worker spool should be accepted");
        }

        assert_eq!(measurements.streamed_bytes, 5 * WORKER_OUTPUT_LIMIT_BYTES);
        assert!(measurements.streamed_bytes > LEGACY_PASS_OUTPUT_LIMIT_BYTES);
        assert_eq!(measurements.largest_worker_bytes, WORKER_OUTPUT_LIMIT_BYTES);
        let error = measurements
            .observe_worker_output(WORKER_OUTPUT_LIMIT_BYTES + 1)
            .expect_err("an individual oversized worker spool must still fail");
        assert!(error.to_string().contains("worker output exceeded"));
    }

    #[test]
    fn consumed_worker_spool_is_streamed_then_removed() {
        let temporary = tempfile::tempdir().expect("temporary spool directory");
        let worker_path = temporary.path().join("worker.ndjson");
        let records_path = temporary.path().join("records.ndjson");
        let diagnostic = JavaAnalysisDiagnosticOutput {
            code: "java.synthetic-spool-regression".to_owned(),
            severity: DiagnosticSeverity::Warning,
            message: "synthetic bounded spool".to_owned(),
            span: None,
        };
        std::fs::write(
            &worker_path,
            format!(
                "{WORKER_OUTPUT_SCHEMA}\ndiagnostic\t{}\n",
                facet_json::to_string(&diagnostic).expect("diagnostic JSON")
            ),
        )
        .expect("worker spool");
        let expected_bytes = std::fs::metadata(&worker_path)
            .expect("worker spool metadata")
            .len();
        let mut records =
            BufWriter::new(std::fs::File::create(&records_path).expect("records destination"));
        let mut counts = DependencySymbolIndexCounts {
            source_files: 1,
            definitions: 0,
            usages: 0,
            diagnostics: 0,
        };
        let mut diagnostics_seen = BTreeSet::new();
        let mut accumulator = PassAccumulator {
            records: &mut records,
            resolution: None,
            counts: &mut counts,
            diagnostics_seen: &mut diagnostics_seen,
            output: PassOutputMeasurements::default(),
        };

        assert_eq!(
            consume_worker_output(&worker_path, &mut accumulator)
                .expect("consume bounded worker spool"),
            expected_bytes
        );
        assert_eq!(accumulator.output.streamed_bytes, expected_bytes);
        assert_eq!(accumulator.output.successful_workers, 1);
        drop(accumulator);
        records.flush().expect("flush streamed records");

        assert!(!worker_path.exists(), "consumed spool must not accumulate");
        assert_eq!(counts.diagnostics, 1);
        assert!(
            std::fs::read_to_string(records_path)
                .expect("streamed records")
                .contains("diagnostic\t")
        );
    }

    #[test]
    fn private_worker_writes_only_requested_local_pass_records() {
        let temporary = tempfile::tempdir().expect("temporary worker directory");
        let source = temporary.path().join("A.java");
        let output = temporary.path().join("output.json");
        std::fs::write(
            &source,
            "package example; public class A { public int value; public void run() {} }",
        )
        .expect("write Java source");
        run_worker_request(WorkerRequest {
            schema: WORKER_SCHEMA.to_owned(),
            pass: WorkerPass::Types,
            context: context(),
            files: vec![WorkerSourceFile {
                path: source.display().to_string(),
                report_path: "dependency/example/A.java".to_owned(),
                source_set: "dependency:example".to_owned(),
            }],
            jdk_context_path: None,
            resolution_body_path: None,
            output_path: output.display().to_string(),
        })
        .expect("worker pass");

        let body = read_body(output).expect("typed worker output");
        assert_eq!(body.definitions.len(), 1);
        assert_eq!(body.definitions[0].symbol.qualified_name, "example.A");
        assert!(body.usages.is_empty());
        assert!(
            body.definitions.iter().all(|definition| {
                definition.identifier_span.path == "dependency/example/A.java"
            })
        );
    }

    #[test]
    fn cross_shard_type_pass_does_not_leak_transient_member_resolution_diagnostics() {
        let temporary = tempfile::tempdir().expect("temporary worker directory");
        let helper_source = temporary.path().join("SFMGameTestHelper.java");
        let test_source = temporary
            .path()
            .join("MoveWithoutTagConjunctionGameTest.java");
        let helper_output = temporary.path().join("helper-types.ndjson");
        let test_types_output = temporary.path().join("test-types.ndjson");
        let test_members_output = temporary.path().join("test-members.ndjson");
        let types_path = temporary.path().join("types.tsv");
        std::fs::write(
            &helper_source,
            "package ca.teamdman.sfm.gametest; public class SFMGameTestHelper {}",
        )
        .expect("write helper source");
        std::fs::write(
            &test_source,
            concat!(
                "package ca.teamdman.sfm.gametest.tests.migrated; ",
                "import ca.teamdman.sfm.gametest.SFMGameTestHelper; ",
                "public class MoveWithoutTagConjunctionGameTest { ",
                "public void run(SFMGameTestHelper helper) {} ",
                "}",
            ),
        )
        .expect("write test source");
        let worker_file = |path: &Path, report_path: &str| WorkerSourceFile {
            path: path.display().to_string(),
            report_path: report_path.to_owned(),
            source_set: "gametest".to_owned(),
        };

        run_worker_request(WorkerRequest {
            schema: WORKER_SCHEMA.to_owned(),
            pass: WorkerPass::Types,
            context: context(),
            files: vec![worker_file(
                &helper_source,
                "platform/minecraft/src/gametest/java/ca/teamdman/sfm/gametest/SFMGameTestHelper.java",
            )],
            jdk_context_path: None,
            resolution_body_path: None,
            output_path: helper_output.display().to_string(),
        })
        .expect("helper type shard");
        run_worker_request(WorkerRequest {
            schema: WORKER_SCHEMA.to_owned(),
            pass: WorkerPass::Types,
            context: context(),
            files: vec![worker_file(
                &test_source,
                "platform/minecraft/src/gametest/java/ca/teamdman/sfm/gametest/tests/migrated/MoveWithoutTagConjunctionGameTest.java",
            )],
            jdk_context_path: None,
            resolution_body_path: None,
            output_path: test_types_output.display().to_string(),
        })
        .expect("test type shard");

        let helper_types = read_body(&helper_output).expect("read helper type shard");
        let test_types = read_body(&test_types_output).expect("read test type shard");
        assert!(
            test_types
                .diagnostics
                .iter()
                .all(|diagnostic| diagnostic.code != "java.unresolved-type"),
            "the type-only pass must not publish member-resolution diagnostics: {:#?}",
            test_types.diagnostics
        );

        let mut type_definitions = helper_types.definitions;
        type_definitions.extend(test_types.definitions);
        write_resolution_body(&types_path, &type_definitions).expect("write combined type index");
        run_worker_request(WorkerRequest {
            schema: WORKER_SCHEMA.to_owned(),
            pass: WorkerPass::Members,
            context: context(),
            files: vec![worker_file(
                &test_source,
                "platform/minecraft/src/gametest/java/ca/teamdman/sfm/gametest/tests/migrated/MoveWithoutTagConjunctionGameTest.java",
            )],
            jdk_context_path: None,
            resolution_body_path: Some(types_path.display().to_string()),
            output_path: test_members_output.display().to_string(),
        })
        .expect("test member shard");

        let test_members = read_body(test_members_output).expect("read test member shard");
        let run = test_members
            .definitions
            .iter()
            .find(|definition| definition.symbol.name == "run")
            .expect("resolved run method");
        assert_eq!(
            run.symbol.descriptor.as_deref(),
            Some("(Lca/teamdman/sfm/gametest/SFMGameTestHelper;)V")
        );
        assert_eq!(run.confidence, ResolutionConfidence::Resolved);
        assert!(
            test_members
                .diagnostics
                .iter()
                .all(|diagnostic| diagnostic.code != "java.unresolved-type")
        );
    }

    #[test]
    fn member_pass_resolves_varargs_constructor_with_pinned_jdk_worker_context() {
        let temporary = tempfile::tempdir().expect("temporary worker directory");
        let jdk_tree = temporary.path().join("jdk");
        let java_lang = jdk_tree.join("java.base/java/lang");
        std::fs::create_dir_all(&java_lang).expect("JDK java.lang directory");
        std::fs::write(
            java_lang.join("Object.java"),
            "package java.lang; public class Object {}\n",
        )
        .expect("JDK Object source");
        std::fs::write(
            java_lang.join("String.java"),
            "package java.lang; public final class String extends Object {}\n",
        )
        .expect("JDK String source");
        let jdk_sources =
            JdkSourceDomainState::ready_from_tree("17", &jdk_tree).expect("JDK source domain");
        let jdk_context_path = temporary.path().join("jdk-context.json");
        assert!(
            jdk_sources
                .write_worker_context(&jdk_context_path)
                .expect("JDK worker context")
        );

        let mut worker_context = context();
        jdk_sources.apply_to_context(&mut worker_context);
        worker_context
            .source_sets
            .push(crate::java_analysis::JavaSourceSetOutput {
                id: "dependency:fixture".to_owned(),
                visible_source_sets: vec![
                    "dependency:fixture".to_owned(),
                    "jdk:java-17".to_owned(),
                ],
            });
        worker_context
            .source_sets
            .sort_by(|left, right| left.id.cmp(&right.id));

        let source_path = temporary.path().join("Widget.java");
        let source = concat!(
            "package p;\n",
            "public class Widget {\n",
            "    public Widget(String key) {}\n",
            "    public Widget(String key, Object... args) {}\n",
            "}\n",
        );
        std::fs::write(&source_path, source).expect("Widget source");
        let worker_file = WorkerSourceFile {
            path: source_path.display().to_string(),
            report_path: "dependency/fixture/p/Widget.java".to_owned(),
            source_set: "dependency:fixture".to_owned(),
        };
        let types_output = temporary.path().join("widget-types.ndjson");
        let members_output = temporary.path().join("widget-members.ndjson");
        let types_path = temporary.path().join("types.tsv");

        run_worker_request(WorkerRequest {
            schema: WORKER_SCHEMA.to_owned(),
            pass: WorkerPass::Types,
            context: worker_context.clone(),
            files: vec![worker_file.clone()],
            jdk_context_path: Some(jdk_context_path.display().to_string()),
            resolution_body_path: None,
            output_path: types_output.display().to_string(),
        })
        .expect("Widget type pass");
        let types = read_body(&types_output).expect("Widget type body");
        write_resolution_body(&types_path, &types.definitions).expect("Widget type resolution");

        run_worker_request(WorkerRequest {
            schema: WORKER_SCHEMA.to_owned(),
            pass: WorkerPass::Members,
            context: worker_context,
            files: vec![worker_file],
            jdk_context_path: Some(jdk_context_path.display().to_string()),
            resolution_body_path: Some(types_path.display().to_string()),
            output_path: members_output.display().to_string(),
        })
        .expect("Widget member pass");

        let members = read_body(members_output).expect("Widget member body");
        let descriptors = members
            .definitions
            .iter()
            .filter(|definition| definition.symbol.kind == JavaSymbolKind::Constructor)
            .map(|definition| {
                (
                    definition.symbol.descriptor.as_deref(),
                    definition.confidence,
                )
            })
            .collect::<Vec<_>>();
        assert_eq!(
            descriptors,
            [
                (
                    Some("(Ljava/lang/String;)V"),
                    ResolutionConfidence::Resolved
                ),
                (
                    Some("(Ljava/lang/String;[Ljava/lang/Object;)V"),
                    ResolutionConfidence::Resolved,
                ),
            ]
        );
    }

    #[test]
    fn types_pass_preserves_authoritative_parse_diagnostics() {
        let temporary = tempfile::tempdir().expect("temporary worker directory");
        let source = temporary.path().join("Broken.java");
        let output = temporary.path().join("types.ndjson");
        std::fs::write(
            &source,
            "package example; public class Broken { void run( {",
        )
        .expect("write malformed Java source");

        run_worker_request(WorkerRequest {
            schema: WORKER_SCHEMA.to_owned(),
            pass: WorkerPass::Types,
            context: context(),
            files: vec![WorkerSourceFile {
                path: source.display().to_string(),
                report_path: "source/example/Broken.java".to_owned(),
                source_set: "main".to_owned(),
            }],
            jdk_context_path: None,
            resolution_body_path: None,
            output_path: output.display().to_string(),
        })
        .expect("malformed type shard");

        let body = read_body(output).expect("read malformed type shard");
        assert!(
            body.diagnostics
                .iter()
                .any(|diagnostic| diagnostic.code == "java.parse-gap"),
            "type shards must preserve parser evidence: {:#?}",
            body.diagnostics
        );
        assert!(
            body.diagnostics
                .iter()
                .all(|diagnostic| diagnostic.code.starts_with("java.parse-"))
        );
    }

    #[test]
    fn worker_line_protocol_round_trips_shared_context_and_file_records() {
        let temporary = tempfile::tempdir().expect("worker protocol directory");
        let context_path = temporary.path().join("context.json");
        let request_path = temporary.path().join("request.tsv");
        std::fs::write(
            &context_path,
            facet_json::to_string(&context()).expect("context JSON"),
        )
        .expect("context file");
        write_worker_request(
            &request_path,
            &WorkerInvocation {
                pass: WorkerPass::Members,
                files: vec![WorkerSourceFile {
                    path: "C:\\sources\\A.java".to_owned(),
                    report_path: "dependency/example/A.java".to_owned(),
                    source_set: "dependency:example".to_owned(),
                }],
                jdk_context_path: Some("C:\\cache\\jdk-context.json".to_owned()),
                resolution_body_path: Some("C:\\cache\\types.tsv".to_owned()),
                output_path: "C:\\output\\members.ndjson".to_owned(),
            },
            &context_path,
        )
        .expect("write request");

        let request = read_worker_request(&request_path).expect("read request");
        assert_eq!(request.schema, WORKER_SCHEMA);
        assert_eq!(request.pass, WorkerPass::Members);
        assert_eq!(request.context, context());
        assert_eq!(request.files.len(), 1);
        assert_eq!(request.files[0].report_path, "dependency/example/A.java");
        assert_eq!(
            request.jdk_context_path.as_deref(),
            Some("C:\\cache\\jdk-context.json")
        );
        assert_eq!(
            request.resolution_body_path.as_deref(),
            Some("C:\\cache\\types.tsv")
        );
        assert_eq!(request.output_path, "C:\\output\\members.ndjson");
    }

    #[test]
    fn per_file_resolution_filter_is_a_conservative_identifier_superset() {
        let definition = |kind, owner: &str, name: &str| JavaDependencyResolutionDefinition {
            symbol: JavaSymbolIdentityOutput {
                kind,
                owner: owner.to_owned(),
                name: name.to_owned(),
                descriptor: None,
                qualified_name: format!("{owner}.{name}"),
            },
            source_set: "dependency:minecraft:main".to_owned(),
        };
        let definitions = vec![
            definition(JavaSymbolKind::Class, "example", "Editor"),
            definition(JavaSymbolKind::Class, "example", "Unused"),
            definition(JavaSymbolKind::Method, "example.Editor", "render"),
            definition(JavaSymbolKind::Method, "example.Unused", "render"),
            definition(JavaSymbolKind::Field, "example.Editor", "value"),
            definition(JavaSymbolKind::Constructor, "example.Editor", "<init>"),
        ];
        let filtered = relevant_resolution_definitions(
            &definitions,
            "import example.Editor; class Use { void go() { new Editor().render(); } }",
        );
        let symbols = filtered
            .iter()
            .map(|definition| (definition.symbol.kind, definition.symbol.name.as_str()))
            .collect::<BTreeSet<_>>();

        assert!(symbols.contains(&(JavaSymbolKind::Class, "Editor")));
        assert!(symbols.contains(&(JavaSymbolKind::Method, "render")));
        assert!(symbols.contains(&(JavaSymbolKind::Constructor, "<init>")));
        assert!(!symbols.contains(&(JavaSymbolKind::Class, "Unused")));
        assert!(!symbols.contains(&(JavaSymbolKind::Field, "value")));
        assert_eq!(
            filtered
                .iter()
                .filter(|definition| definition.symbol.name == "render")
                .count(),
            1
        );
    }

    #[test]
    fn member_access_tokens_require_dot_qualification_and_allow_whitespace() {
        let tokens = java_member_access_tokens(
            "Config . setup(); value = other\n  . value; import static example.Type.FIELD;",
        );

        assert!(tokens.contains("setup"));
        assert!(tokens.contains("value"));
        assert!(tokens.contains("FIELD"));
        assert!(!tokens.contains("Config"));
    }

    #[test]
    fn compact_resolution_reader_allocates_only_relevant_identifier_rows() {
        let temporary = tempfile::tempdir().expect("temporary resolution index");
        let path = temporary.path().join("resolution.tsv");
        std::fs::write(
            &path,
            concat!(
                "sfm.dependency-symbol-resolution-index/3\n",
                "class\texample\tEditor\t\texample.Editor\tdependency:example\n",
                "class\texample\tUnused\t\texample.Unused\tdependency:example\n",
                "method\texample.Editor\trender\t()V\texample.Editor.render\tdependency:example\n",
                "method\texample.Unused\trender\t()V\texample.Unused.render\tdependency:example\n",
                "field\texample.Editor\tvalue\tI\texample.Editor.value\tdependency:example\n",
                "constructor\texample.Editor\t<init>\t()V\texample.Editor.<init>\tdependency:example\n",
            ),
        )
        .expect("write compact resolution index");
        let identifiers = ["Editor", "render"]
            .into_iter()
            .map(str::to_owned)
            .collect::<BTreeSet<_>>();
        let member_accesses = ["render"]
            .into_iter()
            .map(str::to_owned)
            .collect::<BTreeSet<_>>();

        let definitions = read_resolution_body_relevant(&path, &identifiers, &member_accesses)
            .expect("read relevant rows");
        let symbols = definitions
            .iter()
            .map(|definition| (definition.symbol.kind, definition.symbol.name.as_str()))
            .collect::<BTreeSet<_>>();

        assert_eq!(symbols.len(), 3);
        assert!(symbols.contains(&(JavaSymbolKind::Class, "Editor")));
        assert!(symbols.contains(&(JavaSymbolKind::Method, "render")));
        assert!(symbols.contains(&(JavaSymbolKind::Constructor, "<init>")));
        assert!(!symbols.contains(&(JavaSymbolKind::Class, "Unused")));
        assert!(!symbols.contains(&(JavaSymbolKind::Field, "value")));
    }

    #[test]
    fn compact_resolution_index_round_trips_records_individually() {
        let temporary = tempfile::tempdir().expect("temporary resolution index");
        let path = temporary.path().join("resolution.jsonl");
        let definitions = (0..12_000)
            .map(|index| {
                let source_set = format!("dependency:fixture:{index}");
                let span = JavaSourceSpanOutput {
                    path: format!("dependency/fixture/Type{index}.java"),
                    source_set,
                    source_hash: format!("blake3:{index}"),
                    start_byte: 0,
                    end_byte: 1,
                    start_line: 1,
                    start_column: 1,
                    end_line: 1,
                    end_column: 2,
                };
                JavaSymbolDefinitionOutput {
                    symbol: JavaSymbolIdentityOutput {
                        kind: JavaSymbolKind::Class,
                        owner: "fixture".to_owned(),
                        name: format!("Type{index}"),
                        descriptor: None,
                        qualified_name: format!("fixture.Type{index}"),
                    },
                    identifier_span: span.clone(),
                    declaration_span: span,
                    confidence: ResolutionConfidence::Resolved,
                }
            })
            .collect::<Vec<_>>();

        write_resolution_body(&path, &definitions).expect("write compact resolution index");
        let round_trip = read_resolution_body(&path).expect("read compact resolution index");
        assert_eq!(round_trip.len(), definitions.len());
        assert_eq!(
            round_trip
                .iter()
                .find(|definition| definition.symbol.qualified_name == "fixture.Type17")
                .expect("Type17")
                .source_set,
            "dependency:fixture:17"
        );
    }

    #[test]
    #[ignore = "requires SFM_REAL_JAVA_SOURCE_ROOT"]
    fn real_dependency_source_shard_is_compact_and_finds_multiline_edit_box() {
        let root = PathBuf::from(
            std::env::var_os("SFM_REAL_JAVA_SOURCE_ROOT")
                .expect("SFM_REAL_JAVA_SOURCE_ROOT must name an acquired source tree"),
        );
        let mut paths = walkdir::WalkDir::new(&root)
            .follow_links(false)
            .into_iter()
            .filter_map(Result::ok)
            .filter(|entry| {
                entry.file_type().is_file()
                    && entry.path().extension().and_then(|value| value.to_str()) == Some("java")
            })
            .map(walkdir::DirEntry::into_path)
            .collect::<Vec<_>>();
        paths.sort();
        let multiline = paths
            .iter()
            .find(|path| {
                path.ends_with("net/minecraft/client/gui/components/MultiLineEditBox.java")
            })
            .cloned()
            .expect("MultiLineEditBox.java in acquired source tree");
        paths.retain(|path| path != &multiline);
        paths.truncate(WORKER_CHUNK_SIZE - 1);
        paths.push(multiline);
        paths.sort();
        assert_eq!(paths.len(), WORKER_CHUNK_SIZE);

        let temporary = tempfile::tempdir().expect("temporary worker output");
        let output = temporary.path().join("output.json");
        let started = Instant::now();
        run_worker_request(WorkerRequest {
            schema: WORKER_SCHEMA.to_owned(),
            pass: WorkerPass::Types,
            context: context(),
            files: paths
                .into_iter()
                .map(|path| WorkerSourceFile {
                    report_path: format!(
                        "dependency/minecraft/main/minecraft-pipeline/{}",
                        path.strip_prefix(&root)
                            .expect("source below root")
                            .to_string_lossy()
                            .replace('\\', "/")
                    ),
                    path: path.display().to_string(),
                    source_set: "dependency:minecraft:main".to_owned(),
                })
                .collect(),
            jdk_context_path: None,
            resolution_body_path: None,
            output_path: output.display().to_string(),
        })
        .expect("real source worker shard");
        let output_bytes = std::fs::metadata(&output)
            .expect("worker output metadata")
            .len();
        let body = read_body(output).expect("typed real worker output");
        eprintln!(
            "real dependency shard: files={} definitions={} diagnostics={} bytes={} elapsed_ms={}",
            WORKER_CHUNK_SIZE,
            body.definitions.len(),
            body.diagnostics.len(),
            output_bytes,
            started.elapsed().as_millis()
        );
        assert!(output_bytes <= WORKER_OUTPUT_LIMIT_BYTES);
        assert!(body.definitions.iter().any(|definition| {
            definition.symbol.qualified_name
                == "net.minecraft.client.gui.components.MultiLineEditBox"
        }));
    }

    #[test]
    #[ignore = "requires SFM_REAL_DEPENDENCY_TYPE_BODY and SFM_REAL_DEPENDENCY_WORKER_REQUEST"]
    fn real_dependency_member_shard_resolves_against_compact_type_index() {
        let type_body_path = PathBuf::from(
            std::env::var_os("SFM_REAL_DEPENDENCY_TYPE_BODY")
                .expect("SFM_REAL_DEPENDENCY_TYPE_BODY must name a retained type-pass body"),
        );
        let request_path = std::env::var_os("SFM_REAL_DEPENDENCY_WORKER_REQUEST")
            .expect("SFM_REAL_DEPENDENCY_WORKER_REQUEST must name a retained worker request");
        let type_body = if type_body_path.is_dir() {
            let mut outputs = std::fs::read_dir(&type_body_path)
                .expect("list retained type-pass directory")
                .filter_map(Result::ok)
                .map(|entry| entry.path())
                .filter(|path| {
                    path.file_name()
                        .and_then(|name| name.to_str())
                        .is_some_and(|name| {
                            name.starts_with("Types-") && name.ends_with("-output.json")
                        })
                })
                .collect::<Vec<_>>();
            outputs.sort();
            let mut combined =
                DependencyJavaSymbolIndexBody::new(Vec::new(), Vec::new(), Vec::new());
            for output in outputs {
                let mut body = read_legacy_body(output).expect("read retained type worker output");
                combined.definitions.append(&mut body.definitions);
                combined.diagnostics.append(&mut body.diagnostics);
            }
            DependencyJavaSymbolIndexBody::new(
                combined.definitions,
                Vec::new(),
                combined.diagnostics,
            )
        } else {
            read_legacy_body(type_body_path).expect("read retained type-pass body")
        };
        let request_text = std::fs::read_to_string(request_path).expect("read retained request");
        let mut request: WorkerRequest =
            facet_json::from_str(&request_text).expect("parse retained request");
        let temporary = tempfile::tempdir().expect("temporary member worker files");
        let resolution_path = temporary.path().join("types.tsv");
        let output_path = temporary.path().join("members.json");
        write_resolution_body(&resolution_path, &type_body.definitions)
            .expect("write compact type index");
        request.schema = WORKER_SCHEMA.to_owned();
        request.pass = WorkerPass::Members;
        request.resolution_body_path = Some(resolution_path.display().to_string());
        request.output_path = output_path.display().to_string();

        run_worker_request(request).expect("member shard should resolve under compact types");
        let body = read_body(output_path).expect("read member output");
        assert!(
            body.definitions
                .iter()
                .all(|definition| !is_type(definition.symbol.kind))
        );
    }
}
