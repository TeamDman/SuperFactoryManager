use super::DiagnosticSeverity;
use super::JavaAnalysisContextOutput;
use super::JavaAnalysisDiagnosticOutput;
use super::JavaDependencyResolutionDefinition;
use super::JavaFileFactIdentity;
use super::JavaFileFacts;
use super::JavaRawCallableFact;
use super::JavaRawFieldFact;
use super::JavaSourceSpanOutput;
use super::JavaSymbolDefinitionOutput;
use super::JavaSymbolIndex;
use super::JavaSymbolKind;
use super::ResolutionConfidence;
use eyre::WrapErr;
use std::collections::BTreeMap;
use std::collections::BTreeSet;

pub(crate) struct JavaDefinitionLinker {
    context: JavaAnalysisContextOutput,
    dependency_source_sets: BTreeSet<String>,
    files: BTreeMap<u64, JavaFileFactIdentity>,
    file_keys: BTreeSet<(String, String)>,
    scopes: BTreeMap<u64, JavaFileResolutionScope>,
    types_by_name: BTreeMap<String, Vec<LinkTypeDeclaration>>,
    live_types: Vec<JavaSymbolDefinitionOutput>,
    members: BTreeMap<RawMemberKey, RawMemberFact>,
    watchers: BTreeMap<String, BTreeSet<RawMemberKey>>,
    linked_members: BTreeMap<RawMemberKey, LinkedMember>,
    diagnostics: Vec<JavaAnalysisDiagnosticOutput>,
}

#[derive(Clone, Debug, Eq, PartialEq, Ord, PartialOrd)]
struct LinkTypeDeclaration {
    qualified_name: String,
    source_set: String,
    live: bool,
}

#[derive(Clone, Debug)]
struct JavaFileResolutionScope {
    package_name: String,
    visible_source_sets: BTreeSet<String>,
    direct_types: BTreeMap<String, BTreeSet<String>>,
    wildcard_packages: Vec<String>,
}

#[derive(Clone, Debug, Eq, PartialEq, Ord, PartialOrd)]
struct RawMemberKey {
    sequence: u64,
    start_byte: u64,
    end_byte: u64,
    kind: JavaSymbolKind,
    owner: String,
    name: String,
}

#[derive(Clone, Debug)]
enum RawMemberFact {
    Field(JavaRawFieldFact),
    Callable(JavaRawCallableFact),
}

impl RawMemberFact {
    fn key(&self, sequence: u64) -> RawMemberKey {
        let (span, kind, owner, name) = match self {
            Self::Field(field) => (
                &field.identifier_span,
                JavaSymbolKind::Field,
                &field.owner,
                &field.name,
            ),
            Self::Callable(callable) => (
                &callable.identifier_span,
                callable.kind,
                &callable.owner,
                &callable.name,
            ),
        };
        RawMemberKey {
            sequence,
            start_byte: span.start_byte,
            end_byte: span.end_byte,
            kind,
            owner: owner.clone(),
            name: name.clone(),
        }
    }

    fn raw_types(&self) -> Vec<&str> {
        match self {
            Self::Field(field) => vec![field.raw_type.as_str()],
            Self::Callable(callable) => callable
                .raw_parameter_types
                .iter()
                .map(String::as_str)
                .chain(std::iter::once(callable.raw_return_type.as_str()))
                .collect(),
        }
    }
}

#[derive(Clone, Debug)]
struct LinkedMember {
    definition: JavaSymbolDefinitionOutput,
    diagnostics: Vec<JavaAnalysisDiagnosticOutput>,
}

#[derive(Clone, Debug)]
enum TypeResolutionFailure {
    Unresolved(String),
    Ambiguous(String),
    Inaccessible(String),
}

impl JavaDefinitionLinker {
    pub(crate) fn new(
        context: JavaAnalysisContextOutput,
        external: &[JavaDependencyResolutionDefinition],
    ) -> Self {
        let dependency_source_sets = external
            .iter()
            .map(|definition| definition.source_set.clone())
            .collect::<BTreeSet<_>>();
        let mut linker = Self {
            context,
            dependency_source_sets,
            files: BTreeMap::new(),
            file_keys: BTreeSet::new(),
            scopes: BTreeMap::new(),
            types_by_name: BTreeMap::new(),
            live_types: Vec::new(),
            members: BTreeMap::new(),
            watchers: BTreeMap::new(),
            linked_members: BTreeMap::new(),
            diagnostics: Vec::new(),
        };
        for definition in external.iter().filter(|definition| {
            matches!(
                definition.symbol.kind,
                JavaSymbolKind::Class
                    | JavaSymbolKind::Interface
                    | JavaSymbolKind::Enum
                    | JavaSymbolKind::Record
                    | JavaSymbolKind::Annotation
            )
        }) {
            linker.insert_type(&LinkTypeDeclaration {
                qualified_name: definition.symbol.qualified_name.clone(),
                source_set: definition.source_set.clone(),
                live: false,
            });
        }
        linker
    }

    pub(crate) fn ingest(&mut self, facts: JavaFileFacts) -> eyre::Result<()> {
        facts.validate()?;
        let sequence = facts.file.sequence;
        if self.files.contains_key(&sequence) {
            eyre::bail!("duplicate Java file facts sequence {sequence}");
        }
        let file_key = (
            facts.file.source_set.clone(),
            facts.file.report_path.clone(),
        );
        if !self.file_keys.insert(file_key) {
            eyre::bail!(
                "duplicate Java file facts source `{}` in source set `{}`",
                facts.file.report_path,
                facts.file.source_set
            );
        }

        let mut visible_source_sets = facts
            .visible_source_sets
            .iter()
            .cloned()
            .collect::<BTreeSet<_>>();
        visible_source_sets.extend(self.dependency_source_sets.iter().cloned());
        let mut direct_types = BTreeMap::<String, BTreeSet<String>>::new();
        for import in &facts.direct_type_imports {
            direct_types
                .entry(import.simple_name.clone())
                .or_default()
                .insert(import.qualified_name.clone());
        }
        self.scopes.insert(
            sequence,
            JavaFileResolutionScope {
                package_name: facts.package_name.clone(),
                visible_source_sets,
                direct_types,
                wildcard_packages: facts.wildcard_packages.clone(),
            },
        );
        self.files.insert(sequence, facts.file.clone());
        self.diagnostics.extend(facts.diagnostics);

        let mut inserted_type_names = BTreeSet::new();
        for definition in facts.types {
            inserted_type_names.extend(self.insert_type(&LinkTypeDeclaration {
                qualified_name: definition.symbol.qualified_name.clone(),
                source_set: definition.identifier_span.source_set.clone(),
                live: true,
            }));
            self.live_types.push(definition);
        }

        let mut new_member_keys = Vec::new();
        for member in facts
            .fields
            .into_iter()
            .map(RawMemberFact::Field)
            .chain(facts.callables.into_iter().map(RawMemberFact::Callable))
        {
            let key = member.key(sequence);
            if self.members.insert(key.clone(), member.clone()).is_some() {
                eyre::bail!("duplicate Java raw member fact {key:?}");
            }
            let scope = self
                .scopes
                .get(&sequence)
                .expect("scope is inserted before member registration");
            for raw_type in member.raw_types() {
                for candidate in candidate_type_names(raw_type, &key.owner, scope) {
                    self.watchers
                        .entry(candidate)
                        .or_default()
                        .insert(key.clone());
                }
            }
            new_member_keys.push(key);
        }

        let mut affected = new_member_keys.into_iter().collect::<BTreeSet<_>>();
        for type_name in inserted_type_names {
            if let Some(keys) = self.watchers.get(&type_name) {
                affected.extend(keys.iter().cloned());
            }
        }
        for key in affected {
            self.relink_member(&key)?;
        }
        Ok(())
    }

    pub(crate) fn seal(mut self, expected_files: usize) -> eyre::Result<JavaSymbolIndex> {
        if self.files.len() != expected_files {
            eyre::bail!(
                "Java definition snapshot expected {expected_files} files but received {}",
                self.files.len()
            );
        }
        for sequence in 0..expected_files {
            let sequence = u64::try_from(sequence)
                .wrap_err("Java definition snapshot sequence does not fit u64")?;
            if !self.files.contains_key(&sequence) {
                eyre::bail!("Java definition snapshot is missing file sequence {sequence}");
            }
        }
        if self.linked_members.len() != self.members.len() {
            eyre::bail!("Java definition snapshot contains an unlinked member");
        }

        let mut definitions = self.live_types;
        for linked in self.linked_members.into_values() {
            definitions.push(linked.definition);
            self.diagnostics.extend(linked.diagnostics);
        }
        definitions.sort();
        definitions.dedup();
        self.diagnostics.sort();
        self.diagnostics.dedup();
        let files = self.files.into_values().collect::<Vec<_>>();
        Ok(JavaSymbolIndex::from_linked_definitions(
            self.context,
            &files,
            definitions,
            self.diagnostics,
        ))
    }

    fn insert_type(&mut self, declaration: &LinkTypeDeclaration) -> BTreeSet<String> {
        let mut names = BTreeSet::from([declaration.qualified_name.clone()]);
        if declaration.qualified_name.contains('$') {
            names.insert(declaration.qualified_name.replace('$', "."));
        }
        for name in &names {
            let declarations = self.types_by_name.entry(name.clone()).or_default();
            declarations.push(declaration.clone());
            declarations.sort();
            declarations.dedup();
        }
        names
    }

    fn relink_member(&mut self, key: &RawMemberKey) -> eyre::Result<()> {
        let member = self
            .members
            .get(key)
            .cloned()
            .ok_or_else(|| eyre::eyre!("unknown Java raw member fact {key:?}"))?;
        let scope = self
            .scopes
            .get(&key.sequence)
            .cloned()
            .ok_or_else(|| eyre::eyre!("missing Java resolution scope for {key:?}"))?;
        let linked = match member {
            RawMemberFact::Field(field) => self.link_field(field, &scope),
            RawMemberFact::Callable(callable) => self.link_callable(callable, &scope),
        };
        self.linked_members.insert(key.clone(), linked);
        Ok(())
    }

    fn link_field(&self, field: JavaRawFieldFact, scope: &JavaFileResolutionScope) -> LinkedMember {
        let resolution = self.resolve_type(&field.raw_type, &field.owner, scope, false);
        let mut diagnostics = Vec::new();
        if let Err(failure) = &resolution {
            diagnostics.push(type_resolution_diagnostic(
                failure.clone(),
                field.identifier_span.clone(),
                "field type",
            ));
        }
        LinkedMember {
            definition: JavaSymbolDefinitionOutput {
                symbol: super::JavaSymbolIdentityOutput {
                    kind: JavaSymbolKind::Field,
                    owner: field.owner.clone(),
                    name: field.name.clone(),
                    descriptor: None,
                    qualified_name: format!("{}.{}", field.owner, field.name),
                },
                identifier_span: field.identifier_span,
                declaration_span: field.declaration_span,
                confidence: if resolution.is_ok() {
                    ResolutionConfidence::Resolved
                } else {
                    ResolutionConfidence::PartiallyResolved
                },
            },
            diagnostics,
        }
    }

    fn link_callable(
        &self,
        callable: JavaRawCallableFact,
        scope: &JavaFileResolutionScope,
    ) -> LinkedMember {
        let mut diagnostics = Vec::new();
        let mut parameter_descriptors = Vec::new();
        let mut complete = true;
        for raw_type in &callable.raw_parameter_types {
            match self.resolve_type(raw_type, &callable.owner, scope, false) {
                Ok((_, descriptor)) => parameter_descriptors.push(descriptor),
                Err(failure) => {
                    complete = false;
                    diagnostics.push(type_resolution_diagnostic(
                        failure,
                        callable.identifier_span.clone(),
                        "method parameter type",
                    ));
                }
            }
        }
        let return_descriptor =
            match self.resolve_type(&callable.raw_return_type, &callable.owner, scope, true) {
                Ok((_, descriptor)) => Some(descriptor),
                Err(failure) => {
                    complete = false;
                    diagnostics.push(type_resolution_diagnostic(
                        failure,
                        callable.identifier_span.clone(),
                        "method return type",
                    ));
                    None
                }
            };
        let descriptor = (complete
            && parameter_descriptors.len() == callable.raw_parameter_types.len())
        .then(|| {
            format!(
                "({}){}",
                parameter_descriptors.join(""),
                return_descriptor.as_deref().unwrap_or("V")
            )
        });
        let qualified_name = descriptor.as_ref().map_or_else(
            || format!("{}.{}", callable.owner, callable.name),
            |descriptor| format!("{}.{}{descriptor}", callable.owner, callable.name),
        );
        LinkedMember {
            definition: JavaSymbolDefinitionOutput {
                symbol: super::JavaSymbolIdentityOutput {
                    kind: callable.kind,
                    owner: callable.owner,
                    name: callable.name,
                    descriptor,
                    qualified_name,
                },
                identifier_span: callable.identifier_span,
                declaration_span: callable.declaration_span,
                confidence: if complete {
                    ResolutionConfidence::Resolved
                } else {
                    ResolutionConfidence::Unresolved
                },
            },
            diagnostics,
        }
    }

    fn resolve_type(
        &self,
        raw_type: &str,
        owner: &str,
        scope: &JavaFileResolutionScope,
        allow_void: bool,
    ) -> Result<(String, String), TypeResolutionFailure> {
        let (base, dimensions) = erase_java_type(raw_type);
        let primitive_descriptor = match base.as_str() {
            "boolean" => Some("Z"),
            "byte" => Some("B"),
            "char" => Some("C"),
            "short" => Some("S"),
            "int" => Some("I"),
            "long" => Some("J"),
            "float" => Some("F"),
            "double" => Some("D"),
            "void" if allow_void && dimensions == 0 => Some("V"),
            _ => None,
        };
        if let Some(descriptor) = primitive_descriptor {
            return Ok((
                format!("{base}{}", "[]".repeat(dimensions)),
                format!("{}{descriptor}", "[".repeat(dimensions)),
            ));
        }
        if base == "void" || base == "var" || base.is_empty() || base == "<unresolved>" {
            return Err(TypeResolutionFailure::Unresolved(raw_type.to_owned()));
        }
        let qualified_name = self.resolve_reference_type(&base, owner, scope)?;
        Ok((
            format!("{qualified_name}{}", "[]".repeat(dimensions)),
            format!(
                "{}L{};",
                "[".repeat(dimensions),
                qualified_name.replace('.', "/")
            ),
        ))
    }

    fn resolve_reference_type(
        &self,
        raw_name: &str,
        owner: &str,
        scope: &JavaFileResolutionScope,
    ) -> Result<String, TypeResolutionFailure> {
        let direct = unique_direct_import(scope, raw_name)?;
        let imported_nested = if let Some((head, tail)) = raw_name.split_once('.') {
            unique_direct_import(scope, head)?.map(|import| format!("{import}.{tail}"))
        } else {
            None
        };
        if let Some(candidate) = direct.or(imported_nested) {
            return self.resolve_candidates(
                &candidate,
                std::slice::from_ref(&candidate),
                scope,
                true,
            );
        }
        if raw_name.contains('.') && raw_name.chars().next().is_some_and(char::is_lowercase) {
            return self.resolve_candidates(raw_name, &[raw_name.to_owned()], scope, true);
        }
        let candidates = unqualified_candidates(raw_name, owner, scope);
        match self.resolve_candidates(raw_name, &candidates, scope, false) {
            Err(TypeResolutionFailure::Unresolved(_)) if is_java_lang_type(raw_name) => {
                Ok(format!("java.lang.{raw_name}"))
            }
            result => result,
        }
    }

    fn resolve_candidates(
        &self,
        display_name: &str,
        candidates: &[String],
        scope: &JavaFileResolutionScope,
        allow_qualified_java_lang: bool,
    ) -> Result<String, TypeResolutionFailure> {
        let mut visible = BTreeSet::new();
        let mut inaccessible = false;
        for candidate in candidates {
            let declarations = self
                .types_by_name
                .get(candidate)
                .cloned()
                .unwrap_or_default();
            let declarations = prefer_live_declarations(declarations);
            let available = declarations
                .iter()
                .filter(|declaration| scope.visible_source_sets.contains(&declaration.source_set))
                .collect::<Vec<_>>();
            inaccessible |= !declarations.is_empty() && available.is_empty();
            visible.extend(
                available
                    .into_iter()
                    .map(|declaration| declaration.qualified_name.clone()),
            );
        }
        match visible.len() {
            1 => Ok(visible
                .into_iter()
                .next()
                .expect("one visible declaration exists")),
            0 if inaccessible => Err(TypeResolutionFailure::Inaccessible(display_name.to_owned())),
            0 if allow_qualified_java_lang
                && candidates
                    .first()
                    .is_some_and(|candidate| is_known_java_lang_qualified_type(candidate)) =>
            {
                Ok(candidates[0].clone())
            }
            0 => Err(TypeResolutionFailure::Unresolved(display_name.to_owned())),
            _ => Err(TypeResolutionFailure::Ambiguous(display_name.to_owned())),
        }
    }
}

fn prefer_live_declarations(declarations: Vec<LinkTypeDeclaration>) -> Vec<LinkTypeDeclaration> {
    let live_names = declarations
        .iter()
        .filter(|declaration| declaration.live)
        .map(|declaration| declaration.qualified_name.clone())
        .collect::<BTreeSet<_>>();
    declarations
        .into_iter()
        .filter(|declaration| declaration.live || !live_names.contains(&declaration.qualified_name))
        .collect()
}

fn unique_direct_import(
    scope: &JavaFileResolutionScope,
    simple_name: &str,
) -> Result<Option<String>, TypeResolutionFailure> {
    let Some(imports) = scope.direct_types.get(simple_name) else {
        return Ok(None);
    };
    match imports.len() {
        0 => Ok(None),
        1 => Ok(imports.first().cloned()),
        _ => Err(TypeResolutionFailure::Ambiguous(simple_name.to_owned())),
    }
}

fn candidate_type_names(
    raw_type: &str,
    owner: &str,
    scope: &JavaFileResolutionScope,
) -> Vec<String> {
    let (base, _) = erase_java_type(raw_type);
    if is_primitive_or_void(&base) || base == "var" || base.is_empty() || base == "<unresolved>" {
        return Vec::new();
    }
    if let Ok(Some(import)) = unique_direct_import(scope, &base) {
        return vec![import];
    }
    if let Some((head, tail)) = base.split_once('.')
        && let Ok(Some(import)) = unique_direct_import(scope, head)
    {
        return vec![format!("{import}.{tail}")];
    }
    if base.contains('.') && base.chars().next().is_some_and(char::is_lowercase) {
        return vec![base];
    }
    unqualified_candidates(&base, owner, scope)
}

fn unqualified_candidates(
    raw_name: &str,
    owner: &str,
    scope: &JavaFileResolutionScope,
) -> Vec<String> {
    let mut candidates = Vec::new();
    let mut enclosing = Some(owner);
    while let Some(current) = enclosing {
        candidates.push(format!("{current}${raw_name}"));
        enclosing = current.rsplit_once('$').map(|(parent, _)| parent);
    }
    candidates.push(qualify_name(&scope.package_name, raw_name));
    for package in &scope.wildcard_packages {
        candidates.push(format!("{package}.{raw_name}"));
    }
    candidates.sort();
    candidates.dedup();
    candidates
}

fn erase_java_type(raw_type: &str) -> (String, usize) {
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
    let mut dimensions = 0;
    let mut base = erased.trim().to_owned();
    while let Some(component) = base.strip_suffix("[]") {
        dimensions += 1;
        base = component.trim().to_owned();
    }
    (base, dimensions)
}

fn type_resolution_diagnostic(
    failure: TypeResolutionFailure,
    span: JavaSourceSpanOutput,
    role: &str,
) -> JavaAnalysisDiagnosticOutput {
    let (code, message) = match failure {
        TypeResolutionFailure::Unresolved(name) => (
            "java.unresolved-type",
            format!("Could not resolve {role} `{name}` without guessing"),
        ),
        TypeResolutionFailure::Ambiguous(name) => (
            "java.ambiguous-type",
            format!("{role} `{name}` resolves to more than one visible source declaration"),
        ),
        TypeResolutionFailure::Inaccessible(name) => (
            "java.inaccessible-source-set-reference",
            format!("{role} `{name}` exists only in a source set that is not visible here"),
        ),
    };
    JavaAnalysisDiagnosticOutput {
        code: code.to_owned(),
        severity: DiagnosticSeverity::Warning,
        message,
        span: Some(span),
    }
}

fn qualify_name(package: &str, simple_name: &str) -> String {
    if package.is_empty() {
        simple_name.to_owned()
    } else {
        format!("{package}.{simple_name}")
    }
}

fn is_primitive_or_void(name: &str) -> bool {
    matches!(
        name,
        "boolean" | "byte" | "char" | "short" | "int" | "long" | "float" | "double" | "void"
    )
}

fn is_known_java_lang_qualified_type(candidate: &str) -> bool {
    candidate
        .strip_prefix("java.lang.")
        .is_some_and(is_java_lang_type)
}

fn is_java_lang_type(name: &str) -> bool {
    matches!(
        name,
        "Appendable"
            | "AutoCloseable"
            | "Boolean"
            | "Byte"
            | "Character"
            | "CharSequence"
            | "Class"
            | "ClassLoader"
            | "Cloneable"
            | "Comparable"
            | "Double"
            | "Enum"
            | "Error"
            | "Exception"
            | "Float"
            | "Integer"
            | "Iterable"
            | "Long"
            | "Math"
            | "Number"
            | "Object"
            | "Record"
            | "Runnable"
            | "RuntimeException"
            | "Short"
            | "String"
            | "StringBuilder"
            | "System"
            | "Thread"
            | "Throwable"
            | "Void"
    )
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::java_analysis::JavaClasspathMode;
    use crate::java_analysis::JavaFileFactsInput;
    use crate::java_analysis::JavaSourceFile;
    use crate::java_analysis::JavaSourceSetOutput;
    use crate::java_analysis::JavaSourceWorkspace;
    use crate::java_analysis::JavaSymbolSelector;
    use crate::java_analysis::SymbolCommandOutcome;
    use crate::java_analysis::definition_equivalence::compare_definition_indexes;
    use crate::java_analysis::extract_java_file_facts;
    use crate::java_analysis::extract_java_file_facts_from_text;

    fn context() -> JavaAnalysisContextOutput {
        JavaAnalysisContextOutput {
            branch: "1.19.2".to_owned(),
            minecraft_version: "1.19.2".to_owned(),
            java_release: "17".to_owned(),
            jdk: "fixture".to_owned(),
            source_roots: Vec::new(),
            source_sets: vec![JavaSourceSetOutput {
                id: "scenario".to_owned(),
                visible_source_sets: vec!["scenario".to_owned()],
            }],
            source_exclusions: Vec::new(),
            classpath_mode: JavaClasspathMode::Isolated,
            classpath_fingerprint: "fixture".to_owned(),
            parser_fingerprint: String::new(),
            index_fingerprint: String::new(),
        }
    }

    fn facts(sequence: u64, path: &str, source: &str) -> JavaFileFacts {
        extract_java_file_facts_from_text(
            JavaFileFactsInput {
                sequence,
                report_path: path,
                source_set: "scenario",
                visible_source_sets: &["scenario".to_owned()],
            },
            source.to_owned(),
            None,
        )
        .expect("fixture should extract")
    }

    fn selector(terms: &[&str]) -> JavaSymbolSelector {
        JavaSymbolSelector::parse_terms(
            &terms
                .iter()
                .map(|term| (*term).to_owned())
                .collect::<Vec<_>>(),
        )
        .expect("selector should parse")
    }

    #[test]
    fn linker_resolves_member_arriving_before_cross_file_type() {
        let mut linker = JavaDefinitionLinker::new(context(), &[]);
        linker
            .ingest(facts(
                0,
                "source/p/A.java",
                "package p; class A { B echo(B value) { return value; } }",
            ))
            .unwrap();
        let before = linker
            .linked_members
            .values()
            .next()
            .expect("method should be provisionally linked");
        assert_eq!(before.definition.symbol.descriptor, None);

        linker
            .ingest(facts(1, "source/p/B.java", "package p; class B {}"))
            .unwrap();
        let after = linker
            .linked_members
            .values()
            .next()
            .expect("method should be relinked");
        assert_eq!(
            after.definition.symbol.descriptor.as_deref(),
            Some("(Lp/B;)Lp/B;")
        );

        let index = linker.seal(2).unwrap();
        let report = index.definition(&selector(&["p.A", "echo(Lp/B;)Lp/B;"]));
        assert_eq!(report.outcome, SymbolCommandOutcome::Success);
    }

    #[test]
    fn linker_revises_early_resolution_after_late_visible_duplicate() {
        let mut linker = JavaDefinitionLinker::new(context(), &[]);
        linker
            .ingest(facts(
                0,
                "source/q/A.java",
                "package q; import p1.*; import p2.*; class A { B make() { return null; } }",
            ))
            .unwrap();
        linker
            .ingest(facts(1, "source/p1/B.java", "package p1; class B {}"))
            .unwrap();
        assert_eq!(
            linker
                .linked_members
                .values()
                .next()
                .unwrap()
                .definition
                .symbol
                .descriptor
                .as_deref(),
            Some("()Lp1/B;")
        );

        linker
            .ingest(facts(2, "source/p2/B.java", "package p2; class B {}"))
            .unwrap();
        let linked_member = linker.linked_members.values().next().unwrap();
        assert_eq!(linked_member.definition.symbol.descriptor, None);
        assert!(
            linked_member
                .diagnostics
                .iter()
                .any(|diagnostic| diagnostic.code == "java.ambiguous-type")
        );
    }

    #[test]
    fn linker_seal_is_deterministic_for_reordered_fact_arrival() {
        let a = facts(
            0,
            "source/p/A.java",
            "package p; class A { B echo(B value) { return value; } }",
        );
        let b = facts(1, "source/p/B.java", "package p; class B {}");

        let mut forward = JavaDefinitionLinker::new(context(), &[]);
        forward.ingest(a.clone()).unwrap();
        forward.ingest(b.clone()).unwrap();
        let forward = forward.seal(2).unwrap();

        let mut reversed = JavaDefinitionLinker::new(context(), &[]);
        reversed.ingest(b).unwrap();
        reversed.ingest(a).unwrap();
        let reversed = reversed.seal(2).unwrap();

        for selector in [
            selector(&["p.A"]),
            selector(&["p.B"]),
            selector(&["p.A", "echo(Lp/B;)Lp/B;"]),
        ] {
            assert_eq!(
                forward.definition(&selector),
                reversed.definition(&selector)
            );
        }
    }

    #[test]
    fn linker_seal_rejects_missing_snapshot_sequences() {
        let mut linker = JavaDefinitionLinker::new(context(), &[]);
        linker
            .ingest(facts(1, "source/p/B.java", "package p; class B {}"))
            .unwrap();
        let error = linker.seal(2).expect_err("sequence zero is missing");
        assert!(error.to_string().contains("expected 2 files"));
    }

    #[test]
    fn definition_linker_matches_legacy_definition_builder() {
        let temporary = tempfile::tempdir().unwrap();
        let a_path = temporary.path().join("A.java");
        let b_path = temporary.path().join("B.java");
        std::fs::write(
            &a_path,
            "package p; import java.util.List; class A { B echo(B value) { return value; } List<B> many(B... values) { return null; } }",
        )
        .unwrap();
        std::fs::write(&b_path, "package p; class B {}").unwrap();
        let workspace = JavaSourceWorkspace {
            context: context(),
            files: vec![
                JavaSourceFile {
                    absolute_path: a_path,
                    report_path: "source/p/A.java".to_owned(),
                    source_set: "scenario".to_owned(),
                },
                JavaSourceFile {
                    absolute_path: b_path,
                    report_path: "source/p/B.java".to_owned(),
                    source_set: "scenario".to_owned(),
                },
            ],
            diagnostics: Vec::new(),
            classpath_entries: Vec::new(),
        };
        let legacy = JavaSymbolIndex::build_definitions(&workspace).unwrap();
        let mut linker = JavaDefinitionLinker::new(context(), &[]);
        for (sequence, file) in workspace.files.iter().enumerate() {
            linker
                .ingest(
                    extract_java_file_facts(
                        file,
                        u64::try_from(sequence).unwrap(),
                        &["scenario".to_owned()],
                        None,
                    )
                    .unwrap(),
                )
                .unwrap();
        }
        let candidate = linker.seal(workspace.files.len()).unwrap();
        compare_definition_indexes(
            &legacy,
            &candidate,
            &[
                selector(&["p.A"]),
                selector(&["p.B"]),
                selector(&["p.A", "echo(Lp/B;)Lp/B;"]),
                selector(&["p.A", "many([Lp/B;)Ljava/util/List;"]),
            ],
        )
        .unwrap();
    }
}
