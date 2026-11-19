const fs = require('fs');
const scan = fs.readFileSync(__dirname + '/SCAN_JS.js', 'utf8');
const container = {id:'player',className:'video-player',parentElement:null,getAttribute(){return null;},querySelectorAll(){return[];}};
const video = {
  tagName:'VIDEO',parentElement:container,currentSrc:'blob:https://xhamster.example/video',paused:false,ended:false,
  readyState:4,currentTime:18,duration:600,videoWidth:1280,videoHeight:720,poster:'',__vbUserInitiated:true,
  getAttribute(name){return name==='src'?this.currentSrc:null;},
  getBoundingClientRect(){return{left:100,top:80,right:900,bottom:530,width:800,height:450};},
  querySelectorAll(){return[];},addEventListener(){}
};
const document={baseURI:'https://xhamster.example/videos/123',querySelectorAll(s){return s==='video'?[video]:[];},addEventListener(){}};
const window={
  innerWidth:1280,innerHeight:720,location:{href:document.baseURI},
  flashvars_123:{mediaDefinitions:[]},
  initials:{
    videoModel:{duration:600,sources:{
      mp4:{'720p':{url:'https://cdn.example/xh-main-720.mp4',quality:'720p',width:1280,height:720},'480p':'https://cdn.example/xh-main-480.mp4'},
      hls:{'2160p':{url:'https://cdn.example/xh-main-2160.m3u8',resolution:'3840x2160'}},
      download:{'720p':'https://cdn.example/xh-download.mp4'}
    }},
    xplayerSettings:{sources:{standard:{h264:'b'.repeat(64)}}}
  },
  VBVideoGesture:{onVideoEvent(){},onVideoGesture(){}}
};
const run=new Function('window','document','location','getComputedStyle','const result='+scan.trim().replace(/;\s*$/,'')+';return JSON.parse(result);');
const result=run(window,document,{href:document.baseURI},()=>({display:'block',visibility:'visible',opacity:'1'}));
if(!result.p) throw new Error('Main frame video was not selected');
if(!result.p.xhamsterStateFound || result.p.xhamsterSourceCount < 3) throw new Error('Main frame xHamster initials state was not read');
for(const file of ['xh-main-720.mp4','xh-main-480.mp4','xh-main-2160.m3u8']) {
  const url=result.p.sources.find(value=>value.includes(file));
  if(!url) throw new Error('Missing xHamster rendition '+file);
  const meta=result.p.sourceMeta.find(value=>value.url===url);
  if(!meta || meta.captureSource!=='player-xhamster') throw new Error('Missing source metadata '+file);
}
if(result.p.xhamsterQualities.indexOf('2160p')<0) throw new Error('Exposed 2160p rendition not listed in diagnostics');
if(result.p.sources.some(value=>value.includes('xh-download.mp4'))) throw new Error('Download endpoint leaked as a player rendition');
if(result.p.xhamsterOpaqueCount<1) throw new Error('Opaque value was not counted without decoding');
video.duration=12;
const preroll=run(window,document,{href:document.baseURI},()=>({display:'block',visibility:'visible',opacity:'1'}));
if(!preroll.p.ad || preroll.p.sources.some(value=>value.includes('xh-main-'))) throw new Error('xHamster content renditions leaked while duration-mismatch preroll was active');
video.duration=600;
const afterAd=run(window,document,{href:document.baseURI},()=>({display:'block',visibility:'visible',opacity:'1'}));
if(afterAd.p.ad || !afterAd.p.sources.some(value=>value.includes('xh-main-2160'))) throw new Error('xHamster content renditions were not restored after ad transition');
console.log('Main-frame xHamster extraction passed: clear MP4/HLS renditions and quality metadata captured; download/opaque values handled safely; preroll gated');
