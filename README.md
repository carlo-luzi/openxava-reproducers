# OpenXava Reproducers

Minimal, self-contained projects that reproduce bugs in
[OpenXava](https://www.openxava.org/).

Each project is generated from the official OpenXava Maven archetype with no
customizations beyond what is strictly necessary to trigger the bug.
The goal is to provide the OX team with ready-to-run reproducers: clone, build,
run, and observe the issue. Each project's `README.md` contains detailed
reproduction steps, root cause analysis, and — where available — a proposed fix.

## Projects

| # | Directory | Bug | Root cause | OX file |
|---|-----------|-----|------------|---------|
| 01 | [`01-elemcol`](01-elemcol/) | Ghost rows appear in `@ElementCollection` grid after changing a date via the flatpickr widget | Global selector `$('.xava_date > input').change()` fires on all date inputs instead of the current one | `dateCalendarEditor.js:135` |
| 02 | [`02-ordcol`](02-ordcol/) | Collection totals render under the wrong column when using `@OrderColumn` with 2+ `@ManyToOne` references | `mpListSize` hides visible total cells in `@OneToMany` collections where it should only apply to `@ElementCollection` | `collectionTotals.jsp:30-31` |
| 03 | [`03-orphan`](03-orphan/) | Recursive loop in `MapFacade.remove()` with `@OrderColumn` + `orphanRemoval=true` — deleting a child from standalone module causes infinite recursion and may delete siblings | `removeCollectionElement` with `deletingElement=true` + `orphanRemoval` calls `remove()` recursively | `MapFacadeBean.java:618-633` |

## Quick start

Each project follows the same workflow:

```bash
cd 01-elemcol          # or 02-ordcol
mvn package
mvn exec:java          # starts the embedded server (Ctrl+C to stop)
```

Then open the URL shown in the console and sign in with **admin / admin**.

See each project's `README.md` for specific reproduction steps.

## Requirements

- **Java** 17+
- **Maven** 3.9+
- No external database needed — all projects use HSQLDB embedded (in-memory)

## Project generation

New projects are generated from the official OpenXava Maven archetype with no
customizations beyond the minimal entity model needed to trigger each bug:

```bash
mvn archetype:generate \
  -DarchetypeGroupId=org.openxava \
  -DarchetypeArtifactId=openxava-archetype \
  -DarchetypeVersion=7.7 \
  -DgroupId=com.example \
  -DartifactId=<project-name> \
  -DinteractiveMode=false
```

The archetype provides the complete OX runtime (persistence.xml, xava.properties,
naviox-users.properties, application.xml, controllers.xml, entry point class,
HSQLDB manager). Only entity classes and README are added per project.

## Note

These reproducers were developed with significant assistance from LLMs,
particularly for root cause analysis and code generation.
Code review and manual verification of bug reproducibility were performed
by the author.

## License

[MIT](LICENSE)
