use super::role_input::RoleInput;
use super::source_lock_reference::SourceLockReference;
use super::supplement::Supplement;
use facet::Facet;
#[derive(Clone, Debug, Facet)]
pub(super) struct TargetRecipe {
    pub target: String,
    pub minecraft_version: String,
    pub recipe_id: String,
    pub source_lock: SourceLockReference,
    pub role_input_hashes: Vec<RoleInput>,
    pub source_excludes: Vec<super::exclusion_witness::ExclusionWitness>,
    pub released_gradle_supplements: Vec<Supplement>,
}
