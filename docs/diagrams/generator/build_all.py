"""Render every diagram into docs/diagrams/ and mirror .uxf/.png into the external 'UML diagram' folder."""
import os
import shutil
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = r"E:\UOW 2026 spring\CSCI318 Software Engineering Practices&Principles\FreshFlow"
DOCS = os.path.join(REPO, "docs", "diagrams")
MIRROR = r"E:\UOW 2026 spring\CSCI318 Software Engineering Practices&Principles\UML diagram"

os.makedirs(DOCS, exist_ok=True)
os.makedirs(MIRROR, exist_ok=True)
os.environ["UML_OUT"] = DOCS
sys.path.insert(0, HERE)

import gen_activity, gen_domain, gen_layered, gen_db, gen_eventflow, gen_context  # noqa: E402

produced = []
for key in sorted(gen_activity.ALL):
    f = gen_activity.ALL[key]()
    produced.append(f.finish(os.path.join(DOCS, f"activity-UC-{key}.uxf")))
produced.append(gen_domain.build())
produced.append(gen_layered.build_inventory())
produced.append(gen_layered.build_all())
produced.append(gen_db.build())
produced.append(gen_eventflow.build())
produced.append(gen_context.build())

from PIL import Image  # noqa: E402
print("file,width,height")
for png in produced:
    uxf = png[:-4] + ".uxf"
    for src in (png, uxf):
        shutil.copy2(src, os.path.join(MIRROR, os.path.basename(src)))
    w, h = Image.open(png).size
    print(f"{os.path.basename(png)},{w},{h}")
