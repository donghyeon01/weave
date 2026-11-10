-- 이슈 #18: UK를 (room_id, client_message_id) → (room_id, sender_id, client_message_id).
-- 같은 방 멤버가 타인의 멱등키를 선점하면 피해자 재전송이 선점자 행을 반환하는 문제 차단.
ALTER TABLE chat_messages
    DROP INDEX uk_chat_messages_client,
    ADD CONSTRAINT uk_chat_messages_client UNIQUE (room_id, sender_id, client_message_id);
