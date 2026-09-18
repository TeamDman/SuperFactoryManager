use crate::curseforge::CurseforgeApiSecret;
use crate::curseforge::CurseforgeCoreCredential;
use eyre::Context;
use reqwest::Method;
use reqwest::Url;
use reqwest::blocking::Client;
use reqwest::blocking::RequestBuilder;
use reqwest::header::HeaderMap;
use reqwest::header::HeaderValue;
use reqwest::header::USER_AGENT;
use std::time::Duration;

/// Credentials never escape through a raw client, redirects or arbitrary download URLs.
#[derive(Debug)]
pub struct CurseforgeHttpClient {
    client: Client,
    core: Option<CurseforgeCoreCredential>,
    origin: String,
    read_only: bool,
}

impl CurseforgeHttpClient {
    /// Build an author client from explicit per-invocation authorization.
    /// # Errors
    /// Returns an error if the header or HTTP client cannot be constructed.
    pub fn new(token: &CurseforgeApiSecret) -> eyre::Result<Self> {
        Ok(Self {
            client: Self::build("X-Api-Token", token.as_str())?,
            core: None,
            origin: "https://minecraft.curseforge.com".to_owned(),
            read_only: false,
        })
    }

    /// Build a read-only Core client, preserving any lease expiry guard.
    /// # Errors
    /// Returns an error if the header or HTTP client cannot be constructed.
    pub fn new_core_api(api_key: CurseforgeCoreCredential) -> eyre::Result<Self> {
        api_key.check()?;
        Ok(Self {
            client: Self::build("x-api-key", api_key.secret.as_str())?,
            core: Some(api_key),
            origin: "https://api.curseforge.com".to_owned(),
            read_only: true,
        })
    }

    fn build(header_name: &'static str, secret: &str) -> eyre::Result<Client> {
        let mut headers = HeaderMap::new();
        let mut header = HeaderValue::from_str(secret).wrap_err("Invalid credential header")?;
        header.set_sensitive(true);
        headers.insert(header_name, header);
        headers.insert(
            USER_AGENT,
            HeaderValue::from_static("sfm-propagate-changes/curseforge"),
        );
        Client::builder()
            .default_headers(headers)
            .redirect(reqwest::redirect::Policy::none())
            .timeout(Duration::from_mins(2))
            .build()
            .wrap_err("Cannot build CurseForge HTTP client")
    }

    fn request(&self, method: Method, value: &str) -> eyre::Result<RequestBuilder> {
        if let Some(core) = &self.core {
            core.check()?;
        }
        let url = Url::parse(value).wrap_err("Invalid CurseForge URL")?;
        eyre::ensure!(
            url.origin().ascii_serialization() == self.origin
                && url.username().is_empty()
                && url.password().is_none(),
            "Refusing to send a CurseForge credential to another origin"
        );
        eyre::ensure!(
            !self.read_only || method == Method::GET,
            "Discovery credentials authorize GET only"
        );
        Ok(self.client.request(method, url))
    }

    /// Construct an authenticated read after validating origin and lease.
    /// # Errors
    /// Returns an error for an expired lease or foreign origin.
    pub fn get(&self, url: &str) -> eyre::Result<RequestBuilder> {
        self.request(Method::GET, url)
    }

    /// Construct an author write; discovery cannot use this path.
    /// # Errors
    /// Returns an error for a discovery credential or foreign origin.
    pub fn post(&self, url: &str) -> eyre::Result<RequestBuilder> {
        self.request(Method::POST, url)
    }

    #[cfg(test)]
    pub(crate) fn test_client(client: Client, origin: String) -> Self {
        Self {
            client,
            core: None,
            origin,
            read_only: true,
        }
    }
}

impl AsRef<Self> for CurseforgeHttpClient {
    fn as_ref(&self) -> &Self {
        self
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn core_cannot_write_or_leak_to_download_and_redirect_targets() {
        let credential =
            CurseforgeCoreCredential::explicit(CurseforgeApiSecret::new("dummy-test-key").unwrap());
        let client = CurseforgeHttpClient::new_core_api(credential).unwrap();
        assert!(client.get("https://api.curseforge.com/v1/mods/1").is_ok());
        assert!(client.post("https://api.curseforge.com/v1/mods/1").is_err());
        for url in [
            "https://minecraft.curseforge.com/api/projects/1/upload-file",
            "https://example.invalid/file",
            "http://api.curseforge.com/v1/mods/1",
            "https://api.curseforge.com.evil.invalid/",
            "https://user@api.curseforge.com/v1",
        ] {
            assert!(client.get(url).is_err());
        }
        assert!(!format!("{client:?}").contains("dummy-test-key"));
    }
}
