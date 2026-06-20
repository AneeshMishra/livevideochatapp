package com.platform.catalog.web.dto;

import com.platform.catalog.domain.Category;

public class CategoryResponse {

    private String id;
    private String name;
    private String slug;
    private String description;
    private int displayOrder;

    public static CategoryResponse from(Category c) {
        var r = new CategoryResponse();
        r.id           = c.getId().toString();
        r.name         = c.getName();
        r.slug         = c.getSlug();
        r.description  = c.getDescription();
        r.displayOrder = c.getDisplayOrder();
        return r;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public String getDescription() { return description; }
    public int getDisplayOrder() { return displayOrder; }
}
