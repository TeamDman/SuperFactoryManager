use http_body_util::BodyExt as _;
use std::path::Path;

pub(super) async fn fetch(
    endpoint: &Path,
    name: &str,
    path: &str,
    budget: usize,
) -> eyre::Result<Option<Vec<u8>>> {
    let mut url = super::url(&["containers", name, "archive"])?;
    url.query_pairs_mut().append_pair("path", path);
    let uri = format!("{}?{}", url.path(), url.query().unwrap_or_default());
    let mut response = super::super::transport::request(
        endpoint,
        "GET",
        &uri,
        super::super::transport::bytes_body(Vec::new()),
        0,
    )
    .await?;
    if response.response.status() == hyper::StatusCode::NOT_FOUND {
        return Ok(None);
    }
    eyre::ensure!(
        response.response.status().is_success(),
        "artifact archive API HTTP {}",
        response.response.status()
    );
    let mut raw = Vec::new();
    while let Some(frame) = response.response.body_mut().frame().await {
        if let Ok(data) = frame?.into_data() {
            eyre::ensure!(
                raw.len() + data.len() <= budget + 65536,
                "artifact archive exceeds byte budget"
            );
            raw.extend_from_slice(&data);
        }
    }
    let name = path
        .rsplit('/')
        .next()
        .ok_or_else(|| eyre::eyre!("missing artifact basename"))?;
    Ok(Some(single_file(&raw, name, budget)?))
}
fn text(bytes: &[u8]) -> eyre::Result<&str> {
    Ok(std::str::from_utf8(
        bytes.split(|b| *b == 0).next().unwrap_or_default(),
    )?)
}
fn octal(bytes: &[u8]) -> eyre::Result<usize> {
    let value = text(bytes)?.trim();
    Ok(if value.is_empty() {
        0
    } else {
        usize::from_str_radix(value, 8)?
    })
}
fn pax(bytes: &[u8], expected: &str) -> eyre::Result<()> {
    let mut rest = bytes;
    while !rest.is_empty() {
        let space = rest
            .iter()
            .position(|b| *b == b' ')
            .ok_or_else(|| eyre::eyre!("invalid PAX length"))?;
        let length = std::str::from_utf8(&rest[..space])?.parse::<usize>()?;
        eyre::ensure!(
            length > space + 1 && length <= rest.len() && rest[length - 1] == b'\n',
            "invalid PAX record"
        );
        let record = std::str::from_utf8(&rest[space + 1..length - 1])?;
        let (key, value) = record
            .split_once('=')
            .ok_or_else(|| eyre::eyre!("invalid PAX record"))?;
        match key {
            "path" => eyre::ensure!(
                value.trim_start_matches("./") == expected,
                "PAX path does not match requested artifact"
            ),
            "linkpath" => eyre::bail!("PAX links refused"),
            "size" => {
                eyre::bail!("PAX size override refused");
            }
            _ => eyre::ensure!(!key.starts_with("GNU.sparse"), "sparse archive refused"),
        }
        rest = &rest[length..];
    }
    Ok(())
}
pub(super) fn single_file(raw: &[u8], expected: &str, budget: usize) -> eyre::Result<Vec<u8>> {
    eyre::ensure!(
        !expected.is_empty() && !expected.contains(['/', '\\']) && expected != "..",
        "unsafe requested artifact name"
    );
    let mut offset = 0;
    let mut result = None;
    while offset + 512 <= raw.len() {
        let header = &raw[offset..offset + 512];
        offset += 512;
        if header.iter().all(|b| *b == 0) {
            eyre::ensure!(
                raw[offset..].iter().all(|b| *b == 0),
                "data after tar terminator"
            );
            break;
        }
        let actual: usize = header
            .iter()
            .enumerate()
            .map(|(i, b)| {
                if (148..156).contains(&i) {
                    usize::from(b' ')
                } else {
                    usize::from(*b)
                }
            })
            .sum();
        eyre::ensure!(
            actual == octal(&header[148..156])?,
            "tar header checksum mismatch"
        );
        let size = octal(&header[124..136])?;
        let padding = size
            .checked_add(511)
            .ok_or_else(|| eyre::eyre!("tar size overflow"))?
            / 512
            * 512;
        eyre::ensure!(
            size <= budget
                && offset
                    .checked_add(padding)
                    .is_some_and(|end| end <= raw.len()),
            "tar artifact size/truncation budget exceeded"
        );
        let body = &raw[offset..offset + size];
        offset += padding;
        match header[156] {
            b'x' | b'g' => {
                eyre::ensure!(size <= 65536, "PAX byte budget exceeded");
                pax(body, expected)?;
            }
            0 | b'0' => {
                let name = text(&header[..100])?;
                let prefix = text(&header[345..500])?;
                eyre::ensure!(
                    prefix.is_empty()
                        && name.trim_start_matches("./") == expected
                        && result.is_none(),
                    "unexpected or duplicate file in artifact archive"
                );
                result = Some(body.to_vec());
            }
            _ => eyre::bail!("links/directories/other tar entry types refused"),
        }
    }
    eyre::ensure!(
        offset == raw.len() || raw[offset..].iter().all(|b| *b == 0),
        "truncated tar header"
    );
    result.ok_or_else(|| eyre::eyre!("requested artifact absent from archive"))
}
