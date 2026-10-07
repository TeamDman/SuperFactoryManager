package ca.teamdman.sfm.client.symbol;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Exact JSON envelopes shared with the long-lived Rust symbol server. */
public final class SFMSymbolServerProtocol {
    public static final String PROTOCOL_SCHEMA = "sfm.symbol-server/1";
    public static final String HELLO_SCHEMA = "sfm.symbol-server.hello/1";
    public static final String DEFINITION_SCHEMA = "sfm.symbol-server.definition/1";
    public static final String USAGE_AT_POSITION_SCHEMA = "sfm.symbol-server.usage-at-position/1";
    public static final String JAVA_INTERACTION_MAP_SCHEMA = "sfm.symbol-server.java-interaction-map/1";
    public static final String CANCEL_SCHEMA = "sfm.symbol-server.cancel/1";
    public static final String WORKSPACE_GENERATION_SCHEMA = "sfm.symbol-server.workspace-generation/1";
    public static final String PING_SCHEMA = "sfm.symbol-server.ping/1";
    public static final String SHUTDOWN_SCHEMA = "sfm.symbol-server.shutdown/1";
    public static final String ERROR_SCHEMA = "sfm.symbol-server.error/1";

    public static final String CAPABILITY_DEFINITION = "definition-at-position";
    public static final String CAPABILITY_USAGE_AT_POSITION = "usage-at-position";
    public static final String CAPABILITY_JAVA_INTERACTION_MAP = "java-interaction-map";
    public static final String CAPABILITY_CANCELLATION = "cancellation";
    public static final String CAPABILITY_WORKSPACE_GENERATION = "workspace-generation";
    public static final String CAPABILITY_PING = "ping";
    public static final String CAPABILITY_SHUTDOWN = "shutdown";
    public static final List<String> CLIENT_CAPABILITIES = List.of(
            CAPABILITY_DEFINITION,
            CAPABILITY_CANCELLATION,
            CAPABILITY_WORKSPACE_GENERATION,
            CAPABILITY_PING,
            CAPABILITY_SHUTDOWN,
            CAPABILITY_USAGE_AT_POSITION,
            CAPABILITY_JAVA_INTERACTION_MAP
    );
    private static final Set<String> CANCELLATION_STATUSES = Set.of(
            "recorded-before-request",
            "cancellation-requested",
            "already-terminal",
            "rejected-capacity",
            "stale-generation"
    );
    private static final Set<String> ERROR_DISPOSITIONS = Set.of("request", "fatal");

    private SFMSymbolServerProtocol() {
    }

    /** Canonical worker mapping from a resolver-authorized absolute root to one portable root. */
    public record SourceRootMapping(
            String canonicalAbsolutePath,
            String rootId,
            String sourceSet,
            String reportRootPath
    ) {
        public SourceRootMapping {
            canonicalAbsolutePath = normalizeCanonicalAbsolutePath(canonicalAbsolutePath);
            rootId = nonBlank(rootId, "rootId");
            sourceSet = nonBlank(sourceSet, "sourceSet");
            reportRootPath = canonicalRelativePath(reportRootPath, "reportRootPath", false);
        }

        public String absoluteRootAddress() {
            return ca.teamdman.sfm.client.explorer.SFMPath
                    .fromNative(Path.of(canonicalAbsolutePath))
                    .canonical();
        }
    }

    /** Acquired dependency-source root authenticated and disclosed by the local Rust worker. */
    public record DependencySourceRootMapping(
            String canonicalAbsolutePath,
            String rootId,
            String sourceSet,
            String reportPrefix
    ) {
        public DependencySourceRootMapping {
            canonicalAbsolutePath = normalizeCanonicalAbsolutePath(canonicalAbsolutePath);
            rootId = nonBlank(rootId, "rootId");
            sourceSet = nonBlank(sourceSet, "sourceSet");
            reportPrefix = canonicalRelativePath(reportPrefix, "reportPrefix", false);
            if (!reportPrefix.startsWith("dependency/")) {
                throw new IllegalArgumentException("Dependency source report prefix must be under dependency/");
            }
        }

        public String absoluteRootAddress() {
            return ca.teamdman.sfm.client.explorer.SFMPath
                    .fromNative(Path.of(canonicalAbsolutePath))
                    .canonical();
        }
    }

    /** Worker-owned immutable source tree (for example the branch-selected JDK source cache). */
    public record ManagedSourceRootMapping(
            String resolverId,
            String addressScheme,
            String resolverIdentity,
            String canonicalAbsolutePath,
            String rootId,
            String sourceSet,
            Optional<String> portableRootPath,
            Optional<String> reportPrefix
    ) {
        public ManagedSourceRootMapping {
            resolverId = nonBlank(resolverId, "resolverId");
            addressScheme = nonBlank(addressScheme, "addressScheme");
            resolverIdentity = nonBlank(resolverIdentity, "resolverIdentity");
            canonicalAbsolutePath = normalizeCanonicalAbsolutePath(canonicalAbsolutePath);
            rootId = nonBlank(rootId, "rootId");
            sourceSet = nonBlank(sourceSet, "sourceSet");
            portableRootPath = Objects.requireNonNull(portableRootPath, "portableRootPath")
                    .map(value -> canonicalRelativePath(value, "portableRootPath", false));
            reportPrefix = Objects.requireNonNull(reportPrefix, "reportPrefix")
                    .map(value -> canonicalRelativePath(value, "reportPrefix", false));
            if (!resolverId.matches("[a-z][a-z0-9+.-]*")
                    || !addressScheme.matches("[a-z][a-z0-9+.-]*")) {
                throw new IllegalArgumentException("Managed source resolver identities must be canonical schemes");
            }
        }

        public String absoluteRootAddress() {
            return ca.teamdman.sfm.client.explorer.SFMPath
                    .fromNative(Path.of(canonicalAbsolutePath))
                    .canonical();
        }
    }

    /** Worker-resolved identity retained for exact later context adaptation. */
    public record WorkspaceMetadata(
            SFMDefinitionRequest.Workspace workspace,
            List<SourceRootMapping> rootMappings,
            List<DependencySourceRootMapping> dependencySourceRootMappings,
            List<ManagedSourceRootMapping> managedSourceRootMappings
    ) {
        public WorkspaceMetadata(
                SFMDefinitionRequest.Workspace workspace,
                List<SourceRootMapping> rootMappings
        ) {
            this(workspace, rootMappings, List.of(), List.of());
        }

        public WorkspaceMetadata(
                SFMDefinitionRequest.Workspace workspace,
                List<SourceRootMapping> rootMappings,
                List<DependencySourceRootMapping> dependencySourceRootMappings
        ) {
            this(workspace, rootMappings, dependencySourceRootMappings, List.of());
        }

        public WorkspaceMetadata {
            Objects.requireNonNull(workspace, "workspace");
            rootMappings = List.copyOf(rootMappings);
            dependencySourceRootMappings = List.copyOf(dependencySourceRootMappings);
            managedSourceRootMappings = List.copyOf(managedSourceRootMappings);
            if (workspace.sourceRoots().size() != rootMappings.size()) {
                throw new IllegalArgumentException("Worker root mappings do not cover every request root");
            }
            for (int index = 0; index < rootMappings.size(); index++) {
                SourceRootMapping mapping = rootMappings.get(index);
                SFMDefinitionRequest.SourceRoot root = workspace.sourceRoots().get(index);
                if (!root.id().equals(mapping.rootId())) {
                    throw new IllegalArgumentException("Worker root mapping order disagrees with request roots");
                }
                if (!root.sourceSet().equals(mapping.sourceSet())) {
                    throw new IllegalArgumentException("Worker root mapping source set disagrees with root");
                }
                if (!root.path().equals(mapping.reportRootPath())) {
                    throw new IllegalArgumentException("Worker root mapping report path disagrees with root");
                }
            }
            HashSet<String> dependencyRootIds = new HashSet<>();
            for (DependencySourceRootMapping mapping : dependencySourceRootMappings) {
                if (!dependencyRootIds.add(mapping.rootId())) {
                    throw new IllegalArgumentException(
                            "Worker dependency source root id is duplicated: " + mapping.rootId());
                }
            }
            HashSet<String> managedRootIds = new HashSet<>();
            for (ManagedSourceRootMapping mapping : managedSourceRootMappings) {
                String key = mapping.resolverId() + "://" + mapping.rootId();
                if (!managedRootIds.add(key)) {
                    throw new IllegalArgumentException("Worker managed source root is duplicated: " + key);
                }
                SFMDefinitionRequest.SourceRoot sourceRoot = workspace.sourceRoots().stream()
                        .filter(root -> root.id().equals(mapping.rootId()))
                        .filter(root -> root.sourceSet().equals(mapping.sourceSet()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Worker managed source root has no matching request root: " + key));
                if (sourceRoot.kind().equals("jdk")
                        && (!mapping.resolverId().equals("jdk-source")
                        || !mapping.addressScheme().equals("jdk-source"))) {
                    throw new IllegalArgumentException("JDK source roots require the jdk-source resolver identity");
                }
            }
        }

        public List<SourceRootMapping> mappingsForAddress(String canonicalAddress) {
            String canonical = canonicalFileAddress(canonicalAddress);
            return rootMappings.stream()
                    .filter(mapping -> mapping.absoluteRootAddress().equals(canonical))
                    .toList();
        }
    }

    /** Validated hello plus its additive raw payload for future protocol extensions. */
    public record ServerHello(
            String protocolSchema,
            String serverName,
            String serverVersion,
            Set<String> capabilities,
            int maximumFrameBytes,
            int maximumPendingDefinitions,
            WorkspaceMetadata workspace,
            String rawHelloJson
    ) {
        public ServerHello {
            if (!PROTOCOL_SCHEMA.equals(protocolSchema)) {
                throw new IllegalArgumentException("Unsupported symbol-server protocol schema");
            }
            serverName = nonBlank(serverName, "serverName");
            serverVersion = nonBlank(serverVersion, "serverVersion");
            capabilities = Set.copyOf(capabilities);
            if (!capabilities.containsAll(CLIENT_CAPABILITIES)) {
                throw new IllegalArgumentException("Symbol server is missing required capabilities");
            }
            if (maximumFrameBytes <= 0) throw new IllegalArgumentException("maximumFrameBytes must be positive");
            if (maximumPendingDefinitions <= 0) {
                throw new IllegalArgumentException("maximumPendingDefinitions must be positive");
            }
            workspace = Objects.requireNonNull(workspace, "workspace");
            rawHelloJson = nonBlank(rawHelloJson, "rawHelloJson");
        }

        public long workspaceGeneration() {
            return workspace.workspace().workspaceGeneration();
        }
    }

    public sealed interface ServerFrame permits HelloFrame, DefinitionResultFrame,
            DefinitionCancelledFrame, DefinitionFailedFrame, UsageAtPositionResultFrame,
            UsageAtPositionCancelledFrame, UsageAtPositionFailedFrame,
            JavaInteractionMapResultFrame, JavaInteractionMapCancelledFrame,
            JavaInteractionMapFailedFrame, CancelledFrame,
            WorkspaceGenerationFrame, PongFrame, ShutdownFrame, ErrorFrame {
    }

    public record HelloFrame(ServerHello hello) implements ServerFrame {
        public HelloFrame { Objects.requireNonNull(hello, "hello"); }
    }

    public record DefinitionResultFrame(SFMDefinitionResult result) implements ServerFrame {
        public DefinitionResultFrame { Objects.requireNonNull(result, "result"); }
    }

    public record DefinitionCancelledFrame(
            long requestId,
            long requestGeneration,
            long workspaceGeneration,
            String reason
    ) implements ServerFrame {
        public DefinitionCancelledFrame { reason = Objects.requireNonNull(reason, "reason"); }
    }

    public record DefinitionFailedFrame(
            long requestId,
            long requestGeneration,
            long workspaceGeneration,
            String code,
            String message,
            boolean retryable
    ) implements ServerFrame {
        public DefinitionFailedFrame {
            code = nonBlank(code, "code");
            message = Objects.requireNonNull(message, "message");
        }
    }

    public record UsageAtPositionResultFrame(SFMUsageAtPositionResult result) implements ServerFrame {
        public UsageAtPositionResultFrame { Objects.requireNonNull(result, "result"); }
    }

    public record UsageAtPositionCancelledFrame(
            long requestId,
            long requestGeneration,
            long workspaceGeneration,
            String reason
    ) implements ServerFrame {
        public UsageAtPositionCancelledFrame { reason = Objects.requireNonNull(reason, "reason"); }
    }

    public record UsageAtPositionFailedFrame(
            long requestId,
            long requestGeneration,
            long workspaceGeneration,
            String code,
            String message,
            boolean retryable
    ) implements ServerFrame {
        public UsageAtPositionFailedFrame {
            code = nonBlank(code, "code");
            message = Objects.requireNonNull(message, "message");
        }
    }

    public record JavaInteractionMapResultFrame(SFMJavaInteractionMap.Result result) implements ServerFrame {
        public JavaInteractionMapResultFrame { Objects.requireNonNull(result, "result"); }
    }

    public record JavaInteractionMapCancelledFrame(
            long requestId,
            long requestGeneration,
            long workspaceGeneration,
            String reason
    ) implements ServerFrame {
        public JavaInteractionMapCancelledFrame { reason = Objects.requireNonNull(reason, "reason"); }
    }

    public record JavaInteractionMapFailedFrame(
            long requestId,
            long requestGeneration,
            long workspaceGeneration,
            String code,
            String message,
            boolean retryable
    ) implements ServerFrame {
        public JavaInteractionMapFailedFrame {
            code = nonBlank(code, "code");
            message = Objects.requireNonNull(message, "message");
        }
    }

    public record CancelledFrame(
            long requestId,
            long requestGeneration,
            long workspaceGeneration,
            String status
    ) implements ServerFrame {
        public CancelledFrame { status = wireName(status, "status", CANCELLATION_STATUSES); }
    }

    public record WorkspaceGenerationFrame(
            WorkspaceMetadata workspace,
            long cancelledRequests
    ) implements ServerFrame {
        public WorkspaceGenerationFrame { Objects.requireNonNull(workspace, "workspace"); }

        public long workspaceGeneration() {
            return workspace.workspace().workspaceGeneration();
        }
    }

    public record PongFrame(long nonce) implements ServerFrame {
    }

    public record ShutdownFrame() implements ServerFrame {
    }

    public record ErrorFrame(
            String code,
            String message,
            String disposition,
            Optional<Long> requestId,
            Optional<Long> requestGeneration
    ) implements ServerFrame {
        public ErrorFrame {
            code = nonBlank(code, "code");
            message = Objects.requireNonNull(message, "message");
            disposition = wireName(disposition, "disposition", ERROR_DISPOSITIONS);
            requestId = Objects.requireNonNull(requestId, "requestId");
            requestGeneration = Objects.requireNonNull(requestGeneration, "requestGeneration");
        }

        public boolean fatal() {
            return disposition.equals("fatal");
        }
    }

    public static String hello(
            String clientName,
            String clientVersion,
            int maximumFrameBytes
    ) {
        JsonObject hello = new JsonObject();
        hello.addProperty("protocol_schema", PROTOCOL_SCHEMA);
        hello.addProperty("client_name", nonBlank(clientName, "clientName"));
        hello.addProperty("client_version", nonBlank(clientVersion, "clientVersion"));
        JsonArray capabilities = new JsonArray();
        CLIENT_CAPABILITIES.forEach(capabilities::add);
        hello.add("capabilities", capabilities);
        hello.addProperty("max_frame_bytes", maximumFrameBytes);
        return envelope("hello", HELLO_SCHEMA, "hello", hello).toString();
    }

    public static String definition(SFMDefinitionRequest request) {
        Objects.requireNonNull(request, "request");
        return envelope(
                "definition",
                DEFINITION_SCHEMA,
                "request",
                SFMDefinitionJsonCodec.encodeRequestObject(request)
        ).toString();
    }

    public static String usageAtPosition(SFMUsageAtPositionRequest request) {
        Objects.requireNonNull(request, "request");
        return envelope(
                "usage-at-position",
                USAGE_AT_POSITION_SCHEMA,
                "request",
                SFMDefinitionJsonCodec.encodeUsageRequestObject(request)
        ).toString();
    }

    public static String javaInteractionMap(SFMJavaInteractionMap.Request request) {
        Objects.requireNonNull(request, "request");
        return envelope(
                "java-interaction-map",
                JAVA_INTERACTION_MAP_SCHEMA,
                "request",
                SFMJavaInteractionMapJsonCodec.encodeRequestObject(request)
        ).toString();
    }

    public static String cancel(SFMDefinitionRequest request, String reason) {
        Objects.requireNonNull(request, "request");
        JsonObject frame = base("cancel", CANCEL_SCHEMA);
        frame.addProperty("request_id", request.requestId());
        frame.addProperty("request_generation", request.requestGeneration());
        frame.addProperty("workspace_generation", request.workspace().workspaceGeneration());
        frame.addProperty("reason", Objects.requireNonNull(reason, "reason"));
        return frame.toString();
    }

    public static String cancel(SFMUsageAtPositionRequest request, String reason) {
        Objects.requireNonNull(request, "request");
        return cancel(
                request.requestId(),
                request.requestGeneration(),
                request.workspace().workspaceGeneration(),
                reason
        );
    }

    public static String cancel(SFMJavaInteractionMap.Request request, String reason) {
        Objects.requireNonNull(request, "request");
        return cancel(
                request.requestId(),
                request.requestGeneration(),
                request.workspace().workspaceGeneration(),
                reason
        );
    }

    private static String cancel(
            long requestId,
            long requestGeneration,
            long workspaceGeneration,
            String reason
    ) {
        JsonObject frame = base("cancel", CANCEL_SCHEMA);
        frame.addProperty("request_id", requestId);
        frame.addProperty("request_generation", requestGeneration);
        frame.addProperty("workspace_generation", workspaceGeneration);
        frame.addProperty("reason", Objects.requireNonNull(reason, "reason"));
        return frame.toString();
    }

    public static String workspaceGeneration(long generation) {
        JsonObject frame = base("workspace-generation", WORKSPACE_GENERATION_SCHEMA);
        frame.addProperty("workspace_generation", generation);
        return frame.toString();
    }

    public static String ping(long nonce) {
        JsonObject frame = base("ping", PING_SCHEMA);
        frame.addProperty("nonce", nonce);
        return frame.toString();
    }

    public static String shutdown(String reason) {
        JsonObject frame = base("shutdown", SHUTDOWN_SCHEMA);
        frame.addProperty("reason", Objects.requireNonNull(reason, "reason"));
        return frame.toString();
    }

    public static ServerFrame decodeServerFrame(String json) throws ProtocolException {
        try {
            JsonObject frame = object(JsonParser.parseString(json), "symbol-server frame");
            String kind = string(frame, "kind");
            String schema = string(frame, "schema");
            return switch (kind) {
                case "hello" -> {
                    requireSchema(schema, HELLO_SCHEMA);
                    yield new HelloFrame(readHello(requiredObject(frame, "hello")));
                }
                case "definition-result" -> {
                    requireSchema(schema, DEFINITION_SCHEMA);
                    yield new DefinitionResultFrame(SFMDefinitionJsonCodec.decodeResultObject(
                            requiredObject(frame, "result")
                    ));
                }
                case "definition-cancelled" -> {
                    requireSchema(schema, DEFINITION_SCHEMA);
                    JsonObject value = requiredObject(frame, "cancellation");
                    yield new DefinitionCancelledFrame(
                            nonNegativeLong(value, "request_id"),
                            nonNegativeLong(value, "request_generation"),
                            nonNegativeLong(value, "workspace_generation"),
                            string(value, "reason")
                    );
                }
                case "definition-failed" -> {
                    requireSchema(schema, DEFINITION_SCHEMA);
                    JsonObject value = requiredObject(frame, "error");
                    yield new DefinitionFailedFrame(
                            nonNegativeLong(value, "request_id"),
                            nonNegativeLong(value, "request_generation"),
                            nonNegativeLong(value, "workspace_generation"),
                            string(value, "code"),
                            string(value, "message"),
                            bool(value, "retryable")
                    );
                }
                case "usage-at-position-result" -> {
                    requireSchema(schema, USAGE_AT_POSITION_SCHEMA);
                    yield new UsageAtPositionResultFrame(SFMDefinitionJsonCodec.decodeUsageResultObject(
                            requiredObject(frame, "result")
                    ));
                }
                case "usage-at-position-cancelled" -> {
                    requireSchema(schema, USAGE_AT_POSITION_SCHEMA);
                    JsonObject value = requiredObject(frame, "cancellation");
                    yield new UsageAtPositionCancelledFrame(
                            nonNegativeLong(value, "request_id"),
                            nonNegativeLong(value, "request_generation"),
                            nonNegativeLong(value, "workspace_generation"),
                            string(value, "reason")
                    );
                }
                case "usage-at-position-failed" -> {
                    requireSchema(schema, USAGE_AT_POSITION_SCHEMA);
                    JsonObject value = requiredObject(frame, "error");
                    yield new UsageAtPositionFailedFrame(
                            nonNegativeLong(value, "request_id"),
                            nonNegativeLong(value, "request_generation"),
                            nonNegativeLong(value, "workspace_generation"),
                            string(value, "code"),
                            string(value, "message"),
                            bool(value, "retryable")
                    );
                }
                case "java-interaction-map-result" -> {
                    requireSchema(schema, JAVA_INTERACTION_MAP_SCHEMA);
                    yield new JavaInteractionMapResultFrame(SFMJavaInteractionMapJsonCodec.decodeResultObject(
                            requiredObject(frame, "result")
                    ));
                }
                case "java-interaction-map-cancelled" -> {
                    requireSchema(schema, JAVA_INTERACTION_MAP_SCHEMA);
                    JsonObject value = requiredObject(frame, "cancellation");
                    yield new JavaInteractionMapCancelledFrame(
                            nonNegativeLong(value, "request_id"),
                            nonNegativeLong(value, "request_generation"),
                            nonNegativeLong(value, "workspace_generation"),
                            string(value, "reason")
                    );
                }
                case "java-interaction-map-failed" -> {
                    requireSchema(schema, JAVA_INTERACTION_MAP_SCHEMA);
                    JsonObject value = requiredObject(frame, "error");
                    yield new JavaInteractionMapFailedFrame(
                            nonNegativeLong(value, "request_id"),
                            nonNegativeLong(value, "request_generation"),
                            nonNegativeLong(value, "workspace_generation"),
                            string(value, "code"),
                            string(value, "message"),
                            bool(value, "retryable")
                    );
                }
                case "cancelled" -> {
                    requireSchema(schema, CANCEL_SCHEMA);
                    JsonObject value = requiredObject(frame, "cancellation");
                    yield new CancelledFrame(
                            nonNegativeLong(value, "request_id"),
                            nonNegativeLong(value, "request_generation"),
                            nonNegativeLong(value, "workspace_generation"),
                            string(value, "status")
                    );
                }
                case "workspace-generation" -> {
                    requireSchema(schema, WORKSPACE_GENERATION_SCHEMA);
                    JsonObject value = requiredObject(frame, "update");
                    yield new WorkspaceGenerationFrame(
                            decodeWorkspaceMetadata(requiredObject(value, "workspace")),
                            nonNegativeLong(value, "cancelled_requests")
                    );
                }
                case "pong" -> {
                    requireSchema(schema, PING_SCHEMA);
                    yield new PongFrame(nonNegativeLong(frame, "nonce"));
                }
                case "shutdown" -> {
                    requireSchema(schema, SHUTDOWN_SCHEMA);
                    yield new ShutdownFrame();
                }
                case "error" -> {
                    requireSchema(schema, ERROR_SCHEMA);
                    JsonObject value = requiredObject(frame, "error");
                    yield new ErrorFrame(
                            string(value, "code"),
                            string(value, "message"),
                            string(value, "disposition"),
                            optionalLong(value, "request_id"),
                            optionalLong(value, "request_generation")
                    );
                }
                default -> throw new ProtocolException("Unknown symbol-server frame kind: " + bounded(kind));
            };
        } catch (ProtocolException failure) {
            throw failure;
        } catch (JsonParseException | IllegalArgumentException failure) {
            throw new ProtocolException("Invalid symbol-server JSON envelope", failure);
        }
    }

    private static ServerHello readHello(JsonObject hello) throws ProtocolException {
        String protocolSchema = string(hello, "protocol_schema");
        String serverName = string(hello, "server_name");
        String serverVersion = string(hello, "server_version");
        Set<String> capabilities = Set.copyOf(strings(hello, "capabilities"));
        int maximumFrameBytes = positiveInt(hello, "max_frame_bytes");
        int maximumPending = positiveInt(hello, "max_pending_definitions");
        WorkspaceMetadata workspace = decodeWorkspaceMetadata(requiredObject(hello, "workspace"));
        try {
            return new ServerHello(
                    protocolSchema,
                    serverName,
                    serverVersion,
                    capabilities,
                    maximumFrameBytes,
                    maximumPending,
                    workspace,
                    hello.deepCopy().toString()
            );
        } catch (IllegalArgumentException failure) {
            throw new ProtocolException("Invalid symbol-server hello", failure);
        }
    }

    private static WorkspaceMetadata decodeWorkspaceMetadata(JsonObject workspaceJson)
            throws ProtocolException {
        JsonObject identity = requiredObject(workspaceJson, "request_workspace");
        List<SFMDefinitionRequest.SourceRoot> roots = new ArrayList<>();
        for (JsonObject root : objects(identity, "source_roots")) {
            roots.add(new SFMDefinitionRequest.SourceRoot(
                    string(root, "id"),
                    string(root, "source_set"),
                    string(root, "path"),
                    string(root, "kind"),
                    bool(root, "exists")
            ));
        }
        SFMDefinitionRequest.Workspace workspace = new SFMDefinitionRequest.Workspace(
                string(identity, "branch"),
                SFMDefinitionRequest.ClasspathMode.fromWireName(string(identity, "classpath_mode")),
                roots,
                string(identity, "classpath_fingerprint"),
                optionalString(identity, "dependency_index_identity"),
                string(identity, "workspace_fingerprint"),
                nonNegativeLong(identity, "workspace_generation")
        );

        JsonArray mappingValues = requiredArray(workspaceJson, "roots");
        ArrayList<SourceRootMapping> mappings = new ArrayList<>();
        for (JsonElement element : mappingValues) {
            JsonObject mapping = object(element, "roots[]");
            mappings.add(new SourceRootMapping(
                    string(mapping, "canonical_absolute_path"),
                    string(mapping, "root_id"),
                    string(mapping, "source_set"),
                    string(mapping, "report_root_path")
            ));
        }
        ArrayList<DependencySourceRootMapping> dependencyMappings = new ArrayList<>();
        if (workspaceJson.has("dependency_source_roots")) {
            for (JsonElement element : requiredArray(workspaceJson, "dependency_source_roots")) {
                JsonObject mapping = object(element, "dependency_source_roots[]");
                dependencyMappings.add(new DependencySourceRootMapping(
                        string(mapping, "canonical_absolute_path"),
                        string(mapping, "root_id"),
                        string(mapping, "source_set"),
                        string(mapping, "report_prefix")
                ));
            }
        }
        ArrayList<ManagedSourceRootMapping> managedMappings = new ArrayList<>();
        if (workspaceJson.has("managed_source_roots")) {
            for (JsonElement element : requiredArray(workspaceJson, "managed_source_roots")) {
                JsonObject mapping = object(element, "managed_source_roots[]");
                managedMappings.add(new ManagedSourceRootMapping(
                        string(mapping, "resolver_id"),
                        string(mapping, "address_scheme"),
                        string(mapping, "resolver_identity"),
                        string(mapping, "canonical_absolute_path"),
                        string(mapping, "root_id"),
                        string(mapping, "source_set"),
                        optionalString(mapping, "portable_root_path"),
                        optionalString(mapping, "report_prefix")
                ));
            }
        }
        try {
            return new WorkspaceMetadata(workspace, mappings, dependencyMappings, managedMappings);
        } catch (IllegalArgumentException failure) {
            throw new ProtocolException("Invalid resolved workspace metadata", failure);
        }
    }

    private static JsonObject base(String kind, String schema) {
        JsonObject frame = new JsonObject();
        frame.addProperty("kind", kind);
        frame.addProperty("schema", schema);
        return frame;
    }

    private static JsonObject envelope(String kind, String schema, String field, JsonObject value) {
        JsonObject frame = base(kind, schema);
        frame.add(field, value);
        return frame;
    }

    private static void requireSchema(String actual, String expected) throws ProtocolException {
        if (!expected.equals(actual)) {
            throw new ProtocolException("Symbol-server frame schema does not match its kind");
        }
    }

    private static int positiveInt(JsonObject json, String name) throws ProtocolException {
        long value = nonNegativeLong(json, name);
        if (value == 0 || value > Integer.MAX_VALUE) {
            throw new ProtocolException(name + " is outside the supported positive integer range");
        }
        return (int) value;
    }

    private static long nonNegativeLong(JsonObject json, String name) throws ProtocolException {
        JsonElement value = required(json, name);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new ProtocolException(name + " must be a non-negative JSON integer");
        }
        String encoded = value.getAsString();
        if (!encoded.matches("0|[1-9][0-9]*")) {
            throw new ProtocolException(name + " must be a non-negative JSON integer");
        }
        try {
            return Long.parseLong(encoded);
        } catch (NumberFormatException failure) {
            throw new ProtocolException(name + " exceeds Java's supported u64 range", failure);
        }
    }

    private static Optional<Long> optionalLong(JsonObject json, String name) throws ProtocolException {
        if (!json.has(name) || json.get(name) instanceof JsonNull) return Optional.empty();
        return Optional.of(nonNegativeLong(json, name));
    }

    private static boolean bool(JsonObject json, String name) throws ProtocolException {
        JsonElement value = required(json, name);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new ProtocolException(name + " must be a JSON boolean");
        }
        return value.getAsBoolean();
    }

    private static String string(JsonObject json, String name) throws ProtocolException {
        JsonElement value = required(json, name);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new ProtocolException(name + " must be a JSON string");
        }
        return value.getAsString();
    }

    private static Optional<String> optionalString(JsonObject json, String name) throws ProtocolException {
        if (!json.has(name) || json.get(name) instanceof JsonNull) return Optional.empty();
        return Optional.of(string(json, name));
    }

    private static List<String> strings(JsonObject json, String name) throws ProtocolException {
        JsonArray values = requiredArray(json, name);
        ArrayList<String> result = new ArrayList<>(values.size());
        for (JsonElement value : values) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                throw new ProtocolException(name + " contains a non-string value");
            }
            result.add(value.getAsString());
        }
        return List.copyOf(result);
    }

    private static List<JsonObject> objects(JsonObject json, String name) throws ProtocolException {
        JsonArray values = requiredArray(json, name);
        ArrayList<JsonObject> result = new ArrayList<>(values.size());
        for (JsonElement value : values) result.add(object(value, name + "[]"));
        return List.copyOf(result);
    }

    private static JsonElement required(JsonObject json, String name) throws ProtocolException {
        if (!json.has(name) || json.get(name) instanceof JsonNull) {
            throw new ProtocolException("Missing JSON field: " + name);
        }
        return json.get(name);
    }

    private static JsonObject requiredObject(JsonObject json, String name) throws ProtocolException {
        return object(required(json, name), name);
    }

    private static Optional<JsonObject> optionalObject(JsonObject json, String name)
            throws ProtocolException {
        if (!json.has(name) || json.get(name) instanceof JsonNull) return Optional.empty();
        return Optional.of(object(json.get(name), name));
    }

    private static JsonArray requiredArray(JsonObject json, String name) throws ProtocolException {
        JsonElement value = required(json, name);
        if (!value.isJsonArray()) throw new ProtocolException(name + " must be a JSON array");
        return value.getAsJsonArray();
    }

    private static JsonObject object(JsonElement value, String label) throws ProtocolException {
        if (!value.isJsonObject()) throw new ProtocolException(label + " must be a JSON object");
        return value.getAsJsonObject();
    }

    private static String canonicalFileAddress(String value) {
        String candidate = nonBlank(value, "canonicalAddress");
        if (candidate.startsWith("file://")) {
            return ca.teamdman.sfm.client.explorer.SFMPath.parse(candidate).canonical();
        }
        return ca.teamdman.sfm.client.explorer.SFMPath.fromNative(Path.of(candidate)).canonical();
    }

    private static String normalizeCanonicalAbsolutePath(String value) {
        String candidate = ordinaryWindowsPath(nonBlank(value, "canonicalAbsolutePath"));
        Path path = Path.of(candidate);
        if (!path.isAbsolute()) throw new IllegalArgumentException("canonicalAbsolutePath must be absolute");
        return path.normalize().toString();
    }

    /**
     * Java NIO accepts ordinary drive and UNC paths but rejects the equivalent
     * Win32 extended-length spelling emitted by Rust canonicalization.
     */
    static String ordinaryWindowsPath(String value) {
        return ca.teamdman.sfm.common.util.SFMNativePaths.ordinaryWindowsPath(value);
    }

    private static String canonicalRelativePath(String value, String label, boolean allowEmpty) {
        Objects.requireNonNull(value, label);
        String normalized = value.replace('\\', '/');
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        if (normalized.isEmpty()) {
            if (allowEmpty) return normalized;
            throw new IllegalArgumentException(label + " must not be empty");
        }
        for (String segment : normalized.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException(label + " is not canonical");
            }
        }
        return normalized;
    }

    private static String nonBlank(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isBlank()) throw new IllegalArgumentException(label + " must not be blank");
        return value;
    }

    private static String wireName(String value, String label, Set<String> allowed) {
        String checked = nonBlank(value, label);
        if (!allowed.contains(checked)) throw new IllegalArgumentException("Unknown " + label + ": " + checked);
        return checked;
    }

    private static String bounded(String value) {
        if (value.length() <= 80) return value;
        return value.substring(0, 80);
    }

    public static final class ProtocolException extends IOException {
        public ProtocolException(String message) {
            super(message);
        }

        public ProtocolException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
