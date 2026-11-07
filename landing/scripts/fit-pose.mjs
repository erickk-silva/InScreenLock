// Fits a rigid perspective pose (yaw, pitch, roll about a device seen by a camera at distance D) to the outer corners of the phone in 01-HERO-APROVADO.png (1672x941).
// Result for D=16: yaw -28.1, pitch -19.9, roll -10.8 (RMS 4.3 px) -> INITIAL_POSE in src/lib/phone-motion.ts. Run: node scripts/fit-pose.mjs
// fit rigid perspective pose to reference corners (px in 1672x941 image)
const target = [[1095,118],[1422,100],[1296,793],[966,768]]; // TL TR BR BL
const W=2.5,H=5.0; const corners=[[-W/2,H/2],[W/2,H/2],[W/2,-H/2],[-W/2,-H/2]];
const rad=d=>d*Math.PI/180;
function rot(yaw,pitch,roll){ // R = Rz(roll)*Rx(pitch)*Ry(yaw)
  const cy=Math.cos(yaw),sy=Math.sin(yaw),cx=Math.cos(pitch),sx=Math.sin(pitch),cz=Math.cos(roll),sz=Math.sin(roll);
  const Ry=[[cy,0,sy],[0,1,0],[-sy,0,cy]], Rx=[[1,0,0],[0,cx,-sx],[0,sx,cx]], Rz=[[cz,-sz,0],[sz,cz,0],[0,0,1]];
  const m=(A,B)=>A.map((r,i)=>B[0].map((_,j)=>r.reduce((s,_,k)=>s+A[i][k]*B[k][j],0)));
  return m(Rz,m(Rx,Ry));
}
function project(p){ const [yaw,pitch,roll,D,s,tx,ty]=p; const R=rot(rad(yaw),rad(pitch),rad(roll));
  return corners.map(([x,y])=>{ const v=[x,y,0]; const r=R.map(row=>row[0]*v[0]+row[1]*v[1]+row[2]*v[2]); const z=D-r[2]; const f=D; return [tx+s*f*r[0]/z, ty-s*f*r[1]/z]; }); }
function cost(p){ const q=project(p); let c=0; for(let i=0;i<4;i++){c+=(q[i][0]-target[i][0])**2+(q[i][1]-target[i][1])**2;}
  return c; }
function fit(fixedD, seed){ let best=null; 
  let rnd=seed; const R=()=>{rnd=(rnd*16807)%2147483647;return rnd/2147483647;};
  for(let it=0;it<3000;it++){ let p=[ -60+R()*120, -40+R()*80, -40+R()*80, fixedD, 60+R()*120, 1200+R()*100, 440+R()*60 ];
    let c=cost(p), step=[10,10,10,0,10,20,20];
    for(let k=0;k<300;k++){ let improved=false; for(let j=0;j<7;j++){ if(j===3)continue; for(const sg of [-1,1]){ const q=p.slice(); q[j]+=sg*step[j]; const cc=cost(q); if(cc<c){p=q;c=cc;improved=true;} } } if(!improved){ step=step.map(s=>s*0.6); if(step[0]<0.001)break; } }
    if(!best||c<best.c) best={c,p}; }
  return best; }
for(const D of [7,9,12,16,24]){ const b=fit(D,12345+D); console.log('D',D,'rms',Math.sqrt(b.c/8).toFixed(2),'yaw,pitch,roll',b.p.slice(0,3).map(v=>v.toFixed(1)).join(','),'s',b.p[4].toFixed(1),'t',b.p[5].toFixed(0),b.p[6].toFixed(0)); }
