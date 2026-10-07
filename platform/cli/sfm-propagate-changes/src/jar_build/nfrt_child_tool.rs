//! Construct a child command from the pinned original function and graph binding.
//! This is not an execution permit. The process host still owns SDK attestation,
//! immutable input leases, clean environment, child lifetime and result checks.

use super::nfrt_launch_contract::NfrtInputSnapshotExport;
use super::nfrt_launch_contract::PreparedNfrtInvocationInput;
use crate::source_projection::promotion::validate_relative_path;
use eyre::Result;
use eyre::ensure;
use facet::Facet;
use std::collections::BTreeMap;
use std::path::Path;
use std::process::Command;

#[derive(Facet)]
#[facet(deny_unknown_fields)]
struct ToolRequest {
    schema: String,
    node_id: String,
    configuration_json: String,
    workspace_relative_path: String,
    classpath_relative_paths: Vec<String>,
    main_class: Option<String>,
    jvm_args: Vec<String>,
    args: Vec<String>,
    #[facet(default)]
    argument_templates: Option<Vec<String>>,
}

#[derive(Facet)]
#[facet(deny_unknown_fields)]
struct ToolConfiguration {
    #[facet(rename = "class")]
    action_class: String,
    classpath: Vec<String>,
    main_class: Option<String>,
    repository: Option<String>,
    args: Vec<String>,
    jvm_args: Vec<String>,
    library_options_attached: bool,
    #[facet(default)]
    transform_preparation_json: Option<String>,
}

#[derive(Facet)]
#[facet(deny_unknown_fields)]
struct TransformPreparation {
    schema: String,
    data_ids: Vec<String>,
    additional_at: Vec<String>,
    validated_at: Vec<String>,
    interfaces: Vec<String>,
    parchment: Option<String>,
    parser_bindings: Vec<String>,
    additional_arguments: Vec<String>,
}

pub(crate) struct NfrtChildCommand {
    pub(crate) node_id: String,
    pub(crate) command: Command,
}

pub(crate) fn requested_node(payload: &str) -> Result<String> {
    ensure!(payload.len() <= 4 * 1024 * 1024, "Oversized child request");
    Ok(facet_json::from_str::<ToolRequest>(payload)?.node_id)
}

/// `graph_configuration` must come from the registered owned graph, not the
/// request itself. Template substitutions come only from the fixed Java bridge;
/// this function does not claim independently to interpret NFRT's environment.
#[expect(
    clippy::too_many_lines,
    reason = "Validate the complete original child command before constructing any process."
)]
pub(crate) fn prepare_child_command(
    prepared: &PreparedNfrtInvocationInput,
    root: &Path,
    java: &Path,
    graph_node_id: &str,
    graph_configuration: &str,
    payload: &str,
    sealed_paths: &BTreeMap<(String, String), String>,
) -> Result<NfrtChildCommand> {
    ensure!(payload.len() <= 4 * 1024 * 1024, "Oversized child request");
    let request: ToolRequest = facet_json::from_str(payload)?;
    ensure!(
        request.schema == "sfm:nfrt_child_tool_request@1"
            && request.node_id == graph_node_id
            && request.configuration_json == graph_configuration,
        "Child request changed its registered graph binding"
    );
    let config: ToolConfiguration = facet_json::from_str(graph_configuration)?;
    let specialized = matches!(
        config.action_class.as_str(),
        "net.neoforged.neoform.runtime.actions.ApplySourceTransformAction"
            | "net.neoforged.neoform.runtime.actions.ApplyDevTransformsAction"
    );
    if specialized {
        validate_transform_configuration(prepared, &config)?;
    } else {
        ensure!(
            config.action_class == "net.neoforged.neoform.runtime.actions.ExternalJavaToolAction"
                && config.transform_preparation_json.is_none()
                && request.argument_templates.is_none(),
            "Child request is not an original external-tool action"
        );
        let step_library_alias = original_step_library_alias(
            &prepared.receipt().joined_steps_json,
            graph_node_id,
            &prepared.receipt().minecraft_version,
        )?;
        let original_function = prepared.receipt().functions.iter().any(|function| {
            function.classpath == config.classpath
                && function.main_class == config.main_class
                && function
                    .args
                    .iter()
                    .map(|argument| {
                        graph_recipe_function_argument(
                            argument,
                            &prepared.receipt().minecraft_version,
                            config.library_options_attached,
                            step_library_alias,
                        )
                    })
                    .eq(config.args.iter().cloned())
                && function.jvm_args == config.jvm_args
                && function.original_repository == config.repository
        });
        if !original_function {
            validate_patch_configuration(prepared, root, graph_node_id, &config)?;
        }
    }
    ensure!(
        request.workspace_relative_path == format!("work/{graph_node_id}"),
        "Child requested another workspace"
    );
    validate_relative_path(&request.workspace_relative_path)?;
    ensure!(
        !config.classpath.is_empty()
            && config.classpath.len() <= 64
            && request.main_class == config.main_class
            && request.classpath_relative_paths.len() == config.classpath.len(),
        "Child changed classpath cardinality or entry point"
    );
    let mut classpath = Vec::new();
    for (coordinate, relative) in config
        .classpath
        .iter()
        .zip(&request.classpath_relative_paths)
    {
        validate_relative_path(relative)?;
        let pins = prepared
            .receipt()
            .inputs
            .iter()
            .filter(|input| input.coordinate.as_ref() == Some(coordinate))
            .collect::<Vec<_>>();
        ensure!(
            pins.len() == 1 && pins[0].snapshot_relative_path == *relative,
            "Child classpath is not its unique original authenticated snapshot"
        );
        // The child cwd is exactly root/work/<node>. Keep Java's class-loader
        // arguments short and free of Windows verbatim prefixes; ownership was
        // already checked against the absolute immutable snapshot above.
        classpath.push(Path::new("../..").join(relative));
    }
    let expected_jvm = if (23..26).contains(&prepared.receipt().tool_sdk.major) {
        std::iter::once("--sun-misc-unsafe-memory-access=allow".to_owned())
            .chain(config.jvm_args.iter().cloned())
            .collect::<Vec<_>>()
    } else {
        config.jvm_args.clone()
    };
    // The original reviewed recipes have literal JVM options. Do not widen this
    // check to allow agent/classpath options supplied by an interpolation request.
    ensure!(
        request.jvm_args == expected_jvm,
        "Child changed pinned JVM options"
    );
    let templates = if specialized {
        let templates = request
            .argument_templates
            .as_ref()
            .ok_or_else(|| eyre::eyre!("Transform request omitted its prepared templates"))?;
        validate_transform_templates(
            &config,
            root,
            graph_node_id,
            templates,
            &prepared.receipt().inputs,
            sealed_paths,
        )?;
        templates
    } else {
        &config.args
    };
    ensure!(
        request.args.len() == templates.len() && request.args.len() <= 1024,
        "Child changed application argument cardinality"
    );
    let vineflower = config
        .classpath
        .iter()
        .any(|coordinate| coordinate.starts_with("org.vineflower:vineflower:"));
    for (template, argument) in templates.iter().zip(&request.args) {
        ensure!(
            argument.len() <= 64 * 1024 && !argument.contains('\0'),
            "Oversized or invalid child argument"
        );
        let template = if vineflower {
            template.replace("TRACE", "WARN")
        } else {
            template.clone()
        };
        if template.contains('{') {
            ensure!(
                !argument.contains('{') && !argument.contains('}'),
                "Unresolved child argument template"
            );
        } else {
            ensure!(
                *argument == template,
                "Child changed a literal original argument"
            );
        }
    }
    let mut command = Command::new(java);
    command.args(&request.jvm_args);
    if let Some(main) = &request.main_class {
        ensure!(
            !main.is_empty() && !main.starts_with('-'),
            "Invalid child main class"
        );
        command
            .arg("-cp")
            .arg(std::env::join_paths(&classpath)?)
            .arg(main);
    } else {
        ensure!(
            classpath.len() == 1,
            "Jar entry point requires one exact tool"
        );
        command.arg("-jar").arg(&classpath[0]);
    }
    command
        .args(&request.args)
        .current_dir(root.join(&request.workspace_relative_path));
    for name in &prepared.receipt().required_clean_environment_names {
        command.env_remove(name);
    }
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt as _;
        command.creation_flags(0x0800_0000) // CREATE_NO_WINDOW; these build tools are noninteractive.
    };
    Ok(NfrtChildCommand {
        node_id: request.node_id,
        command,
    })
}

#[derive(Facet)]
struct NeoformPatchConfig {
    data: NeoformPatchData,
}
#[derive(Facet)]
struct NeoformPatchData {
    patches: BTreeMap<String, String>,
}
#[derive(Facet)]
struct Neoform26PatchConfig {
    spec: u32,
    version: String,
    data: Neoform26PatchData,
}
#[derive(Facet)]
struct Neoform26PatchData {
    patches: String,
}

/// Spec 6's exact 26.1.2 parent uses a shared patch directory, not side keys.
/// Keep earlier recipes on their original joined-side contract.
fn original_neoform_patch_prefix(bytes: &[u8], version: &str) -> Result<String> {
    if version == "26.1.2" {
        let original: Neoform26PatchConfig = facet_json::from_slice(bytes)?;
        ensure!(
            original.spec == 6 && original.version == version && !original.data.patches.is_empty(),
            "Original shared patch directory belongs to another recipe"
        );
        Ok(original.data.patches)
    } else {
        let original: NeoformPatchConfig = facet_json::from_slice(bytes)?;
        original
            .data
            .patches
            .get("joined")
            .cloned()
            .ok_or_else(|| eyre::eyre!("Original recipe has no joined patches"))
    }
}
#[derive(Facet)]
struct UserdevPatchConfig {
    patches: String,
    #[facet(rename = "patchesOriginalPrefix", default)]
    original_prefix: Option<String>,
    #[facet(rename = "patchesModifiedPrefix", default)]
    modified_prefix: Option<String>,
}

/// NFRT `PatchActionFactory` creates these two fixed external actions from the
/// original config archives. They are not `NeoForm` function-table entries.
fn validate_patch_configuration(
    prepared: &PreparedNfrtInvocationInput,
    root: &Path,
    node: &str,
    config: &ToolConfiguration,
) -> Result<()> {
    ensure!(
        matches!(node, "patch" | "applyNeoforgePatches")
            && config.classpath == ["io.codechicken:DiffPatch:2.0.0.36:all"]
            && config.main_class.is_none()
            && config.repository.is_none()
            && config.jvm_args.is_empty()
            && !config.library_options_attached,
        "External tool has no exact original function or patch-factory binding"
    );
    let tools = prepared
        .receipt()
        .inputs
        .iter()
        .filter(|input| {
            input.coordinate.as_deref() == Some("io.codechicken:DiffPatch:2.0.0.36:all")
                && input.origin == "approved_nfrt_child"
        })
        .collect::<Vec<_>>();
    ensure!(
        tools.len() == 1,
        "Patch tool is not its exact approved snapshot"
    );
    let parent = unique_patch_parent(
        &prepared.receipt().inputs,
        prepared.source_pin_origin(),
        node,
    )?;
    let bytes = std::fs::read(root.join(&parent.snapshot_relative_path))?;
    ensure!(
        bytes.len() == parent.bytes
            && crate::source_projection::provenance::sha256(&bytes) == parent.full_sha256,
        "Held patch parent changed"
    );
    let config_bytes = super::nfrt_launch_contract::bounded_source_config(&bytes)?;
    let (prefix, base, modified) = if node == "patch" {
        (
            original_neoform_patch_prefix(&config_bytes, &prepared.receipt().minecraft_version)?,
            "a/".to_owned(),
            "b/".to_owned(),
        )
    } else {
        let original: UserdevPatchConfig = facet_json::from_slice(&config_bytes)?;
        (
            original.patches,
            original.original_prefix.unwrap_or_else(|| "a/".to_owned()),
            original.modified_prefix.unwrap_or_else(|| "b/".to_owned()),
        )
    };
    ensure!(
        config.args.len() == 21,
        "Patch factory argument cardinality changed"
    );
    let archive = owned_argument_path(root, node, &config.args[1])?;
    ensure!(
        archive
            == owned_argument_path(
                root,
                node,
                &root.join(&parent.snapshot_relative_path).to_string_lossy()
            )?,
        "Patch factory changed original archive"
    );
    let expected = [
        "{input}",
        config.args[1].as_str(),
        "--prefix",
        &prefix,
        "--patch",
        "--archive",
        "ZIP",
        "--output",
        "{output}",
        "--log-level",
        "WARN",
        "--mode",
        "OFFSET",
        "--archive-rejects",
        "ZIP",
        "--reject",
        "{outputRejects}",
        "--base-path-prefix",
        &base,
        "--modified-path-prefix",
        &modified,
    ];
    ensure!(
        config.args.iter().map(String::as_str).eq(expected),
        "Patch factory changed original arguments"
    );
    Ok(())
}

#[cfg(test)]
mod original_patch_directory_tests {
    use super::NeoformPatchConfig;
    use super::original_neoform_patch_prefix;

    #[test]
    fn actual_26_parent_reproduces_legacy_failure_and_reads_shared_directory() -> eyre::Result<()> {
        let fixture = include_str!("../../tests/fixtures/neoform/26.1.2-1-config.json");
        // The fixture adds one final LF; the archive entry has none.
        let original = fixture.strip_suffix('\n').unwrap_or(fixture).as_bytes();
        assert_eq!(
            crate::source_projection::provenance::sha256(original),
            "sha256:48e374b748a41985182aac160a0fdf625e037d85e8e12e482ef44b6ceaee4a59"
        );
        let error = facet_json::from_slice::<NeoformPatchConfig>(original)
            .err()
            .ok_or_else(|| {
                eyre::eyre!("Legacy reader unexpectedly accepted the real shared directory")
            })?;
        assert!(error.to_string().contains("data.patches"));
        assert_eq!(
            original_neoform_patch_prefix(original, "26.1.2")?,
            "patches/"
        );
        Ok(())
    }

    #[test]
    fn reduced_shared_and_joined_directories_keep_distinct_contracts() -> eyre::Result<()> {
        let shared = br#"{"spec":6,"version":"26.1.2","data":{"patches":"patches/"}}"#;
        let joined = br#"{"data":{"patches":{"joined":"patches/","client":"client/"}}}"#;
        assert_eq!(original_neoform_patch_prefix(shared, "26.1.2")?, "patches/");
        assert_eq!(original_neoform_patch_prefix(joined, "1.21.1")?, "patches/");
        assert!(original_neoform_patch_prefix(shared, "1.21.1").is_err());
        assert!(original_neoform_patch_prefix(joined, "26.1.2").is_err());
        Ok(())
    }

    #[test]
    fn shared_directory_rejects_foreign_recipe_and_invalid_shapes() {
        for bytes in [
            br#"{"spec":5,"version":"26.1.2","data":{"patches":"patches/"}}"#.as_slice(),
            br#"{"spec":6,"version":"1.21.1","data":{"patches":"patches/"}}"#.as_slice(),
            br#"{"spec":6,"version":"26.1.2","data":{"patches":null}}"#.as_slice(),
            br#"{"spec":6,"version":"26.1.2","data":{}}"#.as_slice(),
        ] {
            assert!(
                original_neoform_patch_prefix(bytes, "26.1.2").is_err(),
                "Malformed shared patch config accepted: {}",
                String::from_utf8_lossy(bytes)
            );
        }
        assert!(
            original_neoform_patch_prefix(
                br#"{"data":{"patches":{"client":"patches/"}}}"#,
                "1.21.1"
            )
            .is_err()
        );
    }
}

fn validate_transform_configuration(
    prepared: &PreparedNfrtInvocationInput,
    config: &ToolConfiguration,
) -> Result<()> {
    let source = config.action_class.ends_with(".ApplySourceTransformAction");
    let tool = if source {
        "net.neoforged.jst:jst-cli-bundle:2.0.8"
    } else {
        "net.neoforged.installertools:installertools:4.0.12:fatjar"
    };
    ensure!(
        config.classpath == [tool]
            && config.main_class.is_none()
            && config.repository.is_none()
            && config.jvm_args.is_empty()
            && config.args.is_empty()
            && config.library_options_attached == source,
        "Transform changed its pinned entry point or initial arguments"
    );
    let pins = prepared
        .receipt()
        .inputs
        .iter()
        .filter(|input| input.coordinate.as_deref() == Some(tool))
        .collect::<Vec<_>>();
    ensure!(
        pins.len() == 1 && pins[0].origin == "approved_nfrt_child",
        "Transform tool is not the approved exact source-tool snapshot"
    );
    let descriptor = config
        .transform_preparation_json
        .as_ref()
        .ok_or_else(|| eyre::eyre!("Missing frozen transform preparation"))?;
    ensure!(
        descriptor.len() <= 128 * 1024,
        "Oversized transform preparation"
    );
    let transform: TransformPreparation = facet_json::from_str(descriptor)?;
    ensure!(
        transform.schema
            == if source {
                "sfm:nfrt_source_transform@1"
            } else {
                "sfm:nfrt_dev_transform@1"
            },
        "Wrong transform preparation schema"
    );
    ensure!(
        transform.data_ids.len() <= 256
            && transform.parser_bindings.len() <= 256
            && transform.additional_arguments.len() <= 64,
        "Oversized transform configuration"
    );
    for id in &transform.data_ids {
        ensure!(
            !id.is_empty() && id.len() <= 4096 && !id.contains('\0'),
            "Invalid transform data ID"
        );
    }
    for path in transform
        .additional_at
        .iter()
        .chain(&transform.validated_at)
        .chain(&transform.interfaces)
        .chain(transform.parchment.iter())
    {
        validate_relative_path(path)?;
    }
    if !source {
        ensure!(
            transform.validated_at.is_empty()
                && transform.parchment.is_none()
                && transform.parser_bindings.is_empty()
                && transform.additional_arguments.is_empty(),
            "Dev transform introduced source-only behavior"
        );
    }
    Ok(())
}

/// Validate the fixed transform command grammar before interpolation. Variable
/// embedded AT paths come only from the bound Java extraction helper and must
/// stay within this invocation's fresh data/work trees. Literal options and
/// configured project paths cannot be replaced by the request.
#[expect(
    clippy::too_many_lines,
    reason = "Keep ordered transform arguments and their original input bindings together."
)]
fn validate_transform_templates(
    config: &ToolConfiguration,
    root: &Path,
    node: &str,
    templates: &[String],
    inputs: &[NfrtInputSnapshotExport],
    sealed_paths: &BTreeMap<(String, String), String>,
) -> Result<()> {
    ensure!(
        templates.len() <= 1024
            && templates
                .iter()
                .all(|arg| arg.len() <= 64 * 1024 && !arg.contains('\0')),
        "Unbounded transform templates"
    );
    let transform: TransformPreparation = facet_json::from_str(
        config
            .transform_preparation_json
            .as_deref()
            .ok_or_else(|| eyre::eyre!("Missing transform preparation"))?,
    )?;
    let source = transform.schema == "sfm:nfrt_source_transform@1";
    let mut cursor = 0;
    if source {
        expect_template(templates, &mut cursor, "--problems-report")?;
        expect_owned_path(
            templates,
            &mut cursor,
            root,
            node,
            &format!("work/{node}/problems.json"),
        )?;
        expect_template(templates, &mut cursor, "--libraries-list")?;
        expect_owned_path(
            templates,
            &mut cursor,
            root,
            node,
            &format!("work/{node}/libraries.txt"),
        )?;
        for literal in ["--in-format", "ARCHIVE", "--out-format", "ARCHIVE"] {
            expect_template(templates, &mut cursor, literal)?;
        }
        if !transform.data_ids.is_empty()
            || !transform.additional_at.is_empty()
            || !transform.validated_at.is_empty()
        {
            expect_template(templates, &mut cursor, "--enable-accesstransformers")?;
        }
    } else {
        for literal in [
            "--task",
            "PROCESS_MINECRAFT_JAR",
            "--input",
            "{input}",
            "--output",
            "{output}",
            "--no-mod-manifest",
        ] {
            expect_template(templates, &mut cursor, literal)?;
        }
    }
    let mut ats = Vec::new();
    while templates
        .get(cursor)
        .is_some_and(|value| value == "--access-transformer")
    {
        cursor += 1;
        let value = templates
            .get(cursor)
            .ok_or_else(|| eyre::eyre!("Missing AT path"))?;
        ats.push(owned_argument_path(root, node, value)?);
        cursor += 1;
    }
    let configured = transform
        .validated_at
        .iter()
        .chain(&transform.additional_at)
        .map(|path| owned_argument_path(root, node, &root.join(path).to_string_lossy()))
        .collect::<Result<Vec<_>>>()?;
    ensure!(
        ats.len() >= configured.len() && ats.ends_with(&configured),
        "Transform changed configured AT order or paths"
    );
    let extracted = &ats[..ats.len() - configured.len()];
    ensure!(
        !transform.data_ids.is_empty() || extracted.is_empty(),
        "Transform injected undeclared embedded ATs"
    );
    let fresh_work = owned_argument_path(
        root,
        node,
        &root.join(format!("work/{node}")).to_string_lossy(),
    )?;
    let fresh_home = owned_argument_path(root, node, &root.join("home").to_string_lossy())?;
    ensure!(
        extracted
            .iter()
            .all(|path| path.starts_with(&fresh_work) || path.starts_with(&fresh_home)),
        "Embedded AT left the fresh extraction trees"
    );
    if source && !transform.interfaces.is_empty() {
        expect_template(templates, &mut cursor, "--enable-interface-injection")?;
    }
    for path in &transform.interfaces {
        expect_template(templates, &mut cursor, "--interface-injection-data")?;
        expect_owned_path(templates, &mut cursor, root, node, path)?;
    }
    if source && !transform.interfaces.is_empty() {
        expect_template(templates, &mut cursor, "--interface-injection-stubs")?;
        expect_template(templates, &mut cursor, "{stubs}")?;
    }
    if let Some(path) = &transform.parchment {
        expect_template(templates, &mut cursor, "--enable-parchment")?;
        let value = templates
            .get(cursor)
            .and_then(|value| value.strip_prefix("--parchment-mappings="))
            .ok_or_else(|| eyre::eyre!("Missing exact Parchment argument"))?;
        ensure!(
            owned_argument_path(root, node, value)?
                == owned_argument_path(root, node, &root.join(path).to_string_lossy())?,
            "Parchment path changed"
        );
        cursor += 1;
    }
    if !transform.parser_bindings.is_empty() {
        expect_template(templates, &mut cursor, "--classpath")?;
        let value = templates
            .get(cursor)
            .ok_or_else(|| eyre::eyre!("Missing parser classpath"))?;
        let paths = std::env::split_paths(value).collect::<Vec<_>>();
        ensure!(
            paths.len() == transform.parser_bindings.len(),
            "Parser classpath cardinality changed"
        );
        for (path, binding) in paths.iter().zip(&transform.parser_bindings) {
            let expected = parser_binding_snapshot(binding, inputs, sealed_paths)?;
            ensure!(
                owned_argument_path(root, node, &path.to_string_lossy())?
                    == owned_argument_path(root, node, &root.join(expected).to_string_lossy())?,
                "Parser classpath changed its exact authenticated binding or order"
            );
        }
        cursor += 1;
    }
    for literal in &transform.additional_arguments {
        expect_template(templates, &mut cursor, literal)?;
    }
    if source {
        expect_template(templates, &mut cursor, "{input}")?;
        expect_template(templates, &mut cursor, "{output}")?;
    }
    ensure!(
        cursor == templates.len(),
        "Transform appended unregistered arguments"
    );
    Ok(())
}

/// Resolve only held original snapshots or outputs whose publication succeeded.
/// A graph declaration alone does not establish that a producer has completed.
fn parser_binding_snapshot<'a>(
    binding: &str,
    inputs: &'a [NfrtInputSnapshotExport],
    sealed_paths: &'a BTreeMap<(String, String), String>,
) -> Result<&'a str> {
    ensure!(
        binding.len() <= 4096 && !binding.contains('\0'),
        "Invalid parser binding"
    );
    if let Some(value) = binding.strip_prefix("node:") {
        let (node, output) = value
            .split_once('/')
            .ok_or_else(|| eyre::eyre!("Malformed parser producer binding"))?;
        return sealed_paths
            .get(&(node.to_owned(), output.to_owned()))
            .map(String::as_str)
            .ok_or_else(|| eyre::eyre!("Parser producer has no published sealed output"));
    }
    if let Some(value) = binding.strip_prefix("path:") {
        validate_relative_path(value)?;
        ensure!(
            inputs
                .iter()
                .any(|input| input.snapshot_relative_path == value)
                || sealed_paths.values().any(|path| path == value),
            "Parser path is not a held snapshot"
        );
        return inputs
            .iter()
            .find(|input| input.snapshot_relative_path == value)
            .map(|input| input.snapshot_relative_path.as_str())
            .or_else(|| {
                sealed_paths
                    .values()
                    .find(|path| path.as_str() == value)
                    .map(String::as_str)
            })
            .ok_or_else(|| eyre::eyre!("Missing parser snapshot"));
    }
    let (coordinate, repository, original_first) =
        if let Some(value) = binding.strip_prefix("maven:") {
            let (coordinate, repository) = value
                .rsplit_once('@')
                .ok_or_else(|| eyre::eyre!("Missing parser repository binding"))?;
            (
                coordinate,
                (repository != "null").then_some(repository),
                true,
            )
        } else if let Some(value) = binding.strip_prefix("minecraft:") {
            (value, None, false)
        } else {
            eyre::bail!("Unreviewed parser binding kind");
        };
    let coordinate = coordinate.strip_suffix("@jar").unwrap_or(coordinate);
    let artifact_path = coordinate_artifact_path(coordinate)?;
    let originals = inputs
        .iter()
        .filter(|input| input.coordinate.as_deref() == Some(coordinate))
        .collect::<Vec<_>>();
    let selected = if original_first && !originals.is_empty() {
        ensure!(originals.len() == 1, "Ambiguous parser coordinate");
        originals[0]
    } else {
        let key = format!("minecraft/{artifact_path}");
        let matches = inputs
            .iter()
            .filter(|input| {
                input.request_key == key && input.origin == "authenticated_minecraft_child"
            })
            .collect::<Vec<_>>();
        ensure!(
            matches.len() == 1,
            "Parser library lacks its exact authenticated Minecraft snapshot"
        );
        matches[0]
    };
    if let Some(repository) = repository {
        ensure!(
            repository.ends_with('/')
                && selected.exact_url.as_deref()
                    == Some(format!("{repository}{artifact_path}").as_str()),
            "Parser repository differs from authenticated artifact URL"
        );
    }
    Ok(&selected.snapshot_relative_path)
}

fn coordinate_artifact_path(coordinate: &str) -> Result<String> {
    let (coordinate, extension) = coordinate.split_once('@').unwrap_or((coordinate, "jar"));
    let parts = coordinate.split(':').collect::<Vec<_>>();
    ensure!(
        (3..=4).contains(&parts.len())
            && parts.iter().chain(std::iter::once(&extension)).all(|part| {
                !part.is_empty()
                    && *part != "."
                    && *part != ".."
                    && part.bytes().all(|byte| {
                        byte.is_ascii_alphanumeric() || matches!(byte, b'.' | b'_' | b'-')
                    })
            }),
        "Malformed parser Maven coordinate"
    );
    let classifier = parts
        .get(3)
        .map(|part| format!("-{part}"))
        .unwrap_or_default();
    let path = format!(
        "{}/{}/{}/{}-{}{}.{}",
        parts[0].replace('.', "/"),
        parts[1],
        parts[2],
        parts[1],
        parts[2],
        classifier,
        extension
    );
    validate_relative_path(&path)?;
    Ok(path)
}

fn unique_patch_parent<'a>(
    inputs: &'a [NfrtInputSnapshotExport],
    source_pin_origin: &str,
    node: &str,
) -> Result<&'a NfrtInputSnapshotExport> {
    ensure!(
        matches!(node, "patch" | "applyNeoforgePatches")
            && matches!(
                source_pin_origin,
                "original_schema2_pin" | "captured_schema4_pin"
            ),
        "Unreviewed patch source authority"
    );
    let parents = inputs
        .iter()
        .filter(|input| {
            input.coordinate.as_ref().is_some_and(|coordinate| {
                if node == "patch" {
                    coordinate.starts_with("net.neoforged:neoform:")
                } else {
                    coordinate.ends_with(":userdev")
                }
            })
        })
        .collect::<Vec<_>>();
    ensure!(
        parents.len() == 1,
        "Patch archive has no unique original parent"
    );
    ensure!(
        parents[0].origin == source_pin_origin,
        "Patch parent belongs to another source authority"
    );
    Ok(parents[0])
}

#[cfg(test)]
mod patch_source_tests {
    use super::*;

    #[test]
    fn actual_development_patch_parents_keep_catalog_bound_origins() {
        let development: Vec<NfrtInputSnapshotExport> = facet_json::from_str(include_str!(
            "../../tests/fixtures/neoform/development-patch-parents.json"
        ))
        .unwrap();
        for (node, path) in [
            ("patch", "inputs/artifacts/0001.zip"),
            ("applyNeoforgePatches", "inputs/artifacts/0002.jar"),
        ] {
            assert_eq!(
                unique_patch_parent(&development, "captured_schema4_pin", node)
                    .unwrap()
                    .snapshot_relative_path,
                path
            );
            assert!(unique_patch_parent(&development, "original_schema2_pin", node).is_err());
            let mut released = development.clone();
            for row in &mut released {
                row.origin = "original_schema2_pin".to_owned();
            }
            assert!(unique_patch_parent(&released, "original_schema2_pin", node).is_ok());
            assert!(unique_patch_parent(&released, "captured_schema4_pin", node).is_err());
            let mut duplicate = development.clone();
            duplicate.extend(development.clone());
            assert!(unique_patch_parent(&duplicate, "captured_schema4_pin", node).is_err());
            assert!(unique_patch_parent(&[], "captured_schema4_pin", node).is_err());
        }
        assert!(unique_patch_parent(&development, "captured_schema4_pin", "other").is_err());
        assert!(unique_patch_parent(&development, "unreviewed", "patch").is_err());
    }
}

#[cfg(test)]
mod parser_binding_tests {
    use super::*;

    fn snapshot(coordinate: Option<&str>, key: &str, origin: &str) -> NfrtInputSnapshotExport {
        NfrtInputSnapshotExport {
            request_key: key.to_owned(),
            coordinate: coordinate.map(str::to_owned),
            exact_url: Some(
                "https://example.test/repo/org/example/library/1/library-1.jar".to_owned(),
            ),
            origin: origin.to_owned(),
            original_identity: "sha256:test".to_owned(),
            snapshot_relative_path: "inputs/artifacts/0000.bin".to_owned(),
            bytes: 1,
            full_sha256: "sha256:test".to_owned(),
        }
    }

    #[test]
    fn parser_original_and_minecraft_bindings_are_exact() {
        let sealed = BTreeMap::new();
        let inputs = [snapshot(
            Some("org.example:library:1"),
            "original/library",
            "original_schema2_pin",
        )];
        for binding in [
            "maven:org.example:library:1@null",
            "maven:org.example:library:1@jar@https://example.test/repo/",
        ] {
            assert_eq!(
                parser_binding_snapshot(binding, &inputs, &sealed).unwrap(),
                "inputs/artifacts/0000.bin"
            );
        }
        for binding in [
            "maven:org.example:library:2@null",
            "maven:org.example:library:1@https://other.test/repo/",
            "minecraft:org.example:library:1",
        ] {
            assert!(
                parser_binding_snapshot(binding, &inputs, &sealed).is_err(),
                "{binding}"
            );
        }
        let minecraft = [snapshot(
            None,
            "minecraft/org/example/library/1/library-1.jar",
            "authenticated_minecraft_child",
        )];
        for binding in [
            "minecraft:org.example:library:1",
            "maven:org.example:library:1@null",
        ] {
            assert_eq!(
                parser_binding_snapshot(binding, &minecraft, &sealed).unwrap(),
                "inputs/artifacts/0000.bin"
            );
        }
        let duplicate = [inputs[0].clone(), inputs[0].clone()];
        assert!(
            parser_binding_snapshot("maven:org.example:library:1@null", &duplicate, &sealed)
                .is_err()
        );
    }

    #[test]
    fn parser_requires_publication_and_rejects_arbitrary_owned_paths() {
        let mut sealed = BTreeMap::new();
        assert!(parser_binding_snapshot("node:rename/output", &[], &sealed).is_err());
        sealed.insert(
            ("rename".to_owned(), "output".to_owned()),
            "inputs/generated/0001.bin".to_owned(),
        );
        for binding in ["node:rename/output", "path:inputs/generated/0001.bin"] {
            assert_eq!(
                parser_binding_snapshot(binding, &[], &sealed).unwrap(),
                "inputs/generated/0001.bin"
            );
        }
        for binding in [
            "node:rename/other",
            "node:other/output",
            "path:work/rename/output.jar",
            "path:inputs/generated/0002.bin",
            "path:../outside",
            "unknown:anything",
        ] {
            assert!(
                parser_binding_snapshot(binding, &[], &sealed).is_err(),
                "{binding}"
            );
        }
    }

    #[test]
    fn parser_coordinates_cannot_escape_artifact_membership() {
        assert_eq!(
            coordinate_artifact_path("org.example:library:1:fatjar@zip").unwrap(),
            "org/example/library/1/library-1-fatjar.zip"
        );
        for coordinate in [
            "org.example:library",
            "../group:library:1",
            "group:../library:1",
            "group:library:..",
            "group:library:1@../jar",
            "group:library:1:classifier:extra",
        ] {
            assert!(
                coordinate_artifact_path(coordinate).is_err(),
                "{coordinate}"
            );
        }
    }
}

fn expect_template(templates: &[String], cursor: &mut usize, expected: &str) -> Result<()> {
    ensure!(
        templates
            .get(*cursor)
            .is_some_and(|actual| actual == expected),
        "Transform changed literal argument {expected}"
    );
    *cursor += 1;
    Ok(())
}

fn expect_owned_path(
    templates: &[String],
    cursor: &mut usize,
    root: &Path,
    node: &str,
    expected: &str,
) -> Result<()> {
    let actual = templates
        .get(*cursor)
        .ok_or_else(|| eyre::eyre!("Missing transform path"))?;
    ensure!(
        owned_argument_path(root, node, actual)?
            == owned_argument_path(root, node, &root.join(expected).to_string_lossy())?,
        "Transform changed registered file path"
    );
    *cursor += 1;
    Ok(())
}

fn owned_argument_path(root: &Path, node: &str, argument: &str) -> Result<std::path::PathBuf> {
    use std::path::Component;
    ensure!(
        !argument.is_empty() && !argument.contains('{') && !argument.contains('}'),
        "Invalid transform file argument"
    );
    let root_text = root
        .to_str()
        .ok_or_else(|| eyre::eyre!("Non-UTF8 invocation root"))?;
    let root = Path::new(root_text.strip_prefix("\\\\?\\").unwrap_or(root_text));
    let text = argument.strip_prefix("\\\\?\\").unwrap_or(argument);
    let path = Path::new(text);
    ensure!(
        path.is_absolute() || !matches!(path.components().next(), Some(Component::Prefix(_))),
        "Drive-relative transform path"
    );
    let absolute = if path.is_absolute() {
        path.to_owned()
    } else {
        root.join("work").join(node).join(path)
    };
    let mut normalized = std::path::PathBuf::new();
    for component in absolute.components() {
        match component {
            Component::CurDir => (),
            Component::ParentDir => {
                ensure!(normalized.pop(), "Transform path traversal above its root");
            }
            _ => normalized.push(component.as_os_str()),
        }
    }
    ensure!(
        normalized.starts_with(root),
        "Transform file leaves owned invocation"
    );
    Ok(normalized)
}

/// NFRT substitutes the joined version and replaces the recipe library input
/// with its attached library-options producer. No other argument is rewritten.
fn graph_function_argument(argument: &str, version: &str, attached_libraries: bool) -> String {
    if attached_libraries && argument == "{libraries}" {
        "{listLibrariesOutput}".to_owned()
    } else {
        argument.replace("{version}", version)
    }
}

/// The 26.1.2 original step assigns a library producer to `inputLibraries`.
/// Admit only that authenticated declaration, not a caller-provided alias.
fn original_step_library_alias(steps: &[String], node: &str, version: &str) -> Result<bool> {
    if version != "26.1.2" || node != "decompile" {
        return Ok(false);
    }
    let mut matching = Vec::new();
    for raw in steps {
        let step: BTreeMap<String, String> = facet_json::from_str(raw)?;
        if step
            .get("name")
            .or_else(|| step.get("type"))
            .map(String::as_str)
            == Some(node)
        {
            matching.push(step);
        }
    }
    Ok(matching.len() == 1
        && matching[0].get("type").map(String::as_str) == Some("decompile")
        && matching[0].get("inputLibraries").map(String::as_str) == Some("{listLibrariesOutput}"))
}

fn graph_recipe_function_argument(
    argument: &str,
    version: &str,
    attached_libraries: bool,
    step_library_alias: bool,
) -> String {
    if attached_libraries && step_library_alias && argument == "-cfg={inputLibraries}" {
        "-cfg={listLibrariesOutput}".to_owned()
    } else {
        graph_function_argument(argument, version, attached_libraries)
    }
}

#[cfg(test)]
mod original_function_binding_tests {
    use super::graph_function_argument;
    use super::graph_recipe_function_argument;
    use super::original_step_library_alias;

    #[test]
    fn exact_26_library_alias_requires_the_original_step() -> eyre::Result<()> {
        let original = r#"{"type":"decompile","inputLibraries":"{listLibrariesOutput}","input":"{preProcessJarOutput}"}"#;
        assert!(original_step_library_alias(
            &[original.to_owned()],
            "decompile",
            "26.1.2"
        )?);
        for (node, version) in [("patch", "26.1.2"), ("decompile", "1.21.1")] {
            assert!(!original_step_library_alias(
                &[original.to_owned()],
                node,
                version
            )?);
        }
        assert!(!original_step_library_alias(&[], "decompile", "26.1.2")?);
        assert!(!original_step_library_alias(
            &[original.to_owned(), original.to_owned()],
            "decompile",
            "26.1.2"
        )?);
        assert!(!original_step_library_alias(
            &[original.replace("{listLibrariesOutput}", "{foreignOutput}")],
            "decompile",
            "26.1.2"
        )?);
        for (attached, declared) in [(false, false), (false, true), (true, false)] {
            assert_eq!(
                graph_recipe_function_argument(
                    "-cfg={inputLibraries}",
                    "26.1.2",
                    attached,
                    declared
                ),
                "-cfg={inputLibraries}"
            );
        }
        assert_eq!(
            graph_recipe_function_argument("-cfg={inputLibraries}", "26.1.2", true, true),
            "-cfg={listLibrariesOutput}"
        );
        assert_eq!(
            graph_recipe_function_argument("prefix-cfg={inputLibraries}", "26.1.2", true, true),
            "prefix-cfg={inputLibraries}"
        );
        Ok(())
    }

    #[test]
    fn attached_library_input_uses_original_graph_output() {
        assert_eq!(
            graph_function_argument("{libraries}", "1.20.2", true),
            "{listLibrariesOutput}"
        );
        assert_eq!(
            graph_function_argument("{libraries}", "1.20.2", false),
            "{libraries}"
        );
    }

    #[test]
    fn version_substitution_preserves_other_arguments() {
        assert_eq!(
            graph_function_argument("{version}", "1.20.2", false),
            "1.20.2"
        );
        for argument in [
            "--ann-fix",
            "{input}",
            "{mappings}",
            "{output}",
            "prefix{libraries}",
        ] {
            assert_eq!(graph_function_argument(argument, "1.20.2", true), argument);
        }
    }
}
