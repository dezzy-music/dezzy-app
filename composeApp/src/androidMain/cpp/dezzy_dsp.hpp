/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 *
 * C++20 DSP Core Header - Real-time 64-bit TDF-II Parametric EQ, Sub-Bass,
 * Multi-Band Vocal/Instrumental Separator, Phase-Coherent Mid/Side Virtualizer,
 * Soft-Knee Limiter & FFT Spectrum.
 */

#pragma once

#include <array>
#include <cmath>
#include <complex>
#include <cstdint>
#include <vector>

namespace dezzy::dsp {

constexpr int kMaxEqBands = 32;
constexpr int kMaxChannels = 8;

enum class FilterType : int32_t {
    PEAKING = 0,
    LOW_SHELF = 1,
    HIGH_SHELF = 2,
    LOW_PASS = 3,
    HIGH_PASS = 4,
};

struct BiquadSection {
    double b0 = 1.0;
    double b1 = 0.0;
    double b2 = 0.0;
    double a1 = 0.0;
    double a2 = 0.0;
    bool active = false;
    std::array<double, kMaxChannels> z1{};
    std::array<double, kMaxChannels> z2{};

    inline void resetStates() noexcept {
        z1.fill(0.0);
        z2.fill(0.0);
    }

    inline double processSample(double x, int ch) noexcept {
        const double y = b0 * x + z1[ch];
        double nextZ1 = b1 * x - a1 * y + z2[ch];
        double nextZ2 = b2 * x - a2 * y;
        if (std::abs(nextZ1) < 1e-24) nextZ1 = 0.0;
        if (std::abs(nextZ2) < 1e-24) nextZ2 = 0.0;
        z1[ch] = nextZ1;
        z2[ch] = nextZ2;
        return y;
    }
};

struct AudioDspContext {
    int sampleRate = 44100;
    int channelCount = 2;

    // Multi-band Vocal / Instrumental Separator (-1.0 = Karaoke, 0.0 = Original, +1.0 = Acapella)
    float vocalMix = 0.0f;
    bool vocalBassGuard = true;
    BiquadSection vocalLowLp{};      // 190 Hz LP: ch 0 = Mid, ch 1 = Side
    BiquadSection vocalLowHp{};      // 190 Hz HP: ch 0 = Mid, ch 1 = Side
    BiquadSection vocalHighLp{};     // 4600 Hz LP: ch 0 = Mid, ch 1 = Side
    BiquadSection vocalHighHp{};     // 4600 Hz HP: ch 0 = Mid, ch 1 = Side
    double vocalEnvM = 0.0;
    double vocalEnvS = 0.0;
    double vocalEnvCoeff = 0.002;

    bool eqEnabled = false;
    int activeBandCount = 0;
    std::array<BiquadSection, kMaxEqBands> bands{};

    // Dedicated warm sub-bass shelf (68 Hz LOW_SHELF)
    float bassStrength = 0.0f;
    BiquadSection bassShelf{};

    // Phase-coherent Mid/Side stereo virtualizer (preserves low-end < 160 Hz center)
    float virtualizerStrength = 0.0f;
    double sideLpCoeff = 0.02;
    double sideLpState = 0.0;

    // Smoothed linear preamp gain
    float targetOutputGain = 1.0f;
    double currentOutputGain = 1.0;
    double gainSmoothCoeff = 0.005;

    // Envelope-tracked soft-knee true-peak limiter
    bool limiterEnabled = true;
    double limiterEnv = 0.0;
    double limiterAttackCoeff = 0.03;
    double limiterReleaseCoeff = 0.0004;
    float lastGainReductionDb = 0.0f;

    // Fast TPDF dither PRNG state (non-zero seed)
    uint32_t ditherState = 0x12345678u;

    inline void resetPlaybackStates() noexcept {
        vocalLowLp.resetStates();
        vocalLowHp.resetStates();
        vocalHighLp.resetStates();
        vocalHighHp.resetStates();
        vocalEnvM = 0.0;
        vocalEnvS = 0.0;
        for (auto& b : bands) {
            b.resetStates();
        }
        bassShelf.resetStates();
        sideLpState = 0.0;
        limiterEnv = 0.0;
        currentOutputGain = static_cast<double>(targetOutputGain);
        lastGainReductionDb = 0.0f;
    }
};

struct SpectrumConfig {
    int numBands = 32;
    float minFreq = 20.0f;
    float maxFreq = 20000.0f;
    float smoothingFactor = 0.65f;
    float sampleRate = 44100.0f;
};

class AudioSpectrumProcessor {
public:
    explicit AudioSpectrumProcessor(const SpectrumConfig& config = SpectrumConfig{});
    ~AudioSpectrumProcessor() = default;

    AudioSpectrumProcessor(const AudioSpectrumProcessor&) = delete;
    AudioSpectrumProcessor& operator=(const AudioSpectrumProcessor&) = delete;
    AudioSpectrumProcessor(AudioSpectrumProcessor&&) noexcept = default;
    AudioSpectrumProcessor& operator=(AudioSpectrumProcessor&&) noexcept = default;

    void processPcm(const int16_t* pcmSamples, size_t count, float* outBands);
    void setSmoothing(float factor);

private:
    void computeFft(std::vector<std::complex<float>>& data);

    SpectrumConfig config_;
    std::vector<float> prevBands_;
    std::vector<float> hanningWindow_;
};

} // namespace dezzy::dsp
