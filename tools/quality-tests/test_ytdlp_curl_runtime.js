'use strict';
const fs = require('fs');
const path = require('path');
const assert = require('assert');
const root = path.resolve(__dirname, '../..');
const extractor = fs.readFileSync(path.join(root, 'app/src/main/java/com/example/videobrowser/YtDlpExtractor.kt'), 'utf8');
const formats = fs.readFileSync(path.join(root, 'app/src/main/java/com/example/videobrowser/Formats.kt'), 'utf8');
const net = fs.readFileSync(path.join(root, 'app/src/main/java/com/example/videobrowser/Net.kt'), 'utf8');
const model = fs.readFileSync(path.join(root, 'app/src/main/java/com/example/videobrowser/Models.kt'), 'utf8');
const downloads = fs.readFileSync(path.join(root, 'app/src/main/java/com/example/videobrowser/DownloadCenter.kt'), 'utf8');

assert.match(extractor, /--impersonate", "chrome"/);
assert.match(extractor, /--format", formatId/);
assert.match(extractor, /--output", template\.absolutePath/);
assert.match(extractor, /--cookies/);
assert.match(extractor, /cookieFile\?\.delete\(\)/);
assert.match(extractor, /http_headers/);
assert.match(extractor, /ytDlpPageUrl = pageUrl/);
assert.match(extractor, /ytDlpFormatId = formatId/);
assert.match(extractor, /yt:\$formatId/);
assert.match(extractor, /4L \* 60L \* 60L/);
assert.match(formats, /trustedVideoMetadata/);
assert.match(net, /HTTP \$code/);
assert.match(downloads, /YtDlpExtractor\.downloadToFile/);
assert.match(downloads, /\.put\("ytDlpPageUrl", t\.ytDlpPageUrl\)/);
assert.match(downloads, /\.put\("ytDlpFormatId", t\.ytDlpFormatId\)/);
assert.match(model, /ytDlpDownloadedOutput/);

console.log('PASS: yt-dlp-selected format retains source page/id and uses curl_cffi for actual download; cookie file cleanup and task persistence are wired.');
