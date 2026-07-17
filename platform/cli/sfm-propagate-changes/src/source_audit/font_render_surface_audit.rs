use super::AuditRuleDiagnostic;
use super::BranchSourceAuditReport;
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

/// Audit Java source without binding the audit engine to a particular Minecraft API member.
///
/// The resolver is intentionally lexical: it understands package/import names plus fields,
/// parameters, local declarations, and `var` aliases in the current Java source file. It only
/// attempts that work after a call's member name matches a declarative deny rule.
pub(crate) fn audit_java_font_render_surface(
    report: &mut BranchSourceAuditReport,
    branch: &str,
    repo_path: &str,
    line_count: SourceLineCount,
    source: &str,
    rules: &AuditRules,
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
        self.callers.push(Caller {
            owner,
            member,
            descriptor,
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
        self.record_call(
            member_node,
            member,
            descriptor.as_deref(),
            receiver.as_deref(),
            receiver_expression,
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
        self.record_call(
            type_node,
            "<init>",
            descriptor.as_deref(),
            receiver.as_deref(),
            receiver_expression,
        );
    }

    fn record_call(
        &mut self,
        location: Node<'_>,
        member: &str,
        descriptor: Option<&str>,
        receiver: Option<&str>,
        receiver_expression: &str,
    ) {
        let caller = self.callers.last().cloned().unwrap_or_else(|| Caller {
            owner: self
                .classes
                .last()
                .map_or_else(|| "<top-level>".to_string(), |class| class.owner.clone()),
            member: "<initializer>".to_string(),
            descriptor: None,
        });
        if self.rules.is_permitted_caller(&caller) {
            return;
        }
        let line = location.start_position().row + 1;
        let column = location.start_position().column + 1;
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
                    caller_context: format_caller(&caller),
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
                    member: member.to_string(),
                    receiver_expression: receiver_expression.to_string(),
                    caller_context: format_caller(&caller),
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
                node_text(node, self.source).and_then(|name| self.resolve_policy_owner(name))
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

fn format_caller(caller: &Caller) -> String {
    format!(
        "{} {} {}",
        caller.owner,
        caller.member,
        caller
            .descriptor
            .as_deref()
            .unwrap_or("<unresolved-descriptor>")
    )
}

#[cfg(test)]
mod tests {
    use super::AuditRules;
    use super::Caller;
    use super::audit_java_font_render_surface;
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
        }));
        assert!(!rules.is_permitted_caller(&Caller {
            owner: "example.Trusted".to_string(),
            member: "render".to_string(),
            descriptor: Some("(J)V".to_string()),
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
                member,
                receiver,
                ..
            } if member == "draw" && receiver == "minecraft.font"
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
