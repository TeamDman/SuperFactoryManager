//! Test-only exact reconstruction for three bounded authored Java slices.
//!
//! These tests use real core membership and the controlled Liquid renderer.
//! Historical blobs are pinned witnesses only. Passing is not Java compilation,
//! complete-project generation, JAR reproducibility or gameplay acceptance.

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

const TICK: &str =
    "src/main/java/ca/teamdman/sfm/client/screen/tick_graph/TickTimeGraphRenderer.java";
const TANK: &str =
    "src/main/java/ca/teamdman/sfm/common/containermenu/TestBarrelTankContainerMenu.java";
const TRACKER: &str = "src/main/java/ca/teamdman/sfm/common/program/linting/ProblemTracker.java";
const PATHS: [&str; 3] = [TANK, TICK, TRACKER];
const TICK_FIX: &str = "tick_graph_null_samples";
const TANK_FIX: &str = "test_barrel_constructor_delegation";
const CONFIG_FIX: &str = "unloaded_config_defaults";
const TRACKER_OFF: &str = "8242b87ae8812a72ec08cb4746a0e72bf5606e36";
const TRACKER_ON: &str = "3806bae101a313f1233beeabc9e9d5b5fc88ce2d";
const CROSS_LEDGER: &str = "docs/tasks/sfm-core-cross-target-first-slice.json";
const TRACKER_LEDGER: &str = "docs/tasks/sfm-core-problem-tracker-first-slice.json";

#[derive(Clone, Copy)]
struct RawGolden {
    oid: &'static str,
    raw_sha256: &'static str,
    raw_bytes: usize,
    normalized_sha256: Option<&'static str>,
    crlf_pairs: usize,
    final_lf_added: bool,
}

const RAW_GOLDENS: [RawGolden; 9] = [
    RawGolden {
        oid: "2c82f70042e6f661d9a7eacf4d36f7b9fe515f2c",
        raw_sha256: "sha256:2a3304fd8f2bede44e222c3bc5e68e17341f94bd0d11bd909d5dcb9b50330f83",
        raw_bytes: 2398,
        normalized_sha256: None,
        crlf_pairs: 0,
        final_lf_added: false,
    },
    RawGolden {
        oid: "93fbdc3284c89f92f61f2db5898838306b23efbc",
        raw_sha256: "sha256:a2871b15464babb78c8bc50d55f20af968c07df94a8e284ddcbcd90fcc56f66a",
        raw_bytes: 2284,
        normalized_sha256: Some(
            "sha256:644099c0bbf44529b00e60508f7e494804f8cc74278a1642f72d9fa67db5eb6b",
        ),
        crlf_pairs: 0,
        final_lf_added: true,
    },
    RawGolden {
        oid: "342e3e680341466c11b5ab6e68655550a27c79f1",
        raw_sha256: "sha256:5d937119c37b1daef7a430781c25cb6076a956ac88cca7df22814b1f71a6c310",
        raw_bytes: 3684,
        normalized_sha256: None,
        crlf_pairs: 0,
        final_lf_added: false,
    },
    RawGolden {
        oid: "eac5fe2d912961792ef62606d53a9fd1315228fa",
        raw_sha256: "sha256:659a8bbbef4ec973412f653412f74981b27d9566b5625411add0d6848f3d6d3e",
        raw_bytes: 3692,
        normalized_sha256: None,
        crlf_pairs: 0,
        final_lf_added: false,
    },
    RawGolden {
        oid: "26b4006a0b93f75cef7ed411e9a12294027b2b28",
        raw_sha256: "sha256:3887c181479c9a03cc16bb924a98e49e6c394e428fbf901f35700942820ef652",
        raw_bytes: 3747,
        normalized_sha256: None,
        crlf_pairs: 0,
        final_lf_added: false,
    },
    RawGolden {
        oid: "7eead3df43b36fafb9fe8c3f20da92f79fcd134d",
        raw_sha256: "sha256:d72abdf64e9f3904bdd504a2405e346e2dc921bfc83344365bc0088e0795858f",
        raw_bytes: 4102,
        normalized_sha256: None,
        crlf_pairs: 0,
        final_lf_added: false,
    },
    RawGolden {
        oid: "d2c218637a7f68ff0cc4edacfb86b5ed252d6f36",
        raw_sha256: "sha256:00ef54c7bf1b64ab349e568dd2fa559ae9492552572bf6d7a104432cc708ede9",
        raw_bytes: 3965,
        normalized_sha256: None,
        crlf_pairs: 0,
        final_lf_added: false,
    },
    RawGolden {
        oid: "8242b87ae8812a72ec08cb4746a0e72bf5606e36",
        raw_sha256: "sha256:4b04a4453e85c8c99fa9c118430c7ca2fc449d65ffb55dabfa87b2b5f688f5c4",
        raw_bytes: 1350,
        normalized_sha256: Some(
            "sha256:8a660d3424a962945014c0fe963d1d0f71b21e4776708d0f60a75f42ffe69a7a",
        ),
        crlf_pairs: 45,
        final_lf_added: false,
    },
    RawGolden {
        oid: "3806bae101a313f1233beeabc9e9d5b5fc88ce2d",
        raw_sha256: "sha256:0b5590877ab7edfcb68118aec76fda23b90c241414a996937a4a93cf2e401390",
        raw_bytes: 1380,
        normalized_sha256: Some(
            "sha256:b73c5a0351ba2c5f1202dcb64b54962a3e4dbd3510afff18b4163ff98800e838",
        ),
        crlf_pairs: 34,
        final_lf_added: false,
    },
];

// Parse only the witness contract, not the prose/evidence report extensions.
#[derive(Facet)]
struct CrossLedger {
    schema: String,
    context_commits: BTreeMap<String, String>,
    files: BTreeMap<String, CrossFile>,
}

#[derive(Facet)]
struct CrossFile {
    source_sha256: String,
    source_byte_count: usize,
    witnesses: BTreeMap<String, Option<BlobWitness>>,
}

#[derive(Facet)]
struct BlobWitness {
    mode: String,
    blob: String,
}

#[derive(Facet)]
struct TrackerLedger {
    schema: String,
    path: String,
    feature: String,
    supported_targets: Vec<String>,
    witnesses: Vec<TrackerWitness>,
}

#[derive(Facet)]
struct TrackerWitness {
    blob: String,
    raw_sha256: String,
    raw_bytes: usize,
    normalized_sha256: String,
    crlf_count: usize,
    final_lf_added: bool,
}

struct FixFixture {
    core: CoreTestFixture,
    ledger: CrossLedger,
    inventory: BTreeSet<String>,
    raw_blobs: BTreeMap<String, Vec<u8>>,
}

impl FixFixture {
    fn load() -> Result<Self> {
        let core = CoreTestFixture::load()?;
        let ledger_bytes =
            read_bounded(&checked_file(&core.repository, CROSS_LEDGER)?, 1024 * 1024)?;
        let ledger: CrossLedger = facet_json::from_str(std::str::from_utf8(&ledger_bytes)?)?;
        ensure!(
            ledger.schema == "sfm:core-cross-target-first-slice@1",
            "wrong cross-slice witness schema"
        );
        ensure!(
            ledger.context_commits.len() == 20 && ledger.files.len() == 2,
            "cross-slice witness scope changed"
        );
        ensure!(
            ledger.files.contains_key(TANK) && ledger.files.contains_key(TICK),
            "wrong cross-slice source paths"
        );
        for row in ledger.files.values() {
            ensure!(
                row.witnesses.len() == 20,
                "incomplete cross-slice witness membership"
            );
            ensure!(
                row.witnesses.keys().eq(ledger.context_commits.keys()),
                "witness contexts do not match pinned context set"
            );
            ensure!(
                row.witnesses.values().flatten().all(|w| w.mode == "100644"),
                "nonregular witness mode"
            );
        }
        let tracker_bytes = read_bounded(
            &checked_file(&core.repository, TRACKER_LEDGER)?,
            1024 * 1024,
        )?;
        let tracker: TrackerLedger = facet_json::from_str(std::str::from_utf8(&tracker_bytes)?)?;
        ensure!(
            tracker.schema == "sfm:core-problem-tracker-first-slice@1"
                && tracker.path == TRACKER
                && tracker.feature == CONFIG_FIX
                && tracker.supported_targets == ["1.19.2", "1.19.4"]
                && tracker.witnesses.len() == 2,
            "ProblemTracker witness contract changed"
        );
        for row in &tracker.witnesses {
            let golden = golden(&row.blob)?;
            ensure!(
                row.raw_sha256 == golden.raw_sha256
                    && row.raw_bytes == golden.raw_bytes
                    && Some(row.normalized_sha256.as_str()) == golden.normalized_sha256
                    && row.crlf_count == golden.crlf_pairs
                    && row.final_lf_added == golden.final_lf_added,
                "ProblemTracker normalization/hash evidence changed"
            );
        }
        ensure!(
            tracker
                .witnesses
                .iter()
                .map(|w| w.blob.as_str())
                .collect::<BTreeSet<_>>()
                == BTreeSet::from([TRACKER_OFF, TRACKER_ON]),
            "wrong ProblemTracker raw witnesses"
        );
        let ids = RAW_GOLDENS.iter().map(|g| g.oid.to_owned()).collect();
        let raw_blobs = read_git_blobs(&core.repository, &ids)?;
        for row in RAW_GOLDENS {
            let raw = &raw_blobs[row.oid];
            ensure!(
                raw.len() == row.raw_bytes && sha256(raw) == row.raw_sha256,
                "raw witness {} changed",
                row.oid
            );
        }
        let inventory = discover_core_source_files(&core.core)?;
        ensure!(
            PATHS.iter().all(|path| inventory.contains(*path)),
            "authored core slice is missing"
        );
        Ok(Self {
            core,
            ledger,
            inventory,
            raw_blobs,
        })
    }

    fn render_selected(&self, path: &str, context: &ProjectionContext) -> Result<Option<Vec<u8>>> {
        let selected = self
            .core
            .selection_for_assertion(context, &self.inventory)?;
        let Some(input) = selected.inputs.get(path) else {
            ensure!(
                selected.omitted_paths.contains(path),
                "source vanished outside a membership predicate"
            );
            return Ok(None);
        };
        ensure!(
            input.input == path,
            "slice selected an unexpected alternate input"
        );
        // No Java source bytes are read until this target's real selection succeeds.
        let source = self.core.read_source(path)?;
        if let Some(row) = self.ledger.files.get(path) {
            ensure!(
                source.len() == row.source_byte_count && sha256(&source) == row.source_sha256,
                "authored source changed from its reviewed slice ledger"
            );
        }
        Ok(Some(
            render_java_source(std::str::from_utf8(&source)?, context)?.into_bytes(),
        ))
    }

    fn expected(&self, oid: &str) -> Result<Vec<u8>> {
        let evidence = golden(oid)?;
        let raw = &self.raw_blobs[oid];
        let Some(expected_hash) = evidence.normalized_sha256 else {
            return Ok(raw.clone());
        };
        let normalized =
            normalize_reviewed_java(raw, evidence.crlf_pairs, evidence.final_lf_added)?;
        ensure!(
            sha256(&normalized) == expected_hash,
            "reviewed normalized witness hash changed"
        );
        Ok(normalized)
    }

    fn expected_oid<'a>(&'a self, path: &str, context: &str) -> Result<Option<&'a str>> {
        if path == TRACKER {
            return Ok(Some(if matches!(context, "dev/1.19.2" | "dev/1.19.4") {
                TRACKER_ON
            } else {
                TRACKER_OFF
            }));
        }
        Ok(self.ledger.files[path].witnesses[context]
            .as_ref()
            .map(|w| w.blob.as_str()))
    }
}

fn golden(oid: &str) -> Result<&'static RawGolden> {
    RAW_GOLDENS
        .iter()
        .find(|w| w.oid == oid)
        .ok_or_else(|| eyre::eyre!("unreviewed raw witness {oid}"))
}

fn normalize_reviewed_java(
    raw: &[u8],
    expected_crlf: usize,
    final_lf_added: bool,
) -> Result<Vec<u8>> {
    ensure!(
        !raw.starts_with(b"\xef\xbb\xbf"),
        "normalization must not remove a BOM"
    );
    let source = std::str::from_utf8(raw)?;
    ensure!(
        !source.contains("\"\"\""),
        "this normalization review excludes Java text blocks"
    );
    ensure!(
        source.matches("\r\n").count() == expected_crlf,
        "CRLF evidence changed"
    );
    let mut normalized = source.replace("\r\n", "\n");
    ensure!(
        !normalized.contains('\r'),
        "normalization must not rewrite bare carriage returns"
    );
    if final_lf_added {
        ensure!(
            !normalized.ends_with('\n'),
            "reviewed final LF was already present"
        );
        normalized.push('\n');
    } else {
        ensure!(normalized.ends_with('\n'), "unreviewed missing final LF");
    }
    Ok(normalized.into_bytes())
}

fn fixture_features(context: &str) -> &'static [&'static str] {
    match context {
        "dev/1.19.2" | "dev/1.19.4" => &[CONFIG_FIX],
        "dev/26.1.2" => &[TICK_FIX, TANK_FIX],
        _ => &[],
    }
}

#[test]
fn core_fix_slices_reconstruct_all_twenty_off_and_witnessed_on_contexts() -> Result<()> {
    let fixture = FixFixture::load()?;
    let mut present = 0;
    let mut absent = 0;
    for context_name in fixture.ledger.context_commits.keys() {
        let (_, target) = context_name
            .split_once('/')
            .ok_or_else(|| eyre::eyre!("bad witness context"))?;
        let context = fixture
            .core
            .context(target, fixture_features(context_name))?;
        for path in PATHS {
            let actual = fixture.render_selected(path, &context)?;
            if let Some(oid) = fixture.expected_oid(path, context_name)? {
                assert_eq!(
                    actual.as_deref(),
                    Some(fixture.expected(oid)?.as_slice()),
                    "{context_name} / {path}"
                );
                present += 1;
            } else {
                assert!(
                    actual.is_none(),
                    "{context_name} / {path} must be omitted before Java read/render"
                );
                absent += 1;
            }
        }
    }
    assert_eq!((present, absent), (42, 18));
    Ok(())
}

#[test]
fn core_fix_slices_keep_release_output_when_every_flag_is_off() -> Result<()> {
    let fixture = FixFixture::load()?;
    let mut present = 0;
    let mut absent = 0;
    for context_name in fixture.ledger.context_commits.keys() {
        let (_, target) = context_name
            .split_once('/')
            .ok_or_else(|| eyre::eyre!("bad witness context"))?;
        let context = fixture.core.context(target, &[])?;
        let release_context = format!("release/{target}");
        for path in PATHS {
            let actual = fixture.render_selected(path, &context)?;
            if let Some(oid) = fixture.expected_oid(path, &release_context)? {
                assert_eq!(
                    actual.as_deref(),
                    Some(fixture.expected(oid)?.as_slice()),
                    "feature-off {context_name} / {path}"
                );
                present += 1;
            } else {
                assert!(actual.is_none(), "feature-off {context_name} / {path}");
                absent += 1;
            }
        }
    }
    assert_eq!((present, absent), (42, 18));
    Ok(())
}

#[test]
fn both_26_fixes_toggle_independently_and_do_not_change_problem_tracker() -> Result<()> {
    let fixture = FixFixture::load()?;
    for enabled in [
        &[][..],
        &[TICK_FIX][..],
        &[TANK_FIX][..],
        &[TICK_FIX, TANK_FIX][..],
    ] {
        let context = fixture.core.context("26.1.2", enabled)?;
        for (path, feature) in [(TICK, TICK_FIX), (TANK, TANK_FIX)] {
            let witness_context = if enabled.contains(&feature) {
                "dev/26.1.2"
            } else {
                "release/26.1.2"
            };
            let oid = fixture
                .expected_oid(path, witness_context)?
                .expect("26.1.2 source exists");
            assert_eq!(
                fixture.render_selected(path, &context)?,
                Some(fixture.expected(oid)?),
                "{enabled:?} / {path}"
            );
        }
        assert_eq!(
            fixture.render_selected(TRACKER, &context)?,
            Some(fixture.expected(TRACKER_OFF)?)
        );
    }
    Ok(())
}

#[test]
fn unloaded_config_defaults_toggles_only_the_two_supported_targets() -> Result<()> {
    let fixture = FixFixture::load()?;
    for target in ["1.19.2", "1.19.4"] {
        for (enabled, tracker_oid) in [(&[][..], TRACKER_OFF), (&[CONFIG_FIX][..], TRACKER_ON)] {
            let context = fixture.core.context(target, enabled)?;
            assert_eq!(
                fixture.render_selected(TRACKER, &context)?,
                Some(fixture.expected(tracker_oid)?)
            );
            let release = format!("release/{target}");
            let tank_oid = fixture
                .expected_oid(TANK, &release)?
                .expect("tank source exists");
            assert_eq!(
                fixture.render_selected(TANK, &context)?,
                Some(fixture.expected(tank_oid)?)
            );
            assert!(fixture.render_selected(TICK, &context)?.is_none());
        }
    }
    for target in [
        "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1", "26.1.2",
    ] {
        assert!(
            fixture.core.context(target, &[CONFIG_FIX]).is_err(),
            "{target} config fix must fail support validation"
        );
    }
    for target in [
        "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
    ] {
        assert!(fixture.core.context(target, &[TICK_FIX]).is_err());
        assert!(fixture.core.context(target, &[TANK_FIX]).is_err());
    }
    Ok(())
}

#[test]
fn named_key_and_environment_do_not_choose_fix_source_content_or_membership() -> Result<()> {
    let fixture = FixFixture::load()?;
    for (target, enabled) in [
        ("1.19.2", &[CONFIG_FIX][..]),
        ("1.19.4", &[][..]),
        ("26.1.2", &[TICK_FIX, TANK_FIX][..]),
    ] {
        let context = fixture.core.context(target, enabled)?;
        let mut alternate = context.clone();
        alternate.environment = "dev".to_owned();
        alternate.projection_key = "arbitrary/nested/sources/not-a-version".to_owned();
        alternate.preset = alternate.projection_key.clone();
        for path in PATHS {
            assert_eq!(
                fixture.render_selected(path, &context)?,
                fixture.render_selected(path, &alternate)?,
                "{target} / {path}"
            );
        }
    }
    Ok(())
}

#[test]
fn reviewed_normalization_is_not_general_whitespace_cleanup() -> Result<()> {
    assert_eq!(
        normalize_reviewed_java(b"  class A {}  \r\n", 1, false)?,
        b"  class A {}  \n"
    );
    assert_eq!(
        normalize_reviewed_java(b"class A {}", 0, true)?,
        b"class A {}\n"
    );
    assert!(normalize_reviewed_java(b"\xef\xbb\xbfclass A {}\n", 0, false).is_err());
    assert!(normalize_reviewed_java(b"class A {}\r", 0, true).is_err());
    assert!(normalize_reviewed_java(b"class A {}\n", 0, true).is_err());
    assert!(normalize_reviewed_java(b"class A {}\r\n", 0, false).is_err());
    assert!(normalize_reviewed_java(b"String value = \"\"\"x\"\"\";\n", 0, false).is_err());
    Ok(())
}
