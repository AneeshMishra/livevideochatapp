package com.platform.broadcaster.service;

import com.platform.broadcaster.domain.Broadcaster;
import com.platform.broadcaster.domain.Studio;
import com.platform.broadcaster.exception.StudioNotFoundException;
import com.platform.broadcaster.repository.BroadcasterRepository;
import com.platform.broadcaster.repository.StudioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StudioService {

    private final StudioRepository studioRepository;
    private final BroadcasterRepository broadcasterRepository;

    @Transactional
    public Studio create(UUID ownerId, String name, int defaultRevenueSplitPercent) {
        Studio studio = new Studio();
        studio.setOwnerId(ownerId);
        studio.setName(name);
        studio.setDefaultRevenueSplitPercent(defaultRevenueSplitPercent);
        Studio saved = studioRepository.save(studio);
        log.info("Studio created studioId={} ownerId={}", saved.getId(), ownerId);
        return saved;
    }

    @Transactional(readOnly = true)
    public Studio getById(UUID id) {
        return studioRepository.findById(id)
            .orElseThrow(() -> new StudioNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<Broadcaster> listBroadcasters(UUID studioId) {
        getById(studioId); // assert exists
        return broadcasterRepository.findByStudioId(studioId);
    }

    /**
     * Assigns a broadcaster to a studio and optionally overrides the revenue split.
     * The studio owner negotiates the split; pass null to use the studio default.
     */
    @Transactional
    public Broadcaster assignBroadcaster(UUID studioId, UUID broadcasterId, Integer revenueSplitPercent) {
        Studio studio = getById(studioId);
        Broadcaster broadcaster = broadcasterRepository.findById(broadcasterId)
            .orElseThrow(() -> new IllegalArgumentException("Broadcaster not found: " + broadcasterId));

        broadcaster.setStudioId(studioId);
        broadcaster.setRevenueSplitPercent(
            revenueSplitPercent != null ? revenueSplitPercent : studio.getDefaultRevenueSplitPercent()
        );
        return broadcasterRepository.save(broadcaster);
    }

    @Transactional
    public void removeBroadcaster(UUID studioId, UUID broadcasterId) {
        getById(studioId); // assert exists
        Broadcaster broadcaster = broadcasterRepository.findById(broadcasterId)
            .orElseThrow(() -> new IllegalArgumentException("Broadcaster not found: " + broadcasterId));

        if (!studioId.equals(broadcaster.getStudioId())) {
            throw new IllegalArgumentException("Broadcaster " + broadcasterId + " is not in studio " + studioId);
        }

        broadcaster.setStudioId(null);
        broadcaster.setRevenueSplitPercent(50); // reset to independent default
        broadcasterRepository.save(broadcaster);
    }
}
