#[derive(Clone, Copy, Debug, Default)]
pub enum RunTestAction {
    #[default]
    Run,
    List,
}

#[derive(Clone, Debug, Default)]
pub struct RunTestOptions {
    pub action: RunTestAction,
    pub filter: Option<String>,
    pub no_capture: bool,
}
