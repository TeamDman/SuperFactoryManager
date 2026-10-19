use crate::status::Status;
use facet::Facet;

#[derive(Facet, Debug, Clone, Default)]
pub(crate) struct State {
    pub(crate) status: Status,
}
