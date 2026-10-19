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
    let candidates = keys.collect::<Vec<_>>();
    let keys = candidates
        .iter()
        .copied()
        .filter(|key| pattern.matches(key))
        .map(str::to_owned)
        .collect::<Vec<_>>();
    if keys.is_empty() {
        let available = if candidates.is_empty() {
            "  (none defined)".to_owned()
        } else {
            candidates
                .iter()
                .map(|key| format!("  {key}"))
                .collect::<Vec<_>>()
                .join("\n")
        };
        eyre::bail!("no projections match {selector:?}\n\nAvailable projections:\n{available}");
    }
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
    fn unmatched_selector_lists_all_available_projections() {
        let error = matching_keys(KEYS.into_iter(), "a").unwrap_err();
        assert_eq!(
            error.to_string(),
            format!(
                "no projections match \"a\"\n\nAvailable projections:\n  {}",
                KEYS.join("\n  ")
            )
        );
    }

    #[test]
    fn empty_catalog_reports_no_available_projections() {
        let error = matching_keys(std::iter::empty(), "a").unwrap_err();
        assert_eq!(
            error.to_string(),
            "no projections match \"a\"\n\nAvailable projections:\n  (none defined)"
        );
    }

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
