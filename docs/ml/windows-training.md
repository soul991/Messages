# Windows GPU training setup

Updated: 2026-10-10

The isolated research environment uses Python 3.12 and the CUDA-enabled PyTorch
wheel on the Windows host's NVIDIA RTX 3060. The Android project has no
PyTorch dependency. Keep checkpoints and logs outside
`protection-engine/src/main/resources/`.

## Environment

Verified versions on this host:

- Python 3.12.10
- PyTorch 2.8.0+cu128 (CUDA runtime 12.8)
- NumPy 2.5.3, PyArrow 26.0.0
- NVIDIA GeForce RTX 3060; `torch.cuda.is_available()` returned true

Create an isolated environment at the workspace root and install the GPU wheel
from the official PyTorch CUDA 12.8 index. The wheel bundles its CUDA runtime;
the NVIDIA display driver must still be installed on Windows.

```powershell
Set-Location D:\Istak\Messages
py -3.12 -m venv .venv-ml
.\.venv-ml\Scripts\python.exe -m pip install --upgrade pip
.\.venv-ml\Scripts\python.exe -m pip install --index-url https://download.pytorch.org/whl/cu128 'torch==2.8.0+cu128'
.\.venv-ml\Scripts\python.exe -m pip install numpy==2.5.3 pyarrow==26.0.0
# Optional, only for the separate MuRIL/MiniLM tools:
.\.venv-ml\Scripts\python.exe -m pip install transformers==5.19.0
.\.venv-ml\Scripts\python.exe -c "import torch; print(torch.__version__, torch.version.cuda, torch.cuda.is_available(), torch.cuda.get_device_name(0))"
```

The existing venv also has `transformers==5.19.0` for the separate MuRIL and
MiniLM evaluation tools. It is not required by the from-scratch character CNN.

## Character CNN runs

The workspace datasets are at `D:\Istak\Messages\Datasets`. The newly supplied
Apache-2.0 source is nested at
`Datasets\Datasets\scam-ham-india\scam_hum_india.csv`; do not point the data
root at the nested folder because the established public train/validation/test
corpora are in the outer folder.

The baseline and supplemental commands below use identical fit settings. The
supplemental corpus is fit-only; exact normalized-text duplicates and
conflicting labels are excluded, and exact overlap with the frozen challenge
is removed before fitting. Validation selects the checkpoint and threshold;
the public test and frozen challenge are evaluation-only.

```powershell
$repo = 'D:\Istak\Messages\Messages App\01_active_codebase\messages'
$python = 'D:\Istak\Messages\.venv-ml\Scripts\python.exe'
$data = 'D:\Istak\Messages\Datasets'
$trainer = Join-Path $repo 'protection-engine\tools\train_char_cnn.py'

& $python -u $trainer --data-root $data `
  --out-dir 'D:\Istak\Messages\char-cnn-windows-baseline' `
  --epochs 12 --batch-size 256 --patience 3

& $python -u $trainer --data-root $data `
  --scam-ham-india-csv 'D:\Istak\Messages\Datasets\Datasets\scam-ham-india\scam_hum_india.csv' `
  --out-dir 'D:\Istak\Messages\char-cnn-windows-india' `
  --epochs 12 --batch-size 256 --patience 3
```

Evaluate each frozen candidate at the threshold selected from its validation
ham rows:

```powershell
$evaluator = Join-Path $repo 'protection-engine\tools\evaluate_char_cnn_candidate.py'
$challenge = Join-Path $data 'trilingual-fraud-consumer-protection-v2\data.csv'

& $python $evaluator --data-root $data `
  --candidate-dir 'D:\Istak\Messages\char-cnn-windows-baseline' `
  --challenge $challenge

& $python $evaluator --data-root $data `
  --candidate-dir 'D:\Istak\Messages\char-cnn-windows-india' `
  --challenge $challenge `
  --scam-ham-india-csv 'D:\Istak\Messages\Datasets\Datasets\scam-ham-india\scam_hum_india.csv'
```

Run artifacts and aggregate-only logs stay in the workspace-level output
directories. Do not copy either research checkpoint into the installed app
resource or enable statistical routing based on these experiments alone.
