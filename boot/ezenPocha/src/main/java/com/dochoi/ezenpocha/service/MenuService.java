package com.dochoi.ezenpocha.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.dochoi.ezenpocha.config.PochaWebSocketHandler;
import com.dochoi.ezenpocha.mapper.MenuRepository;
import com.dochoi.ezenpocha.vo.Menu;
import com.dochoi.ezenpocha.vo.MenuDTO;

import jakarta.transaction.Transactional;

@Service
public class MenuService {

    private final MenuRepository menuRepository;

    public MenuService(MenuRepository menuRepository) {
        this.menuRepository = menuRepository;
    }

    public List<Menu> getAllMenus() {
        return menuRepository.findAll();
    }

    @Transactional
    public Menu addMenu(MenuDTO dto) {
        Menu menu = new Menu();
        menu.setName(dto.getName());
        menu.setPrice(dto.getPrice());
        menu.setCategory(dto.getCategory());
        menu.setImage(dto.getImage());
        return menuRepository.save(menu);
    }

    @Transactional
    public Menu updateMenu(Long id, MenuDTO dto) {
        Optional<Menu> optMenu = menuRepository.findById(id);
        if (optMenu.isPresent()) {
            Menu menu = optMenu.get();
            menu.setName(dto.getName());
            menu.setPrice(dto.getPrice());
            menu.setCategory(dto.getCategory());
            menu.setImage(dto.getImage());
            return menuRepository.save(menu);
        }
        return null;
    }

    @Transactional
    public void deleteMenu(Long id) {
        menuRepository.deleteById(id);
    }
}