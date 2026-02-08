package org.acme.inventory.microservice.config;

import org.acme.inventory.domain.api.InventoryService;
import org.acme.inventory.domain.api.InventoryServiceFactory;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/**
 * CDI Producer for inventory-domain-module beans.
 * 
 * Uses factory from the public API to avoid importing internal packages.
 */
@ApplicationScoped
public class DomainBeanProducer {

    @Produces
    public InventoryService inventoryService() {
        return InventoryServiceFactory.createDefault();
    }
}
