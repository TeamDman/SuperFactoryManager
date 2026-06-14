#[derive(Clone, Copy, Debug)]
pub enum RunKind {
    Client,
    ClientSmoke,
    ClientPuppet,
    Server,
    Data,
    GameTestServer,
}
