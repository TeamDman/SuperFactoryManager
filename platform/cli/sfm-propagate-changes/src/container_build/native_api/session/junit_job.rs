use super::CancellationToken;
use super::SessionTarget;
use super::empty;
use super::file_archive;
use super::json;
use super::policy;
use super::url;
use super::write_new;
use crate::container_build::ContainerTestReceipt;
use crate::container_build::ContainerTestRequest;
use crate::container_build::ContextManifest;
use std::collections::BTreeMap;
const OWNER: &str = "sfm-local-junit-v1";
fn entrypoint(seconds: u64) -> Vec<String> {
    [
        "/usr/bin/setpriv",
        "--no-new-privs",
        "/usr/bin/timeout",
        &format!("{seconds}s"),
        "/usr/bin/python3",
        "/opt/sfm/junit_job.py",
    ]
    .map(str::to_owned)
    .to_vec()
}
pub(crate) fn execute(
    request: &ContainerTestRequest,
    token: &CancellationToken,
) -> eyre::Result<ContainerTestReceipt> {
    request.validate()?;
    let parent = request
        .output
        .parent()
        .ok_or_else(|| eyre::eyre!("test output needs a parent"))?;
    crate::source_projection::candidate_lock::checked_directory(parent)?;
    crate::container_build::disk::check(parent, 8 * 1024 * 1024 * 1024)?;
    let root = crate::source_projection::candidate_lock::checked_directory(&request.context)?;
    let raw = crate::source_projection::core_catalog::read_bounded_input(
        &root,
        "context-manifest.json",
        32 * 1024 * 1024,
    )?;
    let manifest: ContextManifest = facet_json::from_slice(&raw)?;
    crate::container_build::backend::verify_context(&root, &manifest)?;
    eyre::ensure!(
        manifest.projection == request.projection,
        "test projection/context mismatch"
    );
    let context = manifest
        .context_identity
        .ok_or_else(|| eyre::eyre!("test context binding missing"))?;
    let runtime = tokio::runtime::Builder::new_current_thread()
        .enable_all()
        .build()?;
    runtime.block_on(run(request, &context, token))
}
async fn run(
    request: &ContainerTestRequest,
    context: &str,
    token: &CancellationToken,
) -> eyre::Result<ContainerTestReceipt> {
    token.bail_if_cancelled()?;
    let engine = policy::engine(&request.engine_endpoint, true).await?;
    let path = url(&["images", &request.image, "json"])?;
    let image: super::super::api_image::ApiImage = facet_json::from_slice(
        &super::super::transport::small(&request.engine_endpoint, "GET", path.path()).await?,
    )?;
    eyre::ensure!(
        image.id == request.image
            && image.os == "linux"
            && image.architecture == "amd64"
            && image.config.user.as_deref() == Some("10001:10001"),
        "test image platform/identity/user mismatch"
    );
    let labels = image
        .config
        .labels
        .as_ref()
        .ok_or_else(|| eyre::eyre!("test image labels missing"))?;
    eyre::ensure!(
        labels
            .get("org.sfm.container.context")
            .is_some_and(|c| c == context)
            && labels
                .get("org.sfm.container.profile")
                .is_some_and(|p| ["developer", "tests"].contains(&p.as_str())),
        "test image requires matching verified SDK profile/context"
    );
    super::memory_probe::check(&request.engine_endpoint, &image, &engine, token).await?;
    std::fs::create_dir(&request.output)?;
    let target = SessionTarget {
        engine_endpoint: request.engine_endpoint.clone(),
        session: format!("sfm-session-junit-{:016x}", rand::random::<u64>()),
    };
    let spec = super::sandbox_create::create(
        &target.session,
        &image.id,
        entrypoint(request.timeout_seconds),
        vec![request.projection.clone()],
        BTreeMap::from([
            ("org.sfm.junit.owner".into(), OWNER.into()),
            ("org.sfm.junit.image".into(), image.id.clone()),
            ("org.sfm.container.context".into(), context.into()),
        ]),
        super::sandbox_role::SandboxRole::Session,
    );
    let created: super::super::api_created::ApiCreated = facet_json::from_slice(
        &json(
            &target.engine_endpoint,
            "POST",
            &format!("{}/containers/create", super::API),
            &spec,
        )
        .await?,
    )?;
    let bound = policy::bind(&target, &created.id)?;
    let mut receipt = ContainerTestReceipt {
        schema: "sfm:container_test@1".into(),
        image: image.id.clone(),
        context_identity: context.into(),
        projection: request.projection.clone(),
        worker: created.id,
        exit_code: None,
        seconds: 0.0,
        summary: None,
        accepted: false,
        removed: false,
        error: None,
        artifacts: BTreeMap::new(),
    };
    let started = std::time::Instant::now();
    let result = wait(request, &bound, context, token).await;
    if let Err(error) = result {
        receipt.error = Some(format!("{error:#}"));
    }
    let cleanup = finish(request, &bound, context, &mut receipt).await;
    receipt.seconds = started.elapsed().as_secs_f64();
    if let Err(error) = cleanup {
        receipt.error = Some(format!(
            "{}; finalization error (worker removed={}): {error:#}",
            receipt.error.as_deref().unwrap_or("job/export failed"),
            receipt.removed
        ));
    }
    receipt.accepted = receipt.removed
        && receipt.error.is_none()
        && receipt.exit_code == Some(0)
        && receipt.summary.as_ref().is_some_and(accounted);
    write_new(
        &request.output.join("receipt.json"),
        facet_json::to_string_pretty(&receipt)?.as_bytes(),
    )?;
    Ok(receipt)
}
async fn inspect(
    request: &ContainerTestRequest,
    target: &SessionTarget,
    context: &str,
) -> eyre::Result<super::session_inspect::SessionInspect> {
    let path = url(&["containers", &target.session, "json"])?;
    let value: super::session_inspect::SessionInspect = facet_json::from_slice(
        &super::super::transport::small(&target.engine_endpoint, "GET", path.path()).await?,
    )?;
    eyre::ensure!(
        value.id == target.session
            && value.image == request.image
            && value
                .config
                .labels
                .get("org.sfm.junit.owner")
                .is_some_and(|o| o == OWNER)
            && value.config.labels.get("org.sfm.junit.image") == Some(&request.image)
            && value
                .config
                .labels
                .get("org.sfm.container.context")
                .is_some_and(|c| c == context)
            && value.config.command == [request.projection.clone()]
            && value.config.entrypoint == entrypoint(request.timeout_seconds)
            && value.config.user == "10001:10001",
        "JUnit immutable worker ownership/command/image mismatch; no action taken"
    );
    let phase = if value.state.status == "created" {
        eyre::ensure!(
            !value.state.running && !value.state.oom_killed,
            "JUnit created state changed"
        );
        super::session_inspection_phase::SessionInspectionPhase::Created
    } else {
        super::session_inspection_phase::SessionInspectionPhase::Runtime
    };
    policy::validate_sandbox(&value, phase, super::sandbox_role::SandboxRole::Session)?;
    Ok(value)
}
async fn wait(
    request: &ContainerTestRequest,
    target: &SessionTarget,
    context: &str,
    token: &CancellationToken,
) -> eyre::Result<()> {
    let before = inspect(request, target, context).await?;
    eyre::ensure!(
        before.state.status == "created",
        "JUnit job was already started"
    );
    empty(
        &target.engine_endpoint,
        "POST",
        url(&["containers", &target.session, "init"])?.path(),
    )
    .await?;
    let initialized = inspect(request, target, context).await?;
    eyre::ensure!(
        initialized.state.status == "initialized"
            && !initialized.state.running
            && initialized.host.mappings.as_ref().map(|m| (&m.uid, &m.gid))
                == before.host.mappings.as_ref().map(|m| (&m.uid, &m.gid)),
        "JUnit namespace initialization changed mappings/state"
    );
    empty(
        &target.engine_endpoint,
        "POST",
        url(&["containers", &target.session, "start"])?.path(),
    )
    .await?;
    let deadline =
        std::time::Instant::now() + std::time::Duration::from_secs(request.timeout_seconds + 15);
    loop {
        token.bail_if_cancelled()?;
        let current = inspect(request, target, context).await?;
        eyre::ensure!(
            !current.state.oom_killed,
            "JUnit job exceeded its memory cap"
        );
        if !current.state.running {
            return Ok(());
        }
        eyre::ensure!(std::time::Instant::now() < deadline, "JUnit job timed out");
        crate::container_build::disk::check(&request.output, 8 * 1024 * 1024 * 1024)?;
        policy::engine(&target.engine_endpoint, false)
            .await?
            .store
            .check()?;
        tokio::time::sleep(std::time::Duration::from_secs(5)).await;
    }
}
async fn finish(
    request: &ContainerTestRequest,
    target: &SessionTarget,
    context: &str,
    receipt: &mut ContainerTestReceipt,
) -> eyre::Result<()> {
    let state = inspect(request, target, context).await?;
    if state.state.running {
        empty(
            &target.engine_endpoint,
            "POST",
            &format!(
                "{}?timeout=20",
                url(&["containers", &target.session, "stop"])?.path()
            ),
        )
        .await?;
    }
    let state = inspect(request, target, context).await?;
    eyre::ensure!(!state.state.running, "JUnit owned stop unconfirmed");
    receipt.exit_code = Some(state.state.exit_code);
    let mut export_error = None;
    for (name, budget) in [
        ("job-security.json", 65536),
        ("input-verification.json", 65536),
        ("junit-run.json", 65536),
        ("junit-run.log", 16 * 1024 * 1024),
        ("junit-events.log", 16 * 1024 * 1024),
        ("junit-compile.json", 65536),
        ("junit-compile.log", 4 * 1024 * 1024),
    ] {
        match file_archive::fetch(
            &target.engine_endpoint,
            &target.session,
            &format!("/work/test-receipts/{name}"),
            budget,
        )
        .await
        {
            Ok(Some(bytes)) => {
                if name == "junit-events.log" {
                    match std::str::from_utf8(&bytes)
                        .map_err(eyre::Report::from)
                        .and_then(crate::jar_build::junit_summary_from_log)
                    {
                        Ok(s) => receipt.summary = Some(s),
                        Err(e) => export_error = Some(e),
                    }
                }
                if let Err(e) = write_new(&request.output.join(name), &bytes) {
                    export_error = Some(e);
                } else {
                    receipt.artifacts.insert(
                        name.into(),
                        crate::source_projection::provenance::sha256(&bytes),
                    );
                }
            }
            Ok(None) => {}
            Err(e) => export_error = Some(e),
        }
    }
    empty(
        &target.engine_endpoint,
        "DELETE",
        &format!(
            "{}?force=false&v=false",
            url(&["containers", &target.session])?.path()
        ),
    )
    .await?;
    receipt.removed = true;
    if let Some(e) = export_error {
        return Err(e);
    }
    eyre::ensure!(!state.state.oom_killed, "JUnit OOM failure");
    eyre::ensure!(
        receipt.artifacts.contains_key("input-verification.json")
            && receipt.artifacts.contains_key("job-security.json"),
        "JUnit security/source verification evidence missing"
    );
    eyre::ensure!(receipt.summary.is_some(), "JUnit summary missing");
    Ok(())
}
fn accounted(s: &crate::jar_build::JunitSummary) -> bool {
    s.tests_found > 0
        && s.containers_found > 0
        && s.containers_failed == 0
        && s.tests_failed == 0
        && s.tests_started.checked_add(s.tests_skipped) == Some(s.tests_found)
        && s.tests_succeeded
            .checked_add(s.tests_failed)
            .and_then(|n| n.checked_add(s.tests_aborted))
            == Some(s.tests_started)
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn failed_containers_and_unstarted_tests_are_not_accepted() {
        let mut s = crate::jar_build::JunitSummary {
            tests_found: 5,
            tests_started: 5,
            tests_succeeded: 5,
            containers_found: 3,
            ..Default::default()
        };
        assert!(accounted(&s));
        s.containers_failed = 1;
        assert!(!accounted(&s));
        s.containers_failed = 0;
        s.tests_started = 0;
        s.tests_succeeded = 0;
        assert!(!accounted(&s));
        s.tests_started = 2;
        s.tests_succeeded = 1;
        s.tests_aborted = 1;
        s.tests_skipped = 3;
        assert!(accounted(&s));
        s.containers_failed = 1;
        assert!(!accounted(&s));
    }
}
