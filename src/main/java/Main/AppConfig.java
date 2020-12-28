package Main;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class AppConfig {

    private final static String propsFile = "/HttpTester.properties";
    private final static String envPropsPrefix = "App.HttpTester.";
    private static Properties props;

    static {
        props = new Properties();
        // Загрузка свойств из файла
        try (InputStream fis = AppConfig.class.getResourceAsStream(propsFile)) { // получить ресурс относительно classpath
            if (fis != null) props.load(fis);
        } catch (IOException ex) {}
        // Если есть свойства в окружении, то приоритет у них
        System.getenv().forEach((k, v)-> {
            String key = k.trim();
            String value = v.trim();
            if (key.startsWith(envPropsPrefix) && key.length() > envPropsPrefix.length())
                props.setProperty(key.substring(envPropsPrefix.length()), value);
        });
    }

    public static int getProperty(String key, int defaultValue) {
        int returnValue = defaultValue;
        String propertyValue = props.getProperty(key);
        if (propertyValue != null) {
            try {
                returnValue = Integer.parseInt(propertyValue);
            } catch (NumberFormatException ex) {}
        }
        return returnValue;
    }

    public static boolean getProperty(String key, boolean defaultValue) {
        boolean returnValue = defaultValue;
        String propertyValue = props.getProperty(key);
        if (propertyValue != null) {
            try {
                returnValue = Boolean.parseBoolean(propertyValue);
            } catch (NumberFormatException ex) {}
        }
        return returnValue;
    }

    public static String getProperty(String key, String defaultValue) {
        String propertyValue = props.getProperty(key);
        if (propertyValue != null) return propertyValue;
        else return defaultValue;
    }

    private AppConfig() {
    }

}
