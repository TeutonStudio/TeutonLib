#!/usr/bin/env python3
"""Add German chapter captions to the recorded in-game footage and export MP4."""
from pathlib import Path
import json
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]


def main():
    report = Path(sys.argv[1]) if len(sys.argv) > 1 else Path((ROOT / "build/reports/train-demo/latest.txt").read_text())
    result = json.loads((report / "result.json").read_text())
    assert result["passed"]
    recording = Path(result["recording"])
    rendered = report / "captioned"
    rendered.mkdir(exist_ok=True)
    for i, clip in enumerate(result["clips"], 1):
        name = clip["name"]
        title, subtitle = rendered / f"{name}-title.txt", rendered / f"{name}-subtitle.txt"
        title.write_text(clip["title"].replace("→", ">"))
        subtitle.write_text(clip["subtitle"].replace("→", ">"))
        filters = (
            "drawbox=x=0:y=ih-108:w=iw:h=108:color=0x101722@0.88:t=fill,"
            "drawbox=x=32:y=ih-84:w=5:h=60:color=0x31d7d7:t=fill,"
            f"drawtext=font='Noto Sans':textfile='{title}':fontcolor=white:fontsize=30:x=52:y=h-89,"
            f"drawtext=font='Noto Sans':textfile='{subtitle}':fontcolor=0xd6e0e9:fontsize=19:x=52:y=h-45,"
            f"drawtext=font='Noto Sans':text='TEUTONLIB  /  INGAME-DEMO{'  /  F3+B' if result.get('continuousHitboxes') else ''}':fontcolor=white:fontsize=15:x=28:y=22:shadowcolor=black:shadowx=1:shadowy=1,"
            f"drawtext=font='Noto Sans':text='{i:02d} / {len(result['clips']):02d}':fontcolor=white:fontsize=18:x=w-tw-28:y=20:shadowcolor=black:shadowx=1:shadowy=1"
        )
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(recording / f"{name}.mp4"),
                        "-vf", filters, "-an", "-c:v", "libopenh264", "-b:v", "8M", "-pix_fmt", "yuv420p",
                        str(rendered / f"{name}.mp4")], check=True)
        print("Captioned", name, flush=True)
    concat = rendered / "concat.txt"
    concat.write_text("".join(f"file '{clip['name']}.mp4'\n" for clip in result["clips"]))
    suffix = "-f3b" if result.get("continuousHitboxes") else ""
    output = ROOT / f"media/teutonlib-zug-sable-contraptionlevel-portal{suffix}.mp4"
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-f", "concat", "-safe", "0", "-i", str(concat),
                    "-c", "copy", "-movflags", "+faststart", str(output)], check=True)
    probe = json.loads(subprocess.check_output(["ffprobe", "-v", "error", "-show_format", "-show_streams", "-of", "json", str(output)]))
    (report / "video.json").write_text(json.dumps(probe, indent=2))
    print(output, flush=True)


if __name__ == "__main__": main()
