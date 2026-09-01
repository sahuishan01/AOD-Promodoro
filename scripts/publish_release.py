#!/usr/bin/env python3
import os
import sys
import json
import urllib.request
import urllib.error

def main():
    token = os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN")
    repo = os.environ.get("GH_REPO") or os.environ.get("GITHUB_REPOSITORY")
    tag = os.environ.get("GITHUB_REF_NAME")

    if not token or not repo or not tag:
        print(f"Missing env: GH_TOKEN={bool(token)}, GH_REPO={repo}, GITHUB_REF_NAME={tag}")
        sys.exit(1)

    print(f"Publishing release for {repo} tag {tag}...")

    # 1. Check if release already exists
    req = urllib.request.Request(
        f"https://api.github.com/repos/{repo}/releases/tags/{tag}",
        headers={
            "Authorization": f"Bearer {token}",
            "Accept": "application/vnd.github+json",
            "User-Agent": "GitHub-Actions",
        },
    )
    release = None
    try:
        with urllib.request.urlopen(req) as resp:
            release = json.loads(resp.read().decode())
            print(f"Found existing release ID {release['id']}")
    except urllib.error.HTTPError as e:
        if e.code == 404:
            print(f"Release {tag} does not exist yet. Creating...")
        else:
            print(f"Error fetching release: HTTP {e.code} {e.read().decode()}")

    # 2. If not found, create release
    if not release:
        create_payload = json.dumps({
            "tag_name": tag,
            "name": f"AOD Pomodoro {tag}",
            "body": f"Release {tag}",
            "draft": False,
            "prerelease": False
        }).encode("utf-8")

        req = urllib.request.Request(
            f"https://api.github.com/repos/{repo}/releases",
            data=create_payload,
            headers={
                "Authorization": f"Bearer {token}",
                "Accept": "application/vnd.github+json",
                "User-Agent": "GitHub-Actions",
                "Content-Type": "application/json"
            },
            method="POST"
        )
        try:
            with urllib.request.urlopen(req) as resp:
                release = json.loads(resp.read().decode())
                print(f"Successfully created release ID {release['id']}")
        except urllib.error.HTTPError as e:
            print(f"Error creating release: HTTP {e.code} {e.read().decode()}")
            sys.exit(1)

    release_id = release["id"]
    upload_url = release["upload_url"].split("{")[0]

    # 3. Get existing assets and delete duplicates
    req = urllib.request.Request(
        f"https://api.github.com/repos/{repo}/releases/{release_id}/assets",
        headers={
            "Authorization": f"Bearer {token}",
            "Accept": "application/vnd.github+json",
            "User-Agent": "GitHub-Actions",
        },
    )
    with urllib.request.urlopen(req) as resp:
        existing_assets = json.loads(resp.read().decode())
    asset_map = {a["name"]: a["id"] for a in existing_assets}

    # 4. Find all files in dist/
    dist_dir = sys.argv[1] if len(sys.argv) > 1 else "dist"
    files = [os.path.join(dist_dir, f) for f in os.listdir(dist_dir) if f.endswith(".apk") or f.endswith(".aab")]
    if not files:
        print(f"Error: No binaries found in {dist_dir}!")
        sys.exit(1)

    print(f"Uploading {len(files)} files: {[os.path.basename(f) for f in files]}")

    for fpath in files:
        fname = os.path.basename(fpath)
        fsize = os.path.getsize(fpath)
        if fname in asset_map:
            print(f"Deleting existing asset {fname} (id={asset_map[fname]})...")
            del_req = urllib.request.Request(
                f"https://api.github.com/repos/{repo}/releases/assets/{asset_map[fname]}",
                headers={
                    "Authorization": f"Bearer {token}",
                    "Accept": "application/vnd.github+json",
                    "User-Agent": "GitHub-Actions",
                },
                method="DELETE"
            )
            try:
                with urllib.request.urlopen(del_req) as del_resp:
                    pass
            except urllib.error.HTTPError as e:
                print(f"Warning deleting asset: HTTP {e.code}")

        print(f"Uploading {fname} ({fsize / 1048576:.2f} MB)...")
        with open(fpath, "rb") as f:
            file_data = f.read()

        up_req = urllib.request.Request(
            f"{upload_url}?name={fname}",
            data=file_data,
            headers={
                "Authorization": f"Bearer {token}",
                "Accept": "application/vnd.github+json",
                "User-Agent": "GitHub-Actions",
                "Content-Type": "application/octet-stream",
                "Content-Length": str(len(file_data)),
            },
            method="POST"
        )
        try:
            with urllib.request.urlopen(up_req) as up_resp:
                print(f"✓ Uploaded {fname} (HTTP {up_resp.status})")
        except urllib.error.HTTPError as e:
            print(f"✗ Failed to upload {fname}: HTTP {e.code} - {e.read().decode()}")
            sys.exit(1)

    print(f"Release {tag} published and all assets uploaded successfully!")

if __name__ == "__main__":
    main()
