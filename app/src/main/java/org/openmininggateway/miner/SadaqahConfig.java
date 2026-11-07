package org.openmininggateway.miner;

public final class SadaqahConfig {
    private SadaqahConfig() {}

    public static final String GAZA_WALLET =
        "8BdgKdMQPgJRa9tsfcZrGHEWRfPinexXVRjcRmtQYHMyGGgDXZ3hueyZ18zhXEuquBGG73kugNmnYFVXca8Ut3LfKDkYJDg";

    public static final long USER_MINING_MS = 2L * 60 * 1000;
    public static final long GAZA_MINING_MS = 3L * 60 * 1000;
    public static final long TOTAL_SESSION_MS =
        USER_MINING_MS + GAZA_MINING_MS;
}
