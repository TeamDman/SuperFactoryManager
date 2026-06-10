use std::{
    process::Command,
    time::{SystemTime, UNIX_EPOCH},
};

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

    // Try to get a short git revision; on failure, set to "unknown".
    let rev =
        git_output(&["rev-parse", "--short", "HEAD"]).unwrap_or_else(|| "unknown".to_string());

    println!("cargo:rustc-env=GIT_REVISION={rev}");
}

/// Re-run the build script when the current git revision changes.
fn add_git_revision_inputs() {
    if let Some(head_path) = git_output(&["rev-parse", "--git-path", "HEAD"]) {
        println!("cargo:rerun-if-changed={head_path}");
    }

    if let Some(head_ref) = git_output(&["symbolic-ref", "--quiet", "HEAD"]) {
        if let Some(head_ref_path) = git_output(&["rev-parse", "--git-path", &head_ref]) {
            println!("cargo:rerun-if-changed={head_ref_path}");
        }
    }
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
