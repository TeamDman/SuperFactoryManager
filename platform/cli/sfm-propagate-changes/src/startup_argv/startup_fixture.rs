use facet::Facet;
use figue as args;

#[derive(Debug, Facet)]
pub(super) struct StartupFixture {
    #[facet(args::named)]
    pub request: Option<String>,
    #[facet(args::named)]
    pub token: Option<String>,
    #[facet(args::named)]
    pub password: Option<String>,
    #[facet(args::named)]
    pub message: Option<String>,
    #[facet(args::named, default)]
    pub debug: bool,
}
