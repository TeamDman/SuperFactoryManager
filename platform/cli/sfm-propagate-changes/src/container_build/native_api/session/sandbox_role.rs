#[derive(Clone, Copy)]
pub(super) enum SandboxRole {
    Session,
    MemoryProbe,
}
impl SandboxRole {
    pub(super) const fn memory(self) -> u64 {
        match self {
            Self::Session => super::MEMORY,
            Self::MemoryProbe => 128 * 1024 * 1024,
        }
    }
    pub(super) const fn cpu_quota(self) -> u64 {
        match self {
            Self::Session => 200_000,
            Self::MemoryProbe => 12_500,
        }
    }
    pub(super) const fn pids(self) -> u64 {
        match self {
            Self::Session => 512,
            Self::MemoryProbe => 32,
        }
    }
}
