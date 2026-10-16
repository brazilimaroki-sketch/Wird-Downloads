# فحوصات المشروع

- `node tests/memory_test.js`: اختبار مقارنة القراءة ونتائج الميكروفون المتكررة والمراجعة.
- `node tests/ui_test.js`: محاكاة DOM للإعدادات والثيم والصلاة والتذكيرات؛ تتطلب حزمة jsdom 26 متاحة لـ Node.

هذه فحوصات للكود ولا تعوّض تجربة الصوت والأذان والويدجت وشريط النظام على هاتف Android.

- `python3 tests/resources_test.py`: سلامة مراجع الموارد وملفات XML وجميع آيات التفاسير المدمجة.

- `node tests/updates_test.js`: مقارنة الإصدارات واشتراط إصدار رسمي يحوي APK.
- `java tests/TestNativeUpdate.java app/src/main/java/com/wird/app/UpdateVersion.java`: اختبار مقارنة النسخ في Java باستخدام JDK.

- `python3 tests/prayer_alarm_test.py`: تنفيذ كود الجدولة الحقيقي مع حدود Android محاكية: الأذونات، إعادة التشغيل، تعطيل الصلوات، السباق عند دقيقة الأذان وتجربة 60 ثانية. لا يحاكي صوت الجهاز أو قيود الشركة المصنعة.

- `python3 tests/adhan_voice_test.py`: الاختيار المحفوظ والمؤذّن المستقل للفجر واستبعاد ملف الفجر من بقية الصلوات والرجوع للافتراضي.

- `python3 tests/prayer_data_test.py`: تجميع محرك Adhan والكود الأصلي مع org.json وحدود Android محاكية؛ اختبار الأيام وفجر الغد والكاش والجدولة والتذكير. يتطلب JDK 17. ملف tests/support/json-20240303.jar للاختبارات فقط ولا يدخل APK.
- `node tests/reader_tools_test.js`: حفظ التلاوة واستكمالها وحذفها وإلغاء التحميل وعزل القراء، والتحقق من بيانات الترجمة والكاش. يتطلب jsdom 26 وfake-indexeddb 6.
- `node tests/reader_audio_test.js`: استخدام الصوت المحفوظ وعدد التكرارات وإلغاء التشغيل أثناء انتظار الكاش أو التكرار. يتطلب jsdom 26.

يمكن تثبيت حزم الاختبار خارج المشروع: `npm install --prefix /tmp/wird-testdeps jsdom@26 fake-indexeddb@6`، ثم ضبط `NODE_PATH=/tmp/wird-testdeps/node_modules` عند تشغيل Node.

- `python3 tests/screen_awake_native_test.py`: تجميع وتشغيل طرق Java الفعلية مع حدود Activity/WebView محاكية للتحقق من إيقاف إبقاء الشاشة مضاءة في الخلفية واستعادته عند الرجوع.
- اختبار الواجهة يتضمن القراءة والأذكار والتسبيح والخروج والخلفية.


75.80: شغّل python3 tests/monthly_prayer_test.py و NODE_PATH=... node tests/monthly_ui_test.js. تشمل التحقق من هوية المدينة، الحصة الكاملة، حدود السنة والسنة الكبيسة، حفظ ذري، دفعات التحميل واستكمالها وتعذر الشبكة، وعدم استعمال إحداثيات بديلة للمدن الجديدة. هذه اختبارات بيانات صناعية، ولا تُغني عن تجربة تنزيل الوزارة على الهاتف.


75.81: python3 tests/prayer_tools_test.py و NODE_PATH=... node tests/prayer_tools_ui_test.js. تُختبر المصادر المستوردة والتصحيح الافتراضي صفر وعدم تراكمه وعزل المدن والتقرير التشخيصي والواجهة. الاختبارات ليست تجربة هاتف أو APK.
