use crate::terminal_output::stdout_line;
use facet::Facet;

/// Arguments for listing discovered JDKs.
#[derive(Facet, Debug)]
pub struct JdkListArgs;

impl JdkListArgs {
    /// # Errors
    ///
    /// Returns an error if JDK discovery output cannot be written.
    pub fn invoke(self) -> eyre::Result<()> {
        let jdks = crate::jdk::list_jdks()?;
        if jdks.is_empty() {
            stdout_line("No JDKs discovered.")?;
            return Ok(());
        }

        stdout_line(format!(
            "{:<6} {:<5} {:<12} {:<16} Home",
            "Java", "JBR", "Source", "Javac"
        ))?;
        for jdk in jdks {
            let home = jdk
                .home
                .as_ref()
                .map_or_else(|| "<PATH>".to_string(), |home| home.display().to_string());
            stdout_line(format!(
                "{:<6} {:<5} {:<12} {:<16} {}",
                jdk.major_version,
                if jdk.is_jbr { "yes" } else { "no" },
                jdk.source,
                jdk.javac_executable.display(),
                home
            ))?;
        }
        Ok(())
    }
}
