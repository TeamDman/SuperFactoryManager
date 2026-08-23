package ca.teamdman.sfm.client.review.release_review;

import ca.teamdman.sfm.client.review.session.SFMReviewSessionV2;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV2Codec;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Strict deterministic JSON codec for {@code sfm.release-review/1}. */
public final class SFMReleaseReviewV1Codec {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private SFMReleaseReviewV1Codec() {
    }

    public static SFMReleaseReviewV1 parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        fields(root, "release review", Set.of(
                "schema", "review_session", "repository_bindings", "corpus_documents", "review_units",
                "selector_bindings", "migration_reports", "named_queries", "resume_state",
                "producer_generations", "completion_attestations"
        ));
        equal(text(root, "schema"), SFMReleaseReviewV1.SCHEMA, "schema");
        SFMReviewSessionV2 session = SFMReviewSessionV2Codec.parse(GSON.toJson(object(root, "review_session")));
        List<SFMReleaseReviewV1.RepositoryBinding> bindings = new ArrayList<>();
        for (JsonElement value : array(root, "repository_bindings")) bindings.add(parseBinding(value.getAsJsonObject()));
        List<SFMReleaseReviewV1.CorpusDocument> documents = new ArrayList<>();
        for (JsonElement value : array(root, "corpus_documents")) documents.add(parseDocument(value.getAsJsonObject()));
        List<SFMReleaseReviewV1.ReviewUnit> units = new ArrayList<>();
        for (JsonElement value : array(root, "review_units")) units.add(parseUnit(value.getAsJsonObject()));
        List<SFMReleaseReviewV1.CommentSelectorBinding> selectorBindings = new ArrayList<>();
        for (JsonElement value : array(root, "selector_bindings")) {
            selectorBindings.add(parseSelectorBinding(value.getAsJsonObject()));
        }
        List<SFMReleaseReviewV1.MigrationReport> migrationReports = new ArrayList<>();
        for (JsonElement value : array(root, "migration_reports")) {
            migrationReports.add(parseMigrationReport(value.getAsJsonObject()));
        }
        List<SFMReleaseReviewV1.NamedQuery> queries = new ArrayList<>();
        for (JsonElement value : array(root, "named_queries")) queries.add(parseQuery(value.getAsJsonObject()));
        List<SFMReleaseReviewV1.ProducerGeneration> producers = new ArrayList<>();
        for (JsonElement value : array(root, "producer_generations")) producers.add(parseProducer(value.getAsJsonObject()));
        List<SFMReleaseReviewV1.CompletionAttestation> attestations = new ArrayList<>();
        for (JsonElement value : array(root, "completion_attestations")) {
            attestations.add(parseAttestation(value.getAsJsonObject()));
        }
        return new SFMReleaseReviewV1(
                text(root, "schema"), session, bindings, documents, units, selectorBindings, migrationReports, queries,
                parseResume(object(root, "resume_state")), producers, attestations
        );
    }

    public static String write(SFMReleaseReviewV1 document) {
        JsonObject root = new JsonObject();
        root.addProperty("schema", document.schema());
        root.add("review_session", JsonParser.parseString(SFMReviewSessionV2Codec.write(document.reviewSession())));
        JsonArray bindings = new JsonArray();
        document.repositoryBindings().forEach(value -> bindings.add(writeBinding(value)));
        root.add("repository_bindings", bindings);
        JsonArray documents = new JsonArray();
        document.corpusDocuments().forEach(value -> documents.add(writeDocument(value)));
        root.add("corpus_documents", documents);
        JsonArray units = new JsonArray();
        document.reviewUnits().forEach(value -> units.add(writeUnit(value)));
        root.add("review_units", units);
        JsonArray selectorBindings = new JsonArray();
        document.selectorBindings().forEach(value -> selectorBindings.add(writeSelectorBinding(value)));
        root.add("selector_bindings", selectorBindings);
        JsonArray migrationReports = new JsonArray();
        document.migrationReports().forEach(value -> migrationReports.add(writeMigrationReport(value)));
        root.add("migration_reports", migrationReports);
        JsonArray queries = new JsonArray();
        document.namedQueries().forEach(value -> queries.add(writeQuery(value)));
        root.add("named_queries", queries);
        root.add("resume_state", writeResume(document.resumeState()));
        JsonArray producers = new JsonArray();
        document.producerGenerations().forEach(value -> producers.add(writeProducer(value)));
        root.add("producer_generations", producers);
        JsonArray attestations = new JsonArray();
        document.completionAttestations().forEach(value -> attestations.add(writeAttestation(value)));
        root.add("completion_attestations", attestations);
        return GSON.toJson(root) + "\n";
    }

    private static SFMReleaseReviewV1.RepositoryBinding parseBinding(JsonObject value) {
        fields(value, "repository binding", Set.of(
                "lane_id", "repository_id", "root_hint", "before_label", "before_commit", "before_tree",
                "after_label", "candidate_commit", "candidate_tree", "review_evidence_paths"
        ));
        return new SFMReleaseReviewV1.RepositoryBinding(
                text(value, "lane_id"), text(value, "repository_id"), text(value, "root_hint"),
                text(value, "before_label"), text(value, "before_commit"), text(value, "before_tree"),
                text(value, "after_label"), text(value, "candidate_commit"), text(value, "candidate_tree"),
                strings(value, "review_evidence_paths")
        );
    }

    private static JsonObject writeBinding(SFMReleaseReviewV1.RepositoryBinding value) {
        JsonObject answer = new JsonObject();
        answer.addProperty("lane_id", value.laneId());
        answer.addProperty("repository_id", value.repositoryId());
        answer.addProperty("root_hint", value.rootHint());
        answer.addProperty("before_label", value.beforeLabel());
        answer.addProperty("before_commit", value.beforeCommit());
        answer.addProperty("before_tree", value.beforeTree());
        answer.addProperty("after_label", value.afterLabel());
        answer.addProperty("candidate_commit", value.candidateCommit());
        answer.addProperty("candidate_tree", value.candidateTree());
        answer.add("review_evidence_paths", strings(value.reviewEvidencePaths()));
        return answer;
    }

    private static SFMReleaseReviewV1.CorpusDocument parseDocument(JsonObject value) {
        fields(value, "corpus document", Set.of(
                "id", "lane_id", "snapshot_side", "path", "document_revision_id", "sha256",
                "source_owner", "source_locator", "materialization"
        ));
        return new SFMReleaseReviewV1.CorpusDocument(
                text(value, "id"), text(value, "lane_id"), enumValue(SFMReleaseReviewV1.SnapshotSide.class,
                text(value, "snapshot_side")), text(value, "path"), text(value, "document_revision_id"),
                text(value, "sha256"), text(value, "source_owner"), text(value, "source_locator"),
                enumValue(SFMReleaseReviewV1.Materialization.class, text(value, "materialization"))
        );
    }

    private static JsonObject writeDocument(SFMReleaseReviewV1.CorpusDocument value) {
        JsonObject answer = new JsonObject();
        answer.addProperty("id", value.id());
        answer.addProperty("lane_id", value.laneId());
        answer.addProperty("snapshot_side", lower(value.snapshotSide()));
        answer.addProperty("path", value.path());
        answer.addProperty("document_revision_id", value.documentRevisionId());
        answer.addProperty("sha256", value.sha256());
        answer.addProperty("source_owner", value.sourceOwner());
        answer.addProperty("source_locator", value.sourceLocator());
        answer.addProperty("materialization", lower(value.materialization()));
        return answer;
    }

    private static SFMReleaseReviewV1.ReviewUnit parseUnit(JsonObject value) {
        fields(value, "review unit", Set.of(
                "id", "lane_id", "operation", "path_before", "path_after", "before_document_revision_id",
                "after_document_revision_id", "before_ranges", "after_ranges", "language", "surface_kind",
                "semantic_key", "limitation", "producer_id", "producer_generation"
        ));
        return new SFMReleaseReviewV1.ReviewUnit(
                text(value, "id"), text(value, "lane_id"),
                enumValue(SFMReleaseReviewV1.ChangeOperation.class, text(value, "operation")),
                optionalText(value, "path_before"), optionalText(value, "path_after"),
                optionalText(value, "before_document_revision_id"), optionalText(value, "after_document_revision_id"),
                ranges(value, "before_ranges"), ranges(value, "after_ranges"), text(value, "language"),
                enumValue(SFMReleaseReviewV1.SurfaceKind.class, text(value, "surface_kind")),
                optionalText(value, "semantic_key"), optionalText(value, "limitation"),
                text(value, "producer_id"), text(value, "producer_generation")
        );
    }

    private static JsonObject writeUnit(SFMReleaseReviewV1.ReviewUnit value) {
        JsonObject answer = new JsonObject();
        answer.addProperty("id", value.id());
        answer.addProperty("lane_id", value.laneId());
        answer.addProperty("operation", lower(value.operation()));
        optional(answer, "path_before", value.pathBefore());
        optional(answer, "path_after", value.pathAfter());
        optional(answer, "before_document_revision_id", value.beforeDocumentRevisionId());
        optional(answer, "after_document_revision_id", value.afterDocumentRevisionId());
        answer.add("before_ranges", ranges(value.beforeRanges()));
        answer.add("after_ranges", ranges(value.afterRanges()));
        answer.addProperty("language", value.language());
        answer.addProperty("surface_kind", lower(value.surfaceKind()));
        optional(answer, "semantic_key", value.semanticKey());
        optional(answer, "limitation", value.limitation());
        answer.addProperty("producer_id", value.producerId());
        answer.addProperty("producer_generation", value.producerGeneration());
        return answer;
    }

    private static SFMReleaseReviewV1.CommentSelectorBinding parseSelectorBinding(JsonObject value) {
        fields(value, "comment selector binding", Set.of("comment_id", "captured_selection", "selected_proposal"));
        return new SFMReleaseReviewV1.CommentSelectorBinding(
                text(value, "comment_id"), parsePinnedSelection(object(value, "captured_selection")),
                parseSelectorProposal(object(value, "selected_proposal"))
        );
    }

    private static JsonObject writeSelectorBinding(SFMReleaseReviewV1.CommentSelectorBinding value) {
        JsonObject answer = new JsonObject();
        answer.addProperty("comment_id", value.commentId());
        answer.add("captured_selection", writePinnedSelection(value.capturedSelection()));
        answer.add("selected_proposal", writeSelectorProposal(value.selectedProposal()));
        return answer;
    }

    private static SFMReleaseReviewV1.PinnedSelection parsePinnedSelection(JsonObject value) {
        fields(value, "pinned selection", Set.of(
                "selection_revision", "source_expression", "primary_range_index", "ranges"
        ));
        List<SFMReleaseReviewV1.PinnedSelectionRange> ranges = new ArrayList<>();
        for (JsonElement element : array(value, "ranges")) ranges.add(parsePinnedRange(element.getAsJsonObject()));
        return new SFMReleaseReviewV1.PinnedSelection(
                text(value, "selection_revision"), text(value, "source_expression"),
                integer(value, "primary_range_index"), ranges
        );
    }

    private static JsonObject writePinnedSelection(SFMReleaseReviewV1.PinnedSelection value) {
        JsonObject answer = new JsonObject();
        answer.addProperty("selection_revision", value.selectionRevision());
        answer.addProperty("source_expression", value.sourceExpression());
        answer.addProperty("primary_range_index", value.primaryRangeIndex());
        JsonArray ranges = new JsonArray();
        value.ranges().forEach(range -> ranges.add(writePinnedRange(range)));
        answer.add("ranges", ranges);
        return answer;
    }

    private static SFMReleaseReviewV1.PinnedSelectionRange parsePinnedRange(JsonObject value) {
        fields(value, "pinned selection range", Set.of(
                "direction", "document_revision_id", "document_sha256", "start_byte", "end_byte"
        ));
        return new SFMReleaseReviewV1.PinnedSelectionRange(
                enumValue(SFMReleaseReviewV1.SelectionDirection.class, text(value, "direction")),
                text(value, "document_revision_id"), text(value, "document_sha256"),
                integer(value, "start_byte"), integer(value, "end_byte")
        );
    }

    private static JsonObject writePinnedRange(SFMReleaseReviewV1.PinnedSelectionRange value) {
        JsonObject answer = new JsonObject();
        answer.addProperty("direction", lower(value.direction()));
        answer.addProperty("document_revision_id", value.documentRevisionId());
        answer.addProperty("document_sha256", value.documentSha256());
        answer.addProperty("start_byte", value.startByte());
        answer.addProperty("end_byte", value.endByte());
        return answer;
    }

    private static SFMReleaseReviewV1.SelectorProposal parseSelectorProposal(JsonObject value) {
        fields(value, "selector proposal", Set.of(
                "id", "kind", "selection_rule", "literal_witness", "semantic_provider", "semantic_key",
                "semantic_provenance", "confidence", "projection_fingerprint", "source_snapshot_id", "diagnostics"
        ));
        List<SFMReleaseReviewV1.Evidence> evidence = new ArrayList<>();
        for (JsonElement element : array(value, "semantic_provenance")) {
            JsonObject entry = element.getAsJsonObject();
            fields(entry, "selector evidence", Set.of("key", "value"));
            evidence.add(new SFMReleaseReviewV1.Evidence(text(entry, "key"), text(entry, "value")));
        }
        return new SFMReleaseReviewV1.SelectorProposal(
                text(value, "id"), enumValue(SFMReleaseReviewV1.SelectorKind.class, text(value, "kind")),
                parseRule(object(value, "selection_rule")), parsePinnedSelection(object(value, "literal_witness")),
                optionalText(value, "semantic_provider"), optionalText(value, "semantic_key"), evidence,
                enumValue(SFMReleaseReviewV1.ProposalConfidence.class, text(value, "confidence")),
                text(value, "projection_fingerprint"), text(value, "source_snapshot_id"), strings(value, "diagnostics")
        );
    }

    private static JsonObject writeSelectorProposal(SFMReleaseReviewV1.SelectorProposal value) {
        JsonObject answer = new JsonObject();
        answer.addProperty("id", value.id());
        answer.addProperty("kind", lower(value.kind()));
        answer.add("selection_rule", writeRule(value.selectionRule()));
        answer.add("literal_witness", writePinnedSelection(value.literalWitness()));
        optional(answer, "semantic_provider", value.semanticProvider());
        optional(answer, "semantic_key", value.semanticKey());
        JsonArray evidence = new JsonArray();
        value.semanticProvenance().forEach(item -> {
            JsonObject entry = new JsonObject();
            entry.addProperty("key", item.key());
            entry.addProperty("value", item.value());
            evidence.add(entry);
        });
        answer.add("semantic_provenance", evidence);
        answer.addProperty("confidence", lower(value.confidence()));
        answer.addProperty("projection_fingerprint", value.projectionFingerprint());
        answer.addProperty("source_snapshot_id", value.sourceSnapshotId());
        answer.add("diagnostics", strings(value.diagnostics()));
        return answer;
    }

    private static SFMReleaseReviewV1.MigrationReport parseMigrationReport(JsonObject value) {
        fields(value, "migration report", Set.of(
                "id", "source_selector_id", "source_evaluation", "candidate_evaluation", "old_witnesses",
                "new_candidates", "decision", "decision_comment_id"
        ));
        List<SFMReleaseReviewV1.PinnedSelectionRange> oldWitnesses = new ArrayList<>();
        for (JsonElement element : array(value, "old_witnesses")) {
            oldWitnesses.add(parsePinnedRange(element.getAsJsonObject()));
        }
        return new SFMReleaseReviewV1.MigrationReport(
                text(value, "id"), text(value, "source_selector_id"),
                parseEvaluation(object(value, "source_evaluation")),
                parseEvaluation(object(value, "candidate_evaluation")), oldWitnesses,
                addressedRanges(value, "new_candidates"),
                enumValue(SFMReleaseReviewV1.MigrationDecision.class, text(value, "decision")),
                optionalText(value, "decision_comment_id")
        );
    }

    private static JsonObject writeMigrationReport(SFMReleaseReviewV1.MigrationReport value) {
        JsonObject answer = new JsonObject();
        answer.addProperty("id", value.id());
        answer.addProperty("source_selector_id", value.sourceSelectorId());
        answer.add("source_evaluation", writeEvaluation(value.sourceEvaluation()));
        answer.add("candidate_evaluation", writeEvaluation(value.candidateEvaluation()));
        JsonArray witnesses = new JsonArray();
        value.oldWitnesses().forEach(item -> witnesses.add(writePinnedRange(item)));
        answer.add("old_witnesses", witnesses);
        answer.add("new_candidates", addressedRanges(value.newCandidates()));
        answer.addProperty("decision", lower(value.decision()));
        optional(answer, "decision_comment_id", value.decisionCommentId());
        return answer;
    }

    private static SFMReleaseReviewV1.EvaluationResult parseEvaluation(JsonObject value) {
        fields(value, "evaluation result", Set.of(
                "selector_id", "status", "ranges", "candidates", "invalidation_keys", "diagnostics"
        ));
        List<SFMReleaseReviewV1.InvalidationKey> keys = new ArrayList<>();
        for (JsonElement element : array(value, "invalidation_keys")) {
            JsonObject key = element.getAsJsonObject();
            fields(key, "invalidation key", Set.of("owner", "generation", "fingerprint"));
            keys.add(new SFMReleaseReviewV1.InvalidationKey(
                    text(key, "owner"), text(key, "generation"), text(key, "fingerprint")));
        }
        return new SFMReleaseReviewV1.EvaluationResult(
                text(value, "selector_id"),
                enumValue(SFMReleaseReviewV1.EvaluationStatus.class, text(value, "status")),
                addressedRanges(value, "ranges"), addressedRanges(value, "candidates"), keys,
                strings(value, "diagnostics")
        );
    }

    private static JsonObject writeEvaluation(SFMReleaseReviewV1.EvaluationResult value) {
        JsonObject answer = new JsonObject();
        answer.addProperty("selector_id", value.selectorId());
        answer.addProperty("status", lower(value.status()));
        answer.add("ranges", addressedRanges(value.ranges()));
        answer.add("candidates", addressedRanges(value.candidates()));
        JsonArray keys = new JsonArray();
        value.invalidationKeys().forEach(item -> {
            JsonObject key = new JsonObject();
            key.addProperty("owner", item.owner());
            key.addProperty("generation", item.generation());
            key.addProperty("fingerprint", item.fingerprint());
            keys.add(key);
        });
        answer.add("invalidation_keys", keys);
        answer.add("diagnostics", strings(value.diagnostics()));
        return answer;
    }

    private static List<SFMReleaseReviewV1.AddressedRange> addressedRanges(JsonObject owner, String key) {
        List<SFMReleaseReviewV1.AddressedRange> answer = new ArrayList<>();
        for (JsonElement element : array(owner, key)) {
            JsonObject value = element.getAsJsonObject();
            fields(value, "addressed range", Set.of("document_revision_id", "start_byte", "end_byte"));
            answer.add(new SFMReleaseReviewV1.AddressedRange(
                    text(value, "document_revision_id"), integer(value, "start_byte"), integer(value, "end_byte")));
        }
        return answer;
    }

    private static JsonArray addressedRanges(List<SFMReleaseReviewV1.AddressedRange> values) {
        JsonArray answer = new JsonArray();
        values.forEach(value -> {
            JsonObject range = new JsonObject();
            range.addProperty("document_revision_id", value.documentRevisionId());
            range.addProperty("start_byte", value.startByte());
            range.addProperty("end_byte", value.endByte());
            answer.add(range);
        });
        return answer;
    }

    private static SFMReviewSessionV1.SelectionRule parseRule(JsonObject value) {
        String kind = text(value, "kind");
        return switch (kind) {
            case "literal_utf8_range" -> {
                fields(value, "literal selection rule", Set.of(
                        "kind", "document_revision_id", "start_byte", "end_byte",
                        "document_sha256", "selected_text_sha256"));
                yield new SFMReviewSessionV1.LiteralUtf8Range(
                        text(value, "document_revision_id"), integer(value, "start_byte"), integer(value, "end_byte"),
                        text(value, "document_sha256"), text(value, "selected_text_sha256"));
            }
            case "union" -> {
                fields(value, "union selection rule", Set.of("kind", "rules"));
                yield new SFMReviewSessionV1.Union(rules(value, "rules"));
            }
            case "intersection" -> {
                fields(value, "intersection selection rule", Set.of("kind", "rules"));
                yield new SFMReviewSessionV1.Intersection(rules(value, "rules"));
            }
            case "difference" -> {
                fields(value, "difference selection rule", Set.of("kind", "include", "exclude"));
                yield new SFMReviewSessionV1.Difference(
                        parseRule(object(value, "include")), rules(value, "exclude"));
            }
            default -> throw new IllegalArgumentException("Unknown selection rule kind '" + kind + "'");
        };
    }

    private static List<SFMReviewSessionV1.SelectionRule> rules(JsonObject owner, String key) {
        List<SFMReviewSessionV1.SelectionRule> answer = new ArrayList<>();
        for (JsonElement element : array(owner, key)) answer.add(parseRule(element.getAsJsonObject()));
        return answer;
    }

    private static JsonObject writeRule(SFMReviewSessionV1.SelectionRule value) {
        JsonObject answer = new JsonObject();
        if (value instanceof SFMReviewSessionV1.LiteralUtf8Range literal) {
            answer.addProperty("kind", "literal_utf8_range");
            answer.addProperty("document_revision_id", literal.documentRevisionId());
            answer.addProperty("start_byte", literal.startByte());
            answer.addProperty("end_byte", literal.endByte());
            answer.addProperty("document_sha256", literal.documentSha256());
            answer.addProperty("selected_text_sha256", literal.selectedTextSha256());
        } else if (value instanceof SFMReviewSessionV1.Union union) {
            answer.addProperty("kind", "union");
            answer.add("rules", writeRules(union.rules()));
        } else if (value instanceof SFMReviewSessionV1.Intersection intersection) {
            answer.addProperty("kind", "intersection");
            answer.add("rules", writeRules(intersection.rules()));
        } else if (value instanceof SFMReviewSessionV1.Difference difference) {
            answer.addProperty("kind", "difference");
            answer.add("include", writeRule(difference.include()));
            answer.add("exclude", writeRules(difference.exclude()));
        } else {
            throw new IllegalArgumentException("Unsupported selection rule " + value.getClass().getName());
        }
        return answer;
    }

    private static JsonArray writeRules(List<SFMReviewSessionV1.SelectionRule> values) {
        JsonArray answer = new JsonArray();
        values.forEach(value -> answer.add(writeRule(value)));
        return answer;
    }

    private static SFMReleaseReviewV1.NamedQuery parseQuery(JsonObject value) {
        fields(value, "named query", Set.of("id", "expression"));
        return new SFMReleaseReviewV1.NamedQuery(text(value, "id"), text(value, "expression"));
    }

    private static JsonObject writeQuery(SFMReleaseReviewV1.NamedQuery value) {
        JsonObject answer = new JsonObject();
        answer.addProperty("id", value.id());
        answer.addProperty("expression", value.expression());
        return answer;
    }

    private static SFMReleaseReviewV1.ResumeState parseResume(JsonObject value) {
        fields(value, "resume state", Set.of(
                "active_query_id", "active_query_expression", "current_unit_id", "deferred_unit_ids", "generation"
        ));
        return new SFMReleaseReviewV1.ResumeState(
                optionalText(value, "active_query_id"), optionalText(value, "active_query_expression"),
                optionalText(value, "current_unit_id"), strings(value, "deferred_unit_ids"),
                longInteger(value, "generation")
        );
    }

    private static JsonObject writeResume(SFMReleaseReviewV1.ResumeState value) {
        JsonObject answer = new JsonObject();
        optional(answer, "active_query_id", value.activeQueryId());
        optional(answer, "active_query_expression", value.activeQueryExpression());
        optional(answer, "current_unit_id", value.currentUnitId());
        answer.add("deferred_unit_ids", strings(value.deferredUnitIds()));
        answer.addProperty("generation", value.generation());
        return answer;
    }

    private static SFMReleaseReviewV1.ProducerGeneration parseProducer(JsonObject value) {
        fields(value, "producer generation", Set.of(
                "producer_id", "generation", "input_fingerprint", "output_fingerprint"
        ));
        return new SFMReleaseReviewV1.ProducerGeneration(
                text(value, "producer_id"), text(value, "generation"), text(value, "input_fingerprint"),
                text(value, "output_fingerprint")
        );
    }

    private static JsonObject writeProducer(SFMReleaseReviewV1.ProducerGeneration value) {
        JsonObject answer = new JsonObject();
        answer.addProperty("producer_id", value.producerId());
        answer.addProperty("generation", value.generation());
        answer.addProperty("input_fingerprint", value.inputFingerprint());
        answer.addProperty("output_fingerprint", value.outputFingerprint());
        return answer;
    }

    private static SFMReleaseReviewV1.CompletionAttestation parseAttestation(JsonObject value) {
        fields(value, "completion attestation", Set.of(
                "id", "review_semantic_state_hash", "maintainer", "attested_at", "statement"
        ));
        return new SFMReleaseReviewV1.CompletionAttestation(
                text(value, "id"), text(value, "review_semantic_state_hash"), text(value, "maintainer"),
                text(value, "attested_at"), text(value, "statement")
        );
    }

    private static JsonObject writeAttestation(SFMReleaseReviewV1.CompletionAttestation value) {
        JsonObject answer = new JsonObject();
        answer.addProperty("id", value.id());
        answer.addProperty("review_semantic_state_hash", value.reviewSemanticStateHash());
        answer.addProperty("maintainer", value.maintainer());
        answer.addProperty("attested_at", value.attestedAt());
        answer.addProperty("statement", value.statement());
        return answer;
    }

    private static List<SFMReleaseReviewV1.Utf8Range> ranges(JsonObject owner, String key) {
        List<SFMReleaseReviewV1.Utf8Range> answer = new ArrayList<>();
        for (JsonElement element : array(owner, key)) {
            JsonObject value = element.getAsJsonObject();
            fields(value, "UTF-8 range", Set.of("start_byte", "end_byte"));
            answer.add(new SFMReleaseReviewV1.Utf8Range(integer(value, "start_byte"), integer(value, "end_byte")));
        }
        return answer;
    }

    private static JsonArray ranges(List<SFMReleaseReviewV1.Utf8Range> values) {
        JsonArray answer = new JsonArray();
        values.forEach(value -> {
            JsonObject range = new JsonObject();
            range.addProperty("start_byte", value.startByte());
            range.addProperty("end_byte", value.endByte());
            answer.add(range);
        });
        return answer;
    }

    private static JsonArray strings(List<String> values) {
        JsonArray answer = new JsonArray();
        values.forEach(answer::add);
        return answer;
    }

    private static List<String> strings(JsonObject owner, String key) {
        List<String> answer = new ArrayList<>();
        for (JsonElement value : array(owner, key)) answer.add(value.getAsString());
        return answer;
    }

    private static void optional(JsonObject owner, String key, Optional<String> value) {
        value.ifPresent(item -> owner.addProperty(key, item));
    }

    private static Optional<String> optionalText(JsonObject owner, String key) {
        if (!owner.has(key) || owner.get(key).isJsonNull()) return Optional.empty();
        return Optional.of(owner.get(key).getAsString());
    }

    private static JsonArray array(JsonObject owner, String key) {
        if (!owner.has(key) || !owner.get(key).isJsonArray()) {
            throw new IllegalArgumentException("Expected array field '" + key + "'");
        }
        return owner.getAsJsonArray(key);
    }

    private static JsonObject object(JsonObject owner, String key) {
        if (!owner.has(key) || !owner.get(key).isJsonObject()) {
            throw new IllegalArgumentException("Expected object field '" + key + "'");
        }
        return owner.getAsJsonObject(key);
    }

    private static String text(JsonObject owner, String key) {
        if (!owner.has(key) || owner.get(key).isJsonNull()) {
            throw new IllegalArgumentException("Expected text field '" + key + "'");
        }
        return owner.get(key).getAsString();
    }

    private static int integer(JsonObject owner, String key) {
        return owner.get(key).getAsInt();
    }

    private static long longInteger(JsonObject owner, String key) {
        return owner.get(key).getAsLong();
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value) {
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown " + type.getSimpleName() + " value '" + value + "'", exception);
        }
    }

    private static String lower(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    private static void equal(String actual, String expected, String label) {
        if (!expected.equals(actual)) throw new IllegalArgumentException("Unsupported " + label + " '" + actual + "'");
    }

    private static void fields(JsonObject value, String label, Set<String> allowed) {
        Set<String> unknown = new HashSet<>(value.keySet());
        unknown.removeAll(allowed);
        if (!unknown.isEmpty()) throw new IllegalArgumentException("Unknown " + label + " fields " + unknown);
    }
}
