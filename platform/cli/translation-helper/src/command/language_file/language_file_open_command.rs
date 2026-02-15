use crate::command::StandaloneCommand;
use crate::config::get_root_dir;
use crate::domain_object::language_file_path::LanguageFilePath;
use tracing::info;

pub struct LanguageFileOpenCommand;
impl StandaloneCommand for LanguageFileOpenCommand {
    type Output = ();

    fn execute(self) -> impl std::future::Future<Output = eyre::Result<Self::Output>> + Send {
        async move {
            let paths = LanguageFilePath::discover_in_root_dir(&get_root_dir().await?)?;
            for (path, _game_version) in paths {
                info!("Opening language file: {}", path.display());
                if let Err(e) = open::that(path.as_path()) {
                    eyre::bail!(
                        "Failed to open language file: {}. Error: {}",
                        path.display(),
                        e
                    );
                }
            }
            Ok(())
        }
    }
}
