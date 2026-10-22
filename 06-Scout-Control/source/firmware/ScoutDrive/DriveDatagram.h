#pragma once
#include "DriveCore.h"
namespace scout {
constexpr unsigned DRIVE_PACKET_SIZE = 45, ACK_PACKET_SIZE = 50;
struct DrivePacket { bool stop; char token[33]; uint32_t sequence; int left, right; };
inline uint32_t read32(const uint8_t* b) { return (uint32_t(b[0])<<24)|(uint32_t(b[1])<<16)|(uint32_t(b[2])<<8)|b[3]; }
inline int read16(const uint8_t* b) { unsigned n = (unsigned(b[0])<<8)|b[1]; return n >= 32768 ? int(n)-65536 : int(n); }
inline void write32(uint8_t* b, uint32_t n) { b[0]=uint8_t(n>>24); b[1]=uint8_t(n>>16); b[2]=uint8_t(n>>8); b[3]=uint8_t(n); }
inline void write16(uint8_t* b, int n) { b[0]=uint8_t(unsigned(n)>>8); b[1]=uint8_t(n); }
inline bool decodeDrive(const uint8_t* b, unsigned size, DrivePacket& p) {
  if (size!=DRIVE_PACKET_SIZE || b[0]!='S'||b[1]!='C'||b[2]!='T'||b[3]!='2'||(b[4]!='D'&&b[4]!='S')) return false;
  p.stop=b[4]=='S';
  for (unsigned i=0;i<32;++i) { char c=char(b[5+i]); if (!((c>='0'&&c<='9')||(c>='a'&&c<='f'))) return false; p.token[i]=c; }
  p.token[32]=0; p.sequence=read32(b+37); p.left=read16(b+41); p.right=read16(b+43);
  return (p.stop || p.sequence!=0) && p.left>=-100&&p.left<=100&&p.right>=-100&&p.right<=100;
}
inline void encodeAck(uint8_t* b, const DrivePacket& p, uint8_t code, uint32_t boot, uint8_t flags, int left, int right) {
  b[0]='A';b[1]='C';b[2]='K';b[3]='2';
  for(unsigned i=0;i<32;++i)b[4+i]=uint8_t(p.token[i]);
  write32(b+36,p.sequence);b[40]=code;write32(b+41,boot);b[45]=flags;write16(b+46,left);write16(b+48,right);
}
}
