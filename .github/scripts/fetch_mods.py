#!/usr/bin/env python3
"""Downloads mods (and their required dependencies) from Modrinth for an export test.

A dependency without a pinned version gets the newest version published within 60 days of the mod that needs it, so
an older mod gets a library from its own time rather than a later rewrite.

Usage: fetch_mods.py <minecraft version> <loader: forge|neoforge> <mods folder> <mod> [<mod> ...]
A mod is a Modrinth slug, optionally pinned: "jei", "gregtechceu-modern@mc1.20.1-7.5.3".
Without a pin the newest version for that Minecraft version and loader is used.
"""
import datetime
import json
import os
import sys
import time
import urllib.parse
import urllib.request

API = "https://api.modrinth.com/v2"
mc, loader, out = sys.argv[1], sys.argv[2], sys.argv[3]
wanted = sys.argv[4:]
os.makedirs(out, exist_ok=True)


def get(path, **params):
    url = API + path
    if params:
        url += "?" + urllib.parse.urlencode({k: json.dumps(v) for k, v in params.items()})
    for attempt in range(4):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "syhros/gtcalc-exporter CI"})
            with urllib.request.urlopen(req, timeout=60) as r:
                return json.load(r)
        except Exception as e:  # rate limits and flaky network: retry
            if attempt == 3:
                raise
            print(f"  retry {url}: {e}")
            time.sleep(3 * (attempt + 1))


def versions(project):
    return get(f"/project/{project}/version", game_versions=[mc], loaders=[loader])


done = set()


def date(s):
    return datetime.datetime.fromisoformat(s.replace("Z", "+00:00"))


def fetch(version, why):
    if version["project_id"] in done:
        return
    done.add(version["project_id"])
    files = version["files"]
    f = next((x for x in files if x.get("primary")), files[0])
    dest = os.path.join(out, f["filename"])
    print(f"{f['filename']}  ({why})")
    req = urllib.request.Request(f["url"], headers={"User-Agent": "syhros/gtcalc-exporter CI"})
    with urllib.request.urlopen(req, timeout=300) as r, open(dest, "wb") as o:
        o.write(r.read())
    for d in version.get("dependencies", []):
        if d["dependency_type"] != "required":
            continue
        if d.get("version_id"):
            dep = get(f"/version/{d['version_id']}")
        elif d.get("project_id"):
            if d["project_id"] in done:
                continue
            found = versions(d["project_id"])
            if not found:
                print(f"  ! no {mc} {loader} version of required dependency {d['project_id']}")
                continue
            limit = date(version["date_published"]) + datetime.timedelta(days=60)
            dep = next((v for v in found if date(v["date_published"]) <= limit), found[-1])
        else:
            continue
        fetch(dep, f"needed by {f['filename']}")


for spec in wanted:
    slug, _, pin = spec.partition("@")
    found = versions(slug)
    if pin:
        found = [v for v in found if v["version_number"] == pin]
    if not found:
        sys.exit(f"no {mc} {loader} version of {spec} on Modrinth")
    fetch(found[0], spec)
