package com.knowledgegraph.core.entity;

import com.knowledgegraph.core.relationship.RelationshipDto;
import com.knowledgegraph.core.relationship.RelationshipService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EntityController {

    private final EntityService entityService;
    private final RelationshipService relationshipService;

    public EntityController(EntityService entityService, RelationshipService relationshipService) {
        this.entityService = entityService;
        this.relationshipService = relationshipService;
    }

    @PostMapping("/entities")
    public ResponseEntity<EntityDto> create(@RequestBody EntityCreateRequest request) {
        Entity created = entityService.create(request.type(), request.properties());
        return ResponseEntity.status(HttpStatus.CREATED).body(EntityDto.from(created));
    }

    @GetMapping("/entities")
    public List<EntityDto> list(@RequestParam(required = false) String type) {
        return entityService.list(type).stream().map(EntityDto::from).toList();
    }

    @GetMapping("/entities/{entityId}")
    public EntityDto get(@PathVariable String entityId) {
        return EntityDto.from(entityService.getById(entityId));
    }

    @PatchMapping("/entities/{entityId}")
    public EntityDto update(@PathVariable String entityId, @RequestBody EntityUpdateRequest request) {
        return EntityDto.from(entityService.update(entityId, request.properties()));
    }

    @DeleteMapping("/entities/{entityId}")
    public ResponseEntity<Void> delete(
            @PathVariable String entityId, @RequestParam(defaultValue = "false") boolean cascade) {
        entityService.delete(entityId, cascade);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/entities/{entityId}/relationships")
    public List<RelationshipDto> listRelationships(
            @PathVariable String entityId, @RequestParam(defaultValue = "both") String direction) {
        return relationshipService.listByEntity(entityId, direction).stream()
                .map(RelationshipDto::from)
                .toList();
    }
}
