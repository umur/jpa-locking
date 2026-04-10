package com.umurinan.jpalocking;

import com.umurinan.jpalocking.entity.Product;
import com.umurinan.jpalocking.repository.ProductRepository;
import com.umurinan.jpalocking.service.PessimisticInventoryService;
import com.umurinan.jpalocking.util.SqlCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class PessimisticLockingIT extends AbstractIT {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private PessimisticInventoryService inventoryService;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
    }

    @Test
    @DisplayName("findByIdWithLock issues SELECT ... FOR UPDATE")
    void findByIdWithLockIssuesSelectForUpdate() {
        Product product = productRepository.save(new Product("Widget", 10));

        SqlCaptor.reset();
        inventoryService.decreaseStock(product.getId(), 3);

        List<String> queries = SqlCaptor.getQueries();
        // PostgreSQL uses FOR NO KEY UPDATE for PESSIMISTIC_WRITE (weaker than FOR UPDATE,
        // avoids blocking foreign key lookups). Other databases use FOR UPDATE.
        assertThat(queries).anyMatch(sql -> sql.contains("for update") || sql.contains("for no key update"));
    }

    @Test
    @DisplayName("concurrent access with pessimistic locking always produces correct stock")
    void concurrentAccessProducesCorrectStock() throws InterruptedException {
        Product product = productRepository.save(new Product("Widget", 10));
        Long productId = product.getId();

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        List<Exception> errors = new CopyOnWriteArrayList<>();

        Runnable task = () -> {
            try {
                start.await();
                inventoryService.decreaseStock(productId, 3);
            } catch (Exception e) {
                errors.add(e);
            } finally {
                done.countDown();
            }
        };

        new Thread(task).start();
        new Thread(task).start();
        start.countDown();
        done.await(10, TimeUnit.SECONDS);

        assertThat(errors).isEmpty();

        Product result = productRepository.findById(productId).orElseThrow();
        assertThat(result.getStock()).isEqualTo(4); // 10 - 3 - 3, always correct
    }
}
