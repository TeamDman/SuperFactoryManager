//! Explicit, content-pinned external Java trust-store selection.
//!
//! The caller is responsible for deriving and approving the store. The SHA-256
//! identifies those approved bytes; it does not certify their CA policy.

use eyre::{Result, ensure};
use std::fs::{self, File};
use std::io::Read;
use std::path::{Path, PathBuf};
use std::process::Command;

const STORE_ENV: &str = "SFM_JAVA_TRUST_STORE";
const DIGEST_ENV: &str = "SFM_JAVA_TRUST_SHA256";
const MAX_STORE_BYTES: u64 = 8 * 1024 * 1024;
const JVM_OVERRIDE_NAMES: [&str; 4] = [
    "JAVA_TOOL_OPTIONS",
    "JDK_JAVA_OPTIONS",
    "_JAVA_OPTIONS",
    "JDK_JAVAC_OPTIONS",
];

#[derive(Debug)]
pub(crate) struct JavaTrust {
    path: PathBuf,
    sha256: String,
    held: File,
    identity: crate::file_identity::FileIdentity,
}

impl JavaTrust {
    pub(crate) fn configure_java(
        command: &mut Command,
        compiler: bool,
        major: u32,
    ) -> Result<Option<Self>> {
        let selected = Self::from_environment_for_major(major)?;
        if let Some(trust) = &selected {
            if compiler {
                trust.apply_javac(command)?;
            } else {
                trust.apply_java(command)?;
            }
            tracing::info!(trust_store_sha256 = %trust.identity(), "selected explicit Java trust store");
        }
        Ok(selected)
    }

    pub(crate) fn recheck_optional(trust: Option<&Self>) -> Result<()> {
        if let Some(trust) = trust {
            trust.recheck()?;
        }
        Ok(())
    }

    /// Opt in only with both values; a partial or malformed policy fails closed.
    pub(crate) fn from_environment() -> Result<Option<Self>> {
        Self::from_pair(STORE_ENV, DIGEST_ENV)
    }

    pub(crate) fn from_environment_for_major(major: u32) -> Result<Option<Self>> {
        match Self::from_pair(
            &format!("{STORE_ENV}_{major}"),
            &format!("{DIGEST_ENV}_{major}"),
        )? {
            Some(trust) => Ok(Some(trust)),
            None => Self::from_environment(),
        }
    }

    pub(crate) fn environment_identity() -> Result<String> {
        let mut identities = Vec::new();
        for major in [17, 21, 25] {
            identities.push(format!(
                "{major}={}",
                Self::from_environment_for_major(major)?
                    .as_ref()
                    .map_or_else(|| "jdk-default".to_owned(), Self::identity)
            ));
        }
        Ok(identities.join("\n"))
    }

    fn from_pair(store_env: &str, digest_env: &str) -> Result<Option<Self>> {
        match (std::env::var_os(store_env), std::env::var_os(digest_env)) {
            (None, None) => Ok(None),
            (Some(path), Some(digest)) => {
                let digest = digest
                    .into_string()
                    .map_err(|_non_unicode| eyre::eyre!("Java trust SHA-256 must be Unicode"))?;
                Ok(Some(Self::capture(Path::new(&path), &digest)?))
            }
            _ => Err(eyre::eyre!("{store_env} and {digest_env} must be set together")),
        }
    }

    pub(crate) fn capture(path: &Path, sha256: &str) -> Result<Self> {
        ensure!(path.is_absolute(), "Java trust store path must be absolute");
        // The same path must be representable in JAVA_TOOL_OPTIONS for a nested
        // Cargo/Java producer, without creating another launcher option.
        Self::property_path(path)?;
        let digest = sha256
            .strip_prefix("sha256:")
            .unwrap_or(sha256)
            .to_ascii_lowercase();
        ensure!(
            digest.len() == 64 && digest.bytes().all(|b| b.is_ascii_hexdigit()),
            "Java trust store identity must be a SHA-256 digest"
        );
        let metadata = fs::symlink_metadata(path)?;
        ensure!(
            metadata.is_file()
                && !metadata.file_type().is_symlink()
                && metadata.len() <= MAX_STORE_BYTES
                && metadata.len() != 0,
            "Java trust store must be a bounded, nonempty regular file, not a symlink"
        );
        let path = dunce::canonicalize(path)?;
        Self::property_path(&path)?;
        let held = File::open(&path)?;
        let identity = crate::file_identity::file_identity(&held)?;
        let trust = Self {
            path,
            sha256: digest,
            held,
            identity,
        };
        trust.recheck()?;
        Ok(trust)
    }

    pub(crate) fn identity(&self) -> String {
        format!("sha256:{}", self.sha256)
    }

    pub(crate) fn recheck(&self) -> Result<()> {
        let metadata = fs::symlink_metadata(&self.path)?;
        ensure!(
            metadata.is_file()
                && !metadata.file_type().is_symlink()
                && metadata.len() != 0
                && metadata.len() <= MAX_STORE_BYTES,
            "selected Java trust store is no longer a bounded regular file"
        );
        let opened = File::open(&self.path)?;
        ensure!(
            crate::file_identity::file_identity(&self.held)? == self.identity
                && crate::file_identity::file_identity(&opened)? == self.identity,
            "selected Java trust store file identity changed"
        );
        let mut bytes = Vec::new();
        (&opened)
            .take(MAX_STORE_BYTES + 1)
            .read_to_end(&mut bytes)?;
        ensure!(
            bytes.len() as u64 <= MAX_STORE_BYTES
                && crate::source_projection::provenance::sha256(&bytes) == self.identity(),
            "selected Java trust store bytes changed or do not match the approved SHA-256"
        );
        let reopened = File::open(&self.path)?;
        ensure!(
            crate::file_identity::file_identity(&reopened)? == self.identity,
            "selected Java trust store was replaced while checking bytes"
        );
        Ok(())
    }

    fn clean_overrides(command: &mut Command) {
        for name in JVM_OVERRIDE_NAMES {
            command.env_remove(name);
        }
    }

    fn property_path(path: &Path) -> Result<String> {
        let text = path
            .to_str()
            .ok_or_else(|| eyre::eyre!("Java trust store path must be Unicode"))?;
        let text = if cfg!(windows) {
            text.replace('\\', "/")
        } else {
            text.to_owned()
        };
        ensure!(
            !text.contains(['\0', '\r', '\n', '\'', '"', '\\']),
            "Java trust store path cannot be safely represented in nested JVM options"
        );
        Ok(text)
    }

    /// Add only the two trust-store properties after the caller's admission.
    pub(crate) fn apply_java(&self, command: &mut Command) -> Result<()> {
        self.recheck()?;
        Self::clean_overrides(command);
        command
            .arg(format!(
                "-Djavax.net.ssl.trustStore={}",
                Self::property_path(&self.path)?
            ))
            .arg("-Djavax.net.ssl.trustStorePassword=changeit");
        Ok(())
    }

    pub(crate) fn apply_javac(&self, command: &mut Command) -> Result<()> {
        self.recheck()?;
        Self::clean_overrides(command);
        command
            .arg(format!(
                "-J-Djavax.net.ssl.trustStore={}",
                Self::property_path(&self.path)?
            ))
            .arg("-J-Djavax.net.ssl.trustStorePassword=changeit");
        Ok(())
    }

    /// The Cargo producer starts JVMs itself, so carry the pinned properties
    /// only in that child environment; do not change the parent environment.
    pub(crate) fn apply_nested(&self, command: &mut Command) -> Result<()> {
        self.recheck()?;
        Self::clean_overrides(command);
        command.env(
            "JAVA_TOOL_OPTIONS",
            format!(
                "\"-Djavax.net.ssl.trustStore={}\" -Djavax.net.ssl.trustStorePassword=changeit",
                Self::property_path(&self.path)?
            ),
        );
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn verifies_identity_and_refuses_changed_or_replaced_store() -> Result<()> {
        let root = tempfile::tempdir()?;
        let path = root.path().join("trust store");
        fs::write(&path, b"original")?;
        let hash = crate::source_projection::provenance::sha256(b"original");
        let trust = JavaTrust::capture(&path, &hash)?;
        assert_eq!(trust.identity(), hash);
        let mut nested = Command::new("unused");
        trust.apply_nested(&mut nested)?;
        let options = nested
            .get_envs()
            .find(|(name, _)| *name == "JAVA_TOOL_OPTIONS")
            .and_then(|(_, value)| value)
            .unwrap()
            .to_string_lossy();
        assert!(options.contains("trust store\""));
        fs::write(&path, b"modified")?;
        assert!(trust.recheck().is_err());
        fs::remove_file(&path)?;
        fs::write(&path, b"original")?;
        assert!(trust.recheck().is_err());
        Ok(())
    }

    #[test]
    fn rejects_unpinned_or_unsafe_path() -> Result<()> {
        let root = tempfile::tempdir()?;
        let path = root.path().join("unsafe\"name");
        let error = JavaTrust::capture(&path, &"00".repeat(32)).unwrap_err();
        assert!(error.to_string().contains("cannot be safely represented"));
        let path = root.path().join("safe");
        fs::write(&path, b"bytes")?;
        assert!(JavaTrust::capture(&path, &"00".repeat(32)).is_err());
        assert!(JavaTrust::capture(Path::new("relative"), &"00".repeat(32)).is_err());
        Ok(())
    }

    #[test]
    fn selection_probe_child() -> Result<()> {
        let Ok(expected) = std::env::var("SFM_TEST_TRUST_SELECTION") else {
            return Ok(());
        };
        let selected = JavaTrust::from_environment_for_major(21);
        if expected == "error" {
            ensure!(
                selected.is_err(),
                "partial major-specific selection fell back"
            );
        } else {
            ensure!(
                selected?
                    .as_ref()
                    .map_or_else(|| "none".to_owned(), JavaTrust::identity)
                    == expected,
                "wrong major-specific trust store selected"
            );
        }
        Ok(())
    }

    #[test]
    fn major_specific_store_precedes_shared_store_and_partial_pair_fails() -> Result<()> {
        let root = tempfile::tempdir()?;
        let shared = root.path().join("shared");
        let specific = root.path().join("java21");
        fs::write(&shared, b"shared store")?;
        fs::write(&specific, b"java21 store")?;
        let shared_hash = crate::source_projection::provenance::sha256(b"shared store");
        let specific_hash = crate::source_projection::provenance::sha256(b"java21 store");
        for case in 0..4 {
            let mut command = Command::new(std::env::current_exe()?);
            command.args([
                "--exact",
                "jar_build::java_trust::tests::selection_probe_child",
                "--nocapture",
            ]);
            for name in [
                STORE_ENV,
                DIGEST_ENV,
                "SFM_JAVA_TRUST_STORE_21",
                "SFM_JAVA_TRUST_SHA256_21",
            ] {
                command.env_remove(name);
            }
            let expected = match case {
                0 => "none",
                1 => &shared_hash,
                2 => &specific_hash,
                _ => "error",
            };
            if case > 0 {
                command
                    .env(STORE_ENV, &shared)
                    .env(DIGEST_ENV, &shared_hash);
            }
            if case > 1 {
                command.env("SFM_JAVA_TRUST_STORE_21", &specific);
            }
            if case == 2 {
                command.env("SFM_JAVA_TRUST_SHA256_21", &specific_hash);
            }
            command.env("SFM_TEST_TRUST_SELECTION", expected);
            let result = command.output()?;
            ensure!(
                result.status.success(),
                "selection test failed: {} {}",
                String::from_utf8_lossy(&result.stdout),
                String::from_utf8_lossy(&result.stderr)
            );
        }
        Ok(())
    }

    #[cfg(unix)]
    #[test]
    #[ignore = "Requires explicit Java, filtered store, CA policy and allowed HTTPS destination"]
    fn nested_java_uses_pinned_store_for_https() -> Result<()> {
        let env = |name: &str| -> Result<String> { Ok(std::env::var(name)?) };
        let java: String = env("SFM_TEST_TRUST_JAVA")?;
        let source: String = env("SFM_TEST_TRUST_STORE")?;
        let denied: String = env("SFM_TEST_TRUST_DENIED_SHA256")?;
        let proxy: String = env("SFM_TEST_TRUST_PROXY_HOST")?;
        let proxy_port: String = env("SFM_TEST_TRUST_PROXY_PORT")?;
        let proxy_ca: String = env("SFM_TEST_TRUST_PROXY_CA")?;
        let url: String = env("SFM_TEST_TRUST_URL")?;
        let root = tempfile::tempdir()?;
        let path = root.path().join("trust store with spaces");
        fs::copy(source, &path)?;
        let hash = crate::source_projection::provenance::sha256(&fs::read(&path)?);
        let trust = JavaTrust::capture(&path, &hash)?;
        let probe = root.path().join("JavaTrustProbe.java");
        fs::write(&probe, include_str!("../../scripts/JavaTrustProbe.java"))?;
        for nested in [false, true] {
            let mut command = if nested {
                let mut command = Command::new("sh");
                command.args(["-c", "exec \"$@\"", "nested-producer", &java]);
                command
            } else {
                Command::new(&java)
            };
            command.env(
                "_JAVA_OPTIONS",
                "-agentlib:sfm_invalid_override_must_be_removed",
            );
            if nested {
                trust.apply_nested(&mut command)?;
            } else {
                trust.apply_java(&mut command)?;
            }
            command
                .arg(format!("-Dhttps.proxyHost={proxy}"))
                .arg(format!("-Dhttps.proxyPort={proxy_port}"))
                .arg(&probe)
                .args([&denied, &proxy_ca, &url]);
            let result = command.output()?;
            trust.recheck()?;
            ensure!(
                result.status.success(),
                "JVM probe failed (nested={nested}): {}",
                String::from_utf8_lossy(&result.stderr)
            );
            ensure!(
                String::from_utf8_lossy(&result.stdout)
                    .contains("denied=0 expected_ca=present https=200"),
                "JVM probe did not report the expected filtered HTTPS result"
            );
        }
        Ok(())
    }
}
