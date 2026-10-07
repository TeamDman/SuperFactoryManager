//! Text and membership proof for the three mixed private/remote adapters.
//! No prerequisite/default changes, Java compile closure or runtime proof.

#![cfg(test)]

use super::context::ProjectionContext;
use super::core_catalog::CoreCatalog;
use super::core_inputs::discover_core_source_files;
use super::core_inputs::select_core_inputs;
use super::core_slice_test_support::CoreTestFixture;
use super::oracle::ORACLES_PATH;
use super::oracle::parse_bindings;
use super::oracle::read_bounded;
use super::oracle_git::OracleGitRepository;
use super::render_java_source;
use eyre::Result;
use std::collections::BTreeSet;

const ADAPTERS: [(&str, &str, &str); 3] = [
    (
        "src/main/java/ca/teamdman/sfm/client/net/SFMClientPacketTransport.java",
        "packet_transport_private",
        "1438c847726a9f86f12c76c6a4f2a7ae53b7afd3",
    ),
    (
        "src/main/java/ca/teamdman/sfm/client/net/SFMClientInboxTransport.java",
        "client_inbox",
        "8867ee132ff7f2ebd3a3d4a3db470b6566aac201",
    ),
    (
        "src/main/java/ca/teamdman/sfm/common/net/SFMServerClientInboxTransport.java",
        "client_inbox",
        "6a90062cc2d48ab2b379d0993887f065d0cfb9a5",
    ),
];

fn explicit_context(
    fixture: &CoreTestFixture,
    target: &str,
    owners: &[&str],
) -> Result<ProjectionContext> {
    let mut pending = owners
        .iter()
        .map(|name| (*name).to_owned())
        .collect::<Vec<_>>();
    let mut enabled = BTreeSet::new();
    while let Some(name) = pending.pop() {
        if enabled.insert(name.clone()) {
            pending.extend(fixture.features.0[&name].requires.iter().cloned());
        }
    }
    let names = enabled.iter().map(String::as_str).collect::<Vec<_>>();
    fixture.context(target, &names)
}

#[test]
fn network_transport_guards_exact_old_two_witnesses_and_other_eighteen_omissions() -> Result<()> {
    let fixture = CoreTestFixture::load()?;
    let bindings = parse_bindings(&read_bounded(
        &fixture.repository,
        ORACLES_PATH,
        1024 * 1024,
    )?)?;
    let git = OracleGitRepository::open(&fixture.repository)?;
    let inventory = discover_core_source_files(&fixture.core)?;
    for binding in &bindings.bindings {
        let present = binding.environment == "dev"
            && ["1.19.2", "1.19.4"].contains(&binding.target_id.as_str());
        let context = if present {
            explicit_context(&fixture, &binding.target_id, &["multiplayer_packets"])?
        } else {
            fixture.historical_feature_off_catalog_context(&binding.projection)?
        };
        let selected = select_core_inputs(&fixture.metadata, &context, &inventory)?;
        let raw =
            git.files_at_commit_filtered(&binding.commit, &binding.source_prefix, |path| {
                ADAPTERS.iter().any(|(candidate, _, _)| *candidate == path)
            })?;
        for (path, _, oid) in ADAPTERS {
            assert_eq!(
                raw.contains_key(path),
                present,
                "{}: {path}",
                binding.projection
            );
            assert_eq!(
                selected.inputs.contains_key(path),
                present,
                "{}: {path}",
                binding.projection
            );
            if present {
                assert_eq!(raw[path].oid, oid);
                assert_eq!(raw[path].mode, 0o100644);
                let source = fixture.read_source(path)?;
                let rendered = render_java_source(std::str::from_utf8(&source)?, &context)?;
                assert_eq!(
                    rendered.as_bytes(),
                    raw[path].bytes,
                    "{}: {path}",
                    binding.projection
                );
            }
        }
    }
    Ok(())
}

#[test]
fn network_transport_guards_private_and_inbox_partials_drop_optional_owned_references() -> Result<()>
{
    let fixture = CoreTestFixture::load()?;
    let inventory = discover_core_source_files(&fixture.core)?;
    for target in ["1.19.2", "1.19.4"] {
        for (owner, consent) in [
            ("packet_transport_private", false),
            ("packet_transport_private", true),
            ("client_inbox", false),
            ("client_inbox", true),
        ] {
            let owners = if consent {
                vec![owner, "client_program_consent"]
            } else {
                vec![owner]
            };
            let context = explicit_context(&fixture, target, &owners)?;
            assert!(!context.features["multiplayer_packets"]);
            assert_eq!(context.features["client_program_consent"], consent);
            let selected = select_core_inputs(&fixture.metadata, &context, &inventory)?;
            for (path, required, _) in ADAPTERS {
                let present = context.features[required];
                assert_eq!(
                    selected.inputs.contains_key(path),
                    present,
                    "{target}: {path}"
                );
                if !present {
                    continue;
                }
                let source = fixture.read_source(path)?;
                let rendered = render_java_source(std::str::from_utf8(&source)?, &context)?;
                assert!(!rendered.contains("SFMMultiplayer"), "{target}: {path}");
                assert!(!rendered.contains("{%"), "{target}: {path}");
                if path.contains("/client/net/") {
                    assert_eq!(
                        rendered.contains("ClientProgramIdentity"),
                        consent,
                        "{target}: {path}"
                    );
                    assert_eq!(rendered.contains(" caller"), consent, "{target}: {path}");
                }
                if required == "packet_transport_private" {
                    assert!(rendered.contains("return false;"));
                    assert!(rendered.contains("SFMPackets.sendToServer(ServerboundPacketInsertionPacket.fromValue(target, value))"));
                } else if path.contains("/client/net/") {
                    assert!(rendered.contains("return Optional.empty();"));
                    assert!(rendered.contains("subscribe(address, SFMPackets::sendToServer)"));
                    assert!(rendered.contains("effectsAllowed(minecraft) ? server : null"));
                } else {
                    assert!(rendered.contains("return Result.EFFECTS_DISABLED;"));
                    assert!(rendered.contains(
                        "SFMPackets.sendToPlayer(recipient, new ClientboundClientInboxValuePacket"
                    ));
                }
            }
        }
    }
    Ok(())
}

#[test]
fn network_transport_guards_keep_exact_owners_and_current_catalog_selection() -> Result<()> {
    let fixture = CoreTestFixture::load()?;
    for (owner, requires) in [
        ("packet_transport_private", vec!["packet_computation"]),
        ("client_inbox", vec!["packet_transport_private"]),
        (
            "multiplayer_packets",
            vec![
                "client_inbox",
                "client_frame_language",
                "client_program_actions",
            ],
        ),
        ("client_program_consent", vec!["sfml_execution_side"]),
    ] {
        let definition = &fixture.features.0[owner];
        assert_eq!(definition.supported_targets, ["1.19.2", "1.19.4"]);
        assert_eq!(definition.requires, requires);
    }
    for (path, owner, _) in ADAPTERS {
        let variants = &fixture.metadata.source_rules[path];
        assert_eq!(variants.len(), 1);
        assert_eq!(variants[0].input, path);
        assert!(variants[0].template);
        assert_eq!(variants[0].when.targets, ["1.19.2", "1.19.4"]);
        assert_eq!(variants[0].when.all_features, [owner]);
        assert!(variants[0].when.any_features.is_empty());
        assert!(variants[0].when.none_features.is_empty());
    }
    let catalog = CoreCatalog::load(&fixture.repository, &fixture.repository)?;
    let inventory = discover_core_source_files(&fixture.core)?;
    for (key, entry) in &catalog.catalog.0 {
        let context = catalog.context(key)?;
        let target = entry.target_id()?;
        let selected = select_core_inputs(&fixture.metadata, &context, &inventory)?;
        for (path, owner, _) in ADAPTERS {
            let expected = ["1.19.2", "1.19.4"].contains(&target) && context.features[owner];
            assert_eq!(
                selected.inputs.contains_key(path),
                expected,
                "{key}: {path}"
            );
        }
    }
    Ok(())
}
