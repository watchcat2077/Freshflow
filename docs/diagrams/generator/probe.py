"""Probe render: shapes and label placements used by the real generators."""
import os
from uxf import Diagram, render

HERE = os.path.dirname(os.path.abspath(__file__))
d = Diagram()

d.text(0, 0, 400, 30, "Probe: shapes and labels", wrap=False)
d.lane(0, 40, 400, 400, "Lane A")
d.lane(400, 40, 300, 400, "Kafka")

a = d.state(60, 80, 200, 40, "Publish batch-received", type_="sender")
b = d.state(60, 150, 200, 40, "Receive item-sold event", type_="receiver")
c = d.state(60, 220, 200, 50, "Two line action box\nsecond line here")
e = d.obj(450, 80, 200, 40, "<<event>>\nbatch-received")
e2 = d.obj(450, 150, 200, 40, "batch-received\nbg=#F0F0F0")

dec = d.decision(140, 300)
rej = d.state(240, 300, 150, 40, "Answer 400")
fin = d.final(300, 380)

# horizontal with m1 (near start) and m2 (near end)
d.relation([a.right, e.left], m1="m1here", m2="m2here")
# horizontal with middle label
d.relation([b.right, e2.left], label="middle")
# vertical with m2 and m1
d.relation([c.bottom, dec.top], m1="[m1 vert]", m2="[m2 vert]")
d.relation([dec.right, rej.left], m2="[m2 horiz]")
d.relation([dec.bottom, (dec.cx, 400)], label="[middle vert]")
d.relation([rej.bottom, fin.top], lt="->")

# class and package probes
d.package(0, 460, 400, 300, "Inventory & Batch", bg="#EAF2F8")
d.klass(20, 500, 220, 130, "StockBatch", stereotype="Aggregate Root",
        attrs=["batchId: BatchId", "sku: Sku"], ops=["deduct(Quantity)"], bg="#DCEBEE")
d.klass(260, 500, 120, 60, "BatchId", stereotype="Value Object", bg="#FFFFFF")
d.relation([(240, 530), (260, 530)], lt="<<<<-")
d.relation([(130, 630), (130, 700)], lt=".>", label="ADR 0005")
d.note(260, 600, 130, 80, "Note text that is fairly long and wraps?")
d.klass(20, 660, 220, 60, "BatchStatus", stereotype="enum", attrs=["ACTIVE", "DEPLETED"])
d.relation([(400, 520), (500, 520)], lt="<<-")
d.relation([(400, 560), (500, 560)], lt="<<.")
d.relation([(400, 600), (500, 600)], lt="<<<-")
d.relation([(400, 640), (500, 640)], lt="-", label="plain")
d.relation([(400, 680), (500, 680)], lt="<->")
d.text(400, 700, 300, 60, "Text wordwrap element with a long sentence that should wrap in the box.")
d.actor(520, 500, 60, 100, "Store\nmanager")

p = d.save(os.path.join(HERE, "probe.uxf"))
print(render(p))
