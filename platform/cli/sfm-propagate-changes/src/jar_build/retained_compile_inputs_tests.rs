use super::*;
use crate::source_projection::catalog_owned_project::tests::Fixture;

fn fixture() -> Result<(Fixture, RetainedCompileInputs)> {
    let (fixture, slot, recipe, _) =
        super::super::named_forge_compile_cohort_tests::fixture("1.19.2")?;
    let core = fixture
        .repository()
        .join(crate::source_projection::core_inputs::CORE_ROOT);
    for (relative, bytes) in [
        (
            "src/main/resources/retained-fixture.txt",
            &b"retained resource bytes\n"[..],
        ),
        (
            "src/main/java/nested/Retained.java",
            &b"class Retained {}\n"[..],
        ),
    ] {
        let path = core.join(relative);
        std::fs::create_dir_all(path.parent().unwrap())?;
        std::fs::write(path, bytes)?;
    }
    let project = super::super::named_forge_compile_cohort_tests::prepared(
        &fixture, slot, "release", &recipe,
    )?;
    Ok((fixture, RetainedCompileInputs::admit(Arc::new(project))?))
}

#[test]
fn retained_file_identity_matches_live_framing_then_ignores_body_edits() -> Result<()> {
    let (_fixture, inputs) = fixture()?;
    let root = dunce::simplified(inputs.project().project().project_root());
    let path = root.join("src/main/java/Shared.java");
    let bytes = inputs.bytes_at(&path)?.unwrap().to_vec();
    let live_hash =
        super::super::ContentHash::from_path(&path, super::super::ContentHashAlgorithm::Blake3)?;
    let expected = format!(
        "path:{}\nfile:{live_hash}\n",
        path.to_string_lossy().replace('\\', "/")
    );
    assert_eq!(inputs.path_input(&path)?.unwrap(), expected.as_bytes());

    std::fs::write(&path, b"class ChangedAfterAdmission {}\n")?;
    assert_eq!(inputs.bytes_at(&path)?.unwrap(), bytes);
    assert_eq!(inputs.path_input(&path)?.unwrap(), expected.as_bytes());
    inputs.recheck()?;
    assert!(inputs.project().recheck(false).is_err());
    Ok(())
}

#[test]
fn retained_membership_and_absence_never_import_new_physical_sources() -> Result<()> {
    let (_fixture, inputs) = fixture()?;
    let root = dunce::simplified(inputs.project().project().project_root());
    let java = root.join("src/main/java");
    let before = inputs.selected_paths(&java, "java")?.unwrap();
    let mut live = super::super::collect_files_under(&java)?;
    live.retain(|path| {
        path.extension()
            .is_some_and(|extension| extension == "java")
    });
    live.sort();
    assert_eq!(
        before
            .iter()
            .map(|path| path.as_os_str())
            .collect::<Vec<_>>(),
        live.iter().map(|path| path.as_os_str()).collect::<Vec<_>>()
    );
    let unknown = java.join("UnknownAfterAdmission.java");
    assert!(
        inputs
            .path_input(&unknown)
            .unwrap_err()
            .is::<super::super::all_hit_cache_miss::AllHitCacheMiss>()
    );
    std::fs::write(&unknown, b"class UnknownAfterAdmission {}\n")?;
    assert_eq!(inputs.selected_paths(&java, "java")?.unwrap(), before);
    assert!(
        inputs
            .path_input(&unknown)
            .unwrap_err()
            .is::<super::super::all_hit_cache_miss::AllHitCacheMiss>()
    );
    assert!(inputs.bytes_at(&unknown)?.is_none());
    assert!(
        inputs
            .paths_under(&root.join("src/datagen/resources"))?
            .unwrap()
            .is_empty()
    );
    assert!(inputs.paths_under(&root.join("build/project"))?.is_none());
    assert!(inputs.project().recheck(false).is_err());
    Ok(())
}

#[test]
fn retained_properties_and_resource_bytes_do_not_reopen_the_live_project() -> Result<()> {
    let (_fixture, inputs) = fixture()?;
    let root = dunce::simplified(inputs.project().project().project_root());
    let properties = root.join("gradle.properties");
    let original = inputs.properties()?;
    let resource_root = root.join("src/main/resources");
    let entries = inputs.entries_under(&resource_root)?.unwrap();
    let (path, bytes) = entries
        .into_iter()
        .find(|(path, _)| {
            path.file_name()
                .is_some_and(|name| name == "retained-fixture.txt")
        })
        .unwrap();
    let expected = bytes.to_vec();
    std::fs::write(&path, b"edited after admission")?;
    assert_eq!(inputs.bytes_at(&path)?.unwrap(), expected);
    std::fs::write(
        &properties,
        b"minecraft_version=changed\nmod_version=changed\n",
    )?;
    assert_eq!(inputs.properties()?, original);
    assert!(inputs.recheck().is_err());
    Ok(())
}

#[test]
fn retained_view_rejects_alias_traversal_and_has_no_arbitrary_file_fallback() -> Result<()> {
    let (fixture, inputs) = fixture()?;
    let root = inputs.project().project().project_root();
    let path = root.join("src/main/java/Shared.java");
    assert_eq!(
        inputs.bytes_at(&path)?,
        inputs.bytes_at(&dunce::simplified(root).join("src/main/java/Shared.java"))?
    );
    assert!(
        inputs
            .bytes_at(Path::new("src/main/java/Shared.java"))?
            .is_none()
    );
    let traversal = dunce::simplified(root).join("src/main/java/../java/Shared.java");
    assert!(
        traversal
            .components()
            .any(|part| matches!(part, Component::ParentDir)),
        "the fixture must preserve traversal before testing rejection"
    );
    assert!(
        inputs
            .bytes_at(&traversal)
            .unwrap_err()
            .is::<super::super::all_hit_cache_miss::AllHitCacheMiss>()
    );
    assert!(
        inputs
            .path_input(&traversal)
            .unwrap_err()
            .is::<super::super::all_hit_cache_miss::AllHitCacheMiss>()
    );
    let outside = fixture.repository().join("not-selected.bin");
    std::fs::write(&outside, b"arbitrary bytes")?;
    assert!(inputs.bytes_at(&outside)?.is_none());
    assert!(inputs.path_input(&outside)?.is_none());
    Ok(())
}

#[test]
fn empty_or_unsupported_directory_identity_declines_without_live_fallback() -> Result<()> {
    let (_fixture, inputs) = fixture()?;
    let root = dunce::simplified(inputs.project().project().project_root());
    let empty = root.join("src/datagen/resources");
    assert!(inputs.entries_under(&empty)?.unwrap().is_empty());
    assert!(
        inputs
            .path_input(&empty)
            .unwrap_err()
            .is::<super::super::all_hit_cache_miss::AllHitCacheMiss>()
    );
    std::fs::create_dir_all(&empty)?;
    assert!(
        inputs
            .path_input(&empty)
            .unwrap_err()
            .is::<super::super::all_hit_cache_miss::AllHitCacheMiss>()
    );
    let exclusions = root.join("gradle/source-excludes");
    assert!(
        inputs
            .path_input(&exclusions)
            .unwrap_err()
            .is::<super::super::all_hit_cache_miss::AllHitCacheMiss>()
    );
    Ok(())
}

#[test]
fn retained_nonempty_directory_identity_matches_the_strict_live_consumer() -> Result<()> {
    let (_fixture, inputs) = fixture()?;
    let root = dunce::simplified(inputs.project().project().project_root());
    let sources = root.join("src");
    let selected = inputs.paths_under(&sources)?.unwrap();
    assert!(
        selected
            .iter()
            .any(|path| path.ends_with("nested/Retained.java"))
    );
    assert!(
        selected
            .iter()
            .any(|path| path.ends_with("retained-fixture.txt"))
    );
    let mut strict_plan = super::super::tests::minimal_plan_for_paths();
    strict_plan.minecraft_dir = root.to_path_buf();
    let context = super::super::ExecutionContext::new(
        &strict_plan,
        crate::cancellation::CancellationToken::new(),
    )?;
    assert_eq!(
        inputs.path_input(&sources)?.unwrap(),
        super::super::hash_path_input(&context, &sources)?
    );
    Ok(())
}

#[test]
fn traversal_before_the_project_prefix_cannot_delegate_to_live_reads() -> Result<()> {
    let (_fixture, inputs) = fixture()?;
    let root = dunce::simplified(inputs.project().project().project_root());
    let parent = root.parent().unwrap();
    let alias = parent
        .join("..")
        .join(parent.file_name().unwrap())
        .join(root.file_name().unwrap())
        .join("src/main/java/Shared.java");
    assert_eq!(
        std::fs::read(&alias)?,
        inputs
            .bytes_at(&root.join("src/main/java/Shared.java"))?
            .unwrap()
    );
    assert!(
        inputs
            .bytes_at(&alias)
            .unwrap_err()
            .is::<super::super::all_hit_cache_miss::AllHitCacheMiss>()
    );
    assert!(
        inputs
            .path_input(&alias)
            .unwrap_err()
            .is::<super::super::all_hit_cache_miss::AllHitCacheMiss>()
    );
    Ok(())
}

#[cfg(windows)]
#[test]
fn windows_case_aliases_decline_instead_of_delegating_to_live_sources() -> Result<()> {
    let (_fixture, inputs) = fixture()?;
    let root = dunce::simplified(inputs.project().project().project_root());
    let upper_root = PathBuf::from(root.to_string_lossy().to_ascii_uppercase());
    for alias in [
        root.join("SRC/main/java/Shared.java"),
        root.join("src/MAIN/java/Shared.java"),
        upper_root.join("src/main/java/Shared.java"),
    ] {
        assert_eq!(
            std::fs::read(&alias)?,
            inputs
                .bytes_at(&root.join("src/main/java/Shared.java"))?
                .unwrap()
        );
        assert!(
            inputs
                .bytes_at(&alias)
                .unwrap_err()
                .is::<super::super::all_hit_cache_miss::AllHitCacheMiss>()
        );
        assert!(
            inputs
                .path_input(&alias)
                .unwrap_err()
                .is::<super::super::all_hit_cache_miss::AllHitCacheMiss>()
        );
    }
    assert!(
        inputs
            .paths_under(&root.join("SRC/main/java"))
            .unwrap_err()
            .is::<super::super::all_hit_cache_miss::AllHitCacheMiss>()
    );
    Ok(())
}

#[cfg(windows)]
#[test]
fn unsupported_windows_unicode_paths_decline_before_external_fallback() -> Result<()> {
    let (fixture, inputs) = fixture()?;
    let root = dunce::simplified(inputs.project().project().project_root());
    let source = root.join("src/main/java/Unsupported-\u{00e9}.java");
    let external = fixture.repository().join("cache-\u{00e9}.jar");
    std::fs::write(&external, b"unrelated existing cache bytes")?;
    for path in [source, external] {
        assert!(
            inputs
                .bytes_at(&path)
                .unwrap_err()
                .is::<super::super::all_hit_cache_miss::AllHitCacheMiss>()
        );
        assert!(
            inputs
                .path_input(&path)
                .unwrap_err()
                .is::<super::super::all_hit_cache_miss::AllHitCacheMiss>()
        );
        assert!(
            inputs
                .paths_under(&path)
                .unwrap_err()
                .is::<super::super::all_hit_cache_miss::AllHitCacheMiss>()
        );
    }
    Ok(())
}
