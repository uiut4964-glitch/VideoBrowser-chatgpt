const fs = require('fs');
const vm = require('vm');
const scan = fs.readFileSync(__dirname + '/SCAN_JS.js', 'utf8');
const container = {
  id: 'main-video-player', className: 'video-player', parentElement: null,
  getAttribute() { return null; },
  querySelectorAll(selector) { return []; }
};
const video = {
  tagName: 'VIDEO', parentElement: container, currentSrc: 'https://cdn.example/current-240.mp4',
  paused: true, ended: false, readyState: 4, currentTime: 0, duration: 600, poster: '',
  __vbUserInitiated: false,
  getBoundingClientRect() { return {left:100, top:100, right:900, bottom:550, width:800, height:450}; },
  getAttribute(name) { return name === 'src' ? this.currentSrc : null; },
  querySelectorAll() { return []; }, addEventListener() {}
};
const document = {
  baseURI: 'https://site.example/watch/123',
  querySelectorAll(selector) { return selector === 'video' ? [video] : []; },
  addEventListener() {}
};
const window = {
  innerWidth: 1200, innerHeight: 800,
  location: {href: document.baseURI},
  flashvars_987: { video_duration: 600, mediaDefinitions: [
    {format:'hls', quality:1080, videoUrl:'https://cdn.example/flashvars-1080.m3u8'},
    {format:'hls', quality:720, videoUrl:'https://cdn.example/flashvars-720.m3u8'},
    {format:'mp4', quality:480, videoUrl:'https://cdn.example/flashvars-480.mp4'},
    {format:'mp4', quality:360, videoUrl:'https://cdn.example/ads/spot-360.mp4', isAd:true}
  ]},
  playerConfig: {sources: [
    {label:'240p', height:240, type:'video/mp4', src:'https://cdn.example/main-240.mp4'},
    {label:'480p', height:480, type:'video/mp4', src:'https://cdn.example/main-480.mp4'},
    {label:'720p', height:720, type:'video/mp4', src:'https://cdn.example/main-720.mp4'},
    {label:'1080p', height:1080, type:'video/mp4', src:'https://cdn.example/main-1080.mp4'}
  ]},
  VBVideoGesture: { onVideoEvent(){}, onVideoGesture(){} }
};
function getComputedStyle() { return {display:'block', visibility:'visible', opacity:'1'}; }
const expr = scan.trim().replace(/;\s*$/, '');
const run = new Function('window','document','location','getComputedStyle', 'const result = ' + expr + '; return result;');
const raw = run(window, document, {href:document.baseURI}, getComputedStyle); console.log('raw scan result:', raw); const result = JSON.parse(raw);
const sources = result.p.sources;
const expected = [240,480,720,1080];
for (const q of expected) {
  if (!sources.some(u => u.includes(`main-${q}.mp4`))) throw new Error(`Missing quality ${q}p: ${sources.join(',')}`);
}
for (const q of [480, 720, 1080]) {
  const url = result.p.sources.find(item => item.includes(`flashvars-${q}`));
  if (!url) throw new Error(`Missing flashvars mediaDefinition quality ${q}p`);
  const meta = result.p.sourceMeta.find(x => x.url === url);
  if (!meta || meta.height !== q || meta.captureSource !== 'player-flashvars') throw new Error(`Missing flashvars source metadata ${q}p`);
}
for (const q of expected) {
  const meta = result.p.sourceMeta.find(x => x.url.includes(`main-${q}.mp4`));
  if (!meta || meta.height !== q || !String(meta.label).toLowerCase().includes(String(q))) {
    throw new Error(`Missing quality metadata ${q}p: ${JSON.stringify(meta)}`);
  }
}
if (sources.some(u => u.includes('/ads/spot-360.mp4'))) throw new Error('Explicit ad definition leaked into content sources');
if ((result.p.playerDefinitionQualities || []).includes('360p')) throw new Error('Explicit ad quality leaked into content quality diagnostics');
console.log(`player-config extraction passed: ${expected.length} explicit quality sources and their height/label metadata captured; explicit ad definition excluded`);
