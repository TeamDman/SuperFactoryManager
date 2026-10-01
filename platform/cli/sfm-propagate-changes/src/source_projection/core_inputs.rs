//! Core-owned membership and project-file selection for named projections.
//!
//! The public catalog chooses Minecraft version and explicit feature names.
//! This metadata only describes authored inputs and supported build targets;
//! it cannot choose a projection key, output root, historical tree, or preset.
//! Membership is resolved before selected file bytes are read or rendered.

use super::candidate_lock::checked_directory;
use super::candidate_lock::checked_file;
use super::context::ProjectionContext;
use super::core_network_layout::NETWORK_REGISTRATION_PATH;
use super::core_network_layout::validate_network_context;
use super::directive_scanner::ScannedSource;
use super::directive_scanner::scan;
use super::inputs::render_java_artifact;
use super::project_layout::validate_target_project;
use super::projection_catalog::SUPPORTED_TARGETS;
use super::projection_catalog::validate_projection_key;
use super::render_java_source;
use super::sync::MANIFEST_FILE;
use super::sync::ProjectedArtifact;
use eyre::Result;
use eyre::WrapErr;
use eyre::ensure;
use facet::Facet;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::fs;
use std::io::Read as _;
use std::path::Path;
use std::path::PathBuf;
use walkdir::WalkDir;

pub const CORE_ROOT: &str = "platform/minecraft/core-liquid-template";
pub const CORE_METADATA_PATH: &str = "platform/minecraft/core-liquid-template/project-inputs.json";
pub const MAX_CORE_METADATA_BYTES: u64 = 8 * 1024 * 1024;
pub const MAX_CORE_FILE_BYTES: u64 = 16 * 1024 * 1024;
pub const MAX_CORE_PROJECTION_BYTES: u64 = 512 * 1024 * 1024;

const REQUIRED_PROJECT_FILES: &[&str] = &[
    "build.gradle",
    "settings.gradle",
    "gradle.properties",
    "gradlew",
    "gradlew.bat",
    "sfm-toolchain.lock.json",
    "gradle/wrapper/gradle-wrapper.properties",
    "gradle/wrapper/gradle-wrapper.jar",
];

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
pub struct CoreProjectInputs {
    pub schema_version: u32,
    pub targets: BTreeMap<String, BuildTargetMetadata>,
    /// Sparse rules for canonical src/... output paths. Unmentioned files in
    /// core/src are shared. A rule replaces that default; no match omits it.
    pub source_rules: BTreeMap<String, Vec<InputVariant>>,
    /// Explicit project-root/Gradle/fixture inputs. Nothing outside core/src is
    /// discovered implicitly, so inactive implementations cannot leak out.
    pub project_files: BTreeMap<String, Vec<InputVariant>>,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
pub struct BuildTargetMetadata {
    pub java_major: u16,
    pub loader: String,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
pub struct InputVariant {
    /// Exact, portable path below core-liquid-template; never repository-root
    /// paths, globs, Git objects, or paths outside the core authoring boundary.
    pub input: String,
    #[facet(default)]
    pub when: InputPredicate,
    /// Opt in to controlled rendering for non-Java text. Every Java output is
    /// rendered regardless of this flag; binary assets remain exact by default.
    #[facet(default)]
    pub template: bool,
}

#[derive(Clone, Debug, Default, Eq, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
pub struct InputPredicate {
    /// Stable matrix IDs. An empty list does not constrain the target.
    #[facet(default)]
    pub targets: Vec<String>,
    #[facet(default)]
    pub all_features: Vec<String>,
    /// At least one must be enabled when this list is nonempty.
    #[facet(default)]
    pub any_features: Vec<String>,
    #[facet(default)]
    pub none_features: Vec<String>,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct SelectedCoreInput {
    pub input: String,
    pub template: bool,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct CoreSelection {
    pub target_id: String,
    pub build_target: BuildTargetMetadata,
    pub inputs: BTreeMap<String, SelectedCoreInput>,
    pub omitted_paths: BTreeSet<String>,
    minecraft_version: String,
    feature_flags: BTreeMap<String, bool>,
    target_flags: BTreeMap<String, bool>,
}

impl CoreProjectInputs {
    /// Parse bounded, typed authoring metadata without consulting a filesystem.
    ///
    /// # Errors
    ///
    /// Rejects unsupported schemas, duplicate JSON fields/path keys, unknown
    /// fields/flags/targets, unsafe paths, and contradictory membership rules.
    pub fn from_json(input: &str, registered_features: &BTreeSet<String>) -> Result<Self> {
        ensure!(
            input.len() as u64 <= MAX_CORE_METADATA_BYTES,
            "core project metadata exceeds the {MAX_CORE_METADATA_BYTES}-byte limit"
        );
        reject_duplicate_object_keys(input)?;
        let metadata: Self =
            facet_json::from_str(input).wrap_err("could not parse core project inputs")?;
        metadata.validate(registered_features)?;
        Ok(metadata)
    }

    /// Validate authoring paths and registered predicate names independently of
    /// which catalog entry will select them. Inactive typoed rules still fail.
    ///
    /// # Errors
    ///
    /// Rejects unsupported versions, loaders or JDK lines, invalid predicates,
    /// nonportable paths, case aliases, and file/directory output collisions.
    pub fn validate(&self, registered_features: &BTreeSet<String>) -> Result<()> {
        ensure!(
            self.schema_version == 1,
            "unsupported core project-input schema"
        );
        ensure!(
            !self.targets.is_empty(),
            "core project inputs declare no build targets"
        );
        for (id, target) in &self.targets {
            ensure!(supported_target(id), "unsupported core build target `{id}`");
            let (loader, java_major) = expected_build_target(id);
            ensure!(
                target.loader == loader && target.java_major == java_major,
                "core target `{id}` requires loader `{loader}` and Java {java_major}"
            );
        }
        let mut outputs = BTreeSet::new();
        let mut inputs = BTreeSet::new();
        for (output, variants) in &self.source_rules {
            validate_output_path(output)?;
            ensure!(
                output.starts_with("src/"),
                "source rule `{output}` must use a src/... output"
            );
            outputs.insert(output.as_str());
            validate_variants(output, variants, registered_features, &mut inputs)?;
        }
        for (output, variants) in &self.project_files {
            validate_output_path(output)?;
            ensure!(
                !output.starts_with("src/"),
                "project file `{output}` must not shadow source rules"
            );
            ensure!(
                outputs.insert(output.as_str()),
                "duplicate core output `{output}`"
            );
            validate_variants(output, variants, registered_features, &mut inputs)?;
        }
        validate_path_collisions(outputs.iter().copied(), "core output")?;
        validate_path_collisions(inputs.iter().copied(), "core input")?;
        Ok(())
    }
}

/// Enumerate core/src file names and metadata only, without reading contents.
/// Fragments and build implementations outside src require explicit selection.
///
/// # Errors
///
/// Rejects a wrong core layout, reparse/symlink paths, nonregular source entries,
/// nonportable names, case aliases, or file/directory collisions.
pub fn discover_core_source_files(core_root: &Path) -> Result<BTreeSet<String>> {
    let root = checked_core_root(core_root)?;
    let source_root = checked_directory(&root.join("src"))?;
    let mut paths = BTreeSet::new();
    for entry in WalkDir::new(&source_root)
        .follow_links(false)
        .sort_by_file_name()
    {
        let entry = entry.wrap_err("cannot enumerate core source names")?;
        if entry.path() == source_root {
            continue;
        }
        ensure!(
            !entry.file_type().is_symlink(),
            "core source traverses a symlink"
        );
        let relative = portable_path(entry.path().strip_prefix(&root)?)?;
        if entry.file_type().is_dir() {
            validate_output_path(&relative)?;
            checked_directory(entry.path())?;
        } else {
            validate_output_path(&relative)?;
            checked_file(&root, &relative)?;
            paths.insert(relative);
        }
    }
    validate_path_collisions(paths.iter().map(String::as_str), "core source")?;
    Ok(paths)
}

/// Resolve source membership and project variants using only Minecraft and
/// explicit flags. This function performs no reads or output writes.
///
/// # Errors
///
/// Rejects inconsistent context booleans, unknown predicate names, multiple
/// matching variants, unsafe inventories, and colliding selected output paths.
pub fn select_core_inputs(
    metadata: &CoreProjectInputs,
    context: &ProjectionContext,
    source_inventory: &BTreeSet<String>,
) -> Result<CoreSelection> {
    let registered = context.features.keys().cloned().collect();
    metadata.validate(&registered)?;
    let target_id = target_id(context)?;
    let build_target = metadata
        .targets
        .get(target_id)
        .ok_or_else(|| eyre::eyre!("core inputs do not declare build target `{target_id}`"))?;
    validate_target_flags(context, target_id, &build_target.loader)?;
    let mut selected = BTreeMap::new();
    let mut omitted_paths = BTreeSet::new();
    for output in source_inventory {
        validate_output_path(output)?;
        ensure!(
            output.starts_with("src/"),
            "core source inventory contains non-source output `{output}`"
        );
        if !metadata.source_rules.contains_key(output) {
            selected.insert(
                output.clone(),
                SelectedCoreInput {
                    input: output.clone(),
                    template: false,
                },
            );
        }
    }
    for (output, variants) in metadata.source_rules.iter().chain(&metadata.project_files) {
        let matches = variants
            .iter()
            .filter(|variant| variant.when.matches(target_id, &context.features))
            .collect::<Vec<_>>();
        ensure!(
            matches.len() <= 1,
            "core output `{output}` has more than one matching input variant"
        );
        if let Some(variant) = matches.first() {
            ensure!(
                selected
                    .insert(
                        output.clone(),
                        SelectedCoreInput {
                            input: variant.input.clone(),
                            template: variant.template
                        }
                    )
                    .is_none(),
                "core output `{output}` is selected more than once"
            );
        } else {
            omitted_paths.insert(output.clone());
        }
    }
    validate_path_collisions(selected.keys().map(String::as_str), "selected core output")?;
    validate_path_collisions(
        selected.values().map(|input| input.input.as_str()),
        "selected core input",
    )?;
    Ok(CoreSelection {
        target_id: target_id.to_owned(),
        build_target: build_target.clone(),
        inputs: selected,
        omitted_paths,
        minecraft_version: context.minecraft_version.clone(),
        feature_flags: context.features.clone(),
        target_flags: context.targets.clone(),
    })
}

/// Read only selected core-owned inputs and produce ordinary project artifacts.
/// Every Java output receives the established generated banner. Exact-copy
/// assets and wrappers preserve bytes; opted-in non-Java text is rendered.
///
/// # Errors
///
/// Rejects unsafe selected paths, changed selection context, oversized inputs,
/// invalid directives/UTF-8, unreviewed network layouts, forbidden environment/key selectors, and incomplete
/// or version-incorrect standalone Gradle inputs. Performs no output writes.
pub fn collect_core_artifacts(
    core_root: &Path,
    selection: &CoreSelection,
    context: &ProjectionContext,
) -> Result<BTreeMap<String, ProjectedArtifact>> {
    ensure!(
        selection.minecraft_version == context.minecraft_version
            && selection.feature_flags == context.features
            && selection.target_flags == context.targets,
        "core selection context changed before collection"
    );
    if selection.inputs.contains_key(NETWORK_REGISTRATION_PATH) {
        validate_network_context(&selection.target_id, context)?;
    }
    let root = checked_core_root(core_root)?;
    validate_path_collisions(
        selection.inputs.keys().map(String::as_str),
        "selected core output",
    )?;
    validate_path_collisions(
        selection.inputs.values().map(|input| input.input.as_str()),
        "selected core input",
    )?;
    let mut artifacts = BTreeMap::new();
    let mut total_bytes = 0;
    for (output, selected) in &selection.inputs {
        validate_output_path(output)?;
        validate_input_path(&selected.input)?;
        let bytes = read_selected_input(&root, &selected.input)?;
        total_bytes = add_to_budget(total_bytes, bytes.len() as u64)?;
        let java = is_java(output);
        let mut artifact = ProjectedArtifact {
            source_path: format!("{CORE_ROOT}/{}", selected.input),
            output_bytes: bytes.clone(),
            source_bytes: bytes,
            overlay: None,
        };
        if java || selected.template {
            let source = std::str::from_utf8(&artifact.source_bytes)
                .wrap_err_with(|| format!("core template `{}` is not UTF-8", selected.input))?;
            validate_core_template_selectors(source, output)?;
            if java {
                render_java_artifact(output, &mut artifact, context)?;
            } else {
                let (bom, body) = if let Some(body) = source.strip_prefix('\u{feff}') {
                    ("\u{feff}", body)
                } else {
                    ("", source)
                };
                let rendered = render_java_source(body, context)
                    .wrap_err_with(|| format!("cannot render core text `{}`", selected.input))?;
                artifact.output_bytes = Vec::with_capacity(bom.len() + rendered.len());
                artifact.output_bytes.extend_from_slice(bom.as_bytes());
                artifact.output_bytes.extend_from_slice(rendered.as_bytes());
            }
        }
        total_bytes = add_to_budget(total_bytes, artifact.output_bytes.len() as u64)?;
        ensure!(
            artifacts.insert(output.clone(), artifact).is_none(),
            "duplicate core artifact `{output}`"
        );
    }
    for required in REQUIRED_PROJECT_FILES {
        ensure!(
            artifacts.contains_key(*required),
            "core projection is missing standalone Gradle input `{required}`"
        );
    }
    validate_target_project(&artifacts, &selection.target_id, &context.minecraft_version)?;
    Ok(artifacts)
}

/// Resolve and render a complete standalone project from a checked core tree.
/// Registry validation remains caller-owned; no legacy metadata is consulted.
///
/// # Errors
///
/// Returns any validation, selection, safe-input read, or rendering failure.
pub fn prepare_core_artifacts(
    core_root: &Path,
    metadata: &CoreProjectInputs,
    context: &ProjectionContext,
    registered_features: &BTreeSet<String>,
) -> Result<BTreeMap<String, ProjectedArtifact>> {
    metadata.validate(registered_features)?;
    ensure!(
        context.features.keys().cloned().collect::<BTreeSet<_>>() == *registered_features,
        "core context must explicitly resolve every registered feature and no unknown feature"
    );
    let inventory = discover_core_source_files(core_root)?;
    let selected = select_core_inputs(metadata, context, &inventory)?;
    collect_core_artifacts(core_root, &selected, context)
}

impl InputPredicate {
    fn validate(&self, registered_features: &BTreeSet<String>) -> Result<()> {
        let mut targets = BTreeSet::new();
        for target in &self.targets {
            ensure!(
                supported_target(target),
                "unknown core predicate target `{target}`"
            );
            ensure!(
                targets.insert(target),
                "duplicate core predicate target `{target}`"
            );
        }
        for (name, features) in [
            ("all_features", &self.all_features),
            ("any_features", &self.any_features),
            ("none_features", &self.none_features),
        ] {
            let mut declared = BTreeSet::new();
            for feature in features {
                ensure!(
                    registered_features.contains(feature),
                    "unknown core predicate feature `{feature}`"
                );
                ensure!(
                    declared.insert(feature),
                    "duplicate `{name}` feature `{feature}`"
                );
            }
        }
        ensure!(
            !self
                .all_features
                .iter()
                .any(|feature| self.none_features.contains(feature)),
            "core predicate requires a feature to be both enabled and disabled"
        );
        ensure!(
            self.any_features.is_empty()
                || self
                    .any_features
                    .iter()
                    .any(|feature| !self.none_features.contains(feature)),
            "core predicate forbids every any_features alternative"
        );
        Ok(())
    }

    fn matches(&self, target: &str, features: &BTreeMap<String, bool>) -> bool {
        (self.targets.is_empty() || self.targets.iter().any(|candidate| candidate == target))
            && self.all_features.iter().all(|feature| features[feature])
            && (self.any_features.is_empty()
                || self.any_features.iter().any(|feature| features[feature]))
            && self.none_features.iter().all(|feature| !features[feature])
    }
}

fn validate_variants<'a>(
    output: &str,
    variants: &'a [InputVariant],
    registered_features: &BTreeSet<String>,
    inputs: &mut BTreeSet<&'a str>,
) -> Result<()> {
    for variant in variants {
        validate_input_path(&variant.input)?;
        variant
            .when
            .validate(registered_features)
            .wrap_err_with(|| format!("core output `{output}`"))?;
        inputs.insert(&variant.input);
    }
    Ok(())
}

fn supported_target(target: &str) -> bool {
    SUPPORTED_TARGETS.iter().any(|(id, _)| *id == target)
}

fn expected_build_target(target: &str) -> (&'static str, u16) {
    let loader = if matches!(target, "1.19.2" | "1.19.4" | "1.20") {
        "forge"
    } else {
        "neoforge"
    };
    let java = match target {
        "26.1.2" => 25,
        "1.21.0" | "1.21.1" => 21,
        _ => 17,
    };
    (loader, java)
}

fn target_id(context: &ProjectionContext) -> Result<&'static str> {
    SUPPORTED_TARGETS
        .iter()
        .find(|(_, minecraft)| *minecraft == context.minecraft_version)
        .map(|(id, _)| *id)
        .ok_or_else(|| {
            eyre::eyre!(
                "unsupported core Minecraft version `{}`",
                context.minecraft_version
            )
        })
}

fn validate_target_flags(context: &ProjectionContext, target: &str, loader: &str) -> Result<()> {
    let mut expected = SUPPORTED_TARGETS
        .iter()
        .map(|(id, _)| (format!("mc_{}", id.replace('.', "_")), *id == target))
        .collect::<BTreeMap<_, _>>();
    expected.insert("forge".to_owned(), loader == "forge");
    expected.insert("neoforge".to_owned(), loader == "neoforge");
    ensure!(
        context.targets == expected,
        "core target booleans do not match the declared Minecraft version and loader"
    );
    Ok(())
}

fn checked_core_root(root: &Path) -> Result<PathBuf> {
    ensure!(
        root.ends_with(Path::new(CORE_ROOT)),
        "core source root must use the fixed platform/minecraft/core-liquid-template layout"
    );
    checked_directory(root)
}

fn validate_input_path(path: &str) -> Result<()> {
    validate_projection_key(path)?;
    ensure!(
        !path.split('/').any(|part| matches!(
            part.to_ascii_lowercase().as_str(),
            ".git" | ".gradle" | ".idea"
        )) && !path
            .split('/')
            .next()
            .is_some_and(|part| matches!(part.to_ascii_lowercase().as_str(), "run" | "target")),
        "core input `{path}` names runtime, Git or IDE state"
    );
    Ok(())
}

fn validate_output_path(path: &str) -> Result<()> {
    validate_projection_key(path)?;
    ensure!(
        !path.split('/').any(|part| matches!(
            part.to_ascii_lowercase().as_str(),
            ".git" | ".gradle" | ".idea"
        )) && !path.split('/').next().is_some_and(|part| matches!(
            part.to_ascii_lowercase().as_str(),
            "build" | "run" | "target"
        )) && !path
            .split('/')
            .next()
            .is_some_and(|part| part.eq_ignore_ascii_case(MANIFEST_FILE)),
        "core output `{path}` names generated state or projection provenance"
    );
    Ok(())
}

fn portable_path(path: &Path) -> Result<String> {
    let parts = path
        .components()
        .map(|part| match part {
            std::path::Component::Normal(name) => name
                .to_str()
                .ok_or_else(|| eyre::eyre!("core path is not UTF-8")),
            _ => Err(eyre::eyre!("core path is not an exact relative path")),
        })
        .collect::<Result<Vec<_>>>()?;
    let path = parts.join("/");
    validate_projection_key(&path)?;
    Ok(path)
}

fn validate_path_collisions<'a>(
    paths: impl IntoIterator<Item = &'a str>,
    role: &str,
) -> Result<()> {
    let mut files = BTreeMap::new();
    let mut prefixes = BTreeMap::new();
    for path in paths {
        validate_projection_key(path)?;
        let folded = path.to_ascii_lowercase();
        if let Some(other) = files.insert(folded, path) {
            ensure!(
                other == path,
                "{role} paths `{other}` and `{path}` collide by case"
            );
        }
        for index in path
            .match_indices('/')
            .map(|(index, _)| index)
            .chain(std::iter::once(path.len()))
        {
            let prefix = &path[..index];
            if let Some(other) = prefixes.insert(prefix.to_ascii_lowercase(), prefix) {
                ensure!(
                    other == prefix,
                    "{role} path components `{other}` and `{prefix}` collide by case"
                );
            }
        }
    }
    for (folded, path) in &files {
        for (index, _) in folded.match_indices('/') {
            ensure!(
                !files.contains_key(&folded[..index]),
                "{role} file `{path}` conflicts with an ancestor file"
            );
        }
    }
    Ok(())
}

fn read_selected_input(root: &Path, relative: &str) -> Result<Vec<u8>> {
    let path = checked_file(root, relative)?;
    let file = fs::File::open(path)
        .wrap_err_with(|| format!("cannot open selected core input `{relative}`"))?;
    ensure!(
        file.metadata()?.len() <= MAX_CORE_FILE_BYTES,
        "core input `{relative}` exceeds the {MAX_CORE_FILE_BYTES}-byte limit"
    );
    let mut bytes = Vec::new();
    file.take(MAX_CORE_FILE_BYTES + 1)
        .read_to_end(&mut bytes)
        .wrap_err_with(|| format!("cannot read selected core input `{relative}`"))?;
    ensure!(
        bytes.len() as u64 <= MAX_CORE_FILE_BYTES,
        "core input `{relative}` exceeds the {MAX_CORE_FILE_BYTES}-byte limit"
    );
    Ok(bytes)
}

fn add_to_budget(current: u64, additional: u64) -> Result<u64> {
    let next = current
        .checked_add(additional)
        .ok_or_else(|| eyre::eyre!("core projection byte budget overflow"))?;
    ensure!(
        next <= MAX_CORE_PROJECTION_BYTES,
        "core projection source/output bytes exceed the {MAX_CORE_PROJECTION_BYTES}-byte limit"
    );
    Ok(next)
}

fn is_java(path: &str) -> bool {
    Path::new(path)
        .extension()
        .is_some_and(|extension| extension.eq_ignore_ascii_case("java"))
}

fn validate_core_template_selectors(source: &str, path: &str) -> Result<()> {
    // Java's BOM is preserved by render_java_artifact and must not hide the
    // first whole-line directive from this same parsed-selector inspection.
    let source = source.strip_prefix('\u{feff}').unwrap_or(source);
    if let ScannedSource::Template(template) = scan(source)? {
        for (selector, line) in template.referenced_selectors {
            ensure!(
                selector == "minecraft_version",
                "core template `{path}` line {line}: production selection may only case on minecraft_version, not `{selector}`; use explicit feature flags"
            );
        }
    }
    Ok(())
}

/// Every JSON object, not just the root map, needs duplicate-name refusal:
/// Facet map insertion can otherwise replace an authored path silently.
fn reject_duplicate_object_keys(input: &str) -> Result<()> {
    let bytes = input.as_bytes();
    let mut position = 0;
    let mut containers: Vec<Option<BTreeSet<String>>> = Vec::new();
    while position < bytes.len() {
        match bytes[position] {
            b'{' => {
                containers.push(Some(BTreeSet::new()));
                position += 1;
            }
            b'[' => {
                containers.push(None);
                position += 1;
            }
            b'}' | b']' => {
                containers.pop();
                position += 1;
            }
            b'"' => {
                let start = position;
                position += 1;
                let mut closed = false;
                while position < bytes.len() {
                    match bytes[position] {
                        b'\\' => position = (position + 2).min(bytes.len()),
                        b'"' => {
                            position += 1;
                            closed = true;
                            break;
                        }
                        _ => position += 1,
                    }
                }
                let mut next = position;
                while next < bytes.len() && bytes[next].is_ascii_whitespace() {
                    next += 1;
                }
                if closed
                    && bytes.get(next) == Some(&b':')
                    && let Some(Some(keys)) = containers.last_mut()
                {
                    let key: String = facet_json::from_str(&input[start..position])
                        .wrap_err("invalid core metadata object key")?;
                    ensure!(
                        keys.insert(key.clone()),
                        "duplicate core metadata key `{key}`"
                    );
                }
            }
            _ => position += 1,
        }
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn registry() -> BTreeSet<String> {
        BTreeSet::from(["alpha".to_owned(), "beta".to_owned()])
    }

    fn context(version: &str, alpha: bool) -> ProjectionContext {
        let target = SUPPORTED_TARGETS
            .iter()
            .find(|(_, minecraft)| *minecraft == version)
            .unwrap()
            .0;
        let (loader, _) = expected_build_target(target);
        let mut targets = SUPPORTED_TARGETS
            .iter()
            .map(|(id, _)| (format!("mc_{}", id.replace('.', "_")), *id == target))
            .collect::<BTreeMap<_, _>>();
        targets.insert("forge".to_owned(), loader == "forge");
        targets.insert("neoforge".to_owned(), loader == "neoforge");
        ProjectionContext {
            minecraft_version: version.to_owned(),
            preset: "arbitrary/nested/key".to_owned(),
            environment: "dev".to_owned(),
            projection_key: "arbitrary/nested/key".to_owned(),
            features: BTreeMap::from([("alpha".to_owned(), alpha), ("beta".to_owned(), false)]),
            targets,
        }
    }

    fn variant(input: &str) -> InputVariant {
        InputVariant {
            input: input.to_owned(),
            when: InputPredicate::default(),
            template: false,
        }
    }

    fn metadata() -> CoreProjectInputs {
        CoreProjectInputs {
            schema_version: 1,
            targets: SUPPORTED_TARGETS
                .iter()
                .map(|(id, _)| {
                    let (loader, java_major) = expected_build_target(id);
                    (
                        (*id).to_owned(),
                        BuildTargetMetadata {
                            java_major,
                            loader: loader.to_owned(),
                        },
                    )
                })
                .collect(),
            source_rules: BTreeMap::new(),
            project_files: BTreeMap::new(),
        }
    }

    struct Fixture {
        _temp: tempfile::TempDir,
        core: PathBuf,
        metadata: CoreProjectInputs,
    }

    impl Fixture {
        fn new() -> Self {
            let temp = tempfile::tempdir().unwrap();
            let core = temp.path().join(CORE_ROOT);
            fs::create_dir_all(core.join("src/main/java")).unwrap();
            fs::write(core.join("src/main/java/Shared.java"), b"class Shared {}\n").unwrap();
            let mut fixture = Self {
                _temp: temp,
                core,
                metadata: metadata(),
            };
            for output in REQUIRED_PROJECT_FILES {
                let input = format!("build/common/{output}");
                let bytes: &[u8] = match *output {
                    "gradle.properties" => b"minecraft_version=1.19.2\nmod_version=4.34.0\n",
                    "settings.gradle" => b"rootProject.name = 'sfm-1.19.2'\n",
                    "gradle/wrapper/gradle-wrapper.properties" => {
                        b"distributionUrl=https\\://example.invalid/gradle-7.5-bin.zip\n"
                    }
                    "gradle/wrapper/gradle-wrapper.jar" => b"\0\xff\x01wrapper",
                    _ => b"fixture\n",
                };
                fixture.write(&input, bytes);
                fixture
                    .metadata
                    .project_files
                    .insert((*output).to_owned(), vec![variant(&input)]);
            }
            fixture
        }

        fn write(&self, input: &str, bytes: &[u8]) {
            let path = self.core.join(input);
            fs::create_dir_all(path.parent().unwrap()).unwrap();
            fs::write(path, bytes).unwrap();
        }

        fn prepare(
            &self,
            context: &ProjectionContext,
        ) -> Result<BTreeMap<String, ProjectedArtifact>> {
            prepare_core_artifacts(&self.core, &self.metadata, context, &registry())
        }
    }

    #[test]
    fn unreviewed_network_layout_refuses_collection_before_any_selected_source_read() {
        let mut resolved = context("1.19.2", false);
        for name in [
            "packet_transport_private",
            "client_inbox",
            "client_program_signing",
            "multiplayer_packets",
            "manager_operator_queries",
        ] {
            resolved.features.insert(name.to_owned(), false);
        }
        resolved
            .features
            .insert("packet_transport_private".to_owned(), true);
        let selected = select_core_inputs(
            &metadata(),
            &resolved,
            &BTreeSet::from([NETWORK_REGISTRATION_PATH.to_owned()]),
        )
        .unwrap();
        let absent_root = Path::new("uncreated-core-layout-refusal-fixture");
        let error = collect_core_artifacts(absent_root, &selected, &resolved).unwrap_err();
        assert!(
            error
                .to_string()
                .contains("unreviewed partial network layout")
        );

        resolved.features.remove("client_program_signing");
        let selected = select_core_inputs(
            &metadata(),
            &resolved,
            &BTreeSet::from([NETWORK_REGISTRATION_PATH.to_owned()]),
        )
        .unwrap();
        let error = collect_core_artifacts(absent_root, &selected, &resolved).unwrap_err();
        assert!(
            error
                .to_string()
                .contains("explicitly resolve `client_program_signing`")
        );
    }

    #[test]
    fn synthetic_baseline_registry_can_still_collect_a_network_source() {
        let fixture = Fixture::new();
        fixture.write(NETWORK_REGISTRATION_PATH, b"class SFMPackets {}\n");
        let artifacts = fixture.prepare(&context("1.19.2", false)).unwrap();
        assert!(artifacts.contains_key(NETWORK_REGISTRATION_PATH));
    }

    #[test]
    fn ordinary_java_and_exact_binary_inputs_remain_core_owned_and_read_only() {
        let fixture = Fixture::new();
        let before = fs::read(fixture.core.join("src/main/java/Shared.java")).unwrap();
        let files = fixture.prepare(&context("1.19.2", false)).unwrap();
        let java = &files["src/main/java/Shared.java"];
        assert_eq!(
            java.source_path,
            format!("{CORE_ROOT}/src/main/java/Shared.java")
        );
        assert_eq!(java.source_bytes, before);
        assert!(
            java.output_bytes
                .starts_with(b"// GENERATED by sfm-propagate-changes;")
        );
        assert_eq!(
            files["gradle/wrapper/gradle-wrapper.jar"].source_bytes,
            files["gradle/wrapper/gradle-wrapper.jar"].output_bytes
        );
        assert!(
            files
                .values()
                .all(|file| file.source_path.starts_with(CORE_ROOT) && file.overlay.is_none())
        );
        assert_eq!(
            fs::read(fixture.core.join("src/main/java/Shared.java")).unwrap(),
            before
        );
        assert!(!fixture.core.parent().unwrap().join("projections").exists());
    }

    #[test]
    fn disabled_source_and_asset_membership_is_decided_before_reads_and_rendering() {
        let mut fixture = Fixture::new();
        fixture.write("src/main/java/Disabled.java", b"{% malformed forever %}\n");
        let mut disabled = variant("src/main/java/Disabled.java");
        disabled.when.all_features.push("alpha".to_owned());
        fixture
            .metadata
            .source_rules
            .insert("src/main/java/Disabled.java".to_owned(), vec![disabled]);
        let mut missing = variant("assets/missing.png");
        missing.when.all_features.push("alpha".to_owned());
        fixture.metadata.source_rules.insert(
            "src/main/resources/assets/example.png".to_owned(),
            vec![missing],
        );
        let files = fixture.prepare(&context("1.19.2", false)).unwrap();
        assert!(!files.contains_key("src/main/java/Disabled.java"));
        assert!(!files.contains_key("src/main/resources/assets/example.png"));
        assert!(fixture.prepare(&context("1.19.2", true)).is_err());
    }

    #[test]
    fn explicit_fragment_replaces_shared_default_without_rendering_the_replaced_file() {
        let mut fixture = Fixture::new();
        fixture.write("src/main/java/Shared.java", b"{% bad ignored primary %}\n");
        fixture.write("fragments/Shared.java", b"{% case minecraft_version %}\n{% when \"1.19.2\", \"1.19.4\" %}\nclass Shared { int old; }\n{% else %}\nclass Shared { int newer; }\n{% endcase %}\n");
        fixture.metadata.source_rules.insert(
            "src/main/java/Shared.java".to_owned(),
            vec![variant("fragments/Shared.java")],
        );
        let files = fixture.prepare(&context("1.19.2", false)).unwrap();
        assert!(
            std::str::from_utf8(&files["src/main/java/Shared.java"].output_bytes)
                .unwrap()
                .contains("int old")
        );
        assert_eq!(
            files["src/main/java/Shared.java"].source_path,
            format!("{CORE_ROOT}/fragments/Shared.java")
        );
        assert!(!files.contains_key("fragments/Shared.java"));
    }

    #[test]
    fn selected_java_always_renders_while_non_java_text_requires_opt_in() {
        let mut fixture = Fixture::new();
        fixture.write("src/main/java/Shared.java", b"{% if features.alpha %}\nclass Enabled {}\n{% else %}\nclass Disabled {}\n{% endif %}\n");
        fixture.write(
            "build/plain.txt",
            b"{% if features.alpha %}\nliteral text\n{% endif %}\n",
        );
        fixture
            .metadata
            .project_files
            .insert("plain.txt".to_owned(), vec![variant("build/plain.txt")]);
        let mut templated = variant("build/plain.txt");
        templated.template = true;
        fixture
            .metadata
            .project_files
            .insert("rendered.txt".to_owned(), vec![templated]);
        let files = fixture.prepare(&context("1.19.2", true)).unwrap();
        assert!(
            std::str::from_utf8(&files["src/main/java/Shared.java"].output_bytes)
                .unwrap()
                .contains("class Enabled {}")
        );
        assert_eq!(
            files["plain.txt"].output_bytes,
            files["plain.txt"].source_bytes
        );
        assert_eq!(files["rendered.txt"].output_bytes, b"literal text\n");
    }

    #[test]
    fn java_and_opted_in_text_preserve_bom_without_hiding_initial_directives() {
        let mut fixture = Fixture::new();
        let source = "\u{feff}{% if features.alpha %}\nclass Enabled {}\n{% else %}\nclass Disabled {}\n{% endif %}\n";
        fixture.write("src/main/java/Shared.java", source.as_bytes());
        fixture.write("build/bom.txt", source.as_bytes());
        let mut text = variant("build/bom.txt");
        text.template = true;
        fixture
            .metadata
            .project_files
            .insert("bom.txt".to_owned(), vec![text]);
        let files = fixture.prepare(&context("1.19.2", true)).unwrap();
        let java = std::str::from_utf8(&files["src/main/java/Shared.java"].output_bytes).unwrap();
        assert!(java.starts_with("\u{feff}// GENERATED"));
        assert!(java.contains("class Enabled {}") && !java.contains("class Disabled {}"));
        assert_eq!(
            files["bom.txt"].output_bytes,
            "\u{feff}class Enabled {}\n".as_bytes()
        );
    }

    #[test]
    fn release_dev_and_arbitrary_keys_do_not_change_production_selection() {
        let fixture = Fixture::new();
        let mut release = context("1.19.2", false);
        release.environment = "release".to_owned();
        release.preset = "sfm-dev/misleading-name".to_owned();
        release.projection_key = "sfm-dev/misleading-name".to_owned();
        let dev = context("1.19.2", false);
        assert_eq!(
            fixture.prepare(&release).unwrap(),
            fixture.prepare(&dev).unwrap()
        );
        for selector in ["environment", "preset", "projection_key"] {
            fixture.write("src/main/java/Shared.java", format!("{{% case   {selector} %}}\n{{% when \"dev\" %}}\nclass Forbidden {{}}\n{{% else %}}\nclass Other {{}}\n{{% endcase %}}\n").as_bytes());
            assert!(fixture.prepare(&dev).is_err(), "accepted {selector}");
        }
    }

    #[test]
    fn any_all_none_and_target_predicates_resolve_without_environment_inference() {
        let mut declared = metadata();
        let mut input = variant("fragments/Conditional.java");
        input.when.targets = vec!["1.19.2".to_owned()];
        input.when.all_features = vec!["alpha".to_owned()];
        input.when.any_features = vec!["alpha".to_owned(), "beta".to_owned()];
        input.when.none_features = vec!["beta".to_owned()];
        declared
            .source_rules
            .insert("src/main/java/Conditional.java".to_owned(), vec![input]);
        let empty = BTreeSet::new();
        assert!(
            select_core_inputs(&declared, &context("1.19.2", true), &empty)
                .unwrap()
                .inputs
                .contains_key("src/main/java/Conditional.java")
        );
        assert!(
            select_core_inputs(&declared, &context("1.19.2", false), &empty)
                .unwrap()
                .omitted_paths
                .contains("src/main/java/Conditional.java")
        );
        assert!(
            select_core_inputs(&declared, &context("1.20.1", true), &empty)
                .unwrap()
                .inputs
                .is_empty()
        );
    }

    #[test]
    fn multiple_matching_variants_fail_instead_of_last_writer_wins() {
        let mut declared = metadata();
        declared.source_rules.insert(
            "src/main/java/Duplicate.java".to_owned(),
            vec![variant("fragments/One.java"), variant("fragments/Two.java")],
        );
        assert!(
            select_core_inputs(&declared, &context("1.19.2", false), &BTreeSet::new()).is_err()
        );
    }

    #[test]
    fn unknown_targets_features_duplicates_and_contradictory_predicates_fail_even_inactive() {
        for bad in [
            "target",
            "feature",
            "duplicates",
            "contradiction",
            "any_forbidden",
        ] {
            let mut declared = metadata();
            let mut input = variant("fragments/One.java");
            match bad {
                "target" => input.when.targets = vec!["1.21".to_owned()],
                "feature" => input.when.all_features = vec!["misspelled".to_owned()],
                "duplicates" => {
                    input.when.none_features = vec!["alpha".to_owned(), "alpha".to_owned()]
                }
                "contradiction" => {
                    input.when.all_features = vec!["alpha".to_owned()];
                    input.when.none_features = vec!["alpha".to_owned()];
                }
                "any_forbidden" => {
                    input.when.any_features = vec!["alpha".to_owned()];
                    input.when.none_features = vec!["alpha".to_owned()];
                }
                _ => unreachable!(),
            }
            declared
                .source_rules
                .insert("src/main/java/One.java".to_owned(), vec![input]);
            assert!(declared.validate(&registry()).is_err(), "accepted {bad}");
        }
    }

    #[test]
    fn unsafe_paths_case_aliases_and_file_directory_collisions_are_rejected() {
        for bad in [
            "../escape.java",
            "C:/outside.java",
            "src\\bad.java",
            "src/CON.java",
            "src/name.",
            ".git/config",
            "run/options.txt",
        ] {
            let mut declared = metadata();
            declared
                .source_rules
                .insert("src/main/java/One.java".to_owned(), vec![variant(bad)]);
            assert!(
                declared.validate(&registry()).is_err(),
                "accepted input {bad}"
            );
        }
        for outputs in [
            ["src/main/java/A.java", "src/main/java/a.java"],
            ["src/main/java/A", "src/main/java/A/B.java"],
            ["src/Main/java/A.java", "src/main/java/B.java"],
        ] {
            let mut declared = metadata();
            for output in outputs {
                declared
                    .source_rules
                    .insert(output.to_owned(), vec![variant("fragments/One.java")]);
            }
            assert!(
                declared.validate(&registry()).is_err(),
                "accepted {outputs:?}"
            );
        }
        let mut declared = metadata();
        declared
            .project_files
            .insert(MANIFEST_FILE.to_owned(), vec![variant("build/input")]);
        assert!(declared.validate(&registry()).is_err());
        validate_input_path("src/main/java/example/run/target/Build.java").unwrap();
        validate_output_path("src/main/java/example/build/run/Target.java").unwrap();
    }

    #[test]
    fn typed_metadata_rejects_environment_fields_duplicates_and_unsupported_schema() {
        let json = facet_json::to_string(&metadata()).unwrap();
        CoreProjectInputs::from_json(&json, &registry()).unwrap();
        for invalid in [
            json.replacen("\"schema_version\":1", "\"schema_version\":2", 1),
            json.replacen("\"schema_version\":1", "\"schema_version\":1,\"schema_version\":1", 1),
            json.replacen("\"source_rules\":{}", "\"source_rules\":{\"src/A.java\":[{\"input\":\"src/A.java\",\"when\":{\"environments\":[\"dev\"]}}]}", 1),
            json.replacen("\"source_rules\":{}", "\"source_rules\":{\"src/A.java\":[],\"src/\\u0041.java\":[]}", 1),
            json.replacen("\"source_rules\":{}", "\"source_rules\":{\"src/A.java\":[{\"input\":\"src/A.java\",\"snapshot\":\"old\"}]}", 1),
        ] {
            assert!(CoreProjectInputs::from_json(&invalid, &registry()).is_err(), "accepted {invalid}");
        }
        assert!(CoreProjectInputs::from_json("not JSON", &registry()).is_err());
    }

    #[test]
    fn build_target_and_context_boolean_boundaries_are_exact_for_all_ten_versions() {
        let declared = metadata();
        for (_, version) in SUPPORTED_TARGETS {
            let selected =
                select_core_inputs(&declared, &context(version, false), &BTreeSet::new()).unwrap();
            assert_eq!(
                selected.build_target.java_major,
                expected_build_target(&selected.target_id).1
            );
        }
        let mut bad = declared.clone();
        bad.targets.get_mut("1.20.1").unwrap().loader = "forge".to_owned();
        assert!(bad.validate(&registry()).is_err());
        bad = declared.clone();
        bad.targets.get_mut("26.1.2").unwrap().java_major = 21;
        assert!(bad.validate(&registry()).is_err());
        let mut wrong = context("1.19.2", false);
        wrong.targets.insert("mc_26_1_2".to_owned(), true);
        assert!(select_core_inputs(&declared, &wrong, &BTreeSet::new()).is_err());
    }

    #[test]
    fn collection_rejects_changed_context_missing_wrappers_and_wrong_project_version() {
        let mut fixture = Fixture::new();
        let initial = context("1.19.2", false);
        let inventory = discover_core_source_files(&fixture.core).unwrap();
        let selection = select_core_inputs(&fixture.metadata, &initial, &inventory).unwrap();
        assert!(
            collect_core_artifacts(&fixture.core, &selection, &context("1.19.2", true)).is_err()
        );
        fixture.metadata.project_files.remove("gradlew.bat");
        assert!(fixture.prepare(&initial).is_err());
        fixture.metadata.project_files.insert(
            "gradlew.bat".to_owned(),
            vec![variant("build/common/gradlew.bat")],
        );
        fixture.write(
            "build/common/gradle.properties",
            b"minecraft_version=26.1.2\n",
        );
        assert!(fixture.prepare(&initial).is_err());
        fixture.write(
            "build/common/gradle.properties",
            b"minecraft_version=1.19.2\n",
        );
        fixture.write(
            "build/common/settings.gradle",
            b"rootProject.name = 'wrong-name'\n",
        );
        assert!(fixture.prepare(&initial).is_err());
    }

    #[test]
    fn reads_and_aggregate_byte_budget_are_bounded_without_large_allocations() {
        let fixture = Fixture::new();
        let path = fixture.core.join("build/oversized.bin");
        let file = fs::File::create(&path).unwrap();
        file.set_len(MAX_CORE_FILE_BYTES + 1).unwrap();
        assert!(read_selected_input(&fixture.core, "build/oversized.bin").is_err());
        assert!(add_to_budget(MAX_CORE_PROJECTION_BYTES, 1).is_err());
        assert!(add_to_budget(u64::MAX, 1).is_err());
        assert_eq!(
            add_to_budget(MAX_CORE_PROJECTION_BYTES - 1, 1).unwrap(),
            MAX_CORE_PROJECTION_BYTES
        );
        assert!(
            CoreProjectInputs::from_json(
                &" ".repeat(usize::try_from(MAX_CORE_METADATA_BYTES + 1).unwrap()),
                &registry()
            )
            .is_err()
        );
    }

    #[cfg(unix)]
    #[test]
    fn core_and_selected_inputs_refuse_symlinks() {
        use std::os::unix::fs::symlink;
        let fixture = Fixture::new();
        let external = fixture._temp.path().join("external.java");
        fs::write(&external, b"class External {}\n").unwrap();
        let local = fixture.core.join("src/main/java/Shared.java");
        fs::remove_file(&local).unwrap();
        symlink(&external, &local).unwrap();
        assert!(discover_core_source_files(&fixture.core).is_err());
        fs::remove_file(&local).unwrap();
        fs::write(local, b"class Shared {}\n").unwrap();
        fs::create_dir_all(fixture.core.join("fragments")).unwrap();
        symlink(&external, fixture.core.join("fragments/External.java")).unwrap();
        assert!(read_selected_input(&fixture.core, "fragments/External.java").is_err());
    }
}
