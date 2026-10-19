//! Embed the approved unmodified font and its complete licence for standalone reports.
use base64::Engine as _;
pub(super) fn embed(html: &str) -> String {
    let font = base64::engine::general_purpose::STANDARD
        .encode(include_bytes!("assets/paper-mono/PaperMono-variable.woff2"));
    let css = format!(
        "<style>@font-face{{font-family:'Paper Mono';font-style:normal;font-weight:100 800;font-display:swap;src:url(data:font/woff2;base64,{font}) format('woff2');}}{}</style>",
        include_str!("templates/typography.css")
    );
    let notice = format!(
        "<details class=\"font-license\"><summary>Paper Mono font · SIL Open Font License 1.1</summary><pre>{}</pre></details>",
        super::render::escape(include_str!("assets/paper-mono/OFL.txt"))
    );
    html.replacen("</head>", &format!("{css}</head>"), 1)
        .replacen("</body>", &format!("{notice}</body>"), 1)
}
#[cfg(test)]
mod tests {
    #[test]
    fn font_and_full_notice_are_local_and_prose_is_not_restyled() {
        let html = super::embed("<html><head></head><body>Prose</body></html>");
        assert!(html.contains("data:font/woff2;base64,"));
        assert!(html.contains("Copyright 2025 The Paper-Mono.Git Project Authors"));
        assert!(html.contains("SIL OPEN FONT LICENSE Version 1.1"));
        assert!(html.contains("DISCLAIMER"));
        assert!(!html.contains("localhost"));
        assert!(!html.contains("body{font"));
    }
}
