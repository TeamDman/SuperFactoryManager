//! Post-promotion source contracts for the three released transfer AST classes.
//!
//! Real core selection precedes every source read. Historical Git blobs are
//! bounded test witnesses, never production fallbacks. No Java build/runtime
//! acceptance follows from these source assertions.

#![cfg(test)]

use super::candidate_lock::checked_file;
use super::context::ProjectionContext;
use super::core_inputs::discover_core_source_files;
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

const PREFIX: &str = "src/main/java/ca/teamdman/sfml/ast/";
const CORE_PREFIX: &str = "platform/minecraft/core-liquid-template/";
const STAGE_PREFIX: &str = "platform/cli/sfm-propagate-changes/target/core-transfer-ast-stage-v1/";
const LEDGER: &str = "docs/tasks/sfm-core-transfer-ast-slice.json";
const INPUT: &str = "InputStatement.java";
const OUTPUT: &str = "OutputStatement.java";
const FORGET: &str = "ForgetStatement.java";
const NAMES: [&str; 3] = [INPUT, OUTPUT, FORGET];
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
    oid: &'static str,
    digest: &'static str,
    bytes: usize,
}
const GOLDENS: [Golden; 8] = [
    Golden {
        oid: "0914c11cb27f8d3f980cd39a7006ebfa5f5cba16",
        digest: "sha256:15c9296b2e11f648657140d4056bc253793f385b1babc2fb9a4bade508de29f1",
        bytes: 34019,
    },
    Golden {
        oid: "110ae7d7cdb3f042d4d4b93514ad0b30fa94fe81",
        digest: "sha256:d303ca87173e92346f3fe90ce26b9b80c01eb0789bd0f0f0205c29cb44042d33",
        bytes: 34493,
    },
    Golden {
        oid: "7c254cec224aab0c79b3bd88deeccfec932ae306",
        digest: "sha256:125f9a6b466fe916fdbdd83f758354fe3d21748eeed672337267d6461099ee0e",
        bytes: 12757,
    },
    Golden {
        oid: "87bfeed60c730f05c3b974e563aadd5e5e282049",
        digest: "sha256:c8797d7672d21795ef71d5ae826bdfb8e3b5f90d34cc87d7e0330efae5cd6ccc",
        bytes: 3316,
    },
    Golden {
        oid: "9e2f8d90ee991d46c66e956dcf8a1dc366ed66aa",
        digest: "sha256:18bb4bde9eae3fe49205a55f8c82f8e974e0273b6e3fcb7692a603361d3d80e3",
        bytes: 2351,
    },
    Golden {
        oid: "ad1f009cf92f9eaa99b1fea5afe6df840cb1148d",
        digest: "sha256:1401aa401941eb876362831c5945caff10eee5fcf5c3c33598029b2bfda72ccf",
        bytes: 34515,
    },
    Golden {
        oid: "cf8b94d12caacdc42f17cce606d35df13de7f9fb",
        digest: "sha256:672c05ce23417b93817e3ddb7e827be4c759051ad2b917ad4e27bd4ddee0cd59",
        bytes: 34485,
    },
    Golden {
        oid: "fdb0f9a028d82679b3eb43c5f87cbb3ef3f213c1",
        digest: "sha256:a8634a77684e857eff1bd882a0fb3f11f7ff89359a275ab3385ad9b8938077ac",
        bytes: 6380,
    },
];
#[derive(Clone, Copy)]
struct Template {
    name: &'static str,
    digest: &'static str,
    bytes: usize,
}
const TEMPLATES: [Template; 3] = [
    Template {
        name: "ForgetStatement.java",
        digest: "sha256:1fb2a19e5746644f15eb4d2ddb42e1d0d4781ce864a9a1b9d933d1a71e4728d4",
        bytes: 5023,
    },
    Template {
        name: "InputStatement.java",
        digest: "sha256:24165e50462caaf351c36a634838bf1ab304f8946870406f028fdf736db53bed",
        bytes: 16580,
    },
    Template {
        name: "OutputStatement.java",
        digest: "sha256:16ba72a6cf99d5127543503e8310ec709b06bfee673246957a9f0b778e7f752f",
        bytes: 36980,
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
    raw_variants: Vec<RawVariant>,
    witnesses: Vec<Witness>,
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
    normalized_sha256: String,
    normalized_bytes: usize,
    crlf_to_lf_count: usize,
    appended_final_lf: bool,
    other_token_edits: bool,
}
#[derive(Facet)]
struct Witness {
    context: String,
    source_commit: String,
    present: bool,
    mode: String,
    raw_blob: String,
    raw_sha256: String,
    raw_bytes: usize,
    explicit_features: Vec<String>,
    feature_context_origin: String,
    stage_preview_raw_exact: bool,
}
struct TransferFixture {
    core: CoreTestFixture,
    inventory: BTreeSet<String>,
    raw: BTreeMap<String, Vec<u8>>,
}
impl TransferFixture {
    fn load() -> Result<Self> {
        let core = CoreTestFixture::load()?;
        let file = checked_file(&core.repository, LEDGER)?;
        let bytes = read_bounded(&file, 1024 * 1024)?;
        let ledger: Ledger = facet_json::from_str(std::str::from_utf8(&bytes)?)?;
        ensure!(
            ledger.schema == "sfm:core_transfer_ast_slice@1"
                && ledger.scope == "three_released_ast_transfer_classes",
            "transfer ledger scope changed"
        );
        let commits: BTreeMap<String, String> = COMMITS
            .into_iter()
            .map(|(name, oid)| (name.to_owned(), oid.to_owned()))
            .collect();
        ensure!(
            ledger.source_context_commits == commits && ledger.inputs.len() == 3,
            "transfer context/file scope changed"
        );
        let definition = core
            .features
            .0
            .get(PC)
            .ok_or_else(|| eyre::eyre!("missing packet runtime owner"))?;
        ensure!(
            same_names(&definition.supported_targets, &TARGETS[..2])
                && same_names(&definition.requires, &[VALUES, CLEANUP]),
            "transfer acquired invented runtime prerequisites"
        );
        let mut names = BTreeSet::new();
        let mut oids = BTreeSet::new();
        let mut present = 0;
        for input in ledger.inputs {
            let name = input
                .path
                .strip_prefix(PREFIX)
                .ok_or_else(|| eyre::eyre!("unexpected transfer input"))?;
            let fixed = template(name)?;
            ensure!(
                names.insert(name.to_owned())
                    && input.core_input == format!("{CORE_PREFIX}{}", input.path)
                    && input.staged_input == format!("{STAGE_PREFIX}{name}")
                    && input.template_sha256 == fixed.digest
                    && input.template_bytes == fixed.bytes,
                "transfer template provenance changed"
            );
            ensure!(
                input.membership.targets.is_empty()
                    && input.membership.all_features.is_empty()
                    && input.membership.any_features.is_empty()
                    && input.membership.none_features.is_empty(),
                "released transfer membership was made conditional"
            );
            if let Some(rules) = core.metadata.source_rules.get(&input.path) {
                ensure!(
                    rules.len() == 1
                        && rules[0].input == input.path
                        && rules[0].when.targets.is_empty()
                        && rules[0].when.all_features.is_empty()
                        && rules[0].when.any_features.is_empty()
                        && rules[0].when.none_features.is_empty(),
                    "actual released transfer ownership changed"
                );
            }
            let mut local_oids = BTreeSet::new();
            for variant in input.raw_variants {
                let golden = golden(&variant.oid)?;
                ensure!(
                    local_oids.insert(variant.oid.clone())
                        && variant.raw_sha256 == golden.digest
                        && variant.raw_bytes == golden.bytes
                        && variant.normalized_sha256 == golden.digest
                        && variant.normalized_bytes == golden.bytes
                        && variant.crlf_to_lf_count == 0
                        && !variant.appended_final_lf
                        && !variant.other_token_edits,
                    "transfer raw bytes were silently normalized"
                );
                oids.insert(variant.oid);
            }
            ensure!(
                input.witnesses.len() == 20,
                "incomplete transfer witness matrix"
            );
            let mut contexts = BTreeSet::new();
            let mut witnessed = BTreeSet::new();
            for witness in input.witnesses {
                let (environment, target) = witness
                    .context
                    .split_once('/')
                    .ok_or_else(|| eyre::eyre!("bad transfer context"))?;
                let pc = environment == "dev" && is_d2(target);
                let oid = expected_oid(name, target, pc)?;
                let fixed = golden(oid)?;
                let flags = owner_flags(pc);
                core.context(target, &flags)?;
                ensure!(
                    contexts.insert(witness.context.clone())
                        && commits.get(&witness.context) == Some(&witness.source_commit)
                        && witness.present
                        && witness.mode == "100644"
                        && witness.raw_blob == oid
                        && witness.raw_sha256 == fixed.digest
                        && witness.raw_bytes == fixed.bytes
                        && same_names(&witness.explicit_features, &flags)
                        && witness.stage_preview_raw_exact
                        && witness.feature_context_origin
                            == "reconstructed_explicit_owner_selection_not_historical_feature_claim",
                    "transfer witness identity/member/full bytes changed"
                );
                witnessed.insert(oid.to_owned());
                present += 1;
            }
            ensure!(local_oids == witnessed, "transfer variant coverage changed");
        }
        ensure!(
            names == NAMES.into_iter().map(str::to_owned).collect()
                && oids == GOLDENS.iter().map(|row| row.oid.to_owned()).collect()
                && present == 60,
            "transfer scope/member totals changed"
        );
        let raw = read_git_blobs(&core.repository, &oids)?;
        for fixed in GOLDENS {
            let bytes = &raw[fixed.oid];
            ensure!(
                bytes.len() == fixed.bytes
                    && sha256(bytes) == fixed.digest
                    && raw_exact(bytes)?.as_slice() == bytes.as_slice(),
                "transfer fixed raw witness changed"
            );
        }
        let inventory = discover_core_source_files(&core.core)?;
        ensure!(
            NAMES
                .iter()
                .all(|name| inventory.contains(&format!("{PREFIX}{name}"))),
            "transfer inputs must be promoted to actual core before these tests"
        );
        Ok(Self {
            core,
            inventory,
            raw,
        })
    }

    fn render(&self, name: &str, context: &ProjectionContext) -> Result<Vec<u8>> {
        let path = format!("{PREFIX}{name}");
        let selection = self
            .core
            .selection_for_assertion(context, &self.inventory)?;
        let input = selection
            .inputs
            .get(&path)
            .ok_or_else(|| eyre::eyre!("released transfer source omitted"))?;
        ensure!(input.input == path, "unreviewed alternate transfer input");
        let source = self.core.read_source(&path)?;
        let fixed = template(name)?;
        ensure!(
            source.len() == fixed.bytes
                && sha256(&source) == fixed.digest
                && raw_exact(&source)?.as_slice() == source.as_slice(),
            "promoted transfer template bytes changed"
        );
        Ok(render_java_source(std::str::from_utf8(&source)?, context)?.into_bytes())
    }
    fn assert_profile(&self, target: &str, flags: &[&str]) -> Result<()> {
        let context = self.core.context(target, flags)?;
        for name in NAMES {
            let oid = expected_oid(name, target, context.features[PC])?;
            ensure!(
                self.render(name, &context)? == self.raw[oid],
                "transfer full body mismatch: {name} / {target}"
            );
        }
        Ok(())
    }
    fn body(&self, name: &str, context: &ProjectionContext) -> Result<String> {
        Ok(String::from_utf8(self.render(name, context)?)?)
    }
}
fn raw_exact(bytes: &[u8]) -> Result<Vec<u8>> {
    ensure!(
        !bytes.contains(&b'\r') && bytes.ends_with(b"\n"),
        "transfer source normalization is not authorized"
    );
    std::str::from_utf8(bytes)?;
    Ok(bytes.to_vec())
}
fn same_names(actual: &[String], expected: &[&str]) -> bool {
    actual.len() == expected.len()
        && actual.iter().map(String::as_str).collect::<BTreeSet<_>>()
            == expected.iter().copied().collect()
}
fn is_d2(target: &str) -> bool {
    matches!(target, "1.19.2" | "1.19.4")
}
fn owner_flags(pc: bool) -> Vec<&'static str> {
    if pc {
        vec![PC, VALUES, CLEANUP]
    } else {
        vec![]
    }
}
fn template(name: &str) -> Result<Template> {
    TEMPLATES
        .iter()
        .find(|row| row.name == name)
        .copied()
        .ok_or_else(|| eyre::eyre!("unexpected transfer template"))
}
fn golden(oid: &str) -> Result<Golden> {
    GOLDENS
        .iter()
        .find(|row| row.oid == oid)
        .copied()
        .ok_or_else(|| eyre::eyre!("unexpected transfer blob"))
}
fn expected_oid(name: &str, target: &str, pc: bool) -> Result<&'static str> {
    ensure!(
        TARGETS.contains(&target) && (!pc || is_d2(target)),
        "unsupported transfer profile"
    );
    match name {
        INPUT if pc => Ok("fdb0f9a028d82679b3eb43c5f87cbb3ef3f213c1"),
        INPUT => Ok("7c254cec224aab0c79b3bd88deeccfec932ae306"),
        FORGET if pc => Ok("9e2f8d90ee991d46c66e956dcf8a1dc366ed66aa"),
        FORGET => Ok("87bfeed60c730f05c3b974e563aadd5e5e282049"),
        OUTPUT if pc => Ok("cf8b94d12caacdc42f17cce606d35df13de7f9fb"),
        OUTPUT if target == "26.1.2" => Ok("110ae7d7cdb3f042d4d4b93514ad0b30fa94fe81"),
        OUTPUT if matches!(target, "1.21.0" | "1.21.1") => {
            Ok("ad1f009cf92f9eaa99b1fea5afe6df840cb1148d")
        }
        OUTPUT => Ok("0914c11cb27f8d3f980cd39a7006ebfa5f5cba16"),
        _ => Err(eyre::eyre!("unexpected transfer profile input")),
    }
}

#[test]
fn transfer_ast_slice_matches_all_sixty_frozen_source_witnesses() -> Result<()> {
    let fixture = TransferFixture::load()?;
    let mut cells = 0;
    for (context, _) in COMMITS {
        let (environment, target) = context.split_once('/').expect("fixed context");
        fixture.assert_profile(target, &owner_flags(environment == "dev" && is_d2(target)))?;
        cells += NAMES.len();
    }
    assert_eq!(cells, 60);
    Ok(())
}

#[test]
fn transfer_cleanup_values_and_client_hosts_do_not_enable_packet_hunks_implicitly() -> Result<()> {
    let fixture = TransferFixture::load()?;
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
            fixture.assert_profile(target, &flags)?;
            profiles += 1;
        }
    }
    assert_eq!(profiles, 20);
    for target in &TARGETS[..2] {
        assert!(fixture.core.context(target, &[PC]).is_err());
        assert!(fixture.core.context(target, &[PC, VALUES]).is_err());
        assert!(fixture.core.context(target, &[PC, CLEANUP]).is_err());
        let cleanup = fixture.core.context(target, &[CLEANUP])?;
        assert!(!cleanup.features[PC]);
        let input = fixture.body(INPUT, &cleanup)?;
        assert!(input.contains("limitedInputSlotsCache = null;"));
        assert!(input.contains("context.addInput(this);"));
        assert!(input.contains("public void freeSlots()"));
        assert!(input.contains("public void transferSlotsTo(InputStatement other)"));
        assert!(!input.contains("ProgramInputSelection") && !input.contains("ProgramRelation"));
        assert!(
            fixture
                .body(FORGET, &cleanup)?
                .contains("context.getInputs().addAll(newInputs);")
        );
        assert!(
            !fixture
                .body(FORGET, &cleanup)?
                .contains("boolean allInputs")
        );
        assert!(
            !fixture
                .body(OUTPUT, &cleanup)?
                .contains("isGeneratedSource()")
        );
    }
    Ok(())
}

#[test]
fn packet_input_binding_and_forget_preserve_simulation_and_source_lifetime_contracts() -> Result<()>
{
    let fixture = TransferFixture::load()?;
    for target in &TARGETS[..2] {
        let context = fixture.core.context(target, &owner_flags(true))?;
        let input = fixture.body(INPUT, &context)?;
        assert!(input.contains("new WorldProgramInputSource(this)"));
        assert!(input.contains("new FilteringProgramInputSource(inputSource, selection)"));
        assert!(input.contains("ProgramRelation.EMPTY"));
        assert!(input.contains("ProgramOccurrenceId.create()"));
        assert!(input.contains(
            "for (long occurrence = 0; occurrence < observation.amount(); occurrence++)"
        ));
        let simulation_binding = input
            .find("ProgramRelation.EMPTY")
            .expect("simulation binding");
        let observation = input
            .find("ProgramResourceObserver.observe(")
            .expect("resource observation");
        assert!(simulation_binding < observation);
        assert!(
            !input.contains("public void freeSlots()") && !input.contains("limitedInputSlotsCache")
        );
        let forget = fixture.body(FORGET, &context)?;
        assert!(forget.contains("ProgramInputForgetRequest.all()"));
        assert!(forget.contains("inputSource.forget(context, request)"));
        assert!(forget.contains("context.replaceInputs(newInputs);"));
        assert!(forget.contains("labelToForget = Set.copyOf(labelToForget);"));
        assert!(forget.contains("Bare FORGET cannot also name labels"));
        assert!(forget.contains("public static ForgetStatement allInputsStatement()"));
        assert!(forget.contains("if (allInputs) {\n            return \"FORGET\";"));
        assert!(!forget.contains("oldInputStatement.transferSlotsTo"));
        let output = fixture.body(OUTPUT, &context)?;
        // The manager's earlier loss-summary position is unrelated. Check the
        // actual slot-report method before it dereferences a generated source.
        let slot_report = &output[output
            .find("private static <STACK, ITEM, CAP> void addSlotDetailsToReport(")
            .expect("slot report method")..];
        let generated = slot_report
            .find("inputSlot.isGeneratedSource()")
            .expect("generated-source branch");
        let position = slot_report
            .find("report.append(\"Position: \").append(slot.getPos())")
            .expect("world-position diagnostics");
        assert!(generated < position);
        assert!(slot_report[generated..position].contains("return;"));
    }
    Ok(())
}

#[test]
fn output_stack_limits_and_dimension_identifiers_retain_released_version_behavior() -> Result<()> {
    let fixture = TransferFixture::load()?;
    for target in TARGETS {
        let context = fixture.core.context(target, &[])?;
        let output = fixture.body(OUTPUT, &context)?;
        let modern = matches!(target, "1.21.0" | "1.21.1" | "26.1.2");
        assert_eq!(output.contains("if (maxStackSizeForSlot > 99)"), modern);
        assert_eq!(output.contains("type.getMaxStackSize(stack);"), modern);
        assert_eq!(
            output.contains("@SuppressWarnings(\"RedundantIfStatement\")"),
            modern
        );
        assert_eq!(
            output
                .contains("return type.getAmount(stack) < type.getMaxStackSizeForSlot(cap, slot);"),
            !modern
        );
        assert_eq!(
            output.contains(".append(level.dimensionTypeId().location())"),
            !modern
        );
        assert_eq!(
            output.contains(".append(level.dimension().location())"),
            matches!(target, "1.21.0" | "1.21.1")
        );
        assert_eq!(
            output.contains(".append(level.dimension().identifier())"),
            target == "26.1.2"
        );
        assert_eq!(
            output.contains("import net.minecraft.resources.Identifier;"),
            target == "26.1.2"
        );
        assert_eq!(
            output.contains("import net.minecraft.resources.ResourceLocation;"),
            target != "26.1.2"
        );
        if target == "26.1.2" {
            assert!(output.contains("Identifier resourceTypeName"));
            assert!(output.contains("Identifier inputBlockEntityType"));
            assert!(output.contains("Identifier blockType"));
            assert!(!output.contains("ResourceLocation"));
        }
    }
    Ok(())
}

#[test]
fn transfer_outputs_use_explicit_version_features_not_environment_or_nested_identity() -> Result<()>
{
    let fixture = TransferFixture::load()?;
    for target in TARGETS {
        for pc in if is_d2(target) {
            &[false, true][..]
        } else {
            &[false][..]
        } {
            let context = fixture.core.context(target, &owner_flags(*pc))?;
            let mut changed = context.clone();
            changed.environment = "release".to_owned();
            changed.projection_key = "arbitrary/nested/transfer-review".to_owned();
            changed.preset = "different-visible-label".to_owned();
            for name in NAMES {
                assert_eq!(
                    fixture.render(name, &context)?,
                    fixture.render(name, &changed)?
                );
            }
        }
    }
    for target in &TARGETS[2..] {
        assert!(fixture.core.context(target, &owner_flags(true)).is_err());
    }
    Ok(())
}

#[test]
fn transfer_source_policy_rejects_every_unapproved_line_ending_edit() -> Result<()> {
    let _fixture = TransferFixture::load()?;
    assert_eq!(raw_exact(b" x \n\n")?, b" x \n\n");
    assert!(raw_exact(b"x\r\n").is_err());
    assert!(raw_exact(b"x\ry\n").is_err());
    assert!(raw_exact(b"x").is_err());
    assert!(raw_exact(b"").is_err());
    assert!(raw_exact(&[0xff, b'\n']).is_err());
    Ok(())
}
