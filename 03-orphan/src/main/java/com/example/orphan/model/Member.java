package com.example.orphan.model;

import javax.persistence.*;

import org.openxava.annotations.*;
import org.openxava.model.*;

/**
 * Child entity with @ManyToOne(optional=false) back to Team.
 *
 * Deletable from its own standalone module (the standard CRUD delete).
 */
@Entity
@Table(name = "orphan_member")
@View(members = "name ; role ; team")
public class Member extends Identifiable {

    @Required @Column(length = 100)
    private String name;
    public String getName() { return name; }
    public void setName(String n) { this.name = n; }

    @Column(length = 100)
    private String role;
    public String getRole() { return role; }
    public void setRole(String r) { this.role = r; }

    @ManyToOne(optional = false)
    @JoinColumn(foreignKey = @ForeignKey(name = "fk_orphan_member__team"))
    private Team team;
    public Team getTeam() { return team; }
    public void setTeam(Team t) { this.team = t; }
}
