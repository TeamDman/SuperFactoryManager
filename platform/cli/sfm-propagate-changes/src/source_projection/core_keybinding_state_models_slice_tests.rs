//! Prepared source-only preservation and direct-provider regressions.
//! Run only after independently reviewed promotion; no Java or input dispatch.
//! This slice does not prove the absent profile/storage/screens or full keyboard runtime.
#![cfg(test)]

use super::candidate_lock::checked_file;
use super::context::ProjectionContext;
use super::core_catalog::CoreCatalog;
use super::core_inputs::CORE_ROOT;
use super::core_inputs::collect_core_artifacts;
use super::core_inputs::discover_core_source_files;
use super::core_inputs::select_core_inputs;
use super::core_slice_test_support::CoreTestFixture;
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

const LEDGER: &str = "docs/tasks/sfm-core-keybinding-state-models-slice.json";
const PATHS: [&str; 3] = [
    "src/main/java/ca/teamdman/sfm/client/keybinding/SFMKeyBindingUserState.java",
    "src/main/java/ca/teamdman/sfm/client/keybinding/SFMKeyBindingListModel.java",
    "src/main/java/ca/teamdman/sfm/client/keybinding/SFMKeySequenceCapture.java",
];
const TARGETS: [&str; 10] = [
    "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
    "26.1.2",
];
const ON: [&str; 3] = ["client_actions", "keyboard_profiles", "keybinding_settings"];
const KEYBOARD: [&str; 2] = ["client_actions", "keyboard_profiles"];
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
        path: "src/main/java/ca/teamdman/sfm/client/keybinding/SFMKeyBindingUserState.java",
        owner: "keyboard_profiles",
        oid: "2129a6a61364804a8125d0d84def7c2d705b33f5",
        digest: "sha256:e9860ead1b9e1398d5d2e59d0a4e4ec63dece6afa2df9382a7dac5c1048afe8a",
        bytes: 977,
    },
    Golden {
        path: "src/main/java/ca/teamdman/sfm/client/keybinding/SFMKeyBindingListModel.java",
        owner: "keybinding_settings",
        oid: "bbe26a5dd14bdc27adde70801af2f1c90bf8c4d1",
        digest: "sha256:5f44b68a0c4e92ea117fcf36f59183eaf2126697f3a53293563bbf453dc61079",
        bytes: 3943,
    },
    Golden {
        path: "src/main/java/ca/teamdman/sfm/client/keybinding/SFMKeySequenceCapture.java",
        owner: "keybinding_settings",
        oid: "28ba77a0371b9b61d90bb64d1507f6f29b99a2a7",
        digest: "sha256:61c0f5ab6b4eae5ed1be11942d19832f44e0814ef11abfbaf7565f189bc1afc1",
        bytes: 5149,
    },
];
#[derive(Clone, Copy)]
struct Provider {
    path: &'static str,
    digest: &'static str,
    bytes: usize,
    d2_only: bool,
}
const PROVIDERS: [Provider; 5] = [
    Provider {
        path: "src/main/java/ca/teamdman/sfm/client/keybinding/SFMKeyBinding.java",
        digest: "sha256:d6316750e06a542d2a09e8b0d551cde13acafedd08a8c4e0ac73bd1eabafa128",
        bytes: 1131,
        d2_only: false,
    },
    Provider {
        path: "src/main/java/ca/teamdman/sfm/client/keybinding/SFMKeyStroke.java",
        digest: "sha256:225efdea8693a16e1dadd870e0b5a7fd3529b31b8d8a16a6bb0f053c93ddd30a",
        bytes: 601,
        d2_only: false,
    },
    Provider {
        path: "src/main/java/ca/teamdman/sfm/client/keybinding/SFMKeySequence.java",
        digest: "sha256:58b0d1d84d9accfd3f3c24d346f284276a3e44613e5d73f236411b8bbbbea732",
        bytes: 437,
        d2_only: false,
    },
    Provider {
        path: "src/main/java/ca/teamdman/sfm/client/keybinding/SFMKeyModifier.java",
        digest: "sha256:ba8c53488b937c0fdef8147c5ca0faf263167c481d897a79bf92997a994d3831",
        bytes: 118,
        d2_only: false,
    },
    Provider {
        path: "src/main/java/ca/teamdman/sfm/client/keybinding/SFMKeyBindingOverride.java",
        digest: "sha256:125730bddd4a27915b8210c36e46e234960ac3d830cad20935e403235f440199",
        bytes: 1267,
        d2_only: true,
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
fn is_d2(target: &str) -> bool {
    matches!(target, "1.19.2" | "1.19.4")
}
fn pinned_commits() -> BTreeMap<String, String> {
    COMMITS
        .into_iter()
        .map(|(c, h)| (c.to_owned(), h.to_owned()))
        .collect()
}
fn validate_bytes(body: &[u8], bytes: usize, digest: &str) -> Result<()> {
    ensure!(
        body.len() == bytes && sha256(body) == digest,
        "model/provider bytes changed"
    );
    ensure!(
        !body.contains(&b'\r')
            && body.ends_with(b"\n")
            && !body.ends_with(b"\n\n")
            && !body.starts_with(&[0xef, 0xbb, 0xbf]),
        "model/provider raw framing changed"
    );
    std::str::from_utf8(body)?;
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
            ledger.schema == "sfm:core_keybinding_state_models_slice@1"
                && ledger.context_commits == pinned_commits()
                && ledger.normalization == "none_raw_exact_lf_with_exactly_one_terminal_lf"
                && ledger.files.len() == 3,
            "model ledger scope changed"
        );
        let keyboard = &core.features.0["keyboard_profiles"];
        let settings = &core.features.0["keybinding_settings"];
        ensure!(
            same(&keyboard.supported_targets, &TARGETS)
                && same(&keyboard.requires, &["client_actions"])
                && same(&settings.supported_targets, &TARGETS[..2])
                && same(&settings.requires, &["keyboard_profiles"]),
            "existing model owners changed"
        );
        let mut seen = BTreeSet::new();
        for leaf in ledger.files {
            let golden = GOLDENS
                .iter()
                .find(|g| g.path == leaf.path)
                .ok_or_else(|| eyre::eyre!("unexpected model path"))?;
            ensure!(
                seen.insert(leaf.path.clone())
                    && leaf.core_path == format!("{CORE_ROOT}/{}", leaf.path)
                    && leaf.template_sha256 == golden.digest
                    && leaf.template_bytes == golden.bytes
                    && leaf.raw_blob == golden.oid
                    && leaf.raw_sha256 == golden.digest
                    && leaf.raw_bytes == golden.bytes
                    && leaf.normalization == "none"
                    && leaf.line_endings == "lf"
                    && leaf.terminal_newline == "exactly_one_lf"
                    && same(&leaf.membership.targets, &TARGETS[..2])
                    && same(&leaf.membership.all_features, &[golden.owner])
                    && leaf.membership.any_features.is_empty()
                    && leaf.membership.none_features.is_empty(),
                "model raw contract changed"
            );
            let rules = core
                .metadata
                .source_rules
                .get(golden.path)
                .ok_or_else(|| eyre::eyre!("model owner rule missing"))?;
            ensure!(
                rules.len() == 1
                    && rules[0].input == golden.path
                    && rules[0].template
                    && same(&rules[0].when.targets, &TARGETS[..2])
                    && same(&rules[0].when.all_features, &[golden.owner])
                    && rules[0].when.any_features.is_empty()
                    && rules[0].when.none_features.is_empty(),
                "model owner rule changed"
            );
            ensure!(leaf.witnesses.len() == 20, "model frozen cells missing");
            let mut contexts = BTreeSet::new();
            for witness in leaf.witnesses {
                let (environment, target) = witness
                    .context
                    .split_once('/')
                    .ok_or_else(|| eyre::eyre!("invalid model context"))?;
                let present = environment == "dev" && is_d2(target);
                let features = if present { &ON[..] } else { &[][..] };
                ensure!(
                    contexts.insert(witness.context.clone())
                        && pinned_commits().get(&witness.context) == Some(&witness.source_commit)
                        && witness.present == present
                        && witness.raw_blob.as_deref() == present.then_some(golden.oid)
                        && witness.raw_sha256.as_deref() == present.then_some(golden.digest)
                        && witness.raw_bytes == present.then_some(golden.bytes)
                        && witness.mode.as_deref() == present.then_some("100644")
                        && same(&witness.explicit_registered_features, features)
                        && witness.features_origin
                            == "reviewed_current_explicit_reconstruction_not_historical_manifest",
                    "model historical witness changed"
                );
                core.context(target, features)?;
            }
        }
        ensure!(
            seen == PATHS.into_iter().map(str::to_owned).collect(),
            "model path scope changed"
        );
        for provider in PROVIDERS {
            let rules = core
                .metadata
                .source_rules
                .get(provider.path)
                .ok_or_else(|| eyre::eyre!("actual provider rule absent"))?;
            let targets = if provider.d2_only {
                &TARGETS[..2]
            } else {
                &[][..]
            };
            ensure!(
                rules.len() == 1
                    && rules[0].input == provider.path
                    && rules[0].template
                    && same(&rules[0].when.targets, targets)
                    && same(&rules[0].when.all_features, &["keyboard_profiles"])
                    && rules[0].when.any_features.is_empty()
                    && rules[0].when.none_features.is_empty(),
                "real provider contract changed"
            );
            validate_bytes(
                &core.read_source(provider.path)?,
                provider.bytes,
                provider.digest,
            )?;
        }
        let raw = read_git_blobs(
            &core.repository,
            &GOLDENS.iter().map(|g| g.oid.to_owned()).collect(),
        )?;
        for golden in GOLDENS {
            validate_bytes(&raw[golden.oid], golden.bytes, golden.digest)?;
        }
        let inventory = discover_core_source_files(&core.core)?;
        ensure!(
            PATHS.iter().all(|p| inventory.contains(*p))
                && PROVIDERS.iter().all(|p| inventory.contains(p.path)),
            "authored model/provider absent"
        );
        Ok(Self {
            core,
            inventory,
            raw,
        })
    }
    fn render(&self, index: usize, context: &ProjectionContext) -> Result<Option<Vec<u8>>> {
        let path = PATHS[index];
        let selected = self
            .core
            .selection_for_assertion(context, &self.inventory)?;
        let Some(input) = selected.inputs.get(path) else {
            ensure!(
                selected.omitted_paths.contains(path),
                "model omitted without rule"
            );
            return Ok(None);
        };
        ensure!(
            input.input == path && input.template,
            "unexpected model alternate source"
        );
        let bytes = self.core.read_source(path)?;
        let golden = GOLDENS[index];
        validate_bytes(&bytes, golden.bytes, golden.digest)?;
        Ok(Some(
            render_java_source(std::str::from_utf8(&bytes)?, context)?.into_bytes(),
        ))
    }
    fn assert_context(&self, target: &str, context: &ProjectionContext) -> Result<()> {
        let selected = self
            .core
            .selection_for_assertion(context, &self.inventory)?;
        for (index, golden) in GOLDENS.iter().enumerate() {
            let present = is_d2(target) && context.features[golden.owner];
            assert_eq!(
                self.render(index, context)?.as_deref(),
                present.then(|| self.raw[golden.oid].as_slice()),
                "{}/{}",
                golden.path,
                target
            );
            if present {
                for provider in PROVIDERS {
                    // A leaf only needs its own referenced types; the selected keyboard
                    // cohort is a stronger existing-provider inclusion witness, not a new prereq.
                    assert!(
                        selected.inputs.contains_key(provider.path),
                        "real provider absent: {}",
                        provider.path
                    );
                }
            }
        }
        Ok(())
    }
}

#[test]
fn models_reconstruct_exact_sixty_frozen_memberships_and_git_trees() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut present = 0;
    let mut absent = 0;
    for (context, commit) in COMMITS {
        let (environment, target) = context.split_once('/').expect("fixed model context");
        let on = environment == "dev" && is_d2(target);
        let explicit = fixture
            .core
            .context(target, if on { &ON[..] } else { &[][..] })?;
        fixture.assert_context(target, &explicit)?;
        let mut command = frozen_git_command(&fixture.core.repository);
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
        let output = command.output()?;
        ensure!(
            output.status.success() && output.stdout.len() <= 4096,
            "bounded model tree query failed"
        );
        let mut found = BTreeMap::new();
        for line in std::str::from_utf8(&output.stdout)?.lines() {
            let (header, path) = line
                .split_once('\t')
                .ok_or_else(|| eyre::eyre!("invalid model tree framing"))?;
            let fields = header.split_whitespace().collect::<Vec<_>>();
            ensure!(
                fields.len() == 3 && fields[0] == "100644" && fields[1] == "blob",
                "unexpected model historical mode/type"
            );
            ensure!(
                found
                    .insert(path.to_owned(), fields[2].to_owned())
                    .is_none(),
                "duplicate model historical path"
            );
        }
        for golden in GOLDENS {
            assert_eq!(
                found
                    .get(&format!("platform/minecraft/{}", golden.path))
                    .map(String::as_str),
                on.then_some(golden.oid)
            );
            if on {
                present += 1;
            } else {
                absent += 1;
            }
        }
    }
    assert_eq!((present, absent), (6, 54));
    Ok(())
}

#[test]
fn models_twenty_feature_off_controls_omit_before_source_reads() -> Result<()> {
    let mut fixture = Fixture::load()?;
    let catalog = CoreCatalog::load(&fixture.core.repository, &fixture.core.repository)?;
    assert_eq!(catalog.catalog.0.len(), 20);
    fixture.core.core = fixture
        .core
        .core
        .join("deliberately_missing_keybinding_model_sources");
    let mut cells = 0;
    for key in catalog.catalog.0.keys() {
        let context = fixture.core.historical_feature_off_catalog_context(key)?;
        for index in 0..3 {
            assert!(fixture.render(index, &context)?.is_none());
            cells += 1;
        }
    }
    assert_eq!(cells, 60);
    Ok(())
}

#[test]
fn models_explicit_owners_do_not_invent_other_keyboard_features() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut masks = 0;
    for target in TARGETS {
        for enabled in [&[][..], &["client_actions"][..], &KEYBOARD[..]] {
            fixture.assert_context(target, &fixture.core.context(target, enabled)?)?;
            masks += 1;
        }
        if is_d2(target) {
            for enabled in [
                &ON[..],
                &[
                    "client_actions",
                    "keyboard_profiles",
                    "keybinding_settings",
                    "client_theme",
                    "workspace_panels",
                ][..],
            ] {
                let explicit = fixture.core.context(target, enabled)?;
                fixture.assert_context(target, &explicit)?;
                for forbidden in [
                    "typed_command_palette",
                    "client_program_actions",
                    "packet_values",
                ] {
                    assert!(!explicit.features[forbidden]);
                }
                masks += 1;
            }
        }
    }
    assert_eq!(masks, 34);
    Ok(())
}

#[test]
fn models_real_provider_members_supply_each_direct_reference() -> Result<()> {
    let fixture = Fixture::load()?;
    for target in &TARGETS[..2] {
        let context = fixture.core.context(target, &ON)?;
        fixture.assert_context(target, &context)?;
        let binding = PROVIDERS[0];
        let rendered = render_java_source(
            std::str::from_utf8(&fixture.core.read_source(binding.path)?)?,
            &context,
        )?;
        for marker in [
            "ResourceLocation situationId,",
            "public SFMKeyBinding withEnabled(boolean value)",
        ] {
            assert!(
                rendered.contains(marker),
                "actual D2 binding lacks {marker}"
            );
        }
        for provider in PROVIDERS {
            let body = render_java_source(
                std::str::from_utf8(&fixture.core.read_source(provider.path)?)?,
                &context,
            )?;
            assert!(
                !body.contains("{%"),
                "provider retained template directives"
            );
        }
    }
    Ok(())
}

#[test]
fn models_old_targets_and_missing_existing_prerequisites_fail_closed() -> Result<()> {
    let fixture = Fixture::load()?;
    for target in &TARGETS[2..] {
        assert!(fixture.core.context(target, &ON).is_err());
        fixture.assert_context(target, &fixture.core.context(target, &KEYBOARD)?)?;
    }
    for target in &TARGETS[..2] {
        for invalid in [
            &["keyboard_profiles"][..],
            &["keybinding_settings"][..],
            &["client_actions", "keybinding_settings"][..],
            &[
                "client_actions",
                "keyboard_profiles",
                "keybinding_state_unregistered",
            ][..],
        ] {
            assert!(fixture.core.context(target, invalid).is_err());
        }
    }
    Ok(())
}

#[test]
fn models_environment_and_projection_names_are_descriptive_only() -> Result<()> {
    let fixture = Fixture::load()?;
    for target in &TARGETS[..2] {
        for environment in ["release", "dev"] {
            let mut context = fixture.core.context(target, &ON)?;
            context.environment = environment.to_owned();
            context.projection_key = format!("review/models/{environment}/{target}");
            context.preset = context.projection_key.clone();
            fixture.assert_context(target, &context)?;
        }
    }
    Ok(())
}

#[test]
fn models_keep_data_contracts_without_dispatch_polling_or_persistence() -> Result<()> {
    let fixture = Fixture::load()?;
    let contracts: [(&str, &[&str]); 3] = [
        (
            PATHS[0],
            &[
                "bindings = List.copyOf(bindings);",
                "defaultOverrides = Map.copyOf(defaultOverrides);",
                "tombstones = Set.copyOf(tombstones);",
                "defaultFingerprints = Map.copyOf(defaultFingerprints);",
            ],
        ),
        (
            PATHS[1],
            &[
                "enum SortColumn { NAME, BINDING_COUNT }",
                "enum Direction { ASCENDING, DESCENDING }",
                "binding.situationId().equals(situationFilter)",
                "thenComparing(ResourceLocation::toString)",
            ],
        ),
        (
            PATHS[2],
            &[
                "ESCAPE_WINDOW_MILLIS = 3000L",
                "pendingEscapes >= 3",
                "EscapeResult.CANCELLED",
                "SFMKeyStroke.of(GLFW.GLFW_KEY_ESCAPE)",
                "public void capture(int keyCode, Set<SFMKeyModifier> modifiers, long nowMillis)",
            ],
        ),
    ];
    for (index, (path, required)) in contracts.into_iter().enumerate() {
        let body = std::str::from_utf8(&fixture.raw[GOLDENS[index].oid])?;
        for marker in required {
            assert!(
                body.contains(marker),
                "lost model contract: {path}/{marker}"
            );
        }
        for forbidden in [
            "java.nio.file.",
            "java.io.",
            "ProcessBuilder",
            "Runtime.getRuntime",
            "Minecraft.getInstance",
            "glfwGet",
            "SFMClientActionExecutor",
            "SFMKeyBindingService",
            "SFMPacket",
            "setClipboard",
        ] {
            assert!(
                !body.contains(forbidden),
                "model gained effect: {path}/{forbidden}"
            );
        }
        assert!(!body.contains("{%") && !body.contains("{#"));
    }
    Ok(())
}

#[test]
fn models_actual_collector_omits_malformed_off_sources_and_rejects_them_when_enabled() -> Result<()>
{
    let fixture = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    std::fs::create_dir_all(&root)?;
    let mut metadata = fixture.core.metadata.clone();
    metadata
        .source_rules
        .retain(|p, _| PATHS.contains(&p.as_str()));
    let inventory = PATHS
        .into_iter()
        .map(str::to_owned)
        .collect::<BTreeSet<_>>();
    let off = fixture.core.context("1.19.2", &[])?;
    let off_selected = select_core_inputs(&metadata, &off, &inventory)?;
    for (output, input) in &off_selected.inputs {
        ensure!(!output.starts_with("src/"), "off selected a model");
        let source = read_bounded(
            &checked_file(&fixture.core.core, &input.input)?,
            16 * 1024 * 1024,
        )?;
        let destination = root.join(&input.input);
        std::fs::create_dir_all(destination.parent().expect("selected input parent"))?;
        std::fs::write(destination, source)?;
    }
    for path in PATHS {
        let destination = root.join(path);
        std::fs::create_dir_all(destination.parent().expect("model parent"))?;
        std::fs::write(destination, [0xff, 0xfe])?;
    }
    let omitted = collect_core_artifacts(&root, &off_selected, &off)?;
    assert!(PATHS.iter().all(|path| !omitted.contains_key(*path)));
    let on = fixture.core.context("1.19.2", &ON)?;
    let selected = select_core_inputs(&metadata, &on, &inventory)?;
    assert!(collect_core_artifacts(&root, &selected, &on).is_err());
    for path in PATHS {
        std::fs::write(root.join(path), fixture.core.read_source(path)?)?;
    }
    let artifacts = collect_core_artifacts(&root, &selected, &on)?;
    for golden in GOLDENS {
        assert!(selected.inputs[golden.path].template);
        assert_eq!(artifacts[golden.path].source_bytes, fixture.raw[golden.oid]);
        assert!(
            artifacts[golden.path]
                .output_bytes
                .ends_with(&fixture.raw[golden.oid])
        );
    }
    Ok(())
}
