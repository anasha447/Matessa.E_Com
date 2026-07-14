package com.matessa.commercial.repository;

import com.matessa.commercial.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByActiveTrueOrderByDisplayOrderAscNameAsc();

    List<Category> findByParentIdIsNullAndActiveTrueOrderByDisplayOrderAscNameAsc();

    List<Category> findByParentIdAndActiveTrueOrderByDisplayOrderAscNameAsc(Long parentId);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);
}
