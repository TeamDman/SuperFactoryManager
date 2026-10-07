//! One shared button builder with narrow, version-specific tooltip fragments.
//! Historical blobs are bounded test witnesses, never production inputs.
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

const PATH: &str = "src/main/java/ca/teamdman/sfm/client/screen/widget/SFMButtonBuilder.java";
const LEDGER: &str = "docs/tasks/sfm-core-button-builder-slice.json";
const CORE_DIGEST: &str = "sha256:88ec33c620f2a04b9b025553db35599dfa9548f1ffc9ddf923a7bf795fc1171c";
const CORE_BYTES: usize = 6890;
const PINS: [(&str, &str, usize, usize, &str, usize); 4] = [
    (
        "e864f659c1235bb9c3aa15d69ec08b2992deaf33",
        "sha256:c6a0c80db42a16dc19b423b5484a3a20f3c06f71ea38c0f29cef68e67b7d67c0",
        2910,
        104,
        "sha256:cfdba520af2cef3d2f0e9305768b6d920cc1ca575bc6e5099b4af7b3548ca147",
        2806,
    ),
    (
        "2b2e18b456e87ea6a5c4dcc285c09f7875e5fb3f",
        "sha256:d351245de2e5a9ae3cd4729c61bad665ac9b0f69f54412f7f9889a6cc12298b8",
        2797,
        100,
        "sha256:0382ce230cc1ada554856daaf52a7f8cad18d8426e6b3d4eaa3d48b9ccc3da51",
        2697,
    ),
    (
        "e47ecd06b41ca7c36ac2c3da38684eb4ab8a9261",
        "sha256:f3846593b6d9f00c6fbf8fb2ad05c989cb1efe2772eb965c7c8a22fb82f5f990",
        3183,
        97,
        "sha256:f556e33d7a951d3a84d379e658201fd1bfbabffce044cf3cac91607b1d477a1b",
        3086,
    ),
    (
        "782dea246bf08cad11cd5e2909b83084e5b7be82",
        "sha256:64161abeb1d49144d36fe6183025613f74e02e88585e68d3a007709f55bf2466",
        3688,
        75,
        "sha256:324ece78c0e538c606ceb42fa02ec6376b320f4c87825a75d575b41cc5ef0fd0",
        3613,
    ),
];

#[derive(Facet)]
struct Ledger {
    schema: String,
    path: String,
    authored: Authored,
    membership: String,
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
    changed_members_only_on: Vec<String>,
}
#[derive(Facet)]
struct Normalization {
    policy: String,
    final_lf: String,
    lone_cr: String,
    bom: String,
    #[facet(rename = "tokens_or_whitespace_other_than_CRLF")]
    unchanged_tokens: String,
}
#[derive(Facet)]
struct Variant {
    oid: String,
    bytes: usize,
    sha256: String,
    crlf: usize,
    #[facet(rename = "finalLF")]
    final_lf: bool,
    normalized_bytes: usize,
    normalized_sha256: String,
    contexts: Vec<String>,
}

struct Fixture {
    shared: CoreTestFixture,
    source: String,
    raw: BTreeMap<String, Vec<u8>>,
    normalized: BTreeMap<String, String>,
}
impl Fixture {
    fn load() -> Result<Self> {
        let shared = CoreTestFixture::load()?;
        let source_bytes = shared.read_source(PATH)?;
        ensure!(
            source_bytes.len() == CORE_BYTES && sha256(&source_bytes) == CORE_DIGEST,
            "review authored button template changes explicitly"
        );
        let source = String::from_utf8(source_bytes)?;
        let ledger: Ledger = facet_json::from_str(std::str::from_utf8(&read_bounded(
            &shared.repository.join(LEDGER),
            16 * 1024,
        )?)?)?;
        ensure!(
            ledger.schema == "sfm:core-button-builder-slice@1"
                && ledger.path == PATH
                && ledger.membership == "shared_all_ten_targets_all_feature_masks",
            "button cohort identity changed"
        );
        ensure!(
            ledger.authored.sha256 == CORE_DIGEST
                && ledger.authored.bytes == CORE_BYTES
                && ledger.authored.common_lines == 86
                && ledger.authored.conditional_fragments == 8,
            "button authored witness changed"
        );
        ensure!(
            ledger.normalization.policy
                == "only_replace_CRLF_with_LF_for_four_explicit_raw_witnesses"
                && ledger.normalization.final_lf == "preserved_no_append"
                && ledger.normalization.lone_cr == "forbidden"
                && ledger.normalization.bom == "forbidden"
                && ledger.normalization.unchanged_tokens == "unchanged",
            "normalization approval widened"
        );
        let owner = shared
            .features
            .0
            .get("canvas_text_editor")
            .ok_or_else(|| eyre::eyre!("missing canvas owner"))?;
        ensure!(
            ledger.owner.id == "canvas_text_editor"
                && ledger.owner.supported_targets == owner.supported_targets
                && ledger.owner.requires == owner.requires
                && ledger.owner.changed_members_only_on == ["1.19.2", "1.19.4"],
            "canvas contract changed"
        );
        let mut recorded = BTreeMap::new();
        let mut seen = BTreeSet::new();
        ensure!(
            ledger.variants.len() == PINS.len(),
            "button raw witness scope changed"
        );
        for v in ledger.variants {
            let pin = PINS
                .iter()
                .find(|p| p.0 == v.oid)
                .ok_or_else(|| eyre::eyre!("unreviewed raw witness"))?;
            ensure!(
                seen.insert(v.oid.clone())
                    && (v.bytes, v.crlf, v.normalized_bytes) == (pin.2, pin.3, pin.5)
                    && v.sha256 == pin.1
                    && v.normalized_sha256 == pin.4
                    && v.final_lf,
                "button raw evidence changed"
            );
            for context in v.contexts {
                ensure!(
                    recorded.insert(context, v.oid.clone()).is_none(),
                    "duplicate button context"
                );
            }
        }
        let expected = SUPPORTED_TARGETS
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
            recorded == expected,
            "twenty historical button assignments changed"
        );
        let raw = read_git_blobs(
            &shared.repository,
            &PINS.iter().map(|p| p.0.to_owned()).collect(),
        )?;
        let normalized = PINS
            .iter()
            .map(|p| Ok((p.0.to_owned(), normalize_raw(p.0, &raw[p.0])?)))
            .collect::<Result<BTreeMap<_, _>>>()?;
        Ok(Self {
            shared,
            source,
            raw,
            normalized,
        })
    }
    fn render(&self, target: &str, canvas: bool) -> Result<String> {
        let flags = if canvas {
            &["canvas_text_editor"][..]
        } else {
            &[][..]
        };
        let context = self.shared.context(target, flags)?;
        let selected = select_core_inputs(
            &self.shared.metadata,
            &context,
            &BTreeSet::from([PATH.to_owned()]),
        )?;
        let input = selected
            .inputs
            .get(PATH)
            .ok_or_else(|| eyre::eyre!("shared button omitted"))?;
        ensure!(
            input.input == PATH
                && (input.template || PATH.ends_with(".java"))
                && !selected.omitted_paths.contains(PATH),
            "button gained alternate source or non-template input"
        );
        render_java_source(&self.source, &context)
    }
}
fn historical_oid(target: &str, dev: bool) -> &'static str {
    match (target, dev) {
        ("1.19.2", false) => PINS[0].0,
        ("1.19.2", true) => PINS[2].0,
        ("1.19.4", true) => PINS[3].0,
        _ => PINS[1].0,
    }
}
fn normalize_raw(oid: &str, bytes: &[u8]) -> Result<String> {
    let pin = PINS
        .iter()
        .find(|p| p.0 == oid)
        .ok_or_else(|| eyre::eyre!("unapproved raw object"))?;
    ensure!(
        bytes.len() == pin.2
            && sha256(bytes) == pin.1
            && bytes.ends_with(b"\n")
            && !bytes.starts_with(&[0xef, 0xbb, 0xbf]),
        "raw bytes changed before normalization"
    );
    let raw = std::str::from_utf8(bytes)?;
    ensure!(
        raw.matches("\r\n").count() == pin.3,
        "raw CRLF evidence changed"
    );
    let normalized = raw.replace("\r\n", "\n");
    ensure!(
        !normalized.contains('\r')
            && normalized.len() == pin.5
            && sha256(normalized.as_bytes()) == pin.4,
        "normalization exceeded explicit CRLF approval"
    );
    Ok(normalized)
}

#[test]
fn twenty_historical_button_contexts_reconstruct_exact_approved_lf_bodies() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut cells = 0;
    for (target, _) in SUPPORTED_TARGETS {
        for dev in [false, true] {
            let actual = fixture.render(target, dev)?;
            assert_eq!(
                actual,
                fixture.normalized[historical_oid(target, dev)],
                "{target}, dev={dev}"
            );
            cells += 1;
        }
    }
    assert_eq!(cells, 20);
    Ok(())
}
#[test]
fn dynamic_suppliers_do_not_enable_unrelated_ui_and_keep_both_tooltip_apis() -> Result<()> {
    let fixture = Fixture::load()?;
    for (target, _) in SUPPORTED_TARGETS {
        for canvas in [false, true] {
            let context = fixture
                .shared
                .context(target, if canvas { &["canvas_text_editor"] } else { &[] })?;
            let on = context
                .features
                .iter()
                .filter_map(|(f, v)| v.then_some(f.as_str()))
                .collect::<BTreeSet<_>>();
            assert_eq!(
                on,
                if canvas {
                    BTreeSet::from(["canvas_text_editor"])
                } else {
                    BTreeSet::new()
                }
            );
            let actual = fixture.render(target, canvas)?;
            let dynamic = canvas && matches!(target, "1.19.2" | "1.19.4");
            assert_eq!(actual.contains("setTooltipSupplier("), dynamic);
            assert_eq!(actual.contains("Button.OnTooltip"), target == "1.19.2");
            assert_eq!(
                actual.contains("import net.minecraft.client.gui.components.Tooltip;"),
                target != "1.19.2"
            );
            assert_eq!(
                actual.contains("public void renderWidget(PoseStack"),
                dynamic && target == "1.19.4"
            );
            assert_eq!(
                actual.contains("font.split(tooltip.get()"),
                dynamic && target == "1.19.2"
            );
            assert_eq!(
                actual.contains("font.split(tooltip, Math.max"),
                !canvas && target == "1.19.2"
            );
        }
    }
    Ok(())
}
#[test]
fn shared_button_edit_reaches_all_targets_without_changing_template_guards() -> Result<()> {
    let fixture = Fixture::load()?;
    const ANCHOR: &str = "    private int width = 150;\n";
    const EDIT: &str = "    private int width = 155;\n";
    assert_eq!(fixture.source.matches(ANCHOR).count(), 1);
    let edited = fixture.source.replacen(ANCHOR, EDIT, 1);
    let guards = |text: &str| {
        text.lines()
            .filter(|l| l.trim_start().starts_with("{% "))
            .map(str::to_owned)
            .collect::<Vec<_>>()
    };
    assert_eq!(guards(&fixture.source), guards(&edited));
    for (target, _) in SUPPORTED_TARGETS {
        for canvas in [false, true] {
            let context = fixture
                .shared
                .context(target, if canvas { &["canvas_text_editor"] } else { &[] })?;
            assert_eq!(
                render_java_source(&edited, &context)?,
                fixture.render(target, canvas)?.replacen(ANCHOR, EDIT, 1)
            );
        }
    }
    assert_eq!(fixture.shared.read_source(PATH)?, fixture.source.as_bytes());
    Ok(())
}
#[test]
fn approved_raw_normalization_refuses_token_bom_line_ending_or_final_lf_edits() -> Result<()> {
    let fixture = Fixture::load()?;
    for pin in PINS {
        let bytes = &fixture.raw[pin.0];
        assert_eq!(normalize_raw(pin.0, bytes)?, fixture.normalized[pin.0]);
        let mut token = bytes.clone();
        token[0] = b'P';
        assert!(normalize_raw(pin.0, &token).is_err());
        assert!(normalize_raw(pin.0, &bytes[..bytes.len() - 1]).is_err());
        let mut added = bytes.clone();
        added.push(b'\n');
        assert!(normalize_raw(pin.0, &added).is_err());
        let mut bom = vec![0xef, 0xbb, 0xbf];
        bom.extend(bytes);
        assert!(normalize_raw(pin.0, &bom).is_err());
        assert!(normalize_raw(pin.0, fixture.normalized[pin.0].as_bytes()).is_err());
    }
    assert!(normalize_raw("HEAD", b"package example;\n").is_err());
    Ok(())
}
