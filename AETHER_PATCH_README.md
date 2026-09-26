# Aether feature patch for Narcic NG

این زیپ فقط شامل فایل‌های **اضافه‌شده** یا **اصلاح‌شده** برای فیچر «افزودن دستی کانفیگ Aether» است.
مسیر هر فایل داخل زیپ دقیقاً همون مسیریه که باید توی ریشه‌ی پروژه‌ی Narcic NG جایگزین/اضافه بشه.

## نحوه‌ی استفاده
محتوای زیپ رو مستقیم روی ریشه‌ی پروژه (کنار پوشه‌ی V2rayNG و .github) اکسترکت/Overwrite کنید.

## فایل‌های جدید (اضافه می‌شوند)
- V2rayNG/app/src/main/java/com/narcic/ng/dto/AetherEndpoint.kt
- V2rayNG/app/src/main/java/com/narcic/ng/dto/AetherRange.kt
- V2rayNG/app/src/main/java/com/narcic/ng/enums/AetherOption.kt
- V2rayNG/app/src/main/java/com/narcic/ng/fmt/AetherFmt.kt
- V2rayNG/app/src/main/java/com/narcic/ng/core/AetherScanner.kt
- V2rayNG/app/src/main/java/com/narcic/ng/core/AetherIdentityManager.kt
- V2rayNG/app/src/main/java/com/narcic/ng/core/AetherDelayTester.kt
- V2rayNG/app/src/main/java/com/narcic/ng/core/AetherCoreManager.kt
- V2rayNG/app/src/main/java/com/narcic/ng/ui/server/AetherEditorRepository.kt
- V2rayNG/app/src/main/java/com/narcic/ng/ui/server/ServerAetherViewModel.kt
- V2rayNG/app/src/main/java/com/narcic/ng/ui/server/ServerAetherActivity.kt
- compile-aether.sh

## فایل‌های اصلاح‌شده (Overwrite می‌شوند)
- V2rayNG/app/src/main/java/com/narcic/ng/dto/entities/ProfileItem.kt
- V2rayNG/app/src/main/java/com/narcic/ng/enums/EConfigType.kt
- V2rayNG/app/src/main/java/com/narcic/ng/AppConfig.kt
- V2rayNG/app/src/main/java/com/narcic/ng/ui/server/ServerUiState.kt
- V2rayNG/app/src/main/java/com/narcic/ng/ui/server/BaseServerActivity.kt
- V2rayNG/app/src/main/java/com/narcic/ng/ui/AboutActivity.kt
- V2rayNG/app/src/main/java/com/narcic/ng/core/CoreOutboundBuilder.kt
- V2rayNG/app/src/main/java/com/narcic/ng/core/CoreConfigContextBuilder.kt
- V2rayNG/app/src/main/java/com/narcic/ng/core/LauncherManager.kt
- V2rayNG/app/src/main/java/com/narcic/ng/helper/MessageHelper.kt
- V2rayNG/app/src/main/java/com/narcic/ng/ui/main/MainActivity.kt
- V2rayNG/app/src/main/java/com/narcic/ng/ui/main/MainDrawer.kt
- V2rayNG/app/src/main/java/com/narcic/ng/extension/ConfigTypeExt.kt
- V2rayNG/app/src/main/java/com/narcic/ng/handler/AngConfigManager.kt
- V2rayNG/app/src/main/java/com/narcic/ng/core/CoreServiceManager.kt
- V2rayNG/app/src/main/java/com/narcic/ng/service/RealPingWorkerService.kt
- V2rayNG/app/src/main/java/com/narcic/ng/ui/main/MainVpnConfigList.kt
- V2rayNG/app/src/main/java/com/narcic/ng/ui/compose/DesignTokens.kt
- V2rayNG/app/src/main/AndroidManifest.xml
- V2rayNG/app/src/main/res/values/strings.xml
- V2rayNG/app/src/main/res/values/arrays.xml
- V2rayNG/app/src/main/res/values-fa/strings.xml
- .gitmodules
- .github/workflows/release.yml

## نکته‌ی مهم درباره‌ی .gitmodules و compile-aether.sh
بعد از اکسترکت کردن، این دستورها رو اجرا کنید تا ساب‌ماژول aether واقعاً clone بشه:

```
git submodule sync
git submodule update --init --recursive
```

## توصیه
بعد از اعمال پچ، حتماً این‌ها رو اجرا کنید تا مطمئن بشید همه‌چیز کامپایل می‌شه:
```
cd V2rayNG
./gradlew :app:testPlaystoreDebugUnitTest
./gradlew assembleDebug
```

## آپدیت بعدی: رفع باگ برگشتن دکمه‌ی اتصال حین اسکن Aether
سه فایل زیر هم به پچ اضافه شدن (این‌ها فایل‌های عمومی UI هستن، نه اختصاصی Aether، ولی برای رفع باگ دکمه لازم بودن):
- V2rayNG/app/src/main/java/com/narcic/ng/ui/main/MainContract.kt
- V2rayNG/app/src/main/java/com/narcic/ng/ui/main/MainViewModel.kt
- V2rayNG/app/src/main/java/com/narcic/ng/ui/main/MainScreen.kt

## آپدیت بعدی: دکمه‌ی + توی لیست کانفیگ‌ها (V2Ray و AmneziaWG)
- V2rayNG/app/src/main/java/com/narcic/ng/ui/main/MainVpnConfigList.kt
حالا دیالوگ "افزودن کانفیگ" که از دکمه‌ی + بالای لیست باز می‌شه، یه گزینه‌ی سوم هم داره:
- توی تب V2Ray: «افزودن دستی کانفیگ Aether»
- توی تب AmneziaWG: «افزودن دستی WARP to WARP»
هر دو همون صفحه‌ی Aether رو باز می‌کنن (چون WARP-in-WARP یکی از پروتکل‌های همون صفحه‌ست)، فقط برچسبشون فرق داره.

## آپدیت بزرگ: Psiphon on chain + Tor برای Aether

این آپدیت از سورس جدید PattNG پورت شد (نه حدسی). شامل:

### فایل‌های کاملاً جدید
- V2rayNG/app/src/main/java/com/narcic/ng/core/AetherCore.kt
- V2rayNG/app/src/main/java/com/narcic/ng/core/PsiphonServerList.kt
- compile-psiphon.sh (باینری psiphon-tunnel-core که خودِ Aether اجراش می‌کنه)
- compile-pt.sh (لایربرد/pluggable transport برای پل‌های Tor)
- fetch-psiphon-servers.sh (لیست سرور امضاشده‌ی عمومی Psiphon، در بیلد دانلود می‌شه)

### فایل‌های به‌روزشده (Overwrite کامل)
- core/AetherCoreManager.kt (بازنویسی کامل، شامل Psiphon و Tor)
- core/AetherDelayTester.kt, core/AetherIdentityManager.kt, core/AetherScanner.kt
- fmt/AetherFmt.kt (اعتبارسنجی فیلدهای جدید)
- dto/entities/ProfileItem.kt (فیلدهای جدید Psiphon/Tor/DNS/ExitLoc/Command)
- enums/AetherOption.kt (enum های AetherPsiphon, AetherTor و...)
- ui/server/ServerUiState.kt, AetherEditorRepository.kt, ServerAetherViewModel.kt, ServerAetherActivity.kt (کل UI صفحه‌ی Aether، شامل بخش Psiphon، Tor، تنظیمات پیشرفته، خط‌فرمان دستی)
- core/CoreOutboundBuilder.kt, core/CoreServiceManager.kt (پورت واقعی از AetherCore.of(profile).port)
- handler/SettingsManager.kt (تابع جدید getLocalProxyPorts)
- ui/compose/FormFields.kt (پارامتر جدید supportingText)
- values/strings.xml, values/arrays.xml (~۴۰ رشته و آرایه‌ی جدید)
- compile-aether.sh (حالا با فلگ --features tor بیلد می‌شه)
- .github/workflows/release.yml (مراحل ساخت/کش Psiphon و pluggable transport اضافه شد)

### نکته‌ی مهم submodule
چون compile-aether.sh عوض شده (--features tor)، کش قبلی aether دیگه استفاده نمی‌شه و یه‌بار از نو بیلد می‌شه (کمی طول می‌کشه، عادیه).

### هنوز باقی‌مونده (اختیاری، غیر build-blocking)
- ترجمه‌ی فارسی رشته‌های جدید Psiphon/Tor (فعلاً فقط انگلیسی نشون داده می‌شن)

### آگاهانه پورت نشد
سیستم AetherDependency/ReloadOutcome از PattNG (تشخیص تداخل چند پروفایل Aether در یک کانفیگ) — چون Aether در Narcic اصلاً نمی‌تونه زنجیره/chain بشه (قبلاً همین‌جا مسدود شده)، این سناریو پیش نمیاد و نیازی به این پیچیدگی نیست.
