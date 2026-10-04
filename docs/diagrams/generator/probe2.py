import os
from uxf import Diagram, render

HERE = os.path.dirname(os.path.abspath(__file__))
d = Diagram()
# package behind (layer=-1) with bg
d.package(0, 0, 420, 300, "Inventory & Batch (layer=-1)", bg="#EAF2F8", extra="layer=-1")
d.klass(20, 40, 220, 130, "StockBatch", stereotype="Aggregate Root",
        attrs=["batchId: BatchId", "sku: Sku"], ops=["deduct(Quantity)"], bg="#DCEBEE")
d.klass(260, 40, 140, 60, "BatchId", stereotype="Value Object", bg="#FFFFFF")
d.klass(260, 120, 140, 60, "Plain no bg", stereotype="Value Object")
d.klass(20, 200, 220, 60, "transparency=0", stereotype="enum", attrs=["ACTIVE"], bg="#DCEBEE", extra="transparency=0")
d.note(260, 200, 140, 80, "Note bg", bg="#FFF8DC")

# arrow heads
y = 330
for lt in ["<<<<<-", "<<<<-", "<<<-", "<<-", "<<.", ".>", "<.", "->", "<-", "-"]:
    d.relation([(20, y), (200, y)], lt=lt, label=lt.replace("<", "&lt;") if False else lt)
    y += 40

# multi-segment with middle label and fg colour
d.relation([(260, 330), (400, 330), (400, 450), (260, 450)], lt="->", label="elbow label", extra="fg=#1F4E79")
d.relation([(260, 500), (400, 500), (400, 600)], lt=".>", m2="m2 end", m1="m1 start")
# package with lines and halign
d.package(0, 760, 300, 120, "presentation", lines=["BatchController", "CreateBatchRequestDto"], extra="halign=left")
d.package(320, 760, 300, 120, "application", lines=["BatchApplicationService", "ApplyItemSoldHandler"], bg="#F2F2F2", extra="layer=-1")
p = d.save(os.path.join(HERE, "probe2.uxf"))
print(render(p))
