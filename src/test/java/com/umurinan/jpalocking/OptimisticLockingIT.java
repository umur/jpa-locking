package com.umurinan.jpalocking;

import com.umurinan.jpalocking.entity.Product;
import com.umurinan.jpalocking.repository.ProductRepository;
import com.umurinan.jpalocking.service.OptimisticInventoryService;
import com.umurinan.jpalocking.util.SqlCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OptimisticLockingIT extends AbstractIT {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OptimisticInventoryService inventoryService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
    }

    @Test
    @DisplayName("@Version field starts at 0 and increments on each update")
    void versionFieldIncrementsOnEachUpdate() {
        Product product = productRepository.save(new Product("Widget", 10));
        assertThat(product.getVersion()).isEqualTo(0);

        inventoryService.decreaseStock(product.getId(), 3);

        Product updated = productRepository.findById(product.getId()).orElseThrow();
        assertThat(updated.getStock()).isEqualTo(7);
        assertThat(updated.getVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("UPDATE statement includes WHERE version = ? check")
    void updateIncludesVersionCheck() {
        Product product = productRepository.save(new Product("Widget", 10));

        SqlCaptor.reset();
        inventoryService.decreaseStock(product.getId(), 3);

        List<String> queries = SqlCaptor.getQueries();
        String updateSql = queries.stream()
            .filter(q -> q.startsWith("update"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No UPDATE statement found"));

        assertThat(updateSql).contains("version");
    }

    @Test
    @DisplayName("concurrent modification throws ObjectOptimisticLockingFailureException")
    void concurrentModificationThrowsOptimisticLockException() {
        Product product = productRepository.save(new Product("Widget", 10));

        // Simulate Thread A: loads the entity at version 0
        Product staleProduct = productRepository.findById(product.getId()).orElseThrow();

        // Simulate Thread B: commits a change first, version becomes 1
        transactionTemplate.execute(status -> {
            Product fresh = productRepository.findById(product.getId()).orElseThrow();
            fresh.setStock(fresh.getStock() - 3);
            productRepository.save(fresh);
            return null;
        });

        // Thread A tries to save its stale copy (version = 0, but DB is now at version = 1)
        staleProduct.setStock(staleProduct.getStock() - 3);

        assertThatThrownBy(() ->
            transactionTemplate.execute(status -> {
                productRepository.saveAndFlush(staleProduct);
                return null;
            })
        ).isInstanceOf(ObjectOptimisticLockingFailureException.class);

        // Thread B's write is preserved
        Product result = productRepository.findById(product.getId()).orElseThrow();
        assertThat(result.getStock()).isEqualTo(7);
        assertThat(result.getVersion()).isEqualTo(1);
    }
}
