use crate::artifact_lock::ArtifactLock;
use crate::cancellation::CancellationToken;
use crate::dependency_inventory::AcquisitionStatus;
use crate::dependency_inventory::DependencyInventory;
use crate::jar_build::hash::ContentHash;
use crate::jar_build::hash::ContentHashAlgorithm;
use crate::jdk::resolve_java;
use crate::payload_fetcher::PayloadFetcher;
use crate::payload_fetcher::write_payload_atomically;
use crate::source_cache::SourceCacheLayout;
use crate::toolchain_lockfile_schema::version::v3::ArtifactV3;
use crate::toolchain_lockfile_schema::version::v3::DecompileSourceDeclarationV3;
use crate::toolchain_lockfile_schema::version::v3::DecompileSourceDerivedChecksV3;
use crate::toolchain_lockfile_schema::version::v3::DecompileSourceProviderV3;
use eyre::Context;
use std::fs;
use std::path::Path;
use std::path::PathBuf;
use std::process::Command;
use std::process::Stdio;
use std::thread;
use std::time::Duration;

pub(crate) const FINGERPRINT_FILE: &str = ".sfm-decompile-fingerprint";
const VINEFLOWER_ARGUMENTS: &[&str] = &["--folder"];
const VINEFLOWER_LIBRARY_POLICY: &str = "no-external-libraries";
const VINEFLOWER_MAPPING_POLICY: &str = "no-external-mappings";
const REQUIRED_JAVA_MAJOR: u32 = 17;

pub(crate) fn configure_decompile_sources(
    inventory: &DependencyInventory,
    component: &crate::toolchain_lockfile_schema::version::v3::DependencyComponentV3,
    decompiler_component: &crate::toolchain_lockfile_schema::version::v3::DependencyComponentV3,
    roots: Vec<String>,
) -> eyre::Result<DecompileSourceProviderV3> {
    let binary = inventory.artifact(component);
    derive_locked_decompile_provider(inventory, binary, decompiler_component, "vineflower", roots)
}

/// Derive a content-addressed decompile provider from artifacts already
/// authenticated by the effective lock. This does not mutate the lockfile or
/// inspect ambient Maven/Gradle caches.
pub(crate) fn derive_locked_decompile_provider(
    inventory: &DependencyInventory,
    binary: &ArtifactV3,
    decompiler_component: &crate::toolchain_lockfile_schema::version::v3::DependencyComponentV3,
    provider_id: &str,
    roots: Vec<String>,
) -> eyre::Result<DecompileSourceProviderV3> {
    let runtime = VineflowerRunner.prepare()?;
    Ok(derive_locked_decompile_provider_with_runtime(
        inventory,
        binary,
        decompiler_component,
        provider_id,
        roots,
        &runtime.runtime_identity,
    ))
}

pub(crate) fn derive_locked_decompile_provider_with_runtime(
    inventory: &DependencyInventory,
    binary: &ArtifactV3,
    decompiler_component: &crate::toolchain_lockfile_schema::version::v3::DependencyComponentV3,
    provider_id: &str,
    roots: Vec<String>,
    runtime_identity: &str,
) -> DecompileSourceProviderV3 {
    let decompiler = inventory.artifact(decompiler_component);
    let fingerprint = decompile_fingerprint(binary.hash, decompiler.hash, runtime_identity);
    DecompileSourceProviderV3 {
        id: provider_id.to_owned(),
        declaration: DecompileSourceDeclarationV3 { roots },
        derived_checks: DecompileSourceDerivedChecksV3 {
            binary_artifact_id: binary.id.clone(),
            decompiler_artifact_id: decompiler.id.clone(),
            tree_cache_path: SourceCacheLayout::decompiled(binary.hash, &fingerprint),
            fingerprint,
        },
    }
}

pub(crate) fn acquire_locked_decompiled_sources(
    inventory: &DependencyInventory,
    provider: &DecompileSourceProviderV3,
    cancellation_token: &CancellationToken,
    fetcher: &dyn PayloadFetcher,
) -> eyre::Result<()> {
    acquire_with_runner(
        inventory,
        provider,
        cancellation_token,
        fetcher,
        &VineflowerRunner,
    )
}

fn acquire_with_runner(
    inventory: &DependencyInventory,
    provider: &DecompileSourceProviderV3,
    cancellation_token: &CancellationToken,
    fetcher: &dyn PayloadFetcher,
    runner: &dyn DecompileRunner,
) -> eyre::Result<()> {
    cancellation_token.bail_if_cancelled()?;
    let binary = inventory
        .artifact_by_id(&provider.derived_checks.binary_artifact_id)
        .ok_or_else(|| {
            eyre::eyre!(
                "Unknown decompile binary artifact '{}'.",
                provider.derived_checks.binary_artifact_id
            )
        })?;
    let decompiler = inventory
        .artifact_by_id(&provider.derived_checks.decompiler_artifact_id)
        .ok_or_else(|| {
            eyre::eyre!(
                "Unknown decompiler artifact '{}'.",
                provider.derived_checks.decompiler_artifact_id
            )
        })?;
    let tree = inventory.local_path(&provider.derived_checks.tree_cache_path);
    let _lock = acquire_source_lock(&tree)?;
    let prepared = runner.prepare()?;
    let fingerprint =
        decompile_fingerprint(binary.hash, decompiler.hash, &prepared.runtime_identity);
    if fingerprint != provider.derived_checks.fingerprint {
        eyre::bail!(
            "Locked decompile fingerprint mismatch for '{}': expected {}, got {}. Use the Java runtime and options recorded when this provider was configured.",
            provider.id,
            provider.derived_checks.fingerprint,
            fingerprint
        );
    }
    if completed_tree_matches(&tree, &fingerprint, &provider.declaration.roots) {
        return Ok(());
    }

    let binary_path = acquire_locked_artifact(inventory, binary, cancellation_token, fetcher)?;
    let decompiler_path =
        acquire_locked_artifact(inventory, decompiler, cancellation_token, fetcher)?;
    cancellation_token.bail_if_cancelled()?;
    materialize_tree(
        runner,
        &prepared,
        MaterializeRequest {
            decompiler: &decompiler_path,
            binary: &binary_path,
            output: &tree,
            fingerprint: &fingerprint,
            roots: &provider.declaration.roots,
            cancellation_token,
        },
    )
}

fn acquire_locked_artifact(
    inventory: &DependencyInventory,
    artifact: &ArtifactV3,
    cancellation_token: &CancellationToken,
    fetcher: &dyn PayloadFetcher,
) -> eyre::Result<PathBuf> {
    let path = inventory.local_path(&artifact.cache_path);
    if inventory.locked_file_status(&artifact.cache_path, artifact.hash)
        == AcquisitionStatus::Acquired
    {
        return Ok(path);
    }
    let url = artifact.url.as_deref().ok_or_else(|| {
        eyre::eyre!(
            "Locked artifact '{}' has no downloadable URL and is not present in the cache.",
            artifact.id
        )
    })?;
    let bytes = fetcher.fetch(url, cancellation_token)?.ok_or_else(|| {
        eyre::eyre!(
            "Locked artifact '{}' is no longer available: {url}",
            artifact.id
        )
    })?;
    cancellation_token.bail_if_cancelled()?;
    let actual = ContentHash::from_bytes(&bytes, artifact.hash.algorithm);
    if actual != artifact.hash {
        eyre::bail!(
            "Locked artifact hash mismatch for {}: expected {}, got {}",
            artifact.id,
            artifact.hash,
            actual
        );
    }
    write_payload_atomically(&path, &bytes)?;
    Ok(path)
}

#[derive(Clone, Copy)]
struct MaterializeRequest<'a> {
    decompiler: &'a Path,
    binary: &'a Path,
    output: &'a Path,
    fingerprint: &'a str,
    roots: &'a [String],
    cancellation_token: &'a CancellationToken,
}

fn materialize_tree(
    runner: &dyn DecompileRunner,
    prepared: &PreparedDecompiler,
    request: MaterializeRequest<'_>,
) -> eyre::Result<()> {
    let parent = request.output.parent().ok_or_else(|| {
        eyre::eyre!(
            "Decompiled source tree has no parent: {}",
            request.output.display()
        )
    })?;
    fs::create_dir_all(parent)
        .wrap_err_with(|| format!("Failed to create source cache parent {}", parent.display()))?;
    let temporary = tempfile::Builder::new()
        .prefix(".sfm-decompile-")
        .tempdir_in(parent)
        .wrap_err_with(|| format!("Failed to create staging directory in {}", parent.display()))?;
    let staged_tree = temporary.path().join("tree");
    fs::create_dir(&staged_tree)
        .wrap_err_with(|| format!("Failed to create {}", staged_tree.display()))?;
    runner.run(
        prepared,
        request.decompiler,
        request.binary,
        &staged_tree,
        request.cancellation_token,
    )?;
    request.cancellation_token.bail_if_cancelled()?;
    validate_roots(&staged_tree, request.roots)?;
    fs::write(staged_tree.join(FINGERPRINT_FILE), request.fingerprint).wrap_err_with(|| {
        format!(
            "Failed to write decompile fingerprint in {}",
            staged_tree.display()
        )
    })?;
    if request.output.exists() {
        fs::remove_dir_all(request.output).wrap_err_with(|| {
            format!(
                "Failed to replace decompiled source tree {}",
                request.output.display()
            )
        })?;
    }
    fs::rename(&staged_tree, request.output).wrap_err_with(|| {
        format!(
            "Failed to publish decompiled source tree {} to {}",
            staged_tree.display(),
            request.output.display()
        )
    })?;
    Ok(())
}

pub(crate) fn completed_tree_matches(tree: &Path, fingerprint: &str, roots: &[String]) -> bool {
    if !tree.is_dir() {
        return false;
    }
    let Ok(recorded) = fs::read_to_string(tree.join(FINGERPRINT_FILE)) else {
        return false;
    };
    if recorded != fingerprint {
        return false;
    }
    validate_roots(tree, roots).is_ok()
}

fn validate_roots(tree: &Path, roots: &[String]) -> eyre::Result<()> {
    for root in roots {
        let path = Path::new(root);
        if path.is_absolute()
            || path
                .components()
                .any(|component| !matches!(component, std::path::Component::Normal(_)))
        {
            eyre::bail!("Decompiled source root must be a relative normalized path: {root}");
        }
        let resolved = tree.join(path);
        if !resolved.is_dir() {
            eyre::bail!(
                "Configured decompiled source root does not exist in generated output: {}",
                resolved.display()
            );
        }
    }
    Ok(())
}

fn acquire_source_lock(tree: &Path) -> eyre::Result<ArtifactLock> {
    let file_name = tree.file_name().ok_or_else(|| {
        eyre::eyre!(
            "Decompiled source tree has no file name: {}",
            tree.display()
        )
    })?;
    ArtifactLock::acquire(
        tree.with_file_name(format!("{}.lock", file_name.to_string_lossy())),
        tree.display().to_string(),
    )
}

fn decompile_fingerprint(
    binary_hash: ContentHash,
    decompiler_hash: ContentHash,
    runtime_identity: &str,
) -> String {
    ContentHash::from_bytes(
        format!(
            "format=sfm-decompile-v1\nbinary={binary_hash}\nmappings={VINEFLOWER_MAPPING_POLICY}\ndecompiler={decompiler_hash}\nruntime={runtime_identity}\narguments={}\nlibraries={VINEFLOWER_LIBRARY_POLICY}\n",
            VINEFLOWER_ARGUMENTS.join("\u{1f}"),
        )
        .as_bytes(),
        ContentHashAlgorithm::Blake3,
    )
    .to_string()
}

struct PreparedDecompiler {
    executable: PathBuf,
    runtime_identity: String,
}

trait DecompileRunner {
    fn prepare(&self) -> eyre::Result<PreparedDecompiler>;

    fn run(
        &self,
        prepared: &PreparedDecompiler,
        decompiler: &Path,
        binary: &Path,
        output: &Path,
        cancellation_token: &CancellationToken,
    ) -> eyre::Result<()>;
}

struct VineflowerRunner;

impl DecompileRunner for VineflowerRunner {
    fn prepare(&self) -> eyre::Result<PreparedDecompiler> {
        let java = resolve_java(None, REQUIRED_JAVA_MAJOR)?;
        Ok(PreparedDecompiler {
            executable: java.executable,
            runtime_identity: java.version_output,
        })
    }

    fn run(
        &self,
        prepared: &PreparedDecompiler,
        decompiler: &Path,
        binary: &Path,
        output: &Path,
        cancellation_token: &CancellationToken,
    ) -> eyre::Result<()> {
        cancellation_token.bail_if_cancelled()?;
        let mut child = Command::new(&prepared.executable)
            .arg("-jar")
            .arg(decompiler)
            .args(VINEFLOWER_ARGUMENTS)
            .arg(binary)
            .arg(output)
            .stdout(Stdio::null())
            .stderr(Stdio::piped())
            .spawn()
            .wrap_err_with(|| {
                format!(
                    "Failed to launch Vineflower using {}",
                    prepared.executable.display()
                )
            })?;
        let status = loop {
            if let Some(status) = child.try_wait().wrap_err("Failed to poll Vineflower")? {
                break status;
            }
            if cancellation_token.is_cancelled() {
                let _ = child.kill();
                let _ = child.wait();
                cancellation_token.bail_if_cancelled()?;
            }
            thread::sleep(Duration::from_millis(250));
        };
        let output_result = child
            .wait_with_output()
            .wrap_err("Failed to collect Vineflower output")?;
        if !status.success() {
            eyre::bail!(
                "Vineflower exited with {} while decompiling {}: {}",
                output_result.status,
                binary.display(),
                String::from_utf8_lossy(&output_result.stderr).trim()
            );
        }
        cancellation_token.bail_if_cancelled()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::branch_targets::BranchName;
    use crate::branch_targets::WorktreePath;
    use crate::branch_targets::WorktreeTarget;
    use crate::paths::CacheHome;
    use crate::toolchain_lockfile_schema::read_current;
    use crate::toolchain_lockfile_schema::version::v3::ArtifactProvenanceV3;
    use std::cell::Cell;

    struct NeverFetcher;

    impl PayloadFetcher for NeverFetcher {
        fn fetch(
            &self,
            _url: &str,
            _cancellation_token: &CancellationToken,
        ) -> eyre::Result<Option<Vec<u8>>> {
            panic!("validated decompiled sources must not fetch artifacts")
        }
    }

    struct FixtureRunner {
        runs: Cell<u32>,
    }

    impl DecompileRunner for FixtureRunner {
        fn prepare(&self) -> eyre::Result<PreparedDecompiler> {
            Ok(PreparedDecompiler {
                executable: PathBuf::from("fixture-java"),
                runtime_identity: "fixture Java 17".to_owned(),
            })
        }

        fn run(
            &self,
            _prepared: &PreparedDecompiler,
            _decompiler: &Path,
            _binary: &Path,
            output: &Path,
            _cancellation_token: &CancellationToken,
        ) -> eyre::Result<()> {
            self.runs.set(self.runs.get() + 1);
            let source = output.join("src/Example.java");
            fs::create_dir_all(source.parent().expect("source parent"))?;
            fs::write(source, "class Example {}")?;
            Ok(())
        }
    }

    #[test]
    fn fingerprint_includes_every_deterministic_input() {
        let binary = ContentHash::from_bytes(b"binary", ContentHashAlgorithm::Blake3);
        let decompiler = ContentHash::from_bytes(b"decompiler", ContentHashAlgorithm::Blake3);
        let first = decompile_fingerprint(binary, decompiler, "Java 17.0.1");
        assert_eq!(
            first,
            decompile_fingerprint(binary, decompiler, "Java 17.0.1")
        );
        assert_ne!(
            first,
            decompile_fingerprint(binary, decompiler, "Java 17.0.2")
        );
        assert_ne!(
            first,
            decompile_fingerprint(
                ContentHash::from_bytes(b"other", ContentHashAlgorithm::Blake3),
                decompiler,
                "Java 17.0.1"
            )
        );
    }

    #[test]
    fn completed_decompilation_is_idempotent_without_fetching_or_rerunning() {
        let cache = tempfile::tempdir().expect("temporary cache");
        let (mut inventory, mut provider) = fixture(CacheHome(cache.path().to_path_buf()));
        prepare_artifacts(&mut inventory);
        let runner = FixtureRunner { runs: Cell::new(0) };
        provider.derived_checks.fingerprint = decompile_fingerprint(
            inventory.artifact_by_id("binary").unwrap().hash,
            inventory.artifact_by_id("decompiler").unwrap().hash,
            "fixture Java 17",
        );

        acquire_with_runner(
            &inventory,
            &provider,
            &CancellationToken::new(),
            &NeverFetcher,
            &runner,
        )
        .expect("first materialization");
        acquire_with_runner(
            &inventory,
            &provider,
            &CancellationToken::new(),
            &NeverFetcher,
            &runner,
        )
        .expect("second materialization reuses completed tree");

        let tree = inventory.local_path(&provider.derived_checks.tree_cache_path);
        assert_eq!(runner.runs.get(), 1);
        assert!(tree.join("src/Example.java").is_file());
        assert_eq!(
            fs::read_to_string(tree.join(FINGERPRINT_FILE)).unwrap(),
            provider.derived_checks.fingerprint
        );
    }

    #[test]
    fn rejects_a_runtime_that_does_not_match_the_locked_fingerprint() {
        let cache = tempfile::tempdir().expect("temporary cache");
        let (mut inventory, provider) = fixture(CacheHome(cache.path().to_path_buf()));
        prepare_artifacts(&mut inventory);
        let runner = FixtureRunner { runs: Cell::new(0) };

        let error = acquire_with_runner(
            &inventory,
            &provider,
            &CancellationToken::new(),
            &NeverFetcher,
            &runner,
        )
        .expect_err("unlocked runtime must fail");

        assert!(error.to_string().contains("fingerprint mismatch"));
        assert_eq!(runner.runs.get(), 0);
    }

    fn prepare_artifacts(inventory: &mut DependencyInventory) {
        let mut writes = Vec::new();
        for artifact in &mut inventory.lockfile.artifacts {
            if artifact.id == "binary" || artifact.id == "decompiler" {
                let bytes = artifact.id.as_bytes();
                artifact.hash = ContentHash::from_bytes(bytes, ContentHashAlgorithm::Blake3);
                writes.push((artifact.cache_path.clone(), bytes.to_vec()));
            }
        }
        for (cache_path, bytes) in writes {
            let path = inventory.local_path(&cache_path);
            fs::create_dir_all(path.parent().expect("artifact parent")).unwrap();
            fs::write(path, bytes).unwrap();
        }
    }

    fn fixture(cache_home: CacheHome) -> (DependencyInventory, DecompileSourceProviderV3) {
        let input = include_str!("../../../minecraft/sfm-toolchain.lock.json");
        let mut lockfile = read_current(input).expect("v3 fixture");
        lockfile
            .artifacts
            .retain(|artifact| artifact.id != "binary" && artifact.id != "decompiler");
        for (id, cache_path) in [
            ("binary", "$sfm-cache/decompile-fixture/binary.jar"),
            ("decompiler", "$sfm-cache/decompile-fixture/decompiler.jar"),
        ] {
            lockfile.artifacts.push(ArtifactV3 {
                id: id.to_owned(),
                owner: None,
                purposes: Vec::new(),
                coordinate: None,
                repository_id: None,
                url: Some(format!("https://example.invalid/{id}.jar")),
                hash: ContentHash::from_bytes(b"placeholder", ContentHashAlgorithm::Blake3),
                cache_path: PathBuf::from(cache_path),
                provenance: ArtifactProvenanceV3::RemoteMaven,
                source_git: None,
                source_build: None,
                weak: None,
            });
        }
        let inventory = DependencyInventory {
            target: WorktreeTarget {
                branch: BranchName::from("1.19.2"),
                worktree_path: WorktreePath::from(PathBuf::from("fixture")),
                core: true,
                mc_version: None,
            },
            lockfile_path: PathBuf::from("fixture/sfm-toolchain.lock.json"),
            cache_home,
            original_input: input.to_owned(),
            lockfile,
        };
        let provider = DecompileSourceProviderV3 {
            id: "vineflower".to_owned(),
            declaration: DecompileSourceDeclarationV3 {
                roots: vec!["src".to_owned()],
            },
            derived_checks: DecompileSourceDerivedChecksV3 {
                binary_artifact_id: "binary".to_owned(),
                decompiler_artifact_id: "decompiler".to_owned(),
                fingerprint: "deliberately-wrong".to_owned(),
                tree_cache_path: PathBuf::from("$sfm-cache/decompile-fixture/tree"),
            },
        };
        (inventory, provider)
    }
}
