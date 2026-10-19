use facet::Facet;

/// Ordered process argv, including argv[0]. Masked entries retain their positions.
#[derive(Debug, Facet, PartialEq)]
pub(crate) struct StartupArguments {
    pub event_kind: String,
    pub message: String,
    pub argv: Vec<String>,
    pub redacted_indices: Vec<usize>,
    pub policy: String,
}

macro_rules! named {
    ($levels:expr, $token:expr) => {{
        let name = $token.split('=').next().unwrap_or($token);
        $levels.iter().rev().find_map(|level| {
            if let Some(long) = name.strip_prefix("--") {
                level
                    .args()
                    .get(long.strip_prefix("no-").unwrap_or(long))
                    .map(|(_, arg)| arg)
            } else if name.len() == 2 && name.starts_with('-') {
                level
                    .args()
                    .iter()
                    .find(|(_, arg)| arg.kind().short() == name.chars().nth(1))
                    .map(|(_, arg)| arg)
            } else {
                None
            }
        })
    }};
}

impl StartupArguments {
    pub(crate) fn redact<T: Facet<'static>>(raw: &[String]) -> eyre::Result<Self> {
        let schema = figue::builder::<T>()?.build().schema;
        let mut levels = vec![schema.args()];
        let mut record = Self {
            event_kind: "startup".into(),
            message: "Process started".into(),
            argv: Vec::with_capacity(raw.len()),
            redacted_indices: Vec::new(),
            policy: "schema-command-and-option-names; reviewed-value-options-only; unicode-only; masked-entries-not-replayable@1".into(),
        };
        let mut index = 0;
        let mut positional = false;
        while index < raw.len() {
            let token = &raw[index];
            if index == 0 {
                record.push(token, !credential_url(token));
            } else if positional {
                record.push(token, false);
            } else if token == "--" {
                record.push(token, true);
                positional = true;
            } else if let Some(arg) = named!(levels, token.as_str()) {
                let safe = safe_value_option(arg.name()) && !sensitive_name(arg.name());
                if let Some((name, value)) = token.split_once('=') {
                    if (safe || arg.value().is_bool()) && !credential_url(value) {
                        record.push(token, true);
                    } else {
                        record.redacted_indices.push(record.argv.len());
                        record.argv.push(format!("{name}=[redacted]"));
                    }
                } else {
                    record.push(token, true);
                    if !arg.value().is_bool() && !arg.kind().is_counted() {
                        index += 1;
                        if let Some(value) = raw.get(index) {
                            record.push(value, safe && !credential_url(value));
                        }
                    }
                }
            } else if let Some(command) = levels.last().and_then(|level| {
                level
                    .subcommands()
                    .values()
                    .find(|command| command.cli_name() == token)
            }) {
                record.push(token, true);
                levels.push(command.args());
            } else {
                // Unknown options, unreviewed positional values and free text are opaque.
                record.push(token, false);
            }
            index += 1;
        }
        Ok(record)
    }

    fn push(&mut self, value: &str, safe: bool) {
        if safe {
            self.argv.push(value.to_owned());
        } else {
            self.redacted_indices.push(self.argv.len());
            self.argv.push("[redacted]".into());
        }
    }
}

fn safe_value_option(name: &str) -> bool {
    // Schema recognition is required as well as this review. New options default to opaque.
    matches!(
        name.replace('_', "-").as_str(),
        "log-filter"
            | "log-level"
            | "log-format"
            | "log-file"
            | "log-file-filter"
            | "log-file-format"
            | "output-format"
            | "repo-root"
            | "sdk"
            | "profile"
            | "branch"
            | "output-dir"
            | "output"
            | "request"
            | "input"
            | "path"
            | "projection"
            | "projection-cache-root"
            | "source-root"
            | "target-root"
            | "timeout"
            | "timeout-seconds"
            | "parallel"
            | "filter"
            | "mode"
            | "minecraft-version"
            | "loader"
            | "release"
            | "java-version"
            | "workers"
            | "test-threads"
    )
}

fn sensitive_name(name: &str) -> bool {
    name.to_ascii_lowercase()
        .split(['-', '_', '.'])
        .any(|word| {
            matches!(
                word,
                "token"
                    | "password"
                    | "passwd"
                    | "secret"
                    | "credential"
                    | "credentials"
                    | "authorization"
                    | "cookie"
                    | "body"
                    | "message"
                    | "prompt"
                    | "apikey"
                    | "auth"
                    | "session"
                    | "key"
                    | "signature"
                    | "sig"
                    | "code"
            )
        })
}

fn credential_url(value: &str) -> bool {
    let trimmed = value.trim_start();
    if ["authorization:", "cookie:"].iter().any(|prefix| {
        trimmed
            .get(..prefix.len())
            .is_some_and(|head| head.eq_ignore_ascii_case(prefix))
    }) {
        return true;
    }
    if value.split_ascii_whitespace().next().is_some_and(|prefix| {
        ["bearer", "basic", "authorization:", "cookie:"]
            .iter()
            .any(|scheme| prefix.eq_ignore_ascii_case(scheme))
    }) {
        return true;
    }
    let Some((_, rest)) = value.split_once("://") else {
        return false;
    };
    let authority = rest.split(['/', '?', '#']).next().unwrap_or_default();
    // Query/fragment contents are opaque: even an unnamed value can be a credential.
    authority.contains('@') || rest.contains(['?', '#'])
}
