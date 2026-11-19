use super::projection_catalog::ProjectionEntry;
use super::projection_name::ProjectionName;
use super::projection_pattern::ProjectionPattern;
use eyre::Result;
use eyre::WrapErr;
use facet::Facet;
use std::collections::BTreeMap;

/// Template parameters for each named projection in `projections.json`.
#[derive(Clone, Debug, Eq, Facet, PartialEq)]
#[facet(transparent)]
pub struct ProjectionDefinitions(pub BTreeMap<String, ProjectionEntry>);

impl ProjectionDefinitions {
    /// Match a native build or run glob against the configured projection names.
    pub(crate) fn matching_glob_names(&self, selector: &str) -> Result<Vec<String>> {
        let pattern = ProjectionPattern::parse(selector)?;
        Ok(self
            .matching_pattern_names(&pattern)?
            .into_iter()
            .map(str::to_owned)
            .collect())
    }

    pub(crate) fn matching_glob_projections(&self, selector: &str) -> Result<Vec<ProjectionName>> {
        let pattern = ProjectionPattern::parse(selector)?;
        self.matching_pattern_names(&pattern)?
            .into_iter()
            .map(ProjectionName::parse)
            .collect()
    }

    pub(crate) fn matching_names(&self, name_or_pattern: &str) -> Result<Vec<ProjectionName>> {
        // A projection's literal name may include brackets.
        if self.0.contains_key(name_or_pattern) {
            return Ok(vec![ProjectionName::parse(name_or_pattern)?]);
        }
        let pattern = ProjectionPattern::parse(name_or_pattern)
            .wrap_err_with(|| format!("invalid projection pattern {name_or_pattern:?}"))?;
        self.matching_pattern_names(&pattern)?
            .into_iter()
            .map(ProjectionName::parse)
            .collect()
    }

    fn matching_pattern_names(&self, pattern: &ProjectionPattern) -> Result<Vec<&str>> {
        pattern.matching_names(self.0.keys().map(String::as_str))
    }
}

#[cfg(test)]
mod tests {
    use super::ProjectionDefinitions;
    use super::ProjectionEntry;
    use crate::source_projection::projection_catalog::ProjectionEnvironment;

    #[test]
    fn native_globs_and_exact_checks_keep_their_distinct_bracket_meanings() {
        let definitions = ProjectionDefinitions(
            ["sfm-dev/mc-1.19.2", "sfm-dev/mc-1.19.[2]"]
                .into_iter()
                .map(|name| {
                    (
                        name.to_owned(),
                        ProjectionEntry {
                            minecraft_version: "1.19.2".to_owned(),
                            environment: ProjectionEnvironment::Dev,
                            features: Vec::new(),
                        },
                    )
                })
                .collect(),
        );

        let bracket_name = "sfm-dev/mc-1.19.[2]";
        assert_eq!(
            definitions.matching_glob_names(bracket_name).unwrap(),
            ["sfm-dev/mc-1.19.2"]
        );
        assert_eq!(
            definitions.matching_glob_projections(bracket_name).unwrap()[0].as_str(),
            "sfm-dev/mc-1.19.2"
        );
        assert_eq!(
            definitions.matching_names(bracket_name).unwrap()[0].as_str(),
            bracket_name
        );
    }
}
