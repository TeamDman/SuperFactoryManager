//! Promoted foundation evidence, not a production historical-source resolver.
//!
//! The staged ledger remains historical authoring evidence. Tests read the real
//! promoted core bodies and require actual registered sparse membership; no
//! staging fallback or synthesized production rule is used.
//! Passing proves raw source/membership boundaries, not whole-mod compilation.

#![cfg(test)]

use super::candidate_lock::checked_file;
use super::core_inputs::select_core_inputs;
use super::core_slice_test_support::CoreTestFixture;
use super::core_slice_test_support::read_bounded;
use super::core_slice_test_support::read_git_blobs;
use super::projection_catalog::SUPPORTED_TARGETS;
use super::provenance::sha256;
use super::release_baseline::frozen_git_command;
use super::render_java_source;
use eyre::Result;
use eyre::WrapErr;
use eyre::ensure;
use facet::Facet;
use std::collections::BTreeMap;
use std::collections::BTreeSet;

const LEDGER: &str = "docs/tasks/sfm-core-value-foundation-slice.json";
const STAGE: &str = "platform/cli/sfm-propagate-changes/target/core-value-template-stage-v1";
const VALUE_PREFIX: &str = "src/main/java/ca/teamdman/sfm/common/value/";
const FEATURE: &str = "packet_values";
const MAX_FILE_BYTES: u64 = 1024 * 1024;

const PINNED_CONTEXTS: [(&str, &str); 20] = [
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
];

struct RawGolden {
    basename: &'static str,
    oid: &'static str,
    sha256: &'static str,
    bytes: usize,
}

const RAW_GOLDENS: [RawGolden; 5] = [
    RawGolden {
        basename: "SFMValue.java",
        oid: "8f9c4c208e13a070e51ae620c95b99c10eaf11b3",
        sha256: "sha256:7d76b274268d5bb037423524e388245469d95db6bb8d0cbd7cc0f6e0e0196d84",
        bytes: 3029,
    },
    RawGolden {
        basename: "SFMValueJsonCodec.java",
        oid: "3a607dc625af680e17cc68f1c0a442e2ff9ca3a3",
        sha256: "sha256:63f64c11c641c6b61ab91013cd7934f9eb74a4249b7871a438d653f5710d78b4",
        bytes: 10352,
    },
    RawGolden {
        basename: "SFMValueMatch.java",
        oid: "3ffca5cf522ff1e3bc9fff511c69a9b8c65d7273",
        sha256: "sha256:0a61bdef5955fc2921f7424a4cf298713f667b064f05b96f4a113470a2cdeadd",
        bytes: 403,
    },
    RawGolden {
        basename: "SFMValueSchema.java",
        oid: "5a9b96c3e0df56a1b3a2941cd01656ad058e6565",
        sha256: "sha256:d094a301cf0639a6ff2bbe27ca4e136b074e221fa44f4ebde3f9c0ad00c0e0a8",
        bytes: 12879,
    },
    RawGolden {
        basename: "SFMValuePattern.java",
        oid: "7535ee25a93ab211ee27f47290c8573934f233ba",
        sha256: "sha256:c33198398e277e51958816acc9fa3d04012fc8786e81802237d55d5a03f28c57",
        bytes: 3066,
    },
];

#[derive(Facet)]
struct ValueLedger {
    schema: String,
    status: String,
    context_commits: BTreeMap<String, String>,
    files: BTreeMap<String, ValueFile>,
}

#[derive(Facet)]
struct ValueFile {
    original_path: String,
    staged_path: String,
    feature_owner: String,
    supported_targets: Vec<String>,
    required_features: Vec<String>,
    feature_prerequisites: Vec<String>,
    source_blob: String,
    source_sha256: String,
    source_bytes: usize,
    line_endings: String,
    final_newline: String,
    current_canonical_sha256: String,
    witnesses: BTreeMap<String, Option<String>>,
}

struct ValueFixture {
    core: CoreTestFixture,
    source: BTreeMap<String, Vec<u8>>,
}

impl ValueFixture {
    fn load() -> Result<Self> {
        let core = CoreTestFixture::load()?;
        let ledger: ValueLedger = facet_json::from_str(std::str::from_utf8(&read_bounded(
            &checked_file(&core.repository, LEDGER)?,
            MAX_FILE_BYTES,
        )?)?)?;
        ensure!(
            ledger.schema == "sfm:core-value-foundation-slice@1"
                && ledger.status
                    == "exact_source_bodies_staged_pending_root_promotion_and_sparse_membership_registration",
            "wrong staged value foundation ledger"
        );
        let pinned = PINNED_CONTEXTS
            .into_iter()
            .map(|(name, commit)| (name.to_owned(), commit.to_owned()))
            .collect::<BTreeMap<_, _>>();
        ensure!(
            ledger.context_commits == pinned,
            "value source commits changed"
        );
        ensure!(
            ledger.files.len() == RAW_GOLDENS.len(),
            "value slice scope changed"
        );
        let oids = RAW_GOLDENS.iter().map(|g| g.oid.to_owned()).collect();
        let raw = read_git_blobs(&core.repository, &oids)?;
        let mut source = BTreeMap::new();
        for golden in &RAW_GOLDENS {
            let path = format!("{VALUE_PREFIX}{}", golden.basename);
            let evidence = ledger
                .files
                .get(&path)
                .ok_or_else(|| eyre::eyre!("value evidence missing for {path}"))?;
            ensure!(
                evidence.original_path == format!("platform/minecraft/{path}")
                    && evidence.staged_path == format!("{STAGE}/{}", golden.basename)
                    && evidence.feature_owner == FEATURE
                    && evidence.supported_targets == ["1.19.2", "1.19.4"]
                    && evidence.required_features == [FEATURE]
                    && evidence.feature_prerequisites.is_empty()
                    && evidence.source_blob == golden.oid
                    && evidence.source_sha256 == golden.sha256
                    && evidence.current_canonical_sha256 == golden.sha256
                    && evidence.source_bytes == golden.bytes
                    && evidence.line_endings == "lf"
                    && evidence.final_newline == "exactly_one_lf",
                "value ownership or raw-byte evidence changed for {path}"
            );
            let expected_witnesses = PINNED_CONTEXTS
                .into_iter()
                .map(|(name, _)| {
                    let oid =
                        matches!(name, "dev/1.19.2" | "dev/1.19.4").then(|| golden.oid.to_owned());
                    (name.to_owned(), oid)
                })
                .collect::<BTreeMap<_, _>>();
            ensure!(
                evidence.witnesses == expected_witnesses,
                "value membership changed"
            );
            let staged = core.read_source(&path)?;
            ensure!(
                staged == raw[golden.oid]
                    && staged.len() == golden.bytes
                    && sha256(&staged) == golden.sha256
                    && !staged.contains(&b'\r')
                    && staged.ends_with(b"\n")
                    && !staged.ends_with(b"\n\n"),
                "promoted value body differs from its raw golden"
            );
            let text = std::str::from_utf8(&staged)?;
            ensure!(
                !text.contains("{%") && !text.contains("{{"),
                "unexpected value directives"
            );
            source.insert(path, staged);
        }
        let fixture = Self { core, source };
        fixture.validate_metadata()?;
        Ok(fixture)
    }

    fn validate_metadata(&self) -> Result<()> {
        for path in self.source.keys() {
            let rules = self
                .core
                .metadata
                .source_rules
                .get(path)
                .ok_or_else(|| eyre::eyre!("promoted value input lacks a registered rule"))?;
            ensure!(
                rules.len() == 1
                    && rules[0].input == *path
                    && rules[0].template
                    && rules[0].when.targets == ["1.19.2", "1.19.4"]
                    && rules[0].when.all_features == [FEATURE]
                    && rules[0].when.any_features.is_empty()
                    && rules[0].when.none_features.is_empty(),
                "promoted value input differs from its reviewed sparse membership"
            );
        }
        Ok(())
    }
}

#[test]
fn value_foundation_witnesses_cover_all_twenty_exact_tree_membership_cells() -> Result<()> {
    let fixture = ValueFixture::load()?;
    let paths = fixture
        .source
        .keys()
        .map(|path| format!("platform/minecraft/{path}"))
        .collect::<Vec<_>>();
    let mut cells = 0;
    for (name, commit) in PINNED_CONTEXTS {
        let output = frozen_git_command(&fixture.core.repository)
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
            .output()
            .wrap_err("cannot read fixed offline value witness tree")?;
        ensure!(
            output.status.success(),
            "offline value witness tree query failed"
        );
        let text = std::str::from_utf8(&output.stdout)?;
        let mut actual = BTreeMap::new();
        for line in text.lines() {
            let (header, path) = line
                .split_once('\t')
                .ok_or_else(|| eyre::eyre!("malformed value witness tree row"))?;
            let fields = header.split_whitespace().collect::<Vec<_>>();
            ensure!(
                fields.len() == 3
                    && fields[0] == "100644"
                    && fields[1] == "blob"
                    && paths.iter().any(|expected| expected == path)
                    && actual
                        .insert(path.to_owned(), fields[2].to_owned())
                        .is_none(),
                "unexpected value witness tree member"
            );
        }
        let expected = if matches!(name, "dev/1.19.2" | "dev/1.19.4") {
            RAW_GOLDENS
                .iter()
                .map(|golden| {
                    (
                        format!("platform/minecraft/{VALUE_PREFIX}{}", golden.basename),
                        golden.oid.to_owned(),
                    )
                })
                .collect()
        } else {
            BTreeMap::new()
        };
        assert_eq!(
            actual, expected,
            "wrong five-class witness membership for {name}"
        );
        cells += RAW_GOLDENS.len();
    }
    assert_eq!(cells, 100);
    Ok(())
}

#[test]
fn value_foundation_registered_selector_omits_all_off_and_keeps_packet_values_only() -> Result<()> {
    let fixture = ValueFixture::load()?;
    let metadata = &fixture.core.metadata;
    let inventory = fixture.source.keys().cloned().collect::<BTreeSet<_>>();
    let mut off_cells = 0;
    for (name, _) in PINNED_CONTEXTS {
        let (environment, target) = name.split_once('/').expect("fixed context name");
        let mut context = fixture.core.context(target, &[])?;
        context.environment = environment.to_owned();
        let selected = select_core_inputs(metadata, &context, &inventory)?;
        for path in fixture.source.keys() {
            assert!(
                !selected.inputs.contains_key(path),
                "disabled value input leaked"
            );
            assert!(selected.omitted_paths.contains(path));
            off_cells += 1;
        }
    }
    assert_eq!(off_cells, 100);
    let mut on_cells = 0;
    for target in ["1.19.2", "1.19.4"] {
        let context = fixture.core.context(target, &[FEATURE])?;
        let selected = select_core_inputs(metadata, &context, &inventory)?;
        for (path, raw) in &fixture.source {
            let input = &selected.inputs[path];
            assert_eq!(&input.input, path);
            assert!(!selected.omitted_paths.contains(path));
            assert_eq!(
                render_java_source(std::str::from_utf8(raw)?, &context)?.as_bytes(),
                raw,
                "value render changed raw body on {target}"
            );
            on_cells += 1;
        }
    }
    assert_eq!(on_cells, 10);
    Ok(())
}

#[test]
fn value_foundation_refuses_unsupported_targets_and_missing_membership_flag() -> Result<()> {
    let fixture = ValueFixture::load()?;
    let metadata = &fixture.core.metadata;
    let inventory = fixture.source.keys().cloned().collect::<BTreeSet<_>>();
    let mut refused = 0;
    for (target, _) in SUPPORTED_TARGETS {
        if !matches!(target, "1.19.2" | "1.19.4") {
            assert!(fixture.core.context(target, &[FEATURE]).is_err());
            refused += 1;
        }
    }
    assert_eq!(refused, 8);
    let mut context = fixture.core.context("1.19.2", &[])?;
    assert_eq!(context.features.remove(FEATURE), Some(false));
    assert!(select_core_inputs(metadata, &context, &inventory).is_err());
    Ok(())
}

#[test]
fn value_foundation_has_only_jdk_and_gson_imports_and_preserves_codec_contract() -> Result<()> {
    let fixture = ValueFixture::load()?;
    for raw in fixture.source.values() {
        let text = std::str::from_utf8(raw)?;
        for line in text.lines() {
            if let Some(import) = line.strip_prefix("import ") {
                assert!(
                    import.starts_with("java.")
                        || matches!(
                            import,
                            "com.google.gson.stream.JsonReader;"
                                | "com.google.gson.stream.JsonToken;"
                                | "com.google.gson.stream.JsonWriter;"
                        ),
                    "foundation imported a client/MC runtime: {import}"
                );
            }
        }
    }
    let codec =
        std::str::from_utf8(&fixture.source[&format!("{VALUE_PREFIX}SFMValueJsonCodec.java")])?;
    assert!(codec.contains("public static final int VERSION = 2;"));
    assert!(codec.contains("public static final int OLDEST_READABLE_VERSION = 1;"));
    assert!(codec.contains("MAX_ENCODED_UTF8_BYTES = 3_072"));
    assert!(codec.contains("Duplicate packet value field: "));
    let value = std::str::from_utf8(&fixture.source[&format!("{VALUE_PREFIX}SFMValue.java")])?;
    assert!(value.contains("if (!Double.isFinite(value))"));
    let schema =
        std::str::from_utf8(&fixture.source[&format!("{VALUE_PREFIX}SFMValueSchema.java")])?;
    assert!(schema.contains("MAX_ACTION_ENCODED_UTF8_BYTES = 16 * 1024"));
    Ok(())
}
