# رفع ShadowBooks إلى GitHub

المستودع المحدد:

```text
https://github.com/mhameed716/ShadowBooks.git
```

بعد فك ضغط `ShadowBooksAndroid_GitHubReady.zip`، افتح الطرفية داخل مجلد `ShadowBooksAndroid` ثم نفّذ:

```bash
git init -b main
git add .
git commit -m "Initial ShadowBooks Android project"
git remote add origin https://github.com/mhameed716/ShadowBooks.git
git push -u origin main
```

سجّل الدخول إلى GitHub من جهازك عندما يطلب Git ذلك؛ لا ترسل كلمة المرور أو رمز الوصول في المحادثة.

لا تُرفع ملفات PDF إلى المستودع لتفادي مستودع حجمه يقارب 400 MB. بعد رفع المصدر، يشغّل GitHub Actions سكربت تنزيل الكتب الخمسة عشر، ويضمّنها داخل APK، ثم ينشر APK تجريبياً كـ Artifact في تبويب **Actions**.
