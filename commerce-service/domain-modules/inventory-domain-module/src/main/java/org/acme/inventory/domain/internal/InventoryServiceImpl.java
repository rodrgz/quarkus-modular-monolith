package org.acme.inventory.domain.internal;

import org.acme.inventory.domain.api.InventoryService;
import org.acme.inventory.domain.api.dto.ProductDTO;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of the Inventory Service.
 * 
 * This is an in-memory implementation for the PoC.
 * In production, it would be replaced by an implementation with persistence.
 * 
 * Note that this class does NOT have framework annotations.
 */
public class InventoryServiceImpl implements InventoryService {

    // In-memory stock simulation
    private final Map<String, ProductDTO> products;

    public InventoryServiceImpl() {
        this.products = new ConcurrentHashMap<>();
        initializeProducts();
    }
    
    /**
     * Constructor with data injection (for tests).
     */
    public InventoryServiceImpl(Map<String, ProductDTO> initialProducts) {
        this.products = new ConcurrentHashMap<>(initialProducts);
    }

    private void initializeProducts() {
        // Example products for the PoC
        products.put("PROD-001", new ProductDTO(
            "PROD-001", "Notebook Dell Inspiron", 
            "Notebook 15.6\" Intel i7 16GB RAM",
            new BigDecimal("4599.90"), 10, "Electronics"
        ));
        products.put("PROD-002", new ProductDTO(
            "PROD-002", "Mouse Logitech MX Master", 
            "Wireless Ergonomic Mouse",
            new BigDecimal("599.90"), 25, "Peripherals"
        ));
        products.put("PROD-003", new ProductDTO(
            "PROD-003", "Mechanical Keyboard Keychron", 
            "Wireless Mechanical Keyboard RGB",
            new BigDecimal("899.90"), 15, "Peripherals"
        ));
        products.put("PROD-004", new ProductDTO(
            "PROD-004", "Monitor LG UltraWide 34\"", 
            "Curved Monitor 34\" WQHD",
            new BigDecimal("2899.90"), 5, "Monitors"
        ));
        products.put("PROD-005", new ProductDTO(
            "PROD-005", "Webcam Logitech C920", 
            "Webcam Full HD 1080p",
            new BigDecimal("449.90"), 0, "Peripherals" // Out of stock!
        ));
    }

    @Override
    public Optional<ProductDTO> findById(String productId) {
        return Optional.ofNullable(products.get(productId));
    }

    @Override
    public List<ProductDTO> findByIds(List<String> productIds) {
        return productIds.stream()
            .map(products::get)
            .filter(p -> p != null)
            .toList();
    }

    @Override
    public List<ProductDTO> listAvailable() {
        return products.values().stream()
            .filter(ProductDTO::isAvailable)
            .toList();
    }

    @Override
    public boolean reserve(String productId, int quantity) {
        ProductDTO product = products.get(productId);
        if (product == null || !product.hasStockFor(quantity)) {
            return false;
        }
        
        // Update stock (in production this would be atomic with DB)
        ProductDTO updated = new ProductDTO(
            product.productId(),
            product.name(),
            product.description(),
            product.price(),
            product.quantityAvailable() - quantity,
            product.category()
        );
        products.put(productId, updated);
        return true;
    }

    @Override
    public void release(String productId, int quantity) {
        ProductDTO product = products.get(productId);
        if (product == null) {
            return;
        }
        
        ProductDTO updated = new ProductDTO(
            product.productId(),
            product.name(),
            product.description(),
            product.price(),
            product.quantityAvailable() + quantity,
            product.category()
        );
        products.put(productId, updated);
    }
}
