'use strict';

const fs = require('fs');
const path = require('path');
const assert = require('assert');

const root = path.resolve(__dirname, '../..');
const read = (relative) => fs.readFileSync(path.join(root, relative), 'utf8');

const gradle = read('app/build.gradle.kts');
const manifest = read('app/src/main/AndroidManifest.xml');
const extractor = read('app/src/main/java/com/example/videobrowser/YtDlpExtractor.kt');
const main = read('app/src/main/java/com/example/videobrowser/MainActivity.kt');
const downloads = read('app/src/main/java/com/example/videobrowser/DownloadCenter.kt');
const settings = read('app/src/main/java/com/example/videobrowser/SettingsActivity.kt');
const app = read('app/src/main/java/com/example/videobrowser/App.kt');
const notices = read('THIRD_PARTY_NOTICES.md');

assert.match(gradle, /io\.github\.junkfood02\.youtubedl-android:library:0\.18\.1/,
  'The app must depend on the published youtubedl-android library version 0.18.1.');
assert.match(gradle, /abiFilters\.add\("arm64-v8a"\)/,
  'The test build must package the ARM64 native runtime used by the target phone.');
assert.match(manifest, /android:extractNativeLibs="true"/,
  'Native Python/QuickJS libraries must be extracted for the upstream wrapper.');

assert.match(extractor, /import com\.yausername\.youtubedl_android\.YoutubeDL\b/);
assert.match(extractor, /import com\.yausername\.youtubedl_android\.YoutubeDLRequest\b/);
assert.match(extractor, /YoutubeDL\.getInstance\(\)\.getInfo\(request\)/,
  'The app must call the real upstream extraction API rather than a local mock.');
assert.match(extractor, /CookieManager\.getInstance\(\)\.getCookie\(pageUrl\)/,
  'The extractor must pass the current WebView cookie context to yt-dlp.');
assert.match(extractor, /request\.addOption\("--add-headers"/,
  'The extractor must pass page request headers to yt-dlp.');
assert.match(extractor, /info\.formats\.orEmpty\(\)/,
  'The final list must be mapped from formats returned by yt-dlp.');
assert.match(extractor, /extension\(url\) == "mpd".*?continue/s,
  'DASH must not be exposed as downloadable until the app has a DASH merge path.');

assert.match(main, /startYtDlpPreflight\(t, url\)/,
  'Page-first extraction must start after the page loads, before playback.');
assert.match(main, /mergeFormatLists\(t\.resolvedFormats\.orEmpty\(\), result\.formats\)/,
  'yt-dlp output must be merged into the existing quality list.');
assert.match(main, /private fun onFabClick\(\)/);
assert.match(main, /shouldUseYtDlpPageExtractor\(pageUrl\)/,
  'The floating button must use the selected page extraction results.');
assert.match(main, /isDomain\("xhamster\.com"\)/);
assert.match(main, /isDomain\("pornhub\.com"\)/);

assert.match(app, /YtDlpExtractor\.warmUp\(this\)/,
  'The native runtime should initialize away from the UI thread on app startup.');
assert.match(settings, /YtDlpExtractor\.update\(this\)/,
  'The user must be able to update the actual yt-dlp executable from settings.');

assert.match(downloads, /internal fun requestHeaders\(t: Task, targetUrl: String\)/,
  'The request-header helper must be visible to the separate Engine object.');
assert.match(downloads, /DownloadCenter\.requestHeaders\(t, t\.url\)/,
  'The Engine must call the request-header helper through DownloadCenter.');
assert.match(downloads, /CookieManager\.getInstance\(\)\.getCookie\(targetUrl\)/);
assert.match(main, /YtDlpExtractor\.status\(this@MainActivity\)/,
  'The diagnostics builder must pass the Activity context, not its StringBuilder receiver.');
assert.match(downloads, /\.put\("headers", JSONObject\(t\.headers\)\)/,
  'Only the safe, persistent header set is stored with download tasks.');
assert.match(notices, /GNU GPL v3\.0/,
  'The upstream GPL-3.0 licensing notice must be present.');

console.log('PASS: youtubedl-android dependency, page-first getInfo, WebView cookies/headers, FAB integration, format mapping, DASH guard, and license notice.');
