use facet::Facet;
#[derive(Clone, Debug, Facet)]
#[facet(deny_unknown_fields)]
pub(crate) struct LinuxToolchainLock {
    pub schema: String,
    pub platform: String,
    pub source_lock_sha256: String,
    /// Reviewed base with Linux JDK, graphics stack, Rust/Cargo and locked source-build inputs.
    pub base_image: String,
    pub java_major: u16,
    /// Exact java -version substring; never a shell expression.
    pub java_identity: String,
    /// Verified ELF executable, relative to this contract's directory.
    pub cli_path: String,
    pub cli_sha256: String,
    /// Current CLI source tree identity binds this binary to the source being exported.
    pub cli_source_identity: String,
}
