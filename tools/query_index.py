#!/usr/bin/env python3
"""Desktop SQL approximation for pack QA; Android also reranks passages in Java."""
import argparse
import re
import sqlite3
import math

STOP = {"the", "a", "an", "what", "why", "how", "tell", "me", "about", "in", "is", "are", "best", "restaurants", "restaurant", "vegan", "compare", "and", "of", "for", "from", "with", "causes", "caused", "cause", "work", "works", "does", "do", "did", "was", "were", "have", "has", "had", "its", "this", "that", "main", "way", "get", "stay", "into", "between", "can", "you", "to", "at"}


def comparison_subjects(question):
    patterns=(r'^\s*(?:compare|contrast)\s+(.{2,80}?)\s+(?:and|with|versus|vs\.?)\s+(.{2,80}?)\s*[?.!]*\s*$',
              r'^\s*what(?:\'s| is)\s+(?:the\s+)?difference\s+between\s+(.{2,80}?)\s+and\s+(.{2,80}?)\s*[?.!]*\s*$',
              r'^\s*(.{2,80}?)\s+(?:versus|vs\.?)\s+(.{2,80}?)\s*[?.!]*\s*$')
    for pattern in patterns:
        m=re.match(pattern,question,re.I)
        if not m: continue
        first,second=m.group(1).strip(),m.group(2).strip()
        second=re.sub(r'\s+for\s+(?:a|an|the|my|your|our)\s+.*$','',second,flags=re.I)
        if ' ' not in first and ' ' in second:
            last=second.rsplit(' ',1)[-1]
            if len(last)>3: first+=' '+last
        return first,second
    return None


def subject_article(con, subject):
    if subject.lower().endswith(' power'):
        for equipment in (' panel',' turbine',' cell'):
            candidate=exact_article(con,subject[:-6]+equipment)
            if candidate and 'electric' in candidate['excerpt'].lower() and 'may refer to' not in candidate['excerpt'].lower():
                return candidate
    exact=exact_article(con,subject)
    if exact: return exact
    if subject.lower().endswith(' power'):
        alternate=exact_article(con,subject[:-6]+' energy')
        if alternate and 'may refer to' not in alternate['excerpt'].lower(): return alternate
    if len(subject.split())==2:
        first=exact_article(con,subject.split()[0])
        if first: return first
    tokens=[token for token in re.findall(r'[^\W_]+',subject.lower()) if len(token)>2][:4]
    if not tokens: return None
    title_filter=' AND '.join('instr(lower(d.title), ?) > 0' for _ in tokens)
    row=con.execute(f"""SELECT d.id,d.title,d.source,d.source_date,d.license,
      substr(d.body,1,700) AS excerpt FROM doc_search JOIN documents d ON d.rowid=doc_search.rowid
      WHERE doc_search MATCH ? AND {title_filter}
      ORDER BY length(d.title),CASE WHEN d.id LIKE 'simplewiki:%' THEN 0 ELSE 1 END LIMIT 1""",
      (' AND '.join('"'+token+'"' for token in tokens),*tokens)).fetchone()
    return dict(row) if row else None


def exact_article(con, subject):
    exact=con.execute("""SELECT id,title,source,source_date,license,substr(body,1,700) AS excerpt
      FROM documents WHERE lower(id)=lower(?) OR lower(id)=lower(?)
      ORDER BY CASE WHEN id LIKE 'enwikivoyage:%' THEN 0 ELSE 1 END LIMIT 1""",
      ('simplewiki:'+subject,'enwikivoyage:'+subject)).fetchone()
    return dict(exact) if exact else None


def vegan_listings(body):
    """Conservative QA counterpart for the Android Wikivoyage listing parser."""
    lower=body.lower()
    start=0
    while (start:=lower.find('{{eat',start))>=0:
        after=start+5
        if after<len(body) and not (body[after].isspace() or body[after]=='|'):
            start=after
            continue
        depth=1; end=after
        while end+1<len(body) and depth:
            if body.startswith('{{',end): depth+=1; end+=2
            elif body.startswith('}}',end): depth-=1; end+=2
            else: end+=1
        if depth: break
        listing=body[after:end-2]
        start=end
        if '…' in listing or re.search(r'\b(permanently closed|temporarily closed|closed down|now closed|out of business)\b',listing,re.I):
            continue
        fields={}; begin=0; nested=0; links=0
        for i in range(len(listing)+1):
            if listing.startswith('{{',i): nested+=1
            if listing.startswith('}}',i): nested-=1
            if listing.startswith('[[',i): links+=1
            if listing.startswith(']]',i): links-=1
            if i==len(listing) or listing[i]=='|' and not nested and not links:
                item=listing[begin:i]
                if '=' in item:
                    key,value=item.split('=',1)
                    fields[key.strip().lower()]=value.strip()
                begin=i+1
        content=fields.get('content','')
        if not fields.get('name') or not re.search(r'\bvegan\b',content,re.I): continue
        if re.search(r'\b(cart|stall|bakery|grocery|supermarket)\b|\bhealth foods?\b',fields['name']+' '+content,re.I): continue
        if re.search(r'\b(no|not|without)\s+vegan\b|\bvegan\s+(options?\s+)?(unavailable|no longer available)\b',content,re.I): continue
        yield fields


def terms(question):
    return [word for word in re.findall(r"[^\W_]+", question.lower(), re.UNICODE) if len(word) > 2 and word not in STOP]


def unique_places(rows, limit=12):
    seen=set(); result=[]
    for row in rows:
        key=(row['name'].casefold(),round(row['lat'],4),round(row['lon'],4))
        if key in seen: continue
        seen.add(key)
        result.append(row)
        if len(result)>=limit: break
    return result


def search(con, question):
    con.row_factory = sqlite3.Row
    comparison=comparison_subjects(question)
    if comparison:
        first,second=(subject_article(con,subject) for subject in comparison)
        if first and second and first['title'].casefold()!=second['title'].casefold():
            return {"kind":"comparison","warning":"Two offline subject articles; verify the comparison against both.","results":[first,second]}
    tokens = terms(question)
    if re.search(r"\bvegan\b", question, re.I) and re.search(r"\brestaurant", question, re.I):
        match = re.search(r"\bin\s+([\w\s-]+?)(?:[?.,]|$)", question, re.I)
        city = match.group(1).strip() if match else ""
        if not city:
            return {"kind": "places", "warning": "Please specify a city.", "results": []}
        hint=re.search(r'\bin\s+[\w\s-]+,\s*([\w\s]+?)(?:[?.]|$)',question,re.I)
        # The QA counterpart only maps countries currently included in the pack.
        country_names={'nigeria':'NG','portugal':'PT','japan':'JP','spain':'ES','kenya':'KE'}
        country=(country_names.get(hint.group(1).strip().lower(),hint.group(1).strip().upper()) if hint else None)
        if country and (len(country)!=2 or not country.isalpha()):
            return {"kind":"places","warning":"Country not recognized.","results":[]}
        constraint=' AND country = ?' if country else ''
        city_row = con.execute(f"""SELECT name,lat,lon FROM cities WHERE (name = ? COLLATE NOCASE
            OR ascii_name = ? COLLATE NOCASE){constraint} ORDER BY population DESC LIMIT 1""",
            (city,city,country) if country else (city,city)).fetchone()
        actual = con.execute(f"""SELECT city FROM places WHERE (lower(?) = lower(city)
           OR lower(?) LIKE lower(city) || ' %'){constraint}
           ORDER BY length(city) DESC LIMIT 1""", (city,city,country) if country else (city,city)).fetchone()
        if actual:
            city = actual[0]
        if city_row:
            city = city_row[0]
            lat,lon = city_row[1],city_row[2]
            lat_span = 25/111.2
            lon_span = 25/(111.2*max(.05,math.cos(math.radians(lat))))
            where = 'lat BETWEEN ? AND ? AND lon BETWEEN ? AND ?'+constraint
            where_args = (lat-lat_span,lat+lat_span,lon-lon_span,lon+lon_span)+((country,) if country else ())
        else:
            where = 'city = ? COLLATE NOCASE'+constraint
            where_args = (city,)+((country,) if country else ())
        rows = con.execute(f"""SELECT * FROM places WHERE {where}
         AND diet_vegan IN ('only','yes','limited')
         ORDER BY CASE diet_vegan WHEN 'only' THEN 0 WHEN 'yes' THEN 1 ELSE 2 END,
         name COLLATE NOCASE LIMIT 60""", where_args).fetchall()
        rows=unique_places(rows)
        if not rows:
            cuisine_rows=con.execute(f"""SELECT * FROM places WHERE {where}
                AND diet_vegan = 'unknown' AND ((';' || lower(cuisine) || ';') LIKE '%;vegan;%'
                    OR lower(name) LIKE '%vegan%')
                ORDER BY CASE WHEN (';' || lower(cuisine) || ';') LIKE '%;vegan;%' THEN 0 ELSE 1 END,
                    name COLLATE NOCASE LIMIT 40""",where_args).fetchall()
            cuisine_rows=unique_places(cuisine_rows)
            if cuisine_rows:
                return {"kind":"unverified_leads", "warning":
                        f"These {city} places have vegan in an OSM cuisine tag or name, but no explicit positive diet:vegan tag. Treat them as unverified leads; menus, quality and hours are unknown.",
                        "results":[dict(row) for row in cuisine_rows]}
            guide_rows = con.execute("""SELECT d.id,d.title,d.source,d.source_date,d.license,
                d.body
                FROM doc_search JOIN documents d ON d.rowid=doc_search.rowid
                WHERE doc_search MATCH 'vegan' AND d.id LIKE 'enwikivoyage:%'
                    AND (d.title = ? COLLATE NOCASE OR d.title LIKE ? COLLATE NOCASE)
                ORDER BY CASE WHEN d.title = ? COLLATE NOCASE THEN 0 ELSE 1 END,d.title LIMIT 40""",
                (city,city+'/%',city)).fetchall()
            leads=[]; seen=set()
            for row in guide_rows:
                for listing in vegan_listings(row['body']):
                    name=listing['name']
                    if name.casefold() in seen: continue
                    seen.add(name.casefold())
                    leads.append({'title':name,'excerpt':listing['content'],
                                  'guide':row['title'],'address':listing.get('address',''),
                                  'lastedit':listing.get('lastedit',''),
                                  'source':row['source'],'source_date':row['source_date'],'license':row['license']})
                    if len(leads)>=12: break
                if len(leads)>=12: break
            return {"kind": "guide_listings", "warning":
                    (f"Named dining leads from older Wikivoyage snapshots for {city}; current menus, hours and rankings are unverified."
                     if leads else f"No named vegan dining leads found for {city} in this pack; this does not establish there are none in the city."),
                    "results":leads}
        return {"kind": "places", "warning": "Offline records cannot establish today's hours or which venue is best. Check the source date and dietary tag.", "results": [dict(row) for row in rows]}
    if not tokens:
        return {"kind": "documents", "warning": "Use more specific search words.", "results": []}
    base_tokens=list(dict.fromkeys(tokens[:10]))
    expanded=[]
    for token in tokens[:12]:
        expanded.append(token)
        if len(token)>5 and token.endswith('ies'):
            expanded.append(token[:-3]+'y')
        elif len(token)>4 and token.endswith('s') and not token.endswith('ss'):
            expanded.append(token[:-1])
    tokens = list(dict.fromkeys(expanded))[:20]
    expression = " OR ".join(f'"{word}"' for word in tokens)
    priority = " + ".join("CASE WHEN instr(lower(d.title), ?) > 0 THEN 1 ELSE 0 END" for _ in tokens[:12])
    phrases = [tokens[i]+' '+tokens[i+1] for i in range(len(tokens)-1)]
    exact_title = f"CASE WHEN lower(d.title) IN ({','.join('?' for _ in phrases)}) THEN 0 ELSE 1 END," if phrases else ""
    sql=f"""SELECT d.id,d.title,d.source,d.source_date,d.license,
      snippet(doc_search,'[',']',' … ',-1,24) AS excerpt
      FROM doc_search JOIN documents d ON d.rowid=doc_search.rowid
      WHERE doc_search MATCH ? ORDER BY {exact_title} ({priority}) DESC,
      CASE WHEN lower(d.title) LIKE '%(movie)%' OR lower(d.title) LIKE '%(film)%' THEN 1 ELSE 0 END,
      length(d.title), d.title COLLATE NOCASE LIMIT 16"""
    anchors=(f'"{base_tokens[0]}" AND "{base_tokens[-1]}"' if len(base_tokens)>1 else expression)
    rows=con.execute(sql,(anchors,*phrases,*tokens[:12])).fetchall()
    if anchors!=expression:
        rows+=con.execute(sql,(expression,*phrases,*tokens[:12])).fetchall()
    unique = {}
    for row in rows:
        unique.setdefault(row['title'].casefold(), dict(row))
    # The desktop tool shows retrieval leads, not the Android evidence score.
    # Keep the subject's article ahead of a coincidental film title.
    subject=' '.join(base_tokens)
    def lead_key(row):
        title=row['title'].casefold()
        fictional=any(f'({kind})' in title for kind in ('movie','film','song','album','band'))
        overlap=sum(word in title.split() for word in base_tokens)
        subject_bonus=subject in title
        return (fictional, -int(subject_bonus), -overlap, len(title))
    warning = ("The offline pack did not find distinct articles for both subjects; these are partial leads."
               if comparison else "These are source excerpts, not a synthesized answer.")
    return {"kind": "partial_comparison" if comparison else "documents", "warning": warning,
            "results": sorted(unique.values(), key=lead_key)}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("database")
    parser.add_argument("question")
    args = parser.parse_args()
    with sqlite3.connect(f"file:{args.database}?mode=ro", uri=True) as connection:
        result = search(connection, args.question)
    import json
    print(json.dumps(result, ensure_ascii=False, indent=2))
