package ca.teamdman.sfm.gametest.tests.general;

import ca.teamdman.sfm.common.item.PacketItem;
import ca.teamdman.sfm.common.util.SFMItemUtils;
import ca.teamdman.sfm.common.value.SFMValue;
import ca.teamdman.sfm.common.value.SFMValueJsonCodec;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.LinkedHashMap;
import java.util.Map;

@SFMGameTest
public class PacketItemGameTest extends SFMGameTestDefinition {
    @Override
    public String template() {
        return "1x1x1";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        LinkedHashMap<String, SFMValue> reverseOrder = new LinkedHashMap<>();
        reverseOrder.put("type", SFMValue.of("Response"));
        reverseOrder.put("JobId", SFMValue.of("same"));

        ItemStack first = PacketItem.create(SFMValue.object(reverseOrder));
        first.setCount(32);
        ItemStack equal = PacketItem.create(SFMValue.object(Map.of(
                "JobId", SFMValue.of("same"),
                "type", SFMValue.of("Response")
        )));
        equal.setCount(16);
        ItemStack different = PacketItem.create(SFMValue.object(Map.of(
                "JobId", SFMValue.of("different"),
                "type", SFMValue.of("Response")
        )));

        helper.assertTrue(first.getMaxStackSize() == 64, "Packet item must stack to 64");
        helper.assertTrue(
                SFMItemUtils.isSameItemSameTags(first, equal),
                "Canonical-equivalent packet payloads must stack"
        );
        helper.assertTrue(
                !SFMItemUtils.isSameItemSameTags(first, different),
                "Different packet payloads must not stack"
        );
        helper.assertTrue(
                PacketItem.getValue(first).orElseThrow().equals(SFMValue.object(reverseOrder)),
                "Packet item must round-trip a copied value"
        );
        helper.assertTrue(
                first.getOrCreateTag().getInt(PacketItem.CODEC_VERSION_TAG) == SFMValueJsonCodec.VERSION,
                "Packet item must persist the codec version"
        );
        helper.assertTrue(
                first.getOrCreateTag().getString(PacketItem.VALUE_JSON_TAG)
                        .equals("{\"JobId\":\"same\",\"type\":\"Response\"}"),
                "Packet item must persist canonical JSON"
        );

        ItemStack blank = new ItemStack(first.getItem());
        helper.assertTrue(!blank.hasTag(), "Blank packet must begin without NBT");
        helper.assertTrue(PacketItem.getValue(blank).isEmpty(), "Blank packet must not decode a value");
        helper.assertTrue(!blank.hasTag(), "Reading a blank packet must not create NBT");

        ItemStack old = new ItemStack(first.getItem());
        old.getOrCreateTag().putInt(PacketItem.CODEC_VERSION_TAG, 1);
        old.getOrCreateTag().putString(PacketItem.VALUE_JSON_TAG, "{\"count\":9007199254740993}");
        helper.assertTrue(
                PacketItem.getValue(old).orElseThrow().equals(SFMValue.object(Map.of(
                        "count", SFMValue.of(9_007_199_254_740_993L)
                ))),
                "Version 1 packet items must retain exact integer values"
        );
        helper.assertTrue(
                old.getOrCreateTag().getInt(PacketItem.CODEC_VERSION_TAG) == 1,
                "Reading an old packet must not rewrite its stored version"
        );

        ItemStack floating = PacketItem.create(SFMValue.of(0.5));
        helper.assertTrue(
                floating.getOrCreateTag().getInt(PacketItem.CODEC_VERSION_TAG) == 2
                && PacketItem.getValue(floating).orElseThrow().equals(SFMValue.of(0.5)),
                "Version 2 packet items must carry finite floating values"
        );

        ItemStack malformed = new ItemStack(first.getItem());
        malformed.getOrCreateTag().putInt(PacketItem.CODEC_VERSION_TAG, SFMValueJsonCodec.VERSION);
        malformed.getOrCreateTag().putString(PacketItem.VALUE_JSON_TAG, "{");
        helper.assertTrue(PacketItem.getValue(malformed).isEmpty(), "Malformed packet data must fail closed");

        ItemStack unsupported = new ItemStack(first.getItem());
        unsupported.getOrCreateTag().putInt(PacketItem.CODEC_VERSION_TAG, SFMValueJsonCodec.VERSION + 1);
        unsupported.getOrCreateTag().putString(PacketItem.VALUE_JSON_TAG, "null");
        helper.assertTrue(PacketItem.getValue(unsupported).isEmpty(), "Unknown codec versions must fail closed");

        ItemStack paper = new ItemStack(Items.PAPER);
        boolean rejectedNonPacket = false;
        try {
            PacketItem.setValue(paper, SFMValue.nullValue());
        } catch (IllegalArgumentException expected) {
            rejectedNonPacket = true;
        }
        helper.assertTrue(rejectedNonPacket, "Packet data writes must reject non-packet items");
        helper.assertTrue(PacketItem.getValue(paper).isEmpty(), "Non-packet items must not decode packet data");

        helper.succeed();
    }
}
