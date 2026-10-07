//! Prepared actual-core regressions for twenty-six original terminal model providers.
//! Root must atomically promote the exact sources, sparse rules, two pure error
//! selector refinements and portable ledger before registering these tests.
//! V2 validates the exact current Service/Scrollback properties consumer unions
//! first, then reverses ONLY those two rules in this isolated fixture to retain
//! immutable v1 historical goldens. These reconstructed rows are not current
//! properties selection proof: the presentation-provider tests exercise that
//! current collector and all four real properties-only carriers separately.
//! Git is offline evidence only, never production source routing. No test here
//! establishes Java types, endpoint/session admission, actor consent, GL, terminal
//! backend effects or native/runtime compatibility. Authored and unexecuted.
#![cfg(test)]

use super::candidate_lock::checked_file;
use super::context::ProjectionContext;
use super::core_inputs::CORE_ROOT;
use super::core_inputs::CoreProjectInputs;
use super::core_inputs::InputVariant;
use super::core_inputs::collect_core_artifacts;
use super::core_inputs::discover_core_source_files;
use super::core_inputs::select_core_inputs;
use super::core_slice_test_support::CoreTestFixture;
use super::core_slice_test_support::historical_feature_registry;
use super::core_slice_test_support::read_bounded;
use super::core_slice_test_support::read_git_blobs;
use super::provenance::sha256;
use super::release_baseline::frozen_git_command;
use super::render_java_source;
use eyre::Result;
use eyre::ensure;
use facet::Facet;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::fs;
use std::path::Path;

const LEDGER: &str = "docs/tasks/sfm-core-terminal-model-providers-slice.json";
const PREFIX: &str = "src/main/java/ca/teamdman/sfm/client/terminal/";
const D2: [&str; 2] = ["1.19.2", "1.19.4"];
const TEN: [&str; 10] = [
    "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
    "26.1.2",
];
const NAMES: [&str; 26] = [
    "SFMTerminalConnectionSnapshot",
    "SFMTerminalPropertiesSnapshot",
    "SFMTerminalPresentationAdvertisedMode",
    "SFMTerminalPresentationCatalog",
    "SFMTerminalPresentationChangeResult",
    "SFMTerminalPresentationDiagnostics",
    "SFMTerminalPresentationModeOption",
    "SFMTerminalPresentationSelection",
    "SFMTerminalPresentationTransitionState",
    "SFMTerminalPresentationTuple",
    "SFMTerminalPresentationUnavailable",
    "SFMTerminalRendererId",
    "SFMTerminalRendererOption",
    "SFMTerminalTransportOption",
    "SFMTerminalTuningChangeResult",
    "SFMTerminalTuningOperation",
    "SFMTerminalTuningRejection",
    "SFMTerminalTuningSettings",
    "SFMTerminalRasterizationOwner",
    "SFMTerminalRasterFrameValidator",
    "SFMTerminalRgbaCompositor",
    "SFMTerminalPngResourceState",
    "SFMTerminalPngTelemetry",
    "SFMTerminalScrollback",
    "SFMVoxTerminalTelemetry",
    "SFMTerminalService",
];
const CONTEXTS: [(&str, &str); 20] = [
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
const RAW: [(&str, usize, &str); 29] = [
    (
        "6ae1d380b086db44e40abbb81450077da892aa94",
        474,
        "sha256:b0b96e250ea05e6da2556804da09d6299eac08c83b1a519004b19ade29756eb5",
    ),
    (
        "0e8a91f9e3390e7c08fc9973e3cd5f71ea50e306",
        16106,
        "sha256:b64e29f0c23807f948124dc0669b5f9dfbf3f24ba3d10aa5bae8a3214c918c79",
    ),
    (
        "060eecf2201b825aabdb541f74cfb190fb50de44",
        1469,
        "sha256:e492d0d4911170831bdb93b126d03eb543028067a3037a744369c224d52ed7a6",
    ),
    (
        "232eb24bcc4a8d59f2abf14f9cdf5f372ee97db1",
        12861,
        "sha256:3c9fd8300d495febc7bbe82b9cf76bb88dec7be512d74bcd9d4d5c9343c42783",
    ),
    (
        "cd3b587cc6420756e1a1f8ff2825328f138d4d59",
        638,
        "sha256:bd1175111e172f1442f4e52647d234446725ea05026b8f632383ffcea3ad99e0",
    ),
    (
        "714229db6be44037a74c867a716fed5bd0d865ae",
        1076,
        "sha256:17d5ef339201818ba5d1ac5e0508755d274cfdfa85e88c64ecca9b7628f4129a",
    ),
    (
        "b392417782c155e98cbaa23e076eefeae36c122e",
        1190,
        "sha256:db9323b477de3dff6dd1246c923a678affb4338dab0420f0e1ddb1dff988089b",
    ),
    (
        "a746f658051e5427973e97c997f36a9ba0a00cc2",
        1146,
        "sha256:a092ee8f6b7011aa0d0f08c99b2f47ebabfc3ca12884b2c1ba4b82679342fd50",
    ),
    (
        "e4a2e3d88eba2fefbbd20b55fa9e24d6420fb6a6",
        728,
        "sha256:012333f7cad0879951fb701d2f377de99d00eb3ed5102003f40aaac2bbcc181f",
    ),
    (
        "1471c31755a3151f86366d8839f9389c051db4fe",
        1069,
        "sha256:770ca2d4f07b27e73c9bc90775cd08925166d40754ec62e3c8d952aac234886a",
    ),
    (
        "aec95be8ef46a8e065c45db9b7dfa454e29443d0",
        808,
        "sha256:339079ace852a06b233edf740cd8537e9866531830109e698b0a41c6e33ae9a0",
    ),
    (
        "c3de8988fba2923deedbc94275efe9c7b9c77403",
        1254,
        "sha256:69c6709444064a9750a1b457e717addf3adb7561defe19583510a76bc86d3075",
    ),
    (
        "53d9388d69b18739d94bdca9a5bc7dd71c34dd4e",
        771,
        "sha256:49a41d06273798c894b4969199baa006984cbd9589a21a264756e0444d8e725c",
    ),
    (
        "a1d2b9cfcef7685799e48302e9f26cae82422dd1",
        598,
        "sha256:bcf6fc18dc7adb10265a67451acef49c1b86d2b10a6462c1ba2cbdbefd7bf644",
    ),
    (
        "39415571987c0be0b918178dccf3df1804eed522",
        1116,
        "sha256:753ab9ae4ee982196f1272c98b8986d37603bd2e659151e051679f5b406d1961",
    ),
    (
        "9345d4b5f33acea8114dc7f8c0da65d396faf4dd",
        3090,
        "sha256:af7ace87dca866e613ed8ca396f698726491921920bdec26068632f8b2cf7bdf",
    ),
    (
        "b3b9a8c442d2f5f6e1d1a20ff50fc82ca03497d9",
        914,
        "sha256:c7f08d357d09b1cd134b3f6e0bf8fc938f30cf1ff750022b0143d13e05542e48",
    ),
    (
        "9a46c3faecbaa79567c82564c5084e5a295fd648",
        4008,
        "sha256:fca2dfca36e83859730b7f23f55f30bde484055fcc0a2a503aa1feaca249b287",
    ),
    (
        "ad28681227ef07c1ec3ec79ca76ac6e564efe326",
        772,
        "sha256:403a40726e74ad75df7133c9be6c2b6468b5105519d7266f751e1571e8c7649f",
    ),
    (
        "dd2c0de14e75ae67dbd8532777037ece79e28f7a",
        9431,
        "sha256:92d4cfd4c5595aa69303e2905046ced6cd6b57c80945aefe6f8b48fe29ae1466",
    ),
    (
        "7a7c0e37a67d5844121e01c89663e726ed63a37e",
        6228,
        "sha256:56d0fa5225ba0e5e4642c53ba5365a2992b1a883cbfc1cde565408c6742beee6",
    ),
    (
        "20efdc8cd0e8777090f83c606c07a14c340cfc44",
        2167,
        "sha256:38f374d50dd25fe97a9faacbdbb0f5c22361d7e3624e948f14bea0548178bf15",
    ),
    (
        "eb76c3f68f9adc2c91d7bab8da4685f940dda70f",
        8590,
        "sha256:6750aeb2b7de9560e8f9e55a8713ba0404fe7c3cd6cee4b0c32191f0891050b9",
    ),
    (
        "e0caa13fb14e1ad1250db6197d881a7cb5f8377e",
        5599,
        "sha256:7ea64b0c1d3e6406a6fb6daab8e1b441e55e18e8828f1a0060aa4f3c6f38a84a",
    ),
    (
        "70bc19dcb3b7c4f0e033fe30121cc60ea74a8667",
        8082,
        "sha256:e829848c2318ad095078775c5dc03c03c0e858826ea5c4aec12c9a8c54c05f05",
    ),
    (
        "47052c952a040b4d50a584fa93ca448736eb6445",
        338,
        "sha256:aad57cff7c746953fd9a537f8e94bb0050a5690bce63ed9a4529500a056667e6",
    ),
    (
        "935533693c01e03fdd9b5b273b659f500db10713",
        344,
        "sha256:b22817f909620c0b11755833b2930b9e5c73f0cd9a8f930eacf418c130a70b6c",
    ),
    (
        "824533a3908254254cb1e85c53926a78781014c3",
        452,
        "sha256:9f8cf05197d9ccef4d62648759e9525c9ba4868801ee3ea953c987585e73e660",
    ),
    (
        "6699803de6099ca8db4e25af354ca9b13fff1326",
        295,
        "sha256:ec05d9eb1b6f8bfc9a68d0b84df878a3a7850ea0535af81e075b27b69d63897d",
    ),
];
const DEFINITIONS: [(&str, &[&str], &[&str]); 9] = [
    (
        "terminal_local",
        &[
            "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
            "26.1.2",
        ],
        &[],
    ),
    (
        "terminal_remote",
        &[
            "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
            "26.1.2",
        ],
        &[],
    ),
    (
        "terminal_vox_runtime",
        &[
            "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
            "26.1.2",
        ],
        &[],
    ),
    (
        "terminal_properties",
        &["1.19.2", "1.19.4"],
        &["workspace_panels"],
    ),
    (
        "terminal_tuning_actions",
        &["1.19.2", "1.19.4"],
        &["client_actions", "workspace_panels", "terminal_remote"],
    ),
    (
        "terminal_presentation_actions",
        &["1.19.2", "1.19.4"],
        &["client_actions", "workspace_panels", "terminal_remote"],
    ),
    ("terminal_frame_metadata", &["1.19.2", "1.19.4"], &[]),
    (
        "workspace_panels",
        &[
            "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
            "26.1.2",
        ],
        &[],
    ),
    (
        "client_actions",
        &[
            "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
            "26.1.2",
        ],
        &[],
    ),
];
const ALL: [&str; 8] = [
    "client_actions",
    "workspace_panels",
    "terminal_remote",
    "terminal_vox_runtime",
    "terminal_properties",
    "terminal_tuning_actions",
    "terminal_presentation_actions",
    "terminal_frame_metadata",
];
const ORIGINAL_ERRORS: [&str; 3] = [
    "terminal_vox_runtime",
    "terminal_tuning_actions",
    "terminal_presentation_actions",
];
const CURRENT_ERRORS: [&str; 5] = [
    "terminal_vox_runtime",
    "terminal_tuning_actions",
    "terminal_presentation_actions",
    "terminal_remote",
    "terminal_properties",
];

#[derive(Facet)]
struct Ledger {
    schema: String,
    normalization: String,
    contract_changes: bool,
    definitions: BTreeMap<String, Definition>,
    context_commits: BTreeMap<String, String>,
    files: Vec<SourceEvidence>,
    existing_provider_refinements: Vec<ProviderEvidence>,
}
#[derive(Facet)]
struct Definition {
    supported_targets: Vec<String>,
    requires: Vec<String>,
}
#[derive(Facet)]
struct RawEvidence {
    oid: String,
    bytes: usize,
    sha256: String,
}
#[derive(Facet)]
struct SourceEvidence {
    path: String,
    owners: Vec<String>,
    source_rule: InputVariant,
    raw_variants: Vec<RawEvidence>,
    witnesses: BTreeMap<String, Option<String>>,
    authored_bytes: usize,
    authored_sha256: String,
    cr_count: usize,
    lf_count: usize,
    final_lf: bool,
}
#[derive(Facet)]
struct ProviderEvidence {
    path: String,
    raw_oid: String,
    raw_bytes: usize,
    raw_sha256: String,
    original_rule: InputVariant,
    proposed_rule: InputVariant,
    witnesses: BTreeMap<String, Option<String>>,
    java_body_unchanged: bool,
}
struct Fixture {
    core: CoreTestFixture,
    ledger: Ledger,
    sources: BTreeMap<String, Vec<u8>>,
    raw: BTreeMap<String, Vec<u8>>,
}
fn path(name: &str) -> String {
    format!("{PREFIX}{name}.java")
}
fn owners(name: &str) -> Vec<&'static str> {
    match name {
        "SFMTerminalService" | "SFMTerminalScrollback" => {
            vec!["terminal_local", "terminal_remote", "terminal_vox_runtime"]
        }
        "SFMTerminalConnectionSnapshot"
        | "SFMTerminalRasterFrameValidator"
        | "SFMTerminalRgbaCompositor"
        | "SFMTerminalPngResourceState"
        | "SFMTerminalPngTelemetry" => vec!["terminal_remote", "terminal_vox_runtime"],
        "SFMVoxTerminalTelemetry" => vec!["terminal_vox_runtime"],
        "SFMTerminalPresentationDiagnostics" => vec![
            "terminal_remote",
            "terminal_vox_runtime",
            "terminal_properties",
        ],
        "SFMTerminalPropertiesSnapshot"
        | "SFMTerminalTuningChangeResult"
        | "SFMTerminalTuningOperation"
        | "SFMTerminalTuningRejection"
        | "SFMTerminalTuningSettings" => vec![
            "terminal_remote",
            "terminal_vox_runtime",
            "terminal_tuning_actions",
            "terminal_properties",
        ],
        _ => vec![
            "terminal_remote",
            "terminal_vox_runtime",
            "terminal_presentation_actions",
        ],
    }
}
fn shared(name: &str) -> bool {
    matches!(name, "SFMTerminalService" | "SFMTerminalScrollback")
}
fn profiles() -> Vec<Vec<&'static str>> {
    vec![
        vec![],
        vec!["terminal_local"],
        vec!["terminal_remote"],
        vec!["terminal_vox_runtime"],
        vec!["workspace_panels", "terminal_properties"],
        vec![
            "client_actions",
            "workspace_panels",
            "terminal_remote",
            "terminal_presentation_actions",
        ],
        vec![
            "client_actions",
            "workspace_panels",
            "terminal_remote",
            "terminal_tuning_actions",
        ],
        ALL.to_vec(),
    ]
}
fn verify_raw(bytes: &[u8], oid: &str) -> Result<()> {
    let (_, size, digest) = RAW
        .iter()
        .find(|(raw, _, _)| *raw == oid)
        .ok_or_else(|| eyre::eyre!("unreviewed raw terminal object"))?;
    ensure!(
        bytes.len() == *size
            && sha256(bytes) == *digest
            && !bytes.contains(&b'\r')
            && bytes.ends_with(b"\n")
            && !bytes.starts_with(&[0xef, 0xbb, 0xbf]),
        "terminal raw witness bytes changed"
    );
    std::str::from_utf8(bytes)?;
    Ok(())
}
fn properties_without_from_frame(raw: &str) -> Result<String> {
    let start = raw
        .find("    public static AcceptedFrame fromFrame(")
        .ok_or_else(|| eyre::eyre!("raw fromFrame start missing"))?;
    let end = raw[start..]
        .find("    public String lastRejection()")
        .map(|offset| start + offset)
        .ok_or_else(|| eyre::eyre!("raw fromFrame end missing"))?;
    Ok(format!("{}{}", &raw[..start], &raw[end..]))
}
impl Fixture {
    fn load() -> Result<Self> {
        let mut core = CoreTestFixture::load()?;
        let (_, historical_features) = historical_feature_registry()?;
        let ledger: Ledger = facet_json::from_str(std::str::from_utf8(&read_bounded(
            &checked_file(&core.repository, LEDGER)?,
            256 * 1024,
        )?)?)?;
        ensure!(
            ledger.schema == "sfm:core-terminal-model-providers-slice@1"
                && ledger.normalization == "none"
                && !ledger.contract_changes
                && ledger.files.len() == 26
                && ledger.existing_provider_refinements.len() == 2
                && ledger.definitions.len() == 9
                && historical_features.0.len() == 180
                && ledger.context_commits
                    == CONTEXTS
                        .into_iter()
                        .map(|(name, commit)| (name.to_owned(), commit.to_owned()))
                        .collect(),
            "terminal model scope/contracts changed"
        );
        for (name, support, requires) in DEFINITIONS {
            let old = &ledger.definitions[name];
            let actual = &core.features.0[name];
            ensure!(
                old.supported_targets == support
                    && old.requires == requires
                    && actual.supported_targets == support
                    && actual.requires == requires,
                "original current terminal feature contract changed: {name}"
            );
        }
        let raw = read_git_blobs(
            &core.repository,
            &RAW.iter().map(|(oid, _, _)| (*oid).to_owned()).collect(),
        )?;
        for (oid, _, _) in RAW {
            verify_raw(&raw[oid], oid)?;
        }
        let mut sources = BTreeMap::new();
        for (name, e) in NAMES.iter().zip(&ledger.files) {
            ensure!(
                e.path == path(name)
                    && e.owners == owners(name)
                    && e.raw_variants.len() == if *name == "SFMTerminalService" { 2 } else { 1 }
                    && e.cr_count == 0
                    && e.final_lf,
                "terminal source identity/order changed"
            );
            for variant in &e.raw_variants {
                let (_, size, digest) = RAW
                    .iter()
                    .find(|(oid, _, _)| *oid == variant.oid)
                    .ok_or_else(|| eyre::eyre!("unknown raw terminal variant"))?;
                ensure!(
                    variant.bytes == *size && variant.sha256 == *digest,
                    "raw variant ledger pin changed"
                );
            }
            for (context, _) in CONTEXTS {
                let expected = if context.starts_with("release/")
                    || (!shared(name) && !matches!(context, "dev/1.19.2" | "dev/1.19.4"))
                {
                    None
                } else {
                    Some(
                        e.raw_variants[usize::from(
                            *name == "SFMTerminalService"
                                && !matches!(context, "dev/1.19.2" | "dev/1.19.4"),
                        )]
                        .oid
                        .clone(),
                    )
                };
                ensure!(
                    e.witnesses.get(context) == Some(&expected),
                    "terminal historical twenty-cell witness changed"
                );
            }
            let source = core.read_source(&e.path)?;
            ensure!(
                source.len() == e.authored_bytes
                    && sha256(&source) == e.authored_sha256
                    && source.iter().filter(|byte| **byte == b'\n').count() == e.lf_count
                    && !source.contains(&b'\r')
                    && source.ends_with(b"\n"),
                "root must promote the exact authored terminal source: {}",
                e.path
            );
            if shared(name) {
                let mut current_rule = e.source_rule.clone();
                current_rule
                    .when
                    .any_features
                    .push("terminal_properties".to_owned());
                ensure!(
                    core.metadata.source_rules.get(&e.path) == Some(&vec![current_rule]),
                    "current pure carrier must add only the exact properties consumer"
                );
                // Historical v1 witness reconstruction in this private fixture
                // ONLY. No source/catalog/live metadata write, and no claim that
                // these old counts describe the current properties-only graph.
                core.metadata
                    .source_rules
                    .insert(e.path.clone(), vec![e.source_rule.clone()]);
            }
            let rules = core
                .metadata
                .source_rules
                .get(&e.path)
                .ok_or_else(|| eyre::eyre!("root must promote terminal source rules first"))?;
            ensure!(
                rules == &vec![e.source_rule.clone()]
                    && rules[0].input == e.path
                    && rules[0].template
                    && rules[0].when.targets
                        == if shared(name) {
                            TEN.to_vec()
                        } else {
                            D2.to_vec()
                        }
                    && rules[0].when.all_features.is_empty()
                    && rules[0].when.any_features == owners(name)
                    && rules[0].when.none_features.is_empty(),
                "terminal exact existing-owner rule changed"
            );
            sources.insert(e.path.clone(), source);
        }
        for (name, e) in ["SFMTerminalError", "SFMTerminalErrorCode"]
            .iter()
            .zip(&ledger.existing_provider_refinements)
        {
            ensure!(
                e.path == path(name)
                    && e.java_body_unchanged
                    && e.original_rule.when.any_features == ORIGINAL_ERRORS
                    && e.proposed_rule.when.any_features == CURRENT_ERRORS
                    && e.original_rule.input == e.path
                    && e.proposed_rule.input == e.path
                    && e.original_rule.when.targets == D2
                    && e.proposed_rule.when.targets == D2
                    && e.original_rule.template
                    && e.proposed_rule.template
                    && e.proposed_rule.when.all_features.is_empty()
                    && e.proposed_rule.when.none_features.is_empty(),
                "pure error refinement broadened beyond exact real consumers"
            );
            verify_raw(&raw[&e.raw_oid], &e.raw_oid)?;
            ensure!(
                e.raw_bytes == raw[&e.raw_oid].len() && e.raw_sha256 == sha256(&raw[&e.raw_oid]),
                "pure error raw ledger changed"
            );
            let expected = CONTEXTS
                .into_iter()
                .map(|(name, _)| {
                    (
                        name.to_owned(),
                        matches!(name, "dev/1.19.2" | "dev/1.19.4").then(|| e.raw_oid.clone()),
                    )
                })
                .collect::<BTreeMap<_, _>>();
            ensure!(
                e.witnesses == expected,
                "pure error historical witnesses changed"
            );
            ensure!(
                core.metadata.source_rules.get(&e.path) == Some(&vec![e.proposed_rule.clone()]),
                "root must promote exact pure error selector refinement first"
            );
            let source = core.read_source(&e.path)?;
            ensure!(
                source == raw[&e.raw_oid],
                "pure existing error Java must remain raw exact"
            );
            sources.insert(e.path.clone(), source);
        }
        Ok(Self {
            core,
            ledger,
            sources,
            raw,
        })
    }
    fn inventory(&self) -> BTreeSet<String> {
        self.sources.keys().cloned().collect()
    }
    fn render(&self, path: &str, context: &ProjectionContext) -> Result<Option<String>> {
        let selected = self
            .core
            .selection_for_assertion(context, &self.inventory())?;
        if !selected.inputs.contains_key(path) {
            ensure!(
                selected.omitted_paths.contains(path),
                "terminal omission is unaccounted"
            );
            return Ok(None);
        }
        Ok(Some(render_java_source(
            std::str::from_utf8(&self.sources[path])?,
            context,
        )?))
    }
    fn bounded_metadata(&self) -> CoreProjectInputs {
        let mut metadata = self.core.metadata.clone();
        metadata
            .source_rules
            .retain(|path, _| self.sources.contains_key(path));
        metadata
    }
    fn write_sources(&self, root: &Path) -> Result<()> {
        for (path, bytes) in &self.sources {
            let destination = root.join(path);
            fs::create_dir_all(destination.parent().expect("fixed source parent"))?;
            fs::write(destination, bytes)?;
        }
        Ok(())
    }
    fn copy_project_inputs(
        &self,
        root: &Path,
        metadata: &CoreProjectInputs,
        context: &ProjectionContext,
    ) -> Result<()> {
        let selected = select_core_inputs(metadata, context, &self.inventory())?;
        for (output, input) in &selected.inputs {
            if self.sources.contains_key(output) {
                continue;
            }
            let bytes = read_bounded(
                &checked_file(&self.core.core, &input.input)?,
                16 * 1024 * 1024,
            )?;
            let destination = root.join(&input.input);
            if destination.exists() {
                ensure!(
                    read_bounded(&destination, 16 * 1024 * 1024)? == bytes,
                    "actual standalone fixture input changed between masks"
                );
            } else {
                fs::create_dir_all(destination.parent().expect("standalone input parent"))?;
                fs::write(destination, bytes)?;
            }
        }
        Ok(())
    }
}

#[test]
fn terminal_models_and_pure_errors_match_560_real_frozen_git_cells() -> Result<()> {
    let f = Fixture::load()?;
    let paths = f
        .inventory()
        .iter()
        .map(|path| format!("platform/minecraft/{path}"))
        .collect::<Vec<_>>();
    let mut tree_counts = (0, 0);
    let mut render_counts = (0, 0);
    for (name, commit) in CONTEXTS {
        let output = frozen_git_command(&f.core.repository)
            .env("GIT_ALLOW_PROTOCOL", "")
            .env("GIT_TERMINAL_PROMPT", "0")
            .args([
                "-c",
                "protocol.allow=never",
                "-c",
                "core.fsmonitor=false",
                "ls-tree",
                commit,
                "--",
            ])
            .args(&paths)
            .output()?;
        ensure!(
            output.status.success()
                && output.stdout.len() <= 32 * 1024
                && output.stderr.len() <= 4096,
            "bounded original terminal tree read failed"
        );
        let mut actual = BTreeMap::new();
        for line in std::str::from_utf8(&output.stdout)?.lines() {
            let (header, path) = line
                .split_once('\t')
                .ok_or_else(|| eyre::eyre!("malformed terminal tree row"))?;
            let fields = header.split_whitespace().collect::<Vec<_>>();
            ensure!(
                fields.len() == 3
                    && fields[0] == "100644"
                    && fields[1] == "blob"
                    && paths.iter().any(|expected| expected == path)
                    && actual
                        .insert(path.to_owned(), fields[2].to_owned())
                        .is_none(),
                "unexpected terminal tree row"
            );
        }
        let mut expected = BTreeMap::new();
        for e in &f.ledger.files {
            if let Some(oid) = &e.witnesses[name] {
                expected.insert(format!("platform/minecraft/{}", e.path), oid.clone());
            }
        }
        for e in &f.ledger.existing_provider_refinements {
            if let Some(oid) = &e.witnesses[name] {
                expected.insert(format!("platform/minecraft/{}", e.path), oid.clone());
            }
        }
        assert_eq!(actual, expected, "exact original raw tree: {name}");
        tree_counts.0 += actual.len();
        tree_counts.1 += 28 - actual.len();
        let (_, target) = name.split_once('/').expect("fixed context delimiter");
        let flags = if matches!(name, "dev/1.19.2" | "dev/1.19.4") {
            ALL.to_vec()
        } else if name.starts_with("dev/") {
            vec!["terminal_local", "terminal_remote", "terminal_vox_runtime"]
        } else {
            vec![]
        };
        let context = f.core.context(target, &flags)?;
        for e in &f.ledger.files {
            let body = f.render(&e.path, &context)?;
            if let Some(oid) = &e.witnesses[name] {
                assert_eq!(
                    body.expect("historical terminal provider omitted")
                        .as_bytes(),
                    f.raw[oid]
                );
                render_counts.0 += 1;
            } else {
                assert!(body.is_none());
                render_counts.1 += 1;
            }
        }
    }
    assert_eq!(tree_counts, (72, 488));
    assert_eq!(render_counts, (68, 452));
    for (oid, _, _) in RAW {
        let mut damaged = f.raw[oid].clone();
        damaged[0] ^= 1;
        assert!(verify_raw(&damaged, oid).is_err());
        let mut crlf = f.raw[oid].clone();
        crlf.insert(0, b'\r');
        assert!(verify_raw(&crlf, oid).is_err());
    }
    Ok(())
}

#[test]
fn eight_valid_profiles_preserve_416_new_model_cells_without_description_authority() -> Result<()> {
    let f = Fixture::load()?;
    let expected_counts = [0, 2, 25, 26, 6, 25, 25, 26];
    let mut counts = (0, 0);
    for target in D2 {
        for (index, flags) in profiles().iter().enumerate() {
            let context = f.core.context(target, flags)?;
            let mut descriptive = context.clone();
            descriptive.environment = "release".to_owned();
            descriptive.preset = "terminal-model-description-not-an-owner".to_owned();
            descriptive.projection_key = "independent/terminal-models".to_owned();
            let mut selected = 0;
            for (name, e) in NAMES.iter().zip(&f.ledger.files) {
                let body = f.render(&e.path, &context)?;
                assert_eq!(
                    body.is_some(),
                    owners(name).iter().any(|owner| context.features[*owner])
                );
                assert_eq!(body, f.render(&e.path, &descriptive)?);
                if body.is_some() {
                    selected += 1;
                    counts.0 += 1;
                } else {
                    counts.1 += 1;
                }
            }
            assert_eq!(selected, expected_counts[index]);
        }
    }
    assert_eq!(counts, (270, 146));
    let mut older = (0, 0);
    for target in &TEN[2..] {
        for flags in [
            vec![],
            vec!["terminal_local"],
            vec!["terminal_remote"],
            vec!["terminal_vox_runtime"],
        ] {
            let context = f.core.context(target, &flags)?;
            for (name, e) in NAMES.iter().zip(&f.ledger.files) {
                let present = f.render(&e.path, &context)?.is_some();
                assert_eq!(present, shared(name) && !flags.is_empty());
                if present {
                    older.0 += 1;
                } else {
                    older.1 += 1;
                }
            }
        }
    }
    assert_eq!(older, (48, 784));
    Ok(())
}

#[test]
fn unchanged_feature_contracts_refuse_missing_prerequisites_and_unknown_owner_flags() -> Result<()>
{
    let f = Fixture::load()?;
    let mut refusals = 0;
    for target in D2 {
        for (owner, flags) in [
            ("terminal_properties", profiles()[4].clone()),
            ("terminal_presentation_actions", profiles()[5].clone()),
            ("terminal_tuning_actions", profiles()[6].clone()),
        ] {
            for prerequisite in &f.core.features.0[owner].requires {
                let missing = flags
                    .iter()
                    .copied()
                    .filter(|flag| *flag != prerequisite.as_str())
                    .collect::<Vec<_>>();
                assert!(f.core.context(target, &missing).is_err());
                refusals += 1;
            }
        }
        for owner in [
            "terminal_local",
            "terminal_remote",
            "terminal_vox_runtime",
            "terminal_properties",
            "terminal_tuning_actions",
            "terminal_presentation_actions",
        ] {
            let mut unknown = f.core.context(target, &ALL)?;
            assert!(unknown.features.remove(owner).is_some());
            assert!(select_core_inputs(&f.core.metadata, &unknown, &f.inventory()).is_err());
        }
    }
    assert_eq!(refusals, 14);
    for target in &TEN[2..] {
        for flags in [
            &["terminal_frame_metadata"][..],
            &["workspace_panels", "terminal_properties"][..],
            &[
                "client_actions",
                "workspace_panels",
                "terminal_remote",
                "terminal_presentation_actions",
            ][..],
            &[
                "client_actions",
                "workspace_panels",
                "terminal_remote",
                "terminal_tuning_actions",
            ][..],
        ] {
            assert!(f.core.context(target, flags).is_err());
        }
    }
    Ok(())
}

#[test]
fn two_pure_error_refinements_preserve_old_owners_and_all32_predicate_truth_rows() -> Result<()> {
    let f = Fixture::load()?;
    let mut counts = (0, 0);
    for target in D2 {
        for bits in 0_u32..32 {
            // Predicate algebra only: some rows deliberately violate action
            // prerequisites. They are NOT validated or admitted feature profiles.
            let mut context = f.core.context(target, &[])?;
            for (index, owner) in CURRENT_ERRORS.iter().enumerate() {
                context
                    .features
                    .insert((*owner).to_owned(), bits & (1 << index) != 0);
            }
            for name in ["SFMTerminalError", "SFMTerminalErrorCode"] {
                let present = f.render(&path(name), &context)?.is_some();
                assert_eq!(present, bits != 0);
                if present {
                    counts.0 += 1;
                } else {
                    counts.1 += 1;
                }
            }
            for effect in [
                "terminal_keyboard_input",
                "terminal_clipboard",
                "terminal_paste_confirmation",
                "terminal_server_lifecycle",
                "terminal_vox",
                "packet_transport_private",
                "multiplayer_packets",
                "client_inbox",
                "touch_display_terminal_mount",
            ] {
                assert!(
                    !context.features[effect],
                    "pure owner union enabled effect {effect}"
                );
            }
        }
        let remote = f.core.context(target, &["terminal_remote"])?;
        let properties = f
            .core
            .context(target, &["workspace_panels", "terminal_properties"])?;
        for context in [remote, properties] {
            for name in ["SFMTerminalError", "SFMTerminalErrorCode"] {
                assert!(f.render(&path(name), &context)?.is_some());
            }
        }
    }
    assert_eq!(counts, (124, 4));
    Ok(())
}

#[test]
fn properties_metadata_is_an_independent_method_guard_with_exact_all_on_raw_body() -> Result<()> {
    let f = Fixture::load()?;
    let e = f
        .ledger
        .files
        .iter()
        .find(|e| e.path == path("SFMTerminalPropertiesSnapshot"))
        .expect("fixed properties provider");
    let raw = std::str::from_utf8(&f.raw[&e.raw_variants[0].oid])?;
    let off = properties_without_from_frame(raw)?;
    assert_eq!(off.len(), 15406);
    assert_eq!(
        sha256(off.as_bytes()),
        "sha256:dca88b8ddccd6f845ed098b13e254b7c2fe9286a047c74261a244e1f426fd18e"
    );
    for target in D2 {
        for flags in [
            vec!["terminal_remote"],
            vec!["workspace_panels", "terminal_properties"],
            vec!["terminal_vox_runtime"],
        ] {
            let context = f.core.context(target, &flags)?;
            assert!(!context.features["terminal_frame_metadata"]);
            let body = f
                .render(&e.path, &context)?
                .expect("properties carrier selected by actual owner");
            assert_eq!(body, off);
            for forbidden in [
                "fromFrame(",
                "SFMTerminalFrameMetadata",
                ".metadata()",
                "SFMTerminalFrame frame",
            ] {
                assert!(!body.contains(forbidden));
            }
            // AcceptedFrame is a retained value carrier, not SFMTerminalFrame.
            assert!(body.contains(
                "lines.add(\"properties_accepted_stream_identity=\" + frame.streamIdentity());"
            ));
            assert!(body.contains("public record AcceptedFrame("));
            assert!(body.contains("public String artifact()"));
            let mut enabled = flags.clone();
            enabled.push("terminal_frame_metadata");
            let on = f.core.context(target, &enabled)?;
            assert_eq!(
                f.render(&e.path, &on)?
                    .expect("metadata-on properties")
                    .as_bytes(),
                raw.as_bytes()
            );
        }
    }
    Ok(())
}

#[test]
fn genuine_provider_anchors_and_pure_carriers_do_not_supply_backend_or_effect_acceptance()
-> Result<()> {
    let f = Fixture::load()?;
    for target in D2 {
        let context = f.core.context(target, &ALL)?;
        for (name, anchors) in [
            (
                "SFMTerminalFrame",
                &[
                    "public record SFMTerminalFrame(",
                    "SFMTerminalFrameMetadata metadata",
                    "String streamIdentity",
                ][..],
            ),
            (
                "SFMTerminalFrameMetadata",
                &[
                    "record SFMTerminalFrameMetadata(",
                    "int logicalColumns",
                    "int targetPanelWidth",
                ][..],
            ),
            (
                "SFMTerminalRasterLimits",
                &[
                    "public record SFMTerminalRasterLimits(",
                    "64L * 1024L * 1024L",
                    "RGBA8_V1_MAX_REGIONS = 64",
                ][..],
            ),
            (
                "SFMTerminalTransportId",
                &[
                    "FULL_PNG",
                    "FULL_RAW_RGBA",
                    "DIRTY_RAW_RGBA",
                    "SUPPORTED_VERSION = 1",
                ][..],
            ),
            (
                "SFMTerminalLine",
                &[
                    "public record SFMTerminalLine(",
                    "static SFMTerminalLine plain(",
                ][..],
            ),
            (
                "SFMTerminalResponse",
                &["public final class SFMTerminalResponse", "styledLines"][..],
            ),
        ] {
            let provider = path(name);
            let selection = select_core_inputs(
                &f.core.metadata,
                &context,
                &BTreeSet::from([provider.clone()]),
            )?;
            let selected = selection
                .inputs
                .get(&provider)
                .expect("genuine actual provider selected");
            let body = render_java_source(
                std::str::from_utf8(&f.core.read_source(&selected.input)?)?,
                &context,
            )?;
            for anchor in anchors {
                assert!(
                    body.contains(anchor),
                    "actual provider anchor {name}: {anchor}"
                );
            }
        }
    }
    for name in NAMES {
        let body = std::str::from_utf8(&f.sources[&path(name)])?;
        for forbidden in [
            "ProcessBuilder",
            "java.net.Socket",
            "Class.forName",
            "VoxClient",
            "org.lwjgl",
            "NativeImage",
            "Minecraft.getInstance",
            "System.setProperty",
            "Files.write",
            "FileOutputStream",
        ] {
            assert!(
                !body.contains(forbidden),
                "pure cohort acquired effect API {name}: {forbidden}"
            );
        }
    }
    let validator = std::str::from_utf8(&f.sources[&path("SFMTerminalRasterFrameValidator")])?;
    for anchor in [
        "Math.multiplyExact",
        "Math.addExact",
        "validateNoGeometricOverlap",
        "dirty region payload ranges must consume the complete packed payload",
    ] {
        assert!(validator.contains(anchor));
    }
    let compositor = std::str::from_utf8(&f.sources[&path("SFMTerminalRgbaCompositor")])?;
    assert!(
        compositor
            .find("return ApplyResult.STALE_GENERATION;")
            .expect("stale refusal")
            < compositor
                .find("validateRgba8(frame, limits)")
                .expect("validation")
    );
    for absent in [
        "SFMTerminalRemoteService",
        "SFMTerminalPanel",
        "SFMTerminalPropertiesPanel",
        "SFMTerminalServiceFactory",
        "SFMJavaLocalTerminalService",
        "SFMTerminalPngRenderer",
        "SFMTerminalRgbaRenderer",
        "SFMVoxTerminalService",
        "SFMVoxTerminalPresentationAdapter",
    ] {
        assert!(!f.inventory().contains(&path(absent)));
    }
    Ok(())
}

#[test]
fn real_collector_omits_malformed_off_inputs_then_renders_java_even_with_template_false()
-> Result<()> {
    let f = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    for source in f.inventory() {
        let destination = root.join(source);
        fs::create_dir_all(destination.parent().expect("fixed model parent"))?;
        fs::write(
            destination,
            b"{% if features.unreviewed_effect_owner %}\n\xff",
        )?;
    }
    let mut metadata = f.bounded_metadata();
    let inventory = discover_core_source_files(&root)?;
    assert_eq!(inventory, f.inventory());
    for target in D2 {
        let context = f.core.context(target, &[])?;
        f.copy_project_inputs(&root, &metadata, &context)?;
        let selection = select_core_inputs(&metadata, &context, &inventory)?;
        let artifacts = collect_core_artifacts(&root, &selection, &context)?;
        for source in f.inventory() {
            assert!(!artifacts.contains_key(&source));
        }
    }
    for name in ["SFMTerminalService", "SFMTerminalScrollback"] {
        fs::write(root.join(path(name)), &f.sources[&path(name)])?;
    }
    for target in D2 {
        let context = f.core.context(target, &["terminal_local"])?;
        f.copy_project_inputs(&root, &metadata, &context)?;
        let selection = select_core_inputs(&metadata, &context, &inventory)?;
        let artifacts = collect_core_artifacts(&root, &selection, &context)?;
        for name in NAMES {
            assert_eq!(artifacts.contains_key(&path(name)), shared(name));
        }
    }
    f.write_sources(&root)?;
    for rules in metadata.source_rules.values_mut() {
        rules[0].template = false;
    }
    let probe_path = path("SFMTerminalConnectionSnapshot");
    let original = std::str::from_utf8(&f.sources[&probe_path])?;
    let probe = format!(
        "{original}{{% if features.terminal_remote %}}\n// Isolated automatic Java probe.\n{{% endif %}}\n"
    );
    fs::write(root.join(&probe_path), &probe)?;
    for target in D2 {
        let context = f.core.context(target, &ALL)?;
        f.copy_project_inputs(&root, &metadata, &context)?;
        let selection = select_core_inputs(&metadata, &context, &inventory)?;
        let artifacts = collect_core_artifacts(&root, &selection, &context)?;
        for (path, source) in &f.sources {
            let artifact = &artifacts[path];
            let output = std::str::from_utf8(&artifact.output_bytes)?;
            let (_, body) = output
                .split_once('\n')
                .expect("actual generated Java banner");
            let input = if *path == probe_path {
                probe.as_str()
            } else {
                std::str::from_utf8(source)?
            };
            assert_eq!(body, render_java_source(input, &context)?);
            assert!(!body.contains("{%"));
            assert_eq!(artifact.source_path, format!("{CORE_ROOT}/{path}"));
            assert!(artifact.overlay.is_none());
        }
    }
    Ok(())
}

#[test]
fn common_authored_model_edits_reach_all_selected_targets_without_live_mutation() -> Result<()> {
    let f = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    f.write_sources(&root)?;
    let mut edited = BTreeMap::new();
    for name in NAMES {
        let path = path(name);
        let source = std::str::from_utf8(&f.sources[&path])?;
        let changed = source.replacen(
            "package ca.teamdman.sfm.client.terminal;",
            "package ca.teamdman.sfm.client.terminal;\n// Isolated common terminal model edit.",
            1,
        );
        assert_ne!(source, changed);
        fs::write(root.join(&path), &changed)?;
        edited.insert(path, changed);
    }
    let metadata = f.bounded_metadata();
    let inventory = discover_core_source_files(&root)?;
    let mut changed_outputs = 0;
    for target in TEN {
        let flags = if D2.contains(&target) {
            ALL.to_vec()
        } else {
            vec!["terminal_local", "terminal_remote", "terminal_vox_runtime"]
        };
        let context = f.core.context(target, &flags)?;
        f.copy_project_inputs(&root, &metadata, &context)?;
        let selection = select_core_inputs(&metadata, &context, &inventory)?;
        let artifacts = collect_core_artifacts(&root, &selection, &context)?;
        for name in NAMES {
            let path = path(name);
            let expected = D2.contains(&target) || shared(name);
            assert_eq!(artifacts.contains_key(&path), expected);
            if expected {
                let output = std::str::from_utf8(&artifacts[&path].output_bytes)?;
                let (_, body) = output.split_once('\n').expect("actual common-edit banner");
                assert_eq!(body, render_java_source(&edited[&path], &context)?);
                assert!(body.contains("// Isolated common terminal model edit."));
                changed_outputs += 1;
            }
        }
    }
    assert_eq!(changed_outputs, 68);
    for (path, source) in &f.sources {
        assert_eq!(f.core.read_source(path)?, *source);
    }
    Ok(())
}
