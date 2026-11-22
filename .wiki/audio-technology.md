# Audio transport, calibration and processing

Android uses C++/Oboe full-duplex audio; Windows uses miniaudio. Projects use 48 kHz. Offline track/mix processing is shared, while device selection and real-time transport remain platform-specific. See [modeling](modeling.md) and [data format](data-format.md) for boundaries and persistence.

## Acoustic alignment

Overdubbing needs playback and capture on a common musical timeline. Acoustic sweep correlation measures the whole route, including converters, DSP and sound propagation. Hardware timestamps alone do not represent that entire delay. Calibration and recording open compatible device configurations; each take stores its own correction as an offset, independent of later calibration.

Reference Pixel 9 speaker/internal-microphone measurements found approximately 1016–1033 frames (21.17–21.52 ms) acoustic delay. Timestamp estimates omitted roughly 13 ms. Six sweeps within a session agreed within 0.02 frames, SNR was at least 38 dB, and no xruns occurred in that test; these are device-specific measurements. Acceptance thresholds are 5 ms spread for internal/wired routes and 25 ms with Bluetooth, whose stream-start timing is more variable.

Bluetooth music output has a one-second silent warmup shared by calibration/take/reference boundaries. Only later xruns count against the measurement. Reconnection invalidates Bluetooth calibration. Headset microphone capture uses a non-Bluetooth output; the app checks the actually opened devices. Long-term drift and additional hardware remain open tests.

## Capture and playback

Track processing prepares the mix before transport. The real-time callback reads prepared playback and adds the deterministic metronome. Android recording moves microphone frames through a four-second Oboe FIFO to a writer thread, finalizing the PCM16 WAV header at stop; dropped frames are logged. Live peak values are polled around every 50 ms. USB input selection uses one channel or a mono sum; references remain mono. Playback and mix exports are stereo.

Record starts at project frame zero after any silent pre-roll/count-in. With metronome, the file includes a count-in before zero and its stored offset accounts for both count-in and latency. A separate sample-accurate practice loop affects playback only. Android background service/notification preserves transport until stop or interruption, which saves without automatic restart.

## Click and bleed removal

The click is a deterministic 30 ms exponentially decaying sine burst, 1.5 kHz with a 2.5 kHz first-beat accent. One recorded four-beat count-in provides isolated templates. Template windows extend up to 300 ms or 0.9 beats, starting 32 frames early; repeated examples must agree sufficiently before subtraction. Per-type gain is constrained to 0.7–1.3 with a derivative term for sub-sample alignment. The fallback linear model estimates a 512-tap response. Cleanup is optional per track.

Bleed removal uses the immutable playback reference from that take, aligned by measured latency. The 384-tap filter starts 64 frames early; FFT size is 16384, estimation uses at most 60 s, and subtraction uses overlap-add convolution. Clipping-aware bounds and smoothed gain tracking form cache version 4. See [audio quality](audio-quality.md) for measurements and limitations.

## Tone, effects and master

Track input cleanup and normalization precede up to six effect slots, then volume/envelope/pan. The sum passes through the master chain, BS.1770 loudness normalization to −14 LUFS (at most +24 dB), and a −1 dBFS limiter. Metronome is added afterward. Airwindows slots optionally match output/input loudness within ±24 dB. The preview path renders short clips with the same processing.

## Live monitoring

`LiveChain` runs a track chain in the duplex callback: EQ/compressor, the shared FDN reverb (`Reverb.h`) and Airwindows instances created off the audio thread, then track volume and mono pan. Without level matching it is sample-identical to the rendered chain (`tools/live_chain_test.cpp`); Airwindows level matching uses a gain estimated on pink noise, and normalization/cleanup need the whole take and do not apply. Airwindows instances are seeded with a fixed `rand()` value, so renders are reproducible and match live. `LiveMonitor` swaps chains lock-free with a 20 ms crossfade and frees replaced chains on the control thread. The callback writes the dry microphone sample to the take FIFO before the monitor reads its own copy; the overdub reference is captured before the monitored signal is added. Live audition opens the same duplex streams as a take without playback or file. The global buffer setting sets the Oboe output buffer to 1 or 4 bursts or the miniaudio period to 128 or 512 frames (Short/Long) for calibration, takes and live alike, and calibrations are keyed per setting.

My Audio reverb uses a 16-line feedback delay network with prime delay lengths scaled from 23–80 ms, Householder feedback and per-line absorption for low/high RT60. Pre-delay, eight early-reflection taps and four diffusion allpasses precede it. Room scale is sqrt(RT60/1.5 s), bounded to 0.5–2. Impulse-response energy normalization and equal-power dry/wet mixing keep levels predictable. Reverb tails extend tracks up to 12 s. Stereo channels retain separate input paths.

## Verification boundaries

Native tests exercise deterministic processing and warmup/timeline boundaries. JVM tests cover parameters, format and calibration evaluation. Emulator tests cover UI and imports; physical devices are required for latency, routing, drift, CPU load and listening. Provisional minimum Android API is 29; hardware breadth and playback-underflow instrumentation remain in the backlog.
