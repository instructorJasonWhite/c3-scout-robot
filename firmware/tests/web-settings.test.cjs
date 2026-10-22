const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const ScoutControl=require('../web/control-core.js');
function app(){
  const nodes=new Map(),listeners={};
  function element(){return {textContent:'',style:{},dataset:{},value:'35',tagName:'DIV',classList:{toggle(){}},setAttribute(){},append(){},setPointerCapture(){},getBoundingClientRect(){return {left:0,top:0,width:200,height:200};}};}
  const node=id=>{if(!nodes.has(id))nodes.set(id,element());return nodes.get(id);};
  let ws;
  class Socket{
    static OPEN=1;static CONNECTING=0;
    constructor(){ws=this;this.readyState=1;this.bufferedAmount=0;this.sent=[];}
    send(data){this.sent.push(data);}close(){this.closed=true;}
  }
  const context={ScoutControl,WebSocket:Socket,performance:{now:()=>100},document:{getElementById:node,createElement:element,hidden:false,hasFocus:()=>true,addEventListener(n,f){listeners[n]=f;}},window:{addEventListener(n,f){listeners[n]=f;}},location:{hostname:'192.168.4.1'},requestAnimationFrame(){},setTimeout(){},clearTimeout(){},setInterval(f){this.tick=f;}};
  vm.runInNewContext(fs.readFileSync(path.join(__dirname,'../web/control-app.js'),'utf8'),context);
  node('connect').onclick();
  ws.onmessage({data:JSON.stringify({protocol:2,timeoutMs:400,browserTimeoutMs:1000,boot:7,ready:true,connected:true,oledReady:true,enabled:false,left:0,right:0,token:'0123456789abcdef0123456789abcdef'})});
  node('pad').onpointerdown({pointerId:1,button:0,clientX:100,clientY:10,preventDefault(){}});
  return {node,ws};
}
for(const action of ['speed','focus away from joystick'])test(`${action}: one buffered drive frame must allow an immediate zero without disconnect`,()=>{
  const {node,ws}=app();ws.bufferedAmount=45;
  if(action==='speed'){node('limit').value='60';node('limit').oninput();}else node('pad').onblur();
  assert.ok(!ws.closed,'ordinary buffered drive frame incorrectly closes the session');
  const zero=ws.sent.find(x=>x instanceof ArrayBuffer);
  assert.ok(zero,'release must send a zero frame');
  assert.equal(new DataView(zero).getInt16(41),0);
  assert.equal(new DataView(zero).getInt16(43),0);
});
test('release still disconnects when the transport backlog exceeds its bounded limit',()=>{
  const {node,ws}=app();ws.bufferedAmount=300;node('limit').oninput();assert.equal(ws.closed,true);
  assert.match(node('message').textContent,/send queue stalled.*300 bytes/);
});

test('closed transport reports a different cause from a stalled send queue',()=>{
  const {node,ws}=app();ws.readyState=3;node('limit').oninput();
  assert.equal(ws.closed,true);
  assert.match(node('message').textContent,/connection closed/);
});
