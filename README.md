# ShadowBooks — تطبيق أندرويد

مشروع Android أصلي بلغة Kotlin وواجهات Jetpack Compose، لطلاب الصف الثالث الثانوي (المنهج اليمني).

## ما يتضمنه المشروع

- شاشة افتتاحية باسم التطبيق ونص «مناهج الصف الثالث ثانوي» و«إعداد: محمد حجر».
- رفوف عربية من اليمين إلى اليسار لثلاثة عشر مادة.
- شاشة اختيار جزأي الحديث ونسختي الإنجليزية (كتاب الطالب وكتاب الواجب)، مع بقاء عدد أغطية المواد 13.
- قارئ PDF داخلي باستخدام `PdfRenderer`، مع التكبير والتصغير والتنقل المباشر برقم الصفحة.
- أيقونة ShadowBooks مضافة إلى موارد التطبيق.
- سكربت يتحقق من وجود ملفات PDF الخمسة عشر وينزّلها إلى أصول التطبيق قبل البناء؛ لذلك يتضمن APK الناتج جميع الكتب للعمل دون اتصال.

## التشغيل والبناء محلياً

1. افتح مجلد `ShadowBooksAndroid` في Android Studio واستخدم JDK 17.
2. نزّل الكتب إلى مجلد أصول التطبيق:

```bash
bash scripts/download_books.sh
```

3. ابنِ التطبيق بعد تثبيت Android SDK 35:

```bash
chmod +x ./gradlew
./gradlew assembleDebug
```

## ملفات الكتب

يضع السكربت الملفات الخمسة عشر التالية داخل `app/src/main/assets/books/`:

| المادة / النسخة | اسم الملف |
|---|---|
| القرآن الكريم وعلومه | `quran-and-its-sciences.pdf` |
| الإيمان والتزكية | `faith-and-purification.pdf` |
| الفقه | `fiqh.pdf` |
| الحديث الشريف — الجزء الأول | `hadith-part-1.pdf` |
| الحديث الشريف — الجزء الثاني | `hadith-part-2.pdf` |
| السيرة النبوية | `seerah.pdf` |
| النحو والصرف | `grammar-and-morphology.pdf` |
| القراءة والتعبير | `reading-and-expression.pdf` |
| الأدب والنصوص والنقد | `literature-and-criticism.pdf` |
| الكيمياء | `chemistry.pdf` |
| الفيزياء | `physics.pdf` |
| الأحياء | `biology.pdf` |
| الرياضيات | `mathematics.pdf` |
| اللغة الإنجليزية — كتاب الطالب | `english-student-book.pdf` |
| اللغة الإنجليزية — كتاب الواجب | `english-workbook.pdf` |

ملفات PDF مستثناة من مستودع Git لتجنّب رفع نحو 400 MB من الملفات الثنائية. عند البناء محلياً شغّل السكربت أعلاه، وفي GitHub Actions يتم تشغيله تلقائياً قبل البناء؛ بعدها تُضمّن الكتب كلها في APK. يتطلب ذلك أن تكون روابط Google Drive متاحة للتنزيل.

## البناء على GitHub

يوجد Workflow في `.github/workflows/build-android.yml`. بعد رفع المصدر إلى فرع `main`، ينزّل الكتب، يبني APK تجريبياً، ثم يحفظه كـ Artifact باسم `ShadowBooks-debug-apk` في صفحة Actions.

هذه نسخة مصدر Android وليست APK موقّعاً للنشر.
