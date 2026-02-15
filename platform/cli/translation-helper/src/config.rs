use eye_config::persistable_state::PersistableState;
use eye_config::persistence_key::PersistenceKey;
use serde::Deserialize;
use serde::Serialize;
use std::path::PathBuf;

#[derive(Debug, Serialize, Deserialize, Clone, PartialEq, Default)]
pub struct TranslationHelperConfig {
    pub root_dir: Option<PathBuf>,
}

#[eye_config::async_trait::async_trait]
impl PersistableState for TranslationHelperConfig {
    async fn key() -> eyre::Result<PersistenceKey> {
        Ok(PersistenceKey::new("translation_helper", "config.json"))
    }
}

pub async fn get_root_dir() -> eyre::Result<PathBuf> {
    let config = TranslationHelperConfig::load().await?;
    let Some(root_dir) = config.root_dir else {
        return Err(eyre::eyre!("Root directory not set"));
    };
    if !root_dir.exists() {
        return Err(eyre::eyre!(
            "Root directory does not exist: {}",
            root_dir.display()
        ));
    }
    Ok(root_dir)
}
