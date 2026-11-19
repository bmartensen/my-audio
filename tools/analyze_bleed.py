"""Offline diagnostic and equal-gain A/B WAVs for copied My Audio projects.

Install tools/requirements-analysis.txt in .venv. Never modifies the source project.
This is a numerical reference for BleedRemoval.cpp, not a full project renderer.
Takes with an active recorded metronome are reported, but not processed: the native
click-removal stage must run first. Energy differences are NOT isolated bleed dB.
"""
from __future__ import annotations

import argparse
import hashlib
import html
import json
from pathlib import Path

import numpy as np
from scipy import linalg, signal
from scipy.io import wavfile

RATE = 48000
TAPS = 384
PRE = 64
FFT_SIZE = 16384
BLOCK = FFT_SIZE - TAPS
PCM_LIMIT = 32767 / 32768


def read_wav(path: Path) -> np.ndarray:
    rate, values = wavfile.read(path)
    if rate != RATE or values.ndim != 1 or values.dtype != np.int16:
        raise ValueError(f"Expected mono PCM16 / 48000 Hz: {path}")
    return values.astype(np.float64) / 32768


def db(values: np.ndarray) -> float:
    return float(10 * np.log10(np.mean(values * values) + 1e-30))


def pcm(values: np.ndarray) -> np.ndarray:
    return np.rint(np.clip(values, -1, 1) * 32767).astype(np.int16)


def aligned(reference: np.ndarray, length: int, latency: int) -> np.ndarray:
    source = np.zeros(length)
    shift = latency - PRE
    start = max(0, shift)
    end = min(length, len(reference) + shift)
    if end > start:
        source[start:end] = reference[start - shift:end - shift]
    return source


def correlations(source, take, starts=None, block=BLOCK):
    if starts is None:
        starts = range(0, min(len(source), 60 * RATE), block)
    auto = np.zeros(TAPS)
    cross = np.zeros(TAPS)
    for start in starts:
        segment = np.fft.rfft(source[start:start + block], n=FFT_SIZE)
        full = np.fft.rfft(source[start:start + FFT_SIZE], n=FFT_SIZE)
        recorded = np.fft.rfft(take[start:start + FFT_SIZE], n=FFT_SIZE)
        auto += np.fft.irfft(np.conj(segment) * full, n=FFT_SIZE)[:TAPS]
        cross += np.fft.irfft(np.conj(segment) * recorded, n=FFT_SIZE)[:TAPS]
    auto[0] *= 1.0001
    return auto, cross


def predict(source, coefficients):
    return signal.oaconvolve(source, coefficients)[:len(source)]


def estimate(source, take, *, clipping=False, starts=None, block=BLOCK):
    auto, cross = correlations(source, take, starts, block)
    if auto[0] <= 0:
        return np.zeros(TAPS)
    coefficients = linalg.solve_toeplitz(auto, cross)
    positive = take >= PCM_LIMIT
    negative = take <= -PCM_LIMIT
    if clipping and np.any(positive | negative):
        for _ in range(4):
            model = predict(source, coefficients)
            restored = take.copy()
            restored[positive] = np.maximum(take[positive], model[positive])
            restored[negative] = np.minimum(take[negative], model[negative])
            _, cross = correlations(source, restored, starts, block)
            coefficients = linalg.solve_toeplitz(auto, cross)
    return coefficients


def leak_gains(model, take):
    hop = 12000
    centers = list(range(0, len(take) + hop, hop))
    gains = []
    for center in centers:
        lo, hi = max(0, center - hop), min(len(take), center + hop)
        valid = np.abs(take[lo:hi]) < PCM_LIMIT
        x, y = model[lo:hi][valid], take[lo:hi][valid]
        xx, xy, yy = np.dot(x, x), np.dot(x, y), np.dot(y, y)
        gain = 1.0
        if len(x) >= 960 and xx > 1e-8 and yy > 0 and xy > 0:
            candidate = xy / xx
            trust = np.clip((xy * xy / (xx * yy) - 0.8) / 0.15, 0, 1)
            if 0.5 <= candidate <= 2:
                gain += trust * (candidate - 1)
        gains.append(gain)
    return np.interp(np.arange(len(take)), centers, gains)


def clean(source, take, *, clipping=False, adaptive=False, starts=None, block=BLOCK):
    model = predict(source, estimate(source, take, clipping=clipping, starts=starts, block=block))
    if adaptive:
        model *= leak_gains(model, take)
    if clipping and np.any(np.abs(take) >= PCM_LIMIT):
        model = np.clip(model, -PCM_LIMIT, PCM_LIMIT)
    return take - model


def held_out(source, take):
    """Fit alternate 16000-frame blocks; evaluate only interiors of the other blocks.

    Adjacent blocks contain related music; this checks local overfitting, not independent
    performances. No isolated clean target exists for a real take.
    """
    limit = min(len(take), 60 * RATE)
    source, take = source[:limit], take[:limit]
    old, new = take.copy(), take.copy()
    valid = np.zeros(limit, dtype=bool)
    for side in range(2):
        starts = list(range(side * BLOCK, limit - TAPS, BLOCK * 2))
        a = clean(source, take, starts=starts, block=BLOCK - TAPS)
        b = clean(source, take, clipping=True, starts=starts, block=BLOCK - TAPS)
        for start in range((1 - side) * BLOCK, limit - TAPS, BLOCK * 2):
            lo, hi = start + TAPS, min(limit, start + BLOCK)
            old[lo:hi], new[lo:hi], valid[lo:hi] = a[lo:hi], b[lo:hi], True
    if not np.any(valid):
        return None
    return {"linear_dbfs": db(pcm(old[valid]) / 32768),
            "clipping_aware_dbfs": db(pcm(new[valid]) / 32768)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("project", type=Path, help="Copied directory containing project.json and tracks/")
    parser.add_argument("--output", required=True, type=Path, help="Separate output directory")
    parser.add_argument("--cross-validate", action="store_true")
    args = parser.parse_args()
    source_dir = args.project.resolve()
    output = args.output.resolve()
    if output.is_relative_to(source_dir) or source_dir.is_relative_to(output):
        parser.error("Source and output must be separate directories, neither containing the other")
    output.mkdir(parents=True, exist_ok=True)
    project = json.loads((source_dir / "project.json").read_text(encoding="utf-8"))
    report = {"project": project["name"], "sample_rate": RATE,
              "method": "384 taps, 64 pre-taps, 4 censored-observation iterations, coherence-gated gain tracking",
              "limitation": "Energy reduction includes the new performance; not isolated bleed suppression. "
                            "No independent clean target exists. A/B files use identical gain, no normalization.",
              "tracks": []}
    cards = []

    def track_path(filename):
        path = (source_dir / "tracks" / filename).resolve()
        if not path.is_relative_to(source_dir / "tracks"):
            raise ValueError("Track path leaves project")
        return path

    for track in project["tracks"]:
        path = track_path(track["fileName"])
        take = read_wav(path)
        if not len(take):
            continue
        row = {"id": track["id"], "name": track["name"], "seconds": len(take) / RATE,
               "raw_sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
               "raw_dbfs": db(take), "clipped_samples": int(np.sum(np.abs(take) >= PCM_LIMIT)),
               "clipped_percent": float(100 * np.mean(np.abs(take) >= PCM_LIMIT))}
        report["tracks"].append(row)
        if not track.get("referenceFileName"):
            row["skipped"] = "No playback reference"
            continue
        metronome = track.get("recordedMetronome", project.get("metronome", {}))
        if metronome.get("enabled", False):
            row["skipped"] = "Requires native click removal before bleed estimation"
            continue
        ref_path = track_path(track["referenceFileName"])
        row["reference_sha256"] = hashlib.sha256(ref_path.read_bytes()).hexdigest()
        reference = read_wav(ref_path)
        latency = -track["offsetFrames"] - track.get("countInFrames", 0)
        source = aligned(reference, len(take), latency)
        old = pcm(clean(source, take))
        saturation = pcm(clean(source, take, clipping=True))
        new = pcm(clean(source, take, clipping=True, adaptive=True))
        row.update(linear_dbfs=db(old / 32768), clipping_aware_dbfs=db(saturation / 32768),
                   adaptive_dbfs=db(new / 32768))
        for version, expected in [(2, old), (3, saturation), (4, new)]:
            cache = path.with_name(f"{path.stem}.clean-v{version}.wav")
            if cache.exists():
                cached = read_wav(cache)
                row[f"cache_v{version}_difference_dbfs"] = db(cached - expected / 32768)
        if args.cross_validate:
            row["held_out_static_filter_only"] = held_out(source, take)
        for label, samples in [("previous", old), ("corrected", new)]:
            wavfile.write(output / f"{track['id']}-{label}.wav", RATE, samples)
        title = html.escape(track["name"])
        cards.append(f'<section><h2>{title}</h2><p>Clipped samples: {row["clipped_percent"]:.3f} %</p>'
                     f'<p>Static filter</p><audio controls preload="none" src="{track["id"]}-previous.wav"></audio>'
                     f'<p>Clipping-aware filter with gain tracking</p><audio controls preload="none" src="{track["id"]}-corrected.wav"></audio></section>')
        print(json.dumps(row, ensure_ascii=False), flush=True)
    (output / "report.json").write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    (output / "compare.html").write_text(
        '<!doctype html><html lang="en"><meta charset="utf-8"><title>Bleed-removal comparison</title>'
        '<style>body{max-width:850px;margin:40px auto;background:#181c20;color:#eee;font:18px system-ui;padding:20px}'
        'section{border-top:1px solid #555;margin-top:30px}audio{width:100%}</style>'
        f'<h1>{html.escape(project["name"])} – Listening comparison</h1>'
        '<p>Identical gain, without normalization. Compare the same position in both outputs. '
        'Each file contains one track, including residual bleed. '
        'Lower total energy does not establish that only bleed was removed.</p>'
        + ''.join(cards) + '</html>\n', encoding="utf-8")


if __name__ == "__main__":
    main()
