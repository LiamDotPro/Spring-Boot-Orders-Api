package com.liamread.orders.order.pricing;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

@Component
public class PriceCatalog {

    private static final Map<String, CatalogueItem> items = Map.of(
            "SKU-1", new CatalogueItem("SKU-1", "Bookcase", 1, new BigDecimal("320.99")),
            "SKU-2", new CatalogueItem("SKU-2", "Shelf", 11, new BigDecimal("11.99")),
            "SKU-3", new CatalogueItem("SKU-3", "Wardrobe", 3, new BigDecimal("33.99"))
    );

    public Optional<CatalogueItem> lookupItem(String sku) {
        return Optional.ofNullable(items.get(sku));
    }

}
