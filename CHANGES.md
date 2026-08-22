# خلاصه‌ی تغییرات (Narcic APK)

این فایل خلاصه‌ی فنی تغییراتی‌ست که برای رفع ۵ مشکل مطرح‌شده روی پروژه اعمال شد.
مسیر فایل‌ها نسبت به `V2rayNG/app/src/main/java/com/narcic/ng/` نوشته شده.

---

## ۱. سابسکریپشن بعد از اضافه‌شدن در لیست نمی‌آمد

**علت:** تابع افزودن سابسکریپشن، تسکِ به‌روزرسانی لیست گروه‌ها را صدا می‌زد ولی
منتظرش نمی‌ماند (`join()` نمی‌کرد) و در صفحه‌ی سابسکریپشن‌ها هم هیچ نشانه‌ی
بارگذاری نمایش داده نمی‌شد؛ روی اینترنت کند به نظر می‌رسید هنگ کرده.

**تغییرات:**
- `ui/main/MainViewModel.kt` → `addSubscriptionFromText()`: حالا `setupGroupTab(forceRefresh = true).join()`
  را صدا می‌زند تا لیست واقعاً به‌روز شده باشد قبل از این‌که loading خاموش شود.
- همین اصلاح برای اطمینان بیشتر روی `refreshSubscription()` (تازه‌سازی یک ساب) هم اعمال شد.
- `ui/main/SubscriptionsScreen.kt`: پارامتر `isAdding` اضافه شد؛ حین افزودن، دکمه‌ی
  «+ افزودن» غیرفعال می‌شود و متنش به «در حال افزودن…» تغییر می‌کند، و نوار بالای صفحه
  spinner لودینگ نشان می‌دهد (`AppTopBar(isLoading = ...)`).
- `ui/main/MainScreen.kt`: `isLoading` از ViewModel به `SubscriptionsScreen` پاس داده می‌شود.

---

## ۲. گزینه‌ی «خودکار» و تنظیم سقف پینگ

**علت:** وقتی نتیجه‌ی تستی موجود نبود، `autoConnect()` کل «همه‌ی سابسکریپشن‌ها» را
تست می‌کرد (`testAllRealPing()` سراسری)، نه فقط سابسکریپشن انتخابی — همین وقت‌گیر بودنش.

**تغییرات:**
- `ui/main/MainViewModel.kt`:
  - تابع جدید `testGroupRealPing(groupId)`: فقط کانفیگ‌های همان گروه را تست می‌کند.
  - `autoConnect()` حالا از `testGroupRealPing()` استفاده می‌کند، نه تست سراسری.
  - تابع جدید `checkAutoConnectEarlyStop(guid)`: اگر سقف پینگ فعال باشد، به‌محض
    رسیدن نتیجه‌ی هر کانفیگ (`MeasureConfigSuccess`)، اگر پینگش در بازه‌ی مجاز باشد
    بلافاصله تست را متوقف کرده و وصل می‌شود.
- `ui/main/MainServiceEvent.kt` و `ui/main/MainRepository.kt`: رویداد
  `MeasureConfigSuccess` حالا شناسه‌ی کانفیگ (`guid`) را هم حمل می‌کند (قبلاً نداشت).
- `AppConfig.kt`: دو کلید تنظیمات جدید: `PREF_AUTO_CONNECT_PING_LIMIT_ENABLED` و
  `PREF_AUTO_CONNECT_PING_LIMIT_MS`.
- `handler/SettingsChangeManager.kt`: این دو کلید به‌عنوان تنظیمات صرفاً-رابط‌کاربری
  علامت‌گذاری شدند (نیاز به ری‌استارت سرویس VPN ندارند).
- **صفحه‌ی جدید:** `ui/settings/AutoConnectSettingsActivity.kt` — سوئیچ «محدود کردن
  پینگ برای اتصال خودکار» + فیلد عددی «حداکثر پینگ (میلی‌ثانیه)».

---

## ۳. دکمه‌ی «تست» فقط باید همان سابسکریپشن را تست بگیرد

**علت:** دکمه‌ی «تست دوباره» در صفحه‌ی انتخاب اتصال، اکشن سراسری
`MainAction.TestRealAllServers` را صدا می‌زد.

**تغییرات:**
- `ui/main/MainContract.kt`: اکشن جدید `MainAction.TestGroupServers(groupId)`.
- `ui/main/MainViewModel.kt`: این اکشن به `testGroupRealPing(groupId)` وصل شد.
- `ui/main/MainScreen.kt`: `onRetest` حالا
  `onAction(MainAction.TestGroupServers(uiState.selectedGroupId))` صدا می‌زند.
- (منوی «Manage configs» در Drawer که تست سراسری همه‌ی ساب‌ها را انجام می‌دهد
  دست‌نخورده باقی ماند، چون آن یک قابلیت جداگانه و عمدی‌ست.)

---

## ۴. حذف سه‌نقطه‌ی بالا و انتقال به «تنظیمات»

**تغییرات:**
- **صفحه‌ی جدید:** `ui/settings/SettingsHubActivity.kt` — هاب تنظیمات با دو بخش:
  1. «تنظیمات» (همان صفحه‌ی فنی قبلی) + «تنظیم اتصال خودکار» (مورد ۲)
  2. Per-app settings / Asset files / Check for update / About
- `ui/main/MainDrawer.kt`: دو گروه آیتم (per_app_proxy, user_asset, settings) و
  (check_update, about) حذف شدند؛ Drawer فقط «Import config» و «Manage configs» را نگه داشت.
- `ui/main/MainActivity.kt`: مسیر `"settings"` حالا `SettingsHubActivity` را باز
  می‌کند (به‌جای مستقیم `SettingsActivity`)؛ مسیرهای مرده‌ی per_app_proxy/user_asset/
  check_update/about از `navigateTo()` حذف شدند (این‌ها حالا مستقیماً از داخل
  `SettingsHubActivity` باز می‌شوند).
- `AndroidManifest.xml`: دو Activity جدید (`SettingsHubActivity`,
  `AutoConnectSettingsActivity`) اضافه شدند.

---

## ۵. گزینه‌ی حذف سابسکریپشن

**تغییرات:**
- `ui/main/MainContract.kt`: اکشن جدید `MainAction.RemoveSubscriptionGroup(groupId)`.
- `ui/main/MainDataSource.kt` / `ui/main/MainRepository.kt`: تابع جدید
  `removeSubscription(groupId)` که از تابع موجود
  `SettingsManager.removeSubscriptionWithDefault()` استفاده می‌کند.
- `ui/main/MainViewModel.kt`: تابع `removeSubscriptionGroup(groupId)` (با دیالوگ
  تأیید، و محافظت در برابر حذف تصادفی کارت «همه‌ی کانفیگ‌ها»/فیلتر all).
- `ui/main/SubscriptionsScreen.kt`: آیتم «حذف سابسکریپشن» به منوی «گزینه‌ها ⋮» هر
  کارت اضافه شد (فقط برای سابسکریپشن‌های واقعی، نه کارت فیلتر «همه»).
- `ui/main/MainDialogs.kt` و `ui/main/MainScreen.kt`: دیالوگ تأیید حذف
  (`confirm_delete_subscription_group`) وصل شد.

---

## نکته‌ی مهم برای ساخت (Build)

این تغییرات در محیطی بدون دسترسی به اینترنت نوشته و به‌دقت بازبینی شدند (تطابق
امضای توابع، importها، براکت‌ها)، ولی **کامپایل واقعی با Gradle انجام نشده**.
لطفاً پروژه را یک‌بار در Android Studio باز و Build کنید؛ اگر خطای کوچکی
(مثلاً یک import فراموش‌شده) باقی مانده باشد، معمولاً IDE خودش دقیقاً محل آن را
نشان می‌دهد و به‌راحتی قابل رفع است.
