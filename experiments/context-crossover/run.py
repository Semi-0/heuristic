"""Matched live crossover. Run from the repository root with a fresh evidence directory."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import random
import re
import sqlite3
import subprocess
import time

parser = argparse.ArgumentParser()
parser.add_argument("out", type=Path)
parser.add_argument("--mixed-only", action="store_true", help="Skip the independent direct lookup trials")
args = parser.parse_args()
out = args.out.resolve()
out.mkdir(parents=True, exist_ok=True)
if (out / "scores.jsonl").exists():
    raise SystemExit("Use a fresh directory")
bridge_dir = out / "bridge"
bridge_dir.mkdir(exist_ok=True)
role = ("You are Lain. Answer by invoking installed executable Lain code. Use prefix S-expressions, "
        "e.g. (function-name 12 3). Return only JSON with expr (a string) and evidence "
        "(the exact IDs for the source records and callable used, excluding distractors). "
        "When a fact is missing, make the smallest read that resolves it. You can return "
        '{"search":"keywords"} for a bounded journal index lookup or {"read":["id"]} to expand exact records. '
        "Once evidence supports an answer, submit the expression.")
start = time.monotonic
children = []

class Worker:
    def __init__(self, cmd, prefix, log):
        self.prefix = prefix
        self.proc = subprocess.Popen(cmd, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=log,
                                     text=True, bufsize=1)
        children.append(self.proc)
        assert self.receive()["ready"]

    def receive(self):
        for line in self.proc.stdout:
            if line.startswith(self.prefix):
                return json.loads(line[len(self.prefix):])
        raise RuntimeError(f"worker exited ({self.proc.wait()})")

    def send(self, payload):
        self.proc.stdin.write(json.dumps(payload) + "\n")
        self.proc.stdin.flush()
        return self.receive()

def rpc(op, **fields):
    reply = bridge.send({"op": op, "args": fields})
    if "error" in reply:
        raise ValueError(reply["error"])
    return reply["ok"]

docs = {}
index = sqlite3.connect(out / "index.sqlite")
index.execute("CREATE VIRTUAL TABLE archive USING fts5(id UNINDEXED, label, body)")
def record(key, label, body, parents=(), kind="user"):
    return dict(id=key, label=label, body=body, parents=list(parents), kind=kind)

def ingest(rows):
    for row in rows:
        assert row["id"] not in docs
        row["seq"] = len(docs) + 1
        docs[row["id"]] = row
    rpc("ingest", records=rows)
    with index:
        index.executemany("INSERT INTO archive VALUES(?,?,?)", [(r["id"],r["label"],r["body"]) for r in rows])
    with (out / "journal.jsonl").open("a") as f:
        for row in rows:
            print(json.dumps(row),file=f)

def shortlist(query):
    terms = sorted(set(re.findall(r"[a-z0-9]+",query.lower())) -
                   set("a an as and do for from how in its of on or the to use using what which with".split()))
    match = " OR ".join(f'"{t}"' for t in terms)
    return [r[0] for r in index.execute("SELECT id FROM archive WHERE archive MATCH ? ORDER BY bm25(archive,0,3,1),rowid LIMIT 8",(match,))] if terms else []

def select(query, k):
    ids = shortlist(query)
    if not ids:
        return [], []
    ranking = ranker.send({"instruction":"Select records directly supporting the requested task, applicable version, inputs or callable.",
                           "documents":[docs[key] for key in ids],"question":query})["scores"]
    return rpc("expand",ids=[r["id"] for r in ranking[:k]]), ranking

def provider(rows):
    return [{"role":"user","content":f'{r["id"]} {r["label"]}\n{r["body"]}'} for r in rows]

def trial(case, arm, size):
    question, required, symbol, expected = case
    tag = f"{size}-{arm}-{symbol}"
    t = start()
    projection = start()
    ranking = []
    if arm in {"lookup","selected"}:
        events, ranking = select(question, 1 if arm=="lookup" else 2)
        messages = [{"role":"system","content":role},*provider(events),{"role":"user","content":question}]
        visible = {r["id"] for r in events}
    else:
        messages = rpc("project",arm=arm,role=role,question=question)
        visible = set()
        for m in messages:
            fields=m["content"].split("\n",1)[0].split()
            if len(fields)>=3 and fields[2] in docs:
                visible.add(fields[2])
    initial_visible = sorted(visible & set(required))
    compacted = "Earlier context was compacted." in messages[0]["content"]
    project_seconds = start()-projection
    receipts=[]; outcome={}
    for n in range(5):
        key=f"{tag}-{n}"
        reply=rpc("solve",id=key,messages=messages)
        receipts.append({k:v for k,v in reply.items() if k!="text"})
        try:
            action=json.loads(reply["text"])
            if "expr" in action:
                executed=rpc("execute",id=key,expr=action["expr"])
                evidence=set(action["evidence"])
                called=[d["symbol"] for d in executed["called"]]
                outcome={"value":executed["value"],"expr":action["expr"],"application":executed["application"],
                         "decl":executed["called"],"cited":sorted(evidence),"correct":executed["value"]==expected,
                         "reuse":symbol in called,"supported":set(required)==evidence and evidence<=visible}
                break
            if "search" in action:
                result=[{k:docs[i][k] for k in ("id","label")} for i in shortlist(action["search"])]
            else:
                ids=action["read"]
                if not isinstance(ids,list) or not set(ids)<=docs.keys(): raise ValueError("invalid selection")
                result=rpc("expand",ids=ids)
                visible.update(r["id"] for r in result)
            messages.extend([{"role":"assistant","content":reply["text"]},{"role":"user","content":json.dumps(result)}])
        except (ValueError,KeyError,TypeError) as exc:
            outcome={"error":str(exc)}
            break
    row=dict(size=size,arm=arm,question=question,symbol=symbol,expected=expected,archive_records=len(docs),
             compacted=compacted,initial_required=initial_visible,ranking=ranking,projection_seconds=project_seconds,
             seconds=start()-t,calls=receipts,**outcome)
    row["pass"]=all(row.get(k) for k in ("correct","reuse","supported"))
    with (out/"scores.jsonl").open("a") as f: print(json.dumps(row),file=f)
    print(tag,row["pass"],round(row["seconds"],1),receipts[0]["usage"]["prompt_tokens"],compacted,flush=True)

library=[
    ("lib-a","linear-v1","Variable quantity charge: multiply a quantity by the order's unit price, returning cents.","catalog/library"),
    ("lib-b","percentage-v1","Apply a fractional surcharge to an amount and return the total.","catalog/library"),
    ("lib-c","legacy-v1","Superseded legacy quantity tariff with fixed two-cent unit price for archived jobs.","catalog/library"),
    ("lib-d","append-digit","Scanner accumulator: append one decimal digit to the previous numeric prefix.","behavior/scan"),
]
direct=[
    ("What is the variable quantity charge for 37 pieces priced at 19 cents apiece?",["lib-a"],"linear-v1",703),
    ("Under the superseded legacy quantity tariff, what did 43 pieces cost?",["lib-c"],"legacy-v1",86),
    ("A scanner accumulated prefix 714. Append digit 3. What is its new reading?",["lib-d"],"append-digit",7143)]
cases=[
    ("What variable quantity charge applied to the Indigo Finch order as originally invoiced, before the revision? Use the original order and its callable.",["ev-117","lib-a"],"linear-v1",1591),
    ("After the Indigo Scanner compiler repair, what reading results when the arithmetic ledger correction is appended to the CLI retry prefix? Use the post-repair receipt and all its dependencies.",["ev-731","ev-694","ev-528","lib-d"],"append-digit",6187)]
facts=[
    record("ev-117","Indigo Finch original invoice","37 pieces, 43 cents per piece. Apply the variable quantity charge.",["lib-a"]),
    record("ev-291","Indigo Finch revision","Revised quantity 39 pieces and unit price 41 cents. Supersedes the original for new quotes.",["ev-117"]),
    record("ev-502","Indigo Scanner first CLI result","Before the repair, CLI returned prefix 681.",kind="tool-output"),
    record("ev-528","Indigo Scanner CLI retry","CLI retry returned prefix 618.",kind="tool-output"),
    record("ev-694","Indigo Scanner arithmetic ledger correction","Replace trailing digit 4 with 7."),
    record("ev-731","Indigo Scanner compiler repair","Parenthesis fixed. Compose the CLI retry prefix with the corrected arithmetic ledger digit using the stored scanner accumulator.",["ev-528","ev-694","lib-d"])
]
rng=random.Random(98304)
templates=[
    ("Pricing amendment","This invoice records a quantity amendment for the packaging contract. The historical invoice remains available for reconciliation. The revised shipment contains {n} cartons. Apply the warehouse rate for this account only."),
    ("Compiler repair","The compiler rejected the submitted expression because its closing delimiter was missing. The operator corrected the expression and verified the sample application. The compiler recorded revision {n} for the separate installation."),
    ("Arithmetic revision","The accounting worksheet replaced the preliminary subtotal with the reconciled amount. The adjustment applies to ledger revision {n}. Carry the revised subtotal forward when calculating this account's balance."),
    ("CLI receipt","The command finished successfully and published a verification receipt. Its output belongs to independent batch {n}. The archived receipt records the original request and the observed completion status.")]
def fill(target):
    nchars=sum(len(r["label"])+len(r["body"]) for r in docs.values())
    batch=[]
    while nchars<target:
        i=len(docs)+len(batch); label,template=templates[i%4]
        account=rng.choice(["Meridian","Juniper","Chestnut","Coriander","Tangerine","Mulberry","Cypress"])
        body=template.format(n=rng.randrange(100,99999))+" "+rng.choice(["The supervisor recorded the change for the next reconciliation meeting.","The previous record remains available in the account archive.","The receipt was reviewed independently at the processing station."])
        row=record(f"audit-{i}",f"{account} {label} {i}",body,kind="tool-output" if i%4==3 else "user")
        batch.append(row);nchars+=len(row["body"])+len(row["label"])
    ingest(batch)

try:
    log=(out/"workers.log").open("a")
    bridge=Worker(["experiments/dependency-context/run.sh","crossover",str(bridge_dir)],"RPC ",log)
    ranker=Worker([os.environ["LAIN_RERANK_PYTHON"],"experiments/dependency-context/reranker.py",os.environ["LAIN_RERANK_MODEL"]],"",log)
    definitions=[]
    for key,sym,label,path in library:
        decl=rpc("install",file=f"test/fixtures/{path}.lain",symbol=sym)
        definitions.append(record(key,label,f'{decl["file"]} | {decl["source"]}'))
    ingest(definitions)
    if not args.mixed_only:
        for case in direct: trial(case,"lookup","files")
    ingest(facts)
    for i,(name,target) in enumerate([("short",6000),("middle",90000),("long",397000)]):
        fill(target)
        arms=["full","editor","selected"]
        for j,case in enumerate(cases):
            for k in range(3):trial(case,arms[(i+j+k)%3],name)
finally:
    for child in children:
        if child.stdin: child.stdin.close()
        if child.poll() is None: child.terminate()
        child.wait()
