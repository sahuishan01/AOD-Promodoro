#!/usr/bin/env python3
import os
import sys
import subprocess

def run(cmd):
    print(f"+ {' '.join(cmd)}")
    res = subprocess.run(cmd, text=True)
    return res.returncode

def main():
    token = os.environ.get("GITHUB_TOKEN") or os.environ.get("GH_TOKEN")
    repo = os.environ.get("GITHUB_REPOSITORY")
    tag = os.environ.get("GITHUB_REF_NAME")
    sha = os.environ.get("GITHUB_SHA", "main")

    if not token or not repo or not tag:
        print(f"Error: Missing env: GITHUB_TOKEN={bool(token)}, GITHUB_REPOSITORY={repo}, GITHUB_REF_NAME={tag}")
        sys.exit(1)

    os.environ["GH_TOKEN"] = token

    dist_dir = "dist"
    if not os.path.exists(dist_dir):
        print(f"Error: {dist_dir} does not exist!")
        sys.exit(1)

    files = sorted([
        os.path.join(dist_dir, f) for f in os.listdir(dist_dir)
        if f.endswith(".apk") or f.endswith(".aab")
    ])
    print(f"Staged assets in dist/: {files}")
    if not files:
        print("No files found in dist/")
        sys.exit(1)

    print(f"=== Ensuring Release Exists for {tag} ===")
    run(["gh", "release", "create", tag, "--repo", repo, "--title", f"AOD Pomodoro {tag}", "--notes", f"AOD Pomodoro {tag} Release", "--target", sha])

    print(f"=== Uploading Assets to {tag} with Clobber ===")
    code = run(["gh", "release", "upload", tag] + files + ["--repo", repo, "--clobber"])
    if code != 0:
        print(f"gh release upload failed with exit code {code}")
        sys.exit(code)

    print(f"=== Verifying Release Assets for {tag} ===")
    code = run(["gh", "release", "view", tag, "--repo", repo])
    if code != 0:
        print(f"gh release view failed with exit code {code}")
        sys.exit(code)

    print("=== Release publication successfully completed and verified! ===")

if __name__ == "__main__":
    main()
