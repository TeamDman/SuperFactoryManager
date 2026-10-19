//! Synthetic input/capability cases; no actual SDK, Java tool or HTTP request.

use super::ContentHash;
use super::ContentHashAlgorithm;
use super::DirectoryLeases;
use super::ForgePreparationInputRequest;
use super::ForgePreparationInputs;
use super::HeldForgePreparationInput;
use super::MAX_INPUT_FILES;
use super::append;
use super::environment_is_clean;
use super::finish_fingerprint;
use super::recipe_basis;
use super::sha256;
use super::validate_selected_lock;
use crate::cancellation::CancellationToken;
use eyre::Result;
use sha2::Digest as _;
use sha2::Sha256;
use std::fs;
use std::os::windows::fs::OpenOptionsExt as _;
use std::path::Path;

fn payloads(kind: &str) -> Vec<(&'static str, &'static str, &'static [u8])> {
    let mut inputs: Vec<(&str, &str, &[u8])> = vec![
        (
            "selected-lock:authored",
            "core/schema-4.json",
            b"{\"schema\":4}\n",
        ),
        (
            "selected-lock:generated",
            "project/schema-4.json",
            b"{\"schema\":4}\n",
        ),
        (
            "minecraft:version-json",
            "minecraft/version.json",
            b"actual parent fixture",
        ),
        (
            "artifact:tool-fart",
            "maven/fart.jar",
            b"actual tool fixture",
        ),
    ];
    if kind == "mcp" {
        inputs.extend([
            (
                "artifact:mcp-config",
                "maven/config.zip",
                b"actual config".as_slice(),
            ),
            (
                "minecraft:client",
                "minecraft/client.jar",
                b"actual client".as_slice(),
            ),
            (
                "library:org/example/library.jar",
                "libraries/library.jar",
                b"actual library".as_slice(),
            ),
        ]);
    } else {
        inputs.extend([
            (
                "mcp-output:patch/joined-patched-sources.jar",
                "mcp/patch/sources.jar",
                b"actual patched sources".as_slice(),
            ),
            (
                "mcp-output:mergeMappings/output.tsrg",
                "mcp/mergeMappings/output.tsrg",
                b"actual mappings".as_slice(),
            ),
            (
                "mcp-output:rename/output.jar",
                "mcp/rename/output.jar",
                b"actual renamed classes".as_slice(),
            ),
            (
                "selected-project-at",
                "project/at.cfg",
                b"public fixture.Field".as_slice(),
            ),
        ]);
    }
    inputs
}

fn captured_fixture(
    root: &Path,
    kind: &str,
    changed_role: Option<&str>,
) -> Result<(DirectoryLeases, Vec<HeldForgePreparationInput>)> {
    let mut leases = DirectoryLeases::default();
    let mut files = Vec::new();
    for (role, relative, original) in payloads(kind) {
        let path = root.join(relative);
        fs::create_dir_all(path.parent().unwrap())?;
        let bytes = if changed_role == Some(role) {
            b"different consumed bytes".as_slice()
        } else {
            original
        };
        fs::write(&path, bytes)?;
        let request = ForgePreparationInputRequest::new(role, path);
        let captured =
            HeldForgePreparationInput::capture(&request, &mut leases, &CancellationToken::new())?
                .ok_or_else(|| eyre::eyre!("Synthetic input lease was unavailable"))?;
        files.push(captured.input);
    }
    Ok((leases, files))
}

fn actual_key(kind: &str, files: &[HeldForgePreparationInput]) -> String {
    let mut basis = Sha256::new();
    recipe_basis(
        &mut basis,
        kind,
        "1.19.2",
        "net.minecraftforge:forge:1.19.2-43.4.0",
        17,
        Some("official"),
        Some("1.19.2"),
    );
    append(&mut basis, b"same-selected-authored-lock-path");
    append(&mut basis, b"same-selected-AT-role-and-template-mode");
    finish_fingerprint(
        basis,
        kind,
        "actual-sdk-fixture",
        "held-image-fixture",
        files,
    )
}

#[test]
fn consumed_keys_ignore_project_cache_roots_and_unselected_sfm_body_edits() -> Result<()> {
    let fixture = tempfile::tempdir()?;
    for kind in ["mcp", "forge"] {
        let first = fixture.path().join(kind).join("source-generation-a");
        let second = fixture.path().join(kind).join("source-generation-b");
        let (_first_leases, first_files) = captured_fixture(&first, kind, None)?;
        let (_second_leases, second_files) = captured_fixture(&second, kind, None)?;
        let body = first.join("project/src/main/java/SFM.java");
        fs::create_dir_all(body.parent().unwrap())?;
        fs::write(&body, b"class SFM {}\n")?;
        let before = actual_key(kind, &first_files);
        fs::write(&body, b"class SFM {} // body-only edit\n")?;
        assert_eq!(actual_key(kind, &first_files), before);
        assert_eq!(actual_key(kind, &second_files), before);
    }
    Ok(())
}

#[test]
fn consumed_keys_change_for_actual_at_mcp_tool_library_or_config_bytes() -> Result<()> {
    let fixture = tempfile::tempdir()?;
    for (case, kind, role) in [
        ("at", "forge", "selected-project-at"),
        ("mcp", "forge", "mcp-output:rename/output.jar"),
        ("tool", "mcp", "artifact:tool-fart"),
        ("library", "mcp", "library:org/example/library.jar"),
        ("config", "mcp", "artifact:mcp-config"),
        ("lock", "mcp", "selected-lock:authored"),
    ] {
        let root = fixture.path().join(case);
        let (_first_leases, first) = captured_fixture(&root.join("before"), kind, None)?;
        let (_second_leases, second) = captured_fixture(&root.join("after"), kind, Some(role))?;
        assert_ne!(
            actual_key(kind, &first),
            actual_key(kind, &second),
            "{role}"
        );
    }
    Ok(())
}

#[test]
fn consumed_key_frames_the_complete_set_and_semantic_roles() -> Result<()> {
    let fixture = tempfile::tempdir()?;
    let (_leases, mut files) = captured_fixture(fixture.path(), "forge", None)?;
    let original = actual_key("forge", &files);
    files.reverse();
    assert_eq!(
        actual_key("forge", &files),
        original,
        "capture order is not semantic order"
    );
    let removed = files.pop().unwrap();
    assert_ne!(
        actual_key("forge", &files),
        original,
        "missing role must change key"
    );
    files.push(removed);
    files[0].role.push_str("-different-role");
    assert_ne!(
        actual_key("forge", &files),
        original,
        "role names are framed"
    );
    Ok(())
}

#[test]
fn selected_lock_requires_current_raw_source_and_output_bytes() -> Result<()> {
    let selected = b"{\"schema\":4}\n";
    let expected = sha256(selected);
    validate_selected_lock(selected, selected, &expected)?;
    let whitespace_change = b"{\"schema\":4}\n \n";
    for current in [whitespace_change.as_slice(), b"stale source".as_slice()] {
        assert!(validate_selected_lock(current, selected, &expected).is_err());
        assert!(validate_selected_lock(current, current, &expected).is_err());
    }
    Ok(())
}

#[test]
fn held_inputs_enforce_pins_and_refuse_existing_or_later_writers() -> Result<()> {
    use windows::Win32::Storage::FileSystem::{
        FILE_SHARE_DELETE, FILE_SHARE_READ, FILE_SHARE_WRITE,
    };
    let fixture = tempfile::tempdir()?;
    let path = fixture.path().join("input.jar");
    let raw = b"actual source bytes";
    fs::write(&path, raw)?;
    let writer = fs::OpenOptions::new()
        .write(true)
        .share_mode((FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE).0)
        .open(&path)?;
    let request = ForgePreparationInputRequest::new("tool", path.clone())
        .pinned(ContentHash::from_bytes(raw, ContentHashAlgorithm::Sha1));
    let mut leases = DirectoryLeases::default();
    assert!(
        HeldForgePreparationInput::capture(&request, &mut leases, &CancellationToken::new())?
            .is_none()
    );
    drop(writer);
    let captured =
        HeldForgePreparationInput::capture(&request, &mut leases, &CancellationToken::new())?
            .ok_or_else(|| eyre::eyre!("Synthetic file lease was unavailable"))?;
    assert!(fs::write(&path, b"changed").is_err());
    assert!(fs::rename(&path, fixture.path().join("renamed.jar")).is_err());
    captured.input.recheck()?;
    drop(captured);
    fs::write(&path, b"changed")?;
    assert!(
        HeldForgePreparationInput::capture(&request, &mut leases, &CancellationToken::new())
            .is_err()
    );
    fs::write(path, raw)?;
    Ok(())
}

#[test]
fn optional_admission_declines_bounded_capacity_and_all_java_overrides() {
    let owner = ForgePreparationInputs {
        fingerprint: String::new(),
        kind: "mcp".to_owned(),
        sdk_identity: "fixture".to_owned(),
        sdk_image: None,
        files: Vec::new(),
        directories: DirectoryLeases::default(),
        at: None,
    };
    let requests = (0..=MAX_INPUT_FILES)
        .map(|index| {
            ForgePreparationInputRequest::new(
                format!("role-{index}"),
                std::path::PathBuf::from(format!(r"C:\fixture\{index}\input.jar")),
            )
        })
        .collect::<Vec<_>>();
    assert!(!owner.capacity_allows(&requests));
    assert!(environment_is_clean(|_| None));
    for name in [
        "JAVA_TOOL_OPTIONS",
        "JDK_JAVA_OPTIONS",
        "_JAVA_OPTIONS",
        "JDK_ALTERNATE_VM",
    ] {
        assert!(
            !environment_is_clean(|candidate| (candidate == name).then(std::ffi::OsString::new)),
            "present empty {name}"
        );
        assert!(
            !environment_is_clean(
                |candidate| (candidate == name).then(|| std::ffi::OsString::from("value"))
            ),
            "present {name}"
        );
    }
}

fn recapture_existing_fixture(
    root: &Path,
    kind: &str,
) -> Result<(DirectoryLeases, Vec<HeldForgePreparationInput>)> {
    let mut leases = DirectoryLeases::default();
    let mut files = Vec::new();
    for (role, relative, _) in payloads(kind) {
        let request = ForgePreparationInputRequest::new(role, root.join(relative));
        let captured =
            HeldForgePreparationInput::capture(&request, &mut leases, &CancellationToken::new())?
                .ok_or_else(|| eyre::eyre!("Synthetic input lease was unavailable"))?;
        files.push(captured.input);
    }
    Ok((leases, files))
}

fn produce_fixture_outputs(
    root: &Path,
    at: &[u8],
    count: &std::cell::Cell<usize>,
) -> Result<Vec<(&'static str, std::path::PathBuf)>> {
    super::super::make_directory(root)?;
    count.set(count.get() + 1);
    ["classes.jar", "sources.jar", "mappings.tsrg"]
        .into_iter()
        .map(|name| {
            let path = root.join(name);
            let mut bytes = name.as_bytes().to_vec();
            bytes.extend_from_slice(at);
            fs::write(&path, bytes)?;
            Ok((name, path))
        })
        .collect()
}

#[test]
fn actual_at_change_rekeys_store_decision_and_preserves_previous_entry() -> Result<()> {
    use super::super::entry_directories;
    use super::super::make_directory;
    use super::super::publish;
    use super::super::restore;

    let fixture = tempfile::tempdir()?;
    let forge_root = fixture.path().join("forge-current-inputs");
    let (old_leases, old_inputs) = captured_fixture(&forge_root, "forge", None)?;
    let old_key = actual_key("forge", &old_inputs);
    let (_mcp_leases, mcp_inputs) =
        captured_fixture(&fixture.path().join("mcp-inputs"), "mcp", None)?;
    let mcp_key = actual_key("mcp", &mcp_inputs);
    let at_path = forge_root.join("project/at.cfg");
    let old_at = fs::read(&at_path)?;
    let produced = std::cell::Cell::new(0);
    let old_outputs =
        produce_fixture_outputs(&fixture.path().join("old-produced"), &old_at, &produced)?;
    let old_store = make_directory(
        &fixture
            .path()
            .join("store")
            .join(old_key.trim_start_matches("sha256:")),
    )?;
    publish(&old_store, &old_key, &old_outputs, || Ok(()))?;
    let old_entry = entry_directories(&old_store)?.unwrap().pop().unwrap();
    let old_state = fs::read(old_entry.join("inputs.sha256"))?;
    let old_payload = old_outputs
        .iter()
        .map(|(name, _)| Ok((*name, fs::read(old_entry.join("payload").join(name))?)))
        .collect::<Result<Vec<_>>>()?;
    drop(old_inputs);
    drop(old_leases);

    fs::write(&at_path, b"public changed.Field")?;
    let (_new_leases, new_inputs) = recapture_existing_fixture(&forge_root, "forge")?;
    let new_key = actual_key("forge", &new_inputs);
    assert_ne!(new_key, old_key);
    assert_eq!(actual_key("mcp", &mcp_inputs), mcp_key);
    let new_store = make_directory(
        &fixture
            .path()
            .join("store")
            .join(new_key.trim_start_matches("sha256:")),
    )?;
    let destination = make_directory(&fixture.path().join("new-project"))?;
    let selected = old_outputs
        .iter()
        .map(|(name, _)| (*name, destination.join(name)))
        .collect::<Vec<_>>();
    assert!(!restore(
        &new_store,
        &new_key,
        &destination,
        &selected,
        || Ok(())
    )?);
    assert!(!restore(
        &old_store,
        &new_key,
        &destination,
        &selected,
        || Ok(())
    )?);
    assert!(selected.iter().all(|(_, path)| !path.exists()));
    assert_eq!(produced.get(), 1);

    let current_at = fs::read(&at_path)?;
    let new_outputs =
        produce_fixture_outputs(&fixture.path().join("new-produced"), &current_at, &produced)?;
    publish(&new_store, &new_key, &new_outputs, || Ok(()))?;
    assert_eq!(produced.get(), 2);
    assert!(restore(
        &new_store,
        &new_key,
        &destination,
        &selected,
        || Ok(())
    )?);
    for ((_, generated), (_, materialized)) in new_outputs.iter().zip(selected) {
        assert_eq!(fs::read(generated)?, fs::read(materialized)?);
    }
    assert_eq!(fs::read(old_entry.join("inputs.sha256"))?, old_state);
    for (name, bytes) in old_payload {
        assert_eq!(fs::read(old_entry.join("payload").join(name))?, bytes);
    }
    assert_eq!(entry_directories(&old_store)?.unwrap(), vec![old_entry]);
    assert_eq!(entry_directories(&new_store)?.unwrap().len(), 1);
    Ok(())
}
