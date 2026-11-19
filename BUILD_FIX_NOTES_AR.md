# إصلاح أخطاء بناء Android — VideoBrowser 1.11.7

عولج خطآ Kotlin اللذان ظهرا في سجل `gradle assembleDebug`:

1. أصبحت `DownloadCenter.requestHeaders(...)` ذات صلاحية `internal` حتى يستطيع الكائن المستقل `Engine` استخدامها، وتم تأهيل الاستدعاءات داخل `Engine` باسم `DownloadCenter.requestHeaders(...)`.
2. أصبح استدعاء حالة yt-dlp في تشخيص `MainActivity` يمرر `this@MainActivity` بدل `this` داخل `buildString`، لأن `this` هناك يشير إلى `StringBuilder` وليس إلى `Context`.

لم تُغيّر هذه الرقعة سلوك استخراج الصيغ أو قواعد التصفية. اجتازت اختبارات JavaScript واختبارات Kotlin المحلية الموضحة في `BUILD_TEST_PACKAGE_AR.md`.

**مهم:** لم يُنفذ Android `assembleDebug` على هذه البيئة؛ لذلك يلزم إعادة تشغيل GitHub Actions للتأكد من نجاح بناء APK كامل بعد الإصلاح. تحذيرات `jvmTarget` و`extractNativeLibs` الواردة في السجل ليستا سببي فشل التجميع السابق.


# VideoBrowser 1.11.8 — إصلاح التقاط المحتوى أثناء الإعلان وإعادة المحاولة

1. أزيل شرط كان يحذف `player-flashvars` و`player-xhamster` و`performance-manifest` من قائمة المصادر عندما يُصنّف عنصر الفيديو إعلانًا. أصبح التطبيق يحتفظ فقط بالمصادر المعلنة صراحة من flashvars/initials أثناء الإعلان؛ وتظل روابط الإعلان المعروفة وResource Timing manifests القديمة مستبعدة.
2. أُطبق السلوك نفسه داخل الإطارات متقاطعة الأصل.
3. يسمح الزر العائم بإظهار صيغ المحتوى المعلنة أثناء الإعلان، ويصبح أحمر بعد تفاعل المستخدم وظهور مصادر محتوى معروفة. لا يُعرض `currentSrc` للإعلان.
4. يُقيد Resolver في حالة الإعلان بالمصادر المعلنة، ويستخدم مدة المحتوى التي أعلنها المشغل لمقارنة مدة كل صيغة.
5. يعالج الضغط اليدوي نتيجة الصفر كحالة قابلة لإعادة المحاولة: تُمسح ذاكرة الفشل ويُعاد تشغيل resolver وyt-dlp بدل عرض نفس التشخيص دون محاولة جديدة.

هذه تغييرات مصدر وليست إثباتًا لنجاح تشغيل Pornhub على جهاز فعلي. يجب إعادة تشغيل `gradle assembleDebug --no-daemon` عبر GitHub Actions واختبار الموقع. لا يوجد APK مبني في هذه الحزمة.


## v1.11.9 — Pornhub request-context mitigation

- Scope Pornhub `Referer` to the page origin root and retain the matching `Origin`.
- Share only allowlisted, non-secret request headers from a recent WebView request with sibling media definitions on the exact same CDN host and same player-page origin.
- Cookies are not copied between request contexts; they continue to be read from Android CookieManager for each target URL. Authorization and custom headers are excluded.
- Diagnostics report how many prepared renditions inherited safe WebView headers from the same CDN host.
- Important limitation: this does not implement curl_cffi or TLS fingerprint impersonation. A persistent HTTP 410 from Pornhub may still require a network backend that can impersonate the browser TLS handshake. This build must not be described as a confirmed Pornhub 410 fix until tested on the user's device.


# VideoBrowser 1.12.0 — استبدال Runtime yt-dlp لمسار Pornhub

- أزيل `youtubedl-android:0.18.1` وبيئة Python 3.8 القديمة.
- أضيف Runtime Python 3.14.8 مع `curl_cffi 0.17.0` من حزمة Android ARM64 منشورة، مع تثبيت SHA-256 في سكربت إعداد البناء.
- يضاف yt-dlp `2026.10.26` zipapp مع SHA-256 مثبت.
- تهيئة Python تشغل `libpython.so` مع `PYTHONHOME` و`LD_LIBRARY_PATH` وملف الشهادات، وتفك runtime ZIP مع الحفاظ على symlinks.
- استخراج Pornhub يستخدم `--impersonate chrome` وملف Cookies Netscape مؤقتًا بدل ترويسة Cookie القديمة.
- كل صيغة yt-dlp تحفظ page URL وformat ID، والتنزيل يعيد طلب نفس الصيغة بواسطة yt-dlp/curl_cffi بدل `HttpURLConnection`.
- تمت إضافة `tools/prepare_android_runtime.sh` لإعداد ملفات runtime والتحقق من سلامتها في CI أو محليًا.

**لم يتم بناء APK أو اختبار Pornhub على جهاز حقيقي في بيئة إعداد المصدر. يلزم تشغيل GitHub Actions واختبار 240/480/720/1080 وتنزيل ملف فعلي قبل إعلان إصلاح 410 ناجحًا.**

## 1.12.0 — إصلاح فقدان خيارات الجودة ومسار التنزيل

- لم يعد `YtDlpExtractor` يدمج صيغ yt-dlp اعتمادًا على URL وحده؛ يحافظ على `format_id` المختلف حتى إن اشتركت الجودات في URL/manifest نفسه.
- ينفذ `mergeFormatLists` في واجهة التطبيق عملية الدمج حسب `pageUrl + format_id` للصيغ المستخرجة، مع إزالة نسخ WebView المكررة من URL نفسه عند وجود صيغة yt-dlp مقابلة.
- زيد حد تنزيل yt-dlp إلى أربع ساعات، وأصلح استعادة حالة مهمة yt-dlp بعد إغلاق التطبيق، كما أزيلت معاملة التقدم الخاصة بـHLS من مهام yt-dlp.
- أصبح تشخيص الخطأ يحتفظ برسالة أطول بعد تنقيح الروابط والاعتمادات السرية.
