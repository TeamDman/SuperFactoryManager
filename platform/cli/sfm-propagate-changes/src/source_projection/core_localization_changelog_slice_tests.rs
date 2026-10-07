//! Post-promotion contracts for the two authored localization/changelog inputs.
//!
//! These tests use real core selection and the controlled production renderer.
//! Historical blobs are offline, bounded witnesses only. Isolated Gradle bytes
//! are source-fixture scaffolding, not a native recipe or Java/gameplay proof.
//! Unknown proposed owners refuse until the real feature registry is reviewed.
//! `version_adapter_notes` owns historical release-note presentation only; it
//! does not attest a Java feature, adapter implementation or runtime closure.

#![cfg(test)]

use super::candidate_lock::checked_file;
use super::context::ProjectionContext;
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
use super::render_java_source;
use eyre::Result;
use eyre::ensure;
use facet::Facet;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::fs;
use std::path::Path;
use std::path::PathBuf;

const LEDGER: &str = "docs/tasks/sfm-core-localization-changelog-slice.json";
const STAGE_PREFIX: &str =
    "platform/cli/sfm-propagate-changes/target/core-localization-changelog-stage-v1/";
const LANG: &str = "src/generated/resources/assets/sfm/lang/en_us.json";
const CHANGELOG: &str = "src/main/resources/assets/sfm/template_programs/changelog.sfml";
const BASE_CHANGELOG: &str = "df532f35a03d1f90c5efbaed2e5b05b4cef84d25";
const PRE: &str = "\n---- 4.35.0 PRE ----\n";
const LIMIT: u64 = 1024 * 1024;
const REQUIRED_PROJECT_FILES: [&str; 8] = [
    "build.gradle",
    "settings.gradle",
    "gradle.properties",
    "gradlew",
    "gradlew.bat",
    "sfm-toolchain.lock.json",
    "gradle/wrapper/gradle-wrapper.properties",
    "gradle/wrapper/gradle-wrapper.jar",
];
const TARGETS: [&str; 10] = [
    "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
    "26.1.2",
];
const COMMITS: [(&str, &str); 20] = [
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
#[derive(Clone, Copy)]
struct Golden {
    oid: &'static str,
    raw_sha256: &'static str,
    raw_bytes: usize,
    final_lf: bool,
    append_lf: bool,
    normalized_sha256: &'static str,
    normalized_bytes: usize,
}
const GOLDENS: [Golden; 14] = [
    Golden {
        oid: "b5299272e7078501f69f3c57c5a358b7784f2318",
        raw_sha256: "sha256:3b610fda93a80e3940a3d2c64a8d835fc3b00166d34688eafe4a35c508749fe1",
        raw_bytes: 61985,
        final_lf: true,
        append_lf: false,
        normalized_sha256: "sha256:3b610fda93a80e3940a3d2c64a8d835fc3b00166d34688eafe4a35c508749fe1",
        normalized_bytes: 61985,
    },
    Golden {
        oid: "961f0c71e84f71521d282b01892ee23fbd749474",
        raw_sha256: "sha256:faf7ce889dedb4fc0cbc76762ce47037acd81fa3998d18783955d98d6e292bf7",
        raw_bytes: 42451,
        final_lf: false,
        append_lf: true,
        normalized_sha256: "sha256:df9906aacdce2172981c446783f885168336364bbd25a88b663f763eec221097",
        normalized_bytes: 42452,
    },
    Golden {
        oid: "e1db48676507aa4af36abbaa358c5964b1a6a1e6",
        raw_sha256: "sha256:9f94f6dd9dd8f421092c5d02ca4431a31fa18e305086b47cc6c6b6dec42df0a5",
        raw_bytes: 61559,
        final_lf: true,
        append_lf: false,
        normalized_sha256: "sha256:9f94f6dd9dd8f421092c5d02ca4431a31fa18e305086b47cc6c6b6dec42df0a5",
        normalized_bytes: 61559,
    },
    Golden {
        oid: "41bd9bfef95475f4d735bca6fccd82184b687752",
        raw_sha256: "sha256:83a2d0295af3d149fab400335d88e822d6e65e8eab67c76898a3ce5ae2250796",
        raw_bytes: 41859,
        final_lf: false,
        append_lf: true,
        normalized_sha256: "sha256:f8ba022a7660ce8854f6b84ae8ca8be0604f3d6ba198945b14e9083ab69c6999",
        normalized_bytes: 41860,
    },
    Golden {
        oid: "4f3be3a3c9b68e62258afb1fa666ab3d16a4597f",
        raw_sha256: "sha256:49821558efd23462414541c996b0d9d6cd7a88c638d66c80fc92750157311448",
        raw_bytes: 27066,
        final_lf: true,
        append_lf: false,
        normalized_sha256: "sha256:49821558efd23462414541c996b0d9d6cd7a88c638d66c80fc92750157311448",
        normalized_bytes: 27066,
    },
    Golden {
        oid: "7ea96baa7aefbfca2472529bfc5ec3df74136f70",
        raw_sha256: "sha256:879c4df02142e6716cbdb8f8597913f20d42cc88796069a6abf0e191f9abe4df",
        raw_bytes: 26887,
        final_lf: false,
        append_lf: true,
        normalized_sha256: "sha256:8230e86e699ab8a0542a588a7646eba3cf527e736b02b81cbd0fddfa96c3181c",
        normalized_bytes: 26888,
    },
    Golden {
        oid: "f40ce5ec8cbda9e4d78addb6fc0b60e1138e6e44",
        raw_sha256: "sha256:1181df8ed71e30a8b8085777a2945bc92b5e9dd59a51d3bf149af5feaf17baa7",
        raw_bytes: 27418,
        final_lf: false,
        append_lf: true,
        normalized_sha256: "sha256:ab28b11ddc7a23e3ac86fe285111903f80758071834990a85c9fc4425cb49b98",
        normalized_bytes: 27419,
    },
    Golden {
        oid: "6df874f46ec7a54e054364cf3324201dadef62ec",
        raw_sha256: "sha256:9035c4b1eac1083d56c8e277639c406237c67805ff83a0582371d192adce7749",
        raw_bytes: 26636,
        final_lf: false,
        append_lf: true,
        normalized_sha256: "sha256:718a3bbaf041b699c30f85db36a23087920853064c70567f55ae72fd55249644",
        normalized_bytes: 26637,
    },
    Golden {
        oid: "12e0ac969f660698c981600df5f270b80fe8a04b",
        raw_sha256: "sha256:1ffae33a4ff8ecc8f9b1da74377f513018e4de7f4d555e4a8484eb5b1ed34fb4",
        raw_bytes: 27361,
        final_lf: true,
        append_lf: false,
        normalized_sha256: "sha256:1ffae33a4ff8ecc8f9b1da74377f513018e4de7f4d555e4a8484eb5b1ed34fb4",
        normalized_bytes: 27361,
    },
    Golden {
        oid: "df532f35a03d1f90c5efbaed2e5b05b4cef84d25",
        raw_sha256: "sha256:a34a91f4fbc1ac6c895b2e5e9a265af417258dffec14f7dbc7c1badea4bfc4f0",
        raw_bytes: 23357,
        final_lf: true,
        append_lf: false,
        normalized_sha256: "sha256:a34a91f4fbc1ac6c895b2e5e9a265af417258dffec14f7dbc7c1badea4bfc4f0",
        normalized_bytes: 23357,
    },
    Golden {
        oid: "9d335d0d073da71bb8829985590e1ec468ef7e34",
        raw_sha256: "sha256:51f26b44ccbc88b2286da96e4edccf4d5b408d11f67f39c88bd7f87c04fdb407",
        raw_bytes: 22381,
        final_lf: false,
        append_lf: true,
        normalized_sha256: "sha256:9fd0b58983c9c647d4a9d89822caa34316614589e6d166787930420112d9c3d1",
        normalized_bytes: 22382,
    },
    Golden {
        oid: "4a30f00cb3cef02a374dff6dcd05a62d22fdb201",
        raw_sha256: "sha256:984570c58d1c1a0f813069c84ca7149c5466838bdefcc076326d5858d1b3fd4c",
        raw_bytes: 21850,
        final_lf: false,
        append_lf: true,
        normalized_sha256: "sha256:a8ed082e8c83197c14a821d27342c1a0841605a8fafe94d3b6809b71390d5ff5",
        normalized_bytes: 21851,
    },
    Golden {
        oid: "1c1a46b9d74e40786458d7f72f2e888b292896dc",
        raw_sha256: "sha256:acd97488e763b55b64b9cd4cbc3755d7ccffabf2bfb5f575be699bb10428b41c",
        raw_bytes: 21599,
        final_lf: false,
        append_lf: true,
        normalized_sha256: "sha256:1f12833f4dde3bd0bcfc08177168fc15634cedc6cb28b1647936653217284f29",
        normalized_bytes: 21600,
    },
    Golden {
        oid: "816f290ef1110fd2a9f8e88d5eee0c9fb5130e15",
        raw_sha256: "sha256:64189966cbcaf6714a4d56f0dcca9e057e70ce7a413fc466325d2bf1cdb0b090",
        raw_bytes: 21857,
        final_lf: false,
        append_lf: true,
        normalized_sha256: "sha256:a89bbb095d53a675b0f72f6666a4dc5546deba08dc98b33709b81b13380491c7",
        normalized_bytes: 21858,
    },
];
#[derive(Clone, Copy)]
struct Template {
    path: &'static str,
    digest: &'static str,
    bytes: usize,
}
const TEMPLATES: [Template; 2] = [
    Template {
        path: LANG,
        digest: "sha256:c7a2ed951497fb18b52deab52f2d02d74c7e40a1dcf08b090c47577800aef715",
        bytes: 78729,
    },
    Template {
        path: CHANGELOG,
        digest: "sha256:151424ca0e4a1a922cd267179854db9c8c7f492bcae01b38c391bdb98552c57c",
        bytes: 130404,
    },
];
#[derive(Facet)]
struct Ledger {
    schema: String,
    paths: Vec<String>,
    target_ids: Vec<String>,
    frozen_contexts: Vec<FrozenContext>,
    raw_goldens: Vec<RawGolden>,
    templates: Vec<TemplateEvidence>,
    language_baseline_key_cases: Vec<BaselineKey>,
    language_key_predicates: Vec<KeyPredicate>,
    changelog_shared_parts: SharedParts,
    changelog_note_order: Vec<String>,
    changelog_note_predicates: Vec<NotePredicate>,
    authoring_reconstruction_proofs: Vec<Witness>,
    proposed_owner_table: Vec<OwnerEvidence>,
}
#[derive(Facet)]
struct OwnerEvidence {
    feature: String,
    proposed_supported_targets: Vec<String>,
    required_existing_prerequisites: Vec<String>,
    source_consumers: Vec<ConsumerEvidence>,
    tooling_only_status: String,
}
#[derive(Facet)]
struct ConsumerEvidence {
    path: String,
}
#[derive(Facet)]
struct FrozenContext {
    context: String,
    source_commit: String,
}
#[derive(Facet)]
struct RawGolden {
    oid: String,
    raw_sha256: String,
    raw_bytes: usize,
    raw_final_lf: bool,
    crlf_count: usize,
    lone_cr_count: usize,
    bom: bool,
    normalization: String,
    normalized_sha256: String,
    normalized_bytes: usize,
}
#[derive(Facet)]
struct TemplateEvidence {
    path: String,
    stage_path: String,
    sha256: String,
    bytes: usize,
    template: bool,
    production_input: bool,
}
#[derive(Facet)]
struct BaselineKey {
    key: String,
    targets: Vec<String>,
    literal_line: String,
}
#[derive(Facet)]
struct KeyPredicate {
    key: String,
    targets: Vec<String>,
    all_features: Vec<String>,
    any_features: Vec<String>,
    literal_forms: Vec<LiteralForm>,
}
#[derive(Facet)]
struct LiteralForm {
    line: String,
    contexts: Vec<String>,
}
#[derive(Facet)]
struct NotePredicate {
    id: String,
    text: String,
    variants: Vec<NoteVariant>,
}
#[derive(Facet)]
struct NoteVariant {
    targets: Vec<String>,
    all_features: Vec<String>,
    any_features: Vec<String>,
}
#[derive(Facet)]
struct SharedParts {
    header: String,
    suffix: String,
}
#[derive(Facet)]
struct Witness {
    context: String,
    source_commit: String,
    path: String,
    oid: String,
    present: bool,
    mode: String,
    explicit_owner_roots: Vec<String>,
    reconstruction_equal: bool,
    proof: String,
    feature_context_origin: String,
}
#[derive(Debug, Facet, PartialEq, Eq)]
#[facet(transparent)]
struct Language(BTreeMap<String, String>);

struct Fixture {
    core: CoreTestFixture,
    ledger: Ledger,
    raw: BTreeMap<String, Vec<u8>>,
    normalized: BTreeMap<String, Vec<u8>>,
    templates: BTreeMap<String, Vec<u8>>,
}
impl Fixture {
    fn load() -> Result<Self> {
        let core = CoreTestFixture::load()?;
        let bytes = read_bounded(&checked_file(&core.repository, LEDGER)?, LIMIT)?;
        reject_duplicate_catalog_keys(std::str::from_utf8(&bytes)?)?;
        let ledger: Ledger = facet_json::from_str(std::str::from_utf8(&bytes)?)?;
        validate_ledger(&ledger)?;
        let oids = GOLDENS.iter().map(|g| g.oid.to_owned()).collect();
        let raw = read_git_blobs(&core.repository, &oids)?;
        let normalized = GOLDENS
            .iter()
            .map(|fixed| Ok((fixed.oid.to_owned(), normalize(&raw[fixed.oid], *fixed)?)))
            .collect::<Result<BTreeMap<_, _>>>()?;
        let mut templates = BTreeMap::new();
        for fixed in TEMPLATES {
            validate_metadata(&core.metadata, fixed.path)?;
            let bytes = read_bounded(&checked_file(&core.core, fixed.path)?, LIMIT)?;
            validate_template(&bytes, fixed)?;
            templates.insert(fixed.path.to_owned(), bytes);
        }
        for witness in &ledger.authoring_reconstruction_proofs {
            let (_, target) = witness.context.split_once('/').expect("validated witness");
            closed_context(&core, target, &witness.explicit_owner_roots)?;
        }
        for owner in &ledger.proposed_owner_table {
            let definition = core.features.0.get(&owner.feature).ok_or_else(|| {
                eyre::eyre!("unregistered reviewed resource owner: {}", owner.feature)
            })?;
            let expected_support = if owner.feature == "screen_diagnostics" {
                ensure!(
                    owner.proposed_supported_targets == ["1.19.2", "1.19.4"]
                        && owner.required_existing_prerequisites.is_empty()
                        && definition.requires.is_empty(),
                    "historical or current diagnostics prerequisite evidence changed"
                );
                [
                    "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0",
                    "1.21.1", "26.1.2",
                ]
                .map(str::to_owned)
                .into_iter()
                .collect::<BTreeSet<_>>()
            } else {
                owner
                    .proposed_supported_targets
                    .iter()
                    .cloned()
                    .collect::<BTreeSet<_>>()
            };
            ensure!(
                definition
                    .supported_targets
                    .iter()
                    .cloned()
                    .collect::<BTreeSet<_>>()
                    == expected_support
                    && owner
                        .required_existing_prerequisites
                        .iter()
                        .all(|dependency| definition.requires.contains(dependency)),
                "reviewed source support or existing prerequisite drift"
            );
        }
        Ok(Self {
            core,
            ledger,
            raw,
            normalized,
            templates,
        })
    }
    fn isolated(&self) -> Result<Isolated> {
        let temp = tempfile::tempdir()?;
        let root = temp.path().join(CORE_ROOT);
        fs::create_dir_all(&root)?;
        let mut metadata = self.core.metadata.clone();
        metadata
            .source_rules
            .retain(|path, _| path == LANG || path == CHANGELOG);
        metadata.project_files.clear();
        for fixed in TEMPLATES {
            write_fixture(&root, fixed.path, &self.templates[fixed.path])?;
        }
        for output in REQUIRED_PROJECT_FILES {
            metadata.project_files.insert(
                output.to_owned(),
                vec![InputVariant {
                    input: format!("build/isolated-localization-fixture/{output}"),
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
    fn roots(&self, context: &str) -> Result<Vec<String>> {
        let witnesses = self
            .ledger
            .authoring_reconstruction_proofs
            .iter()
            .filter(|w| w.context == context)
            .collect::<Vec<_>>();
        ensure!(witnesses.len() == 2, "missing exact two-resource witness");
        ensure!(
            witnesses[0].explicit_owner_roots == witnesses[1].explicit_owner_roots,
            "one frozen context gained resource-specific defaults"
        );
        Ok(witnesses[0].explicit_owner_roots.clone())
    }
    fn expected_language(&self, target: &str, context: &ProjectionContext) -> Result<Language> {
        let mut expected = parse_language(&self.normalized[expected_oid(LANG, false, target)?])?;
        for key in &self.ledger.language_key_predicates {
            if enabled(
                &key.targets,
                &key.all_features,
                &key.any_features,
                target,
                context,
            )? {
                let identity = format!("dev/{target}");
                let forms = key
                    .literal_forms
                    .iter()
                    .filter(|form| form.contexts.contains(&identity))
                    .collect::<Vec<_>>();
                ensure!(
                    forms.len() == 1,
                    "key literal does not have an exact target witness"
                );
                let value = literal_value(&key.key, &forms[0].line)?;
                expected.0.insert(key.key.clone(), value);
            }
        }
        Ok(expected)
    }
    fn expected_changelog(&self, target: &str, context: &ProjectionContext) -> Result<String> {
        let mut lines = Vec::new();
        for id in &self.ledger.changelog_note_order {
            let note = self
                .ledger
                .changelog_note_predicates
                .iter()
                .find(|note| &note.id == id)
                .expect("validated exact note order");
            let mut selected = 0;
            for variant in &note.variants {
                if enabled(
                    &variant.targets,
                    &variant.all_features,
                    &variant.any_features,
                    target,
                    context,
                )? {
                    selected += 1;
                }
            }
            ensure!(selected <= 1, "overlapping exact-note variants");
            if selected == 1 {
                lines.push(note.text.as_str());
            }
        }
        let mut output = self.ledger.changelog_shared_parts.header.clone();
        if !lines.is_empty() {
            output.push_str(PRE);
            for line in lines {
                output.push_str(line);
                output.push('\n');
            }
        }
        output.push_str(&self.ledger.changelog_shared_parts.suffix);
        Ok(output)
    }
}
struct Isolated {
    _temp: tempfile::TempDir,
    root: PathBuf,
    metadata: CoreProjectInputs,
}
impl Isolated {
    fn collect(&self, context: &ProjectionContext) -> Result<BTreeMap<String, Vec<u8>>> {
        let target = target_id(context);
        for output in REQUIRED_PROJECT_FILES {
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
                "gradle/wrapper/gradle-wrapper.jar" => b"\0\xff\x01source-fixture-only".to_vec(),
                _ => b"source-fixture-only\n".to_vec(),
            };
            write_fixture(
                &self.root,
                &format!("build/isolated-localization-fixture/{output}"),
                &bytes,
            )?;
        }
        let inventory = discover_core_source_files(&self.root)?;
        let selection = select_core_inputs(&self.metadata, context, &inventory)?;
        let artifacts = collect_core_artifacts(&self.root, &selection, context)?;
        let mut outputs = BTreeMap::new();
        for fixed in TEMPLATES {
            let selected = selection
                .inputs
                .get(fixed.path)
                .ok_or_else(|| eyre::eyre!("shared resource was omitted before rendering"))?;
            let artifact = artifacts
                .get(fixed.path)
                .ok_or_else(|| eyre::eyre!("shared resource failed to emit"))?;
            ensure!(
                selected.template
                    && selected.input == fixed.path
                    && artifact.source_path == format!("{CORE_ROOT}/{}", fixed.path)
                    && artifact.overlay.is_none(),
                "resource text mode or core source binding drift"
            );
            outputs.insert(fixed.path.to_owned(), artifact.output_bytes.clone());
        }
        Ok(outputs)
    }
}
fn target_id(context: &ProjectionContext) -> &str {
    if context.minecraft_version == "1.21" {
        "1.21.0"
    } else {
        &context.minecraft_version
    }
}
fn write_fixture(root: &Path, relative: &str, bytes: &[u8]) -> Result<()> {
    let path = root.join(relative);
    fs::create_dir_all(path.parent().expect("fixed fixture parent"))?;
    fs::write(path, bytes)?;
    Ok(())
}
fn parse_language(bytes: &[u8]) -> Result<Language> {
    let input = std::str::from_utf8(bytes)?;
    let mut in_string = false;
    let mut escaped = false;
    for byte in bytes {
        if in_string {
            ensure!(
                *byte >= 0x20,
                "raw JSON control character in localization string"
            );
            if escaped {
                escaped = false;
            } else if *byte == b'\\' {
                escaped = true;
            } else if *byte == b'"' {
                in_string = false;
            }
        } else if *byte == b'"' {
            in_string = true;
        }
    }
    ensure!(
        !in_string && !escaped,
        "unterminated localization JSON string"
    );
    reject_duplicate_catalog_keys(input)?;
    // Facet's String conversion also accepts scalar numbers. Inspect the
    // original JSON tokens first so this localization fixture cannot treat
    // non-string values as an accepted string-only resource schema.
    let values: BTreeMap<String, facet_json::RawJson<'static>> = facet_json::from_str(input)?;
    ensure!(
        values
            .values()
            .all(|value| value.as_str().trim_start().starts_with('"')),
        "localization values must be JSON strings"
    );
    Ok(facet_json::from_str(input)?)
}
fn literal_value(key: &str, line: &str) -> Result<String> {
    let parsed = parse_language(format!("{{\n{line}\n}}").as_bytes())?;
    ensure!(
        parsed.0.len() == 1 && parsed.0.contains_key(key),
        "literal key mismatch"
    );
    Ok(parsed.0[key].clone())
}
fn golden(oid: &str) -> Result<Golden> {
    GOLDENS
        .iter()
        .copied()
        .find(|g| g.oid == oid)
        .ok_or_else(|| eyre::eyre!("blob outside immutable resource witnesses"))
}
fn normalize(bytes: &[u8], fixed: Golden) -> Result<Vec<u8>> {
    ensure!(
        bytes.len() == fixed.raw_bytes
            && sha256(bytes) == fixed.raw_sha256
            && !bytes.contains(&b'\r')
            && bytes.ends_with(b"\n") == fixed.final_lf,
        "raw resource bytes/hash/LF drift"
    );
    ensure!(
        !std::str::from_utf8(bytes)?.starts_with('\u{feff}'),
        "raw resource BOM drift"
    );
    let mut output = bytes.to_vec();
    if fixed.append_lf {
        ensure!(!fixed.final_lf, "unapproved duplicate terminal LF");
        output.push(b'\n');
    }
    ensure!(
        output.len() == fixed.normalized_bytes && sha256(&output) == fixed.normalized_sha256,
        "normalization exceeded exact approval"
    );
    Ok(output)
}
fn validate_template(bytes: &[u8], fixed: Template) -> Result<()> {
    ensure!(
        bytes.len() == fixed.bytes
            && sha256(bytes) == fixed.digest
            && !bytes.contains(&b'\r')
            && bytes.ends_with(b"\n"),
        "authored resource template drift"
    );
    std::str::from_utf8(bytes)?;
    Ok(())
}
fn validate_metadata(metadata: &CoreProjectInputs, path: &str) -> Result<()> {
    ensure!(
        TEMPLATES.iter().any(|fixed| fixed.path == path),
        "outside exact resource cohort"
    );
    let rules = metadata
        .source_rules
        .get(path)
        .ok_or_else(|| eyre::eyre!("shared resource requires explicit controlled-text rule"))?;
    ensure!(
        rules.len() == 1
            && rules[0].input == path
            && rules[0].template
            && rules[0].when == InputPredicate::default(),
        "text mode or all-target membership drift"
    );
    Ok(())
}
fn closed_context(
    core: &CoreTestFixture,
    target: &str,
    roots: &[String],
) -> Result<ProjectionContext> {
    let mut enabled = BTreeSet::new();
    let mut pending = roots.to_vec();
    while let Some(id) = pending.pop() {
        let definition = core
            .features
            .0
            .get(&id)
            .ok_or_else(|| eyre::eyre!("unregistered resource owner: {id}"))?;
        ensure!(
            definition.supported_targets.iter().any(|t| t == target),
            "unsupported resource owner: {id}"
        );
        if enabled.insert(id) {
            pending.extend(definition.requires.iter().cloned());
        }
    }
    core.context(
        target,
        &enabled.iter().map(String::as_str).collect::<Vec<_>>(),
    )
}
fn roots(names: &[&str]) -> Vec<String> {
    names.iter().map(|name| (*name).to_owned()).collect()
}
fn enabled(
    targets: &[String],
    all: &[String],
    any: &[String],
    target: &str,
    context: &ProjectionContext,
) -> Result<bool> {
    for name in all.iter().chain(any) {
        ensure!(
            context.features.contains_key(name),
            "unknown predicate owner: {name}"
        );
    }
    Ok(targets.iter().any(|t| t == target)
        && all.iter().all(|name| context.features[name])
        && (any.is_empty() || any.iter().any(|name| context.features[name])))
}
fn expected_oid(path: &str, development: bool, target: &str) -> Result<&'static str> {
    ensure!(TARGETS.contains(&target), "unknown resource target");
    match path {
        LANG => Ok(match (development, target) {
            (true, "1.19.2") => "961f0c71e84f71521d282b01892ee23fbd749474",
            (true, "1.19.4") => "41bd9bfef95475f4d735bca6fccd82184b687752",
            (true, "1.20.1") => "f40ce5ec8cbda9e4d78addb6fc0b60e1138e6e44",
            (true, "1.20.2" | "1.20.3" | "1.20.4") => "6df874f46ec7a54e054364cf3324201dadef62ec",
            (true, "26.1.2") => "12e0ac969f660698c981600df5f270b80fe8a04b",
            (true, _) => "7ea96baa7aefbfca2472529bfc5ec3df74136f70",
            (false, "1.19.2" | "1.20.1") => "9d335d0d073da71bb8829985590e1ec468ef7e34",
            (false, "1.20.2" | "1.20.3" | "1.20.4") => "1c1a46b9d74e40786458d7f72f2e888b292896dc",
            (false, "26.1.2") => "816f290ef1110fd2a9f8e88d5eee0c9fb5130e15",
            (false, _) => "4a30f00cb3cef02a374dff6dcd05a62d22fdb201",
        }),
        CHANGELOG => Ok(match (development, target) {
            (false, _) => BASE_CHANGELOG,
            (true, "1.19.2") => "b5299272e7078501f69f3c57c5a358b7784f2318",
            (true, "1.19.4") => "e1db48676507aa4af36abbaa358c5964b1a6a1e6",
            (true, _) => "4f3be3a3c9b68e62258afb1fa666ab3d16a4597f",
        }),
        _ => Err(eyre::eyre!("path outside exact resource scope")),
    }
}
fn validate_ledger(ledger: &Ledger) -> Result<()> {
    ensure!(
        ledger.schema == "sfm:core-localization-changelog-slice@1"
            && ledger.paths == [LANG, CHANGELOG]
            && ledger.target_ids == TARGETS,
        "resource ledger schema or target/path scope drift"
    );
    let contexts = ledger
        .frozen_contexts
        .iter()
        .map(|c| (c.context.as_str(), c.source_commit.as_str()))
        .collect::<BTreeMap<_, _>>();
    ensure!(
        ledger.frozen_contexts.len() == 20 && contexts == COMMITS.into_iter().collect(),
        "frozen resource commits drift"
    );
    ensure!(
        ledger.raw_goldens.len() == 14
            && ledger.templates.len() == 2
            && ledger.authoring_reconstruction_proofs.len() == 40
            && ledger.language_key_predicates.len() == 269
            && ledger.language_baseline_key_cases.len() == 10
            && ledger.changelog_note_predicates.len() == 299
            && ledger.changelog_note_order.len() == 299,
        "bounded resource matrix or ownership accounting drift"
    );
    let mut owners = BTreeSet::new();
    for owner in &ledger.proposed_owner_table {
        ensure!(
            owners.insert(owner.feature.as_str()),
            "repeated resource owner"
        );
        validate_targets(&owner.proposed_supported_targets)?;
        for consumer in &owner.source_consumers {
            ensure!(
                consumer.path.starts_with("platform/minecraft/src/")
                    && !consumer.path.contains("..")
                    && !consumer.path.contains('\\'),
                "consumer witness escaped its portable source boundary"
            );
        }
    }
    let note_only = ledger
        .proposed_owner_table
        .iter()
        .find(|owner| owner.feature == "version_adapter_notes")
        .ok_or_else(|| eyre::eyre!("historical note presentation owner missing"))?;
    ensure!(
        note_only.source_consumers.is_empty()
            && note_only
                .tooling_only_status
                .starts_with("Data-only historical migration note;"),
        "historical release note was misrepresented as a Java/runtime owner"
    );
    let mut raw_ids = BTreeSet::new();
    for row in &ledger.raw_goldens {
        let fixed = golden(&row.oid)?;
        ensure!(
            raw_ids.insert(row.oid.as_str())
                && row.raw_sha256 == fixed.raw_sha256
                && row.raw_bytes == fixed.raw_bytes
                && row.raw_final_lf == fixed.final_lf
                && row.crlf_count == 0
                && row.lone_cr_count == 0
                && !row.bom
                && row.normalized_sha256 == fixed.normalized_sha256
                && row.normalized_bytes == fixed.normalized_bytes
                && row.normalization
                    == if fixed.append_lf {
                        "sfm:text_append_terminal_lf@1"
                    } else {
                        "raw_exact"
                    },
            "immutable raw or normalization contract drift"
        );
    }
    let mut template_paths = BTreeSet::new();
    for row in &ledger.templates {
        let fixed = TEMPLATES
            .iter()
            .find(|t| t.path == row.path)
            .ok_or_else(|| eyre::eyre!("unreviewed template path"))?;
        ensure!(
            template_paths.insert(row.path.as_str())
                && row.stage_path == format!("{STAGE_PREFIX}{}", fixed.path)
                && row.sha256 == fixed.digest
                && row.bytes == fixed.bytes
                && row.template
                && !row.production_input,
            "authored-stage template contract drift"
        );
    }
    let mut witness_cells = BTreeSet::new();
    for row in &ledger.authoring_reconstruction_proofs {
        let (environment, target) = row
            .context
            .split_once('/')
            .ok_or_else(|| eyre::eyre!("malformed resource witness context"))?;
        ensure!(
            contexts.get(row.context.as_str()) == Some(&row.source_commit.as_str())
                && witness_cells.insert((row.context.as_str(), row.path.as_str()))
                && row.oid == expected_oid(&row.path, environment == "dev", target)?
                && row.present
                && row.mode == "100644"
                && row.reconstruction_equal
                && row.proof == "literal_authoring_reconstruction_not_production_renderer"
                && row.feature_context_origin
                    == "explicit_review_owner_selection_not_historical_feature_manifest_claim",
            "frozen resource cell evidence drift"
        );
        let expected = if environment == "dev" {
            ledger
                .language_key_predicates
                .iter()
                .filter(|key| key.targets.iter().any(|t| t == target))
                .flat_map(|key| key.all_features.iter().chain(&key.any_features))
                .chain(ledger.changelog_note_predicates.iter().flat_map(|note| {
                    note.variants
                        .iter()
                        .filter(|v| v.targets.iter().any(|t| t == target))
                        .flat_map(|v| v.all_features.iter().chain(&v.any_features))
                }))
                .cloned()
                .collect::<BTreeSet<_>>()
        } else {
            BTreeSet::new()
        };
        ensure!(
            row.explicit_owner_roots.len() == expected.len()
                && row
                    .explicit_owner_roots
                    .iter()
                    .cloned()
                    .collect::<BTreeSet<_>>()
                    == expected,
            "frozen exact explicit owner roots drift"
        );
    }
    let mut key_ids = BTreeSet::new();
    for row in &ledger.language_key_predicates {
        ensure!(
            key_ids.insert(row.key.as_str())
                && !row.targets.is_empty()
                && !row.literal_forms.is_empty()
                && (!row.all_features.is_empty() || !row.any_features.is_empty()),
            "optional key has empty ownership"
        );
        validate_targets(&row.targets)?;
        for form in &row.literal_forms {
            literal_value(&row.key, &form.line)?;
            ensure!(
                !form.contexts.is_empty(),
                "empty literal witness membership"
            );
        }
    }
    for row in &ledger.language_baseline_key_cases {
        validate_targets(&row.targets)?;
        literal_value(&row.key, &row.literal_line)?;
    }
    let ids = ledger
        .changelog_note_predicates
        .iter()
        .map(|n| n.id.as_str())
        .collect::<BTreeSet<_>>();
    let expected_ids = (1..=299)
        .map(|id| format!("note-{id:03}"))
        .collect::<BTreeSet<_>>();
    ensure!(
        ids.len() == 299
            && ids.iter().copied().collect::<BTreeSet<_>>()
                == expected_ids.iter().map(String::as_str).collect()
            && ledger
                .changelog_note_order
                .iter()
                .map(String::as_str)
                .collect::<BTreeSet<_>>()
                == ids,
        "exact changelog note identity/order scope drift"
    );
    for note in &ledger.changelog_note_predicates {
        ensure!(
            note.text.starts_with("-- ") && !note.text.contains('\n') && !note.variants.is_empty(),
            "exact changelog note shape drift"
        );
        for variant in &note.variants {
            validate_targets(&variant.targets)?;
            ensure!(
                !variant.all_features.is_empty() && variant.any_features.is_empty(),
                "unowned or widened compound note"
            );
        }
    }
    Ok(())
}
fn validate_targets(targets: &[String]) -> Result<()> {
    ensure!(
        !targets.is_empty()
            && targets.iter().all(|t| TARGETS.contains(&t.as_str()))
            && targets.iter().collect::<BTreeSet<_>>().len() == targets.len(),
        "unknown/repeated/empty resource target membership"
    );
    Ok(())
}

#[test]
fn localization_changelog_collects_all_forty_frozen_cells_exactly() -> Result<()> {
    let fixture = Fixture::load()?;
    let isolated = fixture.isolated()?;
    let base = std::str::from_utf8(&fixture.raw[BASE_CHANGELOG])?;
    assert_eq!(
        base,
        format!(
            "{}{}",
            fixture.ledger.changelog_shared_parts.header,
            fixture.ledger.changelog_shared_parts.suffix
        )
    );
    let mut count = 0;
    for (identity, _) in COMMITS {
        let (environment, target) = identity.split_once('/').expect("fixed context");
        let context = closed_context(&fixture.core, target, &fixture.roots(identity)?)?;
        let outputs = isolated.collect(&context)?;
        for fixed in TEMPLATES {
            let oid = expected_oid(fixed.path, environment == "dev", target)?;
            assert_eq!(
                outputs[fixed.path], fixture.normalized[oid],
                "{identity} / {}",
                fixed.path
            );
            count += 1;
        }
        assert_eq!(
            parse_language(&outputs[LANG])?,
            fixture.expected_language(target, &context)?
        );
        assert_eq!(
            std::str::from_utf8(&outputs[CHANGELOG])?,
            fixture.expected_changelog(target, &context)?
        );
    }
    assert_eq!(count, 40);
    Ok(())
}
#[test]
fn localization_changelog_independent_data_owners_and_json_boundaries() -> Result<()> {
    let fixture = Fixture::load()?;
    let isolated = fixture.isolated()?;
    let independent = [
        "editor_pointer_actions",
        "editor_search",
        "explorer_navigation",
        "screen_diagnostics",
        "keybinding_settings",
        "terminal_focus_gestures",
        "tooltip_mode_override",
    ];
    for target in &TARGETS[..2] {
        for mask in 0..128u8 {
            let owner_roots = independent
                .iter()
                .enumerate()
                .filter(|(bit, _)| mask & (1 << bit) != 0)
                .map(|(_, name)| (*name).to_owned())
                .collect::<Vec<_>>();
            let context = closed_context(&fixture.core, target, &owner_roots)?;
            let outputs = isolated.collect(&context)?;
            assert_eq!(
                parse_language(&outputs[LANG])?,
                fixture.expected_language(target, &context)?
            );
            let actual = std::str::from_utf8(&outputs[CHANGELOG])?;
            assert_eq!(actual, fixture.expected_changelog(target, &context)?);
            assert!(actual.ends_with(&fixture.ledger.changelog_shared_parts.suffix));
        }
        for owner_roots in [
            vec![],
            roots(&["computercraft"]),
            roots(&["touch_display"]),
            roots(&["computercraft", "touch_display"]),
        ] {
            let context = closed_context(&fixture.core, target, &owner_roots)?;
            let outputs = isolated.collect(&context)?;
            assert_eq!(
                parse_language(&outputs[LANG])?,
                fixture.expected_language(target, &context)?
            );
        }
    }
    for target in TARGETS {
        for owner_roots in [
            vec![],
            roots(&["computercraft"]),
            roots(&["terminal_focus_gestures"]),
        ] {
            let context = closed_context(&fixture.core, target, &owner_roots)?;
            let outputs = isolated.collect(&context)?;
            assert_eq!(
                parse_language(&outputs[LANG])?,
                fixture.expected_language(target, &context)?
            );
            assert_eq!(
                std::str::from_utf8(&outputs[CHANGELOG])?,
                fixture.expected_changelog(target, &context)?
            );
        }
    }
    for owner_roots in [vec![], roots(&["mekanism_sidedness_messages"])] {
        let context = closed_context(&fixture.core, "26.1.2", &owner_roots)?;
        let outputs = isolated.collect(&context)?;
        let lang = parse_language(&outputs[LANG])?;
        assert_eq!(lang, fixture.expected_language("26.1.2", &context)?);
        assert_eq!(
            lang.0
                .contains_key("program.sfm.warnings.mekanism_bad_side_config"),
            context.features["mekanism_sidedness_messages"]
        );
    }
    Ok(())
}
#[test]
fn localization_changelog_compound_notes_do_not_emit_empty_pre_headers() -> Result<()> {
    let fixture = Fixture::load()?;
    let isolated = fixture.isolated()?;
    for target in &TARGETS[..2] {
        for owner_roots in [
            vec![],
            roots(&["client_manager"]),
            roots(&["touch_display"]),
            roots(&["client_manager", "touch_display"]),
            roots(&["terminal_local"]),
            roots(&["terminal_vox_runtime"]),
            roots(&["terminal_local", "terminal_vox_runtime"]),
        ] {
            let context = closed_context(&fixture.core, target, &owner_roots)?;
            let outputs = isolated.collect(&context)?;
            let text = std::str::from_utf8(&outputs[CHANGELOG])?;
            let expected = fixture.expected_changelog(target, &context)?;
            assert_eq!(text, expected);
            assert_eq!(text.contains(PRE), expected.contains(PRE));
            assert!(text.matches(PRE).count() <= 1);
        }
    }
    for target in &TARGETS[2..] {
        for owner_roots in [
            vec![],
            roots(&["terminal_local"]),
            roots(&["terminal_vox_runtime"]),
            roots(&["terminal_local", "terminal_vox_runtime"]),
            roots(&["keyboard_profiles"]),
            roots(&["client_theme"]),
            roots(&["input_diagnostics_screen"]),
        ] {
            let context = closed_context(&fixture.core, target, &owner_roots)?;
            let outputs = isolated.collect(&context)?;
            assert_eq!(
                std::str::from_utf8(&outputs[CHANGELOG])?,
                fixture.expected_changelog(target, &context)?
            );
        }
    }
    Ok(())
}
#[test]
fn localization_changelog_shared_edits_reach_all_targets_not_environment_keys() -> Result<()> {
    let fixture = Fixture::load()?;
    let isolated = fixture.isolated()?;
    let source = std::str::from_utf8(&fixture.templates[LANG])?;
    let old = "\"block.sfm.manager\": \"Factory Manager\"";
    let new = "\"block.sfm.manager\": \"Shared resource edit\"";
    assert_eq!(source.matches(old).count(), 1);
    write_fixture(&isolated.root, LANG, source.replace(old, new).as_bytes())?;
    let source = std::str::from_utf8(&fixture.templates[CHANGELOG])?;
    let changed = format!("-- isolated shared resource edit\n{source}");
    write_fixture(&isolated.root, CHANGELOG, changed.as_bytes())?;
    for target in TARGETS {
        let context = closed_context(&fixture.core, target, &[])?;
        let outputs = isolated.collect(&context)?;
        assert_eq!(
            parse_language(&outputs[LANG])?.0["block.sfm.manager"],
            "Shared resource edit"
        );
        assert!(outputs[CHANGELOG].starts_with(b"-- isolated shared resource edit\n"));
        let mut renamed = context.clone();
        renamed.environment = "dev".to_owned();
        renamed.projection_key = "arbitrary/nested/resource-fixture".to_owned();
        renamed.preset = renamed.projection_key.clone();
        assert_eq!(isolated.collect(&renamed)?, outputs);
    }
    Ok(())
}
#[test]
fn localization_changelog_hashes_modes_normalization_unknowns_refuse() -> Result<()> {
    let fixture = Fixture::load()?;
    for fixed in GOLDENS {
        let mut altered = fixture.raw[fixed.oid].clone();
        altered.push(b' ');
        assert!(normalize(&altered, fixed).is_err());
        let mut wrong = fixed;
        wrong.append_lf = !fixed.append_lf;
        assert!(normalize(&fixture.raw[fixed.oid], wrong).is_err());
    }
    for fixed in TEMPLATES {
        let mut bytes = fixture.templates[fixed.path].clone();
        bytes.push(b' ');
        assert!(validate_template(&bytes, fixed).is_err());
        let mut metadata = fixture.core.metadata.clone();
        metadata
            .source_rules
            .get_mut(fixed.path)
            .expect("owned resource")[0]
            .template = false;
        assert!(validate_metadata(&metadata, fixed.path).is_err());
        metadata
            .source_rules
            .get_mut(fixed.path)
            .expect("owned resource")[0]
            .template = true;
        metadata
            .source_rules
            .get_mut(fixed.path)
            .expect("owned resource")[0]
            .input = format!("fragments/unreviewed/{}", fixed.path);
        assert!(validate_metadata(&metadata, fixed.path).is_err());
        let context = closed_context(&fixture.core, "1.19.2", &[])?;
        let unknown = format!(
            "{{% if features.absent_resource_owner %}}\n{}{{% endif %}}\n",
            std::str::from_utf8(&fixture.templates[fixed.path])?
        );
        assert!(render_java_source(&unknown, &context).is_err());
        let isolated = fixture.isolated()?;
        write_fixture(&isolated.root, fixed.path, unknown.as_bytes())?;
        assert!(isolated.collect(&context).is_err());
    }
    assert!(parse_language(br#"{"a":"one","a":"two"}"#).is_err());
    assert!(parse_language(br#"{"a":"one","\u0061":"two"}"#).is_err());
    assert!(parse_language(br#"{"a":42}"#).is_err());
    assert!(parse_language(br#"{"a":1e2}"#).is_err());
    assert!(parse_language(br#"{"a":true}"#).is_err());
    assert!(parse_language(br#"{"a":null}"#).is_err());
    assert!(parse_language(br#"{"a":[]}"#).is_err());
    assert!(parse_language(br#"{"a":{}}"#).is_err());
    assert!(parse_language(b"{\"a\":\"raw\nnewline\"}").is_err());
    assert!(parse_language(b"{\"raw\nkey\":\"value\"}").is_err());
    assert!(parse_language(b"{\"a\":\"raw\ttab\"}").is_err());
    assert!(parse_language(br#"{"a":"escaped\nnewline and \"quote\""}"#).is_ok());
    let temp = tempfile::NamedTempFile::new()?;
    temp.as_file().set_len(LIMIT + 1)?;
    assert!(read_bounded(temp.path(), LIMIT).is_err());
    assert!(closed_context(&fixture.core, "1.19.2", &roots(&["absent_resource_owner"])).is_err());
    for name in [
        "editor_pointer_actions",
        "editor_search",
        "keybinding_settings",
        "input_diagnostics_panel",
    ] {
        assert!(closed_context(&fixture.core, "26.1.2", &roots(&[name])).is_err());
    }
    for target in TARGETS {
        let context = closed_context(&fixture.core, target, &roots(&["screen_diagnostics"]))?;
        assert!(context.features["screen_diagnostics"]);
        assert!(!context.features["workspace_panels"] && !context.features["client_actions"]);
    }
    for name in ["keybinding_settings", "client_action_help", "echo_action"] {
        let complete = closed_context(&fixture.core, "1.19.2", &roots(&[name]))?;
        for dependency in &fixture.core.features.0[name].requires {
            let missing = complete
                .features
                .iter()
                .filter(|(id, value)| **value && *id != dependency)
                .map(|(id, _)| id.as_str())
                .collect::<Vec<_>>();
            assert!(fixture.core.context("1.19.2", &missing).is_err());
        }
    }
    let mut ledger: Ledger = facet_json::from_str(std::str::from_utf8(&read_bounded(
        &checked_file(&fixture.core.repository, LEDGER)?,
        LIMIT,
    )?)?)?;
    ledger.raw_goldens[0].normalized_sha256.push('0');
    assert!(validate_ledger(&ledger).is_err());
    Ok(())
}
