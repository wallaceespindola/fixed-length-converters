"""Generate banking-parser-platform.pptx from slide content."""

from pptx import Presentation
from pptx.util import Inches, Pt, Emu
from pptx.dml.color import RGBColor
from pptx.enum.text import PP_ALIGN
from pptx.util import Inches, Pt
import os

# ── Colors ────────────────────────────────────────────────────────────────────
ORANGE      = RGBColor(0xE6, 0x51, 0x00)  # #e65100
DARK_ORANGE = RGBColor(0xBF, 0x36, 0x0C)  # #bf360c
WHITE       = RGBColor(0xFF, 0xFF, 0xFF)
BLACK       = RGBColor(0x1A, 0x1A, 0x1A)
GRAY_BG     = RGBColor(0xF4, 0xF4, 0xF4)
DARK_BG     = RGBColor(0x1E, 0x1E, 0x1E)
LIGHT_TEXT  = RGBColor(0xD4, 0xD4, 0xD4)
ALT_ROW     = RGBColor(0xFE, 0xF6, 0xF2)
ROW_BORDER  = RGBColor(0xDD, 0xDD, 0xDD)
LINK_BLUE   = RGBColor(0x15, 0x65, 0xC0)

W  = Inches(13.33)   # widescreen 16:9
H  = Inches(7.5)

OUT_DIR   = os.path.join(os.path.dirname(__file__), "..", "..", "docs", "slides")
OUT_FILE  = os.path.join(OUT_DIR, "banking-parser-platform.pptx")


def new_prs() -> Presentation:
    prs = Presentation()
    prs.slide_width  = W
    prs.slide_height = H
    return prs


def blank_slide(prs: Presentation):
    layout = prs.slide_layouts[6]   # completely blank
    return prs.slides.add_slide(layout)


# ── Low-level helpers ─────────────────────────────────────────────────────────

def fill_slide(slide, color: RGBColor):
    from pptx.oxml.ns import qn
    from lxml import etree
    bg = slide.background
    fill = bg.fill
    fill.solid()
    fill.fore_color.rgb = color


def add_rect(slide, left, top, width, height, color: RGBColor):
    shape = slide.shapes.add_shape(1, left, top, width, height)
    shape.fill.solid()
    shape.fill.fore_color.rgb = color
    shape.line.fill.background()
    return shape


def add_textbox(slide, left, top, width, height,
                text, font_size=18, bold=False,
                color: RGBColor = BLACK,
                align=PP_ALIGN.LEFT,
                italic=False,
                word_wrap=True):
    txb = slide.shapes.add_textbox(left, top, width, height)
    txb.word_wrap = word_wrap
    tf  = txb.text_frame
    tf.word_wrap = word_wrap
    p   = tf.paragraphs[0]
    p.alignment = align
    run = p.add_run()
    run.text = text
    run.font.size  = Pt(font_size)
    run.font.bold  = bold
    run.font.color.rgb = color
    run.font.italic = italic
    run.font.name  = "Calibri"
    return txb


def add_paragraph(tf, text, font_size=16, bold=False,
                  color: RGBColor = BLACK,
                  align=PP_ALIGN.LEFT,
                  italic=False,
                  space_before=Pt(4)):
    from pptx.util import Pt as _Pt
    p = tf.add_paragraph()
    p.alignment = align
    p.space_before = space_before
    run = p.add_run()
    run.text = text
    run.font.size  = _Pt(font_size)
    run.font.bold  = bold
    run.font.color.rgb = color
    run.font.italic = italic
    run.font.name  = "Calibri"
    return p


def add_code_box(slide, left, top, width, height, code_text, font_size=11):
    rect = add_rect(slide, left, top, width, height, DARK_BG)
    txb  = slide.shapes.add_textbox(
        left + Inches(0.15), top + Inches(0.1),
        width - Inches(0.3), height - Inches(0.2))
    txb.word_wrap = False
    tf = txb.text_frame
    tf.word_wrap = False
    lines = code_text.strip().split("\n")
    for i, line in enumerate(lines):
        p = tf.paragraphs[0] if i == 0 else tf.add_paragraph()
        run = p.add_run()
        run.text = line
        run.font.size  = Pt(font_size)
        run.font.color.rgb = LIGHT_TEXT
        run.font.name  = "Courier New"
    return txb


def add_table(slide, left, top, width, rows_data,
              col_widths=None, font_size=14):
    num_rows = len(rows_data)
    num_cols = len(rows_data[0])
    row_h    = Inches(0.38)
    height   = row_h * num_rows

    table = slide.shapes.add_table(
        num_rows, num_cols, left, top, width, height).table

    if col_widths:
        for ci, cw in enumerate(col_widths):
            table.columns[ci].width = cw

    for ri, row in enumerate(rows_data):
        for ci, cell_text in enumerate(row):
            cell = table.cell(ri, ci)
            cell.text = cell_text
            tf   = cell.text_frame
            tf.word_wrap = True
            p    = tf.paragraphs[0]
            run  = p.add_run() if not p.runs else p.runs[0]
            run.font.size = Pt(font_size)
            run.font.name = "Calibri"
            if ri == 0:                  # header row
                run.font.bold  = True
                run.font.color.rgb = WHITE
                fill = cell.fill
                fill.solid()
                fill.fore_color.rgb = ORANGE
            else:
                run.font.color.rgb = BLACK
                fill = cell.fill
                fill.solid()
                fill.fore_color.rgb = ALT_ROW if ri % 2 == 0 else WHITE

    return table


# ── Slide builders ────────────────────────────────────────────────────────────

def slide_title(prs):
    slide = blank_slide(prs)
    fill_slide(slide, ORANGE)

    # bottom white bar
    add_rect(slide, 0, H - Inches(1.4), W, Inches(1.4), RGBColor(0xFF, 0xFF, 0xFF))

    add_textbox(slide,
        Inches(0.8), Inches(1.0), W - Inches(1.6), Inches(1.4),
        "Banking Fixed-Length File Platform",
        font_size=40, bold=True, color=WHITE, align=PP_ALIGN.CENTER)

    add_textbox(slide,
        Inches(0.8), Inches(2.6), W - Inches(1.6), Inches(0.6),
        "Generating, parsing and benchmarking CODA & SWIFT MT940 files",
        font_size=22, color=WHITE, align=PP_ALIGN.CENTER)

    add_textbox(slide,
        Inches(0.8), Inches(3.2), W - Inches(1.6), Inches(0.6),
        "across 7 Java formatter libraries via Strategy Pattern + Spring Batch",
        font_size=22, color=WHITE, align=PP_ALIGN.CENTER)

    add_textbox(slide,
        Inches(0.8), Inches(4.2), W - Inches(1.6), Inches(0.6),
        "Wallace Espindola  ·  wallace.espindola@gmail.com",
        font_size=17, color=WHITE, align=PP_ALIGN.CENTER)

    add_textbox(slide,
        Inches(0.8), Inches(4.8), W - Inches(1.6), Inches(0.5),
        "linkedin.com/in/wallaceespindola   ·   github.com/wallaceespindola",
        font_size=15, color=WHITE, align=PP_ALIGN.CENTER, italic=True)

    # page number area (bottom bar)
    add_textbox(slide,
        Inches(0.4), H - Inches(1.1), W - Inches(0.8), Inches(0.4),
        "1 / 15", font_size=12, color=RGBColor(0x88, 0x88, 0x88),
        align=PP_ALIGN.RIGHT)


def slide_heading(slide, title, page):
    add_rect(slide, 0, 0, W, Inches(0.78), ORANGE)
    add_textbox(slide,
        Inches(0.4), Inches(0.1), W - Inches(0.8), Inches(0.6),
        title, font_size=26, bold=True, color=WHITE)
    add_textbox(slide,
        Inches(0.4), H - Inches(0.35), W - Inches(0.8), Inches(0.3),
        f"{page} / 15", font_size=11,
        color=RGBColor(0x88, 0x88, 0x88), align=PP_ALIGN.RIGHT)


def slide_problem(prs):
    slide = blank_slide(prs)
    slide_heading(slide, "Problem Statement", 2)

    add_textbox(slide,
        Inches(0.5), Inches(1.0), W - Inches(1.0), Inches(0.5),
        "Multiple Java libraries claim to support fixed-length banking file formats.",
        font_size=17, color=BLACK)
    add_textbox(slide,
        Inches(0.5), Inches(1.5), W - Inches(1.0), Inches(0.5),
        "Which one is best for enterprise Spring Batch use?",
        font_size=20, bold=True, color=ORANGE)

    add_textbox(slide,
        Inches(0.5), Inches(2.2), Inches(3), Inches(0.35),
        "Evaluation criteria:", font_size=17, bold=True, color=BLACK)

    criteria = [
        ("Correctness", "Does output conform to Febelfin / SWIFT specifications?"),
        ("Performance", "Throughput in records/second under realistic load"),
        ("Maintainability", "Annotation quality, no XML, clean code"),
        ("Spring Batch fit", "Chunk-oriented reader/writer compatibility"),
    ]
    for i, (label, desc) in enumerate(criteria):
        top = Inches(2.7) + i * Inches(0.65)
        add_textbox(slide, Inches(0.5), top, Inches(2.5), Inches(0.5),
                    f"{i+1}. {label}", font_size=17, bold=True, color=DARK_ORANGE)
        add_textbox(slide, Inches(3.1), top, W - Inches(3.6), Inches(0.5),
                    f"— {desc}", font_size=16, color=BLACK)

    add_rect(slide,
        Inches(0.5), Inches(5.6), W - Inches(1.0), Inches(0.85),
        GRAY_BG)
    add_textbox(slide,
        Inches(0.7), Inches(5.65), W - Inches(1.4), Inches(0.75),
        "One codebase, 7 libraries, identical domain data, automated benchmarks.",
        font_size=16, italic=True, color=DARK_ORANGE)


def slide_architecture(prs):
    slide = blank_slide(prs)
    slide_heading(slide, "Architecture", 3)

    code = """\
Web UI (HTML/CSS/JS)
        |  HTTP REST
        v
   REST API Layer           POST /api/domain/generate
   (Spring MVC)             POST /api/batch/generate
        |                   GET  /api/benchmark/results
        v
  Spring Batch Pipeline
  DomainEntityItemReader -> FileGenerationItemProcessor -> FileOutputItemWriter
                                      |
                               StrategyResolver  (O(1) map lookup)
                              /    |    |    |    |    |    \\
                         BeanIO  ff4j  VL  Bindy CamelBIO Vel SB
                              |
                       14 FileGenerationStrategy implementations
                       (7 libraries x CODA + SWIFT)"""

    add_code_box(slide,
        Inches(0.4), Inches(0.95), W - Inches(0.8), Inches(5.9),
        code, font_size=13)


def slide_coda(prs):
    slide = blank_slide(prs)
    slide_heading(slide, "CODA Format — Belgian Banking Standard", 4)

    add_textbox(slide,
        Inches(0.5), Inches(0.95), W - Inches(1.0), Inches(0.45),
        "Febelfin CODA — fixed-width ASCII, exactly 128 characters per record",
        font_size=17, bold=True, color=DARK_ORANGE)

    rows = [
        ("Record", "Meaning"),
        ("0", "File header"),
        ("1", "Movement (debit/credit transaction)"),
        ("2", "Movement detail / free communication"),
        ("8", "Information record (closing balance)"),
        ("9", "File trailer"),
    ]
    col_w = [Inches(1.2), W - Inches(1.7)]
    add_table(slide, Inches(0.5), Inches(1.5), W - Inches(1.0),
              rows, col_w, font_size=15)

    code = """\
0310HDR       BE68539007547034                     EUR000000000000000022052622052...
1310REF0000001BE12345678901234567890123456789012345EUR000000000000075029042629042...
9000TRAILER   ..."""
    add_code_box(slide,
        Inches(0.5), Inches(4.7), W - Inches(1.0), Inches(1.25),
        code, font_size=13)

    add_textbox(slide,
        Inches(0.5), Inches(6.1), W - Inches(1.0), Inches(0.4),
        "Each field has an exact byte offset — annotations define the mapping.",
        font_size=14, italic=True, color=RGBColor(0x55, 0x55, 0x55))


def slide_swift(prs):
    slide = blank_slide(prs)
    slide_heading(slide, "SWIFT MT940 — International Statement Format", 5)

    add_textbox(slide,
        Inches(0.5), Inches(0.95), W - Inches(1.0), Inches(0.4),
        "SWIFT MT940 — tag-based messages, inter-message separator '---'",
        font_size=17, bold=True, color=DARK_ORANGE)

    rows = [
        ("Tag",    "Field",                  "Example"),
        (":20:",   "Transaction reference",  "STMT000001"),
        (":25:",   "Account identification", "BE68539007547034/EUR"),
        (":28C:",  "Statement / sequence",   "00001/001"),
        (":60F:",  "Opening balance",        "C260429EUR10000,00"),
        (":61:",   "Statement line",         "260429260429C750NMSCREF001"),
        (":86:",   "Narrative",              "Payment for services"),
        (":62F:",  "Closing balance",        "C260429EUR10750,00"),
    ]
    col_w = [Inches(1.2), Inches(4.0), W - Inches(5.7)]
    add_table(slide, Inches(0.5), Inches(1.5), W - Inches(1.0),
              rows, col_w, font_size=14)

    code = """\
:20:STMT000001
:25:BE68539007547034/EUR
:60F:C260429EUR10000,00
:61:260429260429C750NMSCREF001NONREF
:62F:C260429EUR10750,00
---"""
    add_code_box(slide,
        Inches(0.5), Inches(5.3), W - Inches(1.0), Inches(1.55),
        code, font_size=13)


def slide_libraries(prs):
    slide = blank_slide(prs)
    slide_heading(slide, "7 Parser Libraries", 6)

    rows = [
        ("Library",          "Mechanism",                                       "CODA W", "CODA R", "SWIFT"),
        ("BeanIO",           "@Record + @Field annotations",                    "Yes",    "Yes",    "Yes"),
        ("fixedformat4j",    "@Record(length=128) + @Field(offset, length)",    "Yes",    "Yes",    "Yes"),
        ("fixedlength",      "@FixedLine + @FixedField(offset, length)",        "Yes",    "Yes",    "Yes"),
        ("Camel Bindy",      "@FixedLengthRecord + @DataField(pos, length)",    "Yes",    "Yes",    "Yes"),
        ("Camel BeanIO",     "XML stream mapping",                              "Yes",    "Yes",    "Yes"),
        ("Velocity",         ".vm template files (write-only for CODA)",        "Yes",    "—",      "Yes"),
        ("Spring Batch",     "FormatterLineAggregator + FixedLengthTokenizer",  "Yes",    "Yes",    "Yes"),
    ]
    col_w = [Inches(2.0), Inches(5.0), Inches(1.3), Inches(1.3), Inches(1.3)]
    add_table(slide, Inches(0.4), Inches(0.95), W - Inches(0.8),
              rows, col_w, font_size=13)

    add_textbox(slide,
        Inches(0.5), Inches(6.6), W - Inches(1.0), Inches(0.4),
        "All libraries share the same domain data and produce comparable output files.",
        font_size=13, italic=True, color=RGBColor(0x55, 0x55, 0x55))


def slide_strategy(prs):
    slide = blank_slide(prs)
    slide_heading(slide, "Strategy Pattern — One Interface, 14 Implementations", 7)

    code1 = """\
public interface FileGenerationStrategy {
    String generate(List<Transaction> txs, List<Account> accounts);
    List<Transaction> parse(String fileContent);
    FileType getFileType();   // CODA | SWIFT
    Library   getLibrary();   // BEANIO | FIXFORMAT4J | FIXEDLENGTH
                              // BINDY | CAMEL_BEANIO | VELOCITY | SPRING_BATCH
    default String strategyKey() { return getFileType() + "_" + getLibrary(); }
}"""
    add_code_box(slide, Inches(0.4), Inches(0.95),
                 W - Inches(0.8), Inches(2.5), code1, font_size=13)

    code2 = """\
// Resolution — O(1) map lookup, no if/switch chains
FileGenerationStrategy s = resolver.resolve(FileType.CODA, Library.BEANIO);
String codaFile = s.generate(transactions, accounts);"""
    add_code_box(slide, Inches(0.4), Inches(3.6),
                 W - Inches(0.8), Inches(1.5), code2, font_size=13)

    add_textbox(slide,
        Inches(0.5), Inches(5.3), W - Inches(1.0), Inches(0.5),
        "StrategyResolver auto-wires all 14 beans from Spring context at startup.",
        font_size=16, color=DARK_ORANGE)


def slide_batch(prs):
    slide = blank_slide(prs)
    slide_heading(slide, "Spring Batch Pipeline", 8)

    code = """\
bankingFileGenerationJob  (restartable — saveState=true)
+-- fileGenerationStep    (chunk-size = 100)
    +-- DomainEntityItemReader
    |     Loads all Transaction rows from H2
    +-- FileGenerationItemProcessor
    |     Resolves FileGenerationStrategy by (fileType, library)
    |     Calls strategy.generate([transaction], accounts)
    +-- FileOutputItemWriter
          Buffers chunk output; on @AfterStep writes output/*.txt
          Stores file content + metadata in step ExecutionContext
               |
               v
         BatchMetricsListener (JobExecutionListener)
         Saves BenchmarkMetrics row to H2 on job completion"""
    add_code_box(slide, Inches(0.4), Inches(0.95),
                 W - Inches(0.8), Inches(5.1), code, font_size=13)

    add_textbox(slide,
        Inches(0.5), Inches(6.25), W - Inches(1.0), Inches(0.4),
        "Job parameters: fileType, library, operationId, runTimestamp",
        font_size=14, italic=True, color=RGBColor(0x55, 0x55, 0x55))


def slide_api(prs):
    slide = blank_slide(prs)
    slide_heading(slide, "REST API", 9)

    rows = [
        ("Method", "Endpoint",                        "Description"),
        ("POST",   "/api/domain/generate",            "Seed H2 with sample data (?loadProfile=LOW|HIGH)"),
        ("POST",   "/api/batch/generate",             "Trigger Spring Batch job {fileType, library}"),
        ("GET",    "/api/batch/history",              "Last 50 job executions"),
        ("GET",    "/api/benchmark/results",          "All benchmark metrics"),
        ("GET",    "/api/benchmark/export/csv",       "Export as CSV"),
        ("GET",    "/api/benchmark/export/json",      "Export as JSON"),
        ("GET",    "/api/benchmark/export/markdown",  "Export as Markdown"),
        ("GET",    "/api/benchmark/export/html",      "Velocity-rendered HTML report"),
        ("GET",    "/actuator/health",                "Application health + version"),
        ("GET",    "/actuator/info",                  "App name, version, description"),
    ]
    col_w = [Inches(1.1), Inches(3.8), W - Inches(5.4)]
    add_table(slide, Inches(0.4), Inches(0.95), W - Inches(0.8),
              rows, col_w, font_size=13)


def slide_benchmark(prs):
    slide = blank_slide(prs)
    slide_heading(slide, "Benchmark Metrics", 10)

    rows = [
        ("Metric",                "Description"),
        ("throughputRps",         "Records processed per second"),
        ("batchDurationMs",       "Total Spring Batch job wall-clock time"),
        ("generationDurationMs",  "File serialisation time only"),
        ("parseDurationMs",       "File parsing time (round-trip)"),
        ("symmetryRate",          "% of parsed transactions matching original domain data"),
        ("successRate",           "% of chunks completed without error"),
    ]
    col_w = [Inches(3.2), W - Inches(3.7)]
    add_table(slide, Inches(0.4), Inches(0.95), W - Inches(0.8),
              rows, col_w, font_size=15)

    code = """\
# Run JMH benchmark suite (28 @Benchmark methods)
mvn test -Pbenchmark

# Export results
curl http://localhost:8080/api/benchmark/export/csv -o results.csv
curl http://localhost:8080/api/benchmark/export/json"""
    add_code_box(slide, Inches(0.4), Inches(4.55),
                 W - Inches(0.8), Inches(1.8), code, font_size=13)


def slide_recommendations(prs):
    slide = blank_slide(prs)
    slide_heading(slide, "Library Recommendations", 11)

    rows = [
        ("Use Case",                    "Recommended Library",  "Reason"),
        ("Enterprise CODA processing",  "BeanIO",              "Best grammar support, battle-tested"),
        ("New projects, modern code",   "fixedformat4j",       "Best annotation DX, no boilerplate"),
        ("Existing Camel ecosystem",    "Camel Bindy",         "Native Camel route integration"),
        ("Lightweight / prototyping",   "fixedlength",         "Minimal setup, pure annotations"),
        ("Template-driven reports",     "Velocity",            "Flexible .vm template rendering"),
        ("Tightest Spring Batch fit",   "Spring Batch native", "Reuses existing batch components"),
    ]
    col_w = [Inches(3.5), Inches(2.8), W - Inches(6.8)]
    add_table(slide, Inches(0.4), Inches(0.95), W - Inches(0.8),
              rows, col_w, font_size=14)

    add_rect(slide,
        Inches(0.4), Inches(5.9), W - Inches(0.8), Inches(1.0),
        GRAY_BG)
    add_textbox(slide,
        Inches(0.6), Inches(5.95), W - Inches(1.2), Inches(0.45),
        "Recommendation: Pick one library and standardise across the codebase.",
        font_size=15, bold=True, color=DARK_ORANGE)
    add_textbox(slide,
        Inches(0.6), Inches(6.4), W - Inches(1.2), Inches(0.4),
        "Don't mix libraries in production — benchmark first, then commit.",
        font_size=14, italic=True, color=BLACK)


def slide_quality(prs):
    slide = blank_slide(prs)
    slide_heading(slide, "Code Quality & CI/CD", 12)

    add_textbox(slide,
        Inches(0.5), Inches(0.95), Inches(4), Inches(0.4),
        "Testing — 118 tests across 12 test classes:",
        font_size=17, bold=True, color=BLACK)

    rows = [
        ("Category",   "Tests",                                              "Coverage"),
        ("Unit",       "DomainDataGeneratorTest, CodaRecordTest",            "Mock repos, field validation"),
        ("Integration","StrategyResolverTest, CodaStrategyTest, SwiftStrategyTest", "All 14 strategies"),
        ("Symmetry",   "SymmetryTest",                                        "Round-trip: generate -> parse -> compare"),
        ("Golden file","GoldenFileTest",                                      "128-char CODA lines, MT940 tags"),
        ("API",        "DomainControllerTest, BatchControllerTest",           "MockMvc"),
        ("Actuator",   "ActuatorTest, SwaggerAvailabilityTest",               "TestRestTemplate"),
    ]
    col_w = [Inches(1.5), Inches(4.5), W - Inches(6.5)]
    add_table(slide, Inches(0.4), Inches(1.45), W - Inches(0.8),
              rows, col_w, font_size=13)

    bullets = [
        "CI/CD: GitHub Actions — build · test · benchmark · CodeQL · release",
        "Coverage: JaCoCo enforced at minimum threshold · Dependabot weekly PRs",
    ]
    for i, b in enumerate(bullets):
        add_textbox(slide,
            Inches(0.5), Inches(5.55) + i * Inches(0.5),
            W - Inches(1.0), Inches(0.45),
            f"• {b}", font_size=15, color=BLACK)


def slide_quickstart(prs):
    slide = blank_slide(prs)
    slide_heading(slide, "Quick Start", 13)

    code = """\
# Clone and build (Java 21 + Maven 3.9 required — no Node.js needed)
git clone https://github.com/wallaceespindola/fixed-length-converters
cd fixed-length-converters
mvn clean install

# Start in dev mode (Swagger UI at /swagger-ui.html)
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Step 1 -- Generate domain data
curl -X POST http://localhost:8080/api/domain/generate?loadProfile=HIGH

# Step 2 -- Run batch job (pick any library)
curl -X POST http://localhost:8080/api/batch/generate \\
  -H "Content-Type: application/json" \\
  -d '{"fileType":"CODA","library":"BEANIO"}'

# Step 3 -- Export benchmark results
curl http://localhost:8080/api/benchmark/export/csv -o results.csv"""

    add_code_box(slide, Inches(0.4), Inches(0.95),
                 W - Inches(0.8), Inches(5.95), code, font_size=13)


def slide_stack(prs):
    slide = blank_slide(prs)
    slide_heading(slide, "Technology Stack", 14)

    rows = [
        ("Area",        "Technology"),
        ("Language",    "Java 21"),
        ("Backend",     "Spring Boot, Spring Batch, Spring Data JPA"),
        ("Database",    "H2 In-Memory"),
        ("API Docs",    "OpenAPI + Swagger UI (dev profile)"),
        ("Monitoring",  "Spring Actuator (/health, /info)"),
        ("Frontend",    "Vanilla HTML/CSS/JS + Chart.js"),
        ("Build",       "Maven (single mvn clean install, no profiles)"),
        ("Testing",     "JUnit 5 + Mockito, 118 tests, JMH benchmarks"),
        ("CI/CD",       "GitHub Actions (build, test, benchmark, CodeQL)"),
    ]
    col_w = [Inches(2.5), W - Inches(3.0)]
    add_table(slide, Inches(0.4), Inches(0.95), W - Inches(0.8),
              rows, col_w, font_size=15)


def slide_thankyou(prs):
    slide = blank_slide(prs)
    fill_slide(slide, ORANGE)

    add_rect(slide, 0, H - Inches(1.4), W, Inches(1.4), WHITE)

    add_textbox(slide,
        Inches(0.8), Inches(1.2), W - Inches(1.6), Inches(1.2),
        "Thank You",
        font_size=52, bold=True, color=WHITE, align=PP_ALIGN.CENTER)

    add_textbox(slide,
        Inches(0.8), Inches(2.7), W - Inches(1.6), Inches(0.7),
        "github.com/wallaceespindola/fixed-length-converters",
        font_size=22, color=WHITE, align=PP_ALIGN.CENTER)

    add_textbox(slide,
        Inches(0.8), Inches(3.6), W - Inches(1.6), Inches(0.55),
        "Wallace Espindola",
        font_size=20, bold=True, color=WHITE, align=PP_ALIGN.CENTER)

    add_textbox(slide,
        Inches(0.8), Inches(4.15), W - Inches(1.6), Inches(0.5),
        "wallace.espindola@gmail.com",
        font_size=17, color=WHITE, align=PP_ALIGN.CENTER)

    add_textbox(slide,
        Inches(0.8), Inches(4.7), W - Inches(1.6), Inches(0.5),
        "linkedin.com/in/wallaceespindola   ·   github.com/wallaceespindola",
        font_size=15, italic=True, color=WHITE, align=PP_ALIGN.CENTER)

    add_textbox(slide,
        Inches(0.8), Inches(5.5), W - Inches(1.6), Inches(0.5),
        "Questions welcome — slides, code and benchmarks all open source",
        font_size=15, italic=True, color=WHITE, align=PP_ALIGN.CENTER)

    add_textbox(slide,
        Inches(0.4), H - Inches(1.1), W - Inches(0.8), Inches(0.4),
        "15 / 15", font_size=12,
        color=RGBColor(0x88, 0x88, 0x88), align=PP_ALIGN.RIGHT)


# ── Main ──────────────────────────────────────────────────────────────────────

def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    prs = new_prs()

    slide_title(prs)
    slide_problem(prs)
    slide_architecture(prs)
    slide_coda(prs)
    slide_swift(prs)
    slide_libraries(prs)
    slide_strategy(prs)
    slide_batch(prs)
    slide_api(prs)
    slide_benchmark(prs)
    slide_recommendations(prs)
    slide_quality(prs)
    slide_quickstart(prs)
    slide_stack(prs)
    slide_thankyou(prs)

    prs.save(OUT_FILE)
    print(f"Saved: {OUT_FILE}")
    print(f"Slides: {len(prs.slides)}")


if __name__ == "__main__":
    main()
