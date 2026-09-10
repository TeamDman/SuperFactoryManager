use crate::cancellation::CancellationToken;
use crate::cli::output::CliOutput;
use crate::syntax_highlight::DEFAULT_SYNTAX_HIGHLIGHT_MAX_SOURCE_BYTES;
use crate::syntax_highlight::DEFAULT_SYNTAX_HIGHLIGHT_MAX_SPANS;
use crate::syntax_highlight::SyntaxHighlightEngine;
use crate::syntax_highlight::SyntaxHighlightEngineLimits;
use crate::syntax_highlight::SyntaxHighlightRequest;
use facet::Facet;
use figue::{self as args};
use std::io::Read;

#[derive(Facet, Debug)]
pub struct SyntaxHighlightArgs {
    /// Enabled language id: java, rust, json, groovy, markdown, powershell, typescript, toml.
    #[facet(args::named)]
    pub language: String,
    /// Read the exact UTF-8 source document from stdin.
    #[facet(default, args::named)]
    pub stdin: bool,
    /// Optional request id for deterministic integration evidence.
    #[facet(default, args::named)]
    pub request_id: Option<u64>,
    /// Optional request generation for deterministic integration evidence.
    #[facet(default, args::named)]
    pub request_generation: Option<u64>,
    /// Optional origin id for deterministic integration evidence.
    #[facet(default, args::named)]
    pub origin_id: Option<String>,
    /// Optional origin generation for deterministic integration evidence.
    #[facet(default, args::named)]
    pub origin_generation: Option<u64>,
    /// Maximum number of highlighted spans accepted from this request.
    #[facet(default, args::named)]
    pub maximum_spans: Option<u64>,
}

impl SyntaxHighlightArgs {
    /// # Errors
    ///
    /// Returns an error when stdin is not selected/readable or engine setup
    /// fails. Request validation failures are typed command output.
    pub fn invoke(self, cancellation_token: &CancellationToken) -> eyre::Result<CliOutput> {
        self.invoke_with_reader(cancellation_token, std::io::stdin().lock())
    }

    /// Execute the direct command against an explicit reader for deterministic
    /// tests and adapters.
    ///
    /// # Errors
    ///
    /// Returns an error for invalid CLI mode, non-UTF-8/oversize input, or
    /// engine infrastructure failure.
    pub fn invoke_with_reader(
        self,
        cancellation_token: &CancellationToken,
        reader: impl Read,
    ) -> eyre::Result<CliOutput> {
        if !self.stdin {
            eyre::bail!("syntax highlight requires --stdin");
        }
        let mut source = String::new();
        reader
            .take((DEFAULT_SYNTAX_HIGHLIGHT_MAX_SOURCE_BYTES + 1) as u64)
            .read_to_string(&mut source)?;
        if source.len() > DEFAULT_SYNTAX_HIGHLIGHT_MAX_SOURCE_BYTES {
            eyre::bail!(
                "syntax stdin contains more than {} bytes",
                DEFAULT_SYNTAX_HIGHLIGHT_MAX_SOURCE_BYTES
            );
        }
        let maximum_spans = self
            .maximum_spans
            .map_or(Ok(DEFAULT_SYNTAX_HIGHLIGHT_MAX_SPANS), usize::try_from)?;
        let request = SyntaxHighlightRequest::new(
            self.request_id.unwrap_or(1),
            self.request_generation.unwrap_or(1),
            self.origin_id.unwrap_or_else(|| "cli:stdin".to_owned()),
            self.origin_generation.unwrap_or(1),
            self.language,
            source,
            maximum_spans,
        );
        let mut engine = SyntaxHighlightEngine::new(SyntaxHighlightEngineLimits::default())?;
        let result = engine.highlight(&request, cancellation_token)?;
        let status = result.status();
        Ok(CliOutput::facet_with_status(result, status))
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::cli::output::OutputFormat;

    #[test]
    fn syntax_highlight_cli_emits_typed_json_for_exact_stdin() {
        let output = SyntaxHighlightArgs {
            language: "java".to_owned(),
            stdin: true,
            request_id: Some(7),
            request_generation: Some(8),
            origin_id: Some("test:editor".to_owned()),
            origin_generation: Some(9),
            maximum_spans: None,
        }
        .invoke_with_reader(&CancellationToken::new(), "class A {}".as_bytes())
        .expect("direct output")
        .render(Some(OutputFormat::Json), false)
        .expect("render JSON")
        .expect("typed result");
        assert!(output.contains("sfm.syntax-highlight.result/1"));
        assert!(output.contains("test:editor"));
        assert!(output.contains("highlighted"));
        assert!(!output.contains("class A"));
    }

    #[test]
    fn syntax_highlight_cli_requires_explicit_stdin_mode() {
        let error = SyntaxHighlightArgs {
            language: "java".to_owned(),
            stdin: false,
            request_id: None,
            request_generation: None,
            origin_id: None,
            origin_generation: None,
            maximum_spans: None,
        }
        .invoke_with_reader(&CancellationToken::new(), "class A {}".as_bytes())
        .expect_err("missing --stdin must fail");
        assert!(error.to_string().contains("requires --stdin"));
    }
}
