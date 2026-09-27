#!/usr/bin/env python3
"""Convert a regional OSM XML node extract into provenance-bearing place JSONL.

OSM .pbf must first be converted to .osm by a standard OSM tool. This importer
only reads node POIs; ways and relations require a separate geometry-aware pass.
"""
import argparse
import datetime as dt
import json
import xml.etree.ElementTree as ET
from pathlib import Path


def convert(input_path, output_path):
    count = 0
    with Path(output_path).open("w", encoding="utf-8") as output:
        events = ET.iterparse(input_path, events=("start", "end"))
        _, root = next(events)
        for event, node in events:
            if event != "end":
                continue
            if node.tag != "node":
                continue
            tags = {child.get("k"): child.get("v") for child in node if child.tag == "tag"}
            if tags.get("amenity") not in {"restaurant", "cafe", "fast_food", "food_court"}:
                root.clear()
                continue
            if not tags.get("name") or not tags.get("addr:city"):
                root.clear()
                continue
            date = node.get("timestamp", "")[:10]
            try:
                dt.date.fromisoformat(date)
                lat, lon = float(node.get("lat")), float(node.get("lon"))
            except (ValueError, TypeError):
                root.clear()
                continue
            diet = tags.get("diet:vegan", "unknown")
            if diet not in {"only", "yes", "limited", "no"}:
                diet = "unknown"
            record = {
                "id": "osm:node:" + node.get("id"), "name": tags["name"],
                "city": tags["addr:city"], "country": tags.get("addr:country", "Unknown"),
                "lat": lat, "lon": lon, "diet_vegan": diet,
                "diet_vegetarian": tags.get("diet:vegetarian", "unknown"),
                "cuisine": tags.get("cuisine", ""),
                "address": " ".join(filter(None, [tags.get("addr:housenumber"), tags.get("addr:street")])),
                "source": "https://www.openstreetmap.org/node/" + node.get("id"),
                "source_date": date, "license": "ODbL 1.0",
            }
            output.write(json.dumps(record, ensure_ascii=False, sort_keys=True) + "\n")
            count += 1
            root.clear()
    return count


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("input_xml")
    parser.add_argument("output_jsonl")
    args = parser.parse_args()
    print(f"Exported {convert(args.input_xml, args.output_jsonl)} node places")
