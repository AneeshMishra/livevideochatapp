package com.platform.broadcaster.service;

import com.platform.broadcaster.api.dto.*;
import com.platform.broadcaster.domain.*;
import com.platform.broadcaster.event.BroadcasterEventPublisher;
import com.platform.broadcaster.exception.BroadcasterNotFoundException;
import com.platform.broadcaster.exception.DuplicateBroadcasterException;
import com.platform.broadcaster.repository.BroadcasterRepository;
import com.platform.broadcaster.repository.GeoBlockRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BroadcasterServiceTest {

    @Mock BroadcasterRepository broadcasterRepository;
    @Mock GeoBlockRuleRepository geoBlockRuleRepository;
    @Mock BroadcasterEventPublisher eventPublisher;

    @InjectMocks BroadcasterService service;

    private UUID userId;
    private Broadcaster broadcaster;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        broadcaster = new Broadcaster();
        broadcaster.setUserId(userId);
        broadcaster.setDisplayName("TestModel");
        broadcaster.setKycStatus(KycStatus.APPROVED);
        broadcaster.setStatus(BroadcasterStatus.OFFLINE);
        StreamSettings settings = new StreamSettings();
        settings.setBroadcaster(broadcaster);
        broadcaster.setStreamSettings(settings);
    }

    @Test
    void register_succeeds_for_new_user() {
        when(broadcasterRepository.existsByUserId(userId)).thenReturn(false);
        when(broadcasterRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateBroadcasterRequest req = new CreateBroadcasterRequest(userId, null, "TestModel", "Hello");
        Broadcaster result = service.register(req);

        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getDisplayName()).isEqualTo("TestModel");
        assertThat(result.getKycStatus()).isEqualTo(KycStatus.PENDING);
        assertThat(result.getStreamSettings()).isNotNull();
        verify(broadcasterRepository).save(any());
    }

    @Test
    void register_throws_for_duplicate_userId() {
        when(broadcasterRepository.existsByUserId(userId)).thenReturn(true);

        CreateBroadcasterRequest req = new CreateBroadcasterRequest(userId, null, "TestModel", null);
        assertThatThrownBy(() -> service.register(req))
            .isInstanceOf(DuplicateBroadcasterException.class);
        verify(broadcasterRepository, never()).save(any());
    }

    @Test
    void changeStatus_to_online_requires_approved_kyc() {
        broadcaster.setKycStatus(KycStatus.PENDING);
        UUID id = UUID.randomUUID();
        when(broadcasterRepository.findById(id)).thenReturn(Optional.of(broadcaster));

        assertThatThrownBy(() -> service.changeStatus(id, BroadcasterStatus.ONLINE))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("KYC status is PENDING");
        verify(broadcasterRepository, never()).save(any());
    }

    @Test
    void changeStatus_to_online_succeeds_when_kyc_approved() {
        UUID id = UUID.randomUUID();
        when(broadcasterRepository.findById(id)).thenReturn(Optional.of(broadcaster));
        when(broadcasterRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Broadcaster result = service.changeStatus(id, BroadcasterStatus.ONLINE);

        assertThat(result.getStatus()).isEqualTo(BroadcasterStatus.ONLINE);
        verify(eventPublisher).publish(any());
    }

    @Test
    void changeStatus_to_offline_does_not_check_kyc() {
        broadcaster.setKycStatus(KycStatus.PENDING);
        broadcaster.setStatus(BroadcasterStatus.ONLINE);
        UUID id = UUID.randomUUID();
        when(broadcasterRepository.findById(id)).thenReturn(Optional.of(broadcaster));
        when(broadcasterRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Broadcaster result = service.changeStatus(id, BroadcasterStatus.OFFLINE);
        assertThat(result.getStatus()).isEqualTo(BroadcasterStatus.OFFLINE);
    }

    @Test
    void getById_throws_not_found_for_missing_id() {
        UUID id = UUID.randomUUID();
        when(broadcasterRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(id))
            .isInstanceOf(BroadcasterNotFoundException.class);
    }

    @Test
    void updateProfile_publishes_event() {
        UUID id = UUID.randomUUID();
        when(broadcasterRepository.findById(id)).thenReturn(Optional.of(broadcaster));
        when(broadcasterRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.updateProfile(id, new UpdateBroadcasterRequest("NewName", null, null));

        verify(eventPublisher).publish(argThat(e ->
            e instanceof com.platform.broadcaster.event.BroadcasterEvent.ProfileUpdated));
    }

    @Test
    void addGeoBlockRule_rejects_duplicate_country() {
        UUID id = UUID.randomUUID();
        when(broadcasterRepository.findById(id)).thenReturn(Optional.of(broadcaster));
        when(geoBlockRuleRepository.existsByBroadcasterIdAndCountryCode(id, "US")).thenReturn(true);

        assertThatThrownBy(() ->
            service.addGeoBlockRule(id, new AddGeoBlockRuleRequest("US", GeoBlockType.BLOCK)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("US");
    }
}
