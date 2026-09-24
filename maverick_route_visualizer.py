import json
import sys
from pathlib import Path

try:
    import folium
except ImportError:
    print("Folium is not installed.")
    print("Run: pip install folium")
    sys.exit(1)

if len(sys.argv) != 2:
    print(r"Usage: python D:\MAVEMap\maverick_route_visualizer.py D:\track.geojson")
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
                lines.append([(float(p[1]), float(p[0])) for p in c])

        elif t == "MultiLineString":
            for line in c or []:
                if line:
                    lines.append([(float(p[1]), float(p[0])) for p in line])

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
    print("ERROR: No LineString/MultiLineString found in track.geojson")
    sys.exit(1)

points = [p for line in lines for p in line]

min_lat = min(p[0] for p in points)
max_lat = max(p[0] for p in points)
min_lon = min(p[1] for p in points)
max_lon = max(p[1] for p in points)

center = [
    (min_lat + max_lat) / 2,
    (min_lon + max_lon) / 2,
]

# IMPORTANT:
# Do not use OpenStreetMap volunteer tiles.
# The first map layer below uses Esri World Street Map.
# A second CARTO layer is available as a fallback.
m = folium.Map(
    location=center,
    zoom_start=15,
    control_scale=True,
    tiles=None
)

esri = folium.TileLayer(
    tiles="https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/{z}/{y}/{x}",
    attr="Tiles © Esri",
    name="Street Map",
    overlay=False,
    control=True,
    max_zoom=19
)
esri.add_to(m)

carto = folium.TileLayer(
    tiles="https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png",
    attr="© CARTO",
    name="Light Map",
    subdomains="abcd",
    overlay=False,
    control=True,
    max_zoom=20
)
carto.add_to(m)

# Actual GNSS path
for line in lines:
    folium.PolyLine(
        line,
        color="#1976D2",
        weight=5,
        opacity=0.95,
        tooltip="Actual GNSS trajectory"
    ).add_to(m)

# Start/end
folium.Marker(
    points[0],
    tooltip="START",
    popup="Actual GNSS start"
).add_to(m)

folium.Marker(
    points[-1],
    tooltip="END",
    popup="Actual GNSS end"
).add_to(m)

# Fit to the trajectory
m.fit_bounds([
    [min_lat, min_lon],
    [max_lat, max_lon]
])

folium.LayerControl(collapsed=False).add_to(m)

# Make a fresh filename so the browser cannot show the old cached HTML.
output = geojson_file.parent / "maverick_route_map_v2.html"
m.save(output)

print()
print("SUCCESS")
print("==========================================")
print(f"Input : {geojson_file}")
print(f"Output: {output}")
print("==========================================")
print()
print("Open THIS file, not the previous maverick_route_map.html:")
print(output)
