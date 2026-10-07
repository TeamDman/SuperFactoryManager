//! Shared confirmation review behavior with exact released/version witnesses.
//! Historical blobs are test-only; this file is unregistered until promotion.
#![cfg(test)]

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

const PATH: &str = "src/main/java/ca/teamdman/sfm/client/screen/SFMConfirmationScreen.java";
const LEDGER: &str = "docs/tasks/sfm-core-confirmation-screen-slice.json";
const DIGEST: &str = "sha256:3fc7b6d5e1ea41f246f4574ef942edfcfdff9480f2a47556fdb1d4b19e09cf6a";
const BYTES: usize = 6157;
const PINS: [(&str, &str, usize, usize, &str, usize); 3] = [
    (
        "d7c0949f05611cb712e21711ddff12b4f6fa3929",
        "sha256:a1176bf6931966e333040eba0fcd1054d03c4785ae73f13feb9f218067aa391f",
        3874,
        35,
        "sha256:8e0f934891696ebbf0ce42b82cde65fe63d405539ecde7231b4d671ca2bf0c37",
        3839,
    ),
    (
        "859c7b6b3c671a81bdd9ba95bfe0f27cb39c4e04",
        "sha256:7689a1d98e2857616f6ad683696e9f610afa27d74906e5ca2b266398d1419890",
        1519,
        49,
        "sha256:6253dbd0de67dacc25fe350a0c49eefb7909606e3785680be00f69af22cd5071",
        1470,
    ),
    (
        "1f78d448e788876536e55f616da28518bc740d74",
        "sha256:d95e19d13c13d89bf2b9c9071802c74df0fc14d96911732d91a8355c3e1ed6e6",
        1657,
        55,
        "sha256:535b3d2516cfaa5cadeb77b97e9ab0b192f2834ac6641691fb1b5451b409412a",
        1602,
    ),
];

#[derive(Facet)]
struct Ledger {
    schema: String,
    path: String,
    membership: String,
    authored: Authored,
    owner: Owner,
    normalization: Normalization,
    variants: Vec<Variant>,
}
#[derive(Facet)]
struct Authored {
    sha256: String,
    bytes: usize,
    common_lines: usize,
    conditional_fragments: usize,
}
#[derive(Facet)]
struct Owner {
    id: String,
    supported_targets: Vec<String>,
    requires: Vec<String>,
}
#[derive(Facet)]
struct Normalization {
    policy: String,
    final_lf: String,
    lone_cr: String,
    bom: String,
    other_changes: bool,
}
#[derive(Facet)]
struct Variant {
    oid: String,
    sha256: String,
    bytes: usize,
    crlf: usize,
    #[facet(rename = "finalLF")]
    final_lf: bool,
    normalized_sha256: String,
    normalized_bytes: usize,
    contexts: Vec<String>,
}
struct Fixture {
    shared: CoreTestFixture,
    source: String,
    raw: BTreeMap<String, Vec<u8>>,
    expected: BTreeMap<String, String>,
}
impl Fixture {
    fn load() -> Result<Self> {
        let shared = CoreTestFixture::load()?;
        let source_bytes = shared.read_source(PATH)?;
        ensure!(
            source_bytes.len() == BYTES && sha256(&source_bytes) == DIGEST,
            "review confirmation core changes explicitly"
        );
        let ledger: Ledger = facet_json::from_str(std::str::from_utf8(&read_bounded(
            &shared.repository.join(LEDGER),
            16 * 1024,
        )?)?)?;
        ensure!(
            ledger.schema == "sfm:core-confirmation-screen-slice@1"
                && ledger.path == PATH
                && ledger.membership == "shared_all_ten_targets_all_feature_masks"
                && ledger.authored.sha256 == DIGEST
                && ledger.authored.bytes == BYTES
                && ledger.authored.common_lines == 40
                && ledger.authored.conditional_fragments == 8,
            "confirmation cohort identity changed"
        );
        let owner = shared
            .features
            .0
            .get("confirmation_review_callbacks")
            .ok_or_else(|| eyre::eyre!("missing review owner"))?;
        ensure!(
            ledger.owner.id == "confirmation_review_callbacks"
                && ledger.owner.supported_targets == ["1.19.2", "1.19.4"]
                && ledger.owner.supported_targets == owner.supported_targets
                && ledger.owner.requires.is_empty()
                && owner.requires.is_empty(),
            "review owner changed"
        );
        ensure!(
            ledger.normalization.policy
                == "only_replace_CRLF_with_LF_for_three_explicit_raw_witnesses"
                && ledger.normalization.final_lf == "preserved_no_append"
                && ledger.normalization.lone_cr == "forbidden"
                && ledger.normalization.bom == "forbidden"
                && !ledger.normalization.other_changes,
            "normalization approval widened"
        );
        ensure!(ledger.variants.len() == 3, "raw scope changed");
        let mut assignments = BTreeMap::new();
        let mut seen = BTreeSet::new();
        for row in ledger.variants {
            let pin = PINS
                .iter()
                .find(|p| p.0 == row.oid)
                .ok_or_else(|| eyre::eyre!("unreviewed raw witness"))?;
            ensure!(
                seen.insert(row.oid.clone())
                    && row.sha256 == pin.1
                    && (row.bytes, row.crlf, row.normalized_bytes) == (pin.2, pin.3, pin.5)
                    && row.normalized_sha256 == pin.4
                    && row.final_lf,
                "raw witness changed"
            );
            for context in row.contexts {
                ensure!(
                    assignments.insert(context, row.oid.clone()).is_none(),
                    "duplicate context"
                );
            }
        }
        let expected_assignments = SUPPORTED_TARGETS
            .into_iter()
            .flat_map(|(target, _)| {
                [false, true].map(move |dev| {
                    (
                        format!("{}/{target}", if dev { "dev" } else { "release" }),
                        historical_oid(target, dev).to_owned(),
                    )
                })
            })
            .collect::<BTreeMap<_, _>>();
        ensure!(
            assignments == expected_assignments,
            "twenty context identities changed"
        );
        let raw = read_git_blobs(
            &shared.repository,
            &PINS.iter().map(|p| p.0.to_owned()).collect(),
        )?;
        let expected = PINS
            .iter()
            .map(|p| Ok((p.0.to_owned(), normalize_raw(p.0, &raw[p.0])?)))
            .collect::<Result<BTreeMap<_, _>>>()?;
        Ok(Self {
            shared,
            source: String::from_utf8(source_bytes)?,
            raw,
            expected,
        })
    }
    fn render(&self, target: &str, enabled: bool) -> Result<String> {
        let flags: &[&str] = if enabled {
            &["confirmation_review_callbacks"]
        } else {
            &[]
        };
        let context = self.shared.context(target, flags)?;
        let selection = select_core_inputs(
            &self.shared.metadata,
            &context,
            &BTreeSet::from([PATH.to_owned()]),
        )?;
        let input = selection
            .inputs
            .get(PATH)
            .ok_or_else(|| eyre::eyre!("shared confirmation omitted"))?;
        ensure!(
            input.input == PATH
                && (input.template || PATH.ends_with(".java"))
                && !selection.omitted_paths.contains(PATH),
            "alternate confirmation input"
        );
        render_java_source(&self.source, &context)
    }
}
fn historical_oid(target: &str, dev: bool) -> &'static str {
    if target == "26.1.2" {
        PINS[2].0
    } else if dev && matches!(target, "1.19.2" | "1.19.4") {
        PINS[0].0
    } else {
        PINS[1].0
    }
}
fn normalize_raw(oid: &str, bytes: &[u8]) -> Result<String> {
    let pin = PINS
        .iter()
        .find(|p| p.0 == oid)
        .ok_or_else(|| eyre::eyre!("unapproved normalization"))?;
    ensure!(
        bytes.len() == pin.2
            && sha256(bytes) == pin.1
            && bytes.ends_with(b"\n")
            && !bytes.starts_with(&[0xef, 0xbb, 0xbf])
            && bytes.windows(2).filter(|p| *p == b"\r\n").count() == pin.3,
        "raw identity mismatch"
    );
    for (i, b) in bytes.iter().enumerate() {
        ensure!(
            *b != b'\r' || bytes.get(i + 1) == Some(&b'\n'),
            "lone CR rejected"
        );
    }
    let result = std::str::from_utf8(bytes)?.replace("\r\n", "\n");
    ensure!(
        result.len() == pin.5 && sha256(result.as_bytes()) == pin.4,
        "normalized identity mismatch"
    );
    Ok(result)
}
#[test]
fn twenty_historical_confirmation_bodies_match_real_core() -> Result<()> {
    let f = Fixture::load()?;
    for (target, _) in SUPPORTED_TARGETS {
        for dev in [false, true] {
            let enabled = dev && matches!(target, "1.19.2" | "1.19.4");
            assert_eq!(
                f.render(target, enabled)?,
                f.expected[historical_oid(target, dev)],
                "{target}/{dev}"
            );
        }
    }
    Ok(())
}
#[test]
fn review_owner_is_independent_and_legacy_delay_seams_remain_exact() -> Result<()> {
    let f = Fixture::load()?;
    for (target, _) in SUPPORTED_TARGETS {
        let off = f.render(target, false)?;
        assert_eq!(off, f.expected[historical_oid(target, false)]);
        assert!(!off.contains("AtomicBoolean") && !off.contains("Runnable cancelled"));
        assert_eq!(off.contains("setDelay(this.delay);"), target == "26.1.2");
        if matches!(target, "1.19.2" | "1.19.4") {
            let on = f.render(target, true)?;
            assert_eq!(on, f.expected[PINS[0].0]);
            for anchor in [
                "compareAndSet(false, true)",
                "choice.accept(false)",
                "setDelay(remainingDelay)",
                "setInitialFocus(children().get(children().size() - 1))",
                "cancel.active = true",
                "if (remainingDelay > 0) remainingDelay--;",
            ] {
                assert!(on.contains(anchor), "{anchor}");
            }
            let c = f
                .shared
                .context(target, &["confirmation_review_callbacks"])?;
            assert_eq!(
                c.features
                    .iter()
                    .filter_map(|(k, v)| v.then_some(k.as_str()))
                    .collect::<BTreeSet<_>>(),
                BTreeSet::from(["confirmation_review_callbacks"])
            );
        } else {
            assert!(
                f.shared
                    .context(target, &["confirmation_review_callbacks"])
                    .is_err()
            );
        }
    }
    Ok(())
}
#[test]
fn common_confirmation_edit_reaches_all_twenty_cells_without_changing_guards() -> Result<()> {
    let f = Fixture::load()?;
    let anchor = "public class SFMConfirmationScreen extends ConfirmScreen {\n";
    ensure!(
        f.source.matches(anchor).count() == 1,
        "common anchor changed"
    );
    let replacement = format!("{anchor}    // Shared confirmation edit proof.\n");
    let edited = f.source.replacen(anchor, &replacement, 1);
    for (target, _) in SUPPORTED_TARGETS {
        for dev in [false, true] {
            let enabled = dev && matches!(target, "1.19.2" | "1.19.4");
            let c = f.shared.context(
                target,
                if enabled {
                    &["confirmation_review_callbacks"]
                } else {
                    &[]
                },
            )?;
            assert_eq!(
                render_java_source(&edited, &c)?,
                f.render(target, enabled)?.replacen(anchor, &replacement, 1)
            );
        }
    }
    assert_eq!(f.shared.read_source(PATH)?, f.source.as_bytes());
    Ok(())
}
#[test]
fn explicit_crlf_approvals_reject_token_bom_and_final_lf_mutation() -> Result<()> {
    let f = Fixture::load()?;
    for pin in PINS {
        let bytes = &f.raw[pin.0];
        assert_eq!(normalize_raw(pin.0, bytes)?, f.expected[pin.0]);
        assert!(normalize_raw(pin.0, &bytes[..bytes.len() - 1]).is_err());
        let mut bom = vec![0xef, 0xbb, 0xbf];
        bom.extend(bytes);
        assert!(normalize_raw(pin.0, &bom).is_err());
        let source = std::str::from_utf8(bytes)?;
        ensure!(source.contains("public class"), "mutation anchor changed");
        assert!(
            normalize_raw(
                pin.0,
                source
                    .replacen("public class", "private class", 1)
                    .as_bytes()
            )
            .is_err()
        );
    }
    Ok(())
}
