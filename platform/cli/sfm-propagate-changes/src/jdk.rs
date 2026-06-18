use eyre::Context;
use std::cmp::Ordering;
use std::collections::BTreeSet;
use std::path::Path;
use std::path::PathBuf;
use std::process::Command;
use std::thread;
use tracing::instrument;
use tracing::warn;

use crate::logging::set_tracy_thread_name;

#[derive(Clone, Debug)]
pub(crate) struct JdkInstallation {
    pub(crate) home: Option<PathBuf>,
    pub(crate) java_executable: PathBuf,
    pub(crate) javac_executable: PathBuf,
    pub(crate) version_output: String,
    pub(crate) major_version: u32,
    pub(crate) source: String,
    /// JetBrains Runtime (JBR) is preferred for development builds of SFM, so we track whether each discovered JDK is a JBR distribution.
    pub(crate) is_jbr: bool,
}

#[derive(Clone, Debug)]
pub(crate) struct ResolvedJava {
    pub(crate) executable: PathBuf,
    pub(crate) home: Option<PathBuf>,
    pub(crate) version_output: String,
    pub(crate) major_version: u32,
}

#[instrument]
pub(crate) fn list_jdks() -> eyre::Result<Vec<JdkInstallation>> {
    let mut handles = Vec::new();
    for (index, jdk) in discover_jdk_homes().into_iter().enumerate() {
        let (home, source) = jdk;
        let thread_name = format!("Java Discovery Worker {index} ({source})");
        handles.push(
            thread::Builder::new()
                .name(thread_name.clone())
                .spawn(move || {
                    set_tracy_thread_name(&thread_name);
                    JdkInstallation::from_home(&home, source)
                })?,
        );
    }
    let thread_name = "Java Discovery Worker PATH".to_string();
    handles.push(
        thread::Builder::new()
            .name(thread_name.clone())
            .spawn(move || {
                set_tracy_thread_name(&thread_name);
                JdkInstallation::from_path()
            })?,
    );

    let mut jdks = Vec::with_capacity(handles.len());
    for handle in handles {
        match handle.join().map_err(|panic| {
            eyre::eyre!("JDK discovery worker panicked: {}", panic_message(&panic))
        })? {
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
        return Ok(jdk.into_resolved_java());
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
        .then_with(|| source_rank(left).cmp(&source_rank(right)))
        .then_with(|| left.home.cmp(&right.home))
        .then_with(|| left.java_executable.cmp(&right.java_executable))
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

fn panic_message(panic: &(dyn std::any::Any + Send)) -> String {
    if let Some(message) = panic.downcast_ref::<&str>() {
        return (*message).to_string();
    }
    if let Some(message) = panic.downcast_ref::<String>() {
        return message.clone();
    }
    "<non-string panic payload>".to_string()
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
    use super::parse_java_major_version;
    use super::push_child_directories;
    use super::select_jdk;
    use std::fs;
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
