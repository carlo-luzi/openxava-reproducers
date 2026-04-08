package com.example.elemcol.model;

import java.math.BigDecimal;
import java.sql.Timestamp;

import javax.persistence.*;

import org.openxava.annotations.*;

/**
 * Embeddable with a calculated property (@Depends) and a boolean field.
 *
 * This is the minimal setup to trigger the ghost rows bug in OX 7.7
 * when a parent property change propagates into the ElementCollection.
 */
@Embeddable
public class TimeSlot {

    @Stereotype("DATETIME") @Required
    private Timestamp start;
    public Timestamp getStart() { return start; }
    public void setStart(Timestamp start) { this.start = start; }

    @Stereotype("DATETIME")
    private Timestamp end;
    public Timestamp getEnd() { return end; }
    public void setEnd(Timestamp end) { this.end = end; }

    @Column(length = 200)
    private String description;
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    private boolean excluded;
    public boolean isExcluded() { return excluded; }
    public void setExcluded(boolean excluded) { this.excluded = excluded; }

    @Column(precision = 4, scale = 2)
    @Depends("start, end")
    public BigDecimal getHours() {
        if (getStart() == null || getEnd() == null) return BigDecimal.ZERO;
        return new BigDecimal((getEnd().getTime() - getStart().getTime()) / 3600000.0);
    }
}
