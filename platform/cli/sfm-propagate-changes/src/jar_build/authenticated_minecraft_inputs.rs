//! Authenticated compile inputs from one exact frozen Minecraft version JSON.
//!
//! Pure authenticated request model; acquisition belongs to the engine.
//! Children are authenticated metadata descendants, not invented original lock
//! rows. There is no I/O, cache discovery, lock rewrite, asset/index recursion,
//! live version-manifest discovery or external-tool permission here.

use crate::jar_build::hash::ContentHash;
use crate::jar_build::hash::ContentHashAlgorithm;
use crate::source_projection::frozen_recipe_project::FrozenRecipeProject;
use crate::source_projection::projection_catalog::validate_projection_key;
use eyre::Result;
use eyre::WrapErr;
use eyre::ensure;
use facet::Facet;
use reqwest::Url;
use std::collections::BTreeMap;
use std::collections::BTreeSet;

const MAX_PARENT_BYTES: usize = 16 * 1024 * 1024;
const MAX_CHILD_BYTES: u64 = 512 * 1024 * 1024;
const MAX_LIBRARIES: usize = 4096;
const MAX_URL_BYTES: usize = 4096;

/// Compile downloads only; assets and native classifiers are separate gates.
#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub(super) enum MinecraftCompileDownload {
    ClientJar,
    ServerJar,
    ClientMappings,
    ServerMappings,
}

/// Private parent identity cannot be substituted by a pipeline consumer.
#[derive(Clone, Debug, Eq, PartialEq)]
struct VersionParent {
    minecraft_version: String,
    artifact_index: usize,
    exact_url: String,
    original_hash: ContentHash,
    bytes: usize,
}

#[derive(Clone, Debug, Eq, PartialEq)]
enum ChildRole {
    Download(MinecraftCompileDownload),
    Library(String),
}

/// An exact request selected from authenticated parent bytes, not a lock pin.
#[derive(Clone, Debug, Eq, PartialEq)]
pub(super) struct AuthenticatedMinecraftChild {
    parent: VersionParent,
    role: ChildRole,
    exact_url: String,
    expected_sha1: ContentHash,
    expected_bytes: u64,
}

impl AuthenticatedMinecraftChild {
    pub(super) fn url(&self) -> &str {
        &self.exact_url
    }

    pub(super) fn expected_hash(&self) -> &ContentHash {
        &self.expected_sha1
    }

    pub(super) fn expected_bytes(&self) -> u64 {
        self.expected_bytes
    }

    pub(super) fn library_relative_path(&self) -> Option<&str> {
        match &self.role {
            ChildRole::Library(path) => Some(path),
            ChildRole::Download(_) => None,
        }
    }
}

/// One pinned parent's immutable compile-only child selection.
#[derive(Clone, Debug, Eq, PartialEq)]
pub(super) struct AuthenticatedMinecraftInputs {
    parent: VersionParent,
    downloads: BTreeMap<MinecraftCompileDownload, AuthenticatedMinecraftChild>,
    libraries: Vec<AuthenticatedMinecraftChild>,
    library_paths: BTreeMap<String, usize>,
}

impl AuthenticatedMinecraftInputs {
    #[cfg(test)]
    pub(super) fn parent_hash(&self) -> ContentHash {
        self.parent.original_hash
    }

    #[cfg(test)]
    pub(super) fn parent_url(&self) -> &str {
        &self.parent.exact_url
    }

    pub(super) fn download(
        &self,
        role: MinecraftCompileDownload,
    ) -> Result<&AuthenticatedMinecraftChild> {
        self.downloads.get(&role).ok_or_else(|| {
            eyre::eyre!("authenticated version JSON has no compile download {role:?}")
        })
    }

    pub(super) fn libraries(&self) -> impl Iterator<Item = &AuthenticatedMinecraftChild> {
        self.libraries.iter()
    }

    /// The exact 26.1.2 recipe consumes unobfuscated jars without mapping
    /// downloads. Earlier admitted recipes still require both mappings.
    pub(super) fn compile_mapping_urls(&self) -> Result<(Option<String>, Option<String>)> {
        if self.parent.minecraft_version == "26.1.2" {
            ensure!(
                !self
                    .downloads
                    .contains_key(&MinecraftCompileDownload::ClientMappings)
                    && !self
                        .downloads
                        .contains_key(&MinecraftCompileDownload::ServerMappings),
                "26.1.2 compile metadata unexpectedly declares mapping downloads"
            );
            return Ok((None, None));
        }
        Ok((
            Some(
                self.download(MinecraftCompileDownload::ClientMappings)?
                    .url()
                    .to_owned(),
            ),
            Some(
                self.download(MinecraftCompileDownload::ServerMappings)?
                    .url()
                    .to_owned(),
            ),
        ))
    }

    /// Validate exact downloaded or cached bytes, before consuming a child.
    /// Callers must also retain the checked project and recheck before effects.
    pub(super) fn verify_child_bytes(
        &self,
        request: &AuthenticatedMinecraftChild,
        bytes: &[u8],
    ) -> Result<()> {
        let selected = match &request.role {
            ChildRole::Download(role) => self.downloads.get(role),
            ChildRole::Library(path) => self
                .library_paths
                .get(path)
                .and_then(|index| self.libraries.get(*index)),
        };
        ensure!(
            selected == Some(request) && request.parent == self.parent,
            "Minecraft child request is unrecorded or belongs to a different authenticated parent"
        );
        ensure!(
            u64::try_from(bytes.len())? == request.expected_bytes,
            "Minecraft child size mismatch for {}: expected {}, got {}",
            request.exact_url,
            request.expected_bytes,
            bytes.len()
        );
        ensure!(
            ContentHash::from_bytes(bytes, ContentHashAlgorithm::Sha1) == request.expected_sha1,
            "Minecraft child SHA1 mismatch for {}",
            request.exact_url
        );
        Ok(())
    }
}

#[cfg(test)]
pub(super) mod test_support {
    use super::*;

    /// Synthetic cached child bytes through the real strict parser. This is
    /// not a prepared original version-JSON pin or acquisition permission.
    pub(in crate::jar_build) fn cached_library_inputs(
        count: usize,
    ) -> Result<(AuthenticatedMinecraftInputs, Vec<u8>)> {
        ensure!(
            (1..=64).contains(&count),
            "test library count out of bounds"
        );
        let bytes = b"synthetic immutable cached library fixture".to_vec();
        let sha1 = ContentHash::from_bytes(&bytes, ContentHashAlgorithm::Sha1).hex();
        let libraries = (0..count).map(|index| {
            let path = format!("fixture/library-{index}/1/library-{index}-1.jar");
            format!(r#"{{"downloads":{{"artifact":{{"path":"{path}","url":"https://libraries.minecraft.net/{path}","sha1":"{sha1}","size":{}}}}}}}"#, bytes.len())
        }).collect::<Vec<_>>().join(",");
        let raw = format!(
            r#"{{"id":"1.19.2","downloads":{{"client":{{"url":"https://piston-data.mojang.com/v1/objects/synthetic-client/client.jar","sha1":"{sha1}","size":{size}}},"server":{{"url":"https://piston-data.mojang.com/v1/objects/synthetic-server/server.jar","sha1":"{sha1}","size":{size}}}}},"libraries":[{libraries}]}}"#,
            size = bytes.len()
        );
        let parent = VersionParent {
            minecraft_version: "1.19.2".to_owned(),
            artifact_index: 1,
            exact_url: "https://piston-meta.mojang.com/v1/packages/synthetic-fixture/1.19.2.json"
                .to_owned(),
            original_hash: ContentHash::from_bytes(raw.as_bytes(), ContentHashAlgorithm::Blake3),
            bytes: raw.len(),
        };
        let inputs = parse_authenticated_parent(parent, raw.as_bytes())?;
        for request in inputs.libraries() {
            inputs.verify_child_bytes(request, &bytes)?;
        }
        Ok((inputs, bytes))
    }
}

/// Verify the exact selected frozen parent before parsing any metadata.
/// The request remains bound to the prepared catalog's original index/hash.
/// This does not recheck the filesystem or authorize acquisition/execution.
pub(super) fn authenticate_version_json(
    project: &FrozenRecipeProject,
    raw: &[u8],
) -> Result<AuthenticatedMinecraftInputs> {
    ensure!(
        !raw.is_empty() && raw.len() <= MAX_PARENT_BYTES,
        "Minecraft version JSON is empty or exceeds the bounded parent limit"
    );
    let receipt = &project.receipt().released_inputs;
    let inputs = project.dependencies();
    ensure!(
        inputs.receipt().target_id == receipt.target_id
            && inputs.receipt().minecraft_version == receipt.minecraft_version
            && inputs.receipt().recipe_id == receipt.recipe_id,
        "frozen Minecraft parent lost its prepared target/recipe identity"
    );
    let request = inputs.exact_url(&receipt.direct_version_json_url, false)?;
    ensure!(
        request.original_artifact_index() == receipt.direct_version_json_artifact_index
            && request.original_content_hash() == receipt.direct_version_json_hash
            && request.exact_transport_url() == Some(receipt.direct_version_json_url.as_str())
            && request.resolved_coordinate().is_none(),
        "Minecraft parent is not the exact recorded direct version JSON pin"
    );
    inputs.verify_artifact_bytes(&request, raw, false)?;
    authenticate_pinned_version_json(
        &receipt.minecraft_version,
        receipt.direct_version_json_artifact_index,
        &receipt.direct_version_json_url,
        &receipt.direct_version_json_hash,
        raw,
    )
}

/// Parse only after authenticating the exact, already-selected parent pin.
pub(super) fn authenticate_pinned_version_json(
    minecraft_version: &str,
    artifact_index: usize,
    exact_url: &str,
    original_hash: &str,
    raw: &[u8],
) -> Result<AuthenticatedMinecraftInputs> {
    ensure!(
        !raw.is_empty() && raw.len() <= MAX_PARENT_BYTES,
        "Minecraft version JSON is empty or exceeds the bounded parent limit"
    );
    let parent = VersionParent {
        minecraft_version: minecraft_version.to_owned(),
        artifact_index,
        exact_url: exact_url.to_owned(),
        original_hash: ContentHash::parse(original_hash).map_err(|error| eyre::eyre!(error))?,
        bytes: raw.len(),
    };
    parse_authenticated_parent(parent, raw)
}

// These strict typed fields deliberately do not change the legacy engine's
// MinecraftDownload/MinecraftPlan JSON shape. Unknown ordinary metadata fields
// are irrelevant to this bounded compile-only view; duplicate typed fields
// are refused by the typed parser.
#[derive(Debug, Facet)]
struct CompileVersionJson {
    id: String,
    downloads: CompileDownloads,
    libraries: Vec<CompileLibrary>,
}

#[derive(Debug, Facet)]
struct CompileDownloads {
    client: CompileDownload,
    server: CompileDownload,
    #[facet(default)]
    client_mappings: Option<CompileDownload>,
    #[facet(default)]
    server_mappings: Option<CompileDownload>,
}

#[derive(Debug, Facet)]
struct CompileDownload {
    url: String,
    sha1: String,
    size: facet_value::Value,
}

#[derive(Debug, Facet)]
struct CompileLibrary {
    #[facet(default)]
    downloads: Option<CompileLibraryDownloads>,
}

#[derive(Debug, Facet)]
struct CompileLibraryDownloads {
    #[facet(default)]
    artifact: Option<CompileLibraryArtifact>,
}

#[derive(Debug, Facet)]
struct CompileLibraryArtifact {
    path: String,
    url: String,
    sha1: String,
    size: facet_value::Value,
}

fn exact_integer_size(value: &facet_value::Value) -> Result<u64> {
    let number = value
        .as_number()
        .ok_or_else(|| eyre::eyre!("Minecraft child size must be a JSON integer"))?;
    ensure!(
        number.is_integer(),
        "Minecraft child size must be a JSON integer"
    );
    number
        .to_u64()
        .ok_or_else(|| eyre::eyre!("Minecraft child size must be an unsigned 64-bit integer"))
}

fn parse_authenticated_parent(
    parent: VersionParent,
    raw: &[u8],
) -> Result<AuthenticatedMinecraftInputs> {
    ensure!(
        !raw.is_empty()
            && raw.len() <= MAX_PARENT_BYTES
            && parent.bytes == raw.len()
            && ContentHash::from_bytes(raw, parent.original_hash.algorithm) == parent.original_hash,
        "Minecraft parent bytes do not match the original exact frozen pin"
    );
    let text = std::str::from_utf8(raw).wrap_err("authenticated version JSON is not UTF-8")?;
    let metadata: CompileVersionJson = facet_json::from_str(text)
        .wrap_err("cannot parse authenticated Minecraft compile metadata")?;
    ensure!(
        metadata.id == parent.minecraft_version,
        "authenticated version JSON id differs from the exact target Minecraft version"
    );
    ensure!(
        metadata.libraries.len() <= MAX_LIBRARIES,
        "authenticated Minecraft metadata exceeds the bounded library limit"
    );
    let mut downloads = BTreeMap::new();
    let mut urls = BTreeSet::new();
    for (role, download) in [
        (
            MinecraftCompileDownload::ClientJar,
            Some(metadata.downloads.client),
        ),
        (
            MinecraftCompileDownload::ServerJar,
            Some(metadata.downloads.server),
        ),
        (
            MinecraftCompileDownload::ClientMappings,
            metadata.downloads.client_mappings,
        ),
        (
            MinecraftCompileDownload::ServerMappings,
            metadata.downloads.server_mappings,
        ),
    ] {
        if let Some(download) = download {
            let child = child_request(
                &parent,
                ChildRole::Download(role),
                download.url,
                &download.sha1,
                exact_integer_size(&download.size)?,
            )?;
            ensure!(
                urls.insert(child.exact_url.clone()),
                "duplicate Minecraft child URL"
            );
            downloads.insert(role, child);
        }
    }
    let mut libraries = Vec::new();
    let mut library_paths = BTreeMap::<String, usize>::new();
    let mut path_aliases = BTreeSet::new();
    for library in metadata.libraries {
        // Classifier-only or non-download metadata is not an ordinary compile
        // artifact. Do not infer a URL, classifier or missing checksum.
        let Some(artifact) = library.downloads.and_then(|value| value.artifact) else {
            continue;
        };
        validate_projection_key(&artifact.path)
            .wrap_err("unsafe authenticated Minecraft library-relative path")?;
        ensure!(
            artifact.path.len() <= MAX_URL_BYTES
                && path_aliases.insert(artifact.path.to_ascii_lowercase()),
            "duplicate or case-alias Minecraft library path"
        );
        for previous in library_paths.keys() {
            ensure!(
                !path_is_ancestor(previous, &artifact.path)
                    && !path_is_ancestor(&artifact.path, previous),
                "Minecraft library file/directory collision"
            );
        }
        let child = child_request(
            &parent,
            ChildRole::Library(artifact.path.clone()),
            artifact.url,
            &artifact.sha1,
            exact_integer_size(&artifact.size)?,
        )?;
        let parsed = Url::parse(&child.exact_url)?;
        ensure!(
            parsed.path() == format!("/{}", artifact.path),
            "Minecraft library URL does not match its exact relative path"
        );
        ensure!(
            urls.insert(child.exact_url.clone()),
            "duplicate Minecraft child URL"
        );
        library_paths.insert(artifact.path, libraries.len());
        libraries.push(child);
    }
    Ok(AuthenticatedMinecraftInputs {
        parent,
        downloads,
        libraries,
        library_paths,
    })
}

fn path_is_ancestor(left: &str, right: &str) -> bool {
    right
        .to_ascii_lowercase()
        .strip_prefix(&left.to_ascii_lowercase())
        .is_some_and(|suffix| suffix.starts_with('/'))
}

fn child_request(
    parent: &VersionParent,
    role: ChildRole,
    url: String,
    sha1: &str,
    size: u64,
) -> Result<AuthenticatedMinecraftChild> {
    ensure!(
        size > 0 && size <= MAX_CHILD_BYTES,
        "Minecraft child size is absent, zero or exceeds the bounded compile limit"
    );
    // Mojang's metadata field is a raw forty-hex SHA1, not a custom algorithm
    // prefix. Do not accept a replacement BLAKE3 or weaker caller-provided hash.
    let expected_sha1 = ContentHash::parse_hex(sha1, ContentHashAlgorithm::Sha1)
        .map_err(|error| eyre::eyre!(error))?;
    ensure!(
        !url.is_empty()
            && url.len() <= MAX_URL_BYTES
            && url.is_ascii()
            && !url
                .chars()
                .any(|value| value.is_control() || value.is_whitespace())
            && !url.chars().any(|value| matches!(value, '\\' | '%')),
        "unsafe Minecraft child URL spelling"
    );
    let parsed = Url::parse(&url).wrap_err("invalid authenticated Minecraft child URL")?;
    let host_is_allowed = match &role {
        ChildRole::Download(_) => matches!(
            parsed.host_str(),
            Some("piston-data.mojang.com" | "launcher.mojang.com")
        ),
        ChildRole::Library(_) => parsed.host_str() == Some("libraries.minecraft.net"),
    };
    ensure!(
        parsed.scheme() == "https"
            && host_is_allowed
            && parsed.username().is_empty()
            && parsed.password().is_none()
            && parsed.port().is_none()
            && parsed.query().is_none()
            && parsed.fragment().is_none()
            && parsed.as_str() == url
            && !parsed
                .path()
                .split('/')
                .any(|part| part == "." || part == ".."),
        "unsafe or unreviewed Minecraft child transport; review new hosts explicitly"
    );
    Ok(AuthenticatedMinecraftChild {
        parent: parent.clone(),
        role,
        exact_url: url,
        expected_sha1,
        expected_bytes: size,
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    const CHILD: &[u8] = b"authenticated compile fixture";

    fn metadata() -> String {
        let sha1 = ContentHash::from_bytes(CHILD, ContentHashAlgorithm::Sha1).hex();
        r#"{"id":"1.19.2","downloads":{"client":{"url":"https://piston-data.mojang.com/v1/objects/client/client.jar","sha1":"SHA1","size":SIZE},"server":{"url":"https://piston-data.mojang.com/v1/objects/server/server.jar","sha1":"SHA1","size":SIZE},"client_mappings":{"url":"https://piston-data.mojang.com/v1/objects/maps/client.txt","sha1":"SHA1","size":SIZE}},"libraries":[{"downloads":{"artifact":{"path":"example/library/1/library-1.jar","url":"https://libraries.minecraft.net/example/library/1/library-1.jar","sha1":"SHA1","size":SIZE}}}],"assetIndex":{"id":"fixture","url":"http://never-follow.invalid/index.json"}}"#
            .replace("SHA1", &sha1)
            .replace("SIZE", &CHILD.len().to_string())
    }

    fn parent(raw: &[u8]) -> VersionParent {
        VersionParent {
            minecraft_version: "1.19.2".to_owned(),
            artifact_index: 1,
            exact_url: "https://piston-meta.mojang.com/v1/packages/frozen/1.19.2.json".to_owned(),
            original_hash: ContentHash::from_bytes(raw, ContentHashAlgorithm::Blake3),
            bytes: raw.len(),
        }
    }

    fn parse(text: &str) -> Result<AuthenticatedMinecraftInputs> {
        parse_authenticated_parent(parent(text.as_bytes()), text.as_bytes())
    }

    #[test]
    fn exact_parent_and_compile_children_are_preserved_without_asset_following() -> Result<()> {
        let text = metadata();
        let inputs = parse(&text)?;
        assert_eq!(inputs.parent_hash(), parent(text.as_bytes()).original_hash);
        assert_eq!(inputs.parent_url(), parent(text.as_bytes()).exact_url);
        for role in [
            MinecraftCompileDownload::ClientJar,
            MinecraftCompileDownload::ServerJar,
            MinecraftCompileDownload::ClientMappings,
        ] {
            let request = inputs.download(role)?;
            assert_eq!(request.expected_bytes(), u64::try_from(CHILD.len())?);
            assert_eq!(
                request.expected_hash().algorithm,
                ContentHashAlgorithm::Sha1
            );
            assert!(request.library_relative_path().is_none());
            inputs.verify_child_bytes(request, CHILD)?;
        }
        assert!(
            inputs
                .download(MinecraftCompileDownload::ServerMappings)
                .is_err()
        );
        let libraries = inputs.libraries().collect::<Vec<_>>();
        assert_eq!(libraries.len(), 1);
        assert_eq!(
            libraries[0].library_relative_path(),
            Some("example/library/1/library-1.jar")
        );
        assert!(
            libraries[0]
                .url()
                .starts_with("https://libraries.minecraft.net/")
        );
        inputs.verify_child_bytes(libraries[0], CHILD)?;
        Ok(())
    }

    #[test]
    fn parent_hash_is_checked_before_utf8_or_json_parsing() {
        let text = metadata();
        let selected = parent(text.as_bytes());
        for changed in [b"not JSON".as_slice(), &[0xff, 0xff][..], b"{}".as_slice()] {
            let error = parse_authenticated_parent(selected.clone(), changed).unwrap_err();
            assert!(error.to_string().contains("original exact frozen pin"));
        }
    }

    #[test]
    fn mapping_absence_is_admitted_only_for_the_exact_26_recipe() -> Result<()> {
        let text = metadata();
        let mut json: CompileVersionJson = facet_json::from_str(&text)?;
        let client = json.downloads.client_mappings.as_ref().unwrap();
        json.downloads.server_mappings = Some(CompileDownload {
            url: client.url.replace("client.txt", "server.txt"),
            sha1: client.sha1.clone(),
            size: client.size.clone(),
        });
        let mapped = facet_json::to_string(&json)?;
        let urls = parse(&mapped)?.compile_mapping_urls()?;
        assert!(urls.0.is_some() && urls.1.is_some());
        json.id = "26.1.2".to_owned();
        json.downloads.client_mappings = None;
        json.downloads.server_mappings = None;
        let raw = facet_json::to_string(&json)?;
        let mut selected = parent(raw.as_bytes());
        selected.minecraft_version = "26.1.2".to_owned();
        let inputs = parse_authenticated_parent(selected, raw.as_bytes())?;
        assert_eq!(inputs.compile_mapping_urls()?, (None, None));
        assert!(
            parse(&raw.replace("26.1.2", "1.19.2"))?
                .compile_mapping_urls()
                .is_err()
        );
        assert!(
            inputs
                .download(MinecraftCompileDownload::ClientMappings)
                .is_err()
        );
        assert!(
            inputs
                .download(MinecraftCompileDownload::ServerMappings)
                .is_err()
        );
        assert!(parse(&text)?.compile_mapping_urls().is_err());
        let declared = text.replace("1.19.2", "26.1.2");
        let mut selected = parent(declared.as_bytes());
        selected.minecraft_version = "26.1.2".to_owned();
        assert!(
            parse_authenticated_parent(selected, declared.as_bytes())?
                .compile_mapping_urls()
                .is_err()
        );
        Ok(())
    }

    #[test]
    #[ignore = "requires explicit SFM_NATIVE_26_VERSION_JSON_WITNESS for an existing cache file"]
    fn actual_released_26_parent_has_no_mapping_downloads() -> Result<()> {
        use std::io::Read as _;
        let path = std::env::var_os("SFM_NATIVE_26_VERSION_JSON_WITNESS")
            .ok_or_else(|| eyre::eyre!("provide the existing cache file explicitly"))?;
        let mut raw = Vec::new();
        std::fs::File::open(path)?
            .take(u64::try_from(MAX_PARENT_BYTES)? + 1)
            .read_to_end(&mut raw)?;
        let selected = VersionParent {
            minecraft_version: "26.1.2".to_owned(),
            artifact_index: 1,
            exact_url: "https://piston-meta.mojang.com/v1/packages/978e6c0dd077d0f8d5d53887454928f1d3687ba8/26.1.2.json".to_owned(),
            original_hash: ContentHash::parse("blake3:6624f695775e8083367dca64a86952e5b61fe7d8")
                .map_err(|error| eyre::eyre!(error))?,
            bytes: raw.len(),
        };
        let inputs = parse_authenticated_parent(selected, &raw)?;
        assert_eq!(inputs.compile_mapping_urls()?, (None, None));
        assert!(inputs.download(MinecraftCompileDownload::ClientJar).is_ok());
        assert!(inputs.download(MinecraftCompileDownload::ServerJar).is_ok());
        assert_eq!(inputs.libraries().count(), 107);
        Ok(())
    }

    #[test]
    fn exact_child_size_hash_url_and_parent_membership_are_enforced() -> Result<()> {
        let inputs = parse(&metadata())?;
        let selected = inputs.download(MinecraftCompileDownload::ClientJar)?;
        assert!(inputs.verify_child_bytes(selected, b"short").is_err());
        let mut changed_bytes = CHILD.to_vec();
        changed_bytes[0] ^= 1;
        assert!(inputs.verify_child_bytes(selected, &changed_bytes).is_err());
        let mut changed = selected.clone();
        changed.exact_url.push_str("?override");
        assert!(inputs.verify_child_bytes(&changed, CHILD).is_err());
        changed = selected.clone();
        changed.parent.artifact_index += 1;
        assert!(inputs.verify_child_bytes(&changed, CHILD).is_err());
        let other = parse(&metadata().replace("\"assetIndex\"", "\"unrelated\""))?;
        assert!(other.verify_child_bytes(selected, CHILD).is_err());
        Ok(())
    }

    #[test]
    fn missing_invalid_prefixed_or_zero_identity_fields_are_refused() {
        let text = metadata();
        let sha1 = ContentHash::from_bytes(CHILD, ContentHashAlgorithm::Sha1).hex();
        for changed in [
            text.replace(&format!("\"sha1\":\"{sha1}\","), ""),
            text.replace(&format!("\"size\":{}", CHILD.len()), "\"size\":0"),
            text.replace(&format!("\"size\":{}", CHILD.len()), "\"size\":-1"),
            text.replace(&format!("\"size\":{}", CHILD.len()), "\"size\":1.0"),
            text.replace(&sha1, &format!("sha1:{sha1}")),
            text.replace(&sha1, "invalid"),
            text.replace("\"id\":\"1.19.2\"", "\"id\":\"1.19.4\""),
        ] {
            assert!(
                parse(&changed).is_err(),
                "accepted invalid identity: {changed}"
            );
        }
    }

    #[test]
    fn unsafe_transports_and_relative_paths_are_refused() {
        let text = metadata();
        let base = "https://libraries.minecraft.net/example/library/1/library-1.jar";
        for replacement in [
            "http://libraries.minecraft.net/example/library/1/library-1.jar",
            "https://localhost/example/library/1/library-1.jar",
            "https://127.0.0.1/example/library/1/library-1.jar",
            "https://user:password@libraries.minecraft.net/example/library/1/library-1.jar",
            "https://libraries.minecraft.net:444/example/library/1/library-1.jar",
            "https://libraries.minecraft.net/example/library/1/library-1.jar?extra=1",
            "https://libraries.minecraft.net/example/library/1/library-1.jar#extra",
            "https://libraries.minecraft.net/example/library/1/%6cibrary-1.jar",
            "https://libraries.minecraft.net/other.jar",
        ] {
            assert!(parse(&text.replace(base, replacement)).is_err());
        }
        for path in [
            "../outside.jar",
            "/absolute.jar",
            "C:/outside.jar",
            "nul/file.jar",
            "file./child.jar",
        ] {
            assert!(parse(&text.replace("example/library/1/library-1.jar", path)).is_err());
        }
    }

    #[test]
    fn duplicate_typed_keys_library_aliases_and_file_directory_collisions_are_refused() {
        let text = metadata();
        assert!(
            parse(&text.replace("\"id\":\"1.19.2\"", "\"id\":\"1.19.2\",\"id\":\"1.19.2\""))
                .is_err()
        );
        assert!(parse(&text.replace("\"client\":", "\"client\":null,\"client\":")).is_err());
        let start = text
            .find("[{\"downloads\"")
            .expect("fixture library starts");
        let end = text.find("],\"assetIndex\"").expect("fixture library ends");
        let library = &text[start + 1..end];
        for other in [
            library.to_owned(),
            library.replace(
                "example/library/1/library-1.jar",
                "Example/library/1/library-1.jar",
            ),
            library.replace(
                "example/library/1/library-1.jar",
                "example/library/1/library-1.jar/child.jar",
            ),
        ] {
            let doubled = format!("{}[{library},{other}]{}", &text[..start], &text[end + 1..]);
            assert!(parse(&doubled).is_err());
        }
    }

    #[test]
    fn optional_classifier_metadata_does_not_invent_compile_artifacts() -> Result<()> {
        let text = metadata();
        let with_classifier = text.replace(
            "\"libraries\":[",
            "\"libraries\":[{\"downloads\":{\"classifiers\":{\"natives-test\":{\"url\":\"http://never-follow.invalid/native.jar\"}}}},",
        );
        assert_eq!(parse(&with_classifier)?.libraries().count(), 1);
        Ok(())
    }

    #[test]
    fn ordinary_library_order_matches_the_existing_compiler_not_key_sorting() -> Result<()> {
        let text = metadata();
        let start = text
            .find("[{\"downloads\"")
            .expect("fixture library starts");
        let end = text.find("],\"assetIndex\"").expect("fixture library ends");
        let library = &text[start + 1..end];
        let first = library.replace(
            "example/library/1/library-1.jar",
            "z/library/1/library-1.jar",
        );
        let second = library.replace(
            "example/library/1/library-1.jar",
            "a/library/1/library-1.jar",
        );
        let text = format!("{}[{first},{second}]{}", &text[..start], &text[end + 1..]);
        let inputs = parse(&text)?;
        let legacy_order = super::super::engine::original_minecraft_library_paths(&text)?;
        let selected_order = inputs
            .libraries()
            .filter_map(|request| request.library_relative_path())
            .map(str::to_owned)
            .collect::<Vec<_>>();
        assert_eq!(selected_order, legacy_order);
        assert!(selected_order[0].starts_with("z/"));
        assert!(selected_order[1].starts_with("a/"));
        Ok(())
    }

    #[test]
    fn parent_and_child_resource_limits_refuse_before_consumption() {
        let text = metadata();
        let oversized_child = text.replace(
            &format!("\"size\":{}", CHILD.len()),
            &format!("\"size\":{}", MAX_CHILD_BYTES + 1),
        );
        assert!(parse(&oversized_child).is_err());
        let empty = parent(&[]);
        assert!(parse_authenticated_parent(empty, &[]).is_err());
        let oversized = vec![b' '; MAX_PARENT_BYTES + 1];
        assert!(parse_authenticated_parent(parent(&oversized), &oversized).is_err());
    }

    /// Opt in with the already identified cache file. Never discover, download
    /// or replace it. The actual frozen BLAKE3 pin must match before parsing.
    #[test]
    #[ignore = "requires explicit SFM_NATIVE_VERSION_JSON_WITNESS for an existing 1.19.2 cache file"]
    fn actual_released_1192_parent_authenticates_children_and_original_order() -> Result<()> {
        use std::io::Read as _;
        let path = std::env::var_os("SFM_NATIVE_VERSION_JSON_WITNESS")
            .ok_or_else(|| eyre::eyre!("provide the existing cache file explicitly"))?;
        let mut raw = Vec::new();
        std::fs::File::open(path)?
            .take(u64::try_from(MAX_PARENT_BYTES)? + 1)
            .read_to_end(&mut raw)?;
        let document = crate::toolchain_lockfile_schema::parse_document(include_str!(
            "../../../../minecraft/core-liquid-template/build/lockfiles/1.19.2/schema-2.json"
        ))?;
        let crate::toolchain_lockfile_schema::ToolchainLockfileDocument::V2 {
            lockfile: lock, ..
        } = document
        else {
            eyre::bail!("released witness no longer uses its exact schema-2 lock");
        };
        let exact_url = "https://piston-meta.mojang.com/v1/packages/ed548106acf3ac7e8205a6ee8fd2710facfa164f/1.19.2.json";
        let (index, pin) = lock
            .artifacts
            .iter()
            .enumerate()
            .find(|(_, entry)| entry.url.as_deref() == Some(exact_url))
            .ok_or_else(|| eyre::eyre!("exact released parent pin absent"))?;
        let selected = VersionParent {
            minecraft_version: "1.19.2".to_owned(),
            artifact_index: index,
            exact_url: exact_url.to_owned(),
            original_hash: pin.hash,
            bytes: raw.len(),
        };
        let authenticated = parse_authenticated_parent(selected, &raw)?;
        // Compare to the existing compiler's parser, not a second iteration of
        // our strict model. Legacy compile includes ordinary artifacts in JSON
        // order and does not interpret `rules`; that behavior stays unchanged.
        let original_order =
            super::super::engine::original_minecraft_library_paths(std::str::from_utf8(&raw)?)?;
        let verified_order = authenticated
            .libraries()
            .map(|request| {
                request
                    .library_relative_path()
                    .unwrap_or_default()
                    .to_owned()
            })
            .collect::<Vec<_>>();
        ensure!(
            original_order == verified_order && verified_order.len() == 88,
            "actual authenticated compile-library order differs from original metadata"
        );
        for role in [
            MinecraftCompileDownload::ClientJar,
            MinecraftCompileDownload::ServerJar,
            MinecraftCompileDownload::ClientMappings,
            MinecraftCompileDownload::ServerMappings,
        ] {
            let request = authenticated.download(role)?;
            ensure!(
                request.expected_hash().algorithm == ContentHashAlgorithm::Sha1
                    && request.expected_bytes() > 0,
                "actual released compile download lacks SHA1/size"
            );
        }
        // No child bytes were acquired or executed. Ordinary-artifact selection
        // preserves the existing compiler's ordering and rules behavior.
        Ok(())
    }
}
