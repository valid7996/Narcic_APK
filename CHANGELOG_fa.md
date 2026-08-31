# گزارش تغییرات — بازطراحی UI برای دو موتور (AmneziaWG / V2Ray)

این فایل خلاصه‌ی دقیق کاری‌ست که روی پروژه‌ی `com.narcic.ng` انجام شد. کد واقعی پروژه (نه فرضی) بررسی و باهاش تطبیق داده شد، چون بعضی جاها اسم فیلدها/کلاس‌ها با چیزی که در توضیح اولیه اومده بود کمی فرق داشت.

## تطبیق‌های مهم با کد واقعی (نه فرضی)
- `AmneziaWG` روی `EConfigType.WIREGUARD` **و** `EConfigType.AMNEZIAWG` هر دو منطبق شد (نه فقط WIREGUARD) — این دو تایپ جدا هستن ولی هر دو خانواده‌ی موتور AmneziaWG محسوب می‌شن.
- اسم واقعی state، `MainUiState` هست (نه `UiState`) و `isLoading` از `BaseViewModel` می‌آد، نه از خود `MainUiState`.
- گروه‌ها از نوع `GroupMapItem` (فیلدهای `id`/`remarks`) هستن؛ گروه «پیش‌فرض» همون سابسکریپشنیه که `id == AppConfig.DEFAULT_SUBSCRIPTION_ID`.
- **منطق ViewModel دست نخورده** — طبق خواسته‌ی خودتون. سوییچ موتور (AWG/V2Ray) کاملاً در لایه‌ی UI پیاده شده: لیست هر گروه که از `mainViewModel.serversForGroup(id)` می‌گیریم، سمت Compose بر اساس `configType` فیلتر می‌شه.
- «در حال اتصال» (`isConnecting`) یک state محلی در `MainScreen` است (چون خود ViewModel این وضعیت رو جدا expose نمی‌کنه) — دقیقاً همون چیزی که خودتون هم در Part D4 پیش‌بینی کرده بودید.
- صفحه‌ی آمار به‌جای داده‌ی فرضی، از `TrafficStatsManager` واقعی (که از قبل در پروژه بود و توسط `NotificationManager` تغذیه می‌شه) استفاده می‌کنه. بخش «به تفکیک سرور» و «سابقه‌ی تست سرعت» که در اسپک اولیه بود حذف شد چون داده‌ای براش در پروژه ذخیره نمی‌شه؛ ساختن عدد الکی گمراه‌کننده بود.

## فایل‌های جدید (`ui/compose`)
- `DesignTokens.kt` — شیء `Nc` (پالت کامل بخش B1) + `accentFor(isAwg)` برای رنگ فعال هر موتور.
- `GooLoader.kt` — لودر متابال با blur+threshold (API31+)، fallback ساده در نسخه‌های پایین‌تر.
- `GooOverlay.kt` — اورلی تمام‌صفحه برای «تست گروه».
- `SignalBars.kt` / `Sparkline.kt` — آنتن پینگ و نمودار زنده‌ی سرعت.
- `EngineSwitch.kt` — سوییچ لغزان AmneziaWG/V2Ray، هنگام اتصال قفل می‌شه.
- `GroupTabsPill.kt` — تب‌های پیل‌شکل گروه؛ شمارنده‌ی هر تب زنده و بر اساس موتور فعال فیلتر می‌شه.
- `StatusPill.kt` — پیل کوچک وضعیت بالای دکمه‌ی اتصال.

## فایل‌های جدید (`ui/main`)
- `ServerCard.kt` — کارت سرور کاملاً بصری طبق اسپک (رادیو/رتبه، آدرس مونو، آنتن، بج پروتکل، ادیت/اشتراک/حذف).

## فایل‌های بازنویسی‌شده
- `ConnectHero.kt` — دکمه‌ی اتصال با accent پویا، GooLoader هنگام اتصال، ripple/glow/ring دقیقاً طبق تایمینگ‌های Part B3.
- `ConnectionStatsPanel.kt` — کارت شیشه‌ای پرچم/کشور/IP + اسپارک‌لاین زنده + گرید ۴تایی.
- `MainVpnConfigList.kt` — تب داخلی گروه حذف شد (چون به `MainScreen` منتقل شد)، فیلتر موتور اضافه شد، ردیف‌ها الان از `ServerCard` استفاده می‌کنن، بج‌های پروتکل با پالت `Nc` هماهنگ شدن.
- `MainVpnBottomNav.kt` — از `NavigationBar` متریال به نوار شناور (radius24، ارتفاع66، ایندیکیتور گرادیانی) تبدیل شد.
- `StatisticsActivity.kt` — سه کارت + نمودار میله‌ای ۷روزه (گرادیان‌های دقیق اسپک) + لیست روزها، همه از داده‌ی واقعی.
- `MainScreen.kt` — سیم‌کشی همه‌چیز: `StatusPill → EngineSwitch → ConnectHero → ConnectionStatsPanel → GroupTabs → MainServerListSection`، بافر ۲۸نقطه‌ای اسپارک‌لاین، `GooOverlay` برای تست گروه.

## چیزهایی که عمداً انجام نشد (برای ادامه‌ی کار)
طبق Part D7 خودتون این‌ها «بدون کد کامل، فقط مقادیر» بودن و اولویت پایین‌تری داشتن؛ به‌خاطر حجم کار در این پاس دست‌نخورده موندن:
- ریستایل کامل `MainTopBar` (دکمه‌های شیشه‌ای ۴۲dp، چرخش آیکون sync)
- `MainDrawerContent`، `AddSubscriptionDialog`، `DeleteConfirmDialog`، `SubscriptionsScreen` — این‌ها فعلاً با پالت رنگی قدیمی (`MaterialTheme`/Aurora) کار می‌کنن، نه `Nc`.

اگه بخواید همین مسیر رو روی این فایل‌ها هم ادامه بدم، بگید تا پاس بعدی رو انجام بدم.

## نکته‌ی مهم درباره‌ی build
اینجا به `dl.google.com`/Maven مرکزی دسترسی نداشتم، پس نتونستم `gradle build` واقعی بگیرم. در عوض:
- تمام فایل‌های تغییریافته/جدید رو تک‌تک بررسی کردم (import، امضای تابع، مقادیر واقعی enum/data class) و با کد واقعی پروژه (نه با فرض) تطبیق دادم.
- تعادل `{ } ( ) [ ]` همه‌ی فایل‌ها رو به‌صورت خودکار چک کردم.
- منطق ViewModel/Repository را اصلاً لمس نکردم (diff صفر).

پیشنهاد می‌کنم قبل از هر چیز یک بار `./gradlew :V2rayNG:app:compileFullDebugKotlin` (یا وریانت مشابه) رو لوکال بگیرید تا هرگونه خطای تایپی که با چشم از قلم افتاده رو گیر بیارید.
