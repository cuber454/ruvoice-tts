"""Эталон звука для SileroModelsTest (app/src/androidTest/assets/golden_audio.f32) из той модели, что лежит в сборке full:
tts_mel.ptl и head.ptl (lite) и backbone.pte (ExecuTorch) из app/src/full/assets/silero. Нужен после замены модели
full (с 1.0 — пак ru_voices). Запуск: ../venv-et/bin/python -I tools/golden_audio.py"""
import os
import torch
from torch.jit.mobile import _load_for_lite_interpreter
from executorch.runtime import Runtime

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
MODEL = os.path.join(ROOT, 'app/src/full/assets/silero')

# символы — из пакета v5_5_ru, как в export_silero.tts_args; голос 4 и вход — как в тесте
pk = torch.package.PackageImporter(os.path.join(HERE, 'v5_5_ru.pt')).load_pickle('tts_models', 'model').packages[0]
seq, _ = pk.preprocess_tacotron('прив+ет, м+ир.'); seq = seq.unsqueeze(0); n = seq.shape[1]
args = (seq, torch.LongTensor([4]), 48000, None, torch.ones(1, n), torch.ones(1, n), None, None, 'cpu', -1, False,
        torch.zeros(1, n, dtype=torch.long), None)
with torch.no_grad():
    mel, _ = _load_for_lite_interpreter(os.path.join(MODEL, 'tts_mel.ptl'))(*args)
    hid = Runtime.get().load_program(os.path.join(MODEL, 'backbone.pte')).load_method('forward').execute([mel.contiguous()])[0]
    audio = _load_for_lite_interpreter(os.path.join(MODEL, 'head.ptl'))(hid, 48000, 0., True).flatten()
audio.numpy().astype('<f4').tofile(os.path.join(ROOT, 'app/src/androidTest/assets/golden_audio.f32'))
print('golden_audio.f32:', audio.numel(), 'сэмплов')
