use eyre::Context as _;
use eyre::bail;
use facet::Facet;
use sfm_propagate_changes::cancellation::CancellationToken;
use sfm_propagate_changes::cli::Cli;
use sfm_propagate_changes::cli::jar::BranchSelector;
use sfm_propagate_changes::cli::output::OutputFormat;
use sfm_propagate_changes::cli::symbol::scenario_dependency_index_inputs;
use sfm_propagate_changes::java_analysis::DEPENDENCY_JAVA_SYMBOL_INDEX_STREAM_SCHEMA;
use sfm_propagate_changes::java_analysis::DefinitionAtPositionEngine;
use sfm_propagate_changes::java_analysis::DefinitionAtPositionEngineLimits;
use sfm_propagate_changes::java_analysis::DefinitionAtPositionOutcome;
use sfm_propagate_changes::java_analysis::DefinitionDocumentInput;
use sfm_propagate_changes::java_analysis::DependencyJavaSymbolIndexStreamHeader;
use sfm_propagate_changes::java_analysis::DependencySymbolIndexCompleteness;
use sfm_propagate_changes::java_analysis::DependencySymbolIndexCounts;
use sfm_propagate_changes::java_analysis::DependencySymbolIndexCreationMetadata;
use sfm_propagate_changes::java_analysis::DependencySymbolIndexInputStatus;
use sfm_propagate_changes::java_analysis::DependencySymbolIndexManifest;
use sfm_propagate_changes::java_analysis::DependencySymbolIndexProbeStatus;
use sfm_propagate_changes::java_analysis::DependencySymbolIndexSourceInput;
use sfm_propagate_changes::java_analysis::DependencySymbolIndexStore;
use sfm_propagate_changes::java_analysis::JavaAnalysisContextOutput;
use sfm_propagate_changes::java_analysis::JavaAnalysisScenarioFixture;
use sfm_propagate_changes::java_analysis::JavaClasspathMode;
use sfm_propagate_changes::java_analysis::JavaInteractionClassificationStatus;
use sfm_propagate_changes::java_analysis::JavaInteractionMapOutcome;
use sfm_propagate_changes::java_analysis::JavaInteractionMapRequest;
use sfm_propagate_changes::java_analysis::JavaSourceSpanOutput;
use sfm_propagate_changes::java_analysis::JavaSourceWorkspace;
use sfm_propagate_changes::java_analysis::JavaSymbolDefinitionOutput;
use sfm_propagate_changes::java_analysis::JavaSymbolIdentityOutput;
use sfm_propagate_changes::java_analysis::JavaSymbolKind;
use sfm_propagate_changes::java_analysis::JavaSymbolUsageOutput;
use sfm_propagate_changes::java_analysis::JavaUsageKind;
use sfm_propagate_changes::java_analysis::ResolutionConfidence;
use sfm_propagate_changes::java_analysis::SymbolCommandOutcome;
use sfm_propagate_changes::java_analysis::SymbolServerWorkspaceOutput;
use sfm_propagate_changes::java_analysis::blake3_content_hash;
use sfm_propagate_changes::java_analysis::scan_java_analysis_scenario_index;
use sfm_propagate_changes::java_analysis::with_java_analysis_scenario_fixture;
use sfm_propagate_changes::paths::CacheHome;
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
fn java_analysis_scenario_cache_is_thread_scoped() -> eyre::Result<()> {
    let jdk_source_tree =
        Path::new(env!("CARGO_MANIFEST_DIR")).join("tests/java_analysis/jdk_sources");
    let first = tempfile::tempdir()?;
    let second = tempfile::tempdir()?;
    let first_path = first.path().to_path_buf();
    let second_path = second.path().to_path_buf();
    let barrier = std::sync::Arc::new(std::sync::Barrier::new(2));
    let threads = [first_path.clone(), second_path.clone()]
        .into_iter()
        .map(|cache_path| {
            let jdk_source_tree = jdk_source_tree.clone();
            let barrier = barrier.clone();
            std::thread::spawn(move || {
                with_java_analysis_scenario_fixture(
                    JavaAnalysisScenarioFixture {
                        jdk_source_tree,
                        cache_home: CacheHome(cache_path.clone()),
                        decompiler_runtime_identity: "sfm:scenario_decompiler_runtime@1".to_owned(),
                    },
                    || -> eyre::Result<()> {
                        barrier.wait();
                        eyre::ensure!(CacheHome::resolve()?.0 == cache_path);
                        let workspace = JavaSourceWorkspace::resolve(
                            BranchSelector::from("1.19.2".to_owned()),
                            &[Path::new(env!("CARGO_MANIFEST_DIR"))
                                .join("tests/java_analysis/scenarios/definition_at_position_jdk_object/source")],
                            JavaClasspathMode::Isolated,
                            Path::new(env!("CARGO_MANIFEST_DIR")),
                        )?;
                        eyre::ensure!(workspace.context.source_roots.iter().any(|root| {
                            root.path == "$scenario-jdk/java-17"
                        }));
                        Ok(())
                    },
                )
            })
        })
        .collect::<Vec<_>>();
    for thread in threads {
        thread.join().expect("scenario fixture thread")?;
    }
    eyre::ensure!(first_path != second_path);
    Ok(())
}

#[test]
fn partial_index_identity_is_stable_across_isolated_cache_locations() -> eyre::Result<()> {
    with_partial_index_context(|branch, context, first_cache| {
        let second_cache = tempfile::tempdir()?;
        let (first, _) = scenario_dependency_index_inputs(branch, context, first_cache.clone())?;
        let (second, _) = scenario_dependency_index_inputs(
            branch,
            context,
            CacheHome(second_cache.path().to_path_buf()),
        )?;
        first.validate()?;
        second.validate()?;
        eyre::ensure!(
            first == second,
            "cache location changed the Partial index identity"
        );
        Ok(())
    })
}

#[test]
fn partial_index_identity_changes_when_java_release_changes() -> eyre::Result<()> {
    with_partial_index_context(|branch, context, cache_home| {
        let (original, _) = scenario_dependency_index_inputs(branch, context, cache_home.clone())?;
        eyre::ensure!(
            context.java_release == "17",
            "the 1.19.2 fixture must use Java 17"
        );
        let mut changed = context.clone();
        changed.java_release = "21".to_owned();
        let (other_release, _) = scenario_dependency_index_inputs(branch, &changed, cache_home)?;
        original.validate()?;
        other_release.validate()?;
        eyre::ensure!(
            original.digest != other_release.digest,
            "changing the semantic Java release did not change the Partial index identity"
        );
        Ok(())
    })
}

#[test]
fn partial_index_fixture_does_not_acquire_sdk_in_empty_scoped_cache() -> eyre::Result<()> {
    with_partial_index_context(|branch, context, cache_home| {
        let sdk_cache = cache_home.0.join("minecraft-toolchain/jbrsdk");
        eyre::ensure!(!sdk_cache.exists(), "fixture must begin with no SDK cache");
        let (identity, _) = scenario_dependency_index_inputs(branch, context, cache_home)?;
        identity.validate()?;
        eyre::ensure!(
            !sdk_cache.exists(),
            "read-only scenario index preparation acquired a real SDK"
        );
        Ok(())
    })
}

#[test]
fn partial_index_fixture_runtime_identity_is_explicit_and_scope_restores() -> eyre::Result<()> {
    with_partial_index_context(|branch, context, cache_home| {
        let (original, _) = scenario_dependency_index_inputs(branch, context, cache_home.clone())?;
        let fixture = |runtime: &str| JavaAnalysisScenarioFixture {
            jdk_source_tree: Path::new(env!("CARGO_MANIFEST_DIR"))
                .join("tests/java_analysis/jdk_sources"),
            cache_home: cache_home.clone(),
            decompiler_runtime_identity: runtime.to_owned(),
        };
        let alternate = with_java_analysis_scenario_fixture(
            fixture("sfm:scenario_decompiler_runtime/alternate@1"),
            || scenario_dependency_index_inputs(branch, context, cache_home.clone()),
        )?;
        eyre::ensure!(
            original.digest != alternate.0.digest,
            "fixture runtime omitted from identity"
        );
        let refused = with_java_analysis_scenario_fixture(fixture(""), || {
            scenario_dependency_index_inputs(branch, context, cache_home.clone())
        });
        eyre::ensure!(refused.is_err(), "empty fixture identity was accepted");
        let (restored, _) = scenario_dependency_index_inputs(branch, context, cache_home.clone())?;
        eyre::ensure!(
            original == restored,
            "nested runtime fixture leaked into caller scope"
        );
        eyre::ensure!(
            !cache_home.0.join("minecraft-toolchain/jbrsdk").exists(),
            "identity-only fixture work created an SDK cache"
        );
        Ok(())
    })
}

fn with_partial_index_context<T>(
    test: impl FnOnce(&BranchSelector, &JavaAnalysisContextOutput, CacheHome) -> eyre::Result<T>,
) -> eyre::Result<T> {
    let scenario = Path::new(env!("CARGO_MANIFEST_DIR"))
        .join("tests/java_analysis/scenarios/usage_at_position_partial_index");
    let branch = BranchSelector::from("1.19.2".to_owned());
    let cache_directory = tempfile::tempdir()?;
    let cache_home = CacheHome(cache_directory.path().to_path_buf());
    let fixture = JavaAnalysisScenarioFixture {
        jdk_source_tree: Path::new(env!("CARGO_MANIFEST_DIR"))
            .join("tests/java_analysis/jdk_sources"),
        cache_home: cache_home.clone(),
        decompiler_runtime_identity: "sfm:scenario_decompiler_runtime@1".to_owned(),
    };
    with_java_analysis_scenario_fixture(fixture, || {
        let workspace = JavaSourceWorkspace::resolve(
            branch.clone(),
            &[scenario.join("source")],
            JavaClasspathMode::Branch,
            &scenario,
        )?;
        test(&branch, &workspace.context, cache_home)
    })
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
    let cache_directory = tempfile::tempdir()?;
    let cache_home = CacheHome(cache_directory.path().to_path_buf());
    let fixture = JavaAnalysisScenarioFixture {
        jdk_source_tree: Path::new(env!("CARGO_MANIFEST_DIR"))
            .join("tests/java_analysis/jdk_sources"),
        cache_home: cache_home.clone(),
        decompiler_runtime_identity: "sfm:scenario_decompiler_runtime@1".to_owned(),
    };
    let (rendered, actual_exit_code) = with_java_analysis_scenario_fixture(fixture, || {
        let partial_identity = if is_partial_index_scenario(scenario) {
            Some(publish_partial_index_fixture(scenario, &cache_home)?)
        } else {
            None
        };
        let (rendered, status) = invoke_production_cli(&argv, scenario)?;
        assert_scenario_inputs(
            scenario,
            &rendered,
            &cache_home,
            partial_identity.as_deref(),
        )?;
        Ok::<_, eyre::Report>((rendered, status))
    })?;
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
    let actual = canonicalize_scenario_json(&rendered, scenario)
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

fn is_partial_index_scenario(scenario: &Path) -> bool {
    scenario
        .file_name()
        .is_some_and(|name| name == "usage_at_position_partial_index")
}

fn publish_partial_index_fixture(scenario: &Path, cache_home: &CacheHome) -> eyre::Result<String> {
    let branch = BranchSelector::from("1.19.2".to_owned());
    let workspace = JavaSourceWorkspace::resolve(
        branch.clone(),
        &[scenario.join("source")],
        JavaClasspathMode::Branch,
        scenario,
    )?;
    let (identity, mut source_inputs) =
        scenario_dependency_index_inputs(&branch, &workspace.context, cache_home.clone())?;
    let first = source_inputs
        .first_mut()
        .ok_or_else(|| eyre::eyre!("partial-index scenario has no dependency source inputs"))?;
    first.status = DependencySymbolIndexInputStatus::Unavailable;
    source_inputs.push(DependencySymbolIndexSourceInput {
        dependency: "scenario".to_owned(),
        component: "partial".to_owned(),
        provider: "fixture".to_owned(),
        portable_origin: "dependency/scenario/partial/fixture".to_owned(),
        fingerprint: "fixture:Indexed.java".to_owned(),
        status: DependencySymbolIndexInputStatus::Ready,
    });

    let indexed_source = fs::read_to_string(scenario.join("Indexed.java"))?;
    let first_name = indexed_source
        .find("Indexed")
        .ok_or_else(|| eyre::eyre!("dependency fixture lacks its type declaration"))?;
    let second_name = indexed_source
        .rfind("Indexed")
        .ok_or_else(|| eyre::eyre!("dependency fixture lacks its type usage"))?;
    let declaration_start = indexed_source
        .find("public final class")
        .ok_or_else(|| eyre::eyre!("dependency fixture lacks its class declaration"))?;
    let declaration_end = indexed_source
        .rfind('}')
        .ok_or_else(|| eyre::eyre!("dependency fixture lacks its closing brace"))?
        + 1;
    eyre::ensure!(first_name != second_name);
    let span = |start: usize, end: usize| -> JavaSourceSpanOutput {
        let position = |offset: usize| {
            let prefix = &indexed_source[..offset];
            (
                1 + prefix.bytes().filter(|byte| *byte == b'\n').count() as u64,
                1 + prefix
                    .rsplit('\n')
                    .next()
                    .unwrap_or_default()
                    .chars()
                    .count() as u64,
            )
        };
        let (start_line, start_column) = position(start);
        let (end_line, end_column) = position(end);
        JavaSourceSpanOutput {
            path: "dependency/scenario/Indexed.java".to_owned(),
            source_set: "dependency:scenario:partial".to_owned(),
            source_hash: blake3_content_hash(&indexed_source),
            start_byte: start as u64,
            end_byte: end as u64,
            start_line,
            start_column,
            end_line,
            end_column,
        }
    };
    let symbol = JavaSymbolIdentityOutput {
        kind: JavaSymbolKind::Class,
        owner: "fixture".to_owned(),
        name: "Indexed".to_owned(),
        descriptor: None,
        qualified_name: "fixture.Indexed".to_owned(),
    };
    let definition = JavaSymbolDefinitionOutput {
        symbol: symbol.clone(),
        identifier_span: span(first_name, first_name + "Indexed".len()),
        declaration_span: span(declaration_start, declaration_end),
        confidence: ResolutionConfidence::Resolved,
    };
    let usage = JavaSymbolUsageOutput {
        target: symbol,
        kind: JavaUsageKind::TypeReference,
        span: span(second_name, second_name + "Indexed".len()),
        confidence: ResolutionConfidence::Resolved,
    };

    let mut payload = tempfile::NamedTempFile::new_in(&cache_home.0)?;
    writeln!(
        payload,
        "{}",
        facet_json::to_string(&DependencyJavaSymbolIndexStreamHeader::new(
            identity.clone()
        ))?
    )?;
    writeln!(
        payload,
        "definition\tclass\tfixture\tIndexed\t\tfixture.Indexed\tdependency:scenario:partial\t{}",
        facet_json::to_string(&definition)?
    )?;
    writeln!(
        payload,
        "usage\tclass\tfixture\tIndexed\t\tfixture.Indexed\t\t{}",
        facet_json::to_string(&usage)?
    )?;
    payload.flush()?;
    let manifest = DependencySymbolIndexManifest::for_prepared_payload(
        identity.clone(),
        DependencySymbolIndexCreationMetadata {
            created_at_utc: "2026-09-23T00:00:00Z".to_owned(),
            producer: "java-analysis-scenario-fixture".to_owned(),
            refresh_duration_ms: 0,
        },
        source_inputs,
        DependencySymbolIndexCounts {
            source_files: 1,
            definitions: 1,
            usages: 1,
            diagnostics: 0,
        },
        DependencySymbolIndexCompleteness::Partial,
        DEPENDENCY_JAVA_SYMBOL_INDEX_STREAM_SCHEMA,
        payload.path(),
    )?;
    let store = DependencySymbolIndexStore::new(cache_home.clone());
    store.publish_prepared(&manifest, payload.path())?;
    let probe = store.probe(&identity)?;
    eyre::ensure!(
        probe.status == DependencySymbolIndexProbeStatus::Partial && probe.loadable,
        "partial-index fixture did not publish a loadable Partial entry"
    );
    let (loaded_manifest, loaded_payload) =
        store.validated_payload(&identity, DEPENDENCY_JAVA_SYMBOL_INDEX_STREAM_SCHEMA)?;
    let decoded =
        scan_java_analysis_scenario_index(&loaded_payload, &identity, &loaded_manifest.counts)?;
    eyre::ensure!(
        decoded.definitions.len() == 1
            && decoded.definitions[0].symbol.qualified_name == "fixture.Indexed"
            && decoded.usages.len() == 1
            && decoded.usages[0].target.qualified_name == "fixture.Indexed"
            && decoded.usages[0].span.source_hash == blake3_content_hash(&indexed_source),
        "Partial index definition and usage did not decode from the published stream"
    );
    Ok(identity.digest)
}

fn assert_scenario_inputs(
    scenario: &Path,
    rendered: &str,
    cache_home: &CacheHome,
    partial_identity: Option<&str>,
) -> eyre::Result<()> {
    let CanonicalJson::Object(output) = parse_json(rendered)? else {
        bail!("scenario CLI output is not an object");
    };
    if let Some(CanonicalJson::Object(context)) = output.get("context") {
        let Some(CanonicalJson::Array(roots)) = context.get("source_roots") else {
            bail!("scenario context has no source roots");
        };
        eyre::ensure!(
            roots.iter().any(|root| {
                matches!(root, CanonicalJson::Object(fields)
                    if fields.get("kind") == Some(&CanonicalJson::String("jdk".to_owned()))
                        && fields.get("path") == Some(&CanonicalJson::String("$scenario-jdk/java-17".to_owned())))
            }),
            "scenario context did not use the fixed JDK corpus"
        );
    }
    if let Some(CanonicalJson::Object(index)) = output.get("dependency_index") {
        let Some(CanonicalJson::String(path)) = index.get("path") else {
            bail!("scenario dependency index has no concrete path");
        };
        eyre::ensure!(
            Path::new(path).starts_with(&cache_home.0),
            "scenario dependency index escaped its isolated cache"
        );
    }
    if let Some(identity) = partial_identity {
        let Some(CanonicalJson::Object(index)) = output.get("dependency_index") else {
            bail!("partial-index scenario returned no dependency index");
        };
        eyre::ensure!(
            index.get("status") == Some(&CanonicalJson::String("partial".to_owned()))
                && index.get("expected_identity")
                    == Some(&CanonicalJson::String(identity.to_owned()))
                && index.get("reason")
                    == Some(&CanonicalJson::String(
                        "manifest and payload are valid but dependency coverage is partial"
                            .to_owned(),
                    )),
            "CLI did not load the published Partial index identity"
        );
        let Some(CanonicalJson::Object(context)) = output.get("context") else {
            bail!("partial-index scenario has no context");
        };
        let Some(CanonicalJson::Array(source_sets)) = context.get("source_sets") else {
            bail!("partial-index scenario has no source sets");
        };
        eyre::ensure!(
            source_sets.iter().any(|source_set| {
                matches!(source_set, CanonicalJson::Object(fields)
                    if fields.get("id") == Some(&CanonicalJson::String("dependency:scenario:partial".to_owned())))
            }),
            "CLI did not use the Partial payload's dependency definition"
        );
        let Some(CanonicalJson::Array(actions)) = output.get("recovery_actions") else {
            bail!("partial-index scenario returned no recovery actions");
        };
        eyre::ensure!(
            actions.iter().any(|action| {
                matches!(action, CanonicalJson::Object(fields)
                    if fields.get("kind") == Some(&CanonicalJson::String("refresh-dependency-index".to_owned()))
                        && fields.get("command") == Some(&CanonicalJson::String("sfm-propagate-changes.exe symbol index refresh --branch 1.19.2".to_owned())))
            }),
            "partial-index scenario omitted its refresh recovery action"
        );
    }
    assert_fixed_jdk_source_span(scenario, &output)
}

fn assert_fixed_jdk_source_span(
    scenario: &Path,
    output: &BTreeMap<String, CanonicalJson>,
) -> eyre::Result<()> {
    let Some(name) = scenario.file_name().and_then(|name| name.to_str()) else {
        return Ok(());
    };
    let class_name = match name {
        "definition_at_position_jdk_object" => "Object",
        "definition_at_position_jdk_string" => "String",
        "definition_at_position_jdk_string_builder" => "StringBuilder",
        _ => return Ok(()),
    };
    let Some(CanonicalJson::Array(definitions)) = output.get("definitions") else {
        bail!("JDK scenario returned no definitions");
    };
    let Some(CanonicalJson::Object(definition)) = definitions.first() else {
        bail!("JDK scenario returned no definition");
    };
    let Some(CanonicalJson::Object(declaration)) = definition.get("declaration_span") else {
        bail!("JDK definition has no declaration span");
    };
    let Some(CanonicalJson::Object(identifier)) = definition.get("identifier_span") else {
        bail!("JDK definition has no identifier span");
    };
    let path = Path::new(env!("CARGO_MANIFEST_DIR"))
        .join("tests/java_analysis/jdk_sources/java.base/java/lang")
        .join(format!("{class_name}.java"));
    let source = fs::read_to_string(path)?;
    let byte = |span: &BTreeMap<String, CanonicalJson>, key: &str| -> eyre::Result<usize> {
        let Some(CanonicalJson::Number(value)) = span.get(key) else {
            bail!("JDK span has no {key}");
        };
        Ok(value.parse()?)
    };
    eyre::ensure!(
        declaration.get("source_hash")
            == Some(&CanonicalJson::String(blake3_content_hash(&source)))
            && declaration.get("resolver_id")
                == Some(&CanonicalJson::String("jdk-source".to_owned()))
            && source
                .get(byte(declaration, "start_byte")?..byte(declaration, "end_byte")?)
                .is_some_and(|text| text.contains(class_name))
            && source.get(byte(identifier, "start_byte")?..byte(identifier, "end_byte")?)
                == Some(class_name),
        "JDK definition span does not address the fixed source bytes"
    );
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
    canonicalize_value(&mut value)
}

fn canonicalize_scenario_json(input: &str, scenario: &Path) -> eyre::Result<String> {
    let mut value = parse_json(input)?;
    if is_partial_index_scenario(scenario) {
        // The Partial scenario checks an authenticated index and its refresh
        // recovery action above. Source-acquisition hints come from the local
        // preflight, so their provider list can vary and their command embeds
        // the absolute Cargo test executable. They are outside this fixture's
        // index payload; leave identity, status, source spans and refresh intact.
        if let CanonicalJson::Object(output) = &mut value {
            if let Some(CanonicalJson::Object(index)) = output.get_mut("dependency_index") {
                index.insert(
                    "acquisition_commands".to_owned(),
                    CanonicalJson::Array(Vec::new()),
                );
            }
            if let Some(CanonicalJson::Array(actions)) = output.get_mut("recovery_actions") {
                actions.retain(|action| {
                    !matches!(action, CanonicalJson::Object(fields)
                        if fields.get("kind") == Some(&CanonicalJson::String("acquire-dependency-sources".to_owned())))
                });
            }
        }
    }
    canonicalize_value(&mut value)
}

fn canonicalize_value(value: &mut CanonicalJson) -> eyre::Result<String> {
    normalize_dependency_index_paths(value)?;
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
