package com.example.ordcol.model;

import java.math.BigDecimal;

import javax.persistence.*;

import org.openxava.annotations.*;
import org.openxava.model.*;

/**
 * Child entity for @OrderColumn + summation reproducer.
 */
@Entity
@Table(name = "ordcol_task")
@View(members = "description; weight; hours; category")
public class Task extends Identifiable {

    @Required @Column(length = 200)
    private String description;
    public String getDescription() { return description; }
    public void setDescription(String d) { this.description = d; }

    private int weight;
    public int getWeight() { return weight; }
    public void setWeight(int w) { this.weight = w; }

    @Column(precision = 8, scale = 2)
    private BigDecimal hours;
    public BigDecimal getHours() { return hours; }
    public void setHours(BigDecimal h) { this.hours = h; }

    @ManyToOne(optional = false)
    @JoinColumn(foreignKey = @ForeignKey(name = "fk_ordcol_task__project"))
    private Project project;
    public Project getProject() { return project; }
    public void setProject(Project p) { this.project = p; }

    // Second reference: reproduces the collectionTotals.jsp bug
    // (mpListSize = metaPropertiesList.size() - keyPropertiesList.size()
    //  with 2 references, mpListSize = 3-2 = 1, and c>=1 becomes hidden)
    @ManyToOne(optional = true)
    @JoinColumn(foreignKey = @ForeignKey(name = "fk_ordcol_task__category"))
    private Category category;
    public Category getCategory() { return category; }
    public void setCategory(Category c) { this.category = c; }
}
