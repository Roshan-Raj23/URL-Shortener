package com.roshan.Url.Shortener.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.roshan.Url.Shortener.repository.ShardedUrlRepository;


@RestController
public class ShardTestController {

    private final ShardedUrlRepository repo;

    public ShardTestController(ShardedUrlRepository repo) {
        this.repo = repo;
    }

    @GetMapping("/test/{code}")
    public String test(@PathVariable String code) {
        return repo.findLongUrl(code);
    }
}
