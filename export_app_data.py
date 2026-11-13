#!/usr/bin/env python3
"""Copy a fresh catalog into the Android project so the APK ships with data
inside it (works offline on first launch). The app also refreshes itself from
https://hindianimestuff.web.app/app-data.json, so you only need to rebuild the
APK when the app code changes - NOT every time you add titles.

USAGE:  python export_app_data.py
"""
import json
from pathlib import Path
from generate import build_app_data, ROOT, load_data

data = load_data()
links_path = ROOT / "links.json"
links = json.loads(links_path.read_text(encoding="utf-8")) if links_path.exists() else {}
payload = json.dumps(build_app_data(data.get("items", []), links), ensure_ascii=False, separators=(",", ":"))
dest = ROOT / "android" / "app" / "src" / "main" / "assets" / "app-data.json"
dest.parent.mkdir(parents=True, exist_ok=True)
dest.write_text(payload, encoding="utf-8")
print(f"Wrote {dest} ({len(payload) / 1024 / 1024:.1f} MB)")
