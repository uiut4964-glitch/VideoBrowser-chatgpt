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
const documentListeners = {};
const nativeMessages = [];
const document = {
  baseURI: 'https://player.example/embed/42',
  querySelectorAll(selector) { return selector === 'video' ? [video] : []; },
  addEventListener(name, fn) { (documentListeners[name] || (documentListeners[name] = [])).push(fn); }
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
  VideoBrowserCapture: {postMessage(raw) { nativeMessages.push(JSON.parse(raw)); }},
  flashvars_987: {
    mediaDefinitions: [
      {format:'hls', quality:1080, videoUrl:'https://cdn.example/content-1080.m3u8'},
      {format:'hls', quality:720, videoUrl:'https://cdn.example/content-720.m3u8'},
      {format:'mp4', quality:480, videoUrl:'https://cdn.example/content-480.mp4'},
      {format:'mp4', quality:360, videoUrl:'https://cdn.example/ads/ad-360.mp4', role:'advertisement'}
    ]
  },
  initials: {
    videoModel: { duration:420, sources: {
      mp4: {
        '720p': {url:'https://cdn.example/xh-720.mp4', quality:'720p', width:1280, height:720},
        '480p': 'https://cdn.example/xh-480.mp4'
      },
      hls: { '2160p': {url:'https://cdn.example/xh-2160-master.m3u8', resolution:'3840x2160'} },
      download: { '720p': 'https://cdn.example/xh-download-link.mp4' }
    }},
    xplayerSettings: { sources: { standard: { h264: 'a'.repeat(64) } } }
  }
};

const performanceTimeOrigin = Date.now() - 5000;
let frameResourceEntries = [
  {name:'https://cdn.example/active-from-player.m3u8', startTime:3000},
  {name:'https://cdn.example/segment-001.m4s', startTime:3200},
  {name:'https://ads.example/ad.m3u8', startTime:3100}
];
const performance = {
  timeOrigin: performanceTimeOrigin,
  getEntriesByType(kind) { return kind === 'resource' ? frameResourceEntries : []; }
};

const context = {
  window, document, location: window.location, URL, Headers, TextDecoder, performance,
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
if (selected.playerVideoDuration !== 420) throw new Error('Declared content duration was not forwarded to native');
if (!selected.sources.some(url => url.includes('master.m3u8'))) throw new Error('Current HLS source was not included');
if (!selected.sources.some(url => url.includes('active-from-player.m3u8'))) throw new Error('Recent HLS manifest from Resource Timing was not included');
if (selected.sources.some(url => url.includes('segment-001.m4s'))) throw new Error('A media segment was incorrectly exposed as a downloadable manifest');
if (!selected.sources.some(url => url.includes('1080.m3u8'))) throw new Error('Alternative source was not included');
// A tap inside the player should remain a user-initiated marker after the 12-second pointer window.
(documentListeners.pointerdown || []).forEach(fn => fn({clientX:200, clientY:150}));
const playingState = nativeMessages.filter(item => item.kind === 'frame-video-state').slice(-1)[0];
if (!playingState || !playingState.videos[0].gesture) throw new Error('Frame user gesture was not retained');
for (const q of [1080, 720, 480]) {
  const url = selected.sources.find(value => value.includes('content-' + q));
  const meta = selected.sourceMeta.find(value => value.url === url);
  if (!meta || meta.height !== q || meta.captureSource !== 'player-flashvars') throw new Error('flashvars mediaDefinitions metadata missing for ' + q + 'p');
}
if (selected.sources.some(url => url.includes('/ads/ad-360.mp4'))) throw new Error('Explicit ad mediaDefinition leaked into frame content sources');
if (state.playerMediaDefinitionCount !== 4) throw new Error('mediaDefinitions count was not reported');
if (!state.playerDefinitionQualities.includes('1080p') || !state.playerDefinitionQualities.includes('720p') || state.playerDefinitionQualities.includes('360p')) throw new Error('flashvars quality diagnostics missing or includes an ad quality');
if (!state.xhamsterStateFound || state.xhamsterSourceCount < 3) throw new Error('xHamster initials video source map was not read in iframe');
if (!state.xhamsterQualities.includes('720p') || !state.xhamsterQualities.includes('480p') || !state.xhamsterQualities.includes('2160p')) throw new Error('xHamster quality ladder/2K rendition was not reported from exposed data');
if (state.xhamsterOpaqueCount < 1) throw new Error('Opaque xplayerSettings value was not counted safely');
for (const expected of ['xh-720.mp4','xh-480.mp4','xh-2160-master.m3u8']) {
  if (!selected.sources.some(url => url.includes(expected))) throw new Error('xHamster frame source missing: '+expected);
  const foundUrl = selected.sources.find(url => url.includes(expected));
  const foundMeta = selected.sourceMeta.find(meta => meta.url === foundUrl);
  if (!foundMeta || foundMeta.captureSource !== 'player-xhamster') throw new Error('xHamster source metadata missing: '+expected);
}
if (selected.sources.some(url => url.includes('xh-download-link'))) throw new Error('xHamster download endpoint was treated as a rendition');
if (JSON.stringify(state).includes('session=must-not-copy')) throw new Error('Frame document query leaked into snapshot');
if (typeof window.__interval !== 'function') throw new Error('Frame state polling was not installed');

// A short in-player preroll is recognized against the content duration declared by flashvars.
video.duration = 12;
video.currentTime = 4;
window.__interval();
const adState = nativeMessages.filter(item => item.kind === 'frame-video-state').slice(-1)[0];
if (!adState.videos[0].ad || adState.videos[0].adReason !== 'player-duration-mismatch') throw new Error('Preroll duration mismatch was not classified as an ad');
if (adState.videos[0].playerVideoDuration !== 420) throw new Error('Content duration missing during preroll diagnostic');
if (adState.videos[0].sources.some(url => url.includes('content-1080') || url.includes('active-from-player') || url.includes('xh-720') || url.includes('xh-2160'))) throw new Error('Content renditions leaked while an ad was playing');

// When the same player transitions to its declared content duration, old preroll manifests stay hidden,
// while a genuinely fresh manifest requested after the transition can be admitted.
video.duration = 420;
video.currentTime = 19;
frameResourceEntries = [
  {name:'https://cdn.example/active-from-player.m3u8', startTime:3000},
  {name:'https://cdn.example/fresh-content-after-transition.m3u8', startTime:Date.now() - performanceTimeOrigin}
];
window.__interval();
const contentState = nativeMessages.filter(item => item.kind === 'frame-video-state').slice(-1)[0];
if (contentState.videos[0].ad) throw new Error('Content remained classified as an ad after duration transition');
if (!contentState.videos[0].sources.some(url => url.includes('content-1080'))) throw new Error('Content renditions were not restored after preroll ended');
if (contentState.videos[0].sources.some(url => url.includes('active-from-player'))) throw new Error('A manifest from before the preroll-to-content boundary was reused');
if (!contentState.videos[0].sources.some(url => url.includes('fresh-content-after-transition'))) throw new Error('Fresh post-transition HLS manifest was not captured');
console.log('Cross-origin iframe capture passed: rendition extraction, Resource Timing manifest recovery, preroll gating, content transition, and native-only frame messaging');
