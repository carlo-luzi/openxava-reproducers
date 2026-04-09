# Recursive loop in MapFacade.remove() with @OrderColumn + orphanRemoval — OpenXava 7.7

Minimal reproducer for a bug in OpenXava 7.7 where deleting a child entity
from its standalone module causes a **recursive loop** in
`MapFacadeBean.remove()` when the parent collection uses both
`@OrderColumn` and `orphanRemoval = true`.

## Steps to reproduce

1. Build and start the app:
   ```
   mvn clean package
   mvn exec:java
   ```
2. Open http://localhost:8080/orphan and sign in (admin / admin)
3. Navigate to the **Team** module
4. Create a Team (fill Name, Save)
5. Go back to the list, reopen the Team (the collection is visible after reload)
6. Add 2 Members to the Team via the collection (New button), saving each one
7. Navigate to the **Member** module
8. Open one of the two Members from the list
9. Click **Delete** and confirm
10. **Result:** `java.lang.StackOverflowError` — the application enters
    an infinite recursive loop in `MapFacadeBean.remove()`

## Expected behavior

Only the selected Member should be deleted. The sibling should remain intact.

## Environment

- OpenXava 7.7 (archetype-generated project, no customizations)
- Java 17+ (tested with 21)
- HSQLDB (embedded, in-memory)

## Root cause

The recursive loop originates in `MapFacadeBean`:

```
MapFacade.remove("Member", {id: B})
  → MapFacadeBean.remove()
    → updateSortableCollectionsOnRemove()          [line 1605]
      iterates references of Member → finds "team"
      → removeCollectionElement(Team, members, B, deletingElement=true)  [line 1123]
        → removeFromJavaCollection()                 collection: 2→1 (removes B)
        → isOrphanRemoval=true → remove(Member, B)  [line 625]
          → MapFacadeBean.remove()                   RECURSIVE CALL on same entity
            → updateSortableCollectionsOnRemove()    re-iterates references
              → removeCollectionElement(Team, members, B, deletingElement=true)
                → removeFromJavaCollection()         collection: 1→1 (B not found, no-op)
                → isOrphanRemoval=true → remove(Member, B)  RECURSIVE AGAIN
                  → ... loop continues until entity not found
```

The loop occurs because:
1. `updateSortableCollectionsOnRemove` calls `removeCollectionElement` with
   `deletingElement=true` for sortable collections
2. When `orphanRemoval=true`, `removeCollectionElement` (line 624-625) calls
   `remove()` on the child instead of `setValues(null)`
3. `remove()` calls `updateSortableCollectionsOnRemove` again on the same entity
4. → infinite recursion

Without `orphanRemoval`, the code takes the `else` branch (line 627-631) which
calls `setValues(null)` to disassociate the child — no recursion.

### Side effect: sibling deletion

During the recursive loop, Hibernate's `orphanRemoval` mechanism tracks entities
removed from the Java collection. The repeated manipulation of the collection
may cause Hibernate to mark **all** collection elements as orphans, resulting
in deletion of sibling entities that should not be affected.

## Conditions to trigger

All three must be true:
1. The parent collection uses `@OrderColumn` (making it "sortable" in OX terms)
2. The parent collection uses `orphanRemoval = true`
3. The child entity is deleted from its **standalone module** (CRUD delete),
   not from the parent's collection UI

## Model structure (minimal reproduction)

```
Team (@Entity)
└── members (@OneToMany, @OrderColumn, orphanRemoval=true, @AsEmbedded)
    @ListProperties("name, role")
    └── Member (@Entity)
        ├── name (String, @Required)
        ├── role (String)
        └── team (@ManyToOne, optional=false)
```

## Automated E2E test

The included JUnit test (`OrphanRemovalBugTest`) automates the reproduction:

```bash
mvn clean package
mvn exec:java &                    # start the app in background
sleep 12                           # wait for startup
mvn test -DskipTests=false -Dtest=OrphanRemovalBugTest
```

**Without the fix:** the test fails with `java.lang.StackOverflowError`.

**With the fix** (see below): the test passes — only the target Member is
deleted, the sibling survives.

## Fix

In `MapFacadeBean.removeCollectionElement()` (line 624), the `orphanRemoval`
case must **not** call `remove()` explicitly — Hibernate already handles
deletion via `orphanRemoval` when the child was removed from the Java
collection in `removeFromJavaCollection()`. Calling `remove()` causes
infinite recursion because `remove()` re-enters `updateSortableCollectionsOnRemove()`.

```java
// Before (bug):
if (deletingElement && (metaCollection.isAggregate() || metaCollection.isOrphanRemoval())) {
    remove(childMetaModel, collectionElementKeyValues);
}

// After (fix):
if (deletingElement && metaCollection.isAggregate()) {
    remove(childMetaModel, collectionElementKeyValues);
}
else if (deletingElement && metaCollection.isOrphanRemoval()) {
    // No-op: Hibernate handles deletion via orphanRemoval when the child
    // was removed from the collection in removeFromJavaCollection().
    // Calling remove() here causes infinite recursion.
}
```

To test the fix, copy the patched `MapFacadeBean.java` to
`src/main/java/org/openxava/model/impl/` (classpath override) and rebuild.

## Workaround

Remove `orphanRemoval = true` from the `@OneToMany`. This prevents the
recursive `remove()` call but introduces a different issue: OX then takes
the `setValues(null)` path which validates required fields and fails if the
`@ManyToOne` is `optional=false` and the reference subview is non-editable
in the form.

## Project structure

Generated from the OpenXava 7.7 Maven archetype:
```
mvn archetype:generate \
  -DarchetypeGroupId=org.openxava \
  -DarchetypeArtifactId=openxava-archetype \
  -DarchetypeVersion=7.7
```

```
src/main/java/com/example/orphan/
├── model/
│   ├── Team.java       — Parent with @OneToMany @OrderColumn orphanRemoval
│   └── Member.java     — Child with @ManyToOne(optional=false)
└── run/
    ├── orphan.java     — Entry point
    └── DBManager.java  — HSQLDB manager (archetype-generated)
```
