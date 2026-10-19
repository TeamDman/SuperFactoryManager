//! Actual owned-store handlers for the invocation's graph and producer channel.
//! Not a public execution permit: the process host must authenticate the JVM,
//! adapter/runtime bytes and source owner, then retain this session through exit.
//! Input-binding digests bind graph edges plus the complete original contract;
//! they do not claim to be hashes of a javac command or a runtime consumed-file log.

use super::nfrt_child_tool::NfrtChildCommand;
use super::nfrt_child_tool::prepare_child_command;
use super::nfrt_host_protocol::NfrtHostOperation;
use super::nfrt_host_protocol::NfrtHostRequest;
use super::nfrt_preparation_phase::NfrtPreparationPhase;
use crate::jar_build::nfrt_launch_contract::PreparedNfrtInvocationInput;
use crate::source_projection::nfrt_held_producer::NfrtHeldProducer;
use crate::source_projection::nfrt_input_store::NfrtInputStore;
use crate::source_projection::nfrt_producer_output_receipt::NfrtProducerDeclaration;
use crate::source_projection::nfrt_producer_output_receipt::NfrtProducerObservation;
use crate::source_projection::nfrt_producer_output_receipt::NfrtProducerOutputReceipt;
use crate::source_projection::nfrt_producer_output_receipt::NfrtProducerPlan;
use crate::source_projection::nfrt_producer_output_receipt::NfrtProducerScope;
use crate::source_projection::nfrt_producer_output_receipt::SealedNfrtProducerOutput;
use crate::source_projection::nfrt_project_owner::NfrtProjectOwner;
use crate::source_projection::promotion::validate_relative_path;
use crate::source_projection::provenance::sha256;
use eyre::Result;
use eyre::ensure;
use facet::Facet;
use std::collections::BTreeMap;
use std::collections::BTreeSet;

#[derive(Facet)]
struct Graph {
    schema: String,
    nodes: Vec<Node>,
}

#[derive(Facet)]
struct Node {
    id: String,
    action_class: String,
    predecessors: Vec<String>,
    input_contract_json: String,
    action_contract_json: String,
    outputs: Vec<Output>,
}

#[derive(Facet)]
struct Output {
    id: String,
    output_type: String,
}

#[derive(Facet)]
struct ProducerRequest {
    producer_id: String,
    output_id: String,
    output_type: String,
    output_relative_path: String,
}

pub(crate) struct NfrtHostSession<'a> {
    project: &'a dyn NfrtProjectOwner,
    prepared: &'a PreparedNfrtInvocationInput,
    store: &'a mut NfrtInputStore,
    scope: Option<NfrtProducerScope>,
    plan: Option<NfrtProducerPlan>,
    declarations: BTreeMap<(String, String), NfrtProducerDeclaration>,
    held: BTreeMap<(String, String), NfrtHeldProducer>,
    sealed_paths: BTreeMap<(String, String), String>,
    sealed_receipts: BTreeMap<(String, String), NfrtProducerOutputReceipt>,
    tool_configurations: BTreeMap<String, String>,
    finished: bool,
    compiler_inputs_ready: bool,
}

impl<'a> NfrtHostSession<'a> {
    pub(crate) fn new(
        project: &'a dyn NfrtProjectOwner,
        prepared: &'a PreparedNfrtInvocationInput,
        store: &'a mut NfrtInputStore,
    ) -> Result<Self> {
        let phase = NfrtPreparationPhase::start("host_session.new");
        NfrtPreparationPhase::run("host_session.source_recheck.new", || {
            project.recheck_source()
        })?;
        ensure!(
            store.contract_identity() == prepared.contract_identity()
                && prepared.receipt().preparation_identity == project.preparation_identity()?,
            "Host store, transfer and current source owner differ"
        );
        phase.finish(Ok(Self {
            project,
            prepared,
            store,
            scope: None,
            plan: None,
            declarations: BTreeMap::new(),
            held: BTreeMap::new(),
            sealed_paths: BTreeMap::new(),
            sealed_receipts: BTreeMap::new(),
            tool_configurations: BTreeMap::new(),
            finished: false,
            compiler_inputs_ready: false,
        }))
    }

    /// Prepare only: the caller must establish actual SDK and child ownership
    /// before spawning this command. No protocol success reply is implied.
    pub(crate) fn prepare_tool(
        &self,
        java: &std::path::Path,
        graph_node_id: &str,
        payload: &str,
    ) -> Result<NfrtChildCommand> {
        let mut phase = NfrtPreparationPhase::start("host_session.prepare_child_command");
        phase.work(self.sealed_paths.len() as u64, payload.len() as u64);
        ensure!(
            !self.finished && self.plan.is_some(),
            "No active registered graph"
        );
        NfrtPreparationPhase::run("host_session.source_recheck.prepare_child", || {
            self.project.recheck_source()
        })?;
        let configuration = self
            .tool_configurations
            .get(graph_node_id)
            .ok_or_else(|| eyre::eyre!("Tool is not part of this owned graph"))?;
        phase.finish(prepare_child_command(
            self.prepared,
            self.store.root(),
            java,
            graph_node_id,
            configuration,
            payload,
            &self.sealed_paths,
        ))
    }

    pub(crate) fn execute_tool(
        &mut self,
        request: &NfrtHostRequest,
        sdk: &mut crate::source_projection::nfrt_tool_sdk::NfrtToolSdk,
        cancellation: &crate::cancellation::CancellationToken,
    ) -> Result<String> {
        let mut phase = NfrtPreparationPhase::start("host_session.execute_tool");
        phase.work(1, request.payload.len() as u64);
        cancellation.bail_if_cancelled()?;
        ensure!(
            request.operation == NfrtHostOperation::Tool
                && request.contract_identity() == self.store.contract_identity(),
            "Foreign child-tool request"
        );
        let node = super::nfrt_child_tool::requested_node(&request.payload)?;
        let node_span =
            tracing::info_span!("nfrt_host_tool", node = %NfrtPreparationPhase::safe_node(&node));
        let _node_span = node_span.enter();
        let command = self.prepare_tool(sdk.executable(), &node, &request.payload)?;
        let reply = super::nfrt_child_process::execute_child(command, sdk, cancellation)?;
        NfrtPreparationPhase::run("host_session.source_recheck.after_child", || {
            self.project.recheck_source()
        })?;
        phase.finish(Ok(reply))
    }

    pub(crate) fn handle(&mut self, request: &NfrtHostRequest) -> Result<String> {
        #[derive(Facet)]
        struct Completion<'a> {
            graph_prepared: bool,
            native_execution_enabled: bool,
            compiler_inputs: Option<[&'a NfrtProducerOutputReceipt; 2]>,
        }
        let mut phase = NfrtPreparationPhase::start("host_session.handle");
        phase.work(1, request.payload.len() as u64);
        ensure!(!self.finished, "Host session is already closed");
        ensure!(
            request.contract_identity() == self.store.contract_identity(),
            "Host request belongs to another owned invocation"
        );
        NfrtPreparationPhase::run("host_session.source_recheck.handle", || {
            self.project.recheck_source()
        })?;
        let result = match request.operation {
            NfrtHostOperation::Graph => self.accept_graph(&request.payload),
            NfrtHostOperation::Seal => self.seal(&request.payload),
            NfrtHostOperation::Complete => {
                #[derive(Facet)]
                struct PreparationComplete {
                    phase: String,
                }
                let complete: PreparationComplete = facet_json::from_str(&request.payload)?;
                ensure!(
                    self.plan.is_some()
                        && ((self.held.is_empty() && complete.phase == "graph_prepared")
                            || (self.held.len() == 1
                                && self.held.contains_key(&(
                                    "stripClient".to_owned(),
                                    "output".to_owned()
                                ))
                                && complete.phase == "builtin_producer_proof")
                            || (self.held.len() == 1
                                && self.held.contains_key(&(
                                    "extractServer".to_owned(),
                                    "output".to_owned()
                                ))
                                && complete.phase == "child_tool_proof")
                            || (self
                                .held
                                .contains_key(&("decompile".to_owned(), "output".to_owned()))
                                && complete.phase == "source_recipe_proof")
                            || (self.held.contains_key(&(
                                "transformSources".to_owned(),
                                "output".to_owned()
                            )) && complete.phase == "source_transform_proof")
                            || (self.held.contains_key(&(
                                "compiledWithNeoForge".to_owned(),
                                "output".to_owned()
                            )) && self.sealed_receipts.contains_key(&(
                                "sourcesWithNeoForge".to_owned(),
                                "output".to_owned()
                            )) && complete.phase == "minecraft_compile_proof")),
                    "Native result completion is not integrated; preparation cannot claim it"
                );
                // Return only receipts already published by this session. A
                // caller-supplied path, graph declaration or old cache file
                // cannot be promoted into a compiler input by completion.
                let results = if complete.phase == "minecraft_compile_proof" {
                    Some(self.compiler_result_receipts()?)
                } else {
                    None
                };
                let reply = facet_json::to_string(&Completion {
                    graph_prepared: true,
                    native_execution_enabled: false,
                    compiler_inputs: results,
                })?;
                self.compiler_inputs_ready = complete.phase == "minecraft_compile_proof";
                self.finished = true;
                Ok(reply)
            }
            NfrtHostOperation::Tool => eyre::bail!("Host operation has no integrated handler"),
        };
        phase.finish(result)
    }

    /// Consume the exact published compiler pair while this session retains
    /// original inputs, producer handles and immutable snapshot handles. This
    /// does not authorize a child process; the registered caller must retain
    /// its authenticated SDK and use the owned process service separately.
    /// Never convert these paths into an inherited-cache execution permit.
    pub(crate) fn with_compiler_inputs<T>(
        &self,
        consume: impl FnOnce(&std::path::Path, &std::path::Path) -> Result<T>,
    ) -> Result<T> {
        ensure!(
            self.finished && self.compiler_inputs_ready,
            "Compiler inputs require successful paired-result completion"
        );
        NfrtPreparationPhase::run("host_session.source_recheck.before_consume", || {
            self.project.recheck_source()
        })?;
        let [classes, sources] = self.compiler_result_receipts()?;
        let classes = self.store.root().join(&classes.snapshot_relative_path);
        let sources = self.store.root().join(&sources.snapshot_relative_path);
        let consumer = NfrtPreparationPhase::start("host_session.compiler_consumer");
        let result = consume(&classes, &sources);
        // Check source ownership even when the downstream consumer fails.
        NfrtPreparationPhase::run("host_session.source_recheck.after_consume", || {
            self.project.recheck_source()
        })?;
        consumer.finish(result)
    }

    pub(super) fn compiler_result_receipts(&self) -> Result<[&NfrtProducerOutputReceipt; 2]> {
        let mut results = Vec::with_capacity(2);
        for (producer, output_type) in [
            ("compiledWithNeoForge", "JAR"),
            ("sourcesWithNeoForge", "ZIP"),
        ] {
            let key = (producer.to_owned(), "output".to_owned());
            let receipt = self
                .sealed_receipts
                .get(&key)
                .ok_or_else(|| eyre::eyre!("Compiler result was not published by this session"))?;
            ensure!(
                self.held.contains_key(&key)
                    && self.sealed_paths.get(&key) == Some(&receipt.snapshot_relative_path)
                    && receipt.scope.prepared_contract_sha256 == self.store.contract_identity()
                    && receipt.declaration.output_type == output_type,
                "Compiler result lost held producer, publication or contract identity"
            );
            results.push(receipt);
        }
        Ok([results[0], results[1]])
    }

    #[expect(
        clippy::too_many_lines,
        reason = "Preflight all graph declarations and their original bindings before registration."
    )]
    fn accept_graph(&mut self, payload: &str) -> Result<String> {
        let mut phase = NfrtPreparationPhase::start("host_session.accept_graph");
        ensure!(self.plan.is_none(), "Host graph is already registered");
        let graph: Graph = facet_json::from_str(payload)?;
        ensure!(
            graph.schema == "sfm:nfrt_host_graph@1"
                && !graph.nodes.is_empty()
                && graph.nodes.len() <= 256,
            "Wrong or oversized host graph"
        );
        phase.work(graph.nodes.len() as u64, payload.len() as u64);
        let mut ids = BTreeSet::new();
        for node in &graph.nodes {
            validate_relative_path(&format!("work/{}/reserved-leaf", node.id))?;
            ensure!(
                !node.id.contains('/') && ids.insert(node.id.clone()),
                "Duplicate or unsafe graph node"
            );
            ensure!(
                !node.input_contract_json.is_empty()
                    && node.input_contract_json.len() <= 128 * 1024
                    && !node.action_contract_json.is_empty()
                    && node.action_contract_json.len() <= 128 * 1024
                    && node
                        .action_class
                        .starts_with("net.neoforged.neoform.runtime."),
                "Missing, oversized or foreign node contracts"
            );
        }
        // Even a bounded graph cannot introduce a cyclic execution dependency.
        let mut resolved = BTreeSet::new();
        while resolved.len() < graph.nodes.len() {
            let before = resolved.len();
            for node in &graph.nodes {
                if node.predecessors.iter().all(|id| resolved.contains(id)) {
                    resolved.insert(node.id.clone());
                }
            }
            ensure!(
                resolved.len() > before,
                "Host graph is cyclic or has unknown predecessors"
            );
        }
        let contract = self.prepared.contract_identity();
        let scope = NfrtProducerScope {
            invocation_id: self.prepared.receipt().invocation_id.clone(),
            prepared_contract_sha256: contract.clone(),
            project_preparation_sha256: self.prepared.receipt().preparation_identity.clone(),
            source_lock_sha256: self.prepared.receipt().source_lock_sha256.clone(),
            recipe_id: self.prepared.receipt().recipe_id.clone(),
            graph_plan_sha256: sha256(payload.as_bytes()),
            tool_sdk_sha256: sha256(
                facet_json::to_string(&self.prepared.receipt().tool_sdk)?.as_bytes(),
            ),
        };
        let mut declarations = Vec::new();
        for node in &graph.nodes {
            if node.action_class
                == "net.neoforged.neoform.runtime.actions.RecompileSourcesActionWithJDK"
            {
                #[derive(Facet)]
                #[facet(deny_unknown_fields)]
                struct CompilerConfiguration {
                    #[facet(rename = "class")]
                    action_class: String,
                    target_java_version: u16,
                    compiler_options: Vec<String>,
                }
                let compiler: CompilerConfiguration =
                    facet_json::from_str(&node.action_contract_json)?;
                let release = self.prepared.receipt().compiler_release;
                let expected = vec![
                    "--release".to_owned(),
                    release.to_string(),
                    "-proc:none".to_owned(),
                    "-nowarn".to_owned(),
                    "-g".to_owned(),
                    "-XDuseUnsharedTable=true".to_owned(),
                    "-implicit:none".to_owned(),
                ];
                ensure!(
                    node.id == "recompile"
                        && compiler.action_class == node.action_class
                        && compiler.target_java_version == release
                        && compiler.compiler_options == expected
                        && u32::from(release) <= self.prepared.receipt().tool_sdk.major,
                    "In-process compiler changed original target, options or selected SDK boundary"
                );
            }
            ensure!(
                node.predecessors
                    .iter()
                    .all(|id| ids.contains(id) && id != &node.id),
                "Graph refers to a missing or self predecessor"
            );
            ensure!(
                !node.outputs.is_empty() && node.outputs.len() <= 32,
                "Invalid node output count"
            );
            let alias = matches!(
                node.action_class.as_str(),
                "net.neoforged.neoform.runtime.actions.DownloadVersionManifestAction"
                    | "net.neoforged.neoform.runtime.actions.DownloadFromVersionManifestAction"
                    | "net.neoforged.neoform.runtime.actions.DownloadLauncherManifestAction"
            );
            for output in &node.outputs {
                let extension = match output.output_type.as_str() {
                    "JAR" => ".jar",
                    "JAR_MANIFEST" => ".MF",
                    "TXT" => ".txt",
                    "ZIP" => ".zip",
                    "JSON" => ".json",
                    "TSRG" => ".tsrg",
                    "SRG" => ".srg",
                    _ => eyre::bail!("Unreviewed graph output type"),
                };
                let path = format!("work/{}/{}{extension}", node.id, output.id);
                validate_relative_path(&path)?;
                ensure!(!output.id.contains('/'), "Unsafe graph output ID");
                if alias {
                    continue;
                }
                declarations.push(NfrtProducerDeclaration {
                    producer_id: node.id.clone(),
                    output_id: output.id.clone(),
                    output_type: output.output_type.clone(),
                    output_relative_path: path,
                    producer_inputs_sha256: binding_digest(&contract, &node.input_contract_json)?,
                    tool_classpath_and_arguments_sha256: binding_digest(
                        &contract,
                        &node.action_contract_json,
                    )?,
                });
            }
        }
        let plan = NfrtProducerPlan::from_declared_graph(scope.clone(), declarations.clone())?;
        let node_ids = graph
            .nodes
            .iter()
            .map(|node| node.id.clone())
            .collect::<Vec<_>>();
        NfrtPreparationPhase::run("host_session.source_recheck.graph", || {
            self.project.recheck_source()
        })?;
        NfrtPreparationPhase::run("host_session.create_workspaces", || {
            self.store.create_workspaces(&node_ids)
        })?;
        tracing::info!(
            phase = "host_session.graph",
            graph_nodes = graph.nodes.len(),
            producer_declarations = declarations.len(),
            "nfrt_preparation_work"
        );
        self.declarations = declarations
            .into_iter()
            .map(|declaration| {
                (
                    (
                        declaration.producer_id.clone(),
                        declaration.output_id.clone(),
                    ),
                    declaration,
                )
            })
            .collect();
        self.scope = Some(scope);
        self.tool_configurations = graph
            .nodes
            .iter()
            .filter(|node| {
                matches!(
                    node.action_class.as_str(),
                    "net.neoforged.neoform.runtime.actions.ExternalJavaToolAction"
                        | "net.neoforged.neoform.runtime.actions.ApplySourceTransformAction"
                        | "net.neoforged.neoform.runtime.actions.ApplyDevTransformsAction"
                )
            })
            .map(|node| (node.id.clone(), node.action_contract_json.clone()))
            .collect();
        self.plan = Some(plan);
        phase.finish(Ok(
            "{\"workspaces_ready\":true,\"execution_enabled\":false}".to_owned(),
        ))
    }

    fn seal(&mut self, payload: &str) -> Result<String> {
        let mut phase = NfrtPreparationPhase::start("host_session.seal");
        let plan = self
            .plan
            .as_ref()
            .ok_or_else(|| eyre::eyre!("Producer request precedes graph ownership"))?;
        let scope = self
            .scope
            .as_ref()
            .ok_or_else(|| eyre::eyre!("Missing producer scope"))?;
        let requests: Vec<ProducerRequest> = facet_json::from_str(payload)?;
        ensure!(requests.len() <= 256, "Oversized producer request batch");
        let mut selected = Vec::with_capacity(requests.len());
        // Match every consumer before opening even the first produced file.
        for request in requests {
            let key = (request.producer_id, request.output_id);
            let declaration = self
                .declarations
                .get(&key)
                .ok_or_else(|| eyre::eyre!("Undeclared producer consumer"))?;
            ensure!(
                request.output_type == declaration.output_type
                    && request.output_relative_path == declaration.output_relative_path,
                "Producer consumer changed type or path"
            );
            selected.push((key, declaration.clone()));
        }
        let (observations, producer_bytes) =
            Self::producer_observations(self.store.root(), &mut self.held, &selected, scope)?;
        phase.work(observations.len() as u64, producer_bytes);
        let outputs =
            NfrtPreparationPhase::run("host_session.validate_and_snapshot_producers", || {
                plan.seal_classpath_batch(&observations, |declaration| {
                    let held = self
                        .held
                        .get_mut(&(
                            declaration.producer_id.clone(),
                            declaration.output_id.clone(),
                        ))
                        .ok_or_else(|| eyre::eyre!("Missing held producer"))?;
                    let mut read = NfrtPreparationPhase::start("host_session.read_producer");
                    read.work(1, held.bytes() as u64);
                    read.finish(held.read_once())
                })
            })?;
        NfrtPreparationPhase::run("host_session.source_recheck.seal", || {
            self.project.recheck_source()
        })?;
        NfrtPreparationPhase::run("host_session.publish_generated", || {
            self.store.publish_generated(&outputs)
        })?;
        for output in &outputs {
            let receipt = output.receipt();
            self.sealed_paths.insert(
                (
                    receipt.declaration.producer_id.clone(),
                    receipt.declaration.output_id.clone(),
                ),
                receipt.snapshot_relative_path.clone(),
            );
            self.sealed_receipts.insert(
                (
                    receipt.declaration.producer_id.clone(),
                    receipt.declaration.output_id.clone(),
                ),
                receipt.clone(),
            );
        }
        let receipts = outputs
            .iter()
            .map(SealedNfrtProducerOutput::receipt)
            .collect::<Vec<_>>();
        phase.finish(facet_json::to_string(&receipts).map_err(Into::into))
    }

    fn producer_observations(
        root: &std::path::Path,
        held: &mut BTreeMap<(String, String), NfrtHeldProducer>,
        selected: &[((String, String), NfrtProducerDeclaration)],
        scope: &NfrtProducerScope,
    ) -> Result<(Vec<NfrtProducerObservation>, u64)> {
        let mut observations = Vec::with_capacity(selected.len());
        let mut held_handle_hits = 0_u64;
        let mut held_handle_misses = 0_u64;
        let mut producer_bytes = 0_u64;
        for (key, declaration) in selected {
            if held.contains_key(key) {
                held_handle_hits += 1;
            } else {
                held_handle_misses += 1;
                held.insert(
                    key.clone(),
                    NfrtPreparationPhase::run("host_session.acquire_producer", || {
                        NfrtHeldProducer::acquire(root, declaration)
                    })?,
                );
            }
            let producer = &held[key];
            producer_bytes += producer.bytes() as u64;
            // The fixed Java adapter sends this operation only after its exact
            // owned NodeOutput reaches COMPLETED; it never trusts a user path.
            observations.push(NfrtProducerObservation {
                scope: scope.clone(),
                declaration: declaration.clone(),
                belongs_to_fresh_engine: true,
                completed: true,
                restored_from_cache: false,
                regular_file: true,
                reparse_free_ancestry: true,
                before_file_identity: producer.identity(),
                after_file_identity: producer.identity(),
                before_bytes: producer.bytes(),
                after_bytes: producer.bytes(),
            });
        }
        tracing::info!(
            phase = "host_session.seal_handles",
            held_handle_hits,
            held_handle_misses,
            producer_outputs = observations.len(),
            producer_bytes,
            inherited_cache_restore_enabled = false,
            "nfrt_preparation_work"
        );
        Ok((observations, producer_bytes))
    }
}

fn binding_digest(contract: &str, component: &str) -> Result<String> {
    #[derive(Facet)]
    struct Binding<'a> {
        contract: &'a str,
        component: &'a str,
    }
    Ok(sha256(
        facet_json::to_string(&Binding {
            contract,
            component,
        })?
        .as_bytes(),
    ))
}
