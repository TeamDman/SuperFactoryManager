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
//! Repair e76bef6ade844985e98d8799d139c905fba28c10 retains that closure. Reviewed
//! xtask SHA256 84396dcc1911665f0ecda4ff8d879b9e99150dbd9892696301868c0b8a720264:
//! only adds SDK identity guard and stable manifest metadata; Java change handles
//! late credit with existing JDK collections. No service/native resources; jdeps
//! reports java.base. Exact repaired bytes and SDK recipe are matched separately.
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
            || git.remote_url.as_deref() != Some("https://github.com/TeamDman/facet")
            || git.dirty
            || build.build_system != SourceBuildSystem::CargoCommand
            || !build.tasks.iter().map(String::as_str).eq(tasks)
            || build.output_path.to_string_lossy().replace('\\', "/")
                != "vox/java/target/vox-java-0.10.0-rc.5.jar"
        {
            return None;
        }
        if entry.hash.to_string() == "blake3:4d1e88353f941be926fdf84f1dd8da9bd594b60f"
            && git.commit == "f2afdece6c79e64085d2f8c047e22fe16b2c8c54"
            && build.environment.is_empty()
        {
            return Some(Self {
                jar_bytes: 1_325_104,
                jar_sha256: "ccadaefa439a59138d4ee7f7017d2ea0efb608b61c30100b2333d0ed98962d8d",
            });
        }
        if entry.hash.to_string() == "blake3:549fcd37165645ee461a70f536ae47e1fadd45e8"
            && git.commit == "e76bef6ade844985e98d8799d139c905fba28c10"
            && build.environment.len() == 1
            && build.environment.get("VOX_JAVA_BUILD_JDK").map(String::as_str)
                == Some("Eclipse Adoptium|21.0.8+9-LTS")
        {
            return Some(Self {
                jar_bytes: 1_329_535,
                jar_sha256: "6587629a7d484aab6ea90e1fb4ca4d27cb6eb638029da48d5a01cd99ead8bffe",
            });
        }
        None
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
        let mut repaired = original.clone();
        repaired.source_git.as_mut().unwrap().commit =
            "e76bef6ade844985e98d8799d139c905fba28c10".to_owned();
        repaired.hash = ContentHash::parse("blake3:549fcd37165645ee461a70f536ae47e1fadd45e8").unwrap();
        assert!(FrozenFileDependencyContract::for_artifact(&repaired).is_none());
        repaired.source_build.as_mut().unwrap().environment.insert(
            "VOX_JAVA_BUILD_JDK".to_owned(), "Eclipse Adoptium|21.0.8+9-LTS".to_owned());
        let repaired_contract = FrozenFileDependencyContract::for_artifact(&repaired)
            .expect("exact reviewed repair recipe");
        assert!(repaired_contract.verify_bytes(b"different bytes").is_err());
        repaired.source_build.as_mut().unwrap().environment.insert(
            "EXTRA".to_owned(), "unreviewed".to_owned());
        assert!(FrozenFileDependencyContract::for_artifact(&repaired).is_none());
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
