'use strict';
(() => {
  const $=id=>document.getElementById(id), link=new ScoutControl.DriveLink();
  const names=['Happy','Laughing','Surprised','Angry','Sad','Sleepy','Skeptical','Winking','Cheeky','Dizzy'];
  let socket=null, waiting=false, saving=false, ready=false, oled=false, pointer=null, renderAt=performance.now(), claimTimer;
  const faceButtons=[];
  function text(id,value){$(id).textContent=value;}
  function knob(x=0,y=0){$('knob').style.transform=`translate(-50%,-50%) translate(${x}px,${y}px)`;}
  function paint(){
    $('connect').disabled=link.active||waiting||saving;
    $('saveDirections').disabled=link.active||waiting||saving||!ready;
    $('reverseLeft').disabled=$('reverseRight').disabled=link.active||waiting||saving;
    $('pad').classList.toggle('enabled',link.active);
    for(const button of faceButtons) button.disabled=!link.active||!oled;
    text('state',link.active?'● Connected':waiting?'◌ Connecting…':'○ Disconnected');
  }
  function stop(reason,send=true){
    const owned=link.active;
    link.end();waiting=false;clearTimeout(claimTimer);pointer=null;knob();
    text('left','L 0%');text('right','R 0%');text('message',reason);
    text('faceStatus','Connect to choose · swipe for more');
    for(const button of faceButtons)button.setAttribute('aria-pressed','false');
    if(socket){const old=socket;socket=null;
      if(send&&owned&&old.readyState===WebSocket.OPEN)try{old.send('S');}catch(_){}
      old.close();
    }
    paint();
  }
  function status(j){
    ready=j.ready===true;oled=j.oledReady===true;
    if(typeof j.leftReversed==='boolean')$('reverseLeft').checked=j.leftReversed;
    if(typeof j.rightReversed==='boolean')$('reverseRight').checked=j.rightReversed;
    if(link.active){
      text('left',`L ${j.left>0?'+':''}${j.left||0}%`);text('right',`R ${j.right>0?'+':''}${j.right||0}%`);
      text('faceStatus',oled?(j.face===-1?'Auto · all ten':names[j.face]||'Face ready'):'OLED unavailable');
      for(const b of faceButtons)b.setAttribute('aria-pressed',String(Number(b.dataset.id)===j.face));
    }
    paint();
  }
  function claim(){
    if(!waiting||!socket||socket.readyState!==WebSocket.OPEN)return;
    socket.send('C');clearTimeout(claimTimer);
    claimTimer=setTimeout(()=>stop('Connection not confirmed · try Connect again'),1800);
  }
  function open(){
    const ws=new WebSocket(`ws://${location.hostname}:81/`,'arduino');socket=ws;
    ws.onopen=()=>{if(socket===ws&&waiting)claim();};
    ws.onmessage=event=>{
      if(socket!==ws)return;
      try{
        if(typeof event.data!=='string'||event.data.length>4096)throw Error('Invalid Scout response');
        const j=JSON.parse(event.data);
        if(j.error)throw Error(j.error);
        if(j.protocol!==2||j.timeoutMs!==400||j.browserTimeoutMs!==1000||!Number.isInteger(j.boot))throw Error('Firmware update needed');
        if(j.token){
          if(!waiting||!j.connected||!j.ready||j.enabled||j.left||j.right)throw Error('Neutral session not confirmed');
          link.begin(j.token,j.boot,performance.now());waiting=false;clearTimeout(claimTimer);
          text('message','Connected · touch to drive');
        }else if(link.active){
          if(j.boot!==link.boot||!j.ready||!j.connected)throw Error('Scout session ended · reconnect');
          if(j.ack)link.ack(j,performance.now());
        }else if(!waiting){text('message',j.connected?'Scout already has a controller. Stop it before connecting.':'Ready when you are');}
        status(j);
      }catch(error){stop(error.message);}
    };
    ws.onerror=()=>{if(socket===ws)stop('Scout link unavailable · check Wi-Fi');};
    ws.onclose=()=>{if(socket===ws)stop('Scout disconnected · reconnect to drive',false);};
  }
  $('connect').onclick=()=>{
    if(link.active||waiting||saving||document.hidden)return;
    waiting=true;text('message','Creating a stopped control session…');paint();
    if(socket&&socket.readyState===WebSocket.OPEN)claim();
    else if(!socket||socket.readyState!==WebSocket.CONNECTING)open();
    clearTimeout(claimTimer);claimTimer=setTimeout(()=>stop('No response · join C3-Scout-Test and retry'),4000);
  };
  $('stop').onclick=()=>stop('Stopped · reconnect to drive');
  function send(frame){
    if(!socket||socket.readyState!==WebSocket.OPEN)throw Error('Scout connection closed · reconnect');
    if(socket.bufferedAmount>256)throw Error(`Scout send queue stalled (${socket.bufferedAmount} bytes) · reconnect`);
    socket.send(frame);
  }
  function release(){
    if(pointer===null)return;
    pointer=null;knob();
    try{
      // A normal drive frame may still be buffered when a setting takes focus.
      // Queue its immediate zero through send()'s bounded backlog check.
      const frame=link.release(performance.now());if(frame)send(frame);
    }catch(error){stop(error.message);}
  }
  function move(event){
    if(event.pointerId!==pointer)return;
    const r=$('pad').getBoundingClientRect(),radius=r.width*.4;
    let x=(event.clientX-r.left-r.width/2)/radius,y=(event.clientY-r.top-r.height/2)/radius;
    const scale=Math.max(1,Math.hypot(x,y));x/=scale;y/=scale;
    link.input(-y,x);knob(x*radius,y*radius);
  }
  $('pad').onpointerdown=event=>{
    if(!link.active||link.releasePending||pointer!==null||event.button>0)return;
    event.preventDefault();pointer=event.pointerId;$('pad').setPointerCapture(pointer);move(event);
  };
  $('pad').onpointermove=move;
  $('pad').onpointerup=$('pad').onpointercancel=$('pad').onlostpointercapture=event=>{if(event.pointerId===pointer)release();};
  const keys=new Set();
  $('pad').onkeydown=e=>{
    if(!['ArrowUp','ArrowDown','ArrowLeft','ArrowRight'].includes(e.key)||!link.active||link.releasePending)return;
    e.preventDefault();keys.add(e.key);pointer='keys';
    const f=Number(keys.has('ArrowUp'))-Number(keys.has('ArrowDown'));
    const t=Number(keys.has('ArrowRight'))-Number(keys.has('ArrowLeft'));
    link.input(f,t);const r=$('pad').clientWidth*.3;knob(t*r,-f*r);
  };
  $('pad').onkeyup=e=>{if(keys.has(e.key)){keys.clear();release();}};
  $('pad').onblur=()=>{keys.clear();release();};
  $('limit').oninput=()=>{release();link.cap=Number($('limit').value);text('limitValue',`${link.cap}%`);};
  for(let id=-1;id<names.length;id++){
    const b=document.createElement('button');b.type='button';b.dataset.id=String(id);
    b.textContent=id<0?'Auto':names[id];b.setAttribute('aria-pressed','false');
    b.onclick=()=>{if(link.active&&oled)try{send(`F,${id}`);}catch(e){stop(e.message);}};
    $('faces').append(b);faceButtons.push(b);
  }
  $('saveDirections').onclick=async()=>{
    if(link.active||waiting||saving)return;
    saving=true;paint();text('configStatus','Saving…');
    const abort=new AbortController(),timer=setTimeout(()=>abort.abort(),1800);
    try{
      const body=`leftReversed=${Number($('reverseLeft').checked)}&rightReversed=${Number($('reverseRight').checked)}`;
      const response=await fetch('/config',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body,signal:abort.signal});
      const j=await response.json();if(!response.ok||!j.ready||j.connected)throw Error(j.message||'Directions not confirmed');
      status(j);text('configStatus','Directions saved on Scout');
    }catch(e){text('configStatus',e.name==='AbortError'?'Save not confirmed · check connection':e.message);}
    finally{clearTimeout(timer);saving=false;paint();}
  };
  function render(now){renderAt=now;requestAnimationFrame(render);}requestAnimationFrame(render);
  setInterval(()=>{try{const frame=link.next(performance.now(),renderAt,!document.hidden&&document.hasFocus());if(frame)send(frame);}catch(e){stop(e.message);}},100);
  for(const name of ['blur','pagehide'])window.addEventListener(name,()=>{if(link.active||waiting)stop('Page left · reconnect to drive');});
  document.addEventListener('visibilitychange',()=>{if(document.hidden&&(link.active||waiting))stop('Page hidden · reconnect to drive');});
  window.addEventListener('keydown',e=>{if(e.code==='Space'&&e.target.tagName!=='INPUT'){e.preventDefault();stop('Stopped · reconnect to drive');}});
  paint();open();
})();
