use super::target_recipe::TargetRecipe;
use facet::Facet;
#[derive(Debug, Facet)]
pub(super) struct Configuration {
    pub targets: Vec<TargetRecipe>,
}
