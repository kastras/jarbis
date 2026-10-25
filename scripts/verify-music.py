#!/usr/bin/env python3
"""Verifica Música y resume JUnit; muestra el log de Gradle solo si falla."""
import argparse
from pathlib import Path
import subprocess
import sys
import xml.etree.ElementTree as ET


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--connected", action="store_true", help="Incluye pruebas en dispositivo/emulador")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    tasks = [":app:assembleDebug", ":app:testDebugUnitTest", ":app:lintDebug"]
    if args.connected:
        tasks.append(":app:connectedDebugAndroidTest")
    print("Verificando: " + ", ".join(tasks), flush=True)
    result = subprocess.run([str(root / "gradlew"), *tasks, "--console=plain", "-q"],
                            cwd=root, capture_output=True, text=True)
    if result.returncode:
        sys.stdout.write(result.stdout)
        sys.stderr.write(result.stderr)
        for directory in (root / "app/build/test-results/testDebugUnitTest",
                          root / "app/build/outputs/androidTest-results/connected/debug"):
            for report in directory.rglob("TEST-*.xml"):
                for case in ET.parse(report).getroot().iter("testcase"):
                    for failure in list(case.findall("failure")) + list(case.findall("error")):
                        detail = (failure.text or "Proceso de pruebas interrumpido").splitlines()[0]
                        print(f"FALLO {case.get('classname')}.{case.get('name')}: {detail}")
        return result.returncode
    directories = [root / "app/build/test-results/testDebugUnitTest"]
    if args.connected:
        directories.append(root / "app/build/outputs/androidTest-results/connected/debug")
    for directory in directories:
        reports = list(directory.rglob("TEST-*.xml"))
        if not reports:
            print(f"Sin informes JUnit en {directory}", file=sys.stderr)
            return 1
        totals = dict.fromkeys(("tests", "failures", "errors", "skipped"), 0)
        for report in reports:
            suite = ET.parse(report).getroot()
            for key in totals:
                totals[key] += int(suite.get(key, 0))
        print(f"{directory.name}: " + ", ".join(f"{key}={value}" for key, value in totals.items()))
        if not totals["tests"] or totals["failures"] or totals["errors"]:
            return 1
    print("APK debug y lint: OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
