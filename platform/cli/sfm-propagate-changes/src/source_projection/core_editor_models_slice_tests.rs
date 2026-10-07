//! Frozen source-only editor document and independent clipping helper goldens.
//!
//! Historic Git objects are bounded offline witnesses, never production input
//! routes. These tests require the eight authored inputs to be promoted first.
//! They prove actual core selection and rendering, not Java compilation or UI
//! behavior. Deliberate later common edits require reviewed golden updates.

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

const LEDGER: &str = "docs/tasks/sfm-core-editor-models-slice.json";
const LIMIT: u64 = 128 * 1024;
const FLAGS: [&str; 8] = [
    "editor_documents",
    "editor_async_save",
    "item_inspection_language",
    "editor_v1_panel_clipping",
    "workspace_panels",
    "canvas_text_editor",
    "command_palette",
    "client_overlay_scenes",
];
const SCISSOR_OWNERS: [&str; 5] = [
    "editor_v1_panel_clipping",
    "workspace_panels",
    "canvas_text_editor",
    "command_palette",
    "client_overlay_scenes",
];
const PATHS: [&str; 8] = [
    "src/main/java/ca/teamdman/sfm/client/screen/SFMScissorStack.java",
    "src/main/java/ca/teamdman/sfm/client/text_editor/SFMTextDocumentPosition.java",
    "src/main/java/ca/teamdman/sfm/client/text_editor/SFMTextDocumentRange.java",
    "src/main/java/ca/teamdman/sfm/client/text_editor/SFMTextDocumentLanguage.java",
    "src/main/java/ca/teamdman/sfm/client/text_editor/SFMTextDocumentSourceRootIdentity.java",
    "src/main/java/ca/teamdman/sfm/client/text_editor/SFMTextDocumentSnapshot.java",
    "src/main/java/ca/teamdman/sfm/client/text_editor/SFMTextDocumentSaveResult.java",
    "src/main/java/ca/teamdman/sfm/client/text_editor/SFMTextDocumentSaveSession.java",
];
const TARGETS: [&str; 10] = [
    "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
    "26.1.2",
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
const STAGES: [(&str, u64); 8] = [
    (
        "sha256:0979548abfd14497fb395d678445f52f4435beb0c85610465597b0e64f90a671",
        5073,
    ),
    (
        "sha256:db5d770e1a9448aec99cd282d71f3ca32b7164277ba26c0fb530467a3f3c46e2",
        398,
    ),
    (
        "sha256:2615a4f052f419d4c0088bbad0739285d5ca14f0a72861f8408ab55261ee5ed0",
        3661,
    ),
    (
        "sha256:57c4739b84b41d2becfcd117e38023115a440b7738ba9dcdd3e7b1f052c5ce0c",
        4924,
    ),
    (
        "sha256:321c62334856ad744fc6db0599ee87cd7425edd8ac0307aba31806a0253deb6f",
        2003,
    ),
    (
        "sha256:05e4ee70d5613c3b603c08bfe564d370d90e1a4d00474610cb316740538dda9f",
        19425,
    ),
    (
        "sha256:9d697affdc026ac4ffb0f04b54742da72b2291cda82991f6f8fa862228c58a60",
        1120,
    ),
    (
        "sha256:e84efe0b12acd4b1d67869abfdc03171bfaab70689701b98148c61e19567de8e",
        3150,
    ),
];
const DEV_BLOBS: [[&str; 8]; 2] = [
    [
        "afeaed0b96a40c14d94e7e18707aa5f0a0c610bd",
        "19b5734f2c16400735edf4fc3c339a71d226b502",
        "6e30d45ba66e1b95cdadc20a4c1b384fcc168ac1",
        "ae38b8f27a45876d4afd5ea72588f053a32dba38",
        "c1e3d26428ee53816471455c591cffac66ec2075",
        "686ef829938e3502c64a0e99895b8a09fc4d9dbe",
        "0d99424f5281436dfef673c387cefb520b56dad3",
        "c3c188fef948ec8f8ee629fd775ca4e2897d06a0",
    ],
    [
        "b8282150325c8df444b180b40f0a7b344f34e50e",
        "19b5734f2c16400735edf4fc3c339a71d226b502",
        "6e30d45ba66e1b95cdadc20a4c1b384fcc168ac1",
        "ccfae0ea13b7abfd7c681baecb14c2864187d7e2",
        "c1e3d26428ee53816471455c591cffac66ec2075",
        "686ef829938e3502c64a0e99895b8a09fc4d9dbe",
        "0d99424f5281436dfef673c387cefb520b56dad3",
        "c3c188fef948ec8f8ee629fd775ca4e2897d06a0",
    ],
];
const RAW_FACTS: [(&str, &str, u64); 10] = [
    (
        "0d99424f5281436dfef673c387cefb520b56dad3",
        "sha256:9d697affdc026ac4ffb0f04b54742da72b2291cda82991f6f8fa862228c58a60",
        1120,
    ),
    (
        "19b5734f2c16400735edf4fc3c339a71d226b502",
        "sha256:db5d770e1a9448aec99cd282d71f3ca32b7164277ba26c0fb530467a3f3c46e2",
        398,
    ),
    (
        "686ef829938e3502c64a0e99895b8a09fc4d9dbe",
        "sha256:05e4ee70d5613c3b603c08bfe564d370d90e1a4d00474610cb316740538dda9f",
        19425,
    ),
    (
        "6e30d45ba66e1b95cdadc20a4c1b384fcc168ac1",
        "sha256:2615a4f052f419d4c0088bbad0739285d5ca14f0a72861f8408ab55261ee5ed0",
        3661,
    ),
    (
        "ae38b8f27a45876d4afd5ea72588f053a32dba38",
        "sha256:e8596b35586f03b1f9fb7373fcc56816462c42805519e3609cbc2b07a4db9d94",
        4869,
    ),
    (
        "afeaed0b96a40c14d94e7e18707aa5f0a0c610bd",
        "sha256:e600d7e4074594c4cfc168d7996655629e7762ad0b7e620214765879e60885c0",
        4740,
    ),
    (
        "b8282150325c8df444b180b40f0a7b344f34e50e",
        "sha256:ad529217798a94bc097e5648a2f1d279222dd6b33f8c99abab4a626f2860a037",
        4760,
    ),
    (
        "c1e3d26428ee53816471455c591cffac66ec2075",
        "sha256:321c62334856ad744fc6db0599ee87cd7425edd8ac0307aba31806a0253deb6f",
        2003,
    ),
    (
        "c3c188fef948ec8f8ee629fd775ca4e2897d06a0",
        "sha256:e84efe0b12acd4b1d67869abfdc03171bfaab70689701b98148c61e19567de8e",
        3150,
    ),
    (
        "ccfae0ea13b7abfd7c681baecb14c2864187d7e2",
        "sha256:94cd8c70719e4506afb6d5317157006b6203f1d9b51a066fc17f30134ab64e43",
        4624,
    ),
];

#[derive(Facet)]
struct Ledger {
    schema: String,
    context_commits: BTreeMap<String, String>,
    normalization: Normalization,
    owners: Vec<Owner>,
    consumer_owner_contracts: Vec<Owner>,
    scissor_consumer_union: ScissorUnion,
    files: Vec<AuthoredFile>,
    raw_witnesses: Vec<RawFact>,
}
#[derive(Facet)]
struct Normalization {
    policy: String,
    approved_changes: Vec<String>,
    raw_byte_exact: bool,
}
#[derive(Facet)]
struct Owner {
    name: String,
    support: Vec<String>,
    requires: Vec<String>,
}
#[derive(Facet)]
struct ScissorUnion {
    support: Vec<String>,
    any_features: Vec<String>,
    requires_documents: bool,
}
#[derive(Facet)]
struct AuthoredFile {
    intended_core_path: String,
    stage_bytes: u64,
    stage_sha256: String,
    source_rule: SourceRule,
    witnesses: Vec<Witness>,
}
#[derive(Facet)]
struct SourceRule {
    input: String,
    when: SourceWhen,
    template: bool,
}
#[derive(Facet)]
struct SourceWhen {
    targets: Vec<String>,
    any_features: Vec<String>,
}
#[derive(Facet)]
struct Witness {
    context: String,
    commit: String,
    present: bool,
    git_blob: Option<String>,
    raw_bytes: u64,
    raw_sha256: Option<String>,
}
#[derive(Facet)]
struct RawFact {
    git_blob: String,
    raw_bytes: u64,
    raw_sha256: String,
    normalized_bytes: u64,
    normalized_sha256: String,
    crlf_count: usize,
    lone_cr_count: usize,
    final_lf: bool,
    bom: bool,
    policy: String,
}
struct Fixture {
    shared: CoreTestFixture,
    ledger: Ledger,
    sources: Vec<Vec<u8>>,
    raw: BTreeMap<String, Vec<u8>>,
}
impl Fixture {
    fn load() -> Result<Self> {
        let shared = CoreTestFixture::load()?;
        let bytes = read_bounded(&shared.repository.join(LEDGER), LIMIT)?;
        let ledger: Ledger = facet_json::from_str(std::str::from_utf8(&bytes)?)
            .wrap_err("cannot parse bounded editor helper ledger")?;
        let sources = PATHS
            .iter()
            .map(|path| shared.read_source(path))
            .collect::<Result<Vec<_>>>()?;
        let oids = RAW_FACTS
            .iter()
            .map(|(oid, _, _)| (*oid).to_owned())
            .collect();
        let raw = read_git_blobs(&shared.repository, &oids)?;
        let result = Self {
            shared,
            ledger,
            sources,
            raw,
        };
        result.validate()?;
        Ok(result)
    }
    fn validate(&self) -> Result<()> {
        let commits = CONTEXTS
            .into_iter()
            .map(|(name, commit)| (name.to_owned(), commit.to_owned()))
            .collect::<BTreeMap<_, _>>();
        ensure!(
            self.ledger.schema == "sfm:core-editor-models-slice@1"
                && self.ledger.context_commits == commits,
            "frozen contexts changed"
        );
        let policy = &self.ledger.normalization;
        ensure!(
            policy.policy == "none" && policy.approved_changes.is_empty() && policy.raw_byte_exact,
            "no normalization is authorized for these helpers"
        );
        ensure!(
            self.ledger.owners.len() == 3 && self.ledger.consumer_owner_contracts.len() == 5,
            "owner scope changed"
        );
        let expected_owners = [
            ("editor_documents", &TARGETS[..2], &[][..]),
            (
                "editor_async_save",
                &TARGETS[..2],
                &["editor_documents"][..],
            ),
            (
                "item_inspection_language",
                &TARGETS[..1],
                &["editor_documents"][..],
            ),
            (
                "editor_v1_panel_clipping",
                &TARGETS[..2],
                &["workspace_panels"][..],
            ),
            ("workspace_panels", &TARGETS[..], &[][..]),
            ("canvas_text_editor", &TARGETS[..], &[][..]),
            (
                "command_palette",
                &TARGETS[..],
                &["client_actions", "client_theme", "keyboard_profiles"][..],
            ),
            ("client_overlay_scenes", &TARGETS[..2], &[][..]),
        ];
        let owners = self
            .ledger
            .owners
            .iter()
            .chain(&self.ledger.consumer_owner_contracts);
        for (owner, (name, support, requires)) in owners.zip(expected_owners) {
            let actual = self
                .shared
                .features
                .0
                .get(name)
                .ok_or_else(|| eyre::eyre!("helper owner is unregistered: {name}"))?;
            ensure!(
                owner.name == name
                    && owner.support == support
                    && owner.requires == requires
                    && owner.support == actual.supported_targets
                    && owner.requires == actual.requires,
                "helper owner contract changed: {name}"
            );
        }
        ensure!(
            self.ledger.scissor_consumer_union.support == TARGETS[..2]
                && self.ledger.scissor_consumer_union.any_features == SCISSOR_OWNERS
                && !self.ledger.scissor_consumer_union.requires_documents,
            "scissor must retain its independent five-consumer union"
        );
        ensure!(
            self.ledger.files.len() == 8 && self.ledger.raw_witnesses.len() == 10,
            "bounded helper cohort changed"
        );
        for (index, file) in self.ledger.files.iter().enumerate() {
            let (digest, bytes) = STAGES[index];
            ensure!(
                file.intended_core_path == PATHS[index]
                    && file.stage_sha256 == digest
                    && file.stage_bytes == bytes
                    && sha256(&self.sources[index]) == digest
                    && self.sources[index].len() as u64 == bytes,
                "promoted helper template golden changed: {}",
                PATHS[index]
            );
            let expected_owners = match index {
                0 => &SCISSOR_OWNERS[..],
                7 => &["editor_async_save"][..],
                _ => &["editor_documents"][..],
            };
            ensure!(
                file.source_rule.input == PATHS[index]
                    && file.source_rule.template
                    && file.source_rule.when.targets == TARGETS[..2]
                    && file.source_rule.when.any_features == expected_owners,
                "reviewed helper rule changed: {}",
                PATHS[index]
            );
            ensure!(file.witnesses.len() == 20, "helper context scope changed");
            let mut seen = BTreeSet::new();
            for row in &file.witnesses {
                ensure!(
                    seen.insert(row.context.as_str()),
                    "duplicate helper witness"
                );
                let expected_oid = golden_blob(&row.context, index);
                let fact =
                    expected_oid.and_then(|oid| RAW_FACTS.iter().find(|(id, _, _)| *id == oid));
                ensure!(
                    commits.get(&row.context) == Some(&row.commit)
                        && row.present == expected_oid.is_some()
                        && row.git_blob.as_deref() == expected_oid
                        && row.raw_bytes == fact.map_or(0, |(_, _, count)| *count)
                        && row.raw_sha256.as_deref() == fact.map(|(_, digest, _)| *digest),
                    "helper raw membership or identity changed: {}",
                    row.context
                );
            }
            ensure!(
                seen == commits.keys().map(String::as_str).collect(),
                "context coverage changed"
            );
        }
        for (fact, (oid, digest, bytes)) in self.ledger.raw_witnesses.iter().zip(RAW_FACTS) {
            let raw = self
                .raw
                .get(oid)
                .ok_or_else(|| eyre::eyre!("missing frozen helper blob"))?;
            ensure!(
                fact.git_blob == oid
                    && fact.raw_sha256 == digest
                    && fact.raw_bytes == bytes
                    && fact.normalized_sha256 == digest
                    && fact.normalized_bytes == bytes
                    && fact.crlf_count == 0
                    && fact.lone_cr_count == 0
                    && fact.final_lf
                    && !fact.bom
                    && fact.policy == "none",
                "raw helper evidence changed"
            );
            validate_raw(raw, digest, bytes)?;
        }
        Ok(())
    }
    fn selected(&self, context: &ProjectionContext) -> Result<BTreeSet<usize>> {
        let selection = self.shared.selection_for_assertion(context, &inventory())?;
        let mut actual = BTreeSet::new();
        for (index, path) in PATHS.iter().enumerate() {
            if let Some(input) = selection.inputs.get(*path) {
                ensure!(
                    input.input == *path
                        && input.template
                        && !selection.omitted_paths.contains(*path),
                    "helper routed away from core"
                );
                actual.insert(index);
            } else {
                ensure!(
                    selection.omitted_paths.contains(*path),
                    "false helper owner must cause an explicit omission: {path}"
                );
            }
        }
        Ok(actual)
    }
    fn rendered(&self, index: usize, context: &ProjectionContext) -> Result<String> {
        ensure!(
            self.selected(context)?.contains(&index),
            "refuse to render omitted helper"
        );
        render_java_source(std::str::from_utf8(&self.sources[index])?, context)
    }
    fn assert_profile(&self, context: &ProjectionContext) -> Result<usize> {
        let selected = self.selected(context)?;
        let expected = expected_selected(context);
        ensure!(
            selected == expected,
            "real selector differs from helper ownership predicates"
        );
        for index in &selected {
            let oid = profile_blob(context, *index);
            let raw = self
                .raw
                .get(oid)
                .ok_or_else(|| eyre::eyre!("missing profile raw oracle"))?;
            assert_eq!(
                self.rendered(*index, context)?.as_bytes(),
                raw,
                "{} / {:?}",
                PATHS[*index],
                context.features
            );
        }
        Ok(selected.len())
    }
}
fn validate_raw(bytes: &[u8], digest: &str, count: u64) -> Result<()> {
    ensure!(
        bytes.len() as u64 == count
            && sha256(bytes) == digest
            && !bytes.contains(&b'\r')
            && bytes.ends_with(b"\n")
            && !bytes.starts_with(&[0xef, 0xbb, 0xbf]),
        "raw-exact helper bytes changed"
    );
    Ok(())
}
fn golden_blob(context: &str, index: usize) -> Option<&'static str> {
    match context {
        "dev/1.19.2" => Some(DEV_BLOBS[0][index]),
        "dev/1.19.4" => Some(DEV_BLOBS[1][index]),
        _ => None,
    }
}
fn profile_blob(context: &ProjectionContext, index: usize) -> &'static str {
    let newer = usize::from(context.minecraft_version == "1.19.4");
    if index == 3 && !enabled(context, "item_inspection_language") {
        DEV_BLOBS[1][index]
    } else {
        DEV_BLOBS[newer][index]
    }
}
fn inventory() -> BTreeSet<String> {
    PATHS.into_iter().map(str::to_owned).collect()
}
fn enabled(context: &ProjectionContext, owner: &str) -> bool {
    context.features.get(owner).copied().unwrap_or(false)
}
fn expected_selected(context: &ProjectionContext) -> BTreeSet<usize> {
    if !matches!(context.minecraft_version.as_str(), "1.19.2" | "1.19.4") {
        return BTreeSet::new();
    }
    let mut result = BTreeSet::new();
    if SCISSOR_OWNERS.iter().any(|owner| enabled(context, owner)) {
        result.insert(0);
    }
    if enabled(context, "editor_documents") {
        result.extend(1..=6);
    }
    if enabled(context, "editor_async_save") {
        result.insert(7);
    }
    result
}
fn profile_flags(mask: usize) -> Vec<&'static str> {
    let mut result = FLAGS
        .iter()
        .enumerate()
        .filter_map(|(index, flag)| (mask & (1 << index) != 0).then_some(*flag))
        .collect::<Vec<_>>();
    // This is an explicit reviewed context, not automatic graph expansion.
    if result.contains(&"command_palette") {
        result.extend(["client_actions", "client_theme", "keyboard_profiles"]);
    }
    result
}
fn supported_mask(target: &str, mask: usize) -> bool {
    let has = |index: usize| mask & (1usize << index) != 0;
    (!has(1) || has(0)) && (!has(2) || has(0) && target == "1.19.2") && (!has(3) || has(4))
}

#[test]
fn twenty_frozen_contexts_preserve_all_160_memberships_and_16_raw_bodies() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut present = 0;
    let mut absent = 0;
    for (name, _) in CONTEXTS {
        let (_, target) = name
            .split_once('/')
            .ok_or_else(|| eyre::eyre!("invalid context"))?;
        let historical = matches!(name, "dev/1.19.2" | "dev/1.19.4");
        let mut flags = if historical {
            vec!["editor_documents", "editor_async_save", "workspace_panels"]
        } else {
            vec![]
        };
        if name == "dev/1.19.2" {
            flags.push("item_inspection_language");
        }
        let context = fixture.shared.context(target, &flags)?;
        let selected = fixture.selected(&context)?;
        for index in 0..8 {
            let expected_oid = golden_blob(name, index);
            assert_eq!(
                selected.contains(&index),
                expected_oid.is_some(),
                "{name} {}",
                PATHS[index]
            );
            if let Some(oid) = expected_oid {
                assert_eq!(
                    fixture.rendered(index, &context)?.as_bytes(),
                    fixture.raw[oid],
                    "{name}"
                );
                present += 1;
            } else {
                absent += 1;
            }
        }
    }
    assert_eq!((present, absent), (16, 144));
    Ok(())
}

#[test]
fn all_192_supported_owner_masks_use_real_selection_and_exact_independent_oracles() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut profiles = 0;
    let mut rendered = 0;
    for target in &TARGETS[..2] {
        for mask in 0..256 {
            if !supported_mask(target, mask) {
                continue;
            }
            let context = fixture.shared.context(target, &profile_flags(mask))?;
            rendered += fixture.assert_profile(&context)?;
            profiles += 1;
        }
    }
    assert_eq!(profiles, 192);
    assert_eq!(rendered, 1120);
    Ok(())
}

#[test]
fn clipping_documents_and_async_save_have_independent_minimal_contexts() -> Result<()> {
    let fixture = Fixture::load()?;
    for target in &TARGETS[..2] {
        let documents = fixture.shared.context(target, &["editor_documents"])?;
        assert_eq!(
            fixture.selected(&documents)?,
            BTreeSet::from([1, 2, 3, 4, 5, 6])
        );
        assert!(
            !fixture
                .rendered(3, &documents)?
                .contains("itemInspection()")
        );
        let async_save = fixture
            .shared
            .context(target, &["editor_documents", "editor_async_save"])?;
        assert_eq!(
            fixture.selected(&async_save)?,
            BTreeSet::from([1, 2, 3, 4, 5, 6, 7])
        );
        for owner in SCISSOR_OWNERS {
            let flags = match owner {
                "editor_v1_panel_clipping" => vec![owner, "workspace_panels"],
                "command_palette" => {
                    vec![owner, "client_actions", "client_theme", "keyboard_profiles"]
                }
                _ => vec![owner],
            };
            let context = fixture.shared.context(target, &flags)?;
            assert_eq!(
                fixture.selected(&context)?,
                BTreeSet::from([0]),
                "{target} {owner}"
            );
            assert!(!enabled(&context, "editor_documents"));
        }
    }
    assert!(
        fixture
            .shared
            .context("1.19.2", &["editor_async_save"])
            .is_err()
    );
    assert!(
        fixture
            .shared
            .context("1.19.2", &["item_inspection_language"])
            .is_err()
    );
    assert!(
        fixture
            .shared
            .context("1.19.4", &["editor_documents", "item_inspection_language"])
            .is_err()
    );
    assert!(
        fixture
            .shared
            .context("1.19.2", &["editor_v1_panel_clipping"])
            .is_err()
    );
    for target in &TARGETS[2..] {
        for flags in [
            &[][..],
            &["workspace_panels"][..],
            &["canvas_text_editor"][..],
            &[
                "command_palette",
                "client_actions",
                "client_theme",
                "keyboard_profiles",
            ][..],
        ] {
            assert!(
                fixture
                    .selected(&fixture.shared.context(target, flags)?)?
                    .is_empty(),
                "{target}"
            );
        }
        assert!(
            fixture
                .shared
                .context(target, &["editor_documents"])
                .is_err()
        );
        assert!(
            fixture
                .shared
                .context(target, &["client_overlay_scenes"])
                .is_err()
        );
    }
    Ok(())
}

#[test]
fn exact_models_retain_provenance_without_worker_runtime_or_document_clipping_coupling()
-> Result<()> {
    let fixture = Fixture::load()?;
    let context = fixture
        .shared
        .context("1.19.2", &["editor_documents", "editor_async_save"])?;
    let language = fixture.rendered(3, &context)?;
    assert!(language.contains("REMOTE_WORKER"));
    assert!(language.contains("SFMPath"));
    assert!(!language.contains("ProcessBuilder"));
    let snapshot = fixture.rendered(5, &context)?;
    for anchor in [
        "SFMResolverTextResult",
        "AnalysisIdentity",
        "semanticAnalysisSnapshot",
        "sourceRootIdentity",
        "lineEndingKind",
        "withSavedText",
    ] {
        assert!(
            snapshot.contains(anchor),
            "snapshot provenance anchor missing: {anchor}"
        );
    }
    let session = fixture.rendered(7, &context)?;
    for anchor in [
        "SAVING",
        "SAVED_NEWER_EDITS",
        "CompletableFuture",
        "detach",
        "generation",
    ] {
        assert!(
            session.contains(anchor),
            "save lifecycle anchor missing: {anchor}"
        );
    }
    let scissor = fixture.shared.context("1.19.2", &["workspace_panels"])?;
    assert!(fixture.rendered(0, &scissor)?.contains("STACK.peek()"));
    assert!(!enabled(&scissor, "editor_documents"));
    Ok(())
}

#[test]
fn ten_raw_lf_witnesses_reject_unauthorized_byte_and_eol_mutations() -> Result<()> {
    let fixture = Fixture::load()?;
    for (oid, digest, bytes) in RAW_FACTS {
        let raw = &fixture.raw[oid];
        validate_raw(raw, digest, bytes)?;
        let without_lf = &raw[..raw.len() - 1];
        assert!(validate_raw(without_lf, digest, bytes).is_err());
        let crlf = String::from_utf8(raw.clone())?
            .replace('\n', "\r\n")
            .into_bytes();
        assert!(validate_raw(&crlf, digest, bytes).is_err());
        let mut changed = raw.clone();
        changed[0] ^= 1;
        assert!(validate_raw(&changed, digest, bytes).is_err());
    }
    Ok(())
}

#[test]
fn common_body_edits_reach_both_targets_without_changing_original_sources() -> Result<()> {
    let fixture = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(super::core_inputs::CORE_ROOT);
    let marker = "// Shared editor helper edit proof.\n";
    for (index, path) in PATHS.iter().enumerate() {
        let source = std::str::from_utf8(&fixture.sources[index])?;
        ensure!(
            source.starts_with("package "),
            "common package anchor changed"
        );
        let edited = format!("{marker}{source}");
        let out = root.join(path);
        fs::create_dir_all(
            out.parent()
                .ok_or_else(|| eyre::eyre!("fixture parent absent"))?,
        )?;
        fs::write(out, edited.as_bytes())?;
    }
    let mut outputs = 0;
    for target in &TARGETS[..2] {
        let mut flags = vec!["editor_documents", "editor_async_save", "workspace_panels"];
        if *target == "1.19.2" {
            flags.push("item_inspection_language");
        }
        let context = fixture.shared.context(target, &flags)?;
        let selection = select_core_inputs(&fixture.shared.metadata, &context, &inventory())?;
        for (index, path) in PATHS.iter().enumerate() {
            let input = selection
                .inputs
                .get(*path)
                .ok_or_else(|| eyre::eyre!("helper not selected"))?;
            assert_eq!(input.input, *path);
            assert!(input.template);
            let source = read_bounded(&root.join(&input.input), LIMIT)?;
            let rendered = render_java_source(std::str::from_utf8(&source)?, &context)?;
            let oid = profile_blob(&context, index);
            assert_eq!(
                rendered.as_bytes(),
                [marker.as_bytes(), &fixture.raw[oid]].concat()
            );
            assert_eq!(fixture.shared.read_source(path)?, fixture.sources[index]);
            outputs += 1;
        }
    }
    assert_eq!(outputs, 16);
    Ok(())
}

#[test]
fn twenty_offline_git_trees_independently_recheck_all_160_membership_cells() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut present = 0;
    for (name, commit) in CONTEXTS {
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
        command.args(
            PATHS
                .iter()
                .map(|path| format!("platform/minecraft/{path}")),
        );
        let mut child = command
            .stdout(Stdio::piped())
            .stderr(Stdio::null())
            .spawn()?;
        let mut reader = child
            .stdout
            .take()
            .ok_or_else(|| eyre::eyre!("missing membership output"))?;
        let mut bytes = Vec::new();
        let result = reader.by_ref().take(16385).read_to_end(&mut bytes);
        drop(reader);
        if result.is_err() || bytes.len() > 16384 {
            let _ = child.kill();
            let _ = child.wait();
            result?;
            eyre::bail!("offline helper membership output exceeds bound");
        }
        ensure!(
            child.wait()?.success(),
            "offline frozen membership read failed"
        );
        let mut expected = (0..8)
            .filter_map(|index| {
                golden_blob(name, index)
                    .map(|oid| format!("100644 blob {oid}\tplatform/minecraft/{}\0", PATHS[index]))
            })
            .collect::<Vec<_>>();
        // Git tree output sorts paths, not object IDs.
        expected.sort_by(|left, right| {
            left.split_once('\t')
                .map(|(_, path)| path)
                .cmp(&right.split_once('\t').map(|(_, path)| path))
        });
        assert_eq!(bytes, expected.concat().as_bytes(), "{name}");
        present += expected.len();
    }
    assert_eq!(present, 16);
    Ok(())
}
