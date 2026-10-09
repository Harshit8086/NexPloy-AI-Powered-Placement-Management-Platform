package com.nexploy.campuslink.campus.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexploy.campuslink.campus.entity.Campus;
import org.junit.jupiter.api.Test;

/**
 * CampusResponse carries campus identity only (id/name/city/state/active). Campus metrics
 * (student counts, placement rates, CTC, etc.) are analytics aggregations served by the
 * per-campus dashboard endpoint, NOT campus columns — so there are no derived fields to map
 * here. These tests pin the identity mapping so it cannot silently drift.
 */
class CampusResponseTest {

    @Test
    void mapsAllIdentityFieldsAndActiveFlagFromEntity() {
        Campus campus = Campus.builder()
            .id("cmp_main")
            .name("Main Campus")
            .city("Bengaluru")
            .state("Karnataka")
            .active(true)
            .build();

        CampusResponse response = CampusResponse.from(campus);

        assertThat(response.id()).isEqualTo("cmp_main");
        assertThat(response.name()).isEqualTo("Main Campus");
        assertThat(response.city()).isEqualTo("Bengaluru");
        assertThat(response.state()).isEqualTo("Karnataka");
        assertThat(response.active()).isTrue();
    }

    @Test
    void preservesInactiveFlagAndDoesNotFabricateMissingIdentityFields() {
        Campus campus = Campus.builder()
            .id("cmp_city")
            .name("City Campus")
            .active(false)
            .build();

        CampusResponse response = CampusResponse.from(campus);

        assertThat(response.active()).isFalse();
        // Nulls stay null — the DTO never invents city/state the entity does not hold.
        assertThat(response.city()).isNull();
        assertThat(response.state()).isNull();
    }
}
