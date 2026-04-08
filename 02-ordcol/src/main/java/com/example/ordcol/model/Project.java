package com.example.ordcol.model;

import java.util.List;

import javax.persistence.*;

import org.openxava.annotations.*;
import org.openxava.model.*;

/**
 * Minimal reproducer: @OrderColumn + column summation (+) misalignment.
 *
 * BUG: the sum indicated by "hours+" in @ListProperties renders under the
 * "weight" column instead of the "hours" column when @OrderColumn is present.
 * Removing @OrderColumn (and using @OrderBy instead) makes the sum render
 * in the correct column.
 */
@Entity
@Table(name = "ordcol_project")
@View(members = "name ; tasks")
public class Project extends Identifiable {

    @Required @Column(length = 100)
    private String name;
    public String getName() { return name; }
    public void setName(String n) { this.name = n; }

    @OneToMany(mappedBy = "project")
    @OrderColumn(name = "task_order")
    @ListProperties("description, weight, hours+")
    @AsEmbedded
    private List<Task> tasks;
    public List<Task> getTasks() { return tasks; }
    public void setTasks(List<Task> t) { this.tasks = t; }
}
