import sys
import xml.etree.ElementTree as ET
from pathlib import Path

reports = Path(sys.argv[1])
expected = {
    "RawIdDisplay": "MainActivity.kt",
    "IdInTranslation": "strings.xml",
    "ExceptionToUi": "FixtureBehavior.kt",
    "LocalizedExceptionSwallowed": "FixtureBehavior.kt",
    "MissingLocaleKey": "strings.xml",
    "LocalizedDataLeak": "MainActivity.kt",
}


def issues(variant):
    report = reports / f"lint-results-{variant}Debug.xml"
    if not report.is_file():
        raise SystemExit(f"Missing lint report: {report}")
    return ET.parse(report).getroot().findall("issue")


clean = [issue.get("id") for issue in issues("clean") if issue.get("id") in expected]
if clean:
    raise SystemExit(f"Clean fixture has localization findings: {clean}")

defects = issues("defects")
for rule, filename in expected.items():
    matches = [issue for issue in defects if issue.get("id") == rule]
    if not any(filename in location.get("file", "") for issue in matches for location in issue.findall("location")):
        raise SystemExit(f"Defects fixture missing {rule} in {filename}")

locale_messages = [issue.get("message", "").lower() for issue in defects if issue.get("id") == "MissingLocaleKey"]
if not any("missing" in message for message in locale_messages):
    raise SystemExit("Defects fixture missing the untranslated-key finding")
if not any("format" in message or "argument" in message for message in locale_messages):
    raise SystemExit("Defects fixture missing the incompatible-format finding")
