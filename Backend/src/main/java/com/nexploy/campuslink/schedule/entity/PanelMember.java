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
@Table(name = "panel_members")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PanelMember {

    @Id
    private String id;

    private String panelId;
    private String memberName;
    private String memberEmail;
}
