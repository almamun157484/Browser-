# Mamun Secure Browser & Hub

**Mamun Secure Browser** একটি হালকা, নিরাপদ ও দ্রুতগতির Android Java WebView ব্রাউজার (APK ~2 MB) এবং তার সাথে যুক্ত একটি একক-ফাইল (`index.html`, < 30 KB) GitHub Pages ওয়েব হাব।

---

## প্রধান বৈশিষ্ট্য

### পার্ট A — Android ব্রাউজার অ্যাপ (`com.mamun.duckbrowser`)
- **সব লিংক অ্যাপের ভেতরেই চলে**: DuckDuckGo, DuckDuckGo Mail এবং Hub-এর সব টাইলস সরাসরি অ্যাপের নিজস্ব ট্যাবে খোলে (Chrome বা বাইরের ব্রাউজারে পাঠায় না; এমনকি `intent://` ব্রাউজার রিডাইরেক্টও অ্যাপের ভেতরে রাখা হয়েছে)।
- **DuckDuckGo সার্চ**: অ্যাড্রেস বারে টেক্সট লিখলে `https://duckduckgo.com/?q=`-তে সার্চ হয় এবং ডোমেইন/লিংক লিখলে সরাসরি লোড হয়।
- **মাল্টি-ট্যাব ও Lazy Loading**: সর্বোচ্চ ৩০টি ট্যাব। কেবল দেখানো ট্যাবটিই WebView তৈরি করে। অ্যাপ রিস্টার্টে সব ট্যাব ও নির্বাচিত ট্যাব ফিরে আসে।
- **নিরাপত্তা (HTTPS-First)**: `usesCleartextTraffic="false"`, SSL ত্রুটি কখনোই উপেক্ষা করা হয় না, ফাইল অ্যাক্সেস বন্ধ, রিমোট ডিবাগিং বন্ধ, Safe Browsing চালু।
- **কুকি ও লগইন সংরক্ষণ**: কুকি ও DOM স্টোরেজ সক্রিয় এবং `onPause()` ও `onPageFinished()`-এ ফ্লাশ করা হয়, যাতে লগইন সেশন (Google, Duck Mail, Hub) অ্যাপের **Clear all data** না করা পর্যন্ত টিকে থাকে।
- **Desktop Mode ও Clean User-Agent**: মোবাইল UA থেকে `; wv` ও `Version/x.y` সরানো হয়েছে যাতে Google Sign-In ও DuckDuckGo Mail সহজে কাজ করে।
- **Hub ও `AndroidBridge` সুরক্ষা**: শুধুমাত্র Hub ট্যাবেই `AndroidBridge` (`notify` ও `openTab`) যুক্ত থাকে। মেনুর **Change Hub URL** থেকে **বর্তমান পেজ ব্যবহার** করেও যেকোনো URL-কে Hub সেট করা যায়।

### পার্ট B — Hub ওয়েবসাইট (`index.html`)
- **ইন-অ্যাপ Gmail রিডার**: মেইল ট্যাবে ইনবক্সের তালিকা (সব ইনবক্স / অপঠিত ফিল্টার) দেখায় এবং যেকোনো মেইলে ক্লিক করলে অ্যাপ/হাবের ভেতরেই সম্পূর্ণ ইমেইল (HTML ও Text) পড়া যায়।
- **UI থেকেই Client ID সেটআপ**: `সেটিংস` ট্যাবে গিয়ে সরাসরি Google OAuth Client ID সেভ করা যায় (কোড এডিট না করেই)। ডেটা ক্লিয়ার করার আগ পর্যন্ত এটি সেভ থাকে।
- **যেকোনো Google অ্যাকাউন্ট**: "গুগল সংযুক্ত করুন" ও "অ্যাকাউন্ট বদলান" দিয়ে যেকোনো Gmail অ্যাকাউন্ট সংযুক্ত করা যায়।

---

## ডিপ্লয়মেন্ট ও Client ID সংযোগ নির্দেশিকা

### ১. Google Cloud OAuth Client ID তৈরি (একবারই করতে হবে)
1. [Google Cloud Console](https://console.cloud.google.com/)-এ একটি প্রজেক্ট তৈরি করুন।
2. **APIs & Services → Library** থেকে নিচের দুটি API **Enable** করুন:
   - **Gmail API**
   - **Google Sheets API**
3. **OAuth consent screen** সেটআপ করুন:
   - User Type: **External** নির্বাচন করুন।
   - Scopes যোগ করুন: `https://www.googleapis.com/auth/gmail.readonly` এবং `https://www.googleapis.com/auth/spreadsheets`।
   - **যেকোনো Google অ্যাকাউন্ট দিয়ে ব্যবহার করতে**: **Audience / OAuth consent screen**-এ **Publish App** বাটনে ক্লিক করুন (অথবা Testing মোডে রেখে Test users-এ পছন্দের ইমেইলগুলো যোগ করুন)। *Publish করলে যেকোনো Gmail অ্যাকাউন্ট লগইন করতে পারবে (প্রথমবার "Unverified app → Advanced → Go to..." দেখাতে পারে)।*
4. **Credentials → Create Credentials → OAuth client ID** (Application type: **Web application**) তৈরি করুন:
   - **Authorized JavaScript origins**-এ আপনার Hub ওয়েবসাইটের Origin দিন, যেমন: `https://almamun157484.github.io` (হাবের সেটিংস ট্যাবেই আপনার বর্তমান Origin কপি করার বাটন আছে)।
5. প্রাপ্ত **Client ID** (`...apps.googleusercontent.com`) কপি করে:
   - Hub-এর **সেটিংস → Google API (Client ID) সেটআপ** বক্সে পেস্ট করে **সেভ** করুন (অথবা `index.html`-এর `YOUR_CLIENT_ID.apps.googleusercontent.com` জায়গায় বসান)।

> **সতর্কতা**: কখনোই **Client Secret** কোনো HTML/JS কোডে রাখবেন না বা শেয়ার করবেন না।

### ২. GitHub Pages-এ `index.html` হোস্ট করা
1. `hub/index.html` ফাইলটি `index.html` নামে আপনার GitHub Pages রিপোজিটরিতে (`almamun157484.github.io`) আপলোড করুন।
2. **Settings → Pages** থেকে Branch `main` সেভ করুন।
3. Android অ্যাপের ভেতরে **Hub** বাটনে চাপলেই আপনার সাইট খুলবে এবং তখন DuckDuckGo, DuckDuckGo Mail ও সব লিংক Chrome-এ না গিয়ে অ্যাপের ভেতরেই চলবে!

### ৩. টোকেন ও ব্যাকগ্রাউন্ড সীমাবদ্ধতা
- একবার গুগল অ্যাকাউন্ট কানেক্ট করলে `localStorage`-এ টোকেন ও সংযুক্ত থাকার তথ্য সেভ থাকে এবং **সব ডেটা মুছুন (Clear all data)** বা **বিচ্ছিন্ন** না করা পর্যন্ত কানেক্ট থাকে।
- ব্রাউজার GIS টোকেনের মেয়াদ প্রায় ১ ঘণ্টা; মেয়াদ শেষ হওয়ার ৫ মিনিট আগে বা অ্যাপে ফিরে এলে এটি স্বয়ংক্রিয়ভাবে নবায়ন হয়। (অ্যাপ সম্পূর্ণ বন্ধ থাকা অবস্থায় ব্যাকগ্রাউন্ড নোটিফিকেশনের জন্য ব্যাকএন্ড সার্ভার প্রয়োজন, যা এই ক্লায়েন্ট-সাইড বিল্ডে নেই।)

### ৪. Android APK তৈরি
- **GitHub Actions**: `android-app` ফোল্ডারের ফাইলগুলো GitHub রিপোজিটরিতে পুশ করলেই `.github/workflows/build.yml` **JDK 17 + Gradle 8.7** দিয়ে Debug APK তৈরি করবে।
- **Android Studio**: `android-app` খুলে **Build → Build APK(s)**।

---
Credit by [মামুন](https://www.facebook.com/rtmamun11)
