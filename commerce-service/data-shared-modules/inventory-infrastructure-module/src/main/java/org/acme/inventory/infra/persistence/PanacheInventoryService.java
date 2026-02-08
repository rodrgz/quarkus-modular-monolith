package org.acme.inventory.infra.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.acme.inventory.domain.api.InventoryService;
import org.acme.inventory.domain.api.dto.ProductDTO;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@ApplicationScoped
public class PanacheInventoryService implements InventoryService {

    private final ProductRepository productRepository;

    @Inject
    public PanacheInventoryService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
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
    public List<ProductDTO> listAvailable() {
        return productRepository.list("quantityAvailable > 0").stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public boolean reserve(String productId, int quantity) {
        // Lock pessimistic (simple approach for PoC) could be used here,
        // but default default transaction isolation usually suffices for basic cases
        ProductEntity entity = productRepository.findById(productId);

        if (entity == null || entity.quantityAvailable < quantity) {
            return false;
        }

        entity.quantityAvailable -= quantity;
        productRepository.persist(entity); // Actually update is automatic in managed state
        return true;
    }

    @Override
    @Transactional
    public void release(String productId, int quantity) {
        ProductEntity entity = productRepository.findById(productId);
        if (entity != null) {
            entity.quantityAvailable += quantity;
            productRepository.persist(entity);
        }
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
