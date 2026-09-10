package ca.teamdman.sfm.client.screen.theme_settings;

import ca.teamdman.sfm.client.presentation.SFMItemIcon;
import ca.teamdman.sfm.client.theme.SFMClientTheme;
import ca.teamdman.sfm.client.theme.SFMColourRole;
import ca.teamdman.sfm.client.theme.SFMSyntaxStyle;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure structured draft over the same immutable theme snapshot consumed by runtime rendering. */
public final class SFMThemeSettingsModel {
    public static final ResourceLocation PALETTE_ACTION = new ResourceLocation("sfm:palette/open");
    private SFMClientTheme baseline;
    private SFMClientTheme draft;
    private List<SFMThemeProperty> properties;
    private int selectedIndex;
    private String status = "Select a property and press Enter to edit";

    public SFMThemeSettingsModel(SFMClientTheme theme) {
        baseline = normalize(theme);
        draft = baseline;
        properties = buildProperties(draft);
    }

    public SFMClientTheme draft() { return draft; }
    public List<SFMThemeProperty> properties() { return properties; }
    public int selectedIndex() { return selectedIndex; }
    public SFMThemeProperty selected() { return properties.get(selectedIndex); }
    public String status() { return status; }
    public boolean dirty() { return !draft.equals(baseline); }

    public void select(int index) {
        if (index < 0 || index >= properties.size()) return;
        selectedIndex = index;
        status = "Selected " + selected().label();
    }

    public void move(int delta) { select(Math.max(0, Math.min(properties.size() - 1, selectedIndex + delta))); }

    public void select(SFMThemeProperty.Kind kind, String id) {
        for (int index = 0; index < properties.size(); index++) {
            SFMThemeProperty property = properties.get(index);
            if (property.kind() == kind && property.id().equals(id)) {
                select(index);
                return;
            }
        }
        throw new IllegalArgumentException("Unknown theme property " + kind + " " + id);
    }

    public int selectedColour() {
        SFMThemeProperty property = selected();
        return switch (property.kind()) {
            case COLOUR -> draft.colour(SFMColourRole.byId(property.id()).orElseThrow());
            case SYNTAX -> draft.syntax(property.id()).colour();
            default -> throw new IllegalStateException("Selected property is not colour-backed: " + property.id());
        };
    }

    public SFMItemIcon selectedIcon() {
        SFMThemeProperty property = selected();
        return switch (property.kind()) {
            case FILE_ICON -> draft.fileIcons().get(property.id());
            case ACTION_ICON -> draft.actionIcons().get(new ResourceLocation(property.id()));
            default -> throw new IllegalStateException("Selected property is not icon-backed: " + property.id());
        };
    }

    public void setSelectedColour(int argb) {
        SFMThemeProperty property = selected();
        if (property.kind() == SFMThemeProperty.Kind.COLOUR) {
            EnumMap<SFMColourRole, Integer> colours = new EnumMap<>(SFMColourRole.class);
            colours.putAll(draft.colours());
            colours.put(SFMColourRole.byId(property.id()).orElseThrow(), argb);
            draft = new SFMClientTheme(colours, draft.sfmlSyntax(), draft.fileIcons(), draft.actionIcons(), draft.previewRules(), draft.explicitFileIcons());
        } else if (property.kind() == SFMThemeProperty.Kind.SYNTAX) {
            Map<String, SFMSyntaxStyle> syntax = new LinkedHashMap<>(draft.sfmlSyntax());
            SFMSyntaxStyle old = syntax.get(property.id());
            syntax.put(property.id(), new SFMSyntaxStyle(argb, old.bold(), old.italic(), old.underlined()));
            draft = new SFMClientTheme(draft.colours(), syntax, draft.fileIcons(), draft.actionIcons(), draft.previewRules(), draft.explicitFileIcons());
        } else {
            throw new IllegalStateException("Selected property is not colour-backed");
        }
        status = "Previewing unsaved " + property.label();
    }

    public void setSelectedIcon(SFMItemIcon icon) {
        SFMThemeProperty property = selected();
        if (property.kind() == SFMThemeProperty.Kind.FILE_ICON) {
            Map<String, SFMItemIcon> icons = new LinkedHashMap<>(draft.fileIcons());
            icons.put(property.id(), icon);
            var explicit = new java.util.HashSet<>(draft.explicitFileIcons());
            explicit.add(property.id());
            draft = new SFMClientTheme(draft.colours(), draft.sfmlSyntax(), icons, draft.actionIcons(), draft.previewRules(), explicit);
        } else if (property.kind() == SFMThemeProperty.Kind.ACTION_ICON) {
            Map<ResourceLocation, SFMItemIcon> icons = new LinkedHashMap<>(draft.actionIcons());
            icons.put(new ResourceLocation(property.id()), icon);
            draft = new SFMClientTheme(draft.colours(), draft.sfmlSyntax(), draft.fileIcons(), icons, draft.previewRules(), draft.explicitFileIcons());
        } else {
            throw new IllegalStateException("Selected property is not icon-backed");
        }
        status = "Previewing unsaved " + property.label();
    }

    public void resetSelected() {
        SFMThemeProperty property = selected();
        SFMClientTheme oldDraft = draft;
        draft = baseline;
        if (property.kind() == SFMThemeProperty.Kind.COLOUR || property.kind() == SFMThemeProperty.Kind.SYNTAX) {
            int value = selectedColour();
            draft = oldDraft;
            setSelectedColour(value);
        } else {
            SFMItemIcon value = selectedIcon();
            draft = oldDraft;
            setSelectedIcon(value);
            if (property.kind() == SFMThemeProperty.Kind.FILE_ICON && !baseline.explicitFileIcons().contains(property.id())) {
                var explicit = new java.util.HashSet<>(draft.explicitFileIcons());
                explicit.remove(property.id());
                draft = new SFMClientTheme(draft.colours(), draft.sfmlSyntax(), draft.fileIcons(), draft.actionIcons(), draft.previewRules(), explicit);
            }
        }
        status = "Reset " + property.label() + " to the loaded value";
    }

    public void restoreDefaults() {
        draft = normalize(SFMClientTheme.defaults());
        properties = buildProperties(draft);
        selectedIndex = Math.min(selectedIndex, properties.size() - 1);
        status = "Previewing shipped defaults (unsaved)";
    }

    public void markSaved(SFMClientTheme saved) {
        baseline = normalize(saved);
        draft = baseline;
        properties = buildProperties(draft);
        selectedIndex = Math.min(selectedIndex, properties.size() - 1);
        status = "Saved and reloaded atomically";
    }

    public void setStatus(String status) { this.status = status; }

    public String selectedValue() {
        SFMThemeProperty property = selected();
        return switch (property.kind()) {
            case COLOUR -> String.format("#%08X", selectedColour());
            case SYNTAX -> {
                SFMSyntaxStyle style = draft.syntax(property.id());
                yield String.format("#%08X%s%s%s", style.colour(), style.bold() ? " bold" : "",
                        style.italic() ? " italic" : "", style.underlined() ? " underline" : "");
            }
            case FILE_ICON, ACTION_ICON -> selectedIcon().requestedItem().toString();
        };
    }

    private static SFMClientTheme normalize(SFMClientTheme theme) {
        Map<ResourceLocation, SFMItemIcon> actions = new LinkedHashMap<>(theme.actionIcons());
        actions.putIfAbsent(PALETTE_ACTION, SFMItemIcon.vanilla("compass", "Open command palette"));
        return new SFMClientTheme(theme.colours(), theme.sfmlSyntax(), theme.fileIcons(), actions, theme.previewRules(), theme.explicitFileIcons());
    }

    private static List<SFMThemeProperty> buildProperties(SFMClientTheme theme) {
        List<SFMThemeProperty> answer = new ArrayList<>();
        for (SFMColourRole role : SFMColourRole.values()) {
            answer.add(new SFMThemeProperty(SFMThemeProperty.Kind.COLOUR, role.id(), "Colour · " + role.id()));
        }
        theme.sfmlSyntax().keySet().stream().sorted().forEach(id ->
                answer.add(new SFMThemeProperty(SFMThemeProperty.Kind.SYNTAX, id, "Syntax · " + id)));
        theme.fileIcons().keySet().stream().sorted().forEach(id ->
                answer.add(new SFMThemeProperty(SFMThemeProperty.Kind.FILE_ICON, id, "File icon · " + id)));
        theme.actionIcons().keySet().stream().map(ResourceLocation::toString).sorted().forEach(id ->
                answer.add(new SFMThemeProperty(SFMThemeProperty.Kind.ACTION_ICON, id, "Action icon · " + id)));
        return List.copyOf(answer);
    }
}
