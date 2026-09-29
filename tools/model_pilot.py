#!/usr/bin/env python3
"""Probe the pinned local model without retrieval; no output is a release grade."""
import json
import subprocess
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

QUESTIONS = [
    "Why is the sky red at sunset?",
    "How does a thunderstorm develop?",
    "How does the stomach digest food?",
    "Why do some bacteria become resistant to antibiotics?",
    "Why did the Soviet Union break apart?",
    "How does a solar cell turn light into electricity?",
    "How can I travel from London to Bristol by train?",
    "Name three currently open vegan restaurants in Lagos, Nigeria, with confirmed menus and hours.",
]
SYSTEM = ("Answer stable general-knowledge questions directly in two or three short sentences. "
          "Say when you do not know. Do not invent current businesses, menus, hours, train times, "
          "sources or citations. You have no internet access.")


def request(url, payload=None, timeout=10):
    data=None if payload is None else json.dumps(payload).encode()
    headers={} if data is None else {"Content-Type": "application/json"}
    with urllib.request.urlopen(urllib.request.Request(url, data=data, headers=headers), timeout=timeout) as response:
        return json.load(response)


def main():
    model=Path(sys.argv[1]); binary=Path(sys.argv[2]); output=Path(sys.argv[3])
    questions=QUESTIONS if len(sys.argv)<5 else [line.strip() for line in Path(sys.argv[4]).read_text(encoding="utf-8").splitlines() if line.strip() and not line.startswith("#")]
    port=18887
    server=subprocess.Popen([str(binary),"serve","-m",str(model),"--host","127.0.0.1",
        "--port",str(port),"-c","4096","-t","2","-ngl","0"],
        stdout=subprocess.DEVNULL,stderr=subprocess.STDOUT)
    try:
        for _ in range(180):
            if server.poll() is not None: raise RuntimeError(f"model server exited {server.returncode}")
            try:
                if request(f"http://127.0.0.1:{port}/health").get("status")=="ok": break
            except (urllib.error.URLError, TimeoutError, ValueError): pass
            time.sleep(1)
        else: raise TimeoutError("model server did not become ready")
        with output.open("w",encoding="utf-8") as stream:
            for question in questions:
                begin=time.monotonic()
                try:
                    result=request(f"http://127.0.0.1:{port}/v1/chat/completions",{
                        "messages":[{"role":"system","content":SYSTEM},{"role":"user","content":question}],
                        "temperature":0,"max_tokens":200,"stream":False},timeout=180)
                    answer=result["choices"][0]["message"]["content"]
                    item={"question":question,"answer":answer,"seconds":round(time.monotonic()-begin,2),
                          "finish_reason":result["choices"][0].get("finish_reason")}
                except Exception as error:
                    item={"question":question,"error":repr(error),"seconds":round(time.monotonic()-begin,2)}
                stream.write(json.dumps(item,ensure_ascii=False)+"\n"); stream.flush()
                print(question,item.get("seconds"),item.get("error","ok"),flush=True)
    finally:
        server.terminate()
        try: server.wait(timeout=10)
        except subprocess.TimeoutExpired: server.kill()


if __name__=="__main__": main()
