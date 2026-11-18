const fs = require('fs');
const scan = fs.readFileSync(__dirname + '/SCAN_JS.js', 'utf8');
const container = {id:'video-player',className:'video-player',parentElement:null,getAttribute(){return null;},querySelectorAll(){return[];}};
const video = {tagName:'VIDEO',parentElement:container,currentSrc:'https://cdn.example/selected.mp4',paused:true,ended:false,readyState:4,currentTime:0,duration:720,poster:'',getBoundingClientRect(){return{left:100,top:100,right:900,bottom:550,width:800,height:450};},getAttribute(n){return n==='src'?this.currentSrc:null;},querySelectorAll(){return[];},addEventListener(){}};
const document = {baseURI:'https://site.example/watch',querySelectorAll(s){return s==='video'?[video]:[];},addEventListener(){}};
const window = {innerWidth:1200,innerHeight:800,location:{href:document.baseURI},playerConfig:{qualities:{'240p':{url:'https://cdn.example/v?id=240',type:'video/mp4'},'480p':{url:'https://cdn.example/v?id=480',type:'video/mp4'},'720p':{url:'https://cdn.example/v?id=720',type:'video/mp4'},'1080p':{url:'https://cdn.example/v?id=1080',type:'video/mp4'},'1440p':{url:'https://cdn.example/v?id=1440',type:'video/mp4'}}},VBVideoGesture:{onVideoEvent(){},onVideoGesture(){}}};
function getComputedStyle(){return{display:'block',visibility:'visible',opacity:'1'};}
const expr=scan.trim().replace(/;\s*$/,'');
const run=new Function('window','document','location','getComputedStyle','const result = '+expr+'; return result;');
const result=JSON.parse(run(window,document,{href:document.baseURI},getComputedStyle));
for(const q of [240,480,720,1080,1440]) {
  const url=result.p.sources.find(u=>u.includes(`id=${q}`));
  if(!url) throw new Error('missing quality-key source '+q+'p');
  const meta=result.p.sourceMeta.find(x=>x.url===url);
  if(!meta || meta.height!==q) throw new Error('missing quality-key metadata '+q+'p: '+JSON.stringify(meta));
}
console.log('Player quality-map test passed: 240p through 1440p captured with metadata');
