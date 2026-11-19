package com.example.videobrowser

/**
 * Installed by WebViewCompat at document-start in the main frame and matching child frames.
 * Child-frame payloads go to the native WebMessageListener, never to the parent page via postMessage.
 * It observes only bounded text-like API/manifest responses; media bytes are never read.
 */
internal const val EARLY_API_CAPTURE_JS = """
(function () {
  'use strict';
  try {
    if (!/^https?:$/i.test(String(location.protocol || ''))) return;
    if (window.__vbApiObserverInstalled) return;
    window.__vbApiObserverInstalled = true;
    window.__vbApiPayloads = window.__vbApiPayloads || [];

    var MAX_TEXT = 700000;
    var MAX_ITEMS = 16;
    var SAFE_HEADERS = ['accept', 'accept-language'];
    var RELEVANT = /#EXTM3U|(?:sources|qualities|renditions|formats|m3u8|mpd|video[_-]?url|videoUrl|contentUrl|resolution|height|playbackUrl)/i;

    function absoluteUrl(raw) {
      try { return new URL(String(raw || ''), location.href).href; }
      catch (_) { return ''; }
    }

    function cleanPageUrl(raw) {
      try {
        var u = new URL(String(raw || location.href));
        if (u.protocol !== 'http:' && u.protocol !== 'https:') return '';
        u.search = '';
        u.hash = '';
        return u.href;
      } catch (_) { return ''; }
    }

    function safeHeaders(rawHeaders) {
      var result = {};
      try {
        if (rawHeaders) {
          var h = new Headers(rawHeaders);
          for (var i = 0; i < SAFE_HEADERS.length; i++) {
            var name = SAFE_HEADERS[i];
            var value = h.get(name);
            if (value) result[name === 'accept' ? 'Accept' : 'Accept-Language'] = String(value).slice(0, 4096);
          }
        }
      } catch (_) {}
      // These are page context, not credential headers. Never copy Cookie or Authorization.
      var referer = cleanPageUrl(location.href);
      if (referer) result.Referer = referer;
      try {
        if (location.origin && location.origin !== 'null') result.Origin = String(location.origin).slice(0, 4096);
      } catch (_) {}
      return result;
    }

    function isCandidateResponse(url, contentType, response) {
      var ct = String(contentType || '').toLowerCase();
      var value = String(url || '').toLowerCase();
      var manifestPath = /\.(?:m3u8|mpd)(?:[?#]|$)/i.test(value);
      if (/^(?:video|audio|image)\//i.test(ct)) return false;
      if (/application\/octet-stream/i.test(ct) && !manifestPath) return false;
      if (manifestPath) return true;
      if (/(?:json|text|xml|mpegurl|dash\+xml)/i.test(ct)) return true;
      // Some cross-origin APIs do not expose Content-Type to JavaScript.
      return /(?:\/api\/|\/graphql|player|video|stream|manifest|quality|qualities|rendition|playback|source|format)/i.test(value);
    }

    function putOnTop(payload) {
      try {
        var isTop = window === window.top;
        if (!isTop) {
          // Cross-origin payloads must not be exposed to the embedding page's message listeners.
          // WebViewCompat injects this native bridge into matching frames when supported.
          var bridge = window.VideoBrowserCapture;
          if (bridge && typeof bridge.postMessage === 'function') {
            bridge.postMessage(JSON.stringify(payload));
          }
          return;
        }
        // Same-frame payloads stay in this frame's private queue for SCAN_JS.
        acceptPayload(payload, payload.frameOrigin || location.origin);
      } catch (_) {}
    }

    function acceptPayload(payload, sourceOrigin) {
      try {
        if (!payload || typeof payload !== 'object') return;
        var requestUrl = absoluteUrl(payload.url);
        var text = typeof payload.text === 'string' ? payload.text : '';
        if (!requestUrl || !/^https?:/i.test(requestUrl) || !text || text.length > MAX_TEXT || !RELEVANT.test(text)) return;
        var expectedOrigin = String(payload.frameOrigin || '');
        if (sourceOrigin && expectedOrigin && sourceOrigin !== expectedOrigin) return;
        var list = window.__vbApiPayloads || (window.__vbApiPayloads = []);
        var key = requestUrl + '|' + String(payload.method || 'GET') + '|' + text.length + '|' + text.substring(0, 96);
        if (list.some(function (item) { return item && item.key === key; })) return;
        list.push({
          key: key,
          url: requestUrl,
          text: text,
          at: Number(payload.at || Date.now()),
          type: String(payload.type || ''),
          method: String(payload.method || 'GET').toUpperCase(),
          headers: payload.headers && typeof payload.headers === 'object' ? payload.headers : {},
          source: String(payload.source || 'fetch-api'),
          frameOrigin: expectedOrigin
        });
        while (list.length > MAX_ITEMS) list.shift();
      } catch (_) {}
    }

    function rememberPayload(url, text, contentType, method, headers, source) {
      try {
        if (!text || text.length > MAX_TEXT || !RELEVANT.test(text)) return;
        putOnTop({
          url: absoluteUrl(url),
          text: text,
          at: Date.now(),
          type: String(contentType || ''),
          method: String(method || 'GET').toUpperCase(),
          headers: headers || {},
          source: source || 'fetch-api',
          frameOrigin: String(location.origin || '')
        });
      } catch (_) {}
    }

    function boundedText(response) {
      var copy;
      try { copy = response.clone(); } catch (_) { return Promise.resolve(null); }
      var declaredLength = -1;
      try {
        var rawLength = copy.headers && copy.headers.get('content-length');
        if (rawLength !== null && rawLength !== undefined && rawLength !== '') {
          declaredLength = Number(rawLength);
        }
        if (Number.isFinite(declaredLength) && declaredLength > MAX_TEXT) return Promise.resolve(null);
      } catch (_) {}
      try {
        if (copy.body && typeof copy.body.getReader === 'function' && typeof TextDecoder !== 'undefined') {
          return (async function () {
            var reader = copy.body.getReader();
            var chunks = [];
            var total = 0;
            try {
              while (true) {
                var part = await reader.read();
                if (part.done) break;
                if (!part.value) continue;
                total += part.value.byteLength;
                if (total > MAX_TEXT) {
                  try { await reader.cancel(); } catch (_) {}
                  return null;
                }
                chunks.push(part.value);
              }
              var joined = new Uint8Array(total);
              var offset = 0;
              for (var i = 0; i < chunks.length; i++) {
                joined.set(chunks[i], offset);
                offset += chunks[i].byteLength;
              }
              return new TextDecoder('utf-8').decode(joined);
            } catch (_) { return null; }
          })();
        }
      } catch (_) {}
      // Without a streaming reader, do not materialize an unknown-size response body.
      // Older WebView fallback is allowed only when Content-Length proves it is bounded.
      if (!Number.isFinite(declaredLength) || declaredLength < 0 || declaredLength > MAX_TEXT) {
        return Promise.resolve(null);
      }
      try {
        return copy.text().then(function (text) {
          return text && text.length <= MAX_TEXT ? text : null;
        }).catch(function () { return null; });
      } catch (_) { return Promise.resolve(null); }
    }

    if (typeof window.fetch === 'function' && !window.fetch.__vbEarlyCaptureWrapped) {
      var originalFetch = window.fetch;
      var wrappedFetch = function (input, init) {
        var requestUrl = '';
        var method = 'GET';
        var requestHeaders = {};
        try {
          requestUrl = typeof input === 'string' || input instanceof URL ? String(input) : String((input && input.url) || '');
          method = String((init && init.method) || (input && input.method) || 'GET').toUpperCase();
          requestHeaders = safeHeaders(init && Object.prototype.hasOwnProperty.call(init, 'headers') ? init.headers : (input && input.headers));
        } catch (_) {}
        return originalFetch.apply(this, arguments).then(function (response) {
          try {
            var contentType = response && response.headers ? (response.headers.get('content-type') || '') : '';
            var finalUrl = response && response.url ? response.url : requestUrl;
            if (response && isCandidateResponse(finalUrl || requestUrl, contentType, response)) {
              boundedText(response).then(function (text) {
                if (text) rememberPayload(finalUrl || requestUrl, text, contentType, method, requestHeaders, 'fetch-api');
              }).catch(function () {});
            }
          } catch (_) {}
          return response;
        });
      };
      wrappedFetch.__vbEarlyCaptureWrapped = true;
      wrappedFetch.__vbOriginal = originalFetch;
      window.fetch = wrappedFetch;
    }

    if (window.XMLHttpRequest && window.XMLHttpRequest.prototype && !window.XMLHttpRequest.prototype.__vbEarlyCaptureWrapped) {
      var xhrProto = window.XMLHttpRequest.prototype;
      var originalOpen = xhrProto.open;
      var originalSend = xhrProto.send;
      var originalSetRequestHeader = xhrProto.setRequestHeader;
      xhrProto.open = function (method, url) {
        try {
          this.__vbCapture = {
            url: absoluteUrl(url),
            method: String(method || 'GET').toUpperCase(),
            headers: safeHeaders(null)
          };
        } catch (_) { this.__vbCapture = null; }
        return originalOpen.apply(this, arguments);
      };
      if (typeof originalSetRequestHeader === 'function') {
        xhrProto.setRequestHeader = function (name, value) {
          try {
            var key = String(name || '').toLowerCase();
            if (key === 'accept') this.__vbCapture && (this.__vbCapture.headers.Accept = String(value).slice(0, 4096));
            if (key === 'accept-language') this.__vbCapture && (this.__vbCapture.headers['Accept-Language'] = String(value).slice(0, 4096));
          } catch (_) {}
          return originalSetRequestHeader.apply(this, arguments);
        };
      }
      xhrProto.send = function () {
        var xhr = this;
        try {
          var state = xhr.__vbCapture;
          if (state) {
            xhr.addEventListener('load', function () {
              try {
                var contentType = xhr.getResponseHeader('content-type') || '';
                if (!isCandidateResponse(xhr.responseURL || state.url, contentType, xhr)) return;
                var body = '';
                if (xhr.responseType === '' || xhr.responseType === 'text') body = xhr.responseText || '';
                else if (xhr.responseType === 'json' && xhr.response) body = JSON.stringify(xhr.response);
                if (body && body.length <= MAX_TEXT) {
                  rememberPayload(xhr.responseURL || state.url, body, contentType, state.method, state.headers, 'xhr-api');
                }
              } catch (_) {}
            }, { once: true });
          }
        } catch (_) {}
        return originalSend.apply(this, arguments);
      };
      xhrProto.__vbEarlyCaptureWrapped = true;
    }

    // Cross-origin players may contain the only VIDEO element while the top document has none.
    // Report bounded, non-secret player state directly to the native WebMessageListener.
    if (window !== window.top && !window.__vbFrameVideoObserverInstalled) {
      window.__vbFrameVideoObserverInstalled = true;
      var frameId = Math.random().toString(36).slice(2) + Date.now().toString(36);
      var frameVideoCounter = 0;
      var frameVideoLast = Object.create(null);
      var pointerState = { x: -1, y: -1, at: 0 };
      var frameAdPattern = /(?:doubleclick|googlesyndication|googleadservices|imasdk|vasttag|adservice|adserver|preroll|midroll|postroll)/i;

      function frameVisible(video) {
        try {
          var style = getComputedStyle(video);
          if (style.display === 'none' || style.visibility === 'hidden' || Number(style.opacity || 1) === 0) return false;
          var r = video.getBoundingClientRect();
          return r.width >= 70 && r.height >= 45 && r.right > 0 && r.bottom > 0 &&
            r.left < window.innerWidth && r.top < window.innerHeight;
        } catch (_) { return false; }
      }

      function frameAd(video, src) {
        if (frameAdPattern.test(String(src || ''))) return true;
        try {
          var node = video;
          for (var depth = 0; node && depth < 7; depth++, node = node.parentElement) {
            var marker = String(node.id || '') + ' ' + String(node.className || '') + ' ' +
              String(node.getAttribute && (node.getAttribute('data-ad') || node.getAttribute('data-ad-playing')) || '');
            if (/(^|[\s_-])(ima-ad-container|video-ads|ad-container|ad-player|ad-playing|advertisement|jw-ad|vjs-ad-playing|ad-showing|preroll|midroll|postroll)(?=$|[\s_-])/i.test(marker)) return true;
          }
        } catch (_) {}
        return false;
      }

      function frameMediaSources(video) {
        var result = [];
        var seen = Object.create(null);
        function add(raw, meta) {
          try {
            var value = String(raw || '').trim();
            if (!value || /^blob:/i.test(value) || /^data:/i.test(value)) return;
            var url = new URL(value, document.baseURI).href;
            if (!/^https?:/i.test(url) || frameAdPattern.test(url) || isPreviewFrameUrl(url)) return;
            if (!seen[url] && result.length < 32) {
              seen[url] = true;
              result.push({ url: url, meta: meta || {} });
            }
          } catch (_) {}
        }
        function isPreviewFrameUrl(url) {
          return /(?:^|[\/_.?&=-])(preview|thumbnail|thumb|poster|trailer|teaser|sprite)(?:[\/_.?&=-]|$)/i.test(String(url || ''));
        }
        try {
          add(video.currentSrc || video.getAttribute('src'), {
            mime: 'video', width: Number(video.videoWidth || 0), height: Number(video.videoHeight || 0)
          });
        } catch (_) {}
        try {
          var children = video.querySelectorAll('source[src],source[data-src]');
          for (var i = 0; i < Math.min(children.length, 32); i++) {
            var node = children[i];
            var label = node.getAttribute('label') || node.getAttribute('data-quality') || '';
            var height = Number(node.getAttribute('data-height') || node.getAttribute('height') || 0);
            var type = node.getAttribute('type') || '';
            add(node.getAttribute('src') || node.getAttribute('data-src'), { label: label, height: height, mime: type });
          }
        } catch (_) {}
        try {
          var node = video.parentElement;
          for (var depth = 0; node && depth < 5; depth++, node = node.parentElement) {
            ['data-video-url','data-stream-url','data-hls','data-m3u8','data-file','data-src'].forEach(function(attr){
              var raw = node.getAttribute && node.getAttribute(attr);
              if (raw) add(raw, { mime: /m3u8/i.test(attr) ? 'application/vnd.apple.mpegurl' : '' });
            });
          }
        } catch (_) {}
        return result;
      }

      function sendFrameVideoState() {
        try {
          var bridge = window.VideoBrowserCapture;
          if (!bridge || typeof bridge.postMessage !== 'function') return;
          var nodes = document.querySelectorAll('video');
          var videos = [];
          for (var i = 0; i < Math.min(nodes.length, 8); i++) {
            var video = nodes[i];
            if (!video) continue;
            if (!video.__vbFrameVideoId) video.__vbFrameVideoId = 'fv-' + (++frameVideoCounter).toString(36);
            var src = String(video.currentSrc || video.getAttribute('src') || '');
            var sources = frameMediaSources(video);
            var sourceKey = src + '||' + sources.map(function(item){ return item.url; }).join('||');
            var previous = frameVideoLast[video.__vbFrameVideoId] || { key: '', epoch: 0, cycle: 0, sentAt: 0 };
            if (previous.key && previous.key !== sourceKey) previous.epoch++;
            if (video.ended && !previous.ended) previous.cycle++;
            previous.key = sourceKey;
            previous.ended = !!video.ended;
            var rect = video.getBoundingClientRect();
            var playing = !video.paused && !video.ended;
            var inBounds = frameVisible(video);
            var gesture = Date.now() - pointerState.at <= 12000 && pointerState.x >= rect.left && pointerState.x <= rect.right &&
              pointerState.y >= rect.top && pointerState.y <= rect.bottom;
            var currentAd = frameAd(video, src);
            var meta = sources.map(function(item){ return { url:item.url, label:item.meta.label || '',
              width:Number(item.meta.width || 0), height:Number(item.meta.height || 0), mime:item.meta.mime || '' }; });
            videos.push({
              id: video.__vbFrameVideoId, src: src, sources: sources.map(function(item){ return item.url; }),
              sourceMeta: meta, poster: String(video.poster || ''),
              duration: Number.isFinite(Number(video.duration)) ? Number(video.duration) : 0,
              currentTime: Number(video.currentTime || 0), paused: !!video.paused, ended: !!video.ended,
              playing: playing, visible: inBounds, area: Math.max(0, Number(rect.width || 0) * Number(rect.height || 0)),
              width: Number(video.videoWidth || 0), height: Number(video.videoHeight || 0),
              ad: currentAd, gesture: gesture, sourceEpoch: previous.epoch, cycle: previous.cycle,
              blob: /^blob:/i.test(src)
            });
            if (Date.now() - previous.sentAt > 900 || playing || sourceKey !== previous.lastSentKey) {
              previous.sentAt = Date.now();
              previous.lastSentKey = sourceKey;
            }
            frameVideoLast[video.__vbFrameVideoId] = previous;
          }
          if (!videos.length) return;
          bridge.postMessage(JSON.stringify({
            kind: 'frame-video-state', frameId: frameId, frameOrigin: String(location.origin || ''),
            at: Date.now(), videos: videos
          }));
        } catch (_) {}
      }

      function framePointer(event) {
        try {
          var p = event.touches && event.touches.length ? event.touches[0] : event;
          pointerState = { x: Number(p.clientX || -1), y: Number(p.clientY || -1), at: Date.now() };
          sendFrameVideoState();
        } catch (_) {}
      }
      document.addEventListener('pointerdown', framePointer, true);
      document.addEventListener('touchstart', framePointer, true);
      ['play','playing','loadedmetadata','loadeddata','durationchange','emptied','ended','pause','canplay'].forEach(function(name){
        document.addEventListener(name, function(){ sendFrameVideoState(); }, true);
      });
      try {
        if (window.MutationObserver) {
          new MutationObserver(function(){ sendFrameVideoState(); }).observe(document, { childList: true, subtree: true });
        }
      } catch (_) {}
      if (window.setInterval) window.setInterval(sendFrameVideoState, 1200);
      sendFrameVideoState();
    }
  } catch (_) {}
})();
"""
