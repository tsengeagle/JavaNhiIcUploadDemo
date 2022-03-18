# IC卡上傳

本專案是基於研究目的撰寫

不適合直接用在生產環境

任何建議或指導都非常歡迎發issue

## 需求

需要搭配雲端安全模組

只支援Windows os

## 外部依賴元件

### jacob

用來呼叫CsHisX.dll

[jacob](https://github.com/freemansoft/jacob-project)

### Http client

需要客製SSL context，以繞過ssl validate error

### Jackson

用來將json轉成pojo

[jackson object mapper](https://www.baeldung.com/jackson-object-mapper-tutorial)
