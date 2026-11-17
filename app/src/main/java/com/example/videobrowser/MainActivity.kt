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
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.Executors
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
      if (/[?&](adtag|adurl|ad_type|adtype|vast|vmap|preroll|midroll|postroll)=/i.test(s)) return true;
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
      return r.width >= 70 && r.height >= 45;
    };
    var parsePlayerConfig = function(raw, add, depth){
      if (raw === null || raw === undefined || depth > 8) return;
      if (typeof raw === 'string') {
        var value = raw.trim();
        if (!value) return;
        if ((value.charAt(0) === '{' || value.charAt(0) === '[') && value.length < 500000) {
          try { parsePlayerConfig(JSON.parse(value), add, depth + 1); return; } catch (_) {}
        }
        if (isMediaUrl(value)) add(value);
        return;
      }
      if (Array.isArray(raw)) {
        for (var i = 0; i < Math.min(raw.length, 80); i++) parsePlayerConfig(raw[i], add, depth + 1);
        return;
      }
      if (typeof raw !== 'object') return;
      var badKey = /(^|[_-])(ad|ads|advert|advertisement|vast|vmap|preroll|midroll|postroll|promo|preview|thumb|poster|trailer|teaser|sponsor)([_-]|$)|^(ad|ads|advert|vast|vmap|preroll|midroll|postroll|promo|preview|thumb|poster|trailer|teaser|sponsor)/i;
      var sourceKey = /(file|src|url|hls|m3u8|manifest|source|playlist|contenturl|stream|video|mp4|webm)/i;
      var keys = Object.keys(raw);
      for (var k = 0; k < Math.min(keys.length, 120); k++) {
        var key = keys[k];
        if (badKey.test(key)) continue;
        var value = raw[key];
        if (typeof value === 'string' && sourceKey.test(key)) parsePlayerConfig(value, add, depth + 1);
        else if (value && typeof value === 'object') parsePlayerConfig(value, add, depth + 1);
      }
    };
    var collectSources = function(v){
      var out = [];
      var seen = Object.create(null);
      var add = function(raw){
        var u = absUrl(raw);
        if (!isHttp(u) || isAdUrl(u) || isChunkUrl(u) || !isMediaUrl(u) || seen[u]) return;
        seen[u] = true;
        out.push(u);
      };
      // 1) إعدادات المحتوى المسبقة على عنصر الفيديو والحاويات القريبة.
      var n = v;
      var playerRoot = v.parentElement || document;
      for (var depth = 0; n && depth < 7; depth++, n = n.parentElement) {
        if (!n.getAttribute) continue;
        var attrs = [
          'data-src','data-file','data-video','data-video-url','data-content-url',
          'data-hls','data-m3u8','data-manifest','data-stream-url','data-file-url',
          'data-player-config','data-setup','data-config','data-options'
        ];
        for (var a = 0; a < attrs.length; a++) {
          var attrValue = n.getAttribute(attrs[a]);
          if (!attrValue) continue;
          if (attrs[a].indexOf('config') >= 0 || attrs[a] === 'data-setup' || attrs[a] === 'data-options') {
            parsePlayerConfig(attrValue, add, 0);
          } else add(attrValue);
        }
        var cls = typeof n.className === 'string' ? n.className : '';
        var marker = (n.id || '') + ' ' + cls;
        if (depth > 0 && /(player|video|media|watch|stream|jwplayer|video-js|plyr)/i.test(marker)) {
          playerRoot = n;
          break;
        }
      }
      // 2) مصادر source داخل عنصر الفيديو أو حاوية المشغل فقط، مع تجاهل الصوت.
      var children = v.querySelectorAll ? v.querySelectorAll('source[src],source[data-src]') : [];
      for (var i = 0; i < Math.min(children.length, 12); i++) {
        var childType = (children[i].getAttribute('type') || '').toLowerCase();
        if (!childType || childType.indexOf('video/') === 0 || childType.indexOf('application/vnd.apple.mpegurl') === 0) {
          add(children[i].getAttribute('src') || children[i].getAttribute('data-src'));
        }
      }
      // 3) إعداد JSON/VideoObject داخل نطاق المشغل، وليس جميع روابط الصفحة عشوائيًا.
      var scripts = playerRoot && playerRoot.querySelectorAll ? playerRoot.querySelectorAll('script[type="application/json"],script[type="application/ld+json"],script[id*="player" i],script[id*="video" i],[data-player-config]') : [];
      for (var j = 0; j < Math.min(scripts.length, 12); j++) {
        var text = scripts[j].textContent || scripts[j].getAttribute('data-player-config') || '';
        if (text.length < 500000) parsePlayerConfig(text, add, 0);
      }
      // 4) بيانات VideoObject وOpen Graph على الصفحة، وهي مفيدة قبل بدء الإعلان.
      var ld = document.querySelectorAll('script[type="application/ld+json"]');
      for (var x = 0; x < Math.min(ld.length, 8); x++) {
        var ldText = ld[x].textContent || '';
        if (!/VideoObject/i.test(ldText) || ldText.length > 500000) continue;
        parsePlayerConfig(ldText, add, 0);
      }
      var metas = document.querySelectorAll('meta[property="og:video"],meta[property="og:video:url"],meta[property="og:video:secure_url"]');
      for (var m = 0; m < Math.min(metas.length, 4); m++) add(metas[m].content);
      // currentSrc/src قد يتحولان مؤقتًا إلى ملف الإعلان؛ لا نضيفهما أثناء وضع الإعلان.
      var adNow = hasAdMarker(v) || hasVisibleAdOverlay(v) || isAdUrl(v.currentSrc || v.getAttribute('src') || '');
      if (!adNow) {
        add(v.getAttribute('src'));
        add(v.currentSrc);
      }
      return out.slice(0, 24);
    };
    var makeId = function(v){
      if (!v.__vbId) v.__vbId = Math.random().toString(36).slice(2);
      return String(v.__vbId);
    };
    var emit = function(v, eventName){
      try {
        var id = makeId(v);
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
        var ad = isAdUrl(src) || adContainer;
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
            JSON.stringify(sources), !!adContainer
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
      if (!visible(v)) continue;
      var r = v.getBoundingClientRect();
      var area = Math.max(0, r.width) * Math.max(0, r.height);
      if (area < 9000) continue;
      var src = absUrl(v.currentSrc || v.getAttribute('src') || '');
      var ad = isAdUrl(src) || hasAdMarker(v) || hasVisibleAdOverlay(v);
      var sources = collectSources(v);
      var center = Math.max(0, 1 - Math.abs((r.left + r.width / 2) - (window.innerWidth / 2)) / Math.max(1, window.innerWidth));
      var score = area * (ad ? 0.90 : 1.0) + area * center * 0.08 + (sources.length ? area * 0.05 : 0);
      // اختر أكبر فيديو محتوى أولًا؛ لا تسمح لإعلان أكبر قليلًا أن يزيح فيديو المحتوى.
      if (!ad && score > bestScore) { bestScore = score; bestVideo = v; }
      if (ad && score > bestAdScore) { bestAdScore = score; bestAdVideo = v; }
      var tnow = now;
      var ct = Number(v.currentTime || 0);
      var last = v.__vbLastTime;
      var lastAt = Number(v.__vbLastAt || 0);
      var playing = !v.paused && !v.ended && v.readyState >= 2;
      // بعض مشغلات الإعلانات تستخدم عنصر MSE/blob نفسه، ثم تعيد عداد الوقت إلى الصفر للمحتوى.
      // نعد ذلك جيل مصدر جديدًا ما لم يكن المستخدم يسحب شريط التقدم.
      var resetAtContentBoundary = playing && last !== undefined && ct + 1.25 < last && !v.seeking && !v.__vbWasSeeking;
      if (resetAtContentBoundary) {
        v.__vbSourceEpoch = Number(v.__vbSourceEpoch || 0) + 1;
        v.__vbCycle = Number(v.__vbCycle || 0) + 1;
        emit(v, 'content-transition');
      }
      var advancing = last !== undefined && (tnow - lastAt) >= 300 && ct > last + 0.04;
      v.__vbLastTime = ct;
      v.__vbLastAt = tnow;
      if (playing && advancing && v.__vbUserInitiated && !ad) {
        var activeScore = Number(v.__vbUserInitiatedAt || 0) + area / 1000000;
        if (!active || activeScore > active.score) {
          active = { id: makeId(v), score: activeScore, src: src, duration: Number(v.duration || 0) };
        }
      }
      // إذا تغير المصدر ولم يصل حدث DOM لأي سبب، أرسل تحديثًا من دورة الفحص.
      var sourceKey = src + '||' + sources.join('||');
      var observedAdState = isAdUrl(src) || hasAdMarker(v) || hasVisibleAdOverlay(v);
      if (sourceKey !== v.__vbSourceKey || (v.__vbAdState !== undefined && v.__vbAdState !== observedAdState)) {
        emit(v, 'scan');
      } else if (v.__vbAdState === undefined) {
        emit(v, 'scan');
      }
    }
    var primary = null;
    if (!bestVideo) bestVideo = bestAdVideo;
    if (bestVideo) {
      var pv = bestVideo;
      var psrc = absUrl(pv.currentSrc || pv.getAttribute('src') || '');
      var pad = isAdUrl(psrc) || hasAdMarker(pv) || hasVisibleAdOverlay(pv);
      var pSources = collectSources(pv);
      var pKey = psrc + '||' + pSources.join('||');
      if (pKey !== pv.__vbSourceKey) emit(pv, 'scan');
      primary = {
        id: makeId(pv), src: psrc, sources: pSources,
        sourceKey: pKey, sourceEpoch: Number(pv.__vbSourceEpoch || 0),
        cycle: Number(pv.__vbCycle || 0), ad: !!pad,
        adContainer: hasAdMarker(pv) || hasVisibleAdOverlay(pv), poster: absUrl(pv.poster || ''),
        duration: Number(pv.duration || 0),
        blob: /^blob:/i.test(String(pv.currentSrc || pv.src || ''))
      };
    }
    return JSON.stringify({ p: primary, a: active });
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
        val sourceEpoch: Int
    )

    private class Tab(val web: WebView) {
        var title = "علامة تبويب جديدة"
        var url = ""
        var pending = false
        // media = المصادر الجاهزة للعرض؛ preparedMedia مصدرها DOM/تهيئة المشغل،
        // وsniffed مصادر الشبكة المرتبطة بفترة المصدر الحالية فقط.
        val media = LinkedHashMap<String, MediaItem>()
        val preparedMedia = LinkedHashMap<String, MediaItem>()
        val sniffed = LinkedHashMap<String, SniffedMedia>()
        // طلبات مرشحة ظهرت أثناء إعلان ما قبل التشغيل؛ لا تُعرض للمستخدم إلا بعد انتقال المصدر إلى المحتوى.
        val pendingSniffed = LinkedHashMap<String, Long>()
        // طلبات وسائط التقطت قبل أن ينتهي فحص DOM من تحديد عنصر الفيديو الرئيسي.
        val pageRequests = LinkedHashMap<String, Long>()
        var primaryVideoId: String? = null
        var primarySourceKey = ""
        var primarySourceEpoch = 0
        var primaryCycle = 0
        var primaryIsAd = false
        var expectedDurationSec = 0.0
        var sawBlob = false
        @Volatile var activePlayback = false
        @Volatile var activeId: String? = null
        val userGestureVideoIds = LinkedHashSet<String>()
        @Volatile var userGestureAt = 0L
        var poster = ""
        var lastForced = ""
    }

    class VideoGestureBridge(
        private val onGesture: (String) -> Unit,
        private val onVideoEvent: (String, String, Boolean, String, Double, Int, Int, String, Boolean) -> Unit
    ) {
        @JavascriptInterface
        fun onVideoGesture(videoId: String?) {
            val id = videoId?.take(100)?.trim().orEmpty()
            if (id.isNotEmpty()) onGesture(id)
        }

        @JavascriptInterface
        fun onVideoEvent(
            videoId: String?, sourceKey: String?, isAd: Boolean, eventName: String?,
            duration: Double, sourceEpoch: Int, cycle: Int, sourcesJson: String?, adContainer: Boolean
        ) {
            val id = videoId?.take(100)?.trim().orEmpty()
            if (id.isNotEmpty()) {
                onVideoEvent(
                    id, sourceKey?.take(12000).orEmpty(), isAd,
                    eventName?.take(40).orEmpty(), duration,
                    sourceEpoch, cycle, sourcesJson?.take(16000).orEmpty(), adContainer
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
        ui.post(scanLoop)
    }

    override fun onPause() {
        ui.removeCallbacks(scanLoop)
        cur?.let {
            it.activePlayback = false
            it.activeId = null
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
        ui.removeCallbacksAndMessages(null)
        content.removeAllViews()
        tabs.forEach { it.web.destroy() }
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

    private fun updateFab() {
        // لا عدادات أو شارات: الأحمر حصريًا عند رصد تشغيل فعلي بدأه المستخدم.
        val active = cur?.activePlayback == true
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
        t.pendingSniffed.clear()
        t.pageRequests.clear()
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
        w.addJavascriptInterface(VideoGestureBridge(
            onGesture = { id ->
                ui.post {
                    // لا نحذف التفاعل عند pause/ended؛ فقد يكون هذا انتقالًا من إعلان قصير إلى المحتوى.
                    tab.userGestureVideoIds.add(id)
                    tab.userGestureAt = System.currentTimeMillis()
                    if (tab.userGestureVideoIds.size > 12) {
                        val keep = tab.userGestureVideoIds.toList().takeLast(8)
                        tab.userGestureVideoIds.clear()
                        tab.userGestureVideoIds.addAll(keep)
                    }
                }
            },
            onVideoEvent = { id, key, isAd, event, duration, epoch, cycle, sourcesJson, adContainer ->
                handleVideoEvent(tab, id, key, isAd, event, duration, epoch, cycle, sourcesJson, adContainer)
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
                t.url = url
                resetVideoSession(t)
                if (t === cur) { urlBar.setText(url); updateFab() }
            }

            override fun onPageFinished(v: WebView, url: String) {
                val t = tabOf(v) ?: return
                t.url = url
                t.title = v.title ?: url
                addHistory(t.title, url)
                scan(t)
            }

            override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse? {
                val u = r.url.toString()
                // نخزن الطلبات المرشحة داخليًا فقط إذا أمكن ربطها لاحقًا بعنصر الفيديو الرئيسي.
                // لا يظهر أي عداد أو رابط خام للمستخدم، وتُرفض الإعلانات والمعاينات والمقاطع الجزئية.
                if (!r.isForMainFrame && isCandidateMediaRequest(u)) {
                    ui.post { rec(tab, u) }
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
        val path = Uri.parse(raw).path.orEmpty().lowercase()
        return path.endsWith(".m3u8")
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
        return false
    }

    private fun resetVideoSession(t: Tab) {
        t.media.clear()
        t.preparedMedia.clear()
        t.sniffed.clear()
        t.pendingSniffed.clear()
        t.pageRequests.clear()
        t.primaryVideoId = null
        t.primarySourceKey = ""
        t.primarySourceEpoch = 0
        t.primaryCycle = 0
        t.primaryIsAd = false
        t.expectedDurationSec = 0.0
        t.activePlayback = false
        t.activeId = null
        t.userGestureVideoIds.clear()
        t.userGestureAt = 0L
        t.sawBlob = false
        t.poster = ""
    }

    private fun syncMedia(t: Tab) {
        t.media.clear()
        t.preparedMedia.forEach { (url, item) -> t.media[url] = item }
        val id = t.primaryVideoId ?: return
        if (t.primaryIsAd) return
        val now = System.currentTimeMillis()
        t.sniffed.values
            .filter { it.videoId == id && it.sourceEpoch == t.primarySourceEpoch && now - it.requestedAt in 0L..180_000L }
            .forEach { captured ->
                if (isCandidateMediaRequest(captured.url)) {
                    t.media[captured.url] = MediaItem(captured.url, if (isHlsUrl(captured.url)) "hls" else "direct", "network", id, captured.sourceEpoch)
                }
            }
    }

    private fun addPreparedSource(t: Tab, url: String, videoId: String) {
        if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) return
        if (!isCandidateMediaRequest(url)) return
        val type = if (isHlsUrl(url)) "hls" else "direct"
        t.preparedMedia[url] = MediaItem(url, type, "dom", videoId, t.primarySourceEpoch)
    }

    private fun promotePageRequests(t: Tab, id: String, epoch: Int, isAd: Boolean) {
        val now = System.currentTimeMillis()
        t.pageRequests.entries.removeAll { now - it.value !in 0L..90_000L }
        t.pageRequests.forEach { (url, time) ->
            if (now - time <= 90_000L && isCandidateMediaRequest(url)) {
                if (isAd) t.pendingSniffed[url] = time
                else t.sniffed[url] = SniffedMedia(url, time, id, epoch)
            }
        }
        t.pageRequests.clear()
    }

    private fun advanceSniffedEpochForContent(t: Tab, id: String, epoch: Int) {
        val now = System.currentTimeMillis()
        val recent = t.sniffed.values.filter {
            it.videoId == id && now - it.requestedAt in 0L..120_000L
        }
        t.sniffed.entries.removeAll { it.value.videoId == id }
        recent.forEach { old ->
            t.sniffed[old.url] = SniffedMedia(old.url, old.requestedAt, id, epoch)
        }
    }

    private fun promotePendingSniffed(t: Tab, id: String, epoch: Int) {
        val now = System.currentTimeMillis()
        t.pendingSniffed.entries.removeAll { now - it.value !in 0L..120_000L }
        t.pendingSniffed.forEach { (url, time) ->
            if (now - time <= 120_000L && isCandidateMediaRequest(url)) {
                t.sniffed[url] = SniffedMedia(url, time, id, epoch)
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
        duration: Double, sourceEpoch: Int, cycle: Int, sourcesJson: String, adContainer: Boolean
    ) {
        ui.post {
            if (tabs.none { it === t }) return@post
            // لا ننسب أحداث عناصر المعاينة أو الإعلان إلى الفيديو الرئيسي قبل أن يحدده فحص DOM.
            if (t.primaryVideoId == null || t.primaryVideoId != id) return@post

            val generationChanged = (t.primarySourceKey.isNotEmpty() && t.primarySourceKey != sourceKey) ||
                t.primarySourceEpoch != sourceEpoch || t.primaryCycle != cycle
            val wasAd = t.primaryIsAd
            val nowAd = isAd || adContainer
            if (generationChanged) {
                if (wasAd && !nowAd) advanceSniffedEpochForContent(t, id, sourceEpoch)
                else t.sniffed.entries.removeAll { it.value.videoId == id && it.value.sourceEpoch != sourceEpoch }
                if (!wasAd && !nowAd) {
                    t.preparedMedia.entries.removeAll { it.value.videoId == id && it.value.sourceEpoch != sourceEpoch }
                }
            }
            t.primarySourceKey = sourceKey
            t.primarySourceEpoch = sourceEpoch
            t.primaryCycle = cycle
            t.primaryIsAd = nowAd
            // لا تسجل مدة الإعلان على أنها مدة المحتوى. تُحدّث المدة فقط بعد زوال علامات الإعلان.
            if (!nowAd && duration.isFinite() && duration > 0.0) t.expectedDurationSec = duration
            // احتفظ بمصادر المحتوى المسبقة التي عثرنا عليها في إعدادات المشغل أثناء الإعلان.
            // أضف المصادر الجديدة التي استخرجها DOM؛ addPreparedSource يستبعد روابط الإعلان الصريحة.
            if (sourcesJson.isNotBlank()) {
                try {
                    val arr = org.json.JSONArray(sourcesJson)
                    for (i in 0 until arr.length()) addPreparedSource(t, arr.optString(i), id)
                } catch (_: Exception) {
                }
            }
            if (wasAd && !nowAd) promotePendingSniffed(t, id, sourceEpoch)
            if (t.primaryIsAd || event == "pause" || event == "ended" || event == "emptied") {
                t.activePlayback = false
                t.activeId = null
            }
            syncMedia(t)
            if (cur === t) updateFab()
        }
    }

    private fun rec(t: Tab, url: String) {
        if (!isCandidateMediaRequest(url)) return
        // قد تُطلب قائمة الجودة قبل أن يكتمل أول فحص DOM؛ احتفظ بمرشحات قصيرة العمر
        // حتى نحدد عنصر الفيديو الرئيسي ثم نربطها به بدل إسقاطها.
        val id = t.primaryVideoId
        val now = System.currentTimeMillis()
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
        if (t.primaryIsAd) {
            // لا نعرض مصادر فترة الإعلان، لكن نحتفظ مؤقتًا بالطلبات المرشحة غير المعلّمة
            // لنربط طلبات المحتوى التي بدأ تحميلها قبل انتقال المشغل من الإعلان إلى الفيلم.
            t.pendingSniffed[url] = now
            t.pendingSniffed.entries.removeAll { now - it.value !in 0L..120_000L }
            while (t.pendingSniffed.size > 100) {
                val oldest = t.pendingSniffed.keys.firstOrNull() ?: break
                t.pendingSniffed.remove(oldest)
            }
            return
        }
        t.sniffed[url] = SniffedMedia(url, now, id, t.primarySourceEpoch)
        while (t.sniffed.size > 80) {
            val oldest = t.sniffed.keys.firstOrNull() ?: break
            t.sniffed.remove(oldest)
        }
        syncMedia(t)
    }

    private fun scan(t: Tab) {
        if (t.pending) return
        t.web.evaluateJavascript(SCAN_JS) { r ->
            try {
                if (r != null && r != "null") {
                    val s = JSONTokener(r).nextValue() as? String
                    if (s != null) onScan(t, JSONObject(s))
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun onScan(t: Tab, o: JSONObject) {
        val p = o.optJSONObject("p")
        if (p == null) {
            t.activePlayback = false
            t.activeId = null
            // لا نمسح المصادر المحضّرة لمجرد أن العنصر خرج مؤقتًا من DOM/الرؤية.
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
        val nowAd = isAd || adContainer

        val primaryChanged = t.primaryVideoId != id
        if (primaryChanged) {
            t.media.clear()
            t.preparedMedia.clear()
            t.sniffed.clear()
            t.pendingSniffed.clear()
            t.userGestureVideoIds.removeAll { it != id }
        } else if (sourceChanged) {
            if (wasAd && !nowAd) advanceSniffedEpochForContent(t, id, epoch)
            else t.sniffed.entries.removeAll { it.value.videoId == id && it.value.sourceEpoch != epoch }
            if (!wasAd && !nowAd) {
                t.preparedMedia.entries.removeAll { it.value.videoId == id && it.value.sourceEpoch != epoch }
            }
        }

        t.primaryVideoId = id
        t.primarySourceKey = key
        t.primarySourceEpoch = epoch
        t.primaryCycle = cycle
        t.primaryIsAd = nowAd
        if (primaryChanged) promotePageRequests(t, id, epoch, nowAd)
        val src = p.optString("src")
        t.sawBlob = p.optBoolean("blob", src.startsWith("blob:", true))
        t.poster = p.optString("poster")
        val duration = p.optDouble("duration", 0.0)
        // مدة عنصر الفيديو أثناء preroll تخص الإعلان؛ لا تستبدل بها مدة الفيلم الرئيسي.
        if (!nowAd && duration.isFinite() && duration > 0.0) t.expectedDurationSec = duration
        if (wasAd && !nowAd) promotePendingSniffed(t, id, epoch)

        // تجهّز المصادر من DOM وإعدادات المشغل قبل بدء التشغيل، وتبقى محفوظة أثناء الإعلان.
        // JavaScript يستبعد currentSrc عند ظهور علامات الإعلان، بينما يستخرج مصادر المحتوى من config.
        val arr = p.optJSONArray("sources")
        if (arr != null) {
            for (i in 0 until arr.length()) addPreparedSource(t, arr.optString(i), id)
        }

        val a = o.optJSONObject("a")
        val gestureMatches = id in t.userGestureVideoIds
        t.activePlayback = a != null && a.optString("id") == id && gestureMatches && !t.primaryIsAd
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
        return m
    }

    private fun onFabClick() {
        val t = cur ?: return
        val videoId = t.primaryVideoId
        if (videoId == null || t.media.isEmpty()) {
            val message = when {
                t.primaryIsAd -> "المشغل يعرض إعلانًا حاليًا؛ يتم تجهيز روابط المحتوى الرئيسي بعد انتقال المشغل إليه"
                t.sawBlob -> "مصدر الفيديو يستخدم Blob/MSE؛ انتظر تحميل مصدر المحتوى أو ابدأ تشغيله بعد الإعلان"
                else -> "لم تُكتشف مصادر الفيديو الرئيسي بعد؛ انتظر اكتمال تهيئة المشغل"
            }
            toast(message)
            return
        }
        val expectedDuration = t.expectedDurationSec
        val sourceEpoch = t.primarySourceEpoch
        toast(if (t.activePlayback) "جارٍ جلب جودات الفيديو الجاري تشغيله..." else "جارٍ جلب الجودات المحضّرة للفيديو الرئيسي...")
        val items = t.media.values.toList()
        val headers = headersFor(t)
        val title = if (t.title.isBlank()) Util.host(t.url) else t.title
        val poster = t.poster
        io.execute {
            val resolvedGroups = ArrayList<Pair<MediaItem, List<Format>>>()
            for (item in items) {
                try {
                    val formats = Formats.resolve(item, headers)
                    if (formats.isNotEmpty()) resolvedGroups.add(item to formats)
                } catch (_: Exception) {
                }
            }

            // إذا أصبحت مدة المحتوى معروفة، استبعد مجموعة مصدر قصيرة/قديمة لا تطابق مدة الفيديو.
            // لا نستخدم هذا الشرط قبل معرفة مدة المحتوى؛ فقد يكون إعلان ما قبل التشغيل هو الذي يعرض مدته مؤقتًا.
            val durationFiltered = resolvedGroups.filter { (_, group) ->
                val candidateDuration = group.map { it.durSec }.filter { it > 0.0 }.maxOrNull() ?: 0.0
                when {
                    candidateDuration <= 0.0 -> true
                    expectedDuration > 20.0 -> abs(candidateDuration - expectedDuration) <= maxOf(18.0, expectedDuration * 0.12)
                    expectedDuration in 1.0..12.0 -> candidateDuration >= expectedDuration + maxOf(20.0, expectedDuration * 2.0)
                    else -> true
                }
            }
            val hasLongContentAlternative = expectedDuration in 1.0..12.0 &&
                resolvedGroups.any { (_, group) ->
                    val candidateDuration = group.map { it.durSec }.filter { it > 0.0 }.maxOrNull() ?: 0.0
                    candidateDuration >= expectedDuration + maxOf(20.0, expectedDuration * 2.0)
                }
            val longestKnownDuration = resolvedGroups.maxOfOrNull { (_, group) ->
                group.map { it.durSec }.filter { it > 0.0 }.maxOrNull() ?: 0.0
            } ?: 0.0
            val contentLengthFiltered = if (expectedDuration <= 0.0 && longestKnownDuration > 20.0) {
                resolvedGroups.filter { (_, group) ->
                    val d = group.map { it.durSec }.filter { it > 0.0 }.maxOrNull() ?: 0.0
                    d <= 0.0 || d >= maxOf(20.0, longestKnownDuration * 0.35)
                }
            } else resolvedGroups
            val usableGroups = when {
                expectedDuration > 20.0 -> durationFiltered
                hasLongContentAlternative -> durationFiltered
                expectedDuration <= 0.0 && longestKnownDuration > 20.0 -> contentLengthFiltered
                else -> resolvedGroups
            }
            val masterGroup = usableGroups
                .filter { (_, group) -> group.any { it.fromMaster && !it.isAudio } }
                .maxByOrNull { (_, group) -> group.count { it.fromMaster && !it.isAudio } }
            val chosenGroups = if (masterGroup != null) listOf(masterGroup) else usableGroups
            val clean = chosenGroups.flatMap { it.second }
                .filter { f ->
                    !f.isAudio && !Formats.isAudio(f.ext) && !isExcludedMediaUrl(f.url) &&
                        (f.kind == "hls" || Util.kindOf("x." + f.ext) == "video")
                }
                .distinctBy { f -> f.url }
                .sortedWith(compareByDescending<Format> { qualityRank(it.label) }.thenByDescending { it.bytes })
            ui.post {
                if (isFinishing || isDestroyed) return@post
                // لا نعرض نتائج إعلان/فيديو/فترة مصدر سابقة بعد انتقال المشغل إلى محتوى آخر.
                if (cur !== t || t.primaryVideoId != videoId || t.primarySourceEpoch != sourceEpoch) return@post
                if (clean.isEmpty()) {
                    toast("لم أتمكن من استخراج جودات الفيديو الرئيسي؛ قد يكون المصدر محميًا أو غير مكشوف للصفحة")
                } else {
                    showFormatSheet(clean, headers, title, poster)
                }
            }
        }
    }

    private fun qualityRank(label: String): Int {
        val match = Regex("(\\d{3,4})\\s*[pP]").find(label)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    }

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
            val sz = TextView(this).apply { text = f.sizeText; textSize = 14f; setTextColor(gray) }
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(2), dp(4), dp(2), dp(4))
                setBackgroundResource(ripple())
                setOnClickListener { select(f) }
            }
            r.addView(rb)
            r.addView(lbl, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginStart = dp(6) })
            if (f.kind == "direct" && !f.isAudio) {
                r.addView(TextView(this).apply { text = f.ext.uppercase(); textSize = 12f; setTextColor(gray) },
                    LinearLayout.LayoutParams(WRAP, WRAP).apply { marginEnd = dp(14) })
            }
            r.addView(sz)
            rowsRef.add(RowRef(rb, lbl, sz, f))
            box.addView(r, LinearLayout.LayoutParams(MATCH, dp(48)))
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
        val wantH = rows * dp(50) + sections * dp(44) + dp(8)
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
                if (f != null) playPreview(f, headers, et.text.toString())
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
                    startDownload(f, et.text.toString(), headers, poster)
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
