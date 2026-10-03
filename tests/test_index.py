import json
import sqlite3
import tempfile
import unittest
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
from build_index import build
from query_index import search
from import_osm_xml import convert
from import_cirrus import convert as convert_cirrus
from import_osm_pbf import closest_city, load_cities


class IndexTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.path = Path(self.temp.name) / "atlas.db"
        build(ROOT / "data/sample_documents.jsonl", ROOT / "data/sample_places.jsonl", self.path)
        self.con = sqlite3.connect(self.path)

    def tearDown(self):
        self.con.close()
        self.temp.cleanup()

    def test_vegan_filter_excludes_nonvegan_place_and_exposes_provenance(self):
        result = search(self.con, "Tell me the best vegan restaurants in Porto")
        self.assertEqual([r["id"] for r in result["results"]], ["fixture:porto:1", "fixture:porto:2"])
        self.assertTrue(all(r["source_date"] and r["source"] for r in result["results"]))
        self.assertIn("cannot establish", result["warning"])

    def test_duplicate_osm_place_keeps_one_local_result(self):
        original=self.con.execute("SELECT * FROM places WHERE id='fixture:porto:1'").fetchone()
        self.con.execute("INSERT INTO places VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",('duplicate:porto:1',*original[1:]))
        result=search(self.con,'vegan restaurants in Porto')
        self.assertEqual(len(result['results']),2)

    def test_no_city_does_not_invent_venue(self):
        result = search(self.con, "best vegan restaurants")
        self.assertEqual(result["results"], [])
        self.assertIn("specify a city", result["warning"])

    def test_travel_city_can_be_followed_by_time_words(self):
        result=search(self.con,'vegan restaurants in Porto right now')
        self.assertEqual([item['id'] for item in result['results']],
                         ['fixture:porto:1','fixture:porto:2'])

    def test_missing_vegan_tags_return_guide_leads_without_claiming_verified_venues(self):
        self.con.execute("""INSERT INTO documents VALUES (?,?,?,?,?,?)""",(
            'enwikivoyage:Tokyo','Tokyo','{{eat | name=East Cafe | content=Vegan bread and coffee. | lastedit=2024-03-11 }}',
            'https://en.wikivoyage.org/wiki/Tokyo','2026-09-20','CC BY-SA 4.0'))
        answer=search(self.con,'vegan restaurants in Tokyo')
        self.assertEqual(answer['kind'],'guide_listings')
        self.assertIn('unverified',answer['warning'])
        self.assertEqual(answer['results'][0]['title'],'East Cafe')
        self.assertEqual(answer['results'][0]['lastedit'],'2024-03-11')

    def test_cuisine_vegan_lead_keeps_diet_unknown(self):
        self.con.execute("""INSERT INTO places VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)""",(
            'osm:node:99','Plant Kitchen','Lagos','NG',6.5,3.4,'unknown','unknown',
            'vegan;african','','https://www.openstreetmap.org/node/99','2026-09-20','ODbL 1.0'))
        answer=search(self.con,'vegan restaurants in Lagos')
        self.assertEqual(answer['kind'],'unverified_leads')
        self.assertEqual(answer['results'][0]['diet_vegan'],'unknown')
        self.assertIn('unverified',answer['warning'])

    def test_city_coordinates_include_ward_venues(self):
        self.con.execute("INSERT INTO cities VALUES (?,?,?,?,?,?)",('Tokyo','Tokyo','JP',35.68,139.69,9000000))
        self.con.execute("""INSERT INTO places VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)""",(
            'osm:node:100','Ward Cafe','Shibuya','JP',35.66,139.70,'yes','unknown',
            'cafe','','https://www.openstreetmap.org/node/100','2026-09-20','ODbL 1.0'))
        answer=search(self.con,'vegan restaurants in Tokyo')
        self.assertEqual(answer['kind'],'places')
        self.assertEqual(answer['results'][0]['name'],'Ward Cafe')

    def test_same_city_name_can_select_country(self):
        self.con.executemany("INSERT INTO cities VALUES (?,?,?,?,?,?)",[
            ('Lagos','Lagos','NG',6.5,3.4,15000000),
            ('Lagos','Lagos','PT',37.1,-8.7,30000)])
        self.con.executemany("INSERT INTO places VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",[
            ('osm:node:101','Nigerian Cafe','Lagos','NG',6.5,3.4,'yes','unknown','','','https://example.invalid/ng','2026-09-20','TEST ONLY'),
            ('osm:node:102','Portuguese Cafe','Lagos','PT',37.1,-8.7,'yes','unknown','','','https://example.invalid/pt','2026-09-20','TEST ONLY')])
        rows=search(self.con,'best vegan restaurants in Lagos, Portugal')['results']
        self.assertEqual([item['name'] for item in rows],['Portuguese Cafe'])

    def test_source_retrieval_includes_citation(self):
        result = search(self.con, "compare boiling and water filters")
        self.assertEqual(result["results"][0]["id"], "fixture:water")
        self.assertEqual(result["results"][0]["source"], "https://example.invalid/water")

    def test_plural_retrieval_finds_singular_topic(self):
        self.con.execute("INSERT INTO documents VALUES (?,?,?,?,?,?)",(
            'fixture:monsoon','Monsoon','A monsoon is a seasonal wind system associated with heavy rainfall.',
            'https://example.invalid/monsoon','2026-09-01','TEST ONLY'))
        self.assertEqual(search(self.con,'What causes monsoons?')['results'][0]['title'],'Monsoon')

    def test_history_query_finds_subject_before_film_title(self):
        self.con.executemany("INSERT INTO documents VALUES (?,?,?,?,?,?)", [
            ('fixture:roman','Roman Empire','History of the Roman Empire and its decline.',
             'https://example.invalid/roman','2026-09-01','TEST ONLY'),
            ('fixture:movie','The Fall of the Roman Empire (movie)','A movie about the fall of the Roman Empire.',
             'https://example.invalid/movie','2026-09-01','TEST ONLY')])
        self.assertEqual(search(self.con,'Why did the Roman Empire fall?')['results'][0]['title'],'Roman Empire')

    def test_london_guide_excludes_london_ontario(self):
        self.con.executemany("INSERT INTO documents VALUES (?,?,?,?,?,?)", [
            ('enwikivoyage:London/Ontario','London (Ontario)','{{eat | name=Ontario Table | content=Vegan food. }}',
             'https://example.invalid/ontario','2026-09-01','TEST ONLY'),
            ('enwikivoyage:London','London','{{eat | name=London Table | content=Vegan food. }}',
             'https://example.invalid/london','2026-09-01','TEST ONLY')])
        rows=search(self.con,'best vegan restaurants in London')['results']
        self.assertEqual([r['title'] for r in rows],['London Table'])

    def test_comparison_recovers_both_subjects_instead_of_mixed_topic(self):
        self.con.executemany("INSERT INTO documents VALUES (?,?,?,?,?,?)",[
            ('fixture:plant','Solar power plant','Solar power plant makes electricity.',
             'https://example.invalid/solar','2026-09-01','TEST ONLY'),
            ('fixture:wind','Wind power','Wind power makes electricity.',
             'https://example.invalid/wind','2026-09-01','TEST ONLY'),
            ('fixture:solar-wind','Solar wind','Solar wind is a particle stream.',
             'https://example.invalid/solar-wind','2026-09-01','TEST ONLY')])
        r=search(self.con,'What is the difference between solar and wind power?')
        self.assertEqual(r['kind'],'comparison')
        self.assertEqual([v['title'] for v in r['results']],['Solar power plant','Wind power'])

    def test_partial_comparison_is_not_labeled_complete(self):
        self.con.execute("INSERT INTO documents VALUES (?,?,?,?,?,?)",(
            'simplewiki:Photosynthesis','Photosynthesis','Plants use light to make sugars.',
            'https://example.invalid/plants','2026-09-01','TEST ONLY'))
        r=search(self.con,'Photosynthesis vs respiration')
        self.assertEqual(r['kind'],'partial_comparison')
        self.assertIn('partial',r['warning'])

    def test_lowercase_question_finds_case_sensitive_source_ids(self):
        self.con.executemany("INSERT INTO documents VALUES (?,?,?,?,?,?)",[
            ('simplewiki:Photosynthesis','Photosynthesis','Plants use light to make sugars.',
             'https://example.invalid/plants','2026-09-01','TEST ONLY'),
            ('simplewiki:Respiration','Respiration','Living cells use energy.',
             'https://example.invalid/cells','2026-09-01','TEST ONLY')])
        r=search(self.con,'photosynthesis vs respiration')
        self.assertEqual(r['kind'],'comparison')
        self.assertEqual([v['id'] for v in r['results']],
                         ['simplewiki:Photosynthesis','simplewiki:Respiration'])

    def test_comparison_does_not_force_unshared_second_subject_noun(self):
        self.con.executemany("INSERT INTO documents VALUES (?,?,?,?,?,?)",[
            ('simplewiki:Malaria','Malaria','Malaria is caused by a parasite.',
             'https://example.invalid/malaria','2026-09-01','TEST ONLY'),
            ('simplewiki:Dengue fever','Dengue fever','Dengue fever is caused by a virus.',
             'https://example.invalid/dengue','2026-09-01','TEST ONLY')])
        answer=search(self.con,'What is the difference between malaria and dengue fever?')
        self.assertEqual([row['title'] for row in answer['results']],['Malaria','Dengue fever'])

    def test_reject_missing_provenance_without_retaining_partial_db(self):
        documents = Path(self.temp.name) / "invalid.jsonl"
        documents.write_text(json.dumps({"id":"bad","title":"X","body":"Y","source":"https://example.invalid"})+"\n")
        self.con.close()
        self.con = sqlite3.connect(":memory:")
        self.path.unlink()
        with self.assertRaises(ValueError):
            build(documents, ROOT / "data/sample_places.jsonl", self.path)
        self.assertFalse(self.path.exists())

    def test_osm_import_does_not_infer_vegan_from_cuisine(self):
        xml = Path(self.temp.name) / "sample.osm"
        out = Path(self.temp.name) / "places.jsonl"
        xml.write_text('''<osm><node id="7" lat="41.1" lon="-8.6" timestamp="2026-08-02T01:00:00Z"><tag k="amenity" v="restaurant"/><tag k="name" v="Vegan-sounding name"/><tag k="addr:city" v="Porto"/><tag k="cuisine" v="vegan"/></node><node id="8" lat="41.2" lon="-8.6" timestamp="2026-08-02T01:00:00Z"><tag k="amenity" v="restaurant"/><tag k="name" v="Real options"/><tag k="addr:city" v="Porto"/><tag k="diet:vegan" v="yes"/></node></osm>''')
        self.assertEqual(convert(xml,out),2)
        entries=[json.loads(line) for line in out.read_text().splitlines()]
        self.assertEqual([item["diet_vegan"] for item in entries],["unknown","yes"])

    def test_failed_rebuild_preserves_usable_pack(self):
        documents = Path(self.temp.name) / "bad.jsonl"
        documents.write_text('{"id":"bad"}\n')
        self.assertEqual(self.con.execute('PRAGMA user_version').fetchone()[0], 5)
        with self.assertRaises(ValueError):
            build(documents, ROOT / "data/sample_places.jsonl", self.path)
        self.assertEqual(self.con.execute('PRAGMA integrity_check').fetchone()[0], 'ok')
        self.assertEqual(sqlite3.connect(self.path).execute('PRAGMA user_version').fetchone()[0], 5)

    def test_cirrus_import_preserves_article_license_and_url(self):
        shard=Path(self.temp.name)/'voyage.json'
        output=Path(self.temp.name)/'voyage.jsonl'
        shard.write_text(json.dumps({'index': {'_id': 1}})+'\n'+json.dumps({
            'namespace': 0, 'title': 'São Paulo',
            'source_text': '<p>' + 'Travel text. '*10 + '</p>'})+'\n')
        self.assertEqual(convert_cirrus(shard,output,'enwikivoyage','20260920'),1)
        article=json.loads(output.read_text())
        self.assertEqual(article['source'],'https://en.wikivoyage.org/wiki/S%C3%A3o_Paulo')
        self.assertIn('CC BY-SA',article['license'])

    def test_nearest_city_respects_country_and_distance(self):
        cities=[('Porto','PT',41.15,-8.61,200000),('Vigo','ES',42.24,-8.72,290000)]
        self.assertEqual(closest_city(41.16,-8.60,cities),('Porto','PT'))
        self.assertIsNone(closest_city(41.16,-8.60,cities,country='ES',max_km=35))
        file=Path(self.temp.name)/'cities.txt'
        row=['1','Porto','Porto','','41.15','-8.61','','','PT','','','','','','200000']
        file.write_text('\t'.join(row)+'\n')
        self.assertEqual(closest_city(41.16,-8.60,load_cities(file)),('Porto','PT'))


if __name__ == "__main__":
    unittest.main()
