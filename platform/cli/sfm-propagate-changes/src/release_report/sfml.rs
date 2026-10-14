//! Display formatting and token colors grounded in SFM's SFML.g4 lexer.
//! This is a presentation lexer, not a parser or program rewrite.
use super::render::escape;

pub(super) const KEYWORDS: &[&str] = &[
    // SFML.g4 case-insensitive letter tokens; generated from the repository grammar.
    "AND",
    "AS",
    "BACK",
    "BE",
    "BLOCK",
    "BOTTOM",
    "BROADCAST",
    "BTW",
    "BY",
    "CAPABILITY",
    "CHANNEL",
    "CLIENT",
    "CREATE",
    "DO",
    "EACH",
    "EAST",
    "ELSE",
    "EMPTY",
    "END",
    "EQ",
    "EVERY",
    "EXCEPT",
    "FALSE",
    "FIELD",
    "FOR",
    "FORGET",
    "FRAME",
    "FROM",
    "FRONT",
    "GE",
    "GT",
    "GUID",
    "HAS",
    "IF",
    "IMAGE",
    "IN",
    "INPUT",
    "INVOKE",
    "JSON",
    "LABEL",
    "LE",
    "LEFT",
    "LET",
    "LIKE",
    "LONE",
    "LT",
    "MOD",
    "NAME",
    "NEW",
    "NORTH",
    "NOT",
    "NULL",
    "OBJECT",
    "OF",
    "OFFSET",
    "ONE",
    "OR",
    "OUTPUT",
    "OVERALL",
    "PLAYER",
    "PULSE",
    "REDSTONE",
    "RENDER",
    "RETAIN",
    "RIGHT",
    "ROBIN",
    "ROUND",
    "SECOND",
    "SECONDS",
    "SERVER",
    "SIDE",
    "SLOT",
    "SLOTS",
    "SOME",
    "SOUTH",
    "STRING",
    "TAG",
    "THEN",
    "TICK",
    "TICKS",
    "TO",
    "TOP",
    "TRUE",
    "WEST",
    "WHERE",
    "WITH",
    "WITHOUT",
];

pub(super) fn applies(language: &str, id: &str) -> bool {
    matches!(language.to_ascii_lowercase().as_str(), "sfm" | "sfml")
        || id.ends_with("-sfml")
        || std::path::Path::new(id)
            .extension()
            .is_some_and(|ext| ext.eq_ignore_ascii_case("sfml"))
}

fn token(input: &str) -> (&str, &str, &str) {
    let Some(first) = input.chars().next() else {
        return ("", "", "");
    };
    let length;
    let kind;
    if input.starts_with("--") {
        length = input.find('\n').unwrap_or(input.len());
        kind = "comment";
    } else if first == '"' {
        let mut escaped = false;
        let mut end = input.len();
        for (offset, ch) in input.char_indices().skip(1) {
            if !escaped && ch == '"' {
                end = offset + ch.len_utf8();
                break;
            }
            escaped = !escaped && ch == '\\';
        }
        length = end;
        kind = "string";
    } else if first.is_whitespace() {
        length = input
            .find(|c: char| !c.is_whitespace())
            .unwrap_or(input.len());
        kind = "";
    } else if first.is_ascii_digit() {
        length = input
            .find(|c: char| !c.is_ascii_digit())
            .unwrap_or(input.len());
        kind = "number";
    } else if first.is_ascii_alphabetic() || first == '_' || first == '*' {
        length = input
            .find(|c: char| !(c.is_ascii_alphanumeric() || matches!(c, '_' | '*' | ':' | '.')))
            .unwrap_or(input.len());
        let word = &input[..length];
        kind = if word.contains(':') {
            "resource"
        } else if KEYWORDS.contains(&word.to_ascii_uppercase().as_str()) {
            "keyword"
        } else {
            "label"
        };
    } else {
        length = first.len_utf8();
        kind = "";
    }
    (kind, &input[..length], &input[length..])
}

fn quote_state(line: &str, mut quoted: bool) -> bool {
    let mut chars = line.chars().peekable();
    while let Some(ch) = chars.next() {
        if !quoted && ch == '-' && chars.peek() == Some(&'-') {
            break;
        }
        if quoted && ch == '\\' {
            chars.next();
        } else if ch == '"' {
            quoted = !quoted;
        }
    }
    quoted
}

/// Change only leading whitespace outside string literals; retain tokens/comments.
pub(super) fn format(source: &str) -> String {
    let mut result = String::new();
    let mut depth: usize = 0;
    let mut quoted = false;
    for line in source.split_inclusive('\n') {
        let newline = if line.ends_with('\n') { "\n" } else { "" };
        let body = line.trim_end_matches(['\r', '\n']);
        let text = body.trim_start_matches([' ', '\t']);
        let mut rest = text;
        let mut words = Vec::new();
        if !quoted {
            while !rest.is_empty() {
                let (kind, value, next) = token(rest);
                if kind == "keyword" {
                    words.push(value.to_ascii_uppercase());
                }
                rest = next;
            }
        }
        let first = words.first().map(String::as_str);
        if matches!(first, Some("END" | "ELSE")) {
            depth = depth.saturating_sub(1);
        }
        if quoted {
            result.push_str(body);
        } else if !text.is_empty() {
            result.push_str(&"    ".repeat(depth));
            result.push_str(text);
        }
        result.push_str(newline);
        quoted = quote_state(body, quoted);
        let opens = (first == Some("EVERY") && words.last().is_some_and(|w| w == "DO"))
            || (matches!(first, Some("IF" | "ELSE")) && words.last().is_some_and(|w| w == "THEN"))
            || (first == Some("ELSE") && words.len() == 1);
        if opens {
            depth = (depth + 1).min(32);
        }
    }
    result
}

pub(super) fn highlight(source: &str) -> String {
    let mut html = String::new();
    let mut active = "";
    let mut rest = source;
    while !rest.is_empty() {
        let (kind, value, next) = token(rest);
        // Keep consecutive comments in one span; whitespace has no visible color.
        let desired = if value.chars().all(char::is_whitespace) {
            active
        } else {
            kind
        };
        if desired != active {
            if !active.is_empty() {
                html.push_str("</span>");
            }
            if !desired.is_empty() {
                html.push_str("<span class=\"sfml-");
                html.push_str(desired);
                html.push_str("\">");
            }
            active = desired;
        }
        html.push_str(&escape(value));
        rest = next;
    }
    if !active.is_empty() {
        html.push_str("</span>");
    }
    html
}

pub(super) fn render(source: &str) -> String {
    highlight(&format(source))
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn nested_body_and_else_alignment_preserve_strings_and_comments() {
        let input = "EVERY TICK DO\nIF x GT 0 THEN\nOUTPUT fe:: TO \"END THEN\" -- DO END\nELSE\nINPUT fe:: FROM PowerSource\nEND\nEND\n";
        assert_eq!(
            format(input),
            "EVERY TICK DO\n    IF x GT 0 THEN\n        OUTPUT fe:: TO \"END THEN\" -- DO END\n    ELSE\n        INPUT fe:: FROM PowerSource\n    END\nEND\n"
        );
    }
    #[test]
    fn literal_contents_and_hostile_text_are_not_rewritten_or_executed() {
        let source = "NAME \"multi\n  END\nline\"\n-- <script>bad</script>\nEVERY TICK DO\nOUTPUT fe:: TO Healthy\nEND";
        assert!(format(source).contains("\"multi\n  END\nline\""));
        let html = render(source);
        assert!(!html.contains("<script>"));
        assert!(html.contains("&lt;script&gt;"));
        assert!(html.contains("class=\"sfml-resource\">fe::"));
        assert!(html.contains("class=\"sfml-label\">Healthy"));
    }
    #[test]
    fn formatting_is_idempotent_and_resource_names_are_not_keywords() {
        let source = "EVERY TICK DO\nINPUT fe:: FROM PowerSource TOP SIDE\nOUTPUT fe:: TO Dropper, Healthy\nEND\n";
        assert_eq!(format(&format(source)), format(source));
        assert!(render(source).contains("\n    INPUT "));
        assert!(!highlight("minecraft:stone").contains("sfml-keyword"));
    }
}
