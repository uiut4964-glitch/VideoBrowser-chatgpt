
(function(){
  try {
    var isHttp = function(u){ return /^https?:\/\//i.test(String(u || '')); };
    var absUrl = function(u){
      try { return new URL(String(u || ''), document.baseURI).href; }
      catch (_) { return String(u || ''); }
    };
    var isAdUrl = function(raw){
      var s = String(raw || '').toLowerCase();
      try { s = decodeURIComponent(s); } catch (_) {}
      if (/(doubleclick|googlesyndication|googleadservices|adservice|adserver|imasdk|vasttag|spotx|freewheel|adnxs|adsystem|advertising)/i.test(s)) return true;
      try {
        var host = new URL(s, document.baseURI).hostname.toLowerCase();
        var adHosts = ['doubleclick.net','googlesyndication.com','googleadservices.com','adsrvr.org',
          'taboola.com','outbrain.com','criteo.com','pubmatic.com','rubiconproject.com','openx.net',
          'moatads.com','zedo.com','adnxs.com','spotx.tv','freewheel.tv'];
        if (adHosts.some(function(h){ return host === h || host.endsWith('.' + h); })) return true;
      } catch (_) {}
      if (/[?&](ad|adtag|adurl|ad_type|adtype|vast|vmap|preroll|midroll|postroll)=/i.test(s)) return true;
      return /(?:^|[\/_.?&=-])(ads?|advert|advertisement|preroll|midroll|postroll|vast|vmap)(?:[\/_.?&=-]|$)/i.test(s);
    };
    var isChunkUrl = function(raw){
      var u = absUrl(raw);
      var p = '';
      try { p = new URL(u).pathname.toLowerCase(); } catch (_) {}
      var n = p.substring(p.lastIndexOf('/') + 1);
      return /\.(ts|m4s|cmfv|cmfa|m4v-frag)$/i.test(p) ||
        /(?:^|[-_.])(chunk|chunks|segment|segments|fragment|fragments|part|seg)(?:[-_.]?[0-9]*)(?:[-_.]|$)/i.test(n);
    };
    var isMediaUrl = function(raw){
      var u = absUrl(raw);
      var p = '';
      try { p = new URL(u).pathname.toLowerCase(); } catch (_) {}
      return /\.(m3u8|mp4|m4v|webm|mkv|mov|flv|3gp)(?:$)/i.test(p) || /\/videoplayback$/i.test(p);
    };
    var hasAdMarker = function(el){
      var n = el;
      for (var depth = 0; n && depth < 7; depth++, n = n.parentElement) {
        var id = n.id || '';
        var cls = typeof n.className === 'string' ? n.className : '';
        var role = n.getAttribute ? (n.getAttribute('data-ad') || '') : '';
        var flag = n.getAttribute ? (n.getAttribute('data-ad-playing') || '') : '';
        var sig = id + ' ' + cls + ' ' + role + ' ' + flag;
        if (/(^|[\s_-])(ima-ad-container|video-ads|ad-container|ad-player|ad-playing|advertisement|advertising|jw-ad|vjs-ad-playing|ad-showing|preroll|midroll|postroll)(?=$|[\s_-])/i.test(sig)) return true;
        if (role && role !== 'false' && role !== '0' && role !== 'none') return true;
        if (flag === 'true' || flag === '1') return true;
      }
      return false;
    };
    var hasVisibleAdOverlay = function(v){
      try {
        var selectors = '.ima-ad-container,.video-ads,.jw-ad,.jw-flag-ads,.vjs-ad-playing,.ad-showing,.shaka-ad-container,[class*="ad-container" i],[id*="ad-container" i],[class*="ad-playing" i],[data-ad-playing="true"],[aria-label*="advertisement" i]';
        var layers = document.querySelectorAll(selectors);
        var vr = v.getBoundingClientRect();
        for (var i = 0; i < Math.min(layers.length, 20); i++) {
          var layer = layers[i];
          if (!visible(layer)) continue;
          var lr = layer.getBoundingClientRect();
          var iw = Math.max(0, Math.min(vr.right, lr.right) - Math.max(vr.left, lr.left));
          var ih = Math.max(0, Math.min(vr.bottom, lr.bottom) - Math.max(vr.top, lr.top));
          var overlap = iw * ih;
          var videoArea = Math.max(1, vr.width * vr.height);
          if (overlap / videoArea > 0.18) return true;
        }
      } catch (_) {}
      return false;
    };
    var visible = function(el){
      if (!el || !el.getBoundingClientRect) return false;
      var cs = getComputedStyle(el);
      if (cs.display === 'none' || cs.visibility === 'hidden' || Number(cs.opacity || 1) === 0) return false;
      var r = el.getBoundingClientRect();
      var iw = Math.max(0, Math.min(r.right, window.innerWidth) - Math.max(r.left, 0));
      var ih = Math.max(0, Math.min(r.bottom, window.innerHeight) - Math.max(r.top, 0));
      return r.width >= 70 && r.height >= 45 && iw >= 55 && ih >= 35;
    };
    var isKnownNonVideoUrl = function(raw){
      var p = '';
      try { p = new URL(absUrl(raw)).pathname.toLowerCase(); } catch (_) {}
      return /\.(jpg|jpeg|png|gif|webp|avif|svg|ico|css|js|json|vtt|srt|woff2?|ttf|otf)(?:$)/i.test(p);
    };
    var isPreviewUrl = function(raw){
      var s = String(raw || '').toLowerCase();
      try { s = decodeURIComponent(s); } catch (_) {}
      return /(?:^|[\/_.?&=-])(preview|previews|thumbnail|thumb|thumbs|poster|trailer|trailers|teaser|teasers|storyboard|sprite)(?:[\/_.?&=-]|$)/i.test(s);
    };
    var isPlayerMediaUrl = function(raw, trustedVideoContext){
      if (isMediaUrl(raw)) return true;
      if (!trustedVideoContext || isKnownNonVideoUrl(raw)) return false;
      var u = absUrl(raw);
      if (!isHttp(u)) return false;
      // بعض CDN endpoints لا تضع امتداد الملف في المسار؛ نقبلها فقط عندما تأتي
      // من كائن مصدر فيديو صريح يحوي type/height/quality أو من حقل file/manifest.
      return !isChunkUrl(u);
    };
    var mergeQualityMeta = function(raw, inherited, keyHint){
      var meta = Object.assign({}, inherited || {});
      if (typeof raw === 'object' && raw) {
        var label = raw.label || raw.quality || raw.resolution || raw.qualityLabel || raw.formatLabel || '';
        if (label && /(?:\d{3,4}\s*p|\d{3,4}\s*x\s*\d{3,4}|2k|4k|uhd|qhd|fhd|hd)/i.test(String(label))) meta.label = String(label).trim();
        var w = Number(raw.width || raw.videoWidth || raw.w || 0);
        var h = Number(raw.height || raw.videoHeight || raw.video_height || raw.h || 0);
        if ((!w || !h) && typeof raw.resolution === 'string') {
          var dims = /(?:^|\D)(\d{3,4})\s*x\s*(\d{3,4})(?:\D|$)/i.exec(raw.resolution);
          if (dims) { w = w || Number(dims[1]); h = h || Number(dims[2]); }
        }
        if (w > 0 && w < 10000) meta.width = w;
        if (h > 0 && h < 10000) meta.height = h;
        var mime = raw.mimeType || raw.mime_type || raw.contentType || raw.content_type || raw.type || '';
        if (mime && /^(video\/|application\/(?:vnd\.apple\.mpegurl|x-mpegurl|dash\+xml)|audio\/)/i.test(String(mime))) meta.mime = String(mime);
        var codec = raw.codecs || raw.codec || raw.videoCodec || raw.video_codec || raw.videoCodecName || '';
        if (codec) meta.codec = String(codec).slice(0, 160);
        var fps = Number(raw.frameRate || raw.frame_rate || raw.fps || raw.frameRateHint || 0);
        if (fps > 0 && fps <= 240) meta.frameRate = fps;
        var range = raw.videoRange || raw.video_range || raw.dynamicRange || raw.hdr || raw.colorSpace || '';
        if (range) meta.dynamicRange = String(range).slice(0, 80);
        var bandwidth = Number(raw.averageBandwidth || raw.average_bandwidth || raw.bandwidth || raw.bitrate || 0);
        if (bandwidth > 0 && bandwidth < 1000000000) meta.bandwidth = bandwidth;
        var representationId = raw.representationId || raw.representation_id || raw.formatId || raw.itag || raw.id || '';
        if (representationId !== '') meta.representationId = String(representationId).slice(0, 120);
      }
      if (keyHint) {
        var key = String(keyHint).trim();
        if (/^(\d{3,4})p?$/i.test(key)) {
          var kh = Number(key.match(/\d+/)[0]);
          meta.height = meta.height || kh;
          meta.label = meta.label || (kh + 'p');
        } else if (/^(2k|qhd)$/i.test(key)) {
          meta.height = meta.height || 1440;
          meta.label = meta.label || '1440p 2K';
        } else if (/^(4k|uhd)$/i.test(key)) {
          meta.height = meta.height || 2160;
          meta.label = meta.label || '2160p 4K';
        }
      }
      if (meta.height && !meta.label) meta.label = meta.height + 'p';
      return meta;
    };
    var parsePlayerConfig = function(raw, add, depth, videoContext, inheritedMeta, keyHint){
      if (raw === null || raw === undefined || depth > 10) return;
      var trusted = !!videoContext;
      var meta = mergeQualityMeta(raw, inheritedMeta, keyHint);
      if (typeof raw === 'string') {
        var value = raw.trim();
        if (!value) return;
        if ((value.charAt(0) === '{' || value.charAt(0) === '[') && value.length < 750000) {
          try { parsePlayerConfig(JSON.parse(value), add, depth + 1, trusted, meta); return; } catch (_) {}
        }
        if (isPlayerMediaUrl(value, trusted)) add(value, meta);
        return;
      }
      if (Array.isArray(raw)) {
        for (var i = 0; i < Math.min(raw.length, 240); i++) parsePlayerConfig(raw[i], add, depth + 1, trusted, meta);
        return;
      }
      if (typeof raw !== 'object') return;
      var badKey = /(^|[_-])(ad|ads|advert|advertisement|vast|vmap|preroll|midroll|postroll|promo|preview|thumb|poster|trailer|teaser|sponsor)([_-]|$)|^(ad|ads|advert|vast|vmap|preroll|midroll|postroll|promo|preview|thumb|poster|trailer|teaser|sponsor)/i;
      var sourceKey = /(file|src|url|uri|link|download|path|endpoint|playback|hls|m3u8|manifest|source|playlist|contenturl|stream|video|mp4|webm|dash|mpd|qualityurl|formaturl)/i;
      var typeText = String(raw.type || raw.mimeType || raw.mime_type || raw.contentType || raw.content_type || raw.kind || meta.mime || '').toLowerCase();
      var labelText = String(raw.label || raw.quality || raw.resolution || raw.qualityLabel || meta.label || '').toLowerCase();
      var hasDimensions = Number(raw.height || raw.videoHeight || raw.width || raw.videoWidth || meta.height || meta.width || 0) > 0;
      var hasVideoHint = trusted || /video|mpegurl|m3u8|dash|mp4|webm|quicktime/.test(typeText) ||
        (hasDimensions && !/audio/.test(typeText)) || /^(\d{3,4}\s*p|uhd|hd|sd|4k|2k|qhd|fhd)$/i.test(labelText);
      var explicitlyAudio = /audio|aac|mp3|opus/.test(typeText);
      var collectionKey = /^(sources|qualities|formats|renditions|variants|streams|tracks)$/i;
      var keys = Object.keys(raw);
      for (var k = 0; k < Math.min(keys.length, 240); k++) {
        var key = keys[k];
        if (badKey.test(key)) continue;
        var value = raw[key];
        var keyIsSource = sourceKey.test(key);
        var keyIsQuality = /^(\d{3,4}p?|2k|4k|qhd|uhd)$/i.test(key);
        var childVideoContext = !explicitlyAudio && (hasVideoHint || collectionKey.test(key) || keyIsQuality || /^(file|hls|m3u8|manifest|playlist|videoUrl|video_url|contentUrl|content_url)$/i.test(key));
        var childMeta = mergeQualityMeta(value, meta, keyIsQuality ? key : '');
        if (typeof value === 'string' && (keyIsSource || (trusted && keyIsQuality))) parsePlayerConfig(value, add, depth + 1, childVideoContext, childMeta, keyIsQuality ? key : '');
        else if (value && typeof value === 'object') parsePlayerConfig(value, add, depth + 1, childVideoContext, childMeta, keyIsQuality ? key : '');
      }
    };
    var extractPlayerScriptUrls = function(text, add){
      if (!text || text.length > 900000) return;
      // JSON configs are handled structurally first.
      parsePlayerConfig(text, add, 0, false);
      // Also handle common JS setup syntax that is not valid JSON, but only named media fields.
      var re = /(?:file|src|url|uri|link|download|downloadUrl|download_url|playbackUrl|playback_url|manifest|hls|m3u8|playlist|videoUrl|video_url|contentUrl|content_url|sourceUrl|source_url|qualityUrl|quality_url)\s*[:=]\s*['\"](https?:\\?\/\/[^'\"]+)['\"]/gi;
      var match;
      while ((match = re.exec(text)) !== null) {
        var candidate = match[1].replace(/\\\//g, '/').replace(/\\u0026/gi, '&');
        if (isPlayerMediaUrl(candidate, true)) add(candidate);
      }
      var qualityMap = /(?:['\"]?(\d{3,4}p?|2k|4k|qhd|uhd)['\"]?)\s*:\s*['\"](https?:\\?\/\/[^'\"]+)['\"]/gi;
      while ((match = qualityMap.exec(text)) !== null) {
        var qualityUrl = match[2].replace(/\\\//g, '/').replace(/\\u0026/gi, '&');
        if (isPlayerMediaUrl(qualityUrl, true)) {
          var ql = match[1];
          var qn = /^\d/.test(ql) ? Number(ql.match(/\d+/)[0]) : (/^(2k|qhd)$/i.test(ql) ? 1440 : 2160);
          add(qualityUrl, {label: ql, height: qn});
        }
      }
    };
    var collectSources = function(v, apiOnly){
      var out = [];
      var seen = Object.create(null);
      var sourceMeta = Object.create(null);
      var activeApiContext = null;
      var flashvarsObjectCount = 0;
      var flashvarsDefinitionCount = 0;
      var flashvarsQualities = [];
      var flashvarsVideoDuration = 0;
      var flashvarsExplicitAd = false;
      var add = function(raw, meta){
        var u = absUrl(raw);
        if (!isHttp(u) || isAdUrl(u) || isPreviewUrl(u) || isChunkUrl(u) || !isPlayerMediaUrl(u, true)) return;
        var incoming = mergeQualityMeta(null, meta || {}, '');
        if (!seen[u]) { seen[u] = true; out.push(u); }
        var existing = sourceMeta[u] || {};
        if (incoming.label && (!existing.label || !/\d/.test(existing.label))) existing.label = incoming.label;
        if (incoming.width && !existing.width) existing.width = incoming.width;
        if (incoming.height && !existing.height) existing.height = incoming.height;
        if (incoming.mime && !existing.mime) existing.mime = incoming.mime;
        if (incoming.codec && !existing.codec) existing.codec = incoming.codec;
        if (incoming.frameRate && !existing.frameRate) existing.frameRate = incoming.frameRate;
        if (incoming.dynamicRange && !existing.dynamicRange) existing.dynamicRange = incoming.dynamicRange;
        if (incoming.bandwidth && !existing.bandwidth) existing.bandwidth = incoming.bandwidth;
        if (incoming.representationId && !existing.representationId) existing.representationId = incoming.representationId;
        if (meta && meta.captureSource && !existing.captureSource) existing.captureSource = String(meta.captureSource).slice(0, 80);
        if (activeApiContext) {
          if (activeApiContext.headers && Object.keys(activeApiContext.headers).length) {
            existing.requestHeaders = Object.assign({}, existing.requestHeaders || {}, activeApiContext.headers);
          }
          if (activeApiContext.source) existing.captureSource = activeApiContext.source;
        }
        sourceMeta[u] = existing;
      };
      if (!apiOnly) {
      // 1) إعدادات المحتوى المسبقة على عنصر الفيديو والحاويات القريبة.
      var n = v;
      var playerRoot = v.parentElement || document;
      for (var depth = 0; n && depth < 7; depth++, n = n.parentElement) {
        if (!n.getAttribute) continue;
        var attrs = [
          'data-src','data-file','data-video','data-video-url','data-content-url',
          'data-hls','data-m3u8','data-manifest','data-stream-url','data-file-url',
          'data-sources','data-renditions','data-formats','data-qualities',
          'data-player-config','data-setup','data-config','data-options'
        ];
        for (var a = 0; a < attrs.length; a++) {
          var attrValue = n.getAttribute(attrs[a]);
          if (!attrValue) continue;
          if (/(config|setup|options|sources|renditions|formats|qualities)/i.test(attrs[a])) {
            parsePlayerConfig(attrValue, add, 0, true);
          } else add(attrValue);
        }
        var cls = typeof n.className === 'string' ? n.className : '';
        var marker = (n.id || '') + ' ' + cls;
        if (depth > 0 && /(player|video|media|watch|stream|jwplayer|video-js|plyr)/i.test(marker)) {
          playerRoot = n;
          break;
        }
      }
      // 2) مصادر source داخل الفيديو أو حاوية المشغل، بما فيها عناصر الجودة البديلة.
      var children = playerRoot && playerRoot.querySelectorAll ? playerRoot.querySelectorAll('source[src],source[data-src]') :
        (v.querySelectorAll ? v.querySelectorAll('source[src],source[data-src]') : []);
      for (var i = 0; i < Math.min(children.length, 80); i++) {
        var childType = (children[i].getAttribute('type') || '').toLowerCase();
        if (!childType || childType.indexOf('video/') === 0 || childType.indexOf('application/vnd.apple.mpegurl') === 0) {
          add(children[i].getAttribute('src') || children[i].getAttribute('data-src'), {mime: childType});
        }
      }
      var qualityNodes = playerRoot && playerRoot.querySelectorAll ?
        playerRoot.querySelectorAll('[data-quality-url],[data-quality][data-url],[data-resolution][data-url],[data-height][data-src],[data-file][data-quality],option[value]') : [];
      for (var qn = 0; qn < Math.min(qualityNodes.length, 80); qn++) {
        var qel = qualityNodes[qn];
        var qurl = qel.getAttribute('data-quality-url') || qel.getAttribute('data-url') ||
          qel.getAttribute('data-src') || qel.getAttribute('data-file') || qel.value || '';
        if (!qurl) continue;
        var qheight = Number(qel.getAttribute('data-height') || qel.getAttribute('data-resolution') || 0);
        var qlabel = qel.getAttribute('data-quality') || qel.getAttribute('label') || qel.textContent || '';
        parsePlayerConfig({src:qurl, type:'video', height:qheight, quality:qlabel}, add, 0, true);
      }
      // 3) إعداد JSON/VideoObject داخل نطاق المشغل، وليس جميع روابط الصفحة عشوائيًا.
      var scripts = playerRoot && playerRoot.querySelectorAll ? playerRoot.querySelectorAll('script[type="application/json"],script[type="application/ld+json"],script[id*="player" i],script[id*="video" i],[data-player-config]') : [];
      for (var j = 0; j < Math.min(scripts.length, 32); j++) {
        var text = scripts[j].textContent || scripts[j].getAttribute('data-player-config') || '';
        extractPlayerScriptUrls(text, add);
      }
      // 4) بيانات VideoObject وOpen Graph على الصفحة، وهي مفيدة قبل بدء الإعلان.
      var ld = document.querySelectorAll('script[type="application/ld+json"]');
      for (var x = 0; x < Math.min(ld.length, 8); x++) {
        var ldText = ld[x].textContent || '';
        if (!/VideoObject/i.test(ldText) || ldText.length > 500000) continue;
        extractPlayerScriptUrls(ldText, add);
      }
      var metas = document.querySelectorAll('meta[property="og:video"],meta[property="og:video:url"],meta[property="og:video:secure_url"]');
      for (var m = 0; m < Math.min(metas.length, 4); m++) add(metas[m].content);
      // 5) كثير من المواقع تضع إعداد المشغل في script عام خارج عنصر player نفسه.
      // نفحص عددًا محدودًا من scripts التي تحمل مؤشرات إعدادات فيديو فقط، ولا نفحص كل نصوص الصفحة.
      var globalScripts = document.querySelectorAll('script:not([src])');
      var inspected = 0;
      for (var gs = 0; gs < globalScripts.length && inspected < 32; gs++) {
        var scriptText = globalScripts[gs].textContent || '';
        if (scriptText.length < 20 || scriptText.length > 900000 ||
            !/(jwplayer|videojs|sources|qualities|renditions|m3u8|hls|manifest|videoUrl|contentUrl|\\.mp4)/i.test(scriptText)) continue;
        inspected++;
        extractPlayerScriptUrls(scriptText, add);
      }
      // 6) Read common globally exposed player state containers. Limit to named roots and
      // player-specific subtrees so unrelated page links are not treated as video qualities.
      try {
        var configRoots = ['__PLAYER_CONFIG__','playerConfig','videoConfig','videoData','__VIDEO_DATA__',
          '__PLAYER_DATA__','initialPlayerConfig','playerData','videoSources','__VIDEO_PLAYER__'];
        for (var cr = 0; cr < configRoots.length; cr++) {
          var configValue = window[configRoots[cr]];
          if (configValue) parsePlayerConfig(configValue, add, 0, true);
        }
        // Pornhub-style players expose the complete rendition list in window.flashvars_<id>.
        // Read only the explicit mediaDefinitions array, not arbitrary page globals.
        var flashvarsNames = Object.keys(window).filter(function(name){ return /^flashvars(?:_\d+)?$/i.test(name); }).slice(0, 12);
        flashvarsObjectCount = flashvarsNames.length;
        for (var fi = 0; fi < flashvarsNames.length; fi++) {
          var flashvars = window[flashvarsNames[fi]];
          if (!flashvars || typeof flashvars !== 'object') continue;
          var declaredDuration = Number(flashvars.video_duration || flashvars.videoDuration || flashvars.duration || 0);
          if (Number.isFinite(declaredDuration) && declaredDuration > flashvarsVideoDuration && declaredDuration < 86400) flashvarsVideoDuration = declaredDuration;
          var adFlagNames = ['isAdPlaying','adPlaying','adActive','isAd','prerollActive','isPreroll','adBreakActive'];
          for (var af = 0; af < adFlagNames.length; af++) {
            if (flashvars[adFlagNames[af]] === true || flashvars[adFlagNames[af]] === 1 ||
                /^(playing|active|preroll)$/i.test(String(flashvars[adFlagNames[af]] || ''))) flashvarsExplicitAd = true;
          }
          var definitions = flashvars.mediaDefinitions;
          if (!Array.isArray(definitions)) continue;
          flashvarsDefinitionCount += definitions.length;
          for (var fd = 0; fd < Math.min(definitions.length, 64); fd++) {
            var definition = definitions[fd];
            if (!definition || typeof definition !== 'object') continue;
            var definitionUrl = definition.videoUrl || definition.video_url || definition.url || '';
            if (!definitionUrl) continue;
            var formatName = String(definition.format || definition.type || '').toLowerCase();
            var qualityValue = definition.quality;
            var qualityNumber = Array.isArray(qualityValue) ? 0 : Number(String(qualityValue || '').match(/\d{3,4}/)?.[0] || 0);
            if (definition.isAd === true || definition.ad === true || /^(ad|advertisement)$/i.test(String(definition.role || '')) || isAdUrl(definitionUrl)) continue;
            if (qualityNumber > 0 && flashvarsQualities.indexOf(qualityNumber + 'p') < 0 && flashvarsQualities.length < 16) flashvarsQualities.push(qualityNumber + 'p');
            var definitionMeta = {
              captureSource: 'player-flashvars',
              mime: /^(hls|m3u8|mpegurl)$/.test(formatName) ? 'application/vnd.apple.mpegurl' :
                (/^(mp4|webm|quicktime)$/.test(formatName) ? 'video/' + formatName : ''),
              label: qualityNumber > 0 ? qualityNumber + 'p' : '',
              height: qualityNumber > 0 ? qualityNumber : 0,
              codec: definition.codecs || definition.codec || '',
              frameRate: Number(definition.frameRate || definition.fps || 0),
              bandwidth: Number(definition.bandwidth || definition.bitrate || 0),
              representationId: definition.id || definition.formatId || ''
            };
            add(definitionUrl, definitionMeta);
          }
        }
        // Blob/MSE players may fetch the active HLS/DASH manifest without exposing it as currentSrc.
        // Read only recent manifest-like resource names; never inspect or copy media segment bodies.
        try {
          var perfEntries = performance && performance.getEntriesByType ? performance.getEntriesByType('resource') : [];
          var perfNow = Date.now();
          for (var pe = Math.max(0, perfEntries.length - 320); pe < perfEntries.length; pe++) {
            var entry = perfEntries[pe];
            var resourceUrl = String(entry && entry.name || '');
            if (!isHttp(resourceUrl) || isAdUrl(resourceUrl) || isPreviewUrl(resourceUrl) || isChunkUrl(resourceUrl)) continue;
            var resourcePath = '';
            try { resourcePath = new URL(resourceUrl).pathname.toLowerCase(); } catch (_) {}
            var manifestLike = /\.(m3u8|mpd)(?:$)/i.test(resourcePath) || /(?:^|[\/_-])(manifest|playlist)(?:[\/_-]|$)/i.test(resourcePath) ||
              /[?&](?:format|type|mime|contenttype)=(?:[^&]*(?:m3u8|mpegurl|dash|mpd))/i.test(resourceUrl);
            if (!manifestLike) continue;
            var age = perfNow - (Number(performance.timeOrigin || 0) + Number(entry.startTime || 0));
            if (age < -5000 || age > 180000) continue;
            add(resourceUrl, {mime: /mpd/i.test(resourcePath) ? 'application/dash+xml' : 'application/vnd.apple.mpegurl', captureSource:'performance-manifest'});
          }
        } catch (_) {}
        var nextData = window.__NEXT_DATA__;
        if (nextData && nextData.props && nextData.props.pageProps) {
          var pageProps = nextData.props.pageProps;
          ['video','player','media','playback','stream','sources'].forEach(function(k){
            if (pageProps[k]) parsePlayerConfig(pageProps[k], add, 0, true);
          });
        }
        var nuxtData = window.__NUXT__;
        if (nuxtData && typeof nuxtData === 'object') {
          ['video','player','media','playback','stream','sources'].forEach(function(k){
            if (nuxtData[k]) parsePlayerConfig(nuxtData[k], add, 0, true);
          });
        }
      } catch (_) {}
      }
      // 7) Dynamic API payloads: wrap fetch/XHR once and inspect only bounded text/JSON
      // responses that look like player metadata or manifests. Binary media is never read here.
      try {
        if (!window.__vbApiObserverInstalled) {
          window.__vbApiObserverInstalled = true;
          window.__vbApiPayloads = [];
          var rememberPayload = function(requestUrl, text, contentType){
            try {
              if (!text || text.length > 1000000 || isAdUrl(requestUrl)) return;
              var relevant = /#EXTM3U|(?:sources|qualities|renditions|formats|m3u8|mpd|video_url|videoUrl|contentUrl|resolution|height|playbackUrl)/i.test(text);
              if (!relevant) return;
              var list = window.__vbApiPayloads || (window.__vbApiPayloads = []);
              var absoluteRequestUrl = String(requestUrl || location.href);
              try { absoluteRequestUrl = new URL(absoluteRequestUrl, location.href).href; } catch (_) {}
              var key = absoluteRequestUrl + '|' + text.length + '|' + text.substring(0, 96);
              if (list.some(function(x){ return x.key === key; })) return;
              list.push({key:key, url:absoluteRequestUrl, text:text, at:Date.now(), type:String(contentType || '')});
              while (list.length > 24) list.shift();
            } catch (_) {}
          };
          if (typeof window.fetch === 'function') {
            var originalFetch = window.fetch;
            window.fetch = function(input, init){
              var requestUrl = typeof input === 'string' ? input : (input && input.url) || '';
              return originalFetch.apply(this, arguments).then(function(response){
                try {
                  var ct = response.headers ? (response.headers.get('content-type') || '') : '';
                  var path = String(requestUrl).toLowerCase();
                  if (/json|text|xml|mpegurl|dash\+xml/i.test(ct) || /\.(m3u8|mpd)(?:[?#]|$)/i.test(path)) {
                    response.clone().text().then(function(text){ rememberPayload(requestUrl, text, ct); }).catch(function(){});
                  }
                } catch (_) {}
                return response;
              });
            };
          }
          if (window.XMLHttpRequest) {
            var OriginalXHR = window.XMLHttpRequest;
            var WrappedXHR = function(){
              var xhr = new OriginalXHR();
              var requestUrl = '';
              var oldOpen = xhr.open;
              xhr.open = function(method, url){ requestUrl = String(url || ''); return oldOpen.apply(xhr, arguments); };
              xhr.addEventListener('load', function(){
                try {
                  var ct = xhr.getResponseHeader('content-type') || '';
                  var body = '';
                  if (xhr.responseType === '' || xhr.responseType === 'text') body = xhr.responseText || '';
                  else if (xhr.responseType === 'json' && xhr.response) body = JSON.stringify(xhr.response);
                  if (body && (/json|text|xml|mpegurl|dash\+xml/i.test(ct) || /\.(m3u8|mpd)(?:[?#]|$)/i.test(requestUrl))) {
                    rememberPayload(requestUrl, body, ct);
                  }
                } catch (_) {}
              });
              return xhr;
            };
            WrappedXHR.prototype = OriginalXHR.prototype;
            try { Object.setPrototypeOf(WrappedXHR, OriginalXHR); } catch (_) {}
            window.XMLHttpRequest = WrappedXHR;
          }
        }
      } catch (_) {}
      var parseHlsAttributes = function(raw){
        var attrs = Object.create(null);
        var i = 0;
        while (i < String(raw || '').length) {
          while (i < raw.length && (raw[i] === ',' || /\s/.test(raw[i]))) i++;
          var start = i;
          while (i < raw.length && raw[i] !== '=' && raw[i] !== ',') i++;
          if (i >= raw.length || raw[i] !== '=') break;
          var key = raw.substring(start, i).trim().toUpperCase();
          i++;
          var value = '';
          if (raw[i] === '"') {
            i++;
            var valueStart = i;
            while (i < raw.length && raw[i] !== '"') i++;
            value = raw.substring(valueStart, i);
            if (i < raw.length) i++;
          } else {
            var plainStart = i;
            while (i < raw.length && raw[i] !== ',') i++;
            value = raw.substring(plainStart, i).trim();
          }
          if (key) attrs[key] = value;
          while (i < raw.length && raw[i] !== ',') i++;
        }
        return attrs;
      };
      var nativeApiPayloads = (typeof __vbNativeApiPayloads !== 'undefined' && Array.isArray(__vbNativeApiPayloads))
        ? __vbNativeApiPayloads : [];
      var apiPayloads = (window.__vbApiPayloads || []).concat(nativeApiPayloads);
      for (var ap = Math.max(0, apiPayloads.length - 32); ap < apiPayloads.length; ap++) {
        var payload = apiPayloads[ap];
        if (!payload || Date.now() - payload.at > 180000 || isAdUrl(payload.url)) continue;
        var previousApiContext = activeApiContext;
        activeApiContext = {
          headers: payload.headers || {},
          source: 'api-' + String(payload.source || 'response')
        };
        // Parsing each API payload once avoids repeating JSON/HLS work on every video scan.
        // Cache only extracted URL + quality metadata, then replay the compact candidates
        // into each collectSources() result so repeated calls in one scan keep the sources.
        if (payload.__vbScanned && Array.isArray(payload.__vbParsedSources)) {
          for (var cachedIndex = 0; cachedIndex < payload.__vbParsedSources.length; cachedIndex++) {
            var cachedSource = payload.__vbParsedSources[cachedIndex];
            if (cachedSource) add(cachedSource.url, cachedSource.meta || {});
          }
          activeApiContext = previousApiContext;
          continue;
        }
        var body = payload.text || '';
        var originalAdd = add;
        var parsedApiCandidates = [];
        add = function(raw, meta) {
          try {
            var candidateUrl = absUrl(raw);
            if (candidateUrl && isHttp(candidateUrl) && !isAdUrl(candidateUrl) && !isPreviewUrl(candidateUrl) && !isChunkUrl(candidateUrl) && isPlayerMediaUrl(candidateUrl, true) && parsedApiCandidates.length < 128) {
              parsedApiCandidates.push({url:candidateUrl, meta:meta || {}});
            }
          } catch (_) {}
          return originalAdd(raw, meta);
        };
        try {
          if (/^\s*#EXTM3U/m.test(body)) {
            var lines = body.split(/\r?\n/);
            var waitingVariant = null;
            for (var li = 0; li < lines.length; li++) {
              var line = lines[li].trim();
              if (line.indexOf('#EXT-X-STREAM-INF:') === 0) {
                waitingVariant = parseHlsAttributes(line.substring(line.indexOf(':') + 1));
                continue;
              }
              if (waitingVariant && line && line.charAt(0) !== '#') {
                var variantUrl = absUrl(new URL(line, payload.url).href);
                var resolution = String(waitingVariant.RESOLUTION || '');
                var dims = /^(\d{2,5})x(\d{2,5})$/i.exec(resolution);
                var variantHeight = dims ? Number(dims[2]) : 0;
                var variantWidth = dims ? Number(dims[1]) : 0;
                var variantLabel = waitingVariant.NAME || (variantHeight ? variantHeight + 'p' : '');
                add(variantUrl, {
                  mime:'application/vnd.apple.mpegurl',
                  label:variantLabel,
                  width:variantWidth,
                  height:variantHeight
                });
                waitingVariant = null;
              }
              if (line.indexOf('#EXT-X-MEDIA:') === 0) {
                var mediaAttrs = parseHlsAttributes(line.substring(line.indexOf(':') + 1));
                if (String(mediaAttrs.TYPE || '').toUpperCase() === 'VIDEO' && mediaAttrs.URI) {
                  var mediaResolution = String(mediaAttrs.RESOLUTION || '');
                  var mediaDims = /^(\d{2,5})x(\d{2,5})$/i.exec(mediaResolution);
                  var mediaHeight = mediaDims ? Number(mediaDims[2]) : 0;
                  var mediaWidth = mediaDims ? Number(mediaDims[1]) : 0;
                  add(absUrl(new URL(mediaAttrs.URI, payload.url).href), {
                    mime:'application/vnd.apple.mpegurl',
                    label:mediaAttrs.NAME || (mediaHeight ? mediaHeight + 'p' : ''),
                    width:mediaWidth,
                    height:mediaHeight
                  });
                }
              }
            }
            // The API endpoint itself may be a manifest even when its URL has no suffix.
            add(payload.url, {mime:'application/vnd.apple.mpegurl'});
          } else {
            try {
              var parsedPayload = JSON.parse(body);
              parsePlayerConfig(parsedPayload, add, 0, true);
            } catch (_) {
              extractPlayerScriptUrls(body, add);
            }
          }
        } catch (_) {
        } finally {
          add = originalAdd;
          payload.__vbParsedSources = parsedApiCandidates;
          payload.__vbScanned = true;
          activeApiContext = previousApiContext;
        }
      }
      // 8) Known player adapters: prefer actual quality representations from the player instance.
      if (!apiOnly) try {
        if (typeof window.videojs === 'function' && window.videojs.getPlayers) {
          var vplayers = window.videojs.getPlayers() || {};
          var vkeys = Object.keys(vplayers);
          for (var vp = 0; vp < Math.min(vkeys.length, 8); vp++) {
            try {
              var vplayer = vplayers[vkeys[vp]];
              if (!vplayer) continue;
              if (vplayer.currentSources) parsePlayerConfig(vplayer.currentSources(), add, 0, true);
              if (vplayer.currentSource) parsePlayerConfig(vplayer.currentSource(), add, 0, true);
              var tech = vplayer.tech ? vplayer.tech(true) : null;
              var vhs = tech && (tech.vhs || tech.hls);
              if (vhs && vhs.playlists && vhs.playlists.master) parsePlayerConfig(vhs.playlists.master, add, 0, true);
              if (vhs && vhs.representations) parsePlayerConfig(vhs.representations(), add, 0, true);
            } catch (_) {}
          }
        }
      } catch (_) {}
      try {
        var hlsNames = ['hls','__hls','hlsPlayer','playerHls','__playerHls'];
        for (var hn = 0; hn < hlsNames.length; hn++) {
          var hlsInstance = window[hlsNames[hn]];
          if (hlsInstance && Array.isArray(hlsInstance.levels)) parsePlayerConfig(hlsInstance.levels, add, 0, true);
        }
      } catch (_) {}
      // 9) Known player adapters: prefer the current playlist/config object, not arbitrary page URLs.
      try {
        if (typeof window.jwplayer === 'function') {
          var jwNodes = playerRoot && playerRoot.querySelectorAll ? playerRoot.querySelectorAll('[id]') : [];
          var jwIds = [];
          if (playerRoot && playerRoot.id) jwIds.push(playerRoot.id);
          for (var ji = 0; ji < Math.min(jwNodes.length, 8); ji++) {
            var jid = jwNodes[ji].id;
            if (jid && /jw|player|video/i.test(jid) && jwIds.indexOf(jid) < 0) jwIds.push(jid);
          }
          if (!jwIds.length) jwIds.push('');
          for (var jx = 0; jx < jwIds.length; jx++) {
            try {
              var jw = window.jwplayer(jwIds[jx] || undefined);
              if (jw && jw.getConfig) parsePlayerConfig(jw.getConfig(), add, 0, true);
              if (jw && jw.getPlaylistItem) parsePlayerConfig(jw.getPlaylistItem(), add, 0, true);
            } catch (_) {}
          }
        }
      } catch (_) {}
      // currentSrc/src قد يتحولان مؤقتًا إلى ملف الإعلان؛ لا نضيفهما أثناء وضع الإعلان.
      // وإذا لم يكشف المشغل أي بديل وكان المصدر قصيرًا جدًا، نؤجّل اعتباره محتوى حتى يحدث انتقال.
      var currentSrc = absUrl(v.currentSrc || v.getAttribute('src') || '');
      var currentDuration = Number(v.duration || 0);
      var durationMismatchAd = flashvarsVideoDuration > 20 && currentDuration > 0 &&
        currentDuration < flashvarsVideoDuration - Math.max(15, flashvarsVideoDuration * 0.15) &&
        currentDuration / flashvarsVideoDuration < 0.80;
      var adNow = hasAdMarker(v) || hasVisibleAdOverlay(v) || isAdUrl(currentSrc) || flashvarsExplicitAd || durationMismatchAd;
      v.__vbAdDetected = !!adNow;
      v.__vbAdReason = hasAdMarker(v) || hasVisibleAdOverlay(v) ? 'ad-container' :
        (isAdUrl(currentSrc) ? 'ad-url' : (flashvarsExplicitAd ? 'player-ad-flag' : (durationMismatchAd ? 'player-duration-mismatch' : '')));
      v.__vbPlayerDuration = flashvarsVideoDuration;
      if (adNow) {
        out = out.filter(function(u){
          var capture = sourceMeta[u] && sourceMeta[u].captureSource || '';
          return capture !== 'player-flashvars' && capture !== 'performance-manifest';
        });
      }
      var hasAlternativeSource = out.some(function(u){ return u !== currentSrc; });
      // A short current item plus another source exposed by the same player is a strong
      // preroll signal: retain the alternative source(s), but do not add the ad currentSrc.
      var shortUnclassifiedSource = currentDuration > 0 && currentDuration <= 12 &&
        (out.length === 0 || hasAlternativeSource) && !v.__vbContentTransition;
      if (!apiOnly && !adNow && !shortUnclassifiedSource) {
        add(v.getAttribute('src'), {width:Number(v.videoWidth || 0), height:Number(v.videoHeight || 0), mime:'video/mp4'});
        add(v.currentSrc, {width:Number(v.videoWidth || 0), height:Number(v.videoHeight || 0), mime:'video/mp4'});
      }
      v.__vbFlashvarsStats = { objects:flashvarsObjectCount, definitions:flashvarsDefinitionCount, qualities:flashvarsQualities.slice(0, 16) };
      v.__vbSourceMeta = out.slice(0, 128).map(function(u){
        var m = sourceMeta[u] || {};
        return {url:u, label:m.label || '', width:Number(m.width || 0), height:Number(m.height || 0), mime:m.mime || '',
          codec:m.codec || '', frameRate:Number(m.frameRate || 0), dynamicRange:m.dynamicRange || '',
          bandwidth:Number(m.bandwidth || 0), representationId:m.representationId || '',
          requestHeaders:m.requestHeaders || {}, captureSource:m.captureSource || ''};
      });
      return out.slice(0, 128);
    };
    var makeId = function(v){
      if (!v.__vbId) v.__vbId = Math.random().toString(36).slice(2);
      return String(v.__vbId);
    };
    var emit = function(v, eventName){
      try {
        var id = makeId(v);
        if (eventName === 'loadstart' || eventName === 'emptied') v.__vbContentTransition = false;
        var src = absUrl(v.currentSrc || v.getAttribute('src') || '');
        var sources = collectSources(v);
        var key = src + '||' + sources.join('||');
        var duration = Number(v.duration || 0);
        if (key !== v.__vbSourceKey) {
          v.__vbSourceKey = key;
          v.__vbSourceEpoch = Number(v.__vbSourceEpoch || 0) + 1;
        }
        if (duration > 0 && v.__vbLastDuration > 0 && Math.abs(duration - v.__vbLastDuration) > 2) {
          v.__vbSourceEpoch = Number(v.__vbSourceEpoch || 0) + 1;
        }
        if (duration > 0) v.__vbLastDuration = duration;
        if (eventName === 'ended') {
          v.__vbCycle = Number(v.__vbCycle || 0) + 1;
          v.__vbEndedSincePlay = true;
        } else if (eventName === 'playing' && v.__vbEndedSincePlay) {
          v.__vbEndedSincePlay = false;
          v.__vbSourceEpoch = Number(v.__vbSourceEpoch || 0) + 1;
        }
        var adContainer = hasAdMarker(v) || hasVisibleAdOverlay(v);
        var ad = isAdUrl(src) || adContainer || v.__vbAdDetected === true;
        var wasAdState = v.__vbAdState;
        if (wasAdState === true && !ad) {
          // إعلان ما قبل التشغيل انتهى وبدأ المحتوى على عنصر الفيديو نفسه.
          v.__vbSourceEpoch = Number(v.__vbSourceEpoch || 0) + 1;
          v.__vbCycle = Number(v.__vbCycle || 0) + 1;
        }
        v.__vbAdState = ad;
        if (window.VBVideoGesture && window.VBVideoGesture.onVideoEvent) {
          window.VBVideoGesture.onVideoEvent(
            id, key, !!ad, String(eventName || 'scan'), duration,
            Number(v.__vbSourceEpoch || 0), Number(v.__vbCycle || 0),
            JSON.stringify(sources), !!adContainer, JSON.stringify(v.__vbSourceMeta || [])
          );
        }
      } catch (_) {}
    };
    var videos = Array.prototype.slice.call(document.querySelectorAll('video'));
    if (!window.__vbGestureTrackerInstalled) {
      window.__vbGestureTrackerInstalled = true;
      document.addEventListener('click', function(e){
        try {
          var path = e.composedPath ? e.composedPath() : [];
          var target = null;
          for (var i = 0; i < path.length; i++) {
            if (path[i] && path[i].tagName === 'VIDEO') { target = path[i]; break; }
          }
          if (!target) {
            var best = -1;
            var currentVideos = document.querySelectorAll('video');
            for (var j = 0; j < currentVideos.length; j++) {
              var r = currentVideos[j].getBoundingClientRect();
              if (e.clientX >= r.left && e.clientX <= r.right && e.clientY >= r.top && e.clientY <= r.bottom) {
                var a = Math.max(0, r.width) * Math.max(0, r.height);
                if (a > best) { best = a; target = currentVideos[j]; }
              }
            }
          }
          if (!target) return;
          var tr = target.getBoundingClientRect();
          var currentVideosForOverlap = document.querySelectorAll('video');
          for (var k = 0; k < currentVideosForOverlap.length; k++) {
            var v = currentVideosForOverlap[k];
            var r2 = v.getBoundingClientRect();
            var iw = Math.max(0, Math.min(tr.right, r2.right) - Math.max(tr.left, r2.left));
            var ih = Math.max(0, Math.min(tr.bottom, r2.bottom) - Math.max(tr.top, r2.top));
            var overlap = iw * ih;
            var area = Math.max(1, Math.min(tr.width * tr.height, r2.width * r2.height));
            if (v === target || overlap / area > 0.55) {
              var id = makeId(v);
              v.__vbUserInitiated = true;
              v.__vbUserInitiatedAt = Date.now();
              if (window.VBVideoGesture && window.VBVideoGesture.onVideoGesture) {
                window.VBVideoGesture.onVideoGesture(id);
              }
            }
          }
        } catch (_) {}
      }, true);
    }
    var bestVideo = null;
    var bestScore = -1;
    var bestAdVideo = null;
    var bestAdScore = -1;
    var now = Date.now();
    var active = null;
    var activeVideoEl = null;
    for (var i = 0; i < videos.length; i++) {
      var v = videos[i];
      makeId(v);
      if (!v.__vbHooks) {
        v.__vbHooks = true;
        ['loadstart','emptied','loadedmetadata','durationchange','playing','pause','ended','seeking','seeked'].forEach(function(name){
          v.addEventListener(name, function(){
            if (name === 'seeking') v.__vbWasSeeking = true;
            if (name === 'seeked') v.__vbWasSeeking = false;
            emit(v, name);
          }, true);
        });
      }
      var r = v.getBoundingClientRect();
      var inViewport = visible(v);
      var visibleWidth = Math.max(0, Math.min(r.right, window.innerWidth) - Math.max(r.left, 0));
      var visibleHeight = Math.max(0, Math.min(r.bottom, window.innerHeight) - Math.max(r.top, 0));
      var area = visibleWidth * visibleHeight;
      var src = absUrl(v.currentSrc || v.getAttribute('src') || '');
      var ad = isAdUrl(src) || hasAdMarker(v) || hasVisibleAdOverlay(v);
      var sources = (inViewport || v.__vbUserInitiated) ? collectSources(v) : [];
      if (inViewport && area >= 9000) {
        var center = Math.max(0, 1 - Math.abs((r.left + r.width / 2) - (window.innerWidth / 2)) / Math.max(1, window.innerWidth));
        var score = area * (ad ? 0.90 : 1.0) + area * center * 0.08 + (sources.length ? area * 0.05 : 0);
        // اختر أكبر فيديو محتوى ظاهر في إطار العرض، وليس صورة مصغرة أسفل/خارج الشاشة.
        if (!ad && score > bestScore) { bestScore = score; bestVideo = v; }
        if (ad && score > bestAdScore) { bestAdScore = score; bestAdVideo = v; }
      }
      var tnow = now;
      var ct = Number(v.currentTime || 0);
      var last = v.__vbLastTime;
      var lastAt = Number(v.__vbLastAt || 0);
      var playing = !v.paused && !v.ended && v.readyState >= 2;
      // بعض مشغلات الإعلانات تستخدم عنصر MSE/blob نفسه، ثم تعيد عداد الوقت إلى الصفر للمحتوى.
      // نعد ذلك جيل مصدر جديدًا ما لم يكن المستخدم يسحب شريط التقدم.
      var resetAtContentBoundary = playing && last !== undefined && ct + 1.25 < last && !v.seeking && !v.__vbWasSeeking;
      if (resetAtContentBoundary) {
        v.__vbContentTransition = true;
        v.__vbSourceEpoch = Number(v.__vbSourceEpoch || 0) + 1;
        v.__vbCycle = Number(v.__vbCycle || 0) + 1;
        emit(v, 'content-transition');
      }
      var advancing = last !== undefined && (tnow - lastAt) >= 300 && ct > last + 0.04;
      v.__vbLastTime = ct;
      v.__vbLastAt = tnow;
      var hasAlternativeSource = sources.some(function(u){ return u !== src; });
      var shortUnclassifiedSource = Number(v.duration || 0) > 0 && Number(v.duration || 0) <= 12 &&
        (sources.length === 0 || hasAlternativeSource) && !v.__vbContentTransition;
      if (playing && advancing && v.__vbUserInitiated && !ad && !shortUnclassifiedSource) {
        var activeScore = Number(v.__vbUserInitiatedAt || 0) + area / 1000000;
        if (!active || activeScore > active.score) {
          active = { id: makeId(v), score: activeScore, src: src, duration: Number(v.duration || 0) };
          activeVideoEl = v;
        }
      }
      // إذا تغير المصدر ولم يصل حدث DOM لأي سبب، أرسل تحديثًا من دورة الفحص.
      var sourceKey = src + '||' + sources.join('||');
      var observedAdState = isAdUrl(src) || hasAdMarker(v) || hasVisibleAdOverlay(v) || v.__vbAdDetected === true;
      if (sourceKey !== v.__vbSourceKey || (v.__vbAdState !== undefined && v.__vbAdState !== observedAdState)) {
        emit(v, 'scan');
      } else if (v.__vbAdState === undefined) {
        emit(v, 'scan');
      }
    }
    var primary = null;
    // Keep the user-started video as primary even if the user scrolls it out of view.
    // When nothing is actively playing, use the largest visible content player.
    var primaryVideo = activeVideoEl || bestVideo || bestAdVideo;
    var orphanSources = [];
    var orphanSourceMeta = [];
    var nativeApiQueueAvailable = typeof __vbNativeApiPayloads !== 'undefined' &&
      Array.isArray(__vbNativeApiPayloads) && __vbNativeApiPayloads.length > 0;
    var pageApiQueueAvailable = window.__vbApiPayloads && window.__vbApiPayloads.length > 0;
    if (!primaryVideo && (pageApiQueueAvailable || nativeApiQueueAvailable)) {
      // Parse only the already-captured API/manifest queue. Do not inspect arbitrary DOM/player
      // state with a fake video element. This supports pages whose player lives in a cross-origin iframe.
      var apiProbe = {
        parentElement: null,
        currentSrc: '', duration: 0, videoWidth: 0, videoHeight: 0,
        getAttribute: function(){ return null; },
        querySelectorAll: function(){ return []; },
        getBoundingClientRect: function(){ return {left:0,top:0,right:0,bottom:0,width:0,height:0}; }
      };
      orphanSources = collectSources(apiProbe, true);
      orphanSourceMeta = apiProbe.__vbSourceMeta || [];
    }
    if (primaryVideo) {
      var pv = primaryVideo;
      var psrc = absUrl(pv.currentSrc || pv.getAttribute('src') || '');
      var pad = isAdUrl(psrc) || hasAdMarker(pv) || hasVisibleAdOverlay(pv) || pv.__vbAdDetected === true;
      var pSources = collectSources(pv);
      var pKey = psrc + '||' + pSources.join('||');
      if (pKey !== pv.__vbSourceKey) emit(pv, 'scan');
      primary = {
        id: makeId(pv), src: psrc, sources: pSources, sourceMeta: pv.__vbSourceMeta || [],
        sourceKey: pKey, sourceEpoch: Number(pv.__vbSourceEpoch || 0),
        cycle: Number(pv.__vbCycle || 0), ad: !!pad,
        adContainer: hasAdMarker(pv) || hasVisibleAdOverlay(pv), poster: absUrl(pv.poster || ''),
        duration: Number(pv.duration || 0),
        playerConfigObjectCount: Number((pv.__vbFlashvarsStats || {}).objects || 0),
        playerMediaDefinitionCount: Number((pv.__vbFlashvarsStats || {}).definitions || 0),
        playerDefinitionQualities: (pv.__vbFlashvarsStats || {}).qualities || [],
        playerVideoDuration: Number(pv.__vbPlayerDuration || 0),
        adReason: String(pv.__vbAdReason || ''),
        suspectedPreroll: Number(pv.duration || 0) > 0 && Number(pv.duration || 0) <= 12 &&
          (pSources.length === 0 || pSources.some(function(u){ return u !== psrc; })) && !pv.__vbContentTransition,
        blob: /^blob:/i.test(String(pv.currentSrc || pv.src || ''))
      };
    }
    return JSON.stringify({ p: primary, a: active, o: orphanSources, om: orphanSourceMeta });
  } catch (_) {
    return JSON.stringify({ p: null, a: null });
  }
})();
