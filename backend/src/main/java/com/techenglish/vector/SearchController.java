package com.techenglish.vector;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final VectorSearchService vectorSearchService;

    @GetMapping
    public VectorDtos.SearchResponse search(@RequestParam String q,
                                            @RequestParam(defaultValue = "8") int limit) {
        return vectorSearchService.search(q, limit);
    }

    @GetMapping("/status")
    public VectorDtos.VectorStatus status() {
        return vectorSearchService.status();
    }
}
