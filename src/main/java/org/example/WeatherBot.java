package org.example;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

public class WeatherBot extends TelegramLongPollingBot {

    private final String BOT_TOKEN = "7139643006:AAGj4xg_uHdRDCGtPqlNryjGh9TGVkBExCs";
    private final String BOT_USERNAME = "uz1_weather_bot";
    private final String WEATHER_API_KEY = "11ff03f21c7d0af2918c6ef91075beda";

    // Har bir foydalanuvchiga bir nechta shaharlarni saqlash
    private final Map<Long, List<String>> userCities = new HashMap<>();
    private final String CITIES_FILE = "user_cities.dat";

    public WeatherBot() {
        loadSavedCities();
    }

    @Override
    public String getBotToken() {
        return BOT_TOKEN;
    }

    @Override
    public String getBotUsername() {
        return BOT_USERNAME;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText().trim();
            Long chatId = update.getMessage().getChatId();
            String responseText;

            switch (messageText.toLowerCase()) {
                case "/start":
                    responseText = "Salom! Men ob-havo haqida ma’lumot beruvchi botman.\n" +
                            "Iltimos, shahar nomini yuboring (masalan: Tashkent).";
                    break;
                case "/week":
                    responseText = getCityListBasedResponse(chatId, this::getWeeklyWeather, "5 kunlik prognoz uchun");
                    break;
                case "/hour":
                    responseText = getCityListBasedResponse(chatId, this::getHourlyWeather, "soatlik prognoz uchun");
                    break;
                case "/clear":
                    userCities.remove(chatId);
                    saveCities();
                    responseText = "✅ Saqlangan barcha shaharlar tozalandi.";
                    break;
                case "/cities":
                    List<String> saved = userCities.get(chatId);
                    if (saved == null || saved.isEmpty()) {
                        responseText = "❌ Hech qanday shahar saqlanmagan.";
                    } else {
                        responseText = "📍 Saqlangan shaharlar:\n" + String.join("\n", saved);
                    }
                    break;
                default:
                    List<String> cities = userCities.computeIfAbsent(chatId, k -> new ArrayList<>());
                    if (!cities.contains(messageText)) {
                        cities.add(messageText);
                        saveCities();
                    }
                    responseText = getWeather(messageText) +
                            "\n\nQuyidagi buyruqlardan foydalaning:\n/week - 5 kunlik prognoz\n/hour - yaqin soatlar uchun ob-havo\n/clear - saqlangan shaharlarni o‘chirish\n/cities - saqlangan shaharlar ro‘yxati";
                    break;
            }
            sendMessage(chatId.toString(), responseText, userCities.get(chatId));

        }
    }

    private String getCityListBasedResponse(Long chatId, java.util.function.Function<String, String> weatherFunction, String actionName) {
        List<String> cities = userCities.get(chatId);
        if (cities == null || cities.isEmpty()) {
            return "Avval hech bo'lmaganda bitta shahar yuboring " + actionName + ".";
        }
        StringBuilder sb = new StringBuilder();
        for (String city : cities) {
            sb.append(weatherFunction.apply(city)).append("\n\n");
        }
        return sb.toString();
    }

    private void saveCities() {
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(CITIES_FILE))) {
            out.writeObject(userCities);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadSavedCities() {
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(CITIES_FILE))) {
            Object obj = in.readObject();
            if (obj instanceof HashMap) {
                userCities.putAll((HashMap<Long, List<String>>) obj);
            }
        } catch (Exception e) {
            // Fayl mavjud emas yoki o'qib bo'lmadi
        }
    }

    private String getWeather(String city) {
        try {
            String apiUrl = "https://api.openweathermap.org/data/2.5/weather?q=" + city +
                    "&appid=" + WEATHER_API_KEY + "&units=metric&lang=uz";

            JSONObject json = new JSONObject(readURL(apiUrl));
            JSONObject main = json.getJSONObject("main");
            JSONObject weather = json.getJSONArray("weather").getJSONObject(0);
            JSONObject wind = json.getJSONObject("wind");

            double temp = main.getDouble("temp");
            int humidity = main.getInt("humidity");
            double windSpeed = wind.getDouble("speed");
            String description = weather.getString("description");

            return city + " shahridagi ob-havo:\n" +
                    "🌡 Harorat: " + temp + "°C\n" +
                    "💧 Namlik: " + humidity + "%\n" +
                    "🌬 Shamol: " + windSpeed + " m/s\n" +
                    "⛅️ Holat: " + description;

        } catch (Exception e) {
            return "❌ Ob-havo ma’lumotlarini olishda xatolik. Shahar nomini tekshiring!";
        }
    }

    private String getWeeklyWeather(String city) {
        try {
            String apiUrl = "https://api.openweathermap.org/data/2.5/forecast?q=" + city +
                    "&appid=" + WEATHER_API_KEY + "&units=metric&lang=uz";
            JSONObject json = new JSONObject(readURL(apiUrl));
            JSONArray list = json.getJSONArray("list");

            StringBuilder sb = new StringBuilder("📅 5 kunlik ob-havo (" + city + "):\n");
            Set<String> addedDates = new HashSet<>();
            for (int i = 0; i < list.length(); i++) {
                JSONObject obj = list.getJSONObject(i);
                String dt_txt = obj.getString("dt_txt");
                String date = dt_txt.split(" ")[0];

                if (!addedDates.contains(date)) {
                    addedDates.add(date);
                    JSONObject main = obj.getJSONObject("main");
                    String desc = obj.getJSONArray("weather").getJSONObject(0).getString("description");

                    sb.append("\n📆 ").append(date).append(":\n")
                            .append("🌡 ").append(main.getDouble("temp")).append("°C\n")
                            .append("⛅️ ").append(desc).append("\n");
                }

                if (addedDates.size() >= 5) break;
            }
            return sb.toString();
        } catch (Exception e) {
            return "❌ 5 kunlik prognozda xatolik.";
        }
    }

    private String getHourlyWeather(String city) {
        try {
            String apiUrl = "https://api.openweathermap.org/data/2.5/forecast?q=" + city +
                    "&appid=" + WEATHER_API_KEY + "&units=metric&lang=uz";
            JSONObject json = new JSONObject(readURL(apiUrl));
            JSONArray list = json.getJSONArray("list");

            StringBuilder sb = new StringBuilder("⏰ Yaqin 5 ta soatlik ob-havo (" + city + "):\n");
            for (int i = 0; i < 5 && i < list.length(); i++) {
                JSONObject obj = list.getJSONObject(i);
                String time = obj.getString("dt_txt");
                JSONObject main = obj.getJSONObject("main");
                String desc = obj.getJSONArray("weather").getJSONObject(0).getString("description");

                sb.append("\n🕒 ").append(time).append(":\n")
                        .append("🌡 ").append(main.getDouble("temp")).append("°C\n")
                        .append("⛅️ ").append(desc).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return "❌ Soatlik prognozda xatolik.";
        }
    }

    private String readURL(String apiUrl) throws Exception {
        URL url = new URL(apiUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) sb.append(line);
        in.close();
        return sb.toString();
    }
    private void sendMessage(String chatId, String text, List<String> cities) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);

        if (cities != null && !cities.isEmpty()) {
            ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
            List<KeyboardRow> rows = new ArrayList<>();

            for (String city : cities) {
                KeyboardRow row = new KeyboardRow();
                row.add(city);
                rows.add(row);
            }

            keyboardMarkup.setKeyboard(rows);
            keyboardMarkup.setResizeKeyboard(true);
            message.setReplyMarkup(keyboardMarkup);
        }

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

}
