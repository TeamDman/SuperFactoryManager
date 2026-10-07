package ca.teamdman.sfm.client.explorer;

/** A stable, machine-inspectable failure while parsing explorer contract text. */
public final class SFMParseException extends IllegalArgumentException {
    private final String code;
    private final int offset;

    public SFMParseException(String code, String message, int offset) {
        super(message);
        this.code = code;
        this.offset = offset;
    }

    public String code() {
        return code;
    }

    public int offset() {
        return offset;
    }
}
