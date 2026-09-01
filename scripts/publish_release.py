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

    # 1. Get or create release
    print(f"Fetching release for tag {tag} in {repo}...")
    resp = requests.get(f"https://api.github.com/repos/{repo}/releases/tags/{tag}", headers=headers)
    if resp.status_code == 200:
        release = resp.json()
    else:
        print(f"Creating release for {tag}...")
        resp = requests.post(
            f"https://api.github.com/repos/{repo}/releases",
            headers=headers,
            json={"tag_name": tag, "name": f"AOD Pomodoro {tag}", "body": f"AOD Pomodoro {tag} Release"},
        )
        resp.raise_for_status()
        release = resp.json()

    release_id = release["id"]
    print(f"Release ID: {release_id}")

    # Map existing assets to avoid duplicates
    existing_assets = {a["name"]: a["id"] for a in release.get("assets", [])}

    dist_dir = "dist"
    if not os.path.isdir(dist_dir):
        print(f"Directory {dist_dir} does not exist!", file=sys.stderr)
        sys.exit(1)

    for fname in sorted(os.listdir(dist_dir)):
        fpath = os.path.join(dist_dir, fname)
        if not os.path.isfile(fpath):
            continue

        if fname in existing_assets:
            print(f"Deleting existing asset {fname} (ID {existing_assets[fname]})...")
            del_resp = requests.delete(
                f"https://api.github.com/repos/{repo}/releases/assets/{existing_assets[fname]}",
                headers=headers,
            )
            del_resp.raise_for_status()

        print(f"Reading {fname} into memory ({os.path.getsize(fpath)} bytes)...")
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
        print(f"Upload result: {up_resp.status_code}")
        if up_resp.status_code not in (200, 201):
            print(f"Error uploading {fname}: {up_resp.text}", file=sys.stderr)
            sys.exit(1)
        print(f"Successfully uploaded {fname}!")

    print("All release assets uploaded successfully!")

if __name__ == "__main__":
    main()
