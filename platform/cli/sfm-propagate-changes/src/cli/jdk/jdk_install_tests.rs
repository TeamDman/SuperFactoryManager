use super::*;
use crate::cli::jdk::{JdkArgs, JdkCommand};
use crate::source_projection::catalog_owned_project::tests::Fixture;

fn published_sdk_project(fixture: &Fixture) -> eyre::Result<PathBuf> {
    let catalog = CoreCatalog::load(fixture.repository(), fixture.repository())?;
    let key = catalog
        .catalog
        .0
        .iter()
        .find(|(_, entry)| {
            entry.minecraft_version == "26.1.2"
                && entry.environment
                    == crate::source_projection::projection_catalog::ProjectionEnvironment::Dev
        })
        .map(|(key, _)| key)
        .expect("fixture development projection");
    Ok(fixture.publish(key))
}

#[test]
fn public_sdk_install_dispatch_returns_verified_offline_home_without_running_java()
-> eyre::Result<()> {
    use sha2::Digest as _;
    let made = std::process::Command::new("python3").args(["-I", "-S", "-c", r#"import io,sys,tarfile
b=io.BytesIO()
with tarfile.open(fileobj=b,mode='w:gz') as t:
 for name,data,mode in [('sdk/bin/java',b'not executable Java',0o755),('sdk/bin/javac',b'javac',0o755),('sdk/lib/modules',b'modules',0o644)]:
  i=tarfile.TarInfo(name);i.size=len(data);i.mode=mode;t.addfile(i,io.BytesIO(data))
sys.stdout.buffer.write(b.getvalue())"#]).output()?;
    assert!(made.status.success());
    let sha = format!("{:x}", sha2::Sha512::digest(&made.stdout));
    let original = include_str!(
        "../../../../../minecraft/core-liquid-template/build/lockfiles/26.1.2/schema-4.json"
    );
    let pins = read_jdk_pins(original)?.expect("fixture pins");
    let old_sha = &pins[0]
        .artifacts
        .iter()
        .find(|a| a.platform == "linux-x64")
        .expect("Linux pin")
        .sha512;
    let lock = original.replace(old_sha, &sha);
    let mut fixture = Fixture::new_for_target("26.1.2");
    fixture.set_project_file_for(
        "26.1.2",
        "sfm-toolchain.lock.json",
        "build/proof/sdk-pin.json",
        lock.as_bytes(),
        false,
    )?;
    let project = published_sdk_project(&fixture)?;
    let external = tempfile::tempdir()?;
    let root = external.path().join("sdk");
    let archives = root.join("minecraft-toolchain/jbrsdk/archives");
    std::fs::create_dir_all(&archives)?;
    std::fs::write(archives.join(format!("{sha}.tar.gz")), &made.stdout)?;
    let command = JdkArgs {
        command: JdkCommand::Install(JdkInstallArgs {
            project_root: project,
            major: 25,
            cache_root: root.clone(),
            offline: true,
            extract_bytes_per_second: Some(2097152),
        }),
    };
    let output = command.invoke_in(&CancellationToken::new(), fixture.repository())?;
    let json = output
        .render(Some(crate::cli::output::OutputFormat::Json), false)?
        .expect("SDK report");
    assert!(json.contains("sfm:managed_sdk_install@1"));
    assert!(json.contains("not_run"));
    assert!(json.contains(&sha));
    assert!(
        root.join("minecraft-toolchain/jbrsdk/installs")
            .read_dir()?
            .count()
            == 2
    );
    assert!(!root.join(".sfm-native-cache-root.json").exists());
    assert!(!root.join("forge-linux-v1").exists());
    assert!(!root.join("projects").exists());
    Ok(())
}

#[test]
fn parses_explicit_sdk_only_install_controls() -> eyre::Result<()> {
    let parsed = figue::from_slice::<JdkArgs>(&[
        "install",
        "--project-root",
        "/project",
        "--major",
        "25",
        "--cache-root",
        "/sdk-cache",
        "--offline",
        "--extract-bytes-per-second",
        "2097152",
    ])
    .into_result()?
    .get_silent();
    let JdkCommand::Install(args) = parsed.command else {
        panic!("SDK install command")
    };
    assert_eq!(args.major, 25);
    assert!(args.offline);
    assert_eq!(args.extract_bytes_per_second, Some(2097152));
    for missing in [
        vec!["install"],
        vec!["install", "--project-root", "/project", "--major", "25"],
    ] {
        assert!(
            figue::from_slice::<JdkArgs>(&missing)
                .into_result()
                .is_err()
        );
    }
    Ok(())
}

#[test]
fn sdk_only_install_checks_source_pin_root_rate_and_cancellation_before_cache_effects()
-> eyre::Result<()> {
    let mut fixture = Fixture::new_for_target("26.1.2");
    fixture.set_project_file_for(
        "26.1.2",
        "sfm-toolchain.lock.json",
        "build/proof/sdk-pin.json",
        include_bytes!(
            "../../../../../minecraft/core-liquid-template/build/lockfiles/26.1.2/schema-4.json"
        ),
        false,
    )?;
    let project = published_sdk_project(&fixture)?;
    let external = tempfile::tempdir()?;
    let mut args = JdkInstallArgs {
        project_root: project.clone(),
        major: 25,
        cache_root: external.path().join("sdk"),
        offline: true,
        extract_bytes_per_second: None,
    };
    let cancel = CancellationToken::new();
    let original_root = args.cache_root.clone();
    args.cache_root = PathBuf::from("/");
    let root_error = args
        .install(&cancel, fixture.repository())
        .expect_err("root is not an SDK cache");
    assert!(root_error.to_string().contains("absolute non-root path"));
    assert!(!original_root.exists());
    args.cache_root = original_root;
    let error = args
        .install(&cancel, fixture.repository())
        .expect_err("offline archive is absent");
    assert!(format!("{error:#}").contains("offline mode forbids downloading"));
    assert!(
        !args
            .cache_root
            .join("minecraft-toolchain/jbrsdk/installs")
            .read_dir()?
            .any(|entry| entry.is_ok())
    );
    args.cache_root = external.path().join("must-not-create");
    args.extract_bytes_per_second = Some(0);
    assert!(args.install(&cancel, fixture.repository()).is_err());
    assert!(!args.cache_root.exists());
    args.extract_bytes_per_second = None;
    args.major = 21;
    assert!(args.install(&cancel, fixture.repository()).is_err());
    assert!(!args.cache_root.exists());
    args.major = 25;
    std::fs::write(project.join("sfm-toolchain.lock.json"), "changed")?;
    assert!(args.install(&cancel, fixture.repository()).is_err());
    assert!(!args.cache_root.exists());
    std::fs::write(
        project.join("sfm-toolchain.lock.json"),
        include_bytes!(
            "../../../../../minecraft/core-liquid-template/build/lockfiles/26.1.2/schema-4.json"
        ),
    )?;
    args.cache_root = fixture.repository().join("must-not-create-sdk");
    assert!(args.install(&cancel, fixture.repository()).is_err());
    assert!(!args.cache_root.exists());
    cancel.request_cancel("test cancelled");
    assert!(args.install(&cancel, fixture.repository()).is_err());
    Ok(())
}

#[test]
fn external_sdk_root_refuses_relative_linked_or_native_roots() -> eyre::Result<()> {
    let temp = tempfile::tempdir()?;
    let repo = temp.path().join("repo");
    std::fs::create_dir(&repo)?;
    assert!(external_root(Path::new("relative"), &repo).is_err());
    assert!(external_root(Path::new("/"), &repo).is_err());
    assert!(external_root(&temp.path().join("repo/sdk"), &repo).is_err());
    assert!(external_root(temp.path(), &repo).is_err());
    let root = temp.path().join("native");
    std::fs::create_dir(&root)?;
    std::fs::write(root.join(".sfm-native-cache-root.json"), "retained marker")?;
    assert!(external_root(&root.join("sdk"), &repo).is_err());
    assert_eq!(
        std::fs::read(root.join(".sfm-native-cache-root.json"))?,
        b"retained marker"
    );
    let sdk_home = temp.path().join("existing-sdk");
    std::fs::create_dir(&sdk_home)?;
    std::fs::write(
        sdk_home.join(".sfm-jbrsdk-receipt"),
        "preserved SDK receipt",
    )?;
    assert!(external_root(&sdk_home.join("nested-cache"), &repo).is_err());
    assert!(!sdk_home.join("nested-cache").exists());
    for nested in [
        "minecraft-toolchain",
        "minecraft-toolchain/jbrsdk",
        "minecraft-toolchain/jbrsdk/archives",
        "minecraft-toolchain/jbrsdk/installs",
        "minecraft-toolchain/jbrsdk/locks",
    ] {
        let external = temp.path().join(nested.replace('/', "-"));
        let marked = external.join(nested);
        std::fs::create_dir_all(&marked)?;
        std::fs::write(marked.join(".sfm-native-cache-root.json"), "nested marker")?;
        assert!(external_cache(&external.join("minecraft-toolchain/jbrsdk"), &repo).is_err());
    }
    #[cfg(unix)]
    {
        let link = temp.path().join("link");
        std::os::unix::fs::symlink(&root, &link)?;
        assert!(external_root(&link.join("sdk"), &repo).is_err());
    }
    Ok(())
}
