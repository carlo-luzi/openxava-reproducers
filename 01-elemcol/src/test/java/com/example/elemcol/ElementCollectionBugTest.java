package com.example.elemcol;

import static org.junit.Assert.*;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.*;
import org.junit.*;

/**
 * E2E test that reproduces the ElementCollection ghost rows bug in OX 7.7.
 *
 * The key is using the flatpickr date picker widget (not direct input),
 * because the bug is triggered by the flatpickr onClose handler firing
 * a global change event on all date inputs in the page.
 *
 * Prerequisites: app running on localhost:8080/elemcol
 *
 * See docs/openxava-e2e-selectors.md for OX selector reference.
 */
public class ElementCollectionBugTest {

    static final String BASE = "http://localhost:8080/elemcol";
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
        context = browser.newContext();
        page = context.newPage();
        page.setDefaultTimeout(15000);
        login();
    }

    @After
    public void tearDown() {
        page.screenshot(new Page.ScreenshotOptions()
                .setPath(java.nio.file.Paths.get("target/screenshots/teardown-"
                        + System.currentTimeMillis() + ".png")));
        context.close();
    }

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
        page.waitForTimeout(800);
        page.waitForLoadState(LoadState.NETWORKIDLE);
    }

    /** Count rows with delete icon in the ElementCollection. */
    private int countDeleteIcons() {
        return page.locator("i.ox-element-collection-remove-action[data-row]").count();
    }

    /**
     * Change a date field by clicking the flatpickr calendar icon and
     * selecting a different day. This triggers the onClose handler that
     * contains the global change() bug.
     */
    private void changeDateViaFlatpickr(String fieldIdFragment) {
        // Find the calendar toggle icon next to the date input.
        // Structure: <div class="xava_date"><input .../><a data-toggle>icon</a></div>
        Locator dateInput = page.locator("input[id*='" + fieldIdFragment + "']");
        Locator dateContainer = dateInput.locator("xpath=..");
        Locator calendarToggle = dateContainer.locator("a[data-toggle]");
        calendarToggle.click();
        page.waitForTimeout(500);

        // The flatpickr calendar popup is now open.
        // Click a day that is different from the currently selected one.
        // We pick the first non-selected, non-previous-month day.
        Locator availableDays = page.locator(
                ".flatpickr-calendar.open .flatpickr-day"
                + ":not(.prevMonthDay):not(.nextMonthDay)"
                + ":not(.selected):not(.today)");
        availableDays.first().click();
        page.waitForTimeout(500);
    }

    /**
     * Reproduces the bug:
     * 1. Create a Record with one TimeSlot
     * 2. Reopen the saved record
     * 3. Change recordDate via the flatpickr widget
     * 4. Assert that no ghost rows appeared in the TimeSlots grid
     */
    @Test
    public void changeDateViaDatepickerCreatesGhostRows() throws Exception {
        page.navigate(BASE + "/m/Record");
        page.waitForTimeout(3000);

        // If in list mode, navigate to detail
        Locator listDetailBtn = page.locator("a.xava_action[data-action='List.viewDetail']");
        if (listDetailBtn.count() > 0) {
            listDetailBtn.first().click();
            waitForOX();
        }

        // Ensure we have a record with at least one TimeSlot
        int existingSlots = countDeleteIcons();
        if (existingSlots == 0) {
            // Create a new record with one TimeSlot
            page.locator("a.xava_action[data-action='CRUD.new']").click();
            waitForOX();
            page.locator("input[id*='title']").fill("Bug Repro");
            page.locator("input[id*='timeSlots'][id*='0'][id*='start']").fill("1/15/2026 9:00 AM");
            page.keyboard().press("Tab");
            waitForOX();
            page.locator("input[id*='timeSlots'][id*='0'][id*='end']").fill("1/15/2026 1:00 PM");
            page.keyboard().press("Tab");
            waitForOX();
            page.locator("[id*='save']").first().click();
            waitForOX();
            page.waitForTimeout(1000);
        }

        // Count delete icons before changing the date
        int iconsBefore = countDeleteIcons();
        System.out.println("Delete icons BEFORE date change: " + iconsBefore);
        assertTrue("Need at least 1 time slot row", iconsBefore >= 1);

        page.screenshot(new Page.ScreenshotOptions()
                .setPath(java.nio.file.Paths.get("target/screenshots/before-date-change.png")));

        // Change the date using the flatpickr widget (NOT direct input).
        // This triggers the onClose handler with the global change() bug.
        changeDateViaFlatpickr("recordDate");

        waitForOX();
        page.waitForTimeout(1500);

        page.screenshot(new Page.ScreenshotOptions()
                .setPath(java.nio.file.Paths.get("target/screenshots/after-date-change.png")));

        int iconsAfter = countDeleteIcons();
        System.out.println("Delete icons AFTER date change:  " + iconsAfter);

        // The bug: ghost rows appear, increasing the delete icon count
        assertEquals(
                "Ghost rows appeared after changing recordDate via flatpickr! "
                + "Before: " + iconsBefore + ", After: " + iconsAfter,
                iconsBefore, iconsAfter);
    }
}
