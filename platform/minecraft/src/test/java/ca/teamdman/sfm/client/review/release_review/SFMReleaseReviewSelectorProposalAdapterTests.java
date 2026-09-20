package ca.teamdman.sfm.client.review.release_review;

import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1Kernel;
import ca.teamdman.sfm.client.symbol.SFMDefinitionRequest;
import ca.teamdman.sfm.client.symbol.SFMDefinitionResult;
import ca.teamdman.sfm.client.symbol.SFMJavaInteractionMap;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentRange;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSelection;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMReleaseReviewSelectorProposalAdapterTests {
    @Test
    void zeroSemanticEvidenceStillReturnsTheExactLiteralProposal() {
        Fixture fixture = fixture("class A {}\n", 6, 7);

        var batch = SFMReleaseReviewSelectorProposalAdapter.propose(
                fixture.adapted(), fixture.documents(), List.of()
        );

        assertEquals(List.of(SFMReleaseReviewV1.SelectorKind.LITERAL),
                batch.proposals().stream().map(SFMReleaseReviewV1.SelectorProposal::kind).toList());
        assertTrue(batch.diagnostics().isEmpty());
        assertEquals(fixture.adapted().pinnedSelection(), batch.proposals().get(0).literalWitness());
    }

    @Test
    void oneCertifiedInteractionRegionProducesOneStructuralProposal() {
        String text = "class A {}\n";
        Fixture fixture = fixture(text, 6, 7);
        SFMJavaInteractionMap.Result map = declarationMap(text);

        var batch = SFMReleaseReviewSelectorProposalAdapter.propose(
                fixture.adapted(),
                fixture.documents(),
                List.of(new SFMReleaseReviewSelectorProposalAdapter.JavaInteractionMapProvider(
                        Map.of("document-a", map)
                ))
        );

        assertEquals(List.of(
                        SFMReleaseReviewV1.SelectorKind.LITERAL,
                        SFMReleaseReviewV1.SelectorKind.DECLARATION
                ),
                batch.proposals().stream().map(SFMReleaseReviewV1.SelectorProposal::kind).toList());
        var declaration = batch.proposals().get(1);
        assertEquals(Optional.of(SFMReleaseReviewSelectorProposalAdapter.JAVA_INTERACTION_MAP_PROVIDER),
                declaration.semanticProvider());
        assertEquals(fixture.adapted().pinnedSelection(), declaration.literalWitness());
        var rule = assertInstanceOf(SFMReviewSessionV1.LiteralUtf8Range.class,
                declaration.selectionRule());
        assertEquals(0, rule.startByte());
        assertEquals(10, rule.endByte());
        assertTrue(batch.diagnostics().isEmpty());
    }

    @Test
    void lexicalInteractionRegionDoesNotPretendToBeStructuralEvidence() {
        String text = "class A {}\n";
        Fixture fixture = fixture(text, 6, 7);
        SFMJavaInteractionMap.Result map = lexicalOnlyMap(declarationMap(text));

        var batch = SFMReleaseReviewSelectorProposalAdapter.propose(
                fixture.adapted(),
                fixture.documents(),
                List.of(new SFMReleaseReviewSelectorProposalAdapter.JavaInteractionMapProvider(
                        Map.of("document-a", map)
                ))
        );

        assertEquals(List.of(SFMReleaseReviewV1.SelectorKind.LITERAL),
                batch.proposals().stream().map(SFMReleaseReviewV1.SelectorProposal::kind).toList());
        assertTrue(batch.diagnostics().isEmpty());
    }

    @Test
    void currentJavaPublicationOffersMethodTypeFieldAndImportDeclarations() {
        String text = "import java.util.List;\n"
                + "class Example {\n"
                + "    int field;\n"
                + "    String method(int value) { return field + value; }\n"
                + "}\n";
        SFMJavaInteractionMap.Result map = structuralPublication(text);

        ProposalCase importCase = proposeAt(text, map, text.indexOf("List"), "List".length());
        assertStructuralProposal(importCase, "region:import", SFMReleaseReviewV1.SelectorKind.DECLARATION,
                0, text.indexOf('\n'));

        ProposalCase typeCase = proposeAt(text, map, text.indexOf("Example"), "Example".length());
        assertStructuralProposal(typeCase, "region:type", SFMReleaseReviewV1.SelectorKind.DECLARATION,
                text.indexOf("class Example"), text.lastIndexOf('}') + 1);

        ProposalCase fieldCase = proposeAt(text, map, text.indexOf("field"), "field".length());
        int fieldStart = text.indexOf("int field");
        assertStructuralProposal(fieldCase, "region:field", SFMReleaseReviewV1.SelectorKind.DECLARATION,
                fieldStart, text.indexOf(';', fieldStart) + 1);

        ProposalCase methodCase = proposeAt(text, map, text.indexOf("method"), "method".length());
        int methodStart = text.indexOf("String method");
        int methodEnd = text.indexOf('}', methodStart) + 1;
        int methodBodyStart = text.indexOf('{', methodStart);
        assertStructuralProposal(methodCase, "region:method", SFMReleaseReviewV1.SelectorKind.DECLARATION,
                methodStart, methodEnd);
        assertStructuralProposal(methodCase, "region:method/signature", SFMReleaseReviewV1.SelectorKind.SIGNATURE,
                methodStart, methodBodyStart);
        assertStructuralProposal(methodCase, "region:method/body", SFMReleaseReviewV1.SelectorKind.BODY,
                methodBodyStart, methodEnd);
    }

    @Test
    void certifiedPunctuationBoundaryUsesExplicitContainmentAndNeverANearestTokenGuess() {
        String text = "import java.util.List;\n"
                + "class Example {\n"
                + "    int field;\n"
                + "    String method(int value) { return field + value; }\n"
                + "}\n";
        SFMJavaInteractionMap.Result map = structuralPublication(text);
        int brace = text.indexOf('{', text.indexOf("String method"));

        ProposalCase punctuation = proposeAt(text, map, brace, 1);
        assertStructuralProposal(punctuation, "region:method", SFMReleaseReviewV1.SelectorKind.DECLARATION,
                text.indexOf("String method"), text.indexOf('}', brace) + 1);
        assertStructuralProposal(punctuation, "region:method/body", SFMReleaseReviewV1.SelectorKind.BODY,
                brace, text.indexOf('}', brace) + 1);

        SFMJavaInteractionMap.Region punctuationOnly = region(
                "region:isolated-punctuation", "java-delimiter", brace, brace + 1);
        ProposalCase isolated = proposeAt(text, currentMap(
                text, List.of(punctuationOnly), List.of(), List.of()), brace, 1);
        assertEquals(List.of(SFMReleaseReviewV1.SelectorKind.LITERAL),
                isolated.batch().proposals().stream().map(SFMReleaseReviewV1.SelectorProposal::kind).toList(),
                "a punctuation glyph without certified structural containment must not borrow a nearby token");
    }

    @Test
    void overlappingCertifiedTypeAndFieldRegionsRemainSeparateProposals() {
        String text = "import java.util.List;\n"
                + "class Example {\n"
                + "    int field;\n"
                + "    String method(int value) { return field + value; }\n"
                + "}\n";
        SFMJavaInteractionMap.Result map = structuralPublication(text);
        ProposalCase fieldCase = proposeAt(text, map, text.indexOf("field"), "field".length());

        List<String> declarationKeys = fieldCase.batch().proposals().stream()
                .filter(value -> value.kind() == SFMReleaseReviewV1.SelectorKind.DECLARATION)
                .map(value -> value.semanticKey().orElseThrow())
                .toList();
        assertEquals(List.of("region:field", "region:type"), declarationKeys);
        assertFalse(proposal(fieldCase.batch(), SFMReleaseReviewV1.SelectorKind.DECLARATION, "region:field")
                .selectionRule().equals(proposal(
                        fieldCase.batch(), SFMReleaseReviewV1.SelectorKind.DECLARATION, "region:type")
                        .selectionRule()));
    }

    @Test
    void staleJavaPublicationIsDiagnosedAndCannotCreateStructuralEvidence() {
        String text = "class A {}\n";
        Fixture fixture = fixture(text, 6, 7);
        SFMJavaInteractionMap.Result current = declarationMap(text);
        SFMDefinitionResult.DocumentIdentity staleIdentity = new SFMDefinitionResult.DocumentIdentity(
                current.document().address(), current.document().rootId(), current.document().rootRelativePath(),
                current.document().reportPath(), current.document().sourceSet(),
                SFMDefinitionRequest.sha256("class B {}\n"), current.document().diskContentHash());
        SFMJavaInteractionMap.Result stale = copyMap(current, staleIdentity);

        var batch = SFMReleaseReviewSelectorProposalAdapter.propose(
                fixture.adapted(), fixture.documents(),
                List.of(new SFMReleaseReviewSelectorProposalAdapter.JavaInteractionMapProvider(
                        Map.of("document-a", stale)))
        );

        assertEquals(List.of(SFMReleaseReviewV1.SelectorKind.LITERAL),
                batch.proposals().stream().map(SFMReleaseReviewV1.SelectorProposal::kind).toList());
        assertEquals(1, batch.diagnostics().size());
        assertTrue(batch.diagnostics().get(0).message().contains("stale"));
    }

    @Test
    void ambiguousCurrentJavaOutlinksRetainEveryDestinationAndRelationProvenance() {
        String text = "class A { Object value; }\n";
        int start = text.indexOf("value");
        SFMJavaInteractionMap.Region source = region("region:value", "java-identifier", start, start + 5);
        SFMJavaInteractionMap.Outlink first = navigation(
                "outlink:definition:a", source.id(), "external:a", "symbol://example.A#value");
        SFMJavaInteractionMap.Outlink second = navigation(
                "outlink:definition:b", source.id(), "external:b", "symbol://example.B#value");
        SFMJavaInteractionMap.Classification classification = new SFMJavaInteractionMap.Classification(
                source.id(), SFMJavaInteractionMap.ClassificationStatus.ACTIONABLE, Optional.empty(),
                List.of(second.id(), first.id()), List.of());
        SFMJavaInteractionMap.Result map = currentMap(
                text, List.of(source), List.of(classification), List.of(second, first));

        ProposalCase selected = proposeAt(text, map, start, 5);
        SFMReleaseReviewV1.SelectorProposal symbol = selected.batch().proposals().stream()
                .filter(value -> value.kind() == SFMReleaseReviewV1.SelectorKind.SYMBOL)
                .findFirst().orElseThrow();

        assertEquals(Optional.of("symbol://example.A#value|symbol://example.B#value"), symbol.semanticKey());
        assertEquals(SFMReleaseReviewV1.ProposalConfidence.CONSERVATIVE, symbol.confidence());
        assertTrue(symbol.diagnostics().stream().anyMatch(value -> value.contains("ambiguous")));
        assertEquals(List.of("relation-0", "relation-1"), symbol.semanticProvenance().stream()
                .map(SFMReleaseReviewV1.Evidence::key)
                .filter(value -> value.startsWith("relation-"))
                .toList());
        String relationWitnesses = symbol.semanticProvenance().stream()
                .filter(value -> value.key().startsWith("relation-"))
                .map(SFMReleaseReviewV1.Evidence::value)
                .collect(java.util.stream.Collectors.joining("\n"));
        assertTrue(relationWitnesses.contains(first.id()));
        assertTrue(relationWitnesses.contains(second.id()));
    }

    @Test
    void manyExplicitKindsAndDisjointRangesRemainSeparateConservativeChoices() {
        String text = "int answer() { return 42; }\n";
        Fixture fixture = fixture(text, 4, 10);
        String projection = hash("projection");
        var provider = new SFMReleaseReviewSelectorProposalAdapter.SemanticEvidenceProvider() {
            @Override
            public String id() {
                return "test:semantic-regions";
            }

            @Override
            public List<SFMReleaseReviewSelectorProposalAdapter.SemanticEvidence> propose(
                    SFMReleaseReviewV1.PinnedSelection selection,
                    SFMReleaseReviewSelectionAdapter.DocumentResolver documents
            ) {
                return List.of(
                        evidence(SFMReleaseReviewV1.SelectorKind.DECLARATION, "method", 0, 27, projection),
                        evidence(SFMReleaseReviewV1.SelectorKind.SIGNATURE, "method/signature", 0, 13, projection),
                        evidence(SFMReleaseReviewV1.SelectorKind.BODY, "method/body", 13, 27, projection),
                        evidence(SFMReleaseReviewV1.SelectorKind.RETURN_TYPE, "method/return", 0, 3, projection),
                        evidence(SFMReleaseReviewV1.SelectorKind.SYMBOL, "example.Type#answer()I", 4, 10, projection),
                        new SFMReleaseReviewSelectorProposalAdapter.SemanticEvidence(
                                SFMReleaseReviewV1.SelectorKind.BOUNDED_MULTI_REGION,
                                "method/name-and-return",
                                List.of(
                                        new SFMReleaseReviewV1.AddressedRange("document-a", 0, 3),
                                        new SFMReleaseReviewV1.AddressedRange("document-a", 4, 10)
                                ),
                                List.of(new SFMReleaseReviewV1.Evidence("authority", "test fixture")),
                                SFMReleaseReviewV1.ProposalConfidence.CONSERVATIVE,
                                projection,
                                "snapshot-a",
                                List.of("test.explicit-disjoint-regions")
                        )
                );
            }
        };

        var batch = SFMReleaseReviewSelectorProposalAdapter.propose(
                fixture.adapted(), fixture.documents(), List.of(provider)
        );

        assertEquals(7, batch.proposals().size());
        assertEquals(List.of(
                        SFMReleaseReviewV1.SelectorKind.LITERAL,
                        SFMReleaseReviewV1.SelectorKind.DECLARATION,
                        SFMReleaseReviewV1.SelectorKind.SIGNATURE,
                        SFMReleaseReviewV1.SelectorKind.BODY,
                        SFMReleaseReviewV1.SelectorKind.RETURN_TYPE,
                        SFMReleaseReviewV1.SelectorKind.SYMBOL,
                        SFMReleaseReviewV1.SelectorKind.BOUNDED_MULTI_REGION
                ),
                batch.proposals().stream().map(SFMReleaseReviewV1.SelectorProposal::kind).toList());
        assertTrue(batch.proposals().stream().skip(1)
                .allMatch(proposal -> proposal.literalWitness().equals(fixture.adapted().pinnedSelection())));
    }

    @Test
    void explicitMultipleEditorRangesOfferABoundedMultiRegionProposal() {
        String text = "one two three";
        String revision = "selection-revision-multi";
        var document = SFMReleaseReviewSelectionAdapter.DocumentWitness.capture(
                "document-a", revision, "file:///A.java", text
        );
        var capture = new SFMReleaseReviewSelectionAdapter.SelectionCapture(
                revision,
                "selection://multi@revision-1",
                List.of(new SFMReleaseReviewSelectionAdapter.OrderedDocumentSelection(
                        document,
                        List.of(
                                selection("one", text, 0, 3, true),
                                selection("three", text, 8, 13, false)
                        )
                ))
        );
        var adapted = SFMReleaseReviewSelectionAdapter.adapt(capture);

        var batch = SFMReleaseReviewSelectorProposalAdapter.propose(
                adapted, SFMReleaseReviewSelectionAdapter.resolver(capture), List.of()
        );

        assertEquals(List.of(
                        SFMReleaseReviewV1.SelectorKind.LITERAL,
                        SFMReleaseReviewV1.SelectorKind.BOUNDED_MULTI_REGION
                ),
                batch.proposals().stream().map(SFMReleaseReviewV1.SelectorProposal::kind).toList());
        assertInstanceOf(SFMReviewSessionV1.Union.class, batch.proposals().get(1).selectionRule());
    }

    @Test
    void providerFailureIsDiagnosedWithoutLosingTheLiteralWitness() {
        Fixture fixture = fixture("class A {}\n", 6, 7);
        var failed = new SFMReleaseReviewSelectorProposalAdapter.SemanticEvidenceProvider() {
            @Override
            public String id() {
                return "test:failed-provider";
            }

            @Override
            public List<SFMReleaseReviewSelectorProposalAdapter.SemanticEvidence> propose(
                    SFMReleaseReviewV1.PinnedSelection selection,
                    SFMReleaseReviewSelectionAdapter.DocumentResolver documents
            ) {
                throw new IllegalStateException("provider offline");
            }
        };

        var batch = SFMReleaseReviewSelectorProposalAdapter.propose(
                fixture.adapted(), fixture.documents(), List.of(failed)
        );

        assertEquals(1, batch.proposals().size());
        assertEquals(SFMReleaseReviewV1.SelectorKind.LITERAL, batch.proposals().get(0).kind());
        assertEquals(1, batch.diagnostics().size());
        assertEquals("review.semantic-provider-failed", batch.diagnostics().get(0).code());
        assertEquals("test:failed-provider", batch.diagnostics().get(0).providerId());
        assertTrue(batch.diagnostics().get(0).message().contains("provider offline"));
    }

    private static SFMReleaseReviewSelectorProposalAdapter.SemanticEvidence evidence(
            SFMReleaseReviewV1.SelectorKind kind,
            String key,
            int start,
            int end,
            String projection
    ) {
        return new SFMReleaseReviewSelectorProposalAdapter.SemanticEvidence(
                kind,
                key,
                List.of(new SFMReleaseReviewV1.AddressedRange("document-a", start, end)),
                List.of(new SFMReleaseReviewV1.Evidence("authority", "test fixture")),
                SFMReleaseReviewV1.ProposalConfidence.EXACT,
                projection,
                "snapshot-a",
                List.of()
        );
    }

    private static ProposalCase proposeAt(
            String text,
            SFMJavaInteractionMap.Result map,
            int startByte,
            int byteLength
    ) {
        Fixture fixture = fixture(text, startByte, startByte + byteLength);
        var batch = SFMReleaseReviewSelectorProposalAdapter.propose(
                fixture.adapted(), fixture.documents(),
                List.of(new SFMReleaseReviewSelectorProposalAdapter.JavaInteractionMapProvider(
                        Map.of("document-a", map)))
        );
        return new ProposalCase(fixture, batch);
    }

    private static void assertStructuralProposal(
            ProposalCase selected,
            String semanticKey,
            SFMReleaseReviewV1.SelectorKind kind,
            int expectedStart,
            int expectedEnd
    ) {
        SFMReleaseReviewV1.SelectorProposal proposal = proposal(selected.batch(), kind, semanticKey);
        assertEquals(selected.fixture().adapted().pinnedSelection(), proposal.literalWitness(),
                "every semantic proposal must retain the exact selected bytes");
        var rule = assertInstanceOf(SFMReviewSessionV1.LiteralUtf8Range.class, proposal.selectionRule());
        assertEquals(expectedStart, rule.startByte());
        assertEquals(expectedEnd, rule.endByte());
        assertEquals(SFMReleaseReviewV1.ProposalConfidence.EXACT, proposal.confidence());
    }

    private static SFMReleaseReviewV1.SelectorProposal proposal(
            SFMReleaseReviewSelectorProposalAdapter.ProposalBatch batch,
            SFMReleaseReviewV1.SelectorKind kind,
            String semanticKey
    ) {
        return batch.proposals().stream()
                .filter(value -> value.kind() == kind)
                .filter(value -> value.semanticKey().equals(Optional.of(semanticKey)))
                .findFirst().orElseThrow(() -> new AssertionError(
                        "Missing " + kind + " proposal " + semanticKey + ": " + batch.proposals()));
    }

    private static SFMJavaInteractionMap.Result structuralPublication(String text) {
        int importStart = 0;
        int importEnd = text.indexOf('\n');
        int importNameStart = text.indexOf("List");
        int typeStart = text.indexOf("class Example");
        int typeEnd = text.lastIndexOf('}') + 1;
        int typeNameStart = text.indexOf("Example");
        int fieldStart = text.indexOf("int field");
        int fieldEnd = text.indexOf(';', fieldStart) + 1;
        int fieldNameStart = text.indexOf("field", fieldStart);
        int methodStart = text.indexOf("String method");
        int methodBodyStart = text.indexOf('{', methodStart);
        int methodEnd = text.indexOf('}', methodBodyStart) + 1;
        int methodNameStart = text.indexOf("method", methodStart);
        List<SFMJavaInteractionMap.Region> regions = List.of(
                region("region:import-name", "java-identifier", importNameStart, importNameStart + 4),
                region("region:import", "java-import", importStart, importEnd),
                region("region:type-name", "java-identifier", typeNameStart, typeNameStart + 7),
                region("region:type", "java-class-declaration", typeStart, typeEnd),
                region("region:field-name", "java-identifier", fieldNameStart, fieldNameStart + 5),
                region("region:field", "java-field-declaration", fieldStart, fieldEnd),
                region("region:method-name", "java-identifier", methodNameStart, methodNameStart + 6),
                region("region:method", "java-method-declaration", methodStart, methodEnd),
                region("region:method/signature", "java-signature", methodStart, methodBodyStart),
                region("region:method/body", "java-body", methodBodyStart, methodEnd),
                region("region:method/open-brace", "java-delimiter", methodBodyStart, methodBodyStart + 1)
        );
        List<SFMJavaInteractionMap.Outlink> outlinks = List.of(
                relation("outlink:import-parent", "region:import-name", "region:import", "containing-region"),
                relation("outlink:type-parent", "region:type-name", "region:type", "containing-region"),
                relation("outlink:field-parent", "region:field-name", "region:field", "containing-region"),
                relation("outlink:field-type-parent", "region:field", "region:type", "containing-region"),
                relation("outlink:method-parent", "region:method-name", "region:method", "containing-region"),
                relation("outlink:method-type-parent", "region:method", "region:type", "containing-region"),
                relation("outlink:brace-parent", "region:method/open-brace", "region:method/body", "containing-region"),
                relation("outlink:body-parent", "region:method/body", "region:method", "containing-region"),
                relation("outlink:method-signature", "region:method", "region:method/signature", "signature"),
                relation("outlink:method-body", "region:method", "region:method/body", "body")
        );
        return currentMap(text, regions, List.of(), outlinks);
    }

    private static SFMJavaInteractionMap.Region region(
            String id,
            String semanticKind,
            int start,
            int end
    ) {
        return new SFMJavaInteractionMap.Region(
                SFMJavaInteractionMap.REGION_SCHEMA, id, "domain:utf8", "utf8-byte-range",
                List.of(new SFMJavaInteractionMap.Axis(start, end)), "half-open", semanticKind,
                "rust:arborium/current-java-publication", List.of("projection:utf8-canvas"));
    }

    private static SFMJavaInteractionMap.Outlink relation(
            String id,
            String source,
            String destination,
            String kind
    ) {
        return new SFMJavaInteractionMap.Outlink(
                SFMJavaInteractionMap.OUTLINK_SCHEMA, id, source, destination, Optional.empty(), kind,
                "navigate", "rust:arborium", 7, "certified Java syntax relation", "resolved", "complete",
                "start", List.of(), "rust:arborium/current-java-publication");
    }

    private static SFMJavaInteractionMap.Outlink navigation(
            String id,
            String source,
            String destination,
            String destinationQuery
    ) {
        return new SFMJavaInteractionMap.Outlink(
                SFMJavaInteractionMap.OUTLINK_SCHEMA, id, source, destination, Optional.of(destinationQuery),
                "definition", "navigate", "rust:java-symbol-index", 7,
                "certified Java definition outlink", "resolved", "complete", "start", List.of(),
                "rust:java-symbol-index/current-java-publication");
    }

    private static SFMJavaInteractionMap.Result currentMap(
            String text,
            List<SFMJavaInteractionMap.Region> regions,
            List<SFMJavaInteractionMap.Classification> classifications,
            List<SFMJavaInteractionMap.Outlink> outlinks
    ) {
        String taggedHash = SFMDefinitionRequest.sha256(text);
        var document = new SFMDefinitionResult.DocumentIdentity(
                "file:///workspace/A.java", "root-main", "A.java", "A.java", "main",
                taggedHash, Optional.empty());
        var utf8 = new SFMJavaInteractionMap.Domain(
                SFMJavaInteractionMap.DOMAIN_SCHEMA, "domain:utf8", "utf8", 1,
                List.of("utf8-byte"), "rust:arborium", taggedHash);
        var canvas = new SFMJavaInteractionMap.Domain(
                SFMJavaInteractionMap.DOMAIN_SCHEMA, "domain:canvas", "canvas", 2,
                List.of("x", "y"), "java:editor-v3", taggedHash);
        var projection = new SFMJavaInteractionMap.Projection(
                SFMJavaInteractionMap.PROJECTION_SCHEMA, "projection:utf8-canvas", utf8.id(), canvas.id(),
                "lossless", "complete", "editor-layout", "projection-current-java", "java:editor-v3");
        return new SFMJavaInteractionMap.Result(
                SFMJavaInteractionMap.RESULT_SCHEMA, 1, 1, 1, "workspace-current-java", 1, 7,
                "blake3:" + "7".repeat(64), SFMJavaInteractionMap.Outcome.SUCCESS, document,
                List.of(utf8, canvas), List.of(projection), regions, classifications, outlinks,
                List.of(), List.of(), List.of(),
                new SFMJavaInteractionMap.Page(0, regions.size(), regions.size(), Optional.empty(),
                        0, 0, 0, Optional.empty(), 1024), List.of());
    }

    private static SFMJavaInteractionMap.Result copyMap(
            SFMJavaInteractionMap.Result source,
            SFMDefinitionResult.DocumentIdentity document
    ) {
        return new SFMJavaInteractionMap.Result(
                source.schema(), source.requestId(), source.requestGeneration(), source.workspaceGeneration(),
                source.workspaceFingerprint(), source.documentGeneration(), source.semanticGeneration(),
                source.semanticFingerprint(), source.outcome(), document, source.domains(), source.projections(),
                source.regions(), source.classifications(), source.outlinks(), source.reciprocity(),
                source.exceptions(), source.files(), source.page(), source.diagnostics());
    }

    private static Fixture fixture(String text, int startByte, int endByte) {
        String revision = "selection-revision-a";
        var document = SFMReleaseReviewSelectionAdapter.DocumentWitness.capture(
                "document-a", revision, "file:///workspace/A.java", text
        );
        var capture = new SFMReleaseReviewSelectionAdapter.SelectionCapture(
                revision,
                "file:///workspace/A.java",
                List.of(new SFMReleaseReviewSelectionAdapter.OrderedDocumentSelection(
                        document,
                        List.of(selection("primary", text, startByte, endByte, true))
                ))
        );
        return new Fixture(
                SFMReleaseReviewSelectionAdapter.adapt(capture),
                SFMReleaseReviewSelectionAdapter.resolver(capture)
        );
    }

    private static SFMJavaInteractionMap.Result declarationMap(String text) {
        String taggedHash = SFMDefinitionRequest.sha256(text);
        var document = new SFMDefinitionResult.DocumentIdentity(
                "file:///workspace/A.java",
                "root-main",
                "A.java",
                "A.java",
                "main",
                taggedHash,
                Optional.empty()
        );
        var utf8 = new SFMJavaInteractionMap.Domain(
                SFMJavaInteractionMap.DOMAIN_SCHEMA,
                "domain:utf8",
                "utf8",
                1,
                List.of("utf8-byte"),
                "rust:arborium",
                taggedHash
        );
        var canvas = new SFMJavaInteractionMap.Domain(
                SFMJavaInteractionMap.DOMAIN_SCHEMA,
                "domain:canvas",
                "canvas",
                2,
                List.of("x", "y"),
                "java:editor-v3",
                taggedHash
        );
        var projection = new SFMJavaInteractionMap.Projection(
                SFMJavaInteractionMap.PROJECTION_SCHEMA,
                "projection:utf8-canvas",
                utf8.id(),
                canvas.id(),
                "lossless",
                "complete",
                "editor-layout",
                "projection-fixture",
                "java:editor-v3"
        );
        var identifier = new SFMJavaInteractionMap.Region(
                SFMJavaInteractionMap.REGION_SCHEMA,
                "region:identifier",
                utf8.id(),
                "utf8-byte-range",
                List.of(new SFMJavaInteractionMap.Axis(6, 7)),
                "half-open",
                "java-identifier",
                "arborium",
                List.of(projection.id())
        );
        var declaration = new SFMJavaInteractionMap.Region(
                SFMJavaInteractionMap.REGION_SCHEMA,
                "region:class-declaration",
                utf8.id(),
                "utf8-byte-range",
                List.of(new SFMJavaInteractionMap.Axis(0, 10)),
                "half-open",
                "java-class-declaration",
                "arborium",
                List.of(projection.id())
        );
        var containing = new SFMJavaInteractionMap.Outlink(
                SFMJavaInteractionMap.OUTLINK_SCHEMA,
                "outlink:containing",
                identifier.id(),
                declaration.id(),
                Optional.empty(),
                "containing-region",
                "navigate",
                "rust:arborium",
                3,
                "exact syntax parent",
                "resolved",
                "complete",
                "start",
                List.of(),
                "arborium"
        );
        return new SFMJavaInteractionMap.Result(
                SFMJavaInteractionMap.RESULT_SCHEMA,
                1,
                1,
                1,
                "workspace-fixture",
                1,
                3,
                "blake3:" + "1".repeat(64),
                SFMJavaInteractionMap.Outcome.SUCCESS,
                document,
                List.of(utf8, canvas),
                List.of(projection),
                List.of(identifier, declaration),
                List.of(),
                List.of(containing),
                List.of(),
                List.of(),
                List.of(),
                new SFMJavaInteractionMap.Page(0, 2, 2, Optional.empty(), 0, 0, 0,
                        Optional.empty(), 256),
                List.of()
        );
    }

    private static SFMJavaInteractionMap.Result lexicalOnlyMap(SFMJavaInteractionMap.Result source) {
        return new SFMJavaInteractionMap.Result(
                source.schema(),
                source.requestId(),
                source.requestGeneration(),
                source.workspaceGeneration(),
                source.workspaceFingerprint(),
                source.documentGeneration(),
                source.semanticGeneration(),
                source.semanticFingerprint(),
                source.outcome(),
                source.document(),
                source.domains(),
                source.projections(),
                List.of(source.regions().get(0)),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                source.files(),
                new SFMJavaInteractionMap.Page(0, 1, 1, Optional.empty(), 0, 0, 0,
                        Optional.empty(), 128),
                source.diagnostics()
        );
    }

    private static SFMTextDocumentSelection selection(
            String id,
            String text,
            int start,
            int end,
            boolean primary
    ) {
        return new SFMTextDocumentSelection(
                id,
                SFMTextDocumentRange.positionAtByteOffset(text, start),
                SFMTextDocumentRange.positionAtByteOffset(text, end),
                primary
        );
    }

    private static String hash(String value) {
        return SFMReviewSessionV1Kernel.sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private record Fixture(
            SFMReleaseReviewSelectionAdapter.AdaptedSelection adapted,
            SFMReleaseReviewSelectionAdapter.DocumentResolver documents
    ) {
    }

    private record ProposalCase(
            Fixture fixture,
            SFMReleaseReviewSelectorProposalAdapter.ProposalBatch batch
    ) {
    }
}
