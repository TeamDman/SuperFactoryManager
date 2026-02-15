use serde::Serialize;

pub mod language_file;

pub trait StandaloneCommand {
    type Output: std::fmt::Debug + Serialize + Send + 'static;
    fn execute(self)
    -> impl std::future::Future<Output = eyre::Result<Self::Output>> + Send;
}
