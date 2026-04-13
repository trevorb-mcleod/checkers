package org.example.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.example.model.GameState;
import org.example.model.Room;
import org.example.service.BattleshipCpuPlayer;
import org.example.service.BattleshipGameService;
import org.example.service.BattleshipGameService.BattleshipSnapshot;
import org.example.service.CheckersGameService;
import org.example.service.Connect4CpuPlayer;
import org.example.service.Connect4GameService;
import org.example.service.CpuPlayer;
import org.example.service.RoomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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

    @Autowired private RoomService         roomService;
    @Autowired private CpuPlayer           cpuPlayer;
    @Autowired private BattleshipCpuPlayer battleshipCpuPlayer;
    @Autowired private Connect4CpuPlayer   connect4CpuPlayer;
    @Autowired private SimpMessagingTemplate messaging;

    /** Push a thin notification to every subscriber of this room's topic. */
    private void broadcast(Room room) {
        messaging.convertAndSend("/topic/room/" + room.getCode(), Map.of());
    }

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
     * Body: {"mode": "LOCAL" | "CPU" | "ONLINE", "gameType": "CHECKERS" | "BATTLESHIP"}
     */
    @PostMapping("/create")
    public GameState create(@RequestBody Map<String, String> body, HttpSession session) {
        String mode     = body.getOrDefault("mode", "LOCAL");
        String gameType = body.getOrDefault("gameType", "CHECKERS");
        Room room = roomService.createRoom(session.getId(), mode, gameType);

        // For CPU Battleship: auto-place CPU ships immediately
        if ("CPU".equals(mode) && "BATTLESHIP".equals(gameType)) {
            battleshipCpuPlayer.placeShips(room.getBattleshipGame());
        }
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
        broadcast(room);   // wake up the waiting room creator
        return ResponseEntity.ok(buildResponse(room, session.getId()));
    }

    // ── In-game shared actions ────────────────────────────────────────────────

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
        if ("BATTLESHIP".equals(room.getGameType())) {
            room.getBattleshipGame().newGame();
            if ("CPU".equals(room.getMode()))
                battleshipCpuPlayer.placeShips(room.getBattleshipGame());
        } else if ("CONNECT4".equals(room.getGameType())) {
            room.getConnect4Game().newGame();
        } else {
            room.getGame().newGame();
        }
        room.touch();
        broadcast(room);
        return buildResponse(room, session.getId());
    }

    // ── Checkers actions ──────────────────────────────────────────────────────

    /** Handle a board click. */
    @PostMapping("/click")
    public GameState click(@RequestBody Map<String, Integer> body, HttpSession session) {
        Room room = roomService.getRoom(session.getId());
        if (room == null || "BATTLESHIP".equals(room.getGameType())) return new GameState();

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
        broadcast(room);
        return buildResponse(room, session.getId());
    }

    /** Toggle forced-jumps rule. */
    @PostMapping("/forced-jumps")
    public GameState forcedJumps(@RequestBody Map<String, Boolean> body, HttpSession session) {
        Room room = roomService.getRoom(session.getId());
        if (room == null || room.getGame() == null) return new GameState();
        room.getGame().setForcedJumps(body.get("enabled"));
        return buildResponse(room, session.getId());
    }

    // ── Battleship actions ────────────────────────────────────────────────────

    /** Place the next ship.  Body: {row, col, horizontal: true|false} */
    @PostMapping("/battleship/place")
    public GameState bsPlace(@RequestBody Map<String, Object> body, HttpSession session) {
        Room room = roomService.getRoom(session.getId());
        if (room == null || !"BATTLESHIP".equals(room.getGameType())) return new GameState();

        BattleshipGameService game = room.getBattleshipGame();
        String playerRole = room.getPlayerRole(session.getId());
        String actor = "ANY".equals(playerRole) ? game.getPlacingTurn() : playerRole;

        int     row  = ((Number) body.get("row")).intValue();
        int     col  = ((Number) body.get("col")).intValue();
        boolean horiz = Boolean.TRUE.equals(body.get("horizontal"));

        game.placeShip(actor, row, col, horiz);

        // After RED finishes placing in CPU mode, mark game ready (CPU already placed)
        if ("CPU".equals(room.getMode()) && "BLACK".equals(game.getPlacingTurn())) {
            game.markRedReady();
        }

        room.touch();
        broadcast(room);
        return buildResponse(room, session.getId());
    }

    /** Fire a shot.  Body: {row, col} */
    @PostMapping("/battleship/shoot")
    public GameState bsShoot(@RequestBody Map<String, Integer> body, HttpSession session) {
        Room room = roomService.getRoom(session.getId());
        if (room == null || !"BATTLESHIP".equals(room.getGameType())) return new GameState();

        BattleshipGameService game = room.getBattleshipGame();
        String playerRole = room.getPlayerRole(session.getId());

        // In ONLINE mode, ignore shots when it's not the caller's turn
        if ("ONLINE".equals(room.getMode()) && !"ANY".equals(playerRole)
                && !playerRole.equals(game.getCurrentPlayer()))
            return buildResponse(room, session.getId());

        String actor = "ANY".equals(playerRole) ? game.getCurrentPlayer() : playerRole;
        game.shoot(actor, body.get("row"), body.get("col"));

        // CPU responds after a miss (which switches to BLACK's turn)
        if ("CPU".equals(room.getMode()) && "BLACK".equals(game.getCurrentPlayer())
                && "PLAYING".equals(game.getStatus()))
            battleshipCpuPlayer.makeMove(game);

        room.touch();
        broadcast(room);
        return buildResponse(room, session.getId());
    }

    // ── Connect Four actions ──────────────────────────────────────────────────

    /** Drop a piece.  Body: {col: 0-6} */
    @PostMapping("/connect4/drop")
    public GameState c4Drop(@RequestBody Map<String, Integer> body, HttpSession session) {
        Room room = roomService.getRoom(session.getId());
        if (room == null || !"CONNECT4".equals(room.getGameType())) return new GameState();

        Connect4GameService game = room.getConnect4Game();
        String playerRole = room.getPlayerRole(session.getId());

        if ("ONLINE".equals(room.getMode()) && !"ANY".equals(playerRole)
                && !playerRole.equals(game.getCurrentPlayer()))
            return buildResponse(room, session.getId());

        String actor = "ANY".equals(playerRole) ? game.getCurrentPlayer() : playerRole;
        game.dropPiece(actor, body.get("col"));

        // CPU responds immediately after player's move
        if ("CPU".equals(room.getMode()) && "BLACK".equals(game.getCurrentPlayer())
                && "PLAYING".equals(game.getStatus()))
            connect4CpuPlayer.makeMove(game);

        room.touch();
        broadcast(room);
        return buildResponse(room, session.getId());
    }

    // ── Response builder ──────────────────────────────────────────────────────

    private GameState buildResponse(Room room, String sessionId) {
        GameState state;
        if ("BATTLESHIP".equals(room.getGameType())) {
            String playerRole = room.getPlayerRole(sessionId);
            BattleshipSnapshot snap = room.getBattleshipGame().getSnapshot(playerRole);
            state = new GameState();
            state.setStatus(snap.status);
            state.setCurrentPlayer(snap.currentPlayer);
            state.setMyGrid(snap.myGrid);
            state.setEnemyGrid(snap.enemyGrid);
            state.setMyShipsPlaced(snap.myShipsPlaced);
            state.setViewingPlayer(snap.viewingPlayer);
            state.setPlacingTurn(snap.placingTurn);
            state.setLastResult(snap.lastResult);
            state.setRedReady(snap.redReady);
            state.setBlackReady(snap.blackReady);
        } else if ("CONNECT4".equals(room.getGameType())) {
            state = room.getConnect4Game().getState();
        } else {
            state = room.getGame().getState();
        }
        state.setGameType(room.getGameType());
        state.setMode(room.getMode());
        state.setRoomCode("ONLINE".equals(room.getMode()) ? room.getCode() : null);
        state.setPlayerRole(room.getPlayerRole(sessionId));
        state.setRoomStatus(room.isWaitingForOpponent() ? "WAITING" : "PLAYING");
        return state;
    }
}

