use crate::jar_build::Parallelism;

pub(crate) fn normalize_parallel_args<I, S>(args: I) -> Vec<String>
where
    I: IntoIterator<Item = S>,
    S: Into<String>,
{
    let args = args.into_iter().map(Into::into).collect::<Vec<_>>();
    let mut normalized = Vec::with_capacity(args.len() + 1);
    let mut index = 0;

    while index < args.len() {
        let arg = &args[index];
        normalized.push(arg.clone());
        if arg == "--parallel" {
            let next_is_value = args
                .get(index + 1)
                .is_some_and(|next| !next.starts_with('-'));
            if !next_is_value {
                normalized.push(Parallelism::DEFAULT_LIMIT.to_string());
            }
        }
        index += 1;
    }

    normalized
}

#[cfg(test)]
mod tests {
    use super::normalize_parallel_args;

    #[test]
    fn inserts_default_for_bare_parallel() {
        assert_eq!(
            normalize_parallel_args(["run", "client", "--parallel", "--dry-run"]),
            vec!["run", "client", "--parallel", "10", "--dry-run"]
        );
    }

    #[test]
    fn preserves_explicit_parallel_value() {
        assert_eq!(
            normalize_parallel_args(["jar", "build", "--parallel", "4"]),
            vec!["jar", "build", "--parallel", "4"]
        );
    }

    #[test]
    fn leaves_parallel_equals_form_untouched() {
        assert_eq!(
            normalize_parallel_args(["jar", "build", "--parallel=4"]),
            vec!["jar", "build", "--parallel=4"]
        );
    }
}
