package com.platform.catalog.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PagedRoomsResponse {

    private List<RoomCardResponse> rooms;
    private long total;
    private int page;
    private int size;
    private int totalPages;

    public PagedRoomsResponse() {}

    public PagedRoomsResponse(List<RoomCardResponse> rooms, long total, int page, int size) {
        this.rooms      = rooms;
        this.total      = total;
        this.page       = page;
        this.size       = size;
        this.totalPages = size > 0 ? (int) Math.ceil((double) total / size) : 0;
    }

    public List<RoomCardResponse> getRooms() { return rooms; }
    public long getTotal() { return total; }
    public int getPage() { return page; }
    public int getSize() { return size; }
    public int getTotalPages() { return totalPages; }
}
