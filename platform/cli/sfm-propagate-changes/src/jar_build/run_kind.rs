#[derive(Clone, Copy, Debug)]
pub enum RunKind {
    Client,
    Server,
    Data,
    GameTestServer,
}
