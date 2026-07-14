package com.matessa.commercial.repository;

import com.matessa.commercial.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    List<ProductImage> findByProductIdOrderByDisplayOrderAscIdAsc(Long productId);

    List<ProductImage> findByProductIdAndPrimaryTrue(Long productId);
}
