"""Run sequential matched Soul project agents in private snapshots of two repositories."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import sqlite3
import subprocess as sp
import tarfile
import time
import urllib.request
from contextlib import ExitStack

BASE = Path(__file__).resolve().parents[2]
PROJECTS = BASE.parent
NODE = Path(shutil.which("node")).resolve()
CLOCK = time.monotonic
PINS = {"compiler": ("lain-compiler","343f08d"), "editor": ("lain-editor","bf181c4")}
TICKETS = [
    "Extend the existing textarea completion in the committed editor. On a partially typed symbol offer prior define names and supplied environment candidates. Suppress incidental reference words, quoted data, strings and comments. Keep existing mid-symbol replacement behavior and existing tests. Work via the normal editor entry point. Consult the compiler source for the language forms.",
    "Make that completion lexical. Visible network parameters and earlier local defines should be offered in an unfinished body, together with inherited names. Hide closed sibling locals. Inner bindings shadow outer/environment candidates, with origin distinguishing a local shadow. Keep public options(source,cursor,environment) compatible.",
    "Integrate fresh session names through the existing asynchronous completion UI. The mock gateway extends complete(pane,generation) with optional child from the pane's data-lexical-child and echoes it in result.child. Preserve existing untagged panes. Reopening must refresh names, isolate panes and reject a mismatched reply child. This benchmark supplies the gateway fixture.",
    "Late bug: a complete request issued in child red resolves after data-lexical-child changes to blue, without a new generation. A second bug occurs when the buffer is edited while the request is pending. The late candidates must never appear. Fresh completions must still work. Add a regression test for this race."
]
ROLE = """You are Lain, a coding agent in a private compiler/ and editor/ project. Use real file tools to implement the current ticket. Use small, testable edits. Once you find the relevant function, make one change and run a test before broadening your search. If an edit is too large for one action, split it. Return only a JSON array of actions (executed in order). Actions:
{"op":"search","text":"literal","root":"compiler"} or root "editor"; {"op":"files","root":"editor","text":"name substring"};
{"op":"read","path":"editor/...","line":1,"count":120}; {"op":"replace","path":"...","old":"exact unique substring","new":"replacement"}; {"op":"write","path":"...","text":"complete file"};
{"op":"test"} runs the editor editing tests, optionally path "editor/scripts/your.test.mjs"; {"op":"recall","text":"query"} lists earlier record IDs; {"op":"event","id":"e1"} reads one.
{"op":"submit"} requests ticket evaluation. On failure you may repair within the dispatch budget. Multiple reads or edits may be batched. Paths are relative to the paired project root. Test the actual code, add tests as appropriate and preserve earlier working behavior. Do not describe a patch instead of applying it."""
MAX_CALLS = 8
ALLOWANCE = 12288

def dump(path,obj):
    path.write_text(json.dumps(obj,indent=2)+"\n")

def append(path,obj):
    with path.open("a") as f: print(json.dumps(obj),file=f,flush=True)

def git(path,*args):
    return sp.check_output(["git","-C",str(path),*args],text=True).strip()

def checkout(repo,pin,path):
    sha = git(repo,"rev-parse",pin+"^{commit}")
    path.mkdir()
    proc=sp.Popen(["git","-C",str(repo),"archive",sha],stdout=sp.PIPE)
    with tarfile.open(fileobj=proc.stdout,mode="r|") as tar: tar.extractall(path,filter="data")
    assert proc.wait()==0
    return sha

def sandbox(work, command, extra=()):
    cmd=["bwrap","--unshare-all","--die-with-parent","--new-session","--clearenv","--ro-bind","/usr","/usr",
         "--ro-bind","/lib","/lib","--ro-bind",str(NODE),"/node","--dev","/dev","--proc","/proc",
         "--tmpfs","/tmp","--setenv","HOME","/tmp","--ro-bind",str(work),"/project","--chdir","/project/editor",*extra,"--","/node",*command]
    p=sp.run(["prlimit","--cpu=25","--nofile=256","--",*cmd],capture_output=True,text=True,timeout=35)
    return p.returncode,(p.stdout+p.stderr)[-3800:]

def gates(work,count):
    return [sandbox(work,[f"/gate.mjs",str(i)],["--ro-bind",str(BASE/"experiments/autocomplete-project/gates.mjs"),"/gate.mjs"])[0]==0 for i in range(1,count+1)]

class Pipe:
    def __init__(self,cmd,log,prefix=""):
        self.proc=sp.Popen(cmd,stdin=sp.PIPE,stdout=sp.PIPE,stderr=log,text=True,bufsize=1)
        self.prefix=prefix
        assert self.recv()["ready"]
    def recv(self):
        for line in self.proc.stdout:
            if line.startswith(self.prefix): return json.loads(line[len(self.prefix):])
        raise RuntimeError(f"Worker exited {self.proc.wait()}")
    def send(self,data):
        print(json.dumps(data,ensure_ascii=False),file=self.proc.stdin,flush=True)
        return self.recv()
    def close(self):
        self.proc.stdin.close()
        try:self.proc.wait(timeout=10)
        except sp.TimeoutExpired:self.proc.terminate();self.proc.wait()

def action_array(text):
    # Some completions reproduce the journal display envelope. Only unwrap known
    # transport decoration; prose and malformed/incomplete actions remain errors.
    text=re.sub(r"^(?:Fixture|Lain) tool-call [^\n]+\nLain actions\n", "", text.strip())
    if text.startswith("```json\n") and text.endswith("\n```"):
        text=text[8:-4]
    actions=json.loads(text)
    if not isinstance(actions,list) or not 1<=len(actions)<=24 or any(not isinstance(a,dict) for a in actions):
        raise ValueError("Expected 1–24 actions")
    return actions

def run_arm(out,arm,rank):
    os.environ["LAIN_ARCHIVE_EXTERNAL_TEXT"]="1"
    arm_dir=out/arm;arm_dir.mkdir()
    work=arm_dir/"project";work.mkdir()
    pins={name:checkout(PROJECTS/repo,pin,work/name) for name,(repo,pin) in PINS.items()}
    git(work,"init","-q");git(work,"add","-f","compiler","editor")
    def commit(msg):git(work,"-c","user.name=Lain Benchmark","-c","user.email=fixture@localhost","commit","-q","--allow-empty","-m",msg)
    commit("Pinned snapshots")
    baseline=git(work,"rev-parse","HEAD")
    public="scripts/editing.test.mjs"
    assert sandbox(work,["--test",public])[0]==0
    initial=gates(work,4)
    assert initial==[False]*4,initial
    packets=arm_dir/"packets";packets.mkdir()
    events={};lastfile={};serial=0
    fts=sqlite3.connect(":memory:");fts.execute("create virtual table idx using fts5(id UNINDEXED,label,body)")
    def hits(query,k):
        terms=list(dict.fromkeys(re.findall(r"[a-zA-Z0-9_]{3,}",query.lower())))[:45]
        return [r[0] for r in fts.execute("select id from idx where idx match ? order by bm25(idx,0,3,1),rowid limit ?",(" OR ".join('"'+t+'"' for t in terms),k))] if terms else []
    def safe(path):
        if not isinstance(path,str):raise ValueError("missing path")
        dest=(work/path).resolve()
        if not any(dest.is_relative_to(work/n) for n in PINS) or ".git" in dest.parts:raise ValueError("outside checkout")
        return dest
    with ExitStack() as stack:
        log=stack.enter_context((arm_dir/"worker.log").open("w"))
        engine=Pipe([str(BASE/"experiments/dependency-context/run.sh"),"crossover",str(packets)],log,"RPC ");stack.callback(engine.close)
        def rpc(op,**args):
            r=engine.send({"op":op,"args":args})
            if "error" in r:raise RuntimeError(r["error"])
            return r["ok"]
        def event(label,body,kind,parents=()):
            key="e"+str(len(events)+1)
            record=dict(id=key,label=label,body=body,kind=kind,parents=list(dict.fromkeys(parents)),seq=len(events)+1)
            rpc("ingest",records=[record]);events[key]=record
            fts.execute("insert into idx values(?,?,?)",(key,label,body))
            append(arm_dir/"events.jsonl",record)
            return key
        prior=[];dropped=set();overflow=False
        for idx,ticket in enumerate(TICKETS,1):
            opened=CLOCK()
            ticketid=event("Ticket "+str(idx),ticket,"user")
            recent=ticketid;complete=False
            for turn in range(1,MAX_CALLS+1):
                serial+=1
                query=f"Ticket {idx}. {ticket}\nDispatch {turn} of {MAX_CALLS}. Take the next action."
                t=CLOCK()
                if arm=="selected":
                    ids=hits(ticket+" "+events[recent]['label'],8)
                    ranked=rank.send({"question":query,"instruction":"Find earlier repository evidence, decisions or tool feedback needed for this software change.","documents":[dict(id=i,label=events[i]['label'],body=events[i]['body'][:1200]) for i in ids]})
                    roots=[x["id"] for x in ranked["scores"][:2]]
                    # Resolve transitive provenance; carry the latest feedback and root records within a character budget.
                    expanded=[events[r['id']] for r in rpc("expand",ids=list(dict.fromkeys(roots+[recent])))]
                    byid={r['id']:r for r in expanded}; chosen=set();space=60000
                    for key in [recent,*roots,*reversed(list(byid))]:
                        if key in chosen:continue
                        n=len(byid[key]["body"])+len(byid[key]["label"])+40
                        if n<=space:chosen.add(key);space-=n
                    msgs=[{"role":"system","content":ROLE}]+[{"role":"user","content":f'{r["id"]} {r["label"]}\n{r["body"]}'} for r in expanded if r["id"] in chosen]+[{"role":"user","content":query}]
                else:
                    msgs=rpc("project",role=ROLE,question=query,arm=arm)
                # Present previous assistant actions exactly as the tool protocol
                # requires them to be emitted, without an invented fixture speaker.
                for m in msgs:
                    match=re.match(r"^(?:Fixture tool-call e[^\n]*|e\d+ Lain actions)\n(?:Lain actions\n)?",m["content"])
                    if match:
                        m["role"]="assistant"
                        m["content"]=m["content"][match.end():]
                compacted="Earlier context was compacted." in msgs[0]["content"]
                if compacted:
                    shown=set(re.findall(r"^.*?\b(e\d+)\b", "\n".join(m['content'].split('\n',1)[0] for m in msgs),re.M))
                    evicted=set(events)-shown
                else:evicted=set()
                ptime=CLOCK()-t
                req=urllib.request.Request("http://127.0.0.1:18320/tokenize",data=json.dumps({"model":"qwen3.8-flash-next","messages":msgs,"chat_template_kwargs":{"enable_thinking":True,"reasoning_effort":"xhigh"}}).encode(),headers={"Content-Type":"application/json"})
                with urllib.request.urlopen(req,timeout=60) as resp: token_count=json.load(resp)["count"]
                if token_count+ALLOWANCE>131072:
                    append(arm_dir/"calls.jsonl",dict(ticket=idx,turn=turn,overflow=True,input=token_count));overflow=True;break
                ans=rpc("solve",id=f"t{idx}-r{turn}",messages=msgs)
                append(arm_dir/"calls.jsonl",dict(ticket=idx,turn=turn,context_seconds=ptime,compacted=compacted,new_evictions=len(evicted-dropped),input=token_count,**{k:v for k,v in ans.items() if k!="text"}))
                dropped|=evicted
                callid=event("Lain actions",ans["text"],"tool-call",[ticketid,recent])
                try:
                    if ans["finish-reason"]=="length":
                        raise ValueError("Generation limit reached before a usable action. Return one small action now, such as a targeted replace.")
                    acts=action_array(ans["text"])
                except ValueError as e:
                    recent=event("Response format feedback",str(e),"tool-output",[callid]);continue
                results=[];parents=[callid];paths=[]
                for act in acts:
                    try:
                        op=act["op"]
                        if op in ("read","replace","write"):
                            path=act["path"];dest=safe(path);paths.append(path)
                            if path in lastfile:parents.append(lastfile[path])
                            if op=="read":
                                i=max(0,int(act.get("line",1))-1);lines=dest.read_text().splitlines();res="\n".join(lines[i:i+min(int(act.get("count",120)),300)])
                            elif op=="write":
                                dest.parent.mkdir(parents=True,exist_ok=True);dest.write_text(act["text"]);res="written"
                            else:
                                old=act["old"];text=dest.read_text()
                                if not old or text.count(old)!=1:raise ValueError("old text must occur exactly once")
                                dest.write_text(text.replace(old,act["new"],1));res="replaced"
                        elif op in ("files","search"):
                            root=act.get("root","editor")
                            if root not in PINS:raise ValueError("unknown root")
                            if op=="files":
                                files=sp.check_output(["rg","--files",root],cwd=work,text=True).splitlines()
                                res="\n".join(p for p in files if act.get("text","").lower() in p.lower())
                            else:
                                res=sp.run(["rg","-n","-i","-F","--",act["text"],root],cwd=work,stdout=sp.PIPE,text=True).stdout
                        elif op=="test":
                            path=safe(act.get("path","editor/"+public))
                            code,output=sandbox(work,["--test","/project/"+str(path.relative_to(work))]);res=f"exit={code}\n{output}"
                        elif op=="recall":res=str([(i,events[i]["label"]) for i in hits(act["text"],8)])
                        elif op=="event":res=events[act["id"]]["body"];parents.append(act["id"])
                        elif op=="submit":
                            passed=gates(work,idx);code,_=sandbox(work,["--test",public])
                            complete=all(passed) and code==0
                            res=f"Ticket gates 1..{idx}: {passed}; existing tests exit {code}"
                        else:raise ValueError("unknown op")
                    except (ValueError,KeyError,OSError,TypeError,sp.TimeoutExpired) as e:res="Tool error: "+str(e)
                    results.append(str(act.get("op"))+" "+str(act.get("path",act.get("text","")))[:140]+"\n"+str(res)[:12500])
                    if complete:break
                recent=event("Tool feedback "+", ".join(paths)[:300],("\n\n".join(results))[:32000],"tool-output",parents)
                for path in paths:lastfile[path]=recent
                print(arm,idx,turn,[a.get("op") for a in acts],"passed" if complete else "",flush=True)
                if complete:break
            passed=gates(work,idx)
            row=dict(ticket=idx,gates=passed,seconds=CLOCK()-opened,overflow=overflow,dispatches=turn)
            prior.append(row);append(arm_dir/"milestones.jsonl",row)
            if overflow:break
        git(work,"add","-A");commit("Agent output");final=git(work,"rev-parse","HEAD")
        clean=arm_dir/"verification";checkout(work,final,clean)
        immutable=arm_dir/"original.test.mjs";immutable.write_text(git(work,"show",baseline+":editor/"+public)+"\n")
        code,output=sandbox(clean,["--test",public],["--ro-bind",str(immutable),"/project/editor/"+public])
        finalg=gates(clean,4)
        (arm_dir/"changes.patch").write_text(sp.check_output(["git","-C",str(work),"diff",baseline,final],text=True))
        dump(arm_dir/"result.json",dict(pins=pins,baseline=initial,final=finalg,regression_exit=code,regression_output=output,milestones=prior,commit=final))
        print("FINAL",arm,finalg,code,flush=True)

def main():
    ap=argparse.ArgumentParser();ap.add_argument("out",type=Path);ap.add_argument("arms",nargs="*",default=["full","editor","selected"]);args=ap.parse_args()
    out=args.out.resolve();out.mkdir(exist_ok=True,parents=True)
    dump(out/"protocol.json",dict(tickets=TICKETS,calls_per_ticket=MAX_CALLS,max_completion=ALLOWANCE,gates_sha256=hashlib.sha256((BASE/"experiments/autocomplete-project/gates.mjs").read_bytes()).hexdigest()))
    for arm in args.arms:
        with ExitStack() as stack:
            rank=None
            if arm=="selected":
                log=stack.enter_context((out/"ranker.log").open("w"))
                rank=Pipe([os.environ["LAIN_RERANK_PYTHON"],str(BASE/"experiments/dependency-context/reranker.py"),os.environ["LAIN_RERANK_MODEL"]],log);stack.callback(rank.close)
            run_arm(out,arm,rank)
if __name__=="__main__":main()
