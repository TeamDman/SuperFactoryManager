//! Frozen source-only Echo/registrar migration proofs.
//!
//! Local Git blobs are bounded test witnesses, never production source routes.
//! Run after promotion and reviewed owner/rule registration. D1's old registrar
//! is already a controlled template; raw identity and rendered golden are
//! checked separately. No Java/runtime/full-project acceptance is claimed.
//! Current-v2 reverses only five reviewed notification guards for historical
//! assertions, then tests current independent members separately. Immutable
//! ledger/raw/full goldens are not rewritten to describe the refinement.
//! V3 additionally reverses five nested conjunction encodings to exact immutable v2.

#![cfg(test)]

use super::context::ProjectionContext;
use super::core_inputs::CORE_ROOT;
use super::core_inputs::collect_core_artifacts;
use super::core_inputs::select_core_inputs;
use super::core_slice_test_support::CoreTestFixture;
use super::core_slice_test_support::read_bounded;
use super::core_slice_test_support::read_git_blobs;
use super::core_workspace_palette_current_contract::current_workspace_palette_member_enabled;
use super::core_workspace_palette_current_contract::pre_workspace_palette_source;
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

const LEDGER: &str = "docs/tasks/sfm-core-echo-palette-slice.json";
const LEDGER_SHA: &str = "sha256:fb705a4cdd818579f2e28e12d36c684efe6f6778a80f25b4c3688abb357da1cb";
const LIMIT: u64 = 256 * 1024;
const PATHS: [&str; 2] = [
    "src/main/java/ca/teamdman/sfm/client/action/EchoAction.java",
    "src/main/java/ca/teamdman/sfm/client/action/SFMCommandPaletteActions.java",
];
const SOURCE_FACTS: [(u64, &str); 2] = [
    (
        2170,
        "sha256:c0d4e2b4dbb97e804f44c4d4694267f8115e5fff2f908fd8d681ff143db9df8e",
    ),
    (
        31977,
        "sha256:1862eb535ea1ee1b0a9f44906a6e257b5fe700211261fc42323536e0b47cd0b1",
    ),
];

// Current-v2 is a reviewed member-boundary refinement; immutable v1 facts stay above.
const CURRENT_REGISTRAR_FACTS: (u64, &str) = (
    32247,
    "sha256:16cf4557b043f1221c261024113098eb9f17b82c95a644025c221851730eac6a",
);
const V2_REGISTRAR_FACTS: (u64, &str) = (
    32162,
    "sha256:57c2d33c1b40378761e8aeaafbc586caa6986368da55fcccff24223d9862734d",
);
const REGISTRAR_V3_EDITS: [(&str, &str, &str); 5] = [
    (
        "COPY_TOAST_PATH",
        "{% if features.workspace_toast_path_actions and features.workspace_notifications %}\n    public static final SFMRegistryObject<SFMClientAction<?>, SFMToastPathAction> COPY_TOAST_PATH =\n            REGISTERER.register(\"toast/path/copy\", () -> new SFMToastPathAction(SFMToastPathAction.Operation.COPY));\n{% endif %}\n",
        "{% if features.workspace_toast_path_actions %}\n{% if features.workspace_notifications %}\n    public static final SFMRegistryObject<SFMClientAction<?>, SFMToastPathAction> COPY_TOAST_PATH =\n            REGISTERER.register(\"toast/path/copy\", () -> new SFMToastPathAction(SFMToastPathAction.Operation.COPY));\n{% endif %}\n{% endif %}\n",
    ),
    (
        "OPEN_TOAST_PATH_TEXT",
        "{% if features.workspace_toast_path_actions and features.workspace_notifications %}\n    public static final SFMRegistryObject<SFMClientAction<?>, SFMToastPathAction> OPEN_TOAST_PATH_TEXT =\n            REGISTERER.register(\"toast/path/text/open\", () -> new SFMToastPathAction(SFMToastPathAction.Operation.OPEN_TEXT));\n{% endif %}\n",
        "{% if features.workspace_toast_path_actions %}\n{% if features.workspace_notifications %}\n    public static final SFMRegistryObject<SFMClientAction<?>, SFMToastPathAction> OPEN_TOAST_PATH_TEXT =\n            REGISTERER.register(\"toast/path/text/open\", () -> new SFMToastPathAction(SFMToastPathAction.Operation.OPEN_TEXT));\n{% endif %}\n{% endif %}\n",
    ),
    (
        "OPEN_TOAST_PATH_EXPLORER",
        "{% if features.workspace_toast_path_actions and features.workspace_notifications %}\n    public static final SFMRegistryObject<SFMClientAction<?>, SFMToastPathAction> OPEN_TOAST_PATH_EXPLORER =\n            REGISTERER.register(\"toast/path/explorer/open\", () -> new SFMToastPathAction(SFMToastPathAction.Operation.OPEN_EXPLORER));\n\n{% endif %}\n",
        "{% if features.workspace_toast_path_actions %}\n{% if features.workspace_notifications %}\n    public static final SFMRegistryObject<SFMClientAction<?>, SFMToastPathAction> OPEN_TOAST_PATH_EXPLORER =\n            REGISTERER.register(\"toast/path/explorer/open\", () -> new SFMToastPathAction(SFMToastPathAction.Operation.OPEN_EXPLORER));\n\n{% endif %}\n{% endif %}\n",
    ),
    (
        "COPY_TOAST",
        "{% if features.workspace_toast_actions and features.workspace_notifications %}\n    public static final SFMRegistryObject<SFMClientAction<?>, SFMToastAction> COPY_TOAST = REGISTERER.register(\n            \"toast/copy\",\n            () -> new SFMToastAction(SFMToastAction.Operation.COPY)\n    );\n\n{% endif %}\n",
        "{% if features.workspace_toast_actions %}\n{% if features.workspace_notifications %}\n    public static final SFMRegistryObject<SFMClientAction<?>, SFMToastAction> COPY_TOAST = REGISTERER.register(\n            \"toast/copy\",\n            () -> new SFMToastAction(SFMToastAction.Operation.COPY)\n    );\n\n{% endif %}\n{% endif %}\n",
    ),
    (
        "COPY_TOAST_DETAILS",
        "{% if features.workspace_toast_actions and features.workspace_notifications %}\n    public static final SFMRegistryObject<SFMClientAction<?>, SFMToastAction> COPY_TOAST_DETAILS = REGISTERER.register(\n            \"toast/details/copy\", () -> new SFMToastAction(SFMToastAction.Operation.COPY_DETAILS));\n\n{% endif %}\n",
        "{% if features.workspace_toast_actions %}\n{% if features.workspace_notifications %}\n    public static final SFMRegistryObject<SFMClientAction<?>, SFMToastAction> COPY_TOAST_DETAILS = REGISTERER.register(\n            \"toast/details/copy\", () -> new SFMToastAction(SFMToastAction.Operation.COPY_DETAILS));\n\n{% endif %}\n{% endif %}\n",
    ),
];
const REGISTRAR_EDITS: [(&str, &str, &str); 5] = [
    (
        "COPY_TOAST_PATH",
        "{% if features.workspace_toast_path_actions %}\n    public static final SFMRegistryObject<SFMClientAction<?>, SFMToastPathAction> COPY_TOAST_PATH",
        "{% if features.workspace_toast_path_actions and features.workspace_notifications %}\n    public static final SFMRegistryObject<SFMClientAction<?>, SFMToastPathAction> COPY_TOAST_PATH",
    ),
    (
        "OPEN_TOAST_PATH_TEXT",
        "{% if features.workspace_toast_path_actions %}\n    public static final SFMRegistryObject<SFMClientAction<?>, SFMToastPathAction> OPEN_TOAST_PATH_TEXT",
        "{% if features.workspace_toast_path_actions and features.workspace_notifications %}\n    public static final SFMRegistryObject<SFMClientAction<?>, SFMToastPathAction> OPEN_TOAST_PATH_TEXT",
    ),
    (
        "OPEN_TOAST_PATH_EXPLORER",
        "{% if features.workspace_toast_path_actions %}\n    public static final SFMRegistryObject<SFMClientAction<?>, SFMToastPathAction> OPEN_TOAST_PATH_EXPLORER",
        "{% if features.workspace_toast_path_actions and features.workspace_notifications %}\n    public static final SFMRegistryObject<SFMClientAction<?>, SFMToastPathAction> OPEN_TOAST_PATH_EXPLORER",
    ),
    (
        "COPY_TOAST",
        "{% if features.workspace_toast_actions %}\n    public static final SFMRegistryObject<SFMClientAction<?>, SFMToastAction> COPY_TOAST =",
        "{% if features.workspace_toast_actions and features.workspace_notifications %}\n    public static final SFMRegistryObject<SFMClientAction<?>, SFMToastAction> COPY_TOAST =",
    ),
    (
        "COPY_TOAST_DETAILS",
        "{% if features.workspace_toast_actions %}\n    public static final SFMRegistryObject<SFMClientAction<?>, SFMToastAction> COPY_TOAST_DETAILS =",
        "{% if features.workspace_toast_actions and features.workspace_notifications %}\n    public static final SFMRegistryObject<SFMClientAction<?>, SFMToastAction> COPY_TOAST_DETAILS =",
    ),
];
const NOTIFICATION_MEMBERS: [&str; 5] = [
    "COPY_TOAST_PATH",
    "OPEN_TOAST_PATH_TEXT",
    "OPEN_TOAST_PATH_EXPLORER",
    "COPY_TOAST",
    "COPY_TOAST_DETAILS",
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
        "aea39744b023f5e86b9d9e89dc320de7fd1d3ee7",
        "sha256:c0d4e2b4dbb97e804f44c4d4694267f8115e5fff2f908fd8d681ff143db9df8e",
        2170,
    ),
    (
        "33ba029e7c13c95b8dc9f998acb77a2a8db476fd",
        "sha256:dd9518cfca18b5a395e9c3ba315a318abea26d23e54b675bf8b463d94e879c88",
        25346,
    ),
    (
        "600e046db28698b94bc16aed31d659a804949247",
        "sha256:d16b48f61b62542ea4cc8345ec8a250f5d700ab77488c3ae27306de55210b245",
        24667,
    ),
    (
        "3104daac6d40ca98dbc9a108ead10bd8ea8669b7",
        "sha256:d88fecf51ffba2fca79c84a9ee1ba9111ff4f8b3506617044f94291163375177",
        3696,
    ),
    (
        "72c81871cf2c7049ce30d730fd0e5d81d9f492aa",
        "sha256:fbafabbe8347a4697f0fe8e9de1eb9a346a374f8bea6ea0e2ba0a71f12fa5fa1",
        3686,
    ),
    (
        "18e1c1fef244ceccc6a2951bcbae7fb117bc2dc8",
        "sha256:ea1bc9a8fa994ffb49494f27f06104efe9a9639c720f1e0e3120e1d79a86516b",
        3693,
    ),
];
const PRIOR_TEMPLATE: &str = "33ba029e7c13c95b8dc9f998acb77a2a8db476fd";
const PRIOR_RENDER_SHA: &str =
    "sha256:1fa52ca37d29d8707ad218cda72bf0036250de2d72645d990f4d350b824157e7";

#[derive(Facet)]
struct Ledger {
    schema: String,
    context_commits: BTreeMap<String, String>,
    normalization: Normalization,
    owners: Vec<Owner>,
    prerequisite_definitions: BTreeMap<String, Definition>,
    files: Vec<AuthoredFile>,
    raw_variants: Vec<RawVariant>,
    full_source_profiles: Vec<Profile>,
    registration_members: Vec<Member>,
}
#[derive(Facet)]
struct Normalization {
    policy: String,
    raw_byte_exact: bool,
    prior_template_git_blob: String,
    prior_template_full_on_output: OutputFacts,
}
#[derive(Facet)]
struct OutputFacts {
    bytes: u64,
    sha256: String,
}
#[derive(Facet)]
struct Owner {
    name: String,
    support: Vec<String>,
    requires: Vec<String>,
}
#[derive(Facet)]
struct Definition {
    supported_targets: Vec<String>,
    requires: Vec<String>,
}
#[derive(Facet)]
struct AuthoredFile {
    intended_core_path: String,
    stage_bytes: u64,
    stage_sha256: String,
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
}
#[derive(Facet)]
struct Witness {
    context: String,
    commit: String,
    present: bool,
    git_blob: Option<String>,
    raw_bytes: u64,
    raw_sha256: Option<String>,
    rendered_bytes: u64,
    rendered_sha256: Option<String>,
}
#[derive(Facet)]
struct RawVariant {
    git_blob: String,
    bytes: u64,
    sha256: String,
    crlf_count: usize,
    lone_cr_count: usize,
    final_lf: bool,
    bom: bool,
}
#[derive(Facet)]
struct Profile {
    target: String,
    enabled: Vec<String>,
    raw_oid: String,
    prior_template: bool,
}
#[derive(Facet)]
struct Member {
    field: String,
    class: String,
    owner: String,
    available_contexts: Vec<String>,
    command_ids: Vec<String>,
    conditional_companion: Option<String>,
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
            "reviewed registrar ledger changed"
        );
        let ledger: Ledger = facet_json::from_str(std::str::from_utf8(&bytes)?)
            .wrap_err("cannot parse bounded Echo/registrar evidence")?;
        let sources = PATHS
            .iter()
            .map(|path| shared.read_source(path))
            .collect::<Result<Vec<_>>>()?;
        let raw = read_git_blobs(
            &shared.repository,
            &RAW_FACTS.iter().map(|fact| fact.0.to_owned()).collect(),
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
        let contexts = CONTEXTS
            .into_iter()
            .map(|(name, oid)| (name.to_owned(), oid.to_owned()))
            .collect::<BTreeMap<_, _>>();
        ensure!(
            self.ledger.schema == "sfm:core-echo-palette-slice@1"
                && self.ledger.context_commits == contexts,
            "frozen Echo/registrar context changed"
        );
        let policy = &self.ledger.normalization;
        ensure!(
            policy.policy == "none"
                && policy.raw_byte_exact
                && policy.prior_template_git_blob == PRIOR_TEMPLATE
                && policy.prior_template_full_on_output.bytes == 25304
                && policy.prior_template_full_on_output.sha256 == PRIOR_RENDER_SHA,
            "no byte normalization or expanded prior-template claim is authorized"
        );
        ensure!(
            self.ledger.files.len() == 2
                && self.ledger.owners.len() == 29
                && self.ledger.raw_variants.len() == 6
                && self.ledger.full_source_profiles.len() == 10
                && self.ledger.registration_members.len() == 104,
            "bounded registrar scope changed"
        );
        for (name, definition) in &self.ledger.prerequisite_definitions {
            let actual = self.shared.features.0.get(name).ok_or_else(|| {
                eyre::eyre!("source contract owner/prerequisite not registered: {name}")
            })?;
            let expected_support = if name == "screen_diagnostics" {
                ensure!(
                    definition.supported_targets == ["1.19.2", "1.19.4"]
                        && definition.requires.is_empty(),
                    "immutable diagnostics source support evidence changed"
                );
                TARGETS
                    .iter()
                    .map(|target| (*target).to_owned())
                    .collect::<Vec<_>>()
            } else {
                definition.supported_targets.clone()
            };
            ensure!(
                actual.supported_targets == expected_support
                    && actual.requires == definition.requires,
                "source owner dependency/support changed: {name}"
            );
        }
        let notifications = self
            .shared
            .features
            .0
            .get("workspace_notifications")
            .ok_or_else(|| eyre::eyre!("reviewed current notification owner is not registered"))?;
        ensure!(
            notifications.supported_targets == ["1.19.2", "1.19.4"]
                && notifications.requires == ["workspace_panels", "font_formatted_text"],
            "current notification contract changed"
        );
        let font = self
            .shared
            .features
            .0
            .get("font_formatted_text")
            .ok_or_else(|| eyre::eyre!("reviewed formatted-text owner is not registered"))?;
        ensure!(
            font.supported_targets == TARGETS && font.requires.is_empty(),
            "current formatted-text contract changed"
        );
        let mut names = BTreeSet::new();
        for owner in &self.ledger.owners {
            ensure!(
                names.insert(owner.name.as_str()),
                "duplicate reviewed owner"
            );
            let definition = self
                .ledger
                .prerequisite_definitions
                .get(&owner.name)
                .ok_or_else(|| eyre::eyre!("owner definition absent"))?;
            ensure!(
                owner.support == definition.supported_targets
                    && owner.requires == definition.requires,
                "owner declaration changed"
            );
        }
        for (row, fact) in self.ledger.raw_variants.iter().zip(RAW_FACTS) {
            ensure!(
                row.git_blob == fact.0
                    && row.sha256 == fact.1
                    && row.bytes == fact.2
                    && row.crlf_count == 0
                    && row.lone_cr_count == 0
                    && row.final_lf
                    && !row.bom,
                "raw registrar witness facts changed"
            );
            validate_raw(&self.raw[fact.0], fact.1, fact.2)?;
        }
        for (index, file) in self.ledger.files.iter().enumerate() {
            ensure!(
                file.intended_core_path == PATHS[index]
                    && file.stage_bytes == SOURCE_FACTS[index].0
                    && file.stage_sha256 == SOURCE_FACTS[index].1,
                "authored source identity changed"
            );
            let historical_source = self.historical_source(index)?;
            validate_raw(
                &historical_source,
                SOURCE_FACTS[index].1,
                SOURCE_FACTS[index].0,
            )?;
            let required = if index == 0 {
                "echo_action"
            } else {
                "client_actions"
            };
            ensure!(
                file.source_rule.input == PATHS[index]
                    && file.source_rule.template
                    && file.source_rule.when.targets == TARGETS
                    && file.source_rule.when.all_features == [required],
                "sparse membership contract changed"
            );
            ensure!(file.witnesses.len() == 20, "witness cells changed");
            let mut seen = BTreeSet::new();
            for row in &file.witnesses {
                let (kind, target) = row
                    .context
                    .split_once('/')
                    .ok_or_else(|| eyre::eyre!("invalid witness"))?;
                let expected = kind == "dev";
                let oid = if index == 0 {
                    RAW_FACTS[0].0
                } else {
                    registrar_oid(target)?
                };
                let fact = RAW_FACTS
                    .iter()
                    .find(|fact| fact.0 == oid)
                    .ok_or_else(|| eyre::eyre!("unknown frozen registrar object"))?;
                let (rendered_len, rendered_sha) = if oid == PRIOR_TEMPLATE {
                    (25304, PRIOR_RENDER_SHA)
                } else {
                    (fact.2, fact.1)
                };
                ensure!(
                    seen.insert(row.context.as_str())
                        && contexts.get(&row.context) == Some(&row.commit)
                        && row.present == expected
                        && row.git_blob.as_deref() == expected.then_some(oid)
                        && row.raw_sha256.as_deref() == expected.then_some(fact.1)
                        && row.raw_bytes == if expected { fact.2 } else { 0 }
                        && row.rendered_bytes == if expected { rendered_len } else { 0 }
                        && row.rendered_sha256.as_deref() == expected.then_some(rendered_sha),
                    "raw versus rendered registrar evidence changed"
                );
            }
            ensure!(
                seen == contexts.keys().map(String::as_str).collect(),
                "frozen membership coverage changed"
            );
        }
        let mut fields = BTreeSet::new();
        for member in &self.ledger.registration_members {
            ensure!(
                fields.insert(member.field.as_str()) && names.contains(member.owner.as_str()),
                "duplicate/unowned registration field"
            );
            ensure!(
                !member.class.is_empty() && !member.command_ids.is_empty(),
                "member evidence lacks exact class/command"
            );
            let support = &self.ledger.prerequisite_definitions[&member.owner].supported_targets;
            for name in &member.available_contexts {
                let (kind, target) = name
                    .split_once('/')
                    .ok_or_else(|| eyre::eyre!("invalid member context"))?;
                ensure!(
                    kind == "dev" && support.iter().any(|known| known == target),
                    "owner enables unwitnessed member target"
                );
            }
            if let Some(companion) = &member.conditional_companion {
                ensure!(
                    companion == "terminal_presentation_actions"
                        && matches!(
                            member.field.as_str(),
                            "CONNECT_RUST_SERVER" | "START_RUST_SERVER"
                        ),
                    "unexpected vocabulary owner"
                );
            }
        }
        Ok(())
    }
    fn selected(&self, context: &ProjectionContext) -> Result<BTreeSet<usize>> {
        let selection = select_core_inputs(&self.shared.metadata, context, &inventory())?;
        let mut members = BTreeSet::new();
        for (index, path) in PATHS.iter().enumerate() {
            let required = if index == 0 {
                "echo_action"
            } else {
                "client_actions"
            };
            let expected = enabled(context, required);
            if let Some(input) = selection.inputs.get(*path) {
                ensure!(
                    expected && input.input == *path && !selection.omitted_paths.contains(*path),
                    "false owner or alternate source escaped sparse membership: {path}"
                );
                members.insert(index);
            } else {
                ensure!(
                    !expected && selection.omitted_paths.contains(*path),
                    "false owner must explicitly omit class: {path}"
                );
            }
        }
        Ok(members)
    }
    fn render(&self, index: usize, context: &ProjectionContext) -> Result<String> {
        ensure!(
            self.selected(context)?.contains(&index),
            "refuse to render omitted class"
        );
        render_java_source(std::str::from_utf8(&self.sources[index])?, context)
    }
    fn historical_source(&self, index: usize) -> Result<Vec<u8>> {
        if index == 0 {
            return Ok(self.sources[0].clone());
        }
        // Bind current bytes before restoring only the reviewed Workspace guards.
        let previous = pre_workspace_palette_source(&self.sources[1])?;
        validate_raw(
            &previous,
            CURRENT_REGISTRAR_FACTS.1,
            CURRENT_REGISTRAR_FACTS.0,
        )?;
        let mut source = std::str::from_utf8(&previous)?.to_owned();
        for (_, before, after) in REGISTRAR_V3_EDITS.iter().rev() {
            ensure!(
                source.matches(after).count() == 1,
                "current-v3 nested registrar reverse anchor is not unique"
            );
            source = source.replacen(after, before, 1);
        }
        validate_raw(
            source.as_bytes(),
            V2_REGISTRAR_FACTS.1,
            V2_REGISTRAR_FACTS.0,
        )?;
        for (_, before, after) in REGISTRAR_EDITS.iter().rev() {
            ensure!(
                source.matches(after).count() == 1,
                "current-v2 registrar reverse anchor is not unique"
            );
            source = source.replacen(after, before, 1);
        }
        let bytes = source.into_bytes();
        validate_raw(&bytes, SOURCE_FACTS[1].1, SOURCE_FACTS[1].0)?;
        Ok(bytes)
    }
    fn historical_render(&self, index: usize, context: &ProjectionContext) -> Result<String> {
        ensure!(
            self.selected(context)?.contains(&index),
            "historical class is omitted"
        );
        let source = self.historical_source(index)?;
        render_java_source(std::str::from_utf8(&source)?, context)
    }
    fn full_context(&self, target: &str) -> Result<ProjectionContext> {
        let profile = self
            .ledger
            .full_source_profiles
            .iter()
            .find(|row| row.target == target)
            .ok_or_else(|| eyre::eyre!("full explicit profile absent"))?;
        ensure!(
            profile.raw_oid == registrar_oid(target)?
                && profile.prior_template == (target == "1.19.2"),
            "frozen full-on profile changed"
        );
        // Validate the exact original manifest before adding the explicit current
        // notification/content-rendering companions for the five refined fields.
        let historical = self.shared.context(
            target,
            &profile
                .enabled
                .iter()
                .map(String::as_str)
                .collect::<Vec<_>>(),
        )?;
        if !matches!(target, "1.19.2" | "1.19.4") {
            return Ok(historical);
        }
        let historical_names = profile.enabled.iter().cloned().collect::<BTreeSet<_>>();
        ensure!(
            !historical_names.contains("workspace_notifications")
                && !historical_names.contains("font_formatted_text"),
            "immutable Echo full profile unexpectedly contains current companions"
        );
        let mut current_names = historical_names.clone();
        current_names.extend([
            "workspace_notifications".to_owned(),
            "font_formatted_text".to_owned(),
        ]);
        let current = self.shared.context(
            target,
            &current_names.iter().map(String::as_str).collect::<Vec<_>>(),
        )?;
        ensure!(
            current_names
                .difference(&historical_names)
                .map(String::as_str)
                .collect::<BTreeSet<_>>()
                == BTreeSet::from(["workspace_notifications", "font_formatted_text"]),
            "current full profile widened beyond the two reviewed companions"
        );
        for name in historical_names {
            ensure!(
                enabled(&historical, &name) && enabled(&current, &name),
                "immutable Echo full profile was changed"
            );
        }
        Ok(current)
    }
    fn current_workspace_full_context(&self, target: &str) -> Result<ProjectionContext> {
        // Keep full_context's immutable manifest and original two-companion
        // assertion intact. Add only the separately reviewed current companions.
        let original = self.full_context(target)?;
        if !matches!(target, "1.19.2" | "1.19.4") {
            // Current legacy review registration requires its actual providers.
            // Keep the frozen source profile unchanged and enable these companions
            // only for the current full-source reconstruction.
            let mut current_names = original
                .features
                .iter()
                .filter(|(_, value)| **value)
                .map(|(name, _)| name.clone())
                .collect::<BTreeSet<_>>();
            ensure!(
                current_names.contains("repository_review_bundle_actions")
                    && current_names.contains("workspace_panels"),
                "legacy full profile lacks the historical review consumer"
            );
            current_names.extend([
                "legacy_repository_review".to_owned(),
                "client_theme".to_owned(),
                "legacy_file_explorer".to_owned(),
            ]);
            return self.shared.context(
                target,
                &current_names.iter().map(String::as_str).collect::<Vec<_>>(),
            );
        }
        let original_names = original
            .features
            .iter()
            .filter(|(_, value)| **value)
            .map(|(name, _)| name.clone())
            .collect::<BTreeSet<_>>();
        let additions = BTreeSet::from([
            "workspace_stack_controls".to_owned(),
            "workspace_panel_metadata".to_owned(),
            "workspace_panel_reopening".to_owned(),
            "workspace_directional_resize".to_owned(),
            "workspace_dividers".to_owned(),
        ]);
        ensure!(
            additions.is_disjoint(&original_names),
            "immutable Echo full profile unexpectedly includes Workspace companions"
        );
        let current_names = original_names
            .union(&additions)
            .cloned()
            .collect::<BTreeSet<_>>();
        // CoreTestFixture validates current definitions and prerequisites; it
        // does not silently close or widen the historical owner's manifest.
        let current = self.shared.context(
            target,
            &current_names.iter().map(String::as_str).collect::<Vec<_>>(),
        )?;
        let actual_names = current
            .features
            .iter()
            .filter(|(_, value)| **value)
            .map(|(name, _)| name.clone())
            .collect::<BTreeSet<_>>();
        ensure!(
            actual_names == current_names
                && actual_names
                    .difference(&original_names)
                    .cloned()
                    .collect::<BTreeSet<_>>()
                    == additions,
            "current Workspace full profile widened beyond five reviewed additions"
        );
        Ok(current)
    }
    fn context_for_owners(&self, target: &str, owners: &[&str]) -> Result<ProjectionContext> {
        // This test-only closure uses the reviewed portable manifest, not implicit
        // production helper expansion. The resulting exact set is passed to the
        // ordinary CoreTestFixture validation boundary.
        let mut requested = owners
            .iter()
            .map(|name| (*name).to_owned())
            .collect::<BTreeSet<_>>();
        loop {
            let mut changed = false;
            for name in requested.clone() {
                let definition = self
                    .ledger
                    .prerequisite_definitions
                    .get(&name)
                    .ok_or_else(|| eyre::eyre!("unreviewed test prerequisite: {name}"))?;
                ensure!(
                    definition
                        .supported_targets
                        .iter()
                        .any(|known| known == target),
                    "unsupported source owner: {name}/{target}"
                );
                for dependency in &definition.requires {
                    changed |= requested.insert(dependency.clone());
                }
            }
            if !changed {
                break;
            }
            ensure!(requested.len() <= 64, "bounded owner closure exceeded");
        }
        self.shared.context(
            target,
            &requested.iter().map(String::as_str).collect::<Vec<_>>(),
        )
    }
    fn expected_registrar(&self, target: &str, context: &ProjectionContext) -> Result<String> {
        let oid = registrar_oid(target)?;
        let bytes = &self.raw[oid];
        if oid == PRIOR_TEMPLATE {
            render_java_source(std::str::from_utf8(bytes)?, context)
        } else {
            Ok(std::str::from_utf8(bytes)?.to_owned())
        }
    }
    fn validate_members(&self, target: &str, context: &ProjectionContext) -> Result<()> {
        let historical = self.historical_render(1, context)?;
        let historical_fields = field_names(&historical)?;
        let historical_expected = self
            .ledger
            .registration_members
            .iter()
            .filter(|member| enabled(context, &member.owner))
            .map(|member| member.field.clone())
            .collect::<Vec<_>>();
        ensure!(
            historical_fields == historical_expected,
            "immutable-v1 registration ownership/order changed for {target}"
        );
        let actual = self.render(1, context)?;
        let fields = field_names(&actual)?;
        let expected = historical_expected
            .into_iter()
            .filter(|field| {
                !NOTIFICATION_MEMBERS.contains(&field.as_str())
                    || enabled(context, "workspace_notifications")
            })
            .filter(|field| current_workspace_palette_member_enabled(field, context))
            .collect::<Vec<_>>();
        ensure!(
            fields == expected,
            "current-v2 member boundary or stable order changed for {target}"
        );
        ensure!(
            actual.contains("import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceSide;")
                == enabled(context, "workspace_panel_actions"),
            "workspace import leaked"
        );
        ensure!(
            actual.contains("import ca.teamdman.sfm.client.terminal.SFMTerminalTuningOperation;")
                == enabled(context, "terminal_tuning_actions"),
            "terminal tuning import leaked"
        );
        let forge = matches!(target, "1.19.2" | "1.19.4" | "1.20" | "1.20.1");
        ensure!(
            actual.contains("import net.minecraftforge.eventbus.api.IEventBus;") == forge
                && actual.contains("import net.neoforged.bus.api.IEventBus;") != forge,
            "historical loader import changed"
        );
        ensure!(
            !actual.contains("{%") && !actual.contains("case environment"),
            "controlled source fragments remain in ordinary Java"
        );
        Ok(())
    }
}
fn enabled(context: &ProjectionContext, name: &str) -> bool {
    context.features.get(name).copied().unwrap_or(false)
}
fn inventory() -> BTreeSet<String> {
    PATHS.into_iter().map(str::to_owned).collect()
}
fn registrar_oid(target: &str) -> Result<&'static str> {
    Ok(match target {
        "1.19.2" => RAW_FACTS[1].0,
        "1.19.4" => RAW_FACTS[2].0,
        "1.20" | "1.20.1" => RAW_FACTS[3].0,
        "1.20.2" | "1.20.3" | "1.20.4" | "1.21.0" | "1.21.1" => RAW_FACTS[4].0,
        "26.1.2" => RAW_FACTS[5].0,
        _ => eyre::bail!("unreviewed registrar target: {target}"),
    })
}
fn validate_raw(bytes: &[u8], digest: &str, count: u64) -> Result<()> {
    ensure!(
        bytes.len() as u64 == count
            && sha256(bytes) == digest
            && bytes.ends_with(b"\n")
            && !bytes.contains(&b'\r')
            && !bytes.starts_with(&[0xef, 0xbb, 0xbf]),
        "exact LF source identity changed"
    );
    Ok(())
}
fn field_names(body: &str) -> Result<Vec<String>> {
    let anchor = "    public static final SFMRegistryObject<SFMClientAction<?>,";
    body.match_indices(anchor)
        .map(|(start, _)| {
            let suffix = &body[start..];
            let (declaration, assignment) = suffix
                .split_once('=')
                .ok_or_else(|| eyre::eyre!("registration assignment absent"))?;
            ensure!(
                assignment.trim_start().starts_with("REGISTERER.register("),
                "registration assignment is not a contributor call"
            );
            let name = declaration
                .split_whitespace()
                .last()
                .ok_or_else(|| eyre::eyre!("registration field name absent"))?;
            ensure!(
                name.bytes()
                    .all(|byte| byte.is_ascii_uppercase() || byte == b'_'),
                "invalid registration field"
            );
            Ok(name.to_owned())
        })
        .collect()
}

#[test]
fn forty_frozen_memberships_reconstruct_twenty_raw_or_prior_template_bodies() -> Result<()> {
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
        for index in 0..2 {
            assert_eq!(
                selected.contains(&index),
                kind == "dev",
                "{name} {}",
                PATHS[index]
            );
            if kind == "dev" {
                let historical = fixture.historical_render(index, &context)?;
                let current_context = fixture.current_workspace_full_context(target)?;
                let output = fixture.render(index, &current_context)?;
                let expected = if index == 0 {
                    std::str::from_utf8(&fixture.raw[RAW_FACTS[0].0])?.to_owned()
                } else {
                    fixture.expected_registrar(target, &context)?
                };
                assert_eq!(historical, expected, "historical-v1 {name}");
                assert_eq!(output, expected, "current-v2 full-on {name}");
                let row = fixture.ledger.files[index]
                    .witnesses
                    .iter()
                    .find(|row| row.context == name)
                    .ok_or_else(|| eyre::eyre!("witness row absent"))?;
                assert_eq!(
                    sha256(historical.as_bytes()),
                    row.rendered_sha256
                        .clone()
                        .ok_or_else(|| eyre::eyre!("rendered digest absent"))?
                );
                present += 1;
            } else {
                absent += 1;
            }
        }
    }
    assert_eq!((present, absent), (20, 20));
    Ok(())
}

#[test]
fn basic_echo_is_independent_on_all_ten_targets_and_basic_action_shell_has_no_optional_fields()
-> Result<()> {
    let fixture = Fixture::load()?;
    for target in TARGETS {
        let context = fixture
            .shared
            .context(target, &["client_actions", "echo_action"])?;
        assert_eq!(fixture.selected(&context)?, BTreeSet::from([0, 1]));
        assert_eq!(
            fixture.render(0, &context)?.as_bytes(),
            fixture.raw[RAW_FACTS[0].0]
        );
        assert_eq!(field_names(&fixture.render(1, &context)?)?, ["ECHO"]);
        assert_eq!(
            context
                .features
                .iter()
                .filter_map(|(name, on)| on.then_some(name.as_str()))
                .collect::<BTreeSet<_>>(),
            BTreeSet::from(["client_actions", "echo_action"])
        );
        for forbidden in [
            "command_palette",
            "typed_command_palette",
            "terminal_remote",
            "workspace_panels",
            "client_program_actions",
            "editor_documents",
            "editor_overlay_push",
            "client_theme",
            "packet_values",
        ] {
            assert!(!enabled(&context, forbidden), "{target} {forbidden}");
        }
        let shell = fixture.shared.context(target, &["client_actions"])?;
        assert_eq!(fixture.selected(&shell)?, BTreeSet::from([1]));
        assert!(field_names(&fixture.render(1, &shell)?)?.is_empty());
        assert!(fixture.shared.context(target, &["echo_action"]).is_err());
        assert!(
            fixture
                .selected(&fixture.shared.context(target, &[])?)?
                .is_empty()
        );
    }
    let echo = std::str::from_utf8(&fixture.sources[0])?;
    for anchor in [
        "StringArgumentType.greedyString()",
        "SFMClientActionAvailability::available",
        ".withStyle(ChatFormatting.AQUA)",
        ".append(Component.literal(message))",
        "gui.sfm.client_action.echo.title",
        "gui.sfm.client_action.echo.description",
    ] {
        assert!(echo.contains(anchor), "{anchor}");
    }
    let localization = fixture
        .shared
        .read_source("src/main/java/ca/teamdman/sfm/common/localization/LocalizationEntry.java")?;
    assert!(std::str::from_utf8(&localization)?.contains("public MutableComponent getComponent()"));
    Ok(())
}

#[test]
fn all_supported_independent_owner_profiles_preserve_registration_order_and_import_boundaries()
-> Result<()> {
    let fixture = Fixture::load()?;
    let mut profiles = 0;
    for target in TARGETS {
        for owner in &fixture.ledger.owners {
            if !owner.support.iter().any(|known| known == target) {
                continue;
            }
            let context = fixture.context_for_owners(target, &["client_actions", &owner.name])?;
            fixture.validate_members(target, &context)?;
            profiles += 1;
        }
        fixture.validate_members(target, &fixture.full_context(target)?)?;
    }
    assert_eq!(profiles, 145);
    for target in &TARGETS[..2] {
        for mask in 0..8 {
            let mut owners = vec!["client_actions"];
            if mask & 1 != 0 {
                owners.push("echo_action");
            }
            if mask & 2 != 0 {
                owners.push("typed_command_palette");
            }
            if mask & 4 != 0 {
                owners.push("command_history");
            }
            let context = fixture.context_for_owners(target, &owners)?;
            fixture.validate_members(target, &context)?;
            assert_eq!(fixture.selected(&context)?.contains(&0), mask & 1 != 0);
        }
    }
    for owner in &fixture.ledger.owners {
        for target in TARGETS {
            if !owner.support.iter().any(|known| known == target) {
                assert!(
                    fixture.context_for_owners(target, &[&owner.name]).is_err(),
                    "{} {target}",
                    owner.name
                );
            }
        }
    }
    Ok(())
}

#[test]
fn old_template_echo_toggle_and_terminal_vocabulary_extensions_are_explicit_not_mc_surrogates()
-> Result<()> {
    let fixture = Fixture::load()?;
    let mut full = fixture.full_context("1.19.2")?;
    let mut current_full = fixture.current_workspace_full_context("1.19.2")?;
    let prior = std::str::from_utf8(&fixture.raw[PRIOR_TEMPLATE])?;
    assert_eq!(prior.matches("{% if features.echo_action %}").count(), 1);
    assert_eq!(prior.matches("{% endif %}").count(), 1);
    for echo in [false, true] {
        full.features.insert("echo_action".to_owned(), echo);
        current_full.features.insert("echo_action".to_owned(), echo);
        let expected = render_java_source(prior, &full)?;
        assert_eq!(fixture.render(1, &current_full)?, expected);
        assert_eq!(
            field_names(&expected)?.iter().any(|field| field == "ECHO"),
            echo
        );
    }
    for target in &TARGETS[..2] {
        let remote = fixture.context_for_owners(target, &["client_actions", "terminal_remote"])?;
        let old = fixture.render(1, &remote)?;
        assert!(
            old.contains("\"terminal/connect-rust-server\"")
                && old.contains("\"terminal/start-rust-server\"")
        );
        assert!(
            !old.contains("\"terminal/server/connect\"") && !old.contains("SET_TERMINAL_TRANSPORT")
        );
        let expanded = fixture.context_for_owners(target, &["terminal_presentation_actions"])?;
        let current = fixture.render(1, &expanded)?;
        assert!(
            current.contains("\"terminal/server/connect\"")
                && current.contains("\"terminal/server/start\"")
        );
        assert!(!current.contains("\"terminal/connect-rust-server\""));
        assert!(current.contains("SET_TERMINAL_TRANSPORT"));
    }
    for target in &TARGETS[2..] {
        let legacy = fixture.context_for_owners(target, &["workspace_legacy_open_action"])?;
        let current = fixture.render(1, &legacy)?;
        assert_eq!(
            current.contains("() -> new OpenScreenToSideAction()"),
            *target == "26.1.2"
        );
        assert_eq!(
            current.contains("OpenScreenToSideAction::new"),
            *target != "26.1.2"
        );
        assert!(!current.contains("OpenPanelAction"));
    }
    Ok(())
}

#[test]
fn six_raw_witnesses_and_authored_templates_reject_unauthorized_eol_bom_and_token_changes()
-> Result<()> {
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
    for (index, (count, digest)) in SOURCE_FACTS.iter().enumerate() {
        let mut token = fixture.historical_source(index)?;
        validate_raw(&token, digest, *count)?;
        token[0] ^= 1;
        assert!(validate_raw(&token, digest, *count).is_err());
    }
    let context = fixture.full_context("1.19.2")?;
    let source = std::str::from_utf8(&fixture.sources[1])?;
    let mismatched = source.replacen("{% endif %}", "{% endcase %}", 1);
    assert!(render_java_source(&mismatched, &context).is_err());
    let unowned = source.replacen(
        "features.echo_action",
        "features.unregistered_echo_owner",
        1,
    );
    assert!(render_java_source(&unowned, &context).is_err());
    Ok(())
}

#[test]
fn twenty_common_edits_use_fixed_core_root_without_changing_owner_guards_or_actual_inputs()
-> Result<()> {
    let fixture = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let marker = "    // Common Echo/registrar edit proof.\n";
    let anchors = [
        "public final class EchoAction implements SFMClientAction<SFMClientActionContext> {\n",
        "public final class SFMCommandPaletteActions {\n",
    ];
    for index in 0..2 {
        let source = std::str::from_utf8(&fixture.sources[index])?;
        ensure!(
            source.matches(anchors[index]).count() == 1,
            "shared class anchor changed"
        );
        let replacement = format!("{}{marker}", anchors[index]);
        let edited = source.replacen(anchors[index], &replacement, 1);
        assert_eq!(
            edited
                .lines()
                .filter(|line| line.starts_with("{%"))
                .collect::<Vec<_>>(),
            source
                .lines()
                .filter(|line| line.starts_with("{%"))
                .collect::<Vec<_>>()
        );
        let path = temp.path().join(CORE_ROOT).join(PATHS[index]);
        fs::create_dir_all(
            path.parent()
                .ok_or_else(|| eyre::eyre!("fixture parent absent"))?,
        )?;
        fs::write(&path, edited)?;
    }
    let mut outputs = 0;
    for target in TARGETS {
        let context = fixture.current_workspace_full_context(target)?;
        let selection = select_core_inputs(&fixture.shared.metadata, &context, &inventory())?;
        for index in 0..2 {
            let selected = selection
                .inputs
                .get(PATHS[index])
                .ok_or_else(|| eyre::eyre!("selected authored input absent"))?;
            assert_eq!(selected.input, PATHS[index]);
            let path = temp.path().join(CORE_ROOT).join(&selected.input);
            let bytes = read_bounded(&path, LIMIT)?;
            let output = render_java_source(std::str::from_utf8(&bytes)?, &context)?;
            let replacement = format!("{}{marker}", anchors[index]);
            let expected =
                fixture
                    .render(index, &context)?
                    .replacen(anchors[index], &replacement, 1);
            assert_eq!(output, expected, "{target}");
            assert_eq!(
                fixture.shared.read_source(PATHS[index])?,
                fixture.sources[index]
            );
            outputs += 1;
        }
    }
    assert_eq!(outputs, 20);
    Ok(())
}

#[test]
fn isolated_collection_enforces_fixed_core_and_automatic_java_without_full_runtime_claim()
-> Result<()> {
    let fixture = Fixture::load()?;
    let context = fixture
        .shared
        .context("1.19.2", &["client_actions", "echo_action"])?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    let mut metadata = fixture.shared.metadata.clone();
    metadata
        .source_rules
        .retain(|path, _| PATHS.contains(&path.as_str()));
    let selection = select_core_inputs(&metadata, &context, &inventory())?;
    for (output, selected) in &selection.inputs {
        let bytes = if let Some(index) = PATHS.iter().position(|path| *path == output) {
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
    // Force the optional template hints off; .java must still be rendered.
    for variants in metadata.source_rules.values_mut() {
        for variant in variants {
            variant.template = false;
        }
    }
    let selected = select_core_inputs(&metadata, &context, &inventory())?;
    let artifacts = collect_core_artifacts(&root, &selected, &context)?;
    for index in 0..2 {
        assert!(!selected.inputs[PATHS[index]].template);
        let artifact = &artifacts[PATHS[index]];
        assert_eq!(artifact.source_bytes, fixture.sources[index]);
        assert_eq!(
            artifact.source_path,
            format!("{CORE_ROOT}/{}", PATHS[index])
        );
        assert!(artifact.overlay.is_none());
        let output = std::str::from_utf8(&artifact.output_bytes)?;
        let (_, body) = output
            .split_once('\n')
            .ok_or_else(|| eyre::eyre!("banner absent"))?;
        assert_eq!(body, fixture.render(index, &context)?);
        assert!(!body.contains("{%"));
    }
    assert!(
        collect_core_artifacts(&temp.path().join("alternate-core"), &selected, &context).is_err()
    );
    let source = std::str::from_utf8(&fixture.sources[1])?;
    let forbidden =
        format!("{{% case environment %}}\n{{% when \"release\" %}}\n{source}{{% endcase %}}\n");
    fs::write(root.join(PATHS[1]), forbidden)?;
    assert!(collect_core_artifacts(&root, &selected, &context).is_err());
    for (index, path) in PATHS.iter().enumerate() {
        assert_eq!(fixture.shared.read_source(path)?, fixture.sources[index]);
    }
    Ok(())
}

#[test]
fn forty_bounded_offline_git_cells_preserve_frozen_blobs_and_release_absence() -> Result<()> {
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
            .ok_or_else(|| eyre::eyre!("tree output absent"))?;
        let mut bytes = Vec::new();
        let result = reader.by_ref().take(4097).read_to_end(&mut bytes);
        drop(reader);
        if result.is_err() || bytes.len() > 4096 {
            let _ = child.kill();
            let _ = child.wait();
            result?;
            eyre::bail!("bounded frozen tree output exceeded");
        }
        ensure!(child.wait()?.success(), "offline tree read failed");
        let expected = if kind == "dev" {
            present += 2;
            format!(
                "100644 blob {}\tplatform/minecraft/{}\0\
                    100644 blob {}\tplatform/minecraft/{}\0",
                RAW_FACTS[0].0,
                PATHS[0],
                registrar_oid(target)?,
                PATHS[1]
            )
        } else {
            String::new()
        };
        assert_eq!(bytes, expected.as_bytes(), "{name}");
    }
    assert_eq!(present, 20);
    Ok(())
}
