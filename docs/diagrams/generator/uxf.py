"""Minimal UMLet 15.1 .uxf writer with absolute-coordinate helpers.

Every helper takes absolute canvas coordinates on a 10px grid. Relations take
absolute polyline points and the element box (with a 10px margin) is derived.
"""
from __future__ import annotations

import os
import subprocess
from dataclasses import dataclass, field
from xml.sax.saxutils import escape

UMLET_JAR = r"E:\UOW 2026 spring\ISIT312 Big Data Management\UMLet-15.1\umlet.jar"

PX_PER_CHAR = 6.6   # measured on the probe render at zoom 10


def wrap(text, width_px, pad=20):
    """Hard-wrap text to fit a box of width_px (UMLNote/UMLClass do not wrap)."""
    import textwrap
    cols = max(8, int((width_px - pad) / PX_PER_CHAR))
    out = []
    for para in text.split("\n"):
        out.extend(textwrap.wrap(para, cols) or [""])
    return "\n".join(out)


@dataclass
class Box:
    x: int
    y: int
    w: int
    h: int

    @property
    def cx(self):
        return self.x + self.w // 2

    @property
    def cy(self):
        return self.y + self.h // 2

    @property
    def top(self):
        return (self.cx, self.y)

    @property
    def bottom(self):
        return (self.cx, self.y + self.h)

    @property
    def left(self):
        return (self.x, self.cy)

    @property
    def right(self):
        return (self.x + self.w, self.cy)

    def top_at(self, x):
        return (x, self.y)

    def bottom_at(self, x):
        return (x, self.y + self.h)

    def left_at(self, y):
        return (self.x, y)

    def right_at(self, y):
        return (self.x + self.w, y)


@dataclass
class Diagram:
    zoom: int = 10
    elements: list = field(default_factory=list)

    # ---- raw ---------------------------------------------------------
    def add(self, kind, x, y, w, h, attrs, extra=""):
        self.elements.append((kind, int(x), int(y), int(w), int(h), attrs, extra))
        return Box(int(x), int(y), int(w), int(h))

    # ---- element helpers --------------------------------------------
    def state(self, x, y, w, h, text, bg=None, type_=None):
        a = text
        if bg:
            a += f"\nbg={bg}"
        if type_:
            a += f"\ntype={type_}"
        return self.add("UMLState", x, y, w, h, a)

    def initial(self, x, y, size=20):
        return self.add("UMLSpecialState", x, y, size, size, "type=initial")

    def final(self, x, y, size=20):
        return self.add("UMLSpecialState", x, y, size, size, "type=final")

    def flow_final(self, x, y, size=20):
        return self.add("UMLSpecialState", x, y, size, size, "type=flow_final")

    def decision(self, x, y, size=40):
        return self.add("UMLSpecialState", x, y, size, size, "type=decision")

    def sync_bar(self, x, y, w, h=10):
        return self.add("UMLSyncBarHorizontal", x, y, w, h, "lw=5")

    def lane(self, x, y, w, h, title, bg=None):
        a = f"{title}\nvalign=top\nlayer=-1"
        if bg:
            a += f"\nbg={bg}"
        return self.add("UMLObject", x, y, w, h, a)

    def obj(self, x, y, w, h, text, bg=None):
        a = text
        if bg:
            a += f"\nbg={bg}"
        return self.add("UMLObject", x, y, w, h, a)

    def text(self, x, y, w, h, text, wrap=True, extra=""):
        a = text
        if wrap:
            a += "\nstyle=wordwrap"
        if extra:
            a += "\n" + extra
        return self.add("Text", x, y, w, h, a)

    def note(self, x, y, w, h, text, bg=None):
        a = text
        if bg:
            a += f"\nbg={bg}"
        return self.add("UMLNote", x, y, w, h, a)

    def package(self, x, y, w, h, name, lines=(), bg=None, extra=""):
        a = name
        if lines:
            a += "\n--\n" + "\n".join(lines)
        if bg:
            a += f"\nbg={bg}"
        if extra:
            a += "\n" + extra
        return self.add("UMLPackage", x, y, w, h, a)

    def klass(self, x, y, w, h, name, stereotype=None, attrs=(), ops=(), bg=None, extra=""):
        parts = []
        head = ""
        if stereotype:
            head += f"<<{stereotype}>>\n"
        head += name
        parts.append(head)
        if attrs or ops:
            parts.append("\n".join(attrs) if attrs else "")
        if ops:
            parts.append("\n".join(ops))
        a = "\n--\n".join(parts)
        if bg:
            a += f"\nbg={bg}"
        if extra:
            a += "\n" + extra
        return self.add("UMLClass", x, y, w, h, a)

    def actor(self, x, y, w, h, name):
        return self.add("UMLActor", x, y, w, h, name)

    def relation(self, points, lt="->", m1=None, m2=None, label=None, extra=""):
        """points: list of absolute (x, y). Arrow head at the END for lt='->'."""
        xs = [p[0] for p in points]
        ys = [p[1] for p in points]
        x0, y0 = min(xs) - 10, min(ys) - 10
        w, h = max(xs) - min(xs) + 20, max(ys) - min(ys) + 20
        attrs = f"lt={lt}"
        if label:
            attrs += f"\n{label}"
        if m1:
            attrs += f"\nm1={m1}"
        if m2:
            attrs += f"\nm2={m2}"
        if extra:
            attrs += "\n" + extra
        rel = ";".join(f"{float(px - x0):.1f};{float(py - y0):.1f}" for px, py in points)
        self.elements.append(("Relation", x0, y0, w, h, attrs, rel))

    # ---- output -----------------------------------------------------
    def to_xml(self):
        out = ['<?xml version="1.0" encoding="UTF-8" standalone="no"?>',
               '<diagram program="umlet" version="15.1">',
               f'  <zoom_level>{self.zoom}</zoom_level>']
        for kind, x, y, w, h, attrs, extra in self.elements:
            out.append('  <element>')
            out.append(f'    <id>{kind}</id>')
            out.append(f'    <coordinates><x>{x}</x><y>{y}</y><w>{w}</w><h>{h}</h></coordinates>')
            out.append(f'    <panel_attributes>{escape(attrs)}</panel_attributes>')
            if extra:
                out.append(f'    <additional_attributes>{escape(extra)}</additional_attributes>')
            else:
                out.append('    <additional_attributes/>')
            out.append('  </element>')
        out.append('</diagram>')
        return "\n".join(out) + "\n"

    def save(self, path):
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            f.write(self.to_xml())
        return path


def render(uxf_path):
    """Render a .uxf to a .png next to it with UMLet's CLI. Returns the png path."""
    base = os.path.splitext(uxf_path)[0]
    cmd = ["java", "-jar", UMLET_JAR, "-action=convert", "-format=png",
           f"-filename={uxf_path}", f"-output={base}"]
    r = subprocess.run(cmd, capture_output=True, text=True)
    msg = (r.stdout + r.stderr)
    if "Conversion finished" not in msg:
        raise RuntimeError(f"UMLet failed for {uxf_path}:\n{msg}")
    return base + ".png"
