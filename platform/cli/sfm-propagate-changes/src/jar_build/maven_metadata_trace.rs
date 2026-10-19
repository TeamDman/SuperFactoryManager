use super::consumed_maven_pom::ConsumedMavenPom;
use super::maven_metadata_catalog::MavenMetadataCatalog;
use super::maven_metadata_pin::MavenMetadataPin;
use std::collections::BTreeMap;
#[derive(Debug, Default)]
pub(super) struct MavenMetadataTrace {
    pub entries: BTreeMap<String, ConsumedMavenPom>,
    repository: Option<String>,
    reads: usize,
    bytes: usize,
}
impl MavenMetadataTrace {
    pub fn visit(&mut self) -> eyre::Result<()> {
        self.reads += 1;
        eyre::ensure!(
            self.reads <= 1024,
            "test metadata snapshot read budget exceeded"
        );
        Ok(())
    }
    pub fn count_bytes(&mut self, size: usize) -> eyre::Result<()> {
        self.bytes = self
            .bytes
            .checked_add(size)
            .ok_or_else(|| eyre::eyre!("metadata byte count overflow"))?;
        eyre::ensure!(
            self.bytes <= 16 * 1024 * 1024,
            "test metadata snapshot byte budget exceeded"
        );
        Ok(())
    }
    pub fn capture(
        &mut self,
        repository: &str,
        pin: &MavenMetadataPin,
        raw: &[u8],
    ) -> eyre::Result<()> {
        chrono::DateTime::parse_from_rfc3339(&pin.retrieved_utc).map_err(|error| {
            eyre::eyre!(
                "invalid metadata retrieval timestamp for {}: {error}",
                pin.coordinate
            )
        })?;
        self.count_bytes(raw.len())?;
        if let Some(prior) = &self.repository {
            eyre::ensure!(
                prior == repository,
                "metadata repository changed during snapshot"
            );
        } else {
            self.repository = Some(repository.into());
        }
        if let Some(prior) = self.entries.get(&pin.coordinate) {
            eyre::ensure!(
                prior.pin == *pin && prior.raw == raw,
                "consumed metadata changed during snapshot: {}",
                pin.coordinate
            );
        } else {
            eyre::ensure!(
                self.entries.len() < 128,
                "test metadata snapshot unique model budget exceeded"
            );
            self.entries.insert(
                pin.coordinate.clone(),
                ConsumedMavenPom {
                    pin: pin.clone(),
                    raw: raw.into(),
                },
            );
        }
        Ok(())
    }
    pub fn catalog(&self) -> eyre::Result<Option<Vec<u8>>> {
        let Some(repository) = &self.repository else {
            return Ok(None);
        };
        let catalog = MavenMetadataCatalog {
            schema: "sfm:maven_metadata_provision@1".into(),
            repository: repository.clone(),
            artifacts: self.entries.values().map(|p| p.pin.clone()).collect(),
        };
        Ok(Some(facet_json::to_string(&catalog)?.into_bytes()))
    }
}
