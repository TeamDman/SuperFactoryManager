//! Conservative whitespace discovery in complete, already manifested Java.
//! No Liquid parsing, formatting, rewriting or oracle-policy changes.

use eyre::Result;
use eyre::ensure;
use facet::Facet;
use std::borrow::Cow;
use std::ops::Range;
use tree_sitter_patched_arborium::Node;
use tree_sitter_patched_arborium::Parser;

pub(crate) const ALGORITHM: &str = "sfm:manifested_java_whitespace@3";
pub(crate) const MAX_SOURCE_BYTES: usize = 1024 * 1024;
const MAX_ATOMS: usize = 100_000;
const MAX_REGIONS: usize = 128;

#[derive(Debug)]
struct Atom {
    key: String,
    // Ancestor kinds, not sibling indices or identifier spellings. This is a
    // candidate-discovery guard, never an equivalence certificate.
    ancestors: Vec<u16>,
    range: Range<usize>,
    start_line: usize,
    end_line: usize,
}

#[derive(Debug)]
pub(crate) struct ParsedJava {
    source: String,
    shape: String,
    atoms: Vec<Atom>,
}

#[derive(Debug, Facet)]
pub(crate) struct Snippet {
    pub start_byte: usize,
    pub end_byte: usize,
    pub start_line: usize,
    pub end_line: usize,
    pub text: String,
    pub text_truncated: bool,
}

#[derive(Debug, Facet)]
pub(crate) struct Region {
    pub before: Snippet,
    pub after: Snippet,
}

#[derive(Clone, Copy, Debug, Eq, Facet, PartialEq)]
#[facet(rename_all = "snake_case")]
#[repr(u8)]
pub(crate) enum ComparisonClassification {
    Exact,
    WhitespaceOnly,
    CodeOrCommentChange,
}

#[derive(Debug, Facet)]
pub(crate) struct Comparison {
    pub classification: ComparisonClassification,
    pub whitespace_gap_count: usize,
    pub structurally_unmatched_gap_count: usize,
    pub whitespace_candidates: Vec<Region>,
    pub changed_token_region_count: usize,
    pub changed_token_regions: Vec<Region>,
    pub regions_truncated: bool,
}

fn whitespace(bytes: &[u8]) -> bool {
    bytes
        .iter()
        .all(|byte| matches!(byte, b' ' | b'\t' | b'\r' | b'\n' | 0x0c))
}

pub(crate) fn parse(source: String) -> Result<ParsedJava> {
    ensure!(
        source.len() <= MAX_SOURCE_BYTES,
        "Java source exceeds 1 MiB"
    );
    let mut parser = Parser::new();
    parser.set_language(&arborium_java::language().into())?;
    let mut tree = parser
        .parse(&source, None)
        .ok_or_else(|| eyre::eyre!("Java parser returned no tree"))?;
    let mut delegation_keywords = Vec::new();
    if tree.root_node().has_error()
        && let Some(adapted) =
            super::simplify_flexible_constructor::adapt(&source, &mut parser, &tree)
                .map_err(|error| eyre::eyre!(error))?
    {
        tree = adapted.tree;
        delegation_keywords = adapted.delegation_keywords;
    }
    let root = tree.root_node();
    ensure!(
        !root.has_error(),
        "Java parser reported unsupported or malformed syntax"
    );
    let mut atoms = Vec::new();
    atoms.push(Atom {
        key: "<BOF>".into(),
        ancestors: Vec::new(),
        range: 0..0,
        start_line: 1,
        end_line: 1,
    });
    collect(root, &source, &delegation_keywords, 0, &mut atoms)?;
    let last_line = source.bytes().filter(|byte| *byte == b'\n').count() + 1;
    atoms.push(Atom {
        key: "<EOF>".into(),
        ancestors: Vec::new(),
        range: source.len()..source.len(),
        start_line: last_line,
        end_line: last_line,
    });
    for pair in atoms.windows(2) {
        ensure!(
            pair[0].range.end <= pair[1].range.start,
            "overlapping Java token ranges"
        );
        ensure!(
            whitespace(&source.as_bytes()[pair[0].range.end..pair[1].range.start]),
            "unrecognized bytes between Java tokens"
        );
    }
    Ok(ParsedJava {
        source,
        shape: root.to_sexp(),
        atoms,
    })
}

fn collect(
    node: Node<'_>,
    source: &str,
    delegation_keywords: &[Range<usize>],
    depth: usize,
    atoms: &mut Vec<Atom>,
) -> Result<()> {
    ensure!(
        depth <= 256 && atoms.len() < MAX_ATOMS,
        "Java syntax complexity limit exceeded"
    );
    ensure!(
        !node.is_missing() && !node.is_error(),
        "missing or erroneous Java syntax"
    );
    let kind = if delegation_keywords.contains(&node.byte_range()) {
        &source[node.byte_range()]
    } else {
        node.kind()
    };
    // Preserve comments and literals as complete atoms. Only physical line
    // terminators in block comments and text blocks are canonicalized below;
    // indentation, escapes and all other contents remain exact.
    if node.child_count() == 0
        || kind.ends_with("_literal")
        || matches!(kind, "line_comment" | "block_comment")
    {
        if node.end_byte() > node.start_byte() {
            let mut range = node.byte_range();
            // Java line terminators are outside a // comment. This grammar
            // includes CR in that node on CRLF lines; leave it in the gap so
            // Windows/Unix line endings do not appear to change comment text.
            if kind == "line_comment" && source[range.clone()].ends_with('\r') {
                range.end -= 1;
            }
            let raw = &source[range.clone()];
            validate_unicode_atom(kind, raw)?;
            atoms.push(Atom {
                key: format!("{}:{kind}{}", kind.len(), atom_spelling(kind, raw)),
                ancestors: ancestor_kinds(node),
                range,
                start_line: node.start_position().row + 1,
                end_line: node.end_position().row + 1,
            });
        }
    } else {
        let mut cursor = node.walk();
        for child in node.children(&mut cursor) {
            collect(child, source, delegation_keywords, depth + 1, atoms)?;
        }
    }
    Ok(())
}

fn atom_spelling<'a>(kind: &str, raw: &'a str) -> Cow<'a, str> {
    // JLS 3.4 treats CRLF as one line terminator. JLS 3.10.6 explicitly
    // normalizes physical CRLF/CR to LF before text-block indentation and
    // escape processing. Do not normalize escaped \\r, indentation, or text.
    if raw.contains('\r')
        && (kind == "block_comment" || (kind == "string_literal" && raw.starts_with("\"\"\"")))
    {
        Cow::Owned(raw.replace("\r\n", "\n").replace('\r', "\n"))
    } else {
        Cow::Borrowed(raw)
    }
}

fn validate_unicode_atom(kind: &str, raw: &str) -> Result<()> {
    if !raw.contains("\\u") {
        return Ok(());
    }
    ensure!(
        matches!(
            kind,
            "string_literal" | "character_literal" | "line_comment" | "block_comment"
        ),
        "Unicode escapes outside preserved literals/comments are not certified"
    );
    // JLS 3.3: only an eligible backslash starts an escape. Reject translated
    // backslashes and lexical delimiters, so translation cannot change token
    // boundaries or the eligibility of a later escape. Keep raw spelling exact.
    let bytes = raw.as_bytes();
    let mut index = 0;
    let mut slashes = 0;
    while index < bytes.len() {
        if bytes[index] != b'\\' {
            slashes = 0;
            index += 1;
            continue;
        }
        if slashes % 2 == 0 && bytes.get(index + 1) == Some(&b'u') {
            let mut digits = index + 1;
            while bytes.get(digits) == Some(&b'u') {
                digits += 1;
            }
            let hex = raw
                .get(digits..digits + 4)
                .ok_or_else(|| eyre::eyre!("incomplete Unicode escape"))?;
            let value = u16::from_str_radix(hex, 16)?;
            ensure!(
                !matches!(value, 0x0a | 0x0d | 0x22 | 0x27 | 0x2a | 0x2f | 0x5c),
                "Unicode escape may change lexical boundaries"
            );
            index = digits + 4;
            slashes = 0;
        } else {
            slashes += 1;
            index += 1;
        }
    }
    Ok(())
}

fn ancestor_kinds(node: Node<'_>) -> Vec<u16> {
    let mut kinds = Vec::new();
    let mut parent = node.parent();
    while let Some(node) = parent {
        kinds.push(node.kind_id());
        parent = node.parent();
    }
    kinds
}

fn snippet(parsed: &ParsedJava, range: Range<usize>, lines: (usize, usize)) -> Snippet {
    let text = &parsed.source[range.clone()];
    let mut end = text.len().min(1024);
    while !text.is_char_boundary(end) {
        end -= 1;
    }
    Snippet {
        start_byte: range.start,
        end_byte: range.end,
        start_line: lines.0,
        end_line: lines.1,
        text: text[..end].to_owned(),
        text_truncated: end != text.len(),
    }
}

fn token_region(parsed: &ParsedJava, tokens: Range<usize>) -> Snippet {
    let first = &parsed.atoms[tokens.start.min(parsed.atoms.len() - 1)];
    let last = &parsed.atoms[tokens
        .end
        .saturating_sub(1)
        .max(tokens.start)
        .min(parsed.atoms.len() - 1)];
    let end = if tokens.is_empty() {
        first.range.start
    } else {
        last.range.end
    };
    snippet(
        parsed,
        first.range.start..end,
        (
            first.start_line,
            if tokens.is_empty() {
                first.start_line
            } else {
                last.end_line
            },
        ),
    )
}

pub(crate) fn compare(before: &ParsedJava, after: &ParsedJava) -> Comparison {
    let mut input = gix::diff::blob::InternedInput::<&str>::default();
    input.update_before(before.atoms.iter().map(|atom| atom.key.as_str()));
    input.update_after(after.atoms.iter().map(|atom| atom.key.as_str()));
    let diff =
        gix::diff::blob::diff_with_slider_heuristics(gix::diff::blob::Algorithm::Histogram, &input);
    let equal_tokens = before
        .atoms
        .iter()
        .map(|a| &a.key)
        .eq(after.atoms.iter().map(|a| &a.key));
    let classification = if before.source == after.source {
        ComparisonClassification::Exact
    } else if equal_tokens && before.shape == after.shape {
        ComparisonClassification::WhitespaceOnly
    } else {
        ComparisonClassification::CodeOrCommentChange
    };
    let mut result = Comparison {
        classification,
        whitespace_gap_count: 0,
        structurally_unmatched_gap_count: 0,
        whitespace_candidates: Vec::new(),
        changed_token_region_count: 0,
        changed_token_regions: Vec::new(),
        regions_truncated: false,
    };
    let mut left = 0;
    let mut right = 0;
    for hunk in diff.hunks() {
        equal_run(
            before,
            after,
            left..hunk.before.start as usize,
            right,
            &mut result,
        );
        result.changed_token_region_count += 1;
        if result.changed_token_regions.len() < MAX_REGIONS {
            result.changed_token_regions.push(Region {
                before: token_region(before, hunk.before.start as usize..hunk.before.end as usize),
                after: token_region(after, hunk.after.start as usize..hunk.after.end as usize),
            });
        }
        left = hunk.before.end as usize;
        right = hunk.after.end as usize;
    }
    equal_run(before, after, left..before.atoms.len(), right, &mut result);
    result.regions_truncated = result.whitespace_gap_count > result.whitespace_candidates.len()
        || result.changed_token_region_count > result.changed_token_regions.len();
    result
}

fn equal_run(
    before: &ParsedJava,
    after: &ParsedJava,
    run: Range<usize>,
    after_start: usize,
    result: &mut Comparison,
) {
    for left in run.start..run.end.saturating_sub(1) {
        let right = after_start + left - run.start;
        let a = &before.atoms[left..=left + 1];
        let b = &after.atoms[right..=right + 1];
        let a_gap = &before.source[a[0].range.end..a[1].range.start];
        let b_gap = &after.source[b[0].range.end..b[1].range.start];
        if a_gap == b_gap {
            continue;
        }
        if a[0].ancestors != b[0].ancestors || a[1].ancestors != b[1].ancestors {
            result.structurally_unmatched_gap_count += 1;
            continue;
        }
        result.whitespace_gap_count += 1;
        if result.whitespace_candidates.len() < MAX_REGIONS {
            result.whitespace_candidates.push(Region {
                before: snippet(
                    before,
                    a[0].range.start..a[1].range.end,
                    (a[0].start_line, a[1].end_line),
                ),
                after: snippet(
                    after,
                    b[0].range.start..b[1].range.end,
                    (b[0].start_line, b[1].end_line),
                ),
            });
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn classification_preserves_wire_names_and_rejects_unknown_values() {
        for (classification, wire) in [
            (ComparisonClassification::Exact, "\"exact\""),
            (
                ComparisonClassification::WhitespaceOnly,
                "\"whitespace_only\"",
            ),
            (
                ComparisonClassification::CodeOrCommentChange,
                "\"code_or_comment_change\"",
            ),
        ] {
            assert_eq!(facet_json::to_string(&classification).unwrap(), wire);
            assert_eq!(
                facet_json::from_str::<ComparisonClassification>(wire).unwrap(),
                classification
            );
        }
        assert!(facet_json::from_str::<ComparisonClassification>("\"unknown\"").is_err());
    }

    fn comparison(a: &str, b: &str) -> Comparison {
        compare(&parse(a.into()).unwrap(), &parse(b.into()).unwrap())
    }

    #[test]
    fn whitespace_only_requires_complete_token_and_structure_equality() {
        let result = comparison("class A { int x; }", "\nclass A{\n int x;\n}\n");
        assert_eq!(
            result.classification,
            ComparisonClassification::WhitespaceOnly
        );
        assert!(result.whitespace_gap_count > 0);
        assert_eq!(result.changed_token_region_count, 0);
        assert_eq!(
            comparison("class A {}", "class A {}").classification,
            ComparisonClassification::Exact
        );
    }

    #[test]
    fn literal_comment_and_operator_changes_are_not_whitespace() {
        for (a, b) in [
            (
                "class A { String s = \"a b\"; }",
                "class A { String s = \"ab\"; }",
            ),
            ("class A { char c = ' '; }", "class A { char c = 'x'; }"),
            (
                "class A { /* a b */ int x; }",
                "class A { /* ab */ int x; }",
            ),
            (
                "class A { int x = a + ++b; }",
                "class A { int x = a++ + b; }",
            ),
            (
                "class A { String s = \"\"\"\n a\n \"\"\"; }",
                "class A { String s = \"\"\"\n  a\n \"\"\"; }",
            ),
        ] {
            assert_eq!(
                comparison(a, b).classification,
                ComparisonClassification::CodeOrCommentChange,
                "{a}"
            );
        }
    }

    #[test]
    fn crlf_line_terminators_are_whitespace_but_comment_contents_remain_exact() {
        assert_eq!(
            comparison(
                "class A { // keep\r\n int x; }",
                "class A { // keep\n int x; }"
            )
            .classification,
            ComparisonClassification::WhitespaceOnly
        );
        assert_eq!(
            comparison(
                "class A { // keep\r\n int x; }",
                "class A { // changed\n int x; }"
            )
            .classification,
            ComparisonClassification::CodeOrCommentChange
        );
        assert_eq!(
            comparison(
                "class A { /* keep\r\n text */ }",
                "class A { /* keep\n text */ }"
            )
            .classification,
            ComparisonClassification::WhitespaceOnly
        );
        assert_eq!(
            comparison(
                "class A { /* keep\r\n text */ }",
                "class A { /* keep\n changed */ }"
            )
            .classification,
            ComparisonClassification::CodeOrCommentChange
        );
    }

    #[test]
    fn text_block_physical_line_endings_are_equivalent_but_contents_are_exact() {
        let before = "class A { String s = \"\"\"\r\n  keep\\r\r\n  text\r\n  \"\"\"; }";
        let after = before.replace("\r\n", "\n");
        assert_eq!(
            comparison(before, &after).classification,
            ComparisonClassification::WhitespaceOnly
        );
        for changed in [
            after.replace("keep", "changed"),
            after.replace("\\r", "\\n"),
            after.replace("  text", "   text"),
        ] {
            assert_eq!(
                comparison(before, &changed).classification,
                ComparisonClassification::CodeOrCommentChange
            );
        }
        assert_eq!(
            comparison(
                r#"class A { String s = "keep\r\n"; }"#,
                r#"class A { String s = "keep\n"; }"#
            )
            .classification,
            ComparisonClassification::CodeOrCommentChange
        );
    }

    #[test]
    fn safe_unicode_literal_spelling_is_preserved_without_translating_source() {
        let before = r#"class A { String s = "\u00a7\\u00"; }"#;
        let after = r#"class A {  String s = "\u00a7\\u00"; }"#;
        assert_eq!(
            comparison(before, after).classification,
            ComparisonClassification::WhitespaceOnly
        );
        assert_eq!(
            comparison(before, &after.replace("00a7", "00a8")).classification,
            ComparisonClassification::CodeOrCommentChange
        );
        for source in [
            r#"class A { String s = "\u000a"; }"#,
            r#"class A { String s = "\u005c"; }"#,
            r#"class A { // \u000a int x;
}"#,
        ] {
            assert!(parse(source.into()).is_err(), "{source}");
        }
    }

    #[test]
    fn flexible_constructor_comparison_keeps_original_tokens_and_structure() {
        let before = "class A { A() { int x = 1; this(x); done(); } }";
        let after = "class A { A() {\n int x = 1;\n this( x );\n done(); } }";
        assert_eq!(
            comparison(before, after).classification,
            ComparisonClassification::WhitespaceOnly
        );
        for changed in [
            after.replace("this", "super"),
            after.replace("1", "2"),
            after.replace("done()", "changed()"),
            after.replace("this", "sfm$"),
        ] {
            assert_eq!(
                comparison(before, &changed).classification,
                ComparisonClassification::CodeOrCommentChange
            );
        }
        assert!(parse("class A { A() { if (yes) { this(1); } } }".into()).is_err());
    }

    #[test]
    fn local_candidates_do_not_certify_different_files() {
        let result = comparison(
            "import a.X; class A { A() {\n\n run(); } }",
            "import b.Y; class A { A() {\n run(); } }",
        );
        assert_eq!(
            result.classification,
            ComparisonClassification::CodeOrCommentChange
        );
        assert!(result.changed_token_region_count > 0);
        assert!(
            result
                .whitespace_candidates
                .iter()
                .any(|r| r.before.text.contains("\n\n"))
        );
    }

    #[test]
    fn manager_removal_nesting_is_not_a_formatting_candidate() {
        let result = comparison(
            "class ManagerBlock { void onRemove() {\n\n        if (!state.is(newState.getBlock())) {\n            if (level.getBlockEntity(pos) instanceof Container container) {\n                Containers.dropContents(level, pos, container);\n                level.updateNeighbourForOutputSignal(pos, this);\n            }\n            CableNetworkManager.onCableRemoved(level, pos);\n            super.onRemove(state, level, pos, newState, isMoving);\n        }\n    }\n}",
            "class ManagerBlock { void affectNeighborsAfterRemoval() {\n\n        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);\n        level.updateNeighbourForOutputSignal(pos, this);\n        CableNetworkManager.onCableRemoved(level, pos);\n    }\n}",
        );
        assert_eq!(
            result.classification,
            ComparisonClassification::CodeOrCommentChange
        );
        assert_eq!(result.whitespace_gap_count, 0);
        assert!(result.structurally_unmatched_gap_count > 0);
    }

    #[test]
    fn different_depth_and_equal_depth_different_parent_are_filtered() {
        for (a, b) in [
            (
                "class A { void f() { if (x) {\n        run();\n    } } }",
                "class A { void f() {\n    run();\n} }",
            ),
            (
                "class A { void f() { if (x) {\n        run();\n    } } }",
                "class A { void f() { while (x) {\n    run();\n} } }",
            ),
        ] {
            let result = comparison(a, b);
            assert_eq!(
                result.classification,
                ComparisonClassification::CodeOrCommentChange
            );
            assert_eq!(result.whitespace_gap_count, 0);
            assert!(result.structurally_unmatched_gap_count > 0);
        }
    }

    #[test]
    fn matching_nesting_still_reports_real_whitespace_drift() {
        let result = comparison(
            "class A { void f() { if (x) {\n        run();\n    } } }",
            "class A { void f() { if (x) {\n    run();\n} } }",
        );
        assert_eq!(
            result.classification,
            ComparisonClassification::WhitespaceOnly
        );
        assert!(result.whitespace_gap_count > 0);
        assert_eq!(result.structurally_unmatched_gap_count, 0);
    }

    #[test]
    fn ambiguous_or_unparsed_inputs_are_not_certified() {
        for source in [
            "class A {",
            "class A { \\u0061 }",
            "\u{feff}class A {}",
            "class A { // comment int x; }",
        ] {
            assert!(parse(source.into()).is_err(), "{source}");
        }
    }

    #[test]
    fn reports_bound_snippets_and_regions_without_hiding_counts() {
        let a = format!("class A {{ {} }}", "int x; ".repeat(300));
        let b = format!("class A {{ {} }}", "int  x; ".repeat(300));
        let result = comparison(&a, &b);
        assert!(result.regions_truncated);
        assert_eq!(result.whitespace_candidates.len(), MAX_REGIONS);
        assert_eq!(result.whitespace_gap_count, 300);
    }
}
