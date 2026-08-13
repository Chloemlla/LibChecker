#!/usr/bin/env python3
"""Fetch and stage the latest lumen-crash SDK release into local-maven/.

The Lumen Crash SDK (bundle + core) is published as GitHub release assets on
https://github.com/Chloemlla/Project-Lumen. This script:

  1. Queries the GitHub Releases API (optionally authenticated via
     GITHUB_TOKEN) and picks the latest non-draft release tagged
     ``lumen-crash-v*``.
  2. Downloads the 8 Maven artifacts (bundle + core each ship
     .aar/.pom/.module/-sources.jar) plus ``checksums.txt``.
  3. Stages them under ``local-maven/com/chloemlla/lumen/...`` following the
     standard Maven repository layout so the Gradle build can resolve them
     without GitHub Packages credentials (local-maven is preferred in
     settings.gradle.kts and registered before GitHub Packages).
  4. Verifies every downloaded artifact against the SHA-256 checksums published
     in ``checksums.txt`` (a mismatch is fatal).
  5. Writes the resolved artifact version to ``lumen-crash.resolved.version``
     at the repository root (gitignored). ``app/build.gradle.kts`` reads this
     version from the gradle property, env var, or this file.

The script is re-runnable: it clears the version directories for the target
version before re-staging.

Usage:
  python3 .github/scripts/fetch-lumen-crash-sdk.py [--print-version]
"""

import argparse
import hashlib
import json
import os
import shutil
import sys
import urllib.error
import urllib.request
from pathlib import Path

REPO = "Chloemlla/Project-Lumen"
API_RELEASES = "https://api.github.com/repos/%s/releases?per_page=100" % REPO
TAG_PREFIX = "lumen-crash-v"
ARTIFACTS = ("lumen-crash-core", "lumen-crash")
ASSET_SUFFIXES = (".aar", ".pom", ".module", "-sources.jar")

REPO_ROOT = Path(__file__).resolve().parents[2]
LOCAL_MAVEN = REPO_ROOT / "local-maven"
GROUP_PATH = Path("com") / "chloemlla" / "lumen"
RESOLVED_VERSION_FILE = REPO_ROOT / "lumen-crash.resolved.version"
CHECKSUMS_NAME = "checksums.txt"


def api_headers():
  headers = {
    "Accept": "application/vnd.github+json",
    "User-Agent": "lumen-crash-fetch",
  }
  token = os.environ.get("GITHUB_TOKEN")
  if token:
    headers["Authorization"] = "Bearer %s" % token
  return headers


def fetch_json(url):
  request = urllib.request.Request(url, headers=api_headers())
  try:
    with urllib.request.urlopen(request) as response:
      return json.loads(response.read().decode("utf-8"))
  except urllib.error.HTTPError as error:
    raise RuntimeError("GitHub API request failed (%s): %s %s" % (url, error.code, error.reason)) from error
  except urllib.error.URLError as error:
    raise RuntimeError("GitHub API request failed (%s): %s" % (url, error.reason)) from error


def download(url, destination):
  request = urllib.request.Request(url, headers=api_headers())
  try:
    with urllib.request.urlopen(request) as response:
      destination.write_bytes(response.read())
  except urllib.error.HTTPError as error:
    raise RuntimeError("Download failed (%s): %s %s" % (url, error.code, error.reason)) from error
  except urllib.error.URLError as error:
    raise RuntimeError("Download failed (%s): %s" % (url, error.reason)) from error


def latest_release():
  releases = fetch_json(API_RELEASES)
  candidates = [
    release for release in releases
    if not release.get("draft") and release.get("tag_name", "").startswith(TAG_PREFIX)
  ]
  if not candidates:
    raise RuntimeError("No non-draft '%s*' release found in %s" % (TAG_PREFIX, REPO))
  # Ascending by published_at (fallback created_at); the most recent is last.
  candidates.sort(key=lambda release: release.get("published_at") or release.get("created_at") or "")
  return candidates[-1]


def artifact_of(asset_name, version):
  """Return the artifact name if the asset is one of the version's Maven files, else None."""
  for artifact in ARTIFACTS:
    prefix = "%s-%s" % (artifact, version)
    if asset_name.startswith(prefix) and asset_name[len(prefix):] in ASSET_SUFFIXES:
      return artifact
  return None


def sha256_of(path):
  digest = hashlib.sha256()
  with path.open("rb") as handle:
    for chunk in iter(lambda: handle.read(65536), b""):
      digest.update(chunk)
  return digest.hexdigest()


def parse_checksums(checksums_path):
  checksums = {}
  for line in checksums_path.read_text(encoding="utf-8").splitlines():
    parts = line.split()
    if len(parts) >= 2:
      checksums[parts[1]] = parts[0]
  return checksums


def stage_release(release):
  tag = release["tag_name"]
  version = tag[len(TAG_PREFIX):]
  if not version:
    raise RuntimeError("Malformed release tag: %r" % tag)

  assets = release.get("assets", [])
  by_name = {asset["name"]: asset["browser_download_url"] for asset in assets}

  if CHECKSUMS_NAME not in by_name:
    raise RuntimeError("Release %s is missing %s" % (tag, CHECKSUMS_NAME))

  # Download checksums first so every artifact can be verified against it.
  checksums_path = LOCAL_MAVEN / CHECKSUMS_NAME
  checksums_path.parent.mkdir(parents=True, exist_ok=True)
  download(by_name[CHECKSUMS_NAME], checksums_path)
  checksums = parse_checksums(checksums_path)

  artifacts = {}
  for asset_name, asset_url in sorted(by_name.items()):
    artifact = artifact_of(asset_name, version)
    if artifact:
      artifacts.setdefault(artifact, []).append((asset_name, asset_url))

  expected = {artifact for artifact in ARTIFACTS}
  found = set(artifacts)
  if found != expected:
    raise RuntimeError(
      "Release %s does not contain the full Maven asset set. Expected %s, got %s" % (tag, sorted(expected), sorted(found))
    )

  for artifact in ARTIFACTS:
    artifact_dir = LOCAL_MAVEN / GROUP_PATH / artifact / version
    if artifact_dir.exists():
      shutil.rmtree(artifact_dir)
    artifact_dir.mkdir(parents=True, exist_ok=True)
    for asset_name, asset_url in artifacts[artifact]:
      target = artifact_dir / asset_name
      download(asset_url, target)
      actual = sha256_of(target)
      expected_sha = checksums.get(asset_name)
      if expected_sha is None:
        raise RuntimeError("No SHA-256 recorded in %s for %s" % (CHECKSUMS_NAME, asset_name))
      if actual != expected_sha:
        raise RuntimeError(
          "SHA-256 mismatch for %s: expected %s, got %s. The release assets or checksums.txt may be stale."
          % (asset_name, expected_sha, actual)
        )

  RESOLVED_VERSION_FILE.write_text(version + "\n", encoding="utf-8")
  print("Staged lumen-crash SDK %s (%s -> local-maven/com/chloemlla/lumen)" % (version, tag))


def main(argv=None):
  parser = argparse.ArgumentParser(
    description=__doc__,
    formatter_class=argparse.RawDescriptionHelpFormatter,
  )
  parser.add_argument("--print-version", action="store_true", help="print the resolved artifact version and exit")
  args = parser.parse_args(argv)

  try:
    release = latest_release()
  except RuntimeError as error:
    print("error: %s" % error, file=sys.stderr)
    return 1

  version = release["tag_name"][len(TAG_PREFIX):]
  if args.print_version:
    print(version)
    return 0

  try:
    stage_release(release)
  except RuntimeError as error:
    print("error: %s" % error, file=sys.stderr)
    return 1
  return 0


if __name__ == "__main__":
  sys.exit(main())
