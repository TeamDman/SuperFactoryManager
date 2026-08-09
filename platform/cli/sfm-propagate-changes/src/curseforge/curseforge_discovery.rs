use crate::curseforge::CurseforgeProjectFileItem;
use crate::curseforge::CurseforgeProjectId;
use facet::Facet;
use reqwest::blocking::Client;

pub const CURSEFORGE_CORE_API_ROOT: &str = "https://api.curseforge.com/v1";
const MINECRAFT_GAME_ID: u32 = 432;
const PAGE_SIZE: u32 = 50;

#[derive(Clone, Copy, Debug, Eq, Facet, Ord, PartialEq, PartialOrd)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub enum CurseforgeModLoader {
    Forge,
    Fabric,
    Quilt,
    Neoforge,
}

impl CurseforgeModLoader {
    #[must_use]
    pub const fn api_value(self) -> u8 {
        match self {
            Self::Forge => 1,
            Self::Fabric => 4,
            Self::Quilt => 5,
            Self::Neoforge => 6,
        }
    }

    #[must_use]
    pub const fn from_api_value(value: u8) -> Option<Self> {
        match value {
            1 => Some(Self::Forge),
            4 => Some(Self::Fabric),
            5 => Some(Self::Quilt),
            6 => Some(Self::Neoforge),
            _ => None,
        }
    }

    #[must_use]
    pub const fn label(self) -> &'static str {
        match self {
            Self::Forge => "forge",
            Self::Fabric => "fabric",
            Self::Quilt => "quilt",
            Self::Neoforge => "neoforge",
        }
    }

    #[must_use]
    pub const fn game_version_label(self) -> &'static str {
        match self {
            Self::Forge => "Forge",
            Self::Fabric => "Fabric",
            Self::Quilt => "Quilt",
            Self::Neoforge => "NeoForge",
        }
    }

    #[must_use]
    pub fn from_game_version_label(value: &str) -> Option<Self> {
        [Self::Forge, Self::Fabric, Self::Quilt, Self::Neoforge]
            .into_iter()
            .find(|loader| value.eq_ignore_ascii_case(loader.game_version_label()))
    }
}

#[derive(Clone, Debug, Facet)]
pub struct CurseforgePagination {
    pub index: u32,
    #[facet(rename = "pageSize")]
    pub page_size: u32,
    #[facet(rename = "resultCount")]
    pub result_count: u32,
    #[facet(rename = "totalCount")]
    pub total_count: u32,
}

#[derive(Clone, Debug, Facet)]
pub struct CurseforgeModSearchEnvelope {
    pub data: Vec<CurseforgeMod>,
    pub pagination: CurseforgePagination,
}

#[derive(Clone, Debug, Facet)]
pub struct CurseforgeMod {
    pub id: CurseforgeProjectId,
    pub name: String,
    #[facet(default)]
    pub slug: Option<String>,
    #[facet(default)]
    pub summary: Option<String>,
    #[facet(default, rename = "downloadCount")]
    pub download_count: u64,
    #[facet(default, rename = "dateModified")]
    pub date_modified: Option<String>,
    #[facet(default, rename = "dateReleased")]
    pub date_released: Option<String>,
    #[facet(default, rename = "gamePopularityRank")]
    pub game_popularity_rank: Option<u64>,
}

#[derive(Clone, Debug, Facet)]
pub struct CurseforgeProjectFileSearchEnvelope {
    pub data: Vec<CurseforgeProjectFileItem>,
    pub pagination: CurseforgePagination,
}

#[derive(Clone, Debug, Facet)]
pub struct CurseforgeProjectFileEnvelope {
    pub data: CurseforgeProjectFileItem,
}

#[derive(Clone, Debug, Facet)]
pub struct CurseforgeModEnvelope {
    pub data: CurseforgeMod,
}

/// Read exact `CurseForge` project/file metadata before a dependency declaration is mutated.
pub trait CurseforgeProjectMetadata {
    /// # Errors
    ///
    /// Returns an error when the project cannot be fetched or parsed.
    fn fetch_project(&self, project_id: CurseforgeProjectId) -> eyre::Result<CurseforgeMod>;

    /// # Errors
    ///
    /// Returns an error when the file cannot be fetched, parsed, or verified against its project.
    fn fetch_project_file(
        &self,
        project_id: CurseforgeProjectId,
        file_id: crate::curseforge::CurseforgeProjectFileId,
    ) -> eyre::Result<CurseforgeProjectFileItem>;
}

impl CurseforgeProjectMetadata for Client {
    fn fetch_project(&self, project_id: CurseforgeProjectId) -> eyre::Result<CurseforgeMod> {
        fetch_project(self, project_id)
    }

    fn fetch_project_file(
        &self,
        project_id: CurseforgeProjectId,
        file_id: crate::curseforge::CurseforgeProjectFileId,
    ) -> eyre::Result<CurseforgeProjectFileItem> {
        fetch_project_file(self, project_id, file_id)
    }
}

/// Search Minecraft mods through the `CurseForge` Core API using explicit version and loader filters.
///
/// # Errors
///
/// Returns an error when the query is empty, the API request fails, or a paginated response is
/// malformed.
pub fn search_mods(
    client: &Client,
    query: &str,
    minecraft_version: &str,
    loader: CurseforgeModLoader,
) -> eyre::Result<Vec<CurseforgeMod>> {
    if query.trim().is_empty() {
        eyre::bail!("CurseForge mod search query must not be empty.");
    }
    let mut mods = collect_pages(|index| {
        let endpoint = format!("{CURSEFORGE_CORE_API_ROOT}/mods/search");
        let parameters = [
            ("gameId", MINECRAFT_GAME_ID.to_string()),
            ("gameVersion", minecraft_version.to_owned()),
            ("searchFilter", query.to_owned()),
            ("modLoaderType", loader.api_value().to_string()),
            ("index", index.to_string()),
            ("pageSize", PAGE_SIZE.to_string()),
        ];
        let body = get_core_response(client, &endpoint, &parameters)?;
        let page: CurseforgeModSearchEnvelope =
            facet_json::from_str(&body).map_err(eyre::Report::from)?;
        Ok(PagedResponse {
            items: page.data,
            pagination: page.pagination,
        })
    })?;
    mods.sort_by_key(|mod_| mod_.id);
    Ok(mods)
}

/// List a `CurseForge` project's files using exact Minecraft-version and loader filters.
///
/// # Errors
///
/// Returns an error when the API request fails or a paginated response is malformed.
pub fn list_project_files_for_version(
    client: &Client,
    project_id: CurseforgeProjectId,
    minecraft_version: &str,
    loader: CurseforgeModLoader,
) -> eyre::Result<Vec<CurseforgeProjectFileItem>> {
    let mut files = collect_pages(|index| {
        let endpoint = format!("{CURSEFORGE_CORE_API_ROOT}/mods/{project_id}/files");
        let parameters = [
            ("gameVersion", minecraft_version.to_owned()),
            ("modLoaderType", loader.api_value().to_string()),
            ("index", index.to_string()),
            ("pageSize", PAGE_SIZE.to_string()),
        ];
        let body = get_core_response(client, &endpoint, &parameters)?;
        let page: CurseforgeProjectFileSearchEnvelope =
            facet_json::from_str(&body).map_err(eyre::Report::from)?;
        Ok(PagedResponse {
            items: page.data,
            pagination: page.pagination,
        })
    })?;
    files.retain(|file| file_matches_version_and_loader(file, minecraft_version, loader));
    files.sort_by_key(|file| file.id);
    Ok(files)
}

/// Check that a project file explicitly supports one exact Minecraft-version and loader pair.
#[must_use]
pub fn file_matches_version_and_loader(
    file: &CurseforgeProjectFileItem,
    minecraft_version: &str,
    loader: CurseforgeModLoader,
) -> bool {
    file.game_versions
        .iter()
        .any(|version| version == minecraft_version)
        && file_supported_loaders(file).contains(&loader)
}

/// Return the loader names explicitly advertised for a project file.
///
/// `CurseForge` Core file responses commonly omit `sortableGameVersions[].modLoader`, while
/// retaining values such as `Forge` and `NeoForge` in `gameVersions`. Use the numeric field when
/// supplied; otherwise, use those advertised labels as the compatibility fallback.
#[must_use]
pub fn file_supported_loaders(file: &CurseforgeProjectFileItem) -> Vec<CurseforgeModLoader> {
    let mut loaders = file
        .sortable_game_versions
        .iter()
        .filter_map(|version| version.mod_loader)
        .filter_map(CurseforgeModLoader::from_api_value)
        .collect::<Vec<_>>();
    if loaders.is_empty() {
        loaders.extend(
            file.game_versions
                .iter()
                .filter_map(|version| CurseforgeModLoader::from_game_version_label(version)),
        );
    }
    loaders.sort_unstable();
    loaders.dedup();
    loaders
}

/// Fetch one exact project file so callers can verify that a selected file belongs to its project.
///
/// # Errors
///
/// Returns an error when the request fails, the response is invalid, or its `modId` does not match
/// `project_id`.
pub fn fetch_project_file(
    client: &Client,
    project_id: CurseforgeProjectId,
    file_id: crate::curseforge::CurseforgeProjectFileId,
) -> eyre::Result<CurseforgeProjectFileItem> {
    fetch_project_file_at(client, CURSEFORGE_CORE_API_ROOT, project_id, file_id)
}

fn fetch_project_file_at(
    client: &Client,
    api_root: &str,
    project_id: CurseforgeProjectId,
    file_id: crate::curseforge::CurseforgeProjectFileId,
) -> eyre::Result<CurseforgeProjectFileItem> {
    let endpoint = format!("{api_root}/mods/{project_id}/files/{file_id}");
    let body = get_core_response(client, &endpoint, &[])?;
    let file: CurseforgeProjectFileEnvelope =
        facet_json::from_str(&body).map_err(eyre::Report::from)?;
    let actual = file
        .data
        .mod_id
        .ok_or_else(|| eyre::eyre!("CurseForge file {file_id} did not include its modId."))?;
    if actual != project_id {
        eyre::bail!("CurseForge file {file_id} belongs to project {actual}, not {project_id}.");
    }
    Ok(file.data)
}

/// Fetch one exact `CurseForge` project to resolve its authoritative slug.
///
/// # Errors
///
/// Returns an error when the request fails or the API response is invalid.
pub fn fetch_project(
    client: &Client,
    project_id: CurseforgeProjectId,
) -> eyre::Result<CurseforgeMod> {
    fetch_project_at(client, CURSEFORGE_CORE_API_ROOT, project_id)
}

fn fetch_project_at(
    client: &Client,
    api_root: &str,
    project_id: CurseforgeProjectId,
) -> eyre::Result<CurseforgeMod> {
    let endpoint = format!("{api_root}/mods/{project_id}");
    let body = get_core_response(client, &endpoint, &[])?;
    let project: CurseforgeModEnvelope = facet_json::from_str(&body).map_err(eyre::Report::from)?;
    if project.data.id != project_id {
        eyre::bail!(
            "CurseForge project response returned {}, not requested project {project_id}.",
            project.data.id
        );
    }
    Ok(project.data)
}

fn get_core_response(
    client: &Client,
    endpoint: &str,
    parameters: &[(&str, String)],
) -> eyre::Result<String> {
    let response = client
        .get(endpoint)
        .query(parameters)
        .send()
        .map_err(|error| eyre::eyre!("Failed to query CurseForge Core API {endpoint}: {error}"))?;
    let status = response.status();
    let body = response
        .text()
        .map_err(|error| eyre::eyre!("Failed to read CurseForge Core API response: {error}"))?;
    if !status.is_success() {
        eyre::bail!("CurseForge Core API request failed ({status}): {body}");
    }
    Ok(body)
}

struct PagedResponse<T> {
    items: Vec<T>,
    pagination: CurseforgePagination,
}

fn collect_pages<T>(
    mut fetch_page: impl FnMut(u32) -> eyre::Result<PagedResponse<T>>,
) -> eyre::Result<Vec<T>> {
    let mut next_index = 0;
    let mut items = Vec::new();
    loop {
        let page = fetch_page(next_index)?;
        let response_count = usize::try_from(page.pagination.result_count)
            .map_err(|error| eyre::eyre!("CurseForge pagination count is invalid: {error}"))?;
        if page.items.len() != response_count {
            eyre::bail!(
                "CurseForge pagination resultCount {} does not match {} returned items.",
                page.pagination.result_count,
                page.items.len()
            );
        }
        let total_count = usize::try_from(page.pagination.total_count)
            .map_err(|error| eyre::eyre!("CurseForge pagination total is invalid: {error}"))?;
        items.extend(page.items);
        if items.len() >= total_count || response_count == 0 {
            return Ok(items);
        }
        let advanced = page
            .pagination
            .index
            .checked_add(page.pagination.result_count)
            .ok_or_else(|| eyre::eyre!("CurseForge pagination index overflow."))?;
        if advanced <= next_index {
            eyre::bail!("CurseForge pagination did not advance past index {next_index}.");
        }
        next_index = advanced;
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::curseforge::CurseforgeProjectFileId;
    use crate::curseforge::CurseforgeSortableGameVersion;
    use std::io::Read;
    use std::io::Write;
    use std::net::TcpListener;
    use std::thread;

    #[test]
    fn pagination_collects_every_page_in_server_order() {
        let mut requested_indices = Vec::new();
        let items = collect_pages(|index| {
            requested_indices.push(index);
            let (items, pagination) = match index {
                0 => (
                    vec!["first", "second"],
                    CurseforgePagination {
                        index,
                        page_size: 2,
                        result_count: 2,
                        total_count: 3,
                    },
                ),
                2 => (
                    vec!["third"],
                    CurseforgePagination {
                        index,
                        page_size: 2,
                        result_count: 1,
                        total_count: 3,
                    },
                ),
                _ => unreachable!("unexpected page index"),
            };
            Ok(PagedResponse { items, pagination })
        })
        .expect("all pages should be collected");

        assert_eq!(requested_indices, [0, 2]);
        assert_eq!(items, ["first", "second", "third"]);
    }

    #[test]
    fn pagination_rejects_response_counts_that_do_not_match_items() {
        let error = collect_pages(|index| {
            Ok(PagedResponse {
                items: vec!["only"],
                pagination: CurseforgePagination {
                    index,
                    page_size: 50,
                    result_count: 2,
                    total_count: 2,
                },
            })
        })
        .expect_err("inconsistent pagination must fail");

        assert!(error.to_string().contains("does not match"));
    }

    #[test]
    fn core_loader_values_match_the_documented_minecraft_codes() {
        assert_eq!(CurseforgeModLoader::Forge.api_value(), 1);
        assert_eq!(CurseforgeModLoader::Neoforge.api_value(), 6);
        assert_eq!(
            CurseforgeModLoader::from_api_value(4),
            Some(CurseforgeModLoader::Fabric)
        );
    }

    #[test]
    fn exact_file_filter_requires_the_requested_version_and_loader_pair() {
        let mut file = CurseforgeProjectFileItem {
            id: CurseforgeProjectFileId(4_644_795),
            mod_id: Some(CurseforgeProjectId(268_560)),
            file_name: None,
            display_name: None,
            release_type: None,
            file_status: None,
            game_versions: vec!["1.19.2".to_owned(), "Forge".to_owned()],
            sortable_game_versions: vec![CurseforgeSortableGameVersion {
                game_version: Some("1.19.2".to_owned()),
                mod_loader: None,
            }],
            download_url: None,
            download_count: 0,
            file_date: None,
            hashes: Vec::new(),
        };
        assert!(file_matches_version_and_loader(
            &file,
            "1.19.2",
            CurseforgeModLoader::Forge
        ));
        assert!(!file_matches_version_and_loader(
            &file,
            "1.19.2",
            CurseforgeModLoader::Neoforge
        ));

        file.game_versions[0] = "1.19.1".to_owned();
        assert!(!file_matches_version_and_loader(
            &file,
            "1.19.2",
            CurseforgeModLoader::Forge
        ));
    }

    #[test]
    fn exact_project_and_file_requests_surface_not_found_responses() {
        let client = Client::builder()
            .no_proxy()
            .build()
            .expect("test HTTP client");
        let project_server = serve_one_response("404 Not Found", "unknown project");

        let project_error = fetch_project_at(
            &client,
            project_server.endpoint(),
            CurseforgeProjectId(268_560),
        )
        .expect_err("unknown project must fail");
        project_server.finish();
        assert!(project_error.to_string().contains("404 Not Found"));
        assert!(project_error.to_string().contains("unknown project"));
        let file_server = serve_one_response("404 Not Found", "unknown file");

        let file_error = fetch_project_file_at(
            &client,
            file_server.endpoint(),
            CurseforgeProjectId(268_560),
            CurseforgeProjectFileId(4_644_795),
        )
        .expect_err("unknown file must fail");
        file_server.finish();
        assert!(file_error.to_string().contains("404 Not Found"));
        assert!(file_error.to_string().contains("unknown file"));
    }

    struct OneResponseServer {
        endpoint: String,
        thread: thread::JoinHandle<()>,
    }

    impl OneResponseServer {
        fn endpoint(&self) -> &str {
            &self.endpoint
        }

        fn finish(self) {
            self.thread.join().expect("HTTP server");
        }
    }

    fn serve_one_response(status: &str, body: &str) -> OneResponseServer {
        let listener = TcpListener::bind("127.0.0.1:0").expect("loopback listener");
        let address = listener.local_addr().expect("listener address");
        let status = status.to_owned();
        let body = body.to_owned();
        let thread = thread::spawn(move || {
            let (mut stream, _) = listener.accept().expect("HTTP request");
            let mut request = [0_u8; 1024];
            let _ = stream.read(&mut request);
            let response = format!(
                "HTTP/1.1 {status}\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{body}",
                body.len()
            );
            stream
                .write_all(response.as_bytes())
                .expect("HTTP response");
        });
        OneResponseServer {
            endpoint: format!("http://{address}"),
            thread,
        }
    }
}
