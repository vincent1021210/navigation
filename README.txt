PATS 導航（Android / Kotlin + Google Maps）
==========================================
一般開車／走路導航：底圖 Google Maps、路線 Directions API、定位 Play Services。


一、Google Cloud（必做，否則地圖是空白）
----------------------------------------
1. 開 https://console.cloud.google.com/ 建立專案，並啟用帳單（每月有抵扣，超量才收費）。
2. 啟用這三個 API：
   - Maps SDK for Android
   - Directions API
   - Places API（新版請開 Places API，Autocomplete 需要）
3. 建立「API 金鑰」，建議限制：
   - 應用程式限制：Android，套件名稱 tw.pats.nav
   - 加上你的 SHA-1（Android Studio → Gradle → signingReport）
4. 複製 nav-android/local.properties.example 為 local.properties
   填入 sdk.dir 與 MAPS_API_KEY=你的金鑰
   這個檔不要提交到 Git。


二、用 Android Studio 開啟
----------------------------------------
1. File → Open → 選這個資料夾「nav-android」
2. 等 Gradle Sync
3. 接實體機（需有 Google Play）或含 Play 的模擬器
4. Run app


三、怎麼用
----------------------------------------
1. 允許定位後，地圖會出現藍色「我的位置」
2. 點上方搜尋列，選終點
3. 切換「開車」或「走路」，會畫藍線並顯示時間／距離
4. 按「開始導航」：依 GPS 更新下一步提示，接近轉彎會語音播報
5. 偏離路線約 40 公尺會自動重算（12 秒內只重算一次，避免刷爆配額）


四、注意
----------------------------------------
- 第一版不支援鎖屏背景導航、離線圖、機車專用路網
- 金鑰外洩會被他人刷 Directions／Places 計費，請務必設應用程式限制
