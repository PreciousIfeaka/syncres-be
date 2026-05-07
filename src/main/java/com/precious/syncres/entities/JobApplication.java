package com.precious.syncres.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "job_applications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cv_document_id")
    private CvDocument cvDocument;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "jd_snapshot_id")
    private JdSnapshot jdSnapshot;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Column(name = "role_title")
    private String roleTitle;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "application_status", nullable = false, columnDefinition = "application_status")
    private ApplicationStatus applicationStatus = ApplicationStatus.SAVED;

    @Column(name = "match_score")
    private Short matchScore;

    @Column(name = "match_summary")
    private String matchSummary;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "matched_skills", columnDefinition = "TEXT[]")
    private List<String> matchedSkills;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "missing_skills", columnDefinition = "TEXT[]")
    private List<String> missingSkills;

    @Column(name = "retailored_cv_path", length = 500)
    private String retailoredCvPath;

    @Column(name = "jd_url", length = 2000)
    private String jdUrl;

    @Column(name = "applied_at")
    private OffsetDateTime appliedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        updatedAt = OffsetDateTime.now();
    }
}
