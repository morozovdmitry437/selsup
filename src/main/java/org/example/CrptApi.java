package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.util.concurrent.*;

public class CrptApi {
    private final TimeUnit timeUnit;
    private final int requestLimit;
    private final BlockingQueue<Long> requestTimes;
    private final ObjectMapper objectMapper;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.timeUnit = timeUnit;
        this.requestLimit = requestLimit;
        this.requestTimes = new LinkedBlockingQueue<>(requestLimit);
        this.objectMapper = new ObjectMapper();
    }

    public synchronized void createDocument(Object document, String signature) throws IOException, InterruptedException {
        long currentTime = Instant.now().toEpochMilli();
        long timeWindowMillis = timeUnit.toMillis(1);

        while (requestTimes.size() == requestLimit) {
            long oldestRequestTime = requestTimes.peek();
            long timeSinceOldestRequest = currentTime - oldestRequestTime;
            if (timeSinceOldestRequest < timeWindowMillis) {
                wait(timeWindowMillis - timeSinceOldestRequest);
                currentTime = Instant.now().toEpochMilli();
            } else {
                requestTimes.poll();
            }
        }

        requestTimes.offer(currentTime);
        notifyAll();

        String jsonDocument = objectMapper.writeValueAsString(document);
        String requestBody = String.format("{\"description\":%s,\"signature\":\"%s\"}", jsonDocument, signature);

        URL url = new URL("https://ismp.crpt.ru/api/v3/1k/documents/create");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);

        try (var outputStream = connection.getOutputStream()) {
            outputStream.write(requestBody.getBytes());
        }

        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("HTTP error code: " + responseCode);
        }
    }
}

// Пример использования.
// CrptApi api = new CrptApi(TimeUnit.MINUTES, 100);
// api.createDocument(documentObject, "signatureString");
