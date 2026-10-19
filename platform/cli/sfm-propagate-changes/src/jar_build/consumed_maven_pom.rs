use super::maven_metadata_pin::MavenMetadataPin;
#[derive(Debug)]
pub(super) struct ConsumedMavenPom {
    pub pin: MavenMetadataPin,
    pub raw: Vec<u8>,
}
