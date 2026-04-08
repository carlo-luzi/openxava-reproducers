# ElementCollection ghost rows bug — OpenXava 7.7

Minimal reproducer for a bug in OpenXava 7.7 where changing a date field
causes ghost (empty) rows to appear in an `@ElementCollection` grid,
accompanied by the error **"No correct reaction to property change"**.

## Steps to reproduce

1. Build and start the app:
   ```
   mvn package
   mvn exec:java
   ```
2. Open http://localhost:8080/elemcol and sign in (admin / admin)
3. Navigate to the **Record** module
4. Fill in Title, add a TimeSlot (Start + End), and **Save**
5. Change the **Record date** field using the **date picker widget**
   (clicking the calendar icon, not typing directly)
6. **Result:** ghost empty rows appear in the Time Slots grid, and
   an error banner "No correct reaction to property change" is shown

## Environment

- OpenXava 7.7 (archetype-generated project, no customizations)
- Java 17+ (tested with 21)
- HSQLDB (embedded, in-memory)
- Tested on Chrome and Firefox (desktop and mobile)

## Root cause

The bug is in `dateCalendarEditor.js`, line 135.

In the flatpickr `onClose` handler, when the date value changes, the code
triggers a `change` event using a **global selector**:

```javascript
$('.xava_date > input').change();   // BUG: fires on ALL date inputs in the page
```

This fires the `change` event on **every** date input in the page — including
the `@Stereotype("DATETIME")` inputs in the empty pre-rendered rows of the
`@ElementCollection` grid. These spurious change events are sent to the server
as `xava_changed_property` (e.g. `timeSlots.2.end`), which causes
`View.collectionEditingRow` to be set to a row index that doesn't exist
in `collectionValues`, leading to `IndexOutOfBoundsException` in
`View.moveViewValuesToCollectionValues()`.

### Why automated tests don't catch it

Playwright / HtmlUnit tests that use `fill()` + keyboard Tab to change a date
bypass the flatpickr widget entirely — the `onClose` callback is never invoked,
so the global `change()` never fires. The bug only manifests through real
browser interaction with the flatpickr calendar widget.

## Fix

Replace the global selector with the instance-specific input reference
(line 135 of `dateCalendarEditor.js`):

```javascript
// Before (bug):
$('.xava_date > input').change();

// After (fix):
$(instance.input).change();
```

The `instance.input` variable is already available in the `onClose` callback
scope and is used correctly in the lines immediately above (133–134).
The `onChange` handler (lines 102–126) also uses `$(instance.input)`
exclusively — the global selector in `onClose` appears to be an oversight.

This one-line change is sufficient to resolve the issue.

## Model structure (minimal reproduction)

The entity setup that triggers the bug requires:

1. **A date field** — any `Date` property (serves as the field the user changes)
2. **A calculated property with `@Depends`** on the date field — causes OX to
   fire a `propertyChanged` event when the date changes
3. **An `@ElementCollection`** of an `@Embeddable` that contains
   `@Stereotype("DATETIME")` fields — these are the date inputs that receive
   the spurious `change()` event from the global selector

```
Record (@Entity)
├── recordDate (Date)
├── dayLabel (@Depends("recordDate"))  ← triggers property change
└── timeSlots (@ElementCollection)
    └── TimeSlot (@Embeddable)
        ├── start (@Stereotype("DATETIME"))  ← spurious change target
        ├── end (@Stereotype("DATETIME"))    ← spurious change target
        ├── excluded (boolean)
        └── hours (@Depends("start, end"))
```

## Automated E2E test

A Playwright test reproduces the bug by interacting with the flatpickr widget:

```
mvn package
mvn exec:java &                        # start the app in background
mvn test -DskipTests=false             # run the E2E test (expects app on :8080)
```

The test clicks the calendar icon (not direct input), which triggers flatpickr's
`onClose` → global `$('.xava_date > input').change()` → ghost rows.

The test **fails** (as expected) — confirming the bug is reproducible:
```
Ghost rows appeared after changing recordDate via flatpickr! Before: 2, After: 4
```

Note: the previous approach of using `page.fill()` + Tab to change the date
did NOT reproduce the bug, because it bypasses flatpickr entirely.

## Project structure

```
src/main/java/com/example/elemcol/
├── model/
│   ├── Record.java       — Entity with @Depends + @ElementCollection
│   └── TimeSlot.java     — @Embeddable with DATETIME fields
└── run/
    ├── elemcol.java       — Entry point
    └── DBManager.java     — HSQLDB manager
src/test/java/com/example/elemcol/
    └── ElementCollectionBugTest.java  — Playwright E2E test (reproduces the bug)
```
