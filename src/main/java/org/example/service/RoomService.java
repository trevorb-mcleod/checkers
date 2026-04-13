package org.example.service;

import org.example.model.Room;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RoomService {

    private final Map<String, Room>   rooms         = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToCode = new ConcurrentHashMap<>();
    private final Random              rng           = new Random();

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Create a new room for the given session.
     * Modes: "LOCAL", "CPU", "ONLINE".
     */
    public Room createRoom(String sessionId, String mode, String gameType) {
        String oldCode = sessionToCode.remove(sessionId);
        if (oldCode != null) {
            Room old = rooms.get(oldCode);
            if (old != null && sessionId.equals(old.getRedSessionId())) rooms.remove(oldCode);
        }
        String code = generateCode();
        Room room   = new Room(code, mode, gameType, sessionId);
        rooms.put(code, room);
        sessionToCode.put(sessionId, code);
        return room;
    }

    /** Back-compat overload — defaults to CHECKERS. */
    public Room createRoom(String sessionId, String mode) {
        return createRoom(sessionId, mode, "CHECKERS");
    }

    /**
     * Join an existing ONLINE room identified by its 4-digit code.
     * Returns null if the room doesn't exist or isn't accepting players.
     */
    public Room joinRoom(String sessionId, String code) {
        Room room = rooms.get(code);
        if (room == null)                       return null;   // not found
        if (!"ONLINE".equals(room.getMode()))   return null;   // wrong type
        if (!room.isWaitingForOpponent()) {
            // If this session is already in the room, let them back in
            if (sessionId.equals(room.getRedSessionId())
                    || sessionId.equals(room.getBlackSessionId())) {
                sessionToCode.put(sessionId, code);
                return room;
            }
            return null;  // full
        }

        room.setBlackSessionId(sessionId);
        sessionToCode.put(sessionId, code);
        room.touch();
        return room;
    }

    /** Return the current room for this session, or null. */
    public Room getRoom(String sessionId) {
        String code = sessionToCode.get(sessionId);
        return code != null ? rooms.get(code) : null;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String generateCode() {
        String code;
        do { code = String.format("%04d", rng.nextInt(10000)); }
        while (rooms.containsKey(code));
        return code;
    }

    /** Remove rooms that have been inactive for more than 2 hours. */
    @Scheduled(fixedDelay = 15 * 60 * 1000)   // runs every 15 minutes
    public void evictStaleRooms() {
        long cutoff = System.currentTimeMillis() - 2 * 60 * 60 * 1000L;
        rooms.entrySet().removeIf(e -> e.getValue().getLastActivity() < cutoff);
        sessionToCode.entrySet().removeIf(e -> !rooms.containsKey(e.getValue()));
    }
}

