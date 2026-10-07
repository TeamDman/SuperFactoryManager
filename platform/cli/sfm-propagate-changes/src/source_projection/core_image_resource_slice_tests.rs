//! Frozen source-only image-resource leaf goldens.
//!
//! Historical bytes are bounded offline test oracles, never production inputs.
//! The production selector controls class absence; an ungated source preview
//! alone cannot prove absence. Later deliberate source edits require refreshing
//! these migration goldens. No Java compilation or gameplay claim is made.

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

const LEDGER: &str = "docs/tasks/sfm-core-image-resource-slice.json";
const PATH: &str = "src/main/java/ca/teamdman/sfm/common/resourcetype/ImageResourceType.java";
const BLOB: &str = "5be85ac469b77413f3400ede344d52a617d26a91";
const DIGEST: &str = "sha256:8a54e10f85b88178121888ade64298841d7017e5c071ffa74d9fa67bd45949ad";
const BYTE_COUNT: u64 = 4351;
const FLAGS: [&str; 2] = ["image_resources", "packet_values"];
const MAX_BYTES: u64 = 64 * 1024;
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
    owner: Owner,
    normalization: Normalization,
    file: AuthoredFile,
}
#[derive(Facet)]
struct Owner {
    name: String,
    support: Vec<String>,
    requires: Vec<String>,
}
#[derive(Facet)]
struct Normalization {
    policy: String,
    approved_changes: Vec<String>,
    raw_byte_exact: bool,
}
#[derive(Facet)]
struct AuthoredFile {
    intended_core_path: String,
    stage_bytes: u64,
    stage_sha256: String,
    git_blob: String,
    raw_sha256: String,
    raw_bytes: u64,
    crlf_count: usize,
    lone_cr_count: usize,
    final_lf: bool,
    bom: bool,
    witnesses: Vec<Witness>,
}
#[derive(Facet)]
struct Witness {
    context: String,
    present: bool,
    git_blob: Option<String>,
    raw_sha256: Option<String>,
    raw_bytes: u64,
}
struct Fixture {
    shared: CoreTestFixture,
    ledger: Ledger,
    source: Vec<u8>,
    raw: Vec<u8>,
}
impl Fixture {
    fn load() -> Result<Self> {
        let shared = CoreTestFixture::load()?;
        let ledger_bytes = read_bounded(&shared.repository.join(LEDGER), MAX_BYTES)?;
        let ledger: Ledger = facet_json::from_str(std::str::from_utf8(&ledger_bytes)?)
            .wrap_err("cannot parse bounded image resource golden evidence")?;
        let source = shared.read_source(PATH)?;
        let raw = read_git_blobs(&shared.repository, &BTreeSet::from([BLOB.to_owned()]))?
            .remove(BLOB)
            .ok_or_else(|| eyre::eyre!("missing pinned image resource blob"))?;
        let result = Self {
            shared,
            ledger,
            source,
            raw,
        };
        result.validate()?;
        Ok(result)
    }
    fn validate(&self) -> Result<()> {
        let commits = PINNED_CONTEXTS
            .into_iter()
            .map(|(context, commit)| (context.to_owned(), commit.to_owned()))
            .collect::<BTreeMap<_, _>>();
        ensure!(
            self.ledger.schema == "sfm:core-image-resource-slice@1"
                && self.ledger.context_commits == commits,
            "frozen context identity changed"
        );
        let owner = &self.ledger.owner;
        let actual_owner = self
            .shared
            .features
            .0
            .get("image_resources")
            .ok_or_else(|| eyre::eyre!("image owner is not registered"))?;
        ensure!(
            owner.name == "image_resources"
                && owner.support == ["1.19.2", "1.19.4"]
                && owner.requires == ["packet_values"]
                && owner.support == actual_owner.supported_targets
                && owner.requires == actual_owner.requires,
            "image owner support/prerequisites changed"
        );
        ensure!(
            self.ledger.normalization.policy == "none"
                && self.ledger.normalization.approved_changes.is_empty()
                && self.ledger.normalization.raw_byte_exact,
            "raw image witness normalization is not authorized"
        );
        let file = &self.ledger.file;
        ensure!(
            file.intended_core_path == PATH
                && file.stage_bytes == BYTE_COUNT
                && file.raw_bytes == BYTE_COUNT
                && file.stage_sha256 == DIGEST
                && file.raw_sha256 == DIGEST
                && file.git_blob == BLOB
                && file.crlf_count == 0
                && file.lone_cr_count == 0
                && file.final_lf
                && !file.bom,
            "image leaf golden identity changed"
        );
        for bytes in [&self.raw, &self.source] {
            ensure!(
                bytes.len() as u64 == BYTE_COUNT
                    && sha256(bytes) == DIGEST
                    && !bytes.contains(&b'\r')
                    && bytes.ends_with(b"\n")
                    && !bytes.starts_with(&[0xef, 0xbb, 0xbf]),
                "raw-exact image leaf bytes changed"
            );
        }
        ensure!(
            self.source == self.raw,
            "promoted image leaf is not the frozen exact body"
        );
        ensure!(file.witnesses.len() == 20, "image witness scope changed");
        let mut seen = BTreeSet::new();
        for row in &file.witnesses {
            ensure!(seen.insert(row.context.as_str()), "duplicate witness");
            let expected = matches!(row.context.as_str(), "dev/1.19.2" | "dev/1.19.4");
            ensure!(
                commits.contains_key(&row.context)
                    && row.present == expected
                    && row.git_blob.as_deref() == expected.then_some(BLOB)
                    && row.raw_sha256.as_deref() == expected.then_some(DIGEST)
                    && row.raw_bytes == if expected { BYTE_COUNT } else { 0 },
                "frozen image membership/bytes changed"
            );
        }
        ensure!(
            seen == commits.keys().map(String::as_str).collect(),
            "context coverage changed"
        );
        Ok(())
    }
    fn selected(&self, context: &ProjectionContext) -> Result<bool> {
        let selection = select_core_inputs(&self.shared.metadata, context, &inventory())?;
        let input = selection.inputs.get(PATH);
        if let Some(input) = input {
            ensure!(
                input.input == PATH && input.template,
                "image leaf was routed away from core template"
            );
            ensure!(
                !selection.omitted_paths.contains(PATH),
                "selected image leaf also omitted"
            );
        } else {
            ensure!(
                selection.omitted_paths.contains(PATH),
                "false owner is not an explicit omission"
            );
        }
        Ok(input.is_some())
    }
    fn render(&self, context: &ProjectionContext) -> Result<String> {
        ensure!(self.selected(context)?, "refuse to render an omitted class");
        render_java_source(std::str::from_utf8(&self.source)?, context)
    }
}
fn inventory() -> BTreeSet<String> {
    BTreeSet::from([PATH.to_owned()])
}

#[test]
fn twenty_frozen_image_leaf_memberships_and_two_exact_bodies_match_production() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut present = 0;
    let mut absent = 0;
    for (name, _) in PINNED_CONTEXTS {
        let (_, target) = name
            .split_once('/')
            .ok_or_else(|| eyre::eyre!("invalid context"))?;
        let expected = matches!(name, "dev/1.19.2" | "dev/1.19.4");
        let context = fixture
            .shared
            .context(target, if expected { &FLAGS } else { &[] })?;
        assert_eq!(fixture.selected(&context)?, expected, "{name}");
        if expected {
            assert_eq!(fixture.render(&context)?.as_bytes(), fixture.raw, "{name}");
            present += 1;
        } else {
            absent += 1;
        }
    }
    assert_eq!((present, absent), (2, 18));
    Ok(())
}

#[test]
fn image_owner_does_not_require_touch_gui_redstone_or_persistence_and_false_owner_omits_class()
-> Result<()> {
    let fixture = Fixture::load()?;
    for target in ["1.19.2", "1.19.4"] {
        let context = fixture.shared.context(target, &FLAGS)?;
        assert_eq!(
            context
                .features
                .iter()
                .filter_map(|(key, enabled)| enabled.then_some(key.as_str()))
                .collect::<BTreeSet<_>>(),
            BTreeSet::from(FLAGS)
        );
        for non_owner in [
            "touch_display",
            "client_frame_language",
            "client_frame_render",
            "client_manager_gui",
            "redstone_buffer_storage",
        ] {
            assert_eq!(context.features.get(non_owner), Some(&false), "{non_owner}");
        }
        // This separately proposed persistence owner is not a storage prerequisite.
        assert_ne!(
            context.features.get("buffer_image_persistence"),
            Some(&true)
        );
        assert_eq!(fixture.render(&context)?.as_bytes(), fixture.raw);
        for absent_flags in [&[][..], &["packet_values"][..]] {
            assert!(!fixture.selected(&fixture.shared.context(target, absent_flags)?)?);
        }
        assert!(
            fixture
                .shared
                .context(target, &["image_resources"])
                .is_err()
        );
        for companion in [
            "src/main/java/ca/teamdman/sfm/common/block/BufferBlock.java",
            "src/main/java/ca/teamdman/sfm/common/blockentity/BufferBlockEntityContents.java",
        ] {
            let source = fixture.shared.read_source(companion)?;
            let rendered = render_java_source(std::str::from_utf8(&source)?, &context)?;
            if companion.ends_with("/BufferBlock.java") {
                assert!(
                    rendered.contains("        Image,")
                        && !rendered.contains("getAnalogOutputSignal")
                );
            } else {
                assert!(
                    rendered.contains("public void markChanged()")
                        && rendered.contains("private final Runnable onChanged")
                        && !rendered.contains("public int getStoredRedstone()")
                );
            }
        }
    }
    for (target, _) in super::projection_catalog::SUPPORTED_TARGETS {
        if !matches!(target, "1.19.2" | "1.19.4") {
            assert!(fixture.shared.context(target, &FLAGS).is_err(), "{target}");
        }
    }
    Ok(())
}

#[test]
fn image_resource_transfer_simulation_exclusion_and_immutable_copy_anchors_remain_exact()
-> Result<()> {
    let fixture = Fixture::load()?;
    let source = std::str::from_utf8(&fixture.source)?;
    for anchor in [
        "if (!held.isEmpty() || !contents.allowInsertion(ImageResourceType.this)) return image;",
        "if (!simulate) {\n                    held = image;",
        "if (!simulate && !held.isEmpty()) {",
        "contents.lastUsedResource = BufferBlock.ContainedResource.Image;",
        "contents.lastUsedResource = BufferBlock.ContainedResource.Unknown;",
        "contents.markChanged();",
        "return slot == 0 && amount >= 1 ? handler.extractImage(simulate) : SFMImageStack.EMPTY;",
        "return slot == 0 ? handler.insertImage(stack, simulate) : stack;",
        "return amount <= 0 ? SFMImageStack.EMPTY : stack;",
        "public SFMImageStack copy(SFMImageStack stack) {\n        return stack;",
    ] {
        assert!(
            source.contains(anchor),
            "historical resource anchor changed: {anchor}"
        );
    }
    assert!(!source.contains("{%"));
    Ok(())
}

#[test]
fn common_image_resource_edit_reaches_both_supported_targets_through_real_renderer() -> Result<()> {
    let fixture = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let anchor = "public class ImageResourceType extends ScalarResourceType<SFMImageStack, IImageHandler> {\n";
    let replacement = format!("{anchor}    // Common image resource edit proof.\n");
    let source = std::str::from_utf8(&fixture.source)?;
    ensure!(
        source.matches(anchor).count() == 1,
        "common edit anchor changed"
    );
    let edited = source.replacen(anchor, &replacement, 1);
    let root = temp.path().join(super::core_inputs::CORE_ROOT);
    let path = root.join(PATH);
    fs::create_dir_all(
        path.parent()
            .ok_or_else(|| eyre::eyre!("missing fixture parent"))?,
    )?;
    fs::write(&path, edited.as_bytes())?;
    for target in ["1.19.2", "1.19.4"] {
        let context = fixture.shared.context(target, &FLAGS)?;
        let selected = select_core_inputs(&fixture.shared.metadata, &context, &inventory())?;
        let input = selected
            .inputs
            .get(PATH)
            .ok_or_else(|| eyre::eyre!("missing selected input"))?;
        assert_eq!(input.input, PATH);
        let bytes = read_bounded(&root.join(&input.input), MAX_BYTES)?;
        let rendered = render_java_source(std::str::from_utf8(&bytes)?, &context)?;
        assert_eq!(rendered, edited, "{target}");
        assert!(rendered.contains("Common image resource edit proof."));
    }
    assert_eq!(fixture.shared.read_source(PATH)?, fixture.source);
    Ok(())
}

#[test]
fn twenty_offline_git_membership_cells_match_exact_image_blob_or_absence() -> Result<()> {
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
                &format!("platform/minecraft/{PATH}"),
            ]);
        let mut child = command
            .stdout(Stdio::piped())
            .stderr(Stdio::null())
            .spawn()?;
        let mut reader = child
            .stdout
            .take()
            .ok_or_else(|| eyre::eyre!("missing membership output"))?;
        let mut bytes = Vec::new();
        let result = reader.by_ref().take(4097).read_to_end(&mut bytes);
        drop(reader);
        if result.is_err() || bytes.len() > 4096 {
            let _ = child.kill();
            let _ = child.wait();
            result?;
            eyre::bail!("offline membership output exceeds bound");
        }
        ensure!(
            child.wait()?.success(),
            "offline frozen membership read failed"
        );
        if matches!(name, "dev/1.19.2" | "dev/1.19.4") {
            assert_eq!(
                bytes,
                format!("100644 blob {BLOB}\tplatform/minecraft/{PATH}\0").as_bytes()
            );
            present += 1;
        } else {
            assert!(bytes.is_empty(), "{name}: unexpected image source");
            absent += 1;
        }
    }
    assert_eq!((present, absent), (2, 18));
    Ok(())
}
