"""Generates the DXF test fixtures with ezdxf (independent third-party writer).

Run:  pip install ezdxf && python tools/dxf/generate_fixtures.py
Output goes to app/src/test/resources/dxf/. Fixtures are committed so CI needs no Python.
"""
import pathlib
import ezdxf
from ezdxf import units

OUT = pathlib.Path(__file__).resolve().parents[2] / "app/src/test/resources/dxf"
OUT.mkdir(parents=True, exist_ok=True)


def basic_r2000_meters():
    d = ezdxf.new("R2000", setup=False)
    d.units = units.M
    d.layers.add("Architectural")
    d.layers.add("Electrical")
    msp = d.modelspace()
    msp.add_line((0, 0), (6, 0), dxfattribs={"layer": "Architectural"})
    msp.add_lwpolyline([(0, 0, 0, 0, 0), (6, 0, 0, 0, 0.5), (6, 4, 0, 0, 0), (0, 4, 0, 0, 0)],
                       format="xyseb", close=True, dxfattribs={"layer": "Architectural"})
    msp.add_circle((3, 2), 0.35, dxfattribs={"layer": "Electrical"})
    msp.add_arc((1, 1), 0.9, 0, 90, dxfattribs={"layer": "Architectural"})
    msp.add_text("مجلس رجال", height=0.25,
                 dxfattribs={"insert": (2, 3), "rotation": 15, "layer": "Architectural"})
    msp.add_text("Majlis A", height=0.2, dxfattribs={"insert": (4, 3), "layer": "Electrical"})
    d.saveas(OUT / "basic_r2000_meters.dxf")


def unsupported_mix():
    d = ezdxf.new("R2000", setup=True)
    d.units = units.MM
    blk = d.blocks.new("DOOR")
    blk.add_line((0, 0), (900, 0))
    msp = d.modelspace()
    msp.add_line((0, 0), (1000, 0))  # the only supported entity
    msp.add_blockref("DOOR", (100, 0))
    msp.add_blockref("DOOR", (400, 0))
    msp.add_mtext("ملاحظة\\Pسطر ثان", dxfattribs={"insert": (0, 500), "char_height": 50})
    msp.add_hatch(color=2).paths.add_polyline_path([(0, 0), (100, 0), (100, 100)], is_closed=True)
    msp.add_spline([(0, 0), (100, 50), (200, 0), (300, 50)])
    msp.add_ellipse((500, 500), major_axis=(200, 0), ratio=0.5)
    msp.add_point((10, 10))
    msp.add_polyline3d([(0, 0, 0), (100, 0, 50), (100, 100, 100)])
    msp.add_linear_dim(base=(0, -200), p1=(0, 0), p2=(1000, 0)).render()
    d.saveas(OUT / "unsupported_mix.dxf")


def r12_polyline_unitless():
    d = ezdxf.new("R12", setup=False)
    msp = d.modelspace()
    msp.add_line((0, 0), (10, 0))
    msp.add_polyline2d([(0, 0), (10, 0), (10, 5), (0, 5)], close=True)
    d.saveas(OUT / "r12_polyline_unitless.dxf")


def z_and_extrusion():
    d = ezdxf.new("R2000", setup=False)
    d.units = units.M
    msp = d.modelspace()
    msp.add_line((0, 0, 0), (5, 0, 3))                       # 3D line: Z is dropped -> must be reported
    msp.add_arc((0, 0), 1, 0, 90, dxfattribs={"extrusion": (0, 0, -1)})   # mirrored OCS -> unsupported
    msp.add_circle((1, 1), 0.5)
    d.saveas(OUT / "z_and_extrusion.dxf")


if __name__ == "__main__":
    basic_r2000_meters()
    unsupported_mix()
    r12_polyline_unitless()
    z_and_extrusion()
    for p in sorted(OUT.glob("*.dxf")):
        print(p.name, p.stat().st_size)
