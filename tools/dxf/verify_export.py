"""Independent check of a DXF exported by the app, using ezdxf (strict reader + auditor).

Run:  pip install ezdxf && python tools/dxf/verify_export.py exported.dxf
Exit code 0 only if ezdxf reads the file strictly and the audit finds no errors.
"""
import sys
import ezdxf
from ezdxf import recover

path = sys.argv[1]
doc = ezdxf.readfile(path)            # strict: raises on structural errors
auditor = doc.audit()
print("version:", doc.dxfversion, "| units code:", doc.header.get("$INSUNITS"))
print("layers:", sorted(l.dxf.name for l in doc.layers))
for e in doc.modelspace():
    d = e.dxf
    extra = ""
    if e.dxftype() == "TEXT":
        extra = " text=" + repr(d.text)
    elif e.dxftype() == "LWPOLYLINE":
        extra = f" points={list(e.get_points('xyb'))} closed={e.closed}"
    print(f"  {e.dxftype():10s} layer={d.layer}{extra}")
print("audit errors:", len(auditor.errors), "| fixes:", len(auditor.fixes))
for err in auditor.errors:
    print("  ERROR:", err.code, err.message)
sys.exit(1 if auditor.errors else 0)
