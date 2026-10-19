use super::*;
use std::cell::Cell;

fn write_output(root: &Path) -> Result<()> {
    fs::create_dir_all(root.join("classes/package"))?;
    fs::create_dir_all(root.join("resources"))?;
    fs::write(
        root.join("classes/package/Example.class"),
        b"compiled bytes",
    )?;
    fs::write(root.join("resources/refmap.json"), b"generated refmap")?;
    Ok(())
}

#[test]
fn compilation_inventory_reuses_exact_outputs_and_rejects_changed_dependencies() -> Result<()> {
    let fixture = tempfile::tempdir()?;
    let root = fixture.path();
    write_output(root)?;
    let state = root.join("main.inputs.sha1");
    let classes = root.join("classes");
    let resources = root.join("resources");
    let roots = [
        ("classes", classes.as_path()),
        ("resources", resources.as_path()),
    ];
    fs::write(&state, "selected inputs")?;
    record(root, &state, "selected inputs", &roots, || Ok(()))?;
    assert!(matches(root, &state, "selected inputs", &roots, || Ok(()))?);
    fs::write(classes.join("package/Example.class"), b"compiled bytes")?;
    assert!(
        matches(root, &state, "selected inputs", &roots, || Ok(()))?,
        "Equal bytes with a new timestamp remain reusable"
    );
    assert!(!matches(root, &state, "other inputs", &roots, || Ok(()))?);
    Ok(())
}

#[test]
fn compilation_inventory_refuses_corrupt_missing_and_extra_payloads() -> Result<()> {
    for mutation in ["class", "resource", "missing", "extra", "manifest"] {
        let fixture = tempfile::tempdir()?;
        let root = fixture.path();
        write_output(root)?;
        let state = root.join("main.inputs.sha1");
        let classes = root.join("classes");
        let resources = root.join("resources");
        let roots = [
            ("classes", classes.as_path()),
            ("resources", resources.as_path()),
        ];
        fs::write(&state, "selected inputs")?;
        record(root, &state, "selected inputs", &roots, || Ok(()))?;
        match mutation {
            "class" => fs::write(classes.join("package/Example.class"), b"changed! bytes")?,
            "resource" => fs::write(resources.join("refmap.json"), b"changed! refmap!")?,
            "missing" => fs::remove_file(classes.join("package/Example.class"))?,
            "extra" => fs::write(classes.join("Foreign.class"), b"foreign")?,
            "manifest" => fs::write(manifest_path(&state), b"incomplete JSON")?,
            _ => unreachable!(),
        }
        assert!(
            !matches(root, &state, "selected inputs", &roots, || Ok(()))?,
            "adopted {mutation}"
        );
    }
    Ok(())
}

#[test]
fn compilation_inventory_legacy_state_and_foreign_paths_do_not_authorize_reuse() -> Result<()> {
    let fixture = tempfile::tempdir()?;
    let outside = tempfile::tempdir()?;
    let root = fixture.path();
    write_output(root)?;
    let state = root.join("main.inputs.sha1");
    let classes = root.join("classes");
    fs::write(&state, "selected inputs")?;
    assert!(!matches(
        root,
        &state,
        "selected inputs",
        &[("classes", &classes)],
        || Ok(())
    )?);
    fs::write(outside.path().join("untouched"), b"outside")?;
    assert!(
        record(
            root,
            &state,
            "selected inputs",
            &[("classes", outside.path())],
            || Ok(())
        )
        .is_err()
    );
    assert_eq!(fs::read(outside.path().join("untouched"))?, b"outside");
    Ok(())
}

#[test]
fn compilation_inventory_cancellation_is_an_error_and_never_publishes() -> Result<()> {
    let fixture = tempfile::tempdir()?;
    let root = fixture.path();
    write_output(root)?;
    let state = root.join("main.inputs.sha1");
    let classes = root.join("classes");
    let calls = Cell::new(0);
    let check = || {
        calls.set(calls.get() + 1);
        ensure!(calls.get() < 3, "fixture cancelled");
        Ok(())
    };
    assert!(
        record(
            root,
            &state,
            "selected inputs",
            &[("classes", &classes)],
            check
        )
        .is_err()
    );
    assert!(!manifest_path(&state).exists());
    fs::write(&state, "selected inputs")?;
    record(
        root,
        &state,
        "selected inputs",
        &[("classes", &classes)],
        || Ok(()),
    )?;
    calls.set(0);
    let result = matches(
        root,
        &state,
        "selected inputs",
        &[("classes", &classes)],
        check,
    );
    assert!(
        result
            .unwrap_err()
            .to_string()
            .contains("fixture cancelled")
    );
    Ok(())
}

#[test]
fn compilation_inventory_invalidates_prior_marker_after_unbound_execution() -> Result<()> {
    let fixture = tempfile::tempdir()?;
    let root = fixture.path();
    write_output(root)?;
    let state = root.join("main.inputs.sha1");
    let classes = root.join("classes");
    let roots = [("classes", classes.as_path())];
    record(root, &state, "inputs", &roots, || Ok(()))?;
    invalidate(root, &state, || Ok(()))?;
    assert!(
        !matches(root, &state, "inputs", &roots, || Ok(()))?,
        "An earlier marker must not bless outputs overwritten by an unbound compiler"
    );
    record(root, &state, "inputs", &roots, || Ok(()))?;
    assert!(matches(root, &state, "inputs", &roots, || Ok(()))?);
    Ok(())
}

#[cfg(any(windows, unix))]
#[test]
fn compilation_records_refuse_hardlink_destinations_without_mutating_alias() -> Result<()> {
    let fixture = tempfile::tempdir()?;
    let outside = tempfile::tempdir()?;
    let root = fixture.path();
    let original = outside.path().join("preserved");
    fs::write(&original, b"user bytes")?;
    let alias = root.join("aliased-control-record");
    fs::hard_link(&original, &alias)?;
    assert!(write_record(root, &alias, b"replacement").is_err());
    assert_eq!(fs::read(&original)?, b"user bytes");
    assert_eq!(fs::read(&alias)?, b"user bytes");
    Ok(())
}
