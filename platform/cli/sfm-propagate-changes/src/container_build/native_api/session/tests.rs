use super::*;
#[test]
fn archive_single_file_is_bounded_and_rejects_other_names_and_header_corruption() -> eyre::Result<()>
{
    let raw = include_bytes!("fixtures/resource-sample.tar");
    let data = file_archive::single_file(raw, "resource-sample.json", 65536)?;
    assert!(std::str::from_utf8(&data)?.contains("memory_events"));
    assert!(file_archive::single_file(raw, "secret", 65536).is_err());
    assert!(file_archive::single_file(raw, "resource-sample.json", 8).is_err());
    let mut broken = raw.to_vec();
    broken[0] ^= 1;
    assert!(file_archive::single_file(&broken, "resource-sample.json", 65536).is_err());
    Ok(())
}
#[test]
fn png_validation_checks_crc_and_truncation() -> eyre::Result<()> {
    let raw = include_bytes!("fixtures/pixel.png");
    png::verify(raw)?;
    let mut corrupt = raw.to_vec();
    corrupt[20] ^= 1;
    assert!(png::verify(&corrupt).is_err());
    assert!(png::verify(&raw[..raw.len() - 1]).is_err());
    Ok(())
}
#[test]
fn optional_operation_fields_do_not_override_java_defaults() -> eyre::Result<()> {
    let request = session_operation::SessionOperation {
        op: "reset".into(),
        game_test: None,
        angle: None,
    };
    assert_eq!(facet_json::to_string(&request)?, r#"{"op":"reset"}"#);
    Ok(())
}
#[test]
fn unsafe_session_names_test_ids_and_capture_heap_are_refused_before_engine_access()
-> eyre::Result<()> {
    let target = SessionTarget {
        engine_endpoint: std::path::PathBuf::from("irrelevant"),
        session: "unowned".into(),
    };
    assert!(target.validate().is_err());
    assert!(crate::container_build::session_request::bare_test("sfm:../escape").is_err());
    let start = crate::container_build::SessionStartRequest {
        target: SessionTarget {
            engine_endpoint: std::path::PathBuf::from("irrelevant"),
            session: "sfm-session-test".into(),
        },
        image: "local".into(),
        projection: "sfm-dev/mc-1.19.2".into(),
        game_test: "sfm:move_1_stack_direct".into(),
        java_max_heap_mib: 8193,
        session_seconds: 900,
        startup_seconds: 720,
    };
    assert!(start.command().is_err());
    Ok(())
}

#[test]
fn immutable_container_binding_survives_name_replacement_for_all_routes() -> eyre::Result<()> {
    let name = SessionTarget {
        engine_endpoint: "fixture".into(),
        session: "sfm-session-reused".into(),
    };
    let id = "a".repeat(64);
    let bound = policy::bind(&name, &id)?;
    let replacement_id = "b".repeat(64);
    let mut names = std::collections::BTreeMap::from([(name.session.clone(), id.clone())]);
    names.insert(name.session.clone(), replacement_id.clone());
    for action in ["init", "start", "stop", "exec", "archive", "json"] {
        let path = url(&["containers", &bound.session, action])?;
        assert!(path.path().contains(&format!("/containers/{id}/")));
        assert!(!path.path().contains(&replacement_id));
    }
    assert!(url(&["containers", &bound.session])?.path().ends_with(&id));
    assert!(policy::bind(&name, &name.session).is_err());
    Ok(())
}

#[cfg(windows)]
#[test]
fn engine_stop_uses_admitted_id_when_name_is_replaced() -> eyre::Result<()> {
    use tokio::io::AsyncReadExt as _;
    use tokio::io::AsyncWriteExt as _;
    let runtime = tokio::runtime::Builder::new_current_thread()
        .enable_all()
        .build()?;
    runtime.block_on(async {
        let pipe = format!(r"\\.\pipe\sfm-owned-stop-unit-{}-{}", std::process::id(), rand::random::<u64>());
        let fixture = include_str!("fixtures/admitted-worker.json");
        let inspected: session_inspect::SessionInspect = facet_json::from_str(fixture)?;
        policy::validate(&inspected)?;
        let target = SessionTarget { engine_endpoint: pipe.clone().into(), session: "sfm-session-reused".into() };
        let receipt = receipt(&target, &inspected, None);
        let mut server = tokio::net::windows::named_pipe::ServerOptions::new().first_pipe_instance(true).create(&pipe)?;
        let serving = tokio::spawn(async move {
            let id = "a".repeat(64);
            let mut paths = Vec::new();
            for index in 0..5 {
                server.connect().await?;
                let mut request = Vec::new();
                loop {
                    let mut byte = [0];
                    server.read_exact(&mut byte).await?;
                    request.push(byte[0]);
                    eyre::ensure!(request.len() < 8192, "fixture request budget");
                    if request.ends_with(b"\r\n\r\n") { break; }
                }
                let request = std::str::from_utf8(&request)?;
                let path = request.lines().next().ok_or_else(|| eyre::eyre!("missing fixture request"))?.split_whitespace().nth(1).ok_or_else(|| eyre::eyre!("missing fixture path"))?.to_owned();
                paths.push(path.clone());
                // The session name now denotes a foreign replacement, after admission.
                // Any action or inspection by that mutable name fails this fixture.
                eyre::ensure!(!path.contains("sfm-session-reused"), "mutable name redirected action");
                let body = if path.contains("/images/") { format!(r#"{{"Id":"{}","Digest":"sha256:{}","ManifestType":"application/vnd.oci.image.manifest.v1+json","Os":"linux","Architecture":"amd64","Config":{{"Labels":{{"org.sfm.container.context":"sha256:{}"}}}}}}"#, "c".repeat(64), "d".repeat(64), "d".repeat(64)) } else if path.contains("/stop?") { String::new() } else { eyre::ensure!(path.contains(&id), "unexpected fixture container"); if index > 2 { fixture.replace("\"Running\": true", "\"Running\": false") } else { fixture.to_owned() } };
                let response = format!("HTTP/1.1 200 OK\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{body}", body.len());
                let next = if index < 4 { Some(tokio::net::windows::named_pipe::ServerOptions::new().create(&pipe)?) } else { None };
                server.write_all(response.as_bytes()).await?;
                server.flush().await?;
                if let Some(next) = next { server = next; }
            }
            Ok::<_, eyre::Report>(paths)
        });
        let request = crate::container_build::SessionStopRequest { target, output: None, remove: false };
        let result = tokio::time::timeout(Duration::from_secs(10), lifecycle::stop(&request, receipt, &CancellationToken::new())).await??;
        assert!(!result.running);
        let paths = serving.await??;
        assert!(paths[2].contains(&format!("/containers/{}/stop?", "a".repeat(64))));
        Ok::<_, eyre::Report>(())
    })
}

#[test]
fn null_annotations_or_missing_real_id_maps_cannot_admit_a_session() -> eyre::Result<()> {
    let fixture = include_str!("fixtures/admitted-worker.json");
    let mut worker: session_inspect::SessionInspect = facet_json::from_str(fixture)?;
    policy::validate(&worker)?;
    worker.host.annotations = None;
    assert!(policy::validate(&worker).is_err());
    let mut worker: session_inspect::SessionInspect = facet_json::from_str(fixture)?;
    worker.host.mappings = None;
    assert!(policy::validate(&worker).is_err());
    let mut worker: session_inspect::SessionInspect = facet_json::from_str(fixture)?;
    worker.host.mappings.as_mut().unwrap().uid = vec!["0:0:65536".into()];
    assert!(policy::validate(&worker).is_err());
    let raw = fixture.replace("\r\n", "\n").replace(
        "\"Annotations\": {\n      \"io.podman.annotations.userns\": \"auto:size=65536\"\n    }",
        "\"Annotations\": null",
    );
    let worker: session_inspect::SessionInspect = facet_json::from_str(&raw)?;
    assert!(worker.host.annotations.is_none());
    Ok(())
}

#[test]
fn created_auto_mapping_admission_cannot_admit_runtime_or_foreign_namespaces() -> eyre::Result<()> {
    let fixture = include_str!("fixtures/admitted-worker.json");
    let mut worker: session_inspect::SessionInspect = facet_json::from_str(fixture)?;
    worker.state.status = "created".into();
    worker.state.running = false;
    worker.host.userns.clear();
    policy::validate_created(&worker)?;
    assert!(
        policy::validate(&worker).is_err(),
        "created spec must never authorize game execution"
    );
    worker.host.userns = "host".into();
    assert!(policy::validate_created(&worker).is_err());
    worker.host.userns.clear();
    worker.state.running = true;
    assert!(policy::validate_created(&worker).is_err());
    worker.state.running = false;
    worker.state.status = "initialized".into();
    assert!(policy::validate_created(&worker).is_err());
    worker.state.status = "created".into();
    worker.host.mappings.as_mut().unwrap().uid = vec!["0:0:65536".into()];
    assert!(policy::validate_created(&worker).is_err());
    worker.host.mappings.as_mut().unwrap().uid = vec!["0:1:65536".into()];
    worker.host.annotations = None;
    assert!(policy::validate_created(&worker).is_err());
    Ok(())
}
