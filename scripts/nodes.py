#!/usr/bin/env python3
"""Lists the build's Stonecutter nodes as JSON, for CI matrices and release scripts.

python3 scripts/nodes.py            [{"node": "26.3-neoforge", "minecraft": "26.3", "loader": "neoforge",
                                      "java": 25, "releases": ["26.3"]}, ...]

Nodes come from settings.gradle.kts; their properties from stonecutter.properties.toml, looked up the way
Stonecutter does: [loader."version"], then ["version"], then the top level.
"""
import json
import re
import sys
import tomllib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def nodes():
    settings = (ROOT / "settings.gradle.kts").read_text()
    props = tomllib.loads((ROOT / "stonecutter.properties.toml").read_text())
    found = []
    for args in re.findall(r'^\s*match\(([^)]*)\)', settings, re.M):
        minecraft, *loaders = re.findall(r'"([^"]+)"', args)
        for loader in loaders:
            def prop(key):
                for table in (props.get(loader, {}).get(minecraft, {}), props.get(minecraft, {}), props):
                    value = table
                    for part in key.split("."):
                        value = value.get(part) if isinstance(value, dict) else None
                    if value is not None:
                        return value
                raise KeyError(f"{key} for {minecraft}-{loader}")
            found.append({
                "node": f"{minecraft}-{loader}",
                "minecraft": minecraft,
                "loader": loader,
                "java": 25 if int(minecraft.split(".")[0]) >= 26 else 21,
                "releases": prop("mod.mc_releases"),
            })
    return found


if __name__ == "__main__":
    json.dump(nodes(), sys.stdout)
    print()
