CREATE TABLE notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    type VARCHAR(64) NOT NULL,
    title VARCHAR(200) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    reference_id VARCHAR(64),
    reference_type VARCHAR(64),
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

CREATE INDEX idx_notifications_user_id ON notifications (user_id);
CREATE INDEX idx_notifications_is_read ON notifications (is_read);
CREATE INDEX idx_notifications_created_at ON notifications (created_at);
CREATE INDEX idx_notifications_reference_id ON notifications (reference_id);
