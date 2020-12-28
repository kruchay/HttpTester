package Main;

import Main.Tester.Tester;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

public class AppEntryPoint {

    public static void main(String [] args) {
        runApp();
    }

    private static void runApp() {
        // Получение базовых конфигурационных параметров:
        // ..целевой URL
        URL targetURL = null;
        String targetUrlValue = AppConfig.getProperty("target_url", "");
        if (targetUrlValue.isEmpty()) {
            System.out.print("\r\nError! Don't specified target url\r\n");
            return;
        } else {
            try {
                targetURL = new URL(targetUrlValue);
            }  catch (MalformedURLException ex) {
                System.out.print("\r\nError! Not correct target url\r\n");
                return;
            }
        }
        // ..требуемая нагрузка (количество запросов в секунду)
        int requiredLoad = AppConfig.getProperty("requests_per_second", 10);

        // Запуск тестирования
        Thread tester1 = new Thread(new Tester(targetURL, requiredLoad, System.out)::start, "tester1");
        tester1.start();

        // Ожидание нажатия ENTER
        try {
            while (tester1.isAlive() & System.in.available() == 0);
        } catch (IOException ex) {}

        // Останов тестирования
        tester1.interrupt();
    }

}
