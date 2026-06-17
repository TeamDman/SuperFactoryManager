use crate::one_password::OnePasswordSecretReference;
use crate::one_password::OnePasswordSecretValue;
use facet::Facet;
use std::fmt;
use std::ops::Deref;

pub const MODRINTH_TOKEN_ENV_VAR: &str = "MODRINTH_TOKEN";
pub const DEFAULT_OP_SECRET_REFERENCE: &str = "op://Private/Modrinth SFM API token/credential";

#[derive(Clone, Eq, Facet, Hash, Ord, PartialEq, PartialOrd)]
#[repr(transparent)]
pub struct ModrinthApiSecret(#[facet(sensitive)] String);

impl ModrinthApiSecret {
    pub fn new(value: impl AsRef<str>) -> eyre::Result<Self> {
        Self::from_raw(value.as_ref(), "Modrinth API secret was empty")
    }

    pub fn resolve(token: Option<String>, op_secret: Option<String>) -> eyre::Result<Self> {
        if let Some(value) = token {
            return Self::from_cli_argument(value, "--token");
        }

        if let Some(secret) = Self::from_environment(MODRINTH_TOKEN_ENV_VAR)? {
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

    pub fn as_str(&self) -> &str {
        &self.0
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

impl OnePasswordSecretValue for ModrinthApiSecret {
    fn from_secret_value(value: String) -> eyre::Result<Self> {
        Self::new(value)
    }

    fn secret_kind() -> &'static str {
        "Modrinth API secret"
    }
}

impl fmt::Debug for ModrinthApiSecret {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter
            .debug_tuple("ModrinthApiSecret")
            .field(&"<redacted>")
            .finish()
    }
}

impl Deref for ModrinthApiSecret {
    type Target = str;

    fn deref(&self) -> &Self::Target {
        self.as_str()
    }
}

impl AsRef<str> for ModrinthApiSecret {
    fn as_ref(&self) -> &str {
        self.as_str()
    }
}

impl From<ModrinthApiSecret> for String {
    fn from(value: ModrinthApiSecret) -> Self {
        value.0
    }
}
