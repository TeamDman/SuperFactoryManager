use super::tests::{java, project};
use super::*;

#[test]
fn shared_source_snapshot_refuses_foreign_owners_without_changing_latest() -> Result<()> {
    let temporary = tempfile::tempdir()?;
    let (minecraft, cache) = project(temporary.path(), "projection")?;
    let (_, source_cache) = project(temporary.path(), "separate-compiler")?;
    let source = source_cache.join("project");
    fs::write(
        source.join("classes/example/Main.class"),
        b"separate compiler bytes",
    )?;
    let publish = |destination: &Path, producer: &Path| {
        BuildSnapshotReceipt::publish_from_source_cache_cancellable(
            &minecraft,
            destination,
            &source_cache,
            "1.19.2",
            &java(&minecraft),
            producer,
            ("sdk-fixture", "fixture-runtime-contract"),
            &CancellationToken::new(),
        )
    };
    let receipt = publish(&cache, &source)?;
    receipt.recheck()?;
    assert_eq!(
        fs::read(
            receipt
                .snapshot_dir
                .join("project/classes/example/Main.class")
        )?,
        b"separate compiler bytes"
    );
    assert_eq!(
        fs::read(cache.join("project/classes/example/Main.class"))?,
        b"main class fixture"
    );
    let latest = cache.join("state/latest-successful-build.json");
    let before = fs::read(&latest)?;
    let count = fs::read_dir(cache.join("build-snapshots"))?.count();
    let _ = publish(&cache, &cache.join("project")).unwrap_err();
    let foreign = temporary.path().join("foreign-destination");
    fs::create_dir(&foreign)?;
    let _ = publish(&foreign, &source).unwrap_err();
    assert!(!foreign.join("build-snapshots").exists());
    assert!(!source_cache.join("build-snapshots").exists());
    assert_eq!(fs::read(&latest)?, before);
    assert_eq!(fs::read_dir(cache.join("build-snapshots"))?.count(), count);
    receipt.recheck()?;
    Ok(())
}

#[cfg(unix)]
#[test]
fn shared_source_snapshot_refuses_linked_roots_leaves_and_ancestors() -> Result<()> {
    for damage in [
        "source-root",
        "source-leaf",
        "source-ancestor",
        "destination-ancestor",
    ] {
        let temporary = tempfile::tempdir()?;
        let (minecraft, cache) = project(temporary.path(), "projection")?;
        let (_, source_cache) = project(temporary.path(), "separate-compiler")?;
        let source = source_cache.join("project");
        let publish = || {
            BuildSnapshotReceipt::publish_from_source_cache_cancellable(
                &minecraft,
                &cache,
                &source_cache,
                "1.19.2",
                &java(&minecraft),
                &source,
                ("sdk-fixture", "fixture-runtime-contract"),
                &CancellationToken::new(),
            )
        };
        let receipt = publish()?;
        receipt.recheck()?;
        let latest = cache.join("state/latest-successful-build.json");
        let before = fs::read(&latest)?;
        match damage {
            "source-root" => {
                let held = source_cache.join("held-classes");
                fs::rename(source.join("classes"), &held)?;
                std::os::unix::fs::symlink(held, source.join("classes"))?;
            }
            "source-leaf" => {
                std::os::unix::fs::symlink(
                    source.join("classes/example/Main.class"),
                    source.join("classes/example/Linked.class"),
                )?;
            }
            "source-ancestor" => {
                let held = source_cache.join("held-project");
                fs::rename(&source, &held)?;
                std::os::unix::fs::symlink(held, &source)?;
            }
            "destination-ancestor" => {
                let snapshots = cache.join("build-snapshots");
                let held = cache.join("held-snapshots");
                fs::rename(&snapshots, &held)?;
                std::os::unix::fs::symlink(held, snapshots)?;
            }
            _ => unreachable!(),
        }
        let _ = publish().unwrap_err();
        assert_eq!(fs::read(&latest)?, before, "{damage}");
        assert!(!source_cache.join("build-snapshots").exists());
    }
    Ok(())
}
