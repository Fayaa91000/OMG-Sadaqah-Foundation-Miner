package org.openmininggateway.miner;

public final class SadaqahSchedule {
    private SadaqahSchedule() {}

    public static final int USER = 0;
    public static final int GAZA = 1;
    public static final int COMPLETE = 2;

    public static int phase(long elapsedMs) {
        if (elapsedMs < SadaqahConfig.USER_MINING_MS) {
            return USER;
        }
        if (elapsedMs < SadaqahConfig.TOTAL_SESSION_MS) {
            return GAZA;
        }
        return COMPLETE;
    }

    public static long remaining(long elapsedMs) {
        return Math.max(0L,
            SadaqahConfig.TOTAL_SESSION_MS - elapsedMs);
    }
}
