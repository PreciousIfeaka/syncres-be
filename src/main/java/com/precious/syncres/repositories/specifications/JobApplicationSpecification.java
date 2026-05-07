package com.precious.syncres.repositories.specifications;

import com.precious.syncres.entities.ApplicationStatus;
import com.precious.syncres.entities.JobApplication;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

public class JobApplicationSpecification {
    public static Specification<JobApplication> forUser(UUID userId) {
        return (root, query, cb) -> cb.equal(root.get("user").get("id"), userId);
    }

    public static Specification<JobApplication> notDeleted() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    public static Specification<JobApplication> withStatus(ApplicationStatus status) {
        if (status == null) return null;
        return (root, query, cb) -> cb.equal(root.get("applicationStatus"), status);
    }

    public static Specification<JobApplication> companyLike(String company) {
        if (company == null || company.isBlank()) return null;
        return (root, query, cb) -> cb.like(
                cb.lower(root.get("companyName")),
                "%" + company.toLowerCase() + "%"
        );
    }

    public static Specification<JobApplication> withCvDocument() {
        return (root, query, cb) -> {
            root.fetch("cvDocument", JoinType.LEFT);
            return null;
        };
    }
}
