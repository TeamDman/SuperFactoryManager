use crate::modrinth::ModrinthApiSecret;
use eyre::Context;
use reqwest::blocking::Client;
use reqwest::header::AUTHORIZATION;
use reqwest::header::HeaderMap;
use reqwest::header::HeaderValue;
use reqwest::header::USER_AGENT;
use std::ops::Deref;
use std::ops::DerefMut;
use std::time::Duration;

const MODRINTH_USER_AGENT: &str = "sfm-propagate-changes/modrinth";

#[derive(Clone, Debug)]
#[repr(transparent)]
pub struct ModrinthHttpClient(pub Client);

impl ModrinthHttpClient {
    pub fn new(token: Option<&ModrinthApiSecret>) -> eyre::Result<Self> {
        let mut headers = HeaderMap::new();
        headers.insert(USER_AGENT, HeaderValue::from_static(MODRINTH_USER_AGENT));

        if let Some(token_value) = token {
            headers.insert(
                AUTHORIZATION,
                HeaderValue::from_str(token_value.as_str())
                    .wrap_err("Invalid Modrinth token for header")?,
            );
        }

        let client = Client::builder()
            .default_headers(headers)
            .timeout(Duration::from_mins(2))
            .build()
            .wrap_err("Failed to build Modrinth HTTP client")?;

        Ok(Self(client))
    }
}

impl Deref for ModrinthHttpClient {
    type Target = Client;

    fn deref(&self) -> &Self::Target {
        &self.0
    }
}

impl DerefMut for ModrinthHttpClient {
    fn deref_mut(&mut self) -> &mut Self::Target {
        &mut self.0
    }
}

impl AsRef<Client> for ModrinthHttpClient {
    fn as_ref(&self) -> &Client {
        &self.0
    }
}

impl From<Client> for ModrinthHttpClient {
    fn from(value: Client) -> Self {
        Self(value)
    }
}

impl From<ModrinthHttpClient> for Client {
    fn from(value: ModrinthHttpClient) -> Self {
        value.0
    }
}
