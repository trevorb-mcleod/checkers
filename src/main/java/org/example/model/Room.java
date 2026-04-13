package org.example.model;

import org.example.service.BattleshipGameService;
import org.example.service.CheckersGameService;
import org.example.service.Connect4GameService;

/**
 * Represents one game session.  Modes: "LOCAL", "CPU", "ONLINE".
 * GameTypes: "CHECKERS", "BATTLESHIP".
 */
public class Room {

    private final String code;
    private final String mode;
    private final String gameType;
    private final String redSessionId;
    private       String blackSessionId;
    private final CheckersGameService   checkersGame;
    private final BattleshipGameService battleshipGame;
    private final Connect4GameService   connect4Game;
    private long lastActivity;

    public Room(String code, String mode, String gameType, String redSessionId) {
        this.code           = code;
        this.mode           = mode;
        this.gameType       = gameType != null ? gameType : "CHECKERS";
        this.redSessionId   = redSessionId;
        this.blackSessionId = "CPU".equals(mode) ? "CPU" : null;
        this.checkersGame   = "CHECKERS" .equals(this.gameType) ? new CheckersGameService()   : null;
        this.battleshipGame = "BATTLESHIP".equals(this.gameType) ? new BattleshipGameService() : null;
        this.connect4Game   = "CONNECT4"  .equals(this.gameType) ? new Connect4GameService()   : null;
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
    public String                getGameType()       { return gameType; }
    public String                getRedSessionId()   { return redSessionId; }
    public String                getBlackSessionId() { return blackSessionId; }
    public void                  setBlackSessionId(String id) { this.blackSessionId = id; }
    public CheckersGameService   getGame()           { return checkersGame; }
    public BattleshipGameService getBattleshipGame() { return battleshipGame; }
    public Connect4GameService   getConnect4Game()   { return connect4Game; }
    public long                  getLastActivity()   { return lastActivity; }
}
