//! Operational SDK extraction controls; never part of an SDK/cache identity.
use crate::cancellation::CancellationToken;
use eyre::Context as _;
use std::io::{Read as _, Seek as _};
use std::process::{Child, Command, ExitStatus, Stdio};
use std::time::{Duration, Instant};

pub(crate) const MAX_EXTRACTION_RATE: u64 = 1024 * 1024 * 1024;

#[derive(Clone, Copy, Default)]
pub(crate) struct InstallControl<'a> {
    pub extraction_bytes_per_second: Option<u64>,
    pub cancellation: Option<&'a CancellationToken>,
}

impl InstallControl<'_> {
    pub(crate) fn validate(self, linux_tar: bool) -> eyre::Result<()> {
        if let Some(rate) = self.extraction_bytes_per_second {
            eyre::ensure!(
                linux_tar,
                "SDK extraction pacing requires a Linux tar artifact"
            );
            eyre::ensure!(
                (1..=MAX_EXTRACTION_RATE).contains(&rate),
                "SDK extraction rate must be 1..={MAX_EXTRACTION_RATE} bytes per second"
            );
        }
        self.checkpoint()
    }

    pub(crate) fn checkpoint(self) -> eyre::Result<()> {
        if let Some(cancel) = self.cancellation {
            cancel.bail_if_cancelled()?;
        }
        Ok(())
    }

    pub(crate) fn run(self, command: &mut Command) -> eyre::Result<(ExitStatus, String)> {
        self.checkpoint()?;
        let mut error = tempfile::tempfile()?;
        command.stdout(Stdio::null()).stderr(error.try_clone()?);
        let mut child = HelperChild(
            command
                .spawn()
                .wrap_err("Linux JBRSDK tar handling requires Python 3")?,
        );
        #[cfg(test)]
        SPAWN_OBSERVER.with(|slot| {
            if let Some(mut observer) = slot.borrow_mut().take() {
                observer(child.0.id());
            }
        });
        let started = Instant::now();
        let status = loop {
            self.checkpoint()?;
            eyre::ensure!(
                started.elapsed() < Duration::from_secs(3600),
                "SDK tar helper exceeded one hour"
            );
            if let Some(status) = child.0.try_wait()? {
                break status;
            }
            std::thread::sleep(Duration::from_millis(50));
        };
        self.checkpoint()?;
        error.rewind()?;
        let mut diagnostic = String::new();
        error.take(64 * 1024).read_to_string(&mut diagnostic)?;
        Ok((status, diagnostic))
    }
}

#[cfg(test)]
thread_local! {
    static SPAWN_OBSERVER: std::cell::RefCell<Option<Box<dyn FnMut(u32)>>> = const { std::cell::RefCell::new(None) };
}

#[cfg(test)]
pub(crate) fn with_spawn_observer<T>(
    observer: impl FnMut(u32) + 'static,
    run: impl FnOnce() -> T,
) -> T {
    struct Reset;
    impl Drop for Reset {
        fn drop(&mut self) {
            SPAWN_OBSERVER.with(|slot| {
                slot.borrow_mut().take();
            });
        }
    }
    SPAWN_OBSERVER.with(|slot| {
        assert!(slot.borrow().is_none(), "SDK spawn observer is not nested");
        *slot.borrow_mut() = Some(Box::new(observer));
    });
    let _reset = Reset;
    run()
}

// The embedded Python helper starts no descendants. Kill and reap this exact
// child before its private extraction directory may be dropped on cancellation.
struct HelperChild(Child);
impl Drop for HelperChild {
    fn drop(&mut self) {
        if !matches!(self.0.try_wait(), Ok(Some(_))) {
            let _ = self.0.kill();
            let _ = self.0.wait();
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn sdk_install_controls_reject_invalid_rates_and_cancel_before_child() -> eyre::Result<()> {
        for rate in [0, MAX_EXTRACTION_RATE + 1] {
            assert!(
                InstallControl {
                    extraction_bytes_per_second: Some(rate),
                    cancellation: None
                }
                .validate(true)
                .is_err()
            );
        }
        assert!(
            InstallControl {
                extraction_bytes_per_second: Some(1),
                cancellation: None
            }
            .validate(false)
            .is_err()
        );
        let cancel = CancellationToken::new();
        cancel.request_cancel("SDK test cancellation");
        let control = InstallControl {
            extraction_bytes_per_second: None,
            cancellation: Some(&cancel),
        };
        assert!(
            control
                .run(&mut Command::new("must-not-launch-sdk-test"))
                .is_err()
        );
        Ok(())
    }

    #[test]
    #[cfg(target_os = "linux")]
    fn sdk_helper_reports_errors_and_reaps_a_cancelled_child() -> eyre::Result<()> {
        let (status, error) = InstallControl::default().run(Command::new("python3").args([
            "-I",
            "-S",
            "-c",
            "import sys;sys.stderr.write('sdk failure');sys.exit(7)",
        ]))?;
        assert_eq!(status.code(), Some(7));
        assert_eq!(error, "sdk failure");
        let cancel = CancellationToken::new();
        let request = cancel.clone();
        let worker = std::thread::spawn(move || {
            std::thread::sleep(Duration::from_millis(150));
            request.request_cancel("cancel sdk helper");
        });
        let started = Instant::now();
        let result = InstallControl {
            extraction_bytes_per_second: None,
            cancellation: Some(&cancel),
        }
        .run(Command::new("python3").args([
            "-I",
            "-S",
            "-c",
            "import time;time.sleep(30)",
        ]));
        worker.join().expect("cancellation worker");
        assert!(result.is_err());
        assert!(started.elapsed() < Duration::from_secs(5));
        Ok(())
    }
}
