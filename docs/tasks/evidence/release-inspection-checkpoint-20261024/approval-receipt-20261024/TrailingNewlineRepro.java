import ca.teamdman.sfm.client.text_editor.TextEditContext;
import ca.teamdman.sfm.client.screen.SFMDrawCanvasModel;

/** Reproduces existing losses; does not patch production behavior. */
public final class TrailingNewlineRepro {
    public static void main(String[] args) {
        String input = "x\n";
        String v2 = new TextEditContext(input).getContent();
        SFMDrawCanvasModel canvas = new SFMDrawCanvasModel();
        canvas.replaceText(input, glyph -> 1, 9);
        String v3 = canvas.documentIndex(1, 9).projection().text();
        if (!v2.equals("x") || !v3.equals("x"))
            throw new AssertionError("Observed behavior changed; expected both losses");
        System.out.println("V2: input=x\\n (2 chars), output=x (1 char)");
        System.out.println("V3 model/index: input=x\\n (2 chars), output=x (1 char)");
        System.out.println("Both pre-existing trailing-newline losses reproduced.");
    }
}
