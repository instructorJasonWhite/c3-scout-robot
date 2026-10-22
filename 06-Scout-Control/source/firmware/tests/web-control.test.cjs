const {test} = require('node:test');
const assert = require('node:assert/strict');
const {mix, encodeDrive, DriveLink} = require('../web/control-core.js');
const token = '0123456789abcdef0123456789abcdef';
const reply = (ack, extra={}) => ({ack, boot:7, ready:true, connected:true, ...extra});
test('mixer preserves Scout mapping, steering and bounded symmetry', () => {
  assert.deepEqual(mix(1,0,35),[44,44]);
  assert.deepEqual(mix(1,0,30),[0,0]);
  assert.deepEqual(mix(0,1,100),[-100,100]);
  assert.deepEqual(mix(NaN,0,100),[0,0]);
  for (let cap=0; cap<=100; cap++) for (const [f,t] of [[1,0],[0,1],[1,1],[.5,-.7]]) {
    const a=mix(f,t,cap), b=f===0 ? mix(0,-t,cap) : mix(-f,t,cap);
    for(let i=0;i<2;i++) {
      assert.ok(Math.abs(a[i])<=100);
      assert.ok(a[i]===0 || Math.abs(a[i])>=40);
      assert.equal(a[i]+b[i],0);
    }
  }
});
test('reverse arcs follow steering while forward arcs and stationary turns stay unchanged', () => {
  assert.deepEqual(mix(1,1,35),[0,44]);
  assert.deepEqual(mix(1,-1,35),[44,0]);
  assert.deepEqual(mix(-1,1,35),[0,-44]);
  assert.deepEqual(mix(-1,-1,35),[-44,0]);
  assert.deepEqual(mix(-1,0,35),[-44,-44]);
  assert.deepEqual(mix(0,1,35),[-44,44]);
  assert.deepEqual(mix(0,-1,35),[44,-44]);
});
test('binary frame matches protocol 2 byte order and signed targets', () => {
  const frame=encodeDrive(token,0xfedcba98,-44,100);
  assert.equal(frame.byteLength,45);
  const bytes=new Uint8Array(frame), view=new DataView(frame);
  assert.equal(String.fromCharCode(...bytes.slice(0,5)),'SCT2D');
  assert.equal(view.getUint32(37),0xfedcba98);
  assert.equal(view.getInt16(41),-44); assert.equal(view.getInt16(43),100);
});
test('continuous updates, release acknowledgement gate and neutral reconnect', () => {
  const link=new DriveLink(); link.begin(token,7,0); link.input(1,0);
  assert.ok(link.next(50,50,true)); assert.equal(link.pending,1);
  assert.ok(link.next(150,150,true));
  const zero=new DataView(link.release(160)); assert.equal(zero.getInt16(41),0);
  link.input(1,0); assert.deepEqual(link.target,[0,0]);
  link.ack(reply(2),170); assert.equal(link.releasePending,true);
  assert.ok(link.next(260,260,true));
  link.ack(reply(3),270); assert.equal(link.releasePending,false);
  link.input(1,0); assert.deepEqual(link.target,[44,44]);
  link.end(); link.begin(token,8,1000); assert.deepEqual(link.target,[0,0]);
});
test('UI stalls, hidden page, missing ack, boot change and expiry stop control', () => {
  for (const run of [
    l=>l.next(300,0,true), l=>l.next(50,50,false),
    l=>{l.next(50,50,true);l.next(800,800,true);},
    l=>{l.next(50,50,true);l.ack(reply(1,{boot:8}),60);},
    l=>{l.next(50,50,true);l.ack(reply(1,{connected:false}),60);}
  ]) { const l=new DriveLink();l.begin(token,7,0);assert.throws(()=>run(l)); }
});

test('600ms reply latency does not stall the 100ms command stream', () => {
  const l=new DriveLink();l.begin(token,7,0);l.input(1,0);
  for(let now=0;now<=2000;now+=100){
    if(now>=600) l.ack(reply((now-600)/100+1),now);
    const frame=l.next(now,now,true);
    assert.ok(frame,'drive stream stalled at '+now);
    assert.equal(new DataView(frame).getInt16(41),44);
  }
});

test('duplicate, future and stale acknowledgements cannot renew reply freshness', () => {
  const l=new DriveLink();l.begin(token,7,0);l.next(0,0,true);
  l.ack(reply(1),100);l.ack(reply(1),350);l.ack(reply(100),350);
  assert.throws(()=>l.next(900,900,true),/replies delayed/);
  const stale=new DriveLink();stale.begin(token,7,0);stale.next(0,0,true);
  stale.ack(reply(1),801);
  assert.throws(()=>stale.next(801,801,true),/replies delayed/);
});

test('periodic updates are 100ms apart and reply cutoff is exactly 800ms',()=>{
  const l=new DriveLink();l.begin(token,7,0);
  assert.ok(l.next(0,0,true));assert.equal(l.next(99,99,true),null);
  assert.ok(l.next(100,100,true));assert.ok(l.next(799,799,true));
  assert.throws(()=>l.next(800,800,true),/replies delayed/);
});
