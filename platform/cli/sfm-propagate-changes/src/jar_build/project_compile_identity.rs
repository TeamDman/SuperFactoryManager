//! Stable identity for the single held `NeoForm` compiler-classpath role.
//! Actual javac paths/order/flags stay untouched; content is hashed separately.
use eyre::Result;
use eyre::ensure;
use std::path::Path;
use std::path::PathBuf;

const LOGICAL_CLASSES: &str = "__sfm_bound_neoform_classes_v1__";

pub(super) fn is_bound(path: &Path, held: &Path) -> bool {
    dunce::simplified(path) == dunce::simplified(held)
}

pub(super) fn arguments(
    args: &[String],
    classpath: &[PathBuf],
    held: &Path,
) -> Result<Vec<String>> {
    let separator = if cfg!(windows) { ";" } else { ":" };
    let physical = classpath
        .iter()
        .map(|path| path.display().to_string())
        .collect::<Vec<_>>()
        .join(separator);
    let mut found = false;
    let logical = classpath
        .iter()
        .map(|path| {
            if is_bound(path, held) {
                found = true;
                LOGICAL_CLASSES.to_owned()
            } else {
                path.display().to_string()
            }
        })
        .collect::<Vec<_>>()
        .join(separator);
    ensure!(
        found,
        "Held NeoForm classpath is absent from javac arguments"
    );
    let positions = args
        .iter()
        .enumerate()
        .filter(|(_, argument)| argument.as_str() == "-classpath")
        .map(|(index, _)| index)
        .collect::<Vec<_>>();
    ensure!(positions.len() == 1, "Ambiguous javac classpath option");
    let index = positions[0] + 1;
    ensure!(
        args.get(index) == Some(&physical),
        "Javac classpath differs from its bound inputs"
    );
    let mut canonical = args.to_vec();
    canonical[index] = logical;
    Ok(canonical)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn args(classpath: &[PathBuf]) -> Vec<String> {
        vec![
            "-g".to_owned(),
            "-classpath".to_owned(),
            classpath
                .iter()
                .map(|path| path.display().to_string())
                .collect::<Vec<_>>()
                .join(if cfg!(windows) { ";" } else { ":" }),
            "-proc:none".to_owned(),
        ]
    }

    #[test]
    fn compiler_identity_excludes_only_the_bound_invocation_path() -> Result<()> {
        let a = vec![
            PathBuf::from("first/held.jar"),
            PathBuf::from("other/stable.jar"),
        ];
        let b = vec![
            PathBuf::from("second/held.jar"),
            PathBuf::from("other/stable.jar"),
        ];
        let actual = args(&a);
        let canonical = arguments(&actual, &a, &a[0])?;
        assert_eq!(canonical, arguments(&args(&b), &b, &b[0])?);
        assert_eq!(
            actual,
            args(&a),
            "Fingerprinting must not mutate actual javac argv"
        );
        let mut changed = b.clone();
        changed[1] = "different/ordinary.jar".into();
        assert_ne!(
            canonical,
            arguments(&args(&changed), &changed, &changed[0])?
        );
        let mut reversed = a.clone();
        reversed.reverse();
        assert_ne!(canonical, arguments(&args(&reversed), &reversed, &a[0])?);
        let mut flags = actual.clone();
        flags[0] = "-g:none".to_owned();
        assert_ne!(canonical, arguments(&flags, &a, &a[0])?);
        Ok(())
    }

    #[test]
    fn compiler_identity_refuses_missing_or_ambiguous_binding() {
        let classpath = vec![PathBuf::from("held.jar")];
        assert!(arguments(&args(&classpath), &classpath, Path::new("foreign.jar")).is_err());
        let mut duplicate = args(&classpath);
        duplicate.extend(["-classpath".to_owned(), "other.jar".to_owned()]);
        assert!(arguments(&duplicate, &classpath, &classpath[0]).is_err());
        assert!(arguments(&["-classpath".to_owned()], &classpath, &classpath[0]).is_err());
    }
}
