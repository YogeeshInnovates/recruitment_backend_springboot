package com.recruitment.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "candidate_activity_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CandidateActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "interview_id", nullable = false)
    private Long interviewId;

    private String eventType;

    @Column(columnDefinition = "TEXT")
    private String detail;

    private LocalDateTime occurredAt;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
