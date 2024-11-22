package ru.helper.worker.business.received_bid.process.state.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import ru.helper.worker.business.received_bid.process.context.BidContext;
import ru.helper.worker.business.received_bid.process.state.BidState;
import ru.helper.worker.controller.events.MessageEditEvent;
import ru.helper.worker.controller.events.MessageSendEvent;
import ru.helper.worker.controller.events.OrderProcessCompletedEvent;
import ru.helper.worker.rest.external.bid.interfaces.AcceptBidClient;
import ru.helper.worker.rest.external.bid.interfaces.RejectBidClient;
import ru.helper.worker.rest.external.bid.model.BidChangeStatusRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static ru.helper.worker.business.received_bid.model.enums.BidReceivePayloadEnum.ACCEPT_BID;
import static ru.helper.worker.business.received_bid.model.enums.BidReceivePayloadEnum.INFO_BID;
import static ru.helper.worker.business.received_bid.model.enums.BidReceivePayloadEnum.REJECT_BID;
import static ru.helper.worker.business.received_bid.model.enums.BidReceivePayloadEnum.SEND_MESSAGE_BID;

/**
 * Пришел отзыв от мастера.
 * Мы публикуем об этом сообщение, предупреждаем что время жизни предложения ограничен. (т.о. чистим память приложения)
 * Кейсы:
 * BidReceiveState -> AcceptedBidState -> CompletedBidState -> FeedbackBidCase ->
 * Отказаться от предложения ->
 * Задать вопрос исполнителю ->
 * Подробнее про исполнителя ->
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BidReceiveState implements BidState {

    private static final String FAILED_MSG = "Извините, но похоже на то, что предложение от исполнителя уже не актуально. " +
            "\nНе огорчайтесь, будем искать дальше.";
    private static final String NOTICE_MESSAGE = "Похоже что предложение данного мастера Вас не заинтересовало. " +
            "\nБудем искать дальше.";
    private static final String WARNING_MESSAGE = "Похоже что в процессе произошла ошибка. В любом случае мы отменили заявку. " +
            "\nПри необходимости обратитесь в поддержку.";

    private final ApplicationEventPublisher eventPublisher;
    private final AcceptBidClient acceptBidClient;
    private final RejectBidClient rejectBidClient;
    private final AcceptedBidState nextState;

    @Override
    public void handleInput(BidContext context, String input) {
        var orderId = context.getRequest().orderId();
        var bidId = context.getRequest().bidId();

        if (ACCEPT_BID.name().equals(input)) {

            // сходим проверим что отклик актуальный
            var request = new BidChangeStatusRequest(orderId, bidId);
            var response = acceptBidClient.doRequest(request);

            // Проверяем ответ
            if (response != null && response.hasBody() && response.getStatusCode().is2xxSuccessful()) {
                log.warn("Success to process accept bid order with ID: {}", orderId);
                updateState(context);
            } else {
                log.warn("Failed to process accept bid order with ID: {}", orderId);
                // подумать можем ли текст ошибки прокинуть клиенту ?
                eventPublisher.publishEvent(new MessageEditEvent(this, context.getChatId(), context.getMessageIdActualState(), FAILED_MSG));
                eventPublisher.publishEvent(new OrderProcessCompletedEvent(this, context.getChatId()));
            }
        }
        if (REJECT_BID.name().equals(input)) {
            var request = new BidChangeStatusRequest(orderId, bidId);
            var response = rejectBidClient.doRequest(request);

            if (response != null && response.hasBody() && response.getStatusCode().is2xxSuccessful()) {
                log.warn("Success to process reject bid order with ID: {}", orderId);
                eventPublisher.publishEvent(new MessageEditEvent(this, context.getChatId(), context.getMessageIdActualState(), NOTICE_MESSAGE));
            } else {
                eventPublisher.publishEvent(new MessageEditEvent(this, context.getChatId(), context.getMessageIdActualState(), WARNING_MESSAGE));
            }
            eventPublisher.publishEvent(new OrderProcessCompletedEvent(this, context.getChatId()));

        }

        if (SEND_MESSAGE_BID.name().equals(input)) {
            // обратно придет ответ сообщения от стандартной формы как сейчас, только с вопрос/ответом в этой же форме
            // Мы перезатрем текущую форму отклика на уведомление о том, что мы отправили Ваш запрос мастеру, как он ответит мы сообщим
//            eventPublisher.publishEvent(new MessageSendEvent(this, context.getChatId(), NOTICE_MESSAGE));
//            eventPublisher.publishEvent(new OrderProcessCompletedEvent(this, context.getChatId()));
        }

    }

    // Добавить рейтинг
    @Override
    public void enter(BidContext context) {
        String summary = String.format(
                "Мы нашли мастера для Вашего заказа. " +
                        "\n\n Мастера зовут: %s. " +
                        "\n Сообщение от мастера: " +
                        "\n %s" +
                        "\n\n Предлагаемый бюджет: %s руб. " +
                        "\n\n Обращаем Ваше внимание на то, " +
                        "что данное предложение актуально 48 часов.",
                context.getRequest().offer().specialistName(),
                context.getRequest().offer().message(),
                context.getRequest().offer().price()
        );

        List<List<InlineKeyboardButton>> buttons = new ArrayList<>();
        buttons.add(getUpperRowInlineButtons());
        buttons.add(getLowerRowInlineButtons());

        InlineKeyboardMarkup keys = InlineKeyboardMarkup.builder().keyboard(buttons).build();
        var messageEvent = new MessageSendEvent(this, context.getChatId(), summary, keys);
        var messageId = getMessageIdAfterPublishMessage(messageEvent);
        context.setMessageIdActualState(messageId);
    }

    @Override
    public void updateState(BidContext context) {
        context.setCurrentState(nextState);
        context.getCurrentState().enter(context);
    }

    private List<InlineKeyboardButton> getUpperRowInlineButtons() {
        return List.of(InlineKeyboardButton.builder()
                        .text("Выбрать мастера")
                        .callbackData(ACCEPT_BID.name())
                        .build(),
                InlineKeyboardButton.builder()
                        .text("Отказаться от предложения")
                        .callbackData(REJECT_BID.name())
                        .build());
    }

    private List<InlineKeyboardButton> getLowerRowInlineButtons() {
        return List.of(InlineKeyboardButton.builder()
                        .text("Задать вопрос исполнителю")
                        .callbackData(SEND_MESSAGE_BID.name())
                        .build());
    }

    public Integer getMessageIdAfterPublishMessage(MessageSendEvent event) {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        event.setCallback(future::complete); // Устанавливаем callback для передачи messageId
        eventPublisher.publishEvent(event); // Публикуем событие
        try {
            return future.get(); // Ожидаем завершения
        } catch (Exception e) {
            throw new RuntimeException("Error getting messageId from event", e);
        }
    }
}
