//! Test-only byte, source-membership and feature-prerequisite checks.
//!
//! Four baseline event-discovery classes remain shared on every target.
//! Optional-mod filtering is independent from the ComputerCraft presence probe.
//! Historical Git objects are exact test witnesses, never production inputs.
//! Passing these fixtures does not establish Java compilation or CC runtime support.

#![cfg(test)]

use super::candidate_lock::checked_file;
use super::context::ProjectionContext;
use super::core_inputs::discover_core_source_files;
use super::core_inputs::select_core_inputs;
use super::core_slice_test_support::CoreTestFixture;
use super::core_slice_test_support::read_bounded;
use super::core_slice_test_support::read_git_blobs;
use super::projection_catalog::SUPPORTED_TARGETS;
use super::provenance::sha256;
use super::render_java_source;
use eyre::Result;
use eyre::ensure;
use facet::Facet;
use std::collections::BTreeMap;
use std::collections::BTreeSet;

const ANNOTATION: &str = "src/main/java/ca/teamdman/sfm/common/event_bus/SFMSubscribeEvent.java";
const SUBSCRIBER: &str =
    "src/main/java/ca/teamdman/sfm/common/event_bus/SFMAutomaticEventSubscriber.java";
const ANNOTATION_UTILS: &str = "src/main/java/ca/teamdman/sfm/common/util/SFMAnnotationUtils.java";
const MOD_COMPAT: &str = "src/main/java/ca/teamdman/sfm/common/compat/SFMModCompat.java";
const PATHS: [&str; 4] = [ANNOTATION, SUBSCRIBER, ANNOTATION_UTILS, MOD_COMPAT];
const LEDGER_PATH: &str = "docs/tasks/sfm-core-event-discovery-slice.json";
const FILTER: &str = "mod_event_filtering";
const CC: &str = "computercraft";
const MAX_LEDGER_BYTES: u64 = 1024 * 1024;

// Raw Git object, independent SHA-256, exact bytes, CR count, LF count.
const RAW_GOLDENS: [(&str, &str, usize, usize, usize); 20] = [
    (
        "2a06f6404fd6902d8fa1400067a01d942c8f8765",
        "sha256:b6afab21ed37c6cce5121b7d07273e69ece5dabd166eedda75a79ed90d202ce6",
        1327,
        27,
        36,
    ),
    (
        "52643e95902a9f200a91715e73ae7eeff63d8243",
        "sha256:7064487d9889f4269925439f891dbfa3ba71d00b749338c471450f7d3642fef7",
        1107,
        30,
        30,
    ),
    (
        "c6a1d3916af3813b032288c288199f6a106709b7",
        "sha256:7cd57c25696f39e102df4e101d10e4adbdbe198a3e94cb6f97e1330d852fe65a",
        1087,
        30,
        30,
    ),
    (
        "f60586b6b9d178add4da66ffc33020f607ad989f",
        "sha256:c5328082283f63ca868134f0868083ffc0d779f26a82f132c527abeaf0fc9edb",
        1347,
        27,
        36,
    ),
    (
        "173d3c3a3c2b7bddd1c0680dad4e4a405090bf4e",
        "sha256:b6f5160f98b607ccfdc603080527ac6c54fd1741a6abb73352a1349d1bb8d514",
        3984,
        92,
        92,
    ),
    (
        "1ff5aa95988175902c66a0fe7563c1d3e5e293a8",
        "sha256:30c04be49ca2eb6f4320a771b9022302fccccd556d1c1faae9706e1851904cf2",
        4334,
        88,
        98,
    ),
    (
        "4f30c4005f8ff1951ebfc0fc7d14d9bae0c93a11",
        "sha256:c8eccfe98a22b61ac42175bcef22e84832d58cf447b2ffe856794b351414b052",
        4278,
        87,
        97,
    ),
    (
        "ddfed2b332b88eef1f39fc0a5dae9bc8bb8948e0",
        "sha256:e2100a014d7a72f46436bfa07e346890160c11d2e9d2724eae7064bd89c32be7",
        4040,
        93,
        93,
    ),
    (
        "032ebda77e97d8985ad060437d1808b3412b43eb",
        "sha256:259c874954255354253556a61600c2b975ef44d9024230eff3f2a654a98402c5",
        3875,
        127,
        127,
    ),
    (
        "1c5e7089b8e487cb350163b742ee77fe1c21f12b",
        "sha256:aebc6bd04354692050cfa90b8a563832cebe5d8387ecbda2f8db19539ddb4946",
        4069,
        121,
        133,
    ),
    (
        "1dba38b53d201e4b56c7ce0cb884e23678fc2119",
        "sha256:e38904d3ada8a8a0eb14598392c37acdb625eceff2f3af9ce57e8c43f82081c9",
        4089,
        121,
        133,
    ),
    (
        "657af6e8dfbe90134e7cb27e802f28538542d0b8",
        "sha256:e233edfd669c5f1146a3748b3f56e9188a7a5deee60ced0214151c27b4ee9f7e",
        3887,
        127,
        127,
    ),
    (
        "990976c1d627e5e19eef97dd434e3d11ea584d73",
        "sha256:b83a1e9c67b2e79753369afa6198318be9e037bcca0b68f890e867b208e1cdcf",
        3867,
        127,
        127,
    ),
    (
        "b6e82e3ecddaa1b71043ce1fedb17cc20452b819",
        "sha256:8f89c29bf2975dc4bf0de50647cc12b4ba5409d1e5531fae8bf8a41d4011cc0b",
        4077,
        121,
        133,
    ),
    (
        "2ed488a5d8dc8965a8f2069354df1a8d3654f014",
        "sha256:354b8b7c7c5b82546716028005d513b836dbc449c3539b84d4f532966fba7c96",
        982,
        0,
        32,
    ),
    (
        "4543aa1fdd1cb737534edc2275244f9246214abe",
        "sha256:6147cfef08a4ac2e5c486a3ed816465b5beba04c495e5b28ade30dcdad36a69f",
        987,
        0,
        32,
    ),
    (
        "86a4b5df2cee4d4516b37c584c88a1fa174bba56",
        "sha256:f24fe7ade8e9e2543eb9ba22b365c033621c8a2e9a106644f84d7c50ff9538b7",
        970,
        0,
        32,
    ),
    (
        "a162489e65c24921fcd7a472b73f4b347464876e",
        "sha256:675f4d1932591bc72ae6636df450bec82d028df8cfcfe9e57e8f6b4f55df6431",
        1086,
        0,
        36,
    ),
    (
        "ae0c66f4dd1fe80383c20d5a0238f88dd163c049",
        "sha256:e73b8b75b6ae78c6f30de3fadd5ca227519c0ed6a148fee764b7e89d633469f4",
        1091,
        0,
        36,
    ),
    (
        "b3ebdb4d9eb49f34de45958098f0561472719162",
        "sha256:fafa5d6f45166a24a63ef07a8a2cc23cfc26d7e37dbc5079e09f8924347e81d0",
        1074,
        0,
        36,
    ),
];

const AUTHORED_GOLDENS: [(&str, &str, usize); 4] = [
    (
        ANNOTATION,
        "sha256:033c8970768a54ac472f333ab03a2058e9bf24de89e35e92b60bd260d03cac75",
        1916,
    ),
    (
        SUBSCRIBER,
        "sha256:fb153062cbbb361ddb45836453d0ba688671f9223b8a61f4bfea25166067f294",
        4976,
    ),
    (
        ANNOTATION_UTILS,
        "sha256:d76404073eb290edff673feb0234fd9f9fbb2228d8c4ae2af69701b49a7feb30",
        4868,
    ),
    (
        MOD_COMPAT,
        "sha256:aabcea4bd4aedc32897523215dc499d227a9bfdc54a232e08abe1ba46c301dc5",
        1535,
    ),
];

const PINNED_CONTEXTS: [(&str, &str); 20] = [
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

#[derive(Facet)]
struct Ledger {
    schema: String,
    context_commits: BTreeMap<String, String>,
    files: BTreeMap<String, LedgerFile>,
    feature_ownership: BTreeMap<String, FeatureOwnership>,
}

#[derive(Facet)]
struct LedgerFile {
    source_sha256: String,
    source_bytes: usize,
    functional_member_owner: String,
    raw_witness_variants: Vec<WitnessVariant>,
    historical_memberships: BTreeMap<String, String>,
}

#[derive(Facet)]
struct WitnessVariant {
    blob: String,
    sha256: String,
    bytes: usize,
    cr_count: usize,
    lf_count: usize,
    final_lf: bool,
    contexts: Vec<String>,
}

#[derive(Facet)]
struct FeatureOwnership {
    supported_targets: Vec<String>,
    requires: Vec<String>,
}

struct EventFixture {
    core: CoreTestFixture,
    inventory: BTreeSet<String>,
    files: BTreeMap<String, LedgerFile>,
    raw: BTreeMap<String, Vec<u8>>,
}

impl EventFixture {
    fn load() -> Result<Self> {
        let core = CoreTestFixture::load()?;
        let ledger: Ledger = facet_json::from_str(std::str::from_utf8(&read_bounded(
            &checked_file(&core.repository, LEDGER_PATH)?,
            MAX_LEDGER_BYTES,
        )?)?)?;
        ensure!(
            ledger.schema == "sfm:core-event-discovery-slice@1"
                && ledger.files.len() == PATHS.len()
                && PATHS.iter().all(|path| ledger.files.contains_key(*path)),
            "event-discovery evidence scope changed"
        );
        let contexts = PINNED_CONTEXTS
            .into_iter()
            .map(|(context, commit)| (context.to_owned(), commit.to_owned()))
            .collect::<BTreeMap<_, _>>();
        ensure!(
            ledger.context_commits == contexts,
            "event-discovery pinned contexts changed"
        );
        let targets = SUPPORTED_TARGETS
            .iter()
            .map(|(target, _)| *target)
            .collect::<BTreeSet<_>>();
        ensure!(
            ledger.feature_ownership.len() == 2
                && ledger.feature_ownership.contains_key(FILTER)
                && ledger.feature_ownership.contains_key(CC),
            "event-discovery ownership scope changed"
        );
        for feature in [FILTER, CC] {
            let owner = &ledger.feature_ownership[feature];
            let definition = core
                .features
                .0
                .get(feature)
                .ok_or_else(|| eyre::eyre!("required slice owner {feature} is unregistered"))?;
            let expected_requires: &[&str] = if feature == CC { &[FILTER] } else { &[] };
            ensure!(
                owner
                    .supported_targets
                    .iter()
                    .map(String::as_str)
                    .collect::<BTreeSet<_>>()
                    == targets
                    && definition
                        .supported_targets
                        .iter()
                        .map(String::as_str)
                        .collect::<BTreeSet<_>>()
                        == targets
                    && owner
                        .requires
                        .iter()
                        .map(String::as_str)
                        .collect::<Vec<_>>()
                        == expected_requires
                    && definition
                        .requires
                        .iter()
                        .map(String::as_str)
                        .collect::<Vec<_>>()
                        == expected_requires,
                "event-discovery target/prerequisite contract changed"
            );
        }
        let oids = RAW_GOLDENS
            .iter()
            .map(|(oid, ..)| (*oid).to_owned())
            .collect();
        let raw = read_git_blobs(&core.repository, &oids)?;
        for (oid, hash, bytes, cr, lf) in RAW_GOLDENS {
            let actual = &raw[oid];
            ensure!(
                actual.len() == bytes && sha256(actual) == hash,
                "raw event witness {oid} changed"
            );
            let text = std::str::from_utf8(actual)?;
            ensure!(
                actual.iter().filter(|byte| **byte == b'\r').count() == cr
                    && actual.iter().filter(|byte| **byte == b'\n').count() == lf
                    && text.matches("\r\n").count() == cr
                    && text.ends_with('\n'),
                "raw event witness EOL/EOF shape changed"
            );
        }
        let mut witnessed_oids = BTreeSet::new();
        for (path, file) in &ledger.files {
            ensure!(
                file.functional_member_owner == if path == MOD_COMPAT { CC } else { FILTER }
                    && file.historical_memberships.len() == 20
                    && file.historical_memberships.keys().eq(contexts.keys()),
                "wrong event member owner or historical membership"
            );
            let mut seen_contexts = BTreeSet::new();
            let mut seen_variants = BTreeSet::new();
            for variant in &file.raw_witness_variants {
                let golden = RAW_GOLDENS
                    .iter()
                    .find(|(oid, ..)| *oid == variant.blob)
                    .ok_or_else(|| eyre::eyre!("unreviewed event witness {}", variant.blob))?;
                ensure!(
                    variant.sha256 == golden.1
                        && variant.bytes == golden.2
                        && variant.cr_count == golden.3
                        && variant.lf_count == golden.4
                        && variant.final_lf
                        && !variant.contexts.is_empty()
                        && seen_variants.insert(variant.blob.as_str()),
                    "event witness metadata changed"
                );
                witnessed_oids.insert(variant.blob.as_str());
                for name in &variant.contexts {
                    let (environment, target) = split_context(name)?;
                    let on = environment == "dev";
                    ensure!(
                        seen_contexts.insert(name.as_str())
                            && variant.blob == expected_oid(path, target, on, on)?
                            && file.historical_memberships[name] == variant.blob,
                        "event witness assigned to wrong context"
                    );
                }
            }
            ensure!(
                seen_contexts == contexts.keys().map(String::as_str).collect::<BTreeSet<_>>(),
                "incomplete event witness context coverage"
            );
        }
        ensure!(
            witnessed_oids
                == RAW_GOLDENS
                    .iter()
                    .map(|(oid, ..)| *oid)
                    .collect::<BTreeSet<_>>(),
            "missing or extra fixed raw event witness"
        );
        for (path, hash, bytes) in AUTHORED_GOLDENS {
            ensure!(
                ledger.files[path].source_sha256 == hash
                    && ledger.files[path].source_bytes == bytes,
                "authored event ledger changed for {path}"
            );
        }
        let inventory = discover_core_source_files(&core.core)?;
        ensure!(
            PATHS.iter().all(|path| inventory.contains(*path)),
            "authored event-discovery source missing"
        );
        Ok(Self {
            core,
            inventory,
            files: ledger.files,
            raw,
        })
    }

    fn render_selected(&self, path: &str, context: &ProjectionContext) -> Result<Vec<u8>> {
        let selected = select_core_inputs(&self.core.metadata, context, &self.inventory)?;
        let input = selected
            .inputs
            .get(path)
            .ok_or_else(|| eyre::eyre!("baseline event class {path} was excluded"))?;
        ensure!(
            input.input == path && !selected.omitted_paths.contains(path),
            "event class selected a historical alternate or became optional"
        );
        let source = self.core.read_source(path)?;
        ensure!(
            source.len() == self.files[path].source_bytes
                && sha256(&source) == self.files[path].source_sha256,
            "authored event source changed from its reviewed ledger"
        );
        Ok(render_java_source(std::str::from_utf8(&source)?, context)?.into_bytes())
    }

    fn assert_case(&self, target: &str, filtering: bool, cc: bool) -> Result<usize> {
        ensure!(!cc || filtering, "invalid fixture: CC requires filtering");
        let features: &[&str] = match (filtering, cc) {
            (false, false) => &[],
            (true, false) => &[FILTER],
            (true, true) => &[FILTER, CC],
            (false, true) => unreachable!(),
        };
        let context = self.core.context(target, features)?;
        for path in PATHS {
            let oid = expected_oid(path, target, filtering, cc)?;
            assert_eq!(
                self.render_selected(path, &context)?,
                self.raw[oid],
                "wrong event output for {target} {path} filter={filtering} cc={cc}"
            );
        }
        Ok(PATHS.len())
    }
}

fn split_context(name: &str) -> Result<(&str, &str)> {
    let (environment, target) = name
        .split_once('/')
        .ok_or_else(|| eyre::eyre!("invalid event witness context"))?;
    ensure!(
        matches!(environment, "release" | "dev")
            && SUPPORTED_TARGETS.iter().any(|(id, _)| *id == target),
        "unsupported event witness context"
    );
    Ok((environment, target))
}

fn expected_oid(path: &str, target: &str, filtering: bool, cc: bool) -> Result<&'static str> {
    ensure!(
        SUPPORTED_TARGETS.iter().any(|(id, _)| *id == target),
        "unsupported event fixture target"
    );
    let forge = matches!(target, "1.19.2" | "1.19.4" | "1.20" | "1.20.1");
    let modscan = matches!(target, "1.21.0" | "1.21.1" | "26.1.2");
    Ok(match path {
        ANNOTATION => match (forge, filtering) {
            (true, false) => "52643e95902a9f200a91715e73ae7eeff63d8243",
            (true, true) => "f60586b6b9d178add4da66ffc33020f607ad989f",
            (false, false) => "c6a1d3916af3813b032288c288199f6a106709b7",
            (false, true) => "2a06f6404fd6902d8fa1400067a01d942c8f8765",
        },
        SUBSCRIBER => match (target == "26.1.2", filtering) {
            (false, false) => "173d3c3a3c2b7bddd1c0680dad4e4a405090bf4e",
            (false, true) => "4f30c4005f8ff1951ebfc0fc7d14d9bae0c93a11",
            (true, false) => "ddfed2b332b88eef1f39fc0a5dae9bc8bb8948e0",
            (true, true) => "1ff5aa95988175902c66a0fe7563c1d3e5e293a8",
        },
        ANNOTATION_UTILS => match (forge, modscan, filtering) {
            (true, false, false) => "657af6e8dfbe90134e7cb27e802f28538542d0b8",
            (true, false, true) => "1dba38b53d201e4b56c7ce0cb884e23678fc2119",
            (false, false, false) => "032ebda77e97d8985ad060437d1808b3412b43eb",
            (false, false, true) => "b6e82e3ecddaa1b71043ce1fedb17cc20452b819",
            (false, true, false) => "990976c1d627e5e19eef97dd434e3d11ea584d73",
            (false, true, true) => "1c5e7089b8e487cb350163b742ee77fe1c21f12b",
            (true, true, _) => unreachable!(),
        },
        MOD_COMPAT => match (forge, target == "26.1.2", cc) {
            (true, false, false) => "4543aa1fdd1cb737534edc2275244f9246214abe",
            (true, false, true) => "ae0c66f4dd1fe80383c20d5a0238f88dd163c049",
            (false, false, false) => "2ed488a5d8dc8965a8f2069354df1a8d3654f014",
            (false, false, true) => "a162489e65c24921fcd7a472b73f4b347464876e",
            (false, true, false) => "86a4b5df2cee4d4516b37c584c88a1fa174bba56",
            (false, true, true) => "b3ebdb4d9eb49f34de45958098f0561472719162",
            (true, true, _) => unreachable!(),
        },
        _ => eyre::bail!("path outside bounded event fixture"),
    })
}

#[test]
fn event_classes_reconstruct_all_twenty_historical_contexts_and_membership() -> Result<()> {
    let fixture = EventFixture::load()?;
    let mut cells = 0;
    for (name, _) in PINNED_CONTEXTS {
        let (environment, target) = split_context(name)?;
        let on = environment == "dev";
        let mut context = fixture
            .core
            .context(target, if on { &[FILTER, CC] } else { &[] })?;
        context.environment = environment.to_owned();
        context.projection_key =
            format!("{}/mc-{target}", if on { "sfm-dev" } else { "sfm-4.34.0" });
        context.preset.clone_from(&context.projection_key);
        for path in PATHS {
            assert_eq!(
                fixture.render_selected(path, &context)?,
                fixture.raw[expected_oid(path, target, on, on)?],
                "raw event witness mismatch {name} {path}"
            );
            cells += 1;
        }
    }
    assert_eq!(cells, 80);
    Ok(())
}

#[test]
fn independent_event_owners_keep_all_four_classes_in_one_hundred_twenty_cells() -> Result<()> {
    let fixture = EventFixture::load()?;
    let mut cells = 0;
    for (target, _) in SUPPORTED_TARGETS {
        for (filtering, cc) in [(false, false), (true, false), (true, true)] {
            cells += fixture.assert_case(target, filtering, cc)?;
        }
    }
    assert_eq!(cells, 120);
    Ok(())
}

#[test]
fn computercraft_refuses_missing_filtering_prerequisite_on_every_target() -> Result<()> {
    let fixture = EventFixture::load()?;
    let mut refused = 0;
    for (target, _) in SUPPORTED_TARGETS {
        let error = fixture
            .core
            .context(target, &[CC])
            .expect_err("CC must not silently omit or auto-enable its prerequisite");
        assert!(error.to_string().contains(FILTER));
        refused += 1;
    }
    assert_eq!(refused, 10);
    for (path, feature) in [(ANNOTATION, FILTER), (MOD_COMPAT, CC)] {
        let mut context = fixture.core.context("1.19.2", &[])?;
        assert_eq!(context.features.remove(feature), Some(false));
        let source = fixture.core.read_source(path)?;
        let error = render_java_source(std::str::from_utf8(&source)?, &context)
            .expect_err("unregistered inactive selector must fail closed");
        assert!(error.to_string().contains(feature));
    }
    Ok(())
}

#[test]
fn filter_only_precedes_class_loading_without_exposing_computercraft_probe() -> Result<()> {
    let fixture = EventFixture::load()?;
    for target in ["1.19.2", "1.20.2", "1.21.0", "26.1.2"] {
        let context = fixture.core.context(target, &[FILTER])?;
        let subscriber = fixture.render_selected(SUBSCRIBER, &context)?;
        let text = std::str::from_utf8(&subscriber)?;
        let filter_position = text
            .find("String requiredModId = annotationData.getString(")
            .ok_or_else(|| eyre::eyre!("optional-mod filter is missing"))?;
        let register_position = text
            .find(".forEach(SFMAutomaticEventSubscriber::tryRegisterAnnotatedMethod)")
            .ok_or_else(|| eyre::eyre!("registration pipeline is missing"))?;
        let load_position = text
            .find("annotationData.tryLoadClass()")
            .ok_or_else(|| eyre::eyre!("class-loading boundary is missing"))?;
        assert!(filter_position < register_position && register_position < load_position);
        let compat = fixture.render_selected(MOD_COMPAT, &context)?;
        assert!(!std::str::from_utf8(&compat)?.contains("isComputerCraftLoaded"));
        assert!(std::str::from_utf8(&compat)?.contains("boolean isModLoaded(String modid)"));
        let utils = fixture.render_selected(ANNOTATION_UTILS, &context)?;
        let utils_text = std::str::from_utf8(&utils)?;
        if matches!(target, "1.21.0" | "26.1.2") {
            assert!(utils_text.contains("return holder.value();"));
        } else {
            assert!(utils_text.contains("return holder.getValue();"));
        }
        if target == "1.21.0" {
            assert_eq!(context.minecraft_version, "1.21");
        }
        if target == "26.1.2" {
            assert!(text.contains("{@link SFM#SFM(IEventBus)}  SFM}."));
            assert!(std::str::from_utf8(&compat)?.contains("Identifier blockId"));
        }
    }
    Ok(())
}
