//! Test-only exact current registrar successor. This does not mint historical
//! witnesses or change selection/defaults. Both caller suites validate current
//! bytes, invert reviewed action-provider refinements and two workspace guards,
//! then check both original predecessor pins. Production renders current bytes.
#![cfg(test)]
use super::core_slice_test_support::CoreTestFixture;
use super::provenance::sha256;
use eyre::Result;
use eyre::ensure;
pub(super) const CLIENT_REGISTRAR_PRE_WORKSPACE: (u64, &str) = (
    11404,
    "sha256:54cf007cb986311b9a831aaee9ad91612202b36cf23a1b8ac9a7a5824976bdfb",
);
const CLIENT_REGISTRAR_PRE_ACTION_REFINEMENTS: (u64, &str) = (
    11490,
    "sha256:80686295d7ea7bb87bed0d2dd9a4d2c60e33a7ce6731a222b97233091ef7c6e1",
);
pub(super) const CLIENT_REGISTRAR_CURRENT: (u64, &str) = (
    13147,
    "sha256:7f6df2e21a994985badab6fae4ee737341284fec34b620b273f18188ba3d35c5",
);
fn exact(bytes: &[u8], pin: (u64, &str)) -> Result<()> {
    ensure!(
        bytes.len() as u64 == pin.0
            && sha256(bytes) == pin.1
            && bytes.ends_with(b"\n")
            && !bytes.contains(&b'\r')
            && !bytes.starts_with(&[0xef, 0xbb, 0xbf]),
        "current registrar identity changed"
    );
    std::str::from_utf8(bytes)?;
    Ok(())
}
pub(super) fn reviewed_pre_workspace_registrar(bytes: &[u8]) -> Result<String> {
    exact(bytes, CLIENT_REGISTRAR_CURRENT)?;
    let mut predecessor = std::str::from_utf8(bytes)?.to_owned();
    // Exact inverse of the retained packet-action, action-registrar and
    // explorer-owner caller patches; never relax or replace historical pins.
    for statement in [
        "import ca.teamdman.sfm.client.action.SFMDeveloperActions;",
        "        SFMDeveloperActions.register(bus);",
    ] {
        let refined = format!(
            "{{% case minecraft_version %}}\n{{% when \"1.19.2\", \"1.19.4\" %}}\n\
             {{% if features.developer_world_actions %}}\n{statement}\n\
             {{% else %}}\n{{% if features.workspace_panel_actions %}}\n\
             {{% if features.input_diagnostics_panel %}}\n{statement}\n\
             {{% else %}}\n\
             {{% if features.editor_document_panels and features.workspace_panel_reopening %}}\n\
             {statement}\n{{% endif %}}\n{{% endif %}}\n{{% endif %}}\n{{% endif %}}\n\
             {{% else %}}\n{statement}\n{{% endcase %}}\n"
        );
        ensure!(
            predecessor.matches(&refined).count() == 1,
            "developer refinement drift"
        );
        predecessor = predecessor.replacen(&refined, &format!("{statement}\n"), 1);
    }
    for statement in [
        "import ca.teamdman.sfm.client.action.SFMPacketActions;",
        "        SFMPacketActions.register(bus);",
    ] {
        let refined = format!(
            "{{% if features.packet_transport_private or features.multiplayer_packets %}}\n\
             {statement}\n{{% endif %}}\n"
        );
        ensure!(
            predecessor.matches(&refined).count() == 1,
            "packet refinement drift"
        );
        predecessor = predecessor.replacen(&refined, &format!("{statement}\n"), 1);
    }
    for (refined, original) in [
        (
            "{% if features.document_history or features.editor_pointer_actions or features.editor_search %}\n",
            "{% if features.document_history %}\n",
        ),
        (
            "{% if features.file_explorer or features.registry_explorer or features.explorer_search or features.explorer_compaction or features.explorer_navigation or features.java_symbols or features.release_review or features.workspace_counterfactuals or features.theme_preview_rules or features.clipboard_action_commands %}\n",
            "{% if features.file_explorer or features.registry_explorer or features.explorer_search or features.explorer_compaction or features.icon_rules %}\n",
        ),
    ] {
        ensure!(
            predecessor.matches(refined).count() == 2,
            "action owner refinement drift"
        );
        predecessor = predecessor.replace(refined, original);
    }
    exact(
        predecessor.as_bytes(),
        CLIENT_REGISTRAR_PRE_ACTION_REFINEMENTS,
    )?;
    for (before, after) in [
        (
            "{% if features.workspace_lifecycle %}\nimport ca.teamdman.sfm.client.action.SFMWorkspaceLifecycleActions;\n",
            "{% if features.workspace_lifecycle or features.workspace_panel_entry_controls %}\nimport ca.teamdman.sfm.client.action.SFMWorkspaceLifecycleActions;\n",
        ),
        (
            "{% if features.workspace_lifecycle %}\n        SFMWorkspaceLifecycleActions.register(bus);\n",
            "{% if features.workspace_lifecycle or features.workspace_panel_entry_controls %}\n        SFMWorkspaceLifecycleActions.register(bus);\n",
        ),
    ] {
        ensure!(
            predecessor.matches(after).count() == 1,
            "reviewed guard absent or duplicated"
        );
        predecessor = predecessor.replacen(after, before, 1);
    }
    exact(predecessor.as_bytes(), CLIENT_REGISTRAR_PRE_WORKSPACE)?;
    Ok(predecessor)
}
#[test]
fn current_registrar_exact_inverse_and_outside_guard_mutations_fail_closed() -> Result<()> {
    let core = CoreTestFixture::load()?;
    let current =
        core.read_source("src/main/java/ca/teamdman/sfm/client/SFMClientRegistrations.java")?;
    let predecessor = reviewed_pre_workspace_registrar(&current)?;
    exact(predecessor.as_bytes(), CLIENT_REGISTRAR_PRE_WORKSPACE)?;
    // None of these bytes belongs to either reviewed guard.
    for index in [0, current.len() - 2] {
        let mut changed = current.clone();
        changed[index] ^= 1;
        assert!(reviewed_pre_workspace_registrar(&changed).is_err());
    }
    assert!(reviewed_pre_workspace_registrar(&current[..current.len() - 1]).is_err());
    assert!(reviewed_pre_workspace_registrar(predecessor.as_bytes()).is_err());
    for refined in [
        "features.packet_transport_private or features.multiplayer_packets",
        "features.document_history or features.editor_pointer_actions or features.editor_search",
        "features.editor_document_panels and features.workspace_panel_reopening",
        "features.explorer_navigation or features.java_symbols",
    ] {
        let changed =
            std::str::from_utf8(&current)?.replacen(refined, "features.client_actions", 1);
        assert!(reviewed_pre_workspace_registrar(changed.as_bytes()).is_err());
    }
    let crlf = std::str::from_utf8(&current)?.replace('\n', "\r\n");
    assert!(reviewed_pre_workspace_registrar(crlf.as_bytes()).is_err());
    Ok(())
}
