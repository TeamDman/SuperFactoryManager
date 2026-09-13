use eyre::Context as _;
use facet::Facet;
use facet_pretty::{ColorMode, PrettyPrinter};
use std::io::IsTerminal as _;

#[derive(Clone, Copy, Debug, Default, PartialEq, Eq, Facet)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub enum OutputFormat {
    #[default]
    Text,
    Json,
    Csv,
}

pub struct CliOutput {
    value: Box<dyn CliOutputValue>,
    exit_code: u8,
}

trait CliOutputValue {
    fn render(&self, format: OutputFormat, stdout_is_terminal: bool) -> eyre::Result<String>;
}

struct FacetCliOutput<T> {
    value: T,
}

struct StructuredJsonCliOutput {
    json: String,
    text: String,
}

impl CliOutput {
    #[must_use]
    pub fn facet<T>(value: T) -> Self
    where
        T: Facet<'static> + 'static,
    {
        Self::facet_with_status(value, 0)
    }

    #[must_use]
    pub fn facet_with_status<T>(value: T, exit_code: u8) -> Self
    where
        T: Facet<'static> + 'static,
    {
        Self {
            value: Box::new(FacetCliOutput { value }),
            exit_code,
        }
    }

    #[must_use]
    pub const fn exit_code(&self) -> u8 {
        self.exit_code
    }

    /// Build one output whose JSON surface is an already-versioned object.
    ///
    /// # Errors
    ///
    /// Returns an error when `json` is not one complete JSON object.
    pub fn structured_json(json: String, text: String, exit_code: u8) -> eyre::Result<Self> {
        let trimmed = json.trim();
        eyre::ensure!(
            trimmed.starts_with('{') && trimmed.ends_with('}'),
            "structured command JSON must be an object"
        );
        let _: facet_json::RawJson<'_> = facet_json::from_str_borrowed(trimmed)
            .wrap_err("structured command JSON is malformed")?;
        Ok(Self {
            value: Box::new(StructuredJsonCliOutput { json, text }),
            exit_code,
        })
    }

    pub(crate) fn emit(self, requested_format: Option<OutputFormat>) -> eyre::Result<()> {
        let terminal = std::io::stdout().is_terminal();
        let format = requested_format.unwrap_or(if terminal {
            OutputFormat::Text
        } else {
            OutputFormat::Json
        });
        let rendered = self.value.render(format, terminal)?;
        let mut stdout = std::io::stdout().lock();
        std::io::Write::write_all(&mut stdout, rendered.as_bytes())
            .wrap_err("failed to write command output")?;
        if !rendered.ends_with('\n') {
            std::io::Write::write_all(&mut stdout, b"\n")
                .wrap_err("failed to terminate command output")?;
        }
        Ok(())
    }
}

impl CliOutputValue for StructuredJsonCliOutput {
    fn render(&self, format: OutputFormat, _stdout_is_terminal: bool) -> eyre::Result<String> {
        match format {
            OutputFormat::Text => Ok(self.text.clone()),
            OutputFormat::Json => Ok(self.json.clone()),
            OutputFormat::Csv => {
                eyre::bail!("CSV output is not supported for structured packet results")
            }
        }
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
            OutputFormat::Csv => facet_csv::to_string(&self.value)
                .wrap_err("failed to serialize command output as CSV"),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn structured_packet_output_has_stable_text_and_direct_json_surfaces() {
        let output = StructuredJsonCliOutput {
            json: r#"{"schema":"sfm.packet.list/1","entries":[]}"#.to_owned(),
            text: "packet-action: list\nschema: sfm.packet.list/1".to_owned(),
        };
        assert_eq!(
            output.render(OutputFormat::Json, false).expect("JSON"),
            r#"{"schema":"sfm.packet.list/1","entries":[]}"#
        );
        assert_eq!(
            output.render(OutputFormat::Text, false).expect("text"),
            "packet-action: list\nschema: sfm.packet.list/1"
        );
        assert!(output.render(OutputFormat::Csv, false).is_err());
    }

    #[test]
    fn structured_packet_output_rejects_invalid_json() {
        assert!(CliOutput::structured_json("not-json".to_owned(), "text".to_owned(), 0).is_err());
    }
}
