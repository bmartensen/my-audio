# Bleed-removal model and audio-quality evidence

The current cleanup cache uses version 4. It combines a short reference FIR, clipping-aware estimation and conservative local level tracking. Raw takes and references remain immutable; no timing shift or noise gate is applied. New takes default to cleanup disabled, with the recorded reference retained for optional processing.

## Signal model

Speaker bleed is estimated from the exact recorded playback reference. A 384-tap FIR models the short acoustic path; longer filters can fit musical similarity between tracks instead of the actual echo. Estimation uses FFT auto/cross-correlation and a Toeplitz/Levinson solution, with at most 60 s of audio. Four iterations treat saturated PCM16 samples as bounds, not exact amplitudes, and constrain predicted playback before subtraction. Click subtraction can shift these bounds, so fitting can receive an unchanged raw copy. Lost clipped target peaks cannot be recovered.

Local gain tracking uses 500 ms windows every 250 ms, at least 20 ms of valid unsaturated observations, a squared-correlation transition from 0.8 to 0.95 and gains limited to 0.5–2 with interpolation. It adjusts the echo estimate, not independent microphone noise. Musical target content can correlate with the reference, so listening remains necessary.

## Reference-device evidence

The speaker/microphone test on a Pixel 9 used fixed phone position and playback level. Three overdubs had 0.197%, 0.367% and 0.688% saturated samples. Clipping-aware fitting reduced total cleaned-track energy by approximately 0.48/1.39/4.56 dB on held-out alternating blocks. These are **total energy changes**, not isolated bleed-rejection measurements: no separate clean target recording exists.

In the studied 0.5–4.0 s initial pauses, local tracking yielded −65.85, −58.63 and −45.17 dBFS RMS. Native output agreed with the numerical reference below −131 dBFS. Initial render of the studied project took 4.48 s, with 0.87–1.55 s cleanup per overdub. Hash checks confirmed source takes/references were unchanged. Listening acceptance supports this particular speaker/phone setup, not a universal quality bound.

## Reproduction and open limits

`tools/analyze_bleed.py <copied-project> --output <separate-directory> --cross-validate` writes measurements, source hashes and a listening report without modifying the input or uploading it. Install `tools/requirements-analysis.txt` separately. Cross-validation covers the static clipping model; local gain tracking is not reported as independently validated fitting. Takes with a recorded metronome are inventoried only because native click removal is not integrated into this Python tool.

`tools/bleed_removal_test.cpp` checks known independent target signals, unclipped/moderately/heavily clipped input, click-subtracted bounds, changing echo level in pauses, silent/nonoverlapping references and invalid lengths. Preventing capture clipping before PCM16 storage remains open; saved files alone cannot locate the clipping stage.
