use crate::cli::Cli;
use crate::cli::Command;
use crate::cli::audit::AuditArgs;
use crate::cli::global_args::GlobalArgs;
use crate::cli::jar::BranchSelector;
use crate::source_audit::SourceLanguage;
use crate::source_audit::SourceLineLimit;
use figue::ToArgs;
use std::ffi::OsString;

fn args_to_strings(args: Vec<OsString>) -> Vec<String> {
    args.into_iter()
        .map(|arg| arg.to_string_lossy().into_owned())
        .collect()
}

#[test]
fn typed_top_level_audit_command_roundtrips() {
    let command = Cli {
        global_args: GlobalArgs::default(),
        command: Command::Audit(AuditArgs {
            branch: Some(BranchSelector("popular AND >= 1.20.4".to_string())),
            project_root: None,
            language: vec![SourceLanguage::Rust, SourceLanguage::Java],
            lang: Vec::new(),
            max_lines: Some(SourceLineLimit(1200)),
            version_surfaces: true,
            font_render_surface: true,
        }),
        builtins: figue::FigueBuiltins::default(),
    };

    let args = args_to_strings(command.to_args().expect("typed command should render"));
    assert_eq!(
        args,
        [
            "audit",
            "--branch",
            "popular AND >= 1.20.4",
            "--language",
            "rust",
            "--language",
            "java",
            "--max-lines",
            "1200",
            "--version-surfaces",
            "--font-render-surface",
        ]
    );

    let arg_refs = args.iter().map(String::as_str).collect::<Vec<_>>();
    let parsed = figue::from_slice::<Cli>(&arg_refs)
        .into_result()
        .expect("rendered command should parse")
        .get_silent();
    let Command::Audit(parsed) = parsed.command else {
        panic!("expected top-level audit command");
    };
    assert_eq!(
        parsed.branch.as_ref().unwrap().as_ref(),
        "popular AND >= 1.20.4"
    );
    assert_eq!(
        parsed.language,
        [SourceLanguage::Rust, SourceLanguage::Java]
    );
    assert_eq!(parsed.max_lines, Some(SourceLineLimit(1200)));
    assert!(parsed.version_surfaces);
    assert!(parsed.font_render_surface);

    let display = command
        .to_args_string()
        .expect("display command should render")
        .to_string_lossy()
        .into_owned();
    assert!(display.contains("\"popular AND >= 1.20.4\""));

    let full_display = command
        .to_args_string_with_current_exe()
        .expect("full display command should render")
        .to_string_lossy()
        .into_owned();
    assert!(full_display.ends_with(&display));
}
