# Offline pack records

Input is UTF-8 JSON Lines. One object per line. All records require `id`, `source`, `source_date` (ISO YYYY-MM-DD), and `license`. The builder validates dates and rejects duplicate IDs, empty required fields, and invalid coordinates.

Article record:

```json
{"id":"wiki:example","title":"Example","body":"Source text.","source":"https://example.org/article","source_date":"2026-01-01","license":"CC BY-SA 4.0"}
```

Place record:

```json
{"id":"osm:node:1","name":"Example Kitchen","city":"Porto","country":"Portugal","lat":41.1,"lon":-8.6,"diet_vegan":"yes","diet_vegetarian":"yes","cuisine":"Portuguese","address":"Example street","source":"https://www.openstreetmap.org/node/1","source_date":"2026-01-01","license":"ODbL 1.0"}
```

`diet_vegan` accepts `only`, `yes`, `limited`, `no`, and `unknown`. Treat `yes` as evidence of vegan options, **not** proof the restaurant is fully vegan. Do not convert missing tags into positive claims. A future importer should preserve original OSM IDs, timestamps, and tags; respect ODbL attribution/share-alike and any source-specific rights. OSM venue presence, vegan tags, and coordinates do not prove quality, opening hours, or whether a business still operates. The UI explicitly avoids a "best" ranking without independent quality evidence.

Fixture URLs use `example.invalid` and fixture licenses say `TEST ONLY`; these records are fabricated to test the code.

`tools/import_osm_pbf.py` streams regional OSM PBF extracts, handling nodes and ways with usable locations. It accepts explicit positive `diet:vegan` tags and preserves places whose cuisine tag or name mentions vegan as **unverified leads**. It resolves missing `addr:city` from nearby GeoNames `cities15000.txt` records within 35 km. It skips venues that cannot be assigned a city and country, and cannot verify menus, hours or the OSM tag's freshness. The indexed GeoNames cities support 25 km geographic retrieval so a query for Tokyo includes its wards. `tools/import_osm_xml.py` remains a small XML-only fixture tool. Preserve the original extract URL, snapshot date, checksums and OpenStreetMap/GeoNames attribution with every distributed pack.

`tools/import_cirrus.py` streams dated Wikimedia CirrusSearch content shards. It filters to main namespace, records project, source URL, snapshot date and CC BY-SA attribution. Include a full contributor revision history reference at the URL. This converter does not prove article factual accuracy; it preserves evidence for on-device retrieval.
