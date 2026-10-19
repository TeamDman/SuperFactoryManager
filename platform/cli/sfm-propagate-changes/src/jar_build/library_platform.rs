//! Narrow Linux/amd64 selection; other hosts retain the legacy library set.
use super::library_rule::LibraryRule;

pub(crate) fn allows(
    path: &str,
    rules: &[LibraryRule],
    os: &str,
    arch: &str,
) -> Result<bool, &'static str> {
    if os != "linux" || arch != "x86_64" {
        return Ok(true);
    }
    if rules.len() > 128 {
        return Err("library rule-count budget exceeded");
    }
    let mut allowed = rules.is_empty();
    for rule in rules {
        if rule.features.is_some() {
            return Err("unsupported library feature rule");
        }
        if !matches!(rule.action.as_str(), "allow" | "disallow") {
            return Err("unknown library rule action");
        }
        let matches = if let Some(rule_os) = &rule.os {
            let matches = rule_os.name.as_deref().is_none_or(|name| name == "linux")
                && rule_os
                    .arch
                    .as_deref()
                    .is_none_or(|arch| matches!(arch, "x86_64" | "amd64"));
            if matches && rule_os.version.is_some() {
                return Err("unsupported Linux OS version rule");
            }
            matches
        } else {
            true
        };
        if matches {
            allowed = rule.action == "allow";
        }
    }
    let filename = path.rsplit('/').next().unwrap_or(path).to_ascii_lowercase();
    let classifier_allowed = filename
        .strip_suffix(".jar")
        .and_then(|name| name.rsplit_once("-natives-"))
        .is_none_or(|(_, classifier)| classifier == "linux");
    // Netty encodes its classifier without the LWJGL `natives-` marker.
    let foreign_netty = filename.starts_with("netty-transport-native-")
        && (filename.contains("-linux-aarch_64") || filename.contains("-linux-arm"));
    Ok(allowed && classifier_allowed && !foreign_netty)
}

#[cfg(test)]
mod tests {
    use super::super::library_os::LibraryOs;
    use super::*;
    fn rule(action: &str, os: Option<&str>) -> LibraryRule {
        LibraryRule {
            action: action.into(),
            os: os.map(|name| LibraryOs {
                name: Some(name.into()),
                arch: None,
                version: None,
            }),
            features: None,
        }
    }
    #[test]
    fn linux_selects_native_and_neutral_in_metadata_order() {
        let paths = [
            "org/lwjgl/lwjgl.jar",
            "org/lwjgl/lwjgl-natives-windows.jar",
            "org/lwjgl/lwjgl-natives-linux.jar",
            "org/lwjgl/lwjgl-natives-linux-arm64.jar",
            "org/lwjgl/lwjgl-natives-macos.jar",
            "org/example/neutral.jar",
        ];
        let selected = paths
            .into_iter()
            .filter(|path| allows(path, &[], "linux", "x86_64").unwrap())
            .collect::<Vec<_>>();
        assert_eq!(selected, [paths[0], paths[2], paths[5]]);
        assert!(
            !allows(
                "netty-transport-native-epoll-4-linux-aarch_64.jar",
                &[],
                "linux",
                "x86_64"
            )
            .unwrap()
        );
    }
    #[test]
    fn windows_keeps_its_existing_neutral_and_classifier_selection() {
        for path in [
            "neutral.jar",
            "lwjgl-natives-windows.jar",
            "lwjgl-natives-linux.jar",
            "lwjgl-natives-macos.jar",
        ] {
            assert!(allows(path, &[rule("allow", Some("osx"))], "windows", "x86_64").unwrap());
        }
    }
    #[test]
    fn ordered_rules_exclude_mac_only_neutral_and_fail_closed_on_unknown_linux_rules() {
        assert!(
            !allows(
                "java-objc-bridge.jar",
                &[rule("allow", Some("osx"))],
                "linux",
                "x86_64"
            )
            .unwrap()
        );
        assert!(
            allows(
                "neutral.jar",
                &[rule("allow", None), rule("disallow", Some("osx"))],
                "linux",
                "x86_64"
            )
            .unwrap()
        );
        assert!(
            !allows(
                "neutral.jar",
                &[rule("allow", None), rule("disallow", Some("linux"))],
                "linux",
                "x86_64"
            )
            .unwrap()
        );
        assert!(allows("neutral.jar", &[rule("unknown", None)], "linux", "x86_64").is_err());
    }
}
