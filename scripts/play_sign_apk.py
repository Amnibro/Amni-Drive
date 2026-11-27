import argparse
import base64
import json
import os
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
def b64u(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode("ascii")
def get_access_token(sa_path: str) -> str:
    sa = json.load(open(os.path.expanduser(sa_path), "r", encoding="utf-8"))
    now = int(time.time())
    header = b64u(json.dumps({"alg": "RS256", "typ": "JWT"}).encode("utf-8"))
    claim = b64u(json.dumps({
        "iss": sa["client_email"],
        "scope": "https://www.googleapis.com/auth/androidpublisher",
        "aud": "https://oauth2.googleapis.com/token",
        "iat": now,
        "exp": now + 3600
    }).encode("utf-8"))
    msg = f"{header}.{claim}".encode("utf-8")
    with tempfile.NamedTemporaryFile("w", delete=False) as tf:
        tf.write(sa["private_key"])
        key_file = tf.name
    try:
        sig = subprocess.check_output(["openssl", "dgst", "-sha256", "-sign", key_file], input=msg)
    finally:
        os.unlink(key_file)
    assertion = f"{header}.{claim}.{b64u(sig)}"
    req = urllib.request.Request(
        "https://oauth2.googleapis.com/token",
        data=f"grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion={assertion}".encode("utf-8"),
        headers={"Content-Type": "application/x-www-form-urlencoded"}
    )
    return json.loads(urllib.request.urlopen(req).read().decode("utf-8"))["access_token"]
def api_json(method: str, url: str, token: str, body=None, content_type="application/json"):
    data = None
    headers = {"Authorization": f"Bearer {token}"}
    if body is not None:
        if isinstance(body, (bytes, bytearray)):
            data = body
            headers["Content-Type"] = content_type
        else:
            data = json.dumps(body).encode("utf-8")
            headers["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=300) as resp:
            raw = resp.read().decode("utf-8")
            return json.loads(raw) if raw else {}
    except urllib.error.HTTPError as e:
        err_body = e.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"HTTP {e.code} {method} {url}: {err_body}")
def upload_and_fetch_universal(pkg: str, aab_path: str, track: str, out_apk: str, sa_path: str, release_name: str, notes: str):
    token = get_access_token(sa_path)
    base = f"https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{pkg}"
    edit = api_json("POST", f"{base}/edits", token, {})
    eid = edit["id"]
    print(f"[play] opened edit {eid} for {pkg}", flush=True)
    aab_bytes = open(aab_path, "rb").read()
    upload_url = f"https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/{pkg}/edits/{eid}/bundles?uploadType=media"
    print(f"[play] uploading {os.path.basename(aab_path)} ({len(aab_bytes)} bytes)...", flush=True)
    bundle = api_json("POST", upload_url, token, aab_bytes, content_type="application/octet-stream")
    vc = int(bundle["versionCode"])
    print(f"[play] uploaded bundle versionCode={vc} sha256={bundle.get('sha256')}", flush=True)
    for status in ("completed", "draft"):
        payload = {
            "track": track,
            "releases": [{
                "name": release_name or str(vc),
                "versionCodes": [str(vc)],
                "status": status,
                "releaseNotes": [{"language": "en-US", "text": notes[:480]}] if notes else []
            }]
        }
        try:
            api_json("PUT", f"{base}/edits/{eid}/tracks/{track}", token, payload)
            api_json("POST", f"{base}/edits/{eid}:commit", token, {})
            print(f"[play] committed track={track} status={status} versionCode={vc}", flush=True)
            break
        except RuntimeError as e:
            if status == "completed" and "draft" in str(e).lower():
                print(f"[play] app is in draft mode, retrying track status=draft...", flush=True)
                continue
            raise
    download_id = None
    cert_sha = None
    for attempt in range(1, 37):
        try:
            gen = api_json("GET", f"{base}/generatedApks/{vc}", token)
            for item in gen.get("generatedApks", []):
                uni = item.get("generatedUniversalApk")
                if uni and uni.get("downloadId"):
                    download_id = uni["downloadId"]
                    cert_sha = item.get("certificateSha256Hash")
                    break
            if download_id:
                break
        except RuntimeError as e:
            print(f"[play] poll {attempt}: waiting for generatedApks ({str(e)[:120]})", flush=True)
        time.sleep(5)
    if not download_id:
        raise RuntimeError(f"Timed out waiting for Play generatedUniversalApk for {pkg} versionCode={vc}")
    print(f"[play] universal APK ready! cert={cert_sha}", flush=True)
    dl_url = f"{base}/generatedApks/{vc}/downloads/{urllib.parse.quote(download_id, safe='')}:download?alt=media"
    req = urllib.request.Request(dl_url, headers={"Authorization": f"Bearer {token}"})
    os.makedirs(os.path.dirname(os.path.abspath(out_apk)), exist_ok=True)
    with urllib.request.urlopen(req, timeout=300) as resp, open(out_apk, "wb") as out:
        while True:
            chunk = resp.read(262144)
            if not chunk:
                break
            out.write(chunk)
    size = os.path.getsize(out_apk)
    print(f"[play] saved Play-signed universal APK -> {out_apk} ({size} bytes)", flush=True)
def main():
    p = argparse.ArgumentParser()
    p.add_argument("--package", required=True)
    p.add_argument("--aab", required=True)
    p.add_argument("--out", required=True)
    p.add_argument("--track", default="internal")
    p.add_argument("--sa", default="~/.config/amni/play-gsa.json")
    p.add_argument("--name", default="")
    p.add_argument("--notes", default="")
    args = p.parse_args()
    upload_and_fetch_universal(args.package, args.aab, args.track, args.out, args.sa, args.name, args.notes)
if __name__ == "__main__":
    main()
