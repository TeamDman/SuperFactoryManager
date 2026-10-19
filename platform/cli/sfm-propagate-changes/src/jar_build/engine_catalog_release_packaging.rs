// Bounded engine-namespace helper for exact admitted package recipes.
// Only a checked catalog release using an already-admitted exact Jar recipe
// receives released packaging; legacy and named development retain their policy.

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
struct CatalogReleasePackagingPolicy {
    implementation_title: &'static str,
}

fn catalog_release_packaging_policy_for_receipt(
    receipt: &crate::source_projection::frozen_recipe_project::FrozenRecipeProjectReceipt,
    properties: &BTreeMap<String, String>,
) -> eyre::Result<Option<CatalogReleasePackagingPolicy>> {
    use crate::source_projection::projection_catalog::ProjectionEnvironment;

    if receipt.ownership.environment == ProjectionEnvironment::Dev {
        return Ok(None);
    }
    require_named_package_recipe_identity(
        &receipt.ownership.target_id,
        &receipt.ownership.minecraft_version,
        &receipt.released_inputs.recipe_id,
        &receipt.prepared_inputs.source_lock_sha256,
        (
            u32::from(receipt.compiler_release),
            u32::from(receipt.tool_jvm_minimum),
        ),
    )?;
    eyre::ensure!(
        catalog_release_property_values_match(
            &receipt.ownership.minecraft_version,
            &receipt.project_mod_version,
            properties,
        ),
        "catalog release packaging lost its selected Minecraft/mod properties"
    );

    let implementation_title = catalog_release_implementation_title(&receipt.ownership.target_id)?;
    Ok(Some(CatalogReleasePackagingPolicy {
        implementation_title,
    }))
}

fn catalog_release_implementation_title(target: &str) -> eyre::Result<&'static str> {
    // Observed published manifest values, not a directory-name rule. In
    // particular, the 1.21.0 catalog target compiles Minecraft 1.21 but keeps
    // the published sfm-1.21.0 title. Recipe admission remains a separate gate.
    Ok(match target {
        "1.19.2" => "sfm-1.19.2",
        "1.19.4" => "sfm-1.19.4",
        "1.20" => "sfm-1.20",
        "1.20.1" => "sfm-1.20.1",
        "1.20.2" => "sfm-1.20.2",
        "1.20.3" => "sfm-1.20.3",
        "1.20.4" => "sfm-1.20.4",
        "1.21.0" => "sfm-1.21.0",
        "1.21.1" => "sfm-1.21.1",
        "26.1.2" => "sfm-26.1.2",
        _ => eyre::bail!("catalog release packaging has no admitted exact Jar title"),
    })
}

fn checked_catalog_release_packaging_policy(
    plan: &BuildPlan,
) -> eyre::Result<Option<CatalogReleasePackagingPolicy>> {
    use crate::source_projection::projection_catalog::ProjectionEnvironment;

    let Some(project) = plan.named_project() else {
        return Ok(None);
    };
    if project.receipt().ownership.environment == ProjectionEnvironment::Dev {
        return Ok(None);
    }
    // Validate the genuine checked handle, exact loader packaging identity and
    // current owner-derived inputs before any packaging reset or file write.
    validate_named_package_project(project)?;
    eyre::ensure!(
        plan.minecraft_version.as_str() == project.receipt().ownership.minecraft_version,
        "catalog release packaging plan has a mismatched Minecraft version"
    );
    catalog_release_packaging_policy_for_receipt(project.receipt(), &plan.properties)
}

fn project_manifest_implementation_title(context: &ExecutionContext<'_>) -> eyre::Result<String> {
    if let Some(policy) = checked_catalog_release_packaging_policy(context.plan)? {
        return Ok(policy.implementation_title.to_owned());
    }
    // Preserve the exact existing legacy/development fallback, including its
    // default when a named development plan has no worktree-path identity.
    Ok(context
        .plan
        .worktree_path
        .as_deref()
        .and_then(Path::file_name)
        .and_then(|name| name.to_str())
        .map_or_else(|| "sfm".to_string(), |version| format!("sfm-{version}")))
}

fn stage_synthesized_antlr_grammar_resources(
    policy: Option<CatalogReleasePackagingPolicy>,
) -> bool {
    // This excludes only the extra source-grammar staging step. Owned resources
    // from main/generated/javac roots are still copied with their exact bytes.
    policy.is_none()
}

fn catalog_release_property_values_match(
    minecraft_version: &str,
    mod_version: &str,
    properties: &BTreeMap<String, String>,
) -> bool {
    properties.get("minecraft_version").map(String::as_str) == Some(minecraft_version)
        && properties.get("mod_version").map(String::as_str) == Some(mod_version)
}

#[cfg(test)]
mod catalog_release_metadata_tests {
    use super::catalog_release_property_values_match;
    use std::collections::BTreeMap;

    fn properties(version: &str) -> BTreeMap<String, String> {
        BTreeMap::from([
            ("minecraft_version".to_owned(), "1.21".to_owned()),
            ("mod_version".to_owned(), version.to_owned()),
        ])
    }

    #[test]
    fn original_release_metadata_remains_admitted() {
        assert!(catalog_release_property_values_match(
            "1.21",
            "4.34.0",
            &properties("4.34.0")
        ));
    }

    #[test]
    fn new_release_metadata_is_selected_explicitly() {
        assert!(catalog_release_property_values_match(
            "1.21",
            "4.35.0",
            &properties("4.35.0")
        ));
        assert!(!catalog_release_property_values_match(
            "1.21",
            "4.35.0",
            &properties("4.34.0")
        ));
    }

    #[test]
    fn stale_missing_or_empty_version_is_rejected() {
        for version in ["4.34.0", "4.35.0"] {
            let mut values = properties(version);
            values.remove("mod_version");
            assert!(!catalog_release_property_values_match(
                "1.21", version, &values
            ));
            assert!(!catalog_release_property_values_match(
                "1.21",
                version,
                &properties("")
            ));
            assert!(!catalog_release_property_values_match(
                "1.21",
                version,
                &properties("4.36.0")
            ));
        }
    }

    #[test]
    fn upstream_minecraft_value_is_not_inferred_from_target_alias() {
        assert!(!catalog_release_property_values_match(
            "1.21.0",
            "4.35.0",
            &properties("4.35.0")
        ));
    }
}
