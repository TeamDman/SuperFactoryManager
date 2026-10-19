use super::*;

fn fixture() -> Result<(tempfile::TempDir, PathBuf, PathBuf)> {
    let directory = tempfile::tempdir()?;
    let owner = directory.path().join("owner");
    fs::create_dir_all(owner.join("build/libs"))?;
    let output = resolve(
        &owner,
        "review-fresh.jar",
        &owner.join("build/libs/default-rust.jar"),
        &owner.join("build/libs/default.jar"),
    )?;
    Ok((directory, owner, output))
}

#[test]
fn portable_name_and_default_alias_validation_is_lexical() -> Result<()> {
    let root = std::env::temp_dir().join("fresh-jar-lexical-not-created");
    let rust = root.join("build/libs/Default-Rust.jar");
    let gradle = root.join("build/libs/Default.jar");
    for name in [
        "",
        ".jar",
        "../fresh.jar",
        r"..\fresh.jar",
        "nested/fresh.jar",
        r"nested\fresh.jar",
        "C:drive.jar",
        "fresh.jar:stream",
        "fresh.jar ",
        "fresh.jar.",
        "fresh .jar",
        "fresh;name.jar",
        "fresh:name.jar",
        "fresh?.jar",
        "fresh.txt",
        "CON.jar",
        "con.extra.jar",
        "NUL.jar",
        "PRN.jar",
        "AUX.jar",
        "COM1.jar",
        "com9.extra.jar",
        "LPT1.jar",
        "LPT9.extra.jar",
        "COM\u{00b9}.jar",
        "DEFAULT-RUST.JAR",
        "default.jar",
    ] {
        assert!(resolve(&root, name, &rust, &gradle).is_err(), "{name}");
    }
    assert!(resolve(&root, &format!("{}.jar", "a".repeat(125)), &rust, &gradle).is_err());
    assert_eq!(
        resolve(&root, "review-1_2.JAR", &rust, &gradle)?,
        root.join("build/libs/review-1_2.JAR")
    );
    Ok(())
}

#[test]
fn preparation_requires_exact_existing_namespace_without_creating_it() -> Result<()> {
    let directory = tempfile::tempdir()?;
    let owner = directory.path().join("owner");
    fs::create_dir(&owner)?;
    let output = owner.join("build/libs/fresh.jar");
    assert!(FreshJarOutput::prepare(&owner, &output).is_err());
    assert!(!owner.join("build").exists());
    fs::create_dir(owner.join("build"))?;
    fs::write(owner.join("build/libs"), b"not a directory")?;
    assert!(FreshJarOutput::prepare(&owner, &output).is_err());
    assert_eq!(fs::read(owner.join("build/libs"))?, b"not a directory");
    assert!(FreshJarOutput::prepare(&owner, &directory.path().join("outside.jar")).is_err());
    assert!(FreshJarOutput::prepare(&owner, &owner.join("build/other/fresh.jar")).is_err());
    Ok(())
}

#[test]
fn existing_file_and_directory_outputs_are_preserved() -> Result<()> {
    let (_directory, owner, output) = fixture()?;
    fs::write(&output, b"prior evidence")?;
    assert!(FreshJarOutput::prepare(&owner, &output).is_err());
    assert_eq!(fs::read(&output)?, b"prior evidence");
    fs::remove_file(&output)?;
    fs::create_dir(&output)?;
    assert!(FreshJarOutput::prepare(&owner, &output).is_err());
    assert!(output.is_dir());
    Ok(())
}

#[test]
fn competitor_after_preparation_is_never_overwritten() -> Result<()> {
    let (_directory, owner, output) = fixture()?;
    let mut capability = FreshJarOutput::prepare(&owner, &output)?;
    let input = owner.join("complete.jar");
    fs::write(&input, b"complete synthetic package")?;
    fs::write(&output, b"competitor evidence")?;
    assert!(
        capability
            .publish_copy(&input, &CancellationToken::new())
            .is_err()
    );
    assert_eq!(fs::read(&output)?, b"competitor evidence");
    assert_eq!(fs::read(&input)?, b"complete synthetic package");
    Ok(())
}

#[test]
fn competitor_between_absence_check_and_create_new_is_preserved() -> Result<()> {
    let (_directory, owner, output) = fixture()?;
    let capability = FreshJarOutput::prepare(&owner, &output)?;
    let input = owner.join("complete.jar");
    fs::write(&input, b"complete synthetic package")?;
    capability.recheck_absent()?;
    fs::write(&output, b"competitor won the final create")?;
    // Exercise the final create_new boundary, without another absence check.
    assert!(
        capability
            .copy_completed(&input, &CancellationToken::new())
            .is_err()
    );
    assert_eq!(fs::read(&output)?, b"competitor won the final create");
    assert_eq!(fs::read(&input)?, b"complete synthetic package");
    Ok(())
}

#[test]
fn missing_input_and_cancelled_attempt_do_not_create_final_output() -> Result<()> {
    let (_directory, owner, output) = fixture()?;
    let mut capability = FreshJarOutput::prepare(&owner, &output)?;
    assert!(
        capability
            .publish_copy(&owner.join("missing.jar"), &CancellationToken::new())
            .is_err()
    );
    assert!(!output.exists());
    assert!(
        capability
            .publish_copy(&owner, &CancellationToken::new())
            .is_err()
    );
    assert!(!output.exists());
    let input = owner.join("complete.jar");
    fs::write(&input, b"complete synthetic package")?;
    let cancellation = CancellationToken::new();
    cancellation.request_cancel("fixture cancellation before publication");
    assert!(capability.publish_copy(&input, &cancellation).is_err());
    assert!(!output.exists());
    assert_eq!(fs::read(&input)?, b"complete synthetic package");
    Ok(())
}

#[test]
fn verified_copy_succeeds_once_and_preserves_prior_final_on_retry() -> Result<()> {
    let (_directory, owner, output) = fixture()?;
    let input = owner.join("complete.jar");
    fs::write(&input, b"complete synthetic package")?;
    let mut capability = FreshJarOutput::prepare(&owner, &output)?;
    assert_eq!(capability.path(), output);
    capability.publish_copy(&input, &CancellationToken::new())?;
    assert_eq!(fs::read(&output)?, b"complete synthetic package");
    assert!(
        capability
            .publish_copy(&input, &CancellationToken::new())
            .is_err()
    );
    assert_eq!(fs::read(&output)?, b"complete synthetic package");
    Ok(())
}

#[test]
fn private_writer_is_capability_bound_and_directory_can_be_cleaned_after_copy() -> Result<()> {
    let (_directory, owner, output) = fixture()?;
    let project = owner.join("build/cache/project");
    fs::create_dir_all(&project)?;
    let mut capability = FreshJarOutput::prepare(&owner, &output)?;
    assert!(capability.private_output_path().is_err());
    assert!(
        capability
            .private_output_directory(owner.parent().expect("fixture owner parent"))
            .is_err()
    );
    let private = capability.private_output_directory(&project)?;
    let private_path = private.path().to_path_buf();
    let writer = capability.private_output_path()?.to_path_buf();
    assert_eq!(writer, private_path.join("reobfuscated.jar"));
    assert!(
        private_path
            .file_name()
            .and_then(|name| name.to_str())
            .unwrap_or_default()
            .starts_with("fresh-jar-")
    );
    assert!(capability.private_output_directory(&project).is_err());
    let other = project.join("other.jar");
    fs::write(&other, b"other synthetic package")?;
    assert!(
        capability
            .publish_copy(&other, &CancellationToken::new())
            .is_err()
    );
    assert!(!output.exists());
    fs::write(&writer, b"complete private synthetic package")?;
    capability.publish_copy(&writer, &CancellationToken::new())?;
    assert_eq!(fs::read(&output)?, b"complete private synthetic package");
    private.close()?;
    assert!(!private_path.exists());
    Ok(())
}

#[test]
fn cancellation_after_copy_retains_new_final_evidence() -> Result<()> {
    let (_directory, owner, output) = fixture()?;
    let input = owner.join("complete.jar");
    fs::write(&input, b"complete synthetic package")?;
    let mut capability = FreshJarOutput::prepare(&owner, &output)?;
    let cancellation = CancellationToken::new();
    assert!(
        capability
            .publish_copy_inner(&input, &cancellation, || {
                cancellation.request_cancel("fixture cancellation after copy");
            })
            .is_err()
    );
    assert_eq!(fs::read(&output)?, b"complete synthetic package");
    assert_eq!(fs::read(&input)?, b"complete synthetic package");
    Ok(())
}

#[cfg(windows)]
#[test]
fn final_output_ancestry_is_held_until_capability_drop() -> Result<()> {
    let (_directory, owner, output) = fixture()?;
    let capability = FreshJarOutput::prepare(&owner, &output)?;
    let libs = owner.join("build/libs");
    let moved = owner.join("build/moved-libs");
    assert!(fs::rename(&libs, &moved).is_err());
    capability.recheck_absent()?;
    drop(capability);
    fs::rename(&libs, &moved)?;
    Ok(())
}

#[test]
fn source_growth_after_authentication_is_bounded_and_preserves_existing_finals() -> Result<()> {
    use std::io::Write as _;
    let (_directory, owner, output) = fixture()?;
    let historical = owner.join("build/libs/default-rust.jar");
    fs::write(&historical, b"historical final evidence")?;
    let input = owner.join("complete.jar");
    let original = b"authenticated synthetic package";
    fs::write(&input, original)?;
    let capability = FreshJarOutput::prepare(&owner, &output)?;
    let error = capability
        .copy_authenticated(&input, &CancellationToken::new(), || {
            fs::OpenOptions::new()
                .append(true)
                .open(&input)?
                .write_all(&[b'x'; 4096])?;
            Ok(())
        })
        .unwrap_err();
    assert!(
        error.to_string().contains("source length changed"),
        "{error:?}"
    );
    assert_eq!(
        fs::metadata(&output)?.len(),
        u64::try_from(original.len())? + 1
    );
    assert_eq!(fs::read(&historical)?, b"historical final evidence");
    Ok(())
}
