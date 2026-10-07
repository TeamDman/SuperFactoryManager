//! Bounded invocation-bound host transport; not an execution permit.
//! Handlers must validate payloads and retain input/output ownership before reply.

use eyre::Result;
use eyre::ensure;
use facet::Facet;
use std::io::BufRead;
use std::io::Write;

const MAX_FRAME_BYTES: usize = 4 * 1024 * 1024;
const MAX_REQUESTS: u64 = 4096;

/// Read one bounded wire frame without interpreting or advancing lifecycle.
/// The owned runner may do this on its pipe-reader thread; only the dispatch
/// thread performs invocation/sequence validation and replies.
pub(crate) fn read_frame(input: &mut impl BufRead) -> Result<Vec<u8>> {
    let mut bytes = Vec::new();
    loop {
        let available = input.fill_buf()?;
        ensure!(
            !available.is_empty(),
            "Host request closed before frame terminator"
        );
        let newline = available.iter().position(|b| *b == b'\n');
        let count = newline.unwrap_or(available.len());
        ensure!(
            bytes
                .len()
                .checked_add(count)
                .is_some_and(|length| length <= MAX_FRAME_BYTES),
            "Oversized host request"
        );
        bytes.extend_from_slice(&available[..count]);
        input.consume(count + usize::from(newline.is_some()));
        if newline.is_some() {
            break;
        }
    }
    ensure!(!bytes.is_empty(), "Empty host request");
    bytes.push(b'\n');
    Ok(bytes)
}

#[derive(Clone, Debug, Facet, PartialEq)]
pub(crate) struct NfrtHostRequest {
    schema: String,
    contract_identity: String,
    sequence: u64,
    pub(crate) operation: String,
    pub(crate) payload: String,
}

impl NfrtHostRequest {
    pub(crate) fn contract_identity(&self) -> &str {
        &self.contract_identity
    }
}

#[derive(Facet)]
struct NfrtHostResponse<'a> {
    schema: &'a str,
    contract_identity: &'a str,
    sequence: u64,
    operation: &'a str,
    payload: &'a str,
}

pub(crate) struct NfrtHostProtocol {
    contract_identity: String,
    sequence: u64,
    graph_ready: bool,
    complete: bool,
    pending: Option<NfrtHostRequest>,
    failed: bool,
}

impl NfrtHostProtocol {
    pub(crate) fn new(contract_identity: String) -> Result<Self> {
        let digest = contract_identity
            .strip_prefix("sha256:")
            .ok_or_else(|| eyre::eyre!("Missing host contract SHA-256"))?;
        ensure!(
            digest.len() == 64
                && digest
                    .bytes()
                    .all(|b| b.is_ascii_digit() || (b'a'..=b'f').contains(&b)),
            "Invalid host contract SHA-256"
        );
        Ok(Self {
            contract_identity,
            sequence: 0,
            graph_ready: false,
            complete: false,
            pending: None,
            failed: false,
        })
    }

    pub(crate) fn read_request(&mut self, input: &mut impl BufRead) -> Result<NfrtHostRequest> {
        ensure!(
            !self.complete
                && !self.failed
                && self.pending.is_none()
                && self.sequence < MAX_REQUESTS,
            "Host lifecycle does not accept another request"
        );
        let bytes = read_frame(input)?;
        let request: NfrtHostRequest = facet_json::from_str(std::str::from_utf8(&bytes)?)?;
        ensure!(
            request.schema == "sfm:nfrt_host_request@1"
                && request.contract_identity == self.contract_identity
                && request.sequence == self.sequence + 1,
            "Foreign or replayed host request"
        );
        ensure!(
            match request.operation.as_str() {
                "graph" => !self.graph_ready,
                "seal" | "tool" | "complete" => self.graph_ready,
                _ => false,
            },
            "Invalid host operation or ordering"
        );
        self.pending = Some(request.clone());
        Ok(request)
    }

    /// Call only after the actual handler succeeds. Failed handlers receive no
    /// success acknowledgement and cannot advance the host lifecycle.
    pub(crate) fn reply(
        &mut self,
        output: &mut impl Write,
        request: &NfrtHostRequest,
        payload: &str,
    ) -> Result<()> {
        ensure!(
            !self.failed && self.pending.as_ref() == Some(request),
            "Response does not match a live pending request"
        );
        let response = NfrtHostResponse {
            schema: "sfm:nfrt_host_response@1",
            contract_identity: &self.contract_identity,
            sequence: request.sequence,
            operation: &request.operation,
            payload,
        };
        let bytes = facet_json::to_string(&response)?;
        ensure!(bytes.len() <= MAX_FRAME_BYTES, "Oversized host response");
        self.failed = true;
        output.write_all(bytes.as_bytes())?;
        output.write_all(b"\n")?;
        output.flush()?;
        self.failed = false;
        self.sequence = request.sequence;
        if request.operation == "graph" {
            self.graph_ready = true;
        }
        if request.operation == "complete" {
            self.complete = true;
        }
        self.pending = None;
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Cursor;

    #[test]
    fn bounded_pipe_frames_preserve_one_frame_and_leave_the_next_unread() -> Result<()> {
        let mut input = Cursor::new(b"first\nsecond\n".to_vec());
        assert_eq!(read_frame(&mut input)?, b"first\n");
        assert_eq!(read_frame(&mut input)?, b"second\n");
        assert!(read_frame(&mut input).is_err());
        let mut exact = vec![b'x'; MAX_FRAME_BYTES];
        exact.push(b'\n');
        assert_eq!(
            read_frame(&mut Cursor::new(exact))?.len(),
            MAX_FRAME_BYTES + 1
        );
        assert!(read_frame(&mut Cursor::new(vec![b'x'; MAX_FRAME_BYTES + 1])).is_err());
        assert!(read_frame(&mut Cursor::new(b"unterminated".to_vec())).is_err());
        Ok(())
    }

    #[test]
    #[ignore = "Requires explicitly supplied existing Java executable and compiled host fixture classpath"]
    fn real_java_stdio_matches_rust_host_framing() -> Result<()> {
        use std::io::BufReader;
        use std::process::Command;
        use std::process::Stdio;
        let java = std::env::var_os("SFM_NFRT_HOST_TEST_JAVA")
            .ok_or_else(|| eyre::eyre!("Missing explicit Java test executable"))?;
        let classpath = std::env::var_os("SFM_NFRT_HOST_TEST_CLASSPATH")
            .ok_or_else(|| eyre::eyre!("Missing compiled host test classpath"))?;
        let mut command = Command::new(java);
        command
            .args(["-Xmx64m", "-cp"])
            .arg(classpath)
            .arg("ca.teamdman.sfm.toolchain.nfrt.SFMHostChannelProcessTests")
            .arg(identity())
            .stdin(Stdio::piped())
            .stdout(Stdio::piped())
            .stderr(Stdio::inherit());
        #[cfg(windows)]
        {
            use std::os::windows::process::CommandExt as _;
            command.creation_flags(0x0800_0000); // CREATE_NO_WINDOW; fixture is noninteractive.
        }
        let mut child = command.spawn()?;
        let mut writer = child
            .stdin
            .take()
            .ok_or_else(|| eyre::eyre!("Missing host fixture stdin"))?;
        let mut reader = BufReader::new(
            child
                .stdout
                .take()
                .ok_or_else(|| eyre::eyre!("Missing host fixture stdout"))?,
        );
        let mut protocol = NfrtHostProtocol::new(identity())?;
        let result = (|| -> Result<()> {
            for expected in ["graph", "seal", "complete"] {
                let request = protocol.read_request(&mut reader)?;
                ensure!(
                    request.operation == expected && request.payload == "{\"fixture\":true}",
                    "Actual Java request changed operation or payload"
                );
                protocol.reply(&mut writer, &request, "{\"accepted\":true}")?;
            }
            Ok(())
        })();
        drop(writer);
        drop(reader);
        if result.is_err() {
            let _ = child.kill();
        }
        let status = child.wait()?;
        result?;
        ensure!(status.success(), "Host channel fixture process failed");
        Ok(())
    }

    fn identity() -> String {
        format!("sha256:{}", "0".repeat(64))
    }
    fn wire(sequence: u64, operation: &str) -> Vec<u8> {
        let request = NfrtHostRequest {
            schema: "sfm:nfrt_host_request@1".to_owned(),
            contract_identity: identity(),
            sequence,
            operation: operation.to_owned(),
            payload: "{}".to_owned(),
        };
        format!("{}\n", facet_json::to_string(&request).unwrap()).into_bytes()
    }

    #[test]
    fn request_lifecycle_requires_exact_acknowledgements() -> Result<()> {
        let mut protocol = NfrtHostProtocol::new(identity())?;
        let request = protocol.read_request(&mut Cursor::new(wire(1, "graph")))?;
        ensure!(
            protocol
                .read_request(&mut Cursor::new(wire(2, "seal")))
                .is_err(),
            "Pending request was bypassed"
        );
        let mut wrong = request.clone();
        wrong.payload = "changed".to_owned();
        ensure!(
            protocol.reply(&mut Vec::new(), &wrong, "{}").is_err(),
            "Changed request was acknowledged"
        );
        protocol.reply(&mut Vec::new(), &request, "{}")?;
        ensure!(
            protocol
                .read_request(&mut Cursor::new(wire(1, "seal")))
                .is_err(),
            "Replay accepted"
        );
        let request = protocol.read_request(&mut Cursor::new(wire(2, "complete")))?;
        protocol.reply(&mut Vec::new(), &request, "{}")?;
        ensure!(
            protocol
                .read_request(&mut Cursor::new(wire(3, "seal")))
                .is_err(),
            "Completed host reopened"
        );
        Ok(())
    }

    #[test]
    fn malformed_wrong_order_and_oversized_frames_are_refused() -> Result<()> {
        for bytes in [
            Vec::new(),
            b"\n".to_vec(),
            b"{}".to_vec(),
            b"\xff\n".to_vec(),
            wire(1, "seal"),
            wire(1, "unknown"),
            vec![b'x'; MAX_FRAME_BYTES + 1],
        ] {
            ensure!(
                NfrtHostProtocol::new(identity())?
                    .read_request(&mut Cursor::new(bytes))
                    .is_err(),
                "Invalid frame accepted"
            );
        }
        Ok(())
    }
}
