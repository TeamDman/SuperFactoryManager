package ca.teamdman.sfm.client.semantic;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonSerializationContext;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Strict snake-case JSON codec for the spatial semantic version-one bundle. */
public final class SFMSpatialSemanticJsonCodec {
    private static final Gson GSON = builder().create();
    private static final Gson PRETTY_GSON = builder().setPrettyPrinting().create();

    private SFMSpatialSemanticJsonCodec() {
    }

    public static String encode(SFMSpatialSemanticContract.Bundle value) {
        return GSON.toJson(value);
    }

    public static String encodePretty(SFMSpatialSemanticContract.Bundle value) {
        return PRETTY_GSON.toJson(value) + "\n";
    }

    public static String encodeCoverageRequest(SFMSpatialSemanticContract.CoverageRequest value) {
        return PRETTY_GSON.toJson(value) + "\n";
    }

    public static String encodeCoverageReport(SFMSpatialSemanticContract.CoverageReport value) {
        return PRETTY_GSON.toJson(value) + "\n";
    }

    public static SFMSpatialSemanticContract.Bundle decode(String json) {
        return readBundle(object(JsonParser.parseString(json), "spatial semantic bundle"));
    }

    private static SFMSpatialSemanticContract.Bundle readBundle(JsonObject json) {
        return new SFMSpatialSemanticContract.Bundle(
                readDomain(requiredObject(json, "domain")),
                readRegion(requiredObject(json, "region")),
                readProjection(requiredObject(json, "projection")),
                readOutlink(requiredObject(json, "outlink")),
                readProbe(requiredObject(json, "probe")),
                readCoverageRequest(requiredObject(json, "coverage_request")),
                readCoverageReport(requiredObject(json, "coverage_report")),
                readFraming(requiredObject(json, "framing_observation"))
        );
    }

    private static SFMSpatialSemanticContract.Domain readDomain(JsonObject json) {
        return new SFMSpatialSemanticContract.Domain(
                string(json, "schema"),
                string(json, "id"),
                SFMSpatialSemanticContract.DomainKind.fromWireName(string(json, "kind")),
                integer(json, "dimensions"),
                strings(json, "coordinate_kinds"),
                string(json, "authority"),
                string(json, "snapshot_identity")
        );
    }

    private static SFMSpatialSemanticContract.Region readRegion(JsonObject json) {
        List<SFMSpatialSemanticContract.AxisBound> bounds = objects(json, "bounds").stream()
                .map(value -> new SFMSpatialSemanticContract.AxisBound(
                        decimal(value, "start_inclusive"), decimal(value, "end_exclusive")))
                .toList();
        return new SFMSpatialSemanticContract.Region(
                string(json, "schema"),
                string(json, "id"),
                string(json, "domain_id"),
                SFMSpatialSemanticContract.Representation.fromWireName(string(json, "representation")),
                bounds,
                string(json, "edge_policy"),
                string(json, "semantic_kind"),
                string(json, "provenance"),
                strings(json, "projection_ids")
        );
    }

    private static SFMSpatialSemanticContract.Projection readProjection(JsonObject json) {
        return new SFMSpatialSemanticContract.Projection(
                string(json, "schema"),
                string(json, "id"),
                string(json, "from_domain_id"),
                string(json, "to_domain_id"),
                SFMSpatialSemanticContract.ProjectionLoss.fromWireName(string(json, "loss")),
                SFMSpatialSemanticContract.Completeness.fromWireName(string(json, "completeness")),
                string(json, "transform"),
                string(json, "fingerprint"),
                string(json, "authority")
        );
    }

    private static SFMSpatialSemanticContract.ActionDraft readAction(JsonObject json) {
        return new SFMSpatialSemanticContract.ActionDraft(
                string(json, "action_id"), strings(json, "arguments"));
    }

    private static SFMSpatialSemanticContract.Outlink readOutlink(JsonObject json) {
        return new SFMSpatialSemanticContract.Outlink(
                string(json, "schema"),
                string(json, "id"),
                string(json, "source_region_id"),
                nullableString(json, "destination_region_id"),
                nullableString(json, "destination_query"),
                string(json, "relation_kind"),
                SFMSpatialSemanticContract.Intent.fromWireName(string(json, "intent")),
                string(json, "provider_id"),
                nonNegativeLong(json, "provider_generation"),
                string(json, "reason"),
                SFMSpatialSemanticContract.Confidence.fromWireName(string(json, "confidence")),
                SFMSpatialSemanticContract.Completeness.fromWireName(string(json, "completeness")),
                string(json, "recommended_projection"),
                objects(json, "action_drafts").stream().map(SFMSpatialSemanticJsonCodec::readAction).toList(),
                string(json, "provenance")
        );
    }

    private static SFMSpatialSemanticContract.Probe readProbe(JsonObject json) {
        JsonObject classification = requiredObject(json, "classification");
        return new SFMSpatialSemanticContract.Probe(
                string(json, "schema"),
                string(json, "query_domain_id"),
                decimals(json, "query_point"),
                SFMSpatialSemanticContract.Intent.fromWireName(string(json, "requested_intent")),
                readRegion(requiredObject(json, "certified_region")),
                new SFMSpatialSemanticContract.Classification(
                        SFMSpatialSemanticContract.ClassificationStatus.fromWireName(
                                string(classification, "status")),
                        nullableString(classification, "reason_code")),
                objects(json, "outlinks").stream().map(SFMSpatialSemanticJsonCodec::readOutlink).toList(),
                objects(json, "action_drafts").stream().map(SFMSpatialSemanticJsonCodec::readAction).toList(),
                objects(json, "provider_evidence").stream().map(value ->
                        new SFMSpatialSemanticContract.ProviderEvidence(
                                string(value, "provider_id"), integer(value, "priority"),
                                string(value, "outcome"), nullableString(value, "diagnostic"))).toList(),
                nonNegativeLong(json, "workspace_generation"),
                nonNegativeLong(json, "document_generation"),
                nonNegativeLong(json, "semantic_generation"),
                nonNegativeLong(json, "layout_generation")
        );
    }

    private static SFMSpatialSemanticContract.CoverageRequest readCoverageRequest(JsonObject json) {
        JsonObject snapshot = requiredObject(json, "snapshot");
        return new SFMSpatialSemanticContract.CoverageRequest(
                string(json, "schema"),
                string(json, "request_id"),
                SFMSpatialSemanticContract.Scope.fromWireName(string(json, "scope")),
                string(json, "selector"),
                string(json, "profile"),
                string(json, "layout_matrix"),
                nonNegativeLong(json, "seed"),
                positiveLong(json, "budget"),
                string(json, "artifact_destination"),
                new SFMSpatialSemanticContract.SnapshotIdentity(
                        string(snapshot, "workspace_fingerprint"),
                        nonNegativeLong(snapshot, "workspace_generation"),
                        string(snapshot, "document_address"),
                        string(snapshot, "document_hash"),
                        nonNegativeLong(snapshot, "document_generation"),
                        string(snapshot, "semantic_fingerprint"),
                        nonNegativeLong(snapshot, "semantic_generation"),
                        string(snapshot, "layout_fingerprint"),
                        nonNegativeLong(snapshot, "layout_generation"))
        );
    }

    private static SFMSpatialSemanticContract.CoverageReport readCoverageReport(JsonObject json) {
        return new SFMSpatialSemanticContract.CoverageReport(
                string(json, "schema"),
                string(json, "request_id"),
                string(json, "policy"),
                string(json, "policy_version"),
                nonNegativeLong(json, "seed"),
                positiveLong(json, "budget"),
                longs(json, "sample_sequence"),
                objects(json, "dimensions").stream().map(value ->
                        new SFMSpatialSemanticContract.CoverageDimension(
                                string(value, "id"), nonNegativeLong(value, "covered"),
                                nonNegativeLong(value, "total"))).toList(),
                objects(json, "files").stream().map(value -> new SFMSpatialSemanticContract.FileRow(
                        string(value, "address"), string(value, "source_set"), string(value, "content_hash"),
                        SFMSpatialSemanticContract.FileState.fromWireName(string(value, "state")),
                        nullableString(value, "diagnostic"))).toList(),
                nonNegativeLong(json, "semantic_query_count"),
                nonNegativeLong(json, "certified_region_reuse"),
                nonNegativeLong(json, "cache_hits"),
                nonNegativeLong(json, "subdivisions"),
                nonNegativeLong(json, "fallback_probes"),
                strings(json, "artifact_paths")
        );
    }

    private static SFMSpatialSemanticContract.FramingObservation readFraming(JsonObject json) {
        return new SFMSpatialSemanticContract.FramingObservation(
                string(json, "schema"), string(json, "pane_id"), string(json, "document_address"),
                string(json, "document_hash"), string(json, "destination_region_id"),
                string(json, "landmark_projection"), rectangle(json, "document_bounds"),
                rectangle(json, "line_bounds"), rectangle(json, "destination_bounds"),
                rectangle(json, "viewport_bounds"), decimal(json, "inset"),
                camera(json, "previous_camera"), camera(json, "chosen_camera"),
                rectangle(json, "visible_intersection"), bool(json, "document_left_visible"),
                bool(json, "line_left_visible"), bool(json, "document_top_visible"),
                bool(json, "landmark_visible"), nullableString(json, "clipping_reason")
        );
    }

    private static SFMSpatialSemanticContract.Rectangle rectangle(JsonObject parent, String key) {
        JsonObject json = requiredObject(parent, key);
        return new SFMSpatialSemanticContract.Rectangle(
                decimal(json, "left"), decimal(json, "top"), decimal(json, "right"), decimal(json, "bottom"));
    }

    private static SFMSpatialSemanticContract.Camera camera(JsonObject parent, String key) {
        JsonObject json = requiredObject(parent, key);
        return new SFMSpatialSemanticContract.Camera(
                decimal(json, "x"), decimal(json, "y"), decimal(json, "zoom"));
    }

    private static SFMSpatialSemanticContract.Bundle validate(SFMSpatialSemanticContract.Bundle value) {
        var domain = value.domain();
        domain = new SFMSpatialSemanticContract.Domain(
                domain.schema(), domain.id(), domain.kind(), domain.dimensions(), domain.coordinateKinds(),
                domain.authority(), domain.snapshotIdentity());
        var region = validateRegion(value.region());
        var projection = value.projection();
        projection = new SFMSpatialSemanticContract.Projection(
                projection.schema(), projection.id(), projection.fromDomainId(), projection.toDomainId(),
                projection.loss(), projection.completeness(), projection.transform(), projection.fingerprint(),
                projection.authority());
        var outlink = validateOutlink(value.outlink());
        var probe = value.probe();
        probe = new SFMSpatialSemanticContract.Probe(
                probe.schema(), probe.queryDomainId(), probe.queryPoint(), probe.requestedIntent(),
                validateRegion(probe.certifiedRegion()),
                new SFMSpatialSemanticContract.Classification(
                        probe.classification().status(), probe.classification().reasonCode()),
                probe.outlinks().stream().map(SFMSpatialSemanticJsonCodec::validateOutlink).toList(),
                probe.actionDrafts().stream().map(SFMSpatialSemanticJsonCodec::validateAction).toList(),
                probe.providerEvidence().stream().map(evidence -> new SFMSpatialSemanticContract.ProviderEvidence(
                        evidence.providerId(), evidence.priority(), evidence.outcome(), evidence.diagnostic())).toList(),
                probe.workspaceGeneration(), probe.documentGeneration(), probe.semanticGeneration(),
                probe.layoutGeneration());
        var request = value.coverageRequest();
        var snapshot = request.snapshot();
        request = new SFMSpatialSemanticContract.CoverageRequest(
                request.schema(), request.requestId(), request.scope(), request.selector(), request.profile(),
                request.layoutMatrix(), request.seed(), request.budget(), request.artifactDestination(),
                new SFMSpatialSemanticContract.SnapshotIdentity(
                        snapshot.workspaceFingerprint(), snapshot.workspaceGeneration(), snapshot.documentAddress(),
                        snapshot.documentHash(), snapshot.documentGeneration(), snapshot.semanticFingerprint(),
                        snapshot.semanticGeneration(), snapshot.layoutFingerprint(), snapshot.layoutGeneration()));
        var report = value.coverageReport();
        report = new SFMSpatialSemanticContract.CoverageReport(
                report.schema(), report.requestId(), report.policy(), report.policyVersion(), report.seed(),
                report.budget(), report.sampleSequence(),
                report.dimensions().stream().map(dimension -> new SFMSpatialSemanticContract.CoverageDimension(
                        dimension.id(), dimension.covered(), dimension.total())).toList(),
                report.files().stream().map(file -> new SFMSpatialSemanticContract.FileRow(
                        file.address(), file.sourceSet(), file.contentHash(), file.state(), file.diagnostic())).toList(),
                report.semanticQueryCount(), report.certifiedRegionReuse(), report.cacheHits(), report.subdivisions(),
                report.fallbackProbes(), report.artifactPaths());
        var frame = value.framingObservation();
        frame = new SFMSpatialSemanticContract.FramingObservation(
                frame.schema(), frame.paneId(), frame.documentAddress(), frame.documentHash(),
                frame.destinationRegionId(), frame.landmarkProjection(), rectangle(frame.documentBounds()),
                rectangle(frame.lineBounds()), rectangle(frame.destinationBounds()), rectangle(frame.viewportBounds()),
                frame.inset(), camera(frame.previousCamera()), camera(frame.chosenCamera()),
                rectangle(frame.visibleIntersection()), frame.documentLeftVisible(), frame.lineLeftVisible(),
                frame.documentTopVisible(), frame.landmarkVisible(), frame.clippingReason());
        return new SFMSpatialSemanticContract.Bundle(domain, region, projection, outlink, probe, request, report, frame);
    }

    private static SFMSpatialSemanticContract.Region validateRegion(SFMSpatialSemanticContract.Region value) {
        return new SFMSpatialSemanticContract.Region(
                value.schema(), value.id(), value.domainId(), value.representation(),
                value.bounds().stream().map(bound -> new SFMSpatialSemanticContract.AxisBound(
                        bound.startInclusive(), bound.endExclusive())).toList(),
                value.edgePolicy(), value.semanticKind(), value.provenance(), value.projectionIds());
    }

    private static SFMSpatialSemanticContract.ActionDraft validateAction(
            SFMSpatialSemanticContract.ActionDraft value
    ) {
        return new SFMSpatialSemanticContract.ActionDraft(value.actionId(), value.arguments());
    }

    private static SFMSpatialSemanticContract.Outlink validateOutlink(SFMSpatialSemanticContract.Outlink value) {
        return new SFMSpatialSemanticContract.Outlink(
                value.schema(), value.id(), value.sourceRegionId(), value.destinationRegionId(),
                value.destinationQuery(), value.relationKind(), value.intent(), value.providerId(),
                value.providerGeneration(), value.reason(), value.confidence(), value.completeness(),
                value.recommendedProjection(), value.actionDrafts().stream()
                        .map(SFMSpatialSemanticJsonCodec::validateAction).toList(), value.provenance());
    }

    private static SFMSpatialSemanticContract.Rectangle rectangle(SFMSpatialSemanticContract.Rectangle value) {
        return new SFMSpatialSemanticContract.Rectangle(value.left(), value.top(), value.right(), value.bottom());
    }

    private static SFMSpatialSemanticContract.Camera camera(SFMSpatialSemanticContract.Camera value) {
        return new SFMSpatialSemanticContract.Camera(value.x(), value.y(), value.zoom());
    }

    private static JsonObject object(JsonElement value, String label) {
        if (value == null || value.isJsonNull() || !value.isJsonObject()) {
            throw new IllegalArgumentException(label + " must be an object");
        }
        return value.getAsJsonObject();
    }

    private static JsonObject requiredObject(JsonObject parent, String key) {
        return object(parent.get(key), key);
    }

    private static String string(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(key + " must be a string");
        }
        return value.getAsString();
    }

    private static String nullableString(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        if (value == null || value.isJsonNull()) return null;
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(key + " must be a string or null");
        }
        return value.getAsString();
    }

    private static boolean bool(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(key + " must be a boolean");
        }
        return value.getAsBoolean();
    }

    private static int integer(JsonObject parent, String key) {
        long value = exactLong(parent, key);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(key + " exceeds integer range");
        }
        return (int) value;
    }

    private static long nonNegativeLong(JsonObject parent, String key) {
        long value = exactLong(parent, key);
        if (value < 0) throw new IllegalArgumentException(key + " must be non-negative");
        return value;
    }

    private static long positiveLong(JsonObject parent, String key) {
        long value = exactLong(parent, key);
        if (value <= 0) throw new IllegalArgumentException(key + " must be positive");
        return value;
    }

    private static long exactLong(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(key + " must be an integer");
        }
        String text = value.getAsString();
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key + " must be an exact 64-bit integer", exception);
        }
    }

    private static double decimal(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(key + " must be a number");
        }
        double result = value.getAsDouble();
        if (!Double.isFinite(result)) throw new IllegalArgumentException(key + " must be finite");
        return result;
    }

    private static List<JsonObject> objects(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        if (value == null || !value.isJsonArray()) throw new IllegalArgumentException(key + " must be an array");
        ArrayList<JsonObject> result = new ArrayList<>();
        for (JsonElement element : value.getAsJsonArray()) result.add(object(element, key + " entry"));
        return List.copyOf(result);
    }

    private static List<String> strings(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        if (value == null || !value.isJsonArray()) throw new IllegalArgumentException(key + " must be an array");
        ArrayList<String> result = new ArrayList<>();
        for (JsonElement element : value.getAsJsonArray()) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException(key + " entries must be strings");
            }
            result.add(element.getAsString());
        }
        return List.copyOf(result);
    }

    private static List<Double> decimals(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        if (value == null || !value.isJsonArray()) throw new IllegalArgumentException(key + " must be an array");
        ArrayList<Double> result = new ArrayList<>();
        for (JsonElement element : value.getAsJsonArray()) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException(key + " entries must be numbers");
            }
            double number = element.getAsDouble();
            if (!Double.isFinite(number)) throw new IllegalArgumentException(key + " entries must be finite");
            result.add(number);
        }
        return List.copyOf(result);
    }

    private static List<Long> longs(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        if (value == null || !value.isJsonArray()) throw new IllegalArgumentException(key + " must be an array");
        ArrayList<Long> result = new ArrayList<>();
        for (JsonElement element : value.getAsJsonArray()) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException(key + " entries must be integers");
            }
            try {
                result.add(Long.parseLong(element.getAsString()));
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(key + " entries must be exact 64-bit integers", exception);
            }
        }
        return List.copyOf(result);
    }

    private static GsonBuilder builder() {
        GsonBuilder builder = new GsonBuilder()
                .disableHtmlEscaping()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES);
        wireEnum(builder, SFMSpatialSemanticContract.DomainKind.class,
                SFMSpatialSemanticContract.DomainKind::wireName,
                SFMSpatialSemanticContract.DomainKind::fromWireName);
        wireEnum(builder, SFMSpatialSemanticContract.Representation.class,
                SFMSpatialSemanticContract.Representation::wireName,
                SFMSpatialSemanticContract.Representation::fromWireName);
        wireEnum(builder, SFMSpatialSemanticContract.ProjectionLoss.class,
                SFMSpatialSemanticContract.ProjectionLoss::wireName,
                SFMSpatialSemanticContract.ProjectionLoss::fromWireName);
        wireEnum(builder, SFMSpatialSemanticContract.Completeness.class,
                SFMSpatialSemanticContract.Completeness::wireName,
                SFMSpatialSemanticContract.Completeness::fromWireName);
        wireEnum(builder, SFMSpatialSemanticContract.Intent.class,
                SFMSpatialSemanticContract.Intent::wireName,
                SFMSpatialSemanticContract.Intent::fromWireName);
        wireEnum(builder, SFMSpatialSemanticContract.ClassificationStatus.class,
                SFMSpatialSemanticContract.ClassificationStatus::wireName,
                SFMSpatialSemanticContract.ClassificationStatus::fromWireName);
        wireEnum(builder, SFMSpatialSemanticContract.Confidence.class,
                SFMSpatialSemanticContract.Confidence::wireName,
                SFMSpatialSemanticContract.Confidence::fromWireName);
        wireEnum(builder, SFMSpatialSemanticContract.Scope.class,
                SFMSpatialSemanticContract.Scope::wireName,
                SFMSpatialSemanticContract.Scope::fromWireName);
        wireEnum(builder, SFMSpatialSemanticContract.FileState.class,
                SFMSpatialSemanticContract.FileState::wireName,
                SFMSpatialSemanticContract.FileState::fromWireName);
        return builder;
    }

    private static <T> void wireEnum(
            GsonBuilder builder,
            Class<T> type,
            Function<T, String> encoder,
            Function<String, T> decoder
    ) {
        builder.registerTypeAdapter(type, new WireEnumAdapter<>(encoder, decoder));
    }

    private static final class WireEnumAdapter<T> implements JsonSerializer<T>, JsonDeserializer<T> {
        private final Function<T, String> encoder;
        private final Function<String, T> decoder;

        private WireEnumAdapter(Function<T, String> encoder, Function<String, T> decoder) {
            this.encoder = encoder;
            this.decoder = decoder;
        }

        @Override
        public JsonElement serialize(T source, Type type, JsonSerializationContext context) {
            return new JsonPrimitive(encoder.apply(source));
        }

        @Override
        public T deserialize(JsonElement json, Type type, JsonDeserializationContext context) {
            if (json == null || !json.isJsonPrimitive() || !json.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("Wire enum must be a string");
            }
            return decoder.apply(json.getAsString());
        }
    }
}
