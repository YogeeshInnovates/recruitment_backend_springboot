package com.recruitment.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "interview_snapshots")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "interview_id", nullable = false)
    private Long interviewId;

    private String eventType;

    @Column(name = "cloudinary_url", length = 1024)
    private String cloudinaryUrl;

    private LocalDateTime capturedAt;
}
