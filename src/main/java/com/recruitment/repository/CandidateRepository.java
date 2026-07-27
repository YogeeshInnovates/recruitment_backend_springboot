package com.recruitment.repository;

import com.recruitment.model.Candidate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CandidateRepository extends JpaRepository<Candidate, Long> {

    List<Candidate> findAllByOrganizationId(Long organizationId);

    Optional<Candidate> findByOrganizationIdAndEmail(Long organizationId, String email);
}
