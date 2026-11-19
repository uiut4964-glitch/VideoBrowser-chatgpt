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
const models = read('app/src/main/java/com/example/videobrowser/Models.kt');
const settings = read('app/src/main/java/com/example/videobrowser/SettingsActivity.kt');
const app = read('app/src/main/java/com/example/videobrowser/App.kt');
const workflow = read('.github/workflows/build.yml');
const prepare = read('tools/prepare_android_runtime.sh');
const notices = read('THIRD_PARTY_NOTICES.md');

assert.match(gradle, /org\.apache\.commons:commons-compress:1\.27\.1/,
  'The runtime extractor must preserve Unix symlinks in the Android Python runtime ZIP.');
assert.match(gradle, /abiFilters\.add\("arm64-v8a"\)/,
  'The test build must package the ARM64 curl_cffi runtime.');
assert.match(gradle, /useLegacyPackaging\s*=\s*true/,
  'The packaged Android Python executable and zip runtime must be extracted through AGP packaging configuration.');
assert.doesNotMatch(manifest, /android:extractNativeLibs=/,
  'AGP packaging DSL owns extractNativeLibs; the manifest should not declare the deprecated attribute.');

assert.match(extractor, /libpython\.so/);
assert.match(extractor, /libpython\.zip\.so/);
assert.match(extractor, /PYTHONHOME/);
assert.match(extractor, /LD_LIBRARY_PATH/);
assert.match(extractor, /import sys,curl_cffi/,
  'Runtime initialization must fail clearly unless curl_cffi is importable.');
assert.match(extractor, /--impersonate", "chrome"/,
  'Pornhub extraction and downloads must use the Chrome TLS impersonation backend.');
assert.match(extractor, /--cookies/);
assert.match(extractor, /createCookieFile/,
  'Cookies must be written to a temporary Netscape jar, not passed in a Cookie header.');
assert.match(extractor, /fun downloadToFile\(/,
  'Selected yt-dlp formats must be downloadable through the same curl_cffi runtime.');
assert.doesNotMatch(extractor, /YoutubeDL\.getInstance|YoutubeDLRequest|--add-headers.*Cookie/,
  'The older Python 3.8 wrapper and deprecated Cookie-header method must not remain.');
assert.match(extractor, /curl_cffi\.__version__/,
  'The current runtime must verify the bundled curl_cffi package.');

assert.match(workflow, /node test_ytdlp_curl_runtime\.js/);
assert.match(workflow, /prepare_android_runtime\.sh/);
assert.match(prepare, /python-3\.14\.8/);
assert.match(prepare, /073430d0db34333a4a9d6b3e6801c3a00212f1a9632e05182c18165ab1ee1ca4/);
assert.match(prepare, /2026\.10\.26/);
assert.match(prepare, /e51e69e89489f199530f13d32a4342da3ddb2cf34842f364603a066db05afbf3/);

assert.match(main, /startYtDlpPreflight\(t, url\)/,
  'Page-first extraction must start after the page loads, before playback.');
assert.match(main, /mergeFormatLists\(t\.resolvedFormats\.orEmpty\(\), result\.formats\)/,
  'yt-dlp output must be merged into the existing quality list.');
assert.match(main, /YtDlpExtractor\.status\(this@MainActivity\)/);
assert.match(main, /yt:\$\{format\.ytDlpPageUrl\}:\$\{format\.ytDlpFormatId\}/,
  'Distinct yt-dlp format IDs must survive when they share one manifest URL.');
assert.match(downloads, /t\.ytDlpPageUrl\.isNotBlank\(\) && t\.ytDlpFormatId\.isNotBlank\(\)/,
  'yt-dlp-origin formats must bypass the legacy HttpURLConnection probe/download path.');
assert.match(downloads, /YtDlpExtractor\.downloadToFile\(/);
assert.match(downloads, /ytDlpDownloadedOutput/);
assert.match(models, /var ytDlpPageUrl = ""/);
assert.match(models, /var ytDlpFormatId = ""/);
assert.match(app, /YtDlpExtractor\.warmUp\(this\)/);
assert.match(settings, /YtDlpExtractor\.update\(this\)/);
assert.match(notices, /Python 3\.14\.8/);
assert.match(notices, /curl_cffi 0\.17\.0/);
assert.match(notices, /GNU GPL-3\.0/);

console.log('PASS: pinned Python/curl_cffi runtime, Chrome impersonation, temporary cookies, yt-dlp extraction and download path, workflow verification, and licenses.');
