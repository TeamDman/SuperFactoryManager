//! Post-promotion source contracts for the eight packet-runtime closure helpers.
//!
//! Every read follows actual core membership selection. Historical objects are
//! bounded test witnesses, never production inputs. These tests assert source
//! fidelity and authoring boundaries, not Java compilation or game behaviour.

#![cfg(test)]

use super::candidate_lock::checked_file;
use super::context::ProjectionContext;
use super::core_inputs::CoreProjectInputs;
use super::core_inputs::discover_core_source_files;
use super::core_inputs::select_core_inputs;
use super::core_slice_test_support::CoreTestFixture;
use super::core_slice_test_support::read_bounded;
use super::core_slice_test_support::read_git_blobs;
use super::provenance::sha256;
use super::render_java_source;
use eyre::Result;
use eyre::ensure;
use facet::Facet;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::fs;
use std::path::Path;

const CORE_PREFIX: &str = "platform/minecraft/core-liquid-template/";
const STAGE_PREFIX: &str =
    "platform/cli/sfm-propagate-changes/target/core-runtime-closure-stage-v1/";
const LEDGER: &str = "docs/tasks/sfm-core-runtime-closure-slice.json";
const PC: &str = "packet_computation";
const VALUES: &str = "packet_values";
const CLEANUP: &str = "runtime_resource_cleanup";
const TARGETS: [&str; 10] = [
    "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
    "26.1.2",
];
const COMMITS: [(&str, &str); 20] = [
    ("dev/1.19.2", "f3ff2f6425434f36c7c680fa909c977b158e1860"),
    ("dev/1.19.4", "2e3b561c15d663fb89fd353ccc2af67eeb0c2053"),
    ("dev/1.20", "6bf4845761d06560fc5e0e36018b5d583589004d"),
    ("dev/1.20.1", "faa040ce14dd825f2dd9716ea59508bf46278c06"),
    ("dev/1.20.2", "a829fb4db2ae06eddd2defb22e33f0b45a4b3ff9"),
    ("dev/1.20.3", "704aa69edad5376d8d6cfb0b0ef7845af077e647"),
    ("dev/1.20.4", "11d3ed07d654ff801329f17cf1eb81c2b347eecd"),
    ("dev/1.21.0", "43068d610b1c053c6569be486439769eec2ae9ef"),
    ("dev/1.21.1", "7524ab5512878b773e212600c9578b2bc4db4717"),
    ("dev/26.1.2", "6bd27f03863ddd041344cbc3da51b01a7b3c3e1f"),
    ("release/1.19.2", "31135b8e86801b862d5cb2283c7c5878b7cc5bb4"),
    ("release/1.19.4", "23785b63e3e1fe35e5be6a6d89b6638ee0ff0daa"),
    ("release/1.20", "3df18123a19535fd0e5d1dc81aa302105c3fd2f6"),
    ("release/1.20.1", "bb5babf12f467235b3a44ad5098666ee3ed171ec"),
    ("release/1.20.2", "cfbbafaeda4a006ae32743a92de330711983056b"),
    ("release/1.20.3", "1b7f9605da0ef13c7601daf3786545868dfc3c78"),
    ("release/1.20.4", "a637581b5e1078d7cc0ca68333add568e3e387ff"),
    ("release/1.21.0", "6bfab8a21e7a5bbeb0bb136bf4e5b26f6ce03e25"),
    ("release/1.21.1", "f5366c79c823ff52712130e69dd9c8166c70bd14"),
    ("release/26.1.2", "fe32b29453b13b4f3050ad441677c7eb79e80814"),
];

#[derive(Clone, Copy)]
struct Golden {
    path: &'static str,
    oid: &'static str,
    digest: &'static str,
    bytes: usize,
}
const GOLDENS: [Golden; 8] = [
    Golden {
        path: "src/main/java/ca/teamdman/sfm/common/program/ProgramExecutionScope.java",
        oid: "f6163559ef772519d0bff34af3a2f467c457589c",
        digest: "sha256:f2d79bb075334b2ea05ee1a463ed60a4d5da215c4e4e9fbbf30eaa1a9e2c0627",
        bytes: 2919,
    },
    Golden {
        path: "src/main/java/ca/teamdman/sfm/common/program/ProgramVariableEnvironment.java",
        oid: "6c84a6362589f95557c7980e1fbf06a2e07dc9a5",
        digest: "sha256:a71fe83d6fd726dcb5b588fe5263cff6ba517650cca3d7a0a19d99239579d9f8",
        bytes: 2454,
    },
    Golden {
        path: "src/main/java/ca/teamdman/sfm/common/program/ProgramEphemeralResourceOwner.java",
        oid: "889c70540db3c9ed693f7772904590835521624b",
        digest: "sha256:c482299f4e18f99807211956d515d16ab994e70cd24f2bcd4ae6057bf61af5a0",
        bytes: 2083,
    },
    Golden {
        path: "src/main/java/ca/teamdman/sfm/common/program/ProgramInputSource.java",
        oid: "2c253fccf622eb0221107a571fabb33c9e26af69",
        digest: "sha256:fed857fa900948a6cdff775174ca1bc1bc92f0fb6fdf49ed846926982da9951f",
        bytes: 1019,
    },
    Golden {
        path: "src/main/java/ca/teamdman/sfm/common/program/ProgramInputForgetRequest.java",
        oid: "8e446e2a7433cc7e4d76a8916b8bde6088787c21",
        digest: "sha256:c44eff67c2a43c33660675023263872f768eece3b815683bc324298fea01557e",
        bytes: 829,
    },
    Golden {
        path: "src/main/java/ca/teamdman/sfml/ast/ProgramInputSelection.java",
        oid: "5051cf990e0035ba62beb7be379808a58383eede",
        digest: "sha256:62ac87419d943234452c20b33504ad74ef6a70482e509e46662633bc06cc01a5",
        bytes: 3694,
    },
    Golden {
        path: "src/main/java/ca/teamdman/sfm/common/program/FilteringProgramInputSource.java",
        oid: "307df4ff3d995fe30b55c7568bcdf38cc5267a0c",
        digest: "sha256:36853c96015f5aaf2f05af9cbf9bc9f3a5bdce58abad919b12909714212b421d",
        bytes: 1930,
    },
    Golden {
        path: "src/main/java/ca/teamdman/sfm/common/program/ObservationInputResourceTracker.java",
        oid: "464071b65968d097719b7c758c757876db6cf70c",
        digest: "sha256:2e62fe98582ff62de3486b41a9320ff133c70190124c349e86d4885613982a7d",
        bytes: 5569,
    },
];

#[derive(Facet)]
struct Ledger {
    schema: String,
    scope: String,
    source_context_commits: BTreeMap<String, String>,
    inputs: Vec<InputEvidence>,
}
#[derive(Facet)]
struct InputEvidence {
    path: String,
    core_input: String,
    staged_input: String,
    template_sha256: String,
    template_bytes: usize,
    membership: Predicate,
    raw_variant: RawVariant,
    witnesses: Vec<Witness>,
    consumer_contexts: Vec<ConsumerContext>,
}
#[derive(Facet)]
struct Predicate {
    targets: Vec<String>,
    all_features: Vec<String>,
    any_features: Vec<String>,
    none_features: Vec<String>,
}
#[derive(Facet)]
struct RawVariant {
    oid: String,
    raw_sha256: String,
    raw_bytes: usize,
}
#[derive(Facet)]
struct Witness {
    context: String,
    source_commit: String,
    present: bool,
    mode: Option<String>,
    raw_blob: Option<String>,
    raw_sha256: Option<String>,
    raw_bytes: Option<usize>,
    explicit_features: Vec<String>,
    feature_context_origin: String,
    physical_stage_raw_exact: Option<bool>,
}
#[derive(Facet)]
struct ConsumerContext {
    context: String,
    main_consumers: Vec<String>,
    test_consumers: Vec<String>,
}
struct ClosureFixture {
    core: CoreTestFixture,
    inventory: BTreeSet<String>,
    raw: BTreeMap<String, Vec<u8>>,
}
impl ClosureFixture {
    fn load() -> Result<Self> {
        let core = CoreTestFixture::load()?;
        let bytes = read_bounded(&checked_file(&core.repository, LEDGER)?, 1024 * 1024)?;
        let ledger: Ledger = facet_json::from_str(std::str::from_utf8(&bytes)?)?;
        ensure!(
            ledger.schema == "sfm:core_runtime_closure_slice@1"
                && ledger.scope == "eight_packet_runtime_helpers",
            "runtime closure ledger scope changed"
        );
        let commits: BTreeMap<String, String> = COMMITS
            .into_iter()
            .map(|(context, commit)| (context.to_owned(), commit.to_owned()))
            .collect();
        ensure!(
            ledger.source_context_commits == commits && ledger.inputs.len() == GOLDENS.len(),
            "runtime closure context/path scope changed"
        );
        let definition = core
            .features
            .0
            .get(PC)
            .ok_or_else(|| eyre::eyre!("missing packet runtime owner"))?;
        ensure!(
            same_names(&definition.supported_targets, &TARGETS[..2])
                && same_names(&definition.requires, &[VALUES, CLEANUP]),
            "runtime helpers gained invented dependency or target authority"
        );
        let cleanup = core
            .features
            .0
            .get(CLEANUP)
            .ok_or_else(|| eyre::eyre!("missing independent cleanup owner"))?;
        ensure!(
            same_names(&cleanup.supported_targets, &TARGETS[..2]) && cleanup.requires.is_empty(),
            "cleanup stopped being independent of packet runtime"
        );
        let mut paths = BTreeSet::new();
        let mut present = 0;
        let mut absent = 0;
        for input in ledger.inputs {
            let fixed = golden(&input.path)?;
            let name = input.path.rsplit('/').next().expect("fixed Java path");
            ensure!(
                paths.insert(input.path.clone())
                    && input.core_input == format!("{CORE_PREFIX}{}", input.path)
                    && input.staged_input == format!("{STAGE_PREFIX}{name}")
                    && input.template_sha256 == fixed.digest
                    && input.template_bytes == fixed.bytes
                    && input.raw_variant.oid == fixed.oid
                    && input.raw_variant.raw_sha256 == fixed.digest
                    && input.raw_variant.raw_bytes == fixed.bytes,
                "runtime closure physical/raw provenance changed"
            );
            ensure!(
                same_names(&input.membership.targets, &TARGETS[..2])
                    && same_names(&input.membership.all_features, &[PC])
                    && input.membership.any_features.is_empty()
                    && input.membership.none_features.is_empty(),
                "runtime closure gained cleanup-only or unrelated ownership"
            );
            validate_rule(&core.metadata, &input.path)?;
            ensure!(
                input.witnesses.len() == 20,
                "incomplete runtime witness matrix"
            );
            let mut contexts = BTreeSet::new();
            for witness in input.witnesses {
                let (environment, target) = witness
                    .context
                    .split_once('/')
                    .ok_or_else(|| eyre::eyre!("invalid fixed runtime witness context"))?;
                let expected = environment == "dev" && is_d2(target);
                let flags = owner_flags(expected);
                core.context(target, &flags)?;
                ensure!(
                    contexts.insert(witness.context.clone())
                        && commits.get(&witness.context) == Some(&witness.source_commit)
                        && witness.present == expected
                        && same_names(&witness.explicit_features, &flags)
                        && witness.feature_context_origin
                            == "reconstructed_explicit_owner_selection_not_historical_feature_claim",
                    "runtime witness identity or explicit context changed"
                );
                if expected {
                    ensure!(
                        witness.mode.as_deref() == Some("100644")
                            && witness.raw_blob.as_deref() == Some(fixed.oid)
                            && witness.raw_sha256.as_deref() == Some(fixed.digest)
                            && witness.raw_bytes == Some(fixed.bytes)
                            && witness.physical_stage_raw_exact == Some(true),
                        "runtime present witness raw identity changed"
                    );
                    present += 1;
                } else {
                    ensure!(
                        witness.mode.is_none()
                            && witness.raw_blob.is_none()
                            && witness.raw_sha256.is_none()
                            && witness.raw_bytes.is_none()
                            && witness.physical_stage_raw_exact.is_none(),
                        "absent runtime helper gained historical source"
                    );
                    absent += 1;
                }
            }
            ensure!(
                contexts == commits.keys().cloned().collect(),
                "runtime witness scope drift"
            );
            ensure!(
                input.consumer_contexts.len() == 20,
                "incomplete consumer tree scope"
            );
            let mut consumer_contexts = BTreeSet::new();
            for consumer in input.consumer_contexts {
                let (environment, target) = consumer
                    .context
                    .split_once('/')
                    .ok_or_else(|| eyre::eyre!("invalid runtime consumer context"))?;
                let expected = environment == "dev" && is_d2(target);
                ensure!(
                    consumer_contexts.insert(consumer.context.clone())
                        && commits.contains_key(&consumer.context)
                        && (expected
                            || (consumer.main_consumers.is_empty()
                                && consumer.test_consumers.is_empty())),
                    "unreviewed non-D2 runtime consumer was introduced"
                );
                ensure!(
                    consumer
                        .main_consumers
                        .iter()
                        .all(|path| path.starts_with("platform/minecraft/src/main/java/")
                            && path.ends_with(".java"))
                        && consumer
                            .test_consumers
                            .iter()
                            .all(|path| path.starts_with("platform/minecraft/src/test/java/")
                                && path.ends_with(".java")),
                    "consumer evidence escaped its fixed source boundary"
                );
            }
            ensure!(
                consumer_contexts == commits.keys().cloned().collect(),
                "runtime consumer tree matrix changed"
            );
        }
        ensure!(
            paths == GOLDENS.iter().map(|row| row.path.to_owned()).collect()
                && present == 16
                && absent == 144,
            "runtime closure frozen membership counts changed"
        );
        let oids = GOLDENS.iter().map(|row| row.oid.to_owned()).collect();
        let raw = read_git_blobs(&core.repository, &oids)?;
        for fixed in GOLDENS {
            verify_source(&raw[fixed.oid], fixed)?;
        }
        let inventory = discover_core_source_files(&core.core)?;
        ensure!(
            GOLDENS.iter().all(|row| inventory.contains(row.path)),
            "runtime helpers must be promoted before actual-core tests run"
        );
        Ok(Self {
            core,
            inventory,
            raw,
        })
    }

    fn render(&self, path: &str, context: &ProjectionContext) -> Result<Option<Vec<u8>>> {
        let selection = self
            .core
            .selection_for_assertion(context, &self.inventory)?;
        let Some(input) = selection.inputs.get(path) else {
            ensure!(
                selection.omitted_paths.contains(path),
                "untracked runtime omission"
            );
            return Ok(None);
        };
        ensure!(
            input.input == path,
            "runtime source fallback or adapter changed"
        );
        let source = self.core.read_source(path)?;
        verify_source(&source, golden(path)?)?;
        Ok(Some(
            render_java_source(std::str::from_utf8(&source)?, context)?.into_bytes(),
        ))
    }

    fn assert_profile(&self, target: &str, flags: &[&str]) -> Result<(usize, usize)> {
        let context = self.core.context(target, flags)?;
        let expected = is_d2(target) && context.features[PC];
        let mut present = 0;
        let mut absent = 0;
        for fixed in GOLDENS {
            let rendered = self.render(fixed.path, &context)?;
            if expected {
                ensure!(
                    rendered.as_deref() == Some(self.raw[fixed.oid].as_slice()),
                    "runtime closure full bytes changed: {} / {target}",
                    fixed.path
                );
                present += 1;
            } else {
                ensure!(
                    rendered.is_none(),
                    "runtime closure leaked into feature-off projection"
                );
                absent += 1;
            }
        }
        Ok((present, absent))
    }

    fn source(&self, name: &str) -> Result<String> {
        let path = GOLDENS
            .iter()
            .find(|row| row.path.ends_with(name))
            .ok_or_else(|| eyre::eyre!("unknown runtime helper name"))?
            .path;
        let context = self.core.context("1.19.2", &owner_flags(true))?;
        Ok(String::from_utf8(
            self.render(path, &context)?
                .ok_or_else(|| eyre::eyre!("enabled helper omitted"))?,
        )?)
    }

    fn slice_metadata(&self) -> CoreProjectInputs {
        let mut metadata = self.core.metadata.clone();
        metadata
            .source_rules
            .retain(|path, _| GOLDENS.iter().any(|row| row.path == path));
        metadata.project_files.clear();
        metadata
    }
}

fn same_names(actual: &[String], expected: &[&str]) -> bool {
    actual.len() == expected.len()
        && actual.iter().map(String::as_str).collect::<BTreeSet<_>>()
            == expected.iter().copied().collect()
}
fn is_d2(target: &str) -> bool {
    matches!(target, "1.19.2" | "1.19.4")
}
fn owner_flags(enabled: bool) -> Vec<&'static str> {
    if enabled {
        vec![PC, VALUES, CLEANUP]
    } else {
        vec![]
    }
}
fn golden(path: &str) -> Result<Golden> {
    GOLDENS
        .iter()
        .find(|row| row.path == path)
        .copied()
        .ok_or_else(|| eyre::eyre!("unexpected runtime closure path"))
}
fn validate_rule(metadata: &CoreProjectInputs, path: &str) -> Result<()> {
    let rules = metadata
        .source_rules
        .get(path)
        .ok_or_else(|| eyre::eyre!("missing runtime helper owner"))?;
    ensure!(
        rules.len() == 1
            && rules[0].input == path
            && same_names(&rules[0].when.targets, &TARGETS[..2])
            && same_names(&rules[0].when.all_features, &[PC])
            && rules[0].when.any_features.is_empty()
            && rules[0].when.none_features.is_empty(),
        "runtime closure requires exact D2 packet-computation membership"
    );
    Ok(())
}
fn verify_source(bytes: &[u8], fixed: Golden) -> Result<()> {
    ensure!(
        bytes.len() == fixed.bytes
            && sha256(bytes) == fixed.digest
            && !bytes.contains(&b'\r')
            && bytes.ends_with(b"\n"),
        "runtime helper raw source or LF policy changed"
    );
    std::str::from_utf8(bytes)?;
    Ok(())
}
fn isolated_render(
    core: &Path,
    metadata: &CoreProjectInputs,
    inventory: &BTreeSet<String>,
    context: &ProjectionContext,
    path: &str,
) -> Result<Option<Vec<u8>>> {
    let selection = select_core_inputs(metadata, context, inventory)?;
    let Some(input) = selection.inputs.get(path) else {
        ensure!(
            selection.omitted_paths.contains(path),
            "isolated omission is not owned"
        );
        return Ok(None);
    };
    ensure!(input.input == path, "isolated source fallback");
    let bytes = read_bounded(&checked_file(core, path)?, 1024 * 1024)?;
    Ok(Some(
        render_java_source(std::str::from_utf8(&bytes)?, context)?.into_bytes(),
    ))
}

#[test]
fn runtime_closure_matches_all_160_frozen_cells_and_twenty_consumer_trees() -> Result<()> {
    let fixture = ClosureFixture::load()?;
    let mut counts = (0, 0);
    for (context, _) in COMMITS {
        let (environment, target) = context.split_once('/').expect("fixed context");
        let (present, absent) =
            fixture.assert_profile(target, &owner_flags(environment == "dev" && is_d2(target)))?;
        counts.0 += present;
        counts.1 += absent;
    }
    assert_eq!(counts, (16, 144));
    Ok(())
}

#[test]
fn runtime_closure_packet_cleanup_and_client_hosts_remain_independent() -> Result<()> {
    let fixture = ClosureFixture::load()?;
    let mut counts = (0, 0);
    let mut profiles = 0;
    for target in TARGETS {
        let choices = if is_d2(target) {
            vec![
                vec![],
                vec![VALUES],
                vec![CLEANUP],
                vec![VALUES, CLEANUP],
                owner_flags(true),
                vec![
                    "client_manager",
                    "disk_readonly_access",
                    "client_program_consent",
                    "sfml_execution_side",
                ],
            ]
        } else {
            vec![vec![]]
        };
        for flags in choices {
            let (present, absent) = fixture.assert_profile(target, &flags)?;
            counts.0 += present;
            counts.1 += absent;
            profiles += 1;
        }
    }
    assert_eq!(profiles, 20);
    assert_eq!(counts, (16, 144));
    for target in &TARGETS[..2] {
        assert!(fixture.core.context(target, &[PC]).is_err());
        assert!(fixture.core.context(target, &[PC, VALUES]).is_err());
        assert!(fixture.core.context(target, &[PC, CLEANUP]).is_err());
        let enabled = fixture.core.context(target, &owner_flags(true))?;
        for unrelated in [
            "packet_transport_private",
            "client_inbox",
            "client_program_actions",
            "client_program_signing",
            "client_frame_language",
            "image_resources",
        ] {
            if let Some(value) = enabled.features.get(unrelated) {
                assert!(!value, "packet runtime implicitly gained {unrelated}");
            }
        }
    }
    for target in &TARGETS[2..] {
        assert!(fixture.core.context(target, &owner_flags(true)).is_err());
        assert!(fixture.core.context(target, &[CLEANUP]).is_err());
    }
    Ok(())
}

#[test]
fn runtime_closure_preserves_lifetime_filter_binding_and_observation_contracts() -> Result<()> {
    let fixture = ClosureFixture::load()?;
    let scope = fixture.source("ProgramExecutionScope.java")?;
    for token in [
        "Collections.unmodifiableList(activeInputs)",
        "List.copyOf(inputSources)",
        "activeInputs.clear();",
        "input.free();",
        "ephemeralResources.free();",
        "variables.free();",
        "catch (RuntimeException | Error cleanupFailure)",
        "first.addSuppressed(next);",
        "Execution scope has been freed",
    ] {
        assert!(scope.contains(token), "missing scope contract: {token}");
    }
    assert!(scope.find("freed = true;") < scope.find("input.free();"));
    let variables = fixture.source("ProgramVariableEnvironment.java")?;
    for token in [
        "new LinkedHashMap<>()",
        "ProgramRelation.singleton(ProgramValueReference.resolved(value))",
        "relation.rows().size() != 1",
        "Collections.unmodifiableMap(values)",
        "relations.clear();",
        "toLowerCase(java.util.Locale.ROOT)",
    ] {
        assert!(
            variables.contains(token),
            "missing variable contract: {token}"
        );
    }
    let ephemeral = fixture.source("ProgramEphemeralResourceOwner.java")?;
    for token in [
        "Collections.newSetFromMap(new IdentityHashMap<>())",
        "resources.remove(resource)",
        "List.copyOf(resources)",
        "resources.clear();",
        "first.addSuppressed(next);",
    ] {
        assert!(
            ephemeral.contains(token),
            "missing ephemeral contract: {token}"
        );
    }
    assert!(ephemeral.find("resources.remove(resource)") < ephemeral.find("resource.free();"));
    let forget = fixture.source("ProgramInputForgetRequest.java")?;
    assert!(forget.contains("Set.copyOf(Objects.requireNonNull(labels))"));
    assert!(forget.contains("allInputs && !labels.isEmpty()"));
    let filtering = fixture.source("FilteringProgramInputSource.java")?;
    for token in [
        "type.copy(stack)",
        "selection.matches(slot.type, stack)",
        "slotConsumer.accept(slot)",
        "delegate.forget(context, request)",
        "retained == null ? null : new FilteringProgramInputSource(retained, selection)",
        "return delegate.inputStatement();",
        "delegate.free();",
    ] {
        assert!(
            filtering.contains(token),
            "missing filter contract: {token}"
        );
    }
    assert!(
        filtering.find("selection.matches(slot.type, stack)")
            < filtering.find("slotConsumer.accept(slot)")
    );
    let selection = fixture.source("ProgramInputSelection.java")?;
    for token in [
        "permits",
        "new ProgramResourceValue(resourceType, unitStack)",
        "capabilityId.equals(\"sfm:text\")",
        "SFMTextResourceAdapters.supports(itemStack)",
        "PacketItem.getValue(itemStack).filter(pattern::matches).isPresent()",
        ".<Object>map(ProgramValueReference::resolved)",
    ] {
        assert!(
            selection.contains(token),
            "missing binding contract: {token}"
        );
    }
    let observation = fixture.source("ObservationInputResourceTracker.java")?;
    for token in [
        "delegate.getRetentionObligationForSlot",
        "delegate.getRemainingRetentionObligation",
        "delegate.getMaxTransferable",
        "retainedByGroup.merge",
        "retainedBySlot.merge",
        "transferredByGroup.merge",
        "Retention observation cannot be negative",
        "Transfer observation cannot be negative",
        "IdExpansionBehaviour.NO_EXPAND",
        "new ExpandedGroup(resourceType, resourceType.getRegistryKeyForStack(stack))",
    ] {
        assert!(
            observation.contains(token),
            "missing observation contract: {token}"
        );
    }
    assert!(!observation.contains("delegate.trackRetentionObligation("));
    assert!(!observation.contains("delegate.trackTransfer("));
    let source = fixture.source("ProgramInputSource.java")?;
    assert!(source.contains("default Optional<InputStatement> inputStatement()"));
    assert!(source.contains("return Optional.empty();"));
    assert!(source.contains("void free();"));
    Ok(())
}

#[test]
fn runtime_closure_common_edits_propagate_through_real_selection_without_touching_core()
-> Result<()> {
    let fixture = ClosureFixture::load()?;
    let temp = tempfile::tempdir()?;
    let core = temp.path().join("platform/minecraft/core-liquid-template");
    let metadata = fixture.slice_metadata();
    let path = GOLDENS[0].path;
    let destination = core.join(path);
    fs::create_dir_all(destination.parent().expect("fixed source parent"))?;
    let original = fixture.core.read_source(path)?;
    let mut edited = original.clone();
    edited.extend_from_slice(b"// isolated shared-source edit\n");
    fs::write(&destination, &edited)?;
    let inventory = BTreeSet::from([path.to_owned()]);
    for target in &TARGETS[..2] {
        let enabled = fixture.core.context(target, &owner_flags(true))?;
        assert_eq!(
            isolated_render(&core, &metadata, &inventory, &enabled, path)?,
            Some(edited.clone())
        );
        let disabled = fixture.core.context(target, &[CLEANUP])?;
        assert!(isolated_render(&core, &metadata, &inventory, &disabled, path)?.is_none());
    }
    assert_eq!(fixture.core.read_source(path)?, original);
    // No helper files exist in this separate tree. Off/cleanup-only selection
    // must return omissions without attempting a Java file read.
    let empty_core = temp
        .path()
        .join("empty/platform/minecraft/core-liquid-template");
    fs::create_dir_all(&empty_core)?;
    for target in TARGETS {
        let flags = if is_d2(target) { vec![CLEANUP] } else { vec![] };
        let context = fixture.core.context(target, &flags)?;
        for fixed in GOLDENS {
            assert!(
                isolated_render(
                    &empty_core,
                    &metadata,
                    &BTreeSet::new(),
                    &context,
                    fixed.path
                )?
                .is_none()
            );
        }
    }
    Ok(())
}

#[test]
fn runtime_closure_refuses_owner_conflicts_and_raw_source_mutations() -> Result<()> {
    let fixture = ClosureFixture::load()?;
    let path = GOLDENS[0].path;
    let mut metadata = fixture.slice_metadata();
    metadata.source_rules.get_mut(path).expect("validated rule")[0]
        .when
        .all_features
        .clear();
    assert!(validate_rule(&metadata, path).is_err());
    let context = fixture.core.context("1.19.2", &owner_flags(true))?;
    let mut conflicts = fixture.slice_metadata();
    let rule = conflicts.source_rules[path][0].clone();
    conflicts
        .source_rules
        .get_mut(path)
        .expect("validated rule")
        .push(rule);
    assert!(select_core_inputs(&conflicts, &context, &BTreeSet::new()).is_err());
    let mut unknown = fixture.slice_metadata();
    unknown.source_rules.get_mut(path).expect("validated rule")[0]
        .when
        .all_features
        .push("unregistered_runtime_owner".to_owned());
    assert!(select_core_inputs(&unknown, &context, &BTreeSet::new()).is_err());
    for fixed in GOLDENS {
        let raw = &fixture.raw[fixed.oid];
        assert!(verify_source(raw, fixed).is_ok());
        let mut token_edit = raw.clone();
        token_edit[0] ^= 1;
        assert!(verify_source(&token_edit, fixed).is_err());
        let mut extra_lf = raw.clone();
        extra_lf.push(b'\n');
        assert!(verify_source(&extra_lf, fixed).is_err());
        assert!(verify_source(&raw[..raw.len() - 1], fixed).is_err());
        let crlf = String::from_utf8(raw.clone())?
            .replace('\n', "\r\n")
            .into_bytes();
        assert!(verify_source(&crlf, fixed).is_err());
    }
    Ok(())
}

#[test]
fn runtime_closure_ignores_environment_nested_key_and_preset_for_selection() -> Result<()> {
    let fixture = ClosureFixture::load()?;
    for target in TARGETS {
        let choices = if is_d2(target) {
            vec![false, true]
        } else {
            vec![false]
        };
        for enabled in choices {
            let context = fixture.core.context(target, &owner_flags(enabled))?;
            let mut changed = context.clone();
            changed.environment = "release".to_owned();
            changed.projection_key = "arbitrary/nested/runtime-closure-review".to_owned();
            changed.preset = "unrelated-readable-label".to_owned();
            for fixed in GOLDENS {
                assert_eq!(
                    fixture.render(fixed.path, &context)?,
                    fixture.render(fixed.path, &changed)?
                );
            }
        }
    }
    Ok(())
}
