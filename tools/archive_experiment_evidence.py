#!/usr/bin/env python3
"""Losslessly archive large, untracked experiment artifacts; retain local originals."""

import argparse
import gzip
import hashlib
import json
from pathlib import Path
import shutil
import subprocess


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def unpacked_digest(path):
    with gzip.open(path, "rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=["archive", "verify", "restore"])
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parent.parent)
    parser.add_argument("--manifest", default="runtime/evidence-archive-20261108.json")
    parser.add_argument("--min-bytes", type=int, default=2 * 1024 * 1024)
    args = parser.parse_args()
    root = args.root.resolve()
    manifest = root / args.manifest
    records = json.loads(manifest.read_text())["files"] if manifest.exists() else []

    if args.mode == "archive":
        paths = subprocess.check_output(
            ["git", "ls-files", "--others", "--exclude-standard", "-z", "runtime"], cwd=root
        ).split(b"\0")
        existing = {item["raw_path"] for item in records}
        ignored = []
        for raw in paths:
            if not raw:
                continue
            relative = raw.decode()
            source = root / relative
            if (not source.is_file() or source.stat().st_size < args.min_bytes
                    or source.suffix == ".gz" or relative in existing):
                continue
            archive = source.with_name(source.name + ".gz")
            if archive.exists():
                raise RuntimeError(f"Archive already exists outside manifest: {archive}")
            raw_sha = digest(source)
            with source.open("rb") as incoming, archive.open("wb") as outgoing:
                with gzip.GzipFile(filename="", fileobj=outgoing, mode="wb", mtime=0) as compressed:
                    shutil.copyfileobj(incoming, compressed)
            if unpacked_digest(archive) != raw_sha or digest(source) != raw_sha:
                raise RuntimeError(f"Evidence changed or archive failed verification: {relative}")
            records.append({"raw_path": relative, "archive_path": str(archive.relative_to(root)),
                            "raw_sha256": raw_sha, "archive_sha256": digest(archive),
                            "raw_bytes": source.stat().st_size, "archive_bytes": archive.stat().st_size})
            ignored.append("/" + relative)
        manifest.write_text(json.dumps({"format": "individual-gzip-sha256-v1", "files": records}, indent=2) + "\n")
        if ignored:
            with (root / ".gitignore").open("a") as stream:
                stream.write("\n# Local originals of losslessly archived November 8 evidence.\n")
                stream.write("\n".join(ignored) + "\n")

    for item in records:
        archive = root / item["archive_path"]
        source = root / item["raw_path"]
        if not archive.resolve().is_relative_to(root) or not source.resolve().is_relative_to(root):
            raise RuntimeError("Archive paths must stay within the repository")
        if digest(archive) != item["archive_sha256"] or unpacked_digest(archive) != item["raw_sha256"]:
            raise RuntimeError(f"Archive checksum mismatch: {archive}")
        if args.mode == "restore":
            if source.exists():
                if digest(source) != item["raw_sha256"]:
                    raise RuntimeError(f"Refusing to overwrite changed evidence: {source}")
            else:
                source.parent.mkdir(parents=True, exist_ok=True)
                with gzip.open(archive, "rb") as incoming, source.open("xb") as outgoing:
                    shutil.copyfileobj(incoming, outgoing)
                if digest(source) != item["raw_sha256"]:
                    raise RuntimeError(f"Restored checksum mismatch: {source}")
    print(json.dumps({"mode": args.mode, "verified_archives": len(records),
                      "raw_bytes": sum(item["raw_bytes"] for item in records),
                      "archive_bytes": sum(item["archive_bytes"] for item in records)}))


if __name__ == "__main__":
    main()
