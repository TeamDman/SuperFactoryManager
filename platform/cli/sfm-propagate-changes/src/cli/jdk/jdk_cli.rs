use super::{JdkAddArgs, JdkListArgs, JdkRemoveArgs, JdkShowArgs};
use facet::Facet;
use figue as args;

/// Arguments for JDK discovery and machine-local preferences.
#[derive(Facet, Debug)]
pub struct JdkArgs {
    /// JDK subcommand.
    #[facet(args::subcommand)]
    pub command: JdkCommand,
}

impl JdkArgs {
    /// # Errors
    /// Returns an error if the selected JDK command fails.
    pub fn invoke(self) -> eyre::Result<()> {
        self.command.invoke()
    }
}

/// JDK discovery and machine-local preference commands.
#[derive(Facet, Debug)]
#[repr(u8)]
pub enum JdkCommand {
    /// Register a real JDK home by logical ID; this is not a lockfile pin.
    Add(JdkAddArgs),
    /// List registered preferences and discovered JDKs.
    List(JdkListArgs),
    /// Remove a preference mapping, retaining the installed JDK.
    Remove(JdkRemoveArgs),
    /// Show one stored JDK preference.
    Show(JdkShowArgs),
}

impl JdkCommand {
    /// # Errors
    /// This function returns an error if JDK inspection or storage fails.
    pub fn invoke(self) -> eyre::Result<()> {
        match self {
            Self::Add(args) => args.invoke(),
            Self::List(args) => args.invoke(),
            Self::Remove(args) => args.invoke(),
            Self::Show(args) => args.invoke(),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::{JdkArgs, JdkCommand};

    #[test]
    fn parses_jdk_preference_commands() {
        let add = figue::from_slice::<JdkArgs>(&["add", "--id", "jbr17", "--home", "C:/JDK"])
            .into_result()
            .expect("jdk add should parse")
            .get_silent();
        assert!(matches!(add.command, JdkCommand::Add(_)));
        for command in ["list", "remove", "show"] {
            let argv = if command == "list" {
                vec![command]
            } else {
                vec![command, "jbr17"]
            };
            figue::from_slice::<JdkArgs>(&argv)
                .into_result()
                .unwrap_or_else(|_| panic!("jdk {command} should parse"));
        }
    }
}
