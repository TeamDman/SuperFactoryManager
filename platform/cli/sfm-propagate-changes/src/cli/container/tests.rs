use crate::cli::Cli;
use crate::container_build::ContainerEngine;

#[test]
fn container_build_parses_typed_offline_defaults() {
    let cli = figue::from_slice::<Cli>(&[
        "container",
        "build",
        "--projection",
        "sfm-dev/mc-1.19.2",
        "--context",
        "new-context",
        "--prepare-only",
    ])
    .into_result()
    .expect("CLI should parse")
    .get_silent();
    let crate::cli::Command::Container(args) = cli.command else {
        panic!("wrong command");
    };
    let super::ContainerCommand::Build(args) = args.command else {
        panic!("expected build command");
    };
    assert!(!args.offline && args.prepare_only);
    assert_eq!(args.engine, ContainerEngine::Podman);
    assert_eq!(args.platform, "linux/amd64");
}

#[test]
fn container_build_rejects_branch_and_requires_projection() {
    for args in [
        vec!["container", "build", "--context", "x"],
        vec![
            "container",
            "build",
            "--projection",
            "sfm-dev/mc-1.19.2",
            "--context",
            "x",
            "--branch",
            "1.19.2",
        ],
    ] {
        assert!(figue::from_slice::<Cli>(&args).into_result().is_err());
    }
}

#[test]
fn public_profiles_and_junit_command_parse_to_typed_requests() {
    for profile in ["runtime", "tests", "developer"] {
        let cli = figue::from_slice::<Cli>(&[
            "container",
            "build",
            "--projection",
            "sfm-dev/mc-1.19.2",
            "--context",
            "new",
            "--profile",
            profile,
        ])
        .into_result()
        .unwrap()
        .get_silent();
        let crate::cli::Command::Container(args) = cli.command else {
            panic!("wrong command")
        };
        let super::ContainerCommand::Build(args) = args.command else {
            panic!("wrong build")
        };
        assert_eq!(args.profile.stage(), profile);
    }
    let image = "a".repeat(64);
    let cli = figue::from_slice::<Cli>(&[
        "container",
        "test",
        "--engine-endpoint",
        "pipe",
        "--image",
        &image,
        "--context",
        "context",
        "--output",
        "out",
    ])
    .into_result()
    .unwrap()
    .get_silent();
    let crate::cli::Command::Container(args) = cli.command else {
        panic!("wrong command")
    };
    let super::ContainerCommand::Test(args) = args.command else {
        panic!("wrong test")
    };
    assert_eq!(args.timeout_seconds, 900);
    assert!(
        figue::from_slice::<Cli>(&[
            "container",
            "build",
            "--projection",
            "sfm-dev/mc-1.19.2",
            "--context",
            "new",
            "--profile",
            "privileged"
        ])
        .into_result()
        .is_err()
    );
}
