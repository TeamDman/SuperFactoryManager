use crate::paths::InvocationDirectory;
use eyre::Result;
use eyre::ensure;
use eyre::eyre;
use facet::Facet;
use std::path::Path;

/// Explicit inputs; Git trees never imply historical catalog reconstruction.
#[derive(Clone, Debug, PartialEq, Eq, Facet)]
#[repr(u8)]
#[facet(deny_unknown_fields)]
pub enum ComparisonInput {
    Directory {
        root: String,
    },
    PinnedGit {
        repository: String,
        commit: String,
        prefix: String,
    },
    Projection {
        repository: String,
        projection: String,
    },
}

impl ComparisonInput {
    pub(crate) fn resolve_root(&mut self, invocation_dir: &InvocationDirectory<'_>) -> Result<()> {
        let root = match self {
            Self::Directory { root } => root,
            Self::PinnedGit { repository, .. } | Self::Projection { repository, .. } => repository,
        };
        *root = invocation_dir
            .resolve(Path::new(root))?
            .into_os_string()
            .into_string()
            .map_err(|_path| eyre!("comparison input root is not UTF-8"))?;
        Ok(())
    }

    pub(super) fn absolute_root(&self) -> Result<&Path> {
        let root = match self {
            Self::Directory { root } => root,
            Self::PinnedGit { repository, .. } | Self::Projection { repository, .. } => repository,
        };
        let path = Path::new(root);
        ensure!(path.is_absolute(), "comparison input root must be absolute");
        Ok(path)
    }
}
