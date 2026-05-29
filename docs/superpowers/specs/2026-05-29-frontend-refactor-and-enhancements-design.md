# Frontend Refactor & Enhancements — Design Spec

**Date:** 2026-05-29  
**Status:** Approved  
**Reference project:** `camt-mt-converters` (same author, same stack)

---

## Overview

Refactor the current single-file frontend (`index.html`, 773 lines) into a 3-file structure matching the `camt-mt-converters` project, and add 4 feature enhancements:

1. **Diagrams tab** — 5 Mermaid architecture/flow diagrams
2. **Library comparison matrix** — color-coded heatmap table in Benchmarks tab
3. **HTML export preview** — inline iframe in Benchmarks tab
4. **File preview improvement** — scrollable `<pre>` + Copy button in Batch Runner tab

No backend changes. All work is confined to `src/main/resources/static/`.

---

## File Structure

### Before
```
src/main/resources/static/
└── index.html       773 lines (HTML + CSS + JS combined)
```

### After
```
src/main/resources/static/
├── index.html       ~50 lines   — page shell, CDN links, nav, containers
├── css/
│   └── style.css    ~220 lines  — all styles, CSS variables, responsive rules
└── js/
    └── app.js       ~950 lines  — all view logic, API calls, charts, diagrams
```

The split follows the same conventions as `camt-mt-converters`:
- `index.html` contains only structural HTML, CDN `<script>`/`<link>` tags, and the nav sidebar markup
- `style.css` uses CSS custom properties (`--primary`, `--surface`, etc.) for theming
- `app.js` exports no globals; all state is module-level variables; views are functions called on nav click

---

## Feature 1: Diagrams Tab

### Navigation
Add a 6th sidebar item: `🔀 Diagrams` — positioned after Benchmarks.

### Layout
Stacked full-width cards (same as `camt-mt-converters`), one per diagram. Each card has:
- A colour-accented left border (`#e65100`) on the active/first diagram; neutral on others
- A bold uppercase title label
- The rendered Mermaid SVG below

### Diagrams

| # | Title | Mermaid type | Content |
|---|-------|-------------|---------|
| 1 | System Architecture | `flowchart TB` | SPA → REST API (3 controllers) → Spring Batch job → StrategyResolver → 14 strategies → `/output/` files → BenchmarkMetrics saved |
| 2 | Strategy Pattern | `classDiagram` | `FileGenerationStrategy` interface; `AbstractCodaStrategy`, `AbstractSwiftStrategy` base classes; 7 CODA + 7 SWIFT concrete implementations; `StrategyResolver` map |
| 3 | Batch Execution Sequence | `sequenceDiagram` | Participants: Frontend, REST API, BatchJobService, Spring Batch, ItemReader, ItemProcessor, ItemWriter, BenchmarkMetrics. Two phases: generate domain data, then run batch job |
| 4 | CODA / SWIFT Format Flow | `flowchart LR` | Domain entities (Account, Transaction, BankingStatement) → formatter wrappers (7 libs) → CODA output (128-char fixed) and SWIFT MT940 output |
| 5 | Database Schema | `erDiagram` | Entities: `account`, `transaction`, `banking_statement`, `benchmark_metrics`. Relationships: account 1:N transaction, banking_statement 1:N transaction |

### Mermaid Theme
```js
mermaid.initialize({
  startOnLoad: false,
  theme: document.documentElement.dataset.theme === 'dark' ? 'dark' : 'default',
  themeVariables: {
    primaryColor: '#e65100',
    primaryTextColor: '#fff',
    primaryBorderColor: '#bf360c',
    lineColor: '#555',
    fontSize: '14px'
  }
});
```

Re-initialise on theme toggle so diagrams reflect the current mode. Use `mermaid.render()` (async) with SVG injected safely into a `<div>` — no `innerHTML` with user data.

---

## Feature 2: Library Comparison Matrix (Benchmarks Tab)

### Position
Added as a new card above the existing charts in the Benchmarks tab, rendered after benchmark data loads.

### Structure
Table with:
- **Rows:** 7 libraries (BeanIO, FixedFormat4J, FixedLength, Bindy, CamelBeanIO, Velocity, SpringBatch)
- **Columns:** Throughput (ops/s), Batch Duration (ms), Gen Duration (ms), Memory (MB), Success Rate (%)
- Values: averages across all runs for that library

### Colour coding (per column, relative ranking)
- Top third → green (`#c8e6c9`)
- Middle third → amber (`#fff9c4`)
- Bottom third → red (`#ffcdd2`)

Rankings are computed independently per column (best throughput ≠ best duration).

### Data source
`GET /api/benchmark/results` — same endpoint already used by the charts. Aggregate client-side: group by library, compute column averages, rank within each column.

### Empty state
If no benchmark data exists yet: show a muted message "Run some batch jobs first to see the comparison matrix."

---

## Feature 3: HTML Export Preview (Benchmarks Tab)

### Position
Collapsible section at the bottom of the Benchmarks tab, below the charts.

### Behaviour
- Default state: collapsed, showing a "▼ Show Report Preview" button
- On expand: fetches `GET /api/benchmark/export/html` and renders the response in a sandboxed `<iframe>` (`sandbox="allow-same-origin"`)
- iframe height: 400px, full width, border matches card style
- On collapse: iframe src cleared to avoid stale content

### Error handling
If the export endpoint returns empty or fails (no data): show an info alert "Generate and run batch jobs first to populate the benchmark report."

---

## Feature 4: File Preview in Batch Runner

### Current behaviour
After a job runs, the API response includes `fileContent` (truncated string). Currently shown in a small read-only textarea.

### New behaviour
- Replace textarea with a `<pre>` block: monospace font, `overflow-y: auto`, `max-height: 400px`, `white-space: pre`
- Add a "📋 Copy" button (top-right of the preview block) using `navigator.clipboard.writeText()`
- Show full `fileContent` from the API response (no client-side truncation)
- File name displayed above the block: `Generated: {fileName}`

---

## CSS Changes

New rules to add to `style.css`:

- `.matrix-table` — comparison matrix table styles (sticky header, colored cells)
- `.file-preview pre` — monospace scrollable block
- `.copy-btn` — small overlay button for clipboard copy
- `.diagram-card` — card wrapper for each Mermaid diagram
- `.collapse-toggle` — button style for the HTML preview expand/collapse

Existing CSS variables (`--primary`, `--surface`, `--border`, `--text`) cover theming — no new variables needed.

---

## CDN Dependencies Added

```html
<!-- Mermaid (Diagrams tab) -->
<script src="https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.min.js"></script>
```

Chart.js already loaded. No other new dependencies.

---

## What Does Not Change

- All 5 existing tabs (Dashboard, Generate Data, Batch Runner, Batch History, Benchmarks) keep their current behaviour and layout
- All REST API endpoints — no backend changes
- All 118 tests — no Java changes
- Orange color theme (`#e65100`) throughout
- Dark/light theme toggle behaviour
- Auto-refresh on Batch History (15s) and Charts (30s)

---

## Testing

Manual verification (no new automated tests needed — purely UI):
- [ ] All 6 tabs navigate correctly
- [ ] All 5 Mermaid diagrams render in light mode and dark mode
- [ ] Library comparison matrix populates after running batch jobs
- [ ] Matrix shows empty state message before any data
- [ ] HTML export preview expands/collapses; renders the Velocity-styled report
- [ ] File preview shows full content with working Copy button
- [ ] `mvn test` still passes (118 tests, no Java changes)
