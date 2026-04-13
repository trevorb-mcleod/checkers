package org.example.model;

import java.util.List;

public class GameState {

    // Board values: 0=NONE, 1=RED, 2=RED_KING, 3=BLACK, 4=BLACK_KING
    private int[][] board;
    private String currentPlayer;  // "RED" or "BLACK"
    private String status;         // "PLAYING", "RED_WINS", "BLACK_WINS"
    private int selectedRow;
    private int selectedCol;
    private List<int[]> validMoves;
    private int redCount;
    private int blackCount;
    private int[] multiJumpPiece;  // [row, col] of piece that must keep jumping, or null

    // Room / mode fields
    private String mode;        // "LOCAL", "CPU", "ONLINE" — null when no room exists
    private String roomCode;    // 4-digit code (ONLINE only), null otherwise
    private String playerRole;  // "ANY" (local/cpu), "RED", "BLACK"
    private String roomStatus;  // "WAITING" (online, awaiting 2nd player), "PLAYING"

    private boolean forcedJumps;

    public GameState() {
        this.selectedRow = -1;
        this.selectedCol = -1;
    }

    public int[][] getBoard() { return board; }
    public void setBoard(int[][] board) { this.board = board; }

    public String getCurrentPlayer() { return currentPlayer; }
    public void setCurrentPlayer(String currentPlayer) { this.currentPlayer = currentPlayer; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getSelectedRow() { return selectedRow; }
    public void setSelectedRow(int selectedRow) { this.selectedRow = selectedRow; }

    public int getSelectedCol() { return selectedCol; }
    public void setSelectedCol(int selectedCol) { this.selectedCol = selectedCol; }

    public List<int[]> getValidMoves() { return validMoves; }
    public void setValidMoves(List<int[]> validMoves) { this.validMoves = validMoves; }

    public int getRedCount() { return redCount; }
    public void setRedCount(int redCount) { this.redCount = redCount; }

    public int getBlackCount() { return blackCount; }
    public void setBlackCount(int blackCount) { this.blackCount = blackCount; }

    public int[] getMultiJumpPiece() { return multiJumpPiece; }
    public void setMultiJumpPiece(int[] multiJumpPiece) { this.multiJumpPiece = multiJumpPiece; }

    public boolean isForcedJumps() { return forcedJumps; }
    public void setForcedJumps(boolean forcedJumps) { this.forcedJumps = forcedJumps; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public String getRoomCode() { return roomCode; }
    public void setRoomCode(String roomCode) { this.roomCode = roomCode; }

    public String getPlayerRole() { return playerRole; }
    public void setPlayerRole(String playerRole) { this.playerRole = playerRole; }

    public String getRoomStatus() { return roomStatus; }
    public void setRoomStatus(String roomStatus) { this.roomStatus = roomStatus; }
}
