package vswe.superfactory.network.packets;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public final class FileHelper {
	private static File dir;

	private FileHelper() {
	}

	public static void setConfigDir(File dir) {
		FileHelper.dir = dir;
	}

	/**
	 * Remember to close the readers given from this method
	 *
	 * @param name
	 * @return A reader that has to be closed after usage, might be null if reading failed
	 */
	public static DataReader read(String name) {
		try {
			File file = new File(dir, name + ".dat");
			if (file.exists()) {
				return new DataReader(new FileInputStream(file));
			} else {
				return null;
			}
		} catch (IOException ignored) {
			return null;
		}
	}

	/**
	 * Remember to close the writers given from this method
	 *
	 * @param name
	 * @return A writer that has to be closed after usage, might be null if writing failed
	 */
	public static DataWriter getWriter(String name) {
		try {
			File file = new File(dir, name + ".dat");
			if (file.exists()) {
				//file.delete()
			}
			return new DataWriter(new FileOutputStream(file));
		} catch (IOException ignored) {
			return null;
		}
	}

	public static void close(DataWriter dw) {
		dw.writeFinalBits();
		dw.close();
	}
}
