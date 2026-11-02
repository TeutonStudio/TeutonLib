#!/usr/bin/env python3
"""Caption and validate a real F3+B docking portal recording; export its MP4."""

import hashlib
import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def main():
    report = Path(sys.argv[1]) if len(sys.argv) > 1 else Path((ROOT / "build/reports/train-docking-demo/latest.txt").read_text())
    result = json.loads((report / "result.json").read_text())
    assert result["passed"] and not result.get("serverOnly") and result["continuousHitboxes"], result
    assert set(result["evidence"]) == {"source", "target"}, result
    recording = Path(result["recording"])
    rendered = report / "captioned"
    rendered.mkdir(exist_ok=True)
    captures = []
    for index, clip in enumerate(result["clips"], 1):
        name = clip["name"]
        capture = json.loads((recording / f"{name}.json").read_text())
        assert capture["continuousHitboxes"] and capture["capturedFrames"] > 0 and capture["framesWithoutHitboxes"] == 0, capture
        captures.append(capture)
        title, subtitle = rendered / f"{name}-title.txt", rendered / f"{name}-subtitle.txt"
        title.write_text(clip["title"])
        subtitle.write_text(clip["subtitle"])
        filters = (
            "drawbox=x=0:y=ih-108:w=iw:h=108:color=0x101722@0.88:t=fill,"
            "drawbox=x=32:y=ih-84:w=5:h=60:color=0x31d7d7:t=fill,"
            f"drawtext=font='Noto Sans':textfile='{title}':fontcolor=white:fontsize=29:x=52:y=h-89,"
            f"drawtext=font='Noto Sans':textfile='{subtitle}':fontcolor=0xd6e0e9:fontsize=18:x=52:y=h-45,"
            "drawtext=font='Noto Sans':text='TEUTONLIB  /  DOCKING-TEST  /  F3+B':fontcolor=white:fontsize=15:x=28:y=22:shadowcolor=black:shadowx=1:shadowy=1,"
            f"drawtext=font='Noto Sans':text='{index:02d} / {len(result['clips']):02d}':fontcolor=white:fontsize=18:x=w-tw-28:y=20:shadowcolor=black:shadowx=1:shadowy=1"
        )
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(recording / f"{name}.mp4"),
                        "-vf", filters, "-an", "-c:v", "libopenh264", "-b:v", "8M", "-pix_fmt", "yuv420p",
                        str(rendered / f"{name}.mp4")], check=True)
        print("Captioned", name, flush=True)
    concat = rendered / "concat.txt"
    concat.write_text("".join(f"file '{clip['name']}.mp4'\n" for clip in result["clips"]))
    label = "gross" if result["case"] == "wide" else "klein"
    output = ROOT / f"media/teutonlib-docking-portal-{label}-f3b.mp4"
    output.parent.mkdir(exist_ok=True)
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-f", "concat", "-safe", "0", "-i", str(concat),
                    "-c", "copy", "-movflags", "+faststart", str(output)], check=True)
    probe = json.loads(subprocess.check_output(["ffprobe", "-v", "error", "-show_format", "-show_streams", "-of", "json", str(output)]))
    video = next(stream for stream in probe["streams"] if stream["codec_type"] == "video")
    assert video["codec_name"] == "h264" and video["r_frame_rate"] == "30/1", video
    assert float(probe["format"]["duration"]) > 40, probe
    (report / "video.json").write_text(json.dumps(probe, indent=2))
    final = {
        "passed": True, "case": result["case"], "output": str(output),
        "size": output.stat().st_size, "sha256": hashlib.sha256(output.read_bytes()).hexdigest(),
        "duration": float(probe["format"]["duration"]), "width": video["width"], "height": video["height"],
        "clips": len(captures), "continuousHitboxes": True,
        "capturedFrames": sum(capture["capturedFrames"] for capture in captures),
        "framesWithoutHitboxes": sum(capture["framesWithoutHitboxes"] for capture in captures),
        "dockingEvidence": result["evidence"],
    }
    (report / "final-verification.json").write_text(json.dumps(final, ensure_ascii=False, indent=2))
    print(json.dumps(final, ensure_ascii=False, indent=2), flush=True)


if __name__ == "__main__":
    main()
