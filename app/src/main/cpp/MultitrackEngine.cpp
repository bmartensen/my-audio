#include "MultitrackEngine.h"

#include <algorithm>
#include <chrono>
#include <cmath>

#include <android/log.h>

#include "Metronome.h"

namespace myaudio {
namespace {

constexpr const char *kTag = "MyAudioEngine";
constexpr int kFifoSeconds = 4;
constexpr int kWriterChunkFrames = 4096;

}  // namespace

MultitrackEngine::MultitrackEngine() : mPlaybackCallback(*this) {}

MultitrackEngine &MultitrackEngine::instance() {
    static MultitrackEngine engine;
    return engine;
}

void MultitrackEngine::seekPlayback(int64_t frame) {
    std::lock_guard<std::mutex> guard(mLock);
    if (!mOutput || mRecording) return;
    // The callback owns mPosition; it picks the request up at the start of its next block.
    mSeekRequest.store(std::clamp<int64_t>(frame, 0, mixFrames()), std::memory_order_relaxed);
}

std::vector<float> MultitrackEngine::renderPlayback() {
    std::lock_guard<std::mutex> guard(mLock);
    if (mOutput) return {};
    // Same output as the playback callback: the mix plus the click if it is currently audible,
    // ending where playback ends.
    // Interleaved stereo.
    const auto frames = static_cast<size_t>(mixFrames());
    std::vector<float> output(frames * 2);
    const int64_t savedPosition = mPosition.load();
    mPosition = 0;
    constexpr int kChunk = 4096;
    for (size_t start = 0; start < frames; start += kChunk) {
        const int chunk = static_cast<int>(std::min<size_t>(kChunk, frames - start));
        mix(output.data() + start * 2, 2, chunk);
    }
    mPosition = savedPosition;
    return output;
}

void MultitrackEngine::setMetronome(bool enabled, float bpm, float volume) {
    mMetronomeBpm.store(std::clamp(bpm, 20.0f, 400.0f), std::memory_order_relaxed);
    mMetronomeGain.store(std::clamp(volume, 0.0f, 1.0f), std::memory_order_relaxed);
    mMetronomeEnabled.store(enabled, std::memory_order_relaxed);
}

bool MultitrackEngine::setTracks(const std::vector<TrackSource> &sources, const std::vector<EffectSlot> &master) {
    std::lock_guard<std::mutex> guard(mLock);
    if (mOutput) return false;
    std::vector<float> mixLeft;
    std::vector<float> mixRight;
    renderProjectMix(sources, master, mixLeft, mixRight);
    mMixLeft = std::move(mixLeft);
    mMixRight = std::move(mixRight);
    return true;
}

bool MultitrackEngine::exportStems(const std::vector<TrackSource> &sources, int64_t projectFrames,
                                   const std::vector<std::string> &targets) {
    std::lock_guard<std::mutex> guard(mLock);
    if (mOutput) return false;
    return writeStems(sources, projectFrames, targets);
}

oboe::Result MultitrackEngine::openOutput(oboe::AudioStreamDataCallback *callback, int deviceId) {
    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)
            ->setDeviceId(deviceId)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setSharingMode(oboe::SharingMode::Exclusive)
            ->setFormat(oboe::AudioFormat::Float)
            ->setChannelCount(oboe::ChannelCount::Stereo)
            ->setChannelConversionAllowed(true)
            ->setSampleRate(kSampleRate)
            ->setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium)
            ->setUsage(oboe::Usage::Media)
            ->setContentType(oboe::ContentType::Music)
            ->setDataCallback(callback)
            ->setErrorCallback(this);
    const auto result = builder.openStream(mOutput);
    if (result == oboe::Result::OK && deviceId != 0 && mOutput->getDeviceId() != deviceId) {
        mOutput->close();
        mOutput.reset();
        return oboe::Result::ErrorUnavailable;
    }
    return result;
}

bool MultitrackEngine::startPlayback(int outputDeviceId, bool bluetooth) {
    std::lock_guard<std::mutex> guard(mLock);
    if (mOutput || mMixLeft.empty()) return false;
    // With a practice loop, the very first block already starts there.
    const PlaybackLoop::Range loop = mLoop.range();
    mPosition = loop.active() ? loop.start : 0;
    mSeekRequest = -1;
    mRecording = false;
    mWarmup.reset(bluetooth ? kSampleRate * StreamWarmup::kBluetoothSeconds : 0);
    if (openOutput(&mPlaybackCallback, outputDeviceId) != oboe::Result::OK) {
        mOutput.reset();
        return false;
    }
    mActive = true;
    if (mOutput->requestStart() != oboe::Result::OK || !waitForWarmup()) {
        mActive = false;
        closeStreams();
        return false;
    }
    return true;
}

bool MultitrackEngine::startRecording(const std::string &path, const std::string &referencePath,
                                      int64_t countInFrames, int outputDeviceId, int inputDeviceId, bool handsFreeInput, bool bluetooth,
                                      InputChannel inputChannel) {
    std::lock_guard<std::mutex> guard(mLock);
    if (mOutput) return false;
    mPosition = -std::max<int64_t>(0, countInFrames);
    mSeekRequest = -1;  // takes always start at the beginning, never at a stale jump target
    mInputPeak = 0.0f;
    mDroppedFrames = 0;
    mInputChannel = inputChannel;
    mWarmup.reset(bluetooth ? kSampleRate * StreamWarmup::kBluetoothSeconds : 0);
    if (openOutput(this, outputDeviceId) != oboe::Result::OK) {
        mOutput.reset();
        return false;
    }
    // Same configuration as the calibration so the measured latency applies unchanged.
    oboe::AudioStreamBuilder inputBuilder;
    inputBuilder.setDirection(oboe::Direction::Input)
            ->setDeviceId(inputDeviceId)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setSharingMode(oboe::SharingMode::Exclusive)
            ->setFormat(oboe::AudioFormat::Float)
            // Mono conversion keeps only the first channel; two-channel devices pick it in the callback.
            ->setChannelCount(inputChannel == InputChannel::Device ? oboe::ChannelCount::Mono : oboe::ChannelCount::Stereo)
            ->setChannelConversionAllowed(true)
            ->setSampleRate(kSampleRate)
            ->setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium)
            ->setInputPreset(handsFreeInput ? oboe::InputPreset::VoiceRecognition : oboe::InputPreset::VoicePerformance)
            ->setBufferCapacityInFrames(mOutput->getBufferCapacityInFrames() * 2)
            ->setErrorCallback(this);
    if (inputBuilder.openStream(mInput) != oboe::Result::OK ||
        mInput->getDeviceId() != inputDeviceId || !mWav.open(path, kSampleRate)) {
        closeStreams();
        mWav.finish();
        return false;
    }
    mFifo = std::make_unique<oboe::FifoBuffer>(sizeof(float), kFifoSeconds * kSampleRate);
    // Capture exactly what is played (effects as they are now), in step with the take.
    mReferenceFifo.reset();
    if (!referencePath.empty() && !mMixLeft.empty() && mReferenceWav.open(referencePath, kSampleRate)) {
        mReferenceFifo = std::make_unique<oboe::FifoBuffer>(sizeof(float), kFifoSeconds * kSampleRate);
        mReferenceScratch.assign(static_cast<size_t>(mOutput->getBufferCapacityInFrames()), 0.0f);
    }
    setSharedOutputStream(mOutput);
    setSharedInputStream(mInput);
    mRecording = true;
    mActive = true;
    mWriterRunning = true;
    mWriter = std::thread(&MultitrackEngine::writerLoop, this);
    if (start() != oboe::Result::OK || !waitForWarmup()) {
        mActive = false;
        closeStreams();
        mWriterRunning = false;
        mWriter.join();
        mWav.finish();
        mReferenceWav.finish();
        mRecording = false;
        return false;
    }
    mStartXruns = streamXruns();
    return true;
}

bool MultitrackEngine::waitForWarmup() {
    const auto deadline = std::chrono::steady_clock::now() + std::chrono::seconds(StreamWarmup::kBluetoothSeconds + 3);
    while (!mWarmup.ready() && mActive.load(std::memory_order_relaxed) &&
           std::chrono::steady_clock::now() < deadline) {
        std::this_thread::sleep_for(std::chrono::milliseconds(5));
    }
    return mWarmup.ready() && mActive.load(std::memory_order_relaxed);
}

int MultitrackEngine::streamXruns() const {
    const auto output = mOutput ? mOutput->getXRunCount() : oboe::ResultWithValue<int32_t>(0);
    const auto input = mInput ? mInput->getXRunCount() : oboe::ResultWithValue<int32_t>(0);
    return (output ? output.value() : 0) + (input ? input.value() : 0);
}

int MultitrackEngine::transportXruns() {
    std::lock_guard<std::mutex> guard(mLock);
    return std::max(0, streamXruns() - mStartXruns) + (mDroppedFrames.load() > 0 ? 1 : 0);
}

std::vector<int> MultitrackEngine::currentDeviceIds() {
    std::lock_guard<std::mutex> guard(mLock);
    return {mOutput ? mOutput->getDeviceId() : 0, mInput ? mInput->getDeviceId() : 0};
}

int64_t MultitrackEngine::stopTransport() {
    std::lock_guard<std::mutex> guard(mLock);
    mActive = false;
    closeStreams();
    if (!mRecording) return -1;
    mRecording = false;
    mWriterRunning = false;
    if (mWriter.joinable()) mWriter.join();
    const int64_t frames = mWav.finish();
    mReferenceWav.finish();
    mReferenceFifo.reset();
    if (mDroppedFrames > 0) {
        __android_log_print(ANDROID_LOG_WARN, kTag, "Dropped %lld input frames", static_cast<long long>(mDroppedFrames.load()));
    }
    return frames;
}

void MultitrackEngine::closeStreams() {
    if (mOutput) {
        mOutput->stop();
        mOutput->close();
        mOutput.reset();
    }
    if (mInput) {
        mInput->stop();
        mInput->close();
        mInput.reset();
    }
}

void MultitrackEngine::mix(float *output, int channels, int numFrames, float *mixOnly, bool looping) {
    const int64_t seek = mSeekRequest.exchange(-1, std::memory_order_relaxed);
    int64_t position = seek >= 0 ? seek : mPosition.load(std::memory_order_relaxed);
    const int64_t length = mixFrames();
    const bool metronome = mMetronomeEnabled.load(std::memory_order_relaxed);
    const ClickGrid grid(mMetronomeBpm.load(std::memory_order_relaxed), kSampleRate);
    const float metronomeGain = mMetronomeGain.load(std::memory_order_relaxed);
    // Only playback loops; a take and the shared export need the continuous project timeline.
    const PlaybackLoop::Range loop = looping ? mLoop.range() : PlaybackLoop::Range{0, -1};
    for (int frame = 0; frame < numFrames; ++frame) {
        position = loop.wrap(position);
        const int64_t projectFrame = position++;
        const bool inMix = projectFrame >= 0 && projectFrame < length;
        float left = inMix ? mMixLeft[projectFrame] : 0.0f;
        float right = inMix ? mMixRight[projectFrame] : 0.0f;
        // The overdub reference is what a mono microphone hears: the sum of both channels.
        if (mixOnly != nullptr) mixOnly[frame] = 0.5f * (left + right);
        if (metronome) {
            const int64_t beat = grid.beatAt(projectFrame);
            const int64_t offset = projectFrame - grid.beatStart(beat);
            const std::vector<float> &click = ClickGrid::click(ClickGrid::isAccent(beat));
            if (offset >= 0 && offset < static_cast<int64_t>(click.size())) {
                const float tick = metronomeGain * click[offset];
                left = std::clamp(left + tick, -1.0f, 1.0f);
                right = std::clamp(right + tick, -1.0f, 1.0f);
            }
        }
        float *out = output + frame * channels;
        if (channels == 1) {
            out[0] = 0.5f * (left + right);
        } else {
            out[0] = left;
            out[1] = right;
            for (int channel = 2; channel < channels; ++channel) out[channel] = 0.0f;
        }
    }
    mPosition.store(loop.wrap(position), std::memory_order_relaxed);
}

oboe::DataCallbackResult MultitrackEngine::onBothStreamsReady(const void *inputData, int numInputFrames,
                                                             void *outputData, int numOutputFrames) {
    if (mWarmup.discard(numOutputFrames)) {
        std::fill_n(static_cast<float *>(outputData),
                    numOutputFrames * getOutputStream()->getChannelCount(), 0.0f);
        return oboe::DataCallbackResult::Continue;
    }
    const bool captureReference = mReferenceFifo != nullptr &&
                                  numOutputFrames <= static_cast<int>(mReferenceScratch.size());
    mix(static_cast<float *>(outputData), getOutputStream()->getChannelCount(), numOutputFrames,
        captureReference ? mReferenceScratch.data() : nullptr);
    if (captureReference && mReferenceFifo->write(mReferenceScratch.data(), numOutputFrames) != numOutputFrames) {
        mDroppedFrames.fetch_add(numOutputFrames, std::memory_order_relaxed);
    }

    const auto *input = static_cast<const float *>(inputData);
    const int inputChannels = getInputStream()->getChannelCount();
    float peak = mInputPeak.load(std::memory_order_relaxed);
    for (int frame = 0; frame < numInputFrames; ++frame) {
        const float sample = monoInputSample(input + frame * inputChannels, inputChannels, mInputChannel);
        peak = std::max(peak, std::fabs(sample));
        if (mFifo->write(&sample, 1) != 1) mDroppedFrames.fetch_add(1, std::memory_order_relaxed);
    }
    mInputPeak.store(peak, std::memory_order_relaxed);
    return oboe::DataCallbackResult::Continue;
}

oboe::DataCallbackResult MultitrackEngine::PlaybackCallback::onAudioReady(oboe::AudioStream *stream, void *audioData,
                                                                          int32_t numFrames) {
    if (mEngine.mWarmup.discard(numFrames)) {
        std::fill_n(static_cast<float *>(audioData), numFrames * stream->getChannelCount(), 0.0f);
        return oboe::DataCallbackResult::Continue;
    }
    mEngine.mix(static_cast<float *>(audioData), stream->getChannelCount(), numFrames, nullptr, true);
    if (mEngine.positionFrames() >= mEngine.mixFrames()) {
        mEngine.mActive.store(false, std::memory_order_relaxed);
        return oboe::DataCallbackResult::Stop;
    }
    return oboe::DataCallbackResult::Continue;
}

void MultitrackEngine::onErrorBeforeClose(oboe::AudioStream * /* stream */, oboe::Result error) {
    __android_log_print(ANDROID_LOG_WARN, kTag, "Stream error: %s", oboe::convertToText(error));
    mActive.store(false, std::memory_order_relaxed);
}

void MultitrackEngine::writerLoop() {
    std::vector<float> chunk(kWriterChunkFrames);
    std::vector<int16_t> pcm(kWriterChunkFrames);
    // Moves what is available from one FIFO to its WAV file; returns the frames written.
    auto drain = [&](oboe::FifoBuffer *fifo, WavWriter &wav) {
        if (fifo == nullptr) return 0;
        const auto available = static_cast<int32_t>(fifo->getFullFramesAvailable());
        if (available <= 0) return 0;
        const int32_t frames = fifo->read(chunk.data(), std::min(available, kWriterChunkFrames));
        for (int32_t i = 0; i < frames; ++i) pcm[i] = toPcm16(chunk[i]);
        wav.write(pcm.data(), static_cast<size_t>(frames));
        return frames;
    };
    while (true) {
        const bool running = mWriterRunning.load(std::memory_order_acquire);
        const int written = drain(mFifo.get(), mWav) + drain(mReferenceFifo.get(), mReferenceWav);
        if (written > 0) continue;
        if (!running) break;  // streams are stopped and both FIFOs are drained
        std::this_thread::sleep_for(std::chrono::milliseconds(10));
    }
}

}  // namespace myaudio
