//! Pinned candidate closure for an external source producer.
//!
//! This reader does not grant Gradle execution. The eventual producer runner
//! must verify Gradle-managed dependencies against these pinned inputs, run
//! offline, and retain verified handles through their last consumer.
//! Arbitrary task-time filesystem or HTTP reads are outside this verifier.

use super::local_dependency_cache::lease_ancestors;
use super::local_dependency_cache::open_read;
use crate::file_identity::file_identity;
use crate::source_projection::candidate_lock::checked_directory;
use crate::source_projection::candidate_lock::checked_file;
use crate::source_projection::promotion::validate_relative_path;
use eyre::Result;
use eyre::ensure;
use facet::Facet;
use sha2::Digest as _;
use std::collections::{BTreeMap, BTreeSet};
use std::fmt::Write as _;
use std::fs;
use std::io::Read;
use std::io::Seek;
use std::io::SeekFrom;
#[cfg(test)]
use std::io::Write;
use std::path::Path;
#[cfg(test)]
use std::path::PathBuf;
#[cfg(test)]
use std::process::Command;

const SCHEMA: &str = "sfm:source_producer_external_inputs@1";
const MAX_MANIFEST: u64 = 4 * 1024 * 1024;
const MAX_ENTRY: u64 = 2 * 1024 * 1024 * 1024;
const MAX_ENTRIES: usize = 8192;
const GRADLE_ARCHIVE_REQUEST: &str = "tool:gradle-archive";
const JAVA_ARCHIVE_REQUEST: &str = "tool:java-archive";

#[derive(Clone, Debug, Eq, PartialEq)]
pub(crate) struct HistoricalProducerAnchor {
    pub receipt_sha256: String,
    pub repository_url: String,
    pub commit: String,
    pub source_tree: String,
    pub gradle_archive_sha256: String,
    pub java_archive_sha256: String,
    pub tasks: Vec<String>,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
pub(crate) struct ExternalInputManifest {
    schema: String,
    historical_receipt_sha256: String,
    repository_url: String,
    commit: String,
    source_tree: String,
    gradle_archive_sha256: String,
    java_archive_sha256: String,
    tasks: Vec<String>,
    inputs: Vec<ExternalInput>,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
struct ExternalInput {
    request: String,
    coordinate: Option<String>,
    source_url: String,
    variant: String,
    relative_path: String,
    bytes: u64,
    sha256: String,
}

pub(crate) struct VerifiedExternalInput {
    _file: fs::File,
    _directories: Vec<fs::File>,
}

/// Verified files stay open until the staged Gradle consumer finishes.
#[cfg(test)]
pub(crate) struct VerifiedGradleInputs {
    held: Vec<VerifiedExternalInput>,
    verification_metadata: String,
}

/// Owns the source staging directory and verified inputs through Gradle exit
/// and output publication. Dropping it retires only this invocation's scratch.
#[cfg(test)]
pub(crate) struct StagedGradleInputs {
    _checkout: tempfile::TempDir,
    project_root: PathBuf,
    gradle_home: PathBuf,
    _held: Vec<VerifiedExternalInput>,
}

#[cfg(test)]
impl VerifiedGradleInputs {
    /// The caller must populate `checkout/tree` from the pinned Git revision
    /// before staging. A private `TempDir` prevents writes into shared Git trees.
    pub(crate) fn stage(self, checkout: tempfile::TempDir) -> Result<StagedGradleInputs> {
        let project = checked_directory(&checkout.path().join("tree"))?;
        let gradle = checked_directory(&project.join("gradle"))?;
        let target = gradle.join("verification-metadata.xml");
        let mut temporary = tempfile::NamedTempFile::new_in(&gradle)?;
        temporary.write_all(self.verification_metadata.as_bytes())?;
        temporary.as_file().sync_all()?;
        temporary.persist_noclobber(&target)?;
        let gradle_home = checkout.path().join("gradle-home");
        fs::create_dir(&gradle_home)?;
        Ok(StagedGradleInputs {
            _checkout: checkout,
            project_root: project,
            gradle_home,
            _held: self.held,
        })
    }
}

#[cfg(test)]
impl StagedGradleInputs {
    pub(crate) fn project_root(&self) -> &Path {
        &self.project_root
    }

    pub(crate) fn gradle_home(&self) -> &Path {
        &self.gradle_home
    }

    pub(crate) fn require_strict_offline(&self, command: &mut Command) {
        command
            .current_dir(&self.project_root)
            .arg("--offline")
            .arg("--dependency-verification")
            .arg("strict");
    }
}

#[cfg(test)]
pub(crate) fn staged_for_runner_test() -> Result<StagedGradleInputs> {
    let checkout = tempfile::tempdir()?;
    fs::create_dir_all(checkout.path().join("tree/gradle"))?;
    VerifiedGradleInputs {
        held: Vec::new(),
        verification_metadata: "<verification-metadata/>\n".to_owned(),
    }
    .stage(checkout)
}
fn digest(value: &str) -> bool {
    value.len() == 64
        && value
            .bytes()
            .all(|byte| byte.is_ascii_hexdigit() && !byte.is_ascii_uppercase())
}

fn safe_maven_part(value: &str) -> bool {
    !value.is_empty()
        && value
            .bytes()
            .all(|byte| byte.is_ascii_alphanumeric() || matches!(byte, b'.' | b'_' | b'-' | b'+'))
}

impl ExternalInputManifest {
    /// A digest in the reviewed recipe selects the exact manifest bytes. The
    /// old receipt's inventory digest is never presumed to name these bytes.
    pub(crate) fn load_pinned(
        cache: &Path,
        pin: &str,
        anchor: &HistoricalProducerAnchor,
    ) -> Result<Self> {
        ensure!(digest(pin), "invalid external-input manifest pin");
        let cache = checked_directory(cache)?;
        let relative = format!("producer-external-inputs/{pin}.json");
        let path = checked_file(&cache, &relative)?;
        ensure!(
            fs::metadata(&path)?.len() <= MAX_MANIFEST,
            "external-input manifest exceeds bound"
        );
        let raw = fs::read(path)?;
        ensure!(
            format!("{:x}", sha2::Sha256::digest(&raw)) == pin,
            "external-input manifest digest mismatch"
        );
        let manifest: Self = facet_json::from_slice(&raw)?;
        manifest.validate(anchor)?;
        Ok(manifest)
    }

    fn validate(&self, anchor: &HistoricalProducerAnchor) -> Result<()> {
        ensure!(
            self.schema == SCHEMA,
            "unknown external-input manifest schema"
        );
        ensure!(
            self.historical_receipt_sha256 == anchor.receipt_sha256
                && self.repository_url == anchor.repository_url
                && self.commit == anchor.commit
                && self.source_tree == anchor.source_tree
                && self.gradle_archive_sha256 == anchor.gradle_archive_sha256
                && self.java_archive_sha256 == anchor.java_archive_sha256
                && self.tasks == anchor.tasks,
            "external-input manifest differs from historical producer anchor"
        );
        ensure!(
            !self.inputs.is_empty() && self.inputs.len() <= MAX_ENTRIES,
            "external-input manifest has no bounded input set"
        );
        let mut requests = BTreeSet::new();
        let mut paths = BTreeSet::new();
        for input in &self.inputs {
            ensure!(
                !input.request.is_empty()
                    && input.request.len() <= 1024
                    && !input.request.chars().any(char::is_control)
                    && requests.insert(&input.request),
                "duplicate or unsafe external-input request"
            );
            ensure!(
                input.source_url.starts_with("https://")
                    && input.source_url.len() <= 2048
                    && !input.source_url.chars().any(char::is_whitespace)
                    && !input.variant.is_empty()
                    && input.variant.len() <= 256
                    && input
                        .coordinate
                        .as_ref()
                        .is_none_or(|value| !value.is_empty()),
                "external-input provenance is incomplete"
            );
            validate_relative_path(&input.relative_path)?;
            ensure!(
                paths.insert(&input.relative_path),
                "duplicate external-input path"
            );
            ensure!(
                input.bytes > 0 && input.bytes <= MAX_ENTRY && digest(&input.sha256),
                "external-input size or digest is invalid"
            );
        }
        for (request, expected_sha256) in [
            (GRADLE_ARCHIVE_REQUEST, &anchor.gradle_archive_sha256),
            (JAVA_ARCHIVE_REQUEST, &anchor.java_archive_sha256),
        ] {
            let tool = self
                .inputs
                .iter()
                .find(|input| input.request == request)
                .ok_or_else(|| eyre::eyre!("external-input manifest omits {request}"))?;
            ensure!(
                tool.coordinate.is_none() && tool.sha256 == *expected_sha256,
                "external-input manifest tool archive differs from producer anchor: {request}"
            );
        }
        Ok(())
    }

    /// Rejects any request not listed in the pinned manifest. This API must
    /// become the producer's only external-read path before it can run.
    pub(crate) fn verify_requested(
        &self,
        cache: &Path,
        request: &str,
    ) -> Result<VerifiedExternalInput> {
        let input = self
            .inputs
            .iter()
            .find(|input| input.request == request)
            .ok_or_else(|| eyre::eyre!("unlisted external producer input: {request}"))?;
        let cache = checked_directory(cache)?;
        let path = checked_file(&cache, &input.relative_path)?;
        let directories = lease_ancestors(path.parent().expect("checked file has parent"))?;
        let mut file = open_read(&path)?;
        let identity = file_identity(&file)?;
        ensure!(
            file.metadata()?.len() == input.bytes,
            "external producer input size mismatch"
        );
        let mut hash = sha2::Sha256::new();
        let mut total = 0_u64;
        let mut buffer = vec![0_u8; 64 * 1024];
        loop {
            let count = file.read(&mut buffer)?;
            if count == 0 {
                break;
            }
            total += u64::try_from(count)?;
            ensure!(
                total <= input.bytes,
                "external producer input grew during read"
            );
            hash.update(&buffer[..count]);
        }
        ensure!(
            total == input.bytes
                && format!("{:x}", hash.finalize()) == input.sha256
                && file_identity(&file)? == identity
                && file.metadata()?.len() == input.bytes,
            "external producer input bytes or identity changed"
        );
        file.seek(SeekFrom::Start(0))?;
        Ok(VerifiedExternalInput {
            _file: file,
            _directories: directories,
        })
    }

    /// Build strict Gradle dependency-verification metadata from declared Maven
    /// artifacts. Gradle must actually load this XML before it verifies managed
    /// dependencies; this does not constrain arbitrary task-time reads.
    pub(crate) fn gradle_verification_metadata(&self) -> Result<String> {
        let mut components: BTreeMap<(String, String, String), BTreeMap<String, String>> =
            BTreeMap::new();
        for input in &self.inputs {
            let Some(coordinate) = &input.coordinate else {
                ensure!(
                    !input.request.contains('@'),
                    "Gradle-like external input has no Maven coordinate"
                );
                continue;
            };
            let (notation, extension) = input
                .request
                .split_once('@')
                .ok_or_else(|| eyre::eyre!("Gradle input request lacks artifact extension"))?;
            ensure!(
                coordinate == notation && safe_maven_part(extension),
                "Gradle input request differs from declared Maven coordinate"
            );
            let parts: Vec<_> = notation.split(':').collect();
            let (group, name, version, classifier) = match parts.as_slice() {
                [group, name, version] => (*group, *name, *version, None),
                [group, name, version, classifier] => (*group, *name, *version, Some(*classifier)),
                _ => eyre::bail!("Gradle input has invalid Maven coordinate"),
            };
            ensure!(
                [group, name, version].into_iter().all(safe_maven_part)
                    && classifier.is_none_or(safe_maven_part),
                "Gradle input has unsafe Maven coordinate"
            );
            let classifier_suffix =
                classifier.map_or_else(String::new, |value| format!("-{value}"));
            let file_name = format!("{name}-{version}{classifier_suffix}.{extension}");
            ensure!(
                input.source_url.rsplit('/').next() == Some(file_name.as_str()),
                "Gradle input URL does not name its declared Maven artifact"
            );
            let artifacts = components
                .entry((group.to_owned(), name.to_owned(), version.to_owned()))
                .or_default();
            if let Some(previous) = artifacts.insert(file_name, input.sha256.clone()) {
                ensure!(
                    previous == input.sha256,
                    "Gradle input has conflicting checksums for one artifact"
                );
            }
        }
        ensure!(
            !components.is_empty(),
            "Gradle input manifest has no Maven dependency verification entries"
        );
        let mut xml = String::from(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n\
             <verification-metadata xmlns=\"https://schema.gradle.org/dependency-verification\" \
             xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" \
             xsi:schemaLocation=\"https://schema.gradle.org/dependency-verification \
             https://schema.gradle.org/dependency-verification/dependency-verification-1.4.xsd\">\n\
             \t<configuration>\n\
             \t\t<verify-metadata>true</verify-metadata>\n\
             \t\t<verify-signatures>false</verify-signatures>\n\
             \t</configuration>\n\
             \t<components>\n",
        );
        for ((group, name, version), artifacts) in components {
            writeln!(
                &mut xml,
                "\t\t<component group=\"{group}\" name=\"{name}\" version=\"{version}\">"
            )?;
            for (file_name, sha256) in artifacts {
                writeln!(
                    &mut xml,
                    "\t\t\t<artifact name=\"{file_name}\"><sha256 value=\"{sha256}\"/></artifact>"
                )?;
            }
            xml.push_str("\t\t</component>\n");
        }
        xml.push_str("\t</components>\n</verification-metadata>\n");
        Ok(xml)
    }

    pub(crate) fn verify_all(&self, cache: &Path) -> Result<Vec<VerifiedExternalInput>> {
        self.inputs
            .iter()
            .map(|input| self.verify_requested(cache, &input.request))
            .collect()
    }

    #[cfg(test)]
    pub(crate) fn verify_for_gradle(&self, cache: &Path) -> Result<VerifiedGradleInputs> {
        let held = self.verify_all(cache)?;
        let verification_metadata = self.gradle_verification_metadata()?;
        Ok(VerifiedGradleInputs {
            held,
            verification_metadata,
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn fixture() -> Result<(
        tempfile::TempDir,
        HistoricalProducerAnchor,
        ExternalInputManifest,
        String,
    )> {
        let root = tempfile::tempdir()?;
        fs::create_dir_all(root.path().join("maven/example"))?;
        fs::create_dir_all(root.path().join("tools"))?;
        fs::write(
            root.path().join("tools/gradle.zip"),
            b"verified gradle archive",
        )?;
        fs::write(root.path().join("tools/java.zip"), b"verified java archive")?;
        fs::write(
            root.path().join("maven/example/pinned.jar"),
            b"verified bytes",
        )?;
        let anchor = HistoricalProducerAnchor {
            receipt_sha256: "1".repeat(64),
            repository_url: "https://github.com/mekanism/Mekanism/".to_owned(),
            commit: "a".repeat(40),
            source_tree: "b".repeat(40),
            gradle_archive_sha256: format!(
                "{:x}",
                sha2::Sha256::digest(b"verified gradle archive")
            ),
            java_archive_sha256: format!("{:x}", sha2::Sha256::digest(b"verified java archive")),
            tasks: vec!["jar".to_owned(), "apiJar".to_owned()],
        };
        let mut manifest = ExternalInputManifest {
            schema: SCHEMA.to_owned(),
            historical_receipt_sha256: anchor.receipt_sha256.clone(),
            repository_url: anchor.repository_url.clone(),
            commit: anchor.commit.clone(),
            source_tree: anchor.source_tree.clone(),
            gradle_archive_sha256: anchor.gradle_archive_sha256.clone(),
            java_archive_sha256: anchor.java_archive_sha256.clone(),
            tasks: anchor.tasks.clone(),
            inputs: vec![ExternalInput {
                request: "example:pinned:1@jar".to_owned(),
                coordinate: Some("example:pinned:1".to_owned()),
                source_url: "https://repo.example.invalid/example/pinned/1/pinned-1.jar".to_owned(),
                variant: "runtimeElements".to_owned(),
                relative_path: "maven/example/pinned.jar".to_owned(),
                bytes: 14,
                sha256: format!("{:x}", sha2::Sha256::digest(b"verified bytes")),
            }],
        };
        manifest.inputs.extend([
            ExternalInput {
                request: GRADLE_ARCHIVE_REQUEST.to_owned(),
                coordinate: None,
                source_url: "https://services.gradle.org/distributions/gradle-test-all.zip"
                    .to_owned(),
                variant: "distribution-archive".to_owned(),
                relative_path: "tools/gradle.zip".to_owned(),
                bytes: 23,
                sha256: anchor.gradle_archive_sha256.clone(),
            },
            ExternalInput {
                request: JAVA_ARCHIVE_REQUEST.to_owned(),
                coordinate: None,
                source_url: "https://example.invalid/java-sdk.zip".to_owned(),
                variant: "sdk-archive".to_owned(),
                relative_path: "tools/java.zip".to_owned(),
                bytes: 21,
                sha256: anchor.java_archive_sha256.clone(),
            },
        ]);
        let raw = facet_json::to_string(&manifest)?;
        let pin = format!("{:x}", sha2::Sha256::digest(raw.as_bytes()));
        fs::create_dir_all(root.path().join("producer-external-inputs"))?;
        fs::write(
            root.path()
                .join(format!("producer-external-inputs/{pin}.json")),
            raw,
        )?;
        Ok((root, anchor, manifest, pin))
    }

    #[test]
    fn pinned_manifest_accepts_only_its_verified_request() -> Result<()> {
        let (root, anchor, _, pin) = fixture()?;
        let manifest = ExternalInputManifest::load_pinned(root.path(), &pin, &anchor)?;
        let _held = manifest.verify_requested(root.path(), "example:pinned:1@jar")?;
        assert_eq!(
            fs::read(root.path().join("maven/example/pinned.jar"))?,
            b"verified bytes"
        );
        assert!(
            manifest
                .verify_requested(root.path(), "example:unlisted:1@jar")
                .is_err()
        );
        assert_eq!(manifest.verify_all(root.path())?.len(), 3);
        Ok(())
    }

    #[test]
    fn changed_manifest_anchor_and_unlisted_or_unsafe_entries_fail() -> Result<()> {
        let (root, anchor, mut manifest, pin) = fixture()?;
        let mut wrong_anchor = anchor.clone();
        wrong_anchor.commit = "c".repeat(40);
        assert!(ExternalInputManifest::load_pinned(root.path(), &pin, &wrong_anchor).is_err());
        let path = root
            .path()
            .join(format!("producer-external-inputs/{pin}.json"));
        fs::write(&path, b"{}")?;
        assert!(ExternalInputManifest::load_pinned(root.path(), &pin, &anchor).is_err());
        manifest.inputs.push(manifest.inputs[0].clone());
        assert!(manifest.validate(&anchor).is_err());
        manifest.inputs.pop();
        manifest.inputs[0].relative_path = "../outside.jar".to_owned();
        assert!(manifest.validate(&anchor).is_err());
        Ok(())
    }

    #[test]
    fn tool_archives_must_be_declared_and_match_historical_receipt() -> Result<()> {
        let (_, anchor, mut manifest, _) = fixture()?;
        manifest
            .inputs
            .retain(|input| input.request != GRADLE_ARCHIVE_REQUEST);
        assert!(manifest.validate(&anchor).is_err());
        manifest.inputs.push(ExternalInput {
            request: GRADLE_ARCHIVE_REQUEST.to_owned(),
            coordinate: None,
            source_url: "https://services.gradle.org/distributions/gradle-test-all.zip".to_owned(),
            variant: "distribution-archive".to_owned(),
            relative_path: "tools/gradle.zip".to_owned(),
            bytes: 23,
            sha256: "f".repeat(64),
        });
        assert!(manifest.validate(&anchor).is_err());
        Ok(())
    }

    #[test]
    fn gradle_verification_only_lists_declared_maven_artifacts() -> Result<()> {
        let (_, _, manifest, _) = fixture()?;
        let xml = manifest.gradle_verification_metadata()?;
        assert!(xml.contains("<verify-metadata>true</verify-metadata>"));
        assert!(xml.contains("<component group=\"example\" name=\"pinned\" version=\"1\">"));
        assert!(xml.contains("<artifact name=\"pinned-1.jar\">"));
        assert!(xml.contains(&manifest.inputs[0].sha256));
        assert!(!xml.contains("gradle.zip"));
        assert!(!xml.contains("java.zip"));
        assert!(!xml.contains("unlisted"));
        Ok(())
    }

    #[test]
    fn verified_gradle_inputs_stage_only_into_owned_tree() -> Result<()> {
        let (cache, _, manifest, _) = fixture()?;
        let checkout = tempfile::tempdir()?;
        fs::create_dir_all(checkout.path().join("tree/gradle"))?;
        let staged = manifest.verify_for_gradle(cache.path())?.stage(checkout)?;
        let xml = fs::read_to_string(
            staged
                .project_root()
                .join("gradle/verification-metadata.xml"),
        )?;
        assert!(xml.contains("<artifact name=\"pinned-1.jar\">"));
        let mut command = Command::new("gradle");
        staged.require_strict_offline(&mut command);
        let args: Vec<_> = command
            .get_args()
            .map(|part| part.to_string_lossy().to_string())
            .collect();
        assert_eq!(args, ["--offline", "--dependency-verification", "strict"]);

        let checkout = tempfile::tempdir()?;
        fs::create_dir_all(checkout.path().join("tree/gradle"))?;
        fs::write(
            checkout
                .path()
                .join("tree/gradle/verification-metadata.xml"),
            b"preexisting",
        )?;
        assert!(
            manifest
                .verify_for_gradle(cache.path())?
                .stage(checkout)
                .is_err()
        );
        Ok(())
    }

    #[test]
    fn gradle_verification_rejects_unmatched_request_or_artifact_url() -> Result<()> {
        let (_, _, mut manifest, _) = fixture()?;
        manifest.inputs[0].request = "example:other:1@jar".to_owned();
        assert!(manifest.gradle_verification_metadata().is_err());
        manifest.inputs[0].request = "example:pinned:1@jar".to_owned();
        manifest.inputs[0].source_url =
            "https://repo.example.invalid/example/pinned/1/other-1.jar".to_owned();
        assert!(manifest.gradle_verification_metadata().is_err());
        manifest.inputs[0].source_url =
            "https://repo.example.invalid/example/pinned/1/pinned-1.jar".to_owned();
        manifest.inputs[0].coordinate = None;
        assert!(manifest.gradle_verification_metadata().is_err());
        Ok(())
    }

    #[test]
    fn absent_or_tampered_input_is_never_a_verified_request() -> Result<()> {
        let (root, anchor, _, pin) = fixture()?;
        let manifest = ExternalInputManifest::load_pinned(root.path(), &pin, &anchor)?;
        let path = root.path().join("maven/example/pinned.jar");
        fs::write(&path, b"tampered bytes")?;
        assert!(
            manifest
                .verify_requested(root.path(), "example:pinned:1@jar")
                .is_err()
        );
        fs::remove_file(path)?;
        assert!(
            manifest
                .verify_requested(root.path(), "example:pinned:1@jar")
                .is_err()
        );
        Ok(())
    }
}
