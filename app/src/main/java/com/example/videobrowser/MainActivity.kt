package com.example.videobrowser

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.ScriptHandler
import androidx.webkit.WebMessageCompat
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.ExecutionException
import kotlin.math.abs

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

// يرصد نقرة المستخدم على عنصر فيديو، ثم يتحقق من تقدم currentTime حتى إن مرّر المستخدم الصفحة لاحقًا.
// السحب/التمرير وطلبات الشبكة وحدها لا تفعّل الزر العائم.
private const val SCAN_JS = """
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
      var xhStateFound = false;
      var xhSourceCount = 0;
      var xhQualities = [];
      var xhOpaqueCount = 0;
      var xhSeen = Object.create(null);
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
        // xHamster exposes plain renditions in named page state containers on some pages.
        // Read only these documented player subtrees and direct HTTP(S) URLs; never decode opaque values.
        try {
          var xhInitials = window.initials;
          if (!xhInitials && typeof initials !== 'undefined') xhInitials = initials;
          if (xhInitials && typeof xhInitials === 'object') {
            var xhVideoModel = xhInitials.videoModel && typeof xhInitials.videoModel === 'object' ? xhInitials.videoModel : null;
            var xhDuration = Number(xhVideoModel && (xhVideoModel.duration || xhVideoModel.videoDuration || xhVideoModel.video_duration) || xhInitials.videoDuration || 0);
            if (Number.isFinite(xhDuration) && xhDuration > 20 && xhDuration < 86400 && xhDuration > flashvarsVideoDuration) flashvarsVideoDuration = xhDuration;
            var xhSourceGroups = xhVideoModel && xhVideoModel.sources && typeof xhVideoModel.sources === 'object' ? xhVideoModel.sources : null;
            var xhPlayerSources = xhInitials.xplayerSettings && xhInitials.xplayerSettings.sources &&
              typeof xhInitials.xplayerSettings.sources === 'object' ? xhInitials.xplayerSettings.sources : null;
            xhStateFound = !!(xhSourceGroups || xhPlayerSources);
            var xhOpaqueSeen = Object.create(null);
            var xhVisited = [];
            var xhNodeCount = 0;
            var xhDirectKeys = ['url','videoUrl','video_url','src','file','link','playbackUrl','playback_url',
              'fallback','manifest','playlist','streamUrl','stream_url','hlsUrl','hls_url'];
            var xhQualityFor = function(item, keyHint) {
              var meta = mergeQualityMeta(item && typeof item === 'object' ? item : null, {}, String(keyHint || ''));
              var text = [keyHint || '', meta.label || '', item && item.quality || '', item && item.resolution || ''].join(' ');
              var dimensions = /(\d{3,5})\s*[x×]\s*(\d{3,5})/i.exec(text);
              var numeric = /(?:^|[^0-9])(\d{3,4})\s*p?(?:$|[^0-9])/i.exec(text);
              var explicitNumber = item && typeof item === 'object' ? Number(item.height || item.video_height || item.videoHeight || 0) : 0;
              var height = Number(meta.height || (dimensions ? dimensions[2] : 0) || explicitNumber || (numeric ? numeric[1] : 0) || 0);
              var namedQuality = /\b(?:2k|qhd|4k|uhd)\b/i.exec(text);
              var label = namedQuality ? namedQuality[0].toUpperCase() :
                (height >= 144 && height <= 4320 ? height + 'p' : (meta.label || ''));
              return {meta:meta, height:height, label:label};
            };
            var xhAddUrl = function(raw, formatName, keyHint, item) {
              var value = String(raw || '').trim();
              if (!value) return;
              if (!/^https?:\/\//i.test(value)) {
                if (/^[a-f0-9]{24,}$/i.test(value) && !xhOpaqueSeen[value]) { xhOpaqueSeen[value] = true; xhOpaqueCount++; }
                return;
              }
              var normalized = absUrl(value);
              if (!isHttp(normalized) || isAdUrl(normalized) || isPreviewUrl(normalized)) return;
              var q = xhQualityFor(item, keyHint);
              var meta = q.meta;
              if (q.height >= 144 && q.height <= 4320) meta.height = q.height;
              if (q.label) meta.label = q.label;
              var fmt = String(formatName || '').toLowerCase();
              if (/hls|m3u8/.test(fmt)) meta.mime = 'application/vnd.apple.mpegurl';
              else if (/mp4|h264|avc|av1|standard/.test(fmt)) meta.mime = meta.mime || 'video/mp4';
              if (/h264|avc/.test(fmt)) meta.codec = meta.codec || 'H.264';
              else if (/av1/.test(fmt)) meta.codec = meta.codec || 'AV1';
              meta.captureSource = 'player-xhamster';
              if (q.label && xhQualities.indexOf(q.label) < 0 && xhQualities.length < 24) xhQualities.push(q.label);
              if (xhSeen[normalized]) return;
              xhSeen[normalized] = true;
              add(normalized, meta);
              if (sourceMeta[normalized]) xhSourceCount++;
            };
            var xhWalk = function(node, formatName, keyHint, depth) {
              if (node == null || depth > 6 || xhNodeCount++ > 260) return;
              if (typeof node === 'string') { xhAddUrl(node, formatName, keyHint, null); return; }
              if (typeof node !== 'object') return;
              if (xhVisited.indexOf(node) >= 0) return;
              xhVisited.push(node);
              if (Array.isArray(node)) {
                for (var ai = 0; ai < Math.min(node.length, 48); ai++) xhWalk(node[ai], formatName, keyHint, depth + 1);
                return;
              }
              var objectQuality = node.quality || node.qualityLabel || node.resolution || node.height || keyHint || '';
              var foundDirect = false;
              for (var uk = 0; uk < xhDirectKeys.length; uk++) {
                var directValue = node[xhDirectKeys[uk]];
                if (typeof directValue === 'string') {
                  xhAddUrl(directValue, formatName, objectQuality, node);
                  foundDirect = true;
                }
              }
              var keys = Object.keys(node);
              for (var ki = 0; ki < Math.min(keys.length, 64); ki++) {
                var childKey = keys[ki];
                if (xhDirectKeys.indexOf(childKey) >= 0 || /^(?:download|downloadUrl|download_url|ads?|advertisement|preview|thumbnail|thumb|poster|trailer|teaser|token|signature|cookie|authorization)$/i.test(childKey)) continue;
                var child = node[childKey];
                if (child == null || typeof child === 'boolean' || typeof child === 'number') continue;
                var nextFormat = /^(?:hls|m3u8|mp4|webm|h264|avc|av1|standard)$/i.test(childKey) ? childKey : formatName;
                var nextQuality = /(?:^|[^0-9])(?:\d{3,4}\s*p?|2k|qhd|4k|uhd)(?:$|[^a-z0-9])/i.test(childKey) ? childKey : objectQuality;
                xhWalk(child, nextFormat, nextQuality, depth + 1);
              }
              if (foundDirect) return;
            };
            if (xhSourceGroups) {
              var xhFormatKeys = Object.keys(xhSourceGroups);
              for (var xf = 0; xf < Math.min(xhFormatKeys.length, 20); xf++) {
                var xhFormat = xhFormatKeys[xf];
                if (/^(?:download|ads?|advertisement|preview|thumbnail|trailer)$/i.test(xhFormat)) continue;
                xhWalk(xhSourceGroups[xhFormat], xhFormat, '', 0);
              }
            }
            if (xhPlayerSources) {
              var xhRootKeys = Object.keys(xhPlayerSources);
              for (var xb = 0; xb < Math.min(xhRootKeys.length, 20); xb++) {
                var xhBucketName = xhRootKeys[xb];
                if (/download|ad|preview|trailer/i.test(xhBucketName)) continue;
                if (!/(?:standard|h264|avc|av1|hls|mp4|source|quality)/i.test(xhBucketName)) continue;
                xhWalk(xhPlayerSources[xhBucketName], xhBucketName, '', 0);
              }
            }
          }
        } catch (_) {}
        // Blob/MSE players may fetch the active HLS/DASH manifest without exposing it as currentSrc.
        // Read only recent manifest-like resource names; never inspect or copy media segment bodies.
        try {
          var perfEntries = performance && performance.getEntriesByType ? performance.getEntriesByType('resource') : [];
          var perfNow = Date.now();
          var perfCurrentSrc = absUrl(v.currentSrc || v.getAttribute('src') || '');
          var perfDuration = Number(v.duration || 0);
          var perfDurationMismatch = flashvarsVideoDuration > 20 && perfDuration > 0 &&
            perfDuration < flashvarsVideoDuration - Math.max(15, flashvarsVideoDuration * 0.15) &&
            perfDuration / flashvarsVideoDuration < 0.80;
          var perfAdNow = hasAdMarker(v) || hasVisibleAdOverlay(v) || isAdUrl(perfCurrentSrc) || flashvarsExplicitAd || perfDurationMismatch;
          if (perfAdNow) v.__vbContentTransitionAt = 0;
          else if (v.__vbPerfWasAd === true) v.__vbContentTransitionAt = perfNow;
          v.__vbPerfWasAd = !!perfAdNow;
          var contentTransitionAt = Number(v.__vbContentTransitionAt || 0);
          for (var pe = Math.max(0, perfEntries.length - 320); pe < perfEntries.length; pe++) {
            var entry = perfEntries[pe];
            var resourceUrl = String(entry && entry.name || '');
            if (!isHttp(resourceUrl) || isAdUrl(resourceUrl) || isPreviewUrl(resourceUrl) || isChunkUrl(resourceUrl)) continue;
            var resourcePath = '';
            try { resourcePath = new URL(resourceUrl).pathname.toLowerCase(); } catch (_) {}
            var manifestLike = /\.(m3u8|mpd)(?:$)/i.test(resourcePath) || /(?:^|[\/_-])(manifest|playlist)(?:[\/_-]|$)/i.test(resourcePath) ||
              /[?&](?:format|type|mime|contenttype)=(?:[^&]*(?:m3u8|mpegurl|dash|mpd))/i.test(resourceUrl);
            if (!manifestLike) continue;
            var entryAt = Number(performance.timeOrigin || 0) + Number(entry.startTime || 0);
            var age = perfNow - entryAt;
            if (age < -5000 || age > 180000) continue;
            // Ignore manifests loaded before the preroll-to-content transition, with a small race margin.
            if (contentTransitionAt > 0 && entryAt < contentTransitionAt - 1500) continue;
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
        // The active <video> may be playing a preroll, but named player configuration
        // still contains the actual movie renditions. Keep only those explicit rendition
        // sources (flashvars / xHamster model); do not expose the ad currentSrc or old
        // Resource-Timing manifests collected before the content transition.
        out = out.filter(function(u){
          var capture = sourceMeta[u] && sourceMeta[u].captureSource || '';
          return capture === 'player-flashvars' || capture === 'player-xhamster';
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
      v.__vbXhamsterStats = { stateFound:!!xhStateFound, sourceCount:xhSourceCount, qualities:xhQualities.slice(0, 24), opaqueCount:xhOpaqueCount };
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
        if (eventName === 'loadstart' || eventName === 'emptied') { v.__vbContentTransition = false; v.__vbPerfWasAd = false; v.__vbContentTransitionAt = 0; }
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
        xhamsterStateFound: !!(pv.__vbXhamsterStats || {}).stateFound,
        xhamsterSourceCount: Number((pv.__vbXhamsterStats || {}).sourceCount || 0),
        xhamsterQualities: (pv.__vbXhamsterStats || {}).qualities || [],
        xhamsterOpaqueCount: Number((pv.__vbXhamsterStats || {}).opaqueCount || 0),
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
"""
private const val DESKTOP_UA =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

class MainActivity : AppCompatActivity() {

    private data class SniffedMedia(
        val url: String,
        val requestedAt: Long,
        val videoId: String,
        val sourceEpoch: Int,
        val capturedDuringAd: Boolean = false,
        val request: CapturedMediaRequest? = null
    )

    private class Tab(val web: WebView) {
        var title = "علامة تبويب جديدة"
        var documentStartScript: ScriptHandler? = null
        var documentStartCaptureInstalled = false
        var url = ""
        var pending = false
        // media = المصادر الجاهزة للعرض؛ preparedMedia مصدرها DOM/تهيئة المشغل،
        // وsniffed مصادر الشبكة المرتبطة بفترة المصدر الحالية فقط.
        val media = LinkedHashMap<String, MediaItem>()
        val preparedMedia = LinkedHashMap<String, MediaItem>()
        val sniffed = LinkedHashMap<String, SniffedMedia>()
        // Full, non-secret request context is held only in memory for the current page session.
        val requestContexts = LinkedHashMap<String, CapturedMediaRequest>()
        // Cross-origin iframe API payloads arrive through WebMessageListener and stay native-side.
        val frameApiPayloads = LinkedHashMap<String, String>()
        // Per-frame startup probes distinguish script injection from missing <video> discovery.
        val frameProbeStates = LinkedHashMap<String, String>()
        var frameBridgeRegistered = false
        var frameBridgeError = ""
        var frameProbeMessageCount = 0
        var frameVideoMessageCount = 0
        var frameReportedLightVideoCount = 0
        var frameReportedShadowVideoCount = 0
        var playerConfigObjectCount = 0
        var playerMediaDefinitionCount = 0
        var playerDefinitionQualities = ""
        var playerVideoDurationSec = 0.0
        var playerAdReason = ""
        var xhamsterStateFound = false
        var xhamsterSourceCount = 0
        var xhamsterQualities = ""
        var xhamsterOpaqueCount = 0
        // Cross-origin/frame player snapshots, held only in memory for the active page.
        val frameVideoStates = LinkedHashMap<String, String>()
        var primaryFrameOrigin = ""
        var primaryFrameId = ""
        var primaryFrameSeenAt = 0L
        var syntheticApiUpdatedAt = 0L
        // طلبات مرشحة ظهرت أثناء إعلان ما قبل التشغيل؛ لا تُعرض للمستخدم إلا بعد انتقال المصدر إلى المحتوى.
        val pendingSniffed = LinkedHashMap<String, Long>()
        // طلبات وسائط التقطت قبل أن ينتهي فحص DOM من تحديد عنصر الفيديو الرئيسي.
        val pageRequests = LinkedHashMap<String, Long>()
        var primaryVideoId: String? = null
        var primarySourceKey = ""
        var primaryCurrentSrc = ""
        var primarySourceEpoch = 0
        var primaryCycle = 0
        var primaryIsAd = false
        // A short (<=12s) active source with no exposed alternatives OR with another
        // predeclared player source is treated as a possible preroll until content transition.
        var suspectedPreroll = false
        var expectedDurationSec = 0.0
        var sawBlob = false
        @Volatile var activePlayback = false
        @Volatile var resolvedFormats: List<Format>? = null
        @Volatile var resolvedKey = ""
        @Volatile var resolvingKey = ""
        @Volatile var pendingFormatSheet = false
        @Volatile var lastResolveReport = "لم يبدأ تحليل المصادر بعد"
        @Volatile var lastResolveCandidateCount = 0
        // Results extracted directly from the page URL by the real yt-dlp engine.
        @Volatile var ytdlpFormats: List<Format>? = null
        @Volatile var ytdlpPageUrl = ""
        @Volatile var ytdlpLoadingUrl = ""
        @Volatile var ytdlpAttemptedUrl = ""
        @Volatile var ytdlpReport = "لم يبدأ استخراج yt-dlp بعد"
        @Volatile var ytdlpGeneration = 0
        @Volatile var activeId: String? = null
        // Short-lived native frame playback proof prevents the periodic top-page scan
        // from clearing the FAB state when the actual player lives inside an iframe.
        @Volatile var framePlaybackActiveId: String? = null
        @Volatile var framePlaybackAt = 0L
        val userGestureVideoIds = LinkedHashSet<String>()
        @Volatile var userGestureAt = 0L
        var poster = ""
        var lastForced = ""
    }

    class VideoGestureBridge(
        private val onGesture: (String) -> Unit,
        private val onVideoEvent: (String, String, Boolean, String, Double, Int, Int, String, Boolean, String) -> Unit
    ) {
        @JavascriptInterface
        fun onVideoGesture(videoId: String?) {
            val id = videoId?.take(100)?.trim().orEmpty()
            if (id.isNotEmpty()) onGesture(id)
        }

        @JavascriptInterface
        fun onVideoEvent(
            videoId: String?, sourceKey: String?, isAd: Boolean, eventName: String?,
            duration: Double, sourceEpoch: Int, cycle: Int, sourcesJson: String?, adContainer: Boolean,
            sourceMetaJson: String?
        ) {
            val id = videoId?.take(100)?.trim().orEmpty()
            if (id.isNotEmpty()) {
                onVideoEvent(
                    id, sourceKey?.take(12000).orEmpty(), isAd,
                    eventName?.take(40).orEmpty(), duration,
                    sourceEpoch, cycle, sourcesJson?.take(16000).orEmpty(), adContainer,
                    sourceMetaJson?.take(24000).orEmpty()
                )
            }
        }
    }

    private val tabs = ArrayList<Tab>()
    private var cur: Tab? = null
    private lateinit var content: FrameLayout
    private lateinit var urlBar: EditText
    private lateinit var progress: ProgressBar
    private lateinit var fab: FrameLayout
    private lateinit var fabIcon: ImageView
    private lateinit var tabBtn: TextView
    private val io = Executors.newCachedThreadPool()
    // Resolve at most four candidate sources at once to reduce page-start latency without flooding CDNs.
    private val qualityResolver = Executors.newFixedThreadPool(4)
    private val ui = Handler(Looper.getMainLooper())
    private var customView: View? = null
    private var customCb: WebChromeClient.CustomViewCallback? = null
    private var desktop = false
    private lateinit var defaultUa: String
    private var pathView: TextView? = null
    private var refreshId: String? = null
    private lateinit var refreshBar: TextView

    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) {
            }
            Prefs.setFolder(this, uri.toString())
            pathView?.text = pathText()
        }
    }

    private val listLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val d = r.data
        val u = d?.getStringExtra("url")
        if (r.resultCode == RESULT_OK && d != null && u != null) {
            if (d.getBooleanExtra("newTab", false)) newTab(u) else cur?.web?.loadUrl(u)
        }
    }

    // ---------------------------------------------------------------- وضع تحديث رابط تحميل فاشل
    private fun refreshTask(): Task? = refreshId?.let { id -> DownloadCenter.tasks.firstOrNull { it.id == id } }

    private fun clearRefresh() {
        refreshId = null
        refreshBar.visibility = View.GONE
    }

    private fun handleRefreshIntent(i: Intent?) {
        if (i == null) return
        val id = i.getStringExtra("refreshTask") ?: return
        val page = i.getStringExtra("refreshUrl") ?: return
        i.removeExtra("refreshTask")
        val t = DownloadCenter.tasks.firstOrNull { it.id == id } ?: return
        refreshId = id
        refreshBar.text = "تحديث رابط «" + t.name + "»: شغّل نفس الفيديو ثم اضغط الزر الأحمر واختر نفس الجودة. (اضغط هنا للإلغاء)"
        refreshBar.visibility = View.VISIBLE
        newTab(page)
    }

    private fun openList(key: String) {
        listLauncher.launch(Intent(this, HistoryActivity::class.java).putExtra("key", key))
    }

    private val scanLoop = object : Runnable {
        override fun run() {
            cur?.let { scan(it) }
            ui.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DownloadCenter.init(this)
        defaultUa = WebSettings.getDefaultUserAgent(this).replace("; wv", "").replace(Regex(" Version/\\d+\\.\\d+"), "")
        desktop = Prefs.desktop(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        setContentView(buildUi())
        CookieManager.getInstance().setAcceptCookie(true)
        restoreTabs(intent?.data?.toString())
        handleRefreshIntent(intent)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (customView != null) { hideCustom(); return }
                val t = cur
                if (t != null && t.web.canGoBack()) t.web.goBack()
                else if (t != null && tabs.size > 1) closeTab(t)
                else finish()
            }
        })
        showLastCrash()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val u = intent.data?.toString()
        if (u != null) newTab(u)
        handleRefreshIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        MediaCaptureCoordinator.attach(this) { request ->
            ui.post { captureServiceWorkerRequest(request) }
        }
        ui.post(scanLoop)
    }

    override fun onPause() {
        MediaCaptureCoordinator.detach(this)
        ui.removeCallbacks(scanLoop)
        cur?.let {
            it.activePlayback = false
            it.activeId = null
            it.framePlaybackActiveId = null
            it.framePlaybackAt = 0L
            updateFab()
        }
        saveTabs()
        super.onPause()
    }

    override fun onStop() {
        saveTabs()
        super.onStop()
    }

    override fun onDestroy() {
        MediaCaptureCoordinator.detach(this)
        ui.removeCallbacksAndMessages(null)
        qualityResolver.shutdownNow()
        io.shutdownNow()
        content.removeAllViews()
        tabs.forEach {
            try { it.documentStartScript?.remove() } catch (_: Exception) {}
            it.web.destroy()
        }
        super.onDestroy()
    }

    // ---------------------------------------------------------------- حفظ واسترجاع التبويبات
    private fun saveTabs() {
        val kept = tabs.filter { it.url.startsWith("http") }
        if (kept.isEmpty()) return
        Store.put(this, "tabs", kept.map { it.title to it.url })
        getSharedPreferences("state", Context.MODE_PRIVATE).edit()
            .putInt("cur", kept.indexOf(cur).coerceAtLeast(0)).apply()
    }

    private fun restoreTabs(start: String?) {
        val saved = Store.get(this, "tabs")
        if (saved.isEmpty()) {
            newTab(start ?: Prefs.home(this))
            return
        }
        val idx = getSharedPreferences("state", Context.MODE_PRIVATE).getInt("cur", 0)
        for (p in saved) newTab(p.second, false, p.first)
        selectTab(tabs[idx.coerceIn(0, tabs.size - 1)])
        if (start != null) newTab(start)
    }

    private fun showLastCrash() {
        val f = File(filesDir, "crash.txt")
        if (!f.exists()) return
        val txt = try { f.readText() } catch (e: Exception) { return }
        val sv = ScrollView(this)
        sv.addView(TextView(this).apply { text = txt; textSize = 10f; setPadding(dp(16), dp(8), dp(16), dp(8)); setTextIsSelectable(true) })
        AlertDialog.Builder(this)
            .setTitle("حدث خطأ في التشغيل السابق")
            .setView(sv)
            .setPositiveButton("نسخ") { _, _ ->
                (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("crash", txt))
                f.delete()
            }
            .setNegativeButton("إغلاق") { _, _ -> f.delete() }
            .show()
    }

    // ---------------------------------------------------------------- واجهة المستخدم
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    @SuppressLint("ClickableViewAccessibility")
    private fun buildUi(): View {
        val dark = 0xFF455A64.toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(4), dp(6))
            setBackgroundColor(0xFFF1F3F4.toInt())
        }
        tabBtn = TextView(this).apply {
            text = "1"
            gravity = Gravity.CENTER
            textSize = 13f
            setTextColor(dark)
            background = rounded(Color.WHITE, 6, 0xFF607D8B.toInt())
            setOnClickListener { showTabs() }
        }
        top.addView(tabBtn, LinearLayout.LayoutParams(dp(30), dp(30)))
        urlBar = EditText(this).apply {
            hint = "ابحث أو اكتب رابط URL"
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            textSize = 14f
            textDirection = View.TEXT_DIRECTION_LTR
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            background = rounded(Color.WHITE, 22)
            setPadding(dp(14), 0, dp(14), 0)
            setOnEditorActionListener { v, _, _ ->
                go((v as EditText).text.toString())
                true
            }
        }
        top.addView(urlBar, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginStart = dp(8); marginEnd = dp(2) })
        top.addView(iconBtn(R.drawable.ic_more, dark, 8).apply { setOnClickListener { showMenu(it) } },
            LinearLayout.LayoutParams(dp(40), dp(40)))
        root.addView(top, LinearLayout.LayoutParams(MATCH, WRAP))

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
        }
        root.addView(progress, LinearLayout.LayoutParams(MATCH, dp(3)))

        refreshBar = TextView(this).apply {
            textSize = 13f
            setTextColor(0xFF5D4037.toInt())
            setBackgroundColor(0xFFFFF3E0.toInt())
            setPadding(dp(14), dp(8), dp(14), dp(8))
            visibility = View.GONE
            setOnClickListener { clearRefresh() }
        }
        root.addView(refreshBar, LinearLayout.LayoutParams(MATCH, WRAP))

        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(MATCH, 0, 1f))

        // الزر العائم
        fab = FrameLayout(this)
        fabIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_download)
            setPadding(dp(15), dp(15), dp(15), dp(15))
            background = circle(0xFF78909C.toInt())
            elevation = dp(6).toFloat()
        }
        fab.addView(fabIcon, FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER))
        content.addView(fab, FrameLayout.LayoutParams(dp(62), dp(62), Gravity.BOTTOM or Gravity.END).apply {
            setMargins(dp(14), dp(14), dp(14), dp(24))
        })
        fab.setOnTouchListener(object : View.OnTouchListener {
            var dx = 0f; var dy = 0f; var sx = 0f; var sy = 0f; var moved = false
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        dx = v.x - e.rawX; dy = v.y - e.rawY; sx = e.rawX; sy = e.rawY; moved = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (abs(e.rawX - sx) > dp(6) || abs(e.rawY - sy) > dp(6)) moved = true
                        if (moved) {
                            v.x = (e.rawX + dx).coerceIn(0f, (content.width - v.width).toFloat())
                            v.y = (e.rawY + dy).coerceIn(0f, (content.height - v.height).toFloat())
                        }
                    }
                    MotionEvent.ACTION_UP -> if (!moved) onFabClick()
                }
                return true
            }
        })

        return root
    }

    private fun hasPrerollPlayerRenditions(t: Tab): Boolean {
        val id = t.primaryVideoId ?: return false
        return t.media.values.any { item ->
            item.videoId == id && item.sourceEpoch == t.primarySourceEpoch &&
                item.origin == "dom-preroll" &&
                item.captureSource in setOf("player-flashvars", "player-xhamster") &&
                item.url != t.primaryCurrentSrc && !isLikelyAdUrl(item.url) && !isExcludedMediaUrl(item.url)
        }
    }

    private fun updateFab() {
        // No counters/badges. During a preroll the FAB can still turn red when the
        // user started playback and the player has exposed explicit movie renditions.
        val active = cur?.let { tab ->
            val normalPlayback = tab.activePlayback && !tab.primaryIsAd && !tab.suspectedPreroll
            val recentUserPlay = tab.primaryVideoId?.let { it in tab.userGestureVideoIds } == true &&
                System.currentTimeMillis() - tab.userGestureAt in 0L..300_000L
            val prerollCaptureReady = (tab.primaryIsAd || tab.suspectedPreroll) &&
                recentUserPlay && hasPrerollPlayerRenditions(tab)
            normalPlayback || prerollCaptureReady
        } == true
        (fabIcon.background as GradientDrawable).setColor(
            if (active) 0xFFE53935.toInt() else 0xFF78909C.toInt()
        )
        fabIcon.alpha = if (active) 1.0f else 0.88f
    }

    // ---------------------------------------------------------------- التبويبات
    private fun tabOf(w: WebView): Tab? = tabs.firstOrNull { it.web === w }

    private fun newTab(url: String?, load: Boolean = true, title: String = "") {
        val w = WebView(this)
        val t = Tab(w)
        setupWebView(w, t)
        tabs.add(t)
        if (load) {
            selectTab(t)
            if (url != null) w.loadUrl(url)
        } else {
            t.url = url ?: ""
            if (title.isNotEmpty()) t.title = title
            t.pending = true
        }
        tabBtn.text = tabs.size.toString()
    }

    private fun selectTab(t: Tab) {
        cur?.let {
            it.activePlayback = false
            it.activeId = null
            content.removeView(it.web)
            it.web.onPause()
        }
        cur = t
        t.web.onResume()
        content.addView(t.web, 0, FrameLayout.LayoutParams(MATCH, MATCH))
        if (t.pending) {
            t.pending = false
            t.web.loadUrl(t.url)
        }
        urlBar.setText(t.url)
        tabBtn.text = tabs.size.toString()
        progress.visibility = View.GONE
        updateFab()
    }

    private fun closeTab(t: Tab) {
        val i = tabs.indexOf(t)
        tabs.remove(t)
        t.activePlayback = false
        t.activeId = null
        t.media.clear()
        t.preparedMedia.clear()
        t.sniffed.clear()
        try { t.documentStartScript?.remove() } catch (_: Exception) {}
        t.documentStartScript = null
        t.requestContexts.clear()
        t.frameApiPayloads.clear()
        t.frameProbeStates.clear()
        t.frameProbeMessageCount = 0
        t.frameVideoMessageCount = 0
        t.frameReportedLightVideoCount = 0
        t.frameReportedShadowVideoCount = 0
        t.playerConfigObjectCount = 0
        t.playerMediaDefinitionCount = 0
        t.playerDefinitionQualities = ""
        t.frameVideoStates.clear()
        t.primaryFrameOrigin = ""
        t.primaryFrameId = ""
        t.primaryFrameSeenAt = 0L
        t.syntheticApiUpdatedAt = 0L
        t.pendingSniffed.clear()
        t.pageRequests.clear()
        t.resolvedFormats = null
        t.resolvedKey = ""
        t.resolvingKey = ""
        t.ytdlpGeneration++
        t.ytdlpFormats = null
        t.ytdlpPageUrl = ""
        t.ytdlpLoadingUrl = ""
        t.ytdlpAttemptedUrl = ""
        content.removeView(t.web)
        t.web.destroy()
        if (cur === t) cur = null
        if (tabs.isEmpty()) newTab(Prefs.home(this)) else if (cur == null) selectTab(tabs[minOf(i, tabs.size - 1)])
        tabBtn.text = tabs.size.toString()
    }

    private fun showTabs() {
        val names = tabs.mapIndexed { i, t -> "${i + 1}. " + t.title }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("علامات التبويب")
            .setItems(names) { _, i -> selectTab(tabs[i]) }
            .setPositiveButton("علامة جديدة") { _, _ -> newTab(Prefs.home(this)) }
            .setNeutralButton("إغلاق الحالية") { _, _ -> cur?.let { closeTab(it) } }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun go(input: String) {
        val s = input.trim()
        if (s.isEmpty()) return
        val url = when {
            s.startsWith("http://") || s.startsWith("https://") -> s
            s.contains(" ") || !s.contains(".") -> Prefs.searchUrl(this) + URLEncoder.encode(s, "UTF-8")
            else -> "https://$s"
        }
        cur?.web?.loadUrl(url)
        urlBar.clearFocus()
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(urlBar.windowToken, 0)
    }

    // ---------------------------------------------------------------- WebView
    private fun applyUa(w: WebView) {
        w.settings.userAgentString = if (desktop) DESKTOP_UA else defaultUa
    }

    /**
     * Install a native-only message bridge for cross-origin iframe captures.
     * Unlike window.top.postMessage, this does not reveal iframe API response bodies to page scripts.
     */
    private fun installFrameApiMessageBridge(w: WebView, tab: Tab) {
        try {
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                tab.frameBridgeRegistered = false
                tab.frameBridgeError = "WEB_MESSAGE_LISTENER غير مدعوم في مزود WebView"
                return
            }
            WebViewCompat.addWebMessageListener(
                w,
                "VideoBrowserCapture",
                setOf("*"),
                object : WebViewCompat.WebMessageListener {
                    override fun onPostMessage(
                        view: WebView,
                        message: WebMessageCompat,
                        sourceOrigin: Uri,
                        isMainFrame: Boolean,
                        replyProxy: JavaScriptReplyProxy
                    ) {
                        if (view !== tab.web || message.type != WebMessageCompat.TYPE_STRING) return
                        val raw = message.data ?: return
                        val actualOrigin = sourceOrigin.toString()
                        val messageKind = if (raw.length <= 160_000) {
                            try { JSONObject(raw).optString("kind") } catch (_: Exception) { "" }
                        } else ""
                        when (messageKind) {
                            // Frame probes are accepted from the top document and child frames.
                            "frame-probe" -> captureFrameProbe(tab, raw, actualOrigin, isMainFrame)
                            // The top document can also use this as a fallback for players
                            // inside open Shadow DOM that SCAN_JS cannot traverse directly.
                            "frame-video-state" -> captureFrameVideoState(tab, raw, actualOrigin)
                            // API bodies are bridged only from child frames; the main frame
                            // keeps its own queue and is consumed by SCAN_JS.
                            else -> if (!isMainFrame) captureFrameApiPayload(tab, raw, actualOrigin)
                        }
                    }
                }
            )
            tab.frameBridgeRegistered = true
            tab.frameBridgeError = ""
        } catch (e: Exception) {
            tab.frameBridgeRegistered = false
            val safeMessage = (e.message ?: "registration failed").replace(Regex("https?://\\S+"), "[origin]").take(120)
            tab.frameBridgeError = e.javaClass.simpleName + ": " + safeMessage
        }
    }

    private fun sameWebOrigin(first: String, second: String): Boolean {
        return try {
            val a = Uri.parse(first)
            val b = Uri.parse(second)
            val schemeA = a.scheme?.lowercase().orEmpty()
            val schemeB = b.scheme?.lowercase().orEmpty()
            val hostA = a.host?.lowercase().orEmpty()
            val hostB = b.host?.lowercase().orEmpty()
            fun normalizedPort(uri: Uri, scheme: String): Int =
                if (uri.port >= 0) uri.port else if (scheme == "https") 443 else if (scheme == "http") 80 else -1
            schemeA in setOf("http", "https") && schemeA == schemeB &&
                hostA.isNotBlank() && hostA == hostB && normalizedPort(a, schemeA) == normalizedPort(b, schemeB)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Record a tiny startup/heartbeat from every injected frame, even when it contains
     * no video element. This makes an empty frame state diagnosable instead of ambiguous.
     */
    private fun captureFrameProbe(t: Tab, raw: String, sourceOrigin: String, isMainFrame: Boolean) {
        if (tabs.none { it === t } || raw.length !in 20..4096) return
        try {
            val incoming = JSONObject(raw)
            if (incoming.optString("kind") != "frame-probe") return
            val claimedOrigin = incoming.optString("frameOrigin")
            val originIsWeb = sourceOrigin.startsWith("http://", true) || sourceOrigin.startsWith("https://", true)
            if (originIsWeb && !sameWebOrigin(claimedOrigin, sourceOrigin)) return
            // Opaque-origin frames are counted only as probes; their content is never used.
            val frameId = incoming.optString("frameId").take(100)
            if (frameId.isBlank()) return
            val now = System.currentTimeMillis()
            val storedOrigin = if (originIsWeb) sourceOrigin else "opaque-origin"
            val key = "$storedOrigin|$frameId"
            val probe = JSONObject()
                .put("origin", storedOrigin)
                .put("frameId", frameId)
                .put("phase", incoming.optString("phase").take(40))
                .put("protocol", incoming.optString("protocol").take(20))
                .put("isTop", isMainFrame)
                .put("readyState", incoming.optString("readyState").take(20))
                .put("bridgeReady", incoming.optBoolean("bridgeReady", false))
                .put("lightVideoCount", incoming.optInt("lightVideoCount", 0).coerceIn(0, 12))
                .put("shadowVideoCount", incoming.optInt("shadowVideoCount", 0).coerceIn(0, 12))
                .put("shadowRootCount", incoming.optInt("shadowRootCount", 0).coerceIn(0, 24))
                .put("at", now)
            t.frameProbeMessageCount++
            t.frameReportedLightVideoCount = maxOf(t.frameReportedLightVideoCount, probe.optInt("lightVideoCount"))
            t.frameReportedShadowVideoCount = maxOf(t.frameReportedShadowVideoCount, probe.optInt("shadowVideoCount"))
            t.frameProbeStates.remove(key)
            t.frameProbeStates[key] = probe.toString()
            t.frameProbeStates.entries.removeAll { entry ->
                try { now - JSONObject(entry.value).optLong("at", 0L) !in 0L..30_000L }
                catch (_: Exception) { true }
            }
            while (t.frameProbeStates.size > 32) {
                val oldest = t.frameProbeStates.keys.firstOrNull() ?: break
                t.frameProbeStates.remove(oldest)
            }
        } catch (_: Exception) {
            // Invalid diagnostic messages are ignored.
        }
    }

    /**
     * Receive bounded player state from a frame. The browser page cannot forge sourceOrigin:
     * it is supplied by AndroidX WebMessageListener itself.
     */
    private fun captureFrameVideoState(t: Tab, raw: String, sourceOrigin: String) {
        if (tabs.none { it === t } || raw.length !in 20..160_000 || !sourceOrigin.startsWith("http")) return
        try {
            val incoming = JSONObject(raw)
            if (incoming.optString("kind") != "frame-video-state") return
            val claimedOrigin = incoming.optString("frameOrigin")
            if (!sameWebOrigin(claimedOrigin, sourceOrigin)) return
            val frameId = incoming.optString("frameId").take(100)
            val incomingVideos = incoming.optJSONArray("videos") ?: return
            if (frameId.isBlank() || incomingVideos.length() == 0) return

            val safeVideos = JSONArray()
            for (i in 0 until minOf(incomingVideos.length(), 12)) {
                val item = incomingVideos.optJSONObject(i) ?: continue
                val videoId = item.optString("id").take(80)
                if (videoId.isBlank()) continue
                val sources = JSONArray()
                val sourceMeta = JSONArray()
                val sourceArray = item.optJSONArray("sources")
                val metaArray = item.optJSONArray("sourceMeta")
                val metaByUrl = HashMap<String, JSONObject>()
                if (metaArray != null) {
                    for (mi in 0 until minOf(metaArray.length(), 48)) {
                        val meta = metaArray.optJSONObject(mi) ?: continue
                        val url = meta.optString("url")
                        if (url.isNotBlank()) metaByUrl[url] = meta
                    }
                }
                val seen = HashSet<String>()
                if (sourceArray != null) {
                    for (si in 0 until minOf(sourceArray.length(), 48)) {
                        val url = sourceArray.optString(si)
                        if ((!url.startsWith("http://", true) && !url.startsWith("https://", true)) ||
                            !isCandidatePlayerSource(url) || !seen.add(url)) continue
                        sources.put(url)
                        val m = metaByUrl[url] ?: JSONObject()
                        sourceMeta.put(JSONObject()
                            .put("url", url)
                            .put("label", m.optString("label").take(80))
                            .put("width", m.optInt("width", 0).coerceIn(0, 10000))
                        .put("height", m.optInt("height", 0).coerceIn(0, 10000))
                        .put("mime", m.optString("mime").take(120))
                        .put("captureSource", m.optString("captureSource").take(80))
                        .put("codec", m.optString("codec").take(160))
                        .put("frameRate", m.optDouble("frameRate", 0.0).coerceIn(0.0, 240.0))
                        .put("dynamicRange", m.optString("dynamicRange").take(80))
                        .put("bandwidth", m.optLong("bandwidth", 0L).coerceIn(0L, 1_000_000_000L))
                        .put("representationId", m.optString("representationId").take(120)))
                    }
                }
                val src = item.optString("src").take(8192)
                val duration = item.optDouble("duration", 0.0).let { if (it.isFinite() && it >= 0.0) it else 0.0 }
                safeVideos.put(JSONObject()
                    .put("id", videoId).put("src", src).put("sources", sources).put("sourceMeta", sourceMeta)
                    .put("poster", item.optString("poster").take(4096))
                    .put("duration", duration)
                    .put("playing", item.optBoolean("playing", false))
                    .put("visible", item.optBoolean("visible", false))
                    .put("area", item.optDouble("area", 0.0).coerceIn(0.0, 100_000_000.0))
                    .put("width", item.optInt("width", 0).coerceIn(0, 10000))
                    .put("height", item.optInt("height", 0).coerceIn(0, 10000))
                    .put("ad", item.optBoolean("ad", false) || isLikelyAdUrl(src))
                    .put("adReason", item.optString("adReason").take(80))
                    .put("playerVideoDuration", item.optDouble("playerVideoDuration", 0.0).coerceIn(0.0, 86400.0))
                    .put("gesture", item.optBoolean("gesture", false))
                    .put("sourceEpoch", item.optInt("sourceEpoch", 0).coerceAtLeast(0))
                    .put("cycle", item.optInt("cycle", 0).coerceAtLeast(0))
                    .put("blob", item.optBoolean("blob", src.startsWith("blob:", true))))
            }
            if (safeVideos.length() == 0) return
            val now = System.currentTimeMillis()
            val key = "$sourceOrigin|$frameId"
            t.frameVideoMessageCount++
            t.frameReportedLightVideoCount = maxOf(t.frameReportedLightVideoCount, incoming.optInt("lightVideoCount", 0).coerceIn(0, 12))
            t.frameReportedShadowVideoCount = maxOf(t.frameReportedShadowVideoCount, incoming.optInt("shadowVideoCount", 0).coerceIn(0, 12))
            t.playerConfigObjectCount = maxOf(t.playerConfigObjectCount, incoming.optInt("playerConfigObjectCount", 0).coerceIn(0, 12))
            t.playerMediaDefinitionCount = maxOf(t.playerMediaDefinitionCount, incoming.optInt("playerMediaDefinitionCount", 0).coerceIn(0, 64))
            val incomingQualities = incoming.optJSONArray("playerDefinitionQualities")
            if (incomingQualities != null) {
                val q = (0 until minOf(incomingQualities.length(), 16)).map { incomingQualities.optString(it).take(12) }.filter { it.isNotBlank() }.distinct()
                if (q.isNotEmpty()) t.playerDefinitionQualities = q.joinToString(", ")
            }
            t.xhamsterStateFound = t.xhamsterStateFound || incoming.optBoolean("xhamsterStateFound", false)
            t.xhamsterSourceCount = maxOf(t.xhamsterSourceCount, incoming.optInt("xhamsterSourceCount", 0).coerceIn(0, 64))
            t.xhamsterOpaqueCount = maxOf(t.xhamsterOpaqueCount, incoming.optInt("xhamsterOpaqueCount", 0).coerceIn(0, 128))
            val frameXhQualities = incoming.optJSONArray("xhamsterQualities")
            if (frameXhQualities != null) {
                val values = (0 until minOf(frameXhQualities.length(), 24)).map { frameXhQualities.optString(it).take(16) }.filter { it.isNotBlank() }.distinct()
                if (values.isNotEmpty()) t.xhamsterQualities = values.joinToString(", ")
            }
            val stored = JSONObject().put("origin", sourceOrigin).put("frameId", frameId)
                .put("isTop", incoming.optBoolean("isTop", false))
                .put("at", now).put("videos", safeVideos).toString()
            t.frameVideoStates.remove(key)
            t.frameVideoStates[key] = stored
            t.frameVideoStates.entries.removeAll { entry ->
                try { now - JSONObject(entry.value).optLong("at", 0L) !in 0L..15_000L }
                catch (_: Exception) { true }
            }
            while (t.frameVideoStates.size > 16) {
                val oldest = t.frameVideoStates.keys.firstOrNull() ?: break
                t.frameVideoStates.remove(oldest)
            }
            applyFrameVideoStates(t)
        } catch (_: Exception) {
            // An invalid frame message is ignored without affecting the page.
        }
    }

    private fun applyFrameVideoStates(t: Tab) {
        // If SCAN_JS already selected a top-frame VIDEO, keep its identity but use the
        // native frame snapshot for playback/gesture state (especially for Blob/MSE players).
        val currentId = t.primaryVideoId
        if (!currentId.isNullOrBlank() && !currentId.startsWith("frame-") && !currentId.startsWith("api-")) {
            val expectedSrc = t.primaryCurrentSrc
            if (expectedSrc.isNotBlank()) {
                t.frameVideoStates.values.forEach { encoded ->
                    try {
                        val snapshot = JSONObject(encoded)
                        if (!snapshot.optBoolean("isTop", false) ||
                            !sameWebOrigin(t.url, snapshot.optString("origin")) ||
                            System.currentTimeMillis() - snapshot.optLong("at", 0L) !in 0L..15_000L) return@forEach
                        val videos = snapshot.optJSONArray("videos") ?: return@forEach
                        val match = (0 until videos.length()).mapNotNull { videos.optJSONObject(it) }
                            .firstOrNull { it.optString("src") == expectedSrc }
                        if (match != null) {
                            val playing = match.optBoolean("playing", false)
                            val gesture = match.optBoolean("gesture", false)
                            // The fresh frame snapshot is authoritative for this exact video.
                            // Do not OR with the previous ad flag, otherwise a completed preroll
                            // can keep the content permanently classified as an advertisement.
                            val ad = match.optBoolean("ad", false) || isLikelyAdUrl(expectedSrc)
                            t.playerVideoDurationSec = match.optDouble("playerVideoDuration", t.playerVideoDurationSec).takeIf { it.isFinite() && it in 0.0..86400.0 } ?: t.playerVideoDurationSec
                            t.playerAdReason = match.optString("adReason").take(80)
                            t.primaryIsAd = ad
                            val frameActive = FramePlaybackPolicy.shouldActivate(playing, gesture, ad, t.suspectedPreroll)
                            t.framePlaybackActiveId = if (frameActive) currentId else null
                            t.framePlaybackAt = System.currentTimeMillis()
                            if (frameActive) {
                                t.userGestureVideoIds.add(currentId)
                                t.userGestureAt = t.framePlaybackAt
                            }
                            t.activePlayback = frameActive
                            t.activeId = if (frameActive) currentId else null
                            if (cur === t) updateFab()
                        }
                    } catch (_: Exception) {}
                }
            }
            return
        }
        val now = System.currentTimeMillis()
        data class Candidate(val origin: String, val frameId: String, val item: JSONObject)
        val candidates = ArrayList<Candidate>()
        t.frameVideoStates.values.forEach { encoded ->
            try {
                val snapshot = JSONObject(encoded)
                if (now - snapshot.optLong("at", 0L) !in 0L..15_000L) return@forEach
                val origin = snapshot.optString("origin")
                val frameId = snapshot.optString("frameId")
                val videos = snapshot.optJSONArray("videos") ?: return@forEach
                for (i in 0 until videos.length()) {
                    val item = videos.optJSONObject(i) ?: continue
                    if (!item.optBoolean("visible", false) && !item.optBoolean("playing", false)) continue
                    candidates += Candidate(origin, frameId, item)
                }
            } catch (_: Exception) {}
        }
        if (candidates.isEmpty()) return
        val selected = candidates.sortedWith(
            compareBy<Candidate> { it.item.optBoolean("ad", false) }
                .thenByDescending { it.item.optBoolean("playing", false) }
                .thenByDescending { it.item.optBoolean("gesture", false) }
                .thenByDescending { it.item.optDouble("area", 0.0) }
        ).first()
        val item = selected.item
        val videoId = item.optString("id")
        val id = "frame-" + Integer.toHexString("${selected.origin}|${selected.frameId}|$videoId".hashCode())
        val src = item.optString("src")
        val sourceEpoch = item.optInt("sourceEpoch", 0)
        val cycle = item.optInt("cycle", 0)
        val isAd = item.optBoolean("ad", false)
        t.playerVideoDurationSec = item.optDouble("playerVideoDuration", 0.0).takeIf { it.isFinite() && it in 0.0..86400.0 } ?: 0.0
        t.playerAdReason = item.optString("adReason").take(80)
        val sourceKey = src + "||" + (item.optJSONArray("sources")?.let { arr ->
            (0 until arr.length()).joinToString("||") { arr.optString(it) }
        } ?: "")
        val primaryChanged = t.primaryVideoId != id
        val sourceChanged = primaryChanged || t.primarySourceKey != sourceKey ||
            t.primarySourceEpoch != sourceEpoch || t.primaryCycle != cycle
        val wasAd = t.primaryIsAd
        val wasPreroll = t.suspectedPreroll
        val duration = item.optDouble("duration", 0.0)
        val nextPreroll = !isAd && duration in 0.001..12.0 &&
            (item.optJSONArray("sources")?.length() ?: 0) > 0
        if (primaryChanged) {
            t.media.clear()
            t.preparedMedia.clear()
            t.sniffed.clear()
            t.pendingSniffed.clear()
            t.resolvedFormats = null
            t.resolvedKey = ""
            t.resolvingKey = ""
        } else if (sourceChanged) {
            t.sniffed.entries.removeAll { it.value.videoId == id && it.value.sourceEpoch != sourceEpoch }
            if (!wasAd && !isAd && !wasPreroll && !nextPreroll) {
                t.preparedMedia.entries.removeAll { it.value.videoId == id && it.value.sourceEpoch != sourceEpoch }
            }
        }
        t.primaryVideoId = id
        t.primaryFrameOrigin = selected.origin
        t.primaryFrameId = selected.frameId
        t.primaryFrameSeenAt = now
        t.primarySourceKey = sourceKey
        t.primarySourceEpoch = sourceEpoch
        t.primaryCycle = cycle
        t.primaryCurrentSrc = src
        t.primaryIsAd = isAd
        t.suspectedPreroll = nextPreroll
        t.expectedDurationSec = if (!isAd && !t.suspectedPreroll && duration > 12.0) duration else 0.0
        if (!primaryChanged && (wasAd || wasPreroll) && !isAd && !nextPreroll) {
            promotePendingSniffed(t, id, sourceEpoch)
        }
        t.sawBlob = item.optBoolean("blob", src.startsWith("blob:", true))
        t.poster = item.optString("poster")
        if (primaryChanged) promotePageRequests(t, id, sourceEpoch, isAd || t.suspectedPreroll)
        val sources = item.optJSONArray("sources")
        val metadata = item.optJSONArray("sourceMeta")
        if (sources != null) {
            val metaByUrl = HashMap<String, JSONObject>()
            if (metadata != null) for (i in 0 until metadata.length()) {
                val meta = metadata.optJSONObject(i) ?: continue
                val url = meta.optString("url")
                if (url.isNotBlank()) metaByUrl[url] = meta
            }
            for (i in 0 until sources.length()) addPreparedSource(t, sources.optString(i), id, metaByUrl[sources.optString(i)])
        }
        val framePlaying = item.optBoolean("playing", false)
        val frameGesture = item.optBoolean("gesture", false)
        if (!isAd && !t.suspectedPreroll && framePlaying && frameGesture) {
            t.userGestureVideoIds.add(id)
            t.userGestureAt = now
        }
        // SCAN_JS cannot observe currentTime/gesture inside a cross-origin iframe.
        // Use the authenticated native frame snapshot for the FAB state instead.
        t.activePlayback = FramePlaybackPolicy.shouldActivate(framePlaying, frameGesture, isAd, t.suspectedPreroll)
        t.framePlaybackActiveId = if (t.activePlayback) id else null
        t.framePlaybackAt = now
        t.activeId = if (t.activePlayback) id else null
        if ((t.primaryIsAd || t.suspectedPreroll) && !isAd) return
        syncMedia(t)
        if (cur === t) updateFab()
    }

    /** Validate and store bounded iframe API messages outside the page's JavaScript world. */
    private fun captureFrameApiPayload(t: Tab, raw: String, sourceOrigin: String) {
        if (raw.length !in 2..705_000) return
        try {
            val incoming = JSONObject(raw)
            val claimedOrigin = incoming.optString("frameOrigin")
            if (!sameWebOrigin(claimedOrigin, sourceOrigin)) return
            val url = incoming.optString("url")
            if (url.length !in 8..8192) return
            val urlScheme = Uri.parse(url).scheme?.lowercase()
            if (urlScheme != "http" && urlScheme != "https") return
            val body = incoming.optString("text")
            // Native validates relevance independently; page-provided bridge messages are untrusted.
            if (body.isBlank() || body.length > 240_000 ||
                !Regex("#EXTM3U|(?:sources|qualities|renditions|formats|m3u8|mpd|video[_-]?url|videoUrl|contentUrl|resolution|height|playbackUrl)", RegexOption.IGNORE_CASE).containsMatchIn(body)
            ) return
            val source = incoming.optString("source")
            if (source != "fetch-api" && source != "xhr-api") return
            val method = incoming.optString("method", "GET").uppercase()
            if (method !in setOf("GET", "POST", "PUT")) return

            val rawHeaders = incoming.optJSONObject("headers")
            val headerMap = LinkedHashMap<String, String>()
            if (rawHeaders != null) {
                val keys = rawHeaders.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    headerMap[key] = rawHeaders.optString(key)
                }
            }
            val safeHeaders = CaptureHeaders.safeRequestHeaders(headerMap)
            val key = sourceOrigin + "|" + url + "|" + method + "|" + body.length + "|" + body.take(96)
            val safePayload = JSONObject()
                .put("key", key)
                .put("url", url)
                .put("text", body)
                .put("at", incoming.optLong("at", System.currentTimeMillis()))
                .put("type", incoming.optString("type").take(128))
                .put("method", method)
                .put("headers", JSONObject(safeHeaders))
                .put("source", source)
                .put("frameOrigin", sourceOrigin)
            val encoded = safePayload.toString()
            t.frameApiPayloads.remove(key)
            t.frameApiPayloads[key] = encoded

            val now = System.currentTimeMillis()
            t.frameApiPayloads.entries.removeAll { entry ->
                try { now - JSONObject(entry.value).optLong("at", 0L) !in 0L..180_000L }
                catch (_: Exception) { true }
            }
            var totalChars = t.frameApiPayloads.values.sumOf { it.length }
            while (t.frameApiPayloads.size > 16 || totalChars > 300_000) {
                val oldest = t.frameApiPayloads.keys.firstOrNull() ?: break
                totalChars -= t.frameApiPayloads.remove(oldest)?.length ?: 0
            }
        } catch (_: Exception) {
            // Page-provided messages are untrusted; malformed payloads are ignored.
        }
    }

    /** Install before the first loadUrl so the hook runs before page JavaScript in every frame. */
    private fun installEarlyApiCapture(w: WebView, tab: Tab) {
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                tab.documentStartScript = WebViewCompat.addDocumentStartJavaScript(
                    w,
                    EARLY_API_CAPTURE_JS,
                    setOf("*")
                )
                tab.documentStartCaptureInstalled = true
            }
        } catch (_: Exception) {
            tab.documentStartScript = null
            tab.documentStartCaptureInstalled = false
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView(w: WebView, tab: Tab) {
        val s = w.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.mediaPlaybackRequiresUserGesture = true
        s.useWideViewPort = true
        s.loadWithOverviewMode = true
        s.builtInZoomControls = true
        s.displayZoomControls = false
        s.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        s.javaScriptCanOpenWindowsAutomatically = false
        applyUa(w)
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, true)
        installFrameApiMessageBridge(w, tab)
        installEarlyApiCapture(w, tab)
        w.addJavascriptInterface(VideoGestureBridge(
            onGesture = { id ->
                ui.post {
                    // لا نحذف التفاعل عند pause/ended؛ فقد يكون هذا انتقالًا من إعلان قصير إلى المحتوى.
                    tab.userGestureVideoIds.add(id)
                    tab.userGestureAt = System.currentTimeMillis()
                    if (cur === tab) updateFab()
                    if (tab.userGestureVideoIds.size > 12) {
                        val keep = tab.userGestureVideoIds.toList().takeLast(8)
                        tab.userGestureVideoIds.clear()
                        tab.userGestureVideoIds.addAll(keep)
                    }
                }
            },
            onVideoEvent = { id, key, isAd, event, duration, epoch, cycle, sourcesJson, adContainer, sourceMetaJson ->
                handleVideoEvent(tab, id, key, isAd, event, duration, epoch, cycle, sourcesJson, adContainer, sourceMetaJson)
            }
        ), "VBVideoGesture")

        w.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                val u = r.url.toString()
                val host = r.url.host ?: ""
                if (r.isForMainFrame && host.startsWith("accounts.") && host.endsWith("google.com")) {
                    val t = tabOf(v)
                    if (t != null && t.lastForced != u) {
                        t.lastForced = u
                        v.loadUrl(u, mapOf("X-Requested-With" to ""))
                        return true
                    }
                }
                return !(u.startsWith("http://") || u.startsWith("https://") || u.startsWith("about:") ||
                    u.startsWith("data:") || u.startsWith("blob:"))
            }

            override fun onPageStarted(v: WebView, url: String, favicon: Bitmap?) {
                val t = tabOf(v) ?: return
                // Older WebView providers may not implement DOCUMENT_START_SCRIPT.
                // This fallback is best-effort and cannot guarantee catching the first page request.
                if (!t.documentStartCaptureInstalled && url.startsWith("http", true)) {
                    try { v.evaluateJavascript(EARLY_API_CAPTURE_JS, null) } catch (_: Exception) {}
                }
                t.url = url
                resetVideoSession(t)
                if (t === cur) { urlBar.setText(url); updateFab() }
            }

            override fun onPageFinished(v: WebView, url: String) {
                val t = tabOf(v) ?: return
                t.url = url
                t.title = v.title ?: url
                addHistory(t.title, url)
                // Safety net for the main document: even when the document-start script
                // was registered but did not execute in this provider, install the same
                // idempotent observer after navigation. Child frames still use document-start.
                try { v.evaluateJavascript(EARLY_API_CAPTURE_JS, null) } catch (_: Exception) {}
                scan(t)
                // Start page-first yt-dlp extraction after the document has loaded;
                // this does not require pressing Play and does not change the FAB state.
                startYtDlpPreflight(t, url)
            }

            override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse? {
                val u = r.url.toString()
                // Snapshot only allowlisted request headers while still on the request callback.
                // This callback is passive: returning null lets WebView perform the original request.
                // Player-declared rendition URLs may be opaque (no extension or quality query).
                // Capture their safe request context too; rec() will retain it only when the
                // URL is a real media candidate or matches a prepared player rendition.
                if (r.method.equals("GET", true) && isCandidatePlayerSource(u)) {
                    val captured = MediaCaptureCoordinator.snapshot(
                        url = u,
                        method = r.method,
                        requestHeaders = r.requestHeaders,
                        fallbackPageUrl = tab.url,
                        source = "webview",
                        isMainFrame = r.isForMainFrame,
                        hasGesture = r.hasGesture()
                    )
                    ui.post { rec(tab, captured) }
                }
                return null
            }
        }

        w.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(v: WebView, p: Int) {
                if (tabOf(v) === cur) {
                    progress.progress = p
                    progress.visibility = if (p in 1..99) View.VISIBLE else View.GONE
                }
            }

            override fun onReceivedTitle(v: WebView, title: String?) {
                if (title != null) tabOf(v)?.title = title
            }

            override fun onShowCustomView(view: View, cb: WebChromeClient.CustomViewCallback) {
                if (customView != null) { cb.onCustomViewHidden(); return }
                customView = view
                customCb = cb
                view.setBackgroundColor(Color.BLACK)
                (window.decorView as ViewGroup).addView(view, ViewGroup.LayoutParams(MATCH, MATCH))
                WindowCompat.getInsetsController(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
            }

            override fun onHideCustomView() { hideCustom() }
        }

        w.setDownloadListener { url, _, cd, mime, len ->
            if (url.startsWith("blob:") || url.startsWith("data:")) {
                toast("هذا النوع من الروابط لا يمكن تحميله مباشرة")
            } else {
                val fname = URLUtil.guessFileName(url, cd, mime)
                val ext = fname.substringAfterLast('.', "bin")
                val t = cur
                val f = Format("الملف", "", url, "direct", ext, false, if (len > 0) Util.fmtSize(len) else "")
                showFormatSheet(listOf(f), if (t != null) headersFor(t, w) else emptyMap(), fname.substringBeforeLast('.'), "")
            }
        }
    }

    private fun hideCustom() {
        val v = customView ?: return
        (window.decorView as ViewGroup).removeView(v)
        customView = null
        customCb?.onCustomViewHidden()
        customCb = null
        WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
    }

    private fun captureServiceWorkerRequest(request: CapturedMediaRequest) {
        if (request.method != "GET" || request.isMainFrame || !isCandidateMediaRequest(request.url)) return
        val page = request.pageUrl
        val pageUri = try { Uri.parse(page) } catch (_: Exception) { null }
        val pageHost = pageUri?.host?.lowercase()
        val matches = if (!pageHost.isNullOrBlank()) tabs.filter {
            try { Uri.parse(it.url).host?.equals(pageHost, true) == true } catch (_: Exception) { false }
        } else emptyList()
        val exactMatches = if (pageUri != null && !pageUri.path.isNullOrBlank()) matches.filter {
            try { Uri.parse(it.url).path == pageUri.path } catch (_: Exception) { false }
        } else emptyList()
        val tab = when {
            exactMatches.size == 1 -> exactMatches.first()
            matches.size == 1 -> matches.first()
            tabs.size == 1 && page.isBlank() -> tabs.first()
            else -> null // Do not assign an ambiguous Service Worker request to the wrong tab.
        } ?: return
        rec(tab, request)
    }

    private fun addHistory(title: String, url: String) {
        if (!url.startsWith("http")) return
        val l = Store.entries(this, "history")
        l.removeAll { it.url == url }
        l.add(0, Store.Entry(title, url, System.currentTimeMillis()))
        Store.putEntries(this, "history", l)
    }

    // ---------------------------------------------------------------- اكتشاف الفيديو الرئيسي واستبعاد الإعلانات والمعاينات
    private fun isLikelyAdUrl(raw: String): Boolean = Formats.isAdUrl(raw)

    private fun isExcludedMediaUrl(raw: String): Boolean {
        if (isLikelyAdUrl(raw)) return true
        val decoded = try { java.net.URLDecoder.decode(raw, "UTF-8") } catch (_: Exception) { raw }
        val uri = Uri.parse(decoded)
        val pathAndQuery = (uri.path.orEmpty() + "?" + uri.query.orEmpty()).lowercase()
        val unwantedRoles = listOf(
            "preview", "previews", "thumbnail", "thumb", "thumbs", "poster",
            "trailer", "trailers", "teaser", "teasers", "storyboard", "sprite"
        )
        val rolePattern = Regex("(^|[/_.?&=\\-])(" + unwantedRoles.joinToString("|") { Regex.escape(it) } + ")([/_.?&=\\-]|$)")
        return rolePattern.containsMatchIn(pathAndQuery)
    }

    private fun isStreamingChunkRequest(raw: String): Boolean {
        val uri = Uri.parse(raw)
        val path = uri.path.orEmpty().lowercase()
        val name = path.substringAfterLast('/')
        if (path.endsWith(".ts") || path.endsWith(".m4s") || path.endsWith(".cmfv") || path.endsWith(".cmfa")) return true
        val chunkQueryNames = setOf("sq", "range", "chunk", "chunks", "segment", "segments", "fragment", "part")
        if (uri.queryParameterNames.any { it.lowercase() in chunkQueryNames }) return true
        val chunkName = Regex("(^|[-_.])(chunk|chunks|chunked|segment|segments|fragment|fragments|part|seg)([-_.]?[0-9]*)([-_.]|$)")
        return chunkName.containsMatchIn(name)
    }

    private fun isHlsUrl(raw: String): Boolean {
        val uri = Uri.parse(raw)
        val path = uri.path.orEmpty().lowercase()
        if (path.endsWith(".m3u8")) return true
        val hints = setOf("type", "mime", "mimetype", "contenttype", "format", "response-content-type")
        return uri.queryParameterNames.any { key ->
            key.lowercase() in hints && (uri.getQueryParameter(key) ?: "").lowercase().let {
                it.contains("mpegurl") || it.contains("m3u8")
            }
        }
    }

    private fun isCandidateMediaRequest(raw: String): Boolean {
        if (!raw.startsWith("http://", true) && !raw.startsWith("https://", true)) return false
        if (isExcludedMediaUrl(raw)) return false
        val uri = Uri.parse(raw)
        val path = uri.path.orEmpty().lowercase()
        if (path.endsWith(".m3u8")) return true
        if (isStreamingChunkRequest(raw)) return false
        val directVideo = listOf(".mp4", ".m4v", ".webm", ".mkv", ".mov", ".flv", ".3gp")
            .any { path.endsWith(it) }
        if (directVideo) return true
        if (path.contains("/videoplayback")) {
            return uri.getQueryParameter("sq") == null && uri.getQueryParameter("range") == null
        }
        // بعض مشغلات المواقع تستخدم روابط CDN بلا امتداد ملف، وتضع الجودة أو MIME في query.
        // لا نقبلها إلا بوجود حقل يدل صراحة على تمثيل فيديو.
        val videoParamNames = setOf("mime", "mimetype", "contenttype", "type", "format", "itag", "quality", "resolution", "height", "video")
        val hasVideoParam = uri.queryParameterNames.any { it.lowercase() in videoParamNames }
        val querySignalsVideo = uri.queryParameterNames.any { name ->
            val n = name.lowercase()
            if (n !in videoParamNames) false else {
                val value = (uri.getQueryParameter(name) ?: "").lowercase()
                n in setOf("mime", "mimetype", "contenttype", "type", "format") &&
                    (value.contains("video") || value.contains("mp4") || value.contains("webm") || value.contains("mpegurl")) ||
                    n in setOf("itag", "quality", "resolution", "height", "video")
            }
        }
        if (hasVideoParam && querySignalsVideo) return true
        return false
    }

    /** مصدر قادم مباشرة من إعدادات مشغل الفيديو/عنصره؛ قد يكون endpoint بلا امتداد. */
    private fun isCandidatePlayerSource(raw: String): Boolean {
        if (!raw.startsWith("http://", true) && !raw.startsWith("https://", true)) return false
        if (isExcludedMediaUrl(raw) || isStreamingChunkRequest(raw)) return false
        if (isCandidateMediaRequest(raw)) return true
        val path = Uri.parse(raw).path.orEmpty().lowercase()
        if (Regex("\\.(jpg|jpeg|png|gif|webp|avif|svg|ico|css|js|json|vtt|srt|woff2?|ttf|otf)$").containsMatchIn(path)) return false
        return true
    }

    private fun resetVideoSession(t: Tab) {
        t.media.clear()
        t.preparedMedia.clear()
        t.sniffed.clear()
        t.requestContexts.clear()
        t.frameApiPayloads.clear()
        t.frameProbeStates.clear()
        t.frameProbeMessageCount = 0
        t.frameVideoMessageCount = 0
        t.frameReportedLightVideoCount = 0
        t.frameReportedShadowVideoCount = 0
        t.playerConfigObjectCount = 0
        t.playerMediaDefinitionCount = 0
        t.playerDefinitionQualities = ""
        t.playerVideoDurationSec = 0.0
        t.playerAdReason = ""
        t.xhamsterStateFound = false
        t.xhamsterSourceCount = 0
        t.xhamsterQualities = ""
        t.xhamsterOpaqueCount = 0
        t.pendingFormatSheet = false
        t.frameVideoStates.clear()
        t.primaryFrameOrigin = ""
        t.primaryFrameId = ""
        t.primaryFrameSeenAt = 0L
        t.syntheticApiUpdatedAt = 0L
        t.pendingSniffed.clear()
        t.pageRequests.clear()
        t.primaryVideoId = null
        t.primarySourceKey = ""
        t.primaryCurrentSrc = ""
        t.primarySourceEpoch = 0
        t.primaryCycle = 0
        t.primaryIsAd = false
        t.suspectedPreroll = false
        t.expectedDurationSec = 0.0
        t.resolvedFormats = null
        t.resolvedKey = ""
        t.resolvingKey = ""
        t.lastResolveReport = "لم يبدأ تحليل المصادر بعد"
        t.lastResolveCandidateCount = 0
        t.ytdlpGeneration++
        t.ytdlpFormats = null
        t.ytdlpPageUrl = ""
        t.ytdlpLoadingUrl = ""
        t.ytdlpAttemptedUrl = ""
        t.ytdlpReport = "لم يبدأ استخراج yt-dlp بعد"
        t.activePlayback = false
        t.activeId = null
        t.framePlaybackActiveId = null
        t.framePlaybackAt = 0L
        t.userGestureVideoIds.clear()
        t.userGestureAt = 0L
        t.sawBlob = false
        t.poster = ""
    }

    private fun syncMedia(t: Tab) {
        t.media.clear()
        val id = t.primaryVideoId
        val now = System.currentTimeMillis()
        t.preparedMedia.forEach { (url, item) ->
            val context = t.requestContexts[url]
            val freshContext = context?.takeIf {
                CaptureHeaders.matchesPreparedSource(url, it, t.primaryFrameOrigin, t.url, now)
            }
            val headers = if (freshContext != null) CaptureHeaders.merge(item.requestHeaders, freshContext.headers) else item.requestHeaders
            t.media[url] = if (headers == item.requestHeaders) item else MediaItem(
                item.url, item.type, item.origin, item.videoId, item.sourceEpoch, item.qualityHint,
                item.widthHint, item.heightHint, item.mimeHint, headers,
                item.captureSource.ifBlank { freshContext?.source.orEmpty() }, item.codecHint,
                item.frameRateHint, item.dynamicRangeHint, item.bandwidthHint, item.representationIdHint
            )
        }
        if (id != null && !t.primaryIsAd && !t.suspectedPreroll) {
            t.sniffed.values
                .filter { it.videoId == id && it.sourceEpoch == t.primarySourceEpoch && now - it.requestedAt in 0L..180_000L }
                .forEach { captured ->
                    if (isCandidateMediaRequest(captured.url) || t.preparedMedia.containsKey(captured.url)) {
                        // لا تسمح لالتقاط الشبكة الخام بأن يمحو بيانات الجودة التي استخرجناها
                        // مسبقًا من إعدادات المشغل لنفس الرابط (خصوصًا signed URLs بلا امتداد).
                        val prepared = t.preparedMedia[captured.url] ?: t.media[captured.url]
                        val origin = when {
                            prepared?.origin == "dom-preroll" -> "dom-preroll"
                            prepared?.origin == "dom" -> "dom"
                            captured.capturedDuringAd -> "network-pending-ad"
                            else -> "network"
                        }
                        t.media[captured.url] = MediaItem(
                            captured.url,
                            prepared?.type ?: if (isHlsUrl(captured.url)) "hls" else "direct",
                            origin,
                            id,
                            captured.sourceEpoch,
                            prepared?.qualityHint.orEmpty(),
                            prepared?.widthHint ?: 0,
                            prepared?.heightHint ?: 0,
                            prepared?.mimeHint.orEmpty(),
                            CaptureHeaders.merge(
                                prepared?.requestHeaders.orEmpty(),
                                captured.request?.headers.orEmpty()
                            ),
                            captured.request?.source.orEmpty(),
                            prepared?.codecHint.orEmpty(), prepared?.frameRateHint ?: 0.0,
                            prepared?.dynamicRangeHint.orEmpty(), prepared?.bandwidthHint ?: 0L,
                            prepared?.representationIdHint.orEmpty()
                        )
                    }
                }
        }
        // Prepare qualities in the background, but never use preparation state to color the FAB.
        scheduleBackgroundResolve(t)
    }

    private fun resolutionKey(t: Tab): String {
        val id = t.primaryVideoId.orEmpty()
        val sources = t.media.values
            .sortedBy { it.url }
            .joinToString("\n") { item ->
                val headerKey = item.requestHeaders.entries
                    .sortedBy { header -> header.key.lowercase() }
                    .joinToString(";") { header -> "${header.key}:${header.value}" }
                "${item.origin}|${item.type}|${item.url}|${item.qualityHint}|${item.widthHint}x${item.heightHint}|${item.mimeHint}|${item.codecHint}|${item.frameRateHint}|${item.dynamicRangeHint}|${item.bandwidthHint}|${item.representationIdHint}|${item.captureSource}|$headerKey"
            }
        return "$id|${t.primarySourceEpoch}|${t.primaryCurrentSrc}|${t.primaryIsAd}|${t.suspectedPreroll}|${"%.1f".format(java.util.Locale.US, t.expectedDurationSec)}|$sources"
    }

    private data class ResolveOutcome(
        val formats: List<Format>,
        val attempted: Int,
        val resolvedSources: Int,
        val emptySources: Int,
        val rejectedByContentFilter: Int,
        val failures: List<String>,
        val filterDetails: List<String> = emptyList()
    ) {
        fun summary(): String = buildString {
            append("المصادر المرشحة: ").append(attempted)
            append(" | مصادر أنتجت نتائج: ").append(resolvedSources)
            append(" | مصادر بلا صيغ: ").append(emptySources)
            append(" | صيغ استبعدها فلتر المحتوى: ").append(rejectedByContentFilter)
            append(" | صيغ الفيديو النهائية: ").append(formats.size)
            if (failures.isNotEmpty()) {
                append("\nأسباب تعذر التحليل:")
                failures.take(5).forEach { append("\n• ").append(it) }
            }
            if (filterDetails.isNotEmpty()) {
                append("\nتفاصيل صيغ استبعدها فلتر المحتوى:")
                filterDetails.take(4).forEach { append("\n• ").append(it) }
            }
            if (formats.isEmpty() && failures.isEmpty()) {
                append("\nلم ينتج أي مصدر صيغة فيديو مقبولة؛ تحقق من نوع الاستجابة أو تصنيف الإعلان/المعاينة.")
            }
        }
    }

    private fun shortResolveError(error: Throwable): String {
        val message = (error.message ?: error.javaClass.simpleName)
            .replace(Regex("https?://\\S+"), "[رابط مخفي]")
            .replace(Regex("[?&](token|sig|signature|auth|key|expires|session)=[^&\\s]+", RegexOption.IGNORE_CASE), "[معامل مخفي]")
            .take(180)
        return message.ifBlank { error.javaClass.simpleName }
    }

    private fun resolveQualityGroups(
        items: List<MediaItem>,
        headers: Map<String, String>,
        expectedDuration: Double,
        isAdOrPreroll: Boolean,
        currentSrc: String
    ): ResolveOutcome {
        if (items.isEmpty()) return ResolveOutcome(emptyList(), 0, 0, 0, 0, emptyList())

        // Four workers maximum; collect in source order so row order stays deterministic.
        val candidates = items.take(48)
        val futures = candidates.map { item -> item to qualityResolver.submit<List<Format>> { Formats.resolve(item, headers) } }
        val groups = ArrayList<Pair<MediaItem, List<Format>>>()
        val failures = ArrayList<String>()
        var emptySources = 0
        val deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(70)
        for ((item, future) in futures) {
            try {
                val remainingNanos = deadlineNanos - System.nanoTime()
                if (remainingNanos <= 0L) throw java.util.concurrent.TimeoutException("انتهت مهلة تحليل مجموعة المصادر")
                val result = future.get(remainingNanos, TimeUnit.NANOSECONDS)
                if (result.isEmpty()) emptySources++ else groups.add(item to result)
            } catch (e: Exception) {
                future.cancel(true)
                val cause = if (e is ExecutionException) (e.cause ?: e) else e
                val host = try { Uri.parse(item.url).host ?: "مصدر" } catch (_: Exception) { "مصدر" }
                failures.add("$host: ${shortResolveError(cause)}")
            }
        }

        fun duration(group: List<Format>): Double =
            group.map { it.durSec }.filter { it.isFinite() && it > 0.0 }.maxOrNull() ?: 0.0

        val longest = groups.maxOfOrNull { duration(it.second) } ?: 0.0
        val hasLongAlternative = groups.any { (_, formats) ->
            val d = duration(formats)
            d > 20.0 && (expectedDuration !in 1.0..12.0 || d >= expectedDuration + maxOf(20.0, expectedDuration * 2.0))
        }
        var rejectedByContentFilter = 0
        val filterDetails = ArrayList<String>()
        val usable = groups.mapNotNull { (item, formats) ->
            val requiresNetworkDurationProof = item.origin == "network-pending-ad"
            val isPredeclaredPlayerSource = item.origin == "dom-preroll"
            val checkedFormats = formats.filter { format ->
                val d = format.durSec.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
                val rank = qualityRank(format.label)
                val accepted = when {
                    requiresNetworkDurationProof -> d > 20.0 && when {
                        expectedDuration > 20.0 -> abs(d - expectedDuration) <= maxOf(18.0, expectedDuration * 0.12)
                        expectedDuration in 1.0..12.0 -> d >= expectedDuration + maxOf(20.0, expectedDuration * 2.0)
                        else -> true
                    }
                    isPredeclaredPlayerSource -> item.url != currentSrc && (
                        (d > 20.0 && (expectedDuration <= 20.0 || abs(d - expectedDuration) <= maxOf(18.0, expectedDuration * 0.12))) ||
                            (d <= 0.0 && rank > 0)
                        )
                    isAdOrPreroll -> item.origin == "dom" && item.url != currentSrc && (
                        (d > 20.0 && (expectedDuration <= 20.0 || abs(d - expectedDuration) <= maxOf(18.0, expectedDuration * 0.12))) ||
                            (d <= 0.0 && rank > 0)
                        )
                    expectedDuration > 20.0 -> d <= 0.0 || abs(d - expectedDuration) <= maxOf(18.0, expectedDuration * 0.12)
                    expectedDuration in 1.0..12.0 && hasLongAlternative ->
                        d <= 0.0 || d >= expectedDuration + maxOf(20.0, expectedDuration * 2.0)
                    expectedDuration <= 0.0 && longest > 20.0 -> d <= 0.0 || d >= maxOf(20.0, longest * 0.35)
                    else -> true
                }
                if (!accepted) {
                    rejectedByContentFilter++
                    if (filterDetails.size < 8) {
                        val host = try { Uri.parse(item.url).host ?: "مصدر" } catch (_: Exception) { "مصدر" }
                        val expectedText = if (expectedDuration > 0.0) String.format(java.util.Locale.US, "%.0fs", expectedDuration) else "غير معلنة"
                        val actualText = if (d > 0.0) String.format(java.util.Locale.US, "%.0fs", d) else "غير معروفة"
                        filterDetails.add("$host / ${item.captureSource.ifBlank { item.origin }} / ${format.label}: مدة $actualText مقابل $expectedText")
                    }
                }
                accepted
            }
            if (checkedFormats.isEmpty()) null else item to checkedFormats
        }

        val collected = usable.flatMap { it.second }.filter { format ->
            !format.isAudio && !Formats.isAudio(format.ext) && !isExcludedMediaUrl(format.url) &&
                (format.kind == "hls" || Util.kindOf("x." + format.ext) == "video")
        }
        // Keep different representations at the same height (AVC/HEVC, HDR/SDR, fps, bitrate).
        // Merge exact duplicate URLs only; height-only deduplication used to hide valid choices.
        val byUrl = LinkedHashMap<String, Format>()
        for (format in collected) {
            val existing = byUrl[format.url]
            if (existing == null) {
                byUrl[format.url] = format
            } else {
                val existingScore = listOf(existing.codec.isNotBlank(), existing.dynamicRange.isNotBlank(), existing.frameRate > 0.0, existing.bandwidth > 0L, existing.bytes > 0L).count { it }
                val newScore = listOf(format.codec.isNotBlank(), format.dynamicRange.isNotBlank(), format.frameRate > 0.0, format.bandwidth > 0L, format.bytes > 0L).count { it }
                if (newScore > existingScore || (newScore == existingScore && format.bytes > existing.bytes)) byUrl[format.url] = format
            }
        }
        val sorted = byUrl.values.sortedWith(
            compareByDescending<Format> { qualityRank(it.label) }
                .thenByDescending { it.frameRate }
                .thenByDescending { it.bandwidth }
                .thenByDescending { it.bytes }
                .thenBy { it.codec }
                .thenBy { it.url }
        )
        return ResolveOutcome(sorted, candidates.size, groups.size, emptySources, rejectedByContentFilter, failures, filterDetails)
    }

    private fun scheduleBackgroundResolve(t: Tab) {
        val key = resolutionKey(t)
        if (t.primaryVideoId.isNullOrBlank() || t.media.isEmpty()) {
            t.resolvedFormats = null
            t.resolvedKey = ""
            t.resolvingKey = ""
            return
        }
        if (key == t.resolvedKey || key == t.resolvingKey) return

        val videoId = t.primaryVideoId
        val epoch = t.primarySourceEpoch
        val blockedAsAd = t.primaryIsAd || t.suspectedPreroll
        val expectedDuration = t.expectedDurationSec.takeIf { it > 12.0 }
            ?: t.playerVideoDurationSec.takeIf { it > 12.0 }
            ?: 0.0
        val currentSrc = t.primaryCurrentSrc
        val sourcePool = if (blockedAsAd) {
            // Only explicit player-declared content renditions are eligible during an ad.
            // Network captures and performance manifests are intentionally withheld.
            t.media.values.filter { item ->
                item.origin == "dom-preroll" &&
                    item.captureSource in setOf("player-flashvars", "player-xhamster")
            }
        } else t.media.values.toList()
        val items = sourcePool.map { item ->
            if (blockedAsAd && item.origin == "dom") {
                MediaItem(item.url, item.type, "dom-preroll", item.videoId, item.sourceEpoch,
                    item.qualityHint, item.widthHint, item.heightHint, item.mimeHint,
                    item.requestHeaders, item.captureSource, item.codecHint, item.frameRateHint,
                    item.dynamicRangeHint, item.bandwidthHint, item.representationIdHint)
            } else item
        }.sortedWith(
            compareBy<MediaItem> { when (it.captureSource) {
                "performance-manifest" -> 0
                "webview" -> 1
                "fetch", "xhr", "service-worker" -> 2
                "player-flashvars" -> 3
                "player-xhamster" -> 3
                else -> 4
            } }
                .thenBy { if (it.type == "hls") 0 else 1 }
                .thenBy { if (it.origin == "dom" || it.origin == "dom-preroll") 0 else 1 }
        ).take(48)
        val headers = headersFor(t)
        t.lastResolveCandidateCount = items.size
        t.lastResolveReport = "بدأ تحليل ${items.size} مصدرًا..."
        t.resolvingKey = key
        io.execute {
            val outcome = try {
                resolveQualityGroups(items, headers, expectedDuration, blockedAsAd, currentSrc)
            } catch (e: Exception) {
                ResolveOutcome(emptyList(), items.size, 0, 0, 0, listOf(shortResolveError(e)))
            }
            ui.post {
                val stillCurrent = tabs.any { it === t } && t.primaryVideoId == videoId &&
                    t.primarySourceEpoch == epoch && resolutionKey(t) == key
                val combinedFormats = mergeFormatLists(outcome.formats, t.ytdlpFormats.orEmpty())
                if (stillCurrent) {
                    t.resolvedFormats = combinedFormats
                    t.resolvedKey = key
                    t.lastResolveReport = outcome.summary() + if (t.ytdlpReport.isNotBlank()) "\n" + t.ytdlpReport else ""
                }
                if (t.resolvingKey == key) t.resolvingKey = ""
                if (cur === t) {
                    updateFab()
                    if (stillCurrent && t.pendingFormatSheet) {
                        t.pendingFormatSheet = false
                        if (combinedFormats.isNotEmpty()) {
                            showFormatSheet(combinedFormats, headersFor(t), if (t.title.isBlank()) Util.host(t.url) else t.title, t.poster)
                        } else {
                            showDiscoveryDiagnostics(t, "اكتمل تحليل المصادر، لكن لم ينتج خيار تنزيل صالحًا.")
                        }
                    }
                } else if (!stillCurrent || !tabs.any { it === t }) {
                    t.pendingFormatSheet = false
                }
            }
        }
    }

    private fun addPreparedSource(t: Tab, url: String, videoId: String, meta: JSONObject? = null) {
        if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) return
        if (!isCandidatePlayerSource(url)) return
        val type = if (isHlsUrl(url) || meta?.optString("mime", "")?.contains("mpegurl", true) == true) "hls" else "direct"
        val origin = if (t.primaryIsAd || t.suspectedPreroll) "dom-preroll" else "dom"
        // Never carry metadata or request headers from an older source generation (for
        // example a preroll ad) into a newly selected content rendition that reused a URL.
        val previous = t.preparedMedia[url]?.takeIf {
            it.videoId == videoId && it.sourceEpoch == t.primarySourceEpoch
        }
        val quality = meta?.optString("label")?.takeIf { it.isNotBlank() } ?: previous?.qualityHint.orEmpty()
        val width = meta?.optInt("width", 0)?.takeIf { it > 0 } ?: previous?.widthHint ?: 0
        val height = meta?.optInt("height", 0)?.takeIf { it > 0 } ?: previous?.heightHint ?: 0
        val mime = meta?.optString("mime")?.takeIf { it.isNotBlank() } ?: previous?.mimeHint.orEmpty()
        val codec = meta?.optString("codec")?.takeIf { it.isNotBlank() } ?: previous?.codecHint.orEmpty()
        val frameRate = meta?.optDouble("frameRate", 0.0)?.takeIf { it > 0.0 } ?: previous?.frameRateHint ?: 0.0
        val dynamicRange = meta?.optString("dynamicRange")?.takeIf { it.isNotBlank() } ?: previous?.dynamicRangeHint.orEmpty()
        val bandwidth = meta?.optLong("bandwidth", 0L)?.takeIf { it > 0L } ?: previous?.bandwidthHint ?: 0L
        val representationId = meta?.optString("representationId")?.takeIf { it.isNotBlank() } ?: previous?.representationIdHint.orEmpty()
        val requestHeaderObject = meta?.optJSONObject("requestHeaders")
        val requestHeaderMap = LinkedHashMap<String, String>()
        if (requestHeaderObject != null) {
            val keys = requestHeaderObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                requestHeaderMap[key] = requestHeaderObject.optString(key)
            }
        }
        val safeRequestHeaders = CaptureHeaders.safeRequestHeaders(requestHeaderMap)
        val captureSource = meta?.optString("captureSource")?.takeIf { it.isNotBlank() }
            ?: previous?.captureSource.orEmpty()
        t.preparedMedia[url] = MediaItem(
            url, type, origin, videoId, t.primarySourceEpoch,
            quality, width, height, mime,
            CaptureHeaders.merge(previous?.requestHeaders.orEmpty(), safeRequestHeaders),
            captureSource, codec, frameRate, dynamicRange, bandwidth, representationId
        )
    }

    private fun promotePageRequests(t: Tab, id: String, epoch: Int, isAd: Boolean) {
        val now = System.currentTimeMillis()
        t.pageRequests.entries.removeAll { now - it.value !in 0L..90_000L }
        t.pageRequests.forEach { (url, time) ->
            if (now - time <= 90_000L && isCandidateMediaRequest(url)) {
                if (isAd) {
                    t.pendingSniffed[url] = time
                    t.requestContexts[url]?.let { t.requestContexts[url] = it.copy(capturedDuringAd = true) }
                } else t.sniffed[url] = SniffedMedia(url, time, id, epoch, false, t.requestContexts[url])
            }
        }
        t.pageRequests.clear()
    }

    private fun promotePendingSniffed(t: Tab, id: String, epoch: Int) {
        val now = System.currentTimeMillis()
        t.pendingSniffed.entries.removeAll { now - it.value !in 0L..180_000L }
        t.pendingSniffed.forEach { (url, time) ->
            // لا نسقط روابط MP4/المصادر المنفصلة التي جهزها المشغل قبل انتهاء الإعلان.
            // يتم التحقق من مدة الملف المباشر ونوعه لاحقًا في Formats.resolve قبل عرضه.
            if (now - time <= 180_000L && isCandidateMediaRequest(url) && !isExcludedMediaUrl(url)) {
                t.requestContexts[url]?.let { t.requestContexts[url] = it.copy(capturedDuringAd = true) }
                t.sniffed[url] = SniffedMedia(url, time, id, epoch, true, t.requestContexts[url])
            }
        }
        t.pendingSniffed.clear()
        while (t.sniffed.size > 80) {
            val oldest = t.sniffed.keys.firstOrNull() ?: break
            t.sniffed.remove(oldest)
        }
    }

    private fun handleVideoEvent(
        t: Tab, id: String, sourceKey: String, isAd: Boolean, event: String,
        duration: Double, sourceEpoch: Int, cycle: Int, sourcesJson: String, adContainer: Boolean,
        sourceMetaJson: String
    ) {
        ui.post {
            if (tabs.none { it === t }) return@post
            // لا ننسب أحداث عناصر المعاينة أو الإعلان إلى الفيديو الرئيسي قبل أن يحدده فحص DOM.
            if (t.primaryVideoId == null || t.primaryVideoId != id) return@post

            val generationChanged = (t.primarySourceKey.isNotEmpty() && t.primarySourceKey != sourceKey) ||
                t.primarySourceEpoch != sourceEpoch || t.primaryCycle != cycle
            val wasAd = t.primaryIsAd
            val wasPreroll = t.suspectedPreroll
            val nowAd = isAd || adContainer
            if (generationChanged) {
                t.sniffed.entries.removeAll { it.value.videoId == id && it.value.sourceEpoch != sourceEpoch }
                if (!wasAd && !nowAd && !wasPreroll) {
                    t.preparedMedia.entries.removeAll { it.value.videoId == id && it.value.sourceEpoch != sourceEpoch }
                }
            }
            t.primarySourceKey = sourceKey
            // sourceKey is src + delimiter + discovered URLs; use source from latest scan when available.
            t.primarySourceEpoch = sourceEpoch
            t.primaryCycle = cycle
            t.primaryIsAd = nowAd
            if (event == "content-transition") t.suspectedPreroll = false
            // لا تسجل مدة قصيرة جدًا للإعلان/المعاينة باعتبارها مدة المحتوى.
            if (!nowAd && !t.suspectedPreroll && duration.isFinite() && duration > 12.0) {
                t.expectedDurationSec = duration
            }
            // احتفظ بمصادر المحتوى المسبقة التي عثرنا عليها في إعدادات المشغل أثناء الإعلان.
            // أضف المصادر الجديدة التي استخرجها DOM؛ addPreparedSource يستبعد روابط الإعلان الصريحة.
            if (sourcesJson.isNotBlank()) {
                try {
                    val arr = org.json.JSONArray(sourcesJson)
                    val metaByUrl = HashMap<String, JSONObject>()
                    if (sourceMetaJson.isNotBlank()) {
                        val metaArr = org.json.JSONArray(sourceMetaJson)
                        for (mi in 0 until metaArr.length()) {
                            val meta = metaArr.optJSONObject(mi) ?: continue
                            val metaUrl = meta.optString("url")
                            if (metaUrl.isNotBlank()) metaByUrl[metaUrl] = meta
                        }
                    }
                    for (i in 0 until arr.length()) {
                        val sourceUrl = arr.optString(i)
                        addPreparedSource(t, sourceUrl, id, metaByUrl[sourceUrl])
                    }
                } catch (_: Exception) {
                }
            }
            if ((wasAd || wasPreroll) && !nowAd && !t.suspectedPreroll) promotePendingSniffed(t, id, sourceEpoch)
            if (t.primaryIsAd || t.suspectedPreroll || event == "pause" || event == "ended" || event == "emptied") {
                t.activePlayback = false
                t.activeId = null
            }
            syncMedia(t)
            if (cur === t) updateFab()
        }
    }

    private fun rec(t: Tab, request: CapturedMediaRequest) {
        val url = request.url
        if (request.method != "GET" || !isCandidatePlayerSource(url)) return
        val knownPreparedSource = t.preparedMedia.containsKey(url) || t.media.containsKey(url)
        val mediaCandidate = isCandidateMediaRequest(url)
        val now = request.capturedAt
        // Keep only recent, allowlisted request context in memory. Cookies are resolved
        // from CookieManager later; Authorization/Cookie headers are never copied here.
        // Keep opaque player URLs as context-only until the matching mediaDefinitions
        // entry arrives; do not promote unrelated API endpoints to downloadable media.
        val safeContext = request.copy(capturedDuringAd = t.primaryIsAd || t.suspectedPreroll)
        t.requestContexts.remove(url)
        t.requestContexts[url] = safeContext
        t.requestContexts.entries.removeAll { now - it.value.capturedAt !in 0L..180_000L }
        while (t.requestContexts.size > 120) {
            val oldest = t.requestContexts.keys.firstOrNull() ?: break
            t.requestContexts.remove(oldest)
        }
        if (!mediaCandidate && !knownPreparedSource) return

        // قد تُطلب قائمة الجودة قبل أن يكتمل أول فحص DOM؛ احتفظ بمرشحات قصيرة العمر
        // حتى نحدد عنصر الفيديو الرئيسي ثم نربطها به بدل إسقاطها.
        val id = t.primaryVideoId
        if (id == null) {
            t.pageRequests[url] = now
            t.pageRequests.entries.removeAll { now - it.value !in 0L..90_000L }
            while (t.pageRequests.size > 80) {
                val oldest = t.pageRequests.keys.firstOrNull() ?: break
                t.pageRequests.remove(oldest)
            }
            return
        }
        // تُجهّز مصادر مشغل الفيديو الرئيسي منذ تحميل صفحة المشاهدة، حتى قبل ضغط Play.
        // لا يغيّر ذلك لون الزر؛ فالتفعيل الأحمر ما زال يتطلب تشغيلًا فعليًا بدأه المستخدم.
        if (t.primaryIsAd || t.suspectedPreroll) {
            t.pendingSniffed[url] = now
            t.pendingSniffed.entries.removeAll { now - it.value !in 0L..180_000L }
            while (t.pendingSniffed.size > 100) {
                val oldest = t.pendingSniffed.keys.firstOrNull() ?: break
                t.pendingSniffed.remove(oldest)
            }
            return
        }
        t.sniffed[url] = SniffedMedia(url, now, id, t.primarySourceEpoch, false, safeContext)
        while (t.sniffed.size > 80) {
            val oldest = t.sniffed.keys.firstOrNull() ?: break
            t.sniffed.remove(oldest)
        }
        syncMedia(t)
    }

    private fun scan(t: Tab) {
        if (t.pending) return
        val now = System.currentTimeMillis()
        t.frameApiPayloads.entries.removeAll { entry ->
            try { now - JSONObject(entry.value).optLong("at", 0L) !in 0L..180_000L }
            catch (_: Exception) { true }
        }
        val pendingFramePayloads = LinkedHashMap(t.frameApiPayloads.filterValues { encoded ->
            if (t.primaryFrameOrigin.isBlank()) true
            else try { JSONObject(encoded).optString("frameOrigin") == t.primaryFrameOrigin } catch (_: Exception) { false }
        })
        val nativeQueue = JSONArray()
        pendingFramePayloads.values.forEach { encoded ->
            try { nativeQueue.put(JSONObject(encoded)) } catch (_: Exception) {}
        }
        // Keep cross-origin iframe payloads in a lexical argument; never expose them as a page global.
        val script = "(function(__vbNativeApiPayloads){return $SCAN_JS;})($nativeQueue)"
        t.web.evaluateJavascript(script) { r ->
            try {
                if (r != null && r != "null") {
                    val s = JSONTokener(r).nextValue() as? String
                    if (s != null) {
                        onScan(t, JSONObject(s))
                        // Do not parse the same native payload on every polling cycle.
                        pendingFramePayloads.forEach { (key, value) ->
                            if (t.frameApiPayloads[key] == value) t.frameApiPayloads.remove(key)
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun onScan(t: Tab, o: JSONObject) {
        val now = System.currentTimeMillis()
        val p = o.optJSONObject("p")
        if (p == null) {
            // Some sites host the entire player in a cross-origin iframe, leaving no VIDEO
            // element in the top document. Use only early API/manifest candidates in that case.
            val orphanSources = o.optJSONArray("o")
            if (orphanSources != null && orphanSources.length() > 0 && t.primaryVideoId?.startsWith("frame-") == true) {
                // API/manifests captured inside the selected iframe belong to its already identified player.
                val id = t.primaryVideoId!!
                val metadataArray = o.optJSONArray("om")
                val metadata = HashMap<String, JSONObject>()
                if (metadataArray != null) for (i in 0 until metadataArray.length()) {
                    val item = metadataArray.optJSONObject(i) ?: continue
                    val url = item.optString("url")
                    if (url.isNotBlank()) metadata[url] = item
                }
                for (i in 0 until orphanSources.length()) {
                    val url = orphanSources.optString(i)
                    addPreparedSource(t, url, id, metadata[url])
                }
                syncMedia(t)
            } else if (orphanSources != null && orphanSources.length() > 0 &&
                (t.primaryVideoId == null || t.primaryVideoId?.startsWith("api-") == true)) {
                val id = "api-" + Integer.toHexString(t.url.substringBefore('#').hashCode())
                val urls = ArrayList<String>(orphanSources.length())
                for (i in 0 until orphanSources.length()) {
                    val url = orphanSources.optString(i)
                    if (url.startsWith("http://", true) || url.startsWith("https://", true)) urls.add(url)
                }
                if (urls.isNotEmpty()) {
                    val oldUrls = t.preparedMedia.keys.toSet()
                    val newUrls = urls.toSet()
                    val isNewSyntheticVideo = t.primaryVideoId != id
                    val isDisjointSourceSet = !isNewSyntheticVideo && oldUrls.isNotEmpty() &&
                        newUrls.isNotEmpty() && oldUrls.intersect(newUrls).isEmpty()
                    if (isNewSyntheticVideo || isDisjointSourceSet) {
                        t.media.clear()
                        t.preparedMedia.clear()
                        t.sniffed.clear()
                        t.primarySourceEpoch++
                        t.userGestureVideoIds.clear()
                    }
                    t.primaryVideoId = id
                    t.syntheticApiUpdatedAt = System.currentTimeMillis()
                    t.primarySourceKey = urls.distinct().sorted().joinToString("||")
                    t.primaryCurrentSrc = ""
                    t.primaryCycle = 0
                    t.primaryIsAd = false
                    t.suspectedPreroll = false
                    t.expectedDurationSec = 0.0
                    t.poster = ""
                    t.activePlayback = false
                    t.activeId = null

                    val metadata = HashMap<String, JSONObject>()
                    val metadataArray = o.optJSONArray("om")
                    if (metadataArray != null) {
                        for (i in 0 until metadataArray.length()) {
                            val item = metadataArray.optJSONObject(i) ?: continue
                            val url = item.optString("url")
                            if (url.isNotBlank()) metadata[url] = item
                        }
                    }
                    for (url in urls.distinct()) addPreparedSource(t, url, id, metadata[url])
                    if (isNewSyntheticVideo || isDisjointSourceSet) promotePageRequests(t, id, t.primarySourceEpoch, false)
                    syncMedia(t)
                }
            } else if (t.primaryVideoId?.startsWith("api-") == true) {
                // Keep successfully parsed iframe sources for the same 3-minute capture window,
                // even though the one-shot native payload queue has already been consumed.
                val stillFresh = t.preparedMedia.isNotEmpty() &&
                    System.currentTimeMillis() - t.syntheticApiUpdatedAt in 0L..180_000L
                if (!stillFresh) {
                    t.media.clear()
                    t.preparedMedia.clear()
                    t.sniffed.clear()
                    t.primaryVideoId = null
                    t.primarySourceKey = ""
                    t.primaryCurrentSrc = ""
                    t.primaryIsAd = false
                    t.suspectedPreroll = false
                    t.expectedDurationSec = 0.0
                    t.syntheticApiUpdatedAt = 0L
                    t.resolvedFormats = null
                    t.resolvedKey = ""
                    t.resolvingKey = ""
                }
            }
            val frameId = t.primaryVideoId
            val frameStateFresh = frameId != null &&
                t.framePlaybackActiveId == frameId && now - t.framePlaybackAt in 0L..5_000L &&
                !t.primaryIsAd && !t.suspectedPreroll
            if (!frameStateFresh) {
                t.activePlayback = false
                t.activeId = null
                t.framePlaybackActiveId = null
            } else {
                t.activePlayback = true
                t.activeId = frameId
            }
            // Keep an authenticated frame's recent playback state when the top document has no VIDEO.
            if (cur === t) updateFab()
            return
        }

        val id = p.optString("id")
        if (id.isBlank()) return
        val key = p.optString("sourceKey")
        val epoch = p.optInt("sourceEpoch", 0)
        val cycle = p.optInt("cycle", 0)
        val isAd = p.optBoolean("ad", false)
        val adContainer = p.optBoolean("adContainer", false)
        val sourceChanged = t.primaryVideoId != id || t.primarySourceKey != key ||
            t.primarySourceEpoch != epoch || t.primaryCycle != cycle
        val wasAd = t.primaryIsAd
        val wasPreroll = t.suspectedPreroll
        val nowAd = isAd || adContainer
        val sourcesForContent = p.optJSONArray("sources")
        val metadataForContent = p.optJSONArray("sourceMeta")
        val suspectedPreroll = p.optBoolean("suspectedPreroll", false) && !nowAd

        val primaryChanged = t.primaryVideoId != id
        if (primaryChanged) {
            t.pendingFormatSheet = false
            t.syntheticApiUpdatedAt = 0L
            t.primaryFrameOrigin = ""
            t.primaryFrameId = ""
            t.primaryFrameSeenAt = 0L
            t.media.clear()
            t.preparedMedia.clear()
            t.sniffed.clear()
            t.requestContexts.entries.removeAll { System.currentTimeMillis() - it.value.capturedAt > 180_000L }
            t.pendingSniffed.clear()
            t.userGestureVideoIds.removeAll { it != id }
        } else if (sourceChanged) {
            t.sniffed.entries.removeAll { it.value.videoId == id && it.value.sourceEpoch != epoch }
            if (!wasAd && !nowAd && !wasPreroll && !suspectedPreroll) {
                t.preparedMedia.entries.removeAll { it.value.videoId == id && it.value.sourceEpoch != epoch }
            }
        }

        t.primaryVideoId = id
        t.primarySourceKey = key
        t.primarySourceEpoch = epoch
        t.primaryCycle = cycle
        t.primaryIsAd = nowAd
        t.suspectedPreroll = suspectedPreroll
        if (primaryChanged) promotePageRequests(t, id, epoch, nowAd || suspectedPreroll)
        val src = p.optString("src")
        t.primaryCurrentSrc = src
        t.sawBlob = p.optBoolean("blob", src.startsWith("blob:", true))
        t.poster = p.optString("poster")
        val duration = p.optDouble("duration", 0.0)
        t.playerConfigObjectCount = maxOf(t.playerConfigObjectCount, p.optInt("playerConfigObjectCount", 0).coerceIn(0, 12))
        t.playerMediaDefinitionCount = maxOf(t.playerMediaDefinitionCount, p.optInt("playerMediaDefinitionCount", 0).coerceIn(0, 64))
        val pageQualities = p.optJSONArray("playerDefinitionQualities")
        if (pageQualities != null) {
            val q = (0 until minOf(pageQualities.length(), 16)).map { pageQualities.optString(it).take(12) }.filter { it.isNotBlank() }.distinct()
            if (q.isNotEmpty()) t.playerDefinitionQualities = q.joinToString(", ")
        }
        t.xhamsterStateFound = t.xhamsterStateFound || p.optBoolean("xhamsterStateFound", false)
        t.xhamsterSourceCount = maxOf(t.xhamsterSourceCount, p.optInt("xhamsterSourceCount", 0).coerceIn(0, 64))
        t.xhamsterOpaqueCount = maxOf(t.xhamsterOpaqueCount, p.optInt("xhamsterOpaqueCount", 0).coerceIn(0, 128))
        val xhQualities = p.optJSONArray("xhamsterQualities")
        if (xhQualities != null) {
            val values = (0 until minOf(xhQualities.length(), 24)).map { xhQualities.optString(it).take(16) }.filter { it.isNotBlank() }.distinct()
            if (values.isNotEmpty()) t.xhamsterQualities = values.joinToString(", ")
        }
        t.playerVideoDurationSec = p.optDouble("playerVideoDuration", 0.0).takeIf { it.isFinite() && it in 0.0..86400.0 } ?: 0.0
        t.playerAdReason = p.optString("adReason").take(80)
        // مدة عنصر الفيديو القصيرة وغير المصنفة قد تكون مدة إعلان؛ لا نعتمدها كمدة الفيلم.
        if (!nowAd && !suspectedPreroll && duration.isFinite() && duration > 12.0) {
            t.expectedDurationSec = duration
        }
        if ((wasAd || wasPreroll) && !nowAd && !suspectedPreroll) promotePendingSniffed(t, id, epoch)

        // تجهّز المصادر من DOM وإعدادات المشغل قبل بدء التشغيل، وتبقى محفوظة أثناء الإعلان.
        // JavaScript يستبعد currentSrc عند ظهور علامات الإعلان، بينما يستخرج مصادر المحتوى من config.
        val arr = sourcesForContent
        if (arr != null) {
            val metaByUrl = HashMap<String, JSONObject>()
            if (metadataForContent != null) {
                for (mi in 0 until metadataForContent.length()) {
                    val meta = metadataForContent.optJSONObject(mi) ?: continue
                    val metaUrl = meta.optString("url")
                    if (metaUrl.isNotBlank()) metaByUrl[metaUrl] = meta
                }
            }
            for (i in 0 until arr.length()) {
                val sourceUrl = arr.optString(i)
                addPreparedSource(t, sourceUrl, id, metaByUrl[sourceUrl])
            }
        }

        val a = o.optJSONObject("a")
        val gestureMatches = id in t.userGestureVideoIds
        val pagePlayback = a != null && a.optString("id") == id && gestureMatches
        val framePlayback = FramePlaybackPolicy.shouldUseFrameFallback(
            matchesVideo = t.framePlaybackActiveId == id,
            fresh = System.currentTimeMillis() - t.framePlaybackAt in 0L..5_000L,
            playing = t.framePlaybackActiveId == id,
            userGesture = gestureMatches,
            isAd = t.primaryIsAd,
            suspectedPreroll = t.suspectedPreroll
        )
        t.activePlayback = (pagePlayback || framePlayback) && !t.primaryIsAd && !t.suspectedPreroll
        t.activeId = if (t.activePlayback) id else null
        syncMedia(t)
        if (cur === t) updateFab()
    }

    private fun headersFor(t: Tab, w: WebView = t.web): Map<String, String> {
        val m = LinkedHashMap<String, String>()
        m["User-Agent"] = w.settings.userAgentString
        if (t.url.startsWith("http")) {
            m["Referer"] = t.url
            try {
                val u = URL(t.url)
                m["Origin"] = u.protocol + "://" + u.host
            } catch (_: Exception) {
            }
        }
        return CaptureHeaders.safeRequestHeaders(m)
    }

    private fun showDiscoveryDiagnostics(t: Tab, heading: String) {
        val kinds = t.media.values.groupingBy { it.captureSource.ifBlank { it.origin } }.eachCount()
        val probeSnapshots = t.frameProbeStates.values.mapNotNull { encoded ->
            try { JSONObject(encoded) } catch (_: Exception) { null }
        }
        val mainFrameProbes = probeSnapshots.count { it.optBoolean("isTop", false) }
        val childFrameProbes = probeSnapshots.size - mainFrameProbes
        val probeProtocols = probeSnapshots.map { it.optString("protocol") }.filter { it.isNotBlank() }.distinct()
        val message = buildString {
            append(heading).append("\n\n")
            append("هوية الفيديو الرئيسي: ").append(if (t.primaryVideoId.isNullOrBlank()) "غير محددة" else "محددة").append("\n")
            append("مصادر DOM/API المحضّرة: ").append(t.preparedMedia.size).append("\n")
            append("مصادر الشبكة المرتبطة: ").append(t.sniffed.size).append("\n")
            append("طلبات تنتظر ربطها بالفيديو: ").append(t.pageRequests.size).append("\n")
            append("سياقات طلبات محفوظة: ").append(t.requestContexts.size).append("\n")
            append("سياقات مرتبطة بروابط المشغل: ").append(t.preparedMedia.count { (url, _) ->
                t.requestContexts[url]?.let { CaptureHeaders.matchesPreparedSource(url, it, t.primaryFrameOrigin) } == true
            }).append("\n")
            append("رسائل API من الإطارات: ").append(t.frameApiPayloads.size).append("\n")
            append("نبضات تشغيل السكربت: ").append(t.frameProbeStates.size)
                .append(" (الصفحة الرئيسية ").append(mainFrameProbes)
                .append(" / الإطارات الفرعية ").append(childFrameProbes)
                .append(" / إجمالي الرسائل ").append(t.frameProbeMessageCount).append(")\n")
            if (probeProtocols.isNotEmpty()) append("بروتوكولات الإطارات المرصودة: ").append(probeProtocols.joinToString()).append("\n")
            append("إطارات أرسلت حالة فيديو: ").append(t.frameVideoStates.size)
                .append(" (إجمالي الرسائل ").append(t.frameVideoMessageCount).append(")\n")
            append("عناصر فيديو مرصودة: DOM=").append(t.frameReportedLightVideoCount)
                .append(" / Shadow DOM=").append(t.frameReportedShadowVideoCount).append("\n")
            append("إعدادات مشغل مرصودة: ").append(t.playerConfigObjectCount)
                .append(" / mediaDefinitions: ").append(t.playerMediaDefinitionCount)
            if (t.playerDefinitionQualities.isNotBlank()) append(" / الجودات: ").append(t.playerDefinitionQualities)
            if (t.playerVideoDurationSec > 0.0) append(" / مدة المحتوى المعلنة: ").append(String.format(java.util.Locale.US, "%.0f ثانية", t.playerVideoDurationSec))
            if (t.playerAdReason.isNotBlank()) append(" / إشارة الإعلان: ").append(t.playerAdReason)
            append("\nمصادر xHamster: ").append(if (t.xhamsterStateFound) "initials متاحة" else "initials غير مرصودة")
                .append(" / روابط مباشرة: ").append(t.xhamsterSourceCount)
                .append(" / قيم غير قابلة للقراءة: ").append(t.xhamsterOpaqueCount)
            if (t.xhamsterQualities.isNotBlank()) append(" / الجودات: ").append(t.xhamsterQualities)
            append("\n")
            append("جسر WebMessage Listener: ").append(if (t.frameBridgeRegistered) "مسجل" else "لم يسجل")
            if (t.frameBridgeError.isNotBlank()) append(" — ").append(t.frameBridgeError)
            append("\n")
            append("إطار الفيديو الرئيسي: ").append(if (t.primaryFrameOrigin.isBlank()) "غير محدد" else Uri.parse(t.primaryFrameOrigin).host ?: "محدد").append("\n")
            append("Document Start Script: ").append(if (t.documentStartCaptureInstalled) "ثبتت واجهة التسجيل" else "غير متاح/لم يثبت").append("\n")
            append("Web Message Listener: ").append(if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) "مدعوم" else "غير مدعوم").append("\n")
            append("مصادر Resolver الحالية: ").append(t.media.size).append("\n")
            append("مصادر ضمن آخر محاولة تحليل: ").append(t.lastResolveCandidateCount).append("\n")
            append("مصادر الالتقاط: ").append(if (kinds.isEmpty()) "لا توجد" else kinds.entries.joinToString { "${it.key}=${it.value}" }).append("\n")
            append("قوائم HLS/DASH المرصودة من تحميل المشغل: ").append(t.preparedMedia.values.count { it.captureSource == "performance-manifest" }).append("\n")
            append("روابط موقعة منتهية حسب validto/expires: ").append(t.preparedMedia.keys.count { MediaUrlExpiry.isExpired(it) }).append("\n")
            append("محرك yt-dlp: ").append(YtDlpExtractor.status(this@MainActivity)).append("\n")
            append("استخراج صفحة الموقع: ").append(t.ytdlpReport).append("\n")
            if (t.lastResolveReport.isNotBlank()) append("\nتقرير تحليل WebView/Manifest:\n").append(t.lastResolveReport)
            append("\n\nالتشخيص لا يعرض Cookies أو Authorization أو قيم الروابط الموقعة.")
        }
        AlertDialog.Builder(this)
            .setTitle("تشخيص اكتشاف الصيغ")
            .setMessage(message)
            .setPositiveButton("نسخ التشخيص") { _, _ ->
                (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("VideoBrowser diagnostics", message))
                toast("تم نسخ التشخيص؛ أرسله هنا")
            }
            .setNegativeButton("إغلاق", null)
            .show()
    }

    private fun pageExtractionKey(rawUrl: String): String = rawUrl.substringBefore('#').trim()

    private fun shouldUseYtDlpPageExtractor(rawUrl: String): Boolean {
        if (!rawUrl.startsWith("http://", true) && !rawUrl.startsWith("https://", true)) return false
        val uri = try { Uri.parse(rawUrl) } catch (_: Exception) { return false }
        val host = uri.host.orEmpty().lowercase()
        val path = uri.path.orEmpty().lowercase()
        val query = uri.query.orEmpty().lowercase()
        fun isDomain(domain: String) = host == domain || host.endsWith(".$domain")
        val targetHost = isDomain("xhamster.com") || isDomain("xhamster.one") ||
            isDomain("xhamster.desi") || isDomain("xhms.pro") ||
            isDomain("xhday.com") || isDomain("xhvid.com") ||
            Regex("^xhamster\\d+\\.(?:com|desi)$").matches(host) ||
            isDomain("pornhub.com") || isDomain("pornhubpremium.com") ||
            isDomain("pornhub.net") || isDomain("pornhub.org") || isDomain("thumbzilla.com")
        val videoPath = Regex("(?:^|/)(?:watch|video|videos|movie|movies|embed|shorts|reel|clip)(?:/|$)")
            .containsMatchIn(path)
        val videoQuery = query.contains("viewkey=") || query.contains("video_id=") ||
            query.contains("video=") || query.contains("idvideo=")
        // Keep page-first extraction scoped to the services under active investigation.
        // Generic sites continue to use the existing WebView/player capture until a
        // dedicated site extractor has been validated for them.
        return targetHost && (path.endsWith("view_video.php") || videoPath || videoQuery)
    }

    private fun mergeFormatLists(primary: List<Format>, secondary: List<Format>): List<Format> {
        val byUrl = LinkedHashMap<String, Format>()
        for (format in primary + secondary) {
            if (format.url.isBlank()) continue
            val old = byUrl[format.url]
            if (old == null) {
                byUrl[format.url] = format
            } else {
                val oldScore = listOf(old.codec.isNotBlank(), old.frameRate > 0.0, old.bandwidth > 0L,
                    old.bytes > 0L, old.requestHeaders.isNotEmpty()).count { it }
                val newScore = listOf(format.codec.isNotBlank(), format.frameRate > 0.0, format.bandwidth > 0L,
                    format.bytes > 0L, format.requestHeaders.isNotEmpty()).count { it }
                if (newScore > oldScore || (newScore == oldScore && format.bytes > old.bytes)) byUrl[format.url] = format
            }
        }
        return byUrl.values.sortedWith(
            compareByDescending<Format> { qualityRank(it.label) }
                .thenByDescending { it.frameRate }
                .thenByDescending { it.bandwidth }
                .thenByDescending { it.bytes }
                .thenBy { it.codec }
                .thenBy { it.url }
        )
    }

    private fun startYtDlpPreflight(t: Tab, rawUrl: String) {
        val pageUrl = pageExtractionKey(rawUrl)
        if (!shouldUseYtDlpPageExtractor(pageUrl)) return
        if (t.ytdlpPageUrl == pageUrl && !t.ytdlpFormats.isNullOrEmpty()) return
        if (t.ytdlpLoadingUrl == pageUrl || t.ytdlpAttemptedUrl == pageUrl) return

        t.ytdlpLoadingUrl = pageUrl
        val generation = ++t.ytdlpGeneration
        t.ytdlpReport = "يحلل yt-dlp صفحة الفيديو مباشرة قبل التشغيل..."
        val requestHeaders = LinkedHashMap(headersFor(t))
        YtDlpExtractor.extract(applicationContext, pageUrl, requestHeaders) { result ->
            ui.post {
                if (tabs.none { it === t } || t.ytdlpGeneration != generation || t.ytdlpLoadingUrl != pageUrl) return@post
                if (pageExtractionKey(t.url) != pageUrl) {
                    t.ytdlpLoadingUrl = ""
                    return@post
                }
                t.ytdlpLoadingUrl = ""
                t.ytdlpAttemptedUrl = pageUrl
                t.ytdlpPageUrl = pageUrl
                t.ytdlpFormats = result.formats
                t.ytdlpReport = buildString {
                    append("yt-dlp ").append(result.version.ifBlank { "المضمن" })
                    if (result.extractor.isNotBlank()) append(" / المستخرج: ").append(result.extractor)
                    append(" / الصيغ الصالحة: ").append(result.formats.size)
                    if (result.durationSec > 0.0) append(" / المدة: ").append(result.durationSec.toInt()).append("s")
                    if (result.error.isNotBlank()) append(" / ").append(result.error)
                }
                if (result.formats.isNotEmpty()) {
                    t.resolvedFormats = mergeFormatLists(t.resolvedFormats.orEmpty(), result.formats)
                    if (!t.primaryVideoId.isNullOrBlank()) t.resolvedKey = resolutionKey(t)
                }
                if (cur === t && t.pendingFormatSheet) {
                    val formats = mergeFormatLists(t.resolvedFormats.orEmpty(), result.formats)
                    if (formats.isNotEmpty()) {
                        t.pendingFormatSheet = false
                        showFormatSheet(formats, headersFor(t), if (t.title.isBlank()) Util.host(t.url) else t.title, t.poster)
                    } else if (t.media.isNotEmpty()) {
                        // Keep the pending UI request alive when a WebView resolver job is
                        // already running; its completion will show any formats it finds.
                        val currentKey = resolutionKey(t)
                        if (t.resolvingKey != currentKey) {
                            t.resolvedKey = "" // allow a fresh resolver pass after yt-dlp returned zero
                            scheduleBackgroundResolve(t)
                        }
                    } else {
                        t.pendingFormatSheet = false
                        showDiscoveryDiagnostics(t, "لم ينتج yt-dlp خيار تنزيل صالحًا لهذه الصفحة. ${result.error}")
                    }
                }
            }
        }
    }

    private fun onFabClick() {
        val t = cur ?: return
        val pageUrl = pageExtractionKey(t.web.url ?: t.url)
        val isPreroll = t.primaryIsAd || t.suspectedPreroll
        val title = if (t.title.isBlank()) Util.host(t.url) else t.title
        val ytdlpFormats = if (t.ytdlpPageUrl == pageUrl) t.ytdlpFormats.orEmpty() else emptyList()

        // Page-level yt-dlp results describe the requested video page, not the current
        // ad element. YtDlpExtractor already rejects ad/preview/audio-only/DASH formats.
        if (ytdlpFormats.isNotEmpty()) {
            showFormatSheet(mergeFormatLists(t.resolvedFormats.orEmpty(), ytdlpFormats), headersFor(t), title, t.poster)
            return
        }

        val videoId = t.primaryVideoId
        if (videoId == null || t.media.isEmpty()) {
            if (shouldUseYtDlpPageExtractor(pageUrl)) {
                t.pendingFormatSheet = true
                if (t.ytdlpLoadingUrl == pageUrl) {
                    toast("يستخرج yt-dlp جودات صفحة الفيديو؛ ستظهر القائمة عند اكتمال التحليل")
                } else {
                    // Empty/failed results are retryable. A page refresh must not permanently
                    // cache a zero-format result for the lifetime of this WebView URL.
                    t.ytdlpAttemptedUrl = ""
                    t.resolvedKey = ""
                    startYtDlpPreflight(t, pageUrl)
                    toast("أعيد طلب استخراج جودات الفيديو من صفحة الموقع")
                }
                return
            }
            val message = when {
                isPreroll -> "الإعلان يعمل، لكن إعدادات المشغل لم تكشف بعد صيغ المحتوى المسبقة"
                t.sawBlob -> "مصدر الفيديو يستخدم Blob/MSE ولم تُكشف قائمة المحتوى بعد"
                else -> "لم تُكتشف مصادر الفيديو الرئيسي بعد"
            }
            showDiscoveryDiagnostics(t, message)
            scan(t)
            return
        }

        if (isPreroll && !hasPrerollPlayerRenditions(t)) {
            // Never offer the ad's currentSrc or raw ad/network requests. Ask yt-dlp for
            // the page's content formats, or refresh the player scan if no page extractor applies.
            if (shouldUseYtDlpPageExtractor(pageUrl)) {
                t.pendingFormatSheet = true
                if (t.ytdlpLoadingUrl == pageUrl) {
                    toast("يجري استخراج صيغ محتوى الفيديو أثناء الإعلان")
                } else {
                    t.ytdlpAttemptedUrl = ""
                    t.resolvedKey = ""
                    startYtDlpPreflight(t, pageUrl)
                    toast("يبحث عن جودات الفيديو الأصلية أثناء الإعلان")
                }
            } else {
                scan(t)
                showDiscoveryDiagnostics(t, "لم تظهر صيغ المحتوى الأصلية بعد؛ تم تجاهل مصدر الإعلان. أعد الضغط بعد تحديث بيانات المشغل")
            }
            return
        }

        val key = resolutionKey(t)
        val headers = headersFor(t)
        val poster = t.poster
        val cached = t.resolvedFormats
        if (t.resolvedKey == key && !cached.isNullOrEmpty()) {
            showFormatSheet(mergeFormatLists(cached, ytdlpFormats), headers, title, poster)
            return
        }
        if (t.resolvingKey == key) {
            t.pendingFormatSheet = true
            toast("جارٍ تجهيز جودات الفيديو؛ ستظهر قائمة التحميل تلقائيًا عند اكتمالها")
            return
        }
        if (t.resolvedKey == key && cached.isNullOrEmpty()) {
            // Negative cache is useful for background polling, but an explicit user tap
            // is a request to retry fresh links and not to repeat the same diagnostic.
            t.resolvedKey = ""
        }

        // Retry yt-dlp when its previous page extraction returned zero formats, even if
        // WebView/player sources exist. This is the important post-reload recovery path.
        if (shouldUseYtDlpPageExtractor(pageUrl) && ytdlpFormats.isEmpty() &&
            t.ytdlpLoadingUrl != pageUrl) {
            t.ytdlpAttemptedUrl = ""
            startYtDlpPreflight(t, pageUrl)
        }

        t.pendingFormatSheet = true
        scheduleBackgroundResolve(t)
        toast(if (isPreroll) "يلتقط جودات المحتوى أثناء الإعلان؛ لن تُعرض روابط الإعلان" else "بدأ تجهيز جودات الفيديو؛ ستظهر قائمة التحميل تلقائيًا عند اكتمالها")
    }

    private fun qualityRank(label: String): Int =
        Formats.qualityFromLabel(label) ?: 0

    // ---------------------------------------------------------------- شاشة اختيار الجودة
    private fun pathText(): String {
        val f = Prefs.folder(this)
        return "المسار: " + (if (f == null) "Download/VideoBrowser" else (Uri.parse(f).lastPathSegment ?: f))
    }

    private fun playPreview(f: Format, headers: Map<String, String>, title: String) {
        val m = LinkedHashMap<String, String>(headers)
        try {
            val ck = CookieManager.getInstance().getCookie(f.url)
            if (ck != null && !m.containsKey("Cookie")) m["Cookie"] = ck
        } catch (e: Exception) {
        }
        val h = ArrayList<String>()
        for ((k, v) in m) { h.add(k); h.add(v) }
        startActivity(
            Intent(this, PlayerActivity::class.java)
                .putExtra("uris", arrayOf(f.url))
                .putExtra("names", arrayOf(title))
                .putExtra("headers", h.toTypedArray())
                .putExtra("hls", f.kind == "hls")
                .putExtra("ext", f.ext)
        )
    }

    private fun showFormatSheet(list: List<Format>, headers: Map<String, String>, title: String, poster: String) {
        val dlg = BottomSheetDialog(this)
        val gray = 0xFF78909C.toInt()
        val red = 0xFFE53935.toInt()
        val outer = FrameLayout(this).apply {
            setBackgroundColor(Color.WHITE)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(12))
        }
        outer.addView(root, FrameLayout.LayoutParams(MATCH, WRAP))

        // ---- الرأس: صورة + مدة + الشبكة + الاسم + مشاركة + إغلاق
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val tf = FrameLayout(this).apply {
            background = rounded(0xFF263238.toInt(), 6)
            clipToOutline = true
            outlineProvider = ViewOutlineProvider.BACKGROUND
        }
        val firstKind = Util.kindOf("x." + list[0].ext)
        val thumb = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER
            setImageResource(
                when (firstKind) {
                    "apk" -> R.drawable.ic_apk
                    "audio" -> R.drawable.ic_music
                    "video" -> R.drawable.ic_video
                    else -> R.drawable.ic_file
                }
            )
            setColorFilter(if (firstKind == "apk") 0xFF43A047.toInt() else 0xFF90A4AE.toInt())
        }
        tf.addView(thumb, FrameLayout.LayoutParams(MATCH, MATCH))
        val maxDur = list.maxOfOrNull { it.durSec } ?: 0.0
        if (maxDur > 0) {
            tf.addView(TextView(this).apply {
                text = Util.fmtDur(maxDur)
                textSize = 11f
                setTextColor(Color.WHITE)
                background = rounded(0xAA000000.toInt(), 4)
                setPadding(dp(5), dp(1), dp(5), dp(1))
            }, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.END).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
        }
        head.addView(tf, LinearLayout.LayoutParams(dp(120), dp(86)))
        val infoCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        infoCol.addView(TextView(this).apply {
            text = "الشبكة: " + (if (Net.onWifi(this@MainActivity)) "WiFi" else "بيانات الجوال")
            textSize = 12f
            setTextColor(gray)
        })
        val et = EditText(this).apply {
            setText(title)
            textSize = 15f
            maxLines = 3
            background = null
            setPadding(0, dp(2), 0, dp(2))
            setTextColor(Color.BLACK)
        }
        infoCol.addView(et)
        infoCol.addView(TextView(this).apply {
            text = "مشاركة"
            textSize = 13f
            setTextColor(0xFF455A64.toInt())
            background = rounded(0xFFF1F3F4.toInt(), 18)
            setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_share, 0, 0, 0)
            compoundDrawableTintList = android.content.res.ColorStateList.valueOf(gray)
            compoundDrawablePadding = dp(6)
            setPadding(dp(14), dp(7), dp(14), dp(7))
            setOnClickListener {
                startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, cur?.url ?: list[0].url),
                        "مشاركة"
                    )
                )
            }
        }, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(4) })
        head.addView(infoCol, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = dp(12); marginEnd = dp(26) })
        root.addView(head)
        outer.addView(iconBtn(R.drawable.ic_close, gray, 12).apply { setOnClickListener { dlg.dismiss() } },
            FrameLayout.LayoutParams(dp(40), dp(40), Gravity.TOP or Gravity.END))
        if (poster.startsWith("http")) {
            ThumbLoader.loadUrl(poster, headers) { b ->
                if (b != null) {
                    thumb.clearColorFilter()
                    thumb.scaleType = ImageView.ScaleType.CENTER_CROP
                    thumb.setImageBitmap(b)
                }
            }
        }

        // ---- المسار
        val prow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val pv = TextView(this).apply {
            text = pathText(); textSize = 12f; setTextColor(gray)
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        }
        pathView = pv
        prow.addView(pv, LinearLayout.LayoutParams(0, WRAP, 1f))
        prow.addView(TextView(this).apply {
            text = "تغيير"; textSize = 13f; setTextColor(0xFF1565C0.toInt())
            setPadding(dp(10), dp(8), dp(4), dp(8))
            setOnClickListener { pickFolder.launch(null) }
        })
        root.addView(prow, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(6) })
        root.addView(View(this).apply { setBackgroundColor(0xFFE8EAED.toInt()) }, LinearLayout.LayoutParams(MATCH, dp(1)))

        // ---- القائمة: الصوت ثم الفيديو
        class RowRef(val rb: RadioButton, val lbl: TextView, val size: TextView, val f: Format)
        var selected: Format? = null
        val rowsRef = ArrayList<RowRef>()
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val tint = android.content.res.ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(red, 0xFF9E9E9E.toInt())
        )
        var rows = 0
        var sections = 0
        fun section(icon: Int, text: String) {
            sections++
            val h = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(4), dp(14), dp(4), dp(6))
            }
            h.addView(ImageView(this).apply { setImageResource(icon); setColorFilter(0xFF37474F.toInt()) }, LinearLayout.LayoutParams(dp(24), dp(24)))
            h.addView(TextView(this).apply {
                this.text = text; textSize = 17f; setTextColor(0xFF37474F.toInt())
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = dp(8) })
            box.addView(h)
        }
        var playBtn: TextView? = null
        fun isMedia(f: Format): Boolean {
            val k = Util.kindOf("x." + f.ext)
            return f.kind == "hls" || f.isAudio || k == "video" || k == "audio"
        }
        fun select(f: Format) {
            selected = f
            playBtn?.visibility = if (isMedia(f)) View.VISIBLE else View.GONE
            for (r in rowsRef) {
                val on = r.f === f
                r.rb.isChecked = on
                r.lbl.setTextColor(if (on) red else Color.BLACK)
                r.size.setTextColor(if (on) red else gray)
            }
        }
        fun row(f: Format) {
            rows++
            val rb = RadioButton(this).apply { isClickable = false; isFocusable = false; buttonTintList = tint }
            val lbl = TextView(this).apply { text = f.label; textSize = 16f; setTextColor(Color.BLACK) }
            val detail = TextView(this).apply {
                text = f.details
                textSize = 10f
                setTextColor(gray)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            val labelColumn = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(lbl)
                if (f.details.isNotBlank()) addView(detail)
            }
            val sz = TextView(this).apply { text = f.sizeText; textSize = 14f; setTextColor(gray) }
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(2), dp(4), dp(2), dp(4))
                setBackgroundResource(ripple())
                setOnClickListener { select(f) }
            }
            r.addView(rb)
            r.addView(labelColumn, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = dp(6) })
            if (f.kind == "direct" && !f.isAudio) {
                r.addView(TextView(this).apply { text = f.ext.uppercase(); textSize = 12f; setTextColor(gray) },
                    LinearLayout.LayoutParams(WRAP, WRAP).apply { marginEnd = dp(14) })
            }
            r.addView(sz)
            rowsRef.add(RowRef(rb, lbl, sz, f))
            box.addView(r, LinearLayout.LayoutParams(MATCH, dp(56)))
            box.addView(View(this).apply { setBackgroundColor(0xFFF1F3F4.toInt()) }, LinearLayout.LayoutParams(MATCH, dp(1)))
        }
        val audio = list.filter { it.isAudio || Formats.isAudio(it.ext) }
        val rest = list.filter { !(it.isAudio || Formats.isAudio(it.ext)) }
        val video = rest.filter { it.kind == "hls" || Util.kindOf("x." + it.ext) == "video" }
        val files = rest.filter { !(it.kind == "hls" || Util.kindOf("x." + it.ext) == "video") }
        if (audio.isNotEmpty()) { section(R.drawable.ic_music, "الصوت"); audio.forEach { row(it) } }
        if (video.isNotEmpty()) { section(R.drawable.ic_play_circle, "الفيديو"); video.forEach { row(it) } }
        if (files.isNotEmpty()) {
            val apk = files.any { Util.kindOf("x." + it.ext) == "apk" }
            section(if (apk) R.drawable.ic_apk else R.drawable.ic_file, if (apk) "تطبيق" else "ملف")
            files.forEach { row(it) }
        }
        (video.firstOrNull() ?: audio.firstOrNull() ?: files.firstOrNull())?.let { select(it) }
        val rt0 = refreshTask()
        if (rt0 != null && rt0.total > 0) {
            list.filter { it.kind == rt0.kind && it.bytes > 0 }
                .minByOrNull { Math.abs(it.bytes - rt0.total) }?.let { select(it) }
        }

        val sv = ScrollView(this)
        sv.addView(box)
        val maxH = (resources.displayMetrics.heightPixels * 0.40).toInt()
        val wantH = rows * dp(58) + sections * dp(44) + dp(8)
        root.addView(sv, LinearLayout.LayoutParams(MATCH, minOf(wantH, maxH)))

        // ---- الأزرار: تشغيل + تحميل
        val btns = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val pb = TextView(this).apply {
            text = "تشغيل"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(gray)
            background = rounded(Color.WHITE, 6, 0xFFCFD8DC.toInt())
            setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_play, 0, 0, 0)
            compoundDrawableTintList = android.content.res.ColorStateList.valueOf(gray)
            compoundDrawablePadding = dp(6)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener {
                val f = selected
                if (f != null) playPreview(f, CaptureHeaders.merge(headers, f.requestHeaders), et.text.toString())
            }
        }
        playBtn = pb
        btns.addView(pb, LinearLayout.LayoutParams(0, WRAP, 1f))
        selected?.let { pb.visibility = if (isMedia(it)) View.VISIBLE else View.GONE }
        btns.addView(TextView(this).apply {
            text = if (refreshTask() != null) "تحديث الرابط واستئناف" else "تحميل"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(red, 6)
            setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_download, 0, 0, 0)
            compoundDrawablePadding = dp(8)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener {
                val f = selected
                if (f != null) {
                    dlg.dismiss()
                    startDownload(f, et.text.toString(), CaptureHeaders.merge(headers, f.requestHeaders), poster)
                }
            }
        }, LinearLayout.LayoutParams(0, WRAP, 2f).apply { marginStart = dp(10) })
        root.addView(btns, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(10) })

        dlg.setContentView(outer)
        dlg.setOnDismissListener { pathView = null }
        dlg.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dlg.show()
    }

    private fun startDownload(f: Format, n: String, headers: Map<String, String>, poster: String) {
        val rt = refreshTask()
        if (rt != null) {
            if (rt.kind != f.kind) {
                toast("نوع الرابط مختلف عن الملف الأصلي، اختر نفس الجودة")
                return
            }
            DownloadCenter.replaceSource(rt, f.url, headers)
            clearRefresh()
            toast("تم تحديث الرابط واستئناف التحميل — تابعه من (التحميلات)")
            return
        }
        var name = Util.safeName(n)
        if (!name.endsWith("." + f.ext, true)) name += "." + f.ext
        val t = Task(UUID.randomUUID().toString(), f.url, name, f.kind, headers)
        t.mime = Util.mimeOf(name)
        t.thumbUrl = poster
        DownloadCenter.add(t)
        toast("بدأ التحميل — تابعه من زر (التحميلات)")
    }

    // ---------------------------------------------------------------- القائمة (على نمط كروم)
    private fun addBookmark() {
        val t = cur
        if (t != null && t.url.startsWith("http")) {
            val l = Store.entries(this, "bookmarks")
            l.removeAll { it.url == t.url }
            l.add(0, Store.Entry(t.title, t.url, System.currentTimeMillis()))
            Store.putEntries(this, "bookmarks", l)
            toast("تمت الإضافة إلى الإشارات المرجعية")
        }
    }

    private fun showMenu(anchor: View) {
        val dark = 0xFF37474F.toInt()
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        val pw = PopupWindow(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            background = rounded(Color.WHITE, 16, 0xFFE0E0E0.toInt())
            setPadding(dp(6), dp(8), dp(6), dp(8))
        }
        val topRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        fun topBtn(res: Int, enabled: Boolean = true, onClick: () -> Unit) {
            val b = iconBtn(res, dark, 11)
            b.background = circle(0xFFF1F3F4.toInt())
            b.alpha = if (enabled) 1f else 0.35f
            b.setOnClickListener { if (enabled) { pw.dismiss(); onClick() } }
            topRow.addView(b, LinearLayout.LayoutParams(dp(46), dp(46)).apply { setMargins(dp(4), dp(2), dp(4), dp(2)) })
        }
        val t = cur
        topBtn(R.drawable.ic_forward, t?.web?.canGoForward() == true) { cur?.web?.goForward() }
        topBtn(R.drawable.ic_back, t?.web?.canGoBack() == true) { cur?.web?.goBack() }
        topBtn(R.drawable.ic_star) { addBookmark() }
        topBtn(R.drawable.ic_download) { startActivity(Intent(this, DownloadsActivity::class.java)) }
        topBtn(R.drawable.ic_refresh) { cur?.web?.reload() }
        col.addView(topRow)

        fun line() = col.addView(View(this).apply { setBackgroundColor(0xFFE8EAED.toInt()) },
            LinearLayout.LayoutParams(MATCH, dp(1)).apply { setMargins(dp(8), dp(6), dp(8), dp(6)) })

        fun item(res: Int, text: String, mark: Boolean = false, onClick: () -> Unit) {
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), 0, dp(12), 0)
                setBackgroundResource(ripple())
                setOnClickListener { pw.dismiss(); onClick() }
            }
            r.addView(ImageView(this).apply { setImageResource(res); setColorFilter(dark) }, LinearLayout.LayoutParams(dp(22), dp(22)))
            r.addView(TextView(this).apply { this.text = text; textSize = 15f; setTextColor(Color.BLACK) },
                LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = dp(16) })
            if (mark) r.addView(TextView(this).apply { this.text = "✓"; textSize = 16f; setTextColor(0xFF1565C0.toInt()) })
            col.addView(r, LinearLayout.LayoutParams(MATCH, dp(48)))
        }

        line()
        item(R.drawable.ic_add, "علامة تبويب جديدة") { newTab(Prefs.home(this)) }
        item(R.drawable.ic_tabs, "علامات التبويب (" + tabs.size + ")") { showTabs() }
        item(R.drawable.ic_home, "الصفحة الرئيسية") { cur?.web?.loadUrl(Prefs.home(this)) }
        line()
        item(R.drawable.ic_history, "السجل") { openList("history") }
        item(R.drawable.ic_download, "عمليات التنزيل") { startActivity(Intent(this, DownloadsActivity::class.java)) }
        item(R.drawable.ic_star, "الإشارات المرجعية") { openList("bookmarks") }
        line()
        item(R.drawable.ic_share, "مشاركة") {
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, cur?.url ?: ""),
                    "مشاركة"
                )
            )
        }
        item(R.drawable.ic_copy, "نسخ الرابط") {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("url", cur?.url ?: ""))
            toast("تم نسخ الرابط")
        }
        item(R.drawable.ic_desktop, "وضع سطح المكتب", desktop) {
            desktop = !desktop
            tabs.forEach { applyUa(it.web) }
            cur?.web?.reload()
        }
        line()
        item(R.drawable.ic_settings, "الإعدادات") { startActivity(Intent(this, SettingsActivity::class.java)) }

        val sv = ScrollView(this)
        sv.addView(col)
        pw.contentView = sv
        pw.width = dp(280)
        pw.height = minOf(dp(600), (screenH * 0.85).toInt())
        pw.isFocusable = true
        pw.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        pw.elevation = dp(10).toFloat()
        val loc = IntArray(2)
        anchor.getLocationOnScreen(loc)
        val x = loc[0].coerceIn(dp(8), maxOf(dp(8), screenW - dp(280) - dp(8)))
        pw.showAtLocation(anchor.rootView, Gravity.TOP or Gravity.LEFT, x, loc[1])
    }

    private fun showSaved(title: String, key: String) {
        val list = Store.get(this, key)
        if (list.isEmpty()) { toast("القائمة فارغة"); return }
        val names = list.map { if (it.first.isEmpty()) it.second else it.first }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(names) { _, i -> cur?.web?.loadUrl(list[i].second) }
            .setNeutralButton("مسح الكل") { _, _ -> Store.put(this, key, emptyList()) }
            .setNegativeButton("إغلاق", null)
            .show()
    }
}
