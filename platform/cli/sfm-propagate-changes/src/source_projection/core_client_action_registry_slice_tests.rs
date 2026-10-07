//! Frozen source-only client-action registry consolidation proofs.
//!
//! Register only after promoting the reviewed registry and sparse membership.
//! Git blobs are bounded offline golden evidence, never production input routes.
//! Basic Echo and command dispatch do not require programmatic action adapters.
//! These tests prove source selection/rendering, not Java or live registration.
//! Intentional future core edits need deliberate reviewed golden updates.

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

const PATH: &str = "src/main/java/ca/teamdman/sfm/client/registry/SFMClientActions.java";
const LEDGER: &str = "docs/tasks/sfm-core-client-action-registry-slice.json";
const LEDGER_SHA: &str = "sha256:a23fb7fb5f1c5283a4922efc0acccf9cef8e59f60647d94a23b2dbb9df9a81f2";
const SOURCE_SHA: &str = "sha256:28e99bfe0fa20dcf7e646b4002ea6adab2cffcacf3b1f2e2046864048f15814c";
const SOURCE_BYTES: u64 = 4852;
const LIMIT: u64 = 128 * 1024;
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
const RAW_FACTS: [(&str, &str, u64); 4] = [
    (
        "2117aa4881b27b2e27af72c62c50dc52b2a3dece",
        "sha256:e35ae0c5b6dfc7c3f9f4b499aa5d238af30e4c8394e27645dd030f9195591bb4",
        3826,
    ),
    (
        "d59f070e5fdda502838e9baaddcfcf21f6a22b74",
        "sha256:7e509b9ebec541c11fef9b1c675505cab22fd2bc04201f2ca2e10aedcf903593",
        2665,
    ),
    (
        "df32543d0d16f8fd0fc6184e28be871e4cd000b5",
        "sha256:feadbaccff8720c007dbb53371be7196a1788006813fcf3813e6c99e2b0d0e63",
        2655,
    ),
    (
        "35639778b60ed3e66f9d2a199fc7e8b8c2aac35f",
        "sha256:e133323fad55d3b3e2fcb14e2b6779a13f068592cd37988befb97fd94e16d87c",
        2786,
    ),
];

#[derive(Facet)]
struct Ledger {
    schema: String,
    scope: Scope,
    context_commits: BTreeMap<String, String>,
    normalization: Normalization,
    owners: Vec<Owner>,
    prerequisite_definitions: BTreeMap<String, Definition>,
    file: AuthoredFile,
    raw_variants: Vec<RawVariant>,
    full_source_profiles: Vec<Profile>,
}
#[derive(Facet)]
struct Scope {
    production_files: usize,
    primitive_companions: usize,
    contexts: usize,
    present_cells: usize,
    absent_cells: usize,
    raw_variants: usize,
    java_compilation: bool,
    live_registration: bool,
    dependency_changes: bool,
}
#[derive(Facet)]
struct Normalization {
    policy: String,
    raw_byte_exact: bool,
    authorized_transformations: Vec<String>,
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
}
struct Fixture {
    shared: CoreTestFixture,
    ledger: Ledger,
    source: Vec<u8>,
    raw: BTreeMap<String, Vec<u8>>,
}
impl Fixture {
    fn load() -> Result<Self> {
        let shared = CoreTestFixture::load()?;
        let bytes = read_bounded(&shared.repository.join(LEDGER), LIMIT)?;
        ensure!(
            sha256(&bytes) == LEDGER_SHA,
            "reviewed registry ledger changed"
        );
        let ledger: Ledger = facet_json::from_str(std::str::from_utf8(&bytes)?)
            .wrap_err("cannot parse bounded registry evidence")?;
        let source = shared.read_source(PATH)?;
        let raw = read_git_blobs(
            &shared.repository,
            &RAW_FACTS.iter().map(|fact| fact.0.to_owned()).collect(),
        )?;
        let result = Self {
            shared,
            ledger,
            source,
            raw,
        };
        result.validate()?;
        Ok(result)
    }

    fn validate(&self) -> Result<()> {
        let scope = &self.ledger.scope;
        ensure!(
            self.ledger.schema == "sfm:core-client-action-registry-slice@1"
                && scope.production_files == 1
                && scope.primitive_companions == 0
                && scope.contexts == 20
                && scope.present_cells == 10
                && scope.absent_cells == 10
                && scope.raw_variants == 4
                && !scope.java_compilation
                && !scope.live_registration
                && !scope.dependency_changes,
            "bounded source-only registry scope changed"
        );
        let contexts = CONTEXTS
            .into_iter()
            .map(|(name, oid)| (name.to_owned(), oid.to_owned()))
            .collect::<BTreeMap<_, _>>();
        ensure!(
            self.ledger.context_commits == contexts,
            "frozen registry contexts changed"
        );
        ensure!(
            self.ledger.normalization.policy == "none"
                && self.ledger.normalization.raw_byte_exact
                && self
                    .ledger
                    .normalization
                    .authorized_transformations
                    .is_empty(),
            "no registry byte normalization is authorized"
        );
        ensure!(
            self.ledger.owners.len() == 2
                && self.ledger.raw_variants.len() == 4
                && self.ledger.full_source_profiles.len() == 10,
            "registry owner or raw scope changed"
        );
        let mut owners = BTreeSet::new();
        for owner in &self.ledger.owners {
            ensure!(
                owners.insert(owner.name.as_str()),
                "duplicate registry owner"
            );
            let reviewed = self
                .ledger
                .prerequisite_definitions
                .get(&owner.name)
                .ok_or_else(|| eyre::eyre!("owner lacks reviewed definition"))?;
            ensure!(
                owner.support == reviewed.supported_targets && owner.requires == reviewed.requires,
                "registry owner definition changed"
            );
        }
        ensure!(
            owners == BTreeSet::from(["client_actions", "client_program_actions"]),
            "unknown registry owner"
        );
        for (name, reviewed) in &self.ledger.prerequisite_definitions {
            let actual =
                self.shared.features.0.get(name).ok_or_else(|| {
                    eyre::eyre!("registry fixture feature not registered: {name}")
                })?;
            ensure!(
                actual.supported_targets == reviewed.supported_targets
                    && actual.requires == reviewed.requires,
                "registry support/prerequisite contract changed: {name}"
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
                "raw registry witness changed"
            );
            validate_raw(&self.raw[fact.0], fact.1, fact.2)?;
        }
        let file = &self.ledger.file;
        ensure!(
            file.intended_core_path == PATH
                && file.stage_bytes == SOURCE_BYTES
                && file.stage_sha256 == SOURCE_SHA
                && file.source_rule.input == PATH
                && file.source_rule.template
                && file.source_rule.when.targets == TARGETS
                && file.source_rule.when.all_features == ["client_actions"]
                && file.witnesses.len() == 20,
            "registry source/rule identity changed"
        );
        validate_raw(&self.source, SOURCE_SHA, SOURCE_BYTES)?;
        let mut seen = BTreeSet::new();
        for row in &file.witnesses {
            let (kind, target) = row
                .context
                .split_once('/')
                .ok_or_else(|| eyre::eyre!("invalid registry witness"))?;
            let present = kind == "dev";
            let oid = registry_oid(target)?;
            let fact = RAW_FACTS
                .iter()
                .find(|fact| fact.0 == oid)
                .ok_or_else(|| eyre::eyre!("unknown registry object"))?;
            ensure!(
                seen.insert(row.context.as_str())
                    && contexts.get(&row.context) == Some(&row.commit)
                    && row.present == present
                    && row.git_blob.as_deref() == present.then_some(oid)
                    && row.raw_sha256.as_deref() == present.then_some(fact.1)
                    && row.raw_bytes == if present { fact.2 } else { 0 },
                "registry frozen membership changed"
            );
        }
        ensure!(
            seen == contexts.keys().map(String::as_str).collect(),
            "registry frozen coverage changed"
        );
        Ok(())
    }

    fn selected(&self, context: &ProjectionContext) -> Result<bool> {
        let selection = select_core_inputs(&self.shared.metadata, context, &inventory())?;
        let expected = enabled(context, "client_actions");
        if let Some(selected) = selection.inputs.get(PATH) {
            ensure!(
                expected && selected.input == PATH && !selection.omitted_paths.contains(PATH),
                "registry alternate source or false owner escaped selection"
            );
            Ok(true)
        } else {
            ensure!(
                !expected && selection.omitted_paths.contains(PATH),
                "registry false owner must explicitly omit source"
            );
            Ok(false)
        }
    }

    fn render(&self, context: &ProjectionContext) -> Result<String> {
        ensure!(
            self.selected(context)?,
            "refuse to render an omitted registry"
        );
        render_java_source(std::str::from_utf8(&self.source)?, context)
    }

    fn full_context(&self, target: &str) -> Result<ProjectionContext> {
        let profile = self
            .ledger
            .full_source_profiles
            .iter()
            .find(|row| row.target == target)
            .ok_or_else(|| eyre::eyre!("registry full profile absent"))?;
        ensure!(
            profile.raw_oid == registry_oid(target)?,
            "full registry raw changed"
        );
        self.shared.context(
            target,
            &profile
                .enabled
                .iter()
                .map(String::as_str)
                .collect::<Vec<_>>(),
        )
    }

    fn owner_context(&self, target: &str, requested: &[&str]) -> Result<ProjectionContext> {
        // Review-ledger closure is test-only. Pass the explicit resulting set to
        // the ordinary validation helper; production never expands requests here.
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
                    .ok_or_else(|| eyre::eyre!("unreviewed registry test feature: {name}"))?;
                ensure!(
                    definition
                        .supported_targets
                        .iter()
                        .any(|known| known == target),
                    "unsupported registry test feature: {name}/{target}"
                );
                for requirement in &definition.requires {
                    changed |= names.insert(requirement.clone());
                }
            }
            if !changed {
                return self.shared.context(
                    target,
                    &names.iter().map(String::as_str).collect::<Vec<_>>(),
                );
            }
            ensure!(names.len() <= 64, "bounded registry test closure exceeded");
        }
        eyre::bail!("registry prerequisite closure did not terminate")
    }
}
fn enabled(context: &ProjectionContext, name: &str) -> bool {
    context.features.get(name).copied().unwrap_or(false)
}
fn inventory() -> BTreeSet<String> {
    BTreeSet::from([PATH.to_owned()])
}
fn registry_oid(target: &str) -> Result<&'static str> {
    Ok(match target {
        "1.19.2" | "1.19.4" => RAW_FACTS[0].0,
        "1.20" | "1.20.1" => RAW_FACTS[1].0,
        "1.20.2" | "1.20.3" | "1.20.4" | "1.21.0" | "1.21.1" => RAW_FACTS[2].0,
        "26.1.2" => RAW_FACTS[3].0,
        _ => eyre::bail!("unreviewed registry target: {target}"),
    })
}
fn validate_raw(bytes: &[u8], digest: &str, count: u64) -> Result<()> {
    ensure!(
        bytes.len() as u64 == count
            && sha256(bytes) == digest
            && bytes.ends_with(b"\n")
            && !bytes.contains(&b'\r')
            && !bytes.starts_with(&[0xef, 0xbb, 0xbf]),
        "exact LF registry identity changed"
    );
    Ok(())
}
fn validate_members(body: &str, target: &str, programmatic: bool) {
    for member in [
        "import ca.teamdman.sfm.client.action.SFMClientActionDescriptor;",
        "import ca.teamdman.sfm.client.action.SFMClientProgramActionDispatcher;",
        "import java.util.Optional;",
        "public static Optional<SFMClientActionDescriptor> programmaticDescriptor(",
        "public static Optional<SFMClientProgramActionDispatcher.Binding> programmaticBinding(",
        "Lookup for program adapters",
    ] {
        assert_eq!(body.contains(member), programmatic, "{target} {member}");
    }
    for base in [
        "createSFMRegistryKey(\"client_action\")",
        ".onlyIf(SFMEnvironmentUtils::isClient)",
        ".createNewRegistry()",
        "public static SFMDeferredRegister<SFMClientAction<?>> createContributor(String namespace)",
        "REGISTRY_CREATOR.register(bus);",
        "public static SFMRegistryWrapper<SFMClientAction<?>> registry()",
        "public static synchronized SFMClientActionCommandTree commandTree()",
        "SFMClientActionDispatcherCompiler.compileCommandTree(registrations)",
    ] {
        assert!(body.contains(base), "{target} {base}");
    }
    let forge = matches!(target, "1.19.2" | "1.19.4" | "1.20" | "1.20.1");
    assert_eq!(
        body.contains("import net.minecraftforge.eventbus.api.IEventBus;"),
        forge
    );
    assert_eq!(
        body.contains("import net.neoforged.bus.api.IEventBus;"),
        !forge
    );
    let identifier = target == "26.1.2";
    assert_eq!(
        body.contains("import net.minecraft.resources.Identifier;"),
        identifier
    );
    assert_eq!(
        body.contains("import net.minecraft.resources.ResourceLocation;"),
        !identifier
    );
    assert_eq!(
        body.contains(".map(reference -> reference.value())"),
        identifier
    );
    assert_eq!(
        body.contains("Missing registered client action "),
        identifier
    );
    assert_eq!(
        body.contains("Objects.requireNonNull(registry().get(id))"),
        !identifier
    );
    assert!(!body.contains("{%"));
}

#[test]
fn twenty_frozen_registry_memberships_reconstruct_ten_exact_java_bodies() -> Result<()> {
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
        assert_eq!(fixture.selected(&context)?, kind == "dev", "{name}");
        if kind == "dev" {
            assert_eq!(
                fixture.render(&context)?.as_bytes(),
                fixture.raw[registry_oid(target)?],
                "{name}"
            );
            present += 1;
        } else {
            absent += 1;
        }
    }
    assert_eq!((present, absent), (10, 10));
    Ok(())
}

#[test]
fn all_ten_basic_registry_and_echo_profiles_need_no_programmatic_or_palette_graph() -> Result<()> {
    let fixture = Fixture::load()?;
    let echo = fixture
        .shared
        .read_source("src/main/java/ca/teamdman/sfm/client/action/EchoAction.java")?;
    let echo = std::str::from_utf8(&echo)?;
    assert!(echo.contains("SFMClientActionAvailability::available"));
    assert!(!echo.contains("programmaticDescriptor"));
    let mut outputs = 0;
    for target in TARGETS {
        for echo_on in [false, true] {
            let names = if echo_on {
                vec!["client_actions", "echo_action"]
            } else {
                vec!["client_actions"]
            };
            let context = fixture.shared.context(target, &names)?;
            assert!(fixture.selected(&context)?);
            let body = fixture.render(&context)?;
            validate_members(&body, target, false);
            for name in [
                "client_program_actions",
                "client_manager",
                "packet_values",
                "packet_computation",
                "client_program_consent",
                "terminal_remote",
                "workspace_panels",
                "command_palette",
                "typed_command_palette",
                "editor_documents",
                "client_theme",
            ] {
                assert!(!enabled(&context, name), "{target} {name}");
            }
            assert_eq!(
                context
                    .features
                    .values()
                    .filter(|enabled| **enabled)
                    .count(),
                if echo_on { 2 } else { 1 }
            );
            if matches!(target, "1.19.2" | "1.19.4") {
                assert_eq!(body.as_bytes(), fixture.raw[RAW_FACTS[1].0]);
            } else {
                assert_eq!(body.as_bytes(), fixture.raw[registry_oid(target)?]);
            }
            outputs += 1;
        }
        assert!(!fixture.selected(&fixture.shared.context(target, &[])?)?);
        assert!(fixture.shared.context(target, &["echo_action"]).is_err());
    }
    assert_eq!(outputs, 20);
    Ok(())
}

#[test]
fn sixteen_d2_independent_programmatic_echo_and_structured_masks_preserve_optional_members()
-> Result<()> {
    let fixture = Fixture::load()?;
    let mut outputs = 0;
    for target in &TARGETS[..2] {
        for mask in 0..8 {
            let mut owners = vec!["client_actions"];
            if mask & 1 != 0 {
                owners.push("client_program_actions");
            }
            if mask & 2 != 0 {
                owners.push("echo_action");
            }
            if mask & 4 != 0 {
                owners.push("structured_action_results");
            }
            let context = fixture.owner_context(target, &owners)?;
            let body = fixture.render(&context)?;
            validate_members(&body, target, mask & 1 != 0);
            let expected = if mask & 1 != 0 {
                RAW_FACTS[0].0
            } else {
                RAW_FACTS[1].0
            };
            assert_eq!(body.as_bytes(), fixture.raw[expected]);
            assert!(!enabled(&context, "terminal_remote"));
            assert!(!enabled(&context, "workspace_panels"));
            assert!(!enabled(&context, "command_palette"));
            outputs += 1;
        }
        assert!(
            fixture
                .shared
                .context(target, &["client_actions", "client_program_actions"])
                .is_err()
        );
    }
    for target in &TARGETS[2..] {
        assert!(
            fixture
                .owner_context(target, &["client_program_actions"])
                .is_err()
        );
    }
    assert_eq!(outputs, 16);
    Ok(())
}

#[test]
fn registry_source_primitives_are_already_core_and_real_target_lookup_adapters_remain_visible()
-> Result<()> {
    let fixture = Fixture::load()?;
    for (path, anchors) in [
        (
            "src/main/java/ca/teamdman/sfm/client/action/SFMClientAction.java",
            vec![
                "interface SFMClientAction",
                "features.client_program_actions",
            ],
        ),
        (
            "src/main/java/ca/teamdman/sfm/client/action/SFMClientActionCommandTree.java",
            vec![
                "SFMClientActionCommandTree",
                "CommandDispatcher<SFMClientActionSource>",
            ],
        ),
        (
            "src/main/java/ca/teamdman/sfm/client/action/SFMClientActionDispatcherCompiler.java",
            vec!["compileCommandTree(", "Identifier", "ResourceLocation"],
        ),
        (
            "src/main/java/ca/teamdman/sfm/common/registry/SFMDeferredRegister.java",
            vec![
                "void register(IEventBus bus)",
                "SFMRegistryWrapper<T> registry()",
            ],
        ),
        (
            "src/main/java/ca/teamdman/sfm/common/registry/SFMDeferredRegisterBuilder.java",
            vec![
                "createNewRegistry()",
                "onlyIf(BooleanSupplier condition)",
                "SFMDeferredRegister<T> build()",
            ],
        ),
        (
            "src/main/java/ca/teamdman/sfm/common/registry/SFMRegistryWrapper.java",
            vec![
                "Set<Identifier> keys()",
                "Optional<Holder.Reference<T>> get(Identifier",
                "Set<ResourceLocation> keys()",
            ],
        ),
        (
            "src/main/java/ca/teamdman/sfm/common/util/SFMResourceLocation.java",
            vec!["ResourceKey<Registry<T>> createSFMRegistryKey(String path)"],
        ),
        (
            "src/main/java/ca/teamdman/sfm/common/util/SFMEnvironmentUtils.java",
            vec!["boolean isClient()"],
        ),
    ] {
        let bytes = fixture.shared.read_source(path)?;
        let source = std::str::from_utf8(&bytes)?;
        for anchor in anchors {
            assert!(source.contains(anchor), "{path} {anchor}");
        }
    }
    for target in TARGETS {
        let context = fixture.shared.context(target, &["client_actions"])?;
        validate_members(&fixture.render(&context)?, target, false);
    }
    Ok(())
}

#[test]
fn raw_and_shared_registry_sources_refuse_eol_bom_token_and_directive_mutation() -> Result<()> {
    let fixture = Fixture::load()?;
    for (oid, digest, count) in RAW_FACTS {
        let raw = &fixture.raw[oid];
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
    let mut changed = fixture.source.clone();
    changed[0] ^= 1;
    assert!(validate_raw(&changed, SOURCE_SHA, SOURCE_BYTES).is_err());
    let context = fixture.shared.context("1.19.2", &["client_actions"])?;
    let source = std::str::from_utf8(&fixture.source)?;
    let unmatched = source.replacen("{% endif %}", "{% endcase %}", 1);
    assert!(render_java_source(&unmatched, &context).is_err());
    let unknown = source.replacen(
        "features.client_program_actions",
        "features.unknown_registry_owner",
        1,
    );
    assert!(render_java_source(&unknown, &context).is_err());
    let orphan = format!("{{% else %}}\n{source}");
    assert!(render_java_source(&orphan, &context).is_err());
    Ok(())
}

#[test]
fn twelve_registry_common_edits_reach_all_targets_and_preserve_optional_guard_lines() -> Result<()>
{
    let fixture = Fixture::load()?;
    let source = std::str::from_utf8(&fixture.source)?;
    let anchor = "public final class SFMClientActions {\n";
    let marker = "    // Shared registry authoring proof.\n";
    ensure!(
        source.matches(anchor).count() == 1,
        "common registry anchor changed"
    );
    let replacement = format!("{anchor}{marker}");
    let edited = source.replacen(anchor, &replacement, 1);
    assert_eq!(
        source
            .lines()
            .filter(|line| line.starts_with("{%"))
            .collect::<Vec<_>>(),
        edited
            .lines()
            .filter(|line| line.starts_with("{%"))
            .collect::<Vec<_>>()
    );
    let temp = tempfile::tempdir()?;
    let path = temp.path().join(CORE_ROOT).join(PATH);
    fs::create_dir_all(
        path.parent()
            .ok_or_else(|| eyre::eyre!("fixture parent absent"))?,
    )?;
    fs::write(&path, edited)?;
    let mut outputs = 0;
    for target in TARGETS {
        let mut contexts = vec![fixture.shared.context(target, &["client_actions"])?];
        if matches!(target, "1.19.2" | "1.19.4") {
            contexts.push(fixture.full_context(target)?);
        }
        for context in contexts {
            let selection = select_core_inputs(&fixture.shared.metadata, &context, &inventory())?;
            let selected = selection
                .inputs
                .get(PATH)
                .ok_or_else(|| eyre::eyre!("registry input absent"))?;
            assert_eq!(selected.input, PATH);
            let bytes = read_bounded(&temp.path().join(CORE_ROOT).join(&selected.input), LIMIT)?;
            let output = render_java_source(std::str::from_utf8(&bytes)?, &context)?;
            assert_eq!(
                output,
                fixture.render(&context)?.replacen(anchor, &replacement, 1)
            );
            assert_eq!(fixture.shared.read_source(PATH)?, fixture.source);
            outputs += 1;
        }
    }
    assert_eq!(outputs, 12);
    Ok(())
}

#[test]
fn isolated_registry_collection_keeps_fixed_core_root_automatic_java_and_no_snapshot_selector()
-> Result<()> {
    let fixture = Fixture::load()?;
    let context = fixture.shared.context("1.19.2", &["client_actions"])?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    let mut metadata = fixture.shared.metadata.clone();
    metadata.source_rules.retain(|path, _| path == PATH);
    for variants in metadata.source_rules.values_mut() {
        for variant in variants {
            variant.template = false;
        }
    }
    let selected = select_core_inputs(&metadata, &context, &inventory())?;
    for (output, selected) in &selected.inputs {
        let bytes = if output == PATH {
            fixture.source.clone()
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
    assert!(!selected.inputs[PATH].template);
    let artifact = &artifacts[PATH];
    assert_eq!(artifact.source_bytes, fixture.source);
    assert_eq!(artifact.source_path, format!("{CORE_ROOT}/{PATH}"));
    assert!(artifact.overlay.is_none());
    let output = std::str::from_utf8(&artifact.output_bytes)?;
    let (_, body) = output
        .split_once('\n')
        .ok_or_else(|| eyre::eyre!("banner absent"))?;
    assert_eq!(body, fixture.render(&context)?);
    assert!(!body.contains("{%"));
    assert!(
        collect_core_artifacts(&temp.path().join("alternate-core"), &selected, &context).is_err()
    );
    let forbidden = format!(
        "{{% case environment %}}\n{{% when \"release\" %}}\n{}{{% endcase %}}\n",
        std::str::from_utf8(&fixture.source)?
    );
    fs::write(root.join(PATH), forbidden)?;
    assert!(collect_core_artifacts(&root, &selected, &context).is_err());
    assert_eq!(fixture.shared.read_source(PATH)?, fixture.source);
    Ok(())
}

#[test]
fn twenty_bounded_offline_git_cells_prove_exact_registry_blobs_and_release_absence() -> Result<()> {
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
            ])
            .arg(format!("platform/minecraft/{PATH}"));
        let mut child = command
            .stdout(Stdio::piped())
            .stderr(Stdio::null())
            .spawn()?;
        let mut reader = child
            .stdout
            .take()
            .ok_or_else(|| eyre::eyre!("tree output absent"))?;
        let mut bytes = Vec::new();
        let result = reader.by_ref().take(2049).read_to_end(&mut bytes);
        drop(reader);
        if result.is_err() || bytes.len() > 2048 {
            let _ = child.kill();
            let _ = child.wait();
            result?;
            eyre::bail!("bounded registry tree exceeded");
        }
        ensure!(child.wait()?.success(), "offline registry tree failed");
        let expected = if kind == "dev" {
            present += 1;
            format!(
                "100644 blob {}\tplatform/minecraft/{PATH}\0",
                registry_oid(target)?
            )
        } else {
            String::new()
        };
        assert_eq!(bytes, expected.as_bytes(), "{name}");
    }
    assert_eq!(present, 10);
    Ok(())
}
