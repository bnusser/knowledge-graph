package com.knowledgegraph.core.relationship;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RelationshipController {

    private final RelationshipService relationshipService;

    public RelationshipController(RelationshipService relationshipService) {
        this.relationshipService = relationshipService;
    }

    @PostMapping("/relationships")
    public ResponseEntity<RelationshipDto> create(@RequestBody RelationshipCreateRequest request) {
        Relationship created = relationshipService.create(
                request.type(), request.sourceEntityId(), request.targetEntityId(), request.properties());
        return ResponseEntity.status(HttpStatus.CREATED).body(RelationshipDto.from(created));
    }

    @PatchMapping("/relationships/{relationshipId}")
    public RelationshipDto update(@PathVariable String relationshipId, @RequestBody RelationshipUpdateRequest request) {
        return RelationshipDto.from(relationshipService.update(relationshipId, request.properties()));
    }

    @DeleteMapping("/relationships/{relationshipId}")
    public ResponseEntity<Void> delete(@PathVariable String relationshipId) {
        relationshipService.delete(relationshipId);
        return ResponseEntity.noContent().build();
    }
}
