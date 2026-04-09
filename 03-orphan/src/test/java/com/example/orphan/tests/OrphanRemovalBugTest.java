package com.example.orphan.tests;

import org.openxava.tests.ModuleTestBase;

/**
 * Reproduces the @OrderColumn + orphanRemoval=true recursive loop bug.
 *
 * Steps:
 * 1. Create a Team with 2 Members (via the collection)
 * 2. Navigate to the Member standalone module
 * 3. Delete one Member
 * 4. Verify: the other Member still exists, no errors
 *
 * Without the fix in MapFacadeBean.removeCollectionElement(), step 3 causes
 * a StackOverflowError or deletes both Members.
 */
public class OrphanRemovalBugTest extends ModuleTestBase {

    public OrphanRemovalBugTest(String testName) {
        super(testName, "orphan", "Team");
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        login("admin", "admin");
    }

    /**
     * Create a Team with 2 Members, delete one from standalone module,
     * verify the sibling survives.
     */
    public void testDeleteMemberFromStandaloneModule() throws Exception {
        // --- Create Team ---
        execute("CRUD.new");
        setValue("name", "Test Team");
        execute("CRUD.save");
        assertNoErrors();

        // Reload the saved Team to access its collection
        execute("Mode.list");
        setConditionValues(new String[]{ "Test Team" });
        execute("List.filter");
        assertListRowCount(1);
        execute("List.viewDetail", "row=0");
        assertValue("name", "Test Team");

        // --- Add Member A via collection ---
        execute("Collection.new", "viewObject=xava_view_members");
        setValue("name", "Member A");
        execute("Collection.save");
        assertNoErrors();

        // --- Add Member B via collection ---
        execute("Collection.new", "viewObject=xava_view_members");
        setValue("name", "Member B");
        execute("Collection.save");
        assertNoErrors();

        // Verify 2 members in collection
        assertCollectionRowCount("members", 2);

        // --- Switch to Member standalone module ---
        changeModule("orphan", "Member");

        // Find Member B in list (changeModule may land in list or detail)
        try { execute("Mode.list"); } catch (Exception e) { /* already in list */ }
        setConditionValues(new String[]{ "Member B" });
        execute("List.filter");
        assertListRowCount(1);
        execute("List.viewDetail", "row=0");
        assertValue("name", "Member B");

        // --- Delete Member B from standalone module ---
        execute("CRUD.delete");
        assertNoErrors();

        // --- Verify Member A still exists ---
        execute("Mode.list");
        setConditionValues(new String[]{ "Member A" });
        execute("List.filter");
        assertListRowCount(1);

        // --- Verify Member B is gone ---
        setConditionValues(new String[]{ "Member B" });
        execute("List.filter");
        assertListRowCount(0);

        // --- Cleanup ---
        setConditionValues(new String[]{ "Member A" });
        execute("List.filter");
        execute("List.viewDetail", "row=0");
        execute("CRUD.delete");

        changeModule("orphan", "Team");
        execute("Mode.list");
        setConditionValues(new String[]{ "Test Team" });
        execute("List.filter");
        if (getListRowCount() > 0) {
            execute("List.viewDetail", "row=0");
            execute("CRUD.delete");
        }
    }
}
