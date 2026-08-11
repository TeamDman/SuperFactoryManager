use crate::client::invoke_client_action;
use crate::discovery::{
    DiscoveredInstanceOutput, discover_instances, instance_descriptor_dir, mark_default,
    select_instance,
};
use crate::output::{CliOutput, OutputFormat};
use crate::protocol::validate_action_tokens;
use facet::Facet;
use figue::{self as args, FigueBuiltins};

#[derive(Debug, Facet)]
pub struct Cli {
    #[facet(default, args::named)]
    pub output_format: Option<OutputFormat>,
    #[facet(args::subcommand)]
    pub command: Command,
    #[facet(flatten)]
    pub builtins: FigueBuiltins,
}

impl Cli {
    pub(crate) async fn invoke(self) -> eyre::Result<CliOutput> {
        self.command.invoke().await
    }
}

#[derive(Debug, Facet)]
#[repr(u8)]
pub enum Command {
    /// Discover and inspect live SFM Minecraft instances.
    Instance(InstanceArgs),
    /// Invoke one registered SFM client action in a selected game.
    Invoke(InvokeArgs),
}

impl Command {
    async fn invoke(self) -> eyre::Result<CliOutput> {
        match self {
            Self::Instance(args) => args.invoke().await,
            Self::Invoke(args) => args.invoke().await,
        }
    }
}

#[derive(Debug, Facet)]
pub struct InstanceArgs {
    #[facet(args::subcommand)]
    pub command: InstanceCommand,
}

impl InstanceArgs {
    async fn invoke(self) -> eyre::Result<CliOutput> {
        self.command.invoke().await
    }
}

#[derive(Debug, Facet)]
#[repr(u8)]
pub enum InstanceCommand {
    /// List and live-probe published game instances.
    List(InstanceListArgs),
}

impl InstanceCommand {
    async fn invoke(self) -> eyre::Result<CliOutput> {
        match self {
            Self::List(args) => args.invoke().await,
        }
    }
}

#[derive(Debug, Facet)]
pub struct InstanceListArgs {}

impl InstanceListArgs {
    async fn invoke(self) -> eyre::Result<CliOutput> {
        let mut snapshot = discover_instances().await?;
        let default = select_instance(&snapshot, None, None).ok();
        mark_default(&mut snapshot, default.as_ref());
        let default_instance_id = default.as_ref().map_or_else(String::new, |instance| {
            instance.descriptor.instance_id.clone()
        });
        let selection_reason = if default.is_some() {
            let responsive_count = snapshot
                .records
                .iter()
                .filter(|record| {
                    matches!(
                        record.output.health,
                        crate::discovery::InstanceHealth::Live
                            | crate::discovery::InstanceHealth::Incompatible
                    )
                })
                .count();
            if responsive_count == 1 {
                "only-responsive-compatible-instance"
            } else {
                "unique-most-recent-focus"
            }
        } else if snapshot.records.iter().any(|record| record.live.is_some()) {
            "ambiguous-live-instances"
        } else {
            "no-compatible-live-instance"
        }
        .to_owned();
        let live_count = snapshot
            .records
            .iter()
            .filter(|record| record.live.is_some())
            .count();
        Ok(CliOutput::facet(InstanceListOutput {
            schema: "sfm.instance-list/1".to_owned(),
            descriptor_dir: instance_descriptor_dir()?.display().to_string(),
            default_instance_present: default.is_some(),
            default_instance_id,
            selection_reason,
            live_count,
            instances: snapshot
                .records
                .into_iter()
                .map(|record| record.output)
                .collect(),
        }))
    }
}

#[derive(Debug, Facet)]
pub struct InvokeArgs {
    /// Registered SFM client action id followed by its action arguments.
    #[facet(args::positional)]
    pub action: Vec<String>,
    #[facet(default, flatten)]
    pub target: TargetArgs,
}

impl InvokeArgs {
    async fn invoke(self) -> eyre::Result<CliOutput> {
        validate_action_tokens(&self.action)?;
        let snapshot = discover_instances().await?;
        let selected = select_instance(
            &snapshot,
            self.target.instance_pid,
            self.target.instance_id.as_deref(),
        )?;
        let result = invoke_client_action(&selected, self.action).await?;
        Ok(CliOutput::facet(InvokeOutput {
            schema: "sfm.invoke/1".to_owned(),
            instance_id: result.instance_id,
            process_id: result.process_id,
            request_id: result.request_id,
            canonical_action: result.canonical_action,
            result_code: result.result_code,
            feedback: result.feedback,
            resulting_screen_present: result.resulting_screen_present,
            resulting_screen: result.resulting_screen,
            workspace_present: result.workspace_present,
            workspace_panel_count: result.workspace_panel_count,
        }))
    }
}

#[derive(Clone, Debug, Default, Facet)]
pub struct TargetArgs {
    /// Target the live game whose process id exactly matches this value.
    #[facet(default, args::named)]
    pub instance_pid: Option<u32>,
    /// Target the live game whose opaque start-scoped id exactly matches.
    #[facet(default, args::named)]
    pub instance_id: Option<String>,
}

#[derive(Debug, Facet)]
struct InstanceListOutput {
    schema: String,
    descriptor_dir: String,
    default_instance_present: bool,
    default_instance_id: String,
    selection_reason: String,
    live_count: usize,
    instances: Vec<DiscoveredInstanceOutput>,
}

#[derive(Debug, Facet)]
struct InvokeOutput {
    schema: String,
    instance_id: String,
    process_id: u32,
    request_id: String,
    canonical_action: String,
    result_code: i32,
    feedback: Vec<String>,
    resulting_screen_present: bool,
    resulting_screen: String,
    workspace_present: bool,
    workspace_panel_count: u32,
}

#[cfg(test)]
mod tests {
    use super::*;
    use figue::ToArgs as _;

    fn parse(arguments: &[&str]) -> Cli {
        figue::from_slice::<Cli>(arguments)
            .into_result()
            .expect("SFM control CLI should parse")
            .get_silent()
    }

    #[test]
    fn parses_instance_list() {
        let parsed = parse(&["instance", "list"]);
        assert!(matches!(
            parsed.command,
            Command::Instance(InstanceArgs {
                command: InstanceCommand::List(_)
            })
        ));
    }

    #[test]
    fn parses_exact_visible_size_display_invocation() {
        let parsed = parse(&["invoke", "sfm:panel/open", "sfm:size_display"]);
        let Command::Invoke(invoke) = parsed.command else {
            panic!("expected invoke command");
        };
        assert_eq!(invoke.action, ["sfm:panel/open", "sfm:size_display"]);
    }

    #[test]
    fn invoke_selectors_round_trip_canonically() {
        let parsed = parse(&[
            "invoke",
            "sfm:test_screen",
            "text with spaces",
            "--instance-pid",
            "1234",
        ]);
        let rendered = parsed.to_args().expect("canonical arguments");
        let rendered = rendered
            .iter()
            .map(|argument| argument.to_string_lossy().into_owned())
            .collect::<Vec<_>>();
        assert!(
            rendered
                .windows(2)
                .any(|pair| pair == ["--instance-pid", "1234"])
        );
        assert!(
            rendered
                .iter()
                .any(|argument| argument == "text with spaces")
        );
    }
}
