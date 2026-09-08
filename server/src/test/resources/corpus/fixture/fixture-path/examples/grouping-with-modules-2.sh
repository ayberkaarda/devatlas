#!/usr/bin/env bash
set -euo pipefail

python -m venv .venv
source .venv/bin/activate
python -m pip install --quiet --upgrade pip
python -m billing.report --month 2026-08
