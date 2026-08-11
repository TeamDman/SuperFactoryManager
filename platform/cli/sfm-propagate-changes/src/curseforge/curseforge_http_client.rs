use crate::curseforge::CurseforgeApiSecret;
use eyre::Context;
use reqwest::blocking::Client;
use reqwest::header::HeaderMap;
use reqwest::header::HeaderValue;
use reqwest::header::USER_AGENT;
use std::ops::Deref;
use std::ops::DerefMut;
use std::time::Duration;

const CURSEFORGE_USER_AGENT: &str = "sfm-propagate-changes/curseforge";

#[derive(Clone, Debug)]
#[repr(transparent)]
pub struct CurseforgeHttpClient(pub Client);

impl CurseforgeHttpClient {
    /// Build a `CurseForge` client that authenticates with the legacy API token header.
    ///
    /// # Errors
    ///
    /// Returns an error when the token cannot be encoded as an HTTP header or the client
    /// cannot be constructed.
    pub fn new(token: &CurseforgeApiSecret) -> eyre::Result<Self> {
        Self::build(
            "X-Api-Token",
            token.as_str(),
            "Invalid API token for header",
            "Failed to build CurseForge HTTP client",
        )
    }

    /// Build a `CurseForge` client that authenticates with the Core API key header.
    ///
    /// # Errors
    ///
    /// Returns an error when the API key cannot be encoded as an HTTP header or the client
    /// cannot be constructed.
    pub fn new_core_api(api_key: &CurseforgeApiSecret) -> eyre::Result<Self> {
        Self::build(
            "x-api-key",
            api_key.as_str(),
            "Invalid Core API key for header",
            "Failed to build CurseForge Core API HTTP client",
        )
    }

    fn build(
        header_name: &'static str,
        header_value: &str,
        invalid_header_message: &'static str,
        build_message: &'static str,
    ) -> eyre::Result<Self> {
        let mut headers = HeaderMap::new();
        headers.insert(
            header_name,
            HeaderValue::from_str(header_value).wrap_err(invalid_header_message)?,
        );
        headers.insert(USER_AGENT, HeaderValue::from_static(CURSEFORGE_USER_AGENT));

        let client = Client::builder()
            .default_headers(headers)
            .timeout(Duration::from_mins(2))
            .build()
            .wrap_err(build_message)?;

        Ok(Self(client))
    }
}

impl Deref for CurseforgeHttpClient {
    type Target = Client;

    fn deref(&self) -> &Self::Target {
        &self.0
    }
}

impl DerefMut for CurseforgeHttpClient {
    fn deref_mut(&mut self) -> &mut Self::Target {
        &mut self.0
    }
}

impl AsRef<Client> for CurseforgeHttpClient {
    fn as_ref(&self) -> &Client {
        &self.0
    }
}

impl From<Client> for CurseforgeHttpClient {
    fn from(value: Client) -> Self {
        Self(value)
    }
}

impl From<CurseforgeHttpClient> for Client {
    fn from(value: CurseforgeHttpClient) -> Self {
        value.0
    }
}
