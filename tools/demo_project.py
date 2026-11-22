"""Writes a My Audio backup ZIP with a short synthesized demo song, for screenshots and user documentation.

The song is generated here (no samples, no recordings): 100 BPM, Am-F-C-G over eight bars with drums, bass,
keys and a melody, plus effects, an envelope, notes with chords, a practice loop and a prepared record track.
Import it with "Import project" on the home screen.

Usage: python tools/demo_project.py <output.zip>
"""
import io
import json
import math
import random
import struct
import sys
import time
import wave
import zipfile

RATE = 48000
BPM = 100
BEAT = 60.0 / BPM
BARS = 8
LENGTH = int(BARS * 4 * BEAT * RATE)
CHORDS = [  # root (Hz) and triad (Hz) per bar: Am, F, C, G
    (55.00, [220.00, 261.63, 329.63]),
    (43.65, [174.61, 220.00, 261.63]),
    (65.41, [261.63, 329.63, 392.00]),
    (49.00, [196.00, 246.94, 293.66]),
]


def new_track():
    return [0.0] * LENGTH


def add(track, start, samples):
    for i, value in enumerate(samples):
        if 0 <= start + i < LENGTH:
            track[start + i] += value


def frame(beats):
    return int(beats * BEAT * RATE)


def drums():
    track = new_track()
    rnd = random.Random(7)
    kick = [0.9 * math.exp(-i / (0.07 * RATE)) * math.sin(2 * math.pi * (45 * i / RATE + 75 * 0.04 * (1 - math.exp(-i / (0.04 * RATE)))))
            for i in range(int(0.35 * RATE))]
    snare = [(0.5 * rnd.uniform(-1, 1) + 0.3 * math.sin(2 * math.pi * 185 * i / RATE)) * math.exp(-i / (0.06 * RATE))
             for i in range(int(0.25 * RATE))]
    hat = []
    last = 0.0
    for i in range(int(0.05 * RATE)):
        white = rnd.uniform(-1, 1)
        hat.append(0.18 * (white - last) * math.exp(-i / (0.012 * RATE)))
        last = white
    for bar in range(BARS):
        for beat in range(4):
            start = bar * 4 + beat
            if beat in (0, 2) or (beat == 3 and bar % 2 == 1):
                add(track, frame(start + (0.5 if beat == 3 else 0)), kick)
            if beat in (1, 3):
                add(track, frame(start), snare)
            add(track, frame(start), hat)
            add(track, frame(start + 0.5), [0.6 * v for v in hat])
    return track


def bass():
    track = new_track()
    for bar in range(BARS):
        root = CHORDS[bar % 4][0]
        for step, (offset, octave) in enumerate([(0, 1), (1.5, 1), (2, 2), (3, 1), (3.5, 2)]):
            length = int(0.4 * BEAT * RATE * (2 if step == 0 else 1))
            f = root * octave
            note = [0.45 * math.tanh(1.6 * (math.sin(2 * math.pi * f * i / RATE) + 0.35 * math.sin(4 * math.pi * f * i / RATE)))
                    * min(1.0, i / 200) * math.exp(-i / (0.35 * RATE)) for i in range(length)]
            add(track, frame(bar * 4 + offset), note)
    return track


def keys():
    track = new_track()
    for bar in range(BARS):
        triad = CHORDS[bar % 4][1]
        length = frame(4)
        start = frame(bar * 4)
        for f in triad:
            for detune in (0.997, 1.003):
                add(track, start, [0.07 * math.sin(2 * math.pi * f * detune * i / RATE)
                                   * min(1.0, i / (0.08 * RATE)) * (0.55 + 0.45 * math.exp(-i / (1.2 * RATE)))
                                   * min(1.0, (length - i) / (0.05 * RATE)) for i in range(length)])
    return track


def melody():
    track = new_track()
    # Beats, MIDI note and length in beats; silent in the first four bars, then a phrase twice.
    phrase = [(0, 76, 1), (1, 74, 0.5), (1.5, 72, 0.5), (2, 69, 1.5), (4, 72, 1), (5, 74, 1), (6, 76, 2),
              (8, 79, 1), (9, 77, 0.5), (9.5, 76, 0.5), (10, 74, 2), (12, 71, 1), (13, 72, 1), (14, 74, 2)]
    for repeat in (0, 1):
        for beat, note, beats in phrase:
            if repeat == 0 and beat < 8:
                continue
            f = 440.0 * 2 ** ((note - 69) / 12)
            length = frame(beats * 0.95)
            tone = []
            phase = 0.0
            for i in range(length):
                vibrato = 1 + 0.004 * math.sin(2 * math.pi * 5.5 * i / RATE) * min(1.0, i / (0.3 * RATE))
                phase += f * vibrato / RATE
                triangle = 4 * abs(phase - math.floor(phase + 0.5)) - 1
                tone.append(0.3 * triangle * min(1.0, i / (0.02 * RATE)) * min(1.0, (length - i) / (0.03 * RATE)))
            add(track, frame(16 + repeat * 0 + beat) if repeat == 0 else frame(16 + beat), tone)
    return track


def wav_bytes(track):
    peak = max(abs(v) for v in track) or 1.0
    scale = 0.7 / peak * 32767
    buffer = io.BytesIO()
    with wave.open(buffer, 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b''.join(struct.pack('<h', int(max(-32767, min(32767, v * scale)))) for v in track))
    return buffer.getvalue()


def slot(kind, **fields):
    return dict({'type': kind, 'enabled': True}, **fields)


def tone(low, presence, high, compressor):
    return slot('myaudio.eq', eq={'enabled': True, 'lowDb': low, 'presenceDb': presence, 'highDb': high},
                compressor={'enabled': True, 'amount': compressor})


def main(target):
    now = int(time.time() * 1000)
    hall = slot('myaudio.reverb', seconds=1.8, preDelayMs=20.0, damping=0.4, mix=0.25)
    specs = [
        ('Drums', drums, 0.0, 1.0, [tone(2.0, 2.0, 1.5, 0.5)], []),
        ('Bass', bass, 0.0, 0.9, [slot('airwindows.ToTape9', values=[])], []),
        ('Keys', keys, -0.35, 0.8, [hall], []),
        ('Melody', melody, 0.3, 0.85, [tone(-2.0, 1.5, 1.0, 0.3), dict(hall, mix=0.3)],
         [{'frame': frame(16), 'gain': 0.4}, {'frame': frame(20), 'gain': 1.0}]),
    ]
    tracks = []
    with zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED) as archive:
        for index, (name, make, pan, volume, chain, envelope) in enumerate(specs):
            track_id = now + index + 1
            archive.writestr(f'tracks/{track_id}.wav', wav_bytes(make()))
            tracks.append({
                'id': track_id, 'name': name, 'fileName': f'{track_id}.wav', 'offsetFrames': 0, 'lengthFrames': LENGTH,
                'volume': volume, 'muted': False, 'solo': False, 'pan': pan, 'createdAtMillis': now, 'countInFrames': 0,
                'effects': {'removeMetronome': False, 'removeBleed': False,
                            'normalize': {'enabled': False, 'targetDb': -6.0}, 'chain': chain},
                'volumeEnvelope': envelope,
            })
        project = {
            'version': 3, 'id': now, 'name': 'Demo Song', 'createdAtMillis': now, 'sampleRate': RATE,
            'nextTrackNumber': len(tracks) + 1,
            'notes': ('[Verse]\nAm        F         C         G\nSunday morning, coffee on the window\n'
                      'Am        F         C         G\nhumming melodies I almost know\n\n'
                      '[Chorus]\nF         G         Am   Am/G\nall the little songs we never wrote\n'
                      'F         G         C\nfinally finding home\n'),
            'notesTextSize': 18,
            'metronome': {'enabled': True, 'bpm': BPM, 'volume': 0.15, 'duringPlayback': False},
            'master': {'chain': [slot('airwindows.ToTape9', values=[])]},
            'loop': {'startFrames': frame(16), 'endFrames': frame(24), 'enabled': True},
            'tracks': tracks,
            'recordTrack': {'name': 'Guitar', 'volume': 1.0, 'muted': False, 'solo': False, 'pan': 0.15,
                            'effects': {'removeMetronome': False, 'removeBleed': False,
                                        'normalize': {'enabled': False, 'targetDb': -6.0},
                                        'chain': [tone(-2.0, 1.5, 1.0, 0.3), slot('airwindows.Drive', values=[])]},
                            'volumeEnvelope': []},
        }
        archive.writestr('project.json', json.dumps(project, indent=2))


if __name__ == '__main__':
    main(sys.argv[1])
