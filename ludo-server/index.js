const express = require('express');
const http = require('http');
const { Server } = require('socket.io');

const app = express();
const server = http.createServer(app);
const io = new Server(server, {
    cors: { origin: "*" }
});

// A simple link for UptimeRobot to ping so Render doesn't fall asleep
app.get('/ping', (req, res) => {
    res.send('Ludo Server is awake!');
});

// When a phone connects to the server
io.on('connection', (socket) => {
    console.log('A player connected:', socket.id);

    // When a phone wants to join a specific 4-digit room
    socket.on('join_room', (roomCode) => {
        socket.join(roomCode);
        console.log(`Player joined room: ${roomCode}`);
    });

    // When a phone rolls a dice or moves a token, send it to everyone ELSE in the room
    socket.on('game_action', (data) => {
        socket.to(data.roomCode).emit('game_update', data);
    });

    socket.on('disconnect', () => {
        console.log('A player disconnected');
    });
});

const PORT = process.env.PORT || 3000;
server.listen(PORT, () => {
    console.log(`Server running on port ${PORT}`);
});