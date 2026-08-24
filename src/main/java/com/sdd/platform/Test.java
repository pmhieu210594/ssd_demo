import java.io.IOException;

public class Test {
    public void runCommand(String userInput) throws IOException {
        // LỖI: Truyền trực tiếp dữ liệu từ user vào hệ thống mà không kiểm duyệt
        Runtime.getRuntime().exec("ping " + userInput); 
    }
}
