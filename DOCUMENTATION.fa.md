# مستندات برنامه «منتظر مهدی» (Await Mahdi)

این سند برای توسعه‌دهنده‌ای نوشته شده که می‌خواهد در مدت کوتاه با ساختار کد و نحوه‌ی اجرای برنامه آشنا شود.
برنامه یک شمارنده‌ی صلوات آنلاین/آفلاین است که به صورت PWA کار می‌کند و شامل دو قسمت اصلی است:

- `backend/` — سرویس API مبتنی بر **FastAPI** + **PostgreSQL**
- `frontend/` — اپلیکیشن **React + Vite** به شکل PWA

---

## ۱. نحوه‌ی اجرای برنامه (Run)

### پیش‌نیازها

- Python 3.10 یا بالاتر
- Node.js 18 یا بالاتر (همراه با `npm`)
- PostgreSQL 13 یا بالاتر (لوکال یا ریموت)

### مرحله ۱ — آماده‌سازی پایگاه‌داده

در `backend/database.py` رشته‌ی اتصال به صورت زیر تعریف شده است:

```python
SQLALCHEMY_DATABASE_URL = "postgresql://postgres:123456@localhost/taajil"
```

قبل از اجرای بک‌اند باید دیتابیس `taajil` ساخته شود. برای ساخت دیتابیس اسکریپت کمکی در ریشه‌ی پروژه هست:

```bash
python create_db.py
```

این اسکریپت با کاربر `postgres` و پسورد `123456` به PostgreSQL متصل می‌شود و در صورت نبودن، دیتابیس `taajil` را می‌سازد. اگر یوزر/پسورد دیگری دارید، همین مقادیر را در `create_db.py` و `backend/database.py` اصلاح کنید.

برای چک‌کردن اتصال و لیست دیتابیس‌های موجود می‌توانید از `list_databases.py` هم استفاده کنید.

### مرحله ۲ — اجرای بک‌اند

```bash
cd backend
python -m venv .venv
source .venv/bin/activate        # در ویندوز: .venv\Scripts\activate
pip install -r requirements.txt
```

سپس از **یک پوشه‌ی بالاتر از `backend/`** (یعنی ریشه‌ی پروژه) اجرا کنید، چون در `main.py` از ایمپورت‌های نسبی استفاده شده (`from . import crud, models, ...`):

```bash
uvicorn backend.main:app --host 0.0.0.0 --port 8000 --reload
```

در اولین بالا آمدن سرویس، این خط جداول را به‌صورت خودکار می‌سازد:

```python
models.Base.metadata.create_all(bind=engine)
```

مستندات تعاملی Swagger روی `http://localhost:8000/docs` در دسترس است.

### مرحله ۳ — اجرای فرانت‌اند

```bash
cd frontend
npm install
npm run dev
```

فرانت روی پورت پیش‌فرض Vite (معمولاً `5173`) بالا می‌آید و چون در `vite.config.js` گزینه‌ی `server.host: true` فعال است، از روی شبکه‌ی محلی و موبایل هم قابل دسترسی است.

آدرس API به صورت خودکار بر اساس همان `hostname` صفحه‌ی فرانت ساخته می‌شود (`src/api.js`):

```js
return `http://${hostname}:8000`;
```

اگر می‌خواهید آدرس API را override کنید، متغیر محیطی `VITE_API_URL` را قبل از `npm run dev` یا `npm run build` ست کنید.

### ساخت نسخه‌ی Production فرانت

```bash
cd frontend
npm run build      # خروجی در frontend/dist
npm run preview    # پیش‌نمایش لوکال از dist
```

---

## ۲. ساختار بک‌اند

بک‌اند یک سرویس **FastAPI** است که داده‌ها را با **SQLAlchemy** در **PostgreSQL** نگه می‌دارد. معماری آن سبک و لایه‌ای است و فقط چند فایل دارد:

| فایل | نقش |
|---|---|
| `backend/database.py` | ساخت `engine`، `SessionLocal` و `Base` برای SQLAlchemy. |
| `backend/models.py` | مدل‌های ORM: `User` و `Salavat`. |
| `backend/schemas.py` | اسکیماهای Pydantic برای ورودی/خروجی API. |
| `backend/auth.py` | هش پسورد با `bcrypt` و صدور JWT با `python-jose`. |
| `backend/crud.py` | لایه‌ی دسترسی به داده (ایجاد کاربر، ثبت صلوات، محاسبه‌ی آمار). |
| `backend/main.py` | تعریف اپ FastAPI، CORS، وابستگی‌ها و اندپوینت‌ها. |

### مدل داده

دو جدول اصلی داریم:

- **`users`**: ثبت کاربران رجیستر شده.
  - `id` (UUID رشته‌ای، Primary Key)
  - `username` (موبایل یا ایمیل — یکتا)
  - `hashed_password` (با `bcrypt`)
  - `is_active`, `created_at`

- **`salavats`**: هر رکورد یک «باکت» شمارش صلوات است.
  - `user_id` (اختیاری — اگر کاربر لاگین بود)
  - `guest_uuid` (اختیاری — برای کاربر مهمان که UUID در کوکی نگه می‌دارد)
  - `count`
  - `created_at`

ارتباط: `User.salavats ↔ Salavat.owner` به صورت one-to-many.

### منطق «باکت ۱۵ دقیقه‌ای»

در `crud.add_salavat_log` برای هر کاربر/مهمان، اگر در ۱۵ دقیقه‌ی اخیر رکوردی داشته باشد، مقدار جدید به همان رکورد اضافه می‌شود؛ در غیر این‌صورت یک رکورد جدید ساخته می‌شود. این کار برای کم‌کردن تعداد Row و داشتن خلاصه‌ی مفید از فعالیت کاربر انجام شده است.

### احراز هویت

- پسوردها با `passlib[bcrypt]` هش می‌شوند.
- توکن JWT با `python-jose`، الگوریتم `HS256` و اعتبار ۳۰ روز صادر می‌شود (`auth.py`).
- کلاینت در Authorization header به شکل `Bearer <token>` ارسال می‌کند.
- **توجه‌ی مهم امنیتی:** `SECRET_KEY` داخل `auth.py` و پسورد دیتابیس داخل `database.py` هاردکد شده‌اند؛ برای محیط پروداکشن حتماً باید به Environment Variable منتقل شوند.

### اندپوینت‌ها

| متد | مسیر | شرح |
|---|---|---|
| `POST` | `/register` | ساخت کاربر جدید + لاگین خودکار، برمی‌گرداند: `access_token`, `user_id`, `username`. |
| `POST` | `/login` | لاگین با `username`/`password` و دریافت JWT. |
| `POST` | `/heartbeat` | اندپوینت اصلی. ترکیب ثبت صلوات + گرفتن آمار در یک درخواست. اگر `count > 0` باشد ذخیره و سپس `Stats` جدید برگشت می‌زند. توکن اختیاری است (از هدر `Authorization`). |
| `GET` | `/stats` | برگرداندن آمار بدون ثبت چیزی. (برای سازگاری قدیمی.) |

خروجی `Stats` چهار عدد است: `today_total`, `all_time_total`, `user_today`, `user_total`.

### CORS

در `main.py` لیست `origins` شامل `localhost:5173/5174`، چند IP شبکه‌ی محلی و `*` برای توسعه است. برای پروداکشن باید این لیست محدود شود.

### وابستگی‌ها

```
fastapi
uvicorn
sqlalchemy
psycopg2-binary
pydantic
passlib[bcrypt]
python-jose[cryptography]
python-multipart
```

---

## ۳. ساختار فرانت‌اند

فرانت یک SPA نوشته‌شده با **React 19 + Vite 5** است که به صورت **PWA** (با `vite-plugin-pwa`) پکیج می‌شود. زبان رابط فارسی، جهت `rtl` و با قابلیت نصب روی موبایل.

### ساختار پوشه

```
frontend/
├── index.html                # پوسته‌ی HTML + اسپلش اولیه + متادیتای PWA
├── vite.config.js            # پیکربندی Vite + VitePWA (Manifest + Workbox)
├── package.json
├── public/
│   ├── icons/                # آیکون‌های PWA (انواع سایز + maskable)
│   ├── offline.html
│   └── favicon.ico
└── src/
    ├── main.jsx              # Entry point، رجیستر Service Worker
    ├── App.jsx               # منطق اصلی UI
    ├── api.js                # کلاینت Axios + interceptor توکن
    ├── AnimatedCounter.jsx   # انیمیشن شمارش اعداد
    ├── InstallPrompt.jsx     # UI پیشنهاد نصب PWA
    ├── InstallPrompt.css
    ├── index.css / App.css
    └── assets/               # تصاویر (از جمله دکمه‌ی صلوات)
```

### کتابخانه‌های مهم

- `react` / `react-dom` 19
- `axios` — کلاینت HTTP
- `js-cookie` — نگه‌داری توکن، `guest_uuid` و کش آمار
- `uuid` — ساخت UUID برای کاربر مهمان
- `react-router-dom` (نصب شده ولی فعلاً مسیریابی پیچیده ندارد)
- `vite-plugin-pwa` — ساخت Service Worker و Manifest

### جریان کار UI

فایل `src/App.jsx` تمام منطق را در یک کامپوننت نگه می‌دارد:

1. **راه‌اندازی اولیه** (داخل `useEffect`):
   - اگر کوکی `guest_uuid` نبود، یک UUID جدید می‌سازد و در کوکی (۳۶۵ روز) ذخیره می‌کند.
   - از کوکی‌های `token` و `user_info` اطلاعات کاربر لاگین را بازیابی می‌کند.
   - یک `heartbeat(0)` اولیه برای گرفتن آمار سرور می‌فرستد.
   - یک `setInterval` هر ۱۲ ثانیه `heartbeat(localCount)` را صدا می‌زند.
   - هندلر `beforeunload` قبل از بستن تب، اعداد ذخیره‌نشده را flush می‌کند.

2. **کلیک روی دکمه‌ی صلوات**:
   - فقط `localCount` (state کلاینت) یک واحد زیاد می‌شود — درخواستی فوری زده نمی‌شود.
   - یک انیمیشن روشن‌شدن (`isClicked`) برای فیدبک بصری اجرا می‌شود.

3. **Heartbeat** (در `api.js`):
   - `POST /heartbeat { count, guest_uuid }` با توکن (اگر باشد) در هدر.
   - پاسخ سرور شامل `Stats` جدید است؛ همان مقدار در کوکی `stats` کش می‌شود تا بار اول بدون تماس با سرور هم عددی برای نمایش داشته باشیم.
   - اگر `/heartbeat` روی سرور ۴۰۴ بدهد، به عقب fallback می‌کند روی `POST /sync` + `GET /stats`.

4. **محافظ مونوتونیک**: در `updateStats` اگر `all_time_total` جدید از مقدار فعلی کمتر بود نادیده گرفته می‌شود تا عدد نمایش داده‌شده هیچ‌وقت پایین نیاید (مثلاً روی پاسخ قدیمی/خراب).

5. **ورود/ثبت‌نام** (`handleAuthSubmit`): بعد از دریافت JWT، توکن و `user_info` در کوکی (۳۰ روز) ذخیره و بلافاصله یک `heartbeat` جدید زده می‌شود تا آمار کاربر شخصی نمایش داده شود.

6. **خروج** (`logout`): اگر مقدار لوکال ذخیره‌نشده دارد اول همان را روی `guest_uuid` flush می‌کند، بعد توکن را پاک می‌کند.

### `AnimatedCounter`

یک کامپوننت ساده که بر پایه‌ی `requestAnimationFrame` عدد را از مقدار قبلی به مقدار جدید انیمیت می‌کند. برای تغییرات کوچک (`<= 5`) از انیمیشن کوتاه ۱۰۰ms و برای تغییرات بزرگ (sync سرور) از `duration` پیش‌فرض (۵ ثانیه) استفاده می‌کند. خروجی با `toLocaleString('fa-IR')` به ارقام فارسی تبدیل می‌شود.

### `InstallPrompt`

کامپوننت مستقل برای پیشنهاد نصب PWA. سناریوهای Android/Chromium (با `beforeinstallprompt`) و iOS (نمایش راهنمای Add to Home Screen) را جدا مدیریت می‌کند و تاریخ dismiss را در `localStorage` نگه می‌دارد تا تا ۲۴ ساعت دوباره نمایش داده نشود.

### PWA و آفلاین

در `vite.config.js`:

- `VitePWA` با `registerType: 'autoUpdate'` ست شده است.
- **Manifest** کامل (نام فارسی، `dir: 'rtl'`، آیکون‌های ۷۲ تا ۵۱۲ + maskable) درج می‌شود.
- **Workbox runtime caching**:
  - Google Fonts (stylesheet: `StaleWhileRevalidate`, webfont: `CacheFirst`).
  - تصاویر: `CacheFirst` با حداکثر ۸۰ آیتم، ۶۰ روز.
  - اسکریپت/استایل/ورکر: `StaleWhileRevalidate`.
  - درخواست‌های `/api/*`: `NetworkFirst` با تایم‌اوت ۶ ثانیه و کش ۲۴ ساعته.
- `navigateFallback: '/index.html'` برای SPA routing در حالت آفلاین.
- یک Splash ساده‌ی دستی در `index.html` تا قبل از هیدرات‌شدن React نمایش داده شود (حداکثر ۵ ثانیه).

### استایل و i18n

- کل `index.html` با `lang="fa"` و `dir="rtl"`.
- استایل‌ها در `index.css`, `App.css`, `InstallPrompt.css` (بدون فریم‌ورک CSS؛ Vanilla CSS).
- رنگ تم تاریک (`#0F0F0F`) و لهجه‌ی طلایی.

---

## ۴. ملزومات هاست و سرور برای اجرا

در ادامه چیزهایی که برای دیپلوی برنامه به صورت Production نیاز است لیست شده‌اند.

### سخت‌افزار و سیستم عامل

- یک VPS یا سرور ابری با حداقل **۱ vCPU و ۱GB RAM** (برای بار کم کافی است؛ برای بار متوسط ۲GB توصیه می‌شود).
- توزیع لینوکسی Ubuntu 22.04/24.04 یا Debian 12 (هر توزیع دیگری هم کار می‌کند).
- دسترسی روت / sudo.

### نرم‌افزار روی سرور

1. **Python 3.10+** و `pip`.
2. **Node.js 18+** و `npm` — فقط برای **build** فرانت (پس از build می‌توانید حذفش کنید).
3. **PostgreSQL 13+**:
   - ساخت یوزر اختصاصی و دیتابیس (مثلاً `taajil`).
   - به‌روزرسانی `backend/database.py` یا بهتر، انتقال `SQLALCHEMY_DATABASE_URL` به Environment Variable.
4. **Nginx** (یا Caddy) به عنوان reverse proxy جلوی uvicorn و برای سرو فایل‌های ساخته‌شده‌ی فرانت.
5. **Systemd** برای بالا نگه داشتن سرویس uvicorn (یا جایگزین: `supervisord`, `pm2`, کانتینر Docker).
6. **Certbot / Let’s Encrypt** برای HTTPS — **الزامی** است چون:
   - Service Worker و ویژگی‌های PWA فقط روی HTTPS کار می‌کنند.
   - کوکی توکن باید روی کانکشن امن رد و بدل شود.

### معماری پیشنهادی پروداکشن

```
Internet
   │
   ▼
Nginx (443, TLS)
   ├── /          → فایل‌های استاتیک فرانت (frontend/dist)
   └── /api/*     → پروکسی به uvicorn روی 127.0.0.1:8000
                    │
                    ▼
              FastAPI (uvicorn/gunicorn)
                    │
                    ▼
                PostgreSQL
```

نکات لازم:

- در Nginx هدرهای `X-Forwarded-For` و `X-Forwarded-Proto` را به بک‌اند پاس بدهید.
- برای فرانت، `try_files $uri /index.html;` بگذارید تا SPA routing درست کار کند.
- در فرانت قبل از build متغیر `VITE_API_URL=https://yourdomain.com` را ست کنید (یا مسیر را به `/api` تغییر دهید و Nginx را مطابقش کانفیگ کنید).
- در بک‌اند `origins` را به دامنه‌ی واقعی محدود کنید و `*` را بردارید.

### Environment Variableهای توصیه‌شده (برای Production)

موارد زیر در حال حاضر هاردکدند و **باید** به env منتقل شوند:

| متغیر | مقدار فعلی در کد | کاربرد |
|---|---|---|
| `DATABASE_URL` | `postgresql://postgres:123456@localhost/taajil` در `backend/database.py` | رشته‌ی اتصال PostgreSQL |
| `JWT_SECRET_KEY` | `YOUR_SUPER_SECRET_KEY_CHANGE_IN_PROD` در `backend/auth.py` | امضای JWT |
| `JWT_ALGORITHM` | `HS256` | الگوریتم JWT |
| `ACCESS_TOKEN_EXPIRE_MINUTES` | `43200` (۳۰ روز) | اعتبار توکن |
| `CORS_ORIGINS` | لیست هاردکد در `main.py` | محدودکردن CORS |
| `VITE_API_URL` | فرانت — `http://${hostname}:8000` | آدرس بک‌اند در build فرانت |

### اجرای سرویس بک‌اند (نمونه systemd)

```ini
[Unit]
Description=Await Mahdi API
After=network.target postgresql.service

[Service]
User=www-data
WorkingDirectory=/opt/await-mahdi
Environment="DATABASE_URL=postgresql://user:pass@localhost/taajil"
Environment="JWT_SECRET_KEY=... (یک کلید تصادفی قوی)"
ExecStart=/opt/await-mahdi/.venv/bin/uvicorn backend.main:app \
          --host 127.0.0.1 --port 8000 --workers 2
Restart=always

[Install]
WantedBy=multi-user.target
```

برای بار بیشتر می‌توانید به‌جای uvicorn از `gunicorn -k uvicorn.workers.UvicornWorker` با چند worker استفاده کنید.

### بک‌آپ و مانیتورینگ

- بک‌آپ روزانه‌ی PostgreSQL با `pg_dump` + نگه‌داری روی storage جدا.
- مانیتورینگ ساده با `journalctl` برای systemd یا اضافه‌کردن `uptime-kuma` / `prometheus + grafana`.
- لاگ‌های دسترسی Nginx و لاگ‌های uvicorn به دو فایل مجزا هدایت شوند.

### چک‌لیست امنیتی قبل از رفتن به Production

- [ ] تغییر `SECRET_KEY` به یک مقدار تصادفی (`openssl rand -hex 32`).
- [ ] تغییر پسورد دیتابیس و محدودکردن PostgreSQL به `127.0.0.1`.
- [ ] برداشتن `"*"` از لیست `origins` در CORS.
- [ ] فعال‌کردن HTTPS روی Nginx.
- [ ] بستن پورت ۸۰۰۰ روی firewall (فقط از روی لوپ‌بک قابل دسترسی باشد).
- [ ] تنظیم cookie ها به `Secure` و `SameSite=Lax` در فرانت.
- [ ] اضافه‌کردن Rate limiting (مثلاً با ماژول Nginx یا `slowapi`) برای اندپوینت `/heartbeat`.
