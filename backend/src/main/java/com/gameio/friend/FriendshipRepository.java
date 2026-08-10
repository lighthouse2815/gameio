package com.gameio.friend;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface FriendshipRepository extends JpaRepository<Friendship, UUID> {
    @Query("""
            select f from Friendship f
            join fetch f.userLow
            join fetch f.userHigh
            where f.userLow.id = :lowId and f.userHigh.id = :highId
            """)
    Optional<Friendship> findPair(@Param("lowId") UUID lowId, @Param("highId") UUID highId);

    @Query("""
            select f from Friendship f
            join fetch f.userLow
            join fetch f.userHigh
            where f.status = :status
              and (f.userLow.id = :userId or f.userHigh.id = :userId)
            """)
    List<Friendship> findForUser(
            @Param("userId") UUID userId,
            @Param("status") FriendshipStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select f from Friendship f
            join fetch f.userLow
            join fetch f.userHigh
            where f.id = :id
            """)
    Optional<Friendship> findByIdForUpdate(@Param("id") UUID id);
}
