const {chromium}=require('C:/Users/instr/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const fs=require('node:fs'),http=require('node:http'),assert=require('node:assert/strict'),path=require('node:path');
const root=path.resolve(__dirname,'..'),out=path.join(root,'artifacts/verification/web-control');
fs.mkdirSync(out,{recursive:true});
(async()=>{
  const html=fs.readFileSync(path.join(root,'artifacts/web-control/index.html'));
  const server=http.createServer((req,res)=>{res.setHeader('Content-Type','text/html');res.end(html);});
  await new Promise(r=>server.listen(8765,'127.0.0.1',r));
  let browser;
  try{
    browser=await chromium.launch({headless:true,channel:'chrome'});
    for(const [name,viewport] of [['portrait',{width:390,height:844}],['small-phone',{width:375,height:667}],['landscape',{width:844,height:390}]]){
      const page=await browser.newPage({viewport});
      let selected=-1,left=0,right=0,connected=false;
      const status=extra=>({protocol:2,version:'SCOUT 2',timeoutMs:400,browserTimeoutMs:1000,boot:7,ready:true,enabled:false,
        connected,left,right,face:selected,oledReady:true,leftReversed:true,rightReversed:true,...extra});
      await page.routeWebSocket('**',ws=>{
        ws.send(JSON.stringify(status()));
        ws.onMessage(data=>{
          if(typeof data==='string'){
            if(data==='C'){connected=true;ws.send(JSON.stringify(status({token:'0123456789abcdef0123456789abcdef'})));}
            if(data==='S'){connected=false;left=right=0;ws.send(JSON.stringify(status()));}
            if(data.startsWith('F,')){selected=Number(data.slice(2));ws.send(JSON.stringify(status()));}
          }else{
            const b=Buffer.from(data);left=b.readInt16BE(41);right=b.readInt16BE(43);
            ws.send(JSON.stringify(status({ack:b.readUInt32BE(37)})));
          }
        });
        ws.onClose(()=>{connected=false;left=right=0;});
      });
      await page.goto('http://127.0.0.1:8765');await page.bringToFront();
      await page.locator('#connect').click();
      await page.waitForFunction(()=>document.getElementById('state').textContent.includes('● Connected'));
      assert.equal(await page.locator('#faces button').count(),11);
      assert.equal(await page.locator('#saveDirections').isDisabled(),true);
      const stop=await page.locator('#stop').boundingBox();assert.ok(stop.y>=0&&stop.y+stop.height<=viewport.height+1,name+' Stop visibility');
      const pad=await page.locator('#pad').boundingBox();
      const firstFace=await page.locator('#faces button').first().boundingBox();
      assert.ok(firstFace.y+firstFace.height<=stop.y-3,name+' face row must stay above Stop');
      assert.ok(pad.y+pad.height<=stop.y-3,name+' joystick must stay above Stop');
      await page.mouse.move(pad.x+pad.width/2,pad.y+pad.height*.08);await page.mouse.down();
      await page.waitForFunction(()=>document.getElementById('left').textContent.includes('+44'));
      await page.locator('#faces button').filter({hasText:'Happy'}).evaluate(b=>b.click());
      await page.waitForFunction(()=>document.getElementById('faceStatus').textContent==='Happy');
      await page.mouse.up();await page.waitForFunction(()=>document.getElementById('left').textContent==='L 0%');
      assert.equal(connected,true);
      await page.screenshot({path:path.join(out,`browser-${name}.png`),fullPage:true});
      await page.locator('#stop').click();
      assert.equal(await page.locator('#connect').isEnabled(),true);
      await page.close();
      console.log('PASS '+name+': connect, eleven face buttons, drive+face, release, visible Stop, calibration lock');
    }
  }finally{if(browser)await browser.close();await new Promise(r=>server.close(r));}
})().catch(e=>{console.error(e);process.exitCode=1;});
