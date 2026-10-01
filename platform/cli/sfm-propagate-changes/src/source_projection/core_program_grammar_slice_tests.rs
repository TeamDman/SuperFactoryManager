//! Frozen test-only evidence for the authored SFML grammar and Program slice.
//!
//! Real rendering and core selection are exercised here. Historical Git bytes
//! are bounded golden evidence, never a production generation input. Feature
//! combinations are source/grammar-contract checks, not Java compilation.
//! Deliberate future edits require review of this bounded golden ledger rather
//! than automatic per-target hash adoption or historical-tree fallback.

#![cfg(test)]

use super::candidate_lock::checked_file;
use super::context::ProjectionContext;
use super::core_inputs::select_core_inputs;
use super::core_slice_test_support::CoreTestFixture;
use super::core_slice_test_support::read_bounded;
use super::core_slice_test_support::read_git_blobs;
use super::provenance::sha256;
use super::render_java_source;
use eyre::Result;
use eyre::WrapErr;
use eyre::ensure;
use facet::Facet;
use std::collections::BTreeMap;
use std::collections::BTreeSet;

const LEDGER: &str = "docs/tasks/sfm-core-language-grammar-program-slice.json";
const GRAMMAR: &str = "src/main/antlr/sfml/SFML.g4";
const PROGRAM: &str = "src/main/java/ca/teamdman/sfml/ast/Program.java";
const MAX_SOURCE_BYTES: u64 = 64 * 1024;
const MAX_LEDGER_BYTES: u64 = 1024 * 1024;
const OWNERS: [&str; 9] = [
    "client_frame_language",
    "client_frame_render",
    "client_inbox",
    "client_program_actions",
    "packet_computation",
    "packet_transport_private",
    "runtime_resource_cleanup",
    "sfml_execution_side",
    "sfml_worded_intervals",
];
const GRAMMAR_OFF: &str = "3c2e70f7828622b4f98638d9c36a47d95f333f15";
const GRAMMAR_ON: &str = "79995bb5edccc6ebe421438e705d56b266ff2e58";
const PROGRAM_ON: &str = "8c67a6afbb147793e767a2afb2b28c5ee9dc5529";
const PROGRAM_FORGE: &str = "bd6d245b0f41585389829845c576f2aedb0f2fec";
const PROGRAM_NEOFORGE: &str = "52b9a4b4432354f5b0764c8e46b51a5987b06dd0";
const PROGRAM_NO_NETWORK_HOOKS: &str = "0a04f68e48213714cabbfae685133261c8c745c1";

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
const PINNED_BLOBS: [(&str, &str, u64); 6] = [
    (
        "0a04f68e48213714cabbfae685133261c8c745c1",
        "sha256:e4b7cf7a031c7e94d7fe8b72f2ccf7211b486941127bd6ea613b1f1dc91c85ae",
        13625,
    ),
    (
        "3c2e70f7828622b4f98638d9c36a47d95f333f15",
        "sha256:6b33125cb1e6fe7af1deafc215766d88d6eb33c68771d5daa5c32dd643374d30",
        7927,
    ),
    (
        "52b9a4b4432354f5b0764c8e46b51a5987b06dd0",
        "sha256:1ea362d4831850b62e6d60e23ccd1b82300452d703231a758efceaf91b8743b1",
        13677,
    ),
    (
        "79995bb5edccc6ebe421438e705d56b266ff2e58",
        "sha256:b09f2304c4b76e52c0b2424789db017a9107d377464058ec227ae94263a04173",
        11948,
    ),
    (
        "8c67a6afbb147793e767a2afb2b28c5ee9dc5529",
        "sha256:776b972a131ce871333adeaca37b4712fcc653537dd9c3b7a020fd51d8dfa03f",
        18081,
    ),
    (
        "bd6d245b0f41585389829845c576f2aedb0f2fec",
        "sha256:8205ee84da44f0063cc68218b7b6427d42a0ce4d54cc1f2a75e78f04edaa664f",
        13673,
    ),
];

const ORIGINAL_IDENTIFIER: &str = r#"identifier : (IDENTIFIER | REDSTONE | GLOBAL | SECOND | SECONDS | TOP | BOTTOM | LEFT | RIGHT | FRONT | BACK
           | LET | BE | PLAYER | OF | LIKE | OBJECT | FIELD | GUID | STRING_TYPE | INVOKE | CAPABILITY
           | AS | CREATE | BROADCAST | CHANNEL | NEW | CLIENT | SERVER | BTW | OFFSET
           | FRAME | FOR | MOD | RENDER | IMAGE | JSON) ;
"#;
const NORMALIZED_IDENTIFIER: &str = r#"identifier : (IDENTIFIER | REDSTONE | GLOBAL | SECOND | SECONDS | TOP | BOTTOM | LEFT | RIGHT | FRONT | BACK
           | LET
           | BE
           | PLAYER
           | OF
           | LIKE
           | OBJECT
           | FIELD
           | GUID
           | STRING_TYPE
           | INVOKE
           | CAPABILITY
           | AS
           | CREATE
           | BROADCAST
           | CHANNEL
           | NEW
           | CLIENT
           | SERVER
           | BTW
           | OFFSET
           | FRAME
           | FOR
           | MOD
           | RENDER
           | IMAGE
           | JSON
           ) ;
"#;

// Only consume the typed golden fields. Other ledger prose is not executable.
#[derive(Debug, Facet)]
struct Ledger {
    schema: String,
    authored_inputs: BTreeMap<String, AuthoredInput>,
    witness_matrix: Vec<Witness>,
    normalization_policies: Vec<Normalization>,
}
#[derive(Debug, Facet)]
struct AuthoredInput {
    bytes: u64,
    sha256: String,
}
#[derive(Debug, Facet)]
struct Witness {
    context: String,
    commit: String,
    target_id: String,
    minecraft_version: String,
    enabled_owners: Vec<String>,
    witnesses: BTreeMap<String, ExpectedWitness>,
}
#[derive(Debug, Facet)]
struct ExpectedWitness {
    raw_git_blob: String,
    raw_sha256: String,
    raw_bytes: u64,
    expected_sha256: String,
    expected_bytes: u64,
    comparison: String,
}
#[derive(Debug, Facet)]
struct Normalization {
    id: String,
    scope: NormalizationScope,
    raw: NormalizationBytes,
    normalized: NormalizationBytes,
    original_block: Option<String>,
    normalized_block: Option<String>,
}
#[derive(Debug, Facet)]
struct NormalizationScope {
    path: String,
    raw_git_blob: String,
}
#[derive(Debug, Facet)]
struct NormalizationBytes {
    sha256: String,
    bytes: u64,
    lf_added: Option<bool>,
    crlf_count: u64,
    lone_cr_count: u64,
    bom: bool,
    terminal_lf: bool,
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
        let ledger_bytes =
            read_bounded(&checked_file(&shared.repository, LEDGER)?, MAX_LEDGER_BYTES)?;
        let ledger: Ledger = facet_json::from_str(std::str::from_utf8(&ledger_bytes)?)
            .wrap_err("cannot read the typed Program/grammar golden subset")?;
        let sources = [GRAMMAR, PROGRAM]
            .into_iter()
            .map(|path| {
                let bytes = read_bounded(&checked_file(&shared.core, path)?, MAX_SOURCE_BYTES)?;
                Ok((path.to_owned(), bytes))
            })
            .collect::<Result<BTreeMap<_, _>>>()?;
        let ids = PINNED_BLOBS
            .into_iter()
            .map(|(oid, _, _)| oid.to_owned())
            .collect();
        let blobs = read_git_blobs(&shared.repository, &ids)?;
        let fixture = Self {
            shared,
            ledger,
            sources,
            blobs,
        };
        fixture.validate()?;
        Ok(fixture)
    }

    fn source(&self, path: &str) -> Result<&str> {
        Ok(std::str::from_utf8(self.sources.get(path).ok_or_else(
            || eyre::eyre!("source outside bounded slice: {path}"),
        )?)?)
    }

    fn blob(&self, oid: &str) -> Result<&[u8]> {
        Ok(self
            .blobs
            .get(oid)
            .ok_or_else(|| eyre::eyre!("blob outside bounded slice: {oid}"))?)
    }

    fn validate(&self) -> Result<()> {
        ensure!(
            self.ledger.schema == "sfm:core_language_grammar_program_slice@1",
            "unsupported Program/grammar golden schema"
        );
        ensure!(
            self.blobs.len() == PINNED_BLOBS.len(),
            "test blob scope changed"
        );
        for (oid, digest, size) in PINNED_BLOBS {
            let bytes = self.blob(oid)?;
            ensure!(
                sha256(bytes) == digest && bytes.len() as u64 == size,
                "pinned raw blob changed: {oid}"
            );
            ensure!(
                !bytes.starts_with(&[0xef, 0xbb, 0xbf]) && !bytes.contains(&b'\r'),
                "raw witnesses must have no BOM/CRLF/lone CR"
            );
            ensure!(
                bytes.ends_with(b"\n") == (oid != GRAMMAR_OFF),
                "pinned terminal newline shape changed: {oid}"
            );
        }
        ensure!(
            self.ledger
                .authored_inputs
                .keys()
                .map(String::as_str)
                .collect::<BTreeSet<_>>()
                == BTreeSet::from([GRAMMAR, PROGRAM]),
            "authored path scope changed"
        );
        for (path, file) in &self.ledger.authored_inputs {
            let bytes = self
                .sources
                .get(path)
                .ok_or_else(|| eyre::eyre!("unreviewed authored source: {path}"))?;
            ensure!(
                sha256(bytes) == file.sha256 && bytes.len() as u64 == file.bytes,
                "authored golden changed: {path}; review deliberately"
            );
        }
        let expected_contexts = PINNED_CONTEXTS
            .into_iter()
            .map(|(name, commit)| (name.to_owned(), commit.to_owned()))
            .collect::<BTreeMap<_, _>>();
        let mut actual_contexts = BTreeMap::new();
        for witness in &self.ledger.witness_matrix {
            ensure!(
                actual_contexts
                    .insert(witness.context.clone(), witness.commit.clone())
                    .is_none(),
                "repeated golden context"
            );
            let (_, target) = witness
                .context
                .split_once('/')
                .ok_or_else(|| eyre::eyre!("invalid witness context"))?;
            let context = self.shared.context(target, &[])?;
            ensure!(
                witness.target_id == target
                    && witness.minecraft_version == context.minecraft_version,
                "witness target/version mapping changed"
            );
            let on = matches!(witness.context.as_str(), "dev/1.19.2" | "dev/1.19.4");
            let expected_owners = if on {
                OWNERS.into_iter().map(str::to_owned).collect()
            } else {
                Vec::<String>::new()
            };
            ensure!(
                witness.enabled_owners == expected_owners,
                "owner assignment changed"
            );
            ensure!(
                witness
                    .witnesses
                    .keys()
                    .map(String::as_str)
                    .collect::<BTreeSet<_>>()
                    == BTreeSet::from([GRAMMAR, PROGRAM]),
                "witness path scope changed"
            );
            for (path, expected) in &witness.witnesses {
                let oid = expected_blob(path, target, on)?;
                ensure!(
                    expected.raw_git_blob == oid,
                    "witness blob ownership changed"
                );
                let raw = self.blob(oid)?;
                ensure!(
                    expected.raw_sha256 == sha256(raw) && expected.raw_bytes == raw.len() as u64,
                    "raw ledger hash/size changed"
                );
                let bytes = expected_body(path, oid, raw)?;
                ensure!(
                    expected.expected_sha256 == sha256(&bytes)
                        && expected.expected_bytes == bytes.len() as u64,
                    "normalized/raw expected evidence changed"
                );
                let comparison = if path == PROGRAM {
                    "raw_bytes"
                } else if on {
                    "sfm:antlr_keyword_alternation_lines@1"
                } else {
                    "sfm:antlr_lf_final_newline@1"
                };
                ensure!(
                    expected.comparison == comparison,
                    "comparison policy changed"
                );
            }
        }
        ensure!(
            actual_contexts == expected_contexts,
            "exact twenty-context scope changed"
        );
        self.validate_normalizations()
    }

    fn validate_normalizations(&self) -> Result<()> {
        ensure!(
            self.ledger.normalization_policies.len() == 2,
            "normalization scope widened"
        );
        let mut policies = BTreeSet::new();
        for policy in &self.ledger.normalization_policies {
            ensure!(
                policies.insert(policy.id.as_str()),
                "repeated normalization policy"
            );
            let oid = match policy.id.as_str() {
                "sfm:antlr_lf_final_newline@1" => GRAMMAR_OFF,
                "sfm:antlr_keyword_alternation_lines@1" => GRAMMAR_ON,
                _ => eyre::bail!("unapproved grammar normalization"),
            };
            ensure!(
                policy.scope.path == GRAMMAR && policy.scope.raw_git_blob == oid,
                "normalization witness scope changed"
            );
            let raw = self.blob(oid)?;
            let normalized = expected_body(GRAMMAR, oid, raw)?;
            for (record, bytes) in [
                (&policy.raw, raw),
                (&policy.normalized, normalized.as_slice()),
            ] {
                ensure!(
                    record.sha256 == sha256(bytes)
                        && record.bytes == bytes.len() as u64
                        && record.crlf_count == 0
                        && record.lone_cr_count == 0
                        && !record.bom
                        && record.terminal_lf == bytes.ends_with(b"\n"),
                    "normalization evidence differs from actual bytes"
                );
            }
            ensure!(
                policy.normalized.lf_added == Some(oid == GRAMMAR_OFF),
                "terminal LF evidence changed"
            );
            if oid == GRAMMAR_ON {
                ensure!(
                    policy.original_block.as_deref() == Some(ORIGINAL_IDENTIFIER)
                        && policy.normalized_block.as_deref() == Some(NORMALIZED_IDENTIFIER),
                    "approved identifier reflow scope changed"
                );
            } else {
                ensure!(
                    policy.original_block.is_none() && policy.normalized_block.is_none(),
                    "release newline policy must not reflow a grammar production"
                );
            }
        }
        Ok(())
    }

    fn render(
        &self,
        target: &str,
        requested: &[&str],
    ) -> Result<(ProjectionContext, String, String)> {
        let enabled = feature_closure(&self.shared, requested)?;
        let enabled_refs = enabled.iter().map(String::as_str).collect::<Vec<_>>();
        let context = self.shared.context(target, &enabled_refs)?;
        let inventory = BTreeSet::from([GRAMMAR.to_owned(), PROGRAM.to_owned()]);
        let selected = select_core_inputs(&self.shared.metadata, &context, &inventory)?;
        for path in [GRAMMAR, PROGRAM] {
            let input = selected
                .inputs
                .get(path)
                .ok_or_else(|| eyre::eyre!("always-present core source omitted: {path}"))?;
            ensure!(input.input == path, "source routed away from shared core");
            ensure!(
                path != GRAMMAR || input.template,
                "non-Java grammar must be explicitly selected template:true"
            );
        }
        let grammar = render_java_source(self.source(GRAMMAR)?, &context)?;
        let program = render_java_source(self.source(PROGRAM)?, &context)?;
        Ok((context, grammar, program))
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
            .ok_or_else(|| eyre::eyre!("unregistered requested owner {name}"))?;
        for prerequisite in &definition.requires {
            if enabled.insert(prerequisite.clone()) {
                pending.push(prerequisite.clone());
            }
        }
    }
    Ok(enabled)
}

fn expected_blob(path: &str, target: &str, on: bool) -> Result<&'static str> {
    if path == GRAMMAR {
        return Ok(if on { GRAMMAR_ON } else { GRAMMAR_OFF });
    }
    ensure!(path == PROGRAM, "path outside bounded slice");
    if on {
        ensure!(
            matches!(target, "1.19.2" | "1.19.4"),
            "unwitnessed feature-on target"
        );
        return Ok(PROGRAM_ON);
    }
    Ok(match target {
        "1.19.2" | "1.19.4" | "1.20" | "1.20.1" => PROGRAM_FORGE,
        "1.20.2" | "1.20.3" => PROGRAM_NEOFORGE,
        "1.20.4" | "1.21.0" | "1.21.1" | "26.1.2" => PROGRAM_NO_NETWORK_HOOKS,
        _ => eyre::bail!("unknown witness target"),
    })
}

fn expected_body(path: &str, oid: &str, raw: &[u8]) -> Result<Vec<u8>> {
    if path == PROGRAM {
        return Ok(raw.to_vec());
    }
    let (_, digest, bytes) = PINNED_BLOBS
        .into_iter()
        .find(|(candidate, _, _)| *candidate == oid)
        .ok_or_else(|| eyre::eyre!("normalization needs an exact pinned raw witness"))?;
    ensure!(
        sha256(raw) == digest && raw.len() as u64 == bytes,
        "normalization refuses an altered raw witness"
    );
    ensure!(
        path == GRAMMAR && !raw.starts_with(&[0xef, 0xbb, 0xbf]) && !raw.contains(&b'\r'),
        "grammar normalization rejects BOM/CRLF/lone CR"
    );
    if oid == GRAMMAR_OFF {
        ensure!(
            !raw.ends_with(b"\n"),
            "release grammar already has final LF"
        );
        let mut normalized = raw.to_vec();
        normalized.push(b'\n');
        return Ok(normalized);
    }
    ensure!(oid == GRAMMAR_ON, "unapproved normalized grammar witness");
    let source = std::str::from_utf8(raw)?;
    ensure!(
        source.matches(ORIGINAL_IDENTIFIER).count() == 1,
        "exact original identifier block changed"
    );
    ensure!(
        ORIGINAL_IDENTIFIER.lines().next() == NORMALIZED_IDENTIFIER.lines().next()
            && without_whitespace(ORIGINAL_IDENTIFIER) == without_whitespace(NORMALIZED_IDENTIFIER)
            && !ORIGINAL_IDENTIFIER.contains(['\'', '"'])
            && !ORIGINAL_IDENTIFIER.contains("//")
            && !ORIGINAL_IDENTIFIER.contains("/*"),
        "keyword normalization must only alter whitespace outside quotes/comments"
    );
    Ok(source
        .replacen(ORIGINAL_IDENTIFIER, NORMALIZED_IDENTIFIER, 1)
        .into_bytes())
}

fn without_whitespace(source: &str) -> String {
    source
        .chars()
        .filter(|character| !character.is_whitespace())
        .collect()
}

fn assert_feature_contract(
    context: &ProjectionContext,
    grammar: &str,
    program: &str,
) -> Result<()> {
    let enabled = |name: &str| -> Result<bool> {
        context
            .features
            .get(name)
            .copied()
            .ok_or_else(|| eyre::eyre!("unknown contract owner {name}"))
    };
    for (marker, owner) in [
        ("executionSideDeclaration", "sfml_execution_side"),
        ("declaration : LET", "packet_computation"),
        ("#FrameTrigger", "client_frame_language"),
        ("renderImageStatement :", "client_frame_render"),
        ("#ClientJsonValueExpression", "client_program_actions"),
        ("broadcastStatement :", "packet_transport_private"),
        ("CHANNEL         :", "client_inbox"),
        ("OFFSET  :", "sfml_worded_intervals"),
    ] {
        ensure!(
            grammar.contains(marker) == enabled(owner)?,
            "grammar guard leaked/omitted {marker}"
        );
    }
    for (marker, owner) in [
        ("ProgramDefinitions definitions", "packet_computation"),
        (
            "ProgramExecutionSideDeclaration executionSideDeclaration",
            "sfml_execution_side",
        ),
        ("FrameTrigger", "client_frame_language"),
        ("RenderImageStatement", "client_frame_render"),
        ("ClientValueExpression", "client_program_actions"),
        ("cleanupFailure", "runtime_resource_cleanup"),
    ] {
        ensure!(
            program.contains(marker) == enabled(owner)?,
            "Program guard leaked/omitted {marker}"
        );
    }
    ensure!(
        grammar.lines().any(|line| line == "number: NUMBER ;")
            && grammar
                .lines()
                .any(|line| line == "NUMBER                  : [0-9]+ ;"),
        "integer control grammar changed"
    );
    ensure!(
        !grammar.contains("{%") && !program.contains("{%"),
        "directive remained after rendering"
    );
    assert_parser_token_references(grammar)
}

/// A bounded token-reference preflight, not a substitute for ANTLR generation.
fn assert_parser_token_references(grammar: &str) -> Result<()> {
    let start = grammar
        .find("program :")
        .ok_or_else(|| eyre::eyre!("missing program root"))?;
    let end = grammar[start..]
        .find("// LEXER")
        .map(|relative| start + relative)
        .ok_or_else(|| eyre::eyre!("missing lexer marker"))?;
    let mut tokens = BTreeSet::new();
    for line in grammar.lines() {
        let trimmed = line.trim_start();
        let length = trimmed
            .bytes()
            .take_while(|byte| byte.is_ascii_alphanumeric() || *byte == b'_')
            .count();
        let (name, remainder) = trimmed.split_at(length);
        if lexer_token_name(name) && remainder.trim_start().starts_with(':') {
            tokens.insert(name);
        }
    }
    for reference in grammar_words(&grammar[start..end])? {
        if !lexer_token_name(reference) {
            continue;
        }
        ensure!(
            reference == "EOF" || tokens.contains(reference),
            "parser refers to undefined lexer token {}",
            reference
        );
    }
    Ok(())
}

fn lexer_token_name(word: &str) -> bool {
    word.as_bytes().first().is_some_and(u8::is_ascii_uppercase)
        && word
            .bytes()
            .all(|byte| byte.is_ascii_uppercase() || byte.is_ascii_digit() || byte == b'_')
}

/// Extract ASCII grammar identifiers while ignoring quoted literals/comments.
/// No dependency or grammar-rewriting behavior is introduced by this preflight.
fn grammar_words(source: &str) -> Result<Vec<&str>> {
    let bytes = source.as_bytes();
    let mut words = Vec::new();
    let mut index = 0;
    while index < bytes.len() {
        match bytes[index] {
            b'\'' | b'"' => {
                let quote = bytes[index];
                index += 1;
                let mut closed = false;
                while index < bytes.len() {
                    if bytes[index] == b'\\' {
                        index += 2;
                    } else if bytes[index] == quote {
                        index += 1;
                        closed = true;
                        break;
                    } else {
                        index += 1;
                    }
                }
                ensure!(closed, "unterminated quoted grammar literal");
            }
            b'/' if bytes.get(index + 1) == Some(&b'/') => {
                index += 2;
                while index < bytes.len() && bytes[index] != b'\n' {
                    index += 1;
                }
            }
            b'/' if bytes.get(index + 1) == Some(&b'*') => {
                index += 2;
                let mut closed = false;
                while index + 1 < bytes.len() {
                    if bytes[index] == b'*' && bytes[index + 1] == b'/' {
                        index += 2;
                        closed = true;
                        break;
                    }
                    index += 1;
                }
                ensure!(closed, "unterminated grammar block comment");
            }
            byte if byte.is_ascii_alphabetic() || byte == b'_' => {
                let start = index;
                index += 1;
                while bytes
                    .get(index)
                    .is_some_and(|byte| byte.is_ascii_alphanumeric() || *byte == b'_')
                {
                    index += 1;
                }
                words.push(&source[start..index]);
            }
            _ => index += 1,
        }
    }
    Ok(words)
}

#[test]
fn twenty_frozen_program_grammar_witnesses_use_real_renderer_and_template_selection() -> Result<()>
{
    let fixture = Fixture::load()?;
    let mut comparisons = 0;
    for witness in &fixture.ledger.witness_matrix {
        let requested = witness
            .enabled_owners
            .iter()
            .map(String::as_str)
            .collect::<Vec<_>>();
        let (_, grammar, program) = fixture.render(&witness.target_id, &requested)?;
        for (path, rendered) in [(GRAMMAR, grammar), (PROGRAM, program)] {
            let expected = &witness.witnesses[path];
            let bytes = expected_body(
                path,
                &expected.raw_git_blob,
                fixture.blob(&expected.raw_git_blob)?,
            )?;
            assert_eq!(rendered.as_bytes(), bytes, "witness {}", witness.context);
            comparisons += 1;
        }
    }
    assert_eq!(comparisons, 40);
    Ok(())
}

#[test]
fn fifty_eight_dependency_valid_owner_combinations_keep_rules_and_tokens_closed() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut combinations = BTreeSet::new();
    for bits in 0..(1_u16 << OWNERS.len()) {
        let requested = OWNERS
            .iter()
            .enumerate()
            .filter_map(|(index, name)| ((bits & (1 << index)) != 0).then_some(*name))
            .collect::<Vec<_>>();
        combinations.insert(feature_closure(&fixture.shared, &requested)?);
    }
    assert_eq!(
        combinations.len(),
        58,
        "review dependency changes deliberately"
    );
    let mut checked = 0;
    for enabled in combinations {
        let requested = enabled.iter().map(String::as_str).collect::<Vec<_>>();
        for target in ["1.19.2", "1.19.4"] {
            let (context, grammar, program) = fixture.render(target, &requested)?;
            assert_feature_contract(&context, &grammar, &program)?;
            checked += 1;
        }
    }
    assert_eq!(checked, 116);
    Ok(())
}

#[test]
fn exact_grammar_policies_cannot_reflow_release_rules_or_broaden_newline_normalization()
-> Result<()> {
    let fixture = Fixture::load()?;
    let release = fixture.blob(GRAMMAR_OFF)?;
    let normalized = expected_body(GRAMMAR, GRAMMAR_OFF, release)?;
    assert_eq!(&normalized[..release.len()], release);
    assert_eq!(normalized.len(), release.len() + 1);
    for oid in [GRAMMAR_OFF, GRAMMAR_ON] {
        let raw = fixture.blob(oid)?;
        let mut token_edit = raw.to_vec();
        token_edit[0] = b'G';
        assert!(expected_body(GRAMMAR, oid, &token_edit).is_err());
        let mut whitespace_edit = raw.to_vec();
        whitespace_edit.insert(0, b' ');
        assert!(expected_body(GRAMMAR, oid, &whitespace_edit).is_err());
        let mut bom = vec![0xef, 0xbb, 0xbf];
        bom.extend_from_slice(raw);
        assert!(expected_body(GRAMMAR, oid, &bom).is_err());
        let mut lone_cr = raw.to_vec();
        lone_cr.push(b'\r');
        assert!(expected_body(GRAMMAR, oid, &lone_cr).is_err());
        let crlf = std::str::from_utf8(raw)?.replace('\n', "\r\n");
        assert!(expected_body(GRAMMAR, oid, crlf.as_bytes()).is_err());
    }
    let dev = std::str::from_utf8(fixture.blob(GRAMMAR_ON)?)?;
    let expected = String::from_utf8(expected_body(GRAMMAR, GRAMMAR_ON, dev.as_bytes())?)?;
    let (before, after) = dev
        .split_once(ORIGINAL_IDENTIFIER)
        .ok_or_else(|| eyre::eyre!("missing identifier block"))?;
    assert_eq!(expected, format!("{before}{NORMALIZED_IDENTIFIER}{after}"));
    Ok(())
}

#[test]
fn parser_token_preflight_rejects_a_dangling_optional_token() -> Result<()> {
    let fixture = Fixture::load()?;
    let (_, grammar, _) = fixture.render("1.19.2", &["client_frame_render"])?;
    assert_parser_token_references(&grammar)?;
    let marker = "IMAGE   : I M A G E ;\n";
    ensure!(grammar.matches(marker).count() == 1, "token anchor changed");
    let broken = grammar.replacen(marker, "", 1);
    assert!(assert_parser_token_references(&broken).is_err());
    Ok(())
}
