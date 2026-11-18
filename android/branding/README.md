# SmartIQ branding

`smartiq-logo.png` is the supplied artwork (transparent PNG). `smartiq-emblem.png` is the
emblem (the two students, book, check and wifi marks) cropped above the "SmartIQ" wordmark;
all Android assets are generated from it:

| Output | Size | Background |
| --- | --- | --- |
| `res/mipmap-{m,h,x,xx,xxx}dpi/ic_launcher_foreground.png` | 108dp canvas, emblem at 72% | transparent (adaptive icon over `@color/ic_launcher_background` = white) |
| `res/mipmap-{m,h,x,xx,xxx}dpi/ic_launcher.png` | 48dp | white |
| `res/mipmap-{m,h,x,xx,xxx}dpi/ic_launcher_round.png` | 48dp, circular | white |
| `res/drawable-nodpi/smartiq_emblem.png` | 512px | transparent (in-app header logo) |
| `play-store-icon-512.png` | 512px | white (store listing) |

The emblem is cropped by removing every connected component whose top edge is below the
wordmark (`y ≈ 780` in the source) - that keeps the wifi marks and the book, and drops the
wordmark and the tagline. Icons are rendered with Pillow (LANCZOS) and centred; nothing is
stretched, so the artwork keeps its proportions at every density.
