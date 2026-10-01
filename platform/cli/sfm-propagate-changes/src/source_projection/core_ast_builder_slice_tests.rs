//! Frozen migration evidence for the two authored parser-visitor inputs.
//!
//! Uses the real Liquid renderer, core membership selector and validated
//! contexts. Historical Git blobs are test-only witnesses, never production
//! generation inputs. Deliberate common-core edits require reviewed golden
//! updates, not automatic snapshot/hash adoption. Complete ASTBuilder Java
//! compilation still requires its feature-owned statement/runtime companions.

#![cfg(test)]

use super::context::ProjectionContext;
use super::core_inputs::CORE_ROOT;
use super::core_inputs::discover_core_source_files;
use super::core_inputs::select_core_inputs;
use super::core_slice_test_support::CoreTestFixture;
use super::core_slice_test_support::read_bounded;
use super::core_slice_test_support::read_git_blobs;
use super::projection_catalog::SUPPORTED_TARGETS;
use super::provenance::sha256;
use super::render_java_source;
use eyre::Result;
use eyre::WrapErr;
use eyre::ensure;
use facet::Facet;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::fs;

const LEDGER: &str = "docs/tasks/sfm-core-ast-builder-slice.json";
const AST: &str = "src/main/java/ca/teamdman/sfml/ast/ASTBuilder.java";
const GLOB: &str = "src/main/java/ca/teamdman/sfml/ast/SFMLLiteralGlob.java";
const GRAMMAR: &str = "src/main/antlr/sfml/SFML.g4";
const PATHS: [&str; 2] = [AST, GLOB];
const MAX_SOURCE_BYTES: u64 = 128 * 1024;
const MAX_LEDGER_BYTES: u64 = 1024 * 1024;
const AST_OFF: &str = "7d0c4d10f7bce9a7e11faa0b9bc655b2222f158b";
const AST_FULL: &str = "36f6186b1a2c0d66c10fc90e32929284217c824b";
const AST_GLOB_ONLY: &str = "84533bad0b11dd493c2eecf9cc368abbe7e83efc";
const GLOB_ON: &str = "9a4b9ee0043657a1b185343dd9a2838d4283df0d";
const OWNERS: [&str; 10] = [
    "packet_computation",
    "sfml_execution_side",
    "sfml_worded_intervals",
    "client_frame_language",
    "client_frame_render",
    "client_program_actions",
    "packet_transport_private",
    "client_inbox",
    "sfml_literal_globs",
    "sfml_interval_overflow_validation",
];

const PINNED_BLOBS: [(&str, &str, u64); 4] = [
    (
        "36f6186b1a2c0d66c10fc90e32929284217c824b",
        "sha256:b3c300a6ea36d7e98438e15974c33d58fbb49e086a9abc5c6ec6820c98c4f33f",
        44875,
    ),
    (
        "7d0c4d10f7bce9a7e11faa0b9bc655b2222f158b",
        "sha256:890d2ee42ae8a9c623f6f8cac74bdec2d1ba7c08ce8805ec9051d6aac7b7997b",
        30173,
    ),
    (
        "84533bad0b11dd493c2eecf9cc368abbe7e83efc",
        "sha256:80e41974da67692c3c3d09f9d202eec5ce525a1966efab351458b8f36c529ce4",
        30254,
    ),
    (
        "9a4b9ee0043657a1b185343dd9a2838d4283df0d",
        "sha256:53ab2aeccfffc5c733ab9fffa38007688100ee8f581596988c3ada74dcf5e3f8",
        1529,
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

// Evidence prose is not executable: consume a typed golden subset only.
#[derive(Debug, Facet)]
struct Ledger {
    schema: String,
    context_commits: BTreeMap<String, String>,
    witness_feature_contexts: BTreeMap<String, WitnessContext>,
    direct_owners: Vec<String>,
    feature_contracts: BTreeMap<String, FeatureContract>,
    files: BTreeMap<String, AuthoredFile>,
}

#[derive(Debug, Facet)]
struct WitnessContext {
    minecraft_version: String,
    features: BTreeMap<String, bool>,
}

#[derive(Debug, Facet)]
struct FeatureContract {
    supported_targets: Vec<String>,
    requires: Vec<String>,
}

#[derive(Debug, Facet)]
struct AuthoredFile {
    authored_sha256: String,
    authored_bytes: u64,
    witness_groups: Vec<WitnessGroup>,
}

#[derive(Debug, Facet)]
struct WitnessGroup {
    git_blob: Option<String>,
    source_sha256: Option<String>,
    raw_bytes: u64,
    contexts: Vec<String>,
}

struct Fixture {
    shared: CoreTestFixture,
    ledger: Ledger,
    sources: BTreeMap<String, Vec<u8>>,
    grammar: String,
    blobs: BTreeMap<String, Vec<u8>>,
}

impl Fixture {
    fn load() -> Result<Self> {
        let shared = CoreTestFixture::load()?;
        let ledger = read_bounded(&shared.repository.join(LEDGER), MAX_LEDGER_BYTES)?;
        let ledger: Ledger = facet_json::from_str(std::str::from_utf8(&ledger)?)
            .wrap_err("cannot parse bounded ASTBuilder slice golden evidence")?;
        let sources = PATHS
            .into_iter()
            .map(|path| Ok((path.to_owned(), shared.read_source(path)?)))
            .collect::<Result<BTreeMap<_, _>>>()?;
        let grammar =
            String::from_utf8(read_bounded(&shared.core.join(GRAMMAR), MAX_SOURCE_BYTES)?)?;
        let ids = PINNED_BLOBS
            .into_iter()
            .map(|(id, _, _)| id.to_owned())
            .collect();
        let blobs = read_git_blobs(&shared.repository, &ids)?;
        for (oid, _, _) in PINNED_BLOBS {
            validate_raw(oid, &blobs[oid])?;
        }
        let fixture = Self {
            shared,
            ledger,
            sources,
            grammar,
            blobs,
        };
        fixture.validate_golden_scope()?;
        Ok(fixture)
    }

    fn validate_golden_scope(&self) -> Result<()> {
        ensure!(
            self.ledger.schema == "sfm:core-ast-builder-slice@1",
            "unsupported ASTBuilder evidence schema"
        );
        let expected_contexts = PINNED_CONTEXTS
            .into_iter()
            .map(|(context, commit)| (context.to_owned(), commit.to_owned()))
            .collect::<BTreeMap<_, _>>();
        ensure!(
            self.ledger.context_commits == expected_contexts
                && self
                    .ledger
                    .witness_feature_contexts
                    .keys()
                    .eq(expected_contexts.keys()),
            "twenty frozen ASTBuilder contexts changed; review evidence deliberately"
        );
        ensure!(
            self.ledger.direct_owners == OWNERS.map(str::to_owned),
            "direct ASTBuilder ownership changed"
        );
        let expected_features = feature_closure(&self.shared, &OWNERS)?;
        ensure!(
            self.ledger
                .feature_contracts
                .keys()
                .cloned()
                .collect::<BTreeSet<_>>()
                == expected_features,
            "ASTBuilder prerequisite closure changed; update bounded evidence explicitly"
        );
        for (name, expected) in &self.ledger.feature_contracts {
            let registered = self
                .shared
                .features
                .0
                .get(name)
                .ok_or_else(|| eyre::eyre!("missing registered owner {name}"))?;
            ensure!(
                expected.requires == registered.requires
                    && expected.supported_targets == registered.supported_targets,
                "ASTBuilder feature contract changed at {name}"
            );
        }
        ensure!(
            self.ledger
                .files
                .keys()
                .map(String::as_str)
                .collect::<BTreeSet<_>>()
                == PATHS.into_iter().collect(),
            "ASTBuilder two-file scope changed"
        );
        for (name, witness) in &self.ledger.witness_feature_contexts {
            let (_, target) = split_context(name)?;
            let version = SUPPORTED_TARGETS
                .into_iter()
                .find(|(id, _)| *id == target)
                .map(|(_, version)| version)
                .ok_or_else(|| eyre::eyre!("unsupported golden target {target}"))?;
            let full = matches!(name.as_str(), "dev/1.19.2" | "dev/1.19.4");
            let development = name.starts_with("dev/");
            let expected = OWNERS
                .into_iter()
                .map(|owner| {
                    (
                        owner.to_owned(),
                        full || (owner == "sfml_literal_globs" && development),
                    )
                })
                .collect::<BTreeMap<_, _>>();
            ensure!(
                witness.minecraft_version == version && witness.features == expected,
                "frozen direct-owner assignment changed at {name}"
            );
        }
        for (path, file) in &self.ledger.files {
            let actual = &self.sources[path];
            ensure!(
                actual.len() as u64 <= MAX_SOURCE_BYTES
                    && sha256(actual) == file.authored_sha256
                    && actual.len() as u64 == file.authored_bytes,
                "authored golden changed at {path}; review the common-core edit explicitly"
            );
            let mut covered = BTreeSet::new();
            for group in &file.witness_groups {
                if let Some(oid) = &group.git_blob {
                    let raw = self
                        .blobs
                        .get(oid)
                        .ok_or_else(|| eyre::eyre!("unreviewed golden blob {oid}"))?;
                    ensure!(
                        group.source_sha256.as_deref() == Some(sha256(raw).as_str())
                            && group.raw_bytes == raw.len() as u64,
                        "raw witness group changed"
                    );
                } else {
                    ensure!(
                        group.source_sha256.is_none() && group.raw_bytes == 0,
                        "absent group contains a body"
                    );
                }
                for name in &group.contexts {
                    ensure!(
                        expected_contexts.contains_key(name) && covered.insert(name),
                        "unknown or repeated raw witness context"
                    );
                    ensure!(
                        group.git_blob.as_deref() == expected_blob(path, name)?,
                        "wrong assigned raw witness at {path}/{name}"
                    );
                }
            }
            ensure!(
                covered.into_iter().eq(expected_contexts.keys()),
                "incomplete twenty-context membership coverage"
            );
        }
        Ok(())
    }

    fn render(
        &self,
        target: &str,
        requested: &[&str],
    ) -> Result<(ProjectionContext, String, Option<String>)> {
        let enabled = feature_closure(&self.shared, requested)?;
        let requested = enabled.iter().map(String::as_str).collect::<Vec<_>>();
        let context = self.shared.context(target, &requested)?;
        let inventory = PATHS
            .into_iter()
            .chain([GRAMMAR])
            .map(str::to_owned)
            .collect();
        let selected = select_core_inputs(&self.shared.metadata, &context, &inventory)?;
        ensure!(
            selected
                .inputs
                .get(AST)
                .is_some_and(|input| input.input == AST),
            "ASTBuilder must remain the genuinely shared core input"
        );
        ensure!(
            selected
                .inputs
                .get(GRAMMAR)
                .is_some_and(|input| input.input == GRAMMAR && input.template),
            "grammar companion must use its explicit templated core input"
        );
        let ast = render_java_source(std::str::from_utf8(&self.sources[AST])?, &context)?;
        let carrier = if context.features["sfml_literal_globs"] {
            ensure!(
                selected
                    .inputs
                    .get(GLOB)
                    .is_some_and(|input| input.input == GLOB && input.template),
                "enabled literal-glob carrier must be selected from the exact core path"
            );
            Some(render_java_source(
                std::str::from_utf8(&self.sources[GLOB])?,
                &context,
            )?)
        } else {
            ensure!(
                !selected.inputs.contains_key(GLOB) && selected.omitted_paths.contains(GLOB),
                "off literal-glob carrier must be omitted, not rendered empty"
            );
            None
        };
        Ok((context, ast, carrier))
    }
}

fn feature_closure(shared: &CoreTestFixture, requested: &[&str]) -> Result<BTreeSet<String>> {
    let mut enabled = requested
        .iter()
        .map(|name| (*name).to_owned())
        .collect::<BTreeSet<_>>();
    let mut pending = enabled.iter().cloned().collect::<Vec<_>>();
    while let Some(name) = pending.pop() {
        let definition = shared
            .features
            .0
            .get(&name)
            .ok_or_else(|| eyre::eyre!("unknown test-only requested owner {name}"))?;
        for prerequisite in &definition.requires {
            if enabled.insert(prerequisite.clone()) {
                pending.push(prerequisite.clone());
            }
        }
    }
    Ok(enabled)
}

fn split_context(name: &str) -> Result<(&str, &str)> {
    name.split_once('/')
        .ok_or_else(|| eyre::eyre!("invalid golden context {name}"))
}

fn expected_blob(path: &str, context: &str) -> Result<Option<&'static str>> {
    let (kind, target) = split_context(context)?;
    ensure!(
        ["dev", "release"].contains(&kind)
            && SUPPORTED_TARGETS.into_iter().any(|(id, _)| id == target),
        "unknown frozen witness context"
    );
    let development = kind == "dev";
    Ok(match path {
        AST => Some(if !development {
            AST_OFF
        } else if matches!(target, "1.19.2" | "1.19.4") {
            AST_FULL
        } else {
            AST_GLOB_ONLY
        }),
        GLOB => development.then_some(GLOB_ON),
        _ => eyre::bail!("path outside the two-file slice"),
    })
}

fn validate_raw(oid: &str, raw: &[u8]) -> Result<()> {
    let (_, digest, size) = PINNED_BLOBS
        .into_iter()
        .find(|(expected, _, _)| *expected == oid)
        .ok_or_else(|| eyre::eyre!("unreviewed golden raw object"))?;
    ensure!(
        sha256(raw) == digest
            && raw.len() as u64 == size
            && raw.ends_with(b"\n")
            && !raw.contains(&b'\r')
            && !raw.starts_with(&[0xef, 0xbb, 0xbf]),
        "raw witness must be byte-exact: no normalization is approved in this slice"
    );
    Ok(())
}

fn assert_owner_contract(context: &ProjectionContext, ast: &str, grammar: &str) -> Result<()> {
    let enabled = |owner: &str| -> Result<bool> {
        context
            .features
            .get(owner)
            .copied()
            .ok_or_else(|| eyre::eyre!("unknown owner {owner}"))
    };
    for (marker, owner) in [
        ("ctx.executionSideDeclaration()", "sfml_execution_side"),
        ("ctx.declaration().forEach", "packet_computation"),
        (
            "ProgramDefinitions(PATTERN_DEFINITIONS",
            "packet_computation",
        ),
        ("ctx.inputSelection()", "packet_computation"),
        ("visitFrameTrigger(", "client_frame_language"),
        ("visitBooleanFrameModulo(", "client_frame_language"),
        ("visitRenderImageStatement(", "client_frame_render"),
        ("visitClientJsonValueExpression(", "client_program_actions"),
        (
            "visitClientInvokeValueExpression(",
            "client_program_actions",
        ),
        ("visitClientFieldValueExpression(", "client_program_actions"),
        ("visitBooleanClientValueEquals(", "client_program_actions"),
        ("visitBroadcastStatement(", "packet_transport_private"),
        ("ctx.qualifiedId() == null", "client_inbox"),
        ("SFMLParser.TimeUnitContext", "sfml_worded_intervals"),
        ("SFMLLiteralGlob::toRegex", "sfml_literal_globs"),
    ] {
        ensure!(
            ast.contains(marker) == enabled(owner)?,
            "optional AST marker {marker} leaked or was omitted"
        );
    }
    let packet = enabled("packet_computation")?;
    let side = enabled("sfml_execution_side")?;
    let frames = enabled("client_frame_render")?;
    ensure!(
        ast.contains("import net.minecraft.resources.ResourceLocation;")
            == (packet || frames || enabled("client_program_actions")?),
        "ResourceLocation import must follow its explicit consumers"
    );
    ensure!(
        ast.matches("Program program = new Program(").count() == 1
            && ast.contains("executionSideDeclaration\n        );") == side,
        "Program constructor does not match the independent side/packet composition"
    );
    let worded = enabled("sfml_worded_intervals")?;
    let checked = enabled("sfml_interval_overflow_validation")?;
    ensure!(
        ast.matches("Math.multiplyExact").count()
            == if checked {
                if worded { 1 } else { 4 }
            } else {
                0
            }
            && ast.matches("*= 20;").count() == if !worded && !checked { 4 } else { 0 }
            && ast.contains("return value * 20;") == (worded && !checked),
        "independent overflow validation did not cover every selected arithmetic site"
    );
    ensure!(
        ast.matches("SFMLLiteralGlob::toRegex").count()
            == if enabled("sfml_literal_globs")? { 2 } else { 0 },
        "literal resource and tag conversion must move together"
    );
    if !worded {
        ensure!(
            !ast.contains("ctx.period")
                && !ast.contains("ctx.legacyOffset")
                && !ast.contains("ctx.newOffset")
                && ast.contains("ctx.NUMBER_WITH_G_SUFFIX()"),
            "legacy parser branch contains worded grammar fields"
        );
    }
    ensure!(
        !ast.contains("{%") && !grammar.contains("{%"),
        "whole-line directive survived rendering"
    );
    // Check each explicit SFMLParser context against the actual selected grammar.
    // This is a bounded structural preflight, not ANTLR/javac compilation.
    for (index, _) in ast.match_indices("SFMLParser.") {
        let remaining = &ast[index + "SFMLParser.".len()..];
        let length = remaining
            .bytes()
            .take_while(u8::is_ascii_alphanumeric)
            .count();
        let identifier = &remaining[..length];
        let Some(name) = identifier.strip_suffix("Context") else {
            continue;
        };
        let mut letters = name.chars();
        let first = letters
            .next()
            .ok_or_else(|| eyre::eyre!("empty parser context"))?;
        let rule = format!("{}{}", first.to_ascii_lowercase(), letters.as_str());
        let exists = grammar.lines().any(|line| {
            let head = line.trim_start();
            let rule_exists = head
                .strip_prefix(&rule)
                .is_some_and(|tail| tail.trim_start().starts_with(':'));
            let label_exists = line.split('#').skip(1).any(|label| {
                let label = label.trim_start();
                let length = label.bytes().take_while(u8::is_ascii_alphanumeric).count();
                &label[..length] == name
            });
            rule_exists || label_exists
        });
        ensure!(
            exists,
            "selected ASTBuilder refers to absent parser context {identifier}"
        );
    }
    Ok(())
}

#[test]
fn twenty_ast_builder_witnesses_reconstruct_exact_bytes_and_carrier_membership() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut present = 0;
    let mut absent = 0;
    for (name, witness) in &fixture.ledger.witness_feature_contexts {
        let (_, target) = split_context(name)?;
        let requested = witness
            .features
            .iter()
            .filter_map(|(name, enabled)| enabled.then_some(name.as_str()))
            .collect::<Vec<_>>();
        let (_, ast, carrier) = fixture.render(target, &requested)?;
        for (path, actual) in [(AST, Some(ast)), (GLOB, carrier)] {
            match expected_blob(path, name)? {
                Some(oid) => {
                    let rendered = actual.ok_or_else(|| eyre::eyre!("missing witnessed member"))?;
                    assert_eq!(rendered.as_bytes(), fixture.blobs[oid], "{name}/{path}");
                    present += 1;
                }
                None => {
                    assert!(
                        actual.is_none(),
                        "unexpected absent-witness member {name}/{path}"
                    );
                    absent += 1;
                }
            }
        }
    }
    assert_eq!((present, absent), (30, 10));
    Ok(())
}

#[test]
fn four_hundred_sixteen_supported_cases_keep_visitors_and_arithmetic_independent() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut combinations = BTreeSet::new();
    for mask in 0..(1_u16 << OWNERS.len()) {
        let requested = OWNERS
            .iter()
            .enumerate()
            .filter_map(|(index, owner)| ((mask & (1 << index)) != 0).then_some(*owner))
            .collect::<Vec<_>>();
        combinations.insert(feature_closure(&fixture.shared, &requested)?);
    }
    assert_eq!(
        combinations.len(),
        200,
        "review changed prerequisite combinations explicitly"
    );
    let mut cases = 0;
    let mut present = 0;
    let mut absent = 0;
    for target in ["1.19.2", "1.19.4"] {
        for enabled in &combinations {
            let requested = enabled.iter().map(String::as_str).collect::<Vec<_>>();
            let (context, ast, carrier) = fixture.render(target, &requested)?;
            let grammar = render_java_source(&fixture.grammar, &context)?;
            assert_owner_contract(&context, &ast, &grammar)?;
            present += 1 + usize::from(carrier.is_some());
            absent += usize::from(carrier.is_none());
            if let Some(carrier) = carrier {
                assert_eq!(carrier.as_bytes(), fixture.blobs[GLOB_ON]);
            }
            cases += 1;
        }
    }
    for (target, _) in SUPPORTED_TARGETS {
        if matches!(target, "1.19.2" | "1.19.4") {
            continue;
        }
        for globs in [false, true] {
            let requested = if globs {
                vec!["sfml_literal_globs"]
            } else {
                vec![]
            };
            let (context, ast, carrier) = fixture.render(target, &requested)?;
            let grammar = render_java_source(&fixture.grammar, &context)?;
            assert_owner_contract(&context, &ast, &grammar)?;
            assert_eq!(
                ast.as_bytes(),
                fixture.blobs[if globs { AST_GLOB_ONLY } else { AST_OFF }]
            );
            present += 1 + usize::from(carrier.is_some());
            absent += usize::from(carrier.is_none());
            cases += 1;
        }
    }
    assert_eq!((cases, present, absent), (416, 624, 208));
    Ok(())
}

#[test]
fn overflow_fix_is_valid_without_worded_intervals_and_rejects_unsupported_targets() -> Result<()> {
    let fixture = Fixture::load()?;
    for target in ["1.19.2", "1.19.4"] {
        for worded in [false, true] {
            let mut requested = vec!["sfml_interval_overflow_validation"];
            if worded {
                requested.push("sfml_worded_intervals");
            }
            let (_, ast, carrier) = fixture.render(target, &requested)?;
            assert!(carrier.is_none());
            assert_eq!(
                ast.matches("Math.multiplyExact").count(),
                if worded { 1 } else { 4 }
            );
            assert_eq!(
                ast.matches("Interval exceeds supported tick count").count(),
                if worded { 1 } else { 4 }
            );
            assert!(!ast.contains("ctx.declaration()"));
        }
    }
    for (target, _) in SUPPORTED_TARGETS {
        if !matches!(target, "1.19.2" | "1.19.4") {
            assert!(
                fixture
                    .shared
                    .context(target, &["sfml_interval_overflow_validation"])
                    .is_err()
            );
        }
        fixture.shared.context(target, &["sfml_literal_globs"])?;
    }
    Ok(())
}

#[test]
fn isolated_common_visitor_edit_reaches_multiple_versions_without_changing_guards() -> Result<()> {
    let fixture = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let core = temp.path().join(CORE_ROOT);
    for (path, bytes) in &fixture.sources {
        let output = core.join(path);
        fs::create_dir_all(
            output
                .parent()
                .ok_or_else(|| eyre::eyre!("missing fixture parent"))?,
        )?;
        fs::write(output, bytes)?;
    }
    let original = std::str::from_utf8(&fixture.sources[AST])?;
    const ANCHOR: &str = "public class ASTBuilder extends SFMLBaseVisitor<ASTNode> {\n";
    const EDIT: &str = "// shared visitor authoring probe\npublic class ASTBuilder extends SFMLBaseVisitor<ASTNode> {\n";
    ensure!(
        original.matches(ANCHOR).count() == 1,
        "common edit anchor changed"
    );
    let edited = original.replacen(ANCHOR, EDIT, 1);
    let guards = |source: &str| {
        source
            .lines()
            .filter(|line| line.trim_start().starts_with("{%"))
            .map(str::to_owned)
            .collect::<Vec<_>>()
    };
    assert_eq!(guards(original), guards(&edited));
    fs::write(core.join(AST), edited.as_bytes())?;
    let inventory = discover_core_source_files(&core)?;
    assert_eq!(inventory, PATHS.into_iter().map(str::to_owned).collect());
    let cases: [(&str, &[&str]); 4] = [
        ("1.19.2", &[]),
        ("1.19.4", &["sfml_execution_side", "sfml_literal_globs"]),
        ("1.21.1", &["sfml_literal_globs"]),
        ("26.1.2", &[]),
    ];
    for (target, requested) in cases {
        let (context, before, _) = fixture.render(target, requested)?;
        let selected = select_core_inputs(&fixture.shared.metadata, &context, &inventory)?;
        assert_eq!(selected.inputs[AST].input, AST);
        let input = read_bounded(&core.join(AST), MAX_SOURCE_BYTES)?;
        let actual = render_java_source(std::str::from_utf8(&input)?, &context)?;
        assert_eq!(actual, before.replacen(ANCHOR, EDIT, 1));
    }
    for (path, bytes) in &fixture.sources {
        assert_eq!(
            read_bounded(&fixture.shared.core.join(path), MAX_SOURCE_BYTES)?,
            *bytes
        );
    }
    Ok(())
}

#[test]
fn raw_ast_witnesses_refuse_whitespace_or_token_normalization() -> Result<()> {
    let fixture = Fixture::load()?;
    for (oid, _, _) in PINNED_BLOBS {
        let raw = &fixture.blobs[oid];
        let mut token_edit = raw.clone();
        token_edit[0] = b'P';
        assert!(validate_raw(oid, &token_edit).is_err());
        assert!(validate_raw(oid, &raw[..raw.len() - 1]).is_err());
        let crlf = std::str::from_utf8(raw)?.replace('\n', "\r\n");
        assert!(validate_raw(oid, crlf.as_bytes()).is_err());
        let mut added = raw.clone();
        added.push(b'\n');
        assert!(validate_raw(oid, &added).is_err());
        let mut bom = vec![0xef, 0xbb, 0xbf];
        bom.extend_from_slice(raw);
        assert!(validate_raw(oid, &bom).is_err());
    }
    assert!(validate_raw("HEAD", b"package example;\n").is_err());
    Ok(())
}
