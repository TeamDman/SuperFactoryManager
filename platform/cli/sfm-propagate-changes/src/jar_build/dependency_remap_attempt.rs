//! Fresh remap workspace; it never imports prior project-local existence hits.
use super::forge_preparation_cache::make_held_directory;
use crate::source_projection::core_input_leases::DirectoryLeases;
use std::path::Path;
use std::path::PathBuf;

pub(super) struct DependencyRemapAttempt {
    pub(super) outputs: Vec<(&'static str, PathBuf)>,
    _ancestry: DirectoryLeases,
}

impl DependencyRemapAttempt {
    pub(super) fn create(base: &Path, key: &str) -> eyre::Result<Option<Self>> {
        let work = base.join("work");
        let Some((_verified_work, _work_ancestry)) = make_held_directory(&work)? else {
            return Ok(None);
        };
        // This diagnostic/output path is not the Java cwd: the existing launch
        // adapter transports it through its bounded short retained scratch.
        let path = tempfile::Builder::new()
            .prefix(&format!("remap-{key}-"))
            .tempdir_in(&work)?
            .keep();
        let Some((_verified_path, ancestry)) = make_held_directory(&path)? else {
            return Ok(None);
        };
        Ok(Some(Self {
            outputs: vec![
                ("specialsource.jar", path.join("specialsource.jar")),
                ("remapped.jar", path.join("remapped.jar")),
            ],
            _ancestry: ancestry,
        }))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn dependency_remap_workspace_keeps_normal_spelling_and_full_key_identity() -> eyre::Result<()>
    {
        let root = tempfile::tempdir()?;
        let key = "a".repeat(64);
        let base = root.path().join("selected-store");
        let (_verified_base, _base_ancestry) = make_held_directory(&base)?.unwrap();
        let attempt = DependencyRemapAttempt::create(&base, &key)?.unwrap();
        for (_, path) in &attempt.outputs {
            crate::jar_build::engine::require_specialsource_normal_absolute_path(path)?;
            assert!(
                path.parent()
                    .unwrap()
                    .file_name()
                    .unwrap()
                    .to_str()
                    .unwrap()
                    .contains(&key)
            );
        }
        let input = root.path().join("input.jar");
        let mapping = root.path().join("mapping.tsrg");
        let args = crate::jar_build::engine::specialsource_dependency_arguments(
            &input,
            &attempt.outputs[0].1,
            &mapping,
        )?;
        assert_eq!(args[3], attempt.outputs[0].1.to_str().unwrap());
        let scratch = crate::jar_build::engine::RetainedSpecialSourceScratch::create()?;
        assert!(
            crate::jar_build::engine::specialsource_path_utf16_units(&scratch.path)?
                < crate::jar_build::engine::SPECIALSOURCE_LAUNCH_CWD_LIMIT
        );
        assert_ne!(scratch.path, attempt.outputs[0].1.parent().unwrap());
        assert!(!attempt.outputs.iter().any(|(_, path)| path.exists()));
        Ok(())
    }
}
