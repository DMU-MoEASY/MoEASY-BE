-- Match the project's explicit ON UPDATE RESTRICT policy without rewriting the applied migration.
ALTER TABLE availability_response
    DROP FOREIGN KEY fk_availability_response_poll,
    DROP FOREIGN KEY fk_availability_response_member,
    ADD CONSTRAINT fk_availability_response_poll_restrict FOREIGN KEY (availability_poll_id)
        REFERENCES availability_poll(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT fk_availability_response_member_restrict FOREIGN KEY (group_member_id)
        REFERENCES group_member(id) ON DELETE RESTRICT ON UPDATE RESTRICT;
