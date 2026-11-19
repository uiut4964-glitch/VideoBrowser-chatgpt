const fs = require('fs');
const path = require('path');
const assert = require('assert');
const root = path.resolve(__dirname, '../..');
const main = fs.readFileSync(path.join(root, 'app/src/main/java/com/example/videobrowser/MainActivity.kt'), 'utf8');
const extractor = fs.readFileSync(path.join(root, 'app/src/main/java/com/example/videobrowser/YtDlpExtractor.kt'), 'utf8');
const capture = fs.readFileSync(path.join(root, 'app/src/main/java/com/example/videobrowser/MediaCapture.kt'), 'utf8');

assert.match(extractor, /safe\["Referer"\] = "\$pageOrigin\/"/,
  'yt-dlp must use the canonical Pornhub site-root Referer rather than a stale WebView URL.');
assert.match(extractor, /safe\["Origin"\] = pageOrigin/);
assert.match(extractor, /safe\.remove\("User-Agent"\)/,
  'Do not pair WebView mobile User-Agent with curl_cffi desktop Chrome impersonation.');

assert.match(main, /host == "pornhub\.com" \|\| host\.endsWith\("\.pornhub\.com"\)/,
  'Pornhub-specific root Referer/Origin handling must remain scoped to Pornhub domains.');
assert.match(main, /val referer = if \(host == "pornhub\.com" \|\| host\.endsWith\("\.pornhub\.com"\)\) "\$pageOrigin\/" else t\.url/,
  'Pornhub requests should use the site-root Referer required by the upstream extractor fix.');
assert.match(main, /matchesPreparedSourceHost\(url, it, t\.primaryFrameOrigin, t\.url, now\)/,
  'Sibling media renditions should receive safe headers from a same-CDN WebView request.');
assert.match(main, /CaptureHeaders\.safeRequestHeaders\(it\.headers\)/,
  'Only the explicitly allowlisted, non-secret browser headers may be shared.');
assert.match(capture, /fun matchesPreparedSourceHost\(/);
assert.match(capture, /if \(preparedHost != requestHost\) return false/,
  'Header inheritance must never cross CDN hosts.');
assert.match(capture, /safeRequestHeaders\(request\.headers\)\.isNotEmpty\(\)/,
  'A host-level context without safe headers must not be inherited.');
assert.match(main, /جودات ورثت ترويسات WebView من CDN نفسه/,
  'Diagnostics must report when sibling renditions inherited browser request headers.');

console.log('PASS: Pornhub site-root headers, safe same-CDN request-context sharing, and diagnostics.');
