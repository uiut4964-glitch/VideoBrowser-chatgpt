# اختبارات الالتقاط واستخراج الجودات — VideoBrowser 1.11.0 / Stage 3

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
```

وتتحقق من:
- استخراج عدة روابط جودة من player config.
- التقاط إعداد JSON من fetch/XHR.
- استخراج أربعة تمثيلات HLS من API manifest حتى عندما تكون روابطها بلا امتداد `.m3u8`.
- قراءة خرائط جودة بمفاتيح مثل `240p`, `480p`, `720p`, `1080p`, `1440p`.
- اختبار حقن مراقبة Fetch وXHR المبكر، وتمرير بيانات iframe عبر جسر WebView أصلي دون كشفها لسكريبتات صفحة الاستضافة، وحماية الترويسات السرية، واستبعاد أجسام الفيديو الثنائية وقراءات الاستجابة غير محدودة الحجم.
- اختبار تكاملي لمحاكاة انتقال استجابة Fetch المبكرة عبر `SCAN_JS` حتى قائمة الجودات وبيانات كل مصدر.
- اختبار حفظ مرشحات API بعد تحليلها مرة واحدة وإعادة استخدامها في فحوص الصفحة المتكررة.
- اختبار بقاء تمثيلين HLS لهما الدقة نفسها لكن يختلفان في AVC/HEVC وSDR/PQ ومعدل الإطارات.
- اختبار بقاء تمثيل مباشر معلن من المشغل عند رفض CDN لفحص Range، دون اختلاق حجم الملف.
- اختبار عدم إسقاط مصدر فيديو صغير عندما تكون بيانات النوع والدقة معلنة وموثوقة.

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
  -include-runtime -d /tmp/videobrowser-quality-tests.jar

java -cp /tmp/videobrowser-quality-tests.jar com.example.videobrowser.HlsMasterTestKt
java -cp /tmp/videobrowser-quality-tests.jar com.example.videobrowser.HlsFormatsResolveTestKt
java -cp /tmp/videobrowser-quality-tests.jar com.example.videobrowser.DirectQualityMetadataTestKt
java -cp /tmp/videobrowser-quality-tests.jar com.example.videobrowser.CaptureHeadersTestKt
```

`NetTestSupport.kt` وملفات `android/*` بدائل اختبار فقط؛ لا تنقلها إلى `app/src/main`.

يُفحص ملف JavaScript المحقون عند بداية المستند منفصلًا بواسطة: `kotlinc app/src/main/java/com/example/videobrowser/ApiCaptureScript.kt -d /tmp/vb-early-api-capture.jar`.
