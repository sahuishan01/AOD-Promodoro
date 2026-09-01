import os
import sys
import requests

def main():
    token = os.environ.get("GITHUB_TOKEN")
    repo = os.environ.get("GITHUB_REPOSITORY")
    tag = os.environ.get("GITHUB_REF_NAME")

    if not all([token, repo, tag]):
        print("Missing GITHUB_TOKEN, GITHUB_REPOSITORY, or GITHUB_REF_NAME", file=sys.stderr)
        sys.exit(1)

    headers = {
        "Authorization": f"Bearer {token}",
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
    }

    # 1. Search for all built APK and AAB binaries under app/build/outputs
    print("Searching for built APK and AAB binaries under app/build/outputs...")
    assets_to_upload = {}
    for root, dirs, files in os.walk("."):
        clean_root = root.replace("\\", "/")
        if "outputs" not in clean_root or "baselineProfiles" in clean_root:
            continue
        for f in files:
            if (f.endswith(".apk") or f.endswith(".aab")) and not f.startswith("."):
                fpath = os.path.join(root, f)
                assets_to_upload[f] = fpath

    print(f"Found {len(assets_to_upload)} release asset(s):")
    for name, path in assets_to_upload.items():
        print(f"  • {name} ({os.path.getsize(path)} bytes) at {path}")

    if not assets_to_upload:
        print("ERROR: No APK or AAB binaries found to publish!", file=sys.stderr)
        sys.exit(1)

    # 2. Get or create release
    print(f"\nFetching release for tag {tag} in {repo}...")
    resp = requests.get(f"https://api.github.com/repos/{repo}/releases/tags/{tag}", headers=headers)
    if resp.status_code == 200:
        release = resp.json()
        print(f"Existing release found (ID {release['id']}).")
    else:
        print(f"Creating new release for {tag}...")
        resp = requests.post(
            f"https://api.github.com/repos/{repo}/releases",
            headers=headers,
            json={"tag_name": tag, "name": f"AOD Pomodoro {tag}", "body": f"AOD Pomodoro {tag} Release"},
        )
        resp.raise_for_status()
        release = resp.json()
        print(f"Created release (ID {release['id']}).")

    release_id = release["id"]

    # Map existing assets to delete before replacing
    existing_assets = {a["name"]: a["id"] for a in release.get("assets", [])}

    # 3. Upload each asset
    for fname, fpath in assets_to_upload.items():
        if fname in existing_assets:
            print(f"Deleting duplicate existing asset {fname} (ID {existing_assets[fname]})...")
            del_resp = requests.delete(
                f"https://api.github.com/repos/{repo}/releases/assets/{existing_assets[fname]}",
                headers=headers,
            )
            del_resp.raise_for_status()

        size = os.path.getsize(fpath)
        print(f"Reading {fname} into memory ({size} bytes)...")
        with open(fpath, "rb") as f:
            data = f.read()

        upload_url = f"https://uploads.github.com/repos/{repo}/releases/{release_id}/assets?name={fname}"
        upload_headers = {
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/octet-stream",
            "Content-Length": str(len(data)),
        }
        print(f"Uploading {fname} to {upload_url}...")
        up_resp = requests.post(upload_url, headers=upload_headers, data=data)
        print(f"Upload response status: {up_resp.status_code}")
        if up_resp.status_code not in (200, 201):
            print(f"Error uploading {fname}: {up_resp.text}", file=sys.stderr)
            sys.exit(1)
        print(f"Successfully uploaded {fname}!")

    # 4. Final Verification
    print("\nVerifying published release assets on GitHub...")
    verify_resp = requests.get(f"https://api.github.com/repos/{repo}/releases/{release_id}/assets", headers=headers)
    verify_resp.raise_for_status()
    verified_assets = verify_resp.json()
    print(f"Confirmed {len(verified_assets)} asset(s) on GitHub Release:")
    for a in verified_assets:
        print(f"  ✓ {a['name']} ({a['size']} bytes) -> {a['browser_download_url']}")

    if not verified_assets:
        print("ERROR: Verification failed! No assets listed on release.", file=sys.stderr)
        sys.exit(1)

    print("\nRelease publishing completed successfully!")

if __name__ == "__main__":
    main()
