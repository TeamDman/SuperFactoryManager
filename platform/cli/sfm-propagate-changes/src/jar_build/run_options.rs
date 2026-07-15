use facet::Facet;
use std::fmt;
use std::str::FromStr;
use std::time::Duration;

#[derive(Clone, Debug, Default)]
pub struct RunOptions {
    pub game_test_filter: Option<String>,
    pub game_puppet_filter: Option<String>,
    pub game_test_bisect: Option<GameTestBisectOptions>,
    pub client_puppet_keep_open: ClientPuppetKeepOpen,
    pub preview_width: u16,
    pub preview_height: u16,
    pub client_title_screen: Option<ClientTitleScreen>,
    pub client_solo: bool,
    pub client_hotswap_port: Option<u16>,
}

#[derive(Clone, Copy, Debug, Eq, Facet, PartialEq)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub enum ClientTitleScreen {
    TextEditor,
    InputDiag,
    DrawCanvas,
}

impl ClientTitleScreen {
    #[must_use]
    pub const fn property_value(self) -> &'static str {
        match self {
            Self::TextEditor => "text-editor",
            Self::InputDiag => "input-diag",
            Self::DrawCanvas => "draw-canvas",
        }
    }
}

impl fmt::Display for ClientTitleScreen {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(self.property_value())
    }
}

impl FromStr for ClientTitleScreen {
    type Err = eyre::Report;

    fn from_str(input: &str) -> Result<Self, Self::Err> {
        match input.trim().to_ascii_lowercase().as_str() {
            "text-editor" | "text_editor" | "editor" => Ok(Self::TextEditor),
            "input-diag" | "input_diag" | "input-diagnostics" | "key-diag" | "key-debug" => {
                Ok(Self::InputDiag)
            }
            "draw-canvas" | "draw_canvas" | "draw" | "canvas" => Ok(Self::DrawCanvas),
            _ => eyre::bail!(
                "Invalid --title-screen '{input}'. Expected 'text-editor', 'input-diag', or 'draw-canvas'."
            ),
        }
    }
}

#[derive(Clone, Debug)]
pub struct GameTestBisectOptions {
    pub target: String,
    pub max_runs: Option<usize>,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum ClientPuppetKeepOpen {
    Countdown { seconds: u64 },
    Forever,
}

impl ClientPuppetKeepOpen {
    pub const DEFAULT_COUNTDOWN_SECONDS: u64 = 25;

    /// Convert the optional-value CLI flag into client-puppet keep-open behavior.
    ///
    /// # Errors
    ///
    /// Returns an error if the supplied duration cannot be parsed, is zero, or cannot be
    /// represented by the client runtime seconds property.
    pub fn from_cli(value: Option<Option<String>>) -> eyre::Result<Self> {
        match value {
            None => Ok(Self::default()),
            Some(None) => Ok(Self::Forever),
            Some(Some(raw)) => {
                let trimmed = raw.trim();
                if trimmed.is_empty() {
                    eyre::bail!("--keep-open duration must not be empty");
                }
                let duration = parse_keep_open_duration(trimmed)?;
                Ok(Self::Countdown {
                    seconds: duration_to_property_seconds(duration)?,
                })
            }
        }
    }

    #[must_use]
    pub fn property_seconds(self) -> String {
        match self {
            Self::Countdown { seconds } => seconds.to_string(),
            Self::Forever => "-1".to_string(),
        }
    }

    #[must_use]
    pub const fn countdown_seconds(self) -> Option<u64> {
        match self {
            Self::Countdown { seconds } => Some(seconds),
            Self::Forever => None,
        }
    }
}

impl Default for ClientPuppetKeepOpen {
    fn default() -> Self {
        Self::Countdown {
            seconds: Self::DEFAULT_COUNTDOWN_SECONDS,
        }
    }
}

fn parse_keep_open_duration(input: &str) -> eyre::Result<Duration> {
    match humantime::parse_duration(input) {
        Ok(duration) => Ok(duration),
        Err(error) if input.bytes().all(|byte| byte.is_ascii_digit()) => input
            .parse::<u64>()
            .map(Duration::from_secs)
            .map_err(|parse_error| {
                eyre::eyre!(
                    "Invalid --keep-open duration {input:?}: {error}; also failed to parse as seconds: {parse_error}"
                )
            }),
        Err(error) => Err(eyre::eyre!(
            "Invalid --keep-open duration {input:?}: {error}"
        )),
    }
}

fn duration_to_property_seconds(duration: Duration) -> eyre::Result<u64> {
    if duration.is_zero() {
        eyre::bail!("--keep-open duration must be greater than zero");
    }
    let seconds = duration
        .as_secs()
        .saturating_add(u64::from(duration.subsec_nanos() > 0));
    if seconds > i32::MAX as u64 {
        eyre::bail!("--keep-open duration must not exceed {} seconds", i32::MAX);
    }
    Ok(seconds)
}
