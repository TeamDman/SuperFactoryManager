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
        let project_classes_dir = minecraft_dir
            .join("build")
            .join("sfm-toolchain")
            .join("project");
        let classes_dirs = client_hotswap_class_directories(&project_classes_dir)?;

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
            &class_selector,
            &classes_dirs,
        )
    }
}

fn client_hotswap_class_directories(project_dir: &Path) -> eyre::Result<Vec<PathBuf>> {
    let candidates = [
        project_dir.join("classes"),
        project_dir.join("gametest").join("classes"),
    ];
    let classes_dirs = candidates
        .into_iter()
        .filter(|path| path.is_dir())
        .collect::<Vec<_>>();
    if classes_dirs.is_empty() {
        eyre::bail!(
            "No compiled class directories active in the interactive client exist under {}",
            project_dir.display()
        );
    }
    Ok(classes_dirs)
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
    class_prefix: &str,
    classes_dirs: &[PathBuf],
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
        .arg(class_prefix)
        .args(classes_dirs);
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

#[cfg(test)]
mod tests {
    use super::client_hotswap_class_directories;
    use std::fs;

    #[test]
    fn discovers_every_class_directory_active_in_an_interactive_client() {
        let temp = tempfile::tempdir().expect("tempdir");
        let project = temp.path().join("project");
        let main = project.join("classes");
        let gametest = project.join("gametest").join("classes");
        let datagen = project.join("datagen").join("classes");
        let test = project.join("test").join("classes");
        for directory in [&main, &gametest, &datagen, &test] {
            fs::create_dir_all(directory).expect("class directory");
        }

        assert_eq!(
            client_hotswap_class_directories(&project).expect("interactive class directories"),
            vec![main, gametest]
        );
    }

    #[test]
    fn permits_a_client_without_the_optional_gametest_source_set() {
        let temp = tempfile::tempdir().expect("tempdir");
        let project = temp.path().join("project");
        let main = project.join("classes");
        fs::create_dir_all(&main).expect("main class directory");

        assert_eq!(
            client_hotswap_class_directories(&project).expect("main class directory"),
            vec![main]
        );
    }

    #[test]
    fn rejects_missing_interactive_client_outputs() {
        let temp = tempfile::tempdir().expect("tempdir");

        let error = client_hotswap_class_directories(temp.path())
            .expect_err("missing class directories should fail");

        assert!(error.to_string().contains("No compiled class directories"));
    }
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
