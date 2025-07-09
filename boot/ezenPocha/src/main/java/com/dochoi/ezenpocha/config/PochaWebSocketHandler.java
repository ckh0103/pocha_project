package com.dochoi.ezenpocha.config;

import java.awt.*;
import java.io.IOException;
import java.net.URI; // URI 임포트 추가
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.dochoi.ezenpocha.service.*;
import com.dochoi.ezenpocha.vo.Menu;
import com.dochoi.ezenpocha.vo.MenuDTO;
import com.dochoi.ezenpocha.vo.TableStatusDTO;
import com.fasterxml.jackson.databind.ObjectMapper;


@Component
public class PochaWebSocketHandler extends TextWebSocketHandler {

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final TableService tableService;
    private final MenuService menuService;

    public PochaWebSocketHandler(TableService tableService, MenuService menuService, ObjectMapper objectMapper) {
        this.tableService = tableService;
        this.menuService = menuService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String id = extractId(session);
        if (id != null) {
            sessions.put(id, session);
            System.out.println("WebSocket 연결됨: " + id);
            sendInitialTableStatus(session);
            sendInitialMenuStatus(session);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String rawPayload = message.getPayload();
        System.out.println("메시지 수신 (" + extractId(session) + "): " + rawPayload);
        
        Map<String, Object> messageMap = objectMapper.readValue(rawPayload, Map.class);
        String type = (String) messageMap.get("type");

        if (type == null) return;

        if (isPeerToPeerMessage(type)) {
            forwardMessageToTarget(messageMap, rawPayload);
            return;
        }
        
        if ("staff_call".equals(type)) {
            sendMessageToAdmin(rawPayload);
            return;
        }

        boolean stateChanged = true;
        switch (type) {
            case "enter_table":
                tableService.enterTable(messageMap);
                break;
            case "place_order":
                tableService.placeOrder(messageMap);
                break;
            case "clear_table":
                tableService.clearTable(messageMap);
                break;
            default:
                stateChanged = false;
                System.out.println("처리되지 않은 메시지 타입: " + type);
                break;
        }

        if (stateChanged) {
            broadcastTableStatus();
        }
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String id = extractId(session);
        if (id != null) {
            sessions.remove(id);
            System.out.println("WebSocket 연결 끊김: " + id + " (상태: " + status.getCode() + ")");
            broadcastTableStatus();
        }
    }

    public void broadcastMenuUpdate() throws IOException {
        List<Menu> menus = menuService.getAllMenus();
        
        // ▼▼▼ 이 부분을 한 줄로 연결합니다. ▼▼▼
        List<MenuDTO> menuDTOs = menus.stream()
                .map(menu -> new MenuDTO(menu.getId(), menu.getName(), menu.getPrice(), menu.getCategory(), menu.getImage()))
                .collect(Collectors.toList());
                
        String messageToSend = objectMapper.writeValueAsString(Map.of("type", "all_menus_update", "payload", menuDTOs));

        for (WebSocketSession s : sessions.values()) {
            if (s.isOpen()) {
                s.sendMessage(new TextMessage(messageToSend));
            }
        }
        System.out.println("모든 메뉴 현황 브로드캐스트 완료.");
    }


 // sendInitialMenuStatus 메서드 수정

    private void sendInitialMenuStatus(WebSocketSession session) throws IOException {
        List<Menu> menus = menuService.getAllMenus();
        
        // ▼▼▼ 이 부분도 동일하게 한 줄로 연결합니다. ▼▼▼
        List<MenuDTO> menuDTOs = menus.stream()
               .map(menu -> new MenuDTO(menu.getId(), menu.getName(), menu.getPrice(), menu.getCategory(), menu.getImage()))
               .collect(Collectors.toList());

        String messageToSend = objectMapper.writeValueAsString(Map.of("type", "all_menus_update", "payload", menuDTOs));

        if (session.isOpen()) {
            session.sendMessage(new TextMessage(messageToSend));
            System.out.println(extractId(session) + "에게 초기 메뉴 현황 전송 완료.");
        }
    }

    public void broadcastTableStatus() throws IOException {
        List<TableStatusDTO> allTableStatus = tableService.getAllTableStatus();
        String messageToSend = objectMapper.writeValueAsString(Map.of("type", "all_tables_status", "payload", allTableStatus));
        for (WebSocketSession s : sessions.values()) {
            if (s.isOpen()) {
                s.sendMessage(new TextMessage(messageToSend));
            }
        }
        System.out.println("모든 테이블 현황 브로드캐스트 완료.");
    }

    private void sendInitialTableStatus(WebSocketSession session) throws IOException {
        List<TableStatusDTO> allTableStatus = tableService.getAllTableStatus();
        String messageToSend = objectMapper.writeValueAsString(Map.of("type", "all_tables_status", "payload", allTableStatus));
        if (session.isOpen()) {
            session.sendMessage(new TextMessage(messageToSend));
            System.out.println(extractId(session) + "에게 초기 테이블 현황 전송 완료.");
        }
    }
    
    private boolean isPeerToPeerMessage(String type) {
        return "chat_request".equals(type) || "chat_accept".equals(type) || "chat_message".equals(type);
    }

    private void forwardMessageToTarget(Map<String, Object> messageMap, String rawPayload) throws IOException {
        Map<String, Object> payloadMap = (Map<String, Object>) messageMap.get("payload");
        String targetId = (String) payloadMap.get("to");

        if (targetId != null) {
            WebSocketSession targetSession = sessions.get(targetId);
            if (targetSession != null && targetSession.isOpen()) {
                targetSession.sendMessage(new TextMessage(rawPayload));
                System.out.println("메시지 전달 완료: -> " + targetId);
            } else {
                System.out.println("메시지 전달 실패: 타겟 세션을 찾을 수 없거나 닫혀있습니다. (ID: " + targetId + ")");
            }
        }
    }

    private void sendMessageToAdmin(String payload) throws IOException {
        WebSocketSession adminSession = sessions.get("admin");
        if (adminSession != null && adminSession.isOpen()) {
            adminSession.sendMessage(new TextMessage(payload));
            System.out.println("관리자에게 메시지 전송: " + payload);
        }
    }

    private String extractId(WebSocketSession session) {
        String query = session.getUri() != null ? session.getUri().getQuery() : null;
        if (query != null && query.startsWith("id=")) {
            return query.substring("id=".length());
        }
        return "unknown-" + session.getId();
    }
}