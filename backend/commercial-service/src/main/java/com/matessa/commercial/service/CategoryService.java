package com.matessa.commercial.service;

import com.matessa.commercial.dto.request.CategoryRequest;
import com.matessa.commercial.dto.response.CategoryResponse;

import java.util.List;

public interface CategoryService {

    List<CategoryResponse> getTree();

    List<CategoryResponse> getTopLevel();

    List<CategoryResponse> getChildren(Long parentId);

    CategoryResponse get(Long id);

    CategoryResponse create(CategoryRequest request);

    CategoryResponse update(Long id, CategoryRequest request);

    void delete(Long id);
}
