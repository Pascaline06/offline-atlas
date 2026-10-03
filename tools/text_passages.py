"""Deterministic, offline wiki cleaning and overlapping passage construction."""
import html
import re


def plain_text(body):
    body = re.sub(r"(?is)<ref\b[^>]*>.*?</ref\s*>|<ref\b[^>]*/>", " ", body)
    body = re.sub(r"(?is)<!--.*?-->", " ", body)
    # Balanced templates/media links; regex alone cannot handle nesting.
    out, depth, i = [], 0, 0
    while i < len(body):
        if body.startswith("{{", i):
            depth += 1
            i += 2
        elif depth and body.startswith("}}", i):
            depth -= 1
            i += 2
            if not depth:
                out.append(" ")
        else:
            if not depth:
                out.append(body[i])
            i += 1
    body = "".join(out)
    # Media options may themselves contain wiki links. Skip the balanced outer
    # media link, leaving following factual links intact.
    out=[];i=0
    while i<len(body):
        media=re.match(r'(?i)\[\[(?:File|Image|Category):',body[i:i+20])
        if media:
            level=1;i+=2
            while i<len(body) and level:
                if body.startswith('[[',i): level+=1;i+=2
                elif body.startswith(']]',i): level-=1;i+=2
                else: i+=1
            out.append(' ')
        else:
            out.append(body[i]);i+=1
    body=''.join(out)
    body = re.sub(r"\[\[([^\]]+)\]\]", lambda m: m[1].split("|")[-1], body)
    body = re.sub(r"\[https?://[^\s\]]+\s*([^\]]*)\]", r"\1", body)
    body = re.sub(r"(?is)<[^>]+>", " ", body)
    body = html.unescape(body).replace("'''", "").replace("''", "")
    body = re.sub(r"(?m)^\s*={2,6}\s*(.*?)\s*={2,6}\s*$", r"\n\n\1:\n", body)
    # Cirrus source_text sometimes has flattened line breaks. Preserve headings.
    body = re.sub(r"\s*={2,6}\s*([^=\n]+?)\s*={2,6}\s*", r"\n\n\1:\n", body)
    body = re.sub(r"[ \t]+", " ", body)
    body = re.sub(r"\n{3,}", "\n\n", body)
    return body.strip()


def passages(body, max_chars=1800, overlap_chars=250):
    """Cover the entire cleaned article; avoid silently discarding later sections."""
    text = plain_text(body)
    start = 0
    while start < len(text):
        end = min(len(text), start + max_chars)
        if end < len(text):
            boundary = max(text.rfind(". ", start + max_chars // 2, end),
                           text.rfind("\n", start + max_chars // 2, end))
            if boundary >= 0:
                end = boundary + 1
            else:
                space = text.rfind(" ", start, end)
                if space > start:
                    end = space
        chunk = text[start:end].strip()
        if chunk:
            yield chunk
        if end == len(text):
            break
        next_start = max(start + 1, end - overlap_chars)
        boundary = text.find(" ", next_start, end)
        start = boundary + 1 if boundary >= 0 else end
