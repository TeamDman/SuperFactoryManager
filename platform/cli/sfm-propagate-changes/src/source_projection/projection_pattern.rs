use eyre::Result;
use eyre::bail;
use glob::Pattern;

/// A glob over complete source projection names, preserving their input order.
#[derive(Debug)]
pub(crate) struct ProjectionPattern(Pattern);

impl ProjectionPattern {
    /// Parse using the same glob syntax as native build and run commands.
    ///
    /// # Errors
    /// Rejects malformed glob syntax.
    pub(crate) fn parse(value: &str) -> Result<Self> {
        Ok(Self(Pattern::new(value)?))
    }

    /// Return matching names, or explain the available names when none match.
    ///
    /// # Errors
    /// Returns an error when no supplied name matches, including an empty input.
    pub(crate) fn matching_names<'a>(
        &self,
        names: impl Iterator<Item = &'a str>,
    ) -> Result<Vec<&'a str>> {
        let candidates = names.collect::<Vec<_>>();
        let matched = candidates
            .iter()
            .copied()
            .filter(|name| self.0.matches(name))
            .collect::<Vec<_>>();
        if matched.is_empty() {
            let available = if candidates.is_empty() {
                "  (none defined)".to_owned()
            } else {
                candidates
                    .iter()
                    .map(|name| format!("  {name}"))
                    .collect::<Vec<_>>()
                    .join("\n")
            };
            let pattern = self.0.as_str();
            bail!("no projections match {pattern:?}\n\nAvailable projections:\n{available}");
        }
        Ok(matched)
    }
}

#[cfg(test)]
mod tests {
    use super::ProjectionPattern;

    const NAMES: [&str; 3] = [
        "sfm-dev/mc-1.20.4",
        "sfm-dev/mc-1.20.1",
        "sfm-dev/mc-1.20.10",
    ];

    #[test]
    fn literal_and_star_match_complete_names_in_input_order() {
        assert_eq!(
            ProjectionPattern::parse(NAMES[1])
                .unwrap()
                .matching_names(NAMES.into_iter())
                .unwrap(),
            [NAMES[1]]
        );
        for value in ["*", "sfm-dev/*"] {
            assert_eq!(
                ProjectionPattern::parse(value)
                    .unwrap()
                    .matching_names(NAMES.into_iter())
                    .unwrap(),
                NAMES
            );
        }
    }

    #[test]
    fn patterns_address_projection_names_without_branch_or_path_fallback() {
        for value in ["1.20.1", "../*"] {
            assert!(
                ProjectionPattern::parse(value)
                    .unwrap()
                    .matching_names(NAMES.into_iter())
                    .is_err()
            );
        }
    }

    #[test]
    fn no_match_lists_every_available_name_in_input_order() {
        let available = NAMES
            .iter()
            .map(|name| format!("  {name}"))
            .collect::<Vec<_>>()
            .join("\n");
        assert_eq!(
            ProjectionPattern::parse("missing")
                .unwrap()
                .matching_names(NAMES.into_iter())
                .unwrap_err()
                .to_string(),
            format!("no projections match \"missing\"\n\nAvailable projections:\n{available}")
        );
        assert_eq!(
            ProjectionPattern::parse("*")
                .unwrap()
                .matching_names(std::iter::empty())
                .unwrap_err()
                .to_string(),
            "no projections match \"*\"\n\nAvailable projections:\n  (none defined)"
        );
    }
}
