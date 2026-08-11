use crate::branch_targets::MinecraftVersion;
use crate::cli::jar::get_jar_dir;
use eyre::Context;
use std::ffi::OsStr;
use std::path::Path;
use std::path::PathBuf;
use tracing::info;

pub(super) fn update_mods_folder_with_jar(mods_dir: &Path, jar: &Path) -> eyre::Result<()> {
    std::fs::create_dir_all(mods_dir)?;

    remove_sfm_jars_from_mods_folder(mods_dir)?;

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

pub(super) fn remove_sfm_jars_from_mods_folder(mods_dir: &Path) -> eyre::Result<()> {
    if !mods_dir.exists() {
        return Ok(());
    }

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

    Ok(())
}

pub(crate) fn resolve_client_mods_dir(
    instance_dir: &Path,
    mc_version: &str,
) -> eyre::Result<PathBuf> {
    Ok(resolve_client_game_dir(instance_dir, mc_version)?.join("mods"))
}

pub(super) fn remove_sfm_jars_from_other_client_game_dirs(
    instance_dir: &Path,
    selected_mods_dir: &Path,
) -> eyre::Result<()> {
    for game_dir in client_game_dir_candidates(instance_dir) {
        let mods_dir = game_dir.join("mods");
        if mods_dir != selected_mods_dir {
            remove_sfm_jars_from_mods_folder(&mods_dir)?;
        }
    }

    Ok(())
}

fn resolve_client_game_dir(instance_dir: &Path, mc_version: &str) -> eyre::Result<PathBuf> {
    let sentinel_matches: Vec<PathBuf> = client_game_dir_candidates(instance_dir)
        .into_iter()
        .filter(|game_dir| game_dir.join("options.txt").is_file())
        .collect();

    match sentinel_matches.as_slice() {
        [game_dir] => return Ok(game_dir.clone()),
        [] => {}
        matches => {
            let rendered = matches
                .iter()
                .map(|path| path.display().to_string())
                .collect::<Vec<_>>()
                .join(", ");
            eyre::bail!(
                "Multiple Prism game directories contain options.txt for {}: {}",
                instance_dir.display(),
                rendered
            );
        }
    }

    let version = MinecraftVersion::parse(mc_version)?;
    let modern_layout = version >= MinecraftVersion::parse("1.21.1")?;
    let game_dir_name = if modern_layout {
        "minecraft"
    } else {
        ".minecraft"
    };
    Ok(instance_dir.join(game_dir_name))
}

fn client_game_dir_candidates(instance_dir: &Path) -> Vec<PathBuf> {
    vec![
        instance_dir.join(".minecraft"),
        instance_dir.join("minecraft"),
    ]
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

#[cfg(test)]
mod tests {
    use super::resolve_client_mods_dir;

    #[test]
    fn client_mods_dir_uses_dot_minecraft_sentinel_when_present() {
        let temp = tempfile::tempdir().expect("tempdir should be created");
        let instance = temp.path().join("sfm-1.19.2");
        let game_dir = instance.join(".minecraft");
        std::fs::create_dir_all(&game_dir).expect("game dir should be created");
        std::fs::write(game_dir.join("options.txt"), "").expect("sentinel should be written");

        let mods_dir =
            resolve_client_mods_dir(&instance, "1.21.1").expect("mods dir should resolve");

        assert_eq!(mods_dir, instance.join(".minecraft").join("mods"));
    }

    #[test]
    fn client_mods_dir_uses_modern_sentinel_when_present() {
        let temp = tempfile::tempdir().expect("tempdir should be created");
        let instance = temp.path().join("sfm-1.21.1");
        let game_dir = instance.join("minecraft");
        std::fs::create_dir_all(&game_dir).expect("game dir should be created");
        std::fs::write(game_dir.join("options.txt"), "").expect("sentinel should be written");

        let mods_dir =
            resolve_client_mods_dir(&instance, "1.19.2").expect("mods dir should resolve");

        assert_eq!(mods_dir, instance.join("minecraft").join("mods"));
    }

    #[test]
    fn client_mods_dir_falls_back_by_version_before_launch() {
        let temp = tempfile::tempdir().expect("tempdir should be created");

        assert_eq!(
            resolve_client_mods_dir(&temp.path().join("sfm-1.19.2"), "1.19.2")
                .expect("old version should resolve"),
            temp.path()
                .join("sfm-1.19.2")
                .join(".minecraft")
                .join("mods")
        );
        assert_eq!(
            resolve_client_mods_dir(&temp.path().join("sfm-1.21.1"), "1.21.1")
                .expect("new version should resolve"),
            temp.path()
                .join("sfm-1.21.1")
                .join("minecraft")
                .join("mods")
        );
    }

    #[test]
    fn client_mods_dir_rejects_ambiguous_sentinels() {
        let temp = tempfile::tempdir().expect("tempdir should be created");
        let instance = temp.path().join("sfm-1.21.1");
        let old_game_dir = instance.join(".minecraft");
        let new_game_dir = instance.join("minecraft");
        std::fs::create_dir_all(&old_game_dir).expect("old game dir should be created");
        std::fs::create_dir_all(&new_game_dir).expect("new game dir should be created");
        std::fs::write(old_game_dir.join("options.txt"), "").expect("sentinel should be written");
        std::fs::write(new_game_dir.join("options.txt"), "").expect("sentinel should be written");

        let err = resolve_client_mods_dir(&instance, "1.21.1").unwrap_err();

        assert!(err.to_string().contains("Multiple Prism game directories"));
    }
}
