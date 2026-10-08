//! Parser-only bridge for Java's flexible constructor bodies (JEP 513).
//!
//! The bundled Java grammar requires delegation before any ordinary statement.
//! Replace only parser-recognized delegation keywords with same-width identifiers,
//! then prove that they are direct constructor-body statements in an error-free
//! tree. Callers retain the original source for every token and byte position.

use std::ops::Range;
use tree_sitter_patched_arborium::Node;
use tree_sitter_patched_arborium::Parser;
use tree_sitter_patched_arborium::Tree;

/// A complete parse with validated constructor delegations represented as calls.
pub(crate) struct AdaptedConstructor {
    pub tree: Tree,
    pub delegation_keywords: Vec<Range<usize>>,
}

/// Try only the flexible-constructor grammar gap after an ordinary parse failed.
///
/// The caller must use `source`, not the parser-only copy, to collect atom spelling.
/// Every replacement preserves byte width, so ranges and line positions are exact.
pub(crate) fn adapt(
    source: &str,
    parser: &mut Parser,
    original: &Tree,
) -> Result<Option<AdaptedConstructor>, String> {
    if !original.root_node().has_error() {
        return Ok(None);
    }
    let mut keywords = Vec::new();
    discover(original.root_node(), source, 0, &mut keywords)?;
    if keywords.is_empty() {
        return Ok(None);
    }
    let mut parser_source = source.as_bytes().to_vec();
    for range in &keywords {
        let replacement: &[u8] = match &source[range.clone()] {
            "this" => b"sfm$",
            "super" => b"sfm$$",
            _ => return Err("unexpected constructor delegation keyword".into()),
        };
        parser_source[range.clone()].copy_from_slice(replacement);
    }
    let parser_source = String::from_utf8(parser_source)
        .map_err(|error| format!("constructor adapter produced invalid UTF-8: {error}"))?;
    let tree = parser
        .parse(&parser_source, None)
        .ok_or("constructor adapter parser returned no tree")?;
    if tree.root_node().has_error() {
        return Err("Java parser reported unsupported or malformed syntax".into());
    }
    let mut bodies = Vec::new();
    for range in &keywords {
        let keyword = tree
            .root_node()
            .descendant_for_byte_range(range.start, range.end)
            .ok_or("constructor adapter lost keyword range")?;
        if keyword.kind() != "identifier" || keyword.byte_range() != *range {
            return Err("constructor delegation is not a complete identifier".into());
        }
        let invocation = keyword.parent().ok_or("missing constructor invocation")?;
        if invocation.kind() != "method_invocation"
            || invocation.child_by_field_name("name") != Some(keyword)
            || invocation.child_by_field_name("arguments").is_none()
            || invocation.child_by_field_name("object").is_some()
            || invocation.child_by_field_name("type_arguments").is_some()
        {
            return Err("unsupported constructor delegation form".into());
        }
        let statement = invocation.parent().ok_or("missing delegation statement")?;
        let body = statement.parent().ok_or("missing constructor body")?;
        if statement.kind() != "expression_statement"
            || body.kind() != "constructor_body"
            || body
                .parent()
                .is_none_or(|parent| parent.kind() != "constructor_declaration")
        {
            return Err("constructor delegation is not a direct constructor-body statement".into());
        }
        let body_range = body.byte_range();
        if bodies.contains(&body_range) {
            return Err("duplicate constructor delegation".into());
        }
        // An unadapted, ordinary first-position delegation can remain when its
        // keyword has intervening comments. It must not coexist with this one.
        let mut cursor = body.walk();
        if body
            .children(&mut cursor)
            .any(|child| child.kind() == "explicit_constructor_invocation")
        {
            return Err("duplicate constructor delegation".into());
        }
        bodies.push(body_range);
    }
    Ok(Some(AdaptedConstructor {
        tree,
        delegation_keywords: keywords,
    }))
}

fn discover(
    node: Node<'_>,
    source: &str,
    depth: usize,
    keywords: &mut Vec<Range<usize>>,
) -> Result<(), String> {
    if depth > 256 || keywords.len() > 1024 {
        return Err("constructor adapter complexity limit exceeded".into());
    }
    let kind = node.kind();
    if kind.ends_with("_literal") || matches!(kind, "line_comment" | "block_comment") {
        return Ok(());
    }
    if node.child_count() == 0 {
        if matches!(kind, "this" | "super") {
            let range = node.byte_range();
            if matches!(&source[range.clone()], "this" | "super")
                && source.as_bytes()[range.end..]
                    .iter()
                    .find(|byte| !byte.is_ascii_whitespace())
                    == Some(&b'(')
            {
                keywords.push(range);
            }
        }
        return Ok(());
    }
    let mut cursor = node.walk();
    for child in node.children(&mut cursor) {
        discover(child, source, depth + 1, keywords)?;
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn parser() -> Parser {
        let mut parser = Parser::new();
        parser
            .set_language(&arborium_java::language().into())
            .unwrap();
        parser
    }

    fn adapted(source: &str) -> Result<Option<AdaptedConstructor>, String> {
        let mut parser = parser();
        let original = parser.parse(source, None).unwrap();
        adapt(source, &mut parser, &original)
    }

    #[test]
    fn real_test_barrel_constructor_prologue_keeps_exact_positions() {
        // Exact constructor body from the released 26.1.2 oracle, wrapped only
        // with the original class name and its constructor signature.
        let source = r#"class TestBarrelTankContainerMenu {
    public TestBarrelTankContainerMenu(
            int containerId,
            Inventory inventory,
            TestBarrelTankBlockEntity blockEntity
    ) {
        FluidStacksResourceHandler tank = blockEntity.getTank();
        this(
                containerId,
                inventory,
                blockEntity,
                tank.getResource(0).toStack(tank.getAmountAsInt(0))
        );
    }
}"#;
        let original = parser().parse(source, None).unwrap();
        assert!(original.root_node().has_error());
        let result = adapted(source).unwrap().unwrap();
        assert!(!result.tree.root_node().has_error());
        assert_eq!(result.delegation_keywords.len(), 1);
        assert_eq!(&source[result.delegation_keywords[0].clone()], "this");
        assert_eq!(result.tree.root_node().end_byte(), source.len());
    }

    #[test]
    fn reduced_prologue_and_first_position_control_locate_the_grammar_gap() {
        assert!(
            parser()
                .parse("class A { A() { int x = 1; this(x); } }", None)
                .unwrap()
                .root_node()
                .has_error()
        );
        assert!(
            adapted("class A { A() { int x = 1; this(x); } }")
                .unwrap()
                .is_some()
        );
        assert!(adapted("class A { A() { this(1); } }").unwrap().is_none());
        assert!(
            adapted("class A { A() { int x = 1; super(x); done(); } }")
                .unwrap()
                .is_some()
        );
    }

    #[test]
    fn strings_comments_text_blocks_and_sentinel_identifiers_are_not_delegations() {
        let source = r#"class A { A() {
            String a = "this(fake)";
            String b = """
            super(fake)
            """;
            // this(fake);
            /* super(fake); */
            sfm$(fake);
            int x = 1;
            this(x);
        } }"#;
        let result = adapted(source).unwrap().unwrap();
        assert_eq!(result.delegation_keywords.len(), 1);
        assert_eq!(&source[result.delegation_keywords[0].clone()], "this");
    }

    #[test]
    fn malformed_misplaced_and_duplicate_delegations_fail_closed() {
        for source in [
            "class A { A() { int x = ; this(x); } }",
            "class A { A() { int x = 1; this(x) } }",
            "class A { void f() { int x = 1; this(x); } }",
            "class A { A() { if (yes) { this(1); } } }",
            "class A { A() { this(); int x = 1; super(x); } }",
            "class A { A() { int x = 1; this(x); this(x); } }",
            "class A { A() { int x = 1; consume(this(x)); } }",
            "class A { A() { int x = 1; outer.super(x); } }",
            "class A { A() { this /* comment */ (); int x = 1; super(x); } }",
        ] {
            assert!(
                !matches!(adapted(source), Ok(Some(_))),
                "adapter certified unsupported source: {source}"
            );
        }
    }
}
