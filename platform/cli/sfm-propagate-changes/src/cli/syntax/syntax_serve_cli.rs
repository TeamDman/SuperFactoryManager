use crate::cancellation::CancellationToken;
use crate::cli::output::CliOutput;
use crate::syntax_highlight::SyntaxHighlightEngineLimits;
use crate::syntax_highlight::SyntaxServerIdentity;
use crate::syntax_highlight::SyntaxServerLimits;
use crate::syntax_highlight::SyntaxServerRuntimeSummary;
use crate::syntax_highlight::run_syntax_server_runtime;
use facet::Facet;
use std::io::Read;
use std::io::Write;

#[derive(Facet, Debug, Default)]
pub struct SyntaxServeArgs;

impl SyntaxServeArgs {
    /// Run the lightweight reusable syntax worker until its client disconnects,
    /// acknowledges shutdown, or host cancellation is requested.
    ///
    /// # Errors
    ///
    /// Returns an error when framed protocol I/O or engine startup fails.
    pub fn invoke(self, cancellation_token: &CancellationToken) -> eyre::Result<CliOutput> {
        let stdout = std::io::stdout();
        let mut writer = stdout.lock();
        let summary = self.invoke_with_io(
            SyntaxServerStdin,
            &mut writer,
            cancellation_token,
            SyntaxServerLimits::default(),
            SyntaxHighlightEngineLimits::default(),
        )?;
        tracing::info!(
            target: "sfm::syntax_server",
            terminal_requests = summary.terminal_requests,
            failed_requests = summary.failed_requests,
            cancelled_requests = summary.cancelled_requests,
            query_compilations = summary.query_compilations,
            parse_count = summary.parse_count,
            cache_hits = summary.cache_hits,
            cache_misses = summary.cache_misses,
            cache_entries = summary.final_cache_entries,
            cache_bytes = summary.final_cache_bytes,
            "syntax server stopped"
        );
        Ok(CliOutput::none())
    }

    /// Execute the server against explicit streams and bounds for tests and
    /// non-process adapters. Only framed protocol bytes are written.
    ///
    /// # Errors
    ///
    /// Returns an error when protocol I/O or engine startup fails.
    pub fn invoke_with_io<R, W>(
        self,
        reader: R,
        writer: &mut W,
        cancellation_token: &CancellationToken,
        server_limits: SyntaxServerLimits,
        engine_limits: SyntaxHighlightEngineLimits,
    ) -> eyre::Result<SyntaxServerRuntimeSummary>
    where
        R: Read + Send + 'static,
        W: Write,
    {
        run_syntax_server_runtime(
            reader,
            writer,
            SyntaxServerIdentity {
                server_name: "sfm-propagate-changes".to_owned(),
                server_version: env!("CARGO_PKG_VERSION").to_owned(),
            },
            server_limits,
            engine_limits,
            cancellation_token,
        )
    }
}

struct SyntaxServerStdin;

impl Read for SyntaxServerStdin {
    fn read(&mut self, buf: &mut [u8]) -> std::io::Result<usize> {
        std::io::stdin().lock().read(buf)
    }
}
