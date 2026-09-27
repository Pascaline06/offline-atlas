#!/usr/bin/env python3
"""Build an Android-compatible, atomic article/place SQLite FTS4 index."""
import argparse
import datetime as dt
import json
import sqlite3
from pathlib import Path

SCHEMA = """
PRAGMA journal_mode=DELETE;
CREATE TABLE documents(id TEXT PRIMARY KEY, title TEXT NOT NULL, body TEXT NOT NULL,
 source TEXT NOT NULL, source_date TEXT NOT NULL, license TEXT NOT NULL);
CREATE INDEX documents_id_lower ON documents(lower(id));
CREATE VIRTUAL TABLE doc_search USING fts4(title, body, content='documents', tokenize=unicode61);
CREATE TRIGGER docs_ai AFTER INSERT ON documents BEGIN
 INSERT INTO doc_search(rowid,title,body) VALUES (new.rowid,new.title,new.body);
END;
CREATE TABLE places(id TEXT PRIMARY KEY, name TEXT NOT NULL, city TEXT NOT NULL,
 country TEXT NOT NULL, lat REAL NOT NULL, lon REAL NOT NULL,
 diet_vegan TEXT NOT NULL, diet_vegetarian TEXT NOT NULL, cuisine TEXT NOT NULL,
 address TEXT NOT NULL, source TEXT NOT NULL, source_date TEXT NOT NULL, license TEXT NOT NULL);
CREATE INDEX places_city ON places(city COLLATE NOCASE);
CREATE INDEX places_geo ON places(lat,lon);
CREATE TABLE cities(name TEXT NOT NULL, ascii_name TEXT NOT NULL, country TEXT NOT NULL,
 lat REAL NOT NULL, lon REAL NOT NULL, population INTEGER NOT NULL);
CREATE INDEX cities_name ON cities(name COLLATE NOCASE);
CREATE INDEX cities_ascii ON cities(ascii_name COLLATE NOCASE);
CREATE VIRTUAL TABLE place_search USING fts4(name,city,country,cuisine,content='places',tokenize=unicode61);
CREATE TRIGGER places_ai AFTER INSERT ON places BEGIN
 INSERT INTO place_search(rowid,name,city,country,cuisine) VALUES(new.rowid,new.name,new.city,new.country,new.cuisine);
END;
"""


def records(path):
    if isinstance(path, (list, tuple)):
        for item in path:
            yield from records(item)
        return
    with Path(path).open(encoding="utf-8") as handle:
        for line_number, line in enumerate(handle, 1):
            if line.strip():
                try:
                    yield json.loads(line)
                except json.JSONDecodeError as exc:
                    raise ValueError(f"{path}:{line_number}: {exc}") from exc


def validate_common(record):
    for key in ("id", "source", "source_date", "license"):
        if not isinstance(record.get(key), str) or not record[key].strip():
            raise ValueError(f"{record.get('id', '?')}: missing {key}")
    try:
        dt.date.fromisoformat(record["source_date"])
    except ValueError as exc:
        raise ValueError(f"{record['id']}: invalid source_date") from exc


def build(documents, places, output, cities=None):
    output = Path(output)
    output.parent.mkdir(parents=True, exist_ok=True)
    temp = output.with_name(output.name + '.building')
    temp.unlink(missing_ok=True)
    try:
        with sqlite3.connect(temp) as con:
            con.executescript(SCHEMA)
            for item in records(documents):
                validate_common(item)
                if not item.get("title") or not item.get("body"):
                    raise ValueError(f"{item['id']}: missing title or body")
                con.execute("INSERT INTO documents VALUES (?,?,?,?,?,?)", tuple(item[k] for k in
                    ("id", "title", "body", "source", "source_date", "license")))
            for item in records(places):
                validate_common(item)
                for key in ("name", "city", "country"):
                    if not item.get(key):
                        raise ValueError(f"{item['id']}: missing {key}")
                lat, lon = float(item["lat"]), float(item["lon"])
                if not (-90 <= lat <= 90 and -180 <= lon <= 180):
                    raise ValueError(f"{item['id']}: invalid coordinates")
                vegan = item.get("diet_vegan", "unknown")
                if vegan not in {"only", "yes", "limited", "no", "unknown"}:
                    raise ValueError(f"{item['id']}: invalid vegan tag")
                con.execute("INSERT INTO places VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)", (
                    item["id"], item["name"], item["city"], item["country"], lat, lon,
                    vegan, item.get("diet_vegetarian", "unknown"), item.get("cuisine", ""),
                    item.get("address", ""), item["source"], item["source_date"], item["license"]))
            if cities:
                with Path(cities).open(encoding='utf-8') as handle:
                    for line in handle:
                        fields=line.rstrip('\n').split('\t')
                        if len(fields)<15:
                            continue
                        try:
                            con.execute("INSERT INTO cities VALUES (?,?,?,?,?,?)",(
                                fields[1],fields[2],fields[8],float(fields[4]),float(fields[5]),int(fields[14])))
                        except ValueError:
                            continue
            con.execute("PRAGMA user_version=4")
            con.execute("INSERT INTO doc_search(doc_search) VALUES('optimize')")
            con.execute("INSERT INTO place_search(place_search) VALUES('optimize')")
            if con.execute('PRAGMA integrity_check').fetchone()[0] != 'ok':
                raise ValueError('SQLite integrity check failed')
        temp.replace(output)
    except Exception:
        temp.unlink(missing_ok=True)
        raise
    return output


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--documents", nargs='+', required=True)
    parser.add_argument("--places", nargs='+', required=True)
    parser.add_argument("--cities", help="Extracted GeoNames cities15000.txt")
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    print(build(args.documents, args.places, args.output,args.cities))
