use facet::Facet;
use std::path::PathBuf;

#[derive(Clone, Debug, Facet)]
#[facet(transparent)]
pub(crate) struct JsonPath(String);

impl TryFrom<JsonPath> for PathBuf {
    type Error = String;

    fn try_from(value: JsonPath) -> Result<Self, Self::Error> {
        // Lockfiles written on Windows must still resolve as path components on Unix.
        Ok(PathBuf::from(value.0.replace('\\', "/")))
    }
}

impl TryFrom<&PathBuf> for JsonPath {
    type Error = String;

    fn try_from(value: &PathBuf) -> Result<Self, Self::Error> {
        value
            .to_str()
            .map(|path| JsonPath(path.replace('\\', "/")))
            .ok_or_else(|| format!("Path is not valid Unicode: {}", value.display()))
    }
}

#[derive(Clone, Debug, Facet)]
#[facet(transparent)]
pub(crate) struct JsonOptionalPath(Option<String>);

impl TryFrom<JsonOptionalPath> for Option<PathBuf> {
    type Error = String;

    fn try_from(value: JsonOptionalPath) -> Result<Self, Self::Error> {
        value
            .0
            .map(|path| PathBuf::try_from(JsonPath(path)))
            .transpose()
    }
}

impl TryFrom<&Option<PathBuf>> for JsonOptionalPath {
    type Error = String;

    fn try_from(value: &Option<PathBuf>) -> Result<Self, Self::Error> {
        value
            .as_ref()
            .map(JsonPath::try_from)
            .transpose()
            .map(|path| JsonOptionalPath(path.map(|path| path.0)))
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::path::Path;

    #[test]
    fn windows_cache_path_resolves_beneath_portable_cache_root() {
        let input =
            r#""$sfm-cache\\maven\\org\\facet\\vox-java\\0.10.0-rc.5\\vox-java-0.10.0-rc.5.jar""#;
        let encoded: JsonPath = facet_json::from_str(input).unwrap();
        let path = PathBuf::try_from(encoded).unwrap();
        let relative = path.strip_prefix("$sfm-cache").unwrap();
        assert_eq!(
            Path::new("cache").join(relative),
            Path::new("cache/maven/org/facet/vox-java/0.10.0-rc.5/vox-java-0.10.0-rc.5.jar")
        );
    }

    #[test]
    fn source_build_output_resolves_under_checkout_with_either_separator() {
        for input in [
            r"vox\java\target\vox-java-0.10.0-rc.5.jar",
            "vox/java/target/vox-java-0.10.0-rc.5.jar",
        ] {
            let path = PathBuf::try_from(JsonPath(input.to_string())).unwrap();
            assert_eq!(
                Path::new("checkout").join(path),
                Path::new("checkout/vox/java/target/vox-java-0.10.0-rc.5.jar")
            );
        }
    }

    #[test]
    fn serialized_paths_use_forward_slashes() {
        let path = PathBuf::from(r"$sfm-cache\maven\example.jar");
        let encoded = JsonPath::try_from(&path).unwrap();
        assert_eq!(encoded.0, "$sfm-cache/maven/example.jar");
    }

    #[test]
    fn optional_paths_share_portable_path_handling() {
        let encoded = JsonOptionalPath(Some(r"run\screenshots\capture.png".to_string()));
        let path = Option::<PathBuf>::try_from(encoded).unwrap();
        assert_eq!(path, Some(PathBuf::from("run/screenshots/capture.png")));
        let encoded =
            JsonOptionalPath::try_from(&Some(PathBuf::from(r"run\screenshots\capture.png")))
                .unwrap();
        assert_eq!(encoded.0.as_deref(), Some("run/screenshots/capture.png"));
    }

    #[test]
    fn absent_optional_path_round_trips() {
        let encoded: JsonOptionalPath = facet_json::from_str("null").unwrap();
        let path = Option::<PathBuf>::try_from(encoded).unwrap();
        assert!(path.is_none());
        assert!(JsonOptionalPath::try_from(&path).unwrap().0.is_none());
    }
}
