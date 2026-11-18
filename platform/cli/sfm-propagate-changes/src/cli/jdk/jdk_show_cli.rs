use crate::jdk_preferences::JdkPreferenceRegistry;
use crate::paths::AppHome;
use crate::terminal_output::stdout_line;
use facet::Facet;
use figue as args;

/// Show a machine-local JDK registration without claiming pin compatibility.
#[derive(Facet, Debug)]
pub struct JdkShowArgs {
    #[facet(args::positional)]
    pub id: String,
}

impl JdkShowArgs {
    /// # Errors
    /// Fails if preferences are corrupt or the logical ID is absent.
    pub fn invoke(self, app_home: &AppHome) -> eyre::Result<()> {
        let record = JdkPreferenceRegistry::new(app_home)
            .show(&self.id)?
            .ok_or_else(|| eyre::eyre!("No JDK preference named `{}`", self.id))?;
        stdout_line(format!(
            "{} -> {}\nJava {} | {} | {}\nLocator only; lockfile compatibility is checked separately.",
            record.id, record.home, record.version, record.vendor, record.build
        ))
    }
}
