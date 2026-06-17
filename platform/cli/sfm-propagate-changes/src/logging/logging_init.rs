use crate::logging::logging_config::LoggingConfig;
use crate::logging::terminal_event_layer::TerminalEventLayer;
use std::fs::File;
use std::fs::OpenOptions;
use tracing::info;
use tracing_subscriber::EnvFilter;
use tracing_subscriber::Registry;
use tracing_subscriber::fmt::writer::BoxMakeWriter;
use tracing_subscriber::prelude::*;
use tracing_subscriber::util::SubscriberInitExt;

#[cfg(all(feature = "tracy", not(test)))]
const SFM_ENABLE_TRACY_LAYER_ENV: &str = "SFM_ENABLE_TRACY_LAYER";

fn build_env_filter(config: &LoggingConfig) -> EnvFilter {
    let builder = EnvFilter::builder().with_default_directive(config.default_directive.clone());
    if config.read_env_filter {
        builder.from_env_lossy()
    } else {
        EnvFilter::default().add_directive(config.default_directive.clone())
    }
}

#[cfg(all(feature = "tracy", not(test)))]
fn env_flag_enabled(value: Option<&str>) -> bool {
    let Some(value) = value else {
        return false;
    };

    matches!(
        value.trim().to_ascii_lowercase().as_str(),
        "1" | "true" | "yes" | "on"
    )
}

#[cfg(all(feature = "tracy", not(test)))]
fn tracy_layer_requested() -> bool {
    env_flag_enabled(std::env::var(SFM_ENABLE_TRACY_LAYER_ENV).ok().as_deref())
}

/// Initialize logging based on the provided configuration.
///
/// # Errors
///
/// This function will return an error if creating the log file or directories fails.
///
/// # Panics
///
/// This function may panic if locking or cloning the log file handle fails.
pub fn init_logging(config: &LoggingConfig) -> eyre::Result<()> {
    let subscriber = Registry::default();

    {
        // this is what we used to have, for reference.
        let old_stderr_layer = tracing_subscriber::fmt::layer()
            .with_file(cfg!(debug_assertions))
            .with_target(true)
            .with_line_number(cfg!(debug_assertions))
            .with_writer(std::io::stderr)
            .pretty()
            .without_time()
            .with_filter(build_env_filter(config));
        if false {
            subscriber.with(old_stderr_layer);
            panic!()
        }
    }

    let terminal_layer = TerminalEventLayer.with_filter(build_env_filter(config));
    let subscriber = subscriber.with(terminal_layer);

    let json_layer = if let Some(json_log_path) = config.json_log_path.as_ref() {
        // Create parent directories if they don't exist
        if let Some(parent) = json_log_path.parent() {
            std::fs::create_dir_all(parent)?;
        }

        File::create(json_log_path)?;
        let json_writer = {
            let json_log_path = json_log_path.clone();
            BoxMakeWriter::new(move || {
                OpenOptions::new()
                    .append(true)
                    .create(true)
                    .open(&json_log_path)
                    .expect("failed to open json log file for appending")
            })
        };

        let json_layer = tracing_subscriber::fmt::layer()
            .json()
            .with_current_span(true)
            .with_span_list(true)
            .with_file(true)
            .with_target(false)
            .with_line_number(true)
            .with_writer(json_writer)
            .with_filter(build_env_filter(config));

        Some(json_layer)
    } else {
        None
    };
    let subscriber = subscriber.with(json_layer);

    #[cfg(all(feature = "tracy", not(test)))]
    let tracy_layer_requested = tracy_layer_requested();
    #[cfg(all(feature = "tracy", not(test)))]
    let subscriber =
        subscriber.with(tracy_layer_requested.then(tracing_tracy::TracyLayer::default));

    if let Err(error) = subscriber.try_init() {
        eprintln!(
            "Failed to initialize tracing subscriber - are you running `cargo test`? If so, multiple test entrypoints may be running from the same process. https://github.com/tokio-rs/console/issues/505 : {error}"
        );
        return Ok(());
    }

    if let Some(json_log_path) = config.json_log_path.as_ref() {
        info!(?json_log_path, "JSON log output initialized");
    }

    #[cfg(all(feature = "tracy", not(test)))]
    if tracy_layer_requested {
        info!(
            env_var = SFM_ENABLE_TRACY_LAYER_ENV,
            "Tracy profiling layer enabled"
        );
    }

    Ok(())
}
