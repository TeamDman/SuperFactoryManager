//! Reproducible mechanical authority benchmark, not pinned-game qualification.
use super::*;
use crate::jar_build::verification_metrics as metrics;
use std::cell::RefCell;
use std::rc::Rc;
use std::sync::Arc;
use std::time::Instant;

#[derive(facet::Facet)]
struct Sample {
    phase: String,
    remaps: usize,
    repetitions: usize,
    wall_us: u64,
    cpu_ticks: u64,
    read_chars: u64,
    source_authored_scans: u64,
    source_generated_scans: u64,
    counts: metrics::Counts,
}

fn usage() -> (u64, u64) {
    let stat = fs::read_to_string("/proc/self/stat").unwrap();
    let fields = stat
        .rsplit_once(')')
        .unwrap()
        .1
        .split_whitespace()
        .collect::<Vec<_>>();
    let ticks = fields[11].parse::<u64>().unwrap() + fields[12].parse::<u64>().unwrap();
    let io = fs::read_to_string("/proc/self/io").unwrap();
    let chars = io
        .lines()
        .find_map(|l| l.strip_prefix("rchar: "))
        .unwrap()
        .parse()
        .unwrap();
    (ticks, chars)
}

fn measure(
    phase: &str,
    remaps: usize,
    repetitions: usize,
    action: impl FnOnce() -> eyre::Result<()>,
) -> eyre::Result<Sample> {
    metrics::reset();
    let scans = Rc::new(RefCell::new((0u64, 0u64)));
    let seen = Rc::clone(&scans);
    let _scope = crate::source_projection::catalog_owned_project::source_check_observation::install(
        move |kind| {
            if kind == "authored" {
                seen.borrow_mut().0 += 1;
            }
            if kind == "recheck" {
                seen.borrow_mut().1 += 1;
            }
        },
    );
    let before = usage();
    let started = Instant::now();
    action()?;
    let after = usage();
    let (authored, generated) = *scans.borrow();
    let sample = Sample {
        phase: phase.to_owned(),
        remaps,
        repetitions,
        wall_us: started.elapsed().as_micros().try_into()?,
        cpu_ticks: after.0 - before.0,
        read_chars: after.1 - before.1,
        source_authored_scans: authored,
        source_generated_scans: generated,
        counts: metrics::snapshot(),
    };
    if let Some(output) = std::env::var_os("SFM_FORGE_BENCH_OUTPUT") {
        use std::io::Write as _;
        let mut file = fs::OpenOptions::new()
            .append(true)
            .create(true)
            .open(PathBuf::from(output).with_extension("samples.jsonl"))?;
        writeln!(file, "{}", facet_json::to_string(&sample)?)?;
    }
    eprintln!(
        "benchmark phase={phase} remaps={remaps} wall_us={}",
        sample.wall_us
    );
    Ok(sample)
}

fn fixture_store(
    root: &Path,
    kind: &str,
    index: usize,
    roles: &[&str],
    cancel: &CancellationToken,
) -> eyre::Result<Store> {
    let key = format!("sha256:{index:064x}");
    let mut store = Store::begin(root, kind, &key, roles, cancel)?;
    store.produce(
        cancel,
        |attempt| {
            roles
                .iter()
                .map(|role| {
                    let path = attempt.path.join(role);
                    fs::write(&path, vec![u8::try_from(index % 251)?; 2 * 1024 * 1024])?;
                    Ok(path)
                })
                .collect()
        },
        || Ok(()),
    )?;
    store.release_producer();
    Ok(store)
}

// Mechanical reference for the 8f gate layout, using the same full-byte
// primitives and fixture payloads. This is not execution of an old release.
fn legacy_reference_recheck(
    context: &ExecutionContext<'_>,
    owners: &[ForgePreparationInputs],
) -> eyre::Result<()> {
    eyre::ensure!(
        crate::jar_build::local_dependency_cache::environment_allows_reuse(),
        "inherited launch controls"
    );
    let image = context.held_forge_sdk_image()?;
    image.recheck(&context.cancellation_token)?; // Old retained getter.
    image.recheck(&context.cancellation_token)?; // Old explicit outer gate.
    let state = context.linux_forge.lock().unwrap();
    for (_, store, owner) in &state.preparations {
        owner.recheck(context)?;
        store.recheck(&context.cancellation_token)?;
    }
    eyre::ensure!(
        owners.len() >= state.remaps.len(),
        "reference common owners incomplete"
    );
    for ((store, artifact), owner) in state.remaps.iter().zip(owners) {
        owner.recheck(context)?;
        artifact.recheck(context)?;
        store.recheck(&context.cancellation_token)?;
    }
    Ok(())
}

#[test]
#[ignore = "Explicit representative benchmark; existing full SDK and output path required, no downloads."]
fn representative_forge_authority_benchmark() -> eyre::Result<()> {
    let home = PathBuf::from(
        std::env::var_os("SFM_FORGE_BENCH_JAVA_HOME")
            .ok_or_else(|| eyre::eyre!("benchmark requires an existing complete SDK"))?,
    );
    let output = PathBuf::from(
        std::env::var_os("SFM_FORGE_BENCH_OUTPUT")
            .ok_or_else(|| eyre::eyre!("benchmark output required"))?,
    );
    let count = std::env::var("SFM_FORGE_BENCH_REMAPS")
        .unwrap_or_else(|_| "38".to_owned())
        .parse::<usize>()?;
    eyre::ensure!(count <= 38, "bounded representative count");
    eyre::ensure!(
        crate::jar_build::local_dependency_cache::environment_allows_reuse(),
        "genuine launch controls decline benchmark admission"
    );
    let (mut fixture, slot, recipe, _) =
        super::super::named_forge_compile_cohort_tests::fixture("1.19.2")?;
    let resources = (0..1143)
        .map(|i| {
            (
                format!("authority-benchmark/{i:04}.txt"),
                format!("benchmark/authority/{i:04}.txt"),
                format!("fixture {i:04}\n{}", "x".repeat(16 * 1024)),
            )
        })
        .collect::<Vec<_>>();
    fixture.set_project_files_for(
        "1.19.2",
        resources
            .iter()
            .map(|(o, i, b)| (o.as_str(), i.as_str(), b.as_bytes(), false)),
    )?;
    let project = super::super::named_forge_compile_cohort_tests::prepared(
        &fixture, slot, "release", &recipe,
    )?;
    let source_files = project.project().receipt().files.len();
    let temporary = tempfile::tempdir()?;
    let cancel = CancellationToken::new();
    let root = super::super::native_cache_root::NativeCacheRoot::acquire_for_source(
        &temporary.path().join("native"),
        fixture.repository(),
        project.project().project_root(),
        &cancel,
    )?;
    let mut plan = super::super::tests::minimal_plan_for_paths();
    plan.minecraft_dir = project.project().project_root().to_path_buf();
    plan.identity = BuildProjectIdentity::Catalog {
        frozen: Arc::new(project),
    };
    plan.derived_cache_root = Some(root.path().to_path_buf());
    plan.java.home = Some(home.clone());
    plan.java.executable = home.join("bin/java");
    plan.java.selection = "explicit-java-home".to_owned();
    plan.java.major_version = 17;
    plan.java.version_output = fs::read_to_string(home.join("release"))?;
    plan.java.execution_identity = Some(super::super::named_sdk_identity(&plan.java)?);
    let context = super::super::all_hit_compile_tests::context(&plan);
    let extra = temporary.path().join("tool-mapping-fixture");
    fs::write(&extra, vec![7u8; 512 * 1024])?;
    let reference = std::env::var("SFM_FORGE_BENCH_REFERENCE").as_deref() == Ok("8f");
    let mut reference_owners = Vec::new();
    let mut samples = Vec::new();
    samples.push(measure("isolated_source_gate", 0, 3, || {
        for _ in 0..3 {
            context.plan.recheck_named_inputs()?;
        }
        Ok(())
    })?);
    let image = context.held_forge_sdk_image()?;
    samples.push(measure("isolated_sdk_gate", 0, 3, || {
        for _ in 0..3 {
            image.recheck(&cancel)?;
        }
        Ok(())
    })?);
    samples.push(measure("preparation_capture", count, 1, || {
        for (i, kind, roles) in [(1, "mcp", MCP_ROLES), (2, "forge", FORGE_ROLES)] {
            if reference {
                context.held_forge_sdk_image()?.recheck(&cancel)?;
            }
            let owner = ForgePreparationInputs::performance_fixture(&context, kind, &extra)?;
            let store = fixture_store(root.path(), kind, i, roles, &cancel)?;
            context
                .linux_forge
                .lock()
                .unwrap()
                .preparations
                .push((kind.to_owned(), store, owner));
        }
        let common = if reference {
            None
        } else {
            let owner = Arc::new(ForgePreparationInputs::performance_fixture(
                &context, "remap", &extra,
            )?);
            Some(bind_remap_common(&context, owner)?)
        };
        for index in 0..count {
            let owner = if reference {
                // The old capture_sdk getter performed another full image
                // recheck for each separately retained remap common owner.
                context.held_forge_sdk_image()?.recheck(&cancel)?;
                reference_owners.push(ForgePreparationInputs::performance_fixture(
                    &context, "remap", &extra,
                )?);
                reference_owners.last().unwrap()
            } else {
                common.as_ref().unwrap().as_ref()
            };
            let artifact = temporary.path().join(format!("artifact-{index}"));
            fs::write(&artifact, vec![u8::try_from(index)?; 1024 * 1024])?;
            let input = DependencyRemapArtifactInputs::performance_fixture(
                &artifact,
                owner.fingerprint(),
                &cancel,
            )?;
            let store = fixture_store(root.path(), "remap", 100 + index, REMAP_ROLES, &cancel)?;
            context
                .linux_forge
                .lock()
                .unwrap()
                .remaps
                .push((store, input));
        }
        Ok(())
    })?);
    let tail = if count > 27 {
        context.linux_forge.lock().unwrap().remaps.split_off(27)
    } else {
        Vec::new()
    };
    if count > 27 {
        samples.push(measure("complete_warm_boundary", 27, 3, || {
            for _ in 0..3 {
                if reference {
                    legacy_reference_recheck(&context, &reference_owners)?;
                } else {
                    context.recheck_compiler_image()?;
                }
            }
            Ok(())
        })?);
        samples.push(measure("current_producer_input_boundary", 27, 3, || {
            for _ in 0..3 {
                if reference {
                    legacy_reference_recheck(&context, &reference_owners)?;
                } else {
                    recheck_remap_inputs(&context)?;
                }
            }
            Ok(())
        })?);
        context.linux_forge.lock().unwrap().remaps.extend(tail);
    }
    samples.push(measure("complete_warm_boundary", count, 3, || {
        for _ in 0..3 {
            if reference {
                legacy_reference_recheck(&context, &reference_owners)?;
            } else {
                context.recheck_compiler_image()?;
            }
        }
        Ok(())
    })?);
    samples.push(measure(
        "current_producer_input_boundary",
        count,
        3,
        || {
            for _ in 0..3 {
                if reference {
                    legacy_reference_recheck(&context, &reference_owners)?;
                } else {
                    recheck_remap_inputs(&context)?;
                }
            }
            Ok(())
        },
    )?);
    let body = format!(
        "{{\"benchmark\":\"representative mechanical authority, no game production\",\"strategy\":{},\"source_files\":{source_files},\"extra_source_bytes_each\":16384,\"artifact_bytes_each\":1048576,\"export_bytes_each\":2097152,\"sdk_home\":{},\"sdk_identity\":{},\"samples\":{}}}",
        facet_json::to_string(if reference {
            "8f_gate_layout_reference"
        } else {
            "repaired"
        })?,
        facet_json::to_string(&home.display().to_string())?,
        facet_json::to_string(context.held_forge_sdk_image()?.identity())?,
        facet_json::to_string(&samples)?
    );
    fs::write(output, body)?;
    Ok(())
}
