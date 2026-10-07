//! Frozen pure workspace geometry/identity/divider source proofs.
//!
//! Run after root promotion and exact sparse-rule registration. Frozen Git
//! blobs are bounded test goldens only, never production inputs. Reference
//! audits are not javac or UI/service runtime proofs. Future core edits require
//! deliberate review of their golden evidence.
#![cfg(test)]

use super::context::ProjectionContext;
use super::core_inputs::CORE_ROOT;
use super::core_inputs::collect_core_artifacts;
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

const LEDGER: &str = "docs/tasks/sfm-core-workspace-primitives-slice.json";
const LEDGER_SHA: &str = "sha256:a175a7ea17136bbb1c10490c3a51967aeb8fc5b0d56ada127afa8c4b6bbdbdf5";
const LIMIT: u64 = 256 * 1024;
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
// Frozen raw identity, SHA-256 and exact byte count, all LF/final LF.
const RAW: [(&str, &str, u64); 12] = [
    (
        "36124a6df1fa8133eadd8b818dbfb93337f60e07",
        "sha256:cf9503cda9d837a9743656999d42ef98ca2c102df563dfc07b820421c5347254",
        749,
    ),
    (
        "d2dce70738ca32e4cff122f908307ec55e34ba3c",
        "sha256:8bbd7ddf20e4b69b7cac753423a7052f27005fa7187a66747be5ad6c869b3c07",
        3686,
    ),
    (
        "2554d52aed1da5fa5530c664bfd24a6251b2fc7d",
        "sha256:639e97e7c03ecf264a5de1cb3b3606e7fe8c0236744c1dc50d5d6abddf1a0f51",
        112,
    ),
    (
        "e2fbf51e42f018ba6783da09ddaa18d68fa6144e",
        "sha256:1d9e88f2c8eeacc8d10572081558579e0ae1cc959785d8c8152a6667ac3943b0",
        590,
    ),
    (
        "78f83830e699192357e86096640d3a9be59773aa",
        "sha256:5910f47ded96c51a38ab0e607d71df0e4d5f8b72ad8c4cad7899507e6d7843f7",
        471,
    ),
    (
        "0c0457717f9ecea409e6e808719fc9acdb3f4644",
        "sha256:e1bfccdf915f338abe621a6a3dca63fb0b870fb4db1ad235083a4ffe5537dec4",
        2396,
    ),
    (
        "edab47cca87462f7abc4ee2b947e06f36fb57eb8",
        "sha256:20011cc1f8da6c20f1d21d6826814a2c8245cfa3e460b788c8d6575566150eda",
        535,
    ),
    (
        "b652ab4dd74bf7802cd172f9d9d4cd2911df9d6d",
        "sha256:d5353404859d147acf47a5987ac1f7dddc19002eb5b648890e8a5c7c05313c1f",
        835,
    ),
    (
        "f757d48e0dc5857e532ca400f6d81e51c0f48430",
        "sha256:838908e481b533e1c91750c3761e3b89d3c3f318c80a5492389974d94f8d23e1",
        2065,
    ),
    (
        "6f2315f53bc36d45b5513c93dfd8a9491aab152b",
        "sha256:f93cf4f08be2ba64eaf73ed87f06a67ecb329c6b6fde8d36571b7a59f2ad6e30",
        1532,
    ),
    (
        "4c42861f45d31660b5a4192ff92dd956e870bbc2",
        "sha256:a786f7e11443ed0a0307a89e551706942bbda55c0a37fa5b8dbb1c731199c984",
        251,
    ),
    (
        "4be9792ca9ae9445528994eed4031d005a1a53d1",
        "sha256:ca429e43213a95f1ee36a387135c1d229dcf3cbdb190e49f1937395493e17b53",
        837,
    ),
];
// Class, staged shared-template hash/count, D2 raw, optional later8 raw, all10 membership.
const FILES: [(&str, &str, u64, &str, &str, bool); 12] = [
    (
        "SFMScreenPanelBounds",
        "sha256:cf9503cda9d837a9743656999d42ef98ca2c102df563dfc07b820421c5347254",
        749,
        "36124a6df1fa8133eadd8b818dbfb93337f60e07",
        "",
        true,
    ),
    (
        "SFMWorkspacePanelMetrics",
        "sha256:8bbd7ddf20e4b69b7cac753423a7052f27005fa7187a66747be5ad6c869b3c07",
        3686,
        "d2dce70738ca32e4cff122f908307ec55e34ba3c",
        "",
        false,
    ),
    (
        "SFMWorkspaceAxis",
        "sha256:639e97e7c03ecf264a5de1cb3b3606e7fe8c0236744c1dc50d5d6abddf1a0f51",
        112,
        "2554d52aed1da5fa5530c664bfd24a6251b2fc7d",
        "",
        true,
    ),
    (
        "SFMWorkspaceSide",
        "sha256:1d9e88f2c8eeacc8d10572081558579e0ae1cc959785d8c8152a6667ac3943b0",
        590,
        "e2fbf51e42f018ba6783da09ddaa18d68fa6144e",
        "",
        true,
    ),
    (
        "SFMWorkspaceStackId",
        "sha256:5910f47ded96c51a38ab0e607d71df0e4d5f8b72ad8c4cad7899507e6d7843f7",
        471,
        "78f83830e699192357e86096640d3a9be59773aa",
        "",
        false,
    ),
    (
        "SFMWorkspaceDividerId",
        "sha256:e1bfccdf915f338abe621a6a3dca63fb0b870fb4db1ad235083a4ffe5537dec4",
        2396,
        "0c0457717f9ecea409e6e808719fc9acdb3f4644",
        "",
        false,
    ),
    (
        "SFMWorkspaceDividerLinkId",
        "sha256:20011cc1f8da6c20f1d21d6826814a2c8245cfa3e460b788c8d6575566150eda",
        535,
        "edab47cca87462f7abc4ee2b947e06f36fb57eb8",
        "",
        false,
    ),
    (
        "SFMWorkspaceDividerHit",
        "sha256:d5353404859d147acf47a5987ac1f7dddc19002eb5b648890e8a5c7c05313c1f",
        835,
        "b652ab4dd74bf7802cd172f9d9d4cd2911df9d6d",
        "",
        false,
    ),
    (
        "SFMWorkspaceDividerView",
        "sha256:838908e481b533e1c91750c3761e3b89d3c3f318c80a5492389974d94f8d23e1",
        2065,
        "f757d48e0dc5857e532ca400f6d81e51c0f48430",
        "",
        false,
    ),
    (
        "SFMWorkspaceDivider",
        "sha256:f93cf4f08be2ba64eaf73ed87f06a67ecb329c6b6fde8d36571b7a59f2ad6e30",
        1532,
        "6f2315f53bc36d45b5513c93dfd8a9491aab152b",
        "",
        false,
    ),
    (
        "SFMWorkspaceDividerCursor",
        "sha256:a786f7e11443ed0a0307a89e551706942bbda55c0a37fa5b8dbb1c731199c984",
        251,
        "4c42861f45d31660b5a4192ff92dd956e870bbc2",
        "",
        false,
    ),
    (
        "SFMWorkspaceDividerResizeResult",
        "sha256:ca429e43213a95f1ee36a387135c1d229dcf3cbdb190e49f1937395493e17b53",
        837,
        "4be9792ca9ae9445528994eed4031d005a1a53d1",
        "",
        false,
    ),
];

#[derive(Facet)]
struct Ledger {
    schema: String,
    scope: Scope,
    context_commits: BTreeMap<String, String>,
    normalization: Normalization,
    prerequisite_definitions: BTreeMap<String, Definition>,
    files: Vec<File>,
    raw_variants: Vec<RawVariant>,
    full_source_profiles: Vec<Profile>,
}
#[derive(Facet)]
struct Scope {
    production_files: usize,
    context_cells: usize,
    present_cells: usize,
    absent_cells: usize,
    raw_variants: usize,
    stage_bytes: u64,
    new_flags: usize,
    new_dependencies: usize,
    java_compilation: bool,
    live_runtime: bool,
}
#[derive(Facet)]
struct Normalization {
    policy: String,
    raw_byte_exact: bool,
    authorized_transformations: Vec<String>,
}
#[derive(Facet)]
struct Definition {
    supported_targets: Vec<String>,
    requires: Vec<String>,
}
#[derive(Facet)]
struct File {
    name: String,
    intended_core_path: String,
    stage_bytes: u64,
    stage_sha256: String,
    class_support: Vec<String>,
    source_rule: SourceRule,
    witnesses: Vec<Witness>,
}
#[derive(Facet)]
struct SourceRule {
    input: String,
    template: bool,
    when: SourceWhen,
}
#[derive(Facet)]
struct SourceWhen {
    targets: Vec<String>,
    all_features: Vec<String>,
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
#[derive(Facet)]
struct RawVariant {
    name: String,
    git_blob: String,
    sha256: String,
    bytes: u64,
    crlf_count: usize,
    lone_cr_count: usize,
    final_lf: bool,
    bom: bool,
}
#[derive(Facet)]
struct Profile {
    target: String,
    enabled: Vec<String>,
    selected_paths: Vec<String>,
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
        ensure!(
            sha256(&bytes) == LEDGER_SHA,
            "reviewed workspace-primitive ledger changed"
        );
        let ledger: Ledger = facet_json::from_str(std::str::from_utf8(&bytes)?)
            .wrap_err("cannot parse workspace primitive evidence")?;
        let sources = FILES
            .iter()
            .map(|f| shared.read_source(&path(f.0)))
            .collect::<Result<Vec<_>>>()?;
        let raw = read_git_blobs(
            &shared.repository,
            &RAW.iter().map(|r| r.0.to_owned()).collect(),
        )?;
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
        let s = &self.ledger.scope;
        ensure!(
            self.ledger.schema == "sfm:core-workspace-primitives-slice@1"
                && s.production_files == 12
                && s.context_cells == 240
                && s.present_cells == 48
                && s.absent_cells == 192
                && s.raw_variants == 12
                && s.stage_bytes == 14059
                && s.new_flags == 1
                && s.new_dependencies == 0
                && !s.java_compilation
                && !s.live_runtime,
            "workspace primitive source-only scope changed"
        );
        ensure!(
            self.ledger.normalization.policy == "none"
                && self.ledger.normalization.raw_byte_exact
                && self
                    .ledger
                    .normalization
                    .authorized_transformations
                    .is_empty(),
            "no normalization authorized"
        );
        let contexts = CONTEXTS
            .into_iter()
            .map(|(n, id)| (n.to_owned(), id.to_owned()))
            .collect::<BTreeMap<_, _>>();
        ensure!(
            self.ledger.context_commits == contexts
                && self.ledger.files.len() == 12
                && self.ledger.raw_variants.len() == 12
                && self.ledger.full_source_profiles.len() == 10,
            "primitive frozen witness coverage changed"
        );
        for (name, definition) in &self.ledger.prerequisite_definitions {
            let actual = self
                .shared
                .features
                .0
                .get(name)
                .ok_or_else(|| eyre::eyre!("primitive feature not registered: {name}"))?;
            ensure!(
                actual.supported_targets == definition.supported_targets
                    && actual.requires == definition.requires,
                "primitive dependency/support changed: {name}"
            );
        }
        for (oid, digest, count) in RAW {
            validate_bytes(&self.raw[oid], digest, count)?;
            let row = self
                .ledger
                .raw_variants
                .iter()
                .find(|r| r.git_blob == oid)
                .ok_or_else(|| eyre::eyre!("raw primitive fact absent"))?;
            ensure!(
                row.sha256 == digest
                    && row.bytes == count
                    && row.crlf_count == 0
                    && row.lone_cr_count == 0
                    && row.final_lf
                    && !row.bom,
                "raw primitive facts changed"
            );
            ensure!(
                FILES
                    .iter()
                    .any(|f| f.0 == row.name && (f.3 == oid || f.4 == oid)),
                "raw owner changed"
            );
        }
        for (index, fact) in FILES.iter().enumerate() {
            let row = &self.ledger.files[index];
            let p = path(fact.0);
            let targets = if fact.5 {
                TARGETS.to_vec()
            } else {
                TARGETS[..2].to_vec()
            };
            let (all, any) = predicates(index);
            ensure!(
                row.name == fact.0
                    && row.intended_core_path == p
                    && row.stage_sha256 == fact.1
                    && row.stage_bytes == fact.2
                    && row.class_support == targets
                    && row.witnesses.len() == 20
                    && row.source_rule.input == p
                    && row.source_rule.template
                    && row.source_rule.when.targets == targets
                    && row.source_rule.when.all_features == all
                    && row.source_rule.when.any_features == any,
                "canonical primitive identity/predicate changed: {}",
                fact.0
            );
            validate_bytes(&self.sources[index], fact.1, fact.2)?;
            let rules = self
                .shared
                .metadata
                .source_rules
                .get(&p)
                .ok_or_else(|| eyre::eyre!("explicit primitive source rule absent"))?;
            ensure!(rules.len() == 1, "primitive requires one canonical input");
            let rule = &rules[0];
            ensure!(
                rule.input == p
                    && rule.when.none_features.is_empty()
                    && set(&rule.when.targets) == targets.iter().copied().collect()
                    && set(&rule.when.all_features) == all.into_iter().collect()
                    && set(&rule.when.any_features) == any.into_iter().collect(),
                "actual primitive predicate/support changed"
            );
            let mut seen = BTreeSet::new();
            for witness in &row.witnesses {
                let (kind, target) = witness
                    .context
                    .split_once('/')
                    .ok_or_else(|| eyre::eyre!("bad witness"))?;
                let present = kind == "dev" && (fact.5 || is_d2(target));
                let oid = if present {
                    Some(witness_oid(index, target))
                } else {
                    None
                };
                let raw_fact = oid.and_then(|id| RAW.iter().find(|r| r.0 == id));
                ensure!(
                    seen.insert(witness.context.as_str())
                        && contexts.get(&witness.context) == Some(&witness.commit)
                        && witness.present == present
                        && witness.git_blob.as_deref() == oid
                        && witness.raw_bytes == raw_fact.map_or(0, |r| r.2)
                        && witness.raw_sha256.as_deref() == raw_fact.map(|r| r.1),
                    "frozen primitive membership changed"
                );
            }
            ensure!(
                seen == contexts.keys().map(String::as_str).collect(),
                "witness contexts incomplete"
            );
            let source = std::str::from_utf8(&self.sources[index])?;
            ensure!(
                !source.contains("{%"),
                "pure primitive acquired an unreviewed body branch"
            );
            ensure!(
                self.sources[index] == self.raw[fact.3],
                "primitive body is not its exact raw witness"
            );
            for line in source.lines().filter(|l| l.starts_with("import ")) {
                ensure!(
                    line.starts_with("import java.")
                        || (index == 9 && line == "import org.jetbrains.annotations.Nullable;"),
                    "unreviewed primitive host dependency"
                );
            }
        }
        Ok(())
    }
    fn context(&self, target: &str, requested: &[&str]) -> Result<ProjectionContext> {
        // Test-only explicit dependency closure from the immutable reviewed
        // contract. Production context helper still refuses incomplete sets.
        let mut flags = requested
            .iter()
            .map(|f| (*f).to_owned())
            .collect::<BTreeSet<_>>();
        loop {
            let mut added = false;
            for name in flags.clone() {
                let definition = self
                    .ledger
                    .prerequisite_definitions
                    .get(&name)
                    .ok_or_else(|| eyre::eyre!("unreviewed primitive owner: {name}"))?;
                ensure!(
                    definition.supported_targets.iter().any(|t| t == target),
                    "owner unsupported on target"
                );
                for dependency in &definition.requires {
                    added |= flags.insert(dependency.clone());
                }
            }
            if !added {
                break;
            }
        }
        self.shared.context(
            target,
            &flags.iter().map(String::as_str).collect::<Vec<_>>(),
        )
    }
    fn selected(&self, context: &ProjectionContext) -> Result<BTreeSet<usize>> {
        let selection = select_core_inputs(&self.shared.metadata, context, &inventory())?;
        let mut result = BTreeSet::new();
        for (index, file) in self.ledger.files.iter().enumerate() {
            let (all, any) = predicates(index);
            let expected = file.class_support.contains(&selection.target_id)
                && all.iter().all(|f| enabled(context, f))
                && (any.is_empty() || any.iter().any(|f| enabled(context, f)));
            if let Some(input) = selection.inputs.get(&file.intended_core_path) {
                ensure!(
                    expected
                        && input.input == file.intended_core_path
                        && !selection.omitted_paths.contains(&file.intended_core_path),
                    "primitive provenance escaped predicate"
                );
                result.insert(index);
            } else {
                ensure!(
                    !expected && selection.omitted_paths.contains(&file.intended_core_path),
                    "inactive primitive not explicitly omitted"
                );
            }
        }
        Ok(result)
    }
    fn render(&self, index: usize, context: &ProjectionContext) -> Result<String> {
        ensure!(
            self.selected(context)?.contains(&index),
            "refuse omitted primitive rendering"
        );
        render_java_source(std::str::from_utf8(&self.sources[index])?, context)
    }
    fn full_context(&self, target: &str) -> Result<ProjectionContext> {
        let profile = self
            .ledger
            .full_source_profiles
            .iter()
            .find(|p| p.target == target)
            .ok_or_else(|| eyre::eyre!("full primitive profile absent"))?;
        let context = self.shared.context(
            target,
            &profile
                .enabled
                .iter()
                .map(String::as_str)
                .collect::<Vec<_>>(),
        )?;
        let paths = self
            .selected(&context)?
            .iter()
            .map(|i| path(FILES[*i].0))
            .collect::<Vec<_>>();
        ensure!(
            paths == profile.selected_paths,
            "full primitive profile selection changed"
        );
        Ok(context)
    }
}

fn path(name: &str) -> String {
    format!("src/main/java/ca/teamdman/sfm/client/screen/workspace/{name}.java")
}
fn inventory() -> BTreeSet<String> {
    FILES.iter().map(|f| path(f.0)).collect()
}
fn is_d2(target: &str) -> bool {
    matches!(target, "1.19.2" | "1.19.4")
}
fn enabled(context: &ProjectionContext, feature: &str) -> bool {
    context.features.get(feature).copied().unwrap_or(false)
}
fn set(values: &[String]) -> BTreeSet<&str> {
    values.iter().map(String::as_str).collect()
}
fn witness_oid(index: usize, target: &str) -> &'static str {
    let fact = FILES[index];
    if is_d2(target) || fact.4.is_empty() {
        fact.3
    } else {
        fact.4
    }
}
fn predicates(index: usize) -> (Vec<&'static str>, Vec<&'static str>) {
    let any = match index {
        0 => vec![
            "workspace_panels",
            "terminal_remote",
            "terminal_vox_runtime",
            "terminal_clipboard",
            "terminal_properties",
            "workspace_dividers",
            "file_explorer",
            "registry_explorer",
            "client_overlay_scenes",
            "client_program_consent",
        ],
        1 => vec![
            "workspace_panels",
            "terminal_remote",
            "terminal_vox_runtime",
            "terminal_clipboard",
            "terminal_properties",
        ],
        2 => vec!["workspace_panels", "workspace_dividers"],
        3 => vec!["workspace_panels"],
        4 => vec!["workspace_panels"],
        5 => vec!["workspace_dividers"],
        6 => vec!["workspace_dividers"],
        7 => vec!["workspace_dividers"],
        8 => vec!["workspace_dividers"],
        9 => vec!["workspace_dividers"],
        10 => vec!["workspace_dividers"],
        11 => vec!["workspace_dividers"],
        _ => vec![],
    };
    (vec![], any)
}
fn validate_bytes(bytes: &[u8], expected: &str, count: u64) -> Result<()> {
    ensure!(
        bytes.len() as u64 == count && sha256(bytes) == expected,
        "exact primitive bytes changed"
    );
    ensure!(
        !bytes.starts_with(&[0xef, 0xbb, 0xbf])
            && !bytes.contains(&b'\r')
            && bytes.ends_with(b"\n"),
        "only raw LF/final-LF primitive text authorized"
    );
    std::str::from_utf8(bytes)?;
    Ok(())
}

#[test]
fn two_hundred_forty_frozen_cells_and_forty_eight_raw_bodies_reconstruct() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut cells = 0;
    let mut outputs = 0;
    for (name, _) in CONTEXTS {
        let (kind, target) = name
            .split_once('/')
            .ok_or_else(|| eyre::eyre!("invalid context"))?;
        let context = if kind == "dev" {
            fixture.full_context(target)?
        } else {
            fixture.shared.context(target, &[])?
        };
        for (index, fact) in FILES.iter().enumerate() {
            let expected = kind == "dev" && (fact.5 || is_d2(target));
            assert_eq!(
                fixture.selected(&context)?.contains(&index),
                expected,
                "{name} {}",
                fact.0
            );
            if expected {
                assert_eq!(
                    fixture.render(index, &context)?.as_bytes(),
                    fixture.raw[fact.3]
                );
                outputs += 1;
            } else {
                assert!(fixture.render(index, &context).is_err());
            }
            cells += 1;
        }
    }
    assert_eq!((cells, outputs), (240, 48));
    Ok(())
}

#[test]
fn singleton_pair_none_and_all_consumer_masks_keep_each_model_independent() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut count = 0;
    for target in TARGETS {
        for (index, fact) in FILES.iter().enumerate() {
            let (_, owners) = predicates(index);
            let mut profiles = BTreeSet::from([vec![], owners.clone()]);
            for owner in &owners {
                profiles.insert(vec![*owner]);
            }
            for (i, left) in owners.iter().enumerate() {
                for right in owners.iter().skip(i + 1) {
                    profiles.insert(vec![*left, *right]);
                }
            }
            for flags in profiles {
                let supported = flags.iter().all(|f| {
                    fixture.ledger.prerequisite_definitions[*f]
                        .supported_targets
                        .iter()
                        .any(|t| t == target)
                });
                if !supported {
                    assert!(fixture.context(target, &flags).is_err());
                    continue;
                }
                let context = fixture.context(target, &flags)?;
                let expected =
                    (fact.5 || is_d2(target)) && owners.iter().any(|f| enabled(&context, f));
                assert_eq!(fixture.selected(&context)?.contains(&index), expected);
                if expected {
                    assert_eq!(
                        fixture.render(index, &context)?.as_bytes(),
                        fixture.raw[fact.3]
                    );
                } else {
                    assert!(fixture.render(index, &context).is_err());
                }
                count += 1;
            }
        }
    }
    assert!(count > 200);
    Ok(())
}

#[test]
fn divider_only_value_models_use_real_panel_identity_without_enabling_workspace_host() -> Result<()>
{
    let fixture = Fixture::load()?;
    let provider = path("SFMWorkspacePanelId");
    let bytes = fixture.shared.read_source(&provider)?;
    validate_bytes(
        &bytes,
        "sha256:c300406b1a1a52c4ea856d399f2f06c17564dbc52be863d6e5ec47e033880439",
        320,
    )?;
    let rules = fixture
        .shared
        .metadata
        .source_rules
        .get(&provider)
        .ok_or_else(|| eyre::eyre!("panel identity rule absent"))?;
    ensure!(
        rules.len() == 1,
        "panel identity must remain one canonical raw source"
    );
    ensure!(
        rules[0].input == provider
            && rules[0].when.all_features.is_empty()
            && rules[0].when.none_features.is_empty()
            && rules[0].when.targets.is_empty()
            && set(&rules[0].when.any_features)
                == BTreeSet::from(["workspace_panels", "workspace_dividers"]),
        "panel identity consumer union integration missing"
    );
    for target in &TARGETS[..2] {
        let context = fixture.shared.context(target, &["workspace_dividers"])?;
        assert_eq!(
            fixture.selected(&context)?,
            BTreeSet::from([0, 2, 5, 6, 7, 8, 9, 10, 11])
        );
        let mut paths = inventory();
        paths.insert(provider.clone());
        let selection = select_core_inputs(&fixture.shared.metadata, &context, &paths)?;
        assert_eq!(selection.inputs[&provider].input, provider);
        for flag in [
            "workspace_panels",
            "client_actions",
            "terminal_remote",
            "terminal_vox_runtime",
            "terminal_clipboard",
        ] {
            assert!(!enabled(&context, flag));
        }
        let ordinary = fixture.shared.context(target, &["workspace_panels"])?;
        assert_eq!(
            fixture.selected(&ordinary)?,
            BTreeSet::from([0, 1, 2, 3, 4])
        );
    }
    for target in &TARGETS[2..] {
        assert!(fixture.context(target, &["workspace_dividers"]).is_err());
    }
    Ok(())
}

fn model_type_tokens(source: &str) -> BTreeSet<&str> {
    // A bounded reference audit, not a Java parser. Exclude comments and
    // quoted literals so a Javadoc link does not become a fabricated compile
    // dependency. The reviewed carriers have ordinary ASCII type names.
    let bytes = source.as_bytes();
    let mut result = BTreeSet::new();
    let mut index = 0;
    while index < bytes.len() {
        if bytes[index..].starts_with(b"//") {
            index += 2;
            while index < bytes.len() && bytes[index] != b'\n' {
                index += 1;
            }
        } else if bytes[index..].starts_with(b"/*") {
            index += 2;
            while index < bytes.len() && !bytes[index..].starts_with(b"*/") {
                index += 1;
            }
            index = (index + 2).min(bytes.len());
        } else if bytes[index..].starts_with(b"\"\"\"") {
            index += 3;
            while index < bytes.len() && !bytes[index..].starts_with(b"\"\"\"") {
                index += if bytes[index] == b'\\' { 2 } else { 1 };
            }
            index = (index + 3).min(bytes.len());
        } else if matches!(bytes[index], b'\"' | b'\'') {
            let quote = bytes[index];
            index += 1;
            while index < bytes.len() && bytes[index] != quote {
                index += if bytes[index] == b'\\' { 2 } else { 1 };
            }
            index = (index + 1).min(bytes.len());
        } else if bytes[index].is_ascii_alphabetic() || bytes[index] == b'_' {
            let start = index;
            while index < bytes.len()
                && (bytes[index].is_ascii_alphanumeric() || bytes[index] == b'_')
            {
                index += 1;
            }
            let word = &source[start..index];
            if word.starts_with("SFM") && word.len() > 3 {
                result.insert(word);
            }
        } else {
            index += 1;
        }
    }
    result
}

#[test]
fn pure_type_reference_audit_resolves_models_and_exact_terminal_geometry_contracts() -> Result<()> {
    assert_eq!(
        model_type_tokens(
            "/* SFMDoc */ SFMActual // SFMLine\n\"SFMString\\\" SFMStillString\" '\\'' SFMOther \"\"\"SFMTextBlock\"\"\""
        ),
        BTreeSet::from(["SFMActual", "SFMOther"])
    );
    let fixture = Fixture::load()?;
    let provider = path("SFMWorkspacePanelId");
    for target in TARGETS {
        let mut requested = vec!["workspace_panels"];
        if is_d2(target) {
            requested.extend(["workspace_dividers", "terminal_clipboard"]);
        }
        let context = fixture.context(target, &requested)?;
        let mut paths = inventory();
        paths.insert(provider.clone());
        let terminal_paths = [
            "src/main/java/ca/teamdman/sfm/client/terminal/SFMTerminalImageLayout.java",
            "src/main/java/ca/teamdman/sfm/client/terminal/SFMTerminalSelectionLayout.java",
        ];
        paths.extend(terminal_paths.map(str::to_owned));
        let selection = select_core_inputs(&fixture.shared.metadata, &context, &paths)?;
        let providers = fixture
            .selected(&context)?
            .iter()
            .map(|i| FILES[*i].0)
            .chain(std::iter::once("SFMWorkspacePanelId"))
            .collect::<BTreeSet<_>>();
        for index in fixture.selected(&context)? {
            let rendered = fixture.render(index, &context)?;
            assert!(model_type_tokens(&rendered).is_subset(&providers));
            for dependency in model_type_tokens(&rendered) {
                assert!(selection.inputs.contains_key(&path(dependency)));
            }
        }
        if is_d2(target) {
            let bounds = fixture.render(0, &context)?;
            let metrics = fixture.render(1, &context)?;
            assert!(bounds.contains(
                "public record SFMScreenPanelBounds(int x, int y, int width, int height)"
            ));
            for field in [
                "SFMScreenPanelBounds logicalBounds,",
                "SFMScreenPanelBounds globalGuiLogicalBounds,",
                "double panelRenderScale,",
                "double guiToPhysicalScaleX,",
                "double guiToPhysicalScaleY,",
            ] {
                assert!(metrics.contains(field));
            }
            let mut known = providers.clone();
            known.extend(["SFMTerminalImageLayout", "SFMTerminalSelectionLayout"]);
            for (index, p) in terminal_paths.iter().enumerate() {
                ensure!(
                    selection
                        .inputs
                        .get(*p)
                        .is_some_and(|input| input.input == *p),
                    "actual terminal layout not selected in clipboard proof"
                );
                let source = fixture.shared.read_source(p)?;
                let digest = if index == 0 {
                    "sha256:b9fd3fdd2471349e9727e4daa41be81f3be6bf0d4bc654b92a43ac8fe4b7a39c"
                } else {
                    "sha256:207bf89fd772530f32aafbec2229b9be68bece9668618d47732537d5af4bdf7f"
                };
                ensure!(
                    sha256(&source) == digest,
                    "reviewed terminal geometry API changed"
                );
                let rendered = render_java_source(std::str::from_utf8(&source)?, &context)?;
                assert!(model_type_tokens(&rendered).is_subset(&known));
            }
            let clipboard_only = fixture.shared.context(target, &["terminal_clipboard"])?;
            assert_eq!(fixture.selected(&clipboard_only)?, BTreeSet::from([0, 1]));
            assert!(
                !enabled(&clipboard_only, "workspace_panels")
                    && !enabled(&clipboard_only, "workspace_dividers")
            );
        }
    }
    // This checks source/type/accessor seams only. It is deliberately not a
    // compiler, annotation-processor, UI host or native-backend execution proof.
    Ok(())
}

#[derive(Facet)]
struct AnnotationLock {
    schema_version: u32,
    artifacts: Vec<AnnotationArtifact>,
}
#[derive(Facet)]
struct AnnotationArtifact {
    coordinate: Option<String>,
    hash: String,
}

#[test]
fn nullable_remains_a_real_locked_project_compile_annotation_without_acquisition() -> Result<()> {
    let fixture = Fixture::load()?;
    assert!(
        std::str::from_utf8(&fixture.sources[9])?
            .contains("import org.jetbrains.annotations.Nullable;")
    );
    for target in &TARGETS[..2] {
        let lock_path = format!("build/lockfiles/{target}/schema-2.json");
        let bytes = read_bounded(&fixture.shared.core.join(lock_path), 2 * 1024 * 1024)?;
        let lock: AnnotationLock = facet_json::from_str(std::str::from_utf8(&bytes)?)?;
        ensure!(
            lock.schema_version == 2,
            "reviewed annotation lock schema changed"
        );
        let matching = lock
            .artifacts
            .iter()
            .filter(|a| a.coordinate.as_deref() == Some("org.jetbrains:annotations:24.0.1"))
            .collect::<Vec<_>>();
        assert_eq!(matching.len(), 1);
        assert_eq!(
            matching[0].hash,
            "blake3:9a3d2fae94bf334d4dc72f71112c47755d37f533"
        );
    }
    let engine = read_bounded(
        &fixture
            .shared
            .repository
            .join("platform/cli/sfm-propagate-changes/src/jar_build/engine.rs"),
        2 * 1024 * 1024,
    )?;
    let engine = std::str::from_utf8(&engine)?;
    assert!(engine.contains("const PROJECT_COMPILE_ANNOTATION_COORDINATES"));
    assert!(engine.contains("\"org.jetbrains:annotations:24.0.1\""));
    let sources = read_bounded(
        &fixture
            .shared
            .repository
            .join("platform/cli/sfm-propagate-changes/src/jar_build/engine_sources.rs"),
        2 * 1024 * 1024,
    )?;
    let sources = std::str::from_utf8(&sources)?;
    assert!(sources.contains("fn resolve_project_compile_classpath("));
    assert!(
        sources.contains("let annotation_coordinates = PROJECT_COMPILE_ANNOTATION_COORDINATES")
    );
    assert!(sources.contains("ArtifactPurpose::from(\"Project compile annotations\")"));
    Ok(())
}

#[test]
fn unapproved_raw_eol_bom_eof_and_token_mutations_are_rejected() -> Result<()> {
    let fixture = Fixture::load()?;
    for (oid, digest, count) in RAW {
        let raw = &fixture.raw[oid];
        assert!(validate_bytes(&raw[..raw.len() - 1], digest, count).is_err());
        let crlf = std::str::from_utf8(raw)?.replace('\n', "\r\n").into_bytes();
        assert!(validate_bytes(&crlf, digest, count).is_err());
        let mut bom = vec![0xef, 0xbb, 0xbf];
        bom.extend(raw);
        assert!(validate_bytes(&bom, digest, count).is_err());
        let mut token = raw.clone();
        token[0] ^= 1;
        assert!(validate_bytes(&token, digest, count).is_err());
    }
    Ok(())
}

#[test]
fn forty_eight_common_edits_propagate_through_one_core_input_without_real_writes() -> Result<()> {
    let fixture = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let anchor = "package ca.teamdman.sfm.client.screen.workspace;\n";
    let replacement = format!("{anchor}\n// Common workspace primitive edit proof.\n");
    for (index, fact) in FILES.iter().enumerate() {
        let source = std::str::from_utf8(&fixture.sources[index])?;
        ensure!(source.matches(anchor).count() == 1, "common anchor changed");
        let p = temp.path().join(CORE_ROOT).join(path(fact.0));
        fs::create_dir_all(
            p.parent()
                .ok_or_else(|| eyre::eyre!("fixture parent absent"))?,
        )?;
        fs::write(p, source.replacen(anchor, &replacement, 1))?;
    }
    let mut outputs = 0;
    for target in TARGETS {
        let context = fixture.full_context(target)?;
        let selection = select_core_inputs(&fixture.shared.metadata, &context, &inventory())?;
        for index in fixture.selected(&context)? {
            let p = path(FILES[index].0);
            let input = &selection.inputs[&p];
            let bytes = read_bounded(&temp.path().join(CORE_ROOT).join(&input.input), LIMIT)?;
            let rendered = render_java_source(std::str::from_utf8(&bytes)?, &context)?;
            assert_eq!(
                rendered,
                fixture
                    .render(index, &context)?
                    .replacen(anchor, &replacement, 1)
            );
            assert_eq!(fixture.shared.read_source(&p)?, fixture.sources[index]);
            outputs += 1;
        }
    }
    assert_eq!(outputs, 48);
    Ok(())
}

#[test]
fn actual_collector_renders_java_from_fixed_core_and_refuses_snapshot_routing() -> Result<()> {
    let fixture = Fixture::load()?;
    let context = fixture.context("1.19.2", &["workspace_dividers"])?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    let paths = inventory();
    let mut metadata = fixture.shared.metadata.clone();
    metadata.source_rules.retain(|p, _| paths.contains(p));
    for variants in metadata.source_rules.values_mut() {
        for variant in variants {
            variant.template = false;
        }
    }
    let selection = select_core_inputs(&metadata, &context, &paths)?;
    for (output, input) in &selection.inputs {
        let bytes = if let Some(index) = FILES.iter().position(|f| path(f.0) == *output) {
            fixture.sources[index].clone()
        } else {
            read_bounded(&fixture.shared.core.join(&input.input), 16 * 1024 * 1024)?
        };
        let p = root.join(&input.input);
        fs::create_dir_all(
            p.parent()
                .ok_or_else(|| eyre::eyre!("fixture parent absent"))?,
        )?;
        fs::write(p, bytes)?;
    }
    let artifacts = collect_core_artifacts(&root, &selection, &context)?;
    for index in fixture.selected(&context)? {
        let p = path(FILES[index].0);
        let artifact = &artifacts[&p];
        assert_eq!(artifact.source_bytes, fixture.sources[index]);
        assert_eq!(artifact.source_path, format!("{CORE_ROOT}/{p}"));
        assert!(artifact.overlay.is_none());
        let (_, body) = std::str::from_utf8(&artifact.output_bytes)?
            .split_once('\n')
            .ok_or_else(|| eyre::eyre!("banner absent"))?;
        assert_eq!(body, fixture.render(index, &context)?);
    }
    let p = path(FILES[0].0);
    let source = std::str::from_utf8(&fixture.sources[0])?;
    fs::write(
        root.join(&p),
        format!(
            "{source}{{% if features.workspace_dividers %}}\n// automatic Java proof\n{{% endif %}}\n"
        ),
    )?;
    let artifacts = collect_core_artifacts(&root, &selection, &context)?;
    assert!(
        std::str::from_utf8(&artifacts[&p].output_bytes)?.ends_with("// automatic Java proof\n")
    );
    fs::write(
        root.join(&p),
        format!("{{% case environment %}}\n{{% when \"release\" %}}\n{source}{{% endcase %}}\n"),
    )?;
    assert!(collect_core_artifacts(&root, &selection, &context).is_err());
    assert!(collect_core_artifacts(&temp.path().join("other-core"), &selection, &context).is_err());
    for (index, fact) in FILES.iter().enumerate() {
        assert_eq!(
            fixture.shared.read_source(&path(fact.0))?,
            fixture.sources[index]
        );
    }
    Ok(())
}

#[test]
fn unsupported_unknown_and_malformed_primitive_sources_fail_closed() -> Result<()> {
    let fixture = Fixture::load()?;
    for target in TARGETS {
        assert!(
            fixture
                .shared
                .context(target, &["unknown_workspace_primitive_owner"])
                .is_err()
        );
        if !is_d2(target) {
            for owner in [
                "workspace_dividers",
                "terminal_clipboard",
                "terminal_properties",
                "client_program_consent",
            ] {
                assert!(fixture.context(target, &[owner]).is_err());
            }
        }
        let remote = fixture.context(target, &["terminal_remote"])?;
        assert_eq!(
            fixture.selected(&remote)?,
            if is_d2(target) {
                BTreeSet::from([0, 1])
            } else {
                BTreeSet::from([0])
            }
        );
        assert!(!enabled(&remote, "workspace_panels") && !enabled(&remote, "workspace_dividers"));
    }
    let context = fixture.context("1.19.2", &["workspace_panels"])?;
    let source = std::str::from_utf8(&fixture.sources[0])?;
    for changed in [
        format!("{{% else %}}\n{source}"),
        format!("{{% if features.unknown_workspace_primitive_owner %}}\n{source}{{% endif %}}\n"),
        format!("{{% if features.workspace_panels %}}\n{source}{{% endcase %}}\n"),
    ] {
        assert!(render_java_source(&changed, &context).is_err());
    }
    Ok(())
}

#[test]
fn twenty_bounded_offline_git_trees_confirm_two_hundred_forty_cells() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut present = 0;
    for (name, commit) in CONTEXTS {
        let (kind, target) = name
            .split_once('/')
            .ok_or_else(|| eyre::eyre!("invalid context"))?;
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
            FILES
                .iter()
                .map(|f| format!("platform/minecraft/{}", path(f.0))),
        );
        let mut child = command
            .stdout(Stdio::piped())
            .stderr(Stdio::null())
            .spawn()?;
        let mut reader = child
            .stdout
            .take()
            .ok_or_else(|| eyre::eyre!("tree output absent"))?;
        let mut bytes = Vec::new();
        let result = reader.by_ref().take(8193).read_to_end(&mut bytes);
        drop(reader);
        if result.is_err() || bytes.len() > 8192 {
            let _ = child.kill();
            let _ = child.wait();
            result?;
            eyre::bail!("tree output too large");
        }
        ensure!(child.wait()?.success(), "offline tree read failed");
        let mut expected = BTreeMap::new();
        for fact in FILES {
            if kind == "dev" && (fact.5 || is_d2(target)) {
                let p = path(fact.0);
                expected.insert(
                    p.clone(),
                    format!("100644 blob {}\tplatform/minecraft/{p}\0", fact.3),
                );
                present += 1;
            }
        }
        assert_eq!(
            bytes,
            expected.into_values().collect::<String>().as_bytes(),
            "{name}"
        );
    }
    assert_eq!(present, 48);
    Ok(())
}
