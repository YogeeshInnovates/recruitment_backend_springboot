package com.recruitment.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "interview_transcripts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewTranscript {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interview_id", nullable = false)
    @JsonBackReference("interview-transcripts")
    private Interview interview;

    private String speaker;

    @Column(columnDefinition = "TEXT")
    private String content;

    private LocalDateTime timestamp;

    private Integer questionNumber;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
