package ru.helper.worker.rest.external.bid.interfaces;

import org.springframework.http.ResponseEntity;
import ru.helper.worker.rest.external.bid.model.BidChangeStatusRequest;

public interface AcceptBidClient {

    ResponseEntity<Void> doRequest(BidChangeStatusRequest request);
}
