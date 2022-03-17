package poc.nhi;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class IcUpload {
    private NhiService nhiService = new NhiService();
    private ArrayList<ProcessResult> processResultArrayList = new ArrayList<ProcessResult>();

    public void doUploadFrom(String sourcePath) {
        System.out.println("source path: " + sourcePath);
        System.out.println("上傳所有XML");
        // 取出全部XML檔案
        List<Path> xmlList = getAllXmlFromSourcePath(sourcePath);
        for (Path path : xmlList) {
            System.out.println("處理: " + path.toString());
            String content = "";
            try {
                // 讀檔案轉成字串
                content = new String(Files.readAllBytes(path));
            } catch (IOException e) {
                e.printStackTrace();
            }

            // 計算MB1及MB2，當作表頭與明細的數量
            int headCount = countPattern(content, "<MB1>");
            int bodyCount = countPattern(content, "<MB2>");

            // 呼叫nhi上傳服務
            JsonNode uploadResult = nhiService.vpnUpload(content, headCount, bodyCount);
            System.out.println("upload result: " + uploadResult);

            if (uploadResult.get("RtnCode").asText("").equals("0000")) {
                System.out.println("上傳成功");
                String opCode = uploadResult.get("Opcode").asText("");
                System.out.println("op code: " + opCode);

                // 保存上傳結果
                ProcessResult processResult = new ProcessResult();
                processResult.fileName = path.toString();
                processResult.opCode = opCode;
                processResultArrayList.add(processResult);
            } else {
                System.out.println("上傳失敗");
            }
        }
    }

    public void doDownload() {
        System.out.println("下載檢核結果");
        for (ProcessResult processResult : processResultArrayList) {
            System.out.println("開始下載檢核結果: " + processResult.fileName);
            JsonNode downloadResult = nhiService.vpnDownload(processResult.opCode);

            String rtnCode = downloadResult.get("RtnCode").asText();
            if (rtnCode.equals("5002")) {
                System.out.println("檔案還沒準備好");
                continue;
            }
            if (rtnCode.equals("0000")) {
                System.out.println("下載完畢");
                String txResultJson;
                // 下載的json有斷行，所以用MimeDecoder處理
                txResultJson = new String(Base64.getMimeDecoder().decode(downloadResult.get("Tx_result_json").asText().getBytes(StandardCharsets.UTF_8)));
                System.out.println("result_json: " + txResultJson);
                processResult.receiveDateTime = new Date();
                processResult.resultJson = txResultJson;
            } else {
                System.out.println("下載失敗, RtnCode: " + rtnCode);
            }
        }
    }

    private List<Path> getAllXmlFromSourcePath(String sourcePath) {
        List<Path> xmlList = null;
        try {
            Files.list(Paths.get(sourcePath)).filter(f -> f.toString().endsWith(".xml")).forEach(a -> {
                System.out.println(a);
            });
            xmlList = Files.list(Paths.get(sourcePath)).filter(f -> f.toString().endsWith(".xml")).collect(Collectors.toList());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return xmlList;
    }

    private int countPattern(String givenString, String pattern) {
        int index = 0;
        int count = 0;
        while (true) {
            index = givenString.indexOf(pattern, index);
            if (index != -1) {
                count++;
                index += pattern.length();
            } else {
                break;
            }
        }
        return count;
    }
}
