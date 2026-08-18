use eyre::Context as _;
use eyre::bail;
use facet::Facet;
use sfm_propagate_changes::cancellation::CancellationToken;
use sfm_propagate_changes::cli::Cli;
use sfm_propagate_changes::cli::jar::BranchSelector;
use sfm_propagate_changes::cli::output::OutputFormat;
use sfm_propagate_changes::java_analysis::DefinitionAtPositionEngine;
use sfm_propagate_changes::java_analysis::DefinitionAtPositionEngineLimits;
use sfm_propagate_changes::java_analysis::DefinitionAtPositionOutcome;
use sfm_propagate_changes::java_analysis::DefinitionDocumentInput;
use sfm_propagate_changes::java_analysis::JavaClasspathMode;
use sfm_propagate_changes::java_analysis::JavaInteractionClassificationStatus;
use sfm_propagate_changes::java_analysis::JavaInteractionMapOutcome;
use sfm_propagate_changes::java_analysis::JavaInteractionMapRequest;
use sfm_propagate_changes::java_analysis::JavaSourceWorkspace;
use sfm_propagate_changes::java_analysis::SymbolCommandOutcome;
use sfm_propagate_changes::java_analysis::SymbolServerWorkspaceOutput;
use sfm_propagate_changes::java_analysis::blake3_content_hash;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::fs;
use std::io::Write as _;
use std::path::Path;
use std::path::PathBuf;
use std::process::Command;
use std::process::ExitStatus;

const EXPECTED_FILE: &str = "output-expected.json";
const ACTUAL_FILE: &str = "output-actual.json";
const COMMAND_FILE: &str = "command.ps1";
const REQUIRED_EXECUTABLE: &str = "sfm-propagate-changes.exe";

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
enum Quote {
    None,
    Single,
    Double,
}

#[derive(Facet, Clone, Debug, Eq, PartialEq)]
struct JavaInteractionMapScenarioRegression {
    id: String,
    covered: bool,
}

#[derive(Facet, Clone, Debug, Eq, PartialEq)]
struct JavaInteractionMapScenarioSummary {
    schema: String,
    outcome: JavaInteractionMapOutcome,
    semantic_kinds_complete: bool,
    relations_complete: bool,
    strict_non_whitespace_complete: bool,
    reciprocity_complete: bool,
    workspace_inventory_complete: bool,
    exact_regressions: Vec<JavaInteractionMapScenarioRegression>,
}

#[test]
fn java_analysis_scenarios() -> eyre::Result<()> {
    let scenarios = scenario_directories()?;
    assert!(
        !scenarios.is_empty(),
        "at least one Java analysis scenario is required"
    );
    let mut failures = Vec::new();
    for scenario in scenarios {
        if let Err(error) = run_scenario(&scenario) {
            failures.push(format!("{}:\n{error:#}", scenario.display()));
        }
    }
    if !failures.is_empty() {
        bail!(
            "Java analysis scenario failures:\n\n{}",
            failures.join("\n\n")
        );
    }
    Ok(())
}

#[test]
fn java_interaction_map_semantic_matrix_scenario() -> eyre::Result<()> {
    let scenario = Path::new(env!("CARGO_MANIFEST_DIR"))
        .join("tests")
        .join("java_analysis")
        .join("interaction_map_scenarios")
        .join("semantic_matrix");
    let source_root = scenario.join("source");
    let workspace = JavaSourceWorkspace::resolve(
        BranchSelector::from("1.19.2".to_owned()),
        std::slice::from_ref(&source_root),
        JavaClasspathMode::Isolated,
        &scenario,
    )?;
    let expected_workspace_files = workspace
        .files
        .iter()
        .map(|file| (file.report_path.clone(), file.source_set.clone()))
        .collect::<BTreeSet<_>>();
    let document = workspace
        .source_file("ca/teamdman/sfm/LexerAdapter.java")?
        .clone();
    let text = fs::read_to_string(&document.absolute_path)?;
    let served = SymbolServerWorkspaceOutput::from_workspace(&workspace, None, 0)?;
    let request = JavaInteractionMapRequest::new(
        1,
        1,
        served.request_workspace,
        DefinitionDocumentInput {
            address: format!(
                "workspace://{}/{}",
                document.root_id, document.root_relative_path
            ),
            root_id: document.root_id,
            root_relative_path: document.root_relative_path,
            report_path: document.report_path,
            source_set: document.source_set,
            text: text.clone(),
            content_hash: blake3_content_hash(&text),
            disk_content_hash: Some(blake3_content_hash(&text)),
        },
    );
    let engine = DefinitionAtPositionEngine::new(
        workspace,
        None,
        None,
        DefinitionAtPositionEngineLimits::default(),
    )?;
    let result = engine.analyze_interaction_map(&request, &CancellationToken::new())?;
    let kinds = result
        .regions
        .iter()
        .map(|region| region.semantic_kind.as_str())
        .collect::<BTreeSet<_>>();
    let semantic_kinds_complete = [
        "java-import",
        "java-annotation",
        "java-modifiers",
        "java-class-declaration",
        "java-field-declaration",
        "java-method-declaration",
        "java-parameter-declaration",
        "java-signature",
        "java-body",
        "java-delimiter",
        "java-statement",
        "java-punctuation",
        "java-operator",
        "java-literal",
        "java-qualified-name",
    ]
    .into_iter()
    .all(|required| kinds.contains(required));
    let relation_kinds = result
        .outlinks
        .iter()
        .map(|outlink| outlink.relation_kind.as_str())
        .collect::<BTreeSet<_>>();
    let relations_complete = [
        "definition",
        "reference",
        "matching-delimiter",
        "containing-region",
        "child-region",
        "signature",
        "body",
        "statement",
        "path",
    ]
    .into_iter()
    .all(|required| relation_kinds.contains(required));
    let classifications = result
        .classifications
        .iter()
        .map(|classification| (classification.region_id.as_str(), classification))
        .collect::<BTreeMap<_, _>>();
    let exceptions = result
        .exceptions
        .iter()
        .map(|exception| exception.region_id.as_str())
        .collect::<BTreeSet<_>>();
    let strict_non_whitespace_complete = text.char_indices().all(|(offset, character)| {
        character.is_whitespace()
            || result.regions.iter().any(|region| {
                region.start_byte() <= offset as u64
                    && (offset as u64) < region.end_byte()
                    && (classifications
                        .get(region.id.as_str())
                        .is_some_and(|classification| {
                            classification.status == JavaInteractionClassificationStatus::Actionable
                                && !classification.navigation_outlink_ids.is_empty()
                        })
                        || exceptions.contains(region.id.as_str()))
            })
    });
    let outlinks = result
        .outlinks
        .iter()
        .map(|outlink| (outlink.id.as_str(), outlink))
        .collect::<BTreeMap<_, _>>();
    let reciprocity_complete = !result.reciprocity.is_empty()
        && result.reciprocity.iter().all(|evidence| {
            let Some(definition) = outlinks.get(evidence.definition_outlink_id.as_str()) else {
                return false;
            };
            let Some(reference) = outlinks.get(evidence.reference_outlink_id.as_str()) else {
                return false;
            };
            definition.relation_kind == "definition"
                && reference.relation_kind == "reference"
                && definition.source_region_id == reference.destination_region_id
                && definition.destination_region_id == reference.source_region_id
        });
    let actual_workspace_files = result
        .files
        .iter()
        .filter(|file| file.resolver_id == "workspace")
        .map(|file| (file.report_path.clone(), file.source_set.clone()))
        .collect::<BTreeSet<_>>();
    let workspace_inventory_complete = expected_workspace_files == actual_workspace_files;
    let exact_regressions = [
        "LexerAdapter",
        "@Mod",
        "LocalizationEntry",
        "String",
        "java.io.Serializable",
        "java.io.Serial",
        "serialVersionUID",
    ]
    .into_iter()
    .map(|needle| {
        let covered = result.regions.iter().any(|region| {
            let Ok(start) = usize::try_from(region.start_byte()) else {
                return false;
            };
            let Ok(end) = usize::try_from(region.end_byte()) else {
                return false;
            };
            text.get(start..end).is_some_and(|value| value == needle)
                && result.outlinks.iter().any(|outlink| {
                    outlink.source_region_id == region.id && outlink.relation_kind == "definition"
                })
        });
        JavaInteractionMapScenarioRegression {
            id: needle.to_owned(),
            covered,
        }
    })
    .collect::<Vec<_>>();
    let actual = JavaInteractionMapScenarioSummary {
        schema: "sfm.java-interaction-map-scenario-summary/1".to_owned(),
        outcome: result.outcome,
        semantic_kinds_complete,
        relations_complete,
        strict_non_whitespace_complete,
        reciprocity_complete,
        workspace_inventory_complete,
        exact_regressions,
    };
    let expected: JavaInteractionMapScenarioSummary =
        facet_json::from_str(&fs::read_to_string(scenario.join(EXPECTED_FILE))?)?;
    assert_eq!(actual, expected);
    Ok(())
}

fn scenario_directories() -> eyre::Result<Vec<PathBuf>> {
    let scenarios_root = Path::new(env!("CARGO_MANIFEST_DIR"))
        .join("tests")
        .join("java_analysis")
        .join("scenarios");
    let mut scenarios = fs::read_dir(&scenarios_root)
        .wrap_err_with(|| format!("failed to read {}", scenarios_root.display()))?
        .filter_map(Result::ok)
        .filter(|entry| entry.file_type().is_ok_and(|kind| kind.is_dir()))
        .map(|entry| entry.path())
        .collect::<Vec<_>>();
    scenarios.sort();
    Ok(scenarios)
}

fn run_scenario(scenario: &Path) -> eyre::Result<()> {
    let command_path = scenario.join(COMMAND_FILE);
    let command = fs::read_to_string(&command_path)
        .wrap_err_with(|| format!("failed to read {}", command_path.display()))?;
    let argv = tokenize_restricted_powershell_command(&command)
        .wrap_err_with(|| format!("invalid command in {}", command_path.display()))?;
    verify_snapshot_git_policy(scenario)?;

    let before = scenario_files(scenario)?;
    let (rendered, actual_exit_code) = invoke_production_cli(&argv, scenario)?;
    let after = scenario_files(scenario)?;
    if before != after {
        let changed = before
            .keys()
            .chain(after.keys())
            .filter(|path| before.get(*path) != after.get(*path))
            .collect::<std::collections::BTreeSet<_>>();
        bail!(
            "production CLI mutated scenario inputs in {}: {}",
            scenario.display(),
            changed
                .into_iter()
                .map(|path| path.display().to_string())
                .collect::<Vec<_>>()
                .join(", ")
        );
    }
    let actual = canonicalize_json(&rendered)
        .wrap_err_with(|| format!("scenario output was not valid JSON: {}", scenario.display()))?;
    let actual_path = scenario.join(ACTUAL_FILE);
    write_atomically(&actual_path, actual.as_bytes())?;

    let expected_path = scenario.join(EXPECTED_FILE);
    let (expected, expected_exit_code) = match fs::read_to_string(&expected_path) {
        Ok(expected) => {
            let expected_exit_code = expected_public_status(&expected).wrap_err_with(|| {
                format!(
                    "expected snapshot does not encode a valid public status: {}",
                    expected_path.display()
                )
            })?;
            let expected = canonicalize_json(&expected).wrap_err_with(|| {
                format!(
                    "expected snapshot is not valid JSON: {}",
                    expected_path.display()
                )
            })?;
            (expected, expected_exit_code)
        }
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => {
            bail!(snapshot_acceptance_guidance(
                scenario,
                "expected snapshot is missing"
            ));
        }
        Err(error) => {
            return Err(error)
                .wrap_err_with(|| format!("failed to read {}", expected_path.display()));
        }
    };
    if actual_exit_code != expected_exit_code {
        bail!(snapshot_acceptance_guidance(
            scenario,
            &format!(
                "public exit status differs: expected {expected_exit_code} from the snapshot outcome, got {actual_exit_code}"
            )
        ));
    }
    if actual != expected {
        bail!(snapshot_acceptance_guidance(
            scenario,
            "snapshot differs from expected output"
        ));
    }
    Ok(())
}

fn scenario_files(scenario: &Path) -> eyre::Result<BTreeMap<PathBuf, Vec<u8>>> {
    fn visit(
        root: &Path,
        directory: &Path,
        files: &mut BTreeMap<PathBuf, Vec<u8>>,
    ) -> eyre::Result<()> {
        for entry in fs::read_dir(directory)
            .wrap_err_with(|| format!("failed to read {}", directory.display()))?
        {
            let entry = entry?;
            let path = entry.path();
            let file_type = entry.file_type()?;
            if file_type.is_dir() {
                visit(root, &path, files)?;
            } else if file_type.is_file() && entry.file_name() != ACTUAL_FILE {
                let relative = path
                    .strip_prefix(root)
                    .wrap_err("scenario entry escaped its scenario root")?
                    .to_path_buf();
                files.insert(relative, fs::read(&path)?);
            }
        }
        Ok(())
    }

    let mut files = BTreeMap::new();
    visit(scenario, scenario, &mut files)?;
    Ok(files)
}

fn invoke_production_cli(argv: &[String], invocation_dir: &Path) -> eyre::Result<(String, u8)> {
    let arguments = argv.iter().skip(1).map(String::as_str).collect::<Vec<_>>();
    let cli = figue::from_slice::<Cli>(&arguments)
        .into_result()
        .map_err(|error| eyre::eyre!("Figue rejected scenario arguments: {error:?}"))?
        .get_silent();
    if cli.global_args.output_format != Some(OutputFormat::Json) {
        bail!("scenario command must explicitly select --output-format json");
    }
    let output_format = cli.global_args.output_format;

    // Phase 0.3 integration assumption: this seam must be public because an
    // explicit Cargo integration test is a separate crate. It consumes `self`
    // and accepts `(CancellationToken, &Path)` in that order.
    let output = cli.invoke_in(CancellationToken::new(), invocation_dir)?;
    let exit_code = output.exit_code();
    let rendered = output
        .render(output_format, false)?
        .ok_or_else(|| eyre::eyre!("scenario command returned no typed output"))?;
    Ok((rendered, exit_code))
}

fn tokenize_restricted_powershell_command(command: &str) -> eyre::Result<Vec<String>> {
    let command = command.trim();
    if command.is_empty() {
        bail!("command is empty");
    }
    if command.contains(['\r', '\n']) {
        bail!("exactly one command line is allowed");
    }

    let mut quote = Quote::None;
    let mut token = String::new();
    let mut token_started = false;
    let mut tokens = Vec::new();
    let mut characters = command.chars().peekable();
    while let Some(character) = characters.next() {
        match quote {
            Quote::None => match character {
                character if character.is_whitespace() => {
                    if token_started {
                        tokens.push(std::mem::take(&mut token));
                        token_started = false;
                    }
                }
                '\'' => {
                    quote = Quote::Single;
                    token_started = true;
                }
                '"' => {
                    quote = Quote::Double;
                    token_started = true;
                }
                '$' | '`' => {
                    bail!("variables, interpolation, and command substitution are forbidden");
                }
                ';' | '|' | '&' | '<' | '>' | '#' | '{' | '}' | '(' | ')' | '@' => {
                    bail!("PowerShell metacharacter {character:?} is forbidden outside quotes");
                }
                character if character.is_control() => {
                    bail!("control characters are forbidden");
                }
                _ => {
                    token.push(character);
                    token_started = true;
                }
            },
            Quote::Single => {
                if character == '\'' {
                    if characters.peek() == Some(&'\'') {
                        characters.next();
                        token.push('\'');
                    } else {
                        quote = Quote::None;
                    }
                } else if character.is_control() {
                    bail!("control characters are forbidden");
                } else {
                    token.push(character);
                }
            }
            Quote::Double => match character {
                '"' => quote = Quote::None,
                '$' | '`' => bail!("double-quoted interpolation and escapes are forbidden"),
                character if character.is_control() => {
                    bail!("control characters are forbidden");
                }
                _ => token.push(character),
            },
        }
    }
    if quote != Quote::None {
        bail!("unterminated quoted argument");
    }
    if token_started {
        tokens.push(token);
    }
    if tokens.first().map(String::as_str) != Some(REQUIRED_EXECUTABLE) {
        bail!("command must begin with exact executable basename {REQUIRED_EXECUTABLE}");
    }
    if tokens.len() == 1 {
        bail!("command must include CLI arguments");
    }
    Ok(tokens)
}

fn verify_snapshot_git_policy(scenario: &Path) -> eyre::Result<()> {
    verify_snapshot_git_policy_paths(&scenario.join(ACTUAL_FILE), &scenario.join(EXPECTED_FILE))
}

fn verify_snapshot_git_policy_paths(actual_path: &Path, expected_path: &Path) -> eyre::Result<()> {
    let repository_root =
        git_repository_root(actual_path.parent().ok_or_else(|| {
            eyre::eyre!("actual snapshot has no parent: {}", actual_path.display())
        })?)?;
    let actual = repository_relative_path(&repository_root, actual_path)?;
    let expected = repository_relative_path(&repository_root, expected_path)?;
    if git_predicate(
        &repository_root,
        &["ls-files", "--error-unmatch", "--"],
        &actual,
    )? {
        bail!("refusing to overwrite tracked actual snapshot: {actual}");
    }
    if !git_predicate(
        &repository_root,
        &["check-ignore", "--quiet", "--no-index", "--"],
        &actual,
    )? {
        bail!("refusing to write non-ignored actual snapshot: {actual}");
    }
    if git_predicate(
        &repository_root,
        &["check-ignore", "--quiet", "--no-index", "--"],
        &expected,
    )? {
        bail!("expected snapshot must not be ignored: {expected}");
    }
    Ok(())
}

fn git_repository_root(cwd: &Path) -> eyre::Result<PathBuf> {
    let output = Command::new("git")
        .args(["rev-parse", "--show-toplevel"])
        .current_dir(cwd)
        .output()
        .wrap_err("failed to run git rev-parse without a shell")?;
    require_git_success(output.status, &output.stderr, "git rev-parse")?;
    let root = String::from_utf8(output.stdout).wrap_err("git root was not UTF-8")?;
    Ok(PathBuf::from(root.trim()))
}

fn repository_relative_path(repository_root: &Path, path: &Path) -> eyre::Result<String> {
    let relative = path.strip_prefix(repository_root).wrap_err_with(|| {
        format!(
            "snapshot path {} is outside repository {}",
            path.display(),
            repository_root.display()
        )
    })?;
    Ok(relative.to_string_lossy().replace('\\', "/"))
}

fn git_predicate(cwd: &Path, arguments: &[&str], path: &str) -> eyre::Result<bool> {
    let output = Command::new("git")
        .args(arguments)
        .arg(path)
        .current_dir(cwd)
        .output()
        .wrap_err("failed to query Git snapshot policy without a shell")?;
    if output.status.success() {
        return Ok(true);
    }
    if output.status.code() == Some(1) {
        return Ok(false);
    }
    require_git_success(output.status, &output.stderr, "Git snapshot policy query")?;
    unreachable!("non-success Git predicate status should have returned an error")
}

fn require_git_success(status: ExitStatus, stderr: &[u8], operation: &str) -> eyre::Result<()> {
    if status.success() {
        return Ok(());
    }
    bail!(
        "{operation} failed with {status}: {}",
        String::from_utf8_lossy(stderr).trim()
    )
}

fn write_atomically(path: &Path, contents: &[u8]) -> eyre::Result<()> {
    let parent = path
        .parent()
        .ok_or_else(|| eyre::eyre!("snapshot path has no parent: {}", path.display()))?;
    let mut temporary = tempfile::NamedTempFile::new_in(parent).wrap_err_with(|| {
        format!(
            "failed to create temporary snapshot beside {}",
            path.display()
        )
    })?;
    temporary
        .write_all(contents)
        .wrap_err_with(|| format!("failed to write temporary snapshot for {}", path.display()))?;
    temporary
        .flush()
        .wrap_err_with(|| format!("failed to flush temporary snapshot for {}", path.display()))?;
    temporary
        .as_file()
        .sync_all()
        .wrap_err_with(|| format!("failed to sync temporary snapshot for {}", path.display()))?;
    temporary
        .persist(path)
        .map_err(|error| error.error)
        .wrap_err_with(|| format!("failed to atomically replace {}", path.display()))?;
    Ok(())
}

fn snapshot_acceptance_guidance(scenario: &Path, reason: &str) -> String {
    format!(
        "{reason}. Actual output was retained at {}. Review it, then accept explicitly:\n  Set-Location '{}'\n  Copy-Item {} {}",
        scenario.join(ACTUAL_FILE).display(),
        scenario.display(),
        ACTUAL_FILE,
        EXPECTED_FILE
    )
}

// A small test-local JSON model keeps snapshot canonicalization schema-agnostic.
// This intentionally avoids adding a dependency (and therefore touching Cargo.lock).
#[derive(Clone, Debug, PartialEq, Eq)]
enum CanonicalJson {
    Null,
    Bool(bool),
    Number(String),
    String(String),
    Array(Vec<Self>),
    Object(BTreeMap<String, Self>),
}

fn canonicalize_json(input: &str) -> eyre::Result<String> {
    let mut value = parse_json(input)?;
    normalize_dependency_index_paths(&mut value)?;
    let mut output = String::new();
    value.write_pretty(&mut output, 0);
    output.push('\n');
    Ok(output)
}

fn normalize_dependency_index_paths(value: &mut CanonicalJson) -> eyre::Result<()> {
    match value {
        CanonicalJson::Array(values) => {
            for value in values {
                normalize_dependency_index_paths(value)?;
            }
        }
        CanonicalJson::Object(fields) => {
            let dependency_index_paths = match (fields.get("portable_path"), fields.get("path")) {
                (Some(CanonicalJson::String(portable_path)), Some(CanonicalJson::String(path))) => {
                    Some((portable_path.clone(), path.clone()))
                }
                _ => None,
            };
            if let Some((portable_path, path)) = dependency_index_paths
                && let Some(suffix) = portable_path.strip_prefix("$sfm-cache/")
                && suffix.starts_with("symbol-index/")
            {
                let normalized_path = path.replace('\\', "/");
                if normalized_path == portable_path || !normalized_path.ends_with(suffix) {
                    bail!(
                        "dependency-index path {path:?} is not a concrete cache path for portable identity {portable_path:?}"
                    );
                }
                fields.insert(
                    "path".to_owned(),
                    CanonicalJson::String(format!("$sfm-cache-local/{suffix}")),
                );
            }
            for value in fields.values_mut() {
                normalize_dependency_index_paths(value)?;
            }
        }
        CanonicalJson::Null
        | CanonicalJson::Bool(_)
        | CanonicalJson::Number(_)
        | CanonicalJson::String(_) => {}
    }
    Ok(())
}

fn parse_json(input: &str) -> eyre::Result<CanonicalJson> {
    let mut parser = JsonParser::new(input);
    let value = parser.parse_value()?;
    parser.skip_whitespace();
    if !parser.is_finished() {
        bail!(
            "trailing content after JSON value at byte {}",
            parser.position
        );
    }
    Ok(value)
}

fn expected_public_status(input: &str) -> eyre::Result<u8> {
    let CanonicalJson::Object(fields) = parse_json(input)? else {
        bail!("expected snapshot root must be a JSON object");
    };
    let Some(CanonicalJson::String(outcome)) = fields.get("outcome") else {
        bail!("expected snapshot must contain a string outcome field");
    };
    if matches!(
        outcome.as_str(),
        "stale-document" | "invalid-request" | "unavailable"
    ) {
        return Ok(DefinitionAtPositionOutcome::InvalidRequest.exit_code());
    }
    if matches!(
        fields.get("completeness"),
        Some(CanonicalJson::String(completeness)) if completeness == "incomplete"
    ) {
        return Ok(5);
    }
    let status = match outcome.as_str() {
        "success" => SymbolCommandOutcome::Success.exit_code(),
        "no-match" => SymbolCommandOutcome::NoMatch.exit_code(),
        "no-symbol" => DefinitionAtPositionOutcome::NoSymbol.exit_code(),
        "no-definition" => DefinitionAtPositionOutcome::NoDefinition.exit_code(),
        "ambiguous" => SymbolCommandOutcome::Ambiguous.exit_code(),
        "unsupported" => SymbolCommandOutcome::Unsupported.exit_code(),
        other => bail!("unknown public command outcome {other:?}"),
    };
    Ok(status)
}

impl CanonicalJson {
    fn write_pretty(&self, output: &mut String, depth: usize) {
        match self {
            Self::Null => output.push_str("null"),
            Self::Bool(value) => output.push_str(if *value { "true" } else { "false" }),
            Self::Number(value) => output.push_str(value),
            Self::String(value) => write_json_string(output, value),
            Self::Array(values) => {
                if values.is_empty() {
                    output.push_str("[]");
                    return;
                }
                output.push_str("[\n");
                for (index, value) in values.iter().enumerate() {
                    write_indent(output, depth + 1);
                    value.write_pretty(output, depth + 1);
                    if index + 1 != values.len() {
                        output.push(',');
                    }
                    output.push('\n');
                }
                write_indent(output, depth);
                output.push(']');
            }
            Self::Object(values) => {
                if values.is_empty() {
                    output.push_str("{}");
                    return;
                }
                output.push_str("{\n");
                for (index, (key, value)) in values.iter().enumerate() {
                    write_indent(output, depth + 1);
                    write_json_string(output, key);
                    output.push_str(": ");
                    value.write_pretty(output, depth + 1);
                    if index + 1 != values.len() {
                        output.push(',');
                    }
                    output.push('\n');
                }
                write_indent(output, depth);
                output.push('}');
            }
        }
    }
}

fn write_indent(output: &mut String, depth: usize) {
    for _ in 0..depth {
        output.push_str("  ");
    }
}

fn write_json_string(output: &mut String, value: &str) {
    output.push('"');
    for character in value.chars() {
        match character {
            '"' => output.push_str("\\\""),
            '\\' => output.push_str("\\\\"),
            '\u{08}' => output.push_str("\\b"),
            '\u{0C}' => output.push_str("\\f"),
            '\n' => output.push_str("\\n"),
            '\r' => output.push_str("\\r"),
            '\t' => output.push_str("\\t"),
            character if character <= '\u{1F}' => {
                use std::fmt::Write as _;
                write!(output, "\\u{:04x}", character as u32)
                    .expect("writing to a String cannot fail");
            }
            _ => output.push(character),
        }
    }
    output.push('"');
}

struct JsonParser<'a> {
    input: &'a [u8],
    position: usize,
}

impl<'a> JsonParser<'a> {
    fn new(input: &'a str) -> Self {
        Self {
            input: input.as_bytes(),
            position: 0,
        }
    }

    fn parse_value(&mut self) -> eyre::Result<CanonicalJson> {
        self.skip_whitespace();
        match self.peek() {
            Some(b'n') => {
                self.expect_bytes(b"null")?;
                Ok(CanonicalJson::Null)
            }
            Some(b't') => {
                self.expect_bytes(b"true")?;
                Ok(CanonicalJson::Bool(true))
            }
            Some(b'f') => {
                self.expect_bytes(b"false")?;
                Ok(CanonicalJson::Bool(false))
            }
            Some(b'"') => self.parse_string().map(CanonicalJson::String),
            Some(b'[') => self.parse_array(),
            Some(b'{') => self.parse_object(),
            Some(b'-' | b'0'..=b'9') => self.parse_number().map(CanonicalJson::Number),
            Some(byte) => bail!("unexpected JSON byte {byte:?} at byte {}", self.position),
            None => bail!("unexpected end of JSON input"),
        }
    }

    fn parse_array(&mut self) -> eyre::Result<CanonicalJson> {
        self.expect_byte(b'[')?;
        let mut values = Vec::new();
        self.skip_whitespace();
        if self.consume_byte(b']') {
            return Ok(CanonicalJson::Array(values));
        }
        loop {
            values.push(self.parse_value()?);
            self.skip_whitespace();
            if self.consume_byte(b']') {
                break;
            }
            self.expect_byte(b',')?;
        }
        Ok(CanonicalJson::Array(values))
    }

    fn parse_object(&mut self) -> eyre::Result<CanonicalJson> {
        self.expect_byte(b'{')?;
        let mut values = BTreeMap::new();
        self.skip_whitespace();
        if self.consume_byte(b'}') {
            return Ok(CanonicalJson::Object(values));
        }
        loop {
            self.skip_whitespace();
            let key = self.parse_string()?;
            self.skip_whitespace();
            self.expect_byte(b':')?;
            let value = self.parse_value()?;
            if values.insert(key.clone(), value).is_some() {
                bail!("duplicate JSON object key {key:?}");
            }
            self.skip_whitespace();
            if self.consume_byte(b'}') {
                break;
            }
            self.expect_byte(b',')?;
        }
        Ok(CanonicalJson::Object(values))
    }

    fn parse_string(&mut self) -> eyre::Result<String> {
        self.expect_byte(b'"')?;
        let mut output = String::new();
        let mut raw_start = self.position;
        loop {
            let Some(byte) = self.peek() else {
                bail!("unterminated JSON string");
            };
            match byte {
                b'"' => {
                    output.push_str(self.utf8_slice(raw_start, self.position)?);
                    self.position += 1;
                    return Ok(output);
                }
                b'\\' => {
                    output.push_str(self.utf8_slice(raw_start, self.position)?);
                    self.position += 1;
                    let escaped = self
                        .next_byte()
                        .ok_or_else(|| eyre::eyre!("unterminated JSON escape"))?;
                    match escaped {
                        b'"' => output.push('"'),
                        b'\\' => output.push('\\'),
                        b'/' => output.push('/'),
                        b'b' => output.push('\u{08}'),
                        b'f' => output.push('\u{0C}'),
                        b'n' => output.push('\n'),
                        b'r' => output.push('\r'),
                        b't' => output.push('\t'),
                        b'u' => output.push(self.parse_unicode_escape()?),
                        _ => bail!("invalid JSON escape at byte {}", self.position - 1),
                    }
                    raw_start = self.position;
                }
                0x00..=0x1F => bail!("unescaped control character in JSON string"),
                _ => self.position += 1,
            }
        }
    }

    fn parse_unicode_escape(&mut self) -> eyre::Result<char> {
        let first = self.parse_hex_quad()?;
        let scalar = if (0xD800..=0xDBFF).contains(&first) {
            self.expect_byte(b'\\')?;
            self.expect_byte(b'u')?;
            let second = self.parse_hex_quad()?;
            if !(0xDC00..=0xDFFF).contains(&second) {
                bail!("invalid low surrogate in JSON Unicode escape");
            }
            0x1_0000 + (((u32::from(first) - 0xD800) << 10) | (u32::from(second) - 0xDC00))
        } else {
            u32::from(first)
        };
        char::from_u32(scalar).ok_or_else(|| eyre::eyre!("invalid JSON Unicode scalar"))
    }

    fn parse_hex_quad(&mut self) -> eyre::Result<u16> {
        let mut value = 0_u16;
        for _ in 0..4 {
            let byte = self
                .next_byte()
                .ok_or_else(|| eyre::eyre!("truncated JSON Unicode escape"))?;
            let digit = match byte {
                b'0'..=b'9' => u16::from(byte - b'0'),
                b'a'..=b'f' => u16::from(byte - b'a' + 10),
                b'A'..=b'F' => u16::from(byte - b'A' + 10),
                _ => bail!("invalid hex digit in JSON Unicode escape"),
            };
            value = value * 16 + digit;
        }
        Ok(value)
    }

    fn parse_number(&mut self) -> eyre::Result<String> {
        let start = self.position;
        self.consume_byte(b'-');
        match self.peek() {
            Some(b'0') => self.position += 1,
            Some(b'1'..=b'9') => {
                self.position += 1;
                while matches!(self.peek(), Some(b'0'..=b'9')) {
                    self.position += 1;
                }
            }
            _ => bail!("invalid JSON number at byte {start}"),
        }
        if self.consume_byte(b'.') {
            self.require_digit("fraction")?;
            while matches!(self.peek(), Some(b'0'..=b'9')) {
                self.position += 1;
            }
        }
        if matches!(self.peek(), Some(b'e' | b'E')) {
            self.position += 1;
            if matches!(self.peek(), Some(b'+' | b'-')) {
                self.position += 1;
            }
            self.require_digit("exponent")?;
            while matches!(self.peek(), Some(b'0'..=b'9')) {
                self.position += 1;
            }
        }
        Ok(std::str::from_utf8(&self.input[start..self.position])?.to_owned())
    }

    fn require_digit(&mut self, part: &str) -> eyre::Result<()> {
        if !matches!(self.peek(), Some(b'0'..=b'9')) {
            bail!(
                "JSON number {part} requires a digit at byte {}",
                self.position
            );
        }
        self.position += 1;
        Ok(())
    }

    fn utf8_slice(&self, start: usize, end: usize) -> eyre::Result<&str> {
        std::str::from_utf8(&self.input[start..end]).wrap_err("JSON string was not UTF-8")
    }

    fn skip_whitespace(&mut self) {
        while matches!(self.peek(), Some(b' ' | b'\t' | b'\r' | b'\n')) {
            self.position += 1;
        }
    }

    fn expect_bytes(&mut self, expected: &[u8]) -> eyre::Result<()> {
        if self
            .input
            .get(self.position..self.position + expected.len())
            == Some(expected)
        {
            self.position += expected.len();
            Ok(())
        } else {
            bail!("expected JSON token at byte {}", self.position)
        }
    }

    fn expect_byte(&mut self, expected: u8) -> eyre::Result<()> {
        self.skip_whitespace();
        if self.consume_byte(expected) {
            Ok(())
        } else {
            bail!("expected JSON byte {expected:?} at byte {}", self.position)
        }
    }

    fn consume_byte(&mut self, expected: u8) -> bool {
        if self.peek() == Some(expected) {
            self.position += 1;
            true
        } else {
            false
        }
    }

    fn next_byte(&mut self) -> Option<u8> {
        let byte = self.peek()?;
        self.position += 1;
        Some(byte)
    }

    fn peek(&self) -> Option<u8> {
        self.input.get(self.position).copied()
    }

    fn is_finished(&self) -> bool {
        self.position == self.input.len()
    }
}

#[test]
fn restricted_tokenizer_preserves_quoted_descriptors_and_windows_paths() -> eyre::Result<()> {
    let tokens = tokenize_restricted_powershell_command(
        "sfm-propagate-changes.exe --output-format json symbol list-usages example.A 'run(Ljava/lang/String;)V' --branch 1.19.2 --source-root \"C:\\Java Source\" --classpath-mode isolated",
    )?;
    assert_eq!(tokens[0], REQUIRED_EXECUTABLE);
    assert!(tokens.contains(&"run(Ljava/lang/String;)V".to_owned()));
    assert!(tokens.contains(&"C:\\Java Source".to_owned()));
    Ok(())
}

#[test]
fn restricted_tokenizer_rejects_malformed_and_multiple_commands() {
    let invalid = [
        "",
        "other.exe symbol show-definition example.A --branch 1.19.2",
        "sfm-propagate-changes.exe",
        "sfm-propagate-changes.exe symbol show-definition 'example.A --branch 1.19.2",
        "sfm-propagate-changes.exe symbol show-definition $target --branch 1.19.2",
        "sfm-propagate-changes.exe symbol show-definition $(Get-Content target) --branch 1.19.2",
        "sfm-propagate-changes.exe symbol show-definition example.A --branch 1.19.2; whoami",
        "sfm-propagate-changes.exe symbol show-definition example.A --branch 1.19.2 | Out-File result",
        "sfm-propagate-changes.exe symbol show-definition example.A --branch 1.19.2 > result",
        "sfm-propagate-changes.exe symbol show-definition example.A --branch 1.19.2\nwhoami",
    ];
    for command in invalid {
        assert!(
            tokenize_restricted_powershell_command(command).is_err(),
            "unsafe command unexpectedly parsed: {command:?}"
        );
    }
}

#[test]
fn canonical_json_sorts_keys_and_normalizes_layout() -> eyre::Result<()> {
    let left = canonicalize_json(r#"{"z": [true, null], "a": {"two": 2, "one": 1}}"#)?;
    let right =
        canonicalize_json("{\n  \"a\": {\"one\": 1, \"two\": 2},\n  \"z\": [true, null]\n}")?;
    assert_eq!(left, right);
    assert!(left.starts_with("{\n  \"a\""));
    Ok(())
}

#[test]
fn canonical_json_preserves_dependency_index_path_contract_portably() -> eyre::Result<()> {
    let canonical = canonicalize_json(
        r#"{"dependency_index":{"portable_path":"$sfm-cache/symbol-index/v3/abc","path":"C:\\cache\\symbol-index\\v3\\abc"}}"#,
    )?;
    assert!(canonical.contains("\"portable_path\": \"$sfm-cache/symbol-index/v3/abc\""));
    assert!(canonical.contains("\"path\": \"$sfm-cache-local/symbol-index/v3/abc\""));

    let error = canonicalize_json(
        r#"{"dependency_index":{"portable_path":"$sfm-cache/symbol-index/v3/abc","path":"C:\\cache\\symbol-index\\v3\\wrong"}}"#,
    )
    .expect_err("mismatched concrete path must not be hidden by snapshot normalization");
    assert!(error.to_string().contains("is not a concrete cache path"));
    Ok(())
}

#[test]
fn expected_snapshot_outcome_determines_public_exit_status() -> eyre::Result<()> {
    for (outcome, expected_status) in [
        ("success", 0),
        ("no-match", 2),
        ("no-symbol", 2),
        ("no-definition", 2),
        ("ambiguous", 3),
        ("invalid-request", 4),
        ("unavailable", 4),
        ("unsupported", 4),
    ] {
        let snapshot = format!(r#"{{"outcome":"{outcome}"}}"#);
        assert_eq!(expected_public_status(&snapshot)?, expected_status);
    }
    assert_eq!(
        expected_public_status(r#"{"outcome":"success","completeness":"incomplete"}"#)?,
        5
    );
    let _ = expected_public_status(r#"{"schema":"missing-outcome"}"#).unwrap_err();
    let _ = expected_public_status(r#"{"outcome":"invented"}"#).unwrap_err();
    Ok(())
}

#[test]
fn acceptance_guidance_is_scenario_local_and_explicit() {
    let guidance = snapshot_acceptance_guidance(Path::new("scenario"), "different");
    assert!(guidance.contains("Set-Location 'scenario'"));
    assert!(guidance.contains("Copy-Item output-actual.json output-expected.json"));
}

#[test]
fn snapshot_policy_rejects_unsafe_actual_and_expected_paths() {
    let manifest = Path::new(env!("CARGO_MANIFEST_DIR"));
    let scenario = manifest
        .join("tests")
        .join("java_analysis")
        .join("scenarios")
        .join("symbol_rename_unsupported");
    let expected = scenario.join(EXPECTED_FILE);
    let ignored_actual = scenario.join(ACTUAL_FILE);

    let tracked = verify_snapshot_git_policy_paths(&manifest.join("Cargo.toml"), &expected)
        .expect_err("a tracked actual destination must be refused");
    assert!(tracked.to_string().contains("tracked actual snapshot"));

    let non_ignored =
        verify_snapshot_git_policy_paths(&scenario.join("unsafe-actual.json"), &expected)
            .expect_err("a non-ignored actual destination must be refused");
    assert!(
        non_ignored
            .to_string()
            .contains("non-ignored actual snapshot")
    );

    let ignored_expected = verify_snapshot_git_policy_paths(&ignored_actual, &ignored_actual)
        .expect_err("an ignored expected destination must be refused");
    assert!(
        ignored_expected
            .to_string()
            .contains("expected snapshot must not be ignored")
    );
}

#[test]
fn scenario_file_snapshot_ignores_actual_output_but_detects_input_mutation() -> eyre::Result<()> {
    let directory = tempfile::tempdir()?;
    let source = directory.path().join("Use.java");
    let actual = directory.path().join(ACTUAL_FILE);
    fs::write(&source, "class Use {}")?;
    fs::write(&actual, "first generated result")?;

    let before = scenario_files(directory.path())?;
    fs::write(&actual, "replacement generated result")?;
    assert_eq!(before, scenario_files(directory.path())?);

    fs::write(&source, "class Use { int changed; }")?;
    assert_ne!(before, scenario_files(directory.path())?);
    Ok(())
}
