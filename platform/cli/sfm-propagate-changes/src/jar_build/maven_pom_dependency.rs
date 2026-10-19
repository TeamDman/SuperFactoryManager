use super::xml_node::XmlNode;
use std::collections::BTreeMap;
#[derive(Clone, Debug, Default)]
pub(super) struct MavenPomDependency {
    pub group: String,
    pub artifact: String,
    pub version: Option<String>,
    pub classifier: Option<String>,
    pub kind: Option<String>,
    pub scope: Option<String>,
    pub optional: Option<String>,
    pub exclusions: Vec<String>,
}
impl MavenPomDependency {
    pub fn parse(node: &XmlNode) -> eyre::Result<Self> {
        let required = |name| {
            node.value(name)?
                .ok_or_else(|| eyre::eyre!("dependency missing {name}"))
        };
        let exclusions = node.child("exclusions")?.map_or_else(
            || Ok(Vec::new()),
            |n| {
                n.children
                    .iter()
                    .filter(|d| d.name == "exclusion")
                    .map(|d| {
                        Ok(format!(
                            "{}:{}",
                            d.value("groupId")?
                                .ok_or_else(|| eyre::eyre!("exclusion group missing"))?,
                            d.value("artifactId")?
                                .ok_or_else(|| eyre::eyre!("exclusion artifact missing"))?
                        ))
                    })
                    .collect::<eyre::Result<Vec<_>>>()
            },
        )?;
        Ok(Self {
            group: required("groupId")?,
            artifact: required("artifactId")?,
            version: node.value("version")?,
            classifier: node.value("classifier")?,
            kind: node.value("type")?,
            scope: node.value("scope")?,
            optional: node.value("optional")?,
            exclusions,
        })
    }
    pub fn interpolate(&self, properties: &BTreeMap<String, String>) -> eyre::Result<Self> {
        let expand = |v: &Option<String>| {
            v.as_deref()
                .map(|v| super::maven_pom_model::expand(v, properties))
                .transpose()
        };
        Ok(Self {
            group: super::maven_pom_model::expand(&self.group, properties)?,
            artifact: super::maven_pom_model::expand(&self.artifact, properties)?,
            version: expand(&self.version)?,
            classifier: expand(&self.classifier)?,
            kind: expand(&self.kind)?,
            scope: expand(&self.scope)?,
            optional: expand(&self.optional)?,
            exclusions: self
                .exclusions
                .iter()
                .map(|v| super::maven_pom_model::expand(v, properties))
                .collect::<eyre::Result<_>>()?,
        })
    }
    pub fn resolved_key(&self, properties: &BTreeMap<String, String>) -> eyre::Result<String> {
        let mut identity = self.clone();
        identity.version = None;
        identity.scope = None;
        identity.optional = None;
        identity.exclusions.clear();
        Ok(identity.interpolate(properties)?.key())
    }
    pub fn key(&self) -> String {
        format!(
            "{}:{}:{}:{}",
            self.group,
            self.artifact,
            self.kind.as_deref().unwrap_or("jar"),
            self.classifier.as_deref().unwrap_or("")
        )
    }
    pub fn managed_by(mut self, managed: Option<&Self>) -> Self {
        if let Some(defaults) = managed {
            if self.version.is_none() {
                self.version.clone_from(&defaults.version);
            }
            if self.scope.is_none() {
                self.scope.clone_from(&defaults.scope);
            }
            if self.optional.is_none() {
                self.optional.clone_from(&defaults.optional);
            }
            if self.exclusions.is_empty() {
                self.exclusions.clone_from(&defaults.exclusions);
            }
        }
        self
    }
}
