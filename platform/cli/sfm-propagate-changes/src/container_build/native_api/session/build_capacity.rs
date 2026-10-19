//! Reuse the verified bounded capacity probe; never resize or clear engine memory.
use crate::cancellation::CancellationToken;
use std::path::Path;
fn digest(base: &str) -> eyre::Result<String> {
    let (_, hash) = base
        .rsplit_once("@sha256:")
        .ok_or_else(|| eyre::eyre!("capacity probe requires a digest-pinned existing base"))?;
    eyre::ensure!(
        hash.len() == 64
            && hash
                .bytes()
                .all(|b| b.is_ascii_digit() || (b'a'..=b'f').contains(&b)),
        "capacity probe base digest is invalid"
    );
    Ok(format!("sha256:{hash}"))
}
pub(in crate::container_build::native_api) async fn check(
    endpoint: &Path,
    base: &str,
    info: &super::super::api_info::ApiInfo,
    cancellation: &CancellationToken,
) -> eyre::Result<()> {
    let expected = digest(base)?;
    let mut url = reqwest::Url::parse("http://localhost/")?;
    url.path_segments_mut()
        .map_err(|()| eyre::eyre!("invalid image URI"))?
        .extend(["v6.0.2", "libpod", "images", base, "json"]);
    let image: super::super::api_image::ApiImage = facet_json::from_slice(
        &super::super::transport::small(endpoint, "GET", url.path()).await?,
    )?;
    eyre::ensure!(
        image.digest == expected && image.os == "linux" && image.architecture == "amd64",
        "capacity probe base identity/platform mismatch"
    );
    // Session admission requires 8GiB plus 2GiB reserve, stricter than the 6GiB build cap.
    super::memory_probe::check(endpoint, &image, info, cancellation).await
}
#[cfg(test)]
mod tests {
    use super::digest;
    #[test]
    fn capacity_probe_refuses_mutable_or_malformed_base_references() {
        assert!(digest("localhost/base:latest").is_err());
        assert!(digest("localhost/base@sha256:abcd").is_err());
        assert!(digest(&format!("localhost/base@sha256:{}", "A".repeat(64))).is_err());
        assert_eq!(
            digest(&format!("localhost/base@sha256:{}", "a".repeat(64))).unwrap(),
            format!("sha256:{}", "a".repeat(64))
        );
    }
}
