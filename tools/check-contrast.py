"""Check text color pairs from the actual Kotlin theme tokens."""
from pathlib import Path
import re

source = (Path(__file__).resolve().parents[1] / "app/src/main/java/id/iswara/music/ui/Theme.kt").read_text(encoding="utf-8")
accent = re.search(r"val Orange = Color\(0xFF([A-F0-9]{6})\)", source).group(1)

def luminance(hex_color):
    channels = [int(hex_color[i:i + 2], 16) / 255 for i in (0, 2, 4)]
    linear = [c / 12.92 if c <= .04045 else ((c + .055) / 1.055) ** 2.4 for c in channels]
    return sum(c * weight for c, weight in zip(linear, (.2126, .7152, .0722)))

pairs = [("onBackground", "background"), ("onSurface", "surface"), ("onSurfaceVariant", "surface"), ("primary", "background"), ("onPrimary", "primary"), ("onPrimaryContainer", "primaryContainer"), ("onSecondaryContainer", "secondaryContainer")]
pairs += [(fg, bg) for fg in ("onSurface", "onSurfaceVariant", "primary") for bg in ("surfaceContainerLow", "surfaceContainer", "surfaceContainerHigh", "surfaceContainerHighest")]
for name in ("Dark", "Light"):
    block = re.search(rf"private val {name} = .*?ColorScheme\((.*?)\)\n", source, re.I).group(1)
    colors = dict(re.findall(r"(\w+)\s*=\s*Color\(0xFF([A-F0-9]{6})\)", block))
    if "primary = Orange" in block:
        colors["primary"] = accent
    colors["onPrimary"] = colors.get("onPrimary", "FFFFFF")
    for foreground, background in pairs:
        values = sorted([luminance(colors[foreground]), luminance(colors[background])])
        ratio = (values[1] + .05) / (values[0] + .05)
        assert ratio >= 4.5, f"{name}: {foreground}/{background} fails AA: {ratio:.2f}"
        print(f"PASS {name} {foreground}/{background}: {ratio:.2f}:1")
