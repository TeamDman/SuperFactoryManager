//! Reviewed artifact-specific runtime contract; no generic missing-POM fallback.
//! Source: <https://github.com/TeamDman/facet> at f2afdece6c79e64085d2f8c047e22fe16b2c8c54.
//! vox/java/README.md SHA256 dcb38be547586a58b8e5e8e60b5c02fab9cf0c5a74393bedeafa85c669a99198:
//! explicitly dependency-free Java17; Phon/Vox/generated terminal bindings bundled.
//! vox/xtask/src/main.rs SHA256 f885a8e3bcd7d27632ad7c792031034234e73e5268368688be7e3bcb04fcfb21:
//! `java_runtime_sources` (659), `compile_java` (596), `package_java` (761) compile with no
//! external classpath and assemble runtime classes plus a manifest without Class-Path.
//! Reviewed runtime trees declare no service-provider/native-library loaders; exact
//! JAR has no service/native resources. jdeps corroborates, but does not grant policy.
//! Contract: Java17+, zero external runtime artifacts for these exact reviewed bytes.
//! Peer/service availability is application behavior, not established by this contract.

use super::ArtifactLockEntry;
use super::ArtifactSource;
use super::engine::SourceBuildSystem;

pub(crate) struct FrozenFileDependencyContract {
    jar_bytes: usize,
    jar_sha256: &'static str,
}

impl FrozenFileDependencyContract {
    pub(crate) fn for_artifact(entry: &ArtifactLockEntry) -> Option<Self> {
        let git = entry.source_git.as_ref()?;
        let build = entry.source_build.as_ref()?;
        let tasks = [
            "run",
            "--locked",
            "--package",
            "vox-xtask",
            "--",
            "package-java",
        ];
        if entry.coordinate.as_deref() != Some("org.facet:vox-java:0.10.0-rc.5")
            || entry.source != ArtifactSource::SourceBuild
            || entry.weak.is_some()
            || entry.hash.to_string() != "blake3:4d1e88353f941be926fdf84f1dd8da9bd594b60f"
            || git.commit != "f2afdece6c79e64085d2f8c047e22fe16b2c8c54"
            || git.remote_url.as_deref() != Some("https://github.com/TeamDman/facet")
            || git.dirty
            || build.build_system != SourceBuildSystem::CargoCommand
            || !build.tasks.iter().map(String::as_str).eq(tasks)
            || !build.environment.is_empty()
            || build.output_path.to_string_lossy().replace('\\', "/")
                != "vox/java/target/vox-java-0.10.0-rc.5.jar"
        {
            return None;
        }
        Some(Self {
            jar_bytes: 1_325_104,
            jar_sha256: "ccadaefa439a59138d4ee7f7017d2ea0efb608b61c30100b2333d0ed98962d8d",
        })
    }

    pub(crate) fn verify_bytes(&self, raw: &[u8]) -> eyre::Result<()> {
        let digest = <sha2::Sha256 as sha2::Digest>::digest(raw);
        eyre::ensure!(
            raw.len() == self.jar_bytes && format!("{digest:x}") == self.jar_sha256,
            "reviewed runtime dependency contract artifact size/SHA256 mismatch"
        );
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::jar_build::SourceBuildProvenance;
    use crate::jar_build::SourceGitProvenance;
    use crate::jar_build::hash::ContentHash;
    use std::collections::BTreeMap;
    use std::path::PathBuf;

    #[test]
    fn reviewed_file_contract_requires_exact_artifact_commit_recipe_and_bytes() {
        let mut entry = ArtifactLockEntry {
            coordinate: Some("org.facet:vox-java:0.10.0-rc.5".to_owned()),
            source: ArtifactSource::SourceBuild,
            repository: None,
            url: None,
            cache_path: PathBuf::from("unused.jar"),
            original_path: None,
            source_relative_path: None,
            source_git: Some(SourceGitProvenance {
                root: PathBuf::from("unused"),
                commit: "f2afdece6c79e64085d2f8c047e22fe16b2c8c54".to_owned(),
                branch: "unused".to_owned(),
                dirty: false,
                remote_url: Some("https://github.com/TeamDman/facet".to_owned()),
            }),
            source_build: Some(SourceBuildProvenance {
                build_system: SourceBuildSystem::CargoCommand,
                tasks: [
                    "run",
                    "--locked",
                    "--package",
                    "vox-xtask",
                    "--",
                    "package-java",
                ]
                .map(str::to_owned)
                .to_vec(),
                environment: BTreeMap::new(),
                output_path: PathBuf::from("vox/java/target/vox-java-0.10.0-rc.5.jar"),
            }),
            hash: ContentHash::parse("blake3:4d1e88353f941be926fdf84f1dd8da9bd594b60f")
                .expect("fixed reviewed pin"),
            weak: None,
        };
        let contract =
            FrozenFileDependencyContract::for_artifact(&entry).expect("exact reviewed metadata");
        assert!(
            contract.verify_bytes(b"different bytes").is_err(),
            "metadata alone cannot authorize different artifact bytes"
        );
        let original = entry.clone();
        entry.source_git.as_mut().expect("git").commit = "different".to_owned();
        assert!(FrozenFileDependencyContract::for_artifact(&entry).is_none());
        entry = original.clone();
        entry
            .source_build
            .as_mut()
            .expect("build")
            .tasks
            .push("extra-task".to_owned());
        assert!(FrozenFileDependencyContract::for_artifact(&entry).is_none());
        entry = original.clone();
        entry.coordinate = Some("other:source-built:1".to_owned());
        assert!(
            FrozenFileDependencyContract::for_artifact(&entry).is_none(),
            "other strong source-built artifacts need their own reviewed contract"
        );
        entry = original;
        entry.hash = ContentHash::parse("blake3:0000000000000000000000000000000000000000")
            .expect("test hash");
        assert!(FrozenFileDependencyContract::for_artifact(&entry).is_none());
    }
}
