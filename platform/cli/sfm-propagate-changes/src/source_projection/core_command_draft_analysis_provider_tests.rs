//! Prepared Analysis provider projection regressions; not registered or executed by this capsule.
//! Real fixture/selector/collector reads only. No Git, child process, network or Java execution.
//! Source assertions do not establish UI behavior or full-family transitive compile closure.
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
use super::core_slice_test_support::read_bounded;
use super::provenance::sha256;
use super::render_java_source;
use eyre::Result;
use eyre::ensure;
use facet::Facet;
use sha1::Digest;
use sha1::Sha1;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::path::Path;

const LEDGER: &str = "docs/tasks/sfm-command-draft-analysis-core-provenance-20261002.json";
const PATH: &str = "src/main/java/ca/teamdman/sfm/client/keybinding/SFMCommandDraftAnalysis.java";
const DRAFT: &str = "src/main/java/ca/teamdman/sfm/client/screen/SFMCommandDraftScreen.java";
const TREE: &str = "src/main/java/ca/teamdman/sfm/client/action/SFMClientActionCommandTree.java";
const FRONTIER: &str =
    "src/main/java/ca/teamdman/sfm/client/action/SFMCommandFrontierAnalysis.java";
const INSERTION: &str =
    "src/main/java/ca/teamdman/sfm/client/action/SFMClientCommandInsertion.java";
const EXECUTOR: &str = "src/main/java/ca/teamdman/sfm/client/action/SFMClientActionExecutor.java";
const SOURCE: &str = "src/main/java/ca/teamdman/sfm/client/action/SFMClientActionSource.java";
const OWNER: &str = "keyboard_profiles";
const TARGETS: [&str; 10] = [
    "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
    "26.1.2",
];
const KEYBOARD: [&str; 2] = ["client_actions", OWNER];
const TYPED: [&str; 5] = [
    "client_actions",
    OWNER,
    "client_theme",
    "command_palette",
    "typed_command_palette",
];
const TEMPLATE_BYTES: usize = 5000;
const TEMPLATE_DIGEST: &str =
    "sha256:30824f9e5d6d0b92ed11ea8ac82e1fa78abb84d9a11b2652165aecf6fa48f419";
#[derive(Clone, Copy)]
struct Golden {
    oid: &'static str,
    digest: &'static str,
    bytes: usize,
}
const NORMAL: Golden = Golden {
    oid: "1ebe9b8e87f59ce25026625be53df93b79cbe2ce",
    digest: "sha256:295d499530d3c02c1224ac89f731b8b4570d5c4de9a932ac57017ef9fde0505e",
    bytes: 4179,
};
const TYPED_GOLDEN: Golden = Golden {
    oid: "3c6b4b26c8c59e913d0dd3acf75ff527e710f262",
    digest: "sha256:c48e09b91d85e2a0ad90662bd3945339a0c629a3db5ea79f2d679908a276eb1c",
    bytes: 2859,
};
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
struct Ledger {
    schema: String,
    owner: String,
    normalization: String,
    scope: String,
    context_commits: BTreeMap<String, String>,
    files: Vec<Leaf>,
}
#[derive(Facet)]
struct Leaf {
    path: String,
    core_path: String,
    template_bytes: usize,
    template_sha256: String,
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
    ledger: Ledger,
    inventory: BTreeSet<String>,
}
fn same(actual: &[String], expected: &[&str]) -> bool {
    actual.len() == expected.len()
        && actual.iter().map(String::as_str).collect::<BTreeSet<_>>()
            == expected.iter().copied().collect()
}
fn d2(target: &str) -> bool {
    matches!(target, "1.19.2" | "1.19.4")
}
fn historical_mask(target: &str) -> &'static [&'static str] {
    if d2(target) { &TYPED } else { &KEYBOARD }
}
fn identity(bytes: &[u8], expected: Golden) -> Result<()> {
    ensure!(
        bytes.len() == expected.bytes && sha256(bytes) == expected.digest,
        "Analysis historical digest changed"
    );
    let mut digest = Sha1::new();
    digest.update(format!("blob {}\0", bytes.len()).as_bytes());
    digest.update(bytes);
    ensure!(
        format!("{:x}", digest.finalize()) == expected.oid,
        "Analysis framed Git identity changed"
    );
    ensure!(
        !bytes.contains(&b'\r') && bytes.ends_with(b"\n") && !bytes.ends_with(b"\n\n"),
        "Analysis LF contract changed"
    );
    Ok(())
}
impl Fixture {
    fn load() -> Result<Self> {
        let core = CoreTestFixture::load()?;
        let ledger: Ledger = facet_json::from_str(std::str::from_utf8(&read_bounded(
            &checked_file(&core.repository, LEDGER)?,
            128 * 1024,
        )?)?)?;
        ensure!(
            ledger.schema == "sfm:command_draft_analysis_core_provenance@1"
                && ledger.owner == OWNER
                && ledger.normalization == "none"
                && ledger.scope.contains("Source-only")
                && ledger.files.len() == 1,
            "Analysis ledger contract changed"
        );
        let commits: BTreeMap<String, String> = COMMITS
            .into_iter()
            .map(|(c, h)| (c.to_owned(), h.to_owned()))
            .collect();
        ensure!(
            ledger.context_commits == commits,
            "Analysis frozen twenty commits changed"
        );
        let owner = &core.features.0[OWNER];
        ensure!(
            same(&owner.supported_targets, &TARGETS) && same(&owner.requires, &["client_actions"]),
            "Analysis existing independent owner changed"
        );
        let leaf = &ledger.files[0];
        ensure!(
            leaf.path == PATH
                && leaf.core_path == format!("{CORE_ROOT}/{PATH}")
                && leaf.template_bytes == TEMPLATE_BYTES
                && leaf.template_sha256 == TEMPLATE_DIGEST
                && leaf.witnesses.len() == 20
                && leaf.membership.targets.is_empty()
                && same(&leaf.membership.all_features, &[OWNER])
                && leaf.membership.any_features.is_empty()
                && leaf.membership.none_features.is_empty(),
            "Analysis source membership changed"
        );
        let raw = core.read_source(PATH)?;
        ensure!(
            raw.len() == TEMPLATE_BYTES && sha256(&raw) == TEMPLATE_DIGEST,
            "Analysis shared template changed"
        );
        let rule = core
            .metadata
            .source_rules
            .get(PATH)
            .ok_or_else(|| eyre::eyre!("Analysis rule absent"))?;
        ensure!(
            rule.len() == 1
                && rule[0].input == PATH
                && rule[0].template
                && rule[0].when.targets.is_empty()
                && same(&rule[0].when.all_features, &[OWNER])
                && rule[0].when.any_features.is_empty()
                && rule[0].when.none_features.is_empty(),
            "Analysis rule gained target, alternate input or prerequisite"
        );
        let inventory = discover_core_source_files(&core.core)?;
        ensure!(inventory.contains(PATH), "Analysis canonical source absent");
        Ok(Self {
            core,
            ledger,
            inventory,
        })
    }
    fn render(&self, context: &ProjectionContext) -> Result<Option<Vec<u8>>> {
        let selection = self
            .core
            .selection_for_assertion(context, &self.inventory)?;
        let Some(input) = selection.inputs.get(PATH) else {
            ensure!(
                selection.omitted_paths.contains(PATH),
                "Analysis omitted without rule evidence"
            );
            return Ok(None);
        };
        ensure!(
            input.input == PATH && input.template,
            "Analysis selected an alternate source"
        );
        Ok(Some(
            render_java_source(std::str::from_utf8(&self.core.read_source(PATH)?)?, context)?
                .into_bytes(),
        ))
    }
    fn rendered_source(&self, path: &str, context: &ProjectionContext) -> Result<String> {
        let selection = self
            .core
            .selection_for_assertion(context, &self.inventory)?;
        let input = selection
            .inputs
            .get(path)
            .ok_or_else(|| eyre::eyre!("real direct provider {path} absent"))?;
        ensure!(input.input == path, "direct provider uses alternate input");
        render_java_source(std::str::from_utf8(&self.core.read_source(path)?)?, context)
    }
    fn isolated_metadata(&self) -> CoreProjectInputs {
        let mut metadata = self.core.metadata.clone();
        metadata.source_rules.retain(|path, _| path == PATH);
        metadata
    }
    fn isolated_inventory(&self) -> BTreeSet<String> {
        BTreeSet::from([PATH.to_owned()])
    }
    fn copy_project_inputs(
        &self,
        root: &Path,
        metadata: &CoreProjectInputs,
        context: &ProjectionContext,
    ) -> Result<()> {
        let selection = select_core_inputs(metadata, context, &self.isolated_inventory())?;
        for (output, input) in &selection.inputs {
            if output.starts_with("src/") {
                continue;
            }
            let bytes = read_bounded(
                &checked_file(&self.core.core, &input.input)?,
                16 * 1024 * 1024,
            )?;
            let destination = root.join(&input.input);
            std::fs::create_dir_all(destination.parent().expect("project parent"))?;
            std::fs::write(destination, bytes)?;
        }
        Ok(())
    }
}

#[test]
fn analysis_portable_twenty_cells_retain_ten_present_ten_absent_and_exact_masks() -> Result<()> {
    let f = Fixture::load()?;
    let mut contexts = BTreeSet::new();
    let (mut present, mut absent) = (0, 0);
    for witness in &f.ledger.files[0].witnesses {
        let (environment, target) = witness
            .context
            .split_once('/')
            .ok_or_else(|| eyre::eyre!("invalid context"))?;
        let exists = environment == "dev";
        let golden = if d2(target) { TYPED_GOLDEN } else { NORMAL };
        let mask = if exists { historical_mask(target) } else { &[] };
        ensure!(
            contexts.insert(witness.context.clone())
                && f.ledger.context_commits.get(&witness.context) == Some(&witness.source_commit)
                && witness.present == exists
                && witness.raw_blob.as_deref() == exists.then_some(golden.oid)
                && witness.raw_sha256.as_deref() == exists.then_some(golden.digest)
                && witness.raw_bytes == exists.then_some(golden.bytes)
                && witness.mode.as_deref() == exists.then_some("100644")
                && same(&witness.explicit_registered_features, mask)
                && witness.features_origin
                    == "reviewed_current_explicit_reconstruction_not_historical_manifest",
            "Analysis witness changed"
        );
        let context = f.core.context(target, mask)?;
        if exists {
            identity(
                &f.render(&context)?.expect("historical Analysis selected"),
                golden,
            )?;
            present += 1;
        } else {
            ensure!(
                f.render(&context)?.is_none(),
                "historical release invented Analysis"
            );
            absent += 1;
        }
    }
    assert_eq!((present, absent, contexts.len()), (10, 10, 20));
    Ok(())
}

#[test]
fn analysis_all_ten_historical_masks_reconstruct_authentic_whole_blobs() -> Result<()> {
    let f = Fixture::load()?;
    for target in TARGETS {
        let context = f.core.context(target, historical_mask(target))?;
        let bytes = f.render(&context)?.expect("Analysis selected");
        identity(&bytes, if d2(target) { TYPED_GOLDEN } else { NORMAL })?;
        ensure!(
            !std::str::from_utf8(&bytes)?.contains("{%"),
            "Analysis retained directives"
        );
        if target == "1.21.0" {
            assert_eq!(context.minecraft_version, "1.21");
        }
    }
    Ok(())
}

#[test]
fn analysis_keyboard_only_keeps_real_brigadier_fallback_without_frontier_all_ten() -> Result<()> {
    let f = Fixture::load()?;
    for target in TARGETS {
        let context = f.core.context(target, &KEYBOARD)?;
        assert!(!context.features["typed_command_palette"] && !context.features["command_palette"]);
        let bytes = f
            .render(&context)?
            .expect("independent keyboard Analysis selected");
        identity(&bytes, NORMAL)?;
        let body = std::str::from_utf8(&bytes)?;
        ensure!(
            body.contains("MissingParameter missing = nextArgument(parsed);")
                && body
                    .contains("private static String displayType(ArgumentCommandNode<?, ?> node)")
                && !body.contains("SFMCommandFrontierAnalysis")
                && !body.contains("analyzeFrontier"),
            "keyboard-only Analysis still depends on typed provider"
        );
        let selected = select_core_inputs(&f.core.metadata, &context, &f.inventory)?;
        assert!(!selected.inputs.contains_key(FRONTIER));
        let tree = f.rendered_source(TREE, &context)?;
        ensure!(
            tree.contains("public ParseResults<SFMClientActionSource> parse(")
                && !tree.contains("public SFMCommandFrontierAnalysis analyzeFrontier("),
            "independent tree seam changed"
        );
    }
    Ok(())
}

#[test]
fn analysis_typed_d2_uses_existing_frontier_while_modern_typed_masks_fail_closed() -> Result<()> {
    let f = Fixture::load()?;
    for target in &TARGETS[..2] {
        let context = f.core.context(target, &TYPED)?;
        identity(
            &f.render(&context)?.expect("typed Analysis selected"),
            TYPED_GOLDEN,
        )?;
        let selected = select_core_inputs(&f.core.metadata, &context, &f.inventory)?;
        assert!(selected.inputs.contains_key(FRONTIER));
        ensure!(
            f.rendered_source(TREE, &context)?
                .contains("public SFMCommandFrontierAnalysis analyzeFrontier(")
                && f.rendered_source(FRONTIER, &context)?
                    .contains("missingParameters"),
            "authentic typed provider seam absent"
        );
    }
    for target in &TARGETS[2..] {
        assert!(f.core.context(target, &TYPED).is_err());
    }
    for invalid in [
        &[OWNER][..],
        &["client_actions", OWNER, "typed_command_palette"][..],
    ] {
        assert!(f.core.context("1.19.2", invalid).is_err());
    }
    Ok(())
}

#[test]
fn analysis_real_keyboard_draft_insertion_tree_executor_circuit_is_selected_without_typed_palette()
-> Result<()> {
    let f = Fixture::load()?;
    for target in TARGETS {
        let context = f.core.context(target, &KEYBOARD)?;
        let selection = select_core_inputs(&f.core.metadata, &context, &f.inventory)?;
        for path in [PATH, DRAFT, INSERTION, TREE, EXECUTOR, SOURCE] {
            assert!(
                selection.inputs.contains_key(path),
                "actual direct provider absent: {target}/{path}"
            );
        }
        let draft = f.rendered_source(DRAFT, &context)?;
        ensure!(
            draft.contains("import ca.teamdman.sfm.client.keybinding.SFMCommandDraftAnalysis;")
                && draft.contains("SFMCommandDraftAnalysis.analyze("),
            "real DraftScreen does not reach Analysis"
        );
        let analysis =
            std::str::from_utf8(&f.render(&context)?.expect("Analysis selected"))?.to_owned();
        ensure!(
            analysis.contains("SFMClientCommandInsertion.prepare(command, tree, source)")
                && analysis.contains("tree.parse(prepared, source)")
                && analysis.contains("SFMClientActionExecutor.isExecutable(parsed)"),
            "Analysis lost direct common calls"
        );
        ensure!(
            f.rendered_source(INSERTION, &context)?
                .contains("public static String prepare(")
                && f.rendered_source(EXECUTOR, &context)?
                    .contains("public static boolean isExecutable(")
                && f.rendered_source(SOURCE, &context)?
                    .contains("SFMClientActionSource"),
            "common provider members absent"
        );
    }
    Ok(())
}

#[test]
fn analysis_twenty_feature_off_controls_omit_before_raw_reads() -> Result<()> {
    let mut f = Fixture::load()?;
    let catalog = CoreCatalog::load(&f.core.repository, &f.core.repository)?;
    assert_eq!(catalog.catalog.0.len(), 20);
    f.core.core = f.core.core.join("deliberately_missing_analysis_sources");
    let mut count = 0;
    for key in catalog.catalog.0.keys() {
        let context = f.core.historical_feature_off_catalog_context(key)?;
        assert!(!context.features[OWNER]);
        ensure!(
            f.render(&context)?.is_none(),
            "historical feature-off control admitted Analysis"
        );
        count += 1;
    }
    assert_eq!(count, 20);
    Ok(())
}

#[test]
fn analysis_real_collector_omits_malformed_off_source_and_rejects_selected_source() -> Result<()> {
    let f = Fixture::load()?;
    let metadata = f.isolated_metadata();
    let inventory = f.isolated_inventory();
    let off = f.core.context("1.19.2", &[])?;
    let on = f.core.context("1.19.2", &KEYBOARD)?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    std::fs::create_dir_all(&root)?;
    f.copy_project_inputs(&root, &metadata, &on)?;
    let destination = root.join(PATH);
    std::fs::create_dir_all(destination.parent().expect("Analysis parent"))?;
    std::fs::write(&destination, [0xff])?;
    let omitted = select_core_inputs(&metadata, &off, &inventory)?;
    assert!(!collect_core_artifacts(&root, &omitted, &off)?.contains_key(PATH));
    let selected = select_core_inputs(&metadata, &on, &inventory)?;
    assert!(collect_core_artifacts(&root, &selected, &on).is_err());
    let raw = f.core.read_source(PATH)?;
    let mut poison = b"{% if features.not_registered %}\n{% endif %}\n".to_vec();
    poison.extend_from_slice(&raw);
    std::fs::write(&destination, poison)?;
    assert!(collect_core_artifacts(&root, &selected, &on).is_err());
    std::fs::write(&destination, &raw)?;
    let actual = collect_core_artifacts(&root, &selected, &on)?;
    let artifact = &actual[PATH];
    ensure!(
        artifact.source_bytes == raw
            && artifact.source_path == format!("{CORE_ROOT}/{PATH}")
            && artifact.overlay.is_none()
            && artifact
                .output_bytes
                .starts_with(b"// GENERATED by sfm-propagate-changes;"),
        "actual collector source/provenance changed"
    );
    ensure!(
        artifact
            .output_bytes
            .ends_with(&f.render(&on)?.expect("Analysis selected")),
        "collector normal output differs"
    );
    Ok(())
}

#[test]
fn analysis_shared_source_comment_reaches_twelve_actual_collected_outputs_only_in_temp()
-> Result<()> {
    let f = Fixture::load()?;
    let metadata = f.isolated_metadata();
    let inventory = f.isolated_inventory();
    let before = f.core.read_source(PATH)?;
    let prefix = b"// Shared Analysis source propagation proof.\n";
    let mut count = 0;
    for target in TARGETS {
        let masks: Vec<&[&str]> = if d2(target) {
            vec![&KEYBOARD, &TYPED]
        } else {
            vec![&KEYBOARD]
        };
        for mask in masks {
            let context = f.core.context(target, mask)?;
            let temp = tempfile::tempdir()?;
            let root = temp.path().join(CORE_ROOT);
            std::fs::create_dir_all(&root)?;
            f.copy_project_inputs(&root, &metadata, &context)?;
            let destination = root.join(PATH);
            std::fs::create_dir_all(destination.parent().expect("Analysis parent"))?;
            let mut changed = prefix.to_vec();
            changed.extend_from_slice(&before);
            std::fs::write(&destination, &changed)?;
            let selection = select_core_inputs(&metadata, &context, &inventory)?;
            let actual = collect_core_artifacts(&root, &selection, &context)?;
            let artifact = &actual[PATH];
            let mut expected_output = prefix.to_vec();
            expected_output.extend_from_slice(&f.render(&context)?.expect("Analysis selected"));
            ensure!(
                artifact.source_bytes == changed
                    && artifact.source_bytes != before
                    && artifact.source_path == format!("{CORE_ROOT}/{PATH}")
                    && artifact.overlay.is_none()
                    && artifact
                        .output_bytes
                        .starts_with(b"// GENERATED by sfm-propagate-changes;")
                    && artifact.output_bytes.ends_with(&expected_output),
                "actual shared-source edit did not propagate"
            );
            count += 1;
        }
    }
    assert_eq!(count, 12);
    ensure!(
        f.core.read_source(PATH)? == before,
        "canonical Analysis source was changed"
    );
    Ok(())
}
