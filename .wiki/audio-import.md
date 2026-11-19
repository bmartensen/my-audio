# Stereo import and playback validation

Stereo is preserved through platform decoding, channel-aware resampling, PCM16 WAV storage, shared native mixing and export. Recordings and overdub references remain mono. Windows enables `MA_DR_MP3_FLOAT_OUTPUT` to retain float precision until final PCM16 storage. Android requests float output for compressed formats and reads the actual output format; raw WAV keeps its extractor-reported encoding because KEY_PCM_ENCODING also specifies input encoding.

Stereo balance attenuates only the opposite channel. Normalization/compression share one gain factor across channels, reverb preserves separate inputs, and waveforms use the largest channel magnitude per bucket, including opposite-phase audio. New takes/imports explicitly disable click/bleed cleanup; existing-file missing-field defaults remain part of the format contract.

## Measurements and their limits

A test set of eleven stereo 48 kHz MP3 tracks, each 9,507,456 frames (198.072 s), was used; it is not part of the repository. Opposing channel content in two tracks (correlation −0.389 and −0.263) makes mono folding lose 5.15/4.34 dB of energy relative to mean stereo-channel energy. This supports preserving channels, but does not establish every cause of reported crackle.

The real Windows JNI decoder, shared WAV writer and solo render produced the expected length, two distinct channels, finite values and peaks at or below −1 dBFS for all eleven tracks. Float decoder deviation from an independent FFmpeg decode was approximately −144 to −170 dBFS RMS. The combined mix had the same frame count and rendered in about 0.5 s on the test workstation. Mono compatibility output was sample-identical in the comparison test.

Synthetic MP3/Vorbis/Opus/24-bit WAV tests imported exactly 144,000 frames (3 s at 48 kHz), with 440 Hz left and 660 Hz right; resampling from 44.1 kHz and Opus pre-skip/end trim were covered. Automated tests cover phase opposition, separation, resampling block boundaries, waveform peaks, format versions, backup round trips and cleanup defaults. Native stereo tests cover balance, envelopes, linked effects, reverb and stem export.

The offline Windows callback path plays prepared stereo audio and passes finite-value/peak tests. That does not exclude output underruns or hardware/Bluetooth distortion. Desktop `transportXruns()` counts dropped capture frames, not separate playback underruns. Master normalization can make low-level source artifacts more audible by raising quiet solo tracks toward −14 LUFS, limited to +24 dB.

Listening acceptance with a fresh import of the original material on Windows and Android remains open. Offline measurements alone do not establish that the reported crackle is resolved.
