use blake3::Hasher as Blake3Hasher;
use eyre::Context;
use facet::Facet;
use sha1::Digest;
use sha1::Sha1 as Sha1Hasher;
use sha1::digest::Update;
use std::fmt::Write as _;
use std::path::Path;
use tracing::debug_span;
use tracing::instrument;

#[derive(Copy, Clone, Debug, Facet, PartialEq, Eq, Hash)]
#[repr(u8)]
pub enum ContentHashAlgorithm {
    Sha1,
    Blake3,
}
impl core::fmt::Display for ContentHashAlgorithm {
    fn fmt(&self, f: &mut core::fmt::Formatter<'_>) -> core::fmt::Result {
        match self {
            ContentHashAlgorithm::Sha1 => write!(f, "sha1"),
            ContentHashAlgorithm::Blake3 => write!(f, "blake3"),
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq, Hash, Copy, Facet)]
#[facet(proxy = String)]
pub struct ContentHash {
    pub value: [u8; 20],
    pub algorithm: ContentHashAlgorithm,
}

impl core::fmt::Display for ContentHash {
    fn fmt(&self, f: &mut core::fmt::Formatter<'_>) -> core::fmt::Result {
        write!(f, "{}:", self.algorithm)?;
        for byte in &self.value {
            write!(f, "{byte:02x}")?;
        }
        Ok(())
    }
}

impl ContentHash {
    #[must_use]
    pub fn hex(&self) -> String {
        let mut output = String::with_capacity(self.value.len() * 2);
        for byte in &self.value {
            write!(&mut output, "{byte:02x}").expect("writing to String cannot fail");
        }
        output
    }

    #[must_use]
    pub fn short_hex(&self, chars: usize) -> String {
        self.hex().chars().take(chars).collect()
    }

    /// Parses a content hash with an optional algorithm prefix.
    ///
    /// # Errors
    ///
    /// Returns an error when the algorithm prefix is unsupported or the hash body is not valid hex.
    pub fn parse(input: &str) -> Result<Self, String> {
        let (algorithm, value) = match input.split_once(':') {
            Some(("sha1", value)) => (ContentHashAlgorithm::Sha1, value),
            Some(("blake3", value)) => (ContentHashAlgorithm::Blake3, value),
            Some((algorithm, _)) => {
                return Err(format!("Unsupported content hash algorithm: {algorithm}"));
            }
            None => (ContentHashAlgorithm::Sha1, input),
        };
        Self::parse_hex(value, algorithm)
    }

    /// Parses a raw hexadecimal content hash for the supplied algorithm.
    ///
    /// # Errors
    ///
    /// Returns an error when the input is not exactly 20 bytes of hexadecimal text.
    pub fn parse_hex(input: &str, algorithm: ContentHashAlgorithm) -> Result<Self, String> {
        if input.len() != 40 {
            return Err(format!(
                "Expected 40 hex characters for {algorithm} content hash, got {}",
                input.len()
            ));
        }

        let mut value = [0u8; 20];
        for (index, chunk) in input.as_bytes().chunks_exact(2).enumerate() {
            let text = std::str::from_utf8(chunk)
                .map_err(|_error| format!("Content hash contains invalid UTF-8: {input}"))?;
            value[index] = u8::from_str_radix(text, 16)
                .map_err(|_error| format!("Content hash contains invalid hex: {input}"))?;
        }
        Ok(Self { value, algorithm })
    }

    #[instrument(level = "debug", name = "content_hash_from_bytes", skip_all)]
    pub fn from_bytes(bytes: &[u8], algorithm: ContentHashAlgorithm) -> Self {
        let value = match algorithm {
            ContentHashAlgorithm::Sha1 => {
                let mut hasher = Sha1Hasher::new();
                Update::update(&mut hasher, bytes);
                let mut array = [0u8; 20];
                let digest = Digest::finalize(hasher);
                array.copy_from_slice(&digest);
                array
            }
            ContentHashAlgorithm::Blake3 => {
                let mut hasher = Blake3Hasher::new();
                hasher.update(bytes);
                let mut result = hasher.finalize_xof();
                let mut array = [0u8; 20];
                result.fill(&mut array);
                array
            }
        };
        Self { value, algorithm }
    }

    /// Hashes a file with the supplied algorithm.
    ///
    /// # Errors
    ///
    /// Returns an error when the file cannot be read.
    #[instrument(level = "debug", name = "content_hash_from_path", skip_all, fields(path = %path.as_ref().display()))]
    pub fn from_path(
        path: impl AsRef<Path>,
        algorithm: ContentHashAlgorithm,
    ) -> eyre::Result<Self> {
        let path = path.as_ref();
        let bytes = debug_span!("read_file_for_hash").in_scope(|| {
            std::fs::read(path).wrap_err_with(|| format!("Failed to read {}", path.display()))
        })?;
        Ok(Self::from_bytes(&bytes, algorithm))
    }
}

impl TryFrom<String> for ContentHash {
    type Error = String;

    fn try_from(value: String) -> Result<Self, Self::Error> {
        Self::parse(&value)
    }
}

impl TryFrom<&ContentHash> for String {
    type Error = String;

    fn try_from(value: &ContentHash) -> Result<Self, Self::Error> {
        Ok(value.to_string())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_sha1() {
        let data = b"hello world";
        let hash = ContentHash::from_bytes(data, ContentHashAlgorithm::Sha1);
        assert_eq!(
            hash.to_string(),
            "sha1:2aae6c35c94fcfb415dbe95f408b9ce91ee846ed"
        );
    }

    #[test]
    fn test_blake3() {
        let data = b"hello world";
        let hash = ContentHash::from_bytes(data, ContentHashAlgorithm::Blake3);
        assert_eq!(
            hash.to_string(),
            "blake3:d74981efa70a0c880b8d8c1985d075dbcbf679b9"
        );
    }
}
