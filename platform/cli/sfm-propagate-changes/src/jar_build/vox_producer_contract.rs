//! Exact declaration and host-execution policy for the retained Vox producer.
//! Kept dependency-free so this policy can also be tested without a CLI rebuild.

use std::collections::BTreeMap;
use std::ffi::OsString;
use std::path::Path;
use std::process::Command;

pub(crate) const COORDINATE: &str = "org.facet:teamy-vox-java:0.10.0-rc.7";
pub(crate) const REMOTE: &str = "https://github.com/TeamDman/vox";
pub(crate) const COMMIT: &str = "5628c00d9f3816199f324a9ccc081de8a3a1b1bc";
pub(crate) const LEGACY_KEY: &str = "VOX_JAVA_BUILD_JDK";
pub(crate) const WINDOWS_KEY: &str = "VOX_JAVA_BUILD_JDK_WINDOWS_X64";
pub(crate) const LINUX_KEY: &str = "VOX_JAVA_BUILD_JDK_LINUX_X64";
pub(crate) const WINDOWS_RUNTIME: &str = "JetBrains s.r.o.|21.0.11+1-b1163.116";
pub(crate) const LINUX_RUNTIME: &str = "JetBrains s.r.o.|21.0.11+10-b1163.116";
const TASKS: [&str; 6] = [
    "run",
    "--locked",
    "--package",
    "teamy-vox-xtask",
    "--",
    "package-java",
];
const OUTPUT: &str = "java/target/teamy-vox-java-0.10.0-rc.7.jar";
pub(crate) const AMBIENT_CONTROLS: [&str; 13] = [
    "JAVA_TOOL_OPTIONS",
    "JDK_JAVA_OPTIONS",
    "_JAVA_OPTIONS",
    "JDK_JAVAC_OPTIONS",
    "JDK_ALTERNATE_VM",
    "CLASSPATH",
    "LD_PRELOAD",
    "LD_AUDIT",
    "LD_LIBRARY_PATH",
    "DYLD_INSERT_LIBRARIES",
    "DYLD_LIBRARY_PATH",
    "VOX_JAVA_TRUST_STORE",
    "VOX_JAVA_TRUST_SHA256",
];

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(crate) enum Variant {
    LegacyWindows,
    PlatformDeclared,
}

impl Variant {
    pub(crate) fn name(self) -> &'static str {
        match self {
            Self::LegacyWindows => "legacy-windows@1",
            Self::PlatformDeclared => "platform-declared@1",
        }
    }
}

#[derive(Clone, Copy, Debug)]
pub(crate) struct Selection {
    pub platform: &'static str,
    pub runtime: &'static str,
    pub archive_url: &'static str,
    pub archive_sha512: &'static str,
    pub variant: Variant,
}

pub(crate) struct Recipe<'a> {
    pub coordinate: &'a str,
    pub remote: &'a str,
    pub commit: &'a str,
    pub tasks: &'a [String],
    pub output: &'a Path,
    pub environment: &'a BTreeMap<String, String>,
}

#[derive(Clone, Copy)]
pub(crate) struct PinIdentity<'a> {
    pub major: u32,
    pub vendor: &'a str,
    pub version: &'a str,
    pub build: &'a str,
    pub flavor: &'a str,
    pub platform: &'a str,
    pub url: &'a str,
    pub sha512: &'a str,
}

/// Artifact consumption is host-independent; this does not authorize a launch.
pub(crate) fn declared_variant(
    environment: &BTreeMap<String, String>,
) -> Result<Variant, &'static str> {
    if environment.get(LEGACY_KEY).map(String::as_str) != Some(WINDOWS_RUNTIME) {
        return Err("Vox recipe lost its exact historical Windows SDK reference");
    }
    if environment.len() == 1 {
        return Ok(Variant::LegacyWindows);
    }
    if environment.len() == 3
        && environment.get(WINDOWS_KEY).map(String::as_str) == Some(WINDOWS_RUNTIME)
        && environment.get(LINUX_KEY).map(String::as_str) == Some(LINUX_RUNTIME)
    {
        return Ok(Variant::PlatformDeclared);
    }
    Err("Vox recipe requires either the exact legacy map or the complete exact platform map")
}

pub(crate) fn needs_contract(
    coordinate: &str,
    environment: &BTreeMap<String, String>,
) -> Result<bool, &'static str> {
    if coordinate.starts_with("org.facet:teamy-vox-java:") {
        return Ok(true);
    }
    if environment.keys().any(|key| key.starts_with("VOX_JAVA_")) {
        return Err(
            "Reserved Vox producer controls cannot be attached to an unrelated Cargo recipe",
        );
    }
    Ok(false)
}

pub(crate) fn require_dispatch(
    coordinate: &str,
    environment: &BTreeMap<String, String>,
    cargo: bool,
) -> Result<(), &'static str> {
    if needs_contract(coordinate, environment)? && !cargo {
        return Err("Protected Vox producer requires the exact Cargo build system before dispatch");
    }
    Ok(())
}

pub(crate) fn select(platform: &str, recipe: &Recipe<'_>) -> Result<Selection, &'static str> {
    if recipe.coordinate != COORDINATE
        || recipe.remote != REMOTE
        || recipe.commit != COMMIT
        || !recipe.tasks.iter().map(String::as_str).eq(TASKS)
        || recipe
            .output
            .to_str()
            .map(|path| path.replace('\\', "/"))
            .as_deref()
            != Some(OUTPUT)
    {
        return Err(
            "Vox source, coordinate, tasks or output differ from the reviewed pinned producer",
        );
    }
    let variant = declared_variant(recipe.environment)?;
    let mut selected = expected(platform)?;
    if variant == Variant::LegacyWindows && platform != "windows-x64" {
        return Err(
            "Legacy Vox recipe declares only the Windows runtime; Linux requires the explicit platform recipe",
        );
    }
    selected.variant = variant;
    Ok(selected)
}

pub(crate) fn expected(platform: &str) -> Result<Selection, &'static str> {
    let (platform, runtime, archive_url, archive_sha512) = match platform {
        "windows-x64" => (
            "windows-x64",
            WINDOWS_RUNTIME,
            "https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-21.0.11-windows-x64-b1163.116.zip",
            "457c15d931bb5055ae97574fc7d899aa70917c266d4b40a1c50d949ff1d2b431eb95ad078b2038bd94561fbd57889803f867dd9c295868fbb0c3c870f1f4d1d3",
        ),
        "linux-x64" => (
            "linux-x64",
            LINUX_RUNTIME,
            "https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-21.0.11-linux-x64-b1163.116.tar.gz",
            "13d0aab4bcfa9e4f77a696b97d3998f7e82e22f7f1362d8ce97f0d50d69dddd9331a66ada3fe92c04e544795fc247bcbaf586ad0415df4eda5e0e0505753888f",
        ),
        _ => return Err("Vox producer has no reviewed SDK runtime/archive for this platform"),
    };
    Ok(Selection {
        platform,
        runtime,
        archive_url,
        archive_sha512,
        variant: Variant::PlatformDeclared,
    })
}

pub(crate) fn verify_pin(selected: Selection, pin: &PinIdentity<'_>) -> Result<(), &'static str> {
    if pin.major != 21
        || pin.vendor != "JetBrains"
        || pin.version != "21.0.11"
        || pin.build != "b1163.116"
        || pin.flavor != "jbrsdk"
        || pin.platform != selected.platform
        || pin.url != selected.archive_url
        || !pin.sha512.eq_ignore_ascii_case(selected.archive_sha512)
    {
        return Err(
            "Vox SDK platform, exact archive hash/URL or vendor/version/build pin mismatch",
        );
    }
    Ok(())
}

fn release_property<'a>(release: &'a str, name: &str) -> Result<&'a str, &'static str> {
    let prefix = format!("{name}=");
    let mut values = release
        .lines()
        .filter_map(|line| line.strip_prefix(&prefix));
    let value = values
        .next()
        .ok_or("Pinned Vox SDK release property is missing")?;
    if values.next().is_some() {
        return Err("Pinned Vox SDK release property is duplicated");
    }
    value
        .strip_prefix('"')
        .and_then(|v| v.strip_suffix('"'))
        .ok_or("Pinned Vox SDK release property is malformed")
}

pub(crate) fn verify_runtime(selected: Selection, release: &str) -> Result<(), &'static str> {
    let actual = format!(
        "{}|{}",
        release_property(release, "IMPLEMENTOR")?,
        release_property(release, "JAVA_RUNTIME_VERSION")?
    );
    if actual != selected.runtime {
        return Err("Pinned Vox SDK reports the wrong exact platform runtime");
    }
    Ok(())
}

pub(crate) fn unexpected_ambient(
    mut value: impl FnMut(&str) -> Option<OsString>,
) -> Option<&'static str> {
    AMBIENT_CONTROLS
        .into_iter()
        .find(|name| value(name).is_some())
}

/// The actual pinned xtask has no explicit per-tool trust argument mechanism.
/// Never export ignored trust variables or fall back to a banner-producing JTO.
pub(crate) fn require_supported_trust(external_trust: bool) -> Result<(), &'static str> {
    if external_trust {
        return Err(
            "Pinned Vox producer 5628c00d lacks explicit per-tool Java trust support; JAVA_TOOL_OPTIONS violates its strict jdeps stderr contract. A reviewed Vox change and source pin are required; no producer was launched",
        );
    }
    Ok(())
}

/// Command construction only: the caller must still hold/recheck its SDK image.
pub(crate) fn configure_child(
    selected: Selection,
    home: &Path,
    command: &mut Command,
) -> Result<(), &'static str> {
    if command.get_envs().any(|(name, value)| {
        value.is_some()
            && AMBIENT_CONTROLS
                .iter()
                .any(|blocked| name == std::ffi::OsStr::new(blocked))
    }) {
        return Err("Vox child already contains an unreviewed JVM, loader or trust override");
    }
    command
        .env_remove(WINDOWS_KEY)
        .env_remove(LINUX_KEY)
        .env("JAVA_HOME", home)
        .env(LEGACY_KEY, selected.runtime);
    for name in AMBIENT_CONTROLS {
        command.env_remove(name);
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn environment(platforms: bool) -> BTreeMap<String, String> {
        let mut result = BTreeMap::from([(LEGACY_KEY.to_owned(), WINDOWS_RUNTIME.to_owned())]);
        if platforms {
            result.insert(WINDOWS_KEY.to_owned(), WINDOWS_RUNTIME.to_owned());
            result.insert(LINUX_KEY.to_owned(), LINUX_RUNTIME.to_owned());
        }
        result
    }

    fn recipe<'a>(tasks: &'a [String], env: &'a BTreeMap<String, String>) -> Recipe<'a> {
        Recipe {
            coordinate: COORDINATE,
            remote: REMOTE,
            commit: COMMIT,
            tasks,
            output: Path::new(OUTPUT),
            environment: env,
        }
    }

    #[test]
    fn explicit_platforms_select_exact_runtimes_without_changing_declarations() {
        let env = environment(true);
        let before = env.clone();
        let tasks = TASKS.map(str::to_owned);
        for (platform, runtime) in [
            ("windows-x64", WINDOWS_RUNTIME),
            ("linux-x64", LINUX_RUNTIME),
        ] {
            let selection = select(platform, &recipe(&tasks, &env)).unwrap();
            assert_eq!(selection.runtime, runtime);
            assert_eq!(selection.variant, Variant::PlatformDeclared);
            assert_eq!(selection.variant.name(), "platform-declared@1");
        }
        assert_eq!(env, before);
        assert!(select("linux-aarch64", &recipe(&tasks, &env)).is_err());
        let mut portable = recipe(&tasks, &env);
        portable.output = Path::new("java\\target\\teamy-vox-java-0.10.0-rc.7.jar");
        assert!(select("linux-x64", &portable).is_ok());
        portable.output = Path::new("java//target/teamy-vox-java-0.10.0-rc.7.jar");
        assert!(select("linux-x64", &portable).is_err());
    }

    #[test]
    fn historical_windows_is_unchanged_and_not_reinterpreted_as_linux() {
        let env = environment(false);
        let tasks = TASKS.map(str::to_owned);
        assert_eq!(declared_variant(&env), Ok(Variant::LegacyWindows));
        assert_eq!(
            select("windows-x64", &recipe(&tasks, &env))
                .unwrap()
                .runtime,
            WINDOWS_RUNTIME
        );
        assert!(select("linux-x64", &recipe(&tasks, &env)).is_err());
    }

    #[test]
    fn declarations_refuse_missing_partial_extra_and_cross_platform_values() {
        for changed in 0..6 {
            let mut env = environment(true);
            match changed {
                0 => {
                    env.remove(LEGACY_KEY);
                }
                1 => {
                    env.remove(LINUX_KEY);
                }
                2 => {
                    env.insert(LINUX_KEY.to_owned(), WINDOWS_RUNTIME.to_owned());
                }
                3 => {
                    env.insert(WINDOWS_KEY.to_owned(), LINUX_RUNTIME.to_owned());
                }
                4 => {
                    env.insert("JAVA_HOME".to_owned(), "unreviewed".to_owned());
                }
                _ => {
                    env.insert(LEGACY_KEY.to_owned(), LINUX_RUNTIME.to_owned());
                }
            }
            assert!(declared_variant(&env).is_err(), "mutation {changed}");
        }
    }

    #[test]
    fn protected_coordinate_cannot_downgrade_to_generic_cargo() {
        assert!(needs_contract(COORDINATE, &BTreeMap::new()).unwrap());
        let tasks = TASKS.map(str::to_owned);
        assert!(select("windows-x64", &recipe(&tasks, &BTreeMap::new())).is_err());
        assert!(needs_contract("other:artifact:1", &environment(false)).is_err());
        assert!(!needs_contract("other:artifact:1", &BTreeMap::new()).unwrap());
        let env = environment(true);
        let mut r = recipe(&tasks, &env);
        r.commit = "1315e4441da7512dd35579387df5af3c8ca2c9b0";
        assert!(select("linux-x64", &r).is_err());
        r.commit = COMMIT;
        r.remote = "https://unreviewed.invalid/vox";
        assert!(select("linux-x64", &r).is_err());
        r.remote = REMOTE;
        r.output = Path::new("../java/target/teamy-vox-java-0.10.0-rc.7.jar");
        assert!(select("linux-x64", &r).is_err());
    }

    #[test]
    fn protected_dispatch_refuses_gradle_before_effects_even_without_marker() {
        assert!(require_dispatch(COORDINATE, &BTreeMap::new(), false).is_err());
        assert!(require_dispatch(COORDINATE, &environment(true), false).is_err());
        assert!(require_dispatch(COORDINATE, &environment(true), true).is_ok());
        assert!(require_dispatch("other:artifact:1", &environment(false), false).is_err());
        assert!(require_dispatch("other:artifact:1", &BTreeMap::new(), false).is_ok());
    }

    #[test]
    fn archive_runtime_and_platform_form_one_exact_identity() {
        for platform in ["windows-x64", "linux-x64"] {
            let selected = expected(platform).unwrap();
            let mut pin = PinIdentity {
                major: 21,
                vendor: "JetBrains",
                version: "21.0.11",
                build: "b1163.116",
                flavor: "jbrsdk",
                platform,
                url: selected.archive_url,
                sha512: selected.archive_sha512,
            };
            verify_pin(selected, &pin).unwrap();
            pin.sha512 = "0000";
            assert!(verify_pin(selected, &pin).is_err());
            pin.sha512 = selected.archive_sha512;
            pin.platform = "other";
            assert!(verify_pin(selected, &pin).is_err());
            pin.platform = platform;
            pin.major = 25;
            assert!(verify_pin(selected, &pin).is_err());
            pin.major = 21;
            pin.url = "https://unreviewed.invalid/sdk";
            assert!(verify_pin(selected, &pin).is_err());
            pin.url = selected.archive_url;
            for changed in [
                PinIdentity {
                    vendor: "unreviewed",
                    ..pin
                },
                PinIdentity {
                    version: "21.0.12",
                    ..pin
                },
                PinIdentity {
                    build: "b1163.117",
                    ..pin
                },
                PinIdentity {
                    flavor: "jre",
                    ..pin
                },
            ] {
                assert!(verify_pin(selected, &changed).is_err());
            }
            let runtime = selected.runtime.split_once('|').unwrap().1;
            let release =
                format!("IMPLEMENTOR=\"JetBrains s.r.o.\"\nJAVA_RUNTIME_VERSION=\"{runtime}\"\n");
            verify_runtime(selected, &release).unwrap();
            assert!(
                verify_runtime(selected, &release.replace(runtime, "21.0.11+999-b1163.116"))
                    .is_err()
            );
            assert!(
                verify_runtime(
                    selected,
                    &(release.clone() + "IMPLEMENTOR=\"JetBrains s.r.o.\"\n")
                )
                .is_err()
            );
            assert!(
                verify_runtime(
                    selected,
                    "IMPLEMENTOR=unquoted\nJAVA_RUNTIME_VERSION=\"other\"\n"
                )
                .is_err()
            );
        }
    }

    #[test]
    fn trust_and_ambient_controls_are_not_discarded_to_force_a_launch() {
        assert!(require_supported_trust(true).is_err());
        assert!(require_supported_trust(false).is_ok());
        for name in AMBIENT_CONTROLS {
            for value in ["", "private directive"] {
                assert_eq!(
                    unexpected_ambient(|key| (key == name).then(|| value.into())),
                    Some(name)
                );
            }
        }
        assert_eq!(unexpected_ambient(|_| None), None);
    }

    #[test]
    fn child_gets_only_supported_effective_keys_and_never_a_trust_banner() {
        let selected = expected("linux-x64").unwrap();
        let mut command = Command::new("must-not-launch");
        command
            .env(LEGACY_KEY, WINDOWS_RUNTIME)
            .env(WINDOWS_KEY, WINDOWS_RUNTIME)
            .env(LINUX_KEY, LINUX_RUNTIME);
        configure_child(selected, Path::new("/verified/sdk"), &mut command).unwrap();
        let env: BTreeMap<_, _> = command.get_envs().collect();
        assert_eq!(
            env[std::ffi::OsStr::new(LEGACY_KEY)],
            Some(std::ffi::OsStr::new(LINUX_RUNTIME))
        );
        assert_eq!(
            env[std::ffi::OsStr::new("JAVA_HOME")],
            Some(std::ffi::OsStr::new("/verified/sdk"))
        );
        for name in AMBIENT_CONTROLS.into_iter().chain([WINDOWS_KEY, LINUX_KEY]) {
            assert_eq!(env[std::ffi::OsStr::new(name)], None);
        }
        let mut overridden = Command::new("must-not-launch");
        overridden.env("JAVA_TOOL_OPTIONS", "private directive");
        assert!(configure_child(selected, Path::new("/verified/sdk"), &mut overridden).is_err());
        assert_eq!(
            overridden.get_envs().next().unwrap().1,
            Some(std::ffi::OsStr::new("private directive"))
        );
    }
}
