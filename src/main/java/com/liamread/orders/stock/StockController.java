package com.liamread.orders.stock;

import com.liamread.orders.stock.dto.RestockRequest;
import com.liamread.orders.stock.dto.StockItemResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/stock")
public class StockController {

    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    @GetMapping
    public List<StockItemResponse> listStock() {
        return stockService.findAll().stream().map(StockItemResponse::from).toList();
    }

    @GetMapping("/{sku}")
    public ResponseEntity<StockItemResponse> getStock(@PathVariable String sku) {
        return ResponseEntity.ok(StockItemResponse.from(stockService.getBySku(sku)));
    }

    /** Obviously an admin operation. Auth for it is out of scope until there is any auth at all. */
    @PostMapping("/{sku}/restock")
    public ResponseEntity<StockItemResponse> restock(
            @PathVariable String sku,
            @Valid @RequestBody RestockRequest request
    ) {
        return ResponseEntity.ok(StockItemResponse.from(stockService.restock(sku, request.quantity())));
    }
}
