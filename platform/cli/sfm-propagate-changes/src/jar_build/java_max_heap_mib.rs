/// Validated maximum Java heap, expressed as an argument rather than environment injection.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct JavaMaxHeapMib(u32);

impl JavaMaxHeapMib {
    /// # Errors
    /// Rejects heaps outside the supported 512..=8192 MiB launch budget.
    pub fn new(mib: u32) -> eyre::Result<Self> {
        eyre::ensure!(
            (512..=8192).contains(&mib),
            "--java-max-heap-mib must be between 512 and 8192 MiB"
        );
        Ok(Self(mib))
    }

    #[must_use]
    pub const fn mib(self) -> u32 {
        self.0
    }

    #[must_use]
    pub fn argument(self) -> String {
        format!("-Xmx{}m", self.0)
    }
}

#[cfg(test)]
mod tests {
    use super::JavaMaxHeapMib;
    #[test]
    fn heap_budget_rejects_zero_and_out_of_range_values() {
        for value in [0, 511, 8193, u32::MAX] {
            assert!(JavaMaxHeapMib::new(value).is_err());
        }
        for value in [512, 4096, 8192] {
            assert_eq!(JavaMaxHeapMib::new(value).unwrap().mib(), value);
        }
    }
}
