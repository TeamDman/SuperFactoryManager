use crate::jar_build::SourceCatalogAction;
use crate::jar_build::SourceCatalogCategory;
use arborium_java::language as java_language;
use tree_sitter_patched_arborium::Node;
use tree_sitter_patched_arborium::Parser;

const SFM_GAME_TEST_ANNOTATION: &str = "ca.teamdman.sfm.gametest.SFMGameTest";
const SFM_GAME_TEST_GENERATOR_ANNOTATION: &str = "ca.teamdman.sfm.gametest.SFMGameTestGenerator";
const SFM_GAME_PUPPET_ANNOTATION: &str = "ca.teamdman.sfm.gametest.puppet.SFMGamePuppet";
const JUNIT_ANNOTATIONS: &[&str] = &[
    "org.junit.jupiter.api.Test",
    "org.junit.jupiter.params.ParameterizedTest",
    "org.junit.jupiter.api.RepeatedTest",
    "org.junit.jupiter.api.TestFactory",
    "org.junit.jupiter.api.TestTemplate",
];

#[derive(Clone, Debug, Eq, Ord, PartialEq, PartialOrd)]
struct StaticJavaCatalogEntry {
    category: SourceCatalogCategory,
    id: String,
    class_name: String,
    method_name: Option<String>,
    source_path: PathBuf,
    source_line: usize,
    dynamic: bool,
}

fn print_static_java_catalog_for_target(
    target: &WorktreeTarget,
    query: &SourceCatalogQuery,
) -> eyre::Result<()> {
    let (minecraft_dir, minecraft_version) = static_catalog_target_parts(target)?;
    let entries = static_java_catalog(&minecraft_dir, minecraft_version, query.category)?;
    match &query.action {
        SourceCatalogAction::List => {
            for entry in entries {
                stdout_line(format_static_catalog_entry(&entry))?;
            }
        }
        SourceCatalogAction::Show { id } => {
            let matches = entries
                .into_iter()
                .filter(|entry| entry.id == *id)
                .collect::<Vec<_>>();
            if matches.is_empty() {
                eyre::bail!("No {} entry named {id:?}.", catalog_category_label(query.category));
            }
            for entry in matches {
                stdout_line(format_static_catalog_entry(&entry))?;
            }
        }
    }
    Ok(())
}

fn validate_static_puppet_selection_for_target(
    target: &WorktreeTarget,
    raw_selection: &str,
) -> eyre::Result<()> {
    let selectors = raw_selection
        .split(',')
        .map(str::trim)
        .filter(|selector| !selector.is_empty())
        .map(normalize_sfm_selector)
        .collect::<Vec<_>>();
    if selectors.is_empty() {
        eyre::bail!("SFM game puppet selection must contain at least one nonblank selector.");
    }
    let (minecraft_dir, minecraft_version) = static_catalog_target_parts(target)?;
    let entries = static_java_catalog(&minecraft_dir, minecraft_version, SourceCatalogCategory::Puppet)?;
    let matches = entries.iter().filter(|entry| {
        selectors
            .iter()
            .any(|selector| wildcard_matches(&entry.id, selector))
    });
    if matches.count() == 0 {
        eyre::bail!(
            "SFM game puppet selection {raw_selection:?} matched zero static puppets before build. Run `sfm-propagate-changes puppet list --branch {}` to inspect available ids.",
            target.branch
        );
    }
    Ok(())
}

fn static_java_catalog(
    minecraft_dir: &Path,
    minecraft_version: &str,
    category: SourceCatalogCategory,
) -> eyre::Result<Vec<StaticJavaCatalogEntry>> {
    let source_set = match category {
        SourceCatalogCategory::Test => "test",
        SourceCatalogCategory::GameTest | SourceCatalogCategory::Puppet => "gametest",
    };
    let mut parser = Parser::new();
    let language = java_language().into();
    parser
        .set_language(&language)
        .map_err(|error| eyre::eyre!("Failed to load Arborium Java grammar: {error}"))?;
    let mut entries = BTreeSet::new();
    for source_path in collect_catalog_java_sources(minecraft_dir, minecraft_version, source_set)? {
        let source = fs::read_to_string(&source_path)
            .wrap_err_with(|| format!("Failed to read Java source {}", source_path.display()))?;
        let tree = parser
            .parse(&source, None)
            .ok_or_else(|| eyre::eyre!("Arborium did not produce a parse tree for {}", source_path.display()))?;
        if tree.root_node().has_error() {
            eyre::bail!("Arborium could not parse Java source {}", source_path.display());
        }
        let package_name = find_package_name(tree.root_node(), &source).unwrap_or_default();
        let imports = find_imports(tree.root_node(), &source);
        collect_catalog_entries(
            tree.root_node(),
            &source,
            &package_name,
            &imports,
            &source_path,
            category,
            None,
            &mut entries,
        );
    }
    validate_unique_catalog_ids(&entries, category)?;
    Ok(entries.into_iter().collect())
}

fn validate_unique_catalog_ids(
    entries: &BTreeSet<StaticJavaCatalogEntry>,
    category: SourceCatalogCategory,
) -> eyre::Result<()> {
    let mut entries_by_id = BTreeMap::new();
    for entry in entries {
        if let Some(existing) = entries_by_id.insert(&entry.id, entry) {
            eyre::bail!(
                "Duplicate static {} id {:?}: {}:{} and {}:{}.",
                catalog_category_label(category),
                entry.id,
                existing.source_path.display(),
                existing.source_line,
                entry.source_path.display(),
                entry.source_line,
            );
        }
    }
    Ok(())
}

fn static_catalog_target_parts(target: &WorktreeTarget) -> eyre::Result<(PathBuf, &str)> {
    let minecraft_version = target
        .mc_version
        .as_ref()
        .ok_or_else(|| eyre::eyre!("Branch {} does not declare a Minecraft version.", target.branch))?;
    Ok((
        target.worktree_path.as_path().join("platform").join("minecraft"),
        minecraft_version.as_str(),
    ))
}

fn collect_catalog_java_sources(
    minecraft_dir: &Path,
    minecraft_version: &str,
    source_set: &str,
) -> eyre::Result<Vec<PathBuf>> {
    let source_root = minecraft_dir.join("src").join(source_set).join("java");
    let excludes = read_source_excludes_for_minecraft_dir(minecraft_dir, minecraft_version, source_set)?;
    let mut sources = Vec::new();
    collect_catalog_java_sources_under(&source_root, &source_root, &excludes, &mut sources)?;
    sources.sort();
    Ok(sources)
}

fn collect_catalog_java_sources_under(
    root: &Path,
    current: &Path,
    excludes: &[String],
    sources: &mut Vec<PathBuf>,
) -> eyre::Result<()> {
    if !current.exists() {
        return Ok(());
    }
    for entry in fs::read_dir(current).wrap_err_with(|| format!("Failed to read {}", current.display()))? {
        let entry = entry.wrap_err_with(|| format!("Failed to inspect {}", current.display()))?;
        let path = entry.path();
        if path.is_dir() {
            collect_catalog_java_sources_under(root, &path, excludes, sources)?;
        } else if path.extension().is_some_and(|extension| extension.eq_ignore_ascii_case("java")) {
            let relative = relative_zip_name(root, &path).unwrap_or_default();
            if !is_excluded_source(&relative, excludes) {
                sources.push(path);
            }
        }
    }
    Ok(())
}

#[expect(
    clippy::too_many_arguments,
    clippy::too_many_lines,
    reason = "Tree traversal keeps its immutable parse context explicit and recurses without shared mutable state except the result set."
)]
fn collect_catalog_entries(
    node: Node<'_>,
    source: &str,
    package_name: &str,
    imports: &JavaImports,
    source_path: &Path,
    category: SourceCatalogCategory,
    owner_class: Option<&str>,
    entries: &mut BTreeSet<StaticJavaCatalogEntry>,
) {
    if is_type_declaration(node.kind()) {
        let Some(simple_name) = declaration_name(node, source) else {
            return;
        };
        let class_name = owner_class.map_or_else(
            || qualify_java_name(package_name, &simple_name),
            |owner| format!("{owner}${simple_name}"),
        );
        let annotations = declaration_annotations(node, source);
        match category {
            SourceCatalogCategory::GameTest
                if has_annotation(&annotations, SFM_GAME_TEST_ANNOTATION, package_name, imports) =>
            {
                entries.insert(StaticJavaCatalogEntry {
                    category,
                    id: qualify_sfm_id(&snake_case(
                        simple_name.strip_suffix("GameTest").unwrap_or(&simple_name),
                    )),
                    class_name: class_name.clone(),
                    method_name: None,
                    source_path: source_path.to_path_buf(),
                    source_line: node.start_position().row + 1,
                    dynamic: false,
                });
            }
            SourceCatalogCategory::GameTest
                if has_annotation(
                    &annotations,
                    SFM_GAME_TEST_GENERATOR_ANNOTATION,
                    package_name,
                    imports,
                ) =>
            {
                entries.insert(StaticJavaCatalogEntry {
                    category,
                    id: format!("dynamic:{class_name}"),
                    class_name: class_name.clone(),
                    method_name: None,
                    source_path: source_path.to_path_buf(),
                    source_line: node.start_position().row + 1,
                    dynamic: true,
                });
            }
            SourceCatalogCategory::Puppet
                if has_annotation(&annotations, SFM_GAME_PUPPET_ANNOTATION, package_name, imports) =>
            {
                entries.insert(StaticJavaCatalogEntry {
                    category,
                    id: qualify_sfm_id(&snake_case(
                        simple_name.strip_suffix("GamePuppet").unwrap_or(&simple_name),
                    )),
                    class_name: class_name.clone(),
                    method_name: None,
                    source_path: source_path.to_path_buf(),
                    source_line: node.start_position().row + 1,
                    dynamic: false,
                });
            }
            _ => {}
        }
        let mut cursor = node.walk();
        for child in node.named_children(&mut cursor) {
            collect_catalog_entries(
                child,
                source,
                package_name,
                imports,
                source_path,
                category,
                Some(&class_name),
                entries,
            );
        }
        return;
    }

    if node.kind() == "method_declaration" {
        if category == SourceCatalogCategory::Test
            && let (Some(class_name), Some(method_name)) =
                (owner_class, declaration_name(node, source))
        {
            let annotations = declaration_annotations(node, source);
            let dynamic = has_annotation(&annotations, "org.junit.jupiter.api.TestFactory", package_name, imports);
            if JUNIT_ANNOTATIONS
                .iter()
                .any(|annotation| has_annotation(&annotations, annotation, package_name, imports))
            {
                entries.insert(StaticJavaCatalogEntry {
                    category,
                    id: format!("{class_name}#{method_name}"),
                    class_name: class_name.to_string(),
                    method_name: Some(method_name),
                    source_path: source_path.to_path_buf(),
                    source_line: node.start_position().row + 1,
                    dynamic,
                });
            }
        }
        return;
    }

    let mut cursor = node.walk();
    for child in node.named_children(&mut cursor) {
        collect_catalog_entries(
            child,
            source,
            package_name,
            imports,
            source_path,
            category,
            owner_class,
            entries,
        );
    }
}

#[derive(Default)]
struct JavaImports {
    direct: BTreeSet<String>,
    wildcards: BTreeSet<String>,
}

fn find_package_name(root: Node<'_>, source: &str) -> Option<String> {
    let mut cursor = root.walk();
    root.named_children(&mut cursor)
        .find(|child| child.kind() == "package_declaration")
        .and_then(|node| node_text(node, source))
        .map(|text| text.trim_start_matches("package").trim_end_matches(';').trim().to_string())
}

fn find_imports(root: Node<'_>, source: &str) -> JavaImports {
    let mut imports = JavaImports::default();
    let mut cursor = root.walk();
    for node in root.named_children(&mut cursor).filter(|node| node.kind() == "import_declaration") {
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
        } else {
            imports.direct.insert(normalized.to_string());
        }
    }
    imports
}

fn declaration_annotations(node: Node<'_>, source: &str) -> Vec<String> {
    let mut annotations = Vec::new();
    let mut cursor = node.walk();
    for child in node.named_children(&mut cursor) {
        if child.kind() != "modifiers" {
            continue;
        }
        let mut modifier_cursor = child.walk();
        for modifier in child.named_children(&mut modifier_cursor) {
            if modifier.kind().contains("annotation") && let Some(text) = node_text(modifier, source) {
                annotations.push(
                    text.trim()
                        .trim_start_matches('@')
                        .split('(')
                        .next()
                        .unwrap_or_default()
                        .trim()
                        .to_string(),
                );
            }
        }
    }
    annotations
}

fn has_annotation(
    annotations: &[String],
    expected: &str,
    package_name: &str,
    imports: &JavaImports,
) -> bool {
    let expected_simple = expected.rsplit('.').next().unwrap_or(expected);
    let expected_package = expected.strip_suffix(expected_simple).unwrap_or_default().trim_end_matches('.');
    annotations.iter().any(|annotation| {
        if annotation == expected {
            return true;
        }
        if annotation.contains('.') {
            return false;
        }
        annotation == expected_simple
            && (imports.direct.contains(expected)
                || imports.wildcards.contains(expected_package)
                || package_name == expected_package)
    })
}

fn is_type_declaration(kind: &str) -> bool {
    matches!(
        kind,
        "class_declaration" | "interface_declaration" | "enum_declaration" | "annotation_type_declaration"
    )
}

fn declaration_name(node: Node<'_>, source: &str) -> Option<String> {
    node.child_by_field_name("name")
        .and_then(|name| node_text(name, source))
        .map(ToOwned::to_owned)
}

fn node_text<'a>(node: Node<'_>, source: &'a str) -> Option<&'a str> {
    source.get(node.byte_range())
}

fn qualify_java_name(package_name: &str, simple_name: &str) -> String {
    if package_name.is_empty() {
        simple_name.to_string()
    } else {
        format!("{package_name}.{simple_name}")
    }
}

fn qualify_sfm_id(name: &str) -> String {
    format!("sfm:{name}")
}

fn normalize_sfm_selector(selector: &str) -> String {
    if selector.contains(':') {
        selector.to_string()
    } else {
        qualify_sfm_id(selector)
    }
}

fn wildcard_matches(candidate: &str, selector: &str) -> bool {
    let candidate = candidate.as_bytes();
    let selector = selector.as_bytes();
    let mut candidate_index = 0;
    let mut selector_index = 0;
    let mut star_index = None;
    let mut candidate_after_star = 0;

    while candidate_index < candidate.len() {
        if selector_index < selector.len()
            && (selector[selector_index] == b'?' || selector[selector_index] == candidate[candidate_index])
        {
            candidate_index += 1;
            selector_index += 1;
        } else if selector_index < selector.len() && selector[selector_index] == b'*' {
            star_index = Some(selector_index);
            selector_index += 1;
            candidate_after_star = candidate_index;
        } else if let Some(star) = star_index {
            selector_index = star + 1;
            candidate_after_star += 1;
            candidate_index = candidate_after_star;
        } else {
            return false;
        }
    }

    while selector_index < selector.len() && selector[selector_index] == b'*' {
        selector_index += 1;
    }
    selector_index == selector.len()
}

fn snake_case(input: &str) -> String {
    let mut output = String::new();
    let mut previous_was_lower_or_digit = false;
    let mut previous_was_digit = false;
    for character in input.chars() {
        let is_digit = character.is_ascii_digit();
        let is_upper = character.is_ascii_uppercase();
        if !output.is_empty()
            && ((is_upper && previous_was_lower_or_digit) || (is_digit && !previous_was_digit))
        {
            output.push('_');
        }
        output.extend(character.to_lowercase());
        previous_was_lower_or_digit = character.is_ascii_lowercase() || is_digit;
        previous_was_digit = is_digit;
    }
    output
}

fn catalog_category_label(category: SourceCatalogCategory) -> &'static str {
    match category {
        SourceCatalogCategory::Test => "test",
        SourceCatalogCategory::GameTest => "game-test",
        SourceCatalogCategory::Puppet => "puppet",
    }
}

fn format_static_catalog_entry(entry: &StaticJavaCatalogEntry) -> String {
    let dynamic = if entry.dynamic { " dynamic" } else { "" };
    let method = entry
        .method_name
        .as_deref()
        .map_or_else(String::new, |name| format!("#{name}"));
    format!(
        "{}{}  {}{}  {}:{}",
        entry.id,
        dynamic,
        entry.class_name,
        method,
        entry.source_path.display(),
        entry.source_line
    )
}

#[cfg(test)]
mod static_java_catalog_tests {
    use super::SourceCatalogCategory;
    use super::static_java_catalog;
    use super::snake_case;
    use super::wildcard_matches;
    use std::fs;
    use tempfile::tempdir;

    #[test]
    fn snake_case_matches_runtime_conventions() {
        assert_eq!(snake_case("Move1StackDirect"), "move_1_stack_direct");
        assert_eq!(snake_case("AE2Energy"), "ae_2_energy");
    }

    #[test]
    fn wildcard_matching_matches_puppet_selection_grammar() {
        assert!(wildcard_matches("sfm:move_1_stack_direct_walkthrough", "sfm:*_walkthrough"));
        assert!(wildcard_matches("sfm:move_1_stack_direct_walkthrough", "sfm:move_?_stack_*"));
        assert!(!wildcard_matches("sfm:move_1_stack_direct_walkthrough", "sfm:other_*"));
        assert!(!wildcard_matches("sfm:move_1_stack_direct_walkthrough", "sfm:move_[1]_stack_*"));
    }

    #[test]
    fn catalog_resolves_annotations_and_honors_source_exclusions() {
        let temporary = tempdir().expect("temporary source tree should be created");
        let minecraft_dir = temporary.path().join("minecraft");
        let source_root = minecraft_dir.join("src/gametest/java/example");
        fs::create_dir_all(&source_root).expect("source root should be created");
        fs::write(
            source_root.join("IncludedGamePuppet.java"),
            r"
                package example;
                import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
                @SFMGamePuppet
                public final class IncludedGamePuppet {}
            ",
        )
        .expect("included source should be written");
        fs::write(
            source_root.join("ExcludedGamePuppet.java"),
            r"
                package example;
                import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
                @SFMGamePuppet
                public final class ExcludedGamePuppet {}
            ",
        )
        .expect("excluded source should be written");
        fs::write(
            source_root.join("MentionOnly.java"),
            r#"
                package example;
                final class MentionOnly {
                    private static final String ANNOTATION = "@SFMGamePuppet";
                }
            "#,
        )
        .expect("unannotated source should be written");
        let excludes_path = minecraft_dir.join("gradle/source-excludes/1.19.2/gametest-java.txt");
        fs::create_dir_all(excludes_path.parent().expect("excludes parent should exist"))
            .expect("excludes parent should be created");
        fs::write(excludes_path, "example/ExcludedGamePuppet.java\n")
            .expect("excludes should be written");

        let entries = static_java_catalog(&minecraft_dir, "1.19.2", SourceCatalogCategory::Puppet)
            .expect("catalog should parse included source");
        assert_eq!(entries.len(), 1);
        assert_eq!(entries[0].id, "sfm:included");
        assert_eq!(entries[0].class_name, "example.IncludedGamePuppet");
    }

    #[test]
    fn catalog_rejects_malformed_or_duplicate_static_definitions() {
        let temporary = tempdir().expect("temporary source tree should be created");
        let minecraft_dir = temporary.path().join("minecraft");
        let source_root = minecraft_dir.join("src/gametest/java/example");
        fs::create_dir_all(&source_root).expect("source root should be created");
        fs::write(
            source_root.join("FirstGamePuppet.java"),
            r"
                package example;
                import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
                @SFMGamePuppet
                public final class FirstGamePuppet {}
            ",
        )
        .expect("first source should be written");
        fs::write(
            source_root.join("First.java"),
            r"
                package example;
                import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
                @SFMGamePuppet
                public final class First {}
            ",
        )
        .expect("duplicate source should be written");

        let error = static_java_catalog(&minecraft_dir, "1.19.2", SourceCatalogCategory::Puppet)
            .expect_err("duplicate id should be rejected");
        assert!(error.to_string().contains("Duplicate static puppet id"));

        fs::remove_file(source_root.join("First.java")).expect("duplicate source should be removed");
        fs::write(source_root.join("Malformed.java"), "public class Malformed {")
            .expect("malformed source should be written");
        let error = static_java_catalog(&minecraft_dir, "1.19.2", SourceCatalogCategory::Puppet)
            .expect_err("malformed Java should be rejected");
        assert!(error.to_string().contains("could not parse Java source"));
    }
}
