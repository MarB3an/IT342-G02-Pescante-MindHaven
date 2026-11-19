package edu.cit.pescante.mindhaven.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TopicRoomRepository extends JpaRepository<TopicRoom, UUID> {
    boolean existsByRoomNameIgnoreCase(String roomName);
    boolean existsByCategorySlugIgnoreCase(String categorySlug);
    List<TopicRoom> findAllByActiveTrueOrderByRoomNameAsc();
    List<TopicRoom> findAllByOrderByCreatedAtDesc();
    long countByActiveTrue();
}
