package Main.Tester.Http;

import java.nio.charset.StandardCharsets;

public class HttpStream {

    private static StringBuilder sb = new StringBuilder(65535); // 64K (будет пределом для одного http ответа)

    // Подсчет количества http ответов во входном потоке
    public static int countingHttpResponses(byte [] buf, int len) {
        // Получаем новую порцию данных
        String data = new String(buf, 0, len, StandardCharsets.UTF_8);
        // Если вдруг каким-то не понятным образом стринбилдер раздулся до огромных размеров
        if (sb.length() + data.length() >= sb.capacity()) {
            sb.setLength(0); // освобождение стрингбилдера
            return 0;
        }
        // Если стрингбилдер в норме, добавляем в него данные
        sb.append(data);
        // Начинаем поиск
        int respCount = 0;
        while (sb.length() > 0) {
            // Поиск начала стартовой строки
            int beginStartLine = sb.indexOf("HTTP/");
            // Нет стартовой строки в стрингбилдере
            if (beginStartLine == -1) {
                sb.setLength(0); // освобождение стрингбилдера
                break;
            }
            // Есть стартовая строка, но она не в начале
            if (beginStartLine > 0) {
                sb.delete(0, beginStartLine); // удаляем все до стартовой строки, остальное смещаем в начало стрингбилдера
            }
            // Ищем конец стартовой строки
            int endStartLine = sb.indexOf("\r\n");
            // Нет конца строки
            if (endStartLine == -1)
                break; // со стрингбилдером ничего не делаем, может конец строки еще прийдет в след.буффере
            // Есть начало и конец, получаем стартовую строку и проверяем ее корректность
            String startLine = sb.substring(0, endStartLine);
            String[] startLineTokens = startLine.trim().split(" ", 3);
            if (startLineTokens.length >= 2) { // все ок, стартовая строка корректная
                respCount++;
            }
            sb.delete(0, startLine.length() + 2); // удаляем стартовую строку, +2 для символов конца строки
        }
        return respCount;
    }

}
