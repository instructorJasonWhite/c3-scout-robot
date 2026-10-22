// Actual Scout, RUN OPEN. Exercises settings/focus transitions and idle heartbeats.
const {chromium}=require('C:/Users/instr/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
let browser,page;const started=Date.now(),events=[];
const deadline=setTimeout(()=>{console.error('Settings bench exceeded 110 seconds');process.exit(2);},110000);
(async()=>{
 browser=await chromium.launch({headless:true,channel:'chrome',args:['--no-proxy-server']});
 page=await browser.newPage({viewport:{width:390,height:844}});page.setDefaultTimeout(4000);
 page.on('websocket',ws=>ws.on('close',()=>events.push({type:'socket-close',at:Date.now()-started})));
 await page.goto('http://192.168.4.1/',{waitUntil:'domcontentloaded'});await page.bringToFront();
 const status=()=>page.evaluate(async()=>await(await fetch('/status',{cache:'no-store'})).json());
 const initial=await status();assert.ok(initial.ready&&initial.oledReady&&!initial.connected);
 // Confirm this is the newly served fix, rather than an old cached page.
 assert.ok((await page.content()).includes("Queue its immediate zero through send()'s bounded backlog check"));
 await page.locator('#connect').click();
 await page.waitForFunction(()=>document.getElementById('state').textContent.includes('● Connected'));
 for(let i=0;i<30;i++){
  const pad=page.locator('#pad');await pad.focus();await page.keyboard.down('ArrowUp');
  await page.waitForFunction(()=>document.getElementById('left').textContent.includes('+'));
  if(i%2){
   await page.locator('#limit').evaluate((b,n)=>{b.value=String(n);b.dispatchEvent(new Event('input',{bubbles:true}));},35+(i%4)*15);
  }else{
   await page.locator(`#faces button[data-id="${i%11-1}"]`).evaluate(b=>{b.focus();b.click();});
  }
  await page.keyboard.up('ArrowUp');
  await page.waitForFunction(()=>document.getElementById('left').textContent==='L 0%');
  const j=await status();assert.equal(j.boot,initial.boot,'Scout restarted');assert.ok(j.connected&&j.oledReady,'Session/OLED ended during settings change');
 }
 console.log('PASS 30 drive-to-settings transitions without reconnect or restart');
 for(let i=0;i<35;i++){
  await page.locator(`#faces button[data-id="${i%11-1}"]`).evaluate(b=>b.click());
  await page.waitForTimeout(1000);
  const j=await status();assert.equal(j.boot,initial.boot);assert.ok(j.connected&&j.oledReady);
 }
 await page.locator('#faces button[data-id="-1"]').evaluate(b=>b.click());
 await page.locator('#stop').click();await page.waitForTimeout(100);
 const final=await status();assert.ok(!final.connected&&!final.enabled&&final.left===0&&final.right===0);
 const result={passed:true,elapsedMs:Date.now()-started,boot:initial.boot,settingsTransitions:30,idleFaceChanges:35,unexpectedSocketCloses:events.length,final};
 const out=path.resolve(__dirname,'../artifacts/verification/web-settings-fix');fs.mkdirSync(out,{recursive:true});
 fs.writeFileSync(path.join(out,'device-result.json'),JSON.stringify(result,null,2));
 console.log('PASS idle/settings soak, unchanged boot, OLED ready, final stopped');
})().catch(e=>{console.error(e);process.exitCode=1;}).finally(async()=>{
 clearTimeout(deadline);
 if(page)try{await page.evaluate(()=>fetch('/stop',{method:'POST',signal:AbortSignal.timeout(1200)}));}catch(_){}
 if(browser)await browser.close();
});
