#![cfg(any(windows, target_os = "linux"))]

use super::*;

fn fixture() -> Result<(tempfile::TempDir, PathBuf, PathBuf, PathBuf)> {
    let temporary = tempfile::tempdir()?;
    let base = dunce::canonicalize(temporary.path())?;
    let repository = base.join("repository");
    let project = repository.join("source-project");
    let java = base.join("sdk");
    fs::create_dir(&repository)?;
    fs::create_dir(&project)?;
    fs::create_dir(&java)?;
    Ok((temporary, repository, project, java))
}

fn cache_path(repository: &Path) -> PathBuf {
    repository.parent().unwrap().join("native-cache")
}

#[test]
fn native_cache_preflight_has_no_effects_and_allows_future_source_project() -> Result<()> {
    let (_temporary, repository, _, java) = fixture()?;
    let source = repository.join("not-generated/nested-projection");
    let cache = cache_path(&repository);
    assert_eq!(
        NativeCacheRoot::preflight(&cache, &repository, &source, &java)?,
        cache
    );
    assert!(!cache.exists());
    assert!(!repository.join("not-generated").exists());
    Ok(())
}

#[test]
fn native_cache_rejects_unsafe_spelling_before_any_creation() -> Result<()> {
    let (_temporary, repository, source, java) = fixture()?;
    let parent = repository.parent().unwrap();
    for selected in [
        PathBuf::from("relative-native-cache"),
        parent.join("missing-parent/cache"),
        parent.join("./native-cache"),
        parent.join("child/../native-cache"),
        parent.join("native-cache."),
        parent.join("native-cache "),
        parent.join("native\ncache"),
        parent.join("NUL"),
        parent.join("con.txt"),
        parent.join("COM1.data"),
        parent.join("LPT²"),
        parent.join("cache:alternate"),
    ] {
        assert!(NativeCacheRoot::preflight(&selected, &repository, &source, &java).is_err());
        assert!(
            NativeCacheRoot::acquire(
                &selected,
                &repository,
                &source,
                &java,
                &CancellationToken::new()
            )
            .is_err()
        );
    }
    assert!(!parent.join("native-cache").exists());
    assert!(!parent.join("missing-parent").exists());
    assert!(!parent.join("child").exists());
    Ok(())
}

#[cfg(windows)]
#[test]
fn native_cache_rejects_filesystem_root_verbatim_and_device_aliases() -> Result<()> {
    let (_temporary, repository, source, java) = fixture()?;
    let cache = cache_path(&repository);
    for selected in [
        repository.ancestors().last().unwrap().to_owned(),
        PathBuf::from(format!(r"\\?\{}", cache.display())),
        PathBuf::from(r"\\.\C:\native-cache"),
    ] {
        assert!(NativeCacheRoot::preflight(&selected, &repository, &source, &java).is_err());
    }
    assert!(!cache.exists());
    Ok(())
}

#[test]
fn native_cache_rejects_repository_ancestor_and_project_sdk_overlap() -> Result<()> {
    let (_temporary, repository, source, java) = fixture()?;
    for selected in [
        repository.clone(),
        repository.parent().unwrap().to_owned(),
        source.clone(),
        source.join("child"),
        java.clone(),
        java.join("child"),
    ] {
        assert!(NativeCacheRoot::preflight(&selected, &repository, &source, &java).is_err());
        assert!(!selected.join(MARKER).exists());
    }
    let future_source = repository.join("future/projection");
    let future_parent = repository.join("future");
    assert!(
        NativeCacheRoot::preflight(&future_parent, &repository, &future_source, &java).is_err()
    );
    assert!(!future_parent.exists());
    // A separate derived directory under the repository remains a valid choice.
    let sibling = repository.join("separate-derived-cache");
    assert_eq!(
        NativeCacheRoot::preflight(&sibling, &repository, &source, &java)?,
        sibling
    );
    Ok(())
}

#[test]
fn native_cache_never_claims_an_existing_unmarked_user_directory() -> Result<()> {
    let (_temporary, repository, source, java) = fixture()?;
    let cache = cache_path(&repository);
    fs::create_dir(&cache)?;
    fs::write(
        cache.join("user-evidence.bin"),
        b"preserved arbitrary user bytes",
    )?;
    assert!(NativeCacheRoot::preflight(&cache, &repository, &source, &java).is_err());
    let started = Instant::now();
    assert!(
        NativeCacheRoot::acquire(
            &cache,
            &repository,
            &source,
            &java,
            &CancellationToken::new()
        )
        .is_err()
    );
    assert!(started.elapsed() < Duration::from_secs(30));
    assert_eq!(
        fs::read(cache.join("user-evidence.bin"))?,
        b"preserved arbitrary user bytes"
    );
    assert!(!cache.join(MARKER).exists());
    assert_eq!(fs::read_dir(&cache)?.count(), 1);
    Ok(())
}

#[test]
fn native_cache_new_marker_is_exact_and_an_owned_root_can_be_reused() -> Result<()> {
    let (temporary, repository, source, java) = fixture()?;
    let cache = cache_path(&repository);
    let first = NativeCacheRoot::acquire(
        &cache,
        &repository,
        &source,
        &java,
        &CancellationToken::new(),
    )?;
    assert_eq!(first.path(), cache);
    assert_eq!(fs::read(cache.join(MARKER))?, marker_bytes(&cache)?);
    let second = NativeCacheRoot::acquire(
        &cache,
        &repository,
        &source,
        &java,
        &CancellationToken::new(),
    )?;
    assert_eq!(
        NativeCacheRoot::preflight(&cache, &repository, &source, &java)?,
        cache
    );
    assert_eq!(second.path(), first.path());
    #[cfg(windows)]
    assert!(
        OpenOptions::new()
            .write(true)
            .open(cache.join(MARKER))
            .is_err()
    );
    drop(second);
    drop(first);
    drop(temporary);
    Ok(())
}

#[test]
fn native_cache_wrong_root_or_noncanonical_marker_is_not_repaired() -> Result<()> {
    let (_temporary, repository, source, java) = fixture()?;
    let cache = cache_path(&repository);
    fs::create_dir(&cache)?;
    let wrong = facet_json::to_string(&(SCHEMA.to_owned(), "some-other-root".to_owned()))?;
    fs::write(cache.join(MARKER), &wrong)?;
    assert!(
        NativeCacheRoot::acquire(
            &cache,
            &repository,
            &source,
            &java,
            &CancellationToken::new()
        )
        .is_err()
    );
    assert_eq!(fs::read(cache.join(MARKER))?, wrong.as_bytes());
    let mut noncanonical = marker_bytes(&cache)?;
    noncanonical.push(b'\n');
    fs::write(cache.join(MARKER), &noncanonical)?;
    assert!(NativeCacheRoot::preflight(&cache, &repository, &source, &java).is_err());
    assert_eq!(fs::read(cache.join(MARKER))?, noncanonical);
    Ok(())
}

#[test]
fn native_cache_hardlinked_marker_is_refused_without_removing_evidence() -> Result<()> {
    let (_temporary, repository, source, java) = fixture()?;
    let cache = cache_path(&repository);
    fs::create_dir(&cache)?;
    let outside = repository.parent().unwrap().join("ownership-evidence.json");
    let raw = marker_bytes(&cache)?;
    fs::write(&outside, &raw)?;
    fs::hard_link(&outside, cache.join(MARKER))?;
    assert!(NativeCacheRoot::preflight(&cache, &repository, &source, &java).is_err());
    assert!(
        NativeCacheRoot::acquire(
            &cache,
            &repository,
            &source,
            &java,
            &CancellationToken::new()
        )
        .is_err()
    );
    assert_eq!(fs::read(&outside)?, raw);
    assert!(cache.join(MARKER).exists());
    Ok(())
}

#[test]
fn native_cache_prepares_only_contained_normal_descendants() -> Result<()> {
    let (temporary, repository, source, java) = fixture()?;
    let cache = cache_path(&repository);
    let owner = NativeCacheRoot::acquire(
        &cache,
        &repository,
        &source,
        &java,
        &CancellationToken::new(),
    )?;
    let projection_key = "a".repeat(64);
    for relative in [
        format!("projects/{projection_key}"),
        "nfrt".to_owned(),
        "local-dependencies-v1".to_owned(),
    ] {
        let prepared = owner.prepare_directory(&relative, &CancellationToken::new())?;
        assert_eq!(prepared, cache.join(relative));
        assert!(prepared.is_dir());
    }
    for relative in [
        "",
        "../escape",
        "one/../escape",
        "/absolute",
        "a\\b",
        "a//b",
        "a/./b",
        "NUL/child",
        "a/child.",
    ] {
        assert!(
            owner
                .prepare_directory(relative, &CancellationToken::new())
                .is_err()
        );
    }
    assert!(!cache.join("a").exists());
    assert!(!cache.parent().unwrap().join("escape").exists());
    drop(owner);
    drop(temporary);
    Ok(())
}

#[test]
fn native_cache_cancellation_preserves_unclaimed_and_claimed_roots() -> Result<()> {
    let (temporary, repository, source, java) = fixture()?;
    let cache = cache_path(&repository);
    let cancelled = CancellationToken::new();
    cancelled.request_cancel("native-root fixture cancellation");
    assert!(NativeCacheRoot::acquire(&cache, &repository, &source, &java, &cancelled).is_err());
    assert!(!cache.exists());
    let owner = NativeCacheRoot::acquire(
        &cache,
        &repository,
        &source,
        &java,
        &CancellationToken::new(),
    )?;
    let raw = fs::read(cache.join(MARKER))?;
    assert!(
        owner
            .prepare_directory("must-not-appear", &cancelled)
            .is_err()
    );
    assert!(!cache.join("must-not-appear").exists());
    assert_eq!(fs::read(cache.join(MARKER))?, raw);
    drop(owner);
    drop(temporary);
    Ok(())
}

#[test]
fn native_cache_wait_for_parallel_claim_is_read_only_and_cancellable() -> Result<()> {
    let (_temporary, repository, _, _) = fixture()?;
    let cache = cache_path(&repository);
    fs::create_dir(&cache)?;
    let cancelled = CancellationToken::new();
    cancelled.request_cancel("cancel ownership marker wait");
    assert!(wait_for_marker(&cache, &cancelled).is_err());
    assert_eq!(fs::read_dir(&cache)?.count(), 0);
    Ok(())
}

#[test]
fn native_cache_parallel_new_root_claims_share_the_same_marker() -> Result<()> {
    let (temporary, repository, source, java) = fixture()?;
    let cache = cache_path(&repository);
    let barrier = std::sync::Arc::new(std::sync::Barrier::new(2));
    let (first, second) = std::thread::scope(|scope| {
        let inputs = (&cache, &repository, &source, &java);
        let first_barrier = std::sync::Arc::clone(&barrier);
        let first = scope.spawn(move || {
            first_barrier.wait();
            NativeCacheRoot::acquire(
                inputs.0,
                inputs.1,
                inputs.2,
                inputs.3,
                &CancellationToken::new(),
            )
        });
        let second = scope.spawn(move || {
            barrier.wait();
            NativeCacheRoot::acquire(
                inputs.0,
                inputs.1,
                inputs.2,
                inputs.3,
                &CancellationToken::new(),
            )
        });
        (
            first.join().expect("first root claimant panicked"),
            second.join().expect("second root claimant panicked"),
        )
    });
    let first = first?;
    let second = second?;
    assert_eq!(first.path(), second.path());
    assert_eq!(fs::read(cache.join(MARKER))?, marker_bytes(&cache)?);
    drop(first);
    drop(second);
    drop(temporary);
    Ok(())
}

#[test]
fn native_cache_preflight_waits_read_only_for_a_parallel_marker_commit() -> Result<()> {
    let (temporary, repository, source, java) = fixture()?;
    let cache = cache_path(&repository);
    fs::create_dir(&cache)?;
    let result = std::thread::scope(|scope| {
        let publishing = scope.spawn(|| {
            std::thread::sleep(Duration::from_millis(25));
            write_marker(&cache)
        });
        let checked = NativeCacheRoot::preflight(&cache, &repository, &source, &java);
        publishing.join().expect("marker publisher panicked")?;
        checked
    })?;
    assert_eq!(result, cache);
    assert_eq!(fs::read_dir(&cache)?.count(), 1);
    assert_eq!(fs::read(cache.join(MARKER))?, marker_bytes(&cache)?);
    drop(temporary);
    Ok(())
}

#[cfg(windows)]
#[test]
fn native_cache_rejects_junction_roots_and_owned_descendant_aliases() -> Result<()> {
    use std::os::windows::process::CommandExt as _;
    let (temporary, repository, source, java) = fixture()?;
    let cache = cache_path(&repository);
    let target = repository.parent().unwrap().join("outside-junction-target");
    fs::create_dir(&target)?;
    fs::write(target.join("sentinel.bin"), b"preserved outside target")?;
    let owner = NativeCacheRoot::acquire(
        &cache,
        &repository,
        &source,
        &java,
        &CancellationToken::new(),
    )?;
    let junction = cache.join("linked");
    let output = std::process::Command::new("cmd.exe")
        .args(["/d", "/c", "mklink", "/J"])
        .arg(&junction)
        .arg(&target)
        .creation_flags(0x0800_0000)
        .output()?;
    ensure!(
        output.status.success(),
        "Temporary junction fixture failed: {}",
        String::from_utf8_lossy(&output.stderr)
    );
    assert!(NativeCacheRoot::preflight(&junction, &repository, &source, &java).is_err());
    assert!(
        NativeCacheRoot::preflight(&junction.join("new-root"), &repository, &source, &java)
            .is_err()
    );
    assert!(
        owner
            .prepare_directory("linked/child", &CancellationToken::new())
            .is_err()
    );
    assert_eq!(
        fs::read(target.join("sentinel.bin"))?,
        b"preserved outside target"
    );
    assert!(!target.join("child").exists());
    assert!(!target.join("new-root").exists());
    drop(owner);
    drop(temporary);
    Ok(())
}

#[cfg(unix)]
#[test]
fn native_cache_rejects_symlink_roots_without_creating_the_target_child() -> Result<()> {
    let (_temporary, repository, source, java) = fixture()?;
    let target = repository.parent().unwrap().join("outside-link-target");
    fs::create_dir(&target)?;
    let alias = repository.parent().unwrap().join("cache-alias");
    std::os::unix::fs::symlink(&target, &alias)?;
    assert!(NativeCacheRoot::preflight(&alias, &repository, &source, &java).is_err());
    assert!(NativeCacheRoot::preflight(&alias.join("child"), &repository, &source, &java).is_err());
    assert!(!target.join("child").exists());
    Ok(())
}

#[test]
fn derived_root_none_preserves_the_complete_legacy_build_plan_wire() -> Result<()> {
    let plan = super::super::tests::minimal_plan_for_paths();
    assert!(plan.derived_cache_root.is_none());
    let raw = facet_json::to_string_pretty(&plan)?;
    assert_eq!(raw.len(), 4279);
    assert_eq!(
        crate::source_projection::provenance::sha256(raw.as_bytes()),
        "sha256:14106f776a5ca86f609d735fea05e3d2c4236136957d711ad9cf93c95a0a1f33"
    );
    assert!(!raw.contains("\"derived_cache_root\""));
    Ok(())
}

#[test]
fn derived_root_some_adds_one_path_string_without_changing_other_wire_bytes() -> Result<()> {
    let mut plan = super::super::tests::minimal_plan_for_paths();
    let baseline = facet_json::to_string(&plan)?;
    plan.derived_cache_root = Some(PathBuf::from("C:/sfm-derived-fixture"));
    let mut with_root = facet_json::to_string(&plan)?;
    assert_eq!(with_root.matches("\"derived_cache_root\":").count(), 1);
    let fragment = format!(
        "\"derived_cache_root\":{}",
        facet_json::to_string(&"C:/sfm-derived-fixture")?
    );
    let start = with_root
        .find(&fragment)
        .expect("path property must use JsonOptionalPath string wire");
    let end = start + fragment.len();
    if with_root.as_bytes().get(end) == Some(&b',') {
        with_root.replace_range(start..=end, "");
    } else {
        assert_eq!(with_root.as_bytes().get(start - 1), Some(&b','));
        with_root.replace_range(start - 1..end, "");
    }
    assert_eq!(with_root, baseline);
    Ok(())
}

#[test]
fn native_cache_cli_preflight_accepts_trusted_canonical_inputs_before_source_generation()
-> Result<()> {
    let (_temporary, repository, _, java) = fixture()?;
    let cache = cache_path(&repository);
    // CoreCatalog and SDK admission use std canonical paths (verbatim on
    // Windows). They are trusted inputs, unlike the explicit output spelling.
    let repository = fs::canonicalize(repository)?;
    let java = fs::canonicalize(java)?;
    let source = repository.join("not-generated/projection");
    assert_eq!(
        super::super::preflight_named_cache_root(&cache, &repository, &source, &java)?,
        cache
    );
    assert!(!cache.exists());
    assert!(!source.exists());
    Ok(())
}

#[test]
fn native_cache_catalog_style_source_paths_cover_existing_and_missing_ancestors() -> Result<()> {
    let (_temporary, repository, _, java) = fixture()?;
    let cache = cache_path(&repository);
    let existing = repository.join("catalog/existing/projection");
    fs::create_dir_all(&existing)?;
    for relative in [
        "catalog/existing/projection",
        "catalog/existing/new-projection",
        "not-generated/nested/projection",
    ] {
        let source = repository.join(relative);
        assert_eq!(
            NativeCacheRoot::preflight(&cache, &repository, &source, &java)?,
            cache
        );
        assert_eq!(
            super::super::preflight_named_cache_root(&cache, &repository, &source, &java)?,
            cache
        );
        let canonical_repository = fs::canonicalize(&repository)?;
        let canonical_java = fs::canonicalize(&java)?;
        let canonical_source = canonical_repository.join(relative);
        assert_eq!(
            super::super::preflight_named_cache_root(
                &cache,
                &canonical_repository,
                &canonical_source,
                &canonical_java,
            )?,
            cache
        );
    }
    assert!(!cache.exists());
    assert!(!repository.join("catalog/existing/new-projection").exists());
    assert!(!repository.join("not-generated").exists());
    assert!(existing.is_dir());
    Ok(())
}

#[test]
fn native_cache_normalized_future_source_keeps_traversal_and_overlap_rejections() -> Result<()> {
    let (_temporary, repository, _, java) = fixture()?;
    let cache = cache_path(&repository);
    let source = repository.join("not-generated/nested/projection");
    for output in [repository.join("not-generated"), source.clone()] {
        assert!(NativeCacheRoot::preflight(&output, &repository, &source, &java).is_err());
        assert!(!output.exists());
    }
    for relative in ["not-generated/../projection", "not-generated/./projection"] {
        assert!(
            NativeCacheRoot::preflight(&cache, &repository, &repository.join(relative), &java)
                .is_err()
        );
    }
    assert!(!cache.exists());
    assert!(!repository.join("not-generated").exists());
    Ok(())
}
