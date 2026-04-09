package com.example.orphan.model;

import java.util.List;

import javax.persistence.*;

import org.openxava.annotations.*;
import org.openxava.model.*;

/**
 * Parent entity with @OrderColumn + orphanRemoval on the children collection.
 *
 * BUG: deleting a Member from the standalone Member module triggers a recursive
 * loop in MapFacadeBean.remove() → updateSortableCollectionsOnRemove() →
 * removeCollectionElement(deletingElement=true) → orphanRemoval → remove() → ...
 */
@Entity
@Table(name = "orphan_team")
@View(members = "name ; members")
public class Team extends Identifiable {

    @Required @Column(length = 100)
    private String name;
    public String getName() { return name; }
    public void setName(String n) { this.name = n; }

    @OneToMany(mappedBy = "team", orphanRemoval = true)
    @OrderColumn(name = "member_order")
    @ListProperties("name, role")
    @AsEmbedded
    private List<Member> members;
    public List<Member> getMembers() { return members; }
    public void setMembers(List<Member> m) { this.members = m; }
}
