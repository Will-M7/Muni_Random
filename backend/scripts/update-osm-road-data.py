#!/usr/bin/env python3
"""Fetch one bounded OSM road extract and write the compact local ODbL snapshot."""
import argparse
import json
import math
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "src/main/resources/osm/san-miguel-puno-roads.json"
ENDPOINT = "https://overpass-api.de/api/interpreter"
# Bounded corridor around San Miguel and nearby urban sectors of Juliaca.
SOUTH, WEST, NORTH, EAST = -15.52, -70.18, -15.36, -70.05
HIGHWAYS = {
    "motorway", "motorway_link", "trunk", "trunk_link", "primary", "primary_link",
    "secondary", "secondary_link", "tertiary", "tertiary_link", "unclassified",
    "residential", "living_street", "service", "road", "track", "cycleway",
    "footway", "path", "pedestrian", "steps",
}


def distance_to_segment(point, start, end):
    px, py = point
    ax, ay = start
    bx, by = end
    dx, dy = bx - ax, by - ay
    if dx == 0 and dy == 0:
        return math.hypot(px - ax, py - ay)
    t = max(0, min(1, ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)))
    return math.hypot(px - (ax + t * dx), py - (ay + t * dy))


def simplify(points, tolerance=3.0):
    if len(points) <= 2:
        return points
    mean_lat = sum(p[0] for p in points) / len(points)
    scale_x = 111320 * math.cos(math.radians(mean_lat))
    projected = [(lon * scale_x, lat * 110540) for lat, lon in points]
    keep = {0, len(points) - 1}
    stack = [(0, len(points) - 1)]
    while stack:
        start, end = stack.pop()
        index, distance = max(
            ((i, distance_to_segment(projected[i], projected[start], projected[end]))
             for i in range(start + 1, end)),
            key=lambda item: item[1], default=(-1, -1),
        )
        if distance > tolerance:
            keep.add(index)
            stack.extend(((start, index), (index, end)))
    return [points[i] for i in sorted(keep)]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, help="Use an existing Overpass JSON response instead of making a request")
    args = parser.parse_args()
    if args.input:
        source = json.loads(args.input.read_text(encoding="utf-8"))
    else:
        query = f'[out:json][timeout:25];way["highway"]({SOUTH},{WEST},{NORTH},{EAST});out geom;'
        request = urllib.request.Request(
            ENDPOINT,
            data=urllib.parse.urlencode({"data": query}).encode(),
            headers={"User-Agent": "SanMiguelFiscalizacionRoadSnapshot/1.0", "Content-Type": "application/x-www-form-urlencoded"},
        )
        with urllib.request.urlopen(request, timeout=45) as response:
            source = json.load(response)

    roads = []
    for element in source.get("elements", []):
        tags = element.get("tags", {})
        highway = tags.get("highway")
        geometry = element.get("geometry", [])
        if element.get("type") != "way" or highway not in HIGHWAYS or len(geometry) < 2:
            continue
        points = simplify([(float(p["lat"]), float(p["lon"])) for p in geometry])
        roads.append({"name": tags.get("name", ""), "highway": highway, "points": points})

    all_points = [point for road in roads for point in road["points"]]
    if not all_points:
        raise SystemExit("El extract no contiene vías utilizables; se conservó el archivo anterior.")
    output = {
        "source": "OpenStreetMap",
        "sourceUrl": "https://www.openstreetmap.org/",
        "license": "ODbL-1.0",
        "snapshotAt": source.get("osm3s", {}).get("timestamp_osm_base"),
        "queryBounds": [SOUTH, WEST, NORTH, EAST],
        "dataBounds": [
            min(p[0] for p in all_points), min(p[1] for p in all_points),
            max(p[0] for p in all_points), max(p[1] for p in all_points),
        ],
        "roads": roads,
    }
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(output, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
    print(f"Actualizado {OUTPUT}: {len(roads)} vías, {OUTPUT.stat().st_size} bytes, snapshot {output['snapshotAt']}")


if __name__ == "__main__":
    main()
