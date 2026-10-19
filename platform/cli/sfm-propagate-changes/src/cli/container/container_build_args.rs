use crate::container_build::ContainerBuildRequest;
use crate::container_build::ContainerEngine;
use facet::Facet;
use figue as args;
use std::path::Path;
use std::path::PathBuf;

#[derive(Debug, Facet)]
pub struct ContainerBuildArgs {
    /// Exact named projection key; globs and version branches are not accepted.
    #[facet(args::named)]
    pub projection: String,
    /// Runtime is lean/prebuilt; tests and developer retain SDK, compiled tests and public fixtures.
    #[facet(default, args::named)]
    pub profile: crate::container_build::ContainerBuildProfile,
    /// Source checkout; defaults to Git discovery from the invocation directory.
    #[facet(default, args::named)]
    pub repo_root: Option<PathBuf>,
    /// New context directory. Existing output is never merged or overwritten.
    #[facet(args::named)]
    pub context: PathBuf,
    /// Approved Linux toolchain contract, containing a base digest and exact Linux CLI pin.
    #[facet(default, args::named)]
    pub toolchain_lock: Option<PathBuf>,
    /// Canonical SFM cache root; defaults to `SFM_PROPAGATE_CHANGES_CACHE`.
    #[facet(default, args::named)]
    pub cache_root: Option<PathBuf>,
    /// Only linux/amd64 is admitted by this first container workflow.
    #[facet(default = "linux/amd64", args::named)]
    pub platform: String,
    #[facet(default, args::named)]
    pub engine: ContainerEngine,
    #[facet(default, args::named)]
    pub transport: crate::container_build::ContainerTransport,
    #[facet(default, args::named)]
    pub engine_endpoint: Option<PathBuf>,
    /// Local image tag. This command never pushes it.
    #[facet(default = "sfm-puppet:local", args::named)]
    pub tag: String,
    /// Publish the immutable context without invoking a container engine.
    #[facet(default, args::named)]
    pub prepare_only: bool,
    /// Reuse the existing context only after checking current source and pinned cache bytes.
    #[facet(default, args::named)]
    pub reuse_context: bool,
    /// Disable network acquisition and fail precisely on missing pinned inputs.
    #[facet(default, args::named)]
    pub offline: bool,
    /// Maximum admitted source plus cached-artifact bytes.
    #[facet(default = 8_589_934_592_u64, args::named)]
    pub max_bytes: u64,
    /// Bounded context preparation and engine build duration, such as 20m.
    #[facet(default = "45m", args::named)]
    pub timeout: String,
}

impl ContainerBuildArgs {
    pub(crate) fn into_request(self, invocation: &Path) -> eyre::Result<ContainerBuildRequest> {
        let absolute = |path: PathBuf| {
            if path.is_absolute() {
                path
            } else {
                invocation.join(path)
            }
        };
        let root = self.repo_root.map_or_else(
            || {
                gix::discover(invocation)?
                    .workdir()
                    .map(Path::to_path_buf)
                    .ok_or_else(|| eyre::eyre!("container build requires a source worktree"))
            },
            |root| Ok(absolute(root)),
        )?;
        let request = ContainerBuildRequest {
            projection: self.projection,
            profile: self.profile,
            repo_root: root,
            context: absolute(self.context),
            toolchain_lock: self.toolchain_lock.map(absolute),
            cache_root: self
                .cache_root
                .map_or_else(|| crate::paths::CACHE_DIR.0.clone(), absolute),
            platform: self.platform,
            engine: self.engine,
            transport: self.transport,
            engine_endpoint: self.engine_endpoint,
            tag: self.tag,
            prepare_only: self.prepare_only,
            reuse_context: self.reuse_context,
            offline: self.offline,
            max_bytes: self.max_bytes,
            timeout: humantime::parse_duration(&self.timeout)?,
        };
        request.validate()?;
        Ok(request)
    }
}
