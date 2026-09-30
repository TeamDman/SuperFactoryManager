//! Separates deliberate Liquid control lines from ordinary Java bytes.
//!
//! Liquid's `{{` syntax collides with valid Java array initializers. We parse
//! only complete directive lines and pass all other text to Liquid as opaque
//! string values, so the generated Java is never reparsed as a template.

use std::collections::BTreeMap;
use std::fmt::Write as _;

#[derive(Debug, PartialEq, Eq)]
pub(crate) enum ScannedSource<'a> {
    Identity(&'a str),
    Template(ScannedTemplate),
}

#[derive(Debug, Default, PartialEq, Eq)]
pub(crate) struct ScannedTemplate {
    pub(crate) skeleton: String,
    pub(crate) chunks: Vec<String>,
    pub(crate) referenced_conditions: BTreeMap<String, usize>,
    pub(crate) referenced_selectors: BTreeMap<String, usize>,
}

#[derive(Debug)]
struct ControlBlock {
    opened_at_line: usize,
    has_else: bool,
    kind: BlockKind,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
enum BlockKind {
    If,
    Case { has_when: bool },
}

impl ControlBlock {
    fn name(&self) -> &'static str {
        match self.kind {
            BlockKind::If => "if",
            BlockKind::Case { .. } => "case",
        }
    }

    fn end_name(&self) -> &'static str {
        match self.kind {
            BlockKind::If => "endif",
            BlockKind::Case { .. } => "endcase",
        }
    }
}

#[derive(Debug)]
enum Directive<'a> {
    If(&'a str),
    Elsif(&'a str),
    Else,
    Endif,
    Case(&'a str),
    When(Vec<&'a str>),
    Endcase,
}

/// Scan a primary `.java` file without interpreting Java strings or braces.
///
/// Boolean `if`/`elsif` directives read registered feature/target names.
/// String `case` directives read typed projection metadata; `when` accepts
/// quoted literal alternatives separated by commas or `or`.
/// A line beginning `\{%` emits a literal line beginning `{%`.
pub(crate) fn scan(source: &str) -> eyre::Result<ScannedSource<'_>> {
    let mut template = ScannedTemplate::default();
    let mut raw = String::new();
    let mut blocks: Vec<ControlBlock> = Vec::new();
    let mut projected = false;

    for (index, line) in source.split_inclusive('\n').enumerate() {
        let line_number = index + 1;
        let body = line.strip_suffix('\n').unwrap_or(line);
        let body = body.strip_suffix('\r').unwrap_or(body);
        let trimmed = body.trim();

        if trimmed.starts_with("\\{%") {
            ensure_case_branch(&blocks, line_number, "literal text")?;
            projected = true;
            let escape_at = line
                .find("\\{%")
                .expect("trimmed escaped directive is in line");
            raw.push_str(&line[..escape_at]);
            raw.push_str(&line[escape_at + 1..]);
            continue;
        }

        if !trimmed.starts_with("{%") {
            if !trimmed.is_empty() {
                ensure_case_branch(&blocks, line_number, "literal text")?;
            }
            raw.push_str(line);
            continue;
        }

        let directive = parse_directive(trimmed, line_number)?;
        projected = true;
        flush_chunk(&mut template.skeleton, &mut template.chunks, &mut raw);
        push_directive(&mut template, &mut blocks, directive, line_number)?;
    }

    if let Some(block) = blocks.last() {
        eyre::bail!(
            "line {}: {} has no matching {}",
            block.opened_at_line,
            block.name(),
            block.end_name()
        );
    }
    if !projected {
        return Ok(ScannedSource::Identity(source));
    }
    flush_chunk(&mut template.skeleton, &mut template.chunks, &mut raw);
    Ok(ScannedSource::Template(template))
}

fn push_directive(
    template: &mut ScannedTemplate,
    blocks: &mut Vec<ControlBlock>,
    directive: Directive<'_>,
    line: usize,
) -> eyre::Result<()> {
    match directive {
        Directive::If(condition) => {
            ensure_case_branch(blocks, line, "if")?;
            record_condition(template, condition, line);
            blocks.push(ControlBlock {
                opened_at_line: line,
                has_else: false,
                kind: BlockKind::If,
            });
            write!(template.skeleton, "{{% if {condition} %}}")
                .expect("writing to a String cannot fail");
        }
        Directive::Elsif(condition) => {
            let block = matching_block(blocks, "elsif", "if", line)?;
            ensure_before_else(block, "elsif", line)?;
            record_condition(template, condition, line);
            write!(template.skeleton, "{{% elsif {condition} %}}")
                .expect("writing to a String cannot fail");
        }
        Directive::Case(selector) => {
            ensure_case_branch(blocks, line, "case")?;
            template
                .referenced_selectors
                .entry(selector.to_owned())
                .or_insert(line);
            blocks.push(ControlBlock {
                opened_at_line: line,
                has_else: false,
                kind: BlockKind::Case { has_when: false },
            });
            write!(template.skeleton, "{{% case {selector} %}}")
                .expect("writing to a String cannot fail");
        }
        Directive::When(values) => {
            let block = matching_block(blocks, "when", "case", line)?;
            ensure_before_else(block, "when", line)?;
            block.kind = BlockKind::Case { has_when: true };
            write!(template.skeleton, "{{% when {} %}}", values.join(", "))
                .expect("writing to a String cannot fail");
        }
        Directive::Else => {
            ensure_case_branch(blocks, line, "else")?;
            let block = blocks
                .last_mut()
                .ok_or_else(|| eyre::eyre!("line {line}: else has no matching if or case"))?;
            ensure_before_else(block, "else", line)?;
            block.has_else = true;
            template.skeleton.push_str("{% else %}");
        }
        Directive::Endif => {
            matching_block(blocks, "endif", "if", line)?;
            blocks.pop();
            template.skeleton.push_str("{% endif %}");
        }
        Directive::Endcase => {
            ensure_case_branch(blocks, line, "endcase")?;
            matching_block(blocks, "endcase", "case", line)?;
            blocks.pop();
            template.skeleton.push_str("{% endcase %}");
        }
    }
    Ok(())
}

fn record_condition(template: &mut ScannedTemplate, condition: &str, line: usize) {
    template
        .referenced_conditions
        .entry(condition.to_owned())
        .or_insert(line);
}

fn matching_block<'a>(
    blocks: &'a mut [ControlBlock],
    directive: &str,
    expected: &str,
    line: usize,
) -> eyre::Result<&'a mut ControlBlock> {
    let block = blocks
        .last_mut()
        .ok_or_else(|| eyre::eyre!("line {line}: {directive} has no matching {expected}"))?;
    eyre::ensure!(
        block.name() == expected,
        "line {line}: {directive} cannot close or branch {} opened at line {}; expected {}",
        block.name(),
        block.opened_at_line,
        block.end_name()
    );
    Ok(block)
}

fn ensure_before_else(block: &ControlBlock, directive: &str, line: usize) -> eyre::Result<()> {
    eyre::ensure!(
        !block.has_else,
        "line {line}: {directive} follows else in {} opened at line {}",
        block.name(),
        block.opened_at_line
    );
    Ok(())
}

fn ensure_case_branch(blocks: &[ControlBlock], line: usize, text: &str) -> eyre::Result<()> {
    if let Some(block) = blocks.last() {
        eyre::ensure!(
            !matches!(block.kind, BlockKind::Case { has_when: false }),
            "line {line}: {text} precedes the first when in case opened at line {}",
            block.opened_at_line
        );
    }
    Ok(())
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
        ["case", selector] => {
            eyre::ensure!(
                matches!(
                    *selector,
                    "minecraft_version" | "preset" | "environment" | "projection_key"
                ),
                "line {line_number}: case selector must be minecraft_version, preset, environment or projection_key"
            );
            Ok(Directive::Case(selector))
        }
        ["endcase"] => Ok(Directive::Endcase),
        ["when", ..] => Ok(Directive::When(parse_when_values(
            body.strip_prefix("when")
                .expect("matched when directive")
                .trim(),
            line_number,
        )?)),
        _ => eyre::bail!("line {line_number}: unsupported template directive: {body}"),
    }
}

fn parse_when_values(mut input: &str, line: usize) -> eyre::Result<Vec<&str>> {
    let mut values = Vec::new();
    loop {
        let quote = input
            .as_bytes()
            .first()
            .copied()
            .filter(|quote| matches!(quote, b'\'' | b'"'))
            .ok_or_else(|| eyre::eyre!("line {line}: when requires quoted string literals"))?;
        let closing = input[1..]
            .bytes()
            .position(|byte| byte == quote)
            .ok_or_else(|| eyre::eyre!("line {line}: unterminated when string literal"))?
            + 1;
        eyre::ensure!(
            !input[1..closing].contains('\\'),
            "line {line}: escaped when string literals are not supported"
        );
        values.push(&input[..=closing]);
        input = input[closing + 1..].trim_start();
        if input.is_empty() {
            return Ok(values);
        }
        input = if let Some(rest) = input.strip_prefix(',') {
            rest.trim_start()
        } else if let Some(rest) = input.strip_prefix("or") {
            eyre::ensure!(
                rest.starts_with(char::is_whitespace),
                "line {line}: when alternatives must be separated by comma or 'or'"
            );
            rest.trim_start()
        } else {
            eyre::bail!("line {line}: when alternatives must be separated by comma or 'or'");
        };
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
        assert!(template.referenced_selectors.is_empty());
    }

    #[test]
    fn grouped_string_cases_track_selectors_and_keep_literal_java_opaque() {
        let source = "{% case minecraft_version %}\r\n    {% when \"1.19.2\", \"1.19.4\" or '1.20' %}\r\nint[][] a = {{1, 2}};\r\n{% if features.enabled %}\r\n{% case preset %}\r\n{% when 'dev' %}\r\ndev();\r\n{% else %}\r\nrelease();\r\n{% endcase %}\r\n{% endif %}\r\n{% else %}\r\nmodern();\r\n{% endcase %}\r\n";
        let ScannedSource::Template(template) = scan(source).unwrap() else {
            panic!("case directives should create a template");
        };
        assert_eq!(
            template.referenced_selectors,
            BTreeMap::from([
                ("minecraft_version".to_owned(), 1),
                ("preset".to_owned(), 5)
            ])
        );
        assert_eq!(
            template.referenced_conditions,
            BTreeMap::from([("features.enabled".to_owned(), 4)])
        );
        assert!(
            template
                .skeleton
                .contains("{% when \"1.19.2\", \"1.19.4\", '1.20' %}")
        );
        assert!(
            template
                .chunks
                .iter()
                .any(|chunk| chunk.contains("{{1, 2}}"))
        );
    }

    #[test]
    fn supports_only_typed_string_case_selectors() {
        for selector in [
            "minecraft_version",
            "preset",
            "environment",
            "projection_key",
        ] {
            assert!(
                scan(&format!(
                    "{{% case {selector} %}}\n{{% when 'value' %}}\n{{% endcase %}}\n"
                ))
                .is_ok()
            );
        }
        for selector in [
            "features.enabled",
            "targets.forge",
            "typo",
            "'literal'",
            "42",
        ] {
            let error = scan(&format!("before\n{{% case {selector} %}}\n"))
                .unwrap_err()
                .to_string();
            assert!(error.contains("line 2: case selector"), "{error}");
        }
    }

    #[test]
    fn rejects_case_nesting_and_branch_errors_at_original_lines() {
        for (source, line, detail) in [
            ("{% when 'a' %}\n", 1, "no matching case"),
            ("{% endcase %}\n", 1, "no matching case"),
            (
                "{% case minecraft_version %}\n{% when 'a' %}\n{% endif %}\n",
                3,
                "expected endcase",
            ),
            ("{% if features.a %}\n{% endcase %}\n", 2, "expected endif"),
            (
                "{% case preset %}\n{% when 'a' %}\n{% elsif features.a %}\n",
                3,
                "expected endcase",
            ),
            ("{% if features.a %}\n{% when 'a' %}\n", 2, "expected endif"),
            (
                "{% case preset %}\n{% when 'a' %}\n{% else %}\n{% when 'b' %}\n",
                4,
                "follows else",
            ),
            (
                "{% case preset %}\n{% when 'a' %}\n{% else %}\n{% else %}\n",
                4,
                "follows else",
            ),
            (
                "{% case preset %}\n{% when 'a' %}\n",
                1,
                "no matching endcase",
            ),
            (
                "{% case preset %}\nbefore_when();\n",
                2,
                "precedes the first when",
            ),
            (
                "{% case preset %}\n{% if features.a %}\n",
                2,
                "precedes the first when",
            ),
            (
                "{% case preset %}\n{% else %}\n",
                2,
                "precedes the first when",
            ),
            (
                "{% case preset %}\n{% endcase %}\n",
                2,
                "precedes the first when",
            ),
        ] {
            let error = scan(source).unwrap_err().to_string();
            assert!(error.contains(&format!("line {line}:")), "{error}");
            assert!(error.contains(detail), "{error}");
        }
    }

    #[test]
    fn rejects_nonliteral_or_malformed_when_alternatives_with_source_lines() {
        for values in [
            "",
            "42",
            "true",
            "minecraft_version",
            "'a' 'b'",
            "'a',",
            "'a' or",
            "'a' or'b'",
            "'a',, 'b'",
            "'unterminated",
            "'a\\b'",
        ] {
            let error = scan(&format!("{{% case preset %}}\n{{% when {values} %}}\n"))
                .unwrap_err()
                .to_string();
            assert!(
                error.contains("line 2:"),
                "wrong source line for {values}: {error}"
            );
        }
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
