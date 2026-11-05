"""Run the production API 102 hook against a simulated framework/Samsung boundary.

Requires Java 21 and ANDROID_HOME. This is not a physical device rendering test.
"""
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]

if __name__ == '__main__':
    wrapper = 'gradlew.bat' if os.name == 'nt' else './gradlew'
    subprocess.run([wrapper, '--no-daemon', 'testDebugUnitTest', '--tests',
                    'io.github.aureliomo.prismlock.ModernHookTest'], cwd=ROOT, check=True)
