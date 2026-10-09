package com.nexploy.campuslink.schedule.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "panels")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Panel {

    @Id
    private String id;

    private String campusId;
    private String name;
    private boolean active;
}
