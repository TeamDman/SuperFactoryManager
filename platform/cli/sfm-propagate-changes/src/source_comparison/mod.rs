//! One deterministic model shared by explicit adapters and the existing HTML viewer.
mod adapters;
mod compare;
pub mod difference;
pub mod file;
pub mod file_reference_query;
pub mod identity;
pub mod input;
mod prepare;
pub mod report;
pub mod request;
pub mod snapshot;
pub use adapters::snapshot;
pub use compare::compare;
pub use prepare::prepare;
pub mod preparation_receipt;
#[cfg(test)]
mod tests;
