use crate::prism::PrismComponent;
use crate::prism::PrismLoaderComponent;
use eyre::Context;
use facet::Facet;

const PRISM_META_BASE_URL: &str = "https://meta.prismlauncher.org/v1";

#[derive(Clone, Copy, Debug, Eq, Facet, PartialEq)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub enum PrismLoaderSelection {
    Pinned,
    Recommended,
    Latest,
}

#[derive(Debug, Facet)]
struct PrismMetaIndex {
    uid: String,
    name: String,
    versions: Vec<PrismMetaVersion>,
}

#[derive(Clone, Debug, Facet)]
struct PrismMetaVersion {
    version: String,
    #[facet(default, rename = "releaseTime")]
    release_time: String,
    #[facet(default)]
    r#type: String,
    #[facet(default)]
    recommended: bool,
    #[facet(default)]
    requires: Vec<PrismMetaRequire>,
}

#[derive(Clone, Debug, Facet)]
struct PrismMetaRequire {
    uid: String,
    #[facet(default)]
    equals: Option<String>,
    #[facet(default)]
    suggests: Option<String>,
}

#[derive(Clone, Debug)]
struct LoaderCoordinate {
    uid: &'static str,
    version: String,
}

/// Resolve a Prism loader component for a Minecraft version.
///
/// # Errors
///
/// Returns an error if Prism metadata cannot be fetched or the Gradle coordinate is unsupported.
pub fn resolve_loader_component(
    minecraft_version: &str,
    coordinate: &str,
    loader_selection: PrismLoaderSelection,
) -> eyre::Result<PrismLoaderComponent> {
    let pinned = loader_coordinate(coordinate)?;
    let index = fetch_meta_index(pinned.uid)?;
    let versions = versions_requiring_minecraft(&index, minecraft_version);
    let recommended_version = versions
        .iter()
        .find(|version| version.recommended)
        .map(|version| version.version.clone());
    let latest_version = versions
        .iter()
        .max_by(|left, right| compare_prism_versions(left, right))
        .map(|version| version.version.clone());
    let selected = match loader_selection {
        PrismLoaderSelection::Pinned => pinned.version.clone(),
        PrismLoaderSelection::Recommended => recommended_version.clone().ok_or_else(|| {
            eyre::eyre!(
                "Prism metadata has no recommended {} loader for Minecraft {minecraft_version}",
                index.name
            )
        })?,
        PrismLoaderSelection::Latest => latest_version.clone().ok_or_else(|| {
            eyre::eyre!(
                "Prism metadata has no {} loader for Minecraft {minecraft_version}",
                index.name
            )
        })?,
    };

    Ok(PrismLoaderComponent {
        cached_name: index.name,
        uid: index.uid,
        version: selected,
        pinned_version: pinned.version,
        recommended_version,
        latest_version,
    })
}

/// Resolve the LWJGL component Prism expects for a Minecraft version.
///
/// # Errors
///
/// Returns an error if Prism Minecraft metadata cannot be fetched.
pub fn resolve_lwjgl_component(minecraft_version: &str) -> eyre::Result<Option<PrismComponent>> {
    let index = fetch_meta_index("net.minecraft")?;
    let Some(version) = index
        .versions
        .iter()
        .find(|version| version.version == minecraft_version)
    else {
        return Ok(None);
    };

    Ok(version.requires.iter().find_map(|require| {
        require
            .uid
            .starts_with("org.lwjgl")
            .then(|| PrismComponent {
                cached_name: require.uid.clone(),
                uid: require.uid.clone(),
                version: require.suggests.clone().unwrap_or_default(),
                dependency_only: true,
            })
    }))
}

fn fetch_meta_index(uid: &str) -> eyre::Result<PrismMetaIndex> {
    let url = format!("{PRISM_META_BASE_URL}/{uid}/index.json");
    let text = reqwest::blocking::Client::new()
        .get(&url)
        .header(
            reqwest::header::USER_AGENT,
            "sfm-propagate-changes/prism-sync",
        )
        .send()
        .wrap_err_with(|| format!("Failed to request Prism metadata: {url}"))?
        .error_for_status()
        .wrap_err_with(|| format!("Prism metadata request failed: {url}"))?
        .text()
        .wrap_err_with(|| format!("Failed to read Prism metadata response: {url}"))?;

    facet_json::from_str(&text).wrap_err_with(|| format!("Failed to parse Prism metadata: {url}"))
}

fn versions_requiring_minecraft<'a>(
    index: &'a PrismMetaIndex,
    minecraft_version: &str,
) -> Vec<&'a PrismMetaVersion> {
    index
        .versions
        .iter()
        .filter(|version| {
            version.requires.iter().any(|require| {
                require.uid == "net.minecraft"
                    && require.equals.as_deref() == Some(minecraft_version)
            })
        })
        .collect()
}

fn compare_prism_versions(left: &PrismMetaVersion, right: &PrismMetaVersion) -> std::cmp::Ordering {
    release_rank(left)
        .cmp(&release_rank(right))
        .then_with(|| left.release_time.cmp(&right.release_time))
        .then_with(|| left.version.cmp(&right.version))
}

fn release_rank(version: &PrismMetaVersion) -> u8 {
    u8::from(version.r#type == "release" || version.r#type.is_empty())
}

fn loader_coordinate(coordinate: &str) -> eyre::Result<LoaderCoordinate> {
    let mut parts = coordinate.split(':');
    let group = parts.next().unwrap_or_default();
    let artifact = parts.next().unwrap_or_default();
    let version = parts
        .next()
        .ok_or_else(|| eyre::eyre!("Invalid loader coordinate: {coordinate}"))?;

    match (group, artifact) {
        ("net.minecraftforge" | "net.neoforged", "forge") => Ok(LoaderCoordinate {
            uid: "net.minecraftforge",
            version: version
                .split_once('-')
                .map_or(version, |(_, loader_version)| loader_version)
                .to_string(),
        }),
        ("net.neoforged", "neoforge") => Ok(LoaderCoordinate {
            uid: "net.neoforged",
            version: version.to_string(),
        }),
        _ => eyre::bail!("Unsupported loader coordinate: {coordinate}"),
    }
}
