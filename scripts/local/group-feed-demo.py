#!/usr/bin/env python3
"""로컬 시연 회원(380001)의 피드를 실제 쿠키 인증으로 조회한다. 외부 호스트에는 요청하지 않는다."""
import argparse
import base64
import hashlib
import hmac
import json
import os
from pathlib import Path
import time
import urllib.parse
import urllib.request
import uuid


def encode(value):
    return base64.urlsafe_b64encode(json.dumps(value, separators=(",", ":")).encode()).decode().rstrip("=")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
    parser.add_argument("--after", type=Path, help="직전 응답 JSON 파일")
    parser.add_argument("--output", type=Path, help="응답 저장 경로")
    args = parser.parse_args()
    base = urllib.parse.urlsplit(args.base_url)
    if base.hostname not in {"localhost", "127.0.0.1", "::1"} or base.scheme not in {"http", "https"}:
        parser.error("로컬 서버 주소만 사용할 수 있습니다.")
    secret = os.environ.get("JWT_SECRET", "").encode()
    if len(secret) < 32:
        parser.error("서버와 동일한 JWT_SECRET 환경변수(32바이트 이상)를 설정하세요.")
    now = int(time.time())
    unsigned = encode({"alg": "HS256", "typ": "JWT"}) + "." + encode({
        "iss": "https://moeasy", "sub": "380001", "iat": now, "exp": now + 300,
        "jti": str(uuid.uuid4()), "token_type": "access",
    })
    signature = base64.urlsafe_b64encode(hmac.new(secret, unsigned.encode(), hashlib.sha256).digest()).decode().rstrip("=")
    params = {"sort": "RECOMMENDED", "query": "추천38"}
    if args.after:
        cursor = json.loads(args.after.read_text())["result"]["nextCursor"]
        if cursor is None:
            parser.error("직전 응답이 마지막 페이지입니다.")
        params["cursor"] = cursor
    url = args.base_url.rstrip("/") + "/api/v1/groups?" + urllib.parse.urlencode(params)
    request = urllib.request.Request(url, headers={"Cookie": "moeasy_access_token=" + unsigned + "." + signature})
    # 인증 쿠키가 리다이렉트를 통해 다른 호스트로 전달되지 않게 한다.
    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, req, fp, code, msg, headers, newurl):
            return None
    with urllib.request.build_opener(NoRedirect).open(request, timeout=15) as response:
        data = json.load(response)
    output = json.dumps(data, ensure_ascii=False, indent=2) + "\n"
    if args.output:
        args.output.write_text(output)
    print(output, end="")


if __name__ == "__main__":
    main()
