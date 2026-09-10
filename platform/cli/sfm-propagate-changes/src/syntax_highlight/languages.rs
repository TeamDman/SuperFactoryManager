//! Exact-pinned source grammar registry, independent of filenames and UI icons.
use tree_sitter_patched_arborium::Language;

pub const SUPPORTED: &[&str] = &[
    "java",
    "rust",
    "json",
    "groovy",
    "markdown",
    "powershell",
    "typescript",
    "toml",
];

pub fn grammar(id: &str) -> Option<(Language, &'static str)> {
    Some(match id {
        "java" => (
            arborium_java::language().into(),
            arborium_java::HIGHLIGHTS_QUERY,
        ),
        "rust" => (
            arborium_rust::language().into(),
            arborium_rust::HIGHLIGHTS_QUERY,
        ),
        "json" => (
            arborium_json::language().into(),
            arborium_json::HIGHLIGHTS_QUERY,
        ),
        "groovy" => (
            arborium_groovy::language().into(),
            arborium_groovy::HIGHLIGHTS_QUERY,
        ),
        "markdown" => (
            arborium_markdown::language().into(),
            arborium_markdown::HIGHLIGHTS_QUERY,
        ),
        "powershell" => (
            arborium_powershell::language().into(),
            arborium_powershell::HIGHLIGHTS_QUERY,
        ),
        "typescript" => (
            arborium_typescript::language().into(),
            &arborium_typescript::HIGHLIGHTS_QUERY,
        ),
        "toml" => (
            arborium_toml::language().into(),
            arborium_toml::HIGHLIGHTS_QUERY,
        ),
        _ => return None,
    })
}
