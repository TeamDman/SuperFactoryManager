//! Prepared preservation regressions for the genuine single-line field providers.
//! Register only after exact source/rule/ledger promotion. Local pinned Git objects
//! are test evidence, not production source lookup. Neither source selection nor
//! text rendering executes a widget, history engine, clipboard or signing ceremony.
#![cfg(test)]

use super::candidate_lock::checked_file;
use super::context::ProjectionContext;
use super::core_inputs::CORE_ROOT;
use super::core_inputs::CoreProjectInputs;
use super::core_inputs::InputVariant;
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
use std::fs;
use std::path::Path;

const LEDGER: &str = "docs/tasks/sfm-core-single-line-input-slice.json";
const PREFIX: &str = "src/main/java/ca/teamdman/sfm/client/input/";
const NAMES: [&str; 2] = ["SFMSingleLineEditBox", "SFMSingleLineInput"];
const D2: [&str; 2] = ["1.19.2", "1.19.4"];
const OLDER: [&str; 8] = [
    "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1", "26.1.2",
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
const RAW: [(&str, usize, &str); 2] = [
    (
        "8e4b9cbb93ad180f5055e99c623695ca6c28a58f",
        2873,
        "sha256:0177eb42880de7cf3677718c5fe09aabbbba3e760a035097ac9dcbce34ea1220",
    ),
    (
        "a58fad22140d523b8965d96bcde2d01bcbc20a57",
        12077,
        "sha256:d2db08d9f3684814c6b37d7234dd6df2976f4dc25f79a9938b088de5a9a8d4b1",
    ),
];
const KEYS_OID: &str = "fd4506e439572ecbae3c83650d0a5c9b2dda4d3b";
#[derive(Facet)]
struct Ledger {
    schema: String,
    normalization: String,
    contract_changes: bool,
    feature_definition_changes: bool,
    prerequisite_changes: bool,
    context_commits: BTreeMap<String, String>,
    definitions: BTreeMap<String, Definition>,
    files: Vec<SourceEvidence>,
}
#[derive(Facet)]
struct Definition {
    supported_targets: Vec<String>,
    requires: Vec<String>,
}
#[derive(Facet)]
struct RawEvidence {
    oid: String,
    bytes: usize,
    sha256: String,
    cr_count: usize,
    lf_count: usize,
    final_lf: bool,
    bom: bool,
}
#[derive(Facet)]
struct SourceEvidence {
    path: String,
    owner: String,
    source_rule: InputVariant,
    raw: RawEvidence,
    witnesses: BTreeMap<String, Option<String>>,
    authored_bytes: usize,
    authored_sha256: String,
}
struct Fixture {
    core: CoreTestFixture,
    ledger: Ledger,
    sources: BTreeMap<String, Vec<u8>>,
    raw: BTreeMap<String, Vec<u8>>,
}
fn path(name: &str) -> String {
    format!("{PREFIX}{name}.java")
}
fn verify_raw(bytes: &[u8], oid: &str) -> Result<()> {
    let (_, length, digest) = RAW
        .iter()
        .find(|(id, _, _)| *id == oid)
        .ok_or_else(|| eyre::eyre!("unreviewed single-line raw identity"))?;
    ensure!(
        bytes.len() == *length
            && sha256(bytes) == *digest
            && !bytes.contains(&b'\r')
            && bytes.ends_with(b"\n")
            && !bytes.starts_with(&[0xef, 0xbb, 0xbf]),
        "single-line raw bytes changed"
    );
    std::str::from_utf8(bytes)?;
    Ok(())
}
impl Fixture {
    fn load() -> Result<Self> {
        let core = CoreTestFixture::load()?;
        let ledger: Ledger = facet_json::from_str(std::str::from_utf8(&read_bounded(
            &checked_file(&core.repository, LEDGER)?,
            96 * 1024,
        )?)?)?;
        ensure!(
            ledger.schema == "sfm:core-single-line-input-slice@1"
                && ledger.normalization == "none"
                && !ledger.contract_changes
                && !ledger.feature_definition_changes
                && !ledger.prerequisite_changes
                && ledger.files.len() == 2
                && ledger.definitions.len() == 2
                && ledger.context_commits
                    == CONTEXTS
                        .into_iter()
                        .map(|(key, value)| (key.to_owned(), value.to_owned()))
                        .collect(),
            "single-line reviewed scope changed"
        );
        for owner in ["single_line_input", "document_history"] {
            let original = &ledger.definitions[owner];
            let current = &core.features.0[owner];
            ensure!(
                original.supported_targets == D2
                    && original.requires.is_empty()
                    && current.supported_targets == D2
                    && current.requires.is_empty(),
                "single-line/history original independent contract changed"
            );
        }
        let raw = read_git_blobs(
            &core.repository,
            &RAW.iter().map(|(oid, _, _)| (*oid).to_owned()).collect(),
        )?;
        let mut sources = BTreeMap::new();
        for ((name, e), (oid, bytes, digest)) in NAMES.iter().zip(&ledger.files).zip(RAW) {
            verify_raw(&raw[oid], oid)?;
            ensure!(
                e.path == path(name)
                    && e.owner == "single_line_input"
                    && e.raw.oid == oid
                    && e.raw.bytes == bytes
                    && e.raw.sha256 == digest
                    && e.raw.cr_count == 0
                    && e.raw.final_lf
                    && !e.raw.bom
                    && e.raw.lf_count == raw[oid].iter().filter(|byte| **byte == b'\n').count(),
                "single-line frozen identity changed"
            );
            for (key, _) in CONTEXTS {
                let expected = matches!(key, "dev/1.19.2" | "dev/1.19.4").then(|| oid.to_owned());
                ensure!(
                    e.witnesses.get(key) == Some(&expected),
                    "single-line twenty-cell historical ledger changed"
                );
            }
            let source = core.read_source(&e.path)?;
            ensure!(
                source == raw[oid]
                    && source.len() == e.authored_bytes
                    && sha256(&source) == e.authored_sha256,
                "root must promote the exact raw single-line source"
            );
            let rule = &e.source_rule;
            ensure!(
                rule.input == e.path
                    && rule.template
                    && rule.when.targets == D2
                    && rule.when.all_features == ["single_line_input"]
                    && rule.when.any_features.is_empty()
                    && rule.when.none_features.is_empty()
                    && core.metadata.source_rules.get(&e.path) == Some(&vec![rule.clone()]),
                "root must promote the exact sparse single-line rule"
            );
            sources.insert(e.path.clone(), source);
        }
        Ok(Self {
            core,
            ledger,
            sources,
            raw,
        })
    }
    fn inventory(&self) -> BTreeSet<String> {
        self.sources.keys().cloned().collect()
    }
    fn render(&self, path: &str, context: &ProjectionContext) -> Result<Option<String>> {
        let selected = select_core_inputs(&self.core.metadata, context, &self.inventory())?;
        if !selected.inputs.contains_key(path) {
            ensure!(
                selected.omitted_paths.contains(path),
                "single-line omitted path unaccounted"
            );
            return Ok(None);
        }
        Ok(Some(render_java_source(
            std::str::from_utf8(&self.sources[path])?,
            context,
        )?))
    }
    fn bounded_metadata(&self) -> CoreProjectInputs {
        let mut metadata = self.core.metadata.clone();
        metadata
            .source_rules
            .retain(|path, _| self.sources.contains_key(path));
        metadata
    }
    fn write_sources(&self, root: &Path) -> Result<()> {
        for (path, bytes) in &self.sources {
            let destination = root.join(path);
            fs::create_dir_all(destination.parent().expect("fixed source parent"))?;
            fs::write(destination, bytes)?;
        }
        Ok(())
    }
    fn copy_project_inputs(
        &self,
        root: &Path,
        metadata: &CoreProjectInputs,
        context: &ProjectionContext,
    ) -> Result<()> {
        let selected = select_core_inputs(metadata, context, &self.inventory())?;
        for (output, input) in &selected.inputs {
            if self.sources.contains_key(output) {
                continue;
            }
            let bytes = read_bounded(
                &checked_file(&self.core.core, &input.input)?,
                16 * 1024 * 1024,
            )?;
            let destination = root.join(&input.input);
            if destination.exists() {
                ensure!(
                    read_bounded(&destination, 16 * 1024 * 1024)? == bytes,
                    "actual isolated project input changed across owner masks"
                );
            } else {
                fs::create_dir_all(destination.parent().expect("fixed project input parent"))?;
                fs::write(destination, bytes)?;
            }
        }
        Ok(())
    }
}

#[test]
fn genuine_single_line_fields_match_all40_frozen_tree_and_body_cells() -> Result<()> {
    let f = Fixture::load()?;
    let paths = NAMES.map(|name| format!("platform/minecraft/{}", path(name)));
    let mut counts = (0, 0);
    for (key, commit) in CONTEXTS {
        let output = frozen_git_command(&f.core.repository)
            .env("GIT_ALLOW_PROTOCOL", "")
            .env("GIT_TERMINAL_PROMPT", "0")
            .args([
                "-c",
                "protocol.allow=never",
                "-c",
                "core.fsmonitor=false",
                "ls-tree",
                commit,
                "--",
            ])
            .args(&paths)
            .output()?;
        ensure!(
            output.status.success() && output.stdout.len() <= 4096 && output.stderr.len() <= 4096,
            "bounded offline single-line tree failure"
        );
        let mut actual = BTreeMap::new();
        for line in std::str::from_utf8(&output.stdout)?.lines() {
            let (header, entry) = line
                .split_once('\t')
                .ok_or_else(|| eyre::eyre!("malformed single-line tree entry"))?;
            let fields = header.split_whitespace().collect::<Vec<_>>();
            ensure!(
                fields.len() == 3
                    && fields[0] == "100644"
                    && fields[1] == "blob"
                    && paths.iter().any(|path| path == entry)
                    && actual
                        .insert(entry.to_owned(), fields[2].to_owned())
                        .is_none(),
                "unexpected single-line tree entry"
            );
        }
        let expected = f
            .ledger
            .files
            .iter()
            .filter_map(|e| {
                e.witnesses[key]
                    .as_ref()
                    .map(|oid| (format!("platform/minecraft/{}", e.path), oid.clone()))
            })
            .collect::<BTreeMap<_, _>>();
        assert_eq!(actual, expected, "frozen single-line cells: {key}");
        let (_, target) = key.split_once('/').expect("fixed context");
        let flags = if matches!(key, "dev/1.19.2" | "dev/1.19.4") {
            &["single_line_input"][..]
        } else {
            &[][..]
        };
        let context = f.core.context(target, flags)?;
        for e in &f.ledger.files {
            let body = f.render(&e.path, &context)?;
            if let Some(oid) = &e.witnesses[key] {
                assert_eq!(
                    body.expect("historical selected field").as_bytes(),
                    f.raw[oid]
                );
                counts.0 += 1;
            } else {
                assert!(body.is_none());
                counts.1 += 1;
            }
        }
    }
    assert_eq!(counts, (4, 36));
    Ok(())
}

#[test]
fn single_line_and_document_history_remain_independent_source_owners() -> Result<()> {
    let f = Fixture::load()?;
    let mut counts = (0, 0);
    for target in D2 {
        for flags in [
            &[][..],
            &["single_line_input"][..],
            &["document_history"][..],
            &["single_line_input", "document_history"][..],
        ] {
            let context = f.core.context(target, flags)?;
            let enabled = flags.contains(&"single_line_input");
            let mut description = context.clone();
            description.environment = "release".to_owned();
            description.preset = "description-is-not-history-authority".to_owned();
            description.projection_key = "source-proof/single-line".to_owned();
            for e in &f.ledger.files {
                let body = f.render(&e.path, &context)?;
                assert_eq!(body.is_some(), enabled);
                assert_eq!(body, f.render(&e.path, &description)?);
                if let Some(body) = body {
                    assert_eq!(body.as_bytes(), f.raw[&e.raw.oid]);
                    counts.0 += 1;
                } else {
                    counts.1 += 1;
                }
            }
            for effect in [
                "client_program_signing",
                "command_palette",
                "typed_command_palette",
                "client_program_actions",
                "packet_transport_private",
                "multiplayer_packets",
            ] {
                assert!(
                    !context.features[effect],
                    "pure field context enabled {effect}"
                );
            }
        }
    }
    assert_eq!(counts, (8, 8));
    Ok(())
}

#[test]
fn unsupported_missing_unknown_field_owners_and_damaged_raw_bytes_are_refused() -> Result<()> {
    let f = Fixture::load()?;
    for target in OLDER {
        assert!(f.core.context(target, &["single_line_input"]).is_err());
        assert!(f.core.context(target, &["document_history"]).is_err());
    }
    for target in D2 {
        let mut unknown = f.core.context(target, &["single_line_input"])?;
        unknown.features.remove("single_line_input");
        assert!(select_core_inputs(&f.core.metadata, &unknown, &f.inventory()).is_err());
        assert!(
            f.core
                .context(target, &["single_line_input_unreviewed"])
                .is_err()
        );
    }
    for (oid, _, _) in RAW {
        let mut damaged = f.raw[oid].clone();
        damaged[0] ^= 1;
        assert!(verify_raw(&damaged, oid).is_err());
        let mut crlf = f.raw[oid].clone();
        crlf.insert(0, b'\r');
        assert!(verify_raw(&crlf, oid).is_err());
    }
    Ok(())
}

#[test]
fn real_accessor_and_unconditional_history_api_are_not_surrogate_providers() -> Result<()> {
    let f = Fixture::load()?;
    let accessor = "src/main/java/ca/teamdman/sfm/mixins/EditBoxAccessor.java";
    for target in D2 {
        let context = f.core.context(target, &["single_line_input"])?;
        let selection = select_core_inputs(
            &f.core.metadata,
            &context,
            &BTreeSet::from([accessor.to_owned()]),
        )?;
        let selected = selection
            .inputs
            .get(accessor)
            .expect("genuine mixin selected");
        let source = f.core.read_source(&selected.input)?;
        let body = render_java_source(std::str::from_utf8(&source)?, &context)?;
        for anchor in [
            "@Mixin(EditBox.class)",
            "@Accessor(\"highlightPos\")",
            "int sfm$getHighlightPos();",
        ] {
            assert!(body.contains(anchor));
        }
    }
    let input = std::str::from_utf8(&f.sources[&path("SFMSingleLineInput")])?;
    for anchor in [
        "import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract.*;",
        "import ca.teamdman.sfm.client.history.document.SFMDocumentHistorySession;",
        "public Optional<SFMDocumentHistorySession> history()",
        "new MutationRequest(",
        "private DocumentState snapshot()",
        "history.currentState()",
    ] {
        assert!(
            input.contains(anchor),
            "real public history API erased: {anchor}"
        );
    }
    assert!(!input.contains("{%"));
    // Those actual history sources require their own following preservation tranche.
    // We do not fake them, relax the source contract, or count Java compilation here.
    Ok(())
}

#[test]
fn recorded_false_public_signing_fields_do_not_replace_the_secret_widget() -> Result<()> {
    let f = Fixture::load()?;
    let raw = read_git_blobs(&f.core.repository, &BTreeSet::from([KEYS_OID.to_owned()]))?;
    let bytes = &raw[KEYS_OID];
    assert_eq!(bytes.len(), 15659);
    assert_eq!(
        sha256(bytes),
        "sha256:e786014db7a7cefdf5338250827d2694fad03fafea6b1cc82afe97fee25e5d5e"
    );
    let keys = std::str::from_utf8(bytes)?;
    assert!(keys.contains("private SFMSigningPassphraseWidget passphrase;"));
    assert!(keys.contains("private SFMSigningPassphraseWidget confirmation;"));
    assert!(keys.contains("20, Component.literal(\"New key file name\"), false)"));
    assert!(
        keys.contains("Component.literal(\"Absolute encrypted backup or restore path\"), false)")
    );
    let input = std::str::from_utf8(&f.sources[&path("SFMSingleLineInput")])?;
    assert!(input.contains("history = recordHistory ? SFMDocumentHistorySession.create("));
    assert!(input.contains("Optional.empty()), snapshot()) : null;"));
    let edit = std::str::from_utf8(&f.sources[&path("SFMSingleLineEditBox")])?;
    assert!(edit.contains("fieldMaximumLength, recordHistory, fieldFilter"));
    assert!(
        edit.contains("client.keyboardHandler::getClipboard, client.keyboardHandler::setClipboard")
    );
    Ok(())
}

#[test]
fn real_collector_omits_corrupt_off_fields_before_reading_then_templates_java() -> Result<()> {
    let f = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    for path in f.inventory() {
        let destination = root.join(path);
        fs::create_dir_all(destination.parent().expect("fixed field source parent"))?;
        fs::write(destination, b"{% if features.unreviewed_history %}\n\xff")?;
    }
    let mut metadata = f.bounded_metadata();
    let inventory = discover_core_source_files(&root)?;
    assert_eq!(inventory, f.inventory());
    for target in D2 {
        for flags in [&[][..], &["document_history"][..]] {
            let context = f.core.context(target, flags)?;
            f.copy_project_inputs(&root, &metadata, &context)?;
            let selected = select_core_inputs(&metadata, &context, &inventory)?;
            let artifacts = collect_core_artifacts(&root, &selected, &context)?;
            for path in f.inventory() {
                assert!(!artifacts.contains_key(&path));
            }
        }
    }
    f.write_sources(&root)?;
    for rules in metadata.source_rules.values_mut() {
        rules[0].template = false;
    }
    let probe_path = path("SFMSingleLineEditBox");
    let original = std::str::from_utf8(&f.sources[&probe_path])?;
    let probe = format!(
        "{original}{{% if features.single_line_input %}}\n// Isolated automatic Java proof.\n{{% endif %}}\n"
    );
    fs::write(root.join(&probe_path), &probe)?;
    for target in D2 {
        let context = f.core.context(target, &["single_line_input"])?;
        f.copy_project_inputs(&root, &metadata, &context)?;
        let selected = select_core_inputs(&metadata, &context, &inventory)?;
        let artifacts = collect_core_artifacts(&root, &selected, &context)?;
        for (path, source) in &f.sources {
            let artifact = &artifacts[path];
            let output = std::str::from_utf8(&artifact.output_bytes)?;
            let (_, body) = output.split_once('\n').expect("actual generated banner");
            let input = if *path == probe_path {
                probe.as_str()
            } else {
                std::str::from_utf8(source)?
            };
            assert_eq!(body, render_java_source(input, &context)?);
            assert!(!body.contains("{%"));
            assert_eq!(artifact.source_path, format!("{CORE_ROOT}/{path}"));
            assert!(artifact.overlay.is_none());
        }
    }
    Ok(())
}

#[test]
fn common_field_edits_propagate_to_both_targets_without_mutating_golden_sources() -> Result<()> {
    let f = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    f.write_sources(&root)?;
    let mut edited = BTreeMap::new();
    for (path, source) in &f.sources {
        let original = std::str::from_utf8(source)?;
        let edit = original.replacen(
            "package ca.teamdman.sfm.client.input;",
            "package ca.teamdman.sfm.client.input;\n// Isolated shared field edit.",
            1,
        );
        assert_ne!(original, edit);
        fs::write(root.join(path), &edit)?;
        edited.insert(path.clone(), edit);
    }
    let metadata = f.bounded_metadata();
    let inventory = discover_core_source_files(&root)?;
    let mut count = 0;
    for target in D2 {
        let context = f.core.context(target, &["single_line_input"])?;
        f.copy_project_inputs(&root, &metadata, &context)?;
        let selected = select_core_inputs(&metadata, &context, &inventory)?;
        let artifacts = collect_core_artifacts(&root, &selected, &context)?;
        for path in f.inventory() {
            let output = std::str::from_utf8(&artifacts[&path].output_bytes)?;
            let (_, body) = output.split_once('\n').expect("actual generated banner");
            assert_eq!(body, render_java_source(&edited[&path], &context)?);
            assert!(body.contains("// Isolated shared field edit."));
            count += 1;
        }
    }
    assert_eq!(count, 4);
    for (path, source) in &f.sources {
        assert_eq!(f.core.read_source(path)?, *source);
    }
    Ok(())
}

#[test]
fn original_unicode_filter_validation_and_history_head_boundaries_remain_exact() -> Result<()> {
    let f = Fixture::load()?;
    let input = std::str::from_utf8(&f.sources[&path("SFMSingleLineInput")])?;
    for anchor in [
        "Character.isHighSurrogate(character)",
        "Character.isLowSurrogate(character)",
        "new String(new char[]{pendingHighSurrogate, character})",
        "value.offsetByCodePoints(at, 1)",
        "codePoint != 0x2028 && codePoint != 0x2029",
        "if (!validator.test(next)) return false;",
        "if (history == null) return;",
        "if (history == null) return false;",
        "HeadMoveStatus.APPLIED",
        "MutationKind.SELECTION_CHANGE",
    ] {
        assert!(
            input.contains(anchor),
            "original editing semantics anchor: {anchor}"
        );
    }
    let edit = std::str::from_utf8(&f.sources[&path("SFMSingleLineEditBox")])?;
    for anchor in [
        "if (!canConsumeInput()) return super.keyPressed",
        "if (!canConsumeInput()) return false;",
        "if (!getValue().equals(buffer.text())) setValue(buffer.text());",
        "setHighlightPos(buffer.anchor());",
    ] {
        assert!(edit.contains(anchor));
    }
    for (_, source) in &f.sources {
        let text = std::str::from_utf8(source)?;
        for forbidden in [
            "ProcessBuilder",
            "java.net.",
            "Files.write",
            "Class.forName",
            "passphrase",
        ] {
            assert!(
                !text.contains(forbidden),
                "field tranche acquired unrelated effect: {forbidden}"
            );
        }
    }
    Ok(())
}
