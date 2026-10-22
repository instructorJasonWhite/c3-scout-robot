// Real device bench test. RUN must remain OPEN throughout this test.
const {chromium}=require('C:/Users/instr/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),net=require('node:net'),crypto=require('node:crypto');
const out=path.resolve(__dirname,'../artifacts/verification/web-control');fs.mkdirSync(out,{recursive:true});
let browser;const deadline=setTimeout(()=>{console.error('Device test exceeded 40-second deadline');process.exit(2);},40000);
(async()=>{
  browser=await chromium.launch({headless:true,channel:'chrome',args:['--no-proxy-server']});
  const page=await browser.newPage({viewport:{width:390,height:844}});
  page.setDefaultTimeout(3500);page.setDefaultNavigationTimeout(6500);
  // Windows/Chromium may announce a final network-change event just after DHCP.
  for(let attempt=0;;attempt++){
    try { await page.goto('http://192.168.4.1/',{waitUntil:'domcontentloaded'});break; }
    catch(e){if(attempt>=2||!String(e).includes('ERR_NETWORK_CHANGED'))throw e;await page.waitForTimeout(500);}
  }
  await page.bringToFront();
  const status=()=>page.evaluate(async()=>await(await fetch('/status',{cache:'no-store'})).json());
  const initial=await status();assert.ok(initial.ready&&initial.oledReady&&initial.browserControl);
  assert.equal(initial.connected,false);
  await page.locator('#connect').click();
  await page.waitForFunction(()=>document.getElementById('state').textContent.includes('● Connected'));
  assert.equal(await page.locator('#saveDirections').isDisabled(),true);
  const rejected=await page.evaluate(async()=>({code:(await fetch('/session',{method:'POST',body:''})).status}));
  assert.equal(rejected.code,409);
  const pad=await page.locator('#pad').boundingBox();
  await page.mouse.move(pad.x+pad.width/2,pad.y+pad.height*.08);await page.mouse.down();
  await page.waitForFunction(()=>document.getElementById('left').textContent.includes('+44'));
  for(const id of [0,9,-1]){
    await page.locator(`#faces button[data-id="${id}"]`).evaluate(b=>b.click());
    await page.waitForFunction(id=>document.querySelector(`#faces button[data-id="${id}"]`).getAttribute('aria-pressed')==='true',id);
    const j=await status();assert.equal(j.face,id);assert.equal(j.left,44);assert.equal(j.right,44);assert.ok(j.oledReady&&j.connected);
  }
  const moving=await status();assert.ok(moving.enabled);
  await page.mouse.up();await page.waitForFunction(()=>document.getElementById('left').textContent==='L 0%');
  assert.ok((await status()).connected);
  await page.screenshot({path:path.join(out,'device-browser.png'),fullPage:true});
  await page.locator('#stop').click();await page.waitForTimeout(100);
  let j=await status();assert.ok(!j.connected&&!j.enabled&&j.left===0&&j.right===0);
  await page.locator('#directions summary').click();await page.locator('#saveDirections').click();
  await page.waitForFunction(()=>document.getElementById('configStatus').textContent==='Directions saved on Scout');
  j=await status();assert.equal(j.leftReversed,initial.leftReversed);assert.equal(j.rightReversed,initial.rightReversed);
  console.log('PASS served page: drive +44/+44, Happy/Dizzy/Auto while held, release, Stop, direction save, Android session exclusion');
  await page.evaluate(()=>{
    window.rawOpen=()=>new Promise((resolve,reject)=>{
      const w=new WebSocket('ws://192.168.4.1:81/','arduino');
      const timer=setTimeout(()=>reject(Error('Raw socket open timed out')),1800);
      w.onopen=()=>{clearTimeout(timer);resolve(w);};w.onerror=()=>reject(Error('Raw socket open failed'));
    });
    window.rawRequest=(w,data,key)=>new Promise((resolve,reject)=>{
      const timer=setTimeout(()=>{w.removeEventListener('message',receive);reject(Error('Raw reply timed out'));},1800);
      function receive(event){const j=JSON.parse(event.data);if(key in j){clearTimeout(timer);w.removeEventListener('message',receive);resolve(j);}}
      w.addEventListener('message',receive);w.send(data);
    });
  });
  await page.evaluate(async()=>{
    window.oldSocket=await rawOpen();const claimed=await rawRequest(oldSocket,'C','token');
    await rawRequest(oldSocket,ScoutControl.encodeDrive(claimed.token,1,44,44),'ack');
  });
  await page.waitForTimeout(50);assert.equal((await status()).left,44);
  await page.waitForTimeout(1100);j=await status();assert.ok(!j.connected&&!j.enabled&&j.left===0&&j.oledReady);
  // Simulate the existing Android HTTP session, then disconnect the stale browser.
  const oldClose=await page.evaluate(async()=>{
    const r=await fetch('/session',{method:'POST',body:''});const s=await r.json();
    oldSocket.close();await new Promise(r=>setTimeout(r,60));
    const current=await(await fetch('/status',{cache:'no-store'})).json();
    await fetch('/stop',{method:'POST',body:''});
    return {code:r.status,created:s.connected,stillConnected:current.connected};
  });
  assert.equal(oldClose.code,200);assert.ok(oldClose.created&&oldClose.stillConnected);
  await page.evaluate(async()=>{
    window.ownerSocket=await rawOpen();const s=await rawRequest(ownerSocket,'C','token');
    let seq=0;window.ownerTick=setInterval(()=>{
      if(ownerSocket.readyState===WebSocket.OPEN&&!ownerSocket.bufferedAmount)
        ownerSocket.send(ScoutControl.encodeDrive(s.token,++seq,0,0));
    },50);
    const observer=await rawOpen();const denied=await rawRequest(observer,'C','error');
    if(!denied.error)throw Error('Observer unexpectedly claimed control');observer.close();
  });
  assert.ok((await status()).connected);
  await page.evaluate(()=>{clearInterval(ownerTick);ownerSocket.close();});
  await page.waitForTimeout(120);j=await status();assert.ok(!j.connected&&!j.enabled);
  await page.evaluate(async()=>{
    const bad=await rawOpen();await rawRequest(bad,'C','token');
    await rawRequest(bad,new Uint8Array([1,2,3]),'error');bad.close();
  });
  j=await status();assert.ok(!j.connected&&!j.enabled);
  const rejectedOrigin=await new Promise(resolve=>{
    let data='';const s=net.connect(81,'192.168.4.1');
    s.setTimeout(1500,()=>{s.destroy();resolve(!data.includes('101 Switching'));});
    s.on('connect',()=>s.write(`GET / HTTP/1.1\r\nHost: 192.168.4.1:81\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: ${crypto.randomBytes(16).toString('base64')}\r\nSec-WebSocket-Version: 13\r\nOrigin: http://example.invalid\r\n\r\n`));
    s.on('data',b=>{data+=b.toString();if(data.includes('\r\n\r\n'))s.end();});
    s.on('close',()=>resolve(!data.includes('101 Switching')));s.on('error',()=>resolve(false));
  });
  assert.ok(rejectedOrigin);
  fs.writeFileSync(path.join(out,'device-result.json'),JSON.stringify({
    testedAt:new Date().toISOString(),boot:initial.boot,drive:{left:moving.left,right:moving.right,oledReady:moving.oledReady},
    tested:['served compressed page','joystick','face changes while driving','release','Stop','direction save',
      'Android/browser exclusion','1000 ms browser lease expiry','stale owner disconnect','observer exclusion','disconnect stop','invalid frame stop','origin rejection'],
    final:await status()
  },null,2));
  console.log('PASS real WebSocket: lease expiry, stale owner, observer exclusion, disconnect, malformed frames, origin rejection; Scout stopped');
})().catch(e=>{console.error(e);process.exitCode=1;}).finally(async()=>{clearTimeout(deadline);if(browser)await browser.close();});
