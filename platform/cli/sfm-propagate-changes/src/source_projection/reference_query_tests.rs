#[test]
fn file_issue_cli_requires_explicit_input_and_safe_path_argument() {
    let parsed = figue::from_slice::<crate::cli::Cli>(&[
        "source",
        "references",
        "--input",
        "input.json",
        "--path",
        "src/data/sfm/disk_reset.json",
    ])
    .into_result()
    .unwrap()
    .get_silent();
    assert!(matches!(
        parsed.command,
        crate::cli::Command::Source(crate::cli::source::SourceArgs {
            command: crate::cli::source::SourceCommand::References(_)
        })
    ));
    assert!(
        figue::from_slice::<crate::cli::Cli>(&[
            "source",
            "references",
            "--path",
            "src/data/sfm/disk_reset.json"
        ])
        .into_result()
        .is_err()
    );
}
