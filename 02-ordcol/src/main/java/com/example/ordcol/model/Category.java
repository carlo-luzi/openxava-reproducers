package com.example.ordcol.model;

import javax.persistence.*;

import org.openxava.annotations.*;
import org.openxava.model.*;

/**
 * Second reference for Task — needed to reproduce the
 * collectionTotals.jsp bug with 2+ references.
 */
@Entity
@Table(name = "ordcol_category")
public class Category extends Identifiable {

    @Required @Column(length = 100)
    private String name;
    public String getName() { return name; }
    public void setName(String n) { this.name = n; }
}
