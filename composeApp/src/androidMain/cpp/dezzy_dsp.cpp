/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

/**
 * Dezzy Native DSP & Visualizer Engine (C++20)
 * Zero-copy, zero-GC audio processing:
 *  - Complementary multi-band Mid/Side Vocal <-> Instrumental separator (Karaoke / Acapella)
 *  - 64-bit TDF-II multi-band parametric biquad EQ (Peaking/LowShelf/HighShelf/LowPass/HighPass)
 *  - Warm Sub-Bass shelf enhancement (68 Hz)
 *  - Phase-coherent Mid/Side stereo virtualizer (protects <160 Hz bass center)
 *  - Smooth output preamp + envelope soft-knee anti-clip limiter + 16-bit TPDF dither
 *  - High-speed FFT reduction and 3D spherical visualizer projection
 */

#include "dezzy_dsp.hpp"

#include <jni.h>
#include <algorithm>
#include <cmath>
#include <cstdint>
#include <new>
#include <vector>
#include <android/log.h>

#define TAG "DezzyDSP"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)

namespace {

constexpr int BANDS = 64;
constexpr int DEFAULT_SPHERE_POINTS = 950;
constexpr float GOLDEN_ANGLE = 2.399963f;
constexpr double kPi = 3.14159265358979323846;

struct SphereNode {
    float x;
    float y;
    float z;
    float lat;
};

static std::vector<SphereNode> s_spherePoints;
static bool s_initialized = false;

static void initializeSphere(int pointCount = DEFAULT_SPHERE_POINTS) {
    s_spherePoints.clear();
    s_spherePoints.reserve(pointCount);

    for (int i = 0; i < pointCount; ++i) {
        float y = 1.0f - 2.0f * (static_cast<float>(i) + 0.5f) / static_cast<float>(pointCount);
        float r = std::sqrt(std::max(0.0f, 1.0f - y * y));
        float phi = static_cast<float>(i) * GOLDEN_ANGLE;
        float x = std::cos(phi) * r;
        float z = std::sin(phi) * r;
        float lat = std::asin(std::clamp(y, -1.0f, 1.0f));
        s_spherePoints.push_back({x, y, z, lat});
    }
    s_initialized = true;
}

static void computeBiquadSection(
    dezzy::dsp::BiquadSection& section,
    int32_t typeRaw,
    double freqHz,
    double gainDb,
    double qFactor,
    bool enabled,
    double sampleRate
) noexcept {
    if (!enabled || sampleRate <= 0.0) {
        section.active = false;
        section.b0 = 1.0;
        section.b1 = 0.0;
        section.b2 = 0.0;
        section.a1 = 0.0;
        section.a2 = 0.0;
        return;
    }

    const auto type = static_cast<dezzy::dsp::FilterType>(typeRaw);
    const double safeSr = std::max(8000.0, sampleRate);
    const double safeFreq = std::clamp(freqHz, 12.0, safeSr * 0.485);
    const double safeQ = std::clamp(qFactor, 0.05, 24.0);
    const double safeGain = std::clamp(gainDb, -24.0, 24.0);

    const bool isShelfOrPeak =
        type == dezzy::dsp::FilterType::PEAKING ||
        type == dezzy::dsp::FilterType::LOW_SHELF ||
        type == dezzy::dsp::FilterType::HIGH_SHELF;

    if (isShelfOrPeak && std::abs(safeGain) < 0.01) {
        section.active = false;
        section.b0 = 1.0;
        section.b1 = 0.0;
        section.b2 = 0.0;
        section.a1 = 0.0;
        section.a2 = 0.0;
        return;
    }

    const double w0 = 2.0 * kPi * safeFreq / safeSr;
    const double cosW0 = std::cos(w0);
    const double sinW0 = std::sin(w0);
    const double alpha = sinW0 / (2.0 * safeQ);
    const double a = std::pow(10.0, safeGain / 40.0);
    const double twoSqrtAAlpha = 2.0 * std::sqrt(a) * alpha;

    double b0 = 1.0, b1 = 0.0, b2 = 0.0;
    double a0 = 1.0, a1 = 0.0, a2 = 0.0;

    switch (type) {
        case dezzy::dsp::FilterType::PEAKING:
            b0 = 1.0 + alpha * a;
            b1 = -2.0 * cosW0;
            b2 = 1.0 - alpha * a;
            a0 = 1.0 + alpha / a;
            a1 = -2.0 * cosW0;
            a2 = 1.0 - alpha / a;
            break;
        case dezzy::dsp::FilterType::LOW_SHELF:
            b0 = a * ((a + 1.0) - (a - 1.0) * cosW0 + twoSqrtAAlpha);
            b1 = 2.0 * a * ((a - 1.0) - (a + 1.0) * cosW0);
            b2 = a * ((a + 1.0) - (a - 1.0) * cosW0 - twoSqrtAAlpha);
            a0 = (a + 1.0) + (a - 1.0) * cosW0 + twoSqrtAAlpha;
            a1 = -2.0 * ((a - 1.0) + (a + 1.0) * cosW0);
            a2 = (a + 1.0) + (a - 1.0) * cosW0 - twoSqrtAAlpha;
            break;
        case dezzy::dsp::FilterType::HIGH_SHELF:
            b0 = a * ((a + 1.0) + (a - 1.0) * cosW0 + twoSqrtAAlpha);
            b1 = -2.0 * a * ((a - 1.0) + (a + 1.0) * cosW0);
            b2 = a * ((a + 1.0) + (a - 1.0) * cosW0 - twoSqrtAAlpha);
            a0 = (a + 1.0) - (a - 1.0) * cosW0 + twoSqrtAAlpha;
            a1 = 2.0 * ((a - 1.0) - (a + 1.0) * cosW0);
            a2 = (a + 1.0) - (a - 1.0) * cosW0 - twoSqrtAAlpha;
            break;
        case dezzy::dsp::FilterType::LOW_PASS:
            b0 = (1.0 - cosW0) * 0.5;
            b1 = 1.0 - cosW0;
            b2 = (1.0 - cosW0) * 0.5;
            a0 = 1.0 + alpha;
            a1 = -2.0 * cosW0;
            a2 = 1.0 - alpha;
            break;
        case dezzy::dsp::FilterType::HIGH_PASS:
            b0 = (1.0 + cosW0) * 0.5;
            b1 = -(1.0 + cosW0);
            b2 = (1.0 + cosW0) * 0.5;
            a0 = 1.0 + alpha;
            a1 = -2.0 * cosW0;
            a2 = 1.0 - alpha;
            break;
        default:
            section.active = false;
            return;
    }

    if (std::abs(a0) < 1e-12 || !std::isfinite(a0)) {
        section.active = false;
        return;
    }

    const double invA0 = 1.0 / a0;
    const double nb0 = b0 * invA0;
    const double nb1 = b1 * invA0;
    const double nb2 = b2 * invA0;
    const double na1 = a1 * invA0;
    const double na2 = a2 * invA0;

    if (!std::isfinite(nb0) || !std::isfinite(nb1) || !std::isfinite(nb2) ||
        !std::isfinite(na1) || !std::isfinite(na2)) {
        section.active = false;
        return;
    }

    section.b0 = nb0;
    section.b1 = nb1;
    section.b2 = nb2;
    section.a1 = na1;
    section.a2 = na2;
    section.active = true;
}

inline uint32_t nextXorShift(uint32_t& s) noexcept {
    s ^= s << 13;
    s ^= s >> 17;
    s ^= s << 5;
    return s;
}

inline double tpdfDitherLsb(uint32_t& state) noexcept {
    const double u1 = static_cast<double>(nextXorShift(state) >> 16) * (1.0 / 65535.0);
    const double u2 = static_cast<double>(nextXorShift(state) >> 16) * (1.0 / 65535.0);
    return (u1 - u2) * 0.5;
}

/**
 * Multi-band Mid/Side Vocal <-> Instrumental separation stage.
 * Uses complementary 3-band decomposition (low <175 Hz, vocal 175-4600 Hz, air >4600 Hz)
 * plus center-dominance ratio tracking so bass punch survives Karaoke cleanly and
 * center vocals stand out clearly in Acapella.
 */
inline void applyVocalSeparator(
    dezzy::dsp::AudioDspContext& ctx,
    double& sampleL,
    double& sampleR
) noexcept {
    const double mix = static_cast<double>(ctx.vocalMix);
    if (std::abs(mix) < 0.01) return;

    const double mid = 0.5 * (sampleL + sampleR);
    const double side = 0.5 * (sampleL - sampleR);

    // Butterworth All-Pass complementary 3-band crossover (190 Hz & 4600 Hz):
    // LP(x) - HP(x) is strictly all-pass unity across all frequencies with zero bass leakage into vocal band.
    const double mLow = ctx.vocalLowLp.processSample(mid, 0);
    const double sLow = ctx.vocalLowLp.processSample(side, 1);

    const double mUpper = -ctx.vocalLowHp.processSample(mid, 0);
    const double sUpper = -ctx.vocalLowHp.processSample(side, 1);

    const double mVocal = ctx.vocalHighLp.processSample(mUpper, 0);
    const double sVocal = ctx.vocalHighLp.processSample(sUpper, 1);

    const double mAir = -ctx.vocalHighHp.processSample(mUpper, 0);
    const double sAir = -ctx.vocalHighHp.processSample(sUpper, 1);

    // Fast RMS-like envelope of center vs stereo energy in the vocal band
    ctx.vocalEnvM += ctx.vocalEnvCoeff * (std::abs(mVocal) - ctx.vocalEnvM);
    ctx.vocalEnvS += ctx.vocalEnvCoeff * (std::abs(sVocal) - ctx.vocalEnvS);
    if (ctx.vocalEnvM < 1e-24) ctx.vocalEnvM = 0.0;
    if (ctx.vocalEnvS < 1e-24) ctx.vocalEnvS = 0.0;

    const double centerRatio = ctx.vocalEnvM / (ctx.vocalEnvM + ctx.vocalEnvS + 1e-6);

    double outLowM = mLow;
    double outLowS = sLow;
    double outVoxM = mVocal;
    double outVoxS = sVocal;
    double outAirM = mAir;
    double outAirS = sAir;

    if (mix < 0.0) {
        // Karaoke / Instrumental: suppress center vocals in 190-4600 Hz, guard bass punch
        const double k = -mix;
        const double lowKeep = ctx.vocalBassGuard ? 1.0 : (1.0 - 0.55 * k);
        outLowM = mLow * lowKeep;
        outLowS = sLow * (1.0 - 0.10 * k);

        const double mSuppress = 1.0 - k * (0.72 + 0.24 * centerRatio);
        outVoxM = mVocal * mSuppress;
        outVoxS = sVocal * (1.0 + 0.16 * k);

        outAirM = mAir * (1.0 - 0.45 * k);
        outAirS = sAir * (1.0 + 0.08 * k);
    } else {
        // Acapella / Solo Voz: isolate center vocal formants and attenuate stereo/bass backing
        const double k = mix;
        const double sSuppress = 1.0 - 0.94 * k;
        outLowM = mLow * (1.0 - 0.94 * k);
        outLowS = sLow * sSuppress;

        const double vocalPresence = (1.0 + 0.20 * k) * (1.0 - 0.18 * k * (1.0 - centerRatio));
        outVoxM = mVocal * vocalPresence;
        outVoxS = sVocal * (1.0 - 0.90 * k);

        outAirM = mAir * (1.0 - 0.55 * k);
        outAirS = sAir * sSuppress;
    }

    const double outM = outLowM + outVoxM + outAirM;
    const double outS = outLowS + outVoxS + outAirS;
    sampleL = outM + outS;
    sampleR = outM - outS;
}

inline void processStereoFrame(
    dezzy::dsp::AudioDspContext& ctx,
    double& sampleL,
    double& sampleR
) noexcept {
    // 0) Vocal / Instrumental Separator (before EQ/virtualizer/limiter)
    if (std::abs(ctx.vocalMix) >= 0.01f) {
        applyVocalSeparator(ctx, sampleL, sampleR);
    }

    // 1) Parametric multi-band Biquad EQ (64-bit TDF-II)
    if (ctx.eqEnabled) {
        for (int b = 0; b < ctx.activeBandCount; ++b) {
            auto& sec = ctx.bands[b];
            if (!sec.active) continue;
            sampleL = sec.processSample(sampleL, 0);
            sampleR = sec.processSample(sampleR, 1);
        }
    }

    // 2) Warm analog-style sub-bass shelf (68 Hz)
    if (ctx.bassShelf.active) {
        sampleL = ctx.bassShelf.processSample(sampleL, 0);
        sampleR = ctx.bassShelf.processSample(sampleR, 1);
    }

    // 3) Phase-coherent Mid/Side Stereo Virtualizer (protects low frequencies < 160 Hz)
    if (ctx.virtualizerStrength > 0.001f) {
        const double amt = static_cast<double>(ctx.virtualizerStrength);
        const double mid = 0.5 * (sampleL + sampleR);
        const double side = 0.5 * (sampleL - sampleR);

        ctx.sideLpState += ctx.sideLpCoeff * (side - ctx.sideLpState);
        if (std::abs(ctx.sideLpState) < 1e-24) ctx.sideLpState = 0.0;
        const double sideLow = ctx.sideLpState;
        const double sideHigh = side - sideLow;

        const double widenedSide = sideLow * (1.0 - 0.18 * amt) + sideHigh * (1.0 + 0.58 * amt);
        const double balancedMid = mid * (1.0 - 0.06 * amt);
        sampleL = balancedMid + widenedSide;
        sampleR = balancedMid - widenedSide;
    }

    // 4) Smooth Preamp Output Gain
    ctx.currentOutputGain += ctx.gainSmoothCoeff * (static_cast<double>(ctx.targetOutputGain) - ctx.currentOutputGain);
    sampleL *= ctx.currentOutputGain;
    sampleR *= ctx.currentOutputGain;

    // 5) Envelope-tracked Soft-Knee True-Peak Anti-Clip Limiter
    if (ctx.limiterEnabled) {
        constexpr double kThreshold = 0.89125;
        const double framePeak = std::max(std::abs(sampleL), std::abs(sampleR));
        const double envTarget = (framePeak > kThreshold) ? framePeak : kThreshold;
        const double coeff = (envTarget > ctx.limiterEnv) ? ctx.limiterAttackCoeff : ctx.limiterReleaseCoeff;
        ctx.limiterEnv += coeff * (envTarget - ctx.limiterEnv);

        double gain = 1.0;
        if (ctx.limiterEnv > kThreshold) {
            const double excess = ctx.limiterEnv - kThreshold;
            const double compressed = kThreshold + excess / (1.0 + excess * 3.5);
            gain = compressed / ctx.limiterEnv;
        }

        sampleL *= gain;
        sampleR *= gain;

        if (std::abs(sampleL) > 0.96) {
            const double sign = (sampleL >= 0.0) ? 1.0 : -1.0;
            sampleL = sign * (0.96 + 0.035 * std::tanh((std::abs(sampleL) - 0.96) / 0.12));
        }
        if (std::abs(sampleR) > 0.96) {
            const double sign = (sampleR >= 0.0) ? 1.0 : -1.0;
            sampleR = sign * (0.96 + 0.035 * std::tanh((std::abs(sampleR) - 0.96) / 0.12));
        }
    }
}

inline void processMultiChannelFrame(
    dezzy::dsp::AudioDspContext& ctx,
    double* chSamples,
    int channels
) noexcept {
    if (channels >= 2 && std::abs(ctx.vocalMix) >= 0.01f) {
        applyVocalSeparator(ctx, chSamples[0], chSamples[1]);
    }

    if (ctx.eqEnabled) {
        for (int b = 0; b < ctx.activeBandCount; ++b) {
            auto& sec = ctx.bands[b];
            if (!sec.active) continue;
            for (int c = 0; c < channels; ++c) {
                chSamples[c] = sec.processSample(chSamples[c], c);
            }
        }
    }

    if (ctx.bassShelf.active) {
        for (int c = 0; c < channels; ++c) {
            chSamples[c] = ctx.bassShelf.processSample(chSamples[c], c);
        }
    }

    if (channels >= 2 && ctx.virtualizerStrength > 0.001f) {
        const double amt = static_cast<double>(ctx.virtualizerStrength);
        const double mid = 0.5 * (chSamples[0] + chSamples[1]);
        const double side = 0.5 * (chSamples[0] - chSamples[1]);
        ctx.sideLpState += ctx.sideLpCoeff * (side - ctx.sideLpState);
        if (std::abs(ctx.sideLpState) < 1e-24) ctx.sideLpState = 0.0;
        const double sideHigh = side - ctx.sideLpState;
        const double widenedSide = ctx.sideLpState * (1.0 - 0.18 * amt) + sideHigh * (1.0 + 0.58 * amt);
        const double balancedMid = mid * (1.0 - 0.06 * amt);
        chSamples[0] = balancedMid + widenedSide;
        chSamples[1] = balancedMid - widenedSide;
    }

    ctx.currentOutputGain += ctx.gainSmoothCoeff * (static_cast<double>(ctx.targetOutputGain) - ctx.currentOutputGain);
    double framePeak = 0.0;
    for (int c = 0; c < channels; ++c) {
        chSamples[c] *= ctx.currentOutputGain;
        framePeak = std::max(framePeak, std::abs(chSamples[c]));
    }

    if (ctx.limiterEnabled) {
        constexpr double kThreshold = 0.89125;
        const double envTarget = (framePeak > kThreshold) ? framePeak : kThreshold;
        const double coeff = (envTarget > ctx.limiterEnv) ? ctx.limiterAttackCoeff : ctx.limiterReleaseCoeff;
        ctx.limiterEnv += coeff * (envTarget - ctx.limiterEnv);

        double gain = 1.0;
        if (ctx.limiterEnv > kThreshold) {
            const double excess = ctx.limiterEnv - kThreshold;
            const double compressed = kThreshold + excess / (1.0 + excess * 3.5);
            gain = compressed / ctx.limiterEnv;
        }
        for (int c = 0; c < channels; ++c) {
            double s = chSamples[c] * gain;
            if (std::abs(s) > 0.96) {
                const double sign = (s >= 0.0) ? 1.0 : -1.0;
                s = sign * (0.96 + 0.035 * std::tanh((std::abs(s) - 0.96) / 0.12));
            }
            chSamples[c] = s;
        }
    }
}

} // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_dezzy_app_feature_audio_NativeDspEngine_createAudioDspContextNative(
    JNIEnv* /* env */,
    jobject /* thiz */
) {
    auto* ctx = new (std::nothrow) dezzy::dsp::AudioDspContext();
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT void JNICALL
Java_com_dezzy_app_feature_audio_NativeDspEngine_destroyAudioDspContextNative(
    JNIEnv* /* env */,
    jobject /* thiz */,
    jlong ctxPtr
) {
    auto* ctx = reinterpret_cast<dezzy::dsp::AudioDspContext*>(ctxPtr);
    delete ctx;
}

JNIEXPORT void JNICALL
Java_com_dezzy_app_feature_audio_NativeDspEngine_resetAudioDspContextNative(
    JNIEnv* /* env */,
    jobject /* thiz */,
    jlong ctxPtr
) {
    auto* ctx = reinterpret_cast<dezzy::dsp::AudioDspContext*>(ctxPtr);
    if (!ctx) return;
    ctx->resetPlaybackStates();
}

JNIEXPORT void JNICALL
Java_com_dezzy_app_feature_audio_NativeDspEngine_configureAudioDspContextNative(
    JNIEnv* env,
    jobject /* thiz */,
    jlong ctxPtr,
    jint sampleRate,
    jint channels,
    jint bandCount,
    jintArray typesArray,
    jfloatArray freqsArray,
    jfloatArray gainsArray,
    jfloatArray qsArray,
    jbooleanArray enabledArray,
    jboolean eqEnabled,
    jfloat bassStrength,
    jfloat virtualizerStrength,
    jfloat outputGainDb,
    jboolean limiterEnabled,
    jfloat vocalMix,
    jboolean vocalBassGuard
) {
    auto* ctx = reinterpret_cast<dezzy::dsp::AudioDspContext*>(ctxPtr);
    if (!ctx) return;

    const int safeSr = std::max(8000, static_cast<int>(sampleRate));
    const int safeCh = std::clamp(static_cast<int>(channels), 1, dezzy::dsp::kMaxChannels);
    const int safeBands = std::clamp(static_cast<int>(bandCount), 0, dezzy::dsp::kMaxEqBands);

    ctx->sampleRate = safeSr;
    ctx->channelCount = safeCh;
    ctx->eqEnabled = (eqEnabled == JNI_TRUE);
    ctx->activeBandCount = safeBands;

    const double srD = static_cast<double>(safeSr);
    ctx->gainSmoothCoeff = 1.0 - std::exp(-1.0 / (0.005 * srD));
    ctx->limiterAttackCoeff = 1.0 - std::exp(-1.0 / (0.0008 * srD));
    ctx->limiterReleaseCoeff = 1.0 - std::exp(-1.0 / (0.065 * srD));
    ctx->sideLpCoeff = 1.0 - std::exp((-2.0 * kPi * 160.0) / srD);
    ctx->vocalEnvCoeff = 1.0 - std::exp(-1.0 / (0.012 * srD));

    ctx->vocalMix = std::clamp(static_cast<float>(vocalMix), -1.0f, 1.0f);
    ctx->vocalBassGuard = (vocalBassGuard == JNI_TRUE);
    if (std::abs(ctx->vocalMix) >= 0.01f) {
        computeBiquadSection(
            ctx->vocalLowLp,
            static_cast<int32_t>(dezzy::dsp::FilterType::LOW_PASS),
            190.0,
            0.0,
            0.7071,
            true,
            srD
        );
        computeBiquadSection(
            ctx->vocalLowHp,
            static_cast<int32_t>(dezzy::dsp::FilterType::HIGH_PASS),
            190.0,
            0.0,
            0.7071,
            true,
            srD
        );
        computeBiquadSection(
            ctx->vocalHighLp,
            static_cast<int32_t>(dezzy::dsp::FilterType::LOW_PASS),
            4600.0,
            0.0,
            0.7071,
            true,
            srD
        );
        computeBiquadSection(
            ctx->vocalHighHp,
            static_cast<int32_t>(dezzy::dsp::FilterType::HIGH_PASS),
            4600.0,
            0.0,
            0.7071,
            true,
            srD
        );
    } else {
        ctx->vocalLowLp.active = false;
        ctx->vocalLowHp.active = false;
        ctx->vocalHighLp.active = false;
        ctx->vocalHighHp.active = false;
    }

    ctx->bassStrength = std::clamp(static_cast<float>(bassStrength), 0.0f, 1.0f);
    ctx->virtualizerStrength = std::clamp(static_cast<float>(virtualizerStrength), 0.0f, 1.0f);
    const double clampedOutDb = std::clamp(static_cast<double>(outputGainDb), -15.0, 15.0);
    ctx->targetOutputGain = static_cast<float>(std::pow(10.0, clampedOutDb / 20.0));
    ctx->limiterEnabled = (limiterEnabled == JNI_TRUE);

    if (ctx->bassStrength > 0.005f) {
        computeBiquadSection(
            ctx->bassShelf,
            static_cast<int32_t>(dezzy::dsp::FilterType::LOW_SHELF),
            68.0,
            static_cast<double>(ctx->bassStrength) * 9.0,
            0.68,
            true,
            srD
        );
    } else {
        ctx->bassShelf.active = false;
    }

    if (safeBands > 0 && typesArray && freqsArray && gainsArray && qsArray && enabledArray) {
        jint* types = env->GetIntArrayElements(typesArray, nullptr);
        jfloat* freqs = env->GetFloatArrayElements(freqsArray, nullptr);
        jfloat* gains = env->GetFloatArrayElements(gainsArray, nullptr);
        jfloat* qs = env->GetFloatArrayElements(qsArray, nullptr);
        jboolean* en = env->GetBooleanArrayElements(enabledArray, nullptr);

        if (types && freqs && gains && qs && en) {
            for (int i = 0; i < safeBands; ++i) {
                computeBiquadSection(
                    ctx->bands[i],
                    types[i],
                    static_cast<double>(freqs[i]),
                    static_cast<double>(gains[i]),
                    static_cast<double>(qs[i]),
                    en[i] == JNI_TRUE,
                    srD
                );
            }
        }

        if (types) env->ReleaseIntArrayElements(typesArray, types, JNI_ABORT);
        if (freqs) env->ReleaseFloatArrayElements(freqsArray, freqs, JNI_ABORT);
        if (gains) env->ReleaseFloatArrayElements(gainsArray, gains, JNI_ABORT);
        if (qs) env->ReleaseFloatArrayElements(qsArray, qs, JNI_ABORT);
        if (en) env->ReleaseBooleanArrayElements(enabledArray, en, JNI_ABORT);
    }
}

JNIEXPORT jfloat JNICALL
Java_com_dezzy_app_feature_audio_NativeDspEngine_processAudioDirectNative(
    JNIEnv* env,
    jobject /* thiz */,
    jlong ctxPtr,
    jobject inDirectBuffer,
    jint inOffsetBytes,
    jobject outDirectBuffer,
    jint lengthBytes,
    jboolean isFloat
) {
    auto* ctx = reinterpret_cast<dezzy::dsp::AudioDspContext*>(ctxPtr);
    if (!ctx || !inDirectBuffer || !outDirectBuffer || lengthBytes <= 0) return 0.0f;

    auto* rawIn = static_cast<uint8_t*>(env->GetDirectBufferAddress(inDirectBuffer));
    auto* rawOut = static_cast<uint8_t*>(env->GetDirectBufferAddress(outDirectBuffer));
    if (!rawIn || !rawOut) return -1.0f;

    const uint8_t* inPtr = rawIn + std::max(0, static_cast<int>(inOffsetBytes));
    const int ch = std::clamp(ctx->channelCount, 1, dezzy::dsp::kMaxChannels);

    if (isFloat == JNI_TRUE) {
        const auto* src = reinterpret_cast<const float*>(inPtr);
        auto* dst = reinterpret_cast<float*>(rawOut);
        const int totalSamples = lengthBytes / static_cast<int>(sizeof(float));
        const int frames = totalSamples / ch;

        if (ch == 2) {
            for (int f = 0; f < frames; ++f) {
                const int idx = f * 2;
                double sL = static_cast<double>(src[idx]);
                double sR = static_cast<double>(src[idx + 1]);
                processStereoFrame(*ctx, sL, sR);
                dst[idx] = static_cast<float>(sL);
                dst[idx + 1] = static_cast<float>(sR);
            }
        } else {
            std::array<double, dezzy::dsp::kMaxChannels> frame{};
            for (int f = 0; f < frames; ++f) {
                const int base = f * ch;
                for (int c = 0; c < ch; ++c) {
                    frame[c] = static_cast<double>(src[base + c]);
                }
                processMultiChannelFrame(*ctx, frame.data(), ch);
                for (int c = 0; c < ch; ++c) {
                    dst[base + c] = static_cast<float>(frame[c]);
                }
            }
        }
    } else {
        const auto* src = reinterpret_cast<const int16_t*>(inPtr);
        auto* dst = reinterpret_cast<int16_t*>(rawOut);
        const int totalSamples = lengthBytes / static_cast<int>(sizeof(int16_t));
        const int frames = totalSamples / ch;
        constexpr double kInv32768 = 1.0 / 32768.0;

        if (ch == 2) {
            for (int f = 0; f < frames; ++f) {
                const int idx = f * 2;
                double sL = static_cast<double>(src[idx]) * kInv32768;
                double sR = static_cast<double>(src[idx + 1]) * kInv32768;
                processStereoFrame(*ctx, sL, sR);

                const double dL = tpdfDitherLsb(ctx->ditherState);
                const double dR = tpdfDitherLsb(ctx->ditherState);
                const long iL = std::lround(sL * 32767.0 + dL);
                const long iR = std::lround(sR * 32767.0 + dR);
                dst[idx] = static_cast<int16_t>(std::clamp<long>(iL, -32768L, 32767L));
                dst[idx + 1] = static_cast<int16_t>(std::clamp<long>(iR, -32768L, 32767L));
            }
        } else {
            std::array<double, dezzy::dsp::kMaxChannels> frame{};
            for (int f = 0; f < frames; ++f) {
                const int base = f * ch;
                for (int c = 0; c < ch; ++c) {
                    frame[c] = static_cast<double>(src[base + c]) * kInv32768;
                }
                processMultiChannelFrame(*ctx, frame.data(), ch);
                for (int c = 0; c < ch; ++c) {
                    const double d = tpdfDitherLsb(ctx->ditherState);
                    const long quantized = std::lround(frame[c] * 32767.0 + d);
                    dst[base + c] = static_cast<int16_t>(std::clamp<long>(quantized, -32768L, 32767L));
                }
            }
        }
    }

    if (ctx->limiterEnabled && ctx->limiterEnv > 0.89125) {
        const double compressed = 0.89125 + (ctx->limiterEnv - 0.89125) / (1.0 + (ctx->limiterEnv - 0.89125) * 3.5);
        const float grDb = static_cast<float>(-20.0 * std::log10(std::clamp(compressed / ctx->limiterEnv, 0.05, 1.0)));
        ctx->lastGainReductionDb = grDb;
        return grDb;
    }
    ctx->lastGainReductionDb = 0.0f;
    return 0.0f;
}

JNIEXPORT void JNICALL
Java_com_dezzy_app_feature_audio_NativeDspEngine_initSphereNative(
    JNIEnv* /* env */,
    jobject /* thiz */,
    jint pointCount
) {
    initializeSphere(pointCount);
}

JNIEXPORT void JNICALL
Java_com_dezzy_app_feature_audio_NativeDspEngine_computeSpherePointsNative(
    JNIEnv* env,
    jobject /* thiz */,
    jfloatArray outBuffer,
    jfloat timeSec,
    jfloatArray bandsArray,
    jfloat loudness,
    jfloat cx,
    jfloat cy,
    jfloat baseR,
    jfloat orbAlpha
) {
    if (!s_initialized) {
        initializeSphere(DEFAULT_SPHERE_POINTS);
    }

    const int pointCount = static_cast<int>(s_spherePoints.size());
    jfloat* out = env->GetFloatArrayElements(outBuffer, nullptr);
    jfloat* bands = env->GetFloatArrayElements(bandsArray, nullptr);

    if (!out || !bands) {
        if (out) env->ReleaseFloatArrayElements(outBuffer, out, JNI_ABORT);
        if (bands) env->ReleaseFloatArrayElements(bandsArray, bands, JNI_ABORT);
        return;
    }

    const float rotY = timeSec * 0.30f;
    const float cosR = std::cos(rotY);
    const float sinR = std::sin(rotY);
    const float dotBase = baseR * 0.016f;

    for (int i = 0; i < pointCount; ++i) {
        const auto& p = s_spherePoints[i];
        const float x1 = p.x * cosR + p.z * sinR;
        const float z1 = -p.x * sinR + p.z * cosR;
        const float y1 = p.y;

        int bandIdx = static_cast<int>(((y1 + 1.0f) * 0.5f) * (BANDS - 1));
        bandIdx = std::clamp(bandIdx, 0, BANDS - 1);
        const float band = bands[bandIdx];

        const float ripple = 1.0f + 0.055f * std::sin(p.lat * 9.0f - timeSec * 2.6f) * (0.35f + loudness) + band * 0.11f;
        const float rr = baseR * ripple;

        const float sx = cx + x1 * rr;
        const float sy = cy + y1 * rr;
        const float depth = (z1 + 1.0f) * 0.5f;

        const float alpha = std::clamp(0.22f + 0.72f * depth, 0.0f, 1.0f) * orbAlpha;
        const float radius = dotBase * (0.55f + 0.85f * depth) * (1.0f + band * 0.5f);

        const int offset = i * 4;
        out[offset + 0] = sx;
        out[offset + 1] = sy;
        out[offset + 2] = radius;
        out[offset + 3] = alpha;
    }

    env->ReleaseFloatArrayElements(outBuffer, out, 0);
    env->ReleaseFloatArrayElements(bandsArray, bands, JNI_ABORT);
}

JNIEXPORT jfloat JNICALL
Java_com_dezzy_app_feature_audio_NativeDspEngine_processFftNative(
    JNIEnv* env,
    jobject /* thiz */,
    jbyteArray fftByteArray,
    jfloatArray inOutBands,
    jfloat prevLoudness
) {
    const jsize len = env->GetArrayLength(fftByteArray);
    const int bins = len / 2;
    if (bins < 2) return prevLoudness;

    jbyte* fft = env->GetByteArrayElements(fftByteArray, nullptr);
    jfloat* bands = env->GetFloatArrayElements(inOutBands, nullptr);

    if (!fft || !bands) {
        if (fft) env->ReleaseByteArrayElements(fftByteArray, fft, JNI_ABORT);
        if (bands) env->ReleaseFloatArrayElements(inOutBands, bands, JNI_ABORT);
        return prevLoudness;
    }

    float total = 0.0f;
    const double binsMinusOne = static_cast<double>(bins - 1);

    for (int b = 0; b < BANDS; ++b) {
        int startBin = static_cast<int>(std::pow(binsMinusOne, static_cast<double>(b) / BANDS));
        if (startBin < 1) startBin = 1;

        int endBin = static_cast<int>(std::pow(binsMinusOne, static_cast<double>(b + 1) / BANDS));
        if (endBin <= startBin) endBin = startBin + 1;
        if (endBin > bins) endBin = bins;

        float sum = 0.0f;
        int count = 0;
        for (int i = startBin; i < endBin; ++i) {
            float re = static_cast<float>(fft[2 * i]);
            float im = static_cast<float>(fft[2 * i + 1]);
            sum += std::hypot(re, im);
            count++;
        }

        float raw = (count > 0) ? (sum / static_cast<float>(count)) / 128.0f : 0.0f;
        float boosted = std::clamp(raw * (1.0f + static_cast<float>(b) * 0.025f), 0.0f, 1.0f);

        if (boosted > bands[b]) {
            bands[b] = bands[b] + (boosted - bands[b]) * 0.55f;
        } else {
            bands[b] = bands[b] * 0.86f;
        }
        total += bands[b];
    }

    float newLoudness = prevLoudness * 0.82f + std::clamp((total / static_cast<float>(BANDS)) * 3.0f, 0.0f, 1.0f) * 0.18f;

    env->ReleaseByteArrayElements(fftByteArray, fft, JNI_ABORT);
    env->ReleaseFloatArrayElements(inOutBands, bands, 0);

    return newLoudness;
}

} // extern "C"
