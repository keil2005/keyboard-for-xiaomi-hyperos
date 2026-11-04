#!/usr/bin/env python3
"""合成机械键盘按键音效（青轴 click / 低沉 thock / 柔和），输出 16bit 单声道 WAV 到 res/raw。

原理：机械键盘 click 的声学特征 = 瞬时噪声脉冲（键帽触底碰撞）+ 指数衰减 + 壳体共振。
用 numpy 合成，无需外部音频采样，无版权问题。
"""
import numpy as np
import wave
import os

SR = 44100  # 采样率

def save_wav(path, samples):
    samples = np.clip(samples, -1.0, 1.0)
    pcm = (samples * 32767).astype(np.int16)
    with wave.open(path, 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())

def exp_decay(n, tau):
    """指数衰减包络，tau 越小衰减越快"""
    t = np.arange(n) / SR
    return np.exp(-t / tau)

def click_blue(length=0.06):
    """机械青轴 click：高频噪声脉冲 + 清脆共振，快速衰减"""
    n = int(SR * length)
    t = np.arange(n) / SR

    # 1. 触发瞬间的白噪声脉冲（键帽触底）
    noise = np.random.randn(n)

    # 2. 清脆共振：约 3.2kHz + 4.8kHz 双频（青轴 click 的"咔"）
    reso = 0.5 * np.sin(2 * np.pi * 3200 * t) + 0.5 * np.sin(2 * np.pi * 4800 * t)

    # 混合：噪声主导开头，共振主导后段
    # 噪声快速衰减（tau~4ms），共振稍慢（tau~14ms）
    env_noise = exp_decay(n, 0.004)
    env_reso = exp_decay(n, 0.014)

    sig = 0.55 * noise * env_noise + 0.45 * reso * env_reso

    # 整体再叠一个极快的主衰减，让声音干脆
    sig *= exp_decay(n, 0.018)

    # 前 1ms 加一点冲击（attack 瞬态）
    attack = int(SR * 0.001)
    sig[:attack] *= np.linspace(0, 1, attack)

    return sig

def thock(length=0.09):
    """低沉 thock：低频厚实 + 短促，带壳体低频共振"""
    n = int(SR * length)
    t = np.arange(n) / SR

    # 低频噪声（低通后的噪声更"厚"）—— 用累积平均近似低通
    noise = np.random.randn(n)
    # 简单低通：boxcar 平滑
    k = int(SR * 0.002)
    kernel = np.ones(k) / k
    low_noise = np.convolve(noise, kernel, mode='same')

    # 低频共振：180Hz + 350Hz
    reso = 0.6 * np.sin(2 * np.pi * 180 * t) + 0.4 * np.sin(2 * np.pi * 350 * t)

    env_noise = exp_decay(n, 0.010)
    env_reso = exp_decay(n, 0.025)

    sig = 0.6 * low_noise * env_noise + 0.4 * reso * env_reso
    sig *= exp_decay(n, 0.030)

    attack = int(SR * 0.0015)
    sig[:attack] *= np.linspace(0, 1, attack)

    return sig

def soft(length=0.05):
    """柔和轻声：轻微短音，低频、音量小"""
    n = int(SR * length)
    t = np.arange(n) / SR

    noise = np.random.randn(n)
    k = int(SR * 0.003)
    kernel = np.ones(k) / k
    low_noise = np.convolve(noise, kernel, mode='same')

    reso = 0.5 * np.sin(2 * np.pi * 500 * t) + 0.5 * np.sin(2 * np.pi * 800 * t)

    sig = 0.5 * low_noise * exp_decay(n, 0.008) + 0.5 * reso * exp_decay(n, 0.015)
    sig *= exp_decay(n, 0.020)
    sig *= 0.6  # 柔和音量更小

    attack = int(SR * 0.001)
    sig[:attack] *= np.linspace(0, 1, attack)

    return sig

def main():
    out_dir = os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main', 'res', 'raw')
    out_dir = os.path.abspath(out_dir)
    os.makedirs(out_dir, exist_ok=True)

    save_wav(os.path.join(out_dir, 'key_click.wav'), click_blue())
    save_wav(os.path.join(out_dir, 'key_thock.wav'), thock())
    save_wav(os.path.join(out_dir, 'key_soft.wav'), soft())
    print("已生成:", out_dir)
    for f in ('key_click.wav', 'key_thock.wav', 'key_soft.wav'):
        p = os.path.join(out_dir, f)
        print(f"  {f}  {os.path.getsize(p)} bytes")

if __name__ == '__main__':
    main()
