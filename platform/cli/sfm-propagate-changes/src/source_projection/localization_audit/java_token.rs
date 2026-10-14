#[derive(Debug, Clone)]
pub struct JavaToken {
    pub text: String,
    pub literal: bool,
    pub line: usize,
}

/// Bounded lexical scan, not a Java AST. Comments are omitted, quoted strings
/// decoded, and unsupported literals retained as unresolved expressions.
#[must_use]
pub fn tokens(source: &str) -> Vec<JavaToken> {
    let bytes = source.as_bytes();
    let mut result = Vec::new();
    let mut i = 0;
    let mut line = 1;
    while i < bytes.len() {
        if bytes[i].is_ascii_whitespace() {
            line += usize::from(bytes[i] == b'\n');
            i += 1;
            continue;
        }
        if bytes[i..].starts_with(b"//") {
            while i < bytes.len() && bytes[i] != b'\n' {
                i += 1;
            }
            continue;
        }
        if bytes[i..].starts_with(b"/*") {
            i += 2;
            while i < bytes.len() && !bytes[i..].starts_with(b"*/") {
                line += usize::from(bytes[i] == b'\n');
                i += 1;
            }
            i = (i + 2).min(bytes.len());
            continue;
        }
        let start = i;
        let token_line = line;
        if matches!(bytes[i], b'"' | b'\'') {
            let quote = bytes[i];
            i += 1;
            while i < bytes.len() {
                if bytes[i] == b'\\' {
                    i = (i + 2).min(bytes.len());
                    continue;
                }
                if bytes[i] == quote {
                    i += 1;
                    break;
                }
                line += usize::from(bytes[i] == b'\n');
                i += 1;
            }
            let raw = &source[start..i];
            let value = if quote == b'"' {
                facet_json::from_str::<String>(raw).ok()
            } else {
                None
            };
            result.push(JavaToken {
                literal: value.is_some(),
                text: value.unwrap_or_else(|| raw.into()),
                line: token_line,
            });
        } else if bytes[i].is_ascii_alphanumeric() || bytes[i] == b'_' {
            i += 1;
            while i < bytes.len() && (bytes[i].is_ascii_alphanumeric() || bytes[i] == b'_') {
                i += 1;
            }
            result.push(JavaToken {
                text: source[start..i].into(),
                literal: false,
                line: token_line,
            });
        } else {
            // ASCII punctuation; a non-ASCII identifier is retained intact.
            let Some(ch) = source[i..].chars().next() else {
                break;
            };
            i += ch.len_utf8();
            result.push(JavaToken {
                text: ch.to_string(),
                literal: false,
                line: token_line,
            });
        }
    }
    result
}
