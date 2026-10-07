//! Post-promotion real selection/text-collection tests for five resource inputs.
//!
//! Historical blobs are bounded test witnesses only. Synthetic isolated Gradle
//! files allow the real collector to run; they do not attest dependency locks,
//! native compilation, Gradle builds, JAR parity or gameplay.

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
use super::provenance::sha256;
use eyre::Result;
use eyre::ensure;
use facet::Facet;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::fs;
use std::path::PathBuf;

const LEDGER: &str = "docs/tasks/sfm-core-resource-first-slice.json";
// Independent collector-contract fixture: not imported from the private
// production constant, and not a dependency or build-provenance witness.
const REQUIRED_PROJECT_FILES: &[&str] = &[
    "build.gradle",
    "settings.gradle",
    "gradle.properties",
    "gradlew",
    "gradlew.bat",
    "sfm-toolchain.lock.json",
    "gradle/wrapper/gradle-wrapper.properties",
    "gradle/wrapper/gradle-wrapper.jar",
];
const STAGE_PREFIX: &str =
    "platform/cli/sfm-propagate-changes/target/core-resource-first-stage-v1/";
const CC: &str = "computercraft";
const LIVE: &str = "redstone_live_read";
const BUFFER: &str = "redstone_buffer_storage";
const IMAGE: &str = "image_resources";
const CLIENT: &str = "client_manager";
const TOUCH: &str = "touch_display";
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
    raw_final_lf: bool,
    append_lf: bool,
    output_sha256: &'static str,
    output_bytes: usize,
}
const GOLDENS: [Golden; 19] = [
    Golden {
        oid: "67b59c05733a52196c3ac2695b2ef3bef1e4eb2b",
        raw_sha256: "sha256:5e48c7f15827fbb8c32bdfb92a34a7ce60d00f09d3531b9d791e49521cd71131",
        raw_bytes: 4552,
        raw_final_lf: true,
        append_lf: false,
        output_sha256: "sha256:5e48c7f15827fbb8c32bdfb92a34a7ce60d00f09d3531b9d791e49521cd71131",
        output_bytes: 4552,
    },
    Golden {
        oid: "2d5c71126247a8e078971354cbecfc669b965cdc",
        raw_sha256: "sha256:886739ef92e52a067561afe37c2d92bb837eebb4f89d7d602c86894c8ad6a5ba",
        raw_bytes: 4457,
        raw_final_lf: true,
        append_lf: false,
        output_sha256: "sha256:886739ef92e52a067561afe37c2d92bb837eebb4f89d7d602c86894c8ad6a5ba",
        output_bytes: 4457,
    },
    Golden {
        oid: "4af3937238f80c3fd45ab140e4dd7c72bdd34a00",
        raw_sha256: "sha256:ac064ba5fc0f8d166ea916616e3fc484f0901efc7310e2b59950810e6eca9c90",
        raw_bytes: 4551,
        raw_final_lf: true,
        append_lf: false,
        output_sha256: "sha256:ac064ba5fc0f8d166ea916616e3fc484f0901efc7310e2b59950810e6eca9c90",
        output_bytes: 4551,
    },
    Golden {
        oid: "7aeef2684bde6e5ea4ba4772a763523f13c1db72",
        raw_sha256: "sha256:4084eb6c3f4b837d2b0f296c55ff8f8b6089c364d48ad65a77329deb4fd434bd",
        raw_bytes: 4554,
        raw_final_lf: true,
        append_lf: false,
        output_sha256: "sha256:4084eb6c3f4b837d2b0f296c55ff8f8b6089c364d48ad65a77329deb4fd434bd",
        output_bytes: 4554,
    },
    Golden {
        oid: "01c83bc811df9ab5b47fbc84966a6e7c773c77b3",
        raw_sha256: "sha256:871f6d7254b59f80aca1725bb773c67e69c37670d9beff40e0b95eaf21f982a0",
        raw_bytes: 4539,
        raw_final_lf: true,
        append_lf: false,
        output_sha256: "sha256:871f6d7254b59f80aca1725bb773c67e69c37670d9beff40e0b95eaf21f982a0",
        output_bytes: 4539,
    },
    Golden {
        oid: "df9beecb20c2207da3f7e59d408bff39fe392aba",
        raw_sha256: "sha256:61099b038d393d3c803cc1e5f514a9d729e5749b7748b6ce020b56b41b0c5a03",
        raw_bytes: 4419,
        raw_final_lf: true,
        append_lf: false,
        output_sha256: "sha256:61099b038d393d3c803cc1e5f514a9d729e5749b7748b6ce020b56b41b0c5a03",
        output_bytes: 4419,
    },
    Golden {
        oid: "09684e8f729a1f8f55f8712ee1c5faea504d2084",
        raw_sha256: "sha256:7fc0af2fab602c863e181a018fc2e7e1f161e1652f6e69c7b44a626301eafa86",
        raw_bytes: 4325,
        raw_final_lf: true,
        append_lf: false,
        output_sha256: "sha256:7fc0af2fab602c863e181a018fc2e7e1f161e1652f6e69c7b44a626301eafa86",
        output_bytes: 4325,
    },
    Golden {
        oid: "aae89f3b712a66c55be89444fc28a606e18ecf0f",
        raw_sha256: "sha256:841b4f7bf81d5eb5efebe4742e164567b52d2ecff38b905ca891bf38bcac46c5",
        raw_bytes: 4422,
        raw_final_lf: true,
        append_lf: false,
        output_sha256: "sha256:841b4f7bf81d5eb5efebe4742e164567b52d2ecff38b905ca891bf38bcac46c5",
        output_bytes: 4422,
    },
    Golden {
        oid: "23e9254da5964f30914bd20415c80a3836f9b39b",
        raw_sha256: "sha256:71765b03633a6e266ffaa5b7e4d40e91836dc3d6059359641f84b43dc55eff77",
        raw_bytes: 4407,
        raw_final_lf: true,
        append_lf: false,
        output_sha256: "sha256:71765b03633a6e266ffaa5b7e4d40e91836dc3d6059359641f84b43dc55eff77",
        output_bytes: 4407,
    },
    Golden {
        oid: "a9753a40d5459ed27020e895717a720d78d6d063",
        raw_sha256: "sha256:2091aaf4ffc5e3649e13f460366c18f82fc0ff5e2378806ef9796930b1ef26d0",
        raw_bytes: 4358,
        raw_final_lf: true,
        append_lf: false,
        output_sha256: "sha256:2091aaf4ffc5e3649e13f460366c18f82fc0ff5e2378806ef9796930b1ef26d0",
        output_bytes: 4358,
    },
    Golden {
        oid: "c652df7858e7f85de82484633a1ad120e0097d92",
        raw_sha256: "sha256:ee385b23a2ec38df18a86e71270081270af8a275d2ea62441bcf8d6bf632d16b",
        raw_bytes: 4358,
        raw_final_lf: true,
        append_lf: false,
        output_sha256: "sha256:ee385b23a2ec38df18a86e71270081270af8a275d2ea62441bcf8d6bf632d16b",
        output_bytes: 4358,
    },
    Golden {
        oid: "c7aee3c06585439728b4b87c6788aa7d394f9738",
        raw_sha256: "sha256:7b50032d6ff7765280c55ca318ed186ade37eaec7ad460ae55347e2ab5ec0960",
        raw_bytes: 4227,
        raw_final_lf: true,
        append_lf: false,
        output_sha256: "sha256:7b50032d6ff7765280c55ca318ed186ade37eaec7ad460ae55347e2ab5ec0960",
        output_bytes: 4227,
    },
    Golden {
        oid: "e0cd89768edf806372b6d50c63fc3b47f0ae2b17",
        raw_sha256: "sha256:2ac47f8338030447867e917e3ef31a739efc930870512412c753fd5a2a49df09",
        raw_bytes: 4227,
        raw_final_lf: true,
        append_lf: false,
        output_sha256: "sha256:2ac47f8338030447867e917e3ef31a739efc930870512412c753fd5a2a49df09",
        output_bytes: 4227,
    },
    Golden {
        oid: "d076222a8578a36932ae85a10a4300c5eabecf98",
        raw_sha256: "sha256:96094d8f1090e4ba200dc3e7cfbb48c7496b57f7fc77d10fd46cf53ce73f715d",
        raw_bytes: 1507,
        raw_final_lf: true,
        append_lf: false,
        output_sha256: "sha256:96094d8f1090e4ba200dc3e7cfbb48c7496b57f7fc77d10fd46cf53ce73f715d",
        output_bytes: 1507,
    },
    Golden {
        oid: "6db14349b9015d13dc6d064a1c32dd80de7ae90e",
        raw_sha256: "sha256:5df9366498f12d3e0c110640c3c7b7fb84a5a514f3e3dfdb751ec64cd2ddd8bf",
        raw_bytes: 244,
        raw_final_lf: true,
        append_lf: false,
        output_sha256: "sha256:5df9366498f12d3e0c110640c3c7b7fb84a5a514f3e3dfdb751ec64cd2ddd8bf",
        output_bytes: 244,
    },
    Golden {
        oid: "572e058ea8dc31b989222a6bda9066763ecbe804",
        raw_sha256: "sha256:3ff0c99b7af8882e475e896525e66c42cdda7e28d468572a7887edcb186eac5d",
        raw_bytes: 535,
        raw_final_lf: false,
        append_lf: true,
        output_sha256: "sha256:5b22efd7442d0262702cdcb43685f60981333f73d51bd7d0bb2e2b22bf4c1de9",
        output_bytes: 536,
    },
    Golden {
        oid: "ada518e5706d972c3334fd2c749f0ea5150079ea",
        raw_sha256: "sha256:c49a6045b7498bf88ec4b4f28e7519299f50b3ec131cd36fadeafa0f43c751c0",
        raw_bytes: 464,
        raw_final_lf: false,
        append_lf: true,
        output_sha256: "sha256:5e3b802057ed1af8e890a4ae8cfeb436ecddbbe809687690b36af86169f824de",
        output_bytes: 465,
    },
    Golden {
        oid: "4732243cde87534fbae7371a7eb2455f76a45516",
        raw_sha256: "sha256:e6cffffa4ba07f1eedf75113bb6334e4efa77bf22d576d06c181528aa139a6b0",
        raw_bytes: 491,
        raw_final_lf: false,
        append_lf: true,
        output_sha256: "sha256:20ef444f4ed44a5ccc35dd3c3b041e93005095de08786f3b4696498c44fc326f",
        output_bytes: 492,
    },
    Golden {
        oid: "ba2d99494b4163a121da0b8d15ac56f566d42fdd",
        raw_sha256: "sha256:e2a51b53cdfffa98b1f13ec20f9116c46cb26d52728501895f6bbcb895882747",
        raw_bytes: 440,
        raw_final_lf: false,
        append_lf: true,
        output_sha256: "sha256:f4588cade57ed55a77c7db54746f9ff1d136fa894225e2f847f4937661ce9876",
        output_bytes: 441,
    },
];
#[derive(Clone, Copy)]
struct Resource {
    path: &'static str,
    template_sha256: &'static str,
    template_bytes: usize,
}
const RESOURCES: [Resource; 5] = [
    Resource {
        path: "src/generated/resources/assets/sfm/blockstates/buffer.json",
        template_sha256: "sha256:183d4ca26cc90401f71cfcbf5df552f260a99b5e4181921d4879635394753238",
        template_bytes: 582,
    },
    Resource {
        path: "src/generated/resources/data/minecraft/tags/blocks/mineable/pickaxe.json",
        template_sha256: "sha256:08c26631ea4eabd33f7e910bf0d9651e7a94cb76548ae8fa9f0c16ea77143f09",
        template_bytes: 581,
    },
    Resource {
        path: "src/main/resources/assets/sfm/template_programs/redstone_signals.sfml",
        template_sha256: "sha256:0fd343fa2dcd19256d5ae7db1fa247933af82761fc17e6100f9ffb2ddf3f60da",
        template_bytes: 2022,
    },
    Resource {
        path: "src/main/resources/META-INF/mods.toml",
        template_sha256: "sha256:f6110e2a272d98613491ef403c1b519e61e5671dcc5a28df4dbfdddedac8c3a8",
        template_bytes: 5384,
    },
    Resource {
        path: "src/main/resources/META-INF/neoforge.mods.toml",
        template_sha256: "sha256:0af64b185417578ad4e6f039b82c330f17a4511612484241ab6c66dd21243de5",
        template_bytes: 4503,
    },
];
#[derive(Facet)]
struct Ledger {
    schema: String,
    context_commits: BTreeMap<String, String>,
    files: Vec<FileEvidence>,
}
#[derive(Facet)]
struct FileEvidence {
    path: String,
    intended_core_path: String,
    stage_path: String,
    template_sha256: String,
    template_bytes: usize,
    metadata_rule: InputVariant,
    raw_variants: Vec<RawEvidence>,
    witnesses: Vec<Witness>,
}
#[derive(Facet)]
struct RawEvidence {
    oid: String,
    raw_sha256: String,
    raw_bytes: usize,
    crlf_count: usize,
    lone_cr_count: usize,
    raw_final_lf: bool,
    bom: bool,
    normalization: String,
    normalized_sha256: String,
    normalized_bytes: usize,
}
#[derive(Facet)]
struct Witness {
    context: String,
    source_commit: String,
    present: bool,
    mode: Option<String>,
    oid: Option<String>,
    explicit_owner_roots: Vec<String>,
    reconstruction_equal: Option<bool>,
    proof: String,
    feature_context_origin: String,
}
#[derive(Facet)]
struct Model {
    model: String,
}
#[derive(Facet)]
struct BufferModels {
    variants: BTreeMap<String, Model>,
}
#[derive(Facet)]
struct PickaxeTag {
    values: Vec<String>,
}

struct Fixture {
    core: CoreTestFixture,
    raw: BTreeMap<String, Vec<u8>>,
    normalized: BTreeMap<String, Vec<u8>>,
}
impl Fixture {
    fn load() -> Result<Self> {
        let core = CoreTestFixture::load()?;
        let ledger_bytes = read_bounded(&checked_file(&core.repository, LEDGER)?, 1024 * 1024)?;
        let ledger: Ledger = facet_json::from_str(std::str::from_utf8(&ledger_bytes)?)?;
        ensure!(
            ledger.schema == "sfm:core_resource_first_slice@1",
            "resource ledger schema drift"
        );
        let commits: BTreeMap<String, String> = COMMITS
            .into_iter()
            .map(|(c, id)| (c.to_owned(), id.to_owned()))
            .collect();
        ensure!(
            ledger.context_commits == commits && ledger.files.len() == RESOURCES.len(),
            "resource ledger scope drift"
        );
        let mut paths = BTreeSet::new();
        let mut oids = BTreeSet::new();
        let mut cells = (0, 0);
        for file in ledger.files {
            let resource = resource(&file.path)?;
            verify_metadata(&core.metadata, resource)?;
            ensure!(
                paths.insert(file.path.clone())
                    && file.intended_core_path == format!("{CORE_ROOT}/{}", resource.path)
                    && file.stage_path == format!("{STAGE_PREFIX}{}", resource.path)
                    && file.template_sha256 == resource.template_sha256
                    && file.template_bytes == resource.template_bytes
                    && core.metadata.source_rules[resource.path] == [file.metadata_rule],
                "resource authored owner or exact mode/membership drift"
            );
            let expected_variants = match resource.path {
                MODS => 9,
                NEO => 4,
                _ => 2,
            };
            ensure!(
                file.raw_variants.len() == expected_variants && file.witnesses.len() == 20,
                "resource matrix incomplete"
            );
            let mut file_oids = BTreeSet::new();
            for raw in file.raw_variants {
                let fixed = golden(&raw.oid)?;
                ensure!(
                    file_oids.insert(raw.oid.clone())
                        && oids.insert(raw.oid)
                        && raw.raw_sha256 == fixed.raw_sha256
                        && raw.raw_bytes == fixed.raw_bytes
                        && raw.crlf_count == 0
                        && raw.lone_cr_count == 0
                        && !raw.bom
                        && raw.raw_final_lf == fixed.raw_final_lf
                        && raw.normalization
                            == if fixed.append_lf {
                                "sfm:text_append_terminal_lf@1"
                            } else {
                                "raw_exact"
                            }
                        && raw.normalized_sha256 == fixed.output_sha256
                        && raw.normalized_bytes == fixed.output_bytes,
                    "resource raw witness or normalization approval drift"
                );
            }
            let mut contexts = BTreeSet::new();
            for witness in file.witnesses {
                let (environment, target) = witness
                    .context
                    .split_once('/')
                    .ok_or_else(|| eyre::eyre!("malformed frozen resource context"))?;
                let oid = expected_oid(resource.path, environment == "dev", target)?;
                ensure!(
                    contexts.insert(witness.context.clone())
                        && commits.get(&witness.context) == Some(&witness.source_commit)
                        && witness.present == oid.is_some()
                        && witness.oid.as_deref() == oid
                        && witness.feature_context_origin
                            == "explicit_review_owner_selection_not_historical_feature_manifest_claim",
                    "resource frozen context or membership drift"
                );
                if let Some(oid) = oid {
                    ensure!(
                        witness.mode.as_deref() == Some("100644")
                            && file_oids.contains(oid)
                            && witness.reconstruction_equal == Some(true)
                            && witness.proof
                                == "literal_authoring_reconstruction_not_production_renderer"
                            && same_names(
                                &witness.explicit_owner_roots,
                                &witness_roots(environment, target)
                            ),
                        "resource present witness evidence drift"
                    );
                    cells.0 += 1;
                } else {
                    ensure!(
                        witness.mode.is_none()
                            && witness.reconstruction_equal.is_none()
                            && witness.explicit_owner_roots.is_empty()
                            && witness.proof
                                == "frozen_tree_membership_absence_not_renderer_omission_proof",
                        "resource absence gained historical bytes"
                    );
                    cells.1 += 1;
                }
                closed_context(&core, target, &witness_roots(environment, target))?;
            }
            ensure!(
                contexts == commits.keys().cloned().collect(),
                "resource twenty-context scope drift"
            );
        }
        ensure!(
            paths.len() == 5 && oids.len() == 19 && cells == (74, 26),
            "resource frozen coverage drift"
        );
        let raw = read_git_blobs(&core.repository, &oids)?;
        let mut normalized = BTreeMap::new();
        for fixed in GOLDENS {
            normalized.insert(fixed.oid.to_owned(), normalize_raw(&raw[fixed.oid], fixed)?);
        }
        for resource in RESOURCES {
            let bytes = read_bounded(&checked_file(&core.core, resource.path)?, 1024 * 1024)?;
            verify_template(&bytes, resource)?;
        }
        Ok(Self {
            core,
            raw,
            normalized,
        })
    }
    fn isolated(&self) -> Result<Isolated> {
        let temp = tempfile::tempdir()?;
        let root = temp.path().join(CORE_ROOT);
        fs::create_dir_all(&root)?;
        let mut metadata = self.core.metadata.clone();
        metadata
            .source_rules
            .retain(|path, _| RESOURCES.iter().any(|r| r.path == path));
        metadata.project_files.clear();
        for resource in RESOURCES {
            let bytes = read_bounded(&checked_file(&self.core.core, resource.path)?, 1024 * 1024)?;
            write_fixture(&root, resource.path, &bytes)?;
        }
        for output in REQUIRED_PROJECT_FILES {
            let input = format!("build/isolated-source-fixture/{output}");
            metadata.project_files.insert(
                (*output).to_owned(),
                vec![InputVariant {
                    input,
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
        for output in REQUIRED_PROJECT_FILES {
            let bytes = match *output {
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
                &format!("build/isolated-source-fixture/{output}"),
                &bytes,
            )?;
        }
        let inventory = discover_core_source_files(&self.root)?;
        let selection = select_core_inputs(&self.metadata, context, &inventory)?;
        let artifacts = collect_core_artifacts(&self.root, &selection, context)?;
        let mut outputs = BTreeMap::new();
        for resource in RESOURCES {
            if let Some(artifact) = artifacts.get(resource.path) {
                ensure!(
                    selection.inputs[resource.path].template
                        && selection.inputs[resource.path].input == resource.path
                        && artifact.source_path == format!("{CORE_ROOT}/{}", resource.path)
                        && artifact.overlay.is_none(),
                    "resource text mode or authored source binding drift"
                );
                outputs.insert(resource.path.to_owned(), artifact.output_bytes.clone());
            } else {
                ensure!(
                    selection.omitted_paths.contains(resource.path),
                    "unowned resource omission"
                );
            }
        }
        Ok(outputs)
    }
}
const MODS: &str = "src/main/resources/META-INF/mods.toml";
const NEO: &str = "src/main/resources/META-INF/neoforge.mods.toml";
const REDSTONE: &str = "src/main/resources/assets/sfm/template_programs/redstone_signals.sfml";
const MODELS: &str = "src/generated/resources/assets/sfm/blockstates/buffer.json";
const PICKAXE: &str = "src/generated/resources/data/minecraft/tags/blocks/mineable/pickaxe.json";
fn write_fixture(root: &std::path::Path, relative: &str, bytes: &[u8]) -> Result<()> {
    let path = root.join(relative);
    fs::create_dir_all(path.parent().expect("fixed fixture parent"))?;
    fs::write(path, bytes)?;
    Ok(())
}
fn resource(path: &str) -> Result<Resource> {
    RESOURCES
        .iter()
        .copied()
        .find(|r| r.path == path)
        .ok_or_else(|| eyre::eyre!("resource outside five-file cohort"))
}
fn golden(oid: &str) -> Result<Golden> {
    GOLDENS
        .iter()
        .copied()
        .find(|g| g.oid == oid)
        .ok_or_else(|| eyre::eyre!("resource blob outside immutable witness set"))
}
fn is_d2(target: &str) -> bool {
    matches!(target, "1.19.2" | "1.19.4")
}
fn is_older(target: &str) -> bool {
    TARGETS[..7].contains(&target)
}
fn same_names(actual: &[String], expected: &[&str]) -> bool {
    actual.len() == expected.len()
        && actual.iter().map(String::as_str).collect::<BTreeSet<_>>()
            == expected.iter().copied().collect()
}
fn witness_roots(environment: &str, target: &str) -> Vec<&'static str> {
    if environment != "dev" {
        vec![]
    } else if is_d2(target) {
        vec![CC, LIVE, BUFFER, CLIENT, TOUCH, IMAGE]
    } else {
        vec![CC]
    }
}
fn closed_context(
    core: &CoreTestFixture,
    target: &str,
    roots: &[&str],
) -> Result<ProjectionContext> {
    let mut names = BTreeSet::new();
    let mut pending = roots
        .iter()
        .map(|name| (*name).to_owned())
        .collect::<Vec<_>>();
    while let Some(name) = pending.pop() {
        let definition = core
            .features
            .0
            .get(&name)
            .ok_or_else(|| eyre::eyre!("unknown requested resource owner: {name}"))?;
        ensure!(
            definition.supported_targets.iter().any(|id| id == target),
            "unsupported resource owner: {name}"
        );
        if names.insert(name) {
            pending.extend(definition.requires.iter().cloned());
        }
    }
    core.context(
        target,
        &names.iter().map(String::as_str).collect::<Vec<_>>(),
    )
}
fn verify_metadata(metadata: &CoreProjectInputs, resource: Resource) -> Result<()> {
    let rule = metadata
        .source_rules
        .get(resource.path)
        .ok_or_else(|| eyre::eyre!("resource requires explicit text mode/membership rule"))?;
    let expected_targets: &[&str] = match resource.path {
        MODS | PICKAXE => &TARGETS[..7],
        NEO => &TARGETS[7..],
        _ => &[],
    };
    ensure!(
        rule.len() == 1
            && rule[0].input == resource.path
            && rule[0].template
            && same_names(&rule[0].when.targets, expected_targets)
            && rule[0].when.all_features.is_empty()
            && rule[0].when.any_features.is_empty()
            && rule[0].when.none_features.is_empty(),
        "resource text mode or genuine target membership rule drift"
    );
    Ok(())
}
fn verify_template(bytes: &[u8], resource: Resource) -> Result<()> {
    ensure!(
        bytes.len() == resource.template_bytes
            && sha256(bytes) == resource.template_sha256
            && !bytes.contains(&b'\r')
            && bytes.ends_with(b"\n"),
        "resource authored template drift"
    );
    std::str::from_utf8(bytes)?;
    Ok(())
}
fn normalize_raw(bytes: &[u8], fixed: Golden) -> Result<Vec<u8>> {
    ensure!(
        bytes.len() == fixed.raw_bytes
            && sha256(bytes) == fixed.raw_sha256
            && !bytes.contains(&b'\r')
            && bytes.ends_with(b"\n") == fixed.raw_final_lf,
        "resource raw bytes or LF boundary drift"
    );
    let raw = std::str::from_utf8(bytes)?;
    ensure!(!raw.starts_with('\u{feff}'), "resource raw BOM drift");
    let mut normalized = bytes.to_vec();
    if fixed.append_lf {
        ensure!(!fixed.raw_final_lf, "unapproved duplicate final newline");
        normalized.push(b'\n');
    }
    ensure!(
        normalized.len() == fixed.output_bytes && sha256(&normalized) == fixed.output_sha256,
        "resource normalization exceeded exact approval"
    );
    Ok(normalized)
}
fn expected_oid(path: &str, development: bool, target: &str) -> Result<Option<&'static str>> {
    ensure!(TARGETS.contains(&target), "unknown resource target");
    Ok(match path {
        MODS if !is_older(target) => None,
        MODS => Some(match (development, target) {
            (false, "1.20") => "09684e8f729a1f8f55f8712ee1c5faea504d2084",
            (true, "1.20") => "2d5c71126247a8e078971354cbecfc669b965cdc",
            (false, "1.20.2" | "1.20.3") => "aae89f3b712a66c55be89444fc28a606e18ecf0f",
            (true, "1.20.2" | "1.20.3") => "7aeef2684bde6e5ea4ba4772a763523f13c1db72",
            (false, "1.20.4") => "23e9254da5964f30914bd20415c80a3836f9b39b",
            (true, "1.20.4") => "01c83bc811df9ab5b47fbc84966a6e7c773c77b3",
            (true, "1.20.1") => "4af3937238f80c3fd45ab140e4dd7c72bdd34a00",
            (true, _) => "67b59c05733a52196c3ac2695b2ef3bef1e4eb2b",
            (false, _) => "df9beecb20c2207da3f7e59d408bff39fe392aba",
        }),
        NEO if is_older(target) => None,
        NEO => Some(match (development, target == "26.1.2") {
            (false, false) => "c7aee3c06585439728b4b87c6788aa7d394f9738",
            (true, false) => "a9753a40d5459ed27020e895717a720d78d6d063",
            (false, true) => "e0cd89768edf806372b6d50c63fc3b47f0ae2b17",
            (true, true) => "c652df7858e7f85de82484633a1ad120e0097d92",
        }),
        REDSTONE => Some(if development && is_d2(target) {
            "d076222a8578a36932ae85a10a4300c5eabecf98"
        } else {
            "6db14349b9015d13dc6d064a1c32dd80de7ae90e"
        }),
        MODELS => Some(if development && is_d2(target) {
            "572e058ea8dc31b989222a6bda9066763ecbe804"
        } else {
            "ada518e5706d972c3334fd2c749f0ea5150079ea"
        }),
        PICKAXE if !is_older(target) => None,
        PICKAXE => Some(if development && is_d2(target) {
            "4732243cde87534fbae7371a7eb2455f76a45516"
        } else {
            "ba2d99494b4163a121da0b8d15ac56f566d42fdd"
        }),
        _ => return Err(eyre::eyre!("resource path outside exact witness cohort")),
    })
}
#[test]
fn resource_first_slice_collects_all_one_hundred_frozen_cells_exactly() -> Result<()> {
    let fixture = Fixture::load()?;
    let isolated = fixture.isolated()?;
    let mut counts = (0, 0);
    for (identity, _) in COMMITS {
        let (environment, target) = identity.split_once('/').expect("fixed resource context");
        let context = closed_context(&fixture.core, target, &witness_roots(environment, target))?;
        let outputs = isolated.prepare(&context)?;
        for resource in RESOURCES {
            match expected_oid(resource.path, environment == "dev", target)? {
                Some(oid) => {
                    assert_eq!(
                        outputs[resource.path], fixture.normalized[oid],
                        "{identity} / {}",
                        resource.path
                    );
                    counts.0 += 1;
                }
                None => {
                    assert!(
                        !outputs.contains_key(resource.path),
                        "omitted resource emitted"
                    );
                    counts.1 += 1;
                }
            }
        }
    }
    assert_eq!(counts, (74, 26));
    Ok(())
}
#[test]
fn resource_first_slice_six_independent_owner_subsets_have_valid_resources() -> Result<()> {
    let fixture = Fixture::load()?;
    let isolated = fixture.isolated()?;
    for target in &TARGETS[..2] {
        for mask in 0..64u8 {
            let roots = [CC, LIVE, BUFFER, IMAGE, CLIENT, TOUCH]
                .into_iter()
                .enumerate()
                .filter_map(|(bit, owner)| (mask & (1 << bit) != 0).then_some(owner))
                .collect::<Vec<_>>();
            let context = closed_context(&fixture.core, target, &roots)?;
            let outputs = isolated.prepare(&context)?;
            let toml = std::str::from_utf8(&outputs[MODS])?;
            assert_eq!(
                toml.contains("modId = \"computercraft\""),
                context.features[CC]
            );
            assert!(toml.contains("versionRange = \"[10.3.2,)\""));
            assert!(toml.contains("version = \"${mod_version}\""));
            let redstone = std::str::from_utf8(&outputs[REDSTONE])?;
            assert_eq!(
                redstone.contains("if abc has gt 0 redstone:: then"),
                context.features[LIVE]
            );
            assert_eq!(
                redstone.contains("Experimental buffers transfer"),
                context.features[BUFFER]
            );
            if !context.features[LIVE] {
                assert!(!redstone.contains("if abc east side"));
            }
            if !context.features[BUFFER] {
                assert!(!redstone.contains("stored units in buffers"));
            }
            let models: BufferModels =
                facet_json::from_str(std::str::from_utf8(&outputs[MODELS])?)?;
            assert_eq!(
                models.variants.contains_key("resource=image"),
                context.features[IMAGE]
            );
            assert_eq!(
                models.variants["resource=item"].model,
                "sfm:block/buffer_item"
            );
            let tag: PickaxeTag = facet_json::from_str(std::str::from_utf8(&outputs[PICKAXE])?)?;
            assert_eq!(
                tag.values.contains(&"sfm:client_manager".to_owned()),
                context.features[CLIENT]
            );
            assert_eq!(
                tag.values.contains(&"sfm:touch_display".to_owned()),
                context.features[TOUCH]
            );
            assert_eq!(
                tag.values.iter().collect::<BTreeSet<_>>().len(),
                tag.values.len()
            );
        }
    }
    for target in &TARGETS[2..] {
        for roots in [vec![], vec![CC]] {
            let context = closed_context(&fixture.core, target, &roots)?;
            let outputs = isolated.prepare(&context)?;
            let path = if is_older(target) { MODS } else { NEO };
            assert_eq!(
                outputs[path],
                fixture.normalized[expected_oid(path, context.features[CC], target)?
                    .expect("owned loader resource")]
            );
        }
        for owner in [LIVE, BUFFER, IMAGE, CLIENT, TOUCH] {
            assert!(closed_context(&fixture.core, target, &[owner]).is_err());
        }
    }
    for required in &fixture.core.features.0[CC].requires {
        let context = closed_context(&fixture.core, "1.19.2", &[CC])?;
        let missing = context
            .features
            .iter()
            .filter(|(id, on)| **on && id.as_str() != required)
            .map(|(id, _)| id.as_str())
            .collect::<Vec<_>>();
        assert!(fixture.core.context("1.19.2", &missing).is_err());
    }
    Ok(())
}
#[test]
fn resource_first_slice_preserves_loader_and_path_membership_not_environment() -> Result<()> {
    let fixture = Fixture::load()?;
    let isolated = fixture.isolated()?;
    for target in TARGETS {
        let context = closed_context(&fixture.core, target, &[])?;
        let original = isolated.prepare(&context)?;
        let mut renamed = context.clone();
        renamed.environment = "dev".to_owned();
        renamed.projection_key = "arbitrary/nested/resource-preview".to_owned();
        renamed.preset = renamed.projection_key.clone();
        assert_eq!(isolated.prepare(&renamed)?, original);
        assert_eq!(original.contains_key(MODS), is_older(target));
        assert_eq!(original.contains_key(NEO), !is_older(target));
        assert_eq!(original.contains_key(PICKAXE), is_older(target));
        let loader = std::str::from_utf8(&original[if is_older(target) { MODS } else { NEO }])?;
        assert_eq!(loader.contains("modId = \"mekanism\""), target != "1.20");
        if target == "26.1.2" {
            assert!(loader.contains("versionRange = \"[10.8.0,)\""));
        }
        if target == "1.20.4" {
            assert!(loader.contains("type=\"required\""));
            assert!(loader.contains("type=\"optional\""));
        }
        assert!(
            !loader.starts_with("// GENERATED"),
            "Java banner contaminated TOML"
        );
    }
    Ok(())
}
#[test]
fn resource_first_slice_common_text_edit_reaches_all_real_targets() -> Result<()> {
    let fixture = Fixture::load()?;
    let isolated = fixture.isolated()?;
    let source = read_bounded(&checked_file(&fixture.core.core, REDSTONE)?, 1024 * 1024)?;
    let mut changed = b"-- isolated shared resource edit\n".to_vec();
    changed.extend_from_slice(&source);
    write_fixture(&isolated.root, REDSTONE, &changed)?;
    for target in ["1.19.2", "1.21.1", "26.1.2"] {
        let context = closed_context(&fixture.core, target, &[])?;
        let outputs = isolated.prepare(&context)?;
        let mut expected = b"-- isolated shared resource edit\n".to_vec();
        expected.extend_from_slice(
            &fixture.normalized[expected_oid(REDSTONE, false, target)?.expect("all-target sample")],
        );
        assert_eq!(outputs[REDSTONE], expected);
    }
    Ok(())
}
#[test]
fn resource_first_slice_mode_hash_normalization_and_unknown_conditions_refuse() -> Result<()> {
    let fixture = Fixture::load()?;
    for fixed in GOLDENS {
        let mut altered = fixture.raw[fixed.oid].clone();
        altered.push(b' ');
        assert!(normalize_raw(&altered, fixed).is_err());
        let mut contract = fixed;
        contract.append_lf = !fixed.append_lf;
        assert!(normalize_raw(&fixture.raw[fixed.oid], contract).is_err());
    }
    let mut metadata = fixture.core.metadata.clone();
    metadata
        .source_rules
        .get_mut(MODELS)
        .expect("owned resource")[0]
        .template = false;
    assert!(verify_metadata(&metadata, resource(MODELS)?).is_err());
    let isolated = fixture.isolated()?;
    write_fixture(
        &isolated.root,
        REDSTONE,
        b"{% if features.unknown_resource_owner %}\ninvalid\n{% endif %}\n",
    )?;
    let context = closed_context(&fixture.core, "1.19.2", &[])?;
    assert!(isolated.prepare(&context).is_err());
    assert!(resource("src/../unowned.json").is_err());
    assert!(golden("0000000000000000000000000000000000000000").is_err());
    assert!(closed_context(&fixture.core, "1.19.2", &["unknown_resource_owner"]).is_err());
    Ok(())
}
