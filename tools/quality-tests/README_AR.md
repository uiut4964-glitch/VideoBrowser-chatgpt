# اختبارات الالتقاط واستخراج الجودات — VideoBrowser 1.11.6 / Stage 3.6 – Blob/MSE renditions and frame playback

هذه اختبارات محلية للمحلل وطبقة اكتشاف المصادر. لا تتصل بمواقع حقيقية ولا تثبت دعم كل مشغل.

## اختبارات JavaScript

من هذا المجلد شغّل:

```bash
node test_scan_sources.js
node test_scan_api.js
node test_scan_hls_payload.js
node QualityMapConfigTest.js
node test_early_api_capture.js
node test_early_api_pipeline.js
node test_frame_video_capture.js
node test_frame_probe_shadow.js
node test_preroll_gate.js
node test_stage35_invariants.js
node test_xhamster_sources.js
```

وتتحقق من:
- استخراج عدة روابط جودة من player config.
- التقاط إعداد JSON من fetch/XHR.
- استخراج أربعة تمثيلات HLS من API manifest حتى عندما تكون روابطها بلا امتداد `.m3u8`.
- قراءة خرائط جودة بمفاتيح مثل `240p`, `480p`, `720p`, `1080p`, `1440p`.
- اختبار حقن مراقبة Fetch وXHR المبكر، ونبضات تشخيص كل إطار، وتمرير بيانات iframe عبر جسر WebView أصلي دون كشفها لسكريبتات صفحة الاستضافة، واكتشاف الفيديو داخل Shadow DOM المفتوح، وحماية الترويسات السرية، واستبعاد أجسام الفيديو الثنائية وقراءات الاستجابة غير محدودة الحجم.
- اختبار تكاملي لمحاكاة انتقال استجابة Fetch المبكرة عبر `SCAN_JS` حتى قائمة الجودات وبيانات كل مصدر.
- اختبار حفظ مرشحات API بعد تحليلها مرة واحدة وإعادة استخدامها في فحوص الصفحة المتكررة.
- اختبار بقاء تمثيلين HLS لهما الدقة نفسها لكن يختلفان في AVC/HEVC وSDR/PQ ومعدل الإطارات.
- اختبار بقاء تمثيل مباشر معلن من المشغل عند رفض CDN لفحص Range، دون اختلاق حجم الملف.
- اختبار عدم إسقاط مصدر فيديو صغير عندما تكون بيانات النوع والدقة معلنة وموثوقة.
- اختبار ربط سياق طلب WebView برابط player-declared opaque مطابق حرفيًا، مع منع إعادة استخدام سياق الإعلان أو سياق إطار مختلف أو سياق منتهي الصلاحية.
- فحص CDN: عند رفض Range، يُجرّب HEAD ثم GET دون Range لقراءة الرؤوس فقط؛ لا يُقرأ جسم الفيديو في مرحلة الفحص.
- فحص صلاحية روابط الوسائط الموقعة عبر validto/expires قبل الاتصال، وتفسير 410 كرابط لم يعد متاحًا.
- اختبار تمرير سبب الإعلان ومدة المحتوى المعلنة من iframe إلى التشخيص الأصلي، وعدم بقاء إشارة الإعلان القديمة بعد بدء المحتوى.
- التقاط أسماء قوائم HLS/DASH الحديثة التي حمّلها المشغل فعليًا من Resource Timing، دون قراءة أجسام المقاطع، مع إعطاء أولوية لهذه القوائم قبل روابط Flashvars القديمة.
- كشف الإعلان عندما تختلف مدة عنصر الفيديو بوضوح عن مدة المحتوى المعلنة في flashvars، ومنع احمرار الزر أو فتح خيارات التنزيل أثناء الإعلان، ثم إعادة تعيين الحالة عند انتقال التبويب/المصدر.

## اختبارات Kotlin للمحلل وResolver

تتطلب JDK وKotlin compiler، ولا تتطلب Android SDK لأن المجلد يحتوي بدائل اختبار فقط لـ `MediaMetadataRetriever` و`CookieManager` وطبقة الشبكة.

من جذر المشروع:

```bash
kotlinc \
  app/src/main/java/com/example/videobrowser/Models.kt \
  app/src/main/java/com/example/videobrowser/MediaCapture.kt \
  app/src/main/java/com/example/videobrowser/Hls.kt \
  app/src/main/java/com/example/videobrowser/Formats.kt \
  tools/quality-tests/com/example/videobrowser/NetTestSupport.kt \
  tools/quality-tests/android/media/MediaMetadataRetriever.kt \
  tools/quality-tests/android/webkit/CookieManager.kt \
  tools/quality-tests/HlsMasterTest.kt \
  tools/quality-tests/HlsFormatsResolveTest.kt \
  tools/quality-tests/DirectQualityMetadataTest.kt \
  tools/quality-tests/CaptureHeadersTest.kt \
  tools/quality-tests/MediaUrlExpiryTest.kt \
  -include-runtime -d /tmp/videobrowser-quality-tests.jar

java -cp /tmp/videobrowser-quality-tests.jar com.example.videobrowser.HlsMasterTestKt
java -cp /tmp/videobrowser-quality-tests.jar com.example.videobrowser.HlsFormatsResolveTestKt
java -cp /tmp/videobrowser-quality-tests.jar com.example.videobrowser.DirectQualityMetadataTestKt
java -cp /tmp/videobrowser-quality-tests.jar com.example.videobrowser.CaptureHeadersTestKt
java -cp /tmp/videobrowser-quality-tests.jar com.example.videobrowser.MediaUrlExpiryTestKt

# Net probe fallback test (uses the local JVM HTTP server; no external network)
kotlinc tools/quality-tests/android/content/Context.kt \
  tools/quality-tests/android/net/ConnectivityManager.kt \
  tools/quality-tests/android/webkit/CookieManager.kt \
  app/src/main/java/com/example/videobrowser/Net.kt \
  tools/quality-tests/NetProbeFallbackTest.kt \
  -include-runtime -d /tmp/vb-net-probe-test.jar
java --add-modules jdk.httpserver -jar /tmp/vb-net-probe-test.jar
```

`NetTestSupport.kt` وملفات `android/*` بدائل اختبار فقط؛ لا تنقلها إلى `app/src/main`.

يُفحص ملف JavaScript المحقون عند بداية المستند منفصلًا بواسطة: `kotlinc app/src/main/java/com/example/videobrowser/ApiCaptureScript.kt -d /tmp/vb-early-api-capture.jar`.


## اختبارات Stage 3.6

- قراءة `initials.videoModel.sources` داخل الصفحة الرئيسية وداخل iframe عابر للنطاق.
- قراءة `xplayerSettings.sources` من البنية المعلنة فقط.
- الاحتفاظ بجودات MP4 وHLS الصريحة، بما فيها 1440p/2160p عندما تكون موجودة فعلًا في البيانات المكشوفة.
- عدم فك قيم h264/opaque أو التعامل مع روابط download كتمثيلات تشغيل.
- استبعاد مصادر player-xhamster أثناء الإعلان، ورفض قوائم Resource Timing القديمة السابقة لانتقال الإعلان إلى المحتوى.
- إضافة تفاصيل تشخيصية للمدة المتوقعة والمدة المكتشفة عندما يرفض Content Filter صيغة.
