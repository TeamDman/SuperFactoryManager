package ca.teamdman.sfm.common.ai;

import ca.teamdman.sfm.common.config.SFMConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class SFMAIModelList {
    public String fetchModels() throws IOException {
        String endpoint = SFMConfig.getOrDefault(SFMConfig.AI_CONFIG.openAICompatibleEndpoint);
        System.out.println("Fetching models from endpoint: " + endpoint);

        URL url = new URL(endpoint + "/v1/models");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        // Optional: set headers
        conn.setRequestProperty("User-Agent", "MinecraftMod/1.0");

        int responseCode = conn.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(conn.getInputStream())
            );
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                response.append(line);
            }
            in.close();
            return response.toString();
        } else {
            throw new RuntimeException("GET request failed: " + responseCode);
        }
    }
}
