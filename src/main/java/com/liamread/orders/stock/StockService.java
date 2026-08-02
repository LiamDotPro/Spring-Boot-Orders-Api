package com.liamread.orders.stock;

import com.liamread.orders.stock.exception.InsufficientStockException;
import com.liamread.orders.stock.exception.UnknownSkuException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The only thing in the application that owns stock quantities and SKU prices.
 *
 * <p>The mutating methods here are called from inside {@code OrderService}'s transaction and join
 * it — one transaction spanning the order row and every stock row it touches. That is the only
 * reason partial allocation is impossible: take the annotation off {@code acceptOrder} and a
 * two-line order whose second line is short leaves the first line allocated, which looks like a
 * race condition and is not.
 *
 * <p>That in-process call is also the seam. When stock eventually moves to its own service it
 * becomes a message, and this all-or-nothing transaction becomes a saga with a compensating
 * release — {@link #release} already exists precisely because cancel needs it today.
 */
@Slf4j
@Service
public class StockService {

    private final StockRepository stockRepository;

    public StockService(StockRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    /**
     * Price and description for the ordering side, at order time.
     *
     * <p>The caller copies these onto the order line rather than looking them up again later. That
     * is correctness, not caching: if the price lived only here, repricing a bookcase tomorrow
     * would silently rewrite the total of every order placed today, shipped ones included.
     */
    @Transactional(readOnly = true)
    public PricedSku lookup(String sku) {
        return PricedSku.from(requireItem(sku));
    }

    @Transactional(readOnly = true)
    public List<StockItem> findAll() {
        return stockRepository.findAllByOrderBySkuAsc();
    }

    @Transactional(readOnly = true)
    public StockItem getBySku(String sku) {
        return requireItem(sku);
    }

    /**
     * Reserve goods for an accepted order.
     *
     * <p>The pre-read exists only to tell "no such SKU" apart from "not enough of it" in the error;
     * it is not the safety check. The guard is in the {@code WHERE} clause of the update, which is
     * why two requests racing for the last wardrobe cannot both win.
     */
    @Transactional
    public void allocate(String sku, int quantity) {
        StockItem item = requireItem(sku);

        if (stockRepository.allocate(sku, quantity) == 0) {
            throw new InsufficientStockException(sku, quantity, item.getQuantityAvailable());
        }

        log.info("Allocated {} x {}", quantity, sku);
    }

    /** Give a reservation back — cancelling an order that had already been accepted. */
    @Transactional
    public void release(String sku, int quantity) {
        requireItem(sku);

        if (stockRepository.release(sku, quantity) == 0) {
            // Releasing more than is allocated means the two sides have drifted apart. Fail loudly
            // rather than letting quantityAllocated go negative and quietly corrupt availability.
            throw new IllegalStateException(
                    "Cannot release " + quantity + " of " + sku + " — more than is allocated");
        }

        log.info("Released {} x {}", quantity, sku);
    }

    /** The goods ship. On-hand and allocated both fall, so availability does not move. */
    @Transactional
    public void consume(String sku, int quantity) {
        requireItem(sku);

        if (stockRepository.consume(sku, quantity) == 0) {
            throw new IllegalStateException(
                    "Cannot consume " + quantity + " of " + sku + " — not allocated or not on hand");
        }

        log.info("Consumed {} x {}", quantity, sku);
    }

    /** Goods arrive. Returns the item as it now stands, re-read after the update. */
    @Transactional
    public StockItem restock(String sku, int quantity) {
        requireItem(sku);

        if (stockRepository.restock(sku, quantity) == 0) {
            throw new UnknownSkuException(sku);
        }

        log.info("Restocked {} x {}", quantity, sku);

        // The bulk update cleared the persistence context, so anything read before it is stale.
        return requireItem(sku);
    }

    private StockItem requireItem(String sku) {
        return stockRepository.findBySku(sku).orElseThrow(() -> new UnknownSkuException(sku));
    }
}
