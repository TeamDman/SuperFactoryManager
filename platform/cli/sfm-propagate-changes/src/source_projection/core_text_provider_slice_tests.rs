//! Exact text-adapter source, membership and real Disk provider seams.
//! Ignored-only until promotion. No staged source fallback or Java runtime claim.

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

const RECEIPT: &str = "docs/tasks/sfm-core-text-provider-disk-consumer-slice-v2.json";
const PC: &str = "packet_computation";
const PC_FLAGS: [&str; 3] = [PC, "packet_values", "runtime_resource_cleanup"];
const DISK: &str = "src/main/java/ca/teamdman/sfm/common/item/DiskItem.java";
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
    oid: &'static str,
    digest: &'static str,
    bytes: usize,
    lf: usize,
}
const GOLDENS: [Golden; 2] = [
    Golden {
        path: "src/main/java/ca/teamdman/sfml/ast/SFMTextResourceAdapters.java",
        oid: "aa263bdbbe346a973739b508d998cd5f96df31e1",
        digest: "sha256:38d4be845066ac8a1345c2fd3fb8c18bfd20ed62f14ddd4e6f59cea64f2e5ee0",
        bytes: 2058,
        lf: 55,
    },
    Golden {
        path: "src/main/java/ca/teamdman/sfml/ast/TextReadValueExpression.java",
        oid: "deea8ebb8a90ed250346a906c987006bea629ea4",
        digest: "sha256:6e4084f8d19f86d608533e71c78ffc037ffeec6bd1a27d20bd98ab83ec329265",
        bytes: 1842,
        lf: 44,
    },
];
#[derive(Facet)]
struct Receipt {
    context_commits: BTreeMap<String, String>,
    text_files: BTreeMap<String, TextFile>,
}
#[derive(Facet)]
struct TextFile {
    source_blob: String,
    source_sha256: String,
    source_bytes: usize,
    feature_owner: String,
    supported_targets: Vec<String>,
    prerequisites: Vec<String>,
    line_feed_count: usize,
    carriage_return_count: usize,
    normalized: bool,
    witnesses: BTreeMap<String, Option<String>>,
}
struct Fixture {
    core: CoreTestFixture,
    sources: BTreeMap<String, Vec<u8>>,
}
impl Fixture {
    fn load() -> Result<Self> {
        let core = CoreTestFixture::load()?;
        let receipt: Receipt = facet_json::from_str(std::str::from_utf8(&read_bounded(
            &checked_file(&core.repository, RECEIPT)?,
            MAX_BYTES,
        )?)?)?;
        ensure!(
            receipt.context_commits
                == CONTEXTS
                    .into_iter()
                    .map(|(n, c)| (n.to_owned(), c.to_owned()))
                    .collect()
                && receipt.text_files.len() == 2,
            "text frozen source scope changed"
        );
        let owner = core
            .features
            .0
            .get(PC)
            .ok_or_else(|| eyre::eyre!("missing text owner"))?;
        ensure!(
            owner.supported_targets == ["1.19.2", "1.19.4"]
                && owner.requires == ["packet_values", "runtime_resource_cleanup"],
            "text must not widen packet support or prerequisites"
        );
        let raw = read_git_blobs(
            &core.repository,
            &GOLDENS.iter().map(|g| g.oid.to_owned()).collect(),
        )?;
        let mut sources = BTreeMap::new();
        for g in &GOLDENS {
            let e = receipt
                .text_files
                .get(g.path)
                .ok_or_else(|| eyre::eyre!("missing text path"))?;
            ensure!(
                e.source_blob == g.oid
                    && e.source_sha256 == g.digest
                    && e.source_bytes == g.bytes
                    && e.feature_owner == PC
                    && e.supported_targets == ["1.19.2", "1.19.4"]
                    && e.prerequisites == ["packet_values", "runtime_resource_cleanup"]
                    && e.line_feed_count == g.lf
                    && e.carriage_return_count == 0
                    && !e.normalized
                    && e.witnesses
                        == CONTEXTS
                            .into_iter()
                            .map(|(name, _)| (
                                name.to_owned(),
                                present(name).then(|| g.oid.to_owned())
                            ))
                            .collect(),
                "text raw witness/owner evidence changed"
            );
            let bytes = core.read_source(g.path)?;
            ensure!(
                bytes == raw[g.oid]
                    && bytes.len() == g.bytes
                    && sha256(&bytes) == g.digest
                    && !bytes.contains(&b'\r')
                    && bytes.ends_with(b"\n")
                    && bytes.iter().filter(|b| **b == b'\n').count() == g.lf,
                "text raw source changed"
            );
            let rules = core
                .metadata
                .source_rules
                .get(g.path)
                .ok_or_else(|| eyre::eyre!("text rule missing"))?;
            ensure!(
                rules.len() == 1
                    && rules[0].input == g.path
                    && rules[0].when.targets == ["1.19.2", "1.19.4"]
                    && rules[0].when.all_features == [PC]
                    && rules[0].when.any_features.is_empty()
                    && rules[0].when.none_features.is_empty(),
                "actual text sparse ownership changed"
            );
            sources.insert(g.path.to_owned(), bytes);
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

#[test]
fn raw_text_pair_reconstructs_all_forty_frozen_tree_and_selector_cells() -> Result<()> {
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
        ensure!(output.status.success(), "offline text tree query failed");
        let mut actual = BTreeMap::new();
        for line in std::str::from_utf8(&output.stdout)?.lines() {
            let (header, path) = line
                .split_once('\t')
                .ok_or_else(|| eyre::eyre!("bad text witness"))?;
            let fields = header.split_whitespace().collect::<Vec<_>>();
            ensure!(
                fields.len() == 3
                    && fields[0] == "100644"
                    && fields[1] == "blob"
                    && paths.iter().any(|p| p == path)
                    && actual
                        .insert(path.to_owned(), fields[2].to_owned())
                        .is_none(),
                "bad text tree member"
            );
        }
        let expected = if present(name) {
            GOLDENS
                .iter()
                .map(|g| (format!("platform/minecraft/{}", g.path), g.oid.to_owned()))
                .collect()
        } else {
            BTreeMap::new()
        };
        assert_eq!(actual, expected);
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
    assert_eq!((included, omitted), (4, 36));
    Ok(())
}

#[test]
fn text_owner_masks_use_only_the_real_program_getter_union() -> Result<()> {
    let f = Fixture::load()?;
    for target in ["1.19.2", "1.19.4"] {
        for flags in [
            &[][..],
            &["packet_values"][..],
            &["runtime_resource_cleanup"][..],
            &["packet_values", "runtime_resource_cleanup"][..],
        ] {
            let context = f.core.context(target, flags)?;
            let selected = select_core_inputs(&f.core.metadata, &context, &f.inventory())?;
            for path in f.sources.keys() {
                assert!(selected.omitted_paths.contains(path));
            }
        }
        for readonly in [false, true] {
            let mut flags = PC_FLAGS.to_vec();
            if readonly {
                flags.push("disk_readonly_access");
            }
            let context = f.core.context(target, &flags)?;
            let selected = select_core_inputs(
                &f.core.metadata,
                &context,
                &BTreeSet::from([DISK.to_owned()]),
            )?;
            assert_eq!(selected.inputs[DISK].input, DISK);
            let disk =
                render_java_source(std::str::from_utf8(&f.core.read_source(DISK)?)?, &context)?;
            assert!(disk.contains("getProgramStringReadOnly(ItemStack stack)"));
            assert_eq!(
                disk.contains("getProgramNameReadOnly(ItemStack stack)"),
                readonly
            );
            assert!(
                !disk.contains("import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;")
            );
            for (path, raw) in &f.sources {
                let mut renamed = context.clone();
                renamed.environment = "release".to_owned();
                renamed.projection_key = "synthetic/text/provider-proof".to_owned();
                assert_eq!(
                    render_java_source(std::str::from_utf8(raw)?, &context)?,
                    render_java_source(std::str::from_utf8(raw)?, &renamed)?,
                    "label affected {path}"
                );
            }
        }
        assert!(f.core.context(target, &[PC]).is_err());
        let mut missing = f.core.context(target, &PC_FLAGS)?;
        missing.features.remove(PC);
        assert!(select_core_inputs(&f.core.metadata, &missing, &f.inventory()).is_err());
    }
    for (target, _) in super::projection_catalog::SUPPORTED_TARGETS {
        if !matches!(target, "1.19.2" | "1.19.4") {
            assert!(f.core.context(target, &PC_FLAGS).is_err());
        }
    }
    Ok(())
}

#[test]
fn text_provider_source_contracts_remain_copied_nonmutating_and_not_client_actions() -> Result<()> {
    let f = Fixture::load()?;
    let adapter = std::str::from_utf8(&f.sources[GOLDENS[0].path])?;
    let expression = std::str::from_utf8(&f.sources[GOLDENS[1].path])?;
    assert!(adapter.contains("DiskItem.getProgramStringReadOnly(stack)"));
    assert!(adapter.contains("var tag = stack.getTag();"));
    assert!(!adapter.contains("getOrCreateTag"));
    assert!(adapter.contains("Component.Serializer.fromJson(page)"));
    assert!(adapter.contains("Malformed written-book page "));
    assert!(adapter.contains("String.join(\"\\n\", text)"));
    assert!(expression.contains("if (!operationId.equals(\"sfm:text/read\"))"));
    assert!(expression.contains("ItemStack copiedStack = itemStack.copy();"));
    assert!(expression.contains(
        "ProgramValueReference.lazy(() -> SFMValue.of(SFMTextResourceAdapters.read(copiedStack)))"
    ));
    assert!(!expression.contains("SFMClientActions") && !adapter.contains("SFMClientActions"));
    for target in ["1.19.2", "1.19.4"] {
        let context = f.core.context(target, &PC_FLAGS)?;
        for (path, anchor) in [
            (
                "src/main/java/ca/teamdman/sfml/ast/ProgramValueExpression.java",
                "ProgramRelation evaluate",
            ),
            (
                "src/main/java/ca/teamdman/sfml/ast/ProgramInputSelection.java",
                "SFMTextResourceAdapters.supports(itemStack)",
            ),
            (
                "src/main/java/ca/teamdman/sfm/common/program/ProgramValueReference.java",
                "catch (RuntimeException | Error evaluationFailure)",
            ),
        ] {
            let selected = select_core_inputs(
                &f.core.metadata,
                &context,
                &BTreeSet::from([path.to_owned()]),
            )?;
            let input = selected
                .inputs
                .get(path)
                .ok_or_else(|| eyre::eyre!("actual text peer omitted"))?;
            assert!(
                render_java_source(
                    std::str::from_utf8(&f.core.read_source(&input.input)?)?,
                    &context
                )?
                .contains(anchor)
            );
        }
    }
    Ok(())
}
#[test]
fn isolated_text_adapter_edit_reaches_d2_without_live_source_writes() -> Result<()> {
    let f = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let core = temp.path().join(CORE_ROOT);
    for (path, bytes) in &f.sources {
        let out = core.join(path);
        fs::create_dir_all(out.parent().expect("fixed fixture parent"))?;
        fs::write(out, bytes)?;
    }
    let path = GOLDENS[0].path;
    let raw = std::str::from_utf8(&f.sources[path])?;
    let edited = raw.replace(
        "/** Read-only adapters for the initial {@code sfm:text} capability. */",
        "/** Shared text adapter authoring probe. */",
    );
    ensure!(edited != raw, "text common-edit anchor changed");
    fs::write(core.join(path), edited.as_bytes())?;
    let inventory = discover_core_source_files(&core)?;
    assert_eq!(inventory, f.inventory());
    for target in ["1.19.2", "1.19.4"] {
        let context = f.core.context(target, &PC_FLAGS)?;
        let selected = select_core_inputs(&f.core.metadata, &context, &inventory)?;
        assert_eq!(selected.inputs[path].input, path);
        let source = read_bounded(&checked_file(&core, path)?, MAX_BYTES)?;
        assert_eq!(
            render_java_source(std::str::from_utf8(&source)?, &context)?,
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
