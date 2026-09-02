package com.project.minidog.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/query")
public class QueryController {

    private final QueryService queryService;

    public QueryController(QueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public ResponseEntity<QueryResponse> query(
            @RequestParam String name,
            @RequestParam long from,
            @RequestParam long to,
            @RequestParam(required = false) String stat,
            @RequestParam(name = "tag", required = false) List<String> tagFilters) {
        return ResponseEntity.ok(queryService.query(name, from, to, parseTags(tagFilters), stat));
    }

    private Map<String, String> parseTags(List<String> filters) {
        if (filters == null) {
            return Map.of();
        }
        Map<String, String> tags = new LinkedHashMap<>();
        for (String filter : filters) {
            int separator = filter.indexOf(':');
            if (separator <= 0 || separator == filter.length() - 1) {
                throw new IllegalArgumentException("tag filters must use key:value format");
            }
            tags.put(filter.substring(0, separator), filter.substring(separator + 1));
        }
        return Map.copyOf(tags);
    }
}
