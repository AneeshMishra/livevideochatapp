package com.platform.tipping.exception;

import java.util.UUID;

public class MenuItemNotFoundException extends RuntimeException {
    public MenuItemNotFoundException(UUID itemId) {
        super("Tip menu item not found: " + itemId);
    }
}
