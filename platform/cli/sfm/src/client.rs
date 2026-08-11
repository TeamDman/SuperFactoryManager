use crate::discovery::{LiveInstance, uuid_like_request_id};
use crate::protocol::{
    CONTROL_PROTOCOL_VERSION, SfmControlClient, SfmControlInvokeClientActionRequest,
    SfmControlInvokeClientActionResult, SfmControlPingRequest,
};
use std::time::Duration;

const INVOCATION_TIMEOUT: Duration = Duration::from_secs(10);

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
