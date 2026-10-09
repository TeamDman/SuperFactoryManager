use std::path::Path;
use std::path::PathBuf;

/// Resolve catalog keys, never Minecraft-version worktrees.
pub(crate) fn select_projections(
    repo_root: Option<&Path>,
    selector: &str,
) -> eyre::Result<(PathBuf, Vec<String>)> {
    let cwd = std::env::current_dir()?;
    let root = match repo_root {
        Some(root) => root.to_owned(),
        None => gix::discover(&cwd)?
            .workdir()
            .ok_or_else(|| eyre::eyre!("projection selection requires a repository worktree"))?
            .to_owned(),
    };
    let catalog = crate::source_projection::core_catalog::CoreCatalog::load(&root, &cwd)?;
    let keys = matching_keys(catalog.catalog.0.keys().map(String::as_str), selector)?;
    Ok((catalog.repo_root, keys))
}

fn matching_keys<'a>(
    keys: impl Iterator<Item = &'a str>,
    selector: &str,
) -> eyre::Result<Vec<String>> {
    let pattern = glob::Pattern::new(selector)?;
    let keys = keys
        .filter(|key| pattern.matches(key))
        .map(str::to_owned)
        .collect::<Vec<_>>();
    eyre::ensure!(!keys.is_empty(), "no projections match {selector:?}");
    Ok(keys)
}

#[cfg(test)]
mod tests {
    use super::matching_keys;

    const KEYS: [&str; 3] = [
        "sfm-dev/mc-1.19.2",
        "sfm-dev/mc-26.1.2",
        "sfm-4.34.0/mc-1.19.2",
    ];

    #[test]
    fn selectors_address_projection_keys_not_version_branches() {
        assert_eq!(matching_keys(KEYS.into_iter(), KEYS[0]).unwrap(), [KEYS[0]]);
        assert_eq!(
            matching_keys(KEYS.into_iter(), "sfm-dev/*").unwrap(),
            KEYS[..2]
        );
        assert_eq!(matching_keys(KEYS.into_iter(), "*").unwrap(), KEYS);
        assert!(matching_keys(KEYS.into_iter(), "1.19.2").is_err());
        assert!(matching_keys(KEYS.into_iter(), "../*").is_err());
        assert!(matching_keys(KEYS.into_iter(), "[").is_err());
    }
}
