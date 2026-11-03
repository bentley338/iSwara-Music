"""Buat ulang model mobile dari bobot Spleeter resmi yang dipin; tidak melatih model baru."""
from pathlib import Path
import hashlib
import json
import urllib.request
import onnx

root = Path(__file__).resolve().parents[1]
cache = root / '.temp/stem-research'
cache.mkdir(parents=True, exist_ok=True)
destination = root / 'app/src/main/assets/stem-models'
destination.mkdir(parents=True, exist_ok=True)
commit = '93ba771920ade509f8cbd6825b1a90856c797e08'
originals = {
    'vocals': '24cef84aedcd1fe87c0b743ef3370ad34dc1fabf6c9014d6128a75a538c7b668',
    'accompaniment': 'd14cea55793cc531a5875f5f4da08207d1c5ab9292e8e0099a104eecb014fcc0',
}
manifest = {'origin': 'Deezer Spleeter 2-stems; ONNX conversion by k2-fsa/sherpa-onnx',
            'mirror_commit': commit, 'frames': 128, 'sample_rate': 44100, 'models': {}}
for name, expected in originals.items():
    source = cache / f'{name}.fp16.onnx'
    if not source.exists():
        urllib.request.urlretrieve(f'https://huggingface.co/csukuangfj/sherpa-onnx-spleeter-2stems-fp16/resolve/{commit}/{name}.fp16.onnx', source)
    assert hashlib.sha256(source.read_bytes()).hexdigest() == expected
    model = onnx.load(source)
    # The U-Net is fully convolutional. Keep every learned weight; use bounded windows.
    for value in list(model.graph.input) + list(model.graph.output):
        value.type.tensor_type.shape.dim[2].dim_value = 128
    # Recompute intermediate shapes rather than retaining exported 512-frame annotations.
    del model.graph.value_info[:]
    pending = list(model.graph.node)
    ordered = []
    available = {value.name for value in model.graph.input} | {value.name for value in model.graph.initializer} | {''}
    while pending:
        ready = [node for node in pending if all(value in available for value in node.input)]
        assert ready, 'Graph is not acyclic'
        for node in ready:
            ordered.append(node)
            available.update(node.output)
            pending.remove(node)
    del model.graph.node[:]
    model.graph.node.extend(ordered)
    onnx.checker.check_model(model)
    output = destination / f'{name}.onnx'
    onnx.save(model, output)
    manifest['models'][name] = {'original_sha256': expected, 'sha256': hashlib.sha256(output.read_bytes()).hexdigest(), 'bytes': output.stat().st_size}
    print(name, manifest['models'][name]['sha256'])
(destination / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
