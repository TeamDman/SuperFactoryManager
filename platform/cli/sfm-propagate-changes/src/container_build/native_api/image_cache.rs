use super::super::ContainerBuildRequest;
use super::super::context_manifest::ContextManifest;
use super::super::image_identity::ImageIdentity;
use super::api_image::ApiImage;
use super::transport;
use crate::source_projection::candidate_lock::checked_directory;
use crate::source_projection::candidate_lock::checked_file;
use std::path::Path;
use std::path::PathBuf;
pub(super) const LABEL: &str = "org.sfm.container.context";
pub(in crate::container_build) fn profile<'a>(
    request: &'a ContainerBuildRequest,
    report: &ContextManifest,
) -> &'a str {
    if report.toolchain_lock_sha256.is_some() {
        request.profile.stage()
    } else if report.projection == "portable-dependency-cache" {
        "dependencies"
    } else {
        "tooling"
    }
}
fn receipt(request: &ContainerBuildRequest, report: &ContextManifest) -> eyre::Result<PathBuf> {
    let root = request
        .context
        .parent()
        .ok_or_else(|| eyre::eyre!("image receipt parent missing"))?
        .join(".sfm-container-images");
    std::fs::create_dir_all(&root)?;
    let root = checked_directory(&root)?;
    let key = report
        .context_identity
        .as_deref()
        .ok_or_else(|| eyre::eyre!("image context identity missing"))?
        .strip_prefix("sha256:")
        .ok_or_else(|| eyre::eyre!("image context identity invalid"))?;
    eyre::ensure!(
        key.len() == 64
            && key
                .bytes()
                .all(|b| b.is_ascii_hexdigit() && !b.is_ascii_uppercase()),
        "image context identity invalid"
    );
    Ok(root.join(format!("{key}-{}.json", profile(request, report))))
}
pub(super) fn save(
    request: &ContainerBuildRequest,
    report: &ContextManifest,
    image: &ImageIdentity,
) -> eyre::Result<()> {
    use std::io::Write as _;
    let target = receipt(request, report)?;
    if target.exists() {
        checked_file(
            target.parent().expect("receipt parent"),
            target
                .file_name()
                .and_then(|n| n.to_str())
                .expect("receipt filename"),
        )?;
    }
    let mut temporary = tempfile::NamedTempFile::new_in(target.parent().expect("receipt parent"))?;
    temporary.write_all(facet_json::to_string_pretty(image)?.as_bytes())?;
    temporary.as_file().sync_all()?;
    temporary.persist(target).map_err(|error| error.error)?;
    Ok(())
}
pub(super) fn path(reference: &str, action: &str) -> eyre::Result<String> {
    let mut url = reqwest::Url::parse("http://localhost/")?;
    url.path_segments_mut()
        .map_err(|()| eyre::eyre!("invalid local image URI"))?
        .extend(["v6.0.2", "libpod", "images", reference, action]);
    Ok(url.path().to_owned())
}
pub(super) async fn inspect(endpoint: &Path, reference: &str) -> eyre::Result<Option<ApiImage>> {
    use http_body_util::BodyExt as _;
    let mut response = transport::request(
        endpoint,
        "GET",
        &path(reference, "json")?,
        transport::bytes_body(Vec::new()),
        0,
    )
    .await?;
    if response.response.status() == hyper::StatusCode::NOT_FOUND {
        return Ok(None);
    }
    eyre::ensure!(
        response.response.status().is_success(),
        "image inspection HTTP {}",
        response.response.status()
    );
    let mut bytes = Vec::new();
    while let Some(frame) = response.response.body_mut().frame().await {
        if let Ok(data) = frame?.into_data() {
            eyre::ensure!(
                bytes.len() + data.len() <= 2 * 1024 * 1024,
                "image inspection byte budget exceeded"
            );
            bytes.extend_from_slice(&data);
        }
    }
    Ok(Some(facet_json::from_slice(&bytes)?))
}
pub(super) async fn tag(endpoint: &Path, image: &str, tag: &str) -> eyre::Result<()> {
    let mut url = reqwest::Url::parse(&format!("http://localhost{}", path(image, "tag")?))?;
    let (repository, tag) = tag.rsplit_once(':').unwrap_or((tag, "latest"));
    url.query_pairs_mut()
        .append_pair("repo", repository)
        .append_pair("tag", tag);
    transport::small(
        endpoint,
        "POST",
        &format!("{}?{}", url.path(), url.query().unwrap_or_default()),
    )
    .await?;
    Ok(())
}
pub(super) async fn reuse(
    endpoint: &Path,
    request: &ContainerBuildRequest,
    report: &ContextManifest,
) -> eyre::Result<Option<ImageIdentity>> {
    let target = receipt(request, report)?;
    if !target.exists() {
        return Ok(None);
    }
    let bytes = crate::source_projection::core_catalog::read_bounded_input(
        target.parent().expect("receipt parent"),
        target
            .file_name()
            .and_then(|n| n.to_str())
            .expect("receipt filename"),
        1024 * 1024,
    )?;
    let saved: ImageIdentity = facet_json::from_slice(&bytes)?;
    let Some(live) = inspect(endpoint, &saved.image_config_id).await? else {
        return Ok(None);
    };
    eyre::ensure!(
        live.id == saved.image_config_id
            && live.digest == saved.manifest_digest
            && live.os == "linux"
            && live.architecture == "amd64",
        "cached image identity changed"
    );
    eyre::ensure!(
        live.config
            .labels
            .as_ref()
            .and_then(|labels| labels.get(LABEL))
            == report.context_identity.as_ref(),
        "cached image lacks exact context binding; receipt is not trusted alone"
    );
    eyre::ensure!(
        live.config
            .labels
            .as_ref()
            .and_then(|m| m.get("org.sfm.container.profile"))
            .is_some_and(|p| p == profile(request, report)),
        "cached image profile mismatch"
    );
    tag(endpoint, &live.id, &request.tag).await?;
    tracing::info!(context_identity=?report.context_identity,image_config_id=%live.id,archive_bytes=0,"verified_image_reuse");
    Ok(Some(saved))
}
