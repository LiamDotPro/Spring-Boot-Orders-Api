package com.liamread.orders.stock;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Every quantity change is a <strong>conditional update</strong>: the guard and the write happen in
 * one statement, so the database does the checking and there is no read-then-write gap for a
 * concurrent request to slip into.
 *
 * <p>The alternative approaches both work and both cost more. Optimistic locking ({@code @Version}
 * is already on {@code BaseEntity}) makes the loser fail with an exception you then have to decide
 * whether to retry. Pessimistic locking ({@code SELECT ... FOR UPDATE}) serialises everyone through
 * one row for the duration of your application logic. A conditional update holds no lock across
 * your code at all — {@code if (updated == 0) throw} is doing the entire job.
 *
 * <p><strong>Two consequences of bulk JPQL updates to keep in mind.</strong> They bypass
 * {@code @Version}, so these statements are not optimistically locked — the {@code WHERE} clause is
 * the concurrency control here, which is the whole point. And they run straight against the
 * database, so the persistence context knows nothing about them; hence
 * {@code flushAutomatically} (push pending changes first, so they are not overwritten) and
 * {@code clearAutomatically} (drop now-stale entities afterwards, so nobody reads a wrong number).
 *
 * <p>{@code clearAutomatically} detaches <em>everything</em>, including entities the caller is
 * midway through changing. Callers must flush any pending state change before invoking these — see
 * {@code OrderService.acceptOrder}, which sets the order status first for exactly that reason.
 */
public interface StockRepository extends JpaRepository<StockItem, UUID> {

    Optional<StockItem> findBySku(String sku);

    List<StockItem> findAllByOrderBySkuAsc();

    /** Accept: reserve goods, but only if enough are actually available. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
           update StockItem s
              set s.quantityAllocated = s.quantityAllocated + :quantity
            where s.sku = :sku
              and s.quantityOnHand - s.quantityAllocated >= :quantity
           """)
    int allocate(@Param("sku") String sku, @Param("quantity") int quantity);

    /** Cancel an allocated order: hand the reservation back. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
           update StockItem s
              set s.quantityAllocated = s.quantityAllocated - :quantity
            where s.sku = :sku
              and s.quantityAllocated >= :quantity
           """)
    int release(@Param("sku") String sku, @Param("quantity") int quantity);

    /**
     * Finalize: the goods leave the building. Both counters drop by the same amount, which is why
     * shipping does not change what is available — it was already spoken for.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
           update StockItem s
              set s.quantityOnHand = s.quantityOnHand - :quantity,
                  s.quantityAllocated = s.quantityAllocated - :quantity
            where s.sku = :sku
              and s.quantityAllocated >= :quantity
              and s.quantityOnHand >= :quantity
           """)
    int consume(@Param("sku") String sku, @Param("quantity") int quantity);

    /** A forklift arrived. The only operation that raises on-hand. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
           update StockItem s
              set s.quantityOnHand = s.quantityOnHand + :quantity
            where s.sku = :sku
           """)
    int restock(@Param("sku") String sku, @Param("quantity") int quantity);
}
