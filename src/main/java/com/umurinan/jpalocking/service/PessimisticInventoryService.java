package com.umurinan.jpalocking.service;

import com.umurinan.jpalocking.entity.Product;
import com.umurinan.jpalocking.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PessimisticInventoryService {

    private final ProductRepository productRepository;

    @Transactional
    public void decreaseStock(Long productId, int quantity) {
        Product product = productRepository.findByIdWithLock(productId)
            .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));
        if (product.getStock() < quantity) {
            throw new IllegalStateException("Insufficient stock");
        }
        product.setStock(product.getStock() - quantity);
    }
}
