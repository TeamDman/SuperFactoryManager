use facet::Facet;

/// JDK discovery and selection commands
#[derive(Facet, Debug)]
#[repr(u8)]
pub enum JdkCommand {
    /// List JDKs discovered by the clean-slate toolchain
    List,
}

impl JdkCommand {
    /// # Errors
    ///
    /// This function will return an error if JDK discovery fails.
    pub fn invoke(self) -> eyre::Result<()> {
        match self {
            JdkCommand::List => {
                let jdks = crate::jdk::list_jdks();
                if jdks.is_empty() {
                    println!("No JDKs discovered.");
                    return Ok(());
                }

                println!(
                    "{:<6} {:<5} {:<12} {:<16} Home",
                    "Java", "JBR", "Source", "Javac"
                );
                for jdk in jdks {
                    let home = jdk
                        .home
                        .as_ref()
                        .map_or_else(|| "<PATH>".to_string(), |home| home.display().to_string());
                    println!(
                        "{:<6} {:<5} {:<12} {:<16} {}",
                        jdk.major_version,
                        if jdk.is_jbr { "yes" } else { "no" },
                        jdk.source,
                        jdk.javac_executable.display(),
                        home
                    );
                }
                Ok(())
            }
        }
    }
}
