use std::path::Path;

pub trait TerminalTextExt: std::fmt::Display + Sized {
    fn hyperlink(self, uri: &str) -> String {
        format!("\x1b]8;;{uri}\x1b\\{self}\x1b]8;;\x1b\\")
    }
}

impl<T> TerminalTextExt for T where T: std::fmt::Display + Sized {}

#[must_use]
pub fn vscode_file_uri_for_path(path: &Path, line: usize, column: usize) -> String {
    let path = percent_encode_uri_path(&path.to_string_lossy().replace('\\', "/"));
    format!("vscode://file/{path}:{line}:{column}")
}

fn percent_encode_uri_path(path: &str) -> String {
    let mut output = String::with_capacity(path.len());
    for byte in path.bytes() {
        match byte {
            b'A'..=b'Z' | b'a'..=b'z' | b'0'..=b'9' | b'/' | b':' | b'-' | b'_' | b'.' | b'~' => {
                output.push(char::from(byte));
            }
            byte => {
                const HEX: &[u8; 16] = b"0123456789ABCDEF";
                output.push('%');
                output.push(char::from(HEX[usize::from(byte >> 4)]));
                output.push(char::from(HEX[usize::from(byte & 0x0F)]));
            }
        }
    }
    output
}

#[cfg(test)]
mod tests {
    use crate::logging::terminal_hyperlink::TerminalTextExt;
    use crate::logging::terminal_hyperlink::vscode_file_uri_for_path;
    use std::path::Path;

    #[test]
    fn terminal_hyperlink_wraps_label_with_osc8() {
        assert_eq!(
            "rust".hyperlink("vscode://file/D:/repo/src/main.rs:249:1"),
            "\x1b]8;;vscode://file/D:/repo/src/main.rs:249:1\x1b\\rust\x1b]8;;\x1b\\"
        );
    }

    #[test]
    fn vscode_file_uri_targets_the_requested_line_and_column() {
        let uri = vscode_file_uri_for_path(Path::new("D:/repo with spaces/src/main.rs"), 249, 7);

        assert_eq!(
            uri,
            "vscode://file/D:/repo%20with%20spaces/src/main.rs:249:7"
        );
    }
}
