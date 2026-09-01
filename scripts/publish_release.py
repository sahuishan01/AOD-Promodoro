#!/usr/bin/env python3
import os
import sys
import json
import urllib.request
import urllib.parse
import urllib.error

def main():
    token = os.environ.get("GITHUB_TOKEN") or os.environ.get("GH_TOKEN")
    repo = os.environ.get("GITHUB_REPOSITORY")
    tag = os.environ.get("GITHUB_REF_NAME")

    if not token or not repo or not tag:
        print(f"Missing required env vars: GITHUB_TOKEN={bool(token)}, GITHUB_REPOSITORY={repo}, GITHUB_REF_NAME={tag}")
        sys.exit(1)

    headers = {
        "Authorization": f"Bearer {token}",
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
        "User-Agent": "Release-Publisher/1.0"
    }

    print(f"=== Publishing release for {repo} @ {tag} ===")

    # 1. Check if release already exists
    release_url = f"https://api.github.com/repos/{repo}/releases/tags/{tag}"
    req = urllib.request.Request(release_url, headers=headers)
    release = None
    try:
        with urllib.request.urlopen(req) as resp:
            if resp.status == 200:
                release = json.loads(resp.read().decode())
                print(f"Found existing release id: {release['id']}")
    except urllib.error.HTTPError as e:
        if e.code == 404:
            print(f"No existing release found for tag {tag}. Creating one...")
        else:
            print(f"Error fetching release: {e.code} {e.read().decode()}")
            sys.exit(1)

    # 2. Create release if not found
    if release is None:
        create_url = f"https://api.github.com/repos/{repo}/releases"
        payload = json.dumps({
            "tag_name": tag,
            "name": f"AOD Pomodoro {tag}",
            "body": f"AOD Pomodoro {tag} Release",
            "draft": False,
            "prerelease": False
        }).encode()
        req = urllib.request.Request(create_url, data=payload, headers=headers, method="POST")
        try:
            with urllib.request.urlopen(req) as resp:
                release = json.loads(resp.read().decode())
                print(f"Successfully created release id: {release['id']}")
        except urllib.error.HTTPError as e:
            print(f"Failed to create release: {e.code} {e.read().decode()}")
            sys.exit(1)

    release_id = release["id"]
    upload_url_tmpl = release.get("upload_url", f"https://uploads.github.com/repos/{repo}/releases/{release_id}/assets{{?name,label}}")
    upload_base = upload_url_tmpl.split("{")[0]

    # 3. Find files in dist/
    dist_dir = "dist"
    if not os.path.exists(dist_dir):
        print("dist/ directory does not exist!")
        sys.exit(1)

    files_to_upload = [f for f in os.listdir(dist_dir) if f.endswith(".apk") or f.endswith(".aab")]
    print(f"Files to upload: {files_to_upload}")

    if not files_to_upload:
        print("No .apk or .aab files found in dist/!")
        sys.exit(1)

    # 4. Delete existing assets if they match filename
    existing_assets = {a["name"]: a["id"] for a in release.get("assets", [])}
    for fname in files_to_upload:
        if fname in existing_assets:
            asset_id = existing_assets[fname]
            del_url = f"https://api.github.com/repos/{repo}/releases/assets/{asset_id}"
            print(f"Deleting existing asset {fname} (id: {asset_id})...")
            del_req = urllib.request.Request(del_url, headers=headers, method="DELETE")
            try:
                with urllib.request.urlopen(del_req) as resp:
                    pass
            except Exception as e:
                print(f"Warning: failed to delete asset {fname}: {e}")

    # 5. Upload each file
    for fname in files_to_upload:
        fpath = os.path.join(dist_dir, fname)
        fsize = os.path.getsize(fpath)
        print(f"Uploading {fname} ({fsize} bytes)...")

        target_upload_url = f"{upload_base}?name={urllib.parse.quote(fname)}"
        with open(fpath, "rb") as f:
            data = f.read()

        upload_headers = headers.copy()
        upload_headers["Content-Type"] = "application/octet-stream"
        upload_headers["Content-Length"] = str(len(data))

        upload_req = urllib.request.Request(target_upload_url, data=data, headers=upload_headers, method="POST")
        try:
            with urllib.request.urlopen(upload_req) as resp:
                result = json.loads(resp.read().decode())
                print(f"Uploaded {fname} successfully! Download URL: {result.get('browser_download_url')}")
        except urllib.error.HTTPError as e:
            print(f"Failed to upload {fname}: {e.code} {e.read().decode()}")
            sys.exit(1)

    print("\n=== Release upload verified successfully! ===")

if __name__ == "__main__":
    main()
