//! Bounded, read-only lookup of class declarations in a generated project's
//! schema-v2 checksum-pinned dependency JARs. No branch context or acquisition
//! machinery participates in this path. Existing cache path components are
//! checked for links/reparse points, but this is not a transactional defense
//! against filesystem changes between inspection and opening the JAR.

use super::DependencyJavaSymbolIndexBody;
use super::DiagnosticSeverity;
use super::JavaAnalysisDiagnosticOutput;
use super::JavaSourceSpanOutput;
use super::JavaSymbolDefinitionOutput;
use super::JavaSymbolIdentityOutput;
use super::JavaSymbolKind;
use super::ResolutionConfidence;
use crate::cancellation::CancellationToken;
use crate::jar_build::hash::ContentHash;
use crate::jar_build::hash::ContentHashAlgorithm;
use crate::toolchain_lockfile_schema::ToolchainLockfileDocument;
use crate::toolchain_lockfile_schema::parse_document;
use eyre::Context as _;
use std::io;
use std::io::Read;
use std::io::Seek as _;
use std::path::Component;
use std::path::Path;
use zip::ZipArchive;

const MAX_PINNED_JARS: usize = 128;
const MAX_CLASS_BYTES: u64 = 2 * 1024 * 1024;
const CACHE_PREFIX: &str = "$sfm-cache";

pub(crate) struct GeneratedDependencyTypeScan {
    pub(crate) body: DependencyJavaSymbolIndexBody,
    pub(crate) complete: bool,
}

/// Find one fully-qualified type in checksum-pinned JARs already present in
/// the application cache. Missing/stale pins produce typed diagnostics; no
/// cache files are created or repaired.
#[expect(
    clippy::too_many_lines,
    reason = "the bounded cache scan validates each pin before emitting a declaration"
)]
pub(crate) fn scan_generated_dependency_type(
    project_root: &Path,
    cache_root: &Path,
    qualified_name: &str,
    cancellation_token: &CancellationToken,
) -> eyre::Result<GeneratedDependencyTypeScan> {
    let lock_path = project_root.join("sfm-toolchain.lock.json");
    let input = std::fs::read_to_string(&lock_path)
        .wrap_err_with(|| format!("Failed to read {}", lock_path.display()))?;
    let ToolchainLockfileDocument::V2 { lockfile, .. } = parse_document(&input)? else {
        eyre::bail!("generated dependency type lookup requires a schema-v2 project lockfile");
    };
    let entry_name = format!("{}.class", qualified_name.replace('.', "/"));
    let mut artifacts = lockfile
        .artifacts
        .iter()
        .filter(|artifact| {
            artifact.coordinate.is_some()
                && artifact
                    .cache_path
                    .extension()
                    .is_some_and(|extension| extension.eq_ignore_ascii_case("jar"))
        })
        .collect::<Vec<_>>();
    artifacts.sort_by(|left, right| {
        (&left.coordinate, &left.cache_path).cmp(&(&right.coordinate, &right.cache_path))
    });
    artifacts.dedup_by(|left, right| left.cache_path == right.cache_path);
    eyre::ensure!(
        artifacts.len() <= MAX_PINNED_JARS,
        "generated dependency lookup exceeds the {MAX_PINNED_JARS}-JAR bound"
    );

    let mut definitions = Vec::new();
    let mut diagnostics = Vec::new();
    let mut complete = true;
    for artifact in artifacts {
        cancellation_token.bail_if_cancelled()?;
        let coordinate = artifact.coordinate.as_deref().unwrap_or("<unknown>");
        let Some(path) = cached_artifact_path(cache_root, &artifact.cache_path) else {
            complete = false;
            diagnostics.push(unavailable(
                coordinate,
                "pin is outside the application cache",
            ));
            continue;
        };
        match cached_path_contains_reparse_point(cache_root, &path) {
            Ok(true) => {
                complete = false;
                diagnostics.push(unavailable(
                    coordinate,
                    "pinned JAR path contains a symlink or reparse point",
                ));
                continue;
            }
            Err(_) => {
                complete = false;
                diagnostics.push(unavailable(
                    coordinate,
                    "pinned JAR path could not be inspected",
                ));
                continue;
            }
            Ok(false) => {}
        }
        if !path.is_file() {
            complete = false;
            diagnostics.push(unavailable(
                coordinate,
                "pinned JAR is absent from the cache",
            ));
            continue;
        }
        // Validate the whole pinned JAR before treating an absent class as a
        // complete negative result. A stale JAR can omit the queried class.
        let mut file = std::fs::File::open(&path)?;
        let actual = hash_cached_jar(&mut file, artifact.hash.algorithm)?;
        if actual != artifact.hash {
            complete = false;
            diagnostics.push(unavailable(
                coordinate,
                "cached JAR checksum does not match its pin",
            ));
            continue;
        }
        file.rewind()?;
        let Ok(mut archive) = ZipArchive::new(file) else {
            complete = false;
            diagnostics.push(unavailable(coordinate, "cached JAR is invalid"));
            continue;
        };
        let Ok(mut entry) = archive.by_name(&entry_name) else {
            continue;
        };
        if entry.size() > MAX_CLASS_BYTES {
            complete = false;
            diagnostics.push(unavailable(
                coordinate,
                "selected class exceeds the 2 MiB limit",
            ));
            continue;
        }
        let mut bytes = Vec::new();
        (&mut entry)
            .take(MAX_CLASS_BYTES + 1)
            .read_to_end(&mut bytes)?;
        drop(entry);
        drop(archive);
        if bytes.len() as u64 > MAX_CLASS_BYTES {
            complete = false;
            diagnostics.push(unavailable(
                coordinate,
                "selected class exceeds the 2 MiB limit",
            ));
            continue;
        }
        let Ok(kind) = class_kind(&bytes) else {
            complete = false;
            diagnostics.push(unavailable(coordinate, "selected classfile is invalid"));
            continue;
        };
        let name = qualified_name.rsplit('.').next().unwrap_or(qualified_name);
        let span = JavaSourceSpanOutput {
            path: format!("dependency/{}/{entry_name}", artifact.hash.hex()),
            source_set: "dependency:generated-classfile".to_owned(),
            source_hash: format!("blake3:{}", blake3::hash(&bytes).to_hex()),
            start_byte: 0,
            end_byte: 0,
            start_line: 1,
            start_column: 1,
            end_line: 1,
            end_column: 1,
        };
        definitions.push(JavaSymbolDefinitionOutput {
            symbol: JavaSymbolIdentityOutput {
                kind,
                owner: qualified_name.to_owned(),
                name: name.to_owned(),
                descriptor: None,
                qualified_name: qualified_name.to_owned(),
            },
            identifier_span: span.clone(),
            declaration_span: span,
            confidence: ResolutionConfidence::Resolved,
        });
    }
    definitions.sort();
    definitions.dedup();
    diagnostics.sort();
    diagnostics.dedup();
    Ok(GeneratedDependencyTypeScan {
        body: DependencyJavaSymbolIndexBody::new(definitions, Vec::new(), diagnostics),
        complete,
    })
}

fn cached_artifact_path(cache_root: &Path, portable: &Path) -> Option<std::path::PathBuf> {
    let relative = portable.strip_prefix(CACHE_PREFIX).ok()?;
    if relative
        .components()
        .any(|component| !matches!(component, Component::Normal(_) | Component::CurDir))
    {
        return None;
    }
    Some(cache_root.join("minecraft-toolchain").join(relative))
}

/// Inspect the cache root, every existing parent, and the JAR itself without
/// following links. A missing component is handled by the ordinary absent-pin
/// diagnostic after this check.
fn cached_path_contains_reparse_point(cache_root: &Path, path: &Path) -> io::Result<bool> {
    for component in path
        .ancestors()
        .take_while(|ancestor| ancestor.starts_with(cache_root))
        .collect::<Vec<_>>()
        .into_iter()
        .rev()
    {
        match std::fs::symlink_metadata(component) {
            Ok(metadata) if is_reparse_point(&metadata) => return Ok(true),
            Ok(_) => {}
            Err(error) if error.kind() == io::ErrorKind::NotFound => return Ok(false),
            Err(error) => return Err(error),
        }
    }
    Ok(false)
}

fn is_reparse_point(metadata: &std::fs::Metadata) -> bool {
    #[cfg(windows)]
    {
        use std::os::windows::fs::MetadataExt as _;
        metadata.file_attributes() & 0x400 != 0
    }
    #[cfg(not(windows))]
    {
        metadata.file_type().is_symlink()
    }
}

fn unavailable(coordinate: &str, reason: &str) -> JavaAnalysisDiagnosticOutput {
    JavaAnalysisDiagnosticOutput {
        code: "java.dependency-artifact-unavailable".to_owned(),
        severity: DiagnosticSeverity::Error,
        message: format!("Generated-project dependency `{coordinate}`: {reason}"),
        span: None,
    }
}

fn hash_cached_jar(
    file: &mut std::fs::File,
    algorithm: ContentHashAlgorithm,
) -> eyre::Result<ContentHash> {
    let mut reader = std::io::BufReader::new(file);
    let mut buffer = [0_u8; 16 * 1024];
    let mut blake3 = blake3::Hasher::new();
    let mut sha1 = sha1::Sha1::default();
    loop {
        let count = reader.read(&mut buffer)?;
        if count == 0 {
            break;
        }
        match algorithm {
            ContentHashAlgorithm::Blake3 => {
                blake3.update(&buffer[..count]);
            }
            ContentHashAlgorithm::Sha1 => {
                sha1::Digest::update(&mut sha1, &buffer[..count]);
            }
        }
    }
    let mut value = [0_u8; 20];
    match algorithm {
        ContentHashAlgorithm::Blake3 => blake3.finalize_xof().fill(&mut value),
        ContentHashAlgorithm::Sha1 => value.copy_from_slice(&sha1::Digest::finalize(sha1)),
    }
    Ok(ContentHash { value, algorithm })
}

fn class_kind(bytes: &[u8]) -> eyre::Result<JavaSymbolKind> {
    eyre::ensure!(
        bytes.get(..4) == Some(&[0xca, 0xfe, 0xba, 0xbe]),
        "invalid classfile magic"
    );
    let pool_count = usize::from(read_u16(bytes, 8)?);
    let mut offset = 10;
    let mut index = 1;
    while index < pool_count {
        let tag = *bytes
            .get(offset)
            .ok_or_else(|| eyre::eyre!("truncated constant pool"))?;
        offset += 1;
        let skip = match tag {
            1 => 2 + usize::from(read_u16(bytes, offset)?),
            3 | 4 | 9 | 10 | 11 | 12 | 17 | 18 => 4,
            5 | 6 => {
                index += 1;
                8
            }
            7 | 8 | 16 | 19 | 20 => 2,
            15 => 3,
            _ => eyre::bail!("unknown classfile constant tag {tag}"),
        };
        offset = offset
            .checked_add(skip)
            .ok_or_else(|| eyre::eyre!("classfile offset overflow"))?;
        eyre::ensure!(offset <= bytes.len(), "truncated constant pool entry");
        index += 1;
    }
    let flags = read_u16(bytes, offset)?;
    Ok(if flags & 0x2000 != 0 {
        JavaSymbolKind::Annotation
    } else if flags & 0x4000 != 0 {
        JavaSymbolKind::Enum
    } else if flags & 0x0200 != 0 {
        JavaSymbolKind::Interface
    } else {
        JavaSymbolKind::Class
    })
}

fn read_u16(bytes: &[u8], offset: usize) -> eyre::Result<u16> {
    let pair: [u8; 2] = bytes
        .get(offset..offset.saturating_add(2))
        .ok_or_else(|| eyre::eyre!("truncated classfile"))?
        .try_into()?;
    Ok(u16::from_be_bytes(pair))
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Cursor;
    use std::io::Write as _;
    use zip::ZipWriter;
    use zip::write::SimpleFileOptions;

    #[test]
    fn generated_1192_cached_type_and_1211_missing_pin_are_isolated() {
        let temporary = tempfile::tempdir().expect("test roots");
        let cache_root = temporary.path().join("cache");
        let cached_jar = cache_root.join("minecraft-toolchain/maven/example/library.jar");
        std::fs::create_dir_all(cached_jar.parent().expect("JAR parent")).unwrap();
        let jar = fixture_jar("example/External.class", &minimal_classfile(0x0001));
        std::fs::write(&cached_jar, &jar).unwrap();
        let hash = ContentHash::from_bytes(&jar, ContentHashAlgorithm::Blake3);
        let first = temporary.path().join("mc-version/1.19.2");
        let second = temporary.path().join("mc-version/1.21.1");
        std::fs::create_dir_all(&first).unwrap();
        std::fs::create_dir_all(&second).unwrap();
        write_lock(&first, "1.19.2", hash);
        write_lock(&second, "1.21.1", hash);

        let found = scan_generated_dependency_type(
            &first,
            &cache_root,
            "example.External",
            &CancellationToken::new(),
        )
        .unwrap();
        assert!(found.complete);
        assert_eq!(found.body.definitions.len(), 1);
        assert_eq!(
            found.body.definitions[0].symbol.canonical_selector(),
            "example.External"
        );
        assert!(
            found.body.definitions[0]
                .identifier_span
                .path
                .ends_with("External.class")
        );

        std::fs::remove_file(&cached_jar).unwrap();
        let missing = scan_generated_dependency_type(
            &second,
            &cache_root,
            "example.External",
            &CancellationToken::new(),
        )
        .unwrap();
        assert!(!missing.complete);
        assert!(missing.body.definitions.is_empty());
        assert_eq!(
            missing.body.diagnostics[0].code,
            "java.dependency-artifact-unavailable"
        );
        assert!(
            missing.body.diagnostics[0]
                .message
                .contains("pinned JAR is absent")
        );

        let changed = fixture_jar("example/External.class", &minimal_classfile(0x0201));
        std::fs::write(&cached_jar, changed).unwrap();
        let stale = scan_generated_dependency_type(
            &second,
            &cache_root,
            "example.External",
            &CancellationToken::new(),
        )
        .unwrap();
        assert!(!stale.complete);
        assert!(stale.body.definitions.is_empty());
        assert!(
            stale.body.diagnostics[0]
                .message
                .contains("checksum does not match")
        );

        // A stale JAR without the requested class must not masquerade as a
        // complete lookup that found no matching type.
        let stale_missing_class = fixture_jar("example/Other.class", &minimal_classfile(0x0001));
        std::fs::write(&cached_jar, &stale_missing_class).unwrap();
        let stale_negative = scan_generated_dependency_type(
            &second,
            &cache_root,
            "example.External",
            &CancellationToken::new(),
        )
        .unwrap();
        assert!(!stale_negative.complete);
        assert!(stale_negative.body.definitions.is_empty());
        assert!(
            stale_negative.body.diagnostics[0]
                .message
                .contains("checksum does not match")
        );

        write_lock(
            &second,
            "1.21.1",
            ContentHash::from_bytes(&stale_missing_class, ContentHashAlgorithm::Blake3),
        );
        let valid_negative = scan_generated_dependency_type(
            &second,
            &cache_root,
            "example.External",
            &CancellationToken::new(),
        )
        .unwrap();
        assert!(valid_negative.complete);
        assert!(valid_negative.body.definitions.is_empty());
        assert!(valid_negative.body.diagnostics.is_empty());
    }

    #[test]
    fn generated_dependency_rejects_linked_jar_and_parent_with_typed_diagnostic() {
        let temporary = tempfile::tempdir().expect("test roots");
        let project_root = temporary.path().join("project");
        std::fs::create_dir(&project_root).unwrap();
        let outside_dir = temporary.path().join("outside");
        std::fs::create_dir(&outside_dir).unwrap();
        let outside_jar = outside_dir.join("library.jar");
        let jar = fixture_jar("example/External.class", &minimal_classfile(0x0001));
        std::fs::write(&outside_jar, &jar).unwrap();
        write_lock(
            &project_root,
            "1.21.1",
            ContentHash::from_bytes(&jar, ContentHashAlgorithm::Blake3),
        );

        let parent_cache = temporary.path().join("parent-cache");
        let parent_link = parent_cache.join("minecraft-toolchain/maven/example");
        std::fs::create_dir_all(parent_link.parent().unwrap()).unwrap();
        if let Err(error) = symlink_dir(&outside_dir, &parent_link) {
            // Creating symlinks may require a Windows privilege unavailable to
            // the test runner; the production check is platform-independent.
            #[cfg(windows)]
            if error.kind() == io::ErrorKind::PermissionDenied {
                eprintln!("skipping symlink test: Windows symlink privilege is unavailable");
                return;
            }
            panic!("could not create test directory symlink: {error}");
        }
        assert_linked_pin_unavailable(&project_root, &parent_cache);

        let file_cache = temporary.path().join("file-cache");
        let jar_link = file_cache.join("minecraft-toolchain/maven/example/library.jar");
        std::fs::create_dir_all(jar_link.parent().unwrap()).unwrap();
        symlink_file(&outside_jar, &jar_link).unwrap();
        assert_linked_pin_unavailable(&project_root, &file_cache);
    }

    #[test]
    #[ignore = "requires the local offline artifact cache"]
    fn real_generated_1192_and_1211_roots_resolve_pinned_types() {
        let minecraft = Path::new(env!("CARGO_MANIFEST_DIR")).join("../../minecraft/mc-version");
        let cache = crate::paths::CacheHome::resolve().unwrap();
        for (version, qualified_name) in [
            ("1.19.2", "org.spongepowered.asm.mixin.Mixin"),
            ("1.21.1", "com.google.gson.Gson"),
        ] {
            let scanned = scan_generated_dependency_type(
                &minecraft.join(version),
                &cache.0,
                qualified_name,
                &CancellationToken::new(),
            )
            .unwrap();
            assert!(
                scanned.complete,
                "{version}: {:?}",
                scanned.body.diagnostics
            );
            assert!(
                scanned
                    .body
                    .definitions
                    .iter()
                    .any(|definition| definition.symbol.qualified_name == qualified_name),
                "{version}: {qualified_name}"
            );
        }
    }

    fn write_lock(root: &Path, version: &str, hash: ContentHash) {
        let input = format!(
            r#"{{"schema_version":2,"minecraft_version":"{version}","maven_cache_dir":"$sfm-cache/maven","allow_local_artifact_cache":false,"repositories":[],"dependencies":[],"artifacts":[{{"coordinate":"example:library:1","source":"remote-maven","repository":null,"url":null,"cache_path":"$sfm-cache/maven/example/library.jar","original_path":null,"source_relative_path":null,"source_git":null,"source_build":null,"hash":"{hash}","weak":null}}]}}"#
        );
        std::fs::write(root.join("sfm-toolchain.lock.json"), input).unwrap();
    }

    fn assert_linked_pin_unavailable(project_root: &Path, cache_root: &Path) {
        let scanned = scan_generated_dependency_type(
            project_root,
            cache_root,
            "example.External",
            &CancellationToken::new(),
        )
        .unwrap();
        assert!(!scanned.complete);
        assert!(scanned.body.definitions.is_empty());
        assert_eq!(
            scanned.body.diagnostics[0].code,
            "java.dependency-artifact-unavailable"
        );
        assert!(
            scanned.body.diagnostics[0]
                .message
                .contains("symlink or reparse point")
        );
    }

    #[cfg(windows)]
    fn symlink_dir(target: &Path, link: &Path) -> io::Result<()> {
        std::os::windows::fs::symlink_dir(target, link)
    }

    #[cfg(not(windows))]
    fn symlink_dir(target: &Path, link: &Path) -> io::Result<()> {
        std::os::unix::fs::symlink(target, link)
    }

    #[cfg(windows)]
    fn symlink_file(target: &Path, link: &Path) -> io::Result<()> {
        std::os::windows::fs::symlink_file(target, link)
    }

    #[cfg(not(windows))]
    fn symlink_file(target: &Path, link: &Path) -> io::Result<()> {
        std::os::unix::fs::symlink(target, link)
    }

    fn fixture_jar(entry: &str, bytes: &[u8]) -> Vec<u8> {
        let mut writer = ZipWriter::new(Cursor::new(Vec::new()));
        writer
            .start_file(entry, SimpleFileOptions::default())
            .unwrap();
        writer.write_all(bytes).unwrap();
        writer.finish().unwrap().into_inner()
    }

    fn minimal_classfile(flags: u16) -> Vec<u8> {
        let mut bytes = vec![0xca, 0xfe, 0xba, 0xbe, 0, 0, 0, 61, 0, 1];
        bytes.extend(flags.to_be_bytes());
        bytes
    }
}
