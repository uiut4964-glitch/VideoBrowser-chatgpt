const fs = require('fs');
const vm = require('vm');
const path = require('path');

const sourcePath = path.join(__dirname, '../../app/src/main/java/com/example/videobrowser/ApiCaptureScript.kt');
const kotlinSource = fs.readFileSync(sourcePath, 'utf8');
const match = kotlinSource.match(/EARLY_API_CAPTURE_JS\s*=\s*"""([\s\S]*?)"""/);
if (!match) throw new Error('Could not extract EARLY_API_CAPTURE_JS from Kotlin source');
const earlyScript = match[1];

function makeResponse(body, contentType) {
  const headerMap = {
    'content-type': contentType,
    'content-length': String(Buffer.byteLength(body, 'utf8'))
  };
  return {
    url: '',
    headers: { get(name) { return headerMap[String(name).toLowerCase()] || null; } },
    clone() {
      return {
        headers: this.headers,
        text: async () => body
      };
    }
  };
}

function makeWindow(location, topWindow, fetchImpl, xhrClass) {
  const listeners = {};
  const window = {
    location,
    top: topWindow || null,
    fetch: fetchImpl,
    XMLHttpRequest: xhrClass,
    addEventListener(name, listener) {
      (listeners[name] || (listeners[name] = [])).push(listener);
    },
    postMessage(data) {
      window.__outboundPageMessages.push(data);
    }
  };
  window.__nativeBridgeMessages = [];
  window.__outboundPageMessages = [];
  window.VideoBrowserCapture = {
    postMessage(data) { window.__nativeBridgeMessages.push(JSON.parse(data)); }
  };
  window.__messageListeners = listeners.message || [];
  window.__dispatchMessage = event => (listeners.message || []).forEach(listener => listener(event));
  return window;
}

function runInWindow(window) {
  const context = {
    window,
    location: window.location,
    URL,
    Headers,
    TextDecoder,
    Promise,
    Uint8Array,
    Number,
    String,
    Object,
    Array,
    Date,
    RegExp,
    Math
  };
  vm.runInNewContext(earlyScript, context, { filename: 'ApiCaptureScript.js' });
}

async function main() {
  const apiJson = JSON.stringify({
    qualities: {
      '240p': { url: 'https://cdn.example/opaque240', height: 240 },
      '720p': { url: 'https://cdn.example/opaque720', height: 720 },
      '1080p': { url: 'https://cdn.example/opaque1080', height: 1080 }
    }
  });

  const topLocation = {
    protocol: 'https:', origin: 'https://watch.example', href: 'https://watch.example/watch/42?private=query#player'
  };
  const topResponse = makeResponse(apiJson, 'application/json');
  topResponse.url = 'https://watch.example/api/player-config';
  const topWindow = makeWindow(topLocation, null, async () => topResponse, null);
  topWindow.top = topWindow;
  runInWindow(topWindow);

  await topWindow.fetch('/api/player-config', {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      'Accept-Language': 'ar',
      Authorization: 'Bearer do-not-copy',
      Cookie: 'session=do-not-copy'
    }
  });
  await new Promise(resolve => setImmediate(resolve));

  let items = topWindow.__vbApiPayloads || [];
  if (!items.some(item => item.url === 'https://watch.example/api/player-config' && item.method === 'POST')) {
    throw new Error('Early top-frame Fetch response was not captured');
  }
  const topItem = items.find(item => item.url === 'https://watch.example/api/player-config');
  if (topItem.headers.Accept !== 'application/json' || topItem.headers['Accept-Language'] !== 'ar') {
    throw new Error('Safe Fetch request headers were not captured');
  }
  if (Object.keys(topItem.headers).some(k => /cookie|authorization/i.test(k))) {
    throw new Error('Sensitive Fetch headers leaked into capture metadata');
  }
  if (topItem.headers.Referer !== 'https://watch.example/watch/42' || topItem.headers.Origin !== 'https://watch.example') {
    throw new Error('Page Referer/Origin context was not safely normalized');
  }

  // A cross-origin iframe sends its capture to the native bridge, not to the embedding page.
  const childLocation = {
    protocol: 'https:', origin: 'https://player.cdn.example', href: 'https://player.cdn.example/embed/7?token=private'
  };
  const childResponse = makeResponse('#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=2500000,RESOLUTION=1280x720\n720/index.m3u8\n', 'application/vnd.apple.mpegurl');
  childResponse.url = 'https://player.cdn.example/api/master';
  const childWindow = makeWindow(childLocation, topWindow, async () => childResponse, null);
  childWindow.top = topWindow;
  let pageMessageCount = 0;
  topWindow.addEventListener('message', () => { pageMessageCount++; });
  runInWindow(childWindow);
  await childWindow.fetch('/api/master', { headers: { Accept: 'application/vnd.apple.mpegurl', Authorization: 'not-captured' } });
  await new Promise(resolve => setImmediate(resolve));
  const frameItem = childWindow.__nativeBridgeMessages.find(item => item.url === 'https://player.cdn.example/api/master');
  if (!frameItem) throw new Error('Cross-origin iframe Fetch payload was not sent to native bridge');
  if (pageMessageCount !== 0 || childWindow.__outboundPageMessages.length !== 0) {
    throw new Error('Cross-origin API payload was exposed to page postMessage listeners');
  }
  if (frameItem.frameOrigin !== childLocation.origin || frameItem.source !== 'fetch-api') {
    throw new Error('Iframe origin/source metadata is missing');
  }
  if (frameItem.headers.Accept !== 'application/vnd.apple.mpegurl' || frameItem.headers.Authorization) {
    throw new Error('Iframe safe-header allowlist failed');
  }

  // XHR is hooked at prototype level, preserving the original constructor/prototype.
  class MockXHR {
    constructor() { this.listeners = {}; this.responseType = 'json'; this.response = { qualities: { '480p': { url: 'https://cdn.example/xhr480', height: 480 } } }; this.responseURL = ''; }
    open(method, url) { this.method = method; this.url = url; this.responseURL = new URL(url, childLocation.href).href; }
    setRequestHeader(name, value) { (this.sentHeaders || (this.sentHeaders = {}))[name] = value; }
    addEventListener(name, fn, options) { (this.listeners[name] || (this.listeners[name] = [])).push({ fn, once: !!(options && options.once) }); }
    getResponseHeader(name) { return String(name).toLowerCase() === 'content-type' ? 'application/json' : null; }
    send() {
      const listeners = this.listeners.load || [];
      listeners.slice().forEach(entry => entry.fn.call(this));
      this.listeners.load = listeners.filter(entry => !entry.once);
    }
  }
  const xhrWindow = makeWindow(childLocation, topWindow, async () => makeResponse('', 'text/plain'), MockXHR);
  xhrWindow.top = topWindow;
  runInWindow(xhrWindow);
  const xhr = new xhrWindow.XMLHttpRequest();
  xhr.open('POST', '/api/xhr-player');
  xhr.setRequestHeader('Accept', 'application/json');
  xhr.setRequestHeader('Authorization', 'not-captured');
  xhr.send();
  const xhrItem = xhrWindow.__nativeBridgeMessages.find(item => item.url === 'https://player.cdn.example/api/xhr-player');
  if (!xhrItem || xhrItem.method !== 'POST' || xhrItem.source !== 'xhr-api') {
    throw new Error('Early XHR response was not captured');
  }
  if (xhrItem.headers.Accept !== 'application/json' || xhrItem.headers.Authorization) {
    throw new Error('XHR safe-header allowlist failed');
  }

  // Binary media responses must not be cloned/read as text.
  let binaryTextReads = 0;
  const binaryResponse = {
    url: 'https://cdn.example/movie.mp4',
    headers: { get(name) { return String(name).toLowerCase() === 'content-type' ? 'video/mp4' : null; } },
    clone() { binaryTextReads++; return { text: async () => 'binary' }; }
  };
  const binaryWindow = makeWindow(topLocation, topWindow, async () => binaryResponse, null);
  binaryWindow.top = topWindow;
  runInWindow(binaryWindow);
  await binaryWindow.fetch('https://cdn.example/movie.mp4');
  await new Promise(resolve => setImmediate(resolve));
  if (binaryTextReads !== 0) throw new Error('Binary media response was read as text');

  // If streams are unavailable and Content-Length is unknown, do not materialize the whole body.
  let unknownSizeTextReads = 0;
  const unknownSizeResponse = {
    url: 'https://watch.example/api/unknown-size',
    headers: { get(name) { return String(name).toLowerCase() === 'content-type' ? 'application/json' : null; } },
    clone() { return { headers: this.headers, text: async () => { unknownSizeTextReads++; return apiJson; } }; }
  };
  const unknownSizeWindow = makeWindow(topLocation, topWindow, async () => unknownSizeResponse, null);
  unknownSizeWindow.top = unknownSizeWindow;
  runInWindow(unknownSizeWindow);
  await unknownSizeWindow.fetch('/api/unknown-size');
  await new Promise(resolve => setImmediate(resolve));
  if (unknownSizeTextReads !== 0) throw new Error('Unknown-size non-stream response was fully materialized');

  // The streaming path must cancel a cloned response as soon as it crosses the 700 KB cap.
  let oversizedStreamCancelled = false;
  const oversizedStreamResponse = {
    url: 'https://watch.example/api/oversized',
    headers: { get(name) { return String(name).toLowerCase() === 'content-type' ? 'application/json' : null; } },
    clone() {
      return {
        headers: this.headers,
        body: { getReader() { return {
          async read() { return { done: false, value: new Uint8Array(700001) }; },
          async cancel() { oversizedStreamCancelled = true; }
        }; } }
      };
    }
  };
  const oversizedWindow = makeWindow(topLocation, topWindow, async () => oversizedStreamResponse, null);
  oversizedWindow.top = oversizedWindow;
  runInWindow(oversizedWindow);
  await oversizedWindow.fetch('/api/oversized');
  await new Promise(resolve => setImmediate(resolve));
  if (!oversizedStreamCancelled) throw new Error('Oversized text stream was not cancelled at the configured cap');

  console.log('Early API capture passed: document-start Fetch, native cross-origin iframe bridge, XHR, safe headers, no page-message leak, binary exclusion, and bounded response reads');
}

main().catch(error => {
  console.error(error);
  process.exit(1);
});
