package com.precious.syncres.repositories.specifications;

import com.precious.syncres.entities.ApplicationNote;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

public class ApplicationNotesSpecification {
    public static Specification<ApplicationNote> withApplication() {
        return (root, query, cb) -> {
            root.fetch("application", JoinType.LEFT);
            return null;
        };
    }
}
