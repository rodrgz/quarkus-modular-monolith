package org.acme.inventory.infra.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.acme.cache.CacheInvalidator;
import org.acme.cache.MultiLevelCache;
import org.acme.inventory.domain.api.InventoryService;
import org.acme.inventory.domain.api.dto.ProductDTO;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Panache-based implementation of InventoryService with multi-level caching.
 * 
 * <h2>Cache Strategy:</h2>
 * <ul>
 * <li>{@link #findById(String)} - Cached per product ID</li>
 * <li>{@link #listAvailable()} - Cached as single entry</li>
 * <li>{@link #reserve(String, int)} - Invalidates product cache</li>
 * <li>{@link #release(String, int)} - Invalidates product cache</li>
 * </ul>
 */
@ApplicationScoped
public class PanacheInventoryService implements InventoryService {

    private static final String CACHE_PRODUCTS = "products";
    private static final String CACHE_PRODUCTS_LIST = "products-list";

    private final ProductRepository productRepository;
    private final CacheInvalidator cacheInvalidator;

    @Inject
    public PanacheInventoryService(ProductRepository productRepository, CacheInvalidator cacheInvalidator) {
        this.productRepository = productRepository;
        this.cacheInvalidator = cacheInvalidator;
    }

    @Override
    @MultiLevelCache(cacheName = CACHE_PRODUCTS, l1Ttl = 30, l1MaxSize = 500, l2Ttl = 300)
    public Optional<ProductDTO> findById(String productId) {
        return productRepository.findByIdOptional(productId)
                .map(this::toDTO);
    }

    @Override
    public List<ProductDTO> findByIds(List<String> productIds) {
        return productRepository.list("productId in ?1", productIds).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    @MultiLevelCache(cacheName = CACHE_PRODUCTS_LIST, l1Ttl = 60, l1MaxSize = 10, l2Ttl = 600)
    public List<ProductDTO> listAvailable() {
        return productRepository.list("quantityAvailable > 0").stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public boolean reserve(String productId, int quantity) {
        ProductEntity entity = productRepository.findById(productId);

        if (entity == null || entity.quantityAvailable < quantity) {
            return false;
        }

        entity.quantityAvailable -= quantity;
        productRepository.persist(entity);

        // Invalidate cache after update
        invalidateProductCache(productId);

        return true;
    }

    @Override
    @Transactional
    public void release(String productId, int quantity) {
        ProductEntity entity = productRepository.findById(productId);
        if (entity != null) {
            entity.quantityAvailable += quantity;
            productRepository.persist(entity);

            // Invalidate cache after update
            invalidateProductCache(productId);
        }
    }

    /**
     * Invalidate all caches related to product data.
     */
    private void invalidateProductCache(String productId) {
        // Invalidate specific product
        cacheInvalidator.invalidateByMethod(CACHE_PRODUCTS, "findById", productId);
        // Invalidate product list
        cacheInvalidator.invalidateAll(CACHE_PRODUCTS_LIST);
    }

    private ProductDTO toDTO(ProductEntity entity) {
        return new ProductDTO(
                entity.productId,
                entity.name,
                entity.description,
                entity.price,
                entity.quantityAvailable,
                entity.category);
    }
}
