use super::CurseforgeCoreCredential;
use super::DISCOVERY_LOGIN_HINT;
use super::load_discovery_credential;
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
    /// Validate secret material without including it in errors.
    /// # Errors
    /// Returns an error if the credential is empty.
    pub fn new(value: impl AsRef<str>) -> eyre::Result<Self> {
        let value = value.as_ref().trim();
        eyre::ensure!(!value.is_empty(), "CurseForge credential was empty");
        Ok(Self(value.to_owned()))
    }

    /// Resolve explicit author authorization, never an inherited token or discovery lease.
    /// # Errors
    /// Returns an error if invocation-specific authorization is missing or unavailable.
    pub fn resolve(token: Option<String>, op_secret: Option<String>) -> eyre::Result<Self> {
        if let Some(value) = token {
            return Self::new(value);
        }
        let reference = op_secret.ok_or_else(|| eyre::eyre!(
            "Author API authorization must be explicit for this invocation: pass --op-secret <author-token-reference> (recommended) or --token. Discovery leases and CURSEFORGE_API_TOKEN are not used."
        ))?;
        OnePasswordSecretReference::new(reference)?
            .read()
            .map_err(|_sensitive_error| {
                eyre::eyre!("Could not read the explicitly requested author token from 1Password")
            })
    }

    /// Resolve a Core key without invoking 1Password or falling back to author credentials.
    /// # Errors
    /// Returns an actionable error when no valid discovery authorization is present.
    #[expect(
        clippy::needless_pass_by_value,
        reason = "Own and discard legacy credential arguments; never retain them as discovery sources"
    )]
    pub fn resolve_core(
        api_key: Option<String>,
        token: Option<String>,
        op_secret: Option<String>,
    ) -> eyre::Result<(CurseforgeCoreCredential, String)> {
        eyre::ensure!(
            token.is_none(),
            "--token is an author credential and cannot authorize discovery. {DISCOVERY_LOGIN_HINT}"
        );
        eyre::ensure!(
            op_secret.is_none(),
            "Discovery does not invoke 1Password implicitly; pass --op-secret to auth login instead. {DISCOVERY_LOGIN_HINT}"
        );
        if let Some(value) = api_key {
            return Ok((
                CurseforgeCoreCredential::explicit(Self::new(value)?),
                "--api-key".to_owned(),
            ));
        }
        if let Ok(value) = std::env::var(CURSEFORGE_CORE_API_KEY_ENV_VAR)
            && !value.trim().is_empty()
        {
            return Ok((
                CurseforgeCoreCredential::explicit(Self::new(value)?),
                CURSEFORGE_CORE_API_KEY_ENV_VAR.to_owned(),
            ));
        }
        Ok((
            load_discovery_credential()?,
            "local discovery lease".to_owned(),
        ))
    }

    #[must_use]
    pub fn as_str(&self) -> &str {
        &self.0
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
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.debug_tuple("CurseforgeApiSecret")
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

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn core_rejects_author_and_implicit_one_password_sources() {
        assert!(CurseforgeApiSecret::resolve_core(None, Some("dummy".to_owned()), None).is_err());
        assert!(
            CurseforgeApiSecret::resolve_core(None, None, Some("op://dummy".to_owned())).is_err()
        );
    }
    #[test]
    fn author_requires_explicit_authorization() {
        assert!(CurseforgeApiSecret::resolve(None, None).is_err());
        assert_eq!(
            CurseforgeApiSecret::resolve(Some("dummy".to_owned()), None)
                .unwrap()
                .as_str(),
            "dummy"
        );
    }
    #[test]
    fn credential_debug_is_redacted() {
        let (core, _) =
            CurseforgeApiSecret::resolve_core(Some("secret-test-sentinel".to_owned()), None, None)
                .unwrap();
        assert!(!format!("{core:?}").contains("secret-test-sentinel"));
    }
}
