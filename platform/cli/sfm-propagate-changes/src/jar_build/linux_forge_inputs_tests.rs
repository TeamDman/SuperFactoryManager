use super::*;
use std::fs;
use std::os::unix::fs::{PermissionsExt as _, symlink};

fn captured(path: &Path, role: &str) -> Result<HeldForgePreparationInput> {
    Ok(HeldForgePreparationInput::capture(
        &ForgePreparationInputRequest::new(role, path.to_path_buf()),
        &mut DirectoryLeases::default(),
        &crate::cancellation::CancellationToken::new(),
    )?
    .unwrap()
    .input)
}
fn fingerprint(
    files: &[HeldForgePreparationInput],
    sdk: &str,
    trust: &str,
    release: u32,
) -> String {
    let mut hash = Sha256::new();
    recipe_basis(
        &mut hash,
        "forge",
        "1.19.2",
        "net.minecraftforge:forge:1.19.2-43.3.5",
        release,
        Some("official"),
        Some("1.19.2"),
    );
    append(&mut hash, trust.as_bytes());
    finish_fingerprint(
        hash,
        "forge",
        "invocation SDK path is separately checked",
        sdk,
        files,
    )
}
#[test]
fn linux_forge_keys_share_identical_inputs_across_consumers_and_invalidate_actual_changes()
-> Result<()> {
    let fixture = tempfile::tempdir()?;
    let roles = [
        "selected-lock:authored",
        "selected-lock:generated",
        "tool",
        "selected-project-at",
        "minecraft:version-json",
        "library:ordered-role",
        "mcp-output:renamed",
    ];
    let mut owners = Vec::new();
    for owner in ["sfm-4.34.0", "sfm-4.35.0"] {
        let root = fixture.path().join(owner);
        fs::create_dir(&root)?;
        let mut files = Vec::new();
        for (index, role) in roles.iter().enumerate() {
            let path = root.join(index.to_string());
            fs::write(&path, role.as_bytes())?;
            files.push(captured(&path, role)?);
        }
        owners.push(files);
    }
    let baseline = fingerprint(&owners[0], "full SDK image A", "filtered trust A", 17);
    assert_eq!(
        baseline,
        fingerprint(&owners[1], "full SDK image A", "filtered trust A", 17)
    );
    assert_ne!(
        baseline,
        fingerprint(&owners[0], "full SDK image B", "filtered trust A", 17)
    );
    assert_ne!(
        baseline,
        fingerprint(&owners[0], "full SDK image A", "filtered trust B", 17)
    );
    assert_ne!(
        baseline,
        fingerprint(&owners[0], "full SDK image A", "filtered trust A", 21)
    );
    for (index, role) in roles.iter().enumerate() {
        let path = fixture.path().join("sfm-4.35.0").join(index.to_string());
        fs::write(&path, format!("changed {role}"))?;
        let mut files = Vec::new();
        for (i, role) in roles.iter().enumerate() {
            files.push(captured(
                &fixture.path().join("sfm-4.35.0").join(i.to_string()),
                role,
            )?);
        }
        assert_ne!(
            baseline,
            fingerprint(&files, "full SDK image A", "filtered trust A", 17),
            "{role}"
        );
        fs::write(&path, role.as_bytes())?;
    }
    Ok(())
}

#[test]
fn linux_forge_held_input_rejects_same_length_mutation_links_modes_and_ancestor_replacement()
-> Result<()> {
    for change in ["bytes", "symlink", "hardlink", "parent", "mode"] {
        let fixture = tempfile::tempdir()?;
        let directory = fixture.path().join("inputs");
        fs::create_dir(&directory)?;
        let path = directory.join("tool.jar");
        fs::write(&path, b"original")?;
        let held = captured(&path, "tool")?;
        match change {
            "bytes" => fs::write(&path, b"modified")?,
            "symlink" => {
                fs::rename(&path, directory.join("retained.jar"))?;
                symlink(directory.join("retained.jar"), &path)?;
            }
            "hardlink" => fs::hard_link(&path, directory.join("alias.jar"))?,
            "parent" => {
                fs::rename(&directory, fixture.path().join("retained"))?;
                fs::create_dir(&directory)?;
                fs::write(&path, b"original")?;
            }
            "mode" => fs::set_permissions(&path, fs::Permissions::from_mode(0o666))?,
            _ => unreachable!(),
        }
        assert!(held.recheck().is_err(), "{change}");
    }
    Ok(())
}
