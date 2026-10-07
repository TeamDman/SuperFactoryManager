//! Exact packet declaration leaves and real-core ownership seams.
//! Prepared for later promotion and registration; no ignored-stage fallback.
//! These source contracts are not executed Java or simulation/runtime proof.

#![cfg(test)]

use super::candidate_lock::checked_file;
use super::core_inputs::CORE_ROOT;
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

const LEDGER: &str = "docs/tasks/sfm-core-packet-declarations-slice.json";
const PC: &str = "packet_computation";
const PC_FLAGS: [&str; 3] = [PC, "packet_values", "runtime_resource_cleanup"];
const MAX_BYTES: u64 = 1024 * 1024;
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

struct Golden {
    path: &'static str,
    blob: &'static str,
    digest: &'static str,
    bytes: usize,
    lf: usize,
}
const GOLDENS: [Golden; 4] = [
    Golden {
        path: "src/main/java/ca/teamdman/sfml/ast/ProgramDefinitions.java",
        blob: "b0ae591e77d2373a5f8a978617050902ea08e225",
        digest: "sha256:ba6bcba4f69028666b07fd69224e32c0dc00cbbfd535fc26a695a7a82b1ab6a3",
        bytes: 4187,
        lf: 103,
    },
    Golden {
        path: "src/main/java/ca/teamdman/sfml/ast/ProgramPatternDeclaration.java",
        blob: "681f890735bb3469f4ac4a91eb0408fb930eaf03",
        digest: "sha256:e8eb934d654a351184305e9249ebab13e991876b28018661b58efb86f7af90cf",
        bytes: 339,
        lf: 12,
    },
    Golden {
        path: "src/main/java/ca/teamdman/sfml/ast/ProgramPlayerDeclaration.java",
        blob: "413182cb74f4eef2e6a60c6d2c3c4ab40ed8e29e",
        digest: "sha256:2b1bcf67eacad16ae710d934c277a9cb755e4b8add2dfe079cdbdca55dfb491d",
        bytes: 280,
        lf: 10,
    },
    Golden {
        path: "src/main/java/ca/teamdman/sfml/ast/PatternFieldDefinition.java",
        blob: "bcc291dd7611ce69206da5094a959fb7e1bc2b5a",
        digest: "sha256:ab45f058876d885e38210e28badb232b97849fca939da22a24e9a3c139b5adb8",
        bytes: 349,
        lf: 15,
    },
];
#[derive(Facet)]
struct Ledger {
    schema: String,
    context_commits: BTreeMap<String, String>,
    feature_contract: FeatureContract,
    files: BTreeMap<String, SourceEvidence>,
}
#[derive(Facet)]
struct FeatureContract {
    owner: String,
    supported_targets: Vec<String>,
    requires: Vec<String>,
    changes_to_support_or_prerequisites: bool,
}
#[derive(Facet)]
struct SourceEvidence {
    source_blob: String,
    source_sha256: String,
    source_bytes: usize,
    feature_owner: String,
    supported_targets: Vec<String>,
    feature_prerequisites: Vec<String>,
    line_feed_count: usize,
    carriage_return_count: usize,
    normalized: bool,
    token_or_whitespace_edits: bool,
    witnesses: BTreeMap<String, Option<String>>,
}
struct Fixture {
    core: CoreTestFixture,
    sources: BTreeMap<String, Vec<u8>>,
}
impl Fixture {
    fn load() -> Result<Self> {
        let core = CoreTestFixture::load()?;
        let ledger: Ledger = facet_json::from_str(std::str::from_utf8(&read_bounded(
            &checked_file(&core.repository, LEDGER)?,
            MAX_BYTES,
        )?)?)?;
        ensure!(
            ledger.schema == "sfm:core-packet-declarations-slice@1"
                && ledger.context_commits
                    == CONTEXTS
                        .into_iter()
                        .map(|(name, commit)| (name.to_owned(), commit.to_owned()))
                        .collect()
                && ledger.files.len() == 4
                && ledger.feature_contract.owner == PC
                && ledger.feature_contract.supported_targets == ["1.19.2", "1.19.4"]
                && ledger.feature_contract.requires
                    == ["packet_values", "runtime_resource_cleanup"]
                && !ledger.feature_contract.changes_to_support_or_prerequisites,
            "packet declaration evidence scope changed"
        );
        let owner = core
            .features
            .0
            .get(PC)
            .ok_or_else(|| eyre::eyre!("packet declaration owner missing"))?;
        ensure!(
            owner.supported_targets == ["1.19.2", "1.19.4"]
                && owner.requires == ["packet_values", "runtime_resource_cleanup"],
            "declaration preservation must not widen packet support/prerequisites"
        );
        let raw = read_git_blobs(
            &core.repository,
            &GOLDENS.iter().map(|g| g.blob.to_owned()).collect(),
        )?;
        let mut sources = BTreeMap::new();
        for g in &GOLDENS {
            let e = ledger
                .files
                .get(g.path)
                .ok_or_else(|| eyre::eyre!("packet declaration evidence missing"))?;
            ensure!(
                e.source_blob == g.blob
                    && e.source_sha256 == g.digest
                    && e.source_bytes == g.bytes
                    && e.feature_owner == PC
                    && e.supported_targets == ["1.19.2", "1.19.4"]
                    && e.feature_prerequisites == ["packet_values", "runtime_resource_cleanup"]
                    && e.line_feed_count == g.lf
                    && e.carriage_return_count == 0
                    && !e.normalized
                    && !e.token_or_whitespace_edits
                    && e.witnesses
                        == CONTEXTS
                            .into_iter()
                            .map(|(name, _)| (
                                name.to_owned(),
                                present(name).then(|| g.blob.to_owned())
                            ))
                            .collect(),
                "packet declaration raw witness/ownership changed"
            );
            let source = core.read_source(g.path)?;
            verify_source(&source, g)?;
            ensure!(
                source == raw[g.blob],
                "declaration source is not the exact raw witness"
            );
            let rules = core.metadata.source_rules.get(g.path).ok_or_else(|| {
                eyre::eyre!("declaration rule must be promoted before registration")
            })?;
            ensure!(
                rules.len() == 1
                    && rules[0].input == g.path
                    && rules[0].when.targets == ["1.19.2", "1.19.4"]
                    && rules[0].when.all_features == [PC]
                    && rules[0].when.any_features.is_empty()
                    && rules[0].when.none_features.is_empty(),
                "declaration sparse owner predicate changed"
            );
            sources.insert(g.path.to_owned(), source);
        }
        Ok(Self { core, sources })
    }
    fn inventory(&self) -> BTreeSet<String> {
        self.sources.keys().cloned().collect()
    }
}
fn present(name: &str) -> bool {
    matches!(name, "dev/1.19.2" | "dev/1.19.4")
}
fn verify_source(source: &[u8], g: &Golden) -> Result<()> {
    ensure!(
        source.len() == g.bytes
            && sha256(source) == g.digest
            && !source.contains(&b'\r')
            && source.iter().filter(|byte| **byte == b'\n').count() == g.lf
            && source.ends_with(b"\n")
            && !source.ends_with(b"\n\n"),
        "packet declaration raw bytes changed; normalization is not approved"
    );
    std::str::from_utf8(source)?;
    Ok(())
}

#[test]
fn packet_declaration_cohort_reconstructs_eighty_exact_frozen_tree_and_selector_cells() -> Result<()>
{
    let f = Fixture::load()?;
    let paths = GOLDENS
        .iter()
        .map(|g| format!("platform/minecraft/{}", g.path))
        .collect::<Vec<_>>();
    let mut included = 0;
    let mut omitted = 0;
    for (name, commit) in CONTEXTS {
        let (_, target) = name.split_once('/').expect("fixed context");
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
            output.status.success(),
            "offline declaration tree query failed"
        );
        let mut actual = BTreeMap::new();
        for line in std::str::from_utf8(&output.stdout)?.lines() {
            let (header, path) = line
                .split_once('\t')
                .ok_or_else(|| eyre::eyre!("bad declaration tree witness"))?;
            let fields = header.split_whitespace().collect::<Vec<_>>();
            ensure!(
                fields.len() == 3
                    && fields[0] == "100644"
                    && fields[1] == "blob"
                    && paths.iter().any(|expected| expected == path)
                    && actual
                        .insert(path.to_owned(), fields[2].to_owned())
                        .is_none(),
                "unexpected declaration tree member"
            );
        }
        let expected = if present(name) {
            GOLDENS
                .iter()
                .map(|g| (format!("platform/minecraft/{}", g.path), g.blob.to_owned()))
                .collect()
        } else {
            BTreeMap::new()
        };
        assert_eq!(
            actual, expected,
            "declaration frozen membership changed: {name}"
        );
        let context = f
            .core
            .context(target, if present(name) { &PC_FLAGS } else { &[] })?;
        let selected = select_core_inputs(&f.core.metadata, &context, &f.inventory())?;
        for (path, raw) in &f.sources {
            if present(name) {
                assert_eq!(selected.inputs[path].input, *path);
                assert_eq!(
                    render_java_source(std::str::from_utf8(raw)?, &context)?.as_bytes(),
                    raw
                );
                included += 1;
            } else {
                assert!(selected.omitted_paths.contains(path));
                omitted += 1;
            }
        }
    }
    assert_eq!((included, omitted), (8, 72));
    Ok(())
}

#[test]
fn packet_declaration_owner_is_independent_and_refuses_incomplete_or_unsupported_sets() -> Result<()>
{
    let f = Fixture::load()?;
    for target in ["1.19.2", "1.19.4"] {
        for flags in [
            &[][..],
            &["packet_values"][..],
            &["runtime_resource_cleanup"][..],
            &["packet_values", "runtime_resource_cleanup"][..],
            &["disk_readonly_access"][..],
            &["client_actions"][..],
        ] {
            let context = f.core.context(target, flags)?;
            let selected = select_core_inputs(&f.core.metadata, &context, &f.inventory())?;
            for path in f.sources.keys() {
                assert!(selected.omitted_paths.contains(path));
            }
        }
        let context = f.core.context(target, &PC_FLAGS)?;
        let selected = select_core_inputs(&f.core.metadata, &context, &f.inventory())?;
        for (path, raw) in &f.sources {
            assert_eq!(selected.inputs[path].input, *path);
            let mut relabeled = context.clone();
            relabeled.environment = "release".to_owned();
            relabeled.projection_key = "synthetic/packet/declarations".to_owned();
            assert_eq!(
                render_java_source(std::str::from_utf8(raw)?, &context)?,
                render_java_source(std::str::from_utf8(raw)?, &relabeled)?,
                "declaration rendering must not depend on labels: {path}"
            );
        }
        for flags in [
            &[PC][..],
            &[PC, "packet_values"][..],
            &[PC, "runtime_resource_cleanup"][..],
        ] {
            assert!(f.core.context(target, flags).is_err());
        }
        let mut missing = context;
        assert_eq!(missing.features.remove(PC), Some(true));
        assert!(select_core_inputs(&f.core.metadata, &missing, &f.inventory()).is_err());
    }
    let mut refused = 0;
    for (target, _) in super::projection_catalog::SUPPORTED_TARGETS {
        if !matches!(target, "1.19.2" | "1.19.4") {
            assert!(f.core.context(target, &PC_FLAGS).is_err());
            refused += 1;
        }
    }
    assert_eq!(refused, 8);
    Ok(())
}

#[test]
fn packet_declaration_source_contracts_match_actual_parser_program_and_value_peers() -> Result<()> {
    let f = Fixture::load()?;
    let definitions = std::str::from_utf8(&f.sources[GOLDENS[0].path])?;
    for anchor in [
        "new ProgramDefinitions(Map.of(), Map.of())",
        "patterns = normalizedCopy(patterns, \"pattern\")",
        "players = normalizedCopy(players, \"player\")",
        "case \"any\" -> Optional.of(SFMValuePattern.ANY)",
        "case \"string\" -> Optional.of(SFMValuePattern.STRING)",
        "case \"guid\" -> Optional.of(SFMValuePattern.GUID)",
        "Collections.unmodifiableMap(copy)",
        "copy.putIfAbsent(normalized, Objects.requireNonNull(value))",
        "toLowerCase(Locale.ROOT)",
        "players.forEach",
        "patterns.forEach",
    ] {
        assert!(definitions.contains(anchor));
    }
    for g in &GOLDENS {
        let source = std::str::from_utf8(&f.sources[g.path])?;
        assert!(source.contains("implements ASTNode"));
        assert!(!source.contains("{%"));
        for forbidden in [
            "ProgramContext",
            "ClientValueExpression",
            "BroadcastStatement",
            "SFMPackets",
            "SFMClientInbox",
            "ClientProgramConsent",
            "FrameTrigger",
            "import ca.teamdman.sfm.common.net.",
        ] {
            assert!(!source.contains(forbidden));
        }
    }
    for (index, fields) in [
        (1, &["alias", "pattern"][..]),
        (2, &["alias", "playerName"][..]),
        (3, &["name", "pattern"][..]),
    ] {
        let source = std::str::from_utf8(&f.sources[GOLDENS[index].path])?;
        for field in fields {
            assert!(source.contains(format!("Objects.requireNonNull({field});").as_str()));
        }
    }
    // These are source/API and rendered-owner checks, not executed parser or Java tests.
    for target in ["1.19.2", "1.19.4"] {
        for side in [false, true] {
            let mut flags = PC_FLAGS.to_vec();
            if side {
                flags.push("sfml_execution_side");
            }
            let context = f.core.context(target, &flags)?;
            let off = f
                .core
                .context(target, if side { &["sfml_execution_side"] } else { &[] })?;
            for (path, anchors) in [
                (
                    "src/main/java/ca/teamdman/sfml/ast/ASTBuilder.java",
                    &[
                        "new ProgramDefinitions(PATTERN_DEFINITIONS, PLAYER_DEFINITIONS)",
                        "new ProgramPlayerDeclaration(alias, playerName)",
                        "new ProgramPatternDeclaration(alias, pattern)",
                        "PatternFieldDefinition field",
                        "private SFMValuePattern resolvePattern(String name)",
                    ][..],
                ),
                (
                    "src/main/java/ca/teamdman/sfml/ast/Program.java",
                    &[
                        "ProgramDefinitions definitions",
                        "ProgramDefinitions.EMPTY",
                        "rtn.append(definitions.toSource());",
                    ][..],
                ),
                (
                    "src/main/java/ca/teamdman/sfm/common/value/SFMValuePattern.java",
                    &["record ObjectPattern", "record LiteralPattern"][..],
                ),
                (
                    "src/main/java/ca/teamdman/sfm/common/value/SFMValue.java",
                    &["record StringValue"][..],
                ),
            ] {
                let selected = select_core_inputs(
                    &f.core.metadata,
                    &context,
                    &BTreeSet::from([path.to_owned()]),
                )?;
                let input = selected.inputs.get(path).ok_or_else(|| {
                    eyre::eyre!("actual declaration consumer or value peer omitted: {path}")
                })?;
                let bytes = f.core.read_source(&input.input)?;
                let rendered = render_java_source(std::str::from_utf8(&bytes)?, &context)?;
                for anchor in anchors {
                    assert!(
                        rendered.contains(*anchor),
                        "actual declaration peer seam changed: {path}"
                    );
                }
                if path.ends_with("/ASTBuilder.java") || path.ends_with("/Program.java") {
                    let rendered_off = render_java_source(std::str::from_utf8(&bytes)?, &off)?;
                    for declaration in [
                        "ProgramDefinitions",
                        "ProgramPlayerDeclaration",
                        "ProgramPatternDeclaration",
                        "PatternFieldDefinition",
                    ] {
                        assert!(!rendered_off.contains(declaration));
                    }
                }
            }
            let grammar_path = "src/main/antlr/sfml/SFML.g4";
            let selected = select_core_inputs(
                &f.core.metadata,
                &context,
                &BTreeSet::from([grammar_path.to_owned()]),
            )?;
            let input = selected
                .inputs
                .get(grammar_path)
                .ok_or_else(|| eyre::eyre!("actual declaration grammar input omitted"))?;
            let bytes = read_bounded(&checked_file(&f.core.core, &input.input)?, MAX_BYTES)?;
            let grammar = std::str::from_utf8(&bytes)?;
            let enabled = render_java_source(grammar, &context)?;
            let disabled = render_java_source(grammar, &off)?;
            for anchor in [
                "declaration : LET identifier BE PLAYER OF identifier",
                "valuePattern : GUID",
                "patternField : identifier OF string",
            ] {
                assert!(enabled.contains(anchor));
                assert!(!disabled.contains(anchor));
            }
        }
    }
    Ok(())
}

#[test]
fn packet_declaration_raw_mutation_refuses_and_shared_edit_reaches_d2_only_in_isolated_tree()
-> Result<()> {
    let f = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let core = temp.path().join(CORE_ROOT);
    for g in &GOLDENS {
        let raw = &f.sources[g.path];
        let mut mutated = raw.clone();
        mutated.push(b' ');
        assert!(verify_source(&mutated, g).is_err());
        let destination = core.join(g.path);
        fs::create_dir_all(destination.parent().expect("fixed source parent"))?;
        fs::write(destination, raw)?;
    }
    let path = GOLDENS[0].path;
    let mut edited = f.sources[path].clone();
    edited.extend_from_slice(b"// isolated shared declaration edit\n");
    fs::write(core.join(path), &edited)?;
    let inventory = discover_core_source_files(&core)?;
    assert_eq!(inventory, f.inventory());
    for target in ["1.19.2", "1.19.4"] {
        let context = f.core.context(target, &PC_FLAGS)?;
        let selected = select_core_inputs(&f.core.metadata, &context, &inventory)?;
        assert_eq!(selected.inputs[path].input, path);
        let bytes = read_bounded(&checked_file(&core, path)?, MAX_BYTES)?;
        assert_eq!(
            render_java_source(std::str::from_utf8(&bytes)?, &context)?.as_bytes(),
            edited
        );
        let off = f.core.context(target, &[])?;
        assert!(
            select_core_inputs(&f.core.metadata, &off, &inventory)?
                .omitted_paths
                .contains(path)
        );
    }
    for (path, raw) in &f.sources {
        assert_eq!(f.core.read_source(path)?, *raw);
    }
    Ok(())
}
