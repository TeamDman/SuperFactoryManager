use crate::cancellation::CancellationToken;
use crate::logging::logging_config::LoggingConfig;
use crate::logging::stop_after_layer::StopAfterLayer;
use crate::logging::terminal_event_layer::TerminalEventLayer;
use tracing::info;
use tracing_error::ErrorLayer;
use tracing_subscriber::EnvFilter;
use tracing_subscriber::Registry;
use tracing_subscriber::fmt::writer::BoxMakeWriter;
use tracing_subscriber::prelude::*;
use tracing_subscriber::util::SubscriberInitExt;

#[cfg(feature = "tracy")]
const SFM_ENABLE_TRACY_LAYER_ENV: &str = "SFM_ENABLE_TRACY_LAYER";

fn build_env_filter(config: &LoggingConfig, json: bool) -> EnvFilter {
    let directive = if json {
        &config.json_default_directive
    } else {
        &config.default_directive
    };
    let read_env = if json {
        config.json_read_env_filter
    } else {
        config.read_env_filter
    };
    let builder = EnvFilter::builder().with_default_directive(directive.clone());
    if read_env {
        builder.from_env_lossy()
    } else {
        EnvFilter::default().add_directive(directive.clone())
    }
}

#[cfg(feature = "tracy")]
fn env_flag_enabled(value: Option<&str>) -> bool {
    let Some(value) = value else {
        return false;
    };

    matches!(
        value.trim().to_ascii_lowercase().as_str(),
        "1" | "true" | "yes" | "on"
    )
}

#[cfg(feature = "tracy")]
fn tracy_layer_requested() -> bool {
    env_flag_enabled(std::env::var(SFM_ENABLE_TRACY_LAYER_ENV).ok().as_deref())
}

/// Initialize logging based on the provided configuration.
///
/// # Errors
///
/// This function will return an error if creating the log file or directories fails.
///
pub fn init_logging(
    config: &LoggingConfig,
    cancellation_token: &CancellationToken,
) -> eyre::Result<()> {
    let (subscriber, json_log_path, tracy_requested) =
        configured_subscriber(config, cancellation_token)?;
    subscriber
        .try_init()
        .map_err(|error| eyre::eyre!("failed to initialize tracing subscriber: {error}"))?;
    if let Some(json_log_path) = json_log_path {
        info!(?json_log_path, "JSON log output initialized");
    }
    if tracy_requested {
        info!("Tracy profiling layer enabled");
    }
    Ok(())
}

pub(super) fn configured_subscriber(
    config: &LoggingConfig,
    cancellation_token: &CancellationToken,
) -> eyre::Result<(
    impl tracing::Subscriber + Send + Sync,
    Option<std::path::PathBuf>,
    bool,
)> {
    let subscriber = Registry::default();
    let terminal_layer = TerminalEventLayer.with_filter(build_env_filter(config, false));
    let subscriber = subscriber.with(ErrorLayer::default()).with(terminal_layer);
    let subscriber = subscriber.with(
        config
            .stop_after
            .as_ref()
            .map(|zone| StopAfterLayer::new(zone, cancellation_token.clone())),
    );
    let prepared = super::persistent_log::prepare(config, cancellation_token)?;
    let path = prepared.as_ref().map(|log| log.path.clone());
    let json_layer = prepared.map(|log| {
        let writer = BoxMakeWriter::new(super::persistent_log_writer::LogWriter::new(log.file));
        tracing_subscriber::fmt::layer()
            .json()
            .with_current_span(true)
            .with_span_list(true)
            .with_file(true)
            .with_target(false)
            .with_line_number(true)
            .event_format(crate::startup_argv::StartupJsonFormatter::new(false))
            .with_writer(writer)
            .with_filter(build_env_filter(config, true))
    });
    let subscriber = subscriber.with(json_layer);
    #[cfg(feature = "tracy")]
    let tracy_requested = tracy_layer_requested();
    #[cfg(not(feature = "tracy"))]
    let tracy_requested = false;
    #[cfg(feature = "tracy")]
    let subscriber = subscriber.with(tracy_requested.then(tracing_tracy::TracyLayer::default));
    Ok((subscriber, path, tracy_requested))
}
