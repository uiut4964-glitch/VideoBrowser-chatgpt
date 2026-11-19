# VideoBrowser 1.12.0 — حزمة إصلاح Pornhub

هذه حزمة مصدر مشتقة من `VideoBrowser-v1.11.6-stage3.6`، مع الاحتفاظ بإصلاحات الالتقاط المبكر للإعلانات وإعادة المحاولة. الإصدار 1.12.0 يستبدل Runtime yt-dlp القديم (Python 3.8) بمحرك Android ARM64 يضم Python 3.14.8 و`curl_cffi 0.17.0` مع yt-dlp `2026.10.26`.

## الإصلاحات الرئيسية

1. يستخدم yt-dlp `--impersonate chrome` عند تحليل صفحات Pornhub.
2. تُمرر Cookies عبر ملف Netscape مؤقت ومحدود بمضيف صفحة الموقع؛ لا تُمرر في ترويسة `Cookie` ولا تحفظ في `tasks.json`.
3. تُربط كل صيغة مستخرجة بعنوان صفحة الفيديو ومعرّف الصيغة `format_id`.
4. أصبح دمج الصيغ يعتمد على معرّف صيغة yt-dlp عند توفره، لا على URL فقط؛ لذلك لا تُحذف جودات مختلفة لمجرد أنها تشترك في رابط manifest واحد.
5. تنزيل صيغة yt-dlp يعيد تنفيذ yt-dlp على الصفحة الأصلية ومعرّف الصيغة المختارة، باستخدام `curl_cffi` نفسه. هذا يمنع الرجوع إلى فحص/تنزيل `HttpURLConnection` الذي أعاد HTTP 410.
6. مهلة تنزيل صيغة yt-dlp أربع ساعات بدلًا من ست دقائق، حتى لا تُنهى ملفات الفيديو الطويلة مبكرًا.
7. تستمر الصيغ الملتقطة من WebView في استخدام المحرك القديم عند عدم توفر معرّف صيغة yt-dlp.
8. لا تُشارك Cookies أو Authorization بين مضيفي CDN. سياق الترويسات الموروثة يقتصر على ترويسات غير سرية ومضيف CDN نفسه.
9. يبقى التقاط صيغ الفيلم أثناء الإعلان فعالًا، مع استبعاد `currentSrc` الخاص بالإعلان وروابط الإعلانات الصريحة.

## بناء APK

GitHub Actions ينفذ الخطوات التالية تلقائيًا:

- تنزيل حزمة Python ARM64 `python-3.14.8` من `deniscerri/ytdlnis-packages` والتحقق من SHA-256.
- استخراج `libpython.so` و`libpython.zip.so` و`libqjs.so` إلى `app/src/main/jniLibs/arm64-v8a`.
- تنزيل yt-dlp `2026.10.26` والتحقق من SHA-256 ثم وضعه في `app/src/main/assets/yt-dlp`.
- تشغيل اختبارات JavaScript ثم `gradle assembleDebug --no-daemon`.

للبناء المحلي بعد تثبيت Android SDK وGradle:

```bash
bash tools/prepare_android_runtime.sh
gradle assembleDebug --no-daemon
```

من GitHub افتح `Actions` ثم `Build APK` ثم `Run workflow`. بعد نجاح البناء نزّل Artifact باسم `VideoBrowser-APK`.

## الاختبارات الموجودة

- اختبارات التقاط مصادر الصفحة والإطارات وHLS وxHamster ومنع الإعلان.
- اختبار ربط سياق Pornhub بين طلب WebView وروابط الجودة من مضيف CDN نفسه.
- اختبار runtime يثبت وجود `curl_cffi` والتحقق من Chrome impersonation وملف Cookies المؤقت ومسار تنزيل الصيغ المختارة عبر yt-dlp.
- اختبار حفظ format IDs مختلفة عند اشتراكها في URL/manifest واحد.
- اختبارات Kotlin المحلية للمحللات والترويسات وNetProbe.

## ما لم يتم إثباته بعد

لم أتمكن من تشغيل `assembleDebug` أو تجربة APK على جهاز Android في بيئة إعداد المصدر هذه. الاختبارات الثابتة لا تثبت أن كل رابط Pornhub سيُقبل. يجب أن تؤكد GitHub Actions نجاح التجميع أولًا ثم تجربة الجهاز، خصوصًا استخراج 240/480/720/1080 وتنزيل كل صيغة بالفعل.

## التوافق والترخيص

- هذه الحزمة ARM64 فقط.
- يتطلب إعداد runtime اتصال إنترنت في GitHub Actions.
- Python runtime packaging يحمل التزامات GPL-3.0، وApache Commons Compress يحمل Apache-2.0، وcurl_cffi يحمل MIT. راجع `THIRD_PARTY_NOTICES.md` قبل التوزيع.
