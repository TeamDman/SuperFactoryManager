use super::SourceProjectionCheckArgs;
use facet::Facet;

#[derive(Debug, Facet)]
#[repr(u8)]
pub enum SourceProjectionCommand {
    /// Check generated files for one named projection or all projections matching a glob.
    Check(SourceProjectionCheckArgs),
}
