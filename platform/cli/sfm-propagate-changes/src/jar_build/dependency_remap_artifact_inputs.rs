//! One current pinned dependency jar retained through remap-cache effects.
//!
//! The common SDK/tool/mapping owner stays on the caller thread. This owner
//! captures only this dependency's actual bytes and retains its existing leases.

#[cfg(test)]
#[path = "dependency_remap_artifact_inputs_tests.rs"]
mod tests;

use super::ArtifactPlan;
use super::DirectoryLeases;
use super::ExecutionContext;
use super::ForgePreparationInputRequest;
use super::HeldForgePreparationInput;
use super::MAX_DIRECTORY_LEASES;
use super::append;
use super::artifact_request;
use crate::cancellation::CancellationToken;
use eyre::Result;
use eyre::ensure;
use sha2::Digest as _;
use sha2::Sha256;

pub(in crate::jar_build::engine) struct DependencyRemapArtifactInputs {
    fingerprint: String,
    held: HeldForgePreparationInput,
    _directories: DirectoryLeases,
}

impl DependencyRemapArtifactInputs {
    pub(in crate::jar_build::engine) fn capture(
        context: &ExecutionContext<'_>,
        artifact: &ArtifactPlan,
        coordinate: &str,
        shared_key: &str,
    ) -> Result<Option<Self>> {
        context.bail_if_cancelled()?;
        context.assert_allowed_input(&artifact.cache_path)?;
        ensure!(
            artifact.coordinate.as_deref() == Some(coordinate),
            "Dependency remap coordinate differs from the current resolved artifact"
        );
        let request = artifact_request(context, artifact)?;
        Self::capture_selected(
            &request,
            coordinate,
            shared_key,
            &context.cancellation_token,
        )
    }

    fn capture_selected(
        request: &ForgePreparationInputRequest,
        coordinate: &str,
        shared_key: &str,
        cancellation: &CancellationToken,
    ) -> Result<Option<Self>> {
        ensure!(
            !coordinate.is_empty() && valid_shared_key(shared_key),
            "Dependency remap requires a full common authority key and coordinate"
        );
        let parent = request
            .path
            .parent()
            .ok_or_else(|| eyre::eyre!("Dependency has no parent"))?;
        if parent.ancestors().count() > MAX_DIRECTORY_LEASES {
            return Ok(None);
        }
        let mut directories = DirectoryLeases::default();
        let Some(captured) =
            HeldForgePreparationInput::capture(request, &mut directories, cancellation)?
        else {
            return Ok(None);
        };
        let fingerprint = artifact_fingerprint(shared_key, coordinate, &captured.input);
        Ok(Some(Self {
            fingerprint,
            held: captured.input,
            _directories: directories,
        }))
    }

    pub(in crate::jar_build::engine) fn fingerprint(&self) -> &str {
        &self.fingerprint
    }

    pub(in crate::jar_build::engine) fn recheck(
        &self,
        context: &ExecutionContext<'_>,
    ) -> Result<()> {
        context.bail_if_cancelled()?;
        self.held.recheck()
    }
}

fn valid_shared_key(key: &str) -> bool {
    key.strip_prefix("sha256:").is_some_and(|digest| {
        digest.len() == 64
            && digest
                .bytes()
                .all(|byte| byte.is_ascii_digit() || (b'a'..=b'f').contains(&byte))
    })
}

fn artifact_fingerprint(
    shared_key: &str,
    coordinate: &str,
    held: &HeldForgePreparationInput,
) -> String {
    let mut hash = Sha256::new();
    append(
        &mut hash,
        b"sfm:ordinary_forge_dependency_remap_actual_inputs@1",
    );
    append(&mut hash, shared_key.as_bytes());
    append(&mut hash, coordinate.as_bytes());
    append(&mut hash, &held.bytes.to_le_bytes());
    append(&mut hash, held.sha256.as_bytes());
    format!("sha256:{:x}", hash.finalize())
}
