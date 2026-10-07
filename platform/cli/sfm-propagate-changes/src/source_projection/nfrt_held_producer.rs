//! Unregistered host-side producer reader; no graph or execution authority.
//! Reuses existing `FileIdInfo` and directory leases instead of Java fileKey.
//! Caller must separately bind completion to the owned, cache-disabled engine.

use super::core_input_leases::DirectoryLeases;
use super::nfrt_producer_output_receipt::NfrtProducerDeclaration;
use super::nfrt_producer_output_receipt::NfrtProducerRead;
use crate::file_identity::FileIdentity;
use crate::file_identity::file_identity;
use eyre::Result;
use eyre::ensure;
use std::fs::File;
use std::fs::OpenOptions;
use std::io::Read;
use std::os::windows::fs::MetadataExt;
use std::os::windows::fs::OpenOptionsExt;
use std::path::Path;
use windows::Win32::Storage::FileSystem::FILE_ATTRIBUTE_REPARSE_POINT;
use windows::Win32::Storage::FileSystem::FILE_FLAG_OPEN_REPARSE_POINT;
use windows::Win32::Storage::FileSystem::FILE_SHARE_READ;

const MAX_PRODUCER_BYTES: usize = 512 * 1024 * 1024;

/// A held local file observation, not evidence of fresh graph completion.
pub(crate) struct NfrtHeldProducer {
    file: File,
    identity: FileIdentity,
    bytes: usize,
    _ancestry: DirectoryLeases,
}

impl NfrtHeldProducer {
    pub(crate) fn acquire(root: &Path, declaration: &NfrtProducerDeclaration) -> Result<Self> {
        ensure!(
            declaration.output_relative_path.starts_with("work/"),
            "Producer file is not in the invocation work tree"
        );
        let mut ancestry = DirectoryLeases::default();
        // The existing validator rejects traversal and unsafe path components;
        // held ancestors prevent directory substitution before the leaf open.
        let path = ancestry.prepare(root, &declaration.output_relative_path)?;
        let file = OpenOptions::new()
            .read(true)
            .share_mode(FILE_SHARE_READ.0)
            .custom_flags(FILE_FLAG_OPEN_REPARSE_POINT.0)
            .open(path)?;
        let metadata = file.metadata()?;
        ensure!(
            metadata.is_file() && metadata.file_attributes() & FILE_ATTRIBUTE_REPARSE_POINT.0 == 0,
            "Producer file is a reparse point or nonregular file"
        );
        let bytes = usize::try_from(metadata.len())?;
        ensure!(
            bytes > 0 && bytes <= MAX_PRODUCER_BYTES,
            "Producer output is empty or oversized"
        );
        let identity = file_identity(&file)?;
        Ok(Self {
            file,
            identity,
            bytes,
            _ancestry: ancestry,
        })
    }

    pub(crate) fn identity(&self) -> String {
        format!("{:?}", self.identity)
    }

    pub(crate) fn bytes(&self) -> usize {
        self.bytes
    }

    /// Keep this owner alive through snapshot copying and child use.
    /// The pure receipt layer subsequently hashes these observed bytes.
    pub(crate) fn read_once(&mut self) -> Result<NfrtProducerRead> {
        use std::io::Seek;
        use std::io::SeekFrom;
        self.file.seek(SeekFrom::Start(0))?;
        let before = file_identity(&self.file)?;
        ensure!(
            before == self.identity && self.file.metadata()?.len() == self.bytes as u64,
            "Held producer identity or length changed before read"
        );
        let mut bytes = Vec::with_capacity(self.bytes);
        (&mut self.file)
            .take(self.bytes as u64 + 1)
            .read_to_end(&mut bytes)?;
        let after = file_identity(&self.file)?;
        let after_bytes = usize::try_from(self.file.metadata()?.len())?;
        ensure!(
            bytes.len() == self.bytes && after == before && after_bytes == self.bytes,
            "Held producer identity or length changed during read"
        );
        Ok(NfrtProducerRead {
            bytes,
            after_file_identity: self.identity(),
            after_bytes,
            reparse_free_ancestry: true,
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs;

    fn declaration() -> NfrtProducerDeclaration {
        NfrtProducerDeclaration {
            producer_id: "producer".to_owned(),
            output_id: "output".to_owned(),
            output_type: "JAR".to_owned(),
            output_relative_path: "work/output.jar".to_owned(),
            producer_inputs_sha256: format!("sha256:{}", "0".repeat(64)),
            tool_classpath_and_arguments_sha256: format!("sha256:{}", "0".repeat(64)),
        }
    }

    #[test]
    fn producer_reader_retains_identity_and_blocks_mutation_until_drop() -> Result<()> {
        let temporary = tempfile::tempdir()?;
        fs::create_dir(temporary.path().join("work"))?;
        let path = temporary.path().join("work/output.jar");
        fs::write(&path, b"producer")?;
        let mut held = NfrtHeldProducer::acquire(temporary.path(), &declaration())?;
        assert_eq!(held.bytes(), 8);
        let identity = held.identity();
        let read = held.read_once()?;
        assert_eq!(read.bytes, b"producer");
        assert_eq!(read.after_file_identity, identity);
        assert_eq!(held.read_once()?.bytes, b"producer");
        assert!(OpenOptions::new().write(true).open(&path).is_err());
        assert!(fs::rename(&path, temporary.path().join("moved.jar")).is_err());
        assert_eq!(fs::read(&path)?, b"producer");
        drop(held);
        assert!(OpenOptions::new().write(true).open(&path).is_ok());
        Ok(())
    }

    #[test]
    fn unfinished_writer_empty_file_and_traversal_are_refused() -> Result<()> {
        let temporary = tempfile::tempdir()?;
        fs::create_dir(temporary.path().join("work"))?;
        let path = temporary.path().join("work/output.jar");
        fs::write(&path, b"unfinished")?;
        let writer = OpenOptions::new().write(true).open(&path)?;
        assert!(NfrtHeldProducer::acquire(temporary.path(), &declaration()).is_err());
        drop(writer);
        fs::write(&path, [])?;
        assert!(NfrtHeldProducer::acquire(temporary.path(), &declaration()).is_err());
        let mut unsafe_declaration = declaration();
        unsafe_declaration.output_relative_path = "work/../output.jar".to_owned();
        assert!(NfrtHeldProducer::acquire(temporary.path(), &unsafe_declaration).is_err());
        Ok(())
    }
}
