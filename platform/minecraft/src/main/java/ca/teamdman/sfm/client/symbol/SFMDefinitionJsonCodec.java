package ca.teamdman.sfm.client.symbol;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Strict snake-case JSON adapter shared with the Rust Facet contracts. */
public final class SFMDefinitionJsonCodec {
    private SFMDefinitionJsonCodec() {
    }

    public static String encodeRequest(SFMDefinitionRequest request) {
        return writeRequest(request).toString();
    }

    public static SFMDefinitionRequest decodeRequest(String json) {
        return readRequest(object(JsonParser.parseString(json), "request"));
    }

    public static String encodeResult(SFMDefinitionResult result) {
        return writeResult(result).toString();
    }

    public static SFMDefinitionResult decodeResult(String json) {
        return readResult(object(JsonParser.parseString(json), "result"));
    }

    public static String encodeUsageRequest(SFMUsageAtPositionRequest request) {
        return writeUsageRequest(request).toString();
    }

    public static SFMUsageAtPositionRequest decodeUsageRequest(String json) {
        return readUsageRequest(object(JsonParser.parseString(json), "usage request"));
    }

    public static String encodeUsageResult(SFMUsageAtPositionResult result) {
        return writeUsageResult(result).toString();
    }

    public static SFMUsageAtPositionResult decodeUsageResult(String json) {
        return readUsageResult(object(JsonParser.parseString(json), "usage result"));
    }

    /** Package-local zero-copy envelope hook for the symbol-server protocol. */
    static JsonObject encodeRequestObject(SFMDefinitionRequest request) {
        return writeRequest(request);
    }

    /** Package-local envelope hook that keeps result parsing in this one codec. */
    static SFMDefinitionResult decodeResultObject(JsonObject json) {
        return readResult(json);
    }

    static JsonObject encodeUsageRequestObject(SFMUsageAtPositionRequest request) {
        return writeUsageRequest(request);
    }

    static SFMUsageAtPositionResult decodeUsageResultObject(JsonObject json) {
        return readUsageResult(json);
    }

    private static JsonObject writeRequest(SFMDefinitionRequest value) {
        JsonObject json = new JsonObject();
        json.addProperty("schema", value.schema());
        json.addProperty("request_id", value.requestId());
        json.addProperty("request_generation", value.requestGeneration());
        json.add("workspace", writeWorkspace(value.workspace()));
        json.add("document", writeDocument(value.document()));
        json.add("position", writePosition(value.position()));
        return json;
    }

    private static SFMDefinitionRequest readRequest(JsonObject json) {
        return new SFMDefinitionRequest(
                string(json, "schema"),
                longValue(json, "request_id"),
                longValue(json, "request_generation"),
                readWorkspace(requiredObject(json, "workspace")),
                readDocument(requiredObject(json, "document")),
                readPosition(requiredObject(json, "position"))
        );
    }

    private static JsonObject writeUsageRequest(SFMUsageAtPositionRequest value) {
        JsonObject json = new JsonObject();
        json.addProperty("schema", value.schema());
        json.addProperty("request_id", value.requestId());
        json.addProperty("request_generation", value.requestGeneration());
        json.add("workspace", writeWorkspace(value.workspace()));
        json.add("document", writeDocument(value.document()));
        json.add("position", writePosition(value.position()));
        return json;
    }

    private static SFMUsageAtPositionRequest readUsageRequest(JsonObject json) {
        return new SFMUsageAtPositionRequest(
                string(json, "schema"),
                longValue(json, "request_id"),
                longValue(json, "request_generation"),
                readWorkspace(requiredObject(json, "workspace")),
                readDocument(requiredObject(json, "document")),
                readPosition(requiredObject(json, "position"))
        );
    }

    private static JsonObject writeWorkspace(SFMDefinitionRequest.Workspace value) {
        JsonObject json = new JsonObject();
        json.addProperty("branch", value.branch());
        json.addProperty("classpath_mode", value.classpathMode().wireName());
        JsonArray roots = new JsonArray();
        value.sourceRoots().forEach(root -> roots.add(writeSourceRoot(root)));
        json.add("source_roots", roots);
        json.addProperty("classpath_fingerprint", value.classpathFingerprint());
        addOptional(json, "dependency_index_identity", value.dependencyIndexIdentity());
        json.addProperty("workspace_fingerprint", value.workspaceFingerprint());
        json.addProperty("workspace_generation", value.workspaceGeneration());
        return json;
    }

    private static SFMDefinitionRequest.Workspace readWorkspace(JsonObject json) {
        return new SFMDefinitionRequest.Workspace(
                string(json, "branch"),
                SFMDefinitionRequest.ClasspathMode.fromWireName(string(json, "classpath_mode")),
                objects(json, "source_roots").stream().map(SFMDefinitionJsonCodec::readSourceRoot).toList(),
                string(json, "classpath_fingerprint"),
                optionalString(json, "dependency_index_identity"),
                string(json, "workspace_fingerprint"),
                longValue(json, "workspace_generation")
        );
    }

    private static JsonObject writeSourceRoot(SFMDefinitionRequest.SourceRoot value) {
        JsonObject json = new JsonObject();
        json.addProperty("id", value.id());
        json.addProperty("source_set", value.sourceSet());
        json.addProperty("path", value.path());
        json.addProperty("kind", value.kind());
        json.addProperty("exists", value.exists());
        return json;
    }

    private static SFMDefinitionRequest.SourceRoot readSourceRoot(JsonObject json) {
        return new SFMDefinitionRequest.SourceRoot(
                string(json, "id"),
                string(json, "source_set"),
                string(json, "path"),
                string(json, "kind"),
                bool(json, "exists")
        );
    }

    private static JsonObject writeDocument(SFMDefinitionRequest.Document value) {
        JsonObject json = new JsonObject();
        json.addProperty("address", value.address());
        json.addProperty("root_id", value.rootId());
        json.addProperty("root_relative_path", value.rootRelativePath());
        json.addProperty("report_path", value.reportPath());
        json.addProperty("source_set", value.sourceSet());
        json.addProperty("text", value.text());
        json.addProperty("content_hash", value.contentHash());
        addOptional(json, "disk_content_hash", value.diskContentHash());
        return json;
    }

    private static SFMDefinitionRequest.Document readDocument(JsonObject json) {
        return new SFMDefinitionRequest.Document(
                string(json, "address"),
                string(json, "root_id"),
                string(json, "root_relative_path"),
                string(json, "report_path"),
                string(json, "source_set"),
                string(json, "text"),
                string(json, "content_hash"),
                optionalString(json, "disk_content_hash")
        );
    }

    private static JsonObject writePosition(SFMDefinitionRequest.Position value) {
        JsonObject json = new JsonObject();
        json.addProperty("line", value.line());
        json.addProperty("column", value.column());
        json.addProperty("byte_offset", value.byteOffset());
        return json;
    }

    private static SFMDefinitionRequest.Position readPosition(JsonObject json) {
        return new SFMDefinitionRequest.Position(
                longValue(json, "line"),
                longValue(json, "column"),
                longValue(json, "byte_offset")
        );
    }

    private static JsonObject writeResult(SFMDefinitionResult value) {
        JsonObject json = new JsonObject();
        json.addProperty("schema", value.schema());
        json.addProperty("request_id", value.requestId());
        json.addProperty("request_generation", value.requestGeneration());
        json.addProperty("workspace_generation", value.workspaceGeneration());
        json.addProperty("outcome", value.outcome().wireName());
        json.add("context", writeContext(value.context()));
        json.add("document", writeDocumentIdentity(value.document()));
        json.add("position", writePosition(value.position()));
        json.add("symbols", array(value.symbols().stream().map(SFMDefinitionJsonCodec::writeSymbol).toList()));
        json.add("definitions", array(value.definitions().stream().map(SFMDefinitionJsonCodec::writeDefinition).toList()));
        json.addProperty("completeness", value.completeness().wireName());
        json.add("diagnostics", array(value.diagnostics().stream().map(SFMDefinitionJsonCodec::writeDiagnostic).toList()));
        json.add("recovery_actions", array(value.recoveryActions().stream()
                .map(SFMDefinitionJsonCodec::writeRecoveryAction).toList()));
        value.dependencyIndex().ifPresent(index -> json.add("dependency_index", writeDependencyIndex(index)));
        return json;
    }

    private static SFMDefinitionResult readResult(JsonObject json) {
        return new SFMDefinitionResult(
                string(json, "schema"),
                longValue(json, "request_id"),
                longValue(json, "request_generation"),
                longValue(json, "workspace_generation"),
                SFMDefinitionResult.Outcome.fromWireName(string(json, "outcome")),
                readContext(requiredObject(json, "context")),
                readDocumentIdentity(requiredObject(json, "document")),
                readPosition(requiredObject(json, "position")),
                objects(json, "symbols").stream().map(SFMDefinitionJsonCodec::readSymbol).toList(),
                objects(json, "definitions").stream().map(SFMDefinitionJsonCodec::readDefinition).toList(),
                SFMDefinitionResult.Completeness.fromWireName(string(json, "completeness")),
                objects(json, "diagnostics").stream().map(SFMDefinitionJsonCodec::readDiagnostic).toList(),
                objects(json, "recovery_actions").stream()
                        .map(SFMDefinitionJsonCodec::readRecoveryAction).toList(),
                optionalObject(json, "dependency_index").map(SFMDefinitionJsonCodec::readDependencyIndex)
        );
    }

    private static JsonObject writeUsageResult(SFMUsageAtPositionResult value) {
        JsonObject json = new JsonObject();
        json.addProperty("schema", value.schema());
        json.addProperty("request_id", value.requestId());
        json.addProperty("request_generation", value.requestGeneration());
        json.addProperty("workspace_generation", value.workspaceGeneration());
        json.addProperty("outcome", value.outcome().wireName());
        json.add("context", writeContext(value.context()));
        json.add("document", writeDocumentIdentity(value.document()));
        json.add("position", writePosition(value.position()));
        json.add("targets", array(value.targets().stream().map(SFMDefinitionJsonCodec::writeSymbol).toList()));
        json.add("definitions", array(value.definitions().stream()
                .map(SFMDefinitionJsonCodec::writeDefinition).toList()));
        json.add("usages", array(value.usages().stream().map(SFMDefinitionJsonCodec::writeUsage).toList()));
        json.add("skipped_categories", array(value.skippedCategories().stream()
                .map(SFMDefinitionJsonCodec::writeSkippedCategory).toList()));
        json.addProperty("completeness", value.completeness().wireName());
        json.add("diagnostics", array(value.diagnostics().stream()
                .map(SFMDefinitionJsonCodec::writeDiagnostic).toList()));
        json.add("recovery_actions", array(value.recoveryActions().stream()
                .map(SFMDefinitionJsonCodec::writeRecoveryAction).toList()));
        value.dependencyIndex().ifPresent(index -> json.add("dependency_index", writeDependencyIndex(index)));
        return json;
    }

    private static SFMUsageAtPositionResult readUsageResult(JsonObject json) {
        return new SFMUsageAtPositionResult(
                string(json, "schema"),
                longValue(json, "request_id"),
                longValue(json, "request_generation"),
                longValue(json, "workspace_generation"),
                SFMDefinitionResult.Outcome.fromWireName(string(json, "outcome")),
                readContext(requiredObject(json, "context")),
                readDocumentIdentity(requiredObject(json, "document")),
                readPosition(requiredObject(json, "position")),
                objects(json, "targets").stream().map(SFMDefinitionJsonCodec::readSymbol).toList(),
                objects(json, "definitions").stream().map(SFMDefinitionJsonCodec::readDefinition).toList(),
                objects(json, "usages").stream().map(SFMDefinitionJsonCodec::readUsage).toList(),
                objects(json, "skipped_categories").stream()
                        .map(SFMDefinitionJsonCodec::readSkippedCategory).toList(),
                SFMDefinitionResult.Completeness.fromWireName(string(json, "completeness")),
                objects(json, "diagnostics").stream().map(SFMDefinitionJsonCodec::readDiagnostic).toList(),
                objects(json, "recovery_actions").stream()
                        .map(SFMDefinitionJsonCodec::readRecoveryAction).toList(),
                optionalObject(json, "dependency_index").map(SFMDefinitionJsonCodec::readDependencyIndex)
        );
    }

    private static JsonObject writeContext(SFMDefinitionResult.AnalysisContext value) {
        JsonObject json = new JsonObject();
        json.addProperty("branch", value.branch());
        json.addProperty("minecraft_version", value.minecraftVersion());
        json.addProperty("java_release", value.javaRelease());
        json.addProperty("jdk", value.jdk());
        json.add("source_roots", array(value.sourceRoots().stream()
                .map(SFMDefinitionJsonCodec::writeSourceRoot).toList()));
        json.add("source_sets", array(value.sourceSets().stream().map(SFMDefinitionJsonCodec::writeSourceSet).toList()));
        json.add("source_exclusions", array(value.sourceExclusions().stream()
                .map(SFMDefinitionJsonCodec::writeSourceExclusion).toList()));
        json.addProperty("classpath_mode", value.classpathMode().wireName());
        json.addProperty("classpath_fingerprint", value.classpathFingerprint());
        json.addProperty("parser_fingerprint", value.parserFingerprint());
        json.addProperty("index_fingerprint", value.indexFingerprint());
        return json;
    }

    private static SFMDefinitionResult.AnalysisContext readContext(JsonObject json) {
        return new SFMDefinitionResult.AnalysisContext(
                string(json, "branch"),
                string(json, "minecraft_version"),
                string(json, "java_release"),
                string(json, "jdk"),
                objects(json, "source_roots").stream().map(SFMDefinitionJsonCodec::readSourceRoot).toList(),
                objects(json, "source_sets").stream().map(SFMDefinitionJsonCodec::readSourceSet).toList(),
                objects(json, "source_exclusions").stream()
                        .map(SFMDefinitionJsonCodec::readSourceExclusion).toList(),
                SFMDefinitionRequest.ClasspathMode.fromWireName(string(json, "classpath_mode")),
                string(json, "classpath_fingerprint"),
                string(json, "parser_fingerprint"),
                string(json, "index_fingerprint")
        );
    }

    private static JsonObject writeSourceSet(SFMDefinitionResult.SourceSet value) {
        JsonObject json = new JsonObject();
        json.addProperty("id", value.id());
        json.add("visible_source_sets", strings(value.visibleSourceSets()));
        return json;
    }

    private static SFMDefinitionResult.SourceSet readSourceSet(JsonObject json) {
        return new SFMDefinitionResult.SourceSet(string(json, "id"), stringList(json, "visible_source_sets"));
    }

    private static JsonObject writeSourceExclusion(SFMDefinitionResult.SourceExclusion value) {
        JsonObject json = new JsonObject();
        json.addProperty("source_set", value.sourceSet());
        json.addProperty("path", value.path());
        json.addProperty("origin", value.origin());
        return json;
    }

    private static SFMDefinitionResult.SourceExclusion readSourceExclusion(JsonObject json) {
        return new SFMDefinitionResult.SourceExclusion(
                string(json, "source_set"), string(json, "path"), string(json, "origin")
        );
    }

    private static JsonObject writeDocumentIdentity(SFMDefinitionResult.DocumentIdentity value) {
        JsonObject json = new JsonObject();
        json.addProperty("address", value.address());
        json.addProperty("root_id", value.rootId());
        json.addProperty("root_relative_path", value.rootRelativePath());
        json.addProperty("report_path", value.reportPath());
        json.addProperty("source_set", value.sourceSet());
        json.addProperty("content_hash", value.contentHash());
        addOptional(json, "disk_content_hash", value.diskContentHash());
        return json;
    }

    private static SFMDefinitionResult.DocumentIdentity readDocumentIdentity(JsonObject json) {
        return new SFMDefinitionResult.DocumentIdentity(
                string(json, "address"), string(json, "root_id"),
                string(json, "root_relative_path"), string(json, "report_path"),
                string(json, "source_set"), string(json, "content_hash"),
                optionalString(json, "disk_content_hash")
        );
    }

    private static JsonObject writeSymbol(SFMDefinitionResult.SymbolIdentity value) {
        JsonObject json = new JsonObject();
        json.addProperty("kind", value.kind());
        json.addProperty("owner", value.owner());
        json.addProperty("name", value.name());
        addOptional(json, "descriptor", value.descriptor());
        json.addProperty("qualified_name", value.qualifiedName());
        return json;
    }

    private static SFMDefinitionResult.SymbolIdentity readSymbol(JsonObject json) {
        return new SFMDefinitionResult.SymbolIdentity(
                string(json, "kind"), string(json, "owner"), string(json, "name"),
                optionalString(json, "descriptor"), string(json, "qualified_name")
        );
    }

    private static JsonObject writeSpan(SFMDefinitionResult.SourceSpan value) {
        JsonObject json = new JsonObject();
        json.addProperty("path", value.path());
        json.addProperty("source_set", value.sourceSet());
        json.addProperty("source_hash", value.sourceHash());
        json.addProperty("start_byte", value.startByte());
        json.addProperty("end_byte", value.endByte());
        json.addProperty("start_line", value.startLine());
        json.addProperty("start_column", value.startColumn());
        json.addProperty("end_line", value.endLine());
        json.addProperty("end_column", value.endColumn());
        return json;
    }

    private static SFMDefinitionResult.SourceSpan readSpan(JsonObject json) {
        return new SFMDefinitionResult.SourceSpan(
                string(json, "path"), string(json, "source_set"), string(json, "source_hash"),
                longValue(json, "start_byte"), longValue(json, "end_byte"),
                longValue(json, "start_line"), longValue(json, "start_column"),
                longValue(json, "end_line"), longValue(json, "end_column")
        );
    }

    private static JsonObject writeDefinitionSpan(SFMDefinitionResult.DefinitionSourceSpan value) {
        JsonObject json = new JsonObject();
        json.addProperty("address", value.address());
        json.addProperty("resolver_id", value.resolverId());
        json.addProperty("root_id", value.rootId());
        json.addProperty("root_relative_path", value.rootRelativePath());
        json.addProperty("report_path", value.reportPath());
        json.addProperty("source_set", value.sourceSet());
        json.addProperty("source_hash", value.sourceHash());
        addOptional(json, "source_sha256", value.sourceSha256());
        json.addProperty("start_byte", value.startByte());
        json.addProperty("end_byte", value.endByte());
        json.addProperty("start_line", value.startLine());
        json.addProperty("start_column", value.startColumn());
        json.addProperty("end_line", value.endLine());
        json.addProperty("end_column", value.endColumn());
        return json;
    }

    private static SFMDefinitionResult.DefinitionSourceSpan readDefinitionSpan(JsonObject json) {
        return new SFMDefinitionResult.DefinitionSourceSpan(
                string(json, "address"), string(json, "resolver_id"), string(json, "root_id"),
                string(json, "root_relative_path"), string(json, "report_path"),
                string(json, "source_set"), string(json, "source_hash"),
                optionalString(json, "source_sha256"),
                longValue(json, "start_byte"), longValue(json, "end_byte"),
                longValue(json, "start_line"), longValue(json, "start_column"),
                longValue(json, "end_line"), longValue(json, "end_column")
        );
    }

    private static JsonObject writeDefinition(SFMDefinitionResult.Definition value) {
        JsonObject json = new JsonObject();
        json.add("symbol", writeSymbol(value.symbol()));
        json.add("identifier_span", writeDefinitionSpan(value.identifierSpan()));
        json.add("declaration_span", writeDefinitionSpan(value.declarationSpan()));
        json.addProperty("confidence", value.confidence());
        return json;
    }

    private static SFMDefinitionResult.Definition readDefinition(JsonObject json) {
        return new SFMDefinitionResult.Definition(
                readSymbol(requiredObject(json, "symbol")),
                readDefinitionSpan(requiredObject(json, "identifier_span")),
                readDefinitionSpan(requiredObject(json, "declaration_span")),
                string(json, "confidence")
        );
    }

    private static JsonObject writeUsage(SFMUsageAtPositionResult.Usage value) {
        JsonObject json = new JsonObject();
        json.add("target", writeSymbol(value.target()));
        json.addProperty("kind", value.kind().wireName());
        json.add("span", writeDefinitionSpan(value.span()));
        json.addProperty("confidence", value.confidence());
        return json;
    }

    private static SFMUsageAtPositionResult.Usage readUsage(JsonObject json) {
        return new SFMUsageAtPositionResult.Usage(
                readSymbol(requiredObject(json, "target")),
                SFMUsageAtPositionResult.UsageKind.fromWireName(string(json, "kind")),
                readDefinitionSpan(requiredObject(json, "span")),
                string(json, "confidence")
        );
    }

    private static JsonObject writeSkippedCategory(SFMUsageAtPositionResult.SkippedCategory value) {
        JsonObject json = new JsonObject();
        json.addProperty("category", value.category().wireName());
        json.addProperty("reason", value.reason());
        return json;
    }

    private static SFMUsageAtPositionResult.SkippedCategory readSkippedCategory(JsonObject json) {
        return new SFMUsageAtPositionResult.SkippedCategory(
                SFMUsageAtPositionResult.SkippedCategoryKind.fromWireName(string(json, "category")),
                string(json, "reason")
        );
    }

    private static JsonObject writeDiagnostic(SFMDefinitionResult.Diagnostic value) {
        JsonObject json = new JsonObject();
        json.addProperty("code", value.code());
        json.addProperty("severity", value.severity());
        json.addProperty("message", value.message());
        value.span().ifPresent(span -> json.add("span", writeSpan(span)));
        return json;
    }

    private static SFMDefinitionResult.Diagnostic readDiagnostic(JsonObject json) {
        return new SFMDefinitionResult.Diagnostic(
                string(json, "code"), string(json, "severity"), string(json, "message"),
                optionalObject(json, "span").map(SFMDefinitionJsonCodec::readSpan)
        );
    }

    private static JsonObject writeRecoveryAction(SFMDefinitionResult.RecoveryAction value) {
        JsonObject json = new JsonObject();
        json.addProperty("kind", value.kind().wireName());
        json.addProperty("label", value.label());
        addOptional(json, "command", value.command());
        return json;
    }

    private static SFMDefinitionResult.RecoveryAction readRecoveryAction(JsonObject json) {
        return new SFMDefinitionResult.RecoveryAction(
                SFMDefinitionResult.RecoveryActionKind.fromWireName(string(json, "kind")),
                string(json, "label"),
                optionalString(json, "command")
        );
    }

    private static JsonObject writeDependencyIndex(SFMDefinitionResult.DependencyIndex value) {
        JsonObject json = new JsonObject();
        json.addProperty("status", value.status());
        json.addProperty("completeness", value.completeness().wireName());
        json.addProperty("expected_identity", value.expectedIdentity());
        json.addProperty("portable_path", value.portablePath());
        json.addProperty("path", value.path());
        json.addProperty("reason", value.reason());
        json.addProperty("refresh_command", value.refreshCommand());
        json.add("acquisition_commands", strings(value.acquisitionCommands()));
        return json;
    }

    private static SFMDefinitionResult.DependencyIndex readDependencyIndex(JsonObject json) {
        return new SFMDefinitionResult.DependencyIndex(
                string(json, "status"),
                SFMDefinitionResult.Completeness.fromWireName(string(json, "completeness")),
                string(json, "expected_identity"), string(json, "portable_path"),
                string(json, "path"), string(json, "reason"),
                string(json, "refresh_command"), stringList(json, "acquisition_commands")
        );
    }

    private static void addOptional(JsonObject json, String name, Optional<String> value) {
        value.ifPresent(text -> json.addProperty(name, text));
    }

    private static JsonArray array(List<JsonObject> values) {
        JsonArray result = new JsonArray();
        values.forEach(result::add);
        return result;
    }

    private static JsonArray strings(List<String> values) {
        JsonArray result = new JsonArray();
        values.forEach(result::add);
        return result;
    }

    private static List<JsonObject> objects(JsonObject json, String name) {
        JsonArray values = requiredArray(json, name);
        ArrayList<JsonObject> result = new ArrayList<>(values.size());
        for (JsonElement value : values) result.add(object(value, name + "[]"));
        return List.copyOf(result);
    }

    private static List<String> stringList(JsonObject json, String name) {
        JsonArray values = requiredArray(json, name);
        ArrayList<String> result = new ArrayList<>(values.size());
        for (JsonElement value : values) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException(name + " contains a non-string value");
            }
            result.add(value.getAsString());
        }
        return List.copyOf(result);
    }

    private static String string(JsonObject json, String name) {
        JsonElement value = required(json, name);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(name + " must be a JSON string");
        }
        return value.getAsString();
    }

    private static Optional<String> optionalString(JsonObject json, String name) {
        if (!json.has(name) || json.get(name) instanceof JsonNull) return Optional.empty();
        return Optional.of(string(json, name));
    }

    private static long longValue(JsonObject json, String name) {
        JsonElement value = required(json, name);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(name + " must be a non-negative JSON integer");
        }
        String encoded = value.getAsString();
        if (!encoded.matches("0|[1-9][0-9]*")) {
            throw new IllegalArgumentException(name + " must be a non-negative JSON integer");
        }
        try {
            return Long.parseLong(encoded);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(name + " exceeds Java's supported u64 range", error);
        }
    }

    private static boolean bool(JsonObject json, String name) {
        JsonElement value = required(json, name);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(name + " must be a JSON boolean");
        }
        return value.getAsBoolean();
    }

    private static JsonObject requiredObject(JsonObject json, String name) {
        return object(required(json, name), name);
    }

    private static Optional<JsonObject> optionalObject(JsonObject json, String name) {
        if (!json.has(name) || json.get(name) instanceof JsonNull) return Optional.empty();
        return Optional.of(object(json.get(name), name));
    }

    private static JsonObject object(JsonElement value, String label) {
        if (value == null || !value.isJsonObject()) {
            throw new IllegalArgumentException(label + " must be a JSON object");
        }
        return value.getAsJsonObject();
    }

    private static JsonArray requiredArray(JsonObject json, String name) {
        JsonElement value = required(json, name);
        if (!value.isJsonArray()) throw new IllegalArgumentException(name + " must be a JSON array");
        return value.getAsJsonArray();
    }

    private static JsonElement required(JsonObject json, String name) {
        if (!json.has(name) || json.get(name) instanceof JsonNull) {
            throw new IllegalArgumentException("Missing JSON field: " + name);
        }
        return json.get(name);
    }
}
