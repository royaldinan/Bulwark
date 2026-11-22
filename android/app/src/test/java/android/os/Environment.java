package android.os;

import java.io.File;

/** Host-side test double for the SDK stub, which cannot run in local unit tests. */
public class Environment {
    public static final String DIRECTORY_DOWNLOADS = "Download";

    private static File externalStorageDirectory;
    private static File publicDownloadsDirectory;

    public static File getExternalStorageDirectory() {
        return externalStorageDirectory;
    }

    public static File getExternalStoragePublicDirectory(String type) {
        if (!DIRECTORY_DOWNLOADS.equals(type)) {
            throw new IllegalArgumentException(type);
        }
        return publicDownloadsDirectory;
    }

    public static void setTestDirectories(File root, File downloads) {
        externalStorageDirectory = root;
        publicDownloadsDirectory = downloads;
    }

    public static void clearTestDirectories() {
        externalStorageDirectory = null;
        publicDownloadsDirectory = null;
    }
}
