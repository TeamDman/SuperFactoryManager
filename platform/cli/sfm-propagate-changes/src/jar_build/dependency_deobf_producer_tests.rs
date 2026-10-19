use super::ArtifactId;
use super::ArtifactPlan;
use super::ArtifactProvenance;
use super::ArtifactPurpose;
use super::ArtifactSource;
use super::ContentHash;
use super::ContentHashAlgorithm;
use super::MavenCoordinate;
use super::dependency_deobf_producer::DependencyDeobfProducer;
use crate::artifact_lock::ArtifactLock;
use crate::artifact_lock::ArtifactLockWaitPolicy;
use std::fs;
use std::path::Path;
use std::sync::atomic::AtomicBool;
use std::sync::atomic::Ordering;
use std::thread;
use std::time::Duration;
use std::time::Instant;

fn producer(root: &Path, index: usize, name: &str) -> eyre::Result<DependencyDeobfProducer> {
    let coordinate = MavenCoordinate::parse(&format!("fixture:{name}:1"))?;
    let hash = ContentHash::from_bytes(name.as_bytes(), ContentHashAlgorithm::Blake3);
    DependencyDeobfProducer::new(
        index,
        coordinate.clone(),
        ArtifactPlan {
            id: ArtifactId::from(format!("dependency-{index}")),
            coordinate: Some(coordinate.to_string()),
            repository: Some("fixture".to_owned()),
            url: None,
            cache_path: root.join(format!("input-{name}.jar")),
            sha1: Some(hash),
            downloaded: false,
            required_for: ArtifactPurpose::from(format!("configuration-{index}")),
            provenance: ArtifactProvenance {
                schema_version: 1,
                source: ArtifactSource::RemoteMaven,
                coordinate: Some(coordinate.to_string()),
                repository: Some("fixture".to_owned()),
                url: None,
                original_path: None,
                source_relative_path: None,
                source_git: None,
                source_build: None,
                hash,
            },
        },
        root,
        ContentHash::from_bytes(b"mappings", ContentHashAlgorithm::Blake3),
    )
}

#[test]
fn duplicate_configuration_rows_keep_every_ordered_output_binding() -> eyre::Result<()> {
    let root = tempfile::tempdir()?;
    let original = vec![
        producer(root.path(), 0, "shared")?,
        producer(root.path(), 1, "independent")?,
        producer(root.path(), 2, "shared")?,
    ];
    let expected = original
        .iter()
        .map(|row| row.remapped.clone())
        .collect::<Vec<_>>();
    let selected = DependencyDeobfProducer::select(original)?;
    assert_eq!(selected.len(), 2);
    assert_eq!(selected[0].dependency_indexes, vec![0, 2]);
    assert_eq!(selected[1].dependency_indexes, vec![1]);
    assert_eq!(
        DependencyDeobfProducer::output_bindings(&selected),
        expected
    );
    Ok(())
}

#[test]
fn same_output_refuses_different_full_pins_and_provenance() -> eyre::Result<()> {
    let root = tempfile::tempdir()?;
    let first = producer(root.path(), 0, "shared")?;
    // A filename's shortened digest is not sufficient producer authority.
    let mut changed_hash = first.clone();
    let mut hash = changed_hash.artifact.sha1.expect("fixture pin");
    hash.value[19] ^= 1;
    changed_hash.artifact.sha1 = Some(hash);
    let mut changed_coordinate = first.clone();
    changed_coordinate.coordinate = MavenCoordinate::parse("another:shared:1")?;
    let mut changed_provenance = first.clone();
    changed_provenance.artifact.provenance.repository = Some("other".to_owned());
    let mut changed_cache_path = first.clone();
    changed_cache_path.artifact.cache_path = root.path().join("other-input.jar");
    for conflict in [
        changed_hash,
        changed_coordinate,
        changed_provenance,
        changed_cache_path,
    ] {
        let error = DependencyDeobfProducer::select(vec![first.clone(), conflict]).unwrap_err();
        assert!(
            error
                .to_string()
                .contains("conflicting dependency deobf producers")
        );
    }
    Ok(())
}

#[test]
fn mapping_hash_prefix_collision_refuses_before_scheduling() -> eyre::Result<()> {
    let root = tempfile::tempdir()?;
    let first = producer(root.path(), 0, "shared")?;
    let mut other_mapping = ContentHash::from_bytes(b"mappings", ContentHashAlgorithm::Blake3);
    other_mapping.value[19] ^= 1;
    let other = DependencyDeobfProducer::new(
        1,
        first.coordinate.clone(),
        first.artifact.clone(),
        root.path(),
        other_mapping,
    )?;
    assert_eq!(first.remapped, other.remapped);
    assert!(DependencyDeobfProducer::select(vec![first, other]).is_err());
    Ok(())
}

/// Force the real cross-pool scheduling edge, rather than hoping the OS/Rayon
/// timing reproduces it: the first read waits for the queued second job.
fn run_one_worker_fixture(rows: &[DependencyDeobfProducer]) -> eyre::Result<Vec<eyre::Result<()>>> {
    let producers = rayon::ThreadPoolBuilder::new().num_threads(1).build()?;
    let reads = rayon::ThreadPoolBuilder::new().num_threads(1).build()?;
    let second_started = AtomicBool::new(false);
    let policy = ArtifactLockWaitPolicy::new(Duration::from_millis(2), Duration::from_secs(1))
        .with_max_wait(Duration::from_millis(50));
    let run = |row: &DependencyDeobfProducer| -> eyre::Result<()> {
        let lock_path = row.remapped.with_extension("jar.lock");
        let _lock = ArtifactLock::acquire_with_policy(&lock_path, "fixture remap", &policy)?;
        fs::write(&row.remapped, row.coordinate.to_string())?;
        Ok(())
    };
    let (first, rest) = producers.install(|| {
        rayon::join(
            || -> eyre::Result<()> {
                let row = &rows[0];
                let lock_path = row.remapped.with_extension("jar.lock");
                let _lock = ArtifactLock::acquire_with_policy(&lock_path, "first remap", &policy)?;
                reads.install(|| -> eyre::Result<()> {
                    let deadline = Instant::now() + Duration::from_secs(2);
                    while !second_started.load(Ordering::Acquire) {
                        eyre::ensure!(Instant::now() < deadline, "queued producer did not start");
                        thread::sleep(Duration::from_millis(1));
                    }
                    Ok(())
                })?;
                fs::write(&row.remapped, row.coordinate.to_string())?;
                Ok(())
            },
            || {
                second_started.store(true, Ordering::Release);
                rows[1..].iter().map(run).collect::<Vec<_>>()
            },
        )
    });
    Ok(std::iter::once(first).chain(rest).collect())
}

#[test]
fn duplicate_remap_cross_pool_cycle_is_bounded_and_selected_jobs_complete() -> eyre::Result<()> {
    let before = tempfile::tempdir()?;
    let unselected = vec![
        producer(before.path(), 0, "shared")?,
        producer(before.path(), 1, "shared")?,
        producer(before.path(), 2, "independent")?,
    ];
    let results = run_one_worker_fixture(&unselected)?;
    assert!(results[0].is_ok());
    let timeout = results[1].as_ref().unwrap_err().to_string();
    assert!(timeout.contains("Timed out"), "{timeout}");
    assert!(results[2].is_ok());

    let after = tempfile::tempdir()?;
    let selected = DependencyDeobfProducer::select(vec![
        producer(after.path(), 0, "shared")?,
        producer(after.path(), 1, "shared")?,
        producer(after.path(), 2, "independent")?,
    ])?;
    let results = run_one_worker_fixture(&selected)?;
    assert_eq!(results.len(), 2);
    for result in results {
        result?;
    }
    let bindings = DependencyDeobfProducer::output_bindings(&selected);
    assert_eq!(bindings.len(), 3);
    assert_eq!(bindings[0], bindings[1]);
    assert_ne!(bindings[0], bindings[2]);
    for path in bindings {
        assert!(path.is_file());
    }
    Ok(())
}
