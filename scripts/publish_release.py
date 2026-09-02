#!/usr/bin/env python3
import os
import sys
import json
import time
import urllib.request
import urllib.parse
import urllib.error

class NoAuthRedirectHandler(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        new_req = super().redirect_request(req, fp, code, msg, headers, newurl)
        if new_req and "Authorization" in new_req.headers:
            del new_req.headers["Authorization"]
        return new_req

def api_call(url, token, data=None, method="GET", content_type="application/json"):
    headers = {
        "Authorization": f"Bearer {token}",
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
        "User-Agent": "AOD-Release-Publisher/1.0"
    }
    payload = None
    if data is not None:
        if isinstance(data, dict):
            payload = json.dumps(data).encode("utf-8")
            headers["Content-Type"] = "application/json"
        elif isinstance(data, bytes):
            payload = data
            headers["Content-Type"] = content_type
            headers["Content-Length"] = str(len(data))

    req = urllib.request.Request(url, data=payload, headers=headers, method=method)
    opener = urllib.request.build_opener(NoAuthRedirectHandler())
    try:
        with opener.open(req) as resp:
            body = resp.read()
            if body:
                return resp.status, json.loads(body.decode("utf-8"))
            return resp.status, None
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", errors="replace")
        try:
            return e.code, json.loads(raw)
        except Exception:
            return e.code, {"raw": raw}

def get_or_create_release(repo, tag, token):
    print(f"Fetching release for {repo} tag {tag}...")
    status, data = api_call(f"https://api.github.com/repos/{repo}/releases/tags/{tag}", token)
    if status == 200 and data and "id" in data:
        print(f"Found existing release (ID {data['id']})")
        return data

    print(f"Creating release for tag {tag}...")
    create_body = {
        "tag_name": tag,
        "name": f"AOD Pomodoro {tag}",
        "body": f"Automated production build for AOD Pomodoro {tag}.",
        "draft": False,
        "prerelease": False
    }
    status, data = api_call(f"https://api.github.com/repos/{repo}/releases", token, data=create_body, method="POST")
    if status in (200, 201) and data and "id" in data:
        print(f"Created release successfully (ID {data['id']})")
        return data

    # If already exists or error, list releases
    print(f"Release creation response {status}: {data}. Scanning releases list...")
    status, releases = api_call(f"https://api.github.com/repos/{repo}/releases?per_page=100", token)
    if status == 200 and isinstance(releases, list):
        for r in releases:
            if r.get("tag_name") == tag:
                print(f"Matched release from list (ID {r['id']})")
                return r

    print(f"Fatal: Unable to locate or create release for {tag}!")
    sys.exit(1)

def main():
    token = os.environ.get("GITHUB_TOKEN") or os.environ.get("GH_TOKEN")
    repo = os.environ.get("GITHUB_REPOSITORY")
    tag = os.environ.get("GITHUB_REF_NAME")

    if not token or not repo or not tag:
        print(f"Missing required environment variables: GITHUB_TOKEN, GITHUB_REPOSITORY, GITHUB_REF_NAME")
        sys.exit(1)

    release = get_or_create_release(repo, tag, token)
    release_id = release["id"]
    upload_url_tmpl = release.get("upload_url", f"https://uploads.github.com/repos/{repo}/releases/{release_id}/assets{{?name,label}}")
    upload_base = upload_url_tmpl.split("{")[0]

    dist_dir = "dist"
    files = sorted([
        os.path.join(dist_dir, f) for f in os.listdir(dist_dir)
        if f.endswith(".apk") or f.endswith(".aab")
    ])
    print(f"Files to publish: {[os.path.basename(f) for f in files]}")

    # Check existing assets and purge
    status, assets = api_call(f"https://api.github.com/repos/{repo}/releases/{release_id}/assets", token)
    if status == 200 and isinstance(assets, list):
        for a in assets:
            print(f"Purging existing asset {a['name']} (ID {a['id']})...")
            api_call(f"https://api.github.com/repos/{repo}/releases/assets/{a['id']}", token, method="DELETE")
        if assets:
            time.sleep(3)

    # Upload assets via subprocess curl (natively handles 307 redirect stripping auth for S3)
    import subprocess
    for fpath in files:
        fname = os.path.basename(fpath)
        target_url = f"{upload_base}?name={urllib.parse.quote(fname)}"
        print(f"\n--- Uploading {fname} ({os.path.getsize(fpath):,} bytes) ---")
        cmd = [
            "curl", "-s", "-S",
            "-H", f"Authorization: Bearer {token}",
            "-H", "Accept: application/vnd.github+json",
            "-H", "Content-Type: application/octet-stream",
            "--data-binary", f"@{fpath}",
            target_url
        ]
        res = subprocess.run(cmd, capture_output=True, text=True)
        if res.returncode == 0:
            try:
                resp = json.loads(res.stdout)
                if "id" in resp:
                    print(f"Success! {fname} -> {resp.get('browser_download_url')}")
                    continue
            except Exception:
                pass
        print(f"Error uploading {fname}: {res.stdout} {res.stderr}")
        sys.exit(1)

    print("\n=== Release Published and Verified Successfully ===")

if __name__ == "__main__":
    main()
