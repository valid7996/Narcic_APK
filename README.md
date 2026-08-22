# Narcic NG

کلاینت اندرویدی V2Ray/Xray — فورکی از [v2rayNG](https://github.com/2dust/v2rayNG) با برندینگ و بهبودهای اختصاصی «Narcic».

[![API](https://img.shields.io/badge/API-24%2B-yellow.svg?style=flat)](https://developer.android.com/about/versions/lollipop)
[![Kotlin Version](https://img.shields.io/badge/Kotlin-2.4.0-blue.svg)](https://kotlinlang.org)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Chat on Telegram](https://img.shields.io/badge/Chat%20on-Telegram-brightgreen.svg)](https://t.me/Narcic_Support)

---

## دانلود

آخرین نسخه از بخش [Releases](../../releases) این ریپازیتوری قابل دریافته. سه نسخه‌ی APK منتشر می‌شه:

| فایل | مناسب برای |
|------|-------------|
| `NarcicNG-arm64.apk` | اکثر گوشی‌های جدید (پیشنهادی) |
| `NarcicNG-armeabi.apk` | گوشی‌های قدیمی‌تر (armeabi-v7a) |
| `NarcicNG-universal.apk` | همه‌ی معماری‌ها (حجم بیشتر) |

> پشتیبانی و اعلان‌ها: کانال تلگرام [@Narcic_Support](https://t.me/Narcic_Support)

---

## درباره‌ی پروژه

Narcic NG یک کلاینت V2Ray/Xray برای اندروید است که هسته‌ی [Xray-core](https://github.com/XTLS/Xray-core) و [v2fly core](https://github.com/v2fly/v2ray-core) رو پشتیبانی می‌کنه. این پروژه بر پایه‌ی کد v2rayNG ساخته شده و موارد زیر رو داره:

- **Package name:** `com.narcic.ng`
- **حداقل نسخه‌ی اندروید:** API 24 (Android 7.0)
- **هسته‌ی Xray:** از فورک [`patterniha/AndroidLibXrayLite`](https://github.com/patterniha/AndroidLibXrayLite) به‌صورت خودکار در هر ریلیز دانلود می‌شه
- **تانل هوی سوکس5:** پشتیبانی از [`hev-socks5-tunnel`](https://github.com/heiher/hev-socks5-tunnel) به‌عنوان ساب‌ماژول
- **دو حالت اجرا:** VPN کامل یا فقط پراکسی (بدون نیاز به مجوز VPN)
- **دو Flavor بیلد:** `playstore` و `fdroid`

---

## ساخت پروژه (Build)

```sh
cd V2rayNG
./gradlew assemblePlaystoreDebug   # یا assembleFdroidDebug
```

نیازمندی‌ها: Kotlin 2.4.0، AGP 9.2.1، Android SDK 37، NDK (برای کامپایل `hev-socks5-tunnel`).

فایل AAR هسته‌ی v2ray/xray به‌صورت خودکار توسط ورک‌فلوی گیت‌هاب اکشنز از آخرین ریلیز `patterniha/AndroidLibXrayLite` دانلود و در `V2rayNG/app/libs/` قرار می‌گیره؛ نیازی به کامپایل دستی نیست مگر بخواید نسخه‌ی خودتون رو بسازید.

### تست

```sh
cd V2rayNG && ./gradlew test
```

---

## انتشار نسخه‌ی جدید (Release)

انتشار از طریق ورک‌فلوی گیت‌هاب اکشنز (`.github/workflows/release.yml`) و با پوش کردن یک تگ به فرمت `v*` انجام می‌شه:

```sh
git tag v2.3.3
git push origin v2.3.3
```

این کار به‌صورت خودکار: کد رو چک‌اوت می‌کنه، `libhevtun` و `libv2ray.aar` رو می‌سازه/دانلود می‌کنه، APK امضاشده رو با نام نسخه‌ی تگ می‌سازه و در بخش Releases منتشر می‌کنه.

---

## Geoip و Geosite

- فایل‌های `geoip.dat` و `geosite.dat` در مسیر `Android/data/com.narcic.ng/files/assets` قرار دارن (مسیر ممکنه در برخی گوشی‌ها متفاوت باشه)
- نسخه‌ی تقویت‌شده از طریق قابلیت دانلود داخل اپ و از [این ریپازیتوری](https://github.com/Loyalsoldier/v2ray-rules-dat) دریافت می‌شه (نیاز به پراکسی فعال داره)
- امکان وارد کردن دستی [لیست دامنه‌ها](https://github.com/v2fly/domain-list-community) و [لیست IP](https://github.com/v2fly/geoip) هم وجود داره

---

## حریم خصوصی

سیاست حریم خصوصی این پروژه در فایل [`CR.md`](CR.md) موجوده.

---

## مجوز

این پروژه تحت مجوز [GPLv3](LICENSE) منتشر شده است.

---

## جامعه و پشتیبانی

کانال تلگرام: [@Narcic_Support](https://t.me/Narcic_Support)
