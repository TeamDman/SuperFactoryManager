package ca.teamdman.sfm.client.theme.preview;

import ca.teamdman.sfm.client.presentation.SFMItemIcon;
import java.util.*;
import static ca.teamdman.sfm.client.theme.preview.SFMItemstackPreviewExpression.*;
import static ca.teamdman.sfm.client.theme.preview.SFMItemstackPreviewRules.*;

/** Semantic defaults, not numeric-priority overrides. Every name rule includes its kind. */
public final class SFMItemstackPreviewDefaultNames {
    public static final List<Rule> RULES = create();
    private SFMItemstackPreviewDefaultNames() {}
    private static List<Rule> create() {
        var rules=new ArrayList<Rule>();
        directories(rules,"grass_block","Minecraft platform","minecraft");
        directories(rules,"book","Documentation","docs","documents","doc");
        directories(rules,"scaffolding","Architecture and platforms","architecture","platform");
        directories(rules,"crafting_table","Source inputs","src","source","sources");
        directories(rules,"target","Tests and fixtures","test","tests","gametest","fixtures","scenarios");
        directories(rules,"furnace","Build outputs","build","out","dist","generated","generated-src");
        directories(rules,"blast_furnace","Compiled targets","target","classes");
        directories(rules,"spyglass","Debugging and diagnosis","debug","diagnostics","crash-reports");
        directories(rules,"firework_rocket","Release outputs","release","releases");
        directories(rules,"command_block","Commands and terminals","cli","command","commands","terminal","scripts","computercraft");
        directories(rules,"lever","Actions and handlers","action","actions","handler","handlers");
        directories(rules,"compass","Exploration and navigation","explorer","navigation");
        directories(rules,"bell","Code review","review","reviews","release_review");
        directories(rules,"writable_book","Editing and logs","text_editor","logging","logs");
        directories(rules,"repeater","Configuration","config","configs","defaultconfigs","settings");
        directories(rules,"knowledge_book","Registries and recipes","registry","registries","recipes");
        directories(rules,"enchanting_table","Language processing","syntax","parser","grammar","grammars");
        directories(rules,"painting","Visual resources","assets","textures","screenshots","images","screen","screens");
        directories(rules,"bookshelf","Libraries and dependencies","lib","libs","deps","dependencies","node_modules");
        directories(rules,"honeycomb","Reusable caches","cache","caches",".cache",".fingerprint");
        directories(rules,"redstone","Data and serialization","data","serialization");
        directories(rules,"piston","Compatibility adapters","compat","adapters","mixins");
        directories(rules,"stone_bricks","Blocks and networks","block","blocks","block_network");
        directories(rules,"item_frame","Items","item","items");
        directories(rules,"map","Workspace organization","workspace","workspaces");
        directories(rules,"cocoa_beans","Java packages","java");
        directories(rules,"iron_ingot","Rust source","rust");
        directories(rules,"nether_star","Primary application code","main","common");
        directories(rules,"daylight_detector","Client presentation","client");
        directories(rules,"observer","Server processing","server");
        directories(rules,"comparator","Automation workflows",".github","workflows");
        file(rules,".gitignore","barrier","Ignored paths");
        file(rules,".gitattributes","name_tag","Git file attributes");
        file(rules,"LICENSE","writable_book","License terms");
        file(rules,"LICENSE.txt","writable_book","License terms");
        file(rules,"README.md","lectern","Project introduction");
        file(rules,"AGENTS.md","written_book","Agent instructions");
        return List.copyOf(rules);
    }
    private static void directories(List<Rule> rules,String item,String meaning,String... names) {
        for (String name:names) rules.add(new Rule("sfm:default-name/"+name,Layer.DEFAULT,
                call("sfm:bool/and",call("sfm:entry/is_container"),equalName(name)),new SFMItemIcon(
                        new net.minecraft.resources.ResourceLocation("minecraft",item),
                        new net.minecraft.resources.ResourceLocation("minecraft","barrel"),meaning)));
    }
    private static SFMItemstackPreviewExpression equalName(String name) {
        return call("sfm:string/equals",call("sfm:entry/name"),literal(name));
    }
    private static void file(List<Rule> rules,String name,String item,String meaning) {
        int dot=name.lastIndexOf('.');
        var specificity=dot<=0 ? call("sfm:entry/is_extensionless") : call("sfm:entry/has_suffix",literal(name.substring(dot)));
        rules.add(new Rule("sfm:default-file-name/"+name.toLowerCase(Locale.ROOT),Layer.DEFAULT,
                call("sfm:bool/and",call("sfm:entry/is_file"),call("sfm:bool/and",specificity,equalName(name))),
                SFMItemIcon.vanilla(item,meaning)));
    }
}
