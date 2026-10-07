//! Pure client-expression/descriptor source preservation through actual core inputs.
//!
//! Git is a bounded offline test witness, never a production source provider.
//! These proofs do not invoke Java, evaluate client programs, grant consent,
//! dispatch actions, or claim feature-on compilation/runtime acceptance.
#![cfg(test)]

use super::context::ProjectionContext;
use super::core_inputs::CORE_ROOT;
use super::core_inputs::InputVariant;
use super::core_inputs::collect_core_artifacts;
use super::core_inputs::discover_core_source_files;
use super::core_inputs::select_core_inputs;
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
use std::fs;

const LEDGER: &str = "docs/tasks/sfm-core-client-expression-descriptor-slice.json";
const OWNER: &str = "client_program_actions";
const TARGETS: [&str; 10] = [
    "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
    "26.1.2",
];
const PATHS: [&str; 3] = [
    "src/main/java/ca/teamdman/sfml/ast/ClientValueExpression.java",
    "src/main/java/ca/teamdman/sfml/ast/BoolClientValueEquals.java",
    "src/main/java/ca/teamdman/sfm/client/action/SFMClientActionDescriptor.java",
];
const RAW: [(&str, usize, &str); 3] = [
    (
        "4f16c4b74bfedfeef2cfe46970c4de6fa001af45",
        1626,
        "sha256:80ffa8e7eb8ed7ccd1d3150a78d70c54c1ea57d98edfa7a9cc5e35c6a8f4ab29",
    ),
    (
        "287588676a413ac2bc5fd5756c0ff78eb30884d5",
        811,
        "sha256:610f20eab3aaa2afdce2e5a6476774f2e79bbbdcaaa429c14ea489ab27ce6932",
    ),
    (
        "32b5ce57141ee67b112cb3cdb6224e34e2aeffb2",
        4903,
        "sha256:f12856bbfdc1f316233f9ff5e72a9361d4bf89bba3aae5414b9adfdc8ab8b8aa",
    ),
];
const CLOSED_OWNER: [&str; 9] = [
    "client_actions",
    "client_manager",
    "client_program_actions",
    "client_program_consent",
    "disk_readonly_access",
    "packet_computation",
    "packet_values",
    "runtime_resource_cleanup",
    "sfml_execution_side",
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

#[derive(Facet)]
struct Ledger {
    schema: String,
    normalization: String,
    owner: String,
    definitions: BTreeMap<String, Definition>,
    context_commits: BTreeMap<String, String>,
    files: Vec<File>,
}
#[derive(Facet)]
struct Definition {
    supported_targets: Vec<String>,
    requires: Vec<String>,
}
#[derive(Facet)]
struct File {
    intended_core_path: String,
    stage_bytes: usize,
    stage_sha256: String,
    git_blob: String,
    witnesses: Vec<Witness>,
}
#[derive(Facet)]
struct Witness {
    context: String,
    commit: String,
    present: bool,
    git_blob: Option<String>,
    raw_bytes: usize,
    raw_sha256: Option<String>,
}

struct Fixture {
    shared: CoreTestFixture,
    inventory: BTreeSet<String>,
    sources: Vec<Vec<u8>>,
    raw: BTreeMap<String, Vec<u8>>,
}
impl Fixture {
    fn load() -> Result<Self> {
        let shared = CoreTestFixture::load()?;
        let ledger_bytes = read_bounded(&shared.repository.join(LEDGER), 128 * 1024)?;
        let ledger: Ledger = facet_json::from_str(std::str::from_utf8(&ledger_bytes)?)?;
        ensure!(
            ledger.schema == "sfm:core_client_expression_descriptor_slice@1"
                && ledger.normalization == "none"
                && ledger.owner == OWNER,
            "bounded pure client leaf evidence changed"
        );
        let expected_contexts = CONTEXTS
            .into_iter()
            .map(|(name, commit)| (name.to_owned(), commit.to_owned()))
            .collect::<BTreeMap<_, _>>();
        ensure!(
            ledger.context_commits == expected_contexts
                && ledger.files.len() == 3
                && ledger.definitions.len() == 9,
            "frozen cohort/context/definition count changed"
        );
        let expected_defs: BTreeMap<String, Definition> = facet_json::from_str(
            r#"{"client_program_actions":{"supported_targets":["1.19.2","1.19.4"],"requires":["client_actions","packet_values","sfml_execution_side","client_manager","client_program_consent","packet_computation"]},"client_actions":{"supported_targets":["1.19.2","1.19.4","1.20","1.20.1","1.20.2","1.20.3","1.20.4","1.21.0","1.21.1","26.1.2"],"requires":[]},"packet_values":{"supported_targets":["1.19.2","1.19.4"],"requires":[]},"sfml_execution_side":{"supported_targets":["1.19.2","1.19.4"],"requires":[]},"client_manager":{"supported_targets":["1.19.2","1.19.4"],"requires":["sfml_execution_side","client_program_consent","disk_readonly_access"]},"client_program_consent":{"supported_targets":["1.19.2","1.19.4"],"requires":["sfml_execution_side"]},"packet_computation":{"supported_targets":["1.19.2","1.19.4"],"requires":["packet_values","runtime_resource_cleanup"]},"disk_readonly_access":{"supported_targets":["1.19.2","1.19.4","1.20","1.20.1","1.20.2","1.20.3","1.20.4","1.21.0","1.21.1","26.1.2"],"requires":[]},"runtime_resource_cleanup":{"supported_targets":["1.19.2","1.19.4"],"requires":[]}}"#,
        )?;
        for (name, expected) in &expected_defs {
            let frozen = ledger
                .definitions
                .get(name)
                .ok_or_else(|| eyre::eyre!("frozen prerequisite absent"))?;
            let current = shared
                .features
                .0
                .get(name)
                .ok_or_else(|| eyre::eyre!("actual prerequisite absent"))?;
            ensure!(
                frozen.supported_targets == expected.supported_targets
                    && frozen.requires == expected.requires
                    && current.supported_targets == expected.supported_targets
                    && current.requires == expected.requires,
                "actual or witnessed owner/prerequisite drift: {name}"
            );
        }
        let sources = PATHS
            .iter()
            .map(|p| shared.read_source(p))
            .collect::<Result<Vec<_>>>()?;
        let raw = read_git_blobs(
            &shared.repository,
            &RAW.iter().map(|r| r.0.to_owned()).collect(),
        )?;
        let inventory = discover_core_source_files(&shared.core)?;
        for (index, file) in ledger.files.iter().enumerate() {
            let (oid, count, digest) = RAW[index];
            ensure!(
                file.intended_core_path == PATHS[index]
                    && file.stage_bytes == count
                    && file.stage_sha256 == digest
                    && file.git_blob == oid
                    && file.witnesses.len() == 20,
                "pure leaf source identity/scope changed"
            );
            for bytes in [&sources[index], &raw[oid]] {
                ensure!(
                    bytes.len() == count
                        && sha256(bytes) == digest
                        && !bytes.contains(&b'\r')
                        && bytes.ends_with(b"\n")
                        && !bytes.starts_with(&[0xef, 0xbb, 0xbf]),
                    "raw LF leaf body changed"
                );
            }
            ensure!(
                sources[index] == raw[oid],
                "source must preserve actual frozen Git bytes"
            );
            let rules: &[InputVariant] = shared
                .metadata
                .source_rules
                .get(PATHS[index])
                .ok_or_else(|| eyre::eyre!("actual sparse source rule absent"))?;
            ensure!(
                rules.len() == if index == 2 { 2 } else { 1 }
                    && rules[0].input == PATHS[index]
                    && rules[0].template
                    && rules[0].when.targets == ["1.19.2", "1.19.4"]
                    && rules[0].when.all_features == [OWNER]
                    && rules[0].when.any_features.is_empty()
                    && rules[0].when.none_features.is_empty(),
                "leaf source membership drift"
            );
            if index == 2 {
                let packet = &rules[1];
                ensure!(
                    packet.input == PATHS[index]
                        && packet.template
                        && packet.when.targets == ["1.19.2", "1.19.4"]
                        && packet.when.all_features
                            == [
                                "client_actions",
                                "packet_actions",
                                "packet_transport_private"
                            ]
                        && packet.when.any_features.is_empty()
                        && packet.when.none_features == [OWNER],
                    "disjoint private-packet descriptor membership drift"
                );
            }
            // Java is automatically rendered, regardless of the optional template hint.
            let mut seen = BTreeSet::new();
            for row in &file.witnesses {
                let expected = matches!(row.context.as_str(), "dev/1.19.2" | "dev/1.19.4");
                ensure!(
                    seen.insert(row.context.as_str())
                        && expected_contexts.get(&row.context) == Some(&row.commit)
                        && row.present == expected
                        && row.git_blob.as_deref() == expected.then_some(oid)
                        && row.raw_bytes == if expected { count } else { 0 }
                        && row.raw_sha256.as_deref() == expected.then_some(digest),
                    "frozen tree/byte leaf witness changed"
                );
            }
            ensure!(
                seen == expected_contexts.keys().map(String::as_str).collect(),
                "frozen witness membership incomplete"
            );
        }
        Ok(Self {
            shared,
            inventory,
            sources,
            raw,
        })
    }
    fn context(&self, target: &str, names: &[&str]) -> Result<ProjectionContext> {
        self.shared.context(target, names)
    }
    fn render(&self, index: usize, context: &ProjectionContext) -> Result<Option<String>> {
        let selection = select_core_inputs(&self.shared.metadata, context, &self.inventory)?;
        let path = PATHS[index];
        let Some(selected) = selection.inputs.get(path) else {
            ensure!(
                selection.omitted_paths.contains(path),
                "leaf omission is not explicit"
            );
            return Ok(None);
        };
        ensure!(
            selected.input == path,
            "pure leaf routed through an alternate historical source"
        );
        Ok(Some(render_java_source(
            std::str::from_utf8(&self.sources[index])?,
            context,
        )?))
    }
    fn render_other(&self, path: &str, context: &ProjectionContext) -> Result<String> {
        let selection = select_core_inputs(&self.shared.metadata, context, &self.inventory)?;
        let selected = selection
            .inputs
            .get(path)
            .ok_or_else(|| eyre::eyre!("actual consumer source omitted"))?;
        let bytes = self.shared.read_source(&selected.input)?;
        render_java_source(std::str::from_utf8(&bytes)?, context)
    }
}

#[test]
fn private_packet_descriptor_masks_preserve_source_without_enabling_client_programs() -> Result<()>
{
    let f = Fixture::load()?;
    for target in &TARGETS[..2] {
        for mask in 0..8 {
            let mut names = vec![
                "packet_values",
                "runtime_resource_cleanup",
                "packet_computation",
            ];
            for (bit, name) in [
                (1, "client_actions"),
                (2, "packet_actions"),
                (4, "packet_transport_private"),
            ] {
                if mask & bit != 0 {
                    names.push(name);
                }
            }
            let context = f.context(target, &names)?;
            assert!(!context.features[OWNER]);
            assert!(!context.features["client_manager"]);
            assert!(!context.features["client_frame_render"]);
            for index in 0..2 {
                assert!(f.render(index, &context)?.is_none());
            }
            let descriptor = f.render(2, &context)?;
            assert_eq!(descriptor.is_some(), mask == 7);
            if let Some(body) = descriptor {
                assert_eq!(body.as_bytes(), f.raw[RAW[2].0]);
                let action = f.render_other(
                    "src/main/java/ca/teamdman/sfm/client/action/SFMPacketSendAction.java",
                    &context,
                )?;
                for required in [
                    "PROGRAMMATIC_DESCRIPTOR = createProgrammaticDescriptor()",
                    "SFMPacketSendAction::resolveProgrammaticScope",
                    "SFMValueSchema.Field.required",
                    "LOCAL_TRANSPORT_ATTEMPT_ONLY",
                    "target_unauthorized",
                ] {
                    assert!(action.contains(required), "{required}");
                }
                assert!(!action.contains("programmaticDescriptor()"));
                assert!(!action.contains("programmaticHandler()"));
            }
        }
        let mut combined = CLOSED_OWNER.to_vec();
        combined.extend(["packet_actions", "packet_transport_private"]);
        let context = f.context(target, &combined)?;
        // Both consumers enabled must still select exactly one descriptor rule.
        for index in 0..3 {
            assert_eq!(
                f.render(index, &context)?.unwrap().as_bytes(),
                f.raw[RAW[index].0]
            );
        }
    }
    Ok(())
}

#[test]
fn sixty_frozen_context_cells_select_exact_raw_bodies_or_explicitly_omit() -> Result<()> {
    let f = Fixture::load()?;
    let mut present = 0;
    let mut absent = 0;
    for (name, _) in CONTEXTS {
        let (kind, target) = name
            .split_once('/')
            .ok_or_else(|| eyre::eyre!("bad context"))?;
        let expected = kind == "dev" && matches!(target, "1.19.2" | "1.19.4");
        let mut c = f.context(target, if expected { &CLOSED_OWNER } else { &[] })?;
        c.environment = kind.to_owned();
        c.projection_key = format!("witness/pure-client-leaves/{name}");
        c.preset = c.projection_key.clone();
        for index in 0..3 {
            let body = f.render(index, &c)?;
            assert_eq!(
                body.as_deref().map(str::as_bytes),
                expected.then(|| f.raw[RAW[index].0].as_slice()),
                "{name} {}",
                PATHS[index]
            );
            if expected {
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
fn independent_owner_frame_masks_and_invalid_owner_requests_do_not_widen_support() -> Result<()> {
    let f = Fixture::load()?;
    for target in &TARGETS[..2] {
        for mask in 0..4 {
            let mut names = CLOSED_OWNER
                .into_iter()
                .filter(|name| *name != OWNER || mask & 1 != 0)
                .collect::<Vec<_>>();
            if mask & 2 != 0 {
                names.push("client_frame_language");
            }
            let mut c = f.context(target, &names)?;
            c.environment = "release".to_owned();
            c.projection_key = "descriptive-release-label-is-not-source-routing".to_owned();
            for index in 0..3 {
                let body = f.render(index, &c)?;
                assert_eq!(body.is_some(), mask & 1 != 0);
                if let Some(body) = body {
                    assert_eq!(body.as_bytes(), f.raw[RAW[index].0]);
                }
            }
            assert!(!c.features["client_frame_render"]);
        }
        assert!(f.context(target, &[OWNER]).is_err());
        for dependency in &f.shared.features.0[OWNER].requires {
            let names = CLOSED_OWNER
                .into_iter()
                .filter(|name| *name != dependency.as_str())
                .collect::<Vec<_>>();
            assert!(
                f.context(target, &names).is_err(),
                "missing exact prerequisite {dependency}"
            );
        }
    }
    for target in &TARGETS[2..] {
        assert!(f.context(target, &CLOSED_OWNER).is_err());
        for index in 0..3 {
            assert!(f.render(index, &f.context(target, &[])?)?.is_none());
        }
    }
    assert!(
        f.context("1.19.2", &["unreviewed_client_leaf_owner"])
            .is_err()
    );
    Ok(())
}

#[test]
fn real_parser_program_and_basic_action_consumers_follow_the_existing_owner() -> Result<()> {
    let f = Fixture::load()?;
    for target in &TARGETS[..2] {
        let on = f.context(target, &CLOSED_OWNER)?;
        let off_names = CLOSED_OWNER
            .into_iter()
            .filter(|name| *name != OWNER)
            .collect::<Vec<_>>();
        let off = f.context(target, &off_names)?;
        for path in [
            "src/main/java/ca/teamdman/sfml/ast/ASTBuilder.java",
            "src/main/java/ca/teamdman/sfml/ast/Program.java",
        ] {
            let enabled = f.render_other(path, &on)?;
            let disabled = f.render_other(path, &off)?;
            assert!(
                enabled.contains("ClientValueExpression")
                    && enabled.contains("BoolClientValueEquals")
            );
            assert!(
                !disabled.contains("ClientValueExpression")
                    && !disabled.contains("BoolClientValueEquals")
            );
        }
        let path = "src/main/java/ca/teamdman/sfm/client/action/SFMClientAction.java";
        let enabled = f.render_other(path, &on)?;
        let disabled = f.render_other(path, &off)?;
        assert!(enabled.contains("Optional<SFMClientActionDescriptor> programmaticDescriptor()"));
        assert!(
            !disabled.contains("programmaticDescriptor()")
                && !disabled.contains("SFMClientActionDescriptor")
        );
        // The separate handler/context/authorization runtime closure is not supplied by these leaves.
        assert!(
            enabled.contains("Optional<SFMClientActionProgrammaticHandler> programmaticHandler()")
        );
        assert!(!on.features["client_frame_language"]);
    }
    Ok(())
}

#[test]
fn raw_server_refusal_and_descriptor_schema_order_are_preserved_not_executed() -> Result<()> {
    let f = Fixture::load()?;
    let expr = std::str::from_utf8(&f.sources[0])?;
    let compare = std::str::from_utf8(&f.sources[1])?;
    assert!(expr.contains(
        "throw new IllegalStateException(\"Client value expressions require a Client Manager\")"
    ));
    assert!(compare.contains(
        "throw new IllegalStateException(\"Client value comparison requires a Client Manager\")"
    ));
    for body in [expr, compare] {
        for forbidden in [
            "Minecraft.getInstance()",
            "getServer()",
            "ClientFrameEvaluator",
            "Files.",
            "ProcessBuilder",
        ] {
            assert!(!body.contains(forbidden), "{forbidden}");
        }
    }
    let descriptor = std::str::from_utf8(&f.sources[2])?;
    let validation = descriptor
        .find("inputSchema.validate(input)")
        .ok_or_else(|| eyre::eyre!("schema guard absent"))?;
    let resolver = descriptor
        .find("dataScopeResolver.resolve(input)")
        .ok_or_else(|| eyre::eyre!("scope resolver absent"))?;
    assert!(validation < resolver);
    for required in [
        "dataScopes.size() > 32",
        "scope_resolution_failed",
        "unknown_status",
        "Map.copyOf(sorted)",
        "A descriptor is not",
        "SERVER_ACCEPTANCE_ONLY",
    ] {
        assert!(descriptor.contains(required), "{required}");
    }
    for forbidden in [
        "ClientProgramConsentGate",
        "ClientProgramConsentStore",
        "ClientManagerFrameRuntime",
        "SFMClientProgramActionDispatcher",
        "Minecraft.getInstance()",
        "Files.",
        "ProcessBuilder",
    ] {
        assert!(!descriptor.contains(forbidden), "{forbidden}");
    }
    // Marker checks preserve source contracts, not evaluation or authorization behavior.
    Ok(())
}

#[test]
fn common_authored_edits_collect_through_fixed_core_and_keep_raw_inputs_unchanged() -> Result<()> {
    let f = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    let marker = "// One shared pure-client leaf edit.\n";
    let mut metadata = f.shared.metadata.clone();
    metadata
        .source_rules
        .retain(|path, _| PATHS.contains(&path.as_str()));
    let inventory = PATHS
        .into_iter()
        .map(str::to_owned)
        .collect::<BTreeSet<_>>();
    for index in 0..3 {
        let source = std::str::from_utf8(&f.sources[index])?;
        let edited = source.replacen("\n\n", &format!("\n\n{marker}"), 1);
        let path = root.join(PATHS[index]);
        fs::create_dir_all(
            path.parent()
                .ok_or_else(|| eyre::eyre!("source parent absent"))?,
        )?;
        fs::write(path, edited)?;
    }
    let mut cells = 0;
    for target in &TARGETS[..2] {
        let context = f.context(target, &CLOSED_OWNER)?;
        let selected = select_core_inputs(&metadata, &context, &inventory)?;
        // The real collector also requires the target's exact standalone
        // project inputs. Copy only its already-selected authored inputs into
        // this isolated fixed-core fixture; never invent build stubs.
        for (output, input) in &selected.inputs {
            if PATHS.contains(&output.as_str()) {
                continue;
            }
            let bytes = read_bounded(&f.shared.core.join(&input.input), 16 * 1024 * 1024)?;
            let path = root.join(&input.input);
            if path.exists() {
                assert_eq!(
                    read_bounded(&path, 16 * 1024 * 1024)?,
                    bytes,
                    "shared fixture project input changed"
                );
            } else {
                fs::create_dir_all(
                    path.parent()
                        .ok_or_else(|| eyre::eyre!("project parent absent"))?,
                )?;
                fs::write(&path, bytes)?;
            }
        }
        let artifacts = collect_core_artifacts(&root, &selected, &context)?;
        for index in 0..3 {
            let artifact = &artifacts[PATHS[index]];
            assert_eq!(
                artifact.source_path,
                format!("{CORE_ROOT}/{}", PATHS[index])
            );
            assert!(artifact.overlay.is_none());
            let source = std::str::from_utf8(&f.sources[index])?;
            let expected = source.replacen("\n\n", &format!("\n\n{marker}"), 1);
            let output = std::str::from_utf8(&artifact.output_bytes)?;
            let (_, body) = output
                .split_once('\n')
                .ok_or_else(|| eyre::eyre!("collector banner absent"))?;
            assert_eq!(body, expected);
            assert_eq!(f.shared.read_source(PATHS[index])?, f.sources[index]);
            cells += 1;
        }
        assert!(
            collect_core_artifacts(&temp.path().join("not-the-fixed-core"), &selected, &context)
                .is_err()
        );
    }
    assert_eq!(cells, 6);
    Ok(())
}
