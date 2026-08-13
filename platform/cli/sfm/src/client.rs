use crate::discovery::{LiveInstance, uuid_like_request_id};
use crate::protocol::{
    CONTROL_PROTOCOL_VERSION, EXPLORER_CONTROL_CAPABILITY, SfmControlClient,
    SfmControlExplorerIfNoMatch, SfmControlExplorerOperation, SfmControlExplorerOperationRequest,
    SfmControlExplorerOperationResult, SfmControlInvokeClientActionRequest,
    SfmControlInvokeClientActionResult, SfmControlPingRequest, validate_explorer_operation_request,
    validate_explorer_operation_result,
};
use std::time::Duration;

const INVOCATION_TIMEOUT: Duration = Duration::from_secs(10);

#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct ExplorerOperationInput {
    pub operation: SfmControlExplorerOperation,
    pub explorer_selector: String,
    pub canonical_path: String,
    pub setting_value: String,
    pub if_no_match: SfmControlExplorerIfNoMatch,
}

pub(crate) async fn invoke_client_action(
    instance: &LiveInstance,
    action_tokens: Vec<String>,
) -> eyre::Result<SfmControlInvokeClientActionResult> {
    eyre::ensure!(
        instance
            .description
            .capabilities
            .iter()
            .any(|capability| capability == "client-action.invoke"),
        "selected instance does not advertise client-action.invoke"
    );
    let descriptor = &instance.descriptor;
    let endpoint = format!("{}:{}", descriptor.host, descriptor.port);
    tokio::time::timeout(INVOCATION_TIMEOUT, async {
        let connection = vox::connect(&endpoint)
            .connect_timeout(Duration::from_secs(2))
            .establish()
            .await
            .map_err(|failure| eyre::eyre!("connecting to {endpoint} failed: {failure:?}"))?;
        let client: SfmControlClient = connection
            .open_lane()
            .await
            .map_err(|failure| eyre::eyre!("opening SFM control lane failed: {failure:?}"))?;

        let ping_request_id = uuid_like_request_id();
        let ping = client
            .ping(SfmControlPingRequest {
                authentication_token: descriptor.authentication_token.clone(),
                request_id: ping_request_id.clone(),
                protocol_version: CONTROL_PROTOCOL_VERSION,
            })
            .await
            .map_err(|failure| eyre::eyre!("pre-invocation identity ping failed: {failure:?}"))?;
        eyre::ensure!(
            ping.request_id == ping_request_id,
            "ping request id mismatch"
        );
        eyre::ensure!(
            ping.instance_id == descriptor.instance_id,
            "instance id changed"
        );
        eyre::ensure!(
            ping.process_id == descriptor.process_id,
            "process id changed"
        );
        eyre::ensure!(
            ping.process_start_nonce == descriptor.process_start_nonce,
            "process start nonce changed"
        );

        let request_id = uuid_like_request_id();
        let result = client
            .invoke_client_action(SfmControlInvokeClientActionRequest {
                authentication_token: descriptor.authentication_token.clone(),
                request_id: request_id.clone(),
                protocol_version: CONTROL_PROTOCOL_VERSION,
                action_tokens,
            })
            .await
            .map_err(|failure| eyre::eyre!("SFM client action failed: {failure:?}"))?;
        eyre::ensure!(
            result.request_id == request_id,
            "action request id mismatch"
        );
        eyre::ensure!(
            result.instance_id == descriptor.instance_id,
            "action instance id mismatch"
        );
        eyre::ensure!(
            result.process_id == descriptor.process_id,
            "action process id mismatch"
        );
        let _ = connection.shutdown();
        Ok(result)
    })
    .await
    .map_err(|_| {
        eyre::eyre!(
            "SFM client action timed out after {} ms",
            INVOCATION_TIMEOUT.as_millis()
        )
    })?
}

pub(crate) async fn execute_explorer_operation(
    instance: &LiveInstance,
    input: ExplorerOperationInput,
) -> eyre::Result<SfmControlExplorerOperationResult> {
    eyre::ensure!(
        instance
            .description
            .capabilities
            .iter()
            .any(|capability| capability == EXPLORER_CONTROL_CAPABILITY),
        "selected instance does not advertise {EXPLORER_CONTROL_CAPABILITY}"
    );
    let descriptor = &instance.descriptor;
    let endpoint = format!("{}:{}", descriptor.host, descriptor.port);
    tokio::time::timeout(INVOCATION_TIMEOUT, async {
        let connection = vox::connect(&endpoint)
            .connect_timeout(Duration::from_secs(2))
            .establish()
            .await
            .map_err(|failure| eyre::eyre!("connecting to {endpoint} failed: {failure:?}"))?;
        let client: SfmControlClient = connection
            .open_lane()
            .await
            .map_err(|failure| eyre::eyre!("opening SFM control lane failed: {failure:?}"))?;

        let ping_request_id = uuid_like_request_id();
        let ping = client
            .ping(SfmControlPingRequest {
                authentication_token: descriptor.authentication_token.clone(),
                request_id: ping_request_id.clone(),
                protocol_version: CONTROL_PROTOCOL_VERSION,
            })
            .await
            .map_err(|failure| eyre::eyre!("pre-operation identity ping failed: {failure:?}"))?;
        eyre::ensure!(
            ping.request_id == ping_request_id,
            "ping request id mismatch"
        );
        eyre::ensure!(
            ping.instance_id == descriptor.instance_id,
            "instance id changed"
        );
        eyre::ensure!(
            ping.process_id == descriptor.process_id,
            "process id changed"
        );
        eyre::ensure!(
            ping.process_start_nonce == descriptor.process_start_nonce,
            "process start nonce changed"
        );

        let request = SfmControlExplorerOperationRequest {
            authentication_token: descriptor.authentication_token.clone(),
            request_id: uuid_like_request_id(),
            protocol_version: CONTROL_PROTOCOL_VERSION,
            operation: input.operation,
            explorer_selector: input.explorer_selector,
            canonical_path: input.canonical_path,
            setting_value: input.setting_value,
            if_no_match: input.if_no_match,
        };
        validate_explorer_operation_request(&request)?;
        let result = client
            .explorer_operation(request.clone())
            .await
            .map_err(|failure| eyre::eyre!("SFM explorer operation failed: {failure:?}"))?;
        validate_explorer_operation_result(
            &result,
            &request,
            &descriptor.instance_id,
            descriptor.process_id,
        )?;
        let _ = connection.shutdown();
        Ok(result)
    })
    .await
    .map_err(|_| {
        eyre::eyre!(
            "SFM explorer operation timed out after {} ms",
            INVOCATION_TIMEOUT.as_millis()
        )
    })?
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::protocol::{
        SfmControl, SfmControlDescribeRequest, SfmControlDescribeResult, SfmControlDispatcher,
        SfmControlError, SfmControlExplorerOperationStatus, SfmControlExplorerTargetOutcome,
        SfmControlExplorerTargetResult, SfmControlLifecycle, SfmControlPingResult,
        SfmGameInstanceDescriptor,
    };
    use std::sync::{Arc, Mutex};

    #[derive(Clone)]
    struct FakeControl {
        captured: Arc<Mutex<Option<SfmControlExplorerOperationRequest>>>,
    }

    impl SfmControl for FakeControl {
        async fn ping(
            &self,
            request: SfmControlPingRequest,
        ) -> Result<SfmControlPingResult, SfmControlError> {
            Ok(SfmControlPingResult {
                instance_id: "instance-test".to_owned(),
                process_id: 42,
                process_start_nonce: "process-start-test".to_owned(),
                protocol_version: CONTROL_PROTOCOL_VERSION,
                request_id: request.request_id,
            })
        }

        async fn describe(
            &self,
            request: SfmControlDescribeRequest,
        ) -> Result<SfmControlDescribeResult, SfmControlError> {
            Ok(fake_description(request.request_id))
        }

        async fn invoke_client_action(
            &self,
            request: SfmControlInvokeClientActionRequest,
        ) -> Result<SfmControlInvokeClientActionResult, SfmControlError> {
            Ok(SfmControlInvokeClientActionResult {
                instance_id: "instance-test".to_owned(),
                process_id: 42,
                request_id: request.request_id,
                canonical_action: request.action_tokens.join(" "),
                result_code: 0,
                feedback: Vec::new(),
                resulting_screen_present: false,
                resulting_screen: String::new(),
                workspace_present: false,
                workspace_panel_count: 0,
            })
        }

        async fn explorer_operation(
            &self,
            request: SfmControlExplorerOperationRequest,
        ) -> Result<SfmControlExplorerOperationResult, SfmControlError> {
            *self.captured.lock().expect("capture lock") = Some(request.clone());
            Ok(SfmControlExplorerOperationResult {
                instance_id: "instance-test".to_owned(),
                process_id: 42,
                request_id: request.request_id,
                operation: request.operation,
                explorer_selector: request.explorer_selector,
                canonical_path: request.canonical_path.clone(),
                setting_value: request.setting_value,
                if_no_match: request.if_no_match,
                status: SfmControlExplorerOperationStatus::Applied,
                captured_target_count: 1,
                matched_target_count: 0,
                changed_target_count: 1,
                opened_target_count: 1,
                selection_revision_present: true,
                selection_revision: 17,
                child_relation_revision_present: true,
                child_relation_revision: 19,
                targets: vec![SfmControlExplorerTargetResult {
                    explorer_id: "explorer-created".to_owned(),
                    outcome: SfmControlExplorerTargetOutcome::Created,
                    message: "created by fake game".to_owned(),
                    explorer_revision: 23,
                    selection_revision_present: true,
                    selection_revision: 17,
                    child_relation_revision_present: true,
                    child_relation_revision: 19,
                    child_relation_request_present: false,
                    child_relation_request_id: 0,
                    focused: true,
                    canonical_location_expression: request.canonical_path.clone(),
                    canonical_roots: vec![request.canonical_path.clone()],
                    view_id: "sfm:list".to_owned(),
                    sort_id: "sfm:name".to_owned(),
                    group_id: "sfm:hierarchy".to_owned(),
                    root_hoist_mode: "auto".to_owned(),
                    visible_paths: vec![request.canonical_path],
                    visible_row_count: 1,
                    pending_request_count: 0,
                    diagnostic_count: 0,
                }],
                feedback: vec!["root accepted".to_owned()],
                resulting_screen_present: true,
                resulting_screen: "SFMWorkspaceScreen".to_owned(),
                workspace_present: true,
                workspace_panel_count: 1,
            })
        }
    }

    fn fake_description(request_id: String) -> SfmControlDescribeResult {
        SfmControlDescribeResult {
            instance_id: "instance-test".to_owned(),
            process_id: 42,
            process_start_nonce: "process-start-test".to_owned(),
            protocol_version: CONTROL_PROTOCOL_VERSION,
            sfm_version: "test".to_owned(),
            minecraft_version: "1.19.2".to_owned(),
            lifecycle: SfmControlLifecycle::Ready,
            focused: true,
            focus_seen: true,
            last_focus_epoch_millis: 1,
            current_screen_present: true,
            current_screen: "SFMWorkspaceScreen".to_owned(),
            world_present: false,
            world_label: String::new(),
            capabilities: vec![EXPLORER_CONTROL_CAPABILITY.to_owned()],
            request_id,
        }
    }

    #[tokio::test]
    async fn fake_vox_round_trip_preserves_canonical_explorer_contract() {
        let listener = tokio::net::TcpListener::bind("127.0.0.1:0")
            .await
            .expect("bind fake game");
        let address = listener.local_addr().expect("fake game address");
        let captured = Arc::new(Mutex::new(None));
        let server = {
            let captured = Arc::clone(&captured);
            tokio::spawn(async move {
                vox::serve_listener(
                    listener,
                    SfmControlDispatcher::new(FakeControl { captured }),
                )
                .await
                .expect("serve fake game");
            })
        };
        let live = LiveInstance {
            descriptor: SfmGameInstanceDescriptor {
                schema: crate::protocol::INSTANCE_DESCRIPTOR_SCHEMA.to_owned(),
                protocol_version: CONTROL_PROTOCOL_VERSION,
                instance_id: "instance-test".to_owned(),
                process_id: 42,
                process_start_nonce: "process-start-test".to_owned(),
                transport: "tcp".to_owned(),
                host: "127.0.0.1".to_owned(),
                port: address.port(),
                authentication_token: "secret".to_owned(),
                created_at_epoch_millis: 1,
            },
            description: fake_description("discovery-request".to_owned()),
        };

        let result = execute_explorer_operation(
            &live,
            ExplorerOperationInput {
                operation: SfmControlExplorerOperation::RootAdd,
                explorer_selector: "focused".to_owned(),
                canonical_path: "registry://minecraft/item/".to_owned(),
                setting_value: String::new(),
                if_no_match: SfmControlExplorerIfNoMatch::OpenNew,
            },
        )
        .await
        .expect("typed fake round trip");

        assert_eq!(
            result.request_id,
            captured
                .lock()
                .expect("capture lock")
                .as_ref()
                .expect("captured request")
                .request_id
        );
        assert_eq!(result.explorer_selector, "focused");
        assert_eq!(result.canonical_path, "registry://minecraft/item/");
        assert_eq!(result.targets[0].selection_revision, 17);
        assert_eq!(result.targets[0].child_relation_revision, 19);
        assert_eq!(result.targets[0].explorer_revision, 23);

        let node_result = execute_explorer_operation(
            &live,
            ExplorerOperationInput {
                operation: SfmControlExplorerOperation::NodeRefresh,
                explorer_selector: "id(explorer-created)".to_owned(),
                canonical_path: "registry://minecraft/item/".to_owned(),
                setting_value: String::new(),
                if_no_match: SfmControlExplorerIfNoMatch::Fail,
            },
        )
        .await
        .expect("typed node round trip");
        assert_eq!(
            node_result.operation,
            SfmControlExplorerOperation::NodeRefresh
        );
        assert_eq!(node_result.explorer_selector, "id(explorer-created)");
        assert_eq!(node_result.canonical_path, "registry://minecraft/item/");
        let captured_node = captured
            .lock()
            .expect("capture lock")
            .clone()
            .expect("captured node request");
        assert_eq!(
            captured_node.operation,
            SfmControlExplorerOperation::NodeRefresh
        );
        assert_eq!(captured_node.canonical_path, "registry://minecraft/item/");
        assert!(captured_node.setting_value.is_empty());
        assert_eq!(captured_node.if_no_match, SfmControlExplorerIfNoMatch::Fail);
        server.abort();
    }
}
