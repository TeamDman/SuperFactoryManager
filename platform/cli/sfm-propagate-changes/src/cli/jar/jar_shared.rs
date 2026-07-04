use crate::cli::jar::get_jar_dir;
use eyre::Context;
use std::ffi::OsStr;
use std::path::Path;
use std::path::PathBuf;
use tracing::info;

pub(super) fn update_mods_folder_with_jar(mods_dir: &Path, jar: &Path) -> eyre::Result<()> {
    std::fs::create_dir_all(mods_dir)?;

    for existing in std::fs::read_dir(mods_dir)?
        .filter_map(Result::ok)
        .map(|entry| entry.path())
        .filter(|path| {
            path.is_file()
                && path
                    .file_name()
                    .and_then(OsStr::to_str)
                    .is_some_and(|name| name.contains("Super Factory Manager"))
                && path
                    .extension()
                    .is_some_and(|extension| extension == OsStr::new("jar"))
        })
    {
        std::fs::remove_file(&existing)
            .wrap_err_with(|| format!("Failed to remove old jar: {}", existing.display()))?;
    }

    let destination = mods_dir.join(
        jar.file_name()
            .ok_or_else(|| eyre::eyre!("Jar filename missing: {}", jar.display()))?,
    );

    std::fs::copy(jar, &destination).wrap_err_with(|| {
        format!(
            "Failed to copy jar from {} to {}",
            jar.display(),
            destination.display()
        )
    })?;

    info!(
        source = %jar.display(),
        destination = %destination.display(),
        "Updated mods folder with jar"
    );

    Ok(())
}

pub(super) fn find_best_jar_for_mc_version(mc_version: &str) -> eyre::Result<Option<PathBuf>> {
    let jar_dir = get_jar_dir()?;
    if !jar_dir.exists() {
        return Ok(None);
    }

    let needle = format!("-MC{mc_version}-");

    let mut matches: Vec<PathBuf> = std::fs::read_dir(&jar_dir)?
        .filter_map(Result::ok)
        .map(|entry| entry.path())
        .filter(|path| {
            path.is_file()
                && path
                    .extension()
                    .is_some_and(|extension| extension == OsStr::new("jar"))
                && path
                    .file_name()
                    .and_then(OsStr::to_str)
                    .is_some_and(|name| name.contains(&needle))
        })
        .collect();

    matches.sort_by(|a, b| {
        let a_name = a.file_name().and_then(OsStr::to_str).unwrap_or_default();
        let b_name = b.file_name().and_then(OsStr::to_str).unwrap_or_default();
        a_name.cmp(b_name)
    });

    Ok(matches.into_iter().last())
}

pub(super) fn read_mod_version(gradle_properties: &Path) -> eyre::Result<String> {
    let content = std::fs::read_to_string(gradle_properties)
        .wrap_err("Failed to read gradle.properties for mod_version")?;

    let mod_version = content
        .lines()
        .map(str::trim)
        .filter(|line| !line.is_empty())
        .filter(|line| !line.starts_with('#'))
        .find_map(|line| line.strip_prefix("mod_version=").map(str::trim))
        .ok_or_else(|| eyre::eyre!("mod_version not found"))?;

    if mod_version.is_empty() {
        eyre::bail!("mod_version was empty");
    }

    Ok(mod_version.to_string())
}

pub(super) fn pick_jar_for_mod_version(
    libs_dir: &Path,
    mod_version: &str,
) -> eyre::Result<Option<PathBuf>> {
    let suffix = format!("-{mod_version}.jar");
    let rust_suffix = format!("-{mod_version}-rust.jar");

    let mut candidates: Vec<PathBuf> = std::fs::read_dir(libs_dir)?
        .filter_map(Result::ok)
        .map(|entry| entry.path())
        .filter(|path| {
            path.is_file()
                && path
                    .extension()
                    .is_some_and(|extension| extension == OsStr::new("jar"))
                && path
                    .file_name()
                    .and_then(OsStr::to_str)
                    .is_some_and(|name| name.ends_with(&suffix) || name.ends_with(&rust_suffix))
        })
        .collect();

    candidates.sort_by(|a, b| {
        let a_name = a.file_name().and_then(OsStr::to_str).unwrap_or_default();
        let b_name = b.file_name().and_then(OsStr::to_str).unwrap_or_default();
        let a_is_rust = a_name.ends_with(&rust_suffix);
        let b_is_rust = b_name.ends_with(&rust_suffix);
        a_is_rust.cmp(&b_is_rust).then_with(|| a_name.cmp(b_name))
    });

    Ok(candidates.into_iter().next())
}

pub(super) fn release_jar_file_name(jar: &Path) -> eyre::Result<String> {
    let file_name = jar
        .file_name()
        .and_then(OsStr::to_str)
        .ok_or_else(|| eyre::eyre!("Jar filename missing or invalid UTF-8: {}", jar.display()))?;

    Ok(file_name.replace("-rust.jar", ".jar"))
}
