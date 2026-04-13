package org.example.model;

import org.example.service.CheckersGameService;

/**
 * Represents one game session.  Modes: "LOCAL", "CPU", "ONLINE".
 */
public class Room {

    private final String code;
    private final String mode;
    private final String redSessionId;      // creator
    private       String blackSessionId;    // joiner; "CPU" for CPU mode; null while waiting
    private final CheckersGameService game;
    private long lastActivity;

    public Room(String code, String mode, String redSessionId) {
        this.code           = code;
        this.mode           = mode;
        this.redSessionId   = redSessionId;
        this.blackSessionId = "CPU".equals(mode) ? "CPU" : null;
        this.game           = new CheckersGameService();
        this.lastActivity   = System.currentTimeMillis();
    }

    // ── Derived state ─────────────────────────────────────────────────────────

    /** Returns "RED", "BLACK", or "ANY" (LOCAL) for the given session. */
    public String getPlayerRole(String sessionId) {
        if ("LOCAL".equals(mode))  return "ANY";
        if ("CPU".equals(mode))    return "RED";           // human is always RED
        if (sessionId.equals(redSessionId))   return "RED";
        if (sessionId.equals(blackSessionId)) return "BLACK";
        return "SPECTATOR";
    }

    /** True when the room is ONLINE and the second player hasn't joined yet. */
    public boolean isWaitingForOpponent() {
        return "ONLINE".equals(mode) && blackSessionId == null;
    }

    public void touch() { lastActivity = System.currentTimeMillis(); }

    // ── Getters / setters ─────────────────────────────────────────────────────

    public String                getCode()           { return code; }
    public String                getMode()           { return mode; }
    public String                getRedSessionId()   { return redSessionId; }
    public String                getBlackSessionId() { return blackSessionId; }
    public void                  setBlackSessionId(String id) { this.blackSessionId = id; }
    public CheckersGameService   getGame()           { return game; }
    public long                  getLastActivity()   { return lastActivity; }
}

