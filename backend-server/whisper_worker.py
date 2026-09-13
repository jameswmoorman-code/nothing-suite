#!/usr/bin/env python3
"""
Local speech-to-text worker for the Nothing Suite backend.

Reads utterances from stdin as binary frames:
    uint32 id | uint32 byte_length | int16 PCM mono @ 8 kHz
Writes one JSON object per line to stdout:
    {"event":"ready"} once the model is loaded
    {"event":"result","id":N,"text":"..."} per utterance
    {"event":"error","message":"..."}

Install once:  pip install -r requirements.txt
First run downloads the model (~150 MB for base.en) and caches it.
"""
import argparse
import json
import struct
import sys

import numpy as np

SRC_RATE = 8000
DST_RATE = 16000


def emit(obj):
    sys.stdout.write(json.dumps(obj) + "\n")
    sys.stdout.flush()


def read_exact(stream, n):
    buf = b""
    while len(buf) < n:
        chunk = stream.read(n - len(buf))
        if not chunk:
            return None
        buf += chunk
    return buf


def upsample_8k_to_16k(pcm16: bytes) -> np.ndarray:
    x = np.frombuffer(pcm16, dtype=np.int16).astype(np.float32) / 32768.0
    if x.size == 0:
        return x
    idx = np.linspace(0, x.size - 1, x.size * DST_RATE // SRC_RATE)
    return np.interp(idx, np.arange(x.size), x).astype(np.float32)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", default="base.en")
    args = ap.parse_args()

    try:
        from faster_whisper import WhisperModel
    except ImportError:
        emit({"event": "error", "message": "faster-whisper not installed: pip install -r requirements.txt"})
        sys.exit(1)

    try:
        model = WhisperModel(args.model, device="auto", compute_type="int8")
    except Exception as e:  # noqa: BLE001
        emit({"event": "error", "message": f"could not load model {args.model}: {e}"})
        sys.exit(1)

    emit({"event": "ready"})
    stdin = sys.stdin.buffer

    while True:
        header = read_exact(stdin, 8)
        if header is None:
            break
        utt_id, length = struct.unpack("<II", header)
        payload = read_exact(stdin, length)
        if payload is None:
            break

        audio = upsample_8k_to_16k(payload)
        if audio.size < DST_RATE // 4:  # < 0.25 s, skip
            continue
        try:
            segments, _ = model.transcribe(
                audio,
                language="en",
                beam_size=1,
                vad_filter=True,
                condition_on_previous_text=False,
            )
            text = " ".join(s.text.strip() for s in segments).strip()
            emit({"event": "result", "id": utt_id, "text": text})
        except Exception as e:  # noqa: BLE001
            emit({"event": "error", "message": str(e)})


if __name__ == "__main__":
    main()
