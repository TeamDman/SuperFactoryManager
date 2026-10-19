use facet::Facet;
#[derive(Debug, Facet)]
pub(super) struct ApiStore {
    #[facet(rename = "graphRootAllocated")]
    pub allocated: u64,
    #[facet(rename = "graphRootUsed")]
    pub used: u64,
}
impl ApiStore {
    pub(super) fn check(&self) -> eyre::Result<()> {
        let free = self.allocated.saturating_sub(self.used);
        eyre::ensure!(
            free >= 20 * 1024 * 1024 * 1024,
            "Podman disk exhausted or below20GiB reserve ({free} bytes); build connection closed; engine completion unconfirmed; no pruning attempted"
        );
        Ok(())
    }
}
