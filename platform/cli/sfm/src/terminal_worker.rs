//! Bounded, prompt-free JSON Lines worker for an existing terminal's foreground process.
//!
//! This module does not discover Minecraft, spawn processes, access files, or
//! execute commands. A locally owned terminal may bootstrap this executable;
//! subsequent stdin is data only. Echoed input has type `touch`/`quit`, never
//! the output-only `ready`/`ack`/`render`/`bye`/`error` types.

use crate::output::CliOutput;
use eyre::Context as _;
use facet::Facet;
use std::io::{BufRead, Write};

pub const VERSION: u8 = 1;
pub const MAX_LINE_BYTES: usize = 1024;
pub const MAX_COMMANDS: u32 = 4096;

#[derive(Debug, Facet)]
#[repr(u8)]
#[facet(rename_all = "snake_case")]
enum InputType {
    Touch,
    Quit,
}

#[derive(Debug, Facet)]
#[facet(deny_unknown_fields)]
struct WireInput<'a> {
    version: facet_json::RawJson<'a>,
    #[facet(rename = "type")]
    kind: InputType,
    id: facet_json::RawJson<'a>,
    #[facet(default)]
    u: Option<facet_json::RawJson<'a>>,
    #[facet(default)]
    v: Option<facet_json::RawJson<'a>>,
}

#[derive(Debug)]
struct Input {
    kind: InputType,
    id: u32,
    u: Option<f64>,
    v: Option<f64>,
}

#[derive(Debug, Facet, PartialEq)]
#[repr(u8)]
#[facet(tag = "type", rename_all = "snake_case")]
enum Record {
    Ready {
        version: u8,
        max_line_bytes: usize,
        max_commands: u32,
    },
    Ack {
        version: u8,
        id: u32,
        count: u32,
        u: f64,
        v: f64,
    },
    Render {
        version: u8,
        count: u32,
        color: String,
    },
    Bye {
        version: u8,
        count: u32,
        reason: String,
    },
    Error {
        version: u8,
        count: u32,
        code: String,
    },
}

enum Line {
    Eof,
    Bytes(Vec<u8>),
    TooLong,
}

/// Runs on the CLI's foreground thread; no game/render thread may call it directly.
pub(crate) fn invoke() -> eyre::Result<CliOutput> {
    let input = std::io::stdin();
    let output = std::io::stdout();
    let exit_code = run(&mut input.lock(), &mut output.lock(), MAX_COMMANDS)?;
    Ok(CliOutput::silent(exit_code))
}

fn run(input: &mut impl BufRead, output: &mut impl Write, max_commands: u32) -> eyre::Result<u8> {
    emit(
        output,
        &Record::Ready {
            version: VERSION,
            max_line_bytes: MAX_LINE_BYTES,
            max_commands,
        },
    )?;
    render(output, 0)?;
    let mut count = 0;
    let mut exit_code = 0;
    for _ in 0..max_commands {
        let line = match read_bounded_line(input)? {
            Line::Eof => {
                bye(output, count, "eof")?;
                return Ok(exit_code);
            }
            Line::TooLong => {
                error(output, count, "line_too_long")?;
                bye(output, count, "input_limit")?;
                return Ok(1);
            }
            Line::Bytes(line) => line,
        };
        let command = match decode(&line) {
            Ok(command) => command,
            Err(code) => {
                error(output, count, code)?;
                exit_code = 1;
                continue;
            }
        };
        match command.kind {
            InputType::Quit => {
                bye(output, count, "quit")?;
                return Ok(exit_code);
            }
            InputType::Touch => {
                // decode has validated presence, finiteness and range.
                let (Some(u), Some(v)) = (command.u, command.v) else {
                    unreachable!()
                };
                count += 1;
                emit(
                    output,
                    &Record::Ack {
                        version: VERSION,
                        id: command.id,
                        count,
                        u,
                        v,
                    },
                )?;
                render(output, count)?;
            }
        }
    }
    error(output, count, "command_limit")?;
    bye(output, count, "input_limit")?;
    Ok(1)
}

fn read_bounded_line(input: &mut impl BufRead) -> std::io::Result<Line> {
    let mut line = Vec::with_capacity(MAX_LINE_BYTES);
    loop {
        let available = input.fill_buf()?;
        if available.is_empty() {
            return Ok(if line.is_empty() {
                Line::Eof
            } else {
                Line::Bytes(line)
            });
        }
        let newline = available.iter().position(|byte| *byte == b'\n');
        let length = newline.unwrap_or(available.len());
        if length > MAX_LINE_BYTES - line.len() {
            return Ok(Line::TooLong);
        }
        line.extend_from_slice(&available[..length]);
        input.consume(length + usize::from(newline.is_some()));
        if newline.is_some() {
            return Ok(Line::Bytes(line));
        }
    }
}

fn decode(line: &[u8]) -> Result<Input, &'static str> {
    let text = std::str::from_utf8(line).map_err(|_| "invalid_utf8")?;
    // The protocol has scalar fields only. Reject nested containers before the
    // general JSON decoder so even a full line of opening brackets is bounded.
    if !flat_object(text) {
        return Err("invalid_json");
    }
    let wire: WireInput<'_> = facet_json::from_str_borrowed(text).map_err(|_| "invalid_json")?;
    // Facet accepts quoted numeric strings for numeric target types. Preserve
    // the raw JSON lexeme so this wire protocol can reject that coercion.
    if integer(&wire.version)? != u32::from(VERSION) {
        return Err("unsupported_version");
    }
    let mut command = Input {
        kind: wire.kind,
        id: integer(&wire.id)?,
        u: None,
        v: None,
    };
    match command.kind {
        InputType::Touch => {
            command.u = Some(wire_coordinate(wire.u.as_ref())?);
            command.v = Some(wire_coordinate(wire.v.as_ref())?);
        }
        InputType::Quit => {
            if wire.u.is_some() || wire.v.is_some() {
                return Err("unexpected_coordinates");
            }
        }
    }
    Ok(command)
}

fn integer(raw: &facet_json::RawJson<'_>) -> Result<u32, &'static str> {
    let literal = raw.as_str().trim();
    if literal.is_empty() || !literal.bytes().all(|byte| byte.is_ascii_digit()) {
        return Err("invalid_integer");
    }
    literal.parse().map_err(|_| "invalid_integer")
}

fn wire_coordinate(raw: Option<&facet_json::RawJson<'_>>) -> Result<f64, &'static str> {
    let literal = raw.ok_or("missing_coordinate")?.as_str().trim();
    if !literal
        .bytes()
        .next()
        .is_some_and(|byte| byte.is_ascii_digit() || byte == b'-')
    {
        return Err("invalid_coordinate");
    }
    coordinate(Some(literal.parse().map_err(|_| "invalid_coordinate")?))
}

fn coordinate(value: Option<f64>) -> Result<f64, &'static str> {
    let value = value.ok_or("missing_coordinate")?;
    if !value.is_finite() || !(0.0..=1.0).contains(&value) {
        return Err("invalid_coordinate");
    }
    Ok(if value == 0.0 { 0.0 } else { value })
}

fn flat_object(text: &str) -> bool {
    let mut quoted = false;
    let mut escaped = false;
    let mut opened = false;
    let mut closed = false;
    for byte in text.bytes() {
        if quoted {
            if escaped {
                escaped = false;
            } else if byte == b'\\' {
                escaped = true;
            } else if byte == b'"' {
                quoted = false;
            }
            continue;
        }
        match byte {
            b'"' => quoted = true,
            b'{' if !opened && !closed => opened = true,
            b'}' if opened && !closed => closed = true,
            b'{' | b'}' | b'[' | b']' => return false,
            _ => {}
        }
    }
    opened && closed && !quoted
}

fn render(output: &mut impl Write, count: u32) -> eyre::Result<()> {
    emit(
        output,
        &Record::Render {
            version: VERSION,
            count,
            color: if count.is_multiple_of(2) {
                "red"
            } else {
                "blue"
            }
            .to_owned(),
        },
    )
}

fn bye(output: &mut impl Write, count: u32, reason: &str) -> eyre::Result<()> {
    emit(
        output,
        &Record::Bye {
            version: VERSION,
            count,
            reason: reason.to_owned(),
        },
    )
}

fn error(output: &mut impl Write, count: u32, code: &str) -> eyre::Result<()> {
    emit(
        output,
        &Record::Error {
            version: VERSION,
            count,
            code: code.to_owned(),
        },
    )
}

fn emit(output: &mut impl Write, record: &Record) -> eyre::Result<()> {
    let json =
        facet_json::to_string(record).wrap_err("could not serialize terminal worker record")?;
    eyre::ensure!(
        json.len() <= MAX_LINE_BYTES,
        "terminal worker output exceeded its line limit"
    );
    writeln!(output, "{json}").wrap_err("could not write terminal worker record")?;
    output
        .flush()
        .wrap_err("could not flush terminal worker record")
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::{BufReader, Cursor};

    fn transcript(input: &[u8], limit: u32) -> (u8, Vec<Record>) {
        let mut output = Vec::new();
        let status = run(&mut Cursor::new(input), &mut output, limit).expect("worker transcript");
        let text = String::from_utf8(output).expect("UTF-8 output");
        let records = text
            .lines()
            .map(|line| {
                assert!(line.len() <= MAX_LINE_BYTES);
                facet_json::from_str(line).expect("one complete structured record")
            })
            .collect();
        (status, records)
    }

    #[test]
    fn ready_touch_render_and_quit_are_deterministic_and_distinct_from_command_echo() {
        let (status, records) = transcript(
            concat!(
                "{\"version\":1,\"type\":\"touch\",\"id\":7,\"u\":0.25,\"v\":0.75}\r\n",
                "{\"version\":1,\"type\":\"touch\",\"id\":8,\"u\":0,\"v\":1}\n",
                "{\"version\":1,\"type\":\"quit\",\"id\":9}\n"
            )
            .as_bytes(),
            MAX_COMMANDS,
        );
        assert_eq!(status, 0);
        assert_eq!(
            records,
            vec![
                Record::Ready {
                    version: VERSION,
                    max_line_bytes: MAX_LINE_BYTES,
                    max_commands: MAX_COMMANDS
                },
                Record::Render {
                    version: VERSION,
                    count: 0,
                    color: "red".into()
                },
                Record::Ack {
                    version: VERSION,
                    id: 7,
                    count: 1,
                    u: 0.25,
                    v: 0.75
                },
                Record::Render {
                    version: VERSION,
                    count: 1,
                    color: "blue".into()
                },
                Record::Ack {
                    version: VERSION,
                    id: 8,
                    count: 2,
                    u: 0.0,
                    v: 1.0
                },
                Record::Render {
                    version: VERSION,
                    count: 2,
                    color: "red".into()
                },
                Record::Bye {
                    version: VERSION,
                    count: 2,
                    reason: "quit".into()
                },
            ]
        );
        for record in records {
            let json = facet_json::to_string(&record).expect("record");
            assert!(
                decode(json.as_bytes()).is_err(),
                "output must not parse as echoed input"
            );
        }
    }

    #[test]
    fn eof_is_graceful_with_or_without_a_final_newline() {
        for input in [
            b"".as_slice(),
            br#"{"version":1,"type":"touch","id":0,"u":0,"v":0}"#,
        ] {
            let (status, records) = transcript(input, MAX_COMMANDS);
            assert_eq!(status, 0);
            assert!(matches!(records.last(), Some(Record::Bye { reason, .. }) if reason == "eof"));
        }
    }

    #[test]
    fn malformed_inputs_never_ack_or_change_state() {
        for input in [
            "",
            "null",
            "[]",
            "echo hello",
            "{}",
            r#"{"version":2,"type":"touch","id":1,"u":0,"v":0}"#,
            r#"{"version":1,"type":"ready","id":1}"#,
            r#"{"version":1,"type":"touch","id":1,"u":null,"v":0}"#,
            r#"{"version":1,"type":"touch","id":1,"u":-0.1,"v":0}"#,
            r#"{"version":1,"type":"touch","id":1,"u":0,"v":1.1}"#,
            r#"{"version":1,"type":"touch","id":1,"u":1e309,"v":0}"#,
            r#"{"version":1,"type":"touch","id":1,"u":NaN,"v":0}"#,
            r#"{"version":1,"type":"touch","id":1,"u":"0.5","v":0}"#,
            r#"{"version":1,"type":"touch","id":-1,"u":0,"v":0}"#,
            r#"{"version":1,"type":"touch","id":4294967296,"u":0,"v":0}"#,
            r#"{"version":"1","type":"touch","id":1,"u":0,"v":0}"#,
            r#"{"version":1,"type":"touch","id":"1","u":0,"v":0}"#,
            r#"{"version":1,"type":"touch","id":1.5,"u":0,"v":0}"#,
            r#"{"version":1,"type":"touch","id":1,"u":false,"v":0}"#,
            r#"{"version":1,"type":"quit","id":1,"u":0,"v":0}"#,
            r#"{"version":1,"type":"quit","id":1,"shell":"anything"}"#,
            r#"{"version":1,"type":"quit","id":1,"id":2}"#,
            r#"{"version":1,"type":"quit","id":1}{}"#,
        ] {
            let (status, records) = transcript(format!("{input}\n").as_bytes(), MAX_COMMANDS);
            assert_eq!(status, 1, "accepted {input}");
            assert!(
                matches!(&records[2], Record::Error { count: 0, .. }),
                "{records:?}"
            );
            assert_eq!(records.len(), 4, "malformed input affected state: {input}");
        }
    }

    #[test]
    fn invalid_utf8_recovers_at_the_next_line_without_echoing_untrusted_bytes() {
        let (status, records) = transcript(
            b"\xff\n{\"version\":1,\"type\":\"quit\",\"id\":1}\n",
            MAX_COMMANDS,
        );
        assert_eq!(status, 1);
        assert!(matches!(&records[2], Record::Error { code, .. } if code == "invalid_utf8"));
        assert!(matches!(&records[3], Record::Bye { reason, .. } if reason == "quit"));
    }

    #[test]
    fn oversized_line_fails_closed_before_consuming_following_commands() {
        let mut input = vec![b' '; MAX_LINE_BYTES + 1];
        input.extend_from_slice(b"\n{\"version\":1,\"type\":\"quit\",\"id\":1}\n");
        let (status, records) = transcript(&input, MAX_COMMANDS);
        assert_eq!(status, 1);
        assert!(matches!(&records[2], Record::Error { code, .. } if code == "line_too_long"));
        assert!(matches!(&records[3], Record::Bye { reason, .. } if reason == "input_limit"));
    }

    #[test]
    fn bounded_reader_handles_chunk_boundaries_and_exact_limit() {
        for capacity in [1, 7, 1024, 2048] {
            let mut exact = vec![b' '; MAX_LINE_BYTES];
            exact.push(b'\n');
            let mut reader = BufReader::with_capacity(capacity, Cursor::new(exact));
            assert!(
                matches!(read_bounded_line(&mut reader).expect("line"), Line::Bytes(bytes) if bytes.len() == MAX_LINE_BYTES)
            );
            assert!(matches!(
                read_bounded_line(&mut reader).expect("EOF"),
                Line::Eof
            ));
        }
    }

    #[test]
    fn input_budget_counts_invalid_lines_too() {
        let (status, records) = transcript(b"\n\n\n", 2);
        assert_eq!(status, 1);
        assert_eq!(
            records
                .iter()
                .filter(
                    |record| matches!(record, Record::Error { code, .. } if code == "invalid_json")
                )
                .count(),
            2
        );
        assert!(matches!(&records[4], Record::Error { code, .. } if code == "command_limit"));
    }

    #[test]
    fn coordinates_accept_endpoints_and_normalize_negative_zero() {
        let decoded = decode(br#"{"version":1,"type":"touch","id":1,"u":-0.0,"v":1e0}"#)
            .expect("coordinates");
        assert_eq!(decoded.u.expect("u").to_bits(), 0.0f64.to_bits());
        assert_eq!(decoded.v, Some(1.0));
        assert_eq!(coordinate(Some(f64::NAN)), Err("invalid_coordinate"));
        assert_eq!(coordinate(Some(f64::INFINITY)), Err("invalid_coordinate"));
        assert_eq!(
            coordinate(Some(f64::NEG_INFINITY)),
            Err("invalid_coordinate")
        );
    }

    #[test]
    fn numeric_lexemes_do_not_coerce_quoted_strings() {
        assert_eq!(
            wire_coordinate(Some(&facet_json::RawJson::from("0.5"))),
            Ok(0.5)
        );
        assert_eq!(
            wire_coordinate(Some(&facet_json::RawJson::from("\"0.5\""))),
            Err("invalid_coordinate")
        );
        assert_eq!(integer(&facet_json::RawJson::from("1")), Ok(1));
        assert_eq!(
            integer(&facet_json::RawJson::from("\"1\"")),
            Err("invalid_integer")
        );
    }

    #[test]
    fn deeply_nested_input_is_rejected_before_json_deserialization() {
        let input = format!(
            "{{\"version\":1,\"type\":\"touch\",\"id\":1,\"u\":{},\"v\":0}}",
            "[".repeat(500)
        );
        assert_eq!(
            decode(input.as_bytes()).expect_err("nested input"),
            "invalid_json"
        );
    }
}
