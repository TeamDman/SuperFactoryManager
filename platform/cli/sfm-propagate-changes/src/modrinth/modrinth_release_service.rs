//! One owned release upload shared by legacy and verified-package consumers.
//!
//! Preparing an upload does not acquire credentials or authorize publication.
//! Only the explicit service call invokes its supplied HTTP transport.

use super::ModrinthCreateVersionPayload;
use super::ModrinthCreateVersionResponse;
use eyre::Context as _;
use eyre::Result;
use eyre::ensure;
use reqwest::StatusCode;
use reqwest::blocking::Client;
use reqwest::blocking::Request;
use reqwest::blocking::multipart;
use std::fmt;
use tracing::debug;

pub(crate) const MODRINTH_API_ROOT: &str = "https://api.modrinth.com/v2";

/// Exact metadata and file bytes owned before multipart preparation.
pub(crate) struct ModrinthVersionUpload {
    metadata_json: String,
    file_name: String,
    file_bytes: Vec<u8>,
}

impl ModrinthVersionUpload {
    /// Validate the upload shape without changing the supplied metadata bytes.
    pub(crate) fn new(
        metadata_json: String,
        file_name: String,
        file_bytes: Vec<u8>,
    ) -> Result<Self> {
        validate_file_name(&file_name)?;
        ensure!(!file_bytes.is_empty(), "Modrinth upload JAR is empty");
        let payload: ModrinthCreateVersionPayload = facet_json::from_str(&metadata_json)
            .wrap_err("Invalid Modrinth upload metadata JSON")?;
        ensure!(
            payload.file_parts == ["file"] && payload.version_type == "release",
            "Modrinth release metadata must identify exactly the file part and release type"
        );
        ensure!(
            !payload.name.trim().is_empty()
                && !payload.version_number.trim().is_empty()
                && !payload.project_id.trim().is_empty()
                && !payload.game_versions.is_empty()
                && payload
                    .game_versions
                    .iter()
                    .all(|value| !value.trim().is_empty())
                && !payload.loaders.is_empty()
                && payload.loaders.iter().all(|value| !value.trim().is_empty()),
            "Modrinth release metadata has an empty required identity or target"
        );
        Ok(Self {
            metadata_json,
            file_name,
            file_bytes,
        })
    }
}

impl fmt::Debug for ModrinthVersionUpload {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.debug_struct("ModrinthVersionUpload")
            .field("file_name", &self.file_name)
            .field("metadata_utf8_bytes", &self.metadata_json.len())
            .field("jar_bytes", &self.file_bytes.len())
            .finish_non_exhaustive()
    }
}

fn validate_file_name(file_name: &str) -> Result<()> {
    ensure!(
        !file_name.is_empty()
            && std::path::Path::new(file_name)
                .extension()
                .is_some_and(|extension| extension.eq_ignore_ascii_case("jar"))
            && !file_name.contains(['/', '\\', ':'])
            && !file_name.ends_with([' ', '.'])
            && !file_name
                .chars()
                .any(|character| character.is_control() || "<>\"|?*".contains(character)),
        "Modrinth upload filename must be a safe JAR basename"
    );
    let stem = file_name
        .split('.')
        .next()
        .unwrap_or("")
        .to_ascii_uppercase();
    let numbered_device = stem.len() == 4
        && (stem.starts_with("COM") || stem.starts_with("LPT"))
        && matches!(stem.as_bytes()[3], b'1'..=b'9');
    ensure!(
        !matches!(stem.as_str(), "CON" | "PRN" | "AUX" | "NUL") && !numbered_device,
        "Modrinth upload filename is a reserved basename"
    );
    Ok(())
}

/// Send an owned upload through the caller's already configured HTTP client.
pub(crate) fn create_version(client: &Client, upload: ModrinthVersionUpload) -> Result<String> {
    create_version_with(client, upload, |request| {
        let response = client
            .execute(request)
            .wrap_err("Failed to upload JAR to Modrinth")?;
        let status = response.status();
        let body = response
            .text()
            .wrap_err("Failed to read upload response from Modrinth")?;
        Ok((status, body))
    })
}

/// Build the actual multipart request before invoking one transport call.
///
/// Tests can inspect and buffer this same request without opening a socket.
pub(crate) fn create_version_with(
    client: &Client,
    upload: ModrinthVersionUpload,
    send: impl FnOnce(Request) -> Result<(StatusCode, String)>,
) -> Result<String> {
    let ModrinthVersionUpload {
        metadata_json,
        file_name,
        file_bytes,
    } = upload;
    let part = multipart::Part::bytes(file_bytes)
        .file_name(file_name.clone())
        .mime_str("application/java-archive")?;
    let form = multipart::Form::new()
        .text("data", metadata_json)
        .part("file", part);
    let url = format!("{MODRINTH_API_ROOT}/version");
    let request = client
        .post(&url)
        .multipart(form)
        .build()
        .wrap_err("Failed to build Modrinth upload request")?;
    let (status, body) = send(request)?;
    debug!(body, url, ?status);
    ensure!(
        status.is_success(),
        "Modrinth upload failed for {file_name} ({status}): {body}"
    );
    let parsed: ModrinthCreateVersionResponse =
        facet_json::from_str(&body).wrap_err("Failed to parse Modrinth upload response JSON")?;
    ensure!(
        !parsed.id.is_empty() && parsed.id.bytes().all(|byte| byte.is_ascii_alphanumeric()),
        "Modrinth upload response has an empty or malformed version ID"
    );
    Ok(parsed.id)
}

#[cfg(test)]
mod tests {
    use super::*;
    use reqwest::header::CONTENT_TYPE;

    fn metadata() -> String {
        let payload = ModrinthCreateVersionPayload::for_release(
            "Super Factory Manager MC1.21 v4.35.0",
            "4.35.0",
            "Reviewed notes\n",
            &["1.21".to_owned()],
            &["neoforge".to_owned()],
            "reviewed-project",
        );
        facet_json::to_string_pretty(&payload).unwrap() + "\n"
    }

    fn upload() -> ModrinthVersionUpload {
        ModrinthVersionUpload::new(
            metadata(),
            "SFM-MC1.21-4.35.0.jar".to_owned(),
            b"PK\x03\x04owned JAR bytes\x00\xff".to_vec(),
        )
        .unwrap()
    }

    fn client() -> Client {
        Client::builder().no_proxy().build().unwrap()
    }

    #[test]
    fn fake_transport_receives_exact_owned_multipart_request_once() {
        let metadata = metadata();
        let jar = b"PK\x03\x04owned JAR bytes\x00\xff";
        let mut calls = 0;
        let id = create_version_with(&client(), upload(), |mut request| {
            calls += 1;
            assert_eq!(request.method(), reqwest::Method::POST);
            assert_eq!(request.url().as_str(), "https://api.modrinth.com/v2/version");
            let content_type = request.headers()[CONTENT_TYPE].to_str().unwrap();
            let boundary = content_type
                .strip_prefix("multipart/form-data; boundary=")
                .unwrap()
                .to_owned();
            let mut expected = format!(
                "--{boundary}\r\nContent-Disposition: form-data; name=\"data\"\r\n\r\n{metadata}\r\n\
                 --{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"SFM-MC1.21-4.35.0.jar\"\r\n\
                 Content-Type: application/java-archive\r\n\r\n"
            )
            .into_bytes();
            expected.extend_from_slice(jar);
            expected.extend_from_slice(format!("\r\n--{boundary}--\r\n").as_bytes());
            let body = request.body_mut().as_mut().unwrap().buffer().unwrap();
            assert_eq!(body, expected);
            Ok((StatusCode::CREATED, r#"{"id":"Created123"}"#.to_owned()))
        })
        .unwrap();
        assert_eq!(id, "Created123");
        assert_eq!(calls, 1);
    }

    #[test]
    fn rejects_unsafe_names_empty_jars_and_invalid_metadata() {
        assert!(ModrinthVersionUpload::new(metadata(), "file.JAR".to_owned(), vec![1]).is_ok());
        for name in [
            "",
            "file.txt",
            "../file.jar",
            "dir/file.jar",
            "dir\\file.jar",
            "C:file.jar",
            "file\n.jar",
            "file\".jar",
            "file?.jar",
            "NUL.jar",
            "com1.jar",
            "LPT9.jar",
        ] {
            assert!(ModrinthVersionUpload::new(metadata(), name.to_owned(), vec![1]).is_err());
        }
        assert!(ModrinthVersionUpload::new(metadata(), "file.jar".to_owned(), vec![]).is_err());
        for json in ["", "null", "{}", "{", r#"{"name":42}"#] {
            assert!(
                ModrinthVersionUpload::new(json.to_owned(), "file.jar".to_owned(), vec![1])
                    .is_err()
            );
        }
        for changed in [
            metadata().replace("\"file\"", "\"other\""),
            metadata().replace("\"release\"", "\"beta\""),
            metadata().replace("\"reviewed-project\"", "\"\""),
        ] {
            assert!(ModrinthVersionUpload::new(changed, "file.jar".to_owned(), vec![1]).is_err());
        }
    }

    #[test]
    fn debug_output_omits_reviewed_notes_and_jar_contents() {
        let debug = format!("{:?}", upload());
        assert!(debug.contains("SFM-MC1.21-4.35.0.jar"));
        assert!(!debug.contains("Reviewed notes"));
        assert!(!debug.contains("owned JAR bytes"));
    }

    #[test]
    fn rejects_unsuccessful_or_invalid_responses_without_retry() {
        let client = client();
        for (status, body) in [
            (StatusCode::BAD_REQUEST, r#"{"id":"Created123"}"#),
            (StatusCode::CREATED, "not JSON"),
            (StatusCode::CREATED, "{}"),
            (StatusCode::CREATED, r#"{"id":""}"#),
            (StatusCode::CREATED, r#"{"id":"unsafe/id"}"#),
            (StatusCode::CREATED, r#"{"id":"Created 123"}"#),
            (StatusCode::CREATED, r#"{"id":"Created\n123"}"#),
        ] {
            let mut calls = 0;
            assert!(
                create_version_with(&client, upload(), |_| {
                    calls += 1;
                    Ok((status, body.to_owned()))
                })
                .is_err()
            );
            assert_eq!(calls, 1);
        }
    }

    #[test]
    fn returns_transport_failure_without_retry() {
        let mut calls = 0;
        let error = create_version_with(&client(), upload(), |_| {
            calls += 1;
            eyre::bail!("injected transport failure")
        })
        .unwrap_err();
        assert!(error.to_string().contains("injected transport failure"));
        assert_eq!(calls, 1);
    }
}
