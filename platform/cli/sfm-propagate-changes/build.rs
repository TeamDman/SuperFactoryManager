use std::env;
use std::process::Command;
use std::time::SystemTime;
use std::time::UNIX_EPOCH;

fn main() {
    add_build_script_inputs();
    add_exe_resources();
    add_git_revision();
    add_build_timestamp();
}

/// Re-run the build script when normal binary inputs change so embedded build metadata stays fresh.
fn add_build_script_inputs() {
    println!("cargo:rerun-if-changed=build.rs");
    println!("cargo:rerun-if-changed=Cargo.toml");
    println!("cargo:rerun-if-changed=src");
}

/// Embeds Windows resources (like application icon) into the executable.
fn add_exe_resources() {
    println!("cargo:rerun-if-changed=resources");

    embed_resource::compile("resources/app.rc", embed_resource::NONE)
        .manifest_required()
        .expect("failed to embed resources");
}

/// In your code you can now access git revision using
/// ```rust
/// let git_rev = option_env!("GIT_REVISION").unwrap_or("unknown");
/// ```
fn add_git_revision() {
    add_git_revision_inputs();

    // `cargo install --path` builds from a temporary source copy without `.git`. The installer
    // captures its worktree revision before Cargo makes that copy and supplies this narrow override.
    let rev = install_git_revision_override().unwrap_or_else(|| {
        git_output(&["rev-parse", "--short", "HEAD"]).unwrap_or_else(|| "unknown".to_string())
    });

    println!("cargo:rustc-env=GIT_REVISION={rev}");
}

/// Re-run the build script when the current git revision changes.
fn add_git_revision_inputs() {
    println!("cargo:rerun-if-env-changed=SFM_PROPAGATE_CHANGES_INSTALL_GIT_REVISION");

    if let Some(head_path) = git_output(&["rev-parse", "--git-path", "HEAD"]) {
        println!("cargo:rerun-if-changed={head_path}");
    }

    if let Some(head_ref) = git_output(&["symbolic-ref", "--quiet", "HEAD"])
        && let Some(head_ref_path) = git_output(&["rev-parse", "--git-path", &head_ref])
    {
        println!("cargo:rerun-if-changed={head_ref_path}");
    }
}

fn install_git_revision_override() -> Option<String> {
    let value = env::var_os("SFM_PROPAGATE_CHANGES_INSTALL_GIT_REVISION")?;
    let value = value.into_string().unwrap_or_else(|_| {
        panic!("SFM_PROPAGATE_CHANGES_INSTALL_GIT_REVISION must be valid Unicode")
    });
    assert!(
        (7..=64).contains(&value.len())
            && value
                .bytes()
                .all(|byte| byte.is_ascii_digit() || (b'a'..=b'f').contains(&byte)),
        "SFM_PROPAGATE_CHANGES_INSTALL_GIT_REVISION must contain 7 to 64 lowercase hexadecimal characters"
    );
    Some(value)
}

fn git_output(args: &[&str]) -> Option<String> {
    Command::new("git")
        .args(args)
        .output()
        .ok()
        .and_then(|o| o.status.success().then_some(o.stdout))
        .and_then(|v| String::from_utf8(v).ok())
        .map(|s| s.trim().to_string())
        .filter(|s| !s.is_empty())
}

/// Capture build time as a UTC instant so the runtime can render it in the user's local timezone.
fn add_build_timestamp() {
    let timestamp = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .expect("system clock should be after Unix epoch")
        .as_secs();

    println!("cargo:rustc-env=BUILD_TIMESTAMP_UNIX={timestamp}");
}
