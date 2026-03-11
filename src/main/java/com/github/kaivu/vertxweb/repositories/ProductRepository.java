package com.github.kaivu.vertxweb.repositories;

import com.github.kaivu.vertxweb.domain.Product;
import io.smallrye.mutiny.Uni;
import java.util.List;

public interface ProductRepository {

    Uni<Product> findById(String productId);

    Uni<List<Product>> findAll();

    Uni<Product> save(Product product);

    Uni<Product> updateStock(String productId, int quantity);

    Uni<Boolean> delete(String productId);
}
