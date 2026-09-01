#!/usr/bin/env python3
import os
import sys
import json
import time
import urllib.request
import urllib.parse
import urllib.error
import subprocess

def api_request(url, token, data=None, method="GET"):
    headers = {
        "Authorization": f"Bearer {token}",
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
        "User-Agent": "Release-Publisher/1.0"
    }
    if data is not None and isinstance(data, dict):
        payload = json.dumps(data).encode("utf-8")
        headers["Content-Type"] = "application/json"
    elif data is not None:
        payload = data
    else:
        payload = None

    req = urllib.request.Request(url, data=payload, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as resp:
            content = resp.read()
            if content:
                return resp.status, json.loads(content.decode("utf-8"))
            return resp.status, None
    except urllib.error.HTTPError as e:
        err_body = e.read().decode("utf-8", errors="replace")
        try:
            err_json = json.loads(err_body)
        except Exception:
            err_json = {"raw": err_body}
        return e.code, err_json

def get_or_create_release(repo, tag, token):
    print(f"Finding release for {repo} @ {tag}...")
    
    # 1. Direct tag lookup
    status, data = api_request(f"https://api.github.com/repos/{repo}/releases/tags/{tag}", token)
    if status == 200 and data and "id" in data:
        print(f"Found existing release by tag: ID {data['id']}")
        return data

    # 2. List all releases and check
    status, releases = api_request(f"https://api.github.com/repos/{repo}/releases?per_page=50", token)
    if status == 200 and isinstance(releases, list):
        for r in releases:
            if r.get("tag_name") == tag:
                print(f"Found release in list: ID {r['id']}")
                return r

    # 3. Create release
    print(f"Creating new release for tag {tag}...")
    create_payload = {
        "tag_name": tag,
        "name": f"AOD Pomodoro {tag}",
        "body": f"AOD Pomodoro {tag} Release",
        "draft": False,
        "prerelease": False
    }
    status, data = api_request(f"https://api.github.com/repos/{repo}/releases", token, data=create_payload, method="POST")
    if status in (200, 201) and data and "id" in data:
        print(f"Created release: ID {data['id']}")
        return data

    # 4. If conflict, re-scan
    print(f"Create response {status}: {data}. Re-checking releases list...")
    status, releases = api_request(f"https://api.github.com/repos/{repo}/releases?per_page=50", token)
    if status == 200 and isinstance(releases, list):
        for r in releases:
            if r.get("tag_name") == tag:
                print(f"Found release after conflict: ID {r['id']}")
                return r

    print(f"Failed to find or create release! Status: {status}, Data: {data}")
    sys.exit(1)

def delete_asset_if_exists(repo, release_id, fname, token):
    status, assets = api_request(f"https://api.github.com/repos/{repo}/releases/{release_id}/assets", token)
    if status == 200 and isinstance(assets, list):
        for a in assets:
            if a.get("name") == fname:
                asset_id = a.get("id")
                print(f"Deleting conflicting asset {fname} (ID {asset_id})...")
                api_request(f"https://api.github.com/repos/{repo}/releases/assets/{asset_id}", token, method="DELETE")
                time.sleep(2)

def upload_file_curl(repo, release_id, upload_base, file_path, token, retries=3):
    fname = os.path.basename(file_path)
    fsize = os.path.getsize(file_path)
    target_url = f"{upload_base}?name={urllib.parse.quote(fname)}"

    for attempt in range(1, retries + 1):
        print(f"\n--- Uploading {fname} ({fsize:,} bytes) [Attempt {attempt}/{retries}] ---")
        cmd = [
            "curl", "-s", "-S", "-L",
            "-X", "POST",
            "-H", f"Authorization: Bearer {token}",
            "-H", "Accept: application/vnd.github+json",
            "-H", "Content-Type: application/octet-stream",
            "--data-binary", f"@{file_path}",
            target_url
        ]
        res = subprocess.run(cmd, capture_output=True, text=True)
        if res.returncode != 0:
            print(f"curl failed: {res.stderr}")
            time.sleep(2)
            continue

        try:
            resp_obj = json.loads(res.stdout)
            if "id" in resp_obj and "browser_download_url" in resp_obj:
                print(f"Successfully uploaded {fname}! URL: {resp_obj['browser_download_url']}")
                return
            elif "already_exists" in res.stdout or resp_obj.get("message") == "Validation Failed":
                print(f"Asset conflict detected. Purging and retrying...")
                delete_asset_if_exists(repo, release_id, fname, token)
                continue
            else:
                print(f"Upload response: {resp_obj}")
        except Exception:
            print(f"Raw output: {res.stdout[:200]}")
        
        time.sleep(2)

    print(f"Failed to upload {fname} after {retries} attempts!")
    sys.exit(1)

def main():
    token = os.environ.get("GITHUB_TOKEN") or os.environ.get("GH_TOKEN")
    repo = os.environ.get("GITHUB_REPOSITORY")
    tag = os.environ.get("GITHUB_REF_NAME")

    if not token or not repo or not tag:
        print(f"Error: Missing required env vars: GITHUB_TOKEN={bool(token)}, GITHUB_REPOSITORY={repo}, GITHUB_REF_NAME={tag}")
        sys.exit(1)

    release = get_or_create_release(repo, tag, token)
    release_id = release["id"]
    upload_url_tmpl = release.get("upload_url", f"https://uploads.github.com/repos/{repo}/releases/{release_id}/assets{{?name,label}}")
    upload_base = upload_url_tmpl.split("{")[0]

    dist_dir = "dist"
    if not os.path.exists(dist_dir):
        print(f"Error: {dist_dir} does not exist!")
        sys.exit(1)

    files_to_upload = sorted([
        os.path.join(dist_dir, f) for f in os.listdir(dist_dir)
        if f.endswith(".apk") or f.endswith(".aab")
    ])

    if not files_to_upload:
        print("Error: No .apk or .aab files found in dist/!")
        sys.exit(1)

    print(f"Staged files to publish: {[os.path.basename(f) for f in files_to_upload]}")

    # Delete any existing assets
    for fpath in files_to_upload:
        delete_asset_if_exists(repo, release_id, os.path.basename(fpath), token)

    # Upload files
    for fpath in files_to_upload:
        upload_file_curl(repo, release_id, upload_base, fpath, token)

    # Final verification
    print("\n=== Verifying Release Assets on GitHub ===")
    status, assets = api_request(f"https://api.github.com/repos/{repo}/releases/{release_id}/assets", token)
    if status == 200 and isinstance(assets, list):
        print(f"Total assets verified on release: {len(assets)}")
        for a in assets:
            print(f"  - {a.get('name')}: {a.get('size'):,} bytes -> {a.get('browser_download_url')}")
    else:
        print(f"Warning: could not verify assets listing: status {status}")

    print("\n=== Release publication completed successfully! ===")

if __name__ == "__main__":
    main()
