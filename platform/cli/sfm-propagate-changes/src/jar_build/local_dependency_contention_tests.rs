//! Synthetic Windows store-protocol tests, not fresh NFRT host/admission proof.
//! Each consumer models a separate invocation with an independent token. The
//! ordinary multi-target CLI batch still shares its command cancellation token.

use super::NamedNfrtInvocationInput;
use super::PreparedNfrtInvocationInput;
use crate::artifact_lock::ArtifactLock;
use crate::cancellation::CancellationToken;
use crate::jar_build::local_dependency_cache::LocalDependencyCache;
use crate::jar_build::local_dependency_consumption::LocalDependencyConsumption;
use crate::jar_build::local_dependency_key::LocalDependencyKey;
use crate::jar_build::local_dependency_manifest::fixture_manifest;
use crate::source_projection::nfrt_producer_output_receipt::NfrtProducerOutputReceipt;
use crate::source_projection::provenance::sha256;
use eyre::Result;
use eyre::ensure;
use std::collections::BTreeMap;
use std::fs;
use std::fs::File;
use std::fs::OpenOptions;
use std::path::Path;
use std::path::PathBuf;
use std::sync::Arc;
use std::sync::Mutex;
use std::sync::atomic::AtomicUsize;
use std::sync::atomic::Ordering;
use std::sync::mpsc;
use std::thread;
use std::time::Duration;
use std::time::Instant;

const BOUND: Duration = Duration::from_secs(5);

// This private cfg(test) child can construct its parent's immutable DTO. It is
// deliberately not an admission/launch permit or a new production constructor.
fn prepared(owner: &str) -> Result<PreparedNfrtInvocationInput> {
    let original = crate::jar_build::local_dependency_key::fixture_key();
    let descriptor: (
        (String, String, String),
        (String, String, String),
        (Option<String>, NamedNfrtInvocationInput),
    ) = facet_json::from_str(&original.descriptor_json)?;
    let mut receipt = descriptor.2.1;
    receipt.invocation_id = format!("store-fixture-{owner}");
    receipt.projection_key = format!("sfm-{owner}/mc-1.20.3");
    receipt.preparation_identity = sha256(format!("source owner {owner}").as_bytes());
    receipt.original_request_catalog_identity = sha256(format!("catalog {owner}").as_bytes());
    receipt.supplement_identity = sha256(format!("supplement {owner}").as_bytes());
    ensure!(
        receipt.inputs.len() == 1
            && receipt.inputs[0].bytes == b"version".len()
            && receipt.inputs[0].full_sha256 == sha256(b"version"),
        "Synthetic input fixture changed"
    );
    Ok(PreparedNfrtInvocationInput {
        serialized: facet_json::to_string(&receipt)?,
        receipt,
        snapshots: vec![Arc::from(b"version".as_slice())],
        source_pin_origin: "synthetic-store-protocol-fixture",
    })
}

fn key(input: &PreparedNfrtInvocationInput) -> Result<LocalDependencyKey> {
    LocalDependencyKey::from_prepared(
        input,
        &sha256(b"held-sdk"),
        &sha256(b"full-sdk"),
        &sha256(b"host-implementation"),
        Some(b"access transformer"),
    )
}

fn with_trace<T>(path: &Path, run: impl FnOnce() -> Result<T>) -> Result<T> {
    let subscriber = tracing_subscriber::fmt()
        .json()
        .without_time()
        .with_ansi(false)
        .with_max_level(tracing::Level::INFO)
        .with_writer(Mutex::new(File::create(path)?))
        .finish();
    tracing::subscriber::with_default(subscriber, run)
}

fn fields(line: &str) -> Result<BTreeMap<String, facet_value::Value>> {
    let mut event: BTreeMap<String, facet_value::Value> = facet_json::from_str(line)?;
    let value = event
        .remove("fields")
        .ok_or_else(|| eyre::eyre!("Trace event omitted its fields"))?;
    Ok(facet_json::from_str(&facet_json::to_string(&value)?)?)
}

fn text<'a>(fields: &'a BTreeMap<String, facet_value::Value>, name: &str) -> Option<&'a str> {
    fields.get(name)?.as_string().map(|value| value.as_str())
}

fn wait_for_contention(log: &Path, lock: &Path) -> Result<()> {
    let started = Instant::now();
    loop {
        let contents = fs::read_to_string(log)?;
        ensure!(
            contents.len() <= 128 * 1024,
            "Fixture trace exceeded its bound"
        );
        if contents
            .lines()
            .filter_map(|line| fields(line).ok())
            .any(|row| {
                text(&row, "message") == Some("waiting_for_exclusive_lock")
                    && text(&row, "lock").is_some_and(|path| Path::new(path) == lock)
            })
        {
            return Ok(());
        }
        ensure!(
            started.elapsed() < BOUND,
            "No actual same-key lock contention observed"
        );
        thread::sleep(Duration::from_millis(5));
    }
}

fn publish_fixture(
    cache: &mut LocalDependencyCache,
    input: &PreparedNfrtInvocationInput,
    source: &Path,
    productions: &AtomicUsize,
    cancellation: &CancellationToken,
) -> Result<()> {
    fs::create_dir_all(source)?;
    let classes = source.join("classes.input");
    let sources = source.join("sources.input");
    fs::write(&classes, b"classes")?;
    fs::write(&sources, b"sources")?;
    let manifest = fixture_manifest(&key(input)?, b"classes", b"sources");
    let mut receipts: Vec<NfrtProducerOutputReceipt> =
        facet_json::from_str(&manifest.production_receipts_json)?;
    ensure!(receipts.len() == 2, "Fixture lost its exact producer pair");
    for receipt in &mut receipts {
        receipt.scope.invocation_id = input.receipt().invocation_id.clone();
        receipt.scope.prepared_contract_sha256 = input.contract_identity();
        receipt.scope.project_preparation_sha256 = input.receipt().preparation_identity.clone();
    }
    // Real publication performs typed receipt scope/hash validation, pair copying,
    // complete manifest writing and pointer commit under the real producer lock.
    cache.publish(
        input,
        &classes,
        &sources,
        &[&receipts[0], &receipts[1]],
        cancellation,
    )?;
    productions.fetch_add(1, Ordering::SeqCst);
    Ok(())
}

fn consume_pair(cache: &mut LocalDependencyCache, classes: &Path, output: &Path) -> Result<()> {
    cache.release_producer();
    ensure!(
        OpenOptions::new().write(true).open(classes).is_err(),
        "Releasing the producer lock lost the retained consumer file protection"
    );
    let sources = classes.with_file_name("sources.jar");
    ensure!(
        fs::read(classes)? == b"classes",
        "Consumer selected wrong classes"
    );
    ensure!(
        fs::read(&sources)? == b"sources",
        "Consumer selected wrong sources"
    );
    fs::create_dir_all(output)?;
    // These two disposable files are consumer outcomes, not a replacement cache
    // materialization implementation or claims about SFM compiler output.
    fs::write(output.join("classes.consumed"), fs::read(classes)?)?;
    fs::write(output.join("sources.consumed"), fs::read(sources)?)?;
    Ok(())
}

fn spawn_consumer(
    root: PathBuf,
    input: PreparedNfrtInvocationInput,
    cancellation: CancellationToken,
    log: PathBuf,
    output: PathBuf,
    productions: Arc<AtomicUsize>,
) -> (thread::JoinHandle<Result<()>>, mpsc::Receiver<()>) {
    let (completed, receiving) = mpsc::sync_channel(1);
    let worker = thread::spawn(move || {
        let result = with_trace(&log, || {
            let mut cache = LocalDependencyCache::begin(&root, key(&input)?, &cancellation)?;
            let selected = if let Some(classes) = cache.lookup(&input, &cancellation)? {
                classes
            } else {
                publish_fixture(
                    &mut cache,
                    &input,
                    &output.join("producer"),
                    &productions,
                    &cancellation,
                )?;
                cache
                    .lookup(&input, &cancellation)?
                    .ok_or_else(|| eyre::eyre!("Fresh fixture pair was not reusable"))?
            };
            consume_pair(&mut cache, &selected, &output)
        });
        let _ = completed.send(());
        result
    });
    (worker, receiving)
}

fn join_bounded(
    worker: thread::JoinHandle<Result<()>>,
    completed: &mpsc::Receiver<()>,
    cancellation: &CancellationToken,
) -> Result<Result<()>> {
    let in_time = completed.recv_timeout(BOUND).is_ok();
    if !in_time {
        cancellation.request_cancel("bounded store fixture cleanup");
    }
    let result = worker
        .join()
        .map_err(|_| eyre::eyre!("Store consumer panicked"))?;
    ensure!(in_time, "Store consumer did not complete within its bound");
    Ok(result)
}

fn assert_consumption(
    log: &Path,
    input: &PreparedNfrtInvocationInput,
    production: &PreparedNfrtInvocationInput,
    root: &Path,
) -> Result<PathBuf> {
    let contents = fs::read_to_string(log)?;
    let mut records = Vec::new();
    let expected_key = key(input)?.digest();
    for line in contents.lines() {
        let row = fields(line)?;
        if text(&row, "message")
            != Some("Verified historical dependency reuse; no fresh NFRT execution claimed")
        {
            continue;
        }
        ensure!(
            text(&row, "key") == Some(expected_key.as_str()),
            "Consumption key differed"
        );
        let record = PathBuf::from(
            text(&row, "receipt").ok_or_else(|| eyre::eyre!("Missing consumption path"))?,
        );
        ensure!(
            record.parent() == Some(root.join(&expected_key).as_path()),
            "Consumption escaped the exact key"
        );
        let receipt: LocalDependencyConsumption = facet_json::from_slice(&fs::read(&record)?)?;
        ensure!(
            receipt.schema == "sfm:local_dependency_consumption@1"
                && receipt.key == expected_key
                && receipt.production_contract_sha256 == production.contract_identity()
                && receipt.current_contract_sha256 == input.contract_identity()
                && receipt.current_preparation_identity == input.receipt().preparation_identity
                && receipt.authority == "trusted-local-developer-cache"
                && receipt.classes_bytes == b"classes".len() as u64
                && receipt.classes_sha256 == sha256(b"classes")
                && receipt.sources_bytes == b"sources".len() as u64
                && receipt.sources_sha256 == sha256(b"sources")
                && receipt.historical_reuse,
            "Consumption lost its exact current owner contract"
        );
        records.push(record);
    }
    ensure!(
        records.len() == 1,
        "Consumer must retain one attributable consumption record"
    );
    Ok(records.remove(0))
}

fn assert_outputs(outputs: &[&Path]) -> Result<()> {
    for output in outputs {
        ensure!(
            fs::read(output.join("classes.consumed"))? == b"classes",
            "Consumer classes changed"
        );
        ensure!(
            fs::read(output.join("sources.consumed"))? == b"sources",
            "Consumer sources changed"
        );
    }
    for (index, output) in outputs.iter().enumerate() {
        ensure!(
            outputs[index + 1..].iter().all(|other| output != other),
            "Consumer output owners collided"
        );
    }
    Ok(())
}

#[test]
fn exact_same_key_one_publication_has_two_attributable_consumers() -> Result<()> {
    let fixture = tempfile::tempdir()?;
    let root = fixture.path().join("cache");
    let first = prepared("one")?;
    let second = prepared("two")?;
    ensure!(
        key(&first)? == key(&second)?,
        "Owner labels changed producer identity"
    );
    ensure!(
        first.contract_identity() != second.contract_identity(),
        "Consumer contracts collided"
    );
    let producer_cancel = CancellationToken::new();
    let waiter_cancel = CancellationToken::new();
    let productions = Arc::new(AtomicUsize::new(0));
    let owner_log = fixture.path().join("owner.jsonl");
    let waiter_log = fixture.path().join("waiter.jsonl");
    fs::write(&waiter_log, b"")?;
    let first_output = fixture.path().join("consumer-one");
    let second_output = fixture.path().join("consumer-two");
    with_trace(&owner_log, || {
        let mut owner = LocalDependencyCache::begin(&root, key(&first)?, &producer_cancel)?;
        ensure!(
            owner.lookup(&first, &producer_cancel)?.is_none(),
            "Fixture was already warm"
        );
        let (worker, completed) = spawn_consumer(
            root.clone(),
            second.clone(),
            waiter_cancel.clone(),
            waiter_log.clone(),
            second_output.clone(),
            Arc::clone(&productions),
        );
        let body = (|| {
            wait_for_contention(
                &waiter_log,
                &root.join(key(&first)?.digest()).join("producer.lock"),
            )?;
            publish_fixture(
                &mut owner,
                &first,
                &fixture.path().join("source"),
                &productions,
                &producer_cancel,
            )?;
            let selected = owner
                .lookup(&first, &producer_cancel)?
                .ok_or_else(|| eyre::eyre!("Published owner pair was not reusable"))?;
            consume_pair(&mut owner, &selected, &first_output)
        })();
        owner.release_producer();
        if body.is_err() {
            waiter_cancel.request_cancel("owner fixture failed");
        }
        let waiter = join_bounded(worker, &completed, &waiter_cancel);
        body?;
        waiter??;
        Ok(())
    })?;
    ensure!(
        productions.load(Ordering::SeqCst) == 1,
        "Same key produced more than once"
    );
    assert_outputs(&[&first_output, &second_output])?;
    let first_record = assert_consumption(&owner_log, &first, &first, &root)?;
    let second_record = assert_consumption(&waiter_log, &second, &first, &root)?;
    ensure!(first_record != second_record, "Consumer records collided");
    Ok(())
}

#[test]
#[expect(
    clippy::too_many_lines,
    reason = "Keep the ordered contention/cancellation checkpoints and unconditional worker cleanup together in this bounded protocol fixture."
)]
fn cancelling_one_contended_invocation_preserves_producer_and_healthy_consumer() -> Result<()> {
    let fixture = tempfile::tempdir()?;
    let root = fixture.path().join("cache");
    let inputs = [
        prepared("producer")?,
        prepared("cancelled")?,
        prepared("healthy")?,
    ];
    ensure!(
        key(&inputs[0])? == key(&inputs[1])? && key(&inputs[0])? == key(&inputs[2])?,
        "Producer keys differed"
    );
    let command = CancellationToken::new();
    let producer_cancel = command.child_token();
    let cancelled = command.child_token();
    let healthy = command.child_token();
    let productions = Arc::new(AtomicUsize::new(0));
    let logs = [
        fixture.path().join("owner.jsonl"),
        fixture.path().join("cancelled.jsonl"),
        fixture.path().join("healthy.jsonl"),
    ];
    for log in &logs[1..] {
        fs::write(log, b"")?;
    }
    let outputs = [
        fixture.path().join("producer-consumer"),
        fixture.path().join("cancelled-consumer"),
        fixture.path().join("healthy-consumer"),
    ];
    with_trace(&logs[0], || {
        let mut owner = LocalDependencyCache::begin(&root, key(&inputs[0])?, &producer_cancel)?;
        ensure!(
            owner.lookup(&inputs[0], &producer_cancel)?.is_none(),
            "Fixture was warm"
        );
        let cancelled_worker = spawn_consumer(
            root.clone(),
            inputs[1].clone(),
            cancelled.clone(),
            logs[1].clone(),
            outputs[1].clone(),
            Arc::clone(&productions),
        );
        let healthy_worker = spawn_consumer(
            root.clone(),
            inputs[2].clone(),
            healthy.clone(),
            logs[2].clone(),
            outputs[2].clone(),
            Arc::clone(&productions),
        );
        let mut cancelled_worker = Some(cancelled_worker);
        let body = (|| {
            let lock = root.join(key(&inputs[0])?.digest()).join("producer.lock");
            wait_for_contention(&logs[1], &lock)?;
            wait_for_contention(&logs[2], &lock)?;
            cancelled.request_cancel("cancel only this contended invocation");
            let (worker, completed) = cancelled_worker
                .take()
                .ok_or_else(|| eyre::eyre!("Cancelled worker was already joined"))?;
            let terminal = join_bounded(worker, &completed, &cancelled)?;
            let error = terminal
                .err()
                .ok_or_else(|| eyre::eyre!("Cancelled waiter unexpectedly succeeded"))?;
            ensure!(
                error
                    .to_string()
                    .contains("cancel only this contended invocation"),
                "Wrong cancellation cause"
            );
            ensure!(
                !command.is_cancelled()
                    && !producer_cancel.is_cancelled()
                    && !healthy.is_cancelled(),
                "Waiter cancellation escaped its invocation"
            );
            ensure!(
                ArtifactLock::try_acquire(&lock, "retained producer witness")?.is_none(),
                "Cancellation released another producer's lock"
            );
            ensure!(
                productions.load(Ordering::SeqCst) == 0
                    && !lock.with_file_name("current.json").exists()
                    && !outputs[1].exists(),
                "Cancelled waiter published or consumed before the producer resumed"
            );
            publish_fixture(
                &mut owner,
                &inputs[0],
                &fixture.path().join("source"),
                &productions,
                &producer_cancel,
            )?;
            let selected = owner
                .lookup(&inputs[0], &producer_cancel)?
                .ok_or_else(|| eyre::eyre!("Owner lost its published pair"))?;
            consume_pair(&mut owner, &selected, &outputs[0])
        })();
        cancelled.request_cancel("bounded fixture cleanup");
        owner.release_producer();
        if body.is_err() {
            healthy.request_cancel("owner fixture failed");
        }
        let cancelled_cleanup = cancelled_worker
            .map(|(worker, completed)| join_bounded(worker, &completed, &cancelled));
        let healthy_result = join_bounded(healthy_worker.0, &healthy_worker.1, &healthy);
        body?;
        if let Some(result) = cancelled_cleanup {
            let _ = result?;
        }
        healthy_result??;
        Ok(())
    })?;
    ensure!(
        productions.load(Ordering::SeqCst) == 1,
        "Cancelled waiter changed production count"
    );
    ensure!(
        !outputs[1].exists(),
        "Cancelled waiter wrote a consumer outcome"
    );
    assert_outputs(&[&outputs[0], &outputs[2]])?;
    let owner_record = assert_consumption(&logs[0], &inputs[0], &inputs[0], &root)?;
    let healthy_record = assert_consumption(&logs[2], &inputs[2], &inputs[0], &root)?;
    ensure!(
        owner_record != healthy_record,
        "Healthy consumers shared a record"
    );
    ensure!(
        !fs::read_to_string(&logs[1])?.contains("Verified historical dependency reuse"),
        "Cancelled waiter consumed a pair"
    );
    Ok(())
}
