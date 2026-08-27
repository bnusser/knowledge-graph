package com.knowledgegraph.core.schema;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RelationshipTypeController {

    private final RelationshipTypeService service;

    public RelationshipTypeController(RelationshipTypeService service) {
        this.service = service;
    }

    @PostMapping("/relationship-types")
    public ResponseEntity<RelationshipTypeDefinitionDto> create(@RequestBody RelationshipTypeDefinitionDto request) {
        RelationshipTypeDefinition created = service.create(
                request.name(), request.allowedSourceTypes(), request.allowedTargetTypes(), request.properties());
        return ResponseEntity.status(HttpStatus.CREATED).body(RelationshipTypeDefinitionDto.from(created));
    }

    @GetMapping("/relationship-types")
    public List<RelationshipTypeDefinitionDto> list() {
        return service.list().stream().map(RelationshipTypeDefinitionDto::from).toList();
    }
}
