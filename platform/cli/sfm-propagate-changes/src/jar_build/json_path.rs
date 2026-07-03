use facet::Facet;
use std::path::PathBuf;

#[derive(Clone, Debug, Facet)]
#[facet(transparent)]
pub(crate) struct JsonPath(String);

impl TryFrom<JsonPath> for PathBuf {
    type Error = String;

    fn try_from(value: JsonPath) -> Result<Self, Self::Error> {
        Ok(PathBuf::from(value.0))
    }
}

impl TryFrom<&PathBuf> for JsonPath {
    type Error = String;

    fn try_from(value: &PathBuf) -> Result<Self, Self::Error> {
        value
            .to_str()
            .map(|path| JsonPath(path.to_string()))
            .ok_or_else(|| format!("Path is not valid Unicode: {}", value.display()))
    }
}

#[derive(Clone, Debug, Facet)]
#[facet(transparent)]
pub(crate) struct JsonOptionalPath(Option<String>);

impl TryFrom<JsonOptionalPath> for Option<PathBuf> {
    type Error = String;

    fn try_from(value: JsonOptionalPath) -> Result<Self, Self::Error> {
        Ok(value.0.map(PathBuf::from))
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
