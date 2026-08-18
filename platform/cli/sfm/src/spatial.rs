use crate::protocol::validate_action_tokens;
use facet::Facet;

pub const SPATIAL_COVERAGE_RUN_ACTION: &str = "sfm:spatial/coverage/run";
pub const SPATIAL_COVERAGE_RUN_OUTPUT_SCHEMA: &str = "sfm.spatial-coverage-run/1";
pub const SPATIAL_COVERAGE_RUN_OUTPUT_SCHEMA_VERSION: u16 = 1;

#[derive(Clone, Copy, Debug, PartialEq, Eq, Facet)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub enum SpatialCoverageScope {
    Document,
    Workspace,
}

impl SpatialCoverageScope {
    #[must_use]
    pub const fn action_token(self) -> &'static str {
        match self {
            Self::Document => "document",
            Self::Workspace => "workspace",
        }
    }
}

/// Canonical arguments supplied to `sfm spatial coverage run`.
#[derive(Clone, Debug, PartialEq, Eq, Facet)]
pub struct SpatialCoverageRunInput {
    pub scope: SpatialCoverageScope,
    pub selector: String,
    pub profile: String,
    pub layout_matrix: String,
    pub seed: i64,
    pub budget: i64,
    pub artifact_destination: String,
}

impl SpatialCoverageRunInput {
    /// Validate the frozen v1 coverage-request argument contract.
    ///
    /// Resolver-specific selector and artifact-path authority checks remain
    /// game-owned. This validates the transport-independent shape before the
    /// CLI performs instance discovery or allocates a Vox connection.
    ///
    /// # Errors
    ///
    /// Returns an error when an argument violates the frozen v1 request
    /// contract.
    pub fn validate(&self) -> eyre::Result<()> {
        require_text(&self.selector, "selector")?;
        require_namespaced(&self.profile, "profile")?;
        require_namespaced(&self.layout_matrix, "layout matrix")?;
        eyre::ensure!(self.seed >= 0, "seed must be non-negative");
        eyre::ensure!(self.budget > 0, "budget must be positive");
        require_text(&self.artifact_destination, "artifact destination")?;
        Ok(())
    }

    /// Build the exact registered client-action tokens in deterministic order.
    ///
    /// # Errors
    ///
    /// Returns an error when the input is invalid or the resulting action
    /// exceeds the generic live-game transport bounds.
    pub fn action_tokens(&self) -> eyre::Result<Vec<String>> {
        self.validate()?;
        let tokens = vec![
            SPATIAL_COVERAGE_RUN_ACTION.to_owned(),
            self.scope.action_token().to_owned(),
            self.selector.clone(),
            self.profile.clone(),
            self.layout_matrix.clone(),
            self.seed.to_string(),
            self.budget.to_string(),
            self.artifact_destination.clone(),
        ];
        validate_action_tokens(&tokens)?;
        Ok(tokens)
    }
}

fn require_text(value: &str, label: &str) -> eyre::Result<()> {
    eyre::ensure!(!value.trim().is_empty(), "{label} must not be blank");
    eyre::ensure!(!value.contains('\0'), "{label} contains NUL");
    Ok(())
}

fn require_namespaced(value: &str, label: &str) -> eyre::Result<()> {
    require_text(value, label)?;
    let colon = value.find(':');
    eyre::ensure!(
        colon.is_some_and(|index| index > 0 && index + 1 < value.len()),
        "{label} must be namespaced"
    );
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn input() -> SpatialCoverageRunInput {
        SpatialCoverageRunInput {
            scope: SpatialCoverageScope::Document,
            selector: "focused".to_owned(),
            profile: "sfm:strict_java_navigation".to_owned(),
            layout_matrix: "sfm:auto_1_through_8".to_owned(),
            seed: 0,
            budget: 100_000,
            artifact_destination: "auto".to_owned(),
        }
    }

    #[test]
    fn builds_exact_canonical_action_tokens_in_frozen_order() {
        assert_eq!(
            input().action_tokens().expect("valid action tokens"),
            [
                "sfm:spatial/coverage/run",
                "document",
                "focused",
                "sfm:strict_java_navigation",
                "sfm:auto_1_through_8",
                "0",
                "100000",
                "auto",
            ]
        );
    }

    #[test]
    fn workspace_scope_has_an_exact_distinct_token() {
        let mut input = input();
        input.scope = SpatialCoverageScope::Workspace;
        assert_eq!(input.action_tokens().expect("valid tokens")[1], "workspace");
    }

    #[test]
    fn rejects_invalid_frozen_arguments_before_transport() {
        let mut candidate = input();
        candidate.selector = "  ".to_owned();
        assert_invalid(&candidate, "selector must not be blank");

        let mut candidate = input();
        candidate.profile = "strict".to_owned();
        assert_invalid(&candidate, "profile must be namespaced");

        let mut candidate = input();
        candidate.layout_matrix = "auto".to_owned();
        assert_invalid(&candidate, "layout matrix must be namespaced");

        let mut candidate = input();
        candidate.seed = -1;
        assert_invalid(&candidate, "seed must be non-negative");

        let mut candidate = input();
        candidate.budget = 0;
        assert_invalid(&candidate, "budget must be positive");

        let mut candidate = input();
        candidate.artifact_destination.clear();
        assert_invalid(&candidate, "artifact destination must not be blank");

        let mut candidate = input();
        candidate.selector = "focused\0other".to_owned();
        assert_invalid(&candidate, "selector contains NUL");
    }

    fn assert_invalid(input: &SpatialCoverageRunInput, expected: &str) {
        let failure = input.action_tokens().expect_err("invalid input");
        assert!(
            format!("{failure:#}").contains(expected),
            "expected `{expected}`, got `{failure:#}`"
        );
    }
}
