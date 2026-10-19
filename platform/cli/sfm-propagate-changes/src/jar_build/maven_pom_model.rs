use super::MavenCoordinate;
use super::maven_pom_dependency::MavenPomDependency;
use super::xml_node::XmlNode;
use std::collections::BTreeMap;
#[path = "maven_pom_resolution.rs"]
mod resolution;
use resolution::MavenPomResolution;
#[derive(Clone, Debug, Default)]
pub(super) struct MavenPomModel {
    pub properties: BTreeMap<String, String>,
    pub managed: BTreeMap<String, MavenPomDependency>,
    pub dependencies: Vec<MavenPomDependency>,
}
pub(super) fn expand(value: &str, properties: &BTreeMap<String, String>) -> eyre::Result<String> {
    let mut result = value.to_owned();
    for _ in 0..64 {
        let Some(start) = result.find("${") else {
            return Ok(result);
        };
        let end = result[start..]
            .find('}')
            .ok_or_else(|| eyre::eyre!("unclosed POM property"))?
            + start;
        let key = &result[start + 2..end];
        let replacement = properties
            .get(key)
            .ok_or_else(|| eyre::eyre!("unresolved POM property: {key}"))?;
        result.replace_range(start..=end, replacement);
        eyre::ensure!(
            result.len() <= 65536,
            "POM property expansion byte budget exceeded"
        );
    }
    eyre::bail!("cyclic or excessive POM property expansion: {value}")
}
fn aliases(properties: &mut BTreeMap<String, String>, coordinate: &MavenCoordinate) {
    for (key, value) in [
        ("groupId", &coordinate.group),
        ("artifactId", &coordinate.artifact),
        ("version", &coordinate.version),
    ] {
        properties.insert(format!("project.{key}"), value.clone());
        properties.insert(format!("pom.{key}"), value.clone());
    }
}
fn declared(node: Option<&XmlNode>) -> eyre::Result<Vec<MavenPomDependency>> {
    node.map_or_else(
        || Ok(Vec::new()),
        |n| {
            n.children
                .iter()
                .filter(|d| d.name == "dependency")
                .map(MavenPomDependency::parse)
                .collect()
        },
    )
}
fn load(
    coordinate: &MavenCoordinate,
    state: &mut MavenPomResolution,
    read: &mut impl FnMut(&MavenCoordinate) -> eyre::Result<String>,
) -> eyre::Result<MavenPomModel> {
    let key = coordinate.with_extension("pom").to_string();
    state.visits += 1;
    eyre::ensure!(
        state.visits <= 1024,
        "POM total visit budget exceeded at {key}"
    );
    eyre::ensure!(
        state.stack.len() < 32 && !state.stack.contains(&key),
        "cyclic/excessive parent or BOM hierarchy: {} -> {key}",
        state.stack.join(" -> ")
    );
    if let Some(model) = state.cache.get(&key) {
        return Ok(model.clone());
    }
    eyre::ensure!(
        state.cache.len() + state.stack.len() < 128,
        "POM unique model budget exceeded at {key}"
    );
    state.stack.push(key.clone());
    let result = build(coordinate, state, read);
    state.stack.pop();
    if let Ok(model) = &result {
        state.cache.insert(key, model.clone());
    }
    result
}
fn build(
    coordinate: &MavenCoordinate,
    state: &mut MavenPomResolution,
    read: &mut impl FnMut(&MavenCoordinate) -> eyre::Result<String>,
) -> eyre::Result<MavenPomModel> {
    validate_coordinate(coordinate)?;
    let text = read(&coordinate.with_extension("pom"))?;
    eyre::ensure!(
        text.len() <= 2 * 1024 * 1024,
        "POM artifact byte budget exceeded: {coordinate}"
    );
    state.bytes = state
        .bytes
        .checked_add(text.len())
        .ok_or_else(|| eyre::eyre!("POM total byte budget overflow"))?;
    eyre::ensure!(
        state.bytes <= 16 * 1024 * 1024,
        "POM total byte budget exceeded at {coordinate}"
    );
    let xml = XmlNode::parse(&text)?;
    eyre::ensure!(xml.name == "project", "POM root must be project");
    validate_profiles(&xml, coordinate)?;
    let mut own = BTreeMap::new();
    if let Some(properties) = xml.child("properties")? {
        for property in &properties.children {
            eyre::ensure!(
                property.children.is_empty(),
                "non-scalar POM property {} at {coordinate}",
                property.name
            );
            // MavenXpp3Reader visits properties in order; ModelBase.addProperty
            // uses Properties.put, so later scalar values replace earlier ones.
            own.insert(property.name.clone(), property.text.trim().to_owned());
        }
    }
    let mut parent_context = own.clone();
    aliases(&mut parent_context, coordinate);
    let mut inherited_coordinate = None;
    let mut model = if let Some(parent) = xml.child("parent")? {
        let field = |name| {
            super::maven_pom_model::expand(
                &parent
                    .value(name)?
                    .ok_or_else(|| eyre::eyre!("parent missing {name}"))?,
                &parent_context,
            )
        };
        let parent = MavenCoordinate::parse(&format!(
            "{}:{}:{}@pom",
            field("groupId")?,
            field("artifactId")?,
            field("version")?
        ))?;
        validate_coordinate(&parent)?;
        let model = load(&parent, state, read)?;
        inherited_coordinate = Some(parent.clone());
        for prefix in ["project.parent", "pom.parent", "parent"] {
            for (tag, value) in [
                ("groupId", &parent.group),
                ("artifactId", &parent.artifact),
                ("version", &parent.version),
            ] {
                own.insert(format!("{prefix}.{tag}"), value.clone());
            }
        }
        model
    } else {
        MavenPomModel::default()
    };
    model.properties.extend(own);
    aliases(&mut model.properties, coordinate);
    verify_model_coordinate(
        &xml,
        coordinate,
        inherited_coordinate.as_ref(),
        &model.properties,
    )?;
    merge_management(&xml, &mut model, state, read)?;
    let own = declared(xml.child("dependencies")?)?;
    for dependency in own {
        let key = dependency.resolved_key(&model.properties)?;
        let keys = model
            .dependencies
            .iter()
            .map(|d| d.resolved_key(&model.properties))
            .collect::<eyre::Result<Vec<_>>>()?;
        if let Some(index) = keys.iter().position(|k| *k == key) {
            model.dependencies[index] = dependency;
        } else {
            model.dependencies.push(dependency);
        }
    }
    Ok(model)
}
pub(super) fn runtime_dependencies(
    root: &MavenCoordinate,
    mut read: impl FnMut(&MavenCoordinate) -> eyre::Result<String>,
) -> eyre::Result<Vec<MavenCoordinate>> {
    let model = load(root, &mut MavenPomResolution::default(), &mut read)?;
    let mut result = Vec::new();
    for dependency in &model.dependencies {
        let key = dependency.resolved_key(&model.properties)?;
        let managed = model.managed.get(&key);
        let scope = dependency
            .scope
            .as_ref()
            .or_else(|| managed.and_then(|d| d.scope.as_ref()))
            .map(|s| expand(s, &model.properties))
            .transpose()?;
        let optional = dependency
            .optional
            .as_ref()
            .or_else(|| managed.and_then(|d| d.optional.as_ref()))
            .map(|s| expand(s, &model.properties))
            .transpose()?;
        if optional.as_deref() == Some("true")
            || !matches!(scope.as_deref().unwrap_or("compile"), "compile" | "runtime")
        {
            continue;
        }
        let expanded = dependency.interpolate(&model.properties)?;
        let managed = model
            .managed
            .get(&key)
            .map(|d| d.interpolate(&model.properties))
            .transpose()?;
        let dependency = expanded.managed_by(managed.as_ref());
        let optional = dependency.optional.as_deref().unwrap_or("false");
        eyre::ensure!(
            optional == "true" || optional == "false",
            "invalid optional flag: {optional}"
        );
        if optional == "true"
            || !matches!(
                dependency.scope.as_deref().unwrap_or("compile"),
                "compile" | "runtime"
            )
        {
            continue;
        }
        eyre::ensure!(
            dependency.kind.as_deref().unwrap_or("jar") == "jar",
            "unsupported runtime dependency type: {key}"
        );
        eyre::ensure!(
            dependency.exclusions.is_empty(),
            "runtime exclusions need an edge-aware closure: {key}; refused rather than silently including excluded libraries"
        );
        let version = dependency.version.ok_or_else(|| {
            eyre::eyre!("missing managed dependency version: {key} (root {root})")
        })?;
        eyre::ensure!(
            !version.contains(['[', ']', '(', ')']) && !version.ends_with("SNAPSHOT"),
            "unpinned/ranged dependency version: {key}:{version}"
        );
        let resolved = MavenCoordinate {
            group: dependency.group,
            artifact: dependency.artifact,
            version,
            classifier: dependency.classifier,
            extension: "jar".into(),
        };
        validate_coordinate(&resolved)?;
        result.push(resolved);
    }
    Ok(result)
}

pub(super) fn validate_coordinate(coordinate: &MavenCoordinate) -> eyre::Result<()> {
    for value in [
        &coordinate.group,
        &coordinate.artifact,
        &coordinate.version,
        &coordinate.extension,
    ]
    .into_iter()
    .chain(coordinate.classifier.iter())
    {
        eyre::ensure!(
            !value.is_empty()
                && value.len() <= 256
                && value != "."
                && value != ".."
                && value
                    .bytes()
                    .all(|b| b.is_ascii_alphanumeric() || b"._-+".contains(&b)),
            "unsafe/unpinned Maven metadata coordinate: {coordinate}"
        );
    }
    eyre::ensure!(
        coordinate.group.split('.').all(|p| !p.is_empty()),
        "empty Maven group segment: {coordinate}"
    );
    Ok(())
}

#[cfg(test)]
#[path = "maven_pom_model_tests.rs"]
mod tests;

fn validate_profiles(xml: &XmlNode, coordinate: &MavenCoordinate) -> eyre::Result<()> {
    if let Some(profiles) = xml.child("profiles")? {
        for profile in &profiles.children {
            eyre::ensure!(
                profile.child("dependencyManagement")?.is_none(),
                "profile-dependent management requires explicit target activation policy: {coordinate}"
            );
            for dependency in declared(profile.child("dependencies")?)? {
                eyre::ensure!(
                    dependency.optional.as_deref() == Some("true")
                        || matches!(
                            dependency.scope.as_deref(),
                            Some("test" | "provided" | "system")
                        ),
                    "profile-dependent runtime closure requires explicit target activation policy: {coordinate}"
                );
            }
        }
    }
    Ok(())
}

fn verify_model_coordinate(
    xml: &XmlNode,
    coordinate: &MavenCoordinate,
    inherited_coordinate: Option<&MavenCoordinate>,
    properties: &BTreeMap<String, String>,
) -> eyre::Result<()> {
    for (tag, expected, inherited) in [
        (
            "groupId",
            &coordinate.group,
            inherited_coordinate.map(|p| &p.group),
        ),
        ("artifactId", &coordinate.artifact, None),
        (
            "version",
            &coordinate.version,
            inherited_coordinate.map(|p| &p.version),
        ),
    ] {
        let value = xml
            .value(tag)?
            .or_else(|| inherited.cloned())
            .ok_or_else(|| eyre::eyre!("POM missing {tag}: {coordinate}"))?;
        eyre::ensure!(
            expand(&value, properties)? == *expected,
            "POM coordinate mismatch for {coordinate}: {tag}"
        );
    }
    Ok(())
}

fn merge_management(
    xml: &XmlNode,
    model: &mut MavenPomModel,
    state: &mut MavenPomResolution,
    read: &mut impl FnMut(&MavenCoordinate) -> eyre::Result<String>,
) -> eyre::Result<()> {
    let inherited = std::mem::take(&mut model.managed);
    let management = xml
        .child("dependencyManagement")?
        .map(|n| n.child("dependencies"))
        .transpose()?
        .flatten();
    let mut imports = Vec::new();
    for dependency in declared(management)? {
        let scope = dependency
            .scope
            .as_deref()
            .map(|s| expand(s, &model.properties))
            .transpose()?;
        if scope.as_deref() == Some("import") {
            let expanded = dependency.interpolate(&model.properties)?;
            eyre::ensure!(
                expanded.kind.as_deref() == Some("pom"),
                "BOM import must have type pom"
            );
            imports.push(expanded);
        } else {
            model
                .managed
                .insert(dependency.resolved_key(&model.properties)?, dependency);
        }
    }
    // Maven 3.9.9 seeds the importer with target management (already inherited
    // and child-overridden). Imports fill only missing keys, in source order.
    for dependency in inherited.into_values() {
        let key = dependency.resolved_key(&model.properties)?;
        model.managed.entry(key).or_insert(dependency);
    }
    for import in imports {
        let version = import
            .version
            .as_ref()
            .ok_or_else(|| eyre::eyre!("BOM import version missing: {}", import.key()))?;
        let coordinate = MavenCoordinate::parse(&format!(
            "{}:{}:{version}@pom",
            import.group, import.artifact
        ))?;
        let bom = load(&coordinate, state, read)?;
        for dependency in bom.managed.values() {
            let expanded = dependency.interpolate(&bom.properties)?;
            model.managed.entry(expanded.key()).or_insert(expanded);
        }
    }
    Ok(())
}
