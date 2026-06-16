use facet::Facet;
use std::cmp::Ordering;
use std::fmt;
use std::ops::Deref;
use std::str::FromStr;

#[derive(Clone, Debug, Eq, Facet)]
pub struct MinecraftVersion {
    parts: Vec<u32>,
    text: String,
}

impl MinecraftVersion {
    #[must_use]
    pub fn as_str(&self) -> &str {
        &self.text
    }

    #[must_use]
    pub fn is_core_branch_text(text: &str) -> bool {
        if text.contains('-') {
            return false;
        }
        Self::parse(text).is_ok()
    }

    /// Parse a dotted numeric Minecraft version.
    ///
    /// # Errors
    ///
    /// Returns an error if the version is empty, contains a suffix, has fewer than two numeric
    /// segments, or includes a non-numeric segment.
    pub fn parse(text: &str) -> eyre::Result<Self> {
        let trimmed = text.trim();
        if trimmed.is_empty() {
            eyre::bail!("Minecraft version cannot be empty");
        }
        if trimmed.contains('-') {
            eyre::bail!("Minecraft version cannot contain a suffix: {text}");
        }

        let parts = trimmed
            .split('.')
            .map(|part| {
                if part.is_empty() {
                    eyre::bail!("Minecraft version contains an empty segment: {text}");
                }
                part.parse::<u32>().map_err(|err| {
                    eyre::eyre!("Invalid Minecraft version segment '{part}' in {text}: {err}")
                })
            })
            .collect::<eyre::Result<Vec<_>>>()?;

        if parts.len() < 2 {
            eyre::bail!("Minecraft version must have at least major and minor segments: {text}");
        }

        Ok(Self {
            parts,
            text: trimmed.to_string(),
        })
    }

    fn compare_parts(&self, other: &Self) -> Ordering {
        let len = self.parts.len().max(other.parts.len());
        for index in 0..len {
            let left = self.parts.get(index).copied().unwrap_or_default();
            let right = other.parts.get(index).copied().unwrap_or_default();
            match left.cmp(&right) {
                Ordering::Equal => {}
                ordering => return ordering,
            }
        }
        Ordering::Equal
    }
}

impl fmt::Display for MinecraftVersion {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(&self.text)
    }
}

impl AsRef<str> for MinecraftVersion {
    fn as_ref(&self) -> &str {
        &self.text
    }
}

impl Deref for MinecraftVersion {
    type Target = str;

    fn deref(&self) -> &Self::Target {
        &self.text
    }
}

impl FromStr for MinecraftVersion {
    type Err = eyre::Report;

    fn from_str(text: &str) -> Result<Self, Self::Err> {
        Self::parse(text)
    }
}

impl Ord for MinecraftVersion {
    fn cmp(&self, other: &Self) -> Ordering {
        self.compare_parts(other)
    }
}

impl PartialOrd for MinecraftVersion {
    fn partial_cmp(&self, other: &Self) -> Option<Ordering> {
        Some(self.cmp(other))
    }
}

impl PartialEq for MinecraftVersion {
    fn eq(&self, other: &Self) -> bool {
        self.compare_parts(other) == Ordering::Equal
    }
}

#[cfg(test)]
impl<'a> arbitrary::Arbitrary<'a> for MinecraftVersion {
    fn arbitrary(u: &mut arbitrary::Unstructured<'a>) -> arbitrary::Result<Self> {
        let segments = u.int_in_range(2..=3)?;
        let mut parts = Vec::with_capacity(segments);
        parts.push(u.int_in_range(1..=26)?);
        for _ in 1..segments {
            parts.push(u.int_in_range(0..=30)?);
        }
        let text = parts
            .iter()
            .map(u32::to_string)
            .collect::<Vec<_>>()
            .join(".");
        Ok(Self { parts, text })
    }
}
