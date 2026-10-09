package com.nexploy.campuslink.schedule.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "schedule_optimization_runs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OptimizationRun {

    @Id
    private String id;

    private String scheduleVersion;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime approvedAt;
    private OffsetDateTime rejectedAt;
    private String rejectedReason;

    @Column(columnDefinition = "text", nullable = false)
    private String resultJson;
}
