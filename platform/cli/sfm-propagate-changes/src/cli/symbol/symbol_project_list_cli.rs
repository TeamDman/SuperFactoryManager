use crate::cancellation::CancellationToken;
use crate::cli::output::CliOutput;
use crate::java_analysis::JavaSourceWorkspace;
use crate::java_analysis::JavaSymbolGlob;
use crate::java_analysis::JavaSymbolIndex;
use crate::java_analysis::scan_generated_dependency_type;
use crate::paths::CacheHome;
use facet::Facet;
use figue::{self as args};
use std::path::Path;
use std::path::PathBuf;

/// List declarations in a generated source projection without a branch index.
#[derive(Facet, Debug)]
pub struct SymbolProjectListArgs {
    /// Optional case-sensitive glob over canonical symbol selectors (`*` and `?`).
    #[facet(default, args::positional)]
    pub pattern: Option<String>,
    /// Generated standalone Gradle project root.
    #[facet(args::named)]
    pub project_root: PathBuf,
    /// Explicit exact-major JDK home, matching source build/run --java-home.
    #[facet(default, args::named)]
    pub java_home: Option<PathBuf>,
}

impl SymbolProjectListArgs {
    /// # Errors
    ///
    /// Returns an error when the generated root or Java source cannot be read.
    pub fn invoke_in(
        self,
        cancellation_token: &CancellationToken,
        invocation_dir: &Path,
    ) -> eyre::Result<CliOutput> {
        self.invoke_in_with_resolver(
            cancellation_token,
            invocation_dir,
            JavaSourceWorkspace::resolve_generated_project_with_java_home,
        )
    }

    fn invoke_in_with_resolver(
        self,
        cancellation_token: &CancellationToken,
        invocation_dir: &Path,
        resolve_workspace: impl FnOnce(&Path, Option<&Path>) -> eyre::Result<JavaSourceWorkspace>,
    ) -> eyre::Result<CliOutput> {
        cancellation_token.bail_if_cancelled()?;
        let project_root = if self.project_root.is_absolute() {
            self.project_root
        } else {
            invocation_dir.join(self.project_root)
        };
        let java_home = self.java_home.map(|home| {
            if home.is_absolute() {
                home
            } else {
                invocation_dir.join(home)
            }
        });
        let workspace = resolve_workspace(&project_root, java_home.as_deref())?;
        cancellation_token.bail_if_cancelled()?;
        let mut index = JavaSymbolIndex::build_definitions(&workspace)?;
        cancellation_token.bail_if_cancelled()?;
        let mut report = index.list(&JavaSymbolGlob::new(self.pattern));
        let mut dependency_incomplete = false;
        let exact_type = (!report.pattern.contains(['*', '?', ' '])
            && report.pattern.contains('.'))
        .then(|| report.pattern.clone());
        if report.definitions.is_empty()
            && let Some(qualified_name) = exact_type
        {
            let cache_home = CacheHome::resolve()?;
            let scanned = scan_generated_dependency_type(
                &project_root,
                &cache_home.0,
                &qualified_name,
                cancellation_token,
            )?;
            dependency_incomplete = !scanned.complete;
            if !scanned.body.definitions.is_empty() {
                index = JavaSymbolIndex::build_with_dependencies(&workspace, &scanned.body, false)?;
                report = index.list(&JavaSymbolGlob::new(Some(qualified_name)));
            }
            report.diagnostics.extend(scanned.body.diagnostics);
        }
        // A missing project-selected JDK has no source span, so ordinary
        // symbol-list filtering would hide the reason external types are absent.
        report.diagnostics.extend(
            workspace
                .diagnostics
                .iter()
                .filter(|diagnostic| diagnostic.code == "java.jdk-source-unavailable")
                .cloned(),
        );
        report.diagnostics.sort();
        report.diagnostics.dedup();
        let exit_code = if dependency_incomplete {
            5
        } else {
            report.status()
        };
        Ok(CliOutput::facet_with_csv_and_status(
            report,
            |report| Ok(report.to_csv()),
            exit_code,
        ))
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::cli::output::OutputFormat;
    use crate::source_projection::core_catalog::write_project_catalog_fixture;
    use crate::toolchain_lockfile_schema::version::v4::ArtifactLockfileV4;
    use sha2::Digest as _;
    use sha2::Sha512;

    #[test]
    fn project_list_reports_missing_pinned_jdk_without_losing_project_symbols() {
        let temporary = tempfile::tempdir().expect("temporary generated project");
        let project_root = temporary
            .path()
            .join("platform/minecraft/projections/1.19.2");
        let main_root = project_root.join("src/main/java/example");
        let toolchain_root = project_root.join("gradle/java-toolchain/1.19.2");
        std::fs::create_dir_all(&main_root).expect("main source root");
        std::fs::create_dir_all(&toolchain_root).expect("toolchain root");
        std::fs::write(
            project_root.join("settings.gradle"),
            "rootProject.name = 'sfm'\n",
        )
        .expect("settings.gradle");
        write_project_catalog_fixture(temporary.path(), "1.19.2", "1.19.2").unwrap();
        std::fs::write(
            toolchain_root.join("java-toolchain.gradle"),
            "JavaLanguageVersion.of(17)\n",
        )
        .expect("Java release");
        std::fs::write(
            main_root.join("Example.java"),
            "package example; public class Example { String name; }",
        )
        .expect("project source");
        let mut lockfile: ArtifactLockfileV4 = facet_json::from_str(include_str!(
            "../../../../../minecraft/sfm-toolchain.lock.json"
        ))
        .expect("checked-in v4 lockfile");
        let pin = &mut lockfile.jdk_pins.as_mut().expect("JDK pin catalog")[0];
        let artifact = &mut pin.artifacts[0];
        let platform = crate::jdk::host_jbrsdk_platform().expect("supported test platform");
        artifact.platform = platform.to_owned();
        artifact.url = format!(
            "https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-{}-{platform}-{}.zip",
            pin.version, pin.build
        );
        artifact.sha512 = format!(
            "{:x}",
            Sha512::digest(temporary.path().to_string_lossy().as_bytes())
        );
        std::fs::write(
            project_root.join("sfm-toolchain.lock.json"),
            lockfile.to_canonical_json().expect("pinned lockfile JSON"),
        )
        .expect("project lockfile");

        let output = SymbolProjectListArgs {
            pattern: Some("example.Example".to_owned()),
            project_root: project_root.clone(),
            java_home: None,
        }
        .invoke_in_with_resolver(
            &CancellationToken::new(),
            temporary.path(),
            |root, java_home| {
                assert!(java_home.is_none());
                JavaSourceWorkspace::resolve_generated_project_with_cache(
                    root,
                    &temporary.path().join("jdk-cache"),
                    &temporary.path().join("source-cache"),
                    None,
                )
            },
        )
        .expect("project symbol list");
        assert_eq!(output.exit_code(), 0);
        let json = output
            .render(Some(OutputFormat::Json), false)
            .expect("JSON report")
            .expect("typed report");
        assert!(json.contains("example.Example"));
        assert!(json.contains("java.jdk-source-unavailable"));
        assert!(json.contains("offline mode forbids downloading"));

        let missing_home = temporary.path().join("missing-explicit-jdk");
        let error = SymbolProjectListArgs {
            pattern: Some("example.Example".to_owned()),
            project_root,
            java_home: Some(missing_home),
        }
        .invoke_in_with_resolver(
            &CancellationToken::new(),
            temporary.path(),
            |root, java_home| {
                JavaSourceWorkspace::resolve_generated_project_with_cache(
                    root,
                    &temporary.path().join("jdk-cache"),
                    &temporary.path().join("source-cache"),
                    java_home,
                )
            },
        )
        .expect_err("explicit JDK home failure is an error");
        assert!(
            format!("{error:#}")
                .contains("explicit generated-project --java-home could not be used")
        );
    }

    #[test]
    fn project_list_resolves_relative_java_home_from_invocation_directory() {
        let temporary = tempfile::tempdir().expect("invocation directory");
        let error = SymbolProjectListArgs {
            pattern: None,
            project_root: PathBuf::from("platform/minecraft/projections/1.19.2"),
            java_home: Some(PathBuf::from("selected-jdk")),
        }
        .invoke_in_with_resolver(
            &CancellationToken::new(),
            temporary.path(),
            |root, java_home| {
                assert_eq!(
                    root,
                    temporary
                        .path()
                        .join("platform/minecraft/projections/1.19.2")
                );
                assert_eq!(
                    java_home,
                    Some(temporary.path().join("selected-jdk").as_path())
                );
                eyre::bail!("verified CLI paths")
            },
        )
        .expect_err("test resolver stops after checking paths");
        assert!(error.to_string().contains("verified CLI paths"));
    }
}
