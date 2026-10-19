//! Pure container policy, also testable without compiling the application.
use std::ffi::OsString;
use std::path::Path;

pub(super) fn safe_projection(key: &str) -> bool {
    !key.is_empty()
        && key
            .bytes()
            .all(|byte| byte.is_ascii_alphanumeric() || b"._-/".contains(&byte))
        && key
            .split('/')
            .all(|part| !part.is_empty() && part != "." && part != "..")
}

pub(super) fn linux_elf(bytes: &[u8]) -> bool {
    bytes.len() >= 64 && bytes[..7] == [0x7f, b'E', b'L', b'F', 2, 1, 1] && bytes[18..20] == [62, 0]
}

pub(super) fn build_arguments(podman: bool, context: &Path, tag: &str) -> Vec<OsString> {
    let pull = if podman {
        "--pull=never"
    } else {
        "--pull=false"
    };
    vec![
        "build".into(),
        pull.into(),
        "--network=none".into(),
        "--platform=linux/amd64".into(),
        "--file".into(),
        context.join("Dockerfile").into_os_string(),
        "--tag".into(),
        tag.into(),
        context.as_os_str().into(),
    ]
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn projection_cannot_enter_shell_or_escape_snapshot() {
        for key in [
            "",
            "a/../b",
            "./a",
            "/a",
            "a//b",
            "a'quoted",
            "a$(command)",
            "a;command",
            "a\ncommand",
            "a*",
            "a\\b",
        ] {
            assert!(!safe_projection(key), "{key}");
        }
        assert!(safe_projection("sfm-dev/mc-1.19.2"));
    }
    #[test]
    fn exact_argv_never_pulls_networks_mounts_pushes_or_runs() {
        for podman in [true, false] {
            let args =
                build_arguments(podman, Path::new("context with spaces"), "sfm-puppet:local");
            assert_eq!(args.len(), 9);
            assert_eq!(
                args[1],
                if podman {
                    "--pull=never"
                } else {
                    "--pull=false"
                }
            );
            assert_eq!(args[2], "--network=none");
            assert_eq!(
                args[5],
                Path::new("context with spaces")
                    .join("Dockerfile")
                    .as_os_str()
            );
            assert!(!args.iter().any(|argument| {
                ["--mount", "--volume", "--privileged", "run", "push"]
                    .iter()
                    .any(|forbidden| argument == forbidden)
            }));
        }
    }
    #[test]
    fn rejects_windows_wrong_class_endian_and_architecture() {
        assert!(!linux_elf(b"MZ Windows executable"));
        let mut elf = [0_u8; 64];
        elf[..7].copy_from_slice(&[0x7f, b'E', b'L', b'F', 2, 1, 1]);
        elf[18] = 62;
        assert!(linux_elf(&elf));
        for (offset, value) in [(4, 1), (5, 2), (18, 183), (19, 1)] {
            let mut bad = elf;
            bad[offset] = value;
            assert!(!linux_elf(&bad));
        }
    }
}
