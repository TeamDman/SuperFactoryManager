use crate::domain_object::repo_path::RepoPath;
use crate::domain_object::source_set_path::SourceSetPath;
use eyre::Context;
use serde::Deserialize;
use serde::Serialize;

#[derive(
    Debug, Serialize, Deserialize, strum::Display, strum::EnumString, Clone, PartialEq, Eq,
)]
#[strum(serialize_all = "lowercase")]
#[serde(rename_all = "lowercase")]
pub enum SourceSet {
    Main,
    Generated,
    Datagen,
    Test,
    GameTest,
}
impl SourceSet {
    pub fn path_in_repo(&self, mut path: RepoPath) -> SourceSetPath {
        path.push("src");
        path.push(self.to_string());
        SourceSetPath::new(path)
    }
    pub fn list_in_repo(path: RepoPath) -> eyre::Result<Vec<SourceSetPath>> {
        let src_dir = path.join("src");
        let mut sets = Vec::new();
        let entries = std::fs::read_dir(&src_dir).wrap_err_with(|| format!("Failed to read dir: {}", src_dir.display()))?;
        for entry in entries {
            let entry = entry?;
            if entry.file_type()?.is_dir() {
                let name = entry.file_name();
                let name = name.to_string_lossy();
                let source_set = name.parse::<SourceSet>()?;
                sets.push(source_set.path_in_repo(path.clone()));
            }
        }
        Ok(sets)
    }
}
