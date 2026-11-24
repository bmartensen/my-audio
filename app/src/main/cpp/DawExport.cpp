#include "DawExport.h"

#include <algorithm>
#include <cstdio>
#include <cstring>
#include <limits>

#include "Log.h"
#include "Loudness.h"

namespace myaudio {
namespace {

constexpr const char *kTag = "MyAudioExport";

void put32(std::vector<uint8_t> &out, uint32_t v) {
    for (int i = 0; i < 4; ++i) out.push_back(static_cast<uint8_t>(v >> (8 * i)));
}
void put16(std::vector<uint8_t> &out, uint16_t v) {
    out.push_back(static_cast<uint8_t>(v));
    out.push_back(static_cast<uint8_t>(v >> 8));
}

}  // namespace

bool writeFloatWav(const std::string &path, const std::vector<float> &left, const std::vector<float> &right,
                   size_t first, size_t frames, int channels, int sampleRate) {
    FILE *file = std::fopen(path.c_str(), "wb");
    if (file == nullptr) return false;
    const uint32_t dataBytes = static_cast<uint32_t>(frames * channels * 4);
    std::vector<uint8_t> header;
    header.insert(header.end(), {'R', 'I', 'F', 'F'});
    put32(header, 4 + (8 + 18) + (8 + 4) + (8 + dataBytes));
    header.insert(header.end(), {'W', 'A', 'V', 'E', 'f', 'm', 't', ' '});
    put32(header, 18);
    put16(header, 3);  // IEEE float
    put16(header, static_cast<uint16_t>(channels));
    put32(header, static_cast<uint32_t>(sampleRate));
    put32(header, static_cast<uint32_t>(sampleRate * channels * 4));
    put16(header, static_cast<uint16_t>(channels * 4));
    put16(header, 32);
    put16(header, 0);
    header.insert(header.end(), {'f', 'a', 'c', 't'});
    put32(header, 4);
    put32(header, static_cast<uint32_t>(frames));
    header.insert(header.end(), {'d', 'a', 't', 'a'});
    put32(header, dataBytes);
    bool ok = std::fwrite(header.data(), 1, header.size(), file) == header.size();
    std::vector<float> block;
    constexpr size_t kBlock = 4096;
    for (size_t start = 0; ok && start < frames; start += kBlock) {
        const size_t count = std::min(kBlock, frames - start);
        block.resize(count * channels);
        for (size_t i = 0; i < count; ++i) {
            const size_t index = first + start + i;
            const float l = index < left.size() ? left[index] : 0.0f;
            const float r = right.empty() ? l : (index < right.size() ? right[index] : 0.0f);
            if (channels == 1) {
                block[i] = l;
            } else {
                block[i * 2] = l;
                block[i * 2 + 1] = r;
            }
        }
        ok = std::fwrite(block.data(), sizeof(float), block.size(), file) == block.size();
    }
    return std::fclose(file) == 0 && ok;
}

bool exportDawTracks(const std::vector<TrackSource> &sources, const std::vector<std::vector<EffectSlot>> &renderChains,
                     const std::vector<std::string> &dryPaths, const std::vector<std::string> &wetPaths,
                     std::vector<DawTrack> &tracks) {
    if (renderChains.size() != sources.size() || dryPaths.size() != sources.size() || wetPaths.size() != sources.size()) {
        return false;
    }
    tracks.assign(sources.size(), DawTrack{});
    std::vector<float> left, right, wetLeft, wetRight;
    for (size_t t = 0; t < sources.size(); ++t) {
        const TrackSource &source = sources[t];
        if (!loadProcessedTake(source, left, right)) continue;
        // Count-in and latency lie before the take's start; the clip starts there.
        const auto first = static_cast<size_t>(std::clamp<int64_t>(source.leadFrames(), 0, static_cast<int64_t>(left.size())));
        DawTrack &track = tracks[t];
        track.start = source.offsetFrames + static_cast<int64_t>(first);
        track.dryFrames = static_cast<int64_t>(left.size() - first);
        bool stereo = !right.empty();
        bool wetStereo = stereo;
        if (!renderChains[t].empty()) {
            wetLeft = left;
            wetRight = right;
            applyEffectChain(renderChains[t], wetLeft, wetRight, wetStereo, kProjectSampleRate);
            if (!wetStereo) wetRight.clear();
            track.wetFrames = static_cast<int64_t>(wetLeft.size() > first ? wetLeft.size() - first : 0);
        }
        track.channels = stereo || (track.wetFrames > 0 && wetStereo) ? 2 : 1;
        if (!writeFloatWav(dryPaths[t], left, right, first, static_cast<size_t>(track.dryFrames), track.channels,
                           kProjectSampleRate)) {
            MYAUDIO_LOGW(kTag, "Cannot write %s", dryPaths[t].c_str());
            return false;
        }
        if (track.wetFrames > 0 &&
            !writeFloatWav(wetPaths[t], wetLeft, wetRight, first, static_cast<size_t>(track.wetFrames), track.channels,
                           kProjectSampleRate)) {
            MYAUDIO_LOGW(kTag, "Cannot write %s", wetPaths[t].c_str());
            return false;
        }
    }
    return true;
}

double mixLoudness(const std::vector<TrackSource> &sources, const std::vector<EffectSlot> &master) {
    std::vector<float> left, right;
    mixTracks(sources, left, right);
    if (left.empty()) return -std::numeric_limits<double>::infinity();
    bool stereo = true;
    applyEffectChain(master, left, right, stereo, kProjectSampleRate);
    return integratedLoudness(left, right);
}

}  // namespace myaudio
