use crate::cancellation::CancellationToken;
use eyre::Context;
use reqwest::StatusCode;
use reqwest::blocking::Client;
use std::path::Path;

pub(crate) trait PayloadFetcher {
    fn fetch(
        &self,
        url: &str,
        cancellation_token: &CancellationToken,
    ) -> eyre::Result<Option<Vec<u8>>>;
}

pub(crate) struct ReqwestFetcher(Client);

pub(crate) fn http_fetcher() -> eyre::Result<ReqwestFetcher> {
    let client = Client::builder()
        .user_agent(concat!("sfm-propagate-changes/", env!("CARGO_PKG_VERSION")))
        .build()
        .wrap_err("Failed to create HTTP client")?;
    Ok(ReqwestFetcher(client))
}

impl PayloadFetcher for ReqwestFetcher {
    fn fetch(
        &self,
        url: &str,
        cancellation_token: &CancellationToken,
    ) -> eyre::Result<Option<Vec<u8>>> {
        cancellation_token.bail_if_cancelled()?;
        let response = self
            .0
            .get(url)
            .send()
            .wrap_err_with(|| format!("Failed to fetch {url}"))?;
        if response.status() == StatusCode::NOT_FOUND {
            return Ok(None);
        }
        let response = response
            .error_for_status()
            .wrap_err_with(|| format!("Remote repository rejected {url}"))?;
        cancellation_token.bail_if_cancelled()?;
        Ok(Some(response.bytes()?.to_vec()))
    }
}

pub(crate) fn write_payload_atomically(path: &Path, bytes: &[u8]) -> eyre::Result<()> {
    let parent = path
        .parent()
        .ok_or_else(|| eyre::eyre!("Payload cache path has no parent: {}", path.display()))?;
    std::fs::create_dir_all(parent)
        .wrap_err_with(|| format!("Failed to create {}", parent.display()))?;
    let mut temporary = tempfile::Builder::new()
        .prefix(".sfm-payload.")
        .suffix(".tmp")
        .tempfile_in(parent)?;
    std::io::Write::write_all(&mut temporary, bytes)?;
    temporary.as_file_mut().sync_all()?;
    temporary
        .persist(path)
        .map_err(|error| error.error)
        .wrap_err_with(|| format!("Failed to publish {}", path.display()))?;
    Ok(())
}
