# ARB Smart Bot

Native Android WebView bot app with a Render-hosted subscription/payment backend.

## Projects

- `android-bot/` - lightweight native Android APK project.
- `backend/` - Node/Express API for subscription checks and Cashfree payment flow.
- `render.yaml` - Render web service config. The service runs from `backend/`.

## Android Build

```powershell
cd android-bot
.\gradlew.bat clean assembleRelease
```

Release APK output:

```text
android-bot/app/build/outputs/apk/release/app-release.apk
```

Local APK files are ignored by Git.

## Backend

```powershell
cd backend
npm install
npm start
```

Required production environment variables on Render:

- `SUPABASE_URL`
- `SUPABASE_SERVICE_ROLE_KEY` or `SUPABASE_ANON_KEY`
- `CASHFREE_APP_ID`
- `CASHFREE_SECRET_KEY`
- `PUBLIC_BASE_URL`
- `APP_RETURN_URL`

Default production API used by the APK:

```text
https://arbsmartbot-b6rn.onrender.com
```

Default website loaded by the APK:

```text
https://arbpay.me/
```

Buy page used by the bot:

```text
https://arbpay.me/#/buy/arb
```
