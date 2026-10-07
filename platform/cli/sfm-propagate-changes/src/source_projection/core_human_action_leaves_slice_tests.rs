//! Prepared bounded source regressions. Run only after reviewed promotion.
//!
//! Human actions are never invoked. Git objects are test witnesses, not source
//! providers. These checks do not prove a complete Java build or UI behavior.

#![cfg(test)]

use super::candidate_lock::checked_file;
use super::context::ProjectionContext;
use super::core_catalog::CoreCatalog;
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

const PATHS: [&str; 3] = [
    "src/main/java/ca/teamdman/sfm/client/action/SFMClipboardCopyAction.java",
    "src/main/java/ca/teamdman/sfm/client/action/SFMFocusAction.java",
    "src/main/java/ca/teamdman/sfm/client/action/ManagerEditAction.java",
];
const OWNERS: [&str; 3] = [
    "clipboard_action_commands",
    "focus_target_actions",
    "manager_direct_edit_action",
];
const HOST: &str = "src/main/java/ca/teamdman/sfm/client/action/SFMFocusTargetHost.java";
const HOST_OID: &str = "517bd19fbe5bac67a6131f2a7ed6df8d7d7f3f08";
const HOST_DIGEST: &str = "sha256:6423a5dd4f6415c76155628e166ebec06649f188482c0d27a52fccbabb0469db";
const BASE: &str = "src/main/java/ca/teamdman/sfm/client/action/SFMClientAction.java";
const REGISTRAR: &str = "src/main/java/ca/teamdman/sfm/client/action/SFMCommandPaletteActions.java";
const CONTEXT: &str = "src/main/java/ca/teamdman/sfm/client/action/SFMClientActionContext.java";
const LEDGER: &str = "docs/tasks/sfm-core-human-action-leaves-slice.json";
const CORE_PREFIX: &str = "platform/minecraft/core-liquid-template/";
const TARGETS: [&str; 10] = [
    "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
    "26.1.2",
];
const ALL_OWNERS: [&str; 5] = [
    "client_actions",
    "manager_editor_actions",
    "clipboard_action_commands",
    "focus_target_actions",
    "manager_direct_edit_action",
];
const TYPED: [&str; 5] = [
    "client_actions",
    "client_theme",
    "keyboard_profiles",
    "command_palette",
    "typed_command_palette",
];
// Explicit current registry closure, not automatic production prerequisites.
const MACHINE: [&str; 11] = [
    "client_actions",
    "packet_values",
    "sfml_execution_side",
    "client_manager",
    "client_program_consent",
    "packet_computation",
    "disk_readonly_access",
    "runtime_resource_cleanup",
    "client_program_actions",
    "manager_editor_actions",
    "focus_target_actions",
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
}
const GOLDENS: [Golden; 3] = [
    Golden {
        path: PATHS[0],
        owner: OWNERS[0],
        oid: "bd70d49706f4f40e8f7bc9a5b939dc4be9069e6e",
        digest: "sha256:422424d6ecbee5a41552dce03c4bc3f54d23eca167ab2f98858029c22f0bb7a8",
        bytes: 2547,
    },
    Golden {
        path: PATHS[1],
        owner: OWNERS[1],
        oid: "febb109ea47189a4015a40869f3eae31b3125f6d",
        digest: "sha256:efab70c2f60039047876294ef0d12241180ebda0a4d76c561c02c4fc039015d9",
        bytes: 3392,
    },
    Golden {
        path: PATHS[2],
        owner: OWNERS[2],
        oid: "47fa0bf5fdd16f34fc09319c6980e2d1b34f587d",
        digest: "sha256:cf40759a29f58dcf313305dd906cb58dd2ea36ebea0b5b14f7087a932dac0b03",
        bytes: 1066,
    },
];

#[derive(Facet)]
struct Ledger {
    schema: String,
    context_commits: BTreeMap<String, String>,
    normalization: String,
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
    line_endings: String,
    terminal_newline: String,
    normalization: String,
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
}

struct Fixture {
    core: CoreTestFixture,
    inventory: BTreeSet<String>,
    raw: BTreeMap<String, Vec<u8>>,
}

fn is_d2(target: &str) -> bool {
    matches!(target, "1.19.2" | "1.19.4")
}

fn same(actual: &[String], expected: &[&str]) -> bool {
    actual.len() == expected.len()
        && actual.iter().map(String::as_str).collect::<BTreeSet<_>>()
            == expected.iter().copied().collect()
}

fn pinned_commits() -> BTreeMap<String, String> {
    COMMITS
        .into_iter()
        .map(|(name, oid)| (name.to_owned(), oid.to_owned()))
        .collect()
}

impl Fixture {
    fn load() -> Result<Self> {
        let core = CoreTestFixture::load()?;
        let ledger: Ledger = facet_json::from_str(std::str::from_utf8(&read_bounded(
            &checked_file(&core.repository, LEDGER)?,
            1024 * 1024,
        )?)?)?;
        ensure!(
            ledger.schema == "sfm:core_human_action_leaves_slice@1"
                && ledger.context_commits == pinned_commits()
                && ledger.normalization == "none_raw_exact_lf_with_exactly_one_terminal_lf"
                && ledger.files.len() == 3,
            "action-leaf ledger scope changed"
        );
        let mut seen = BTreeSet::new();
        for leaf in ledger.files {
            let golden = GOLDENS
                .iter()
                .find(|row| row.path == leaf.path)
                .ok_or_else(|| eyre::eyre!("unknown action-leaf path"))?;
            ensure!(
                seen.insert(leaf.path.clone())
                    && leaf.core_path == format!("{CORE_PREFIX}{}", leaf.path)
                    && leaf.template_sha256 == golden.digest
                    && leaf.template_bytes == golden.bytes
                    && leaf.raw_blob == golden.oid
                    && leaf.raw_sha256 == golden.digest
                    && leaf.raw_bytes == golden.bytes
                    && leaf.line_endings == "lf"
                    && leaf.terminal_newline == "exactly_one_lf"
                    && leaf.normalization == "none"
                    && same(&leaf.membership.targets, &TARGETS[..2])
                    && same(&leaf.membership.all_features, &[golden.owner])
                    && leaf.membership.any_features.is_empty()
                    && leaf.membership.none_features.is_empty(),
                "action-leaf raw contract changed"
            );
            let rules = core
                .metadata
                .source_rules
                .get(golden.path)
                .ok_or_else(|| eyre::eyre!("action-leaf rule missing"))?;
            ensure!(
                rules.len() == 1
                    && rules[0].input == golden.path
                    && rules[0].template
                    && same(&rules[0].when.targets, &TARGETS[..2])
                    && same(&rules[0].when.all_features, &[golden.owner])
                    && rules[0].when.any_features.is_empty()
                    && rules[0].when.none_features.is_empty(),
                "action-leaf owner predicate changed"
            );
            let definition = &core.features.0[golden.owner];
            let required = if golden.owner == OWNERS[2] {
                &["manager_editor_actions"][..]
            } else {
                &["client_actions"][..]
            };
            ensure!(
                same(&definition.supported_targets, &TARGETS[..2])
                    && same(&definition.requires, required),
                "action-leaf owner gained prerequisites or support"
            );
            ensure!(
                leaf.witnesses.len() == 20,
                "action-leaf witnesses incomplete"
            );
            let mut contexts = BTreeSet::new();
            for witness in leaf.witnesses {
                let (environment, target) = witness
                    .context
                    .split_once('/')
                    .ok_or_else(|| eyre::eyre!("invalid action-leaf context"))?;
                let present = environment == "dev" && is_d2(target);
                let enabled = if present { &ALL_OWNERS[..] } else { &[][..] };
                ensure!(
                    contexts.insert(witness.context.clone())
                        && pinned_commits().get(&witness.context) == Some(&witness.source_commit)
                        && witness.present == present
                        && witness.raw_blob.as_deref() == present.then_some(golden.oid)
                        && witness.raw_sha256.as_deref() == present.then_some(golden.digest)
                        && witness.raw_bytes == present.then_some(golden.bytes)
                        && witness.mode.as_deref() == present.then_some("100644")
                        && same(&witness.explicit_registered_features, enabled),
                    "action-leaf historical witness changed"
                );
                core.context(target, enabled)?;
            }
        }
        ensure!(
            seen == PATHS.into_iter().map(str::to_owned).collect(),
            "action-leaf scope changed"
        );
        let mut ids = GOLDENS
            .iter()
            .map(|row| row.oid.to_owned())
            .collect::<BTreeSet<_>>();
        ids.insert(HOST_OID.to_owned());
        let raw = read_git_blobs(&core.repository, &ids)?;
        for row in GOLDENS {
            validate_raw(&raw[row.oid], row.bytes, row.digest)?;
        }
        validate_raw(&raw[HOST_OID], 267, HOST_DIGEST)?;
        let inventory = discover_core_source_files(&core.core)?;
        ensure!(
            PATHS.iter().all(|path| inventory.contains(*path)),
            "missing authored action leaf"
        );
        ensure!(inventory.contains(HOST), "focus provider missing");
        Ok(Self {
            core,
            inventory,
            raw,
        })
    }

    fn render(&self, path: &str, context: &ProjectionContext) -> Result<Option<Vec<u8>>> {
        let selected = self
            .core
            .selection_for_assertion(context, &self.inventory)?;
        let Some(input) = selected.inputs.get(path) else {
            ensure!(
                selected.omitted_paths.contains(path),
                "action omitted without predicate"
            );
            return Ok(None);
        };
        ensure!(input.input == path, "unreviewed action alternate input");
        // The omitted branch returns before any source read.
        let bytes = self.core.read_source(path)?;
        let (length, digest) = if path == HOST {
            (267, HOST_DIGEST)
        } else {
            let row = GOLDENS
                .iter()
                .find(|row| row.path == path)
                .ok_or_else(|| eyre::eyre!("unknown bounded action source"))?;
            (row.bytes, row.digest)
        };
        validate_raw(&bytes, length, digest)?;
        Ok(Some(
            render_java_source(std::str::from_utf8(&bytes)?, context)?.into_bytes(),
        ))
    }

    fn assert_leaf(&self, index: usize, context: &ProjectionContext, present: bool) -> Result<()> {
        assert_eq!(
            self.render(PATHS[index], context)?.as_deref(),
            present.then(|| self.raw[GOLDENS[index].oid].as_slice()),
            "{} / {}",
            PATHS[index],
            context.minecraft_version,
        );
        Ok(())
    }
}

fn validate_raw(bytes: &[u8], length: usize, digest: &str) -> Result<()> {
    ensure!(
        bytes.len() == length
            && sha256(bytes) == digest
            && !bytes.contains(&b'\r')
            && bytes.ends_with(b"\n")
            && !bytes.ends_with(b"\n\n"),
        "action raw bytes or exact newline changed"
    );
    std::str::from_utf8(bytes)?;
    Ok(())
}

#[test]
fn human_leaves_reconstruct_twenty_contexts_raw_exact() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut counts = (0, 0);
    for (name, _) in COMMITS {
        let (environment, target) = name.split_once('/').expect("fixed contexts");
        let present = environment == "dev" && is_d2(target);
        let context = fixture
            .core
            .context(target, if present { &ALL_OWNERS } else { &[] })?;
        for index in 0..3 {
            fixture.assert_leaf(index, &context, present)?;
            if present {
                counts.0 += 1;
            } else {
                counts.1 += 1;
            }
        }
    }
    assert_eq!(counts, (6, 54));
    Ok(())
}

#[test]
fn human_leaves_twenty_feature_off_controls_omit_before_input_reads() -> Result<()> {
    let mut fixture = Fixture::load()?;
    let catalog = CoreCatalog::load(&fixture.core.repository, &fixture.core.repository)?;
    assert_eq!(catalog.catalog.0.len(), 20);
    fixture.core.core = fixture
        .core
        .core
        .join("deliberately_missing_human_leaf_boundary");
    for key in catalog.catalog.0.keys() {
        let context = fixture.core.historical_feature_off_catalog_context(key)?;
        for index in 0..3 {
            fixture.assert_leaf(index, &context, false)?;
        }
        assert!(fixture.render(HOST, &context)?.is_none());
    }
    Ok(())
}

#[test]
fn three_human_owners_toggle_independently_without_machine_grants() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut count = 0;
    for target in &TARGETS[..2] {
        for mask in 0_u8..8 {
            let mut enabled = vec!["client_actions", "manager_editor_actions"];
            for (index, owner) in OWNERS.iter().enumerate() {
                if mask & (1 << index) != 0 {
                    enabled.push(owner);
                }
            }
            let context = fixture.core.context(target, &enabled)?;
            for index in 0..3 {
                fixture.assert_leaf(index, &context, mask & (1 << index) != 0)?;
            }
            assert_eq!(fixture.render(HOST, &context)?.is_some(), mask & 2 != 0);
            for forbidden in [
                "typed_command_palette",
                "command_palette",
                "keyboard_profiles",
                "client_program_actions",
                "client_program_consent",
                "packet_values",
            ] {
                assert!(!context.features[forbidden], "invented grant: {forbidden}");
            }
            count += 1;
        }
    }
    assert_eq!(count, 16);
    Ok(())
}

#[test]
fn focus_host_has_exact_union_and_unchanged_body() -> Result<()> {
    let fixture = Fixture::load()?;
    let rules = &fixture.core.metadata.source_rules[HOST];
    ensure!(
        rules.len() == 1
            && rules[0].input == HOST
            // This unchanged rule omits the non-Java opt-in flag. Java is
            // still rendered unconditionally by the production collector.
            && !rules[0].template
            && same(&rules[0].when.targets, &TARGETS[..2])
            && rules[0].when.all_features.is_empty()
            && same(
                &rules[0].when.any_features,
                &["typed_command_palette", "focus_target_actions"]
            )
            && rules[0].when.none_features.is_empty(),
        "focus provider owner union changed"
    );
    for target in &TARGETS[..2] {
        for enabled in [
            vec![],
            vec!["client_actions"],
            TYPED.to_vec(),
            vec!["client_actions", "focus_target_actions"],
        ] {
            let context = fixture.core.context(target, &enabled)?;
            let expected = enabled.contains(&"typed_command_palette")
                || enabled.contains(&"focus_target_actions");
            assert_eq!(
                fixture.render(HOST, &context)?.as_deref(),
                expected.then(|| fixture.raw[HOST_OID].as_slice()),
            );
        }
    }
    Ok(())
}

#[test]
fn descriptive_environment_does_not_dispatch_whole_action_classes() -> Result<()> {
    let fixture = Fixture::load()?;
    for target in &TARGETS[..2] {
        for environment in ["release", "dev"] {
            for enabled in [false, true] {
                let mut context = fixture
                    .core
                    .context(target, if enabled { &ALL_OWNERS } else { &[] })?;
                context.environment = environment.to_owned();
                context.projection_key = format!("review/human-actions/{environment}/{target}");
                context.preset = context.projection_key.clone();
                for index in 0..3 {
                    fixture.assert_leaf(index, &context, enabled)?;
                }
            }
        }
    }
    for target in &TARGETS[2..] {
        let context = fixture
            .core
            .context(target, &["client_actions", "manager_editor_actions"])?;
        for index in 0..3 {
            fixture.assert_leaf(index, &context, false)?;
        }
        for owner in OWNERS {
            assert!(
                fixture
                    .core
                    .context(target, &["client_actions", "manager_editor_actions", owner])
                    .is_err()
            );
        }
    }
    Ok(())
}

#[test]
fn invalid_owner_prerequisites_fail_closed_without_default_expansion() -> Result<()> {
    let fixture = Fixture::load()?;
    for target in &TARGETS[..2] {
        for invalid in [
            vec!["clipboard_action_commands"],
            vec!["focus_target_actions"],
            vec!["manager_direct_edit_action"],
            vec!["client_actions", "manager_direct_edit_action"],
        ] {
            assert!(fixture.core.context(target, &invalid).is_err());
        }
    }
    Ok(())
}

#[test]
fn raw_human_policies_and_machine_default_denial_are_retained() -> Result<()> {
    let fixture = Fixture::load()?;
    for row in GOLDENS {
        let source = std::str::from_utf8(&fixture.raw[row.oid])?;
        for forbidden in [
            "programmaticDescriptor(",
            "programmaticHandler(",
            "SFMClientActionProgrammaticHandler",
            "ProcessBuilder",
            "java.nio.file.",
        ] {
            assert!(
                !source.contains(forbidden),
                "action gained machine or process authority"
            );
        }
    }
    let clipboard = std::str::from_utf8(&fixture.raw[GOLDENS[0].oid])?;
    for marker in [
        "StringArgumentType.greedyString()",
        "SFMClientActionAvailability::available",
        "StringArgumentType.getString(context, \"action_tail\").strip()",
        "keyboardHandler.setClipboard(command)",
        "COPIED.getComponent(Component.literal(command))",
    ] {
        assert!(
            clipboard.contains(marker),
            "clipboard contract changed: {marker}"
        );
    }
    let focus = std::str::from_utf8(&fixture.raw[GOLDENS[1].oid])?;
    let captured = focus
        .find("context.originatingHost() instanceof SFMFocusTargetHost captured")
        .expect("captured host contract");
    let fallback = focus
        .find("minecraft.screen instanceof SFMFocusTargetHost current")
        .expect("current host fallback");
    assert!(captured < fallback);
    for marker in [
        "StringArgumentType.word()",
        "focusTargetIds().forEach(suggestions::suggest)",
        "!target.focusTargetIds().contains(targetId)",
        "return target.focusTarget(targetId) ? 1 : 0;",
    ] {
        assert!(focus.contains(marker), "focus contract changed: {marker}");
    }
    let manager = std::str::from_utf8(&fixture.raw[GOLDENS[2].oid])?;
    assert!(manager.contains("context.requireOriginatingHost("));
    assert!(manager.contains("ManagerScreen.class"));
    assert!(manager.contains("target.openProgramEditorFromAction();"));
    let host_context = String::from_utf8(fixture.core.read_source(CONTEXT)?)?;
    let stale = host_context
        .find("if (!originatingHostIsCurrent.getAsBoolean())")
        .expect("stale originating host guard");
    let host_type = host_context
        .find("if (!requiredType.isInstance(originatingHost))")
        .expect("originating host type guard");
    assert!(stale < host_type);
    assert!(
        host_context
            .contains("ORIGINATING_HOST_CHANGED.getComponent().withStyle(ChatFormatting.RED)")
    );
    let registrar = String::from_utf8(fixture.core.read_source(REGISTRAR)?)?;
    for (owner, class) in OWNERS.into_iter().zip([
        "SFMClipboardCopyAction",
        "SFMFocusAction",
        "ManagerEditAction",
    ]) {
        let start = registrar
            .find(&format!("{{% if features.{owner} %}}"))
            .expect("real registrar owner gate");
        let gated = &registrar[start..];
        let end = gated
            .find("{% endif %}")
            .expect("registrar owner closing gate");
        assert!(gated[..end].contains(&format!("{class}::new")));
    }
    for target in &TARGETS[..2] {
        let mut enabled = MACHINE.to_vec();
        enabled.extend(["clipboard_action_commands", "manager_direct_edit_action"]);
        let context = fixture.core.context(target, &enabled)?;
        let base = render_java_source(
            std::str::from_utf8(&fixture.core.read_source(BASE)?)?,
            &context,
        )?;
        for method in ["programmaticDescriptor()", "programmaticHandler()"] {
            let start = base.find(method).expect("machine default method retained");
            assert!(
                base[start..]
                    .split('}')
                    .next()
                    .expect("method body")
                    .contains("return Optional.empty();")
            );
        }
        for index in 0..3 {
            fixture.assert_leaf(index, &context, true)?;
        }
    }
    Ok(())
}
