package com.platform.broadcaster.service;

import com.platform.broadcaster.api.dto.*;
import com.platform.broadcaster.domain.*;
import com.platform.broadcaster.event.BroadcasterEvent;
import com.platform.broadcaster.event.BroadcasterEventPublisher;
import com.platform.broadcaster.exception.BroadcasterNotFoundException;
import com.platform.broadcaster.exception.DuplicateBroadcasterException;
import com.platform.broadcaster.repository.BroadcasterRepository;
import com.platform.broadcaster.repository.GeoBlockRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BroadcasterService {

    private final BroadcasterRepository broadcasterRepository;
    private final GeoBlockRuleRepository geoBlockRuleRepository;
    private final BroadcasterEventPublisher eventPublisher;

    @Transactional
    public Broadcaster register(CreateBroadcasterRequest req) {
        if (broadcasterRepository.existsByUserId(req.userId())) {
            throw new DuplicateBroadcasterException(req.userId());
        }

        Broadcaster broadcaster = new Broadcaster();
        broadcaster.setUserId(req.userId());
        broadcaster.setStudioId(req.studioId());
        broadcaster.setDisplayName(req.displayName());
        broadcaster.setBio(req.bio());

        // Bootstrap default stream settings
        StreamSettings settings = new StreamSettings();
        settings.setBroadcaster(broadcaster);
        broadcaster.setStreamSettings(settings);

        Broadcaster saved = broadcasterRepository.save(broadcaster);
        log.info("Broadcaster registered broadcasterId={} userId={}", saved.getId(), saved.getUserId());
        return saved;
    }

    @Transactional(readOnly = true)
    public Broadcaster getById(UUID id) {
        return broadcasterRepository.findById(id)
            .orElseThrow(() -> new BroadcasterNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Broadcaster getByUserId(UUID userId) {
        return broadcasterRepository.findByUserId(userId)
            .orElseThrow(() -> new BroadcasterNotFoundException("userId=" + userId));
    }

    @Transactional(readOnly = true)
    public Page<Broadcaster> listOnline(Pageable pageable) {
        return broadcasterRepository.findByStatus(BroadcasterStatus.ONLINE, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Broadcaster> listOnlineVisibleInCountry(String countryCode, Pageable pageable) {
        return broadcasterRepository.findVisibleInCountry(BroadcasterStatus.ONLINE, countryCode, pageable);
    }

    @Transactional
    public Broadcaster updateProfile(UUID id, UpdateBroadcasterRequest req) {
        Broadcaster broadcaster = getById(id);

        if (req.displayName() != null) broadcaster.setDisplayName(req.displayName());
        if (req.bio() != null) broadcaster.setBio(req.bio());
        if (req.avatarUrl() != null) broadcaster.setAvatarUrl(req.avatarUrl());

        Broadcaster saved = broadcasterRepository.save(broadcaster);
        eventPublisher.publish(new BroadcasterEvent.ProfileUpdated(
            saved.getId(), saved.getUserId(),
            saved.getDisplayName(), saved.getAvatarUrl(),
            Instant.now()
        ));
        return saved;
    }

    @Transactional
    public Broadcaster updateStreamSettings(UUID id, UpdateStreamSettingsRequest req) {
        Broadcaster broadcaster = getById(id);
        StreamSettings s = broadcaster.getStreamSettings();

        if (req.title() != null) s.setTitle(req.title());
        if (req.tags() != null) s.setTags(req.tags());
        if (req.category() != null) s.setCategory(req.category());
        if (req.privateShowPricePerMinute() != null) s.setPrivateShowPricePerMinute(req.privateShowPricePerMinute());
        if (req.groupShowPricePerMinute() != null) s.setGroupShowPricePerMinute(req.groupShowPricePerMinute());
        if (req.spyShowPricePerMinute() != null) s.setSpyShowPricePerMinute(req.spyShowPricePerMinute());
        if (req.recordingEnabled() != null) s.setRecordingEnabled(req.recordingEnabled());
        if (req.cam2camMinViewerLevel() != null) s.setCam2camMinViewerLevel(req.cam2camMinViewerLevel());

        Broadcaster saved = broadcasterRepository.save(broadcaster);
        eventPublisher.publish(new BroadcasterEvent.StreamSettingsUpdated(
            saved.getId(), s.getTitle(), s.getTags(), s.getCategory(), Instant.now()
        ));
        return saved;
    }

    @Transactional
    public Broadcaster updateKycStatus(UUID id, KycUpdateRequest req) {
        Broadcaster broadcaster = getById(id);
        KycStatus previous = broadcaster.getKycStatus();

        broadcaster.setKycStatus(req.kycStatus());
        if (req.kycDocumentRef() != null) {
            broadcaster.setKycDocumentRef(req.kycDocumentRef());
        }

        Broadcaster saved = broadcasterRepository.save(broadcaster);
        log.info("KYC status changed broadcasterId={} {} -> {}",
            saved.getId(), previous, req.kycStatus());

        eventPublisher.publish(new BroadcasterEvent.KycStatusChanged(
            saved.getId(), saved.getUserId(), previous, req.kycStatus(), Instant.now()
        ));
        return saved;
    }

    @Transactional
    public Broadcaster changeStatus(UUID id, BroadcasterStatus newStatus) {
        Broadcaster broadcaster = getById(id);

        // Broadcasters may only go live if KYC is approved
        if ((newStatus == BroadcasterStatus.ONLINE || newStatus == BroadcasterStatus.PRIVATE
                || newStatus == BroadcasterStatus.GROUP)
                && !broadcaster.isKycApproved()) {
            throw new IllegalStateException(
                "Broadcaster " + id + " cannot go live: KYC status is " + broadcaster.getKycStatus());
        }

        BroadcasterStatus previous = broadcaster.getStatus();
        broadcaster.setStatus(newStatus);

        Broadcaster saved = broadcasterRepository.save(broadcaster);
        eventPublisher.publish(new BroadcasterEvent.StatusChanged(
            saved.getId(), saved.getUserId(), previous, newStatus, Instant.now()
        ));
        return saved;
    }

    // --- Tip menu ---

    @Transactional
    public TipMenuItem addTipMenuItem(UUID broadcasterId, UpsertTipMenuItemRequest req) {
        Broadcaster broadcaster = getById(broadcasterId);

        TipMenuItem item = new TipMenuItem();
        item.setBroadcaster(broadcaster);
        item.setLabel(req.label());
        item.setDescription(req.description());
        item.setTokenPrice(req.tokenPrice());
        item.setSortOrder(req.sortOrder());
        item.setActive(true);
        broadcaster.getTipMenuItems().add(item);

        broadcasterRepository.save(broadcaster);
        return item;
    }

    @Transactional
    public void removeTipMenuItem(UUID broadcasterId, UUID itemId) {
        Broadcaster broadcaster = getById(broadcasterId);
        broadcaster.getTipMenuItems().removeIf(i -> i.getId().equals(itemId));
        broadcasterRepository.save(broadcaster);
    }

    // --- Geo-block rules ---

    @Transactional
    public GeoBlockRule addGeoBlockRule(UUID broadcasterId, AddGeoBlockRuleRequest req) {
        Broadcaster broadcaster = getById(broadcasterId);

        if (geoBlockRuleRepository.existsByBroadcasterIdAndCountryCode(broadcasterId, req.countryCode())) {
            throw new IllegalArgumentException(
                "Geo-block rule already exists for " + req.countryCode());
        }

        GeoBlockRule rule = new GeoBlockRule();
        rule.setBroadcaster(broadcaster);
        rule.setCountryCode(req.countryCode());
        rule.setType(req.type());
        broadcaster.getGeoBlockRules().add(rule);

        broadcasterRepository.save(broadcaster);
        eventPublisher.publish(new BroadcasterEvent.GeoBlockRulesUpdated(broadcasterId, Instant.now()));
        return rule;
    }

    @Transactional
    public void removeGeoBlockRule(UUID broadcasterId, String countryCode) {
        getById(broadcasterId); // assert exists
        geoBlockRuleRepository.deleteByBroadcasterIdAndCountryCode(broadcasterId, countryCode);
        eventPublisher.publish(new BroadcasterEvent.GeoBlockRulesUpdated(broadcasterId, Instant.now()));
    }
}
