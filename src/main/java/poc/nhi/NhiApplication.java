package poc.nhi;

import java.nio.file.Paths;

public class NhiApplication {
    public static void main(String[] args) {
        IcUpload icUpload = new IcUpload();

        String xmlPath = "./";
        icUpload.doUploadFrom(Paths.get(xmlPath).toString());

        icUpload.doDownload();
    }
}
