//! Source-independent identity for the admitted dependency transformation.
//! Input acquisition/admission remains separate; parsed JSON grants no authority.

#[path = "local_dependency_descriptor.rs"]
mod local_dependency_descriptor;

use super::nfrt_launch_contract::NamedNfrtInvocationInput;
use super::nfrt_launch_contract::PreparedNfrtInvocationInput;
use crate::source_projection::provenance::sha256;
use eyre::Result;
use eyre::ensure;
use facet::Facet;
use local_dependency_descriptor::LocalDependencyDescriptor;

const SCHEMA: &str = "sfm:local_dependency_key@1";
const DESCRIPTOR_SCHEMA: &str = "sfm:local_dependency_descriptor@1";
const MAX_DESCRIPTOR_BYTES: usize = 4 * 1024 * 1024;

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
#[facet(deny_unknown_fields)]
pub(super) struct LocalDependencyKey {
    pub schema: String,
    pub descriptor_json: String,
}

impl LocalDependencyKey {
    pub(super) fn from_prepared(
        prepared: &PreparedNfrtInvocationInput,
        sdk_identity: &str,
        full_sdk_identity: &str,
        host_implementation_identity: &str,
        access_transformer: Option<&[u8]>,
    ) -> Result<Self> {
        Self::from_receipt(
            prepared.receipt().clone(),
            sdk_identity,
            full_sdk_identity,
            host_implementation_identity,
            access_transformer,
        )
    }

    fn from_receipt(
        mut receipt: NamedNfrtInvocationInput,
        sdk_identity: &str,
        full_sdk_identity: &str,
        host_implementation_identity: &str,
        access_transformer: Option<&[u8]>,
    ) -> Result<Self> {
        ensure!(
            access_transformer.is_none_or(|bytes| bytes.len() <= 128 * 1024),
            "Local dependency access transformer exceeds its bound"
        );
        // Source ownership is rechecked independently. Preserve the raw lock,
        // actual artifact pins and ordered tool recipe rather than these labels.
        clear_owner_labels(&mut receipt);
        // Each tuple stays within Facet's pinned tuple-arity limit.
        let descriptor = (
            (
                DESCRIPTOR_SCHEMA.to_owned(),
                std::env::consts::OS.to_owned(),
                std::env::consts::ARCH.to_owned(),
            ),
            (
                canonical_sha256(sdk_identity)?,
                canonical_sha256(full_sdk_identity)?,
                canonical_sha256(host_implementation_identity)?,
            ),
            (access_transformer.map(sha256), receipt),
        );
        let key = Self {
            schema: SCHEMA.to_owned(),
            descriptor_json: facet_json::to_string(&descriptor)?,
        };
        key.validate()?;
        Ok(key)
    }

    /// Persistent lookup digest only; descriptor equality is checked directly.
    pub(super) fn digest(&self) -> String {
        sha256(self.descriptor_json.as_bytes())
            .trim_start_matches("sha256:")
            .to_owned()
    }

    pub(super) fn matches_prepared(&self, prepared: &PreparedNfrtInvocationInput) -> Result<()> {
        self.validate()?;
        let descriptor: LocalDependencyDescriptor = facet_json::from_str(&self.descriptor_json)?;
        let mut receipt = prepared.receipt().clone();
        clear_owner_labels(&mut receipt);
        ensure!(
            descriptor.2.1 == receipt,
            "Local dependency key differs from the current admitted input recipe"
        );
        Ok(())
    }

    /// Small persisted metadata identities for historical producer scope checks.
    pub(super) fn producer_binding(&self) -> Result<(String, String, String)> {
        let descriptor: LocalDependencyDescriptor = facet_json::from_str(&self.descriptor_json)?;
        let receipt = descriptor.2.1;
        // Matches NfrtHostSession::accept_graph: this is the serialized transfer,
        // not NfrtToolSdk::identity() or the additional SDK image digest.
        let sdk = sha256(facet_json::to_string(&receipt.tool_sdk)?.as_bytes());
        Ok((receipt.recipe_id, receipt.source_lock_sha256, sdk))
    }

    pub(super) fn validate(&self) -> Result<()> {
        ensure!(
            self.schema == SCHEMA
                && !self.descriptor_json.is_empty()
                && self.descriptor_json.len() <= MAX_DESCRIPTOR_BYTES,
            "Invalid or oversized local dependency key"
        );
        let descriptor: LocalDependencyDescriptor = facet_json::from_str(&self.descriptor_json)?;
        ensure!(
            facet_json::to_string(&descriptor)? == self.descriptor_json,
            "Local dependency descriptor is not exact canonical typed JSON"
        );
        let ((schema, os, arch), identities, (transformer, receipt)) = descriptor;
        ensure!(
            schema == DESCRIPTOR_SCHEMA
                && os == std::env::consts::OS
                && arch == std::env::consts::ARCH,
            "Local dependency platform or descriptor schema differs"
        );
        for digest in [&identities.0, &identities.1, &identities.2] {
            ensure!(
                canonical_sha256(digest)? == *digest,
                "Noncanonical identity"
            );
        }
        if let Some(digest) = transformer {
            ensure!(
                canonical_sha256(&digest)? == digest,
                "Invalid transformer identity"
            );
        }
        ensure!(
            receipt.schema == "sfm:named_nfrt_invocation_input@1"
                && receipt.invocation_id.is_empty()
                && receipt.projection_key.is_empty()
                && receipt.preparation_identity.is_empty()
                && receipt.original_request_catalog_identity.is_empty()
                && receipt.supplement_identity.is_empty()
                && matches!(receipt.environment.as_str(), "dev" | "release")
                && receipt.fresh_work_root_required
                && !receipt.intermediate_cache_restore
                && !receipt.intermediate_cache_persistence
                && !receipt.launcher_probing
                && !receipt.native_execution_enabled
                && !receipt.os_filesystem_attested,
            "Local dependency descriptor lost its closed-input source boundary"
        );
        canonical_sha256(&receipt.source_lock_sha256)?;
        canonical_sha256(&receipt.supplement_sha256)?;
        ensure!(
            !receipt.inputs.is_empty()
                && receipt.inputs.len() <= 8192
                && receipt.compiler_release > 0
                && u32::from(receipt.compiler_release) <= receipt.tool_sdk.major
                && u32::from(receipt.minimum_tool_jvm) <= receipt.tool_sdk.major,
            "Local dependency descriptor has invalid input or compiler bounds"
        );
        for input in &receipt.inputs {
            ensure!(
                input.bytes > 0 && input.bytes <= 512 * 1024 * 1024,
                "Local dependency input length is invalid"
            );
            canonical_sha256(&input.full_sha256)?;
        }
        Ok(())
    }
}

fn clear_owner_labels(receipt: &mut NamedNfrtInvocationInput) {
    receipt.invocation_id.clear();
    receipt.projection_key.clear();
    receipt.preparation_identity.clear();
    receipt.original_request_catalog_identity.clear();
    receipt.supplement_identity.clear();
}

fn canonical_sha256(value: &str) -> Result<String> {
    let raw = value.strip_prefix("sha256:").unwrap_or(value);
    ensure!(
        raw.len() == 64
            && raw
                .bytes()
                .all(|byte| byte.is_ascii_digit() || (b'a'..=b'f').contains(&byte)),
        "Local dependency identity requires complete lowercase SHA-256"
    );
    Ok(format!("sha256:{raw}"))
}

#[cfg(test)]
pub(super) fn fixture_key() -> LocalDependencyKey {
    LocalDependencyKey::from_receipt(
        fixture_receipt(),
        &sha256(b"held-sdk"),
        &sha256(b"full-sdk"),
        &sha256(b"host-implementation"),
        Some(b"access transformer"),
    )
    .unwrap()
}

#[cfg(test)]
fn fixture_receipt() -> NamedNfrtInvocationInput {
    use super::nfrt_launch_contract::NfrtInputSnapshotExport;
    use super::nfrt_launch_contract::NfrtToolSdkTransfer;
    NamedNfrtInvocationInput {
        schema: "sfm:named_nfrt_invocation_input@1".to_owned(),
        invocation_id: "invocation-source-one".to_owned(),
        projection_key: "sfm-one/mc-1.20.3".to_owned(),
        environment: "release".to_owned(),
        target_id: "1.20.3".to_owned(),
        minecraft_version: "1.20.3".to_owned(),
        recipe_id: "sfm:released-native-inputs/4.34.0/1.20.3@1".to_owned(),
        preparation_identity: sha256(b"sfm source preparation one"),
        source_lock_sha256: sha256(b"source-lock"),
        original_request_catalog_identity: sha256(b"catalog owner one"),
        supplement_identity: sha256(b"supplement owner one"),
        supplement_sha256: sha256(b"exact child supplement"),
        compiler_release: 17,
        minimum_tool_jvm: 21,
        tool_sdk: NfrtToolSdkTransfer {
            major: 21,
            selection: "explicit".to_owned(),
            original_archive_url: None,
            original_archive_sha512: None,
            supplied_executable_bytes: 4,
            supplied_executable_sha256: sha256(b"java"),
            actual_sdk_rechecked: false,
        },
        functions: Vec::new(),
        joined_steps_json: vec!["{\"step\":\"dependency-transform\"}".to_owned()],
        ordered_dependency_rows: Vec::new(),
        ordered_runtime_roots: Vec::new(),
        ordered_transitive_runtime_rows: Vec::new(),
        inputs: vec![NfrtInputSnapshotExport {
            request_key: "version_json".to_owned(),
            coordinate: None,
            exact_url: Some("https://fixture.invalid/version.json".to_owned()),
            origin: "original_schema2_pin".to_owned(),
            original_identity: sha256(b"version"),
            snapshot_relative_path: "inputs/artifacts/0000.bin".to_owned(),
            bytes: 7,
            full_sha256: sha256(b"version"),
        }],
        required_clean_environment_names: vec!["JAVA_TOOL_OPTIONS".to_owned()],
        fresh_work_root_required: true,
        intermediate_cache_restore: false,
        intermediate_cache_persistence: false,
        launcher_probing: false,
        os_filesystem_attested: false,
        native_execution_enabled: false,
        unresolved_execution_gates: vec!["separate native admission".to_owned()],
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn key(receipt: NamedNfrtInvocationInput, transformer: Option<&[u8]>) -> LocalDependencyKey {
        LocalDependencyKey::from_receipt(
            receipt,
            &sha256(b"held-sdk"),
            &sha256(b"full-sdk"),
            &sha256(b"host-implementation"),
            transformer,
        )
        .unwrap()
    }

    #[test]
    fn source_owner_and_invocation_changes_do_not_invalidate_dependency_key() {
        let original = key(fixture_receipt(), None);
        for field in 0..5 {
            let mut changed = fixture_receipt();
            match field {
                0 => changed.invocation_id = "new-invocation".to_owned(),
                1 => changed.projection_key = "other-projection/mc-1.20.3".to_owned(),
                2 => changed.preparation_identity = sha256(b"changed sfm main or test source"),
                3 => changed.original_request_catalog_identity = sha256(b"changed catalog label"),
                _ => changed.supplement_identity = sha256(b"changed supplement label"),
            }
            assert_eq!(key(changed, None), original);
        }
    }

    #[test]
    fn actual_recipe_pin_order_and_compiler_changes_invalidate_dependency_key() {
        let original = key(fixture_receipt(), None);
        for field in 0..8 {
            let mut changed = fixture_receipt();
            match field {
                0 => changed.source_lock_sha256 = sha256(b"different lock"),
                1 => changed.supplement_sha256 = sha256(b"different child supplement"),
                2 => changed.inputs[0].full_sha256 = sha256(b"different input bytes"),
                3 => changed.compiler_release = 18,
                4 => changed.joined_steps_json.push("{}".to_owned()),
                5 => changed.inputs[0].original_identity = sha256(b"different original pin"),
                6 => changed
                    .required_clean_environment_names
                    .push("CLASSPATH".to_owned()),
                _ => changed.target_id = "different reviewed target".to_owned(),
            }
            assert_ne!(key(changed, None), original);
        }
        let mut first = fixture_receipt();
        let mut second = first.inputs[0].clone();
        second.request_key = "second-role".to_owned();
        first.inputs.push(second);
        let mut reordered = first.clone();
        reordered.inputs.reverse();
        assert_ne!(key(first, None), key(reordered, None));
    }

    #[test]
    fn absent_empty_and_changed_access_transformers_are_distinct() {
        let values = [
            None,
            Some(b"".as_slice()),
            Some(b"one".as_slice()),
            Some(b"two".as_slice()),
        ];
        let keys = values.map(|value| key(fixture_receipt(), value).digest());
        assert_eq!(
            keys.iter().collect::<std::collections::BTreeSet<_>>().len(),
            4
        );
    }

    #[test]
    fn sdk_native_and_host_implementation_identities_are_bound() {
        let original = fixture_key();
        for field in 0..3 {
            let identities = [
                sha256(b"held-sdk"),
                sha256(b"full-sdk"),
                sha256(b"host-implementation"),
            ];
            let mut changed = identities;
            changed[field] = sha256(b"changed actual implementation or native input");
            let candidate = LocalDependencyKey::from_receipt(
                fixture_receipt(),
                &changed[0],
                &changed[1],
                &changed[2],
                Some(b"access transformer"),
            )
            .unwrap();
            assert_ne!(candidate, original);
        }
    }

    #[test]
    fn key_roundtrip_is_deterministic_and_rejects_noncanonical_or_hidden_fields() {
        let original = fixture_key();
        assert_eq!(original, fixture_key());
        assert_eq!(original.digest().len(), 64);
        let encoded = facet_json::to_string(&original).unwrap();
        let restored: LocalDependencyKey = facet_json::from_str(&encoded).unwrap();
        restored.validate().unwrap();
        assert_eq!(restored, original);
        let mut changed = original.clone();
        changed.descriptor_json = changed.descriptor_json.replace(
            "\"invocation_id\":\"\"",
            "\"invocation_id\":\"hidden-owner\"",
        );
        assert!(changed.validate().is_err());
        let mut changed = original;
        changed.descriptor_json = changed.descriptor_json.replace(
            "\"invocation_id\":\"\"",
            "\"invocation_id\":\"\",\"unadmitted\":true",
        );
        assert!(changed.validate().is_err());
    }
}
