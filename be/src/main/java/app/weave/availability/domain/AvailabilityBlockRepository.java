package app.weave.availability.domain;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AvailabilityBlockRepository extends JpaRepository<AvailabilityBlock, Long> {

    List<AvailabilityBlock> findByScopeIdOrderById(Long scopeId);

    List<AvailabilityBlock> findByScopeIdIn(Collection<Long> scopeIds);

    void deleteByScopeId(Long scopeId);

    /** 목록 응답의 blockCount — N+1 없이 scope별 개수를 한 쿼리로. */
    @Query("select b.scopeId, count(b) from AvailabilityBlock b where b.scopeId in :scopeIds"
            + " group by b.scopeId")
    List<Object[]> countByScopeIdIn(@Param("scopeIds") Collection<Long> scopeIds);
}
