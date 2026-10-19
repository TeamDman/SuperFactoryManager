//! Redacted, Unicode-only startup evidence. No raw argument reaches tracing.
mod startup_arguments;
mod startup_event_visitor;
#[cfg(test)]
mod startup_fixture;
mod startup_json_formatter;
mod startup_log_record;
#[cfg(test)]
mod tests;

use startup_arguments::StartupArguments;
pub(crate) use startup_json_formatter::StartupJsonFormatter;
use std::ffi::OsString;

pub(crate) const TARGET: &str = "teamy::startup";

/// Reject non-Unicode input without printing, replacing or encoding its contents.
pub(crate) fn unicode_arguments(
    args: impl IntoIterator<Item = OsString>,
) -> eyre::Result<Vec<String>> {
    args.into_iter()
        .map(|argument| {
            argument.into_string().map_err(|_redacted_argument| {
                eyre::eyre!("command arguments must be valid Unicode")
            })
        })
        .collect()
}

pub(crate) fn log<T: facet::Facet<'static>>(arguments: &[String]) -> eyre::Result<()> {
    if tracing::enabled!(target: TARGET, tracing::Level::DEBUG) {
        let record = StartupArguments::redact::<T>(arguments)?;
        let encoded = facet_json::to_string(&record)?;
        // Only this already-redacted Facet record is exposed to any tracing sink.
        tracing::debug!(target: TARGET, startup_arguments = encoded.as_str(), "Process started");
    }
    Ok(())
}

// Figue's failure renderer can include the original command line. Only successful
// builtin displays (help/version/completions) may use its ordinary presentation.
pub(crate) fn finish_parse(
    outcome: figue::DriverOutcome<crate::cli::Cli>,
) -> eyre::Result<crate::cli::Cli> {
    match without_help_suggestions(outcome).into_result() {
        Ok(output) => Ok(output.value),
        Err(error) => {
            if !error.is_success() {
                eyre::bail!("invalid command line; use --help");
            }
            Ok(figue::DriverOutcome::<crate::cli::Cli>::err(error).unwrap())
        }
    }
}

// A missing-required-argument Help can contain an argv-bearing suggestion even
// though Figue classifies it as successful. Preserve help text, omit that payload.
pub(crate) fn without_help_suggestions<T>(
    outcome: figue::DriverOutcome<T>,
) -> figue::DriverOutcome<T> {
    match outcome.into_result() {
        Ok(output) => figue::DriverOutcome::ok(output),
        Err(figue::DriverError::Help { text, .. }) => {
            figue::DriverOutcome::err(figue::DriverError::Help {
                text,
                suggestion: None,
            })
        }
        Err(error) => figue::DriverOutcome::err(error),
    }
}
