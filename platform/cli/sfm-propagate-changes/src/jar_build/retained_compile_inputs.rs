//! Private retained project bytes for an all-hit-only compile attempt.
//!
//! This capability does not authorize any compiler, generator or host launch.
//! Misses must leave it before re-entering the ordinary strict live-source path.
#[path = "retained_compile_entries.rs"]
mod entries;

use crate::source_projection::frozen_recipe_project::FrozenRecipeProject;
use entries::RetainedCompileEntries;
use eyre::Result;
use std::collections::BTreeMap;
use std::path::Component;
use std::path::Path;
use std::path::PathBuf;
use std::sync::Arc;

#[derive(Clone, Debug)]
pub(super) struct RetainedCompileInputs {
    project: Arc<FrozenRecipeProject>,
}

impl RetainedCompileInputs {
    pub(super) fn admit(project: Arc<FrozenRecipeProject>) -> Result<Self> {
        project.recheck(false)?;
        Ok(Self { project })
    }

    pub(super) fn project(&self) -> &FrozenRecipeProject {
        &self.project
    }

    pub(super) fn recheck(&self) -> Result<()> {
        self.project.recheck_retained_authority()
    }

    /// Only exact selected outputs have bytes. Unknown paths never cause I/O.
    pub(super) fn bytes_at(&self, path: &Path) -> Result<Option<&[u8]>> {
        self.reject_lexical_aliases(path)?;
        let Some(relative) = self.relative_output(path) else {
            return Ok(None);
        };
        if !self
            .project
            .project()
            .receipt()
            .files
            .contains_key(&relative)
        {
            return Ok(None);
        }
        Ok(Some(
            self.project
                .project()
                .selected_input(&relative)?
                .output_bytes(),
        ))
    }

    pub(super) fn properties(&self) -> Result<BTreeMap<String, String>> {
        let input = self.project.project().selected_input("gradle.properties")?;
        let content = std::str::from_utf8(input.output_bytes())?;
        let mut properties = BTreeMap::new();
        for line in content.lines().map(str::trim) {
            if line.is_empty() || line.starts_with('#') {
                continue;
            }
            if let Some((key, value)) = line.split_once('=') {
                properties.insert(key.trim().to_owned(), value.trim().to_owned());
            }
        }
        Ok(properties)
    }

    /// A known source root may be empty. Cache/output roots return `None`.
    pub(super) fn paths_under(&self, root: &Path) -> Result<Option<Vec<PathBuf>>> {
        Ok(self
            .entries_under(root)?
            .map(|entries| entries.into_iter().map(|(path, _)| path).collect()))
    }

    pub(super) fn selected_paths(&self, root: &Path, suffix: &str) -> Result<Option<Vec<PathBuf>>> {
        Ok(self.paths_under(root)?.map(|paths| {
            paths
                .into_iter()
                .filter(|path| path.extension().and_then(|value| value.to_str()) == Some(suffix))
                .collect()
        }))
    }

    pub(super) fn entries_under(&self, root: &Path) -> Result<Option<RetainedCompileEntries<'_>>> {
        self.reject_lexical_aliases(root)?;
        let Some(relative) = self.relative_output(root) else {
            return Ok(None);
        };
        if relative != "src" && !relative.starts_with("src/") {
            return Ok(None);
        }
        let prefix = format!("{relative}/");
        let mut entries = self
            .project
            .project()
            .retained_selected_inputs()?
            .into_iter()
            .filter(|input| input.output_path().starts_with(&prefix))
            .map(|input| {
                let suffix = input
                    .output_path()
                    .strip_prefix(&prefix)
                    .unwrap_or_default();
                let path = suffix
                    .split('/')
                    .fold(root.to_path_buf(), |path, part| path.join(part));
                (path, input.output_bytes())
            })
            .collect::<Vec<_>>();
        entries.sort_by(|left, right| left.0.cmp(&right.0));
        Ok(Some(entries))
    }

    /// Exactly the existing file/dir fingerprint framing, using retained bytes.
    /// Non-project cache/classpath inputs keep their separate content checks.
    pub(super) fn path_input(&self, path: &Path) -> Result<Option<Vec<u8>>> {
        let Some(bytes) = self.bytes_at(path)? else {
            let Some(relative) = self.relative_output(path) else {
                eyre::ensure!(
                    !dunce::simplified(path)
                        .starts_with(dunce::simplified(self.project.project().project_root())),
                    "unsafe retained project input path"
                );
                return Ok(None);
            };
            if relative != "src"
                && !relative.starts_with("src/")
                && relative != "gradle/source-excludes"
                && !relative.starts_with("gradle/source-excludes/")
            {
                return Ok(None);
            }
            let Some(entries) = self
                .entries_under(path)?
                .filter(|entries| !entries.is_empty())
            else {
                return Err(super::all_hit_cache_miss::AllHitCacheMiss::new(
                    "ambiguous retained source directory identity",
                )
                .into());
            };
            let mut input = Self::path_prefix(path);
            input.extend_from_slice(b"dir\n");
            for (file, bytes) in entries {
                let relative = super::relative_zip_name(path, &file)?;
                input.extend_from_slice(b"entry:");
                input.extend_from_slice(relative.as_bytes());
                input.extend_from_slice(b":");
                input.extend_from_slice(
                    super::ContentHash::from_bytes(bytes, super::ContentHashAlgorithm::Blake3)
                        .to_string()
                        .as_bytes(),
                );
                input.extend_from_slice(b"\n");
            }
            return Ok(Some(input));
        };
        let mut input = Self::path_prefix(path);
        input.extend_from_slice(b"file:");
        input.extend_from_slice(
            super::ContentHash::from_bytes(bytes, super::ContentHashAlgorithm::Blake3)
                .to_string()
                .as_bytes(),
        );
        input.extend_from_slice(b"\n");
        Ok(Some(input))
    }

    fn reject_lexical_aliases(&self, path: &Path) -> Result<()> {
        if path
            .components()
            .any(|part| matches!(part, Component::ParentDir))
        {
            return Err(super::all_hit_cache_miss::AllHitCacheMiss::new(
                "retained input path contains parent traversal",
            )
            .into());
        }
        #[cfg(windows)]
        self.reject_windows_spelling(path)?;
        Ok(())
    }

    #[cfg(windows)]
    fn reject_windows_spelling(&self, path: &Path) -> Result<()> {
        // This first slice deliberately declines unsupported spellings
        // before external classification, rather than approximating the
        // Windows Unicode case-equivalence rules with ASCII folding.
        let ascii_path = path.as_os_str().to_str().is_some_and(str::is_ascii);
        let ascii_root = self
            .project
            .project()
            .project_root()
            .as_os_str()
            .to_str()
            .is_some_and(str::is_ascii);
        if !ascii_path || !ascii_root {
            return Err(super::all_hit_cache_miss::AllHitCacheMiss::new(
                "retained input has an unsupported Windows path spelling",
            )
            .into());
        }
        self.reject_case_aliases(path)
    }

    #[cfg(windows)]
    fn reject_case_aliases(&self, path: &Path) -> Result<()> {
        let root = dunce::simplified(self.project.project().project_root());
        let path = dunce::simplified(path);
        let root_parts = root.components().collect::<Vec<_>>();
        let path_parts = path.components().collect::<Vec<_>>();
        let same_folded_root = path_parts.len() >= root_parts.len()
            && root_parts.iter().zip(&path_parts).all(|(left, right)| {
                left.as_os_str()
                    .to_string_lossy()
                    .eq_ignore_ascii_case(&right.as_os_str().to_string_lossy())
            });
        if same_folded_root && path.strip_prefix(root).is_err() {
            return Err(super::all_hit_cache_miss::AllHitCacheMiss::new(
                "retained input uses a case alias of the project root",
            )
            .into());
        }
        let Some(relative) = self.relative_output(path) else {
            return Ok(());
        };
        if self
            .project
            .project()
            .receipt()
            .files
            .contains_key(&relative)
        {
            return Ok(());
        }
        let prefix = format!("{relative}/");
        let selected_case_alias = self.project.project().receipt().files.keys().any(|output| {
            output.eq_ignore_ascii_case(&relative)
                || (!output.starts_with(&prefix)
                    && output
                        .get(..prefix.len())
                        .is_some_and(|part| part.eq_ignore_ascii_case(&prefix)))
        });
        let lower = relative.to_ascii_lowercase();
        let namespace_case_alias = (lower == "src" || lower.starts_with("src/"))
            && relative != "src"
            && !relative.starts_with("src/")
            || (lower == "gradle/source-excludes" || lower.starts_with("gradle/source-excludes/"))
                && relative != "gradle/source-excludes"
                && !relative.starts_with("gradle/source-excludes/");
        if selected_case_alias || namespace_case_alias {
            return Err(super::all_hit_cache_miss::AllHitCacheMiss::new(
                "retained input uses a case alias of selected source paths",
            )
            .into());
        }
        Ok(())
    }

    fn path_prefix(path: &Path) -> Vec<u8> {
        let mut input = b"path:".to_vec();
        input.extend_from_slice(path.to_string_lossy().replace('\\', "/").as_bytes());
        input.extend_from_slice(b"\n");
        input
    }

    fn relative_output(&self, path: &Path) -> Option<String> {
        if !path.is_absolute() {
            return None;
        }
        let relative = dunce::simplified(path)
            .strip_prefix(dunce::simplified(self.project.project().project_root()))
            .ok()?;
        relative
            .components()
            .map(|component| match component {
                Component::Normal(name) => name.to_str(),
                _ => None,
            })
            .collect::<Option<Vec<_>>>()
            .map(|parts| parts.join("/"))
    }
}

#[cfg(test)]
#[path = "retained_compile_inputs_tests.rs"]
mod tests;
