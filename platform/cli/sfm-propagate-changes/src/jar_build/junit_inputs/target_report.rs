use super::test_pin::TestPin;
use super::test_root::TestRoot;
use facet::Facet;
#[derive(Clone, Debug, Facet)]
pub(crate) struct TargetReport {
    pub projection_key: String,
    pub target_id: String,
    pub minecraft_version: String,
    pub released_recipe_id: String,
    pub raw_source_lock_sha256: String,
    pub ordered_test_roots: Vec<TestRoot>,
    pub role_selection_identity: String,
    pub role_bindings: Vec<super::role_binding::RoleBinding>,
    pub selected_pin_identity: String,
    pub selected_pins: Vec<TestPin>,
    pub production_base_witnesses: Vec<TestPin>,
    pub classpath_bindings: super::classpath_bindings::ClasspathBindings,
    pub closure_complete: bool,
}
