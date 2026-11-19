use crate::jdk_preferences::JdkPreferenceRegistry;
use crate::paths::AppHome;
use crate::paths::InvocationDirectory;
use crate::terminal_output::stdout_line;
use facet::Facet;
use figue as args;
use std::path::PathBuf;

/// Register an installed machine-local JDK under a logical name.
#[derive(Facet, Debug)]
pub struct JdkAddArgs {
    /// Stable logical name, for example jbr17.
    #[facet(args::named)]
    pub id: String,
    /// Existing JDK installation directory containing bin/java and bin/javac.
    #[facet(args::named)]
    pub home: PathBuf,
}

impl JdkAddArgs {
    /// # Errors
    /// Fails if the JDK cannot be inspected or the name conflicts.
    pub fn invoke_in(
        self,
        invocation_dir: &InvocationDirectory<'_>,
        app_home: &AppHome,
    ) -> eyre::Result<()> {
        let home = invocation_dir.resolve(&self.home)?;
        let record = JdkPreferenceRegistry::new(app_home).add(&self.id, &home)?;
        stdout_line(format!(
            "{} -> {} ({} {} {}; locator only, not a lockfile pin)",
            record.id, record.home, record.vendor, record.version, record.build
        ))
    }
}

#[cfg(test)]
mod tests {
    use super::JdkAddArgs;
    use crate::paths::AppHome;
    use crate::paths::InvocationDirectory;
    use std::path::PathBuf;

    #[test]
    fn relative_jdk_home_is_checked_from_the_invocation_directory() -> eyre::Result<()> {
        let fixture = tempfile::tempdir()?;
        let invocation_dir = InvocationDirectory::new(fixture.path())?;
        let app_home = AppHome(fixture.path().join("settings"));
        let error = JdkAddArgs {
            id: "jbr17".to_owned(),
            home: PathBuf::from("missing-jdk"),
        }
        .invoke_in(&invocation_dir, &app_home)
        .unwrap_err();
        assert!(error.to_string().contains("JDK home is not a directory"));
        assert!(
            error
                .to_string()
                .contains(&fixture.path().join("missing-jdk").display().to_string())
        );
        assert!(!app_home.exists());
        Ok(())
    }
}
