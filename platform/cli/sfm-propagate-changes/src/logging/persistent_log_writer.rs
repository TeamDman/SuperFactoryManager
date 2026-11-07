//! Synchronous record writer: thread serialization and bounded shared-file locks.
use std::fs::File;
use std::io::{self, Write};
use std::sync::{Mutex, MutexGuard};
use tracing_subscriber::fmt::MakeWriter;

pub(super) struct LogWriter {
    file: Mutex<File>,
}
impl LogWriter {
    pub(super) fn new(file: File) -> Self {
        Self {
            file: Mutex::new(file),
        }
    }
}

pub(super) struct LogGuard<'a> {
    file: MutexGuard<'a, File>,
    locked: bool,
    failed: bool,
}
impl<'a> MakeWriter<'a> for LogWriter {
    type Writer = LogGuard<'a>;
    fn make_writer(&'a self) -> Self::Writer {
        let file = self
            .file
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        let result = super::persistent_log::lock(&file, None);
        LogGuard {
            file,
            locked: result.is_ok(),
            failed: result.is_err(),
        }
    }
}
impl Write for LogGuard<'_> {
    fn write(&mut self, buf: &[u8]) -> io::Result<usize> {
        if self.failed {
            return Err(io::Error::other("persistent log lock failed"));
        }
        self.file.write(buf)
    }
    fn flush(&mut self) -> io::Result<()> {
        if self.failed {
            return Err(io::Error::other("persistent log lock failed"));
        }
        self.file.flush()
    }
}
impl Drop for LogGuard<'_> {
    fn drop(&mut self) {
        if self.locked && self.file.unlock().is_err() {
            eprintln!("Failed to unlock persistent log file");
        }
    }
}
