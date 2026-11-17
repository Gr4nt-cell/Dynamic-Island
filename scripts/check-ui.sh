#!/usr/bin/env bash
set -euo pipefail
# Run instrumentation directly so screenshots remain available before app uninstall.
gradle --no-daemon assembleDebug assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell input keyevent 82
adb shell am instrument -w -r com.gr4nt.dynamicisland.test/androidx.test.runner.AndroidJUnitRunner | tee ui-test.log
adb pull /sdcard/Android/data/com.gr4nt.dynamicisland/files/screenshots ui-screenshots
python3 - <<'PY'
import re
from pathlib import Path
log = Path('ui-test.log').read_text()
if any(marker in log for marker in ['FAILURES!!!', 'INSTRUMENTATION_FAILED', 'Process crashed']):
    raise SystemExit('Android UI checks failed; see ui-test.log')
if not re.search(r'OK \(\d+ tests?\)', log):
    raise SystemExit('Android UI runner did not report a successful test suite')
images = list(Path('ui-screenshots').rglob('*.png'))
if len(images) < 10:
    raise SystemExit(f'Expected UI previews, found only {len(images)}')
print(f'UI checks passed; captured {len(images)} previews')
PY
