#[derive(Clone, Debug, Eq, PartialEq)]
struct MavenCoordinate {
    group: String,
    artifact: String,
    version: String,
    classifier: Option<String>,
    extension: String,
}

impl MavenCoordinate {
    fn parse(input: &str) -> eyre::Result<Self> {
        let (notation, extension) = input
            .split_once('@')
            .map_or((input, "jar"), |(left, right)| (left, right));
        let parts: Vec<&str> = notation.split(':').collect();
        match parts.as_slice() {
            [group, artifact, version] => Ok(Self {
                group: (*group).to_string(),
                artifact: (*artifact).to_string(),
                version: (*version).to_string(),
                classifier: None,
                extension: extension.to_string(),
            }),
            [group, artifact, version, classifier] => Ok(Self {
                group: (*group).to_string(),
                artifact: (*artifact).to_string(),
                version: (*version).to_string(),
                classifier: Some((*classifier).to_string()),
                extension: extension.to_string(),
            }),
            _ => eyre::bail!("Invalid Maven coordinate: {input}"),
        }
    }

    fn file_name(&self) -> String {
        let classifier = self
            .classifier
            .as_ref()
            .map_or_else(String::new, |classifier| format!("-{classifier}"));
        format!(
            "{}-{}{}.{}",
            self.artifact, self.version, classifier, self.extension
        )
    }

    fn with_classifier(&self, classifier: &str) -> Self {
        Self {
            group: self.group.clone(),
            artifact: self.artifact.clone(),
            version: self.version.clone(),
            classifier: Some(classifier.to_string()),
            extension: self.extension.clone(),
        }
    }

    fn with_extension(&self, extension: &str) -> Self {
        Self {
            group: self.group.clone(),
            artifact: self.artifact.clone(),
            version: self.version.clone(),
            classifier: self.classifier.clone(),
            extension: extension.to_string(),
        }
    }
}

impl std::fmt::Display for MavenCoordinate {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}:{}:{}", self.group, self.artifact, self.version)?;
        if let Some(classifier) = &self.classifier {
            write!(f, ":{classifier}")?;
        }
        if self.extension != "jar" {
            write!(f, "@{}", self.extension)?;
        }
        Ok(())
    }
}
