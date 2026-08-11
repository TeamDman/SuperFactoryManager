use crate::curseforge::CurseforgeApiSecret;
use crate::curseforge::CurseforgeHttpClient;
use crate::curseforge::CurseforgeModLoader;
use crate::curseforge::CurseforgeProjectFileItem;
use crate::curseforge::CurseforgeProjectId;
use crate::curseforge::file_supported_loaders;
use crate::curseforge::list_project_files_for_version;
use crate::terminal_output::stdout_line;
use facet::Facet;
use figue as args;

/// List `CurseForge` files for one selected project without changing an SFM lockfile.
#[derive(Facet, Debug)]
pub struct CurseforgeModFilesArgs {
    /// Exact `CurseForge` project ID chosen from `curseforge mod search`.
    #[facet(args::positional)]
    pub project: u64,
    /// Exact Minecraft version to match.
    #[facet(args::named)]
    pub minecraft: String,
    /// Mod loader to match.
    #[facet(args::named)]
    pub loader: CurseforgeModLoader,
    /// `CurseForge` Core API key; if omitted, configured secret sources are tried.
    #[facet(default, args::named)]
    pub api_key: Option<String>,
    /// Legacy API token fallback; prefer `--api-key` for Core API queries.
    #[facet(default, args::named)]
    pub token: Option<String>,
    /// 1Password secret reference used when no API key is configured.
    #[facet(default, args::named)]
    pub op_secret: Option<String>,
}

impl CurseforgeModFilesArgs {
    /// # Errors
    ///
    /// Returns an error when credentials cannot be resolved or the Core API query fails.
    pub fn invoke(self) -> eyre::Result<()> {
        let (key, _credential_source) =
            CurseforgeApiSecret::resolve_core(self.api_key, self.token, self.op_secret)?;
        let client = CurseforgeHttpClient::new_core_api(&key)?;
        let files = list_project_files_for_version(
            &client,
            CurseforgeProjectId(self.project),
            &self.minecraft,
            self.loader,
        )?;
        print_files(&files)
    }
}

fn print_files(files: &[CurseforgeProjectFileItem]) -> eyre::Result<()> {
    stdout_line("id\tfile-name\tdisplay-name\trelease-type\tpublished\tversions\tloaders")?;
    for file in files {
        let loaders = file_supported_loaders(file)
            .into_iter()
            .map(CurseforgeModLoader::label)
            .collect::<Vec<_>>();
        stdout_line(format!(
            "{}\t{}\t{}\t{}\t{}\t{}\t{}",
            file.id,
            file.file_name.as_deref().unwrap_or("-"),
            file.display_name.as_deref().unwrap_or("-"),
            release_type_label(file.release_type),
            file.file_date.as_deref().unwrap_or("-"),
            file.game_versions.join(","),
            if loaders.is_empty() {
                "-".to_owned()
            } else {
                loaders.join(",")
            }
        ))?;
    }
    Ok(())
}

fn release_type_label(release_type: Option<u32>) -> &'static str {
    match release_type {
        Some(1) => "release",
        Some(2) => "beta",
        Some(3) => "alpha",
        _ => "unknown",
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn release_type_labels_are_not_recommendations() {
        assert_eq!(release_type_label(Some(1)), "release");
        assert_eq!(release_type_label(Some(2)), "beta");
        assert_eq!(release_type_label(Some(3)), "alpha");
        assert_eq!(release_type_label(None), "unknown");
    }
}
