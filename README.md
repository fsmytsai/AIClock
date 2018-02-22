

# To Do
| State      | Description                 |
|------------|-----------------------------|
| unfinished | 修改後端，避免超過1000次抓取新聞 |
| unfinished | 新聞詳細頁面 |
| finished   | 第幾則新聞提示 |
| finished   | 使用網路提示 |
| finished   | 判斷時間多判斷星期幾 |
| finished   | 測試 google play 更新會不會殺死鬧鐘 |
| finished   | 應在下載音檔前才抓位置 |
| finished   | 取得鬧鐘資料跟文字資料模組化 |
| finished   | 提早插入鬧鐘資料 |
| finished   | 檢查 PrepareService 的 stopself 有沒有效 |
| finished   | 播報天氣新增地名 |
| finished   | 修正沒網路時新建鬧鐘崩潰 |
| finished   | 取消下載 |
| finished   | 簡化使用網路提示 |
| finished   | 重開機重新設置 | 
| finished   | 刪除舊音檔 |
| finished   | 修復背景下載失敗造成整個鬧鐘失效 |
| finished   | 時間用滾動選擇 |
| finished   | 提示詳細溫度 |
| finished   | 修改後端，加上來源網址及圖片 |
| finished   | 響鈴頁面 |
| finished   | 長按刪除鬧鐘 |


# Version Features
## 第一版包含以下功能：
1. 播報者 3 人
2. 天氣播報及溫馨提醒
3. 播報 7 種新聞類別
4. 支持大字體手機不跑版

# Release Note
## 1.3.0 versionCode = 8
1. 設定時間的方式改成滾輪
2. 完成響鈴頁面，包含了新聞標題、簡介、預覽圖

## 1.2.0 versionCode = 7
1. 重開機後會自動重新設置智能鬧鐘，不會再因為重開機造成鬧鐘失效
2. 自動清除過舊播報音檔，節省空間
3. 智能鬧鐘更加穩定

## 1.1.1 versionCode = 6
1. 新增判斷鬧鐘時間是否重複時，多判斷日期，現在可以新增不同天但同時間的鬧鐘囉
2. 修正新增、修改、刪除鬧鐘後，資料錯誤的問題
3. 修正已完成設置鬧鐘後按取消閃退的問題
  
## 1.1.0 versionCode = 5
1. 新增開始播放第幾則新聞的提示
2. 新增取消設置鬧鐘
3. 新增使用網路警告
4. 播報天氣時用的位置更加精準
5. 修正沒網路時設置鬧鐘造成閃退
    
## 1.0.3 versionCode = 4
1. 修正設置鬧鐘時按返回、回到桌面或關閉螢幕造成設置失敗的問題
2. 修正響鈴時關閉螢幕重開後無法繼續播放的問題
3. 修正響鈴時關閉螢幕造成重開 app 時閃退的問題
4. 修正手機大字體時造成的跑版
5. 修正取不到位置閃退的問題
  
## 1.0.2 versionCode = 3
1. 修正低 android 版本一開就閃退的錯誤
  
## 1.0.1 versionCode = 2
1. 修正手機大字體時造成的跑版
2. 修正記憶體溢出錯誤
