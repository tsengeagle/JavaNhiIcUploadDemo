package poc.nhi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jacob.com.Dispatch;
import com.jacob.com.Variant;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.jackson.JacksonFeature;

import javax.net.ssl.*;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.*;

public class NhiService {
    // jackson json的物件轉換器
    private ObjectMapper objectMapper = new ObjectMapper();
    private String       baseUrl       = "https://medvpndti.nhi.gov.tw/V1000/";
    private String       cardType      = "3";
    private String       serviceCode   = "30";
    private String       operationType = "ZZ";

    public JsonNode vpnUpload(String xmlContent, int headCount, int bodyCount) {

        HashMap<String, String> returnMap = getDataFromCsHisX();

        // 組裝payload
        HashMap<String, String> uploadPayload = new HashMap<>();
        uploadPayload.put("sSamid", returnMap.get("samId"));
        uploadPayload.put("sHospid", returnMap.get("hosp"));
        uploadPayload.put("sClientrandom", returnMap.get("randomX"));
        uploadPayload.put("sSignature", returnMap.get("signature"));
        uploadPayload.put("sType", operationType);
        uploadPayload.put("sMrecs", String.valueOf(headCount));
        uploadPayload.put("sPrecs", String.valueOf(bodyCount));
        uploadPayload.put("sPatData", Base64.getEncoder().encodeToString(xmlContent.getBytes(StandardCharsets.UTF_8)));
        uploadPayload.put("sUploadDT", new SimpleDateFormat("yyyyMMddHHmmssSSS").format(new Date()));
        System.out.println("upload payload: " + uploadPayload);

        // 用http client呼叫vpn
        Client httpsClient = getHttpsClient();
        Response response = httpsClient.target(baseUrl).path("VNHI_Upload").request(MediaType.APPLICATION_JSON)
                .post(Entity.entity(uploadPayload, MediaType.APPLICATION_JSON));
        String responseData = response.readEntity(String.class);
        System.out.println("responseData: " + responseData);

        // response body轉成json node
        JsonNode jsonNode = null;
        try {
            jsonNode = objectMapper.readTree(responseData);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return jsonNode;
    }

    // 自訂，為了繞過ssl error
    private static Client getHttpsClient() {
        TrustManager[] trustManagers = {new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException { }

            @Override
            public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException { }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }
        }};

        // 自訂ssl
        SSLContext sslContext = null;
        try {
            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManagers, new SecureRandom());
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            e.printStackTrace();
        }

        HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
        ClientBuilder myClientBuilder = ClientBuilder.newBuilder();
        myClientBuilder.sslContext(sslContext);
        // ssl憑證的hostname驗證
        myClientBuilder.hostnameVerifier(new HostnameVerifier() {
            @Override
            public boolean verify(String s, SSLSession sslSession) {
                return true;
            }
        });

        Client myClient;
        myClient = myClientBuilder.withConfig(new ClientConfig()).register(JacksonFeature.class).build();
        return myClient;
    }

    public JsonNode vpnDownload(String opCode) {
        System.out.println("processing: " + opCode);

        HashMap<String, String> returnMap = getDataFromCsHisX();

        HashMap downloadPayload = new HashMap();
        downloadPayload.put("sSamid", returnMap.get("samId"));
        downloadPayload.put("sHospid", returnMap.get("hosp"));
        downloadPayload.put("sClientrandom", returnMap.get("randomX"));
        downloadPayload.put("sSignature", returnMap.get("signature"));
        downloadPayload.put("sType", operationType);
        downloadPayload.put("sOpcode", opCode);

        Client httpClient = getHttpsClient();
        Response response = httpClient.target(baseUrl).path("VNHI_Download").request(MediaType.APPLICATION_JSON)
                .post(Entity.entity(downloadPayload, MediaType.APPLICATION_JSON));
        String responseData = response.readEntity(String.class);

        JsonNode jsonNode = null;
        try {
            jsonNode = objectMapper.readTree(responseData);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return jsonNode;

    }

    private HashMap<String, String> getDataFromCsHisX() {
        // 透過cshisx.dll，取回samid, hospid, randomX, signature
        Dispatch dispatchTarget     = new Dispatch("CsHisX.nhicshisx.1");
        Variant  getSAMCardInfoInCS = Dispatch.call(dispatchTarget, "GetSAMCardInfoInCS");
        String   samId              = "";
        String   hosp               = "";
        try {
            JsonNode jsonResult = objectMapper.readTree(getSAMCardInfoInCS.toString());
            samId = jsonResult.get("SAMCardInfoInCS").get("SAM").get(0).get("CARD_ID").asText("");
            hosp  = jsonResult.get("SAMCardInfoInCS").get("SAM").get(0).get("HOSP").asText("");
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        String randomX   = Dispatch.call(dispatchTarget, "VPNGetRandomX").toString();
        String signature = Dispatch.call(dispatchTarget, "VPNH_SignX", randomX, cardType, serviceCode).toString();
        HashMap<String, String> returnMap = new HashMap<>();
        returnMap.put("samId", samId);
        returnMap.put("hosp", hosp);
        returnMap.put("randomX", randomX);
        returnMap.put("signature", signature);
        return returnMap;
    }
}
