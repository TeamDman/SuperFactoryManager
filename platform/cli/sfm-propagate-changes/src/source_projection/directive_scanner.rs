//! Separates deliberate Liquid control lines from ordinary Java bytes.
//!
//! Liquid's `{{` syntax collides with valid Java array initializers. We parse
//! only complete directive lines and pass all other text to Liquid as opaque
//! string values, so the generated Java is never reparsed as a template.

use std::collections::BTreeSet;
use std::fmt::Write as _;

#[derive(Debug, PartialEq, Eq)]
pub(crate) enum ScannedSource<'a> {
    Identity(&'a str),
    Template(ScannedTemplate),
}

#[derive(Debug, PartialEq, Eq)]
pub(crate) struct ScannedTemplate {
    pub(crate) skeleton: String,
    pub(crate) chunks: Vec<String>,
    pub(crate) referenced_conditions: BTreeSet<String>,
}

#[derive(Debug)]
struct ConditionalBlock {
    opened_at_line: usize,
    has_else: bool,
}

#[derive(Debug)]
enum Directive<'a> {
    If(&'a str),
    Elsif(&'a str),
    Else,
    Endif,
}

/// Scan a primary `.java` file without interpreting Java strings or braces.
///
/// The initial syntax is a whole-line `{% if features.name %}` or
/// `{% if targets.name %}`, with optional `elsif`, `else` and `endif` lines.
/// A line beginning `\{%` emits a literal line beginning `{%`.
pub(crate) fn scan(source: &str) -> eyre::Result<ScannedSource<'_>> {
    let mut skeleton = String::new();
    let mut chunks = Vec::new();
    let mut raw = String::new();
    let mut referenced_conditions = BTreeSet::new();
    let mut blocks: Vec<ConditionalBlock> = Vec::new();
    let mut projected = false;

    for (index, line) in source.split_inclusive('\n').enumerate() {
        let line_number = index + 1;
        let body = line.strip_suffix('\n').unwrap_or(line);
        let body = body.strip_suffix('\r').unwrap_or(body);
        let trimmed = body.trim();

        if trimmed.starts_with("\\{%") {
            projected = true;
            let escape_at = line
                .find("\\{%")
                .expect("trimmed escaped directive is in line");
            raw.push_str(&line[..escape_at]);
            raw.push_str(&line[escape_at + 1..]);
            continue;
        }

        if !trimmed.starts_with("{%") {
            raw.push_str(line);
            continue;
        }

        let directive = parse_directive(trimmed, line_number)?;
        projected = true;
        flush_chunk(&mut skeleton, &mut chunks, &mut raw);
        match directive {
            Directive::If(condition) => {
                referenced_conditions.insert(condition.to_owned());
                blocks.push(ConditionalBlock {
                    opened_at_line: line_number,
                    has_else: false,
                });
                write!(skeleton, "{{% if {condition} %}}")
                    .expect("writing to a String cannot fail");
            }
            Directive::Elsif(condition) => {
                let block = blocks
                    .last()
                    .ok_or_else(|| eyre::eyre!("line {line_number}: elsif has no matching if"))?;
                eyre::ensure!(
                    !block.has_else,
                    "line {line_number}: elsif follows else in block opened at line {}",
                    block.opened_at_line
                );
                referenced_conditions.insert(condition.to_owned());
                write!(skeleton, "{{% elsif {condition} %}}")
                    .expect("writing to a String cannot fail");
            }
            Directive::Else => {
                let block = blocks
                    .last_mut()
                    .ok_or_else(|| eyre::eyre!("line {line_number}: else has no matching if"))?;
                eyre::ensure!(
                    !block.has_else,
                    "line {line_number}: duplicate else for block opened at line {}",
                    block.opened_at_line
                );
                block.has_else = true;
                skeleton.push_str("{% else %}");
            }
            Directive::Endif => {
                eyre::ensure!(
                    blocks.pop().is_some(),
                    "line {line_number}: endif has no matching if"
                );
                skeleton.push_str("{% endif %}");
            }
        }
    }

    if let Some(block) = blocks.last() {
        eyre::bail!("line {}: if has no matching endif", block.opened_at_line);
    }
    if !projected {
        return Ok(ScannedSource::Identity(source));
    }
    flush_chunk(&mut skeleton, &mut chunks, &mut raw);
    Ok(ScannedSource::Template(ScannedTemplate {
        skeleton,
        chunks,
        referenced_conditions,
    }))
}

fn flush_chunk(skeleton: &mut String, chunks: &mut Vec<String>, raw: &mut String) {
    let index = chunks.len();
    write!(skeleton, "{{{{ __sfm_chunk_{index} }}}}").expect("writing to a String cannot fail");
    chunks.push(std::mem::take(raw));
}

fn parse_directive(line: &str, line_number: usize) -> eyre::Result<Directive<'_>> {
    let body = line
        .strip_prefix("{%")
        .and_then(|line| line.strip_suffix("%}"))
        .ok_or_else(|| eyre::eyre!("line {line_number}: malformed template directive"))?
        .trim();
    let parts: Vec<&str> = body.split_whitespace().collect();
    match parts.as_slice() {
        ["if", condition] => {
            validate_condition(condition, line_number)?;
            Ok(Directive::If(condition))
        }
        ["elsif", condition] => {
            validate_condition(condition, line_number)?;
            Ok(Directive::Elsif(condition))
        }
        ["else"] => Ok(Directive::Else),
        ["endif"] => Ok(Directive::Endif),
        _ => eyre::bail!("line {line_number}: unsupported template directive: {body}"),
    }
}

fn validate_condition(condition: &str, line_number: usize) -> eyre::Result<()> {
    let Some((root, name)) = condition.split_once('.') else {
        eyre::bail!("line {line_number}: condition must be features.<id> or targets.<id>");
    };
    let valid_name = !name.is_empty()
        && name.bytes().enumerate().all(|(index, byte)| {
            byte.is_ascii_lowercase() || (index > 0 && (byte.is_ascii_digit() || byte == b'_'))
        });
    eyre::ensure!(
        matches!(root, "features" | "targets") && valid_name,
        "line {line_number}: invalid condition '{condition}'"
    );
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn directive_free_java_is_an_exact_identity_even_with_liquid_delimiters() {
        let source = "String[][] cases = {{\"cli.rs\", \"rust\"}};\r\nString text = \"{% if features.nope %}\";\r\n";
        assert_eq!(scan(source).unwrap(), ScannedSource::Identity(source));
    }

    #[test]
    fn nested_control_lines_leave_java_as_opaque_chunks() {
        let source = "before\r\n{% if features.touch_display %}\r\nint[][] values = {{1, 2}};\r\n{% if targets.v1_19_2 %}\r\nold();\r\n{% elsif targets.v26_1_2 %}\r\nnewer();\r\n{% else %}\r\nother();\r\n{% endif %}\r\n{% endif %}\r\nafter\r\n";
        let ScannedSource::Template(template) = scan(source).unwrap() else {
            panic!("control lines should create a template");
        };
        assert!(
            template
                .chunks
                .iter()
                .any(|chunk| chunk.contains("{{1, 2}}"))
        );
        assert!(template.chunks.iter().all(|chunk| !chunk.contains("{% if")));
        assert!(template.chunks[0].ends_with("\r\n"));
        assert!(template.chunks.last().unwrap().starts_with("after\r\n"));
        assert_eq!(template.referenced_conditions.len(), 3);
        assert!(template.skeleton.contains("{% elsif targets.v26_1_2 %}"));
    }

    #[test]
    fn escaped_full_line_directive_is_literal_java_text() {
        let ScannedSource::Template(template) = scan("\\{% if features.example %}\n").unwrap()
        else {
            panic!("escaped line should be projected");
        };
        assert_eq!(template.chunks, vec!["{% if features.example %}\n"]);
        assert!(template.referenced_conditions.is_empty());
    }

    #[test]
    fn rejects_bad_nesting_and_unknown_directives_with_line_numbers() {
        for (source, expected) in [
            ("a\n{% else %}\n", "line 2"),
            (
                "{% if features.a %}\n{% else %}\n{% elsif features.b %}\n",
                "line 3",
            ),
            ("{% if features.a %}\n", "line 1"),
            ("{% include 'other.java' %}\n", "line 1"),
            ("{% if features.a %}\n{% endif %}\n{% endif %}\n", "line 3"),
        ] {
            assert!(scan(source).unwrap_err().to_string().contains(expected));
        }
    }

    #[test]
    fn rejects_expressions_and_ambiguous_names() {
        for source in [
            "{% if minecraft_version > '1.19.2' %}",
            "{% if features.packet-send %}",
            "{% if features.missing.value %}",
            "{% if targets.1_19_2 %}",
        ] {
            assert!(scan(source).is_err(), "unexpectedly accepted {source}");
        }
    }
}
