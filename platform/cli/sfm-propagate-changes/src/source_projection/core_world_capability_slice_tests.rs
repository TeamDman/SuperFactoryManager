//! Post-promotion source contracts for the three cable/capability templates.
//!
//! Historical Git blobs are bounded migration witnesses, never production inputs.
//! Tests use actual core selection and rendering. They prove source contracts,
//! not Java compilation, tunnel invalidation or gameplay behavior.

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

const LEDGER: &str = "docs/tasks/sfm-core-world-capability-slice.json";
const CORE_PREFIX: &str = "platform/minecraft/core-liquid-template/";
const STAGE_PREFIX: &str =
    "platform/cli/sfm-propagate-changes/target/core-world-capability-stage-v1/";
const CC: &str = "computercraft";
const LIVE: &str = "redstone_live_read";
const BUFFER: &str = "redstone_buffer_storage";
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
    raw_sha256: &'static str,
    raw_bytes: usize,
    crlf: usize,
    normalized_sha256: &'static str,
    normalized_bytes: usize,
}
const GOLDENS: [Golden; 12] = [
    Golden {
        oid: "596a02f63a40f839049d574263aa6ea25071bb87",
        raw_sha256: "sha256:ffa985f57f223997a7f3823fcad77471cc8dc8701510cd86faa3c6914a934977",
        raw_bytes: 4538,
        crlf: 0,
        normalized_sha256: "sha256:ffa985f57f223997a7f3823fcad77471cc8dc8701510cd86faa3c6914a934977",
        normalized_bytes: 4538,
    },
    Golden {
        oid: "5a91927656653a682ccb9009aefce2ad9b426f0a",
        raw_sha256: "sha256:17776a3d58435b6d4936ff9435976df99301bc75aaf383f65f66cbda8b8a3880",
        raw_bytes: 4882,
        crlf: 0,
        normalized_sha256: "sha256:17776a3d58435b6d4936ff9435976df99301bc75aaf383f65f66cbda8b8a3880",
        normalized_bytes: 4882,
    },
    Golden {
        oid: "6672997e4957938f63c1542531e1bdd2d842150a",
        raw_sha256: "sha256:b21a2a9588015f0d8e8ca51200b1aefc5ea1d30c526757edc59e2d1c699f4cad",
        raw_bytes: 8274,
        crlf: 0,
        normalized_sha256: "sha256:b21a2a9588015f0d8e8ca51200b1aefc5ea1d30c526757edc59e2d1c699f4cad",
        normalized_bytes: 8274,
    },
    Golden {
        oid: "72ac274d61adb7c7b2ce767e4d8e8e472a05d23a",
        raw_sha256: "sha256:0a6e9e490d6a278b46461d5ff3c4ccbc798842a6f428b93cd452b353376abed9",
        raw_bytes: 4546,
        crlf: 0,
        normalized_sha256: "sha256:0a6e9e490d6a278b46461d5ff3c4ccbc798842a6f428b93cd452b353376abed9",
        normalized_bytes: 4546,
    },
    Golden {
        oid: "7c4ca6f91814baf0ffce806979e0f9dedfd835ec",
        raw_sha256: "sha256:d28277c38f7099956f459e0d97e7346d24f15eed16b54edb0ed705e610f2a902",
        raw_bytes: 8273,
        crlf: 0,
        normalized_sha256: "sha256:d28277c38f7099956f459e0d97e7346d24f15eed16b54edb0ed705e610f2a902",
        normalized_bytes: 8273,
    },
    Golden {
        oid: "86f46c7b6b53183fbf651bf022458020f8395c2a",
        raw_sha256: "sha256:61f87a9f780549e24fec626e5c532b6bfff89a7acca419871cf1f8b1bfffab22",
        raw_bytes: 10425,
        crlf: 253,
        normalized_sha256: "sha256:a7baf7bd9780d9ae9d842fc827f50f6d4f9ec771a5d72e19d2db7411d54670b8",
        normalized_bytes: 10172,
    },
    Golden {
        oid: "8abc3ad74c6a1379c41723cdeaeb06b27ec7b884",
        raw_sha256: "sha256:87022fbc11f63bb0591ab1c7c047d147c22b1eb25fb2dec05ec4c1cf2d4fc5d6",
        raw_bytes: 9833,
        crlf: 245,
        normalized_sha256: "sha256:acd5686d2b8372f4e4b3d0b5c677d61e37ce8e0d5ccd457c5a79558e4b787969",
        normalized_bytes: 9588,
    },
    Golden {
        oid: "8ad66b367d8c2650a84dc4a50e33a9f45a8886ed",
        raw_sha256: "sha256:51d46c2ab679d4f3f9ff76bb5b6a83ce6fef009322e985c565171528824ae234",
        raw_bytes: 4890,
        crlf: 0,
        normalized_sha256: "sha256:51d46c2ab679d4f3f9ff76bb5b6a83ce6fef009322e985c565171528824ae234",
        normalized_bytes: 4890,
    },
    Golden {
        oid: "8e64ac463c72c06ed286d54e571c7b3721e7e6f0",
        raw_sha256: "sha256:8d3362e5f290c75fafcac21aea2cc68cfda42b6dc71917504974f8cb65a6e894",
        raw_bytes: 9968,
        crlf: 249,
        normalized_sha256: "sha256:975229aba6bc30be0b24c6c73674d01f5ed7b3fb3fbf7ea1f9d8c8d35e4caf35",
        normalized_bytes: 9719,
    },
    Golden {
        oid: "df778bd931b5773af5dd6ea775b7256d76fa8ec5",
        raw_sha256: "sha256:22b3fb441c32612853a3c7bfe385e56646bf087ad94d7adb33ec019e3f856e1d",
        raw_bytes: 9964,
        crlf: 249,
        normalized_sha256: "sha256:dbc051fd908233b7aa45e0d0bccd05eb6ef768fc76a8ee7c3ab13e746666374c",
        normalized_bytes: 9715,
    },
    Golden {
        oid: "e39db8e70eb314c63926fc5b6f1c86d39243b362",
        raw_sha256: "sha256:63492f6bbea649a6c8ec219ecbbbd70177bb597895c80b3f9afac39ad38dda18",
        raw_bytes: 7575,
        crlf: 0,
        normalized_sha256: "sha256:63492f6bbea649a6c8ec219ecbbbd70177bb597895c80b3f9afac39ad38dda18",
        normalized_bytes: 7575,
    },
    Golden {
        oid: "fc977ede523a7006a6ce841e32c6c8019b76dc88",
        raw_sha256: "sha256:52699a6a09772347784cd8949b9e2779b748c1d4fe1bde470d45ab2d88aec9e4",
        raw_bytes: 7574,
        crlf: 0,
        normalized_sha256: "sha256:52699a6a09772347784cd8949b9e2779b748c1d4fe1bde470d45ab2d88aec9e4",
        normalized_bytes: 7574,
    },
];
#[derive(Clone, Copy)]
struct Source {
    path: &'static str,
    template_sha256: &'static str,
    template_bytes: usize,
}
const SOURCES: [Source; 3] = [
    Source {
        path: "src/main/java/ca/teamdman/sfm/common/block_network/CableNetwork.java",
        template_sha256: "sha256:0158ed0e74219f95acdd210c90b727183ffa528b0c6f560572a441e6ad505b4b",
        template_bytes: 8562,
    },
    Source {
        path: "src/main/java/ca/teamdman/sfm/common/block_network/CableNetworkManager.java",
        template_sha256: "sha256:bc46b493d7a2631ad2627f45ddeb84381f325798fc0ef3fdba29d4cd1a4089b4",
        template_bytes: 5136,
    },
    Source {
        path: "src/main/java/ca/teamdman/sfm/common/capability/SFMBlockCapabilityDiscovery.java",
        template_sha256: "sha256:98f98b29e3e088c971b137bf4e97e02ddc28ff380ea6dc203e99892b5433c88e",
        template_bytes: 11566,
    },
];
#[derive(Facet)]
struct Ledger {
    schema: String,
    context_commits: BTreeMap<String, String>,
    files: Vec<FileEvidence>,
}
#[derive(Facet)]
struct FileEvidence {
    path: String,
    intended_core_path: String,
    stage_path: String,
    template_sha256: String,
    template_bytes: usize,
    membership: String,
    raw_variants: Vec<RawEvidence>,
    witnesses: Vec<Witness>,
}
#[derive(Facet)]
struct RawEvidence {
    oid: String,
    raw_sha256: String,
    raw_bytes: usize,
    crlf_count: usize,
    lone_cr_count: usize,
    final_lf: bool,
    normalization: String,
    normalized_sha256: String,
    normalized_bytes: usize,
}
#[derive(Facet)]
struct Witness {
    context: String,
    source_commit: String,
    oid: String,
    explicit_owner_roots: Vec<String>,
    proof: String,
    authoring_reconstruction_equal: bool,
    feature_context_origin: String,
}
struct Fixture {
    core: CoreTestFixture,
    inventory: BTreeSet<String>,
    raw: BTreeMap<String, Vec<u8>>,
    normalized: BTreeMap<String, Vec<u8>>,
}
impl Fixture {
    fn load() -> Result<Self> {
        let core = CoreTestFixture::load()?;
        let bytes = read_bounded(&checked_file(&core.repository, LEDGER)?, 1024 * 1024)?;
        let ledger: Ledger = facet_json::from_str(std::str::from_utf8(&bytes)?)?;
        ensure!(
            ledger.schema == "sfm:core_world_capability_slice@1",
            "world ledger schema changed"
        );
        let commits: BTreeMap<String, String> = COMMITS
            .into_iter()
            .map(|(c, id)| (c.to_owned(), id.to_owned()))
            .collect();
        ensure!(
            ledger.context_commits == commits && ledger.files.len() == 3,
            "world ledger scope changed"
        );
        let mut seen_paths = BTreeSet::new();
        let mut seen_blobs = BTreeSet::new();
        for file in ledger.files {
            let source = source(&file.path)?;
            ensure!(
                seen_paths.insert(file.path.clone())
                    && file.intended_core_path == format!("{CORE_PREFIX}{}", source.path)
                    && file.stage_path == format!("{STAGE_PREFIX}{}", file_name(source.path))
                    && file.template_sha256 == source.template_sha256
                    && file.template_bytes == source.template_bytes
                    && file.membership
                        == "unconditional_shared_all_ten_targets_no_sparse_owner_rule_required",
                "world template owner or membership changed"
            );
            ensure!(
                file.raw_variants.len() == 4 && file.witnesses.len() == 20,
                "world matrix incomplete"
            );
            let mut file_oids = BTreeSet::new();
            for evidence in file.raw_variants {
                let golden = golden(&evidence.oid)?;
                ensure!(
                    file_oids.insert(evidence.oid.clone())
                        && seen_blobs.insert(evidence.oid)
                        && evidence.raw_sha256 == golden.raw_sha256
                        && evidence.raw_bytes == golden.raw_bytes
                        && evidence.crlf_count == golden.crlf
                        && evidence.lone_cr_count == 0
                        && evidence.final_lf
                        && evidence.normalization == normalization_name(golden)
                        && evidence.normalized_sha256 == golden.normalized_sha256
                        && evidence.normalized_bytes == golden.normalized_bytes,
                    "raw world witness or approved normalization changed"
                );
            }
            let mut contexts = BTreeSet::new();
            for witness in file.witnesses {
                let (environment, target) = witness
                    .context
                    .split_once('/')
                    .ok_or_else(|| eyre::eyre!("malformed fixed world context"))?;
                let roots = witness_roots(environment, target);
                ensure!(
                    contexts.insert(witness.context.clone())
                        && ledger.context_commits.get(&witness.context)
                            == Some(&witness.source_commit)
                        && witness.oid == expected_oid(source.path, environment == "dev", target)?
                        && file_oids.contains(&witness.oid)
                        && same_names(&witness.explicit_owner_roots, &roots)
                        && witness.proof
                            == "independent_literal_authoring_reconstruction_not_production_renderer"
                        && witness.authoring_reconstruction_equal
                        && witness.feature_context_origin
                            == "reconstructed_explicit_owner_selection_not_historical_feature_flag_claim",
                    "frozen context or immutable owner roots changed"
                );
                // Actual registry closure is independently checked; it does not
                // rewrite the historical owner-root witness or bypass failures.
                closed_context(&core, target, &roots)?;
            }
            ensure!(
                contexts == ledger.context_commits.keys().cloned().collect(),
                "world context scope changed"
            );
        }
        ensure!(
            seen_blobs.len() == 12 && seen_paths.len() == 3,
            "world file or blob coverage changed"
        );
        let raw = read_git_blobs(&core.repository, &seen_blobs)?;
        let mut normalized = BTreeMap::new();
        for fixed in GOLDENS {
            normalized.insert(fixed.oid.to_owned(), normalize_raw(&raw[fixed.oid], fixed)?);
        }
        let inventory = discover_core_source_files(&core.core)?;
        for source in SOURCES {
            ensure!(
                inventory.contains(source.path),
                "world template not promoted"
            );
            verify_template(&core.read_source(source.path)?, source)?;
        }
        Ok(Self {
            core,
            inventory,
            raw,
            normalized,
        })
    }
    fn render(&self, path: &str, context: &ProjectionContext) -> Result<Vec<u8>> {
        let selection = self
            .core
            .selection_for_assertion(context, &self.inventory)?;
        let selected = selection
            .inputs
            .get(path)
            .ok_or_else(|| eyre::eyre!("shared world source omitted: {path}"))?;
        ensure!(
            selected.input == path && !selection.omitted_paths.contains(path),
            "world historical adapter or omission"
        );
        let bytes = self.core.read_source(path)?;
        verify_template(&bytes, source(path)?)?;
        Ok(render_java_source(std::str::from_utf8(&bytes)?, context)?.into_bytes())
    }
    fn compare(&self, target: &str, roots: &[&str], development: bool) -> Result<usize> {
        let context = closed_context(&self.core, target, roots)?;
        for source in SOURCES {
            let expected = expected_oid(source.path, development, target)?;
            ensure!(
                self.render(source.path, &context)? == self.normalized[expected],
                "full world witness bytes changed: {} / {target}",
                source.path
            );
        }
        Ok(SOURCES.len())
    }
}
fn source(path: &str) -> Result<Source> {
    SOURCES
        .iter()
        .copied()
        .find(|s| s.path == path)
        .ok_or_else(|| eyre::eyre!("world source outside exact cohort"))
}
fn file_name(path: &str) -> &str {
    path.rsplit('/').next().expect("fixed portable source")
}
fn golden(oid: &str) -> Result<Golden> {
    GOLDENS
        .iter()
        .copied()
        .find(|g| g.oid == oid)
        .ok_or_else(|| eyre::eyre!("world blob outside immutable witness set"))
}
fn is_d2(target: &str) -> bool {
    matches!(target, "1.19.2" | "1.19.4")
}
fn old_forge(target: &str) -> bool {
    matches!(target, "1.19.2" | "1.19.4" | "1.20" | "1.20.1")
}
fn old_capability(target: &str) -> bool {
    old_forge(target) || target == "1.20.2"
}
fn witness_roots(environment: &str, target: &str) -> Vec<&'static str> {
    if environment == "dev" {
        if is_d2(target) {
            vec![CC, LIVE, BUFFER]
        } else {
            vec![CC]
        }
    } else {
        vec![]
    }
}
fn same_names(actual: &[String], expected: &[&str]) -> bool {
    actual.len() == expected.len()
        && actual.iter().map(String::as_str).collect::<BTreeSet<_>>()
            == expected.iter().copied().collect()
}
fn closed_context(
    core: &CoreTestFixture,
    target: &str,
    roots: &[&str],
) -> Result<ProjectionContext> {
    let mut names = BTreeSet::new();
    let mut pending = roots
        .iter()
        .map(|name| (*name).to_owned())
        .collect::<Vec<_>>();
    while let Some(name) = pending.pop() {
        let definition = core
            .features
            .0
            .get(&name)
            .ok_or_else(|| eyre::eyre!("unknown requested world owner: {name}"))?;
        ensure!(
            definition.supported_targets.iter().any(|id| id == target),
            "unsupported world owner: {name}"
        );
        if names.insert(name) {
            pending.extend(definition.requires.iter().cloned());
        }
    }
    let borrowed = names.iter().map(String::as_str).collect::<Vec<_>>();
    core.context(target, &borrowed)
}
fn expected_oid(path: &str, development: bool, target: &str) -> Result<&'static str> {
    ensure!(TARGETS.contains(&target), "unknown world witness target");
    match file_name(path) {
        "CableNetwork.java" => Ok(match (development, target == "26.1.2") {
            (false, false) => "fc977ede523a7006a6ce841e32c6c8019b76dc88",
            (true, false) => "7c4ca6f91814baf0ffce806979e0f9dedfd835ec",
            (false, true) => "e39db8e70eb314c63926fc5b6f1c86d39243b362",
            (true, true) => "6672997e4957938f63c1542531e1bdd2d842150a",
        }),
        "CableNetworkManager.java" => Ok(match (development, old_forge(target)) {
            (false, true) => "596a02f63a40f839049d574263aa6ea25071bb87",
            (true, true) => "5a91927656653a682ccb9009aefce2ad9b426f0a",
            (false, false) => "72ac274d61adb7c7b2ce767e4d8e8e472a05d23a",
            (true, false) => "8ad66b367d8c2650a84dc4a50e33a9f45a8886ed",
        }),
        "SFMBlockCapabilityDiscovery.java" => Ok(if development && is_d2(target) {
            "86f46c7b6b53183fbf651bf022458020f8395c2a"
        } else if old_capability(target) {
            "8abc3ad74c6a1379c41723cdeaeb06b27ec7b884"
        } else if target == "26.1.2" {
            "df778bd931b5773af5dd6ea775b7256d76fa8ec5"
        } else {
            "8e64ac463c72c06ed286d54e571c7b3721e7e6f0"
        }),
        _ => Err(eyre::eyre!("world witness path outside cohort")),
    }
}
fn normalization_name(golden: Golden) -> &'static str {
    if golden.crlf == 0 {
        "raw_exact"
    } else {
        "sfm:java_crlf_to_lf@1"
    }
}
fn normalize_raw(bytes: &[u8], golden: Golden) -> Result<Vec<u8>> {
    ensure!(
        bytes.len() == golden.raw_bytes && sha256(bytes) == golden.raw_sha256,
        "world raw blob drift"
    );
    let text = std::str::from_utf8(bytes)?;
    ensure!(
        text.ends_with('\n') && !text.starts_with('\u{feff}'),
        "world newline or BOM drift"
    );
    let crlf = text.matches("\r\n").count();
    ensure!(
        crlf == golden.crlf && !text.replace("\r\n", "").contains('\r'),
        "world CRLF or lone-CR drift"
    );
    let normalized = text.replace("\r\n", "\n").into_bytes();
    ensure!(
        normalized.len() == golden.normalized_bytes
            && sha256(&normalized) == golden.normalized_sha256,
        "world normalization exceeded exact approval"
    );
    Ok(normalized)
}
fn verify_template(bytes: &[u8], source: Source) -> Result<()> {
    ensure!(
        bytes.len() == source.template_bytes
            && sha256(bytes) == source.template_sha256
            && !bytes.contains(&b'\r')
            && bytes.ends_with(b"\n"),
        "shared world template bytes changed"
    );
    std::str::from_utf8(bytes)?;
    Ok(())
}
fn isolated_render(
    core: &Path,
    metadata: &CoreProjectInputs,
    context: &ProjectionContext,
    path: &str,
) -> Result<Vec<u8>> {
    let inventory = discover_core_source_files(core)?;
    let selection = select_core_inputs(metadata, context, &inventory)?;
    let selected = selection
        .inputs
        .get(path)
        .ok_or_else(|| eyre::eyre!("isolated shared world input omitted"))?;
    ensure!(selected.input == path, "isolated historical adapter");
    let bytes = read_bounded(&checked_file(core, path)?, 1024 * 1024)?;
    Ok(render_java_source(std::str::from_utf8(&bytes)?, context)?.into_bytes())
}
#[test]
fn world_capability_matches_all_sixty_frozen_sources_with_exact_normalization() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut count = 0;
    for (context, _) in COMMITS {
        let (environment, target) = context.split_once('/').expect("fixed world context");
        count += fixture.compare(
            target,
            &witness_roots(environment, target),
            environment == "dev",
        )?;
    }
    assert_eq!(count, 60);
    Ok(())
}
#[test]
fn world_capability_computercraft_and_redstone_owners_are_independent() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut combinations = 0;
    for target in TARGETS {
        for cc in [false, true] {
            let roots = if cc { vec![CC] } else { vec![] };
            let context = closed_context(&fixture.core, target, &roots)?;
            for source in &SOURCES[..2] {
                assert_eq!(
                    fixture.render(source.path, &context)?,
                    fixture.normalized[expected_oid(source.path, cc, target)?]
                );
            }
            assert_eq!(
                fixture.render(SOURCES[2].path, &context)?,
                fixture.normalized[expected_oid(SOURCES[2].path, false, target)?]
            );
            combinations += 1;
        }
    }
    assert_eq!(combinations, 20);
    for target in &TARGETS[..2] {
        for mask in 0..8u8 {
            let roots = [CC, LIVE, BUFFER]
                .into_iter()
                .enumerate()
                .filter_map(|(bit, owner)| (mask & (1 << bit) != 0).then_some(owner))
                .collect::<Vec<_>>();
            let context = closed_context(&fixture.core, target, &roots)?;
            let cable = String::from_utf8(fixture.render(SOURCES[0].path, &context)?)?;
            let manager = String::from_utf8(fixture.render(SOURCES[1].path, &context)?)?;
            let discovery = String::from_utf8(fixture.render(SOURCES[2].path, &context)?)?;
            assert_eq!(cable.contains("getManagers()"), mask & 1 != 0);
            assert_eq!(
                manager.contains("getNetworkFromCablePosition("),
                mask & 1 != 0
            );
            assert_eq!(discovery.contains("state.isSignalSource()"), mask & 2 != 0);
            assert_eq!(discovery.contains("instanceof BufferBlock"), mask & 4 != 0);
            assert_eq!(
                discovery.contains("import ca.teamdman.sfm.common.block.BufferBlock;"),
                mask & 4 != 0
            );
            assert_eq!(
                discovery.contains("BlockState state = level.getBlockState(pos);"),
                mask & 6 != 0
            );
            if mask & 6 == 6 {
                assert!(discovery.contains("if (state.isSignalSource() || state.getBlock() instanceof BufferBlock) return true;"));
            }
            assert!(
                !context.features["client_manager"],
                "world helpers implicitly enable Client Manager"
            );
        }
        let client = closed_context(&fixture.core, target, &["client_manager"])?;
        for source in SOURCES {
            assert_eq!(
                fixture.render(source.path, &client)?,
                fixture.normalized[expected_oid(source.path, false, target)?]
            );
        }
    }
    for target in &TARGETS[2..] {
        assert!(closed_context(&fixture.core, target, &[LIVE]).is_err());
        assert!(closed_context(&fixture.core, target, &[BUFFER]).is_err());
    }
    let prerequisites = &fixture.core.features.0[CC].requires;
    for required in prerequisites {
        let full = closed_context(&fixture.core, "1.19.2", &[CC])?;
        let missing = full
            .features
            .iter()
            .filter(|(name, enabled)| **enabled && name.as_str() != required)
            .map(|(name, _)| name.as_str())
            .collect::<Vec<_>>();
        assert!(
            fixture.core.context("1.19.2", &missing).is_err(),
            "required CC closure bypassed"
        );
    }
    Ok(())
}
#[test]
fn world_capability_api_and_cache_lifecycle_remain_exact() -> Result<()> {
    let fixture = Fixture::load()?;
    for target in TARGETS {
        let context = closed_context(&fixture.core, target, &[])?;
        let cable = String::from_utf8(fixture.render(SOURCES[0].path, &context)?)?;
        let manager = String::from_utf8(fixture.render(SOURCES[1].path, &context)?)?;
        let discovery = String::from_utf8(fixture.render(SOURCES[2].path, &context)?)?;
        assert_eq!(
            cable.contains("dimension().identifier()"),
            target == "26.1.2"
        );
        assert_eq!(cable.contains("dimension().location()"), target != "26.1.2");
        assert_eq!(
            manager.contains("import net.minecraftforge.event.level.ChunkEvent;"),
            old_forge(target)
        );
        assert_eq!(
            manager.contains("import net.neoforged.neoforge.event.level.ChunkEvent;"),
            !old_forge(target)
        );
        assert_eq!(
            discovery.contains("if (!(levelAccessor instanceof Level level))"),
            !old_capability(target)
        );
        assert_eq!(
            discovery.contains("LevelReader levelAccessor"),
            target == "26.1.2"
        );
        assert!(cable.contains("levelCapabilityCache.bustCacheForChunk(chunkPos);"));
        assert!(cable.contains("super.purgeChunk(chunkPos);"));
        assert!(cable.contains("levelCapabilityCache.putAll(otherCable.levelCapabilityCache);"));
        assert!(cable.contains("branch.levelCapabilityCache.overwriteFromOther("));
        assert!(manager.contains("if (level.isClientSide()) return Optional.empty();"));
        assert!(manager.contains("NETWORK_MANAGER.purgeChunk(level, chunk.getPos());"));
        assert!(manager.contains("NETWORK_MANAGER.clearLevel(level);"));
        assert!(discovery.contains("if (cached.isPresent()) return cached;"));
        assert!(discovery.contains("if (!cableNetwork.isAdjacentToCable(pos))"));
        assert!(
            discovery
                .contains("if (!(cableNetwork.getLevel() instanceof ServerLevel serverLevel))")
        );
        assert!(
            discovery.contains("levelCapabilityCache.putCapability(pos, capKind, direction, cap);")
        );
        assert!(
            discovery.find("if (cached.isPresent()) return cached;")
                < discovery.find("if (!cableNetwork.isAdjacentToCable(pos))")
        );
        assert!(
            !discovery.contains("invalidateCapabilities"),
            "cohort started invalidating foreign machines"
        );
    }
    Ok(())
}
#[test]
fn world_capability_common_edit_propagates_without_version_dumps() -> Result<()> {
    let fixture = Fixture::load()?;
    let isolated = tempfile::tempdir()?;
    let isolated_core = isolated.path().join(super::core_inputs::CORE_ROOT);
    let mut metadata = fixture.core.metadata.clone();
    metadata.source_rules.clear();
    metadata.project_files.clear();
    for source in SOURCES {
        let destination = isolated_core.join(source.path);
        fs::create_dir_all(destination.parent().expect("fixed source parent"))?;
        let original = String::from_utf8(fixture.core.read_source(source.path)?)?;
        let marker = "// isolated common-edit source proof\n";
        fs::write(destination, format!("{marker}{original}"))?;
    }
    for target in ["1.19.2", "1.21.1", "26.1.2"] {
        let context = closed_context(&fixture.core, target, &[])?;
        for source in SOURCES {
            let mut expected = b"// isolated common-edit source proof\n".to_vec();
            expected
                .extend_from_slice(&fixture.normalized[expected_oid(source.path, false, target)?]);
            assert_eq!(
                isolated_render(&isolated_core, &metadata, &context, source.path)?,
                expected
            );
        }
    }
    Ok(())
}
#[test]
fn world_capability_hash_path_and_normalization_mutations_fail_closed() -> Result<()> {
    let fixture = Fixture::load()?;
    for fixed in GOLDENS {
        let mut mutated = fixture.raw[fixed.oid].clone();
        mutated.push(b' ');
        assert!(normalize_raw(&mutated, fixed).is_err());
        let mut altered_contract = fixed;
        altered_contract.crlf += 1;
        assert!(normalize_raw(&fixture.raw[fixed.oid], altered_contract).is_err());
    }
    assert!(source("src/../elsewhere.java").is_err());
    assert!(golden("0000000000000000000000000000000000000000").is_err());
    let mut template = fixture.core.read_source(SOURCES[0].path)?;
    template.push(b' ');
    assert!(verify_template(&template, SOURCES[0]).is_err());
    let context = closed_context(&fixture.core, "1.19.2", &[])?;
    let mut metadata = fixture.core.metadata.clone();
    let ambiguous = super::core_inputs::InputVariant {
        input: SOURCES[0].path.to_owned(),
        when: super::core_inputs::InputPredicate::default(),
        template: false,
    };
    metadata.source_rules.insert(
        SOURCES[0].path.to_owned(),
        vec![ambiguous.clone(), ambiguous],
    );
    assert!(select_core_inputs(&metadata, &context, &fixture.inventory).is_err());
    assert!(closed_context(&fixture.core, "1.19.2", &["unknown_world_owner"]).is_err());
    Ok(())
}
