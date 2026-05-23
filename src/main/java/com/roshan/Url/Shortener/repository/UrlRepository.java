package com.roshan.Url.Shortener.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.roshan.Url.Shortener.model.UrlMapping;

@Repository
public interface UrlRepository extends JpaRepository<UrlMapping, String> {
	
}
