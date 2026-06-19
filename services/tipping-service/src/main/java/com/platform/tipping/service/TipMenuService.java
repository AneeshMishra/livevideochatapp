package com.platform.tipping.service;

import com.platform.tipping.domain.TipMenuItem;
import com.platform.tipping.exception.MenuItemNotFoundException;
import com.platform.tipping.repository.TipMenuItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class TipMenuService {

    private static final int MAX_MENU_ITEMS = 20;

    private final TipMenuItemRepository menuItemRepo;

    public TipMenuItem createItem(UUID broadcasterId, String title, String description,
                                   long tokenPrice, int position) {
        int existingCount = menuItemRepo.countByBroadcasterIdAndActiveTrue(broadcasterId);
        if (existingCount >= MAX_MENU_ITEMS) {
            throw new IllegalStateException("Maximum " + MAX_MENU_ITEMS + " active menu items allowed");
        }

        TipMenuItem item = TipMenuItem.create(broadcasterId, title, description, tokenPrice, position);
        return menuItemRepo.save(item);
    }

    public TipMenuItem updateItem(UUID itemId, UUID broadcasterId, String title,
                                   String description, long tokenPrice, int position) {
        TipMenuItem item = menuItemRepo.findByIdAndBroadcasterId(itemId, broadcasterId)
                .orElseThrow(() -> new MenuItemNotFoundException(itemId));

        item.setTitle(title);
        item.setDescription(description);
        item.setTokenPrice(tokenPrice);
        item.setPosition(position);
        return menuItemRepo.save(item);
    }

    public void deactivateItem(UUID itemId, UUID broadcasterId) {
        TipMenuItem item = menuItemRepo.findByIdAndBroadcasterId(itemId, broadcasterId)
                .orElseThrow(() -> new MenuItemNotFoundException(itemId));
        item.setActive(false);
        menuItemRepo.save(item);
    }

    @Transactional(readOnly = true)
    public List<TipMenuItem> getActiveItems(UUID broadcasterId) {
        return menuItemRepo.findByBroadcasterIdAndActiveTrueOrderByPositionAsc(broadcasterId);
    }
}
