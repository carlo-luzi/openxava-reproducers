package com.example.ordcol;

import static org.junit.Assert.*;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.*;
import org.junit.*;

import java.nio.file.Paths;
import java.util.Map;

/**
 * E2E test that verifies whether the column summation ("+") in
 * {@code @ListProperties} aligns correctly when {@code @OrderColumn}
 * is present on a {@code @OneToMany} collection.
 *
 * <p><b>Bug:</b> the sum total rendered by "hours+" appears under the
 * "Weight" column instead of the "Hours" column when the collection
 * has {@code @OrderColumn} (which enables drag-and-drop reordering).
 *
 * <p><b>Prerequisites:</b> app running on localhost:8081/ordcol
 * (start with {@code cd egprj/ordcol && mvn exec:java}).
 * Login: admin / admin.
 *
 * <p><b>Diagnostic output:</b> screenshots saved to target/screenshots/,
 * column positions printed to stdout.
 */
public class OrderColumnSummationBugTest {

    static final String BASE = "http://localhost:8081/ordcol";
    static Playwright playwright;
    static Browser browser;
    BrowserContext context;
    Page page;

    @BeforeClass
    public static void launchBrowser() {
        playwright = Playwright.create();
        browser = playwright.firefox().launch(new BrowserType.LaunchOptions()
                .setHeadless(true));
    }

    @AfterClass
    public static void closeBrowser() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    @Before
    public void setUp() {
        context = browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(1280, 900));
        page = context.newPage();
        page.setDefaultTimeout(15000);
        login();
    }

    @After
    public void tearDown() {
        screenshot("teardown");
        context.close();
    }

    // ----- helpers -----

    private void login() {
        page.navigate(BASE + "/m/SignIn");
        page.waitForSelector("input[value='Sign in']",
                new Page.WaitForSelectorOptions().setTimeout(20000));
        page.locator("input[type='text']:visible").first().fill("admin");
        page.locator("input[type='password']:visible").first().fill("admin");
        page.click("input[value='Sign in']");
        page.waitForTimeout(3000);
    }

    private void waitForOX() {
        page.waitForTimeout(1000);
        page.waitForLoadState(LoadState.NETWORKIDLE);
    }

    private void screenshot(String label) {
        page.screenshot(new Page.ScreenshotOptions()
                .setPath(Paths.get("target/screenshots/" + label + "-"
                        + System.currentTimeMillis() + ".png"))
                .setFullPage(true));
    }

    // ----- tests -----

    /**
     * Test 1: checks alignment on the empty collection (the totals row
     * is already visible with value 0.00 even without data).
     * This is the fastest way to detect the structural misalignment.
     */
    @Test
    public void summationAlignmentOnEmptyCollection() throws Exception {
        page.navigate(BASE + "/m/Project");
        page.waitForTimeout(3000);
        page.waitForSelector("input[id*='name']",
                new Page.WaitForSelectorOptions().setTimeout(10000));

        screenshot("01-empty-collection");
        verifySummationAlignment();
    }

    /**
     * Test 2: creates a Project with Tasks and checks alignment with
     * actual data, to rule out any empty-collection rendering shortcut.
     */
    @Test
    public void summationAlignmentWithData() throws Exception {
        page.navigate(BASE + "/m/Project");
        page.waitForTimeout(4000);

        // Ensure we're on a new detail view (might land on list if records exist)
        ensureDetailView();

        // Fill project name and save
        page.locator("input[id*='name']").fill("Alignment Test " + System.currentTimeMillis());
        page.keyboard().press("Tab");
        waitForOX();

        // Save — use JS to invoke OX action directly (avoids selector fragility)
        oxAction("TypicalNotResetOnSave.save");
        page.waitForTimeout(2000);
        screenshot("02-project-saved");

        // Add tasks via the collection "New" button
        addTask("Design phase", 5, "10.50");
        addTask("Implementation", 8, "24.00");
        addTask("Testing", 3, "7.50");

        screenshot("03-tasks-added");
        verifySummationAlignment();
    }

    /** Navigate to a new detail view, handling both list and detail landing. */
    private void ensureDetailView() {
        Locator nameInput = page.locator("input[id*='name']");
        if (nameInput.count() > 0 && nameInput.first().isVisible()) return;
        // In list mode — invoke New via JS
        oxAction("CRUD.new");
        page.waitForTimeout(2000);
        page.waitForSelector("input[id*='name']",
                new Page.WaitForSelectorOptions().setTimeout(10000));
    }

    /** Invoke an OX action via JavaScript (bypasses visibility issues). */
    private void oxAction(String action) {
        page.evaluate("action => {"
            + "const app = document.querySelector('[data-application]').dataset.application;"
            + "const mod = document.querySelector('[data-module]').dataset.module;"
            + "openxava.executeAction(app, mod, false, false, action);"
            + "}", action);
        waitForOX();
    }

    /**
     * Adds a Task to the current Project's tasks collection.
     */
    private void addTask(String description, int weight, String hours) {
        // Use JS to invoke Collection.new — OX may hide collection actions during DWR refresh
        page.evaluate("() => {"
            + "const app = document.querySelector('[data-application]').dataset.application;"
            + "const mod = document.querySelector('[data-module]').dataset.module;"
            + "openxava.executeAction(app, mod, false, false, 'Collection.new', 'viewObject=xava_view_tasks');"
            + "}");
        waitForOX();

        // Fill the task detail form
        page.locator("input[id*='description']").fill(description);
        page.keyboard().press("Tab");
        page.waitForTimeout(300);

        page.locator("input[id*='weight']").fill(String.valueOf(weight));
        page.keyboard().press("Tab");
        page.waitForTimeout(300);

        page.locator("input[id*='hours']").fill(hours);
        page.keyboard().press("Tab");
        waitForOX();

        // Save the task via JS
        page.evaluate("() => {"
            + "const app = document.querySelector('[data-application]').dataset.application;"
            + "const mod = document.querySelector('[data-module]').dataset.module;"
            + "openxava.executeAction(app, mod, false, false, 'Collection.save', 'viewObject=xava_view_tasks');"
            + "}");
        waitForOX();
        page.waitForTimeout(1000);
    }

    /**
     * Verifies that the summation total cell is horizontally aligned
     * with the "Hours" header, not with the "Weight" header.
     *
     * Uses JS to compare cell indices in the DOM — the most reliable
     * way to detect the off-by-one bug regardless of CSS/viewport.
     */
    @SuppressWarnings("unchecked")
    private void verifySummationAlignment() {
        // Wait for the totals row
        page.waitForTimeout(500);
        Locator totalRow = page.locator("tr[class*='total']");
        assertTrue("No totals row found — the summation (+) may not have been registered. "
                + "Check that @ListProperties has 'hours+' and the collection rendered correctly.",
                totalRow.count() > 0);

        screenshot("alignment-check");

        Map<String, Object> result = (Map<String, Object>) page.evaluate("() => {\n"
            + "  const tables = document.querySelectorAll('table.ox-list');\n"
            + "  let collTable = null;\n"
            + "  for (const t of tables) {\n"
            + "    if (t.id && t.id.includes('tasks')) { collTable = t; break; }\n"
            + "  }\n"
            + "  if (!collTable) {\n"
            + "    for (const t of tables) {\n"
            + "      if (t.querySelector('tr[class*=\"total\"]')) { collTable = t; break; }\n"
            + "    }\n"
            + "  }\n"
            + "  if (!collTable) return { error: 'no collection table found' };\n"
            + "\n"
            + "  const headerRow = collTable.querySelector('tr.ox-list-header');\n"
            + "  if (!headerRow) return { error: 'no header row' };\n"
            + "  const ths = [...headerRow.querySelectorAll('th')];\n"
            + "  let hoursIdx = -1, weightIdx = -1;\n"
            + "  const headerTexts = [];\n"
            + "  ths.forEach((th, i) => {\n"
            + "    const t = th.textContent.trim();\n"
            + "    headerTexts.push(t);\n"
            + "    if (/hours|ore/i.test(t)) hoursIdx = i;\n"
            + "    if (/weight|peso/i.test(t)) weightIdx = i;\n"
            + "  });\n"
            + "\n"
            + "  const trow = collTable.querySelector('tr[class*=\"total\"]');\n"
            + "  if (!trow) return { error: 'no total row' };\n"
            + "  const tds = [...trow.querySelectorAll('td')];\n"
            + "  let totalIdx = -1, totalVal = '';\n"
            + "  tds.forEach((td, i) => {\n"
            + "    if (td.classList.contains('ox-total-cell')) {\n"
            + "      totalIdx = i; totalVal = td.textContent.trim();\n"
            + "    }\n"
            + "  });\n"
            + "\n"
            + "  let hoursX = -1, weightX = -1, totalX = -1;\n"
            + "  if (hoursIdx >= 0) { const r = ths[hoursIdx].getBoundingClientRect(); hoursX = r.x + r.width/2; }\n"
            + "  if (weightIdx >= 0) { const r = ths[weightIdx].getBoundingClientRect(); weightX = r.x + r.width/2; }\n"
            + "  if (totalIdx >= 0) { const r = tds[totalIdx].getBoundingClientRect(); totalX = r.x + r.width/2; }\n"
            + "\n"
            + "  return {\n"
            + "    headers: headerTexts.join(' | '),\n"
            + "    hoursIdx, weightIdx, totalIdx, totalVal,\n"
            + "    hoursX: Math.round(hoursX), weightX: Math.round(weightX), totalX: Math.round(totalX),\n"
            + "    thCount: ths.length, tdCount: tds.length\n"
            + "  };\n"
            + "}");

        System.out.println();
        System.out.println("=== @OrderColumn + Summation Alignment Diagnostic ===");
        System.out.println("Raw: " + result);

        if (result.containsKey("error")) {
            fail("DOM analysis failed: " + result.get("error"));
        }

        String headers     = (String) result.get("headers");
        int hoursIdx       = ((Number) result.get("hoursIdx")).intValue();
        int weightIdx      = ((Number) result.get("weightIdx")).intValue();
        int totalIdx       = ((Number) result.get("totalIdx")).intValue();
        String totalVal    = (String) result.get("totalVal");
        int hoursX         = ((Number) result.get("hoursX")).intValue();
        int weightX        = ((Number) result.get("weightX")).intValue();
        int totalX         = ((Number) result.get("totalX")).intValue();
        int thCount        = ((Number) result.get("thCount")).intValue();
        int tdCount        = ((Number) result.get("tdCount")).intValue();

        System.out.println("Headers:          " + headers);
        System.out.println("TH count:         " + thCount);
        System.out.println("TD count:         " + tdCount);
        System.out.println("Hours header idx: " + hoursIdx + "  (X=" + hoursX + ")");
        System.out.println("Weight header idx:" + weightIdx + "  (X=" + weightX + ")");
        System.out.println("Total cell idx:   " + totalIdx + "  (X=" + totalX + ")");
        System.out.println("Total value:      " + totalVal);
        System.out.println();

        // Structural check
        if (totalIdx == hoursIdx) {
            System.out.println("RESULT: PASS — total aligns with Hours (idx " + totalIdx + ")");
        } else if (totalIdx == weightIdx) {
            System.out.println("RESULT: FAIL — total aligns with WEIGHT (idx " + totalIdx
                    + ") instead of Hours (idx " + hoursIdx + ")");
            System.out.println("  >> This confirms the @OrderColumn summation offset bug");
        } else {
            System.out.println("RESULT: FAIL — total at idx " + totalIdx
                    + ", expected Hours idx " + hoursIdx);
        }

        // Visual check
        int dH = Math.abs(totalX - hoursX);
        int dW = Math.abs(totalX - weightX);
        System.out.println("Visual: dist→Hours=" + dH + "px  dist→Weight=" + dW + "px  → "
                + (dH < dW ? "closer to Hours" : "closer to Weight"));
        System.out.println("=== END ===");
        System.out.println();

        // Structural check: cell index must match
        assertEquals(
                "Structural: summation total ('" + totalVal + "') is at cell index " + totalIdx
                + " but 'Hours' header is at index " + hoursIdx
                + " (headers: " + headers + ").",
                hoursIdx, totalIdx);

        // Visual check: total must be visually under Hours header, not Weight
        assertTrue(
                "Visual: summation total ('" + totalVal + "') renders at X=" + totalX
                + " which is closer to Weight (X=" + weightX + ", dist=" + dW + "px)"
                + " than to Hours (X=" + hoursX + ", dist=" + dH + "px)."
                + " The total-capable cell for Weight is hidden (width=0),"
                + " collapsing the total under the wrong column."
                + " Root cause: collectionTotals.jsp line 31"
                + " calculates mpListSize = metaPropertiesList.size() - keyPropertiesList.size();"
                + " with 2+ @ManyToOne references not in @ListProperties,"
                + " mpListSize becomes too small and the hidden check hides visible cells.",
                dH < dW);
    }
}
