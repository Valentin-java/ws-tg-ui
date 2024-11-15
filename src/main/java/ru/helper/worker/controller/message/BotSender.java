package ru.helper.worker.controller.message;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

public interface BotSender {
    void sendMessage(SendMessage message) throws TelegramApiException;
}
