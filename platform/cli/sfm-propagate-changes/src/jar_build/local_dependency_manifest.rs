//! Historical local developer-cache record; never a fresh execution permit.

use super::local_dependency_key::LocalDependencyKey;
use super::nfrt_launch_contract::PreparedNfrtInvocationInput;
use crate::source_projection::nfrt_producer_output_receipt::NfrtProducerOutputReceipt;
use crate::source_projection::promotion::validate_relative_path;
use eyre::Result;
use eyre::ensure;
use facet::Facet;

const SCHEMA: &str = "sfm:local_dependency_manifest@1";
const ORIGIN: &str = "owned-fresh-local-cache";
const MAX_OUTPUT_BYTES: u64 = 512 * 1024 * 1024;

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
pub(super) struct LocalDependencyManifest {
    pub schema: String,
    pub key: String,
    pub key_descriptor_json: String,
    pub production_contract_sha256: String,
    pub production_origin: String,
    pub production_receipts_json: String,
    pub classes_bytes: u64,
    pub classes_sha256: String,
    pub sources_bytes: u64,
    pub sources_sha256: String,
}

impl LocalDependencyManifest {
    /// Caller must separately prove the owned host completed and exited cleanly.
    /// This portable record and a supplied receipt pair cannot prove that fact.
    pub(super) fn from_fresh(
        key: &LocalDependencyKey,
        prepared: &PreparedNfrtInvocationInput,
        receipts: [&NfrtProducerOutputReceipt; 2],
    ) -> Result<Self> {
        key.matches_prepared(prepared)?;
        let manifest = Self {
            schema: SCHEMA.to_owned(),
            key: key.digest(),
            key_descriptor_json: key.descriptor_json.clone(),
            production_contract_sha256: prepared.contract_identity(),
            production_origin: ORIGIN.to_owned(),
            production_receipts_json: facet_json::to_string(&receipts)?,
            classes_bytes: u64::try_from(receipts[0].bytes)?,
            classes_sha256: receipts[0].full_sha256.clone(),
            sources_bytes: u64::try_from(receipts[1].bytes)?,
            sources_sha256: receipts[1].full_sha256.clone(),
        };
        manifest.validate(key)?;
        Ok(manifest)
    }

    pub(super) fn validate(&self, key: &LocalDependencyKey) -> Result<()> {
        key.validate()?;
        ensure!(
            self.schema == SCHEMA
                && self.production_origin == ORIGIN
                && self.key == key.digest()
                && self.key_descriptor_json == key.descriptor_json
                && !self.production_receipts_json.is_empty()
                && self.production_receipts_json.len() <= 128 * 1024,
            "Local dependency manifest schema, input descriptor or origin differs"
        );
        validate_digest(&self.production_contract_sha256)?;
        let receipts: Vec<NfrtProducerOutputReceipt> =
            facet_json::from_str(&self.production_receipts_json)?;
        ensure!(
            receipts.len() == 2 && receipts[0].scope == receipts[1].scope,
            "Local dependency manifest requires one exact historical receipt pair"
        );
        ensure!(
            facet_json::to_string(&receipts)? == self.production_receipts_json,
            "Local dependency production receipts are not exact canonical typed JSON"
        );
        let (recipe, lock, sdk) = key.producer_binding()?;
        ensure!(
            receipts[0].scope.recipe_id == recipe
                && receipts[0].scope.source_lock_sha256 == lock
                && receipts[0].scope.tool_sdk_sha256 == sdk,
            "Local dependency historical receipt lost its recipe, lock or tool SDK binding"
        );
        for (receipt, (producer, kind, bytes, digest)) in receipts.iter().zip([
            (
                "compiledWithNeoForge",
                "JAR",
                self.classes_bytes,
                &self.classes_sha256,
            ),
            (
                "sourcesWithNeoForge",
                "ZIP",
                self.sources_bytes,
                &self.sources_sha256,
            ),
        ]) {
            validate_digest(digest)?;
            validate_relative_path(&receipt.snapshot_relative_path)?;
            ensure!(
                receipt.schema == "sfm:nfrt_fresh_producer_output@1"
                    && receipt.completion_was_fresh
                    && receipt.inherited_cache_restore_disabled
                    && receipt.memory_snapshot_immutable
                    && !receipt.os_filesystem_attested
                    && !receipt.native_execution_enabled
                    && receipt.scope.prepared_contract_sha256 == self.production_contract_sha256
                    && receipt.declaration.producer_id == producer
                    && receipt.declaration.output_id == "output"
                    && receipt.declaration.output_type == kind
                    && receipt
                        .snapshot_relative_path
                        .starts_with("inputs/generated/")
                    && bytes > 0
                    && bytes <= MAX_OUTPUT_BYTES
                    && receipt.bytes as u64 == bytes
                    && receipt.full_sha256 == *digest,
                "Local dependency historical producer role, flags, length or digest differs"
            );
        }
        Ok(())
    }
}

fn validate_digest(value: &str) -> Result<()> {
    let raw = value.strip_prefix("sha256:").unwrap_or("");
    ensure!(
        raw.len() == 64
            && raw
                .bytes()
                .all(|byte| byte.is_ascii_digit() || (b'a'..=b'f').contains(&byte)),
        "Local dependency manifest requires complete lowercase SHA-256"
    );
    Ok(())
}

#[cfg(test)]
pub(super) fn fixture_manifest(
    key: &LocalDependencyKey,
    classes: &[u8],
    sources: &[u8],
) -> LocalDependencyManifest {
    use crate::source_projection::nfrt_producer_output_receipt::NfrtProducerDeclaration;
    use crate::source_projection::nfrt_producer_output_receipt::NfrtProducerScope;
    use crate::source_projection::provenance::sha256;
    let (recipe, lock, sdk) = key.producer_binding().unwrap();
    let scope = NfrtProducerScope {
        invocation_id: "fixture-completed-owner".to_owned(),
        prepared_contract_sha256: sha256(b"historical actual prepared contract"),
        project_preparation_sha256: sha256(b"historical sfm source"),
        source_lock_sha256: lock,
        recipe_id: recipe,
        graph_plan_sha256: sha256(b"actual graph"),
        tool_sdk_sha256: sdk,
    };
    let receipts = [
        ("compiledWithNeoForge", "JAR", classes),
        ("sourcesWithNeoForge", "ZIP", sources),
    ]
    .map(|(producer, kind, bytes)| NfrtProducerOutputReceipt {
        schema: "sfm:nfrt_fresh_producer_output@1".to_owned(),
        scope: scope.clone(),
        declaration: NfrtProducerDeclaration {
            producer_id: producer.to_owned(),
            output_id: "output".to_owned(),
            output_type: kind.to_owned(),
            output_relative_path: format!("work/{producer}/output.jar"),
            producer_inputs_sha256: sha256(b"actual producer inputs"),
            tool_classpath_and_arguments_sha256: sha256(b"actual tool contract"),
        },
        snapshot_relative_path: format!("inputs/generated/{producer}.bin"),
        bytes: bytes.len(),
        full_sha256: sha256(bytes),
        completion_was_fresh: true,
        inherited_cache_restore_disabled: true,
        memory_snapshot_immutable: true,
        os_filesystem_attested: false,
        native_execution_enabled: false,
    });
    LocalDependencyManifest {
        schema: SCHEMA.to_owned(),
        key: key.digest(),
        key_descriptor_json: key.descriptor_json.clone(),
        production_contract_sha256: scope.prepared_contract_sha256,
        production_origin: ORIGIN.to_owned(),
        production_receipts_json: facet_json::to_string(&receipts).unwrap(),
        classes_bytes: receipts[0].bytes as u64,
        classes_sha256: receipts[0].full_sha256.clone(),
        sources_bytes: receipts[1].bytes as u64,
        sources_sha256: receipts[1].full_sha256.clone(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::source_projection::provenance::sha256;

    #[test]
    fn manifest_retains_exact_historical_pair_and_roundtrips() {
        let key = super::super::local_dependency_key::fixture_key();
        let manifest = fixture_manifest(&key, b"classes", b"sources");
        manifest.validate(&key).unwrap();
        let raw = facet_json::to_string(&manifest).unwrap();
        let restored: LocalDependencyManifest = facet_json::from_str(&raw).unwrap();
        restored.validate(&key).unwrap();
        assert_eq!(manifest, restored);
    }

    #[test]
    fn wrong_input_descriptor_or_payload_metadata_is_not_a_hit() {
        let key = super::super::local_dependency_key::fixture_key();
        for field in 0..5 {
            let mut changed = fixture_manifest(&key, b"classes", b"sources");
            match field {
                0 => changed.key = "0".repeat(64),
                1 => changed.key_descriptor_json.push(' '),
                2 => changed.classes_bytes += 1,
                3 => changed.sources_sha256 = sha256(b"tampered source output"),
                _ => changed.production_contract_sha256 = sha256(b"foreign contract"),
            }
            assert!(changed.validate(&key).is_err());
        }
    }

    #[test]
    fn wrong_roles_foreign_scope_or_nonfresh_production_is_rejected() {
        let key = super::super::local_dependency_key::fixture_key();
        for field in 0..9 {
            let mut changed = fixture_manifest(&key, b"classes", b"sources");
            let mut receipts: Vec<NfrtProducerOutputReceipt> =
                facet_json::from_str(&changed.production_receipts_json).unwrap();
            match field {
                0 => receipts.reverse(),
                1 => receipts[0].scope.invocation_id = "foreign invocation".to_owned(),
                2 => receipts[0].completion_was_fresh = false,
                3 => receipts[0].inherited_cache_restore_disabled = false,
                4 => receipts[0].native_execution_enabled = true,
                5 => receipts[0].snapshot_relative_path = "inputs/generated/../escape".to_owned(),
                6 => {
                    for receipt in &mut receipts {
                        receipt.scope.source_lock_sha256 = sha256(b"foreign lock");
                    }
                }
                7 => {
                    for receipt in &mut receipts {
                        receipt.scope.recipe_id = "foreign recipe".to_owned();
                    }
                }
                _ => {
                    for receipt in &mut receipts {
                        receipt.scope.tool_sdk_sha256 = sha256(b"foreign tool SDK transfer");
                    }
                }
            }
            changed.production_receipts_json = facet_json::to_string(&receipts).unwrap();
            assert!(changed.validate(&key).is_err());
        }
    }
}
