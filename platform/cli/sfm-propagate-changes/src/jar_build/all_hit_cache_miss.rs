//! Private control flow for expected all-hit cache misses. Errors stay errors.
#[derive(Debug)]
pub(super) struct AllHitCacheMiss {
    reason: &'static str,
}

impl AllHitCacheMiss {
    pub(super) fn new(reason: &'static str) -> Self {
        Self { reason }
    }
}

impl std::fmt::Display for AllHitCacheMiss {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(
            f,
            "all-hit compile requires strict execution: {}",
            self.reason
        )
    }
}

impl std::error::Error for AllHitCacheMiss {}

/// All parallel work has completed before this aggregation. A cache miss must
/// not hide another item's authority/cancellation error and authorize retry.
pub(super) fn collect_results<T>(values: Vec<eyre::Result<T>>) -> eyre::Result<Vec<T>> {
    let mut output = Vec::with_capacity(values.len());
    let mut miss = None;
    let mut failure = None;
    for result in values {
        match result {
            Ok(value) => output.push(value),
            Err(error) if error.downcast_ref::<AllHitCacheMiss>().is_some() => {
                if miss.is_none() {
                    miss = Some(error);
                }
            }
            Err(error) => {
                if failure.is_none() {
                    failure = Some(error);
                }
            }
        }
    }
    if let Some(error) = failure.or(miss) {
        return Err(error);
    }
    Ok(output)
}

/// Preserve the same precedence for completed heterogeneous Rayon joins.
pub(super) fn pair_results<A, B>(
    first: eyre::Result<A>,
    second: eyre::Result<B>,
) -> eyre::Result<(A, B)> {
    match (first, second) {
        (Ok(first), Ok(second)) => Ok((first, second)),
        (Err(first), Err(second))
            if first.downcast_ref::<AllHitCacheMiss>().is_some()
                && second.downcast_ref::<AllHitCacheMiss>().is_none() =>
        {
            Err(second)
        }
        (Err(error), _) | (_, Err(error)) => Err(error),
    }
}
