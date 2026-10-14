//! Projection text changes are the primary reader view; bytecode stays separate.
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

pub(super) fn render(doc: &ReleaseReportDocument, diffs: &str) -> (String, String) {
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
    let changes = format!(
        "<section id=\"release-changes\"><h2>What changed in this release?</h2><p>Actual 4.34.0 → 4.35.0 projection text comparisons. Select a Minecraft version and a changed source or resource. Resource moves show both archive paths; an unchanged recipe body is shown as unchanged. Shared fixtures are separate below.</p><div class=\"source-workbench release-workbench\"><aside class=\"source-sidebar\"><label>Minecraft version <select id=\"source-version\">{options}</select></label>{trees}</aside><div class=\"source-preview\">{views}</div></div><p class=\"small-note\">The class explorer is a separate verification view. A Java source file can produce its outer class and multiple inner classes; the shared basename alone does not establish that association. <a href=\"#resource-quantity-cause\">ResourceQuantity: verified source-to-line-table mapping</a> · <a href=\"#class-explorer-panel\">Class verification</a></p></section>"
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
