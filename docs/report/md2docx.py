"""Convert the FreshFlow report / use-case Markdown into a .docx with python-docx.

Handles: # headings (levels 1-4), paragraphs with **bold**, *italic* and `code` runs,
bullet and numbered lists, pipe tables, fenced code blocks, horizontal rules, and
figure placeholders of the form [[Figure N: path ...]] which embed the PNG when it exists.
Other [[...]] placeholders are rendered as highlighted text so the team can find them.
"""
import re
import sys
from pathlib import Path

from docx import Document
from docx.enum.text import WD_COLOR_INDEX
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.shared import Pt, Cm, RGBColor
from docx.oxml.ns import qn
from docx.oxml import OxmlElement

REPO = Path(r"E:\UOW 2026 spring\CSCI318 Software Engineering Practices&Principles\FreshFlow")

INLINE = re.compile(r"(\*\*[^*]+\*\*|`[^`]+`|\*[^*]+\*|\[\[[^\]]+\]\])")


def shade(cell, hex_fill):
    tc_pr = cell._tc.get_or_add_tcPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear")
    shd.set(qn("w:color"), "auto")
    shd.set(qn("w:fill"), hex_fill)
    tc_pr.append(shd)


def add_runs(paragraph, text, base_size=None):
    pos = 0
    for m in INLINE.finditer(text):
        if m.start() > pos:
            r = paragraph.add_run(text[pos:m.start()])
            if base_size:
                r.font.size = base_size
        token = m.group(0)
        if token.startswith("**"):
            r = paragraph.add_run(token[2:-2])
            r.bold = True
        elif token.startswith("`"):
            r = paragraph.add_run(token[1:-1])
            r.font.name = "Consolas"
            r._element.rPr.rFonts.set(qn("w:eastAsia"), "Consolas")
            r.font.size = Pt(9.5)
        elif token.startswith("[["):
            r = paragraph.add_run(token)
            r.font.highlight_color = WD_COLOR_INDEX.YELLOW
        else:
            r = paragraph.add_run(token[1:-1])
            r.italic = True
        if base_size and not token.startswith("`"):
            r.font.size = base_size
        pos = m.end()
    if pos < len(text):
        r = paragraph.add_run(text[pos:])
        if base_size:
            r.font.size = base_size


def add_code(doc, lines):
    for line in lines:
        p = doc.add_paragraph()
        p.paragraph_format.space_after = Pt(0)
        p.paragraph_format.space_before = Pt(0)
        p.paragraph_format.left_indent = Cm(0.5)
        r = p.add_run(line if line else " ")
        r.font.name = "Consolas"
        r._element.rPr.rFonts.set(qn("w:eastAsia"), "Consolas")
        r.font.size = Pt(8.5)
        pPr = p._p.get_or_add_pPr()
        shd = OxmlElement("w:shd")
        shd.set(qn("w:val"), "clear")
        shd.set(qn("w:color"), "auto")
        shd.set(qn("w:fill"), "F2F2F2")
        pPr.append(shd)
    doc.add_paragraph()


def add_table(doc, rows):
    header = [c.strip() for c in rows[0].strip().strip("|").split("|")]
    body = []
    for row in rows[2:]:
        cells = [c.strip() for c in row.strip().strip("|").split("|")]
        body.append(cells)
    table = doc.add_table(rows=1, cols=len(header))
    table.style = "Table Grid"
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    for i, h in enumerate(header):
        cell = table.rows[0].cells[i]
        cell.text = ""
        add_runs(cell.paragraphs[0], h, Pt(9))
        for r in cell.paragraphs[0].runs:
            r.bold = True
        shade(cell, "D9E2F3")
    for cells in body:
        row = table.add_row().cells
        for i in range(len(header)):
            text = cells[i] if i < len(cells) else ""
            row[i].text = ""
            add_runs(row[i].paragraphs[0], text, Pt(9))
    doc.add_paragraph()


def add_figure(doc, placeholder):
    # [[Figure 2.1: docs/diagrams/activity-UC-01.png. Figures 2.2 to 2.12: ...]]
    paths = re.findall(r"docs/diagrams/[A-Za-z0-9_.-]+\.png", placeholder)
    captions = re.findall(r"Figure[s]? [0-9.]+(?: to [0-9.]+)?", placeholder)
    inserted = False
    for path in paths:
        full = REPO / path
        if full.exists():
            doc.add_picture(str(full), width=Cm(16))
            doc.paragraphs[-1].alignment = 1
            cap = doc.add_paragraph()
            cap.alignment = 1
            r = cap.add_run(f"{captions[0] if captions else 'Figure'}: {Path(path).stem}")
            r.italic = True
            r.font.size = Pt(9)
            inserted = True
            captions = captions[1:] if len(captions) > 1 else captions
    if not inserted:
        p = doc.add_paragraph()
        r = p.add_run(placeholder)
        r.font.highlight_color = WD_COLOR_INDEX.YELLOW


def convert(md_path, out_path, title=None):
    doc = Document()
    style = doc.styles["Normal"]
    style.font.name = "Calibri"
    style.font.size = Pt(11)
    for section in doc.sections:
        section.left_margin = Cm(2.2)
        section.right_margin = Cm(2.2)
        section.top_margin = Cm(2.0)
        section.bottom_margin = Cm(2.0)

    lines = Path(md_path).read_text(encoding="utf-8").splitlines()
    i = 0
    para_buf = []

    def flush():
        nonlocal para_buf
        if para_buf:
            text = " ".join(s.strip() for s in para_buf)
            if text.startswith("[[Figure"):
                add_figure(doc, text)
            else:
                p = doc.add_paragraph()
                add_runs(p, text)
            para_buf = []

    while i < len(lines):
        line = lines[i]
        stripped = line.strip()
        if stripped.startswith("```"):
            flush()
            i += 1
            code = []
            while i < len(lines) and not lines[i].strip().startswith("```"):
                code.append(lines[i])
                i += 1
            add_code(doc, code)
            i += 1
            continue
        if stripped.startswith("<!--"):
            i += 1
            continue
        if stripped.startswith("|") and i + 1 < len(lines) and re.match(r"^\s*\|?\s*-{3,}", lines[i + 1]):
            flush()
            rows = []
            while i < len(lines) and lines[i].strip().startswith("|"):
                rows.append(lines[i])
                i += 1
            add_table(doc, rows)
            continue
        m = re.match(r"^(#{1,4})\s+(.*)$", stripped)
        if m:
            flush()
            level = len(m.group(1))
            text = m.group(2).strip()
            if level == 1 and title is None:
                doc.add_heading(text, 0)
            else:
                doc.add_heading(text, min(level, 4))
            i += 1
            continue
        if stripped == "---":
            flush()
            i += 1
            continue
        m = re.match(r"^(\s*)([-*])\s+(.*)$", line)
        if m:
            flush()
            p = doc.add_paragraph(style="List Bullet")
            add_runs(p, m.group(3))
            i += 1
            continue
        m = re.match(r"^(\s*)(\d+)\.\s+(.*)$", line)
        if m:
            flush()
            p = doc.add_paragraph(style="List Number")
            add_runs(p, m.group(3))
            i += 1
            continue
        if stripped == "":
            flush()
            i += 1
            continue
        para_buf.append(line)
        i += 1
    flush()
    doc.save(out_path)
    print("wrote", out_path)


if __name__ == "__main__":
    src, dst = sys.argv[1], sys.argv[2]
    convert(src, dst)
