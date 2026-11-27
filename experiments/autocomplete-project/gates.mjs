import assert from 'node:assert/strict';
import {options, context, install} from '/project/editor/resources/public/lain-editor/editing.mjs';
const gate = Number(process.argv[2]);
const unique = 'az' + 'thistle';
const labels = (buf, env=[]) => options(buf,buf.length,env).map(x=>x.label);
if (gate===1) {
  const source = '(define '+unique+' 12) ; azBogus\n"azString" (az';
  assert.deepEqual(labels(source),[unique]);
  assert.deepEqual(labels('(quote (azGhost)) (az'),[]);
  assert.deepEqual(labels('(unboundCall azStray) (az'),[]);
  assert.deepEqual(context('(azthistle)',3),{from:1,to:10,prefix:'az'});
}
if (gate===2) {
  const source = '(define azOuter 7) (network [azSibling] (define azGone 8) azGone) (network [azActive] (define azInner 9) (az';
  const result = options(source,source.length,[{label:'azActive',origin:'environment'}, {label:'azBase',origin:'environment'}]);
  assert.deepEqual(result.map(x=>x.label).sort(),['azActive','azBase','azInner','azOuter']);
  assert.notEqual(result.find(x=>x.label==='azActive').origin,'environment');
  assert.deepEqual(labels('(network [azExpired] azExpired) (az'),[]);
}
if (gate>=3) {
  const callbacks=new Map(), pending=[];
  class Element {
    constructor(){this.dataset={};this.style={};this.children=[];this.attrs={};this.isConnected=true;}
    append(...els){this.children.push(...els)}
    replaceChildren(...els){this.children=[...els]}
    remove(){this.isConnected=false}
    addEventListener(){}
    setAttribute(k,v){this.attrs[k]=v}
    removeAttribute(k){delete this.attrs[k]}
    scrollIntoView(){} focus(){}
    getBoundingClientRect(){return {left:10,right:40,top:10,bottom:20}}
    matches(){return true} closest(){return this.pane}
    contains(){return false}
  }
  globalThis.HTMLTextAreaElement=Element;
  globalThis.ResizeObserver=class {disconnect(){} observe(){}};
  globalThis.requestAnimationFrame=()=>1;
  globalThis.window={addEventListener(){}};
  globalThis.innerWidth=900;globalThis.innerHeight=700;
  globalThis.document={body:new Element(),getElementById(){return null},createElement(){return new Element()},addEventListener(k,fn){callbacks.set(k,[...(callbacks.get(k)||[]),fn])}};
  globalThis.fetch=async(_,opts)=>new Promise(resolve=>pending.push({cmd:JSON.parse(opts.body),resolve}));
  install();
  const field=new Element();field.value='(az';field.selectionStart=field.selectionEnd=3;
  field.pane=new Element();field.pane.dataset={paneId:'pane-one',generation:'9',lexicalChild:'red'};
  const emit=(k,patch={})=>(callbacks.get(k)||[]).forEach(fn=>fn({target:field,preventDefault(){},stopImmediatePropagation(){},...patch}));
  const open=()=>emit('keydown',{ctrlKey:true,code:'Space'});
  const answer=async(request,child,name)=>{
    request.resolve({status:200,ok:true,json:async()=>({ok:true,result:{generation:'9',child,candidates:[{label:name,origin:'environment'}]}})});
    await new Promise(r=>setImmediate(r));
  };
  const visible=()=>document.body.children.filter(x=>x.isConnected&&x.className==='lain-completions').flatMap(x=>x.children.flatMap(c=>c.children.flatMap(o=>o.children.map(v=>v.textContent))));
  if (gate===3) {
    open();assert.equal(pending[0].cmd.child,'red');await answer(pending[0],'red','azOld');assert(visible().includes('azOld'));
    field.pane.dataset.lexicalChild='blue';field.pane.dataset.paneId='pane-two';
    open();assert.equal(pending[1].cmd.child,'blue');assert.equal(pending[1].cmd.pane,'pane-two');
    await answer(pending[1],'blue','azNew');assert(visible().includes('azNew'));assert(!visible().includes('azOld'));
    open();await answer(pending[2],'red','azWrong');assert(!visible().includes('azWrong'));
  } else {
    open();field.pane.dataset.lexicalChild='blue';
    await answer(pending[0],'red','azStale');assert(!visible().includes('azStale'));
    open();field.value='(azFresh';field.selectionStart=field.selectionEnd=8;emit('input');
    await answer(pending[1],'blue','azFreshStale');assert(!visible().includes('azFreshStale'));
    open();await answer(pending[2],'blue','azFreshValid');assert(visible().includes('azFreshValid'));
  }
}
