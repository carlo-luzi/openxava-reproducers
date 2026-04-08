package com.example.elemcol.model;

import java.text.SimpleDateFormat;
import java.util.*;

import javax.persistence.*;

import org.openxava.annotations.*;
import org.openxava.model.*;

/**
 * Minimal entity to reproduce the ElementCollection ghost rows bug in OX 7.7.
 *
 * Key ingredients:
 * 1. A date field with @DefaultValueCalculator
 * 2. A calculated property with @Depends on that date field
 * 3. An @ElementCollection of an @Embeddable that has @Depends and a boolean
 *
 * Bug: when the user changes the date field in the browser, the @Depends
 * property change propagates into the ElementCollection view, corrupting
 * View.collectionEditingRow. This causes ghost (empty) rows to appear
 * in the collection grid, and the error message:
 * "No correct reaction to property change"
 */
@Entity
@Tab(properties = "title, recordDate, dayLabel")
@View(members =
    "title ;" +
    "recordDate, dayLabel ;" +
    "timeSlots"
)
public class Record extends Identifiable {

    @Column(length = 100) @Required
    private String title;
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    @Required
    @DefaultValueCalculator(org.openxava.calculators.CurrentDateCalculator.class)
    private Date recordDate;
    public Date getRecordDate() { return recordDate; }
    public void setRecordDate(Date recordDate) { this.recordDate = recordDate; }

    @Depends("recordDate") @Column(length = 40)
    public String getDayLabel() {
        if (getRecordDate() == null) return "";
        return new SimpleDateFormat("EEEE d MMMM yyyy").format(getRecordDate());
    }

    @ElementCollection @OrderBy("start ASC")
    @CollectionTable(name = "record_timeslots",
            joinColumns = @JoinColumn(name = "record_id"))
    @ListProperties("start, end, description, excluded, hours")
    private List<TimeSlot> timeSlots;
    public List<TimeSlot> getTimeSlots() { return timeSlots; }
    public void setTimeSlots(List<TimeSlot> timeSlots) { this.timeSlots = timeSlots; }
}
