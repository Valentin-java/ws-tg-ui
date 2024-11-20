package ru.helper.worker.rest.external.bid.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import ru.helper.worker.rest.external.bid.model.BidChangeStatusRequest;
import ru.helper.worker.rest.external.common.ExternalClientService;
import ru.helper.worker.rest.external.feign.BidFeignService;

@Slf4j
@Service
@RequiredArgsConstructor
public class AcceptBidClient implements ExternalClientService<BidChangeStatusRequest, ResponseEntity<Void>>  {

    private final BidFeignService service;

    @Override
    public ResponseEntity<Void> doRequest(BidChangeStatusRequest request) {
        try {
            service.acceptBid(request);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error occurred while sending accept bid request: {}", request, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}