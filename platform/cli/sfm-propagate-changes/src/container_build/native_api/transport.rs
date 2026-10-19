use bytes::Bytes;
use http_body_util::BodyExt;
use http_body_util::Full;
pub(super) type Body = http_body_util::combinators::BoxBody<Bytes, std::io::Error>;
pub(super) fn bytes_body(bytes: impl Into<Bytes>) -> Body {
    Full::new(bytes.into())
        .map_err(|never| match never {})
        .boxed()
}
pub(super) async fn request(
    endpoint: &std::path::Path,
    method: &str,
    path: &str,
    body: Body,
    length: u64,
) -> eyre::Result<super::api_response::ApiResponse> {
    #[cfg(windows)]
    let io = {
        let text = endpoint
            .to_str()
            .ok_or_else(|| eyre::eyre!("non-Unicode engine pipe"))?;
        eyre::ensure!(
            text.starts_with(r"\\.\pipe\") && !text[9..].contains(['\\', '/']),
            "native API requires one local advertised Windows named pipe"
        );
        tokio::net::windows::named_pipe::ClientOptions::new().open(endpoint)?
    };
    #[cfg(unix)]
    let io = {
        eyre::ensure!(
            endpoint.is_absolute(),
            "native API requires a local absolute Unix socket"
        );
        tokio::net::UnixStream::connect(endpoint).await?
    };
    let (mut sender, connection) =
        hyper::client::conn::http1::handshake(hyper_util::rt::TokioIo::new(io)).await?;
    let driver = tokio::spawn(connection);
    let request = hyper::Request::builder()
        .method(method)
        .uri(path)
        .header("Host", "localhost")
        .header("Connection", "close")
        .header(
            "Content-Type",
            if path.contains("/libpod/build?") {
                "application/x-tar"
            } else {
                "application/json"
            },
        )
        .header("Content-Length", length)
        .body(body)?;
    match sender.send_request(request).await {
        Ok(response) => Ok(super::api_response::ApiResponse { response, driver }),
        Err(error) => {
            driver.abort();
            Err(error.into())
        }
    }
}
pub(super) async fn small(
    endpoint: &std::path::Path,
    method: &str,
    path: &str,
) -> eyre::Result<Vec<u8>> {
    let mut received = request(endpoint, method, path, bytes_body(Bytes::new()), 0).await?;
    eyre::ensure!(
        received.response.status().is_success(),
        "engine API HTTP {}",
        received.response.status()
    );
    let mut bytes = Vec::new();
    while let Some(frame) = received.response.body_mut().frame().await {
        if let Ok(data) = frame?.into_data() {
            eyre::ensure!(
                bytes.len() + data.len() <= 2 * 1024 * 1024,
                "engine response exceeds byte budget"
            );
            bytes.extend_from_slice(&data);
        }
    }
    Ok(bytes)
}
