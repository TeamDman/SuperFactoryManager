//! Post-promotion source contracts for two previously released test classes.
//!
//! These checks require actual authored core inputs: no staged or historical
//! production fallback. Exact Git blobs are bounded golden witnesses only.
//! Passing does not establish Java compilation, JAR parity or game acceptance.

#![cfg(test)]

use super::candidate_lock::checked_file;
use super::context::ProjectionContext;
use super::core_inputs::CoreProjectInputs;
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
use std::fs;

const RECIPES: &str =
    "src/gametest/java/ca/teamdman/sfm/gametest/tests/general/RecipesGameTest.java";
const REGEX: &str = "src/test/java/ca/teamdman/sfm/test/RegexCacheTests.java";
const PATHS: [&str; 2] = [RECIPES, REGEX];
const LEDGER: &str = "docs/tasks/sfm-core-released-tests-slice.json";
const PACKET: &str = "packet_values";
const FIX: &str = "regex_overlap_fix";
const REGEX_BASE: &str = "041d410f73fa4bfc69293957eea0d1ba484f135c";
const REGEX_AUTHORED: &str = "4b42504b7393c3454394acfe8d5ff26c8f591066";
const REGEX_COMPILED_SHA256: &str =
    "sha256:993b39de2885897a7caa8e39b2735fe6b958725bf722e863f076bd072a2f53b9";
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
const TEMPLATES: [(&str, &str, usize); 2] = [
    (
        RECIPES,
        "sha256:dd9e35493ac036e16a644c79d3d7d9b787d8f8d365e1154889694a8fe8b49428",
        6498,
    ),
    (
        REGEX,
        "sha256:d9ddc7abbe4f89d7b164ec390726fe2545b23dd6bad09f30710dd391fe32716a",
        5340,
    ),
];

struct Golden {
    oid: &'static str,
    raw_sha256: &'static str,
    raw_bytes: usize,
    normalized_sha256: &'static str,
    normalized_bytes: usize,
    removed_cr: usize,
}
static GOLDENS: [Golden; 8] = [
    Golden {
        oid: "fd3082c9ca548f2ac8b6a6ae4463dfbd8d905a04",
        raw_sha256: "sha256:cbe8a4b04b15ae54f1963079322f196bb66698780ee5826fa93f49284e9e48c0",
        raw_bytes: 3835,
        normalized_sha256: "sha256:af3d42589b64ff1a66f1a944c01183cd7cf3220d70cf6d10ee81b881e661f653",
        normalized_bytes: 3736,
        removed_cr: 99,
    },
    Golden {
        oid: "52d78c1f5916a464bff69453db4ff67a531d49c4",
        raw_sha256: "sha256:d2a016f58ce20ed9c9f4ea97677f2c5d46e2d5684eb897da10544cba8a95bf7f",
        raw_bytes: 3869,
        normalized_sha256: "sha256:88671cafc70e96a8af09d2d7ead840cc7800dcfc6790730a7a3150799e3da0f0",
        normalized_bytes: 3770,
        removed_cr: 99,
    },
    Golden {
        oid: "89e1e78d7b2623e71a4af1dbfe9eac74f1901bc0",
        raw_sha256: "sha256:a7f9c771f66a880e80495b5568a245a657e87dd08513d5c289249448015aad7b",
        raw_bytes: 4018,
        normalized_sha256: "sha256:fb5eca99ea4e961148e7bbe1dd8ca00a0ce15eb81abaa815306eb2855fa39548",
        normalized_bytes: 3917,
        removed_cr: 101,
    },
    Golden {
        oid: "5ad16e402cfdef0758890e17dc3e49ff4c268f9b",
        raw_sha256: "sha256:058f6521024db42f0b49adaa568a08fe3d6372508dfea862abceb9352a448f4c",
        raw_bytes: 4045,
        normalized_sha256: "sha256:e6ee16fc4a08834e564080897977ddae3161575b0447316af5cf2d36cfbd8d61",
        normalized_bytes: 3945,
        removed_cr: 100,
    },
    Golden {
        oid: "898f0768abedda34dcfa17a03f810d33cc936562",
        raw_sha256: "sha256:bce6d8ad94d2b8518814635e0fab105a48bfcd3e100060aba2b8eb5dbe0738b5",
        raw_bytes: 3963,
        normalized_sha256: "sha256:09124e49b1313e9d869b099da2de680ba73f648e40e9a9764feae1210c55fae6",
        normalized_bytes: 3865,
        removed_cr: 98,
    },
    Golden {
        oid: "d717d597d060635780c07beabc0b239fabfed6ca",
        raw_sha256: "sha256:797be1e49bb0dba3c76d476d13535068bbde10a36d4308798ad622cc1c111c2f",
        raw_bytes: 3997,
        normalized_sha256: "sha256:aed7d19a3a17a1bd68acd7da324dd5afb18f81dd79e4a5108a807d79ba615bb4",
        normalized_bytes: 3899,
        removed_cr: 98,
    },
    Golden {
        oid: REGEX_BASE,
        raw_sha256: "sha256:05f6ee856587a228401385a1a1f94b1df970e0255a38780ae115befea2b62eee",
        raw_bytes: 4822,
        normalized_sha256: "sha256:f9eb2f911804a9ae627973816de3f82f705f258bf638a82540709c2aa39f5622",
        normalized_bytes: 4674,
        removed_cr: 148,
    },
    Golden {
        oid: REGEX_AUTHORED,
        raw_sha256: "sha256:7bee201ca4ff45e08c1872553fdace745152f6759536539132019f128a1a485c",
        raw_bytes: 5443,
        normalized_sha256: "sha256:23b3eee8a92ddfec6772259623a464b7709b925c0f4377813296291c4ea5d419",
        normalized_bytes: 5277,
        removed_cr: 166,
    },
];

#[derive(Facet)]
struct Ledger {
    schema: String,
    scope: String,
    source_context_commits: BTreeMap<String, String>,
    inputs: Vec<InputEvidence>,
    raw_variants: Vec<RawEvidence>,
    compiled_primary_regex_witness: CompiledEvidence,
    registered_owners_unchanged: Vec<OwnerEvidence>,
}
#[derive(Facet)]
struct InputEvidence {
    path: String,
    stage_path: String,
    core_path: String,
    source_sha256: String,
    source_bytes: usize,
    membership: String,
    feature_members: Vec<String>,
    witnesses: BTreeMap<String, String>,
}
#[derive(Facet)]
struct RawEvidence {
    blob: String,
    raw_sha256: String,
    raw_bytes: usize,
    normalized_sha256: String,
    normalized_bytes: usize,
    removed_cr: usize,
}
#[derive(Facet)]
struct CompiledEvidence {
    authored_blob: String,
    original_witnessed_feature_mask: Vec<String>,
    expected_sha256: String,
    expected_bytes: usize,
}
#[derive(Facet)]
struct OwnerEvidence {
    id: String,
    supported_targets: Vec<String>,
    requires: Vec<String>,
}

struct TestFixture {
    core: CoreTestFixture,
    inventory: BTreeSet<String>,
    normalized: BTreeMap<String, Vec<u8>>,
    compiled_regex: Vec<u8>,
}
impl TestFixture {
    fn load() -> Result<Self> {
        let core = CoreTestFixture::load()?;
        let ledger: Ledger = facet_json::from_str(std::str::from_utf8(&read_bounded(
            &checked_file(&core.repository, LEDGER)?,
            1024 * 1024,
        )?)?)?;
        ensure!(
            ledger.schema == "sfm:core-released-tests-slice@1"
                && ledger.scope == "RecipesGameTest_and_RegexCacheTests_only",
            "wrong released test slice scope"
        );
        let commits = COMMITS
            .into_iter()
            .map(|(name, commit)| (name.to_owned(), commit.to_owned()))
            .collect::<BTreeMap<_, _>>();
        ensure!(
            ledger.source_context_commits == commits,
            "test witness commits changed"
        );
        ensure!(
            ledger.inputs.len() == 2 && ledger.raw_variants.len() == 8,
            "test witness scope changed"
        );
        let mut seen_paths = BTreeSet::new();
        for input in &ledger.inputs {
            let (_, digest, count) = TEMPLATES
                .iter()
                .find(|(path, _, _)| *path == input.path)
                .ok_or_else(|| eyre::eyre!("unreviewed test input"))?;
            let basename = input.path.rsplit('/').next().expect("fixed test path");
            ensure!(
                seen_paths.insert(input.path.as_str())
                    && input.source_sha256 == *digest
                    && input.source_bytes == *count
                    && input.membership == "shared_all_ten_targets_all_feature_masks"
                    && input.stage_path
                        == format!(
                            "platform/cli/sfm-propagate-changes/target/core-released-tests-stage-v1/{basename}"
                        )
                    && input.core_path
                        == format!("platform/minecraft/core-liquid-template/{}", input.path)
                    && input.feature_members == [if input.path == RECIPES { PACKET } else { FIX }],
                "test source ownership or template witness changed"
            );
            let expected = COMMITS
                .into_iter()
                .map(|(name, _)| Ok((name.to_owned(), expected_oid(&input.path, name)?.to_owned())))
                .collect::<Result<BTreeMap<_, _>>>()?;
            ensure!(
                input.witnesses == expected,
                "incomplete or changed 20-context test witness map"
            );
            validate_shared_rule(&core.metadata, &input.path)?;
        }
        let mut seen_oids = BTreeSet::new();
        for evidence in &ledger.raw_variants {
            let pin = golden(&evidence.blob)?;
            ensure!(
                seen_oids.insert(evidence.blob.as_str())
                    && evidence.raw_sha256 == pin.raw_sha256
                    && evidence.raw_bytes == pin.raw_bytes
                    && evidence.normalized_sha256 == pin.normalized_sha256
                    && evidence.normalized_bytes == pin.normalized_bytes
                    && evidence.removed_cr == pin.removed_cr,
                "raw test witness or approved EOL normalization changed"
            );
        }
        ensure!(
            GOLDENS.iter().map(|pin| pin.removed_cr).sum::<usize>() == 909,
            "normalization scope widened"
        );
        ensure!(
            ledger.registered_owners_unchanged.len() == 2,
            "test owner scope changed"
        );
        let mut owners = BTreeSet::new();
        for owner in ledger.registered_owners_unchanged {
            let support: &[&str] = match owner.id.as_str() {
                PACKET => &["1.19.2", "1.19.4"],
                FIX => &["1.19.2", "26.1.2"],
                _ => eyre::bail!("unreviewed test owner"),
            };
            ensure!(
                owners.insert(owner.id.clone())
                    && owner
                        .supported_targets
                        .iter()
                        .map(String::as_str)
                        .eq(support.iter().copied())
                    && owner.requires.is_empty(),
                "test owner support or prerequisites changed"
            );
            let registered = core
                .features
                .0
                .get(&owner.id)
                .ok_or_else(|| eyre::eyre!("missing test owner"))?;
            ensure!(
                registered.supported_targets == owner.supported_targets
                    && registered.requires == owner.requires,
                "registered owner disagrees with exact test evidence"
            );
        }
        let oids = GOLDENS.iter().map(|pin| pin.oid.to_owned()).collect();
        let raw = read_git_blobs(&core.repository, &oids)?;
        let mut normalized = BTreeMap::new();
        for pin in &GOLDENS {
            normalized.insert(pin.oid.to_owned(), verify_raw(&raw[pin.oid], pin)?);
        }
        let compiled_evidence = ledger.compiled_primary_regex_witness;
        ensure!(
            compiled_evidence.authored_blob == REGEX_AUTHORED
                && compiled_evidence.original_witnessed_feature_mask == [FIX]
                && compiled_evidence.expected_sha256 == REGEX_COMPILED_SHA256
                && compiled_evidence.expected_bytes == 5229,
            "original regex expansion evidence changed"
        );
        let old_source = std::str::from_utf8(&normalized[REGEX_AUTHORED])?;
        ensure!(
            old_source
                .matches("{% if features.regex_overlap_fix %}\n")
                .count()
                == 1
                && old_source.matches("{% endif %}\n").count() == 1,
            "original regex authored witness directives changed"
        );
        // This witnessed source was already Liquid. Keep its raw identity,
        // separately establish the actual Java golden with production rendering.
        let compiled_regex =
            render_java_source(old_source, &core.context("1.19.2", &[FIX])?)?.into_bytes();
        ensure!(
            compiled_regex.len() == 5229 && sha256(&compiled_regex) == REGEX_COMPILED_SHA256,
            "original compiled regex golden changed"
        );
        let inventory = discover_core_source_files(&core.core)?;
        ensure!(
            PATHS.iter().all(|path| inventory.contains(*path)),
            "test templates must be promoted before registration; no staged fallback"
        );
        Ok(Self {
            core,
            inventory,
            normalized,
            compiled_regex,
        })
    }
    fn render(&self, path: &str, context: &ProjectionContext) -> Result<Vec<u8>> {
        let selected = self
            .core
            .selection_for_assertion(context, &self.inventory)?;
        let input = selected
            .inputs
            .get(path)
            .ok_or_else(|| eyre::eyre!("released test was omitted"))?;
        ensure!(
            input.input == path && !selected.omitted_paths.contains(path),
            "released test gained an alternate input or optional membership"
        );
        let source = self.core.read_source(path)?;
        verify_template(path, &source)?;
        Ok(render_java_source(std::str::from_utf8(&source)?, context)?.into_bytes())
    }
    fn expected(&self, path: &str, target: &str, packet: bool, fix: bool) -> Result<Vec<u8>> {
        if path == REGEX && target == "1.19.2" && fix {
            return Ok(self.compiled_regex.clone());
        }
        let environment = if path == RECIPES && packet {
            "dev"
        } else {
            "release"
        };
        Ok(self.normalized[expected_oid(path, &format!("{environment}/{target}"))?].clone())
    }
}

fn expected_oid(path: &str, context: &str) -> Result<&'static str> {
    let (environment, target) = context
        .split_once('/')
        .ok_or_else(|| eyre::eyre!("malformed test context"))?;
    ensure!(
        matches!(environment, "release" | "dev") && TARGETS.contains(&target),
        "unsupported test context"
    );
    Ok(match path {
        REGEX => {
            if context == "dev/1.19.2" {
                REGEX_AUTHORED
            } else {
                REGEX_BASE
            }
        }
        RECIPES => match context {
            "dev/1.19.2" => "898f0768abedda34dcfa17a03f810d33cc936562",
            "dev/1.19.4" => "d717d597d060635780c07beabc0b239fabfed6ca",
            "release/1.19.2" => "fd3082c9ca548f2ac8b6a6ae4463dfbd8d905a04",
            _ => match target {
                "1.19.4" | "1.20" | "1.20.1" => "52d78c1f5916a464bff69453db4ff67a531d49c4",
                "26.1.2" => "5ad16e402cfdef0758890e17dc3e49ff4c268f9b",
                _ => "89e1e78d7b2623e71a4af1dbfe9eac74f1901bc0",
            },
        },
        _ => eyre::bail!("path outside released test slice"),
    })
}
fn golden(oid: &str) -> Result<&Golden> {
    GOLDENS
        .iter()
        .find(|pin| pin.oid == oid)
        .ok_or_else(|| eyre::eyre!("unreviewed raw test blob"))
}
fn normalized_lf(bytes: &[u8]) -> Result<Vec<u8>> {
    ensure!(
        bytes.ends_with(b"\n"),
        "test final-LF mutation is not approved"
    );
    let text = std::str::from_utf8(bytes)?;
    ensure!(
        !text.replace("\r\n", "").contains('\r'),
        "test lone-CR normalization is not approved"
    );
    Ok(text.replace("\r\n", "\n").into_bytes())
}
fn verify_raw(bytes: &[u8], pin: &Golden) -> Result<Vec<u8>> {
    ensure!(
        bytes.len() == pin.raw_bytes && sha256(bytes) == pin.raw_sha256,
        "raw test bytes changed"
    );
    let normalized = normalized_lf(bytes)?;
    ensure!(
        normalized.len() == pin.normalized_bytes
            && sha256(&normalized) == pin.normalized_sha256
            && bytes.len() - normalized.len() == pin.removed_cr,
        "normalization exceeded exact approved EOL-only scope"
    );
    Ok(normalized)
}
fn verify_template(path: &str, bytes: &[u8]) -> Result<()> {
    let (_, digest, count) = TEMPLATES
        .iter()
        .find(|(name, _, _)| *name == path)
        .ok_or_else(|| eyre::eyre!("unreviewed template path"))?;
    ensure!(
        bytes.len() == *count
            && sha256(bytes) == *digest
            && !bytes.contains(&b'\r')
            && bytes.ends_with(b"\n"),
        "test authored bytes changed"
    );
    std::str::from_utf8(bytes)?;
    Ok(())
}
fn validate_shared_rule(metadata: &CoreProjectInputs, path: &str) -> Result<()> {
    if let Some(rules) = metadata.source_rules.get(path) {
        ensure!(
            rules.len() == 1
                && rules[0].input == path
                && rules[0].when.targets.is_empty()
                && rules[0].when.all_features.is_empty()
                && rules[0].when.any_features.is_empty()
                && rules[0].when.none_features.is_empty(),
            "released test must remain one shared unconditional input"
        );
    }
    Ok(())
}

#[test]
fn released_test_templates_reconstruct_all_twenty_witnessed_contexts() -> Result<()> {
    let fixture = TestFixture::load()?;
    let mut cells = 0;
    for (context_name, _) in COMMITS {
        let (environment, target) = context_name.split_once('/').expect("fixed context");
        let packet = environment == "dev" && matches!(target, "1.19.2" | "1.19.4");
        let fix = context_name == "dev/1.19.2";
        let mut flags = Vec::new();
        if packet {
            flags.push(PACKET);
        }
        if fix {
            flags.push(FIX);
        }
        let mut context = fixture.core.context(target, &flags)?;
        context.environment = environment.to_owned();
        context.projection_key = format!(
            "{}/mc-{target}",
            if environment == "release" {
                "sfm-4.34.0"
            } else {
                "sfm-dev"
            }
        );
        context.preset.clone_from(&context.projection_key);
        for path in PATHS {
            let actual = fixture.render(path, &context)?;
            assert_eq!(
                actual,
                fixture.expected(path, target, packet, fix)?,
                "wrong normalized witness {context_name} {path}"
            );
            assert!(!actual.contains(&b'\r'));
            assert!(actual.ends_with(b"\n"));
            assert!(!std::str::from_utf8(&actual)?.contains("{%"));
            cells += 1;
        }
    }
    assert_eq!(cells, 40);
    Ok(())
}

#[test]
fn independent_packet_and_regex_masks_preserve_exact_test_members() -> Result<()> {
    let fixture = TestFixture::load()?;
    let cases: [(&str, &[&str]); 8] = [
        ("1.19.2", &[]),
        ("1.19.2", &[PACKET]),
        ("1.19.2", &[FIX]),
        ("1.19.2", &[PACKET, FIX]),
        ("1.19.4", &[]),
        ("1.19.4", &[PACKET]),
        ("26.1.2", &[]),
        ("26.1.2", &[FIX]),
    ];
    for (target, flags) in cases {
        let context = fixture.core.context(target, flags)?;
        for path in PATHS {
            let actual = fixture.render(path, &context)?;
            assert_eq!(
                actual,
                fixture.expected(path, target, flags.contains(&PACKET), flags.contains(&FIX))?
            );
            let text = std::str::from_utf8(&actual)?;
            if path == RECIPES {
                assert_eq!(
                    text.contains("exemptions.put(SFMItems.PACKET,"),
                    flags.contains(&PACKET)
                );
            } else {
                assert_eq!(
                    text.contains("void wildcardPrefixAndSuffixMustNotOverlap()"),
                    target == "1.19.2" && flags.contains(&FIX)
                );
            }
        }
    }
    Ok(())
}

#[test]
fn released_test_version_adapters_and_owner_refusals_are_explicit() -> Result<()> {
    let fixture = TestFixture::load()?;
    for target in TARGETS {
        let context = fixture.core.context(target, &[])?;
        let body = String::from_utf8(fixture.render(RECIPES, &context)?)?;
        match target {
            "1.19.2" => assert!(body.contains("recipe.getResultItem().getItem()")),
            "1.19.4" | "1.20" | "1.20.1" => {
                assert!(body.contains("List<CraftingRecipe> craftingRecipes"));
                assert!(body.contains("recipe.getResultItem(helper.getLevel().registryAccess())"));
            }
            "26.1.2" => {
                assert!(body.contains("import net.minecraft.resources.Identifier;"));
                assert!(body.contains(".recipeAccess()\n                .recipeMap()\n                .byType(RecipeType.CRAFTING);\n        //"));
                assert!(body.contains("recipe.isSpecial() || recipe instanceof ImbueRecipe"));
                assert!(body.contains("recipe.assemble(CraftingInput.EMPTY).getItem()"));
                assert!(!body.contains("ResourceLocation"));
            }
            _ => {
                assert!(body.contains("List<RecipeHolder<CraftingRecipe>> craftingRecipes"));
                assert!(body.contains("CraftingRecipe recipe = recipeHolder.value();"));
                if target == "1.21.0" {
                    assert_eq!(context.minecraft_version, "1.21");
                }
            }
        }
        if !matches!(target, "1.19.2" | "1.19.4") {
            assert!(fixture.core.context(target, &[PACKET]).is_err());
        }
        if !matches!(target, "1.19.2" | "26.1.2") {
            assert!(fixture.core.context(target, &[FIX]).is_err());
        }
    }
    for (path, selector) in [(RECIPES, PACKET), (REGEX, FIX)] {
        let mut context = fixture.core.context("1.19.2", &[])?;
        assert_eq!(context.features.remove(selector), Some(false));
        let bytes = fixture.core.read_source(path)?;
        let error = render_java_source(std::str::from_utf8(&bytes)?, &context)
            .expect_err("missing member selector must fail closed");
        assert!(error.to_string().contains(selector));
    }
    Ok(())
}

#[test]
fn released_test_common_edit_reaches_all_targets_without_repo_mutation() -> Result<()> {
    let fixture = TestFixture::load()?;
    let temp = tempfile::tempdir()?;
    let core = temp.path().join(super::core_inputs::CORE_ROOT);
    for path in PATHS {
        let destination = core.join(path);
        fs::create_dir_all(destination.parent().expect("fixed source parent"))?;
        let original = fixture.core.read_source(path)?;
        let mut edited = original.clone();
        edited.extend_from_slice(b"// isolated common test edit\n");
        fs::write(&destination, &edited)?;
        let inventory = discover_core_source_files(&core)?;
        let mut metadata = fixture.core.metadata.clone();
        metadata
            .source_rules
            .retain(|output, _| inventory.contains(output));
        metadata.project_files.clear();
        for target in TARGETS {
            let context = fixture.core.context(target, &[])?;
            let selected = select_core_inputs(&metadata, &context, &inventory)?;
            assert_eq!(selected.inputs[path].input, path);
            let bytes = read_bounded(
                &checked_file(&core, &selected.inputs[path].input)?,
                1024 * 1024,
            )?;
            let actual = render_java_source(std::str::from_utf8(&bytes)?, &context)?;
            let mut expected = fixture.render(path, &context)?;
            expected.extend_from_slice(b"// isolated common test edit\n");
            assert_eq!(actual.as_bytes(), expected);
        }
        assert_eq!(fixture.core.read_source(path)?, original);
    }
    Ok(())
}

#[test]
fn released_test_byte_and_membership_mutations_fail_closed() -> Result<()> {
    let fixture = TestFixture::load()?;
    assert!(normalized_lf(b"no final LF").is_err());
    assert!(normalized_lf(b"lone\rCR\n").is_err());
    assert!(normalized_lf(&[0xff, b'\n']).is_err());
    for path in PATHS {
        let mut source = fixture.core.read_source(path)?;
        source.extend_from_slice(b"// unexpected unreviewed change\n");
        assert!(verify_template(path, &source).is_err());
    }
    let pin = golden(REGEX_BASE)?;
    let mut raw = read_git_blobs(
        &fixture.core.repository,
        &BTreeSet::from([REGEX_BASE.to_owned()]),
    )?[REGEX_BASE]
        .clone();
    raw[0] = b'X';
    assert!(verify_raw(&raw, pin).is_err());
    let mut metadata = fixture.core.metadata.clone();
    // An explicit reviewed shared rule is permitted; adding a feature-level
    // whole-file predicate is not. Use the actual schema, not a test-only gate.
    let rule = super::core_inputs::InputVariant {
        input: RECIPES.to_owned(),
        when: super::core_inputs::InputPredicate {
            targets: vec![],
            all_features: vec![PACKET.to_owned()],
            any_features: vec![],
            none_features: vec![],
        },
        template: false,
    };
    metadata.source_rules.insert(RECIPES.to_owned(), vec![rule]);
    assert!(validate_shared_rule(&metadata, RECIPES).is_err());
    Ok(())
}
