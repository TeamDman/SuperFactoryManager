//! Prepared keyboard-provider projection tests. Not registered or executed by this capsule.
//! Uses real CoreTestFixture/selector/collector; no Git, process, network or Java execution.
//! Historical identities are pinned digest/OID witnesses, not source fallback inputs.
#![cfg(test)]
use super::candidate_lock::checked_file;
use super::context::ProjectionContext;
use super::core_catalog::CoreCatalog;
use super::core_inputs::CORE_ROOT;
use super::core_inputs::CoreProjectInputs;
use super::core_inputs::collect_core_artifacts;
use super::core_inputs::discover_core_source_files;
use super::core_inputs::select_core_inputs;
use super::core_slice_test_support::CoreTestFixture;
use super::core_slice_test_support::historical_feature_registry;
use super::core_slice_test_support::read_bounded;
use super::provenance::sha256;
use super::render_java_source;
use eyre::Result;
use eyre::ensure;
use facet::Facet;
use sha1::Digest;
use sha1::Sha1;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::path::Path;
const LEDGER: &str = "docs/tasks/sfm-keybinding-eight-core-provenance-20261002.json";
const OWNER: &str = "keyboard_profiles";
const PATHS: [&str; 8] = [
    "src/main/java/ca/teamdman/sfm/client/keybinding/SFMKeyBindingService.java",
    "src/main/java/ca/teamdman/sfm/client/keybinding/SFMKeyBindingDisplay.java",
    "src/main/java/ca/teamdman/sfm/client/keybinding/SFMKeyBindingCycle.java",
    "src/main/java/ca/teamdman/sfm/client/keybinding/SFMKeyBindingProfile.java",
    "src/main/java/ca/teamdman/sfm/client/keybinding/SFMKeyBindingEngine.java",
    "src/main/java/ca/teamdman/sfm/client/keybinding/SFMKeyBindingStorage.java",
    "src/main/java/ca/teamdman/sfm/client/keybinding/SFMKeyBindingDefaults.java",
    "src/main/java/ca/teamdman/sfm/client/screen/SFMCommandDraftScreen.java",
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
const TEMPLATE_PINS: [(usize, &str); 8] = [
    (
        15555,
        "sha256:619affea12ef57c4f7d976657509cf72e0499c167e06430afea9a26f8ebaa9d0",
    ),
    (
        2958,
        "sha256:cf596cbfa0c4be1d4c6b16e7aca6d6397c24939669ce7b5b22b59fc6ead4bba5",
    ),
    (
        1192,
        "sha256:2b197b80c54ba90da3f3cb3f9923d0822988f85a1a7934cfb59464dd7a90793f",
    ),
    (
        12694,
        "sha256:4de8281ec8ce056cac01c2c83f6ea04da993676ddf26f3e7a0ef0f44d16d6e31",
    ),
    (
        15216,
        "sha256:51f2e837616c474f44fb7d1e533c1b40daf3fc7ca09e799e4215ebdcdd4423bb",
    ),
    (
        25058,
        "sha256:e60fb69babe85bf897c01088ce780faf3e56bdbaef4cc1586f0e56bbe39ca45a",
    ),
    (
        19666,
        "sha256:90dd3f121ae8496f6aef6f63b2226a88b12e742fefa6c90bc4dc974a13852aa3",
    ),
    (
        18874,
        "sha256:ed87cc383ce282d4c4f0d89df800f1443275f79c9c1738988882f4304e8680c4",
    ),
];
#[derive(Clone, Copy)]
struct Golden {
    oid: &'static str,
    digest: &'static str,
    bytes: usize,
}
const GOLDENS: [Golden; 17] = [
    Golden {
        oid: "53ba153bfd62988dceb9f6395a43762410c24413",
        digest: "sha256:4b18c81bf9c21565ccef91af6fa7d7f204515c662b4144ee50189afec6477583",
        bytes: 12425,
    },
    Golden {
        oid: "bb2e23de8fc918a48904b932107bfbf32cad41f9",
        digest: "sha256:914e390409e7a264771da7aa60c77770518bff12858273ae93bf31c36b36b6f9",
        bytes: 4311,
    },
    Golden {
        oid: "20312151e22bf337f7c29bac6862e1aba32faf52",
        digest: "sha256:577485e5fc754fa7c4400c82fb99defdfaa77b2dd24bc35c52e4b3442cee3fc1",
        bytes: 4299,
    },
    Golden {
        oid: "701daf014f39fd4d7304b62257790accc14a5053",
        digest: "sha256:46bba8ab4b8a3782178da8cdd104347bbc4a22e34c8d855782059b189d6fd103",
        bytes: 1919,
    },
    Golden {
        oid: "3761e3113e2e2558007fb73a0a704b623bdc6140",
        digest: "sha256:575e07585469d475e4c449d9defc75fa86c57ceff00e2913dcd1aea641be9226",
        bytes: 1005,
    },
    Golden {
        oid: "478652b2cf8eafb65fc1bd179a44df1422a3594a",
        digest: "sha256:2b197b80c54ba90da3f3cb3f9923d0822988f85a1a7934cfb59464dd7a90793f",
        bytes: 1192,
    },
    Golden {
        oid: "35fc14c0f6c6e01a0c9530182c4b346e12238e43",
        digest: "sha256:81daed90a39a3cd25e605dd8af45e155e981050a4b337e3b988e11c2faa6937b",
        bytes: 7988,
    },
    Golden {
        oid: "79b86e545af46b58cbe0e4e810e1d4cdb08f48c4",
        digest: "sha256:4a698f01eb2801126cdbf4be1a7ada0eae44aaba02440f07b7ea314b733ce699",
        bytes: 1660,
    },
    Golden {
        oid: "97079d32c964bee6f9f2ec9beaef4192e35b5916",
        digest: "sha256:a04c027dcfaeffb6b0daf64333d5bf5082cdf06647249bff2a3b976d6aa569b5",
        bytes: 10364,
    },
    Golden {
        oid: "dd6ee6280c597173d8089cf03c4e529be71b6a81",
        digest: "sha256:865b5f03683ce0351b959199491bf5e689662d6f0b0961384cbd72777a8dd1c4",
        bytes: 3796,
    },
    Golden {
        oid: "e8af0f999850ec741d2944b669e710b4998a25ba",
        digest: "sha256:cd16d785101e20a851190a502e506389172cdd7443c7b1753114704bae2ccbeb",
        bytes: 18135,
    },
    Golden {
        oid: "bafeef57b4f99faf1e10d08a8eeb6f1ca7c1dab3",
        digest: "sha256:56eafcbb14d9341d33f562cf79c7097efff3a21d11aee65752ad63f013eb4b6f",
        bytes: 4845,
    },
    Golden {
        oid: "a310163dcb1791af8d3ddedce182e8581fcaab86",
        digest: "sha256:90dd3f121ae8496f6aef6f63b2226a88b12e742fefa6c90bc4dc974a13852aa3",
        bytes: 19666,
    },
    Golden {
        oid: "0a973bc2321692cd6545adbf828d0204f45bba52",
        digest: "sha256:b21ca09d5c2e65886e5202afb34d474df89636adabf57ba16d100f8a074bc9da",
        bytes: 11160,
    },
    Golden {
        oid: "a8459a6158e91b587e3dba538b30df0778cdea1c",
        digest: "sha256:62bff0eb3af6e59f32ceafbf91c3cad51db49b46eafe811cc90c24c048d1bb54",
        bytes: 11299,
    },
    Golden {
        oid: "50e4881f9dd69203998ef41c10d64b1f34af6f95",
        digest: "sha256:738bb204de58d8c12bd2be60e427502f9dd8c1e0852ff26c213f94527f5618dd",
        bytes: 11328,
    },
    Golden {
        oid: "6ad8dc1008cd49f598b5e64ca937fce749d09c9f",
        digest: "sha256:57306449392883733bdb1c263b18c8cc37162c0e5321caf0320d5bf217ba2130",
        bytes: 11434,
    },
];
#[derive(Facet)]
struct Ledger {
    schema: String,
    owner: String,
    normalization: String,
    scope: String,
    context_commits: BTreeMap<String, String>,
    files: Vec<Source>,
}
#[derive(Facet)]
struct Source {
    path: String,
    core_path: String,
    template_bytes: usize,
    template_sha256: String,
    membership: Membership,
    witnesses: Vec<Witness>,
}
#[derive(Facet)]
struct Membership {
    targets: Vec<String>,
    all_features: Vec<String>,
    any_features: Vec<String>,
    none_features: Vec<String>,
}
#[derive(Facet)]
struct Witness {
    context: String,
    source_commit: String,
    present: bool,
    raw_blob: Option<String>,
    raw_sha256: Option<String>,
    raw_bytes: Option<usize>,
    mode: Option<String>,
    explicit_registered_features: Vec<String>,
    features_origin: String,
}
struct Fixture {
    core: CoreTestFixture,
    ledger: Ledger,
    inventory: BTreeSet<String>,
}
fn same(actual: &[String], expected: &[&str]) -> bool {
    actual.len() == expected.len()
        && actual.iter().map(String::as_str).collect::<BTreeSet<_>>()
            == expected.iter().copied().collect()
}
fn d2(target: &str) -> bool {
    matches!(target, "1.19.2" | "1.19.4")
}
fn on(target: &str) -> &'static [&'static str] {
    if d2(target) {
        &["client_actions", OWNER, "single_line_input"]
    } else {
        &["client_actions", OWNER]
    }
}
fn golden(index: usize, target: &str) -> Option<Golden> {
    Some(match index {
        0 => {
            GOLDENS[if d2(target) {
                0
            } else if target == "26.1.2" {
                2
            } else {
                1
            }]
        }
        1 => GOLDENS[if d2(target) { 3 } else { 4 }],
        2 => GOLDENS[5],
        3 => GOLDENS[if d2(target) { 6 } else { 7 }],
        4 => GOLDENS[if d2(target) { 8 } else { 9 }],
        5 => GOLDENS[if d2(target) { 10 } else { 11 }],
        6 if d2(target) => GOLDENS[12],
        6 => return None,
        7 => {
            GOLDENS[if d2(target) {
                13
            } else if matches!(target, "1.20" | "1.20.1") {
                14
            } else if target == "26.1.2" {
                16
            } else {
                15
            }]
        }
        _ => panic!("fixed eight-path index"),
    })
}
fn identity(bytes: &[u8], expected: Golden) -> Result<()> {
    ensure!(
        bytes.len() == expected.bytes && sha256(bytes) == expected.digest,
        "historical source digest changed"
    );
    let mut digest = Sha1::new();
    digest.update(format!("blob {}\0", bytes.len()).as_bytes());
    digest.update(bytes);
    ensure!(
        format!("{:x}", digest.finalize()) == expected.oid,
        "historical framed blob OID changed"
    );
    ensure!(
        !bytes.contains(&b'\r') && bytes.ends_with(b"\n") && !bytes.ends_with(b"\n\n"),
        "historical LF contract changed"
    );
    Ok(())
}
impl Fixture {
    fn load() -> Result<Self> {
        let core = CoreTestFixture::load()?;
        let bytes = read_bounded(&checked_file(&core.repository, LEDGER)?, 512 * 1024)?;
        let ledger: Ledger = facet_json::from_str(std::str::from_utf8(&bytes)?)?;
        ensure!(
            ledger.schema == "sfm:keyboard_eight_core_provenance@1"
                && ledger.owner == OWNER
                && ledger.normalization == "none"
                && ledger.scope.contains("Source-only")
                && ledger.files.len() == 8,
            "eight-provider ledger changed"
        );
        let commits: BTreeMap<String, String> = COMMITS
            .into_iter()
            .map(|(c, h)| (c.to_owned(), h.to_owned()))
            .collect();
        ensure!(
            ledger.context_commits == commits,
            "twenty historical commits changed"
        );
        let (_, historical_features) = historical_feature_registry()?;
        ensure!(
            historical_features.0.len() == 180,
            "frozen feature definitions changed"
        );
        let owner = &core.features.0[OWNER];
        ensure!(
            same(&owner.supported_targets, &TARGETS) && same(&owner.requires, &["client_actions"]),
            "keyboard owner changed"
        );
        let single = &core.features.0["single_line_input"];
        ensure!(
            same(&single.supported_targets, &TARGETS[..2]) && single.requires.is_empty(),
            "widget owner changed"
        );
        let inventory = discover_core_source_files(&core.core)?;
        for (i, source) in ledger.files.iter().enumerate() {
            ensure!(
                source.path == PATHS[i]
                    && source.core_path == format!("{CORE_ROOT}/{}", PATHS[i])
                    && source.template_bytes == TEMPLATE_PINS[i].0
                    && source.template_sha256 == TEMPLATE_PINS[i].1,
                "canonical source identity changed"
            );
            let raw = core.read_source(PATHS[i])?;
            ensure!(
                raw.len() == source.template_bytes
                    && sha256(&raw) == source.template_sha256
                    && inventory.contains(PATHS[i]),
                "canonical template absent or changed"
            );
            let targets = if i == 6 { &TARGETS[..2] } else { &[][..] };
            ensure!(
                same(&source.membership.targets, targets)
                    && same(&source.membership.all_features, &[OWNER])
                    && source.membership.any_features.is_empty()
                    && source.membership.none_features.is_empty(),
                "proposed membership changed"
            );
            let rules = core
                .metadata
                .source_rules
                .get(PATHS[i])
                .ok_or_else(|| eyre::eyre!("source rule missing"))?;
            ensure!(
                rules.len() == 1
                    && rules[0].input == PATHS[i]
                    && rules[0].template
                    && same(&rules[0].when.targets, targets)
                    && same(&rules[0].when.all_features, &[OWNER])
                    && rules[0].when.any_features.is_empty()
                    && rules[0].when.none_features.is_empty(),
                "eight-provider source rule changed"
            );
        }
        Ok(Self {
            core,
            ledger,
            inventory,
        })
    }
    fn isolated_metadata(&self) -> CoreProjectInputs {
        let mut result = self.core.metadata.clone();
        result
            .source_rules
            .retain(|path, _| PATHS.contains(&path.as_str()));
        result
    }
    fn isolated_inventory(&self) -> BTreeSet<String> {
        PATHS.into_iter().map(str::to_owned).collect()
    }
    fn render(&self, index: usize, context: &ProjectionContext) -> Result<Option<Vec<u8>>> {
        let selection = self
            .core
            .selection_for_assertion(context, &self.inventory)?;
        let Some(input) = selection.inputs.get(PATHS[index]) else {
            ensure!(
                selection.omitted_paths.contains(PATHS[index]),
                "missing omitted-path evidence"
            );
            return Ok(None);
        };
        ensure!(
            input.input == PATHS[index] && input.template,
            "alternate source selected"
        );
        let bytes = self.core.read_source(PATHS[index])?;
        Ok(Some(
            render_java_source(std::str::from_utf8(&bytes)?, context)?.into_bytes(),
        ))
    }
    fn copy_inputs(
        &self,
        root: &Path,
        metadata: &CoreProjectInputs,
        context: &ProjectionContext,
    ) -> Result<()> {
        let selection = select_core_inputs(metadata, context, &self.isolated_inventory())?;
        for (_, input) in selection
            .inputs
            .iter()
            .filter(|(output, _)| !output.starts_with("src/"))
        {
            let bytes = read_bounded(
                &checked_file(&self.core.core, &input.input)?,
                16 * 1024 * 1024,
            )?;
            let destination = root.join(&input.input);
            std::fs::create_dir_all(destination.parent().expect("project parent"))?;
            std::fs::write(destination, bytes)?;
        }
        Ok(())
    }
    fn copy_sources(&self, root: &Path, prefix: &[u8]) -> Result<()> {
        for path in PATHS {
            let mut bytes = prefix.to_vec();
            bytes.extend_from_slice(&self.core.read_source(path)?);
            let destination = root.join(path);
            std::fs::create_dir_all(destination.parent().expect("source parent"))?;
            std::fs::write(destination, bytes)?;
        }
        Ok(())
    }
}

#[test]
fn keyboard_eight_ledger_retains_160_typed_cells_72_present_88_absent() -> Result<()> {
    let f = Fixture::load()?;
    let mut present = 0;
    let mut absent = 0;
    for (i, source) in f.ledger.files.iter().enumerate() {
        ensure!(source.witnesses.len() == 20, "missing source cells");
        let mut unique = BTreeSet::new();
        for witness in &source.witnesses {
            let (environment, target) = witness
                .context
                .split_once('/')
                .ok_or_else(|| eyre::eyre!("invalid context"))?;
            let expected = if environment == "dev" {
                golden(i, target)
            } else {
                None
            };
            let features = if environment == "dev" {
                on(target)
            } else {
                &[]
            };
            ensure!(
                unique.insert(witness.context.clone())
                    && f.ledger.context_commits.get(&witness.context)
                        == Some(&witness.source_commit)
                    && witness.present == expected.is_some()
                    && witness.raw_blob.as_deref() == expected.map(|g| g.oid)
                    && witness.raw_sha256.as_deref() == expected.map(|g| g.digest)
                    && witness.raw_bytes == expected.map(|g| g.bytes)
                    && witness.mode.as_deref() == expected.map(|_| "100644")
                    && same(&witness.explicit_registered_features, features)
                    && witness.features_origin
                        == "reviewed_current_explicit_reconstruction_not_historical_manifest",
                "historical source cell changed"
            );
            f.core.context(target, features)?;
            if expected.is_some() {
                present += 1
            } else {
                absent += 1
            }
        }
    }
    assert_eq!((present, absent), (72, 88));
    Ok(())
}

#[test]
fn keyboard_eight_reconstructs_all_72_authentic_bodies_and_88_omissions() -> Result<()> {
    let f = Fixture::load()?;
    let mut count = (0, 0);
    for (context, _) in COMMITS {
        let (environment, target) = context.split_once('/').expect("fixed context");
        let features = if environment == "dev" {
            on(target)
        } else {
            &[]
        };
        let c = f.core.context(target, features)?;
        for i in 0..8 {
            match (
                f.render(i, &c)?,
                if environment == "dev" {
                    golden(i, target)
                } else {
                    None
                },
            ) {
                (Some(bytes), Some(expected)) => {
                    identity(&bytes, expected)?;
                    count.0 += 1;
                }
                (None, None) => count.1 += 1,
                _ => eyre::bail!("unexpected membership in {context}: {}", PATHS[i]),
            }
        }
    }
    assert_eq!(count, (72, 88));
    Ok(())
}

#[test]
fn keyboard_eight_is_independent_of_typed_palette_and_widget_owner() -> Result<()> {
    let f = Fixture::load()?;
    for target in TARGETS {
        let c = f.core.context(target, &["client_actions", OWNER])?;
        ensure!(
            c.features.get("typed_command_palette") == Some(&false)
                && c.features.get("workspace_panels") == Some(&false),
            "unrelated owner became prerequisite"
        );
        for i in 0..8 {
            let result = f.render(i, &c)?;
            if let Some(g) = golden(i, target) {
                let bytes =
                    result.ok_or_else(|| eyre::eyre!("independent keyboard source missing"))?;
                if i == 7 && d2(target) {
                    let all = f
                        .render(i, &f.core.context(target, on(target))?)?
                        .expect("all-on draft");
                    let expected = std::str::from_utf8(&all)?.replace(
                        "new ca.teamdman.sfm.client.input.SFMSingleLineEditBox",
                        "new EditBox",
                    );
                    ensure!(
                        bytes == expected.as_bytes()
                            && std::str::from_utf8(&all)?
                                .matches("new ca.teamdman.sfm.client.input.SFMSingleLineEditBox")
                                .count()
                                == 2,
                        "widget-independent constructor substitutions changed"
                    );
                } else {
                    identity(&bytes, g)?;
                }
            } else {
                ensure!(result.is_none(), "modern Defaults stub invented");
            }
        }
    }
    Ok(())
}

#[test]
fn keyboard_eight_preserves_existing_prerequisites_and_d2_only_widget_support() -> Result<()> {
    let f = Fixture::load()?;
    for target in TARGETS {
        ensure!(
            f.core.context(target, &[OWNER]).is_err(),
            "client_actions prerequisite removed"
        );
        ensure!(
            f.core
                .context(target, &["client_actions", OWNER, "not_registered"])
                .is_err(),
            "unknown feature accepted"
        );
        if !d2(target) {
            ensure!(
                f.core
                    .context(target, &["client_actions", OWNER, "single_line_input"])
                    .is_err(),
                "widget target widened"
            );
            ensure!(
                f.render(6, &f.core.context(target, on(target))?)?.is_none(),
                "Defaults modern membership widened"
            );
        }
    }
    Ok(())
}

#[test]
fn keyboard_eight_twenty_feature_off_controls_omit_before_source_reads() -> Result<()> {
    let mut core = CoreTestFixture::load()?;
    let catalog = CoreCatalog::load(&core.repository, &core.repository)?;
    let inventory: BTreeSet<String> = PATHS.into_iter().map(str::to_owned).collect();
    // Selection uses typed metadata only. Make all source reads impossible.
    core.core = core
        .repository
        .join("this-prepared-test-never-creates-this-directory");
    let mut count = 0;
    for key in catalog.catalog.0.keys() {
        let context = core.historical_feature_off_catalog_context(key)?;
        let selected = select_core_inputs(&core.metadata, &context, &inventory)?;
        for path in PATHS {
            ensure!(
                !selected.inputs.contains_key(path) && selected.omitted_paths.contains(path),
                "historical feature-off control selected keyboard source"
            );
            count += 1;
        }
    }
    assert_eq!(count, 160);
    ensure!(
        !core.core.exists(),
        "read-refusal sentinel unexpectedly exists"
    );
    Ok(())
}

#[test]
fn keyboard_eight_collector_preserves_actual_raw_bannered_output_and_provenance() -> Result<()> {
    let f = Fixture::load()?;
    let metadata = f.isolated_metadata();
    let inventory = f.isolated_inventory();
    let mut count = 0;
    for target in TARGETS {
        let context = f.core.context(target, on(target))?;
        let temp = tempfile::tempdir()?;
        let root = temp.path().join(CORE_ROOT);
        std::fs::create_dir_all(&root)?;
        f.copy_inputs(&root, &metadata, &context)?;
        f.copy_sources(&root, b"")?;
        let selected = select_core_inputs(&metadata, &context, &inventory)?;
        let actual = collect_core_artifacts(&root, &selected, &context)?;
        for i in 0..8 {
            if let Some(g) = golden(i, target) {
                let artifact = &actual[PATHS[i]];
                let raw = f.core.read_source(PATHS[i])?;
                ensure!(
                    artifact.source_bytes == raw
                        && artifact.source_path == format!("{CORE_ROOT}/{}", PATHS[i])
                        && artifact.overlay.is_none()
                        && artifact
                            .output_bytes
                            .starts_with(b"// GENERATED by sfm-propagate-changes;"),
                    "collector canonical source/provenance changed"
                );
                let rendered = f.render(i, &context)?.expect("selected source");
                identity(&rendered, g)?;
                ensure!(
                    artifact.output_bytes.ends_with(&rendered),
                    "collector output differs from real render"
                );
                count += 1;
            } else {
                ensure!(!actual.contains_key(PATHS[i]), "omitted source collected");
            }
        }
    }
    assert_eq!(count, 72);
    Ok(())
}

#[test]
fn keyboard_eight_omitted_bytes_are_unread_but_selected_poison_fails_closed() -> Result<()> {
    let f = Fixture::load()?;
    let metadata = f.isolated_metadata();
    let inventory = f.isolated_inventory();
    let on = f.core.context("1.19.2", on("1.19.2"))?;
    let off = f.core.context("1.19.2", &[])?;
    let temp = tempfile::tempdir()?;
    let root = temp.path().join(CORE_ROOT);
    std::fs::create_dir_all(&root)?;
    f.copy_inputs(&root, &metadata, &on)?;
    f.copy_sources(&root, b"")?;
    for path in PATHS {
        std::fs::write(root.join(path), [0xff])?;
    }
    let omitted = select_core_inputs(&metadata, &off, &inventory)?;
    let actual = collect_core_artifacts(&root, &omitted, &off)?;
    ensure!(
        PATHS.iter().all(|path| !actual.contains_key(*path)),
        "omitted malformed source read"
    );
    let selected = select_core_inputs(&metadata, &on, &inventory)?;
    for path in PATHS {
        f.copy_sources(&root, b"")?;
        std::fs::write(root.join(path), [0xff])?;
        ensure!(
            collect_core_artifacts(&root, &selected, &on).is_err(),
            "selected non-UTF8 source accepted"
        );
    }
    f.copy_sources(&root, b"")?;
    let mut poison = b"{% if features.not_registered %}\n{% endif %}\n".to_vec();
    poison.extend_from_slice(&f.core.read_source(PATHS[0])?);
    std::fs::write(root.join(PATHS[0]), poison)?;
    ensure!(
        collect_core_artifacts(&root, &selected, &on).is_err(),
        "unknown source selector accepted"
    );
    f.copy_sources(&root, b"")?;
    ensure!(
        collect_core_artifacts(&root, &selected, &off).is_err(),
        "selection/context drift accepted"
    );
    Ok(())
}

#[test]
fn keyboard_eight_shared_source_edit_reaches_all_72_real_collected_outputs_only_in_temp()
-> Result<()> {
    let f = Fixture::load()?;
    let metadata = f.isolated_metadata();
    let inventory = f.isolated_inventory();
    let before: Vec<Vec<u8>> = PATHS
        .iter()
        .map(|path| f.core.read_source(path))
        .collect::<Result<_>>()?;
    let prefix = b"// Shared eight-keyboard-provider source propagation proof.\n";
    let mut count = 0;
    for target in TARGETS {
        let context = f.core.context(target, on(target))?;
        let temp = tempfile::tempdir()?;
        let root = temp.path().join(CORE_ROOT);
        std::fs::create_dir_all(&root)?;
        f.copy_inputs(&root, &metadata, &context)?;
        f.copy_sources(&root, prefix)?;
        let selected = select_core_inputs(&metadata, &context, &inventory)?;
        let artifacts = collect_core_artifacts(&root, &selected, &context)?;
        for i in 0..8 {
            if golden(i, target).is_some() {
                let artifact = &artifacts[PATHS[i]];
                let mut expected_raw = prefix.to_vec();
                expected_raw.extend_from_slice(&before[i]);
                let mut expected_output = prefix.to_vec();
                expected_output
                    .extend_from_slice(&f.render(i, &context)?.expect("selected source"));
                ensure!(
                    artifact.source_bytes == expected_raw
                        && artifact.source_bytes != before[i]
                        && artifact.source_path == format!("{CORE_ROOT}/{}", PATHS[i])
                        && artifact.overlay.is_none()
                        && artifact
                            .output_bytes
                            .starts_with(b"// GENERATED by sfm-propagate-changes;")
                        && artifact.output_bytes.ends_with(&expected_output),
                    "real parent-source edit not collected"
                );
                count += 1;
            } else {
                ensure!(
                    !artifacts.contains_key(PATHS[i]),
                    "omitted Defaults gained output"
                );
            }
        }
    }
    assert_eq!(count, 72);
    for i in 0..8 {
        ensure!(
            f.core.read_source(PATHS[i])? == before[i],
            "canonical source changed"
        );
    }
    Ok(())
}
