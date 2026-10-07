//! Frozen source-only RedstoneResourceType migration goldens.
//!
//! Exact Git objects are bounded offline test witnesses, never production
//! fallback sources. Run only after the shared input is promoted into core.
//! These tests prove source selection/rendering and named companion contracts;
//! they do not prove Java compilation or gameplay. Deliberate core edits need
//! reviewed golden changes rather than automatic witness adoption.

#![cfg(test)]

use super::context::ProjectionContext;
use super::core_inputs::CORE_ROOT;
use super::core_inputs::collect_core_artifacts;
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

const LEDGER: &str = "docs/tasks/sfm-core-redstone-resource-slice.json";
const PATH: &str = "src/main/java/ca/teamdman/sfm/common/resourcetype/RedstoneResourceType.java";
const SOURCE_SHA: &str = "sha256:ea2e4f8ab81e177a6b9dcf027e61aeb2cdecb93c850bfd253eed91c37a619747";
const LIMIT: u64 = 64 * 1024;
const OWNER: &str = "redstone_buffer_storage";
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
const RAW_FACTS: [(&str, &str, u64, usize, &str, u64); 2] = [
    (
        "e1aab360731f8553130df1e13b142dc6a1a43486",
        "sha256:3aff15c27524a1903b8de588d8a85da4b4acabf8c68b0d59ec7f5cd9f7d04391",
        2342,
        77,
        "sha256:87b088ad28b82d18b884355da74fc616e6666fa51b4f71c3c9f36f6522343e2e",
        2265,
    ),
    (
        "dd591c13e4d5f37b18dbf58e78d35a741ba45b83",
        "sha256:78ff6b2efc73e7b064dfb61ad0605a236b2543c5d84c8d300318fdba67978ca4",
        2750,
        88,
        "sha256:54b4e547dffec6dd27d176b582afd9d5e8c5bd3cdf6d2d9bc7bbcc4fae51fcd1",
        2662,
    ),
];

#[derive(Facet)]
struct Ledger {
    schema: String,
    context_commits: BTreeMap<String, String>,
    owner: Owner,
    normalization: Normalization,
    file: AuthoredFile,
}
#[derive(Facet)]
struct Owner {
    name: String,
    support: Vec<String>,
    requires: Vec<String>,
    no_live_read_prerequisite: bool,
}
#[derive(Facet)]
struct Normalization {
    policy: String,
    approved_git_blobs: Vec<String>,
    lf_added: bool,
    bom_allowed: bool,
    lone_cr_allowed: bool,
    raw_byte_exact: bool,
}
#[derive(Facet)]
struct AuthoredFile {
    intended_core_path: String,
    stage_bytes: u64,
    stage_sha256: String,
    variants: Vec<Variant>,
    witnesses: Vec<Witness>,
}
#[derive(Facet)]
struct Variant {
    git_blob: String,
    raw_bytes: u64,
    raw_sha256: String,
    normalized_bytes: u64,
    normalized_sha256: String,
    crlf_count: usize,
    lone_cr_count: usize,
    final_lf: bool,
    lf_added: bool,
    bom: bool,
}
#[derive(Facet)]
struct Witness {
    context: String,
    commit: String,
    present: bool,
    owner_enabled: bool,
    git_blob: String,
    raw_bytes: u64,
    raw_sha256: String,
    normalized_bytes: u64,
    normalized_sha256: String,
}
struct Fixture {
    shared: CoreTestFixture,
    ledger: Ledger,
    source: Vec<u8>,
    raw: BTreeMap<String, Vec<u8>>,
    normalized: BTreeMap<String, Vec<u8>>,
}
impl Fixture {
    fn load() -> Result<Self> {
        let shared = CoreTestFixture::load()?;
        let bytes = read_bounded(&shared.repository.join(LEDGER), LIMIT)?;
        let ledger: Ledger = facet_json::from_str(std::str::from_utf8(&bytes)?)
            .wrap_err("cannot parse bounded redstone resource evidence")?;
        let source = shared.read_source(PATH)?;
        let raw = read_git_blobs(
            &shared.repository,
            &RAW_FACTS.iter().map(|fact| fact.0.to_owned()).collect(),
        )?;
        let normalized = raw
            .iter()
            .map(|(oid, bytes)| Ok((oid.clone(), approved_normalization(oid, bytes)?)))
            .collect::<Result<_>>()?;
        let result = Self {
            shared,
            ledger,
            source,
            raw,
            normalized,
        };
        result.validate()?;
        Ok(result)
    }
    fn validate(&self) -> Result<()> {
        let contexts = CONTEXTS
            .into_iter()
            .map(|(context, commit)| (context.to_owned(), commit.to_owned()))
            .collect::<BTreeMap<_, _>>();
        ensure!(
            self.ledger.schema == "sfm:core-redstone-resource-slice@1"
                && self.ledger.context_commits == contexts,
            "frozen redstone resource contexts changed"
        );
        let actual = self
            .shared
            .features
            .0
            .get(OWNER)
            .ok_or_else(|| eyre::eyre!("redstone buffer owner is unregistered"))?;
        let owner = &self.ledger.owner;
        ensure!(
            owner.name == OWNER
                && owner.support == TARGETS[..2]
                && owner.requires.is_empty()
                && owner.no_live_read_prerequisite
                && actual.supported_targets == owner.support
                && actual.requires.is_empty(),
            "redstone buffer owner support/prerequisites changed"
        );
        let policy = &self.ledger.normalization;
        ensure!(
            policy.policy == "sfm:java_lf_final_newline@1"
                && policy.approved_git_blobs == RAW_FACTS.map(|fact| fact.0.to_owned())
                && !policy.lf_added
                && !policy.bom_allowed
                && !policy.lone_cr_allowed
                && !policy.raw_byte_exact,
            "unapproved redstone normalization policy"
        );
        let file = &self.ledger.file;
        ensure!(
            file.intended_core_path == PATH
                && file.stage_bytes == 3452
                && file.stage_sha256 == SOURCE_SHA
                && self.source.len() == 3452
                && sha256(&self.source) == SOURCE_SHA
                && !self.source.contains(&b'\r')
                && self.source.ends_with(b"\n")
                && !self.source.starts_with(&[0xef, 0xbb, 0xbf]),
            "actual core template differs from reviewed stage"
        );
        ensure!(file.variants.len() == 2, "raw variant count changed");
        for (row, fact) in file.variants.iter().zip(RAW_FACTS) {
            ensure!(
                row.git_blob == fact.0
                    && row.raw_sha256 == fact.1
                    && row.raw_bytes == fact.2
                    && row.crlf_count == fact.3
                    && row.normalized_sha256 == fact.4
                    && row.normalized_bytes == fact.5
                    && row.lone_cr_count == 0
                    && row.final_lf
                    && !row.lf_added
                    && !row.bom,
                "raw or approved-normalized golden facts changed"
            );
        }
        ensure!(
            file.witnesses.len() == 20,
            "redstone witness coverage changed"
        );
        let mut seen = BTreeSet::new();
        for row in &file.witnesses {
            let fact = RAW_FACTS[usize::from(owner_enabled(&row.context))];
            ensure!(
                seen.insert(row.context.as_str())
                    && contexts.get(&row.context) == Some(&row.commit)
                    && row.present
                    && row.owner_enabled == owner_enabled(&row.context)
                    && row.git_blob == fact.0
                    && row.raw_sha256 == fact.1
                    && row.raw_bytes == fact.2
                    && row.normalized_sha256 == fact.4
                    && row.normalized_bytes == fact.5,
                "frozen redstone witness membership/identity changed"
            );
        }
        ensure!(
            seen == contexts.keys().map(String::as_str).collect(),
            "context coverage changed"
        );
        Ok(())
    }
    fn selected(&self, context: &ProjectionContext) -> Result<()> {
        let selection = select_core_inputs(&self.shared.metadata, context, &inventory())?;
        let input = selection
            .inputs
            .get(PATH)
            .ok_or_else(|| eyre::eyre!("unconditional released redstone resource was omitted"))?;
        // Every .java renders automatically, even with sparse/default template:false.
        ensure!(
            input.input == PATH && !selection.omitted_paths.contains(PATH),
            "redstone input routed away from current shared core"
        );
        Ok(())
    }
    fn render(&self, context: &ProjectionContext) -> Result<String> {
        self.selected(context)?;
        render_java_source(std::str::from_utf8(&self.source)?, context)
    }
    fn expected(&self, enabled: bool) -> &[u8] {
        &self.normalized[RAW_FACTS[usize::from(enabled)].0]
    }
}
fn inventory() -> BTreeSet<String> {
    BTreeSet::from([PATH.to_owned()])
}
fn owner_enabled(name: &str) -> bool {
    matches!(name, "dev/1.19.2" | "dev/1.19.4")
}
fn approved_normalization(oid: &str, bytes: &[u8]) -> Result<Vec<u8>> {
    let (_, raw_sha, raw_len, crlf, normalized_sha, normalized_len) = RAW_FACTS
        .iter()
        .find(|fact| fact.0 == oid)
        .copied()
        .ok_or_else(|| eyre::eyre!("unapproved redstone resource blob"))?;
    ensure!(
        bytes.len() as u64 == raw_len
            && sha256(bytes) == raw_sha
            && bytes.ends_with(b"\r\n")
            && !bytes.starts_with(&[0xef, 0xbb, 0xbf])
            && bytes.windows(2).filter(|pair| *pair == b"\r\n").count() == crlf
            && bytes.iter().filter(|&&byte| byte == b'\r').count() == crlf,
        "raw redstone resource witness changed"
    );
    let normalized = std::str::from_utf8(bytes)?
        .replace("\r\n", "\n")
        .into_bytes();
    ensure!(
        normalized.len() as u64 == normalized_len
            && sha256(&normalized) == normalized_sha
            && normalized.ends_with(b"\n")
            && !normalized.contains(&b'\r'),
        "approved redstone resource normalization changed"
    );
    Ok(normalized)
}

#[test]
fn all_twenty_frozen_redstone_resource_bodies_reconstruct_through_real_selection_and_render()
-> Result<()> {
    let fixture = Fixture::load()?;
    let mut bodies = 0;
    for (name, _) in CONTEXTS {
        let (_, target) = name
            .split_once('/')
            .ok_or_else(|| eyre::eyre!("invalid redstone context"))?;
        let enabled = owner_enabled(name);
        let flags = if enabled { vec![OWNER] } else { vec![] };
        let context = fixture.shared.context(target, &flags)?;
        assert_eq!(
            fixture.render(&context)?.as_bytes(),
            fixture.expected(enabled),
            "{name}"
        );
        bodies += 1;
    }
    assert_eq!(bodies, 20);
    Ok(())
}

#[test]
fn eight_buffer_and_live_read_masks_are_independent_without_gui_packet_or_image_prerequisites()
-> Result<()> {
    let fixture = Fixture::load()?;
    let mut profiles = 0;
    for target in &TARGETS[..2] {
        for mask in 0..4 {
            let mut flags = vec![];
            if mask & 1 != 0 {
                flags.push(OWNER);
            }
            if mask & 2 != 0 {
                flags.push("redstone_live_read");
            }
            let context = fixture.shared.context(target, &flags)?;
            assert_eq!(
                fixture.render(&context)?.as_bytes(),
                fixture.expected(mask & 1 != 0)
            );
            assert_eq!(
                context
                    .features
                    .iter()
                    .filter_map(|(name, enabled)| enabled.then_some(name.as_str()))
                    .collect::<BTreeSet<_>>(),
                flags.iter().copied().collect::<BTreeSet<_>>()
            );
            for absent in [
                "image_resources",
                "packet_values",
                "client_manager",
                "touch_display",
                "client_frame_render",
                "client_manager_gui",
                "redstone_signal_documentation",
            ] {
                assert_ne!(context.features.get(absent), Some(&true), "{absent}");
            }
            profiles += 1;
        }
    }
    assert_eq!(profiles, 8);
    for target in &TARGETS[2..] {
        assert!(
            fixture.shared.context(target, &[OWNER]).is_err(),
            "{target}"
        );
        fixture.selected(&fixture.shared.context(target, &[])?)?;
    }
    Ok(())
}

#[test]
fn core_resource_buffer_and_storage_methods_close_the_exact_storage_owner_contract() -> Result<()> {
    let fixture = Fixture::load()?;
    let resource = "src/main/java/ca/teamdman/sfm/common/resourcetype/ResourceType.java";
    let buffer = "src/main/java/ca/teamdman/sfm/common/blockentity/BufferBlockEntityContents.java";
    let storage = "src/main/java/ca/teamdman/sfm/common/capability/RedstoneSignalStorage.java";
    let signal = "src/main/java/ca/teamdman/sfm/common/capability/IRedstoneSignalStorage.java";
    for target in &TARGETS[..2] {
        for enabled in [false, true] {
            let flags = if enabled { vec![OWNER] } else { vec![] };
            let context = fixture.shared.context(target, &flags)?;
            let render = |path| -> Result<String> {
                render_java_source(
                    std::str::from_utf8(&fixture.shared.read_source(path)?)?,
                    &context,
                )
            };
            let parent = render(resource)?;
            for anchor in [
                "public abstract STACK extract(",
                "public boolean canExtract(",
                "public boolean canInsert(",
                "public abstract STACK insert(",
                "* @return the remainder, what was not inserted",
            ] {
                assert!(parent.contains(anchor), "{anchor}");
            }
            let contents = render(buffer)?;
            assert!(
                contents.contains("public boolean allowInsertion(ResourceType<?, ?, ?> queryType)")
            );
            assert!(contents.contains("public final BufferBlockTier tier;"));
            assert_eq!(
                contents.contains("public void onRedstoneChanged()"),
                enabled
            );
            assert_eq!(
                contents.contains("private final Runnable onChanged"),
                enabled
            );
            let store = render(storage)?;
            assert_eq!(
                store.contains("protected void onContentsChanged()"),
                enabled
            );
            assert_eq!(store.contains("onContentsChanged();"), enabled);
            assert_eq!(store.contains("if (!simulate && accept > 0)"), enabled);
            assert_eq!(store.contains("if (!simulate && extract > 0)"), enabled);
            let interface = render(signal)?;
            for anchor in [
                "int insert(int amount, boolean simulate);",
                "int extract(int amount, boolean simulate);",
                "int getMaxStoredAmount();",
                "boolean canExtract();",
                "boolean canReceive();",
            ] {
                assert!(interface.contains(anchor), "{anchor}");
            }
            let output = fixture.render(&context)?;
            assert_eq!(
                output.contains(
                    "public boolean canExtract(IRedstoneSignalStorage capability, int slot)"
                ),
                enabled
            );
            assert_eq!(
                output.contains(
                    "public boolean canInsert(IRedstoneSignalStorage capability, int slot)"
                ),
                enabled
            );
            if enabled {
                assert!(output.contains("Math.max(0L, Math.min(amount, Integer.MAX_VALUE))"));
                assert!(
                    output
                        .contains("return integer - redstoneCapability.insert(integer, simulate);")
                );
                assert!(output.contains("return redstoneCapability.getMaxStoredAmount();"));
                assert!(!output.contains("import ca.teamdman.sfm.common.block.BufferBlock;"));
            } else {
                assert_eq!(output.matches("        return 0;\n").count(), 2);
                assert!(output.contains("return 15;"));
                assert!(output.contains(
                    "contents.lastUsedResource = BufferBlock.ContainedResource.Redstone;"
                ));
            }
        }
    }
    // Version adaptation is in shared existing parents, not a functional flag surrogate.
    for target in TARGETS {
        let context = fixture.shared.context(target, &[])?;
        let integer = fixture.shared.read_source(
            "src/main/java/ca/teamdman/sfm/common/resourcetype/IntegerResourceType.java",
        )?;
        let rendered = render_java_source(std::str::from_utf8(&integer)?, &context)?;
        assert_eq!(
            rendered.contains("Identifier registryKey"),
            target == "26.1.2"
        );
        assert_eq!(
            rendered.contains("ResourceLocation registryKey"),
            target != "26.1.2"
        );
        let location = fixture
            .shared
            .read_source("src/main/java/ca/teamdman/sfm/common/util/SFMResourceLocation.java")?;
        let rendered = render_java_source(std::str::from_utf8(&location)?, &context)?;
        assert!(rendered.contains("fromNamespaceAndPath"));
    }
    Ok(())
}

#[test]
fn two_approved_crlf_witnesses_reject_unapproved_ids_bom_eol_and_token_mutations() -> Result<()> {
    let fixture = Fixture::load()?;
    for (oid, _, _, _, _, _) in RAW_FACTS {
        let raw = &fixture.raw[oid];
        approved_normalization(oid, raw)?;
        assert!(approved_normalization("0000000000000000000000000000000000000000", raw).is_err());
        assert!(approved_normalization(oid, &raw[..raw.len() - 2]).is_err());
        let mut token = raw.clone();
        token[0] ^= 1;
        assert!(approved_normalization(oid, &token).is_err());
        let mut bom = vec![0xef, 0xbb, 0xbf];
        bom.extend(raw);
        assert!(approved_normalization(oid, &bom).is_err());
        assert!(approved_normalization(oid, &fixture.normalized[oid]).is_err());
        let mut lone_cr = raw.clone();
        let pos = lone_cr
            .windows(2)
            .position(|pair| pair == b"\r\n")
            .ok_or_else(|| eyre::eyre!("expected reviewed CRLF witness"))?;
        lone_cr.remove(pos + 1);
        assert!(approved_normalization(oid, &lone_cr).is_err());
    }
    Ok(())
}

fn isolated_project(
    fixture: &Fixture,
    temp: &tempfile::TempDir,
    context: &ProjectionContext,
    source: &[u8],
) -> Result<(std::path::PathBuf, super::core_inputs::CoreProjectInputs)> {
    let root = temp.path().join(CORE_ROOT);
    let mut metadata = fixture.shared.metadata.clone();
    metadata.source_rules.retain(|path, _| path == PATH);
    let selection = select_core_inputs(&metadata, context, &inventory())?;
    for (output, selected) in &selection.inputs {
        let bytes = if output == PATH {
            source.to_vec()
        } else {
            read_bounded(&fixture.shared.core.join(&selected.input), 16 * 1024 * 1024)?
        };
        let dest = root.join(&selected.input);
        fs::create_dir_all(
            dest.parent()
                .ok_or_else(|| eyre::eyre!("fixture parent absent"))?,
        )?;
        fs::write(dest, bytes)?;
    }
    Ok((root, metadata))
}

#[test]
fn fixed_core_collection_renders_java_automatically_and_rejects_unsafe_template_mutations()
-> Result<()> {
    let fixture = Fixture::load()?;
    let context = fixture.shared.context("1.19.2", &[OWNER])?;
    let temp = tempfile::tempdir()?;
    let (root, mut metadata) = isolated_project(&fixture, &temp, &context, &fixture.source)?;
    // Explicitly remove any sparse opt-in rule: .java still must render.
    metadata.source_rules.remove(PATH);
    let selected = select_core_inputs(&metadata, &context, &inventory())?;
    assert!(!selected.inputs[PATH].template);
    let artifacts = collect_core_artifacts(&root, &selected, &context)?;
    let java = &artifacts[PATH];
    assert_eq!(java.source_bytes, fixture.source);
    assert_eq!(java.source_path, format!("{CORE_ROOT}/{PATH}"));
    assert!(java.overlay.is_none());
    let output = std::str::from_utf8(&java.output_bytes)?;
    let (_, body) = output
        .split_once('\n')
        .ok_or_else(|| eyre::eyre!("generated banner absent"))?;
    assert_eq!(body.as_bytes(), fixture.expected(true));
    assert!(!body.contains("{%"));
    let wrong_root = temp.path().join("alternate-core");
    assert!(collect_core_artifacts(&wrong_root, &selected, &context).is_err());
    let original = std::str::from_utf8(&fixture.source)?;
    for invalid in [
        original.replacen(
            "features.redstone_buffer_storage",
            "features.unregistered_redstone_owner",
            1,
        ),
        original.replacen("{% endif %}", "{% endcase %}", 1),
        format!("{{% case environment %}}\n{{% when \"release\" %}}\n{original}{{% endcase %}}\n"),
        format!(
            "{{% case projection_key %}}\n{{% when \"proof/key\" %}}\n{original}{{% endcase %}}\n"
        ),
    ] {
        fs::write(root.join(PATH), invalid.as_bytes())?;
        assert!(collect_core_artifacts(&root, &selected, &context).is_err());
    }
    assert_eq!(fixture.shared.read_source(PATH)?, fixture.source);
    Ok(())
}

#[test]
fn twelve_common_edit_outputs_reach_all_ten_targets_without_changing_owned_guards() -> Result<()> {
    let fixture = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let anchor =
        "public class RedstoneResourceType extends IntegerResourceType<IRedstoneSignalStorage> {\n";
    let replacement = format!("{anchor}    // Common redstone resource edit proof.\n");
    let original = std::str::from_utf8(&fixture.source)?;
    ensure!(
        original.matches(anchor).count() == 1,
        "common class anchor changed"
    );
    let edited = original.replacen(anchor, &replacement, 1);
    let path = temp.path().join(CORE_ROOT).join(PATH);
    fs::create_dir_all(
        path.parent()
            .ok_or_else(|| eyre::eyre!("fixture parent absent"))?,
    )?;
    fs::write(&path, &edited)?;
    let mut outputs = 0;
    for target in TARGETS {
        let enabled = if matches!(target, "1.19.2" | "1.19.4") {
            vec![false, true]
        } else {
            vec![false]
        };
        for on in enabled {
            let flags = if on { vec![OWNER] } else { vec![] };
            let context = fixture.shared.context(target, &flags)?;
            fixture.selected(&context)?;
            let bytes = read_bounded(&path, LIMIT)?;
            let actual = render_java_source(std::str::from_utf8(&bytes)?, &context)?;
            let expected =
                std::str::from_utf8(fixture.expected(on))?.replacen(anchor, &replacement, 1);
            assert_eq!(actual, expected, "{target}");
            outputs += 1;
        }
    }
    assert_eq!(outputs, 12);
    assert_eq!(fixture.shared.read_source(PATH)?, fixture.source);
    Ok(())
}

#[test]
fn twenty_bounded_offline_trees_recheck_exact_redstone_resource_blob_membership() -> Result<()> {
    let fixture = Fixture::load()?;
    for (name, commit) in CONTEXTS {
        let oid = RAW_FACTS[usize::from(owner_enabled(name))].0;
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
                &format!("platform/minecraft/{PATH}"),
            ]);
        let mut child = command
            .stdout(Stdio::piped())
            .stderr(Stdio::null())
            .spawn()?;
        let mut reader = child
            .stdout
            .take()
            .ok_or_else(|| eyre::eyre!("missing tree output"))?;
        let mut bytes = Vec::new();
        let result = reader.by_ref().take(4097).read_to_end(&mut bytes);
        drop(reader);
        if result.is_err() || bytes.len() > 4096 {
            let _ = child.kill();
            let _ = child.wait();
            result?;
            eyre::bail!("frozen redstone membership output exceeds bound");
        }
        ensure!(child.wait()?.success(), "offline redstone tree read failed");
        assert_eq!(
            bytes,
            format!("100644 blob {oid}\tplatform/minecraft/{PATH}\0").as_bytes(),
            "{name}"
        );
    }
    Ok(())
}
