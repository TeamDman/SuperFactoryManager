//! Test-only byte/membership regression for nine authored action/carrier inputs.
//!
//! The production selector runs before any Java input read, and the production
//! controlled renderer produces every compared body. Pinned historical blobs
//! are test witnesses only. These tests do not establish Java dependency
//! closure, compilation, executable permissions or gameplay acceptance.

#![cfg(test)]

use super::candidate_lock::checked_file;
use super::context::ProjectionContext;
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

const REQUIREMENT: &str =
    "src/main/java/ca/teamdman/sfm/client/action/SFMClientActionRequirement.java";
const AVAILABILITY: &str =
    "src/main/java/ca/teamdman/sfm/client/action/SFMClientActionAvailability.java";
const ACTION: &str = "src/main/java/ca/teamdman/sfm/client/action/SFMClientAction.java";
const CONTEXT: &str = "src/main/java/ca/teamdman/sfm/client/action/SFMClientActionContext.java";
const SOURCE: &str = "src/main/java/ca/teamdman/sfm/client/action/SFMClientActionSource.java";
const EXECUTOR: &str = "src/main/java/ca/teamdman/sfm/client/action/SFMClientActionExecutor.java";
const COMPILER: &str =
    "src/main/java/ca/teamdman/sfm/client/action/SFMClientActionDispatcherCompiler.java";
const TREE: &str = "src/main/java/ca/teamdman/sfm/client/action/SFMClientActionCommandTree.java";
const ICON: &str = "src/main/java/ca/teamdman/sfm/client/presentation/SFMItemIcon.java";
const PATHS: [&str; 9] = [
    REQUIREMENT,
    AVAILABILITY,
    ACTION,
    CONTEXT,
    SOURCE,
    EXECUTOR,
    COMPILER,
    TREE,
    ICON,
];
const TARGETS: [&str; 10] = [
    "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
    "26.1.2",
];
const BASIC_LEDGER: &str = "docs/tasks/sfm-core-basic-actions-first-slice.json";
const SECOND_LEDGER: &str = "docs/tasks/sfm-core-basic-actions-second-slice.json";
const FOUNDATION_LEDGER: &str = "docs/tasks/sfm-core-action-foundation-first-slice.json";
const ICON_LEDGER: &str = "docs/tasks/sfm-core-item-icon-carrier-slice.json";
const CORE_PREFIX: &str = "platform/minecraft/core-liquid-template/";

// Explicit reviewed sets, not error-driven expansion or synthetic feature
// registrations. CoreTestFixture::context validates every prerequisite against
// the live core registry; a new required permission/feature fails these fixtures.
const D2_WITNESS_FEATURES: [&str; 19] = [
    "client_actions",
    "client_theme",
    "keyboard_profiles",
    "workspace_panels",
    "structured_action_results",
    "command_palette",
    "typed_command_palette",
    "command_history",
    "context_actions",
    "client_action_completion_diagnostics",
    "client_action_nullable_suggestions",
    "packet_values",
    "runtime_resource_cleanup",
    "packet_computation",
    "client_program_consent",
    "disk_readonly_access",
    "client_manager",
    "client_program_actions",
    "sfml_execution_side",
];
const OTHER_WITNESS_FEATURES: [&str; 5] = [
    "client_actions",
    "client_theme",
    "keyboard_profiles",
    "workspace_panels",
    "command_palette",
];
const D2_FOUNDATION_FEATURES: [&str; 10] = [
    "client_actions",
    "client_theme",
    "keyboard_profiles",
    "workspace_panels",
    "command_palette",
    "typed_command_palette",
    "command_history",
    "context_actions",
    "client_action_completion_diagnostics",
    "client_action_nullable_suggestions",
];
const OTHER_FOUNDATION_FEATURES: [&str; 4] = [
    "client_actions",
    "client_theme",
    "keyboard_profiles",
    "command_palette",
];
const PROGRAM_PREREQUISITES: [&str; 8] = [
    "packet_values",
    "runtime_resource_cleanup",
    "packet_computation",
    "client_program_consent",
    "disk_readonly_access",
    "client_manager",
    "client_program_actions",
    "sfml_execution_side",
];

#[derive(Clone, Copy)]
struct RawGolden {
    oid: &'static str,
    sha256: &'static str,
    bytes: usize,
}

const RAW_GOLDENS: [RawGolden; 21] = [
    RawGolden {
        oid: "7f567bec525a02c7e55dd11f4c79a29813308598",
        sha256: "sha256:5e54dad979d9f81b74820b1d2810b42487df1e4bd34b80a33cc20e0542d742ba",
        bytes: 188,
    },
    RawGolden {
        oid: "4403688a7d9d66fc45dff3e19c2c12a8961cd250",
        sha256: "sha256:aea89e3a2ac072f69579a9447d44d4ce2cba5f0551bb2a3f6e704a3e21618afb",
        bytes: 1363,
    },
    RawGolden {
        oid: "d44844528157d93f958f3649cce66bfca2ff39f0",
        sha256: "sha256:92fdf8e1a9d07a2ad9376ae8b951f960096db4ab3effb0e17fded516aa32bdc8",
        bytes: 2604,
    },
    RawGolden {
        oid: "3fa0d67a2b5501729f2baabb7179900448113163",
        sha256: "sha256:39d92e5600c36a6c838d70253412ce18ee0dba233cff0d3b502fc9a71e446af3",
        bytes: 2129,
    },
    RawGolden {
        oid: "4327d2599deb2f0368ae0d99e2772fe70a18c06a",
        sha256: "sha256:84a59da287f02fd5def1822b76bd183d0e8896a23c01eb3043801ad4ada2307d",
        bytes: 2117,
    },
    RawGolden {
        oid: "374f71d3a29c4d3a21579666084b4c89728bb121",
        sha256: "sha256:6bc1d4c12270474409efe0e4a55058db5457d74e3134d0f4909754b5464e1946",
        bytes: 2220,
    },
    RawGolden {
        oid: "20a8bb876939aae2e66e638ab77e383579e1b9b5",
        sha256: "sha256:a94e24aca185f66f684c261b5503359a6c7828f62882a55c5e1c912b789c9f7d",
        bytes: 1851,
    },
    RawGolden {
        oid: "3db22af9e1ec587fadfe4d3237e955aaead24592",
        sha256: "sha256:51da8ed1d61300383b964905f2ecf17911d727b7e5035d32751eafb45c608c81",
        bytes: 1116,
    },
    RawGolden {
        oid: "2bac8cc4c5d5ba3885460d9dc803da754e921140",
        sha256: "sha256:61331b2c36d7befaf097013e5ab88b6dcf37c36b81b6ae2e10a9bed6659e4075",
        bytes: 629,
    },
    RawGolden {
        oid: "ba5540c5510da6d5e551a323388fee0fbfb3e8a0",
        sha256: "sha256:486404b1a85b2c20abb0f744719d4a70dc2352a63f53f12ce8a3b0e6a2baa587",
        bytes: 2774,
    },
    RawGolden {
        oid: "13d449260d1777ceaff5c8876254d14a6fdcfea9",
        sha256: "sha256:9fc9cc0af6abb539dff9ca93d9a3f5c1042d49a7ab056f412e176987076330fd",
        bytes: 1028,
    },
    RawGolden {
        oid: "aaf74f07139f6ff65f5f9c698b6fd51039c84193",
        sha256: "sha256:587f1a252f70491ee02bc63ae85cf7d81dfb563964ca019633c71a32ff00f871",
        bytes: 1309,
    },
    RawGolden {
        oid: "12341b182af3b6b3aebfbe1c1fb3d472b4ff64a2",
        sha256: "sha256:17a991a3d575a4e1c3b746d1ac3d7cdd4d70de9aa31bd179ae0c7f994133069f",
        bytes: 1214,
    },
    RawGolden {
        oid: "f24da6900ab87461eeb378234c09f2136760bdf1",
        sha256: "sha256:e9cdb7ac145253ad9d2fced3fa0214ff8d7bce2540aebce6b6d07c7d50778777",
        bytes: 1311,
    },
    RawGolden {
        oid: "4c5441d66d2a65eab6da12fb9bf498791ce2e0ac",
        sha256: "sha256:e50c8529cfcc1775250ff69feec620c5278a0db3aee94a753c926d39c9603c83",
        bytes: 1287,
    },
    RawGolden {
        oid: "9cd2a21751c78a00284f4bf7b78f75a1f229fd9f",
        sha256: "sha256:beaad9fe0117ceb9fd4a8276527862d6e959f0b98bb9697d478892d15bc57da0",
        bytes: 7196,
    },
    RawGolden {
        oid: "3594fa16439dc76885e5800fd1477370e63b9617",
        sha256: "sha256:67ae167e7377b6d505f9cb8a3a49c639f5fcf5754448d90d563bf88cf9f81fa9",
        bytes: 6395,
    },
    RawGolden {
        oid: "e9bf9205e92b2d8911bc4eeec99a5cd5e5fd1900",
        sha256: "sha256:0a9f9c65a7384b13a1ee2e163c4d49a75564cf07fc1d45cb84a8fc52241d862c",
        bytes: 6317,
    },
    RawGolden {
        oid: "3319a2b5e9c7d4d8a22742b5aa3e7c37758cb673",
        sha256: "sha256:d839dc8bc0b4cbc53d9a31cbb964554b05c30223046923d339cd81d4e62cb939",
        bytes: 37199,
    },
    RawGolden {
        oid: "8d9d89ce1a46b809ee377c70ceaed402d05db873",
        sha256: "sha256:7e07482be63998772fcf813406c3853d9fdfccc7ce60baf4e9af31dc060f77fd",
        bytes: 6543,
    },
    RawGolden {
        oid: "c17434774346305d27cfc59f55b0337994d375b0",
        sha256: "sha256:189caf6294a23faa3dcad081d75ec4d3dac1f6988ab2496081759ec78b8e3537",
        bytes: 6489,
    },
];

const CONTEXT_COMMITS: [(&str, &str); 20] = [
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

// Deliberately parse only fixed witness contracts. Prose and evidence extensions
// are not executable selectors or a second production catalog.
#[derive(Facet)]
struct BasicLedger {
    schema: String,
    files: BTreeMap<String, BasicFile>,
}

#[derive(Facet)]
struct BasicFile {
    core_path: String,
    feature_owner: String,
    witness_context: String,
    witness_commit: String,
    witness_blob: String,
    source_sha256: String,
    source_bytes: usize,
    target_context_commits: BTreeMap<String, String>,
    suggested_inclusion: BasicMembership,
}

#[derive(Facet)]
struct BasicMembership {
    supported_targets: Vec<String>,
    release_target_membership: Vec<String>,
}

#[derive(Facet)]
struct SecondLedger {
    schema: String,
    files: BTreeMap<String, SecondFile>,
    exact_full_off_reconstruction_expectations: SecondExpectations,
}

#[derive(Facet)]
struct SecondExpectations {
    context_commits: BTreeMap<String, String>,
    flags_by_context: BTreeMap<String, BTreeMap<String, bool>>,
}

#[derive(Facet)]
struct SecondFile {
    core_path: String,
    feature_owner: String,
    authored_template: TemplateWitness,
    source_witness_variants: Vec<VariantWitness>,
    suggested_inclusion: SecondMembership,
}

#[derive(Facet)]
struct SecondMembership {
    supported_targets: Vec<String>,
    release_target_membership: Vec<String>,
}

#[derive(Facet)]
struct TemplateWitness {
    sha256: String,
    bytes: usize,
}

#[derive(Facet)]
struct VariantWitness {
    contexts: Vec<String>,
    witness_context: String,
    witness_commit: String,
    witness_blob: String,
    source_sha256: String,
    source_bytes: usize,
}

#[derive(Facet)]
struct FoundationLedger {
    schema: String,
    source_commits: BTreeMap<String, String>,
    files: Vec<FoundationFile>,
}

#[derive(Facet)]
struct FoundationFile {
    path: String,
    core_path: String,
    template_sha256: String,
    template_byte_count: usize,
    witnesses: BTreeMap<String, FoundationWitness>,
}

#[derive(Facet)]
struct FoundationWitness {
    source_commit: String,
    present: bool,
    #[facet(default)]
    git_blob: Option<String>,
    #[facet(default)]
    source_sha256: Option<String>,
    #[facet(default)]
    byte_count: Option<usize>,
    #[facet(default)]
    mode: Option<String>,
    validated_registered_feature_closure: Vec<String>,
}

#[derive(Facet)]
struct IconLedger {
    schema: String,
    core_relative_file: String,
    core_path: String,
    context_commits: BTreeMap<String, String>,
    authored_template: IconTemplate,
    raw_witnesses: Vec<VariantWitness>,
    source_membership_recommendation: IconMembership,
}

#[derive(Facet)]
struct IconTemplate {
    source_sha256: String,
    source_bytes: usize,
}

#[derive(Facet)]
struct IconMembership {
    supported_targets: Vec<String>,
    released_membership: Vec<String>,
}

struct ActionFixture {
    core: CoreTestFixture,
    inventory: BTreeSet<String>,
    templates: BTreeMap<String, (String, usize)>,
    raw: BTreeMap<String, Vec<u8>>,
}

impl ActionFixture {
    fn load() -> Result<Self> {
        let core = CoreTestFixture::load()?;
        let mut templates = BTreeMap::new();
        validate_basic_ledger(&core, &mut templates)?;
        validate_second_ledger(&core, &mut templates)?;
        validate_foundation_ledger(&core, &mut templates)?;
        validate_icon_ledger(&core, &mut templates)?;
        ensure!(
            templates
                .keys()
                .map(String::as_str)
                .collect::<BTreeSet<_>>()
                == PATHS.into_iter().collect(),
            "action/carrier scope changed"
        );
        let raw = read_git_blobs(
            &core.repository,
            &RAW_GOLDENS.iter().map(|g| g.oid.to_owned()).collect(),
        )?;
        for golden in RAW_GOLDENS {
            let bytes = &raw[golden.oid];
            ensure!(
                bytes.len() == golden.bytes && sha256(bytes) == golden.sha256,
                "raw action witness changed: {}",
                golden.oid
            );
            ensure!(
                !bytes.contains(&b'\r') && bytes.ends_with(b"\n") && !bytes.ends_with(b"\n\n"),
                "action witness has unreviewed newline normalization"
            );
            std::str::from_utf8(bytes)?;
        }
        let inventory = discover_core_source_files(&core.core)?;
        ensure!(
            PATHS.iter().all(|p| inventory.contains(*p)),
            "missing authored action/carrier input"
        );
        Ok(Self {
            core,
            inventory,
            templates,
            raw,
        })
    }

    fn render(&self, path: &str, context: &ProjectionContext) -> Result<Option<Vec<u8>>> {
        let selection = self
            .core
            .selection_for_assertion(context, &self.inventory)?;
        let Some(input) = selection.inputs.get(path) else {
            ensure!(
                selection.omitted_paths.contains(path),
                "action path vanished outside its predicate"
            );
            return Ok(None);
        };
        ensure!(
            input.input == path,
            "action path selected an unreviewed alternate input"
        );
        // No Java bytes are read until the real source predicate includes it.
        let source = self.core.read_source(path)?;
        let (hash, count) = &self.templates[path];
        // The typed-palette consumer now shares the choice-surface API. Bind
        // that exact refinement separately; do not rewrite historical ledgers
        // or render their reconstructed bytes as today's source.
        let historical = if path == TREE {
            reviewed_pre_typed_choice_source(&source)?
        } else {
            source.clone()
        };
        ensure!(
            historical.len() == *count && sha256(&historical) == *hash,
            "authored action/carrier bytes changed from their reviewed ledger: {path}"
        );
        Ok(Some(
            render_java_source(std::str::from_utf8(&source)?, context)?.into_bytes(),
        ))
    }

    fn text(&self, path: &str, context: &ProjectionContext) -> Result<String> {
        let bytes = self
            .render(path, context)?
            .ok_or_else(|| eyre::eyre!("expected action input was omitted: {path}"))?;
        Ok(String::from_utf8(bytes)?)
    }
}

fn reviewed_pre_typed_choice_source(source: &[u8]) -> Result<Vec<u8>> {
    ensure!(
        source.len() == 50_433
            && sha256(source)
                == "sha256:7d8d545a38100f2082c7db8ec63a21959716cd345591ef5fc6a82c6e4aecd0fd",
        "current shared typed-choice template identity changed"
    );
    let text = std::str::from_utf8(source)?;
    let current = "{% if features.context_actions or features.typed_command_palette %}";
    ensure!(
        text.matches(current).count() == 10,
        "shared typed-choice guard refinement count changed"
    );
    let historical = text.replace(current, "{% if features.context_actions %}");
    ensure!(
        historical.len() == 50_093
            && sha256(historical.as_bytes())
                == "sha256:f14bb26e1ac5330cdbe847c97c649c1c3a5c7021f15f273e13ab01e57d1c6cda",
        "shared typed-choice inverse did not recover the immutable foundation"
    );
    Ok(historical.into_bytes())
}

#[test]
fn shared_typed_choice_refinement_preserves_history_and_rejects_unrelated_edits() -> Result<()> {
    let core = CoreTestFixture::load()?;
    let source = core.read_source(TREE)?;
    let historical = reviewed_pre_typed_choice_source(&source)?;
    let context = core.context("1.19.2", &foundation_features(3).expect("typed palette"))?;
    let current_output = render_java_source(std::str::from_utf8(&source)?, &context)?;
    let historical_output = render_java_source(std::str::from_utf8(&historical)?, &context)?;
    assert!(current_output.contains("isolatedPaletteSurface"));
    assert!(!historical_output.contains("isolatedPaletteSurface"));
    for mutation in [
        String::from_utf8(source.clone())?.replacen(
            "{% if features.context_actions or features.typed_command_palette %}",
            "{% if features.context_actions %}",
            1,
        ),
        format!("{}\n// unrelated mutation\n", std::str::from_utf8(&source)?),
    ] {
        assert!(reviewed_pre_typed_choice_source(mutation.as_bytes()).is_err());
    }
    Ok(())
}

fn ledger_source(core: &CoreTestFixture, path: &str) -> Result<String> {
    Ok(String::from_utf8(read_bounded(
        &checked_file(&core.repository, path)?,
        1024 * 1024,
    )?)?)
}

fn pinned_commits() -> BTreeMap<String, String> {
    CONTEXT_COMMITS
        .into_iter()
        .map(|(name, commit)| (name.to_owned(), commit.to_owned()))
        .collect()
}

fn validate_targets(actual: &[String]) -> Result<()> {
    ensure!(
        actual.len() == TARGETS.len()
            && actual.iter().map(String::as_str).collect::<BTreeSet<_>>()
                == TARGETS.into_iter().collect(),
        "action witness target membership changed"
    );
    Ok(())
}

fn raw_golden(oid: &str) -> Result<RawGolden> {
    RAW_GOLDENS
        .iter()
        .copied()
        .find(|g| g.oid == oid)
        .ok_or_else(|| eyre::eyre!("unreviewed action raw blob: {oid}"))
}

fn validate_raw_row(oid: &str, hash: &str, count: usize) -> Result<()> {
    let golden = raw_golden(oid)?;
    ensure!(
        golden.sha256 == hash && golden.bytes == count,
        "action witness hash/size evidence changed: {oid}"
    );
    Ok(())
}

fn register_template(
    templates: &mut BTreeMap<String, (String, usize)>,
    path: &str,
    hash: &str,
    count: usize,
) -> Result<()> {
    ensure!(
        PATHS.contains(&path)
            && hash.starts_with("sha256:")
            && count > 0
            && templates
                .insert(path.to_owned(), (hash.to_owned(), count))
                .is_none(),
        "unreviewed or duplicate action source template: {path}"
    );
    Ok(())
}

fn expected_oid(path: &str, target: &str) -> Result<&'static str> {
    let d2 = is_d2(target);
    let oid = match path {
        REQUIREMENT => "7f567bec525a02c7e55dd11f4c79a29813308598",
        AVAILABILITY => "4403688a7d9d66fc45dff3e19c2c12a8961cd250",
        ACTION if d2 => "d44844528157d93f958f3649cce66bfca2ff39f0",
        ACTION if target == "26.1.2" => "4327d2599deb2f0368ae0d99e2772fe70a18c06a",
        ACTION => "3fa0d67a2b5501729f2baabb7179900448113163",
        CONTEXT if d2 => "374f71d3a29c4d3a21579666084b4c89728bb121",
        CONTEXT => "20a8bb876939aae2e66e638ab77e383579e1b9b5",
        SOURCE if d2 => "3db22af9e1ec587fadfe4d3237e955aaead24592",
        SOURCE => "2bac8cc4c5d5ba3885460d9dc803da754e921140",
        EXECUTOR if d2 => "ba5540c5510da6d5e551a323388fee0fbfb3e8a0",
        EXECUTOR => "13d449260d1777ceaff5c8876254d14a6fdcfea9",
        COMPILER if d2 => "9cd2a21751c78a00284f4bf7b78f75a1f229fd9f",
        COMPILER if target == "26.1.2" => "e9bf9205e92b2d8911bc4eeec99a5cd5e5fd1900",
        COMPILER => "3594fa16439dc76885e5800fd1477370e63b9617",
        TREE if d2 => "3319a2b5e9c7d4d8a22742b5aa3e7c37758cb673",
        TREE if target == "26.1.2" => "c17434774346305d27cfc59f55b0337994d375b0",
        TREE => "8d9d89ce1a46b809ee377c70ceaed402d05db873",
        ICON if d2 => "aaf74f07139f6ff65f5f9c698b6fd51039c84193",
        ICON if target == "26.1.2" => "4c5441d66d2a65eab6da12fb9bf498791ce2e0ac",
        ICON if matches!(target, "1.21.0" | "1.21.1") => "f24da6900ab87461eeb378234c09f2136760bdf1",
        ICON => "12341b182af3b6b3aebfbe1c1fb3d472b4ff64a2",
        _ => eyre::bail!("unreviewed action path"),
    };
    ensure!(TARGETS.contains(&target), "unreviewed action target");
    Ok(oid)
}

fn is_d2(target: &str) -> bool {
    matches!(target, "1.19.2" | "1.19.4")
}

fn validate_basic_ledger(
    core: &CoreTestFixture,
    templates: &mut BTreeMap<String, (String, usize)>,
) -> Result<()> {
    let ledger: BasicLedger = facet_json::from_str(&ledger_source(core, BASIC_LEDGER)?)?;
    ensure!(
        ledger.schema == "sfm:core-basic-actions-first-slice@1" && ledger.files.len() == 2,
        "wrong basic carrier ledger"
    );
    let commits = pinned_commits();
    for path in [REQUIREMENT, AVAILABILITY] {
        let file = ledger
            .files
            .get(path)
            .ok_or_else(|| eyre::eyre!("missing basic carrier witness"))?;
        ensure!(
            file.core_path == format!("{CORE_PREFIX}{path}")
                && file.feature_owner == "client_actions"
                && file
                    .suggested_inclusion
                    .release_target_membership
                    .is_empty(),
            "basic carrier owner/release membership changed"
        );
        ensure!(
            file.witness_context == "dev/1.19.2"
                && file.witness_commit == commits[&file.witness_context],
            "basic carrier witness checkpoint changed"
        );
        validate_targets(&file.suggested_inclusion.supported_targets)?;
        let expected_commits = TARGETS
            .into_iter()
            .map(|target| (target.to_owned(), commits[&format!("dev/{target}")].clone()))
            .collect::<BTreeMap<_, _>>();
        ensure!(
            file.target_context_commits == expected_commits,
            "basic carrier commit pins changed"
        );
        ensure!(
            file.witness_blob == expected_oid(path, "1.19.2")?,
            "wrong basic raw witness"
        );
        validate_raw_row(&file.witness_blob, &file.source_sha256, file.source_bytes)?;
        register_template(templates, path, &file.source_sha256, file.source_bytes)?;
    }
    Ok(())
}

fn validate_variants(path: &str, variants: &[VariantWitness]) -> Result<()> {
    let mut contexts = BTreeSet::new();
    for row in variants {
        validate_raw_row(&row.witness_blob, &row.source_sha256, row.source_bytes)?;
        ensure!(!row.contexts.is_empty(), "empty action raw variant");
        ensure!(
            row.contexts.contains(&row.witness_context)
                && pinned_commits().get(&row.witness_context) == Some(&row.witness_commit),
            "action variant source checkpoint changed"
        );
        for name in &row.contexts {
            let target = name
                .strip_prefix("dev/")
                .ok_or_else(|| eyre::eyre!("nondevelopment action raw variant"))?;
            ensure!(
                contexts.insert(name.clone()) && row.witness_blob == expected_oid(path, target)?,
                "duplicate/wrong action raw context {name}: {path}"
            );
        }
    }
    ensure!(
        contexts == TARGETS.into_iter().map(|t| format!("dev/{t}")).collect(),
        "incomplete action raw context coverage"
    );
    Ok(())
}

fn validate_second_ledger(
    core: &CoreTestFixture,
    templates: &mut BTreeMap<String, (String, usize)>,
) -> Result<()> {
    let ledger: SecondLedger = facet_json::from_str(&ledger_source(core, SECOND_LEDGER)?)?;
    ensure!(
        ledger.schema == "sfm:core-basic-actions-second-slice@1" && ledger.files.len() == 4,
        "wrong second action slice"
    );
    let expectations = ledger.exact_full_off_reconstruction_expectations;
    ensure!(
        expectations.context_commits == pinned_commits(),
        "second slice commit pins changed"
    );
    ensure!(
        expectations.flags_by_context.len() == 20,
        "incomplete second slice feature witnesses"
    );
    for name in pinned_commits().keys() {
        let (environment, target) = name.split_once('/').expect("fixed contexts");
        let dev = environment == "dev";
        let expected = BTreeMap::from([
            ("client_actions".to_owned(), dev),
            ("client_program_actions".to_owned(), dev && is_d2(target)),
            ("workspace_panels".to_owned(), dev),
            ("structured_action_results".to_owned(), dev && is_d2(target)),
            ("keyboard_profiles".to_owned(), dev),
        ]);
        ensure!(
            expectations.flags_by_context.get(name) == Some(&expected),
            "second slice exact feature witness changed: {name}"
        );
    }
    for path in [ACTION, CONTEXT, SOURCE, EXECUTOR] {
        let file = ledger
            .files
            .get(path)
            .ok_or_else(|| eyre::eyre!("missing second action witness"))?;
        ensure!(
            file.core_path == format!("{CORE_PREFIX}{path}")
                && file.feature_owner == "client_actions"
                && file
                    .suggested_inclusion
                    .release_target_membership
                    .is_empty(),
            "second action owner/release membership changed"
        );
        validate_targets(&file.suggested_inclusion.supported_targets)?;
        validate_variants(path, &file.source_witness_variants)?;
        register_template(
            templates,
            path,
            &file.authored_template.sha256,
            file.authored_template.bytes,
        )?;
    }
    Ok(())
}

fn validate_foundation_ledger(
    core: &CoreTestFixture,
    templates: &mut BTreeMap<String, (String, usize)>,
) -> Result<()> {
    let ledger: FoundationLedger = facet_json::from_str(&ledger_source(core, FOUNDATION_LEDGER)?)?;
    let commits = pinned_commits();
    ensure!(
        ledger.schema == "sfm:core_action_foundation_slice@1"
            && ledger.source_commits == commits
            && ledger.files.len() == 2,
        "wrong action foundation witness contract"
    );
    let mut paths = BTreeSet::new();
    for file in ledger.files {
        ensure!(
            matches!(file.path.as_str(), COMPILER | TREE)
                && paths.insert(file.path.clone())
                && file.core_path == format!("{CORE_PREFIX}{}", file.path),
            "wrong foundation path"
        );
        ensure!(
            file.witnesses.keys().eq(commits.keys()),
            "incomplete foundation witness set"
        );
        for (name, row) in file.witnesses {
            let (environment, target) = name.split_once('/').expect("fixed contexts");
            ensure!(
                row.source_commit == commits[&name],
                "foundation commit pin changed"
            );
            let enabled = row
                .validated_registered_feature_closure
                .iter()
                .map(String::as_str)
                .collect::<Vec<_>>();
            let expected = if environment == "release" {
                &[][..]
            } else if is_d2(target) {
                &D2_FOUNDATION_FEATURES[..]
            } else {
                &OTHER_FOUNDATION_FEATURES[..]
            };
            ensure!(
                enabled.len() == expected.len()
                    && enabled.iter().copied().collect::<BTreeSet<_>>()
                        == expected.iter().copied().collect(),
                "foundation exact historical feature witness changed"
            );
            core.context(target, &enabled)?;
            if environment == "release" {
                ensure!(
                    !row.present
                        && enabled.is_empty()
                        && row.git_blob.is_none()
                        && row.source_sha256.is_none()
                        && row.byte_count.is_none()
                        && row.mode.is_none(),
                    "foundation released absence changed"
                );
            } else {
                let oid = expected_oid(&file.path, target)?;
                let golden = raw_golden(oid)?;
                ensure!(
                    row.present
                        && row.git_blob.as_deref() == Some(oid)
                        && row.source_sha256.as_deref() == Some(golden.sha256)
                        && row.byte_count == Some(golden.bytes)
                        && row.mode.as_deref() == Some("100644"),
                    "foundation raw witness changed: {name}"
                );
            }
        }
        register_template(
            templates,
            &file.path,
            &file.template_sha256,
            file.template_byte_count,
        )?;
    }
    Ok(())
}

fn validate_icon_ledger(
    core: &CoreTestFixture,
    templates: &mut BTreeMap<String, (String, usize)>,
) -> Result<()> {
    let ledger: IconLedger = facet_json::from_str(&ledger_source(core, ICON_LEDGER)?)?;
    ensure!(
        ledger.schema == "sfm:core-item-icon-carrier-slice@1"
            && ledger.core_relative_file == ICON
            && ledger.core_path == format!("{CORE_PREFIX}{ICON}")
            && ledger.context_commits == pinned_commits()
            && ledger.raw_witnesses.len() == 4
            && ledger
                .source_membership_recommendation
                .released_membership
                .is_empty(),
        "wrong icon carrier witness contract"
    );
    validate_targets(&ledger.source_membership_recommendation.supported_targets)?;
    validate_variants(ICON, &ledger.raw_witnesses)?;
    register_template(
        templates,
        ICON,
        &ledger.authored_template.source_sha256,
        ledger.authored_template.source_bytes,
    )?;
    Ok(())
}

#[test]
fn nine_action_carrier_inputs_reconstruct_all_twenty_exact_contexts() -> Result<()> {
    let fixture = ActionFixture::load()?;
    let mut present = 0;
    let mut absent = 0;
    for (name, _) in CONTEXT_COMMITS {
        let (environment, target) = name.split_once('/').expect("fixed contexts");
        let enabled = if environment == "release" {
            &[][..]
        } else if is_d2(target) {
            &D2_WITNESS_FEATURES[..]
        } else {
            &OTHER_WITNESS_FEATURES[..]
        };
        let context = fixture.core.context(target, enabled)?;
        for path in PATHS {
            let rendered = fixture.render(path, &context)?;
            if environment == "release" {
                assert!(
                    rendered.is_none(),
                    "released {name} / {path} must be omitted before read/render"
                );
                absent += 1;
            } else {
                let oid = expected_oid(path, target)?;
                assert_eq!(
                    rendered.as_deref(),
                    Some(fixture.raw[oid].as_slice()),
                    "{name} / {path}"
                );
                present += 1;
            }
        }
    }
    assert_eq!((present, absent), (90, 90));
    Ok(())
}

#[test]
fn action_disabled_preserves_historical_omission_and_current_pure_workspace_carriers() -> Result<()>
{
    let fixture = ActionFixture::load()?;
    let mut historical = fixture.core.metadata.clone();
    for path in [SOURCE, CONTEXT, AVAILABILITY] {
        let current = fixture
            .core
            .metadata
            .source_rules
            .get(path)
            .ok_or_else(|| eyre::eyre!("current carrier rule absent: {path}"))?;
        ensure!(
            current.len() == 1
                && current[0].input == path
                && current[0].template
                && current[0].when.any_features == ["client_actions", "workspace_panels"]
                && current[0].when.all_features.is_empty()
                && current[0].when.targets.is_empty()
                && current[0].when.none_features.is_empty(),
            "unauthorized carrier refinement"
        );
        let mut old = current[0].clone();
        old.when.any_features.clear();
        old.when.all_features = vec!["client_actions".to_owned()];
        historical.source_rules.insert(path.to_owned(), vec![old]);
    }
    let mut historical_absent = 0;
    let mut current_absent = 0;
    let mut current_pure = 0;
    for target in TARGETS {
        for enabled in [&[][..], &["workspace_panels"][..]] {
            let context = fixture.core.context(target, enabled)?;
            let old = select_core_inputs(&historical, &context, &fixture.inventory)?;
            for path in PATHS {
                assert!(
                    old.omitted_paths.contains(path),
                    "historical {target} {path}"
                );
                historical_absent += 1;
                let pure = !enabled.is_empty() && [SOURCE, CONTEXT, AVAILABILITY].contains(&path);
                let body = fixture.render(path, &context)?;
                assert_eq!(body.is_some(), pure, "current {target} {path}");
                if pure {
                    current_pure += 1;
                } else {
                    current_absent += 1;
                }
            }
        }
    }
    assert_eq!(
        (historical_absent, current_absent, current_pure),
        (180, 150, 30)
    );
    Ok(())
}

#[test]
fn second_action_members_toggle_without_implicit_machine_or_ui_extensions() -> Result<()> {
    let fixture = ActionFixture::load()?;
    for target in ["1.19.2", "1.19.4"] {
        for mask in 0_u8..16 {
            let mut enabled = vec!["client_actions"];
            if mask & 1 != 0 {
                enabled.extend(PROGRAM_PREREQUISITES);
            }
            if mask & 2 != 0 {
                enabled.push("workspace_panels");
            }
            if mask & 4 != 0 {
                enabled.push("structured_action_results");
            }
            if mask & 8 != 0 {
                enabled.push("keyboard_profiles");
            }
            let context = fixture.core.context(target, &enabled)?;
            let action = fixture.text(ACTION, &context)?;
            let host = fixture.text(CONTEXT, &context)?;
            let source = fixture.text(SOURCE, &context)?;
            let executor = fixture.text(EXECUTOR, &context)?;
            for marker in [
                "programmaticDescriptor()",
                "programmaticHandler()",
                "SFMClientActionDescriptor",
                "SFMClientActionProgrammaticHandler",
            ] {
                assert_eq!(
                    action.contains(marker),
                    mask & 1 != 0,
                    "{target} / {mask} / {marker}"
                );
            }
            for marker in [
                "originatingPanelId",
                "SFMScreenMultiplexer",
                "SFMWorkspacePanelId",
            ] {
                assert_eq!(
                    host.contains(marker),
                    mask & 2 != 0,
                    "{target} / {mask} / {marker}"
                );
            }
            for marker in ["SFMClientActionStructuredResult", "structuredResult"] {
                assert_eq!(
                    source.contains(marker),
                    mask & 4 != 0,
                    "{target} / {mask} / {marker}"
                );
                assert_eq!(
                    executor.contains(marker),
                    mask & 4 != 0,
                    "{target} / {mask} / {marker}"
                );
            }
            assert_eq!(source.contains("publishStructuredResult("), mask & 4 != 0);
            assert_eq!(
                executor.contains("SFMClientActionInvocationTrace"),
                mask & 8 != 0
            );
            assert_eq!(executor.contains("commandTree,"), mask & 12 != 0);
            assert!(
                action.contains("return Optional.empty();"),
                "machine opt-in defaults must stay denied"
            );
            assert!(host.contains("originatingHostIsCurrent.getAsBoolean()"));
            if mask == 0 {
                for (path, oid) in [
                    (ACTION, "3fa0d67a2b5501729f2baabb7179900448113163"),
                    (CONTEXT, "20a8bb876939aae2e66e638ab77e383579e1b9b5"),
                    (SOURCE, "2bac8cc4c5d5ba3885460d9dc803da754e921140"),
                    (EXECUTOR, "13d449260d1777ceaff5c8876254d14a6fdcfea9"),
                ] {
                    assert_eq!(
                        fixture.render(path, &context)?.as_deref(),
                        Some(fixture.raw[oid].as_slice())
                    );
                }
            }
        }
    }
    Ok(())
}

// Bit meanings: legacy palette, typed palette, history, context choices,
// diagnostics, nullable suggestion-ID guard. Invalid combinations are excluded
// by the reviewed functional contract, never because validation returned an error.
fn foundation_features(mask: u8) -> Option<Vec<&'static str>> {
    if mask & 2 != 0 && mask & 1 == 0 {
        return None;
    }
    if mask & 12 != 0 && mask & 2 == 0 {
        return None;
    }
    let mut enabled = vec!["client_actions"];
    if mask & 1 != 0 {
        enabled.extend(["command_palette", "client_theme", "keyboard_profiles"]);
    }
    if mask & 2 != 0 {
        enabled.push("typed_command_palette");
    }
    if mask & 4 != 0 {
        enabled.push("command_history");
    }
    if mask & 8 != 0 {
        enabled.extend(["context_actions", "workspace_panels"]);
    }
    if mask & 16 != 0 {
        enabled.push("client_action_completion_diagnostics");
    }
    if mask & 32 != 0 {
        enabled.push("client_action_nullable_suggestions");
    }
    Some(enabled)
}

#[test]
fn foundation_independent_combinations_use_real_selection_and_renderer() -> Result<()> {
    let fixture = ActionFixture::load()?;
    let mut count = 0;
    for target in TARGETS {
        for mask in 0_u8..64 {
            if !is_d2(target) && mask & !1 != 0 {
                continue;
            }
            let Some(enabled) = foundation_features(mask) else {
                continue;
            };
            let context = fixture.core.context(target, &enabled)?;
            let tree = fixture.text(TREE, &context)?;
            let compiler = fixture.text(COMPILER, &context)?;
            for marker in [
                "Duplicate client action id:",
                "literal(\"list\")",
                "literal(\"help\")",
                "literal(\"invoke\")",
            ] {
                assert!(
                    compiler.contains(marker),
                    "{target} / {mask} lost base dispatch {marker}"
                );
            }
            for marker in [
                "dispatcher.parse(command, source)",
                "dispatcher.execute(command, source)",
                "requirement().resolve(source.context())",
            ] {
                assert!(
                    tree.contains(marker),
                    "{target} / {mask} lost base execution {marker}"
                );
            }
            assert_foundation_members(&tree, &compiler, mask)?;
            assert_constructor_arity_closure(&tree, &compiler)?;
            count += 1;
        }
    }
    assert_eq!(count, 64);
    Ok(())
}

fn assert_foundation_members(tree: &str, compiler: &str, mask: u8) -> Result<()> {
    let combined = format!("{tree}\n{compiler}");
    let owned = [
        (1, &["getPaletteSuggestions", "PALETTE_ACTION_PREFIX"][..]),
        (
            2,
            &[
                "SFMCommandFrontierAnalysis",
                "SFMPaletteCandidate",
                "SFMClientActionCompletion",
                "ActionSearchMetadata",
                "SFMFuzzyScorer",
            ][..],
        ),
        (
            4,
            &[
                "SFMCommandHistoryService",
                "historySuggestions",
                "SFMClientActionArgumentHistory",
                "availableHistory",
                "HISTORY_ACTION_BOOST",
                "actionHistoryRecency",
                "historyBoost",
                "Origin.COMMAND_HISTORY",
            ][..],
        ),
        (
            // Both consumers require this API; enabling typed palette alone
            // must not force the independent contextual-action feature on.
            2 | 8,
            &[
                "isolatedPaletteSurface",
                "paletteChoiceActions",
                "paletteChoiceDisplayTexts",
                "paletteChoiceRange",
                "RankedChoice",
                "choiceScore",
            ][..],
        ),
        (
            16,
            &[
                "SLOW_COMPLETION_NANOS",
                "SFM.LOGGER.warn",
                "completionStarted",
                "System.nanoTime()",
            ][..],
        ),
        (
            32,
            &["return id == null || !actions.containsKey(id) || isAvailable(id, source);"][..],
        ),
    ];
    for (bit, markers) in owned {
        for marker in markers {
            ensure!(
                combined.contains(marker) == (mask & bit != 0),
                "foundation owner leaked/disappeared: mask={mask}, marker={marker}"
            );
        }
    }
    if mask & 1 == 0 {
        for marker in ["StringDistance", "StringRange", "RankedAction"] {
            ensure!(
                !tree.contains(marker),
                "disabled legacy palette dependency remains"
            );
        }
    }
    ensure!(
        tree.contains("StringDistances.damerauLevenshtein()") == (mask & 1 != 0 && mask & 2 == 0),
        "legacy palette scorer was bundled into typed/base action output"
    );
    Ok(())
}

// This bounded structural check catches newly reconstructed constructor seams.
// It is explicitly not a Java type checker or compilation substitute.
fn assert_constructor_arity_closure(tree: &str, compiler: &str) -> Result<()> {
    let tree = java_mask(tree)?;
    let compiler = java_mask(compiler)?;
    let mut declared = BTreeSet::new();
    for (index, _) in tree.match_indices("SFMClientActionCommandTree(") {
        let line = tree[..index].rfind('\n').map_or(0, |i| i + 1);
        if matches!(tree[line..index].trim(), "" | "private") {
            ensure!(
                declared.insert(parameter_count(
                    &tree,
                    index + "SFMClientActionCommandTree".len(),
                    true
                )?),
                "duplicate constructor arity in reconstructed command tree"
            );
        }
    }
    ensure!(!declared.is_empty(), "command tree has no constructor");
    for source in [&tree, &compiler] {
        for marker in ["this(", "new SFMClientActionCommandTree("] {
            for (index, _) in source.match_indices(marker) {
                let count = parameter_count(source, index + marker.len() - 1, false)?;
                ensure!(
                    declared.contains(&count),
                    "new command-tree constructor call has no matching arity: {count}"
                );
            }
        }
    }
    Ok(())
}

fn parameter_count(source: &str, opening: usize, declaration: bool) -> Result<usize> {
    let mut depth = Vec::new();
    let mut commas = 0;
    let mut content = false;
    for &byte in &source.as_bytes()[opening + 1..] {
        if byte == b')' && depth.is_empty() {
            return Ok(if content { commas + 1 } else { 0 });
        }
        if matches!(byte, b'(' | b'[' | b'{') || (declaration && byte == b'<') {
            depth.push(byte);
        } else if matches!(byte, b')' | b']' | b'}') || (declaration && byte == b'>') {
            ensure!(depth.pop().is_some(), "unbalanced constructor parameter");
        } else if byte == b',' && depth.is_empty() {
            commas += 1;
        }
        content |= !byte.is_ascii_whitespace();
    }
    eyre::bail!("unterminated command-tree constructor")
}

fn java_mask(source: &str) -> Result<String> {
    let bytes = source.as_bytes();
    let mut masked = bytes.to_vec();
    let mut index = 0;
    while index < bytes.len() {
        let kind = if bytes[index..].starts_with(b"//") {
            1
        } else if bytes[index..].starts_with(b"/*") {
            2
        } else if bytes[index] == b'"' {
            3
        } else if bytes[index] == b'\'' {
            4
        } else {
            0
        };
        if kind == 0 {
            index += 1;
            continue;
        }
        let start = index;
        index += if kind <= 2 { 2 } else { 1 };
        let mut finished = false;
        while index < bytes.len() {
            if kind == 1 && bytes[index] == b'\n' {
                finished = true;
                break;
            }
            if kind == 2 && bytes[index..].starts_with(b"*/") {
                index += 2;
                finished = true;
                break;
            }
            if kind >= 3 && bytes[index] == b'\\' {
                index += 2;
                continue;
            }
            if kind == 3 && bytes[index] == b'"' || kind == 4 && bytes[index] == b'\'' {
                index += 1;
                finished = true;
                break;
            }
            index += 1;
        }
        ensure!(
            finished || kind == 1,
            "unterminated Java literal/comment in structural seam check"
        );
        ensure!(index <= bytes.len(), "truncated Java escape");
        for byte in &mut masked[start..index] {
            if *byte != b'\n' {
                *byte = b' ';
            }
        }
    }
    let source = String::from_utf8(masked)?;
    let mut stack = Vec::new();
    for byte in source.bytes() {
        match byte {
            b'(' | b'[' | b'{' => stack.push(byte),
            b')' | b']' | b'}' => {
                let expected = match byte {
                    b')' => b'(',
                    b']' => b'[',
                    _ => b'{',
                };
                ensure!(
                    stack.pop() == Some(expected),
                    "unbalanced Java delimiter in reconstructed seam"
                );
            }
            _ => {}
        }
    }
    ensure!(
        stack.is_empty(),
        "unclosed Java delimiter in reconstructed seam"
    );
    Ok(source)
}

#[test]
fn broad_workspace_and_keyboard_flags_preserve_eight_older_member_apis() -> Result<()> {
    let fixture = ActionFixture::load()?;
    let mut outputs = 0;
    for target in TARGETS.into_iter().filter(|target| !is_d2(target)) {
        let context = fixture.core.context(target, &OTHER_WITNESS_FEATURES)?;
        for path in PATHS {
            assert_eq!(
                fixture.render(path, &context)?.as_deref(),
                Some(fixture.raw[expected_oid(path, target)?].as_slice()),
                "broad supported flags must preserve target-specific raw API: {target}/{path}"
            );
            outputs += 1;
        }
        let host = fixture.text(CONTEXT, &context)?;
        let executor = fixture.text(EXECUTOR, &context)?;
        assert!(!host.contains("originatingPanelId"));
        assert!(!host.contains("SFMScreenMultiplexer"));
        assert!(!executor.contains("SFMClientActionInvocationTrace"));
        assert!(
            !fixture
                .text(SOURCE, &context)?
                .contains("publishStructuredResult")
        );
        assert!(
            !fixture
                .text(ACTION, &context)?
                .contains("programmaticDescriptor")
        );
        let expected_api = if target == "26.1.2" {
            "net.minecraft.resources.Identifier"
        } else {
            "net.minecraft.resources.ResourceLocation"
        };
        assert!(fixture.text(ACTION, &context)?.contains(expected_api));
    }
    assert_eq!(outputs, 72);
    Ok(())
}

#[test]
fn action_slice_contexts_fail_closed_on_missing_prerequisites_or_unsupported_extensions()
-> Result<()> {
    let fixture = ActionFixture::load()?;
    for enabled in [
        &["typed_command_palette", "client_actions"][..],
        &["command_palette", "client_actions"][..],
        &["client_program_actions", "client_actions"][..],
        &["keyboard_profiles"][..],
    ] {
        assert!(
            fixture.core.context("1.19.2", enabled).is_err(),
            "accepted incomplete prerequisites {enabled:?}"
        );
    }
    let mut no_workspace = foundation_features(1 | 2 | 8).expect("reviewed foundation mask");
    no_workspace.retain(|feature| *feature != "workspace_panels");
    assert!(fixture.core.context("1.19.2", &no_workspace).is_err());
    for target in TARGETS.into_iter().filter(|target| !is_d2(target)) {
        for feature in [
            "structured_action_results",
            "typed_command_palette",
            "client_action_completion_diagnostics",
            "client_action_nullable_suggestions",
        ] {
            assert!(
                fixture
                    .core
                    .context(target, &["client_actions", feature])
                    .is_err(),
                "unsupported extension accepted on {target}: {feature}"
            );
        }
    }
    // A fixture never changes registry support or catches errors to silently
    // remove an unavailable extension.
    assert!(fixture.core.features.0.contains_key("workspace_panels"));
    assert!(
        fixture
            .core
            .features
            .0
            .contains_key("client_program_actions")
    );
    Ok(())
}
