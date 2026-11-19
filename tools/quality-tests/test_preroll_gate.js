const fs = require('fs');
const scan = fs.readFileSync(__dirname + '/SCAN_JS.js', 'utf8');

function run(duration, declaredDuration) {
  const container = {id:'player', className:'video-player', parentElement:null, getAttribute(){return null;}, querySelectorAll(){return [];}};
  const video = {
    tagName:'VIDEO', parentElement:container,
    currentSrc:'blob:https://site.example/video', paused:false, ended:false, readyState:4,
    currentTime:3, duration, videoWidth:1280, videoHeight:720, poster:'',
    __vbUserInitiated:true,
    getAttribute(name){ return name === 'src' ? this.currentSrc : null; },
    getBoundingClientRect(){ return {left:100,top:80,right:900,bottom:530,width:800,height:450}; },
    querySelectorAll(){ return []; }, addEventListener(){}
  };
  const document = {baseURI:'https://site.example/watch/42', querySelectorAll(sel){return sel==='video'?[video]:[];}, addEventListener(){}};
  const window = {
    innerWidth:1280, innerHeight:720, location:{href:document.baseURI},
    flashvars_42:{video_duration:declaredDuration, mediaDefinitions:[
      {format:'hls',quality:1080,videoUrl:'https://cdn.example/1080.m3u8'},
      {format:'hls',quality:720,videoUrl:'https://cdn.example/720.m3u8'},
      {format:'hls',quality:480,videoUrl:'https://cdn.example/480.m3u8'}
    ]},
    VBVideoGesture:{onVideoEvent(){},onVideoGesture(){}}
  };
  const runScan = new Function('window','document','location','getComputedStyle','const result='+scan.trim().replace(/;\s*$/,'')+'; return JSON.parse(result);');
  return runScan(window,document,{href:document.baseURI},()=>({display:'block',visibility:'visible',opacity:'1'}));
}

const preroll = run(12, 420);
if (!preroll.p || !preroll.p.ad) throw new Error('Player duration mismatch did not mark preroll as ad');
for (const q of [1080,720,480]) if (!preroll.p.sources.some(url => url.includes(q + '.m3u8'))) throw new Error('Player-declared content rendition was not captured during preroll: ' + q);
if (preroll.p.sources.some(url => /(?:ad|advert|preroll)/i.test(url))) throw new Error('An ad-marked rendition leaked during preroll');

const content = run(420, 420);
if (!content.p || content.p.ad) throw new Error('Normal content was incorrectly classified as ad');
for (const q of [1080,720,480]) if (!content.p.sources.some(url=>url.includes(q+'.m3u8'))) throw new Error('Content rendition missing after ad transition: '+q);

const shortContent = run(9, 9);
if (!shortContent.p || shortContent.p.ad) throw new Error('A genuinely short video matching the declared duration was incorrectly classified as ad');
console.log('Preroll capture passed: player-declared content renditions remain available during ads, ad-marked sources stay excluded, and genuinely short videos remain valid');
