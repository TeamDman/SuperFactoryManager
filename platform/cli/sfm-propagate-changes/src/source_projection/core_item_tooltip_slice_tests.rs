//! Frozen source-only ItemUtils and packet tooltip migration goldens.
//!
//! Historical blobs are bounded offline test oracles, never production inputs.
//! Actual core selection proves presence or absence; a source preview alone
//! does not. The optional semantic mode and packet value owners stay independent.
//! Later intentional core edits require deliberately updating these goldens.
//! No Java compilation, physical keyboard, menu, or gameplay proof is claimed.

#![cfg(test)]

use super::context::ProjectionContext;
use super::core_inputs::select_core_inputs;
use super::core_slice_test_support::CoreTestFixture;
use super::core_slice_test_support::read_bounded;
use super::core_slice_test_support::read_git_blobs;
use super::provenance::sha256;
use super::release_baseline::frozen_git_command;
use super::render_java_source;
use eyre::Result;
use eyre::WrapErr;
use eyre::ensure;
use facet::Facet;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::fs;
use std::io::Read;
use std::process::Stdio;

const LEDGER: &str = "docs/tasks/sfm-core-item-tooltip-slice.json";
const MAX_BYTES: u64 = 128 * 1024;
const FLAGS: [&str; 2] = ["packet_values", "tooltip_mode_override"];
const UTILS: usize = 0;
const PACKET: usize = 1;
const FORMATTER: usize = 2;
const MODE: usize = 3;
const PACKET_BLOB: &str = "b4c5e6f596c79b4ba8b4f7e8b0fd753dbddae542";
const FORMATTER_BLOB: &str = "b31c1e1ca814dc8ed766ee49797f76481e169263";
const MODE_BLOB: &str = "044929a47703748f952feb1a5818afadaaecb4a5";
const NORMALIZATION: &str = "sfm:java_lf_final_newline@1";
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
struct FileGolden {
    path: &'static str,
    bytes: u64,
    digest: &'static str,
}
const FILES: [FileGolden; 4] = [
    FileGolden {
        path: "src/main/java/ca/teamdman/sfm/common/util/SFMItemUtils.java",
        bytes: 5333,
        digest: "sha256:c4f5e07b7c436fb2b1ac6cf881dc8d5bafe41ff27c7933dfa85d5921bc01ba9d",
    },
    FileGolden {
        path: "src/main/java/ca/teamdman/sfm/common/item/PacketItem.java",
        bytes: 4289,
        digest: "sha256:9858cfb50bafaecef4a4529e5398364b54307e7c042e677cb12cfa05800e4dbe",
    },
    FileGolden {
        path: "src/main/java/ca/teamdman/sfm/common/value/SFMPacketTooltipFormatter.java",
        bytes: 1765,
        digest: "sha256:1310b8c4dd3ee4ec177ea3f5f7a310ebc5d39b83369c7803607c1d9554612096",
    },
    FileGolden {
        path: "src/main/java/ca/teamdman/sfm/client/tooltip/SFMTooltipModeService.java",
        bytes: 1126,
        digest: "sha256:bf4b5c4c1ebdcac7ff5b803db3bf55d7ecbf137157640141ab760531b3734f17",
    },
];
struct RawGolden {
    blob: &'static str,
    raw_bytes: u64,
    raw_digest: &'static str,
    expected_bytes: u64,
    expected_digest: &'static str,
    crlf_count: usize,
}
const RAW: [RawGolden; 8] = [
    RawGolden {
        blob: "044929a47703748f952feb1a5818afadaaecb4a5",
        raw_bytes: 1126,
        raw_digest: "sha256:bf4b5c4c1ebdcac7ff5b803db3bf55d7ecbf137157640141ab760531b3734f17",
        expected_bytes: 1126,
        expected_digest: "sha256:bf4b5c4c1ebdcac7ff5b803db3bf55d7ecbf137157640141ab760531b3734f17",
        crlf_count: 0,
    },
    RawGolden {
        blob: "b4c5e6f596c79b4ba8b4f7e8b0fd753dbddae542",
        raw_bytes: 4138,
        raw_digest: "sha256:3e7bf2561dcd61f17e2dfffe364f61ae8cc89dad09c79a59c68c1ae7ad359a70",
        expected_bytes: 4138,
        expected_digest: "sha256:3e7bf2561dcd61f17e2dfffe364f61ae8cc89dad09c79a59c68c1ae7ad359a70",
        crlf_count: 0,
    },
    RawGolden {
        blob: "829100dc0889ea294bdb2ada5dcb32a6e858c495",
        raw_bytes: 4353,
        raw_digest: "sha256:78fcc384cce9799a144c6805cd035a2a54f367c26978147b528dd0d79c2e89ca",
        expected_bytes: 4267,
        expected_digest: "sha256:63536b7b522e1f05f85156baa00cf006f36446600c712af67aff7d802a1f388c",
        crlf_count: 86,
    },
    RawGolden {
        blob: "b31c1e1ca814dc8ed766ee49797f76481e169263",
        raw_bytes: 1765,
        raw_digest: "sha256:1310b8c4dd3ee4ec177ea3f5f7a310ebc5d39b83369c7803607c1d9554612096",
        expected_bytes: 1765,
        expected_digest: "sha256:1310b8c4dd3ee4ec177ea3f5f7a310ebc5d39b83369c7803607c1d9554612096",
        crlf_count: 0,
    },
    RawGolden {
        blob: "cebb35ed3369cc199ac3cd49a1bb1efa23cdb929",
        raw_bytes: 3329,
        raw_digest: "sha256:23f9c97847b9d94a3a1cc457d82324c6d797e477d9700939512b9d33a2123953",
        expected_bytes: 3234,
        expected_digest: "sha256:c582456e21f757dea648c507e3a985b01b12ddec0a8238a61cf3f7a713554afe",
        crlf_count: 95,
    },
    RawGolden {
        blob: "4116e47f0cfab4662720c85711061d11a8db9f83",
        raw_bytes: 3335,
        raw_digest: "sha256:190e13d40a5c8b198c0745a7ac5529e55fd8bccab8ef25c67dae82adbb1e031f",
        expected_bytes: 3240,
        expected_digest: "sha256:27b079522099f54eb2b7974c3358155d23751ce53c419da7b42fe22e8139f6d5",
        crlf_count: 95,
    },
    RawGolden {
        blob: "9005bb07095920701fd56975f1b044cc9c06bcc4",
        raw_bytes: 3369,
        raw_digest: "sha256:3700d25c2b9c12179d12ea4c0700a2e721a0578669cdecb19014ae675307c44f",
        expected_bytes: 3274,
        expected_digest: "sha256:318913e120d65ced8a3045b816978d55f2ae17def73cbe737fa9c1436d92ca96",
        crlf_count: 95,
    },
    RawGolden {
        blob: "1a706cbbece9c9c64dcde8e76e7801a26a06b572",
        raw_bytes: 3325,
        raw_digest: "sha256:a8234b5497343da65a8800c02f1fb82ad8847904ed0b64578b1bab97b03bca77",
        expected_bytes: 3230,
        expected_digest: "sha256:e72f3c3c5d02eaaa6e40a568c890c003d0062808ba9e7128bc5e168df5aa18cd",
        crlf_count: 95,
    },
];

#[derive(Facet)]
struct Ledger {
    schema: String,
    context_commits: BTreeMap<String, String>,
    owners: BTreeMap<String, Owner>,
    normalization: Normalization,
    files: Vec<AuthoredFile>,
    raw_witnesses: Vec<RawWitness>,
}
#[derive(Facet)]
struct Owner {
    support: Vec<String>,
    requires: Vec<String>,
}
#[derive(Facet)]
struct Normalization {
    policy: String,
    approved_git_blobs: Vec<String>,
    crlf_to_lf_only: bool,
    append_final_lf: bool,
    other_whitespace_changes: bool,
}
#[derive(Facet)]
struct AuthoredFile {
    intended_core_path: String,
    stage_bytes: u64,
    stage_sha256: String,
    witnesses: Vec<Witness>,
}
#[derive(Facet)]
struct Witness {
    context: String,
    present: bool,
    git_blob: Option<String>,
    raw_bytes: u64,
    raw_sha256: Option<String>,
    expected_bytes: u64,
    expected_sha256: Option<String>,
    crlf_count: usize,
    lf_added: bool,
    normalization: String,
}
#[derive(Facet)]
struct RawWitness {
    path: String,
    git_blob: String,
    raw_bytes: u64,
    raw_sha256: String,
    expected_bytes: u64,
    expected_sha256: String,
    crlf_count: usize,
    lone_cr_count: usize,
    final_lf: bool,
    bom: bool,
    lf_added: bool,
    contexts: Vec<String>,
}
struct Fixture {
    shared: CoreTestFixture,
    ledger: Ledger,
    sources: Vec<Vec<u8>>,
    raw: BTreeMap<String, Vec<u8>>,
    expected: BTreeMap<String, Vec<u8>>,
}
impl Fixture {
    fn load() -> Result<Self> {
        let shared = CoreTestFixture::load()?;
        let ledger_bytes = read_bounded(&shared.repository.join(LEDGER), MAX_BYTES)?;
        let ledger: Ledger = facet_json::from_str(std::str::from_utf8(&ledger_bytes)?)
            .wrap_err("cannot parse bounded item tooltip golden ledger")?;
        let raw = read_git_blobs(
            &shared.repository,
            &RAW.iter().map(|row| row.blob.to_owned()).collect(),
        )?;
        let expected = RAW
            .iter()
            .map(|row| {
                let bytes = raw
                    .get(row.blob)
                    .ok_or_else(|| eyre::eyre!("missing raw tooltip blob"))?;
                Ok((row.blob.to_owned(), reviewed_bytes(row, bytes)?))
            })
            .collect::<Result<BTreeMap<_, _>>>()?;
        let sources = FILES
            .iter()
            .map(|row| shared.read_source(row.path))
            .collect::<Result<Vec<_>>>()?;
        let result = Self {
            shared,
            ledger,
            sources,
            raw,
            expected,
        };
        result.validate()?;
        Ok(result)
    }
    fn validate(&self) -> Result<()> {
        let commits = PINNED_CONTEXTS
            .into_iter()
            .map(|(name, commit)| (name.to_owned(), commit.to_owned()))
            .collect::<BTreeMap<_, _>>();
        ensure!(
            self.ledger.schema == "sfm:core-item-tooltip-slice@1"
                && self.ledger.context_commits == commits,
            "frozen item tooltip context identity changed"
        );
        ensure!(self.ledger.owners.len() == 2, "owner scope changed");
        for name in FLAGS {
            let proposed = self
                .ledger
                .owners
                .get(name)
                .ok_or_else(|| eyre::eyre!("missing tooltip owner"))?;
            let actual = self
                .shared
                .features
                .0
                .get(name)
                .ok_or_else(|| eyre::eyre!("tooltip owner is not registered"))?;
            ensure!(
                proposed.support == ["1.19.2", "1.19.4"]
                    && proposed.requires.is_empty()
                    && proposed.support == actual.supported_targets
                    && proposed.requires == actual.requires,
                "independent owner support/prerequisites changed"
            );
        }
        let normalization = &self.ledger.normalization;
        ensure!(
            normalization.policy == NORMALIZATION
                && normalization.crlf_to_lf_only
                && !normalization.append_final_lf
                && !normalization.other_whitespace_changes
                && normalization.approved_git_blobs.len() == 5
                && normalization
                    .approved_git_blobs
                    .iter()
                    .map(String::as_str)
                    .collect::<BTreeSet<_>>()
                    == RAW
                        .iter()
                        .filter(|row| row.crlf_count > 0)
                        .map(|row| row.blob)
                        .collect(),
            "exact five-blob CRLF-only approval changed"
        );
        ensure!(self.ledger.files.len() == 4, "file scope changed");
        let mut all_present = 0;
        for (index, (file, golden)) in self.ledger.files.iter().zip(&FILES).enumerate() {
            ensure!(
                file.intended_core_path == golden.path
                    && file.stage_bytes == golden.bytes
                    && file.stage_sha256 == golden.digest
                    && self.sources[index].len() as u64 == golden.bytes
                    && sha256(&self.sources[index]) == golden.digest
                    && !self.sources[index].contains(&b'\r')
                    && self.sources[index].ends_with(b"\n")
                    && !self.sources[index].starts_with(&[0xef, 0xbb, 0xbf]),
                "promoted core tooltip template hash/bytes changed"
            );
            ensure!(file.witnesses.len() == 20, "twenty-context scope changed");
            let mut seen = BTreeSet::new();
            for witness in &file.witnesses {
                ensure!(seen.insert(witness.context.as_str()), "duplicate witness");
                let oid = witness_oid(index, &witness.context)?;
                let golden = oid.map(raw_golden).transpose()?;
                ensure!(
                    commits.contains_key(&witness.context)
                        && witness.present == oid.is_some()
                        && witness.git_blob.as_deref() == oid
                        && witness.raw_bytes == golden.map_or(0, |row| row.raw_bytes)
                        && witness.raw_sha256.as_deref() == golden.map(|row| row.raw_digest)
                        && witness.expected_bytes == golden.map_or(0, |row| row.expected_bytes)
                        && witness.expected_sha256.as_deref()
                            == golden.map(|row| row.expected_digest)
                        && witness.crlf_count == golden.map_or(0, |row| row.crlf_count)
                        && !witness.lf_added
                        && witness.normalization
                            == if golden.is_some_and(|row| row.crlf_count > 0) {
                                NORMALIZATION
                            } else {
                                "none"
                            },
                    "frozen item tooltip byte/membership witness changed"
                );
                all_present += usize::from(witness.present);
            }
            ensure!(
                seen == commits.keys().map(String::as_str).collect(),
                "missing context membership"
            );
        }
        ensure!(all_present == 26, "present-cell count changed");
        ensure!(self.ledger.raw_witnesses.len() == 8, "raw scope changed");
        let mut seen_raw = BTreeSet::new();
        for row in &self.ledger.raw_witnesses {
            ensure!(
                seen_raw.insert(row.git_blob.as_str()),
                "duplicate raw identity"
            );
            let golden = raw_golden(&row.git_blob)?;
            let index = if golden.crlf_count > 0 {
                UTILS
            } else {
                match golden.blob {
                    PACKET_BLOB => PACKET,
                    FORMATTER_BLOB => FORMATTER,
                    MODE_BLOB => MODE,
                    _ => eyre::bail!("unreviewed raw leaf"),
                }
            };
            let contexts = PINNED_CONTEXTS
                .iter()
                .filter_map(|(name, _)| {
                    (witness_oid(index, name).ok().flatten() == Some(golden.blob)).then_some(*name)
                })
                .collect::<BTreeSet<_>>();
            ensure!(
                row.path == FILES[index].path
                    && row.raw_bytes == golden.raw_bytes
                    && row.raw_sha256 == golden.raw_digest
                    && row.expected_bytes == golden.expected_bytes
                    && row.expected_sha256 == golden.expected_digest
                    && row.crlf_count == golden.crlf_count
                    && row.lone_cr_count == 0
                    && row.final_lf
                    && !row.bom
                    && !row.lf_added
                    && row.contexts.len() == contexts.len()
                    && row
                        .contexts
                        .iter()
                        .map(String::as_str)
                        .collect::<BTreeSet<_>>()
                        == contexts,
                "raw and normalized identity evidence changed"
            );
        }
        Ok(())
    }
    fn selected(&self, index: usize, context: &ProjectionContext) -> Result<bool> {
        let path = FILES[index].path;
        let selection = select_core_inputs(&self.shared.metadata, context, &inventory())?;
        let input = selection.inputs.get(path);
        if let Some(input) = input {
            ensure!(
                input.input == path
                    && (input.template || path.ends_with(".java"))
                    && !selection.omitted_paths.contains(path),
                "tooltip source is not the exact core template"
            );
        } else {
            ensure!(
                selection.omitted_paths.contains(path),
                "omitted tooltip source is not an explicit omission"
            );
        }
        Ok(input.is_some())
    }
    fn render(&self, index: usize, context: &ProjectionContext) -> Result<String> {
        ensure!(
            self.selected(index, context)?,
            "cannot render omitted tooltip source"
        );
        render_java_source(std::str::from_utf8(&self.sources[index])?, context)
    }
    fn expected_body(
        &self,
        index: usize,
        target: &str,
        packet: bool,
        mode: bool,
    ) -> Result<Option<String>> {
        let oid = match index {
            UTILS => Some(utils_oid(target, mode)?),
            PACKET => packet.then_some(PACKET_BLOB),
            FORMATTER => packet.then_some(FORMATTER_BLOB),
            MODE => mode.then_some(MODE_BLOB),
            _ => eyre::bail!("invalid source index"),
        };
        let Some(oid) = oid else {
            return Ok(None);
        };
        let bytes = self
            .expected
            .get(oid)
            .ok_or_else(|| eyre::eyre!("missing independent oracle"))?;
        let mut body = std::str::from_utf8(bytes)?.to_owned();
        if index == PACKET && !mode {
            let old = "        appendTooltipLines(stack, lines, SFMItemUtils.isClientAndMoreInfoRequested());\n";
            ensure!(
                body.matches(old).count() == 1,
                "physical fallback oracle anchor changed"
            );
            body = body.replacen(old, "        appendTooltipLines(stack, lines, SFMItemUtils.isClientAndMoreInfoKeyPressed());\n", 1);
        }
        Ok(Some(body))
    }
}
fn inventory() -> BTreeSet<String> {
    FILES.iter().map(|row| row.path.to_owned()).collect()
}
fn raw_golden(blob: &str) -> Result<&'static RawGolden> {
    RAW.iter()
        .find(|row| row.blob == blob)
        .ok_or_else(|| eyre::eyre!("unreviewed raw blob"))
}
fn target_of(name: &str) -> Result<&str> {
    name.split_once('/')
        .map(|(_, target)| target)
        .ok_or_else(|| eyre::eyre!("invalid source proof context"))
}
fn is_witnessed_development(name: &str) -> bool {
    matches!(name, "dev/1.19.2" | "dev/1.19.4")
}
fn utils_oid(target: &str, mode: bool) -> Result<&'static str> {
    match target {
        "1.19.2" | "1.19.4" if mode => Ok("829100dc0889ea294bdb2ada5dcb32a6e858c495"),
        "1.19.2" | "1.19.4" => Ok("1a706cbbece9c9c64dcde8e76e7801a26a06b572"),
        "1.20" | "1.20.1" | "1.20.2" | "1.20.3" | "1.20.4" => {
            Ok("cebb35ed3369cc199ac3cd49a1bb1efa23cdb929")
        }
        "1.21.0" | "1.21.1" => Ok("4116e47f0cfab4662720c85711061d11a8db9f83"),
        "26.1.2" => Ok("9005bb07095920701fd56975f1b044cc9c06bcc4"),
        _ => eyre::bail!("unreviewed target"),
    }
}
fn witness_oid(index: usize, name: &str) -> Result<Option<&'static str>> {
    let development = is_witnessed_development(name);
    match index {
        UTILS => Ok(Some(utils_oid(target_of(name)?, development)?)),
        PACKET => Ok(development.then_some(PACKET_BLOB)),
        FORMATTER => Ok(development.then_some(FORMATTER_BLOB)),
        MODE => Ok(development.then_some(MODE_BLOB)),
        _ => eyre::bail!("invalid witness path index"),
    }
}
fn reviewed_bytes(golden: &RawGolden, bytes: &[u8]) -> Result<Vec<u8>> {
    ensure!(
        bytes.len() as u64 == golden.raw_bytes
            && sha256(bytes) == golden.raw_digest
            && !bytes.starts_with(&[0xef, 0xbb, 0xbf])
            && bytes.ends_with(b"\n")
            && bytes.windows(2).filter(|pair| *pair == b"\r\n").count() == golden.crlf_count,
        "raw bytes are not the exact approved witness"
    );
    for (index, byte) in bytes.iter().enumerate() {
        ensure!(
            *byte != b'\r' || bytes.get(index + 1) == Some(&b'\n'),
            "lone CR rejected"
        );
    }
    let text = std::str::from_utf8(bytes)?;
    let expected = if golden.crlf_count == 0 {
        bytes.to_vec()
    } else {
        text.replace("\r\n", "\n").into_bytes()
    };
    ensure!(
        expected.len() as u64 == golden.expected_bytes
            && sha256(&expected) == golden.expected_digest
            && expected.ends_with(b"\n")
            && !expected.contains(&b'\r'),
        "approved normalized identity mismatch"
    );
    Ok(expected)
}

#[test]
fn eighty_frozen_memberships_and_twenty_six_raw_or_normalized_bodies_match_real_core() -> Result<()>
{
    let fixture = Fixture::load()?;
    let mut present = 0;
    let mut absent = 0;
    for (name, _) in PINNED_CONTEXTS {
        let development = is_witnessed_development(name);
        let context = fixture
            .shared
            .context(target_of(name)?, if development { &FLAGS } else { &[] })?;
        for index in 0..FILES.len() {
            let oid = witness_oid(index, name)?;
            assert_eq!(
                fixture.selected(index, &context)?,
                oid.is_some(),
                "{name}: {}",
                FILES[index].path
            );
            if let Some(oid) = oid {
                let expected = fixture
                    .expected
                    .get(oid)
                    .ok_or_else(|| eyre::eyre!("missing witness oracle"))?;
                assert_eq!(
                    fixture.render(index, &context)?.as_bytes(),
                    expected,
                    "{name}: {}",
                    FILES[index].path
                );
                present += 1;
            } else {
                absent += 1;
            }
        }
    }
    assert_eq!((present, absent), (26, 54));
    Ok(())
}

#[test]
fn packet_and_semantic_mode_owners_are_independent_with_physical_key_fallback() -> Result<()> {
    let fixture = Fixture::load()?;
    let profiles: [&[&str]; 4] = [&[], &["packet_values"], &["tooltip_mode_override"], &FLAGS];
    let mut cells = 0;
    for target in ["1.19.2", "1.19.4"] {
        for flags in profiles {
            let context = fixture.shared.context(target, flags)?;
            assert_eq!(
                context
                    .features
                    .iter()
                    .filter_map(|(name, enabled)| enabled.then_some(name.as_str()))
                    .collect::<BTreeSet<_>>(),
                flags.iter().copied().collect()
            );
            let packet = flags.contains(&"packet_values");
            let mode = flags.contains(&"tooltip_mode_override");
            for index in 0..FILES.len() {
                let expected = fixture.expected_body(index, target, packet, mode)?;
                assert_eq!(fixture.selected(index, &context)?, expected.is_some());
                if let Some(expected) = expected {
                    assert_eq!(
                        fixture.render(index, &context)?,
                        expected,
                        "{target}, {flags:?}, {}",
                        FILES[index].path
                    );
                }
                cells += 1;
            }
            let utils = fixture.render(UTILS, &context)?;
            assert_eq!(
                utils.contains("import ca.teamdman.sfm.client.tooltip.SFMTooltipModeService;"),
                mode
            );
            assert_eq!(utils.contains("GUI_COMPACT_TOOLTIP_HINT"), mode);
            assert_eq!(utils.contains("isClientAndMoreInfoRequested()"), mode);
            assert!(
                utils.contains("SFMKeyMappings.isKeyDown(SFMKeyMappings.MORE_INFO_TOOLTIP_KEY)")
            );
            if packet {
                let packet_source = fixture.render(PACKET, &context)?;
                assert_eq!(
                    packet_source.contains("SFMItemUtils.isClientAndMoreInfoRequested()"),
                    mode
                );
                assert_eq!(
                    packet_source.contains("SFMItemUtils.isClientAndMoreInfoKeyPressed()"),
                    !mode
                );
                assert!(
                    !packet_source.contains("client.action")
                        && !packet_source.contains("client.screen")
                );
            }
            for unrelated in [
                "client_actions",
                "item_inspection",
                "client_manager_gui",
                "packet_computation",
                "terminal_remote",
            ] {
                assert_ne!(context.features.get(unrelated), Some(&true), "{unrelated}");
            }
        }
    }
    assert_eq!(cells, 32);
    for (target, _) in super::projection_catalog::SUPPORTED_TARGETS {
        if !matches!(target, "1.19.2" | "1.19.4") {
            assert!(
                fixture
                    .shared
                    .context(target, &["tooltip_mode_override"])
                    .is_err()
            );
            assert!(fixture.shared.context(target, &["packet_values"]).is_err());
        }
    }
    Ok(())
}

#[test]
fn five_crlf_only_approvals_reject_other_whitespace_token_bom_or_newline_changes() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut normalized = 0;
    for golden in &RAW {
        let bytes = fixture
            .raw
            .get(golden.blob)
            .ok_or_else(|| eyre::eyre!("missing raw normalization witness"))?;
        let expected = reviewed_bytes(golden, bytes)?;
        if golden.crlf_count > 0 {
            assert_eq!(bytes.len() - expected.len(), golden.crlf_count);
            normalized += 1;
        } else {
            assert_eq!(&expected, bytes);
        }
        let mut mutations = Vec::new();
        mutations.push(bytes[..bytes.len() - 1].to_vec());
        let mut bom = vec![0xef, 0xbb, 0xbf];
        bom.extend(bytes);
        mutations.push(bom);
        let mut lone_cr = bytes.clone();
        lone_cr.push(b'\r');
        mutations.push(lone_cr);
        let source = std::str::from_utf8(bytes)?;
        ensure!(source.contains("public "), "token-mutation anchor changed");
        mutations.push(source.replacen("public ", "private ", 1).into_bytes());
        ensure!(
            source.contains("    "),
            "whitespace-mutation anchor changed"
        );
        mutations.push(source.replacen("    ", "\t", 1).into_bytes());
        for mutation in mutations {
            assert!(
                reviewed_bytes(golden, &mutation).is_err(),
                "unreviewed mutation accepted: {}",
                golden.blob
            );
        }
    }
    assert_eq!(normalized, 5);
    Ok(())
}

#[test]
fn baseline_item_stack_and_tooltip_consumer_apis_remain_target_specific_without_mode() -> Result<()>
{
    let fixture = Fixture::load()?;
    for (target, _) in super::projection_catalog::SUPPORTED_TARGETS {
        let context = fixture.shared.context(target, &[])?;
        if target == "1.21.0" {
            assert_eq!(context.minecraft_version, "1.21");
        }
        let rendered = fixture.render(UTILS, &context)?;
        let expected = fixture
            .expected_body(UTILS, target, false, false)?
            .ok_or_else(|| eyre::eyre!("missing baseline utility"))?;
        assert_eq!(rendered, expected, "{target}");
        assert_eq!(
            rendered.contains("return ItemStack.isSame(a, b);"),
            matches!(target, "1.19.2" | "1.19.4")
        );
        assert_eq!(
            rendered.contains("return ItemStack.isSameItem(a, b);"),
            !matches!(target, "1.19.2" | "1.19.4")
        );
        assert_eq!(
            rendered.contains("return ItemStack.isSameItemSameComponents(a, b);"),
            matches!(target, "1.21.0" | "1.21.1" | "26.1.2")
        );
        assert_eq!(
            rendered.contains("Consumer<Component> tooltipAdder"),
            target == "26.1.2"
        );
        assert_eq!(
            rendered.contains("tooltipAdder.accept("),
            target == "26.1.2"
        );
        assert_eq!(
            rendered.contains("List<Component> lines"),
            target != "26.1.2"
        );
        for optional in [
            "SFMTooltipModeService",
            "GUI_COMPACT_TOOLTIP_HINT",
            "isClientAndMoreInfoRequested",
            "@Deprecated",
        ] {
            assert!(!rendered.contains(optional), "{target}: leaked {optional}");
        }
    }
    Ok(())
}

#[test]
fn packet_explicit_tooltip_state_canonical_nbt_and_mode_supplier_anchors_are_retained() -> Result<()>
{
    let fixture = Fixture::load()?;
    let packet = fixture.render(
        PACKET,
        &fixture.shared.context("1.19.2", &["packet_values"])?,
    )?;
    for anchor in [
        "public static final int MAX_STACK_SIZE = 64;",
        "super(new Item.Properties().stacksTo(MAX_STACK_SIZE));",
        "tag.putInt(CODEC_VERSION_TAG, SFMValueJsonCodec.VERSION);",
        "tag.putString(VALUE_JSON_TAG, encoded);",
        "var tag = stack.getTag();",
        "!SFMValueJsonCodec.isReadableVersion(tag.getInt(CODEC_VERSION_TAG))",
        "catch (IllegalArgumentException invalid)",
        "public static void appendTooltipLines(ItemStack stack, List<Component> lines, boolean expanded)",
        "SFMPacketTooltipFormatter.describe(value, expanded, getNameLength(stack))",
    ] {
        assert!(packet.contains(anchor), "packet anchor changed: {anchor}");
    }
    let get_value = packet
        .split_once("public static Optional<SFMValue> getValue(ItemStack stack)")
        .and_then(|(_, suffix)| suffix.split_once("    @Override").map(|(body, _)| body))
        .ok_or_else(|| eyre::eyre!("getValue method boundaries changed"))?;
    assert!(!get_value.contains("getOrCreateTag"));
    let formatter = fixture.render(
        FORMATTER,
        &fixture.shared.context("1.19.2", &["packet_values"])?,
    )?;
    for anchor in [
        "PACKET_INVALID_DATA.getComponent().withStyle(ChatFormatting.RED)",
        "PACKET_CONTAINS_DATA.getComponent().withStyle(ChatFormatting.GRAY)",
        "SFMItemUtils.getRainbow(Math.max(4, nameLength))",
        "SFMValueJsonCodec.encodePretty(value.orElseThrow())",
        "Component.literal(line).withStyle(ChatFormatting.AQUA)",
        "return List.copyOf(lines);",
    ] {
        assert!(
            formatter.contains(anchor),
            "formatter anchor changed: {anchor}"
        );
    }
    let mode = fixture.render(
        MODE,
        &fixture
            .shared
            .context("1.19.2", &["tooltip_mode_override"])?,
    )?;
    for anchor in [
        "private volatile Mode mode = Mode.AUTO;",
        "this.configuredKeyPressed = Objects.requireNonNull(configuredKeyPressed);",
        "case AUTO -> configuredKeyPressed.getAsBoolean();",
        "case EXPANDED -> true;",
        "case COMPACT -> false;",
        "this.mode = Objects.requireNonNull(mode);",
    ] {
        assert!(mode.contains(anchor), "mode anchor changed: {anchor}");
    }
    assert_eq!(
        mode.matches("configuredKeyPressed.getAsBoolean()").count(),
        1
    );
    Ok(())
}

#[test]
fn common_body_edits_reach_distinct_targets_and_leaves_without_changing_owner_guards() -> Result<()>
{
    let fixture = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(super::core_inputs::CORE_ROOT);
    let anchors = [
        "public class SFMItemUtils {\n",
        "public class PacketItem extends Item {\n",
        "public final class SFMPacketTooltipFormatter {\n",
        "public final class SFMTooltipModeService {\n",
    ];
    let mut edited = Vec::new();
    for (index, golden) in FILES.iter().enumerate() {
        let original = std::str::from_utf8(&fixture.sources[index])?;
        ensure!(
            original.matches(anchors[index]).count() == 1,
            "common edit anchor changed"
        );
        let replacement = format!(
            "{}    // Shared item-tooltip common-edit proof.\n",
            anchors[index]
        );
        let source = original.replacen(anchors[index], &replacement, 1);
        let guards = |text: &str| -> Vec<String> {
            text.lines()
                .filter(|line| line.trim_start().starts_with("{%"))
                .map(str::to_owned)
                .collect()
        };
        assert_eq!(guards(&source), guards(original));
        let path = root.join(golden.path);
        fs::create_dir_all(
            path.parent()
                .ok_or_else(|| eyre::eyre!("missing fixture parent"))?,
        )?;
        fs::write(path, source.as_bytes())?;
        edited.push(source);
    }
    let mut cells = 0;
    for (index, golden) in FILES.iter().enumerate() {
        let targets: &[&str] = if index == UTILS {
            &["1.19.2", "1.21.0", "26.1.2"]
        } else {
            &["1.19.2", "1.19.4"]
        };
        for target in targets {
            let flags: &[&str] = match index {
                UTILS => &[],
                PACKET | FORMATTER => &["packet_values"],
                MODE => &["tooltip_mode_override"],
                _ => eyre::bail!("invalid edit path"),
            };
            let context = fixture.shared.context(target, flags)?;
            let selection = select_core_inputs(&fixture.shared.metadata, &context, &inventory())?;
            let input = selection
                .inputs
                .get(golden.path)
                .ok_or_else(|| eyre::eyre!("edited source omitted"))?;
            ensure!(
                input.input == golden.path && (input.template || golden.path.ends_with(".java")),
                "edited fixture not selected as core"
            );
            let bytes = read_bounded(&root.join(&input.input), MAX_BYTES)?;
            assert_eq!(std::str::from_utf8(&bytes)?, edited[index]);
            let rendered = render_java_source(std::str::from_utf8(&bytes)?, &context)?;
            let original = fixture.render(index, &context)?;
            assert_eq!(
                rendered,
                original.replacen(
                    anchors[index],
                    &format!(
                        "{}    // Shared item-tooltip common-edit proof.\n",
                        anchors[index]
                    ),
                    1
                )
            );
            cells += 1;
        }
        assert_eq!(
            fixture.shared.read_source(golden.path)?,
            fixture.sources[index]
        );
    }
    assert_eq!(cells, 9);
    Ok(())
}

#[test]
fn eighty_offline_frozen_tree_cells_match_exact_blobs_or_absence() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut present = 0;
    let mut absent = 0;
    for (name, commit) in PINNED_CONTEXTS {
        let mut command = frozen_git_command(&fixture.shared.repository);
        command
            .env("GIT_ALLOW_PROTOCOL", "")
            .env("GIT_TERMINAL_PROMPT", "0")
            .args([
                "-c",
                "protocol.allow=never",
                "-c",
                "core.fsmonitor=false",
                "ls-tree",
                "-z",
                commit,
                "--",
            ]);
        for golden in &FILES {
            command.arg(format!("platform/minecraft/{}", golden.path));
        }
        let mut child = command
            .stdout(Stdio::piped())
            .stderr(Stdio::null())
            .spawn()?;
        let mut reader = child
            .stdout
            .take()
            .ok_or_else(|| eyre::eyre!("missing offline membership output"))?;
        let mut bytes = Vec::new();
        let result = reader.by_ref().take(8193).read_to_end(&mut bytes);
        drop(reader);
        if result.is_err() || bytes.len() > 8192 {
            let _ = child.kill();
            let _ = child.wait();
            result?;
            eyre::bail!("offline membership output exceeds bound");
        }
        ensure!(child.wait()?.success(), "offline membership Git failed");
        let mut expected = Vec::new();
        for (index, golden) in FILES.iter().enumerate() {
            if let Some(oid) = witness_oid(index, name)? {
                expected.push((
                    golden.path,
                    format!("100644 blob {oid}\tplatform/minecraft/{}\0", golden.path),
                ));
                present += 1;
            } else {
                absent += 1;
            }
        }
        expected.sort_by_key(|(path, _)| *path);
        let expected = expected
            .into_iter()
            .map(|(_, record)| record)
            .collect::<String>();
        assert_eq!(std::str::from_utf8(&bytes)?, expected, "{name}");
    }
    assert_eq!((present, absent), (26, 54));
    Ok(())
}
