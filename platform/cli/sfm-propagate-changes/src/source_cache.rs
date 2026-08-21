use crate::jar_build::hash::ContentHash;
use crate::jar_build::hash::ContentHashAlgorithm;
use std::path::PathBuf;

const SOURCE_CACHE_ROOT: &str = "$sfm-cache/sources";

pub struct MavenSourcePaths {
    pub archive: PathBuf,
    pub tree: PathBuf,
}

pub struct GitSourcePaths {
    pub repository: PathBuf,
    pub tree: PathBuf,
}

pub struct JdkSourcePaths {
    pub tree: PathBuf,
}

pub struct SourceCacheLayout;

impl SourceCacheLayout {
    #[must_use]
    pub fn maven(coordinate: &str, source_hash: ContentHash) -> MavenSourcePaths {
        let root = PathBuf::from(SOURCE_CACHE_ROOT)
            .join("maven")
            .join(stable_key(coordinate))
            .join(source_hash.hex());
        MavenSourcePaths {
            archive: root.join("sources.jar"),
            tree: root.join("tree"),
        }
    }

    #[must_use]
    pub fn git(remote_url: &str, commit: &str) -> GitSourcePaths {
        let remote_key = stable_key(remote_url);
        GitSourcePaths {
            repository: PathBuf::from(SOURCE_CACHE_ROOT)
                .join("git/repositories")
                .join(format!("{remote_key}.git")),
            tree: PathBuf::from(SOURCE_CACHE_ROOT)
                .join("git/trees")
                .join(remote_key)
                .join(stable_key(commit)),
        }
    }

    #[must_use]
    pub fn decompiled(binary_hash: ContentHash, decompiler_fingerprint: &str) -> PathBuf {
        PathBuf::from(SOURCE_CACHE_ROOT)
            .join("decompiled")
            .join(binary_hash.hex())
            .join(stable_key(decompiler_fingerprint))
            .join("tree")
    }

    #[must_use]
    pub fn platform(minecraft_version: &str, pipeline_fingerprint: &str) -> PathBuf {
        PathBuf::from(SOURCE_CACHE_ROOT)
            .join("platform")
            .join(stable_key(minecraft_version))
            .join(stable_key(pipeline_fingerprint))
            .join("tree")
    }

    /// Return the portable, content-addressed source-stub location for the
    /// libraries authenticated by one exact Minecraft version JSON.
    #[must_use]
    pub fn minecraft_classfile_stubs(
        minecraft_version: &str,
        version_json_hash: ContentHash,
        generator_fingerprint: &str,
    ) -> PathBuf {
        PathBuf::from(SOURCE_CACHE_ROOT)
            .join("classfile-stubs/minecraft")
            .join(stable_key(minecraft_version))
            .join(version_json_hash.hex())
            .join(stable_key(generator_fingerprint))
            .join("tree")
    }

    /// Return the portable, content-addressed extraction location for one
    /// exact JDK source archive and parser/index consumer.
    #[must_use]
    pub fn jdk(
        java_release: &str,
        source_hash: ContentHash,
        parser_fingerprint: &str,
    ) -> JdkSourcePaths {
        JdkSourcePaths {
            tree: PathBuf::from(SOURCE_CACHE_ROOT)
                .join("jdk")
                .join(stable_key(java_release))
                .join(source_hash.hex())
                .join(stable_key(parser_fingerprint))
                .join("tree"),
        }
    }
}

fn stable_key(value: &str) -> String {
    let readable = value
        .chars()
        .map(|character| {
            if character.is_ascii_alphanumeric() || matches!(character, '-' | '_' | '.') {
                character.to_ascii_lowercase()
            } else {
                '-'
            }
        })
        .collect::<String>();
    let readable = readable
        .trim_matches('-')
        .chars()
        .take(48)
        .collect::<String>();
    let hash = ContentHash::from_bytes(value.as_bytes(), ContentHashAlgorithm::Blake3);
    if readable.is_empty() {
        hash.short_hex(16)
    } else {
        format!("{readable}-{}", hash.short_hex(16))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn paths_are_portable_stable_and_content_addressed() {
        let source_hash = ContentHash::from_bytes(b"sources", ContentHashAlgorithm::Blake3);
        let maven = SourceCacheLayout::maven(
            "org.squiddev:cc-tweaked-1.19.2:1.101.3:sources",
            source_hash,
        );
        assert!(maven.archive.starts_with(SOURCE_CACHE_ROOT));
        assert_eq!(maven.archive.file_name().unwrap(), "sources.jar");
        assert_eq!(maven.tree.file_name().unwrap(), "tree");
        assert!(!maven.archive.is_absolute());

        let first = SourceCacheLayout::git("https://github.com/cc-tweaked/CC-Tweaked.git", "abc");
        let repeated =
            SourceCacheLayout::git("https://github.com/cc-tweaked/CC-Tweaked.git", "abc");
        let other_revision =
            SourceCacheLayout::git("https://github.com/cc-tweaked/CC-Tweaked.git", "def");
        assert_eq!(first.repository, repeated.repository);
        assert_eq!(first.tree, repeated.tree);
        assert_eq!(first.repository, other_revision.repository);
        assert_ne!(first.tree, other_revision.tree);
        assert!(!first.repository.is_absolute());
        assert!(!first.tree.is_absolute());

        let jdk = SourceCacheLayout::jdk("17", source_hash, "arborium-java/2.18.1");
        assert!(jdk.tree.starts_with(SOURCE_CACHE_ROOT));
        assert_eq!(jdk.tree.file_name().unwrap(), "tree");
        assert!(!jdk.tree.is_absolute());

        let stubs = SourceCacheLayout::minecraft_classfile_stubs(
            "1.19.2",
            source_hash,
            "sfm.minecraft-classfile-type-stubs/1",
        );
        assert!(stubs.starts_with(SOURCE_CACHE_ROOT));
        assert_eq!(stubs.file_name().unwrap(), "tree");
        assert!(!stubs.is_absolute());
    }

    #[test]
    fn arbitrary_fingerprints_cannot_create_path_segments() {
        let hash = ContentHash::from_bytes(b"binary", ContentHashAlgorithm::Blake3);
        let path = SourceCacheLayout::decompiled(hash, "../../machine:specific\\fingerprint");
        assert!(path.starts_with(SOURCE_CACHE_ROOT));
        assert!(
            !path
                .components()
                .any(|component| component.as_os_str() == "..")
        );
        assert_eq!(path.file_name().unwrap(), "tree");
    }
}
