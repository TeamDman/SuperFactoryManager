//! Repository inventory and actual engine probes. UI rendering is a separate gate.
use super::DEFAULT_SYNTAX_HIGHLIGHT_MAX_SOURCE_BYTES;
use super::DEFAULT_SYNTAX_HIGHLIGHT_MAX_SPANS;
use super::SyntaxHighlightEngine;
use super::SyntaxHighlightEngineLimits;
use super::SyntaxHighlightLimits;
use super::SyntaxHighlightRequest;
use crate::cancellation::CancellationToken;
use facet::Facet;
use std::collections::BTreeMap;
use std::io::Read;
use std::path::Path;
use std::process::Command;

#[derive(Debug, Facet)]
pub struct SyntaxCoverageReport {
    pub schema: String,
    pub repository_root: String,
    pub scope: String,
    pub verification_boundary: String,
    pub extensions: Vec<SyntaxExtensionCoverage>,
}

#[derive(Debug, Facet, Default)]
pub struct SyntaxExtensionCoverage {
    pub extension: String,
    pub files: u64,
    pub bytes: u64,
    pub grammar: Option<String>,
    pub availability: String,
    pub samples: Vec<SyntaxCoverageSample>,
    pub excluded_samples: BTreeMap<String, u64>,
}

#[derive(Debug, Facet)]
pub struct SyntaxCoverageSample {
    pub path: String,
    pub source_sha256: String,
    pub bytes: u64,
    pub outcome: String,
    pub styled_spans: u64,
    pub diagnostic_codes: Vec<String>,
}

/// Filename mapping is tested against the Java source-language route; not icon rules.
#[must_use]
pub fn repository_language(extension: &str) -> Option<&'static str> {
    match extension {
        "java" => Some("java"),
        "rs" => Some("rust"),
        "json" | "json5" => Some("json"),
        "gradle" | "groovy" => Some("groovy"),
        "md" | "markdown" => Some("markdown"),
        "ps1" => Some("powershell"),
        "ts" => Some("typescript"),
        "toml" => Some("toml"),
        _ => None,
    }
}

/// Inventory tracked and unignored untracked files, then probe up to three meaningful
/// files per extension through the production grammar/query engine. No source is exported.
/// # Errors
/// Rejects a non-repository root, failed enumeration, cancellation or engine failure.
pub fn audit_repository(
    root: &Path,
    cancellation: &CancellationToken,
) -> eyre::Result<SyntaxCoverageReport> {
    let root = root.canonicalize()?;
    cancellation.bail_if_cancelled()?;
    let top = Command::new("git")
        .arg("-C")
        .arg(&root)
        .args(["rev-parse", "--show-toplevel"])
        .output()?;
    if !top.status.success()
        || Path::new(String::from_utf8(top.stdout)?.trim()).canonicalize()? != root
    {
        eyre::bail!("syntax audit requires the repository root, not a subdirectory");
    }
    let listing = Command::new("git")
        .arg("-C")
        .arg(&root)
        .args([
            "ls-files",
            "--cached",
            "--others",
            "--exclude-standard",
            "-z",
        ])
        .output()?;
    if !listing.status.success() {
        eyre::bail!("git file enumeration failed");
    }
    let mut paths: Vec<_> = String::from_utf8(listing.stdout)?
        .split('\0')
        .filter(|path| !path.is_empty())
        .map(str::to_owned)
        .collect();
    paths.sort();
    paths.dedup();
    let mut groups: BTreeMap<String, SyntaxExtensionCoverage> = BTreeMap::new();
    let mut engine = SyntaxHighlightEngine::new(SyntaxHighlightEngineLimits::default())?;
    for path in paths {
        cancellation.bail_if_cancelled()?;
        let extension = Path::new(&path)
            .extension()
            .and_then(|value| value.to_str())
            .unwrap_or("(none)")
            .to_ascii_lowercase();
        let group = groups.entry(extension.clone()).or_insert_with(|| SyntaxExtensionCoverage {
            extension: extension.clone(), grammar: repository_language(&extension).map(str::to_owned),
            availability: if repository_language(&extension).is_some() { "pinned-arborium" } else { "no-arborium-route; binary/native/plain/unsupported require separate classification" }.into(),
            ..SyntaxExtensionCoverage::default()
        });
        group.files += 1;
        inspect_file(&root, &path, group, &mut engine, cancellation)?;
    }
    let mut extensions: Vec<_> = groups.into_values().collect();
    extensions.sort_by(|a, b| {
        b.bytes
            .cmp(&a.bytes)
            .then_with(|| a.extension.cmp(&b.extension))
    });
    Ok(SyntaxCoverageReport {
        schema: "sfm.syntax-repository-coverage/1".into(),
        repository_root: root.display().to_string(),
        scope: "git tracked + unignored untracked; existing generated files included in denominator; ignored files excluded".into(),
        verification_boundary: "production engine spans only; not proof of Java routing, worker framing or rendered ordinary/review/diff views; at most three nonempty UTF-8 samples per extension, lexical path order".into(),
        extensions,
    })
}

fn inspect_file(
    root: &Path,
    relative: &str,
    group: &mut SyntaxExtensionCoverage,
    engine: &mut SyntaxHighlightEngine,
    cancellation: &CancellationToken,
) -> eyre::Result<()> {
    let path = root.join(relative);
    let Ok(metadata) = path.symlink_metadata() else {
        exclude(group, "missing-or-unreadable");
        return Ok(());
    };
    if !metadata.is_file() || metadata.is_symlink() {
        exclude(group, "not-regular-file");
        return Ok(());
    }
    group.bytes += metadata.len();
    let Some(language) = group.grammar.clone() else {
        exclude(group, "no-arborium-route");
        return Ok(());
    };
    if group.samples.len() >= 3 {
        exclude(group, "sample-budget; not-verified");
        return Ok(());
    }
    if metadata.len() > DEFAULT_SYNTAX_HIGHLIGHT_MAX_SOURCE_BYTES as u64 {
        exclude(group, "source-size-limit");
        return Ok(());
    }
    if !path.canonicalize()?.starts_with(root) {
        exclude(group, "outside-root");
        return Ok(());
    }
    let Ok(file) = std::fs::File::open(path) else {
        exclude(group, "unreadable");
        return Ok(());
    };
    let mut source = String::new();
    if file
        .take(DEFAULT_SYNTAX_HIGHLIGHT_MAX_SOURCE_BYTES as u64 + 1)
        .read_to_string(&mut source)
        .is_err()
    {
        exclude(group, "not-utf8-or-unreadable");
        return Ok(());
    }
    if source.len() > DEFAULT_SYNTAX_HIGHLIGHT_MAX_SOURCE_BYTES {
        exclude(group, "source-grew-beyond-size-limit");
        return Ok(());
    }
    if source.trim().len() < 32 {
        exclude(group, "too-small-for-representative-sample");
        return Ok(());
    }
    let request = SyntaxHighlightRequest::new(
        1,
        1,
        relative,
        1,
        language,
        source,
        DEFAULT_SYNTAX_HIGHLIGHT_MAX_SPANS,
    );
    let result = engine.highlight(&request, cancellation)?;
    result.validate_against(&request, SyntaxHighlightLimits::default())?;
    group.samples.push(SyntaxCoverageSample {
        path: relative.into(),
        source_sha256: request.source_sha256.clone(),
        bytes: request.source.len() as u64,
        outcome: format!("{:?}", result.outcome),
        styled_spans: result
            .spans
            .iter()
            .filter(|span| !span.chat_formatting.is_empty())
            .count() as u64,
        diagnostic_codes: result
            .diagnostics
            .iter()
            .map(|value| value.code.clone())
            .collect(),
    });
    Ok(())
}

fn exclude(group: &mut SyntaxExtensionCoverage, reason: &str) {
    *group.excluded_samples.entry(reason.into()).or_default() += 1;
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn repository_audit_measures_real_spans_without_counting_unknown_or_empty_as_covered() {
        let temp = tempfile::tempdir().unwrap();
        assert!(
            Command::new("git")
                .arg("init")
                .arg(temp.path())
                .output()
                .unwrap()
                .status
                .success()
        );
        std::fs::write(
            temp.path().join("example.rs"),
            "pub fn example() { let number = 42; println!(\"{number}\"); }\n",
        )
        .unwrap();
        std::fs::write(temp.path().join("empty.rs"), "").unwrap();
        std::fs::write(temp.path().join("unknown.xyz"), "unregistered").unwrap();
        std::fs::write(temp.path().join(".gitignore"), "ignored.rs\n").unwrap();
        std::fs::write(temp.path().join("ignored.rs"), "fn ignored() {}").unwrap();
        let report = audit_repository(temp.path(), &CancellationToken::new()).unwrap();
        let rust = report
            .extensions
            .iter()
            .find(|row| row.extension == "rs")
            .unwrap();
        assert_eq!(rust.files, 2);
        assert_eq!(rust.samples.len(), 1);
        assert_eq!(rust.samples[0].outcome, "Highlighted");
        assert!(rust.samples[0].styled_spans > 3);
        assert_eq!(
            rust.excluded_samples["too-small-for-representative-sample"],
            1
        );
        let unknown = report
            .extensions
            .iter()
            .find(|row| row.extension == "xyz")
            .unwrap();
        assert_eq!(unknown.bytes, 12);
        assert!(unknown.grammar.is_none());
        assert!(unknown.samples.is_empty());
        std::fs::create_dir(temp.path().join("child")).unwrap();
        assert!(audit_repository(&temp.path().join("child"), &CancellationToken::new()).is_err());
    }
}
