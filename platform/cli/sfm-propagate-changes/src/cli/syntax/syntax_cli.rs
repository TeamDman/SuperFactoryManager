use super::SyntaxAuditArgs;
use super::SyntaxHighlightArgs;
use super::SyntaxServeArgs;
use crate::cancellation::CancellationToken;
use crate::cli::output::CliOutput;
use facet::Facet;
use figue::{self as args};

#[derive(Facet, Debug)]
pub struct SyntaxArgs {
    #[facet(args::subcommand)]
    pub command: SyntaxCommand,
}

impl SyntaxArgs {
    /// # Errors
    ///
    /// Returns an error when highlighting or worker startup fails.
    pub fn invoke(self, cancellation_token: &CancellationToken) -> eyre::Result<CliOutput> {
        self.command.invoke(cancellation_token)
    }
}

#[derive(Facet, Debug)]
#[repr(u8)]
pub enum SyntaxCommand {
    /// Inventory repository file types and probe representative source highlighting.
    Audit(SyntaxAuditArgs),
    /// Highlight exact UTF-8 source supplied on stdin.
    Highlight(SyntaxHighlightArgs),
    /// Serve reusable framed syntax-highlight requests on stdin/stdout.
    Serve(SyntaxServeArgs),
}

impl SyntaxCommand {
    /// # Errors
    ///
    /// Returns an error when the selected syntax operation fails.
    pub fn invoke(self, cancellation_token: &CancellationToken) -> eyre::Result<CliOutput> {
        match self {
            Self::Audit(args) => args.invoke(cancellation_token),
            Self::Highlight(args) => args.invoke(cancellation_token),
            Self::Serve(args) => args.invoke(cancellation_token),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::cli::Cli;
    use crate::cli::Command;

    fn parse(arguments: &[&str]) -> Cli {
        figue::from_slice::<Cli>(arguments)
            .into_result()
            .expect("syntax CLI should parse")
            .get_silent()
    }

    #[test]
    fn syntax_cli_help_surface_parses_highlight_and_serve() {
        let audit = parse(&["syntax", "audit", "--root", "."]);
        assert!(matches!(
            audit.command,
            Command::Syntax(SyntaxArgs {
                command: SyntaxCommand::Audit(_)
            })
        ));
        let highlight = parse(&["syntax", "highlight", "--language", "java", "--stdin"]);
        assert!(matches!(
            highlight.command,
            Command::Syntax(SyntaxArgs {
                command: SyntaxCommand::Highlight(SyntaxHighlightArgs {
                    language,
                    stdin: true,
                    ..
                })
            }) if language == "java"
        ));

        let serve = parse(&["syntax", "serve"]);
        assert!(matches!(
            serve.command,
            Command::Syntax(SyntaxArgs {
                command: SyntaxCommand::Serve(_)
            })
        ));
    }
}
