package com.platform.tipping.service;

import com.platform.tipping.client.WalletClient;
import com.platform.tipping.domain.GoalStatus;
import com.platform.tipping.domain.Tip;
import com.platform.tipping.domain.TipGoal;
import com.platform.tipping.domain.TipMenuItem;
import com.platform.tipping.domain.TipStatus;
import com.platform.tipping.event.TipEvent;
import com.platform.tipping.event.TipEventPublisher;
import com.platform.tipping.exception.InsufficientTokensException;
import com.platform.tipping.exception.MenuItemNotFoundException;
import com.platform.tipping.repository.TipGoalRepository;
import com.platform.tipping.repository.TipMenuItemRepository;
import com.platform.tipping.repository.TipRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TipServiceTest {

    @Mock TipRepository tipRepo;
    @Mock TipMenuItemRepository menuItemRepo;
    @Mock TipGoalRepository goalRepo;
    @Mock WalletClient walletClient;
    @Mock TipEventPublisher eventPublisher;

    @InjectMocks TipService tipService;

    // ── sendTip — happy path ──────────────────────────────────────────────────

    @Test
    void sendTip_success_completesAndPublishesTipReceived() {
        UUID senderId    = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        UUID roomId      = UUID.randomUUID();
        String key       = "key-001";

        when(tipRepo.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(tipRepo.save(any())).thenAnswer(inv -> {
            Tip t = inv.getArgument(0);
            ReflectionTestUtils.setField(t, "id", UUID.randomUUID());
            return t;
        });
        when(goalRepo.findActiveGoalForUpdate(recipientId)).thenReturn(Optional.empty());
        when(walletClient.transfer(any(), any(), anyLong(), any(), any())).thenReturn(null);

        Tip result = tipService.sendTip(senderId, recipientId, roomId, 100L,
                "great show!", null, key, "Viewer123");

        assertThat(result.getStatus()).isEqualTo(TipStatus.COMPLETED);
        assertThat(result.getCompletedAt()).isNotNull();

        ArgumentCaptor<TipEvent> captor = ArgumentCaptor.forClass(TipEvent.class);
        verify(eventPublisher).publish(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(TipEvent.TipReceived.class);
        TipEvent.TipReceived ev = (TipEvent.TipReceived) captor.getValue();
        assertThat(ev.tokenAmount()).isEqualTo(100L);
        assertThat(ev.senderDisplayName()).isEqualTo("Viewer123");
    }

    // ── idempotency ───────────────────────────────────────────────────────────

    @Test
    void sendTip_duplicateKey_returnsExistingTip() {
        String key = "key-dup";
        Tip existing = new Tip();
        ReflectionTestUtils.setField(existing, "id", UUID.randomUUID());
        existing.setStatus(TipStatus.COMPLETED);

        when(tipRepo.findByIdempotencyKey(key)).thenReturn(Optional.of(existing));

        Tip result = tipService.sendTip(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), 50L, null, null, key, null);

        assertThat(result).isSameAs(existing);
        verify(walletClient, never()).transfer(any(), any(), anyLong(), any(), any());
        verify(eventPublisher, never()).publish(any());
    }

    // ── insufficient funds ────────────────────────────────────────────────────

    @Test
    void sendTip_insufficientFunds_marksTipFailed() {
        UUID senderId = UUID.randomUUID();
        String key    = "key-broke";

        when(tipRepo.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(tipRepo.save(any())).thenAnswer(inv -> {
            Tip t = inv.getArgument(0);
            ReflectionTestUtils.setField(t, "id", UUID.randomUUID());
            return t;
        });
        doThrow(new InsufficientTokensException(senderId))
                .when(walletClient).transfer(any(), any(), anyLong(), any(), any());

        assertThatThrownBy(() ->
                tipService.sendTip(senderId, UUID.randomUUID(), UUID.randomUUID(),
                        1000L, null, null, key, null))
                .isInstanceOf(InsufficientTokensException.class);

        // Tip should be saved twice: once PENDING, once FAILED
        verify(tipRepo, times(2)).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    // ── tip-menu item validation ──────────────────────────────────────────────

    @Test
    void sendTip_menuItemPriceMismatch_throwsIllegalArgument() {
        UUID senderId    = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        UUID menuItemId  = UUID.randomUUID();

        TipMenuItem item = new TipMenuItem();
        ReflectionTestUtils.setField(item, "id", menuItemId);
        item.setBroadcasterId(recipientId);
        item.setTokenPrice(50L);
        item.setActive(true);

        when(tipRepo.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(menuItemRepo.findById(menuItemId)).thenReturn(Optional.of(item));

        // Viewer sends 100 but the menu item costs 50
        assertThatThrownBy(() ->
                tipService.sendTip(senderId, recipientId, UUID.randomUUID(),
                        100L, null, menuItemId, "key-mismatch", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("menu item price");
    }

    @Test
    void sendTip_menuItemNotFound_throws() {
        UUID menuItemId = UUID.randomUUID();
        when(tipRepo.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(menuItemRepo.findById(menuItemId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                tipService.sendTip(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        50L, null, menuItemId, "key-nf", null))
                .isInstanceOf(MenuItemNotFoundException.class);
    }

    // ── goal progress ─────────────────────────────────────────────────────────

    @Test
    void sendTip_withActiveGoal_updatesProgressAndPublishesGoalUpdated() {
        UUID recipientId = UUID.randomUUID();
        UUID roomId      = UUID.randomUUID();
        String key       = "key-goal";

        TipGoal goal = TipGoal.create(recipientId, roomId, "Road to 1000", 1000L);
        ReflectionTestUtils.setField(goal, "id", UUID.randomUUID());

        when(tipRepo.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(tipRepo.save(any())).thenAnswer(inv -> {
            Tip t = inv.getArgument(0);
            ReflectionTestUtils.setField(t, "id", UUID.randomUUID());
            return t;
        });
        when(walletClient.transfer(any(), any(), anyLong(), any(), any())).thenReturn(null);
        when(goalRepo.findActiveGoalForUpdate(recipientId)).thenReturn(Optional.of(goal));
        when(goalRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        tipService.sendTip(UUID.randomUUID(), recipientId, roomId, 200L,
                null, null, key, null);

        assertThat(goal.getCurrentTokens()).isEqualTo(200L);
        assertThat(goal.getStatus()).isEqualTo(GoalStatus.ACTIVE);

        // Should publish TipReceived + TipGoalUpdated (2 events, goal not yet complete)
        verify(eventPublisher, times(2)).publish(any());
    }

    @Test
    void sendTip_goalReached_publishesGoalCompleted() {
        UUID recipientId = UUID.randomUUID();
        UUID roomId      = UUID.randomUUID();
        String key       = "key-complete";

        TipGoal goal = TipGoal.create(recipientId, roomId, "Final push", 100L);
        ReflectionTestUtils.setField(goal, "id", UUID.randomUUID());
        goal.addProgress(50L); // 50/100 already

        when(tipRepo.findByIdempotencyKey(key)).thenReturn(Optional.empty());
        when(tipRepo.save(any())).thenAnswer(inv -> {
            Tip t = inv.getArgument(0);
            ReflectionTestUtils.setField(t, "id", UUID.randomUUID());
            return t;
        });
        when(walletClient.transfer(any(), any(), anyLong(), any(), any())).thenReturn(null);
        when(goalRepo.findActiveGoalForUpdate(recipientId)).thenReturn(Optional.of(goal));
        when(goalRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        tipService.sendTip(UUID.randomUUID(), recipientId, roomId, 50L,
                null, null, key, null);

        assertThat(goal.getStatus()).isEqualTo(GoalStatus.COMPLETED);

        // TipReceived + TipGoalUpdated + TipGoalCompleted = 3 events
        verify(eventPublisher, times(3)).publish(any());
    }
}
