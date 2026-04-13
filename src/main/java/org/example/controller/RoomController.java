package org.example.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.example.model.GameState;
import org.example.model.Room;
import org.example.service.CheckersGameService;
import org.example.service.CpuPlayer;
import org.example.service.RoomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Map;

/**
 * Room-based game endpoints.  All operations are session-scoped —
 * the JSESSIONID cookie maps each browser to its room automatically.
 */
@RestController
@RequestMapping("/api/room")
public class RoomController {

    @Autowired private RoomService    roomService;
    @Autowired private CpuPlayer      cpuPlayer;

    // ── Room lifecycle ────────────────────────────────────────────────────────

    /**
     * Returns the best URL others can use to reach this server.
     * Prefers a non-loopback LAN IP so the invite link works across devices.
     */
    @GetMapping("/host-url")
    public Map<String, String> hostUrl(HttpServletRequest request) {
        int port = request.getServerPort();
        String ip = detectLanIp();
        return Map.of("url", "http://" + ip + ":" + port);
    }

    /** Walk network interfaces to find the first non-loopback IPv4 address. */
    private String detectLanIp() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                    if (!addr.isLoopbackAddress() && addr.getAddress().length == 4) {
                        return addr.getHostAddress();   // first real IPv4 wins
                    }
                }
            }
        } catch (Exception ignored) {}
        try { return InetAddress.getLocalHost().getHostAddress(); }
        catch (Exception ignored) {}
        return "localhost";
    }

    /**
     * Create a new room.
     * Body: {"mode": "LOCAL" | "CPU" | "ONLINE"}
     */
    @PostMapping("/create")
    public GameState create(@RequestBody Map<String, String> body, HttpSession session) {
        String mode = body.getOrDefault("mode", "LOCAL");
        Room room   = roomService.createRoom(session.getId(), mode);
        return buildResponse(room, session.getId());
    }

    /**
     * Join an existing ONLINE room by 4-digit code.
     * Body: {"code": "1234"}
     */
    @PostMapping("/join")
    public ResponseEntity<GameState> join(@RequestBody Map<String, String> body, HttpSession session) {
        String code = body.get("code");
        Room room   = roomService.joinRoom(session.getId(), code);
        if (room == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(buildResponse(room, session.getId()));
    }

    // ── In-game actions ───────────────────────────────────────────────────────

    /** Return the current state for this session's room. */
    @GetMapping("/state")
    public GameState state(HttpSession session) {
        Room room = roomService.getRoom(session.getId());
        if (room == null) return new GameState();   // no room → frontend shows mode-select
        return buildResponse(room, session.getId());
    }

    /** Reset the game inside the current room. */
    @PostMapping("/new")
    public GameState newGame(HttpSession session) {
        Room room = roomService.getRoom(session.getId());
        if (room == null) return new GameState();
        room.getGame().newGame();
        room.touch();
        return buildResponse(room, session.getId());
    }

    /** Handle a board click. */
    @PostMapping("/click")
    public GameState click(@RequestBody Map<String, Integer> body, HttpSession session) {
        Room room = roomService.getRoom(session.getId());
        if (room == null) return new GameState();

        String playerRole = room.getPlayerRole(session.getId());
        CheckersGameService game = room.getGame();

        // In online mode, ignore clicks when it's not the caller's turn
        if ("ONLINE".equals(room.getMode())
                && !"ANY".equals(playerRole)
                && !playerRole.equals(game.getCurrentPlayer())) {
            return buildResponse(room, session.getId());
        }

        game.click(body.get("row"), body.get("col"));

        // CPU mode: immediately make the AI move when it becomes BLACK's turn
        if ("CPU".equals(room.getMode())
                && "BLACK".equals(game.getCurrentPlayer())
                && "PLAYING".equals(game.getStatus())) {
            cpuPlayer.makeMove(game);
        }

        room.touch();
        return buildResponse(room, session.getId());
    }

    /** Toggle forced-jumps rule. */
    @PostMapping("/forced-jumps")
    public GameState forcedJumps(@RequestBody Map<String, Boolean> body, HttpSession session) {
        Room room = roomService.getRoom(session.getId());
        if (room == null) return new GameState();
        room.getGame().setForcedJumps(body.get("enabled"));
        return buildResponse(room, session.getId());
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private GameState buildResponse(Room room, String sessionId) {
        GameState state = room.getGame().getState();
        state.setMode(room.getMode());
        state.setRoomCode("ONLINE".equals(room.getMode()) ? room.getCode() : null);
        state.setPlayerRole(room.getPlayerRole(sessionId));
        state.setRoomStatus(room.isWaitingForOpponent() ? "WAITING" : "PLAYING");
        return state;
    }
}

