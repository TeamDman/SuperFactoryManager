//! Read-only, streaming file hashes without an external hashing executable.
//!
//! The conventional BLAKE3 output is 32 bytes. Explicit `--length 20` produces
//! the existing lockfile `ContentHash` identity without changing that format.

use crate::cancellation::CancellationToken;
use crate::cli::output::CliOutput;
use crate::file_identity::FileIdentity;
use crate::file_identity::file_identity;
use crate::sfm_path::SfmPath;
use eyre::Result;
use eyre::WrapErr;
use eyre::ensure;
use facet::Facet;
use figue::{self as args};
use sha2::Digest as _;
use std::fmt::Write as _;
use std::fs;
use std::io::Read;
use std::path::Path;
use std::path::PathBuf;

const READ_BUFFER_BYTES: usize = 16_384;
const DEFAULT_BLAKE3_BYTES: u16 = 32;
const MAX_BLAKE3_BYTES: u16 = 64;

#[derive(Debug, Facet)]
pub struct HashArgs {
    #[facet(args::subcommand)]
    pub command: HashCommand,
}

#[derive(Debug, Facet)]
#[repr(u8)]
pub enum HashCommand {
    /// Hash an existing regular file without changing it.
    File(HashFileArgs),
}

#[derive(Clone, Copy, Debug, Default, Eq, Facet, PartialEq)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub enum HashAlgorithm {
    /// BLAKE3, normally 32 bytes; --length 20 matches SFM lock identities.
    #[default]
    Blake3,
    /// The full 32-byte SHA-256 digest.
    Sha256,
    /// The full 20-byte SHA-1 digest, for existing artifact compatibility.
    Sha1,
}

impl HashAlgorithm {
    const fn as_str(self) -> &'static str {
        match self {
            Self::Blake3 => "blake3",
            Self::Sha256 => "sha256",
            Self::Sha1 => "sha1",
        }
    }

    fn output_bytes(self, length: Option<u16>) -> Result<u16> {
        if self != Self::Blake3 {
            ensure!(length.is_none(), "--length is supported only for blake3");
            return Ok(if self == Self::Sha1 { 20 } else { 32 });
        }
        let length = length.unwrap_or(DEFAULT_BLAKE3_BYTES);
        ensure!(
            (1..=MAX_BLAKE3_BYTES).contains(&length),
            "blake3 --length must be between 1 and {MAX_BLAKE3_BYTES} bytes"
        );
        Ok(length)
    }
}

#[derive(Debug, Facet)]
pub struct HashFileArgs {
    /// Literal existing file path, relative to the command invocation directory.
    #[facet(args::positional)]
    pub path: PathBuf,
    /// Digest algorithm: blake3 (default), sha256 or sha1.
    #[facet(default, args::named)]
    pub algorithm: HashAlgorithm,
    /// BLAKE3 output length in bytes, 1 through 64; defaults to 32.
    #[facet(default, args::named)]
    pub length: Option<u16>,
}

#[derive(Debug, Facet)]
struct FileHashReport {
    schema: String,
    path: SfmPath,
    algorithm: HashAlgorithm,
    digest_bytes: u16,
    bytes_hashed: u64,
    hex_digest: String,
    identity: String,
    writes_performed: bool,
}

impl HashArgs {
    /// # Errors
    ///
    /// Rejects invalid digest options, missing/nonregular/reparse inputs,
    /// unreadable or observably changing files, and cancelled operations.
    pub(super) fn invoke_in(
        self,
        cancellation_token: &CancellationToken,
        invocation_dir: &Path,
    ) -> Result<CliOutput> {
        match self.command {
            HashCommand::File(args) => args.invoke_in(cancellation_token, invocation_dir),
        }
    }
}

impl HashFileArgs {
    /// # Errors
    ///
    /// Rejects invalid options before inspecting/opening the input. Inspected
    /// path components must not be symlinks/reparse points. Only regular files
    /// are read, with fixed-size reads and cancellation between chunks.
    pub(super) fn invoke_in(
        self,
        cancellation_token: &CancellationToken,
        invocation_dir: &Path,
    ) -> Result<CliOutput> {
        Ok(CliOutput::facet(file_report(
            &self,
            cancellation_token,
            invocation_dir,
        )?))
    }
}

fn file_report(
    args: &HashFileArgs,
    cancellation_token: &CancellationToken,
    invocation_dir: &Path,
) -> Result<FileHashReport> {
    file_report_with_after_read(args, cancellation_token, invocation_dir, || Ok(()))
}

// A private, no-op production seam permits deterministic replacement tests
// without sleep-based races. The CLI never supplies a mutating callback.
fn file_report_with_after_read(
    args: &HashFileArgs,
    cancellation_token: &CancellationToken,
    invocation_dir: &Path,
    after_read: impl FnOnce() -> Result<()>,
) -> Result<FileHashReport> {
    let digest_bytes = args.algorithm.output_bytes(args.length)?;
    cancellation_token.bail_if_cancelled()?;
    let requested = if args.path.is_absolute() {
        args.path.clone()
    } else {
        invocation_dir.join(&args.path)
    };
    ensure!(
        requested.is_absolute(),
        "hash input needs an absolute path or an absolute invocation directory"
    );
    check_regular_path(&requested)?;
    let path = dunce::canonicalize(&requested).wrap_err("cannot resolve hash input")?;
    check_regular_path(&path)?;
    let mut file = fs::File::open(&path)
        .wrap_err_with(|| format!("cannot open hash input '{}'", path.display()))?;
    let before = file
        .metadata()
        .wrap_err("cannot inspect opened hash input")?;
    ensure!(
        before.is_file() && !is_reparse(&before),
        "opened hash input must be a regular non-reparse file"
    );
    let identity = file_identity(&file)?;
    let modified = before
        .modified()
        .wrap_err("cannot inspect hash input modification time")?;
    for current in [&requested, &path] {
        check_opened_path(current, identity, &before, cancellation_token)?;
    }
    let (bytes_hashed, hex_digest) = hash_exact_length(
        &mut file,
        before.len(),
        args.algorithm,
        args.length,
        cancellation_token,
    )?;
    after_read()?;
    let after = file
        .metadata()
        .wrap_err("cannot recheck opened hash input")?;
    ensure!(
        before.len() == after.len()
            && modified
                == after
                    .modified()
                    .wrap_err("cannot recheck hash input modification time")?,
        "hash input changed while being read"
    );
    for current in [&requested, &path] {
        check_opened_path(current, identity, &before, cancellation_token)?;
    }
    cancellation_token.bail_if_cancelled()?;
    Ok(FileHashReport {
        schema: "sfm:file_hash@1".to_owned(),
        path: path.into(),
        algorithm: args.algorithm,
        digest_bytes,
        bytes_hashed,
        identity: format!("{}:{hex_digest}", args.algorithm.as_str()),
        hex_digest,
        writes_performed: false,
    })
}

fn check_opened_path(
    path: &Path,
    identity: FileIdentity,
    before: &fs::Metadata,
    cancellation_token: &CancellationToken,
) -> Result<()> {
    cancellation_token.bail_if_cancelled()?;
    check_regular_path(path)?;
    let current = fs::File::open(path).wrap_err("cannot reopen hash input for identity check")?;
    let metadata = current
        .metadata()
        .wrap_err("cannot inspect reopened hash input")?;
    ensure!(
        metadata.is_file()
            && !is_reparse(&metadata)
            && file_identity(&current)? == identity
            && metadata.len() == before.len()
            && metadata
                .modified()
                .wrap_err("cannot inspect reopened input modification time")?
                == before
                    .modified()
                    .wrap_err("cannot inspect original input modification time")?,
        "hash input path was replaced or changed while being read"
    );
    check_regular_path(path)?;
    cancellation_token.bail_if_cancelled()?;
    Ok(())
}

fn check_regular_path(path: &Path) -> Result<()> {
    for (index, component) in path.ancestors().enumerate() {
        let metadata = fs::symlink_metadata(component)
            .wrap_err_with(|| format!("cannot inspect hash input '{}'", component.display()))?;
        ensure!(
            !is_reparse(&metadata)
                && if index == 0 {
                    metadata.is_file()
                } else {
                    metadata.is_dir()
                },
            "hash input must be a regular file with no symlink or reparse point components"
        );
    }
    Ok(())
}

fn is_reparse(metadata: &fs::Metadata) -> bool {
    if metadata.file_type().is_symlink() {
        return true;
    }
    #[cfg(windows)]
    {
        use std::os::windows::fs::MetadataExt as _;
        metadata.file_attributes() & 0x0000_0400 != 0
    }
    #[cfg(not(windows))]
    {
        false
    }
}

fn hash_exact_length(
    reader: &mut impl Read,
    expected_bytes: u64,
    algorithm: HashAlgorithm,
    length: Option<u16>,
    cancellation_token: &CancellationToken,
) -> Result<(u64, String)> {
    // One extra byte detects growth, while a growing file cannot cause an
    // unbounded read. Successful results still cover the entire original file.
    let limit = expected_bytes
        .checked_add(1)
        .ok_or_else(|| eyre::eyre!("hash input length cannot be bounded"))?;
    let result = hash_reader(
        &mut reader.take(limit),
        algorithm,
        length,
        cancellation_token,
    )?;
    ensure!(
        result.0 == expected_bytes,
        "hash input length changed while being read"
    );
    Ok(result)
}

fn hash_reader(
    reader: &mut impl Read,
    algorithm: HashAlgorithm,
    length: Option<u16>,
    cancellation_token: &CancellationToken,
) -> Result<(u64, String)> {
    let output_bytes = algorithm.output_bytes(length)?;
    match algorithm {
        HashAlgorithm::Blake3 => {
            let mut hasher = blake3::Hasher::new();
            let count = pump(reader, cancellation_token, |bytes| {
                hasher.update(bytes);
            })?;
            let mut result = [0_u8; 64];
            let digest = &mut result[..usize::from(output_bytes)];
            hasher.finalize_xof().fill(digest);
            Ok((count, lower_hex(digest)))
        }
        HashAlgorithm::Sha256 => {
            let mut hasher = sha2::Sha256::new();
            let count = pump(reader, cancellation_token, |bytes| hasher.update(bytes))?;
            Ok((count, lower_hex(&hasher.finalize())))
        }
        HashAlgorithm::Sha1 => {
            let mut hasher = sha1::Sha1::new();
            let count = pump(reader, cancellation_token, |bytes| hasher.update(bytes))?;
            Ok((count, lower_hex(&hasher.finalize())))
        }
    }
}

fn pump(
    reader: &mut impl Read,
    cancellation_token: &CancellationToken,
    mut update: impl FnMut(&[u8]),
) -> Result<u64> {
    let mut buffer = [0_u8; READ_BUFFER_BYTES];
    let mut count = 0_u64;
    loop {
        cancellation_token.bail_if_cancelled()?;
        let read = match reader.read(&mut buffer) {
            Err(error) if error.kind() == std::io::ErrorKind::Interrupted => continue,
            result => result.wrap_err("cannot read input while hashing")?,
        };
        if read == 0 {
            return Ok(count);
        }
        count = count
            .checked_add(u64::try_from(read)?)
            .ok_or_else(|| eyre::eyre!("hash input byte count overflow"))?;
        update(&buffer[..read]);
    }
}

fn lower_hex(bytes: &[u8]) -> String {
    let mut hex = String::with_capacity(bytes.len() * 2);
    for byte in bytes {
        write!(&mut hex, "{byte:02x}").expect("writing to String cannot fail");
    }
    hex
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::cli::output::OutputFormat;
    use crate::jar_build::hash::ContentHash;
    use crate::jar_build::hash::ContentHashAlgorithm;
    use std::io;
    use std::io::Cursor;

    fn args(
        path: impl Into<PathBuf>,
        algorithm: HashAlgorithm,
        length: Option<u16>,
    ) -> HashFileArgs {
        HashFileArgs {
            path: path.into(),
            algorithm,
            length,
        }
    }

    #[test]
    fn top_level_cli_dispatches_hash_file_with_json_output() {
        let temp = tempfile::tempdir().unwrap();
        fs::write(temp.path().join("abc.bin"), b"abc").unwrap();
        let cli = figue::from_slice::<crate::cli::Cli>(&[
            "--output-format",
            "json",
            "hash",
            "file",
            "abc.bin",
            "--algorithm",
            "sha256",
        ])
        .into_result()
        .expect("top-level hash command should parse")
        .get_silent();
        assert!(matches!(&cli.command, crate::cli::Command::Hash(_)));
        let output = cli
            .invoke_in(CancellationToken::new(), temp.path())
            .unwrap();
        let json = output
            .render(Some(OutputFormat::Json), false)
            .unwrap()
            .unwrap();
        let report: FileHashReport = facet_json::from_str(&json).unwrap();
        assert_eq!(report.schema, "sfm:file_hash@1");
        assert_eq!(report.algorithm, HashAlgorithm::Sha256);
        assert_eq!(
            report.identity,
            "sha256:ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        );
        assert!(!report.writes_performed);
        assert_eq!(fs::read_dir(temp.path()).unwrap().count(), 1);
    }

    #[test]
    fn hash_cli_parses_default_and_explicit_digest_options() {
        let parse = |input: &[&str]| {
            figue::from_slice::<HashArgs>(input)
                .into_result()
                .expect("hash options parse")
                .get_silent()
        };
        let HashCommand::File(default) = parse(&["file", "literal file.bin"]).command;
        assert_eq!(default.path, PathBuf::from("literal file.bin"));
        assert_eq!(default.algorithm, HashAlgorithm::Blake3);
        assert_eq!(default.length, None);
        for (name, algorithm) in [
            ("blake3", HashAlgorithm::Blake3),
            ("sha256", HashAlgorithm::Sha256),
            ("sha1", HashAlgorithm::Sha1),
        ] {
            let HashCommand::File(parsed) = parse(&["file", "a", "--algorithm", name]).command;
            assert_eq!(parsed.algorithm, algorithm);
        }
        for length in ["20", "32", "64"] {
            let HashCommand::File(parsed) = parse(&["file", "a", "--length", length]).command;
            assert_eq!(parsed.length, Some(length.parse().unwrap()));
        }
        for input in [
            &[][..],
            &["file"][..],
            &["file", "a", "--algorithm", "md5"][..],
            &["file", "a", "--length", "65536"][..],
            &["file", "a", "--length", "-1"][..],
        ] {
            assert!(figue::from_slice::<HashArgs>(input).into_result().is_err());
        }
    }

    #[test]
    fn empty_and_abc_hashes_match_fixed_golden_digests() {
        let goldens: &[(&[u8], HashAlgorithm, &str)] = &[
            (
                b"",
                HashAlgorithm::Blake3,
                "af1349b9f5f9a1a6a0404dea36dcc9499bcb25c9adc112b7cc9a93cae41f3262",
            ),
            (
                b"abc",
                HashAlgorithm::Blake3,
                "6437b3ac38465133ffb63b75273a8db548c558465d79db03fd359c6cd5bd9d85",
            ),
            (
                b"",
                HashAlgorithm::Sha256,
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            ),
            (
                b"abc",
                HashAlgorithm::Sha256,
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            ),
            (
                b"",
                HashAlgorithm::Sha1,
                "da39a3ee5e6b4b0d3255bfef95601890afd80709",
            ),
            (
                b"abc",
                HashAlgorithm::Sha1,
                "a9993e364706816aba3e25717850c26c9cd0d89d",
            ),
        ];
        for (bytes, algorithm, expected) in goldens {
            let (count, actual) = hash_reader(
                &mut Cursor::new(bytes),
                *algorithm,
                None,
                &CancellationToken::new(),
            )
            .unwrap();
            assert_eq!(count, u64::try_from(bytes.len()).unwrap());
            assert_eq!(&actual, expected);
        }
    }

    #[test]
    fn explicit_blake3_twenty_matches_unchanged_lock_content_hash() {
        for bytes in [&b""[..], &b"abc"[..], &b"hello world"[..]] {
            let (_, hex) = hash_reader(
                &mut Cursor::new(bytes),
                HashAlgorithm::Blake3,
                Some(20),
                &CancellationToken::new(),
            )
            .unwrap();
            let identity = format!("blake3:{hex}");
            assert_eq!(
                identity,
                ContentHash::from_bytes(bytes, ContentHashAlgorithm::Blake3).to_string()
            );
            assert!(ContentHash::parse(&identity).is_ok());
        }
        for length in [1, 20, 32, MAX_BLAKE3_BYTES] {
            let (_, hex) = hash_reader(
                &mut Cursor::new(b"abc"),
                HashAlgorithm::Blake3,
                Some(length),
                &CancellationToken::new(),
            )
            .unwrap();
            assert_eq!(hex.len(), usize::from(length) * 2);
        }
    }

    #[test]
    fn invalid_digest_options_are_rejected_before_file_inspection() {
        let temp = tempfile::tempdir().unwrap();
        for (algorithm, length, expected) in [
            (HashAlgorithm::Blake3, Some(0), "between 1 and 64"),
            (HashAlgorithm::Blake3, Some(65), "between 1 and 64"),
            (HashAlgorithm::Sha256, Some(32), "only for blake3"),
            (HashAlgorithm::Sha1, Some(20), "only for blake3"),
        ] {
            let error = args("missing.bin", algorithm, length)
                .invoke_in(&CancellationToken::new(), temp.path())
                .unwrap_err()
                .to_string();
            assert!(error.contains(expected), "{error}");
        }
        assert_eq!(fs::read_dir(temp.path()).unwrap().count(), 0);
    }

    struct CountedReader {
        bytes: Cursor<Vec<u8>>,
        calls: usize,
    }

    impl Read for CountedReader {
        fn read(&mut self, buffer: &mut [u8]) -> io::Result<usize> {
            assert!(buffer.len() <= READ_BUFFER_BYTES);
            self.calls += 1;
            self.bytes.read(buffer)
        }
    }

    #[test]
    fn multibuffer_streaming_matches_full_input_hashes() {
        let bytes = (0_u8..=255)
            .cycle()
            .take(READ_BUFFER_BYTES * 3 + 19)
            .collect::<Vec<_>>();
        for (algorithm, expected) in [
            (
                HashAlgorithm::Blake3,
                blake3::hash(&bytes).to_hex().to_string(),
            ),
            (
                HashAlgorithm::Sha256,
                lower_hex(&sha2::Sha256::digest(&bytes)),
            ),
            (HashAlgorithm::Sha1, lower_hex(&sha1::Sha1::digest(&bytes))),
        ] {
            let mut reader = CountedReader {
                bytes: Cursor::new(bytes.clone()),
                calls: 0,
            };
            let (count, hash) =
                hash_reader(&mut reader, algorithm, None, &CancellationToken::new()).unwrap();
            assert_eq!(count, u64::try_from(bytes.len()).unwrap());
            assert_eq!(hash, expected);
            assert!(reader.calls >= 5);
        }
    }

    struct InterruptedThenShort {
        interrupted: bool,
        bytes: Cursor<&'static [u8]>,
    }

    impl Read for InterruptedThenShort {
        fn read(&mut self, buffer: &mut [u8]) -> io::Result<usize> {
            if !self.interrupted {
                self.interrupted = true;
                return Err(io::ErrorKind::Interrupted.into());
            }
            let limit = buffer.len().min(2);
            self.bytes.read(&mut buffer[..limit])
        }
    }

    #[test]
    fn interrupted_short_reads_preserve_every_input_byte() {
        let mut reader = InterruptedThenShort {
            interrupted: false,
            bytes: Cursor::new(b"abc"),
        };
        let result = hash_reader(
            &mut reader,
            HashAlgorithm::Sha1,
            None,
            &CancellationToken::new(),
        )
        .unwrap();
        assert_eq!(
            result,
            (3, "a9993e364706816aba3e25717850c26c9cd0d89d".to_owned())
        );
    }

    #[test]
    fn stream_length_drift_and_io_faults_do_not_return_partial_identities() {
        for expected_bytes in [0, 2, 4, u64::MAX] {
            assert!(
                hash_exact_length(
                    &mut Cursor::new(b"abc"),
                    expected_bytes,
                    HashAlgorithm::Blake3,
                    None,
                    &CancellationToken::new()
                )
                .is_err()
            );
        }
        struct Fault;
        impl Read for Fault {
            fn read(&mut self, _: &mut [u8]) -> io::Result<usize> {
                Err(io::Error::other("fixture read fault"))
            }
        }
        let error = hash_reader(
            &mut Fault,
            HashAlgorithm::Sha256,
            None,
            &CancellationToken::new(),
        )
        .unwrap_err();
        assert!(format!("{error:?}").contains("fixture read fault"));
    }

    #[test]
    fn literal_relative_file_invocation_emits_typed_read_only_report() {
        let temp = tempfile::tempdir().unwrap();
        let name = "literal [file] with spaces.bin";
        let path = temp.path().join(name);
        fs::write(&path, b"abc").unwrap();
        let original = fs::metadata(&path).unwrap();
        let output = HashArgs {
            command: HashCommand::File(args(name, HashAlgorithm::Blake3, Some(20))),
        }
        .invoke_in(&CancellationToken::new(), temp.path())
        .unwrap();
        assert_eq!(output.exit_code(), 0);
        let json = output
            .render(Some(OutputFormat::Json), false)
            .unwrap()
            .unwrap();
        let report: FileHashReport = facet_json::from_str(&json).unwrap();
        assert_eq!(report.schema, "sfm:file_hash@1");
        assert_eq!(report.algorithm, HashAlgorithm::Blake3);
        assert_eq!(report.bytes_hashed, 3);
        assert_eq!(report.digest_bytes, 20);
        assert_eq!(
            report.identity,
            "blake3:6437b3ac38465133ffb63b75273a8db548c55846"
        );
        assert!(!report.writes_performed);
        assert_eq!(fs::read(&path).unwrap(), b"abc");
        assert_eq!(
            fs::metadata(&path).unwrap().modified().ok(),
            original.modified().ok()
        );
        assert_eq!(fs::read_dir(temp.path()).unwrap().count(), 1);
    }

    #[test]
    fn actual_absolute_file_invocations_preserve_full_conventional_digests() {
        let temp = tempfile::tempdir().unwrap();
        let path = temp.path().join("abc.bin");
        fs::write(&path, b"abc").unwrap();
        for (algorithm, expected, digest_bytes) in [
            (
                HashAlgorithm::Blake3,
                "6437b3ac38465133ffb63b75273a8db548c558465d79db03fd359c6cd5bd9d85",
                32,
            ),
            (
                HashAlgorithm::Sha256,
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                32,
            ),
            (
                HashAlgorithm::Sha1,
                "a9993e364706816aba3e25717850c26c9cd0d89d",
                20,
            ),
        ] {
            let report = file_report(
                &args(&path, algorithm, None),
                &CancellationToken::new(),
                temp.path(),
            )
            .unwrap();
            assert_eq!(report.hex_digest, expected);
            assert_eq!(report.digest_bytes, digest_bytes);
            assert_eq!(report.bytes_hashed, 3);
            assert_eq!(
                report.identity,
                format!("{}:{expected}", algorithm.as_str())
            );
            assert!(!report.writes_performed);
        }
        assert_eq!(fs::read(&path).unwrap(), b"abc");
        assert_eq!(fs::read_dir(temp.path()).unwrap().count(), 1);
    }

    #[cfg(any(unix, windows))]
    #[test]
    fn same_sized_same_mtime_leaf_replacement_is_refused_before_reporting() {
        let temp = tempfile::tempdir().unwrap();
        let path = temp.path().join("input.bin");
        let moved = temp.path().join("moved.bin");
        fs::write(&path, b"abc").unwrap();
        let modified = fs::metadata(&path).unwrap().modified().unwrap();
        let error = file_report_with_after_read(
            &args(&path, HashAlgorithm::Sha256, None),
            &CancellationToken::new(),
            temp.path(),
            || {
                fs::rename(&path, &moved)?;
                let mut replacement = fs::File::create_new(&path)?;
                std::io::Write::write_all(&mut replacement, b"xyz")?;
                replacement.set_times(fs::FileTimes::new().set_modified(modified))?;
                ensure!(
                    replacement.metadata()?.modified()? == modified,
                    "fixture time changed"
                );
                Ok(())
            },
        )
        .unwrap_err();
        assert!(error.to_string().contains("path was replaced"), "{error:?}");
        assert_eq!(fs::read(&path).unwrap(), b"xyz");
        assert_eq!(fs::read(&moved).unwrap(), b"abc");
    }

    #[cfg(any(unix, windows))]
    #[test]
    fn parent_directory_replacement_or_os_refusal_prevents_a_hash_report() {
        let temp = tempfile::tempdir().unwrap();
        let parent = temp.path().join("active");
        let moved = temp.path().join("previous");
        fs::create_dir(&parent).unwrap();
        let path = parent.join("input.bin");
        fs::write(&path, b"abc").unwrap();
        let mut parent_replaced = false;
        let error = file_report_with_after_read(
            &args(&path, HashAlgorithm::Blake3, None),
            &CancellationToken::new(),
            temp.path(),
            || {
                fs::rename(&parent, &moved)?;
                parent_replaced = true;
                fs::create_dir(&parent)?;
                fs::write(&path, b"xyz")?;
                Ok(())
            },
        )
        .unwrap_err();
        #[cfg(windows)]
        if !parent_replaced {
            // Windows can deny directory renaming while its child is open.
            // This proves OS refusal, not execution of our replacement guard.
            assert!(
                error.chain().any(|cause| cause
                    .downcast_ref::<io::Error>()
                    .is_some_and(|error| error.kind() == io::ErrorKind::PermissionDenied)),
                "{error:?}"
            );
            assert!(!moved.exists());
            assert_eq!(fs::read(&path).unwrap(), b"abc");
            assert_eq!(fs::read_dir(&parent).unwrap().count(), 1);
            return;
        }
        assert!(parent_replaced);
        assert!(error.to_string().contains("path was replaced"), "{error:?}");
        assert_eq!(fs::read(&path).unwrap(), b"xyz");
        assert_eq!(fs::read(moved.join("input.bin")).unwrap(), b"abc");
    }

    #[cfg(any(unix, windows))]
    #[test]
    fn hardlink_alias_to_the_same_file_remains_a_valid_hash_input() {
        let temp = tempfile::tempdir().unwrap();
        let original = temp.path().join("original.bin");
        let alias = temp.path().join("alias.bin");
        fs::write(&original, b"abc").unwrap();
        fs::hard_link(&original, &alias).unwrap();
        let report = file_report(
            &args(&alias, HashAlgorithm::Sha256, None),
            &CancellationToken::new(),
            temp.path(),
        )
        .unwrap();
        assert_eq!(report.bytes_hashed, 3);
        assert_eq!(
            report.hex_digest,
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        );
        assert!(!report.writes_performed);
        assert_eq!(fs::read_dir(temp.path()).unwrap().count(), 2);
    }

    #[test]
    fn missing_files_and_directories_are_refused() {
        let temp = tempfile::tempdir().unwrap();
        for path in [temp.path().to_path_buf(), temp.path().join("missing.bin")] {
            assert!(
                args(path, HashAlgorithm::Blake3, None)
                    .invoke_in(&CancellationToken::new(), temp.path())
                    .is_err()
            );
        }
    }

    #[test]
    fn cancelled_hash_refuses_before_opening_input_and_before_reading_chunks() {
        let token = CancellationToken::new();
        token.request_cancel("fixture cancellation");
        let temp = tempfile::tempdir().unwrap();
        let error = args("missing.bin", HashAlgorithm::Blake3, None)
            .invoke_in(&token, temp.path())
            .unwrap_err();
        assert!(error.to_string().contains("fixture cancellation"));
        let mut reader = CountedReader {
            bytes: Cursor::new(b"abc".to_vec()),
            calls: 0,
        };
        assert!(hash_reader(&mut reader, HashAlgorithm::Blake3, None, &token).is_err());
        assert_eq!(reader.calls, 0);
    }

    #[test]
    fn cancellation_during_streaming_refuses_a_partial_hash_before_the_next_chunk() {
        struct CancellingReader {
            token: CancellationToken,
            calls: usize,
        }
        impl Read for CancellingReader {
            fn read(&mut self, buffer: &mut [u8]) -> io::Result<usize> {
                self.calls += 1;
                assert_eq!(self.calls, 1, "must not read after cancellation");
                buffer.fill(42);
                self.token.request_cancel("cancel after first chunk");
                Ok(buffer.len())
            }
        }
        let token = CancellationToken::new();
        let mut reader = CancellingReader {
            token: token.clone(),
            calls: 0,
        };
        let error = hash_reader(&mut reader, HashAlgorithm::Blake3, None, &token).unwrap_err();
        assert!(error.to_string().contains("cancel after first chunk"));
        assert_eq!(reader.calls, 1);
    }

    #[cfg(any(unix, windows))]
    #[test]
    fn symlink_leaf_and_parent_are_refused_when_creation_is_available() {
        let temp = tempfile::tempdir().unwrap();
        let actual = temp.path().join("actual");
        fs::create_dir(&actual).unwrap();
        let file = actual.join("file.bin");
        fs::write(&file, b"abc").unwrap();
        let leaf = temp.path().join("linked.bin");
        let parent = temp.path().join("linked-directory");
        #[cfg(unix)]
        let created = std::os::unix::fs::symlink(&file, &leaf)
            .and_then(|()| std::os::unix::fs::symlink(&actual, &parent));
        #[cfg(windows)]
        let created = std::os::windows::fs::symlink_file(&file, &leaf)
            .and_then(|()| std::os::windows::fs::symlink_dir(&actual, &parent));
        if created.is_err() {
            return;
        }
        for input in [leaf, parent.join("file.bin")] {
            let error = args(input, HashAlgorithm::Blake3, None)
                .invoke_in(&CancellationToken::new(), temp.path())
                .unwrap_err();
            assert!(error.to_string().contains("symlink or reparse"));
        }
    }
}
