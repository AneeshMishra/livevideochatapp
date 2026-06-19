package com.platform.userprofile.service;

import com.platform.userprofile.domain.BlockEntry;
import com.platform.userprofile.event.UserProfileEvent;
import com.platform.userprofile.event.UserProfileEventPublisher;
import com.platform.userprofile.exception.AlreadyBlockedException;
import com.platform.userprofile.exception.SelfActionException;
import com.platform.userprofile.repository.BlockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BlockService {

    private final BlockRepository blockRepository;
    private final UserProfileEventPublisher eventPublisher;

    @Transactional
    public BlockEntry block(UUID blockerId, UUID blockedId, String reason) {
        if (blockerId.equals(blockedId)) {
            throw new SelfActionException("block");
        }
        if (blockRepository.existsByBlockerIdAndBlockedId(blockerId, blockedId)) {
            throw new AlreadyBlockedException();
        }

        BlockEntry entry = BlockEntry.of(blockerId, blockedId, reason);
        BlockEntry saved = blockRepository.save(entry);

        eventPublisher.publish(new UserProfileEvent.UserBlocked(blockerId, blockedId, Instant.now()));
        return saved;
    }

    @Transactional
    public void unblock(UUID blockerId, UUID blockedId) {
        blockRepository.findByBlockerIdAndBlockedId(blockerId, blockedId)
                .ifPresent(entry -> {
                    blockRepository.delete(entry);
                    eventPublisher.publish(new UserProfileEvent.UserUnblocked(
                            blockerId, blockedId, Instant.now()));
                });
    }

    @Transactional(readOnly = true)
    public boolean isBlocked(UUID blockerId, UUID blockedId) {
        return blockRepository.existsByBlockerIdAndBlockedId(blockerId, blockedId);
    }

    @Transactional(readOnly = true)
    public Page<BlockEntry> listBlocked(UUID blockerId, Pageable pageable) {
        return blockRepository.findByBlockerIdOrderByCreatedAtDesc(blockerId, pageable);
    }
}
