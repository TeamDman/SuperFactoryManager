//! Frozen pure terminal value-model source proofs.
//!
//! Run after root promotion and sparse-rule registration. Local frozen Git
//! blobs are test-only goldens, never production source selection. These
//! standard-library models do not imply backend, UI or process readiness.
//! Future deliberate core authoring must explicitly review golden changes.
//! Current four pure consumer unions are checked exactly before deriving a
//! private historical fixture view. That view retains the original 320 cells,
//! 48 present / 272 absent outputs and 336 masks; it is not current selection
//! evidence. Current actual selector/collector proof belongs to model-v2 and
//! presentation-v2, while this module also refuses 44 live-rule mutations.

#![cfg(test)]

use super::context::ProjectionContext;
use super::core_inputs::CORE_ROOT;
use super::core_inputs::CoreProjectInputs;
use super::core_inputs::InputPredicate;
use super::core_inputs::InputVariant;
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

const LEDGER: &str = "docs/tasks/sfm-core-terminal-value-models-slice.json";
const LEDGER_SHA: &str = "sha256:6b952d7f1f463026ee6c727c3622c828c6a81f8341de1d13b09f3387797a4002";
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
// Name, frozen Git blob, raw/stage SHA-256, byte count, all-ten class support.
const FACTS: [(&str, &str, &str, u64, bool); 16] = [
    (
        "SFMTerminalLine",
        "dd20303165846720d0b72f61a420716f1adefe39",
        "sha256:c64ef66f07eb705bec614978a5a10846243220be4296dc19d291dee82170aecb",
        505,
        true,
    ),
    (
        "SFMTerminalResponse",
        "d7c55c4d75a95b2739c04b5ec928a68ca64c8df5",
        "sha256:61245350da59b9b7782e2697ed384639ba448307d519e771053ccd91a400c81a",
        1989,
        true,
    ),
    (
        "SFMTerminalInputDisposition",
        "4ddd50e78f598d8bbed756a1e38a59ff7ee84924",
        "sha256:113ab0d519c7f06ee5cc8a257b381084f9f9a7cf3c247c1bae4927bd89570845",
        218,
        false,
    ),
    (
        "SFMTerminalError",
        "824533a3908254254cb1e85c53926a78781014c3",
        "sha256:9f8cf05197d9ccef4d62648759e9525c9ba4868801ee3ea953c987585e73e660",
        452,
        false,
    ),
    (
        "SFMTerminalErrorCode",
        "6699803de6099ca8db4e25af354ca9b13fff1326",
        "sha256:ec05d9eb1b6f8bfc9a68d0b84df878a3a7850ea0535af81e075b27b69d63897d",
        295,
        false,
    ),
    (
        "SFMTerminalCopyResult",
        "43c1e3a7bb41b3b246250fc227f8cbc0c3d4f805",
        "sha256:2a8c1ae0dedeebcd0e4d9e07144235ea4e48e873aa21b626f6eff8c619483a22",
        624,
        false,
    ),
    (
        "SFMTerminalPasteResult",
        "c79f0f1565c942baac6771a275d92f6cefa1f975",
        "sha256:c70eec6f3d33d98515de76e871c4a80b08a9045af6a6e5949495daa33360f009",
        951,
        false,
    ),
    (
        "SFMTerminalPasteWarning",
        "c608695c177227958874bf94cbfc086657eb9918",
        "sha256:0eebdcf13f47c5bf8fd2398f51c391753e4927234266d2b5684699e3909ce637",
        950,
        false,
    ),
    (
        "SFMTerminalSelection",
        "1a8f25b6a46e860576bbb13faeea7f7786419b6b",
        "sha256:56ee1f5301e50d73c03d77a56fcdbade5ef9d6641d4639dc9d6465c7b0c041a5",
        217,
        false,
    ),
    (
        "SFMTerminalRasterLimits",
        "6563598655c2aa772d726ddee86443e81682cd76",
        "sha256:c86d2e0b27ee5b2b9ff3abb550ac3334dca7cd496b777005403f42a9bc2869bd",
        1844,
        false,
    ),
    (
        "SFMTerminalRasterRegion",
        "f041d2f5eb38580efa74f5009937a4fe45e26c60",
        "sha256:639e9e8785b7b3487042192c10f9e151f116ff5281202f1641a4059616fbcd2b",
        635,
        false,
    ),
    (
        "SFMTerminalRasterEncoding",
        "9d29ac36b109a85db4f85b16db40548ce29df109",
        "sha256:172938ef77abed2a5a0891048cef6810ad9ba1f52939a472d96c8c0eb3879fbb",
        368,
        false,
    ),
    (
        "SFMTerminalRasterFrameKind",
        "38dba34413a59159f0ec0a412e7927e2eb368897",
        "sha256:246f5cfba640da316d5d1c7fcfca1d40b63447973839a497f52c5d52afe2637d",
        185,
        false,
    ),
    (
        "SFMTerminalRasterColorSpace",
        "48604717a1d3ed80fcd135e4c19de3a9696a9155",
        "sha256:c57f1497a3132f65a7d8db1790d7f73a45f8bde2299b4b2dbbff96f5ca0f125d",
        249,
        false,
    ),
    (
        "SFMTerminalRasterAlphaMode",
        "f3adc83b0fbbe2a1d964e219026fa6d9a55dfc13",
        "sha256:a5f44ab5552af2a44b350a671192ae0c247b6e5e11eb11711ebf916e01d0988e",
        259,
        false,
    ),
    (
        "SFMTerminalRasterOrigin",
        "59ce2763b4d253c7a2275922b8486f2b2e7a7271",
        "sha256:e01650bff7c129091703d4cafd86ca74ecb5be0c4d8851487b01d95d446231e3",
        251,
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
    raw_git_blob: String,
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
        let shared = historical_value_model_view_from_reviewed_current(CoreTestFixture::load()?)?;
        let bytes = read_bounded(&shared.repository.join(LEDGER), LIMIT)?;
        ensure!(
            sha256(&bytes) == LEDGER_SHA,
            "reviewed terminal-model ledger changed"
        );
        let ledger: Ledger = facet_json::from_str(std::str::from_utf8(&bytes)?)
            .wrap_err("cannot parse bounded terminal-model evidence")?;
        let sources = FACTS
            .iter()
            .map(|fact| shared.read_source(&model_path(fact.0)))
            .collect::<Result<Vec<_>>>()?;
        let raw = read_git_blobs(
            &shared.repository,
            &FACTS.iter().map(|fact| fact.1.to_owned()).collect(),
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
        let scope = &self.ledger.scope;
        ensure!(
            self.ledger.schema == "sfm:core-terminal-value-models-slice@1"
                && scope.production_files == 16
                && scope.context_cells == 320
                && scope.present_cells == 48
                && scope.absent_cells == 272
                && scope.raw_variants == 16
                && scope.stage_bytes == 9992
                && scope.new_flags == 0
                && scope.new_dependencies == 0
                && !scope.java_compilation
                && !scope.live_runtime,
            "bounded pure terminal-model scope changed"
        );
        let contexts = CONTEXTS
            .into_iter()
            .map(|(name, oid)| (name.to_owned(), oid.to_owned()))
            .collect::<BTreeMap<_, _>>();
        ensure!(
            self.ledger.context_commits == contexts,
            "frozen terminal contexts changed"
        );
        ensure!(
            self.ledger.normalization.policy == "none"
                && self.ledger.normalization.raw_byte_exact
                && self
                    .ledger
                    .normalization
                    .authorized_transformations
                    .is_empty(),
            "no terminal-model normalization is authorized"
        );
        ensure!(
            self.ledger.files.len() == 16
                && self.ledger.raw_variants.len() == 16
                && self.ledger.full_source_profiles.len() == 10,
            "model count changed"
        );
        for (name, reviewed) in &self.ledger.prerequisite_definitions {
            let actual = self
                .shared
                .features
                .0
                .get(name)
                .ok_or_else(|| eyre::eyre!("terminal model feature not registered: {name}"))?;
            ensure!(
                actual.supported_targets == reviewed.supported_targets
                    && actual.requires == reviewed.requires,
                "model consumer support/dependency changed: {name}"
            );
        }
        for (index, fact) in FACTS.iter().enumerate() {
            let file = &self.ledger.files[index];
            let path = model_path(fact.0);
            let support = if fact.4 {
                TARGETS.to_vec()
            } else {
                TARGETS[..2].to_vec()
            };
            let owners = owners(fact.0)?;
            ensure!(
                file.name == fact.0
                    && file.intended_core_path == path
                    && file.stage_bytes == fact.3
                    && file.stage_sha256 == fact.2
                    && file.raw_git_blob == fact.1
                    && file.class_support == support
                    && file.source_rule.input == path
                    && file.source_rule.template
                    && file.source_rule.when.targets == support
                    && file.source_rule.when.any_features == owners
                    && file.witnesses.len() == 20,
                "terminal model source/support/predicate identity changed: {}",
                fact.0
            );
            validate_raw(&self.sources[index], fact.2, fact.3)?;
            validate_raw(&self.raw[fact.1], fact.2, fact.3)?;
            ensure!(
                self.sources[index] == self.raw[fact.1],
                "model core text is not its exact frozen raw body"
            );
            let raw = &self.ledger.raw_variants[index];
            ensure!(
                raw.name == fact.0
                    && raw.git_blob == fact.1
                    && raw.sha256 == fact.2
                    && raw.bytes == fact.3
                    && raw.crlf_count == 0
                    && raw.lone_cr_count == 0
                    && raw.final_lf
                    && !raw.bom,
                "raw terminal-model facts changed"
            );
            let actual = self
                .shared
                .metadata
                .source_rules
                .get(&path)
                .ok_or_else(|| eyre::eyre!("explicit terminal-model membership rule absent"))?;
            ensure!(
                actual.len() == 1,
                "model must have one canonical sparse input"
            );
            let actual = &actual[0];
            ensure!(
                actual.input == path
                    && actual.when.all_features.is_empty()
                    && actual.when.none_features.is_empty()
                    && actual
                        .when
                        .targets
                        .iter()
                        .map(String::as_str)
                        .collect::<BTreeSet<_>>()
                        == support.iter().copied().collect()
                    && actual
                        .when
                        .any_features
                        .iter()
                        .map(String::as_str)
                        .collect::<BTreeSet<_>>()
                        == owners.iter().copied().collect(),
                "actual terminal model membership widened or changed: {}",
                fact.0
            );
            let mut seen = BTreeSet::new();
            for row in &file.witnesses {
                let (kind, target) = row
                    .context
                    .split_once('/')
                    .ok_or_else(|| eyre::eyre!("invalid terminal witness"))?;
                let present = kind == "dev" && support.contains(&target);
                ensure!(
                    seen.insert(row.context.as_str())
                        && contexts.get(&row.context) == Some(&row.commit)
                        && row.present == present
                        && row.git_blob.as_deref() == present.then_some(fact.1)
                        && row.raw_sha256.as_deref() == present.then_some(fact.2)
                        && row.raw_bytes == if present { fact.3 } else { 0 },
                    "frozen terminal-model membership changed: {}",
                    fact.0
                );
            }
            ensure!(
                seen == contexts.keys().map(String::as_str).collect(),
                "model witness coverage changed"
            );
            let source = std::str::from_utf8(&self.sources[index])?;
            ensure!(
                !source.contains("{%"),
                "pure shared model acquired an unreviewed source branch"
            );
            for line in source.lines().filter(|line| line.starts_with("import ")) {
                ensure!(
                    line.starts_with("import java."),
                    "model has an unreviewed host dependency"
                );
            }
        }
        Ok(())
    }

    fn selected(&self, context: &ProjectionContext) -> Result<BTreeSet<usize>> {
        let selection = select_core_inputs(&self.shared.metadata, context, &inventory())?;
        let mut selected = BTreeSet::new();
        for (index, file) in self.ledger.files.iter().enumerate() {
            let expected = file.class_support.contains(&selection.target_id)
                && file
                    .source_rule
                    .when
                    .any_features
                    .iter()
                    .any(|name| enabled(context, name));
            if let Some(input) = selection.inputs.get(&file.intended_core_path) {
                ensure!(
                    expected
                        && input.input == file.intended_core_path
                        && !selection.omitted_paths.contains(&file.intended_core_path),
                    "model owner/target/source provenance escaped sparse rule"
                );
                selected.insert(index);
            } else {
                ensure!(
                    !expected && selection.omitted_paths.contains(&file.intended_core_path),
                    "inactive model must be explicitly omitted"
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

    fn full_context(&self, target: &str) -> Result<ProjectionContext> {
        let profile = self
            .ledger
            .full_source_profiles
            .iter()
            .find(|row| row.target == target)
            .ok_or_else(|| eyre::eyre!("model full profile absent"))?;
        let context = self.shared.context(
            target,
            &profile
                .enabled
                .iter()
                .map(String::as_str)
                .collect::<Vec<_>>(),
        )?;
        let selected = self.selected(&context)?;
        let paths = selected
            .iter()
            .map(|index| model_path(FACTS[*index].0))
            .collect::<Vec<_>>();
        ensure!(
            paths == profile.selected_paths,
            "reviewed model full-profile membership changed"
        );
        Ok(context)
    }

    fn owner_context(&self, target: &str, requested: &[&str]) -> Result<ProjectionContext> {
        // This test-only ledger closure produces an explicit set, then passes it
        // through the real feature validator. Production requests are not expanded.
        let mut names = requested
            .iter()
            .map(|name| (*name).to_owned())
            .collect::<BTreeSet<_>>();
        for _ in 0..64 {
            let mut changed = false;
            for name in names.clone() {
                let definition = self
                    .ledger
                    .prerequisite_definitions
                    .get(&name)
                    .ok_or_else(|| eyre::eyre!("unreviewed model-test feature: {name}"))?;
                ensure!(
                    definition
                        .supported_targets
                        .iter()
                        .any(|known| known == target),
                    "unsupported model-test owner: {name}/{target}"
                );
                for dependency in &definition.requires {
                    changed |= names.insert(dependency.clone());
                }
            }
            if !changed {
                return self.shared.context(
                    target,
                    &names.iter().map(String::as_str).collect::<Vec<_>>(),
                );
            }
            ensure!(names.len() <= 64, "bounded model test closure exceeded");
        }
        eyre::bail!("model consumer closure did not terminate")
    }
}
fn model_path(name: &str) -> String {
    format!("src/main/java/ca/teamdman/sfm/client/terminal/{name}.java")
}
fn inventory() -> BTreeSet<String> {
    FACTS.iter().map(|fact| model_path(fact.0)).collect()
}
fn enabled(context: &ProjectionContext, name: &str) -> bool {
    context.features.get(name).copied().unwrap_or(false)
}
fn owners(name: &str) -> Result<Vec<&'static str>> {
    Ok(match name {
        "SFMTerminalLine" | "SFMTerminalResponse" => {
            vec!["terminal_local", "terminal_remote", "terminal_vox_runtime"]
        }
        "SFMTerminalInputDisposition" => vec!["terminal_keyboard_input", "terminal_clipboard"],
        "SFMTerminalError" | "SFMTerminalErrorCode" => vec![
            "terminal_vox_runtime",
            "terminal_tuning_actions",
            "terminal_presentation_actions",
        ],
        "SFMTerminalCopyResult" | "SFMTerminalPasteResult" | "SFMTerminalSelection" => {
            vec!["terminal_clipboard"]
        }
        "SFMTerminalPasteWarning" => vec!["terminal_paste_confirmation"],
        "SFMTerminalRasterLimits" => vec![
            "terminal_vox_runtime",
            "terminal_remote",
            "terminal_tuning_actions",
            "terminal_properties",
        ],
        "SFMTerminalRasterRegion" => vec!["terminal_vox_runtime", "terminal_remote"],
        "SFMTerminalRasterEncoding"
        | "SFMTerminalRasterFrameKind"
        | "SFMTerminalRasterColorSpace"
        | "SFMTerminalRasterAlphaMode"
        | "SFMTerminalRasterOrigin" => vec![
            "terminal_vox_runtime",
            "terminal_remote",
            "terminal_presentation_actions",
        ],
        _ => eyre::bail!("unreviewed terminal model name: {name}"),
    })
}
// Exact current metadata is checked before creating the private historical view.
// These four rows alone differ from the immutable 16-model ledger.
const CURRENT_REFINED_VALUE_MODELS: [&str; 4] = [
    "SFMTerminalLine",
    "SFMTerminalResponse",
    "SFMTerminalError",
    "SFMTerminalErrorCode",
];

fn reviewed_current_value_rule(name: &str) -> Result<InputVariant> {
    ensure!(
        CURRENT_REFINED_VALUE_MODELS.contains(&name),
        "unreviewed current pure terminal model"
    );
    let mut consumers = owners(name)?;
    let targets = if matches!(name, "SFMTerminalError" | "SFMTerminalErrorCode") {
        consumers.push("terminal_remote");
        &TARGETS[..2]
    } else {
        &TARGETS[..]
    };
    consumers.push("terminal_properties");
    Ok(InputVariant {
        input: model_path(name),
        template: true,
        when: InputPredicate {
            targets: targets.iter().map(|target| (*target).to_owned()).collect(),
            any_features: consumers.into_iter().map(str::to_owned).collect(),
            ..InputPredicate::default()
        },
    })
}

fn validate_reviewed_current_value_rules(metadata: &CoreProjectInputs) -> Result<()> {
    for name in CURRENT_REFINED_VALUE_MODELS {
        let path = model_path(name);
        let expected = reviewed_current_value_rule(name)?;
        ensure!(
            metadata.source_rules.get(&path) == Some(&vec![expected]),
            "exact reviewed current pure terminal rule changed: {path}"
        );
    }
    Ok(())
}

fn historical_value_model_view_from_reviewed_current(
    mut historical: CoreTestFixture,
) -> Result<CoreTestFixture> {
    // Refuse any unreviewed live rule before deriving this private test view.
    validate_reviewed_current_value_rules(&historical.metadata)?;
    for name in CURRENT_REFINED_VALUE_MODELS {
        let path = model_path(name);
        let historical_consumers = owners(name)?.into_iter().map(str::to_owned).collect();
        let variants = historical
            .metadata
            .source_rules
            .get_mut(&path)
            .ok_or_else(|| eyre::eyre!("reviewed current pure terminal rule absent: {path}"))?;
        variants[0].when.any_features = historical_consumers;
    }
    // Only this fixture's four consumer lists change. The real core metadata,
    // source bodies, frozen ledger, target support and public catalog stay intact.
    Ok(historical)
}

fn validate_raw(bytes: &[u8], digest: &str, count: u64) -> Result<()> {
    ensure!(
        bytes.len() as u64 == count
            && sha256(bytes) == digest
            && bytes.ends_with(b"\n")
            && !bytes.contains(&b'\r')
            && !bytes.starts_with(&[0xef, 0xbb, 0xbf]),
        "exact LF terminal model identity changed"
    );
    Ok(())
}

#[test]
fn three_hundred_twenty_frozen_cells_reconstruct_forty_eight_exact_models() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut present = 0;
    let mut absent = 0;
    for (name, _) in CONTEXTS {
        let (kind, target) = name
            .split_once('/')
            .ok_or_else(|| eyre::eyre!("invalid context"))?;
        let context = if kind == "dev" {
            fixture.full_context(target)?
        } else {
            fixture.shared.context(target, &[])?
        };
        let selected = fixture.selected(&context)?;
        for (index, fact) in FACTS.iter().enumerate() {
            let expected = kind == "dev" && (fact.4 || matches!(target, "1.19.2" | "1.19.4"));
            assert_eq!(selected.contains(&index), expected, "{name} {}", fact.0);
            if expected {
                assert_eq!(
                    fixture.render(index, &context)?.as_bytes(),
                    fixture.raw[fact.1]
                );
                present += 1;
            } else {
                absent += 1;
            }
        }
    }
    assert_eq!((present, absent), (48, 272));
    Ok(())
}

#[test]
fn three_hundred_thirty_six_per_file_any_consumer_masks_use_actual_membership_and_renderer()
-> Result<()> {
    let fixture = Fixture::load()?;
    let mut rows = 0;
    for (index, fact) in FACTS.iter().enumerate() {
        let group = owners(fact.0)?;
        for target in if fact.4 { &TARGETS[..] } else { &TARGETS[..2] } {
            for mask in 0..(1_usize << group.len()) {
                let requested = group
                    .iter()
                    .enumerate()
                    .filter_map(|(bit, owner)| (mask & (1 << bit) != 0).then_some(*owner))
                    .collect::<Vec<_>>();
                let context = fixture.owner_context(target, &requested)?;
                let expected = group.iter().any(|name| enabled(&context, name));
                assert_eq!(fixture.selected(&context)?.contains(&index), expected);
                if expected {
                    assert_eq!(
                        fixture.render(index, &context)?.as_bytes(),
                        fixture.raw[fact.1]
                    );
                } else {
                    assert!(fixture.render(index, &context).is_err());
                }
                rows += 1;
            }
        }
    }
    assert_eq!(rows, 336);
    Ok(())
}

#[test]
fn supported_backend_flags_do_not_widen_d2_models_or_require_input_and_clipboard_services()
-> Result<()> {
    let fixture = Fixture::load()?;
    for target in TARGETS {
        for backend in ["terminal_local", "terminal_remote", "terminal_vox_runtime"] {
            let context = fixture.shared.context(target, &[backend])?;
            let selected = fixture.selected(&context)?;
            assert!(selected.contains(&0) && selected.contains(&1));
            if !matches!(target, "1.19.2" | "1.19.4") {
                assert_eq!(selected, BTreeSet::from([0, 1]));
            }
            for forbidden in [
                "terminal_clipboard",
                "terminal_keyboard_input",
                "terminal_paste_confirmation",
                "command_palette",
                "workspace_panels",
                "client_actions",
            ] {
                assert!(
                    !enabled(&context, forbidden),
                    "{target} {backend} {forbidden}"
                );
            }
        }
        let focus = fixture
            .shared
            .context(target, &["terminal_focus_gestures"])?;
        assert!(fixture.selected(&focus)?.is_empty());
        if matches!(target, "1.19.2" | "1.19.4") {
            let config = fixture.shared.context(target, &["terminal_vox"])?;
            assert!(fixture.selected(&config)?.is_empty());
        } else {
            for unsupported in [
                "terminal_clipboard",
                "terminal_keyboard_input",
                "terminal_paste_confirmation",
                "terminal_properties",
                "terminal_tuning_actions",
                "terminal_presentation_actions",
            ] {
                assert!(fixture.owner_context(target, &[unsupported]).is_err());
            }
        }
        assert!(
            fixture
                .shared
                .context(target, &["unknown_terminal_model_owner"])
                .is_err()
        );
    }
    Ok(())
}

#[test]
fn sixteen_exact_raw_models_reject_eol_bom_token_and_terminal_newline_mutations() -> Result<()> {
    let fixture = Fixture::load()?;
    for (index, fact) in FACTS.iter().enumerate() {
        let raw = &fixture.raw[fact.1];
        assert!(validate_raw(&raw[..raw.len() - 1], fact.2, fact.3).is_err());
        let crlf = std::str::from_utf8(raw)?.replace('\n', "\r\n").into_bytes();
        assert!(validate_raw(&crlf, fact.2, fact.3).is_err());
        let mut bom = vec![0xef, 0xbb, 0xbf];
        bom.extend(raw);
        assert!(validate_raw(&bom, fact.2, fact.3).is_err());
        let mut token = raw.clone();
        token[0] ^= 1;
        assert!(validate_raw(&token, fact.2, fact.3).is_err());
        assert_eq!(fixture.sources[index], *raw);
    }
    Ok(())
}

#[test]
fn forty_eight_common_edits_propagate_from_one_core_model_without_touching_real_inputs()
-> Result<()> {
    let fixture = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let anchor = "package ca.teamdman.sfm.client.terminal;\n";
    let replacement = format!("{anchor}\n// Common terminal model edit proof.\n");
    for (index, fact) in FACTS.iter().enumerate() {
        let source = std::str::from_utf8(&fixture.sources[index])?;
        ensure!(
            source.matches(anchor).count() == 1,
            "model common anchor changed"
        );
        let path = temp.path().join(CORE_ROOT).join(model_path(fact.0));
        fs::create_dir_all(
            path.parent()
                .ok_or_else(|| eyre::eyre!("fixture parent absent"))?,
        )?;
        fs::write(path, source.replacen(anchor, &replacement, 1))?;
    }
    let mut outputs = 0;
    for target in TARGETS {
        let context = fixture.full_context(target)?;
        let selection = select_core_inputs(&fixture.shared.metadata, &context, &inventory())?;
        for index in fixture.selected(&context)? {
            let path = model_path(FACTS[index].0);
            let input = &selection.inputs[&path];
            assert_eq!(input.input, path);
            let bytes = read_bounded(&temp.path().join(CORE_ROOT).join(&input.input), LIMIT)?;
            let output = render_java_source(std::str::from_utf8(&bytes)?, &context)?;
            assert_eq!(
                output,
                fixture
                    .render(index, &context)?
                    .replacen(anchor, &replacement, 1)
            );
            assert_eq!(fixture.shared.read_source(&path)?, fixture.sources[index]);
            outputs += 1;
        }
    }
    assert_eq!(outputs, 48);
    Ok(())
}

#[test]
fn pure_model_collection_enforces_fixed_core_and_java_rendering_without_backend_runtime_claim()
-> Result<()> {
    let fixture = Fixture::load()?;
    let context = fixture.shared.context("1.19.2", &["terminal_local"])?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    let paths = inventory();
    let mut metadata = fixture.shared.metadata.clone();
    metadata.source_rules.retain(|path, _| paths.contains(path));
    for variants in metadata.source_rules.values_mut() {
        for variant in variants {
            variant.template = false;
        }
    }
    let selected = select_core_inputs(&metadata, &context, &paths)?;
    for (output, selected) in &selected.inputs {
        let bytes = if let Some(index) = FACTS.iter().position(|fact| model_path(fact.0) == *output)
        {
            fixture.sources[index].clone()
        } else {
            read_bounded(&fixture.shared.core.join(&selected.input), 16 * 1024 * 1024)?
        };
        let path = root.join(&selected.input);
        fs::create_dir_all(
            path.parent()
                .ok_or_else(|| eyre::eyre!("fixture parent absent"))?,
        )?;
        fs::write(path, bytes)?;
    }
    let artifacts = collect_core_artifacts(&root, &selected, &context)?;
    for index in fixture.selected(&context)? {
        let path = model_path(FACTS[index].0);
        assert!(!selected.inputs[&path].template);
        let artifact = &artifacts[&path];
        assert_eq!(artifact.source_bytes, fixture.sources[index]);
        assert_eq!(artifact.source_path, format!("{CORE_ROOT}/{path}"));
        assert!(artifact.overlay.is_none());
        let output = std::str::from_utf8(&artifact.output_bytes)?;
        let (_, body) = output
            .split_once('\n')
            .ok_or_else(|| eyre::eyre!("banner absent"))?;
        assert_eq!(body, fixture.render(index, &context)?);
    }
    // Test-only directive addition proves automatic .java rendering even when
    // the ordinary pure source contains no branch and template hints are false.
    let line_path = model_path(FACTS[0].0);
    let appended = format!(
        "{}{{% if features.terminal_local %}}\n// automatic Java proof\n{{% endif %}}\n",
        std::str::from_utf8(&fixture.sources[0])?
    );
    fs::write(root.join(&line_path), appended)?;
    let rendered = collect_core_artifacts(&root, &selected, &context)?;
    let output = std::str::from_utf8(&rendered[&line_path].output_bytes)?;
    assert!(output.ends_with("// automatic Java proof\n"));
    assert!(!output.contains("{%"));
    assert!(
        collect_core_artifacts(&temp.path().join("alternate-core"), &selected, &context).is_err()
    );
    for (index, fact) in FACTS.iter().enumerate() {
        assert_eq!(
            fixture.shared.read_source(&model_path(fact.0))?,
            fixture.sources[index]
        );
    }
    Ok(())
}

#[test]
fn malformed_model_predicates_input_paths_and_source_directives_fail_closed() -> Result<()> {
    // Use fresh current metadata, before the isolated historical view is derived.
    // Actual current selection/collector assertions remain in model-v2 and
    // presentation-v2. These controls refuse damage to every reviewed live row.
    let current = CoreTestFixture::load()?;
    validate_reviewed_current_value_rules(&current.metadata)?;
    for name in CURRENT_REFINED_VALUE_MODELS {
        let path = model_path(name);
        let mut missing = current.metadata.clone();
        missing.source_rules.remove(&path);
        assert!(validate_reviewed_current_value_rules(&missing).is_err());
        for mutation in 0_u8..10 {
            let mut changed = current.metadata.clone();
            let variants = changed
                .source_rules
                .get_mut(&path)
                .ok_or_else(|| eyre::eyre!("current rule absent: {path}"))?;
            match mutation {
                0 => {
                    variants[0].when.any_features.pop();
                }
                1 => variants[0]
                    .when
                    .any_features
                    .push("terminal_clipboard".to_owned()),
                2 => variants[0].when.any_features.reverse(),
                3 => variants[0]
                    .when
                    .targets
                    .push("unreviewed_terminal_target".to_owned()),
                4 => variants[0]
                    .when
                    .all_features
                    .push("terminal_properties".to_owned()),
                5 => variants[0]
                    .when
                    .none_features
                    .push("terminal_remote".to_owned()),
                6 => variants[0].template = false,
                7 => variants[0].input.push_str(".unreviewed"),
                8 => {
                    let duplicate = variants[0].clone();
                    variants.push(duplicate);
                }
                _ => variants.clear(),
            }
            assert!(
                validate_reviewed_current_value_rules(&changed).is_err(),
                "unreviewed current pure terminal rule accepted: {path}, mutation {mutation}"
            );
        }
    }
    let fixture = Fixture::load()?;
    let path = model_path(FACTS[0].0);
    let mut metadata = fixture.shared.metadata.clone();
    let features = fixture
        .shared
        .features
        .0
        .keys()
        .cloned()
        .collect::<BTreeSet<_>>();
    let first = metadata
        .source_rules
        .get_mut(&path)
        .and_then(|rows| rows.first_mut())
        .ok_or_else(|| eyre::eyre!("model rule absent"))?;
    first
        .when
        .any_features
        .push("unknown_terminal_model_owner".to_owned());
    assert!(metadata.validate(&features).is_err());
    let mut metadata = fixture.shared.metadata.clone();
    let first = metadata
        .source_rules
        .get_mut(&path)
        .and_then(|rows| rows.first_mut())
        .ok_or_else(|| eyre::eyre!("model rule absent"))?;
    first.input = "../historical-tree/SFMTerminalLine.java".to_owned();
    assert!(metadata.validate(&features).is_err());
    let context = fixture.shared.context("1.19.2", &["terminal_local"])?;
    let source = std::str::from_utf8(&fixture.sources[0])?;
    for changed in [
        format!("{{% else %}}\n{source}"),
        format!("{{% if features.unknown_terminal_model_owner %}}\n{source}{{% endif %}}\n"),
        format!("{{% if features.terminal_local %}}\n{source}{{% endcase %}}\n"),
    ] {
        assert!(render_java_source(&changed, &context).is_err());
    }
    // Plain single-source rendering does not validate membership. Actual
    // collection separately forbids environment/snapshot routing selectors.
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    let paths = inventory();
    let mut metadata = fixture.shared.metadata.clone();
    metadata.source_rules.retain(|path, _| paths.contains(path));
    let selected = select_core_inputs(&metadata, &context, &paths)?;
    for (output, input) in &selected.inputs {
        let bytes = if let Some(index) = FACTS.iter().position(|fact| model_path(fact.0) == *output)
        {
            fixture.sources[index].clone()
        } else {
            read_bounded(&fixture.shared.core.join(&input.input), 16 * 1024 * 1024)?
        };
        let path = root.join(&input.input);
        fs::create_dir_all(
            path.parent()
                .ok_or_else(|| eyre::eyre!("fixture parent absent"))?,
        )?;
        fs::write(path, bytes)?;
    }
    let forbidden =
        format!("{{% case environment %}}\n{{% when \"release\" %}}\n{source}{{% endcase %}}\n");
    fs::write(root.join(&path), forbidden)?;
    assert!(collect_core_artifacts(&root, &selected, &context).is_err());
    Ok(())
}

#[test]
fn three_hundred_twenty_bounded_offline_git_cells_preserve_exact_sources_and_absence() -> Result<()>
{
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
            FACTS
                .iter()
                .map(|fact| format!("platform/minecraft/{}", model_path(fact.0))),
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
            eyre::bail!("bounded terminal tree output exceeded");
        }
        ensure!(child.wait()?.success(), "offline terminal tree read failed");
        let mut entries = BTreeMap::new();
        for fact in FACTS {
            if kind == "dev" && (fact.4 || matches!(target, "1.19.2" | "1.19.4")) {
                let path = model_path(fact.0);
                entries.insert(
                    path.clone(),
                    format!("100644 blob {}\tplatform/minecraft/{path}\0", fact.1),
                );
                present += 1;
            }
        }
        let expected = entries.into_values().collect::<String>();
        assert_eq!(bytes, expected.as_bytes(), "{name}");
    }
    assert_eq!(present, 48);
    Ok(())
}
