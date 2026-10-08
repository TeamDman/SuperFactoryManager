// Cache-acquisition regressions in the real engine namespace.
// Loopback fixtures acquire only synthetic bytes, never publisher artifacts.
#[cfg(test)]
mod authenticated_library_batch_cache_tests {
    use super::*;
    use std::cell::Cell;
    use std::net::TcpListener;

    fn client() -> eyre::Result<Client> {
        Ok(Client::builder().no_proxy().build()?)
    }

    #[test]
    fn many_verified_cache_hits_do_not_call_missing_effect_owner_check() -> eyre::Result<()> {
        let root = tempfile::tempdir()?;
        let checks = Cell::new(0_usize);
        let client = client()?;
        let cancellation = CancellationToken::new();
        let bytes = b"same immutable fixture bytes";
        let expected = ContentHash::from_bytes(bytes, ContentHashAlgorithm::Sha1);
        for index in 0..32 {
            let path = root.path().join(format!("library-{index}.jar"));
            fs::write(&path, bytes)?;
            assert_eq!(
                acquire_named_frozen_download(
                    &cancellation,
                    &client,
                    "https://never-contact.invalid/fixture.jar",
                    &path,
                    &expected,
                    bytes.len() as u64,
                    Some(bytes.len() as u64),
                    || {
                        checks.set(checks.get() + 1);
                        eyre::bail!("cached hit must not request a missing-file effect")
                    },
                )?,
                bytes,
            );
        }
        assert_eq!(checks.get(), 0);
        // Existing artifact-lock siblings persist after handle unlock.
        assert_eq!(fs::read_dir(root.path())?.count(), 64);
        Ok(())
    }

    #[test]
    fn same_size_corrupt_cache_is_refused_without_owner_callback_or_repair() -> eyre::Result<()> {
        let root = tempfile::tempdir()?;
        let path = root.path().join("library.jar");
        let recovery = root.path().join("prior.bad.kept");
        fs::write(&path, b"corrupt!")?;
        fs::write(&recovery, b"do not replace")?;
        let expected = ContentHash::from_bytes(b"original", ContentHashAlgorithm::Sha1);
        let checks = Cell::new(0);
        let error = acquire_named_frozen_download(
            &CancellationToken::new(),
            &client()?,
            "https://never-contact.invalid/fixture.jar",
            &path,
            &expected,
            8,
            Some(8),
            || {
                checks.set(checks.get() + 1);
                eyre::bail!("owner callback must not run for a corrupt hit")
            },
        )
        .unwrap_err();
        assert!(error.to_string().contains("hash mismatch"));
        assert_eq!(checks.get(), 0);
        assert_eq!(fs::read(&path)?, b"corrupt!");
        assert_eq!(fs::read(&recovery)?, b"do not replace");
        assert!(artifact_lock_path(&path)?.is_file());
        assert_eq!(fs::read_dir(root.path())?.count(), 3);
        Ok(())
    }

    #[test]
    fn exact_size_mismatch_refuses_cached_bytes_before_owner_callback() -> eyre::Result<()> {
        let root = tempfile::tempdir()?;
        let path = root.path().join("library.jar");
        let bytes = b"original";
        fs::write(&path, bytes)?;
        let expected = ContentHash::from_bytes(bytes, ContentHashAlgorithm::Sha1);
        let checks = Cell::new(0);
        let error = acquire_named_frozen_download(
            &CancellationToken::new(),
            &client()?,
            "https://never-contact.invalid/fixture.jar",
            &path,
            &expected,
            16,
            Some(9),
            || {
                checks.set(checks.get() + 1);
                eyre::bail!("size mismatch must not acquire")
            },
        )
        .unwrap_err();
        assert!(error.to_string().contains("size mismatch"));
        assert_eq!(checks.get(), 0);
        assert_eq!(fs::read(&path)?, bytes);
        assert!(artifact_lock_path(&path)?.is_file());
        assert_eq!(fs::read_dir(root.path())?.count(), 2);
        Ok(())
    }

    #[test]
    fn missing_file_owner_refusal_precedes_http_and_artifact_publication() -> eyre::Result<()> {
        let root = tempfile::tempdir()?;
        let path = root.path().join("missing.jar");
        let checks = Cell::new(0);
        let expected = ContentHash::from_bytes(b"original", ContentHashAlgorithm::Sha1);
        let error = acquire_named_frozen_download(
            &CancellationToken::new(),
            &client()?,
            "https://never-contact.invalid/fixture.jar",
            &path,
            &expected,
            8,
            Some(8),
            || {
                checks.set(checks.get() + 1);
                eyre::bail!("exact project changed before acquisition")
            },
        )
        .unwrap_err();
        assert!(error.to_string().contains("exact project changed"));
        assert_eq!(checks.get(), 1);
        assert!(!path.exists());
        assert!(artifact_lock_path(&path)?.is_file());
        assert_eq!(fs::read_dir(root.path())?.count(), 1);
        Ok(())
    }

    #[test]
    fn cancellation_precedes_missing_file_owner_callback() -> eyre::Result<()> {
        let root = tempfile::tempdir()?;
        let path = root.path().join("missing.jar");
        let checks = Cell::new(0);
        let cancellation = CancellationToken::new();
        cancellation.request_cancel("fixture cancellation");
        let expected = ContentHash::from_bytes(b"original", ContentHashAlgorithm::Sha1);
        let error = acquire_named_frozen_download(
            &cancellation,
            &client()?,
            "https://never-contact.invalid/fixture.jar",
            &path,
            &expected,
            8,
            Some(8),
            || {
                checks.set(checks.get() + 1);
                eyre::bail!("cancelled request must not reach owner callback")
            },
        )
        .unwrap_err();
        assert!(error.to_string().contains("fixture cancellation"));
        assert_eq!(checks.get(), 0);
        assert_eq!(fs::read_dir(root.path())?.count(), 0);
        Ok(())
    }

    #[test]
    fn directory_cache_hit_is_refused_without_owner_callback() -> eyre::Result<()> {
        let root = tempfile::tempdir()?;
        let path = root.path().join("directory.jar");
        fs::create_dir(&path)?;
        let checks = Cell::new(0);
        let expected = ContentHash::from_bytes(b"original", ContentHashAlgorithm::Sha1);
        let error = acquire_named_frozen_download(
            &CancellationToken::new(),
            &client()?,
            "https://never-contact.invalid/fixture.jar",
            &path,
            &expected,
            8,
            Some(8),
            || {
                checks.set(checks.get() + 1);
                eyre::bail!("nonregular hit must not acquire")
            },
        )
        .unwrap_err();
        assert!(error.to_string().contains("not a regular file"));
        assert_eq!(checks.get(), 0);
        assert!(path.is_dir());
        Ok(())
    }

    // The listener is bounded and loopback-only. No test contacts a publisher,
    // resolves a dependency, runs Java or changes a source lock.
    fn one_response(
        bytes: &'static [u8],
    ) -> eyre::Result<(String, thread::JoinHandle<eyre::Result<()>>)> {
        let listener = TcpListener::bind((std::net::Ipv4Addr::LOCALHOST, 0))?;
        let address = listener.local_addr()?;
        listener.set_nonblocking(true)?;
        let server = thread::spawn(move || {
            let deadline = Instant::now() + Duration::from_secs(3);
            let mut stream = loop {
                match listener.accept() {
                    Ok((stream, _)) => break stream,
                    Err(error) if error.kind() == std::io::ErrorKind::WouldBlock => {
                        eyre::ensure!(Instant::now() < deadline, "fixture HTTP accept timed out");
                        thread::sleep(Duration::from_millis(5));
                    }
                    Err(error) => return Err(error.into()),
                }
            };
            // Windows can retain the listener's nonblocking mode on accept.
            // A read timeout does not restore blocking mode; racing the client's
            // first bytes otherwise fails with WSAEWOULDBLOCK under load.
            stream.set_nonblocking(false)?;
            stream.set_read_timeout(Some(Duration::from_secs(3)))?;
            stream.set_write_timeout(Some(Duration::from_secs(3)))?;
            let mut request = [0_u8; 4096];
            eyre::ensure!(stream.read(&mut request)? > 0, "fixture HTTP request is empty");
            write!(
                stream,
                "HTTP/1.1 200 OK\r\nContent-Length: {}\r\nConnection: close\r\n\r\n",
                bytes.len(),
            )?;
            stream.write_all(bytes)?;
            Ok(())
        });
        Ok((format!("http://{address}/fixture.jar"), server))
    }

    #[test]
    fn fresh_owner_refusal_after_http_prevents_temporary_artifact_write() -> eyre::Result<()> {
        let root = tempfile::tempdir()?;
        let path = root.path().join("missing.jar");
        let bytes = b"original";
        let (url, server) = one_response(bytes)?;
        let expected = ContentHash::from_bytes(bytes, ContentHashAlgorithm::Sha1);
        let checks = Cell::new(0);
        let result = acquire_named_frozen_download(
            &CancellationToken::new(),
            &client()?,
            &url,
            &path,
            &expected,
            bytes.len() as u64,
            Some(bytes.len() as u64),
            || {
                checks.set(checks.get() + 1);
                eyre::ensure!(checks.get() == 1, "project changed before temporary write");
                Ok(())
            },
        );
        server.join().expect("fixture HTTP thread panicked")?;
        assert!(result.unwrap_err().to_string().contains("temporary write"));
        assert_eq!(checks.get(), 2);
        assert!(!path.exists());
        assert!(artifact_lock_path(&path)?.is_file());
        assert_eq!(fs::read_dir(root.path())?.count(), 1);
        Ok(())
    }

    #[test]
    fn fresh_owner_refusal_before_link_preserves_owned_recovery_temporary() -> eyre::Result<()> {
        let root = tempfile::tempdir()?;
        let path = root.path().join("missing.jar");
        let bytes = b"original";
        let (url, server) = one_response(bytes)?;
        let expected = ContentHash::from_bytes(bytes, ContentHashAlgorithm::Sha1);
        let checks = Cell::new(0);
        let result = acquire_named_frozen_download(
            &CancellationToken::new(),
            &client()?,
            &url,
            &path,
            &expected,
            bytes.len() as u64,
            Some(bytes.len() as u64),
            || {
                checks.set(checks.get() + 1);
                eyre::ensure!(checks.get() < 3, "project changed before cache link");
                Ok(())
            },
        );
        server.join().expect("fixture HTTP thread panicked")?;
        assert!(result.unwrap_err().to_string().contains("cache link"));
        assert_eq!(checks.get(), 3);
        assert!(!path.exists());
        let mut recovery = fs::read_dir(root.path())?
            .map(|entry| entry.map(|value| value.path()))
            .collect::<std::io::Result<Vec<_>>>()?;
        let lock = artifact_lock_path(&path)?;
        assert!(recovery.contains(&lock));
        recovery.retain(|candidate| candidate != &lock);
        assert_eq!(recovery.len(), 1);
        assert_eq!(fs::read(&recovery[0])?, bytes);
        Ok(())
    }

    #[test]
    fn missing_valid_bytes_recheck_owner_for_http_temporary_and_cache_link() -> eyre::Result<()> {
        let root = tempfile::tempdir()?;
        let path = root.path().join("missing.jar");
        let bytes = b"original";
        let (url, server) = one_response(bytes)?;
        let expected = ContentHash::from_bytes(bytes, ContentHashAlgorithm::Sha1);
        let checks = Cell::new(0);
        let result = acquire_named_frozen_download(
            &CancellationToken::new(),
            &client()?,
            &url,
            &path,
            &expected,
            bytes.len() as u64,
            Some(bytes.len() as u64),
            || {
                checks.set(checks.get() + 1);
                Ok(())
            },
        );
        server.join().expect("fixture HTTP thread panicked")?;
        assert_eq!(result?, bytes);
        assert_eq!(checks.get(), 3);
        assert_eq!(fs::read(&path)?, bytes);
        assert!(artifact_lock_path(&path)?.is_file());
        assert_eq!(fs::read_dir(root.path())?.count(), 2);
        Ok(())
    }
}
