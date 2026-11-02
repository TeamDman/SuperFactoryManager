use crate::terminal_output::stdout_line;
use facet::Facet;
use figue as args;

/// Remove only the machine-local mapping; keep the JDK installation.
#[derive(Facet, Debug)]
pub struct JdkRemoveArgs {
    #[facet(args::positional)]
    pub id: String,
}

impl JdkRemoveArgs {
    /// # Errors
    /// Fails if preferences are corrupt or the logical ID is absent.
    pub fn invoke(self) -> eyre::Result<()> {
        let removed = crate::jdk_preferences::remove(&self.id)?;
        stdout_line(format!(
            "Removed {} mapping; JDK files retained at {}",
            removed.id, removed.home
        ))
    }
}
