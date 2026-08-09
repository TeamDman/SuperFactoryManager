use eyre::Context as _;
use std::path::Path;

pub(crate) fn read_version_source_excludes(
    minecraft_dir: &Path,
    minecraft_version: &str,
    source_set: &str,
) -> eyre::Result<Vec<String>> {
    let path = minecraft_dir
        .join("gradle")
        .join("source-excludes")
        .join(minecraft_version)
        .join(format!("{source_set}-java.txt"));
    if !path.exists() {
        return Ok(Vec::new());
    }
    let excludes_text = std::fs::read_to_string(&path)
        .wrap_err_with(|| format!("Failed to read {}", path.display()))?;
    Ok(excludes_text
        .lines()
        .map(str::trim)
        .filter(|line| !line.is_empty() && !line.starts_with('#'))
        .map(|line| line.replace('\\', "/"))
        .collect())
}

pub(crate) fn is_excluded_java_source(relative: &str, excludes: &[String]) -> bool {
    excludes.iter().any(|exclude| {
        if let Some(prefix) = exclude.strip_suffix("/**") {
            relative.starts_with(prefix)
        } else if Path::new(exclude)
            .extension()
            .is_some_and(|extension| extension.eq_ignore_ascii_case("java"))
        {
            relative == exclude
        } else {
            relative == exclude || relative.starts_with(&format!("{exclude}/"))
        }
    })
}
