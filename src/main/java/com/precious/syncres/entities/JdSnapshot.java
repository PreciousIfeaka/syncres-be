package com.precious.syncres.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "jd_snapshots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JdSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "session_id", length = 128)
    private String sessionId;

    @Column(name = "source_url", length = 2000)
    private String sourceUrl;

    @Column(name = "company_name")
    private String companyName;

    @Column(name = "role_title")
    private String roleTitle;

    @Column(name = "raw_text", nullable = false)
    private String rawText;

    @CreationTimestamp
    @Column(name = "captured_at", nullable = false, updatable = false)
    private OffsetDateTime capturedAt;
}
