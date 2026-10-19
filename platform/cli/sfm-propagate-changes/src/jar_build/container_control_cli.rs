//! Reuse the source-bound Linux control artifact baked into a support-agent image.
//! The fixed root-owned pin is an image build receipt, never a download recipe.
#[cfg(target_os = "linux")]
use eyre::ensure;
use std::path::PathBuf;

pub(crate) fn selected() -> eyre::Result<Option<PathBuf>> {
    let Some(requested) = std::env::var_os("SFM_CONTAINER_CONTROL_CLI_MANIFEST") else {
        return Ok(None);
    };
    #[cfg(not(target_os = "linux"))]
    {
        let _ = requested;
        eyre::bail!("container control artifact requires Linux");
    }
    #[cfg(target_os = "linux")]
    {
        use std::io::Read as _;
        use std::os::unix::fs::MetadataExt as _;
        #[derive(facet::Facet)]
        struct Pin {
            schema: String,
            executable: String,
            sha256: String,
            source_identity: String,
        }
        let path = PathBuf::from(requested);
        ensure!(
            path == std::path::Path::new("/opt/sfm/control-cli-pin.json"),
            "unexpected container control pin path"
        );
        let bytes = crate::source_projection::core_catalog::read_bounded_input(
            std::path::Path::new("/opt/sfm"),
            "control-cli-pin.json",
            4096,
        )?;
        let pin: Pin = facet_json::from_slice(&bytes)?;
        ensure!(
            pin.schema == "sfm:container_control_cli@1"
                && pin.executable == "/opt/sfm/cargo/bin/sfm"
                && pin.source_identity.starts_with("sha256:")
                && pin.source_identity.len() == 71,
            "invalid container control artifact receipt"
        );
        let path = crate::source_projection::candidate_lock::checked_file(
            std::path::Path::new("/opt/sfm"),
            "cargo/bin/sfm",
        )?;
        let file = std::fs::File::open(&path)?;
        let metadata = file.metadata()?;
        ensure!(
            metadata.uid() == 0
                && metadata.mode() & 0o022 == 0
                && metadata.len() <= 256 * 1024 * 1024,
            "container control artifact is writable, non-root-owned or oversized"
        );
        let mut binary = Vec::new();
        file.take(256 * 1024 * 1024 + 1).read_to_end(&mut binary)?;
        ensure!(
            binary.len() >= 64
                && &binary[..4] == b"\x7fELF"
                && binary[4] == 2
                && binary[18] == 62
                && binary[19] == 0
                && crate::source_projection::provenance::sha256(&binary) == pin.sha256,
            "container control artifact bytes differ from pinned amd64 ELF"
        );
        tracing::info!(executable = %path.display(), source_identity = %pin.source_identity,
            "Reusing pinned container control CLI; Cargo is not invoked");
        Ok(Some(path))
    }
}
