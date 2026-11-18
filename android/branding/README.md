# SmartIQ branding

`smartiq-logo.png` is the supplied artwork (1254², transparent). Everything else is generated
from it by `generate-icons.py`:

```bash
pip install pillow
python3 android/branding/generate-icons.py
```

## What the emblem is

The wordmark and the "Smarter Attendance. Smarter Management." tagline are cut off, and the
**wifi marks in the top-right corner are removed** (they read as noise at icon size). The
emblem is the two figures, the mortarboard, the book, the check badge and the pointer.

Selection works on connected components of the alpha channel: shapes whose top edge is below
`y ≈ 774` belong to the wordmark/tagline, and shapes starting at `x ≥ 1000, y ≥ 640` are the
wifi marks - both are dropped, the rest is cropped to its bounding box.

## Outputs

| File | Size | Background |
| --- | --- | --- |
| `res/mipmap-{m,h,x,xx,xxx}dpi/ic_launcher_foreground.png` | 108dp canvas, emblem at 74% | transparent, drawn over `@color/ic_launcher_background` (white) |
| `res/mipmap-{m,h,x,xx,xxx}dpi/ic_launcher.png` | 48/72/96/144/192 px, emblem at 82% | white |
| `res/mipmap-{m,h,x,xx,xxx}dpi/ic_launcher_round.png` | same sizes, circular | white |
| `res/drawable-nodpi/smartiq_emblem.png` | 512 px wide | transparent (sign-in header) |
| `play-store-icon-512.png` | 512² | white (store listing) |
| `icon-preview.png` | contact sheet: adaptive / legacy / round | - |

74% is the largest scale that keeps the artwork inside the 72dp adaptive safe circle - only
the outermost book edges touch it, and launchers that use a squircle show all of it.
