//! Test-only raw-byte and membership checks for three shared utility templates.
//!
//! Historical Git objects are fixed golden witnesses only, never production
//! source inputs. These tests use the production selector and controlled renderer.
//! Passing does not establish Java compilation, JAR parity or gameplay acceptance.

#![cfg(test)]

use super::candidate_lock::checked_file;
use super::context::ProjectionContext;
use super::core_inputs::discover_core_source_files;
use super::core_slice_test_support::CoreTestFixture;
use super::core_slice_test_support::read_bounded;
use super::core_slice_test_support::read_git_blobs;
use super::projection_catalog::SUPPORTED_TARGETS;
use super::provenance::sha256;
use super::render_java_source;
use eyre::Result;
use eyre::ensure;
use facet::Facet;
use std::collections::BTreeMap;
use std::collections::BTreeSet;

const RESOURCE: &str = "src/main/java/ca/teamdman/sfm/common/util/SFMResourceLocation.java";
const ENTITY: &str = "src/main/java/ca/teamdman/sfm/common/util/SFMEntityUtils.java";
const REGEX: &str = "src/main/java/ca/teamdman/sfm/common/program/RegexCache.java";
const PATHS: [&str; 3] = [RESOURCE, ENTITY, REGEX];
const RESOURCE_LEDGER: &str = "docs/tasks/sfm-core-resource-location-slice.json";
const ADAPTER_LEDGER: &str = "docs/tasks/sfm-core-utility-adapters-slice.json";
const REGEX_FIX: &str = "regex_overlap_fix";
const REGEX_OFF: &str = "beddf8d4cb5b391064b347ffacb8ad9753a933b7";
const REGEX_TEMPLATE: &str = "d8c83347fde5b91f41b702810ee336632bd0e58f";
const REGEX_ON_SHA256: &str =
    "sha256:d8cfe16915af1e6893f02e5a55b85a0d0cc0cd5ef08e90c8cdbf774cd56e01a2";
const RESOURCE_TEMPLATE: &str = "3fbd619147df3ed13213edbfc3e5ba5ffeeadc2e";
const ENTITY_TEMPLATE: &str = "d5ee9cdf052c642009020d34cefb560ad29a6498";
const MAX_LEDGER_BYTES: u64 = 1024 * 1024;

struct RawGolden {
    oid: &'static str,
    sha256: &'static str,
    bytes: usize,
    crlf_pairs: usize,
}

const RAW_GOLDENS: [RawGolden; 10] = [
    RawGolden {
        oid: "3fbd619147df3ed13213edbfc3e5ba5ffeeadc2e",
        sha256: "sha256:52f70c59df400dac1d37b74f8f873b86cdf9cdb10418bf0763952e4377e20a29",
        bytes: 2772,
        crlf_pairs: 82,
    },
    RawGolden {
        oid: "39ae0123a58736ed78d900882753f876b0879ac2",
        sha256: "sha256:23fb7cfa00e514b9228f3de292637e037bd35fada79a0f97e142ac466ca8ef93",
        bytes: 1260,
        crlf_pairs: 33,
    },
    RawGolden {
        oid: "9fd893df72662c8cb70acd2355cbfdd19abf39a3",
        sha256: "sha256:993fcde12c2e0cdef5c74482d2ff0f09219fcfc888092b93dfee7fcda177354a",
        bytes: 1279,
        crlf_pairs: 33,
    },
    RawGolden {
        oid: "532cb2cf23ab7dde45608c7f44fe7207418cf69a",
        sha256: "sha256:076db9ecb2cf032da13d5b000152806eb43ed0a00ee91d0f363be14e8c4d40a4",
        bytes: 1219,
        crlf_pairs: 33,
    },
    RawGolden {
        oid: "beddf8d4cb5b391064b347ffacb8ad9753a933b7",
        sha256: "sha256:cceb53200ddf5a42d66be6e0d87182c38ed89f3ac532ef453de8b936b38c7858",
        bytes: 3713,
        crlf_pairs: 0,
    },
    RawGolden {
        oid: "d8c83347fde5b91f41b702810ee336632bd0e58f",
        sha256: "sha256:ab9fba5be9bab55cdcdb9bc273478d8f29447d409954a6b4674f9a67ed3f557e",
        bytes: 3915,
        crlf_pairs: 0,
    },
    RawGolden {
        oid: "09ead0b417378c21a52b963d7f1fc91a0a1d99b9",
        sha256: "sha256:cc315fbfdc225efde3f0ac5800ba5380de6401ff717b6ef10c0b4805be2f2296",
        bytes: 525,
        crlf_pairs: 18,
    },
    RawGolden {
        oid: "538ed94aaf1ddee153d8ce2a0fae67b0addf16aa",
        sha256: "sha256:eaccf339ebd3d20b0f15ac01f965d3fd3362b806a91240b6baf05a2e388dc2f8",
        bytes: 519,
        crlf_pairs: 18,
    },
    RawGolden {
        oid: "d5ee9cdf052c642009020d34cefb560ad29a6498",
        sha256: "sha256:df76a7d21006f94a05c05e5e4a2930b72dc5be8b005a2029860e86bdc61d5fa5",
        bytes: 886,
        crlf_pairs: 32,
    },
    RawGolden {
        oid: "e9e79aa6f27d41b060ceb2b9f520f8bf42edd199",
        sha256: "sha256:a660da5b8bc9146dd3f828ee0996ade64611887c15e1f7e8605711b1be441fa4",
        bytes: 520,
        crlf_pairs: 18,
    },
];

const AUTHORED_GOLDENS: [(&str, &str, usize); 3] = [
    (
        RESOURCE,
        "sha256:0f2ca1de996283914081d4353fa12a7bede98db310c5e613b9540e038a4bb60b",
        2854,
    ),
    (
        ENTITY,
        "sha256:32e0da247ab4a81eed4b56c505c9e5ca2d385ee8d734a2f67a0bb899486f21b3",
        819,
    ),
    (
        REGEX,
        "sha256:ab9fba5be9bab55cdcdb9bc273478d8f29447d409954a6b4674f9a67ed3f557e",
        3915,
    ),
];

const PINNED_CONTEXTS: [(&str, &str); 20] = [
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

#[derive(Facet)]
struct ResourceLedger {
    schema: String,
    core_relative_file: String,
    context_commits: BTreeMap<String, String>,
    authored_template: AuthoredTemplate,
    raw_witness_variants: Vec<WitnessVariant>,
}

#[derive(Facet)]
struct AuthoredTemplate {
    source_sha256: String,
    source_bytes: usize,
}

#[derive(Facet)]
struct AdapterLedger {
    schema: String,
    context_commits: BTreeMap<String, String>,
    files: BTreeMap<String, AdapterFile>,
    regex_feature: RegexFeature,
}

#[derive(Facet)]
struct AdapterFile {
    source_sha256: String,
    source_bytes: usize,
    raw_witness_variants: Vec<WitnessVariant>,
}

#[derive(Facet)]
struct WitnessVariant {
    blob: String,
    sha256: String,
    bytes: usize,
    contexts: Vec<String>,
}

#[derive(Facet)]
struct RegexFeature {
    id: String,
    registered_supported_targets: Vec<String>,
    support_changed: bool,
    expected_on_mutation: RegexMutation,
}

#[derive(Facet)]
struct RegexMutation {
    baseline_blob: String,
    exact_unique_before: String,
    exact_after: String,
    expected_sha256: String,
    expected_bytes: usize,
}

struct UtilityFixture {
    core: CoreTestFixture,
    inventory: BTreeSet<String>,
    raw: BTreeMap<String, Vec<u8>>,
    source_goldens: BTreeMap<String, (String, usize)>,
}

impl UtilityFixture {
    fn load() -> Result<Self> {
        let core = CoreTestFixture::load()?;
        let resource: ResourceLedger = facet_json::from_str(std::str::from_utf8(&read_bounded(
            &checked_file(&core.repository, RESOURCE_LEDGER)?,
            MAX_LEDGER_BYTES,
        )?)?)?;
        let adapter: AdapterLedger = facet_json::from_str(std::str::from_utf8(&read_bounded(
            &checked_file(&core.repository, ADAPTER_LEDGER)?,
            MAX_LEDGER_BYTES,
        )?)?)?;
        ensure!(
            resource.schema == "sfm:core-resource-location-slice@1"
                && adapter.schema == "sfm:core-utility-adapters-slice@1",
            "wrong utility slice evidence schema"
        );
        ensure!(
            resource.core_relative_file == RESOURCE
                && adapter.files.len() == 2
                && adapter.files.contains_key(ENTITY)
                && adapter.files.contains_key(REGEX),
            "utility slice source scope changed"
        );
        let pinned = PINNED_CONTEXTS
            .into_iter()
            .map(|(context, commit)| (context.to_owned(), commit.to_owned()))
            .collect::<BTreeMap<_, _>>();
        ensure!(
            resource.context_commits == pinned && adapter.context_commits == pinned,
            "utility context witnesses changed"
        );
        let oids = RAW_GOLDENS.iter().map(|g| g.oid.to_owned()).collect();
        let raw = read_git_blobs(&core.repository, &oids)?;
        for golden in RAW_GOLDENS {
            let bytes = &raw[golden.oid];
            ensure!(
                bytes.len() == golden.bytes && sha256(bytes) == golden.sha256,
                "raw utility witness {} changed",
                golden.oid
            );
            let text = std::str::from_utf8(bytes)?;
            ensure!(
                text.matches("\r\n").count() == golden.crlf_pairs
                    && !text.replace("\r\n", "").contains('\r')
                    && text.ends_with('\n'),
                "utility witness line ending or final newline changed"
            );
        }
        validate_witnesses(RESOURCE, &resource.raw_witness_variants)?;
        for (path, file) in &adapter.files {
            validate_witnesses(path, &file.raw_witness_variants)?;
        }
        let source_goldens = BTreeMap::from([
            (
                RESOURCE.to_owned(),
                (
                    resource.authored_template.source_sha256,
                    resource.authored_template.source_bytes,
                ),
            ),
            (
                ENTITY.to_owned(),
                (
                    adapter.files[ENTITY].source_sha256.clone(),
                    adapter.files[ENTITY].source_bytes,
                ),
            ),
            (
                REGEX.to_owned(),
                (
                    adapter.files[REGEX].source_sha256.clone(),
                    adapter.files[REGEX].source_bytes,
                ),
            ),
        ]);
        for (path, expected_hash, expected_bytes) in AUTHORED_GOLDENS {
            ensure!(
                source_goldens[path] == (expected_hash.to_owned(), expected_bytes),
                "authored utility evidence changed for {path}"
            );
        }
        let feature = adapter.regex_feature;
        ensure!(
            feature.id == REGEX_FIX
                && feature.registered_supported_targets == ["1.19.2", "26.1.2"]
                && !feature.support_changed,
            "regex feature support changed without reviewed evidence"
        );
        ensure!(
            feature.expected_on_mutation.baseline_blob == REGEX_OFF
                && feature.expected_on_mutation.exact_unique_before == regex_before()
                && feature.expected_on_mutation.exact_after == regex_after()
                && feature.expected_on_mutation.expected_sha256 == REGEX_ON_SHA256
                && feature.expected_on_mutation.expected_bytes == 3788,
            "regex independent fixture changed"
        );
        let inventory = discover_core_source_files(&core.core)?;
        ensure!(
            PATHS.iter().all(|path| inventory.contains(*path)),
            "authored utility input is missing"
        );
        Ok(Self {
            core,
            inventory,
            raw,
            source_goldens,
        })
    }

    fn render_selected(&self, path: &str, context: &ProjectionContext) -> Result<Vec<u8>> {
        let selected = self
            .core
            .selection_for_assertion(context, &self.inventory)?;
        let input = selected
            .inputs
            .get(path)
            .ok_or_else(|| eyre::eyre!("shared utility {path} was excluded"))?;
        ensure!(
            input.input == path && !selected.omitted_paths.contains(path),
            "shared utility selected an alternate source or became optional"
        );
        // Read only after the real membership predicate selects the input.
        let source = self.core.read_source(path)?;
        let (expected_hash, expected_bytes) = &self.source_goldens[path];
        ensure!(
            source.len() == *expected_bytes && sha256(&source) == *expected_hash,
            "authored utility bytes changed for {path}"
        );
        Ok(render_java_source(std::str::from_utf8(&source)?, context)?.into_bytes())
    }

    fn expected(&self, path: &str, target: &str, regex_on: bool) -> Result<Vec<u8>> {
        if path == REGEX && regex_on {
            let baseline = std::str::from_utf8(&self.raw[REGEX_OFF])?;
            ensure!(
                baseline.matches(regex_before()).count() == 1,
                "regex baseline must have one exact owned return statement"
            );
            let expected = baseline
                .replacen(regex_before(), regex_after(), 1)
                .into_bytes();
            ensure!(
                expected.len() == 3788 && sha256(&expected) == REGEX_ON_SHA256,
                "regex independent fixed golden changed"
            );
            return Ok(expected);
        }
        Ok(self.raw[concrete_oid(path, target)?].clone())
    }
}

fn validate_witnesses(path: &str, variants: &[WitnessVariant]) -> Result<()> {
    let mut contexts = BTreeSet::new();
    let mut seen_blobs = BTreeSet::new();
    for variant in variants {
        let golden = RAW_GOLDENS
            .iter()
            .find(|golden| golden.oid == variant.blob)
            .ok_or_else(|| eyre::eyre!("unreviewed utility witness {}", variant.blob))?;
        ensure!(
            seen_blobs.insert(variant.blob.as_str())
                && variant.sha256 == golden.sha256
                && variant.bytes == golden.bytes
                && !variant.contexts.is_empty(),
            "duplicated or changed utility witness"
        );
        for context in &variant.contexts {
            let (_, target) = split_context(context)?;
            let expected = if context == "dev/1.19.2" {
                match path {
                    RESOURCE => RESOURCE_TEMPLATE,
                    ENTITY => ENTITY_TEMPLATE,
                    REGEX => REGEX_TEMPLATE,
                    _ => eyre::bail!("path outside utility witness scope"),
                }
            } else {
                concrete_oid(path, target)?
            };
            ensure!(
                contexts.insert(context.as_str()) && variant.blob == expected,
                "wrong or duplicate utility context membership"
            );
        }
    }
    let expected_contexts = PINNED_CONTEXTS
        .into_iter()
        .map(|(context, _)| context)
        .collect::<BTreeSet<_>>();
    ensure!(
        contexts == expected_contexts,
        "incomplete utility witness context set"
    );
    Ok(())
}

fn split_context(name: &str) -> Result<(&str, &str)> {
    let (environment, target) = name
        .split_once('/')
        .ok_or_else(|| eyre::eyre!("malformed utility witness context"))?;
    ensure!(
        matches!(environment, "release" | "dev")
            && SUPPORTED_TARGETS.iter().any(|(id, _)| *id == target),
        "unsupported utility witness context"
    );
    Ok((environment, target))
}

fn concrete_oid(path: &str, target: &str) -> Result<&'static str> {
    ensure!(
        SUPPORTED_TARGETS.iter().any(|(id, _)| *id == target),
        "unsupported utility target"
    );
    Ok(match path {
        RESOURCE => match target {
            "26.1.2" => "532cb2cf23ab7dde45608c7f44fe7207418cf69a",
            "1.21.0" | "1.21.1" => "9fd893df72662c8cb70acd2355cbfdd19abf39a3",
            _ => "39ae0123a58736ed78d900882753f876b0879ac2",
        },
        ENTITY => match target {
            "1.19.2" | "1.19.4" => "e9e79aa6f27d41b060ceb2b9f520f8bf42edd199",
            "26.1.2" => "538ed94aaf1ddee153d8ce2a0fae67b0addf16aa",
            _ => "09ead0b417378c21a52b963d7f1fc91a0a1d99b9",
        },
        REGEX => REGEX_OFF,
        _ => eyre::bail!("path outside bounded utility slice"),
    })
}

fn regex_before() -> &'static str {
    "                return s -> s.startsWith(begin) && s.endsWith(end);\n"
}

fn regex_after() -> &'static str {
    concat!(
        "                return s -> s.length() >= begin.length() + end.length()\n",
        "                            && s.startsWith(begin) && s.endsWith(end);\n",
    )
}

#[test]
fn utility_templates_reconstruct_all_twenty_baseline_contexts_and_membership() -> Result<()> {
    let fixture = UtilityFixture::load()?;
    let mut cells = 0;
    for (context_name, _) in PINNED_CONTEXTS {
        let (environment, target) = split_context(context_name)?;
        let mut context = fixture.core.context(target, &[])?;
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
            let actual = fixture.render_selected(path, &context)?;
            assert_eq!(
                actual,
                fixture.expected(path, target, false)?,
                "wrong raw utility output for {context_name} {path}"
            );
            assert!(!std::str::from_utf8(&actual)?.contains("{%"));
            cells += 1;
        }
    }
    assert_eq!(cells, 60);
    Ok(())
}

#[test]
fn independent_regex_toggle_keeps_shared_utilities_and_exact_supported_outputs() -> Result<()> {
    let fixture = UtilityFixture::load()?;
    let mut cells = 0;
    for target in ["1.19.2", "26.1.2"] {
        for enabled in [false, true] {
            let features: &[&str] = if enabled { &[REGEX_FIX] } else { &[] };
            let context = fixture.core.context(target, features)?;
            for path in PATHS {
                assert_eq!(
                    fixture.render_selected(path, &context)?,
                    fixture.expected(path, target, enabled)?,
                    "independent regex toggle changed {target} {path}"
                );
                cells += 1;
            }
        }
    }
    assert_eq!(cells, 12);
    Ok(())
}

#[test]
fn regex_fix_refuses_eight_unsupported_targets_and_absent_selector() -> Result<()> {
    let fixture = UtilityFixture::load()?;
    let mut refused = 0;
    for (target, _) in SUPPORTED_TARGETS {
        if !matches!(target, "1.19.2" | "26.1.2") {
            assert!(
                fixture.core.context(target, &[REGEX_FIX]).is_err(),
                "regex fix silently widened to unsupported target {target}"
            );
            refused += 1;
        }
    }
    assert_eq!(refused, 8);
    let mut context = fixture.core.context("1.19.2", &[])?;
    assert_eq!(context.features.remove(REGEX_FIX), Some(false));
    let source = fixture.core.read_source(REGEX)?;
    let error = render_java_source(std::str::from_utf8(&source)?, &context)
        .expect_err("missing regex flag must fail closed");
    assert!(error.to_string().contains(REGEX_FIX));
    Ok(())
}

#[test]
fn utility_api_aliases_and_line_endings_remain_exact_on_three_version_groups() -> Result<()> {
    let fixture = UtilityFixture::load()?;
    for target in ["1.19.2", "1.21.0", "26.1.2"] {
        let context = fixture.core.context(target, &[])?;
        let resource = fixture.render_selected(RESOURCE, &context)?;
        let entity = fixture.render_selected(ENTITY, &context)?;
        for output in [&resource, &entity] {
            let text = std::str::from_utf8(output)?;
            assert!(text.ends_with("\r\n"));
            assert!(!text.replace("\r\n", "").contains('\n'));
        }
        let resource_text = std::str::from_utf8(&resource)?;
        match target {
            "1.19.2" => {
                assert!(resource_text.contains("return new ResourceLocation(namespace, path);"));
                assert!(std::str::from_utf8(&entity)?.contains("return entity.level;"));
            }
            "1.21.0" => {
                assert_eq!(context.minecraft_version, "1.21");
                assert!(
                    resource_text
                        .contains("return ResourceLocation.fromNamespaceAndPath(namespace, path);")
                );
            }
            "26.1.2" => {
                assert!(resource_text.contains("import net.minecraft.resources.Identifier;"));
                assert!(resource_text.contains("catch (IdentifierException rle)"));
                assert!(std::str::from_utf8(&entity)?.contains("return player.level();"));
            }
            _ => unreachable!(),
        }
    }
    let regex = fixture.render_selected(REGEX, &fixture.core.context("1.19.2", &[])?)?;
    assert!(!regex.contains(&b'\r'));
    assert!(regex.ends_with(b"\n"));
    Ok(())
}
