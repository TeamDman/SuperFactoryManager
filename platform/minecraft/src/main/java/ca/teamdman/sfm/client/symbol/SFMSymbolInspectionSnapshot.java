package ca.teamdman.sfm.client.symbol;

import ca.teamdman.sfm.client.context.SFMContextActionProvider;
import ca.teamdman.sfm.client.context.SFMContextContribution;
import ca.teamdman.sfm.client.context.SFMContextCursorProjection;
import ca.teamdman.sfm.client.context.SFMContextDocumentProjection;
import ca.teamdman.sfm.client.context.SFMContextPosition;
import ca.teamdman.sfm.client.context.SFMContextTextCoordinates;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentPosition;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentRange;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * One immutable, copy-safe description of the exact Java editor point which
 * opened a contextual action surface.
 *
 * <p>The snapshot is intentionally useful without a successful symbol
 * resolution. Document identity, current content hash, source coordinates and
 * a location replay command are capture-time facts; semantic-map data only
 * enriches them when an exact, current publication is already available.</p>
 */
public record SFMSymbolInspectionSnapshot(
        String schema,
        long captureGeneration,
        long workspaceGeneration,
        long focusGeneration,
        String originId,
        long contributorGeneration,
        long contentGeneration,
        long selectionGeneration,
        long relationGeneration,
        Document document,
        Point point,
        Region region,
        List<Symbol> symbols,
        String confidence,
        String completeness,
        List<Outlink> definitionOutlinks,
        List<Outlink> referenceOutlinks,
        List<String> diagnostics,
        String replayCommand
) {
    public static final String SCHEMA = "sfm.symbol-inspection/1";

    public SFMSymbolInspectionSnapshot {
        if (!SCHEMA.equals(schema)) throw new IllegalArgumentException("Unsupported symbol inspection schema");
        requireNonNegative(captureGeneration, "captureGeneration");
        requireNonNegative(workspaceGeneration, "workspaceGeneration");
        requireNonNegative(focusGeneration, "focusGeneration");
        originId = requireText(originId, "originId");
        requireNonNegative(contributorGeneration, "contributorGeneration");
        requireNonNegative(contentGeneration, "contentGeneration");
        requireNonNegative(selectionGeneration, "selectionGeneration");
        requireNonNegative(relationGeneration, "relationGeneration");
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(point, "point");
        Objects.requireNonNull(region, "region");
        symbols = List.copyOf(symbols);
        confidence = requireText(confidence, "confidence");
        completeness = requireText(completeness, "completeness");
        definitionOutlinks = List.copyOf(definitionOutlinks);
        referenceOutlinks = List.copyOf(referenceOutlinks);
        diagnostics = List.copyOf(diagnostics);
        replayCommand = requireText(replayCommand, "replayCommand");
        region.textRange().validateAgainst(document.currentText());
    }

    public static Optional<SFMSymbolInspectionSnapshot> capture(
            SFMContextActionProvider.Request request,
            Optional<SemanticEvidence> suppliedEvidence,
            String minecraftVersion
    ) {
        return capture(request, suppliedEvidence, Optional.of(requireText(minecraftVersion, "minecraftVersion")));
    }

    public static Optional<SFMSymbolInspectionSnapshot> capture(
            SFMContextActionProvider.Request request,
            Optional<SemanticEvidence> suppliedEvidence,
            Optional<String> minecraftBranch
    ) {
        Objects.requireNonNull(request, "request");
        suppliedEvidence = Objects.requireNonNull(suppliedEvidence, "suppliedEvidence");
        minecraftBranch = optionalText(minecraftBranch, "minecraftBranch");
        Optional<SFMContextContribution> focused = request.focusedContribution();
        if (focused.isEmpty()
                || !(focused.orElseThrow().projection() instanceof SFMContextDocumentProjection projection)) {
            return Optional.empty();
        }
        Optional<CapturedPoint> capturedPoint = capturePoint(projection);
        if (capturedPoint.isEmpty()) return Optional.empty();

        SFMContextContribution contribution = focused.orElseThrow();
        CapturedPoint local = capturedPoint.orElseThrow();
        SemanticEvidence evidence = suppliedEvidence
                .filter(value -> value.matches(projection, local.position()))
                .orElse(null);
        SFMTextDocumentRange range = evidence == null ? local.localRange() : evidence.textRange();
        range.validateAgainst(projection.currentText());
        String regionText = sliceUtf8(projection.currentText(), range);

        Document document = captureDocument(projection, evidence);
        Point point = new Point(local.position(), local.canvasPoint());
        Region region = new Region(
                range,
                regionText,
                evidence == null ? Optional.empty() : evidence.regionId(),
                evidence == null ? local.localKind() : evidence.semanticKind(),
                evidence == null ? List.of() : evidence.logicalPath(),
                evidence == null ? List.of() : evidence.glyphs(),
                evidence == null ? List.of() : evidence.canvasBounds(),
                evidence == null ? List.of() : evidence.localScreenBounds(),
                evidence == null ? List.of() : evidence.globalScreenBounds(),
                evidence == null ? List.of() : evidence.physicalPixelBounds(),
                evidence == null ? Optional.empty() : evidence.localScreenViewport()
        );

        List<String> selectorValues = evidence == null ? List.of() : evidence.canonicalSelectors();
        boolean accessTransformerKind = local.localKind().equals("java-identifier") && evidence != null
                && isAccessTransformerSemanticKind(evidence.semanticKind());
        List<Symbol> symbols = selectorValues.stream()
                .distinct()
                .sorted()
                .map(Symbol::fromAccessTransformerReference)
                .map(symbol -> accessTransformerKind
                        ? symbol
                        : symbol.withUnavailableKind(evidence == null
                                ? local.localKind()
                                : evidence.semanticKind()))
                .toList();
        ArrayList<String> diagnostics = new ArrayList<>();
        diagnostics.addAll(projection.baseline().diagnostics());
        if (evidence == null) {
            diagnostics.add("java.semantic-map-unavailable: no exact current semantic publication was available; "
                    + "the snapshot contains lexical/source evidence only");
        } else {
            diagnostics.addAll(evidence.diagnostics());
        }
        if (symbols.size() > 1) {
            diagnostics.add("java.ambiguous-symbol: " + symbols.size()
                    + " exact canonical selectors are associated with the captured region");
        }
        if (symbols.isEmpty()) {
            diagnostics.add("java.access-transformer-reference-unavailable: no exact representable static symbol "
                    + "is associated with the captured region");
        }
        if (minecraftBranch.isEmpty()) {
            diagnostics.add("java.replay-branch-unavailable: no Minecraft branch was configured; set -D"
                    + SFMSymbolNavigationRuntime.BRANCH_PROPERTY
                    + "=<branch> to produce an executable replay command");
        }
        diagnostics = new ArrayList<>(diagnostics.stream()
                .map(String::strip)
                .filter(value -> !value.isEmpty())
                .distinct()
                .sorted()
                .toList());

        String replay = replayCommand(document, point, symbols, minecraftBranch);
        var generations = contribution.generations();
        return Optional.of(new SFMSymbolInspectionSnapshot(
                SCHEMA,
                request.snapshot().captureGeneration(),
                request.snapshot().workspaceGeneration(),
                request.snapshot().focusGeneration(),
                contribution.originId().canonical(),
                generations.contributorGeneration(),
                generations.contentGeneration(),
                generations.selectionGeneration(),
                generations.relationGeneration(),
                document,
                point,
                region,
                symbols,
                evidence == null ? "unresolved" : evidence.confidence(),
                evidence == null ? "incomplete" : evidence.completeness(),
                evidence == null ? List.of() : relation(evidence.outlinks(), "definition"),
                evidence == null ? List.of() : relation(evidence.outlinks(), "reference"),
                diagnostics,
                replay
        ));
    }

    /** Exact primary point and deterministic local extent used by both capture and evidence lookup. */
    public static Optional<CapturedPoint> capturePoint(SFMContextDocumentProjection document) {
        Objects.requireNonNull(document, "document");
        Optional<SFMContextCursorProjection> primary = document.cursors().stream()
                .filter(SFMContextCursorProjection::primary)
                .filter(SFMContextCursorProjection::active)
                .findFirst();
        if (primary.isEmpty()) return Optional.empty();
        SFMContextPosition sourcePosition = primary.orElseThrow().position();
        Optional<SFMTextDocumentPosition> textPosition;
        Optional<CanvasPoint> canvasPoint;
        if (sourcePosition instanceof SFMContextPosition.Text text) {
            textPosition = Optional.of(text.position());
            canvasPoint = Optional.empty();
        } else if (sourcePosition instanceof SFMContextPosition.Canvas canvas) {
            textPosition = canvas.textHit();
            canvasPoint = Optional.of(new CanvasPoint(canvas.x(), canvas.y()));
        } else {
            return Optional.empty();
        }
        if (textPosition.isEmpty()) return Optional.empty();
        SFMTextDocumentPosition point = textPosition.orElseThrow();
        LocalExtent extent = localExtent(document.currentText(), point);
        return Optional.of(new CapturedPoint(point, canvasPoint, extent.range(), extent.kind()));
    }

    /** Byte probe which retains exact punctuation and associates an end caret with its preceding identifier. */
    public static int semanticProbeByte(String text, SFMTextDocumentPosition position) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(position, "position");
        int utf16 = SFMContextTextCoordinates.utf16OffsetAtUtf8Byte(text, position.byteOffset());
        if (utf16 < text.length()) return position.byteOffset();
        if (utf16 == 0) return 0;
        int previous = text.offsetByCodePoints(utf16, -1);
        return SFMContextTextCoordinates.atUtf16Offset(text, previous).byteOffset();
    }

    public Optional<String> exactAccessTransformerReference() {
        return symbols.size() == 1 && symbols.get(0).accessTransformerRepresentable()
                ? Optional.of(symbols.get(0).canonicalSelector())
                : Optional.empty();
    }

    public String accessTransformerUnavailableReason() {
        if (symbols.isEmpty()) return "no exact representable static symbol was resolved";
        if (symbols.size() > 1) return "the captured point is ambiguous across " + symbols.size() + " symbols";
        return "symbol kind " + symbols.get(0).kind() + " is not representable by Access Transformers";
    }

    public record Document(
            String editorId,
            String state,
            Optional<String> address,
            Optional<String> resolverId,
            Optional<String> rootId,
            Optional<String> authorizedRoot,
            Optional<String> rootRelativePath,
            Optional<String> reportPath,
            Optional<String> sourceSet,
            Optional<String> baselineSha256,
            String currentSha256,
            String currentText,
            boolean dirty,
            boolean readOnly
    ) {
        public Document {
            editorId = requireText(editorId, "editorId");
            state = requireText(state, "state");
            address = optionalText(address, "address");
            resolverId = optionalText(resolverId, "resolverId");
            rootId = optionalText(rootId, "rootId");
            authorizedRoot = optionalText(authorizedRoot, "authorizedRoot");
            rootRelativePath = optionalText(rootRelativePath, "rootRelativePath");
            reportPath = optionalText(reportPath, "reportPath");
            sourceSet = optionalText(sourceSet, "sourceSet");
            baselineSha256 = optionalText(baselineSha256, "baselineSha256");
            currentSha256 = requireHash(currentSha256, "currentSha256");
            Objects.requireNonNull(currentText, "currentText");
        }
    }

    public record Point(SFMTextDocumentPosition text, Optional<CanvasPoint> canvas) {
        public Point {
            Objects.requireNonNull(text, "text");
            canvas = Objects.requireNonNull(canvas, "canvas");
        }
    }

    public record CanvasPoint(double x, double y) {
        public CanvasPoint {
            requireFinite(x, "canvas x");
            requireFinite(y, "canvas y");
        }
    }

    public record Rectangle(double left, double top, double right, double bottom) {
        public Rectangle {
            requireFinite(left, "rectangle left");
            requireFinite(top, "rectangle top");
            requireFinite(right, "rectangle right");
            requireFinite(bottom, "rectangle bottom");
            if (right < left || bottom < top) throw new IllegalArgumentException("Rectangle bounds are reversed");
        }
    }

    /** One immutable rendered glyph participating in the captured semantic/text extent. */
    public record Glyph(
            int ordinal,
            int utf16Offset,
            String text,
            Rectangle canvasBounds
    ) {
        public Glyph {
            if (ordinal < 0) throw new IllegalArgumentException("Glyph ordinal must be non-negative");
            if (utf16Offset < 0) throw new IllegalArgumentException("Glyph UTF-16 offset must be non-negative");
            Objects.requireNonNull(text, "glyph text");
            if (text.isEmpty()) throw new IllegalArgumentException("Glyph text must not be empty");
            Objects.requireNonNull(canvasBounds, "canvasBounds");
        }
    }

    public record Region(
            SFMTextDocumentRange textRange,
            String selectedText,
            Optional<String> semanticRegionId,
            String semanticKind,
            List<String> logicalPath,
            List<Glyph> glyphs,
            List<Rectangle> canvasBounds,
            List<Rectangle> localScreenBounds,
            List<Rectangle> globalScreenBounds,
            List<Rectangle> physicalPixelBounds,
            Optional<Rectangle> localScreenViewport
    ) {
        public Region {
            Objects.requireNonNull(textRange, "textRange");
            Objects.requireNonNull(selectedText, "selectedText");
            semanticRegionId = optionalText(semanticRegionId, "semanticRegionId");
            semanticKind = requireText(semanticKind, "semanticKind");
            logicalPath = immutableText(logicalPath, "logicalPath");
            glyphs = List.copyOf(glyphs);
            canvasBounds = List.copyOf(canvasBounds);
            localScreenBounds = List.copyOf(localScreenBounds);
            globalScreenBounds = List.copyOf(globalScreenBounds);
            physicalPixelBounds = List.copyOf(physicalPixelBounds);
            localScreenViewport = Objects.requireNonNull(localScreenViewport, "localScreenViewport");
        }
    }

    public record Symbol(
            String kind,
            String owner,
            String name,
            Optional<String> descriptor,
            String qualifiedName,
            String canonicalSelector,
            boolean accessTransformerRepresentable
    ) {
        public Symbol {
            kind = requireText(kind, "symbol kind");
            owner = requireText(owner, "symbol owner");
            name = requireText(name, "symbol name");
            descriptor = optionalText(descriptor, "symbol descriptor");
            qualifiedName = requireText(qualifiedName, "qualifiedName");
            canonicalSelector = requireText(canonicalSelector, "canonicalSelector");
        }

        public static Symbol fromAccessTransformerReference(String selector) {
            String canonical = requireText(selector, "selector").strip();
            int separator = canonical.indexOf(' ');
            if (separator < 0) {
                String name = simpleName(canonical);
                return new Symbol("type", canonical, name, Optional.empty(), canonical, canonical, true);
            }
            String owner = canonical.substring(0, separator).strip();
            String member = canonical.substring(separator + 1).strip();
            if (owner.isEmpty() || member.isEmpty() || member.indexOf(' ') >= 0) {
                return unavailableSymbol(canonical, "invalid-access-transformer-selector");
            }
            int descriptor = member.indexOf('(');
            if (descriptor >= 0) {
                String name = member.substring(0, descriptor);
                String value = member.substring(descriptor);
                if (name.isEmpty() || !looksLikeMethodDescriptor(value)) {
                    return unavailableSymbol(canonical, "invalid-method-selector");
                }
                String kind = name.equals("<init>") ? "constructor" : "method";
                return new Symbol(kind, owner, name, Optional.of(value), owner + "." + name, canonical, true);
            }
            return new Symbol("field", owner, member, Optional.empty(), owner + "." + member, canonical, true);
        }

        private Symbol withUnavailableKind(String semanticKind) {
            return new Symbol(
                    requireText(semanticKind, "semanticKind"),
                    owner,
                    name,
                    descriptor,
                    qualifiedName,
                    canonicalSelector,
                    false
            );
        }

        private static Symbol unavailableSymbol(String canonical, String reason) {
            return new Symbol(reason, "<unresolved>", canonical, Optional.empty(), canonical, canonical, false);
        }
    }

    public record Outlink(
            String id,
            String relationKind,
            Optional<String> destinationQuery,
            String providerId,
            long providerGeneration,
            String confidence,
            String completeness,
            String recommendedProjection,
            String reason,
            String provenance
    ) {
        public Outlink {
            id = requireText(id, "outlink id");
            relationKind = requireText(relationKind, "outlink relationKind");
            destinationQuery = optionalText(destinationQuery, "outlink destinationQuery");
            providerId = requireText(providerId, "outlink providerId");
            requireNonNegative(providerGeneration, "outlink providerGeneration");
            confidence = requireText(confidence, "outlink confidence");
            completeness = requireText(completeness, "outlink completeness");
            recommendedProjection = requireText(recommendedProjection, "outlink recommendedProjection");
            reason = requireText(reason, "outlink reason");
            provenance = requireText(provenance, "outlink provenance");
        }
    }

    /** Optional exact enrichment copied from one already-published semantic map. */
    public record SemanticEvidence(
            String currentSha256,
            SFMTextDocumentPosition capturedPosition,
            DocumentEvidence document,
            SFMTextDocumentRange textRange,
            Optional<String> regionId,
            String semanticKind,
            List<String> logicalPath,
            List<Glyph> glyphs,
            List<String> canonicalSelectors,
            String confidence,
            String completeness,
            List<Outlink> outlinks,
            List<String> diagnostics,
            List<Rectangle> canvasBounds,
            List<Rectangle> localScreenBounds,
            List<Rectangle> globalScreenBounds,
            List<Rectangle> physicalPixelBounds,
            Optional<Rectangle> localScreenViewport
    ) {
        public SemanticEvidence {
            currentSha256 = requireHash(currentSha256, "semantic currentSha256");
            Objects.requireNonNull(capturedPosition, "capturedPosition");
            Objects.requireNonNull(document, "document");
            Objects.requireNonNull(textRange, "textRange");
            regionId = optionalText(regionId, "regionId");
            semanticKind = requireText(semanticKind, "semanticKind");
            logicalPath = immutableText(logicalPath, "logicalPath");
            glyphs = List.copyOf(glyphs);
            canonicalSelectors = immutableText(canonicalSelectors, "canonicalSelectors");
            confidence = requireText(confidence, "confidence");
            completeness = requireText(completeness, "completeness");
            outlinks = List.copyOf(outlinks);
            diagnostics = immutableText(diagnostics, "diagnostics");
            canvasBounds = List.copyOf(canvasBounds);
            localScreenBounds = List.copyOf(localScreenBounds);
            globalScreenBounds = List.copyOf(globalScreenBounds);
            physicalPixelBounds = List.copyOf(physicalPixelBounds);
            localScreenViewport = Objects.requireNonNull(localScreenViewport, "localScreenViewport");
        }

        boolean matches(SFMContextDocumentProjection projection, SFMTextDocumentPosition position) {
            return currentSha256.equals(projection.currentSha256()) && capturedPosition.equals(position);
        }
    }

    public record DocumentEvidence(
            Optional<String> address,
            Optional<String> resolverId,
            Optional<String> rootId,
            Optional<String> rootRelativePath,
            Optional<String> reportPath,
            Optional<String> sourceSet
    ) {
        public DocumentEvidence {
            address = optionalText(address, "address");
            resolverId = optionalText(resolverId, "resolverId");
            rootId = optionalText(rootId, "rootId");
            rootRelativePath = optionalText(rootRelativePath, "rootRelativePath");
            reportPath = optionalText(reportPath, "reportPath");
            sourceSet = optionalText(sourceSet, "sourceSet");
        }

        public static DocumentEvidence empty() {
            return new DocumentEvidence(
                    Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty());
        }
    }

    public record CapturedPoint(
            SFMTextDocumentPosition position,
            Optional<CanvasPoint> canvasPoint,
            SFMTextDocumentRange localRange,
            String localKind
    ) {
        public CapturedPoint {
            Objects.requireNonNull(position, "position");
            canvasPoint = Objects.requireNonNull(canvasPoint, "canvasPoint");
            Objects.requireNonNull(localRange, "localRange");
            localKind = requireText(localKind, "localKind");
        }
    }

    private static Document captureDocument(
            SFMContextDocumentProjection projection,
            SemanticEvidence evidence
    ) {
        var baseline = projection.baseline();
        DocumentEvidence semantic = evidence == null ? DocumentEvidence.empty() : evidence.document();
        Optional<String> address = semantic.address().or(() -> baseline.path().map(path -> path.canonical()));
        Optional<String> authorizedRoot = baseline.authorizedRoot().map(path -> path.canonical());
        Optional<String> relative = semantic.rootRelativePath().or(() -> relativePath(
                baseline.path().map(path -> path.canonical()),
                authorizedRoot
        ));
        Optional<String> resolver = semantic.resolverId().or(() -> baseline.path().map(path -> path.scheme()));
        return new Document(
                projection.editorId(),
                baseline.state().name().toLowerCase(Locale.ROOT),
                address,
                resolver,
                semantic.rootId(),
                authorizedRoot,
                relative,
                semantic.reportPath(),
                semantic.sourceSet(),
                baseline.sha256(),
                projection.currentSha256(),
                projection.currentText(),
                projection.dirty(),
                projection.readOnly()
        );
    }

    private static Optional<String> relativePath(Optional<String> address, Optional<String> root) {
        if (address.isEmpty() || root.isEmpty()) return Optional.empty();
        try {
            var path = ca.teamdman.sfm.client.explorer.SFMPath.parse(address.orElseThrow());
            var rootPath = ca.teamdman.sfm.client.explorer.SFMPath.parse(root.orElseThrow());
            if (path.kind() != ca.teamdman.sfm.client.explorer.SFMPath.Kind.FILE
                    || rootPath.kind() != ca.teamdman.sfm.client.explorer.SFMPath.Kind.FILE) {
                return Optional.empty();
            }
            java.nio.file.Path nativePath = path.toNativePath();
            java.nio.file.Path nativeRoot = rootPath.toNativePath();
            if (!nativePath.startsWith(nativeRoot)) return Optional.empty();
            String relative = nativeRoot.relativize(nativePath).toString().replace('\\', '/');
            return relative.isBlank() ? Optional.empty() : Optional.of(relative);
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static String replayCommand(
            Document document,
            Point point,
            List<Symbol> symbols,
            Optional<String> minecraftBranch
    ) {
        if (minecraftBranch.isEmpty()) {
            String capturedSource = document.rootRelativePath()
                    .or(() -> document.address())
                    .orElse("<unavailable: no source address was captured>");
            return "unavailable: no Minecraft branch was configured; set -D"
                    + SFMSymbolNavigationRuntime.BRANCH_PROPERTY
                    + "=<branch>; captured-source=" + psQuote(capturedSource)
                    + "; line=" + (point.text().line() + 1)
                    + "; column=" + (point.text().column() + 1)
                    + "; current-sha256=" + psQuote(document.currentSha256());
        }
        String branch = minecraftBranch.orElseThrow();
        String prefix = "sfm-propagate-changes.exe --output-format json symbol show-definition";
        if (document.rootRelativePath().isPresent()) {
            StringBuilder command = new StringBuilder(prefix)
                    .append(" --source-path ").append(psQuote(document.rootRelativePath().orElseThrow()));
            document.rootId().ifPresent(value -> command.append(" --source-root-id ").append(psQuote(value)));
            command.append(" --line ").append(point.text().line() + 1)
                    .append(" --column ").append(point.text().column() + 1)
                    .append(" --branch ").append(psQuote(branch));
            return command.toString();
        }
        if (symbols.size() == 1 && symbols.get(0).accessTransformerRepresentable()) {
            return prefix + " " + psQuote(symbols.get(0).canonicalSelector())
                    + " --branch " + psQuote(branch);
        }
        return prefix + " --source-path "
                + psQuote("<unavailable: no root-relative source path was captured>")
                + " --line " + (point.text().line() + 1)
                + " --column " + (point.text().column() + 1)
                + " --branch " + psQuote(branch);
    }

    private static List<Outlink> relation(List<Outlink> values, String kind) {
        return values.stream()
                .filter(value -> value.relationKind().equals(kind))
                .sorted(Comparator.comparing(Outlink::id))
                .toList();
    }

    private static LocalExtent localExtent(String text, SFMTextDocumentPosition position) {
        int insertion = SFMContextTextCoordinates.utf16OffsetAtUtf8Byte(text, position.byteOffset());
        int candidate = insertion;
        if (candidate >= text.length()) {
            candidate = candidate == 0 ? -1 : text.offsetByCodePoints(candidate, -1);
        }
        if (candidate < 0) {
            SFMTextDocumentRange empty = SFMContextTextCoordinates.rangeAtUtf16Offsets(text, insertion, insertion);
            return new LocalExtent(empty, "end-of-document");
        }
        if (text.codePointAt(candidate) == '@') {
            int identifier = candidate + 1;
            if (identifier < text.length() && Character.isJavaIdentifierStart(text.codePointAt(identifier))) {
                int end = identifier + Character.charCount(text.codePointAt(identifier));
                while (end < text.length() && isJavaIdentifierAt(text, end)) {
                    end += Character.charCount(text.codePointAt(end));
                }
                return new LocalExtent(
                        SFMContextTextCoordinates.rangeAtUtf16Offsets(text, candidate, end),
                        "java-identifier"
                );
            }
        }
        if (isJavaIdentifierAt(text, candidate)) {
            int start = candidate;
            while (start > 0) {
                int previous = text.offsetByCodePoints(start, -1);
                if (!isJavaIdentifierAt(text, previous)) break;
                start = previous;
            }
            int end = candidate + Character.charCount(text.codePointAt(candidate));
            while (end < text.length() && isJavaIdentifierAt(text, end)) {
                end += Character.charCount(text.codePointAt(end));
            }
            if (start > 0 && text.codePointBefore(start) == '@') start--;
            return new LocalExtent(
                    SFMContextTextCoordinates.rangeAtUtf16Offsets(text, start, end),
                    "java-identifier"
            );
        }
        int end = candidate + Character.charCount(text.codePointAt(candidate));
        int codePoint = text.codePointAt(candidate);
        String kind = Character.isWhitespace(codePoint) ? "java-whitespace" : "java-punctuation";
        return new LocalExtent(SFMContextTextCoordinates.rangeAtUtf16Offsets(text, candidate, end), kind);
    }

    private static boolean isJavaIdentifierAt(String text, int utf16) {
        return utf16 >= 0 && utf16 < text.length() && Character.isJavaIdentifierPart(text.codePointAt(utf16));
    }

    private static String sliceUtf8(String text, SFMTextDocumentRange range) {
        List<Integer> offsets = SFMContextTextCoordinates.utf16OffsetsAtUtf8Bytes(
                text,
                List.of(range.start().byteOffset(), range.end().byteOffset())
        );
        return text.substring(offsets.get(0), offsets.get(1));
    }

    private static String psQuote(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private static String simpleName(String owner) {
        int dot = Math.max(owner.lastIndexOf('.'), owner.lastIndexOf('$'));
        return dot < 0 ? owner : owner.substring(dot + 1);
    }

    private static boolean looksLikeMethodDescriptor(String value) {
        int close = value.indexOf(')');
        return value.startsWith("(") && close > 0 && close + 1 < value.length()
                && value.indexOf(' ', close) < 0;
    }

    private static boolean isAccessTransformerSemanticKind(String value) {
        String kind = requireText(value, "semanticKind").toLowerCase(Locale.ROOT);
        if (kind.contains("local") || kind.contains("parameter") || kind.contains("variable")
                || kind.contains("punctuation") || kind.contains("whitespace")
                || kind.contains("literal") || kind.contains("comment")
                || kind.contains("unresolved") || kind.contains("ambiguous")) {
            return false;
        }
        return kind.contains("type") || kind.contains("class") || kind.contains("interface")
                || kind.contains("enum") || kind.contains("record") || kind.contains("annotation")
                || kind.contains("field") || kind.contains("method") || kind.contains("constructor");
    }

    private static Optional<String> optionalText(Optional<String> value, String label) {
        Optional<String> copy = Objects.requireNonNull(value, label);
        copy.ifPresent(candidate -> requireText(candidate, label));
        return copy;
    }

    private static List<String> immutableText(List<String> values, String label) {
        LinkedHashSet<String> answer = new LinkedHashSet<>();
        for (String value : List.copyOf(values)) answer.add(requireText(value, label));
        return List.copyOf(answer);
    }

    private static String requireHash(String value, String label) {
        String answer = requireText(value, label);
        if (!answer.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(label + " must be a lowercase SHA-256 digest");
        }
        return answer;
    }

    private static String requireText(String value, String label) {
        Objects.requireNonNull(value, label);
        String answer = value.strip();
        if (answer.isEmpty()) throw new IllegalArgumentException(label + " must not be blank");
        return answer;
    }

    private static void requireFinite(double value, String label) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(label + " must be finite");
    }

    private static void requireNonNegative(long value, String label) {
        if (value < 0) throw new IllegalArgumentException(label + " must not be negative");
    }

    private record LocalExtent(SFMTextDocumentRange range, String kind) {
    }
}
