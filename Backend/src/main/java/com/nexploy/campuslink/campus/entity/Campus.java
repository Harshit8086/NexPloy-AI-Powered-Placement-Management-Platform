package com.nexploy.campuslink.campus.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "campuses")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Campus {

    @Id
    private String id;

    private String name;
    private String city;
    private String state;
    private boolean active;
}
