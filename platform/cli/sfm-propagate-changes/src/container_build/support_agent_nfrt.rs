use super::artifact::missing;
use super::artifact::put_bytes;
use super::context_manifest::ContextManifest;
use crate::cancellation::CancellationToken;
use crate::source_projection::core_catalog::read_bounded_input;
use crate::source_projection::provenance::sha256;
use eyre::ensure;
use std::path::Path;

#[derive(facet::Facet)]
struct Supplement {
    artifacts: Vec<Artifact>,
}
#[derive(facet::Facet)]
struct Artifact {
    coordinate: String,
    bytes: u64,
    sha256: String,
    targets: Vec<String>,
}

/// The captured original tool/source-library identities are separate from raw locks.
/// This exports their exact checked bytes; it never acquires or relaxes a pin.
pub(super) fn export(
    root: &Path,
    cache: &Path,
    stage: &Path,
    target: &str,
    report: &mut ContextManifest,
    max_bytes: u64,
    cancellation: &CancellationToken,
) -> eyre::Result<()> {
    if !matches!(
        target,
        "1.20.2" | "1.20.3" | "1.20.4" | "1.21.0" | "1.21.1" | "26.1.2"
    ) {
        return Ok(());
    }
    for (name, pin) in [
        (
            "nfrt-child-identities.json",
            "sha256:eabeb220e2cdfa3f3701e67a401472bd8a2f3adc696c9f300ef494a5ec4b81b2",
        ),
        (
            "nfrt-source-library-identities.json",
            "sha256:41ee3807912c5202a927654f257ef634432ccbd30f677e010a49e8018114a688",
        ),
    ] {
        let relative = format!("platform/minecraft/core-liquid-template/build/supplements/{name}");
        let raw = read_bounded_input(root, &relative, 128 * 1024)?;
        ensure!(
            sha256(&raw) == pin,
            "Unreviewed NFRT supplement; do not substitute identities"
        );
        let document: Supplement = facet_json::from_slice(&raw)?;
        ensure!(
            document.artifacts.len() <= 64,
            "NFRT supplement entry budget exceeded"
        );
        for artifact in document.artifacts {
            if !artifact.targets.iter().any(|value| value == target) {
                continue;
            }
            cancellation.bail_if_cancelled()?;
            export_artifact(
                cache,
                stage,
                report,
                max_bytes,
                cancellation,
                &artifact,
                pin,
            )?;
        }
    }
    Ok(())
}

fn export_artifact(
    cache: &Path,
    stage: &Path,
    report: &mut ContextManifest,
    max_bytes: u64,
    cancellation: &CancellationToken,
    artifact: &Artifact,
    parent: &str,
) -> eyre::Result<()> {
    let parts = artifact.coordinate.split(':').collect::<Vec<_>>();
    ensure!(
        (parts.len() == 3 || parts.len() == 4)
            && parts.iter().all(|part| !part.is_empty()
                && part
                    .bytes()
                    .all(|b| b.is_ascii_alphanumeric() || b"._-".contains(&b))),
        "Nonexact reviewed Maven coordinate"
    );
    ensure!(
        artifact.bytes > 0 && artifact.bytes <= 64 * 1024 * 1024,
        "NFRT child byte budget exceeded"
    );
    let classifier = parts
        .get(3)
        .map_or_else(String::new, |value| format!("-{value}"));
    let relative = format!(
        "maven/{}/{}/{}/{}-{}{}.jar",
        parts[0].replace('.', "/"),
        parts[1],
        parts[2],
        parts[1],
        parts[2],
        classifier
    );

    let destination = format!("cache/minecraft-toolchain/{relative}");
    let expected = format!("sha256:{}", artifact.sha256);
    match read_bounded_input(cache, &relative, 64 * 1024 * 1024) {
        Ok(bytes) if bytes.len() as u64 == artifact.bytes && sha256(&bytes) == expected => {
            cancellation.bail_if_cancelled()?;
            put_bytes(stage, &destination, &bytes, parent, report, max_bytes)?;
            let row = report
                .files
                .get_mut(&destination)
                .expect("staged checked artifact");
            row.expected_hash = Some(expected.clone());
            row.authenticated_parents.insert(parent.into(), expected);
        }
        Ok(_) => missing(
            report,
            destination,
            "Reviewed NFRT child hash/size mismatch",
        ),
        Err(error) => missing(
            report,
            destination,
            format!("Reviewed NFRT child absent: {error:#}"),
        ),
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn reviewed_sha256_child_is_bound_and_corruption_is_missing() {
        let cache = tempfile::tempdir().unwrap();
        let stage = tempfile::tempdir().unwrap();
        let path = cache.path().join("maven/g/a/1/a-1.jar");
        std::fs::create_dir_all(path.parent().unwrap()).unwrap();
        std::fs::write(&path, b"jar").unwrap();
        let artifact = Artifact {
            coordinate: "g:a:1".into(),
            bytes: 3,
            sha256: sha256(b"jar").trim_start_matches("sha256:").into(),
            targets: vec![],
        };
        let mut report = super::super::tests::report();
        let token = CancellationToken::new();
        export_artifact(
            cache.path(),
            stage.path(),
            &mut report,
            1024,
            &token,
            &artifact,
            "reviewed-parent",
        )
        .unwrap();
        let row = &report.files["cache/minecraft-toolchain/maven/g/a/1/a-1.jar"];
        assert_eq!(row.expected_hash.as_deref(), Some(sha256(b"jar").as_str()));
        assert_eq!(row.authenticated_parents["reviewed-parent"], sha256(b"jar"));
        std::fs::write(&path, b"bad").unwrap();
        let mut corrupt = super::super::tests::report();
        export_artifact(
            cache.path(),
            stage.path(),
            &mut corrupt,
            1024,
            &token,
            &artifact,
            "reviewed-parent",
        )
        .unwrap();
        assert!(corrupt.files.is_empty());
        assert_eq!(corrupt.missing.len(), 1);
    }
}
