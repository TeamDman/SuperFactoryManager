use eyre::Context;
use facet::Facet;
use std::path::Path;

pub const DEPENDENCY_SYMBOL_INDEX_IDENTITY_SCHEMA: &str = "sfm.dependency-symbol-index-identity/1";
pub const DEPENDENCY_SYMBOL_INDEX_STORE_FORMAT_VERSION: u32 = 3;

/// A path-free, semantic projection of the effective dependency lock and the
/// source provider selected for each indexed component.
///
/// This is deliberately not the lockfile model. In particular, it has nowhere
/// to store lockfile text, a worktree path, or a concrete cache path. The
/// Phase 0.8 source-provider adapter is responsible for projecting the
/// effective lock into this model.
#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub struct DependencySymbolIndexIdentityProjection {
    pub schema: String,
    pub effective_lock: EffectiveDependencyLockProjection,
    pub context: DependencyIndexContextProjection,
    pub parser: DependencyIndexParserProjection,
    pub format: DependencyIndexFormatProjection,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub struct EffectiveDependencyLockProjection {
    pub minecraft_dependency: String,
    pub loader_dependency: String,
    pub repositories: Vec<DependencyRepositoryProjection>,
    pub dependencies: Vec<DependencyProjection>,
}

#[derive(Clone, Debug, Eq, Facet, Ord, PartialEq, PartialOrd)]
pub struct DependencyRepositoryProjection {
    pub id: String,
    pub url: String,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub struct DependencyProjection {
    pub id: String,
    pub kind: String,
    pub role: String,
    pub components: Vec<DependencyComponentProjection>,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub struct DependencyComponentProjection {
    pub id: String,
    pub scopes: Vec<String>,
    pub acquisition: Vec<DependencySemanticProperty>,
    pub artifact: DependencyArtifactProjection,
    pub preferred_provider: Option<DependencySourceProviderProjection>,
}

#[derive(Clone, Debug, Eq, Facet, Ord, PartialEq, PartialOrd)]
pub struct DependencyArtifactProjection {
    pub id: String,
    pub content_hash: String,
    pub resolved_coordinate: Option<String>,
    pub provenance: String,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub struct DependencySourceProviderProjection {
    pub id: String,
    pub kind: DependencySourceProviderKind,
    pub priority: u64,
    pub portable_roots: Vec<String>,
    pub declaration: Vec<DependencySemanticProperty>,
    pub derived_checks: Vec<DependencySemanticProperty>,
}

#[derive(Clone, Copy, Debug, Eq, Facet, Ord, PartialEq, PartialOrd)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub enum DependencySourceProviderKind {
    MavenSources,
    Git,
    Decompile,
    PlatformPipeline,
}

/// A named semantic value from a provider/acquisition declaration.
///
/// Property names containing `path` are rejected. Portable source roots have
/// their own field, while concrete cache/worktree paths are intentionally not
/// identity inputs.
#[derive(Clone, Debug, Eq, Facet, Ord, PartialEq, PartialOrd)]
pub struct DependencySemanticProperty {
    pub key: String,
    pub value: String,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub struct DependencyIndexContextProjection {
    pub minecraft_version: String,
    pub loader_id: String,
    pub loader_version: String,
    pub java_release: String,
    pub toolchain_profile: String,
    pub active_features: Vec<String>,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub struct DependencyIndexParserProjection {
    pub parser: String,
    pub parser_version: String,
    pub grammar_fingerprint: String,
}

#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub struct DependencyIndexFormatProjection {
    pub store_format_version: u32,
    pub payload_schema: String,
    pub index_algorithm_fingerprint: String,
}

/// Content identity for one immutable dependency symbol index.
#[derive(Clone, Debug, Eq, Facet, PartialEq)]
pub struct DependencySymbolIndexIdentity {
    pub schema: String,
    pub digest: String,
    pub projection: DependencySymbolIndexIdentityProjection,
}

impl DependencySymbolIndexIdentityProjection {
    #[must_use]
    pub fn normalized(mut self) -> Self {
        self.effective_lock.repositories.sort();
        self.effective_lock.dependencies.sort_by(|left, right| {
            (&left.id, &left.kind, &left.role).cmp(&(&right.id, &right.kind, &right.role))
        });
        for dependency in &mut self.effective_lock.dependencies {
            dependency
                .components
                .sort_by(|left, right| left.id.cmp(&right.id));
            for component in &mut dependency.components {
                component.scopes.sort();
                component.scopes.dedup();
                component.acquisition.sort();
                if let Some(provider) = &mut component.preferred_provider {
                    provider.portable_roots.sort();
                    provider.portable_roots.dedup();
                    provider.declaration.sort();
                    provider.derived_checks.sort();
                }
            }
        }
        self.context.active_features.sort();
        self.context.active_features.dedup();
        self
    }

    /// Validate that every input is semantic and path-portable.
    ///
    /// # Errors
    ///
    /// Returns an error for missing identity inputs, local filesystem paths,
    /// path-shaped semantic properties, unsafe portable roots, or a zero store
    /// format version.
    pub fn validate(&self) -> eyre::Result<()> {
        require_eq(
            &self.schema,
            DEPENDENCY_SYMBOL_INDEX_IDENTITY_SCHEMA,
            "identity projection schema",
        )?;
        require_nonempty(
            &self.effective_lock.minecraft_dependency,
            "Minecraft dependency id",
        )?;
        require_nonempty(
            &self.effective_lock.loader_dependency,
            "loader dependency id",
        )?;
        for repository in &self.effective_lock.repositories {
            require_nonempty(&repository.id, "repository id")?;
            require_portable_semantic_value(&repository.url, "repository URL")?;
        }
        for dependency in &self.effective_lock.dependencies {
            require_nonempty(&dependency.id, "dependency id")?;
            require_nonempty(&dependency.kind, "dependency kind")?;
            require_nonempty(&dependency.role, "dependency role")?;
            for component in &dependency.components {
                require_nonempty(&component.id, "dependency component id")?;
                for scope in &component.scopes {
                    require_nonempty(scope, "dependency scope")?;
                }
                validate_properties(&component.acquisition, "component acquisition")?;
                require_nonempty(&component.artifact.id, "artifact id")?;
                require_nonempty(&component.artifact.content_hash, "artifact content hash")?;
                require_nonempty(&component.artifact.provenance, "artifact provenance")?;
                if let Some(coordinate) = &component.artifact.resolved_coordinate {
                    require_portable_semantic_value(coordinate, "resolved coordinate")?;
                }
                if let Some(provider) = &component.preferred_provider {
                    require_nonempty(&provider.id, "source provider id")?;
                    for root in &provider.portable_roots {
                        validate_portable_root(root)?;
                    }
                    validate_properties(&provider.declaration, "provider declaration")?;
                    validate_properties(&provider.derived_checks, "provider derived checks")?;
                }
            }
        }
        require_nonempty(&self.context.minecraft_version, "Minecraft version")?;
        require_nonempty(&self.context.loader_id, "loader id")?;
        require_nonempty(&self.context.loader_version, "loader version")?;
        require_nonempty(&self.context.java_release, "Java release")?;
        require_nonempty(&self.context.toolchain_profile, "toolchain profile")?;
        for feature in &self.context.active_features {
            require_nonempty(feature, "active feature")?;
        }
        require_nonempty(&self.parser.parser, "parser id")?;
        require_nonempty(&self.parser.parser_version, "parser version")?;
        require_nonempty(
            &self.parser.grammar_fingerprint,
            "parser grammar fingerprint",
        )?;
        if self.format.store_format_version == 0 {
            eyre::bail!("dependency symbol index store format version must not be zero");
        }
        require_nonempty(&self.format.payload_schema, "payload schema")?;
        require_nonempty(
            &self.format.index_algorithm_fingerprint,
            "index algorithm fingerprint",
        )?;
        Ok(())
    }
}

impl DependencySymbolIndexIdentity {
    /// Build a deterministic identity from semantic inputs.
    ///
    /// # Errors
    ///
    /// Returns an error when the projection is invalid or cannot be serialized.
    pub fn from_projection(
        projection: DependencySymbolIndexIdentityProjection,
    ) -> eyre::Result<Self> {
        let projection = projection.normalized();
        projection.validate()?;
        let canonical = facet_json::to_string(&projection)
            .wrap_err("Failed to serialize dependency symbol index identity projection")?;
        let digest = format!("blake3:{}", blake3::hash(canonical.as_bytes()).to_hex());
        Ok(Self {
            schema: DEPENDENCY_SYMBOL_INDEX_IDENTITY_SCHEMA.to_owned(),
            digest,
            projection,
        })
    }

    /// Recompute and validate the recorded digest.
    ///
    /// # Errors
    ///
    /// Returns an error when the identity schema, projection, or digest is invalid.
    pub fn validate(&self) -> eyre::Result<()> {
        require_eq(
            &self.schema,
            DEPENDENCY_SYMBOL_INDEX_IDENTITY_SCHEMA,
            "identity schema",
        )?;
        let expected = Self::from_projection(self.projection.clone())?;
        if expected.digest != self.digest {
            eyre::bail!(
                "dependency symbol index identity digest mismatch: recorded {}, computed {}",
                self.digest,
                expected.digest
            );
        }
        Ok(())
    }

    /// Filesystem-safe content key used as the immutable cache directory name.
    ///
    /// # Errors
    ///
    /// Returns an error if the digest is not a canonical BLAKE3 identity.
    pub fn cache_key(&self) -> eyre::Result<&str> {
        let Some(hex) = self.digest.strip_prefix("blake3:") else {
            eyre::bail!("dependency symbol index identity is not a BLAKE3 digest");
        };
        if hex.len() != 64 || !hex.bytes().all(|byte| byte.is_ascii_hexdigit()) {
            eyre::bail!("dependency symbol index identity is not 64 hexadecimal digits");
        }
        Ok(hex)
    }
}

fn validate_properties(properties: &[DependencySemanticProperty], owner: &str) -> eyre::Result<()> {
    for property in properties {
        require_nonempty(&property.key, &format!("{owner} property key"))?;
        if property.key.to_ascii_lowercase().contains("path") {
            eyre::bail!(
                "{owner} property `{}` is path-shaped; concrete paths are forbidden identity inputs",
                property.key
            );
        }
        require_portable_semantic_value(
            &property.value,
            &format!("{owner} property `{}`", property.key),
        )?;
    }
    Ok(())
}

fn validate_portable_root(root: &str) -> eyre::Result<()> {
    require_nonempty(root, "portable source root")?;
    let path = Path::new(root);
    if path.is_absolute()
        || root.contains('\\')
        || root.starts_with('/')
        || root.starts_with("//")
        || has_windows_drive_prefix(root)
        || path
            .components()
            .any(|component| !matches!(component, std::path::Component::Normal(_)))
    {
        eyre::bail!("source root `{root}` is not a portable relative path");
    }
    Ok(())
}

fn require_portable_semantic_value(value: &str, name: &str) -> eyre::Result<()> {
    require_nonempty(value, name)?;
    if value.starts_with("file:")
        || value.starts_with("\\\\")
        || has_windows_drive_prefix(value)
        || Path::new(value).is_absolute()
    {
        eyre::bail!("{name} contains a machine-local absolute path: {value}");
    }
    Ok(())
}

fn has_windows_drive_prefix(value: &str) -> bool {
    value.as_bytes().get(1) == Some(&b':')
        && value
            .as_bytes()
            .first()
            .is_some_and(u8::is_ascii_alphabetic)
}

fn require_nonempty(value: &str, name: &str) -> eyre::Result<()> {
    if value.trim().is_empty() {
        eyre::bail!("{name} must not be empty");
    }
    Ok(())
}

fn require_eq(actual: &str, expected: &str, name: &str) -> eyre::Result<()> {
    if actual != expected {
        eyre::bail!("{name} `{actual}` does not match `{expected}`");
    }
    Ok(())
}

#[cfg(test)]
#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn dependency_index_identity_is_order_independent_and_byte_stable() {
        let first = DependencySymbolIndexIdentity::from_projection(fixture()).expect("identity");
        let mut reordered = fixture();
        reordered.effective_lock.repositories.reverse();
        reordered.effective_lock.dependencies.reverse();
        reordered.effective_lock.dependencies[0]
            .components
            .reverse();
        reordered.context.active_features.reverse();
        let second = DependencySymbolIndexIdentity::from_projection(reordered).expect("identity");

        assert_eq!(first, second);
        assert_eq!(first.cache_key().expect("cache key").len(), 64);
        first.validate().expect("self-valid identity");
    }

    #[test]
    fn dependency_index_identity_changes_for_every_semantic_input_family() {
        let baseline = identity(fixture());
        let variants = [
            mutate(fixture(), |projection| {
                projection.effective_lock.dependencies[0].components[0]
                    .artifact
                    .content_hash = "blake3:different-artifact".to_owned();
            }),
            mutate(fixture(), |projection| {
                projection.effective_lock.dependencies[0].components[0]
                    .preferred_provider
                    .as_mut()
                    .expect("provider")
                    .derived_checks[0]
                    .value = "different-provider-check".to_owned();
            }),
            mutate(fixture(), |projection| {
                projection.context.minecraft_version = "1.20.1".to_owned();
            }),
            mutate(fixture(), |projection| {
                projection.parser.grammar_fingerprint = "blake3:new-grammar".to_owned();
            }),
            mutate(fixture(), |projection| {
                projection.format.index_algorithm_fingerprint = "java-index/2".to_owned();
            }),
        ];

        for variant in variants {
            assert_ne!(baseline.digest, identity(variant).digest);
        }
    }

    #[test]
    fn dependency_index_identity_rejects_machine_local_paths() {
        for (key, value) in [
            ("tree_path", "portable-looking"),
            ("tree", r"D:\\cache\\sources"),
            ("tree", "file:///tmp/sources"),
        ] {
            let mut projection = fixture();
            projection.effective_lock.dependencies[0].components[0]
                .preferred_provider
                .as_mut()
                .expect("provider")
                .derived_checks
                .push(DependencySemanticProperty {
                    key: key.to_owned(),
                    value: value.to_owned(),
                });
            let _error = DependencySymbolIndexIdentity::from_projection(projection)
                .expect_err("machine-local identity input must be rejected");
        }
    }

    #[test]
    fn dependency_index_identity_round_trips_as_typed_facet_json() {
        let identity = identity(fixture());
        let json = facet_json::to_string_pretty(&identity).expect("identity JSON");
        let decoded: DependencySymbolIndexIdentity =
            facet_json::from_str(&json).expect("identity JSON round trip");
        assert_eq!(decoded, identity);
        decoded.validate().expect("decoded identity");
    }

    fn mutate(
        mut projection: DependencySymbolIndexIdentityProjection,
        mutation: impl FnOnce(&mut DependencySymbolIndexIdentityProjection),
    ) -> DependencySymbolIndexIdentityProjection {
        mutation(&mut projection);
        projection
    }

    fn identity(
        projection: DependencySymbolIndexIdentityProjection,
    ) -> DependencySymbolIndexIdentity {
        DependencySymbolIndexIdentity::from_projection(projection).expect("fixture identity")
    }

    fn fixture() -> DependencySymbolIndexIdentityProjection {
        DependencySymbolIndexIdentityProjection {
            schema: DEPENDENCY_SYMBOL_INDEX_IDENTITY_SCHEMA.to_owned(),
            effective_lock: EffectiveDependencyLockProjection {
                minecraft_dependency: "minecraft".to_owned(),
                loader_dependency: "forge".to_owned(),
                repositories: vec![
                    DependencyRepositoryProjection {
                        id: "forge".to_owned(),
                        url: "https://maven.minecraftforge.net".to_owned(),
                    },
                    DependencyRepositoryProjection {
                        id: "central".to_owned(),
                        url: "https://repo.maven.apache.org/maven2".to_owned(),
                    },
                ],
                dependencies: vec![
                    DependencyProjection {
                        id: "minecraft".to_owned(),
                        kind: "minecraft".to_owned(),
                        role: "platform".to_owned(),
                        components: vec![component("client", "platform-client")],
                    },
                    DependencyProjection {
                        id: "forge".to_owned(),
                        kind: "loader".to_owned(),
                        role: "platform".to_owned(),
                        components: vec![component("universal", "platform-loader")],
                    },
                ],
            },
            context: DependencyIndexContextProjection {
                minecraft_version: "1.19.2".to_owned(),
                loader_id: "forge".to_owned(),
                loader_version: "43.4.0".to_owned(),
                java_release: "17".to_owned(),
                toolchain_profile: "default".to_owned(),
                active_features: vec!["rust".to_owned(), "main".to_owned()],
            },
            parser: DependencyIndexParserProjection {
                parser: "arborium-java".to_owned(),
                parser_version: "2.18.1".to_owned(),
                grammar_fingerprint: "blake3:grammar".to_owned(),
            },
            format: DependencyIndexFormatProjection {
                store_format_version: DEPENDENCY_SYMBOL_INDEX_STORE_FORMAT_VERSION,
                payload_schema: "sfm.dependency-symbol-index-payload/1".to_owned(),
                index_algorithm_fingerprint: "java-index/1".to_owned(),
            },
        }
    }

    fn component(id: &str, provider_id: &str) -> DependencyComponentProjection {
        DependencyComponentProjection {
            id: id.to_owned(),
            scopes: vec!["runtime".to_owned(), "compile".to_owned()],
            acquisition: vec![DependencySemanticProperty {
                key: "requested-version".to_owned(),
                value: "1.19.2".to_owned(),
            }],
            artifact: DependencyArtifactProjection {
                id: format!("{id}-artifact"),
                content_hash: format!("blake3:{id}"),
                resolved_coordinate: None,
                provenance: "toolchain-generated".to_owned(),
            },
            preferred_provider: Some(DependencySourceProviderProjection {
                id: provider_id.to_owned(),
                kind: DependencySourceProviderKind::PlatformPipeline,
                priority: 0,
                portable_roots: vec!["src/main/java".to_owned()],
                declaration: vec![DependencySemanticProperty {
                    key: "kind".to_owned(),
                    value: id.to_owned(),
                }],
                derived_checks: vec![DependencySemanticProperty {
                    key: "fingerprint".to_owned(),
                    value: format!("blake3:{provider_id}"),
                }],
            }),
        }
    }
}
