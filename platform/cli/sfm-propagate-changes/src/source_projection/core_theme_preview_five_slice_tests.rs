//! Prepared source-only regression for five real theme-preview leaves.
//!
//! Uses the actual core selector and controlled renderer after promotion.
//! Never runs Java, evaluates operators, invokes actions or opens a theme file.

#![cfg(test)]

use super::candidate_lock::checked_file;
use super::context::ProjectionContext;
use super::core_catalog::CoreCatalog;
use super::core_inputs::discover_core_source_files;
use super::core_inputs::select_core_inputs;
use super::core_slice_test_support::CoreTestFixture;
use super::core_slice_test_support::read_bounded;
use super::core_slice_test_support::read_git_blobs;
use super::provenance::sha256;
use super::render_java_source;
use eyre::Result;
use eyre::ensure;
use std::collections::BTreeMap;
use std::collections::BTreeSet;

const OWNER: &str = "theme_preview_rules";
const DESCRIPTOR: &str =
    "src/main/java/ca/teamdman/sfm/client/action/SFMClientActionDescriptor.java";
const LEDGER: &str = "docs/tasks/sfm-core-theme-preview-five-slice.json";
// This is the original sealed preparation ledger, not a mutable results file.
const LEDGER_BYTES: usize = 22404;
const LEDGER_SHA: &str = "sha256:6464fdfaeac13a88596383dd295d2697747b73b4cd7acfbfb3dd42493f525935";
const PATHS: [&str; 5] = [
    "src/main/java/ca/teamdman/sfm/client/theme/preview/SFMItemstackPreviewCompletion.java",
    "src/main/java/ca/teamdman/sfm/client/theme/preview/SFMItemstackPreviewCoverage.java",
    "src/main/java/ca/teamdman/sfm/client/theme/preview/SFMItemstackPreviewDefaultNames.java",
    "src/main/java/ca/teamdman/sfm/client/theme/preview/SFMItemstackPreviewRegistry.java",
    "src/main/java/ca/teamdman/sfm/client/theme/preview/SFMItemstackPreviewThemeTarget.java",
];
const RAW: [(&str, usize, &str); 5] = [
    (
        "3a1d2a33da0872ec7dab4858ee02772b1fe3637a",
        4559,
        "sha256:c15e794b749acb17dfdfb23128da642e6f1dbd7ad16240b92773fa46c6b67b03",
    ),
    (
        "7d67d1ee61cfdb4a534dcb6ee47a5e6462f28994",
        1284,
        "sha256:5bb04debf5481de58a579c3afc120aab080e399802a0b9d6f521bd253db9cbd4",
    ),
    (
        "de078cbcf134cbb5b2b9a82e28c9b2ce8835f34a",
        5065,
        "sha256:57d53265f4fdd98ff9e426a722496629ba2d669e2a9dbff03740995625489ed5",
    ),
    (
        "d2ecc0c46b669526e44de2528b338cde6c9c5ed0",
        1642,
        "sha256:7fb1eae67afc031b16da59b9dd561f56951f96d09f2d50c334ed40486fda6fa9",
    ),
    (
        "a31e576db63d50d99e6c7a2b4adbffca5950c73d",
        1320,
        "sha256:a0da8bdc0e54079027eeda262fd39661363a87b7f3ad2074ae0895a0f3ad95a7",
    ),
];
const PROVIDERS: [&str; 9] = [
    "src/main/java/ca/teamdman/sfm/client/theme/preview/SFMItemstackPreviewExpression.java",
    "src/main/java/ca/teamdman/sfm/client/theme/preview/SFMItemstackPreviewOperators.java",
    "src/main/java/ca/teamdman/sfm/client/theme/preview/SFMItemstackPreviewRules.java",
    "src/main/java/ca/teamdman/sfm/client/theme/preview/SFMItemstackPreviewSubject.java",
    "src/main/java/ca/teamdman/sfm/client/presentation/SFMItemIcon.java",
    "src/main/java/ca/teamdman/sfm/client/explorer/SFMPath.java",
    "src/main/java/ca/teamdman/sfm/client/explorer/lazy/SFMExplorerEntry.java",
    "src/main/java/ca/teamdman/sfm/client/explorer/SFMCanonicalText.java",
    "src/main/java/ca/teamdman/sfm/client/explorer/SFMParseException.java",
];
const TARGETS: [&str; 10] = [
    "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
    "26.1.2",
];
// Existing descriptor owner and all its explicit registered prerequisites.
// This is test composition, not production prerequisite expansion.
const DESCRIPTOR_OWNER: [&str; 9] = [
    "client_actions",
    "client_manager",
    "client_program_actions",
    "client_program_consent",
    "disk_readonly_access",
    "packet_computation",
    "packet_values",
    "runtime_resource_cleanup",
    "sfml_execution_side",
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

struct Fixture {
    core: CoreTestFixture,
    inventory: BTreeSet<String>,
    raw: BTreeMap<String, Vec<u8>>,
}

fn is_d2(target: &str) -> bool {
    matches!(target, "1.19.2" | "1.19.4")
}

fn same(actual: &[String], expected: &[&str]) -> bool {
    actual.len() == expected.len()
        && actual.iter().map(String::as_str).collect::<BTreeSet<_>>()
            == expected.iter().copied().collect()
}

fn exact(bytes: &[u8], length: usize, digest: &str) -> Result<()> {
    ensure!(
        bytes.len() == length
            && sha256(bytes) == digest
            && !bytes.contains(&b'\r')
            && bytes.ends_with(b"\n")
            && !bytes.ends_with(b"\n\n"),
        "theme-preview raw byte or exact newline contract changed"
    );
    std::str::from_utf8(bytes)?;
    Ok(())
}

impl Fixture {
    fn load() -> Result<Self> {
        let core = CoreTestFixture::load()?;
        let ledger = read_bounded(&checked_file(&core.repository, LEDGER)?, 1024 * 1024)?;
        ensure!(
            ledger.len() == LEDGER_BYTES && sha256(&ledger) == LEDGER_SHA,
            "original theme-five preparation ledger changed"
        );
        let definition = &core.features.0[OWNER];
        ensure!(
            same(&definition.supported_targets, &TARGETS[..2])
                && same(&definition.requires, &["client_theme"]),
            "theme-preview owner support or prerequisites changed"
        );
        for path in PATHS {
            let rules = core
                .metadata
                .source_rules
                .get(path)
                .ok_or_else(|| eyre::eyre!("theme leaf lacks explicit membership"))?;
            ensure!(
                rules.len() == 1
                    && rules[0].input == path
                    && rules[0].template
                    && same(&rules[0].when.targets, &TARGETS[..2])
                    && same(&rules[0].when.all_features, &[OWNER])
                    && rules[0].when.any_features.is_empty()
                    && rules[0].when.none_features.is_empty(),
                "theme leaf actual source predicate changed"
            );
        }
        let raw = read_git_blobs(
            &core.repository,
            &RAW.iter().map(|row| row.0.to_owned()).collect(),
        )?;
        for (oid, length, digest) in RAW {
            exact(&raw[oid], length, digest)?;
        }
        let inventory = discover_core_source_files(&core.core)?;
        ensure!(
            PATHS
                .iter()
                .chain(PROVIDERS.iter())
                .all(|path| inventory.contains(*path)),
            "theme leaf or real provider missing"
        );
        Ok(Self {
            core,
            inventory,
            raw,
        })
    }

    fn render(&self, index: usize, context: &ProjectionContext) -> Result<Option<Vec<u8>>> {
        let selected = self
            .core
            .selection_for_assertion(context, &self.inventory)?;
        let path = PATHS[index];
        let Some(input) = selected.inputs.get(path) else {
            ensure!(
                selected.omitted_paths.contains(path),
                "leaf omitted without explicit predicate"
            );
            return Ok(None);
        };
        ensure!(
            input.input == path && input.template,
            "unreviewed alternate theme input"
        );
        // Membership is resolved before this bounded source read.
        let bytes = self.core.read_source(path)?;
        exact(&bytes, RAW[index].1, RAW[index].2)?;
        Ok(Some(
            render_java_source(std::str::from_utf8(&bytes)?, context)?.into_bytes(),
        ))
    }

    fn assert_all(&self, context: &ProjectionContext, present: bool) -> Result<()> {
        for index in 0..5 {
            assert_eq!(
                self.render(index, context)?.as_deref(),
                present.then(|| self.raw[RAW[index].0].as_slice()),
                "{} / {}",
                PATHS[index],
                context.minecraft_version
            );
        }
        Ok(())
    }
}

#[test]
fn five_theme_leaves_reconstruct_all_twenty_historical_contexts() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut counts = (0, 0);
    for (name, _) in COMMITS {
        let (environment, target) = name.split_once('/').expect("fixed contexts");
        let present = environment == "dev" && is_d2(target);
        let enabled = if present {
            &["client_theme", OWNER][..]
        } else {
            &[][..]
        };
        fixture.assert_all(&fixture.core.context(target, enabled)?, present)?;
        if present {
            counts.0 += 5;
        } else {
            counts.1 += 5;
        }
    }
    assert_eq!(counts, (10, 90));
    Ok(())
}

#[test]
fn theme_leaves_feature_off_controls_omit_before_java_reads() -> Result<()> {
    let mut fixture = Fixture::load()?;
    let catalog = CoreCatalog::load(&fixture.core.repository, &fixture.core.repository)?;
    assert_eq!(catalog.catalog.0.len(), 20);
    fixture.core.core = fixture
        .core
        .core
        .join("deliberately_missing_theme_five_boundary");
    for key in catalog.catalog.0.keys() {
        fixture.assert_all(
            &fixture.core.historical_feature_off_catalog_context(key)?,
            false,
        )?;
    }
    Ok(())
}

#[test]
fn theme_owner_is_descriptor_independent_and_selects_real_provider_closure() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut cases = 0;
    for target in &TARGETS[..2] {
        for theme in [false, true] {
            for descriptor in [false, true] {
                let mut enabled = if descriptor {
                    DESCRIPTOR_OWNER.to_vec()
                } else {
                    Vec::new()
                };
                enabled.push("client_theme");
                if theme {
                    enabled.push(OWNER);
                }
                let context = fixture.core.context(target, &enabled)?;
                fixture.assert_all(&context, theme)?;
                assert_eq!(context.features["client_program_actions"], descriptor);
                let selected =
                    select_core_inputs(&fixture.core.metadata, &context, &fixture.inventory)?;
                assert_eq!(selected.inputs.contains_key(DESCRIPTOR), descriptor);
                if theme {
                    for provider in PROVIDERS {
                        let input = selected.inputs.get(provider).ok_or_else(|| {
                            eyre::eyre!("legal theme context omitted real provider {provider}")
                        })?;
                        ensure!(
                            input.input == provider,
                            "unreviewed provider alternate input"
                        );
                        let rendered = render_java_source(
                            std::str::from_utf8(&fixture.core.read_source(provider)?)?,
                            &context,
                        )?;
                        assert!(!rendered.contains("{%") && !rendered.contains("{{"));
                    }
                }
                if !descriptor {
                    for forbidden in [
                        "client_actions",
                        "client_program_consent",
                        "packet_values",
                        "sfml_execution_side",
                    ] {
                        assert!(
                            !context.features[forbidden],
                            "theme source acquired action/descriptor grant"
                        );
                    }
                }
                cases += 1;
            }
        }
        // An independently valid descriptor context never opts into a theme.
        fixture.assert_all(&fixture.core.context(target, &DESCRIPTOR_OWNER)?, false)?;
    }
    assert_eq!(cases, 8);
    Ok(())
}

#[test]
fn theme_support_and_prerequisites_fail_closed_without_catalog_changes() -> Result<()> {
    let fixture = Fixture::load()?;
    for target in &TARGETS[..2] {
        assert!(fixture.core.context(target, &[OWNER]).is_err());
        fixture.assert_all(&fixture.core.context(target, &[])?, false)?;
        fixture.assert_all(&fixture.core.context(target, &["client_theme"])?, false)?;
    }
    for target in &TARGETS[2..] {
        fixture.assert_all(&fixture.core.context(target, &["client_theme"])?, false)?;
        assert!(
            fixture
                .core
                .context(target, &["client_theme", OWNER])
                .is_err()
        );
    }
    Ok(())
}

#[test]
fn descriptive_environment_never_dispatches_whole_theme_leaf_classes() -> Result<()> {
    let fixture = Fixture::load()?;
    for target in &TARGETS[..2] {
        for environment in ["release", "dev"] {
            for present in [false, true] {
                let enabled = if present {
                    &["client_theme", OWNER][..]
                } else {
                    &["client_theme"][..]
                };
                let mut context = fixture.core.context(target, enabled)?;
                context.environment = environment.to_owned();
                context.projection_key = format!("review/theme-five/{environment}/{target}");
                context.preset = context.projection_key.clone();
                fixture.assert_all(&context, present)?;
            }
        }
    }
    Ok(())
}

#[test]
fn raw_theme_policies_are_retained_without_running_effects_or_evaluators() -> Result<()> {
    let fixture = Fixture::load()?;
    let text =
        |index: usize| -> Result<&str> { Ok(std::str::from_utf8(&fixture.raw[RAW[index].0])?) };
    for (index, markers) in [
        (
            0,
            &[
                "input.length()-start>16384",
                "depth>16 || ++nodes>128",
                "options=List.copyOf(options)",
                "token.start(),token.end()",
                "MAX_LITERAL_CODEPOINTS",
                "if(result.size()>=256) break;",
            ][..],
        ),
        (
            1,
            &[
                "Status.MATCHED",
                "GENERIC_ICON",
                "GENERIC_RULE",
                "SPECIFIC",
                "winner.predicate().print()",
            ][..],
        ),
        (
            2,
            &[
                "return List.copyOf(rules);",
                "Layer.DEFAULT",
                "sfm:entry/is_container",
                "sfm:entry/is_file",
                "sfm:entry/has_suffix",
                "Locale.ROOT",
                "SFMItemIcon.vanilla(item,meaning)",
            ][..],
        ),
        (
            3,
            &[
                "private static volatile Snapshot active",
                "rules = List.copyOf(rules)",
                "public static synchronized void registerOperator",
                "public static synchronized void registerRule",
                "Layer.MOD",
                "active.rules().size()>=256",
                "existing.id().equals(rule.id())",
                "SFMItemstackPreviewOperators.Type.BOOLEAN",
            ][..],
        ),
        (
            4,
            &[
                "toAbsolutePath().normalize()",
                "[0-9a-f]{64}",
                "#sha256=",
                "uri.getFragment()!=null || uri.getQuery()!=null",
                "Path.of(uri)",
            ][..],
        ),
    ] {
        for marker in markers {
            assert!(
                text(index)?.contains(marker),
                "raw theme policy changed: {marker}"
            );
        }
    }
    for index in 0..5 {
        for forbidden in [
            "Files.",
            "ProcessBuilder",
            "java.net.Socket",
            "SFMClientActionProgrammaticHandler",
            "SFMClientProgramIdentity",
        ] {
            assert!(
                !text(index)?.contains(forbidden),
                "theme leaf gained file/process/machine action coupling"
            );
        }
        assert!(!text(index)?.contains("{%") && !text(index)?.contains("{{"));
    }
    Ok(())
}
