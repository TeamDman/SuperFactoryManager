use super::*;

#[path = "junit_logging_java_tests.rs"]
mod logging_java_tests;

fn sample() -> Result<Snapshot> {
    let mut snapshot = Snapshot::new("selected historical source owner".to_owned());
    for (relative, bytes) in [
        (
            "src/main/java/Selected.java",
            b"class Selected {}".as_slice(),
        ),
        (
            "src/main/resources/assets/sfm/template_programs/template.sfml",
            b"INPUT FROM a".as_slice(),
        ),
        ("examples/demo.sfm", b"INPUT FROM a".as_slice()),
    ] {
        snapshot.file(relative, bytes.to_vec())?;
    }
    Ok(snapshot)
}

fn legacy_find(root: &Path, relative: &str) -> Option<PathBuf> {
    root.ancestors()
        .take(5)
        .map(|parent| parent.join(relative))
        .find(|candidate| candidate.is_dir())
}

#[test]
fn historical_junit_fixture_layout_is_independent_of_cache_depth_and_reuses_checked_bytes()
-> Result<()> {
    let base = tempfile::tempdir()?;
    let snapshot = sample()?;
    let cancel = CancellationToken::new();
    for relative in ["shallow", "unrelated/a/b/c/d/e/f/g/owned-cache"] {
        let cache = base.path().join(relative);
        fs::create_dir_all(&cache)?;
        let fixture = publish(&cache, &snapshot, &cancel)?;
        assert_eq!(
            legacy_find(fixture.root(), "examples"),
            Some(fixture.root().join("examples"))
        );
        assert_eq!(
            legacy_find(
                fixture.root(),
                "src/main/resources/assets/sfm/template_programs"
            ),
            Some(
                fixture
                    .root()
                    .join("src/main/resources/assets/sfm/template_programs")
            )
        );
        let selected = fixture.root().join("src/main/java/Selected.java");
        let before = fs::metadata(&selected)?.modified()?;
        let reused = publish(&cache, &snapshot, &cancel)?;
        assert_eq!(reused.root(), fixture.root());
        assert_eq!(fs::metadata(selected)?.modified()?, before);
        assert_eq!(
            fs::read(reused.root().join("src/main/java/Selected.java"))?,
            b"class Selected {}"
        );
        assert!(!reused.root().join(GENERATED).exists());
    }
    Ok(())
}

#[test]
fn historical_junit_fixture_rejects_corrupt_missing_extra_and_aliased_outputs_without_repair()
-> Result<()> {
    let cancel = CancellationToken::new();
    let snapshot = sample()?;
    for damage in [
        "bytes",
        "missing",
        "extra",
        "directory",
        "manifest",
        "hardlink",
    ] {
        let cache = tempfile::tempdir()?;
        let fixture = publish(cache.path(), &snapshot, &cancel)?;
        let path = fixture.root().join("examples/demo.sfm");
        match damage {
            "bytes" => fs::write(&path, b"changed")?,
            "missing" => fs::remove_file(&path)?,
            "extra" => fs::write(fixture.root().join("extra"), b"unknown")?,
            "directory" => fs::create_dir_all(fixture.root().join(GENERATED))?,
            "manifest" => fs::write(fixture.root().join(MANIFEST), b"changed manifest")?,
            "hardlink" => {
                let other = cache.path().join("other");
                fs::write(&other, b"INPUT FROM a")?;
                fs::remove_file(&path)?;
                fs::hard_link(other, &path)?;
            }
            _ => unreachable!(),
        }
        assert!(
            verify(
                fixture.root(),
                &snapshot.manifest,
                &fixture.manifest,
                &cancel
            )
            .is_err(),
            "{damage}"
        );
        assert!(
            publish(cache.path(), &snapshot, &cancel).is_err(),
            "Cache damage must not be repaired: {damage}"
        );
    }
    Ok(())
}

#[test]
fn historical_junit_fixture_logging_contract_keeps_failed_v1_entries_unchanged() -> Result<()> {
    let cache = tempfile::tempdir()?;
    let cancel = CancellationToken::new();
    let mut old = sample()?;
    old.manifest.schema = "sfm:junit_fixture_snapshot@1".to_owned();
    let failed = publish(cache.path(), &old, &cancel)?;
    fs::create_dir(failed.root().join("logs"))?;
    fs::write(
        failed.root().join("logs/latest.log"),
        b"retained failed JVM evidence",
    )?;
    assert!(publish(cache.path(), &old, &cancel).is_err());
    let current = sample()?;
    let fresh = publish(cache.path(), &current, &cancel)?;
    assert_ne!(fresh.identity(), failed.identity());
    assert_ne!(fresh.root(), failed.root());
    verify(fresh.root(), &current.manifest, &fresh.manifest, &cancel)?;
    assert_eq!(
        fs::read(failed.root().join("logs/latest.log"))?,
        b"retained failed JVM evidence"
    );
    assert!(!fresh.root().join("logs").exists());
    Ok(())
}

#[test]
fn historical_junit_fixture_refuses_unsafe_inputs_and_preserves_empty_generated_source_state()
-> Result<()> {
    let cache = tempfile::tempdir()?;
    let cancel = CancellationToken::new();
    let mut traversal = sample()?;
    traversal.file("../escape", b"not allowed".to_vec())?;
    assert!(publish(cache.path(), &traversal, &cancel).is_err());
    let mut alias = sample()?;
    alias.file("src/main/java/selected.java", b"case alias".to_vec())?;
    assert!(publish(cache.path(), &alias, &cancel).is_err());
    let mut oversized = sample()?;
    assert!(
        oversized
            .file(
                "examples/oversized.sfm",
                vec![0; usize::try_from(MAX_FILE)? + 1]
            )
            .is_err()
    );
    let mut snapshot = sample()?;
    snapshot.manifest.generated_source_present = true;
    snapshot.directory(GENERATED)?;
    let fixture = publish(cache.path(), &snapshot, &cancel)?;
    assert!(fixture.root().join(GENERATED).is_dir());
    let absent = publish(cache.path(), &sample()?, &cancel)?;
    assert_ne!(fixture.identity(), absent.identity());
    assert!(!absent.root().join(GENERATED).exists());
    Ok(())
}

#[cfg(unix)]
#[test]
fn historical_junit_fixture_refuses_source_and_output_links() -> Result<()> {
    let source = tempfile::tempdir()?;
    let outside = tempfile::NamedTempFile::new()?;
    std::os::unix::fs::symlink(outside.path(), source.path().join("linked.sfm"))?;
    let _ = sample()?
        .tree(source.path(), "examples", true, &CancellationToken::new())
        .unwrap_err();
    let other = tempfile::tempdir()?;
    std::os::unix::fs::symlink(other.path(), source.path().join("parent-link"))?;
    let _ = sample()?
        .tree(
            &source.path().join("parent-link/missing"),
            GENERATED,
            false,
            &CancellationToken::new(),
        )
        .unwrap_err();
    let cache = tempfile::tempdir()?;
    let snapshot = sample()?;
    let fixture = publish(cache.path(), &snapshot, &CancellationToken::new())?;
    let path = fixture.root().join("examples/demo.sfm");
    fs::remove_file(&path)?;
    std::os::unix::fs::symlink(outside.path(), &path)?;
    assert!(
        verify(
            fixture.root(),
            &snapshot.manifest,
            &fixture.manifest,
            &CancellationToken::new()
        )
        .is_err()
    );
    Ok(())
}

fn source_plan() -> Result<(
    crate::source_projection::catalog_owned_project::tests::Fixture,
    BuildPlan,
)> {
    use crate::source_projection::catalog_owned_project::tests::Fixture;
    let (fixture, slot, recipe, _) =
        super::super::named_forge_compile_cohort_tests::fixture("1.19.2")?;
    let key = "sfm-4.34.0/mc-1.19.2";
    let catalog = fixture
        .repository()
        .join(crate::source_projection::projection_catalog::CATALOG_PATH);
    let before = fs::read_to_string(&catalog)?;
    let old = facet_json::to_string(&Fixture::key(slot, "release"))?;
    assert_eq!(before.matches(&old).count(), 1);
    fs::write(
        catalog,
        before.replacen(&old, &facet_json::to_string(key)?, 1),
    )?;
    fixture.publish(key);
    let project = crate::source_projection::frozen_recipe_project::prepare_frozen_recipe_project(
        fixture.collect(key)?.check_current()?,
        &recipe,
        false,
        &BTreeMap::new(),
    )?;
    let mut plan = super::super::tests::minimal_plan_for_paths();
    plan.identity = super::super::BuildProjectIdentity::Catalog {
        frozen: std::sync::Arc::new(project),
    };
    plan.minecraft_dir = plan.identity.minecraft_dir();
    plan.cache_dir = fixture.repository().join("separate/owned-cache");
    fs::create_dir_all(&plan.cache_dir)?;
    fs::create_dir(fixture.repository().join("examples"))?;
    fs::write(
        fixture.repository().join("examples/demo.sfm"),
        b"original demo",
    )?;
    Ok((fixture, plan))
}

#[test]
fn historical_junit_fixture_binds_selected_source_examples_and_optional_source_membership()
-> Result<()> {
    let (_owner, plan) = source_plan()?;
    let cancel = CancellationToken::new();
    let fixture = JunitFixture::prepare(&plan, &cancel)?.expect("plain release fixture");
    let source = plan
        .named_project()
        .unwrap()
        .project()
        .selected_input("src/main/java/Shared.java")?;
    assert_eq!(
        fs::read(fixture.root().join(source.output_path()))?,
        source.output_bytes()
    );
    fixture.recheck(&plan, &cancel)?;
    let examples = plan.repository_root().join("examples/demo.sfm");
    fs::write(&examples, b"changed demo")?;
    assert!(fixture.recheck(&plan, &cancel).is_err());
    fs::write(examples, b"original demo")?;
    fs::create_dir_all(plan.minecraft_dir.join(GENERATED))?;
    assert!(fixture.recheck(&plan, &cancel).is_err());
    let updated = JunitFixture::prepare(&plan, &cancel)?.expect("plain release fixture");
    assert_ne!(updated.identity(), fixture.identity());
    assert!(updated.root().join(GENERATED).is_dir());
    updated.recheck(&plan, &cancel)?;
    let empty = "src/main/java/added-empty-directory";
    fs::create_dir_all(plan.minecraft_dir.join(empty))?;
    assert!(updated.recheck(&plan, &cancel).is_err());
    let current = JunitFixture::prepare(&plan, &cancel)?.expect("plain release fixture");
    assert!(current.root().join(empty).is_dir());
    current.recheck(&plan, &cancel)?;
    Ok(())
}

#[test]
#[ignore = "Requires an explicitly selected diagnostic Java; checks filesystem layout only"]
fn historical_junit_fixture_actual_java_searches_select_snapshot_data() -> Result<()> {
    let java = std::env::var_os("SFM_TEST_FIXTURE_JAVA")
        .ok_or_else(|| eyre::eyre!("Set SFM_TEST_FIXTURE_JAVA"))?;
    let cache = tempfile::tempdir()?;
    let snapshot = sample()?;
    let fixture = publish(cache.path(), &snapshot, &CancellationToken::new())?;
    let probe = cache.path().join("FixturePathProbe.java");
    fs::write(
        &probe,
        r#"
import java.nio.file.*;
public class FixturePathProbe {
    static Path find(String relative) {
        Path cwd = Paths.get(System.getProperty("user.dir"));
        for (int i=0; i<5; i++) {
            Path candidate = cwd.resolve(relative);
            if (Files.isDirectory(candidate)) return candidate;
            cwd = cwd.getParent(); if (cwd == null) break;
        }
        throw new AssertionError("missing " + relative);
    }
    static Path project() {
        for (Path p=Paths.get("").toAbsolutePath().normalize(); p!=null; p=p.getParent()) {
            if (Files.isDirectory(p.resolve("src/main/java"))) return p;
            if (Files.isDirectory(p.resolve("platform/minecraft/src/main/java"))) return p.resolve("platform/minecraft");
        }
        throw new AssertionError("missing project");
    }
    public static void main(String[] args) throws Exception {
        Path expected = Paths.get(args[0]);
        if (!Files.isSameFile(project(), expected)) throw new AssertionError("wrong source owner");
        for (String relative : new String[]{"examples", "src/main/resources/assets/sfm/template_programs"})
            if (!Files.isSameFile(find(relative), expected.resolve(relative))) throw new AssertionError("wrong fixture");
        if (!Files.readString(project().resolve("src/main/java/Selected.java")).equals("class Selected {}")) throw new AssertionError("wrong source bytes");
    }
}

"#,
    )?;
    let mut command = std::process::Command::new(java);
    let trust = crate::jar_build::java_trust::JavaTrust::configure_java(&mut command, false, 25)?;
    let output = command
        .arg(&probe)
        .arg(fixture.root())
        .current_dir(fixture.root())
        .output()?;
    crate::jar_build::java_trust::JavaTrust::recheck_optional(trust.as_ref())?;
    ensure!(
        output.status.success(),
        "Java fixture probe failed: {}",
        String::from_utf8_lossy(&output.stderr)
    );
    verify(
        fixture.root(),
        &snapshot.manifest,
        &fixture.manifest,
        &CancellationToken::new(),
    )?;
    Ok(())
}
