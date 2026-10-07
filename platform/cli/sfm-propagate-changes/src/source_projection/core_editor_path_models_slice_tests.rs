//! Frozen source-only pure path/resolver model migration goldens.
//!
//! Exact Git objects are bounded local test witnesses, never production source
//! fallbacks. The six inputs must be promoted into actual core first. These
//! regressions prove selector/renderer contracts only, not Java behavior,
//! filesystem authorization, or an explorer runtime. Later deliberate common
//! edits require reviewed golden evidence updates.

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

const LEDGER: &str = "docs/tasks/sfm-core-editor-path-models-slice.json";
const LIMIT: u64 = 128 * 1024;
const OWNERS: [&str; 3] = ["editor_documents", "file_explorer", "registry_explorer"];
const PATHS: [&str; 6] = [
    "src/main/java/ca/teamdman/sfm/client/explorer/SFMPath.java",
    "src/main/java/ca/teamdman/sfm/client/explorer/SFMCanonicalText.java",
    "src/main/java/ca/teamdman/sfm/client/explorer/SFMParseException.java",
    "src/main/java/ca/teamdman/sfm/client/explorer/lazy/SFMResolverTextResult.java",
    "src/main/java/ca/teamdman/sfm/client/explorer/lazy/SFMResolverTextRequest.java",
    "src/main/java/ca/teamdman/sfm/client/explorer/lazy/SFMExplorerCancellationToken.java",
];
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
const RAW_FACTS: [(&str, &str, u64); 6] = [
    (
        "f57383f046929cd4f52a4cc9b412e4791531f24c",
        "sha256:bc943185664f9c8acd7501660601896aa4e8944334d2e61ae12ca53e8521654b",
        22104,
    ),
    (
        "3f7f8df44ccf1d01f37cc84e751ae953462dfba0",
        "sha256:fa77e2ad754a5bdb837028686d870c264edfa477d310d27ec91f13d369c82705",
        7446,
    ),
    (
        "0861ade1d77cbee95b630d766d3a26ee8f830bdb",
        "sha256:e1fb1f11a650308a3d8066716bdf88dd7ad5c95c6f6b59cdda0bf4da5c4127a4",
        530,
    ),
    (
        "1cc41ad54b3502a25d2b187573a55777c1979d2a",
        "sha256:71a43491073270029a146b5bd22eac3efa872859db08598280b1ade8d9bb5f8a",
        4863,
    ),
    (
        "1095db84b7dacc29b4e492566f95748ea1e26642",
        "sha256:d89ed33f6292db148b084b04607619c83463d62bdb220106bec87c88cbc6dfb2",
        2066,
    ),
    (
        "93b1f12df1502ab3a58a4c7eef751f741d580268",
        "sha256:a72e8e8fbb435c0c8ee1026fc0bb610c2a0ed7272df94470bade9b5f9e576cde",
        680,
    ),
];

#[derive(Facet)]
struct Ledger {
    schema: String,
    context_commits: BTreeMap<String, String>,
    normalization: Normalization,
    owners: Vec<Owner>,
    files: Vec<AuthoredFile>,
}
#[derive(Facet)]
struct Normalization {
    policy: String,
    approved_changes: Vec<String>,
    raw_byte_exact: bool,
}
#[derive(Facet)]
struct Owner {
    name: String,
    support: Vec<String>,
    requires: Vec<String>,
}
#[derive(Facet)]
struct AuthoredFile {
    intended_core_path: String,
    stage_bytes: u64,
    stage_sha256: String,
    git_blob: String,
    raw_bytes: u64,
    raw_sha256: String,
    crlf_count: usize,
    lone_cr_count: usize,
    final_lf: bool,
    bom: bool,
    source_rule: SourceRule,
    witnesses: Vec<Witness>,
}
#[derive(Facet)]
struct SourceRule {
    input: String,
    when: SourceWhen,
    template: bool,
}
#[derive(Facet)]
struct SourceWhen {
    targets: Vec<String>,
    any_features: Vec<String>,
}
#[derive(Facet)]
struct Witness {
    context: String,
    commit: String,
    present: bool,
    git_blob: Option<String>,
    raw_bytes: u64,
    raw_sha256: Option<String>,
}
struct Fixture {
    shared: CoreTestFixture,
    ledger: Ledger,
    sources: Vec<Vec<u8>>,
    raw: BTreeMap<String, Vec<u8>>,
}
impl Fixture {
    fn load() -> Result<Self> {
        let shared = CoreTestFixture::load()?;
        let bytes = read_bounded(&shared.repository.join(LEDGER), LIMIT)?;
        let ledger: Ledger = facet_json::from_str(std::str::from_utf8(&bytes)?)
            .wrap_err("cannot parse bounded path/resolver model ledger")?;
        let sources = PATHS
            .iter()
            .map(|path| shared.read_source(path))
            .collect::<Result<Vec<_>>>()?;
        let oids = RAW_FACTS
            .iter()
            .map(|(oid, _, _)| (*oid).to_owned())
            .collect();
        let raw = read_git_blobs(&shared.repository, &oids)?;
        let result = Self {
            shared,
            ledger,
            sources,
            raw,
        };
        result.validate()?;
        Ok(result)
    }
    fn validate(&self) -> Result<()> {
        let commits = CONTEXTS
            .into_iter()
            .map(|(name, commit)| (name.to_owned(), commit.to_owned()))
            .collect::<BTreeMap<_, _>>();
        ensure!(
            self.ledger.schema == "sfm:core-editor-path-models-slice@1"
                && self.ledger.context_commits == commits,
            "frozen model contexts changed"
        );
        let policy = &self.ledger.normalization;
        ensure!(
            policy.policy == "none" && policy.approved_changes.is_empty() && policy.raw_byte_exact,
            "model normalization is not authorized"
        );
        ensure!(
            self.ledger.owners.len() == 3 && self.ledger.files.len() == 6,
            "bounded pure model cohort changed"
        );
        for (owner, name) in self.ledger.owners.iter().zip(OWNERS) {
            let actual = self
                .shared
                .features
                .0
                .get(name)
                .ok_or_else(|| eyre::eyre!("model consumer is unregistered: {name}"))?;
            ensure!(
                owner.name == name
                    && owner.support == TARGETS[..2]
                    && owner.requires.is_empty()
                    && actual.requires.is_empty()
                    && owner.support == actual.supported_targets,
                "pure model consumer support/prerequisites changed: {name}"
            );
        }
        for (index, file) in self.ledger.files.iter().enumerate() {
            let (oid, digest, count) = RAW_FACTS[index];
            ensure!(
                file.intended_core_path == PATHS[index]
                    && file.git_blob == oid
                    && file.raw_sha256 == digest
                    && file.raw_bytes == count
                    && file.stage_sha256 == digest
                    && file.stage_bytes == count
                    && file.crlf_count == 0
                    && file.lone_cr_count == 0
                    && file.final_lf
                    && !file.bom,
                "pure model identity changed: {}",
                PATHS[index]
            );
            ensure!(
                file.source_rule.input == PATHS[index]
                    && file.source_rule.template
                    && file.source_rule.when.targets == TARGETS[..2]
                    && file.source_rule.when.any_features == OWNERS,
                "pure model rule changed: {}",
                PATHS[index]
            );
            let raw = self
                .raw
                .get(oid)
                .ok_or_else(|| eyre::eyre!("missing pinned model blob"))?;
            validate_raw(raw, digest, count)?;
            validate_raw(&self.sources[index], digest, count)?;
            ensure!(
                &self.sources[index] == raw,
                "model is not raw-exact core input"
            );
            ensure!(file.witnesses.len() == 20, "model witness scope changed");
            let mut seen = BTreeSet::new();
            for row in &file.witnesses {
                let expected = present(&row.context);
                ensure!(
                    seen.insert(row.context.as_str())
                        && commits.get(&row.context) == Some(&row.commit)
                        && row.present == expected
                        && row.git_blob.as_deref() == expected.then_some(oid)
                        && row.raw_sha256.as_deref() == expected.then_some(digest)
                        && row.raw_bytes == if expected { count } else { 0 },
                    "pure model raw membership or identity changed"
                );
            }
            ensure!(
                seen == commits.keys().map(String::as_str).collect(),
                "model coverage changed"
            );
        }
        Ok(())
    }
    fn selected(&self, context: &ProjectionContext) -> Result<BTreeSet<usize>> {
        let selection = select_core_inputs(&self.shared.metadata, context, &inventory())?;
        let mut selected = BTreeSet::new();
        for (index, path) in PATHS.iter().enumerate() {
            if let Some(input) = selection.inputs.get(*path) {
                ensure!(
                    input.input == *path
                        && input.template
                        && !selection.omitted_paths.contains(*path),
                    "pure model routed away from actual core input"
                );
                selected.insert(index);
            } else {
                ensure!(
                    selection.omitted_paths.contains(*path),
                    "false model owner must cause an explicit omission: {path}"
                );
            }
        }
        Ok(selected)
    }
    fn render(&self, index: usize, context: &ProjectionContext) -> Result<String> {
        ensure!(
            self.selected(context)?.contains(&index),
            "refuse to render omitted model"
        );
        render_java_source(std::str::from_utf8(&self.sources[index])?, context)
    }
}
fn inventory() -> BTreeSet<String> {
    PATHS.into_iter().map(str::to_owned).collect()
}
fn present(name: &str) -> bool {
    matches!(name, "dev/1.19.2" | "dev/1.19.4")
}
fn validate_raw(bytes: &[u8], digest: &str, count: u64) -> Result<()> {
    ensure!(
        bytes.len() as u64 == count
            && sha256(bytes) == digest
            && bytes.ends_with(b"\n")
            && !bytes.contains(&b'\r')
            && !bytes.starts_with(&[0xef, 0xbb, 0xbf]),
        "raw-exact model bytes changed"
    );
    Ok(())
}

#[test]
fn twenty_contexts_reconstruct_all_120_memberships_and_12_exact_model_bodies() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut selected = 0;
    let mut omitted = 0;
    for (name, _) in CONTEXTS {
        let (_, target) = name
            .split_once('/')
            .ok_or_else(|| eyre::eyre!("invalid model context"))?;
        let context = fixture.shared.context(
            target,
            if present(name) {
                &["editor_documents"]
            } else {
                &[]
            },
        )?;
        let actual = fixture.selected(&context)?;
        for index in 0..6 {
            assert_eq!(
                actual.contains(&index),
                present(name),
                "{name} {}",
                PATHS[index]
            );
            if present(name) {
                let (oid, _, _) = RAW_FACTS[index];
                assert_eq!(
                    fixture.render(index, &context)?.as_bytes(),
                    fixture.raw[oid],
                    "{name}"
                );
                selected += 1;
            } else {
                omitted += 1;
            }
        }
    }
    assert_eq!((selected, omitted), (12, 108));
    Ok(())
}

#[test]
fn sixteen_explicit_consumer_masks_select_all_or_none_and_preserve_84_raw_bodies() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut profiles = 0;
    let mut rendered = 0;
    for target in &TARGETS[..2] {
        for mask in 0..8 {
            let flags = OWNERS
                .iter()
                .enumerate()
                .filter_map(|(index, owner)| (mask & (1 << index) != 0).then_some(*owner))
                .collect::<Vec<_>>();
            let context = fixture.shared.context(target, &flags)?;
            let expected = if mask == 0 {
                BTreeSet::new()
            } else {
                BTreeSet::from([0, 1, 2, 3, 4, 5])
            };
            assert_eq!(fixture.selected(&context)?, expected);
            for index in expected {
                let (oid, _, _) = RAW_FACTS[index];
                assert_eq!(
                    fixture.render(index, &context)?.as_bytes(),
                    fixture.raw[oid]
                );
                rendered += 1;
            }
            profiles += 1;
        }
    }
    assert_eq!((profiles, rendered), (16, 84));
    Ok(())
}

#[test]
fn explorer_and_document_model_consumers_are_independent_without_runtime_prerequisites()
-> Result<()> {
    let fixture = Fixture::load()?;
    for target in &TARGETS[..2] {
        for owner in OWNERS {
            let context = fixture.shared.context(target, &[owner])?;
            assert_eq!(fixture.selected(&context)?.len(), 6);
            for other in OWNERS {
                assert_eq!(
                    context.features.get(other).copied().unwrap_or(false),
                    other == owner
                );
            }
            for forbidden in [
                "editor_document_panels",
                "editor_async_save",
                "client_overlay_scenes",
                "canvas_text_editor",
                "context_actions",
                "release_review",
                "review_sessions",
                "client_program_actions",
                "packet_values",
                "terminal_vox",
                "terminal_remote",
            ] {
                assert!(
                    !context.features.get(forbidden).copied().unwrap_or(false),
                    "pure model context acquired runtime owner: {forbidden}"
                );
            }
        }
    }
    for target in &TARGETS[2..] {
        assert!(
            fixture
                .selected(&fixture.shared.context(target, &[])?)?
                .is_empty()
        );
        for owner in OWNERS {
            assert!(
                fixture.shared.context(target, &[owner]).is_err(),
                "{target} {owner}"
            );
        }
    }
    Ok(())
}

#[test]
fn preserved_models_have_closed_jdk_dependencies_and_no_runtime_or_io_imports() -> Result<()> {
    let fixture = Fixture::load()?;
    let context = fixture.shared.context("1.19.2", &["editor_documents"])?;
    for index in 0..6 {
        let text = fixture.render(index, &context)?;
        for forbidden in [
            "import net.minecraft",
            "import net.minecraftforge",
            "import net.neoforged",
            "java.nio.file.Files",
            "ProcessBuilder",
            "SFMExplorerRuntime",
            "Minecraft.getInstance",
            "SFMClientActionRegistry",
        ] {
            assert!(!text.contains(forbidden), "{} {forbidden}", PATHS[index]);
        }
    }
    let path = fixture.render(0, &context)?;
    for anchor in [
        "SFMCanonicalText",
        "SFMParseException",
        "toNativePath",
        "path.native-component-escape",
        "this.canonical = encodeCanonical();",
    ] {
        assert!(path.contains(anchor), "path anchor changed: {anchor}");
    }
    let canonical = fixture.render(1, &context)?;
    for anchor in [
        "CodingErrorAction.REPORT",
        "text.invalid-utf8",
        "FunctionCall",
        "SFMParseException",
    ] {
        assert!(
            canonical.contains(anchor),
            "canonical text anchor changed: {anchor}"
        );
    }
    let result = fixture.render(3, &context)?;
    for anchor in [
        "SFMResolverTextRequest",
        "LineEndingKind",
        "STALE_CONTENT",
        "authorizedRoot",
        "byteLength",
        "Status.READY",
    ] {
        assert!(result.contains(anchor), "result anchor changed: {anchor}");
    }
    let request = fixture.render(4, &context)?;
    for anchor in [
        "SFMExplorerCancellationToken",
        "maximumBytes == Integer.MAX_VALUE",
        "expectedResolverGeneration < 0",
        "authorizedRoot.scheme()",
    ] {
        assert!(request.contains(anchor), "request anchor changed: {anchor}");
    }
    let token = fixture.render(5, &context)?;
    for anchor in [
        "AtomicBoolean",
        "compareAndSet(false, true)",
        "CancellationException",
    ] {
        assert!(
            token.contains(anchor),
            "cooperative cancellation anchor changed: {anchor}"
        );
    }
    Ok(())
}

#[test]
fn six_frozen_models_reject_unauthorized_eol_bom_and_token_mutations() -> Result<()> {
    let fixture = Fixture::load()?;
    for (oid, digest, count) in RAW_FACTS {
        let raw = &fixture.raw[oid];
        validate_raw(raw, digest, count)?;
        assert!(validate_raw(&raw[..raw.len() - 1], digest, count).is_err());
        let crlf = std::str::from_utf8(raw)?.replace('\n', "\r\n").into_bytes();
        assert!(validate_raw(&crlf, digest, count).is_err());
        let mut bom = vec![0xef, 0xbb, 0xbf];
        bom.extend(raw);
        assert!(validate_raw(&bom, digest, count).is_err());
        let mut token = raw.clone();
        token[0] ^= 1;
        assert!(validate_raw(&token, digest, count).is_err());
    }
    Ok(())
}

#[test]
fn twelve_common_edit_outputs_use_fixed_core_root_and_leave_actual_inputs_unchanged() -> Result<()>
{
    let fixture = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(super::core_inputs::CORE_ROOT);
    let marker = "// Common pure path/resolver model edit proof.\n";
    for (index, path) in PATHS.iter().enumerate() {
        let source = std::str::from_utf8(&fixture.sources[index])?;
        ensure!(
            source.starts_with("package "),
            "shared package anchor changed"
        );
        let out = root.join(path);
        fs::create_dir_all(
            out.parent()
                .ok_or_else(|| eyre::eyre!("fixture parent absent"))?,
        )?;
        fs::write(out, format!("{marker}{source}"))?;
    }
    let mut outputs = 0;
    for target in &TARGETS[..2] {
        let context = fixture.shared.context(target, &["editor_documents"])?;
        let selection = select_core_inputs(&fixture.shared.metadata, &context, &inventory())?;
        for (index, path) in PATHS.iter().enumerate() {
            let input = selection
                .inputs
                .get(*path)
                .ok_or_else(|| eyre::eyre!("model omitted"))?;
            ensure!(
                input.input == *path && input.template,
                "model fixture input changed"
            );
            let bytes = read_bounded(&root.join(&input.input), LIMIT)?;
            let rendered = render_java_source(std::str::from_utf8(&bytes)?, &context)?;
            assert_eq!(
                rendered.as_bytes(),
                [marker.as_bytes(), &fixture.sources[index]].concat()
            );
            assert_eq!(fixture.shared.read_source(path)?, fixture.sources[index]);
            outputs += 1;
        }
    }
    assert_eq!(outputs, 12);
    Ok(())
}

#[test]
fn twenty_offline_trees_independently_recheck_120_model_membership_cells() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut total = 0;
    for (name, commit) in CONTEXTS {
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
        command.args(
            PATHS
                .iter()
                .map(|path| format!("platform/minecraft/{path}")),
        );
        let mut child = command
            .stdout(Stdio::piped())
            .stderr(Stdio::null())
            .spawn()?;
        let mut reader = child
            .stdout
            .take()
            .ok_or_else(|| eyre::eyre!("missing tree output"))?;
        let mut bytes = Vec::new();
        let result = reader.by_ref().take(16385).read_to_end(&mut bytes);
        drop(reader);
        if result.is_err() || bytes.len() > 16384 {
            let _ = child.kill();
            let _ = child.wait();
            result?;
            eyre::bail!("model membership output exceeds bound");
        }
        ensure!(child.wait()?.success(), "offline model tree read failed");
        let mut expected = if present(name) {
            PATHS
                .iter()
                .enumerate()
                .map(|(index, path)| {
                    format!(
                        "100644 blob {}\tplatform/minecraft/{path}\0",
                        RAW_FACTS[index].0
                    )
                })
                .collect::<Vec<_>>()
        } else {
            vec![]
        };
        expected.sort_by(|left, right| {
            left.split_once('\t')
                .map(|(_, path)| path)
                .cmp(&right.split_once('\t').map(|(_, path)| path))
        });
        assert_eq!(bytes, expected.concat().as_bytes(), "{name}");
        total += expected.len();
    }
    assert_eq!(total, 12);
    Ok(())
}
