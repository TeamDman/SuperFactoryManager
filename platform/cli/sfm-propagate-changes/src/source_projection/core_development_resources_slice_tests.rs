//! Post-promotion real collector tests for 29 bounded development resources.
//!
//! Frozen objects are test witnesses only, never renderer inputs. Isolated
//! Gradle/lock placeholders satisfy collector shape checks, not build identity.
//! This module does not claim native/Gradle/JAR/gameplay or full resource closure.

#![cfg(test)]

use super::candidate_lock::checked_file;
use super::context::ProjectionContext;
use super::core_features::CoreFeatureDefinition;
use super::core_inputs::CORE_ROOT;
use super::core_inputs::CoreProjectInputs;
use super::core_inputs::InputPredicate;
use super::core_inputs::InputVariant;
use super::core_inputs::collect_core_artifacts;
use super::core_inputs::discover_core_source_files;
use super::core_inputs::select_core_inputs;
use super::core_slice_test_support::CoreTestFixture;
use super::core_slice_test_support::read_bounded;
use super::core_slice_test_support::read_git_blobs;
use super::projection_catalog::reject_duplicate_catalog_keys;
use super::provenance::sha256;
use super::release_baseline::frozen_git_command;
use super::render_java_source;
use eyre::Result;
use eyre::ensure;
use facet::Facet;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::fs;
use std::path::Path;
use std::path::PathBuf;

const LEDGER: &str = "docs/tasks/sfm-core-development-resources-slice-v2.json";
const LEDGER_SHA256: &str =
    "sha256:272d3ccd6ecc3f40566571be395703c7d9b5df596e786c51e4e2e5ea03abf801";
const STAGE: &str =
    "platform/cli/sfm-propagate-changes/target/core-development-resources-stage-v1/";
const LIMIT: u64 = 1024 * 1024;
const CM: &str = "client_manager";
const TOUCH: &str = "touch_display";
const CM_SKIN: &str = "client_manager_custom_textures";
const TOUCH_SKIN: &str = "touch_display_custom_textures";
const IMAGE: &str = "image_resources";
const PACKET: &str = "packet_values";
const COMPUTE: &str = "packet_computation";
const PRIVATE: &str = "packet_transport_private";
const BUFFER: &str = "redstone_buffer_storage";
const CC: &str = "computercraft";
const CM_MODEL: &str = "src/generated/resources/assets/sfm/models/block/client_manager.json";
const TOUCH_MODEL: &str = "src/generated/resources/assets/sfm/models/block/touch_display.json";
const CM_RECIPE: &str = "src/generated/resources/data/sfm/recipes/client_manager.json";
const TOUCH_RECIPE: &str = "src/generated/resources/data/sfm/recipes/touch_display.json";
const PACKET_MODEL: &str = "src/generated/resources/assets/sfm/models/item/packet.json";
const PACKET_ICON: &str = "src/main/resources/assets/sfm/textures/item/packet.png";
const IMAGE_MODEL: &str = "src/generated/resources/assets/sfm/models/block/buffer_image.json";
const PACKET_PROGRAM: &str =
    "src/main/resources/assets/sfm/template_programs/packet_computation.sfml";
const REDSTONE_PROGRAM: &str =
    "src/main/resources/assets/sfm/template_programs/buffer_redstone_counter.sfml";
const GRAMMAR: &str = "src/main/antlr/sfml/SFML.g4";
const TARGETS: [&str; 10] = [
    "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
    "26.1.2",
];
// Deliberately local source-collector fixture list, not a private production API.
const PROJECT_FILES: [&str; 8] = [
    "build.gradle",
    "settings.gradle",
    "gradle.properties",
    "gradlew",
    "gradlew.bat",
    "sfm-toolchain.lock.json",
    "gradle/wrapper/gradle-wrapper.properties",
    "gradle/wrapper/gradle-wrapper.jar",
];

#[derive(Facet)]
struct Ledger {
    schema: String,
    context_commits: BTreeMap<String, String>,
    explicit_owner_roots: BTreeMap<String, Vec<String>>,
    owner_contracts: BTreeMap<String, CoreFeatureDefinition>,
    resources: Vec<Resource>,
    raw_blobs: BTreeMap<String, RawBlob>,
    excluded_cache_paths: Vec<String>,
}
#[derive(Facet)]
struct Resource {
    path: String,
    intended_core_path: String,
    stage_path: String,
    template_sha256: String,
    template_bytes: usize,
    metadata_rule: InputVariant,
    witnesses: BTreeMap<String, Option<String>>,
}
#[derive(Facet)]
struct RawBlob {
    raw_sha256: String,
    raw_bytes: usize,
    binary: bool,
    raw_final_lf: Option<bool>,
    crlf_count: Option<usize>,
    lone_cr_count: Option<usize>,
    bom: Option<bool>,
    normalization: String,
    append_lf: bool,
    normalized_sha256: String,
    normalized_bytes: usize,
    png: Option<PngEvidence>,
}
#[derive(Facet)]
struct PngEvidence {
    width: u32,
    height: u32,
    bit_depth: u8,
    color_type: u8,
    alpha_min: u8,
    alpha_max: u8,
    alpha_counts: AlphaCounts,
}
#[derive(Facet)]
struct AlphaCounts {
    opaque: u32,
    transparent: u32,
    translucent: u32,
}
#[derive(Facet)]
struct CubeModel {
    parent: String,
    textures: BTreeMap<String, String>,
}
#[derive(Facet)]
struct ItemModel {
    parent: String,
    #[facet(default)]
    textures: BTreeMap<String, String>,
}

struct Fixture {
    core: CoreTestFixture,
    ledger: Ledger,
    raw: BTreeMap<String, Vec<u8>>,
    normalized: BTreeMap<String, Vec<u8>>,
    authored: BTreeMap<String, Vec<u8>>,
}
impl Fixture {
    fn load() -> Result<Self> {
        let core = CoreTestFixture::load()?;
        let bytes = read_bounded(&checked_file(&core.repository, LEDGER)?, LIMIT)?;
        ensure!(
            sha256(&bytes) == LEDGER_SHA256,
            "immutable resource evidence drift"
        );
        let ledger: Ledger = facet_json::from_str(std::str::from_utf8(&bytes)?)?;
        ensure!(
            ledger.schema == "sfm:core_development_resource_slice@2"
                && ledger.resources.len() == 29
                && ledger.raw_blobs.len() == 29
                && ledger.context_commits.len() == 20
                && ledger.explicit_owner_roots.len() == 20
                && ledger.owner_contracts.len() == 10
                && ledger.excluded_cache_paths.len() == 8,
            "bounded resource scope drift"
        );
        for (owner, expected) in &ledger.owner_contracts {
            let actual = core
                .features
                .0
                .get(owner)
                .ok_or_else(|| eyre::eyre!("resource owner unregistered: {owner}"))?;
            ensure!(
                same_names(&actual.requires, &expected.requires)
                    && same_names(&actual.supported_targets, &expected.supported_targets),
                "resource support/prerequisite contract drift: {owner}"
            );
        }
        let oids = ledger.raw_blobs.keys().cloned().collect();
        let raw = read_git_blobs(&core.repository, &oids)?;
        let mut normalized = BTreeMap::new();
        for (oid, fixed) in &ledger.raw_blobs {
            normalized.insert(oid.clone(), normalize(&raw[oid], fixed)?);
        }
        let mut authored = BTreeMap::new();
        for resource in &ledger.resources {
            ensure!(
                resource.intended_core_path == format!("{CORE_ROOT}/{}", resource.path)
                    && resource.stage_path == format!("{STAGE}{}", resource.path)
                    && resource.metadata_rule.input == resource.path
                    && resource.witnesses.len() == 20
                    && core.metadata.source_rules.get(&resource.path)
                        == Some(&vec![resource.metadata_rule.clone()]),
                "exact resource ownership/mode or twenty-cell scope drift"
            );
            ensure!(
                resource.path.starts_with("src/")
                    && !resource.path.contains("/.cache/")
                    && !resource.metadata_rule.when.targets.is_empty()
                    && !resource.metadata_rule.when.all_features.is_empty()
                    && resource.metadata_rule.when.any_features.is_empty()
                    && resource.metadata_rule.when.none_features.is_empty(),
                "resource gained implicit membership or cache input"
            );
            for (context, oid) in &resource.witnesses {
                ensure!(
                    ledger.context_commits.contains_key(context),
                    "unknown witness context"
                );
                if let Some(oid) = oid {
                    ensure!(normalized.contains_key(oid), "unbound raw witness");
                }
            }
            let source = read_bounded(&checked_file(&core.core, &resource.path)?, LIMIT)?;
            ensure!(
                source.len() == resource.template_bytes
                    && sha256(&source) == resource.template_sha256,
                "authored resource hash drift: {}",
                resource.path
            );
            if !resource.path.ends_with(".png") {
                ensure!(
                    !source.contains(&b'\r')
                        && source.ends_with(b"\n")
                        && !std::str::from_utf8(&source)?.starts_with('\u{feff}'),
                    "authored text LF/BOM drift"
                );
            }
            ensure!(
                authored.insert(resource.path.clone(), source).is_none(),
                "duplicate path"
            );
        }
        for excluded in &ledger.excluded_cache_paths {
            ensure!(
                excluded.starts_with("src/generated/resources/.cache/")
                    && !authored.contains_key(excluded),
                "datagen housekeeping was included"
            );
        }
        Ok(Self {
            core,
            ledger,
            raw,
            normalized,
            authored,
        })
    }
    fn isolated(&self) -> Result<Isolated> {
        let temp = tempfile::tempdir()?;
        let root = temp.path().join(CORE_ROOT);
        fs::create_dir_all(&root)?;
        let mut metadata = self.core.metadata.clone();
        metadata
            .source_rules
            .retain(|path, _| self.authored.contains_key(path));
        metadata.project_files.clear();
        for (path, bytes) in &self.authored {
            write_fixture(&root, path, bytes)?;
        }
        for output in PROJECT_FILES {
            metadata.project_files.insert(
                output.to_owned(),
                vec![InputVariant {
                    input: format!("build/isolated-resource-fixture/{output}"),
                    when: InputPredicate::default(),
                    template: false,
                }],
            );
        }
        Ok(Isolated {
            _temp: temp,
            root,
            metadata,
        })
    }
    fn context(&self, target: &str, roots: &[&str]) -> Result<ProjectionContext> {
        // Resolve only declared current prerequisites of explicitly reviewed
        // roots. validate_entry remains the final support/closure authority.
        let mut enabled = BTreeSet::new();
        let mut pending = roots.iter().map(|id| (*id).to_owned()).collect::<Vec<_>>();
        while let Some(id) = pending.pop() {
            let def = self
                .core
                .features
                .0
                .get(&id)
                .ok_or_else(|| eyre::eyre!("unknown explicit resource owner: {id}"))?;
            ensure!(
                def.supported_targets.iter().any(|t| t == target),
                "unsupported explicit resource owner: {id}"
            );
            if enabled.insert(id) {
                pending.extend(def.requires.iter().cloned());
            }
        }
        self.core.context(
            target,
            &enabled.iter().map(String::as_str).collect::<Vec<_>>(),
        )
    }
}
struct Isolated {
    _temp: tempfile::TempDir,
    root: PathBuf,
    metadata: CoreProjectInputs,
}
impl Isolated {
    fn prepare(&self, context: &ProjectionContext) -> Result<BTreeMap<String, Vec<u8>>> {
        let target = if context.minecraft_version == "1.21" {
            "1.21.0"
        } else {
            &context.minecraft_version
        };
        for output in PROJECT_FILES {
            let bytes = match output {
                "gradle.properties" => format!(
                    "minecraft_version={}\nmod_version=source-fixture-only\n",
                    context.minecraft_version
                )
                .into_bytes(),
                "settings.gradle" => format!("rootProject.name = 'sfm-{target}'\n").into_bytes(),
                "gradle/wrapper/gradle-wrapper.properties" => {
                    b"distributionUrl=https\\://example.invalid/gradle-7.5-bin.zip\n".to_vec()
                }
                "gradle/wrapper/gradle-wrapper.jar" => b"\0\xffsource-fixture-only".to_vec(),
                _ => b"source-fixture-only\n".to_vec(),
            };
            write_fixture(
                &self.root,
                &format!("build/isolated-resource-fixture/{output}"),
                &bytes,
            )?;
        }
        let inventory = discover_core_source_files(&self.root)?;
        let selected = select_core_inputs(&self.metadata, context, &inventory)?;
        let artifacts = collect_core_artifacts(&self.root, &selected, context)?;
        let mut outputs = BTreeMap::new();
        for (path, variants) in &self.metadata.source_rules {
            if let Some(artifact) = artifacts.get(path) {
                ensure!(
                    artifact.source_path == format!("{CORE_ROOT}/{path}")
                        && artifact.overlay.is_none()
                        && selected.inputs[path].input == *path
                        && selected.inputs[path].template == variants[0].template,
                    "resource collected outside exact authored mode/boundary"
                );
                outputs.insert(path.clone(), artifact.output_bytes.clone());
            } else {
                ensure!(selected.omitted_paths.contains(path), "unowned omission");
            }
        }
        Ok(outputs)
    }
}
fn write_fixture(root: &Path, relative: &str, bytes: &[u8]) -> Result<()> {
    let path = root.join(relative);
    fs::create_dir_all(path.parent().expect("fixed fixture parent"))?;
    fs::write(path, bytes)?;
    Ok(())
}
fn same_names(left: &[String], right: &[String]) -> bool {
    left.len() == right.len()
        && left.iter().collect::<BTreeSet<_>>() == right.iter().collect::<BTreeSet<_>>()
}
fn normalize(bytes: &[u8], fixed: &RawBlob) -> Result<Vec<u8>> {
    ensure!(
        bytes.len() == fixed.raw_bytes && sha256(bytes) == fixed.raw_sha256,
        "raw resource bytes/hash drift"
    );
    if fixed.binary {
        ensure!(
            !fixed.append_lf
                && fixed.normalization == "raw_exact"
                && fixed.raw_final_lf.is_none()
                && fixed.crlf_count.is_none()
                && fixed.lone_cr_count.is_none()
                && fixed.bom.is_none(),
            "binary bytes gained a text normalization"
        );
        verify_png_header(
            bytes,
            fixed
                .png
                .as_ref()
                .ok_or_else(|| eyre::eyre!("PNG evidence missing"))?,
        )?;
    } else {
        ensure!(
            fixed.png.is_none()
                && fixed.crlf_count == Some(0)
                && fixed.lone_cr_count == Some(0)
                && fixed.bom == Some(false)
                && !bytes.contains(&b'\r')
                && fixed.raw_final_lf == Some(bytes.ends_with(b"\n"))
                && !std::str::from_utf8(bytes)?.starts_with('\u{feff}'),
            "raw text CR/BOM/EOF drift"
        );
        ensure!(
            fixed.normalization
                == if fixed.append_lf {
                    "sfm:text_append_terminal_lf@1"
                } else {
                    "raw_exact"
                },
            "unapproved text policy"
        );
    }
    let mut output = bytes.to_vec();
    if fixed.append_lf {
        ensure!(
            !fixed.binary && fixed.raw_final_lf == Some(false),
            "duplicate terminal LF"
        );
        output.push(b'\n');
    }
    ensure!(
        output.len() == fixed.normalized_bytes && sha256(&output) == fixed.normalized_sha256,
        "resource exceeded exact normalization approval"
    );
    Ok(output)
}
fn verify_png_header(bytes: &[u8], fixed: &PngEvidence) -> Result<()> {
    ensure!(
        bytes.len() >= 33
            && &bytes[..8] == b"\x89PNG\r\n\x1a\n"
            && &bytes[8..12] == b"\0\0\0\r"
            && &bytes[12..16] == b"IHDR"
            && u32::from_be_bytes(bytes[16..20].try_into()?) == fixed.width
            && u32::from_be_bytes(bytes[20..24].try_into()?) == fixed.height
            && bytes[24] == fixed.bit_depth
            && bytes[25] == fixed.color_type
            && fixed.bit_depth == 8
            && fixed.color_type == 6
            && fixed.width == 16
            && matches!(fixed.height, 16 | 64)
            && bytes[26..29] == [0, 0, 0]
            && fixed.alpha_min <= fixed.alpha_max
            && fixed.alpha_counts.opaque
                + fixed.alpha_counts.transparent
                + fixed.alpha_counts.translucent
                == fixed.width * fixed.height,
        "PNG header or independently decoded alpha evidence drift"
    );
    // Exact raw SHA pins the payload; this pure Rust test does not decode
    // alpha. Ledger alpha counts came from the recorded read-only bitmap probe.
    Ok(())
}
fn parse_json(bytes: &[u8]) -> Result<BTreeMap<String, facet_json::RawJson<'static>>> {
    let input = std::str::from_utf8(bytes)?;
    reject_duplicate_catalog_keys(input)?;
    Ok(facet_json::from_str(input)?)
}
fn model(bytes: &[u8]) -> Result<CubeModel> {
    let object = parse_json(bytes)?;
    ensure!(
        object["parent"].as_str().starts_with('"'),
        "model parent not a JSON string"
    );
    Ok(facet_json::from_str(std::str::from_utf8(bytes)?)?)
}
fn verify_frozen_trees(fixture: &Fixture) -> Result<()> {
    for (identity, commit) in &fixture.ledger.context_commits {
        ensure!(
            commit.len() == 40 && commit.bytes().all(|b| b.is_ascii_hexdigit()),
            "invalid fixed source commit"
        );
        let mut command = frozen_git_command(&fixture.core.repository);
        command
            .env("GIT_ALLOW_PROTOCOL", "")
            .env("GIT_TERMINAL_PROMPT", "0")
            .args(["ls-tree", "-r", commit, "--"]);
        for resource in &fixture.ledger.resources {
            command.arg(format!("platform/minecraft/{}", resource.path));
        }
        let result = command.output()?;
        ensure!(
            result.status.success() && result.stdout.len() < 32 * 1024,
            "bounded frozen tree reader failed"
        );
        let mut actual = BTreeMap::new();
        for line in std::str::from_utf8(&result.stdout)?.lines() {
            let (header, path) = line
                .split_once('\t')
                .ok_or_else(|| eyre::eyre!("invalid tree framing"))?;
            let fields = header.split_whitespace().collect::<Vec<_>>();
            let path = path
                .strip_prefix("platform/minecraft/")
                .ok_or_else(|| eyre::eyre!("tree path outside fixed source boundary"))?;
            ensure!(
                fields.len() == 3
                    && fields[0] == "100644"
                    && fields[1] == "blob"
                    && actual.insert(path, fields[2]).is_none(),
                "unexpected tree mode/type/duplicate"
            );
        }
        for resource in &fixture.ledger.resources {
            assert_eq!(
                actual.get(resource.path.as_str()).copied(),
                resource.witnesses[identity].as_deref(),
                "{identity}/{}",
                resource.path
            );
        }
    }
    Ok(())
}

#[test]
fn development_resources_collect_all_580_frozen_cells_with_exact_raw_pins() -> Result<()> {
    let fixture = Fixture::load()?;
    verify_frozen_trees(&fixture)?;
    let isolated = fixture.isolated()?;
    let mut counts = (0, 0);
    for identity in fixture.ledger.context_commits.keys() {
        let (_, target) = identity.split_once('/').expect("fixed context");
        let roots = fixture.ledger.explicit_owner_roots[identity]
            .iter()
            .map(String::as_str)
            .collect::<Vec<_>>();
        let context = fixture.context(target, &roots)?;
        let output = isolated.prepare(&context)?;
        for resource in &fixture.ledger.resources {
            if let Some(oid) = &resource.witnesses[identity] {
                assert_eq!(
                    output[&resource.path], fixture.normalized[oid],
                    "{identity}/{}",
                    resource.path
                );
                if resource.path.ends_with(".json") || resource.path.ends_with(".mcmeta") {
                    parse_json(&output[&resource.path])?;
                }
                counts.0 += 1;
            } else {
                assert!(
                    !output.contains_key(&resource.path),
                    "{identity}/{} leaked",
                    resource.path
                );
                counts.1 += 1;
            }
        }
    }
    assert_eq!(counts, (52, 528));
    Ok(())
}

#[test]
fn development_resources_skins_are_independent_of_placement_and_seamless_flags() -> Result<()> {
    let fixture = Fixture::load()?;
    let isolated = fixture.isolated()?;
    for cm_mode in 0..3 {
        for touch_mode in 0..3 {
            let mut roots = Vec::new();
            if cm_mode > 0 {
                roots.push(CM);
            }
            if cm_mode == 2 {
                roots.push(CM_SKIN);
            }
            if touch_mode > 0 {
                roots.push(TOUCH);
            }
            if touch_mode == 2 {
                roots.push(TOUCH_SKIN);
            }
            let base = fixture.context("1.19.2", &roots)?;
            let outputs = isolated.prepare(&base)?;
            for (mode, path, suffix) in [
                (cm_mode, CM_MODEL, "client_manager"),
                (touch_mode, TOUCH_MODEL, "touch_display"),
            ] {
                assert_eq!(outputs.contains_key(path), mode > 0);
                if mode > 0 {
                    let parsed = model(&outputs[path])?;
                    assert_eq!(parsed.parent, "minecraft:block/cube_bottom_top");
                    assert_eq!(parsed.textures["particle"], "#top");
                    assert_eq!(
                        parsed.textures["bottom"],
                        if mode == 2 {
                            format!("sfm:block/{suffix}_bottom")
                        } else {
                            "sfm:block/manager_bot".to_owned()
                        }
                    );
                    assert_eq!(
                        parsed.textures["side"],
                        if mode == 2 {
                            format!("sfm:block/{suffix}_side")
                        } else if suffix == "client_manager" {
                            "minecraft:block/cyan_concrete".to_owned()
                        } else {
                            "sfm:block/manager_side".to_owned()
                        }
                    );
                }
                let stem = format!("src/main/resources/assets/sfm/textures/block/{suffix}_");
                assert_eq!(
                    outputs.keys().filter(|key| key.starts_with(&stem)).count(),
                    if mode == 2 { 4 } else { 0 }
                );
            }
            if touch_mode > 0 {
                for placement in [
                    "touch_display_seamless_surface",
                    "touch_display_furnace_placement",
                ] {
                    let mut extended = roots.clone();
                    extended.push(placement);
                    assert_eq!(
                        isolated.prepare(&fixture.context("1.19.2", &extended)?)?,
                        outputs
                    );
                }
            }
        }
    }
    assert!(fixture.core.context("1.19.2", &[CM_SKIN]).is_err());
    assert!(fixture.core.context("1.19.2", &[TOUCH_SKIN]).is_err());
    assert!(fixture.context("1.19.4", &[CM_SKIN]).is_err());
    assert!(fixture.context("1.19.4", &[TOUCH_SKIN]).is_err());
    Ok(())
}

#[test]
fn development_resources_item_image_redstone_and_transport_owners_do_not_blend() -> Result<()> {
    let fixture = Fixture::load()?;
    let isolated = fixture.isolated()?;
    for target in &TARGETS[..2] {
        for owner in [PACKET, IMAGE, BUFFER, COMPUTE, PRIVATE, CM, TOUCH] {
            let context = fixture.context(target, &[owner])?;
            let outputs = isolated.prepare(&context)?;
            assert_eq!(outputs.contains_key(PACKET_MODEL), context.features[PACKET]);
            assert_eq!(outputs.contains_key(PACKET_ICON), context.features[PACKET]);
            assert_eq!(outputs.contains_key(IMAGE_MODEL), context.features[IMAGE]);
            assert_eq!(
                outputs.contains_key(REDSTONE_PROGRAM),
                context.features[BUFFER]
            );
            assert_eq!(
                outputs.contains_key(PACKET_PROGRAM),
                context.features[COMPUTE] && context.features[PRIVATE]
            );
            assert_eq!(outputs.contains_key(CM_MODEL), context.features[CM]);
            assert_eq!(outputs.contains_key(TOUCH_MODEL), context.features[TOUCH]);
            assert!(!outputs.keys().any(|p| p.ends_with(".mcmeta")));
        }
        let packet = isolated.prepare(&fixture.context(target, &[PACKET])?)?;
        assert_eq!(packet.len(), 2);
        let parsed: ItemModel = facet_json::from_str(std::str::from_utf8(&packet[PACKET_MODEL])?)?;
        assert_eq!(parsed.parent, "minecraft:item/generated");
        assert_eq!(parsed.textures["layer0"], "sfm:item/packet");
        let image = isolated.prepare(&fixture.context(target, &[IMAGE])?)?;
        let parsed = model(&image[IMAGE_MODEL])?;
        assert_eq!(parsed.parent, "minecraft:block/cube_all");
        assert_eq!(parsed.textures["all"], "sfm:block/buffer_unknown");
    }
    Ok(())
}

#[test]
fn development_resource_examples_pin_server_text_and_private_broadcast_grammar() -> Result<()> {
    let fixture = Fixture::load()?;
    let isolated = fixture.isolated()?;
    let grammar = read_bounded(&checked_file(&fixture.core.core, GRAMMAR)?, LIMIT)?;
    for target in &TARGETS[..2] {
        let compute = fixture.context(target, &[COMPUTE])?;
        let outputs = isolated.prepare(&compute)?;
        assert!(!outputs.contains_key(PACKET_PROGRAM));
        let language = render_java_source(std::str::from_utf8(&grammar)?, &compute)?;
        assert!(language.contains("STRING_TYPE OF INVOKE invokeActionId WITH identifier"));
        assert!(!language.contains("broadcastStatement : BROADCAST"));
        let transport = fixture.context(target, &[PRIVATE])?;
        let outputs = isolated.prepare(&transport)?;
        let text = std::str::from_utf8(&outputs[PACKET_PROGRAM])?;
        assert!(text.contains("WITH CAPABILITY sfm:text"));
        assert!(text.contains("STRING OF INVOKE sfm:text/read WITH userinput"));
        assert!(text.contains("BROADCAST TO me"));
        assert!(!transport.features["client_program_actions"]);
        assert!(!transport.features["client_actions"]);
        assert!(!transport.features["client_inbox"]);
        let language = render_java_source(std::str::from_utf8(&grammar)?, &transport)?;
        assert!(language.contains("broadcastStatement : BROADCAST TO identifier;"));
        let redstone = isolated.prepare(&fixture.context(target, &[BUFFER])?)?;
        let text = std::str::from_utf8(&redstone[REDSTONE_PROGRAM])?;
        assert!(text.contains("INPUT 1 redstone:: FROM donor"));
        assert!(!text.contains("INVOKE"));
        assert!(!text.contains("BROADCAST"));
    }
    Ok(())
}

#[test]
fn development_resources_json_and_destination_cases_follow_actual_version_not_environment()
-> Result<()> {
    let fixture = Fixture::load()?;
    let isolated = fixture.isolated()?;
    for target in TARGETS {
        let roots = if matches!(target, "1.19.2" | "1.19.4") {
            vec![CM, TOUCH, CC]
        } else {
            vec![CC]
        };
        let context = fixture.context(target, &roots)?;
        let outputs = isolated.prepare(&context)?;
        let mut renamed = context.clone();
        renamed.environment = "arbitrary-descriptive-environment".to_owned();
        renamed.projection_key = "unrelated/nested/resource-preview".to_owned();
        renamed.preset = renamed.projection_key.clone();
        assert_eq!(isolated.prepare(&renamed)?, outputs);
        let plural = "src/main/resources/data/sfm/computercraft/turtle_upgrades/labeler.json";
        let singular = "src/main/resources/data/sfm/computercraft/turtle_upgrade/labeler.json";
        assert_eq!(outputs.contains_key(plural), TARGETS[..8].contains(&target));
        assert_eq!(
            outputs.contains_key(singular),
            TARGETS[8..].contains(&target)
        );
        let cc = parse_json(
            &outputs[if TARGETS[..8].contains(&target) {
                plural
            } else {
                singular
            }],
        )?;
        assert_eq!(cc["type"].as_str(), "\"sfm:labeler\"");
        if matches!(target, "1.19.2" | "1.19.4") {
            for path in [CM_RECIPE, TOUCH_RECIPE] {
                let json = parse_json(&outputs[path])?;
                assert_eq!(json["type"].as_str(), "\"minecraft:crafting_shaped\"");
                assert_eq!(json.contains_key("category"), target == "1.19.4");
                assert_eq!(json.contains_key("show_notification"), target == "1.19.4");
                if target == "1.19.4" {
                    assert_eq!(json["category"].as_str(), "\"misc\"");
                    assert_eq!(json["show_notification"].as_str(), "true");
                }
            }
            for suffix in ["client_manager", "touch_display"] {
                let prefix = "src/generated/resources/data/sfm/advancements/recipes/";
                assert!(outputs.contains_key(&format!(
                    "{prefix}{}{suffix}.json",
                    if target == "1.19.2" { "sfm/" } else { "misc/" }
                )));
                assert!(!outputs.contains_key(&format!(
                    "{prefix}{}{suffix}.json",
                    if target == "1.19.2" { "misc/" } else { "sfm/" }
                )));
            }
        }
    }
    Ok(())
}

#[test]
fn development_resources_inactive_inputs_are_omitted_before_poisoned_reads() -> Result<()> {
    let fixture = Fixture::load()?;
    let isolated = fixture.isolated()?;
    write_fixture(&isolated.root, CM_MODEL, b"\xff\xfeinactive-invalid-utf8")?;
    let off = fixture.context("1.19.2", &[])?;
    assert!(isolated.prepare(&off)?.is_empty());
    let cm = fixture.context("1.19.2", &[CM])?;
    assert!(isolated.prepare(&cm).is_err());
    // Missing exact-copy binary is harmless while its skin owner is false.
    let icon = "src/main/resources/assets/sfm/textures/block/client_manager_top.png";
    fs::remove_file(isolated.root.join(icon))?;
    write_fixture(&isolated.root, CM_MODEL, &fixture.authored[CM_MODEL])?;
    assert!(!isolated.prepare(&cm)?.contains_key(icon));
    assert!(
        isolated
            .prepare(&fixture.context("1.19.2", &[CM_SKIN])?)
            .is_err()
    );
    Ok(())
}

#[test]
fn development_resources_raw_normalization_unknowns_and_mode_mutations_refuse() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut counts = (0, 0, 0);
    for (oid, fixed) in &fixture.ledger.raw_blobs {
        let raw = &fixture.raw[oid];
        let mut changed = raw.clone();
        changed.push(b' ');
        assert!(normalize(&changed, fixed).is_err());
        let mut changed = raw.clone();
        changed.push(b'\n');
        assert!(normalize(&changed, fixed).is_err());
        if fixed.binary {
            counts.2 += 1;
        } else if fixed.append_lf {
            counts.0 += 1;
        } else {
            counts.1 += 1;
        }
    }
    assert_eq!(counts, (18, 4, 7));
    let mut isolated = fixture.isolated()?;
    let context = fixture.context("1.19.2", &[CM])?;
    write_fixture(
        &isolated.root,
        CM_MODEL,
        b"{% if features.unregistered_resource_owner %}\n{}\n{% else %}\n{}\n{% endif %}\n",
    )?;
    assert!(isolated.prepare(&context).is_err());
    write_fixture(&isolated.root, CM_MODEL, &fixture.authored[CM_MODEL])?;
    isolated
        .metadata
        .source_rules
        .get_mut(CM_MODEL)
        .expect("fixed source rule")[0]
        .template = false;
    let raw = isolated.prepare(&context)?;
    assert_ne!(
        raw[CM_MODEL],
        fixture.normalized[fixture
            .ledger
            .resources
            .iter()
            .find(|r| r.path == CM_MODEL)
            .expect("fixed resource")
            .witnesses["dev/1.19.4"]
            .as_ref()
            .expect("fixed raw model")]
    );
    assert!(
        fixture
            .context("1.19.2", &["unregistered_resource_owner"])
            .is_err()
    );
    for owner in [CM, TOUCH, CM_SKIN, TOUCH_SKIN, IMAGE, COMPUTE, PRIVATE] {
        let context = fixture.context("1.19.2", &[owner])?;
        for required in &fixture.core.features.0[owner].requires {
            let names = context
                .features
                .iter()
                .filter(|(id, on)| **on && *id != required)
                .map(|(id, _)| id.as_str())
                .collect::<Vec<_>>();
            assert!(fixture.core.context("1.19.2", &names).is_err());
        }
    }
    Ok(())
}

#[test]
fn development_resources_common_recipe_edit_propagates_through_both_version_bodies() -> Result<()> {
    let fixture = Fixture::load()?;
    let isolated = fixture.isolated()?;
    let original = std::str::from_utf8(&fixture.authored[CM_RECIPE])?;
    let edited = original.replace("\"ABA\"", "\"CCC\"");
    assert_ne!(original, edited);
    write_fixture(&isolated.root, CM_RECIPE, edited.as_bytes())?;
    let resource = fixture
        .ledger
        .resources
        .iter()
        .find(|r| r.path == CM_RECIPE)
        .expect("fixed recipe");
    for target in &TARGETS[..2] {
        let output = isolated.prepare(&fixture.context(target, &[CM])?)?;
        let oid = resource.witnesses[&format!("dev/{target}")]
            .as_ref()
            .expect("fixed recipe witness");
        let expected = std::str::from_utf8(&fixture.normalized[oid])?.replace("\"ABA\"", "\"CCC\"");
        assert_eq!(output[CM_RECIPE], expected.as_bytes());
        parse_json(&output[CM_RECIPE])?;
    }
    Ok(())
}
