use eyre::Context as _;
use facet::Facet;
use facet_pretty::ColorMode;
use facet_pretty::PrettyPrinter;
use std::io::IsTerminal as _;

/// Supported renderings for typed command output.
#[derive(Facet, Clone, Copy, Debug, Default, PartialEq, Eq)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub enum OutputFormat {
    /// Human-readable Facet pretty output.
    #[default]
    Text,
    /// Machine-readable JSON output.
    Json,
    /// Machine-readable CSV output.
    Csv,
}

/// A command result whose presentation is selected once at the CLI boundary.
pub struct CliOutput {
    value: Option<Box<dyn CliOutputValue>>,
    exit_code: u8,
}

impl core::fmt::Debug for CliOutput {
    fn fmt(&self, f: &mut core::fmt::Formatter<'_>) -> core::fmt::Result {
        f.debug_struct("CliOutput")
            .field("has_value", &self.value.is_some())
            .field("exit_code", &self.exit_code)
            .finish()
    }
}

trait CliOutputValue {
    fn render(&self, format: OutputFormat, stdout_is_terminal: bool) -> eyre::Result<String>;
}

struct FacetCliOutput<T> {
    value: T,
    csv_renderer: Option<fn(&T) -> eyre::Result<String>>,
}

impl CliOutput {
    /// Return a result for a legacy command that already owns its output.
    #[must_use]
    pub const fn none() -> Self {
        Self {
            value: None,
            exit_code: 0,
        }
    }

    /// Return a typed value to be rendered at the top-level CLI boundary.
    #[must_use]
    pub fn facet<T>(value: T) -> Self
    where
        T: Facet<'static> + 'static,
    {
        Self::facet_with_status(value, 0)
    }

    /// Return a typed value with a report-specific CSV projection.
    #[must_use]
    pub fn facet_with_csv<T>(value: T, csv_renderer: fn(&T) -> eyre::Result<String>) -> Self
    where
        T: Facet<'static> + 'static,
    {
        Self::facet_with_csv_and_status(value, csv_renderer, 0)
    }

    #[must_use]
    pub fn facet_with_status<T>(value: T, exit_code: u8) -> Self
    where
        T: Facet<'static> + 'static,
    {
        Self {
            value: Some(Box::new(FacetCliOutput {
                value,
                csv_renderer: None,
            })),
            exit_code,
        }
    }

    #[must_use]
    pub fn facet_with_csv_and_status<T>(
        value: T,
        csv_renderer: fn(&T) -> eyre::Result<String>,
        exit_code: u8,
    ) -> Self
    where
        T: Facet<'static> + 'static,
    {
        Self {
            value: Some(Box::new(FacetCliOutput {
                value,
                csv_renderer: Some(csv_renderer),
            })),
            exit_code,
        }
    }

    #[must_use]
    pub const fn exit_code(&self) -> u8 {
        self.exit_code
    }

    /// Render this output without writing it. This is the scenario/test capture seam.
    ///
    /// # Errors
    ///
    /// Returns an error when the selected serializer cannot represent the value.
    pub fn render(
        &self,
        requested_format: Option<OutputFormat>,
        stdout_is_terminal: bool,
    ) -> eyre::Result<Option<String>> {
        let Some(output) = &self.value else {
            return Ok(None);
        };
        let format = requested_format.unwrap_or(if stdout_is_terminal {
            OutputFormat::Text
        } else {
            OutputFormat::Json
        });
        output.render(format, stdout_is_terminal).map(Some)
    }

    /// Render this output and write it to stdout exactly once.
    ///
    /// # Errors
    ///
    /// Returns an error when rendering or writing stdout fails.
    pub fn emit(self, requested_format: Option<OutputFormat>) -> eyre::Result<()> {
        let stdout_is_terminal = std::io::stdout().is_terminal();
        let mut stdout = std::io::stdout().lock();
        self.emit_to(&mut stdout, requested_format, stdout_is_terminal)
    }

    /// Render this output into an explicit writer.
    ///
    /// # Errors
    ///
    /// Returns an error when rendering or writing fails.
    pub fn emit_to(
        &self,
        writer: &mut impl std::io::Write,
        requested_format: Option<OutputFormat>,
        stdout_is_terminal: bool,
    ) -> eyre::Result<()> {
        let Some(rendered) = self.render(requested_format, stdout_is_terminal)? else {
            return Ok(());
        };
        writer
            .write_all(rendered.as_bytes())
            .wrap_err("failed to write command output")?;
        if !rendered.ends_with('\n') {
            writer
                .write_all(b"\n")
                .wrap_err("failed to terminate command output")?;
        }
        writer.flush().wrap_err("failed to flush command output")
    }
}

impl Default for CliOutput {
    fn default() -> Self {
        Self::none()
    }
}

impl<T> CliOutputValue for FacetCliOutput<T>
where
    T: Facet<'static> + 'static,
{
    fn render(&self, format: OutputFormat, stdout_is_terminal: bool) -> eyre::Result<String> {
        match format {
            OutputFormat::Text => Ok(PrettyPrinter::new()
                .with_colors(if stdout_is_terminal {
                    ColorMode::Always
                } else {
                    ColorMode::Never
                })
                .format(&self.value)),
            OutputFormat::Json => facet_json::to_string_pretty(&self.value)
                .wrap_err("failed to serialize command output as JSON"),
            OutputFormat::Csv => self.csv_renderer.map_or_else(
                || {
                    facet_csv::to_string(&self.value)
                        .wrap_err("failed to serialize command output as CSV")
                },
                |render| render(&self.value),
            ),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::CliOutput;
    use super::OutputFormat;
    use facet::Facet;

    #[derive(Facet)]
    struct ExampleOutput {
        name: String,
        count: u32,
    }

    fn example() -> CliOutput {
        CliOutput::facet(ExampleOutput {
            name: "example".to_owned(),
            count: 2,
        })
    }

    #[test]
    fn cli_output_defaults_to_text_for_terminals_and_json_for_redirects() {
        let terminal = example().render(None, true).expect("terminal rendering");
        let redirected = example().render(None, false).expect("redirected rendering");

        assert!(terminal.expect("typed output").contains("example"));
        assert!(redirected.expect("typed output").starts_with('{'));
    }

    #[test]
    fn cli_output_supports_all_explicit_formats() {
        for format in [OutputFormat::Text, OutputFormat::Json, OutputFormat::Csv] {
            let rendered = example()
                .render(Some(format), false)
                .expect("explicit rendering")
                .expect("typed output");
            assert!(rendered.contains("example"));
        }
    }

    #[test]
    fn cli_output_none_writes_no_bytes() {
        let mut bytes = Vec::new();
        CliOutput::none()
            .emit_to(&mut bytes, Some(OutputFormat::Json), false)
            .expect("no output should succeed");
        assert!(bytes.is_empty());
    }

    #[test]
    fn cli_output_preserves_typed_nonzero_status() {
        let output = CliOutput::facet_with_status(
            ExampleOutput {
                name: "missing".to_owned(),
                count: 0,
            },
            2,
        );
        assert_eq!(output.exit_code(), 2);
        assert!(
            output
                .render(Some(OutputFormat::Json), false)
                .expect("render")
                .expect("typed")
                .contains("missing")
        );
    }
}
