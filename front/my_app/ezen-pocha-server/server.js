// ezen-pocha-server/server.js

const express = require('express');
const http = require('http');
const WebSocket = require('ws');

const app = express();
const server = http.createServer(app);
const wss = new WebSocket.Server({ server });

// 연결된 모든 클라이언트를 관리
wss.on('connection', (ws) => {
    console.log('클라이언트가 연결되었습니다.');

    // 클라이언트로부터 메시지를 받았을 때
    ws.on('message', (message) => {
        console.log('받은 메시지: %s', message);

        // 받은 메시지를 모든 클라이언트에게 다시 전송 (Broadcast)
        wss.clients.forEach((client) => {
            if (client !== ws && client.readyState === WebSocket.OPEN) {
                client.send(message.toString());
            }
        });
    });

    ws.on('close', () => {
        console.log('클라이언트 연결이 해제되었습니다.');
    });

    ws.on('error', (error) => {
        console.error('WebSocket 오류 발생:', error);
    });
});

const PORT = 80;
server.listen(PORT, () => {
    console.log(`서버가 ${PORT} 포트에서 실행 중입니다.`);
});