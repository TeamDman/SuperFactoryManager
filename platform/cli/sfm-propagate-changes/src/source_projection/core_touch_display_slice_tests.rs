//! Frozen source-only Touch Display and local insertion migration goldens.
//!
//! Actual production source selection/rendering reconstructs fourteen raw
//! development witnesses and proves 126 absent cells. Full-project wire-layout
//! acceptance is deliberately separate: these are bounded source contracts,
//! not Minecraft compilation, live runtime or network acceptance evidence.
//! Later deliberate source changes require reviewing these migration goldens.

#![cfg(test)]

use super::context::ProjectionContext;
use super::core_inputs::CORE_ROOT;
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
use std::fs;
use std::io::Read;
use std::process::Stdio;

const LEDGER: &str = "docs/tasks/sfm-core-touch-display-slice.json";
const MAX_LEDGER_BYTES: u64 = 128 * 1024;
const MAX_SOURCE_BYTES: u64 = 64 * 1024;
const MAX_TREE_BYTES: u64 = 16 * 1024;
const BLOCK: &str = "src/main/java/ca/teamdman/sfm/common/block/TouchDisplayBlock.java";
const SURFACE: &str = "src/main/java/ca/teamdman/sfm/common/block/TouchDisplaySurface.java";
const ENTITY: &str =
    "src/main/java/ca/teamdman/sfm/common/blockentity/TouchDisplayBlockEntity.java";
const CACHE: &str = "src/main/java/ca/teamdman/sfm/client/render/TouchDisplayTextureCache.java";
const RUNTIME: &str = "src/main/java/ca/teamdman/sfm/client/render/TouchDisplayTextureRuntime.java";
const RENDERER: &str =
    "src/main/java/ca/teamdman/sfm/client/render/TouchDisplayBlockEntityRenderer.java";
const INSERTER: &str = "src/main/java/ca/teamdman/sfm/common/net/SFMPacketInventoryInserter.java";
const PATHS: [&str; 7] = [BLOCK, SURFACE, ENTITY, CACHE, RUNTIME, RENDERER, INSERTER];
const D1_FLAGS: [&str; 4] = [
    "touch_display_furnace_placement",
    "touch_display_seamless_surface",
    "client_frame_render",
    "touch_display_terminal_mount",
];
const PINNED_CORE: [(&str, &str, u64); 7] = [
    (
        BLOCK,
        "sha256:8a78ede9f295bdfb63979c8fb15203b0f2b1ac6b0b961a147c785e5a632e64fd",
        8391,
    ),
    (
        SURFACE,
        "sha256:c467ef279b7677a023abc2929b7caba48713f2e66fd768809e8e4773f5e06511",
        4071,
    ),
    (
        ENTITY,
        "sha256:fb01c764297dfeee1e42c3374e552bd882dbf828d1a9009afcbd0b885ad7118a",
        11649,
    ),
    (
        CACHE,
        "sha256:05828823505162abefb900f75343c97bc75a826e7268e1f31c88591065665404",
        3543,
    ),
    (
        RUNTIME,
        "sha256:ece5fc71ee107fa922b250781787c3613ccbcb848b7256670725b8d3abc9b032",
        4317,
    ),
    (
        RENDERER,
        "sha256:8a454db0e0fb22a756cd9d6d9bce0a6a21af52b43995ad96ce9c4499d86d0f07",
        6013,
    ),
    (
        INSERTER,
        "sha256:6a74880f25ee6110f5cd60dd669d0b60554e86fafc76d1c2b90e9cc3c8c5cc46",
        4068,
    ),
];
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
#[derive(Clone, Copy)]
struct RawSpec {
    path: &'static str,
    blob: &'static str,
    digest: &'static str,
    bytes: u64,
    contexts: &'static [&'static str],
}
const RAW_SPECS: [RawSpec; 11] = [
    RawSpec {
        path: RENDERER,
        blob: "1a8555432aff8eaf4d168ffa562192caa02696d8",
        digest: "sha256:203d98055911561658d88d7727439e20d5caf98efb9ff37f91fce0e3387e4e96",
        bytes: 5016,
        contexts: &["dev/1.19.2"],
    },
    RawSpec {
        path: CACHE,
        blob: "77188deb696c0c32a8fcbfe3c49ae2a277126bad",
        digest: "sha256:05828823505162abefb900f75343c97bc75a826e7268e1f31c88591065665404",
        bytes: 3543,
        contexts: &["dev/1.19.2", "dev/1.19.4"],
    },
    RawSpec {
        path: RUNTIME,
        blob: "31488b9b48001d3abcac82f1e4c2cb92dab7c4be",
        digest: "sha256:ece5fc71ee107fa922b250781787c3613ccbcb848b7256670725b8d3abc9b032",
        bytes: 4317,
        contexts: &["dev/1.19.2", "dev/1.19.4"],
    },
    RawSpec {
        path: BLOCK,
        blob: "6e6c6244cbe6aab89c4ae0eb956bca9e42874b19",
        digest: "sha256:100f10879dd1c21f3722127ae51a9dfa26be6d8039d4635fbc3c6ce5450b6e69",
        bytes: 8239,
        contexts: &["dev/1.19.2"],
    },
    RawSpec {
        path: SURFACE,
        blob: "bf436cb8fc7f7ebd6b98d7a7bb53c48c34eebe53",
        digest: "sha256:a3a5f40871a69d0a9ccb50cb2da71865e7b573d0ce36ae307acfb1095adc2365",
        bytes: 3715,
        contexts: &["dev/1.19.2"],
    },
    RawSpec {
        path: ENTITY,
        blob: "111dfd3712b30556c60c90275a943048318ab681",
        digest: "sha256:fb01c764297dfeee1e42c3374e552bd882dbf828d1a9009afcbd0b885ad7118a",
        bytes: 11649,
        contexts: &["dev/1.19.2", "dev/1.19.4"],
    },
    RawSpec {
        path: INSERTER,
        blob: "982952e7e4ae6da37949f11a731fb3cde7e4379f",
        digest: "sha256:472872970a0613766a4542afad4d10781be4462614e5d1e711970d88f8fd41cf",
        bytes: 3833,
        contexts: &["dev/1.19.2"],
    },
    RawSpec {
        path: RENDERER,
        blob: "510cfd0476a606310a3db7408a01dcefdb99d9d8",
        digest: "sha256:462ffe6d43e6d6e6ec38d2fad45af7f56a0b66211943aadde89af8f2f90ba2a0",
        bytes: 5037,
        contexts: &["dev/1.19.4"],
    },
    RawSpec {
        path: BLOCK,
        blob: "1da41c3dff5c213ea38b89b30b19d881d13c0d28",
        digest: "sha256:39ad8d1dc85a6e3c57f4504c7cf890a646d8beb2090046541a6dddfbfe50825d",
        bytes: 7931,
        contexts: &["dev/1.19.4"],
    },
    RawSpec {
        path: SURFACE,
        blob: "270cfa9a79d7a7cd6a54896a86fe1907e52fbab9",
        digest: "sha256:d2d60be24d2c4d08de013b2d0c9c7a3a0f0de95fd86a185e4e33ad337e3fcada",
        bytes: 3708,
        contexts: &["dev/1.19.4"],
    },
    RawSpec {
        path: INSERTER,
        blob: "3cc8c4a93e61209471ce33e77aee81431bec822f",
        digest: "sha256:4b16a6a1943c508a567a55f638f036afa5e9829ed64cb98316ff9b1b29510075",
        bytes: 3839,
        contexts: &["dev/1.19.4"],
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
    approved_changes: Vec<String>,
    raw_byte_exact: bool,
}
#[derive(Facet)]
struct AuthoredFile {
    intended_core_path: String,
    stage_bytes: u64,
    stage_sha256: String,
    membership: String,
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
#[derive(Facet)]
struct RawWitness {
    git_blob: String,
    bytes: u64,
    sha256: String,
    crlf_count: usize,
    lone_cr_count: usize,
    final_lf: bool,
    bom: bool,
    contexts: Vec<String>,
}
struct Fixture {
    shared: CoreTestFixture,
    ledger: Ledger,
    sources: BTreeMap<String, Vec<u8>>,
    blobs: BTreeMap<String, Vec<u8>>,
}
impl Fixture {
    fn load() -> Result<Self> {
        let shared = CoreTestFixture::load()?;
        let bytes = read_bounded(&shared.repository.join(LEDGER), MAX_LEDGER_BYTES)?;
        let ledger: Ledger = facet_json::from_str(std::str::from_utf8(&bytes)?)
            .wrap_err("cannot parse bounded Touch Display migration evidence")?;
        let sources = PATHS
            .into_iter()
            .map(|path| Ok((path.to_owned(), shared.read_source(path)?)))
            .collect::<Result<BTreeMap<_, _>>>()?;
        let blobs = read_git_blobs(
            &shared.repository,
            &RAW_SPECS
                .into_iter()
                .map(|row| row.blob.to_owned())
                .collect(),
        )?;
        let result = Self {
            shared,
            ledger,
            sources,
            blobs,
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
            self.ledger.schema == "sfm:core-touch-display-slice@1"
                && self.ledger.context_commits == commits,
            "frozen context identity changed"
        );
        ensure!(
            self.ledger.normalization.policy == "none"
                && self.ledger.normalization.approved_changes.is_empty()
                && self.ledger.normalization.raw_byte_exact,
            "no normalization is approved"
        );
        for (name, support, requires) in [
            (
                "touch_display",
                &["1.19.2", "1.19.4"][..],
                &["packet_values", "image_resources"][..],
            ),
            (
                "touch_display_furnace_placement",
                &["1.19.2"][..],
                &["touch_display"][..],
            ),
            (
                "touch_display_seamless_surface",
                &["1.19.2"][..],
                &["touch_display"][..],
            ),
            (
                "client_frame_render",
                &["1.19.2", "1.19.4"][..],
                &["client_frame_language", "touch_display", "image_resources"][..],
            ),
            (
                "touch_display_terminal_mount",
                &["1.19.2", "1.19.4"][..],
                &[][..],
            ),
            (
                "packet_transport_private",
                &["1.19.2", "1.19.4"][..],
                &["packet_computation"][..],
            ),
            (
                "multiplayer_packets",
                &["1.19.2", "1.19.4"][..],
                &[
                    "client_inbox",
                    "client_frame_language",
                    "client_program_actions",
                ][..],
            ),
        ] {
            let owner = self
                .ledger
                .owners
                .get(name)
                .ok_or_else(|| eyre::eyre!("missing owner"))?;
            let definition = self
                .shared
                .features
                .0
                .get(name)
                .ok_or_else(|| eyre::eyre!("owner not registered"))?;
            ensure!(
                owner.support == support
                    && owner.requires == requires
                    && owner.support == definition.supported_targets
                    && owner.requires == definition.requires,
                "owner support/dependencies changed"
            );
        }
        ensure!(
            self.ledger.owners.len() == 7
                && self.ledger.files.len() == 7
                && self.ledger.raw_witnesses.len() == 11,
            "cohort scope changed"
        );
        let mut seen_files = BTreeSet::new();
        for file in &self.ledger.files {
            let path = file.intended_core_path.as_str();
            let (_, digest, bytes) = PINNED_CORE
                .into_iter()
                .find(|(p, _, _)| *p == path)
                .ok_or_else(|| eyre::eyre!("unreviewed core path"))?;
            let source = self
                .sources
                .get(path)
                .ok_or_else(|| eyre::eyre!("missing source"))?;
            let reviewed = if path == RENDERER {
                reviewed_pre_raster_closure(source)?
            } else {
                source.clone()
            };
            ensure!(
                seen_files.insert(path)
                    && file.stage_sha256 == digest
                    && file.stage_bytes == bytes
                    && reviewed.len() as u64 == bytes
                    && sha256(&reviewed) == digest,
                "promoted core golden bytes changed"
            );
            ensure!(
                file.membership
                    == if path == INSERTER {
                        "touch_display OR packet_transport_private OR multiplayer_packets"
                    } else {
                        "touch_display"
                    },
                "source owner predicate changed"
            );
            ensure!(file.witnesses.len() == 20, "missing membership witness");
            let mut seen_contexts = BTreeSet::new();
            for row in &file.witnesses {
                ensure!(
                    seen_contexts.insert(row.context.as_str())
                        && commits.contains_key(&row.context),
                    "unknown or duplicate membership context"
                );
                let spec = expected_spec(path, &row.context);
                ensure!(
                    row.present == spec.is_some()
                        && row.git_blob.as_deref() == spec.map(|s| s.blob)
                        && row.raw_sha256.as_deref() == spec.map(|s| s.digest)
                        && row.raw_bytes == spec.map_or(0, |s| s.bytes),
                    "frozen membership identity changed"
                );
            }
            ensure!(
                seen_contexts == commits.keys().map(String::as_str).collect(),
                "frozen witness coverage changed"
            );
        }
        ensure!(seen_files == BTreeSet::from(PATHS), "source cohort changed");
        let mut seen_raw = BTreeSet::new();
        for row in &self.ledger.raw_witnesses {
            let spec = RAW_SPECS
                .into_iter()
                .find(|s| s.blob == row.git_blob)
                .ok_or_else(|| eyre::eyre!("unreviewed raw witness"))?;
            let bytes = self.blob(spec.blob)?;
            ensure!(
                seen_raw.insert(spec.blob)
                    && row.sha256 == spec.digest
                    && row.bytes == spec.bytes
                    && bytes.len() as u64 == spec.bytes
                    && sha256(bytes) == spec.digest
                    && !bytes.contains(&b'\r')
                    && bytes.ends_with(b"\n")
                    && !bytes.starts_with(&[0xef, 0xbb, 0xbf])
                    && row.crlf_count == 0
                    && row.lone_cr_count == 0
                    && row.final_lf
                    && !row.bom
                    && row.contexts
                        == spec
                            .contexts
                            .iter()
                            .map(|c| (*c).to_owned())
                            .collect::<Vec<_>>(),
                "raw LF identity changed; no normalization permitted"
            );
        }
        Ok(())
    }
    fn blob(&self, id: &str) -> Result<&[u8]> {
        self.blobs
            .get(id)
            .map(Vec::as_slice)
            .ok_or_else(|| eyre::eyre!("missing raw blob"))
    }
    fn raw(&self, path: &str, target: &str) -> Result<&str> {
        let spec = expected_spec(path, &format!("dev/{target}"))
            .ok_or_else(|| eyre::eyre!("unwitnessed target"))?;
        Ok(std::str::from_utf8(self.blob(spec.blob)?)?)
    }
    /// Explicit test-only closure of requested source owners. The unchanged
    /// production fixture then validates that exact enabled set. No wire
    /// families are fabricated and no complete-project validation is bypassed.
    fn context(&self, target: &str, requested: &[&str]) -> Result<ProjectionContext> {
        ensure!(
            self.shared.features.0.len() <= 256,
            "feature graph exceeds bound"
        );
        let mut selected = requested
            .iter()
            .map(|name| (*name).to_owned())
            .collect::<BTreeSet<_>>();
        let mut pending = selected.iter().cloned().collect::<Vec<_>>();
        while let Some(name) = pending.pop() {
            let definition = self
                .shared
                .features
                .0
                .get(&name)
                .ok_or_else(|| eyre::eyre!("unknown requested source owner"))?;
            for required in &definition.requires {
                if selected.insert(required.clone()) {
                    pending.push(required.clone());
                }
            }
        }
        self.shared.context(
            target,
            &selected.iter().map(String::as_str).collect::<Vec<_>>(),
        )
    }
    fn selection(&self, context: &ProjectionContext) -> Result<BTreeSet<String>> {
        let selection = select_core_inputs(&self.shared.metadata, context, &inventory())?;
        let mut selected = BTreeSet::new();
        for path in PATHS {
            if let Some(input) = selection.inputs.get(path) {
                ensure!(
                    input.input == path
                        && input.template
                        && !selection.omitted_paths.contains(path),
                    "selected class is not a core template"
                );
                selected.insert(path.to_owned());
            } else {
                ensure!(
                    selection.omitted_paths.contains(path),
                    "missing explicit owner omission"
                );
            }
        }
        Ok(selected)
    }
    fn render(&self, context: &ProjectionContext) -> Result<BTreeMap<String, String>> {
        let selected = self.selection(context)?;
        selected
            .into_iter()
            .map(|path| {
                Ok((
                    path.clone(),
                    render_java_source(std::str::from_utf8(&self.sources[&path])?, context)?,
                ))
            })
            .collect()
    }
}
fn inventory() -> BTreeSet<String> {
    PATHS.into_iter().map(str::to_owned).collect()
}
fn expected_spec(path: &str, name: &str) -> Option<RawSpec> {
    RAW_SPECS
        .into_iter()
        .find(|row| row.path == path && row.contexts.contains(&name))
}
fn target_id(context: &ProjectionContext) -> Result<&str> {
    SUPPORTED_TARGETS
        .into_iter()
        .find(|(_, mc)| *mc == context.minecraft_version)
        .map(|(id, _)| id)
        .ok_or_else(|| eyre::eyre!("unknown target"))
}
fn enabled(context: &ProjectionContext, name: &str) -> Result<bool> {
    context
        .features
        .get(name)
        .copied()
        .ok_or_else(|| eyre::eyre!("unknown owner boolean"))
}
fn once(source: &str, anchor: &str, replacement: &str) -> Result<String> {
    ensure!(
        source.matches(anchor).count() == 1,
        "reviewed anchor not unique"
    );
    Ok(source.replacen(anchor, replacement, 1))
}
/// Raw-member oracle; no Liquid/source-template interpretation is performed.
fn owner_oracle(fixture: &Fixture, path: &str, context: &ProjectionContext) -> Result<String> {
    let target = target_id(context)?;
    let mut source = fixture.raw(path, target)?.to_owned();
    match path {
        BLOCK => {
            if enabled(context, "touch_display_furnace_placement")? {
                source = fixture.raw(path, "1.19.2")?.to_owned();
            } else {
                source = fixture.raw(path, "1.19.4")?.to_owned();
            }
        }
        SURFACE => {
            source = fixture
                .raw(
                    path,
                    if enabled(context, "touch_display_seamless_surface")? {
                        "1.19.2"
                    } else {
                        "1.19.4"
                    },
                )?
                .to_owned();
        }
        RENDERER => {
            let frame = enabled(context, "client_frame_render")?;
            let raster = enabled(context, "touch_display_terminal_mount")?
                && enabled(context, "client_frame_language")?;
            if !frame {
                source = once(
                    &source,
                    "import ca.teamdman.sfm.client.program.ClientManagerFrameRuntime;\n",
                    "",
                )?;
            }
            if !raster {
                source = once(
                    &source,
                    "import ca.teamdman.sfm.client.raster.TouchDisplayRasterRuntime;\n",
                    "",
                )?;
            }
            let original = "        var programTexture = ClientManagerFrameRuntime.textureFor(blockEntity);\n        var imageLocation = TouchDisplayRasterRuntime.textureFor(blockEntity).or(() -> programTexture).orElseGet(() ->\n";
            let prefix = match (frame, raster) {
                (true, true) => original,
                (true, false) => {
                    "        var programTexture = ClientManagerFrameRuntime.textureFor(blockEntity);\n        var imageLocation = programTexture.orElseGet(() ->\n"
                }
                (false, true) => {
                    "        var imageLocation = TouchDisplayRasterRuntime.textureFor(blockEntity).orElseGet(() ->\n"
                }
                (false, false) => "        var imageLocation = (\n",
            };
            source = once(&source, original, prefix)?;
        }
        _ => {}
    }
    Ok(source)
}

#[test]
fn all_140_touch_display_memberships_and_14_raw_witnesses_match_real_selection_and_renderer()
-> Result<()> {
    let fixture = Fixture::load()?;
    let mut present = 0;
    let mut absent = 0;
    for (name, _) in PINNED_CONTEXTS {
        let (_, target) = name
            .split_once('/')
            .ok_or_else(|| eyre::eyre!("invalid context"))?;
        let historical_dev = matches!(name, "dev/1.19.2" | "dev/1.19.4");
        let mut requested = Vec::new();
        if historical_dev {
            requested.extend([
                "touch_display",
                "client_frame_render",
                "touch_display_terminal_mount",
            ]);
            if target == "1.19.2" {
                requested.extend(&D1_FLAGS[..2]);
            }
        }
        let context = fixture.context(target, &requested)?;
        let actual = fixture.render(&context)?;
        for path in PATHS {
            if historical_dev {
                assert_eq!(actual[path], fixture.raw(path, target)?, "{name}: {path}");
                present += 1;
            } else {
                assert!(!actual.contains_key(path), "{name}: {path}");
                absent += 1;
            }
        }
    }
    assert_eq!((present, absent), (14, 126));
    Ok(())
}

#[test]
fn placement_geometry_and_both_optional_texture_producers_have_independent_source_masks()
-> Result<()> {
    let fixture = Fixture::load()?;
    let mut profiles = 0;
    let mut cells = 0;
    for target in ["1.19.2", "1.19.4"] {
        let flags: &[&str] = if target == "1.19.2" {
            &D1_FLAGS
        } else {
            &D1_FLAGS[2..]
        };
        for mask in 0..(1 << flags.len()) {
            let mut requested = vec!["touch_display"];
            requested.extend(
                flags
                    .iter()
                    .enumerate()
                    .filter_map(|(index, flag)| (mask & (1 << index) != 0).then_some(*flag)),
            );
            let context = fixture.context(target, &requested)?;
            let actual = fixture.render(&context)?;
            for path in PATHS {
                assert_eq!(
                    actual[path],
                    owner_oracle(&fixture, path, &context)?,
                    "{target}: mask {mask}: {path}"
                );
                cells += 1;
            }
            assert_eq!(
                actual[BLOCK].contains("context.getHorizontalDirection().getOpposite()"),
                enabled(&context, "touch_display_furnace_placement")?
            );
            assert_eq!(
                actual[SURFACE].contains("HALF_IMAGE_SIZE = 8F / 16F"),
                enabled(&context, "touch_display_seamless_surface")?
            );
            assert_eq!(
                actual[RENDERER].contains("ClientManagerFrameRuntime.textureFor"),
                enabled(&context, "client_frame_render")?
            );
            assert_eq!(
                actual[RENDERER].contains("TouchDisplayRasterRuntime.textureFor"),
                enabled(&context, "touch_display_terminal_mount")?
                    && enabled(&context, "client_frame_language")?
            );
            if enabled(&context, "client_frame_render")?
                && enabled(&context, "touch_display_terminal_mount")?
            {
                assert!(actual[RENDERER].contains(
                    "TouchDisplayRasterRuntime.textureFor(blockEntity).or(() -> programTexture)"
                ));
            }
            profiles += 1;
        }
    }
    assert_eq!((profiles, cells), (20, 140));
    // The two-owner raster condition is independent of client-frame rendering.
    for target in ["1.19.2", "1.19.4"] {
        for mask in 0..4 {
            let mut requested = vec!["touch_display"];
            if mask & 1 != 0 {
                requested.push("touch_display_terminal_mount");
            }
            if mask & 2 != 0 {
                requested.push("client_frame_language");
            }
            let context = fixture.context(target, &requested)?;
            let output = fixture.render(&context)?;
            assert_eq!(
                output[RENDERER],
                owner_oracle(&fixture, RENDERER, &context)?
            );
            assert_eq!(
                output[RENDERER]
                    .contains("import ca.teamdman.sfm.client.raster.TouchDisplayRasterRuntime;"),
                mask == 3
            );
            assert_eq!(
                output[RENDERER].contains("TouchDisplayRasterRuntime.textureFor"),
                mask == 3
            );
            assert!(!context.features["client_frame_render"]);
            assert!(!output[RENDERER].contains("ClientManagerFrameRuntime"));
        }
    }
    Ok(())
}

#[test]
fn minimal_static_display_needs_no_optional_mod_filtering_frame_terminal_gui_or_wire_owners()
-> Result<()> {
    let fixture = Fixture::load()?;
    for target in ["1.19.2", "1.19.4"] {
        let context = fixture.context(target, &["touch_display"])?;
        let actual = fixture.render(&context)?;
        assert_eq!(
            actual.keys().map(String::as_str).collect::<BTreeSet<_>>(),
            BTreeSet::from(PATHS)
        );
        assert_eq!(
            context
                .features
                .iter()
                .filter_map(|(flag, on)| on.then_some(flag.as_str()))
                .collect::<BTreeSet<_>>(),
            BTreeSet::from(["touch_display", "image_resources", "packet_values"])
        );
        for owner in [
            "client_frame_render",
            "client_frame_language",
            "touch_display_terminal_mount",
            "client_manager",
            "client_manager_gui",
            "packet_transport_private",
            "multiplayer_packets",
            "terminal_remote",
            "terminal_vox",
            "mod_event_filtering",
        ] {
            assert_eq!(context.features.get(owner), Some(&false), "{owner}");
        }
        assert!(
            !actual[RENDERER].contains("ClientManagerFrameRuntime")
                && !actual[RENDERER].contains("TouchDisplayRasterRuntime")
        );
        assert!(actual[RENDERER].contains("TouchDisplayTextureRuntime.textureFor"));
        assert!(
            actual[BLOCK].contains("SFMPacketInventoryInserter.insert")
                && actual[ENTITY].contains("SFMTouchValue.requireCommitEnvelopeFits")
        );
        // Annotation discovery is baseline; requiredModId filtering stays off.
        let explicit_minimal = fixture.shared.context(
            target,
            &["touch_display", "image_resources", "packet_values"],
        )?;
        assert_eq!(
            explicit_minimal.features.get("mod_event_filtering"),
            Some(&false)
        );
        assert_eq!(fixture.render(&explicit_minimal)?, actual);
        assert!(
            fixture
                .shared
                .context(target, &["touch_display", "packet_values"])
                .is_err()
        );
        assert!(
            fixture
                .selection(&fixture.context(target, &["packet_values"])?)?
                .is_empty()
        );
        for fixes in [
            &["touch_display_furnace_placement"][..],
            &["touch_display_seamless_surface"][..],
        ] {
            assert!(fixture.shared.context(target, fixes).is_err());
        }
    }
    for (target, _) in SUPPORTED_TARGETS {
        if !matches!(target, "1.19.2" | "1.19.4") {
            assert!(
                fixture.context(target, &["touch_display"]).is_err(),
                "{target}"
            );
        }
    }
    for flag in &D1_FLAGS[..2] {
        assert!(fixture.context("1.19.4", &[*flag]).is_err(), "{flag}");
    }
    Ok(())
}

#[test]
fn local_inserter_union_keeps_other_display_classes_omitted_for_actual_transport_consumers()
-> Result<()> {
    let fixture = Fixture::load()?;
    for target in ["1.19.2", "1.19.4"] {
        for owner in ["packet_transport_private", "multiplayer_packets"] {
            let context = fixture.context(target, &[owner])?;
            assert_eq!(context.features.get("touch_display"), Some(&false));
            let actual = fixture.render(&context)?;
            assert_eq!(
                actual.keys().map(String::as_str).collect::<BTreeSet<_>>(),
                BTreeSet::from([INSERTER])
            );
            assert_eq!(actual[INSERTER], fixture.raw(INSERTER, target)?);
            assert!(
                actual[INSERTER].contains("if (!server.isSameThread())")
                    && actual[INSERTER].contains("if (!level.isLoaded(target.position()))")
                    && actual[INSERTER]
                        .contains("return lookup.apply(requestedSide.orElse(null));")
            );
        }
    }
    Ok(())
}
fn reviewed_pre_raster_closure(source: &[u8]) -> Result<Vec<u8>> {
    ensure!(
        source.len() == 6083
            && sha256(source)
                == "sha256:2e8b07b8a86fb980c0b717be828baa35a3428986952059d48558d31a69d075c1",
        "current renderer identity changed"
    );
    let text = std::str::from_utf8(source)?;
    let current =
        "{% if features.touch_display_terminal_mount and features.client_frame_language %}";
    ensure!(
        text.matches(current).count() == 2,
        "exact raster import/call pair required"
    );
    let original = text
        .replace(current, "{% if features.touch_display_terminal_mount %}")
        .into_bytes();
    ensure!(
        original.len() == 6013
            && sha256(&original)
                == "sha256:8a454db0e0fb22a756cd9d6d9bce0a6a21af52b43995ad96ce9c4499d86d0f07",
        "original renderer pin not recovered"
    );
    Ok(original)
}

#[test]
fn witnessed_loader_matrix_and_static_budget_lifecycle_anchors_are_retained_without_rewrites()
-> Result<()> {
    let fixture = Fixture::load()?;
    for target in ["1.19.2", "1.19.4"] {
        let actual = fixture.render(&fixture.context(target, &["touch_display"])?)?;
        assert_eq!(
            actual[RENDERER].contains("import com.mojang.math.Matrix4f;"),
            target == "1.19.2"
        );
        assert_eq!(
            actual[RENDERER].contains("        var matrix = pose.pose();"),
            target == "1.19.4"
        );
        assert_eq!(
            actual[INSERTER].contains("Registry.DIMENSION_REGISTRY"),
            target == "1.19.2"
        );
        assert_eq!(
            actual[INSERTER].contains("Registries.DIMENSION"),
            target == "1.19.4"
        );
        for anchor in [
            "MAX_TEXTURES = 16;",
            "MAX_GPU_BYTES = 16L * 1024 * 1024;",
            "if (failedDigests.size() >= MAX_TEXTURES)",
            "pressure = true;",
            "void maintain()",
            "textures.release(oldest.location());",
            "void clear()",
        ] {
            assert!(actual[CACHE].contains(anchor), "{anchor}");
        }
        for anchor in [
            "@SFMSubscribeEvent(SFMDist.CLIENT)",
            "onClientTick",
            "onLevelUnload",
            "onRegisterReloadListener",
            "MemoryUtil.memFree(encoded)",
            "if (!registered && texture != null) texture.close();",
        ] {
            assert!(actual[RUNTIME].contains(anchor), "{anchor}");
        }
        for anchor in [
            "private volatile DisplayContent content",
            "if (!simulate) commitContent(image);",
            "if (previous.revision() == Long.MAX_VALUE)",
            "ClientboundBlockEntityDataPacket.create(this)",
            "imageSinkCapability.invalidate();",
        ] {
            assert!(actual[ENTITY].contains(anchor), "{anchor}");
        }
    }
    Ok(())
}

#[test]
fn common_touch_display_body_edits_reach_both_supported_api_eras_with_unchanged_guards()
-> Result<()> {
    let fixture = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    let mut cells = 0;
    for path in PATHS {
        let source = std::str::from_utf8(&fixture.sources[path])?;
        let name = path
            .rsplit('/')
            .next()
            .and_then(|p| p.strip_suffix(".java"))
            .ok_or_else(|| eyre::eyre!("invalid class"))?;
        let line = source
            .lines()
            .find(|line| line.contains(&format!("class {name} ")) && line.ends_with('{'))
            .ok_or_else(|| eyre::eyre!("missing common class-body anchor"))?;
        let anchor = format!("{line}\n");
        let replacement = format!("{anchor}    // Common Touch Display source edit proof.\n");
        let edited = once(source, &anchor, &replacement)?;
        assert_eq!(
            source
                .lines()
                .filter(|l| l.trim().starts_with("{%"))
                .collect::<Vec<_>>(),
            edited
                .lines()
                .filter(|l| l.trim().starts_with("{%"))
                .collect::<Vec<_>>()
        );
        let file = root.join(path);
        fs::create_dir_all(
            file.parent()
                .ok_or_else(|| eyre::eyre!("missing fixture parent"))?,
        )?;
        fs::write(&file, edited.as_bytes())?;
        for target in ["1.19.2", "1.19.4"] {
            let context = fixture.context(target, &["touch_display"])?;
            let selection = select_core_inputs(&fixture.shared.metadata, &context, &inventory())?;
            let input = selection
                .inputs
                .get(path)
                .ok_or_else(|| eyre::eyre!("missing selected source"))?;
            assert_eq!(input.input, path);
            let bytes = read_bounded(&root.join(&input.input), MAX_SOURCE_BYTES)?;
            let actual = render_java_source(std::str::from_utf8(&bytes)?, &context)?;
            assert_eq!(
                actual,
                once(
                    &owner_oracle(&fixture, path, &context)?,
                    &anchor,
                    &replacement
                )?
            );
            cells += 1;
        }
        assert_eq!(fixture.shared.read_source(path)?, fixture.sources[path]);
    }
    assert_eq!(cells, 14);
    Ok(())
}

#[test]
fn all_140_offline_git_membership_cells_keep_raw_ids_and_true_absence() -> Result<()> {
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
        for path in PATHS {
            command.arg(format!("platform/minecraft/{path}"));
        }
        let mut child = command
            .stdout(Stdio::piped())
            .stderr(Stdio::null())
            .spawn()?;
        let mut reader = child
            .stdout
            .take()
            .ok_or_else(|| eyre::eyre!("missing tree output"))?;
        let mut bytes = Vec::new();
        let read = reader
            .by_ref()
            .take(MAX_TREE_BYTES + 1)
            .read_to_end(&mut bytes);
        drop(reader);
        if read.is_err() || bytes.len() as u64 > MAX_TREE_BYTES {
            let _ = child.kill();
            let _ = child.wait();
            read?;
            eyre::bail!("offline membership output exceeds bound");
        }
        ensure!(child.wait()?.success(), "offline frozen tree failed");
        let mut actual = BTreeMap::new();
        if !bytes.is_empty() {
            ensure!(bytes.last() == Some(&0), "missing tree terminator");
            for row in bytes.split(|byte| *byte == 0).filter(|r| !r.is_empty()) {
                let text = std::str::from_utf8(row)?;
                let (head, path) = text
                    .split_once('\t')
                    .ok_or_else(|| eyre::eyre!("malformed tree"))?;
                let fields = head.split_whitespace().collect::<Vec<_>>();
                ensure!(
                    fields.len() == 3 && fields[0] == "100644" && fields[1] == "blob",
                    "unexpected source mode/type"
                );
                ensure!(
                    actual
                        .insert(path.to_owned(), fields[2].to_owned())
                        .is_none(),
                    "duplicate tree source"
                );
            }
        }
        for path in PATHS {
            let expected = expected_spec(path, name);
            assert_eq!(
                actual
                    .get(&format!("platform/minecraft/{path}"))
                    .map(String::as_str),
                expected.map(|s| s.blob)
            );
            if expected.is_some() {
                present += 1;
            } else {
                absent += 1;
            }
        }
        assert_eq!(
            actual.len(),
            if name.starts_with("dev/1.19.") { 7 } else { 0 }
        );
    }
    assert_eq!((present, absent), (14, 126));
    Ok(())
}
