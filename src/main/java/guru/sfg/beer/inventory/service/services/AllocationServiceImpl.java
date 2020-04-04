package guru.sfg.beer.inventory.service.services;

import guru.sfg.beer.inventory.service.domain.BeerInventory;
import guru.sfg.beer.inventory.service.repositories.BeerInventoryRepository;
import guru.sfg.brewery.model.BeerOrderDto;
import guru.sfg.brewery.model.BeerOrderLineDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@RequiredArgsConstructor
@Service
public class AllocationServiceImpl implements AllocationService {

    private final BeerInventoryRepository beerInventoryRepository;

    @Override
    public Boolean allocateOrder(BeerOrderDto beerOrderDto) {
        log.debug("Allocating OrderId: " + beerOrderDto.getId());

        AtomicInteger totalOrdered = new AtomicInteger();
        AtomicInteger totalAllocated = new AtomicInteger();

        beerOrderDto.getBeerOrderLines().forEach(beerOrderLine -> {
            if ((((beerOrderLine.getOrderQuantity() != null ? beerOrderLine.getOrderQuantity() : 0)
                    - (beerOrderLine.getQuantityAllocated() != null ? beerOrderLine.getQuantityAllocated() : 0)) > 0)) {
                allocateBeerOrderLine(beerOrderLine);
            }
            totalOrdered.set(totalOrdered.get() + beerOrderLine.getOrderQuantity());
            totalAllocated.set(totalAllocated.get() + (beerOrderLine.getQuantityAllocated() != null ? beerOrderLine.getQuantityAllocated() : 0));
        });

        log.debug("Total Ordered: " + totalOrdered.get() + " Total Allocated: " + totalAllocated.get());

        return totalOrdered.get() == totalAllocated.get();
    }

    private void allocateBeerOrderLine(BeerOrderLineDto beerOrderLine) {
        //There can be multiple records for the SAME ITEM which is why we have a findAllByUpc rather than findByUpc.
        //Also records can be removed once quantity reaches 0 (on partial/full allocations) as we do not update records
        //when returning inventory, only when removing inventory on full allocations.
        List<BeerInventory> beerInventoryList = beerInventoryRepository.findAllByUpc(beerOrderLine.getUpc());

        beerInventoryList.forEach(beerInventory -> {
            int inventory = (beerInventory.getQuantityOnHand() == null) ? 0 : beerInventory.getQuantityOnHand();
            int orderQty = (beerOrderLine.getOrderQuantity() == null) ? 0 : beerOrderLine.getOrderQuantity();
            int allocatedQty = (beerOrderLine.getQuantityAllocated() == null) ? 0 : beerOrderLine.getQuantityAllocated();
            int qtyToAllocate = orderQty - allocatedQty;

            if (inventory >= qtyToAllocate) { // full allocation
                inventory = inventory - qtyToAllocate;
                beerOrderLine.setQuantityAllocated(orderQty);
                beerInventory.setQuantityOnHand(inventory);

                beerInventoryRepository.save(beerInventory);

            } else if (inventory > 0) { //partial allocation
                beerOrderLine.setQuantityAllocated(allocatedQty + inventory);
                beerInventory.setQuantityOnHand(0);//set this to zero for easier processing

            }
            //it's possible for order demands to match the on hand quantity of a record so we need to put in a handle
            //to ensure we don't end up with records of zero quantities.
            if(beerInventory.getQuantityOnHand() == 0){

                //we do not rely on a single record for any given item so we can delete a record once quantity drops to 0
                beerInventoryRepository.delete(beerInventory);

            }
        });

    }

    @Override
    public void deAllocateOrder(BeerOrderDto beerOrderDto) {
        //There can be multiple records of the SAME ITEM which is why we have a findAllByUpc on our repository and
        //the following works as it will simply create a new record adding the inventory back to the database.  This
        //is simpler than finding a single record and updating the value in the database.  There may be drawbacks in
        //performance with this, however.  There would also need to be protection against dud records cluttering up
        //the database.
        beerOrderDto.getBeerOrderLines().forEach(beerOrderLine -> {
            BeerInventory beerInventory = BeerInventory.builder()
                    .beerId(beerOrderLine.getBeerId())
                    .upc(beerOrderLine.getUpc())
                    .quantityOnHand(beerOrderLine.getOrderQuantity())
                    .build();

            BeerInventory savedInventory = beerInventoryRepository.save(beerInventory);

            log.debug("Saved Inventory of item upc: " + savedInventory.getUpc() + " for inventory id: " + savedInventory.getId());

        });

    }



}
