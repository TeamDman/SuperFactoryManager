mod api_component;
mod api_created;
mod api_error_detail;
mod api_host;
mod api_image;
mod api_image_config;
mod api_info;
mod api_namespace;
mod api_progress;
mod api_response;
mod api_security;
mod api_store;
mod api_version;
mod archive;
mod copy_cli;
pub(super) mod image_cache;
mod memory_status;
mod policy;
pub(super) mod session;
mod transport;
use super::ContainerBuildRequest;
use super::ContainerEngine;
use super::context_manifest::ContextManifest;
use super::image_identity::ImageIdentity;
use crate::cancellation::CancellationToken;
use http_body_util::BodyExt;
use std::io::Write as _;
use std::time::Duration;
use tokio::io::AsyncReadExt as _;
pub(super) fn build(
    request: &ContainerBuildRequest,
    report: &ContextManifest,
    cancellation: &CancellationToken,
    timeout: Duration,
) -> eyre::Result<ImageIdentity> {
    eyre::ensure!(
        request.engine == ContainerEngine::Podman,
        "native API requires verified Podman; choose command transport for another engine"
    );
    let endpoint = request.engine_endpoint.as_ref().ok_or_else(|| {
        eyre::eyre!("native API requires the advertised local --engine-endpoint pipe/socket")
    })?;
    super::disk::check(
        &request.context,
        report.total_bytes.saturating_add(8 * 1024 * 1024 * 1024),
    )?;
    let runtime = tokio::runtime::Builder::new_current_thread()
        .enable_all()
        .build()?;
    runtime.block_on(async {
        tokio::time::timeout(timeout,async {
            cancellation.bail_if_cancelled()?;
            let version:api_version::ApiVersion=facet_json::from_slice(&transport::small(endpoint,"GET","/version").await?)?;
            eyre::ensure!(version.version==policy::VERSION && version.os=="linux" && version.arch=="amd64"
                && version.components.iter().any(|part|part.name=="Podman Engine"),"unsupported engine identity/version; native safety profile admits only reviewed Podman 6.0.2 linux/amd64");
            let info:api_info::ApiInfo=facet_json::from_slice(&transport::small(endpoint,"GET","/v6.0.2/libpod/info").await?)?;
            info.store.check()?;
            eyre::ensure!(info.host.security.rootless && info.host.security.seccomp_enabled,"native build requires existing rootless/seccomp-enabled engine");
            eyre::ensure!(info.host.mem_total>=policy::MEMORY+2*1024*1024*1024,"insufficient total Linux memory for capped build; engine sizing was not changed");
            #[cfg(windows)]
            eyre::ensure!(memory_status::available()? >= policy::MEMORY+4*1024*1024*1024,"insufficient Windows memory headroom for capped build");
            {
                let _reuse_timer=super::phase_timer::PhaseTimer::start("image_reuse_check",0);
                if let Some(image)=image_cache::reuse(endpoint,request,report).await? { return Ok(image); }
            }
            if info.host.mem_free < policy::MEMORY + 2*1024*1024*1024 {
                let base = report.base_image.as_deref().ok_or_else(|| eyre::eyre!("low MemFree and no pinned executable base for bounded MemAvailable admission"))?;
                session::check_build_capacity(endpoint, base, &info, cancellation).await?;
            }
            // Rechecked immutable contexts may build another public graph target without source mutation.
            let archive=archive::prepare(&request.context,report,cancellation)?;
            let unique_tag=format!("sfm-container-build:{:016x}",rand::random::<u64>());
            let query=policy::query(request.offline,&unique_tag)?;
            let mut query_url=reqwest::Url::parse(&format!("http://localhost/?{query}"))?;
            let labels=std::collections::BTreeMap::from([(image_cache::LABEL.to_owned(),report.context_identity.clone().ok_or_else(||eyre::eyre!("context identity missing"))?)]);
            if report.toolchain_lock_sha256.is_some(){query_url.query_pairs_mut().append_pair("target",request.profile.stage());}
            let mut labels=labels;labels.insert("org.sfm.container.profile".into(),image_cache::profile(request,report).into());
            query_url.query_pairs_mut().append_pair("labels",&facet_json::to_string(&labels)?);
            let query=query_url.query().unwrap_or_default();
            let file=tokio::fs::File::open(archive.path()).await?;let length=file.metadata().await?.len();
            let stream=futures_util::stream::try_unfold((file,super::phase_timer::PhaseTimer::start("archive_body_produced",length)),|(mut file,timer)|async move {
                let mut bytes=vec![0_u8;65536];let read=file.read(&mut bytes).await?;if read==0 {return Ok::<_,std::io::Error>(None);}bytes.truncate(read);
                Ok(Some((hyper::body::Frame::data(bytes::Bytes::from(bytes)),(file,timer))))
            });
            let body=http_body_util::StreamBody::new(stream).boxed();
            let transfer_timer=super::phase_timer::PhaseTimer::start("http_build_response_headers",length);
            let mut response=transport::request(endpoint,"POST",&format!("/v6.0.2/libpod/build?{query}"),body,length).await?;
            drop(transfer_timer);
            let engine_timer=super::phase_timer::PhaseTimer::start("build_response_stream",0);
            eyre::ensure!(response.response.status().is_success(),"native build API HTTP {}",response.response.status());
            let mut line=Vec::new();let mut total=0_usize;

            let diagnostic_path=request.context.join("build-progress.log");
            let mut diagnostic=std::fs::OpenOptions::new().create(true).append(true).open(&diagnostic_path)?;
            let mut capacity_checked=std::time::Instant::now();
            while let Some(frame)=response.response.body_mut().frame().await {
                cancellation.bail_if_cancelled()?;
                if capacity_checked.elapsed()>=Duration::from_secs(5) {
                    super::disk::check(&request.context,2*1024*1024*1024)?;
                    let info:api_info::ApiInfo=facet_json::from_slice(&transport::small(endpoint,"GET","/v6.0.2/libpod/info").await?)?;
                    info.store.check()?; capacity_checked=std::time::Instant::now();
                }
                if let Ok(data)=frame?.into_data() { diagnostic.write_all(&data)?;diagnostic.flush()?;total+=data.len();eyre::ensure!(total<=64*1024*1024,"build progress byte budget exceeded");
                    for byte in data {if byte==b'\n' {if !line.is_empty(){policy::progress(&line)?;line.clear();}}else{eyre::ensure!(line.len()<4*1024*1024,"build progress line budget exceeded");line.push(byte);}}
                }
            }
            if !line.is_empty(){policy::progress(&line)?;}
            drop(engine_timer);
            let mut url=reqwest::Url::parse("http://localhost/")?;url.path_segments_mut().map_err(|()|eyre::eyre!("invalid local image URI"))?.extend(["v6.0.2","libpod","images",unique_tag.as_str(),"json"]);
            let image:api_image::ApiImage=facet_json::from_slice(&transport::small(endpoint,"GET",url.path()).await?)?;
            eyre::ensure!(image.os=="linux" && image.architecture=="amd64","built image has wrong platform");
            eyre::ensure!(image.config.labels.as_ref().and_then(|labels|labels.get(image_cache::LABEL))==report.context_identity.as_ref(),"built image context binding missing");
            eyre::ensure!(image.config.labels.as_ref().and_then(|m|m.get("org.sfm.container.profile")).is_some_and(|p|p==image_cache::profile(request,report)),"built image profile binding missing");
            image_cache::tag(endpoint,&image.id,&request.tag).await?;
            let identity=ImageIdentity {image_config_id:image.id,manifest_digest:image.digest,manifest_media_type:image.manifest_type,repository_digests:image.repo_digests,os:image.os,architecture:image.architecture};
            image_cache::save(request,report,&identity)?;
            Ok::<_,eyre::Report>(identity)
        }).await.map_err(|_elapsed|eyre::eyre!("native image build exceeded time budget; connection closed; engine completion is unconfirmed"))?
    })
}

pub(super) fn memory_limit() -> u64 {
    policy::MEMORY
}
pub(super) fn copy_linux_cli(
    endpoint: &std::path::Path,
    image: &str,
    timeout: Duration,
) -> eyre::Result<Vec<u8>> {
    copy_cli::copy(endpoint, image, timeout)
}
