#!/usr/bin/env python3
"""Extract explicit vegan food venues from an OSM PBF; preserve provenance."""
import argparse
import json
import math
from pathlib import Path

FOOD = {'restaurant', 'cafe', 'fast_food', 'food_court', 'ice_cream'}
VEGAN = {'only', 'yes', 'limited'}


def load_cities(path):
    """Index GeoNames cities15000.txt (CC BY 4.0) in one-degree cells."""
    cities = {}
    with open(path, encoding='utf-8') as handle:
        for line in handle:
            fields = line.rstrip('\n').split('\t')
            if len(fields) >= 15:
                try:
                    lat, lon = float(fields[4]), float(fields[5])
                    cities.setdefault((math.floor(lat), math.floor(lon)), []).append(
                        (fields[1], fields[8], lat, lon, int(fields[14])))
                except ValueError:
                    continue
    return cities


def closest_city(lat, lon, cities, country=None, max_km=35):
    match = None
    best = float('inf')
    if isinstance(cities, dict):
        lat_width = max_km / 111.2
        lon_width = max_km / (111.2 * max(0.01, math.cos(math.radians(lat))))
        candidates = (item for y in range(math.floor(lat-lat_width),math.floor(lat+lat_width)+1)
                      for x in range(math.floor(lon-lon_width),math.floor(lon+lon_width)+1)
                      for item in cities.get((y,x), ()))
    else:
        candidates = iter(cities)
    for city, nation, clat, clon, population in candidates:
        if country and nation != country:
            continue
        dy = (clat-lat)*111.2
        dx = (clon-lon)*111.2*math.cos(math.radians(lat))
        dist = math.hypot(dx,dy)
        if dist < best and dist <= max_km:
            match, best = (city,nation), dist
    return match


def convert(pbf, cities_file, output, snapshot, max_km=35):
    try:
        import osmium
    except ImportError as error:
        raise RuntimeError('Install pyosmium: python3 -m pip install osmium==4.3.1') from error
    cities = load_cities(cities_file)
    count = 0
    target = Path(output)
    target.parent.mkdir(parents=True, exist_ok=True)
    def emit(entity, kind, lat, lon, out):
            nonlocal count
            tags = entity.tags
            diet = tags.get('diet:vegan', '').lower()
            cuisine = tags.get('cuisine','').lower()
            name = tags.get('name','')
            has_cuisine_clue = 'vegan' in cuisine.split(';')
            has_name_clue = 'vegan' in name.lower()
            if tags.get('amenity') not in FOOD or not name or (diet not in VEGAN and not has_cuisine_clue and not has_name_clue):
                return
            country = tags.get('addr:country')
            near = closest_city(lat, lon, cities, country, max_km)
            city = tags.get('addr:city') or (near[0] if near else None)
            nation = country or (near[1] if near else None)
            if not city or not nation:
                return
            address = ' '.join(filter(None, (tags.get('addr:housenumber'), tags.get('addr:street')))).strip()
            entry = {'id': f'osm:{kind}:{entity.id}', 'name': tags['name'], 'city': city,
                     'country': nation, 'lat': lat, 'lon': lon, 'diet_vegan': diet if diet in VEGAN else 'unknown',
                     'diet_vegetarian': tags.get('diet:vegetarian','unknown'),
                     'cuisine': tags.get('cuisine',''), 'address': address,
                     'source': f'https://www.openstreetmap.org/{kind}/{entity.id}',
                     'source_date': snapshot, 'license': 'ODbL 1.0; © OpenStreetMap contributors'}
            out.write(json.dumps(entry, ensure_ascii=False)+'\n')
            count += 1
    with target.open('w',encoding='utf-8') as out:
        processor = osmium.FileProcessor(str(pbf), osmium.osm.NODE | osmium.osm.WAY)
        processor.with_locations().with_filter(osmium.filter.TagFilter(*(('amenity', value) for value in FOOD)))
        for entity in processor:
            if isinstance(entity, osmium.osm.Node) and entity.location.valid():
                emit(entity,'node',entity.location.lat,entity.location.lon,out)
            elif isinstance(entity, osmium.osm.Way):
                points = [(n.lat,n.lon) for n in entity.nodes if n.location.valid()]
                if points:
                    emit(entity,'way',sum(p[0] for p in points)/len(points),sum(p[1] for p in points)/len(points),out)
    return count


if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('pbf'); parser.add_argument('cities'); parser.add_argument('output')
    parser.add_argument('--snapshot',required=True,help='YYYY-MM-DD date of OSM extract')
    args=parser.parse_args()
    print(convert(args.pbf,args.cities,args.output,args.snapshot))
