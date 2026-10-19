//! Official Linux SDK pins. Acquisition is separate; the exporter is cache-only.
use super::ContainerBuildRequest;
use super::context_manifest::ContextManifest;
use crate::source_projection::core_catalog::read_bounded_input;
use crate::source_projection::provenance::sha256;
pub(super) const PINS: &[(u32, &str, u64, &str)] = &[
    (
        21,
        "ce79869e1307ed8ee1e2baa86a412b1eb5b75d10a01006d788a6f968bcfaee94",
        207_473_347,
        "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jdk_x64_linux_hotspot_21.0.12.1_1.tar.gz",
    ),
    (
        25,
        "dbb698396d478e7fa2b1e50f4103324b2a99b90569ee27c33f2261f9215cf41e",
        141_329_719,
        "https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.4.1%2B1/OpenJDK25U-jdk_x64_linux_hotspot_25.0.4.1_1.tar.gz",
    ),
];
pub(super) fn export(
    request: &ContainerBuildRequest,
    stage: &std::path::Path,
    report: &mut ContextManifest,
) -> eyre::Result<()> {
    for &(major, hash, size, url) in PINS {
        let relative = format!("linux-sdks/{hash}.tar.gz");
        match read_bounded_input(&request.cache_root, &relative, 256 * 1024 * 1024) {
            Ok(bytes)
                if bytes.len() as u64 == size && sha256(&bytes) == format!("sha256:{hash}") =>
            {
                super::artifact::put_bytes(
                    stage,
                    &format!("sdks/{major}.tar.gz"),
                    &bytes,
                    url,
                    report,
                    request.max_bytes,
                )?;
            }
            Ok(_) => super::artifact::missing(
                report,
                format!("linux-jdk-{major}"),
                "Official pinned archive hash/size mismatch",
            ),
            Err(error) => super::artifact::missing(
                report,
                format!("linux-jdk-{major}"),
                format!("Cache {relative} absent: {error:#}; official URL: {url}"),
            ),
        }
    }
    Ok(())
}
