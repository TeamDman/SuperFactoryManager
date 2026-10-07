//! Post-promotion evidence for six real packet expression/materialization inputs.
//!
//! Staged under ignored target until root copies and registers this module.
//! Tests then read only actual core sources/rules; historical Git objects are
//! bounded offline witnesses, never production inputs or staging fallbacks.
//! Source/API proofs do not establish a whole-mod Java build or runtime effects.

#![cfg(test)]

use super::candidate_lock::checked_file;
use super::context::ProjectionContext;
use super::core_inputs::CORE_ROOT;
use super::core_inputs::discover_core_source_files;
use super::core_inputs::select_core_inputs;
use super::core_slice_test_support::CoreTestFixture;
use super::core_slice_test_support::read_bounded;
use super::core_slice_test_support::read_git_blobs;
use super::projection_catalog::SUPPORTED_TARGETS;
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

const LEDGER: &str = "docs/tasks/sfm-core-packet-providers-slice.json";
const STAGE: &str = "platform/cli/sfm-propagate-changes/target/core-packet-providers-stage-v1";
const OWNER: &str = "packet_computation";
const VALUES: &str = "packet_values";
const CLEANUP: &str = "runtime_resource_cleanup";
const OWNER_FLAGS: [&str; 3] = [OWNER, VALUES, CLEANUP];
const D2: [&str; 2] = ["1.19.2", "1.19.4"];
const PROGRAM: &str = "src/main/java/ca/teamdman/sfm/common/program/";
const AST: &str = "src/main/java/ca/teamdman/sfml/ast/";
const MAX_BYTES: u64 = 1024 * 1024;
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
struct RawGolden {
    path: &'static str,
    oid: &'static str,
    digest: &'static str,
    bytes: usize,
    lf: usize,
}
const GOLDENS: [RawGolden; 6] = [
    RawGolden {
        path: "src/main/java/ca/teamdman/sfml/ast/ProgramValueExpression.java",
        oid: "2e730f45cac02f00390d617a4c365b2172ea5384",
        digest: "sha256:23fb0e82300c7fe095092123ff4bf6e9d2ca2ea60bcda018119939c74ea79656",
        bytes: 255,
        lf: 8,
    },
    RawGolden {
        path: "src/main/java/ca/teamdman/sfml/ast/ObjectFieldValueExpression.java",
        oid: "bd64be491168d243ce1a52b22b6a10c0a279cc7f",
        digest: "sha256:3a3828a3b4d742d4da1ebce8af55e1defa6123f40e7a3b9dbf4c725c86050187",
        bytes: 2729,
        lf: 91,
    },
    RawGolden {
        path: "src/main/java/ca/teamdman/sfml/ast/ObjectConstructionValueExpression.java",
        oid: "12ec98eb8e2a56a694a75de455e14403b548f501",
        digest: "sha256:c097a321fc1d6fa6be578bf278f6881a58c67e0c8eb4de4d8fd0f327008c4551",
        bytes: 3646,
        lf: 83,
    },
    RawGolden {
        path: "src/main/java/ca/teamdman/sfm/common/program/ProgramEphemeralResource.java",
        oid: "000aa417a6694fae20ec687ef30fdb055e5c4bf0",
        digest: "sha256:8d1049c298bbeb0acfdab610bf2d77b36071247ace2415836f1a2c29393b4d96",
        bytes: 204,
        lf: 7,
    },
    RawGolden {
        path: "src/main/java/ca/teamdman/sfm/common/program/ProgramEphemeralItemResource.java",
        oid: "2a4c0d36077e1fc3a84a88ee3a442fd006e5d884",
        digest: "sha256:5d1f526bb267ae3b637afdcd052e047fc22458d687de76a3edb255e16d7cb1b3",
        bytes: 1839,
        lf: 61,
    },
    RawGolden {
        path: "src/main/java/ca/teamdman/sfm/common/program/GeneratedItemProgramInputSource.java",
        oid: "de76b9de536ca0a47979b5acba41b1e31ea0bf9f",
        digest: "sha256:3a87f6bc3087fa3e0328cdde67d8745e365bf7d447071ee5d76b74d1f151c2e9",
        bytes: 9613,
        lf: 266,
    },
];

#[derive(Facet)]
struct Ledger {
    schema: String,
    status: String,
    scope: String,
    context_commits: BTreeMap<String, String>,
    files: BTreeMap<String, FileEvidence>,
}
#[derive(Facet)]
struct FileEvidence {
    original_path: String,
    core_input: String,
    staged_path: String,
    feature_owner: String,
    supported_targets: Vec<String>,
    required_features: Vec<String>,
    feature_prerequisites: Vec<String>,
    source_blob: String,
    source_sha256: String,
    source_bytes: usize,
    line_endings: String,
    carriage_return_count: usize,
    line_feed_count: usize,
    final_newline: String,
    normalized: bool,
    token_or_whitespace_edits: bool,
    current_canonical_sha256: String,
    staged_sha256: String,
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
            ledger.schema == "sfm:core-packet-providers-slice@1"
                && ledger.status == "exact_sources_ignored_stage_pending_root_promotion"
                && ledger.scope == "six_packet_object_expression_and_materialization_providers",
            "provider ledger scope changed"
        );
        let pinned = PINNED_CONTEXTS
            .into_iter()
            .map(|(name, commit)| (name.to_owned(), commit.to_owned()))
            .collect::<BTreeMap<_, _>>();
        ensure!(
            ledger.context_commits == pinned && ledger.files.len() == 6,
            "provider frozen contexts/path scope changed"
        );
        let owner = core
            .features
            .0
            .get(OWNER)
            .ok_or_else(|| eyre::eyre!("provider owner is missing"))?;
        ensure!(
            same_names(&owner.supported_targets, &D2)
                && same_names(&owner.requires, &[VALUES, CLEANUP]),
            "provider owner support or prerequisites changed"
        );
        for prerequisite in [VALUES, CLEANUP] {
            let definition = core
                .features
                .0
                .get(prerequisite)
                .ok_or_else(|| eyre::eyre!("missing prerequisite {prerequisite}"))?;
            ensure!(
                same_names(&definition.supported_targets, &D2) && definition.requires.is_empty(),
                "provider prerequisite contract changed"
            );
        }
        let oids = GOLDENS.iter().map(|g| g.oid.to_owned()).collect();
        let blobs = read_git_blobs(&core.repository, &oids)?;
        let mut sources = BTreeMap::new();
        for golden in &GOLDENS {
            let evidence = ledger
                .files
                .get(golden.path)
                .ok_or_else(|| eyre::eyre!("missing provider ledger path"))?;
            let basename = golden.path.rsplit('/').next().expect("fixed Java path");
            ensure!(
                evidence.original_path == format!("platform/minecraft/{}", golden.path)
                    && evidence.core_input == format!("{CORE_ROOT}/{}", golden.path)
                    && evidence.staged_path == format!("{STAGE}/{basename}")
                    && evidence.feature_owner == OWNER
                    && same_names(&evidence.supported_targets, &D2)
                    && evidence.required_features == [OWNER]
                    && same_names(&evidence.feature_prerequisites, &[VALUES, CLEANUP])
                    && evidence.source_blob == golden.oid
                    && evidence.source_sha256 == golden.digest
                    && evidence.current_canonical_sha256 == golden.digest
                    && evidence.staged_sha256 == golden.digest
                    && evidence.source_bytes == golden.bytes
                    && evidence.line_endings == "lf"
                    && evidence.carriage_return_count == 0
                    && evidence.line_feed_count == golden.lf
                    && evidence.final_newline == "exactly_one_lf"
                    && !evidence.normalized
                    && !evidence.token_or_whitespace_edits,
                "provider exact source/owner evidence changed"
            );
            let witnesses = PINNED_CONTEXTS
                .into_iter()
                .map(|(name, _)| {
                    (
                        name.to_owned(),
                        present_context(name).then(|| golden.oid.to_owned()),
                    )
                })
                .collect::<BTreeMap<_, _>>();
            ensure!(
                evidence.witnesses == witnesses,
                "provider witness membership changed"
            );
            let raw = core.read_source(golden.path)?;
            validate_raw(golden, &raw)?;
            ensure!(raw == blobs[golden.oid], "provider raw Git source differs");
            let rules = core
                .metadata
                .source_rules
                .get(golden.path)
                .ok_or_else(|| eyre::eyre!("provider sparse source rule missing"))?;
            ensure!(
                rules.len() == 1
                    && rules[0].input == golden.path
                    && same_names(&rules[0].when.targets, &D2)
                    && rules[0].when.all_features == [OWNER]
                    && rules[0].when.any_features.is_empty()
                    && rules[0].when.none_features.is_empty(),
                "actual provider source rule differs from reviewed sparse membership"
            );
            // All Java is processed as a template by the actual collector;
            // membership does not assume the non-Java opt-in template bit.
            sources.insert(golden.path.to_owned(), raw);
        }
        Ok(Self { core, sources })
    }
    fn inventory(&self) -> BTreeSet<String> {
        self.sources.keys().cloned().collect()
    }
    fn render(&self, path: &str, context: &ProjectionContext) -> Result<Option<Vec<u8>>> {
        let selected = select_core_inputs(&self.core.metadata, context, &self.inventory())?;
        if let Some(input) = selected.inputs.get(path) {
            ensure!(
                input.input == path && !selected.omitted_paths.contains(path),
                "provider alias/conflicting omission"
            );
            Ok(Some(
                render_java_source(std::str::from_utf8(&self.sources[path])?, context)?
                    .into_bytes(),
            ))
        } else {
            ensure!(
                selected.omitted_paths.contains(path),
                "provider omission is not explicit"
            );
            Ok(None)
        }
    }
    fn body(&self, path: &str, context: &ProjectionContext) -> Result<String> {
        String::from_utf8(
            self.render(path, context)?
                .ok_or_else(|| eyre::eyre!("provider body expected"))?,
        )
        .map_err(Into::into)
    }
    fn owned_peer_body(&self, path: &str, context: &ProjectionContext) -> Result<String> {
        let selected = select_core_inputs(
            &self.core.metadata,
            context,
            &BTreeSet::from([path.to_owned()]),
        )?;
        let input = selected
            .inputs
            .get(path)
            .ok_or_else(|| eyre::eyre!("actual required provider peer omitted: {path}"))?;
        render_java_source(
            std::str::from_utf8(&self.core.read_source(&input.input)?)?,
            context,
        )
    }
}
fn same_names(actual: &[String], expected: &[&str]) -> bool {
    actual.len() == expected.len()
        && actual.iter().map(String::as_str).collect::<BTreeSet<_>>()
            == expected.iter().copied().collect()
}
fn present_context(context: &str) -> bool {
    matches!(context, "dev/1.19.2" | "dev/1.19.4")
}
fn validate_raw(golden: &RawGolden, raw: &[u8]) -> Result<()> {
    ensure!(
        raw.len() == golden.bytes
            && sha256(raw) == golden.digest
            && !raw.contains(&b'\r')
            && raw.iter().filter(|b| **b == b'\n').count() == golden.lf
            && raw.ends_with(b"\n")
            && !raw.ends_with(b"\n\n"),
        "provider raw bytes changed"
    );
    let source = std::str::from_utf8(raw)?;
    ensure!(
        !source.contains("{%") && !source.contains("{{"),
        "provider contains an unreviewed directive"
    );
    Ok(())
}

#[test]
fn six_packet_provider_witnesses_cover_all_one_hundred_twenty_tree_cells() -> Result<()> {
    let fixture = Fixture::load()?;
    let paths = fixture
        .sources
        .keys()
        .map(|p| format!("platform/minecraft/{p}"))
        .collect::<Vec<_>>();
    let mut cells = 0;
    let mut present = 0;
    for (name, commit) in PINNED_CONTEXTS {
        let output = frozen_git_command(&fixture.core.repository)
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
            .output()
            .wrap_err("cannot read fixed offline provider tree")?;
        ensure!(output.status.success(), "provider tree query failed");
        let mut actual = BTreeMap::new();
        for line in std::str::from_utf8(&output.stdout)?.lines() {
            let (header, path) = line
                .split_once('\t')
                .ok_or_else(|| eyre::eyre!("malformed provider witness row"))?;
            let fields = header.split_whitespace().collect::<Vec<_>>();
            ensure!(
                fields.len() == 3
                    && fields[0] == "100644"
                    && fields[1] == "blob"
                    && paths.iter().any(|p| p == path)
                    && actual
                        .insert(path.to_owned(), fields[2].to_owned())
                        .is_none(),
                "unexpected provider witness member"
            );
        }
        let expected = if present_context(name) {
            GOLDENS
                .iter()
                .map(|g| (format!("platform/minecraft/{}", g.path), g.oid.to_owned()))
                .collect()
        } else {
            BTreeMap::new()
        };
        assert_eq!(actual, expected, "provider membership changed for {name}");
        present += actual.len();
        cells += GOLDENS.len();
    }
    assert_eq!((cells, present, cells - present), (120, 12, 108));
    Ok(())
}

#[test]
fn packet_providers_real_selector_renderer_reconstructs_all_twenty_source_contexts() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut present = 0;
    let mut absent = 0;
    for (name, _) in PINNED_CONTEXTS {
        let (environment, target) = name.split_once('/').expect("fixed context");
        let mut context = fixture.core.context(
            target,
            if present_context(name) {
                &OWNER_FLAGS
            } else {
                &[]
            },
        )?;
        context.environment = environment.to_owned();
        if target == "1.21.0" {
            assert_eq!(context.minecraft_version, "1.21");
        }
        for (path, raw) in &fixture.sources {
            if present_context(name) {
                assert_eq!(
                    fixture.render(path, &context)?.as_deref(),
                    Some(raw.as_slice())
                );
                present += 1;
            } else {
                assert!(fixture.render(path, &context)?.is_none());
                absent += 1;
            }
        }
    }
    assert_eq!((present, absent), (12, 108));
    Ok(())
}

#[test]
fn packet_providers_masks_refuse_unclosed_or_unsupported_owners_and_ignore_labels() -> Result<()> {
    let fixture = Fixture::load()?;
    let masks: [&[&str]; 5] = [&[], &[VALUES], &[CLEANUP], &[VALUES, CLEANUP], &OWNER_FLAGS];
    let mut cells = 0;
    let mut included = 0;
    for target in D2 {
        for flags in masks {
            let context = fixture.core.context(target, flags)?;
            let mut renamed = context.clone();
            renamed.environment = "release".to_owned();
            renamed.projection_key = "synthetic/nested/provider-proof".to_owned();
            renamed.preset = "unrelated-visible-label".to_owned();
            for (path, raw) in &fixture.sources {
                let output = fixture.render(path, &context)?;
                assert_eq!(output, fixture.render(path, &renamed)?);
                if flags.contains(&OWNER) {
                    assert_eq!(output.as_deref(), Some(raw.as_slice()));
                    included += 1;
                } else {
                    assert!(output.is_none());
                }
                cells += 1;
            }
        }
        for (flags, missing) in [
            (&[OWNER][..], VALUES),
            (&[OWNER, VALUES][..], CLEANUP),
            (&[OWNER, CLEANUP][..], VALUES),
        ] {
            assert!(
                fixture
                    .core
                    .context(target, flags)
                    .expect_err("provider owner cannot silently enable a prerequisite")
                    .to_string()
                    .contains(missing)
            );
        }
    }
    assert_eq!((cells, included, cells - included), (60, 12, 48));
    let mut unsupported = 0;
    for (target, _) in SUPPORTED_TARGETS {
        if !D2.contains(&target) {
            assert!(fixture.core.context(target, &OWNER_FLAGS).is_err());
            unsupported += 1;
        }
    }
    assert_eq!(unsupported, 8);
    let mut missing = fixture.core.context(D2[0], &OWNER_FLAGS)?;
    assert_eq!(missing.features.remove(OWNER), Some(true));
    assert!(select_core_inputs(&fixture.core.metadata, &missing, &fixture.inventory()).is_err());
    Ok(())
}

#[test]
fn packet_providers_preserve_actual_lazy_pattern_and_context_owned_materialization_boundaries()
-> Result<()> {
    let fixture = Fixture::load()?;
    for target in D2 {
        let context = fixture.core.context(target, &OWNER_FLAGS)?;
        let construction = fixture.body(
            &format!("{AST}ObjectConstructionValueExpression.java"),
            &context,
        )?;
        assert!(construction.contains("relationVariables.size() > 1"));
        assert!(construction.contains("ProgramValueReference.lazy(() -> construct(null))"));
        assert!(
            construction.contains("row.mapValue(ProgramValueReference.lazy(() -> construct(row)))")
        );
        assert!(construction.contains("if (!pattern.matches(value))"));
        let fields = fixture.body(&format!("{AST}ObjectFieldValueExpression.java"), &context)?;
        assert!(fields.contains("UUID.randomUUID().toString()"));
        assert!(fields.contains("return reference.get();"));
        let generated = fixture.body(
            &format!("{PROGRAM}GeneratedItemProgramInputSource.java"),
            &context,
        )?;
        let simulation = generated
            .find("if (!context.getBehaviour().allowsRuntimeMaterialization())")
            .ok_or_else(|| eyre::eyre!("materialization simulation boundary missing"))?;
        let creation = generated
            .find("valueConstructor.get()")
            .ok_or_else(|| eyre::eyre!("real materialization provider missing"))?;
        assert!(simulation < creation);
        assert!(generated.contains("materializedOwner != null && materializedOwner != owner"));
        assert!(generated.contains("if (materializationFailure != null)"));
        assert!(generated.contains("materializationFailure = failure;"));
        assert!(generated.contains("owner.own(createdResource);"));
        assert!(
            generated.contains("createdResource.onDrained(() -> owner.release(createdResource));")
        );
        assert!(generated.contains("PacketItem::create"));
        assert!(generated.contains("LimitedInputSlotObjectPool.acquireGenerated("));
        let free = generated
            .split("public void free() {")
            .nth(1)
            .ok_or_else(|| eyre::eyre!("generated view cleanup missing"))?
            .split("public boolean isMaterialized()")
            .next()
            .expect("fixed cleanup member");
        assert!(free.contains("LimitedInputSlotObjectPool.release(slot);"));
        assert!(!free.contains("resource.free(") && !free.contains("owner.release("));
        let storage = fixture.body(
            &format!("{PROGRAM}ProgramEphemeralItemResource.java"),
            &context,
        )?;
        assert!(storage.contains("if (freed)"));
        assert!(storage.contains("handler.onDrained = null;"));
        assert!(storage.contains("handler.setStackInSlot(0, ItemStack.EMPTY);"));
        assert!(storage.contains("if (!simulate && !extracted.isEmpty()"));
        assert!(storage.contains("callback.run();"));
        assert!(
            fixture
                .owned_peer_body(
                    &format!("{PROGRAM}SimulateExploreAllPathsProgramBehaviour.java"),
                    &context
                )?
                .contains("public boolean allowsRuntimeMaterialization() {\n        return false;")
        );
        for (path, anchor) in [
            (
                format!("{PROGRAM}ProgramBehaviour.java"),
                "allowsRuntimeMaterialization()",
            ),
            (
                format!("{PROGRAM}ProgramContext.java"),
                "getEphemeralResourceOwner()",
            ),
            (
                format!("{PROGRAM}ProgramEphemeralResourceOwner.java"),
                "resource.free();",
            ),
            (format!("{PROGRAM}ProgramInputSource.java"), "gatherSlots("),
            (
                format!("{PROGRAM}ProgramInputForgetRequest.java"),
                "allInputs",
            ),
            (
                format!("{PROGRAM}LimitedInputSlotObjectPool.java"),
                "acquireGenerated(",
            ),
            (
                "src/main/java/ca/teamdman/sfm/common/item/PacketItem.java".to_owned(),
                "create(SFMValue",
            ),
            (format!("{PROGRAM}ProgramRelation.java"), "row.mapValue("),
            (
                format!("{PROGRAM}ProgramValueReference.java"),
                "catch (RuntimeException | Error evaluationFailure)",
            ),
        ] {
            assert!(
                fixture.owned_peer_body(&path, &context)?.contains(anchor),
                "actual provider peer API changed: {path}"
            );
        }
    }
    Ok(())
}

#[test]
fn one_isolated_provider_edit_reaches_both_targets_without_promoting_other_owners() -> Result<()> {
    let fixture = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let core = temp.path().join(CORE_ROOT);
    for (path, bytes) in &fixture.sources {
        let output = core.join(path);
        fs::create_dir_all(
            output
                .parent()
                .ok_or_else(|| eyre::eyre!("isolated provider parent missing"))?,
        )?;
        fs::write(output, bytes)?;
    }
    let path = format!("{PROGRAM}ProgramEphemeralResource.java");
    let before = std::str::from_utf8(&fixture.sources[&path])?;
    const ANCHOR: &str =
        "/** A trigger-local resource that must be released when its execution ends. */";
    const EDIT: &str = "/** Shared provider authoring probe: a trigger-local resource. */";
    ensure!(
        before.matches(ANCHOR).count() == 1,
        "shared provider edit anchor changed"
    );
    let edited = before.replacen(ANCHOR, EDIT, 1);
    fs::write(core.join(&path), edited.as_bytes())?;
    let inventory = discover_core_source_files(&core)?;
    assert_eq!(inventory, fixture.inventory());
    for target in D2 {
        let context = fixture.core.context(target, &OWNER_FLAGS)?;
        let selection = select_core_inputs(&fixture.core.metadata, &context, &inventory)?;
        assert_eq!(selection.inputs[&path].input, path);
        let input = read_bounded(&checked_file(&core, &path)?, MAX_BYTES)?;
        assert_eq!(
            render_java_source(std::str::from_utf8(&input)?, &context)?,
            edited
        );
        for flags in [&[][..], &[VALUES, CLEANUP][..]] {
            let off = fixture.core.context(target, flags)?;
            assert!(
                select_core_inputs(&fixture.core.metadata, &off, &inventory)?
                    .omitted_paths
                    .contains(&path)
            );
        }
    }
    for (path, raw) in &fixture.sources {
        assert_eq!(fixture.core.read_source(path)?, *raw);
    }
    Ok(())
}

#[test]
fn packet_providers_raw_identity_refuses_eol_eof_or_token_normalization() -> Result<()> {
    let fixture = Fixture::load()?;
    for golden in &GOLDENS {
        let raw = &fixture.sources[golden.path];
        validate_raw(golden, raw)?;
        assert!(
            validate_raw(
                golden,
                std::str::from_utf8(raw)?.replace('\n', "\r\n").as_bytes()
            )
            .is_err()
        );
        assert!(validate_raw(golden, &raw[..raw.len() - 1]).is_err());
        let mut extra = raw.clone();
        extra.push(b'\n');
        assert!(validate_raw(golden, &extra).is_err());
        let mut token = raw.clone();
        token[0] = b'P';
        assert!(validate_raw(golden, &token).is_err());
    }
    Ok(())
}
