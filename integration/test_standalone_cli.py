import json
import os
import shutil
import subprocess
import tempfile
from pathlib import Path

repo = Path(__file__).resolve().parent.parent
jar = repo / "cli/build/libs/i18n-lint.jar"
sdk = os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME")
if not sdk:
    raise SystemExit("Set ANDROID_SDK_ROOT or ANDROID_HOME for the fixture SDK")

with tempfile.TemporaryDirectory(prefix="i18n-cli-") as temp:
    outside = Path(temp)
    project = outside / "project"
    shutil.copytree(repo / "integration/standalone", project)
    shutil.copy2(repo / "gradlew", project / "gradlew")
    shutil.copy2(repo / "gradlew.bat", project / "gradlew.bat")
    shutil.copytree(repo / "gradle/wrapper", project / "gradle/wrapper")
    sdk_path = Path(sdk).resolve().as_posix().replace(":", "\\:")
    (project / "local.properties").write_text(f"sdk.dir={sdk_path}\n", encoding="utf-8")
    env = os.environ.copy()
    env.pop("ANDROID_HOME", None)
    env.pop("ANDROID_SDK_ROOT", None)
    result = subprocess.run(
        ["java", "-jar", str(jar), "lint", "--path", str(project), "--json"],
        cwd=outside, env=env, text=True, capture_output=True,
    )
    if result.returncode != 1:
        raise SystemExit(f"Expected findings (exit 1), got {result.returncode}:\n{result.stdout}\n{result.stderr}")
    envelope = json.loads(result.stdout)
    ids = {finding.get("id") for finding in envelope.get("evidence", [])}
    if envelope.get("status") != "fail" or not {"IdInTranslation", "MissingLocaleKey"} <= ids:
        raise SystemExit(f"Missing expected custom findings: {envelope}")
    run_dir = project / ".android-kit" / "runs" / envelope["run_id"] / "i18n"
    for name in ("lint.xml", "lint.sarif", "report.md"):
        if not (run_dir / name).is_file():
            raise SystemExit(f"Missing project-root artifact: {run_dir / name}")
    if (outside / ".android-kit").exists():
        raise SystemExit("Artifacts were written to the caller's directory instead of the project root")
