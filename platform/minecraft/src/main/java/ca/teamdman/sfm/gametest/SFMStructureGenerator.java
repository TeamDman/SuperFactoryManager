package ca.teamdman.sfm.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static java.util.regex.Pattern.compile;

public class SFMStructureGenerator {
    public static Optional<StructureTemplate> generateStructureTemplate(ResourceLocation id) {
        StructureTemplate template = new StructureTemplate();
        template.setAuthor("TeamDman");
        template.size = extractSizeFromTemplateId(id);

        List<StructureTemplate.StructureBlockInfo> infos = new ArrayList<>();
        for (int x = 0; x < template.size.getX(); x++) {
            int y = 0;
            for (int z = 0; z < template.size.getZ(); z++) {
                BlockPos pos = new BlockPos(x, y, z);
                StructureTemplate.StructureBlockInfo blockInfo = new StructureTemplate.StructureBlockInfo(
                        pos,
                        Blocks.POLISHED_ANDESITE.defaultBlockState(),
                        null
                );
                infos.add(blockInfo);
            }
        }
        template.palettes.add(new StructureTemplate.Palette(infos));
        return Optional.of(template);
    }

    private static Vec3i extractSizeFromTemplateId(
            ResourceLocation id
    ) {
        int x = 1;
        int y = 1;
        int z = 1;
        // "sfm:sometest.1x3x4"
        // "sfm:1x3x4"
        var path = id.getPath();
        if (path.contains(".")) {
            path = path.split("\\.")[1];
        }
        var regex = "([0-9]+)x([0-9]+)x([0-9]+)";
        var matcher = compile(regex).matcher(path);
        if (matcher.find()) {
            x = Integer.parseInt(matcher.group(1));
            y = Integer.parseInt(matcher.group(2));
            z = Integer.parseInt(matcher.group(3));
        }
        return new Vec3i(x, y, z);
    }
}
