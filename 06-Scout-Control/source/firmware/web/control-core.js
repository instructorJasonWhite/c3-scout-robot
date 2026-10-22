'use strict';
const ScoutControl = (() => {
  const UPDATE_MS=100, REPLY_TIMEOUT_MS=800;
  function mix(forward, turn, cap) {
    if (![forward, turn, cap].every(Number.isFinite)) return [0, 0];
    const radius = Math.hypot(forward, turn);
    if (radius <= .08) return [0, 0];
    const gain = (Math.min(1, radius) - .08) / .92 / radius;
    // Reverse steering with travel direction; keep stationary turns unchanged.
    const steering = forward < 0 ? -turn : turn;
    const left = (forward - steering) * gain, right = (forward + steering) * gain;
    const scale = Math.max(1, Math.abs(left), Math.abs(right));
    const limit = Math.max(0, Math.min(100, cap));
    return [left, right].map(v => {
      const command = Math.round(v / scale * limit), n = Math.abs(command);
      return n <= 30 ? 0 : Math.sign(command) * (40 + Math.floor((Math.min(100,n)-30)*60/70));
    });
  }
  function encodeDrive(token, sequence, left, right) {
    if (!/^[0-9a-f]{32}$/.test(token) || !Number.isInteger(sequence) || sequence < 1 || sequence > 0xffffffff ||
        ![left,right].every(n=>Number.isInteger(n) && Math.abs(n)<=100)) throw Error('Invalid drive command');
    const frame = new ArrayBuffer(45), bytes = new Uint8Array(frame), view = new DataView(frame);
    for (let i=0;i<5;i++) bytes[i]='SCT2D'.charCodeAt(i);
    for (let i=0;i<32;i++) bytes[5+i]=token.charCodeAt(i);
    view.setUint32(37,sequence); view.setInt16(41,left); view.setInt16(43,right);
    return frame;
  }
  class DriveLink {
    constructor() { this.cap=35; this.end(); }
    end() { this.active=false;this.token=null;this.target=[0,0];this.pending=0;this.releasePending=false;this.releaseSequence=0;this.acked=0;this.inflight=new Map(); }
    begin(token, boot, now) {
      encodeDrive(token,1,0,0);
      this.end();this.active=true;this.token=token;this.boot=boot;this.sequence=0;
      this.lastAck=now;this.lastSend=now-UPDATE_MS;this.sentAt=now;
    }
    input(forward,turn) { if(this.active && !this.releasePending) this.target=mix(forward,turn,this.cap); }
    packet(now, pair) {
      if(this.sequence>=0xffffffff) throw Error('Session complete · reconnect');
      const frame=encodeDrive(this.token,++this.sequence,...pair);
      for(const [seq,at] of this.inflight) if(now-at>=REPLY_TIMEOUT_MS)this.inflight.delete(seq);
      this.inflight.set(this.sequence,now);
      this.pending=this.sequence;this.sentAt=this.lastSend=now;
      return frame;
    }
    release(now) {
      this.target=[0,0]; if(!this.active) return null;
      this.releasePending=true;
      const frame=this.packet(now,this.target);
      this.releaseSequence=this.sequence;
      return frame;
    }
    next(now, renderAt, foreground) {
      if(!this.active) return null;
      if(!foreground || now-renderAt>250) throw Error('Page paused · reconnect to drive');
      if(now-this.lastAck>=REPLY_TIMEOUT_MS) throw Error('Scout replies delayed · reconnect');
      // Keep fresh commands flowing while earlier replies are in transit.
      // send() separately bounds unsent bytes; the ESP retains its 1000ms browser lease.
      if(now-this.lastSend<UPDATE_MS) return null;
      return this.packet(now,this.target);
    }
    ack(reply, now) {
      if(!this.active) return;
      if(reply.boot!==this.boot || !reply.ready || !reply.connected) throw Error('Scout session ended · reconnect');
      const sent=this.inflight.get(reply.ack);
      if(Number.isInteger(reply.ack)&&reply.ack>this.acked&&sent!==undefined&&now-sent<REPLY_TIMEOUT_MS&&now-this.lastAck<REPLY_TIMEOUT_MS) {
        this.acked=reply.ack;this.lastAck=now;
        for(const [seq] of this.inflight)if(seq<=this.acked)this.inflight.delete(seq);
        if(this.acked===this.pending)this.pending=0;
        if(this.releasePending&&this.acked>=this.releaseSequence)this.releasePending=false;
      }
    }
  }
  return {mix,encodeDrive,DriveLink};
})();
if(typeof module!=='undefined') module.exports=ScoutControl;
