#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
CloudBase WebShell 分片批量下载器 —— 从微信云托管容器里把 app.jar 捞回来。

为什么需要它：WebShell 的「下载文件」单文件上限 10240 字节（约 10KB），
28MB 的 app.jar 必须先 split 成几千个小片，再逐片下载后本地拼接。

用法（在拿到控制台 Cookie 之后）：
    python fetch_parts.py --end 3174 --cookie "<Cookie: 后面的整串>" --out-dir ./parts

    加 --assemble 会在下载完成后自动拼成 app.jar 并校验文件头。

Cookie 怎么拿：见同目录 RECOVERY.md「取回通道」一节 ——
浏览器 F12 → Network → 找到任意一条 /download 请求 → Copy as cURL，
把 `-H 'Cookie: xxx'` 里 xxx 的内容整串复制过来即可。
"""

import argparse
import os
import sys
import time
import threading
import urllib.error
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed

# 这些标识来自云托管 WebShell 的下载链接（在浏览器地址栏里能看到）。
# 出于安全考虑不在仓库里硬编码，改用环境变量传入：
#   export WB_WS_HOST=sh-xxxx-webconsole.cloud.tencent.com
#   export WB_CLUSTER_ID=cls-xxxxxxxx
#   export WB_POD_ID=<pod 名>
#   export WB_CONTAINER_NAME=<容器名>
#   export WB_NAMESPACE=<命名空间>
BASE_URL = "https://{}/download".format(
    os.environ.get("WB_WS_HOST", "YOUR-WEBCONSOLE-HOST"))

DEFAULT_QUERY = {
    "clusterId": os.environ.get("WB_CLUSTER_ID", "YOUR-CLUSTER-ID"),
    "podId": os.environ.get("WB_POD_ID", "YOUR-POD-ID"),
    "containerName": os.environ.get("WB_CONTAINER_NAME", "YOUR-CONTAINER-NAME"),
    "namespace": os.environ.get("WB_NAMESPACE", "YOUR-NAMESPACE"),
    "tkeeks": "1",
    "shell": "sh",
    "tcb": "1",
}

UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")

AUTH_FAIL_BODY = "权限不足"

_lock = threading.Lock()
_done = 0
_failed = []


def build_url(base, remote_dir, name, query):
    q = dict(query)
    q["filePath"] = "{}/{}".format(remote_dir, name)
    # urllib.parse.urlencode 会把 / 编码成 %2F，与控制台一致
    return "{}?{}".format(base, urllib.parse.urlencode(q, safe=""))


def is_auth_fail(raw):
    """服务端把「权限不足」当 404 + text/plain 返回，body 恰好 12 字节。"""
    if not raw:
        return False
    try:
        return raw.decode("utf-8").strip() == AUTH_FAIL_BODY
    except UnicodeDecodeError:
        return False


def fetch_one(url, cookie, dest, retries=4, timeout=30):
    last = None
    for attempt in range(1, retries + 1):
        try:
            req = urllib.request.Request(url)
            req.add_header("User-Agent", UA)
            req.add_header("Referer", "https://console.cloud.tencent.com/")
            if cookie:
                req.add_header("Cookie", cookie)
            try:
                resp = urllib.request.urlopen(req, timeout=timeout)
            except urllib.error.HTTPError as herr:
                body = herr.read()
                if is_auth_fail(body):
                    raise PermissionError("AUTH") from None
                raise RuntimeError("HTTP {}".format(herr.code)) from None
            with resp:
                data = resp.read()
                code = resp.getcode()
            if code != 200:
                raise RuntimeError("HTTP {}".format(code))
            if len(data) == 0:
                raise RuntimeError("empty body")
            if is_auth_fail(data):
                raise PermissionError("AUTH")
            with open(dest, "wb") as fp:
                fp.write(data)
            return len(data)
        except PermissionError:
            raise
        except Exception as exc:  # noqa: BLE001
            last = exc
            if attempt < retries:
                time.sleep(min(2 ** attempt, 8))
    raise RuntimeError("{} (after {} tries)".format(last, retries))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default=BASE_URL)
    ap.add_argument("--remote-dir", default="/tmp/parts")
    ap.add_argument("--prefix", default="p_")
    ap.add_argument("--digits", type=int, default=5, help="split -a 的数字位数")
    ap.add_argument("--start", type=int, default=0, help="起始分片序号（含）")
    ap.add_argument("--end", type=int, required=True, help="结束分片序号（含）")
    ap.add_argument("--cookie", default=os.environ.get("CONSOLE_COOKIE", ""))
    ap.add_argument("--cookie-file", default="", help="也可以把 Cookie 存在文件里")
    ap.add_argument("--out-dir", required=True)
    ap.add_argument("--threads", type=int, default=5)
    ap.add_argument("--assemble", action="store_true", help="下完自动拼成 app.jar")
    ap.add_argument("--jar", default="app.jar")
    args = ap.parse_args()

    cookie = args.cookie
    if args.cookie_file and os.path.exists(args.cookie_file):
        with open(args.cookie_file, "r", encoding="utf-8") as fp:
            cookie = fp.read().strip()
    if not cookie:
        print("[!] 缺少 Cookie。请用 --cookie '<整串>' 或 --cookie-file 提供，"
              "否则腾讯云会返回「权限不足」。", file=sys.stderr)
        return 2

    os.makedirs(args.out_dir, exist_ok=True)
    indices = list(range(args.start, args.end + 1))
    total = len(indices)
    print("[i] 目标：{} 片（{} ~ {}），并发 {}，输出 {}"
          .format(total, args.start, args.end, args.threads, args.out_dir))

    def worker(idx):
        global _done
        name = "{}{:0{width}d}".format(args.prefix, idx, width=args.digits)
        dest = os.path.join(args.out_dir, name)
        if os.path.exists(dest) and os.path.getsize(dest) > 0:
            with _lock:
                _done += 1
            return name, 0
        url = build_url(args.base, args.remote_dir, name, DEFAULT_QUERY)
        size = fetch_one(url, cookie, dest)
        with _lock:
            _done += 1
            if _done % 100 == 0 or _done == total:
                print("    ... {}/{}".format(_done, total), flush=True)
        return name, size

    started = time.time()
    try:
        with ThreadPoolExecutor(max_workers=args.threads) as pool:
            futs = {pool.submit(worker, i): i for i in indices}
            for fut in as_completed(futs):
                idx = futs[fut]
                try:
                    fut.result()
                except PermissionError:
                    print("\n[!] 服务端返回「权限不足」——Cookie 已失效或不对，"
                          "请重新 Copy as cURL 再跑。", file=sys.stderr)
                    for f in futs:
                        f.cancel()
                    return 3
                except Exception as exc:  # noqa: BLE001
                    _failed.append((idx, str(exc)))
                    print("[x] 第 {} 片失败：{}".format(idx, exc), flush=True)
    except KeyboardInterrupt:
        print("\n[!] 已中断。已下载的片会保留，重跑同一命令即可续传。", file=sys.stderr)
        return 130

    cost = time.time() - started
    print("[i] 完成 {}/{}，用时 {:.1f}s".format(total - len(_failed), total, cost))
    if _failed:
        print("[!] 失败分片（重跑本命令会自动重试这些）：{}"
              .format([i for i, _ in _failed][:20]), file=sys.stderr)
        return 1

    if args.assemble:
        out = os.path.join(os.path.dirname(os.path.abspath(args.out_dir)), args.jar)
        with open(out, "wb") as fp:
            for idx in indices:
                name = "{}{:0{width}d}".format(args.prefix, idx, width=args.digits)
                with open(os.path.join(args.out_dir, name), "rb") as part:
                    fp.write(part.read())
        size = os.path.getsize(out)
        with open(out, "rb") as fp:
            head = fp.read(4)
        ok = head[:2] == b"PK"
        print("[i] 已拼接 -> {} ({:,} 字节, {:.1f} MB)".format(out, size, size / 1048576))
        print("[i] 文件头 {} -> {}".format(head, "OK (zip/jar)" if ok else "异常！"))
        if not ok:
            print("[!] 文件头不是 PK，可能分片缺失或顺序错乱，请检查 gaps。",
                  file=sys.stderr)
            return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
