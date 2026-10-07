//! Post-promotion regression for the four staged redstone/capability inputs.
//!
//! Production membership and controlled rendering require real core templates
//! and registered flags. Ignored staging paths and historical source trees are
//! never input fallbacks. Frozen blobs are bounded raw/normalized witnesses.
//! No enabled Java-build, capability lifecycle or gameplay proof is made here.

#![cfg(test)]

use super::candidate_lock::checked_file;
use super::context::ProjectionContext;
use super::core_inputs::discover_core_source_files;
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

const INTERFACE: &str =
    "src/main/java/ca/teamdman/sfm/common/capability/IRedstoneSignalStorage.java";
const STORAGE: &str = "src/main/java/ca/teamdman/sfm/common/capability/RedstoneSignalStorage.java";
const PROVIDER: &str =
    "src/main/java/ca/teamdman/sfm/common/capability/RedstoneSignalCapabilityProvider.java";
const CAPS: &str = "src/main/java/ca/teamdman/sfm/common/capability/SFMWellKnownCapabilities.java";
const PATHS: [&str; 4] = [INTERFACE, STORAGE, PROVIDER, CAPS];
const TARGETS: [&str; 10] = [
    "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21.0", "1.21.1",
    "26.1.2",
];
const LIVE: &str = "redstone_live_read";
const BUFFER: &str = "redstone_buffer_storage";
const DOCS: &str = "redstone_signal_documentation";
const IMAGE: &str = "image_resources";
const FLAGS: [&str; 4] = [LIVE, BUFFER, DOCS, IMAGE];
const ALL_FLAGS: [&str; 5] = [LIVE, BUFFER, DOCS, IMAGE, "packet_values"];
const LEDGER: &str = "docs/tasks/sfm-core-redstone-capability-slice.json";
const CORE_PREFIX: &str = "platform/minecraft/core-liquid-template/";
const STAGE_PREFIX: &str =
    "platform/cli/sfm-propagate-changes/target/core-redstone-capability-stage-v1/";
const NORMALIZATION: &str = "sfm:java_lf_final_newline@1";
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
struct Golden {
    oid: &'static str,
    raw_digest: &'static str,
    raw_bytes: usize,
    normalized_digest: &'static str,
    normalized_bytes: usize,
    crlf_count: usize,
    append_lf: bool,
}

const GOLDENS: [Golden; 15] = [
    Golden {
        oid: "079789e02a1ff21e2ac95ed4490179bf50af418c",
        raw_digest: "sha256:633d86efa23949955be5530c2083d155e87738e31ba0355de7d0c8df7aa0e5c1",
        raw_bytes: 1677,
        normalized_digest: "sha256:3c07f483072e0783405a70c98de7bdb0c9d8ecb82b18f3de9a65a6d44649d94c",
        normalized_bytes: 1640,
        crlf_count: 37,
        append_lf: false,
    },
    Golden {
        oid: "12e2357bd94aaa6283640043f2daadc6f8059a6c",
        raw_digest: "sha256:e82883102cde73b05f537689e4a86223131cac04ec24bfc0a17f6e6b4a337b91",
        raw_bytes: 2091,
        normalized_digest: "sha256:0ea0c8facae0692902a0b4fa4f26bcf9eae32e6b59c062c28e80b5ad710b94dc",
        normalized_bytes: 2011,
        crlf_count: 81,
        append_lf: true,
    },
    Golden {
        oid: "24c87cec392d774ca8a278eb5392747de0b14feb",
        raw_digest: "sha256:9819b967e0230db444ad5aa6ee0b7c53cf50eb28bcddc480a677de562a76a021",
        raw_bytes: 1749,
        normalized_digest: "sha256:20031922f7cb0e4275ce5e04147fe50d45c613fcc7cbf60109243112ea46f311",
        normalized_bytes: 1714,
        crlf_count: 35,
        append_lf: false,
    },
    Golden {
        oid: "2f735e61f4022e555adea5008fd4fbe8ac948849",
        raw_digest: "sha256:f1319c9c141621b47856ff3a2a5418384fa212ad802458ef848b2cc06f423f3c",
        raw_bytes: 748,
        normalized_digest: "sha256:6b0d600321bbb6a439d9eb4bc3a04c2f0fe5ec4c2c08b9db6df0785e09c2b364",
        normalized_bytes: 727,
        crlf_count: 21,
        append_lf: false,
    },
    Golden {
        oid: "3ce6c271d996308b07d0716f5bf275c8d8a8bbc0",
        raw_digest: "sha256:c47d551347a7853dc5c0e56f54f8454c226530944676d01de862bfbf2df5c01f",
        raw_bytes: 1644,
        normalized_digest: "sha256:8a0a34b2cb76ee0c13d791ceb286480d97556a914e34b9c5e8da4a59dcfe44da",
        normalized_bytes: 1611,
        crlf_count: 33,
        append_lf: false,
    },
    Golden {
        oid: "447cab5262c5c1ac3ec63cce70bc5f08d5131692",
        raw_digest: "sha256:2d4516c550aa4b7bff2ee9d35eeb5018a0684340bb77a30f41b5f5be037626a9",
        raw_bytes: 1879,
        normalized_digest: "sha256:6dfb2492f8741f2a6c372a3ff40974a9a714e7b002f4db817cda9e17c8fb4972",
        normalized_bytes: 1805,
        crlf_count: 75,
        append_lf: true,
    },
    Golden {
        oid: "4bf05d7a71619478f5bbfd9b8ab383bca3250da1",
        raw_digest: "sha256:ea6d9327a1634262350744b80ec60a246edf12eb1f28861400ff5d7ef0e10a83",
        raw_bytes: 1891,
        normalized_digest: "sha256:07a7a23f1f10f125dae276c0a03615753821884f7f8ae099320b7ade25112886",
        normalized_bytes: 1860,
        crlf_count: 31,
        append_lf: false,
    },
    Golden {
        oid: "6d1b0da801a2b86e6ea86f550f1ffd5b4fdf5f69",
        raw_digest: "sha256:30cc2d52a956aaee15a865d6abaedad98c5e38e2c036e8e108d19ab649ca4954",
        raw_bytes: 2051,
        normalized_digest: "sha256:2ed4247add6521bea8632b94f768b7158009475c2fa176ee949563d68bb9dbf8",
        normalized_bytes: 1970,
        crlf_count: 81,
        append_lf: false,
    },
    Golden {
        oid: "83e033c7545edfb8c5bae5cff6bbf38258409317",
        raw_digest: "sha256:1fad540a3d4fe073f3b441ddd3c5a6317c4094e92fd88cde54d2b557280c47d4",
        raw_bytes: 3379,
        normalized_digest: "sha256:67af1ebe8cf951b401e3378f16b42646bb3822d478c4ed7814485ffd41df5e45",
        normalized_bytes: 3289,
        crlf_count: 90,
        append_lf: false,
    },
    Golden {
        oid: "baf73c25791fda00885afae4ac959d33cf12701c",
        raw_digest: "sha256:54235062379efa99832f035574fcd366c1e0a68a95c4af3ac959644689823874",
        raw_bytes: 1875,
        normalized_digest: "sha256:dcfbc85cca2eec8a88c3c8d0e08689dc7fee0b2078ba08a9ce2f277d65254f69",
        normalized_bytes: 1801,
        crlf_count: 75,
        append_lf: true,
    },
    Golden {
        oid: "ccb39e12abc0acc9f2fedfa1ecfe3537ac710b8d",
        raw_digest: "sha256:1c4670d4f7352096b0e1dae96f9c0d0f7538bb99a1c7f6fb1e5cc894ed345698",
        raw_bytes: 1965,
        normalized_digest: "sha256:3ce92563e2a793312074e45e81edee6ca34734da60487dfac3c1f7cc263a7aed",
        normalized_bytes: 1889,
        crlf_count: 77,
        append_lf: true,
    },
    Golden {
        oid: "d234c35647106c4f596730dca1ba0ab523ac74aa",
        raw_digest: "sha256:dc0a0ef1d6e26918c8ac6930950e6e9c5fbe79c57db5378c1da41524c8b2b873",
        raw_bytes: 748,
        normalized_digest: "sha256:2ea1d60497dcffdcc57d888454cf2f31b096f8b93036b62ef864a2f7eaa9dd68",
        normalized_bytes: 727,
        crlf_count: 21,
        append_lf: false,
    },
    Golden {
        oid: "dbd41464a96c9552da977e6e9d15adda2dafdab3",
        raw_digest: "sha256:b5cd3ee9a56f7ad19005f3f25be5566b0cfb29c579ec63e6b867acd80c7d9c04",
        raw_bytes: 1726,
        normalized_digest: "sha256:5af127090b93b2a4c72203a94023345811a70ecab0d9f59e6c151c2672eb96fe",
        normalized_bytes: 1692,
        crlf_count: 34,
        append_lf: false,
    },
    Golden {
        oid: "fbb71dba6ea360bfdc3f02d1b06308bd7065f540",
        raw_digest: "sha256:361b937e4c2d38b76f21ca7a7ee01da265ab011e90e2fe64182e4c52c45670c2",
        raw_bytes: 1736,
        normalized_digest: "sha256:98fa45f577f769b848c7cfc92b35d0a44a92db11be65b2f2b62e6ed10ac415b8",
        normalized_bytes: 1702,
        crlf_count: 34,
        append_lf: false,
    },
    Golden {
        oid: "ff1a75fc6845fd38d9c46a897c9ffeed15d1a755",
        raw_digest: "sha256:5741c85f295a855488a1cd1b018c648f44b111b13b428e3bf77920e5ef88d045",
        raw_bytes: 2468,
        normalized_digest: "sha256:7bddb85626c48edd44665f645bb3cb286554588857016c11c19394e10990255e",
        normalized_bytes: 2408,
        crlf_count: 60,
        append_lf: false,
    },
];

#[derive(Clone, Copy)]
struct TemplateGolden {
    path: &'static str,
    digest: &'static str,
    bytes: usize,
}

const TEMPLATES: [TemplateGolden; 4] = [
    TemplateGolden {
        path: "src/main/java/ca/teamdman/sfm/common/capability/IRedstoneSignalStorage.java",
        digest: "sha256:d6e751ebaa1f558063103d2ee3718e52512e56f89a929f198be92781e0ab8259",
        bytes: 980,
    },
    TemplateGolden {
        path: "src/main/java/ca/teamdman/sfm/common/capability/RedstoneSignalStorage.java",
        digest: "sha256:5f988936420c6f411b0d389fd51f992329cec0877d24ba3e2b332c1696f4b5cb",
        bytes: 3972,
    },
    TemplateGolden {
        path: "src/main/java/ca/teamdman/sfm/common/capability/RedstoneSignalCapabilityProvider.java",
        digest: "sha256:130c6b0c31575e04b33c031567e98044c92f3709e257893a17d0c85af6ac0f9f",
        bytes: 6527,
    },
    TemplateGolden {
        path: "src/main/java/ca/teamdman/sfm/common/capability/SFMWellKnownCapabilities.java",
        digest: "sha256:295f1a9cf7eab590006d97d2e8e7d3fe4a1ab0b4bc182a0a00cc2754dc5a9299",
        bytes: 5113,
    },
];

#[derive(Facet)]
struct Ledger {
    schema: String,
    context_commits: BTreeMap<String, String>,
    normalization: String,
    files: Vec<InputEvidence>,
}

#[derive(Facet)]
struct InputEvidence {
    path: String,
    staged_path: String,
    intended_core_path: String,
    template_sha256: String,
    template_bytes: usize,
    whole_source_membership: String,
    member_feature_owner: String,
    raw_variants: Vec<RawVariant>,
    witnesses: Vec<Witness>,
}

#[derive(Facet)]
struct RawVariant {
    oid: String,
    raw_sha256: String,
    raw_bytes: usize,
    crlf_to_lf_count: usize,
    appended_final_lf: bool,
    other_token_edits: bool,
    normalized_sha256: String,
    normalized_bytes: usize,
}

#[derive(Facet)]
struct Witness {
    context: String,
    source_commit: String,
    present: bool,
    raw_blob: String,
    raw_sha256: String,
    raw_bytes: usize,
    normalized_sha256: String,
    normalized_bytes: usize,
    mode: String,
    explicit_registered_features: Vec<String>,
    feature_context_origin: String,
    stage_preview_normalized_exact: bool,
}

struct RedstoneFixture {
    core: CoreTestFixture,
    inventory: BTreeSet<String>,
    normalized: BTreeMap<String, Vec<u8>>,
}

impl RedstoneFixture {
    fn load() -> Result<Self> {
        let core = CoreTestFixture::load()?;
        let source = String::from_utf8(read_bounded(
            &checked_file(&core.repository, LEDGER)?,
            1024 * 1024,
        )?)?;
        let ledger: Ledger = facet_json::from_str(&source)?;
        ensure!(
            ledger.schema == "sfm:core_redstone_capability_slice@1"
                && ledger.context_commits == pinned_commits()
                && ledger.normalization == NORMALIZATION
                && ledger.files.len() == 4,
            "redstone scope/identity/normalization changed"
        );
        for flag in FLAGS {
            let definition = core
                .features
                .0
                .get(flag)
                .ok_or_else(|| eyre::eyre!("redstone/image flag is not registered: {flag}"))?;
            ensure!(
                same_names(&definition.supported_targets, &TARGETS[..2])
                    && if flag == IMAGE {
                        same_names(&definition.requires, &["packet_values"])
                    } else {
                        definition.requires.is_empty()
                    },
                "redstone owners are independent; image requires the real value codec"
            );
        }
        let mut seen = BTreeSet::new();
        for input in ledger.files {
            let expected = template(&input.path)?;
            let name = input.path.rsplit('/').next().expect("fixed filename");
            ensure!(
                seen.insert(input.path.clone())
                    && input.intended_core_path == format!("{CORE_PREFIX}{}", input.path)
                    && input.staged_path == format!("{STAGE_PREFIX}{name}")
                    && input.template_sha256 == expected.digest
                    && input.template_bytes == expected.bytes
                    && input.whole_source_membership
                        == "all10_targets_unconditionally_present_no_feature_owner_excludes_file"
                    && input.member_feature_owner == owner(&input.path)?,
                "redstone staged/promoted template contract changed"
            );
            // Default common membership or one explicit always-true identity
            // predicate is acceptable; no feature can remove a baseline file.
            if let Some(rules) = core.metadata.source_rules.get(&input.path) {
                ensure!(
                    rules.len() == 1
                        && rules[0].input == input.path
                        && rules[0].when.targets.is_empty()
                        && rules[0].when.all_features.is_empty()
                        && rules[0].when.any_features.is_empty()
                        && rules[0].when.none_features.is_empty(),
                    "baseline redstone membership was made conditional"
                );
            }
            let mut variants = BTreeSet::new();
            for variant in input.raw_variants {
                let row = golden(&variant.oid)?;
                ensure!(
                    variants.insert(variant.oid.clone())
                        && variant.raw_sha256 == row.raw_digest
                        && variant.raw_bytes == row.raw_bytes
                        && variant.normalized_sha256 == row.normalized_digest
                        && variant.normalized_bytes == row.normalized_bytes
                        && variant.crlf_to_lf_count == row.crlf_count
                        && variant.appended_final_lf == row.append_lf
                        && !variant.other_token_edits,
                    "redstone raw/normalization variant changed"
                );
            }
            let mut expected_oids = BTreeSet::new();
            for target in TARGETS {
                expected_oids.insert(raw_oid(&input.path, target, false)?);
                expected_oids.insert(raw_oid(&input.path, target, is_d2(target))?);
            }
            ensure!(
                variants.iter().map(String::as_str).collect::<BTreeSet<_>>() == expected_oids,
                "redstone raw variant scope changed"
            );
            ensure!(input.witnesses.len() == 20, "incomplete redstone witnesses");
            let mut contexts = BTreeSet::new();
            for witness in input.witnesses {
                let commit = pinned_commits()
                    .get(&witness.context)
                    .cloned()
                    .ok_or_else(|| eyre::eyre!("unknown redstone witness context"))?;
                let (environment, target) = witness
                    .context
                    .split_once('/')
                    .ok_or_else(|| eyre::eyre!("invalid pinned redstone context"))?;
                let enabled = environment == "dev" && is_d2(target);
                let row = golden(raw_oid(&input.path, target, enabled)?)?;
                // The frozen ledger records four owner flags, not the later
                // packet-values prerequisite needed for current image validation.
                // Keep that original witness identity unchanged while validating
                // the explicitly dependency-closed current invocation context.
                let witness_flags = if enabled { &FLAGS[..] } else { &[][..] };
                let flags = if enabled { &ALL_FLAGS[..] } else { &[][..] };
                core.context(target, flags)?;
                ensure!(
                    contexts.insert(witness.context.clone())
                        && witness.source_commit == commit
                        && witness.present
                        && witness.raw_blob == row.oid
                        && witness.raw_sha256 == row.raw_digest
                        && witness.raw_bytes == row.raw_bytes
                        && witness.normalized_sha256 == row.normalized_digest
                        && witness.normalized_bytes == row.normalized_bytes
                        && witness.mode == "100644"
                        && same_names(&witness.explicit_registered_features, witness_flags)
                        && witness.feature_context_origin
                            == "reconstructed_independent_member_owner_not_historical_flag_claim"
                        && witness.stage_preview_normalized_exact,
                    "redstone full raw/normalized witness changed"
                );
            }
        }
        ensure!(
            seen == PATHS.into_iter().map(str::to_owned).collect(),
            "redstone path scope changed"
        );
        let raw = read_git_blobs(
            &core.repository,
            &GOLDENS.iter().map(|row| row.oid.to_owned()).collect(),
        )?;
        let mut normalized = BTreeMap::new();
        for row in GOLDENS {
            let bytes = &raw[row.oid];
            ensure!(
                bytes.len() == row.raw_bytes
                    && sha256(bytes) == row.raw_digest
                    && bytes.windows(2).filter(|pair| *pair == b"\r\n").count() == row.crlf_count
                    && (!bytes.ends_with(b"\n")) == row.append_lf,
                "redstone raw blob/count witness changed"
            );
            let output = normalize(bytes)?;
            ensure!(
                output.len() == row.normalized_bytes
                    && sha256(&output) == row.normalized_digest
                    && output.len()
                        == bytes.len() - row.crlf_count + if row.append_lf { 1 } else { 0 },
                "redstone normalized bytes or permitted edits changed"
            );
            normalized.insert(row.oid.to_owned(), output);
        }
        let inventory = discover_core_source_files(&core.core)?;
        ensure!(
            PATHS.iter().all(|path| inventory.contains(*path)),
            "staged redstone files have not been promoted to the real core"
        );
        Ok(Self {
            core,
            inventory,
            normalized,
        })
    }

    fn render(&self, path: &str, context: &ProjectionContext) -> Result<Vec<u8>> {
        let selection = self
            .core
            .selection_for_assertion(context, &self.inventory)?;
        let input = selection
            .inputs
            .get(path)
            .ok_or_else(|| eyre::eyre!("baseline redstone source omitted"))?;
        ensure!(input.input == path, "unreviewed redstone alternate input");
        // Production membership is evaluated before this bounded source read.
        let source = self.core.read_source(path)?;
        let expected = template(path)?;
        ensure!(
            source.len() == expected.bytes
                && sha256(&source) == expected.digest
                && !source.contains(&b'\r')
                && source.ends_with(b"\n"),
            "redstone promoted template bytes changed"
        );
        Ok(render_java_source(std::str::from_utf8(&source)?, context)?.into_bytes())
    }

    fn assert_expected(&self, context: &ProjectionContext, target: &str) -> Result<()> {
        for path in PATHS {
            let enabled = context.features[owner(path)?];
            assert_eq!(
                self.render(path, context)?,
                self.normalized[raw_oid(path, target, enabled)?],
                "{path} / {target}"
            );
        }
        Ok(())
    }

    fn body(&self, path: &str, context: &ProjectionContext) -> Result<String> {
        Ok(String::from_utf8(self.render(path, context)?)?)
    }
}

fn normalize(raw: &[u8]) -> Result<Vec<u8>> {
    for (index, byte) in raw.iter().enumerate() {
        if *byte == b'\r' {
            ensure!(raw.get(index + 1) == Some(&b'\n'), "lone CR is not allowed");
        }
    }
    let mut source = std::str::from_utf8(raw)?.replace("\r\n", "\n");
    if !source.ends_with('\n') {
        source.push('\n');
    }
    Ok(source.into_bytes())
}

fn pinned_commits() -> BTreeMap<String, String> {
    CONTEXT_COMMITS
        .into_iter()
        .map(|(context, commit)| (context.to_owned(), commit.to_owned()))
        .collect()
}

fn is_d2(target: &str) -> bool {
    matches!(target, "1.19.2" | "1.19.4")
}

fn same_names(actual: &[String], expected: &[&str]) -> bool {
    actual.len() == expected.len()
        && actual.iter().map(String::as_str).collect::<BTreeSet<_>>()
            == expected.iter().copied().collect()
}

fn template(path: &str) -> Result<TemplateGolden> {
    TEMPLATES
        .iter()
        .find(|row| row.path == path)
        .copied()
        .ok_or_else(|| eyre::eyre!("unexpected promoted redstone input"))
}

fn golden(oid: &str) -> Result<Golden> {
    GOLDENS
        .iter()
        .find(|row| row.oid == oid)
        .copied()
        .ok_or_else(|| eyre::eyre!("unexpected redstone raw object"))
}

fn owner(path: &str) -> Result<&'static str> {
    match path {
        INTERFACE => Ok(DOCS),
        STORAGE => Ok(BUFFER),
        PROVIDER => Ok(LIVE),
        CAPS => Ok(IMAGE),
        _ => Err(eyre::eyre!("unexpected redstone member owner")),
    }
}

fn raw_oid(path: &str, target: &str, enabled: bool) -> Result<&'static str> {
    ensure!(TARGETS.contains(&target), "unexpected redstone target");
    ensure!(
        !enabled || is_d2(target),
        "unsupported redstone member owner"
    );
    match path {
        INTERFACE if enabled => Ok("d234c35647106c4f596730dca1ba0ab523ac74aa"),
        INTERFACE => Ok("2f735e61f4022e555adea5008fd4fbe8ac948849"),
        STORAGE if enabled => Ok("6d1b0da801a2b86e6ea86f550f1ffd5b4fdf5f69"),
        STORAGE => match target {
            "1.19.2" | "1.19.4" | "1.20" | "1.20.1" => {
                Ok("baf73c25791fda00885afae4ac959d33cf12701c")
            }
            "1.20.2" | "1.20.3" | "1.20.4" => Ok("447cab5262c5c1ac3ec63cce70bc5f08d5131692"),
            "1.21.0" | "1.21.1" => Ok("12e2357bd94aaa6283640043f2daadc6f8059a6c"),
            "26.1.2" => Ok("ccb39e12abc0acc9f2fedfa1ecfe3537ac710b8d"),
            _ => unreachable!("validated targets"),
        },
        PROVIDER if enabled => Ok("83e033c7545edfb8c5bae5cff6bbf38258409317"),
        PROVIDER => match target {
            "1.19.2" | "1.19.4" | "1.20" | "1.20.1" | "1.20.2" => {
                Ok("079789e02a1ff21e2ac95ed4490179bf50af418c")
            }
            _ => Ok("ff1a75fc6845fd38d9c46a897c9ffeed15d1a755"),
        },
        CAPS if enabled => Ok("4bf05d7a71619478f5bbfd9b8ab383bca3250da1"),
        CAPS => match target {
            "1.19.2" | "1.19.4" | "1.20" | "1.20.1" => {
                Ok("fbb71dba6ea360bfdc3f02d1b06308bd7065f540")
            }
            "1.20.2" => Ok("24c87cec392d774ca8a278eb5392747de0b14feb"),
            "26.1.2" => Ok("dbd41464a96c9552da977e6e9d15adda2dafdab3"),
            _ => Ok("3ce6c271d996308b07d0716f5bf275c8d8a8bbc0"),
        },
        _ => Err(eyre::eyre!("unexpected redstone path")),
    }
}

#[test]
fn redstone_family_reconstructs_eighty_full_normalized_historical_witnesses() -> Result<()> {
    let fixture = RedstoneFixture::load()?;
    let mut cases = 0;
    for (name, _) in CONTEXT_COMMITS {
        let (environment, target) = name.split_once('/').expect("fixed contexts");
        let enabled = environment == "dev" && is_d2(target);
        let context = fixture
            .core
            .context(target, if enabled { &ALL_FLAGS[..] } else { &[][..] })?;
        fixture.assert_expected(&context, target)?;
        cases += 4;
    }
    assert_eq!(cases, 80);
    Ok(())
}

#[test]
fn normalization_only_changes_crlf_and_missing_terminal_lf() -> Result<()> {
    // Loading verifies all15 fixed raw and normalized hashes plus edit counts.
    let _fixture = RedstoneFixture::load()?;
    assert_eq!(normalize(b"")?, b"\n");
    assert_eq!(normalize(b"x")?, b"x\n");
    assert_eq!(normalize(b"x\r\n")?, b"x\n");
    assert_eq!(normalize(b" x \r\n\r\n")?, b" x \n\n");
    assert_eq!(normalize(b"x\n\n")?, b"x\n\n");
    assert!(normalize(b"x\ry").is_err());
    assert!(normalize(b"x\r").is_err());
    assert!(normalize(&[0xff]).is_err());
    assert_eq!(GOLDENS.iter().filter(|row| row.append_lf).count(), 4);
    Ok(())
}

#[test]
fn redstone_member_owners_are_independent_in_all_supported_d2_combinations() -> Result<()> {
    let fixture = RedstoneFixture::load()?;
    let mut bodies = 0;
    for target in &TARGETS[..2] {
        for mask in 0_u8..16 {
            let mut enabled = FLAGS
                .iter()
                .enumerate()
                .filter(|(index, _)| mask & (1 << index) != 0)
                .map(|(_, flag)| *flag)
                .collect::<Vec<_>>();
            if mask & 8 != 0 {
                enabled.push("packet_values");
            }
            let context = fixture.core.context(target, &enabled)?;
            fixture.assert_expected(&context, target)?;
            let provider = fixture.body(PROVIDER, &context)?;
            let storage = fixture.body(STORAGE, &context)?;
            let interface = fixture.body(INTERFACE, &context)?;
            let caps = fixture.body(CAPS, &context)?;
            assert_eq!(
                provider.contains("private record WorldSignal"),
                mask & 1 != 0
            );
            assert_eq!(provider.contains("return -200;"), mask & 1 != 0);
            assert_eq!(
                storage.contains("protected void onContentsChanged()"),
                mask & 2 != 0
            );
            assert_eq!(storage.contains("    private int value;"), mask & 2 != 0);
            assert_eq!(
                interface.contains("extract will always return 0.\n    boolean canExtract();"),
                mask & 4 != 0
            );
            assert_eq!(caps.contains("IMAGE_HANDLER"), mask & 8 != 0);
            bodies += 4;
        }
    }
    assert_eq!(bodies, 128);
    Ok(())
}

#[test]
fn genuine_mc_capability_and_serialization_apis_preserve_baseline_body() -> Result<()> {
    let fixture = RedstoneFixture::load()?;
    for target in TARGETS {
        let context = fixture.core.context(target, &[])?;
        fixture.assert_expected(&context, target)?;
        let storage = fixture.body(STORAGE, &context)?;
        let provider = fixture.body(PROVIDER, &context)?;
        let caps = fixture.body(CAPS, &context)?;
        assert!(storage.contains("public int value = 0;"));
        assert!(!provider.contains("private record WorldSignal"));
        match target {
            "1.19.2" | "1.19.4" | "1.20" | "1.20.1" => {
                assert!(storage.contains("net.minecraftforge.common.util.INBTSerializable"));
                assert!(caps.contains("ForgeCapabilities.ENERGY"));
                assert!(!provider.contains("IBlockCapabilityProvider"));
            }
            "1.20.2" => {
                assert!(storage.contains("net.neoforged.neoforge.common.util.INBTSerializable"));
                assert!(caps.contains("CapabilityManager.get(new CapabilityToken"));
                assert!(caps.contains("Capabilities.ENERGY"));
                assert!(!provider.contains("IBlockCapabilityProvider"));
            }
            "1.20.3" | "1.20.4" => {
                assert!(storage.contains("INBTSerializable<IntTag>"));
                assert!(provider.contains("IBlockCapabilityProvider"));
                assert!(caps.contains("Capabilities.EnergyStorage.BLOCK"));
            }
            "1.21.0" | "1.21.1" => {
                assert!(storage.contains("serializeNBT(HolderLookup.Provider provider)"));
                assert!(provider.contains("IBlockCapabilityProvider"));
                assert!(caps.contains("Capabilities.ItemHandler.BLOCK"));
            }
            "26.1.2" => {
                assert!(storage.contains("ValueIOSerializable"));
                // The frozen baseline ignores this read result. A migration
                // must not silently introduce a persistence behavior fix.
                assert!(storage.contains("input.getInt(\"value\");"));
                assert!(!storage.contains("this.value = input"));
                assert!(caps.contains("SFMBlockCapabilityKind<EnergyHandler>"));
                assert!(caps.contains("ResourceHandler<FluidResource>"));
                assert!(provider.contains("IBlockCapabilityProvider"));
            }
            _ => unreachable!("fixed targets"),
        }
        if !is_d2(target) {
            for flag in FLAGS {
                assert!(fixture.core.context(target, &[flag]).is_err());
            }
        }
    }
    Ok(())
}

#[test]
fn live_observations_and_counter_transfer_semantics_keep_distinct_members() -> Result<()> {
    let fixture = RedstoneFixture::load()?;
    for target in &TARGETS[..2] {
        let live = fixture.core.context(target, &[LIVE])?;
        let provider = fixture.body(PROVIDER, &live)?;
        for marker in [
            "instanceof BufferBlock",
            "pos.immutable()",
            "level.hasChunkAt(pos)",
            "level.getBlockState(pos)",
            "side.getOpposite()",
            "SFMDirections.DIRECTIONS_WITHOUT_NULL",
        ] {
            assert!(
                provider.contains(marker),
                "lost live signal boundary: {marker}"
            );
        }
        assert!(provider.contains("public int extract(int amount, boolean simulate)"));
        fixture.assert_expected(&live, target)?;
        let buffer = fixture.core.context(target, &[BUFFER])?;
        let storage = fixture.body(STORAGE, &buffer)?;
        for marker in [
            "!simulate && accept > 0",
            "!simulate && extract > 0",
            "this.value = Mth.clamp(nbt.getAsInt(), 0, this.maxValue);",
            "protected void onContentsChanged()",
        ] {
            assert!(storage.contains(marker), "lost counter boundary: {marker}");
        }
        fixture.assert_expected(&buffer, target)?;
        assert!(!fixture.body(PROVIDER, &buffer)?.contains("WorldSignal"));
        assert!(
            !fixture
                .body(STORAGE, &live)?
                .contains("onContentsChanged()")
        );
    }
    Ok(())
}

#[test]
fn descriptive_environment_and_nested_keys_do_not_select_redstone_source() -> Result<()> {
    let fixture = RedstoneFixture::load()?;
    for target in TARGETS {
        let flags = if is_d2(target) {
            &ALL_FLAGS[..]
        } else {
            &[][..]
        };
        for environment in ["release", "dev"] {
            for enabled in [flags, &[][..]] {
                let mut context = fixture.core.context(target, enabled)?;
                context.environment = environment.to_owned();
                context.projection_key =
                    format!("review/redstone-capabilities/{environment}/{target}");
                context.preset = context.projection_key.clone();
                fixture.assert_expected(&context, target)?;
            }
        }
    }
    Ok(())
}
