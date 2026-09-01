import os
import sys
import time
import requests

def main():
    token = os.environ.get("GITHUB_TOKEN")
    repo = os.environ.get("GITHUB_REPOSITORY")
    tag = os.environ.get("GITHUB_REF_NAME")

    if not all([token, repo, tag]):
        print(f"Missing env: token={'set' if token else 'unset'}, repo={repo}, tag={tag}", file=sys.stderr)
        sys.exit(1)

    headers = {
        "Authorization": f"Bearer {token}",
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
    }

    # 1. Search for all built APK and AAB binaries under app/build/outputs
    print("Searching for built APK and AAB binaries under app/build/outputs...")
    assets_to_upload = {}
    for root, dirs, files in os.walk("app/build/outputs"):
        clean_root = root.replace("\\", "/")
        if "baselineProfiles" in clean_root:
            continue
        for f in files:
            if f.endswith(".apk") or f.endswith(".aab"):
                fpath = os.path.join(root, f)
                assets_to_upload[f] = fpath

    print(f"Found {len(assets_to_upload)} release asset(s):")
    for name, path in assets_to_upload.items():
        print(f"  • {name} ({os.path.getsize(path)} bytes) at {path}")

    if not assets_to_upload:
        print("ERROR: No APK or AAB binaries found in app/build/outputs!", file=sys.stderr)
        sys.exit(1)

    # 2. Get or create release
    print(f"\nFetching release for tag {tag} in {repo}...")
    resp = requests.get(f"https://api.github.com/repos/{repo}/releases/tags/{tag}", headers=headers)
    print(f"Get release response: {resp.status_code}")
    if resp.status_code == 200:
        release = resp.json()
    else:
        print(f"Creating new release for {tag}...")
        resp = requests.post(
            f"https://api.github.com/repos/{repo}/releases",
            headers=headers,
            json={"tag_name": tag, "name": f"AOD Pomodoro {tag}", "body": f"AOD Pomodoro {tag} Release"},
        )
        print(f"Create release response: {resp.status_code} {resp.text[:200]}")
        resp.raise_for_status()
        release = resp.json()

    release_id = release["id"]
    upload_url_template = release.get("upload_url", f"https://uploads.github.com/repos/{repo}/releases/{release_id}/assets{{?name,label}}")
    upload_base = upload_url_template.split("{")[0]

    existing_assets = {a["name"]: a["id"] for a in release.get("assets", [])}

    # 3. Upload each asset
    for fname, fpath in assets_to_upload.items():
        if fname in existing_assets:
            print(f"Deleting duplicate asset {fname} (ID {existing_assets[fname]})...")
            requests.delete(
                f"https://api.github.com/repos/{repo}/releases/assets/{existing_assets[fname]}",
                headers=headers,
            )
            time.sleep(1)

        size = os.path.getsize(fpath)
        print(f"Reading {fname} ({size} bytes)...")
        with open(fpath, "rb") as f:
            data = f.read()

        target_upload_url = f"{upload_base}?name={fname}"
        upload_headers = {
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/octet-stream",
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
        }
        print(f"Uploading {fname} to {target_upload_url}...")
        up_resp = requests.post(target_upload_url, headers=upload_headers, data=data)
        print(f"Upload response: {up_resp.status_code} {up_resp.text[:200]}")
        if up_resp.status_code not in (200, 201):
            print(f"ERROR uploading {fname}: {up_resp.status_code} {up_resp.text}", file=sys.stderr)
            sys.exit(1)
        print(f"Successfully uploaded {fname}!")

    # 4. Verification
    print("\nVerifying published release assets...")
    verify_resp = requests.get(f"https://api.github.com/repos/{repo}/releases/{release_id}/assets", headers=headers)
    verify_resp.raise_for_status()
    verified_assets = verify_resp.json()
    print(f"Confirmed {len(verified_assets)} asset(s) on GitHub Release:")
    for a in verified_assets:
        print(f"  ✓ {a['name']} ({a['size']} bytes) -> {a['browser_download_url']}")

    if not verified_assets:
        print("ERROR: Verification failed! No assets found on release.", file=sys.stderr)
        sys.exit(1)

    print("\nRelease publishing completed successfully!")

if __name__ == "__main__":
    main()
