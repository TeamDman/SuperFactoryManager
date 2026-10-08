//! Prepared source-only leaf preservation; no test registration or execution.
//! Provider source closure is witnessed separately from compilation or execution.
#![cfg(test)]
use super::candidate_lock::checked_file;
use super::context::ProjectionContext;
use super::core_catalog::CoreCatalog;
use super::core_inputs::CORE_ROOT;
use super::core_inputs::CoreProjectInputs;
use super::core_inputs::collect_core_artifacts;
use super::core_inputs::discover_core_source_files;
use super::core_inputs::select_core_inputs;
use super::core_slice_test_support::CoreTestFixture;
use super::core_slice_test_support::historical_feature_registry;
use super::core_slice_test_support::read_bounded;
use super::core_slice_test_support::read_git_blobs;
use super::provenance::sha256;
use super::release_baseline::frozen_git_command;
use super::render_java_source;
use eyre::Result;
use eyre::ensure;
use facet::Facet;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::path::Path;
const LEDGER: &str = "docs/tasks/sfm-core-small-three-leaves-slice.json";
const PATHS: [&str; 3] = [
    "src/main/java/ca/teamdman/sfm/client/syntax/SFMSyntaxHighlightLimits.java",
    "src/main/java/ca/teamdman/sfm/common/net/SFMClientInboxAddress.java",
    "src/main/java/ca/teamdman/sfm/common/authorization/SFMManagerOperatorAuthorization.java",
];
const TARGETS: [&str; 10] = [
    "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
    "26.1.2",
];
const INBOX: [&str; 5] = [
    "client_inbox",
    "packet_transport_private",
    "packet_computation",
    "packet_values",
    "runtime_resource_cleanup",
];
const D1_ON: [&str; 7] = [
    "syntax_languages",
    "manager_operator_queries",
    "client_inbox",
    "packet_transport_private",
    "packet_computation",
    "packet_values",
    "runtime_resource_cleanup",
];
const D2_ON: [&str; 6] = [
    "syntax_languages",
    "client_inbox",
    "packet_transport_private",
    "packet_computation",
    "packet_values",
    "runtime_resource_cleanup",
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
#[derive(Clone, Copy)]
struct Golden {
    path: &'static str,
    owner: &'static str,
    oid: &'static str,
    digest: &'static str,
    bytes: usize,
    d1_only: bool,
}
const GOLDENS: [Golden; 3] = [
    Golden {
        path: "src/main/java/ca/teamdman/sfm/client/syntax/SFMSyntaxHighlightLimits.java",
        owner: "syntax_languages",
        oid: "6c9e382978246b7444384476977deafd49bb882e",
        digest: "sha256:7dac142a06a10ec93b9c29f51529593ce4fbee6a6233cb648422734ca3f82493",
        bytes: 934,
        d1_only: false,
    },
    Golden {
        path: "src/main/java/ca/teamdman/sfm/common/net/SFMClientInboxAddress.java",
        owner: "client_inbox",
        oid: "ed8c9ace785bf54d3c64d3f536bc766901c51301",
        digest: "sha256:4f990ed9dcd88e497a349197a41dd3cc79b30d571ba08b053408686eece4f96e",
        bytes: 1465,
        d1_only: false,
    },
    Golden {
        path: "src/main/java/ca/teamdman/sfm/common/authorization/SFMManagerOperatorAuthorization.java",
        owner: "manager_operator_queries",
        oid: "d041b8610d752614fd0031be18bb760339aa2c21",
        digest: "sha256:85a07b50ed90824ed2061f294ddc99cb15187310a2003ec3051935c98d6046ed",
        bytes: 3889,
        d1_only: true,
    },
];

const PROVIDERS: [(&str, usize, &str); 3] = [
    (
        "src/main/java/ca/teamdman/sfm/common/blockentity/ManagerBlockEntity.java",
        26108,
        "sha256:6496f23d64dcdb3825ecbb7ed3cbec1041958ca53947ed058b5954caffa03b93",
    ),
    (
        "src/main/java/ca/teamdman/sfm/common/net/SFMPacketHandlingContext.java",
        9531,
        "sha256:090379da05475414ddc93e848a795e4f3852d142fe51774e7c44d4a2257d3c11",
    ),
    (
        "src/main/java/ca/teamdman/sfm/common/util/SFMEntityUtils.java",
        819,
        "sha256:32e0da247ab4a81eed4b56c505c9e5ca2d385ee8d734a2f67a0bb899486f21b3",
    ),
];
#[derive(Facet)]
struct Ledger {
    schema: String,
    context_commits: BTreeMap<String, String>,
    normalization: String,
    scope: String,
    files: Vec<Leaf>,
}
#[derive(Facet)]
struct Leaf {
    path: String,
    core_path: String,
    template_sha256: String,
    template_bytes: usize,
    raw_blob: String,
    raw_sha256: String,
    raw_bytes: usize,
    normalization: String,
    line_endings: String,
    terminal_newline: String,
    membership: Membership,
    witnesses: Vec<Witness>,
}
#[derive(Facet)]
struct Membership {
    targets: Vec<String>,
    all_features: Vec<String>,
    any_features: Vec<String>,
    none_features: Vec<String>,
}
#[derive(Facet)]
struct Witness {
    context: String,
    source_commit: String,
    present: bool,
    raw_blob: Option<String>,
    raw_sha256: Option<String>,
    raw_bytes: Option<usize>,
    mode: Option<String>,
    explicit_registered_features: Vec<String>,
    features_origin: String,
}
struct Fixture {
    core: CoreTestFixture,
    inventory: BTreeSet<String>,
    raw: BTreeMap<String, Vec<u8>>,
}
fn same(actual: &[String], expected: &[&str]) -> bool {
    actual.len() == expected.len()
        && actual.iter().map(String::as_str).collect::<BTreeSet<_>>()
            == expected.iter().copied().collect()
}
fn d2(target: &str) -> bool {
    matches!(target, "1.19.2" | "1.19.4")
}
fn on(target: &str) -> &'static [&'static str] {
    match target {
        "1.19.2" => &D1_ON,
        "1.19.4" => &D2_ON,
        _ => &[],
    }
}
fn supported(g: Golden, target: &str) -> bool {
    d2(target) && (!g.d1_only || target == "1.19.2")
}
fn commits() -> BTreeMap<String, String> {
    COMMITS
        .into_iter()
        .map(|(c, h)| (c.to_owned(), h.to_owned()))
        .collect()
}
fn raw_contract(bytes: &[u8], g: Golden) -> Result<()> {
    ensure!(
        bytes.len() == g.bytes && sha256(bytes) == g.digest,
        "raw leaf bytes changed"
    );
    ensure!(
        !bytes.contains(&b'\r')
            && bytes.ends_with(b"\n")
            && !bytes.ends_with(b"\n\n")
            && !bytes.starts_with(&[0xef, 0xbb, 0xbf]),
        "raw leaf framing changed"
    );
    std::str::from_utf8(bytes)?;
    Ok(())
}
impl Fixture {
    fn load() -> Result<Self> {
        let core = CoreTestFixture::load()?;
        let ledger: Ledger = facet_json::from_str(std::str::from_utf8(&read_bounded(
            &checked_file(&core.repository, LEDGER)?,
            1024 * 1024,
        )?)?)?;
        ensure!(
            ledger.schema == "sfm:core_small_three_leaves_slice@1"
                && ledger.context_commits == commits()
                && ledger.normalization == "none_raw_exact_lf_with_exactly_one_terminal_lf"
                && ledger.scope.contains("test-only")
                && ledger.files.len() == 3,
            "leaf ledger scope changed"
        );
        let (_, historical_features) = historical_feature_registry()?;
        ensure!(
            historical_features.0.len() == 180,
            "frozen owner count changed"
        );
        for (owner, targets, requires) in [
            ("syntax_languages", &TARGETS[..2], &[][..]),
            (
                "client_inbox",
                &TARGETS[..2],
                &["packet_transport_private"][..],
            ),
            ("manager_operator_queries", &TARGETS[..1], &[][..]),
            (
                "packet_transport_private",
                &TARGETS[..2],
                &["packet_computation"][..],
            ),
            (
                "packet_computation",
                &TARGETS[..2],
                &["packet_values", "runtime_resource_cleanup"][..],
            ),
            ("packet_values", &TARGETS[..2], &[][..]),
            ("runtime_resource_cleanup", &TARGETS[..2], &[][..]),
        ] {
            let contract = &core.features.0[owner];
            ensure!(
                same(&contract.supported_targets, targets) && same(&contract.requires, requires),
                "existing owner/prerequisite changed"
            );
        }
        let mut seen = BTreeSet::new();
        for leaf in ledger.files {
            let g = *GOLDENS
                .iter()
                .find(|g| g.path == leaf.path)
                .ok_or_else(|| eyre::eyre!("unexpected leaf"))?;
            let targets = if g.d1_only {
                &TARGETS[..1]
            } else {
                &TARGETS[..2]
            };
            ensure!(
                seen.insert(leaf.path.clone())
                    && leaf.core_path == format!("{CORE_ROOT}/{}", g.path)
                    && leaf.template_sha256 == g.digest
                    && leaf.template_bytes == g.bytes
                    && leaf.raw_blob == g.oid
                    && leaf.raw_sha256 == g.digest
                    && leaf.raw_bytes == g.bytes
                    && leaf.normalization == "none"
                    && leaf.line_endings == "lf"
                    && leaf.terminal_newline == "exactly_one_lf"
                    && same(&leaf.membership.targets, targets)
                    && same(&leaf.membership.all_features, &[g.owner])
                    && leaf.membership.any_features.is_empty()
                    && leaf.membership.none_features.is_empty(),
                "leaf contract changed"
            );
            let rules = core
                .metadata
                .source_rules
                .get(g.path)
                .ok_or_else(|| eyre::eyre!("leaf rule missing"))?;
            ensure!(
                rules.len() == 1
                    && rules[0].input == g.path
                    && rules[0].template
                    && same(&rules[0].when.targets, targets)
                    && same(&rules[0].when.all_features, &[g.owner])
                    && rules[0].when.any_features.is_empty()
                    && rules[0].when.none_features.is_empty(),
                "leaf rule changed"
            );
            ensure!(leaf.witnesses.len() == 20, "missing frozen leaf cells");
            let mut contexts = BTreeSet::new();
            for w in leaf.witnesses {
                let (environment, target) = w
                    .context
                    .split_once('/')
                    .ok_or_else(|| eyre::eyre!("bad leaf context"))?;
                let present = environment == "dev" && supported(g, target);
                let features = if environment == "dev" {
                    on(target)
                } else {
                    &[]
                };
                ensure!(
                    contexts.insert(w.context.clone())
                        && commits().get(&w.context) == Some(&w.source_commit)
                        && w.present == present
                        && w.raw_blob.as_deref() == present.then_some(g.oid)
                        && w.raw_sha256.as_deref() == present.then_some(g.digest)
                        && w.raw_bytes == present.then_some(g.bytes)
                        && w.mode.as_deref() == present.then_some("100644")
                        && same(&w.explicit_registered_features, features)
                        && w.features_origin
                            == "reviewed_current_explicit_reconstruction_not_historical_manifest",
                    "frozen leaf cell changed"
                );
                core.context(target, features)?;
            }
        }
        ensure!(
            seen == PATHS.into_iter().map(str::to_owned).collect(),
            "leaf path scope changed"
        );
        let raw = read_git_blobs(
            &core.repository,
            &GOLDENS.iter().map(|g| g.oid.to_owned()).collect(),
        )?;
        for g in GOLDENS {
            raw_contract(&raw[g.oid], g)?;
            raw_contract(&core.read_source(g.path)?, g)?;
        }
        let inventory = discover_core_source_files(&core.core)?;
        ensure!(
            PATHS.iter().all(|p| inventory.contains(*p)),
            "authored leaf absent"
        );
        for (path, bytes, digest) in PROVIDERS {
            let body = core.read_source(path)?;
            let body = if path == PROVIDERS[0].0 {
                super::core_buffer_manager_slice_tests::historical_manager_template(&body)?
            } else {
                body
            };
            ensure!(
                body.len() == bytes && sha256(&body) == digest && inventory.contains(path),
                "existing authorization provider changed"
            );
            ensure!(
                !core.metadata.source_rules.contains_key(path),
                "baseline authorization provider gained a feature rule"
            );
        }
        Ok(Self {
            core,
            inventory,
            raw,
        })
    }
    fn render(&self, index: usize, context: &ProjectionContext) -> Result<Option<Vec<u8>>> {
        let g = GOLDENS[index];
        let selected = self
            .core
            .selection_for_assertion(context, &self.inventory)?;
        let Some(input) = selected.inputs.get(g.path) else {
            ensure!(
                selected.omitted_paths.contains(g.path),
                "leaf omitted without a rule"
            );
            return Ok(None);
        };
        ensure!(
            input.input == g.path && input.template,
            "leaf gained an alternate selector"
        );
        let bytes = self.core.read_source(g.path)?;
        raw_contract(&bytes, g)?;
        Ok(Some(
            render_java_source(std::str::from_utf8(&bytes)?, context)?.into_bytes(),
        ))
    }
    fn assert_context(&self, target: &str, context: &ProjectionContext) -> Result<()> {
        let selected = self
            .core
            .selection_for_assertion(context, &self.inventory)?;
        for (i, g) in GOLDENS.iter().enumerate() {
            let present = supported(*g, target) && context.features[g.owner];
            assert_eq!(
                self.render(i, context)?.as_deref(),
                present.then(|| self.raw[g.oid].as_slice()),
                "{}/{target}",
                g.path
            );
            if present && g.d1_only {
                for (path, _, _) in PROVIDERS {
                    assert!(
                        selected.inputs.contains_key(path),
                        "actual provider absent: {path}"
                    );
                }
            }
        }
        Ok(())
    }
    fn isolated_metadata(&self) -> CoreProjectInputs {
        let mut metadata = self.core.metadata.clone();
        metadata
            .source_rules
            .retain(|p, _| PATHS.contains(&p.as_str()));
        metadata
    }
    fn isolated_inventory(&self) -> BTreeSet<String> {
        PATHS.into_iter().map(str::to_owned).collect()
    }
    fn copy_project_inputs(
        &self,
        root: &Path,
        metadata: &CoreProjectInputs,
        context: &ProjectionContext,
    ) -> Result<()> {
        let selected = select_core_inputs(metadata, context, &self.isolated_inventory())?;
        for (output, input) in selected
            .inputs
            .iter()
            .filter(|(p, _)| !p.starts_with("src/"))
        {
            ensure!(!output.starts_with("src/"), "unexpected isolated source");
            let source = read_bounded(
                &checked_file(&self.core.core, &input.input)?,
                16 * 1024 * 1024,
            )?;
            let dest = root.join(&input.input);
            std::fs::create_dir_all(dest.parent().expect("project input parent"))?;
            std::fs::write(dest, source)?;
        }
        Ok(())
    }
}

#[test]
fn small_leaves_reconstruct_five_present_and_fiftyfive_absent_frozen_cells() -> Result<()> {
    let f = Fixture::load()?;
    let (mut present, mut absent) = (0, 0);
    for (name, commit) in COMMITS {
        let (environment, target) = name.split_once('/').expect("fixed frozen context");
        let features = if environment == "dev" {
            on(target)
        } else {
            &[]
        };
        f.assert_context(target, &f.core.context(target, features)?)?;
        let mut command = frozen_git_command(&f.core.repository);
        command.args([
            "-c",
            "protocol.allow=never",
            "-c",
            "core.fsmonitor=false",
            "ls-tree",
            "-r",
            commit,
            "--",
        ]);
        for path in PATHS {
            command.arg(format!("platform/minecraft/{path}"));
        }
        let result = command.output()?;
        ensure!(
            result.status.success() && result.stdout.len() <= 4096,
            "bounded frozen tree query failed"
        );
        let mut found = BTreeMap::new();
        for line in std::str::from_utf8(&result.stdout)?.lines() {
            let (header, path) = line
                .split_once('\t')
                .ok_or_else(|| eyre::eyre!("invalid frozen tree line"))?;
            let fields = header.split_whitespace().collect::<Vec<_>>();
            ensure!(
                fields.len() == 3 && fields[0] == "100644" && fields[1] == "blob",
                "wrong frozen source mode/type"
            );
            ensure!(
                found
                    .insert(path.to_owned(), fields[2].to_owned())
                    .is_none(),
                "duplicate frozen source"
            );
        }
        for g in GOLDENS {
            let exists = environment == "dev" && supported(g, target);
            assert_eq!(
                found
                    .get(&format!("platform/minecraft/{}", g.path))
                    .map(String::as_str),
                exists.then_some(g.oid)
            );
            if exists {
                present += 1;
            } else {
                absent += 1;
            }
        }
    }
    assert_eq!((present, absent), (5, 55));
    Ok(())
}

#[test]
fn small_leaves_twenty_feature_off_controls_omit_before_reading() -> Result<()> {
    let mut f = Fixture::load()?;
    let catalog = CoreCatalog::load(&f.core.repository, &f.core.repository)?;
    assert_eq!(catalog.catalog.0.len(), 20);
    f.core.core = f.core.core.join("deliberately_absent_small_leaf_read_root");
    let mut cells = 0;
    for key in catalog.catalog.0.keys() {
        let context = f.core.historical_feature_off_catalog_context(key)?;
        for i in 0..3 {
            assert!(f.render(i, &context)?.is_none());
            cells += 1;
        }
    }
    assert_eq!(cells, 60);
    Ok(())
}

#[test]
fn small_leaves_explicit_owner_masks_keep_exact_membership_without_new_prerequisites() -> Result<()>
{
    let f = Fixture::load()?;
    for target in TARGETS {
        f.assert_context(target, &f.core.context(target, &[])?)?;
        if d2(target) {
            for enabled in [&["syntax_languages"][..], &INBOX[..], on(target)] {
                let context = f.core.context(target, enabled)?;
                f.assert_context(target, &context)?;
                for unrelated in [
                    "client_actions",
                    "workspace_panels",
                    "terminal_local",
                    "client_manager",
                    "multiplayer_packets",
                ] {
                    assert!(
                        !context.features[unrelated],
                        "leaf owner invented {unrelated}"
                    );
                }
            }
        }
        if target == "1.19.2" {
            f.assert_context(
                target,
                &f.core.context(target, &["manager_operator_queries"])?,
            )?;
        }
    }
    Ok(())
}

#[test]
fn small_leaves_unsupported_targets_and_missing_existing_inbox_chain_fail_closed() -> Result<()> {
    let f = Fixture::load()?;
    for target in &TARGETS[2..] {
        for enabled in [
            &["syntax_languages"][..],
            &INBOX[..],
            &["manager_operator_queries"][..],
        ] {
            assert!(
                f.core.context(target, enabled).is_err(),
                "unsupported owner accepted on {target}"
            );
        }
    }
    assert!(
        f.core
            .context("1.19.4", &["manager_operator_queries"])
            .is_err()
    );
    for target in &TARGETS[..2] {
        for missing in INBOX {
            if missing == "client_inbox" {
                continue;
            }
            let enabled = INBOX
                .into_iter()
                .filter(|name| *name != missing)
                .collect::<Vec<_>>();
            assert!(
                f.core.context(target, &enabled).is_err(),
                "missing prerequisite accepted: {missing}"
            );
        }
        assert!(f.core.context(target, &["client_inbox"]).is_err());
        assert!(
            f.core
                .context(target, &["syntax_languages", "small_leaf_unregistered"])
                .is_err()
        );
    }
    Ok(())
}

#[test]
fn small_leaves_real_provider_members_and_security_bounds_survive_without_fake_callers()
-> Result<()> {
    let f = Fixture::load()?;
    let context = f.core.context("1.19.2", &["manager_operator_queries"])?;
    f.assert_context("1.19.2", &context)?;
    for (path, _, _) in PROVIDERS {
        let bytes = f.core.read_source(path)?;
        let body = render_java_source(std::str::from_utf8(&bytes)?, &context)?;
        let marker = if path == PROVIDERS[0].0 {
            "public class ManagerBlockEntity extends BaseContainerBlockEntity"
        } else if path == PROVIDERS[1].0 {
            "return inner.getSender();"
        } else {
            "return player.getLevel();"
        };
        assert!(
            body.contains(marker),
            "actual authorization provider lacks {marker}"
        );
        assert!(!body.contains("{%"));
    }
    let required: [&[&str]; 3] = [
        &[
            "4 * 1024 * 1024",
            "262_144",
            "DEFAULT_MAXIMUM_DIAGNOSTICS = 256",
            "maximumSourceBytes <= 0 || maximumSpans <= 0 || maximumDiagnostics <= 0",
        ],
        &[
            "MAX_ID_CHARACTERS = 128",
            "Objects.requireNonNull(recipient",
            "Objects.requireNonNull(dimension",
            "Objects.requireNonNull(channel",
            "target.writeUUID(recipient)",
            "source.readUtf(MAX_ID_CHARACTERS)",
        ],
        &[
            "context.sender()",
            "server.isSameThread()",
            "getPlayer(sender.getUUID()) != sender",
            "!sender.connection.getConnection().isConnected()",
            "!sender.isAlive()",
            "sender.isSpectator()",
            "!sender.hasPermissions(Commands.LEVEL_GAMEMASTERS)",
            "level.dimension().location().equals(dimension)",
            "!level.isLoaded(position)",
            "instanceof ManagerBlockEntity manager",
            "manager.isRemoved()",
            "(status == Status.ALLOWED) != (manager != null)",
        ],
    ];
    for (i, g) in GOLDENS.iter().enumerate() {
        let body = std::str::from_utf8(&f.raw[g.oid])?;
        for marker in required[i] {
            assert!(body.contains(marker), "leaf lost {marker}");
        }
        for forbidden in [
            "ProcessBuilder",
            "Minecraft.getInstance",
            "Runtime.getRuntime",
            "setClipboard",
            "java.nio.file.",
        ] {
            assert!(
                !body.contains(forbidden),
                "leaf introduced unrelated effect: {forbidden}"
            );
        }
    }
    // These providers were deferred when this leaf receipt was authored. Their
    // later integration must preserve the actual pinned sources, not keep the
    // growing authored inventory artificially incomplete.
    let providers = [
        (
            "src/main/java/ca/teamdman/sfm/client/syntax/SFMSyntaxHighlightRuntime.java",
            "2d9134cdabbaf4e9f5094225b639f810019c617b",
        ),
        (
            "src/main/java/ca/teamdman/sfm/common/net/ClientboundClientInboxValuePacket.java",
            "f74d93ee2d4085e965a10c464900ce9c5c077997",
        ),
        (
            "src/main/java/ca/teamdman/sfm/common/net/ServerboundClientInboxSubscriptionPacket.java",
            "b44aa026a37d51dd203ed242db70ac7fd3914321",
        ),
        (
            "src/main/java/ca/teamdman/sfm/common/net/SFMManagerShowQuery.java",
            "0c90adeffb8aff3bc3996a7801c4cc50aa7238f9",
        ),
        (
            "src/main/java/ca/teamdman/sfm/common/net/ClientboundManagerShowPacket.java",
            "031be7ef33496ea01c5957ce3c9d0fdc6ad58803",
        ),
    ];
    let raw_providers = read_git_blobs(
        &f.core.repository,
        &providers.iter().map(|(_, oid)| (*oid).to_owned()).collect(),
    )?;
    let context = f.core.context("1.19.2", &D1_ON)?;
    let selected = select_core_inputs(&f.core.metadata, &context, &f.inventory)?;
    for (path, oid) in providers {
        assert!(
            f.inventory.contains(path),
            "integrated provider absent: {path}"
        );
        let input = &selected.inputs[path];
        assert_eq!(input.input, path, "provider gained an alternate input");
        assert!(input.template, "Java provider bypasses Liquid: {path}");
        let source = f.core.read_source(path)?;
        assert!(
            render_java_source(std::str::from_utf8(&source)?, &context)?.as_bytes()
                == raw_providers[oid],
            "integrated provider changed its pinned body: {path}"
        );
    }
    Ok(())
}

#[test]
fn small_leaves_shared_edit_reaches_both_d2_outputs_and_keeps_named_contexts_descriptive()
-> Result<()> {
    let f = Fixture::load()?;
    let metadata = f.isolated_metadata();
    let inventory = f.isolated_inventory();
    for target in &TARGETS[..2] {
        let temp = tempfile::tempdir()?;
        let root = temp.path().join(CORE_ROOT);
        std::fs::create_dir_all(&root)?;
        let base = f.core.context(target, on(target))?;
        f.copy_project_inputs(&root, &metadata, &base)?;
        let mut edited = BTreeMap::new();
        for g in GOLDENS {
            let original = std::str::from_utf8(&f.raw[g.oid])?;
            let prefix = original
                .strip_suffix("}\n")
                .ok_or_else(|| eyre::eyre!("leaf lost closing body"))?;
            let bytes =
                format!("{prefix}    // shared small-leaf propagation proof\n}}\n").into_bytes();
            let path = root.join(g.path);
            std::fs::create_dir_all(path.parent().expect("leaf parent"))?;
            std::fs::write(path, &bytes)?;
            edited.insert(g.path, bytes);
        }
        for environment in ["release", "dev"] {
            let mut context = base.clone();
            context.environment = environment.to_owned();
            context.projection_key = format!("review/small-leaves/{environment}/{target}");
            context.preset = context.projection_key.clone();
            let selection = select_core_inputs(&metadata, &context, &inventory)?;
            let artifacts = collect_core_artifacts(&root, &selection, &context)?;
            for g in GOLDENS {
                if supported(g, target) {
                    let a = &artifacts[g.path];
                    assert_eq!(a.source_bytes, edited[g.path]);
                    assert_ne!(sha256(&a.source_bytes), g.digest);
                    assert!(a.output_bytes.ends_with(&edited[g.path]));
                    assert_eq!(a.source_path, format!("{CORE_ROOT}/{}", g.path));
                    assert!(a.overlay.is_none());
                } else {
                    assert!(!artifacts.contains_key(g.path));
                }
            }
        }
        let off = f.core.context(target, &[])?;
        let selection = select_core_inputs(&metadata, &off, &inventory)?;
        let artifacts = collect_core_artifacts(&root, &selection, &off)?;
        assert!(PATHS.iter().all(|p| !artifacts.contains_key(*p)));
    }
    Ok(())
}

#[test]
fn small_leaves_actual_collector_ignores_malformed_omitted_bytes_and_refuses_each_selected_leaf()
-> Result<()> {
    let f = Fixture::load()?;
    let metadata = f.isolated_metadata();
    let inventory = f.isolated_inventory();
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    std::fs::create_dir_all(&root)?;
    let off = f.core.context("1.19.2", &[])?;
    f.copy_project_inputs(&root, &metadata, &off)?;
    for path in PATHS {
        let file = root.join(path);
        std::fs::create_dir_all(file.parent().expect("leaf parent"))?;
        std::fs::write(file, [0xff, 0xfe])?;
    }
    let selected = select_core_inputs(&metadata, &off, &inventory)?;
    let omitted = collect_core_artifacts(&root, &selected, &off)?;
    assert!(PATHS.iter().all(|path| !omitted.contains_key(*path)));
    for (i, enabled) in [
        &["syntax_languages"][..],
        &INBOX[..],
        &["manager_operator_queries"][..],
    ]
    .into_iter()
    .enumerate()
    {
        let context = f.core.context("1.19.2", enabled)?;
        let selected = select_core_inputs(&metadata, &context, &inventory)?;
        assert!(selected.inputs[PATHS[i]].template);
        assert!(collect_core_artifacts(&root, &selected, &context).is_err());
        std::fs::write(root.join(PATHS[i]), f.core.read_source(PATHS[i])?)?;
        let artifacts = collect_core_artifacts(&root, &selected, &context)?;
        let g = GOLDENS[i];
        let artifact = &artifacts[g.path];
        assert_eq!(artifact.source_bytes, f.raw[g.oid]);
        assert_eq!(sha256(&artifact.source_bytes), g.digest);
        assert_eq!(artifact.source_path, format!("{CORE_ROOT}/{}", g.path));
        assert!(artifact.overlay.is_none());
        assert!(
            artifact
                .output_bytes
                .starts_with(b"// GENERATED by sfm-propagate-changes;")
        );
        assert!(artifact.output_bytes.ends_with(&f.raw[g.oid]));
        assert_ne!(
            sha256(&artifact.output_bytes),
            sha256(&artifact.source_bytes)
        );
        std::fs::write(root.join(PATHS[i]), [0xff, 0xfe])?;
    }
    Ok(())
}

#[test]
fn small_leaves_selected_directives_and_stale_or_snapshot_provenance_fail_closed() -> Result<()> {
    let f = Fixture::load()?;
    let metadata = f.isolated_metadata();
    let inventory = f.isolated_inventory();
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    std::fs::create_dir_all(&root)?;
    let off = f.core.context("1.19.2", &[])?;
    f.copy_project_inputs(&root, &metadata, &off)?;
    for g in GOLDENS {
        let path = root.join(g.path);
        std::fs::create_dir_all(path.parent().expect("leaf parent"))?;
        let bytes = format!(
            "{}{{% if features.unregistered_small_leaf %}}\n{{% endif %}}\n",
            std::str::from_utf8(&f.raw[g.oid])?
        );
        std::fs::write(path, bytes)?;
    }
    let omitted = select_core_inputs(&metadata, &off, &inventory)?;
    assert!(collect_core_artifacts(&root, &omitted, &off).is_ok());
    let on = f.core.context("1.19.2", &["syntax_languages"])?;
    let selected = select_core_inputs(&metadata, &on, &inventory)?;
    assert!(collect_core_artifacts(&root, &selected, &on).is_err());
    std::fs::write(root.join(PATHS[0]), f.core.read_source(PATHS[0])?)?;
    assert!(collect_core_artifacts(&root, &selected, &on).is_ok());
    assert!(
        collect_core_artifacts(&root, &selected, &off).is_err(),
        "collector accepted a changed selection context"
    );
    let mut alternate = metadata.clone();
    alternate.source_rules.get_mut(PATHS[0]).expect("leaf rule")[0].input =
        "release-baselines/4.34.0-1.19.2/overlays/src/main/java/ca/teamdman/sfm/client/syntax/SFMSyntaxHighlightLimits.java".to_owned();
    // Mapped fragments are allowed inside the fixed core; there is no external snapshot fallback.
    let mapped = select_core_inputs(&alternate, &on, &inventory)?;
    assert!(
        collect_core_artifacts(&root, &mapped, &on).is_err(),
        "collector substituted an absent mapped file from an external snapshot"
    );
    assert!(
        collect_core_artifacts(
            &temp.path().join("release-baselines/4.34.0-1.19.2/overlays"),
            &selected,
            &on
        )
        .is_err(),
        "collector accepted a root outside the fixed core layout"
    );
    alternate.source_rules.get_mut(PATHS[0]).expect("leaf rule")[0].input =
        "../release-baselines/4.34.0-1.19.2/overlays/src/main/java/ca/teamdman/sfm/client/syntax/SFMSyntaxHighlightLimits.java".to_owned();
    assert!(
        select_core_inputs(&alternate, &on, &inventory).is_err(),
        "selector accepted parent traversal"
    );
    Ok(())
}
