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
    if not os.path.exists(dist_dir):
        print(f"Error: {dist_dir} directory does not exist!")
        sys.exit(1)

    files = sorted([
        os.path.join(dist_dir, f) for f in os.listdir(dist_dir)
        if f.endswith(".apk") or f.endswith(".aab")
    ])
    if not files:
        print(f"Error: No APK or AAB files found in {dist_dir} directory!")
        sys.exit(1)
    print(f"Files to publish: {[os.path.basename(f) for f in files]}")

    # Check existing assets and purge
    status, assets = api_call(f"https://api.github.com/repos/{repo}/releases/{release_id}/assets", token)
    if status == 200 and isinstance(assets, list):
        for a in assets:
            print(f"Purging existing asset {a['name']} (ID {a['id']})...")
            api_call(f"https://api.github.com/repos/{repo}/releases/assets/{a['id']}", token, method="DELETE")
        if assets:
            time.sleep(3)

    # Upload assets: 2-step upload or stripping Authorization header on redirect
    import subprocess
    for fpath in files:
        fname = os.path.basename(fpath)
        target_url = f"{upload_base}?name={urllib.parse.quote(fname)}"
        print(f"\n--- Uploading {fname} ({os.path.getsize(fpath):,} bytes) ---")
        
        # Step 1: Get release asset upload redirect/URL from GitHub API without auto-following
        headers = [
            "-H", f"Authorization: Bearer {token}",
            "-H", "Accept: application/vnd.github+json",
            "-H", "Content-Type: application/octet-stream",
        ]
        cmd_init = ["curl", "-s", "-S", "-i"] + headers + ["-X", "POST", "--data-binary", f"@{fpath}", target_url]
        print(f"Executing GitHub Upload request for {fname}...")
        res = subprocess.run(cmd_init, capture_output=True, text=True)
        
        # If GitHub returned direct 201 Created JSON
        if "201 Created" in res.stdout or '"id":' in res.stdout:
            try:
                json_part = res.stdout.split("\r\n\r\n")[-1]
                resp = json.loads(json_part)
                if "id" in resp:
                    print(f"Success! {fname} -> {resp.get('browser_download_url')}")
                    continue
            except Exception as e:
                print(f"Direct JSON parse attempt: {e}")

        # If HTTP 307 redirect to S3, extract Location header and upload without auth token
        location = None
        for line in res.stdout.splitlines():
            if line.lower().startswith("location:"):
                location = line.split(":", 1)[1].strip()
                break

        if location:
            print(f"Following 307 redirect to S3 without Bearer token...")
            s3_cmd = ["curl", "-s", "-S", "-i", "-H", "Content-Type: application/octet-stream", "-X", "PUT", "--data-binary", f"@{fpath}", location]
            s3_res = subprocess.run(s3_cmd, capture_output=True, text=True)
            print(f"S3 response header line: {s3_res.stdout.splitlines()[0] if s3_res.stdout else 'EMPTY'}")
            if "HTTP/1.1 200" in s3_res.stdout or "HTTP/2 200" in s3_res.stdout or "HTTP/1.1 201" in s3_res.stdout or "HTTP/2 201" in s3_res.stdout:
                print(f"Success uploading {fname} to S3!")
                continue
            else:
                print(f"S3 upload error response:\n{s3_res.stdout[:1000]}")

        print(f"Error uploading {fname}:\nSTDOUT:\n{res.stdout[:1000]}\nSTDERR:\n{res.stderr[:1000]}")
        sys.exit(1)

    print("\n=== Release Published and Verified Successfully ===")

if __name__ == "__main__":
    main()
