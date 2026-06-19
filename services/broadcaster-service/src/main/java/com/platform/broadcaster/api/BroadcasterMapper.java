package com.platform.broadcaster.api;

import com.platform.broadcaster.api.dto.*;
import com.platform.broadcaster.domain.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper
public interface BroadcasterMapper {

    @Mapping(source = "streamSettings", target = "streamSettings")
    @Mapping(source = "tipMenuItems", target = "tipMenuItems")
    @Mapping(source = "geoBlockRules", target = "geoBlockRules")
    BroadcasterProfileResponse toResponse(Broadcaster broadcaster);

    StreamSettingsResponse toResponse(StreamSettings settings);

    TipMenuItemResponse toResponse(TipMenuItem item);

    GeoBlockRuleResponse toResponse(GeoBlockRule rule);

    List<TipMenuItemResponse> toTipMenuResponses(List<TipMenuItem> items);

    List<GeoBlockRuleResponse> toGeoBlockResponses(List<GeoBlockRule> rules);
}
