use crate::logging::logging_config::LoggingConfig;
use std::fs::File;
use std::sync::Arc;
use std::sync::Mutex;
use tracing::info;
use tracing_subscriber::EnvFilter;
use tracing_subscriber::fmt::writer::BoxMakeWriter;
use tracing_subscriber::prelude::*;
use tracing_subscriber::util::SubscriberInitExt;

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
    let env_filter_layer = EnvFilter::builder()
        .with_default_directive(config.default_directive.clone())
        .from_env_lossy();

    let stderr_layer = tracing_subscriber::fmt::layer()
        .with_file(cfg!(debug_assertions))
        .with_target(true)
        .with_line_number(cfg!(debug_assertions))
        .with_writer(std::io::stderr)
        .pretty()
        .without_time();

    if let Some(json_log_path) = config.json_log_path.as_ref() {
        // Create parent directories if they don't exist
        if let Some(parent) = json_log_path.parent() {
            std::fs::create_dir_all(parent)?;
        }

        let file = File::create(json_log_path)?;
        let file = Arc::new(Mutex::new(file));
        let json_writer = {
            let file = Arc::clone(&file);
            BoxMakeWriter::new(move || {
                file.lock()
                    .expect("failed to lock json log file")
                    .try_clone()
                    .expect("failed to clone json log file handle")
            })
        };

        let json_format = tracing_subscriber::fmt::format().json();
        let json_layer = tracing_subscriber::fmt::layer()
            .event_format(json_format)
            .with_file(true)
            .with_target(false)
            .with_line_number(true)
            .with_writer(json_writer);

        if let Err(error) = tracing_subscriber::registry()
            .with(env_filter_layer)
            .with(stderr_layer)
            .with(json_layer)
            .try_init()
        {
            eprintln!(
                "Failed to initialize tracing subscriber - are you running `cargo test`? If so, multiple test entrypoints may be running from the same process. https://github.com/tokio-rs/console/issues/505 : {error}"
            );
            return Ok(());
        }

        info!(?json_log_path, "JSON log output initialized");
    } else if let Err(error) = tracing_subscriber::registry()
        .with(env_filter_layer)
        .with(stderr_layer)
        .try_init()
    {
        eprintln!(
            "Failed to initialize tracing subscriber - are you running `cargo test`? If so, multiple test entrypoints may be running from the same process. https://github.com/tokio-rs/console/issues/505 : {error}"
        );
        return Ok(());
    }

    Ok(())
}
