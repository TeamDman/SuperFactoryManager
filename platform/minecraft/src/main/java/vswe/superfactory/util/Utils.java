package vswe.superfactory.util;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;

import java.io.*;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class Utils {
	private static final Pattern patternControlCode = Pattern.compile("(?i)\\u00A7[0-9A-FK-OR]");

	public static String stripControlCodes(String s) {
		return patternControlCode.matcher(s).replaceAll("");
	}

	public static NBTTagCompound readCompressed(byte[] bytes, NBTSizeTracker sizeTracker) throws IOException {
		DataInputStream datainputstream = new DataInputStream(new BufferedInputStream(new GZIPInputStream(new ByteArrayInputStream(bytes))));
		NBTTagCompound  nbttagcompound;

		try {
			nbttagcompound = CompressedStreamTools.read(datainputstream, sizeTracker);
		} finally {
			datainputstream.close();
		}

		return nbttagcompound;
	}

	public static byte[] compress(NBTTagCompound tagCompound) throws IOException {
		ByteArrayOutputStream bytearrayoutputstream = new ByteArrayOutputStream();
		DataOutputStream      dataoutputstream      = new DataOutputStream(new GZIPOutputStream(bytearrayoutputstream));

		try {
			CompressedStreamTools.write(tagCompound, dataoutputstream);
		} finally {
			dataoutputstream.close();
		}

		return bytearrayoutputstream.toByteArray();
	}
}
