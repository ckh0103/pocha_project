
import React, { createContext, useContext, useEffect, useRef, useState, useCallback } from 'react';
import { Client } from '@stomp/stompjs'; // STOMP 클라이언트 라이브러리
import SockJS from 'sockjs-client';     // SockJS 폴백 지원 라이브러리

const WebSocketContext = createContext(null);

export const useWebSocket = () => useContext(WebSocketContext);

export const WebSocketProvider = ({ id, children }) => { 
    const [stompClient, setStompClient] = useState(null);
    const [lastMessage, setLastMessage] = useState(null);
    const [isConnected, setIsConnected] = useState(false);
    const reconnectAttempts = useRef(0);
    const MAX_RECONNECT_ATTEMPTS = 5; // 재연결 시도 횟수 제한

    // --- 여기 IP 주소 및 포트 설정 ---
    // .env 파일을 사용하지 않으므로, 백엔드 서버의 실제 IP와 포트를 여기에 직접 입력합니다.
    // **경고: 이 값을 실제 백엔드 서버의 IP 주소와 포트 번호로 반드시 변경해야 합니다!**
    const SERVER_IP = '192.168.0.231'; // <-- 백엔드 서버의 실제 IP 주소
    const SERVER_PORT = '80';       // <-- 백엔드 서버의 실제 포트 (기본 8080)
    const WS_ENDPOINT = `/ws`;        // 백엔드 WebSocketConfig에 설정된 엔드포인트 ("/ws")

    // STOMP 클라이언트 생성 및 설정 함수
    const setupStompClient = useCallback(() => {
        // 기존 클라이언트가 활성 상태라면 먼저 비활성화하여 정리
        if (stompClient && stompClient.active) {
            stompClient.deactivate();
        }
        setStompClient(null); // 상태 초기화

        // SockJS를 사용하여 WebSocket 연결을 생성합니다.
        const socket = new SockJS(`http://${SERVER_IP}:${SERVER_PORT}${WS_ENDPOINT}`);
        
        // @stomp/stompjs Client 인스턴스 생성
        const client = new Client({
            webSocketFactory: () => socket, // StompJS가 SockJS를 통해 통신하도록 설정
            debug: (str) => {
                // console.log("STOMP Debug:", str); // STOMP 디버그 메시지, 필요 시 주석 해제
            },
            reconnectDelay: 5000, // 연결 끊김 시 5초 후 재연결 시도
            heartbeatIncoming: 4000, // 서버에서 4초마다 heartbeat 수신 예상
            heartbeatOutgoing: 4000, // 클라이언트에서 4초마다 heartbeat 전송

            // STOMP 연결 시 서버에 보낼 헤더 (클라이언트 ID 전달)
            connectHeaders: {
                'client-id': id // 각 클라이언트를 식별하기 위한 ID (예: "table-4", "admin")
            }
        });

        // STOMP 연결 성공 시 콜백
        client.onConnect = () => {
            console.log(`WebSocket/STOMP 연결 성공 (ID: ${id})`);
            setIsConnected(true);
            reconnectAttempts.current = 0; // 재연결 성공 시 시도 횟수 초기화

            // --- STOMP 토픽 구독 ---
            client.subscribe('/topic/tableStatus', message => {
                setLastMessage(JSON.parse(message.body));
            });
            client.subscribe(`/user/queue/messages/${id}`, message => {
                 setLastMessage(JSON.parse(message.body));
            });
            client.subscribe(`/queue/table-${id}`, message => {
                setLastMessage(JSON.parse(message.body));
            });
        };

        // STOMP 연결 오류 시 콜백
        client.onStompError = (frame) => {
            console.error('Broker reported error: ' + frame.headers['message']);
            console.error('Additional details: ' + frame.body);
            setIsConnected(false);
            if (reconnectAttempts.current < MAX_RECONNECT_ATTEMPTS) {
                reconnectAttempts.current++;
                console.log(`STOMP 재연결 시도 중... (${reconnectAttempts.current}/${MAX_RECONNECT_ATTEMPTS})`);
            } else {
                console.error("재연결 시도 횟수 초과. 연결 실패.");
                alert("서버 연결에 실패했습니다. 페이지를 새로고침하거나 관리자에게 문의하세요.");
            }
        };

        // WebSocket 연결 끊김 시 콜백
        client.onWebSocketClose = (event) => {
            console.log(`WebSocket 연결 종료 (ID: ${id}, Code: ${event.code}, Reason: ${event.reason})`);
            setIsConnected(false);
        };

        // STOMP 클라이언트 활성화 (실제로 연결을 시작)
        client.activate(); 
        setStompClient(client);

        // 컴포넌트가 언마운트될 때 STOMP 클라이언트 정리
        return () => {
            if (client && client.active) {
                client.deactivate(); // 활성 상태이면 비활성화
              //  setStompClient(null);
            }
        };
    }, [id, SERVER_IP, SERVER_PORT, WS_ENDPOINT]);

    // WebSocket 연결 시작을 위한 useEffect 훅
    useEffect(() => {
        if (id) { 
                        // 연결 시도 전에 기존 클라이언트가 있다면 비활성화
            if (stompClient && stompClient.active) {
                stompClient.deactivate();
                setStompClient(null); // 확실히 이전 클라이언트 상태를 지움
            }
            const cleanup = setupStompClient();
            return () => cleanup(); 
        }
    }, [id, setupStompClient]); 

    // 메시지 전송 함수: STOMP 메시지 발행 (publish)
    const sendMessage = useCallback((message) => {
        if (stompClient && stompClient.active) {
            const destinationMap = {
                'enter_table': '/app/enterTable',
                'place_order': '/app/placeOrder',
                'staff_call': '/app/staffCall',
                'chat_request': '/app/chatRequest',
                'chat_accept': '/app/chatAccept',
                'chat_decline': '/app/chatDecline',
                'chat_message': '/app/chatMessage',
                'game_request': '/app/gameRequest',
                'game_accept': '/app/gameAccept',
                'game_decline': '/app/gameDecline',
            };
            const destination = destinationMap[message.type];

            if (destination) {
                stompClient.publish({ destination: destination, body: JSON.stringify(message.payload) });
            } else {
                console.error(`알 수 없는 메시지 타입: ${message.type}. 메시지를 전송할 수 없습니다.`);
            }
        } else {
            console.error('STOMP 클라이언트가 연결되지 않았습니다. 메시지를 보낼 수 없습니다.');
            alert("서버와 연결이 끊어졌습니다. 잠시 후 다시 시도해주세요.");
        }
    }, [stompClient]); 

    const value = {
        lastMessage,
        isConnected,
        sendMessage,
    };

    return (
        <WebSocketContext.Provider value={value}>
            {children}
        </WebSocketContext.Provider>
    );
};