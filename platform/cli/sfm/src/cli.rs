use crate::client::{ExplorerOperationInput, execute_explorer_operation, invoke_client_action};
use crate::discovery::{
    DiscoveredInstanceOutput, discover_instances, instance_descriptor_dir, mark_default,
    select_instance,
};
use crate::explorer::{EntitySelector, SelectorDomain, SfmPath};
use crate::output::{CliOutput, OutputFormat};
use crate::protocol::{
    SfmControlExplorerIfNoMatch, SfmControlExplorerOperation, SfmControlExplorerOperationResult,
    SfmControlExplorerOperationStatus, SfmControlExplorerTargetResult, validate_action_tokens,
    validate_explorer_projection_id,
};
use crate::spatial::{
    SPATIAL_COVERAGE_RUN_OUTPUT_SCHEMA, SPATIAL_COVERAGE_RUN_OUTPUT_SCHEMA_VERSION,
    SpatialCoverageRunInput, SpatialCoverageScope,
};
use facet::Facet;
use figue::{self as args, FigueBuiltins};
use std::path::Path;

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
    /// Query and mutate generic explorer sessions in a selected game.
    Explorer(ExplorerArgs),
    /// Run spatial analysis and coverage operations in a selected game.
    Spatial(SpatialArgs),
    /// Invoke one registered SFM client action in a selected game.
    Invoke(InvokeArgs),
}

impl Command {
    async fn invoke(self) -> eyre::Result<CliOutput> {
        match self {
            Self::Instance(args) => args.invoke().await,
            Self::Explorer(args) => args.invoke().await,
            Self::Spatial(args) => args.invoke().await,
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
pub struct ExplorerArgs {
    #[facet(args::subcommand)]
    pub command: ExplorerCommand,
}

impl ExplorerArgs {
    async fn invoke(self) -> eyre::Result<CliOutput> {
        self.command.invoke().await
    }
}

#[derive(Debug, Facet)]
#[repr(u8)]
pub enum ExplorerCommand {
    /// List explorer sessions matched by an explicit set-valued selector.
    List(ExplorerListArgs),
    /// Describe explorer state and revision evidence for matched sessions.
    Describe(ExplorerDescribeArgs),
    /// Query or mutate explorer roots.
    Root(ExplorerRootArgs),
    /// Change expansion state or refresh one path in matched explorers.
    Node(ExplorerNodeArgs),
    /// Change explorer view projection.
    View(ExplorerViewArgs),
    /// Change explorer sort projection.
    Sort(ExplorerSortArgs),
    /// Change explorer grouping projection.
    Group(ExplorerGroupArgs),
}

impl ExplorerCommand {
    async fn invoke(self) -> eyre::Result<CliOutput> {
        match self {
            Self::List(args) => args.invoke().await,
            Self::Describe(args) => args.invoke().await,
            Self::Root(args) => args.invoke().await,
            Self::Node(args) => args.invoke().await,
            Self::View(args) => args.invoke().await,
            Self::Sort(args) => args.invoke().await,
            Self::Group(args) => args.invoke().await,
        }
    }
}

#[derive(Debug, Facet)]
pub struct ExplorerListArgs {
    /// Canonical set-valued explorer selector, for example `all` or `focused`.
    #[facet(args::positional)]
    pub explorer_selector: String,
    #[facet(default, flatten)]
    pub target: TargetArgs,
}

impl ExplorerListArgs {
    async fn invoke(self) -> eyre::Result<CliOutput> {
        invoke_explorer_command(
            self.target,
            SfmControlExplorerOperation::List,
            self.explorer_selector,
            String::new(),
            String::new(),
            SfmControlExplorerIfNoMatch::Fail,
        )
        .await
    }
}

#[derive(Debug, Facet)]
pub struct ExplorerDescribeArgs {
    /// Canonical set-valued explorer selector, for example `id(explorer-1)`.
    #[facet(args::positional)]
    pub explorer_selector: String,
    #[facet(default, flatten)]
    pub target: TargetArgs,
}

impl ExplorerDescribeArgs {
    async fn invoke(self) -> eyre::Result<CliOutput> {
        invoke_explorer_command(
            self.target,
            SfmControlExplorerOperation::Describe,
            self.explorer_selector,
            String::new(),
            String::new(),
            SfmControlExplorerIfNoMatch::Fail,
        )
        .await
    }
}

#[derive(Debug, Facet)]
pub struct ExplorerRootArgs {
    #[facet(args::subcommand)]
    pub command: ExplorerRootCommand,
}

impl ExplorerRootArgs {
    async fn invoke(self) -> eyre::Result<CliOutput> {
        self.command.invoke().await
    }
}

#[derive(Debug, Facet)]
#[repr(u8)]
pub enum ExplorerRootCommand {
    /// List roots for every matched explorer.
    List(ExplorerRootListArgs),
    /// Add one canonical or native path as a root.
    Add(ExplorerRootAddArgs),
    /// Remove one canonical or native path from the roots.
    Remove(ExplorerRootRemoveArgs),
    /// Change single-root hoisting presentation.
    Hoist(ExplorerRootHoistArgs),
}

impl ExplorerRootCommand {
    async fn invoke(self) -> eyre::Result<CliOutput> {
        match self {
            Self::List(args) => args.invoke().await,
            Self::Add(args) => args.invoke().await,
            Self::Remove(args) => args.invoke().await,
            Self::Hoist(args) => args.invoke().await,
        }
    }
}

#[derive(Debug, Facet)]
pub struct ExplorerRootListArgs {
    /// Canonical set-valued explorer selector.
    #[facet(args::positional)]
    pub explorer_selector: String,
    #[facet(default, flatten)]
    pub target: TargetArgs,
}

impl ExplorerRootListArgs {
    async fn invoke(self) -> eyre::Result<CliOutput> {
        invoke_explorer_command(
            self.target,
            SfmControlExplorerOperation::RootList,
            self.explorer_selector,
            String::new(),
            String::new(),
            SfmControlExplorerIfNoMatch::Fail,
        )
        .await
    }
}

#[derive(Debug, Facet)]
pub struct ExplorerRootAddArgs {
    /// Canonical set-valued explorer selector.
    #[facet(args::positional)]
    pub explorer_selector: String,
    /// Canonical SFM path or native filesystem path resolved by this process.
    #[facet(args::positional)]
    pub path: String,
    /// Explicit empty-target behavior. Exact ids cannot use `open-new`.
    #[facet(args::named)]
    pub if_no_match: SfmControlExplorerIfNoMatch,
    #[facet(default, flatten)]
    pub target: TargetArgs,
}

impl ExplorerRootAddArgs {
    async fn invoke(self) -> eyre::Result<CliOutput> {
        invoke_explorer_command(
            self.target,
            SfmControlExplorerOperation::RootAdd,
            self.explorer_selector,
            canonicalize_cli_path(&self.path)?,
            String::new(),
            self.if_no_match,
        )
        .await
    }
}

#[derive(Debug, Facet)]
pub struct ExplorerRootRemoveArgs {
    /// Canonical set-valued explorer selector.
    #[facet(args::positional)]
    pub explorer_selector: String,
    /// Canonical SFM path or native filesystem path resolved by this process.
    #[facet(args::positional)]
    pub path: String,
    #[facet(default, flatten)]
    pub target: TargetArgs,
}

impl ExplorerRootRemoveArgs {
    async fn invoke(self) -> eyre::Result<CliOutput> {
        invoke_explorer_command(
            self.target,
            SfmControlExplorerOperation::RootRemove,
            self.explorer_selector,
            canonicalize_cli_path(&self.path)?,
            String::new(),
            SfmControlExplorerIfNoMatch::Fail,
        )
        .await
    }
}

#[derive(Debug, Facet)]
pub struct ExplorerNodeArgs {
    #[facet(args::subcommand)]
    pub command: ExplorerNodeCommand,
}

impl ExplorerNodeArgs {
    async fn invoke(self) -> eyre::Result<CliOutput> {
        self.command.invoke().await
    }
}

#[derive(Debug, Facet)]
#[repr(u8)]
pub enum ExplorerNodeCommand {
    /// Expand one canonical or native path in every matched explorer.
    Expand(ExplorerNodeOperationArgs),
    /// Collapse one canonical or native path in every matched explorer.
    Collapse(ExplorerNodeOperationArgs),
    /// Toggle one canonical or native path in every matched explorer.
    Toggle(ExplorerNodeOperationArgs),
    /// Refresh one canonical or native path in every matched explorer.
    Refresh(ExplorerNodeOperationArgs),
}

impl ExplorerNodeCommand {
    async fn invoke(self) -> eyre::Result<CliOutput> {
        match self {
            Self::Expand(args) => args.invoke(SfmControlExplorerOperation::NodeExpand).await,
            Self::Collapse(args) => args.invoke(SfmControlExplorerOperation::NodeCollapse).await,
            Self::Toggle(args) => args.invoke(SfmControlExplorerOperation::NodeToggle).await,
            Self::Refresh(args) => args.invoke(SfmControlExplorerOperation::NodeRefresh).await,
        }
    }
}

#[derive(Debug, Facet)]
pub struct ExplorerNodeOperationArgs {
    /// Canonical set-valued explorer selector.
    #[facet(args::positional)]
    pub explorer_selector: String,
    /// Canonical SFM path or native filesystem path resolved by this process.
    #[facet(args::positional)]
    pub path: String,
    #[facet(default, flatten)]
    pub target: TargetArgs,
}

impl ExplorerNodeOperationArgs {
    async fn invoke(self, operation: SfmControlExplorerOperation) -> eyre::Result<CliOutput> {
        invoke_explorer_command(
            self.target,
            operation,
            self.explorer_selector,
            canonicalize_cli_path(&self.path)?,
            String::new(),
            SfmControlExplorerIfNoMatch::Fail,
        )
        .await
    }
}

#[derive(Debug, Facet)]
pub struct ExplorerRootHoistArgs {
    #[facet(args::subcommand)]
    pub command: ExplorerRootHoistCommand,
}

impl ExplorerRootHoistArgs {
    async fn invoke(self) -> eyre::Result<CliOutput> {
        self.command.invoke().await
    }
}

#[derive(Debug, Facet)]
#[repr(u8)]
pub enum ExplorerRootHoistCommand {
    /// Set idempotent single-root presentation to `auto` or `show-roots`.
    Set(ExplorerRootHoistSetArgs),
}

impl ExplorerRootHoistCommand {
    async fn invoke(self) -> eyre::Result<CliOutput> {
        match self {
            Self::Set(args) => args.invoke().await,
        }
    }
}

#[derive(Clone, Copy, Debug, Facet)]
#[facet(rename_all = "kebab-case")]
#[repr(u8)]
pub enum ExplorerRootHoistMode {
    Auto,
    ShowRoots,
}

impl ExplorerRootHoistMode {
    const fn canonical(self) -> &'static str {
        match self {
            Self::Auto => "auto",
            Self::ShowRoots => "show-roots",
        }
    }
}

#[derive(Debug, Facet)]
pub struct ExplorerRootHoistSetArgs {
    /// Canonical set-valued explorer selector.
    #[facet(args::positional)]
    pub explorer_selector: String,
    /// Idempotent root presentation mode.
    #[facet(args::positional)]
    pub mode: ExplorerRootHoistMode,
    #[facet(default, flatten)]
    pub target: TargetArgs,
}

impl ExplorerRootHoistSetArgs {
    async fn invoke(self) -> eyre::Result<CliOutput> {
        invoke_explorer_command(
            self.target,
            SfmControlExplorerOperation::RootHoistSet,
            self.explorer_selector,
            String::new(),
            self.mode.canonical().to_owned(),
            SfmControlExplorerIfNoMatch::Fail,
        )
        .await
    }
}

macro_rules! explorer_projection_command {
    (
        $args:ident,
        $command:ident,
        $set_args:ident,
        $operation:expr,
        $noun:literal,
        $value_doc:literal
    ) => {
        #[derive(Debug, Facet)]
        pub struct $args {
            #[facet(args::subcommand)]
            pub command: $command,
        }

        impl $args {
            async fn invoke(self) -> eyre::Result<CliOutput> {
                self.command.invoke().await
            }
        }

        #[derive(Debug, Facet)]
        #[repr(u8)]
        pub enum $command {
            #[doc = concat!("Set the explorer ", $noun, " projection id.")]
            Set($set_args),
        }

        impl $command {
            async fn invoke(self) -> eyre::Result<CliOutput> {
                match self {
                    Self::Set(args) => args.invoke().await,
                }
            }
        }

        #[derive(Debug, Facet)]
        pub struct $set_args {
            /// Canonical set-valued explorer selector.
            #[facet(args::positional)]
            pub explorer_selector: String,
            #[doc = $value_doc]
            #[facet(args::positional)]
            pub value: String,
            #[facet(default, flatten)]
            pub target: TargetArgs,
        }

        impl $set_args {
            async fn invoke(self) -> eyre::Result<CliOutput> {
                validate_explorer_projection_id(&self.value)?;
                invoke_explorer_command(
                    self.target,
                    $operation,
                    self.explorer_selector,
                    String::new(),
                    self.value,
                    SfmControlExplorerIfNoMatch::Fail,
                )
                .await
            }
        }
    };
}

explorer_projection_command!(
    ExplorerViewArgs,
    ExplorerViewCommand,
    ExplorerViewSetArgs,
    SfmControlExplorerOperation::ViewSet,
    "view",
    "Registry contribution id: `sfm:list` or `sfm:small_icons`."
);
explorer_projection_command!(
    ExplorerSortArgs,
    ExplorerSortCommand,
    ExplorerSortSetArgs,
    SfmControlExplorerOperation::SortSet,
    "sort",
    "Registry contribution id: `sfm:name`, `sfm:extension`, or `sfm:icon`."
);
explorer_projection_command!(
    ExplorerGroupArgs,
    ExplorerGroupCommand,
    ExplorerGroupSetArgs,
    SfmControlExplorerOperation::GroupSet,
    "group",
    "Registry contribution id: `sfm:hierarchy` or `sfm:none`."
);

async fn invoke_explorer_command(
    target: TargetArgs,
    operation: SfmControlExplorerOperation,
    explorer_selector: String,
    canonical_path: String,
    setting_value: String,
    if_no_match: SfmControlExplorerIfNoMatch,
) -> eyre::Result<CliOutput> {
    let explorer_selector = canonicalize_explorer_selector(&explorer_selector, if_no_match)?;
    let snapshot = discover_instances().await?;
    let selected = select_instance(
        &snapshot,
        target.instance_pid,
        target.instance_id.as_deref(),
    )?;
    let result = execute_explorer_operation(
        &selected,
        ExplorerOperationInput {
            operation,
            explorer_selector,
            canonical_path,
            setting_value,
            if_no_match,
        },
    )
    .await?;
    Ok(explorer_cli_output(result))
}

fn explorer_cli_output(result: SfmControlExplorerOperationResult) -> CliOutput {
    let exit_code = explorer_cli_exit_code(result.operation, result.status);
    CliOutput::facet_with_status(ExplorerOperationOutput::from(result), exit_code)
}

const fn explorer_cli_exit_code(
    operation: SfmControlExplorerOperation,
    status: SfmControlExplorerOperationStatus,
) -> u8 {
    if matches!(operation, SfmControlExplorerOperation::List)
        && matches!(status, SfmControlExplorerOperationStatus::NoMatch)
    {
        // An empty filtered listing is a successful query. Describe and every
        // mutating command retain the typed no-match exit status.
        0
    } else {
        status.cli_exit_code()
    }
}

fn canonicalize_explorer_selector(
    value: &str,
    if_no_match: SfmControlExplorerIfNoMatch,
) -> eyre::Result<String> {
    let selector = EntitySelector::parse(SelectorDomain::Explorer, value)?;
    if if_no_match == SfmControlExplorerIfNoMatch::OpenNew {
        eyre::ensure!(
            !selector.is_exact_identity(),
            "--if-no-match open-new cannot be combined with an exact explorer id"
        );
    }
    Ok(selector.canonical())
}

fn canonicalize_cli_path(value: &str) -> eyre::Result<String> {
    let path = if value.contains("://") {
        SfmPath::parse(value)?
    } else {
        SfmPath::from_native(Path::new(value))?
    };
    Ok(path.canonical())
}

#[derive(Debug, Facet)]
pub struct SpatialArgs {
    #[facet(args::subcommand)]
    pub command: SpatialCommand,
}

impl SpatialArgs {
    async fn invoke(self) -> eyre::Result<CliOutput> {
        self.command.invoke().await
    }
}

#[derive(Debug, Facet)]
#[repr(u8)]
pub enum SpatialCommand {
    /// Run canvas-owned spatial coverage in a selected live game.
    Coverage(SpatialCoverageArgs),
}

impl SpatialCommand {
    async fn invoke(self) -> eyre::Result<CliOutput> {
        match self {
            Self::Coverage(args) => args.invoke().await,
        }
    }
}

#[derive(Debug, Facet)]
pub struct SpatialCoverageArgs {
    #[facet(args::subcommand)]
    pub command: SpatialCoverageCommand,
}

impl SpatialCoverageArgs {
    async fn invoke(self) -> eyre::Result<CliOutput> {
        self.command.invoke().await
    }
}

#[derive(Debug, Facet)]
#[repr(u8)]
pub enum SpatialCoverageCommand {
    /// Produce document or workspace spatial-coverage evidence and artifacts.
    Run(SpatialCoverageRunArgs),
}

impl SpatialCoverageCommand {
    async fn invoke(self) -> eyre::Result<CliOutput> {
        match self {
            Self::Run(args) => args.invoke().await,
        }
    }
}

#[derive(Debug, Facet)]
pub struct SpatialCoverageRunArgs {
    /// Coverage scope: `document` or `workspace`.
    #[facet(args::positional)]
    pub scope: SpatialCoverageScope,
    /// Resolver-owned selector captured by the game, such as `focused`.
    #[facet(args::positional)]
    pub selector: String,
    /// Namespaced coverage profile, such as `sfm:strict_java_navigation`.
    #[facet(args::positional)]
    pub profile: String,
    /// Namespaced layout matrix, such as `sfm:auto_1_through_8`.
    #[facet(args::positional)]
    pub layout_matrix: String,
    /// Non-negative deterministic sampling seed.
    #[facet(args::positional)]
    pub seed: i64,
    /// Positive maximum semantic-query count.
    #[facet(args::positional)]
    pub budget: i64,
    /// `auto` or an explicit game-authorized artifact destination.
    #[facet(args::positional)]
    pub artifact_destination: String,
    #[facet(default, flatten)]
    pub target: TargetArgs,
}

impl SpatialCoverageRunArgs {
    fn input(&self) -> SpatialCoverageRunInput {
        SpatialCoverageRunInput {
            scope: self.scope,
            selector: self.selector.clone(),
            profile: self.profile.clone(),
            layout_matrix: self.layout_matrix.clone(),
            seed: self.seed,
            budget: self.budget,
            artifact_destination: self.artifact_destination.clone(),
        }
    }

    async fn invoke(self) -> eyre::Result<CliOutput> {
        let input = self.input();
        let action_tokens = input.action_tokens()?;
        let snapshot = discover_instances().await?;
        let selected = select_instance(
            &snapshot,
            self.target.instance_pid,
            self.target.instance_id.as_deref(),
        )?;
        let result = invoke_client_action(&selected, action_tokens.clone()).await?;
        let artifact_destination = input.artifact_destination.clone();
        Ok(CliOutput::facet(SpatialCoverageRunOutput {
            schema: SPATIAL_COVERAGE_RUN_OUTPUT_SCHEMA.to_owned(),
            schema_version: SPATIAL_COVERAGE_RUN_OUTPUT_SCHEMA_VERSION,
            arguments: input,
            action_tokens,
            artifact_destination,
            invocation: SpatialCoverageInvocationOutput {
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
            },
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

#[derive(Debug, Facet)]
struct SpatialCoverageRunOutput {
    schema: String,
    schema_version: u16,
    arguments: SpatialCoverageRunInput,
    action_tokens: Vec<String>,
    artifact_destination: String,
    invocation: SpatialCoverageInvocationOutput,
}

#[derive(Debug, Facet)]
#[allow(clippy::struct_excessive_bools)]
struct SpatialCoverageInvocationOutput {
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

#[derive(Debug, Facet)]
#[allow(clippy::struct_excessive_bools)]
struct ExplorerOperationOutput {
    schema: String,
    instance_id: String,
    process_id: u32,
    request_id: String,
    operation: SfmControlExplorerOperation,
    explorer_selector: String,
    canonical_path: String,
    setting_value: String,
    if_no_match: SfmControlExplorerIfNoMatch,
    status: SfmControlExplorerOperationStatus,
    captured_target_count: u32,
    matched_target_count: u32,
    changed_target_count: u32,
    opened_target_count: u32,
    selection_revision_present: bool,
    selection_revision: i64,
    child_relation_revision_present: bool,
    child_relation_revision: i64,
    targets: Vec<SfmControlExplorerTargetResult>,
    feedback: Vec<String>,
    resulting_screen_present: bool,
    resulting_screen: String,
    workspace_present: bool,
    workspace_panel_count: u32,
}

impl From<SfmControlExplorerOperationResult> for ExplorerOperationOutput {
    fn from(result: SfmControlExplorerOperationResult) -> Self {
        Self {
            schema: explorer_output_schema(result.operation).to_owned(),
            instance_id: result.instance_id,
            process_id: result.process_id,
            request_id: result.request_id,
            operation: result.operation,
            explorer_selector: result.explorer_selector,
            canonical_path: result.canonical_path,
            setting_value: result.setting_value,
            if_no_match: result.if_no_match,
            status: result.status,
            captured_target_count: result.captured_target_count,
            matched_target_count: result.matched_target_count,
            changed_target_count: result.changed_target_count,
            opened_target_count: result.opened_target_count,
            selection_revision_present: result.selection_revision_present,
            selection_revision: result.selection_revision,
            child_relation_revision_present: result.child_relation_revision_present,
            child_relation_revision: result.child_relation_revision,
            targets: result.targets,
            feedback: result.feedback,
            resulting_screen_present: result.resulting_screen_present,
            resulting_screen: result.resulting_screen,
            workspace_present: result.workspace_present,
            workspace_panel_count: result.workspace_panel_count,
        }
    }
}

const fn explorer_output_schema(operation: SfmControlExplorerOperation) -> &'static str {
    match operation {
        SfmControlExplorerOperation::List => "sfm.explorer-list/1",
        SfmControlExplorerOperation::Describe => "sfm.explorer-describe/1",
        SfmControlExplorerOperation::RootList => "sfm.explorer-root-list/1",
        SfmControlExplorerOperation::RootAdd => "sfm.explorer-root-add/1",
        SfmControlExplorerOperation::RootRemove => "sfm.explorer-root-remove/1",
        SfmControlExplorerOperation::ViewSet => "sfm.explorer-view-set/1",
        SfmControlExplorerOperation::SortSet => "sfm.explorer-sort-set/1",
        SfmControlExplorerOperation::GroupSet => "sfm.explorer-group-set/1",
        SfmControlExplorerOperation::RootHoistSet => "sfm.explorer-root-hoist-set/1",
        SfmControlExplorerOperation::NodeExpand => "sfm.explorer-node-expand/1",
        SfmControlExplorerOperation::NodeCollapse => "sfm.explorer-node-collapse/1",
        SfmControlExplorerOperation::NodeToggle => "sfm.explorer-node-toggle/1",
        SfmControlExplorerOperation::NodeRefresh => "sfm.explorer-node-refresh/1",
    }
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

    fn parse_fails(arguments: &[&str]) {
        assert!(
            figue::from_slice::<Cli>(arguments).into_result().is_err(),
            "unexpectedly parsed {arguments:?}"
        );
    }

    fn help(arguments: &[&str]) -> String {
        let error = figue::from_slice::<Cli>(arguments).unwrap_err();
        assert!(
            error.is_help(),
            "expected help for {arguments:?}: {error:?}"
        );
        error.help_text().expect("help text").to_owned()
    }

    fn explorer_result(
        status: SfmControlExplorerOperationStatus,
    ) -> SfmControlExplorerOperationResult {
        SfmControlExplorerOperationResult {
            instance_id: "game-1".to_owned(),
            process_id: 42,
            request_id: "request-1".to_owned(),
            operation: SfmControlExplorerOperation::NodeRefresh,
            explorer_selector: "id(explorer-1)".to_owned(),
            canonical_path: "registry://minecraft/item/".to_owned(),
            setting_value: String::new(),
            if_no_match: SfmControlExplorerIfNoMatch::Fail,
            status,
            captured_target_count: 0,
            matched_target_count: 0,
            changed_target_count: 0,
            opened_target_count: 0,
            selection_revision_present: false,
            selection_revision: 0,
            child_relation_revision_present: false,
            child_relation_revision: 0,
            targets: Vec::new(),
            feedback: vec!["typed explorer outcome".to_owned()],
            resulting_screen_present: false,
            resulting_screen: String::new(),
            workspace_present: false,
            workspace_panel_count: 0,
        }
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

    #[test]
    fn parses_canonical_spatial_coverage_run_with_instance_target() {
        let parsed = parse(&[
            "spatial",
            "coverage",
            "run",
            "document",
            "focused",
            "sfm:strict_java_navigation",
            "sfm:auto_1_through_8",
            "0",
            "100000",
            "auto",
            "--instance-id",
            "game-7",
        ]);
        let Command::Spatial(SpatialArgs {
            command:
                SpatialCommand::Coverage(SpatialCoverageArgs {
                    command: SpatialCoverageCommand::Run(run),
                }),
        }) = parsed.command
        else {
            panic!("expected spatial coverage run command");
        };
        assert_eq!(run.scope, SpatialCoverageScope::Document);
        assert_eq!(run.selector, "focused");
        assert_eq!(run.profile, "sfm:strict_java_navigation");
        assert_eq!(run.layout_matrix, "sfm:auto_1_through_8");
        assert_eq!(run.seed, 0);
        assert_eq!(run.budget, 100_000);
        assert_eq!(run.artifact_destination, "auto");
        assert_eq!(run.target.instance_id.as_deref(), Some("game-7"));
        assert_eq!(
            run.input().action_tokens().expect("canonical tokens"),
            [
                "sfm:spatial/coverage/run",
                "document",
                "focused",
                "sfm:strict_java_navigation",
                "sfm:auto_1_through_8",
                "0",
                "100000",
                "auto",
            ]
        );
    }

    #[test]
    fn spatial_coverage_to_args_retains_full_hierarchy_and_target() {
        let parsed = parse(&[
            "spatial",
            "coverage",
            "run",
            "workspace",
            "focused",
            "sfm:classification",
            "sfm:auto_1_through_8",
            "17",
            "2500",
            "artifact://requested",
            "--instance-pid",
            "1234",
        ]);
        let rendered = parsed
            .to_args()
            .expect("canonical arguments")
            .iter()
            .map(|argument| argument.to_string_lossy().into_owned())
            .collect::<Vec<_>>();
        assert!(
            rendered
                .windows(3)
                .any(|arguments| arguments == ["spatial", "coverage", "run"])
        );
        assert!(
            rendered
                .windows(2)
                .any(|arguments| arguments == ["--instance-pid", "1234"])
        );
        assert!(rendered.iter().any(|argument| argument == "workspace"));
        assert!(
            rendered
                .iter()
                .any(|argument| argument == "artifact://requested")
        );
    }

    #[test]
    fn spatial_coverage_has_no_short_or_flat_aliases() {
        parse_fails(&[
            "coverage",
            "run",
            "document",
            "focused",
            "sfm:classification",
            "sfm:auto_1_through_8",
            "0",
            "1",
            "auto",
        ]);
        parse_fails(&[
            "spatial",
            "run",
            "document",
            "focused",
            "sfm:classification",
            "sfm:auto_1_through_8",
            "0",
            "1",
            "auto",
        ]);
    }

    #[test]
    fn parses_every_direct_canonical_explorer_command() {
        let cases: &[&[&str]] = &[
            &["explorer", "list", "all"],
            &["explorer", "describe", "id(explorer-1)"],
            &["explorer", "root", "list", "focused"],
            &[
                "explorer",
                "root",
                "add",
                "focused",
                "registry://minecraft/item/",
                "--if-no-match",
                "open-new",
            ],
            &[
                "explorer",
                "root",
                "remove",
                "all",
                "registry://minecraft/item/",
            ],
            &[
                "explorer",
                "node",
                "expand",
                "focused",
                "registry://minecraft/item/",
            ],
            &[
                "explorer",
                "node",
                "collapse",
                "id(explorer-1)",
                "registry://minecraft/item/",
            ],
            &[
                "explorer",
                "node",
                "toggle",
                "all",
                "registry://minecraft/item/",
            ],
            &[
                "explorer",
                "node",
                "refresh",
                "id(explorer-1)",
                "registry://minecraft/item/",
            ],
            &["explorer", "view", "set", "all", "sfm:small_icons"],
            &["explorer", "sort", "set", "focused", "sfm:extension"],
            &[
                "explorer",
                "group",
                "set",
                "id(explorer-1)",
                "sfm:hierarchy",
            ],
            &["explorer", "root", "hoist", "set", "all", "show-roots"],
        ];
        for arguments in cases {
            let parsed = parse(arguments);
            assert!(matches!(parsed.command, Command::Explorer(_)));
        }
    }

    #[test]
    fn explorer_root_add_to_args_retains_explicit_policy_and_instance_target() {
        let parsed = parse(&[
            "explorer",
            "root",
            "add",
            "focused",
            "registry://minecraft/item/",
            "--if-no-match",
            "open-new",
            "--instance-id",
            "game-7",
        ]);
        let rendered = parsed
            .to_args()
            .expect("canonical arguments")
            .iter()
            .map(|argument| argument.to_string_lossy().into_owned())
            .collect::<Vec<_>>();
        assert!(
            rendered
                .windows(2)
                .any(|pair| pair == ["--if-no-match", "open-new"])
        );
        assert!(
            rendered
                .windows(2)
                .any(|pair| pair == ["--instance-id", "game-7"])
        );
        assert!(rendered.iter().any(|argument| argument == "explorer"));
        assert!(rendered.iter().any(|argument| argument == "root"));
        assert!(rendered.iter().any(|argument| argument == "add"));
    }

    #[test]
    fn explorer_node_refresh_to_args_retains_direct_hierarchy_and_target() {
        let parsed = parse(&[
            "explorer",
            "node",
            "refresh",
            "id(explorer-7)",
            "registry://minecraft/item/",
            "--instance-pid",
            "1234",
        ]);
        let rendered = parsed
            .to_args()
            .expect("canonical arguments")
            .iter()
            .map(|argument| argument.to_string_lossy().into_owned())
            .collect::<Vec<_>>();
        assert!(
            rendered
                .windows(3)
                .any(|arguments| arguments == ["explorer", "node", "refresh"])
        );
        assert!(
            rendered
                .windows(2)
                .any(|arguments| arguments == ["--instance-pid", "1234"])
        );
        assert!(rendered.iter().any(|argument| argument == "id(explorer-7)"));
    }

    #[test]
    fn explorer_has_no_builtin_short_aliases() {
        parse_fails(&["explore", "list", "all"]);
        parse_fails(&["explorer", "add", "focused", "."]);
        parse_fails(&["explorer", "root", "add", "focused", "."]);
        parse_fails(&["explorer", "hoist", "set", "all", "auto"]);
        parse_fails(&["explorer", "expand", "focused", "."]);
        parse_fails(&["explorer", "node", "open", "focused", "."]);
    }

    #[test]
    fn explorer_projection_commands_use_frozen_builtins() {
        parse(&["explorer", "view", "set", "all", "sfm:list"]);
        parse(&["explorer", "view", "set", "all", "sfm:small_icons"]);
        parse(&["explorer", "sort", "set", "all", "sfm:name"]);
        parse(&["explorer", "sort", "set", "all", "sfm:extension"]);
        parse(&["explorer", "sort", "set", "all", "sfm:icon"]);
        parse(&["explorer", "group", "set", "all", "sfm:hierarchy"]);
        parse(&["explorer", "group", "set", "all", "sfm:none"]);
        let _ = validate_explorer_projection_id("addon:grid")
            .expect_err("canonical extension ids are unsupported in the frozen slice");

        for old_id in [
            "list",
            "small-icons",
            "small_icons",
            "name",
            "extension",
            "icon",
            "hierarchy",
            "none",
            "path-hierarchy",
        ] {
            let _ = validate_explorer_projection_id(old_id)
                .expect_err("compatibility aliases must fail before instance discovery");
        }
    }

    #[test]
    fn explorer_projection_help_names_the_exact_frozen_registry_ids() {
        let view = help(&["explorer", "view", "set", "--help"]);
        assert!(view.contains("`sfm:list` or `sfm:small_icons`"));

        let sort = help(&["explorer", "sort", "set", "--help"]);
        assert!(sort.contains("`sfm:name`"));
        assert!(sort.contains("`sfm:extension`"));
        assert!(sort.contains("`sfm:icon`"));

        let group = help(&["explorer", "group", "set", "--help"]);
        assert!(group.contains("`sfm:hierarchy` or `sfm:none`"));
    }

    #[test]
    fn typed_explorer_failures_remain_structured_and_set_nonzero_status() {
        for (status, expected_exit_code) in [
            (SfmControlExplorerOperationStatus::NoMatch, 2),
            (SfmControlExplorerOperationStatus::Rejected, 1),
        ] {
            let mut result = explorer_result(status);
            if status == SfmControlExplorerOperationStatus::NoMatch {
                result.operation = SfmControlExplorerOperation::Describe;
            }
            let structured = ExplorerOperationOutput::from(result.clone());
            assert_eq!(structured.status, status);
            assert_eq!(structured.feedback, ["typed explorer outcome"]);
            assert_eq!(explorer_cli_output(result).exit_code(), expected_exit_code);
        }
        assert_eq!(
            explorer_cli_output(explorer_result(SfmControlExplorerOperationStatus::Applied))
                .exit_code(),
            0
        );

        let mut empty_list = explorer_result(SfmControlExplorerOperationStatus::NoMatch);
        empty_list.operation = SfmControlExplorerOperation::List;
        assert_eq!(empty_list.operation, SfmControlExplorerOperation::List);
        assert_eq!(explorer_cli_output(empty_list).exit_code(), 0);

        let mut missing_description = explorer_result(SfmControlExplorerOperationStatus::NoMatch);
        missing_description.operation = SfmControlExplorerOperation::Describe;
        assert_eq!(explorer_cli_output(missing_description).exit_code(), 2);
    }

    #[test]
    fn root_paths_accept_canonical_and_absolute_native_forms() {
        assert_eq!(
            canonicalize_cli_path("registry://minecraft/item/").expect("canonical registry path"),
            "registry://minecraft/item/"
        );
        let directory = tempfile::tempdir().expect("temporary native root");
        let canonical = canonicalize_cli_path(
            directory
                .path()
                .to_str()
                .expect("temporary path should be Unicode"),
        )
        .expect("native path canonicalizes");
        assert!(canonical.starts_with("file:///"));
        assert_eq!(
            SfmPath::parse(&canonical)
                .expect("canonical path parses")
                .canonical(),
            canonical
        );
    }

    #[test]
    fn exact_id_open_new_is_rejected_before_instance_discovery() {
        let failure =
            canonicalize_explorer_selector("id(explorer-1)", SfmControlExplorerIfNoMatch::OpenNew)
                .expect_err("exact id must reject fallback");
        assert!(failure.to_string().contains("exact explorer id"));
        assert_eq!(
            canonicalize_explorer_selector("focused", SfmControlExplorerIfNoMatch::OpenNew)
                .expect("focused may create"),
            "focused"
        );
    }

    #[test]
    fn every_explorer_operation_has_a_versioned_output_schema() {
        let schemas = [
            SfmControlExplorerOperation::List,
            SfmControlExplorerOperation::Describe,
            SfmControlExplorerOperation::RootList,
            SfmControlExplorerOperation::RootAdd,
            SfmControlExplorerOperation::RootRemove,
            SfmControlExplorerOperation::ViewSet,
            SfmControlExplorerOperation::SortSet,
            SfmControlExplorerOperation::GroupSet,
            SfmControlExplorerOperation::RootHoistSet,
            SfmControlExplorerOperation::NodeExpand,
            SfmControlExplorerOperation::NodeCollapse,
            SfmControlExplorerOperation::NodeToggle,
            SfmControlExplorerOperation::NodeRefresh,
        ]
        .map(explorer_output_schema);
        assert!(schemas.iter().all(|schema| schema.ends_with("/1")));
        let unique = schemas
            .into_iter()
            .collect::<std::collections::BTreeSet<_>>();
        assert_eq!(unique.len(), 13);
    }
}
