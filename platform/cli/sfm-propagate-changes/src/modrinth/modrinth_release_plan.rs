use std::path::PathBuf;

#[derive(Debug, Clone)]
pub struct ModrinthReleasePlan {
    pub jar_path: PathBuf,
    pub mc_version: String,
    pub display_name: String,
    pub version_number: String,
    pub game_versions: Vec<String>,
    pub loaders: Vec<String>,
}
