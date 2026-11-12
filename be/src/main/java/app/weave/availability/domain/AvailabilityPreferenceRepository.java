package app.weave.availability.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AvailabilityPreferenceRepository
        extends JpaRepository<AvailabilityPreference, Long> {
}
