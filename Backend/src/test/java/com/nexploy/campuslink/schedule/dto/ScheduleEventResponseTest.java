package com.nexploy.campuslink.schedule.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexploy.campuslink.schedule.entity.ActivityType;
import com.nexploy.campuslink.schedule.entity.ParticipantMode;
import com.nexploy.campuslink.schedule.entity.ScheduleEvent;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

/**
 * ScheduleEventResponse enriches a raw event with the resolved venue/panel display names so the
 * calendar renders without a second round-trip. All other fields pass through from the entity
 * (real timestamps, capacities, conflict flag). These tests pin that mapping and, critically, that
 * an unresolved venue/panel name stays {@code null} rather than being fabricated.
 */
class ScheduleEventResponseTest {

    @Test
    void mapsEntityFieldsAndResolvedNames() {
        OffsetDateTime start = OffsetDateTime.parse("2026-09-22T10:00:00Z");
        OffsetDateTime end = OffsetDateTime.parse("2026-09-22T12:00:00Z");
        ScheduleEvent event = ScheduleEvent.builder()
            .id("evt_technova")
            .driveId("drv_technova")
            .campusId("cmp_main")
            .venueId("ven_lab2")
            .panelId("pnl_a")
            .startsAt(start)
            .endsAt(end)
            .expectedParticipants(38)
            .venueCapacity(80)
            .conflict(true)
            .activityType(ActivityType.APTITUDE_TEST)
            .participantMode(ParticipantMode.BATCH)
            .participantCount(38)
            .build();

        ScheduleEventResponse response = ScheduleEventResponse.from(event, "Lab 2", "Panel A");

        assertThat(response.id()).isEqualTo("evt_technova");
        assertThat(response.driveId()).isEqualTo("drv_technova");
        assertThat(response.campusId()).isEqualTo("cmp_main");
        assertThat(response.venueId()).isEqualTo("ven_lab2");
        assertThat(response.venueName()).isEqualTo("Lab 2");
        assertThat(response.panelId()).isEqualTo("pnl_a");
        assertThat(response.panelName()).isEqualTo("Panel A");
        assertThat(response.startsAt()).isEqualTo(start);
        assertThat(response.endsAt()).isEqualTo(end);
        assertThat(response.expectedParticipants()).isEqualTo(38);
        assertThat(response.venueCapacity()).isEqualTo(80);
        assertThat(response.conflict()).isTrue();
        assertThat(response.activityType()).isEqualTo(ActivityType.APTITUDE_TEST);
        assertThat(response.participantMode()).isEqualTo(ParticipantMode.BATCH);
        assertThat(response.participantCount()).isEqualTo(38);
    }

    @Test
    void doesNotFabricateNamesForUnresolvedResources() {
        ScheduleEvent event = ScheduleEvent.builder()
            .id("evt_orphan")
            .driveId("drv_x")
            .campusId("cmp_main")
            .venueId("ven_missing")
            .panelId("pnl_missing")
            .startsAt(OffsetDateTime.parse("2026-09-23T09:00:00Z"))
            .endsAt(OffsetDateTime.parse("2026-09-23T11:00:00Z"))
            .conflict(false)
            .build();

        // A venue/panel id that no longer resolves yields a null name — never a placeholder.
        ScheduleEventResponse response = ScheduleEventResponse.from(event, null, null);

        assertThat(response.venueId()).isEqualTo("ven_missing");
        assertThat(response.venueName()).isNull();
        assertThat(response.panelId()).isEqualTo("pnl_missing");
        assertThat(response.panelName()).isNull();
        assertThat(response.conflict()).isFalse();
    }
}
