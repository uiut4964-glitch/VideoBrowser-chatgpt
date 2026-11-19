const fs = require('fs');
const vm = require('vm');
const path = require('path');
const sourcePath = path.join(__dirname, '../../app/src/main/java/com/example/videobrowser/ApiCaptureScript.kt');
const source = fs.readFileSync(sourcePath, 'utf8');
const match = source.match(/EARLY_API_CAPTURE_JS\s*=\s*"""([\s\S]*?)"""/);
if (!match) throw new Error('Could not extract early capture script');
const script = match[1];

function run({protocol='https:', origin='https://site.example', top=true, videos=[], shadowHosts=[], apiAlreadyInstalled=false}) {
  const messages = [];
  const listeners = {};
  const shadowRoot = {
    querySelectorAll(selector) {
      if (selector === 'video') return videos;
      return [];
    }
  };
  shadowHosts.forEach(host => host.shadowRoot = shadowRoot);
  const doc = {
    baseURI: origin + '/page',
    readyState: 'interactive',
    querySelectorAll(selector) {
      if (selector === 'video') return shadowHosts.length ? [] : videos;
      if (selector === '*') return shadowHosts;
      return [];
    },
    addEventListener(name, fn) { (listeners[name] ||= []).push(fn); }
  };
  const win = {
    location: {protocol, origin, href: origin + '/page'},
    top: null,
    innerWidth: 1200, innerHeight: 800,
    fetch: undefined, XMLHttpRequest: undefined,
    addEventListener(name, fn) { (listeners['window:' + name] ||= []).push(fn); },
    setInterval(fn) { this.__interval = fn; return 1; },
    VideoBrowserCapture: {postMessage(raw) { messages.push(JSON.parse(raw)); }}
  };
  win.top = top ? win : {};
  if (apiAlreadyInstalled) win.__vbApiObserverInstalled = true;
  videos.forEach(v => { v.getRootNode = () => shadowHosts.length ? shadowRoot : doc; });
  const context = {
    window: win, document: doc, location: win.location, URL, Headers, TextDecoder,
    Promise, Uint8Array, Number, String, Object, Array, Date, RegExp, Math,
    getComputedStyle() { return {display:'block', visibility:'visible', opacity:'1'}; }
  };
  vm.runInNewContext(script, context, {filename:'ApiCaptureScript-probe.js'});
  return {messages, window:win};
}

// Main document without video must still report that the hook really executed.
const topProbe = run({top:true, videos:[]});
if (!topProbe.messages.some(m => m.kind === 'frame-probe' && m.isTop === true && m.phase === 'document-start')) {
  // isTop is encoded from the script, and the probe is required even with zero videos.
  throw new Error('Top document did not send a document-start probe');
}
if (topProbe.messages.some(m => m.kind === 'frame-video-state')) throw new Error('Empty page unexpectedly sent a video state');

// about:blank child frames are allowed to run the probe instead of returning early.
const blankProbe = run({protocol:'about:', origin:'https://site.example', top:false, videos:[]});
if (!blankProbe.messages.some(m => m.kind === 'frame-probe' && m.protocol === 'about:')) {
  throw new Error('about:blank child frame was skipped');
}

// A VIDEO inside an open Shadow DOM must be discovered and labeled as shadow content.
const video = {
  __vbFrameVideoId:'', currentSrc:'https://cdn.example/master.m3u8', paused:false, ended:false,
  duration:300, videoWidth:1920, videoHeight:1080, poster:'',
  getAttribute(name) { return name === 'src' ? this.currentSrc : null; },
  getBoundingClientRect() { return {left:10,top:10,right:800,bottom:460,width:790,height:450}; },
  querySelectorAll() { return []; }
};
const shadow = run({top:true, videos:[video], shadowHosts:[{}], apiAlreadyInstalled:true});
const state = shadow.messages.find(m => m.kind === 'frame-video-state');
if (!state) throw new Error('Shadow DOM VIDEO was not reported');
if (state.shadowVideoCount !== 1 || state.shadowRootCount !== 1 || state.isTop !== true) throw new Error('Top-frame Shadow DOM counters are incorrect');
if (!state.videos[0].sources.includes('https://cdn.example/master.m3u8')) throw new Error('Shadow DOM current source missing');
console.log('Frame probes and open Shadow DOM capture passed: top-frame heartbeat, about:blank child, repeat-injection guard, top-frame Shadow DOM video discovery');
