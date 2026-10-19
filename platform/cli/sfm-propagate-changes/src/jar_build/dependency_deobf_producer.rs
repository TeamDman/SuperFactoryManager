use super::ArtifactPlan;
use super::ContentHash;
use super::MavenCoordinate;
use super::remapped_dependency_output_path;
use super::specialsource_dependency_output_path;
use std::collections::BTreeMap;
use std::path::Path;
use std::path::PathBuf;

/// One physical remap, retaining every original configuration's output binding.
#[derive(Clone, Debug)]
pub(super) struct DependencyDeobfProducer {
    pub(super) dependency_indexes: Vec<usize>,
    pub(super) coordinate: MavenCoordinate,
    pub(super) artifact: ArtifactPlan,
    pub(super) remapped: PathBuf,
    pub(super) specialsource_output: PathBuf,
    mapping_hash: ContentHash,
}

impl DependencyDeobfProducer {
    pub(super) fn new(
        dependency_index: usize,
        coordinate: MavenCoordinate,
        artifact: ArtifactPlan,
        output: &Path,
        mapping_hash: ContentHash,
    ) -> eyre::Result<Self> {
        let input_hash = super::resolved_artifact_hash(&artifact)?;
        Ok(Self {
            dependency_indexes: vec![dependency_index],
            remapped: remapped_dependency_output_path(
                output,
                &input_hash,
                &mapping_hash,
                &coordinate,
            ),
            specialsource_output: specialsource_dependency_output_path(
                output,
                &input_hash,
                &mapping_hash,
                &coordinate,
            ),
            coordinate,
            artifact,
            mapping_hash,
        })
    }

    /// Group before entering Rayon: a nested read-pool install may run another
    /// dependency on a worker that already holds this output's exclusive lock.
    pub(super) fn select(rows: Vec<Self>) -> eyre::Result<Vec<Self>> {
        let mut selected: Vec<Self> = Vec::new();
        let mut outputs: BTreeMap<PathBuf, usize> = BTreeMap::new();
        for row in rows {
            let key = output_identity(&row.remapped);
            if let Some(&index) = outputs.get(&key) {
                let existing = &mut selected[index];
                eyre::ensure!(
                    existing.same_inputs(&row),
                    "conflicting dependency deobf producers for output {}",
                    row.remapped.display()
                );
                existing.dependency_indexes.extend(row.dependency_indexes);
            } else {
                outputs.insert(key, selected.len());
                selected.push(row);
            }
        }
        Ok(selected)
    }

    pub(super) fn output_bindings(producers: &[Self]) -> Vec<PathBuf> {
        let mut bindings = producers
            .iter()
            .flat_map(|producer| {
                producer
                    .dependency_indexes
                    .iter()
                    .map(|&index| (index, producer.remapped.clone()))
            })
            .collect::<Vec<_>>();
        bindings.sort_by_key(|(index, _)| *index);
        bindings.into_iter().map(|(_, path)| path).collect()
    }

    pub(super) const fn mapping_hash(&self) -> ContentHash {
        self.mapping_hash
    }

    fn same_inputs(&self, other: &Self) -> bool {
        self.coordinate == other.coordinate
            && self.artifact.sha1 == other.artifact.sha1
            && self.artifact.provenance == other.artifact.provenance
            && self.artifact.cache_path == other.artifact.cache_path
            && self.mapping_hash == other.mapping_hash
            && self.remapped == other.remapped
            && self.specialsource_output == other.specialsource_output
    }
}

fn output_identity(path: &Path) -> PathBuf {
    #[cfg(windows)]
    let path = PathBuf::from(path.as_os_str().to_ascii_lowercase());
    #[cfg(not(windows))]
    let path = path.to_path_buf();
    path
}
