use crate::cli::global_args::GlobalArgs;
use tracing::level_filters::LevelFilter;
use tracing_subscriber::EnvFilter;
use tracing_subscriber::fmt::MakeWriter;
use tracing_subscriber::fmt::SubscriberBuilder;
use tracing_subscriber::fmt::format::FmtSpan;
use tracing_subscriber::fmt::time::SystemTime;
use tracing_subscriber::util::SubscriberInitExt;

pub fn init_tracing(
    global_args: &GlobalArgs,
    writer: impl for<'writer> MakeWriter<'writer> + Send + Sync + 'static,
) -> eyre::Result<()> {
    let mine = SubscriberBuilder::default()
        .with_file(cfg!(debug_assertions))
        .with_line_number(cfg!(debug_assertions))
        .with_level(true)
        .with_target(false)
        .with_ansi(true)
        .with_thread_ids(false)
        .with_thread_names(false)
        .with_span_events(FmtSpan::NONE)
        .with_timer(SystemTime)
        .with_writer(writer)
        .with_env_filter(EnvFilter::try_from_default_env().unwrap_or_else(|_| {
            let determined_default_log_level = match global_args.debug {
                true => LevelFilter::DEBUG,
                false => LevelFilter::INFO,
            };

            let filter_string = format!("{determined_default_log_level}");
            EnvFilter::builder().parse_lossy(filter_string)
        }));
    let subscriber = mine.finish();
    subscriber.init();
    Ok(())
}
