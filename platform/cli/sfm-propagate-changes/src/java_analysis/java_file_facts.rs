use super::JavaAnalysisDiagnosticOutput;
use super::JavaSourceFile;
use super::JavaSourceSpanOutput;
use super::JavaSymbolDefinitionOutput;
use super::JavaSymbolIdentityOutput;
use super::JavaSymbolKind;
use super::ResolutionConfidence;
use super::syntax::JAVA_PARSER_FINGERPRINT;
use super::syntax::JavaSyntaxFile;
use super::syntax::declaration_name_node;
use super::syntax::is_nonsemantic_literal_or_comment;
use super::syntax::is_type_declaration;
use super::syntax::named_children;
use facet::Facet;
use std::collections::BTreeSet;
use tree_sitter_patched_arborium::Node;

pub(crate) const JAVA_FILE_FACTS_SCHEMA: &str = "sfm.java-file-facts/1";

#[derive(Facet, Clone, Copy, Debug, Eq, PartialEq)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub(crate) enum JavaFileFactDetail {
    TypesOnly,
    Declarations,
}

/// Stable identity for one source in a query snapshot.
///
/// `sequence` is assigned after deterministic workspace sorting. It permits
/// worker completion to be nondeterministic without making sealed output order
/// depend on scheduling.
#[derive(Facet, Clone, Debug, Eq, PartialEq, Ord, PartialOrd)]
pub(crate) struct JavaFileFactIdentity {
    pub(crate) sequence: u64,
    pub(crate) report_path: String,
    pub(crate) source_set: String,
    pub(crate) source_hash: String,
}

#[derive(Facet, Clone, Debug, Eq, PartialEq, Ord, PartialOrd)]
pub(crate) struct JavaTypeImportFact {
    pub(crate) simple_name: String,
    pub(crate) qualified_name: String,
    pub(crate) span: JavaSourceSpanOutput,
}

#[derive(Facet, Clone, Debug, Eq, PartialEq, Ord, PartialOrd)]
pub(crate) struct JavaStaticImportFact {
    pub(crate) owner: String,
    pub(crate) member: String,
    pub(crate) span: JavaSourceSpanOutput,
}

#[derive(Facet, Clone, Debug, Eq, PartialEq, Ord, PartialOrd)]
pub(crate) struct JavaRawFieldFact {
    pub(crate) owner: String,
    pub(crate) name: String,
    pub(crate) raw_type: String,
    pub(crate) identifier_span: JavaSourceSpanOutput,
    pub(crate) declaration_span: JavaSourceSpanOutput,
}

#[derive(Facet, Clone, Debug, Eq, PartialEq, Ord, PartialOrd)]
pub(crate) struct JavaRawCallableFact {
    pub(crate) owner: String,
    pub(crate) name: String,
    pub(crate) kind: JavaSymbolKind,
    pub(crate) raw_parameter_types: Vec<String>,
    pub(crate) raw_return_type: String,
    pub(crate) identifier_span: JavaSourceSpanOutput,
    pub(crate) declaration_span: JavaSourceSpanOutput,
}

/// Complete declaration/link input extracted from one retained Java syntax
/// tree. A worker emits this once; the coordinator may admit it immediately
/// while other workers are still parsing.
#[derive(Facet, Clone, Debug, Eq, PartialEq)]
pub(crate) struct JavaFileFacts {
    pub(crate) schema: String,
    pub(crate) parser_fingerprint: String,
    pub(crate) detail: JavaFileFactDetail,
    pub(crate) file: JavaFileFactIdentity,
    pub(crate) visible_source_sets: Vec<String>,
    pub(crate) package_name: String,
    pub(crate) direct_type_imports: Vec<JavaTypeImportFact>,
    pub(crate) wildcard_packages: Vec<String>,
    pub(crate) static_imports: Vec<JavaStaticImportFact>,
    pub(crate) static_wildcard_owners: Vec<String>,
    pub(crate) types: Vec<JavaSymbolDefinitionOutput>,
    pub(crate) fields: Vec<JavaRawFieldFact>,
    pub(crate) callables: Vec<JavaRawCallableFact>,
    pub(crate) referenced_type_names: Vec<String>,
    pub(crate) diagnostics: Vec<JavaAnalysisDiagnosticOutput>,
}

/// Source identity and visibility supplied independently from source text.
/// Keeping this input separate lets a worker read a file once and pass the
/// resulting `String` directly into the text extraction path.
#[derive(Clone, Copy, Debug)]
pub(crate) struct JavaFileFactsInput<'a> {
    pub(crate) sequence: u64,
    pub(crate) report_path: &'a str,
    pub(crate) source_set: &'a str,
    pub(crate) visible_source_sets: &'a [String],
}

/// Read and parse one catalogued source, then release its syntax tree after all
/// owned facts have been extracted.
pub(crate) fn extract_java_file_facts(
    file: &JavaSourceFile,
    sequence: u64,
    visible_source_sets: &[String],
    diagnostic_limit: Option<usize>,
) -> eyre::Result<JavaFileFacts> {
    let syntax = JavaSyntaxFile::parse_with_diagnostic_limit(file, diagnostic_limit)?;
    build_java_file_facts(sequence, visible_source_sets, syntax)
}

/// Parse an already-read source exactly once and extract one complete owned
/// fact record. This is the worker-oriented entry point.
#[cfg(test)]
pub(crate) fn extract_java_file_facts_from_text(
    input: JavaFileFactsInput<'_>,
    source: String,
    diagnostic_limit: Option<usize>,
) -> eyre::Result<JavaFileFacts> {
    extract_java_file_facts_from_text_with_detail(
        input,
        source,
        diagnostic_limit,
        JavaFileFactDetail::Declarations,
    )
}

pub(crate) fn extract_java_file_facts_from_text_with_detail(
    input: JavaFileFactsInput<'_>,
    source: String,
    diagnostic_limit: Option<usize>,
    detail: JavaFileFactDetail,
) -> eyre::Result<JavaFileFacts> {
    let syntax = JavaSyntaxFile::parse_text_with_diagnostic_limit(
        input.report_path,
        input.source_set,
        source,
        diagnostic_limit,
    )?;
    build_java_file_facts_with_detail(input.sequence, input.visible_source_sets, syntax, detail)
}

#[derive(Default)]
struct DeclarationFacts {
    types: Vec<JavaSymbolDefinitionOutput>,
    fields: Vec<JavaRawFieldFact>,
    callables: Vec<JavaRawCallableFact>,
    referenced_type_names: BTreeSet<String>,
}

fn build_java_file_facts(
    sequence: u64,
    visible_source_sets: &[String],
    syntax: JavaSyntaxFile,
) -> eyre::Result<JavaFileFacts> {
    build_java_file_facts_with_detail(
        sequence,
        visible_source_sets,
        syntax,
        JavaFileFactDetail::Declarations,
    )
}

fn build_java_file_facts_with_detail(
    sequence: u64,
    visible_source_sets: &[String],
    syntax: JavaSyntaxFile,
    detail: JavaFileFactDetail,
) -> eyre::Result<JavaFileFacts> {
    let mut declarations = DeclarationFacts::default();
    collect_declaration_facts(
        &syntax,
        syntax.tree.root_node(),
        None,
        None,
        detail,
        &mut declarations,
    );

    let (mut direct_type_imports, wildcard_packages, mut static_imports, static_wildcard_owners) =
        if detail == JavaFileFactDetail::Declarations {
            (
                syntax
                    .imports
                    .direct_types
                    .iter()
                    .flat_map(|(simple_name, imports)| {
                        imports.iter().map(|import| JavaTypeImportFact {
                            simple_name: simple_name.clone(),
                            qualified_name: import.qualified_name.clone(),
                            span: import.span.clone(),
                        })
                    })
                    .collect::<Vec<_>>(),
                syntax
                    .imports
                    .wildcard_packages
                    .iter()
                    .cloned()
                    .collect::<Vec<_>>(),
                syntax
                    .imports
                    .static_members
                    .iter()
                    .map(|import| JavaStaticImportFact {
                        owner: import.owner.clone(),
                        member: import.member.clone(),
                        span: import.span.clone(),
                    })
                    .collect::<Vec<_>>(),
                syntax
                    .imports
                    .static_wildcard_owners
                    .iter()
                    .cloned()
                    .collect::<Vec<_>>(),
            )
        } else {
            (Vec::new(), Vec::new(), Vec::new(), Vec::new())
        };
    let mut visibility = visible_source_sets.to_vec();
    visibility.push(syntax.source_set.clone());

    sort_dedup(&mut visibility);
    sort_dedup(&mut direct_type_imports);
    sort_dedup(&mut static_imports);
    sort_dedup(&mut declarations.types);
    sort_dedup(&mut declarations.fields);
    sort_dedup(&mut declarations.callables);
    let referenced_type_names = declarations
        .referenced_type_names
        .into_iter()
        .collect::<Vec<_>>();

    let facts = JavaFileFacts {
        schema: JAVA_FILE_FACTS_SCHEMA.to_owned(),
        parser_fingerprint: JAVA_PARSER_FINGERPRINT.to_owned(),
        detail,
        file: JavaFileFactIdentity {
            sequence,
            report_path: syntax.report_path.clone(),
            source_set: syntax.source_set.clone(),
            source_hash: syntax.source_hash.clone(),
        },
        visible_source_sets: visibility,
        package_name: syntax.package_name.clone(),
        direct_type_imports,
        wildcard_packages,
        static_imports,
        static_wildcard_owners,
        types: declarations.types,
        fields: declarations.fields,
        callables: declarations.callables,
        referenced_type_names,
        diagnostics: syntax.diagnostics.clone(),
    };

    // `facts` is entirely owned. Explicitly end the retained tree/source
    // lifetime before validation to keep the extraction boundary obvious.
    drop(syntax);
    facts.validate()?;
    Ok(facts)
}

/// Visit every semantically relevant declaration node once. Direct members are
/// identified by the containing type-body edge; nested/local types recursively
/// establish their own owner without a separate type or member pass.
fn collect_declaration_facts(
    file: &JavaSyntaxFile,
    node: Node<'_>,
    enclosing_owner: Option<&str>,
    direct_member_owner: Option<&str>,
    detail: JavaFileFactDetail,
    facts: &mut DeclarationFacts,
) {
    if is_nonsemantic_literal_or_comment(node.kind()) {
        return;
    }

    if detail == JavaFileFactDetail::Declarations
        && let Some(owner) = direct_member_owner
    {
        match node.kind() {
            "field_declaration" | "constant_declaration" => {
                collect_raw_fields(file, owner, node, facts);
            }
            "method_declaration" | "constructor_declaration" => {
                collect_raw_callable(file, owner, node, facts);
            }
            "enum_constant" => collect_enum_constant(file, owner, node, facts),
            _ => {}
        }
    }

    if is_type_declaration(node.kind()) {
        let Some(name_node) = declaration_name_node(node) else {
            return;
        };
        let Some(name) = file.text(name_node) else {
            return;
        };
        let qualified_name = enclosing_owner.map_or_else(
            || qualify_name(&file.package_name, name),
            |owner| format!("{owner}${name}"),
        );
        let owner = enclosing_owner.map_or_else(|| file.package_name.clone(), ToOwned::to_owned);
        facts.types.push(JavaSymbolDefinitionOutput {
            symbol: JavaSymbolIdentityOutput {
                kind: java_type_kind(node.kind()),
                owner,
                name: name.to_owned(),
                descriptor: None,
                qualified_name: qualified_name.clone(),
            },
            identifier_span: file.span(name_node),
            declaration_span: file.span(node),
            confidence: ResolutionConfidence::Resolved,
        });

        let body_range = node
            .child_by_field_name("body")
            .map(|body| body.byte_range());
        for child in named_children(node) {
            if body_range
                .as_ref()
                .is_some_and(|range| *range == child.byte_range())
            {
                for member in named_children(child) {
                    collect_declaration_facts(
                        file,
                        member,
                        Some(&qualified_name),
                        Some(&qualified_name),
                        detail,
                        facts,
                    );
                }
            } else {
                collect_declaration_facts(file, child, Some(&qualified_name), None, detail, facts);
            }
        }
        return;
    }

    for child in named_children(node) {
        collect_declaration_facts(file, child, enclosing_owner, None, detail, facts);
    }
}

fn collect_raw_fields(
    file: &JavaSyntaxFile,
    owner: &str,
    node: Node<'_>,
    facts: &mut DeclarationFacts,
) {
    let Some(type_node) = node.child_by_field_name("type") else {
        return;
    };
    let Some(raw_type) = file.text(type_node) else {
        return;
    };
    remember_referenced_type(raw_type, &mut facts.referenced_type_names);
    for declarator in named_children(node)
        .into_iter()
        .filter(|child| child.kind() == "variable_declarator")
    {
        let Some(name_node) = declarator.child_by_field_name("name") else {
            continue;
        };
        let Some(name) = file.text(name_node) else {
            continue;
        };
        facts.fields.push(JavaRawFieldFact {
            owner: owner.to_owned(),
            name: name.to_owned(),
            raw_type: raw_type.to_owned(),
            identifier_span: file.span(name_node),
            declaration_span: file.span(node),
        });
    }
}

fn collect_enum_constant(
    file: &JavaSyntaxFile,
    owner: &str,
    node: Node<'_>,
    facts: &mut DeclarationFacts,
) {
    let Some(name_node) = declaration_name_node(node) else {
        return;
    };
    let Some(name) = file.text(name_node) else {
        return;
    };
    facts.referenced_type_names.insert(owner.to_owned());
    facts.fields.push(JavaRawFieldFact {
        owner: owner.to_owned(),
        name: name.to_owned(),
        raw_type: owner.to_owned(),
        identifier_span: file.span(name_node),
        declaration_span: file.span(node),
    });
}

fn collect_raw_callable(
    file: &JavaSyntaxFile,
    owner: &str,
    node: Node<'_>,
    facts: &mut DeclarationFacts,
) {
    let Some(name_node) = declaration_name_node(node) else {
        return;
    };
    let constructor = node.kind() == "constructor_declaration";
    let name = if constructor {
        "<init>".to_owned()
    } else {
        file.text(name_node).unwrap_or("<unknown>").to_owned()
    };
    let raw_return_type = if constructor {
        "void".to_owned()
    } else {
        node.child_by_field_name("type")
            .and_then(|type_node| file.text(type_node))
            .unwrap_or("<unresolved>")
            .to_owned()
    };
    let raw_parameter_types = node
        .child_by_field_name("parameters")
        .map(named_children)
        .unwrap_or_default()
        .into_iter()
        .filter_map(|parameter| raw_parameter_type(file, parameter))
        .collect::<Vec<_>>();

    for raw_type in &raw_parameter_types {
        remember_referenced_type(raw_type, &mut facts.referenced_type_names);
    }
    remember_referenced_type(&raw_return_type, &mut facts.referenced_type_names);
    facts.callables.push(JavaRawCallableFact {
        owner: owner.to_owned(),
        name,
        kind: if constructor {
            JavaSymbolKind::Constructor
        } else {
            JavaSymbolKind::Method
        },
        raw_parameter_types,
        raw_return_type,
        identifier_span: file.span(name_node),
        declaration_span: file.span(node),
    });
}

fn raw_parameter_type(file: &JavaSyntaxFile, parameter: Node<'_>) -> Option<String> {
    let type_node = parameter.child_by_field_name("type").or_else(|| {
        (parameter.kind() == "spread_parameter")
            .then(|| {
                named_children(parameter)
                    .into_iter()
                    .find(|child| child.kind() != "modifiers")
            })
            .flatten()
    })?;
    let raw_type = file.text(type_node)?;
    Some(
        if parameter.kind() == "spread_parameter" && !raw_type.ends_with("...") {
            format!("{raw_type}...")
        } else {
            raw_type.to_owned()
        },
    )
}

fn remember_referenced_type(raw_type: &str, names: &mut BTreeSet<String>) {
    let base = erase_java_type(raw_type);
    if !base.is_empty()
        && base != "<unresolved>"
        && !matches!(
            base.as_str(),
            "boolean" | "byte" | "char" | "short" | "int" | "long" | "float" | "double" | "void"
        )
    {
        names.insert(base);
    }
}

fn erase_java_type(raw_type: &str) -> String {
    let mut value = raw_type.trim().replace("...", "[]");
    while value.starts_with('@') {
        let split = value.find(char::is_whitespace).unwrap_or(value.len());
        value = value[split..].trim_start().to_owned();
    }
    if let Some(bound) = value.strip_prefix("? extends ") {
        value = bound.to_owned();
    } else if value.starts_with('?') {
        "java.lang.Object".clone_into(&mut value);
    }
    let mut erased = String::new();
    let mut generic_depth = 0_usize;
    for character in value.chars() {
        match character {
            '<' => generic_depth += 1,
            '>' => generic_depth = generic_depth.saturating_sub(1),
            _ if generic_depth == 0 => erased.push(character),
            _ => {}
        }
    }
    let mut base = erased.trim();
    while let Some(component) = base.strip_suffix("[]") {
        base = component.trim();
    }
    base.to_owned()
}

fn java_type_kind(kind: &str) -> JavaSymbolKind {
    match kind {
        "interface_declaration" => JavaSymbolKind::Interface,
        "enum_declaration" => JavaSymbolKind::Enum,
        "record_declaration" => JavaSymbolKind::Record,
        "annotation_type_declaration" => JavaSymbolKind::Annotation,
        _ => JavaSymbolKind::Class,
    }
}

fn qualify_name(package: &str, simple_name: &str) -> String {
    if package.is_empty() {
        simple_name.to_owned()
    } else {
        format!("{package}.{simple_name}")
    }
}

impl JavaFileFacts {
    #[expect(
        clippy::too_many_lines,
        reason = "one validation boundary audits every field in the versioned worker record"
    )]
    pub(crate) fn validate(&self) -> eyre::Result<()> {
        if self.schema != JAVA_FILE_FACTS_SCHEMA {
            eyre::bail!("unsupported Java file facts schema `{}`", self.schema);
        }
        if self.parser_fingerprint != JAVA_PARSER_FINGERPRINT {
            eyre::bail!(
                "Java file facts parser fingerprint `{}` does not match `{JAVA_PARSER_FINGERPRINT}`",
                self.parser_fingerprint
            );
        }
        if self.detail == JavaFileFactDetail::TypesOnly
            && (!self.direct_type_imports.is_empty()
                || !self.wildcard_packages.is_empty()
                || !self.static_imports.is_empty()
                || !self.static_wildcard_owners.is_empty()
                || !self.fields.is_empty()
                || !self.callables.is_empty()
                || !self.referenced_type_names.is_empty())
        {
            eyre::bail!("types-only Java file facts contain declaration-link payloads");
        }
        if self.file.report_path.is_empty() {
            eyre::bail!("Java file facts report path is empty");
        }
        if self.file.source_set.is_empty() {
            eyre::bail!("Java file facts source set is empty");
        }
        if !self.file.source_hash.starts_with("blake3:") {
            eyre::bail!("Java file facts source hash is not a blake3 identity");
        }
        if !self
            .visible_source_sets
            .iter()
            .any(|source_set| source_set == &self.file.source_set)
        {
            eyre::bail!("Java file facts visibility omits its own source set");
        }
        if !is_strictly_sorted(&self.visible_source_sets)
            || !is_strictly_sorted(&self.direct_type_imports)
            || !is_strictly_sorted(&self.wildcard_packages)
            || !is_strictly_sorted(&self.static_imports)
            || !is_strictly_sorted(&self.static_wildcard_owners)
            || !is_strictly_sorted(&self.types)
            || !is_strictly_sorted(&self.fields)
            || !is_strictly_sorted(&self.callables)
            || !is_strictly_sorted(&self.referenced_type_names)
            || !is_strictly_sorted(&self.diagnostics)
        {
            eyre::bail!(
                "Java file facts vectors must be deterministically sorted and deduplicated"
            );
        }

        let spans =
            self.direct_type_imports
                .iter()
                .map(|import| &import.span)
                .chain(self.static_imports.iter().map(|import| &import.span))
                .chain(self.types.iter().flat_map(|definition| {
                    [&definition.identifier_span, &definition.declaration_span]
                }))
                .chain(
                    self.fields
                        .iter()
                        .flat_map(|field| [&field.identifier_span, &field.declaration_span]),
                )
                .chain(
                    self.callables.iter().flat_map(|callable| {
                        [&callable.identifier_span, &callable.declaration_span]
                    }),
                )
                .chain(
                    self.diagnostics
                        .iter()
                        .filter_map(|diagnostic| diagnostic.span.as_ref()),
                );
        for span in spans {
            if span.path != self.file.report_path
                || span.source_set != self.file.source_set
                || span.source_hash != self.file.source_hash
            {
                eyre::bail!(
                    "Java file facts span identity does not match source {}",
                    self.file.report_path
                );
            }
            if span.start_byte > span.end_byte {
                eyre::bail!(
                    "Java file facts span has an inverted byte range in {}",
                    self.file.report_path
                );
            }
        }
        if self.callables.iter().any(|callable| {
            !matches!(
                callable.kind,
                JavaSymbolKind::Method | JavaSymbolKind::Constructor
            )
        }) {
            eyre::bail!("Java callable facts may only contain methods or constructors");
        }
        if self.types.iter().any(|definition| {
            !matches!(
                definition.symbol.kind,
                JavaSymbolKind::Class
                    | JavaSymbolKind::Interface
                    | JavaSymbolKind::Enum
                    | JavaSymbolKind::Record
                    | JavaSymbolKind::Annotation
            ) || definition.confidence != ResolutionConfidence::Resolved
        }) {
            eyre::bail!("Java file type facts may only contain resolved type declarations");
        }
        let owners = self
            .types
            .iter()
            .map(|definition| definition.symbol.qualified_name.as_str())
            .collect::<BTreeSet<_>>();
        if self
            .fields
            .iter()
            .map(|field| field.owner.as_str())
            .chain(
                self.callables
                    .iter()
                    .map(|callable| callable.owner.as_str()),
            )
            .any(|owner| !owners.contains(owner))
        {
            eyre::bail!("Java member facts contain an owner not declared by the same file");
        }
        let referenced = self.referenced_type_names.iter().collect::<BTreeSet<_>>();
        for raw_type in self
            .fields
            .iter()
            .map(|field| field.raw_type.as_str())
            .chain(
                self.callables
                    .iter()
                    .flat_map(|callable| callable.raw_parameter_types.iter().map(String::as_str)),
            )
            .chain(
                self.callables
                    .iter()
                    .map(|callable| callable.raw_return_type.as_str()),
            )
        {
            let base = erase_java_type(raw_type);
            if !base.is_empty()
                && base != "<unresolved>"
                && !matches!(
                    base.as_str(),
                    "boolean"
                        | "byte"
                        | "char"
                        | "short"
                        | "int"
                        | "long"
                        | "float"
                        | "double"
                        | "void"
                )
                && !referenced.contains(&base)
            {
                eyre::bail!("Java file facts omit referenced declaration type `{base}`");
            }
        }
        Ok(())
    }
}

fn is_strictly_sorted<T: Ord>(values: &[T]) -> bool {
    values.windows(2).all(|pair| pair[0] < pair[1])
}

fn sort_dedup<T: Ord>(values: &mut Vec<T>) {
    values.sort();
    values.dedup();
}

#[cfg(test)]
mod tests {
    use super::super::syntax::java_parse_count;
    use super::super::syntax::reset_java_parse_count;
    use super::*;

    fn input(visible_source_sets: &[String]) -> JavaFileFactsInput<'_> {
        JavaFileFactsInput {
            sequence: 7,
            report_path: "source/p/Outer.java",
            source_set: "gametest",
            visible_source_sets,
        }
    }

    fn extract(source: &str) -> JavaFileFacts {
        extract_java_file_facts_from_text(
            input(&["main".to_owned(), "gametest".to_owned()]),
            source.to_owned(),
            None,
        )
        .expect("Java facts should extract")
    }

    #[test]
    fn java_file_facts_extracts_nested_types_and_raw_members_in_one_parse() {
        reset_java_parse_count();
        let facts = extract(
            "package p;\n\
             import java.util.List;\n\
             class Outer {\n\
               int left, right;\n\
               Outer(String name) {}\n\
               List<String> convert(Inner... values) { return null; }\n\
               enum Kind { ONE, TWO; }\n\
               class Inner { long value; }\n\
             }\n",
        );

        assert_eq!(java_parse_count(), 1);
        let type_names = facts
            .types
            .iter()
            .map(|definition| definition.symbol.qualified_name.as_str())
            .collect::<Vec<_>>();
        assert_eq!(type_names, ["p.Outer", "p.Outer$Inner", "p.Outer$Kind"]);
        assert!(facts.fields.iter().any(|field| {
            field.owner == "p.Outer" && field.name == "left" && field.raw_type == "int"
        }));
        assert!(facts.fields.iter().any(|field| {
            field.owner == "p.Outer$Kind" && field.name == "ONE" && field.raw_type == "p.Outer$Kind"
        }));
        assert!(facts.fields.iter().any(|field| {
            field.owner == "p.Outer$Inner" && field.name == "value" && field.raw_type == "long"
        }));
        assert!(facts.callables.iter().any(|callable| {
            callable.owner == "p.Outer"
                && callable.name == "<init>"
                && callable.kind == JavaSymbolKind::Constructor
                && callable.raw_parameter_types == ["String"]
                && callable.raw_return_type == "void"
        }));
        assert!(
            facts.callables.iter().any(|callable| {
                callable.owner == "p.Outer"
                    && callable.name == "convert"
                    && callable.raw_parameter_types == ["Inner..."]
                    && callable.raw_return_type == "List<String>"
            }),
            "callables: {:?}",
            facts.callables
        );
        assert_eq!(
            facts.referenced_type_names,
            ["Inner", "List", "String", "p.Outer$Kind"]
        );
    }

    #[test]
    fn java_file_facts_normalizes_imports_and_visibility() {
        let facts = extract_java_file_facts_from_text(
            input(&["main".to_owned(), "gametest".to_owned(), "main".to_owned()]),
            "package p;\n\
             import z.B;\n\
             import a.B;\n\
             import q.*;\n\
             import static util.Constants.VALUE;\n\
             import static util.More.*;\n\
             class Outer { B value; }\n"
                .to_owned(),
            None,
        )
        .expect("Java facts should extract");

        assert_eq!(facts.visible_source_sets, ["gametest", "main"]);
        assert_eq!(
            facts
                .direct_type_imports
                .iter()
                .map(|import| (import.simple_name.as_str(), import.qualified_name.as_str()))
                .collect::<Vec<_>>(),
            [("B", "a.B"), ("B", "z.B")]
        );
        assert_eq!(facts.wildcard_packages, ["q"]);
        assert_eq!(facts.static_imports.len(), 1);
        assert_eq!(facts.static_imports[0].owner, "util.Constants");
        assert_eq!(facts.static_imports[0].member, "VALUE");
        assert_eq!(facts.static_wildcard_owners, ["util.More"]);
    }

    #[test]
    fn java_file_facts_preserves_bounded_parse_diagnostics_and_spans() {
        let facts = extract_java_file_facts_from_text(
            input(&["main".to_owned()]),
            "package p; class Outer { void broken( { int nope = ; }".to_owned(),
            Some(1),
        )
        .expect("Arborium should produce a recovery tree");

        assert!(
            facts
                .diagnostics
                .iter()
                .any(|diagnostic| diagnostic.code == "java.parse-gap")
        );
        for span in facts
            .diagnostics
            .iter()
            .filter_map(|diagnostic| diagnostic.span.as_ref())
        {
            assert_eq!(span.path, facts.file.report_path);
            assert_eq!(span.source_set, facts.file.source_set);
            assert_eq!(span.source_hash, facts.file.source_hash);
        }
    }

    #[test]
    fn java_file_facts_path_entry_reads_and_parses_one_source() {
        let directory = tempfile::tempdir().expect("temporary source directory");
        let path = directory.path().join("A.java");
        std::fs::write(&path, "package p; class A { String value; }")
            .expect("temporary Java source should write");
        let file = JavaSourceFile {
            absolute_path: path,
            report_path: "source/p/A.java".to_owned(),
            source_set: "main".to_owned(),
        };

        reset_java_parse_count();
        let facts = extract_java_file_facts(&file, 2, &["main".to_owned()], None)
            .expect("path facts should extract");

        assert_eq!(java_parse_count(), 1);
        assert_eq!(facts.file.sequence, 2);
        assert_eq!(facts.file.report_path, "source/p/A.java");
        assert!(facts.file.source_hash.starts_with("blake3:"));
    }

    #[test]
    fn java_file_facts_rejects_wrong_schema_and_parser_fingerprint() {
        let mut facts = extract("package p; class Outer { String value; }");
        facts.schema = "sfm.java-file-facts/999".to_owned();
        assert!(facts.validate().is_err());

        let mut facts = extract("package p; class Outer { String value; }");
        facts.parser_fingerprint = "some-other-parser/1".to_owned();
        assert!(facts.validate().is_err());
    }

    #[test]
    fn java_file_facts_rejects_cross_file_spans_and_unsorted_sets() {
        let mut cross_file = extract("package p; class Outer { String value; }");
        cross_file.fields[0].identifier_span.path = "source/p/Other.java".to_owned();
        assert!(cross_file.validate().is_err());

        let mut unsorted = extract("package p; class Outer { String value; }");
        unsorted.visible_source_sets = vec!["main".to_owned(), "gametest".to_owned()];
        assert!(unsorted.validate().is_err());
    }

    #[test]
    fn java_file_facts_round_trip_through_facet_json() {
        let expected = extract("package p; class Outer { String value; }");
        let encoded = facet_json::to_string(&expected).expect("facts should encode");
        let decoded: JavaFileFacts = facet_json::from_str(&encoded).expect("facts should decode");
        assert_eq!(decoded, expected);
        decoded
            .validate()
            .expect("round-tripped facts should validate");
    }
}
