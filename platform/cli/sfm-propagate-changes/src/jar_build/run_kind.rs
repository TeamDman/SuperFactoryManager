#[derive(Clone, Copy, Debug)]
pub enum RunKind {
    Client,
    ClientSmoke,
    ClientPuppet,
    GameTestPreview,
    Server,
    Data,
    GameTestServer,
    Test,
}
