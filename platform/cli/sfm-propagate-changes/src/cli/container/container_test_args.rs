use facet::Facet;
use figue as args;
use std::path::Path;
use std::path::PathBuf;
#[derive(Debug, Facet)]
pub struct ContainerTestArgs {
    #[facet(args::named)]
    pub engine_endpoint: PathBuf,
    /// Exact existing Linux image configuration ID, never a mutable tag.
    #[facet(args::named)]
    pub image: String,
    #[facet(default = "sfm-dev/mc-1.19.2", args::named)]
    pub projection: String,
    /// Immutable context matching this image's source/provenance label.
    #[facet(args::named)]
    pub context: PathBuf,
    /// New directory receiving bounded logs, security evidence, counts and cleanup receipt.
    #[facet(args::named)]
    pub output: PathBuf,
    #[facet(default = 900_u64, args::named)]
    pub timeout_seconds: u64,
}
impl ContainerTestArgs {
    pub(crate) fn into_request(
        self,
        invocation: &Path,
    ) -> eyre::Result<crate::container_build::ContainerTestRequest> {
        let absolute = |p: PathBuf| {
            if p.is_absolute() {
                p
            } else {
                invocation.join(p)
            }
        };
        let request = crate::container_build::ContainerTestRequest {
            engine_endpoint: self.engine_endpoint,
            image: self.image,
            projection: self.projection,
            context: absolute(self.context),
            output: absolute(self.output),
            timeout_seconds: self.timeout_seconds,
        };
        request.validate()?;
        Ok(request)
    }
}
