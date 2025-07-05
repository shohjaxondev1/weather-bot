package org.example;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

public class WeatherBot extends TelegramLongPollingBot {

    private final String BOT_TOKEN = "7139643006:AAFxBu-PIq9q-zW53a5fM-3CrjtIUkw-w8s";
    private final String BOT_USERNAME = "uz1_weather_bot";
    private final String WEATHER_API_KEY = "11ff03f21c7d0af2918c6ef91075beda";
    private final String CHANNEL_USERNAME = "@Nemat0vDev";

    private final Map<Long, List<String>> userCities = new HashMap<>();
    private final String CITIES_FILE = "user_cities.dat";

    public WeatherBot() {
        loadSavedCities();
    }

    @Override
    public String getBotUsername() {
        return BOT_USERNAME;
    }

    @Override
    public String getBotToken() {
        return System.getenv("BOT_TOKEN");
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasCallbackQuery()) {
            CallbackQuery callback = update.getCallbackQuery();
            Long chatId = callback.getMessage().getChatId();

            if (callback.getData().equals("check_subscribe")) {
                if (isUserSubscribed(chatId)) {
                    sendMessage(chatId, "✅ Obuna muvaffaqiyatli tekshirildi!");
                    sendMainMenu(chatId);
                } else {
                    sendMessage(chatId, "❌ Hali ham obuna emassiz. Iltimos, kanalga obuna bo‘ling: https://t.me/Nemat0vDev");
                }
            }
            return;
        }

        if (update.hasMessage()) {
            Message msg = update.getMessage();
            Long chatId = msg.getChatId();
            String text = msg.getText();

            if (!isUserSubscribed(chatId)) {
                sendSubscribeMessage(chatId);
                return;
            }

            switch (text) {
                case "/start":
                    sendMessage(chatId, "🤖 Ob-havo botiga xush kelibsiz!");
                    sendMainMenu(chatId);
                    break;

                case "📍 Saqlangan shaharlar":
                    List<String> cities = userCities.get(chatId);
                    if (cities == null || cities.isEmpty()) {
                        sendMessage(chatId, "📍 Hozircha saqlangan shahar yo‘q.");
                    } else {
                        sendMessage(chatId, "📍 Saqlangan shaharlar:\n" + String.join("\n", cities));
                    }
                    break;

                case "➕ Yangi shahar qo‘shish":
                    sendMessage(chatId, "Yangi shahar nomini kiriting:");
                    break;

                case "❌ Tozalash":
                    userCities.remove(chatId);
                    saveCities();
                    sendMessage(chatId, "✅ Barcha saqlangan shaharlar o‘chirildi.");
                    break;

                case "🕒 Soatlik prognoz":
                    sendMessage(chatId, getCityListBasedResponse(chatId, this::getHourlyWeather, "soatlik prognoz uchun"));
                    break;

                case "📅 Haftalik prognoz":
                    sendMessage(chatId, getCityListBasedResponse(chatId, this::getWeeklyWeather, "haftalik prognoz uchun"));
                    break;

                case "🆘 Admin bilan bog‘lanish":
                    sendMessage(chatId, "📞 Admin: @Xon_3500");
                    break;

                default:
                    List<String> userCityList = userCities.computeIfAbsent(chatId, k -> new ArrayList<>());
                    if (!userCityList.contains(text)) {
                        userCityList.add(text);
                        saveCities();
                    }
                    String result = getWeather(text);
                    sendMessage(chatId, result);
                    sendMainMenu(chatId);
                    break;
            }
        }
    }

    private void sendSubscribeMessage(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("❌ Botdan foydalanish uchun quyidagi kanalga obuna bo‘ling:");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        InlineKeyboardButton btn1 = new InlineKeyboardButton();
        btn1.setText("📢 Kanalga obuna bo‘lish");
        btn1.setUrl("https://t.me/Nemat0vDev");

        InlineKeyboardButton btn2 = new InlineKeyboardButton();
        btn2.setText("✅ Tekshirish");
        btn2.setCallbackData("check_subscribe");

        rows.add(Collections.singletonList(btn1));
        rows.add(Collections.singletonList(btn2));
        markup.setKeyboard(rows);

        message.setReplyMarkup(markup);
        send(message);
    }

    private void sendMainMenu(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("Kerakli bo‘limni tanlang:");

        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        List<KeyboardRow> rows = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add("📍 Saqlangan shaharlar");
        row1.add("➕ Yangi shahar qo‘shish");

        KeyboardRow row2 = new KeyboardRow();
        row2.add("🕒 Soatlik prognoz");
        row2.add("📅 Haftalik prognoz");

        KeyboardRow row3 = new KeyboardRow();
        row3.add("❌ Tozalash");
        row3.add("🆘 Admin bilan bog‘lanish");

        rows.add(row1);
        rows.add(row2);
        rows.add(row3);

        markup.setKeyboard(rows);
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(false);
        message.setReplyMarkup(markup);

        send(message);
    }

    private boolean isUserSubscribed(Long chatId) {
        try {
            ChatMember member = execute(new GetChatMember(CHANNEL_USERNAME, chatId));
            String status = member.getStatus();
            return status.equals("member") || status.equals("administrator") || status.equals("creator");
        } catch (TelegramApiException e) {
            return false;
        }
    }

    private void sendMessage(Long chatId, String text) {
        SendMessage message = new SendMessage(chatId.toString(), text);
        send(message);
    }

    private void send(SendMessage message) {
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private String getCityListBasedResponse(Long chatId, java.util.function.Function<String, String> weatherFunc, String reason) {
        List<String> cities = userCities.get(chatId);
        if (cities == null || cities.isEmpty()) return "⚠️ " + reason + " shahar kiriting.";
        StringBuilder sb = new StringBuilder();
        for (String city : cities) {
            sb.append(weatherFunc.apply(city)).append("\n\n");
        }
        return sb.toString();
    }

    private String getWeather(String city) {
        try {
            String api = "https://api.openweathermap.org/data/2.5/weather?q=" + city +
                    "&appid=" + WEATHER_API_KEY + "&units=metric&lang=uz";
            JSONObject json = new JSONObject(readURL(api));
            JSONObject main = json.getJSONObject("main");
            JSONObject weather = json.getJSONArray("weather").getJSONObject(0);
            JSONObject wind = json.getJSONObject("wind");

            return "🌆 " + city + " ob-havosi:\n" +
                    "🌡 " + main.getDouble("temp") + "°C\n" +
                    "💧 Namlik: " + main.getInt("humidity") + "%\n" +
                    "🌬 Shamol: " + wind.getDouble("speed") + " m/s\n" +
                    "⛅️ Holat: " + weather.getString("description");
        } catch (Exception e) {
            return "❌ Shahar topilmadi yoki xatolik.";
        }
    }

    private String getWeeklyWeather(String city) {
        try {
            String api = "https://api.openweathermap.org/data/2.5/forecast?q=" + city +
                    "&appid=" + WEATHER_API_KEY + "&units=metric&lang=uz";
            JSONObject json = new JSONObject(readURL(api));
            JSONArray list = json.getJSONArray("list");

            StringBuilder sb = new StringBuilder("📅 5 kunlik (" + city + "):\n");
            Set<String> dates = new HashSet<>();

            for (int i = 0; i < list.length(); i++) {
                JSONObject obj = list.getJSONObject(i);
                String date = obj.getString("dt_txt").split(" ")[0];

                if (dates.add(date)) {
                    JSONObject main = obj.getJSONObject("main");
                    String desc = obj.getJSONArray("weather").getJSONObject(0).getString("description");

                    sb.append("\n📆 ").append(date).append(":\n")
                            .append("🌡 ").append(main.getDouble("temp")).append("°C\n")
                            .append("⛅️ ").append(desc);
                }

                if (dates.size() >= 5) break;
            }

            return sb.toString();

        } catch (Exception e) {
            return "❌ 5 kunlik ob-havo olishda xatolik.";
        }
    }

    private String getHourlyWeather(String city) {
        try {
            String api = "https://api.openweathermap.org/data/2.5/forecast?q=" + city +
                    "&appid=" + WEATHER_API_KEY + "&units=metric&lang=uz";
            JSONObject json = new JSONObject(readURL(api));
            JSONArray list = json.getJSONArray("list");

            StringBuilder sb = new StringBuilder("🕒 Soatlik (" + city + "):\n");
            for (int i = 0; i < 5 && i < list.length(); i++) {
                JSONObject obj = list.getJSONObject(i);
                String time = obj.getString("dt_txt");
                JSONObject main = obj.getJSONObject("main");
                String desc = obj.getJSONArray("weather").getJSONObject(0).getString("description");

                sb.append("\n🕓 ").append(time).append("\n")
                        .append("🌡 ").append(main.getDouble("temp")).append("°C\n")
                        .append("⛅️ ").append(desc);
            }

            return sb.toString();

        } catch (Exception e) {
            return "❌ Soatlik ob-havo olishda xatolik.";
        }
    }

    private String readURL(String apiUrl) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
        conn.setRequestMethod("GET");

        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) sb.append(line);
        in.close();
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
        } catch (Exception ignored) {
        }
    }
}
