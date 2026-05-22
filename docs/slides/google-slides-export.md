# Google Slides Export

## Upload banking-parser-platform.pptx to Google Slides

### Option 1 — Google Drive web UI (recommended)

1. Open [drive.google.com](https://drive.google.com)
2. Click **+ New → File upload**
3. Select `docs/slides/banking-parser-platform.pptx`
4. After upload, right-click the file → **Open with → Google Slides**
5. Google Slides converts the PPTX automatically
6. Click **Share → Anyone with the link → Viewer** to get a shareable URL

### Option 2 — Google Drive desktop app

If Google Drive for Desktop is installed:

1. Copy `banking-parser-platform.pptx` into your synced Drive folder
2. Open [drive.google.com](https://drive.google.com) and find the file
3. Double-click → **Open with Google Slides**

### Option 3 — `gdrive` CLI

```bash
# Install gdrive CLI (https://github.com/glotlabs/gdrive)
brew install gdrive

# Authenticate (first time only)
gdrive account add

# Upload and convert to Google Slides
gdrive files upload \
  --parent <FOLDER_ID> \
  --convert \
  docs/slides/banking-parser-platform.pptx
```

---

## Presentation structure (15 slides)

| # | Slide |
|---|-------|
| 1 | Title — Banking Fixed-Length File Platform |
| 2 | Problem Statement |
| 3 | Architecture |
| 4 | CODA Format — Belgian Banking Standard |
| 5 | SWIFT MT940 — International Statement Format |
| 6 | 7 Parser Libraries |
| 7 | Strategy Pattern — One Interface, 14 Implementations |
| 8 | Spring Batch Pipeline |
| 9 | REST API |
| 10 | Benchmark Metrics |
| 11 | Library Recommendations |
| 12 | Code Quality & CI/CD |
| 13 | Quick Start |
| 14 | Technology Stack |
| 15 | Thank You |

---

## Source files

| File | Description |
|------|-------------|
| `banking-parser-platform.pptx` | PowerPoint deck (python-pptx generated) |
| `banking-parser-comparison.md` | Marp-formatted slide source (Markdown) |
| `google-slides-export.md` | This file |
| `../../tools/python/generate_pptx.py` | Script used to generate the PPTX |

To regenerate the PPTX after updating slides content:

```bash
python3 tools/python/generate_pptx.py
```
