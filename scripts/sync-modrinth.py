"""Publish the README and gallery with the token held by GitHub Actions."""

import json
import os
from pathlib import Path
from urllib.parse import urlencode
from urllib.request import Request, urlopen


ROOT = Path(__file__).resolve().parents[1]
PROJECT = os.environ["MODRINTH_ID"]
HEADERS = {
    "Authorization": os.environ["MODRINTH_TOKEN"],
    "User-Agent": "metrovoc/Phonon (github.com/metrovoc/Phonon)",
}


def request(method, suffix="", data=None, content_type="application/json"):
    req = Request(
        f"https://api.modrinth.com/v2/project/{PROJECT}{suffix}",
        data=data,
        headers={**HEADERS, "Content-Type": content_type},
        method=method,
    )
    with urlopen(req, timeout=60) as response:
        return response.read()


body = (ROOT / "README.md").read_text()
summary = "Play your music through speakers and portable boomboxes. Share synchronized stations, build playlists, and bring your world to life."
request("PATCH", data=json.dumps({"body": body, "description": summary}).encode())
project = json.loads(request("GET"))
existing = {item.get("title") for item in project["gallery"]}
gallery = json.loads((ROOT / "docs/gallery.json").read_text())
desired_titles = {item["title"] for item in gallery}
for item in project["gallery"]:
    if item.get("title") not in desired_titles:
        request("DELETE", "/gallery?" + urlencode({"url": item["url"]}))
        print(f"Removed gallery image absent from manifest: {item.get('title')}")
for item in gallery:
    if item["title"] in existing:
        continue
    path = ROOT / item["file"]
    query = {key: item[key] for key in ("title", "description", "ordering")}
    query.update(ext=path.suffix[1:], featured=str(item["featured"]).lower())
    request("POST", "/gallery?" + urlencode(query), path.read_bytes(), "image/jpeg")
    print(f"Added gallery image: {item['title']}")

saved = json.loads(request("GET"))
assert saved["body"] == body, "Description readback differs"
assert saved["description"] == summary, "Summary readback differs"
assert {item["title"] for item in gallery} <= {item.get("title") for item in saved["gallery"]}
print("Verified Modrinth description, summary and gallery.")
