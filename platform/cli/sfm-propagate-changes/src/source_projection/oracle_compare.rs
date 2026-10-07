//! Narrow, declared byte comparison for freshly rendered oracle projections.

use super::inputs::GENERATED_BANNER;
use super::projection_catalog::SUPPORTED_TARGETS;
use facet::Facet;
use std::borrow::Cow;

const UTF8_BOM: &[u8] = b"\xEF\xBB\xBF";

/// Versioned comparison allowances; source whitespace is otherwise significant.
#[derive(Facet, Debug, Clone, PartialEq, Eq)]
#[facet(deny_unknown_fields)]
#[expect(
    clippy::struct_excessive_bools,
    reason = "Each independently declared byte allowance remains explicit in the versioned comparison contract"
)]
pub struct ComparisonPolicy {
    pub schema_version: u32,
    pub allow_generated_banner: bool,
    pub normalize_crlf: bool,
    pub allow_final_newline_difference: bool,
    /// Exact pinned settings prefix plus the documented target-name appendix.
    #[facet(default)]
    pub allow_settings_project_identity_override: bool,
}

impl Default for ComparisonPolicy {
    fn default() -> Self {
        Self {
            schema_version: 1,
            allow_generated_banner: true,
            normalize_crlf: false,
            allow_final_newline_difference: false,
            allow_settings_project_identity_override: false,
        }
    }
}

#[derive(Facet, Debug, Clone, PartialEq, Eq)]
pub struct Comparison {
    pub exact_equal: bool,
    pub normalized_equal: bool,
    pub transformations: Vec<String>,
    pub diagnostics: Vec<String>,
}

/// Compare existing files. Absence and file membership belong to the caller.
///
/// Only one exact generator-owned banner on rendered Java may be removed.
/// Explicit EOL allowances apply only to known text paths containing UTF-8
/// without binary control bytes. Other resources retain byte-exact comparison.
#[must_use]
pub fn compare_bytes(
    path: &str,
    expected: &[u8],
    rendered: &[u8],
    policy: &ComparisonPolicy,
) -> Comparison {
    let inputs = prepare_comparison_inputs(path, expected, rendered, policy, None);
    Comparison {
        exact_equal: expected == rendered,
        normalized_equal: inputs.diagnostics.is_empty() && inputs.expected == inputs.rendered,
        transformations: inputs.transformations,
        diagnostics: inputs.diagnostics,
    }
}

/// Bytes for a readable diff under the exact declared comparison policy.
///
/// Without a validated target this never applies the settings layout allowance.
///
/// This uses the same normalization path as [`compare_bytes`]. Raw identity
/// and diagnostics remain authoritative in that comparison report. Invalid
/// policies or malformed generated banners leave the view bytes unchanged.
#[must_use]
pub fn comparison_inputs(
    path: &str,
    expected: &[u8],
    rendered: &[u8],
    policy: &ComparisonPolicy,
) -> (Vec<u8>, Vec<u8>) {
    let inputs = prepare_comparison_inputs(path, expected, rendered, policy, None);
    (inputs.expected.into_owned(), inputs.rendered.into_owned())
}

/// Compare a validated target's projected file with its pinned oracle bytes.
///
/// The optional settings allowance accepts only the complete pinned prefix
/// followed by the exact documented literal project-name appendix. It never
/// derives expected content from an authored template or strips arbitrary code.
#[must_use]
pub fn compare_project_bytes(
    path: &str,
    target_id: &str,
    expected: &[u8],
    rendered: &[u8],
    policy: &ComparisonPolicy,
) -> Comparison {
    let inputs = prepare_comparison_inputs(path, expected, rendered, policy, Some(target_id));
    Comparison {
        exact_equal: expected == rendered,
        normalized_equal: inputs.diagnostics.is_empty() && inputs.expected == inputs.rendered,
        transformations: inputs.transformations,
        diagnostics: inputs.diagnostics,
    }
}

/// Target-aware diff bytes, using the same allowances as [`compare_project_bytes`].
#[must_use]
pub fn project_comparison_inputs(
    path: &str,
    target_id: &str,
    expected: &[u8],
    rendered: &[u8],
    policy: &ComparisonPolicy,
) -> (Vec<u8>, Vec<u8>) {
    let inputs = prepare_comparison_inputs(path, expected, rendered, policy, Some(target_id));
    (inputs.expected.into_owned(), inputs.rendered.into_owned())
}

struct PreparedComparison<'a> {
    expected: Cow<'a, [u8]>,
    rendered: Cow<'a, [u8]>,
    transformations: Vec<String>,
    diagnostics: Vec<String>,
}

fn prepare_comparison_inputs<'a>(
    path: &str,
    expected: &'a [u8],
    rendered: &'a [u8],
    policy: &ComparisonPolicy,
    target_id: Option<&str>,
) -> PreparedComparison<'a> {
    let mut inputs = PreparedComparison {
        expected: Cow::Borrowed(expected),
        rendered: Cow::Borrowed(rendered),
        transformations: Vec::new(),
        diagnostics: Vec::new(),
    };
    if policy.schema_version != 1 {
        inputs.diagnostics.push(format!(
            "unsupported comparison policy schema {}",
            policy.schema_version
        ));
        return inputs;
    }
    if expected == rendered || !is_known_text_path(path) {
        return inputs;
    }
    if !is_safe_utf8_text(expected) || !is_safe_utf8_text(rendered) {
        return inputs;
    }

    if policy.allow_generated_banner
        && path
            .rsplit_once('.')
            .is_some_and(|(_, extension)| extension.eq_ignore_ascii_case("java"))
    {
        let after_bom = rendered.strip_prefix(UTF8_BOM).unwrap_or(rendered);
        if after_bom.starts_with(GENERATED_BANNER.as_bytes()) {
            match remove_generated_java_banner(rendered) {
                Ok(source) => {
                    inputs.rendered = Cow::Owned(source);
                    inputs.transformations.push(
                        "removed one exact generated Java banner from rendered output".into(),
                    );
                }
                Err(diagnostic) => {
                    inputs.diagnostics.push(diagnostic.into());
                    return inputs;
                }
            }
        }
    }
    if policy.normalize_crlf {
        normalize_crlf(&mut inputs.expected, "oracle", &mut inputs.transformations);
        normalize_crlf(
            &mut inputs.rendered,
            "rendered output",
            &mut inputs.transformations,
        );
    }
    if policy.allow_settings_project_identity_override
        && path == "settings.gradle"
        && let Some(target_id) = target_id
    {
        accept_settings_project_identity(&mut inputs, target_id);
    }
    if inputs.expected != inputs.rendered && policy.allow_final_newline_difference {
        let side = if without_single_final_newline(inputs.expected.as_ref())
            == Some(inputs.rendered.as_ref())
        {
            let length = inputs.rendered.len();
            inputs.expected.to_mut().truncate(length);
            Some("oracle")
        } else if without_single_final_newline(inputs.rendered.as_ref())
            == Some(inputs.expected.as_ref())
        {
            let length = inputs.expected.len();
            inputs.rendered.to_mut().truncate(length);
            Some("rendered output")
        } else {
            None
        };
        if let Some(side) = side {
            inputs.transformations.push(format!(
                "ignored one final line ending present only in {side}"
            ));
        }
    }
    inputs
}

fn accept_settings_project_identity(inputs: &mut PreparedComparison<'_>, target_id: &str) {
    if !SUPPORTED_TARGETS.iter().any(|(id, _)| *id == target_id) {
        inputs
            .diagnostics
            .push(format!("unsupported settings layout target {target_id}"));
        return;
    }
    let newline = if inputs.expected.windows(2).any(|pair| pair == b"\r\n") {
        "\r\n"
    } else {
        "\n"
    };
    let mut permitted = inputs.expected.to_vec();
    if !permitted.ends_with(b"\n") {
        permitted.extend_from_slice(newline.as_bytes());
    }
    permitted.extend_from_slice(
        format!(
            "{newline}// Core-owned project identity; projection directory names are not inputs.{newline}rootProject.name = 'sfm-{target_id}'{newline}"
        )
        .as_bytes(),
    );
    if inputs.rendered.as_ref() == permitted.as_slice() {
        inputs.rendered = inputs.expected.clone();
        inputs.transformations.push(format!(
            "accepted settings.gradle layout sfm:literal_project_identity@1 for target {target_id}"
        ));
    }
}

fn is_known_text_path(path: &str) -> bool {
    let leaf = path.rsplit('/').next().unwrap_or(path);
    if matches!(leaf, "gradlew" | ".gitignore" | ".gitattributes") {
        return true;
    }
    let Some((_, extension)) = leaf.rsplit_once('.') else {
        return false;
    };
    matches!(
        extension.to_ascii_lowercase().as_str(),
        "java"
            | "g4"
            | "gradle"
            | "properties"
            | "json"
            | "xml"
            | "mcmeta"
            | "sfml"
            | "md"
            | "txt"
            | "ps1"
            | "bat"
            | "sh"
            | "toml"
            | "yaml"
            | "yml"
    )
}

fn is_safe_utf8_text(bytes: &[u8]) -> bool {
    std::str::from_utf8(bytes).is_ok()
        && !bytes
            .iter()
            .any(|byte| *byte < 0x20 && !matches!(*byte, b'\t' | b'\r' | b'\n'))
}

// Keep this rule aligned with release_source_parity_test.rs. The shared
// generator marker is authoritative; arbitrary comments are never stripped.
fn remove_generated_java_banner(generated: &[u8]) -> Result<Vec<u8>, &'static str> {
    let (bom, after_bom) = if let Some(rest) = generated.strip_prefix(UTF8_BOM) {
        (UTF8_BOM, rest)
    } else {
        (&[][..], generated)
    };
    let after_banner = after_bom
        .strip_prefix(GENERATED_BANNER.as_bytes())
        .ok_or("missing exact generated Java banner")?;
    let (banner_line_ending, source) = if let Some(rest) = after_banner.strip_prefix(b"\r\n") {
        (&b"\r\n"[..], rest)
    } else if let Some(rest) = after_banner.strip_prefix(b"\n") {
        (&b"\n"[..], rest)
    } else if let Some(rest) = after_banner.strip_prefix(b"\r") {
        (&b"\r"[..], rest)
    } else {
        return Err("generated Java banner has no line ending");
    };
    if source.starts_with(UTF8_BOM) {
        return Err("Java BOM occurs after generated banner");
    }
    if banner_line_ending != preferred_line_ending(source) {
        return Err("generated Java banner uses a different line ending from the source");
    }
    Ok([bom, source].concat())
}

fn preferred_line_ending(source: &[u8]) -> &'static [u8] {
    if let Some(index) = source.iter().position(|byte| *byte == b'\n') {
        if index > 0 && source[index - 1] == b'\r' {
            b"\r\n"
        } else {
            b"\n"
        }
    } else if source.contains(&b'\r') {
        b"\r"
    } else {
        b"\n"
    }
}

fn normalize_crlf(bytes: &mut Cow<'_, [u8]>, side: &str, transformations: &mut Vec<String>) {
    if !bytes.windows(2).any(|pair| pair == b"\r\n") {
        return;
    }
    let mut normalized = Vec::with_capacity(bytes.len());
    let mut offset = 0;
    while offset < bytes.len() {
        if bytes[offset..].starts_with(b"\r\n") {
            normalized.push(b'\n');
            offset += 2;
        } else {
            normalized.push(bytes[offset]);
            offset += 1;
        }
    }
    *bytes = Cow::Owned(normalized);
    transformations.push(format!("normalized CRLF to LF in {side}"));
}

fn without_single_final_newline(bytes: &[u8]) -> Option<&[u8]> {
    let body = bytes
        .strip_suffix(b"\r\n")
        .or_else(|| bytes.strip_suffix(b"\n"))?;
    (!body.ends_with(b"\n") && !body.ends_with(b"\r")).then_some(body)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn settings_layout_is_explicit_and_keeps_the_complete_pinned_prefix() {
        let prefix = b"pluginManagement {}\nrootProject.name = 'directory-dependent'\n";
        let output = [
            prefix.as_slice(),
            b"\n// Core-owned project identity; projection directory names are not inputs.\nrootProject.name = 'sfm-1.19.2'\n",
        ]
        .concat();
        let policy = ComparisonPolicy {
            allow_settings_project_identity_override: true,
            ..Default::default()
        };
        let result = compare_project_bytes("settings.gradle", "1.19.2", prefix, &output, &policy);
        assert!(!result.exact_equal && result.normalized_equal);
        assert_eq!(result.transformations.len(), 1);
        assert!(result.diagnostics.is_empty());
        let (left, right) =
            project_comparison_inputs("settings.gradle", "1.19.2", prefix, &output, &policy);
        assert_eq!(left, prefix);
        assert_eq!(right, prefix);
        assert!(
            !compare_project_bytes(
                "settings.gradle",
                "1.19.2",
                prefix,
                &output,
                &ComparisonPolicy::default(),
            )
            .normalized_equal
        );
        assert!(!compare_bytes("settings.gradle", prefix, &output, &policy).normalized_equal);
    }

    #[test]
    fn settings_layout_rejects_wrong_target_changed_prefix_and_arbitrary_code() {
        let prefix = b"pluginManagement {}\nrootProject.name = 'directory-dependent'\n";
        let suffix = b"\n// Core-owned project identity; projection directory names are not inputs.\nrootProject.name = 'sfm-1.19.2'\n";
        let output = [prefix.as_slice(), suffix].concat();
        let policy = ComparisonPolicy {
            allow_settings_project_identity_override: true,
            normalize_crlf: true,
            allow_final_newline_difference: true,
            ..Default::default()
        };
        let wrong_target = String::from_utf8(output.clone())
            .unwrap()
            .replace("'sfm-1.19.2'", "'sfm-1.19.4'")
            .into_bytes();
        for rejected in [
            wrong_target,
            [
                b"pluginManagement { injected() }\nrootProject.name = 'directory-dependent'\n"
                    .as_slice(),
                suffix,
            ]
            .concat(),
            [output.as_slice(), b"injected()\n"].concat(),
            [prefix.as_slice(), b"// other comment\n", suffix].concat(),
            [output.as_slice(), b"\n"].concat(),
        ] {
            let result =
                compare_project_bytes("settings.gradle", "1.19.2", prefix, &rejected, &policy);
            assert!(!result.normalized_equal);
            assert!(result.diagnostics.is_empty());
            assert!(
                !result
                    .transformations
                    .iter()
                    .any(|rule| rule.contains("literal_project_identity"))
            );
        }
        assert!(
            !compare_project_bytes("gradle/settings.gradle", "1.19.2", prefix, &output, &policy)
                .normalized_equal
        );
    }

    #[test]
    fn settings_layout_uses_target_id_and_only_declared_eol_normalization() {
        let prefix = b"rootProject.name = 'directory-dependent'";
        let policy = ComparisonPolicy {
            allow_settings_project_identity_override: true,
            ..Default::default()
        };
        for target_id in ["1.19.2", "1.21.0", "26.1.2"] {
            let output = format!(
                "rootProject.name = 'directory-dependent'\n\n// Core-owned project identity; projection directory names are not inputs.\nrootProject.name = 'sfm-{target_id}'\n"
            );
            assert!(
                compare_project_bytes(
                    "settings.gradle",
                    target_id,
                    prefix,
                    output.as_bytes(),
                    &policy
                )
                .normalized_equal
            );
            let crlf = output.replace('\n', "\r\n");
            assert!(
                !compare_project_bytes(
                    "settings.gradle",
                    target_id,
                    prefix,
                    crlf.as_bytes(),
                    &policy
                )
                .normalized_equal
            );
            let normalized = ComparisonPolicy {
                normalize_crlf: true,
                ..policy.clone()
            };
            assert!(
                compare_project_bytes(
                    "settings.gradle",
                    target_id,
                    prefix,
                    crlf.as_bytes(),
                    &normalized
                )
                .normalized_equal
            );
        }
        let unknown =
            compare_project_bytes("settings.gradle", "unknown", prefix, b"changed", &policy);
        assert!(!unknown.normalized_equal && unknown.diagnostics.len() == 1);
    }

    #[test]
    fn older_policy_records_do_not_enable_the_settings_layout_rule() {
        let policy: ComparisonPolicy = facet_json::from_str(
            r#"{"schema_version":1,"allow_generated_banner":true,"normalize_crlf":true,"allow_final_newline_difference":false}"#,
        )
        .unwrap();
        assert!(!policy.allow_settings_project_identity_override);
    }

    fn generated(source: &[u8], eol: &[u8]) -> Vec<u8> {
        let (bom, body) = source
            .strip_prefix(UTF8_BOM)
            .map_or((&[][..], source), |body| (UTF8_BOM, body));
        [bom, GENERATED_BANNER.as_bytes(), eol, body].concat()
    }

    #[test]
    fn diff_inputs_share_declared_banner_and_crlf_rules_and_keep_changed_lines() {
        let expected = b"class A {\n    int value = 1;\n}\n";
        let changed = b"class A {\r\n    int value = 2;\r\n}\r\n";
        let rendered = generated(changed, b"\r\n");
        let policy = ComparisonPolicy {
            normalize_crlf: true,
            ..Default::default()
        };
        let (left, right) = comparison_inputs("A.java", expected, &rendered, &policy);
        assert_eq!(left, expected);
        assert_eq!(right, b"class A {\n    int value = 2;\n}\n");
        let comparison = compare_bytes("A.java", expected, &rendered, &policy);
        assert!(!comparison.exact_equal && !comparison.normalized_equal);
        assert_eq!(comparison.transformations.len(), 2);
        assert!(comparison.diagnostics.is_empty());

        let (_, strict_right) =
            comparison_inputs("A.java", expected, &rendered, &ComparisonPolicy::default());
        assert_eq!(strict_right, changed);
    }

    #[test]
    fn malformed_banner_keeps_original_diff_bytes_and_comparison_diagnostic() {
        let expected = b"class A {}\n";
        let rendered = generated(b"class A { int added; }\r\n", b"\n");
        let policy = ComparisonPolicy {
            normalize_crlf: true,
            ..Default::default()
        };
        let (left, right) = comparison_inputs("A.java", expected, &rendered, &policy);
        assert_eq!(left, expected);
        assert_eq!(right, rendered);
        let comparison = compare_bytes("A.java", expected, &rendered, &policy);
        assert!(!comparison.normalized_equal);
        assert_eq!(comparison.diagnostics.len(), 1);
        assert!(comparison.transformations.is_empty());
    }

    #[test]
    fn exact_bytes_match_without_transformation_even_for_binary() {
        for path in ["src/A.java", "src/image.png"] {
            let result = compare_bytes(path, b"\0\xff", b"\0\xff", &ComparisonPolicy::default());
            assert!(result.exact_equal && result.normalized_equal);
            assert!(result.transformations.is_empty() && result.diagnostics.is_empty());
        }
    }

    #[test]
    fn one_generated_banner_preserves_bom_and_all_source_bytes() {
        for eol in [&b"\n"[..], &b"\r\n"[..], &b"\r"[..]] {
            for bom in [&[][..], UTF8_BOM] {
                let source = [bom, b"// authored comment", eol, b"class A {}", eol].concat();
                let output = generated(&source, eol);
                let result =
                    compare_bytes("src/A.java", &source, &output, &ComparisonPolicy::default());
                assert!(!result.exact_equal && result.normalized_equal);
                assert_eq!(result.transformations.len(), 1);
                assert!(result.diagnostics.is_empty());
            }
        }
    }

    #[test]
    fn authored_comment_equal_to_marker_is_preserved_and_only_one_is_removed() {
        let source = [GENERATED_BANNER.as_bytes(), b"\nclass A {}\n"].concat();
        let output = generated(&source, b"\n");
        assert!(
            compare_bytes("A.java", &source, &output, &ComparisonPolicy::default())
                .normalized_equal
        );
        assert!(
            !compare_bytes(
                "A.java",
                b"class A {}\n",
                &output,
                &ComparisonPolicy::default()
            )
            .normalized_equal
        );
    }

    #[test]
    fn banner_allowance_is_optional_and_java_only() {
        let source = b"class A {}\n";
        let output = generated(source, b"\n");
        assert!(
            compare_bytes("A.JAVA", source, &output, &ComparisonPolicy::default()).normalized_equal
        );
        let strict = ComparisonPolicy {
            allow_generated_banner: false,
            ..Default::default()
        };
        assert!(!compare_bytes("A.java", source, &output, &strict).normalized_equal);
        assert!(
            !compare_bytes("A.txt", source, &output, &ComparisonPolicy::default()).normalized_equal
        );
        assert!(
            !compare_bytes(
                "A.java",
                source,
                b"// authored\nclass A {}\n",
                &ComparisonPolicy::default()
            )
            .normalized_equal
        );
    }

    #[test]
    fn invalid_banner_never_becomes_a_match_even_with_eol_allowances() {
        let policy = ComparisonPolicy {
            normalize_crlf: true,
            allow_final_newline_difference: true,
            ..Default::default()
        };
        for output in [
            [GENERATED_BANNER.as_bytes(), b" changed\nclass A {}\n"].concat(),
            GENERATED_BANNER.as_bytes().to_vec(),
            [
                GENERATED_BANNER.as_bytes(),
                b"\n",
                UTF8_BOM,
                b"class A {}\n",
            ]
            .concat(),
            generated(b"class A {}\n", b"\r\n"),
        ] {
            let result = compare_bytes("A.java", b"class A {}\n", &output, &policy);
            assert!(!result.normalized_equal);
            assert_eq!(result.diagnostics.len(), 1);
        }
    }

    #[test]
    fn eol_changes_require_policy_and_preserve_lone_cr_comments_and_spaces() {
        let policy = ComparisonPolicy {
            normalize_crlf: true,
            ..Default::default()
        };
        assert!(
            !compare_bytes(
                "build.gradle",
                b"a\nb\n",
                b"a\r\nb\r\n",
                &ComparisonPolicy::default()
            )
            .normalized_equal
        );
        assert!(compare_bytes("build.gradle", b"a\nb\n", b"a\r\nb\r\n", &policy).normalized_equal);
        for output in [b"a\rb\n".as_slice(), b"a \nb\n", b"// comment\na\nb\n"] {
            assert!(!compare_bytes("A.java", b"a\nb\n", output, &policy).normalized_equal);
        }
    }

    #[test]
    fn final_newline_rule_is_bounded_to_one_missing_lf_or_crlf() {
        let policy = ComparisonPolicy {
            allow_final_newline_difference: true,
            ..Default::default()
        };
        for newline in [b"\n".as_slice(), b"\r\n"] {
            let terminated = [b"a".as_slice(), newline].concat();
            assert!(compare_bytes("A.txt", b"a", &terminated, &policy).normalized_equal);
            assert!(compare_bytes("A.txt", &terminated, b"a", &policy).normalized_equal);
        }
        for (expected, output) in [
            (b"a\n".as_slice(), b"a\n\n".as_slice()),
            (b"a\n".as_slice(), b"a\r\n".as_slice()),
            (b"a".as_slice(), b"a\r".as_slice()),
        ] {
            assert!(!compare_bytes("A.txt", expected, output, &policy).normalized_equal);
        }
        assert!(
            !compare_bytes("A.txt", b"a", b"a\n", &ComparisonPolicy::default()).normalized_equal
        );
    }

    #[test]
    fn unknown_binary_invalid_utf8_and_control_bytes_remain_exact() {
        let policy = ComparisonPolicy {
            normalize_crlf: true,
            allow_final_newline_difference: true,
            ..Default::default()
        };
        for (path, expected, output) in [
            ("a.bin", b"a\n".as_slice(), b"a\r\n".as_slice()),
            ("a.snbt", b"a\n".as_slice(), b"a\r\n".as_slice()),
            ("a.txt", b"\xff\n".as_slice(), b"\xff\r\n".as_slice()),
            ("a.json", b"\0\n".as_slice(), b"\0\r\n".as_slice()),
        ] {
            let result = compare_bytes(path, expected, output, &policy);
            assert!(!result.normalized_equal);
            assert!(result.transformations.is_empty() && result.diagnostics.is_empty());
        }
    }

    #[test]
    fn unsupported_policy_is_diagnostic_even_when_bytes_match() {
        let policy = ComparisonPolicy {
            schema_version: 2,
            ..Default::default()
        };
        let result = compare_bytes("A.java", b"a", b"a", &policy);
        assert!(result.exact_equal && !result.normalized_equal);
        assert_eq!(result.diagnostics.len(), 1);
    }
}
