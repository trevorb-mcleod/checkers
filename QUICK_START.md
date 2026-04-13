## 🚀 Connect 4 Quick Start Guide

### Starting the Application

```bash
cd /Users/trevorbell/IdeaProjects/Chekckers
./mvnw clean package -DskipTests
java -jar target/Chekckers-1.0-SNAPSHOT.jar
```

Then open your browser to: **http://localhost:8080**

---

## 🎮 Playing Connect 4

### Game Selection
1. Click the **🔴 Connect 4** button on the main screen
2. Choose your preferred mode

### Game Modes

#### 🏠 Local Play
- Two players share one device
- Players alternate turns manually
- Perfect for learning the game

#### 🤖 CPU vs Player
- You play RED, AI plays BLACK
- AI uses intelligent Minimax strategy
- Difficulty: Expert (depth-7 search)

#### 🌐 Online Multiplayer
- Play with friends remotely
- Real-time synchronization via WebSocket
- Share the 4-digit room code

### How to Play

**Objective**: Get 4 pieces in a row (horizontal, vertical, or diagonal)

1. **Drop Pieces**: Click a column button (⬇) to drop your piece
2. **Gravity**: Pieces fall to the lowest available position
3. **Alternate**: Players take turns dropping pieces
4. **Win**: First to achieve 4-in-a-row wins the game
5. **Draw**: If the board fills without a winner, it's a draw

### Game Controls

| Action | Button | Keyboard |
|--------|--------|----------|
| Drop in column | Click ⬇ button | (Mouse only) |
| New Game | Click "New Game" | (Mouse only) |
| Back to Menu | Click "☰ Menu" | (Mouse only) |

---

## 🧠 AI Strategy

The CPU player uses an advanced Minimax algorithm that:

1. **Looks Ahead**: Evaluates moves 7 levels deep
2. **Scores Positions**: 
   - Wins = 1000 points
   - 3-in-a-row opportunity = 50 points
   - 2-in-a-row = 5 points
   - Blocks opponent threats = defensive scoring
3. **Optimizes**: Uses alpha-beta pruning to speed up calculations
4. **Adds Variety**: Randomizes between equally-good moves

### Sample Moves
- Opens center columns (most winning positions)
- Blocks opponent threats
- Creates winning opportunities
- Adapts to player strategy

---

## 📊 Game States

```
PLAYING    → Game is active, players taking turns
RED_WINS   → Red achieved 4-in-a-row
BLACK_WINS → Black achieved 4-in-a-row
DRAW       → Board full, no winner
```

---

## 🔌 API Reference

All endpoints use `/api/room/` base path.

### Create Game
```bash
curl -X POST http://localhost:8080/api/room/create \
  -H "Content-Type: application/json" \
  -d '{"mode":"LOCAL","gameType":"CONNECT4"}'
```

**Modes**: LOCAL, CPU, ONLINE
**Response**: GameState JSON with board, players, status

### Drop Piece
```bash
curl -X POST http://localhost:8080/api/room/connect4/drop \
  -H "Content-Type: application/json" \
  -d '{"col":3}'
```

**Columns**: 0-6 (left to right)
**Response**: Updated GameState

### Get Current State
```bash
curl http://localhost:8080/api/room/state
```

**Response**: Full game state with board, player info, status

### New Game
```bash
curl -X POST http://localhost:8080/api/room/new
```

**Response**: Fresh GameState with empty board

---

## 📱 Mobile Support

The game is fully responsive on:
- 📱 Phones (portrait & landscape)
- 📱 Tablets (portrait & landscape)
- 💻 Desktops (any size)

The board automatically scales to fit your screen while maintaining the correct proportions.

---

## ⚙️ Settings & Customization

### Game Difficulty (Future Enhancement)
Could be set via game creation parameters:
```json
{
  "mode": "CPU",
  "gameType": "CONNECT4",
  "difficulty": "EASY|MEDIUM|HARD"
}
```

Currently locked at HARD (depth-7).

---

## 🐛 Troubleshooting

### Game Won't Start
1. Ensure server is running (`java -jar target/...`)
2. Check browser console for errors (F12)
3. Try a different game mode
4. Clear browser cache and reload

### CPU Takes Too Long
- This is normal for expert AI (depth-7 calculation)
- Typically takes 2-5 seconds per move
- AI optimizes using alpha-beta pruning

### Online Mode Issues
- Check WebSocket connectivity (browser console)
- Ensure room code is correct (4 digits)
- Both players must be on same network or have port forwarding
- Try refreshing the page to reconnect

---

## 📚 Game Rules

### Standard Connect 4 Rules
1. Board is 6 rows × 7 columns
2. Each player has unlimited pieces
3. Players alternate turns
4. Pieces fall due to gravity
5. First to 4-in-a-row wins
6. Game is a draw if board fills

### Turn Order
- RED always goes first
- In CPU mode: Human = RED, AI = BLACK
- Players alternate after each successful drop

---

## 🎯 Tips for Playing

### Against the CPU

1. **Control the Center**: Center column has most winning patterns
2. **Block Threats**: If AI has 3-in-a-row, block the 4th spot
3. **Create Double Threats**: Force AI to defend while you attack
4. **Look Ahead**: Think 2-3 moves in advance
5. **Avoid Telegraphing**: Don't make obvious threats

### Two-Player Tips

1. **Secure the Middle**: Center board is most valuable
2. **Plan Vertically**: Easier to build 4-in-a-row vertically
3. **Watch Diagonals**: Diagonal threats are easy to miss
4. **Build Multiple Threats**: Create positions that threaten multiple wins

---

## 📈 Performance Notes

- **Board Calculation**: ~50-500ms depending on board state
- **AI Move**: ~2-5 seconds (depth-7 with alpha-beta)
- **Network Latency**: WebSocket updates are instant for local network
- **Mobile Performance**: Optimized for iPhone 6+, Android 5.0+

---

## 🚀 Deployment

### Docker
```bash
docker build -t game-hub .
docker run -p 8080:8080 game-hub
```

### Cloud Platforms
- Railway.com (configured in repo)
- Heroku (with Procfile)
- AWS (with application server)
- Azure (with container setup)

### Environment Variables
```
SERVER_PORT=8080
GAME_TIMEOUT=30m
```

---

## 📖 Documentation Files

- `CONNECT4_IMPLEMENTATION.md` - Technical implementation details
- `CONNECT4_COMPLETION.md` - Completion summary
- `IMPLEMENTATION_CHECKLIST.md` - Feature checklist
- `README.md` - Project overview (coming soon)

---

## 🎮 All Available Games

| Game | Modes | AI | Players |
|------|-------|----|----|
| ♟ Checkers | LOCAL, CPU, ONLINE | Depth-6 AI | 2 |
| ⚓ Battleship | LOCAL, CPU, ONLINE | Strategic AI | 2 |
| 🔴 Connect 4 | LOCAL, CPU, ONLINE | Depth-7 AI | 2 |

---

**Version**: 1.0  
**Last Updated**: April 13, 2026  
**Status**: ✅ Production Ready

Enjoy playing! 🎉

