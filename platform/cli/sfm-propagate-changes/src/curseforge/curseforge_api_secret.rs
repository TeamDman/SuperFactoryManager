use crate::one_password::OnePasswordSecretReference;
use crate::one_password::OnePasswordSecretValue;
use facet::Facet;
use std::fmt;
use std::ops::Deref;

pub const CURSEFORGE_TOKEN_ENV_VAR: &str = "CURSEFORGE_API_TOKEN";
pub const CURSEFORGE_CORE_API_KEY_ENV_VAR: &str = "CURSEFORGE_CORE_API_KEY";
pub const DEFAULT_OP_SECRET_REFERENCE: &str = "op://Private/CurseForge SFM Upload token/credential";
pub const DEFAULT_OP_CORE_API_KEY_SECRET_REFERENCE: &str =
    "op://Private/SFM CurseForge studios token/credential";

#[derive(Clone, Eq, Facet, Hash, Ord, PartialEq, PartialOrd)]
#[repr(transparent)]
pub struct CurseforgeApiSecret(#[facet(sensitive)] String);

impl CurseforgeApiSecret {
    pub fn new(value: impl AsRef<str>) -> eyre::Result<Self> {
        Self::from_raw(value.as_ref(), "CurseForge API secret was empty")
    }

    pub fn resolve(token: Option<String>, op_secret: Option<String>) -> eyre::Result<Self> {
        if let Some(value) = token {
            return Self::from_cli_argument(value, "--token");
        }

        if let Some(secret) = Self::from_environment(CURSEFORGE_TOKEN_ENV_VAR)? {
            return Ok(secret);
        }

        let secret_reference = op_secret
            .map(OnePasswordSecretReference::new)
            .transpose()?
            .unwrap_or(OnePasswordSecretReference::new(
                DEFAULT_OP_SECRET_REFERENCE,
            )?);
        secret_reference.read()
    }

    pub fn resolve_core(
        api_key: Option<String>,
        token: Option<String>,
        op_secret: Option<String>,
    ) -> eyre::Result<(Self, String)> {
        if let Some(value) = api_key {
            return Ok((
                Self::from_cli_argument(value, "--api-key")?,
                "--api-key".to_string(),
            ));
        }

        if let Some(secret) = Self::from_environment(CURSEFORGE_CORE_API_KEY_ENV_VAR)? {
            return Ok((secret, CURSEFORGE_CORE_API_KEY_ENV_VAR.to_string()));
        }

        if let Some(secret_reference) = op_secret {
            return Self::read_with_source(OnePasswordSecretReference::new(secret_reference)?);
        }

        if let Ok(resolved) = Self::read_with_source(OnePasswordSecretReference::new(
            DEFAULT_OP_CORE_API_KEY_SECRET_REFERENCE,
        )?) {
            return Ok(resolved);
        }

        if let Ok(resolved) = Self::read_with_source(OnePasswordSecretReference::new(
            DEFAULT_OP_SECRET_REFERENCE,
        )?) {
            return Ok(resolved);
        }

        if let Some(value) = token {
            return Ok((
                Self::from_cli_argument(value, "--token")?,
                "--token".to_string(),
            ));
        }

        if let Some(secret) = Self::from_environment(CURSEFORGE_TOKEN_ENV_VAR)? {
            return Ok((secret, CURSEFORGE_TOKEN_ENV_VAR.to_string()));
        }

        eyre::bail!(
            "Could not resolve CurseForge Core API key. Tried --api-key, {CURSEFORGE_CORE_API_KEY_ENV_VAR}, and 1Password defaults:\n\
             - {DEFAULT_OP_CORE_API_KEY_SECRET_REFERENCE}\n\
             - {DEFAULT_OP_SECRET_REFERENCE}"
        )
    }

    pub fn as_str(&self) -> &str {
        &self.0
    }

    fn read_with_source(
        secret_reference: OnePasswordSecretReference,
    ) -> eyre::Result<(Self, String)> {
        let source = format!("1Password ({})", secret_reference.as_str());
        let secret = secret_reference.read()?;
        Ok((secret, source))
    }

    fn from_cli_argument(value: String, argument_name: &str) -> eyre::Result<Self> {
        Self::from_raw(&value, &format!("Provided {argument_name} was empty"))
    }

    fn from_environment(env_var: &str) -> eyre::Result<Option<Self>> {
        let Ok(value) = std::env::var(env_var) else {
            return Ok(None);
        };

        let trimmed = value.trim();
        if trimmed.is_empty() {
            return Ok(None);
        }

        Ok(Some(Self::new(trimmed)?))
    }

    fn from_raw(value: &str, empty_message: &str) -> eyre::Result<Self> {
        let trimmed = value.trim();
        if trimmed.is_empty() {
            eyre::bail!("{empty_message}");
        }

        Ok(Self(trimmed.to_string()))
    }
}

impl OnePasswordSecretValue for CurseforgeApiSecret {
    fn from_secret_value(value: String) -> eyre::Result<Self> {
        Self::new(value)
    }

    fn secret_kind() -> &'static str {
        "CurseForge API secret"
    }
}

impl fmt::Debug for CurseforgeApiSecret {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter
            .debug_tuple("CurseforgeApiSecret")
            .field(&"<redacted>")
            .finish()
    }
}

impl Deref for CurseforgeApiSecret {
    type Target = str;

    fn deref(&self) -> &Self::Target {
        self.as_str()
    }
}

impl AsRef<str> for CurseforgeApiSecret {
    fn as_ref(&self) -> &str {
        self.as_str()
    }
}

impl From<CurseforgeApiSecret> for String {
    fn from(value: CurseforgeApiSecret) -> Self {
        value.0
    }
}
