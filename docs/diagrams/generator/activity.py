"""Swimlane activity-diagram layout on top of uxf.Diagram.

Lanes are columns; each lane declares named sub-columns (absolute x centres are
derived). Rows are a fixed pitch. Node helpers return uxf.Box so edges can be
attached to box edges. Edges are orthogonal polylines.
"""
from uxf import Diagram, Box, wrap

ROW_H = 40          # height of a one-line action box; rows are aligned on the top edge
LANE_TOP = 40       # y of the lane header
FIRST_ROW = 100     # y of row 0


class Flow:
    def __init__(self, title, lanes, pitch=100):
        """lanes: list of (title, width, {colname: (x_offset_in_lane, default_box_w)})"""
        self.d = Diagram()
        self.title = title
        self.pitch = pitch
        self.lanes = []
        x = 0
        for t, w, cols in lanes:
            self.lanes.append(dict(title=t, x=x, w=w,
                                   cols={k: (x + v[0], v[1]) for k, v in cols.items()}))
            x += w
        self.total_w = x
        self.max_y = FIRST_ROW
        self.notes = []

    # ---- geometry ------------------------------------------------------
    def ry(self, row):
        return FIRST_ROW + int(row * self.pitch)

    def cx(self, lane, col):
        return self.lanes[lane]["cols"][col][0]

    def bw(self, lane, col):
        return self.lanes[lane]["cols"][col][1]

    def _track(self, box):
        self.max_y = max(self.max_y, box.y + box.h)
        return box

    # ---- nodes ---------------------------------------------------------
    def action(self, lane, col, row, text, w=None, h=None, kind=None, bg=None):
        w = w or self.bw(lane, col)
        n = text.count("\n") + 1
        h = h or (40 if n == 1 else 50 if n == 2 else 60)
        x = self.cx(lane, col) - w // 2
        return self._track(self.d.state(x, self.ry(row), w, h, text, bg=bg, type_=kind))

    def send(self, lane, col, row, text, w=None, bg=None):
        return self.action(lane, col, row, text, w=w, kind="sender", bg=bg)

    def receive(self, lane, col, row, text, w=None, bg=None):
        return self.action(lane, col, row, text, w=w, kind="receiver", bg=bg)

    def event(self, lane, col, row, topic, w=None, extra_line=None):
        w = w or self.bw(lane, col)
        text = "<<event>>\n" + topic
        h = 40
        if extra_line:
            text += "\n" + extra_line
            h = 60
        x = self.cx(lane, col) - w // 2
        return self._track(self.d.obj(x, self.ry(row), w, h, text, bg="#F4F4F4"))

    def decision(self, lane, col, row):
        x = self.cx(lane, col) - 20
        return self._track(self.d.decision(x, self.ry(row), 40))

    def initial(self, lane, col, row):
        x = self.cx(lane, col) - 10
        return self._track(self.d.initial(x, self.ry(row) + 10, 20))

    def final(self, lane, col, row):
        x = self.cx(lane, col) - 10
        return self._track(self.d.final(x, self.ry(row) + 10, 20))

    def final_below(self, box, gap=25):
        """A final node 25px under a box, with the connecting arrow."""
        f = self._track(self.d.final(box.cx - 10, box.y + box.h + gap, 20))
        self.d.relation([box.bottom, f.top])
        return f

    def bar(self, lane, col_from, col_to, row, pad=100):
        x0 = self.cx(lane, col_from) - pad
        x1 = self.cx(lane, col_to) + pad
        return self._track(self.d.sync_bar(x0, self.ry(row) + 15, x1 - x0, 10))

    def note(self, x, y, w, h, text):
        return self._track(self.d.note(x, y, w, h, wrap(text, w), bg="#FFFBE6"))

    def question(self, dec, text, side="left"):
        """Short question text beside a decision diamond, e.g. 'more tool calls?'."""
        w = int(len(text) * 6.6 + 10) // 10 * 10 + 10
        if side == "left":
            return self.d.text(dec.x - w - 5, dec.y - 10, w, 20, text, wrap=False, extra="halign=right")
        return self.d.text(dec.x + dec.w + 5, dec.y - 10, w, 20, text, wrap=False)

    # ---- edges ---------------------------------------------------------
    def edge(self, points, m1=None, m2=None, label=None, lt="->"):
        self.d.relation(points, lt=lt, m1=m1, m2=m2, label=label)

    def v(self, a, b, **kw):
        """Vertical: a.bottom -> b.top (b below a). Uses a's x."""
        if b.y >= a.y + a.h:
            self.edge([a.bottom, b.top_at(a.cx)], **kw)
        else:
            self.edge([a.top, b.bottom_at(a.cx)], **kw)

    def h(self, a, b, **kw):
        """Horizontal at a's y: a.right -> b.left (b right of a) or a.left -> b.right."""
        if b.x >= a.x + a.w:
            self.edge([a.right, b.left_at(a.cy)], **kw)
        else:
            self.edge([a.left, b.right_at(a.cy)], **kw)

    def vh(self, a, b, **kw):
        """Down from a, then across into b's side."""
        start = a.bottom if b.cy > a.cy else a.top
        corner = (a.cx, b.cy)
        end = b.left if b.cx > a.cx else b.right
        self.edge([start, corner, end], **kw)

    def hv(self, a, b, **kw):
        """Across from a's side, then down into b's top."""
        start = a.right if b.cx > a.cx else a.left
        corner = (b.cx, a.cy)
        end = b.top if b.cy > a.cy else b.bottom
        self.edge([start, corner, end], **kw)

    # ---- output --------------------------------------------------------
    def finish(self, path, lane_pad_bottom=30, render_png=True):
        from uxf import render
        h = self.max_y + lane_pad_bottom - LANE_TOP
        lanes = []
        for ln in self.lanes:
            lanes.append(("UMLObject", ln["x"], LANE_TOP, ln["w"], h,
                          f"{ln['title']}\nvalign=top\nlayer=-1", ""))
        title = ("Text", 0, 0, max(600, self.total_w), 30,
                 f"{self.title}\nfontsize=16", "")
        self.d.elements = [title] + lanes + self.d.elements
        self.d.save(path)
        if render_png:
            return render(path)
        return path
