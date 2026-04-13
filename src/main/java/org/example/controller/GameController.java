package org.example.controller;

import org.example.model.GameState;
import org.example.service.CheckersGameService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/game")
public class GameController {

    @Autowired
    private CheckersGameService gameService;

    @GetMapping("/state")
    public GameState getState() {
        return gameService.getState();
    }

    @PostMapping("/new")
    public GameState newGame() {
        gameService.newGame();
        return gameService.getState();
    }

    @PostMapping("/click")
    public GameState click(@RequestBody Map<String, Integer> body) {
        int row = body.get("row");
        int col = body.get("col");
        return gameService.click(row, col);
    }

    @PostMapping("/forced-jumps")
    public GameState setForcedJumps(@RequestBody Map<String, Boolean> body) {
        gameService.setForcedJumps(body.get("enabled"));
        return gameService.getState();
    }
}

