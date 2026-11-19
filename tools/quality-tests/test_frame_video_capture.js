const fs = require('fs');
const vm = require('vm');
const path = require('path');

const sourcePath = path.join(__dirname, '../../app/src/main/java/com/example/videobrowser/ApiCaptureScript.kt');
const source = fs.readFileSync(sourcePath, 'utf8');
const match = source.match(/EARLY_API_CAPTURE_JS\s*=\s*"""([\s\S]*?)"""/);
if (!match) throw new Error('Could not extract EARLY_API_CAPTURE_JS');
const script = match[1];

const childOrigin = 'https://player.example';
const video = {
  __vbFrameVideoId: '',
  currentSrc: 'https://cdn.example/master.m3u8?token=private',
  paused: false,
  ended: false,
  readyState: 4,
  currentTime: 17,
  duration: 420,
  videoWidth: 1920,
  videoHeight: 1080,
  poster: 'https://cdn.example/poster.jpg',
  parentElement: null,
  getAttribute(name) { return name === 'src' ? this.currentSrc : null; },
  getBoundingClientRect() { return {left:100, top:80, right:900, bottom:530, width:800, height:450}; },
  querySelectorAll(selector) {
    if (selector !== 'source[src],source[data-src]') return [];
    return [
      {getAttribute(name) { return ({src:'https://cdn.example/720.m3u8',type:'application/vnd.apple.mpegurl','data-quality':'720p','data-height':'720'})[name] || null; }},
      {getAttribute(name) { return ({src:'https://cdn.example/1080.m3u8',type:'application/vnd.apple.mpegurl','data-quality':'1080p','data-height':'1080'})[name] || null; }}
    ];
  }
};

const listeners = {};
const nativeMessages = [];
const document = {
  baseURI: 'https://player.example/embed/42',
  querySelectorAll(selector) { return selector === 'video' ? [video] : []; },
  addEventListener(name, fn) { (listeners[name] || (listeners[name] = [])).push(fn); }
};
const topWindow = {};
const window = {
  location: {protocol:'https:', origin:childOrigin, href:'https://player.example/embed/42?session=must-not-copy'},
  top: topWindow,
  innerWidth: 1280,
  innerHeight: 720,
  fetch: undefined,
  XMLHttpRequest: undefined,
  addEventListener(name, fn) { (listeners['window:' + name] || (listeners['window:' + name] = [])).push(fn); },
  setInterval(fn) { window.__interval = fn; return 1; },
  VideoBrowserCapture: {postMessage(raw) { nativeMessages.push(JSON.parse(raw)); }}
};

const context = {
  window, document, location: window.location, URL, Headers, TextDecoder,
  Promise, Uint8Array, Number, String, Object, Array, Date, RegExp, Math,
  getComputedStyle() { return {display:'block', visibility:'visible', opacity:'1'}; }
};
vm.runInNewContext(script, context, {filename:'ApiCaptureScript-frame.js'});

const state = nativeMessages.find(item => item.kind === 'frame-video-state');
if (!state) throw new Error('Cross-origin frame did not report its VIDEO element to native');
if (state.frameOrigin !== childOrigin || !state.frameId) throw new Error('Frame identity/origin missing');
if (!Array.isArray(state.videos) || state.videos.length !== 1) throw new Error('Expected exactly one frame video snapshot');
const selected = state.videos[0];
if (!selected.playing || !selected.visible || selected.duration !== 420) throw new Error('Playback/visibility metadata missing');
if (!selected.sources.some(url => url.includes('master.m3u8'))) throw new Error('Current HLS source was not included');
if (!selected.sources.some(url => url.includes('1080.m3u8'))) throw new Error('Alternative source was not included');
if (JSON.stringify(state).includes('session=must-not-copy')) throw new Error('Frame document query leaked into snapshot');
if (typeof window.__interval !== 'function') throw new Error('Frame state polling was not installed');
console.log('Cross-origin iframe video capture passed: native-only frame identity, visibility/playback state, HLS and alternative sources, no page postMessage or page-query leakage');
