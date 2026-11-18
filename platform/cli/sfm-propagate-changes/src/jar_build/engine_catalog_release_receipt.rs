use crate::source_projection::catalog_release::CatalogReleaseArtifactReceipt;
use crate::source_projection::catalog_release::CatalogReleaseVersion;

// Called only after a final native package exists. The input admission remains
// live in execute_package_and_reobfuscate until this handoff finishes.
fn publish_catalog_release_receipt(
    context: &ExecutionContext<'_>,
    admission: Option<&NamedPackageAdmission>,
    package_input_identity: &str,
) -> eyre::Result<()> {
    use crate::source_projection::projection_catalog::ProjectionEnvironment;

    let Some(frozen) = context.plan.named_project() else {
        return Ok(());
    };
    let project = frozen.project();
    if project.receipt().environment != ProjectionEnvironment::Release {
        return Ok(());
    }
    let version = context
        .plan
        .properties
        .get("mod_version")
        .ok_or_else(|| eyre::eyre!("native release package has no mod_version"))?;
    let version = CatalogReleaseVersion::parse(version)?;
    if !project.has_feature(&version.required_feature()) {
        // Historical reproduction remains buildable. Its absent release flag
        // deliberately yields no receipt usable by current release preparation.
        tracing::info!(projection = %project.receipt().projection_key,
            "Native package has no selected release feature; no release artifact receipt emitted");
        return Ok(());
    }
    context.bail_if_cancelled()?;
    context.require_named_package_paths()?;
    let admission = admission
        .ok_or_else(|| eyre::eyre!("completed native package lost its input admission"))?;
    admission.recheck()?;
    context.plan.recheck_named_inputs()?;
    context.plan.recheck_named_sdk()?;
    let sdk_identity = named_sdk_identity(&context.plan.java)?;
    CatalogReleaseArtifactReceipt::publish_completed(
        project,
        &context.plan.rust_output_jar,
        frozen.receipt(),
        package_input_identity,
        &sdk_identity,
        &context.cancellation_token,
    )?;
    admission.recheck()?;
    context.plan.recheck_named_inputs()?;
    context.plan.recheck_named_sdk()?;
    Ok(())
}

/// Structural class sanity for a release archive, reusing the engine's parser.
/// This checks framing and identity; it is not JVM bytecode verification.
pub(crate) fn verify_catalog_release_main_class(bytes: &[u8], java_major: u16) -> eyre::Result<()> {
    eyre::ensure!(
        read_u32(bytes, 0)? == 0xcafe_babe,
        "release SFM class has no classfile magic"
    );
    eyre::ensure!(
        read_u16(bytes, 6)? == java_major + 44,
        "release JAR class major differs from selected Java target"
    );
    let pool = parse_class_constant_pool(bytes)?;
    let cursor = pool.after_constant_pool;
    let flags = read_u16(bytes, cursor)?;
    let this_class = read_u16(bytes, cursor + 2)?;
    eyre::ensure!(
        flags & 1 != 0 && pool.class_name(this_class) == Some("ca/teamdman/sfm/SFM"),
        "release class identity is not public ca/teamdman/sfm/SFM"
    );
    let interfaces = usize::from(read_u16(bytes, cursor + 6)?);
    let cursor = skip_class_members(bytes, cursor + 8 + interfaces * 2)?;
    let mut cursor = skip_class_members(bytes, cursor)?;
    let attributes = read_u16(bytes, cursor)?;
    cursor += 2;
    for _ in 0..attributes {
        let length = usize::try_from(read_u32(bytes, cursor + 2)?)?;
        cursor = cursor
            .checked_add(6)
            .and_then(|value| value.checked_add(length))
            .ok_or_else(|| eyre::eyre!("release class attribute length overflow"))?;
        eyre::ensure!(
            cursor <= bytes.len(),
            "release class attribute is truncated"
        );
    }
    eyre::ensure!(
        cursor == bytes.len(),
        "release class framing is incomplete or has trailing bytes"
    );
    Ok(())
}

// Shared with the existing path reader; release observation supplies the
// already checked selected bytes instead of opening an independent path.
fn native_packaging_properties(bytes: &[u8]) -> eyre::Result<BTreeMap<String, String>> {
    let content = from_utf8(bytes)?;
    let mut properties = BTreeMap::new();
    for line in content.lines().map(str::trim) {
        if line.is_empty() || line.starts_with('#') {
            continue;
        }
        if let Some((key, value)) = line.split_once('=') {
            properties.insert(key.trim().to_string(), value.trim().to_string());
        }
    }
    Ok(properties)
}

pub(crate) fn expand_catalog_release_metadata(
    template: &[u8],
    properties: &[u8],
) -> eyre::Result<Vec<u8>> {
    let properties = native_packaging_properties(properties)?;
    Ok(expand_gradle_resource_template(from_utf8(template)?, &properties)?.into_bytes())
}
