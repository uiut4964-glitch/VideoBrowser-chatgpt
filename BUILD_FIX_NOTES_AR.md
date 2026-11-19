# إصلاح أخطاء بناء Android — VideoBrowser 1.11.7

عولج خطآ Kotlin اللذان ظهرا في سجل `gradle assembleDebug`:

1. أصبحت `DownloadCenter.requestHeaders(...)` ذات صلاحية `internal` حتى يستطيع الكائن المستقل `Engine` استخدامها، وتم تأهيل الاستدعاءات داخل `Engine` باسم `DownloadCenter.requestHeaders(...)`.
2. أصبح استدعاء حالة yt-dlp في تشخيص `MainActivity` يمرر `this@MainActivity` بدل `this` داخل `buildString`، لأن `this` هناك يشير إلى `StringBuilder` وليس إلى `Context`.

لم تُغيّر هذه الرقعة سلوك استخراج الصيغ أو قواعد التصفية. اجتازت اختبارات JavaScript واختبارات Kotlin المحلية الموضحة في `BUILD_TEST_PACKAGE_AR.md`.

**مهم:** لم يُنفذ Android `assembleDebug` على هذه البيئة؛ لذلك يلزم إعادة تشغيل GitHub Actions للتأكد من نجاح بناء APK كامل بعد الإصلاح. تحذيرات `jvmTarget` و`extractNativeLibs` الواردة في السجل ليستا سببي فشل التجميع السابق.
