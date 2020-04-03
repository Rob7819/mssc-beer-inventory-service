package guru.sfg.beer.inventory.service.services;

import guru.sfg.beer.inventory.service.config.JmsConfig;
import guru.sfg.brewery.model.events.AllocateOrderRequest;
import guru.sfg.brewery.model.events.AllocateOrderResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class AllocationListener {

    private final AllocationService allocationService;
    private final JmsTemplate jmsTemplate;

    @JmsListener(destination = JmsConfig.ALLOCATE_ORDER_QUEUE)
    public void listen(AllocateOrderRequest request){
        AllocateOrderResponse.AllocateOrderResponseBuilder builder = AllocateOrderResponse.builder();
        builder.beerOrderDto(request.getBeerOrderDto());

        try{
            Boolean allocationResult = allocationService.allocateOrder(request.getBeerOrderDto());

            if(allocationResult){
                builder.pendingInventory(false);
            }
            else{
                builder.pendingInventory(true);
            }
            builder.allocationError(false);
        }
        catch (Exception e){
            builder.allocationError(true);
            log.error("Allocation error with order Id: " + request.getBeerOrderDto().getId());
        }

        jmsTemplate.convertAndSend(JmsConfig.ALLOCATE_ORDER_RESPONSE_QUEUE, builder.build());

    }
}
