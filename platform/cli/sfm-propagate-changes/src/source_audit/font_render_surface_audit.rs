use super::AuditRuleDiagnostic;
use super::BranchSourceAuditReport;
use super::JavaCallSite;
use super::SourceLineCount;
use super::SourceProblem;
use arborium_java::language as java_language;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::path::Path;
use tree_sitter_patched_arborium::Node;
use tree_sitter_patched_arborium::Parser;

/// Declarative source-call policy loaded from `platform/minecraft/sfm.audit_rules`.
#[derive(Clone, Debug, Eq, PartialEq)]
pub(crate) struct AuditRules {
    deny_calls: Vec<CallRule>,
    permitted_callers: Vec<CallerRule>,
}

#[derive(Clone, Debug, Eq, PartialEq)]
struct CallRule {
    owner: String,
    member: String,
    descriptor: String,
}

#[derive(Clone, Debug, Eq, PartialEq)]
struct CallerRule {
    owner: String,
    member: String,
    descriptor: String,
}

impl AuditRules {
    /// # Errors
    ///
    /// Returns an error when the rule file cannot be read or does not use the audit-rule grammar.
    pub(crate) fn load(path: &Path) -> eyre::Result<Self> {
        let contents = std::fs::read_to_string(path).map_err(|error| {
            eyre::eyre!("Failed to read audit rule file {}: {error}", path.display())
        })?;
        Self::parse(&contents).map_err(|error| {
            eyre::eyre!(
                "Failed to parse audit rule file {}: {error}",
                path.display()
            )
        })
    }

    fn parse(contents: &str) -> Result<Self, String> {
        let mut deny_calls = Vec::new();
        let mut permitted_callers = Vec::new();
        for (line_index, source_line) in contents.lines().enumerate() {
            let line = source_line
                .split_once('#')
                .map_or(source_line, |(before_comment, _)| before_comment)
                .trim();
            if line.is_empty() {
                continue;
            }
            let parts = line.split_ascii_whitespace().collect::<Vec<_>>();
            if parts.len() != 5 {
                return Err(format!(
                    "line {} must have five fields, found {line:?}",
                    line_index + 1
                ));
            }
            match (parts[0], parts[1]) {
                ("DENY", "CALL") => deny_calls.push(CallRule {
                    owner: parts[2].to_string(),
                    member: parts[3].to_string(),
                    descriptor: parts[4].to_string(),
                }),
                ("PERMIT", "CALLER") => permitted_callers.push(CallerRule {
                    owner: parts[2].to_string(),
                    member: parts[3].to_string(),
                    descriptor: parts[4].to_string(),
                }),
                _ => {
                    return Err(format!(
                        "line {} must begin with `DENY CALL` or `PERMIT CALLER`, found {line:?}",
                        line_index + 1
                    ));
                }
            }
        }
        if deny_calls.is_empty() {
            return Err("expected at least one `DENY CALL` rule".to_string());
        }
        Ok(Self {
            deny_calls,
            permitted_callers,
        })
    }

    fn suspicious_member(&self, member: &str) -> bool {
        self.deny_calls
            .iter()
            .any(|rule| wildcard_matches(member, &rule.member))
    }

    fn denied_rule(
        &self,
        owner: &str,
        member: &str,
        descriptor: Option<&str>,
    ) -> Option<&CallRule> {
        self.deny_calls.iter().find(|rule| {
            rule.owner == owner
                && wildcard_matches(member, &rule.member)
                && descriptor_matches(&rule.descriptor, descriptor)
        })
    }

    fn is_permitted_caller(&self, caller: &Caller) -> bool {
        self.permitted_callers.iter().any(|rule| {
            rule.owner == caller.owner
                && wildcard_matches(&caller.member, &rule.member)
                && descriptor_matches(&rule.descriptor, caller.descriptor.as_deref())
        })
    }
}

/// A conservative index of return types declared in SFM Java sources.
///
/// The audit builds this before walking calls so a chained invocation such as
/// `SyntaxHelper.projectCanvasDocument(...).text()` can be resolved to the source-declared
/// return type instead of being confused with an unrelated API method named `text`.
#[derive(Clone, Debug, Default)]
pub(crate) struct JavaSourceTypeIndex {
    method_returns: BTreeMap<String, BTreeMap<String, BTreeSet<String>>>,
}

impl JavaSourceTypeIndex {
    /// # Errors
    ///
    /// Returns an error when the Arborium Java grammar cannot be loaded.
    pub(crate) fn build<'a>(sources: impl IntoIterator<Item = &'a str>) -> eyre::Result<Self> {
        let mut parser = Parser::new();
        let language = java_language().into();
        parser
            .set_language(&language)
            .map_err(|error| eyre::eyre!("Failed to load Arborium Java grammar: {error}"))?;

        let mut type_owners = BTreeSet::new();
        let mut method_drafts = Vec::new();
        for source in sources {
            let Some(tree) = parser.parse(source, None) else {
                continue;
            };
            let package_name = find_package_name(tree.root_node(), source).unwrap_or_default();
            let imports = find_imports(tree.root_node(), source);
            JavaSourceTypeCollector {
                source,
                package_name: &package_name,
                imports: &imports,
                type_owners: &mut type_owners,
                method_drafts: &mut method_drafts,
                owner_stack: Vec::new(),
            }
            .visit(tree.root_node());
        }

        let mut index = Self::default();
        for draft in method_drafts {
            let Some(return_type) = resolve_indexed_type_name(&draft, &type_owners) else {
                continue;
            };
            index
                .method_returns
                .entry(draft.owner)
                .or_default()
                .entry(draft.member)
                .or_default()
                .insert(return_type);
        }
        Ok(index)
    }

    fn method_return_type(&self, owner: &str, member: &str) -> Option<String> {
        let return_types = self.method_returns.get(owner)?.get(member)?;
        (return_types.len() == 1)
            .then(|| return_types.iter().next().cloned())
            .flatten()
    }
}

#[derive(Clone, Debug)]
struct MethodReturnDraft {
    owner: String,
    member: String,
    raw_return_type: String,
    package_name: String,
    imports: JavaImports,
}

struct JavaSourceTypeCollector<'a> {
    source: &'a str,
    package_name: &'a str,
    imports: &'a JavaImports,
    type_owners: &'a mut BTreeSet<String>,
    method_drafts: &'a mut Vec<MethodReturnDraft>,
    owner_stack: Vec<String>,
}

impl JavaSourceTypeCollector<'_> {
    fn visit(&mut self, node: Node<'_>) {
        if is_type_declaration(node.kind()) {
            self.visit_type_declaration(node);
        } else if node.kind() == "method_declaration" {
            self.collect_method_return(node);
        } else {
            self.visit_children(node);
        }
    }

    fn visit_type_declaration(&mut self, node: Node<'_>) {
        let Some(simple_name) = declaration_name(node, self.source) else {
            self.visit_children(node);
            return;
        };
        let owner = self.owner_stack.last().map_or_else(
            || qualify_java_name(self.package_name, &simple_name),
            |parent| format!("{parent}${simple_name}"),
        );
        self.type_owners.insert(owner.clone());
        self.owner_stack.push(owner);
        self.visit_children(node);
        let _ = self.owner_stack.pop();
    }

    fn collect_method_return(&mut self, node: Node<'_>) {
        let (Some(owner), Some(member), Some(raw_return_type)) = (
            self.owner_stack.last(),
            declaration_name(node, self.source),
            node.child_by_field_name("type")
                .and_then(|type_node| node_text(type_node, self.source)),
        ) else {
            return;
        };
        self.method_drafts.push(MethodReturnDraft {
            owner: owner.clone(),
            member,
            raw_return_type: raw_return_type.to_owned(),
            package_name: self.package_name.to_owned(),
            imports: self.imports.clone(),
        });
    }

    fn visit_children(&mut self, node: Node<'_>) {
        let mut cursor = node.walk();
        for child in node.named_children(&mut cursor) {
            self.visit(child);
        }
    }
}

fn resolve_indexed_type_name(
    draft: &MethodReturnDraft,
    type_owners: &BTreeSet<String>,
) -> Option<String> {
    resolve_indexed_type_name_raw(
        &draft.raw_return_type,
        &draft.owner,
        &draft.package_name,
        &draft.imports,
        type_owners,
    )
}

fn resolve_indexed_type_name_raw(
    raw_type: &str,
    owner: &str,
    package_name: &str,
    imports: &JavaImports,
    type_owners: &BTreeSet<String>,
) -> Option<String> {
    let (raw_base_type, raw_type_arguments) = split_parameterized_type(raw_type.trim());
    let simple_name = raw_base_type.trim_end_matches("[]").trim();
    if simple_name.is_empty() {
        return None;
    }
    let resolved_base_type = if is_primitive_or_void(simple_name) {
        simple_name.to_owned()
    } else if simple_name == "String" {
        "java.lang.String".to_owned()
    } else if let Some(imported) = imports.direct.get(simple_name) {
        imported.clone()
    } else if simple_name.contains('.') {
        simple_name.to_owned()
    } else {
        let mut enclosing_owner = Some(owner);
        let mut resolved_nested_type = None;
        while let Some(current_owner) = enclosing_owner {
            let nested_candidate = format!("{current_owner}${simple_name}");
            if type_owners.contains(&nested_candidate) {
                resolved_nested_type = Some(nested_candidate);
                break;
            }
            enclosing_owner = current_owner.rsplit_once('$').map(|(parent, _)| parent);
        }
        resolved_nested_type.or_else(|| {
            let package_candidate = qualify_java_name(package_name, simple_name);
            type_owners
                .contains(&package_candidate)
                .then_some(package_candidate)
        })?
    };

    let Some(raw_type_arguments) = raw_type_arguments else {
        return Some(resolved_base_type);
    };
    let resolved_type_arguments = split_java_type_arguments(raw_type_arguments)
        .into_iter()
        .map(|argument| {
            resolve_indexed_type_name_raw(argument, owner, package_name, imports, type_owners)
        })
        .collect::<Option<Vec<_>>>()?;
    Some(format!(
        "{resolved_base_type}<{}>",
        resolved_type_arguments.join(", ")
    ))
}

fn split_parameterized_type(type_name: &str) -> (&str, Option<&str>) {
    let Some(opening_angle_bracket) = type_name.find('<') else {
        return (type_name, None);
    };
    let Some(closing_angle_bracket) = type_name.rfind('>') else {
        return (type_name, None);
    };
    if closing_angle_bracket + 1 != type_name.len() {
        return (type_name, None);
    }
    (
        &type_name[..opening_angle_bracket],
        Some(&type_name[opening_angle_bracket + 1..closing_angle_bracket]),
    )
}

fn split_java_type_arguments(arguments: &str) -> Vec<&str> {
    let mut depth = 0usize;
    let mut start = 0usize;
    let mut values = Vec::new();
    for (index, character) in arguments.char_indices() {
        match character {
            '<' => depth += 1,
            '>' => depth = depth.saturating_sub(1),
            ',' if depth == 0 => {
                values.push(arguments[start..index].trim());
                start = index + character.len_utf8();
            }
            _ => {}
        }
    }
    values.push(arguments[start..].trim());
    values
}

fn list_get_return_type(receiver_type: &str, member: &str) -> Option<String> {
    if member != "get" {
        return None;
    }
    let (base_type, type_arguments) = split_parameterized_type(receiver_type);
    if base_type != "java.util.List" {
        return None;
    }
    let type_arguments = split_java_type_arguments(type_arguments?);
    (type_arguments.len() == 1).then(|| type_arguments[0].to_owned())
}

/// Audit a single Java source with a same-file return-type index.
///
/// Cross-source production auditing uses [`audit_java_font_render_surface_with_index`] instead.
#[cfg(test)]
pub(crate) fn audit_java_font_render_surface(
    report: &mut BranchSourceAuditReport,
    branch: &str,
    repo_path: &str,
    line_count: SourceLineCount,
    source: &str,
    rules: &AuditRules,
) -> eyre::Result<()> {
    let type_index = JavaSourceTypeIndex::build([source])?;
    audit_java_font_render_surface_with_index(
        report,
        branch,
        repo_path,
        line_count,
        source,
        rules,
        &type_index,
    )
}

/// # Errors
///
/// Returns an error if the Java source cannot be parsed with Arborium.
pub(crate) fn audit_java_font_render_surface_with_index(
    report: &mut BranchSourceAuditReport,
    branch: &str,
    repo_path: &str,
    line_count: SourceLineCount,
    source: &str,
    rules: &AuditRules,
    type_index: &JavaSourceTypeIndex,
) -> eyre::Result<()> {
    let mut parser = Parser::new();
    let language = java_language().into();
    parser
        .set_language(&language)
        .map_err(|error| eyre::eyre!("Failed to load Arborium Java grammar: {error}"))?;
    let tree = parser
        .parse(source, None)
        .ok_or_else(|| eyre::eyre!("Arborium did not produce a parse tree for {repo_path}"))?;
    if tree.root_node().has_error() {
        let error_node = first_error_node(tree.root_node()).unwrap_or(tree.root_node());
        report.push_problem(SourceProblem::audit_rule(
            branch,
            repo_path,
            line_count,
            error_node.start_position().row + 1,
            error_node.start_position().column + 1,
            AuditRuleDiagnostic::ParseFailure { parser: "Arborium" },
        ));
    }

    let package_name = find_package_name(tree.root_node(), source).unwrap_or_default();
    let imports = find_imports(tree.root_node(), source);
    let mut visitor = JavaAuditVisitor {
        report,
        branch,
        repo_path,
        line_count,
        source,
        rules,
        package_name,
        imports,
        classes: Vec::new(),
        known_class_fields: BTreeMap::new(),
        source_type_index: type_index,
        callers: Vec::new(),
        scopes: Vec::new(),
    };
    visitor.visit(tree.root_node());
    Ok(())
}

struct JavaAuditVisitor<'a> {
    report: &'a mut BranchSourceAuditReport,
    branch: &'a str,
    repo_path: &'a str,
    line_count: SourceLineCount,
    source: &'a str,
    rules: &'a AuditRules,
    package_name: String,
    imports: JavaImports,
    classes: Vec<ClassContext>,
    known_class_fields: BTreeMap<String, BTreeMap<String, String>>,
    source_type_index: &'a JavaSourceTypeIndex,
    callers: Vec<Caller>,
    scopes: Vec<BTreeMap<String, String>>,
}

#[derive(Clone, Debug)]
struct ClassContext {
    owner: String,
    superclass: Option<String>,
    fields: BTreeMap<String, String>,
    declared_members: Vec<DeclaredMember>,
}

#[derive(Clone, Debug)]
struct DeclaredMember {
    name: String,
    descriptor: Option<String>,
}

#[derive(Clone, Debug)]
enum ImplicitReceiverResolution {
    Resolved(String),
    KnownNonPolicy,
    Unresolved,
}

#[derive(Clone, Debug)]
struct Caller {
    owner: String,
    member: String,
    descriptor: Option<String>,
    declaration: String,
    returns_value: bool,
}

impl JavaAuditVisitor<'_> {
    fn visit(&mut self, node: Node<'_>) {
        match node.kind() {
            kind if is_type_declaration(kind) => self.visit_type_declaration(node),
            "method_declaration" | "constructor_declaration" => self.visit_method_declaration(node),
            "block" | "constructor_body" => self.visit_block(node),
            "local_variable_declaration" => self.visit_local_variable_declaration(node),
            "method_invocation" => {
                self.audit_method_invocation(node);
                self.visit_children(node);
            }
            "object_creation_expression" => {
                self.audit_object_creation(node);
                self.visit_children(node);
            }
            _ => self.visit_children(node),
        }
    }

    fn visit_type_declaration(&mut self, node: Node<'_>) {
        let Some(simple_name) = declaration_name(node, self.source) else {
            self.visit_children(node);
            return;
        };
        let owner = self.classes.last().map_or_else(
            || qualify_java_name(&self.package_name, &simple_name),
            |parent| format!("{}${simple_name}", parent.owner),
        );
        let fields = self.collect_fields(node);
        let superclass =
            declaration_superclass(node, self.source).and_then(|name| self.resolve_type_name(name));
        let declared_members = self.collect_declared_members(node);
        self.known_class_fields
            .insert(owner.clone(), fields.clone());
        self.classes.push(ClassContext {
            owner,
            superclass,
            fields,
            declared_members,
        });
        self.visit_children(node);
        let _ = self.classes.pop();
    }

    fn visit_method_declaration(&mut self, node: Node<'_>) {
        let Some(owner) = self.classes.last().map(|class| class.owner.clone()) else {
            self.visit_children(node);
            return;
        };
        let member = declaration_name(node, self.source).unwrap_or_else(|| "<init>".to_string());
        let parameters = self.method_parameters(node);
        let descriptor = self.method_descriptor(node, &parameters);
        let returns_value = node.kind() != "constructor_declaration"
            && node
                .child_by_field_name("type")
                .and_then(|type_node| node_text(type_node, self.source))
                .is_some_and(|return_type| return_type != "void");
        self.callers.push(Caller {
            owner,
            member,
            descriptor,
            declaration: method_declaration_signature(node, self.source)
                .unwrap_or_else(|| "void <unknown>()".to_owned()),
            returns_value,
        });
        self.scopes.push(parameters);
        self.visit_children(node);
        let _ = self.scopes.pop();
        let _ = self.callers.pop();
    }

    fn visit_block(&mut self, node: Node<'_>) {
        self.scopes.push(BTreeMap::new());
        self.visit_children(node);
        let _ = self.scopes.pop();
    }

    fn visit_local_variable_declaration(&mut self, node: Node<'_>) {
        let explicit_type = node
            .child_by_field_name("type")
            .and_then(|type_node| node_text(type_node, self.source))
            .and_then(|name| self.resolve_type_name(name));
        let mut cursor = node.walk();
        for declarator in node
            .named_children(&mut cursor)
            .filter(|child| child.kind() == "variable_declarator")
        {
            let value = declarator.child_by_field_name("value");
            if let Some(value) = value {
                self.visit(value);
            }
            let Some(name) = declarator
                .child_by_field_name("name")
                .and_then(|name| node_text(name, self.source))
            else {
                continue;
            };
            let variable_type = explicit_type
                .clone()
                .or_else(|| value.and_then(|expression| self.resolve_expression_type(expression)));
            if let (Some(scope), Some(variable_type)) = (self.scopes.last_mut(), variable_type) {
                scope.insert(name.to_string(), variable_type);
            }
        }
    }

    fn audit_method_invocation(&mut self, node: Node<'_>) {
        let Some(member_node) = node.child_by_field_name("name") else {
            return;
        };
        let Some(member) = node_text(member_node, self.source) else {
            return;
        };
        if !self.rules.suspicious_member(member) {
            return;
        }
        let descriptor = self.invocation_descriptor(node);
        let object = node.child_by_field_name("object");
        let receiver = match object {
            Some(object) if matches!(object.kind(), "this" | "super") => {
                match self.resolve_this_call_receiver(member, descriptor.as_deref()) {
                    ImplicitReceiverResolution::Resolved(owner) => Some(owner),
                    ImplicitReceiverResolution::KnownNonPolicy => return,
                    ImplicitReceiverResolution::Unresolved => None,
                }
            }
            Some(object) => self.resolve_expression_type(object).or_else(|| {
                node_text(object, self.source).and_then(|name| self.resolve_policy_owner(name))
            }),
            None => match self.resolve_this_call_receiver(member, descriptor.as_deref()) {
                ImplicitReceiverResolution::Resolved(owner) => Some(owner),
                ImplicitReceiverResolution::KnownNonPolicy => return,
                ImplicitReceiverResolution::Unresolved => None,
            },
        };
        let receiver_expression = object
            .and_then(|object| node_text(object, self.source))
            .unwrap_or("<implicit-receiver>");
        let call_expression = node_text(node, self.source).unwrap_or(member);
        self.record_call(
            member_node,
            member,
            descriptor.as_deref(),
            receiver.as_deref(),
            receiver_expression,
            call_expression,
        );
    }

    fn audit_object_creation(&mut self, node: Node<'_>) {
        if !self.rules.suspicious_member("<init>") {
            return;
        }
        let Some(type_node) = node.child_by_field_name("type") else {
            return;
        };
        let descriptor = self.invocation_descriptor(node);
        let receiver =
            node_text(type_node, self.source).and_then(|name| self.resolve_type_name(name));
        let receiver_expression = node_text(type_node, self.source).unwrap_or("<unknown-type>");
        let call_expression = node_text(node, self.source).unwrap_or(receiver_expression);
        self.record_call(
            type_node,
            "<init>",
            descriptor.as_deref(),
            receiver.as_deref(),
            receiver_expression,
            call_expression,
        );
    }

    fn record_call(
        &mut self,
        location: Node<'_>,
        member: &str,
        descriptor: Option<&str>,
        receiver: Option<&str>,
        receiver_expression: &str,
        call_expression: &str,
    ) {
        let caller = self.callers.last().cloned().unwrap_or_else(|| Caller {
            owner: self
                .classes
                .last()
                .map_or_else(|| "<top-level>".to_string(), |class| class.owner.clone()),
            member: "<initializer>".to_string(),
            descriptor: None,
            declaration: "void <initializer>()".to_owned(),
            returns_value: false,
        });
        if self.rules.is_permitted_caller(&caller) {
            return;
        }
        let line = location.start_position().row + 1;
        let column = location.start_position().column + 1;
        let call_site = JavaCallSite {
            class_name: simple_class_name(&caller.owner),
            method_declaration: caller.declaration.clone(),
            method_returns_value: caller.returns_value,
            receiver_expression: receiver_expression.to_owned(),
            member: member.to_owned(),
            call_expression: call_expression.to_owned(),
        };
        if let Some(owner) = receiver {
            let Some(rule) = self.rules.denied_rule(owner, member, descriptor) else {
                return;
            };
            self.report.push_problem(SourceProblem::audit_rule(
                self.branch,
                self.repo_path,
                self.line_count,
                line,
                column,
                AuditRuleDiagnostic::Violation {
                    rule: format_rule(rule),
                    forbidden_call: format_matched_call(owner, member, descriptor, rule),
                    call_site,
                },
            ));
        } else {
            let rule = self
                .rules
                .deny_calls
                .iter()
                .find(|rule| wildcard_matches(member, &rule.member))
                .expect("suspicious member must originate from a deny rule");
            self.report.push_problem(SourceProblem::audit_rule(
                self.branch,
                self.repo_path,
                self.line_count,
                line,
                column,
                AuditRuleDiagnostic::UnresolvedCall {
                    rule: format_rule(rule),
                    call_site,
                },
            ));
        }
    }

    fn collect_fields(&self, node: Node<'_>) -> BTreeMap<String, String> {
        let mut fields = BTreeMap::new();
        let Some(body) = node.child_by_field_name("body") else {
            return fields;
        };
        let mut cursor = body.walk();
        for declaration in body
            .named_children(&mut cursor)
            .filter(|child| child.kind() == "field_declaration")
        {
            let Some(type_name) = declaration
                .child_by_field_name("type")
                .and_then(|type_node| node_text(type_node, self.source))
                .and_then(|name| self.resolve_type_name(name))
            else {
                continue;
            };
            let mut declaration_cursor = declaration.walk();
            for declarator in declaration
                .named_children(&mut declaration_cursor)
                .filter(|child| child.kind() == "variable_declarator")
            {
                if let Some(name) = declarator
                    .child_by_field_name("name")
                    .and_then(|name| node_text(name, self.source))
                {
                    fields.insert(name.to_string(), type_name.clone());
                }
            }
        }
        fields
    }

    fn collect_declared_members(&self, node: Node<'_>) -> Vec<DeclaredMember> {
        let Some(body) = node.child_by_field_name("body") else {
            return Vec::new();
        };
        let mut cursor = body.walk();
        body.named_children(&mut cursor)
            .filter(|member| {
                matches!(
                    member.kind(),
                    "method_declaration" | "constructor_declaration"
                )
            })
            .map(|member| {
                let parameters = self.method_parameters(member);
                DeclaredMember {
                    name: declaration_name(member, self.source)
                        .unwrap_or_else(|| "<init>".to_string()),
                    descriptor: self.method_descriptor(member, &parameters),
                }
            })
            .collect()
    }

    fn resolve_this_call_receiver(
        &self,
        member: &str,
        descriptor: Option<&str>,
    ) -> ImplicitReceiverResolution {
        let Some(class) = self.classes.last() else {
            return ImplicitReceiverResolution::Unresolved;
        };
        let declared_members = class
            .declared_members
            .iter()
            .filter(|candidate| candidate.name == member)
            .collect::<Vec<_>>();
        if declared_members.is_empty() {
            return class.superclass.clone().map_or(
                ImplicitReceiverResolution::Unresolved,
                ImplicitReceiverResolution::Resolved,
            );
        }
        let Some(descriptor) = descriptor else {
            return ImplicitReceiverResolution::Unresolved;
        };
        if declared_members.iter().any(|candidate| {
            candidate
                .descriptor
                .as_deref()
                .is_some_and(|candidate_descriptor| {
                    descriptor_matches(candidate_descriptor, Some(descriptor))
                })
        }) {
            return self
                .rules
                .denied_rule(&class.owner, member, Some(descriptor))
                .map_or(ImplicitReceiverResolution::KnownNonPolicy, |rule| {
                    ImplicitReceiverResolution::Resolved(rule.owner.clone())
                });
        }
        if declared_members
            .iter()
            .any(|candidate| candidate.descriptor.is_none())
        {
            return ImplicitReceiverResolution::Unresolved;
        }
        class.superclass.clone().map_or(
            ImplicitReceiverResolution::Unresolved,
            ImplicitReceiverResolution::Resolved,
        )
    }

    fn method_parameters(&self, node: Node<'_>) -> BTreeMap<String, String> {
        let mut parameters = BTreeMap::new();
        let Some(parameter_list) = node.child_by_field_name("parameters") else {
            return parameters;
        };
        let mut cursor = parameter_list.walk();
        for parameter in parameter_list.named_children(&mut cursor) {
            if !matches!(parameter.kind(), "formal_parameter" | "spread_parameter") {
                continue;
            }
            let Some(name) = parameter
                .child_by_field_name("name")
                .and_then(|name| node_text(name, self.source))
            else {
                continue;
            };
            let Some(type_name) = parameter
                .child_by_field_name("type")
                .and_then(|type_node| node_text(type_node, self.source))
                .and_then(|type_name| self.resolve_type_name(type_name))
            else {
                continue;
            };
            parameters.insert(name.to_string(), type_name);
        }
        parameters
    }

    fn method_descriptor(
        &self,
        node: Node<'_>,
        parameters: &BTreeMap<String, String>,
    ) -> Option<String> {
        let return_type = if node.kind() == "constructor_declaration" {
            Some("V".to_string())
        } else {
            node.child_by_field_name("type")
                .and_then(|type_node| node_text(type_node, self.source))
                .and_then(java_type_descriptor)
        }?;
        let parameter_list = node.child_by_field_name("parameters")?;
        let mut descriptors = String::new();
        let mut cursor = parameter_list.walk();
        for parameter in parameter_list.named_children(&mut cursor) {
            let name = parameter
                .child_by_field_name("name")
                .and_then(|name| node_text(name, self.source))?;
            descriptors.push_str(&java_type_descriptor(parameters.get(name)?)?);
        }
        Some(format!("({descriptors}){return_type}"))
    }

    fn invocation_descriptor(&self, node: Node<'_>) -> Option<String> {
        let arguments = node.child_by_field_name("arguments")?;
        let mut descriptor = String::from("(");
        let mut cursor = arguments.walk();
        for argument in arguments.named_children(&mut cursor) {
            descriptor.push_str(&java_type_descriptor(
                &self.resolve_expression_type(argument)?,
            )?);
        }
        descriptor.push(')');
        Some(descriptor)
    }

    fn resolve_expression_type(&self, node: Node<'_>) -> Option<String> {
        match node.kind() {
            "identifier" => {
                let name = node_text(node, self.source)?;
                self.scopes
                    .iter()
                    .rev()
                    .find_map(|scope| scope.get(name).cloned())
                    .or_else(|| {
                        self.classes
                            .last()
                            .and_then(|class| class.fields.get(name).cloned())
                    })
                    .or_else(|| self.resolve_type_name(name))
            }
            "object_creation_expression" => node
                .child_by_field_name("type")
                .and_then(|type_node| node_text(type_node, self.source))
                .and_then(|name| self.resolve_type_name(name)),
            "method_invocation" => self.resolve_method_invocation_return_type(node),
            "field_access" => {
                let field = node
                    .child_by_field_name("field")
                    .and_then(|field| node_text(field, self.source))?;
                let object = node.child_by_field_name("object")?;
                if object.kind() == "this" {
                    self.classes
                        .last()
                        .and_then(|class| class.fields.get(field).cloned())
                } else {
                    self.resolve_expression_type(object)
                        .and_then(|object_type| {
                            self.known_class_fields
                                .get(&object_type)
                                .and_then(|fields| fields.get(field).cloned())
                        })
                }
            }
            "scoped_identifier" => {
                node_text(node, self.source).and_then(|name| self.resolve_type_name(name))
            }
            "cast_expression" => node
                .child_by_field_name("type")
                .and_then(|type_node| node_text(type_node, self.source))
                .and_then(|name| self.resolve_type_name(name)),
            "parenthesized_expression" => first_named_child(node)
                .and_then(|expression| self.resolve_expression_type(expression)),
            "this" => self.classes.last().map(|class| class.owner.clone()),
            "decimal_integer_literal"
            | "hex_integer_literal"
            | "binary_integer_literal"
            | "octal_integer_literal" => Some("int".to_string()),
            "decimal_floating_point_literal" | "hex_floating_point_literal" => {
                Some("double".to_string())
            }
            "true" | "false" => Some("boolean".to_string()),
            "character_literal" => Some("char".to_string()),
            "string_literal" => Some("java.lang.String".to_string()),
            _ => None,
        }
    }

    fn resolve_method_invocation_return_type(&self, node: Node<'_>) -> Option<String> {
        let member = node
            .child_by_field_name("name")
            .and_then(|name| node_text(name, self.source))?;
        let owner = match node.child_by_field_name("object") {
            Some(object) => self.resolve_expression_type(object),
            None => self.classes.last().map(|class| class.owner.clone()),
        }?;
        list_get_return_type(&owner, member)
            .or_else(|| self.source_type_index.method_return_type(&owner, member))
    }

    fn resolve_type_name(&self, raw_name: &str) -> Option<String> {
        let simple_name = raw_name
            .trim()
            .trim_end_matches("...")
            .trim_end_matches("[]")
            .split('<')
            .next()?
            .trim();
        if simple_name.is_empty() || simple_name == "var" {
            return None;
        }
        if is_primitive_or_void(simple_name) {
            return Some(simple_name.to_string());
        }
        if simple_name.contains('.') {
            return Some(simple_name.to_string());
        }
        if simple_name == "String" {
            return Some("java.lang.String".to_string());
        }
        if let Some(imported) = self.imports.direct.get(simple_name) {
            return Some(imported.clone());
        }
        let matching_rules = self
            .rules
            .deny_calls
            .iter()
            .map(|rule| rule.owner.as_str())
            .chain(
                self.rules
                    .permitted_callers
                    .iter()
                    .map(|rule| rule.owner.as_str()),
            )
            .filter(|owner| owner.rsplit('.').next() == Some(simple_name))
            .collect::<BTreeSet<_>>();
        if matching_rules.len() == 1 {
            return matching_rules.into_iter().next().map(ToOwned::to_owned);
        }
        for package in &self.imports.wildcards {
            let candidate = format!("{package}.{simple_name}");
            if self
                .rules
                .deny_calls
                .iter()
                .any(|rule| rule.owner == candidate)
                || self
                    .rules
                    .permitted_callers
                    .iter()
                    .any(|rule| rule.owner == candidate)
            {
                return Some(candidate);
            }
        }
        (!self.package_name.is_empty()).then(|| format!("{}.{}", self.package_name, simple_name))
    }

    fn resolve_policy_owner(&self, name: &str) -> Option<String> {
        self.rules
            .deny_calls
            .iter()
            .map(|rule| rule.owner.as_str())
            .chain(
                self.rules
                    .permitted_callers
                    .iter()
                    .map(|rule| rule.owner.as_str()),
            )
            .any(|owner| owner == name)
            .then(|| name.to_string())
    }

    fn visit_children(&mut self, node: Node<'_>) {
        let mut cursor = node.walk();
        for child in node.named_children(&mut cursor) {
            self.visit(child);
        }
    }
}

#[derive(Clone, Debug, Default)]
struct JavaImports {
    direct: BTreeMap<String, String>,
    wildcards: BTreeSet<String>,
}

fn find_package_name(root: Node<'_>, source: &str) -> Option<String> {
    let mut cursor = root.walk();
    root.named_children(&mut cursor)
        .find(|child| child.kind() == "package_declaration")
        .and_then(|node| node_text(node, source))
        .map(|text| {
            text.trim_start_matches("package")
                .trim_end_matches(';')
                .trim()
                .to_string()
        })
}

fn find_imports(root: Node<'_>, source: &str) -> JavaImports {
    let mut imports = JavaImports::default();
    let mut cursor = root.walk();
    for node in root
        .named_children(&mut cursor)
        .filter(|node| node.kind() == "import_declaration")
    {
        let Some(text) = node_text(node, source) else {
            continue;
        };
        let normalized = text
            .trim()
            .trim_start_matches("import")
            .trim_start_matches("static")
            .trim()
            .trim_end_matches(';')
            .trim();
        if let Some(package) = normalized.strip_suffix(".*") {
            imports.wildcards.insert(package.to_string());
        } else if let Some(simple_name) = normalized.rsplit('.').next() {
            imports
                .direct
                .insert(simple_name.to_string(), normalized.to_string());
        }
    }
    imports
}

fn is_type_declaration(kind: &str) -> bool {
    matches!(
        kind,
        "class_declaration"
            | "interface_declaration"
            | "enum_declaration"
            | "record_declaration"
            | "annotation_type_declaration"
    )
}

fn declaration_name(node: Node<'_>, source: &str) -> Option<String> {
    node.child_by_field_name("name")
        .and_then(|name| node_text(name, source))
        .map(ToOwned::to_owned)
}

fn declaration_superclass<'a>(node: Node<'a>, source: &'a str) -> Option<&'a str> {
    node.child_by_field_name("superclass")
        .and_then(|superclass| {
            superclass
                .child_by_field_name("type")
                .or_else(|| first_named_child(superclass))
        })
        .and_then(|type_node| node_text(type_node, source))
}

fn node_text<'a>(node: Node<'_>, source: &'a str) -> Option<&'a str> {
    source.get(node.byte_range())
}

fn first_named_child(node: Node<'_>) -> Option<Node<'_>> {
    let mut cursor = node.walk();
    node.named_children(&mut cursor).next()
}

fn first_error_node(node: Node<'_>) -> Option<Node<'_>> {
    if node.kind() == "ERROR" || node.is_missing() {
        return Some(node);
    }
    let mut cursor = node.walk();
    node.named_children(&mut cursor).find_map(first_error_node)
}

fn qualify_java_name(package_name: &str, simple_name: &str) -> String {
    if package_name.is_empty() {
        simple_name.to_string()
    } else {
        format!("{package_name}.{simple_name}")
    }
}

fn is_primitive_or_void(name: &str) -> bool {
    matches!(
        name,
        "boolean" | "byte" | "char" | "short" | "int" | "long" | "float" | "double" | "void"
    )
}

fn java_type_descriptor(type_name: &str) -> Option<String> {
    let name = type_name.trim();
    let (component, dimensions) = split_array_type(name);
    let component_descriptor = match component {
        "boolean" => "Z".to_string(),
        "byte" => "B".to_string(),
        "char" => "C".to_string(),
        "short" => "S".to_string(),
        "int" => "I".to_string(),
        "long" => "J".to_string(),
        "float" => "F".to_string(),
        "double" => "D".to_string(),
        "void" => "V".to_string(),
        object if object.contains('.') => format!("L{};", object.replace('.', "/")),
        _ => return None,
    };
    Some(format!(
        "{}{}",
        "[".repeat(dimensions),
        component_descriptor
    ))
}

fn split_array_type(name: &str) -> (&str, usize) {
    let mut dimensions = 0;
    let mut component = name;
    while let Some(trimmed) = component.strip_suffix("[]") {
        dimensions += 1;
        component = trimmed;
    }
    (component, dimensions)
}

fn descriptor_matches(rule_descriptor: &str, actual_descriptor: Option<&str>) -> bool {
    if rule_descriptor == "*" {
        return true;
    }
    let Some(actual) = actual_descriptor else {
        return false;
    };
    if rule_descriptor == actual {
        return true;
    }
    let Some(rule_parameters) = rule_descriptor.split_once(')') else {
        return false;
    };
    actual
        .split_once(')')
        .is_some_and(|actual_parameters| rule_parameters.0 == actual_parameters.0)
}

fn wildcard_matches(candidate: &str, pattern: &str) -> bool {
    if pattern == "*" {
        return true;
    }
    candidate == pattern
}

fn format_rule(rule: &CallRule) -> String {
    format!(
        "DENY CALL {} {} {}",
        rule.owner, rule.member, rule.descriptor
    )
}

fn format_matched_call(
    owner: &str,
    member: &str,
    descriptor: Option<&str>,
    rule: &CallRule,
) -> String {
    format!(
        "{owner} {member} {}",
        descriptor.unwrap_or(&rule.descriptor)
    )
}

fn method_declaration_signature(node: Node<'_>, source: &str) -> Option<String> {
    let declaration = node_text(node, source)?;
    let declaration = declaration
        .split_once('{')
        .map_or(declaration, |(signature, _)| signature)
        .trim_end_matches(';');
    let declaration = normalize_java_declaration_spacing(declaration);
    (!declaration.is_empty()).then_some(declaration)
}

fn normalize_java_declaration_spacing(declaration: &str) -> String {
    let mut normalized = declaration.split_whitespace().collect::<Vec<_>>().join(" ");
    for (from, to) in [
        ("( ", "("),
        (" )", ")"),
        (" ,", ","),
        ("[ ", "["),
        (" ]", "]"),
        (" .", "."),
        (". ", "."),
    ] {
        normalized = normalized.replace(from, to);
    }
    normalized
}

fn simple_class_name(owner: &str) -> String {
    owner.rsplit('.').next().unwrap_or(owner).to_owned()
}

#[cfg(test)]
mod tests {
    use super::AuditRules;
    use super::Caller;
    use super::JavaSourceTypeIndex;
    use super::audit_java_font_render_surface;
    use super::audit_java_font_render_surface_with_index;
    use crate::source_audit::AuditWarning;
    use crate::source_audit::AuditWarningDetail;
    use crate::source_audit::BranchSourceAuditReport;
    use crate::source_audit::SourceLineCount;

    const RULES: &str = r"
        DENY CALL net.minecraft.client.gui.Font draw *
        DENY CALL net.minecraft.client.gui.GuiGraphics drawString *
        DENY CALL net.minecraft.client.gui.screens.Screen drawString *
        DENY CALL net.minecraft.client.gui.screens.Screen drawCenteredString *
        DENY CALL example.StringView <init> (II)V
        PERMIT CALLER ca.teamdman.sfm.client.screen.SFMFontUtils * *
    ";

    fn audit(source: &str) -> Vec<AuditWarning> {
        let rules = AuditRules::parse(RULES).expect("rules should parse");
        let mut report = BranchSourceAuditReport::new("1.19.2");
        audit_java_font_render_surface(
            &mut report,
            "1.19.2",
            "platform/minecraft/src/main/java/ca/teamdman/sfm/Example.java",
            SourceLineCount::from_text(source),
            source,
            &rules,
        )
        .expect("source should parse");
        report
            .problems
            .iter()
            .map(|problem| problem.audit_warning())
            .collect()
    }

    fn is_violation(warning: &AuditWarning) -> bool {
        matches!(
            &warning.detail,
            AuditWarningDetail::AuditRuleViolation { .. }
        )
    }

    fn violation_callee(warning: &AuditWarning) -> Option<&str> {
        match &warning.detail {
            AuditWarningDetail::AuditRuleViolation { callee, .. } => Some(callee),
            _ => None,
        }
    }

    #[test]
    fn parses_declarative_call_and_caller_rules() {
        let rules = AuditRules::parse(RULES).expect("rules should parse");
        assert!(rules.suspicious_member("draw"));
        assert!(rules.suspicious_member("drawString"));
        assert!(!rules.suspicious_member("unrelated"));
        assert!(AuditRules::parse("DENY public Foo x y").is_err());
    }

    #[test]
    fn descriptors_and_permits_match_exact_rules_before_wildcards() {
        let rules = AuditRules::parse(
            r"
            DENY CALL example.Font draw (I)V
            DENY CALL example.Font draw *
            PERMIT CALLER example.Trusted render (I)V
            ",
        )
        .expect("rules should parse");
        assert_eq!(
            rules
                .denied_rule("example.Font", "draw", Some("(I)"))
                .expect("exact descriptor should match")
                .descriptor,
            "(I)V"
        );
        assert_eq!(
            rules
                .denied_rule("example.Font", "draw", Some("(J)"))
                .expect("wildcard descriptor should match")
                .descriptor,
            "*"
        );
        assert!(rules.is_permitted_caller(&Caller {
            owner: "example.Trusted".to_string(),
            member: "render".to_string(),
            descriptor: Some("(I)V".to_string()),
            declaration: "void render(int value)".to_owned(),
            returns_value: false,
        }));
        assert!(!rules.is_permitted_caller(&Caller {
            owner: "example.Trusted".to_string(),
            member: "render".to_string(),
            descriptor: Some("(J)V".to_string()),
            declaration: "void render(long value)".to_owned(),
            returns_value: false,
        }));
    }

    #[test]
    fn flags_direct_imported_and_var_aliased_font_calls() {
        let warnings = audit(
            r#"
            package example;
            import net.minecraft.client.gui.Font;
            final class Example {
                Font font;
                void render(Font parameter) {
                    Font local = parameter;
                    var alias = this.font;
                    local.draw(null, "a", 0, 0, 0);
                    alias.draw(null, "b", 0, 0, 0);
                }
            }
            "#,
        );
        assert_eq!(warnings.len(), 2);
        assert!(warnings.iter().all(is_violation), "warnings: {warnings:?}");
        assert!(warnings.iter().all(|warning| {
            violation_callee(warning)
                .is_some_and(|callee| callee.contains("net.minecraft.client.gui.Font draw"))
        }));
    }

    #[test]
    fn resolves_var_aliases_from_parameters_qualified_fields_and_this_fields() {
        let warnings = audit(
            r#"
            package example;
            import net.minecraft.client.gui.Font;
            import net.minecraft.client.gui.GuiGraphics;
            final class Holder {
                Font font;
            }
            final class Example {
                Font font;
                void render(GuiGraphics graphics, Holder holder) {
                    var fromGraphics = graphics;
                    var fromHolder = holder.font;
                    var fromThis = this.font;
                    fromGraphics.drawString(null, "graphics", 0, 0, 0);
                    fromHolder.draw(null, "holder", 0, 0, 0);
                    fromThis.draw(null, "this", 0, 0, 0);
                }
            }
            "#,
        );
        assert_eq!(warnings.len(), 3);
        assert!(warnings.iter().all(is_violation), "warnings: {warnings:?}");
    }

    #[test]
    fn local_shadowing_prevents_a_field_type_from_leaking_into_the_call() {
        let warnings = audit(
            r#"
            package example;
            import net.minecraft.client.gui.Font;
            final class Example {
                Font target;
                void render(Other target) {
                    target.draw(null, "not a font", 0, 0, 0);
                }
            }
            "#,
        );
        assert!(warnings.is_empty());
    }

    #[test]
    fn flags_gui_graphics_but_ignores_an_unrelated_method_name() {
        let warnings = audit(
            r#"
            package example;
            import net.minecraft.client.gui.GuiGraphics;
            final class Example {
                void render(GuiGraphics graphics, Other other) {
                    graphics.drawString(null, "a", 0, 0, 0);
                    other.drawString();
                }
            }
            "#,
        );
        assert_eq!(warnings.len(), 1);
        assert!(
            violation_callee(&warnings[0])
                .is_some_and(|callee| callee.contains("GuiGraphics drawString"))
        );
    }

    #[test]
    fn resolves_implicit_screen_text_calls_as_definite_violations() {
        let warnings = audit(
            r#"
            package example;
            import net.minecraft.client.gui.screens.Screen;
            final class Example extends Screen {
                void render() {
                    drawString(null, null, "caption", 0, 0, 0);
                    this.drawString(null, null, "caption", 0, 0, 0);
                    super.drawString(null, null, "caption", 0, 0, 0);
                    drawCenteredString(null, null, "caption", 0, 0, 0);
                }
            }
            "#,
        );
        assert_eq!(warnings.len(), 4, "warnings: {warnings:?}");
        assert!(warnings.iter().all(|warning| {
            is_violation(warning)
                && violation_callee(warning).is_some_and(|callee| {
                    callee.contains("net.minecraft.client.gui.screens.Screen")
                })
        }));
    }

    #[test]
    fn does_not_mistake_a_declared_text_helper_for_the_screen_inherited_helper() {
        let warnings = audit(
            r#"
            package example;
            import net.minecraft.client.gui.screens.Screen;
            final class Example extends Screen {
                void drawString(String text) {}
                void render() { drawString("local helper"); }
            }
            "#,
        );
        assert!(warnings.is_empty(), "warnings: {warnings:?}");
    }

    #[test]
    fn allows_only_the_permitted_caller_context() {
        let warnings = audit(
            r#"
            package ca.teamdman.sfm.client.screen;
            import net.minecraft.client.gui.Font;
            final class SFMFontUtils {
                Object minecraft;
                static void draw(Font font) {
                    font.draw(null, "a", 0, 0, 0);
                }
                void drawThroughUnresolvedFieldAccess() {
                    minecraft.font.draw(null, "b", 0, 0, 0);
                }
            }
            "#,
        );
        assert!(warnings.is_empty());
    }

    #[test]
    fn caller_signatures_are_normalized_for_java_diagnostic_frames() {
        let warnings = audit(
            r#"
            package example;
            import net.minecraft.client.gui.Font;
            final class Example {
                Font font;
                public String copyableText( int spaceWidth, int lineHeight ) {
                    return font.draw(null, "a", 0, 0, 0);
                }
            }
            "#,
        );

        assert!(matches!(
            &warnings[0].detail,
            AuditWarningDetail::AuditRuleViolation { call_site, .. }
                if call_site.method_declaration
                    == "public String copyableText(int spaceWidth, int lineHeight)"
        ));
    }

    #[test]
    fn resolves_cross_source_method_return_types_before_matching_a_rule_member_name() {
        let helper_source = r#"
            package example;
            final class SyntaxHelper {
                static CanvasDocumentProjection projectCanvasDocument() {
                    return null;
                }
                record CanvasDocumentProjection(String text) {}
            }
            "#;
        let consumer_source = r#"
            package example;
            final class Consumer {
                String copyableText() {
                    return SyntaxHelper.projectCanvasDocument().text();
                }
            }
            "#;
        let rules =
            AuditRules::parse("DENY CALL net.minecraft.client.gui.GuiGraphicsExtractor text *")
                .expect("rules should parse");
        let type_index = JavaSourceTypeIndex::build([helper_source, consumer_source])
            .expect("source index should build");
        let mut report = BranchSourceAuditReport::new("1.19.2");

        audit_java_font_render_surface_with_index(
            &mut report,
            "1.19.2",
            "platform/minecraft/src/main/java/example/Consumer.java",
            SourceLineCount::from_text(consumer_source),
            consumer_source,
            &rules,
            &type_index,
        )
        .expect("source should parse");

        assert!(
            report.problems.is_empty(),
            "problems: {:?}",
            report.problems
        );
    }

    #[test]
    fn resolves_list_get_to_the_source_declared_element_type() {
        let canvas_source = r#"
            package example;
            import java.util.List;
            final class Canvas {
                List<Glyph> glyphs() {
                    return null;
                }
                record Glyph(String text) {}
            }
            "#;
        let consumer_source = r#"
            package example;
            final class Consumer {
                String copyableText(Canvas canvas) {
                    return canvas.glyphs().get(0).text();
                }
            }
            "#;
        let rules =
            AuditRules::parse("DENY CALL net.minecraft.client.gui.GuiGraphicsExtractor text *")
                .expect("rules should parse");
        let type_index = JavaSourceTypeIndex::build([canvas_source, consumer_source])
            .expect("source index should build");
        let mut report = BranchSourceAuditReport::new("1.19.2");

        audit_java_font_render_surface_with_index(
            &mut report,
            "1.19.2",
            "platform/minecraft/src/main/java/example/Consumer.java",
            SourceLineCount::from_text(consumer_source),
            consumer_source,
            &rules,
            &type_index,
        )
        .expect("source should parse");

        assert!(
            report.problems.is_empty(),
            "problems: {:?}",
            report.problems
        );
    }

    #[test]
    fn resolves_an_indexed_method_return_to_a_denied_imported_type() {
        let helper_source = r#"
            package example;
            import net.minecraft.client.gui.GuiGraphicsExtractor;
            final class Helper {
                static GuiGraphicsExtractor extractor() {
                    return null;
                }
            }
            "#;
        let consumer_source = r#"
            package example;
            final class Consumer {
                void render() {
                    Helper.extractor().text();
                }
            }
            "#;
        let rules =
            AuditRules::parse("DENY CALL net.minecraft.client.gui.GuiGraphicsExtractor text *")
                .expect("rules should parse");
        let type_index = JavaSourceTypeIndex::build([helper_source, consumer_source])
            .expect("source index should build");
        let mut report = BranchSourceAuditReport::new("1.19.2");

        audit_java_font_render_surface_with_index(
            &mut report,
            "1.19.2",
            "platform/minecraft/src/main/java/example/Consumer.java",
            SourceLineCount::from_text(consumer_source),
            consumer_source,
            &rules,
            &type_index,
        )
        .expect("source should parse");

        let warnings = report
            .problems
            .iter()
            .map(|problem| problem.audit_warning())
            .collect::<Vec<_>>();
        assert_eq!(warnings.len(), 1);
        assert!(is_violation(&warnings[0]));
    }

    #[test]
    fn warns_when_a_suspicious_receiver_cannot_be_resolved() {
        let warnings = audit(
            r#"
            package example;
            final class Example {
                void render() {
                    minecraft.font.draw(null, "a", 0, 0, 0);
                }
            }
            "#,
        );
        assert_eq!(warnings.len(), 1);
        assert!(matches!(
            &warnings[0].detail,
            AuditWarningDetail::UnresolvedAuditRuleCall {
                call_site,
                ..
            } if call_site.member == "draw" && call_site.receiver_expression == "minecraft.font"
        ));
    }

    #[test]
    fn resolves_static_and_cast_receivers_without_confusing_other_types() {
        let warnings = audit(
            r#"
            package example;
            import net.minecraft.client.gui.Font;
            final class Example {
                void render(Object value) {
                    Font.draw(null, "static", 0, 0, 0);
                    net.minecraft.client.gui.Font.draw(null, "fully qualified", 0, 0, 0);
                    ((Font) value).draw(null, "cast", 0, 0, 0);
                }
            }
            "#,
        );
        assert_eq!(warnings.len(), 3);
        assert!(warnings.iter().all(is_violation), "warnings: {warnings:?}");
    }

    #[test]
    fn exact_constructor_descriptor_matches_literal_arguments() {
        let warnings = audit(
            r"
            package example;
            final class Example {
                void render() {
                    new StringView(1, 2);
                }
            }
            ",
        );
        assert_eq!(warnings.len(), 1);
        assert!(
            violation_callee(&warnings[0])
                .is_some_and(|callee| callee.contains("example.StringView <init> (II)"))
        );
    }

    #[test]
    fn reports_an_arborium_parse_gap_without_skipping_matching_calls() {
        let warnings = audit(
            r#"
            package example;
            import net.minecraft.client.gui.Font;
            final class Example {
                void render(Font font) { font.draw(null, "a", 0, 0, 0); }
            "#,
        );
        assert_eq!(warnings.len(), 2);
        assert!(warnings.iter().any(|warning| matches!(
            &warning.detail,
            AuditWarningDetail::AuditRuleParseFailure { .. }
        )));
        assert!(warnings.iter().any(is_violation));
    }
}
