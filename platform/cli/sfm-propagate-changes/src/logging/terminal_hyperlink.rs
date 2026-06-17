pub trait TerminalTextExt: std::fmt::Display + Sized {
    fn hyperlink(self, uri: &str) -> String {
        format!("\x1b]8;;{uri}\x1b\\{self}\x1b]8;;\x1b\\")
    }
}

impl<T> TerminalTextExt for T where T: std::fmt::Display + Sized {}

#[cfg(test)]
mod tests {
    use crate::logging::terminal_hyperlink::TerminalTextExt;

    #[test]
    fn terminal_hyperlink_wraps_label_with_osc8() {
        assert_eq!(
            "rust".hyperlink("vscode://file/D:/repo/src/main.rs:249:1"),
            "\x1b]8;;vscode://file/D:/repo/src/main.rs:249:1\x1b\\rust\x1b]8;;\x1b\\"
        );
    }
}
