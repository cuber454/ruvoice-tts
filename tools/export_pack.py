"""Пак голосов Silero v5 для RuVoice (спека 2026-09-18):
dist/ruvoice-pack-<id>.zip (pack.json + tts_mel.ptl + backbone.pte + head.ptl); для cis_ru ещё
app/src/test/resources/golden_pack.json (pack.json и ids golden-фразы для JVM-теста).
Пак не на русском (LANGS) несёт ещё accentor.ptl — акцентор своего языка — и stress.txt, поправки ударений
«слово сл+ово» из exceptions того же акцентора; --stress подменяет stress.txt своим файлом (и у русских паков).
Запуск: ../venv-et/bin/python tools/export_pack.py cis_ru | ru | cis_be [--stress путь]
(модель и акцентор для id скачать заранее в tools/, см. source в PACKS и LANGS)"""
import argparse, json, os, tempfile, zipfile
from typing import List, Tuple
import torch
from torch.jit.mobile import _load_for_lite_interpreter
from silero_export import split_tts, export_backbone, verify_backbone

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
DIST = os.path.join(ROOT, 'dist')
GOLDEN = os.path.join(ROOT, 'app/src/test/resources/golden_pack.json')
FORMAT = 2   # Packs.FORMAT в приложении: три файла моделей; число аргументов forward задаёт types в pack.json
# пак не на русском: 1.0.1 поля lang не знает и читал бы его русским слоем, поэтому формат другой — старое
# приложение такой пак отвергает. В pack.json ещё lang и default (голос языка по умолчанию = golden-голос);
# у русских паков этих полей нет и формат прежний, чтобы их принимали и старые приложения
FORMAT_LANG = 3
# id: (lang, акцентор языка) — accentor-<lang>.pt из pip-пакета silero-stress (MIT, silero_stress/data/) скопировать в tools/
LANGS = {'cis_be': ('be', 'accentor-bel.pt')}
PACKS = {
    # id: (файл модели, фильтр голосов, types, title, license, source, golden-голос)
    'cis_ru': ('v5_cis_base_nostress.pt', lambda s: s.startswith('ru_'), False, 'Русские голоса Silero v5 CIS — экспериментально', 'MIT',
               'https://models.silero.ai/models/tts/ru/v5_cis_base_nostress.pt', 'ru_alexandr'),
    'ru': ('v5_5_ru.pt', lambda s: True, True, 'Штатные голоса Silero v5.5', 'CC BY-NC-SA 4.0',
           'https://models.silero.ai/models/tts/ru/v5_5_ru.pt', 'xenia'),
    'cis_be': ('v5_cis_base_nostress.pt', lambda s: s.startswith('bel_'), False, 'Белорусские голоса Silero v5 CIS', 'MIT',
               'https://models.silero.ai/models/tts/ru/v5_cis_base_nostress.pt', 'bel_larisa'),
}
TEXT = 'прив+ет, м+ир.'


def tts_args(pk, text, sr=48000):
    """Тензоры для forward: seq, sr, rates, pitches; speaker, type_ids и focus_mask подставляет вызывающий."""
    seq, _ = pk.preprocess_tacotron(text); seq = seq.unsqueeze(0); n = seq.shape[1]
    return seq, sr, torch.ones(1, n), torch.ones(1, n)


def forward(model, pk, text, speaker_id, types, sr=48000):
    """11 аргументов forward (как у v5_ru) или 13 (v5_5_ru: type_ids нули, focus_mask None)."""
    seq, sr, rates, pitches = tts_args(pk, text, sr)
    args = [seq, torch.LongTensor([speaker_id]), sr, None, rates, pitches, None, None, 'cpu', -1, False]
    if types: args += [torch.zeros(1, seq.shape[1], dtype=torch.long), None]
    return model(*args)


class Accentor2(torch.nn.Module):
    """Акцентор silero-stress для bel/ukr отдаёт (logits, None), а приложение берёт out[1] тензором
    (у русского там логиты «ё»): вторым выходом нули [n, 1] — «ё» эти акценторы не ставят."""
    def __init__(self, m):
        super().__init__()
        self.m = m

    def forward(self, words: List[str]) -> Tuple[torch.Tensor, torch.Tensor]:
        logits = self.m(words)[0]
        return logits, torch.zeros(logits.size(0), 1)


def export_accentor(pt, out_path):
    """accentor-<lang>.pt → accentor.ptl. Эмбеддинги n-грамм в пакете int8 — деквантуем, как load_accentor
    в silero-stress. Проверка: lite против исходной модели на 50 словах из её exceptions, логиты один в один."""
    a = torch.package.PackageImporter(pt).load_pickle('accentor_models', 'accentor').accentor
    m = a.model
    m.embedding.weight.data = m.scale * (m.embedding.weight.data.clone() - m.zero_point)
    torch.jit.script(Accentor2(m))._save_for_lite_interpreter(out_path)
    words = sorted(a.exceptions)[::len(a.exceptions) // 50][:50]
    lite = _load_for_lite_interpreter(out_path)
    with torch.no_grad():
        ls, ly = lite(words); rs, _ = m(words)
    assert (ls - rs).abs().max().item() == 0 and ly.shape == (len(words), 1), 'accentor mismatch'
    return a.exceptions, lite


def exceptions_stress(exceptions, lite, path):
    """stress.txt из exceptions акцентора: словарные ударения silero-stress (слово → (позиция «+», позиция «ё»;
    у bel «ё» всегда -1)). Python-акцентор ставит их сам, приложению достаётся только модель — берём слова, где
    lite-модель ошибается, выбирая гласную как BelStress.kt: первый максимум среди гласных, но не дальше выходов
    модели. Апостроф BelNormalizer убирает до ударений — убираем и здесь, иначе слово не найдётся."""
    v = 'аеёіоуыэюя'   # BelStress.vowels
    words = {w.replace('’', ''): s - w[:s].count('’') for w, (s, _) in exceptions.items()}
    assert len(words) == len(exceptions), 'слова совпали без апострофа'
    order = sorted(words)
    with torch.no_grad():
        logits = lite(order)[0].tolist()
    out = []
    for w, row in zip(order, logits):
        at = [i for i, c in enumerate(w) if c in v]
        k = max(range(min(len(at), len(row))), key=row.__getitem__)
        if at[k] != words[w]: out.append(f'{w} {w[:words[w]]}+{w[words[w]:]}\n')
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        f.write('# исключения акцентора silero-stress (bel, MIT): слова, где модель ставит ударение иначе\n')
        f.writelines(out)
    print('stress.txt из exceptions:', len(out), 'из', len(words))


def check_stress(path):
    """stress.txt: UTF-8, строки «слово сл+ово» (DictLines.parseStress), «#» — комментарий."""
    with open(path, encoding='utf-8') as f:
        for i, line in enumerate(f, 1):
            p = line.split()
            assert not p or p[0].startswith('#') or (len(p) == 2 and '+' in p[1] and p[1].replace('+', '') == p[0]), f'{path}:{i}: {line!r}'


if __name__ == '__main__':
    ap = argparse.ArgumentParser()
    ap.add_argument('pack_id', nargs='?', default='cis_ru', choices=PACKS)
    ap.add_argument('--stress', help='stress.txt в пак (поправки ударений) вместо собранного из exceptions')
    args = ap.parse_args()
    pack_id = args.pack_id
    pt, keep, types, title, lic, source, golden_speaker = PACKS[pack_id]
    PT = os.path.join(HERE, pt)
    big = torch.package.PackageImporter(PT).load_pickle('tts_models', 'model')
    pk = big.packages[0]
    assert len(big.packages) == 1 and len(pk.models) == 1, 'ожидалась одна модель'
    assert (pk.accentor is None) == (not types), 'акцентор не соответствует types'
    tts = pk.models[0]
    speakers = {s: i for s, i in pk.speaker_to_ids[0].items() if keep(s)}
    assert len(speakers) == {'cis_ru': 29, 'ru': 5, 'cis_be': 3}[pack_id], len(speakers)
    if args.stress: check_stress(args.stress)
    pack = {
        'format': FORMAT, 'id': pack_id, 'title': title, 'license': lic,
        'source': source,
        'symbols': pk.symbols, 'symbol_to_id': pk.symbol_to_id, 'sos': pk.sos_token, 'eos': pk.eos_token,
        'alphabet': ''.join(pk.alphabet), 'types': types, 'speakers': speakers,
    }
    if pack_id in LANGS: pack.update(format=FORMAT_LANG, lang=LANGS[pack_id][0], default=golden_speaker)
    sid = speakers[golden_speaker]
    with torch.no_grad():  # эталоны до хирургии графа
        ref, ref_durs = forward(tts, pk, TEXT, sid, types)
        ref24, _ = forward(tts, pk, TEXT, sid, types, 24000)
    os.makedirs(DIST, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        mel_p, head_p, bb_p = (os.path.join(tmp, n) for n in ('tts_mel.ptl', 'head.ptl', 'backbone.pte'))
        split_tts(tts, mel_p, head_p)
        lite_m = _load_for_lite_interpreter(mel_p); lite_head = _load_for_lite_interpreter(head_p)
        with torch.no_grad():
            mel, durs = forward(lite_m, pk, TEXT, sid, types)
            ref_vocoder = tts.vocoder.backbone(mel)
            a = lite_head(ref_vocoder, 48000, 0., True); a24 = lite_head(ref_vocoder, 24000, 0., True)
        assert (a - ref).abs().max().item() == 0 and (a24 - ref24).abs().max().item() == 0, 'tts_mel+head mismatch'
        assert torch.equal(durs, ref_durs), 'durs mismatch'
        export_backbone(tts.vocoder.backbone.state_dict(), mel.contiguous(), bb_p)
        d = verify_backbone(bb_p, head_p, mel.contiguous(), ref)
        assert d < 1e-3, f'pte mismatch {d}'
        files = [('tts_mel.ptl', mel_p), ('backbone.pte', bb_p), ('head.ptl', head_p)]
        if pack_id in LANGS:
            acc_p = os.path.join(tmp, 'accentor.ptl'); exc, lite = export_accentor(os.path.join(HERE, LANGS[pack_id][1]), acc_p)
            files.append(('accentor.ptl', acc_p))
            if not args.stress: args.stress = os.path.join(tmp, 'stress.txt'); exceptions_stress(exc, lite, args.stress)
        out = os.path.join(DIST, f'ruvoice-pack-{pack_id}.zip')
        with zipfile.ZipFile(out, 'w') as zf:
            zf.writestr('pack.json', json.dumps(pack, ensure_ascii=False), compress_type=zipfile.ZIP_DEFLATED)
            for n, p in files:
                zf.write(p, n, compress_type=zipfile.ZIP_STORED)
            if args.stress: zf.write(args.stress, 'stress.txt', compress_type=zipfile.ZIP_DEFLATED)
    if pack_id == 'cis_ru':
        seq, _ = pk.preprocess_tacotron(TEXT)
        for p in (GOLDEN, os.path.join(ROOT, 'app/src/androidTest/assets/golden_pack.json')):
            with open(p, 'w', encoding='utf-8') as f:
                json.dump({'pack': pack, 'text': TEXT, 'speaker': golden_speaker, 'ids': seq.tolist()}, f, ensure_ascii=False, indent=1)
    print('verify: ok, pte maxdiff', d)
    print(out, round(os.path.getsize(out) / 1048576, 1), 'MB;', len(speakers), 'голосов')
