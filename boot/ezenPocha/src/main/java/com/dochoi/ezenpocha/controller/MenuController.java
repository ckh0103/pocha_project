package com.dochoi.ezenpocha.controller;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import com.dochoi.ezenpocha.config.PochaWebSocketHandler;
import com.dochoi.ezenpocha.service.MenuService;
import com.dochoi.ezenpocha.vo.Menu;
import com.dochoi.ezenpocha.vo.MenuDTO;

@RestController
@RequestMapping("/api/menus") // 메뉴 관련 API의 기본 경로
@CrossOrigin(origins = "http://localhost:3000") // React 개발 서버 포트 허용
public class MenuController {

    private final MenuService menuService;
    private final PochaWebSocketHandler webSocketHandler; // WebSocketHandler 주입

    // 생성자 수정
    public MenuController(MenuService menuService, PochaWebSocketHandler webSocketHandler) {
        this.menuService = menuService;
        this.webSocketHandler = webSocketHandler;
    }

    @GetMapping
    public ResponseEntity<List<MenuDTO>> getAllMenus() {
        List<Menu> menus = menuService.getAllMenus();
        List<MenuDTO> menuDTOs = menus.stream()
                .map(menu -> new MenuDTO(menu.getId(), menu.getName(), menu.getPrice(), menu.getCategory(), menu.getImage()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(menuDTOs);
    }

    @PostMapping
    public ResponseEntity<MenuDTO> addMenu(@RequestBody MenuDTO menuDTO) throws IOException {
        Menu savedMenu = menuService.addMenu(menuDTO);
        webSocketHandler.broadcastMenuUpdate(); // Controller가 직접 알림을 요청
        MenuDTO responseDTO = new MenuDTO(savedMenu.getId(), savedMenu.getName(), savedMenu.getPrice(), savedMenu.getCategory(), savedMenu.getImage());
        return new ResponseEntity<>(responseDTO, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<MenuDTO> updateMenu(@PathVariable Long id, @RequestBody MenuDTO menuDTO) throws IOException {
        Menu updatedMenu = menuService.updateMenu(id, menuDTO);
        if (updatedMenu != null) {
            webSocketHandler.broadcastMenuUpdate(); // Controller가 직접 알림을 요청
            MenuDTO responseDTO = new MenuDTO(updatedMenu.getId(), updatedMenu.getName(), updatedMenu.getPrice(), updatedMenu.getCategory(), updatedMenu.getImage());
            return ResponseEntity.ok(responseDTO);
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMenu(@PathVariable Long id) throws IOException {
        menuService.deleteMenu(id);
        webSocketHandler.broadcastMenuUpdate(); // Controller가 직접 알림을 요청
        return ResponseEntity.noContent().build();
    }
}