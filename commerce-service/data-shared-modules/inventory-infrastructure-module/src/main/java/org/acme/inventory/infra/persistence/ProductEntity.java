package org.acme.inventory.infra.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(name = "product")
public class ProductEntity {

    @Id
    public String productId;

    public String name;
    public String description;
    public BigDecimal price;
    public int quantityAvailable;
    public String category;

    /**
     * Default constructor for JPA/Hibernate.
     */
    public ProductEntity() {
    }

    public ProductEntity(String productId, String name, String description, BigDecimal price, int quantityAvailable,
            String category) {
        this.productId = productId;
        this.name = name;
        this.description = description;
        this.price = price;
        this.quantityAvailable = quantityAvailable;
        this.category = category;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ProductEntity that = (ProductEntity) o;
        return Objects.equals(productId, that.productId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(productId);
    }
}
