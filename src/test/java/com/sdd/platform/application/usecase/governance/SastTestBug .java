package com.sdd.platform.application.usecase.governance;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;

class SastTestBug {

    // LỖI SAST 1: Hardcoded Secret / Mật mã cấu hình cứng (CWE-798)
    // Semgrep sẽ cảnh báo nghiêm trọng vì chứa chuỗi token xác thực ngay trong code.
    private static final String BRC_API_TOKEN = "brc_live_9a8b7c6d5e4f3g2h1i0j_SECRET";

    public void processUserDocument(String userInputFileName, String untrustedData) throws IOException {
        
        // LỖI SAST 2: Path Traversal / Lỗ hổng duyệt đường dẫn (CWE-22)
        // Nguy hiểm: Sử dụng Paths.get() trực tiếp với đầu vào của người dùng (userInputFileName).
        // Nếu kẻ tấn công truyền vào: "../../../etc/passwd", hệ thống sẽ bị lộ file nhạy cảm.
        Path targetPath = Paths.get("/var/data/app/uploads/", userInputFileName);
        
        // Thực hiện đọc file bằng java.nio.file.Files
        if (Files.exists(targetPath)) {
            List<String> lines = Files.readAllLines(targetPath);
            System.out.println("Đã đọc được " + lines.size() + " dòng từ file.");
        }

        // LỖI SAST 3: Độc hại ghi đè tệp tin hệ thống (CWE-73)
        // Kẻ tấn công có thể lợi dụng để ghi đè vào các file cấu hình quan trọng của server (.bashrc, v.v.)
        Path logPath = Paths.get("/var/data/app/logs/" + userInputFileName);
        Files.write(logPath, untrustedData.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}
