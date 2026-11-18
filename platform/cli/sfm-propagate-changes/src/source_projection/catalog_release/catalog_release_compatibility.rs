use super::CatalogReleasePlan;
use eyre::Result;
use eyre::ensure;

/// Provider compatibility policy is distinct from the loader used for packaging.
/// The 1.20.1 artifact retains the existing dual Forge/NeoForge release policy.
#[derive(Debug)]
pub(super) struct CatalogReleaseCompatibility {
    pub(super) loader_slugs: Vec<String>,
    pub(super) curseforge_names: Vec<String>,
}

impl CatalogReleaseCompatibility {
    pub(super) fn for_metadata(metadata: &CatalogReleasePlan) -> Result<Self> {
        let loaders: &[(&str, &str)] = match metadata.target_id() {
            "1.19.2" | "1.19.4" | "1.20" => &[("forge", "Forge")],
            "1.20.1" => &[("forge", "Forge"), ("neoforge", "NeoForge")],
            "1.20.2" | "1.20.3" | "1.20.4" | "1.21.0" | "1.21.1" | "26.1.2" => {
                &[("neoforge", "NeoForge")]
            }
            other => eyre::bail!("no reviewed provider compatibility policy for {other}"),
        };
        ensure!(
            loaders
                .iter()
                .any(|(slug, _)| *slug == metadata.package_loader()),
            "provider compatibility excludes the actual package loader"
        );
        let mut names = vec![
            "Client".to_owned(),
            "Server".to_owned(),
            metadata.minecraft_version().to_owned(),
        ];
        names.extend(loaders.iter().map(|(_, name)| (*name).to_owned()));
        names.push(format!("Java {}", metadata.java_major()));
        Ok(Self {
            loader_slugs: loaders.iter().map(|(slug, _)| (*slug).to_owned()).collect(),
            curseforge_names: names,
        })
    }
}
