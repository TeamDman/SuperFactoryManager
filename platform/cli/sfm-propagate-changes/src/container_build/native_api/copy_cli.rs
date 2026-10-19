use http_body_util::BodyExt;
use std::path::Path;
use std::time::Duration;
/// Copy one admitted ELF from a fresh, never-started, mount-free container; remove that container.
pub(super) fn copy(endpoint: &Path, image: &str, timeout: Duration) -> eyre::Result<Vec<u8>> {
    let runtime = tokio::runtime::Builder::new_current_thread()
        .enable_all()
        .build()?;
    runtime.block_on(async { tokio::time::timeout(timeout,async {
  eyre::ensure!(image.len()==64 && image.bytes().all(|b|b.is_ascii_hexdigit()),"invalid exact image config ID");
  let body=format!(r#"{{"Image":"{image}","Cmd":["/bin/true"],"User":"10001:10001","WorkingDir":"/tmp","HostConfig":{{"NetworkMode":"none","ReadonlyRootfs":true,"CapDrop":["ALL"],"SecurityOpt":["no-new-privileges"],"Binds":[]}}}}"#);
  let mut created=super::transport::request(endpoint,"POST","/v1.44/containers/create",super::transport::bytes_body(body.clone()),body.len() as u64).await?;
  eyre::ensure!(created.response.status().is_success(),"copy container create HTTP {}",created.response.status());
  let mut raw=Vec::new();while let Some(frame)=created.response.body_mut().frame().await {if let Ok(bytes)=frame?.into_data(){eyre::ensure!(raw.len()+bytes.len()<2*1024*1024,"copy create response budget exceeded");raw.extend_from_slice(&bytes);}}
  let created:super::api_created::ApiCreated=facet_json::from_slice(&raw)?;
  eyre::ensure!(created.id.len()==64 && created.id.bytes().all(|b|b.is_ascii_hexdigit()),"invalid owned copy container ID");
  let copied=async {
   let mut archive=super::transport::request(endpoint,"GET",&format!("/v1.44/containers/{}/archive?path=/opt/sfm/cargo/bin/sfm-propagate-changes",created.id),super::transport::bytes_body(Vec::new()),0).await?;
   eyre::ensure!(archive.response.status().is_success(),"Linux CLI archive HTTP {}",archive.response.status());
   let mut raw=Vec::new();while let Some(frame)=archive.response.body_mut().frame().await {if let Ok(bytes)=frame?.into_data(){eyre::ensure!(raw.len()+bytes.len()<=256*1024*1024,"Linux CLI archive byte budget exceeded");raw.extend_from_slice(&bytes);}}
   extract(&raw)
  }.await;
  let removed=super::transport::small(endpoint,"DELETE",&format!("/v1.44/containers/{}?force=true&v=false",created.id)).await;
  removed?;copied
 }).await.map_err(|_elapsed|eyre::eyre!("Linux CLI copy time budget exceeded; scoped copy container may require cleanup"))? })
}
fn extract(raw: &[u8]) -> eyre::Result<Vec<u8>> {
    let mut at = 0;
    let mut output = None;
    while at + 512 <= raw.len() {
        let header = &raw[at..at + 512];
        at += 512;
        if header.iter().all(|b| *b == 0) {
            break;
        }
        let name = std::str::from_utf8(&header[..100])?.trim_end_matches(char::from(0));
        let size = usize::from_str_radix(
            std::str::from_utf8(&header[124..136])?.trim_matches([char::from(0), ' ']),
            8,
        )?;
        eyre::ensure!(
            size <= 256 * 1024 * 1024 && at + size <= raw.len(),
            "truncated/oversized CLI archive"
        );
        match header[156] {
            b'0' | 0 => {
                eyre::ensure!(
                    name == "sfm-propagate-changes" && output.is_none(),
                    "unexpected CLI archive file"
                );
                output = Some(raw[at..at + size].to_vec());
            }
            b'x' => {
                eyre::ensure!(size < 16384, "oversized archive metadata");
            }
            _ => eyre::bail!("CLI archive contains unapproved links or entries"),
        }
        at += size.div_ceil(512) * 512;
    }
    output.ok_or_else(|| eyre::eyre!("Linux CLI missing from archive"))
}
