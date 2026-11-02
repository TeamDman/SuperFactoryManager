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
    pub fn invoke(self) -> eyre::Result<()> {
        let record = crate::jdk_preferences::add(&self.id, &self.home)?;
        stdout_line(format!(
            "{} -> {} ({} {} {}; locator only, not a lockfile pin)",
            record.id, record.home, record.vendor, record.version, record.build
        ))
    }
}
