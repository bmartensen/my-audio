# My Audio branding and store assets

The visual identity combines a graphite/nickel microphone and black piano-lacquer keyboard, warm ivory keys and a small red recording light. Store artwork is illustrative, not a product screenshot.

- `app-icon-512.png`: opaque square 512×512 RGBA Google Play icon, without baked-in corner rounding.
- `feature-graphic-de-1024x500.png` / `feature-graphic-en-1024x500.png`: localized RGB feature graphics without transparency.
- `branding-overview.png`: overview including circular launcher masking and small sizes.
- `store/de-DE/` and `store/en-US/`: deliberately localized title, short/full descriptions and graphic alternative text.
- `../assets/source/branding/`: raster masters; the symbol has a true alpha channel. Public copies omit generation metadata.
- `../app/src/main/res/drawable-nodpi/ic_launcher_brand.png`: adaptive foreground fitted to the central 66 dp safe region.
- `../app/src/main/res/drawable/ic_launcher_monochrome.xml`: native microphone/keyboard silhouette for themed icons.

[Generation prompts](generation-prompts.md) preserve the design specification and input relationships. Reproduce sizes/formats without new image generation:

```powershell
.venv\Scripts\python.exe tools\generate_icon.py
.venv\Scripts\python.exe tools\desktop_icon.py
```

Real product screenshots are still missing. Asset limits: title 30 characters, short description 80, full description 4000, alternative text 140, icon below 1024 KB. Check current [Google Play asset requirements](https://support.google.com/googleplay/android-developer/answer/9866151?hl=en), [text requirements](https://support.google.com/googleplay/android-developer/answer/9859152?hl=en) and [adaptive icon guidance](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive) before submission.
