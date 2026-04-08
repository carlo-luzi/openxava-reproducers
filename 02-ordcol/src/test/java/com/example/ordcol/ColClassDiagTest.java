package com.example.ordcol;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.*;
import org.junit.*;

/**
 * Diagnostica classi _col su ordcol per confronto con oxcl.
 * Prerequisito: app su localhost:8081 con almeno un Project con Tasks.
 */
public class ColClassDiagTest {

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
        // Login
        page.navigate(BASE + "/m/SignIn");
        page.waitForSelector("input[value='Sign in']",
                new Page.WaitForSelectorOptions().setTimeout(20000));
        page.locator("input[type='text']:visible").first().fill("admin");
        page.locator("input[type='password']:visible").first().fill("admin");
        page.click("input[value='Sign in']");
        page.waitForTimeout(3000);
    }

    @After
    public void tearDown() { context.close(); }

    @Test
    public void dumpColClassesAndWidths() throws Exception {
        page.navigate(BASE + "/m/Project");
        page.waitForTimeout(4000);

        // Open first record from list if needed
        Locator viewDetail = page.locator("a.xava_action[data-action='List.viewDetail']");
        if (viewDetail.count() > 0) {
            viewDetail.first().click();
            page.waitForTimeout(2000);
            page.waitForLoadState(LoadState.NETWORKIDLE);
        }

        String dump = (String) page.evaluate("() => {\n"
            + "  const tables = document.querySelectorAll('table.ox-list');\n"
            + "  let t = null;\n"
            + "  for (const tbl of tables) {\n"
            + "    if (tbl.id && tbl.id.includes('tasks')) { t = tbl; break; }\n"
            + "  }\n"
            + "  if (!t) {\n"
            + "    for (const tbl of tables) {\n"
            + "      if (tbl.querySelector('tr[class*=\"total\"]')) { t = tbl; break; }\n"
            + "    }\n"
            + "  }\n"
            + "  if (!t) return 'ERRORE: tabella tasks non trovata';\n"
            + "\n"
            + "  let out = 'TABLE id=' + t.id + '\\n\\n';\n"
            + "\n"
            + "  const hdr = t.querySelector('tr.ox-list-header');\n"
            + "  if (hdr) {\n"
            + "    out += '=== HEADER ROW ===\\n';\n"
            + "    hdr.querySelectorAll('th').forEach((th, i) => {\n"
            + "      const div = th.querySelector('div');\n"
            + "      const cls = div ? [...div.classList].filter(c => c.includes('col')).join(',') : '(no div)';\n"
            + "      const r = th.getBoundingClientRect();\n"
            + "      out += '  TH[' + i + '] text=\"' + th.textContent.trim().substring(0,20)"
            + "        + '\" div._col=' + cls"
            + "        + ' x=' + Math.round(r.x) + ' w=' + Math.round(r.width) + '\\n';\n"
            + "    });\n"
            + "  }\n"
            + "\n"
            + "  const dataRows = t.querySelectorAll('tr.ox-list-pair, tr.ox-list-odd');\n"
            + "  if (dataRows.length > 0) {\n"
            + "    out += '\\n=== FIRST DATA ROW ===\\n';\n"
            + "    dataRows[0].querySelectorAll('td').forEach((td, i) => {\n"
            + "      const div = td.querySelector('div');\n"
            + "      const cls = div ? [...div.classList].filter(c => c.includes('col')).join(',') : '(no div)';\n"
            + "      const r = td.getBoundingClientRect();\n"
            + "      out += '  TD[' + i + '] text=\"' + td.textContent.trim().substring(0,20)"
            + "        + '\" div._col=' + cls"
            + "        + ' x=' + Math.round(r.x) + ' w=' + Math.round(r.width) + '\\n';\n"
            + "    });\n"
            + "  }\n"
            + "\n"
            + "  t.querySelectorAll('tr[class*=\"total\"]').forEach((tr, ri) => {\n"
            + "    out += '\\n=== TOTAL ROW ' + ri + ' ===\\n';\n"
            + "    tr.querySelectorAll('td').forEach((td, i) => {\n"
            + "      const div = td.querySelector('div');\n"
            + "      const cls = div ? [...div.classList].filter(c => c.includes('col')).join(',') : '(no div)';\n"
            + "      const r = td.getBoundingClientRect();\n"
            + "      out += '  TD[' + i + '] class=\"' + [...td.classList].join(',')"
            + "        + '\" text=\"' + td.textContent.trim().substring(0,20)"
            + "        + '\" div._col=' + cls"
            + "        + ' x=' + Math.round(r.x) + ' w=' + Math.round(r.width) + '\\n';\n"
            + "    });\n"
            + "  });\n"
            + "\n"
            + "  return out;\n"
            + "}");

        System.out.println();
        System.out.println(dump);
    }
}
