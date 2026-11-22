package android.os;

import java.util.ArrayList;
import java.util.List;

/** Host-side test double for the SDK stub, which cannot run in local unit tests. */
public abstract class FileObserver {
    public static final int CLOSE_WRITE = 0x00000008;
    public static final int MOVED_TO = 0x00000080;

    private static final List<FileObserver> STARTED = new ArrayList<>();

    private final String path;
    private final int mask;

    public FileObserver(String path, int mask) {
        this.path = path;
        this.mask = mask;
    }

    public abstract void onEvent(int event, String path);

    public void startWatching() {
        STARTED.add(this);
    }

    public void stopWatching() {
        STARTED.remove(this);
    }

    public String getTestPath() {
        return path;
    }

    public int getTestMask() {
        return mask;
    }

    public static List<FileObserver> getStartedTestWatchers() {
        return new ArrayList<>(STARTED);
    }

    public static void clearTestWatchers() {
        STARTED.clear();
    }
}
