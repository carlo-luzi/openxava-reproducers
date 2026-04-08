# Collection totals misalignment bug — OpenXava 7.7

Minimal reproducer for a bug in OpenXava 7.7 where the column summation
(`+` in `@ListProperties`) renders under the **wrong column** when the
collection is rendered via `collectionFromModel.jsp` (triggered by
`@OrderColumn`) and the child entity has **2 or more `@ManyToOne`
references** whose keys are not in `@ListProperties`.

## Steps to reproduce

1. Build and start the app:
   ```
   mvn package
   mvn exec:java
   ```
2. Open http://localhost:8081/ordcol and sign in (admin / admin)
3. Navigate to the **Project** module
4. Fill in **Name** and **Save**
5. Add 2–3 **Tasks** (via the collection "New" button) with
   Description, Weight, and Hours values, saving each one
6. **Result:** with data, the misalignment becomes obvious — the sum of Hours
   appears visually under the **Weight** column instead of the **Hours** column

## Environment

- OpenXava 7.7 (archetype-generated project, no customizations)
- Java 17+ (tested with 21)
- HSQLDB (embedded, in-memory)

## Root cause

The bug is in `collectionTotals.jsp`, lines 28–32 and 64.

### The calculation (lines 28–32)

```java
List<MetaProperty> keyPropertiesList = subview.getKeyPropertiesOfReferencesEntity();
int mpListSize = 0;
if (!keyPropertiesList.isEmpty()) {
    mpListSize = subview.getMetaPropertiesList().size() - keyPropertiesList.size();
}
```

`getKeyPropertiesOfReferencesEntity()` returns hidden key properties for
**all** `@ManyToOne` references whose keys are not in `@ListProperties`.
With 2 references (e.g. `project` + `category`), it returns 2 keys.

`getMetaPropertiesList()` returns only the 3 visible properties
(`description`, `weight`, `hours`).

So: `mpListSize = 3 - 2 = 1`.

### The hidden check (line 64)

```jsp
<td class="ox-total-capable-cell"
    <%=(mpListSize>0 && c>=mpListSize) ? "hidden" : ""%>>
```

For `c=1` (the Weight column): `1 >= 1` → `true` → **`hidden`**.

The Weight total-capable cell gets `display: none` (width = 0), which
shifts the Hours total cell one position to the left — visually aligning
it with the Weight header instead of the Hours header.

### Why it only happens with `collectionFromModel.jsp`

`collectionTotals.jsp` is used only when the collection is rendered via
`collectionFromModel.jsp`. This rendering path is activated when
`View.isCollectionFromModel()` returns `true`, which happens when
the collection has `@OrderColumn` (making it "sortable").

Without `@OrderColumn`, the collection uses `listEditor.jsp`, which has
its own totals rendering (lines 522–586) that does **not** use
`getKeyPropertiesOfReferencesEntity()` or the `hidden` check — so the
totals always align correctly.

### Conditions to trigger the bug

All three must be true:
1. The collection uses `collectionFromModel.jsp` (via `@OrderColumn`,
   or a calculated collection, or custom list actions disabled)
2. The child entity has **2 or more** `@ManyToOne` references
3. Those references' key properties are **not** in `@ListProperties`

With only 1 reference, `mpListSize = 3 - 1 = 2`, and no visible column
gets hidden (`c=1` → `1 >= 2` → false). With 2+ references the
threshold drops and visible columns start being hidden.

## Model structure (minimal reproduction)

```
Project (@Entity)
└── tasks (@OneToMany, @OrderColumn, @AsEmbedded)
    @ListProperties("description, weight, hours+")
    └── Task (@Entity)
        ├── description (String)
        ├── weight (int)
        ├── hours (BigDecimal)          ← sum here
        ├── project (@ManyToOne)        ← reference #1
        └── category (@ManyToOne)       ← reference #2 — triggers the bug
```

Removing the `category` reference (leaving only 1 `@ManyToOne`) makes
the totals align correctly.

## Fix

The fix is a one-line change in `collectionTotals.jsp`, line 30–31.

The `mpListSize` calculation should only apply to `@ElementCollection`,
where hidden key properties of references are appended to the property
list and must be excluded from the visible column count. For `@OneToMany`
collections, references are managed by JPA through foreign keys and their
key properties are never rendered as columns — so `mpListSize` must stay 0.

```jsp
<%-- Before (bug): --%>
if (!keyPropertiesList.isEmpty()) {
    mpListSize = subview.getMetaPropertiesList().size() - keyPropertiesList.size();
}

<%-- After (fix): --%>
if (!keyPropertiesList.isEmpty() && elementCollection) {
    mpListSize = subview.getMetaPropertiesList().size() - keyPropertiesList.size();
}
```

The `elementCollection` variable is already available at line 24:
```java
boolean elementCollection = subview.isRepresentsElementCollection();
```

This fix has been verified: after applying the override, the E2E test
passes both structurally and visually (total at X=525, Hours header at
X=525, distance = 0px).

## Automated E2E test

A Playwright test reproduces the bug by creating a Project with Tasks and
checking both structural (cell index) and visual (X coordinate) alignment:

```
mvn package
mvn exec:java &                        # start the app in background
mvn test -DskipTests=false \
    -Dtest=OrderColumnSummationBugTest#summationAlignmentWithData
```

The test **fails** (as expected) — confirming the visual misalignment:
```
Visual: summation total ('42.00') renders at X=471
  which is closer to Weight (X=471, dist=0px)
  than to Hours (X=533, dist=62px).
  The total-capable cell for Weight is hidden (width=0),
  collapsing the total under the wrong column.
```

## Project structure

```
src/main/java/com/example/ordcol/
├── model/
│   ├── Project.java      — Parent with @OneToMany @OrderColumn
│   ├── Task.java          — Child with 2 @ManyToOne references
│   └── Category.java      — Second reference (triggers the bug)
└── run/
    ├── ordcol.java        — Entry point
    └── DBManager.java     — HSQLDB manager
src/test/java/com/example/ordcol/
├── OrderColumnSummationBugTest.java  — Playwright E2E (detects the bug)
└── ColClassDiagTest.java             — DOM diagnostic (dumps _col classes)
```
