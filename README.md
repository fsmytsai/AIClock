

# To Do
| State      | Description                 |
|------------|-----------------------------|
| warn       | 15 分鐘內重試太短，容易造成最後 2-5 分鐘才決定播放舊音檔，導致 10 分鐘內無法再次呼叫響鈴。(兩個鬧鐘會打架，互相拖 9 分鐘) |
| unfinished | 用 JobService 處理 Android N 以上的開啟網路事件 |
| unfinished | 考慮是否要將響鈴時的順序改成先開 service 再從 service 開 activity |
| unfinished | 改用空氣盒子抓空氣資料，缺點是沒 AQI 跟座標要算 |
| unfinished | 清除伺服器舊資料 |
| unfinished | 語音命令賴床或重播某段音檔等 |
| unfinished | 考慮是否把倒數計時移到外層 |
| unfinished | 卡住原因可能為伺服器端音檔遺失，應該設置 TimeOut |
| unfinished | 美化版面 |
| unfinished | 自選天氣預報位置、首頁新聞種類、播報者 |
| unfinished | 自動抓取位置座標時改為主動 |
| unfinished | 許願與問題回報 cancelable false |

# Version Features
## 第一版包含以下功能：
1. 想賴床可延遲響鈴
2. 播報者 2 人
3. 可自訂背景音樂
4. 天氣播報及溫馨提醒
5. 播報 6 種新聞類別
6. 支持大字體手機不跑版
7. 可查看正在播報新聞

# Release Note
## 1.12.0 versionCode = 36
1. 新增首頁新聞可選類別、播報者、開關天氣
2. 提高穩定性

## 1.11.0 versionCode = 35
1. 新增可播放當前綜合新聞、天氣預報、空氣品質的主頁面
2. 新增設定頁面
3. 新增當有設定鬧鐘時顯示圖示的功能
4. 優化設置較久之後響鈴的鬧鐘時的速度

## 1.10.1 versionCode = 34
1. 提高穩定性

## 1.10.0 versionCode = 33
1. 新增響鈴前通知，可提前取消響鈴
2. 修正於主頁面開啟鬧鐘時，有時會沒有反應的問題

## 1.9.3 versionCode = 32
1. 新增取得資料的時間提示
2. 修正某些情況下選取背景音樂閃退的問題
3. 修正某些情況下響鈴時閃退的問題
4. 修正響鈴頁面叉叉球可能消失的問題
5. 修正響鈴頁面有時不是全螢幕的問題

## 1.9.2 versionCode = 31
1. 修正沒設置鬧鐘時，重開機造成停止運作的問題

## 1.9.1 versionCode = 30
1. 修正手機系統大字體時，背景音樂選項跑版的問題
2. 修正某些情況下響鈴時閃退的問題

## 1.9.0 versionCode = 29
1. 新增背景音樂自訂的功能
2. 修正響鈴時間有可能重複的問題

## 1.8.1 versionCode = 28
1. 拖動叉叉球時的提示字體更明顯
2. 修正某些情況下響鈴時閃退的問題

## 1.8.0 versionCode = 27
1. 新增賴床延遲響鈴的功能
2. 修正響鈴時顯示的天氣預報、空氣品質等資訊也能點擊的問題
3. 取消設置鬧鐘必須超過現在時間 30 秒的限制

## 1.7.1 versionCode = 26
1. 修正某些情況下開啟天氣一直取得位置失敗的問題

## 1.7.0 versionCode = 25
1. 新增設置鬧鐘時靜音的功能
2. 新增天氣預報及空氣品質的文字提示
3. 修正某些情況下設置鬧鐘成功後，卻跳出無法取消設置的提示
4. 修正兩個鬧鐘響鈴時間太接近時，第二個鬧鐘缺少資料的問題
5. 提高穩定性

## 1.6.4 versionCode = 24
1. 修正某些情況下修改或新增鬧鐘後，再次跳出設置視窗的錯誤
2. 修正響鈴時載入圖片後還在轉圈圈的錯誤

## 1.6.3 versionCode = 23
1. 修正兩個鬧鐘響鈴時間太接近時，第二個不會響的問題

## 1.6.2 versionCode = 22
1. 修正重複提示更新新版本的錯誤

## 1.6.1 versionCode = 21
1. 替換掉問題一堆的開關按鈕

## 1.6.0 versionCode = 20
1. 新增自動檢查是否有新版本的功能
2. 修正響鈴時閃退的錯誤

## 1.5.4 versionCode = 19
1. 修正無法設置鬧鐘的錯誤
2. 可設置的新聞篇數由 12 降至 10
3. 移除少人使用的女2聲音
4. 移除科技新聞(未來看情況可能再加回來)

## 1.5.3 versionCode = 18
1. 新增於晚上 8 點後設置鬧鐘取得的天氣預報是明天的
2. 修正某些原因造成的音檔大量遺失
3. 修正在某些座標範圍內，設置有天氣預報的鬧鐘頻繁失敗

## 1.5.2 versionCode = 17
1. 修正刪除鬧鐘後再新增會重複跳出設置鬧鐘框框
2. 修正某些情況下，伺服器不穩造成鬧鐘不會響的問題
3. 增加穩定度
4. 修正低機率遺失音檔造成的閃退(由於機率太低，每次都以為修好，這次應該真的修好了...)

## 1.5.1 versionCode = 16
1. 如果您裝了此版本，請盡快升級

## 1.5.0 versionCode = 15
1. 新增鬧鐘可不重複的功能
2. 將設定新聞篇數移到新聞類別下方
3. 新增 Android 7.0 以下的手機當重新連上網路會自動重設失效鬧鐘
4. 修正天氣播報關不掉的錯誤

## 1.4.1 versionCode = 14
1. 新增可在關於選單看到當前版本號
2. 修正在系統為 Android 6.0 以上的手機沒開網路造成的延遲響鈴

## 1.4.0 versionCode = 13
1. 新增問題回報及許願選單

## 1.3.2 versionCode = 12
1. 新增關於選單
2. 緊急修正由於作者頭撞到造成 1.3.0 版的鬧鐘失效問題
3. 增加穩定度

## 1.3.1 versionCode = 11
1. 增加穩定度

## 1.3.0 versionCode = 10
1. 新增播報新聞篇數
2. 新增更新後自動重設所有鬧鐘
3. 修正某些情況下修改鬧鐘後再次跳出設置視窗的問題
4. 修正取消鬧鐘後還有可能響的問題
5. 修正某些情況下重設鬧鐘造成響鈴時畫面崩潰
6. 修正沒網路時，開啟鬧鐘造成閃退的問題
7. 修正響鈴時間重疊造成第二個鬧鐘失效的問題

## 1.2.2 versionCode = 9
1. 修正大字體時，首頁可能跑版的問題

## 1.2.1 versionCode = 8
1. 智能鬧鐘更加穩定

## 1.2.0 versionCode = 7
1. 新增重開機後會自動重新設置智能鬧鐘，不會再因為重開機造成鬧鐘失效
2. 新增自動清除過舊播報音檔，節省空間
3. 新增設定時間的方式改成滾輪
4. 新增響鈴頁面，包含了新聞標題、簡介、預覽圖
5. 新增詳細新聞頁面
6. 新增長按刪除鬧鐘，現在不必點開修改鬧鐘頁面就能刪除囉
7. 智能鬧鐘更加穩定

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
1. 修正低 Android 版本一開就閃退的錯誤
  
## 1.0.1 versionCode = 2
1. 修正手機大字體時造成的跑版
2. 修正記憶體溢出錯誤
