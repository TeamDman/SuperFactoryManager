//! Conservative whitespace discovery in complete, already manifested Java.
//! No Liquid parsing, formatting, rewriting or oracle-policy changes.

use eyre::Result;
use eyre::ensure;
use facet::Facet;
use std::ops::Range;
use tree_sitter_patched_arborium::Node;
use tree_sitter_patched_arborium::Parser;

pub(crate) const ALGORITHM: &str = "sfm:manifested_java_whitespace@1";
pub(crate) const MAX_SOURCE_BYTES: usize = 1024 * 1024;
const MAX_ATOMS: usize = 100_000;
const MAX_REGIONS: usize = 128;

#[derive(Debug)]
struct Atom {
    key: String,
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

#[derive(Debug, Facet)]
pub(crate) struct Comparison {
    pub classification: String,
    pub whitespace_gap_count: usize,
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
    // Java translates these before tokenization. The selected tree-sitter
    // grammar is not a javac Unicode-translation oracle; do not guess.
    ensure!(
        !source.contains("\\u"),
        "Unicode escapes are not certified by this scanner"
    );
    let mut parser = Parser::new();
    parser.set_language(&arborium_java::language().into())?;
    let tree = parser
        .parse(&source, None)
        .ok_or_else(|| eyre::eyre!("Java parser returned no tree"))?;
    let root = tree.root_node();
    ensure!(
        !root.has_error(),
        "Java parser reported unsupported or malformed syntax"
    );
    let mut atoms = Vec::new();
    atoms.push(Atom {
        key: "<BOF>".into(),
        range: 0..0,
        start_line: 1,
        end_line: 1,
    });
    collect(root, &source, 0, &mut atoms)?;
    let last_line = source.bytes().filter(|byte| *byte == b'\n').count() + 1;
    atoms.push(Atom {
        key: "<EOF>".into(),
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

fn collect(node: Node<'_>, source: &str, depth: usize, atoms: &mut Vec<Atom>) -> Result<()> {
    ensure!(
        depth <= 256 && atoms.len() < MAX_ATOMS,
        "Java syntax complexity limit exceeded"
    );
    ensure!(
        !node.is_missing() && !node.is_error(),
        "missing or erroneous Java syntax"
    );
    let kind = node.kind();
    // Preserve the complete spelling of comments and literals, including text
    // block indentation and line endings. Never trim or visit their fragments.
    if node.child_count() == 0
        || kind.ends_with("_literal")
        || matches!(kind, "line_comment" | "block_comment")
    {
        if node.end_byte() > node.start_byte() {
            let raw = &source[node.byte_range()];
            atoms.push(Atom {
                key: format!("{}:{kind}{raw}", kind.len()),
                range: node.byte_range(),
                start_line: node.start_position().row + 1,
                end_line: node.end_position().row + 1,
            });
        }
    } else {
        let mut cursor = node.walk();
        for child in node.children(&mut cursor) {
            collect(child, source, depth + 1, atoms)?;
        }
    }
    Ok(())
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
        "exact"
    } else if equal_tokens && before.shape == after.shape {
        "whitespace_only"
    } else {
        "code_or_comment_change"
    };
    let mut result = Comparison {
        classification: classification.into(),
        whitespace_gap_count: 0,
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
    fn comparison(a: &str, b: &str) -> Comparison {
        compare(&parse(a.into()).unwrap(), &parse(b.into()).unwrap())
    }

    #[test]
    fn whitespace_only_requires_complete_token_and_structure_equality() {
        let result = comparison("class A { int x; }", "\nclass A{\n int x;\n}\n");
        assert_eq!(result.classification, "whitespace_only");
        assert!(result.whitespace_gap_count > 0);
        assert_eq!(result.changed_token_region_count, 0);
        assert_eq!(
            comparison("class A {}", "class A {}").classification,
            "exact"
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
                "code_or_comment_change",
                "{a}"
            );
        }
    }

    #[test]
    fn local_candidates_do_not_certify_different_files() {
        let result = comparison(
            "import a.X; class A { A() {\n\n run(); } }",
            "import b.Y; class A { A() {\n run(); } }",
        );
        assert_eq!(result.classification, "code_or_comment_change");
        assert!(result.changed_token_region_count > 0);
        assert!(
            result
                .whitespace_candidates
                .iter()
                .any(|r| r.before.text.contains("\n\n"))
        );
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
