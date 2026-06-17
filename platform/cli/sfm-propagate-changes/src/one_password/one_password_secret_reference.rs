use eyre::Context;
use std::ops::Deref;
use std::process::Command;

pub trait OnePasswordSecretValue: Sized {
    fn from_secret_value(value: String) -> eyre::Result<Self>;

    fn secret_kind() -> &'static str;
}

#[derive(Clone, Debug, Eq, Hash, Ord, PartialEq, PartialOrd)]
#[repr(transparent)]
pub struct OnePasswordSecretReference(pub String);

impl OnePasswordSecretReference {
    pub fn new(value: impl AsRef<str>) -> eyre::Result<Self> {
        let trimmed = value.as_ref().trim();
        if trimmed.is_empty() {
            eyre::bail!("1Password secret reference was empty");
        }

        Ok(Self(trimmed.to_string()))
    }

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
