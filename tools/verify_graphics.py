#!/usr/bin/env python3
"""Run the portable TeutonLib graphics contract without starting Minecraft."""

from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]
lua = next((candidate for candidate in ("lua5.4", "lua5.3", "lua") if shutil.which(candidate)), None)
if lua is None:
    raise SystemExit("Lua 5.3 or newer is required for the graphics verification")

subprocess.run([lua, "tools/tests/graphics.lua"], cwd=ROOT, check=True)
