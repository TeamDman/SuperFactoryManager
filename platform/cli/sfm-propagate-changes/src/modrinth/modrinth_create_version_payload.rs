use crate::modrinth::ModrinthDependencyPayload;
use facet::Facet;

#[derive(Facet, Debug, Clone)]
pub struct ModrinthCreateVersionPayload {
    pub name: String,
    #[facet(rename = "version_number")]
    pub version_number: String,
    pub changelog: String,
    pub dependencies: Vec<ModrinthDependencyPayload>,
    #[facet(rename = "game_versions")]
    pub game_versions: Vec<String>,
    #[facet(rename = "version_type")]
    pub version_type: String,
    pub loaders: Vec<String>,
    pub featured: bool,
    #[facet(rename = "project_id")]
    pub project_id: String,
    #[facet(rename = "file_parts")]
    pub file_parts: Vec<String>,
}

impl ModrinthCreateVersionPayload {
    /// Construct the release metadata shared by the legacy uploader and
    /// projection-native read-only provider preflight. This does not choose a
    /// JAR, acquire credentials or perform an upload.
    pub(crate) fn for_release(
        display_name: &str,
        version_number: &str,
        changelog: &str,
        game_versions: &[String],
        loaders: &[String],
        project_id: &str,
    ) -> Self {
        Self {
            name: display_name.to_owned(),
            version_number: version_number.to_owned(),
            changelog: changelog.to_owned(),
            dependencies: Vec::new(),
            game_versions: game_versions.to_vec(),
            version_type: "release".to_owned(),
            loaders: loaders.to_vec(),
            featured: false,
            project_id: project_id.to_owned(),
            file_parts: vec!["file".to_owned()],
        }
    }
}

#[cfg(test)]
mod tests {
    use super::ModrinthCreateVersionPayload;

    #[test]
    fn release_payload_keeps_the_existing_upload_wire_contract() {
        let payload = ModrinthCreateVersionPayload::for_release(
            "Super Factory Manager MC1.20.1 v4.34.0",
            "4.34.0",
            "Reviewed notes\n",
            &["1.20.1".to_owned()],
            &["forge".to_owned(), "neoforge".to_owned()],
            "reviewed-project",
        );
        let json = facet_json::to_string(&payload).unwrap();
        assert_eq!(
            json,
            r#"{"name":"Super Factory Manager MC1.20.1 v4.34.0","version_number":"4.34.0","changelog":"Reviewed notes\n","dependencies":[],"game_versions":["1.20.1"],"version_type":"release","loaders":["forge","neoforge"],"featured":false,"project_id":"reviewed-project","file_parts":["file"]}"#
        );
    }
}
