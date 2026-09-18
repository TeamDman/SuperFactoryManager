//! Explicit, local discovery leases. Expiry is enforced independently of cleanup.
use super::CurseforgeApiSecret;
use super::DEFAULT_OP_CORE_API_KEY_SECRET_REFERENCE;
#[cfg(windows)]
use super::curseforge_auth_windows as native;
use crate::one_password::OnePasswordSecretReference;
use chrono::Utc;
use eyre::Context;
use facet::Facet;
use std::path::Path;
use std::path::PathBuf;
use std::time::Duration;
use std::time::Instant;

pub const DISCOVERY_LOGIN_HINT: &str =
    "Run: sfm-propagate-changes.exe curseforge auth login --purpose discovery --ttl 20m";
const MAX_TTL_SECONDS: u64 = 3600;
const LEASE_SCHEMA: &str = "sfm.curseforge-discovery-lease/1";

/// A Core credential cannot be passed to the author API constructor.
#[derive(Debug)]
pub struct CurseforgeCoreCredential {
    pub(super) secret: CurseforgeApiSecret,
    validity: Option<LeaseValidity>,
}

#[derive(Debug)]
struct LeaseValidity {
    path: PathBuf,
    issued_at: i64,
    expires_at: i64,
    loaded: Instant,
    remaining: Duration,
}

impl CurseforgeCoreCredential {
    pub(super) fn explicit(secret: CurseforgeApiSecret) -> Self {
        Self {
            secret,
            validity: None,
        }
    }

    pub(super) fn check(&self) -> eyre::Result<()> {
        if let Some(validity) = &self.validity {
            let now = Utc::now().timestamp();
            eyre::ensure!(
                now >= validity.issued_at
                    && now < validity.expires_at
                    && validity.loaded.elapsed() < validity.remaining
                    && validity.path.is_file(),
                "Discovery authentication expired or was logged out. {DISCOVERY_LOGIN_HINT}"
            );
        }
        Ok(())
    }
}

// Deliberately no Debug: parse failures must never include the decrypted payload.
#[derive(Facet)]
struct Lease {
    schema: String,
    purpose: String,
    issued_at: i64,
    expires_at: i64,
    #[facet(sensitive)]
    api_key: String,
}

impl Lease {
    fn validate(&self, now: i64) -> eyre::Result<()> {
        eyre::ensure!(
            self.schema == LEASE_SCHEMA
                && self.purpose == "discovery"
                && self
                    .expires_at
                    .checked_sub(self.issued_at)
                    .is_some_and(|ttl| (1..=3600).contains(&ttl))
                && now >= self.issued_at
                && now < self.expires_at
                && !self.api_key.trim().is_empty(),
            "Discovery lease is expired or invalid. {DISCOVERY_LOGIN_HINT}"
        );
        Ok(())
    }
}

/// Parse a bounded, explicit lifetime.
///
/// # Errors
/// Returns an error for invalid, zero, fractional-second or longer-than-one-hour durations.
pub fn parse_discovery_ttl(value: &str) -> eyre::Result<Duration> {
    let duration = humantime::parse_duration(value).wrap_err("Invalid discovery TTL")?;
    eyre::ensure!(
        (1..=MAX_TTL_SECONDS).contains(&duration.as_secs()) && duration.subsec_nanos() == 0,
        "Discovery TTL must be between 1 second and 1 hour, in whole seconds"
    );
    Ok(duration)
}

fn auth_root() -> eyre::Result<PathBuf> {
    eyre::ensure!(
        cfg!(windows),
        "Persistent discovery login currently requires Windows; use CURSEFORGE_CORE_API_KEY for an explicit process credential"
    );
    // Do not use APP_HOME: its configurable/fallback path may be inside a repository.
    let dirs = directories_next::ProjectDirs::from("", "teamdman", "sfm-propagate-changes")
        .ok_or_else(|| eyre::eyre!("Cannot determine private credential storage directory"))?;
    Ok(dirs
        .data_local_dir()
        .join("credentials")
        .join("curseforge-discovery"))
}

fn lease_paths(root: &Path) -> eyre::Result<Vec<PathBuf>> {
    let entries = match std::fs::read_dir(root) {
        Ok(entries) => entries,
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => return Ok(Vec::new()),
        Err(error) => return Err(error.into()),
    };
    let mut paths = Vec::new();
    for entry in entries {
        let entry = entry?;
        let name = entry.file_name();
        if name.to_str().is_some_and(valid_lease_name) {
            paths.push(entry.path());
        }
    }
    paths.sort();
    Ok(paths)
}

pub(super) fn valid_lease_name(name: &str) -> bool {
    name.strip_prefix("lease-")
        .and_then(|s| s.strip_suffix(".bin"))
        .is_some_and(|id| id.len() == 32 && id.bytes().all(|c| c.is_ascii_hexdigit()))
}

fn read_lease(path: &Path) -> eyre::Result<Lease> {
    let name = path
        .file_name()
        .and_then(|n| n.to_str())
        .ok_or_else(|| eyre::eyre!("Invalid lease filename"))?;
    let bytes = native::read(
        path.parent()
            .ok_or_else(|| eyre::eyre!("Missing lease directory"))?,
        name,
    )?;
    let text = String::from_utf8(bytes)
        .map_err(|_sensitive_error| eyre::eyre!("Invalid discovery lease encoding"))?;
    facet_json::from_str(&text).map_err(|_sensitive_error| {
        eyre::eyre!("Invalid discovery lease payload; run curseforge auth logout")
    })
}

/// Load a lease without contacting 1Password or printing its payload.
///
/// # Errors
/// Returns an error when no valid lease exists or storage cannot be read securely.
pub fn load_discovery_credential() -> eyre::Result<CurseforgeCoreCredential> {
    let root = auth_root()?;
    let now = Utc::now().timestamp();
    let mut selected: Option<(PathBuf, Lease)> = None;
    for path in lease_paths(&root)? {
        let lease = read_lease(&path)?;
        if lease.validate(now).is_err() {
            continue;
        }
        if selected
            .as_ref()
            .is_none_or(|(_, old)| old.issued_at < lease.issued_at)
        {
            selected = Some((path, lease));
        }
    }
    let (path, lease) = selected.ok_or_else(|| {
        eyre::eyre!("No valid CurseForge discovery authentication. {DISCOVERY_LOGIN_HINT}")
    })?;
    let remaining = Duration::from_secs(u64::try_from(lease.expires_at - now)?);
    Ok(CurseforgeCoreCredential {
        secret: CurseforgeApiSecret::new(lease.api_key)?,
        validity: Some(LeaseValidity {
            path,
            issued_at: lease.issued_at,
            expires_at: lease.expires_at,
            loaded: Instant::now(),
            remaining,
        }),
    })
}

/// Explicitly authorize repeated discovery for one bounded lifetime.
///
/// # Errors
/// Returns an error if secret acquisition, secure storage or scheduled cleanup fails.
pub fn discovery_login(ttl: &str, op_secret: Option<&str>) -> eyre::Result<String> {
    let ttl = parse_discovery_ttl(ttl)?;
    let root = auth_root()?;
    native::prepare(&root)?;
    let reference = OnePasswordSecretReference::new(
        op_secret.unwrap_or(DEFAULT_OP_CORE_API_KEY_SECRET_REFERENCE),
    )?;
    let secret: CurseforgeApiSecret = reference.read().map_err(|_sensitive_error| {
        eyre::eyre!("1Password could not provide the Core API key; no discovery lease was created")
    })?;
    let now = Utc::now();
    let expires = now + chrono::Duration::seconds(i64::try_from(ttl.as_secs())?);
    let lease = Lease {
        schema: LEASE_SCHEMA.to_owned(),
        purpose: "discovery".to_owned(),
        issued_at: now.timestamp(),
        expires_at: expires.timestamp(),
        api_key: secret.as_str().to_owned(),
    };
    let payload = facet_json::to_string(&lease)
        .map_err(|_sensitive_error| eyre::eyre!("Cannot encode discovery lease"))?;
    let name = format!("lease-{:032x}.bin", rand::random::<u128>());
    native::write(
        &root,
        &name,
        expires,
        payload.as_bytes(),
        &std::env::current_exe()?,
    )?;
    Ok(format!(
        "Discovery authenticated until {}. This expires the local lease, not the underlying API key.",
        expires.to_rfc3339()
    ))
}

/// Describe cached authentication without printing credential values or reading 1Password.
///
/// # Errors
/// Returns an error if private storage cannot be inspected.
pub fn discovery_status() -> eyre::Result<String> {
    let root = auth_root()?;
    let paths = lease_paths(&root)?;
    if paths.is_empty() {
        return Ok(format!(
            "Discovery: not authenticated. {DISCOVERY_LOGIN_HINT}"
        ));
    }
    let now = Utc::now().timestamp();
    let mut active = 0;
    let mut inactive = 0;
    let mut latest = now;
    for path in paths {
        match read_lease(&path) {
            Ok(lease) if lease.validate(now).is_ok() => {
                active += 1;
                latest = latest.max(lease.expires_at);
            }
            _ => inactive += 1,
        }
    }
    Ok(format!(
        "Discovery: {active} active lease(s), {inactive} expired/invalid lease(s); maximum remaining {}s. Publishing: separate explicit authorization required.",
        latest - now
    ))
}

/// Remove only this application's discovery leases and their corresponding cleanup tasks.
///
/// # Errors
/// Returns an error if deletion or task removal fails.
pub fn discovery_logout() -> eyre::Result<usize> {
    let root = auth_root()?;
    let paths = lease_paths(&root)?;
    for path in &paths {
        let name = path
            .file_name()
            .and_then(|name| name.to_str())
            .ok_or_else(|| eyre::eyre!("Invalid lease filename"))?;
        native::remove(&root, name)?;
    }
    Ok(paths.len())
}

/// Remove a single generated lease, without reading its secret or accepting arbitrary paths.
///
/// # Errors
/// Returns an error for invalid identities or failed file/task cleanup.
pub fn discovery_cleanup(name: &str) -> eyre::Result<()> {
    eyre::ensure!(valid_lease_name(name), "Invalid discovery lease identity");
    native::remove(&auth_root()?, name)
}

#[cfg(not(windows))]
mod native {
    use std::path::Path;
    pub(super) fn prepare(_: &Path) -> eyre::Result<()> {
        eyre::bail!("Persistent discovery leases require Windows")
    }
    pub(super) fn read(_: &Path, _: &str) -> eyre::Result<Vec<u8>> {
        eyre::bail!("Persistent discovery leases require Windows")
    }
    pub(super) fn write(
        _: &Path,
        _: &str,
        _: chrono::DateTime<chrono::Utc>,
        _: &[u8],
        _: &Path,
    ) -> eyre::Result<()> {
        eyre::bail!("Persistent discovery leases require Windows")
    }
    pub(super) fn remove(_: &Path, _: &str) -> eyre::Result<()> {
        eyre::bail!("Persistent discovery leases require Windows")
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    fn lease() -> Lease {
        Lease {
            schema: LEASE_SCHEMA.to_owned(),
            purpose: "discovery".to_owned(),
            issued_at: 100,
            expires_at: 200,
            api_key: "dummy-only".to_owned(),
        }
    }
    #[test]
    fn expiry_is_strict_and_clock_rollback_fails_closed() {
        let lease = lease();
        assert!(lease.validate(100).is_ok());
        assert!(lease.validate(199).is_ok());
        assert!(lease.validate(200).is_err());
        assert!(lease.validate(99).is_err());
    }
    #[test]
    fn ttl_is_bounded() {
        assert_eq!(parse_discovery_ttl("20m").unwrap().as_secs(), 1200);
        for invalid in ["0s", "61m", "1.5s", "nonsense"] {
            assert!(parse_discovery_ttl(invalid).is_err());
        }
    }
    #[test]
    fn lease_requires_discovery_purpose_and_schema() {
        let mut lease = lease();
        lease.purpose = "publish".to_owned();
        assert!(lease.validate(150).is_err());
        lease.purpose = "discovery".to_owned();
        lease.schema = "future".to_owned();
        assert!(lease.validate(150).is_err());
    }
    #[test]
    fn only_generated_filenames_are_cleanup_targets() {
        assert!(valid_lease_name(
            "lease-00000000000000000000000000000001.bin"
        ));
        for invalid in [
            "../lease-123.bin",
            "lease-*.bin",
            "lease-x.bin",
            "user-file.txt",
        ] {
            assert!(!valid_lease_name(invalid));
        }
    }

    #[cfg(windows)]
    #[test]
    #[ignore = "creates two short-lived Task Scheduler tasks using dummy credentials"]
    fn windows_dummy_lease_roundtrip_and_scheduled_cleanup() {
        let executable = std::env::var_os("SFM_AUTH_TEST_CLI")
            .map(PathBuf::from)
            .expect("Set SFM_AUTH_TEST_CLI to the newly built CLI, not this test binary");
        assert!(executable.is_file());
        let root = auth_root().unwrap();
        let first = format!("lease-{:032x}.bin", rand::random::<u128>());
        let second = format!("lease-{:032x}.bin", rand::random::<u128>());
        native::prepare(&root).unwrap();
        let now = Utc::now();
        let expires = now + chrono::Duration::seconds(25);
        let payload = Lease {
            schema: LEASE_SCHEMA.to_owned(),
            purpose: "discovery".to_owned(),
            issued_at: now.timestamp(),
            expires_at: expires.timestamp(),
            api_key: "DUMMY-LEASE-INTEGRATION-NOT-A-REAL-SECRET".to_owned(),
        };
        let json = facet_json::to_string(&payload).unwrap();
        native::write(&root, &first, expires, json.as_bytes(), &executable).unwrap();
        let path = root.join(&first);
        let ciphertext = std::fs::read(&path).unwrap();
        assert!(
            !ciphertext
                .windows(payload.api_key.len())
                .any(|s| s == payload.api_key.as_bytes())
        );
        let restored = read_lease(&path).unwrap();
        assert_eq!(restored.api_key, payload.api_key);
        let inspection = native::inspect(&first).unwrap();
        assert!(!inspection.contains(&payload.api_key));
        assert!(inspection.contains("P30D"));
        assert!(inspection.contains("<StartWhenAvailable>true"));
        assert!(inspection.contains("EndBoundary"));
        native::write(
            &root,
            &second,
            now + chrono::Duration::minutes(3),
            json.as_bytes(),
            &executable,
        )
        .unwrap();
        let deadline = Instant::now() + Duration::from_secs(45);
        while path.exists() && Instant::now() < deadline {
            std::thread::sleep(Duration::from_millis(500));
        }
        let removed = !path.exists();
        let renewed_exists = root.join(&second).is_file();
        let task_removed = native::inspect(&first).is_err();
        // Always clean our exact test leases before asserting scheduler results.
        native::remove(&root, &first).unwrap();
        native::remove(&root, &second).unwrap();
        assert!(removed, "scheduled action must delete its lease");
        assert!(task_removed, "scheduled action must unregister itself");
        assert!(renewed_exists, "older task must not delete a newer lease");
    }
}
