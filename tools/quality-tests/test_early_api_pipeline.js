const fs = require('fs');
const vm = require('vm');
const path = require('path');

const root = path.join(__dirname, '../../app/src/main/java/com/example/videobrowser');
const kotlinEarly = fs.readFileSync(path.join(root, 'ApiCaptureScript.kt'), 'utf8');
const earlyMatch = kotlinEarly.match(/EARLY_API_CAPTURE_JS\s*=\s*"""([\s\S]*?)"""/);
if (!earlyMatch) throw new Error('Could not extract early capture script');
const earlyScript = earlyMatch[1];
const scanScript = fs.readFileSync(path.join(__dirname, 'SCAN_JS.js'), 'utf8').trim().replace(/;\s*$/, '');

function makeResponse(body, contentType) {
  const headers = {
    get(name) {
      const key = String(name).toLowerCase();
      if (key === 'content-type') return contentType;
      if (key === 'content-length') return String(Buffer.byteLength(body, 'utf8'));
      return null;
    }
  };
  return {
    url: 'https://watch.example/api/player-config',
    headers,
    clone() { return { headers, text: async () => body }; }
  };
}

function buildWindow(withVideo = true) {
  const location = {
    protocol: 'https:', origin: 'https://watch.example',
    href: 'https://watch.example/watch/42?session=must-not-be-copied#player'
  };
  const listeners = {};
  const body = JSON.stringify({ qualities: {
    '240p': { url: 'https://cdn.example/opaque240?token=a', height: 240, type: 'video/mp4' },
    '480p': { url: 'https://cdn.example/opaque480?token=b', height: 480, type: 'video/mp4' },
    '720p': { url: 'https://cdn.example/opaque720?token=c', height: 720, type: 'video/mp4' },
    '1080p': { url: 'https://cdn.example/opaque1080?token=d', height: 1080, type: 'video/mp4' }
  }});
  const window = {
    location, top: null, innerWidth: 1200, innerHeight: 800,
    fetch: async () => makeResponse(body, 'application/json'),
    addEventListener(name, listener) { (listeners[name] || (listeners[name] = [])).push(listener); },
    postMessage() { window.__outboundPageMessages++; },
    VBVideoGesture: { onVideoEvent() {}, onVideoGesture() {} }
  };
  const nativePayloads = [];
  window.__outboundPageMessages = 0;
  window.VideoBrowserCapture = { postMessage(data) { nativePayloads.push(JSON.parse(data)); } };
  window.top = window;
  const video = {
    tagName: 'VIDEO', parentElement: null, currentSrc: 'https://cdn.example/current-240.mp4',
    paused: true, ended: false, readyState: 4, currentTime: 0, duration: 600, poster: '',
    videoWidth: 640, videoHeight: 360,
    getBoundingClientRect() { return { left: 100, top: 100, right: 900, bottom: 550, width: 800, height: 450 }; },
    getAttribute(name) { return name === 'src' ? this.currentSrc : null; },
    querySelectorAll() { return []; }, addEventListener() {}
  };
  const document = {
    baseURI: location.href,
    querySelectorAll(selector) { return selector === 'video' && withVideo ? [video] : []; },
    addEventListener() {}
  };
  return { window, location, document, body, nativePayloads };
}

async function main() {
  const { window, location, document, nativePayloads } = buildWindow();
  const context = { window, location, URL, Headers, TextDecoder, Promise, Uint8Array, Number, String, Object, Array, Date, RegExp, Math };
  vm.runInNewContext(earlyScript, context, { filename: 'ApiCaptureScript.js' });

  await window.fetch('/api/player-config', {
    method: 'POST',
    headers: {
      Accept: 'application/json', 'Accept-Language': 'ar',
      Authorization: 'Bearer secret', Cookie: 'session=secret'
    }
  });
  await new Promise(resolve => setImmediate(resolve));
  if (!window.__vbApiPayloads || window.__vbApiPayloads.length !== 1) throw new Error('Early API response did not enter the capture queue');

  const getComputedStyle = () => ({ display: 'block', visibility: 'visible', opacity: '1' });
  const runScan = new Function('window', 'document', 'location', 'getComputedStyle', '__vbNativeApiPayloads', 'const result = ' + scanScript + '; return result;');
  const result = JSON.parse(runScan(window, document, location, getComputedStyle, nativePayloads));
  const urls = result.p && result.p.sources || [];
  for (const quality of [240, 480, 720, 1080]) {
    const url = urls.find(item => item.includes('opaque' + quality));
    if (!url) throw new Error('End-to-end scan missed ' + quality + 'p');
    const meta = result.p.sourceMeta.find(item => item.url === url);
    if (!meta || meta.height !== quality) throw new Error('Quality metadata missing for ' + quality + 'p');
    if (meta.captureSource !== 'api-fetch-api') throw new Error('Capture source missing for ' + quality + 'p');
    if (meta.requestHeaders.Accept !== 'application/json' || meta.requestHeaders.Origin !== 'https://watch.example') {
      throw new Error('Safe request context was not propagated for ' + quality + 'p');
    }
    if (meta.requestHeaders.Authorization || meta.requestHeaders.Cookie || meta.requestHeaders.Referer.includes('?')) {
      throw new Error('Secret query/header data leaked into source metadata');
    }
  }
  // A separate site with no top-frame VIDEO element still exposes API data from its iframe.
  const orphan = buildWindow(false);
  const orphanContext = {
    window: orphan.window, location: orphan.location, URL, Headers, TextDecoder,
    Promise, Uint8Array, Number, String, Object, Array, Date, RegExp, Math
  };
  vm.runInNewContext(earlyScript, orphanContext, { filename: 'ApiCaptureScript-orphan-top.js' });
  const childLocation = {
    protocol: 'https:', origin: 'https://player.frame.example',
    href: 'https://player.frame.example/embed/42?secret=frame-token'
  };
  const childResponse = makeResponse(orphan.body, 'application/json');
  childResponse.url = 'https://player.frame.example/api/player-config';
  const childListeners = {};
  const childWindow = {
    location: childLocation, top: orphan.window,
    fetch: async () => childResponse,
    addEventListener(name, listener) { (childListeners[name] || (childListeners[name] = [])).push(listener); },
    postMessage() {},
    VideoBrowserCapture: { postMessage(data) { orphan.nativePayloads.push(JSON.parse(data)); } }
  };
  const childContext = {
    window: childWindow, location: childLocation, URL, Headers, TextDecoder,
    Promise, Uint8Array, Number, String, Object, Array, Date, RegExp, Math
  };
  vm.runInNewContext(earlyScript, childContext, { filename: 'ApiCaptureScript-orphan-frame.js' });
  await childWindow.fetch('/api/player-config', {
    method: 'POST', headers: { Accept: 'application/json', Authorization: 'must-not-copy' }
  });
  await new Promise(resolve => setImmediate(resolve));
  const orphanResult = JSON.parse(runScan(orphan.window, orphan.document, orphan.location, getComputedStyle, orphan.nativePayloads));
  if (orphan.window.__outboundPageMessages !== 0) throw new Error('Cross-origin API payload was leaked to the parent page');
  if (orphanResult.p !== null) throw new Error('No-video test unexpectedly selected a top-frame video');
  for (const quality of [240, 480, 720, 1080]) {
    if (!(orphanResult.o || []).some(url => url.includes('opaque' + quality))) {
      throw new Error('API-only scan without a top-frame VIDEO missed ' + quality + 'p');
    }
    const meta = (orphanResult.om || []).find(item => item.url.includes('opaque' + quality));
    if (!meta || meta.height !== quality || meta.captureSource !== 'api-fetch-api') {
      throw new Error('API-only scan lost metadata for ' + quality + 'p');
    }
    if (meta.requestHeaders.Accept !== 'application/json' || meta.requestHeaders.Origin !== 'https://player.frame.example') {
      throw new Error('Cross-origin iframe request context was not propagated for ' + quality + 'p');
    }
    if (meta.requestHeaders.Referer !== 'https://player.frame.example/embed/42' ||
        meta.requestHeaders.Authorization || meta.requestHeaders.Cookie) {
      throw new Error('Cross-origin iframe metadata leaked secrets or retained a Referer query');
    }
  }
  console.log('Early API pipeline passed: document-start Fetch -> native iframe queue -> SCAN_JS, normal video and no-top-frame-video flows, quality metadata, and safe headers');
}

main().catch(error => { console.error(error); process.exit(1); });
