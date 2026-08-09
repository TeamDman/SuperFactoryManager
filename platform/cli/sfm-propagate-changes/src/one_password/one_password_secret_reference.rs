use eyre::Context;
use std::ops::Deref;
use std::process::Command;

pub trait OnePasswordSecretValue: Sized {
    /// Convert the secret material returned by `op read` into the target type.
    ///
    /// # Errors
    ///
    /// Returns an error when the provided secret value is not valid for the target type.
    fn from_secret_value(value: String) -> eyre::Result<Self>;

    fn secret_kind() -> &'static str;
}

#[derive(Clone, Debug, Eq, Hash, Ord, PartialEq, PartialOrd)]
#[repr(transparent)]
pub struct OnePasswordSecretReference(pub String);

impl OnePasswordSecretReference {
    /// Create a validated `1Password` secret reference.
    ///
    /// # Errors
    ///
    /// Returns an error when the provided reference is empty after trimming.
    pub fn new(value: impl AsRef<str>) -> eyre::Result<Self> {
        let trimmed = value.as_ref().trim();
        if trimmed.is_empty() {
            eyre::bail!("1Password secret reference was empty");
        }

        Ok(Self(trimmed.to_string()))
    }

    /// Read a typed secret value from `1Password` with `op read`.
    ///
    /// # Errors
    ///
    /// Returns an error when the `op` CLI cannot be executed, the secret cannot be read,
    /// the returned value is empty, or the target type rejects the secret contents.
    pub fn read<T>(&self) -> eyre::Result<T>
    where
        T: OnePasswordSecretValue,
    {
        let output = Command::new("op")
            .args(["read", self.as_str(), "--no-newline"])
            .output()
            .wrap_err("Failed to run 1Password CLI (`op`)")?;

        if !output.status.success() {
            eyre::bail!(
                "Failed to read {} from 1Password secret '{}': {}",
                T::secret_kind(),
                self.as_str(),
                String::from_utf8_lossy(&output.stderr)
            );
        }

        let value = String::from_utf8_lossy(&output.stdout).trim().to_string();
        if value.is_empty() {
            eyre::bail!(
                "1Password returned an empty {} for secret '{}'",
                T::secret_kind(),
                self.as_str()
            );
        }

        T::from_secret_value(value)
    }

    #[must_use]
    pub fn as_str(&self) -> &str {
        &self.0
    }
}

impl Deref for OnePasswordSecretReference {
    type Target = str;

    fn deref(&self) -> &Self::Target {
        self.as_str()
    }
}

impl AsRef<str> for OnePasswordSecretReference {
    fn as_ref(&self) -> &str {
        self.as_str()
    }
}

impl From<OnePasswordSecretReference> for String {
    fn from(value: OnePasswordSecretReference) -> Self {
        value.0
    }
}

impl TryFrom<String> for OnePasswordSecretReference {
    type Error = eyre::Report;

    fn try_from(value: String) -> Result<Self, Self::Error> {
        Self::new(value)
    }
}
