#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
从微信云托管 WebShell 的分片下载接口，把 /app/app.jar 完整捞回来并重建。

背景：
  WebShell「下载文件」单文件上限 10240 字节，app.jar 有 28MB，
  必须先 split 成数千片再逐片下载。实测命名规则为两段式：
      idx 0   ~ 649   -> p_ + base26(idx, 2)      (p_aa .. p_yz)
      idx 650 ~ ...   -> p_ + 'z' + base26(idx-650, 3)  (p_zaaa ..)
  末片序号由二分法实测确定，不靠猜。

用法：
  python recover_all.py --end-search      # 仅二分定位末片，快速预检
  python recover_all.py --cookie-file <f> --out-dir ./parts --jar ./app.jar
"""

import argparse
import os
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
import zipfile
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
QUERY = {
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
AUTH_FAIL = "权限不足"

N2 = 650            # 两位后缀分片数（实测 aa..yz）
CHUNK = 9216        # 单片字节数（-b 9k）

_lock = threading.Lock()
_stat = {"ok": 0, "miss": 0, "err": 0}


def base26(n, width):
    s = ""
    for _ in range(width):
        s = chr(ord("a") + (n % 26)) + s
        n //= 26
    return s


def name_of(idx):
    if idx < N2:
        return base26(idx, 2)
    return "z" + base26(idx - N2, 3)


def build_url(name, remote_dir):
    q = dict(QUERY)
    q["filePath"] = "{}/p_{}".format(remote_dir, name)
    return "{}?{}".format(BASE_URL, urllib.parse.urlencode(q, safe=""))


def request(url, cookie, timeout=30):
    req = urllib.request.Request(url)
    req.add_header("User-Agent", UA)
    req.add_header("Referer", "https://console.cloud.tencent.com/")
    if cookie:
        req.add_header("Cookie", cookie)
    try:
        resp = urllib.request.urlopen(req, timeout=timeout)
        with resp:
            return resp.getcode(), resp.read()
    except urllib.error.HTTPError as e:
        return e.code, e.read()


def exists(name, cookie, remote_dir):
    code, body = request(build_url(name, remote_dir), cookie)
    if code == 200 and body and len(body) > 0:
        return True
    if body and AUTH_FAIL in body.decode("utf-8", "ignore"):
        raise PermissionError("Cookie 失效")
    return False


def find_end(cookie, remote_dir, hi=6000):
    """二分定位最后一个四位后缀分片。"""
    if not exists(name_of(N2), cookie, remote_dir):
        print("[!] 起点 p_{} 不存在，命名假设不成立".format(name_of(N2)))
        return None
    if exists(name_of(hi), cookie, remote_dir):
        print("[!] 上界 {} 仍存在，请调大 hi".format(hi))
        return None
    lo, high = N2, hi
    while high - lo > 1:
        mid = (lo + high) // 2
        if exists(name_of(mid), cookie, remote_dir):
            lo = mid
        else:
            high = mid
    # 连续性抽查
    step = max(1, (lo - N2) // 12)
    gaps = [i for i in range(N2, lo + 1, step) if not exists(name_of(i), cookie, remote_dir)]
    print("[i] 末片 idx={} -> p_{}（共 {} 片）".format(lo, name_of(lo), lo + 1))
    if gaps:
        print("[!] 注意：抽样发现空洞 idx={}".format(gaps[:10]))
    return lo


def download(idx, cookie, remote_dir, out_dir, retries=4):
    name = name_of(idx)
    dest = os.path.join(out_dir, "{:05d}_{}".format(idx, name))
    if os.path.exists(dest) and os.path.getsize(dest) > 0:
        with _lock:
            _stat["ok"] += 1
        return idx, os.path.getsize(dest)
    last = None
    for attempt in range(retries):
        try:
            code, body = request(build_url(name, remote_dir), cookie)
            if code == 200 and body:
                if AUTH_FAIL in body.decode("utf-8", "ignore"):
                    raise PermissionError("Cookie 失效")
                with open(dest, "wb") as fp:
                    fp.write(body)
                with _lock:
                    _stat["ok"] += 1
                return idx, len(body)
            if code == 404:
                with _lock:
                    _stat["miss"] += 1
                print("[x] idx {} (p_{}) 缺失".format(idx, name), file=sys.stderr)
                return idx, 0
            raise RuntimeError("HTTP {}".format(code))
        except PermissionError:
            raise
        except Exception as exc:  # noqa: BLE001
            last = exc
            time.sleep(min(2 ** attempt, 6))
    with _lock:
        _stat["err"] += 1
    print("[x] idx {} 失败: {}".format(idx, last), file=sys.stderr)
    return idx, -1


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--cookie", default=os.environ.get("CONSOLE_COOKIE", ""))
    ap.add_argument("--cookie-file", default="")
    ap.add_argument("--remote-dir", default="/tmp/parts")
    ap.add_argument("--out-dir", default="./parts")
    ap.add_argument("--jar", default="./app.jar")
    ap.add_argument("--threads", type=int, default=8)
    ap.add_argument("--end", type=int, default=-1, help="已知末片 idx，跳过二分")
    ap.add_argument("--end-search", action="store_true", help="只做二分定位")
    args = ap.parse_args()

    cookie = args.cookie
    if args.cookie_file and os.path.exists(args.cookie_file):
        with open(args.cookie_file, encoding="utf-8") as fp:
            cookie = fp.read().strip()
    if not cookie:
        print("[!] 需要 Cookie", file=sys.stderr)
        return 2

    end = args.end
    if end < 0:
        t0 = time.time()
        end = find_end(cookie, args.remote_dir)
        print("[i] 二分耗时 {:.1f}s".format(time.time() - t0))
        if end is None:
            return 1
    if args.end_search:
        return 0

    os.makedirs(args.out_dir, exist_ok=True)
    total = end + 1
    print("[i] 开始下载 {} 片 -> {}".format(total, args.out_dir))
    t0 = time.time()
    try:
        with ThreadPoolExecutor(max_workers=args.threads) as pool:
            futs = [pool.submit(download, i, cookie, args.remote_dir, args.out_dir)
                    for i in range(total)]
            for n, fut in enumerate(as_completed(futs), 1):
                fut.result()
                if n % 250 == 0:
                    with _lock:
                        s = dict(_stat)
                    print("    {}/{}  ok={} miss={} err={}  {:.0f}s"
                          .format(n, total, s["ok"], s["miss"], s["err"], time.time() - t0),
                          flush=True)
    except PermissionError as exc:
        print("[!] {}".format(exc), file=sys.stderr)
        return 3
    except KeyboardInterrupt:
        print("[!] 中断，已下载的片保留，重跑可续传", file=sys.stderr)
        return 130

    print("[i] 下载结束：{}，用时 {:.0f}s".format(_stat, time.time() - t0))
    if _stat["miss"] or _stat["err"]:
        print("[!] 有缺失/失败分片，先处理再拼装", file=sys.stderr)
        return 1

    # 拼装
    names = ["{:05d}_{}".format(i, name_of(i)) for i in range(total)]
    sizes = [os.path.getsize(os.path.join(args.out_dir, n)) for n in names]
    bad = [(names[i], sizes[i]) for i in range(total - 1) if sizes[i] != CHUNK]
    if bad:
        print("[!] 非末片大小异常（应为 {}）：{}".format(CHUNK, bad[:10]), file=sys.stderr)
        return 1
    with open(args.jar, "wb") as out:
        for n in names:
            with open(os.path.join(args.out_dir, n), "rb") as fp:
                out.write(fp.read())
    size = os.path.getsize(args.jar)
    print("[i] 已拼装 {} : {:,} 字节 ({:.2f} MiB)".format(args.jar, size, size / 1048576))

    with open(args.jar, "rb") as fp:
        head = fp.read(4)
    print("[i] 文件头 {} ({})".format(head, "OK" if head[:2] == b"PK" else "异常"))
    if head[:2] != b"PK":
        return 1
    try:
        with zipfile.ZipFile(args.jar) as z:
            n = len(z.namelist())
            broken = z.testzip()
        print("[i] ZIP 校验通过：{} 个条目，损坏项={}".format(n, broken))
    except Exception as exc:  # noqa: BLE001
        print("[!] ZIP 校验失败：{}".format(exc), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
