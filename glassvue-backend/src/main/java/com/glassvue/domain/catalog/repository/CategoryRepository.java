package com.glassvue.domain.catalog.repository;

import com.glassvue.domain.catalog.entity.Category;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    boolean existsByName(String name);

    List<Category> findAllByOrderByNameAsc();
}
