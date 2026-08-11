use crate::protocol::{
    CONTROL_PROTOCOL_VERSION, SfmControlClient, SfmControlDescribeRequest,
    SfmControlDescribeResult, SfmControlPingRequest, SfmGameInstanceDescriptor,
};
use directories_next::BaseDirs;
use eyre::Context as _;
use facet::Facet;
use std::path::{Path, PathBuf};
use std::sync::Arc;
use std::time::Duration;
use tokio::sync::Semaphore;
use tokio::task::JoinSet;

const MAX_DESCRIPTOR_BYTES: u64 = 16 * 1024;
const MAX_DESCRIPTOR_FILES: usize = 256;
const MAX_CONCURRENT_PROBES: usize = 16;
const PROBE_TIMEOUT: Duration = Duration::from_secs(2);

#[derive(Clone, Copy, Debug, PartialEq, Eq, Facet)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub enum InstanceHealth {
    Live,
    Incompatible,
    Stale,
    Unreachable,
    Malformed,
}

#[derive(Clone, Debug, Facet)]
pub(crate) struct DiscoveredInstanceOutput {
    pub descriptor_path: String,
    pub health: InstanceHealth,
    pub detail: String,
    pub default_target: bool,
    pub instance_id: String,
    pub process_id: u32,
    pub sfm_version: String,
    pub minecraft_version: String,
    pub focused: bool,
    pub focus_seen: bool,
    pub last_focus_epoch_millis: i64,
    pub current_screen: String,
    pub world_label: String,
    pub capabilities: Vec<String>,
}

#[derive(Clone, Debug)]
pub(crate) struct LiveInstance {
    pub descriptor: SfmGameInstanceDescriptor,
    pub description: SfmControlDescribeResult,
}

#[derive(Debug)]
pub(crate) struct DiscoveryRecord {
    pub output: DiscoveredInstanceOutput,
    pub live: Option<LiveInstance>,
}

#[derive(Debug)]
pub(crate) struct DiscoverySnapshot {
    pub records: Vec<DiscoveryRecord>,
}

pub(crate) fn instance_descriptor_dir() -> eyre::Result<PathBuf> {
    let base = BaseDirs::new()
        .ok_or_else(|| eyre::eyre!("local application-data directory unavailable"))?;
    Ok(base
        .data_local_dir()
        .join("teamdman")
        .join("sfm")
        .join("instances"))
}

pub(crate) fn read_descriptor(path: &Path) -> eyre::Result<SfmGameInstanceDescriptor> {
    let metadata = std::fs::metadata(path)
        .wrap_err_with(|| format!("failed to stat instance descriptor {}", path.display()))?;
    eyre::ensure!(
        metadata.len() <= MAX_DESCRIPTOR_BYTES,
        "instance descriptor is oversized"
    );
    let text = std::fs::read_to_string(path)
        .wrap_err_with(|| format!("failed to read instance descriptor {}", path.display()))?;
    let descriptor: SfmGameInstanceDescriptor = facet_json::from_str(&text)
        .wrap_err_with(|| format!("failed to decode instance descriptor {}", path.display()))?;
    descriptor.validate()?;
    Ok(descriptor)
}

pub(crate) async fn discover_instances() -> eyre::Result<DiscoverySnapshot> {
    let directory = instance_descriptor_dir()?;
    let mut paths = if directory.exists() {
        std::fs::read_dir(&directory)
            .wrap_err_with(|| format!("failed to enumerate {}", directory.display()))?
            .filter_map(Result::ok)
            .map(|entry| entry.path())
            .filter(|path| {
                path.extension()
                    .is_some_and(|extension| extension == "json")
            })
            .collect::<Vec<_>>()
    } else {
        Vec::new()
    };
    paths.sort();
    eyre::ensure!(
        paths.len() <= MAX_DESCRIPTOR_FILES,
        "instance descriptor count exceeds {MAX_DESCRIPTOR_FILES}"
    );

    let capacity = Arc::new(Semaphore::new(MAX_CONCURRENT_PROBES));
    let mut probes = JoinSet::new();
    for path in paths {
        let capacity = Arc::clone(&capacity);
        probes.spawn(async move {
            let _permit = capacity
                .acquire_owned()
                .await
                .map_err(|_| eyre::eyre!("instance probe semaphore closed"))?;
            Ok::<_, eyre::Report>(probe_path(path).await)
        });
    }

    let mut records = Vec::new();
    while let Some(joined) = probes.join_next().await {
        records.push(joined.wrap_err("instance probe task failed")??);
    }
    records.sort_by(|left, right| {
        right
            .output
            .last_focus_epoch_millis
            .cmp(&left.output.last_focus_epoch_millis)
            .then_with(|| left.output.instance_id.cmp(&right.output.instance_id))
            .then_with(|| {
                left.output
                    .descriptor_path
                    .cmp(&right.output.descriptor_path)
            })
    });
    Ok(DiscoverySnapshot { records })
}

async fn probe_path(path: PathBuf) -> DiscoveryRecord {
    let descriptor = match read_descriptor(&path) {
        Ok(descriptor) => descriptor,
        Err(failure) => {
            return DiscoveryRecord {
                output: empty_output(&path, InstanceHealth::Malformed, format!("{failure:#}")),
                live: None,
            };
        }
    };

    let probed = tokio::time::timeout(PROBE_TIMEOUT, probe_descriptor(descriptor.clone())).await;
    match probed {
        Ok(Ok(description)) => {
            let compatible = descriptor.protocol_version == CONTROL_PROTOCOL_VERSION
                && description.protocol_version == CONTROL_PROTOCOL_VERSION;
            let health = if compatible {
                InstanceHealth::Live
            } else {
                InstanceHealth::Incompatible
            };
            let output = output_from_live(&path, health, &description);
            DiscoveryRecord {
                output,
                live: compatible.then_some(LiveInstance {
                    descriptor,
                    description,
                }),
            }
        }
        Ok(Err(failure)) => DiscoveryRecord {
            output: failed_probe_output(&path, &descriptor, format!("{failure:#}")),
            live: None,
        },
        Err(_) => DiscoveryRecord {
            output: failed_probe_output(
                &path,
                &descriptor,
                format!("probe timed out after {} ms", PROBE_TIMEOUT.as_millis()),
            ),
            live: None,
        },
    }
}

pub(crate) async fn probe_descriptor(
    descriptor: SfmGameInstanceDescriptor,
) -> eyre::Result<SfmControlDescribeResult> {
    let endpoint = format!("{}:{}", descriptor.host, descriptor.port);
    let connection = vox::connect(&endpoint)
        .connect_timeout(PROBE_TIMEOUT)
        .establish()
        .await
        .map_err(|failure| eyre::eyre!("connecting to {endpoint} failed: {failure:?}"))?;
    let client: SfmControlClient = connection
        .open_lane()
        .await
        .map_err(|failure| eyre::eyre!("opening SFM control lane failed: {failure:?}"))?;
    let request_id = uuid_like_request_id();
    let ping = client
        .ping(SfmControlPingRequest {
            authentication_token: descriptor.authentication_token.clone(),
            request_id: request_id.clone(),
            protocol_version: CONTROL_PROTOCOL_VERSION,
        })
        .await
        .map_err(|failure| eyre::eyre!("SFM control ping failed: {failure:?}"))?;
    eyre::ensure!(ping.request_id == request_id, "ping request id mismatch");
    eyre::ensure!(
        ping.instance_id == descriptor.instance_id,
        "instance id mismatch"
    );
    eyre::ensure!(
        ping.process_id == descriptor.process_id,
        "process id mismatch"
    );
    eyre::ensure!(
        ping.process_start_nonce == descriptor.process_start_nonce,
        "process start nonce mismatch"
    );

    let request_id = uuid_like_request_id();
    let description = client
        .describe(SfmControlDescribeRequest {
            authentication_token: descriptor.authentication_token.clone(),
            request_id: request_id.clone(),
            protocol_version: CONTROL_PROTOCOL_VERSION,
        })
        .await
        .map_err(|failure| eyre::eyre!("SFM control describe failed: {failure:?}"))?;
    eyre::ensure!(
        description.request_id == request_id,
        "describe request id mismatch"
    );
    eyre::ensure!(
        description.instance_id == descriptor.instance_id,
        "describe instance id mismatch"
    );
    eyre::ensure!(
        description.process_id == descriptor.process_id,
        "describe process id mismatch"
    );
    eyre::ensure!(
        description.process_start_nonce == descriptor.process_start_nonce,
        "describe process start nonce mismatch"
    );
    let _ = connection.shutdown();
    Ok(description)
}

pub(crate) fn select_instance(
    snapshot: &DiscoverySnapshot,
    explicit_process_id: Option<u32>,
    explicit_instance_key: Option<&str>,
) -> eyre::Result<LiveInstance> {
    eyre::ensure!(
        explicit_process_id.is_none() || explicit_instance_key.is_none(),
        "use only one of --instance-pid or --instance-id"
    );
    let live = snapshot
        .records
        .iter()
        .filter_map(|record| record.live.as_ref())
        .collect::<Vec<_>>();

    if let Some(expected_id) = explicit_instance_key {
        let matches = live
            .into_iter()
            .filter(|instance| instance.descriptor.instance_id == expected_id)
            .collect::<Vec<_>>();
        return exact_match(&matches, "instance id", expected_id);
    }
    if let Some(expected_pid) = explicit_process_id {
        let matches = live
            .into_iter()
            .filter(|instance| instance.descriptor.process_id == expected_pid)
            .collect::<Vec<_>>();
        return exact_match(&matches, "process id", &expected_pid.to_string());
    }
    let responsive = snapshot
        .records
        .iter()
        .filter(|record| {
            matches!(
                record.output.health,
                InstanceHealth::Live | InstanceHealth::Incompatible
            )
        })
        .collect::<Vec<_>>();
    match responsive.as_slice() {
        [] => eyre::bail!("no compatible live SFM Minecraft instances; run `sfm instance list`"),
        [only] => select_responsive_record(only),
        _ => {
            let latest = responsive
                .iter()
                .map(|record| record.output.last_focus_epoch_millis)
                .max()
                .unwrap_or_default();
            eyre::ensure!(
                latest > 0,
                "multiple live instances have no focus history; run `sfm instance list` and select one explicitly"
            );
            let latest_records = responsive
                .into_iter()
                .filter(|record| record.output.last_focus_epoch_millis == latest)
                .collect::<Vec<_>>();
            eyre::ensure!(
                latest_records.len() == 1,
                "multiple live instances tie for most recent focus; run `sfm instance list` and select one explicitly"
            );
            select_responsive_record(latest_records[0])
        }
    }
}

fn select_responsive_record(record: &DiscoveryRecord) -> eyre::Result<LiveInstance> {
    if record.output.health == InstanceHealth::Incompatible {
        eyre::bail!(
            "most recently focused live instance {} uses an incompatible control protocol; run `sfm instance list` and select explicitly after upgrading it",
            record.output.instance_id
        );
    }
    record
        .live
        .clone()
        .ok_or_else(|| eyre::eyre!("live instance record omitted its verified identity"))
}

fn exact_match(matches: &[&LiveInstance], kind: &str, value: &str) -> eyre::Result<LiveInstance> {
    match matches {
        [only] => Ok((*only).clone()),
        [] => eyre::bail!(
            "no compatible live instance matches {kind} {value}; run `sfm instance list`"
        ),
        _ => eyre::bail!(
            "multiple live instances match {kind} {value}; use --instance-id after `sfm instance list`"
        ),
    }
}

pub(crate) fn mark_default(snapshot: &mut DiscoverySnapshot, selected: Option<&LiveInstance>) {
    let selected_id = selected.map(|instance| instance.descriptor.instance_id.as_str());
    for record in &mut snapshot.records {
        record.output.default_target =
            selected_id.is_some_and(|instance_id| record.output.instance_id == instance_id);
    }
}

fn output_from_live(
    path: &Path,
    health: InstanceHealth,
    description: &SfmControlDescribeResult,
) -> DiscoveredInstanceOutput {
    DiscoveredInstanceOutput {
        descriptor_path: path.display().to_string(),
        health,
        detail: String::new(),
        default_target: false,
        instance_id: description.instance_id.clone(),
        process_id: description.process_id,
        sfm_version: description.sfm_version.clone(),
        minecraft_version: description.minecraft_version.clone(),
        focused: description.focused,
        focus_seen: description.focus_seen,
        last_focus_epoch_millis: description.last_focus_epoch_millis,
        current_screen: description.current_screen.clone(),
        world_label: description.world_label.clone(),
        capabilities: description.capabilities.clone(),
    }
}

fn descriptor_output(
    path: &Path,
    descriptor: &SfmGameInstanceDescriptor,
    health: InstanceHealth,
    detail: String,
) -> DiscoveredInstanceOutput {
    let mut output = empty_output(path, health, detail);
    output.instance_id.clone_from(&descriptor.instance_id);
    output.process_id = descriptor.process_id;
    output
}

fn failed_probe_output(
    path: &Path,
    descriptor: &SfmGameInstanceDescriptor,
    detail: String,
) -> DiscoveredInstanceOutput {
    let stale = process_is_running(descriptor.process_id) == Some(false);
    let health = if stale {
        remove_unchanged_stale_descriptor(path, descriptor);
        InstanceHealth::Stale
    } else {
        InstanceHealth::Unreachable
    };
    descriptor_output(path, descriptor, health, detail)
}

fn remove_unchanged_stale_descriptor(path: &Path, expected: &SfmGameInstanceDescriptor) {
    if process_is_running(expected.process_id) != Some(false) {
        return;
    }
    if read_descriptor(path).ok().as_ref() != Some(expected) {
        return;
    }
    let _ = std::fs::remove_file(path);
}

#[cfg(windows)]
fn process_is_running(process_id: u32) -> Option<bool> {
    use windows::Win32::Foundation::{CloseHandle, E_ACCESSDENIED, E_INVALIDARG};
    use windows::Win32::System::Threading::{
        GetExitCodeProcess, OpenProcess, PROCESS_QUERY_LIMITED_INFORMATION,
    };

    let handle = unsafe { OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, false, process_id) };
    let handle = match handle {
        Ok(handle) => handle,
        Err(error) if error.code() == E_INVALIDARG => return Some(false),
        Err(error) if error.code() == E_ACCESSDENIED => return None,
        Err(_) => return None,
    };
    let mut exit_code = 0_u32;
    let queried = unsafe { GetExitCodeProcess(handle, &raw mut exit_code) }.is_ok();
    let _ = unsafe { CloseHandle(handle) };
    queried.then_some(exit_code == 259)
}

#[cfg(not(windows))]
fn process_is_running(_process_id: u32) -> Option<bool> {
    None
}

fn empty_output(path: &Path, health: InstanceHealth, detail: String) -> DiscoveredInstanceOutput {
    DiscoveredInstanceOutput {
        descriptor_path: path.display().to_string(),
        health,
        detail,
        default_target: false,
        instance_id: String::new(),
        process_id: 0,
        sfm_version: String::new(),
        minecraft_version: String::new(),
        focused: false,
        focus_seen: false,
        last_focus_epoch_millis: 0,
        current_screen: String::new(),
        world_label: String::new(),
        capabilities: Vec::new(),
    }
}

#[must_use]
pub(crate) fn uuid_like_request_id() -> String {
    use std::time::{SystemTime, UNIX_EPOCH};
    let nanos = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map_or(0, |duration| duration.as_nanos());
    format!("sfm-cli-{}-{nanos}", std::process::id())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::protocol::INSTANCE_DESCRIPTOR_SCHEMA;

    fn descriptor(instance_id: &str, process_id: u32) -> SfmGameInstanceDescriptor {
        SfmGameInstanceDescriptor {
            schema: INSTANCE_DESCRIPTOR_SCHEMA.to_owned(),
            protocol_version: CONTROL_PROTOCOL_VERSION,
            instance_id: instance_id.to_owned(),
            process_id,
            process_start_nonce: format!("start-{instance_id}"),
            transport: "tcp".to_owned(),
            host: "127.0.0.1".to_owned(),
            port: 51_234,
            authentication_token: "secret".to_owned(),
            created_at_epoch_millis: 1,
        }
    }

    #[test]
    fn descriptor_round_trips_from_bounded_json() {
        let dir = tempfile::tempdir().expect("temp dir");
        let path = dir.path().join("instance.json");
        let descriptor = descriptor("instance-1", 42);
        std::fs::write(
            &path,
            facet_json::to_string_pretty(&descriptor).expect("JSON"),
        )
        .expect("write descriptor");
        assert_eq!(read_descriptor(&path).expect("read descriptor"), descriptor);
    }

    #[test]
    fn explicit_selection_never_falls_back() {
        let live = live_instance("instance-1", 42, 100);
        let snapshot = DiscoverySnapshot {
            records: vec![record(live)],
        };
        assert!(select_instance(&snapshot, Some(999), None).is_err());
        assert!(select_instance(&snapshot, None, Some("missing")).is_err());
    }

    #[test]
    fn default_selection_requires_unique_latest_focus() {
        let first = live_instance("a", 1, 100);
        let second = live_instance("b", 2, 100);
        let snapshot = DiscoverySnapshot {
            records: vec![record(first), record(second)],
        };
        assert!(select_instance(&snapshot, None, None).is_err());
    }

    #[test]
    fn newest_incompatible_instance_blocks_fallback_to_older_game() {
        let older = live_instance("older", 1, 100);
        let newer = live_instance("newer", 2, 200);
        let mut incompatible = record(newer);
        incompatible.output.health = InstanceHealth::Incompatible;
        incompatible.live = None;
        let snapshot = DiscoverySnapshot {
            records: vec![record(older), incompatible],
        };
        let failure = select_instance(&snapshot, None, None).expect_err("must not fall back");
        assert!(format!("{failure:#}").contains("incompatible"));
    }

    #[cfg(windows)]
    #[test]
    fn dead_process_descriptor_is_classified_stale_and_removed_if_unchanged() {
        let dir = tempfile::tempdir().expect("temp dir");
        let path = dir.path().join("stale.json");
        let descriptor = descriptor("stale", u32::MAX);
        std::fs::write(
            &path,
            facet_json::to_string_pretty(&descriptor).expect("JSON"),
        )
        .expect("write descriptor");

        let output = failed_probe_output(&path, &descriptor, "unreachable".to_owned());
        assert_eq!(output.health, InstanceHealth::Stale);
        assert!(!path.exists());
    }

    fn live_instance(instance_id: &str, process_id: u32, focus: i64) -> LiveInstance {
        LiveInstance {
            descriptor: descriptor(instance_id, process_id),
            description: SfmControlDescribeResult {
                instance_id: instance_id.to_owned(),
                process_id,
                process_start_nonce: format!("start-{instance_id}"),
                protocol_version: CONTROL_PROTOCOL_VERSION,
                sfm_version: "test".to_owned(),
                minecraft_version: "1.19.2".to_owned(),
                lifecycle: crate::protocol::SfmControlLifecycle::Ready,
                focused: false,
                focus_seen: focus > 0,
                last_focus_epoch_millis: focus,
                current_screen_present: false,
                current_screen: String::new(),
                world_present: false,
                world_label: String::new(),
                capabilities: vec!["client-action.invoke".to_owned()],
                request_id: "request".to_owned(),
            },
        }
    }

    fn record(live: LiveInstance) -> DiscoveryRecord {
        let descriptor_path = PathBuf::from(format!("{}.json", live.descriptor.instance_id));
        DiscoveryRecord {
            output: output_from_live(&descriptor_path, InstanceHealth::Live, &live.description),
            live: Some(live),
        }
    }
}
