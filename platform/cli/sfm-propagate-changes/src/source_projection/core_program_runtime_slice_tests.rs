//! Post-promotion source contracts for nine bounded common-program templates.
//!
//! Uses core-owned selection before source reads and the controlled renderer.
//! Frozen Git blobs are test witnesses only. This is not Java runtime/build proof.

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

const PREFIX: &str = "src/main/java/ca/teamdman/sfm/common/program/";
const CORE_PREFIX: &str = "platform/minecraft/core-liquid-template/";
const STAGE_PREFIX: &str =
    "platform/cli/sfm-propagate-changes/target/core-program-runtime-stage-v1/";
const LEDGER: &str = "docs/tasks/sfm-core-program-runtime-slice.json";
const PC: &str = "packet_computation";
const VALUES: &str = "packet_values";
const CLEANUP: &str = "runtime_resource_cleanup";
const EXECUTE: &str = "ExecuteProgramBehaviour.java";
const TRACKER: &str = "IInputResourceTracker.java";
const INPUT: &str = "LimitedInputSlot.java";
const POOL: &str = "LimitedInputSlotObjectPool.java";
const SLOT: &str = "LimitedSlot.java";
const BEHAVIOUR: &str = "ProgramBehaviour.java";
const CONTEXT: &str = "ProgramContext.java";
const SIMULATE: &str = "SimulateExploreAllPathsProgramBehaviour.java";
const WORLD: &str = "WorldProgramInputSource.java";
const NAMES: [&str; 9] = [
    CONTEXT, BEHAVIOUR, EXECUTE, SIMULATE, TRACKER, SLOT, INPUT, POOL, WORLD,
];
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
    raw_digest: &'static str,
    raw_bytes: usize,
    normalized_digest: &'static str,
    normalized_bytes: usize,
    crlf_count: usize,
}
const GOLDENS: [Golden; 19] = [
    Golden {
        oid: "0a7bfd7c48e098750af079b61c4215b08c960f4d",
        raw_digest: "sha256:59372d8fd9fc3b3f929e0f3842e985f09a14d8384194c4d67724ab2869bc51bc",
        raw_bytes: 268,
        normalized_digest: "sha256:59372d8fd9fc3b3f929e0f3842e985f09a14d8384194c4d67724ab2869bc51bc",
        normalized_bytes: 268,
        crlf_count: 0,
    },
    Golden {
        oid: "0f6ecae7d268a55d3c7a1423217ecbd0c7f8ddb4",
        raw_digest: "sha256:afcd26f84b4cdf5c768d900df5ac6f92968dcb2a856a3881440e39d64931a633",
        raw_bytes: 6144,
        normalized_digest: "sha256:afcd26f84b4cdf5c768d900df5ac6f92968dcb2a856a3881440e39d64931a633",
        normalized_bytes: 6144,
        crlf_count: 0,
    },
    Golden {
        oid: "2db441693db7211b35a658b0f3ad2c51423a5052",
        raw_digest: "sha256:7d83d1f087f7cd9dee616a32ea61cae0ea94d6a6a968126b5327d27195bd564b",
        raw_bytes: 242,
        normalized_digest: "sha256:72a5ef53974f3463481f39649d7b8ab72b729b33876151c021901f82663ea257",
        normalized_bytes: 233,
        crlf_count: 9,
    },
    Golden {
        oid: "30ebd70bcdbf0090c42eada620326f43f47d4005",
        raw_digest: "sha256:524cf87655357ed4861d75086d40dc21781cd5555d012474857b2bfef1a0cbd7",
        raw_bytes: 6217,
        normalized_digest: "sha256:524cf87655357ed4861d75086d40dc21781cd5555d012474857b2bfef1a0cbd7",
        normalized_bytes: 6217,
        crlf_count: 0,
    },
    Golden {
        oid: "417520476a613cf9c45585c0735410ffcbcf65ed",
        raw_digest: "sha256:daf5bf93ea8d4e3c2a9e672ef0a9e91c62ec7dba9f3f2ff101f116ea6030b774",
        raw_bytes: 109,
        normalized_digest: "sha256:daf5bf93ea8d4e3c2a9e672ef0a9e91c62ec7dba9f3f2ff101f116ea6030b774",
        normalized_bytes: 109,
        crlf_count: 0,
    },
    Golden {
        oid: "50bb2cd245f4a1a90876cd88167925dac79cdf45",
        raw_digest: "sha256:9791d9924043dc6e31d442b8d59429305a3f35515bad3cd7827787c2b884c952",
        raw_bytes: 6120,
        normalized_digest: "sha256:9791d9924043dc6e31d442b8d59429305a3f35515bad3cd7827787c2b884c952",
        normalized_bytes: 6120,
        crlf_count: 0,
    },
    Golden {
        oid: "5679887783c692add4dd2c5991308878ed71a7ef",
        raw_digest: "sha256:3afd2b675601401453e5243390a450a2e88f0aa723cc32b17c9e4fb7eb2aad80",
        raw_bytes: 9448,
        normalized_digest: "sha256:3afd2b675601401453e5243390a450a2e88f0aa723cc32b17c9e4fb7eb2aad80",
        normalized_bytes: 9448,
        crlf_count: 0,
    },
    Golden {
        oid: "659aa3a9c188277edbdbb3a9d0515cd5ecb92501",
        raw_digest: "sha256:c80607c3678d84ec0a7d2fa96b87aafc4c19e3243ffd477e605cc1b2c279e085",
        raw_bytes: 5677,
        normalized_digest: "sha256:c80607c3678d84ec0a7d2fa96b87aafc4c19e3243ffd477e605cc1b2c279e085",
        normalized_bytes: 5677,
        crlf_count: 0,
    },
    Golden {
        oid: "67326619fa2a01a5d443ea741716dd69ad7d417a",
        raw_digest: "sha256:9a954b56342894e3d5dde1caa95b261bb55c15e00dcf4497abdf88b1764a41a6",
        raw_bytes: 498,
        normalized_digest: "sha256:9a954b56342894e3d5dde1caa95b261bb55c15e00dcf4497abdf88b1764a41a6",
        normalized_bytes: 498,
        crlf_count: 0,
    },
    Golden {
        oid: "6883b3de24222777f57d043e94276bd1d749f663",
        raw_digest: "sha256:1dd72078edd8abafbf3941c838802bc82a193dcb7a8802b8c6d2b7c94b5ab07e",
        raw_bytes: 4888,
        normalized_digest: "sha256:1dd72078edd8abafbf3941c838802bc82a193dcb7a8802b8c6d2b7c94b5ab07e",
        normalized_bytes: 4888,
        crlf_count: 0,
    },
    Golden {
        oid: "728b665718819b01d9cf9d948c3e595b0bb6abdc",
        raw_digest: "sha256:b94d5889c8a6c4b9ce3907eb9769756a65fee9048599cc4e30cc6cd1f9d4536a",
        raw_bytes: 2526,
        normalized_digest: "sha256:b94d5889c8a6c4b9ce3907eb9769756a65fee9048599cc4e30cc6cd1f9d4536a",
        normalized_bytes: 2526,
        crlf_count: 0,
    },
    Golden {
        oid: "7d363a39b9cd024fedb9d5f4f38cb3ebd2453bb4",
        raw_digest: "sha256:17b72bd8aa20a77161d285409d7308774b1098fdcb063d466b58aaa90259816b",
        raw_bytes: 5877,
        normalized_digest: "sha256:17b72bd8aa20a77161d285409d7308774b1098fdcb063d466b58aaa90259816b",
        normalized_bytes: 5877,
        crlf_count: 0,
    },
    Golden {
        oid: "7dc51478ed0893ea7c37390e6190960e69df29bc",
        raw_digest: "sha256:797a70cabf18a85ff95cf75f1b8002ea8eb0d52c747c57ccd5cbdf97eb678fd8",
        raw_bytes: 4275,
        normalized_digest: "sha256:797a70cabf18a85ff95cf75f1b8002ea8eb0d52c747c57ccd5cbdf97eb678fd8",
        normalized_bytes: 4275,
        crlf_count: 0,
    },
    Golden {
        oid: "81630e15ccca9b7be45d0231408b57d99259fdeb",
        raw_digest: "sha256:9849d26eb14c758a07460b15ba959e2eb64ca2a9bb43c18ca9ab88a60b27a025",
        raw_bytes: 331,
        normalized_digest: "sha256:0796a8619d0291257b0f77050064cb53a04124383637bbaf690aac27a9fb0a7b",
        normalized_bytes: 327,
        crlf_count: 4,
    },
    Golden {
        oid: "8c5400dc80f725be724922ce0c34809d7dca2712",
        raw_digest: "sha256:a606d2f62a6abe6482b5222059f71dcb1949abad1fa846a699c5a64fae6c0c6a",
        raw_bytes: 7348,
        normalized_digest: "sha256:a606d2f62a6abe6482b5222059f71dcb1949abad1fa846a699c5a64fae6c0c6a",
        normalized_bytes: 7348,
        crlf_count: 0,
    },
    Golden {
        oid: "bd1a88c82c24bbfe65a5f322f7970432d7899dda",
        raw_digest: "sha256:e44882531457a7ecc17acac727b7da1b5c9bbb168ae77a153c83af038a7f16dd",
        raw_bytes: 2035,
        normalized_digest: "sha256:e44882531457a7ecc17acac727b7da1b5c9bbb168ae77a153c83af038a7f16dd",
        normalized_bytes: 2035,
        crlf_count: 0,
    },
    Golden {
        oid: "cd241a21b57bab91b2d9aea70ba109b16dc400e5",
        raw_digest: "sha256:3d16e35b1820085967a5f762102898d546636140abc5526be887354a60281f00",
        raw_bytes: 5745,
        normalized_digest: "sha256:3d16e35b1820085967a5f762102898d546636140abc5526be887354a60281f00",
        normalized_bytes: 5745,
        crlf_count: 0,
    },
    Golden {
        oid: "dc0a37fad54a28f2decb2135875da07b516803b6",
        raw_digest: "sha256:701bd45eb544499b7a7bf1d3e2119deef795f2a9a2519415ff44fd2635c9593a",
        raw_bytes: 4892,
        normalized_digest: "sha256:701bd45eb544499b7a7bf1d3e2119deef795f2a9a2519415ff44fd2635c9593a",
        normalized_bytes: 4892,
        crlf_count: 0,
    },
    Golden {
        oid: "dd62422f264fa0e5cbdf32bda373f654837656dd",
        raw_digest: "sha256:4916f44452448c4ec1fba51153d615b792fae17afbe2aa062477432bace2405c",
        raw_bytes: 425,
        normalized_digest: "sha256:4916f44452448c4ec1fba51153d615b792fae17afbe2aa062477432bace2405c",
        normalized_bytes: 425,
        crlf_count: 0,
    },
];
#[derive(Clone, Copy)]
struct Template {
    name: &'static str,
    digest: &'static str,
    bytes: usize,
}
const TEMPLATES: [Template; 9] = [
    Template {
        name: EXECUTE,
        digest: "sha256:405aad15a2b769544dad72db515a00cab1dc9b62147caa6f4655988ef81e8ba1",
        bytes: 387,
    },
    Template {
        name: TRACKER,
        digest: "sha256:9d19c6fcd71a70a8f464e9ce003d532646082ae07e474014ec04bb7c5beaa6b8",
        bytes: 2586,
    },
    Template {
        name: INPUT,
        digest: "sha256:18cf4da79e7a5e68e38d336d25a344665f1ed41f476c88278d089d5ef1307879",
        bytes: 6585,
    },
    Template {
        name: POOL,
        digest: "sha256:048f407a4f40981c361bfae87ce578fe8e14ac163f4d9634984becbf0fa1f107",
        bytes: 6442,
    },
    Template {
        name: SLOT,
        digest: "sha256:4557b99f78e2df8f4983582744784900f22c29754729187943b031070f5b0fc3",
        bytes: 813,
    },
    Template {
        name: BEHAVIOUR,
        digest: "sha256:1b75b72cdbe50cd0119c3801ac0ff94f996d4d35fdbb9ce7bc8f62e1eb14ea74",
        bytes: 328,
    },
    Template {
        name: CONTEXT,
        digest: "sha256:7eefdc1c709bdb841e566d260172b239ca362745c5630fc30142f38e87d308c1",
        bytes: 9027,
    },
    Template {
        name: SIMULATE,
        digest: "sha256:2595c23fc1876eda8742bf3bfce84076854618f9005d05aaf3dda55260c95757",
        bytes: 6102,
    },
    Template {
        name: WORLD,
        digest: "sha256:3afd2b675601401453e5243390a450a2e88f0aa723cc32b17c9e4fb7eb2aad80",
        bytes: 9448,
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
    mode: Option<String>,
    raw_blob: Option<String>,
    raw_sha256: Option<String>,
    raw_bytes: Option<usize>,
    normalized_sha256: Option<String>,
    normalized_bytes: Option<usize>,
    explicit_features: Vec<String>,
    feature_context_origin: String,
    stage_preview_normalized_exact: Option<bool>,
}

struct RuntimeFixture {
    core: CoreTestFixture,
    inventory: BTreeSet<String>,
    normalized: BTreeMap<String, Vec<u8>>,
}
impl RuntimeFixture {
    fn load() -> Result<Self> {
        let core = CoreTestFixture::load()?;
        let ledger_path = checked_file(&core.repository, LEDGER)?;
        let ledger_bytes = read_bounded(&ledger_path, 1024 * 1024)?;
        let ledger: Ledger = facet_json::from_str(std::str::from_utf8(&ledger_bytes)?)?;
        ensure!(
            ledger.schema == "sfm:core_program_runtime_slice@1"
                && ledger.scope == "nine_bounded_common_program_inputs",
            "program runtime ledger scope changed"
        );
        let pinned: BTreeMap<String, String> = COMMITS
            .into_iter()
            .map(|(context, oid)| (context.to_owned(), oid.to_owned()))
            .collect();
        ensure!(
            ledger.source_context_commits == pinned && ledger.inputs.len() == NAMES.len(),
            "runtime context/path scope changed"
        );
        let definition = core
            .features
            .0
            .get(PC)
            .ok_or_else(|| eyre::eyre!("missing packet runtime owner"))?;
        ensure!(
            same_names(&definition.supported_targets, &TARGETS[..2])
                && same_names(&definition.requires, &[VALUES, CLEANUP]),
            "packet runtime ownership/prerequisites changed"
        );
        let mut names = BTreeSet::new();
        let mut all_variants = BTreeSet::new();
        let mut present = 0;
        let mut absent = 0;
        for input in ledger.inputs {
            let name = input
                .path
                .strip_prefix(PREFIX)
                .ok_or_else(|| eyre::eyre!("unexpected runtime path"))?;
            let template = template(name)?;
            ensure!(
                names.insert(name.to_owned())
                    && input.core_input == format!("{CORE_PREFIX}{}", input.path)
                    && input.staged_input == format!("{STAGE_PREFIX}{name}")
                    && input.template_bytes == template.bytes
                    && input.template_sha256 == template.digest,
                "runtime template provenance changed"
            );
            let expected_targets = if name == WORLD { &TARGETS[..2] } else { &[] };
            let expected_all = if name == WORLD { &[PC][..] } else { &[] };
            ensure!(
                same_names(&input.membership.targets, expected_targets)
                    && same_names(&input.membership.all_features, expected_all)
                    && input.membership.any_features.is_empty()
                    && input.membership.none_features.is_empty(),
                "ledger runtime membership differs from contract"
            );
            if let Some(rules) = core.metadata.source_rules.get(&input.path) {
                ensure!(
                    rules.len() == 1
                        && rules[0].input == input.path
                        && same_names(&rules[0].when.targets, expected_targets)
                        && same_names(&rules[0].when.all_features, expected_all)
                        && rules[0].when.any_features.is_empty()
                        && rules[0].when.none_features.is_empty(),
                    "actual runtime source owner differs from contract"
                );
            } else {
                ensure!(
                    name != WORLD,
                    "world input needs explicit sparse source ownership"
                );
            }
            let mut local_variants = BTreeSet::new();
            for variant in input.raw_variants {
                let fixed = golden(&variant.oid)?;
                ensure!(
                    local_variants.insert(variant.oid.clone())
                        && variant.raw_sha256 == fixed.raw_digest
                        && variant.raw_bytes == fixed.raw_bytes
                        && variant.normalized_sha256 == fixed.normalized_digest
                        && variant.normalized_bytes == fixed.normalized_bytes
                        && variant.crlf_to_lf_count == fixed.crlf_count
                        && !variant.appended_final_lf
                        && !variant.other_token_edits,
                    "runtime raw/normalization witness changed"
                );
                all_variants.insert(variant.oid);
            }
            ensure!(
                input.witnesses.len() == 20,
                "incomplete runtime context evidence"
            );
            let mut contexts = BTreeSet::new();
            let mut witnessed = BTreeSet::new();
            for witness in input.witnesses {
                let (environment, target) = witness
                    .context
                    .split_once('/')
                    .ok_or_else(|| eyre::eyre!("invalid runtime context"))?;
                let pc = environment == "dev" && is_d2(target);
                let oid = expected_oid(name, target, pc)?;
                let flags = owner_flags(pc);
                core.context(target, &flags)?;
                ensure!(
                    contexts.insert(witness.context.clone())
                        && pinned.get(&witness.context) == Some(&witness.source_commit)
                        && witness.present == oid.is_some()
                        && witness.raw_blob.as_deref() == oid
                        && same_names(&witness.explicit_features, &flags)
                        && witness.feature_context_origin
                            == "reconstructed_explicit_owner_selection_not_historical_feature_claim",
                    "runtime historical identity/member contract changed"
                );
                if let Some(oid) = oid {
                    let fixed = golden(oid)?;
                    ensure!(
                        witness.mode.as_deref() == Some("100644")
                            && witness.raw_sha256.as_deref() == Some(fixed.raw_digest)
                            && witness.raw_bytes == Some(fixed.raw_bytes)
                            && witness.normalized_sha256.as_deref()
                                == Some(fixed.normalized_digest)
                            && witness.normalized_bytes == Some(fixed.normalized_bytes)
                            && witness.stage_preview_normalized_exact == Some(true),
                        "runtime full byte witness changed"
                    );
                    witnessed.insert(oid.to_owned());
                    present += 1;
                } else {
                    ensure!(
                        witness.mode.is_none()
                            && witness.raw_sha256.is_none()
                            && witness.raw_bytes.is_none()
                            && witness.normalized_sha256.is_none()
                            && witness.normalized_bytes.is_none()
                            && witness.stage_preview_normalized_exact.is_none(),
                        "absent runtime source has an invented witness"
                    );
                    absent += 1;
                }
            }
            ensure!(
                local_variants == witnessed,
                "runtime variant coverage changed"
            );
        }
        ensure!(
            names == NAMES.into_iter().map(str::to_owned).collect()
                && all_variants == GOLDENS.iter().map(|row| row.oid.to_owned()).collect()
                && (present, absent) == (162, 18),
            "runtime cohort/member totals changed"
        );
        let raw = read_git_blobs(&core.repository, &all_variants)?;
        let mut normalized = BTreeMap::new();
        for fixed in GOLDENS {
            let bytes = &raw[fixed.oid];
            ensure!(
                bytes.len() == fixed.raw_bytes
                    && sha256(bytes) == fixed.raw_digest
                    && bytes.windows(2).filter(|pair| *pair == b"\r\n").count() == fixed.crlf_count,
                "fixed runtime raw blob changed"
            );
            let output = normalize(bytes, fixed.crlf_count != 0)?;
            ensure!(
                output.len() == fixed.normalized_bytes
                    && sha256(&output) == fixed.normalized_digest
                    && output.len() == bytes.len() - fixed.crlf_count,
                "runtime normalization exceeded approved byte edits"
            );
            normalized.insert(fixed.oid.to_owned(), output);
        }
        let inventory = discover_core_source_files(&core.core)?;
        ensure!(
            NAMES
                .iter()
                .all(|name| inventory.contains(&format!("{PREFIX}{name}"))),
            "program runtime inputs must be promoted to real core before these tests"
        );
        Ok(Self {
            core,
            inventory,
            normalized,
        })
    }

    fn render(&self, name: &str, context: &ProjectionContext) -> Result<Option<Vec<u8>>> {
        let path = format!("{PREFIX}{name}");
        let selection = self
            .core
            .selection_for_assertion(context, &self.inventory)?;
        let Some(input) = selection.inputs.get(&path) else {
            return Ok(None);
        };
        ensure!(input.input == path, "unreviewed alternate runtime source");
        let source = self.core.read_source(&path)?;
        let fixed = template(name)?;
        ensure!(
            source.len() == fixed.bytes
                && sha256(&source) == fixed.digest
                && !source.contains(&b'\r')
                && source.ends_with(b"\n"),
            "promoted runtime template changed"
        );
        Ok(Some(
            render_java_source(std::str::from_utf8(&source)?, context)?.into_bytes(),
        ))
    }
    fn assert_profile(&self, target: &str, flags: &[&str]) -> Result<(usize, usize)> {
        let context = self.core.context(target, flags)?;
        let pc = context.features[PC];
        let mut present = 0;
        let mut absent = 0;
        for name in NAMES {
            let output = self.render(name, &context)?;
            if let Some(oid) = expected_oid(name, target, pc)? {
                ensure!(
                    output.as_deref() == Some(self.normalized[oid].as_slice()),
                    "runtime full rendered witness mismatch: {name} / {target}"
                );
                present += 1;
            } else {
                ensure!(
                    output.is_none(),
                    "disabled runtime source was read/rendered"
                );
                absent += 1;
            }
        }
        Ok((present, absent))
    }
    fn body(&self, name: &str, context: &ProjectionContext) -> Result<String> {
        let bytes = self
            .render(name, context)?
            .ok_or_else(|| eyre::eyre!("runtime body expected"))?;
        Ok(String::from_utf8(bytes)?)
    }
}
fn normalize(raw: &[u8], allow_crlf: bool) -> Result<Vec<u8>> {
    ensure!(raw.ends_with(b"\n"), "runtime slice cannot add terminal LF");
    for (index, byte) in raw.iter().enumerate() {
        if *byte == b'\r' {
            ensure!(
                allow_crlf && raw.get(index + 1) == Some(&b'\n'),
                "unapproved or lone CR"
            );
        }
    }
    Ok(std::str::from_utf8(raw)?.replace("\r\n", "\n").into_bytes())
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
        .ok_or_else(|| eyre::eyre!("unexpected runtime template"))
}
fn golden(oid: &str) -> Result<Golden> {
    GOLDENS
        .iter()
        .find(|row| row.oid == oid)
        .copied()
        .ok_or_else(|| eyre::eyre!("unexpected runtime blob"))
}
fn expected_oid(name: &str, target: &str, pc: bool) -> Result<Option<&'static str>> {
    ensure!(
        TARGETS.contains(&target) && (!pc || is_d2(target)),
        "unsupported runtime profile"
    );
    let oid = match name {
        EXECUTE if pc => "81630e15ccca9b7be45d0231408b57d99259fdeb",
        EXECUTE => "2db441693db7211b35a658b0f3ad2c51423a5052",
        TRACKER if pc => "728b665718819b01d9cf9d948c3e595b0bb6abdc",
        TRACKER => "bd1a88c82c24bbfe65a5f322f7970432d7899dda",
        INPUT if pc => "cd241a21b57bab91b2d9aea70ba109b16dc400e5",
        INPUT => "7dc51478ed0893ea7c37390e6190960e69df29bc",
        POOL if pc => "30ebd70bcdbf0090c42eada620326f43f47d4005",
        POOL if !matches!(target, "1.19.2" | "1.19.4" | "1.20" | "1.20.1") => {
            "dc0a37fad54a28f2decb2135875da07b516803b6"
        }
        POOL => "6883b3de24222777f57d043e94276bd1d749f663",
        SLOT if pc => "67326619fa2a01a5d443ea741716dd69ad7d417a",
        SLOT => "dd62422f264fa0e5cbdf32bda373f654837656dd",
        BEHAVIOUR if pc => "0a7bfd7c48e098750af079b61c4215b08c960f4d",
        BEHAVIOUR => "417520476a613cf9c45585c0735410ffcbcf65ed",
        CONTEXT if pc => "8c5400dc80f725be724922ce0c34809d7dca2712",
        CONTEXT if target == "26.1.2" => "50bb2cd245f4a1a90876cd88167925dac79cdf45",
        CONTEXT => "0f6ecae7d268a55d3c7a1423217ecbd0c7f8ddb4",
        SIMULATE if pc => "7d363a39b9cd024fedb9d5f4f38cb3ebd2453bb4",
        SIMULATE => "659aa3a9c188277edbdbb3a9d0515cd5ecb92501",
        WORLD if pc => "5679887783c692add4dd2c5991308878ed71a7ef",
        WORLD => return Ok(None),
        _ => return Err(eyre::eyre!("unexpected runtime profile input")),
    };
    Ok(Some(oid))
}

#[test]
fn program_runtime_slice_matches_twenty_historical_byte_and_member_contexts() -> Result<()> {
    let fixture = RuntimeFixture::load()?;
    let mut present = 0;
    let mut absent = 0;
    for (name, _) in COMMITS {
        let (environment, target) = name.split_once('/').expect("fixed context");
        let (p, a) =
            fixture.assert_profile(target, &owner_flags(environment == "dev" && is_d2(target)))?;
        present += p;
        absent += a;
    }
    assert_eq!((present, absent), (162, 18));
    Ok(())
}

#[test]
fn cleanup_values_and_client_manager_do_not_enable_packet_runtime_implicitly() -> Result<()> {
    let fixture = RuntimeFixture::load()?;
    let mut profiles = 0;
    let mut present = 0;
    let mut absent = 0;
    for target in TARGETS {
        let flags = if is_d2(target) {
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
        for enabled in flags {
            let (p, a) = fixture.assert_profile(target, &enabled)?;
            profiles += 1;
            present += p;
            absent += a;
        }
    }
    assert_eq!((profiles, present, absent), (20, 162, 18));
    for target in &TARGETS[..2] {
        assert!(fixture.core.context(target, &[PC]).is_err());
        assert!(fixture.core.context(target, &[PC, VALUES]).is_err());
        assert!(fixture.core.context(target, &[PC, CLEANUP]).is_err());
        let cleanup = fixture.core.context(target, &[CLEANUP])?;
        assert!(!cleanup.features[PC]);
        let body = fixture.body(CONTEXT, &cleanup)?;
        assert!(body.contains("private final List<InputStatement> INPUTS = new ArrayList<>();"));
        assert!(body.contains("INPUTS.addAll(other.INPUTS);"));
        assert!(body.contains("INPUTS.forEach(InputStatement::freeSlots);"));
        assert!(!body.contains("ProgramExecutionScope"));
        assert!(fixture.body(SIMULATE, &cleanup)?.contains("context.getInputs().forEach(inputStatement -> onInputStatementDropped(context, inputStatement));"));
    }
    Ok(())
}

#[test]
fn packet_runtime_scope_and_materialization_contracts_remain_separate_from_simulation() -> Result<()>
{
    let fixture = RuntimeFixture::load()?;
    for target in &TARGETS[..2] {
        let on = fixture.core.context(target, &owner_flags(true))?;
        let off = fixture.core.context(target, &[])?;
        let context = fixture.body(CONTEXT, &on)?;
        assert_eq!(context.matches("new ProgramExecutionScope()").count(), 3);
        assert!(!context.contains("INPUTS.addAll(other.INPUTS);"));
        assert!(context.contains("EXECUTION_SCOPE.free();"));
        assert!(
            context.contains("getVariableEnvironment()")
                && context.contains("getEphemeralResourceOwner()")
        );
        assert!(
            fixture
                .body(BEHAVIOUR, &on)?
                .contains("default boolean allowsRuntimeMaterialization()")
        );
        assert!(fixture.body(EXECUTE, &on)?.contains("return true;"));
        let simulation = fixture.body(SIMULATE, &on)?;
        assert!(
            simulation
                .contains("public boolean allowsRuntimeMaterialization() {\n        return false;")
        );
        assert!(
            simulation.contains(".flatMap(inputSource -> inputSource.inputStatement().stream())")
        );
        for name in [BEHAVIOUR, EXECUTE, SIMULATE] {
            assert!(
                !fixture
                    .body(name, &off)?
                    .contains("allowsRuntimeMaterialization")
            );
        }
        assert!(
            fixture
                .body(TRACKER, &on)?
                .contains("return new ObservationInputResourceTracker(this);")
        );
        assert!(!fixture.body(TRACKER, &off)?.contains("forkForObservation"));
    }
    Ok(())
}

#[test]
fn generated_slot_provenance_preserves_baseline_pool_guards_and_world_cache_contracts() -> Result<()>
{
    let fixture = RuntimeFixture::load()?;
    for target in TARGETS {
        for pc in if is_d2(target) {
            &[false, true][..]
        } else {
            &[false][..]
        } {
            let context = fixture.core.context(target, &owner_flags(*pc))?;
            let pool = fixture.body(POOL, &context)?;
            assert!(pool.contains("Release called on already freed input slot"));
            assert!(pool.contains("Release batch called on already freed input slot"));
            assert!(pool.contains("LEASED.remove(slot) == null"));
            assert_eq!(pool.contains("acquireGenerated("), *pc);
            let input = fixture.body(INPUT, &context)?;
            assert_eq!(input.contains("generatedSourceDescription = null;"), *pc);
            assert_eq!(input.contains("public @Nullable BlockPos pos;"), *pc);
            assert_eq!(input.contains("public void initGenerated("), *pc);
            if *pc {
                assert!(
                    input.contains("this.pos = null;")
                        && input.contains("this.label = null;")
                        && input.contains("this.direction = null;")
                );
                let world = fixture.body(WORLD, &context)?;
                assert!(world.contains("freeSlotsIf(slot -> labels.contains(slot.label));"));
                assert!(world.contains("transferSlotsTo(retainedSource);"));
                assert!(world.contains("if (retainedLabels.isEmpty()) {\n            retainedSource.free();\n            return null;"));
                assert!(world.contains("LimitedInputSlotObjectPool.release(limitedInputSlotsCache);\n            limitedInputSlotsCache = null;"));
            }
        }
    }
    Ok(())
}

#[test]
fn runtime_mc_seams_and_output_identity_follow_explicit_values_not_key_or_environment() -> Result<()>
{
    let fixture = RuntimeFixture::load()?;
    for target in TARGETS {
        let context = fixture.core.context(target, &[])?;
        let body = fixture.body(CONTEXT, &context)?;
        assert_eq!(
            body.contains("LABEL_POSITIONS = LabelPositionHolder.from(manager.getDisk());"),
            target == "26.1.2"
        );
        let pool = fixture.body(POOL, &context)?;
        let forge = matches!(target, "1.19.2" | "1.19.4" | "1.20" | "1.20.1");
        assert_eq!(
            pool.contains("import net.minecraftforge.event.server.ServerStoppedEvent;"),
            forge
        );
        assert_eq!(
            pool.contains("import net.neoforged.neoforge.event.server.ServerStoppedEvent;"),
            !forge
        );
        for pc in if is_d2(target) {
            &[false, true][..]
        } else {
            &[false][..]
        } {
            let context = fixture.core.context(target, &owner_flags(*pc))?;
            let mut renamed = context.clone();
            renamed.environment = "release".to_owned();
            renamed.projection_key = "arbitrary/nested/program-review".to_owned();
            renamed.preset = "unrelated-visible-label".to_owned();
            for name in NAMES {
                assert_eq!(
                    fixture.render(name, &context)?,
                    fixture.render(name, &renamed)?
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
fn runtime_normalization_refuses_unapproved_cr_eof_and_token_changes() -> Result<()> {
    let _fixture = RuntimeFixture::load()?;
    assert_eq!(normalize(b" x \r\n\r\n", true)?, b" x \n\n");
    assert_eq!(normalize(b"x\n\n", false)?, b"x\n\n");
    assert!(normalize(b"x\r\n", false).is_err());
    assert!(normalize(b"x\ry\n", true).is_err());
    assert!(normalize(b"x", true).is_err());
    assert!(normalize(b"", true).is_err());
    assert!(normalize(&[0xff, b'\n'], false).is_err());
    assert_eq!(GOLDENS.iter().filter(|row| row.crlf_count > 0).count(), 2);
    assert_eq!(GOLDENS.iter().map(|row| row.crlf_count).sum::<usize>(), 13);
    Ok(())
}
