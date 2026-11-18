const fs = require('fs');
const scan = fs.readFileSync(__dirname + '/SCAN_JS.js', 'utf8');
const container = {id:'video-player',className:'video-player',parentElement:null,getAttribute(){return null;},querySelectorAll(){return[];}};
const video = {tagName:'VIDEO',parentElement:container,currentSrc:'https://cdn.example/selected.mp4',paused:true,ended:false,readyState:4,currentTime:0,duration:500,poster:'',getBoundingClientRect(){return{left:100,top:100,right:900,bottom:550,width:800,height:450};},getAttribute(n){return n==='src'?this.currentSrc:null;},querySelectorAll(){return[];},addEventListener(){}};
const document={baseURI:'https://site.example/watch',querySelectorAll(s){return s==='video'?[video]:[];},addEventListener(){}};
const payload=JSON.stringify({qualities:{
 '240p':{url:'https://cdn.example/opaque240?token=a',height:240,type:'video/mp4'},
 '480p':{url:'https://cdn.example/opaque480?token=b',height:480,type:'video/mp4'},
 '720p':{url:'https://cdn.example/opaque720?token=c',height:720,type:'video/mp4'},
 '1080p':{url:'https://cdn.example/opaque1080?token=d',height:1080,type:'video/mp4'}
}});
const response={headers:{get(){return'application/json';}},clone(){return this;},text(){return Promise.resolve(payload);}};
const window={innerWidth:1200,innerHeight:800,location:{href:document.baseURI},fetch(){return Promise.resolve(response);},VBVideoGesture:{onVideoEvent(){},onVideoGesture(){}}};
const getComputedStyle=()=>({display:'block',visibility:'visible',opacity:'1'});
const expr=scan.trim().replace(/;\s*$/,'');
const run=new Function('window','document','location','getComputedStyle','const result = '+expr+'; return result;');
(async()=>{
  run(window,document,{href:document.baseURI},getComputedStyle); // installs the fetch observer
  await window.fetch('https://site.example/api/player-config');
  await new Promise(resolve=>setTimeout(resolve,0));
  const result=JSON.parse(run(window,document,{href:document.baseURI},getComputedStyle));
  const sources=result.p.sources;
  if (!window.__vbApiPayloads[0].__vbScanned || !Array.isArray(window.__vbApiPayloads[0].__vbParsedSources)) {
    throw new Error('API payload was not cached after its first parse');
  }
  const cachedResult=JSON.parse(run(window,document,{href:document.baseURI},getComputedStyle));
  if (![240,480,720,1080].every(q=>cachedResult.p.sources.some(u=>u.includes(`opaque${q}`)))) {
    throw new Error('Cached API candidates were not replayed on the next collectSources call');
  }
  for(const q of [240,480,720,1080]) if(!sources.some(u=>u.includes(`opaque${q}`))) throw new Error('missing API quality '+q+': '+sources.join(','));
  for(const q of [240,480,720,1080]) {
    const meta=result.p.sourceMeta.find(x=>x.url.includes('opaque'+q));
    if(!meta || meta.height!==q) throw new Error('missing API quality metadata '+q+': '+JSON.stringify(meta));
  }
  console.log('API JSON interception passed: 4 opaque endpoints and all quality metadata captured');
})().catch(e=>{console.error(e);process.exit(1);});
