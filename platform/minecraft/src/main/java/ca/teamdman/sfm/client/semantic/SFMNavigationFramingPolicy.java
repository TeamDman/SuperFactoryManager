package ca.teamdman.sfm.client.semantic;

import java.util.Objects;

/**
 * Pure destination-framing policy for a canvas editor.
 *
 * <p>All rectangles are expressed in canvas coordinates; viewport width,
 * viewport height, and inset are screen pixels.  The policy therefore remains
 * deterministic across GUI scales while preserving the active zoom.</p>
 */
public final class SFMNavigationFramingPolicy {
    private static final double EPSILON = 0.000_001D;

    private SFMNavigationFramingPolicy() {
    }

    public record Request(
            String paneId,
            String documentAddress,
            String documentHash,
            String destinationRegionId,
            String landmarkProjection,
            SFMSpatialSemanticContract.Rectangle documentBounds,
            SFMSpatialSemanticContract.Rectangle lineBounds,
            SFMSpatialSemanticContract.Rectangle destinationBounds,
            double viewportWidth,
            double viewportHeight,
            double inset,
            SFMSpatialSemanticContract.Camera previousCamera
    ) {
        public Request {
            requireText(paneId, "pane id");
            requireText(documentAddress, "document address");
            requireText(documentHash, "document hash");
            requireText(destinationRegionId, "destination region id");
            requireText(landmarkProjection, "landmark projection");
            Objects.requireNonNull(documentBounds, "documentBounds");
            Objects.requireNonNull(lineBounds, "lineBounds");
            Objects.requireNonNull(destinationBounds, "destinationBounds");
            Objects.requireNonNull(previousCamera, "previousCamera");
            if (!Double.isFinite(viewportWidth) || viewportWidth <= 0
                    || !Double.isFinite(viewportHeight) || viewportHeight <= 0) {
                throw new IllegalArgumentException("viewport dimensions must be finite and positive");
            }
            if (!Double.isFinite(inset) || inset < 0) {
                throw new IllegalArgumentException("inset must be finite and non-negative");
            }
        }
    }

    public record Result(
            SFMSpatialSemanticContract.Camera camera,
            SFMSpatialSemanticContract.FramingObservation observation
    ) {
        public Result {
            Objects.requireNonNull(camera, "camera");
            Objects.requireNonNull(observation, "observation");
        }
    }

    public static Result choose(Request request) {
        Objects.requireNonNull(request, "request");
        double zoom = request.previousCamera().zoom();
        double canvasWidth = request.viewportWidth() / zoom;
        double canvasHeight = request.viewportHeight() / zoom;
        double insetX = Math.min(request.inset() / zoom, canvasWidth / 3.0D);
        double insetY = Math.min(request.inset() / zoom, canvasHeight / 3.0D);

        var document = request.documentBounds();
        var line = request.lineBounds();
        var destination = request.destinationBounds();

        double desiredLeft = Math.min(document.left(), line.left());
        double desiredRight = Math.max(destination.right(), destination.left());
        boolean leftAndDestinationFit = desiredRight - desiredLeft + insetX * 2.0D <= canvasWidth + EPSILON;

        double viewportLeft;
        String clippingReason = null;
        if (leftAndDestinationFit) {
            viewportLeft = desiredLeft - insetX;
        } else if (destination.right() - destination.left() + insetX * 2.0D <= canvasWidth + EPSILON) {
            // Put the complete destination against the right inset.  This
            // maximizes deterministic leading context on long lines.
            viewportLeft = destination.right() - (canvasWidth - insetX);
            clippingReason = "line-left-does-not-fit";
        } else {
            // The destination itself is wider than the viewport.  Preserve its
            // start landmark and report the unavoidable clipping explicitly.
            viewportLeft = destination.left() - insetX;
            clippingReason = "destination-wider-than-viewport";
        }

        boolean firstLine = line.top() <= document.top() + EPSILON;
        double destinationHeight = destination.bottom() - destination.top();
        double viewportTop;
        if (firstLine) {
            viewportTop = document.top() - insetY;
        } else if (destinationHeight + insetY * 2.0D <= canvasHeight + EPSILON) {
            double upperQuarter = canvasHeight * 0.25D;
            double landmarkOffset = Math.max(insetY, Math.min(upperQuarter, canvasHeight - destinationHeight - insetY));
            viewportTop = destination.top() - landmarkOffset;
        } else {
            viewportTop = destination.top() - insetY;
            clippingReason = clippingReason == null
                    ? "destination-taller-than-viewport"
                    : clippingReason + "+destination-taller-than-viewport";
        }

        var viewport = new SFMSpatialSemanticContract.Rectangle(
                viewportLeft,
                viewportTop,
                viewportLeft + canvasWidth,
                viewportTop + canvasHeight
        );
        var chosen = new SFMSpatialSemanticContract.Camera(
                viewportLeft + canvasWidth / 2.0D,
                viewportTop + canvasHeight / 2.0D,
                zoom
        );
        boolean documentLeftVisible = containsX(viewport, document.left());
        boolean lineLeftVisible = containsX(viewport, line.left());
        boolean documentTopVisible = containsY(viewport, document.top());
        boolean landmarkVisible = contains(viewport, destination.left(), destination.top());
        if (!landmarkVisible && clippingReason == null) clippingReason = "landmark-outside-viewport";

        var observation = new SFMSpatialSemanticContract.FramingObservation(
                SFMSpatialSemanticContract.FRAMING_SCHEMA,
                request.paneId(),
                request.documentAddress(),
                request.documentHash(),
                request.destinationRegionId(),
                request.landmarkProjection(),
                document,
                line,
                destination,
                viewport,
                request.inset(),
                request.previousCamera(),
                chosen,
                intersection(document, viewport),
                documentLeftVisible,
                lineLeftVisible,
                documentTopVisible,
                landmarkVisible,
                clippingReason
        );
        return new Result(chosen, observation);
    }

    private static boolean containsX(SFMSpatialSemanticContract.Rectangle rectangle, double x) {
        return x >= rectangle.left() - EPSILON && x <= rectangle.right() + EPSILON;
    }

    private static boolean containsY(SFMSpatialSemanticContract.Rectangle rectangle, double y) {
        return y >= rectangle.top() - EPSILON && y <= rectangle.bottom() + EPSILON;
    }

    private static boolean contains(SFMSpatialSemanticContract.Rectangle rectangle, double x, double y) {
        return containsX(rectangle, x) && containsY(rectangle, y);
    }

    private static SFMSpatialSemanticContract.Rectangle intersection(
            SFMSpatialSemanticContract.Rectangle first,
            SFMSpatialSemanticContract.Rectangle second
    ) {
        double left = Math.max(first.left(), second.left());
        double top = Math.max(first.top(), second.top());
        double right = Math.max(left, Math.min(first.right(), second.right()));
        double bottom = Math.max(top, Math.min(first.bottom(), second.bottom()));
        return new SFMSpatialSemanticContract.Rectangle(left, top, right, bottom);
    }

    private static void requireText(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " must not be blank");
    }
}
