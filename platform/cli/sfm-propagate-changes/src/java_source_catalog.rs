use std::path::Path;
use std::path::PathBuf;

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum CatalogJavaSourceRootKind {
    Declared,
    Generated,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum JavaBuildSourceGroup {
    Main,
    Optional,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) struct JavaSourceSetDeclaration {
    pub id: &'static str,
    pub visible_source_sets: &'static [&'static str],
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) struct JavaSourceRootDeclaration {
    pub id: &'static str,
    pub source_set: &'static str,
    pub minecraft_relative_path: &'static str,
    pub kind: CatalogJavaSourceRootKind,
    pub build_group: Option<JavaBuildSourceGroup>,
    pub honors_source_excludes: bool,
}

impl JavaSourceRootDeclaration {
    pub fn resolve(self, minecraft_dir: &Path) -> PathBuf {
        minecraft_dir.join(self.minecraft_relative_path)
    }
}

#[derive(Clone, Copy, Debug)]
pub(crate) struct JavaSourceCatalog {
    pub source_sets: &'static [JavaSourceSetDeclaration],
    pub roots: &'static [JavaSourceRootDeclaration],
}

impl JavaSourceCatalog {
    pub fn source_set(self, id: &str) -> Option<JavaSourceSetDeclaration> {
        self.source_sets
            .iter()
            .copied()
            .find(|source_set| source_set.id == id)
    }

    pub fn roots_for_source_set(
        self,
        source_set: &str,
    ) -> impl Iterator<Item = JavaSourceRootDeclaration> {
        self.roots
            .iter()
            .copied()
            .filter(move |root| root.source_set == source_set)
    }

    pub fn build_roots(
        self,
        group: JavaBuildSourceGroup,
    ) -> impl Iterator<Item = JavaSourceRootDeclaration> {
        self.roots
            .iter()
            .copied()
            .filter(move |root| root.build_group == Some(group))
    }

    pub fn validate(self) -> eyre::Result<()> {
        let mut source_set_ids = std::collections::BTreeSet::new();
        for source_set in self.source_sets {
            if !source_set_ids.insert(source_set.id) {
                eyre::bail!("duplicate Java source-set declaration `{}`", source_set.id);
            }
            if !source_set.visible_source_sets.iter().all(|visible| {
                self.source_sets
                    .iter()
                    .any(|candidate| candidate.id == *visible)
            }) {
                eyre::bail!(
                    "Java source set `{}` references an undeclared visible source set",
                    source_set.id
                );
            }
        }

        let mut root_ids = std::collections::BTreeSet::new();
        for root in self.roots {
            if !root_ids.insert(root.id) {
                eyre::bail!("duplicate Java source-root declaration `{}`", root.id);
            }
            if !source_set_ids.contains(root.source_set) {
                eyre::bail!(
                    "Java source root `{}` references undeclared source set `{}`",
                    root.id,
                    root.source_set
                );
            }
        }
        Ok(())
    }
}

const JAVA_SOURCE_SETS: &[JavaSourceSetDeclaration] = &[
    JavaSourceSetDeclaration {
        id: "main",
        visible_source_sets: &["main"],
    },
    JavaSourceSetDeclaration {
        id: "gametest",
        visible_source_sets: &["gametest", "main"],
    },
    JavaSourceSetDeclaration {
        id: "datagen",
        visible_source_sets: &["datagen", "main"],
    },
    JavaSourceSetDeclaration {
        id: "test",
        visible_source_sets: &["test", "main"],
    },
    JavaSourceSetDeclaration {
        id: "generated",
        visible_source_sets: &["generated"],
    },
];

const JAVA_SOURCE_ROOTS: &[JavaSourceRootDeclaration] = &[
    JavaSourceRootDeclaration {
        id: "declared-main",
        source_set: "main",
        minecraft_relative_path: "src/main/java",
        kind: CatalogJavaSourceRootKind::Declared,
        build_group: Some(JavaBuildSourceGroup::Main),
        honors_source_excludes: true,
    },
    JavaSourceRootDeclaration {
        id: "generated-antlr-main",
        source_set: "main",
        minecraft_relative_path: "build/sfm-toolchain/project/generated-src/antlr/main",
        kind: CatalogJavaSourceRootKind::Generated,
        build_group: Some(JavaBuildSourceGroup::Main),
        honors_source_excludes: false,
    },
    JavaSourceRootDeclaration {
        id: "declared-gametest",
        source_set: "gametest",
        minecraft_relative_path: "src/gametest/java",
        kind: CatalogJavaSourceRootKind::Declared,
        build_group: Some(JavaBuildSourceGroup::Optional),
        honors_source_excludes: true,
    },
    JavaSourceRootDeclaration {
        id: "declared-datagen",
        source_set: "datagen",
        minecraft_relative_path: "src/datagen/java",
        kind: CatalogJavaSourceRootKind::Declared,
        build_group: Some(JavaBuildSourceGroup::Optional),
        honors_source_excludes: true,
    },
    JavaSourceRootDeclaration {
        id: "declared-test",
        source_set: "test",
        minecraft_relative_path: "src/test/java",
        kind: CatalogJavaSourceRootKind::Declared,
        build_group: None,
        honors_source_excludes: true,
    },
    JavaSourceRootDeclaration {
        id: "declared-generated",
        source_set: "generated",
        minecraft_relative_path: "src/generated/java",
        kind: CatalogJavaSourceRootKind::Declared,
        build_group: None,
        honors_source_excludes: true,
    },
];

pub(crate) const JAVA_SOURCE_CATALOG: JavaSourceCatalog = JavaSourceCatalog {
    source_sets: JAVA_SOURCE_SETS,
    roots: JAVA_SOURCE_ROOTS,
};

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn shared_java_source_catalog_is_valid_and_deterministic() {
        JAVA_SOURCE_CATALOG
            .validate()
            .expect("shared Java source catalog should be internally consistent");
        assert_eq!(
            JAVA_SOURCE_CATALOG
                .build_roots(JavaBuildSourceGroup::Main)
                .map(|root| root.id)
                .collect::<Vec<_>>(),
            ["declared-main", "generated-antlr-main"]
        );
        assert_eq!(
            JAVA_SOURCE_CATALOG
                .source_set("gametest")
                .expect("gametest source set")
                .visible_source_sets,
            ["gametest", "main"]
        );
    }

    #[test]
    fn a_newly_declared_root_is_visible_to_catalog_consumers() {
        const SETS: &[JavaSourceSetDeclaration] = &[JavaSourceSetDeclaration {
            id: "main",
            visible_source_sets: &["main"],
        }];
        const ROOTS: &[JavaSourceRootDeclaration] = &[
            JavaSourceRootDeclaration {
                id: "declared-main",
                source_set: "main",
                minecraft_relative_path: "src/main/java",
                kind: CatalogJavaSourceRootKind::Declared,
                build_group: Some(JavaBuildSourceGroup::Main),
                honors_source_excludes: true,
            },
            JavaSourceRootDeclaration {
                id: "generated-example-main",
                source_set: "main",
                minecraft_relative_path: "build/generated/example/main",
                kind: CatalogJavaSourceRootKind::Generated,
                build_group: Some(JavaBuildSourceGroup::Main),
                honors_source_excludes: false,
            },
        ];
        const CATALOG: JavaSourceCatalog = JavaSourceCatalog {
            source_sets: SETS,
            roots: ROOTS,
        };

        CATALOG
            .validate()
            .expect("extended catalog should be valid");
        assert_eq!(
            CATALOG
                .roots_for_source_set("main")
                .map(|root| root.id)
                .collect::<Vec<_>>(),
            ["declared-main", "generated-example-main"]
        );
        assert_eq!(
            CATALOG
                .build_roots(JavaBuildSourceGroup::Main)
                .map(|root| root.id)
                .collect::<Vec<_>>(),
            ["declared-main", "generated-example-main"]
        );
    }
}
