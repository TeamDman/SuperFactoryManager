//! Preserve the existing Styx state schema until Styx supports the Teamy Facet baseline.
//!
//! The old reflection identity is confined to private wire types. Only owned
//! strings and a plain status cross this boundary; CLI/output types use Teamy Facet.

mod propagation_state;
mod state;
mod status;

pub use propagation_state::PropagationState;

/// Decode the existing `state.styx` representation.
pub fn decode(input: &str) -> eyre::Result<PropagationState> {
    let state: state::State = facet_styx::from_str(input)?;
    Ok(state.status.into())
}

/// Encode the existing `state.styx` representation.
pub fn encode(status: PropagationState) -> eyre::Result<String> {
    Ok(facet_styx::to_string(&state::State {
        status: status.into(),
    })?)
}
