package Main.Tester;

import Main.Tester.Http.HttpStream;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Timer;
import java.util.TimerTask;

public class Tester {

    final private URL targetURL;
    final private int requiredLoad;
    final private PrintStream log;

    private volatile int actualLoad = 0;
    private volatile long reqCounter = 0;
    private volatile long respCounter = 0;
    private volatile long reqCounterPrev = 0;
    private boolean stopping = false;
    private boolean running = false;
    private Timer loadMeter = null;

    private SocketChannel socketChannel = null;
    private ByteBuffer recvBuffer = ByteBuffer.allocate(4096);
    private ByteBuffer sendBuffer = ByteBuffer.allocate(4096);
    private byte [] buf= new byte[4096];

    public Tester(URL targetUrl, int requiredLoad, PrintStream log) {
        this.targetURL = targetUrl;
        this.requiredLoad = Math.max(1, requiredLoad);
        this.log = log;
    }

    public boolean isRunning() {
        return running;
    }

    // Запуск тестера
    public void start() {
        log.printf("\r\nStart testing host: %s, required load: %d req/sec (to stop press 'ENTER')\r\n", targetURL.getHost(), requiredLoad);
        running = true;
        while (!stopping & !Thread.currentThread().isInterrupted()) {
            // Попытка открыть/переоткрыть соединение
            try {
                log.printf("\r%-96s", "trying to connect to host...");
                openConnection();
            } catch (IOException ex) {
                log.printf("\r%-96s", "Error! Host not available");
                break;
            }
            // Соединение открыто...
            // Запуск измерения актуальной нагрузки
            startLoadMeter();
            // Запуск главного цикла обмена данными
            try {
                while (!stopping & !Thread.currentThread().isInterrupted()) {
                    long startTime = System.nanoTime();
                    // Отправка запроса
                    if (sendRequest()) {
                        reqCounter++;
                        //updateStatistic();
                    }
                    // Получение ответов
                    do {
                        int recvCount = recvResponses();
                        if (recvCount > 0) {
                            respCounter += recvCount;
                            //updateStatistic();
                        }
                       } while (System.nanoTime() - startTime < 1_000_000_000 / requiredLoad); // 1s = 1_000_000_000 us
                }
            }
            catch (IOException ex) {
                // Проблема с приемом/передачей => выходим из цикла, закрываем соединение и пытаемся его переоткрыть
            }
            // Закрытие соединения
            closeConnection();
            // Останов измерения актуальной нагрузки
            stopLoadMeter();
        }
        stopping = false;
        running = false;
        log.println("\r\nTesting completed!");
    }

    // Останов тестера
    public void stop() {
        if (running) {
            stopping = true;
        }
    }

    // Отправка http запроса
    // возвращает true, если запрос удалось поместить в буффер для передачи
    private boolean sendRequest() throws IOException {
        boolean result = false;

        // Строка с GET запросом
        String requestLine =
                String.format("GET %s HTTP/1.1\r\n" +
                              "Host: %s\r\n" +
                              "Connection: Keep-alive\r\n" +
                              "\r\n",
                              targetURL.getPath(), targetURL.getHost());
        // Байтовое представление запроса
        byte [] request = requestLine.getBytes(StandardCharsets.UTF_8);

        // Отправка запроса (при переполнении системных tcp буферов, отправка может временно не выполнятся
        // соответсвенно sendBuffer будет заполненный и новые запросы туда поместить не удастся; это может происходить
        // когда приемник запросов на другой стороне не успевает их вычитывать)
        if (sendBuffer.remaining() >= request.length) {
            sendBuffer.put(request);
            result = true;
        }
        sendBuffer.flip(); // подготовить к отправке (скинуть позицию буффера в 0)
        socketChannel.write(sendBuffer);
        sendBuffer.compact(); // удалить отправленное, а оставшееся сдвинуть в начало буффера (позиция буффера = размер оставшегося)
        return result;
    }

    // Прием http ответов
    // возвращает количество принятых ответов
    private int recvResponses() throws IOException {
        //  Функция read вычитывает столько байт, сколько доступно во входном потоке с учетом свободного места в приемном буффере
        if (socketChannel.read(recvBuffer) != -1 && recvBuffer.position() > 0) { // что-то удалось вычитать
            recvBuffer.flip(); // подготовить к вычитке (скинуть позицию буффера в 0, а limit установить равным количеству доступных байт)
            int recvBytesCount = Math.min(recvBuffer.remaining(), buf.length);
            recvBuffer.get(buf, 0, recvBytesCount); // забрать из буффера столько байт, сколько доступно
            recvBuffer.compact(); // удалить вычитанное, а оставшееся сдвинуть в начало буффера (позиция буффера = размер оставшегося)
            // Подсчет количества полученных ответов
            return HttpStream.countingHttpResponses(buf, recvBytesCount);
        }
        return 0;
    }

    // Обновление строки со статистикой
    private void updateStatistic() {
         synchronized (log) {
             log.printf("\r> total requests: %d, total responses: %d, actual load: %d req/sec %-16s", reqCounter, respCounter, actualLoad, "");
        }
    }

    // Запуск измерения актуальной нагрузки
    private void startLoadMeter() {
        long interval = 1000; // 1000мс = 1c
        if (loadMeter == null) {
            loadMeter = new Timer(true);
        }
        loadMeter.schedule(
                new TimerTask() {
                    long beginTime = System.nanoTime();
                    @Override
                    public void run() {
                        long deltaTime = System.nanoTime() - beginTime;
                        actualLoad = (int) ((((reqCounter - reqCounterPrev) * 1_000_000_000) + deltaTime/2) / deltaTime);
                        updateStatistic();
                        reqCounterPrev = reqCounter;
                        beginTime = System.nanoTime();
                    }
                }, interval, interval);
    }

    // Останов измерения актуальной нагрузки
    private void stopLoadMeter() {
        if (loadMeter != null) {
            loadMeter.cancel();
            loadMeter = null;
        }
    }

    // Открытие соединения
    private void openConnection() throws IOException {
        socketChannel = SocketChannel.open();
        socketChannel.connect(new InetSocketAddress(
                InetAddress.getByName(targetURL.getHost()),
                (targetURL.getPort() != -1) ? targetURL.getPort() : targetURL.getDefaultPort()));
        // Установка неблокирующего режима
        socketChannel.configureBlocking(false);
    }

    // Закрытие соединения
    private void closeConnection() {
        try {
            if (socketChannel != null && socketChannel.isConnected()) {
                socketChannel.finishConnect();
                socketChannel.close();
            }
        } catch (IOException ex) {}
    }

}
