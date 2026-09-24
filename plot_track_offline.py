import json
import sys
from pathlib import Path

import matplotlib.pyplot as plt


if len(sys.argv) != 2:
    print(r"Usage: python D:\MAVEMap\plot_track_offline.py D:\track.geojson")
    sys.exit(1)

geojson_file = Path(sys.argv[1])

if not geojson_file.is_file():
    print(f"ERROR: File not found: {geojson_file}")
    sys.exit(1)


def extract_lines(obj):
    lines = []

    def add_geometry(g):
        if not g:
            return

        t = g.get("type")
        c = g.get("coordinates")

        if t == "LineString":
            if c:
                lines.append([(float(p[0]), float(p[1])) for p in c])

        elif t == "MultiLineString":
            for line in c or []:
                if line:
                    lines.append([(float(p[0]), float(p[1])) for p in line])

        elif t == "GeometryCollection":
            for child in g.get("geometries", []):
                add_geometry(child)

    if obj.get("type") == "FeatureCollection":
        for feature in obj.get("features", []):
            add_geometry(feature.get("geometry"))
    elif obj.get("type") == "Feature":
        add_geometry(obj.get("geometry"))
    else:
        add_geometry(obj)

    return lines


with open(geojson_file, "r", encoding="utf-8") as f:
    data = json.load(f)

lines = extract_lines(data)

if not lines:
    print("ERROR: No LineString or MultiLineString found.")
    sys.exit(1)

# Plot longitude on X and latitude on Y.
fig, ax = plt.subplots(figsize=(12, 8))

for line in lines:
    lon = [p[0] for p in line]
    lat = [p[1] for p in line]

    ax.plot(
        lon,
        lat,
        linewidth=2.5,
        label="Actual GNSS trajectory"
    )

# Start/end points
first = lines[0][0]
last = lines[-1][-1]

ax.scatter(
    first[0],
    first[1],
    s=100,
    marker="o",
    label="START",
    zorder=5
)

ax.scatter(
    last[0],
    last[1],
    s=120,
    marker="X",
    label="END",
    zorder=5
)

ax.annotate(
    "START",
    (first[0], first[1]),
    xytext=(8, 8),
    textcoords="offset points"
)

ax.annotate(
    "END",
    (last[0], last[1]),
    xytext=(8, 8),
    textcoords="offset points"
)

ax.set_title("MAVERiCK — Actual GNSS Trajectory")
ax.set_xlabel("Longitude (degrees)")
ax.set_ylabel("Latitude (degrees)")
ax.grid(True, alpha=0.3)
ax.legend()

# Keep geographic proportions approximately correct.
mean_lat = sum(p[1] for line in lines for p in line) / sum(len(line) for line in lines)
ax.set_aspect(1 / max(0.1, __import__("math").cos(__import__("math").radians(mean_lat))))

fig.tight_layout()

output = geojson_file.parent / "maverick_gnss_track.png"
fig.savefig(output, dpi=200, bbox_inches="tight")
plt.close(fig)

print()
print("SUCCESS")
print("==========================================")
print(f"Input : {geojson_file}")
print(f"Output: {output}")
print("==========================================")
print()
print("This is an OFFLINE image. No map tiles or internet are required.")
