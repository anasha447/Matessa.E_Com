package com.matessa.commercial.service.impl;

import com.matessa.commercial.dto.request.CategoryRequest;
import com.matessa.commercial.dto.response.CategoryResponse;
import com.matessa.commercial.entity.Category;
import com.matessa.commercial.exception.BadRequestException;
import com.matessa.commercial.exception.ConflictException;
import com.matessa.commercial.exception.ResourceNotFoundException;
import com.matessa.commercial.repository.CategoryRepository;
import com.matessa.commercial.service.CategoryService;
import com.matessa.commercial.config.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryServiceImpl(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.CATEGORIES, key = "'tree'")
    public List<CategoryResponse> getTree() {
        List<Category> all = categoryRepository.findByActiveTrueOrderByDisplayOrderAscNameAsc();
        Map<Long, List<Category>> byParent = all.stream()
                .filter(category -> category.getParentId() != null)
                .collect(Collectors.groupingBy(Category::getParentId));
        return all.stream()
                .filter(category -> category.getParentId() == null)
                .map(category -> toTreeNode(category, byParent))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.CATEGORIES, key = "'top'")
    public List<CategoryResponse> getTopLevel() {
        return categoryRepository.findByParentIdIsNullAndActiveTrueOrderByDisplayOrderAscNameAsc()
                .stream().map(this::toFlat).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> getChildren(Long parentId) {
        return categoryRepository.findByParentIdAndActiveTrueOrderByDisplayOrderAscNameAsc(parentId)
                .stream().map(this::toFlat).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse get(Long id) {
        return toFlat(findCategory(id));
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CATEGORIES, allEntries = true)
    public CategoryResponse create(CategoryRequest request) {
        if (categoryRepository.existsBySlug(request.slug())) {
            throw new ConflictException("Category slug already exists: " + request.slug());
        }
        validateParent(request.parentId(), null);
        Category category = new Category();
        apply(category, request);
        return toFlat(categoryRepository.save(category));
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CATEGORIES, allEntries = true)
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = findCategory(id);
        if (categoryRepository.existsBySlugAndIdNot(request.slug(), id)) {
            throw new ConflictException("Category slug already exists: " + request.slug());
        }
        validateParent(request.parentId(), id);
        apply(category, request);
        return toFlat(categoryRepository.save(category));
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.CATEGORIES, allEntries = true)
    public void delete(Long id) {
        Category category = findCategory(id);
        category.setActive(false); // soft delete
        categoryRepository.save(category);
    }

    private Category findCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id));
    }

    private void validateParent(Long parentId, Long selfId) {
        if (parentId == null) {
            return;
        }
        if (parentId.equals(selfId)) {
            throw new BadRequestException("A category cannot be its own parent");
        }
        if (!categoryRepository.existsById(parentId)) {
            throw new BadRequestException("Parent category not found: " + parentId);
        }
    }

    private void apply(Category category, CategoryRequest request) {
        category.setParentId(request.parentId());
        category.setName(request.name());
        category.setSlug(request.slug());
        category.setDescription(request.description());
        category.setImageUrl(request.imageUrl());
        category.setDisplayOrder(request.displayOrder());
        category.setActive(true);
    }

    private CategoryResponse toTreeNode(Category category, Map<Long, List<Category>> byParent) {
        List<CategoryResponse> children = byParent.getOrDefault(category.getId(), List.of()).stream()
                .map(child -> toTreeNode(child, byParent))
                .toList();
        return toResponse(category, children);
    }

    private CategoryResponse toFlat(Category category) {
        return toResponse(category, List.of());
    }

    private CategoryResponse toResponse(Category category, List<CategoryResponse> children) {
        return new CategoryResponse(category.getId(), category.getParentId(), category.getName(),
                category.getSlug(), category.getDescription(), category.getImageUrl(),
                category.getDisplayOrder(), category.isActive(), children);
    }
}
