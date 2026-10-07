//! Windows-only palette/history provider preservation regressions.
//! Actual selector/scanner/renderer/collector cases do not prove Java compilation or runtime.
//! The palette screen, PanelActionSupport and callback/member closure remain explicit gates.
#![cfg(all(test, windows))]

use super::candidate_lock::checked_file;
use super::context::ProjectionContext;
use super::core_catalog::CoreCatalog;
use super::core_inputs::CORE_ROOT;
use super::core_inputs::collect_core_artifacts;
use super::core_inputs::discover_core_source_files;
use super::core_inputs::select_core_inputs;
use super::core_slice_test_support::CoreTestFixture;
use super::core_slice_test_support::historical_feature_registry;
use super::core_slice_test_support::read_bounded;
use super::core_slice_test_support::read_wave_git_blobs;
use super::core_slice_test_support::read_wave_git_tree;
use super::provenance::sha256;
use super::render_java_source;
use eyre::Result;
use eyre::ensure;
use facet::Facet;
use std::collections::BTreeMap;
use std::collections::BTreeSet;

const LEDGER: &str = "docs/tasks/sfm-core-palette-history-provider-wave.json";
const TARGETS: [&str; 10] = [
    "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
    "26.1.2",
];
const KEYBOARD: [&str; 2] = ["client_actions", "keyboard_profiles"];
const TYPED: [&str; 5] = [
    "client_actions",
    "keyboard_profiles",
    "client_theme",
    "command_palette",
    "typed_command_palette",
];
const ON: [&str; 6] = [
    "client_actions",
    "keyboard_profiles",
    "client_theme",
    "command_palette",
    "typed_command_palette",
    "command_history",
];
const PATHS: [&str; 13] = [
    "src/main/java/ca/teamdman/sfm/client/action/SFMClientCommandInsertion.java",
    "src/main/java/ca/teamdman/sfm/client/command/SFMCommandHistoryService.java",
    "src/main/java/ca/teamdman/sfm/client/action/SFMClientActionArgumentHistory.java",
    "src/main/java/ca/teamdman/sfm/client/action/SFMClientActionInvocationTrace.java",
    "src/main/java/ca/teamdman/sfm/client/action/SFMPaletteCandidateInspection.java",
    "src/main/java/ca/teamdman/sfm/client/action/SFMPaletteCandidateCopyAction.java",
    "src/main/java/ca/teamdman/sfm/client/action/SFMPaletteCandidateSetCopyAction.java",
    "src/main/java/ca/teamdman/sfm/client/action/PaletteHistoryClearAction.java",
    "src/main/java/ca/teamdman/sfm/client/action/PaletteHistoryPersistenceAction.java",
    "src/main/java/ca/teamdman/sfm/client/command/SFMCommandHistory.java",
    "src/main/java/ca/teamdman/sfm/client/command/SFMCommandHistoryCodec.java",
    "src/main/java/ca/teamdman/sfm/client/screen/SFMTransientActionScreen.java",
    "src/main/java/ca/teamdman/sfm/client/action/ClosePaletteAction.java",
];
#[derive(Clone, Copy)]
struct Source {
    path: &'static str,
    owner: &'static str,
    d2: bool,
    bytes: usize,
    digest: &'static str,
}
const SOURCES: [Source; 13] = [
    Source {
        path: PATHS[0],
        owner: "keyboard_profiles",
        d2: false,
        bytes: 3257,
        digest: "27c16ccce10ce7ea697a67b061c1124bc12ad732709aca6e7163b1b401fe4a75",
    },
    Source {
        path: PATHS[1],
        owner: "command_history",
        d2: true,
        bytes: 7519,
        digest: "3f66cf1249158499598de084c2eb80498a90593dc8edd5d1482c7b560116f33e",
    },
    Source {
        path: PATHS[2],
        owner: "command_history",
        d2: true,
        bytes: 6736,
        digest: "9114d531860d8609b4fe35434979f3d3dd63c09e6d235e118f17508d0f0f2dfc",
    },
    Source {
        path: PATHS[3],
        owner: "keyboard_profiles",
        d2: true,
        bytes: 3379,
        digest: "28f24020f132d01cbdf8472ffae9e9a7287ccc06c324bf4ae8afb0b4fa220f7d",
    },
    Source {
        path: PATHS[4],
        owner: "typed_command_palette",
        d2: true,
        bytes: 7996,
        digest: "9e6ffa501059a523cef620e57b5e2a453d6d6d1821e1c622d0d29bd70bb89094",
    },
    Source {
        path: PATHS[5],
        owner: "typed_command_palette",
        d2: true,
        bytes: 6645,
        digest: "a618e36d63e8e86fade137ce4a65462bbad0e961fb7a8963158e74b12917e93e",
    },
    Source {
        path: PATHS[6],
        owner: "typed_command_palette",
        d2: true,
        bytes: 6730,
        digest: "2b7a10b6410ad7393932d06ec1907c9b75680f24998f482e389a4e6d24aaa864",
    },
    Source {
        path: PATHS[7],
        owner: "command_history",
        d2: true,
        bytes: 992,
        digest: "9234d1284118a72f296e843150b2ae7dc7a14d2a9693850349f3142a7b612030",
    },
    Source {
        path: PATHS[8],
        owner: "command_history",
        d2: true,
        bytes: 2529,
        digest: "a3716dfc2452c491aca954f7c6b8db9896b6e666cb4bdce0a1f90ff8a36ce4c2",
    },
    Source {
        path: PATHS[9],
        owner: "command_history",
        d2: true,
        bytes: 3894,
        digest: "39a798435676fc46ae2ec9f4a34038daf66afb09fca9b0e76585cba55976d4c3",
    },
    Source {
        path: PATHS[10],
        owner: "command_history",
        d2: true,
        bytes: 3093,
        digest: "7a167ca697bb6bd6173b377f768a10c0afa3d02b79e42220c54beec460be97eb",
    },
    Source {
        path: PATHS[11],
        owner: "typed_command_palette",
        d2: true,
        bytes: 206,
        digest: "6041c81926fb1c85509870cec1df0bb883e2765778bdc21c9bb5ac2f321016b3",
    },
    Source {
        path: PATHS[12],
        owner: "typed_command_palette",
        d2: true,
        bytes: 1300,
        digest: "9a53d4a7e8ddd04e15edf6c22263b25a1680a29c8351cbe56d3855adfe254a36",
    },
];
#[derive(Clone, Copy)]
struct Raw {
    oid: &'static str,
    bytes: usize,
    digest: &'static str,
}
const RAW: [Raw; 15] = [
    Raw {
        oid: "8a2665cb80e5851db9d8caf5f8dad27b02d718e5",
        bytes: 2896,
        digest: "49107b922b57ab8c6a4005ea96e561893869b0a2d1f77d2c0918b1a80f7c4ea2",
    },
    Raw {
        oid: "0090f839d3cfeafeb9a6f5d7b369f86c499fb3db",
        bytes: 2163,
        digest: "fb3e73a7b1d89332a85c1d36fbb8040d8bf85132312a2186cfe8f634c4c2b035",
    },
    Raw {
        oid: "57e957303154db2ccb0cd257363d5f5a306236f2",
        bytes: 2151,
        digest: "097344ddc54977790773bfa262c1e4ede33399bb9fe2eb5db8cd02b7948d82fd",
    },
    Raw {
        oid: "c558061b6e46b81f699c60527cfe26695198e053",
        bytes: 7519,
        digest: "3f66cf1249158499598de084c2eb80498a90593dc8edd5d1482c7b560116f33e",
    },
    Raw {
        oid: "cd1dba30f6974c912c60c72851ce2954aeb6cb97",
        bytes: 6736,
        digest: "9114d531860d8609b4fe35434979f3d3dd63c09e6d235e118f17508d0f0f2dfc",
    },
    Raw {
        oid: "8a5a413ca781030608593b6c8e505891abdff5d0",
        bytes: 3379,
        digest: "28f24020f132d01cbdf8472ffae9e9a7287ccc06c324bf4ae8afb0b4fa220f7d",
    },
    Raw {
        oid: "6b12b7ba6f95ebb726ee528d85e19ecd53b1549b",
        bytes: 7996,
        digest: "9e6ffa501059a523cef620e57b5e2a453d6d6d1821e1c622d0d29bd70bb89094",
    },
    Raw {
        oid: "d3d9392be09c13f784294e2d82bd1e8cc82afb24",
        bytes: 6645,
        digest: "a618e36d63e8e86fade137ce4a65462bbad0e961fb7a8963158e74b12917e93e",
    },
    Raw {
        oid: "5c59deb5f45ca741393f2ab1904133842ac11306",
        bytes: 6730,
        digest: "2b7a10b6410ad7393932d06ec1907c9b75680f24998f482e389a4e6d24aaa864",
    },
    Raw {
        oid: "0fd30f9f5234f33a09b0552d337cf9185ac94b6a",
        bytes: 992,
        digest: "9234d1284118a72f296e843150b2ae7dc7a14d2a9693850349f3142a7b612030",
    },
    Raw {
        oid: "e084135763817e65fdf09f79fa9c39de1bf6035e",
        bytes: 2529,
        digest: "a3716dfc2452c491aca954f7c6b8db9896b6e666cb4bdce0a1f90ff8a36ce4c2",
    },
    Raw {
        oid: "9fb213b027d928597e254b27cd89e68949e0946b",
        bytes: 3894,
        digest: "39a798435676fc46ae2ec9f4a34038daf66afb09fca9b0e76585cba55976d4c3",
    },
    Raw {
        oid: "ebd8f404ca087835a7a3f6285f99591f83d31668",
        bytes: 3093,
        digest: "7a167ca697bb6bd6173b377f768a10c0afa3d02b79e42220c54beec460be97eb",
    },
    Raw {
        oid: "27e4d66f79605a201831e3280f7ac5b718229bf8",
        bytes: 206,
        digest: "6041c81926fb1c85509870cec1df0bb883e2765778bdc21c9bb5ac2f321016b3",
    },
    Raw {
        oid: "3ea5a31009d5309f676e8b2a427f723614f4e790",
        bytes: 1300,
        digest: "9a53d4a7e8ddd04e15edf6c22263b25a1680a29c8351cbe56d3855adfe254a36",
    },
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
#[derive(Facet)]
#[facet(deny_unknown_fields)]
struct Ledger {
    schema: String,
    date: String,
    status: String,
    context_commits: BTreeMap<String, String>,
    normalization: String,
    features_origin: String,
    files: Vec<Leaf>,
    raw_bodies: Vec<RawEvidence>,
    tree_inputs: Vec<TreeInput>,
    counts: Counts,
    source_preparation_limits: SourceLimits,
}
#[derive(Facet)]
#[facet(deny_unknown_fields)]
struct Leaf {
    path: String,
    core_path: String,
    template_bytes: usize,
    template_sha256: String,
    membership: Membership,
    witnesses: Vec<Witness>,
}
#[derive(Facet)]
#[facet(deny_unknown_fields)]
struct Membership {
    targets: Vec<String>,
    all_features: Vec<String>,
    any_features: Vec<String>,
    none_features: Vec<String>,
}
#[derive(Facet)]
#[facet(deny_unknown_fields)]
struct Witness {
    context: String,
    source_commit: String,
    present: bool,
    raw_blob: Option<String>,
    mode: Option<String>,
    explicit_registered_features: Vec<String>,
}
#[derive(Facet)]
#[facet(deny_unknown_fields)]
struct RawEvidence {
    blob: String,
    bytes: usize,
    sha256: String,
    raw_role: String,
    scope: String,
}
#[derive(Facet)]
#[facet(deny_unknown_fields)]
struct TreeInput {
    context: String,
    commit: String,
    raw_role: String,
    bytes: usize,
    sha256: String,
}
#[derive(Facet)]
#[facet(deny_unknown_fields)]
struct Counts {
    paths: usize,
    cells: usize,
    present: usize,
    absent: usize,
    raw_blobs: usize,
    raw_bytes: usize,
}
#[derive(Facet)]
#[facet(deny_unknown_fields)]
struct SourceLimits {
    renderer_executed: bool,
    tests_executed: bool,
    java_executed: bool,
    whole_family_closure: bool,
}

struct Fixture {
    core: CoreTestFixture,
    inventory: BTreeSet<String>,
    raw: BTreeMap<String, Vec<u8>>,
}
fn d2(target: &str) -> bool {
    matches!(target, "1.19.2" | "1.19.4")
}
fn same(actual: &[String], expected: &[&str]) -> bool {
    actual.len() == expected.len()
        && actual.iter().map(String::as_str).collect::<BTreeSet<_>>()
            == expected.iter().copied().collect()
}
fn validate_body(body: &[u8], bytes: usize, digest: &str) -> Result<()> {
    ensure!(
        body.len() == bytes && sha256(body) == format!("sha256:{digest}"),
        "pinned source identity changed"
    );
    ensure!(
        !body.contains(&b'\r')
            && body.ends_with(b"\n")
            && !body.ends_with(b"\n\n")
            && !body.starts_with(&[0xef, 0xbb, 0xbf]),
        "source framing changed"
    );
    std::str::from_utf8(body)?;
    Ok(())
}
fn raw_oid(index: usize, target: &str, _context: &ProjectionContext) -> &'static str {
    if index == 0 {
        if d2(target) {
            RAW[0].oid
        } else if target == "26.1.2" {
            RAW[2].oid
        } else {
            RAW[1].oid
        }
    } else {
        // RAW contains the insertion's three historical bodies followed by
        // each remaining source in the fixed PATHS order.
        RAW[index + 2].oid
    }
}

fn validate_current_source(source: Source, current: &[u8]) -> Result<()> {
    if source.path != PATHS[0] {
        return validate_body(current, source.bytes, source.digest);
    }
    ensure!(
        current.len() == 3278
            && sha256(current)
                == "sha256:dd9d8cfcf90e9ef0ab2502e089bcbd51d96a263a48f174f8e77004cb0ad1bde7",
        "current neutral caret source changed outside reviewed version boundary"
    );
    let text = std::str::from_utf8(current)?;
    let start = "{% case minecraft_version %}\n{% when \"1.19.2\", \"1.19.4\" %}\n\n    /**";
    let end = "{% endcase %}\n}\n";
    ensure!(
        text.matches(start).count() == 1 && text.matches(end).count() == 1,
        "reviewed caret method version boundary is not unique"
    );
    let original = text
        .replacen(
            start,
            "{% if features.typed_command_palette %}\n\n    /**",
            1,
        )
        .replacen(end, "{% endif %}\n}\n", 1);
    validate_body(original.as_bytes(), source.bytes, source.digest)
}
impl Fixture {
    fn load() -> Result<Self> {
        let core = CoreTestFixture::load()?;
        let (definitions, historical_features) = historical_feature_registry()?;
        validate_body(
            definitions,
            30254,
            "38d9a82444b8478e80018a51ebcbd99a959788f3ccf38237d4175455be810b83",
        )?;
        ensure!(
            historical_features.0.len() == 180,
            "historical owner boundary changed"
        );
        for (name, targets, requires) in [
            ("keyboard_profiles", &TARGETS[..], &["client_actions"][..]),
            (
                "command_palette",
                &TARGETS[..],
                &["client_actions", "client_theme", "keyboard_profiles"][..],
            ),
            (
                "typed_command_palette",
                &TARGETS[..2],
                &["command_palette"][..],
            ),
            (
                "command_history",
                &TARGETS[..2],
                &["typed_command_palette"][..],
            ),
        ] {
            let owner = &core.features.0[name];
            ensure!(
                same(&owner.supported_targets, targets) && same(&owner.requires, requires),
                "existing provider owner contract changed"
            );
        }
        let ledger_bytes = read_bounded(&checked_file(&core.repository, LEDGER)?, 1024 * 1024)?;
        validate_body(
            &ledger_bytes,
            62536,
            "1b3ae20f18ebcfa3cf531a4d7037296dc81d50f9315f39aca8e6ee1c32fadb88",
        )?;
        let ledger: Ledger = facet_json::from_str(std::str::from_utf8(&ledger_bytes)?)?;
        ensure!(
            ledger.schema == "sfm:core_palette_history_provider_wave@1"
                && ledger.date == "2026-10-02"
                && ledger.status
                    == "prepared_source_only_not_family_compile_runtime_or_promotion_acceptance"
                && ledger.context_commits
                    == COMMITS
                        .into_iter()
                        .map(|(c, h)| (c.to_owned(), h.to_owned()))
                        .collect()
                && ledger.normalization == "none_exact_raw_lf_with_terminal_lf"
                && ledger.features_origin
                    == "reviewed_current_explicit_reconstruction_not_historical_manifest"
                && ledger.files.len() == 13
                && ledger.raw_bodies.len() == 15
                && ledger.tree_inputs.len() == 20
                && ledger.counts.paths == 13
                && ledger.counts.cells == 260
                && ledger.counts.present == 34
                && ledger.counts.absent == 226
                && ledger.counts.raw_blobs == 15
                && ledger.counts.raw_bytes == 58229
                && !ledger.source_preparation_limits.renderer_executed
                && !ledger.source_preparation_limits.tests_executed
                && !ledger.source_preparation_limits.java_executed
                && !ledger.source_preparation_limits.whole_family_closure,
            "immutable preparation/witness contract changed"
        );
        let mut seen = BTreeSet::new();
        for leaf in &ledger.files {
            let source = SOURCES
                .iter()
                .find(|g| g.path == leaf.path)
                .ok_or_else(|| eyre::eyre!("unexpected provider source"))?;
            ensure!(
                seen.insert(leaf.path.clone())
                    && leaf.core_path == format!("{CORE_ROOT}/{}", leaf.path)
                    && leaf.template_bytes == source.bytes
                    && leaf.template_sha256 == source.digest
                    && same(
                        &leaf.membership.targets,
                        if source.d2 {
                            &TARGETS[..2]
                        } else {
                            &TARGETS[..]
                        }
                    )
                    && same(&leaf.membership.all_features, &[source.owner])
                    && leaf.membership.any_features.is_empty()
                    && leaf.membership.none_features.is_empty(),
                "provider membership contract changed"
            );
            let rules = core
                .metadata
                .source_rules
                .get(source.path)
                .ok_or_else(|| eyre::eyre!("provider rule is absent"))?;
            ensure!(
                rules.len() == 1
                    && rules[0].input == source.path
                    && rules[0].template
                    && same(
                        &rules[0].when.targets,
                        if source.d2 {
                            &TARGETS[..2]
                        } else {
                            &TARGETS[..]
                        }
                    )
                    && same(&rules[0].when.all_features, &[source.owner])
                    && rules[0].when.any_features.is_empty()
                    && rules[0].when.none_features.is_empty(),
                "provider rule changed"
            );
            let current = core.read_source(source.path)?;
            validate_current_source(*source, &current)?;
            ensure!(leaf.witnesses.len() == 20, "incomplete typed witness cells");
            let mut contexts = BTreeSet::new();
            for witness in &leaf.witnesses {
                let (environment, target) = witness
                    .context
                    .split_once('/')
                    .ok_or_else(|| eyre::eyre!("invalid witness context"))?;
                let present = environment == "dev" && (!source.d2 || d2(target));
                let enabled = if !present {
                    &[][..]
                } else if d2(target) {
                    &ON[..]
                } else {
                    &KEYBOARD[..]
                };
                let explicit = core.context(target, enabled)?;
                let index = SOURCES
                    .iter()
                    .position(|g| g.path == source.path)
                    .expect("fixed source");
                // Insertion's historical D2 witness contains its typed member;
                // the other historical targets contain only the old caret facade.
                let historical_oid = if index == 0 && d2(target) {
                    RAW[0].oid
                } else {
                    raw_oid(index, target, &explicit)
                };
                ensure!(
                    contexts.insert(witness.context.clone())
                        && ledger.context_commits.get(&witness.context)
                            == Some(&witness.source_commit)
                        && witness.present == present
                        && witness.raw_blob.as_deref() == present.then_some(historical_oid)
                        && witness.mode.as_deref() == present.then_some("100644")
                        && same(&witness.explicit_registered_features, enabled),
                    "historical provider witness changed"
                );
            }
        }
        ensure!(
            seen == PATHS.into_iter().map(str::to_owned).collect(),
            "provider path boundary changed"
        );
        let mut raw_seen = BTreeSet::new();
        for entry in &ledger.raw_bodies {
            let raw = RAW
                .iter()
                .find(|r| r.oid == entry.blob)
                .ok_or_else(|| eyre::eyre!("extra raw body"))?;
            ensure!(
                raw_seen.insert(entry.blob.clone())
                    && raw.bytes == entry.bytes
                    && raw.digest == entry.sha256
                    && !entry.raw_role.is_empty()
                    && matches!(
                        entry.scope.as_str(),
                        "accepted_closed_action_cohort_retained_source"
                            | "root_authorized_exact_oid_recovery"
                    ),
                "raw body witness changed"
            );
        }
        let mut tree_contexts = BTreeSet::new();
        for tree in &ledger.tree_inputs {
            ensure!(
                tree_contexts.insert(tree.context.clone())
                    && ledger.context_commits.get(&tree.context) == Some(&tree.commit)
                    && tree.bytes > 0
                    && tree.bytes <= 2097152
                    && tree.sha256.len() == 64
                    && tree.raw_role.ends_with(".stdout.bin"),
                "retained tree witness framing changed"
            );
        }
        let raw = read_wave_git_blobs(
            &core.repository,
            &RAW.iter().map(|r| r.oid.to_owned()).collect(),
        )?;
        for pinned in RAW {
            validate_body(&raw[pinned.oid], pinned.bytes, pinned.digest)?;
        }
        let inventory = discover_core_source_files(&core.core)?;
        ensure!(
            PATHS.iter().all(|p| inventory.contains(*p)),
            "shared source absent"
        );
        Ok(Self {
            core,
            inventory,
            raw,
        })
    }
    fn render(
        &self,
        index: usize,
        target: &str,
        context: &ProjectionContext,
    ) -> Result<Option<Vec<u8>>> {
        let selected = self
            .core
            .selection_for_assertion(context, &self.inventory)?;
        let source = SOURCES[index];
        let Some(input) = selected.inputs.get(source.path) else {
            ensure!(
                selected.omitted_paths.contains(source.path),
                "provider omitted without explicit owner rule"
            );
            return Ok(None);
        };
        ensure!(
            input.input == source.path && input.template,
            "unexpected alternate provider body"
        );
        let body = self.core.read_source(source.path)?;
        validate_current_source(source, &body)?;
        let rendered = render_java_source(std::str::from_utf8(&body)?, context)?.into_bytes();
        ensure!(
            std::str::from_utf8(&rendered)?.contains("package "),
            "lost real provider body for {target}"
        );
        Ok(Some(rendered))
    }
    fn assert_context(&self, target: &str, context: &ProjectionContext) -> Result<()> {
        for (index, source) in SOURCES.iter().enumerate() {
            let present = (!source.d2 || d2(target)) && context.features[source.owner];
            assert_eq!(
                self.render(index, target, context)?.as_deref(),
                present.then(|| self.raw[raw_oid(index, target, context)].as_slice()),
                "{}/{target}",
                source.path
            );
        }
        Ok(())
    }
}
#[test]
fn palette_history_providers_reconstruct_260_frozen_memberships_and_real_trees() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut counts = (0, 0);
    for (context, commit) in COMMITS {
        let (environment, target) = context.split_once('/').expect("fixed context");
        let enabled = if environment == "release" {
            &[][..]
        } else if d2(target) {
            &ON[..]
        } else {
            &KEYBOARD[..]
        };
        let explicit = fixture.core.context(target, enabled)?;
        fixture.assert_context(target, &explicit)?;
        let stdout = read_wave_git_tree(&fixture.core.repository, commit, &PATHS)?;
        let mut found = BTreeMap::new();
        for line in std::str::from_utf8(&stdout)?.lines() {
            let (header, path) = line
                .split_once('\t')
                .ok_or_else(|| eyre::eyre!("tree framing changed"))?;
            let fields = header.split_whitespace().collect::<Vec<_>>();
            ensure!(
                fields.len() == 3
                    && fields[0] == "100644"
                    && fields[1] == "blob"
                    && found
                        .insert(path.to_owned(), fields[2].to_owned())
                        .is_none(),
                "tree mode/type or uniqueness changed"
            );
        }
        for (index, source) in SOURCES.iter().enumerate() {
            let present = environment == "dev" && (!source.d2 || d2(target));
            assert_eq!(
                found
                    .get(&format!("platform/minecraft/{}", source.path))
                    .map(String::as_str),
                present.then_some(raw_oid(index, target, &explicit))
            );
            if present {
                counts.0 += 1;
            } else {
                counts.1 += 1;
            }
        }
    }
    assert_eq!(counts, (34, 226));
    Ok(())
}
#[test]
fn palette_history_twenty_feature_off_controls_omit_thirteen_before_body_reads() -> Result<()> {
    let mut fixture = Fixture::load()?;
    let catalog = CoreCatalog::load(&fixture.core.repository, &fixture.core.repository)?;
    assert_eq!(catalog.catalog.0.len(), 20);
    fixture.core.core = fixture
        .core
        .core
        .join("deliberately_missing_palette_history_sources");
    let mut cells = 0;
    for key in catalog.catalog.0.keys() {
        let context = fixture.core.historical_feature_off_catalog_context(key)?;
        for index in 0..13 {
            assert!(
                fixture
                    .render(index, "feature-off-control", &context)?
                    .is_none()
            );
            cells += 1;
        }
    }
    assert_eq!(cells, 260);
    Ok(())
}
#[test]
fn palette_history_independent_owner_masks_keep_real_caret_member_boundaries() -> Result<()> {
    let fixture = Fixture::load()?;
    for target in TARGETS {
        for flags in [&[][..], &["client_actions"][..], &KEYBOARD[..]] {
            let context = fixture.core.context(target, flags)?;
            fixture.assert_context(target, &context)?;
            if context.features["keyboard_profiles"] {
                let insertion = String::from_utf8(
                    fixture
                        .render(0, target, &context)?
                        .expect("owned caret facade"),
                )?;
                // The neutral caret query also serves ordinary command callers
                // on the two versions that contain its genuine implementation.
                assert_eq!(
                    insertion.contains("hasAvailableLiteralChildren"),
                    d2(target)
                );
                assert_eq!(
                    insertion.contains("import net.minecraft.resources.Identifier;"),
                    target == "26.1.2"
                );
            }
        }
        if d2(target) {
            for flags in [&TYPED[..], &ON[..]] {
                let context = fixture.core.context(target, flags)?;
                fixture.assert_context(target, &context)?;
                assert!(
                    String::from_utf8(
                        fixture
                            .render(0, target, &context)?
                            .expect("typed caret facade")
                    )?
                    .contains("public static boolean hasAvailableLiteralChildren")
                );
                for unrelated in [
                    "workspace_panels",
                    "document_history",
                    "context_actions",
                    "client_program_actions",
                    "packet_values",
                ] {
                    assert!(!context.features[unrelated]);
                }
            }
        }
    }
    Ok(())
}
#[test]
fn palette_history_original_prerequisites_and_unsupported_targets_refuse() -> Result<()> {
    let fixture = Fixture::load()?;
    for target in &TARGETS[2..] {
        assert!(fixture.core.context(target, &ON).is_err());
        fixture.assert_context(target, &fixture.core.context(target, &KEYBOARD)?)?;
    }
    for target in &TARGETS[..2] {
        for flags in [
            &["keyboard_profiles"][..],
            &["client_actions", "command_palette"][..],
            &[
                "client_actions",
                "keyboard_profiles",
                "client_theme",
                "typed_command_palette",
            ][..],
            &[
                "client_actions",
                "keyboard_profiles",
                "client_theme",
                "command_palette",
                "command_history",
            ][..],
            &["client_actions", "palette_history_unregistered"][..],
        ] {
            assert!(fixture.core.context(target, flags).is_err());
        }
    }
    Ok(())
}
fn collector_fixture(
    fixture: &Fixture,
    root: &std::path::Path,
    target: &str,
) -> Result<(super::core_inputs::CoreProjectInputs, BTreeSet<String>)> {
    std::fs::create_dir_all(root)?;
    let mut metadata = fixture.core.metadata.clone();
    metadata
        .source_rules
        .retain(|p, _| PATHS.contains(&p.as_str()));
    let inventory = PATHS
        .into_iter()
        .map(str::to_owned)
        .collect::<BTreeSet<_>>();
    let off = fixture.core.context(target, &[])?;
    let selected = select_core_inputs(&metadata, &off, &inventory)?;
    for (output, input) in &selected.inputs {
        ensure!(
            !output.starts_with("src/"),
            "off unexpectedly selected provider source"
        );
        let destination = root.join(&input.input);
        std::fs::create_dir_all(destination.parent().expect("input parent"))?;
        std::fs::write(
            destination,
            read_bounded(
                &checked_file(&fixture.core.core, &input.input)?,
                16 * 1024 * 1024,
            )?,
        )?;
    }
    Ok((metadata, inventory))
}
#[test]
fn palette_history_actual_collector_omits_malformed_off_sources_and_rejects_selected() -> Result<()>
{
    let fixture = Fixture::load()?;
    let temporary = tempfile::tempdir()?;
    let root = temporary.path().join(CORE_ROOT);
    let (metadata, inventory) = collector_fixture(&fixture, &root, "1.19.2")?;
    for path in PATHS {
        let destination = root.join(path);
        std::fs::create_dir_all(destination.parent().expect("source parent"))?;
        std::fs::write(destination, [0xff, 0xfe])?;
    }
    let off = fixture.core.context("1.19.2", &[])?;
    let selected = select_core_inputs(&metadata, &off, &inventory)?;
    let artifacts = collect_core_artifacts(&root, &selected, &off)?;
    assert!(PATHS.iter().all(|p| !artifacts.contains_key(*p)));
    let on = fixture.core.context("1.19.2", &ON)?;
    let selected = select_core_inputs(&metadata, &on, &inventory)?;
    assert!(collect_core_artifacts(&root, &selected, &on).is_err());
    Ok(())
}
#[test]
fn palette_history_actual_collector_preserves_shared_provenance_and_real_rendered_bodies()
-> Result<()> {
    let fixture = Fixture::load()?;
    let temporary = tempfile::tempdir()?;
    for target in &TARGETS[..2] {
        let root = temporary.path().join(target).join(CORE_ROOT);
        let (metadata, inventory) = collector_fixture(&fixture, &root, target)?;
        for path in PATHS {
            let destination = root.join(path);
            std::fs::create_dir_all(destination.parent().expect("source parent"))?;
            std::fs::write(destination, fixture.core.read_source(path)?)?;
        }
        let context = fixture.core.context(target, &ON)?;
        let selected = select_core_inputs(&metadata, &context, &inventory)?;
        let artifacts = collect_core_artifacts(&root, &selected, &context)?;
        for (index, source) in SOURCES.iter().enumerate() {
            let artifact = &artifacts[source.path];
            assert_eq!(artifact.source_path, format!("{CORE_ROOT}/{}", source.path));
            assert_eq!(
                artifact.source_bytes,
                fixture.core.read_source(source.path)?
            );
            assert!(artifact.overlay.is_none());
            assert!(
                artifact
                    .output_bytes
                    .ends_with(&fixture.raw[raw_oid(index, target, &context)])
            );
        }
    }
    Ok(())
}
#[test]
fn palette_history_shared_edits_propagate_without_environment_or_named_key_selectors() -> Result<()>
{
    let fixture = Fixture::load()?;
    for target in TARGETS {
        let flags = if d2(target) { &ON[..] } else { &KEYBOARD[..] };
        for environment in ["release", "dev"] {
            let mut context = fixture.core.context(target, flags)?;
            context.environment = environment.to_owned();
            context.projection_key = format!("review/provider-wave/{environment}/{target}");
            context.preset = context.projection_key.clone();
            fixture.assert_context(target, &context)?;
            for (index, source) in SOURCES.iter().enumerate() {
                if source.d2 && !d2(target) {
                    continue;
                }
                let shared = String::from_utf8(fixture.core.read_source(source.path)?)?;
                let edited = format!("{shared}// shared provider propagation witness\n");
                let rendered = render_java_source(&edited, &context)?;
                let before =
                    String::from_utf8(fixture.raw[raw_oid(index, target, &context)].clone())?;
                assert_eq!(
                    rendered,
                    format!("{before}// shared provider propagation witness\n")
                );
            }
        }
    }
    Ok(())
}
#[test]
fn palette_history_retains_genuine_history_and_capture_contracts_with_machine_deny_defaults()
-> Result<()> {
    let fixture = Fixture::load()?;
    for (index, markers) in [
        (
            1,
            &[
                "activeGeneration == generation",
                "active == holder[0]",
                "StandardCopyOption.ATOMIC_MOVE",
                "shutdownNow()",
            ][..],
        ),
        (
            2,
            &[
                "MAX_HISTORY_COMMANDS = 128",
                "MAX_FUZZY_SCORE = 0.65f",
                "slotFingerprint(currentSlot)",
                "newestByValue.putIfAbsent",
            ][..],
        ),
        (
            3,
            &[
                "ThreadLocal<Provenance>",
                "if (previous == null) CURRENT.remove();",
                "sourceEvents.isEmpty()",
            ][..],
        ),
        (
            4,
            &[
                "Immutable evidence captured",
                "keyBindings = List.copyOf(keyBindings)",
                "Only activatable candidates have surface text",
            ][..],
        ),
        (
            5,
            &[
                "CAPTURE_EXPIRED",
                "clipboardWriter.accept(payload)",
                "Objects.requireNonNull(projection, \"projection\")",
            ][..],
        ),
        (
            6,
            &[
                "CAPTURE_EXPIRED",
                "clipboardWriter.accept(String.join",
                "if (values.isEmpty())",
            ][..],
        ),
        (
            9,
            &[
                "MAX_ENTRIES = 200",
                "MAX_COMMAND_LENGTH = 16 * 1024",
                "entries.descendingIterator()",
                "persistenceExecutor.execute",
            ][..],
        ),
        (
            10,
            &[
                "sfm-command-history schema=1",
                "CodingErrorAction.REPORT",
                "Base64.getDecoder().decode",
            ][..],
        ),
    ] {
        let source = String::from_utf8(fixture.core.read_source(PATHS[index])?)?;
        for marker in markers {
            assert!(
                source.contains(marker),
                "lost source contract {}/{marker}",
                PATHS[index]
            );
        }
    }
    for index in [5, 6, 7, 8, 12] {
        let source = String::from_utf8(fixture.core.read_source(PATHS[index])?)?;
        assert!(
            !source.contains("programmaticDescriptor(") && !source.contains("programmaticHandler("),
            "human action widened machine access"
        );
    }
    let action = String::from_utf8(
        fixture
            .core
            .read_source("src/main/java/ca/teamdman/sfm/client/action/SFMClientAction.java")?,
    )?;
    assert!(
        action.contains("default Optional<SFMClientActionDescriptor> programmaticDescriptor()")
    );
    assert!(
        action
            .contains("default Optional<SFMClientActionProgrammaticHandler> programmaticHandler()")
    );
    assert!(action.matches("return Optional.empty();").count() >= 3);
    Ok(())
}
