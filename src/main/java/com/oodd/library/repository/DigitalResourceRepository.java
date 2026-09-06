package com.oodd.library.repository;

import com.oodd.library.model.DigitalResource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DigitalResourceRepository extends JpaRepository<DigitalResource, Long>,
        JpaSpecificationExecutor<DigitalResource> {

    List<DigitalResource> findAllByOrderByUploadDateDesc();

    /** Single-query fetch of every resource (avoids N+1 on category). */
    @Query("SELECT d FROM DigitalResource d LEFT JOIN FETCH d.category ORDER BY d.uploadDate DESC, d.id DESC")
    List<DigitalResource> findAllWithCategory();

    @Query("SELECT d FROM DigitalResource d LEFT JOIN d.category c WHERE " +
           "LOWER(d.title) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(COALESCE(d.author, '')) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(COALESCE(d.description, '')) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(COALESCE(c.name, '')) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "ORDER BY d.uploadDate DESC")
    List<DigitalResource> searchResources(@Param("search") String search);

    long countByCategory_Id(Long categoryId);
}
