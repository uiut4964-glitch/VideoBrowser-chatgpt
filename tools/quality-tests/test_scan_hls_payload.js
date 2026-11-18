const fs = require('fs');
const scan = fs.readFileSync(__dirname + '/SCAN_JS.js', 'utf8');
const container = {id:'video-player',className:'video-player',parentElement:null,getAttribute(){return null;},querySelectorAll(){return[];}};
const video = {tagName:'VIDEO',parentElement:container,currentSrc:'https://cdn.example/current-240.mp4',paused:true,ended:false,readyState:4,currentTime:0,duration:640,poster:'',getBoundingClientRect(){return{left:100,top:100,right:900,bottom:550,width:800,height:450};},getAttribute(n){return n==='src'?this.currentSrc:null;},querySelectorAll(){return[];},addEventListener(){}};
const document = {baseURI:'https://site.example/watch/123',querySelectorAll(s){return s==='video'?[video]:[];},addEventListener(){}};
const master = `#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-STREAM-INF:BANDWIDTH=350000,RESOLUTION=426x240,NAME="240p"\n/api/rendition?id=opaque240\n#EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=854x480,NAME="480p"\n/api/rendition?id=opaque480\n#EXT-X-STREAM-INF:BANDWIDTH=1800000,RESOLUTION=1280x720,NAME="720p"\n/api/rendition?id=opaque720\n#EXT-X-STREAM-INF:BANDWIDTH=3600000,RESOLUTION=1920x1080,NAME="1080p"\n/api/rendition?id=opaque1080\n`;
const response = {headers:{get(){return 'text/plain';}},clone(){return this;},text(){return Promise.resolve(master);}};
const window = {innerWidth:1200,innerHeight:800,location:{href:document.baseURI},fetch(){return Promise.resolve(response);},VBVideoGesture:{onVideoEvent(){},onVideoGesture(){}}};
function getComputedStyle(){return{display:'block',visibility:'visible',opacity:'1'};}
const expr=scan.trim().replace(/;\s*$/,'');
const run=new Function('window','document','location','getComputedStyle','const result = '+expr+'; return result;');
(async()=>{
  run(window,document,{href:document.baseURI},getComputedStyle);
  await window.fetch('https://site.example/api/opaque-manifest');
  await new Promise(resolve=>setTimeout(resolve,0));
  const result=JSON.parse(run(window,document,{href:document.baseURI},getComputedStyle));
  const sources=result.p.sources;
  for(const q of [240,480,720,1080]) {
    const url=sources.find(u=>u.includes(`opaque${q}`));
    if(!url) throw new Error('missing opaque HLS variant '+q+'p: '+sources.join(','));
    const meta=result.p.sourceMeta.find(x=>x.url===url);
    if(!meta || meta.height!==q || !String(meta.mime).includes('mpegurl')) throw new Error('missing manifest metadata '+q+'p: '+JSON.stringify(meta));
  }
  console.log('Opaque HLS API manifest test passed: all 4 variants captured with resolution and HLS MIME metadata');
})().catch(e=>{console.error(e);process.exit(1);});
