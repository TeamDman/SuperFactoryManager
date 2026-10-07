//! Genuine property-provider reconstruction and minimal signing consumer union.
//!
//! Register only after root promotes the source, its exact sparse rule and the
//! portable ledger. Tests use actual core inputs, never ignored-stage fallback.
//! Git objects are offline regression goldens, not production generation data.
//! No test invokes Java, changes JVM properties, creates a store/key or starts
//! a puppet, signing runtime, title screen, network client or external process
//! other than the bounded offline Git witness reader.
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

const LEDGER: &str = "docs/tasks/sfm-core-properties-provider-slice.json";
const SOURCE: &str = "src/main/java/ca/teamdman/sfm/properties/SFMProperties.java";
const OID: &str = "b6d7bbec93a3cc93f48999e7c6a9fe8c61d3b07a";
const RAW_DIGEST: &str = "sha256:6911e65416449c0e1695c96fc4b7b90b4006551b37ab48bc5180be5a42801225";
const TEMPLATE_DIGEST: &str =
    "sha256:4307ea192259e4661efd103ff71e63dd4e2145b21ac83f3b5c2828693b19e168";
const PROPERTIES_DIGEST: &str =
    "sha256:df3a94cd978e49a7c8e1e14024672d37ff7c489b4751bf8331157c5c97a1e1c3";
const SIGNING_DIGEST: &str =
    "sha256:78be7576f846fb10656d71d133d3f1381386bda6af46f3c0bf8299a7a611693d";
const TEN: [&str; 10] = [
    "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
    "26.1.2",
];
const D2: [&str; 2] = ["1.19.2", "1.19.4"];
const PROPERTY_FLAGS: [&str; 1] = ["client_properties"];
const TITLE_FLAGS: [&str; 2] = ["client_properties", "client_launch_screen"];
const SIGNING: [&str; 11] = [
    "client_actions",
    "client_frame_language",
    "client_manager",
    "client_program_actions",
    "client_program_consent",
    "client_program_signing",
    "disk_readonly_access",
    "packet_computation",
    "packet_values",
    "runtime_resource_cleanup",
    "sfml_execution_side",
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
const DEFINITIONS: [(&str, &[&str], &[&str]); 13] = [
    ("client_properties", &TEN, &[]),
    ("client_launch_screen", &TEN, &["client_properties"]),
    (
        "client_program_signing",
        &D2,
        &["client_frame_language", "client_program_actions"],
    ),
    (
        "client_frame_language",
        &D2,
        &[
            "client_manager",
            "sfml_execution_side",
            "client_program_consent",
        ],
    ),
    (
        "client_program_actions",
        &D2,
        &[
            "client_actions",
            "packet_values",
            "sfml_execution_side",
            "client_manager",
            "client_program_consent",
            "packet_computation",
        ],
    ),
    (
        "client_manager",
        &D2,
        &[
            "sfml_execution_side",
            "client_program_consent",
            "disk_readonly_access",
        ],
    ),
    ("client_program_consent", &D2, &["sfml_execution_side"]),
    ("disk_readonly_access", &TEN, &[]),
    ("client_actions", &TEN, &[]),
    (
        "packet_computation",
        &D2,
        &["packet_values", "runtime_resource_cleanup"],
    ),
    ("packet_values", &D2, &[]),
    ("runtime_resource_cleanup", &D2, &[]),
    ("sfml_execution_side", &D2, &[]),
];
const REGIONS: [(&str, usize, usize, &str); 12] = [
    (
        "client_launch_screen",
        37,
        100,
        "sha256:f31fd8786e27434674dd5c0031b4ab263044eb43c04198a4f170e582df31991f",
    ),
    (
        "client_properties",
        100,
        127,
        "sha256:3fd72debb890871d35954560f1899e50d67be790628504bdc60a2edb290bfcde",
    ),
    (
        "client_launch_screen",
        127,
        154,
        "sha256:e6f2f80cf87622a9b81ace73907b153830c7af890cafc9d0e08bb1f6cb02cd1e",
    ),
    (
        "client_properties",
        351,
        563,
        "sha256:ba4522a7ad151456005d6cb96670c475b8f8a8e284f348e743d107dcc4e3ddde",
    ),
    (
        "client_launch_screen",
        563,
        791,
        "sha256:86607636d6948e6331951e4bc668602793d41a3c691c85aadbbaef15463dbba4",
    ),
    (
        "client_properties",
        791,
        1355,
        "sha256:f7c70fbffb9ba3b1cdd0f4b87396a6cc7034e261cb0132a203c201eb7dcb6671",
    ),
    (
        "client_properties",
        1534,
        1854,
        "sha256:45c94894f17bdbec3dec1b45c7dfc4924c73816148cace306d839b48cd7259ba",
    ),
    (
        "client_launch_screen",
        1854,
        2563,
        "sha256:cb020e70959f81ebfcd38c78d357e2616f610c1f82fac0ab0b944a31351c6a9d",
    ),
    (
        "client_properties",
        2563,
        3465,
        "sha256:04036d6a40bc4047151e0dcda51334df376e9caf945a2826187e86e33f6bd753",
    ),
    (
        "client_launch_screen",
        3465,
        3583,
        "sha256:7b790bc33994b10318768e631aa8ce22e6cf074ded3e715fc8f6d2d4412a7dd9",
    ),
    (
        "client_properties",
        3583,
        4217,
        "sha256:1cebc9440948a11a4709087be43bd2fff122d536a92de5bdcb0fb6380aff305c",
    ),
    (
        "client_properties",
        5000,
        5908,
        "sha256:d6e3c3e296256108d152ff32d3eee7e2478b945845b709b384befc2c6dfb0ff4",
    ),
];

#[derive(Facet)]
struct Ledger {
    schema: String,
    normalization: String,
    contract_changes: bool,
    permission_widening: bool,
    source: SourceEvidence,
    context_commits: BTreeMap<String, String>,
    definitions: BTreeMap<String, Definition>,
    raw_member_regions: Vec<Region>,
}
#[derive(Facet)]
struct Definition {
    supported_targets: Vec<String>,
    requires: Vec<String>,
}
#[derive(Facet)]
struct SourceEvidence {
    path: String,
    raw_oid: String,
    raw_bytes: usize,
    raw_sha256: String,
    raw_lf: usize,
    raw_cr: usize,
    final_lf: bool,
    bom: bool,
    authored_bytes: usize,
    authored_sha256: String,
    authored_lf: usize,
    source_rule: InputVariant,
    witnesses: BTreeMap<String, Option<String>>,
}
#[derive(Facet)]
struct Region {
    owner: String,
    start: usize,
    end: usize,
    sha256: String,
}

struct Fixture {
    core: CoreTestFixture,
    source: Vec<u8>,
    raw: Vec<u8>,
}
impl Fixture {
    fn load() -> Result<Self> {
        let core = CoreTestFixture::load()?;
        let ledger: Ledger = facet_json::from_str(std::str::from_utf8(&read_bounded(
            &checked_file(&core.repository, LEDGER)?,
            128 * 1024,
        )?)?)?;
        ensure!(
            ledger.schema == "sfm:core-properties-provider-slice@1"
                && ledger.normalization == "none"
                && !ledger.contract_changes
                && !ledger.permission_widening
                && ledger.context_commits
                    == CONTEXTS
                        .into_iter()
                        .map(|(context, commit)| (context.to_owned(), commit.to_owned()))
                        .collect()
                && ledger.definitions.len() == 13
                && ledger.raw_member_regions.len() == 12,
            "immutable property scope/contracts changed"
        );
        for (name, support, requires) in DEFINITIONS {
            let original = ledger
                .definitions
                .get(name)
                .ok_or_else(|| eyre::eyre!("original property feature absent: {name}"))?;
            let current = core
                .features
                .0
                .get(name)
                .ok_or_else(|| eyre::eyre!("current property feature absent: {name}"))?;
            ensure!(
                original.supported_targets == support
                    && original.requires == requires
                    && current.supported_targets == support
                    && current.requires == requires,
                "property support/prerequisite changed: {name}"
            );
        }
        let evidence = ledger.source;
        ensure!(
            evidence.path == SOURCE
                && evidence.raw_oid == OID
                && evidence.raw_bytes == 5_910
                && evidence.raw_sha256 == RAW_DIGEST
                && evidence.raw_lf == 153
                && evidence.raw_cr == 0
                && evidence.final_lf
                && !evidence.bom
                && evidence.authored_bytes == 6_501
                && evidence.authored_sha256 == TEMPLATE_DIGEST
                && evidence.authored_lf == 177
                && evidence.witnesses == expected_witnesses(),
            "property source receipt changed"
        );
        let raw = read_git_blobs(&core.repository, &BTreeSet::from([OID.to_owned()]))?
            .remove(OID)
            .ok_or_else(|| eyre::eyre!("property raw blob absent"))?;
        verify_bytes(&raw, 5_910, 153, RAW_DIGEST)?;
        let source = core.read_source(SOURCE)?;
        verify_bytes(&source, 6_501, 177, TEMPLATE_DIGEST)?;
        let rules = core
            .metadata
            .source_rules
            .get(SOURCE)
            .ok_or_else(|| eyre::eyre!("root must first promote exact property source rule"))?;
        ensure!(
            rules.len() == 1
                && rules[0] == evidence.source_rule
                && rules[0].input == SOURCE
                && rules[0].template
                && rules[0].when.targets.is_empty()
                && rules[0].when.all_features.is_empty()
                && rules[0].when.any_features == ["client_properties", "client_program_signing"]
                && rules[0].when.none_features.is_empty(),
            "property consumer union changed"
        );
        let mut previous = 0;
        for ((owner, start, end, digest), region) in REGIONS.iter().zip(&ledger.raw_member_regions)
        {
            ensure!(
                region.owner == *owner
                    && region.start == *start
                    && region.end == *end
                    && region.sha256 == *digest
                    && *start >= previous
                    && start < end
                    && *end <= raw.len()
                    && sha256(&raw[*start..*end]) == *digest,
                "exact independent raw member interval changed"
            );
            previous = *end;
        }
        Ok(Self { core, source, raw })
    }
    fn inventory(&self) -> BTreeSet<String> {
        BTreeSet::from([SOURCE.to_owned()])
    }
    fn expected(&self, context: &ProjectionContext) -> Vec<u8> {
        let mut bytes = self.raw.clone();
        // Golden bodies are selected from independently pinned raw intervals,
        // not computed from the template or its rendering implementation.
        for (owner, start, end, _) in REGIONS.iter().rev() {
            if !context.features[*owner] {
                bytes.drain(*start..*end);
            }
        }
        bytes
    }
    fn render(&self, context: &ProjectionContext) -> Result<Option<Vec<u8>>> {
        let selected = select_core_inputs(&self.core.metadata, context, &self.inventory())?;
        if !selected.inputs.contains_key(SOURCE) {
            ensure!(
                selected.omitted_paths.contains(SOURCE),
                "property omission unaccounted"
            );
            return Ok(None);
        }
        Ok(Some(
            render_java_source(std::str::from_utf8(&self.source)?, context)?.into_bytes(),
        ))
    }
    fn bounded_metadata(&self) -> CoreProjectInputs {
        let mut metadata = self.core.metadata.clone();
        metadata.source_rules.retain(|path, _| path == SOURCE);
        metadata
    }
    fn write_source(&self, root: &Path, bytes: &[u8]) -> Result<()> {
        let destination = root.join(SOURCE);
        fs::create_dir_all(
            destination
                .parent()
                .ok_or_else(|| eyre::eyre!("property parent absent"))?,
        )?;
        fs::write(destination, bytes)?;
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
            if output == SOURCE {
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
                    "exact property fixture build input conflicted"
                );
            } else {
                fs::create_dir_all(
                    destination
                        .parent()
                        .ok_or_else(|| eyre::eyre!("build input parent absent"))?,
                )?;
                fs::write(destination, bytes)?;
            }
        }
        Ok(())
    }
}
fn expected_witnesses() -> BTreeMap<String, Option<String>> {
    CONTEXTS
        .into_iter()
        .map(|(name, _)| {
            (
                name.to_owned(),
                name.starts_with("dev/").then(|| OID.to_owned()),
            )
        })
        .collect()
}
fn verify_bytes(bytes: &[u8], length: usize, lf: usize, digest: &str) -> Result<()> {
    ensure!(
        bytes.len() == length
            && sha256(bytes) == digest
            && bytes.iter().filter(|byte| **byte == b'\n').count() == lf
            && !bytes.contains(&b'\r')
            && bytes.last() == Some(&b'\n')
            && !bytes.starts_with(&[239, 187, 191]),
        "property byte shape changed"
    );
    Ok(())
}
fn signing_flags(extra: &[&'static str]) -> Vec<&'static str> {
    SIGNING.into_iter().chain(extra.iter().copied()).collect()
}
fn assert_signing_subset(body: &str) {
    for anchor in [
        "CLIENT_RUN_MODE_PROPERTY = \"sfm.clientRun.mode\"",
        "ClientRunMode clientRunMode()",
        "return ClientRunMode.fromPropertyValue(property(CLIENT_RUN_MODE_PROPERTY))",
        "System.getProperty(name, \"\").trim()",
        "NONE(\"\")",
        "SMOKE(\"smoke\")",
        "PUPPET(\"puppet\")",
        "GAME_PUPPET(\"game-puppet\")",
        "return NONE;",
        "return this == PUPPET || this == GAME_PUPPET;",
    ] {
        assert!(
            body.contains(anchor),
            "genuine run-mode primitive absent: {anchor}"
        );
    }
    for forbidden in [
        "import ",
        "SFMTitleScreenDevScreen",
        "Optional",
        "clientRunTitleScreen",
        "KEEP_OPEN",
        "TITLE_EXIT",
        "GAME_TEST_SELECTION",
        "GAME_PUPPET_SELECTION",
        "USER_DIRECTORY",
        "gameTestSelection",
        "gameTestMaxProgramRunMillis",
        "gamePuppetSelection",
        "gamePuppetViewportSelection",
        "requiredGamePuppetGameTest",
        "SFMGameTestId",
        "userDirectory",
        "integerProperty",
        "longProperty",
        "requiredProperty",
        "System.setProperty",
        "createDirectories",
        "ClientSigningKeyStore",
        "SFMGamePuppetHarness",
        "Minecraft",
    ] {
        assert!(
            !body.contains(forbidden),
            "signing-only provider leaked unrelated/effect API: {forbidden}"
        );
    }
}

#[test]
fn twenty_property_git_cells_and_full_on_renderer_preserve_raw_identity() -> Result<()> {
    let f = Fixture::load()?;
    let mut present = 0;
    let mut absent = 0;
    for (name, commit) in CONTEXTS {
        let output = frozen_git_command(&f.core.repository)
            .args([
                "ls-tree",
                commit,
                "--",
                &format!("platform/minecraft/{SOURCE}"),
            ])
            .output()?;
        ensure!(
            output.status.success()
                && output.stdout.len() <= 16 * 1024
                && output.stderr.len() <= 16 * 1024,
            "offline property tree query failed"
        );
        let target = name
            .split_once('/')
            .ok_or_else(|| eyre::eyre!("context malformed"))?
            .1;
        let context = f.core.context(
            target,
            if name.starts_with("dev/") {
                &TITLE_FLAGS
            } else {
                &[]
            },
        )?;
        if name.starts_with("dev/") {
            assert_eq!(
                std::str::from_utf8(&output.stdout)?,
                format!("100644 blob {OID}\tplatform/minecraft/{SOURCE}\n")
            );
            assert_eq!(
                f.render(&context)?
                    .ok_or_else(|| eyre::eyre!("property selected witness omitted"))?,
                f.raw
            );
            present += 1;
        } else {
            assert!(output.stdout.is_empty());
            assert!(f.render(&context)?.is_none());
            absent += 1;
        }
    }
    assert_eq!((present, absent), (10, 10));
    Ok(())
}

#[test]
fn independent_member_profiles_use_only_real_feature_owners_and_no_context_alias() -> Result<()> {
    let f = Fixture::load()?;
    let mut selected = 0;
    let mut omitted = 0;
    for target in TEN {
        for flags in [
            &[] as &[&str],
            PROPERTY_FLAGS.as_slice(),
            TITLE_FLAGS.as_slice(),
        ] {
            let mut context = f.core.context(target, flags)?;
            context.environment = "unrelated".to_owned();
            context.projection_key = "unrelated/key".to_owned();
            context.preset = "unrelated-preset".to_owned();
            let output = f.render(&context)?;
            if flags.is_empty() {
                assert!(output.is_none());
                omitted += 1;
            } else {
                let body = output.ok_or_else(|| eyre::eyre!("property profile omitted"))?;
                assert_eq!(body, f.expected(&context));
                assert_eq!(
                    sha256(&body),
                    if flags.contains(&"client_launch_screen") {
                        RAW_DIGEST
                    } else {
                        PROPERTIES_DIGEST
                    }
                );
                selected += 1;
            }
        }
    }
    for target in D2 {
        for extras in [
            &[] as &[&'static str],
            &["client_properties"],
            &["client_properties", "client_launch_screen"],
        ] {
            let flags = signing_flags(extras);
            let context = f.core.context(target, &flags)?;
            assert_eq!(
                f.render(&context)?
                    .ok_or_else(|| eyre::eyre!("signing profile omitted"))?,
                f.expected(&context)
            );
            selected += 1;
        }
    }
    assert_eq!((selected, omitted), (26, 10));
    Ok(())
}

#[test]
fn signing_only_real_provider_keeps_fixture_refusal_without_puppet_or_title_activation()
-> Result<()> {
    let f = Fixture::load()?;
    let consumer_oid = "0edc4adccec5cd446618b0f63a74bc36abd3b4e7";
    let blobs = read_git_blobs(
        &f.core.repository,
        &BTreeSet::from([consumer_oid.to_owned()]),
    )?;
    let consumer = &blobs[consumer_oid];
    verify_bytes(
        consumer,
        7_610,
        consumer.iter().filter(|byte| **byte == b'\n').count(),
        "sha256:d2538e0bb576bf3156c572bedc65c136ef4e1ec59940340ddc56650784111680",
    )?;
    let consumer = std::str::from_utf8(consumer)?;
    let gate = consumer
        .find("SFMProperties.clientRunMode() != SFMProperties.ClientRunMode.GAME_PUPPET")
        .ok_or_else(|| eyre::eyre!("real signing fixture mode gate absent"))?;
    let effect = consumer[gate..]
        .find("fixtureKeys = installed;")
        .ok_or_else(|| eyre::eyre!("real signing fixture installation absent"))?;
    assert!(
        effect > 0 && consumer[gate..gate + effect].contains("throw new IllegalStateException")
    );
    for target in D2 {
        let context = f.core.context(target, &SIGNING)?;
        assert!(
            !context.features["client_properties"]
                && !context.features["client_launch_screen"]
                && !context.features["game_puppet_runtime"]
                && !context.features["client_smoke_harness"]
        );
        let bytes = f
            .render(&context)?
            .ok_or_else(|| eyre::eyre!("minimal signing primitive omitted"))?;
        assert_eq!(bytes.len(), 1_198);
        assert_eq!(sha256(&bytes), SIGNING_DIGEST);
        assert_signing_subset(std::str::from_utf8(&bytes)?);
    }
    Ok(())
}

#[test]
fn actual_fixed_core_collector_handles_all_twenty_and_signing_only_with_template_false()
-> Result<()> {
    let f = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    f.write_source(&root, &f.source)?;
    let inventory = discover_core_source_files(&root)?;
    assert_eq!(inventory, f.inventory());
    let mut metadata = f.bounded_metadata();
    metadata
        .source_rules
        .get_mut(SOURCE)
        .ok_or_else(|| eyre::eyre!("rule absent"))?[0]
        .template = false;
    let mut cases = CONTEXTS
        .into_iter()
        .map(|(name, _)| {
            let target = name.split_once('/').expect("fixed context").1;
            f.core.context(
                target,
                if name.starts_with("dev/") {
                    &TITLE_FLAGS
                } else {
                    &[]
                },
            )
        })
        .collect::<Result<Vec<_>>>()?;
    for target in D2 {
        cases.push(f.core.context(target, &SIGNING)?);
    }
    let mut selected_count = 0;
    let mut omitted_count = 0;
    for context in cases {
        f.copy_project_inputs(&root, &metadata, &context)?;
        let selected = select_core_inputs(&metadata, &context, &inventory)?;
        let artifacts = collect_core_artifacts(&root, &selected, &context)?;
        if let Some(artifact) = artifacts.get(SOURCE) {
            let output = std::str::from_utf8(&artifact.output_bytes)?;
            let (_, body) = output
                .split_once('\n')
                .ok_or_else(|| eyre::eyre!("generated banner absent"))?;
            assert_eq!(body.as_bytes(), f.expected(&context));
            assert!(!body.contains("{%"));
            assert_eq!(artifact.source_path, format!("{CORE_ROOT}/{SOURCE}"));
            assert!(artifact.overlay.is_none());
            if context.features["client_program_signing"] && !context.features["client_properties"]
            {
                assert_signing_subset(body);
            }
            selected_count += 1;
        } else {
            assert!(selected.omitted_paths.contains(SOURCE));
            omitted_count += 1;
        }
    }
    assert_eq!((selected_count, omitted_count), (12, 10));
    assert_eq!(f.core.read_source(SOURCE)?, f.source);
    Ok(())
}

#[test]
fn inactive_malformed_property_source_is_omitted_before_read_and_selected_source_refuses()
-> Result<()> {
    let f = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    f.write_source(
        &root,
        b"{% if features.unregistered_property_owner %}\n\xff",
    )?;
    let inventory = discover_core_source_files(&root)?;
    let metadata = f.bounded_metadata();
    for target in TEN {
        let off = f.core.context(target, &[])?;
        f.copy_project_inputs(&root, &metadata, &off)?;
        let selected = select_core_inputs(&metadata, &off, &inventory)?;
        assert!(selected.omitted_paths.contains(SOURCE));
        assert!(!collect_core_artifacts(&root, &selected, &off)?.contains_key(SOURCE));
        let on = f.core.context(target, &PROPERTY_FLAGS)?;
        f.copy_project_inputs(&root, &metadata, &on)?;
        let selected = select_core_inputs(&metadata, &on, &inventory)?;
        assert!(collect_core_artifacts(&root, &selected, &on).is_err());
    }
    let malformed_directive = "{% if features.client_launch_screen %}\n{% if features.unregistered_property_owner %}\n{% endif %}\n{% endif %}\n";
    for target in D2 {
        assert!(
            render_java_source(malformed_directive, &f.core.context(target, &SIGNING)?).is_err()
        );
    }
    Ok(())
}

#[test]
fn real_feature_prerequisites_support_and_missing_owner_keys_remain_fail_closed() -> Result<()> {
    let f = Fixture::load()?;
    for target in TEN {
        assert!(f.core.context(target, &["client_launch_screen"]).is_err());
    }
    let mut missing = 0;
    for target in D2 {
        for removed in SIGNING
            .into_iter()
            .filter(|feature| *feature != "client_program_signing")
        {
            let flags: Vec<_> = SIGNING
                .into_iter()
                .filter(|feature| *feature != removed)
                .collect();
            assert!(f.core.context(target, &flags).is_err());
            missing += 1;
        }
        for removed in [
            "client_properties",
            "client_program_signing",
            "client_launch_screen",
        ] {
            let mut context = f.core.context(target, &SIGNING)?;
            context.features.remove(removed);
            assert!(
                select_core_inputs(&f.core.metadata, &context, &f.inventory()).is_err()
                    || render_java_source(std::str::from_utf8(&f.source)?, &context).is_err()
            );
        }
    }
    assert_eq!(missing, 20);
    for target in TEN.into_iter().filter(|target| !D2.contains(target)) {
        assert!(f.core.context(target, &SIGNING).is_err());
    }
    Ok(())
}

#[test]
fn original_property_defaults_identifiers_and_raw_member_goldens_reject_mutation() -> Result<()> {
    let f = Fixture::load()?;
    let raw = std::str::from_utf8(&f.raw)?;
    for anchor in [
        "System.getProperty(name, \"\").trim()",
        "return NONE;",
        "return value.isEmpty() ? defaultValue : Integer.parseInt(value);",
        "return value.isEmpty() ? defaultValue : Long.parseLong(value);",
        "Required JVM property is blank: ",
        "Unsupported SFM title screen launch screen: ",
        "value.indexOf(':') >= 0",
        "value.indexOf('*') >= 0 || value.indexOf('?') >= 0 || value.indexOf(',') >= 0",
        "value.startsWith(\"sfm:\") ? value.substring(\"sfm:\".length()) : value",
    ] {
        assert!(
            raw.contains(anchor),
            "original property validation/default absent: {anchor}"
        );
    }
    assert_eq!(
        std::str::from_utf8(&f.source)?
            .matches("{% if features.")
            .count(),
        12
    );
    assert!(!std::str::from_utf8(&f.source)?.contains("minecraft_version"));
    let mut extra = f.raw.clone();
    extra.push(b'\n');
    let mut missing = f.raw.clone();
    missing.pop();
    let mut bom = vec![239, 187, 191];
    bom.extend(&f.raw);
    let crlf = raw.replace('\n', "\r\n").into_bytes();
    for mutant in [extra, missing, bom, crlf] {
        assert!(verify_bytes(&mutant, 5_910, 153, RAW_DIGEST).is_err());
    }
    for (flags, length, digest) in [
        (&PROPERTY_FLAGS as &[&str], 4_765, PROPERTIES_DIGEST),
        (&TITLE_FLAGS as &[&str], 5_910, RAW_DIGEST),
        (&SIGNING as &[&str], 1_198, SIGNING_DIGEST),
    ] {
        let context = f.core.context("1.19.2", flags)?;
        let expected = f.expected(&context);
        assert_eq!(expected.len(), length);
        assert_eq!(sha256(&expected), digest);
        assert_eq!(
            f.render(&context)?
                .ok_or_else(|| eyre::eyre!("golden profile omitted"))?,
            expected
        );
    }
    Ok(())
}

#[test]
fn isolated_common_property_edit_reaches_ten_targets_and_minimal_signing_provider() -> Result<()> {
    let f = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    let original = std::str::from_utf8(&f.source)?;
    let anchor = "public final class SFMProperties {";
    assert_eq!(original.matches(anchor).count(), 1);
    let edited = original.replacen(
        anchor,
        &format!("// Isolated common property edit.\n{anchor}"),
        1,
    );
    f.write_source(&root, edited.as_bytes())?;
    let inventory = discover_core_source_files(&root)?;
    let metadata = f.bounded_metadata();
    let mut cases = TEN
        .into_iter()
        .map(|target| f.core.context(target, &PROPERTY_FLAGS))
        .collect::<Result<Vec<_>>>()?;
    for target in D2 {
        cases.push(f.core.context(target, &SIGNING)?);
    }
    let mut outputs = 0;
    for context in cases {
        f.copy_project_inputs(&root, &metadata, &context)?;
        let selected = select_core_inputs(&metadata, &context, &inventory)?;
        let artifacts = collect_core_artifacts(&root, &selected, &context)?;
        let artifact = artifacts
            .get(SOURCE)
            .ok_or_else(|| eyre::eyre!("common property edit omitted"))?;
        let output = std::str::from_utf8(&artifact.output_bytes)?;
        let (_, body) = output
            .split_once('\n')
            .ok_or_else(|| eyre::eyre!("generated banner absent"))?;
        assert_eq!(body, render_java_source(&edited, &context)?);
        assert!(body.contains("// Isolated common property edit."));
        assert!(!body.contains("SFMTitleScreenDevScreen"));
        outputs += 1;
    }
    assert_eq!(outputs, 12);
    assert_eq!(f.core.read_source(SOURCE)?, f.source);
    Ok(())
}
