# اختبارات استخراج الجودات — VideoBrowser 1.7

هذه اختبارات محلية للمحلل وطبقة اكتشاف المصادر. لا تتصل بمواقع حقيقية ولا تثبت دعم كل مشغل.

## اختبارات JavaScript

من هذا المجلد شغّل:

```bash
node test_scan_sources.js
node test_scan_api.js
node test_scan_hls_payload.js
node QualityMapConfigTest.js
```

وتتحقق من:
- استخراج عدة روابط جودة من player config.
- التقاط إعداد JSON من fetch/XHR.
- استخراج أربعة تمثيلات HLS من API manifest حتى عندما تكون روابطها بلا امتداد `.m3u8`.
- قراءة خرائط جودة بمفاتيح مثل `240p`, `480p`, `720p`, `1080p`, `1440p`.

## اختبارات Kotlin للمحلل وResolver

تتطلب JDK وKotlin compiler، ولا تتطلب Android SDK لأن المجلد يحتوي بدائل اختبار فقط لـ `MediaMetadataRetriever` و`CookieManager` وطبقة الشبكة.

من جذر المشروع:

```bash
kotlinc \
  app/src/main/java/com/example/videobrowser/Models.kt \
  app/src/main/java/com/example/videobrowser/Hls.kt \
  app/src/main/java/com/example/videobrowser/Formats.kt \
  tools/quality-tests/com/example/videobrowser/NetTestSupport.kt \
  tools/quality-tests/android/media/MediaMetadataRetriever.kt \
  tools/quality-tests/android/webkit/CookieManager.kt \
  tools/quality-tests/HlsMasterTest.kt \
  tools/quality-tests/HlsFormatsResolveTest.kt \
  tools/quality-tests/DirectQualityMetadataTest.kt \
  -include-runtime -d /tmp/videobrowser-quality-tests.jar

java -cp /tmp/videobrowser-quality-tests.jar com.example.videobrowser.HlsMasterTestKt
java -cp /tmp/videobrowser-quality-tests.jar com.example.videobrowser.HlsFormatsResolveTestKt
java -cp /tmp/videobrowser-quality-tests.jar com.example.videobrowser.DirectQualityMetadataTestKt
```

`NetTestSupport.kt` وملفات `android/*` بدائل اختبار فقط؛ لا تنقلها إلى `app/src/main`.
