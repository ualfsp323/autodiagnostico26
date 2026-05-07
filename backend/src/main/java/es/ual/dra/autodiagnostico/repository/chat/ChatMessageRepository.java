package es.ual.dra.autodiagnostico.repository.chat;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import es.ual.dra.autodiagnostico.model.entitity.chat.ChatMessage;
import es.ual.dra.autodiagnostico.model.entitity.chat.ChatRoomType;
import es.ual.dra.autodiagnostico.model.entitity.chat.ChatSenderRole;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    boolean existsByRoomType(ChatRoomType roomType);

    boolean existsByRoomTypeAndSessionUuid(ChatRoomType roomType, String sessionUuid);

    List<ChatMessage> findTop100ByRoomTypeOrderByCreatedAtDesc(ChatRoomType roomType);

    List<ChatMessage> findTop100ByRoomTypeAndSessionUuidOrderByCreatedAtDesc(ChatRoomType roomType, String sessionUuid);

    List<ChatMessage> findTop100ByRoomTypeAndIdGreaterThanOrderByIdAsc(ChatRoomType roomType, Long id);

    List<ChatMessage> findTop100ByRoomTypeAndSessionUuidAndIdGreaterThanOrderByIdAsc(ChatRoomType roomType,
            String sessionUuid, Long id);

    long countByRoomTypeAndSenderRoleAndReadByUserFalse(ChatRoomType roomType, ChatSenderRole senderRole);

    long countByRoomTypeAndSessionUuidAndSenderRoleAndReadByUserFalse(ChatRoomType roomType, String sessionUuid,
            ChatSenderRole senderRole);

    @Modifying
    @Query("update ChatMessage m set m.readByUser = true where m.roomType = :roomType and m.senderRole = :senderRole and m.readByUser = false")
    int markReadByUser(@Param("roomType") ChatRoomType roomType, @Param("senderRole") ChatSenderRole senderRole);

    @Modifying
    @Query("update ChatMessage m set m.readByUser = true where m.roomType = :roomType and m.sessionUuid = :sessionUuid and m.senderRole = :senderRole and m.readByUser = false")
    int markReadByUserAndSession(@Param("roomType") ChatRoomType roomType, @Param("sessionUuid") String sessionUuid,
            @Param("senderRole") ChatSenderRole senderRole);
}
