use crate::curseforge::CurseforgeGameVersion;
use crate::curseforge::CurseforgeGameVersionTypeId;
use crate::worktree::parse_version;
use facet::Facet;
use std::collections::HashMap;
use std::fmt;
use std::ops::Deref;

#[derive(Facet, Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash)]
#[facet(transparent)]
pub struct CurseforgeGameVersionId(pub u64);

impl CurseforgeGameVersionId {
    #[must_use]
    pub fn build_index(
        game_versions: &[CurseforgeGameVersion],
    ) -> HashMap<String, Vec<CurseforgeGameVersion>> {
        let mut map: HashMap<String, Vec<CurseforgeGameVersion>> =
            HashMap::with_capacity(game_versions.len());
        for version in game_versions {
            let name_key = Self::normalize_version_key(&version.name);
            map.entry(name_key).or_default().push(version.clone());
        }
        map
    }

    /// # Errors
    ///
    /// Returns an error when the named `CurseForge` game version is missing or ambiguous for the required type ID.
    pub fn resolve_exact_type_id(
        game_version_index: &HashMap<String, Vec<CurseforgeGameVersion>>,
        name: &str,
        required_type_id: CurseforgeGameVersionTypeId,
    ) -> eyre::Result<Self> {
        let key = Self::normalize_version_key(name);
        let candidates = game_version_index
            .get(&key)
            .ok_or_else(|| eyre::eyre!("Could not find required game version '{name}'"))?;

        let matches: Vec<&CurseforgeGameVersion> = candidates
            .iter()
            .filter(|candidate| {
                candidate.name == name && candidate.game_version_type_id == Some(required_type_id)
            })
            .collect();

        match matches.as_slice() {
            [single] => Ok(single.id),
            [] => eyre::bail!(
                "Could not resolve required game version '{}' with type id {}. Candidates: {}",
                name,
                required_type_id,
                Self::format_candidates(candidates)
            ),
            _ => {
                let summary = matches
                    .iter()
                    .map(|candidate| candidate.id.to_string())
                    .collect::<Vec<_>>()
                    .join(", ");
                eyre::bail!(
                    "Ambiguous game version '{}' with type id {}. Matching IDs: {}",
                    name,
                    required_type_id,
                    summary
                )
            }
        }
    }

    /// # Errors
    ///
    /// Returns an error when the Minecraft version is missing or resolves to multiple `CurseForge` metadata IDs.
    pub fn resolve_minecraft_version_id(
        game_version_index: &HashMap<String, Vec<CurseforgeGameVersion>>,
        minecraft_version: &str,
    ) -> eyre::Result<Self> {
        let key = Self::normalize_version_key(minecraft_version);
        let candidates = game_version_index.get(&key).ok_or_else(|| {
            eyre::eyre!("Could not find required Minecraft version '{minecraft_version}'")
        })?;

        let matches: Vec<&CurseforgeGameVersion> = candidates
            .iter()
            .filter(|candidate| {
                candidate.name == minecraft_version
                    && candidate
                        .game_version_type_id
                        .is_some_and(|type_id| type_id.0 != 1 && type_id.0 != 615)
                    && parse_version(&candidate.name).is_some()
            })
            .collect();

        match matches.as_slice() {
            [single] => Ok(single.id),
            [] => eyre::bail!(
                "Could not resolve required Minecraft version '{}'. Candidates: {}",
                minecraft_version,
                Self::format_candidates(candidates)
            ),
            _ => {
                let summary = matches
                    .iter()
                    .map(|candidate| {
                        let type_id = candidate
                            .game_version_type_id
                            .map_or_else(|| "<none>".to_string(), |id| id.to_string());
                        format!("{}(type={})", candidate.id, type_id)
                    })
                    .collect::<Vec<_>>()
                    .join(", ");
                eyre::bail!(
                    "Ambiguous Minecraft version '{}'. Matching IDs: {}",
                    minecraft_version,
                    summary
                )
            }
        }
    }

    /// # Errors
    ///
    /// Returns an error when any requested metadata name cannot be resolved to a `CurseForge` game-version ID.
    pub fn resolve_game_version_ids(
        game_version_index: &HashMap<String, Vec<CurseforgeGameVersion>>,
        names: &[String],
    ) -> eyre::Result<Vec<Self>> {
        let mut output = Vec::with_capacity(names.len());
        for name in names {
            let id = match name.as_str() {
                "Forge" => Self::resolve_exact_type_id(
                    game_version_index,
                    "Forge",
                    CurseforgeGameVersionTypeId(68_441),
                )?,
                "NeoForge" => Self::resolve_exact_type_id(
                    game_version_index,
                    "NeoForge",
                    CurseforgeGameVersionTypeId(68_441),
                )?,
                "Client" => Self::resolve_exact_type_id(
                    game_version_index,
                    "Client",
                    CurseforgeGameVersionTypeId(75_208),
                )?,
                "Server" => Self::resolve_exact_type_id(
                    game_version_index,
                    "Server",
                    CurseforgeGameVersionTypeId(75_208),
                )?,
                "Java 17" => Self::resolve_exact_type_id(
                    game_version_index,
                    "Java 17",
                    CurseforgeGameVersionTypeId(2),
                )?,
                "Java 21" => Self::resolve_exact_type_id(
                    game_version_index,
                    "Java 21",
                    CurseforgeGameVersionTypeId(2),
                )?,
                "Java 25" => Self::resolve_exact_type_id(
                    game_version_index,
                    "Java 25",
                    CurseforgeGameVersionTypeId(2),
                )?,
                minecraft if parse_version(minecraft).is_some() => {
                    Self::resolve_minecraft_version_id(game_version_index, minecraft)?
                }
                _ => eyre::bail!("Unsupported release metadata name '{name}'"),
            };

            if !output.contains(&id) {
                output.push(id);
            }
        }
        Ok(output)
    }

    #[must_use]
    pub fn normalize_version_key(input: &str) -> String {
        input.trim().to_ascii_lowercase().replace([' ', '_'], "-")
    }

    #[must_use]
    pub fn format_candidates(candidates: &[CurseforgeGameVersion]) -> String {
        candidates
            .iter()
            .map(|candidate| {
                let type_id = candidate
                    .game_version_type_id
                    .map_or_else(|| "<none>".to_string(), |id| id.to_string());
                format!(
                    "id={},type={},slug={}",
                    candidate.id,
                    type_id,
                    candidate.slug.clone().unwrap_or_default()
                )
            })
            .collect::<Vec<_>>()
            .join("; ")
    }
}

impl From<u64> for CurseforgeGameVersionId {
    fn from(value: u64) -> Self {
        Self(value)
    }
}

impl AsRef<u64> for CurseforgeGameVersionId {
    fn as_ref(&self) -> &u64 {
        &self.0
    }
}

impl Deref for CurseforgeGameVersionId {
    type Target = u64;

    fn deref(&self) -> &Self::Target {
        &self.0
    }
}

impl fmt::Display for CurseforgeGameVersionId {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        self.0.fmt(f)
    }
}

#[cfg(test)]
mod tests {
    use super::CurseforgeGameVersionId;

    #[test]
    fn facet_json_deserializes_transparent_game_version_id() {
        let id = facet_json::from_str::<CurseforgeGameVersionId>("12345").unwrap();
        assert_eq!(id, CurseforgeGameVersionId(12_345));
    }
}
