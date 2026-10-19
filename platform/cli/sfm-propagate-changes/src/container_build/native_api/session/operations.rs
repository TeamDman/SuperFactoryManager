use super::CancellationToken;
use super::Duration;
use super::GAME;
use super::SessionReady;
use super::SessionResponse;
use super::SessionTarget;
use super::controller_receipt;
use super::exec_create;
use super::exec_inspect;
use super::exec_start;
use super::file_archive;
use super::json;
use super::pause;
use super::policy;
use super::session_operation;
use super::url;
pub(super) async fn ready(target: &SessionTarget) -> eyre::Result<Option<SessionReady>> {
    let Some(bytes) = file_archive::fetch(
        &target.engine_endpoint,
        &target.session,
        &format!("{GAME}/managed-session/ready.json"),
        2 * 1024 * 1024,
    )
    .await?
    else {
        return Ok(None);
    };
    let ready: SessionReady = facet_json::from_slice(&bytes)?;
    eyre::ensure!(
        ready.schema == "sfm.managed-session/1"
            && ready.max_requests == 128
            && (1..=28800).contains(&ready.lifetime_seconds)
            && !ready.world.is_empty()
            && ready.world.len() <= 256,
        "unexpected managed puppet readiness contract"
    );
    Ok(Some(ready))
}
#[expect(
    clippy::too_many_lines,
    reason = "Fixed controller exec, receipt and response sequence is one bounded operation"
)]
pub(super) async fn perform(
    target: &SessionTarget,
    op: &str,
    game_test: Option<String>,
    angle: Option<f64>,
    cancellation: &CancellationToken,
) -> eyre::Result<SessionResponse> {
    let inspected = policy::owned(target).await?;
    eyre::ensure!(inspected.state.running, "owned session has exited");
    let bound = policy::bind(target, &inspected.id)?;
    let target = &bound;
    eyre::ensure!(
        ready(target).await?.is_some(),
        "managed puppet is not ready"
    );
    let operation = session_operation::SessionOperation {
        op: op.into(),
        game_test,
        angle,
    };
    let nonce = format!("{:016x}", rand::random::<u64>());
    let create = exec_create::ExecCreate {
        command: vec![
            "/usr/bin/python3".into(),
            "-c".into(),
            include_str!("controller.py").into(),
            facet_json::to_string(&operation)?,
            nonce.clone(),
        ],
        user: "10001:10001".into(),
        privileged: false,
        tty: false,
        stdin: false,
        stdout: false,
        stderr: false,
    };
    let path = url(&["containers", &target.session, "exec"])?;
    let created: super::super::api_created::ApiCreated = facet_json::from_slice(
        &json(&target.engine_endpoint, "POST", path.path(), &create).await?,
    )?;
    let start = exec_start::ExecStart {
        detach: true,
        tty: false,
    };
    let path = url(&["exec", &created.id, "start"])?;
    json(&target.engine_endpoint, "POST", path.path(), &start).await?;
    let deadline = std::time::Instant::now() + Duration::from_secs(15);
    loop {
        let path = url(&["exec", &created.id, "json"])?;
        let state: exec_inspect::ExecInspect = facet_json::from_slice(
            &super::super::transport::small(&target.engine_endpoint, "GET", path.path()).await?,
        )?;
        if !state.running {
            eyre::ensure!(
                matches!(state.exit_code, 0 | 1),
                "controller exited unexpectedly: {}",
                state.exit_code
            );
            break;
        }
        eyre::ensure!(
            std::time::Instant::now() < deadline,
            "controller publication exceeded15s; inspect status before retry"
        );
        pause(cancellation).await?;
    }
    let bytes = file_archive::fetch(
        &target.engine_endpoint,
        &target.session,
        &format!("/work/output/controller-{nonce}.json"),
        65536,
    )
    .await?
    .ok_or_else(|| eyre::eyre!("controller receipt missing; inspect status before retry"))?;
    let receipt: controller_receipt::ControllerReceipt = facet_json::from_slice(&bytes)?;
    let sequence = receipt.sequence.ok_or_else(|| {
        eyre::eyre!(
            "controller refused operation: {}",
            receipt.error.as_deref().unwrap_or("no sequence")
        )
    })?;
    eyre::ensure!(
        (1..=128).contains(&sequence) && receipt.error.is_none(),
        "invalid controller receipt"
    );
    let path = format!("{GAME}/managed-session/{sequence:06}.response.json");
    let deadline = std::time::Instant::now() + Duration::from_mins(2);
    loop {
        if let Some(bytes) = file_archive::fetch(
            &target.engine_endpoint,
            &target.session,
            &path,
            2 * 1024 * 1024,
        )
        .await?
        {
            let response: SessionResponse = facet_json::from_slice(&bytes)?;
            eyre::ensure!(
                response.schema == "sfm.managed-session-response/1"
                    && response.sequence == sequence
                    && response.op == op
                    && response.status == "completed"
                    && response.error.is_none(),
                "puppet operation failed or response mismatch: sequence{sequence}, op{op}, status{}, error{:?}",
                response.status,
                response.error
            );
            return Ok(response);
        }
        eyre::ensure!(
            std::time::Instant::now() < deadline,
            "puppet response exceeded120s; request remains recorded, inspect status before retry"
        );
        pause(cancellation).await?;
    }
}
