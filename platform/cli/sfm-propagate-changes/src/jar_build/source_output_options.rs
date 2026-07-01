use super::BuildOptions;
use super::SourceOutputLayout;

#[derive(Clone, Debug)]
pub struct SourceOutputOptions {
    pub build: BuildOptions,
    pub layout: SourceOutputLayout,
}
