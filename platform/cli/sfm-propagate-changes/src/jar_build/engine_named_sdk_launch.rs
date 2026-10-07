// Checked launch spelling for explicit named-native SDKs only.
// The legacy JDK resolver/discovery and frozen dependency pins are unchanged.
// This is not a JVM, DLL, process, or network sandbox.

const NAMED_SDK_JVM_OVERRIDE_NAMES: [&str; 3] =
    ["JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS"];

fn refuse_named_sdk_jvm_overrides(
    mut value: impl FnMut(&str) -> Option<std::ffi::OsString>,
) -> eyre::Result<()> {
    for name in NAMED_SDK_JVM_OVERRIDE_NAMES {
        eyre::ensure!(
            value(name).is_none_or(|value| value.is_empty()),
            "named SDK version probe refuses nonempty {name}; its value is not logged or erased"
        );
    }
    Ok(())
}

fn normal_named_sdk_spelling(canonical: &Path) -> eyre::Result<PathBuf> {
    let normal = dunce::simplified(canonical).to_path_buf();
    require_named_sdk_launch_path(&normal)?;
    Ok(normal)
}

fn require_named_sdk_launch_path(path: &Path) -> eyre::Result<()> {
    eyre::ensure!(
        path.is_absolute()
            && !path.components().any(|part| matches!(
                part,
                std::path::Component::ParentDir | std::path::Component::CurDir
            )),
        "named SDK launch path must be absolute without traversal"
    );
    let text = path
        .to_str()
        .ok_or_else(|| eyre::eyre!("named SDK launch path must be Unicode"))?;
    eyre::ensure!(
        !text.contains(['\r', '\n', '\0']),
        "named SDK launch path contains a control character"
    );
    #[cfg(windows)]
    require_named_sdk_windows_launch_path(path, text)?;
    Ok(())
}

#[cfg(windows)]
fn require_named_sdk_windows_launch_path(path: &Path, text: &str) -> eyre::Result<()> {
    eyre::ensure!(
        matches!(path.components().next(), Some(std::path::Component::Prefix(prefix))
            if matches!(prefix.kind(), std::path::Prefix::Disk(_))),
        "named SDK cannot be expressed as an unambiguous normal local disk path; no prefix stripping, SDK replacement or fallback is permitted"
    );
    eyre::ensure!(
        text.encode_utf16().count() < 260,
        "named SDK launch path is not shorter than 260 UTF-16 units; no SDK replacement or fallback is permitted"
    );
    Ok(())
}

struct NamedSdkSpellingInput {
    canonical_path: PathBuf,
    launch_path: PathBuf,
    held: File,
    identity: crate::file_identity::FileIdentity,
    bytes: u64,
    modified: SystemTime,
    digest: blake3::Hash,
}

impl NamedSdkSpellingInput {
    fn capture(
        canonical_home: &Path,
        launch_home: &Path,
        relative: &str,
        cancellation: &CancellationToken,
    ) -> eyre::Result<Self> {
        use crate::source_projection::candidate_lock::checked_file;
        cancellation.bail_if_cancelled()?;
        let canonical_path = checked_file(canonical_home, relative)?;
        let launch_path = checked_file(launch_home, relative)?;
        require_named_sdk_launch_path(&launch_path)?;
        eyre::ensure!(
            fs::canonicalize(&launch_path)? == fs::canonicalize(&canonical_path)?,
            "named SDK path spelling changed the selected input"
        );
        let held = File::open(&canonical_path)?;
        let metadata = held.metadata()?;
        eyre::ensure!(metadata.is_file(), "named SDK input is not a regular file");
        let identity = crate::file_identity::file_identity(&held)?;
        let bytes = metadata.len();
        let modified = metadata.modified()?;
        let digest = stream_named_sdk_spelling_hash(&held, cancellation)?;
        let captured = Self {
            canonical_path,
            launch_path,
            held,
            identity,
            bytes,
            modified,
            digest,
        };
        captured.require_same_open_inputs()?;
        Ok(captured)
    }

    fn require_same_open_inputs(&self) -> eyre::Result<()> {
        for path in [&self.canonical_path, &self.launch_path] {
            let reopened = File::open(path)?;
            eyre::ensure!(
                crate::file_identity::file_identity(&reopened)? == self.identity
                    && named_sdk_spelling_metadata_matches(
                        &reopened.metadata()?,
                        self.bytes,
                        self.modified,
                    ),
                "named SDK input identity or metadata changed between canonical and launch spelling"
            );
        }
        eyre::ensure!(
            crate::file_identity::file_identity(&self.held)? == self.identity
                && named_sdk_spelling_metadata_matches(
                    &self.held.metadata()?,
                    self.bytes,
                    self.modified,
                ),
            "named SDK held input changed during spelling verification"
        );
        Ok(())
    }

    fn recheck(&self, cancellation: &CancellationToken) -> eyre::Result<()> {
        cancellation.bail_if_cancelled()?;
        self.require_same_open_inputs()?;
        eyre::ensure!(
            stream_named_sdk_spelling_hash(&self.held, cancellation)? == self.digest,
            "named SDK input bytes changed after spelling verification"
        );
        self.require_same_open_inputs()
    }
}

fn named_sdk_spelling_metadata_matches(
    metadata: &fs::Metadata,
    bytes: u64,
    modified: SystemTime,
) -> bool {
    metadata.is_file() && metadata.len() == bytes && metadata.modified().ok() == Some(modified)
}

fn stream_named_sdk_spelling_hash(
    file: &File,
    cancellation: &CancellationToken,
) -> eyre::Result<blake3::Hash> {
    let mut borrowed = file;
    borrowed.seek(std::io::SeekFrom::Start(0))?;
    let mut hasher = blake3::Hasher::new();
    let mut buffer = [0_u8; 16_384];
    loop {
        cancellation.bail_if_cancelled()?;
        let bytes = borrowed.read(&mut buffer)?;
        if bytes == 0 {
            break;
        }
        hasher.update(&buffer[..bytes]);
    }
    cancellation.bail_if_cancelled()?;
    Ok(hasher.finalize())
}

struct NamedSdkLaunchSpelling {
    canonical_home: PathBuf,
    launch_home: PathBuf,
    inputs: Vec<NamedSdkSpellingInput>,
}

impl NamedSdkLaunchSpelling {
    fn capture(home: &Path, cancellation: &CancellationToken) -> eyre::Result<Self> {
        use crate::source_projection::candidate_lock::checked_directory;
        cancellation.bail_if_cancelled()?;
        let canonical_home = checked_directory(home)?;
        let launch_home = normal_named_sdk_spelling(&canonical_home)?;
        eyre::ensure!(
            checked_directory(&launch_home)? == canonical_home,
            "named SDK normal home does not resolve to its checked canonical home"
        );
        let mut inputs = Vec::with_capacity(4);
        for relative in [
            if cfg!(windows) {
                "bin/java.exe"
            } else {
                "bin/java"
            },
            if cfg!(windows) {
                "bin/javac.exe"
            } else {
                "bin/javac"
            },
            "release",
            "lib/modules",
        ] {
            inputs.push(NamedSdkSpellingInput::capture(
                &canonical_home,
                &launch_home,
                relative,
                cancellation,
            )?);
        }
        let captured = Self {
            canonical_home,
            launch_home,
            inputs,
        };
        captured.recheck(cancellation)?;
        Ok(captured)
    }

    fn recheck(&self, cancellation: &CancellationToken) -> eyre::Result<()> {
        use crate::source_projection::candidate_lock::checked_directory;
        use crate::source_projection::candidate_lock::checked_file;
        cancellation.bail_if_cancelled()?;
        eyre::ensure!(
            checked_directory(&self.canonical_home)? == self.canonical_home
                && checked_directory(&self.launch_home)? == self.canonical_home,
            "named SDK home changed after spelling verification"
        );
        for input in &self.inputs {
            let relative = input.canonical_path.strip_prefix(&self.canonical_home)?;
            let relative = relative.to_string_lossy().replace('\\', "/");
            checked_file(&self.canonical_home, &relative)?;
            checked_file(&self.launch_home, &relative)?;
            input.recheck(cancellation)?;
        }
        Ok(())
    }

    fn require_resolved_paths(&self, resolved: &crate::jdk::ResolvedJava) -> eyre::Result<()> {
        eyre::ensure!(
            resolved.home.as_deref() == Some(self.launch_home.as_path())
                && resolved.executable == self.inputs[0].launch_path,
            "named SDK resolver changed the verified home or executable; no fallback is permitted"
        );
        Ok(())
    }
}

// The resolver callback cannot grant authority or replace any checks. It is
// called only after all existing SDK inputs are checked, and both spellings
// and their held identities/bytes are checked again before returning.
fn resolve_named_java_with_verified_spelling(
    home: &Path,
    cancellation: &CancellationToken,
    resolve: impl FnOnce(&Path) -> eyre::Result<crate::jdk::ResolvedJava>,
) -> eyre::Result<crate::jdk::ResolvedJava> {
    refuse_named_sdk_jvm_overrides(|name| std::env::var_os(name))?;
    let checked = NamedSdkLaunchSpelling::capture(home, cancellation)?;
    cancellation.bail_if_cancelled()?;
    let resolved = resolve(&checked.launch_home)?;
    checked.recheck(cancellation)?;
    checked.require_resolved_paths(&resolved)?;
    Ok(resolved)
}

#[cfg(test)]
mod named_sdk_launch_spelling_tests {
    use super::*;

    fn fixture() -> eyre::Result<(tempfile::TempDir, PathBuf)> {
        let directory = tempfile::tempdir()?;
        let home = directory.path().join("checked sdk with spaces");
        fs::create_dir_all(home.join("bin"))?;
        fs::create_dir_all(home.join("lib"))?;
        for relative in [
            if cfg!(windows) {
                "bin/java.exe"
            } else {
                "bin/java"
            },
            if cfg!(windows) {
                "bin/javac.exe"
            } else {
                "bin/javac"
            },
            "release",
            "lib/modules",
        ] {
            fs::write(home.join(relative), b"not-an-executable-fixture")?;
        }
        Ok((directory, home))
    }

    fn synthetic_resolution(home: &Path) -> crate::jdk::ResolvedJava {
        crate::jdk::ResolvedJava {
            executable: home
                .join("bin")
                .join(if cfg!(windows) { "java.exe" } else { "java" }),
            home: Some(home.to_path_buf()),
            version_output: "synthetic-no-process-started".to_owned(),
            major_version: 17,
            selection: "explicit-java-home".to_owned(),
            pin_url: None,
            pin_sha512: None,
        }
    }

    #[test]
    fn canonical_and_launch_spellings_identify_the_same_four_original_inputs() -> eyre::Result<()> {
        let (_directory, home) = fixture()?;
        let canonical = fs::canonicalize(&home)?;
        let checked = NamedSdkLaunchSpelling::capture(&canonical, &CancellationToken::new())?;
        assert_eq!(checked.canonical_home, canonical);
        assert_eq!(fs::canonicalize(&checked.launch_home)?, canonical);
        assert_eq!(checked.inputs.len(), 4);
        for input in &checked.inputs {
            assert_eq!(
                crate::file_identity::file_identity(&File::open(&input.canonical_path)?)?,
                crate::file_identity::file_identity(&File::open(&input.launch_path)?)?,
            );
            assert_eq!(
                fs::read(&input.canonical_path)?,
                fs::read(&input.launch_path)?
            );
        }
        #[cfg(windows)]
        assert!(!checked.launch_home.to_string_lossy().starts_with(r"\\?\"));
        checked.recheck(&CancellationToken::new())
    }

    #[test]
    fn selected_runtime_fields_and_explicit_override_pin_semantics_are_preserved()
    -> eyre::Result<()> {
        let (_directory, home) = fixture()?;
        let checked = NamedSdkLaunchSpelling::capture(&home, &CancellationToken::new())?;
        let expected = synthetic_resolution(&checked.launch_home);
        let result = resolve_named_java_with_verified_spelling(
            &home,
            &CancellationToken::new(),
            |actual_home| {
                assert_eq!(actual_home, checked.launch_home);
                Ok(synthetic_resolution(actual_home))
            },
        )?;
        assert_eq!(result.executable, expected.executable);
        assert_eq!(result.home, expected.home);
        assert_eq!(result.version_output, expected.version_output);
        assert_eq!(result.major_version, expected.major_version);
        assert_eq!(result.selection, expected.selection);
        assert_eq!(result.pin_url, expected.pin_url);
        assert_eq!(result.pin_sha512, expected.pin_sha512);
        Ok(())
    }

    #[test]
    fn missing_input_is_refused_before_the_resolver_callback() -> eyre::Result<()> {
        let directory = tempfile::tempdir()?;
        let home = directory.path().join("missing sdk");
        let called = std::cell::Cell::new(false);
        let result = resolve_named_java_with_verified_spelling(
            &home,
            &CancellationToken::new(),
            |actual_home| {
                called.set(true);
                Ok(synthetic_resolution(actual_home))
            },
        );
        assert!(result.is_err());
        assert!(!called.get());
        assert_eq!(fs::read_dir(directory.path())?.count(), 0);
        Ok(())
    }

    #[test]
    fn same_size_input_change_during_probe_is_refused_without_repair() -> eyre::Result<()> {
        let (_directory, home) = fixture()?;
        let path = home.join("lib/modules");
        let original = fs::read(&path)?;
        let changed = vec![b'x'; original.len()];
        let result = resolve_named_java_with_verified_spelling(
            &home,
            &CancellationToken::new(),
            |actual_home| {
                fs::write(&path, &changed)?;
                Ok(synthetic_resolution(actual_home))
            },
        );
        assert!(result.is_err());
        assert_eq!(fs::read(path)?, changed);
        Ok(())
    }

    #[test]
    fn equal_byte_replacement_during_probe_is_refused_by_file_identity() -> eyre::Result<()> {
        let (_directory, home) = fixture()?;
        let path = home.join("release");
        let retained = home.join("retained-release");
        let original = fs::read(&path)?;
        let result = resolve_named_java_with_verified_spelling(
            &home,
            &CancellationToken::new(),
            |actual_home| {
                fs::rename(&path, &retained)?;
                fs::write(&path, &original)?;
                Ok(synthetic_resolution(actual_home))
            },
        );
        assert!(result.is_err());
        assert_eq!(fs::read(&path)?, original);
        assert_eq!(fs::read(retained)?, original);
        Ok(())
    }

    #[test]
    fn resolver_cannot_replace_the_checked_sdk_home_or_runtime_path() -> eyre::Result<()> {
        let (_directory, home) = fixture()?;
        for replace_home in [false, true] {
            let result = resolve_named_java_with_verified_spelling(
                &home,
                &CancellationToken::new(),
                |actual_home| {
                    let mut resolved = synthetic_resolution(actual_home);
                    if replace_home {
                        resolved.home = Some(actual_home.join("different sdk"));
                    } else {
                        resolved.executable = actual_home.join("bin/different-runtime");
                    }
                    Ok(resolved)
                },
            );
            assert!(result.is_err());
        }
        Ok(())
    }

    #[test]
    fn cancellation_is_observed_before_the_resolver_callback() -> eyre::Result<()> {
        let (_directory, home) = fixture()?;
        let cancellation = CancellationToken::new();
        cancellation.request_cancel("synthetic cancellation before any version probe");
        let called = std::cell::Cell::new(false);
        let result =
            resolve_named_java_with_verified_spelling(&home, &cancellation, |actual_home| {
                called.set(true);
                Ok(synthetic_resolution(actual_home))
            });
        assert!(result.is_err());
        assert!(!called.get());
        Ok(())
    }

    #[test]
    fn jvm_override_refusal_reports_only_the_name_without_mutating_environment() {
        for name in NAMED_SDK_JVM_OVERRIDE_NAMES {
            let error = refuse_named_sdk_jvm_overrides(|candidate| {
                (candidate == name)
                    .then(|| std::ffi::OsString::from("sensitive-value-not-for-logs"))
            })
            .unwrap_err();
            assert!(error.to_string().contains(name));
            assert!(!error.to_string().contains("sensitive-value-not-for-logs"));
        }
        refuse_named_sdk_jvm_overrides(|_| None).unwrap();
        refuse_named_sdk_jvm_overrides(|_| Some(std::ffi::OsString::new())).unwrap();
    }

    #[cfg(windows)]
    #[test]
    fn unsafe_verbatim_or_long_paths_are_not_stripped_or_substituted() {
        for path in [
            r"\\?\C:\NUL",
            r"\\?\UNC\server\share\sdk",
            r"\\?\GLOBALROOT\Device\path\sdk",
            r"\\?\C:\sdk with trailing space ",
        ] {
            assert!(
                normal_named_sdk_spelling(Path::new(path)).is_err(),
                "{path}"
            );
        }
        let long = format!(r"C:\{}\bin\java.exe", "x".repeat(260));
        assert!(require_named_sdk_launch_path(Path::new(&long)).is_err());
        require_named_sdk_launch_path(Path::new(r"C:\sdk with spaces\bin\java.exe")).unwrap();
    }
}
