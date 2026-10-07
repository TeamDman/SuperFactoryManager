//! Ten pure keyboard carrier preservation checks after atomic parent promotion.
//!
//! Historical objects are bounded offline test witnesses, not production inputs.
//! These tests prove source ownership/API reconstruction only: no input dispatch,
//! profile persistence, Java compilation, game execution or runtime acceptance.

#![cfg(test)]

use super::candidate_lock::checked_file;
use super::context::ProjectionContext;
use super::core_catalog::CoreCatalog;
use super::core_inputs::CORE_ROOT;
use super::core_inputs::collect_core_artifacts;
use super::core_inputs::discover_core_source_files;
use super::core_inputs::select_core_inputs;
use super::core_slice_test_support::CoreTestFixture;
use super::core_slice_test_support::read_bounded;
use super::core_slice_test_support::read_git_blobs;
use super::provenance::sha256;
use super::release_baseline::frozen_git_command;
use super::render_java_source;
use eyre::Result;
use eyre::ensure;
use facet::Facet;
use std::collections::BTreeMap;
use std::collections::BTreeSet;

const LEDGER: &str = "docs/tasks/sfm-core-keyboard-carriers-slice.json";
const PATHS: [&str; 10] = [
    "src/main/java/ca/teamdman/sfm/client/keybinding/SFMKeyModifier.java",
    "src/main/java/ca/teamdman/sfm/client/keybinding/SFMKeyStroke.java",
    "src/main/java/ca/teamdman/sfm/client/keybinding/SFMKeySequence.java",
    "src/main/java/ca/teamdman/sfm/client/keybinding/SFMKeyBinding.java",
    "src/main/java/ca/teamdman/sfm/client/keybinding/SFMKeyInputEvent.java",
    "src/main/java/ca/teamdman/sfm/client/keybinding/SFMKeyBindingSnapshot.java",
    "src/main/java/ca/teamdman/sfm/client/keybinding/SFMActionInvocationIntent.java",
    "src/main/java/ca/teamdman/sfm/client/keybinding/SFMKeyBindingOverride.java",
    "src/main/java/ca/teamdman/sfm/client/keybinding/SFMKeyBindingConflict.java",
    "src/main/java/ca/teamdman/sfm/client/keybinding/SFMKeyBindingMatchResult.java",
];
const TARGETS: [&str; 10] = [
    "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
    "26.1.2",
];
const ON: [&str; 2] = ["client_actions", "keyboard_profiles"];
const BINDING: &str = "src/main/java/ca/teamdman/sfm/client/keybinding/SFMKeyBinding.java";
const OLDER_BINDING_OID: &str = "09f14e6e93ea5fcc415f6bca47950387dd4a5af9";
const CONTEXT_COMMITS: [(&str, &str); 20] = [
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
struct Pin {
    oid: &'static str,
    digest: &'static str,
    bytes: usize,
}
const RAW_PINS: [Pin; 11] = [
    Pin {
        oid: "e7c504859e8c92707fea85afc1f3b3d151be7b81",
        digest: "sha256:ba8c53488b937c0fdef8147c5ca0faf263167c481d897a79bf92997a994d3831",
        bytes: 118,
    },
    Pin {
        oid: "08e794eee9faa52e2bca9f6dae5ffde362276e03",
        digest: "sha256:225efdea8693a16e1dadd870e0b5a7fd3529b31b8d8a16a6bb0f053c93ddd30a",
        bytes: 601,
    },
    Pin {
        oid: "5338b77f87274c48c7f43e1669fb04348c7bfda7",
        digest: "sha256:58b0d1d84d9accfd3f3c24d346f284276a3e44613e5d73f236411b8bbbbea732",
        bytes: 437,
    },
    Pin {
        oid: "0c0fab98ebea8f72246f0e0e1b054576a4f2b6df",
        digest: "sha256:761cd2568bfeb4da30afa5c5018f7b2b250a4edb219aa2b625afed7e9b6f3c8a",
        bytes: 742,
    },
    Pin {
        oid: "6518c7e2642f6b8db7c2df5610df58a5b43db030",
        digest: "sha256:04cd860d2351c0a96c14eda11560e2b11aab9e62baaa837324721e95c4fdb871",
        bytes: 466,
    },
    Pin {
        oid: "b5310df5876ed25447da9c1e6dd19d86030a11d7",
        digest: "sha256:6fd5882e7fe4da6832cd88c98d93197cd19c5f61d02a7de85819de6d899c1c1d",
        bytes: 359,
    },
    Pin {
        oid: "2be8bcf30f81cd259c56cb40d1f53d3d2b760530",
        digest: "sha256:a28cbd07d515b1a092dac63e4e63263b5d4a90292ee9539c19283883ca5514cb",
        bytes: 261,
    },
    Pin {
        oid: "0746adfd0a11768a53cf5879ce37d7d5e4b07eb9",
        digest: "sha256:125730bddd4a27915b8210c36e46e234960ac3d830cad20935e403235f440199",
        bytes: 1267,
    },
    Pin {
        oid: "409b2bcb96299bdf1fe766ab2aa2863c29a4637f",
        digest: "sha256:2b5d22c4325d60aa2cecd95f898575d554c8d713c02ff8b215ae9236e390fa34",
        bytes: 449,
    },
    Pin {
        oid: "00a3c108aa7e5e62e21cec73632bc8de5bac0288",
        digest: "sha256:25aafb5387e8394a89cd65c1ea9098d2415ba2d8cef2094b49cb5f1857fa6659",
        bytes: 580,
    },
    Pin {
        oid: "09f14e6e93ea5fcc415f6bca47950387dd4a5af9",
        digest: "sha256:fb86fba1165ad5945f692c3edd433a19001be3231284683bdc53f38a577c30a5",
        bytes: 596,
    },
];
#[derive(Clone, Copy)]
struct TemplatePin {
    path: &'static str,
    digest: &'static str,
    bytes: usize,
}
const TEMPLATE_PINS: [TemplatePin; 10] = [
    TemplatePin {
        path: "src/main/java/ca/teamdman/sfm/client/keybinding/SFMKeyModifier.java",
        digest: "sha256:ba8c53488b937c0fdef8147c5ca0faf263167c481d897a79bf92997a994d3831",
        bytes: 118,
    },
    TemplatePin {
        path: "src/main/java/ca/teamdman/sfm/client/keybinding/SFMKeyStroke.java",
        digest: "sha256:225efdea8693a16e1dadd870e0b5a7fd3529b31b8d8a16a6bb0f053c93ddd30a",
        bytes: 601,
    },
    TemplatePin {
        path: "src/main/java/ca/teamdman/sfm/client/keybinding/SFMKeySequence.java",
        digest: "sha256:58b0d1d84d9accfd3f3c24d346f284276a3e44613e5d73f236411b8bbbbea732",
        bytes: 437,
    },
    TemplatePin {
        path: "src/main/java/ca/teamdman/sfm/client/keybinding/SFMKeyBinding.java",
        digest: "sha256:d6316750e06a542d2a09e8b0d551cde13acafedd08a8c4e0ac73bd1eabafa128",
        bytes: 1131,
    },
    TemplatePin {
        path: "src/main/java/ca/teamdman/sfm/client/keybinding/SFMKeyInputEvent.java",
        digest: "sha256:04cd860d2351c0a96c14eda11560e2b11aab9e62baaa837324721e95c4fdb871",
        bytes: 466,
    },
    TemplatePin {
        path: "src/main/java/ca/teamdman/sfm/client/keybinding/SFMKeyBindingSnapshot.java",
        digest: "sha256:6fd5882e7fe4da6832cd88c98d93197cd19c5f61d02a7de85819de6d899c1c1d",
        bytes: 359,
    },
    TemplatePin {
        path: "src/main/java/ca/teamdman/sfm/client/keybinding/SFMActionInvocationIntent.java",
        digest: "sha256:a28cbd07d515b1a092dac63e4e63263b5d4a90292ee9539c19283883ca5514cb",
        bytes: 261,
    },
    TemplatePin {
        path: "src/main/java/ca/teamdman/sfm/client/keybinding/SFMKeyBindingOverride.java",
        digest: "sha256:125730bddd4a27915b8210c36e46e234960ac3d830cad20935e403235f440199",
        bytes: 1267,
    },
    TemplatePin {
        path: "src/main/java/ca/teamdman/sfm/client/keybinding/SFMKeyBindingConflict.java",
        digest: "sha256:2b5d22c4325d60aa2cecd95f898575d554c8d713c02ff8b215ae9236e390fa34",
        bytes: 449,
    },
    TemplatePin {
        path: "src/main/java/ca/teamdman/sfm/client/keybinding/SFMKeyBindingMatchResult.java",
        digest: "sha256:25aafb5387e8394a89cd65c1ea9098d2415ba2d8cef2094b49cb5f1857fa6659",
        bytes: 580,
    },
];

#[derive(Facet)]
struct Ledger {
    schema: String,
    context_commits: BTreeMap<String, String>,
    normalization: String,
    files: Vec<FileEvidence>,
    raw_blobs: BTreeMap<String, RawEvidence>,
}
#[derive(Facet)]
struct RawEvidence {
    raw_sha256: String,
    raw_bytes: usize,
    bom: bool,
    crlf_count: usize,
    lone_cr_count: usize,
    final_lf: bool,
    normalization: String,
    prefix_hex: String,
}
#[derive(Facet)]
struct FileEvidence {
    path: String,
    core_path: String,
    template_sha256: String,
    template_bytes: usize,
    membership: Membership,
    member_owners: Vec<String>,
    raw_oids: Vec<String>,
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
    raw_oid: Option<String>,
    raw_sha256: Option<String>,
    raw_bytes: Option<usize>,
    mode: Option<String>,
    explicit_features: Vec<String>,
    features_origin: String,
}
struct Fixture {
    core: CoreTestFixture,
    inventory: BTreeSet<String>,
    raw: BTreeMap<String, Vec<u8>>,
}
impl Fixture {
    fn load() -> Result<Self> {
        let core = CoreTestFixture::load()?;
        let ledger: Ledger = facet_json::from_str(std::str::from_utf8(&read_bounded(
            &checked_file(&core.repository, LEDGER)?,
            1024 * 1024,
        )?)?)?;
        ensure!(
            ledger.schema == "sfm:core_keyboard_carriers_slice@1"
                && ledger.normalization == "none_raw_exact_lf_final_lf"
                && ledger.context_commits == pinned_commits()
                && ledger.files.len() == PATHS.len()
                && ledger.raw_blobs.len() == RAW_PINS.len(),
            "keyboard carrier ledger scope changed"
        );
        let owner = core
            .features
            .0
            .get("keyboard_profiles")
            .ok_or_else(|| eyre::eyre!("missing existing keyboard owner"))?;
        ensure!(
            same_names(&owner.supported_targets, &TARGETS)
                && same_names(&owner.requires, &["client_actions"]),
            "keyboard owner support/prerequisites changed"
        );
        for pin in RAW_PINS {
            let row = ledger
                .raw_blobs
                .get(pin.oid)
                .ok_or_else(|| eyre::eyre!("missing raw pin"))?;
            ensure!(
                row.raw_sha256 == pin.digest
                    && row.raw_bytes == pin.bytes
                    && !row.bom
                    && row.crlf_count == 0
                    && row.lone_cr_count == 0
                    && row.final_lf
                    && row.normalization == "none"
                    && row.prefix_hex == "7061636b61676520",
                "carrier raw identity/newline policy changed"
            );
        }
        let mut seen = BTreeSet::new();
        for input in &ledger.files {
            let pin = template_pin(&input.path)?;
            let targets = membership_targets(&input.path);
            ensure!(
                seen.insert(input.path.clone())
                    && input.core_path == format!("{CORE_ROOT}/{}", input.path)
                    && input.template_sha256 == pin.digest
                    && input.template_bytes == pin.bytes
                    && same_names(&input.membership.targets, targets)
                    && same_names(&input.membership.all_features, &["keyboard_profiles"])
                    && input.membership.any_features.is_empty()
                    && input.membership.none_features.is_empty()
                    && input.member_owners.is_empty(),
                "carrier membership/template evidence changed"
            );
            let rules = core
                .metadata
                .source_rules
                .get(&input.path)
                .ok_or_else(|| eyre::eyre!("carrier lacks explicit sparse ownership"))?;
            ensure!(
                rules.len() == 1
                    && rules[0].input == input.path
                    && rules[0].template
                    && same_names(&rules[0].when.targets, targets)
                    && same_names(&rules[0].when.all_features, &["keyboard_profiles"])
                    && rules[0].when.any_features.is_empty()
                    && rules[0].when.none_features.is_empty(),
                "actual carrier ownership differs from reviewed sparse rule"
            );
            validate_template(pin, &core.read_source(&input.path)?)?;
            ensure!(input.witnesses.len() == 20, "incomplete carrier witnesses");
            let mut contexts = BTreeSet::new();
            let mut oids = BTreeSet::new();
            for witness in &input.witnesses {
                let (environment, target) = witness
                    .context
                    .split_once('/')
                    .ok_or_else(|| eyre::eyre!("invalid context"))?;
                let oid = historical_oid(&input.path, environment, target)?;
                let raw_pin = oid.map(raw_pin).transpose()?;
                let features = if environment == "dev" { &ON[..] } else { &[] };
                ensure!(
                    contexts.insert(witness.context.clone())
                        && pinned_commits().get(&witness.context) == Some(&witness.source_commit)
                        && witness.present == oid.is_some()
                        && witness.raw_oid.as_deref() == oid
                        && witness.raw_sha256.as_deref() == raw_pin.map(|p| p.digest)
                        && witness.raw_bytes == raw_pin.map(|p| p.bytes)
                        && witness.mode.as_deref() == oid.map(|_| "100644")
                        && same_names(&witness.explicit_features, features)
                        && witness.features_origin
                            == "reviewed_current_explicit_reconstruction_not_historical_manifest",
                    "carrier historical cell changed"
                );
                core.context(target, features)?;
                if let Some(oid) = oid {
                    oids.insert(oid.to_owned());
                }
            }
            ensure!(
                input.raw_oids.len() == oids.len()
                    && input.raw_oids.iter().cloned().collect::<BTreeSet<_>>() == oids,
                "raw variant set changed"
            );
        }
        ensure!(
            seen == PATHS.into_iter().map(str::to_owned).collect(),
            "unexpected source path"
        );
        let raw = read_git_blobs(
            &core.repository,
            &RAW_PINS.iter().map(|p| p.oid.to_owned()).collect(),
        )?;
        for pin in RAW_PINS {
            validate_raw(pin, &raw[pin.oid])?;
        }
        let inventory = discover_core_source_files(&core.core)?;
        ensure!(
            PATHS.iter().all(|path| inventory.contains(*path)),
            "missing authored carriers"
        );
        Ok(Self {
            core,
            inventory,
            raw,
        })
    }
    fn render(&self, path: &str, context: &ProjectionContext) -> Result<Option<Vec<u8>>> {
        let selection = self
            .core
            .selection_for_assertion(context, &self.inventory)?;
        let Some(input) = selection.inputs.get(path) else {
            ensure!(
                selection.omitted_paths.contains(path),
                "source omitted without explicit ownership"
            );
            return Ok(None);
        };
        ensure!(input.input == path, "unexpected alternative source");
        let source = self.core.read_source(path)?;
        validate_template(template_pin(path)?, &source)?;
        Ok(Some(
            render_java_source(std::str::from_utf8(&source)?, context)?.into_bytes(),
        ))
    }
    fn assert_context(&self, context: &ProjectionContext) -> Result<()> {
        let target = target_id(context);
        for path in PATHS {
            let present =
                context.features["keyboard_profiles"] && (!d2_only(path) || is_d2(target));
            let body = self.render(path, context)?;
            if !present {
                ensure!(body.is_none(), "disabled carrier included: {path}");
                continue;
            }
            let oid = historical_oid(path, "dev", target)?
                .ok_or_else(|| eyre::eyre!("expected carrier missing"))?;
            assert_eq!(
                body.as_deref(),
                Some(self.raw[oid].as_slice()),
                "{path}/{target}"
            );
        }
        Ok(())
    }
}
fn pinned_commits() -> BTreeMap<String, String> {
    CONTEXT_COMMITS
        .into_iter()
        .map(|(c, h)| (c.to_owned(), h.to_owned()))
        .collect()
}
fn same_names(actual: &[String], expected: &[&str]) -> bool {
    actual.len() == expected.len()
        && actual.iter().map(String::as_str).collect::<BTreeSet<_>>()
            == expected.iter().copied().collect()
}
fn target_id(context: &ProjectionContext) -> &str {
    if context.minecraft_version == "1.21" {
        "1.21.0"
    } else {
        &context.minecraft_version
    }
}
fn is_d2(target: &str) -> bool {
    matches!(target, "1.19.2" | "1.19.4")
}
fn d2_only(path: &str) -> bool {
    PATHS[7..].contains(&path)
}
fn membership_targets(path: &str) -> &'static [&'static str] {
    if d2_only(path) { &TARGETS[..2] } else { &[] }
}
fn template_pin(path: &str) -> Result<TemplatePin> {
    TEMPLATE_PINS
        .iter()
        .find(|pin| pin.path == path)
        .copied()
        .ok_or_else(|| eyre::eyre!("unknown template"))
}
fn raw_pin(oid: &str) -> Result<Pin> {
    RAW_PINS
        .iter()
        .find(|pin| pin.oid == oid)
        .copied()
        .ok_or_else(|| eyre::eyre!("unknown raw witness"))
}
fn basic_oid(path: &str) -> Result<&'static str> {
    Ok(match path {
        "src/main/java/ca/teamdman/sfm/client/keybinding/SFMKeyModifier.java" => {
            "e7c504859e8c92707fea85afc1f3b3d151be7b81"
        }
        "src/main/java/ca/teamdman/sfm/client/keybinding/SFMKeyStroke.java" => {
            "08e794eee9faa52e2bca9f6dae5ffde362276e03"
        }
        "src/main/java/ca/teamdman/sfm/client/keybinding/SFMKeySequence.java" => {
            "5338b77f87274c48c7f43e1669fb04348c7bfda7"
        }
        "src/main/java/ca/teamdman/sfm/client/keybinding/SFMKeyBinding.java" => {
            "0c0fab98ebea8f72246f0e0e1b054576a4f2b6df"
        }
        "src/main/java/ca/teamdman/sfm/client/keybinding/SFMKeyInputEvent.java" => {
            "6518c7e2642f6b8db7c2df5610df58a5b43db030"
        }
        "src/main/java/ca/teamdman/sfm/client/keybinding/SFMKeyBindingSnapshot.java" => {
            "b5310df5876ed25447da9c1e6dd19d86030a11d7"
        }
        "src/main/java/ca/teamdman/sfm/client/keybinding/SFMActionInvocationIntent.java" => {
            "2be8bcf30f81cd259c56cb40d1f53d3d2b760530"
        }
        "src/main/java/ca/teamdman/sfm/client/keybinding/SFMKeyBindingOverride.java" => {
            "0746adfd0a11768a53cf5879ce37d7d5e4b07eb9"
        }
        "src/main/java/ca/teamdman/sfm/client/keybinding/SFMKeyBindingConflict.java" => {
            "409b2bcb96299bdf1fe766ab2aa2863c29a4637f"
        }
        "src/main/java/ca/teamdman/sfm/client/keybinding/SFMKeyBindingMatchResult.java" => {
            "00a3c108aa7e5e62e21cec73632bc8de5bac0288"
        }
        _ => return Err(eyre::eyre!("unreviewed carrier")),
    })
}
fn historical_oid(path: &str, environment: &str, target: &str) -> Result<Option<&'static str>> {
    ensure!(
        matches!(environment, "release" | "dev") && TARGETS.contains(&target),
        "invalid frozen context"
    );
    if environment == "release" || (d2_only(path) && !is_d2(target)) {
        return Ok(None);
    }
    Ok(Some(if path == BINDING && !is_d2(target) {
        OLDER_BINDING_OID
    } else {
        basic_oid(path)?
    }))
}
fn validate_raw(pin: Pin, body: &[u8]) -> Result<()> {
    ensure!(
        body.len() == pin.bytes
            && sha256(body) == pin.digest
            && !body.contains(&b'\r')
            && body.ends_with(b"\n")
            && !body.ends_with(b"\n\n")
            && !body.starts_with(&[0xef, 0xbb, 0xbf]),
        "changed raw carrier bytes/newlines"
    );
    std::str::from_utf8(body)?;
    Ok(())
}
fn validate_template(pin: TemplatePin, body: &[u8]) -> Result<()> {
    ensure!(
        body.len() == pin.bytes && sha256(body) == pin.digest,
        "changed reviewed authored carrier"
    );
    Ok(())
}

#[test]
fn keyboard_carriers_reconstruct_all_two_hundred_frozen_cells() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut present = 0;
    let mut absent = 0;
    for (context, _) in CONTEXT_COMMITS {
        let (environment, target) = context.split_once('/').expect("fixed context");
        let features = if environment == "dev" { &ON[..] } else { &[] };
        let explicit = fixture.core.context(target, features)?;
        fixture.assert_context(&explicit)?;
        for path in PATHS {
            let expected = historical_oid(path, environment, target)?;
            assert_eq!(
                fixture.render(path, &explicit)?.as_deref(),
                expected.map(|oid| fixture.raw[oid].as_slice())
            );
            if expected.is_some() {
                present += 1;
            } else {
                absent += 1;
            }
        }
    }
    assert_eq!((present, absent), (76, 124));
    Ok(())
}

#[test]
fn keyboard_carriers_all_original_tree_memberships_bind_exact_raw_objects() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut cells = 0;
    for (context, commit) in CONTEXT_COMMITS {
        let (environment, target) = context.split_once('/').expect("fixed context");
        let mut command = frozen_git_command(&fixture.core.repository);
        command.args([
            "-c",
            "protocol.allow=never",
            "-c",
            "core.fsmonitor=false",
            "ls-tree",
            "-r",
            commit,
            "--",
        ]);
        for path in PATHS {
            command.arg(format!("platform/minecraft/{path}"));
        }
        let output = command.output()?;
        ensure!(
            output.status.success() && output.stdout.len() <= 8192,
            "bounded frozen tree query failed"
        );
        let mut found = BTreeMap::new();
        for line in std::str::from_utf8(&output.stdout)?.lines() {
            let (header, path) = line
                .split_once('\t')
                .ok_or_else(|| eyre::eyre!("invalid tree framing"))?;
            let fields = header.split_whitespace().collect::<Vec<_>>();
            ensure!(
                fields.len() == 3 && fields[0] == "100644" && fields[1] == "blob",
                "unexpected historical mode/type"
            );
            ensure!(
                found
                    .insert(path.to_owned(), fields[2].to_owned())
                    .is_none(),
                "duplicate tree path"
            );
        }
        for path in PATHS {
            let historical = historical_oid(path, environment, target)?;
            assert_eq!(
                found
                    .get(&format!("platform/minecraft/{path}"))
                    .map(String::as_str),
                historical
            );
            cells += 1;
        }
    }
    assert_eq!(cells, 200);
    Ok(())
}

#[test]
fn keyboard_carriers_feature_off_controls_omit_before_source_read() -> Result<()> {
    let mut fixture = Fixture::load()?;
    let catalog = CoreCatalog::load(&fixture.core.repository, &fixture.core.repository)?;
    assert_eq!(catalog.catalog.0.len(), 20);
    fixture.core.core = fixture
        .core
        .core
        .join("deliberately_missing_keyboard_carrier_sources");
    for key in catalog.catalog.0.keys() {
        let context = fixture.core.historical_feature_off_catalog_context(key)?;
        assert!(
            !context.features["keyboard_profiles"],
            "historical control unexpectedly enabled keyboard ownership"
        );
        fixture.assert_context(&context)?;
    }
    Ok(())
}

#[test]
fn keyboard_carriers_independent_owner_masks_and_prerequisites_fail_closed() -> Result<()> {
    let fixture = Fixture::load()?;
    let mut cases = 0;
    for target in TARGETS {
        for features in [
            &[][..],
            &["client_actions"][..],
            &ON[..],
            &["client_theme"][..],
            &["workspace_panels"][..],
            &[
                "client_actions",
                "keyboard_profiles",
                "client_theme",
                "workspace_panels",
            ][..],
        ] {
            fixture.assert_context(&fixture.core.context(target, features)?)?;
            cases += 1;
        }
        assert!(
            fixture
                .core
                .context(target, &["keyboard_profiles"])
                .is_err()
        );
        assert!(
            fixture
                .core
                .context(target, &["keyboard_carrier_unregistered_owner"])
                .is_err()
        );
        let standalone = fixture.core.context(target, &ON)?;
        for unrelated in [
            "workspace_panels",
            "client_theme",
            "developer_tools",
            "packet_values",
        ] {
            assert!(!standalone.features[unrelated]);
        }
    }
    assert_eq!(cases, 60);
    Ok(())
}

#[test]
fn keyboard_carriers_version_members_do_not_dispatch_on_environment_or_projection_key() -> Result<()>
{
    let fixture = Fixture::load()?;
    for target in TARGETS {
        for environment in ["release", "dev"] {
            let mut on = fixture.core.context(target, &ON)?;
            on.environment = environment.to_owned();
            on.projection_key = format!("review/carriers/{environment}/{target}");
            on.preset = on.projection_key.clone();
            fixture.assert_context(&on)?;
            let binding =
                String::from_utf8(fixture.render(BINDING, &on)?.expect("binding selected"))?;
            assert_eq!(binding.contains("ResourceLocation"), is_d2(target));
            assert_eq!(binding.contains("situationId"), is_d2(target));
            if target == "1.21.0" {
                assert_eq!(on.minecraft_version, "1.21");
            }
            let mut off = fixture.core.context(target, &[])?;
            off.environment = environment.to_owned();
            off.projection_key = format!("review/carriers-off/{environment}/{target}");
            off.preset = off.projection_key.clone();
            fixture.assert_context(&off)?;
        }
    }
    Ok(())
}

#[test]
fn keyboard_carriers_shared_edit_propagates_both_version_groups_without_raw_mutation() -> Result<()>
{
    let fixture = Fixture::load()?;
    let original = fixture.core.read_source(BINDING)?;
    let source = std::str::from_utf8(&original)?;
    let anchor = "withEnabled(boolean value)";
    ensure!(
        source.matches(anchor).count() == 1,
        "shared record method anchor changed"
    );
    let edited = source
        .replace(anchor, "withEnabled(boolean nextEnabled)")
        .replace("sequence, value);", "sequence, nextEnabled);");
    let mut cells = 0;
    for target in TARGETS {
        let context = fixture.core.context(target, &ON)?;
        let before = render_java_source(source, &context)?;
        let after = render_java_source(&edited, &context)?;
        assert_eq!(
            after,
            before
                .replace(anchor, "withEnabled(boolean nextEnabled)")
                .replace("sequence, value);", "sequence, nextEnabled);")
        );
        assert_eq!(after.contains("situationId"), is_d2(target));
        cells += 1;
    }
    assert_eq!(cells, 10);
    let context = fixture.core.context("1.19.2", &ON)?;
    for malformed in [
        format!("{{% if features.carrier_unregistered %}}\n{source}{{% endif %}}\n"),
        format!("{{% if environment %}}\n{source}{{% endif %}}\n"),
    ] {
        assert!(render_java_source(&malformed, &context).is_err());
    }
    let pin = raw_pin(OLDER_BINDING_OID)?;
    let mut mutated = fixture.raw[OLDER_BINDING_OID].clone();
    mutated[0] = b'P';
    assert!(validate_raw(pin, &mutated).is_err());
    let mut extra_newline = fixture.raw[OLDER_BINDING_OID].clone();
    extra_newline.push(b'\n');
    assert!(validate_raw(pin, &extra_newline).is_err());
    let mut bom = vec![0xef, 0xbb, 0xbf];
    bom.extend_from_slice(&fixture.raw[OLDER_BINDING_OID]);
    assert!(validate_raw(pin, &bom).is_err());
    let mut authored = original;
    authored[0] = b'P';
    assert!(validate_template(template_pin(BINDING)?, &authored).is_err());
    Ok(())
}

#[test]
fn keyboard_carriers_pure_model_operations_preserve_real_dependency_boundary() -> Result<()> {
    let fixture = Fixture::load()?;
    for (path, required) in [
        (PATHS[1], &["Set.copyOf(EnumSet.copyOf(modifiers))"][..]),
        (
            PATHS[2],
            &[
                "strokes = List.copyOf(strokes);",
                "A key sequence needs at least one stroke",
            ][..],
        ),
        (
            PATHS[4],
            &[
                "public enum Type { PRESS, RELEASE, REPEAT }",
                "Set.copyOf(EnumSet.copyOf(modifiers))",
            ][..],
        ),
        (
            PATHS[5],
            &[
                ".sorted(Comparator.comparing(SFMKeyBinding::bindingId))",
                ".toList();",
            ][..],
        ),
        (
            PATHS[7],
            &[
                "definition.actionId()",
                "definition.commandDraft()",
                "Binding override id does not match definition",
            ][..],
        ),
        (
            PATHS[8],
            &["bindingIds = bindingIds.stream().sorted().toList();"][..],
        ),
        (
            PATHS[9],
            &[
                "boolean consumed,",
                "new SFMKeyBindingMatchResult(List.of(), false, List.of())",
                "intents = List.copyOf(intents);",
                "conflicts = List.copyOf(conflicts);",
            ][..],
        ),
    ] {
        let body = std::str::from_utf8(&fixture.raw[basic_oid(path)?])?;
        for marker in required {
            ensure!(
                body.contains(marker),
                "lost pure carrier contract: {path}/{marker}"
            );
        }
    }
    for path in PATHS {
        let body = std::str::from_utf8(&fixture.raw[basic_oid(path)?])?;
        for forbidden in [
            "java.nio.file.",
            "org.lwjgl.",
            "ProcessBuilder",
            "Runtime.getRuntime",
            "SFMKeyBindingService",
            "SFMClientActionExecutor",
            "clipboard",
            "SFMPacket",
        ] {
            assert!(
                !body.contains(forbidden),
                "carrier gained effect/provider operation: {path}/{forbidden}"
            );
        }
    }
    Ok(())
}

#[test]
fn keyboard_carriers_real_collector_omits_malformed_off_inputs_and_renders_java_even_template_false()
-> Result<()> {
    let fixture = Fixture::load()?;
    let temp = tempfile::tempdir()?;
    let core_root = temp.path().join(CORE_ROOT);
    std::fs::create_dir_all(&core_root)?;
    let mut metadata = fixture.core.metadata.clone();
    metadata
        .source_rules
        .retain(|path, _| PATHS.contains(&path.as_str()));
    for rules in metadata.source_rules.values_mut() {
        for rule in rules {
            rule.template = false;
        }
    }
    let inventory = PATHS
        .into_iter()
        .map(str::to_owned)
        .collect::<BTreeSet<_>>();
    let off = fixture.core.context("1.19.2", &[])?;
    let selected = select_core_inputs(&metadata, &off, &inventory)?;
    for (output, input) in &selected.inputs {
        ensure!(
            !output.starts_with("src/"),
            "off collector unexpectedly selected carrier"
        );
        let source = read_bounded(
            &checked_file(&fixture.core.core, &input.input)?,
            16 * 1024 * 1024,
        )?;
        let destination = core_root.join(&input.input);
        std::fs::create_dir_all(destination.parent().expect("portable selected parent"))?;
        std::fs::write(destination, source)?;
    }
    for path in PATHS {
        let destination = core_root.join(path);
        std::fs::create_dir_all(destination.parent().expect("source parent"))?;
        std::fs::write(destination, [0xff, 0xfe])?;
    }
    let off_artifacts = collect_core_artifacts(&core_root, &selected, &off)?;
    assert!(PATHS.iter().all(|path| !off_artifacts.contains_key(*path)));
    let on = fixture.core.context("1.19.2", &ON)?;
    let selected = select_core_inputs(&metadata, &on, &inventory)?;
    assert!(collect_core_artifacts(&core_root, &selected, &on).is_err());
    for path in PATHS {
        std::fs::write(core_root.join(path), fixture.core.read_source(path)?)?;
    }
    let artifacts = collect_core_artifacts(&core_root, &selected, &on)?;
    let mut declared_templates = metadata.clone();
    for rules in declared_templates.source_rules.values_mut() {
        for rule in rules {
            rule.template = true;
        }
    }
    let declared = select_core_inputs(&declared_templates, &on, &inventory)?;
    let declared_artifacts = collect_core_artifacts(&core_root, &declared, &on)?;
    for path in PATHS {
        assert!(
            !selected.inputs[path].template,
            "Java test must exercise automatic template semantics"
        );
        assert!(declared.inputs[path].template);
        assert_eq!(
            artifacts[path].source_bytes, declared_artifacts[path].source_bytes,
            "Java metadata must not change authored source bytes"
        );
        assert_eq!(
            artifacts[path].output_bytes, declared_artifacts[path].output_bytes,
            "Java metadata must not change production output bytes"
        );
        let authored = fixture.core.read_source(path)?;
        let rendered = render_java_source(std::str::from_utf8(&authored)?, &on)?.into_bytes();
        assert_eq!(artifacts[path].source_bytes, authored);
        assert!(
            artifacts[path].output_bytes.ends_with(&rendered),
            "generated body did not use production rendering"
        );
    }
    Ok(())
}
