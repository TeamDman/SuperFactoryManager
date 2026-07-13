use crate::branch_targets::select_single_worktree_target;
use crate::cancellation::CancellationToken;
use crate::cli::jar::JarBuildOptionsArgs;
use crate::jar_build::BuildMode;
use crate::jar_build::RunTestAction;
use crate::jar_build::RunTestCommand as JarBuildRunTestCommand;
use crate::jar_build::RunTestOptions;
use crate::jdk::resolve_java;
use eyre::Context;
use facet::Facet;
use figue as args;
use std::fs;
use std::path::Path;
use std::path::PathBuf;
use std::process::Command;

const DEFAULT_HOTSWAP_PORT: u16 = 5005;
const DEFAULT_CLASS_PREFIX: &str = "ca.teamdman.";
const HOTSWAP_HELPER_SOURCE: &str = include_str!("sfm_hotswap_helper.java");

/// Arguments for redefining classes in a running hotswap-enabled client.
#[derive(Facet, Debug, Clone)]
pub struct RunHotswapArgs {
    /// Build and compile options.
    #[facet(flatten)]
    pub options: JarBuildOptionsArgs,
    /// JDWP port exposed by `run client --hotswap`.
    #[facet(default, args::named)]
    pub port: Option<u16>,
    /// Only reload loaded classes whose binary name starts with this prefix.
    #[facet(default, args::named)]
    pub class_prefix: Option<String>,
    /// Only reload this exact loaded class binary name.
    #[facet(default, args::named)]
    pub class_name: Option<String>,
}

impl RunHotswapArgs {
    /// # Errors
    ///
    /// Returns an error if compilation fails, the helper cannot be built, or JDWP redefinition fails.
    pub fn invoke(self, cancellation_token: CancellationToken) -> eyre::Result<()> {
        let build_options = self.options.clone().into_options(BuildMode::Build)?;
        JarBuildRunTestCommand::new(
            build_options.clone(),
            RunTestOptions {
                action: RunTestAction::Compile,
                filter: None,
                no_capture: false,
            },
            cancellation_token,
        )
        .invoke()?;

        let target = select_single_worktree_target(&build_options.branch)?;
        let minecraft_dir = target
            .worktree_path
            .as_path()
            .join("platform")
            .join("minecraft");
        let classes_dir = minecraft_dir
            .join("build")
            .join("sfm-toolchain")
            .join("project")
            .join("classes");
        if !classes_dir.is_dir() {
            eyre::bail!(
                "Compiled main classes directory does not exist: {}",
                classes_dir.display()
            );
        }

        let helper_classes_dir = minecraft_dir
            .join("build")
            .join("sfm-toolchain")
            .join("run")
            .join("hotswap-helper")
            .join("classes");
        let java = resolve_java(build_options.java_home.as_deref(), 17)?;
        compile_hotswap_helper(&java, &helper_classes_dir)?;
        let class_selector = self
            .class_name
            .as_deref()
            .map(|class_name| format!("={class_name}"))
            .or_else(|| self.class_prefix.clone())
            .unwrap_or_else(|| DEFAULT_CLASS_PREFIX.to_string());
        run_hotswap_helper(
            &java,
            &helper_classes_dir,
            self.port.unwrap_or(DEFAULT_HOTSWAP_PORT),
            &classes_dir,
            &class_selector,
        )
    }
}

fn compile_hotswap_helper(
    java: &crate::jdk::ResolvedJava,
    helper_classes_dir: &Path,
) -> eyre::Result<()> {
    let helper_root = helper_classes_dir
        .parent()
        .ok_or_else(|| eyre::eyre!("Invalid hotswap helper output path"))?;
    fs::create_dir_all(helper_root).wrap_err_with(|| {
        format!(
            "Failed to create hotswap helper directory {}",
            helper_root.display()
        )
    })?;
    fs::create_dir_all(helper_classes_dir).wrap_err_with(|| {
        format!(
            "Failed to create hotswap helper classes directory {}",
            helper_classes_dir.display()
        )
    })?;
    let source_path = helper_root.join("SfmHotswapHelper.java");
    fs::write(&source_path, HOTSWAP_HELPER_SOURCE)
        .wrap_err_with(|| format!("Failed to write {}", source_path.display()))?;

    let mut command = Command::new(javac_executable(java));
    command
        .arg("--add-modules")
        .arg("jdk.jdi")
        .arg("-d")
        .arg(helper_classes_dir)
        .arg(&source_path);
    let output = command
        .output()
        .wrap_err("Failed to launch javac for hotswap helper")?;
    if !output.status.success() {
        eyre::bail!(
            "javac failed for hotswap helper with {}.\nstdout:\n{}\nstderr:\n{}",
            output.status,
            String::from_utf8_lossy(&output.stdout),
            String::from_utf8_lossy(&output.stderr)
        );
    }
    Ok(())
}

fn run_hotswap_helper(
    java: &crate::jdk::ResolvedJava,
    helper_classes_dir: &Path,
    port: u16,
    classes_dir: &Path,
    class_prefix: &str,
) -> eyre::Result<()> {
    let mut command = Command::new(&java.executable);
    command
        .arg("--add-modules")
        .arg("jdk.jdi")
        .arg("-cp")
        .arg(helper_classes_dir)
        .arg("SfmHotswapHelper")
        .arg("127.0.0.1")
        .arg(port.to_string())
        .arg(classes_dir)
        .arg(class_prefix);
    let output = command
        .output()
        .wrap_err("Failed to launch hotswap helper")?;
    print!("{}", String::from_utf8_lossy(&output.stdout));
    eprint!("{}", String::from_utf8_lossy(&output.stderr));
    if !output.status.success() {
        eyre::bail!("hotswap helper failed with {}", output.status);
    }
    Ok(())
}

fn javac_executable(java: &crate::jdk::ResolvedJava) -> PathBuf {
    java.home.as_ref().map_or_else(
        || PathBuf::from(if cfg!(windows) { "javac.exe" } else { "javac" }),
        |home| {
            home.join("bin")
                .join(if cfg!(windows) { "javac.exe" } else { "javac" })
        },
    )
}
