use super::DiagnosticSeverity;
use super::JavaAnalysisDiagnosticOutput;
use super::JavaSourceFile;
use super::JavaSourceSpanOutput;
use arborium_java::language as java_language;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::ops::Range;
use tree_sitter_patched_arborium::Node;
use tree_sitter_patched_arborium::Parser;
use tree_sitter_patched_arborium::Tree;

#[cfg(test)]
thread_local! {
    static JAVA_PARSE_COUNT: std::cell::Cell<usize> = const { std::cell::Cell::new(0) };
}

pub(crate) const JAVA_PARSER_FINGERPRINT: &str = "arborium-java/2.18.1";

pub(crate) struct JavaSyntaxFile {
    pub(crate) report_path: String,
    pub(crate) source_set: String,
    pub(crate) source: String,
    pub(crate) source_hash: String,
    pub(crate) tree: Tree,
    pub(crate) package_name: String,
    pub(crate) imports: JavaImports,
    pub(crate) diagnostics: Vec<JavaAnalysisDiagnosticOutput>,
}

#[derive(Clone, Debug, Default)]
pub(crate) struct JavaImports {
    pub(crate) direct_types: BTreeMap<String, Vec<JavaImport>>,
    pub(crate) wildcard_packages: BTreeSet<String>,
    pub(crate) static_members: Vec<JavaStaticImport>,
    pub(crate) static_wildcard_owners: BTreeSet<String>,
}

#[derive(Clone, Debug)]
pub(crate) struct JavaImport {
    pub(crate) qualified_name: String,
    pub(crate) span: JavaSourceSpanOutput,
}

#[derive(Clone, Debug)]
pub(crate) struct JavaStaticImport {
    pub(crate) owner: String,
    pub(crate) member: String,
    pub(crate) owner_span: JavaSourceSpanOutput,
    pub(crate) member_span: JavaSourceSpanOutput,
}

impl JavaSyntaxFile {
    pub(crate) fn parse_with_diagnostic_limit(
        file: &JavaSourceFile,
        diagnostic_limit: Option<usize>,
    ) -> eyre::Result<Self> {
        let source = match &file.source_override {
            Some(source) => source.clone(),
            None => std::fs::read_to_string(&file.absolute_path).map_err(|error| {
                eyre::eyre!(
                    "Failed to read Java source {}: {error}",
                    file.absolute_path.display()
                )
            })?,
        };
        Self::parse_text_with_diagnostic_limit(
            &file.report_path,
            &file.source_set,
            source,
            diagnostic_limit,
        )
    }

    #[cfg(test)]
    pub(crate) fn parse_text(
        report_path: &str,
        source_set: &str,
        source: String,
    ) -> eyre::Result<Self> {
        Self::parse_text_with_diagnostic_limit(report_path, source_set, source, None)
    }

    pub(crate) fn parse_text_with_diagnostic_limit(
        report_path: &str,
        source_set: &str,
        source: String,
        diagnostic_limit: Option<usize>,
    ) -> eyre::Result<Self> {
        #[cfg(test)]
        JAVA_PARSE_COUNT.with(|count| count.set(count.get() + 1));

        let mut parser = Parser::new();
        let language = java_language().into();
        parser
            .set_language(&language)
            .map_err(|error| eyre::eyre!("Failed to load Arborium Java grammar: {error}"))?;
        let tree = parser.parse(&source, None).ok_or_else(|| {
            eyre::eyre!("Arborium did not produce a parse tree for {report_path}")
        })?;
        let source_hash = format!("blake3:{}", blake3::hash(source.as_bytes()).to_hex());
        let package_name = find_package_name(tree.root_node(), &source).unwrap_or_default();
        let mut file = Self {
            report_path: report_path.to_owned(),
            source_set: source_set.to_owned(),
            source,
            source_hash,
            tree,
            package_name,
            imports: JavaImports::default(),
            diagnostics: Vec::new(),
        };
        file.imports = find_imports(&file);
        let mut diagnostics = Vec::new();
        let mut parse_gap_count = 0_usize;
        collect_parse_gaps(
            file.tree.root_node(),
            &file,
            &mut diagnostics,
            &mut parse_gap_count,
            diagnostic_limit,
        );
        if parse_gap_count > diagnostics.len() {
            diagnostics.push(JavaAnalysisDiagnosticOutput {
                code: "java.parse-gaps-suppressed".to_owned(),
                severity: DiagnosticSeverity::Warning,
                message: format!(
                    "Suppressed {} additional Java parse-gap diagnostics in {}",
                    parse_gap_count - diagnostics.len(),
                    file.report_path
                ),
                span: None,
            });
        }
        diagnostics.sort();
        diagnostics.dedup();
        file.diagnostics = diagnostics;
        Ok(file)
    }

    pub(crate) fn span(&self, node: Node<'_>) -> JavaSourceSpanOutput {
        self.span_for_range(node.byte_range())
    }

    pub(crate) fn span_for_range(&self, range: Range<usize>) -> JavaSourceSpanOutput {
        let start = point_for_offset(&self.source, range.start);
        let end = point_for_offset(&self.source, range.end);
        JavaSourceSpanOutput {
            path: self.report_path.clone(),
            source_set: self.source_set.clone(),
            source_hash: self.source_hash.clone(),
            start_byte: range.start as u64,
            end_byte: range.end as u64,
            start_line: start.0,
            start_column: start.1,
            end_line: end.0,
            end_column: end.1,
        }
    }

    pub(crate) fn text(&self, node: Node<'_>) -> Option<&str> {
        self.source.get(node.byte_range())
    }
}

#[cfg(test)]
pub(crate) fn reset_java_parse_count() {
    JAVA_PARSE_COUNT.with(|count| count.set(0));
}

#[cfg(test)]
pub(crate) fn java_parse_count() -> usize {
    JAVA_PARSE_COUNT.with(std::cell::Cell::get)
}

pub(crate) fn is_type_declaration(kind: &str) -> bool {
    matches!(
        kind,
        "class_declaration"
            | "interface_declaration"
            | "enum_declaration"
            | "record_declaration"
            | "annotation_type_declaration"
    )
}

pub(crate) fn declaration_name_node(node: Node<'_>) -> Option<Node<'_>> {
    node.child_by_field_name("name")
}

pub(crate) fn named_children(node: Node<'_>) -> Vec<Node<'_>> {
    let mut cursor = node.walk();
    node.named_children(&mut cursor).collect()
}

pub(crate) fn first_named_child(node: Node<'_>) -> Option<Node<'_>> {
    let mut cursor = node.walk();
    node.named_children(&mut cursor).next()
}

pub(crate) fn is_nonsemantic_literal_or_comment(kind: &str) -> bool {
    matches!(
        kind,
        "line_comment" | "block_comment" | "string_literal" | "character_literal" | "text_block"
    )
}

fn collect_parse_gaps(
    node: Node<'_>,
    file: &JavaSyntaxFile,
    diagnostics: &mut Vec<JavaAnalysisDiagnosticOutput>,
    parse_gap_count: &mut usize,
    diagnostic_limit: Option<usize>,
) {
    if node.kind() == "ERROR" || node.is_missing() {
        *parse_gap_count += 1;
        if diagnostic_limit.is_none_or(|limit| diagnostics.len() < limit) {
            diagnostics.push(JavaAnalysisDiagnosticOutput {
                code: "java.parse-gap".to_owned(),
                severity: DiagnosticSeverity::Warning,
                message: if node.is_missing() {
                    format!(
                        "Arborium inserted missing Java syntax node `{}`",
                        node.kind()
                    )
                } else {
                    "Arborium encountered unparsed Java syntax".to_owned()
                },
                span: Some(file.span(node)),
            });
        }
    }
    for child in named_children(node) {
        collect_parse_gaps(child, file, diagnostics, parse_gap_count, diagnostic_limit);
    }
}

fn find_package_name(root: Node<'_>, source: &str) -> Option<String> {
    named_children(root)
        .into_iter()
        .find(|child| child.kind() == "package_declaration")
        .and_then(|node| source.get(node.byte_range()))
        .map(|text| {
            text.trim_start_matches("package")
                .trim_end_matches(';')
                .trim()
                .to_owned()
        })
}

fn find_imports(file: &JavaSyntaxFile) -> JavaImports {
    let mut imports = JavaImports::default();
    for node in named_children(file.tree.root_node())
        .into_iter()
        .filter(|node| node.kind() == "import_declaration")
    {
        let Some(text) = file.text(node) else {
            continue;
        };
        let body = text.trim().trim_start_matches("import").trim();
        let (is_static, body) = body
            .strip_prefix("static ")
            .map_or((false, body), |body| (true, body.trim()));
        let normalized = body.trim_end_matches(';').trim();
        let Some(relative_start) = text.find(normalized) else {
            continue;
        };
        let absolute_start = node.start_byte() + relative_start;
        if is_static {
            if let Some(owner) = normalized.strip_suffix(".*") {
                imports.static_wildcard_owners.insert(owner.to_owned());
            } else if let Some((owner, member)) = normalized.rsplit_once('.') {
                let member_start = absolute_start + owner.len() + 1;
                imports.static_members.push(JavaStaticImport {
                    owner: owner.to_owned(),
                    member: member.to_owned(),
                    owner_span: file.span_for_range(absolute_start..absolute_start + owner.len()),
                    member_span: file.span_for_range(member_start..member_start + member.len()),
                });
            }
        } else if let Some(package) = normalized.strip_suffix(".*") {
            imports.wildcard_packages.insert(package.to_owned());
        } else if let Some(simple_name) = normalized.rsplit('.').next() {
            imports
                .direct_types
                .entry(simple_name.to_owned())
                .or_default()
                .push(JavaImport {
                    qualified_name: normalized.to_owned(),
                    span: file.span_for_range(absolute_start..absolute_start + normalized.len()),
                });
        }
    }
    for direct_imports in imports.direct_types.values_mut() {
        direct_imports.sort_by(|left, right| {
            (&left.qualified_name, &left.span).cmp(&(&right.qualified_name, &right.span))
        });
    }
    imports.static_members.sort_by(|left, right| {
        (
            &left.owner,
            &left.member,
            &left.owner_span,
            &left.member_span,
        )
            .cmp(&(
                &right.owner,
                &right.member,
                &right.owner_span,
                &right.member_span,
            ))
    });
    imports
}

#[expect(
    clippy::naive_bytecount,
    reason = "span conversion is cold diagnostic code and avoids a counting dependency"
)]
fn point_for_offset(source: &str, offset: usize) -> (u64, u64) {
    let bounded = offset.min(source.len());
    let prefix = &source.as_bytes()[..bounded];
    let line_start = prefix
        .iter()
        .rposition(|byte| *byte == b'\n')
        .map_or(0, |index| index + 1);
    let line = prefix.iter().filter(|byte| **byte == b'\n').count() + 1;
    let column = source[line_start..bounded].chars().count() + 1;
    (line as u64, column as u64)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn java_syntax_collects_import_shapes_and_one_based_spans() {
        let file = JavaSyntaxFile::parse_text(
            "source/example/A.java",
            "scenario",
            "package example;\nimport other.B;\nimport other.*;\nimport static util.C.VALUE;\nclass A { B value; }\n".to_owned(),
        )
        .expect("Java source should parse");

        assert_eq!(file.package_name, "example");
        assert_eq!(file.imports.direct_types["B"][0].qualified_name, "other.B");
        assert!(file.imports.wildcard_packages.contains("other"));
        assert_eq!(file.imports.static_members[0].owner, "util.C");
        assert_eq!(file.imports.static_members[0].member, "VALUE");
        assert_eq!(file.imports.static_members[0].owner_span.start_column, 15);
        assert_eq!(file.imports.static_members[0].owner_span.end_column, 21);
        assert_eq!(file.imports.static_members[0].member_span.start_column, 22);
        assert_eq!(file.imports.static_members[0].member_span.end_column, 27);
        assert_eq!(file.imports.direct_types["B"][0].span.start_line, 2);
        assert_eq!(file.imports.direct_types["B"][0].span.start_column, 8);
        assert!(file.diagnostics.is_empty());
    }

    #[test]
    fn java_syntax_preserves_conflicting_direct_imports() {
        let file = JavaSyntaxFile::parse_text(
            "source/example/A.java",
            "scenario",
            "package example;\nimport p.A;\nimport q.A;\nclass Consumer { A value; }\n".to_owned(),
        )
        .expect("Java source should parse");

        let imports = &file.imports.direct_types["A"];
        assert_eq!(imports.len(), 2);
        assert_eq!(imports[0].qualified_name, "p.A");
        assert_eq!(imports[1].qualified_name, "q.A");
    }

    #[test]
    fn java_syntax_reports_every_parse_gap_with_a_source_span() {
        let file = JavaSyntaxFile::parse_text(
            "source/example/Broken.java",
            "scenario",
            "package example; class Broken { void run( {".to_owned(),
        )
        .expect("Arborium should return a recovery tree");

        assert!(!file.diagnostics.is_empty());
        assert!(file.diagnostics.iter().all(|diagnostic| {
            diagnostic.code == "java.parse-gap" && diagnostic.span.is_some()
        }));
    }
}
