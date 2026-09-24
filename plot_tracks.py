import json
import math
import sys
from pathlib import Path

import matplotlib.pyplot as plt


def extract_lines(geojson):
    """Extract GeoJSON LineString/MultiLineString coordinates.

    GeoJSON coordinates are [longitude, latitude].
    Returns lines as [(longitude, latitude), ...].
    """
    lines = []

    def process_geometry(geometry):
        if not geometry:
            return

        geometry_type = geometry.get("type")
        coordinates = geometry.get("coordinates")

        if geometry_type == "LineString":
            if coordinates:
                lines.append([
                    (float(point[0]), float(point[1]))
                    for point in coordinates
                ])

        elif geometry_type == "MultiLineString":
            for line in coordinates or []:
                if line:
                    lines.append([
                        (float(point[0]), float(point[1]))
                        for point in line
                    ])

        elif geometry_type == "GeometryCollection":
            for child in geometry.get("geometries", []):
                process_geometry(child)

    geojson_type = geojson.get("type")

    if geojson_type == "FeatureCollection":
        for feature in geojson.get("features", []):
            process_geometry(feature.get("geometry"))

    elif geojson_type == "Feature":
        process_geometry(geojson.get("geometry"))

    else:
        process_geometry(geojson)

    return lines


def create_png(geojson_path):
    """Create a PNG plot for one GeoJSON file."""
    try:
        with open(geojson_path, "r", encoding="utf-8") as file:
            data = json.load(file)
    except json.JSONDecodeError as error:
        print(f"ERROR: Invalid JSON in {geojson_path.name}")
        print(f"       {error}")
        return False
    except OSError as error:
        print(f"ERROR: Cannot read {geojson_path}")
        print(f"       {error}")
        return False

    lines = extract_lines(data)

    if not lines:
        print(f"SKIPPED: No route geometry found in {geojson_path.name}")
        return False

    all_points = [point for line in lines for point in line]

    if len(all_points) < 2:
        print(f"SKIPPED: Not enough points in {geojson_path.name}")
        return False

    # Longitude = X axis
    # Latitude  = Y axis
    fig, ax = plt.subplots(figsize=(12, 8))

    for line in lines:
        longitudes = [point[0] for point in line]
        latitudes = [point[1] for point in line]

        ax.plot(
            longitudes,
            latitudes,
            linewidth=2.5,
            label="GNSS trajectory"
        )

    # Start and end points
    start = all_points[0]
    end = all_points[-1]

    ax.scatter(
        start[0],
        start[1],
        s=100,
        marker="o",
        zorder=5,
        label="START"
    )

    ax.scatter(
        end[0],
        end[1],
        s=120,
        marker="X",
        zorder=5,
        label="END"
    )

    ax.annotate(
        "START",
        xy=start,
        xytext=(8, 8),
        textcoords="offset points"
    )

    ax.annotate(
        "END",
        xy=end,
        xytext=(8, 8),
        textcoords="offset points"
    )

    ax.set_title(f"MAVERiCK - {geojson_path.stem}")
    ax.set_xlabel("Longitude (degrees)")
    ax.set_ylabel("Latitude (degrees)")
    ax.grid(True, alpha=0.3)
    ax.legend()

    # Keep geographic scale approximately correct.
    mean_latitude = sum(
        point[1] for point in all_points
    ) / len(all_points)

    cos_latitude = math.cos(math.radians(mean_latitude))

    if abs(cos_latitude) > 0.01:
        ax.set_aspect(1 / abs(cos_latitude))

    fig.tight_layout()

    output_path = geojson_path.with_suffix(".png")

    try:
        fig.savefig(
            output_path,
            dpi=200,
            bbox_inches="tight"
        )
    except OSError as error:
        print(f"ERROR: Could not save {output_path}")
        print(f"       {error}")
        plt.close(fig)
        return False

    plt.close(fig)

    print(f"CREATED: {output_path}")
    return True


def main():
    if len(sys.argv) != 2:
        print("Usage:")
        print("  python plot_tracks.py D:\\")
        print()
        print("Example:")
        print("  python plot_tracks.py D:\\")
        sys.exit(1)

    folder = Path(sys.argv[1])

    if not folder.exists():
        print(f"ERROR: Folder does not exist: {folder}")
        sys.exit(1)

    if not folder.is_dir():
        print(f"ERROR: This is not a folder: {folder}")
        sys.exit(1)

    # Finds track1.geojson, track2.geojson, ... track5.geojson
    # and also supports track.geojson.
    files = sorted(
        folder.glob("track*.geojson"),
        key=lambda path: path.name.lower()
    )

    if not files:
        print(f"No track*.geojson files found in {folder}")
        sys.exit(1)

    print(f"Found {len(files)} GeoJSON files.")
    print()

    successful = 0

    for geojson_path in files:
        if create_png(geojson_path):
            successful += 1

    print()
    print("========================================")
    print("DONE")
    print("========================================")
    print(f"Processed: {len(files)}")
    print(f"Created  : {successful} PNG file(s)")
    print()
    print("PNG files are saved beside each GeoJSON file.")


if __name__ == "__main__":
    main()
