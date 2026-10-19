use facet::Facet;
#[derive(Debug, Facet)]
pub(super) struct MemoryCapacity {
    pub schema: String,
    pub total: u64,
    pub available: u64,
    pub uid: u64,
    pub no_new_privileges: u64,
    pub effective_caps: String,
    pub seccomp: u64,
}
impl MemoryCapacity {
    pub(super) fn check(&self, total: u64) -> eyre::Result<()> {
        eyre::ensure!(
            self.schema == "sfm:memory_probe@1"
                && self.total == total
                && self.available <= total
                && self.uid == 10001
                && self.no_new_privileges == 1
                && self.effective_caps == "0000000000000000"
                && self.seccomp == 2,
            "live memory probe provenance/security/host total mismatch"
        );
        eyre::ensure!(
            self.available >= super::MEMORY + 2 * 1024 * 1024 * 1024,
            "insufficient live Linux MemAvailable for 8GiB session plus 2GiB reserve"
        );
        Ok(())
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    fn sample() -> MemoryCapacity {
        MemoryCapacity {
            schema: "sfm:memory_probe@1".into(),
            total: 32 * 1024 * 1024 * 1024,
            available: 12 * 1024 * 1024 * 1024,
            uid: 10001,
            no_new_privileges: 1,
            effective_caps: "0000000000000000".into(),
            seccomp: 2,
        }
    }
    #[test]
    fn live_capacity_requires_reserve_and_exact_host_security() {
        let mut value = sample();
        assert!(value.check(value.total).is_ok());
        value.available = super::super::MEMORY + 2 * 1024 * 1024 * 1024 - 1;
        assert!(value.check(value.total).is_err());
        let mut value = sample();
        value.available = value.total + 1;
        assert!(value.check(value.total).is_err());
        let value = sample();
        assert!(value.check(value.total - 1).is_err());
        let mut value = sample();
        value.uid = 0;
        assert!(value.check(value.total).is_err());
        let mut value = sample();
        value.no_new_privileges = 0;
        assert!(value.check(value.total).is_err());
        let mut value = sample();
        value.effective_caps = "0000000000000001".into();
        assert!(value.check(value.total).is_err());
        let mut value = sample();
        value.seccomp = 0;
        assert!(value.check(value.total).is_err());
        assert!(facet_json::from_str::<MemoryCapacity>(r#"{"total":1}"#).is_err());
    }
}
