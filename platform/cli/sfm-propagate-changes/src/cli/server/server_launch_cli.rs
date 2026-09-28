use crate::cli::jar::BranchSelector;
use facet::Facet;
use figue as args;
use std::path::PathBuf;

/// Arguments for launching tracked servers.
#[derive(Facet, Debug)]
pub struct ServerLaunchArgs {
    /// Branch selector used to choose tracked server Minecraft versions.
    #[facet(args::named)]
    pub branch: BranchSelector,
    /// Exact-major JDK home for all selected servers. Without this, use unpinned local JDK discovery; tracked servers have no branch lockfile context.
    #[facet(default, args::named)]
    pub java_home: Option<PathBuf>,
}

impl ServerLaunchArgs {
    /// # Errors
    ///
    /// Returns an error if selected tracked servers cannot be launched.
    pub fn invoke(self) -> eyre::Result<()> {
        super::server_cli::launch_servers(self.branch, self.java_home.as_deref())
    }
}

#[cfg(test)]
mod tests {
    use super::ServerLaunchArgs;

    #[test]
    fn server_launch_parses_explicit_java_home_without_inferring_a_lockfile() {
        let args = ["--branch", "1.21.1", "--java-home", "test-jdk-21"];
        let parsed = figue::from_slice::<ServerLaunchArgs>(&args)
            .into_result()
            .expect("server launch arguments should parse")
            .get_silent();

        assert_eq!(parsed.branch.as_ref(), "1.21.1");
        assert_eq!(
            parsed.java_home.as_deref(),
            Some(std::path::Path::new("test-jdk-21"))
        );
    }

    #[test]
    fn server_launch_keeps_local_discovery_when_java_home_is_absent() {
        let parsed = figue::from_slice::<ServerLaunchArgs>(&["--branch", "1.19.2"])
            .into_result()
            .expect("server launch arguments should parse")
            .get_silent();

        assert!(parsed.java_home.is_none());
    }
}
