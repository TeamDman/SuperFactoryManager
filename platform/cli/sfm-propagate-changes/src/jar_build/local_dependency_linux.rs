//! Linux implementation of the same trusted-local historical cache authority.
//! Append-only entries/head; no payload retirement, repair or fresh attestation.
#[path = "local_dependency_linux_sources.rs"]
mod sources;
use super::super::local_dependency_consumption::LocalDependencyConsumption;
use super::super::local_dependency_key::LocalDependencyKey;
use super::super::local_dependency_manifest::LocalDependencyManifest;
use super::super::nfrt_launch_contract::PreparedNfrtInvocationInput;
use super::VerifiedLocalDependencyPublication;
use super::linux_io::Directory;
use super::linux_io::HeldFile;
use super::linux_io::Lease;
use crate::cancellation::CancellationToken;
use crate::source_projection::nfrt_producer_output_receipt::NfrtProducerOutputReceipt;
use eyre::Result;
use eyre::ensure;
use std::fs;
use std::io::Write;
use std::os::unix::fs::FileExt as _;
use std::path::Path;
use std::path::PathBuf;

const MANIFEST_BOUND: u64 = 8 * 1024 * 1024;
const OUTPUT_BOUND: u64 = 512 * 1024 * 1024;

#[derive(Debug)]
pub(crate) struct LocalDependencyCache {
    key: LocalDependencyKey,
    directory: Directory,
    producer: Option<Lease>,
    held: Vec<HeldFile>,
}

impl LocalDependencyCache {
    pub(crate) fn begin(
        root: &Path,
        key: LocalDependencyKey,
        cancel: &CancellationToken,
    ) -> Result<Self> {
        cancel.bail_if_cancelled()?;
        ensure!(
            super::environment_allows_reuse(),
            "cache declined inherited JVM controls"
        );
        key.validate()?;
        let parent = Directory::open(
            root.parent()
                .ok_or_else(|| eyre::eyre!("cache root lacks parent"))?,
        )?;
        let root = parent.child(
            root.file_name()
                .and_then(|n| n.to_str())
                .ok_or_else(|| eyre::eyre!("cache root lacks leaf"))?,
            true,
        )?;
        root.require_owner()?;
        let ownership = facet_json::to_string(&(
            "sfm:linux_local_dependencies@1",
            root.path.display().to_string(),
        ))?;
        match root.read("owner.json") {
            Ok(_) => (),
            Err(error) => {
                if fs::symlink_metadata(root.access("owner.json")).is_ok() {
                    return Err(error);
                }
                ensure!(
                    fs::read_dir(root.access(""))?.next().is_none()
                        || root.read("owner.json").is_ok(),
                    "unmarked nonempty dependency cache root is not adopted"
                );
                // A concurrent first producer may commit the same root marker.
                if let Err(error) = root.publish("owner.json", ownership.as_bytes(), cancel)
                    && root.read("owner.json").is_err()
                {
                    return Err(error);
                }
            }
        }
        let owner = HeldFile::capture(&root, "owner.json", 4096, cancel)?;
        ensure!(
            owner.content(cancel)? == ownership.as_bytes(),
            "cache ownership marker differs"
        );
        let directory = root.child(&key.digest(), true)?;
        directory.require_owner()?;
        let producer = directory.lock("producer.lock", cancel)?;
        Ok(Self {
            key,
            directory,
            producer: Some(producer),
            held: vec![owner],
        })
    }

    pub(crate) fn release_producer(&mut self) {
        drop(self.producer.take());
    }

    pub(crate) fn recheck(&self, cancel: &CancellationToken) -> Result<()> {
        self.directory.recheck()?;
        self.directory.require_owner()?;
        for held in &self.held {
            held.directory.require_owner()?;
        }
        if let Some(producer) = &self.producer {
            producer.recheck()?;
        }
        for held in &self.held {
            held.recheck(cancel)?;
            super::linux_io::readonly_owned(&held.file)?;
        }
        self.directory.require_owner()?;
        for held in &self.held {
            held.directory.require_owner()?;
        }
        Ok(())
    }

    pub(crate) fn lookup(
        &mut self,
        prepared: &PreparedNfrtInvocationInput,
        cancel: &CancellationToken,
    ) -> Result<Option<(PathBuf, PathBuf)>> {
        self.require_producer(cancel)?;
        let value = match self.read_entry(cancel) {
            Ok(value) => value,
            Err(error) => {
                cancel.bail_if_cancelled()?;
                return Err(error.wrap_err("Linux dependency entry rejected; preserve evidence and resolve corruption before reuse"));
            }
        };
        let Some((manifest, entry, held)) = value else {
            return Ok(None);
        };
        let consumption = LocalDependencyConsumption::reused(&self.key, prepared, &manifest)?;
        let text = facet_json::to_string(&consumption)?;
        let digest = crate::source_projection::provenance::sha256(text.as_bytes());
        let name = format!("consumption-{}.json", digest.trim_start_matches("sha256:"));
        self.record(&name, text.as_bytes(), cancel)?;
        self.held.extend(held);
        self.recheck(cancel)?;
        tracing::info!(key = %self.key.digest(), authority = "trusted-local-developer-cache", historical_production = %manifest.production_contract_sha256,
            current_consumption = %prepared.contract_identity(), "Verified historical dependency reuse; no fresh NFRT execution claimed");
        Ok(Some((
            entry.path.join("classes.jar"),
            entry.path.join("sources.jar"),
        )))
    }

    pub(crate) fn publish_verified(
        &mut self,
        prepared: &PreparedNfrtInvocationInput,
        classes: &Path,
        sources: &Path,
        receipts: &[&NfrtProducerOutputReceipt; 2],
        cancel: &CancellationToken,
    ) -> Result<Option<VerifiedLocalDependencyPublication>> {
        self.require_producer(cancel)?;
        let manifest = LocalDependencyManifest::from_fresh(&self.key, prepared, *receipts)?;
        if self.directory.read("current.json").is_ok() {
            // Refresh never replaces historical evidence or copies a second
            // identical payload pair. Different/corrupt bytes remain diagnosed.
            let Some((existing, _, held)) = self.read_entry(cancel)? else {
                return Ok(None);
            };
            ensure!(
                existing.classes_sha256 == manifest.classes_sha256
                    && existing.sources_sha256 == manifest.sources_sha256,
                "fresh dependency outputs differ from existing historical entry; no replacement performed"
            );
            self.held.extend(held);
        } else {
            ensure!(
                fs::symlink_metadata(self.directory.access("current.json")).is_err(),
                "unsafe existing cache head"
            );
            let entry = self
                .directory
                .child(&format!("entry-{:032x}", rand::random::<u128>()), true)?;
            entry.require_owner()?;
            Self::copy_output(
                &entry,
                "classes.jar",
                classes,
                manifest.classes_bytes,
                &manifest.classes_sha256,
                cancel,
            )?;
            Self::copy_output(
                &entry,
                "sources.jar",
                sources,
                manifest.sources_bytes,
                &manifest.sources_sha256,
                cancel,
            )?;
            entry.publish(
                "manifest.json",
                facet_json::to_string(&manifest)?.as_bytes(),
                cancel,
            )?;
            let verified = self.read_payload(&entry, cancel)?;
            entry.sync()?;
            self.require_producer(cancel)?;
            let leaf = entry
                .path
                .file_name()
                .and_then(|n| n.to_str())
                .ok_or_else(|| eyre::eyre!("cache entry lacks leaf"))?;
            self.directory.publish(
                "current.json",
                facet_json::to_string(&leaf)?.as_bytes(),
                cancel,
            )?;
            self.held.extend(verified.1);
            // Independently read the discoverable head and full payloads back.
            let Some((_, _, held)) = self.read_entry(cancel)? else {
                eyre::bail!("published cache entry not discoverable");
            };
            self.held.extend(held);
        }
        self.recheck(cancel)?;
        Ok(Some(VerifiedLocalDependencyPublication {
            contract_identity: prepared.contract_identity(),
            output_sha256: [manifest.classes_sha256, manifest.sources_sha256],
        }))
    }

    pub(crate) fn compiler_identity(&self) -> String {
        // Includes the exact SDK image, trust store, tool/recipe/AT and dependency
        // identity; source-owner labels were already excluded by the existing key.
        format!("sha256:{}", self.key.digest())
    }

    pub(crate) fn paths(&self, cancel: &CancellationToken) -> Result<(PathBuf, PathBuf)> {
        self.recheck(cancel)?;
        let path = |name: &str| {
            self.held
                .iter()
                .find(|h| h.name == name)
                .map(|h| h.directory.path.join(name))
                .ok_or_else(|| eyre::eyre!("verified cache output not held"))
        };
        Ok((path("classes.jar")?, path("sources.jar")?))
    }

    pub(crate) fn source_root(
        &self,
        sources: &Path,
        cancel: &CancellationToken,
    ) -> Result<PathBuf> {
        ensure!(
            self.paths(cancel)?.1 == sources,
            "source output is not the verified cache archive"
        );
        Ok(sources
            .parent()
            .ok_or_else(|| eyre::eyre!("cached source lacks parent"))?
            .to_owned())
    }

    fn require_producer(&self, cancel: &CancellationToken) -> Result<()> {
        cancel.bail_if_cancelled()?;
        ensure!(
            super::environment_allows_reuse(),
            "cache declined inherited JVM controls"
        );
        self.producer
            .as_ref()
            .ok_or_else(|| eyre::eyre!("cache producer lease absent"))?
            .recheck()?;
        self.recheck(cancel)
    }

    fn record(&self, name: &str, bytes: &[u8], cancel: &CancellationToken) -> Result<()> {
        if fs::symlink_metadata(self.directory.access(name)).is_err()
            && let Err(error) = self.directory.publish(name, bytes, cancel)
            && self.directory.read(name).is_err()
        {
            return Err(error);
        }
        let record = HeldFile::capture(&self.directory, name, MANIFEST_BOUND, cancel)?;
        ensure!(
            record.content(cancel)? == bytes,
            "cache receipt differs from content key"
        );
        Ok(())
    }

    fn read_entry(
        &self,
        cancel: &CancellationToken,
    ) -> Result<Option<(LocalDependencyManifest, Directory, Vec<HeldFile>)>> {
        self.directory.recheck()?;
        if let Err(error) = fs::symlink_metadata(self.directory.access("current.json")) {
            if error.kind() == std::io::ErrorKind::NotFound {
                return Ok(None);
            }
            return Err(error.into());
        }
        let pointer = HeldFile::capture(&self.directory, "current.json", 4096, cancel)?;
        super::linux_io::readonly_owned(&pointer.file)?;
        let name: String = facet_json::from_slice(&pointer.content(cancel)?)?;
        ensure!(
            name.starts_with("entry-")
                && name.len() <= 96
                && name.len() > 6
                && name.bytes().all(|b| b.is_ascii_alphanumeric() || b == b'-'),
            "unsafe cache entry name"
        );
        let entry = self.directory.child(&name, false)?;
        let (manifest, mut held) = self.read_payload(&entry, cancel)?;
        held.push(pointer);
        Ok(Some((manifest, entry, held)))
    }

    fn read_payload(
        &self,
        entry: &Directory,
        cancel: &CancellationToken,
    ) -> Result<(LocalDependencyManifest, Vec<HeldFile>)> {
        entry.require_owner()?;
        let meta = HeldFile::capture(entry, "manifest.json", MANIFEST_BOUND, cancel)?;
        super::linux_io::readonly_owned(&meta.file)?;
        let raw = meta.content(cancel)?;
        let manifest: LocalDependencyManifest = facet_json::from_slice(&raw)?;
        manifest.validate(&self.key)?;
        ensure!(
            raw == facet_json::to_string(&manifest)?.as_bytes(),
            "cache manifest is noncanonical"
        );
        let classes = HeldFile::capture(entry, "classes.jar", OUTPUT_BOUND, cancel)?;
        let sources = HeldFile::capture(entry, "sources.jar", OUTPUT_BOUND, cancel)?;
        ensure!(
            classes.bytes == manifest.classes_bytes
                && classes.digest == manifest.classes_sha256
                && sources.bytes == manifest.sources_bytes
                && sources.digest == manifest.sources_sha256,
            "cache outputs differ from producer receipts"
        );
        super::linux_io::readonly_owned(&classes.file)?;
        super::linux_io::readonly_owned(&sources.file)?;
        Ok((manifest, vec![meta, classes, sources]))
    }

    fn copy_output(
        entry: &Directory,
        name: &str,
        source: &Path,
        bytes: u64,
        digest: &str,
        cancel: &CancellationToken,
    ) -> Result<()> {
        let parent = Directory::open(
            source
                .parent()
                .ok_or_else(|| eyre::eyre!("producer output lacks parent"))?,
        )?;
        let input = HeldFile::capture(
            &parent,
            source
                .file_name()
                .and_then(|n| n.to_str())
                .ok_or_else(|| eyre::eyre!("producer output lacks leaf"))?,
            OUTPUT_BOUND,
            cancel,
        )?;
        ensure!(
            input.bytes == bytes && input.digest == digest,
            "fresh output differs from sealed producer receipt"
        );
        entry.publish_with(
            name,
            |target| {
                let mut offset = 0;
                let mut buffer = vec![0; 64 * 1024];
                while offset < bytes {
                    cancel.bail_if_cancelled()?;
                    let count = input.file.read_at(&mut buffer, offset)?;
                    ensure!(count > 0, "producer output shrank during copy");
                    target.write_all(&buffer[..count])?;
                    offset += u64::try_from(count)?;
                    ensure!(offset <= bytes, "producer output grew during copy");
                }
                ensure!(
                    super::linux_io::hash(target, bytes, cancel)? == digest,
                    "copied output digest differs"
                );
                input.recheck(cancel)
            },
            cancel,
        )?;
        let copied = HeldFile::capture(entry, name, OUTPUT_BOUND, cancel)?;
        ensure!(
            copied.bytes == bytes && copied.digest == digest,
            "published output digest differs"
        );
        Ok(())
    }
}
