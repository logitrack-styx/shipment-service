import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import crypto from 'node:crypto';
import assert from 'node:assert/strict';
import {fileURLToPath} from 'node:url';

const service = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const collection = JSON.parse(fs.readFileSync(path.join(service,'docs/sprint3/apidog-coleccion.json'),'utf8'));
const env = Object.fromEntries(fs.readFileSync(path.join(service,'../.env'),'utf8').replace(/^\uFEFF/,'').split(/\r?\n/)
  .filter(s=>s.includes('=')&&!s.trim().startsWith('#')).map(s=>{
    const i=s.indexOf('='); return [s.slice(0,i).trim(),s.slice(i+1).trim().replace(/^['"]|['"]$/g,'')];
  }));
const variables = new Map(collection.variable.map(v=>[v.key,v.value]));
variables.set('aAdminUsername',env.ADMIN_USERNAME||'admin');
variables.set('aAdminPassword',env.ADMIN_PASSWORD);
variables.set('aClientPassword',crypto.randomBytes(24).toString('base64url'));
variables.set('aDemoShipmentId',crypto.randomUUID());
variables.set('aDemoCourierId',crypto.randomUUID());
const results=[];
let response;
function expect(actual) {
  const chain={
    eql(expected){assert.deepEqual(actual,expected);return chain;},
    a(type){assert.equal(type==='array'?Array.isArray(actual):typeof actual===type,true);return chain;},
    an(type){return chain.a(type);},
    lengthOf(n){assert.equal(actual.length,n);return chain;}
  };
  for(const p of ['to','be','have'])Object.defineProperty(chain,p,{get:()=>chain});
  return chain;
}
const pm={collectionVariables:{get:k=>variables.get(k),set:(k,v)=>{
  assert(variables.has(k),'Collection script tried to introduce a variable'); variables.set(k,v);
},unset:k=>variables.set(k,'')},expect,test:(name,fn)=>{
  try {fn();results.push({assertion:name,passed:true});}
  catch {results.push({assertion:name,passed:false});}
}};
function script(item,kind){
  for(const e of item.event||[])if(e.listen===kind)
    vm.runInNewContext(e.script.exec.join('\n'),{pm,atob:s=>Buffer.from(s,'base64').toString('binary')},{timeout:1000});
}
function replace(text){return text.replace(/\{\{([^}]+)\}\}/g,(_,key)=>{
  const value=variables.get(key);assert(value!==undefined&&value!=='','Missing required collection variable: '+key);return value;
});}
for(const folder of collection.item)for(const item of folder.item){
  script(item,'prerequest');
  const req=item.request;
  const headers=Object.fromEntries((req.header||[]).filter(h=>!h.disabled).map(h=>[h.key,replace(h.value)]));
  const auth=req.auth||collection.auth;
  if(auth?.type==='bearer')headers.Authorization='Bearer '+replace(auth.bearer.find(x=>x.key==='token').value);
  const res=await fetch(replace(typeof req.url==='string'?req.url:req.url.raw),{
    method:req.method,headers,body:req.body?.raw?replace(req.body.raw):undefined,
    signal:AbortSignal.timeout(25000)
  });
  const body=await res.json();
  pm.response={code:res.status,json:()=>body};
  script(item,'test');
}
fs.writeFileSync(path.join(service,'docs/sprint3/evidencias/apidog-run.json'),JSON.stringify({
  runner:'Node HTTP runner executing original Postman-compatible prerequest and test scripts',
  collection:collection.info.name,variables:collection.variable.map(v=>v.key),
  requests:collection.item.reduce((n,f)=>n+f.item.length,0),assertions:results
},null,2));
console.log('Collection assertions:',results.length,'; failures:',results.filter(r=>!r.passed).length);
if(results.some(r=>!r.passed))process.exitCode=1;
