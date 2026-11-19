use super::projection_name::ProjectionName;
use facet::Facet;

/// Result of checking the generated files of one projection.
#[derive(Debug, Facet)]
#[facet(tag = "status", rename_all = "snake_case")]
#[expect(dead_code, reason = "the CLI reads these fields through Facet")]
#[repr(u8)]
pub(crate) enum ProjectionCheckResult {
    Current {
        projection: ProjectionName,
    },
    Failed {
        projection: ProjectionName,
        error: String,
    },
}
