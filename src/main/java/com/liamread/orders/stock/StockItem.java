package com.liamread.orders.stock;

import com.liamread.orders.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * One SKU: what it costs, how much is physically here, and how much is spoken for.
 *
 * <p>Replaces the hardcoded {@code PriceCatalog} map. Its {@code CatalogueItem.quantity} was
 * ambiguous to the point of being a bug — it meant stock on hand but was returned to customers as
 * the quantity they had ordered. The three fields here have exactly one meaning each.
 *
 * <p><strong>Available is derived, never stored.</strong> The moment {@code quantityAvailable}
 * becomes a column there are two sources of truth that can disagree, and every future bug turns
 * into "which of the three numbers is lying?". The cost is that you cannot index it — if you ever
 * need "find everything with available &lt; 5" to use an index, a Postgres generated column is the
 * real answer, not a field you maintain by hand.
 *
 * <p>Pricing and inventory are genuinely different concerns and the textbook answer splits them
 * into two tables. Deliberately not yet: one row per SKU keeps this to one repository and one lock.
 * Split when you need price history (a price becomes a row with a valid-from date) or stock per
 * warehouse (the level gains a location and stops being one-row-per-SKU).
 */
@Entity
@Table(
        name = "stock_items",
        uniqueConstraints = @UniqueConstraint(name = "uk_stock_item_sku", columnNames = "sku")
)
@Getter
public class StockItem extends BaseEntity {

    @Column(nullable = false, updatable = false)
    private String sku;

    @Column(nullable = false)
    private String description;

    /** Matches {@code OrderEntity.total} and {@code OrderLineEntity.unitPrice}. */
    @Column(precision = 19, scale = 4, nullable = false)
    private BigDecimal unitPrice;

    /** Physically in the warehouse. Only changes when goods actually move. */
    @Column(nullable = false)
    private int quantityOnHand;

    /** Reserved for accepted orders that have not shipped. */
    @Column(nullable = false)
    private int quantityAllocated;

    protected StockItem() { }   // for Hibernate only

    public StockItem(String sku, String description, BigDecimal unitPrice, int quantityOnHand) {
        this.sku = sku;
        this.description = description;
        this.unitPrice = unitPrice;
        this.quantityOnHand = quantityOnHand;
        this.quantityAllocated = 0;
    }

    /**
     * {@code @Transient} so JPA ignores it — this is a computed getter, not a mapped column.
     *
     * <p>Note the invariant worth checking yourself on: shipping decrements on-hand and allocated
     * by the same amount, so it leaves this number unchanged. Available only moves on accept,
     * cancel and restock.
     */
    @Transient
    public int getQuantityAvailable() {
        return quantityOnHand - quantityAllocated;
    }
}
