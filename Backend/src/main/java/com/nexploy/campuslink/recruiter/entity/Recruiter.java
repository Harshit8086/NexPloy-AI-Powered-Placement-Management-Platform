package com.nexploy.campuslink.recruiter.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "recruiters")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Recruiter {

    @Id
    private String id;

    private String campusId;
    private String name;
    private String companyName;
    private String email;
    private String phone;
    private String companyWebsite;
    private String companyIndustry;
    private String companyLocation;
    private String designation;
    private boolean active;

    // Self-service profile fields (added in V32). All nullable.
    private String logoInitial;
    @Column(columnDefinition = "text")
    private String companyDescription;
    private String companySize;
    private String companyLinkedin;
    private String companyTwitter;
    private String companyGithub;
}
