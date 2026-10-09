package com.nexploy.campuslink.placement.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A campus-scoped placement cycle (an academic year / season). Drives, applications,
 * offers and the AI snapshots reference it so analytics can group by period.
 */
@Entity
@Table(name = "placement_cycles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlacementCycle {

    @Id
    private String id;

    private String campusId;
    private String name;
    private String academicYear;
    private LocalDate startsOn;
    private LocalDate endsOn;
    private String status;
}
