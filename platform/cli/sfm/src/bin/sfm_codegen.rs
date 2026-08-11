#[cfg(feature = "codegen")]
fn main() -> eyre::Result<()> {
    use std::collections::BTreeMap;
    use std::path::PathBuf;

    const UPSTREAM_PACKAGE: &str = "org.facet.vox.generated";
    const SFM_PACKAGE: &str = "ca.teamdman.sfm.client.control.generated";

    let mode = std::env::args()
        .nth(1)
        .unwrap_or_else(|| "--check".to_owned());
    eyre::ensure!(
        mode == "--check" || mode == "--write",
        "expected --check or --write"
    );

    let crate_dir = PathBuf::from(env!("CARGO_MANIFEST_DIR"));
    let repository_root = crate_dir
        .ancestors()
        .nth(3)
        .ok_or_else(|| eyre::eyre!("could not resolve SFM repository root"))?;
    let java_source_root = repository_root.join("platform/minecraft/src/main/java");
    let output_dir = java_source_root.join("ca/teamdman/sfm/client/control/generated");
    let legacy_output_dir = java_source_root.join("org/facet/vox/generated");

    let generated = vox_codegen::targets::java::generate_service(
        sfm::protocol::sfm_control_service_descriptor(),
    )?
    .into_iter()
    .map(|file| {
        let source = file.source.replace(
            &format!("package {UPSTREAM_PACKAGE};"),
            &format!("package {SFM_PACKAGE};"),
        );
        (file.relative_path, source)
    })
    .collect::<BTreeMap<_, _>>();

    if mode == "--write" {
        std::fs::create_dir_all(&output_dir)?;
        for (name, source) in &generated {
            let path = output_dir.join(name);
            if std::fs::read_to_string(&path).ok().as_ref() != Some(source) {
                std::fs::write(path, source)?;
            }
            let legacy_path = legacy_output_dir.join(name);
            if legacy_path.exists() {
                std::fs::remove_file(legacy_path)?;
            }
        }
        if legacy_output_dir.exists() && std::fs::read_dir(&legacy_output_dir)?.next().is_none() {
            std::fs::remove_dir(&legacy_output_dir)?;
        }
        return Ok(());
    }

    for (name, expected) in generated {
        let legacy_path = legacy_output_dir.join(&name);
        eyre::ensure!(
            !legacy_path.exists(),
            "legacy split-package Java file {} must be removed",
            legacy_path.display()
        );
        let path = output_dir.join(&name);
        let actual = std::fs::read_to_string(&path).map_err(|error| {
            eyre::eyre!("generated Java file {} is missing: {error}", path.display())
        })?;
        eyre::ensure!(
            actual == expected,
            "generated Java file {} is stale",
            path.display()
        );
    }
    Ok(())
}

#[cfg(not(feature = "codegen"))]
fn main() {
    unreachable!("sfm-codegen requires the codegen feature")
}
