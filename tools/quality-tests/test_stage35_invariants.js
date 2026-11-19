const fs = require('fs');
const path = require('path');
const root = path.join(__dirname, '../..');
const main = fs.readFileSync(path.join(root, 'app/src/main/java/com/example/videobrowser/MainActivity.kt'), 'utf8');
const api = fs.readFileSync(path.join(root, 'app/src/main/java/com/example/videobrowser/ApiCaptureScript.kt'), 'utf8');
const scan = fs.readFileSync(path.join(__dirname, 'SCAN_JS.js'), 'utf8');

function must(text, pattern, message) {
  if (!pattern.test(text)) throw new Error(message);
}

must(main, /if\s*\(t\.primaryIsAd\s*\|\|\s*t\.suspectedPreroll\)\s*\{[\s\S]{0,260}?showDiscoveryDiagnostics/, 'Download options are not gated while an ad/preroll is active');
must(main, /val active = cur\?\.let \{ it\.activePlayback && !it\.primaryIsAd && !it\.suspectedPreroll \} == true/, 'FAB color is not explicitly blocked for ad/preroll states');
must(main, /t\.playerVideoDurationSec = 0\.0[\s\S]{0,80}t\.playerAdReason = ""/, 'Player duration/ad reason are not reset on navigation');
must(main, /\.put\("adReason", item\.optString\("adReason"\)\.take\(80\)\)[\s\S]{0,120}\.put\("playerVideoDuration", item\.optDouble\("playerVideoDuration"/, 'Frame ad diagnostic state is not forwarded to native');
must(main, /"performance-manifest"\s*->\s*0[\s\S]{0,160}"player-flashvars"\s*->\s*3/, 'Fresh Resource Timing manifests are not prioritized over stale flashvars renditions');
if (/val ad = match\.optBoolean\("ad", false\) \|\| t\.primaryIsAd/.test(main)) {
  throw new Error('A stale ad flag can keep the content classified as an ad after transition');
}
must(api, /captureSource:\s*'performance-manifest'/, 'Iframe Resource Timing manifest capture is missing');
must(scan, /capture !== 'player-flashvars' && capture !== 'performance-manifest'/, 'Main-frame player manifests/renditions are not withheld during preroll');
must(api, /item\.meta\.captureSource !== 'player-flashvars' && item\.meta\.captureSource !== 'performance-manifest'/, 'Iframe player manifests/renditions are not withheld during preroll');
console.log('Stage 3.5 invariants passed: ad gate, grey FAB during preroll, per-page state reset, frame ad diagnostics, fresh-manifest priority, non-sticky ad transition, and manifest preroll filtering');
