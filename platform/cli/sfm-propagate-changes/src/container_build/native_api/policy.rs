use super::api_namespace::ApiNamespace;
use super::api_progress::ApiProgress;
use std::collections::BTreeMap;
pub(super) const VERSION: &str = "6.0.2";
pub(super) const MEMORY: u64 = 6 * 1024 * 1024 * 1024;
pub(super) fn query(offline: bool, tag: &str) -> eyre::Result<String> {
    let namespaces = ["network", "pid", "ipc", "uts"].map(|name| ApiNamespace {
        name: name.into(),
        host: false,
        path: String::new(),
    });
    let mut fields = BTreeMap::from([
        ("dockerfile", "Dockerfile".to_owned()),
        ("t", tag.to_owned()),
        (
            "networkmode",
            if offline {
                "NetworkDisabled"
            } else {
                "NetworkEnabled"
            }
            .into(),
        ),
        ("nsoptions", facet_json::to_string(&namespaces)?),
        ("isolation", "oci".into()),
        ("jobs", "1".into()),
        ("layers", "true".into()),
        ("nocache", "false".into()),
        ("memory", MEMORY.to_string()),
        ("memswap", MEMORY.to_string()),
        ("cpuperiod", "100000".into()),
        ("cpuquota", "200000".into()),
        ("dropcaps", "[\"ALL\"]".into()),
        ("ulimits", "[\"nproc=512:512\"]".into()),
        (
            "pullpolicy",
            if offline { "never" } else { "missing" }.into(),
        ),
        ("rm", "true".into()),
        ("forcerm", "true".into()),
        ("httpproxy", "false".into()),
        (
            "outputformat",
            "application/vnd.oci.image.manifest.v1+json".into(),
        ),
    ]);
    // Deliberately no remote context, mounts, devices, secrets, credentials or security overrides.
    let mut url = reqwest::Url::parse("http://localhost/")?;
    url.query_pairs_mut()
        .extend_pairs(fields.iter_mut().map(|(key, value)| (*key, value.as_str())));
    Ok(url.query().unwrap_or_default().to_owned())
}
pub(super) fn progress(bytes: &[u8]) -> eyre::Result<()> {
    let row: ApiProgress = facet_json::from_slice(bytes)?;
    if let Some(error) = row.error {
        eyre::bail!("engine build error: {error}");
    }
    if let Some(detail) = row.error_detail {
        eyre::bail!(
            "engine build errorDetail: {}",
            detail.message.unwrap_or_else(|| "unspecified error".into())
        );
    }
    if let Some(stream) = row.stream {
        eprint!("{stream}");
    }
    Ok(())
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn native_query_explicitly_disables_host_namespaces_and_offline_network() {
        let url = reqwest::Url::parse(&format!(
            "http://localhost/?{}",
            query(true, "sfm:test").unwrap()
        ))
        .unwrap();
        let fields = url.query_pairs().collect::<BTreeMap<_, _>>();
        assert_eq!(fields["networkmode"], "NetworkDisabled");
        assert_eq!(fields["pullpolicy"], "never");
        assert_eq!(fields["layers"], "true");
        assert_eq!(fields["nocache"], "false");
        let ns: Vec<ApiNamespace> = facet_json::from_str(&fields["nsoptions"]).unwrap();
        assert!(ns.iter().all(|value| !value.host && value.path.is_empty()));
        assert!(ns.iter().any(|value| value.name == "network"));
        for denied in [
            "volume",
            "devices",
            "transientRunMounts",
            "secrets",
            "remote",
            "securityopt",
            "seccomp",
        ] {
            assert!(!fields.contains_key(denied));
        }
    }
    #[test]
    fn errors_inside_http_200_progress_are_failures() {
        assert!(progress(br#"{"error":"failed"}"#).is_err());
        assert!(progress(br#"{"errorDetail":{"message":"failed"}}"#).is_err());
        assert!(progress(br#"{"stream":""}"#).is_ok());
    }
}
