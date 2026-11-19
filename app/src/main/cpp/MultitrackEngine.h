#pragma once

#include <atomic>
#include <cstdint>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

#include <oboe/Oboe.h>

#include "InputChannel.h"
#include "ProjectMix.h"
#include "Wav.h"
#include "VolumeEnvelope.h"
#include "PlaybackLoop.h"
#include "StreamWarmup.h"

namespace myaudio {

/**
 * Plays the project mix and, for overdubs, records the microphone in the same full-duplex
 * callback. Output frame n is project frame n; recorded frame n therefore belongs to project
 * frame n - latency, which the caller stores as the new track's offset.
 *
 * The mix is rendered offline by setTracks(), so per-track processing (gain now, effects such
 * as reverb later) never runs in the real-time callback and never modifies the WAV files.
 * All public methods are serialised; the mix only changes while no stream is running.
 */
class MultitrackEngine : public oboe::FullDuplexStream, public oboe::AudioStreamErrorCallback {
public:
    static constexpr int kSampleRate = kProjectSampleRate;

    static MultitrackEngine &instance();

    /** Loads the tracks and renders the project mix; blocks, call from a background thread. */
    bool setTracks(const std::vector<TrackSource> &sources, const std::vector<EffectSlot> &master);
    /**
     * Writes each take as a mono WAV from project frame 0 to [projectFrames]: click and bleed
     * removed, latency compensated, without effects, volume or pan. Blocks; fails while streaming.
     */
    bool exportStems(const std::vector<TrackSource> &sources, int64_t projectFrames,
                     const std::vector<std::string> &targets);
    bool startPlayback(int outputDeviceId, bool bluetooth);
    /**
     * Records from project frame -countInFrames, so the count-in clicks precede frame 0. If the
     * mix is not empty, the played mix (without the click) is written to [referencePath] in step
     * with the take, for the later bleed removal.
     */
    bool startRecording(const std::string &path, const std::string &referencePath, int64_t countInFrames,
                        int outputDeviceId, int inputDeviceId, bool handsFreeInput, bool bluetooth,
                        InputChannel inputChannel);
    std::vector<int> currentDeviceIds();
    int transportXruns();
    /** Stops any stream; returns the recorded frames for a recording, otherwise -1. */
    int64_t stopTransport();
    int64_t positionFrames() const { return mPosition.load(std::memory_order_relaxed); }
    /** False when idle, after playback reached the end, or after a stream error. */
    bool isActive() const { return mActive.load(std::memory_order_relaxed); }
    float takeInputPeak() { return mInputPeak.exchange(0.0f, std::memory_order_relaxed); }
    /** Click synthesised in the callback on top of the mix; may change while a stream runs. */
    void setMetronome(bool enabled, float bpm, float volume);
    /** Exactly what playback outputs (mix plus audible click), for export; empty while a stream runs. */
    std::vector<float> renderPlayback();
    /** Jumps playback to a project frame; ignored while recording (takes always start at 0). */
    void seekPlayback(int64_t frame);
    /** Practice loop of the playback, [start, end) in project frames; end <= start switches it off. */
    void setLoop(int64_t start, int64_t end) { mLoop.set(start, end); }

    oboe::DataCallbackResult onBothStreamsReady(const void *inputData, int numInputFrames,
                                                void *outputData, int numOutputFrames) override;
    void onErrorBeforeClose(oboe::AudioStream *stream, oboe::Result error) override;

private:
    /** Output-only callback for plain playback. */
    class PlaybackCallback : public oboe::AudioStreamDataCallback {
    public:
        explicit PlaybackCallback(MultitrackEngine &engine) : mEngine(engine) {}
        oboe::DataCallbackResult onAudioReady(oboe::AudioStream *stream, void *audioData, int32_t numFrames) override;

    private:
        MultitrackEngine &mEngine;
    };

    MultitrackEngine();
    oboe::Result openOutput(oboe::AudioStreamDataCallback *callback, int deviceId);
    /** Renders the output; [mixOnly] (optional) receives the mix without the click; [looping] applies the practice loop. */
    void mix(float *output, int channels, int numFrames, float *mixOnly = nullptr, bool looping = false);
    void writerLoop();
    void closeStreams();
    bool waitForWarmup();
    int streamXruns() const;

    std::mutex mLock;
    StreamWarmup mWarmup;
    PlaybackLoop mLoop;
    int mStartXruns = 0;
    // Rendered stereo project mix after the master stage, index = project frame.
    std::vector<float> mMixLeft;
    std::vector<float> mMixRight;
    int64_t mixFrames() const { return static_cast<int64_t>(mMixLeft.size()); }
    PlaybackCallback mPlaybackCallback;
    std::shared_ptr<oboe::AudioStream> mOutput;
    std::shared_ptr<oboe::AudioStream> mInput;
    bool mRecording = false;
    InputChannel mInputChannel = InputChannel::Device;

    std::atomic<int64_t> mPosition{0};
    std::atomic<int64_t> mSeekRequest{-1};  // pending playback jump, -1 = none
    std::atomic<bool> mActive{false};
    std::atomic<float> mInputPeak{0.0f};

    std::atomic<bool> mMetronomeEnabled{false};
    std::atomic<float> mMetronomeBpm{120.0f};
    std::atomic<float> mMetronomeGain{0.15f};

    std::unique_ptr<oboe::FifoBuffer> mFifo;
    std::unique_ptr<oboe::FifoBuffer> mReferenceFifo;  // null when nothing is played
    std::vector<float> mReferenceScratch;
    WavWriter mReferenceWav;
    std::atomic<bool> mWriterRunning{false};
    std::atomic<int64_t> mDroppedFrames{0};
    std::thread mWriter;
    WavWriter mWav;
};

}  // namespace myaudio
