//! Binds reviewed delivery assertions to actual artifacts without inferring runtime success.
use super::article_release_artifact::ArticleReleaseArtifact;
use super::article_release_class_receipt::ArticleReleaseClassReceipt;
use super::article_release_delivery::ArticleReleaseDelivery;
use super::document::ReleaseReportDocument;
use super::jar_pair::ReleaseReportJarPair;
use crate::source_projection::candidate_lock::is_reparse;
use eyre::Result;
use eyre::WrapErr as _;
use eyre::ensure;
use sha2::Digest as _;
use sha2::Sha256;
use std::fs;
use std::io::Read as _;
use std::path::Component;
use std::path::Path;

const MAX_JAR_BYTES: u64 = 128 * 1024 * 1024;
const MAX_RECEIPT_BYTES: u64 = 32 * 1024 * 1024;
const MAX_DELIVERY_BYTES: usize = 1024 * 1024;
const TARGETS: [(&str, &str); 10] = [
    ("jar-1-19-2", "1.19.2"),
    ("jar-1-19-4", "1.19.4"),
    ("jar-1-20", "1.20"),
    ("jar-1-20-1", "1.20.1"),
    ("jar-1-20-2", "1.20.2"),
    ("jar-1-20-3", "1.20.3"),
    ("jar-1-20-4", "1.20.4"),
    ("jar-1-21-0", "1.21.0"),
    ("jar-1-21-1", "1.21.1"),
    ("jar-26-1-2", "26.1.2"),
];

/// Validates artifact delivery only. Runtime assertions remain independently authored.
pub(super) fn validate(doc: &ReleaseReportDocument) -> Result<()> {
    for id in [
        "release435-artifact-delivery",
        "release435-source-comparison-identities",
        "release435-runtime-acceptance",
        "historical-runtime-boundary",
    ] {
        required_section(doc, id)?;
    }
    let body = required_section(doc, "release435-artifact-delivery")?;
    ensure!(
        body.len() <= MAX_DELIVERY_BYTES,
        "release delivery exceeds JSON byte budget"
    );
    let delivery: ArticleReleaseDelivery =
        facet_json::from_str(body).wrap_err("invalid release artifact delivery JSON")?;
    ensure!(
        delivery.schema == "sfm:release435-artifact-delivery@1",
        "unsupported release artifact delivery schema"
    );
    ensure!(
        delivery.artifacts.len() == TARGETS.len() && doc.jars.len() == TARGETS.len(),
        "release delivery requires exactly ten artifact and JAR pairs"
    );
    for ((artifact, pair), (id, target)) in delivery.artifacts.iter().zip(&doc.jars).zip(TARGETS) {
        ensure!(
            artifact.id == id && pair.id == id,
            "release artifact identity/order mismatch: {id}"
        );
        ensure!(
            artifact.projection == format!("sfm-4.35.0/mc-{target}"),
            "release artifact projection mismatch: {id}"
        );
        validate_facts(artifact, target)?;
        validate_evidence(artifact, pair)?;
        validate_class_receipt(artifact, pair, target)?;
    }
    Ok(())
}

fn required_section<'a>(doc: &'a ReleaseReportDocument, id: &str) -> Result<&'a str> {
    let mut matches = doc.sections.iter().filter(|section| section.id == id);
    let section = matches
        .next()
        .ok_or_else(|| eyre::eyre!("release article section missing: {id}"))?;
    ensure!(
        matches.next().is_none(),
        "duplicate release article section: {id}"
    );
    ensure!(
        !section.body.trim().is_empty(),
        "release article section is empty: {id}"
    );
    Ok(&section.body)
}

fn validate_facts(artifact: &ArticleReleaseArtifact, target: &str) -> Result<()> {
    ensure!(
        artifact.energy_extraction_verified
            && artifact.label_gun_success_messages_verified
            && artifact.tunnel_forwarding_preserved
            && artifact.buffer_loot_absent
            && artifact.buffer_production_item_absent,
        "release artifact delivery checks are incomplete: {}",
        artifact.id
    );
    ensure!(
        artifact.french_entries == 276
            && artifact.portuguese_entries == 282
            && artifact.portuguese_added_keys == 23
            && artifact.recipe_count == 29
            && artifact.recipe_advancements == 23,
        "release artifact resource counts differ from reviewed delivery: {}",
        artifact.id
    );
    let expected = if matches!(target, "1.20.3" | "1.20.4" | "1.21.0" | "1.21.1" | "26.1.2") {
        "present"
    } else {
        "not-applicable"
    };
    ensure!(
        artifact.tunnel_relay == expected,
        "release tunnel relay applicability mismatch: {}",
        artifact.id
    );
    Ok(())
}

fn validate_evidence(artifact: &ArticleReleaseArtifact, pair: &ReleaseReportJarPair) -> Result<()> {
    verify_hash(
        &pair.baseline_path,
        &artifact.baseline_sha256,
        MAX_JAR_BYTES,
    )?;
    verify_hash(
        &pair.candidate_path,
        &artifact.candidate_sha256,
        MAX_JAR_BYTES,
    )?;
    for (path, expected) in [
        (
            &artifact.package_receipt_path,
            &artifact.package_receipt_sha256,
        ),
        (
            &artifact.resource_audit_path,
            &artifact.resource_audit_sha256,
        ),
        (&artifact.class_audit_path, &artifact.class_audit_sha256),
    ] {
        verify_hash(path, expected, MAX_RECEIPT_BYTES)?;
    }
    // These safe-relative paths require the request's asset root. Full rendering
    // must retain class_tree::evidence's existing hash and JAR-identity validation.
    ensure!(
        pair.class_evidence_path
            .as_deref()
            .is_some_and(|path| !path.trim().is_empty()),
        "release article requires prepared explorer class evidence: {}",
        pair.id
    );
    let digest = pair
        .class_evidence_sha256
        .as_deref()
        .ok_or_else(|| eyre::eyre!("release explorer class evidence hash missing: {}", pair.id))?;
    validate_digest(digest.strip_prefix("sha256:").unwrap_or(digest))
}

fn validate_class_receipt(
    artifact: &ArticleReleaseArtifact,
    pair: &ReleaseReportJarPair,
    target: &str,
) -> Result<()> {
    let path = Path::new(&artifact.class_audit_path);
    let file = open_regular(path, MAX_RECEIPT_BYTES)?;
    let mut bytes = Vec::new();
    file.take(MAX_RECEIPT_BYTES + 1).read_to_end(&mut bytes)?;
    ensure!(
        bytes.len() as u64 <= MAX_RECEIPT_BYTES,
        "release class receipt exceeds byte budget"
    );
    validate_path(path)?;
    ensure!(
        format!("{:x}", Sha256::digest(&bytes)) == artifact.class_audit_sha256,
        "release class receipt changed before parsing"
    );
    let receipt: ArticleReleaseClassReceipt =
        facet_json::from_slice(&bytes).wrap_err("invalid release class audit receipt")?;
    ensure!(
        receipt.schema == "sfm:built-release-class-delivery@1"
            && receipt.projection_key == artifact.projection
            && receipt.target == target,
        "release class receipt schema/projection/target mismatch: {}",
        artifact.id
    );
    validate_digest(&receipt.sha256)?;
    validate_path(Path::new(&receipt.jar))?;
    ensure!(
        receipt.sha256 == artifact.candidate_sha256
            && receipt.bytes == fs::metadata(&pair.candidate_path)?.len()
            && dunce::canonicalize(&receipt.jar)? == dunce::canonicalize(&pair.candidate_path)?,
        "release class receipt candidate identity mismatch: {}",
        artifact.id
    );
    ensure!(
        receipt.success
            && receipt.energy_extraction_verified
            && receipt.label_gun_success_messages_verified
            && receipt.tunnel_forwarding_preserved
            && receipt.buffer_production_item_absent
            && receipt.relay_delivery_status == artifact.tunnel_relay,
        "release class receipt contradicts reviewed delivery facts: {}",
        artifact.id
    );
    Ok(())
}

fn validate_digest(expected: &str) -> Result<()> {
    ensure!(
        expected.len() == 64
            && expected
                .bytes()
                .all(|byte| byte.is_ascii_digit() || (b'a'..=b'f').contains(&byte)),
        "release evidence requires a lowercase raw SHA256 digest"
    );
    Ok(())
}

fn verify_hash(path: &str, expected: &str, limit: u64) -> Result<()> {
    validate_digest(expected)?;
    let actual = hash_regular(Path::new(path), limit)
        .wrap_err_with(|| format!("could not hash release evidence: {path}"))?;
    ensure!(
        actual == expected,
        "release evidence SHA256 mismatch: {path}"
    );
    Ok(())
}

fn validate_path(path: &Path) -> Result<()> {
    ensure!(
        path.is_absolute()
            && path.components().all(|part| matches!(
                part,
                Component::Prefix(_) | Component::RootDir | Component::Normal(_)
            )),
        "release evidence must use an explicit absolute path: {}",
        path.display()
    );
    for (index, ancestor) in path.ancestors().enumerate() {
        let metadata = fs::symlink_metadata(ancestor)?;
        ensure!(
            !is_reparse(&metadata),
            "release evidence contains a symlink/reparse point: {}",
            ancestor.display()
        );
        ensure!(
            if index == 0 {
                metadata.is_file()
            } else {
                metadata.is_dir()
            },
            "release evidence is not a regular file under directories: {}",
            ancestor.display()
        );
    }
    Ok(())
}

fn open_regular(path: &Path, limit: u64) -> Result<fs::File> {
    validate_path(path)?;
    let mut options = fs::OpenOptions::new();
    options.read(true);
    #[cfg(windows)]
    {
        use std::os::windows::fs::OpenOptionsExt as _;
        // FILE_FLAG_OPEN_REPARSE_POINT: inspect the opened leaf instead of following a link.
        options.custom_flags(0x0020_0000)
    };
    let file = options.open(path)?;
    let metadata = file.metadata()?;
    ensure!(
        metadata.is_file() && !is_reparse(&metadata),
        "release evidence opened a link/non-file"
    );
    ensure!(
        metadata.len() > 0 && metadata.len() <= limit,
        "release evidence is empty or exceeds {limit} byte budget"
    );
    Ok(file)
}

fn hash_regular(path: &Path, limit: u64) -> Result<String> {
    let file = open_regular(path, limit)?;
    let metadata = file.metadata()?;
    let mut input = file.take(limit + 1);
    let mut digest = Sha256::new();
    let mut buffer = [0_u8; 8192];
    let mut total = 0_u64;
    loop {
        let count = input.read(&mut buffer)?;
        if count == 0 {
            break;
        }
        total += u64::try_from(count)?;
        ensure!(
            total <= limit,
            "release evidence exceeds {limit} byte budget"
        );
        digest.update(&buffer[..count]);
    }
    ensure!(
        total == metadata.len() && input.get_ref().metadata()?.len() == total,
        "release evidence size changed while hashing"
    );
    validate_path(path)?;
    Ok(format!("{:x}", digest.finalize()))
}
