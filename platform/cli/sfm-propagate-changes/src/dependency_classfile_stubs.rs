use crate::artifact_lock::ArtifactLock;
use crate::cancellation::CancellationToken;
use crate::dependency_inventory::DependencyInventory;
use crate::dependency_sources::SourcePreflight;
use crate::jar_build::hash::ContentHash;
use crate::payload_fetcher::PayloadFetcher;
use crate::source_cache::SourceCacheLayout;
use crate::source_decompile::acquire_locked_artifact;
use crate::toolchain_lockfile_schema::version::v3::ArtifactProvenanceV3;
use crate::toolchain_lockfile_schema::version::v3::ArtifactPurposeV3;
use crate::toolchain_lockfile_schema::version::v3::ArtifactV3;
use crate::toolchain_lockfile_schema::version::v3::ComponentAcquisitionV3;
use crate::toolchain_lockfile_schema::version::v3::PlatformPipelineSourceDeclarationV3;
use crate::toolchain_lockfile_schema::version::v3::PlatformPipelineSourceDerivedChecksV3;
use crate::toolchain_lockfile_schema::version::v3::PlatformPipelineSourceProviderV3;
use crate::toolchain_lockfile_schema::version::v3::SourceProviderV3;
use crate::toolchain_lockfile_schema::version::v3::ToolchainComponentKindV3;
use eyre::Context;
use facet::Facet;
use std::collections::BTreeMap;
use std::collections::BTreeSet;
use std::fs;
use std::io::Read;
use std::path::Path;
use std::path::PathBuf;
use zip::ZipArchive;

pub(crate) const MINECRAFT_CLASSFILE_STUB_COMPONENT_ID: &str = "classpath-type-stubs";
pub(crate) const MINECRAFT_CLASSFILE_STUB_PROVIDER_ID: &str = "version-json-classfile-type-stubs";
const GENERATOR_FINGERPRINT: &str = "sfm.minecraft-classfile-type-stubs/1";
const JAVA_RELEASE: u16 = 17;
const COMPLETION_FILE: &str = ".sfm-classfile-type-stubs";
const TYPES_PER_FILE: usize = 256;

/// One in-memory source component derived from the already-locked Minecraft
/// version JSON. The checked-in toolchain lock is never modified.
#[derive(Clone, Debug)]
pub(crate) struct LockedMinecraftClassfileStubSource {
    pub(crate) target: String,
    pub(crate) minecraft_version: String,
    pub(crate) version_json_artifact: ArtifactV3,
    pub(crate) provider: PlatformPipelineSourceProviderV3,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(crate) struct ClassfileStubMaterialization {
    pub(crate) libraries: usize,
    pub(crate) types: usize,
    pub(crate) shadowed_types: usize,
    pub(crate) reused: bool,
}

/// Attach the generated type-stub component to the effective in-memory lock.
/// Its identity is transitively authenticated by the locked version JSON hash.
pub(crate) fn attach_locked_minecraft_classfile_stub_source(
    inventory: &mut DependencyInventory,
) -> eyre::Result<LockedMinecraftClassfileStubSource> {
    let dependency_id = inventory.lockfile.platform.minecraft_dependency.clone();
    let dependency_index = inventory
        .lockfile
        .dependencies
        .iter()
        .position(|dependency| dependency.id == dependency_id)
        .ok_or_else(|| eyre::eyre!("Unknown Minecraft dependency `{dependency_id}`"))?;
    let source_component = inventory.lockfile.dependencies[dependency_index]
        .components
        .iter()
        .find(|component| {
            matches!(
                &component.declaration.acquisition,
                ComponentAcquisitionV3::Toolchain(toolchain)
                    if toolchain.kind == ToolchainComponentKindV3::Minecraft
            ) && component.id != MINECRAFT_CLASSFILE_STUB_COMPONENT_ID
        })
        .cloned()
        .ok_or_else(|| {
            eyre::eyre!(
                "Minecraft dependency `{dependency_id}` has no toolchain component to authenticate classfile stubs"
            )
        })?;
    let ComponentAcquisitionV3::Toolchain(toolchain) = &source_component.declaration.acquisition
    else {
        unreachable!("component selected by toolchain acquisition")
    };
    let minecraft_version = toolchain.requested_version.clone();
    let version_json_artifact = inventory
        .artifact_by_id(&source_component.derived_checks.artifact_id)
        .cloned()
        .ok_or_else(|| {
            eyre::eyre!(
                "Minecraft component references unknown version JSON artifact `{}`",
                source_component.derived_checks.artifact_id
            )
        })?;
    let fingerprint = stub_provider_fingerprint(&minecraft_version, version_json_artifact.hash);
    let provider = PlatformPipelineSourceProviderV3 {
        id: MINECRAFT_CLASSFILE_STUB_PROVIDER_ID.to_owned(),
        declaration: PlatformPipelineSourceDeclarationV3 {
            kind: ToolchainComponentKindV3::Minecraft,
            roots: Vec::new(),
        },
        derived_checks: PlatformPipelineSourceDerivedChecksV3 {
            fingerprint,
            tree_cache_path: SourceCacheLayout::minecraft_classfile_stubs(
                &minecraft_version,
                version_json_artifact.hash,
                GENERATOR_FINGERPRINT,
            ),
        },
    };
    if !inventory.lockfile.dependencies[dependency_index]
        .components
        .iter()
        .any(|component| component.id == MINECRAFT_CLASSFILE_STUB_COMPONENT_ID)
    {
        let mut component = source_component;
        MINECRAFT_CLASSFILE_STUB_COMPONENT_ID.clone_into(&mut component.id);
        component.source_providers = vec![SourceProviderV3::PlatformPipeline(provider.clone())];
        inventory.lockfile.dependencies[dependency_index]
            .components
            .push(component);
    }
    inventory.lockfile.validate()?;
    Ok(LockedMinecraftClassfileStubSource {
        target: format!("{dependency_id}/{MINECRAFT_CLASSFILE_STUB_COMPONENT_ID}"),
        minecraft_version,
        version_json_artifact,
        provider,
    })
}

#[must_use]
pub(crate) fn is_minecraft_classfile_stub_target(target: &str) -> bool {
    target
        .split_once('/')
        .is_some_and(|(_, component)| component == MINECRAFT_CLASSFILE_STUB_COMPONENT_ID)
}

/// Materialize navigable Java type declarations for classpath libraries that
/// have no authoritative source declaration. Every input is authenticated by
/// the locked version JSON or by a SHA-1 contained in that exact document.
pub(crate) fn materialize_locked_minecraft_classfile_stubs(
    inventory: &DependencyInventory,
    source: &LockedMinecraftClassfileStubSource,
    preflight: &SourcePreflight,
    cancellation_token: &CancellationToken,
    fetcher: &dyn PayloadFetcher,
) -> eyre::Result<ClassfileStubMaterialization> {
    cancellation_token.bail_if_cancelled()?;
    let tree = inventory.local_path(&source.provider.derived_checks.tree_cache_path);
    let lock_path = tree
        .parent()
        .unwrap_or(&tree)
        .join("classfile-type-stubs.lock");
    let _lock = ArtifactLock::acquire_with_cancellation(
        &lock_path,
        format!(
            "Minecraft {} classfile type stubs",
            source.minecraft_version
        ),
        cancellation_token.clone(),
    )?;
    let shadowed = authoritative_top_level_types(preflight, &source.target)?;
    let completion =
        materialization_fingerprint(&source.provider.derived_checks.fingerprint, &shadowed);
    if completed_tree_matches(&tree, &completion) {
        return Ok(ClassfileStubMaterialization {
            libraries: 0,
            types: count_stub_types(&tree)?,
            shadowed_types: shadowed.len(),
            reused: true,
        });
    }

    let libraries =
        acquire_authenticated_libraries(inventory, source, cancellation_token, fetcher)?;

    let parent = tree
        .parent()
        .ok_or_else(|| eyre::eyre!("Classfile type-stub tree has no parent: {}", tree.display()))?;
    fs::create_dir_all(parent)
        .wrap_err_with(|| format!("Failed to create {}", parent.display()))?;
    let temporary = tempfile::Builder::new()
        .prefix(".sfm-classfile-stubs-")
        .tempdir_in(parent)
        .wrap_err_with(|| {
            format!(
                "Failed to stage classfile type stubs in {}",
                parent.display()
            )
        })?;
    let staged = temporary.path().join("tree");
    fs::create_dir(&staged).wrap_err_with(|| format!("Failed to create {}", staged.display()))?;
    let stats = generate_stub_tree(&libraries, &shadowed, &staged, cancellation_token)?;
    fs::write(staged.join(COMPLETION_FILE), &completion)
        .wrap_err_with(|| format!("Failed to complete {}", staged.display()))?;
    if tree.exists() {
        fs::remove_dir_all(&tree)
            .wrap_err_with(|| format!("Failed to replace {}", tree.display()))?;
    }
    fs::rename(&staged, &tree).wrap_err_with(|| format!("Failed to publish {}", tree.display()))?;
    Ok(ClassfileStubMaterialization {
        libraries: libraries.len(),
        types: stats.types,
        shadowed_types: stats.shadowed_types,
        reused: false,
    })
}

fn acquire_authenticated_libraries(
    inventory: &DependencyInventory,
    source: &LockedMinecraftClassfileStubSource,
    cancellation_token: &CancellationToken,
    fetcher: &dyn PayloadFetcher,
) -> eyre::Result<Vec<AuthenticatedLibrary>> {
    let version_json_path = acquire_locked_artifact(
        inventory,
        &source.version_json_artifact,
        cancellation_token,
        fetcher,
    )?;
    let version_json_text = fs::read_to_string(&version_json_path)
        .wrap_err_with(|| format!("Failed to read {}", version_json_path.display()))?;
    let version: MinecraftVersionDocument = facet_json::from_str(&version_json_text)
        .wrap_err_with(|| format!("Failed to parse {}", version_json_path.display()))?;
    let mut libraries = Vec::new();
    for library in version.libraries {
        cancellation_token.bail_if_cancelled()?;
        let Some(downloads) = library.downloads else {
            continue;
        };
        let Some(artifact) = downloads.artifact else {
            continue;
        };
        let Some(hash) = artifact.sha1 else {
            tracing::warn!(
                coordinate = %library.name,
                path = %artifact.path,
                "Ignoring unauthenticated Minecraft library while building classfile type stubs"
            );
            continue;
        };
        let binary = ArtifactV3 {
            id: format!("minecraft-library-{}", hash.hex()),
            owner: None,
            purposes: vec![ArtifactPurposeV3::Build, ArtifactPurposeV3::Runtime],
            coordinate: Some(library.name.clone()),
            repository_id: None,
            url: Some(artifact.url),
            hash,
            cache_path: PathBuf::from("$sfm-cache")
                .join("minecraft/libraries")
                .join(&artifact.path),
            provenance: ArtifactProvenanceV3::RemoteHttp,
            source_git: None,
            source_build: None,
            weak: None,
        };
        let path = acquire_locked_artifact(inventory, &binary, cancellation_token, fetcher)
            .wrap_err_with(|| {
                format!(
                    "Failed to acquire authenticated Minecraft library {}",
                    library.name
                )
            })?;
        libraries.push(AuthenticatedLibrary {
            coordinate: library.name,
            artifact_path: artifact.path,
            path,
        });
    }
    Ok(libraries)
}

fn stub_provider_fingerprint(minecraft_version: &str, version_json_hash: ContentHash) -> String {
    let input = format!(
        "schema={GENERATOR_FINGERPRINT}\njava-release={JAVA_RELEASE}\nminecraft={minecraft_version}\nversion-json={version_json_hash}\n"
    );
    format!("blake3:{}", blake3::hash(input.as_bytes()).to_hex())
}

fn materialization_fingerprint(base: &str, shadowed: &BTreeSet<String>) -> String {
    let mut hasher = blake3::Hasher::new();
    hasher.update(base.as_bytes());
    for name in shadowed {
        hasher.update(&[0]);
        hasher.update(name.as_bytes());
    }
    format!("blake3:{}", hasher.finalize().to_hex())
}

fn completed_tree_matches(tree: &Path, fingerprint: &str) -> bool {
    tree.is_dir()
        && fs::read_to_string(tree.join(COMPLETION_FILE)).is_ok_and(|actual| actual == fingerprint)
}

fn count_stub_types(tree: &Path) -> eyre::Result<usize> {
    let mut count = 0usize;
    for entry in walkdir::WalkDir::new(tree).follow_links(false) {
        let entry = entry?;
        if entry.file_type().is_file()
            && entry
                .path()
                .extension()
                .is_some_and(|extension| extension == "java")
        {
            count = count.saturating_add(
                fs::read_to_string(entry.path())?
                    .lines()
                    .filter(|line| line.starts_with("// binary-name: "))
                    .count(),
            );
        }
    }
    Ok(count)
}

fn authoritative_top_level_types(
    preflight: &SourcePreflight,
    synthetic_target: &str,
) -> eyre::Result<BTreeSet<String>> {
    let mut types = BTreeSet::new();
    for root in &preflight.roots {
        if root.identity == synthetic_target || !root.root.is_dir() {
            continue;
        }
        for entry in walkdir::WalkDir::new(&root.root).follow_links(false) {
            let entry = entry?;
            if !entry.file_type().is_file()
                || entry
                    .path()
                    .extension()
                    .is_none_or(|extension| extension != "java")
            {
                continue;
            }
            let relative = entry.path().strip_prefix(&root.root)?;
            let mut components = relative
                .components()
                .map(|component| component.as_os_str().to_str())
                .collect::<Option<Vec<_>>>()
                .ok_or_else(|| {
                    eyre::eyre!(
                        "Java source path is not UTF-8 under {}: {}",
                        root.root.display(),
                        relative.display()
                    )
                })?;
            let Some(file_name) = components.pop() else {
                continue;
            };
            let Some(simple_name) = file_name.strip_suffix(".java") else {
                continue;
            };
            if matches!(simple_name, "package-info" | "module-info") {
                continue;
            }
            components.push(simple_name);
            types.insert(components.join("."));
        }
    }
    Ok(types)
}

#[derive(Debug, Facet)]
struct MinecraftVersionDocument {
    #[facet(default)]
    libraries: Vec<MinecraftLibraryDocument>,
}

#[derive(Debug, Facet)]
struct MinecraftLibraryDocument {
    name: String,
    #[facet(default)]
    downloads: Option<MinecraftLibraryDownloads>,
}

#[derive(Debug, Facet)]
struct MinecraftLibraryDownloads {
    #[facet(default)]
    artifact: Option<MinecraftLibraryArtifact>,
}

#[derive(Debug, Facet)]
struct MinecraftLibraryArtifact {
    url: String,
    path: String,
    #[facet(default)]
    sha1: Option<ContentHash>,
}

#[derive(Clone, Debug)]
struct AuthenticatedLibrary {
    coordinate: String,
    artifact_path: String,
    path: PathBuf,
}

#[derive(Clone, Debug, Eq, Ord, PartialEq, PartialOrd)]
struct ClassStub {
    binary_name: String,
    access_flags: u16,
    super_name: Option<String>,
    interfaces: Vec<String>,
    origin: String,
}

#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
struct StubTreeStats {
    types: usize,
    shadowed_types: usize,
}

fn generate_stub_tree(
    libraries: &[AuthenticatedLibrary],
    authoritative_types: &BTreeSet<String>,
    output: &Path,
    cancellation_token: &CancellationToken,
) -> eyre::Result<StubTreeStats> {
    let mut types = BTreeMap::<String, ClassStub>::new();
    let mut shadowed_types = 0usize;
    for library in libraries {
        cancellation_token.bail_if_cancelled()?;
        for stub in read_library_types(library, cancellation_token)? {
            let qualified = stub.binary_name.replace('/', ".");
            let outer = qualified.split('$').next().unwrap_or(&qualified);
            if authoritative_types.contains(&qualified) || authoritative_types.contains(outer) {
                shadowed_types = shadowed_types.saturating_add(1);
                continue;
            }
            types.entry(qualified).or_insert(stub);
        }
    }

    let mut packages = BTreeMap::<String, Vec<(String, ClassStub)>>::new();
    for (qualified, stub) in types {
        let (package, simple) = qualified
            .rsplit_once('.')
            .map_or(("", qualified.as_str()), |(package, simple)| {
                (package, simple)
            });
        if !valid_java_identifier(simple) || java_keyword(simple) {
            continue;
        }
        packages
            .entry(package.to_owned())
            .or_default()
            .push((simple.to_owned(), stub));
    }
    let mut written = 0usize;
    for (package, mut stubs) in packages {
        cancellation_token.bail_if_cancelled()?;
        stubs.sort_by(|left, right| left.0.cmp(&right.0));
        let mut directory = output.to_path_buf();
        for segment in package.split('.').filter(|segment| !segment.is_empty()) {
            directory.push(segment);
        }
        fs::create_dir_all(&directory)?;
        for (chunk_index, chunk) in stubs.chunks(TYPES_PER_FILE).enumerate() {
            let mut source = String::new();
            if !package.is_empty() {
                source.push_str("package ");
                source.push_str(&package);
                source.push_str(";\n\n");
            }
            source.push_str("// Generated from Minecraft-version-JSON-authenticated classfiles.\n");
            source.push_str(
                "// Type declarations are navigable fallbacks; real sources take precedence.\n\n",
            );
            for (simple, stub) in chunk {
                source.push_str("// binary-name: ");
                source.push_str(&stub.binary_name.replace('/', "."));
                source.push('\n');
                source.push_str("// binary-origin: ");
                source.push_str(&stub.origin.replace(['\r', '\n'], " "));
                source.push('\n');
                source.push_str(&render_type_stub(simple, stub));
                source.push_str("\n\n");
                written = written.saturating_add(1);
            }
            fs::write(
                directory.join(format!("__sfm_classfile_types_{chunk_index:04}.java")),
                source,
            )?;
        }
    }
    Ok(StubTreeStats {
        types: written,
        shadowed_types,
    })
}

fn render_type_stub(simple_name: &str, stub: &ClassStub) -> String {
    const ACC_PUBLIC: u16 = 0x0001;
    const ACC_INTERFACE: u16 = 0x0200;
    const ACC_ANNOTATION: u16 = 0x2000;
    const ACC_ENUM: u16 = 0x4000;
    let visibility = if stub.access_flags & ACC_PUBLIC != 0 {
        "public "
    } else {
        ""
    };
    if stub.access_flags & ACC_ANNOTATION != 0 {
        return format!("{visibility}@interface {simple_name} {{}}");
    }
    if stub.access_flags & ACC_ENUM != 0 {
        let implements = render_relationship(" implements ", &stub.interfaces);
        return format!("{visibility}enum {simple_name}{implements} {{;}}");
    }
    if stub.access_flags & ACC_INTERFACE != 0 {
        let extends = render_relationship(" extends ", &stub.interfaces);
        return format!("{visibility}interface {simple_name}{extends} {{}}");
    }
    let extends = stub
        .super_name
        .as_deref()
        .filter(|name| *name != "java/lang/Object")
        .map_or_else(String::new, |name| {
            format!(" extends {}", name.replace('/', "."))
        });
    let implements = render_relationship(" implements ", &stub.interfaces);
    format!("{visibility}class {simple_name}{extends}{implements} {{}}")
}

fn render_relationship(prefix: &str, names: &[String]) -> String {
    if names.is_empty() {
        String::new()
    } else {
        format!(
            "{prefix}{}",
            names
                .iter()
                .map(|name| name.replace('/', "."))
                .collect::<Vec<_>>()
                .join(", ")
        )
    }
}

fn read_library_types(
    library: &AuthenticatedLibrary,
    cancellation_token: &CancellationToken,
) -> eyre::Result<Vec<ClassStub>> {
    let file = fs::File::open(&library.path)
        .wrap_err_with(|| format!("Failed to open {}", library.path.display()))?;
    let mut archive = ZipArchive::new(file)
        .wrap_err_with(|| format!("Failed to read {}", library.path.display()))?;
    let mut selected = BTreeMap::<String, (u16, usize, String)>::new();
    for index in 0..archive.len() {
        let entry = archive.by_index(index)?;
        let name = entry.name().to_owned();
        drop(entry);
        let Some((release, logical)) = logical_class_entry(&name) else {
            continue;
        };
        if release > JAVA_RELEASE {
            continue;
        }
        let replace = selected
            .get(&logical)
            .is_none_or(|(current, _, _)| release > *current);
        if replace {
            selected.insert(logical, (release, index, name));
        }
    }
    let mut stubs = Vec::new();
    for (_, (_, index, entry_name)) in selected {
        cancellation_token.bail_if_cancelled()?;
        let mut entry = archive.by_index(index)?;
        let mut bytes = Vec::with_capacity(usize::try_from(entry.size()).unwrap_or(0));
        entry.read_to_end(&mut bytes)?;
        let stub = parse_class_stub(
            &bytes,
            format!(
                "{}:{}!{entry_name}",
                library.coordinate, library.artifact_path
            ),
        )
        .wrap_err_with(|| format!("Failed to parse {}!{entry_name}", library.path.display()))?;
        if let Some(stub) = stub {
            stubs.push(stub);
        }
    }
    Ok(stubs)
}

fn logical_class_entry(name: &str) -> Option<(u16, String)> {
    if !Path::new(name)
        .extension()
        .is_some_and(|extension| extension.eq_ignore_ascii_case("class"))
    {
        return None;
    }
    if let Some(rest) = name.strip_prefix("META-INF/versions/") {
        let (release, logical) = rest.split_once('/')?;
        return Some((release.parse().ok()?, logical.to_owned()));
    }
    if name.starts_with("META-INF/") {
        return None;
    }
    Some((0, name.to_owned()))
}

fn parse_class_stub(bytes: &[u8], origin: String) -> eyre::Result<Option<ClassStub>> {
    if bytes.len() < 10 || read_u32(bytes, 0)? != 0xCAFE_BABE {
        eyre::bail!("classfile magic is missing");
    }
    let constants = ConstantPool::parse(bytes)?;
    let cursor = constants.after;
    let access_flags = read_u16(bytes, cursor)?;
    let this_class = read_u16(bytes, cursor + 2)?;
    let super_class = read_u16(bytes, cursor + 4)?;
    let binary_name = constants
        .class_name(this_class)
        .ok_or_else(|| eyre::eyre!("classfile has no this_class name"))?
        .to_owned();
    if binary_name.ends_with("module-info") || binary_name.ends_with("package-info") {
        return Ok(None);
    }
    if !binary_name.split('/').all(valid_java_identifier) {
        return Ok(None);
    }
    let interfaces_count = usize::from(read_u16(bytes, cursor + 6)?);
    let mut interfaces = Vec::with_capacity(interfaces_count);
    let mut interface_cursor = cursor + 8;
    for _ in 0..interfaces_count {
        let interface = read_u16(bytes, interface_cursor)?;
        interface_cursor += 2;
        if let Some(name) = constants.class_name(interface) {
            interfaces.push(name.to_owned());
        }
    }
    interfaces.sort();
    interfaces.dedup();
    Ok(Some(ClassStub {
        binary_name,
        access_flags,
        super_name: (super_class != 0)
            .then(|| constants.class_name(super_class).map(str::to_owned))
            .flatten(),
        interfaces,
        origin,
    }))
}

fn valid_java_identifier(value: &str) -> bool {
    let mut chars = value.chars();
    let Some(first) = chars.next() else {
        return false;
    };
    (first == '_' || first == '$' || first.is_alphabetic())
        && chars.all(|character| {
            character == '_'
                || character == '$'
                || character.is_alphanumeric()
                || character == '\u{200c}'
                || character == '\u{200d}'
        })
}

fn java_keyword(value: &str) -> bool {
    matches!(
        value,
        "abstract"
            | "assert"
            | "boolean"
            | "break"
            | "byte"
            | "case"
            | "catch"
            | "char"
            | "class"
            | "const"
            | "continue"
            | "default"
            | "do"
            | "double"
            | "else"
            | "enum"
            | "extends"
            | "final"
            | "finally"
            | "float"
            | "for"
            | "goto"
            | "if"
            | "implements"
            | "import"
            | "instanceof"
            | "int"
            | "interface"
            | "long"
            | "native"
            | "new"
            | "package"
            | "private"
            | "protected"
            | "public"
            | "record"
            | "return"
            | "short"
            | "static"
            | "strictfp"
            | "super"
            | "switch"
            | "synchronized"
            | "this"
            | "throw"
            | "throws"
            | "transient"
            | "try"
            | "var"
            | "void"
            | "volatile"
            | "while"
            | "yield"
    )
}

#[derive(Debug)]
struct ConstantPool {
    entries: Vec<Constant>,
    after: usize,
}

impl ConstantPool {
    fn parse(bytes: &[u8]) -> eyre::Result<Self> {
        let count = usize::from(read_u16(bytes, 8)?);
        let mut entries = Vec::with_capacity(count);
        entries.push(Constant::Other);
        let mut cursor = 10usize;
        let mut index = 1usize;
        while index < count {
            let tag = *bytes
                .get(cursor)
                .ok_or_else(|| eyre::eyre!("class constant pool is truncated"))?;
            cursor += 1;
            match tag {
                1 => {
                    let length = usize::from(read_u16(bytes, cursor)?);
                    cursor += 2;
                    let end = cursor
                        .checked_add(length)
                        .ok_or_else(|| eyre::eyre!("constant UTF-8 length overflow"))?;
                    let value = bytes
                        .get(cursor..end)
                        .ok_or_else(|| eyre::eyre!("constant UTF-8 extends past classfile"))?;
                    entries.push(Constant::Utf8(String::from_utf8_lossy(value).into_owned()));
                    cursor = end;
                }
                3 | 4 | 9 | 10 | 11 | 12 | 17 | 18 => {
                    cursor = cursor.saturating_add(4);
                    entries.push(Constant::Other);
                }
                5 | 6 => {
                    cursor = cursor.saturating_add(8);
                    entries.push(Constant::Other);
                    entries.push(Constant::Other);
                    index += 1;
                }
                7 => {
                    let name_index = read_u16(bytes, cursor)?;
                    cursor += 2;
                    entries.push(Constant::Class(name_index));
                }
                8 | 16 | 19 | 20 => {
                    cursor = cursor.saturating_add(2);
                    entries.push(Constant::Other);
                }
                15 => {
                    cursor = cursor.saturating_add(3);
                    entries.push(Constant::Other);
                }
                _ => eyre::bail!("unsupported class constant-pool tag {tag}"),
            }
            if cursor > bytes.len() {
                eyre::bail!("class constant pool extends past classfile");
            }
            index += 1;
        }
        Ok(Self {
            entries,
            after: cursor,
        })
    }

    fn utf8(&self, index: u16) -> Option<&str> {
        match self.entries.get(usize::from(index))? {
            Constant::Utf8(value) => Some(value),
            Constant::Class(_) | Constant::Other => None,
        }
    }

    fn class_name(&self, index: u16) -> Option<&str> {
        let Constant::Class(name_index) = self.entries.get(usize::from(index))? else {
            return None;
        };
        self.utf8(*name_index)
    }
}

#[derive(Debug)]
enum Constant {
    Utf8(String),
    Class(u16),
    Other,
}

fn read_u16(bytes: &[u8], offset: usize) -> eyre::Result<u16> {
    let value = bytes
        .get(offset..offset + 2)
        .ok_or_else(|| eyre::eyre!("classfile ended before u16 at offset {offset}"))?;
    Ok(u16::from_be_bytes([value[0], value[1]]))
}

fn read_u32(bytes: &[u8], offset: usize) -> eyre::Result<u32> {
    let value = bytes
        .get(offset..offset + 4)
        .ok_or_else(|| eyre::eyre!("classfile ended before u32 at offset {offset}"))?;
    Ok(u32::from_be_bytes([value[0], value[1], value[2], value[3]]))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_and_renders_a_minimal_external_interface() {
        let bytes = minimal_classfile(
            0x0201,
            "org/apache/logging/log4j/Logger",
            "java/lang/Object",
            &["org/apache/logging/log4j/spi/ExtendedLogger"],
        );
        let stub = parse_class_stub(&bytes, "fixture.jar!Logger.class".to_owned())
            .expect("classfile")
            .expect("type");
        assert_eq!(stub.binary_name, "org/apache/logging/log4j/Logger");
        assert_eq!(
            render_type_stub("Logger", &stub),
            "public interface Logger extends org.apache.logging.log4j.spi.ExtendedLogger {}"
        );
    }

    #[test]
    fn multi_release_selection_prefers_java_seventeen_and_ignores_newer_entries() {
        assert_eq!(
            logical_class_entry("p/A.class"),
            Some((0, "p/A.class".to_owned()))
        );
        assert_eq!(
            logical_class_entry("META-INF/versions/17/p/A.class"),
            Some((17, "p/A.class".to_owned()))
        );
        assert_eq!(
            logical_class_entry("META-INF/versions/21/p/A.class"),
            Some((21, "p/A.class".to_owned()))
        );
        assert_eq!(logical_class_entry("META-INF/MANIFEST.MF"), None);
    }

    #[test]
    fn materialization_identity_is_relocatable_and_shadow_sensitive() {
        let hash = ContentHash::from_bytes(
            b"version",
            crate::jar_build::hash::ContentHashAlgorithm::Blake3,
        );
        let first = stub_provider_fingerprint("1.19.2", hash);
        let repeated = stub_provider_fingerprint("1.19.2", hash);
        assert_eq!(first, repeated);
        assert_ne!(
            materialization_fingerprint(&first, &BTreeSet::new()),
            materialization_fingerprint(&first, &BTreeSet::from(["p.A".to_owned()]))
        );
    }

    fn minimal_classfile(
        access_flags: u16,
        this_name: &str,
        super_name: &str,
        interfaces: &[&str],
    ) -> Vec<u8> {
        let mut bytes = Vec::new();
        bytes.extend_from_slice(&0xCAFE_BABEu32.to_be_bytes());
        bytes.extend_from_slice(&0u16.to_be_bytes());
        bytes.extend_from_slice(&61u16.to_be_bytes());
        let constant_count = 5usize + interfaces.len() * 2;
        bytes.extend_from_slice(&u16::try_from(constant_count).unwrap().to_be_bytes());
        push_utf8(&mut bytes, this_name);
        push_class(&mut bytes, 1);
        push_utf8(&mut bytes, super_name);
        push_class(&mut bytes, 3);
        let mut interface_indices = Vec::new();
        for interface in interfaces {
            let utf8 = u16::try_from(interface_indices.len() * 2 + 5).unwrap();
            push_utf8(&mut bytes, interface);
            push_class(&mut bytes, utf8);
            interface_indices.push(utf8 + 1);
        }
        bytes.extend_from_slice(&access_flags.to_be_bytes());
        bytes.extend_from_slice(&2u16.to_be_bytes());
        bytes.extend_from_slice(&4u16.to_be_bytes());
        bytes.extend_from_slice(
            &u16::try_from(interface_indices.len())
                .unwrap()
                .to_be_bytes(),
        );
        for index in interface_indices {
            bytes.extend_from_slice(&index.to_be_bytes());
        }
        bytes.extend_from_slice(&0u16.to_be_bytes());
        bytes.extend_from_slice(&0u16.to_be_bytes());
        bytes.extend_from_slice(&0u16.to_be_bytes());
        bytes
    }

    fn push_utf8(bytes: &mut Vec<u8>, value: &str) {
        bytes.push(1);
        bytes.extend_from_slice(&u16::try_from(value.len()).unwrap().to_be_bytes());
        bytes.extend_from_slice(value.as_bytes());
    }

    fn push_class(bytes: &mut Vec<u8>, name_index: u16) {
        bytes.push(7);
        bytes.extend_from_slice(&name_index.to_be_bytes());
    }
}
