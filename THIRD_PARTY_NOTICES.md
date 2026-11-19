# Third-party notices

## yt-dlp

- Source: https://github.com/yt-dlp/yt-dlp
- Bundled zipapp: stable release `2026.10.26`.
- License: Unlicense; retain upstream copyright and license materials where applicable.

## CPython Android runtime and curl_cffi

- Runtime source/build project: https://github.com/deniscerri/ytdlnis-packages
- Bundled package: `python-3.14.8`, ARM64 Android helper package, SHA-256 pinned in `tools/prepare_android_runtime.sh`.
- Runtime includes Python 3.14.8 and `curl_cffi 0.17.0` built for Android ARM64.
- The runtime packaging project declares GNU GPL-3.0. Review and satisfy its source, attribution, and redistribution obligations before distributing the APK.
- curl_cffi source: https://github.com/lexiforest/curl_cffi (MIT license); libcurl/BoringSSL and other bundled Python dependencies carry their own notices in the upstream runtime bundle.

## Apache Commons Compress

- Maven: `org.apache.commons:commons-compress:1.27.1`.
- Source: https://commons.apache.org/proper/commons-compress/
- License: Apache License 2.0.
- Used to extract the Python runtime archive while preserving Unix symbolic links.

## Previous wrapper (not used in this version)

`io.github.junkfood02.youtubedl-android:library:0.18.1` is removed from the app dependencies in v1.12.0. It is not packaged by this version.
