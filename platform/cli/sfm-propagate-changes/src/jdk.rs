use crate::toolchain_lockfile_schema::version::v4::JdkArtifactV4;
use crate::toolchain_lockfile_schema::version::v4::JdkPinV4;
use eyre::Context;
use rayon::prelude::*;
use std::cmp::Ordering;
use std::collections::BTreeSet;
use std::fs;
use std::path::Path;
use std::path::PathBuf;
use std::process::Command;
use tracing::instrument;
use tracing::warn;

#[derive(Clone, Debug)]
pub(crate) struct JdkInstallation {
    pub(crate) home: Option<PathBuf>,
    pub(crate) java_executable: PathBuf,
    pub(crate) javac_executable: PathBuf,
    pub(crate) version_output: String,
    pub(crate) major_version: u32,
    pub(crate) source: String,
    /// `JetBrains` Runtime (JBR) is preferred for development builds of SFM, so we track whether each discovered JDK is a JBR distribution.
    pub(crate) is_jbr: bool,
}

#[derive(Clone, Debug)]
pub(crate) struct ResolvedJava {
    pub(crate) executable: PathBuf,
    pub(crate) home: Option<PathBuf>,
    pub(crate) version_output: String,
    pub(crate) major_version: u32,
    pub(crate) selection: String,
    pub(crate) pin_url: Option<String>,
    pub(crate) pin_sha512: Option<String>,
}

/// Selection policy only. A pinned artifact must be checksum-verified and
/// installed before it can become a [`ResolvedJava`].
#[derive(Clone, Copy, Debug)]
pub(crate) enum JdkSource<'a> {
    Explicit(&'a Path),
    Pinned {
        pin: &'a JdkPinV4,
        artifact: &'a JdkArtifactV4,
    },
    LegacyDiscovery,
}

/// The presence of a catalog opts a lockfile into exact-major pinning.
/// An explicit Java home always remains the user's deliberate override.
pub(crate) fn select_jdk_source<'a>(
    explicit_java_home: Option<&'a Path>,
    pins: Option<&'a [JdkPinV4]>,
    required_major: u32,
    platform: &str,
) -> eyre::Result<JdkSource<'a>> {
    if let Some(home) = explicit_java_home {
        return Ok(JdkSource::Explicit(home));
    }
    let Some(pins) = pins else {
        return Ok(JdkSource::LegacyDiscovery);
    };
    let pin = pins
        .iter()
        .find(|pin| pin.major == required_major)
        .ok_or_else(|| eyre::eyre!("No exact JBRSDK pin for Java {required_major}; choose --java-home explicitly or add a verified pin"))?;
    let artifact = pin
        .artifacts
        .iter()
        .find(|artifact| artifact.platform == platform)
        .ok_or_else(|| eyre::eyre!(
            "No Java {} JBRSDK artifact is pinned for platform `{platform}`; choose --java-home explicitly or add a verified platform artifact",
            required_major
        ))?;
    Ok(JdkSource::Pinned { pin, artifact })
}

/// Resolve the exact locked SDK when a v4 catalog is present. The cache is
/// caller-owned so tests and future offline entry points can use the same
/// policy without changing process-global environment variables.
pub(crate) fn resolve_java_for_lockfile(
    explicit_java_home: Option<&Path>,
    pins: Option<&[JdkPinV4]>,
    required_major: u32,
    cache_root: &Path,
    offline: bool,
) -> eyre::Result<ResolvedJava> {
    let platform = host_jbrsdk_platform().unwrap_or("unsupported-host");
    match select_jdk_source(explicit_java_home, pins, required_major, platform)? {
        JdkSource::Explicit(home) => resolve_java(Some(home), required_major),
        JdkSource::LegacyDiscovery => resolve_java(None, required_major),
        JdkSource::Pinned { pin, artifact } => {
            let home = crate::jdk_artifact_cache::acquire_pinned_jbrsdk_zip(
                pin, artifact, cache_root, offline,
            )?;
            let jdk = JdkInstallation::from_home(&home, "exact JBRSDK lockfile pin".to_owned())?;
            verify_pinned_runtime(&jdk, pin)?;
            let mut resolved = jdk.into_resolved_java();
            "lockfile-pin".clone_into(&mut resolved.selection);
            resolved.pin_url = Some(artifact.url.clone());
            resolved.pin_sha512 = Some(artifact.sha512.to_ascii_lowercase());
            Ok(resolved)
        }
    }
}

/// Project builds and Prism require an exact Java major on unpinned targets.
/// Preserve that rule for legacy release locks while using the same exact SDK
/// catalog when present; reading the catalog never migrates lockfile contents.
pub(crate) fn resolve_exact_java_for_minecraft_dir(
    minecraft_dir: &Path,
    required_major: u32,
    explicit_java_home: Option<&Path>,
) -> eyre::Result<ResolvedJava> {
    if let Some(home) = explicit_java_home {
        let resolved = resolve_java(Some(home), required_major)?;
        if resolved.major_version != required_major {
            eyre::bail!(
                "Java {} is required for this Prism runtime, but --java-home resolved Java {}",
                required_major,
                resolved.major_version
            );
        }
        return Ok(resolved);
    }
    let lockfile_path = minecraft_dir.join("sfm-toolchain.lock.json");
    let input = fs::read_to_string(&lockfile_path)
        .wrap_err_with(|| format!("Failed to read {}", lockfile_path.display()))?;
    let pins = crate::toolchain_lockfile_schema::read_jdk_pins(&input)?;
    let Some(pins) = pins else {
        return resolve_exact_java(required_major);
    };
    let cache_root = crate::paths::CACHE_DIR
        .0
        .join("minecraft-toolchain")
        .join("jbrsdk");
    resolve_java_for_lockfile(None, Some(&pins), required_major, &cache_root, false)
}

pub(crate) fn host_jbrsdk_platform() -> Option<&'static str> {
    match (std::env::consts::OS, std::env::consts::ARCH) {
        ("windows", "x86_64") => Some("windows-x64"),
        ("windows", "aarch64") => Some("windows-aarch64"),
        ("linux", "x86_64") => Some("linux-x64"),
        ("linux", "aarch64") => Some("linux-aarch64"),
        ("macos", "x86_64") => Some("osx-x64"),
        ("macos", "aarch64") => Some("osx-aarch64"),
        _ => None,
    }
}

fn verify_pinned_runtime(jdk: &JdkInstallation, pin: &JdkPinV4) -> eyre::Result<()> {
    if jdk.major_version != pin.major || !pinned_runtime_identity_matches(&jdk.version_output, pin)
    {
        eyre::bail!(
            "Checksum-verified JBRSDK {} {} reports a different runtime identity: {}",
            pin.version,
            pin.build,
            jdk.version_output
        );
    }
    let javac = jdk.javac_executable.clone();
    let output = Command::new(&javac)
        .arg("-version")
        .output()
        .wrap_err_with(|| format!("Failed to run {} -version", javac.display()))?;
    let compiler_version = String::from_utf8_lossy(&output.stdout).trim().to_owned();
    if !output.status.success() || compiler_version != format!("javac {}", pin.version) {
        eyre::bail!(
            "Checksum-verified JBRSDK {} has a mismatched compiler: {}",
            pin.version,
            compiler_version
        );
    }
    Ok(())
}

fn pinned_runtime_identity_matches(version_output: &str, pin: &JdkPinV4) -> bool {
    let reported_version = version_output.split('"').nth(1);
    let reported_build = version_output
        .split_whitespace()
        .filter_map(|token| token.strip_prefix("JBR-"))
        .filter_map(|token| token.split_once('+'))
        .filter(|(version, _)| *version == pin.version)
        .filter_map(|(_, rest)| rest.split_once('-'))
        .map(|(_, rest)| rest.split('-').next().unwrap_or_default())
        .next();
    reported_version == Some(pin.version.as_str())
        && reported_build == Some(pin.build.trim_start_matches('b'))
}

#[instrument]
pub(crate) fn list_jdks() -> eyre::Result<Vec<JdkInstallation>> {
    let jdk_homes = discover_jdk_homes();
    let (home_results, path_result) = rayon::join(
        || {
            jdk_homes
                .into_par_iter()
                .map(|(home, source)| JdkInstallation::from_home(&home, source))
                .collect::<Vec<_>>()
        },
        JdkInstallation::from_path,
    );

    let mut jdks = Vec::with_capacity(home_results.len() + 1);
    for result in home_results.into_iter().chain(std::iter::once(path_result)) {
        match result {
            Ok(x) => jdks.push(x),
            Err(e) => warn!("Failed to read JDK: {e:?}"),
        }
    }

    Ok(dedup_jdks(jdks))
}

#[instrument]
pub(crate) fn resolve_java(
    explicit_java_home: Option<&Path>,
    required_major: u32,
) -> eyre::Result<ResolvedJava> {
    if let Some(home) = explicit_java_home {
        let jdk = JdkInstallation::from_home(home, "--java-home".to_string())?;
        ensure_jdk_meets_requirement(&jdk, required_major)?;
        let mut resolved = jdk.into_resolved_java();
        "explicit-java-home".clone_into(&mut resolved.selection);
        return Ok(resolved);
    }

    let jdks = list_jdks()?;
    if let Some(jdk) = select_jdk(&jdks, required_major) {
        return Ok(jdk.clone().into_resolved_java());
    }

    let discovered = if jdks.is_empty() {
        "none discovered".to_string()
    } else {
        jdks.iter()
            .map(|jdk| {
                let home = jdk
                    .home
                    .as_ref()
                    .map_or_else(|| "<PATH>".to_string(), |home| home.display().to_string());
                format!("Java {} at {home}", jdk.major_version)
            })
            .collect::<Vec<_>>()
            .join(", ")
    };
    eyre::bail!(
        "Java {} or newer is required for this clean-slate build, but no compatible JDK was found ({discovered})",
        required_major
    );
}

#[instrument]
pub(crate) fn resolve_exact_java(required_major: u32) -> eyre::Result<ResolvedJava> {
    let jdks = list_jdks()?;
    if let Some(jdk) = jdks.iter().find(|jdk| jdk.major_version == required_major) {
        return Ok(jdk.clone().into_resolved_java());
    }

    let discovered = if jdks.is_empty() {
        "none discovered".to_string()
    } else {
        jdks.iter()
            .map(|jdk| {
                let home = jdk
                    .home
                    .as_ref()
                    .map_or_else(|| "<PATH>".to_string(), |home| home.display().to_string());
                format!("Java {} at {home}", jdk.major_version)
            })
            .collect::<Vec<_>>()
            .join(", ")
    };
    eyre::bail!(
        "Java {} is required for this Prism runtime, but no exact matching JDK was found ({discovered})",
        required_major
    );
}

pub(crate) fn parse_java_major_version(version_output: &str) -> Option<u32> {
    let quoted = version_output.split('"').nth(1)?;
    let first = quoted.split('.').next()?;
    if first == "1" {
        quoted.split('.').nth(1)?.parse().ok()
    } else {
        first.parse().ok()
    }
}

#[instrument(level = "debug", skip_all, fields(jdks_count = jdks.len(), required_major))]
fn select_jdk(jdks: &[JdkInstallation], required_major: u32) -> Option<&JdkInstallation> {
    jdks.iter()
        .filter(|jdk| jdk.major_version >= required_major)
        .min_by(|left, right| compare_jdk_preference(left, right))
}

fn compare_jdk_preference(left: &JdkInstallation, right: &JdkInstallation) -> Ordering {
    right
        .is_jbr
        .cmp(&left.is_jbr)
        .then_with(|| left.major_version.cmp(&right.major_version))
        .then_with(|| {
            numeric_runtime_version(&right.version_output)
                .cmp(&numeric_runtime_version(&left.version_output))
        })
        .then_with(|| source_rank(left).cmp(&source_rank(right)))
        .then_with(|| left.home.cmp(&right.home))
        .then_with(|| left.java_executable.cmp(&right.java_executable))
}

fn numeric_runtime_version(output: &str) -> Vec<u32> {
    output
        .split('"')
        .nth(1)
        .unwrap_or_default()
        .split(|character: char| !character.is_ascii_digit())
        .filter_map(|part| part.parse().ok())
        .collect()
}

/// Reject JBR17 build families that predate the JDWP tag-map repair (JBR-6648).
/// Other runtimes can still provide standard, body-only redefinition.
pub(crate) fn ensure_hotswap_runtime(version_output: &str) -> eyre::Result<()> {
    let lower = version_output.to_ascii_lowercase();
    if !lower.contains("jbr") || parse_java_major_version(version_output) != Some(17) {
        return Ok(());
    }
    let build = jbr_build(&lower);
    if build.is_some_and(|build| build >= (1207, 6)) {
        return Ok(());
    }
    eyre::bail!(
        "This JBR17 build is not verified for repeated enhanced hotswap (JBR-6648). \
         Use an exact fixed build, such as JBRSDK 17.0.14 b1367.22, with --java-home. \
         Older b829/b1000/b1087 families remain affected even with a newer Java patch version. \
         Runtime: {version_output}"
    );
}

fn jbr_build(lower_version_output: &str) -> Option<(u32, u32)> {
    let vendor = lower_version_output
        .split_whitespace()
        .find(|token| token.starts_with("jbr-") || token.starts_with("jbrsdk-"))?;
    let build = vendor
        .split_once('+')?
        .1
        .split('-')
        .nth(1)?
        .trim_start_matches('b');
    let (family, revision) = build.split_once('.')?;
    Some((family.parse().ok()?, revision.parse().ok()?))
}

fn source_rank(jdk: &JdkInstallation) -> u8 {
    if jdk.source.contains(".jdks") {
        0
    } else if jdk.source == "JAVA_HOME" {
        1
    } else if jdk.source == "JDK_HOME" {
        2
    } else if jdk.source == "PATH" {
        9
    } else {
        5
    }
}

fn ensure_jdk_meets_requirement(jdk: &JdkInstallation, required_major: u32) -> eyre::Result<()> {
    if jdk.major_version < required_major {
        eyre::bail!(
            "Java {} or newer is required for this clean-slate build, but {} reports Java {}",
            required_major,
            jdk.java_executable.display(),
            jdk.major_version
        );
    }
    Ok(())
}

fn dedup_jdks(jdks: Vec<JdkInstallation>) -> Vec<JdkInstallation> {
    let mut seen = BTreeSet::new();
    let mut output = Vec::new();
    for jdk in jdks {
        let key = jdk
            .home
            .as_ref()
            .and_then(|home| canonicalize_existing(home).ok())
            .unwrap_or_else(|| jdk.java_executable.clone());
        if seen.insert(key) {
            output.push(jdk);
        }
    }
    output.sort_by(compare_jdk_preference);
    output
}

#[instrument]
fn discover_jdk_homes() -> Vec<(PathBuf, String)> {
    let mut homes = Vec::new();
    if let Some(user_profile) = env_path("USERPROFILE").or_else(|| env_path("HOME")) {
        push_child_directories(&mut homes, &user_profile.join(".jdks"), "user .jdks");
    }
    for variable in ["JAVA_HOME", "JDK_HOME"] {
        if let Some(path) = env_path(variable) {
            homes.push((path, variable.to_string()));
        }
    }

    if cfg!(windows) {
        for variable in ["ProgramFiles", "ProgramFiles(x86)"] {
            if let Some(root) = env_path(variable) {
                for directory in ["Java", "Eclipse Adoptium", "Microsoft", "JetBrains"] {
                    push_child_directories(
                        &mut homes,
                        &root.join(directory),
                        &format!("{variable}\\{directory}"),
                    );
                }
            }
        }
    }

    homes
}

fn push_child_directories(output: &mut Vec<(PathBuf, String)>, root: &Path, source: &str) {
    let Ok(entries) = std::fs::read_dir(root) else {
        return;
    };
    for entry in entries.flatten() {
        let Ok(file_type) = entry.file_type() else {
            continue;
        };
        if file_type.is_dir() || file_type.is_symlink() {
            let path = entry.path();
            if jdk_home_has_executables(&path) {
                output.push((path, source.to_string()));
            }
        }
    }
}

fn jdk_home_has_executables(home: &Path) -> bool {
    java_executable_for_home(home).is_file() && javac_executable_for_home(home).is_file()
}

fn env_path(name: &str) -> Option<PathBuf> {
    std::env::var_os(name)
        .filter(|value| !value.as_os_str().is_empty())
        .map(PathBuf::from)
}

impl JdkInstallation {
    #[instrument]
    fn from_home(home: &Path, source: String) -> eyre::Result<Self> {
        let runtime_executable = java_executable_for_home(home);
        let compiler_executable = javac_executable_for_home(home);
        if !runtime_executable.is_file() {
            eyre::bail!("{} does not exist", runtime_executable.display());
        }
        if !compiler_executable.is_file() {
            eyre::bail!("{} does not exist", compiler_executable.display());
        }
        Self::from_parts(
            Some(home.to_path_buf()),
            runtime_executable,
            compiler_executable,
            source,
        )
    }

    #[instrument(level = "debug")]
    fn from_path() -> eyre::Result<Self> {
        Self::from_parts(
            None,
            PathBuf::from(if cfg!(windows) { "java.exe" } else { "java" }),
            PathBuf::from(if cfg!(windows) { "javac.exe" } else { "javac" }),
            "PATH".to_string(),
        )
    }

    #[instrument]
    fn from_parts(
        home: Option<PathBuf>,
        runtime_executable: PathBuf,
        compiler_executable: PathBuf,
        source: String,
    ) -> eyre::Result<Self> {
        let output = Command::new(&runtime_executable)
            .arg("-version")
            .output()
            .wrap_err_with(|| format!("Failed to run {} -version", runtime_executable.display()))?;
        if !output.status.success() {
            eyre::bail!(
                "{} -version exited with {}",
                runtime_executable.display(),
                output.status
            );
        }

        let version_output = String::from_utf8_lossy(&output.stderr).trim().to_string();
        let major_version = parse_java_major_version(&version_output)
            .ok_or_else(|| eyre::eyre!("Could not parse Java version from: {version_output}"))?;
        let lower_home = home
            .as_ref()
            .map_or_else(String::new, |home| home.to_string_lossy().to_lowercase());
        let lower_output = version_output.to_lowercase();
        Ok(Self {
            home,
            java_executable: runtime_executable,
            javac_executable: compiler_executable,
            version_output,
            major_version,
            source,
            is_jbr: lower_home.contains("jbr") || lower_output.contains("jbr"),
        })
    }

    fn into_resolved_java(self) -> ResolvedJava {
        ResolvedJava {
            executable: self.java_executable,
            home: self.home,
            version_output: self.version_output,
            major_version: self.major_version,
            selection: "legacy-discovery".to_owned(),
            pin_url: None,
            pin_sha512: None,
        }
    }
}

fn java_executable_for_home(home: &Path) -> PathBuf {
    home.join("bin")
        .join(if cfg!(windows) { "java.exe" } else { "java" })
}

fn javac_executable_for_home(home: &Path) -> PathBuf {
    home.join("bin")
        .join(if cfg!(windows) { "javac.exe" } else { "javac" })
}

fn canonicalize_existing(path: &Path) -> eyre::Result<PathBuf> {
    if path.exists() {
        return dunce::canonicalize(path)
            .wrap_err_with(|| format!("Failed to canonicalize {}", path.display()));
    }
    Ok(path.to_path_buf())
}

#[cfg(test)]
mod tests {
    use super::JdkInstallation;
    use super::JdkSource;
    use super::parse_java_major_version;
    use super::push_child_directories;
    use super::select_jdk;
    use super::select_jdk_source;
    use crate::toolchain_lockfile_schema::version::v4::JdkArtifactV4;
    use crate::toolchain_lockfile_schema::version::v4::JdkPinV4;
    use std::fs;
    use std::path::Path;
    use std::path::PathBuf;

    #[test]
    fn parses_java_major_versions() {
        assert_eq!(
            parse_java_major_version("openjdk version \"17.0.19\" 2026-04-21 LTS"),
            Some(17)
        );
        assert_eq!(
            parse_java_major_version("openjdk version \"1.8.0_402\""),
            Some(8)
        );
        assert_eq!(
            parse_java_major_version("openjdk version \"25.0.3\" 2026-04-21"),
            Some(25)
        );
    }

    #[test]
    fn selects_lowest_compatible_jbr_before_non_jbr() {
        let jdks = vec![
            fake_jdk("ms-17", 17, false),
            fake_jdk("jbr-21", 21, true),
            fake_jdk("jbr-25", 25, true),
        ];
        assert_eq!(select_jdk(&jdks, 17).unwrap().major_version, 21);
        assert_eq!(select_jdk(&jdks, 25).unwrap().major_version, 25);
        assert!(select_jdk(&jdks, 26).is_none());
    }

    #[test]
    fn hotswap_rejects_jbr_6648_builds_not_just_old_java_patch_versions() {
        for (patch, build) in [(6, "829.9"), (10, "1207.2"), (12, "1087.25")] {
            let version = format!(
                "openjdk version \"17.0.{patch}\"\nOpenJDK Runtime Environment JBR-17.0.{patch}+1-{build}-nomod"
            );
            assert!(
                super::ensure_hotswap_runtime(&version)
                    .unwrap_err()
                    .to_string()
                    .contains("JBR-6648")
            );
        }
        for (patch, build) in [(10, "1207.6"), (12, "1207.37"), (14, "1367.22")] {
            let version = format!(
                "openjdk version \"17.0.{patch}\"\nOpenJDK Runtime Environment JBR-17.0.{patch}+1-{build}-nomod"
            );
            super::ensure_hotswap_runtime(&version).unwrap();
        }
        super::ensure_hotswap_runtime("openjdk version \"17.0.19\"\nMicrosoft OpenJDK").unwrap();
        assert!(
            super::ensure_hotswap_runtime("openjdk version \"17.0.10\"\nJBR unknown-build")
                .is_err()
        );
    }

    #[test]
    fn selects_newest_patch_within_the_preferred_java_major() {
        let mut old = fake_jdk("a-old-jbr", 17, true);
        old.version_output = "openjdk version \"17.0.6\"".to_string();
        let mut fixed = fake_jdk("z-fixed-jbr", 17, true);
        fixed.version_output = "openjdk version \"17.0.14\"".to_string();
        let jdks = vec![old, fixed, fake_jdk("jbr-21", 21, true)];
        assert_eq!(
            select_jdk(&jdks, 17).unwrap().home.as_ref().unwrap(),
            &PathBuf::from("z-fixed-jbr")
        );
    }

    #[test]
    fn child_discovery_ignores_directories_without_java_and_javac() {
        let root = tempfile::Builder::new()
            .prefix("jdk-discovery-test")
            .tempdir()
            .unwrap();
        let root_path = root.path();
        let jdk_home = root_path.join("temurin-17");
        let edge_home = root_path.join("Edge");
        fs::create_dir_all(jdk_home.join("bin")).unwrap();
        fs::create_dir_all(edge_home.join("bin")).unwrap();
        fs::write(super::java_executable_for_home(&jdk_home), "").unwrap();
        fs::write(super::javac_executable_for_home(&jdk_home), "").unwrap();
        fs::write(super::java_executable_for_home(&edge_home), "").unwrap();

        let mut output = Vec::new();
        push_child_directories(&mut output, root_path, "test root");

        assert_eq!(output, vec![(jdk_home, "test root".to_string())]);
    }

    #[test]
    fn pinned_policy_selects_exact_major_and_platform_without_floating() {
        let pins = vec![fake_pin(17), fake_pin(21), fake_pin(25)];
        let JdkSource::Pinned { pin, artifact } =
            select_jdk_source(None, Some(&pins), 21, "windows-x64").unwrap()
        else {
            panic!("expected the exact Java 21 pin");
        };
        assert_eq!(pin.major, 21);
        assert_eq!(artifact.platform, "windows-x64");
        assert!(
            select_jdk_source(None, Some(&pins), 22, "windows-x64")
                .unwrap_err()
                .to_string()
                .contains("No exact JBRSDK pin for Java 22")
        );
        assert!(
            select_jdk_source(None, Some(&pins), 21, "linux-x64")
                .unwrap_err()
                .to_string()
                .contains("No Java 21 JBRSDK artifact is pinned for platform `linux-x64`")
        );
    }

    #[test]
    fn explicit_java_home_precedes_pins_and_missing_catalog_uses_legacy_selection() {
        let pins = vec![fake_pin(17)];
        let explicit = Path::new("explicit-jdk");
        let JdkSource::Explicit(selected) =
            select_jdk_source(Some(explicit), Some(&pins), 25, "unsupported-platform").unwrap()
        else {
            panic!("expected the user's explicit Java home");
        };
        assert_eq!(selected, explicit);
        assert!(matches!(
            select_jdk_source(None, None, 17, "unsupported-platform").unwrap(),
            JdkSource::LegacyDiscovery
        ));
    }

    #[test]
    fn pinned_runtime_identity_rejects_different_patch_and_build() {
        let mut pin = fake_pin(17);
        pin.version = "17.0.14".to_owned();
        pin.build = "b1367.22".to_owned();
        let version = "openjdk version \"17.0.14\" 2025-01-21\nOpenJDK Runtime Environment JBR-17.0.14+1-1367.22-nomod";
        assert!(super::pinned_runtime_identity_matches(version, &pin));
        assert!(!super::pinned_runtime_identity_matches(
            &version.replace("1367.22", "1367.21"),
            &pin
        ));
        assert!(!super::pinned_runtime_identity_matches(
            &version.replace("17.0.14", "17.0.13"),
            &pin
        ));
    }

    fn fake_pin(major: u32) -> JdkPinV4 {
        JdkPinV4 {
            major,
            vendor: "JetBrains".to_owned(),
            version: format!("{major}.0.1"),
            build: "b1.1".to_owned(),
            flavor: "jbrsdk".to_owned(),
            artifacts: vec![JdkArtifactV4 {
                platform: "windows-x64".to_owned(),
                url: format!(
                    "https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-{major}.0.1-windows-x64-b1.1.tar.gz"
                ),
                sha512: "a".repeat(128),
            }],
        }
    }

    fn fake_jdk(name: &str, major_version: u32, is_jbr: bool) -> JdkInstallation {
        JdkInstallation {
            home: Some(PathBuf::from(name)),
            java_executable: PathBuf::from(name).join("bin/java"),
            javac_executable: PathBuf::from(name).join("bin/javac"),
            version_output: format!("openjdk version \"{major_version}.0.0\""),
            major_version,
            source: "test".to_string(),
            is_jbr,
        }
    }
}
