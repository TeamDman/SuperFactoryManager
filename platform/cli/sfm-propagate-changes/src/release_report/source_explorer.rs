//! One shared JAR-first inspector, with repository projections kept as a distinct input.
use super::document::ReleaseReportDocument;
use super::render::escape;
use std::collections::BTreeMap;
use std::fmt::Write as _;

fn fragment<'a>(html: &'a str, id: &str) -> &'a str {
    let marker = format!("<section id=\"{id}\"");
    let Some(start) = html.find(&marker) else {
        return "";
    };
    let rest = &html[start..];
    let end = rest[marker.len()..]
        .find("<section id=\"")
        .map_or(rest.len(), |n| n + marker.len());
    &rest[..end]
}

pub(super) fn tree(rows: &[(String, String)]) -> String {
    let mut files = String::new();
    let mut folders: BTreeMap<String, Vec<(String, String)>> = BTreeMap::new();
    for (path, id) in rows {
        let (old_path, destination) = path
            .split_once(" → ")
            .map_or((path.as_str(), None), |(old, new)| (old, Some(new)));
        if let Some((directory, rest)) = old_path.split_once('/') {
            let rest = destination.map_or_else(|| rest.to_owned(), |new| format!("{rest} → {new}"));
            folders
                .entry(directory.to_owned())
                .or_default()
                .push((rest, id.clone()));
        } else {
            let _ = write!(
                files,
                "<li><a class=\"source-link\" href=\"#{}\">{}</a></li>",
                escape(id),
                escape(path)
            );
        }
    }
    for (directory, rows) in folders {
        let _ = write!(
            files,
            "<li><details open><summary>{}</summary>{}</details></li>",
            escape(&directory),
            tree(&rows)
        );
    }
    format!("<ul>{files}</ul>")
}

pub(super) fn render(
    doc: &ReleaseReportDocument,
    diffs: &str,
    explorer: &str,
    verified_correlations: &str,
) -> (String, String) {
    let mut versions: BTreeMap<String, Vec<(String, String)>> = BTreeMap::new();
    let mut supplemental = Vec::new();
    for diff in &doc.diffs {
        if diff.evidence_only {
            continue;
        }
        if let Some((version, path)) = diff.title.split_once(" · ")
            && doc
                .jars
                .iter()
                .any(|j| j.id[4..].replace('-', ".") == version)
        {
            if diff.before != diff.after || path.contains('→') {
                versions
                    .entry(version.to_owned())
                    .or_default()
                    .push((path.to_owned(), diff.id.clone()));
            }
            continue;
        }
        supplemental.push((diff.title.clone(), diff.id.clone()));
    }
    let mut options = String::new();
    let mut trees = String::new();
    let mut views = String::new();
    for (version, rows) in &versions {
        let _ = write!(
            options,
            "<option{}>{}</option>",
            if version == "1.21.1" { " selected" } else { "" },
            escape(version)
        );
        let _ = write!(
            trees,
            "<div data-version=\"{}\">{}</div>",
            escape(version),
            tree(rows)
        );
        for (_, id) in rows {
            views.push_str(fragment(diffs, id));
        }
    }
    let mut review_options = String::new();
    for jar in &doc.jars {
        let version = jar.id[4..].replace('-', ".");
        let _ = write!(
            review_options,
            "<option value=\"jar-{version}\">Delivered JAR · Minecraft {version}</option><option value=\"repo-{version}\">Repository source · Minecraft {version}</option>"
        );
    }
    // The caller supplies only successful correlation_render output, after its
    // exact source-insertion and class-profile validation. Metadata is not a target.
    let causal_example =
        if verified_correlations.contains("<section id=\"resource-quantity-cause\">") {
            "<a href=\"#resource-quantity-cause\">Verified source-to-line-table example</a> · "
        } else {
            ""
        };
    let changes = format!(
        "<section id=\"release-changes\"><h2>Inspect the JAR and source changes</h2><p class=\"inspector-intro\">Start with the delivered JARs to inspect packaged files. Repository source is a separate captured comparison. Select a file, then use its source/class links to move between the two.</p><label class=\"review-input-control\">Compare <select id=\"review-input\">{review_options}</select></label><div class=\"source-workbench release-workbench\"><aside class=\"source-sidebar\"><label>Repository projection <select id=\"source-version\">{options}</select></label>{trees}</aside><div class=\"source-preview\">{views}</div></div><details id=\"class-explorer-panel\" class=\"inspector-method\" open><summary>JAR comparison details</summary>{explorer}</details><details class=\"inspector-method\"><summary>How to interpret these comparisons</summary><p>A Java source file can produce multiple classes. Source/class navigation uses captured compiler metadata and paths; it does not establish that current repository bytes produced a frozen JAR. Equal instruction text is narrower than semantic equivalence. Debug-table differences and unexplained raw-byte changes remain separately classified.</p><p>Moves retain old and new paths. Raw archive members remain downloadable. {causal_example}<a href=\"#technical-appendix\">Complete evidence</a>.</p></details></section>"
    );
    let sources = supplemental
        .iter()
        .map(|(_, id)| fragment(diffs, id))
        .collect::<String>();
    let supporting = format!(
        "<div class=\"source-workbench\"><aside class=\"source-sidebar\">{}</aside><div class=\"source-preview\">{sources}</div></div>",
        tree(&supplemental)
    );
    (changes, supporting)
}

#[cfg(test)]
mod tests {
    #[test]
    fn nested_paths_escape_and_remain_expanded() {
        let html = super::tree(&[("src/java/A<B.java".to_owned(), "safe".to_owned())]);
        assert_eq!(html.matches("<details open>").count(), 2);
        assert!(html.contains("A&lt;B.java"));
        assert!(html.contains("href=\"#safe\""));
    }
}
