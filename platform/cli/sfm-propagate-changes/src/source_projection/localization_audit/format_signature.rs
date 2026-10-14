use eyre::Result;
use eyre::ensure;
use facet::Facet;
use std::collections::BTreeMap;

/// Java Formatter argument identity. Indexed arguments may be reordered;
/// ordinary arguments have their own counter; `<` reuses the previous one.
#[derive(Debug, Default, PartialEq, Eq, Facet)]
pub struct FormatSignature(pub BTreeMap<usize, Vec<String>>);

impl FormatSignature {
    /// # Errors
    /// Rejects incomplete or unsupported conversions and invalid argument indices.
    pub fn parse(text: &str) -> Result<Self> {
        let mut signature = Self::default();
        let bytes = text.as_bytes();
        let mut i = 0;
        let mut ordinary = 0;
        let mut previous = None;
        while i < bytes.len() {
            if bytes[i] != b'%' {
                i += 1;
                continue;
            }
            i += 1;
            ensure!(i < bytes.len(), "trailing percent");
            let start = i;
            while i < bytes.len() && bytes[i].is_ascii_digit() {
                i += 1;
            }
            let explicit = if i < bytes.len() && bytes[i] == b'$' {
                let index: usize = text[start..i].parse()?;
                ensure!(index > 0, "Java argument indices start at one");
                i += 1;
                Some(index)
            } else {
                i = start;
                None
            };
            let mut reuse = false;
            while i < bytes.len() && b"-#+ 0,(<".contains(&bytes[i]) {
                reuse |= bytes[i] == b'<';
                i += 1;
            }
            while i < bytes.len() && bytes[i].is_ascii_digit() {
                i += 1;
            }
            if i < bytes.len() && bytes[i] == b'.' {
                i += 1;
                ensure!(
                    i < bytes.len() && bytes[i].is_ascii_digit(),
                    "missing precision"
                );
                while i < bytes.len() && bytes[i].is_ascii_digit() {
                    i += 1;
                }
            }
            ensure!(i < bytes.len(), "missing conversion");
            let date = matches!(bytes[i], b't' | b'T');
            if date {
                i += 1;
                ensure!(i < bytes.len(), "missing date conversion");
            }
            let conversion = bytes[i] as char;
            i += 1;
            if !date && matches!(conversion, '%' | 'n') {
                ensure!(
                    explicit.is_none() && !reuse,
                    "nonargument conversion has argument index"
                );
                continue;
            }
            let kind = if date {
                ensure!(
                    "HIklMSLNpzZsQBbhAaCYyjmdeRTrDFc".contains(conversion),
                    "unknown date conversion"
                );
                "datetime"
            } else {
                match conversion {
                    's' | 'S' | 'b' | 'B' | 'h' | 'H' => "general",
                    'c' | 'C' => "character",
                    'd' | 'o' | 'x' | 'X' => "integer",
                    'e' | 'E' | 'f' | 'g' | 'G' => "float",
                    'a' | 'A' => "float_hex",
                    _ => eyre::bail!("unsupported conversion %{conversion}"),
                }
            };
            ensure!(
                !(reuse && explicit.is_some()),
                "both relative and explicit index"
            );
            let index = if reuse {
                previous.ok_or_else(|| eyre::eyre!("relative index without preceding argument"))?
            } else if let Some(index) = explicit {
                index
            } else {
                ordinary += 1;
                ordinary
            };
            previous = Some(index);
            signature.0.entry(index).or_default().push(kind.into());
        }
        for kinds in signature.0.values_mut() {
            kinds.sort();
            kinds.dedup();
        }
        Ok(signature)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn indexed_reordering_mixed_counters_and_literal_percent() {
        assert_eq!(
            FormatSignature::parse("%s: %d %%").unwrap(),
            FormatSignature::parse("%2$d / %1$s %%").unwrap()
        );
        assert_eq!(FormatSignature::parse("%2$s %s %<s").unwrap().0.len(), 2);
        assert_ne!(
            FormatSignature::parse("%s %d").unwrap(),
            FormatSignature::parse("%d %s").unwrap()
        );
        assert!(FormatSignature::parse("%<s").is_err());
        assert!(FormatSignature::parse("%0$s").is_err());
        assert!(FormatSignature::parse("%D %F %O").is_err());
        assert!(FormatSignature::parse("50%").is_err());
        assert!(FormatSignature::parse("%% %n").unwrap().0.is_empty());
    }
}
