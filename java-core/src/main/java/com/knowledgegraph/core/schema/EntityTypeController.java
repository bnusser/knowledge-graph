package com.knowledgegraph.core.schema;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EntityTypeController {

    private final EntityTypeService service;

    public EntityTypeController(EntityTypeService service) {
        this.service = service;
    }

    @PostMapping("/entity-types")
    public ResponseEntity<EntityTypeDefinitionDto> create(@RequestBody EntityTypeDefinitionDto request) {
        EntityTypeDefinition created =
                service.create(request.name(), request.properties(), request.identifyingProperty());
        return ResponseEntity.status(HttpStatus.CREATED).body(EntityTypeDefinitionDto.from(created));
    }

    @GetMapping("/entity-types")
    public List<EntityTypeDefinitionDto> list() {
        return service.list().stream().map(EntityTypeDefinitionDto::from).toList();
    }
}
