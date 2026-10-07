//! Post-promotion source and ownership contracts for the shared DiskItem template.
//!
//! Raw Git objects are bounded witnesses, never production source fallbacks.
//! No dependency acquisition, Java compilation or gameplay acceptance occurs here.

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

const PATH: &str = "src/main/java/ca/teamdman/sfm/common/item/DiskItem.java";
const LEDGER: &str = "docs/tasks/sfm-core-disk-item-slice.json";
const READONLY: &str = "disk_readonly_access";
const SIDE: &str = "sfml_execution_side";
const TOOLTIP: &str = "tooltip_mode_override";
const OWNERS: [&str; 3] = [READONLY, SIDE, TOOLTIP];
const TEMPLATE_SHA: &str =
    "sha256:f5e5067f24f4af3f530b9c453eac18ab8161d8626bb9731239d18db41dc98dbd";
const TEMPLATE_BYTES: usize = 25830;
const CURRENT_TEMPLATE_BYTES: usize = 25861;
const CURRENT_TEMPLATE_SHA: &str =
    "sha256:8bdefe73f7bde25cea8baffa8b71700ab53e6ddb5b7bcb1a9451808705db67f6";
const PC: &str = "packet_computation";
const PC_FLAGS: [&str; 3] = [PC, "packet_values", "runtime_resource_cleanup"];
const REFINEMENT_LEDGER: &str = "docs/tasks/sfm-core-text-provider-disk-consumer-slice-v2.json";
const OLD_PROGRAM_GUARD: &str = "{% if features.disk_readonly_access %}";
const NEW_PROGRAM_GUARD: &str =
    "{% if features.disk_readonly_access or features.packet_computation %}";
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
const WITNESSES: [(&str, &str); 20] = [
    ("dev/1.19.2", "f954a9eb677020128efc121978a9ed2a44a64877"),
    ("dev/1.19.4", "24544c92e33e1356fa102736b39101ef32587280"),
    ("dev/1.20", "efa548545c8377d492812408ebbd81e69fada74e"),
    ("dev/1.20.1", "efa548545c8377d492812408ebbd81e69fada74e"),
    ("dev/1.20.2", "efa548545c8377d492812408ebbd81e69fada74e"),
    ("dev/1.20.3", "efa548545c8377d492812408ebbd81e69fada74e"),
    ("dev/1.20.4", "efa548545c8377d492812408ebbd81e69fada74e"),
    ("dev/1.21.0", "e58d6786497f075c8a81ba816ef424462e504054"),
    ("dev/1.21.1", "e58d6786497f075c8a81ba816ef424462e504054"),
    ("dev/26.1.2", "c30b50d4768bf76552a4f546eac829ce406c57eb"),
    ("release/1.19.2", "8c804a0e3f6ad2f4498cc31e59e8583be3c9ae3c"),
    ("release/1.19.4", "94cda20433501052637127a4ede58b29aa0d55fb"),
    ("release/1.20", "94cda20433501052637127a4ede58b29aa0d55fb"),
    ("release/1.20.1", "94cda20433501052637127a4ede58b29aa0d55fb"),
    ("release/1.20.2", "94cda20433501052637127a4ede58b29aa0d55fb"),
    ("release/1.20.3", "94cda20433501052637127a4ede58b29aa0d55fb"),
    ("release/1.20.4", "94cda20433501052637127a4ede58b29aa0d55fb"),
    ("release/1.21.0", "955b3deb37577427eef282576fe0c22817343059"),
    ("release/1.21.1", "955b3deb37577427eef282576fe0c22817343059"),
    ("release/26.1.2", "473d407ee0963f045917c0fd8a1909a0e4610328"),
];
struct Golden {
    oid: &'static str,
    digest: &'static str,
    bytes: usize,
    lf: usize,
}
static GOLDENS: [Golden; 9] = [
    Golden {
        oid: "24544c92e33e1356fa102736b39101ef32587280",
        digest: "sha256:276f6f9bd6077fa2b4f7763678236af057fe2ab7a04edd7a9b65720ed2727e33",
        bytes: 12031,
        lf: 335,
    },
    Golden {
        oid: "473d407ee0963f045917c0fd8a1909a0e4610328",
        digest: "sha256:b66ef6801f7b655ba2738e03387f0b857e61a851e60d6bbae6eec805d2b13b3d",
        bytes: 11207,
        lf: 296,
    },
    Golden {
        oid: "8c804a0e3f6ad2f4498cc31e59e8583be3c9ae3c",
        digest: "sha256:69828ef1b5b79a79384e6ade2acaa8bd519f52226f1d097973c1f44195d1c328",
        bytes: 11604,
        lf: 319,
    },
    Golden {
        oid: "94cda20433501052637127a4ede58b29aa0d55fb",
        digest: "sha256:4378d324b666c4d567010f5966657bad76192d778d2c8b20dd02b593b61a188e",
        bytes: 11509,
        lf: 318,
    },
    Golden {
        oid: "955b3deb37577427eef282576fe0c22817343059",
        digest: "sha256:79c5b1ecbda2a865c3772f435d97d8bc4fa4990476f7304558c076fc7fde9f55",
        bytes: 10880,
        lf: 297,
    },
    Golden {
        oid: "c30b50d4768bf76552a4f546eac829ce406c57eb",
        digest: "sha256:c4c06ef37c2a87a5d35ad2d143a7bf9708e2c1f923eb82bf96aa89b8f5f0d5dd",
        bytes: 11903,
        lf: 315,
    },
    Golden {
        oid: "e58d6786497f075c8a81ba816ef424462e504054",
        digest: "sha256:422931224d68a27fa72207ed1535605c3ddf1d97d345f880c682385e8226ede4",
        bytes: 11537,
        lf: 316,
    },
    Golden {
        oid: "efa548545c8377d492812408ebbd81e69fada74e",
        digest: "sha256:03569e0c8287eef92bb4db6bde71ad0444bcd07a6b0ac80d9dd3e1364e907494",
        bytes: 12067,
        lf: 336,
    },
    Golden {
        oid: "f954a9eb677020128efc121978a9ed2a44a64877",
        digest: "sha256:4896d877226d7e4975144b0c1eb03ed5cfebdbf847e2555331e37cfdd617ea35",
        bytes: 12126,
        lf: 336,
    },
];

#[derive(Facet)]
struct Ledger {
    schema: String,
    context_commits: BTreeMap<String, String>,
    owners: Vec<Owner>,
    input: Input,
    raw_variants: Vec<RawVariant>,
}
#[derive(Facet)]
struct Owner {
    id: String,
    supported_targets: Vec<String>,
    requires: Vec<String>,
}
#[derive(Facet)]
struct Input {
    path: String,
    stage_path: String,
    core_path: String,
    source_sha256: String,
    source_bytes: usize,
    membership: String,
    member_features: Vec<String>,
    witnesses: BTreeMap<String, String>,
}
#[derive(Facet)]
struct RawVariant {
    blob: String,
    sha256: String,
    bytes: usize,
    cr_count: usize,
    lf_count: usize,
    final_lf: bool,
    contexts: Vec<String>,
}
struct Fixture {
    core: CoreTestFixture,
    inventory: BTreeSet<String>,
    raw: BTreeMap<String, Vec<u8>>,
}
impl Fixture {
    fn load() -> Result<Self> {
        let core = CoreTestFixture::load()?;
        validate_consumer_refinement(&core)?;
        let bytes = read_bounded(&core.repository.join(LEDGER), 1024 * 1024)?;
        let ledger: Ledger = facet_json::from_str(std::str::from_utf8(&bytes)?)?;
        ensure!(
            ledger.schema == "sfm:core-disk-item-slice@1",
            "wrong Disk ledger"
        );
        let expected_commits = COMMITS
            .into_iter()
            .map(|(name, oid)| (name.to_owned(), oid.to_owned()))
            .collect::<BTreeMap<_, _>>();
        ensure!(
            ledger.context_commits == expected_commits,
            "Disk source witness commits changed"
        );
        ensure!(
            ledger.owners.len() == 3 && ledger.raw_variants.len() == 9,
            "Disk ownership scope changed"
        );
        let input = ledger.input;
        ensure!(
            input.path == PATH
                && input.stage_path
                    == "platform/cli/sfm-propagate-changes/target/core-disk-item-stage-v1/DiskItem.java"
                && input.core_path == format!("platform/minecraft/core-liquid-template/{PATH}")
                && input.source_sha256 == TEMPLATE_SHA
                && input.source_bytes == TEMPLATE_BYTES
                && input.membership == "shared_all_ten_targets_all_feature_masks"
                && input.member_features.iter().map(String::as_str).eq(OWNERS),
            "Disk authored input identity or membership changed"
        );
        let expected_witnesses = WITNESSES
            .into_iter()
            .map(|(name, oid)| (name.to_owned(), oid.to_owned()))
            .collect::<BTreeMap<_, _>>();
        ensure!(
            input.witnesses == expected_witnesses,
            "Disk all-context raw witness map changed"
        );
        validate_shared_rule(&core.metadata)?;
        let mut seen_owners = BTreeSet::new();
        for owner in ledger.owners {
            let targets: &[&str] = match owner.id.as_str() {
                READONLY => &TARGETS,
                SIDE | TOOLTIP => &["1.19.2", "1.19.4"],
                _ => eyre::bail!("unreviewed Disk owner"),
            };
            ensure!(
                seen_owners.insert(owner.id.clone())
                    && owner
                        .supported_targets
                        .iter()
                        .map(String::as_str)
                        .eq(targets.iter().copied())
                    && owner.requires.is_empty(),
                "Disk owner support or independence changed"
            );
            let registered = core
                .features
                .0
                .get(&owner.id)
                .ok_or_else(|| eyre::eyre!("Disk owner not registered"))?;
            ensure!(
                registered.supported_targets == owner.supported_targets
                    && registered.requires == owner.requires,
                "Disk registry and evidence disagree"
            );
        }
        // The independently shared helper follows its real callers rather than
        // making the disk class require Client Manager or ComputerCraft.
        for consumer in ["computercraft", "client_manager"] {
            let definition = core
                .features
                .0
                .get(consumer)
                .ok_or_else(|| eyre::eyre!("consumer not registered"))?;
            ensure!(
                definition.requires.iter().any(|id| id == READONLY),
                "Disk readonly consumer prerequisite missing: {consumer}"
            );
        }
        let mut seen_oids = BTreeSet::new();
        for row in ledger.raw_variants {
            let pin = GOLDENS
                .iter()
                .find(|pin| pin.oid == row.blob)
                .ok_or_else(|| eyre::eyre!("unreviewed Disk raw blob"))?;
            let contexts = WITNESSES
                .iter()
                .filter(|(_, oid)| *oid == row.blob)
                .map(|(cell, _)| (*cell).to_owned())
                .collect::<BTreeSet<_>>();
            ensure!(
                seen_oids.insert(row.blob.clone())
                    && row.sha256 == pin.digest
                    && row.bytes == pin.bytes
                    && row.cr_count == 0
                    && row.lf_count == pin.lf
                    && row.final_lf
                    && row.contexts.len() == contexts.len()
                    && row.contexts.into_iter().collect::<BTreeSet<_>>() == contexts,
                "Disk raw identity or context support changed"
            );
        }
        let raw = read_git_blobs(
            &core.repository,
            &GOLDENS.iter().map(|pin| pin.oid.to_owned()).collect(),
        )?;
        for pin in &GOLDENS {
            verify_raw(&raw[pin.oid], pin)?;
        }
        let inventory = discover_core_source_files(&core.core)?;
        ensure!(
            inventory.contains(PATH),
            "Disk template must be promoted before registration; no staged fallback"
        );
        Ok(Self {
            core,
            inventory,
            raw,
        })
    }
    fn context(&self, target: &str, requested: &[&str]) -> Result<ProjectionContext> {
        self.core.context(target, requested)
    }
    fn render(&self, context: &ProjectionContext) -> Result<Vec<u8>> {
        let selected = self
            .core
            .selection_for_assertion(context, &self.inventory)?;
        let input = selected
            .inputs
            .get(PATH)
            .ok_or_else(|| eyre::eyre!("released disk class omitted"))?;
        ensure!(
            input.input == PATH && !selected.omitted_paths.contains(PATH),
            "Disk gained an alternate or optional input"
        );
        let bytes = self.core.read_source(PATH)?;
        verify_template(&bytes)?;
        Ok(render_java_source(std::str::from_utf8(&bytes)?, context)?.into_bytes())
    }
    fn partial_golden(&self, target: &str, context: &ProjectionContext) -> Result<Vec<u8>> {
        let mut expected = String::from_utf8(self.raw[witness(&format!("dev/{target}"))?].clone())?;
        if !context.features[READONLY] && !context.features[PC] {
            replace_unique(&mut expected, &readonly_block(target, false), "")?;
        }
        if !context.features[READONLY] {
            replace_unique(&mut expected, &readonly_block(target, true), "")?;
            if matches!(target, "1.21.0" | "1.21.1" | "26.1.2") {
                replace_unique(
                    &mut expected,
                    "import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;\n",
                    "",
                )?;
            }
        }
        if matches!(target, "1.19.2" | "1.19.4") {
            if !context.features[SIDE] {
                replace_unique(&mut expected, NEW_BUILD, OLD_BUILD)?;
            }
            if !context.features[TOOLTIP] {
                replace_unique(&mut expected, NEW_NAME, OLD_NAME)?;
                replace_unique(&mut expected, NEW_PREDICATE, OLD_PREDICATE)?;
                replace_unique(
                    &mut expected,
                    "import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;\n",
                    "import ca.teamdman.sfm.client.registry.SFMKeyMappings;\nimport ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;\n",
                )?;
                replace_unique(
                    &mut expected,
                    "import ca.teamdman.sfm.common.util.SFMItemUtils;\n",
                    "import ca.teamdman.sfm.common.util.SFMEnvironmentUtils;\nimport ca.teamdman.sfm.common.util.SFMItemUtils;\n",
                )?;
            }
        }
        Ok(expected.into_bytes())
    }
}
const OLD_BUILD: &str = "        new ProgramBuilder(programString).build()\n";
const NEW_BUILD: &str = "        ProgramBuilder builder = new ProgramBuilder(programString);\n        if (manager != null) {\n            builder.forExecutionSide(ca.teamdman.sfml.ast.ProgramExecutionSide.SERVER);\n        }\n        builder.build()\n";
const OLD_NAME: &str = "        if (SFMEnvironmentUtils.isClient()) {\n            if (SFMKeyMappings.isKeyDown(SFMKeyMappings.MORE_INFO_TOOLTIP_KEY))\n                return super.getName(stack);\n        }\n";
const NEW_NAME: &str =
    "        if (SFMItemUtils.isClientAndMoreInfoRequested()) return super.getName(stack);\n";
const OLD_PREDICATE: &str =
    "        if (SFMItemUtils.isClientAndMoreInfoKeyPressed() && !program.isEmpty()) {\n";
const NEW_PREDICATE: &str =
    "        if (SFMItemUtils.isClientAndMoreInfoRequested() && !program.isEmpty()) {\n";
fn readonly_block(target: &str, name: bool) -> String {
    let component = matches!(target, "1.21.0" | "1.21.1" | "26.1.2");
    let field = if name { "program name" } else { "program" };
    let creation = if component { "data" } else { "an NBT tag" };
    let annotation = if component {
        "    @MCVersionDependentBehaviour // 1.21+ stores program data in item components\n"
    } else {
        ""
    };
    let method = if name {
        "getProgramNameReadOnly"
    } else {
        "getProgramStringReadOnly"
    };
    let body = if component {
        if name {
            "        return getProgramName(stack);\n"
        } else if target == "26.1.2" {
            "        return stack.getOrDefault(SFMDataComponents.PROGRAM_STRING.get(), \"\");\n"
        } else {
            "        return getProgramString(stack);\n"
        }
    } else if name {
        "        var tag = stack.getTag();\n        return tag == null ? \"\" : tag.getString(\"sfm:name\");\n"
    } else {
        "        var tag = stack.getTag();\n        return tag == null ? \"\" : tag.getString(\"sfm:program\");\n"
    };
    format!(
        "    /**\n     * Reads the stored {field} without creating {creation} on an otherwise blank disk.\n     */\n{annotation}    public static String {method}(ItemStack stack) {{\n\n{body}    }}\n\n"
    )
}
fn replace_unique(text: &mut String, old: &str, new: &str) -> Result<()> {
    ensure!(
        text.matches(old).count() == 1,
        "owned disk member has no unique raw witness"
    );
    *text = text.replacen(old, new, 1);
    Ok(())
}
fn witness(context: &str) -> Result<&'static str> {
    WITNESSES
        .iter()
        .find(|(cell, _)| *cell == context)
        .map(|(_, oid)| *oid)
        .ok_or_else(|| eyre::eyre!("unreviewed Disk cell"))
}
fn verify_raw(bytes: &[u8], pin: &Golden) -> Result<()> {
    ensure!(
        bytes.len() == pin.bytes
            && sha256(bytes) == pin.digest
            && !bytes.contains(&b'\r')
            && bytes.iter().filter(|byte| **byte == b'\n').count() == pin.lf
            && bytes.ends_with(b"\n"),
        "Disk raw bytes changed; normalization not approved"
    );
    std::str::from_utf8(bytes)?;
    Ok(())
}
#[derive(Facet)]
struct ConsumerRefinementReceipt {
    schema: String,
    status: String,
    historical_ledger_sha256: String,
    historical_ledger_bytes: usize,
    historical_disk_template_sha256: String,
    historical_disk_template_bytes: usize,
    current_disk_template_sha256: String,
    current_disk_template_bytes: usize,
    changed_member: String,
    old_guard: String,
    new_guard: String,
    feature_registry_changed: bool,
    support_changed: bool,
    prerequisites_changed: bool,
    original_raw_witnesses_preserved: bool,
}
fn validate_consumer_refinement(core: &CoreTestFixture) -> Result<()> {
    let old_ledger = read_bounded(&checked_file(&core.repository, LEDGER)?, 1024 * 1024)?;
    let receipt: ConsumerRefinementReceipt =
        facet_json::from_str(std::str::from_utf8(&read_bounded(
            &checked_file(&core.repository, REFINEMENT_LEDGER)?,
            1024 * 1024,
        )?)?)?;
    ensure!(
        receipt.schema == "sfm:core-text-provider-disk-consumer-slice@2"
            && receipt.status == "ignored_stage_pending_root_review_and_promotion"
            && receipt.historical_ledger_sha256
                == "sha256:a9637f3e6adfa6c6178726f30f33dae87891549acd6f9f5643ba78258adfe2dc"
            && receipt.historical_ledger_bytes == 12854
            && old_ledger.len() == receipt.historical_ledger_bytes
            && sha256(&old_ledger) == receipt.historical_ledger_sha256
            && receipt.historical_disk_template_sha256 == TEMPLATE_SHA
            && receipt.historical_disk_template_bytes == TEMPLATE_BYTES
            && receipt.current_disk_template_sha256 == CURRENT_TEMPLATE_SHA
            && receipt.current_disk_template_bytes == CURRENT_TEMPLATE_BYTES
            && receipt.changed_member == "DiskItem.getProgramStringReadOnly"
            && receipt.old_guard == OLD_PROGRAM_GUARD
            && receipt.new_guard == NEW_PROGRAM_GUARD
            && !receipt.feature_registry_changed
            && !receipt.support_changed
            && !receipt.prerequisites_changed
            && receipt.original_raw_witnesses_preserved,
        "current Disk consumer refinement changed historical evidence or widened ownership"
    );
    let pc = core
        .features
        .0
        .get(PC)
        .ok_or_else(|| eyre::eyre!("missing packet computation owner"))?;
    ensure!(
        pc.supported_targets == ["1.19.2", "1.19.4"]
            && pc.requires == ["packet_values", "runtime_resource_cleanup"],
        "Disk consumer union must not alter packet feature support/prerequisites"
    );
    Ok(())
}
fn verify_template(bytes: &[u8]) -> Result<()> {
    ensure!(
        bytes.len() == CURRENT_TEMPLATE_BYTES
            && sha256(bytes) == CURRENT_TEMPLATE_SHA
            && !bytes.contains(&b'\r')
            && bytes.ends_with(b"\n"),
        "current Disk authored bytes changed"
    );
    let current = std::str::from_utf8(bytes)?;
    ensure!(
        current.matches(NEW_PROGRAM_GUARD).count() == 1
            && current.matches(OLD_PROGRAM_GUARD).count() == 2,
        "Disk consumer union may change only the one program getter guard"
    );
    let previous = current.replacen(NEW_PROGRAM_GUARD, OLD_PROGRAM_GUARD, 1);
    ensure!(
        previous.len() == TEMPLATE_BYTES && sha256(previous.as_bytes()) == TEMPLATE_SHA,
        "current Disk source differs beyond the approved one-guard refinement"
    );
    Ok(())
}
fn validate_shared_rule(metadata: &CoreProjectInputs) -> Result<()> {
    if let Some(rules) = metadata.source_rules.get(PATH) {
        ensure!(
            rules.len() == 1
                && rules[0].input == PATH
                && rules[0].when.targets.is_empty()
                && rules[0].when.all_features.is_empty()
                && rules[0].when.any_features.is_empty()
                && rules[0].when.none_features.is_empty(),
            "Disk must remain shared, not gated as a whole unreleased feature"
        );
    }
    Ok(())
}

#[test]
fn disk_template_matches_twenty_exact_raw_witnessed_contexts() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut cells = 0;
    for (cell, _) in COMMITS {
        let (environment, target) = cell.split_once('/').expect("fixed context");
        let requested: &[&str] = if environment == "release" {
            &[]
        } else if matches!(target, "1.19.2" | "1.19.4") {
            &OWNERS
        } else {
            &[READONLY]
        };
        let mut context = fixture.context(target, requested)?;
        context.environment = environment.to_owned();
        context.projection_key = format!(
            "{}/mc-{target}",
            if environment == "dev" {
                "sfm-dev"
            } else {
                "sfm-4.34.0"
            }
        );
        context.preset.clone_from(&context.projection_key);
        let actual = fixture.render(&context)?;
        assert_eq!(
            actual,
            fixture.raw[witness(cell)?],
            "wrong Disk output {cell}"
        );
        assert!(!std::str::from_utf8(&actual)?.contains("{%"));
        cells += 1;
    }
    assert_eq!(cells, 20);
    Ok(())
}

#[test]
fn disk_optional_member_masks_are_independent_and_exact() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut cells = 0;
    for target in ["1.19.2", "1.19.4"] {
        for mask in 0_u8..8 {
            let owners = OWNERS
                .iter()
                .enumerate()
                .filter(|(index, _)| mask & (1_u8 << *index) != 0)
                .map(|(_, id)| *id)
                .collect::<Vec<_>>();
            let context = fixture.context(target, &owners)?;
            assert_eq!(
                fixture.render(&context)?,
                fixture.partial_golden(target, &context)?,
                "wrong independent Disk mask {target}/{mask}"
            );
            cells += 1;
        }
    }
    for target in &TARGETS[2..] {
        for enabled in [false, true] {
            let context = fixture.context(target, if enabled { &[READONLY] } else { &[] })?;
            assert_eq!(
                fixture.render(&context)?,
                fixture.partial_golden(target, &context)?,
                "wrong older-target readonly mask {target}/{enabled}"
            );
            cells += 1;
        }
    }
    assert_eq!(cells, 32);
    Ok(())
}

#[test]
fn disk_released_membership_and_version_apis_stay_unchanged() -> Result<()> {
    let fixture = Fixture::load()?;
    for target in TARGETS {
        let context = fixture.context(target, &[])?;
        let baseline = String::from_utf8(fixture.render(&context)?)?;
        assert!(!baseline.contains("ReadOnly"));
        assert!(!baseline.contains("isClientAndMoreInfoRequested"));
        assert!(!baseline.contains("ProgramExecutionSide"));
        assert!(baseline.contains(OLD_BUILD));
        assert!(baseline.contains(OLD_NAME));
        assert!(baseline.contains(OLD_PREDICATE));
        assert!(baseline.contains("import ca.teamdman.sfm.client.registry.SFMKeyMappings;"));
        assert!(baseline.contains("import ca.teamdman.sfm.common.util.SFMEnvironmentUtils;"));
        assert_eq!(
            baseline.contains(".tab(SFMCreativeTabs.MAIN)"),
            target == "1.19.2"
        );
        if matches!(target, "1.21.0" | "1.21.1" | "26.1.2") {
            assert!(!baseline.contains(".getOrCreateTag()"));
            assert!(baseline.contains("stack.remove(SFMDataComponents.PROGRAM_STRING);"));
        } else {
            assert!(baseline.contains(".getOrCreateTag()"));
            assert!(baseline.contains("stack.removeTagKey(key);"));
        }
        if target == "1.21.0" {
            assert_eq!(context.minecraft_version, "1.21");
        }
        if target == "26.1.2" {
            assert!(baseline.contains("implements TooltipProvider"));
            assert!(baseline.contains("public void addToTooltip("));
            assert!(baseline.contains("return InteractionResult.SUCCESS;"));
            assert!(!baseline.contains("public void appendHoverText("));
        } else {
            assert!(baseline.contains("public void appendHoverText("));
            assert!(baseline.contains("InteractionResultHolder.sidedSuccess"));
        }
        let readonly_context = fixture.context(target, &[READONLY])?;
        let readonly = String::from_utf8(fixture.render(&readonly_context)?)?;
        assert!(readonly.contains(&readonly_block(target, false)));
        assert!(readonly.contains(&readonly_block(target, true)));
        assert_eq!(
            readonly.contains("MCVersionDependentBehaviour"),
            matches!(target, "1.21.0" | "1.21.1" | "26.1.2")
        );
        if !matches!(target, "1.19.2" | "1.19.4") {
            for owner in [SIDE, TOOLTIP] {
                assert!(fixture.context(target, &[owner]).is_err());
            }
        }
    }
    Ok(())
}

#[test]
fn disk_shared_edit_reaches_ten_targets_only_in_isolated_tree() -> Result<()> {
    let fixture = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let core = temp.path().join(super::core_inputs::CORE_ROOT);
    let destination = core.join(PATH);
    fs::create_dir_all(destination.parent().expect("fixed parent"))?;
    let original = fixture.core.read_source(PATH)?;
    let mut edited = original.clone();
    edited.extend_from_slice(b"// isolated shared disk edit\n");
    fs::write(&destination, &edited)?;
    let inventory = discover_core_source_files(&core)?;
    let mut metadata = fixture.core.metadata.clone();
    metadata
        .source_rules
        .retain(|output, _| inventory.contains(output));
    metadata.project_files.clear();
    for target in TARGETS {
        let context = fixture.context(target, &[])?;
        let selected = select_core_inputs(&metadata, &context, &inventory)?;
        assert_eq!(selected.inputs[PATH].input, PATH);
        let bytes = read_bounded(
            &checked_file(&core, &selected.inputs[PATH].input)?,
            1024 * 1024,
        )?;
        let actual = render_java_source(std::str::from_utf8(&bytes)?, &context)?;
        let mut expected = fixture.render(&context)?;
        expected.extend_from_slice(b"// isolated shared disk edit\n");
        assert_eq!(actual.as_bytes(), expected);
    }
    assert_eq!(fixture.core.read_source(PATH)?, original);
    Ok(())
}

#[test]
fn disk_unknown_owner_mutation_or_whole_file_gating_refuses() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut template = fixture.core.read_source(PATH)?;
    template.push(b' ');
    assert!(verify_template(&template).is_err());
    let pin = &GOLDENS[0];
    let mut raw = fixture.raw[pin.oid].clone();
    raw[0] = b'X';
    assert!(verify_raw(&raw, pin).is_err());
    let mut metadata = fixture.core.metadata.clone();
    metadata.source_rules.insert(
        PATH.to_owned(),
        vec![super::core_inputs::InputVariant {
            input: PATH.to_owned(),
            when: super::core_inputs::InputPredicate {
                targets: vec![],
                all_features: vec![READONLY.to_owned()],
                any_features: vec![],
                none_features: vec![],
            },
            template: false,
        }],
    );
    assert!(validate_shared_rule(&metadata).is_err());
    let bytes = fixture.core.read_source(PATH)?;
    for owner in OWNERS {
        let mut context = fixture.context("1.19.2", &[])?;
        context.features.remove(owner);
        assert!(render_java_source(std::str::from_utf8(&bytes)?, &context).is_err());
    }
    Ok(())
}

#[test]
fn disk_program_getter_consumer_union_is_exact_on_four_independent_d2_masks() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut cases = 0;
    for target in ["1.19.2", "1.19.4"] {
        for readonly in [false, true] {
            for packet in [false, true] {
                let mut flags = Vec::new();
                if readonly {
                    flags.push(READONLY);
                }
                if packet {
                    flags.extend(PC_FLAGS);
                }
                let context = fixture.context(target, &flags)?;
                let output = fixture.render(&context)?;
                assert_eq!(output, fixture.partial_golden(target, &context)?);
                let text = std::str::from_utf8(&output)?;
                assert_eq!(
                    text.contains(&readonly_block(target, false)),
                    readonly || packet
                );
                assert_eq!(text.contains(&readonly_block(target, true)), readonly);
                assert!(
                    !text.contains(
                        "import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;"
                    )
                );
                assert!(!text.contains("ProgramExecutionSide"));
                assert!(text.contains(OLD_NAME) && text.contains(OLD_PREDICATE));
                assert_eq!(
                    text.matches("getProgramStringReadOnly(ItemStack stack)")
                        .count(),
                    usize::from(readonly || packet)
                );
                cases += 1;
            }
        }
    }
    assert_eq!(cases, 8);
    Ok(())
}
#[test]
fn disk_program_getter_union_refuses_unsupported_or_missing_packet_owner_without_rewriting_v1()
-> Result<()> {
    let fixture = Fixture::load()?;
    let mut refused = 0;
    for target in &TARGETS[2..] {
        assert!(fixture.context(target, &PC_FLAGS).is_err());
        refused += 1;
    }
    assert_eq!(refused, 8);
    for target in ["1.19.2", "1.19.4"] {
        assert!(fixture.context(target, &[PC]).is_err());
        let mut context = fixture.context(target, &PC_FLAGS)?;
        assert_eq!(context.features.remove(PC), Some(true));
        let current = fixture.core.read_source(PATH)?;
        assert!(render_java_source(std::str::from_utf8(&current)?, &context).is_err());
    }
    // All v1 contract checks, nine raw blobs, twenty witnesses and original32
    // independent masks are retained in the unchanged pre-existing test bodies.
    validate_consumer_refinement(&fixture.core)?;
    Ok(())
}
