"""Publish the README and gallery with the token held by GitHub Actions."""

import json
import os
import tomllib
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
summary = tomllib.loads((ROOT / "stonecutter.properties.toml").read_text())["mod"]["description"]
request("PATCH", data=json.dumps({"body": body, "description": summary}).encode())
project = json.loads(request("GET"))
existing = {item.get("title"): item for item in project["gallery"]}
gallery = json.loads((ROOT / "docs/gallery.json").read_text())
desired_titles = {item["title"] for item in gallery}
for item in project["gallery"]:
    if item.get("title") not in desired_titles:
        request("DELETE", "/gallery?" + urlencode({"url": item["url"]}))
        print(f"Removed gallery image absent from manifest: {item.get('title')}")
for item in gallery:
    current = existing.get(item["title"])
    if current:
        if (current.get("description"), current.get("ordering"), current.get("featured")) != \
                (item["description"], item["ordering"], item["featured"]):
            query = {"url": current["url"], "description": item["description"], "ordering": item["ordering"],
                     "featured": str(item["featured"]).lower()}
            request("PATCH", "/gallery?" + urlencode(query))
            print(f"Updated gallery image: {item['title']}")
        continue
    path = ROOT / item["file"]
    query = {key: item[key] for key in ("title", "description", "ordering")}
    query.update(ext=path.suffix[1:], featured=str(item["featured"]).lower())
    request("POST", "/gallery?" + urlencode(query), path.read_bytes(), "image/jpeg")
    print(f"Added gallery image: {item['title']}")

saved = json.loads(request("GET"))
assert saved["body"] == body, "Description readback differs"
assert saved["description"] == summary, "Summary readback differs"
shown = {item.get("title"): (item.get("ordering"), item.get("featured")) for item in saved["gallery"]}
assert all(shown.get(item["title"]) == (item["ordering"], item["featured"]) for item in gallery), "Gallery readback differs"
print("Verified Modrinth description, summary and gallery.")
