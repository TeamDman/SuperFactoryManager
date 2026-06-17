use std::fmt::Display;
use std::io::Write as _;
// todo(2026-06-16) this may be the wrong abstraction - instead of forbidding println to promote this, our println scenarios should be converted to accept a Writer instead which a top-level cli thing can responsibly print or pass stdout to?

/// Write one raw command-result line to stdout.
///
/// # Errors
///
/// Returns an error when stdout cannot be written.
pub fn stdout_line(value: impl Display) -> eyre::Result<()> {
    let mut stdout = std::io::stdout().lock();
    writeln!(stdout, "{value}")?;
    Ok(())
}

/// Write one raw blank command-result line to stdout.
///
/// # Errors
///
/// Returns an error when stdout cannot be written.
pub fn stdout_blank_line() -> eyre::Result<()> {
    let mut stdout = std::io::stdout().lock();
    writeln!(stdout)?;
    Ok(())
}

/// Write an interactive prompt to stdout without adding a newline.
///
/// # Errors
///
/// Returns an error when stdout cannot be written or flushed.
pub fn stdout_prompt(value: impl Display) -> eyre::Result<()> {
    let mut stdout = std::io::stdout().lock();
    write!(stdout, "{value}")?;
    stdout.flush()?;
    Ok(())
}

/// Write raw streaming text to stderr without adding a newline.
///
/// # Errors
///
/// Returns an error when stderr cannot be written or flushed.
pub fn stderr_text(value: impl Display) -> std::io::Result<()> {
    let mut stderr = std::io::stderr().lock();
    write!(stderr, "{value}")?;
    stderr.flush()
}

/// Write one raw fallback line to stderr when tracing is unavailable.
///
/// # Errors
///
/// Returns an error when stderr cannot be written.
pub fn stderr_line(value: impl Display) -> eyre::Result<()> {
    let mut stderr = std::io::stderr().lock();
    writeln!(stderr, "{value}")?;
    Ok(())
}
